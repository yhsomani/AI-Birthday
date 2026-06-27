package com.example.core.automation.sender

import android.content.Context
import com.example.core.data.R
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.SentMessageEntity
import com.example.core.prefs.SecurePrefs
import com.example.core.resilience.DeadLetterEntry
import com.example.core.resilience.DeadLetterQueue
import com.example.core.resilience.HealthMonitor
import com.example.core.resilience.StructuredLogger
import com.example.domain.event.toOccasion
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.MessageDispatchRequest
import com.example.domain.model.occasion.OccasionType
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
    private val dispatchAttemptDao: DispatchAttemptDao? = null,
) {
    suspend fun dispatch(request: MessageDispatchRequest) = withContext(Dispatchers.IO) {
        val prefs = SecurePrefs(context)

        val messageId = request.messageId.value
        val contactId = request.contactId.value
        val eventRef = request.occasionReference.value
        val dispatchAttemptId = request.dispatchAttemptId?.value
        val messageText = request.messageText
        val preferredChannel = request.preferredChannel

        StructuredLogger.i(TAG, "Dispatching message", mapOf(
            "messageId" to messageId,
            "channel" to preferredChannel.raw,
            "contactId" to contactId,
        ))

        var successfulSentMessageInserted = false
        var success = false
        val primaryPhone = request.primaryPhone
        val primaryEmail = request.primaryEmail
        val dispatchOccasion = resolveDispatchOccasion(eventRef)
        var finalChannel = preferredChannel
            .takeIf { it != MessageChannel.UNKNOWN }
            ?: MessageChannel.SMS
        val blockedChannels = prefs.getChannelBlackout().toChannelSet()
        val deliveryRoutes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = preferredChannel,
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
            senderEmail = prefs.getSenderEmail(),
            senderEmailPassword = prefs.getSenderEmailPassword(),
            blockedChannels = blockedChannels,
        )
        var providerFailure: ProviderDispatchFailure? = null

        if (deliveryRoutes.isEmpty()) {
            StructuredLogger.w(
                TAG,
                "No available delivery route for automatic message dispatch",
                extras = mapOf(
                    "messageId" to messageId,
                    "preferredChannel" to preferredChannel.raw,
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
                        success = waSender.send(primaryPhone, messageText, eventRef)
                        if (!success) {
                            providerFailure = DispatchProviderRetryPolicy.select(
                                providerFailure,
                                DispatchProviderRetryPolicy.whatsAppAutomationUnavailable(),
                            )
                            StructuredLogger.w(TAG, "WhatsApp route failed for $messageId; trying next automatic route")
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
                                contactId = contactId,
                                eventType = dispatchOccasion.occasionType,
                                eventId = dispatchOccasion.eventId,
                                occasionType = dispatchOccasion.occasionType,
                                occasionLabel = dispatchOccasion.occasionLabel,
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
                            providerFailure = DispatchProviderRetryPolicy.select(
                                providerFailure,
                                DispatchProviderRetryPolicy.smsPermissionDenied(),
                            )
                            StructuredLogger.e(TAG, "SMS permission not granted for message $messageId", e)
                            com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                                context,
                                context.getString(R.string.notification_setup_sms_permission_title),
                                context.getString(R.string.notification_setup_sms_permission_message, request.contactDisplayName),
                            )
                        } catch (e: Exception) {
                            if (smsAttemptInserted) {
                                sentMessageDao.updateDeliveryStatus(smsSentMessageId, MessageDeliveryStatus.FAILED.raw)
                            }
                            providerFailure = DispatchProviderRetryPolicy.select(
                                providerFailure,
                                DispatchProviderRetryPolicy.smsProviderException(e),
                            )
                            StructuredLogger.e(TAG, "SMS send failed for message $messageId", e)
                        }
                    }
                }
                MessageChannel.EMAIL -> {
                    if (primaryEmail != null) {
                        try {
                            val emailSender = EmailSender(prefs)
                            emailSender.send(
                                toEmail = primaryEmail,
                                contactName = request.contactDisplayName,
                                messageText = messageText,
                                eventType = dispatchOccasion.occasionType,
                                eventLabel = dispatchOccasion.occasionLabel,
                            )
                            success = true
                        } catch (e: Exception) {
                            providerFailure = DispatchProviderRetryPolicy.select(
                                providerFailure,
                                DispatchProviderRetryPolicy.emailProviderException(e),
                            )
                            StructuredLogger.e(TAG, "Email route failed for $messageId; trying next automatic route", e)
                        }
                    }
                }
                MessageChannel.UNKNOWN -> Unit
            }
            if (success) break
        }

        if (success) {
            updateDispatchAttemptOutcome(
                dispatchAttemptId = dispatchAttemptId,
                result = if (finalChannel == MessageChannel.SMS) {
                    DispatchAttemptResult.PENDING_DELIVERY
                } else {
                    DispatchAttemptResult.SENT
                },
                channel = finalChannel,
                deliveryStatus = if (finalChannel == MessageChannel.SMS) {
                    MessageDeliveryStatus.PENDING_DELIVERY
                } else {
                    MessageDeliveryStatus.SENT
                },
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                deadLetteredAtMs = null,
            )
            StructuredLogger.i(TAG, "Message dispatched successfully", mapOf(
                "messageId" to messageId,
                "channel" to finalChannel.raw,
            ))
            pendingMessageDao.updateStatus(messageId, MessageStatus.SENT.raw)
            if (!successfulSentMessageInserted) {
                sentMessageDao.insert(SentMessageEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = contactId,
                    eventType = dispatchOccasion.occasionType,
                    eventId = dispatchOccasion.eventId,
                    occasionType = dispatchOccasion.occasionType,
                    occasionLabel = dispatchOccasion.occasionLabel,
                    eventYear = Calendar.getInstance().get(Calendar.YEAR),
                    messageText = messageText,
                    channel = finalChannel.raw,
                    sentAtMs = System.currentTimeMillis(),
                    deliveryStatus = MessageDeliveryStatus.SENT.raw,
                    aiGenerated = true
                ))
            }
            contactDao?.let { dao ->
                dao.updateLastWished(contactId, System.currentTimeMillis())
                dao.incrementConsecutiveYearsWished(contactId)
                dao.updateHealthScoreDelta(contactId, 5)
            }
        } else {
            pendingMessageDao.updateStatus(messageId, MessageStatus.FAILED.raw)
            val failedAtMs = System.currentTimeMillis()
            val failure = if (deliveryRoutes.isEmpty()) {
                DispatchProviderRetryPolicy.noDeliveryRoute()
            } else {
                providerFailure ?: DispatchProviderRetryPolicy.dispatchFailure()
            }
            val nextRetryAtMs = failure.nextRetryDelayMs?.let { delayMs -> failedAtMs + delayMs }
            val deadLetteredAtMs = if (failure.result == DispatchAttemptResult.FAILED_FINAL) failedAtMs else null
            updateDispatchAttemptOutcome(
                dispatchAttemptId = dispatchAttemptId,
                result = failure.result,
                channel = finalChannel,
                deliveryStatus = MessageDeliveryStatus.FAILED,
                errorType = failure.errorType,
                errorCode = failure.errorCode,
                redactedErrorMessage = failure.redactedErrorMessage,
                deadLetteredAtMs = deadLetteredAtMs,
                nextRetryAtMs = nextRetryAtMs,
            )
            StructuredLogger.w(TAG, "Failed to dispatch message $messageId via ${preferredChannel.raw}: ${failure.errorType}")
            HealthMonitor.recordError(
                "MessageDispatcher.dispatch",
                "Failed to send $messageId via ${preferredChannel.raw}: ${failure.errorType}",
            )
            if (failure.result == DispatchAttemptResult.FAILED_FINAL) {
                DeadLetterQueue.enqueue(DeadLetterEntry(
                    id = messageId,
                    payload = messageText,
                    errorMessage = failure.redactedErrorMessage,
                    errorType = failure.errorType,
                    retryCount = 0,
                ))
            }
        }
    }

    private suspend fun updateDispatchAttemptOutcome(
        dispatchAttemptId: String?,
        result: DispatchAttemptResult,
        channel: MessageChannel,
        deliveryStatus: MessageDeliveryStatus,
        errorType: String?,
        errorCode: String?,
        redactedErrorMessage: String?,
        deadLetteredAtMs: Long?,
        nextRetryAtMs: Long? = null,
    ) {
        if (dispatchAttemptId.isNullOrBlank()) return
        val resolvedAtMs = System.currentTimeMillis()
        runCatching {
            dispatchAttemptDao?.updateOutcome(
                id = dispatchAttemptId,
                attemptedAtMs = resolvedAtMs,
                resolvedAtMs = resolvedAtMs,
                result = result.raw,
                channel = channel.raw,
                deliveryStatus = deliveryStatus.raw,
                providerMessageId = null,
                errorType = errorType,
                errorCode = errorCode,
                redactedErrorMessage = redactedErrorMessage,
                retryCount = 0,
                nextRetryAtMs = nextRetryAtMs,
                deadLetteredAtMs = deadLetteredAtMs,
            )
        }.onFailure { e ->
            StructuredLogger.e(TAG, "Failed to update dispatch attempt $dispatchAttemptId", e)
        }
    }

    private suspend fun resolveDispatchOccasion(eventRef: String): DispatchOccasion {
        val occasion = eventDao?.getById(eventRef)?.toOccasion()
        if (occasion != null) {
            return DispatchOccasion(
                eventId = occasion.id.value,
                occasionType = occasion.type.raw,
                occasionLabel = occasion.label,
            )
        }
        return DispatchOccasion(
            eventId = null,
            occasionType = classifyOccasionType(eventRef),
            occasionLabel = null,
        )
    }

    private fun classifyOccasionType(eventRef: String): String {
        val normalized = eventRef.trim().uppercase()
        val explicitType = OccasionType.fromRaw(normalized)
        if (explicitType != OccasionType.UNKNOWN) return explicitType.raw
        return when {
            normalized.startsWith("FOLLOWUP_") || normalized.startsWith("FOLLOW_UP_") -> OccasionType.FOLLOW_UP.raw
            normalized.startsWith("HOLIDAY_") -> OccasionType.HOLIDAY.raw
            normalized.startsWith("REVIVAL_") -> OccasionType.REVIVAL.raw
            else -> OccasionType.UNKNOWN.raw
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

    private data class DispatchOccasion(
        val eventId: String?,
        val occasionType: String,
        val occasionLabel: String?,
    )
}
