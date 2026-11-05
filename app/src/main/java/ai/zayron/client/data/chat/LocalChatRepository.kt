package ai.zayron.client.data.chat

import android.content.Context
import ai.zayron.client.ui.chat.ChatMessage
import ai.zayron.client.ui.chat.ChatRole
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * App-private, device-local chat history.
 * Messages are not encrypted backup, cloud-synced, or sent to an AI service.
 */
class LocalChatRepository(context: Context) : ChatRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun loadMessages(): List<ChatMessage> {
        val saved = preferences.getString(KEY_MESSAGES, null) ?: return emptyList()

        return try {
            val encoded = JSONArray(saved)
            buildList {
                for (index in 0 until encoded.length()) {
                    val item = encoded.optJSONObject(index) ?: continue
                    val text = item.optString("text").trim()
                    if (text.isEmpty()) continue

                    add(
                        ChatMessage(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            text = text,
                            role = ChatRole.USER
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Invalid local data must never prevent the chat workspace from opening.
            preferences.edit().remove(KEY_MESSAGES).apply()
            emptyList()
        }
    }

    override fun saveMessages(messages: List<ChatMessage>) {
        val encoded = JSONArray()
        messages.forEach { message ->
            encoded.put(
                JSONObject()
                    .put("id", message.id)
                    .put("text", message.text)
                    .put("role", message.role.name)
            )
        }
        preferences.edit().putString(KEY_MESSAGES, encoded.toString()).apply()
    }

    override fun clearMessages() {
        preferences.edit().remove(KEY_MESSAGES).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "zayron_local_chat"
        const val KEY_MESSAGES = "messages_json"
    }
}
