package com.example.domain.usecase

import android.content.Context
import com.example.automation.scheduler.DailyScheduler
import com.example.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.first

/**
 * Approves a pending message. Sets its status to APPROVED and, if the approval mode
 * is FULLY_AUTO, schedules an exact-time dispatch via DailyScheduler.
 */
@Singleton
class ApprovePendingMessageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository
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
            DailyScheduler.scheduleExactSend(context, updatedPending.eventId)
        }

        return ApprovalOutcome.Approved(updatedPending.id, updatedPending.approvalMode)
    }

    sealed class ApprovalOutcome {
        data object PendingNotFound : ApprovalOutcome()
        data class Approved(val id: String, val approvalMode: String) : ApprovalOutcome()
    }
}
