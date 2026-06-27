package com.example.domain.usecase

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.repository.DispatchAttemptRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryFailedMessageUseCaseTest {
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val dispatchAttemptRepository: DispatchAttemptRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val useCase = RetryFailedMessageUseCase(
        messageRepository = messageRepository,
        dispatchAttemptRepository = dispatchAttemptRepository,
        schedulerService = schedulerService,
    )

    @Test
    fun `invoke updates latest failed attempt as retry queued before scheduling`() = runTest {
        val inserted = slot<PendingMessageEntity>()
        val pending = pendingMessage(status = MessageStatus.FAILED.raw)
        val latestFailure = dispatchAttempt(retryCount = 2)
        coEvery { messageRepository.getPendingById("msg_1") } returns pending
        coEvery {
            dispatchAttemptRepository.getLatestFailureForMessageDraft(MessageDraftId("msg_1"))
        } returns latestFailure

        val result = useCase("msg_1")

        assertTrue(result is RetryFailedMessageUseCase.RetryOutcome.RetryQueued)
        assertEquals(3, (result as RetryFailedMessageUseCase.RetryOutcome.RetryQueued).retryCount)
        coVerify {
            dispatchAttemptRepository.updateOutcome(
                id = DispatchAttemptId("attempt_1"),
                attemptedAtMs = 1_700_000_000_100,
                resolvedAtMs = any(),
                result = DispatchAttemptResult.RETRY_QUEUED,
                channel = MessageChannel.SMS,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY,
                providerMessageId = null,
                errorType = "NO_DELIVERY_ROUTE",
                errorCode = null,
                redactedErrorMessage = "All automatic delivery routes failed.",
                retryCount = 3,
                nextRetryAtMs = any(),
                deadLetteredAtMs = null,
            )
        }
        coVerify { messageRepository.insertPending(capture(inserted)) }
        assertEquals(MessageStatus.APPROVED.raw, inserted.captured.status)
        assertTrue(inserted.captured.scheduledForMs >= pending.scheduledForMs)
        verify { schedulerService.scheduleExactSend("msg_1") }
    }

    @Test
    fun `invoke creates retry marker for legacy failed message without dispatch attempt`() = runTest {
        val attempt = slot<DispatchAttempt>()
        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMessage(status = MessageStatus.FAILED.raw)
        coEvery {
            dispatchAttemptRepository.getLatestFailureForMessageDraft(MessageDraftId("msg_1"))
        } returns null

        val result = useCase("msg_1")

        assertTrue(result is RetryFailedMessageUseCase.RetryOutcome.RetryQueued)
        coVerify { dispatchAttemptRepository.upsert(capture(attempt)) }
        assertEquals(MessageDraftId("msg_1"), attempt.captured.messageDraftId)
        assertEquals(DispatchEligibilityRecord.SEND_NOW, attempt.captured.eligibilityDecision)
        assertEquals(DispatchAttemptResult.RETRY_QUEUED, attempt.captured.result)
        assertEquals("MANUAL_RETRY", attempt.captured.blockOrDeferReason)
        assertEquals(1, attempt.captured.retryCount)
        assertEquals(DispatchAttemptCreator.USER, attempt.captured.createdBy)
        coVerify { messageRepository.insertPending(match { it.status == MessageStatus.APPROVED.raw }) }
        verify { schedulerService.scheduleExactSend("msg_1") }
    }

    @Test
    fun `invoke rejects non failed messages without scheduling`() = runTest {
        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMessage(status = MessageStatus.APPROVED.raw)

        val result = useCase("msg_1")

        assertEquals(
            RetryFailedMessageUseCase.RetryOutcome.NotFailed("msg_1", MessageStatus.APPROVED),
            result,
        )
        coVerify(exactly = 0) { dispatchAttemptRepository.upsert(any()) }
        coVerify(exactly = 0) {
            dispatchAttemptRepository.updateOutcome(
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
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
        verify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
    }

    private fun pendingMessage(status: String): PendingMessageEntity {
        return PendingMessageEntity(
            id = "msg_1",
            contactId = "contact_1",
            eventId = "event_1",
            shortVariant = "short",
            standardVariant = "standard",
            longVariant = "long",
            formalVariant = "formal",
            funnyVariant = "funny",
            emotionalVariant = "emotional",
            selectedVariant = "standard",
            selectedVariantText = "standard",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = 1_700_000_000_000,
            approvalMode = "MANUAL",
            status = status,
        )
    }

    private fun dispatchAttempt(retryCount: Int): DispatchAttempt {
        return DispatchAttempt(
            id = DispatchAttemptId("attempt_1"),
            messageDraftId = MessageDraftId("msg_1"),
            contactId = null,
            occasionId = null,
            channel = MessageChannel.SMS,
            routeRank = 0,
            eligibilityDecision = DispatchEligibilityRecord.SEND_NOW,
            blockOrDeferReason = null,
            requestedAtMs = 1_700_000_000_000,
            attemptedAtMs = 1_700_000_000_100,
            resolvedAtMs = 1_700_000_000_200,
            result = DispatchAttemptResult.FAILED_FINAL,
            deliveryStatus = MessageDeliveryStatus.FAILED,
            providerMessageId = null,
            errorType = "NO_DELIVERY_ROUTE",
            errorCode = null,
            redactedErrorMessage = "All automatic delivery routes failed.",
            retryCount = retryCount,
            nextRetryAtMs = null,
            deadLetteredAtMs = 1_700_000_000_200,
            createdBy = DispatchAttemptCreator.WORKER,
        )
    }
}
