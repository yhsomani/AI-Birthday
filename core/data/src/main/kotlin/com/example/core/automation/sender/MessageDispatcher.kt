package com.example.core.automation.sender

import android.content.Context
import com.example.core.data.R
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

        val sentMessageId = UUID.randomUUID().toString()
        var isSentMessageInserted = false
        var success = false
        val primaryPhone = contact.primaryPhone
        val primaryEmail = contact.primaryEmail
        var finalChannel = message.channel
        val blockedChannels = prefs.getChannelBlackout().toChannelSet()

        if (message.channel.uppercase() in blockedChannels) {
            StructuredLogger.w(TAG, "Dispatch channel is disabled", extras = mapOf("channel" to message.channel))
        } else {
            when (message.channel) {
                "WHATSAPP" -> {
                    if (primaryPhone != null) {
                        val waSender = WhatsAppSender(context)
                        success = waSender.send(primaryPhone, messageText, message.eventId)
                        if (!success) {
                            StructuredLogger.w(TAG, "WhatsApp failed, falling back to SMS for ${message.id}")
                            finalChannel = "SMS"
                            if ("SMS" in blockedChannels) {
                                StructuredLogger.w(TAG, "SMS fallback skipped because channel is disabled")
                            } else {
                                try {
                                    sentMessageDao.insert(SentMessageEntity(
                                        id = sentMessageId,
                                        contactId = message.contactId,
                                        eventType = message.eventId,
                                        eventYear = Calendar.getInstance().get(Calendar.YEAR),
                                        messageText = messageText,
                                        channel = "SMS",
                                        sentAtMs = System.currentTimeMillis(),
                                        deliveryStatus = "PENDING_DELIVERY",
                                        aiGenerated = true
                                    ))
                                    isSentMessageInserted = true

                                    val smsSender = SmsSender(context)
                                    smsSender.send(primaryPhone, messageText, sentMessageId)
                                    success = true
                                } catch (e: SecurityException) {
                                    if (isSentMessageInserted) {
                                        sentMessageDao.updateDeliveryStatus(sentMessageId, "FAILED")
                                    }
                                    StructuredLogger.e(TAG, "SMS permission not granted during WhatsApp fallback for message ${message.id}", e)
                                    com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                                        context,
                                        context.getString(R.string.notification_setup_sms_permission_title),
                                        context.getString(R.string.notification_setup_sms_permission_message, contact.name),
                                    )
                                } catch (e: Exception) {
                                    if (isSentMessageInserted) {
                                        sentMessageDao.updateDeliveryStatus(sentMessageId, "FAILED")
                                    }
                                    StructuredLogger.e(TAG, "SMS send failed during WhatsApp fallback for message ${message.id}", e)
                                }
                            }
                        }
                    }
                }
                "SMS" -> {
                    if (primaryPhone != null) {
                        try {
                            sentMessageDao.insert(SentMessageEntity(
                                id = sentMessageId,
                                contactId = message.contactId,
                                eventType = message.eventId,
                                eventYear = Calendar.getInstance().get(Calendar.YEAR),
                                messageText = messageText,
                                channel = "SMS",
                                sentAtMs = System.currentTimeMillis(),
                                deliveryStatus = "PENDING_DELIVERY",
                                aiGenerated = true
                            ))
                            isSentMessageInserted = true

                            val smsSender = SmsSender(context)
                            smsSender.send(primaryPhone, messageText, sentMessageId)
                            success = true
                        } catch (e: SecurityException) {
                            if (isSentMessageInserted) {
                                sentMessageDao.updateDeliveryStatus(sentMessageId, "FAILED")
                            }
                            StructuredLogger.e(TAG, "SMS permission not granted for message ${message.id}", e)
                            com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                                context,
                                context.getString(R.string.notification_setup_sms_permission_title),
                                context.getString(R.string.notification_setup_sms_permission_message, contact.name),
                            )
                        } catch (e: Exception) {
                            if (isSentMessageInserted) {
                                sentMessageDao.updateDeliveryStatus(sentMessageId, "FAILED")
                            }
                            StructuredLogger.e(TAG, "SMS send failed for message ${message.id}", e)
                        }
                    }
                }
                "EMAIL" -> {
                    if (primaryEmail != null) {
                        if (prefs.getSenderEmail().isBlank() || prefs.getSenderEmailPassword().isBlank()) {
                            com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                                context,
                                context.getString(R.string.notification_setup_email_needed_title),
                                context.getString(R.string.notification_setup_email_needed_message),
                            )
                            success = false
                        } else {
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
            }
        }

        if (success) {
            StructuredLogger.i(TAG, "Message dispatched successfully", mapOf(
                "messageId" to message.id,
                "channel" to finalChannel,
            ))
            pendingMessageDao.updateStatus(message.id, "SENT")
            if (!isSentMessageInserted) {
                sentMessageDao.insert(SentMessageEntity(
                    id = sentMessageId,
                    contactId = message.contactId,
                    eventType = message.eventId,
                    eventYear = Calendar.getInstance().get(Calendar.YEAR),
                    messageText = messageText,
                    channel = finalChannel,
                    sentAtMs = System.currentTimeMillis(),
                    deliveryStatus = "SENT",
                    aiGenerated = true
                ))
            }
            contactDao?.let { dao ->
                dao.updateLastWished(contact.id, System.currentTimeMillis())
                dao.incrementConsecutiveYearsWished(contact.id)
                dao.updateHealthScoreDelta(contact.id, 5)
            }
        } else {
            pendingMessageDao.updateStatus(message.id, "FAILED")
            if (isSentMessageInserted) {
                sentMessageDao.updateDeliveryStatus(sentMessageId, "FAILED")
            }
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

    private fun String.toChannelSet(): Set<String> {
        return try {
            val array = org.json.JSONArray(this)
            List(array.length()) { index -> array.optString(index).uppercase() }
                .filter { it in setOf("SMS", "WHATSAPP", "EMAIL") }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
