package com.example.data.repository

import com.example.core.db.dao.MessageFeedbackDao
import com.example.domain.message.toEntity
import com.example.domain.message.toRecord
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.MessageFeedbackId
import com.example.domain.model.message.MessageFeedbackRecord
import com.example.domain.repository.MessageFeedbackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageFeedbackRepositoryImpl @Inject constructor(
    private val messageFeedbackDao: MessageFeedbackDao,
) : MessageFeedbackRepository {
    override fun getRecent(limit: Int): Flow<List<MessageFeedbackRecord>> =
        messageFeedbackDao.getRecent(limit).map { rows ->
            rows.map { it.toRecord() }
        }

    override suspend fun getByPendingMessage(pendingMessageId: MessageDraftId): List<MessageFeedbackRecord> =
        messageFeedbackDao.getByPendingMessage(pendingMessageId.value).map { it.toRecord() }

    override suspend fun getLatestForPendingMessage(pendingMessageId: MessageDraftId): MessageFeedbackRecord? =
        messageFeedbackDao.getLatestForPendingMessage(pendingMessageId.value)?.toRecord()

    override suspend fun record(feedback: MessageFeedbackRecord) =
        messageFeedbackDao.insert(feedback.toEntity())

    override suspend fun markApplied(id: MessageFeedbackId) =
        messageFeedbackDao.markApplied(id.value)
}
