package ai.zayron.client.ui.chat

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import java.util.UUID

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()

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
    }

    fun clearConversation() {
        messages.clear()
    }
}
