package com.example.domain.usecase

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevokeApprovalUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val useCase = RevokeApprovalUseCase(messageRepository, schedulerService)

    @Test
    fun `invoke with approved message moves status back to pending and cancels schedule`() = runTest {
        val inserted = slot<PendingMessageEntity>()
        val pendingMsg = pendingMessage(status = " approved ")
        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMsg

        val result = useCase("msg_1")

        assertTrue(result is RevokeApprovalUseCase.RevokeOutcome.Revoked)
        assertEquals("msg_1", (result as RevokeApprovalUseCase.RevokeOutcome.Revoked).id)
        coVerify { messageRepository.insertPending(capture(inserted)) }
        assertEquals(MessageStatus.PENDING.raw, inserted.captured.status)
        coVerify { schedulerService.cancelExactSend("msg_1") }
    }

    @Test
    fun `invoke with non-approved message returns NotApproved`() = runTest {
        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMessage(
            status = MessageStatus.PENDING.raw,
        )

        val result = useCase("msg_1")

        assertEquals(RevokeApprovalUseCase.RevokeOutcome.NotApproved, result)
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
        coVerify(exactly = 0) { schedulerService.cancelExactSend(any()) }
    }

    private fun pendingMessage(status: String) = PendingMessageEntity(
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
        scheduledForMs = 0,
        approvalMode = "SMART_APPROVE",
        status = status,
    )
}
