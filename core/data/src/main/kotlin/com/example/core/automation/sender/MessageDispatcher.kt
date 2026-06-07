package com.example.core.automation.sender

import android.content.Context
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.DeadLetterEntry
import com.example.core.resilience.DeadLetterQueue
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.StructuredLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class MessageDispatcher(
    private val context: Context,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: com.example.core.db.dao.ContactDao? = null
) {
    suspend fun dispatch(message: PendingMessageEntity, contact: ContactEntity) = withContext(Dispatchers.IO) {
        val prefs = SecurePrefs(context)

        val messageText: String = (if (message.editedByUser) message.userEditedText else null) ?: message.selectedVariantText.ifBlank {
            when (message.selectedVariant) {
                "short" -> message.shortVariant
                "long" -> message.longVariant
                "funny" -> message.funnyVariant
                "formal" -> message.formalVariant
                else -> message.standardVariant
            }
        }

        StructuredLogger.i(TAG, "Dispatching message", mapOf(
            "messageId" to message.id,
            "channel" to message.channel,
            "contactId" to contact.id,
        ))

        var success = false
        val primaryPhone = contact.primaryPhone
        val primaryEmail = contact.primaryEmail
        when (message.channel) {
            "WHATSAPP" -> {
                if (primaryPhone != null) {
                    val waSender = WhatsAppSender(context)
                    success = waSender.send(primaryPhone, messageText, message.eventId)
                    if (!success) {
                        StructuredLogger.w(TAG, "WhatsApp failed, falling back to SMS for ${message.id}")
                        val smsSender = SmsSender(context)
                        smsSender.send(primaryPhone, messageText, message.eventId)
                        success = true
                    }
                }
            }
            "SMS" -> {
                if (primaryPhone != null) {
                    val smsSender = SmsSender(context)
                    smsSender.send(primaryPhone, messageText, message.eventId)
                    success = true
                }
            }
            "EMAIL" -> {
                if (primaryEmail != null) {
                    try {
                        val emailSender = EmailSender(prefs)
                        emailSender.send(primaryEmail, contact.name, messageText)
                        success = true
                    } catch (e: Exception) {
                        StructuredLogger.e(TAG, "Email send failed for ${message.id}", e)
                    }
                }
            }
        }

        if (success) {
            StructuredLogger.i(TAG, "Message dispatched successfully", mapOf(
                "messageId" to message.id,
                "channel" to message.channel,
            ))
            pendingMessageDao.updateStatus(message.id, "SENT")
            sentMessageDao.insert(SentMessageEntity(
                id = UUID.randomUUID().toString(),
                contactId = message.contactId,
                eventType = message.eventId,
                eventYear = Calendar.getInstance().get(Calendar.YEAR),
                messageText = messageText,
                channel = message.channel,
                sentAtMs = System.currentTimeMillis(),
                deliveryStatus = "SENT",
                aiGenerated = true
            ))
            contactDao?.let { dao ->
                dao.updateLastWished(contact.id, System.currentTimeMillis())
                dao.incrementConsecutiveYearsWished(contact.id)
                dao.updateHealthScoreDelta(contact.id, 5)
            }
        } else {
            pendingMessageDao.updateStatus(message.id, "FAILED")
            StructuredLogger.w(TAG, "Failed to dispatch message ${message.id} via ${message.channel}")
            HealthMonitor.recordError("MessageDispatcher.dispatch", "Failed to send ${message.id} via ${message.channel}")
            DeadLetterQueue.enqueue(DeadLetterEntry(
                id = message.id,
                payload = messageText,
                errorMessage = "All channels failed for ${message.channel}",
                errorType = "DISPATCH_FAILURE",
                retryCount = 0,
            ))
        }
    }

    companion object {
        private const val TAG = "MessageDispatcher"
    }
}
