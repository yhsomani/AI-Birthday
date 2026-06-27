package com.example.core.automation.sender

import com.example.core.resilience.StructuredLogger
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId

internal sealed interface MessageDispatchLifecycleLog {
    data class DispatchStarted(
        val messageId: MessageDraftId,
        val preferredChannel: MessageChannel,
        val contactId: ContactId,
    ) : MessageDispatchLifecycleLog

    data class NoDeliveryRoute(
        val messageId: MessageDraftId,
        val preferredChannel: MessageChannel,
        val blockedChannels: Set<MessageChannel>,
        val hasPhone: Boolean,
        val hasEmail: Boolean,
    ) : MessageDispatchLifecycleLog

    data class DispatchSucceeded(
        val messageId: MessageDraftId,
        val channel: MessageChannel,
    ) : MessageDispatchLifecycleLog

    data class DispatchFailed(
        val messageId: MessageDraftId,
        val preferredChannel: MessageChannel,
        val failure: ProviderDispatchFailure,
    ) : MessageDispatchLifecycleLog

    data class DispatchAttemptOutcomeUpdateFailed(
        val dispatchAttemptId: String?,
        val cause: Throwable,
    ) : MessageDispatchLifecycleLog
}

internal data class MessageDispatchRouteFailureLog(
    val messageId: MessageDraftId,
    val channel: MessageChannel,
    val reason: MessageDispatchRouteFailureReason,
    val cause: Throwable?,
)

internal enum class MessageDispatchRouteFailureReason {
    ROUTE_UNAVAILABLE,
    SMS_PERMISSION_DENIED,
    SMS_PROVIDER_FAILURE,
    EMAIL_PROVIDER_FAILURE,
}

internal fun messageDispatchStartedLog(
    messageId: MessageDraftId,
    preferredChannel: MessageChannel,
    contactId: ContactId,
): MessageDispatchLifecycleLog.DispatchStarted {
    return MessageDispatchLifecycleLog.DispatchStarted(
        messageId = messageId,
        preferredChannel = preferredChannel,
        contactId = contactId,
    )
}

internal fun messageDispatchNoRouteLog(
    messageId: MessageDraftId,
    preferredChannel: MessageChannel,
    blockedChannels: Set<MessageChannel>,
    hasPhone: Boolean,
    hasEmail: Boolean,
): MessageDispatchLifecycleLog.NoDeliveryRoute {
    return MessageDispatchLifecycleLog.NoDeliveryRoute(
        messageId = messageId,
        preferredChannel = preferredChannel,
        blockedChannels = blockedChannels,
        hasPhone = hasPhone,
        hasEmail = hasEmail,
    )
}

internal fun messageDispatchSucceededLog(
    messageId: MessageDraftId,
    channel: MessageChannel,
): MessageDispatchLifecycleLog.DispatchSucceeded {
    return MessageDispatchLifecycleLog.DispatchSucceeded(
        messageId = messageId,
        channel = channel,
    )
}

internal fun messageDispatchFailedLog(
    messageId: MessageDraftId,
    preferredChannel: MessageChannel,
    failure: ProviderDispatchFailure,
): MessageDispatchLifecycleLog.DispatchFailed {
    return MessageDispatchLifecycleLog.DispatchFailed(
        messageId = messageId,
        preferredChannel = preferredChannel,
        failure = failure,
    )
}

internal fun messageDispatchAttemptOutcomeUpdateFailedLog(
    dispatchAttemptId: String?,
    cause: Throwable,
): MessageDispatchLifecycleLog.DispatchAttemptOutcomeUpdateFailed {
    return MessageDispatchLifecycleLog.DispatchAttemptOutcomeUpdateFailed(
        dispatchAttemptId = dispatchAttemptId,
        cause = cause,
    )
}

internal fun recordMessageDispatchLifecycleLog(log: MessageDispatchLifecycleLog) {
    when (log) {
        is MessageDispatchLifecycleLog.DispatchStarted -> {
            StructuredLogger.i(
                MESSAGE_DISPATCHER_LOG_TAG,
                "Dispatching message",
                mapOf(
                    "messageId" to log.messageId.value,
                    "channel" to log.preferredChannel.raw,
                    "contactId" to log.contactId.value,
                ),
            )
        }
        is MessageDispatchLifecycleLog.NoDeliveryRoute -> {
            StructuredLogger.w(
                MESSAGE_DISPATCHER_LOG_TAG,
                "No available delivery route for automatic message dispatch",
                extras = mapOf(
                    "messageId" to log.messageId.value,
                    "preferredChannel" to log.preferredChannel.raw,
                    "blockedChannels" to log.blockedChannels.joinToString(",") { it.raw },
                    "hasPhone" to log.hasPhone.toString(),
                    "hasEmail" to log.hasEmail.toString(),
                ),
            )
        }
        is MessageDispatchLifecycleLog.DispatchSucceeded -> {
            StructuredLogger.i(
                MESSAGE_DISPATCHER_LOG_TAG,
                "Message dispatched successfully",
                mapOf(
                    "messageId" to log.messageId.value,
                    "channel" to log.channel.raw,
                ),
            )
        }
        is MessageDispatchLifecycleLog.DispatchFailed -> {
            StructuredLogger.w(
                MESSAGE_DISPATCHER_LOG_TAG,
                "Failed to dispatch message ${log.messageId.value} via " +
                    "${log.preferredChannel.raw}: ${log.failure.errorType}",
            )
        }
        is MessageDispatchLifecycleLog.DispatchAttemptOutcomeUpdateFailed -> {
            StructuredLogger.e(
                MESSAGE_DISPATCHER_LOG_TAG,
                "Failed to update dispatch attempt ${log.dispatchAttemptId}",
                log.cause,
            )
        }
    }
}

internal fun messageDispatchRouteFailureLog(
    messageId: MessageDraftId,
    channel: MessageChannel,
    failure: ProviderDispatchFailure,
    cause: Throwable? = null,
): MessageDispatchRouteFailureLog {
    return MessageDispatchRouteFailureLog(
        messageId = messageId,
        channel = channel,
        reason = routeFailureReason(channel, failure),
        cause = cause,
    )
}

internal fun recordMessageDispatchRouteFailureLog(log: MessageDispatchRouteFailureLog) {
    when (log.reason) {
        MessageDispatchRouteFailureReason.ROUTE_UNAVAILABLE -> {
            StructuredLogger.w(
                MESSAGE_DISPATCHER_LOG_TAG,
                "${log.channel.routeLabel()} route failed for ${log.messageId.value}; trying next automatic route",
            )
        }
        MessageDispatchRouteFailureReason.SMS_PERMISSION_DENIED -> {
            StructuredLogger.e(
                MESSAGE_DISPATCHER_LOG_TAG,
                "SMS permission not granted for message ${log.messageId.value}",
                log.cause,
            )
        }
        MessageDispatchRouteFailureReason.SMS_PROVIDER_FAILURE -> {
            StructuredLogger.e(
                MESSAGE_DISPATCHER_LOG_TAG,
                "SMS send failed for message ${log.messageId.value}",
                log.cause,
            )
        }
        MessageDispatchRouteFailureReason.EMAIL_PROVIDER_FAILURE -> {
            StructuredLogger.e(
                MESSAGE_DISPATCHER_LOG_TAG,
                "Email route failed for ${log.messageId.value}; trying next automatic route",
                log.cause,
            )
        }
    }
}

private fun routeFailureReason(
    channel: MessageChannel,
    failure: ProviderDispatchFailure,
): MessageDispatchRouteFailureReason {
    return when {
        channel == MessageChannel.SMS &&
            failure.errorType == DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED ->
            MessageDispatchRouteFailureReason.SMS_PERMISSION_DENIED
        channel == MessageChannel.SMS -> MessageDispatchRouteFailureReason.SMS_PROVIDER_FAILURE
        channel == MessageChannel.EMAIL -> MessageDispatchRouteFailureReason.EMAIL_PROVIDER_FAILURE
        else -> MessageDispatchRouteFailureReason.ROUTE_UNAVAILABLE
    }
}

private fun MessageChannel.routeLabel(): String {
    return when (this) {
        MessageChannel.WHATSAPP -> "WhatsApp"
        MessageChannel.EMAIL -> "Email"
        MessageChannel.SMS -> "SMS"
        MessageChannel.UNKNOWN -> "Unknown"
    }
}

private const val MESSAGE_DISPATCHER_LOG_TAG = "MessageDispatcher"
