package com.zeno.assistant.core.model

/**
 * UI-independent representation of one conversation message.
 * Timestamps are Unix milliseconds so they can be persisted consistently.
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val createdAtMillis: Long,
    val deliveryState: DeliveryState = DeliveryState.COMPLETE
) {
    init {
        require(id.isNotBlank()) { "Message id must not be blank" }
        require(content.isNotBlank()) { "Message content must not be blank" }
    }

    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }

    enum class DeliveryState {
        PENDING,
        COMPLETE,
        FAILED
    }
}