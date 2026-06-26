package com.example.domain.usecase

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
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

class ApprovePendingMessageUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val useCase = ApprovePendingMessageUseCase(messageRepository, schedulerService)

    @Test
    fun `invoke with valid pendingMessageId updates status to APPROVED`() = runTest {
        val inserted = slot<PendingMessageEntity>()
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "contact_1",
            eventId = "event_1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "MANUAL",
            status = MessageStatus.PENDING.raw
        )
        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMsg

        val result = useCase("msg_1")

        assertTrue(result is ApprovePendingMessageUseCase.ApprovalOutcome.Approved)
        val approved = result as ApprovePendingMessageUseCase.ApprovalOutcome.Approved
        assertEquals("msg_1", approved.id)
        assertEquals(ApprovalMode.UNKNOWN, approved.approvalMode)
        coVerify { messageRepository.insertPending(capture(inserted)) }
        assertEquals(MessageStatus.APPROVED.raw, inserted.captured.status)
        coVerify { schedulerService.scheduleExactSend("msg_1") }
    }

    @Test
    fun `invoke with any approval mode schedules exact send`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "contact_1",
            eventId = "event_1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = MessageChannel.SMS.raw, scheduledForMs = 0, approvalMode = "FULLY_AUTO",
            status = MessageStatus.PENDING.raw
        )
        coEvery { messageRepository.getPendingById("msg_1") } returns pendingMsg

        useCase("msg_1")

        coVerify { schedulerService.scheduleExactSend("msg_1") }
    }

    @Test
    fun `invoke with invalid id returns PendingNotFound`() = runTest {
        coEvery { messageRepository.getPendingById("invalid_id") } returns null

        val result = useCase("invalid_id")

        assertEquals(ApprovePendingMessageUseCase.ApprovalOutcome.PendingNotFound, result)
    }
}
