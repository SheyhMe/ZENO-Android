package com.zeno.assistant.core.data

import com.zeno.assistant.core.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Conversation storage and transport boundary.
 *
 * Implementations may later combine a local cache with the authenticated
 * ZENO backend. Android screens must depend on this contract, not on a
 * provider SDK directly.
 */
interface ChatRepository {
    fun observeMessages(): Flow<List<ChatMessage>>

    suspend fun sendMessage(text: String): Result<ChatMessage>

    suspend fun clearConversation()
}