package com.example.core.automation.sender

import android.content.Context
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.MessageChannel
import com.example.domain.model.dispatch.MessageDispatchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val routePlan = messageDispatchRoutePlan(
            preferredChannel = preferredChannel,
            primaryPhone = primaryPhone,
            primaryEmail = primaryEmail,
            senderEmail = prefs.getSenderEmail(),
            senderEmailPassword = prefs.getSenderEmailPassword(),
            channelBlackoutJson = prefs.getChannelBlackout(),
        )
        var finalChannel = routePlan.initialFinalChannel

        if (routePlan.noDeliveryRoute) {
            recordMessageDispatchLifecycleLog(
                messageDispatchNoRouteLog(
                    messageId = messageDraftId,
                    preferredChannel = preferredChannel,
                    blockedChannels = routePlan.blockedChannels,
                    hasPhone = !primaryPhone.isNullOrBlank(),
                    hasEmail = !primaryEmail.isNullOrBlank(),
                )
            )
        }

        for (route in routePlan.deliveryRoutes) {
            finalChannel = route
            when (route) {
                MessageChannel.WHATSAPP -> {
                    if (primaryPhone != null) {
                        val routeResult = context.dispatchWhatsAppRouteWithFailureLog(
                            messageId = messageDraftId,
                            phoneNumber = primaryPhone,
                            messageText = messageText,
                            eventRef = eventRef,
                            automationConsentGranted = prefs.isWhatsAppAutomationConsentGranted(),
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

        saveMessageDispatchFinalization(
            dispatchAttemptDao = dispatchAttemptDao,
            pendingMessageDao = pendingMessageDao,
            sentMessageDao = sentMessageDao,
            contactDao = contactDao,
            messageId = messageDraftId,
            contactId = request.contactId,
            dispatchAttemptId = dispatchAttemptId,
            preferredChannel = preferredChannel,
            finalChannel = finalChannel,
            dispatchOccasion = dispatchOccasion,
            messageText = messageText,
            routeLoopState = routeLoopState,
            noDeliveryRoute = routePlan.noDeliveryRoute,
        )
    }
}
