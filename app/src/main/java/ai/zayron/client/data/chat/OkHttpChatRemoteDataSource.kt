package ai.zayron.client.data.chat

import ai.zayron.client.data.network.ApiClient
import ai.zayron.client.data.network.NetworkResult
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

/** Supplies a short-lived Zayron access token from the future secure session store. */
fun interface ChatAccessTokenProvider {
    suspend fun getAccessToken(): String?
}

/**
 * OkHttp SSE transport for the proposed POST /api/v1/chat/stream endpoint.
 * It remains separate from the chat UI until a real backend and session store exist.
 */
class OkHttpChatRemoteDataSource(
    private val accessTokenProvider: ChatAccessTokenProvider?,
    private val client: OkHttpClient = ApiClient.httpClient,
    private val callFactory: okhttp3.Call.Factory = client,
    private val endpointProvider: () -> HttpUrl? = {
        ApiClient.urlFor("api/v1/chat/stream")
    }
) : ChatRemoteDataSource {

    override suspend fun streamReply(
        request: ChatStreamRequest,
        onEvent: (ChatStreamEvent) -> Unit
    ): NetworkResult<Unit> {
        if (request.requestId.isBlank() || request.messages.isEmpty()) {
            return NetworkResult.RejectedRequest
        }
        val endpoint = endpointProvider() ?: return NetworkResult.NotConfigured
        val token = accessTokenProvider?.getAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: return NetworkResult.AuthenticationRequired

        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Accept", "text/event-stream")
            .header("Idempotency-Key", request.requestId)
            .header("Authorization", "Bearer $token")
            .post(request.toJson().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = callFactory.newCall(httpRequest)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(NetworkResult.ConnectionError)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use { received ->
                        if (!received.isSuccessful) {
                            val body = received.body?.string().orEmpty().take(MAX_ERROR_BODY_CHARS)
                            return@use NetworkResult.HttpError(received.code, body)
                        }

                        val body = received.body
                            ?: return@use NetworkResult.StreamError("EMPTY_RESPONSE_BODY")
                        val contentType = received.header("Content-Type").orEmpty()
                        if (!contentType.lowercase().startsWith("text/event-stream")) {
                            return@use NetworkResult.StreamError("UNEXPECTED_CONTENT_TYPE")
                        }

                        val decoder = SseFrameDecoder()
                        var terminalResult: NetworkResult<Unit>? = null

                        try {
                            val source = body.source()
                            while (continuation.isActive && !source.exhausted()) {
                                val line = source.readUtf8Line() ?: break
                                for (event in decoder.append(line + "\n")) {
                                    onEvent(event)
                                    terminalResult = event.toTerminalResult(received.code)
                                    if (terminalResult != null) break
                                }
                                if (terminalResult != null) break
                            }

                            if (terminalResult == null && continuation.isActive) {
                                val trailing = source.buffer.readUtf8()
                                decoder.append(trailing, endOfInput = true).forEach { event ->
                                    onEvent(event)
                                    terminalResult = event.toTerminalResult(received.code) ?: terminalResult
                                }
                            }
                            terminalResult ?: NetworkResult.StreamError("STREAM_ENDED_WITHOUT_COMPLETION")
                        } catch (_: IOException) {
                            NetworkResult.ConnectionError
                        } catch (_: Exception) {
                            NetworkResult.StreamError("STREAM_HANDLER_FAILED")
                        }
                    }

                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    private fun ChatStreamEvent.toTerminalResult(statusCode: Int): NetworkResult<Unit>? =
        when (this) {
            is ChatStreamEvent.Completed -> NetworkResult.Success(Unit, statusCode)
            is ChatStreamEvent.Failed -> NetworkResult.StreamError(code)
            is ChatStreamEvent.Delta, ChatStreamEvent.Done -> null
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_ERROR_BODY_CHARS = 16_384
    }
}
