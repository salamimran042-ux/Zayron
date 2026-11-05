package ai.zayron.client.ui.chat

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import ai.zayron.client.data.chat.ChatRepository
import ai.zayron.client.data.chat.LocalChatRepository
import java.util.UUID

/**
 * Coordinates the chat preview UI with a replaceable persistence boundary.
 * Current messages remain local-only until a real backend repository exists.
 */
class ChatViewModel(
    application: Application,
    private val chatRepository: ChatRepository = LocalChatRepository(application)
) : AndroidViewModel(application) {

    val messages = mutableStateListOf<ChatMessage>()

    init {
        messages.addAll(chatRepository.loadMessages())
    }

    fun sendMessage(rawText: String) {
        val cleanText = rawText.trim()
        if (cleanText.isEmpty()) return

        messages.add(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                text = cleanText,
                role = ChatRole.USER
            )
        )
        chatRepository.saveMessages(messages.toList())
    }

    fun clearConversation() {
        messages.clear()
        chatRepository.clearMessages()
    }
}
