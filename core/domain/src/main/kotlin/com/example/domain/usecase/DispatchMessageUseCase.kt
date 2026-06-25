package com.example.domain.usecase

import com.example.core.db.entities.SentMessageEntity
import com.example.domain.automation.DispatchDecision
import com.example.domain.automation.DispatchEligibilityPolicy
import com.example.domain.model.MessageStatus
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.MessageDispatcherService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a pending message via SMS, WhatsApp, or Email.
 * - Looks up pending message by id first, then eventId for legacy callers
 * - Uses the shared dispatch eligibility policy before sending
 * - No-ops if contact/pending message is missing
 * - Updates contact's last-wished timestamp and consecutive-years-wished count
 */
@Singleton
class DispatchMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val messageDispatcherService: MessageDispatcherService
) {
    suspend operator fun invoke(messageRef: String): DispatchOutcome {
        val pending = messageRepository.getPendingById(messageRef)
            ?: messageRepository.getPendingByEventId(messageRef)
            ?: return DispatchOutcome.PendingNotFound

        when (val decision = DispatchEligibilityPolicy.evaluate(pending)) {
            DispatchDecision.SendNow -> Unit
            is DispatchDecision.DeferUntil -> {
                return DispatchOutcome.Deferred(pending.id, decision.epochMs)
            }
            is DispatchDecision.NeedsApproval -> {
                return DispatchOutcome.NotApproved(pending.status)
            }
            is DispatchDecision.Expire -> {
                messageRepository.updatePendingStatus(pending.id, MessageStatus.EXPIRED.raw)
                return DispatchOutcome.Expired(pending.id)
            }
            is DispatchDecision.Blocked -> {
                return DispatchOutcome.NotApproved(pending.status)
            }
        }

        val contact = contactRepository.getById(pending.contactId) ?: return DispatchOutcome.ContactNotFound

        messageDispatcherService.dispatch(pending, contact)

        return DispatchOutcome.Sent(pending.id, pending.channel)
    }

    sealed class DispatchOutcome {
        data object PendingNotFound : DispatchOutcome()
        data object ContactNotFound : DispatchOutcome()
        data class NotApproved(val status: String) : DispatchOutcome()
        data class Deferred(val pendingId: String, val scheduledForMs: Long) : DispatchOutcome()
        data class Expired(val pendingId: String) : DispatchOutcome()
        data class Sent(val pendingId: String, val channel: String) : DispatchOutcome()
    }
}
