package com.example.core.automation.sender

import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.resilience.LogLevel
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.contact.ContactPostDispatchUpdate
import com.example.domain.model.dispatch.DispatchAttemptOutcomeUpdate
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.MessageDispatchOccasion
import com.example.domain.model.message.MessageStatusUpdate
import com.example.domain.model.message.SentMessageDeliveryStatusUpdate
import com.example.domain.model.message.SentMessageDispatchRecord
import com.example.domain.model.occasion.OccasionType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageDispatcherPersistenceAdaptersTest {
    private val dispatchAttemptDao: DispatchAttemptDao = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)
    private val eventDao: EventDao = mockk(relaxed = true)

    @Test
    fun sentPendingMessageStatusUpdate_mapsDraftIdToSentStatusUpdate() {
        assertEquals(
            MessageStatusUpdate(
                id = MessageDraftId("pending_1"),
                status = MessageStatus.SENT,
            ),
            sentPendingMessageStatusUpdate(MessageDraftId("pending_1")),
        )
    }

    @Test
    fun failedPendingMessageStatusUpdate_mapsDraftIdToFailedStatusUpdate() {
        assertEquals(
            MessageStatusUpdate(
                id = MessageDraftId("pending_1"),
                status = MessageStatus.FAILED,
            ),
            failedPendingMessageStatusUpdate(MessageDraftId("pending_1")),
        )
    }

    @Test
    fun savePendingMessageDispatchStatusUpdate_mapsTypedUpdateToPendingMessageDaoWrite() = runTest {
        pendingMessageDao.savePendingMessageDispatchStatusUpdate(
            MessageStatusUpdate(
                id = MessageDraftId("pending_1"),
                status = MessageStatus.SENT,
            )
        )

        coVerify {
            pendingMessageDao.updateStatus(
                id = "pending_1",
                status = MessageStatus.SENT.raw,
            )
        }
    }

    @Test
    fun successfulDispatchAttemptOutcomeUpdate_mapsSmsSuccessToPendingDeliveryOutcome() {
        val update = successfulDispatchAttemptOutcomeUpdate(
            dispatchAttemptId = "attempt_sms",
            resolvedAtMs = 1_800_000_000_000L,
            channel = MessageChannel.SMS,
        )

        assertEquals(DispatchAttemptId("attempt_sms"), update?.id)
        assertEquals(1_800_000_000_000L, update?.attemptedAtMs)
        assertEquals(1_800_000_000_000L, update?.resolvedAtMs)
        assertEquals(DispatchAttemptResult.PENDING_DELIVERY, update?.result)
        assertEquals(MessageChannel.SMS, update?.channel)
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY, update?.deliveryStatus)
        assertNull(update?.errorType)
        assertNull(update?.errorCode)
        assertNull(update?.redactedErrorMessage)
        assertNull(update?.nextRetryAtMs)
        assertNull(update?.deadLetteredAtMs)
    }

    @Test
    fun successfulDispatchAttemptOutcomeUpdate_mapsNonSmsSuccessToSentOutcome() {
        val update = successfulDispatchAttemptOutcomeUpdate(
            dispatchAttemptId = "attempt_email",
            resolvedAtMs = 1_800_000_000_000L,
            channel = MessageChannel.EMAIL,
        )

        assertEquals(DispatchAttemptId("attempt_email"), update?.id)
        assertEquals(DispatchAttemptResult.SENT, update?.result)
        assertEquals(MessageChannel.EMAIL, update?.channel)
        assertEquals(MessageDeliveryStatus.SENT, update?.deliveryStatus)
        assertNull(update?.errorType)
        assertNull(update?.nextRetryAtMs)
        assertNull(update?.deadLetteredAtMs)
    }

    @Test
    fun failedDispatchAttemptOutcomeUpdate_mapsRetryableFailureToRetryOutcome() {
        val failure = DispatchProviderRetryPolicy.smsProviderException(
            RuntimeException("radio unavailable")
        )
        val update = failedDispatchAttemptOutcomeUpdate(
            dispatchAttemptId = "attempt_retry",
            failedAtMs = 1_800_000_000_000L,
            channel = MessageChannel.SMS,
            failure = failure,
        )

        assertEquals(DispatchAttemptId("attempt_retry"), update?.id)
        assertEquals(DispatchAttemptResult.FAILED_RETRYABLE, update?.result)
        assertEquals(MessageChannel.SMS, update?.channel)
        assertEquals(MessageDeliveryStatus.FAILED, update?.deliveryStatus)
        assertEquals(failure.errorType, update?.errorType)
        assertEquals(failure.errorCode, update?.errorCode)
        assertEquals(failure.redactedErrorMessage, update?.redactedErrorMessage)
        assertEquals(
            1_800_000_000_000L + requireNotNull(failure.nextRetryDelayMs),
            update?.nextRetryAtMs,
        )
        assertNull(update?.deadLetteredAtMs)
    }

    @Test
    fun failedDispatchAttemptOutcomeUpdate_mapsFinalFailureToDeadLetteredOutcome() {
        val failure = DispatchProviderRetryPolicy.noDeliveryRoute()
        val update = failedDispatchAttemptOutcomeUpdate(
            dispatchAttemptId = "attempt_final",
            failedAtMs = 1_800_000_000_000L,
            channel = MessageChannel.SMS,
            failure = failure,
        )

        assertEquals(DispatchAttemptId("attempt_final"), update?.id)
        assertEquals(DispatchAttemptResult.FAILED_FINAL, update?.result)
        assertEquals(MessageChannel.SMS, update?.channel)
        assertEquals(MessageDeliveryStatus.FAILED, update?.deliveryStatus)
        assertEquals(failure.errorType, update?.errorType)
        assertEquals(failure.errorCode, update?.errorCode)
        assertEquals(failure.redactedErrorMessage, update?.redactedErrorMessage)
        assertNull(update?.nextRetryAtMs)
        assertEquals(1_800_000_000_000L, update?.deadLetteredAtMs)
    }

    @Test
    fun dispatchAttemptOutcomeUpdate_mapsDispatcherOutcomeToTypedUpdate() {
        val update = dispatchAttemptOutcomeUpdate(
            dispatchAttemptId = "attempt_1",
            resolvedAtMs = 1_800_000_000_000L,
            result = DispatchAttemptResult.FAILED_RETRYABLE,
            channel = MessageChannel.SMS,
            deliveryStatus = MessageDeliveryStatus.FAILED,
            errorType = "SMS_TRANSIENT_PROVIDER_FAILURE",
            errorCode = "RuntimeException",
            redactedErrorMessage = "Retry is allowed.",
            deadLetteredAtMs = null,
            nextRetryAtMs = 1_800_000_060_000L,
        )

        assertEquals(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_1"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.FAILED_RETRYABLE,
                channel = MessageChannel.SMS,
                deliveryStatus = MessageDeliveryStatus.FAILED,
                providerMessageId = null,
                errorType = "SMS_TRANSIENT_PROVIDER_FAILURE",
                errorCode = "RuntimeException",
                redactedErrorMessage = "Retry is allowed.",
                retryCount = 0,
                nextRetryAtMs = 1_800_000_060_000L,
                deadLetteredAtMs = null,
            ),
            update,
        )
    }

    @Test
    fun dispatchAttemptOutcomeUpdate_ignoresMissingAttemptIds() {
        assertNull(
            dispatchAttemptOutcomeUpdate(
                dispatchAttemptId = null,
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.SENT,
                channel = MessageChannel.EMAIL,
                deliveryStatus = MessageDeliveryStatus.SENT,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                deadLetteredAtMs = null,
                nextRetryAtMs = null,
            )
        )
        assertNull(
            dispatchAttemptOutcomeUpdate(
                dispatchAttemptId = " ",
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.SENT,
                channel = MessageChannel.EMAIL,
                deliveryStatus = MessageDeliveryStatus.SENT,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                deadLetteredAtMs = null,
                nextRetryAtMs = null,
            )
        )
    }

    @Test
    fun saveDispatchAttemptOutcome_mapsTypedUpdateToRawDaoCall() = runTest {
        dispatchAttemptDao.saveDispatchAttemptOutcome(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_1"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.PENDING_DELIVERY,
                channel = MessageChannel.SMS,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        )

        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_1",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.PENDING_DELIVERY.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }
    }

    @Test
    fun saveMessageDispatchAttemptOutcome_mapsTypedUpdateToRawDaoCall() = runTest {
        dispatchAttemptDao.saveMessageDispatchAttemptOutcome(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_2"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.SENT,
                channel = MessageChannel.EMAIL,
                deliveryStatus = MessageDeliveryStatus.SENT,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        )

        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_2",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.SENT.raw,
                channel = MessageChannel.EMAIL.raw,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }
    }

    @Test
    fun saveSuccessfulMessageDispatchAttemptOutcome_routesSmsPendingDeliveryThroughGuardedDaoUpdate() = runTest {
        dispatchAttemptDao.saveSuccessfulMessageDispatchAttemptOutcome(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_sms"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.PENDING_DELIVERY,
                channel = MessageChannel.SMS,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        )

        coVerify {
            dispatchAttemptDao.updateInitialSmsHandoffOutcomeIfAwaitingCallback(
                id = "attempt_sms",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.PENDING_DELIVERY.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }
        coVerify(exactly = 0) {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_sms",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = any(),
                channel = any(),
                deliveryStatus = any(),
                providerMessageId = any(),
                errorType = any(),
                errorCode = any(),
                redactedErrorMessage = any(),
                retryCount = any(),
                nextRetryAtMs = any(),
                deadLetteredAtMs = any(),
            )
        }
    }

    @Test
    fun saveSuccessfulMessageDispatchAttemptOutcome_keepsNonSmsOnStandardDaoUpdate() = runTest {
        dispatchAttemptDao.saveSuccessfulMessageDispatchAttemptOutcome(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_email"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.SENT,
                channel = MessageChannel.EMAIL,
                deliveryStatus = MessageDeliveryStatus.SENT,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        )

        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_email",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.SENT.raw,
                channel = MessageChannel.EMAIL.raw,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }
        coVerify(exactly = 0) {
            dispatchAttemptDao.updateInitialSmsHandoffOutcomeIfAwaitingCallback(
                id = any(),
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = any(),
                channel = any(),
                deliveryStatus = any(),
                providerMessageId = any(),
                errorType = any(),
                errorCode = any(),
                redactedErrorMessage = any(),
                retryCount = any(),
                nextRetryAtMs = any(),
                deadLetteredAtMs = any(),
            )
        }
    }

    @Test
    fun saveMessageDispatchAttemptOutcome_ignoresNullUpdates() = runTest {
        dispatchAttemptDao.saveMessageDispatchAttemptOutcome(null)

        coVerify(exactly = 0) {
            dispatchAttemptDao.updateOutcome(
                id = any(),
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = any(),
                channel = any(),
                deliveryStatus = any(),
                providerMessageId = any(),
                errorType = any(),
                errorCode = any(),
                redactedErrorMessage = any(),
                retryCount = any(),
                nextRetryAtMs = any(),
                deadLetteredAtMs = any(),
            )
        }
    }

    @Test
    fun saveMessageDispatchAttemptOutcome_ignoresAbsentDao() = runTest {
        val absentDao: DispatchAttemptDao? = null

        absentDao.saveMessageDispatchAttemptOutcome(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_missing_dao"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.SENT,
                channel = MessageChannel.EMAIL,
                deliveryStatus = MessageDeliveryStatus.SENT,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        )
    }

    @Test
    fun saveMessageDispatchAttemptOutcome_logsDaoFailuresWithoutThrowing() = runTest {
        StructuredLogger.clearForTests()
        coEvery {
            dispatchAttemptDao.updateOutcome(
                id = any(),
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = any(),
                channel = any(),
                deliveryStatus = any(),
                providerMessageId = any(),
                errorType = any(),
                errorCode = any(),
                redactedErrorMessage = any(),
                retryCount = any(),
                nextRetryAtMs = any(),
                deadLetteredAtMs = any(),
            )
        } throws IllegalStateException("dao unavailable")

        dispatchAttemptDao.saveMessageDispatchAttemptOutcome(
            DispatchAttemptOutcomeUpdate(
                id = DispatchAttemptId("attempt_failing_dao"),
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_500L,
                result = DispatchAttemptResult.SENT,
                channel = MessageChannel.EMAIL,
                deliveryStatus = MessageDeliveryStatus.SENT,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        )

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("Failed to update dispatch attempt attempt_failing_dao", entry.message)
        assertEquals("IllegalStateException", entry.extras["exception"])
        assertEquals("dao unavailable", entry.extras["exceptionMessage"])
        StructuredLogger.clearForTests()
    }

    @Test
    fun contactPostDispatchUpdate_mapsContactAndTimestampToTypedUpdate() {
        val update = contactPostDispatchUpdate(
            contactId = ContactId("contact_1"),
            wishedAtMs = 1_800_000_000_000L,
        )

        assertEquals(
            ContactPostDispatchUpdate(
                contactId = ContactId("contact_1"),
                wishedAtMs = 1_800_000_000_000L,
                healthScoreDelta = 5,
            ),
            update,
        )
    }

    @Test
    fun saveContactPostDispatchUpdate_mapsTypedUpdateToContactDaoWrites() = runTest {
        contactDao.saveContactPostDispatchUpdate(
            ContactPostDispatchUpdate(
                contactId = ContactId("contact_1"),
                wishedAtMs = 1_800_000_000_000L,
                healthScoreDelta = 5,
            )
        )

        coVerify { contactDao.updateLastWished("contact_1", 1_800_000_000_000L) }
        coVerify { contactDao.incrementConsecutiveYearsWished("contact_1") }
        coVerify { contactDao.updateHealthScoreDelta("contact_1", 5) }
    }

    @Test
    fun smsPendingDeliverySentMessageDispatchRecord_mapsSmsAttemptToPendingDeliveryRecord() {
        val record = smsPendingDeliverySentMessageDispatchRecord(
            id = SentMessageId("sent_sms"),
            contactId = ContactId("contact_1"),
            dispatchOccasion = MessageDispatchOccasion(
                occasionId = OccasionId("event_1"),
                occasionType = OccasionType.BIRTHDAY,
                occasionLabel = "Birthday",
            ),
            eventYear = 2026,
            messageText = "Selected",
            sentAtMs = 1_800_000_000_000L,
        )

        assertEquals(
            SentMessageDispatchRecord(
                id = SentMessageId("sent_sms"),
                contactId = ContactId("contact_1"),
                occasionId = OccasionId("event_1"),
                occasionType = OccasionType.BIRTHDAY,
                occasionLabel = "Birthday",
                eventYear = 2026,
                messageText = "Selected",
                channel = MessageChannel.SMS,
                sentAtMs = 1_800_000_000_000L,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY,
                aiGenerated = true,
            ),
            record,
        )
    }

    @Test
    fun successfulSentMessageDispatchRecord_mapsSuccessfulRouteToSentRecord() {
        val record = successfulSentMessageDispatchRecord(
            id = SentMessageId("sent_email"),
            contactId = ContactId("contact_1"),
            dispatchOccasion = MessageDispatchOccasion(
                occasionId = OccasionId("event_1"),
                occasionType = OccasionType.ANNIVERSARY,
                occasionLabel = "Wedding anniversary",
            ),
            eventYear = 2026,
            messageText = "Selected",
            channel = MessageChannel.EMAIL,
            sentAtMs = 1_800_000_000_000L,
        )

        assertEquals(
            SentMessageDispatchRecord(
                id = SentMessageId("sent_email"),
                contactId = ContactId("contact_1"),
                occasionId = OccasionId("event_1"),
                occasionType = OccasionType.ANNIVERSARY,
                occasionLabel = "Wedding anniversary",
                eventYear = 2026,
                messageText = "Selected",
                channel = MessageChannel.EMAIL,
                sentAtMs = 1_800_000_000_000L,
                deliveryStatus = MessageDeliveryStatus.SENT,
                aiGenerated = true,
            ),
            record,
        )
    }

    @Test
    fun sentMessageDispatchRecord_mapsDispatcherValuesToTypedRecord() {
        val record = sentMessageDispatchRecord(
            id = SentMessageId("sent_1"),
            contactId = ContactId("contact_1"),
            occasionId = OccasionId("event_1"),
            occasionType = OccasionType.ANNIVERSARY,
            occasionLabel = "Wedding anniversary",
            eventYear = 2026,
            messageText = "Selected",
            channel = MessageChannel.EMAIL,
            sentAtMs = 1_800_000_000_000L,
            deliveryStatus = MessageDeliveryStatus.SENT,
        )

        assertEquals(
            SentMessageDispatchRecord(
                id = SentMessageId("sent_1"),
                contactId = ContactId("contact_1"),
                occasionId = OccasionId("event_1"),
                occasionType = OccasionType.ANNIVERSARY,
                occasionLabel = "Wedding anniversary",
                eventYear = 2026,
                messageText = "Selected",
                channel = MessageChannel.EMAIL,
                sentAtMs = 1_800_000_000_000L,
                deliveryStatus = MessageDeliveryStatus.SENT,
                aiGenerated = true,
            ),
            record,
        )
    }

    @Test
    fun saveSentMessageDispatchRecord_mapsTypedRecordToSentMessageEntity() = runTest {
        val sentSlot = slot<SentMessageEntity>()
        coEvery { sentMessageDao.insert(capture(sentSlot)) } just Runs

        sentMessageDao.saveSentMessageDispatchRecord(
            SentMessageDispatchRecord(
                id = SentMessageId("sent_1"),
                contactId = ContactId("contact_1"),
                occasionId = OccasionId("event_1"),
                occasionType = OccasionType.ANNIVERSARY,
                occasionLabel = "Wedding anniversary",
                eventYear = 2026,
                messageText = "Selected",
                channel = MessageChannel.EMAIL,
                sentAtMs = 1_800_000_000_000L,
                deliveryStatus = MessageDeliveryStatus.SENT,
                aiGenerated = true,
            )
        )

        assertEquals(
            SentMessageEntity(
                id = "sent_1",
                contactId = "contact_1",
                eventType = OccasionType.ANNIVERSARY.raw,
                eventId = "event_1",
                occasionType = OccasionType.ANNIVERSARY.raw,
                occasionLabel = "Wedding anniversary",
                eventYear = 2026,
                messageText = "Selected",
                channel = MessageChannel.EMAIL.raw,
                sentAtMs = 1_800_000_000_000L,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
                aiGenerated = true,
            ),
            sentSlot.captured,
        )
    }

    @Test
    fun failedSentMessageDeliveryStatusUpdate_mapsIdToFailedDeliveryStatus() {
        assertEquals(
            SentMessageDeliveryStatusUpdate(
                id = SentMessageId("sent_1"),
                deliveryStatus = MessageDeliveryStatus.FAILED,
            ),
            failedSentMessageDeliveryStatusUpdate(SentMessageId("sent_1")),
        )
    }

    @Test
    fun sentMessageDeliveryStatusUpdate_mapsTypedStatusToRawDaoCall() = runTest {
        val update = sentMessageDeliveryStatusUpdate(
            id = SentMessageId("sent_1"),
            deliveryStatus = MessageDeliveryStatus.FAILED,
        )

        sentMessageDao.saveSentMessageDeliveryStatusUpdate(update)

        assertEquals(
            SentMessageDeliveryStatusUpdate(
                id = SentMessageId("sent_1"),
                deliveryStatus = MessageDeliveryStatus.FAILED,
            ),
            update,
        )
        coVerify { sentMessageDao.updateDeliveryStatus("sent_1", MessageDeliveryStatus.FAILED.raw) }
    }

    @Test
    fun messageDispatchOccasion_mapsResolvedEventRowToTypedOccasion() = runTest {
        coEvery {
            eventDao.getById("event_1")
        } returns EventEntity(
            id = "event_1",
            contactId = "contact_1",
            type = OccasionType.ANNIVERSARY.raw,
            label = "Wedding anniversary",
            dayOfMonth = 12,
            month = 6,
            nextOccurrenceMs = 1_800_000_000_000L,
        )

        assertEquals(
            MessageDispatchOccasion(
                occasionId = OccasionId("event_1"),
                occasionType = OccasionType.ANNIVERSARY,
                occasionLabel = "Wedding anniversary",
            ),
            messageDispatchOccasion(eventDao, OccasionId("event_1")),
        )
    }

    @Test
    fun messageDispatchOccasion_classifiesUnresolvedSyntheticReferences() = runTest {
        coEvery { eventDao.getById(any()) } returns null

        assertEquals(
            MessageDispatchOccasion(
                occasionId = null,
                occasionType = OccasionType.FOLLOW_UP,
                occasionLabel = null,
            ),
            messageDispatchOccasion(eventDao, OccasionId("FOLLOWUP_sent_1")),
        )
        assertEquals(
            MessageDispatchOccasion(
                occasionId = null,
                occasionType = OccasionType.FOLLOW_UP,
                occasionLabel = null,
            ),
            messageDispatchOccasion(eventDao, OccasionId("FOLLOW_UP_sent_1")),
        )
        assertEquals(
            MessageDispatchOccasion(
                occasionId = null,
                occasionType = OccasionType.HOLIDAY,
                occasionLabel = null,
            ),
            messageDispatchOccasion(eventDao, OccasionId("HOLIDAY_2026_01_01")),
        )
        assertEquals(
            MessageDispatchOccasion(
                occasionId = null,
                occasionType = OccasionType.REVIVAL,
                occasionLabel = null,
            ),
            messageDispatchOccasion(eventDao, OccasionId("REVIVAL_contact_1")),
        )
        assertEquals(
            MessageDispatchOccasion(
                occasionId = null,
                occasionType = OccasionType.BIRTHDAY,
                occasionLabel = null,
            ),
            messageDispatchOccasion(eventDao, OccasionId("birthday")),
        )
        assertEquals(
            MessageDispatchOccasion(
                occasionId = null,
                occasionType = OccasionType.UNKNOWN,
                occasionLabel = null,
            ),
            messageDispatchOccasion(eventDao, OccasionId("legacy_unknown_ref")),
        )
    }

    @Test
    fun messageDispatchOccasion_usesFallbackWhenEventDaoIsUnavailable() = runTest {
        assertEquals(
            MessageDispatchOccasion(
                occasionId = null,
                occasionType = OccasionType.FOLLOW_UP,
                occasionLabel = null,
            ),
            messageDispatchOccasion(null, OccasionId("FOLLOWUP_sent_1")),
        )
    }
}
