package ai.zayron.client.data.chat

import ai.zayron.client.ui.chat.ChatMessage
import ai.zayron.client.ui.chat.ChatRole
import ai.zayron.client.ui.chat.DeliveryState
import org.json.JSONArray
import org.json.JSONObject

/** Proposed contract for POST /api/v1/chat/stream; no deployed service is implied. */
data class ChatStreamRequest(val requestId: String, val conversationId: String?, val messages: List<ChatMessage>) {
    fun toJson(): String {
        val items = JSONArray()
        messages.filter { it.text.isNotBlank() }.forEach { message ->
            items.put(JSONObject().put("role", message.role.name.lowercase()).put("content", message.text))
        }
        return JSONObject().put("requestId", requestId)
            .put("conversationId", conversationId ?: JSONObject.NULL)
            .put("messages", items).toString()
    }
}


/** Builds the only conversation context allowed to leave the device. */
fun chatMessagesForRemoteRequest(
    history: List<ChatMessage>,
    currentUserMessage: ChatMessage
): List<ChatMessage> {
    require(currentUserMessage.role == ChatRole.USER) { "The current chat message must be from the user." }
    return history.filter { it.deliveryState == DeliveryState.SENT } + currentUserMessage
}

sealed class ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent()
    data class Completed(val messageId: String?) : ChatStreamEvent()
    data class Failed(val code: String?) : ChatStreamEvent()
    /** Protocol sentinel only; it is not proof that the assistant message completed. */
    data object Done : ChatStreamEvent()
}

/** Parses a single complete SSE event; unknown and malformed events are ignored. */
object ChatStreamEventParser {
    fun parse(eventName: String?, data: String): ChatStreamEvent? {
        val type = eventName?.trim()?.lowercase().orEmpty()
        val payload = data.trim()
        if (payload.isEmpty()) return null
        if (payload == "[DONE]") return ChatStreamEvent.Done
        val json = try { JSONObject(payload) } catch (_: Exception) { return null }
        return when (type) {
            "message.delta" -> json.optString("delta").takeIf { it.isNotEmpty() }?.let { ChatStreamEvent.Delta(it) }
            "message.completed" -> ChatStreamEvent.Completed(json.optString("messageId").takeIf { it.isNotBlank() })
            "error" -> ChatStreamEvent.Failed(json.optString("code").takeIf { it.isNotBlank() })
            else -> null
        }
    }
}

/** Future authenticated network boundary; success requires a terminal completion event. */
interface ChatRemoteDataSource {
    suspend fun streamReply(
        request: ChatStreamRequest,
        onEvent: (ChatStreamEvent) -> Unit
    ): ai.zayron.client.data.network.NetworkResult<Unit>
}
