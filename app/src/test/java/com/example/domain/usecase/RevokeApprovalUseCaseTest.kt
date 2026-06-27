package com.example.domain.usecase

import com.example.domain.model.MessageStatus
import com.example.domain.model.ApprovalMode
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

class RevokeApprovalUseCaseTest {

    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val useCase = RevokeApprovalUseCase(messageRepository, schedulerService)

    @Test
    fun `invoke with approved message moves status back to pending and cancels schedule`() = runTest {
        val saved = slot<MessageApprovalState>()
        coEvery { messageRepository.getMessageApprovalStateById("msg_1") } returns approvalState(
            status = MessageStatus.APPROVED,
        )

        val result = useCase("msg_1")

        assertTrue(result is RevokeApprovalUseCase.RevokeOutcome.Revoked)
        assertEquals("msg_1", (result as RevokeApprovalUseCase.RevokeOutcome.Revoked).id)
        coVerify { messageRepository.saveMessageApprovalState(capture(saved)) }
        assertEquals(MessageStatus.PENDING, saved.captured.status)
        coVerify { schedulerService.cancelExactSend("msg_1") }
    }

    @Test
    fun `invoke with non-approved message returns NotApproved`() = runTest {
        coEvery { messageRepository.getMessageApprovalStateById("msg_1") } returns approvalState(
            status = MessageStatus.PENDING,
        )

        val result = useCase("msg_1")

        assertEquals(RevokeApprovalUseCase.RevokeOutcome.NotApproved, result)
        coVerify(exactly = 0) { messageRepository.saveMessageApprovalState(any()) }
        coVerify(exactly = 0) { schedulerService.cancelExactSend(any()) }
    }

    private fun approvalState(status: MessageStatus) = MessageApprovalState(
        id = MessageDraftId("msg_1"),
        selectedVariantText = "standard",
        approvalMode = ApprovalMode.SMART_APPROVE,
        status = status,
        editedByUser = false,
        userEditedText = null,
    )
}
