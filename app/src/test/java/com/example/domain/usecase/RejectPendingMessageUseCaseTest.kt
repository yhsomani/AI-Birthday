package com.example.domain.usecase

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RejectPendingMessageUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val useCase = RejectPendingMessageUseCase(messageRepository)

    @Test
    fun `invoke with non-existent message returns PendingNotFound`() = runTest {
        coEvery { messageRepository.getAllPending() } returns flowOf(emptyList())

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
        coEvery { messageRepository.getAllPending() } returns flowOf(listOf(pendingMsg))

        val result = useCase("m1")

        assertTrue(result is RejectPendingMessageUseCase.RejectionOutcome.Rejected)
        assertEquals("m1", (result as RejectPendingMessageUseCase.RejectionOutcome.Rejected).id)

        coVerify { messageRepository.updatePendingStatus("m1", "REJECTED") }
    }
}
