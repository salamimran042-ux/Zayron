package ai.zayron.client.data.chat

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import ai.zayron.client.data.network.ApiClient
import ai.zayron.client.data.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

data class ChatAttachmentDescriptor(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?
)

data class UploadedChatAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val storagePath: String
)

object ChatAttachmentPolicy {
    const val MAX_BYTES = 10 * 1024 * 1024
    val allowedMimeTypes = setOf(
        "application/pdf", "text/plain", "text/markdown", "text/csv",
        "application/json", "image/png", "image/jpeg", "image/webp"
    )
    fun isAllowed(mimeType: String?): Boolean =
        mimeType?.substringBefore(';')?.trim()?.lowercase() in allowedMimeTypes
    fun isAllowedSize(sizeBytes: Long): Boolean = sizeBytes in 1..MAX_BYTES.toLong()
}

/** Authenticated upload to Zayron's private attachment endpoint; UI never receives the token. */
class AttachmentUploadClient(
    private val contentResolver: ContentResolver,
    private val accessTokenProvider: ChatAccessTokenProvider,
    private val endpointProvider: (String) -> HttpUrl? = ApiClient::urlFor,
    private val requestExecutor: suspend (Request) -> NetworkResult<String> = ApiClient::execute
) {
    fun describe(uri: Uri): ChatAttachmentDescriptor? {
        val mimeType = contentResolver.getType(uri)?.substringBefore(';')?.trim()?.lowercase()
            ?: return null
        if (!ChatAttachmentPolicy.isAllowed(mimeType)) return null
        val metadata = queryMetadata(uri) ?: return null
        val declaredSize = metadata.second
        if (declaredSize != null && !ChatAttachmentPolicy.isAllowedSize(declaredSize)) return null
        return ChatAttachmentDescriptor(metadata.first, mimeType, declaredSize)
    }

    suspend fun upload(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): NetworkResult<UploadedChatAttachment> = withContext(Dispatchers.IO) {
        val descriptor = describe(uri) ?: return@withContext NetworkResult.RejectedRequest
        val bytes = try {
            readBounded(uri)
        } catch (_: IOException) {
            return@withContext NetworkResult.ConnectionError
        } ?: return@withContext NetworkResult.RejectedRequest
        if (!ChatAttachmentPolicy.isAllowedSize(bytes.size.toLong())) {
            return@withContext NetworkResult.RejectedRequest
        }
        val token = accessTokenProvider.getAccessToken()?.takeIf { it.isNotBlank() }
            ?: return@withContext NetworkResult.AuthenticationRequired
        val url = endpointProvider("api/v1/cloud/attachments")
            ?: return@withContext NetworkResult.NotConfigured

        val baseBody = bytes.toRequestBody(descriptor.mimeType.toMediaType())
        val progressBody = ProgressRequestBody(baseBody) { sent, total ->
            if (total > 0L) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
        }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", safeFilename(descriptor.displayName), progressBody)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .post(multipart)
            .build()

        when (val result = requestExecutor(request)) {
            is NetworkResult.Success -> parseUploadResponse(
                result.data, descriptor, bytes.size.toLong(), result.statusCode
            )
            is NetworkResult.HttpError -> NetworkResult.HttpError(result.statusCode, result.responseBody)
            is NetworkResult.StreamError -> NetworkResult.StreamError(result.code)
            NetworkResult.AuthenticationRequired -> NetworkResult.AuthenticationRequired
            NetworkResult.NotConfigured -> NetworkResult.NotConfigured
            NetworkResult.RejectedRequest -> NetworkResult.RejectedRequest
            NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long?>? {
        return try {
            contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else {
                        null
                    }
                    (name?.takeIf { it.isNotBlank() } ?: "attachment") to size
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readBounded(uri: Uri): ByteArray? {
        val input = contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > ChatAttachmentPolicy.MAX_BYTES) return null
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun parseUploadResponse(
        response: String,
        descriptor: ChatAttachmentDescriptor,
        sizeBytes: Long,
        statusCode: Int
    ): NetworkResult<UploadedChatAttachment> {
        return try {
            val json = JSONObject(response)
            val path = sequenceOf("Key", "key", "path", "fullPath")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
                ?: return NetworkResult.StreamError("INVALID_ATTACHMENT_RESPONSE")
            NetworkResult.Success(
                UploadedChatAttachment(
                    UUID.randomUUID().toString(),
                    descriptor.displayName,
                    descriptor.mimeType,
                    sizeBytes,
                    path
                ),
                statusCode
            )
        } catch (_: Exception) {
            NetworkResult.StreamError("INVALID_ATTACHMENT_RESPONSE")
        }
    }

    private fun safeFilename(value: String): String =
        value.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.isLetterOrDigit() || it in "._- " }
            .take(120)
            .ifBlank { "attachment" }

    private class ProgressRequestBody(
        private val delegate: RequestBody,
        private val onProgress: (Long, Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun writeTo(sink: BufferedSink) {
            val total = contentLength()
            var sent = 0L
            val forwarding = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    sent += byteCount
                    onProgress(sent, total)
                }
            }.buffer()
            delegate.writeTo(forwarding)
            forwarding.flush()
        }
    }
}
