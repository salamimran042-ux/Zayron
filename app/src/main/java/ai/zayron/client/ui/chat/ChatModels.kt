package ai.zayron.client.ui.chat

/** Speaker identity is part of the stored conversation, not inferred by the UI. */
enum class ChatRole {
    USER,
    ASSISTANT
}

/** Delivery state is explicit so future remote delivery cannot be mistaken for local storage. */
enum class DeliveryState {
    LOCAL_ONLY,
    PENDING,
    SENT,
    FAILED
}

data class ChatMessage(
    val id: String,
    val text: String,
    val role: ChatRole,
    val deliveryState: DeliveryState = DeliveryState.LOCAL_ONLY
)
