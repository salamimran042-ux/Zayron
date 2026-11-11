package ai.zayron.client.data.chat

import android.content.Context
import ai.zayron.client.data.network.ApiClient
import ai.zayron.client.data.network.NetworkResult
import ai.zayron.client.ui.chat.ChatAttachmentReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CloudMessageReceipt(val conversationId: String, val messageId: String)

interface CloudChatStore {
    suspend fun saveUserMessage(
        content: String,
        attachments: List<ChatAttachmentReference> = emptyList()
    ): NetworkResult<Unit>

    /** Returns the server-issued message identity when the store supports it. */
    suspend fun saveUserMessageWithReceipt(
        content: String,
        attachments: List<ChatAttachmentReference> = emptyList()
    ): NetworkResult<CloudMessageReceipt?> = when (val result = saveUserMessage(content, attachments)) {
        is NetworkResult.Success -> NetworkResult.Success(null, result.statusCode)
        is NetworkResult.HttpError -> result
        is NetworkResult.StreamError -> result
        NetworkResult.AuthenticationRequired -> NetworkResult.AuthenticationRequired
        NetworkResult.NotConfigured -> NetworkResult.NotConfigured
        NetworkResult.RejectedRequest -> NetworkResult.RejectedRequest
        NetworkResult.ConnectionError -> NetworkResult.ConnectionError
    }

    /** Queues extraction only for an attachment already saved on this user's message. */
    suspend fun enqueueDocumentJob(
        receipt: CloudMessageReceipt,
        attachmentId: String
    ): NetworkResult<String> = NetworkResult.NotConfigured

    fun startNewConversation()
}

interface CloudConversationIdStore {
    fun read(): String?
    fun write(id: String)
    fun clear()
}

class SharedPreferencesCloudConversationIdStore(context: Context) : CloudConversationIdStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "zayron_cloud_chat",
        Context.MODE_PRIVATE
    )
    override fun read(): String? = preferences.getString(KEY_CONVERSATION_ID, null)
    override fun write(id: String) {
        preferences.edit().putString(KEY_CONVERSATION_ID, id).apply()
    }
    override fun clear() {
        preferences.edit().remove(KEY_CONVERSATION_ID).apply()
    }
    private companion object {
        const val KEY_CONVERSATION_ID = "conversation_id"
    }
}

/** Saves chat text and private storage references through the authenticated Zayron API. */
class BackendCloudChatStore(
    private val accessTokenProvider: ChatAccessTokenProvider?,
    private val conversationIds: CloudConversationIdStore,
    private val endpointProvider: (String) -> HttpUrl? = ApiClient::urlFor,
    private val requestExecutor: suspend (Request) -> NetworkResult<String> = ApiClient::execute
) : CloudChatStore {
    private val mutex = Mutex()

    override suspend fun saveUserMessage(
        content: String,
        attachments: List<ChatAttachmentReference>
    ): NetworkResult<Unit> = saveUserMessageWithReceipt(content, attachments).asUnitResult()

    override suspend fun saveUserMessageWithReceipt(
        content: String,
        attachments: List<ChatAttachmentReference>
    ): NetworkResult<CloudMessageReceipt?> = mutex.withLock {
        if ((content.isBlank() && attachments.isEmpty()) ||
            content.length > MAX_MESSAGE_LENGTH || attachments.size > MAX_ATTACHMENTS) {
            return@withLock NetworkResult.RejectedRequest
        }
        val token = accessTokenProvider?.getAccessToken()?.takeIf { it.isNotBlank() }
            ?: return@withLock NetworkResult.AuthenticationRequired

        var conversationId = conversationIds.read()
        if (conversationId == null) {
            when (val created = createConversation(token)) {
                is NetworkResult.Success -> conversationId = created.data
                else -> return@withLock created.asUnitResult()
            }
        }

        var result = postMessage(token, conversationId!!, content, attachments)
        if (result is NetworkResult.HttpError && result.statusCode == 404) {
            conversationIds.clear()
            when (val created = createConversation(token)) {
                is NetworkResult.Success -> {
                    conversationId = created.data
                    result = postMessage(token, conversationId!!, content, attachments)
                }
                else -> return@withLock created.asUnitResult()
            }
        }
        result
    }

    override suspend fun enqueueDocumentJob(
        receipt: CloudMessageReceipt,
        attachmentId: String
    ): NetworkResult<String> {
        val token = accessTokenProvider?.getAccessToken()?.takeIf { it.isNotBlank() }
            ?: return NetworkResult.AuthenticationRequired
        val url = endpointProvider("api/v1/documents/jobs") ?: return NetworkResult.NotConfigured
        val body = JSONObject()
            .put("conversation_id", receipt.conversationId)
            .put("message_id", receipt.messageId)
            .put("attachment_id", attachmentId)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return when (val result = requestExecutor(request)) {
            is NetworkResult.Success -> try {
                val id = JSONObject(result.data).getString("id")
                UUID.fromString(id)
                NetworkResult.Success(id, result.statusCode)
            } catch (_: Exception) {
                NetworkResult.StreamError("INVALID_DOCUMENT_JOB_RESPONSE")
            }
            is NetworkResult.HttpError -> result
            is NetworkResult.StreamError -> result
            NetworkResult.AuthenticationRequired -> NetworkResult.AuthenticationRequired
            NetworkResult.NotConfigured -> NetworkResult.NotConfigured
            NetworkResult.RejectedRequest -> NetworkResult.RejectedRequest
            NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override fun startNewConversation() {
        conversationIds.clear()
    }

    private suspend fun createConversation(token: String): NetworkResult<String> {
        val result = execute(
            path = "api/v1/cloud/conversations",
            token = token,
            body = JSONObject().put("title", "Zayron Chat")
        )
        if (result !is NetworkResult.Success) return result
        return try {
            val id = JSONObject(result.data).getJSONObject("conversation").getString("id")
            UUID.fromString(id)
            conversationIds.write(id)
            NetworkResult.Success(id, result.statusCode)
        } catch (_: Exception) {
            NetworkResult.StreamError("INVALID_CLOUD_CONVERSATION_RESPONSE")
        }
    }

    private suspend fun postMessage(
        token: String,
        conversationId: String,
        content: String,
        attachments: List<ChatAttachmentReference>
    ): NetworkResult<CloudMessageReceipt?> {
        val attachmentJson = JSONArray()
        attachments.forEach { item ->
            attachmentJson.put(
                JSONObject()
                    .put("id", item.id)
                    .put("display_name", item.displayName)
                    .put("mime_type", item.mimeType)
                    .put("size_bytes", item.sizeBytes)
                    .put("storage_path", item.storagePath)
            )
        }
        val result = execute(
            path = "api/v1/cloud/messages",
            token = token,
            body = JSONObject()
                .put("conversation_id", conversationId)
                .put("content", content)
                .put("attachments", attachmentJson)
        )
        return when (result) {
            is NetworkResult.Success -> try {
                val messageId = JSONObject(result.data).getJSONObject("message").getString("id")
                UUID.fromString(messageId)
                NetworkResult.Success(CloudMessageReceipt(conversationId, messageId), result.statusCode)
            } catch (_: Exception) {
                NetworkResult.StreamError("INVALID_CLOUD_MESSAGE_RESPONSE")
            }
            is NetworkResult.HttpError -> result
            is NetworkResult.StreamError -> result
            NetworkResult.AuthenticationRequired -> NetworkResult.AuthenticationRequired
            NetworkResult.NotConfigured -> NetworkResult.NotConfigured
            NetworkResult.RejectedRequest -> NetworkResult.RejectedRequest
            NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    private suspend fun execute(path: String, token: String, body: JSONObject): NetworkResult<String> {
        val url = endpointProvider(path) ?: return NetworkResult.NotConfigured
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return requestExecutor(request)
    }

    private fun NetworkResult<CloudMessageReceipt?>.asUnitResult(): NetworkResult<Unit> = when (this) {
        is NetworkResult.Success -> NetworkResult.Success(Unit, statusCode)
        is NetworkResult.HttpError -> NetworkResult.HttpError(statusCode, responseBody)
        is NetworkResult.StreamError -> NetworkResult.StreamError(code)
        NetworkResult.AuthenticationRequired -> NetworkResult.AuthenticationRequired
        NetworkResult.NotConfigured -> NetworkResult.NotConfigured
        NetworkResult.RejectedRequest -> NetworkResult.RejectedRequest
        NetworkResult.ConnectionError -> NetworkResult.ConnectionError
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_MESSAGE_LENGTH = 100_000
        const val MAX_ATTACHMENTS = 5
    }
}
