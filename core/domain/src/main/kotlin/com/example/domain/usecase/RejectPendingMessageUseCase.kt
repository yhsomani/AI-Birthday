package com.example.domain.usecase

import com.example.domain.repository.MessageRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rejects a pending message. Sets its status to REJECTED so it is excluded from dispatch.
 * The pending message is preserved in the database for audit purposes.
 */
@Singleton
class RejectPendingMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(pendingMessageId: String): RejectionOutcome {
        val exists = messageRepository.getAllPending().first().any { it.id == pendingMessageId }
        if (!exists) return RejectionOutcome.PendingNotFound

        messageRepository.updatePendingStatus(pendingMessageId, "REJECTED")
        return RejectionOutcome.Rejected(pendingMessageId)
    }

    sealed class RejectionOutcome {
        val pendingNotFound: String = "PENDING_NOT_FOUND"
        data object PendingNotFound : RejectionOutcome()
        data class Rejected(val id: String) : RejectionOutcome()
    }
}
