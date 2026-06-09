package com.example.domain.usecase

import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rejects a pending message. Sets its status to REJECTED so it is excluded from dispatch.
 * The pending message is preserved in the database for audit purposes.
 */
@Singleton
class RejectPendingMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val schedulerService: SchedulerService,
) {
    suspend operator fun invoke(pendingMessageId: String): RejectionOutcome {
        val pending = messageRepository.getPendingById(pendingMessageId)
            ?: return RejectionOutcome.PendingNotFound

        messageRepository.updatePendingStatus(pendingMessageId, "REJECTED")
        if (pending.status == "APPROVED") {
            schedulerService.cancelExactSend(pending.id)
        }
        return RejectionOutcome.Rejected(pendingMessageId)
    }

    sealed class RejectionOutcome {
        val pendingNotFound: String = "PENDING_NOT_FOUND"
        data object PendingNotFound : RejectionOutcome()
        data class Rejected(val id: String) : RejectionOutcome()
    }
}
