package com.example.core.automation.sender

import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.SentMessageEntity
import com.example.core.resilience.DeadLetterQueue
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.LogLevel
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.MessageDispatchOccasion
import com.example.domain.model.occasion.OccasionType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageDispatcherFinalizationAdaptersTest {
    private val dispatchAttemptDao: DispatchAttemptDao = mockk(relaxed = true)
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)
    private val contactDao: ContactDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        DeadLetterQueue.clear()
        StructuredLogger.clearForTests()
    }

    @After
    fun tearDown() {
        DeadLetterQueue.clear()
        StructuredLogger.clearForTests()
    }

    @Test
    fun saveMessageDispatchFinalization_routesSuccessfulStateToSuccessfulFinalization() = runTest {
        saveMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            sentMessageDao = sentMessageDao,
            contactDao = contactDao,
            messageId = MessageDraftId("pending_wrapper_success"),
            contactId = ContactId("contact_1"),
            dispatchAttemptId = "attempt_wrapper_success",
            preferredChannel = MessageChannel.SMS,
            finalChannel = MessageChannel.SMS,
            dispatchOccasion = dispatchOccasion(),
            messageText = "Selected",
            routeLoopState = successfulRouteLoopState(successfulSentMessageInserted = true),
            noDeliveryRoute = false,
        )

        coVerify { pendingMessageDao.markSmsHandoffSentIfAwaitingCallback("pending_wrapper_success") }
        coVerify {
            dispatchAttemptDao.updateInitialSmsHandoffOutcomeIfAwaitingCallback(
                id = "attempt_wrapper_success",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
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
        coVerify(exactly = 0) { sentMessageDao.insert(any()) }

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Message dispatched successfully", entry.message)
        assertEquals("pending_wrapper_success", entry.extras["messageId"])
    }

    @Test
    fun saveMessageDispatchFinalization_routesRetryableFailureToScheduledRetryFinalization() = runTest {
        val providerFailure = ProviderDispatchFailure(
            result = DispatchAttemptResult.FAILED_RETRYABLE,
            errorType = DispatchProviderRetryPolicy.ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE,
            errorCode = "MessagingException",
            redactedErrorMessage = "Email provider failed before accepting the message; retry is allowed.",
            nextRetryDelayMs = DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS,
        )

        val result = saveMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            sentMessageDao = sentMessageDao,
            contactDao = contactDao,
            messageId = MessageDraftId("pending_wrapper_failure"),
            contactId = ContactId("contact_1"),
            dispatchAttemptId = "attempt_wrapper_failure",
            preferredChannel = MessageChannel.EMAIL,
            finalChannel = MessageChannel.EMAIL,
            dispatchOccasion = dispatchOccasion(),
            messageText = "Selected",
            routeLoopState = failedRouteLoopState(providerFailure),
            noDeliveryRoute = false,
        )

        assertTrue(result.shouldScheduleRetry)
        assertEquals(1, result.retryCount)
        coVerify {
            pendingMessageDao.updateRetryState(
                id = "pending_wrapper_failure",
                status = MessageStatus.APPROVED.raw,
                scheduledForMs = result.retryAtMs ?: error("retryAtMs missing"),
            )
        }
        coVerify(exactly = 0) { pendingMessageDao.updateStatus("pending_wrapper_failure", MessageStatus.FAILED.raw) }
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_wrapper_failure",
                attemptedAtMs = any(),
                resolvedAtMs = any(),
                result = DispatchAttemptResult.FAILED_RETRYABLE.raw,
                channel = MessageChannel.EMAIL.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = DispatchProviderRetryPolicy.ERROR_EMAIL_TRANSIENT_PROVIDER_FAILURE,
                errorCode = "MessagingException",
                redactedErrorMessage = "Email provider failed before accepting the message; retry is allowed.",
                retryCount = 1,
                nextRetryAtMs = any(),
                deadLetteredAtMs = null,
            )
        }
        coVerify(exactly = 0) { sentMessageDao.insert(any()) }
        coVerify(exactly = 0) { contactDao.updateLastWished(any(), any()) }
        assertEquals(0, DeadLetterQueue.count())
        assertTrue(
            HealthMonitor.snapshot().recentErrors.any {
                it.contains(
                    "[MessageDispatcher.dispatch] Failed to send pending_wrapper_failure via EMAIL: " +
                        "EMAIL_TRANSIENT_PROVIDER_FAILURE"
                )
            }
        )
    }

    @Test
    fun saveSuccessfulMessageDispatchFinalization_persistsSuccessfulNonSmsEffects() = runTest {
        val sentSlot = slot<SentMessageEntity>()
        coEvery { sentMessageDao.insert(capture(sentSlot)) } just Runs

        saveSuccessfulMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            sentMessageDao = sentMessageDao,
            contactDao = contactDao,
            messageId = MessageDraftId("pending_1"),
            contactId = ContactId("contact_1"),
            dispatchAttemptId = "attempt_1",
            channel = MessageChannel.EMAIL,
            dispatchOccasion = dispatchOccasion(),
            messageText = "Selected",
            sentMessageAlreadyInserted = false,
            resolvedAtMs = 1_800_000_000_000L,
            sentAtMs = 1_800_000_000_100L,
            wishedAtMs = 1_800_000_000_200L,
            eventYear = 2026,
            sentMessageId = SentMessageId("sent_email"),
        )

        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_1",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
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
        coVerify {
            pendingMessageDao.updateStatus(
                id = "pending_1",
                status = MessageStatus.SENT.raw,
            )
        }
        assertEquals(
            SentMessageEntity(
                id = "sent_email",
                contactId = "contact_1",
                eventType = OccasionType.ANNIVERSARY.raw,
                eventId = "event_1",
                occasionType = OccasionType.ANNIVERSARY.raw,
                occasionLabel = "Wedding anniversary",
                eventYear = 2026,
                messageText = "Selected",
                channel = MessageChannel.EMAIL.raw,
                sentAtMs = 1_800_000_000_100L,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
                aiGenerated = true,
            ),
            sentSlot.captured,
        )
        coVerify { contactDao.updateLastWished("contact_1", 1_800_000_000_200L) }
        coVerify { contactDao.incrementConsecutiveYearsWished("contact_1") }
        coVerify { contactDao.updateHealthScoreDelta("contact_1", 5) }

        val entry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", entry.tag)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Message dispatched successfully", entry.message)
        assertEquals("pending_1", entry.extras["messageId"])
        assertEquals(MessageChannel.EMAIL.raw, entry.extras["channel"])
    }

    @Test
    fun saveSuccessfulMessageDispatchFinalization_skipsSentMessageInsertWhenSmsInsertedPendingRecord() = runTest {
        saveSuccessfulMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            sentMessageDao = sentMessageDao,
            contactDao = null,
            messageId = MessageDraftId("pending_sms"),
            contactId = ContactId("contact_1"),
            dispatchAttemptId = "attempt_sms",
            channel = MessageChannel.SMS,
            dispatchOccasion = dispatchOccasion(),
            messageText = "Selected",
            sentMessageAlreadyInserted = true,
            resolvedAtMs = 1_800_000_000_000L,
            sentAtMs = 1_800_000_000_100L,
            wishedAtMs = 1_800_000_000_200L,
            eventYear = 2026,
            sentMessageId = SentMessageId("sent_sms_duplicate"),
        )

        coVerify {
            dispatchAttemptDao.updateInitialSmsHandoffOutcomeIfAwaitingCallback(
                id = "attempt_sms",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
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
        coVerify { pendingMessageDao.markSmsHandoffSentIfAwaitingCallback("pending_sms") }
        coVerify(exactly = 0) { sentMessageDao.insert(any()) }
    }

    @Test
    fun saveFailedMessageDispatchFinalization_persistsNoRouteFinalFailureEffects() = runTest {
        saveFailedMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            messageId = MessageDraftId("pending_no_route"),
            dispatchAttemptId = "attempt_no_route",
            preferredChannel = MessageChannel.SMS,
            finalChannel = MessageChannel.SMS,
            messageText = "Selected",
            providerFailureSelection = messageDispatchProviderFailureSelection(),
            noDeliveryRoute = true,
            failedAtMs = 1_800_000_000_000L,
        )

        coVerify { pendingMessageDao.updateStatus("pending_no_route", MessageStatus.FAILED.raw) }
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_no_route",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = DispatchProviderRetryPolicy.ERROR_NO_DELIVERY_ROUTE,
                errorCode = null,
                redactedErrorMessage = "All automatic delivery routes failed.",
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = 1_800_000_000_000L,
            )
        }

        val logEntry = StructuredLogger.getRecent(1).single()
        assertEquals("MessageDispatcher", logEntry.tag)
        assertEquals(LogLevel.WARN, logEntry.level)
        assertEquals(
            "Failed to dispatch message pending_no_route via SMS: NO_DELIVERY_ROUTE",
            logEntry.message,
        )

        val deadLetter = DeadLetterQueue.getAll().single()
        assertEquals("pending_no_route", deadLetter.id)
        assertEquals("Selected", deadLetter.payload)
        assertEquals("All automatic delivery routes failed.", deadLetter.errorMessage)
        assertEquals(DispatchProviderRetryPolicy.ERROR_NO_DELIVERY_ROUTE, deadLetter.errorType)
        assertEquals(0, deadLetter.retryCount)
        assertTrue(
            HealthMonitor.snapshot().recentErrors.any {
                it.contains("[MessageDispatcher.dispatch] Failed to send pending_no_route via SMS: NO_DELIVERY_ROUTE")
            }
        )
    }

    @Test
    fun saveFailedMessageDispatchFinalization_persistsRetryableProviderFailureEffects() = runTest {
        val providerFailure = ProviderDispatchFailure(
            result = DispatchAttemptResult.FAILED_RETRYABLE,
            errorType = DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            errorCode = "RuntimeException",
            redactedErrorMessage = "SMS provider failed before delivery confirmation; retry is allowed.",
            nextRetryDelayMs = DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS,
        )

        val result = saveFailedMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            messageId = MessageDraftId("pending_retryable"),
            dispatchAttemptId = "attempt_retryable",
            preferredChannel = MessageChannel.SMS,
            finalChannel = MessageChannel.SMS,
            messageText = "Selected",
            providerFailureSelection = messageDispatchProviderFailureSelection().select(providerFailure),
            noDeliveryRoute = false,
            failedAtMs = 1_800_000_000_000L,
        )

        assertEquals(
            1_800_000_000_000L + DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS,
            result.retryAtMs,
        )
        assertEquals(1, result.retryCount)
        coVerify {
            pendingMessageDao.updateRetryState(
                id = "pending_retryable",
                status = MessageStatus.APPROVED.raw,
                scheduledForMs = 1_800_000_000_000L + DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS,
            )
        }
        coVerify(exactly = 0) { pendingMessageDao.updateStatus("pending_retryable", MessageStatus.FAILED.raw) }
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_retryable",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.FAILED_RETRYABLE.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
                errorCode = "RuntimeException",
                redactedErrorMessage = "SMS provider failed before delivery confirmation; retry is allowed.",
                retryCount = 1,
                nextRetryAtMs = 1_800_000_000_000L + DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS,
                deadLetteredAtMs = null,
            )
        }
        assertEquals(0, DeadLetterQueue.count())
        assertTrue(
            HealthMonitor.snapshot().recentErrors.any {
                it.contains(
                    "[MessageDispatcher.dispatch] Failed to send pending_retryable via SMS: " +
                        "SMS_TRANSIENT_PROVIDER_FAILURE"
                )
            }
        )
    }

    @Test
    fun saveFailedMessageDispatchFinalization_stopsAutomaticRetryAtRetryLimit() = runTest {
        val providerFailure = ProviderDispatchFailure(
            result = DispatchAttemptResult.FAILED_RETRYABLE,
            errorType = DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            errorCode = "RuntimeException",
            redactedErrorMessage = "SMS provider failed before delivery confirmation; retry is allowed.",
            nextRetryDelayMs = DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS,
        )

        val result = saveFailedMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            messageId = MessageDraftId("pending_retry_limit"),
            dispatchAttemptId = "attempt_retry_limit",
            preferredChannel = MessageChannel.SMS,
            finalChannel = MessageChannel.SMS,
            messageText = "Selected",
            providerFailureSelection = messageDispatchProviderFailureSelection().select(providerFailure),
            noDeliveryRoute = false,
            failedAtMs = 1_800_000_000_000L,
            automaticRetryCount = DispatchProviderRetryPolicy.MAX_AUTOMATIC_RETRY_FAILURES,
        )

        assertEquals(null, result.retryAtMs)
        assertEquals(DispatchProviderRetryPolicy.MAX_AUTOMATIC_RETRY_FAILURES, result.retryCount)
        coVerify {
            pendingMessageDao.updateStatus("pending_retry_limit", MessageStatus.FAILED.raw)
        }
        coVerify(exactly = 0) {
            pendingMessageDao.updateRetryState(any(), any(), any())
        }
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_retry_limit",
                attemptedAtMs = 1_800_000_000_000L,
                resolvedAtMs = 1_800_000_000_000L,
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
                errorCode = "RuntimeException",
                redactedErrorMessage = match { it.contains("Automatic retry limit reached") },
                retryCount = DispatchProviderRetryPolicy.MAX_AUTOMATIC_RETRY_FAILURES,
                nextRetryAtMs = null,
                deadLetteredAtMs = 1_800_000_000_000L,
            )
        }
        val deadLetter = DeadLetterQueue.getAll().single()
        assertEquals("pending_retry_limit", deadLetter.id)
        assertEquals(DispatchProviderRetryPolicy.MAX_AUTOMATIC_RETRY_FAILURES, deadLetter.retryCount)
        assertTrue(deadLetter.errorMessage.contains("Automatic retry limit reached"))
    }

    private fun dispatchOccasion(): MessageDispatchOccasion {
        return MessageDispatchOccasion(
            occasionId = OccasionId("event_1"),
            occasionType = OccasionType.ANNIVERSARY,
            occasionLabel = "Wedding anniversary",
        )
    }

    private fun successfulRouteLoopState(
        successfulSentMessageInserted: Boolean,
    ): MessageDispatchRouteLoopState {
        return messageDispatchRouteLoopState().applyRouteOutcome(
            MessageDispatchRouteOutcome(
                sent = true,
                successfulSentMessageInserted = successfulSentMessageInserted,
                failure = null,
            )
        )
    }

    private fun failedRouteLoopState(
        failure: ProviderDispatchFailure,
    ): MessageDispatchRouteLoopState {
        return messageDispatchRouteLoopState().applyRouteOutcome(
            MessageDispatchRouteOutcome(
                sent = false,
                successfulSentMessageInserted = false,
                failure = failure,
            )
        )
    }
}
