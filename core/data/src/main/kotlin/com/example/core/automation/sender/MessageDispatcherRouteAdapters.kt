package com.example.core.automation.sender

import android.content.Context
import com.example.core.prefs.SecurePrefs
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.MessageDraftId

internal data class WhatsAppDispatchRouteResult(
    val sent: Boolean,
    val failure: ProviderDispatchFailure?,
)

internal data class SmsDispatchRouteResult(
    val sent: Boolean,
    val failure: ProviderDispatchFailure?,
    val cause: Throwable?,
) {
    val isPermissionDenied: Boolean
        get() = failure?.errorType == DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED
}

internal data class EmailDispatchRouteResult(
    val sent: Boolean,
    val failure: ProviderDispatchFailure?,
    val cause: Throwable?,
)

internal suspend fun Context.dispatchWhatsAppRoute(
    phoneNumber: String,
    messageText: String,
    eventRef: String,
): WhatsAppDispatchRouteResult {
    val sent = WhatsAppSender(this).send(phoneNumber, messageText, eventRef)
    return WhatsAppDispatchRouteResult(
        sent = sent,
        failure = if (sent) null else DispatchProviderRetryPolicy.whatsAppAutomationUnavailable(),
    )
}

internal suspend fun Context.dispatchWhatsAppRouteWithFailureLog(
    messageId: MessageDraftId,
    phoneNumber: String,
    messageText: String,
    eventRef: String,
): WhatsAppDispatchRouteResult {
    val result = dispatchWhatsAppRoute(
        phoneNumber = phoneNumber,
        messageText = messageText,
        eventRef = eventRef,
    )
    result.failure?.let { failure ->
        recordMessageDispatchRouteFailureLog(
            messageDispatchRouteFailureLog(
                messageId = messageId,
                channel = MessageChannel.WHATSAPP,
                failure = failure,
            )
        )
    }
    return result
}

internal fun Context.dispatchSmsRoute(
    phoneNumber: String,
    messageText: String,
    sentMessageId: String,
): SmsDispatchRouteResult {
    return try {
        SmsSender(this).send(phoneNumber, messageText, sentMessageId)
        SmsDispatchRouteResult(
            sent = true,
            failure = null,
            cause = null,
        )
    } catch (e: SecurityException) {
        SmsDispatchRouteResult(
            sent = false,
            failure = DispatchProviderRetryPolicy.smsPermissionDenied(),
            cause = e,
        )
    } catch (e: Exception) {
        SmsDispatchRouteResult(
            sent = false,
            failure = DispatchProviderRetryPolicy.smsProviderException(e),
            cause = e,
        )
    }
}

internal suspend fun SecurePrefs.dispatchEmailRouteWithFailureLog(
    messageId: MessageDraftId,
    toEmail: String,
    contactName: String,
    messageText: String,
    eventType: String,
    eventLabel: String?,
): EmailDispatchRouteResult {
    val result = dispatchEmailRoute(
        toEmail = toEmail,
        contactName = contactName,
        messageText = messageText,
        eventType = eventType,
        eventLabel = eventLabel,
    )
    result.failure?.let { failure ->
        recordMessageDispatchRouteFailureLog(
            messageDispatchRouteFailureLog(
                messageId = messageId,
                channel = MessageChannel.EMAIL,
                failure = failure,
                cause = result.cause,
            )
        )
    }
    return result
}

internal suspend fun SecurePrefs.dispatchEmailRoute(
    toEmail: String,
    contactName: String,
    messageText: String,
    eventType: String,
    eventLabel: String?,
): EmailDispatchRouteResult {
    return try {
        EmailSender(this).send(
            toEmail = toEmail,
            contactName = contactName,
            messageText = messageText,
            eventType = eventType,
            eventLabel = eventLabel,
            subjectOverride = null,
        )
        EmailDispatchRouteResult(
            sent = true,
            failure = null,
            cause = null,
        )
    } catch (e: Exception) {
        EmailDispatchRouteResult(
            sent = false,
            failure = DispatchProviderRetryPolicy.emailProviderException(e),
            cause = e,
        )
    }
}
