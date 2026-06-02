package com.example.automation.sender

import android.content.Context
import android.util.Log
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.prefs.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class MessageDispatcher(
    private val context: Context,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao
) {
    suspend fun dispatch(message: PendingMessageEntity, contact: ContactEntity) = withContext(Dispatchers.IO) {
        val prefs = SecurePrefs(context)

        val messageText = when (message.selectedVariant) {
            "short" -> message.shortVariant
            "long" -> message.longVariant
            else -> message.standardVariant
        }

        var success = false
        val primaryPhone = contact.primaryPhone
        val primaryEmail = contact.primaryEmail
        when (message.channel) {
            "WHATSAPP" -> {
                if (primaryPhone != null) {
                    val waSender = WhatsAppSender(context)
                    success = waSender.send(primaryPhone, messageText, message.eventId)
                    if (!success) {
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
                    val emailSender = EmailSender(prefs)
                    emailSender.send(primaryEmail, contact.name, messageText)
                    success = true
                }
            }
        }

        if (success) {
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
        } else {
            pendingMessageDao.updateStatus(message.id, "FAILED")
            Log.w("MessageDispatcher", "Failed to dispatch message ${message.id} via ${message.channel}")
        }
    }
}
