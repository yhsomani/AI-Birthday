package com.example.domain.usecase

import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

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
        val allPending = messageRepository.getAllPending().first()
        val pending = allPending.find { it.id == pendingMessageId }
            ?: return ApprovalOutcome.PendingNotFound

        val updatedPending = if (finalEditedText != null && finalEditedText != pending.selectedVariantText) {
            pending.copy(
                status = "APPROVED",
                editedByUser = true,
                userEditedText = finalEditedText,
                selectedVariantText = finalEditedText
            )
        } else {
            pending.copy(status = "APPROVED")
        }

        messageRepository.insertPending(updatedPending)

        if (updatedPending.approvalMode == "FULLY_AUTO") {
            schedulerService.scheduleExactSend(updatedPending.eventId)
        }

        return ApprovalOutcome.Approved(updatedPending.id, updatedPending.approvalMode)
    }

    sealed class ApprovalOutcome {
        data object PendingNotFound : ApprovalOutcome()
        data class Approved(val id: String, val approvalMode: String) : ApprovalOutcome()
    }
}
