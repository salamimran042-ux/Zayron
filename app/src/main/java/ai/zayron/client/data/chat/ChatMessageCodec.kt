package ai.zayron.client.data.chat

import ai.zayron.client.ui.chat.ChatMessage
import ai.zayron.client.ui.chat.ChatRole
import ai.zayron.client.ui.chat.DeliveryState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Stable JSON representation for the local conversation.
 * Unknown enum values fall back safely so an app update cannot strand old history.
 */
object ChatMessageCodec {

    fun encode(messages: List<ChatMessage>): String {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("text", message.text)
                    .put("role", message.role.name)
                    .put("deliveryState", message.deliveryState.name)
            )
        }
        return array.toString()
    }

    fun decode(serialized: String?): List<ChatMessage> {
        if (serialized.isNullOrBlank()) return emptyList()

        return try {
            val array = JSONArray(serialized)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val text = item.optString("text")
                    if (text.isBlank()) continue

                    val role = enumValueOrDefault(
                        item.optString("role"),
                        ChatRole.USER
                    )
                    val deliveryState = enumValueOrDefault(
                        item.optString("deliveryState"),
                        DeliveryState.LOCAL_ONLY
                    )

                    add(
                        ChatMessage(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            text = text,
                            role = role,
                            deliveryState = deliveryState
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        default: T
    ): T = try {
        enumValueOf<T>(value)
    } catch (_: IllegalArgumentException) {
        default
    }
}
