package ai.zayron.client.data.chat

import ai.zayron.client.ui.chat.ChatMessage
import ai.zayron.client.ui.chat.ChatRole
import ai.zayron.client.ui.chat.DeliveryState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStreamContractTest {
    @Test fun requestSerializesWireRolesAndContent() {
        val request = ChatStreamRequest("req-1", "conv-1", listOf(
            ChatMessage("u", "Hello", ChatRole.USER), ChatMessage("a", "Hi", ChatRole.ASSISTANT)
        ))
        val json = JSONObject(request.toJson())
        val messages = json.getJSONArray("messages")
        assertEquals("req-1", json.getString("requestId"))
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("Hello", messages.getJSONObject(0).getString("content"))
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
        assertEquals(false, messages.getJSONObject(0).has("deliveryState"))
    }

    @Test fun nullConversationAndBlankMessagesAreHandled() {
        val request = ChatStreamRequest("req-2", null, listOf(
            ChatMessage("blank", "  ", ChatRole.USER), ChatMessage("ok", "Keep", ChatRole.USER)
        ))
        val json = JSONObject(request.toJson())
        assertEquals(true, json.isNull("conversationId"))
        assertEquals(1, json.getJSONArray("messages").length())
    }


    @Test fun remoteRequestContextIncludesOnlyPreviouslySentMessagesAndCurrentUserMessage() {
        val sentUser = ChatMessage("sent-user", "Earlier question", ChatRole.USER, DeliveryState.SENT)
        val sentAssistant = ChatMessage("sent-assistant", "Earlier answer", ChatRole.ASSISTANT, DeliveryState.SENT)
        val localOnly = ChatMessage("local", "Private draft", ChatRole.USER, DeliveryState.LOCAL_ONLY)
        val failed = ChatMessage("failed", "Failed attempt", ChatRole.USER, DeliveryState.FAILED)
        val pending = ChatMessage("pending", "Interrupted response", ChatRole.ASSISTANT, DeliveryState.PENDING)
        val current = ChatMessage("current", "New question", ChatRole.USER, DeliveryState.PENDING)

        assertEquals(
            listOf(sentUser, sentAssistant, current),
            chatMessagesForRemoteRequest(
                listOf(sentUser, sentAssistant, localOnly, failed, pending),
                current
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun remoteRequestContextRejectsAssistantAsCurrentMessage() {
        chatMessagesForRemoteRequest(
            emptyList(),
            ChatMessage("assistant", "Not a user request", ChatRole.ASSISTANT)
        )
    }

    @Test fun parsesDelta() {
        assertEquals(ChatStreamEvent.Delta("Hello"),
            ChatStreamEventParser.parse("message.delta", """{"delta":"Hello"}"""))
    }

    @Test fun parsesCompletion() {
        assertEquals(ChatStreamEvent.Completed("server-id"),
            ChatStreamEventParser.parse("message.completed", """{"messageId":"server-id"}"""))
    }

    @Test fun parsesSafeErrorCodeOnly() {
        assertEquals(ChatStreamEvent.Failed("SERVICE_UNAVAILABLE"),
            ChatStreamEventParser.parse("error", """{"code":"SERVICE_UNAVAILABLE","debug":"private"}"""))
    }

    @Test fun doneSentinelIsNotCompletion() {
        assertEquals(ChatStreamEvent.Done, ChatStreamEventParser.parse(null, "[DONE]"))
    }

    @Test fun ignoresMalformedAndUnknownEvents() {
        assertNull(ChatStreamEventParser.parse("message.delta", "not-json"))
        assertNull(ChatStreamEventParser.parse("message.other", """{"delta":"ignored"}"""))
    }
}
