package ai.zayron.client.data.chat

import ai.zayron.client.data.network.NetworkResult
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpChatRemoteDataSourceTest {

    @Test
    fun streamsEventsAndSendsBearerToken() {
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
                    ChatStreamRequest("request-1", null, emptyList()),
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
                dataSource.streamReply(ChatStreamRequest("request-2", null, emptyList())) {}
            }

            assertEquals(NetworkResult.AuthenticationRequired, result)
            assertEquals(0, server.requestCount)
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
                dataSource.streamReply(ChatStreamRequest("request-3", null, emptyList())) {}
            }

            assertTrue(result is NetworkResult.HttpError)
            assertEquals(429, (result as NetworkResult.HttpError).statusCode)
        } finally {
            server.shutdown()
        }
    }
}
