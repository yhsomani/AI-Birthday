package com.example.core.automation.sender

import android.content.Context
import com.example.core.db.dao.SentMessageDao
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.SentMessageId
import com.example.domain.model.dispatch.MessageDispatchOccasion
import java.util.Calendar
import java.util.UUID

internal data class SmsRouteDispatchOutcome(
    val sent: Boolean,
    val successfulSentMessageInserted: Boolean,
    val failure: ProviderDispatchFailure?,
)

internal suspend fun Context.dispatchSmsRouteWithSentMessageRecord(
    sentMessageDao: SentMessageDao,
    messageId: MessageDraftId,
    contactId: ContactId,
    dispatchOccasion: MessageDispatchOccasion,
    phoneNumber: String,
    contactDisplayName: String,
    messageText: String,
    sentMessageId: SentMessageId = SentMessageId(UUID.randomUUID().toString()),
    eventYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    sentAtMs: Long = System.currentTimeMillis(),
): SmsRouteDispatchOutcome {
    var smsAttemptInserted = false
    return try {
        sentMessageDao.saveSentMessageDispatchRecord(
            smsPendingDeliverySentMessageDispatchRecord(
                id = sentMessageId,
                contactId = contactId,
                dispatchOccasion = dispatchOccasion,
                eventYear = eventYear,
                messageText = messageText,
                sentAtMs = sentAtMs,
            )
        )
        smsAttemptInserted = true

        val routeResult = dispatchSmsRoute(
            phoneNumber = phoneNumber,
            messageText = messageText,
            sentMessageId = sentMessageId.value,
        )
        if (routeResult.sent) {
            SmsRouteDispatchOutcome(
                sent = true,
                successfulSentMessageInserted = true,
                failure = null,
            )
        } else {
            if (smsAttemptInserted) {
                sentMessageDao.saveSentMessageDeliveryStatusUpdate(
                    failedSentMessageDeliveryStatusUpdate(sentMessageId)
                )
            }
            routeResult.failure?.let { failure ->
                recordMessageDispatchRouteFailureLog(
                    messageDispatchRouteFailureLog(
                        messageId = messageId,
                        channel = MessageChannel.SMS,
                        failure = failure,
                        cause = routeResult.cause,
                    )
                )
                if (failure.requiresSmsPermissionSetupNotification()) {
                    showSmsPermissionSetupNotification(
                        smsPermissionSetupNotificationRequest(contactDisplayName)
                    )
                }
            }
            SmsRouteDispatchOutcome(
                sent = false,
                successfulSentMessageInserted = false,
                failure = routeResult.failure,
            )
        }
    } catch (e: Exception) {
        if (smsAttemptInserted) {
            sentMessageDao.saveSentMessageDeliveryStatusUpdate(
                failedSentMessageDeliveryStatusUpdate(sentMessageId)
            )
        }
        val failure = smsDispatchExceptionFailure(e)
        recordMessageDispatchRouteFailureLog(
            messageDispatchRouteFailureLog(
                messageId = messageId,
                channel = MessageChannel.SMS,
                failure = failure,
                cause = e,
            )
        )
        if (failure.requiresSmsPermissionSetupNotification()) {
            showSmsPermissionSetupNotification(
                smsPermissionSetupNotificationRequest(contactDisplayName)
            )
        }
        SmsRouteDispatchOutcome(
            sent = false,
            successfulSentMessageInserted = false,
            failure = failure,
        )
    }
}
