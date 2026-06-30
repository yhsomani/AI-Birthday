package com.example.core.automation.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class ExactSendRecoveryTest {
    private lateinit var context: Context
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val dispatchAttemptDao: DispatchAttemptDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(DailyScheduler)
        every { DailyScheduler.scheduleExactSendCommand(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `recover reschedules every boot recoverable exact send`() = runTest {
        coEvery { pendingMessageDao.getBootRecoverableAutoSends() } returns listOf(
            pendingMessage(id = "approved_full_auto", status = MessageStatus.APPROVED),
            pendingMessage(
                id = "pending_smart",
                approvalMode = ApprovalMode.SMART_APPROVE,
                status = MessageStatus.PENDING,
            ),
        )

        ExactSendRecovery.recover(context, pendingMessageDao)

        verify { DailyScheduler.scheduleExactSendCommand(context, match { it.messageId.value == "approved_full_auto" }) }
        verify { DailyScheduler.scheduleExactSendCommand(context, match { it.messageId.value == "pending_smart" }) }
    }

    @Test
    fun `recover marks stale unresolved dispatching message failed`() = runTest {
        val nowMs = 2_000_000L
        val requestedAtMs = nowMs - 31 * 60 * 1000L
        coEvery { pendingMessageDao.getDispatchingMessages() } returns listOf(
            pendingMessage(id = "stuck_dispatch", status = MessageStatus.DISPATCHING),
        )
        coEvery { dispatchAttemptDao.getLatestForMessageDraft("stuck_dispatch") } returns dispatchAttempt(
            id = "attempt_1",
            messageDraftId = "stuck_dispatch",
            requestedAtMs = requestedAtMs,
            result = DispatchAttemptResult.QUEUED,
        )
        coEvery { pendingMessageDao.getBootRecoverableAutoSends() } returns emptyList()

        ExactSendRecovery.recover(
            context = context,
            pendingMessageDao = pendingMessageDao,
            dispatchAttemptDao = dispatchAttemptDao,
            nowMs = nowMs,
            staleDispatchingGraceMs = 30 * 60 * 1000L,
        )

        coVerify {
            pendingMessageDao.updateStatusIfCurrent(
                id = "stuck_dispatch",
                expectedStatus = MessageStatus.DISPATCHING.raw,
                newStatus = MessageStatus.FAILED.raw,
            )
        }
        coVerify {
            dispatchAttemptDao.updateOutcome(
                id = "attempt_1",
                attemptedAtMs = requestedAtMs,
                resolvedAtMs = nowMs,
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                channel = null,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = null,
                errorType = "INTERRUPTED_DISPATCH",
                errorCode = null,
                redactedErrorMessage = any(),
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = nowMs,
            )
        }
    }

    @Test
    fun `recover reconciles stale provider accepted dispatching message to sent`() = runTest {
        val nowMs = 2_000_000L
        coEvery { pendingMessageDao.getDispatchingMessages() } returns listOf(
            pendingMessage(id = "accepted_dispatch", status = MessageStatus.DISPATCHING),
        )
        coEvery { dispatchAttemptDao.getLatestForMessageDraft("accepted_dispatch") } returns dispatchAttempt(
            id = "attempt_1",
            messageDraftId = "accepted_dispatch",
            requestedAtMs = nowMs - 31 * 60 * 1000L,
            result = DispatchAttemptResult.PENDING_DELIVERY,
        )
        coEvery { pendingMessageDao.getBootRecoverableAutoSends() } returns emptyList()

        ExactSendRecovery.recover(
            context = context,
            pendingMessageDao = pendingMessageDao,
            dispatchAttemptDao = dispatchAttemptDao,
            nowMs = nowMs,
            staleDispatchingGraceMs = 30 * 60 * 1000L,
        )

        coVerify {
            pendingMessageDao.updateStatusIfCurrent(
                id = "accepted_dispatch",
                expectedStatus = MessageStatus.DISPATCHING.raw,
                newStatus = MessageStatus.SENT.raw,
            )
        }
        coVerify(exactly = 0) {
            dispatchAttemptDao.updateOutcome(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `recover requeues stale retryable dispatching message`() = runTest {
        val nowMs = 2_000_000L
        val retryAtMs = nowMs + 60_000L
        coEvery { pendingMessageDao.getDispatchingMessages() } returns listOf(
            pendingMessage(id = "retry_dispatch", status = MessageStatus.DISPATCHING),
        )
        coEvery { dispatchAttemptDao.getLatestForMessageDraft("retry_dispatch") } returns dispatchAttempt(
            id = "attempt_1",
            messageDraftId = "retry_dispatch",
            requestedAtMs = nowMs - 31 * 60 * 1000L,
            result = DispatchAttemptResult.FAILED_RETRYABLE,
            nextRetryAtMs = retryAtMs,
        )
        coEvery { pendingMessageDao.getBootRecoverableAutoSends() } returns emptyList()

        ExactSendRecovery.recover(
            context = context,
            pendingMessageDao = pendingMessageDao,
            dispatchAttemptDao = dispatchAttemptDao,
            nowMs = nowMs,
            staleDispatchingGraceMs = 30 * 60 * 1000L,
        )

        coVerify {
            pendingMessageDao.updateStatusAndScheduledForIfCurrent(
                id = "retry_dispatch",
                expectedStatus = MessageStatus.DISPATCHING.raw,
                newStatus = MessageStatus.APPROVED.raw,
                scheduledForMs = retryAtMs,
            )
        }
    }

    @Test
    fun `recover leaves fresh dispatching attempt alone`() = runTest {
        val nowMs = 2_000_000L
        coEvery { pendingMessageDao.getDispatchingMessages() } returns listOf(
            pendingMessage(id = "fresh_dispatch", status = MessageStatus.DISPATCHING),
        )
        coEvery { dispatchAttemptDao.getLatestForMessageDraft("fresh_dispatch") } returns dispatchAttempt(
            id = "attempt_1",
            messageDraftId = "fresh_dispatch",
            requestedAtMs = nowMs - 10_000L,
            result = DispatchAttemptResult.QUEUED,
        )
        coEvery { pendingMessageDao.getBootRecoverableAutoSends() } returns emptyList()

        ExactSendRecovery.recover(
            context = context,
            pendingMessageDao = pendingMessageDao,
            dispatchAttemptDao = dispatchAttemptDao,
            nowMs = nowMs,
            staleDispatchingGraceMs = 30 * 60 * 1000L,
        )

        coVerify(exactly = 0) {
            pendingMessageDao.updateStatusIfCurrent(any(), any(), any())
        }
        coVerify(exactly = 0) {
            pendingMessageDao.updateStatusAndScheduledForIfCurrent(any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            dispatchAttemptDao.updateOutcome(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    private fun pendingMessage(
        id: String,
        approvalMode: ApprovalMode = ApprovalMode.FULLY_AUTO,
        status: MessageStatus,
    ): PendingMessageEntity {
        return PendingMessageEntity(
            id = id,
            contactId = "contact_1",
            eventId = "event_1",
            shortVariant = "Short",
            standardVariant = "Standard",
            longVariant = "Long",
            formalVariant = "Formal",
            funnyVariant = "Funny",
            emotionalVariant = "Emotional",
            selectedVariant = "standard",
            selectedVariantText = "Standard",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = 1_800_000_000_000L,
            approvalMode = approvalMode.raw,
            status = status.raw,
        )
    }

    private fun dispatchAttempt(
        id: String,
        messageDraftId: String,
        requestedAtMs: Long,
        result: DispatchAttemptResult,
        nextRetryAtMs: Long? = null,
    ): DispatchAttemptEntity {
        return DispatchAttemptEntity(
            id = id,
            messageDraftId = messageDraftId,
            contactId = "contact_1",
            occasionId = "event_1",
            channel = MessageChannel.SMS.raw,
            eligibilityDecision = DispatchEligibilityRecord.SEND_NOW.raw,
            requestedAtMs = requestedAtMs,
            result = result.raw,
            deliveryStatus = when (result) {
                DispatchAttemptResult.SENT,
                DispatchAttemptResult.DELIVERED,
                DispatchAttemptResult.PENDING_DELIVERY -> MessageDeliveryStatus.PENDING_DELIVERY.raw
                DispatchAttemptResult.FAILED_RETRYABLE,
                DispatchAttemptResult.FAILED_FINAL -> MessageDeliveryStatus.FAILED.raw
                else -> MessageDeliveryStatus.UNKNOWN.raw
            },
            nextRetryAtMs = nextRetryAtMs,
            createdBy = DispatchAttemptCreator.WORKER.raw,
        )
    }
}
