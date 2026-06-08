package com.example.domain.usecase

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
        val pendingMsg = PendingMessageEntity(
            id = "msg_1",
            contactId = "contact_1",
            eventId = "event_1",
            shortVariant = "", standardVariant = "hi", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "hi",
            channel = "SMS", scheduledForMs = 0, approvalMode = "MANUAL",
            status = "PENDING"
        )
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(pendingMsg))

        val result = useCase("msg_1")

        assertTrue(result is ApprovePendingMessageUseCase.ApprovalOutcome.Approved)
        val approved = result as ApprovePendingMessageUseCase.ApprovalOutcome.Approved
        assertEquals("msg_1", approved.id)
        assertEquals("MANUAL", approved.approvalMode)
        coVerify { messageRepository.insertPending(any()) }
        coVerify { schedulerService.scheduleExactSend("event_1") }
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
            channel = "SMS", scheduledForMs = 0, approvalMode = "FULLY_AUTO",
            status = "PENDING"
        )
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(pendingMsg))

        useCase("msg_1")

        coVerify { schedulerService.scheduleExactSend("event_1") }
    }

    @Test
    fun `invoke with invalid id returns PendingNotFound`() = runTest {
        coEvery { messageRepository.getAllPending() } returns flowOf(emptyList())

        val result = useCase("invalid_id")

        assertEquals(ApprovePendingMessageUseCase.ApprovalOutcome.PendingNotFound, result)
    }
}
