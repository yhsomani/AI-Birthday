package com.example.domain.repository

import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.MessageFeedbackId
import com.example.domain.model.message.MessageFeedbackRecord
import kotlinx.coroutines.flow.Flow

interface MessageFeedbackRepository {
    fun getRecent(limit: Int): Flow<List<MessageFeedbackRecord>>
    suspend fun getByPendingMessage(pendingMessageId: MessageDraftId): List<MessageFeedbackRecord>
    suspend fun getLatestForPendingMessage(pendingMessageId: MessageDraftId): MessageFeedbackRecord?
    suspend fun record(feedback: MessageFeedbackRecord)
    suspend fun markApplied(id: MessageFeedbackId)
}
