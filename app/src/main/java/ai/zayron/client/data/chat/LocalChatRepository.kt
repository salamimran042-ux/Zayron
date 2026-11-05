package ai.zayron.client.data.chat

import android.content.Context
import ai.zayron.client.ui.chat.ChatMessage

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
        val messages = ChatMessageCodec.decode(saved)

        if (messages.isEmpty() && saved.isNotBlank()) {
            // Invalid local data must never prevent the chat workspace from opening.
            preferences.edit().remove(KEY_MESSAGES).apply()
        }
        return messages
    }

    override fun saveMessages(messages: List<ChatMessage>) {
        preferences.edit()
            .putString(KEY_MESSAGES, ChatMessageCodec.encode(messages))
            .apply()
    }

    override fun clearMessages() {
        preferences.edit().remove(KEY_MESSAGES).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "zayron_local_chat"
        const val KEY_MESSAGES = "messages_json"
    }
}
