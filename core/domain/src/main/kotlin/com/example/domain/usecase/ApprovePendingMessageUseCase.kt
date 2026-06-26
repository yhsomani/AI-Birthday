package com.example.domain.usecase

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus
import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Approves a pending message. Sets its status to APPROVED and, if the approval mode
 * is FULLY_AUTO, schedules an exact-time dispatch via SchedulerService.
 */
@Singleton
class ApprovePendingMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val schedulerService: SchedulerService
) {
    suspend operator fun invoke(pendingMessageId: String, finalEditedText: String? = null): ApprovalOutcome {
        val pending = messageRepository.getPendingById(pendingMessageId)
            ?: return ApprovalOutcome.PendingNotFound

        val updatedPending = if (finalEditedText != null && finalEditedText != pending.selectedVariantText) {
            pending.copy(
                status = MessageStatus.APPROVED.raw,
                editedByUser = true,
                userEditedText = finalEditedText,
                selectedVariantText = finalEditedText
            )
        } else {
            pending.copy(status = MessageStatus.APPROVED.raw)
        }

        messageRepository.insertPending(updatedPending)

        // Always schedule exactly when approved from the UI
        schedulerService.scheduleExactSend(updatedPending.id)

        return ApprovalOutcome.Approved(
            id = updatedPending.id,
            approvalMode = ApprovalMode.fromRaw(updatedPending.approvalMode),
        )
    }

    sealed class ApprovalOutcome {
        data object PendingNotFound : ApprovalOutcome()
        data class Approved(val id: String, val approvalMode: ApprovalMode) : ApprovalOutcome()
    }
}
