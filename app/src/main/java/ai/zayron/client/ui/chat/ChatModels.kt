package ai.zayron.client.ui.chat

enum class ChatRole {
    USER
}

enum class DeliveryState {
    LOCAL_ONLY
}

data class ChatMessage(
    val id: String,
    val text: String,
    val role: ChatRole,
    val deliveryState: DeliveryState = DeliveryState.LOCAL_ONLY
)
