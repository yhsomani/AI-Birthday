package com.example.data.repository

import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.dao.saveMessageStatusUpdate
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
import com.example.domain.model.message.PendingMessageRecord
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.RetryQueuedMessageDraft
import com.example.domain.model.message.RetryableMessageDraft
import com.example.domain.model.message.SentMessageRecord
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

    override fun getAllPending(): Flow<List<PendingMessageRecord>> {
        return pendingMessageDao.getAll().map { messages ->
            messages.toPendingMessageRecords()
        }
    }

    override fun getPendingListItems(): Flow<List<PendingMessageListItem>> {
        return pendingMessageDao.getAll().map { messages ->
            messages.toPendingMessageRecords().toPendingMessageListItems()
        }
    }

    override fun getWishPreviewReviewQueue(): Flow<List<WishPreviewReviewItem>> {
        return pendingMessageDao.getAll().map { messages ->
            messages.map { it.toPendingMessageRecord().toWishPreviewReviewItem() }
        }
    }

    override suspend fun getAllPendingSync(): List<PendingMessageRecord> {
        return pendingMessageDao.getAllSync().toPendingMessageRecords()
    }

    override suspend fun getAllApproved(): List<PendingMessageRecord> {
        return pendingMessageDao.getAllApproved().toPendingMessageRecords()
    }

    override suspend fun getPendingById(id: String): PendingMessageRecord? {
        return pendingMessageDao.getById(id)?.toPendingMessageRecord()
    }

    override suspend fun getMessageApprovalStateById(id: String): MessageApprovalState? {
        return pendingMessageDao.getById(id)?.toPendingMessageRecord()?.toMessageApprovalState()
    }

    override suspend fun getRetryableMessageDraftById(id: String): RetryableMessageDraft? {
        return pendingMessageDao.getById(id)?.toPendingMessageRecord()?.toRetryableMessageDraft()
    }

    override suspend fun getMessageDispatchStateById(id: String): MessageDispatchState? {
        return pendingMessageDao.getById(id)?.toPendingMessageRecord()?.toMessageDispatchState()
    }

    override suspend fun getPendingByEventId(eventId: String): PendingMessageRecord? {
        return pendingMessageDao.getByEventId(eventId)?.toPendingMessageRecord()
    }

    override suspend fun getMessageDispatchStateByEventId(eventId: String): MessageDispatchState? {
        return pendingMessageDao.getByEventId(eventId)?.toPendingMessageRecord()?.toMessageDispatchState()
    }

    override suspend fun getWishPreviewDraftById(id: String): WishPreviewDraft? {
        return pendingMessageDao.getById(id)?.toPendingMessageRecord()?.toWishPreviewDraft()
    }

    override suspend fun getWishPreviewDraftByEventId(eventId: String): WishPreviewDraft? {
        return pendingMessageDao.getByEventId(eventId)?.toPendingMessageRecord()?.toWishPreviewDraft()
    }

    override fun getWishPreviewDraftByRef(messageRef: String): Flow<WishPreviewDraft?> {
        return pendingMessageDao.getAll().map { messages ->
            messages.firstOrNull { it.id == messageRef }
                ?: messages.firstOrNull { it.eventId == messageRef }
        }.map { message ->
            message?.toPendingMessageRecord()?.toWishPreviewDraft()
        }
    }

    override suspend fun getPendingForEventOccurrence(
        contactId: String,
        eventId: String,
        scheduledYear: Int
    ): PendingMessageRecord? {
        return pendingMessageDao.getPendingMessage(contactId, eventId, scheduledYear)?.toPendingMessageRecord()
    }

    override suspend fun pendingExistsForEvent(eventId: String): Boolean = pendingMessageDao.existsForEvent(eventId)

    override suspend fun pendingExistsForEventOccurrence(
        contactId: String,
        eventId: String,
        scheduledYear: Int
    ): Boolean = pendingMessageDao.existsForEventOccurrence(contactId, eventId, scheduledYear)

    override suspend fun insertPending(message: PendingMessageRecord) {
        pendingMessageDao.insert(message.toPendingMessageEntity())
    }

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

    override fun getAllSent(): Flow<List<SentMessageRecord>> {
        return sentMessageDao.getAll().map { messages ->
            messages.toSentMessageRecords()
        }
    }

    override fun getSentListItems(): Flow<List<SentMessageListItem>> {
        return sentMessageDao.getAll().map { messages ->
            messages.toSentMessageRecords().toSentMessageListItems()
        }
    }

    override suspend fun getSentByContact(contactId: String, limit: Int): List<SentMessageRecord> {
        return sentMessageDao.getByContact(contactId, limit).toSentMessageRecords()
    }

    override fun getSentByContactFlow(contactId: String, limit: Int): Flow<List<SentMessageRecord>> {
        return sentMessageDao.getByContactFlow(contactId, limit).map { messages ->
            messages.toSentMessageRecords()
        }
    }

    override fun countSentByContact(contactId: String): Flow<Int> = sentMessageDao.countByContact(contactId)

    override suspend fun getGenerationHistoryByContact(contactId: String, limit: Int): MessageGenerationHistory {
        return sentMessageDao.getByContact(contactId, limit).toSentMessageRecords().toMessageGenerationHistory()
    }

    override suspend fun getRecentForStyleAnalysis(sinceMs: Long, limit: Int): List<SentMessageRecord> {
        return sentMessageDao.getRecentForStyleAnalysis(sinceMs, limit).toSentMessageRecords()
    }

    override suspend fun getSentSinceYearStart(yearStartMs: Long): List<SentMessageRecord> {
        return sentMessageDao.getSentSinceYearStart(yearStartMs).toSentMessageRecords()
    }

    override suspend fun getSentAnalyticsRecordsSince(sinceMs: Long): List<MessageAnalyticsRecord> {
        return sentMessageDao.getSentSinceYearStart(sinceMs)
            .map { it.toSentMessageRecord().toMessageAnalyticsRecord() }
    }

    override fun getSentAnalyticsRecordsSinceFlow(sinceMs: Long): Flow<List<MessageAnalyticsRecord>> {
        return sentMessageDao.getSentSinceFlow(sinceMs).map { messages ->
            messages.map { it.toSentMessageRecord().toMessageAnalyticsRecord() }
        }
    }

    override fun countAllSent(): Flow<Int> = sentMessageDao.countAll()

    override fun countPending(): Flow<Int> = pendingMessageDao.countPending()

    override suspend fun insertSent(message: SentMessageRecord) {
        sentMessageDao.insert(message.toSentMessageEntity())
    }
}
