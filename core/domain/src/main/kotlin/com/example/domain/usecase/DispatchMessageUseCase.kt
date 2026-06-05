package com.example.domain.usecase

import com.example.core.db.entities.SentMessageEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.service.MessageDispatcherService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a pending message via SMS, WhatsApp, or Email.
 * - Looks up pending message by eventId (each event has at most one pending message)
 * - No-ops if pending message is not in APPROVED state
 * - No-ops if contact/pending message is missing
 * - Updates contact's last-wished timestamp and consecutive-years-wished count
 */
@Singleton
class DispatchMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val messageDispatcherService: MessageDispatcherService
) {
    suspend operator fun invoke(eventId: String): DispatchOutcome {
        val pending = messageRepository.getPendingByEventId(eventId) ?: return DispatchOutcome.PendingNotFound
        if (pending.status != "APPROVED") {
            return DispatchOutcome.NotApproved(pending.status)
        }

        val contact = contactRepository.getById(pending.contactId) ?: return DispatchOutcome.ContactNotFound

        messageDispatcherService.dispatch(pending, contact)

        contactRepository.updateLastWished(contact.id, System.currentTimeMillis())
        contactRepository.incrementConsecutiveYearsWished(contact.id)

        return DispatchOutcome.Sent(pending.id, pending.channel)
    }

    sealed class DispatchOutcome {
        data object PendingNotFound : DispatchOutcome()
        data object ContactNotFound : DispatchOutcome()
        data class NotApproved(val status: String) : DispatchOutcome()
        data class Sent(val pendingId: String, val channel: String) : DispatchOutcome()
    }
}
