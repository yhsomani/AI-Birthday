package com.example.domain.usecase

import com.example.domain.model.MessageStatus
import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Revokes approval for a pending message. Sets its status back to PENDING and
 * cancels the scheduled exact-time dispatch.
 */
@Singleton
class RevokeApprovalUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val schedulerService: SchedulerService
) {
    suspend operator fun invoke(pendingMessageId: String): RevokeOutcome {
        val pending = messageRepository.getPendingById(pendingMessageId)
            ?: return RevokeOutcome.PendingNotFound

        if (MessageStatus.fromRaw(pending.status) != MessageStatus.APPROVED) {
            return RevokeOutcome.NotApproved
        }

        val updatedPending = pending.copy(status = MessageStatus.PENDING.raw)
        messageRepository.insertPending(updatedPending)

        schedulerService.cancelExactSend(updatedPending.id)

        return RevokeOutcome.Revoked(updatedPending.id)
    }

    sealed class RevokeOutcome {
        data object PendingNotFound : RevokeOutcome()
        data object NotApproved : RevokeOutcome()
        data class Revoked(val id: String) : RevokeOutcome()
    }
}
