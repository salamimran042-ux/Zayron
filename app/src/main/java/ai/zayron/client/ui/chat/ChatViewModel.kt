package ai.zayron.client.ui.chat

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.zayron.client.data.auth.AndroidKeystoreSessionStore
import ai.zayron.client.data.auth.SecureChatAccessTokenProvider
import ai.zayron.client.data.chat.ChatRemoteDataSource
import ai.zayron.client.data.chat.AttachmentUploadClient
import ai.zayron.client.data.chat.CloudChatStore
import ai.zayron.client.data.chat.BackendCloudChatStore
import ai.zayron.client.data.chat.SharedPreferencesCloudConversationIdStore
import ai.zayron.client.data.chat.ChatStreamEvent
import ai.zayron.client.data.chat.ChatStreamRequest
import ai.zayron.client.data.chat.chatMessagesForRemoteRequest
import ai.zayron.client.data.chat.LocalChatRepository
import ai.zayron.client.data.chat.OkHttpChatRemoteDataSource
import ai.zayron.client.data.chat.ChatRepository
import ai.zayron.client.data.network.ApiErrorMapper
import ai.zayron.client.data.network.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    ),
    private val cloudChatStore: CloudChatStore = BackendCloudChatStore(
        accessTokenProvider = SecureChatAccessTokenProvider(
            AndroidKeystoreSessionStore(application)
        ),
        conversationIds = SharedPreferencesCloudConversationIdStore(application)
    ),
    private val attachmentUploadClient: AttachmentUploadClient = AttachmentUploadClient(
        application.contentResolver,
        SecureChatAccessTokenProvider(AndroidKeystoreSessionStore(application))
    )
) : AndroidViewModel(application) {

    val messages = mutableStateListOf<ChatMessage>()
    data class PendingAttachment(
        val localId: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long?,
        val progress: Float = 0f,
        val uploaded: ChatAttachmentReference? = null,
        val isUploading: Boolean = true,
        val error: String? = null
    )
    val pendingAttachments = mutableStateListOf<PendingAttachment>()
    private val attachmentUris = mutableMapOf<String, Uri>()
    private val _serviceMessage = mutableStateOf<String?>(null)
    val serviceMessage: String? get() = _serviceMessage.value
    private val _isSending = mutableStateOf(false)
    val isSending: Boolean get() = _isSending.value

    private var sendJob: kotlinx.coroutines.Job? = null
    private var activeRequestId: String? = null

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

    fun addAttachments(uris: List<Uri>) {
        uris.distinct().take((5 - pendingAttachments.size).coerceAtLeast(0)).forEach { uri ->
            val descriptor = attachmentUploadClient.describe(uri)
            if (descriptor == null) {
                _serviceMessage.value = "That file type is unsupported, unavailable, or larger than 10 MB."
                return@forEach
            }
            val localId = UUID.randomUUID().toString()
            pendingAttachments.add(
                PendingAttachment(localId, descriptor.displayName, descriptor.mimeType, descriptor.sizeBytes)
            )
            uploadPendingAttachment(localId, uri)
        }
    }

    fun retryAttachment(localId: String) {
        val item = pendingAttachments.firstOrNull { it.localId == localId } ?: return
        if (item.isUploading || item.uploaded != null) return
        val uri = attachmentUris[localId] ?: return
        updatePending(localId) { it.copy(isUploading = true, progress = 0f, error = null) }
        uploadPendingAttachment(localId, uri)
    }

    fun removeAttachment(localId: String) {
        pendingAttachments.removeAll { it.localId == localId }
        attachmentUris.remove(localId)
    }

    fun clearPendingAttachments() {
        pendingAttachments.clear()
        attachmentUris.clear()
    }

    private fun uploadPendingAttachment(localId: String, uri: Uri) {
        attachmentUris[localId] = uri
        viewModelScope.launch {
            when (val result = attachmentUploadClient.upload(uri) { progress ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    updatePending(localId) { it.copy(progress = progress) }
                }
            }) {
                is NetworkResult.Success -> {
                    val uploaded = result.data
                    updatePending(localId) {
                        it.copy(
                            progress = 1f,
                            uploaded = ChatAttachmentReference(
                                uploaded.id, uploaded.displayName, uploaded.mimeType,
                                uploaded.sizeBytes, uploaded.storagePath
                            ),
                            isUploading = false,
                            error = null
                        )
                    }
                }
                else -> updatePending(localId) {
                    it.copy(
                        isUploading = false,
                        error = ApiErrorMapper.userMessage(result) ?: "Upload failed. Please retry."
                    )
                }
            }
        }
    }

    private fun updatePending(
        localId: String,
        transform: (PendingAttachment) -> PendingAttachment
    ) {
        val index = pendingAttachments.indexOfFirst { it.localId == localId }
        if (index >= 0) pendingAttachments[index] = transform(pendingAttachments[index])
    }

    fun sendMessage(rawText: String, attachments: List<ChatAttachmentReference> = emptyList()) {
        val cleanText = rawText.trim()
        if ((cleanText.isEmpty() && attachments.isEmpty()) || isSending) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = cleanText,
            role = ChatRole.USER,
            deliveryState = DeliveryState.PENDING,
            attachments = attachments
        )
        messages.add(userMessage)
        persistMessages()
        _serviceMessage.value = null

        // Include only history already acknowledged by the backend. A local preview
        // or a failed attempt must never be uploaded as hidden conversation context.
        val requestMessages = chatMessagesForRemoteRequest(messages.toList(), userMessage)

        val requestGeneration = UUID.randomUUID().toString()
        activeRequestId = requestGeneration
        _isSending.value = true
        sendJob = viewModelScope.launch {
            val assistantId = UUID.randomUUID().toString()
            try {
            val cloudSaveResult = try {
                cloudChatStore.saveUserMessageWithReceipt(cleanText, attachments)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                NetworkResult.ConnectionError
            }
            var documentQueueNotice: String? = null
            val savedReceipt = (cloudSaveResult as? NetworkResult.Success)?.data
            if (savedReceipt != null) {
                val documentAttachments = attachments.filter {
                    it.mimeType in setOf(
                        "application/pdf", "text/plain", "text/markdown",
                        "text/csv", "application/json"
                    )
                }
                if (documentAttachments.isNotEmpty()) {
                    var queued = 0
                    var failed = 0
                    for (attachment in documentAttachments) {
                        when (cloudChatStore.enqueueDocumentJob(savedReceipt, attachment.id)) {
                            is NetworkResult.Success -> queued++
                            else -> failed++
                        }
                    }
                    documentQueueNotice = when {
                        queued > 0 && failed == 0 ->
                            "Your document(s) were securely queued for text extraction."
                        queued > 0 ->
                            "$queued document(s) queued; $failed could not be queued."
                        else ->
                            "Your message was saved, but document extraction could not be queued."
                    }
                }
            }

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
            val cloudSaved = cloudSaveResult is NetworkResult.Success
            val finalState = when {
                result is NetworkResult.Success || cloudSaved -> DeliveryState.SENT
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

            _serviceMessage.value = when {
                result is NetworkResult.Success && !cloudSaved ->
                    "Your AI response completed, but cloud chat history could not be saved."
                result is NetworkResult.Success -> documentQueueNotice
                cloudSaved ->
                    "Your message is saved in cloud history, but the AI response service is not connected yet."
                else -> ApiErrorMapper.userMessage(result)
            }
            persistMessages()
            } finally {
                // Cancellation must never leave the composer permanently locked or
                // make an interrupted message look successfully delivered.
                val pendingUserIndex = messages.indexOfFirst {
                    it.id == userMessage.id && it.deliveryState == DeliveryState.PENDING
                }
                if (pendingUserIndex >= 0) {
                    messages[pendingUserIndex] = messages[pendingUserIndex].copy(
                        deliveryState = DeliveryState.LOCAL_ONLY
                    )
                }
                val pendingAssistantIndex = messages.indexOfFirst {
                    it.id == assistantId && it.deliveryState == DeliveryState.PENDING
                }
                if (pendingAssistantIndex >= 0) {
                    messages[pendingAssistantIndex] = messages[pendingAssistantIndex].copy(
                        deliveryState = DeliveryState.FAILED
                    )
                }
                persistMessages()
                // A cancelled older request must not unlock or clear the handle
                // belonging to a newer request started after conversation clearing.
                if (activeRequestId == requestGeneration) {
                    activeRequestId = null
                    _isSending.value = false
                    sendJob = null
                }
            }
        }
    }

    fun cancelCurrentRequest() {
        if (!isSending) return
        _serviceMessage.value = "Generation stopped. The unfinished message remains saved on this device."
        sendJob?.cancel()
    }

    fun clearConversation() {
        activeRequestId = null
        sendJob?.cancel()
        sendJob = null
        messages.clear()
        _serviceMessage.value = null
        _isSending.value = false
        chatRepository.clearMessages()
        cloudChatStore.startNewConversation()
        clearPendingAttachments()
    }

    private fun persistMessages() {
        chatRepository.saveMessages(messages.toList())
    }
}
