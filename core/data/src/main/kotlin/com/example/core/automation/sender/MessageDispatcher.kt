package com.example.core.automation.sender

import android.content.Context
import com.example.core.data.R
import com.example.core.db.dao.EventDao
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
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class MessageDispatcher(
    private val context: Context,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val contactDao: com.example.core.db.dao.ContactDao? = null,
    private val eventDao: EventDao? = null,
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

        var successfulSentMessageInserted = false
        var success = false
        val primaryPhone = contact.primaryPhone
        val primaryEmail = contact.primaryEmail
        var finalChannel = MessageChannel.fromRaw(message.channel)
            .takeIf { it != MessageChannel.UNKNOWN }
            ?: MessageChannel.SMS
        val blockedChannels = prefs.getChannelBlackout().toChannelSet()
        val deliveryRoutes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = MessageChannel.fromRaw(message.channel),
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
            senderEmail = prefs.getSenderEmail(),
            senderEmailPassword = prefs.getSenderEmailPassword(),
            blockedChannels = blockedChannels,
        )

        if (deliveryRoutes.isEmpty()) {
            StructuredLogger.w(
                TAG,
                "No available delivery route for automatic message dispatch",
                extras = mapOf(
                    "messageId" to message.id,
                    "preferredChannel" to message.channel,
                    "blockedChannels" to blockedChannels.joinToString(",") { it.raw },
                    "hasPhone" to (!primaryPhone.isNullOrBlank()).toString(),
                    "hasEmail" to (!primaryEmail.isNullOrBlank()).toString(),
                ),
            )
        }

        for (route in deliveryRoutes) {
            finalChannel = route
            when (route) {
                MessageChannel.WHATSAPP -> {
                    if (primaryPhone != null) {
                        val waSender = WhatsAppSender(context)
                        success = waSender.send(primaryPhone, messageText, message.eventId)
                        if (!success) {
                            StructuredLogger.w(TAG, "WhatsApp route failed for ${message.id}; trying next automatic route")
                        }
                    }
                }
                MessageChannel.SMS -> {
                    if (primaryPhone != null) {
                        val smsSentMessageId = UUID.randomUUID().toString()
                        var smsAttemptInserted = false
                        try {
                            sentMessageDao.insert(SentMessageEntity(
                                id = smsSentMessageId,
                                contactId = message.contactId,
                                eventType = message.eventId,
                                eventYear = Calendar.getInstance().get(Calendar.YEAR),
                                messageText = messageText,
                                channel = MessageChannel.SMS.raw,
                                sentAtMs = System.currentTimeMillis(),
                                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
                                aiGenerated = true
                            ))
                            smsAttemptInserted = true

                            val smsSender = SmsSender(context)
                            smsSender.send(primaryPhone, messageText, smsSentMessageId)
                            successfulSentMessageInserted = true
                            success = true
                        } catch (e: SecurityException) {
                            if (smsAttemptInserted) {
                                sentMessageDao.updateDeliveryStatus(smsSentMessageId, MessageDeliveryStatus.FAILED.raw)
                            }
                            StructuredLogger.e(TAG, "SMS permission not granted for message ${message.id}", e)
                            com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                                context,
                                context.getString(R.string.notification_setup_sms_permission_title),
                                context.getString(R.string.notification_setup_sms_permission_message, contact.name),
                            )
                        } catch (e: Exception) {
                            if (smsAttemptInserted) {
                                sentMessageDao.updateDeliveryStatus(smsSentMessageId, MessageDeliveryStatus.FAILED.raw)
                            }
                            StructuredLogger.e(TAG, "SMS send failed for message ${message.id}", e)
                        }
                    }
                }
                MessageChannel.EMAIL -> {
                    if (primaryEmail != null) {
                        try {
                            val event = eventDao?.getById(message.eventId)
                            val emailSender = EmailSender(prefs)
                            emailSender.send(
                                toEmail = primaryEmail,
                                contactName = contact.name,
                                messageText = messageText,
                                eventType = event?.type,
                                eventLabel = event?.label,
                            )
                            success = true
                        } catch (e: Exception) {
                            StructuredLogger.e(TAG, "Email route failed for ${message.id}; trying next automatic route", e)
                        }
                    }
                }
                MessageChannel.UNKNOWN -> Unit
            }
            if (success) break
        }

        if (success) {
            StructuredLogger.i(TAG, "Message dispatched successfully", mapOf(
                "messageId" to message.id,
                "channel" to finalChannel.raw,
            ))
            pendingMessageDao.updateStatus(message.id, MessageStatus.SENT.raw)
            if (!successfulSentMessageInserted) {
                sentMessageDao.insert(SentMessageEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = message.contactId,
                    eventType = message.eventId,
                    eventYear = Calendar.getInstance().get(Calendar.YEAR),
                    messageText = messageText,
                    channel = finalChannel.raw,
                    sentAtMs = System.currentTimeMillis(),
                    deliveryStatus = MessageDeliveryStatus.SENT.raw,
                    aiGenerated = true
                ))
            }
            contactDao?.let { dao ->
                dao.updateLastWished(contact.id, System.currentTimeMillis())
                dao.incrementConsecutiveYearsWished(contact.id)
                dao.updateHealthScoreDelta(contact.id, 5)
            }
        } else {
            pendingMessageDao.updateStatus(message.id, MessageStatus.FAILED.raw)
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

    private fun String.toChannelSet(): Set<MessageChannel> {
        return CHANNEL_TOKEN_PATTERN.findAll(this)
            .map { MessageChannel.fromRaw(it.groupValues[1]) }
            .filter { it != MessageChannel.UNKNOWN }
            .toSet()
    }

    private companion object {
        private const val TAG = "MessageDispatcher"
        val CHANNEL_TOKEN_PATTERN = Regex("\"([A-Za-z_]+)\"")
    }
}
