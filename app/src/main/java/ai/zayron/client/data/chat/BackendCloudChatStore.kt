package ai.zayron.client.data.chat

import android.content.Context
import ai.zayron.client.data.network.ApiClient
import ai.zayron.client.data.network.NetworkResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/** Persists the current user's chat messages through the authenticated Zayron API. */
interface CloudChatStore {
    suspend fun saveUserMessage(content: String): NetworkResult<Unit>
    fun startNewConversation()
}

/** Small non-secret pointer to the cloud conversation owned by the signed-in account. */
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

/**
 * Creates a cloud conversation on first use, then stores each user message.
 * Authentication is supplied by the encrypted Android Keystore session store.
 * The API forwards the verified user JWT to Supabase so row-level security remains
 * the authority for conversation ownership; no database password is needed here.
 */
class BackendCloudChatStore(
    private val accessTokenProvider: ChatAccessTokenProvider?,
    private val conversationIds: CloudConversationIdStore,
    private val endpointProvider: (String) -> HttpUrl? = ApiClient::urlFor,
    private val requestExecutor: suspend (Request) -> NetworkResult<String> = ApiClient::execute
) : CloudChatStore {
    private val mutex = Mutex()

    override suspend fun saveUserMessage(content: String): NetworkResult<Unit> = mutex.withLock {
        if (content.isBlank() || content.length > MAX_MESSAGE_LENGTH) {
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

        var result = postMessage(token, conversationId!!, content)
        if (result is NetworkResult.HttpError && result.statusCode == 404) {
            // The saved ID may belong to a previous account. RLS prevents access;
            // start a fresh conversation for the current authenticated account.
            conversationIds.clear()
            when (val created = createConversation(token)) {
                is NetworkResult.Success -> {
                    conversationId = created.data
                    result = postMessage(token, conversationId!!, content)
                }
                else -> return@withLock created.asUnitResult()
            }
        }
        result.asUnitResult()
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
            val id = JSONObject(result.data)
                .getJSONObject("conversation")
                .getString("id")
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
        content: String
    ): NetworkResult<String> = execute(
        path = "api/v1/cloud/messages",
        token = token,
        body = JSONObject()
            .put("conversation_id", conversationId)
            .put("content", content)
    )

    private suspend fun execute(
        path: String,
        token: String,
        body: JSONObject
    ): NetworkResult<String> {
        val url = endpointProvider(path) ?: return NetworkResult.NotConfigured
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return requestExecutor(request)
    }

    private fun NetworkResult<String>.asUnitResult(): NetworkResult<Unit> = when (this) {
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
    }
}
