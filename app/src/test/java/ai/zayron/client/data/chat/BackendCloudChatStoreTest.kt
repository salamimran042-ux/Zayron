package ai.zayron.client.data.chat

import ai.zayron.client.data.network.NetworkResult
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendCloudChatStoreTest {

    private class MemoryConversationIds : CloudConversationIdStore {
        private var value: String? = null
        override fun read(): String? = value
        override fun write(id: String) { value = id }
        override fun clear() { value = null }
    }

    @Test
    fun createsConversationThenPersistsUserMessageAndReusesConversation() = runBlocking {
        val conversationId = "00000000-0000-0000-0000-000000000123"
        val requests = mutableListOf<Request>()
        val ids = MemoryConversationIds()
        val store = BackendCloudChatStore(
            accessTokenProvider = ChatAccessTokenProvider { "user-session-token" },
            conversationIds = ids,
            endpointProvider = { path ->
                HttpUrl.Builder()
                    .scheme("https")
                    .host("api.zayron.test")
                    .addPathSegments(path)
                    .build()
            },
            requestExecutor = { request ->
                requests += request
                when (requests.size) {
                    1 -> NetworkResult.Success(
                        """{"conversation":{"id":"$conversationId"}}""",
                        201
                    )
                    else -> NetworkResult.Success("""{"message":{"id":"message-1"}}""", 201)
                }
            }
        )

        assertTrue(store.saveUserMessage("Hello Zayron") is NetworkResult.Success)
        assertTrue(store.saveUserMessage("A second message") is NetworkResult.Success)

        assertEquals(3, requests.size)
        assertEquals("/api/v1/cloud/conversations", requests[0].url.encodedPath)
        assertEquals("/api/v1/cloud/messages", requests[1].url.encodedPath)
        assertEquals("/api/v1/cloud/messages", requests[2].url.encodedPath)
        requests.forEach { request ->
            assertEquals("Bearer user-session-token", request.header("Authorization"))
        }
        assertEquals(conversationId, ids.read())
        val messageBody = okio.Buffer()
        requests[1].body!!.writeTo(messageBody)
        assertEquals(
            "Hello Zayron",
            org.json.JSONObject(messageBody.readUtf8()).getString("content")
        )
    }

    @Test
    fun refusesToSaveWithoutAnAuthenticatedSession() = runBlocking {
        var requestCount = 0
        val store = BackendCloudChatStore(
            accessTokenProvider = ChatAccessTokenProvider { null },
            conversationIds = MemoryConversationIds(),
            endpointProvider = { null },
            requestExecutor = {
                requestCount++
                NetworkResult.ConnectionError
            }
        )

        assertEquals(
            NetworkResult.AuthenticationRequired,
            store.saveUserMessage("Private message")
        )
        assertEquals(0, requestCount)
    }

    @Test
    fun startsANewCloudConversationWhenCurrentConversationIsNotOwned() = runBlocking {
        val firstId = "00000000-0000-0000-0000-000000000111"
        val secondId = "00000000-0000-0000-0000-000000000222"
        val ids = MemoryConversationIds().apply { write(firstId) }
        val requests = mutableListOf<Request>()
        val store = BackendCloudChatStore(
            accessTokenProvider = ChatAccessTokenProvider { "another-user-token" },
            conversationIds = ids,
            endpointProvider = { path ->
                HttpUrl.Builder().scheme("https").host("api.zayron.test")
                    .addPathSegments(path).build()
            },
            requestExecutor = { request ->
                requests += request
                when (requests.size) {
                    1 -> NetworkResult.HttpError(404, """{"error":"conversation_not_found"}""")
                    2 -> NetworkResult.Success(
                        """{"conversation":{"id":"$secondId"}}""", 201
                    )
                    else -> NetworkResult.Success("""{"message":{"id":"message-2"}}""", 201)
                }
            }
        )

        assertTrue(store.saveUserMessage("A message from a different account") is NetworkResult.Success)
        assertEquals(3, requests.size)
        assertEquals(secondId, ids.read())
    }
}
