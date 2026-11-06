package ai.zayron.client.ui.chat

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.zayron.client.data.auth.AndroidKeystoreSessionStore
import ai.zayron.client.data.auth.SecureChatAccessTokenProvider
import ai.zayron.client.data.chat.ChatRemoteDataSource
import ai.zayron.client.data.chat.ChatStreamEvent
import ai.zayron.client.data.chat.ChatStreamRequest
import ai.zayron.client.data.chat.chatMessagesForRemoteRequest
import ai.zayron.client.data.chat.LocalChatRepository
import ai.zayron.client.data.chat.OkHttpChatRemoteDataSource
import ai.zayron.client.data.chat.ChatRepository
import ai.zayron.client.data.network.ApiErrorMapper
import ai.zayron.client.data.network.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Coordinates local chat history and the authenticated streaming boundary.
 *
 * Only the current message and earlier messages confirmed as SENT are included
 * in a request. Local-only messages are never uploaded implicitly.
 */
class ChatViewModel(
    application: Application,
    private val chatRepository: ChatRepository = LocalChatRepository(application),
    private val remoteDataSource: ChatRemoteDataSource = OkHttpChatRemoteDataSource(
        accessTokenProvider = SecureChatAccessTokenProvider(
            AndroidKeystoreSessionStore(application)
        )
    )
) : AndroidViewModel(application) {

    val messages = mutableStateListOf<ChatMessage>()
    private val _serviceMessage = mutableStateOf<String?>(null)
    val serviceMessage: String? get() = _serviceMessage.value
    private val _isSending = mutableStateOf(false)
    val isSending: Boolean get() = _isSending.value

    private var sendJob: kotlinx.coroutines.Job? = null

    init {
        val restored = chatRepository.loadMessages()
        val normalized = restored.map { message ->
            if (message.deliveryState == DeliveryState.PENDING) {
                message.copy(deliveryState = DeliveryState.LOCAL_ONLY)
            } else {
                message
            }
        }
        messages.addAll(normalized)
        if (normalized != restored) chatRepository.saveMessages(normalized)
    }

    fun sendMessage(rawText: String) {
        val cleanText = rawText.trim()
        if (cleanText.isEmpty() || isSending) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = cleanText,
            role = ChatRole.USER,
            deliveryState = DeliveryState.PENDING
        )
        messages.add(userMessage)
        persistMessages()
        _serviceMessage.value = null

        // Include only history already acknowledged by the backend. A local preview
        // or a failed attempt must never be uploaded as hidden conversation context.
        val requestMessages = chatMessagesForRemoteRequest(messages.toList(), userMessage)

        sendJob = viewModelScope.launch {
            _isSending.value = true
            val assistantId = UUID.randomUUID().toString()
            val events = Channel<ChatStreamEvent>(Channel.UNLIMITED)
            val eventCollector = launch {
                for (event in events) {
                    if (event is ChatStreamEvent.Delta && event.text.isNotEmpty()) {
                        val existingIndex = messages.indexOfFirst { it.id == assistantId }
                        if (existingIndex >= 0) {
                            val existing = messages[existingIndex]
                            messages[existingIndex] = existing.copy(text = existing.text + event.text)
                        } else {
                            messages.add(
                                ChatMessage(
                                    id = assistantId,
                                    text = event.text,
                                    role = ChatRole.ASSISTANT,
                                    deliveryState = DeliveryState.PENDING
                                )
                            )
                        }
                        persistMessages()
                    }
                }
            }

            val result = try {
                remoteDataSource.streamReply(
                    request = ChatStreamRequest(
                        requestId = UUID.randomUUID().toString(),
                        conversationId = null,
                        messages = requestMessages
                    ),
                    onEvent = { event -> events.trySend(event) }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                NetworkResult.ConnectionError
            } finally {
                events.close()
                eventCollector.join()
            }

            val userIndex = messages.indexOfFirst { it.id == userMessage.id }
            val assistantIndex = messages.indexOfFirst { it.id == assistantId }
            val localOnly = result == NetworkResult.NotConfigured ||
                result == NetworkResult.AuthenticationRequired
            val finalState = when {
                result is NetworkResult.Success -> DeliveryState.SENT
                localOnly -> DeliveryState.LOCAL_ONLY
                else -> DeliveryState.FAILED
            }

            if (userIndex >= 0) {
                messages[userIndex] = messages[userIndex].copy(deliveryState = finalState)
            }
            if (assistantIndex >= 0) {
                messages[assistantIndex] = messages[assistantIndex].copy(
                    deliveryState = if (result is NetworkResult.Success) {
                        DeliveryState.SENT
                    } else {
                        DeliveryState.FAILED
                    }
                )
            }

            _serviceMessage.value = ApiErrorMapper.userMessage(result)
            persistMessages()
            _isSending.value = false
        }
    }

    fun clearConversation() {
        sendJob?.cancel()
        sendJob = null
        messages.clear()
        _serviceMessage.value = null
        _isSending.value = false
        chatRepository.clearMessages()
    }

    private fun persistMessages() {
        chatRepository.saveMessages(messages.toList())
    }
}
