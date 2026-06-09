package com.example.domain.usecase

import com.example.domain.repository.MessageRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

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
        val allPending = messageRepository.getAllPending().first()
        val pending = allPending.find { it.id == pendingMessageId }
            ?: return RevokeOutcome.PendingNotFound

        if (pending.status != "APPROVED") {
            return RevokeOutcome.NotApproved
        }

        val updatedPending = pending.copy(status = "PENDING")
        messageRepository.insertPending(updatedPending)

        schedulerService.cancelExactSend(updatedPending.eventId)

        return RevokeOutcome.Revoked(updatedPending.id)
    }

    sealed class RevokeOutcome {
        data object PendingNotFound : RevokeOutcome()
        data object NotApproved : RevokeOutcome()
        data class Revoked(val id: String) : RevokeOutcome()
    }
}
