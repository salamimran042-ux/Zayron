package ai.zayron.client.ui.chat

enum class ChatRole { USER, ASSISTANT }
enum class DeliveryState { LOCAL_ONLY, PENDING, SENT, FAILED }

/** Private storage reference only; it is not a public URL or an authentication token. */
data class ChatAttachmentReference(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val storagePath: String
)

data class ChatMessage(
    val id: String,
    val text: String,
    val role: ChatRole,
    val deliveryState: DeliveryState = DeliveryState.LOCAL_ONLY,
    val attachments: List<ChatAttachmentReference> = emptyList()
)
