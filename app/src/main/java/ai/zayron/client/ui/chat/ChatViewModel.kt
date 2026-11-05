package ai.zayron.client.ui.chat

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Stores the user's preview messages in app-private local storage.
 * This is device-local history only: it is not synced or sent to an AI service.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(
        "zayron_local_chat",
        Context.MODE_PRIVATE
    )

    val messages = mutableStateListOf<ChatMessage>()

    init {
        restoreConversation()
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
        persistConversation()
    }

    fun clearConversation() {
        messages.clear()
        preferences.edit().remove(KEY_MESSAGES).apply()
    }

    private fun persistConversation() {
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

    private fun restoreConversation() {
        val saved = preferences.getString(KEY_MESSAGES, null) ?: return
        try {
            val encoded = JSONArray(saved)
            for (index in 0 until encoded.length()) {
                val item = encoded.optJSONObject(index) ?: continue
                val text = item.optString("text").trim()
                if (text.isEmpty()) continue
                messages.add(
                    ChatMessage(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        text = text,
                        role = ChatRole.USER
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt local history should never prevent the chat screen from opening.
            preferences.edit().remove(KEY_MESSAGES).apply()
            messages.clear()
        }
    }

    private companion object {
        const val KEY_MESSAGES = "messages_json"
    }
}
