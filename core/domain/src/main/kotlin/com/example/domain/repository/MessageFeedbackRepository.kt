package com.example.domain.repository

import com.example.core.db.entities.MessageFeedbackEntity
import kotlinx.coroutines.flow.Flow

interface MessageFeedbackRepository {
    fun getRecent(limit: Int): Flow<List<MessageFeedbackEntity>>
    suspend fun getByPendingMessage(pendingMessageId: String): List<MessageFeedbackEntity>
    suspend fun getLatestForPendingMessage(pendingMessageId: String): MessageFeedbackEntity?
    suspend fun record(feedback: MessageFeedbackEntity)
    suspend fun markApplied(id: String)
}
