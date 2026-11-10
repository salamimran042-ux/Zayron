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
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/** Metadata returned after a private attachment has been accepted by the API. */
data class UploadedChatAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val storagePath: String
)

/** Validates the client-side attachment contract shared with the backend. */
object ChatAttachmentPolicy {
    const val MAX_BYTES = 10 * 1024 * 1024

    val allowedMimeTypes = setOf(
        "application/pdf",
        "text/plain",
        "text/markdown",
        "text/csv",
        "application/json",
        "image/png",
        "image/jpeg",
        "image/webp"
    )

    fun isAllowed(mimeType: String?): Boolean =
        mimeType?.substringBefore(';')?.trim()?.lowercase() in allowedMimeTypes

    fun isAllowedSize(sizeBytes: Long): Boolean =
        sizeBytes in 1..MAX_BYTES.toLong()
}

/**
 * Streams Android-selected content into a bounded in-memory buffer, then uploads it
 * through the authenticated Zayron API. The Android UI never receives the session token.
 *
 * Files are capped at 10 MiB before multipart construction. The API forwards the
 * verified user JWT to private Supabase Storage; no service-role credential is used.
 */
class AttachmentUploadClient(
    private val contentResolver: ContentResolver,
    private val accessTokenProvider: ChatAccessTokenProvider,
    private val endpointProvider: (String) -> HttpUrl? = ApiClient::urlFor,
    private val requestExecutor: suspend (Request) -> NetworkResult<String> = ApiClient::execute
) {
    suspend fun upload(uri: Uri): NetworkResult<UploadedChatAttachment> =
        withContext(Dispatchers.IO) {
            val mimeType = contentResolver.getType(uri)
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?: return@withContext NetworkResult.RejectedRequest
            if (!ChatAttachmentPolicy.isAllowed(mimeType)) {
                return@withContext NetworkResult.RejectedRequest
            }

            val metadata = queryMetadata(uri)
                ?: return@withContext NetworkResult.RejectedRequest
            val (displayName, declaredSize) = metadata
            if (declaredSize != null && !ChatAttachmentPolicy.isAllowedSize(declaredSize)) {
                return@withContext NetworkResult.RejectedRequest
            }

            val bytes = try {
                readBounded(uri)
            } catch (_: IOException) {
                return@withContext NetworkResult.ConnectionError
            } ?: return@withContext NetworkResult.RejectedRequest

            if (!ChatAttachmentPolicy.isAllowedSize(bytes.size.toLong())) {
                return@withContext NetworkResult.RejectedRequest
            }

            val token = accessTokenProvider.getAccessToken()
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext NetworkResult.AuthenticationRequired
            val url = endpointProvider("api/v1/cloud/attachments")
                ?: return@withContext NetworkResult.NotConfigured

            val part = bytes.toRequestBody(mimeType.toMediaType())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", safeFilename(displayName), part)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .post(multipart)
                .build()

            when (val result = requestExecutor(request)) {
                is NetworkResult.Success -> parseUploadResponse(
                    result.data,
                    displayName,
                    mimeType,
                    bytes.size.toLong(),
                    result.statusCode
                )
                is NetworkResult.HttpError -> NetworkResult.HttpError(
                    result.statusCode,
                    result.responseBody
                )
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
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else null
                (name?.takeIf { it.isNotBlank() } ?: "attachment") to size
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
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > ChatAttachmentPolicy.MAX_BYTES) return null
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun parseUploadResponse(
        response: String,
        displayName: String,
        mimeType: String,
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
                    id = UUID.randomUUID().toString(),
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    storagePath = path
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
}
