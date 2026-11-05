package ai.zayron.client.data.chat

import ai.zayron.client.ui.chat.ChatMessage

/**
 * Persistence boundary for chat history.
 *
 * The current implementation is device-local. A future authenticated backend
 * repository can implement this contract without coupling network code to UI.
 */
interface ChatRepository {
    fun loadMessages(): List<ChatMessage>
    fun saveMessages(messages: List<ChatMessage>)
    fun clearMessages()
}
