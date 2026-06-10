package com.example.data.repository

import com.example.core.db.dao.MessageFeedbackDao
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.domain.repository.MessageFeedbackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageFeedbackRepositoryImpl @Inject constructor(
    private val messageFeedbackDao: MessageFeedbackDao,
) : MessageFeedbackRepository {
    override fun getRecent(limit: Int): Flow<List<MessageFeedbackEntity>> =
        messageFeedbackDao.getRecent(limit)

    override suspend fun getByPendingMessage(pendingMessageId: String): List<MessageFeedbackEntity> =
        messageFeedbackDao.getByPendingMessage(pendingMessageId)

    override suspend fun getLatestForPendingMessage(pendingMessageId: String): MessageFeedbackEntity? =
        messageFeedbackDao.getLatestForPendingMessage(pendingMessageId)

    override suspend fun record(feedback: MessageFeedbackEntity) =
        messageFeedbackDao.insert(feedback)

    override suspend fun markApplied(id: String) =
        messageFeedbackDao.markApplied(id)
}
