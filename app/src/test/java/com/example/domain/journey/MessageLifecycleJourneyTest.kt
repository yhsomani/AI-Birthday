package com.example.domain.journey

import com.example.domain.message.toMessageApprovalState
import com.example.domain.message.toMessageDispatchState
import com.example.domain.message.toPendingMessageListItems
import com.example.domain.message.toRetryableMessageDraft
import com.example.domain.message.toWishPreviewDraft
import com.example.domain.message.toWishPreviewReviewItem
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.activity.ActivityLogRecord
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.contact.ContactDeliveryRouteProfile
import com.example.domain.model.contact.ContactHeader
import com.example.domain.model.contact.ContactMessageGenerationProfile
import com.example.domain.model.contact.ContactMessagePromptContext
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.model.dispatch.MessageDispatchRecipient
import com.example.domain.model.dispatch.MessageDispatchRequest
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.message.MessageAnalyticsRecord
import com.example.domain.model.message.MessageApprovalState
import com.example.domain.model.message.MessageDispatchState
import com.example.domain.model.message.MessageGenerationHistory
import com.example.domain.model.message.MessageStatusUpdate
import com.example.domain.model.message.PendingMessageListItem
import com.example.domain.model.message.PendingMessageRecord
import com.example.domain.model.message.RetryQueuedMessageDraft
import com.example.domain.model.message.RetryableMessageDraft
import com.example.domain.model.message.SentMessageListItem
import com.example.domain.model.message.SentMessageRecord
import com.example.domain.model.message.WishPreviewDraft
import com.example.domain.model.message.WishPreviewReviewItem
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import com.example.domain.model.notification.ApprovalNotificationRequest
import com.example.domain.repository.ActivityLogRepository
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.AiService
import com.example.domain.service.ContactClassificationResult
import com.example.domain.service.GiftSuggestion
import com.example.domain.service.MessageDispatcherService
import com.example.domain.service.MessageVariantsResult
import com.example.domain.service.NotificationService
import com.example.domain.service.PreferencesRepository
import com.example.domain.service.SchedulerService
import com.example.domain.usecase.ApprovePendingMessageUseCase
import com.example.domain.usecase.DispatchMessageUseCase
import com.example.domain.usecase.GenerateMessageUseCase
import com.example.domain.usecase.RetryFailedMessageUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageLifecycleJourneyTest {

    @Test
    fun `generated review draft can be approved and dispatched through one pending record`() = runTest {
        val nowMs = System.currentTimeMillis()
        val messageRepository = InMemoryMessageRepository()
        val contactRepository = mockContactRepository()
        val eventRepository = mockEventRepository(nowMs)
        val preferencesRepository = mockPreferencesRepository()
        val schedulerService = RecordingSchedulerService()
        val notificationService = RecordingNotificationService()
        val messageDispatcherService = RecordingMessageDispatcherService()
        val dispatchAttemptRepository = RecordingDispatchAttemptRepository()
        val activityLogRepository = RecordingActivityLogRepository()
        val generatedText = "Happy birthday Taylor, hope your day has good coffee and a long relaxed walk."

        val generate = GenerateMessageUseCase(
            contactRepository = contactRepository,
            eventRepository = eventRepository,
            messageRepository = messageRepository,
            styleProfileRepository = mockStyleProfileRepository(),
            memoryNoteRepository = mockMemoryNoteRepository(),
            giftHistoryRepository = mockGiftHistoryRepository(),
            aiService = FixedAiService(generatedText),
            preferencesRepository = preferencesRepository,
            schedulerService = schedulerService,
            notificationService = notificationService,
        )
        val approve = ApprovePendingMessageUseCase(
            messageRepository = messageRepository,
            schedulerService = schedulerService,
        )
        val dispatch = DispatchMessageUseCase(
            messageRepository = messageRepository,
            contactRepository = contactRepository,
            messageDispatcherService = messageDispatcherService,
            activityLogRepository = activityLogRepository,
            dispatchAttemptRepository = dispatchAttemptRepository,
            preferencesRepository = preferencesRepository,
        )

        val generation = generate("event_1") as GenerateMessageUseCase.GenerationOutcome.Generated

        assertEquals(ApprovalMode.ALWAYS_ASK, generation.approvalMode)
        val generatedPending = requireNotNull(messageRepository.pendingById(generation.pendingId))
        assertEquals(MessageStatus.PENDING, generatedPending.status)
        assertEquals(MessageChannel.SMS, generatedPending.channel)
        assertEquals(generatedText, generatedPending.selectedVariantText)
        assertEquals(listOf(generation.pendingId), notificationService.approvalRequests.map { it.messageId.value })
        assertTrue(schedulerService.scheduledIds.isEmpty())

        val editedText = "$generatedText See you this weekend."
        val approval = approve(generation.pendingId, editedText)

        assertTrue(approval is ApprovePendingMessageUseCase.ApprovalOutcome.Approved)
        val approvedPending = requireNotNull(messageRepository.pendingById(generation.pendingId))
        assertEquals(MessageStatus.APPROVED, approvedPending.status)
        assertEquals(editedText, approvedPending.selectedDispatchText())
        assertEquals(listOf(generation.pendingId), schedulerService.scheduledIds)

        val dispatchOutcome = dispatch(generation.pendingId)

        assertEquals(
            DispatchMessageUseCase.DispatchOutcome.Sent(generation.pendingId, MessageChannel.SMS.raw),
            dispatchOutcome,
        )
        val request = messageDispatcherService.requests.single()
        assertEquals(MessageDraftId(generation.pendingId), request.messageId)
        assertEquals(ContactId("contact_1"), request.contactId)
        assertEquals(editedText, request.messageText)
        assertNotNull(request.dispatchAttemptId)
        assertEquals(DispatchEligibilityRecord.SEND_NOW, dispatchAttemptRepository.attempts.single().eligibilityDecision)
        assertEquals(DispatchAttemptResult.QUEUED, dispatchAttemptRepository.attempts.single().result)
        assertEquals("Dispatch sent", activityLogRepository.records.single().title)
        assertEquals(ActivityLogStatus.RESOLVED.raw, activityLogRepository.records.single().status)
    }

    @Test
    fun `failed dispatch can be queued for retry and sent after recovery`() = runTest {
        val nowMs = System.currentTimeMillis()
        val messageRepository = InMemoryMessageRepository()
        val contactRepository = mockContactRepository()
        val eventRepository = mockEventRepository(nowMs)
        val preferencesRepository = mockPreferencesRepository()
        val schedulerService = RecordingSchedulerService()
        val notificationService = RecordingNotificationService()
        val messageDispatcherService = RecordingMessageDispatcherService(failuresBeforeSuccess = 1)
        val dispatchAttemptRepository = RecordingDispatchAttemptRepository()
        val activityLogRepository = RecordingActivityLogRepository()
        val generatedText = "Happy birthday Taylor, hope today gives you space to enjoy something memorable."

        val generate = GenerateMessageUseCase(
            contactRepository = contactRepository,
            eventRepository = eventRepository,
            messageRepository = messageRepository,
            styleProfileRepository = mockStyleProfileRepository(),
            memoryNoteRepository = mockMemoryNoteRepository(),
            giftHistoryRepository = mockGiftHistoryRepository(),
            aiService = FixedAiService(generatedText),
            preferencesRepository = preferencesRepository,
            schedulerService = schedulerService,
            notificationService = notificationService,
        )
        val approve = ApprovePendingMessageUseCase(
            messageRepository = messageRepository,
            schedulerService = schedulerService,
        )
        val dispatch = DispatchMessageUseCase(
            messageRepository = messageRepository,
            contactRepository = contactRepository,
            messageDispatcherService = messageDispatcherService,
            activityLogRepository = activityLogRepository,
            dispatchAttemptRepository = dispatchAttemptRepository,
            preferencesRepository = preferencesRepository,
        )
        val retry = RetryFailedMessageUseCase(
            messageRepository = messageRepository,
            dispatchAttemptRepository = dispatchAttemptRepository,
            schedulerService = schedulerService,
        )

        val generation = generate("event_1") as GenerateMessageUseCase.GenerationOutcome.Generated
        approve(generation.pendingId)

        val firstFailure = runCatching { dispatch(generation.pendingId) }.exceptionOrNull()

        assertTrue(firstFailure is IllegalStateException)
        val failedAttempt = dispatchAttemptRepository.attempts.single()
        assertEquals(DispatchAttemptResult.FAILED_FINAL, failedAttempt.result)
        assertNotNull(failedAttempt.deadLetteredAtMs)

        messageRepository.saveMessageStatusUpdate(
            MessageStatusUpdate(
                id = MessageDraftId(generation.pendingId),
                status = MessageStatus.FAILED,
            )
        )

        val retryOutcome = retry(generation.pendingId)

        assertTrue(retryOutcome is RetryFailedMessageUseCase.RetryOutcome.RetryQueued)
        assertEquals(1, (retryOutcome as RetryFailedMessageUseCase.RetryOutcome.RetryQueued).retryCount)
        val retryQueued = requireNotNull(messageRepository.pendingById(generation.pendingId))
        assertEquals(MessageStatus.APPROVED, retryQueued.status)
        assertEquals(DispatchAttemptResult.RETRY_QUEUED, dispatchAttemptRepository.attempts.single().result)
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY, dispatchAttemptRepository.attempts.single().deliveryStatus)
        assertEquals(null, dispatchAttemptRepository.attempts.single().deadLetteredAtMs)
        assertEquals(
            listOf(generation.pendingId, generation.pendingId),
            schedulerService.scheduledIds,
        )

        val recoveredDispatch = dispatch(generation.pendingId)

        assertEquals(
            DispatchMessageUseCase.DispatchOutcome.Sent(generation.pendingId, MessageChannel.SMS.raw),
            recoveredDispatch,
        )
        assertEquals(2, messageDispatcherService.requests.size)
        assertEquals(2, dispatchAttemptRepository.attempts.size)
        assertEquals(DispatchAttemptResult.QUEUED, dispatchAttemptRepository.attempts.last().result)
        assertEquals("Dispatch sent", activityLogRepository.records.last().title)
    }

    private fun mockContactRepository(): ContactRepository {
        val repository = mockk<ContactRepository>(relaxed = true)
        coEvery { repository.getMessageGenerationProfile("contact_1") } returns generationProfile()
        coEvery { repository.getMessageDispatchRecipient("contact_1") } returns MessageDispatchRecipient(
            id = ContactId("contact_1"),
            displayName = "Taylor",
            primaryPhone = "+15555550101",
            primaryEmail = "taylor@example.com",
        )
        return repository
    }

    private fun mockEventRepository(nowMs: Long): EventRepository {
        val repository = mockk<EventRepository>(relaxed = true)
        coEvery { repository.getOccasionById("event_1") } returns Occasion(
            id = OccasionId("event_1"),
            contactId = ContactId("contact_1"),
            type = OccasionType.BIRTHDAY,
            label = "Taylor birthday",
            date = OccasionDate(dayOfMonth = 1, month = 1),
            nextOccurrenceMs = nowMs - 24 * 60 * 60 * 1000L,
            isActive = true,
            notifyDaysBefore = 1,
            source = "MANUAL",
            confidenceScore = 100,
            isVerified = true,
        )
        return repository
    }

    private fun mockPreferencesRepository(): PreferencesRepository {
        val repository = mockk<PreferencesRepository>(relaxed = true)
        every { repository.isAiWishGenerationEnabled() } returns true
        every { repository.getGlobalAutomationMode() } returns ApprovalMode.ALWAYS_ASK
        every { repository.getChannelBlackout() } returns "[]"
        every { repository.getSenderEmail() } returns ""
        every { repository.getSenderEmailPassword() } returns ""
        every { repository.getQuietHoursStart() } returns 0
        every { repository.getQuietHoursEnd() } returns 0
        every { repository.getBlackoutDates() } returns "[]"
        return repository
    }

    private fun mockStyleProfileRepository(): StyleProfileRepository {
        return mockk<StyleProfileRepository>(relaxed = true).also { repository ->
            coEvery { repository.getProfileOnce() } returns null
        }
    }

    private fun mockMemoryNoteRepository(): MemoryNoteRepository {
        return mockk<MemoryNoteRepository>(relaxed = true).also { repository ->
            coEvery { repository.getRecordsByContact("contact_1") } returns emptyList()
        }
    }

    private fun mockGiftHistoryRepository(): GiftHistoryRepository {
        return mockk<GiftHistoryRepository>(relaxed = true).also { repository ->
            coEvery { repository.getRecordsByContact("contact_1") } returns emptyList()
        }
    }

    private fun generationProfile(): ContactMessageGenerationProfile {
        val contactId = ContactId("contact_1")
        return ContactMessageGenerationProfile(
            id = contactId,
            relationshipType = "FRIEND",
            automationMode = ApprovalMode.ALWAYS_ASK,
            skipAutoWish = false,
            deliveryRouteProfile = ContactDeliveryRouteProfile(
                preferredChannel = MessageChannel.SMS,
                hasPrimaryPhone = true,
                hasPrimaryEmail = false,
            ),
            promptContext = ContactMessagePromptContext(
                id = contactId,
                displayName = "Taylor",
                relationshipType = "FRIEND",
                preferredChannel = MessageChannel.SMS.raw,
            ),
            header = ContactHeader(
                id = contactId,
                displayName = "Taylor",
            ),
            customSendTimeHour = null,
            customSendTimeMinute = null,
        )
    }

    private class FixedAiService(private val text: String) : AiService {
        override suspend fun generateMessage(context: com.example.domain.model.message.MessagePromptContext) =
            MessageVariantsResult(
                short = text,
                standard = text,
                long = text,
                formal = text,
                funny = text,
                emotional = text,
                recommended = "standard",
            )

        override suspend fun regenerateMessage(
            previousMessage: String,
            context: com.example.domain.model.message.MessagePromptContext,
            feedbackInstruction: String?,
        ) = generateMessage(context)

        override suspend fun classifyContact(contact: com.example.domain.model.contact.ContactClassificationPromptContext):
            ContactClassificationResult = error("Not used in this journey")

        override suspend fun generateGiftSuggestions(
            contact: com.example.domain.model.contact.ContactGiftAdvisorProfile,
            history: List<GiftHistoryRecord>,
        ): List<GiftSuggestion> = error("Not used in this journey")
    }

    private class RecordingSchedulerService : SchedulerService {
        val scheduledIds = mutableListOf<String>()

        override fun scheduleExactSend(pendingMessageId: String) {
            scheduledIds += pendingMessageId
        }

        override fun cancelExactSend(pendingMessageId: String) = Unit
    }

    private class RecordingNotificationService : NotificationService {
        val approvalRequests = mutableListOf<ApprovalNotificationRequest>()

        override fun showApprovalNotification(
            request: ApprovalNotificationRequest,
            variants: MessageVariantsResult,
        ) {
            approvalRequests += request
        }

        override fun showAiFallbackAlert() = Unit
    }

    private class RecordingMessageDispatcherService(
        private var failuresBeforeSuccess: Int = 0,
    ) : MessageDispatcherService {
        val requests = mutableListOf<MessageDispatchRequest>()

        override suspend fun dispatch(request: MessageDispatchRequest) {
            requests += request
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                throw IllegalStateException("Provider unavailable")
            }
        }
    }

    private class RecordingActivityLogRepository : ActivityLogRepository {
        val records = mutableListOf<ActivityLogRecord>()

        override fun getRecent(limit: Int): Flow<List<ActivityLogRecord>> = flowOf(records.take(limit))
        override fun getByType(type: String, limit: Int): Flow<List<ActivityLogRecord>> =
            flowOf(records.filter { it.type == type }.take(limit))

        override fun getByStatus(status: String, limit: Int): Flow<List<ActivityLogRecord>> =
            flowOf(records.filter { it.status == status }.take(limit))

        override fun search(query: String, limit: Int): Flow<List<ActivityLogRecord>> =
            flowOf(records.filter { it.title.contains(query, ignoreCase = true) }.take(limit))

        override suspend fun record(entry: ActivityLogRecord) {
            records += entry
        }

        override suspend fun deleteOlderThan(cutoffMs: Long) {
            records.removeAll { it.createdAtMs < cutoffMs }
        }
    }

    private class RecordingDispatchAttemptRepository : DispatchAttemptRepository {
        val attempts = mutableListOf<DispatchAttempt>()

        override suspend fun upsert(attempt: DispatchAttempt) {
            attempts.removeAll { it.id == attempt.id }
            attempts += attempt
        }

        override fun countDeadLettered(): Flow<Int> = flowOf(attempts.count { it.deadLetteredAtMs != null })
        override fun countFailureRecoveryQueue(): Flow<Int> = flowOf(
            attempts.count {
                it.result == DispatchAttemptResult.FAILED_RETRYABLE || it.deadLetteredAtMs != null
            }
        )

        override suspend fun getFailureRecoveryQueue(limit: Int): List<DispatchAttempt> =
            attempts.filter {
                it.result == DispatchAttemptResult.FAILED_RETRYABLE || it.deadLetteredAtMs != null
            }.take(limit)

        override suspend fun getSuccessfulChannelsSince(sinceMs: Long): Set<MessageChannel> =
            attempts.filter {
                it.requestedAtMs >= sinceMs &&
                    (it.result == DispatchAttemptResult.SENT || it.result == DispatchAttemptResult.DELIVERED)
            }.map { it.channel }.toSet()

        override suspend fun getLatestFailureForMessageDraft(messageDraftId: MessageDraftId): DispatchAttempt? =
            attempts.lastOrNull {
                it.messageDraftId == messageDraftId &&
                    (it.result == DispatchAttemptResult.FAILED_RETRYABLE || it.result == DispatchAttemptResult.FAILED_FINAL)
            }

        override suspend fun updateOutcome(
            id: DispatchAttemptId,
            attemptedAtMs: Long?,
            resolvedAtMs: Long?,
            result: DispatchAttemptResult,
            channel: MessageChannel?,
            deliveryStatus: MessageDeliveryStatus,
            providerMessageId: String?,
            errorType: String?,
            errorCode: String?,
            redactedErrorMessage: String?,
            retryCount: Int,
            nextRetryAtMs: Long?,
            deadLetteredAtMs: Long?,
        ) {
            val index = attempts.indexOfFirst { it.id == id }
            if (index >= 0) {
                attempts[index] = attempts[index].copy(
                    attemptedAtMs = attemptedAtMs,
                    resolvedAtMs = resolvedAtMs,
                    result = result,
                    channel = channel ?: attempts[index].channel,
                    deliveryStatus = deliveryStatus,
                    providerMessageId = providerMessageId,
                    errorType = errorType,
                    errorCode = errorCode,
                    redactedErrorMessage = redactedErrorMessage,
                    retryCount = retryCount,
                    nextRetryAtMs = nextRetryAtMs,
                    deadLetteredAtMs = deadLetteredAtMs,
                )
            }
        }
    }

    private class InMemoryMessageRepository : MessageRepository {
        private val pending = linkedMapOf<String, PendingMessageRecord>()
        private val sent = mutableListOf<SentMessageRecord>()
        private val pendingFlow = MutableStateFlow<List<PendingMessageRecord>>(emptyList())
        private val sentFlow = MutableStateFlow<List<SentMessageRecord>>(emptyList())

        fun pendingById(id: String): PendingMessageRecord? = pending[id]

        override fun getAllPending(): Flow<List<PendingMessageRecord>> = pendingFlow
        override fun getPendingListItems(): Flow<List<PendingMessageListItem>> =
            pendingFlow.map { it.toPendingMessageListItems() }

        override fun getWishPreviewReviewQueue(): Flow<List<WishPreviewReviewItem>> =
            pendingFlow.map { messages ->
                messages
                    .filter { it.status == MessageStatus.PENDING }
                    .map { it.toWishPreviewReviewItem() }
            }

        override suspend fun getAllPendingSync(): List<PendingMessageRecord> = pending.values.toList()
        override suspend fun getAllApproved(): List<PendingMessageRecord> =
            pending.values.filter { it.status == MessageStatus.APPROVED }

        override suspend fun getPendingById(id: String): PendingMessageRecord? = pending[id]
        override suspend fun getMessageApprovalStateById(id: String): MessageApprovalState? =
            pending[id]?.toMessageApprovalState()

        override suspend fun getRetryableMessageDraftById(id: String): RetryableMessageDraft? =
            pending[id]?.toRetryableMessageDraft()

        override suspend fun getMessageDispatchStateById(id: String): MessageDispatchState? =
            pending[id]?.toMessageDispatchState()

        override suspend fun getPendingByEventId(eventId: String): PendingMessageRecord? =
            pending.values.firstOrNull { it.occasionId.value == eventId }

        override suspend fun getMessageDispatchStateByEventId(eventId: String): MessageDispatchState? =
            getPendingByEventId(eventId)?.toMessageDispatchState()

        override suspend fun getWishPreviewDraftById(id: String): WishPreviewDraft? =
            pending[id]?.toWishPreviewDraft()

        override suspend fun getWishPreviewDraftByEventId(eventId: String): WishPreviewDraft? =
            getPendingByEventId(eventId)?.toWishPreviewDraft()

        override fun getWishPreviewDraftByRef(messageRef: String): Flow<WishPreviewDraft?> =
            pendingFlow.map { messages ->
                messages.firstOrNull { it.id.value == messageRef || it.occasionId.value == messageRef }
                    ?.toWishPreviewDraft()
            }

        override suspend fun getPendingForEventOccurrence(
            contactId: String,
            eventId: String,
            scheduledYear: Int,
        ): PendingMessageRecord? {
            return pending.values.firstOrNull {
                it.contactId.value == contactId &&
                    it.occasionId.value == eventId &&
                    it.scheduledYear == scheduledYear
            }
        }

        override suspend fun pendingExistsForEvent(eventId: String): Boolean =
            pending.values.any { it.occasionId.value == eventId }

        override suspend fun pendingExistsForEventOccurrence(
            contactId: String,
            eventId: String,
            scheduledYear: Int,
        ): Boolean = getPendingForEventOccurrence(contactId, eventId, scheduledYear) != null

        override suspend fun insertPending(message: PendingMessageRecord) {
            pending[message.id.value] = message
            publishPending()
        }

        override suspend fun saveMessageApprovalState(state: MessageApprovalState) {
            val current = requireNotNull(pending[state.id.value]) {
                "Missing pending message ${state.id.value}"
            }
            pending[state.id.value] = current.copy(
                selectedVariantText = state.selectedVariantText,
                status = state.status,
                editedByUser = state.editedByUser,
                userEditedText = state.userEditedText,
            )
            publishPending()
        }

        override suspend fun saveRetryQueuedMessageDraft(state: RetryQueuedMessageDraft) {
            val current = requireNotNull(pending[state.id.value]) {
                "Missing pending message ${state.id.value}"
            }
            pending[state.id.value] = current.copy(
                status = state.status,
                scheduledForMs = state.scheduledForMs,
            )
            publishPending()
        }

        override suspend fun saveMessageStatusUpdate(update: MessageStatusUpdate) {
            val current = requireNotNull(pending[update.id.value]) {
                "Missing pending message ${update.id.value}"
            }
            pending[update.id.value] = current.copy(status = update.status)
            publishPending()
        }

        override suspend fun updatePendingStatus(id: String, status: String) {
            val current = requireNotNull(pending[id]) {
                "Missing pending message $id"
            }
            pending[id] = current.copy(status = MessageStatus.fromRaw(status))
            publishPending()
        }

        override suspend fun updatePendingStatusByEventId(eventId: String, status: String) {
            val current = requireNotNull(getPendingByEventId(eventId)) {
                "Missing pending message for event $eventId"
            }
            updatePendingStatus(current.id.value, status)
        }

        override fun getAllSent(): Flow<List<SentMessageRecord>> = sentFlow
        override fun getSentListItems(): Flow<List<SentMessageListItem>> = flowOf(emptyList())
        override suspend fun getSentByContact(contactId: String, limit: Int): List<SentMessageRecord> =
            sent.filter { it.contactId?.value == contactId }.take(limit)

        override fun getSentByContactFlow(contactId: String, limit: Int): Flow<List<SentMessageRecord>> =
            sentFlow.map { messages -> messages.filter { it.contactId?.value == contactId }.take(limit) }

        override fun countSentByContact(contactId: String): Flow<Int> =
            sentFlow.map { messages -> messages.count { it.contactId?.value == contactId } }

        override suspend fun getGenerationHistoryByContact(
            contactId: String,
            limit: Int,
        ): MessageGenerationHistory = MessageGenerationHistory(
            previousWishes = sent
                .filter { it.contactId?.value == contactId }
                .takeLast(limit)
                .map { it.messageText },
        )

        override suspend fun getRecentForStyleAnalysis(sinceMs: Long, limit: Int): List<SentMessageRecord> =
            sent.filter { it.sentAtMs >= sinceMs }.take(limit)

        override suspend fun getSentSinceYearStart(yearStartMs: Long): List<SentMessageRecord> =
            sent.filter { it.sentAtMs >= yearStartMs }

        override suspend fun getSentAnalyticsRecordsSince(sinceMs: Long): List<MessageAnalyticsRecord> = emptyList()
        override fun getSentAnalyticsRecordsSinceFlow(sinceMs: Long): Flow<List<MessageAnalyticsRecord>> =
            flowOf(emptyList())

        override fun countAllSent(): Flow<Int> = sentFlow.map { it.size }
        override fun countPending(): Flow<Int> = pendingFlow.map { it.size }

        override suspend fun insertSent(message: SentMessageRecord) {
            sent += message
            sentFlow.value = sent.toList()
        }

        private fun publishPending() {
            pendingFlow.value = pending.values.toList()
        }
    }
}
