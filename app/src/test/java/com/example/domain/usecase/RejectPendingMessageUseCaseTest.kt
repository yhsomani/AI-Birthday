package com.example.domain.usecase

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.message.MessageApprovalState
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

class RejectPendingMessageUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val useCase = RejectPendingMessageUseCase(messageRepository, schedulerService)

    @Test
    fun `invoke with non-existent message returns PendingNotFound`() = runTest {
        coEvery { messageRepository.getMessageApprovalStateById("m1") } returns null

        val result = useCase("m1")

        assertEquals(RejectPendingMessageUseCase.RejectionOutcome.PendingNotFound, result)
    }

    @Test
    fun `invoke with valid message sets status to REJECTED`() = runTest {
        val saved = slot<MessageApprovalState>()
        coEvery { messageRepository.getMessageApprovalStateById("m1") } returns approvalState(
            status = MessageStatus.PENDING,
        )

        val result = useCase("m1")

        assertTrue(result is RejectPendingMessageUseCase.RejectionOutcome.Rejected)
        assertEquals("m1", (result as RejectPendingMessageUseCase.RejectionOutcome.Rejected).id)

        coVerify { messageRepository.saveMessageApprovalState(capture(saved)) }
        assertEquals(MessageStatus.REJECTED, saved.captured.status)
    }

    @Test
    fun `invoke with approved message cancels scheduled send`() = runTest {
        coEvery { messageRepository.getMessageApprovalStateById("m1") } returns approvalState(
            status = MessageStatus.APPROVED,
        )

        useCase("m1")

        coVerify { schedulerService.cancelExactSend("m1") }
    }

    private fun approvalState(status: MessageStatus): MessageApprovalState {
        return MessageApprovalState(
            id = MessageDraftId("m1"),
            selectedVariantText = "",
            approvalMode = ApprovalMode.UNKNOWN,
            status = status,
            editedByUser = false,
            userEditedText = null,
        )
    }
}
