package com.example.domain.usecase

import android.content.Context
import com.example.automation.sender.MessageDispatcher
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.domain.repository.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: ContactDao,
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(eventId: String): DispatchOutcome {
        val pending = pendingMessageDao.getByEventId(eventId) ?: return DispatchOutcome.PendingNotFound
        if (pending.status != "APPROVED") {
            return DispatchOutcome.NotApproved(pending.status)
        }

        val contact = contactDao.getById(pending.contactId) ?: return DispatchOutcome.ContactNotFound

        val dispatcher = MessageDispatcher(context, pendingMessageDao, sentMessageDao)
        dispatcher.dispatch(pending, contact)

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
