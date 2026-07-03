package com.example.domain.repository

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
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getAllPending(): Flow<List<PendingMessageRecord>>
    fun getPendingListItems(): Flow<List<PendingMessageListItem>>
    fun getWishPreviewReviewQueue(): Flow<List<WishPreviewReviewItem>>
    suspend fun getAllPendingSync(): List<PendingMessageRecord>
    suspend fun getAllApproved(): List<PendingMessageRecord>
    suspend fun getPendingById(id: String): PendingMessageRecord?
    suspend fun getMessageApprovalStateById(id: String): MessageApprovalState?
    suspend fun getRetryableMessageDraftById(id: String): RetryableMessageDraft?
    suspend fun getMessageDispatchStateById(id: String): MessageDispatchState?
    suspend fun getPendingByEventId(eventId: String): PendingMessageRecord?
    suspend fun getMessageDispatchStateByEventId(eventId: String): MessageDispatchState?
    suspend fun getWishPreviewDraftById(id: String): WishPreviewDraft?
    suspend fun getWishPreviewDraftByEventId(eventId: String): WishPreviewDraft?
    fun getWishPreviewDraftByRef(messageRef: String): Flow<WishPreviewDraft?>
    suspend fun getPendingForEventOccurrence(contactId: String, eventId: String, scheduledYear: Int): PendingMessageRecord?
    suspend fun pendingExistsForEvent(eventId: String): Boolean
    suspend fun pendingExistsForEventOccurrence(contactId: String, eventId: String, scheduledYear: Int): Boolean
    suspend fun insertPending(message: PendingMessageRecord)
    suspend fun saveMessageApprovalState(state: MessageApprovalState)
    suspend fun saveRetryQueuedMessageDraft(state: RetryQueuedMessageDraft)
    suspend fun saveMessageStatusUpdate(update: MessageStatusUpdate)
    suspend fun updatePendingStatus(id: String, status: String)
    suspend fun updatePendingStatusByEventId(eventId: String, status: String)
    fun getAllSent(): Flow<List<SentMessageRecord>>
    fun getSentListItems(): Flow<List<SentMessageListItem>>
    suspend fun getSentByContact(contactId: String, limit: Int): List<SentMessageRecord>
    fun getSentByContactFlow(contactId: String, limit: Int): Flow<List<SentMessageRecord>>
    fun countSentByContact(contactId: String): Flow<Int>
    suspend fun getGenerationHistoryByContact(contactId: String, limit: Int): MessageGenerationHistory
    suspend fun getRecentForStyleAnalysis(sinceMs: Long, limit: Int = 100): List<SentMessageRecord>
    suspend fun getSentSinceYearStart(yearStartMs: Long): List<SentMessageRecord>
    suspend fun getSentAnalyticsRecordsSince(sinceMs: Long): List<MessageAnalyticsRecord>
    fun getSentAnalyticsRecordsSinceFlow(sinceMs: Long): Flow<List<MessageAnalyticsRecord>>
    fun countAllSent(): Flow<Int>
    fun countPending(): Flow<Int>
    suspend fun insertSent(message: SentMessageRecord)
}
