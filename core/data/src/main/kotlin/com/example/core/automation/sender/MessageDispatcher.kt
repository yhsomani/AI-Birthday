package com.example.core.automation.sender

import android.content.Context
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.dispatch.DispatchAttemptOutcomeUpdate
import com.example.domain.model.dispatch.MessageDispatchRequest
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

        val messageDraftId = request.messageId
        val eventRef = request.occasionReference.value
        val dispatchAttemptId = request.dispatchAttemptId?.value
        val messageText = request.messageText
        val preferredChannel = request.preferredChannel

        recordMessageDispatchLifecycleLog(
            messageDispatchStartedLog(
                messageId = messageDraftId,
                preferredChannel = preferredChannel,
                contactId = request.contactId,
            )
        )

        var routeLoopState = messageDispatchRouteLoopState()
        val primaryPhone = request.primaryPhone
        val primaryEmail = request.primaryEmail
        val dispatchOccasion = messageDispatchOccasion(eventDao, request.occasionReference)
        var finalChannel = preferredChannel
            .takeIf { it != MessageChannel.UNKNOWN }
            ?: MessageChannel.SMS
        val blockedChannels = DeliveryChannelResolver.parseBlockedChannels(
            prefs.getChannelBlackout()
        )
        val deliveryRoutes = DeliveryChannelResolver.resolveRoutes(
            preferredChannel = preferredChannel,
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
            senderEmail = prefs.getSenderEmail(),
            senderEmailPassword = prefs.getSenderEmailPassword(),
            blockedChannels = blockedChannels,
        )

        if (deliveryRoutes.isEmpty()) {
            recordMessageDispatchLifecycleLog(
                messageDispatchNoRouteLog(
                    messageId = messageDraftId,
                    preferredChannel = preferredChannel,
                    blockedChannels = blockedChannels,
                    hasPhone = !primaryPhone.isNullOrBlank(),
                    hasEmail = !primaryEmail.isNullOrBlank(),
                )
            )
        }

        for (route in deliveryRoutes) {
            finalChannel = route
            when (route) {
                MessageChannel.WHATSAPP -> {
                    if (primaryPhone != null) {
                        val routeResult = context.dispatchWhatsAppRouteWithFailureLog(
                            messageId = messageDraftId,
                            phoneNumber = primaryPhone,
                            messageText = messageText,
                            eventRef = eventRef,
                        )
                        routeLoopState = routeLoopState.applyRouteOutcome(
                            routeResult.toMessageDispatchRouteOutcome()
                        )
                    }
                }
                MessageChannel.SMS -> {
                    if (primaryPhone != null) {
                        val smsOutcome = context.dispatchSmsRouteWithSentMessageRecord(
                            sentMessageDao = sentMessageDao,
                            messageId = messageDraftId,
                            contactId = request.contactId,
                            dispatchOccasion = dispatchOccasion,
                            phoneNumber = primaryPhone,
                            contactDisplayName = request.contactDisplayName,
                            messageText = messageText,
                        )
                        routeLoopState = routeLoopState.applyRouteOutcome(
                            smsOutcome.toMessageDispatchRouteOutcome()
                        )
                    }
                }
                MessageChannel.EMAIL -> {
                    if (primaryEmail != null) {
                        val routeResult = prefs.dispatchEmailRouteWithFailureLog(
                            messageId = messageDraftId,
                            toEmail = primaryEmail,
                            contactName = request.contactDisplayName,
                            messageText = messageText,
                            eventType = dispatchOccasion.occasionType.raw,
                            eventLabel = dispatchOccasion.occasionLabel,
                        )
                        routeLoopState = routeLoopState.applyRouteOutcome(
                            routeResult.toMessageDispatchRouteOutcome()
                        )
                    }
                }
                MessageChannel.UNKNOWN -> Unit
            }
            if (routeLoopState.success) break
        }

        if (routeLoopState.success) {
            saveMessageDispatchAttemptOutcome(
                successfulDispatchAttemptOutcomeUpdate(
                    dispatchAttemptId = dispatchAttemptId,
                    resolvedAtMs = System.currentTimeMillis(),
                    channel = finalChannel,
                )
            )
            recordMessageDispatchLifecycleLog(
                messageDispatchSucceededLog(
                    messageId = messageDraftId,
                    channel = finalChannel,
                )
            )
            pendingMessageDao.savePendingMessageDispatchStatusUpdate(
                sentPendingMessageStatusUpdate(messageDraftId)
            )
            if (!routeLoopState.successfulSentMessageInserted) {
                sentMessageDao.saveSentMessageDispatchRecord(
                    successfulSentMessageDispatchRecord(
                        id = SentMessageId(UUID.randomUUID().toString()),
                        contactId = request.contactId,
                        dispatchOccasion = dispatchOccasion,
                        eventYear = Calendar.getInstance().get(Calendar.YEAR),
                        messageText = messageText,
                        channel = finalChannel,
                        sentAtMs = System.currentTimeMillis(),
                    )
                )
            }
            contactDao?.let { dao ->
                dao.saveContactPostDispatchUpdate(
                    contactPostDispatchUpdate(
                        contactId = request.contactId,
                        wishedAtMs = System.currentTimeMillis(),
                    )
                )
            }
        } else {
            pendingMessageDao.savePendingMessageDispatchStatusUpdate(
                failedPendingMessageStatusUpdate(messageDraftId)
            )
            val failedAtMs = System.currentTimeMillis()
            val failure = if (deliveryRoutes.isEmpty()) {
                DispatchProviderRetryPolicy.noDeliveryRoute()
            } else {
                routeLoopState.providerFailureSelection.failureOrDispatchFailure()
            }
            saveMessageDispatchAttemptOutcome(
                failedDispatchAttemptOutcomeUpdate(
                    dispatchAttemptId = dispatchAttemptId,
                    failedAtMs = failedAtMs,
                    channel = finalChannel,
                    failure = failure,
                )
            )
            recordMessageDispatchLifecycleLog(
                messageDispatchFailedLog(
                    messageId = messageDraftId,
                    preferredChannel = preferredChannel,
                    failure = failure,
                )
            )
            recordMessageDispatchFailureSideEffects(
                messageDispatchFailureSideEffects(
                    messageId = messageDraftId,
                    preferredChannel = preferredChannel,
                    messageText = messageText,
                    failure = failure,
                )
            )
        }
    }

    private suspend fun saveMessageDispatchAttemptOutcome(update: DispatchAttemptOutcomeUpdate?) {
        update ?: return
        runCatching {
            dispatchAttemptDao?.saveDispatchAttemptOutcome(update)
        }.onFailure { e ->
            recordMessageDispatchLifecycleLog(
                messageDispatchAttemptOutcomeUpdateFailedLog(
                    dispatchAttemptId = update.id.value,
                    cause = e,
                )
            )
        }
    }
}
