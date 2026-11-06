package ai.zayron.client.data.chat

import ai.zayron.client.data.network.NetworkResult
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.mockwebserver.SocketPolicy
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpChatRemoteDataSourceTest {

    @Test
    fun streamsEventsAndSendsBearerAndIdempotencyHeaders() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(
                    "event: message.delta\ndata: {\"delta\":\"Hello\"}\n\n" +
                        "event: message.completed\ndata: {\"messageId\":\"m1\"}\n\n",
                    7
                )
        )
        server.start()
        try {
            val dataSource = OkHttpChatRemoteDataSource(
                accessTokenProvider = ChatAccessTokenProvider { "test-session-token" },
                client = OkHttpClient(),
                endpointProvider = { server.url("/api/v1/chat/stream") }
            )
            val events = mutableListOf<ChatStreamEvent>()

            val result = kotlinx.coroutines.runBlocking {
                dataSource.streamReply(
                    ChatStreamRequest("request-1", null, listOf(
                        ai.zayron.client.ui.chat.ChatMessage("u1", "Hi", ai.zayron.client.ui.chat.ChatRole.USER)
                    )),
                    events::add
                )
            }

            assertTrue(result is NetworkResult.Success)
            assertEquals(
                listOf(
                    ChatStreamEvent.Delta("Hello"),
                    ChatStreamEvent.Completed("m1")
                ),
                events
            )
            val recorded = server.takeRequest()
            assertEquals("Bearer test-session-token", recorded.getHeader("Authorization"))
            assertEquals("text/event-stream", recorded.getHeader("Accept"))
            assertEquals("request-1", recorded.getHeader("Idempotency-Key"))
            assertEquals("request-1", org.json.JSONObject(recorded.body.readUtf8()).getString("requestId"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun refusesToSendWithoutAnAccessToken() {
        val server = MockWebServer()
        server.start()
        try {
            val dataSource = OkHttpChatRemoteDataSource(
                accessTokenProvider = ChatAccessTokenProvider { null },
                client = OkHttpClient(),
                endpointProvider = { server.url("/api/v1/chat/stream") }
            )
            val result = kotlinx.coroutines.runBlocking {
                dataSource.streamReply(ChatStreamRequest("request-2", null, listOf(
                    ai.zayron.client.ui.chat.ChatMessage("u2", "Hi", ai.zayron.client.ui.chat.ChatRole.USER)
                ))) {}
            }
            assertEquals(NetworkResult.AuthenticationRequired, result)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsDoneSentinelWithoutMessageCompletion() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: [DONE]\n\n")
        )
        server.start()
        try {
            val dataSource = OkHttpChatRemoteDataSource(
                accessTokenProvider = ChatAccessTokenProvider { "token" },
                client = OkHttpClient(),
                endpointProvider = { server.url("/api/v1/chat/stream") }
            )
            val events = mutableListOf<ChatStreamEvent>()
            val result = kotlinx.coroutines.runBlocking {
                dataSource.streamReply(ChatStreamRequest("request-3", null, listOf(
                    ai.zayron.client.ui.chat.ChatMessage("u3", "Hi", ai.zayron.client.ui.chat.ChatRole.USER)
                )), events::add)
            }
            assertEquals(NetworkResult.StreamError("STREAM_ENDED_WITHOUT_COMPLETION"), result)
            assertEquals(listOf(ChatStreamEvent.Done), events)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun returnsHttpErrorsWithoutTreatingThemAsStreamEvents() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"RATE_LIMITED\"}")
        )
        server.start()
        try {
            val dataSource = OkHttpChatRemoteDataSource(
                accessTokenProvider = ChatAccessTokenProvider { "token" },
                client = OkHttpClient(),
                endpointProvider = { server.url("/api/v1/chat/stream") }
            )
            val result = kotlinx.coroutines.runBlocking {
                dataSource.streamReply(ChatStreamRequest("request-4", null, listOf(
                    ai.zayron.client.ui.chat.ChatMessage("u4", "Hi", ai.zayron.client.ui.chat.ChatRole.USER)
                ))) {}
            }
            assertTrue(result is NetworkResult.HttpError)
            assertEquals(429, (result as NetworkResult.HttpError).statusCode)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun returnsUnauthorizedHttpStatusWithoutTreatingItAsStream() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"UNAUTHENTICATED\"}")
        )
        server.start()
        try {
            val dataSource = OkHttpChatRemoteDataSource(
                accessTokenProvider = ChatAccessTokenProvider { "expired-token" },
                client = OkHttpClient(),
                endpointProvider = { server.url("/api/v1/chat/stream") }
            )
            val result = kotlinx.coroutines.runBlocking {
                dataSource.streamReply(ChatStreamRequest("request-401", null, listOf(
                    ai.zayron.client.ui.chat.ChatMessage("u401", "Hi", ai.zayron.client.ui.chat.ChatRole.USER)
                ))) {}
            }
            assertTrue(result is NetworkResult.HttpError)
            assertEquals(401, (result as NetworkResult.HttpError).statusCode)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsSuccessfulResponseWithUnexpectedContentType() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"answer\":\"not an SSE stream\"}")
        )
        server.start()
        try {
            val dataSource = OkHttpChatRemoteDataSource(
                accessTokenProvider = ChatAccessTokenProvider { "token" },
                client = OkHttpClient(),
                endpointProvider = { server.url("/api/v1/chat/stream") }
            )
            val result = kotlinx.coroutines.runBlocking {
                dataSource.streamReply(ChatStreamRequest("request-content-type", null, listOf(
                    ai.zayron.client.ui.chat.ChatMessage("uct", "Hi", ai.zayron.client.ui.chat.ChatRole.USER)
                ))) {}
            }
            assertEquals(NetworkResult.StreamError("UNEXPECTED_CONTENT_TYPE"), result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun coroutineCancellationCancelsTheUnderlyingOkHttpCall() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        val observedCall = AtomicReference<Call?>()
        try {
            val client = OkHttpClient()
            // Capture the exact Call at creation time rather than depending on EventListener timing.
            val callFactory = Call.Factory { request ->
                client.newCall(request).also { observedCall.set(it) }
            }
            val dataSource = OkHttpChatRemoteDataSource(
                accessTokenProvider = ChatAccessTokenProvider { "token" },
                client = client,
                callFactory = callFactory,
                endpointProvider = { server.url("/api/v1/chat/stream") }
            )

            kotlinx.coroutines.runBlocking {
                val requestJob = launch {
                    dataSource.streamReply(ChatStreamRequest("request-cancel", null, listOf(
                        ai.zayron.client.ui.chat.ChatMessage("uc", "Stop this", ai.zayron.client.ui.chat.ChatRole.USER)
                    ))) {}
                }
                assertTrue(
                    "Mock server should receive the request",
                    server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS) != null
                )
                assertTrue("The OkHttp call should have been created", observedCall.get() != null)
                requestJob.cancelAndJoin()
            }

            assertTrue(
                "Coroutine cancellation must cancel the exact OkHttp call created for this request",
                observedCall.get()?.isCanceled() == true
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun interruptedConnectionReturnsConnectionError() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.start()
        try {
            val dataSource = OkHttpChatRemoteDataSource(
                accessTokenProvider = ChatAccessTokenProvider { "token" },
                client = OkHttpClient(),
                endpointProvider = { server.url("/api/v1/chat/stream") }
            )
            val result = kotlinx.coroutines.runBlocking {
                dataSource.streamReply(ChatStreamRequest("request-disconnect", null, listOf(
                    ai.zayron.client.ui.chat.ChatMessage("ud", "Hi", ai.zayron.client.ui.chat.ChatRole.USER)
                ))) {}
            }
            assertEquals(NetworkResult.ConnectionError, result)
        } finally {
            server.shutdown()
        }
    }

}
