package com.example.domain.usecase

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RejectPendingMessageUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val useCase = RejectPendingMessageUseCase(messageRepository, schedulerService)

    @Test
    fun `invoke with non-existent message returns PendingNotFound`() = runTest {
        coEvery { messageRepository.getPendingById("m1") } returns null

        val result = useCase("m1")

        assertEquals(RejectPendingMessageUseCase.RejectionOutcome.PendingNotFound, result)
    }

    @Test
    fun `invoke with valid message sets status to REJECTED`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "m1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "",
            channel = "SMS", scheduledForMs = 0, approvalMode = "MANUAL"
        )
        coEvery { messageRepository.getPendingById("m1") } returns pendingMsg

        val result = useCase("m1")

        assertTrue(result is RejectPendingMessageUseCase.RejectionOutcome.Rejected)
        assertEquals("m1", (result as RejectPendingMessageUseCase.RejectionOutcome.Rejected).id)

        coVerify { messageRepository.updatePendingStatus("m1", "REJECTED") }
    }

    @Test
    fun `invoke with approved message cancels scheduled send`() = runTest {
        val pendingMsg = PendingMessageEntity(
            id = "m1", contactId = "c1", eventId = "e1",
            shortVariant = "", standardVariant = "", longVariant = "",
            formalVariant = "", funnyVariant = "", emotionalVariant = "",
            selectedVariant = "standard", selectedVariantText = "",
            channel = "SMS", scheduledForMs = 0, approvalMode = "MANUAL",
            status = "APPROVED"
        )
        coEvery { messageRepository.getPendingById("m1") } returns pendingMsg

        useCase("m1")

        coVerify { schedulerService.cancelExactSend("m1") }
    }
}
