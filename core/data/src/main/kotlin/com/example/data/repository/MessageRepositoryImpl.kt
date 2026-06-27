package com.example.data.repository

import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.dao.saveMessageStatusUpdate
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.message.toMessageAnalyticsRecord
import com.example.domain.message.toMessageApprovalState
import com.example.domain.message.toMessageDispatchState
import com.example.domain.message.toMessageGenerationHistory
import com.example.domain.message.toPendingMessageListItems
import com.example.domain.message.toRetryableMessageDraft
import com.example.domain.message.toSentMessageListItems
import com.example.domain.message.toWishPreviewDraft
import com.example.domain.message.toWishPreviewReviewItem
import com.example.domain.model.message.MessageApprovalState
import com.example.domain.model.message.MessageAnalyticsRecord
import com.example.domain.model.message.MessageDispatchState
import com.example.domain.model.message.MessageGenerationHistory
import com.example.domain.model.message.MessageStatusUpdate
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.RetryQueuedMessageDraft
import com.example.domain.model.message.RetryableMessageDraft
import com.example.domain.model.message.SentMessageListItem
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewReviewItem
import com.example.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao
) : MessageRepository {

    override fun getAllPending(): Flow<List<PendingMessageEntity>> = pendingMessageDao.getAll()

    override fun getPendingListItems(): Flow<List<PendingMessageListItem>> {
        return pendingMessageDao.getAll().map { messages ->
            messages.toPendingMessageListItems()
        }
    }

    override fun getWishPreviewReviewQueue(): Flow<List<WishPreviewReviewItem>> {
        return pendingMessageDao.getAll().map { messages ->
            messages.map { it.toWishPreviewReviewItem() }
        }
    }

    override suspend fun getAllApproved(): List<PendingMessageEntity> = pendingMessageDao.getAllApproved()

    override suspend fun getPendingById(id: String): PendingMessageEntity? = pendingMessageDao.getById(id)

    override suspend fun getMessageApprovalStateById(id: String): MessageApprovalState? {
        return pendingMessageDao.getById(id)?.toMessageApprovalState()
    }

    override suspend fun getRetryableMessageDraftById(id: String): RetryableMessageDraft? {
        return pendingMessageDao.getById(id)?.toRetryableMessageDraft()
    }

    override suspend fun getMessageDispatchStateById(id: String): MessageDispatchState? {
        return pendingMessageDao.getById(id)?.toMessageDispatchState()
    }

    override suspend fun getPendingByEventId(eventId: String): PendingMessageEntity? = pendingMessageDao.getByEventId(eventId)

    override suspend fun getMessageDispatchStateByEventId(eventId: String): MessageDispatchState? {
        return pendingMessageDao.getByEventId(eventId)?.toMessageDispatchState()
    }

    override suspend fun getWishPreviewDraftById(id: String): WishPreviewDraft? {
        return pendingMessageDao.getById(id)?.toWishPreviewDraft()
    }

    override suspend fun getWishPreviewDraftByEventId(eventId: String): WishPreviewDraft? {
        return pendingMessageDao.getByEventId(eventId)?.toWishPreviewDraft()
    }

    override suspend fun getPendingForEventOccurrence(
        contactId: String,
        eventId: String,
        scheduledYear: Int
    ): PendingMessageEntity? = pendingMessageDao.getPendingMessage(contactId, eventId, scheduledYear)

    override suspend fun pendingExistsForEvent(eventId: String): Boolean = pendingMessageDao.existsForEvent(eventId)

    override suspend fun pendingExistsForEventOccurrence(
        contactId: String,
        eventId: String,
        scheduledYear: Int
    ): Boolean = pendingMessageDao.existsForEventOccurrence(contactId, eventId, scheduledYear)

    override suspend fun insertPending(message: PendingMessageEntity) = pendingMessageDao.insert(message)

    override suspend fun saveMessageApprovalState(state: MessageApprovalState) {
        pendingMessageDao.updateApprovalState(
            id = state.id.value,
            status = state.status.raw,
            selectedVariantText = state.selectedVariantText,
            editedByUser = state.editedByUser,
            userEditedText = state.userEditedText,
        )
    }

    override suspend fun saveRetryQueuedMessageDraft(state: RetryQueuedMessageDraft) {
        pendingMessageDao.updateRetryState(
            id = state.id.value,
            status = state.status.raw,
            scheduledForMs = state.scheduledForMs,
        )
    }

    override suspend fun saveMessageStatusUpdate(update: MessageStatusUpdate) {
        pendingMessageDao.saveMessageStatusUpdate(update)
    }

    override suspend fun updatePendingStatus(id: String, status: String) = pendingMessageDao.updateStatus(id, status)

    override suspend fun updatePendingStatusByEventId(eventId: String, status: String) = pendingMessageDao.updateStatusByEventId(eventId, status)

    override fun getAllSent(): Flow<List<SentMessageEntity>> = sentMessageDao.getAll()

    override fun getSentListItems(): Flow<List<SentMessageListItem>> {
        return sentMessageDao.getAll().map { messages ->
            messages.toSentMessageListItems()
        }
    }

    override suspend fun getSentByContact(contactId: String, limit: Int): List<SentMessageEntity> = sentMessageDao.getByContact(contactId, limit)

    override suspend fun getGenerationHistoryByContact(contactId: String, limit: Int): MessageGenerationHistory {
        return sentMessageDao.getByContact(contactId, limit).toMessageGenerationHistory()
    }

    override suspend fun getRecentForStyleAnalysis(sinceMs: Long, limit: Int): List<SentMessageEntity> = sentMessageDao.getRecentForStyleAnalysis(sinceMs, limit)

    override suspend fun getSentSinceYearStart(yearStartMs: Long): List<SentMessageEntity> = sentMessageDao.getSentSinceYearStart(yearStartMs)

    override suspend fun getSentAnalyticsRecordsSince(sinceMs: Long): List<MessageAnalyticsRecord> {
        return sentMessageDao.getSentSinceYearStart(sinceMs).map { it.toMessageAnalyticsRecord() }
    }

    override fun countAllSent(): Flow<Int> = sentMessageDao.countAll()

    override fun countPending(): Flow<Int> = pendingMessageDao.countPending()

    override suspend fun insertSent(message: SentMessageEntity) = sentMessageDao.insert(message)
}
