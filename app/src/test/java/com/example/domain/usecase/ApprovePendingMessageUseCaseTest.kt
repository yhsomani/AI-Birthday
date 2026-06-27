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

class ApprovePendingMessageUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val useCase = ApprovePendingMessageUseCase(messageRepository, schedulerService)

    @Test
    fun `invoke with valid pendingMessageId updates status to APPROVED`() = runTest {
        val saved = slot<MessageApprovalState>()
        coEvery { messageRepository.getMessageApprovalStateById("msg_1") } returns approvalState(
            approvalMode = ApprovalMode.UNKNOWN,
            status = MessageStatus.PENDING,
        )

        val result = useCase("msg_1")

        assertTrue(result is ApprovePendingMessageUseCase.ApprovalOutcome.Approved)
        val approved = result as ApprovePendingMessageUseCase.ApprovalOutcome.Approved
        assertEquals("msg_1", approved.id)
        assertEquals(ApprovalMode.UNKNOWN, approved.approvalMode)
        coVerify { messageRepository.saveMessageApprovalState(capture(saved)) }
        assertEquals(MessageStatus.APPROVED, saved.captured.status)
        coVerify { schedulerService.scheduleExactSend("msg_1") }
    }

    @Test
    fun `invoke with any approval mode schedules exact send`() = runTest {
        coEvery { messageRepository.getMessageApprovalStateById("msg_1") } returns approvalState(
            approvalMode = ApprovalMode.FULLY_AUTO,
            status = MessageStatus.PENDING,
        )

        useCase("msg_1")

        coVerify { schedulerService.scheduleExactSend("msg_1") }
    }

    @Test
    fun `invoke with invalid id returns PendingNotFound`() = runTest {
        coEvery { messageRepository.getMessageApprovalStateById("invalid_id") } returns null

        val result = useCase("invalid_id")

        assertEquals(ApprovePendingMessageUseCase.ApprovalOutcome.PendingNotFound, result)
    }

    private fun approvalState(
        approvalMode: ApprovalMode,
        status: MessageStatus,
    ): MessageApprovalState {
        return MessageApprovalState(
            id = MessageDraftId("msg_1"),
            selectedVariantText = "hi",
            approvalMode = approvalMode,
            status = status,
            editedByUser = false,
            userEditedText = null,
        )
    }
}
