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
                    else -> NetworkResult.Success("""{"message":{"id":"00000000-0000-0000-0000-000000000201"}}""", 201)
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
                    else -> NetworkResult.Success("""{"message":{"id":"00000000-0000-0000-0000-000000000202"}}""", 201)
                }
            }
        )

        assertTrue(store.saveUserMessage("A message from a different account") is NetworkResult.Success)
        assertEquals(3, requests.size)
        assertEquals(secondId, ids.read())
    }


    @Test
    fun sendsPrivateAttachmentReferencesWithTheCloudMessage() = runBlocking {
        val conversationId = "00000000-0000-0000-0000-000000000123"
        val requests = mutableListOf<Request>()
        val store = BackendCloudChatStore(
            accessTokenProvider = ChatAccessTokenProvider { "user-session-token" },
            conversationIds = MemoryConversationIds(),
            endpointProvider = { path ->
                HttpUrl.Builder().scheme("https").host("api.zayron.test")
                    .addPathSegments(path).build()
            },
            requestExecutor = { request ->
                requests += request
                if (requests.size == 1) {
                    NetworkResult.Success(
                        """{"conversation":{"id":"$conversationId"}}""", 201
                    )
                } else {
                    NetworkResult.Success("""{"message":{"id":"00000000-0000-0000-0000-000000000202"}}""", 201)
                }
            }
        )
        val attachment = ai.zayron.client.ui.chat.ChatAttachmentReference(
            id = "00000000-0000-0000-0000-000000000010",
            displayName = "notes.pdf",
            mimeType = "application/pdf",
            sizeBytes = 2048,
            storagePath = "00000000-0000-0000-0000-000000000001/0123456789abcdef0123456789abcdef.pdf"
        )

        assertTrue(store.saveUserMessage("Read this", listOf(attachment)) is NetworkResult.Success)
        val body = okio.Buffer()
        requests.last().body!!.writeTo(body)
        val json = org.json.JSONObject(body.readUtf8())
        assertEquals(
            attachment.storagePath,
            json.getJSONArray("attachments").getJSONObject(0).getString("storage_path")
        )
    }


    @Test
    fun returnsServerMessageReceiptAndQueuesDocumentAgainstThatMessage() = runBlocking {
        val conversationId = "00000000-0000-0000-0000-000000000123"
        val messageId = "00000000-0000-0000-0000-000000000201"
        val attachmentId = "00000000-0000-0000-0000-000000000010"
        val jobId = "00000000-0000-0000-0000-000000000301"
        val requests = mutableListOf<Request>()
        val store = BackendCloudChatStore(
            accessTokenProvider = ChatAccessTokenProvider { "user-session-token" },
            conversationIds = MemoryConversationIds(),
            endpointProvider = { path ->
                HttpUrl.Builder().scheme("https").host("api.zayron.test")
                    .addPathSegments(path).build()
            },
            requestExecutor = { request ->
                requests += request
                when (requests.size) {
                    1 -> NetworkResult.Success("""{"conversation":{"id":"$conversationId"}}""", 201)
                    2 -> NetworkResult.Success("""{"message":{"id":"$messageId"}}""", 201)
                    else -> NetworkResult.Success("""{"id":"$jobId","status":"queued"}""", 202)
                }
            }
        )

        val saved = store.saveUserMessageWithReceipt("Review this document")
        assertTrue(saved is NetworkResult.Success)
        val receipt = (saved as NetworkResult.Success<CloudMessageReceipt?>).data!!
        assertEquals(conversationId, receipt.conversationId)
        assertEquals(messageId, receipt.messageId)

        val queued = store.enqueueDocumentJob(receipt, attachmentId)
        assertTrue(queued is NetworkResult.Success)
        assertEquals(jobId, (queued as NetworkResult.Success).data)
        assertEquals("/api/v1/documents/jobs", requests[2].url.encodedPath)
        assertEquals("Bearer user-session-token", requests[2].header("Authorization"))
        val body = okio.Buffer()
        requests[2].body!!.writeTo(body)
        val json = org.json.JSONObject(body.readUtf8())
        assertEquals(conversationId, json.getString("conversation_id"))
        assertEquals(messageId, json.getString("message_id"))
        assertEquals(attachmentId, json.getString("attachment_id"))
    }

    @Test
    fun retrievesAuthenticatedDocumentJobStatusAndExtractedText() = runBlocking {
        val jobId = "00000000-0000-0000-0000-000000000301"
        val requests = mutableListOf<Request>()
        val store = BackendCloudChatStore(
            accessTokenProvider = ChatAccessTokenProvider { "user-session-token" },
            conversationIds = MemoryConversationIds(),
            endpointProvider = { path ->
                HttpUrl.Builder().scheme("https").host("api.zayron.test")
                    .addPathSegments(path).build()
            },
            requestExecutor = { request ->
                requests += request
                NetworkResult.Success(
                    """{"id":"$jobId","status":"succeeded","error_code":null,"result":{"extracted_text":"Private document contents"}}""",
                    200
                )
            }
        )

        val result = store.getDocumentJobStatus(jobId)
        assertTrue(result is NetworkResult.Success)
        val status = (result as NetworkResult.Success).data
        assertEquals("succeeded", status.status)
        assertEquals("Private document contents", status.extractedText)
        assertEquals("/api/v1/documents/jobs/$jobId", requests.single().url.encodedPath)
        assertEquals("GET", requests.single().method)
        assertEquals("Bearer user-session-token", requests.single().header("Authorization"))
    }

}
