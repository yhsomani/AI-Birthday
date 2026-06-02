package com.example.domain.usecase

import android.content.Context
import com.example.automation.scheduler.DailyScheduler
import com.example.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Approves a pending message. Sets its status to APPROVED and, if the approval mode
 * is FULLY_AUTO, schedules an exact-time dispatch via DailyScheduler.
 */
@Singleton
class ApprovePendingMessageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(pendingMessageId: String): ApprovalOutcome {
        messageRepository.updatePendingStatus(pendingMessageId, "APPROVED")
        val pending = messageRepository.getAllApproved().firstOrNull { it.id == pendingMessageId }
            ?: return ApprovalOutcome.PendingNotFound

        if (pending.approvalMode == "FULLY_AUTO") {
            DailyScheduler.scheduleExactSend(context, pending.eventId)
        }

        return ApprovalOutcome.Approved(pending.id, pending.approvalMode)
    }

    sealed class ApprovalOutcome {
        data object PendingNotFound : ApprovalOutcome()
        data class Approved(val id: String, val approvalMode: String) : ApprovalOutcome()
    }
}
