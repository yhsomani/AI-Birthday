package com.example.core.automation.sender

import com.example.core.resilience.DeadLetterEntry
import com.example.core.resilience.DeadLetterQueue
import com.example.core.resilience.HealthMonitor
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.MessageDispatchDeadLetterCommand
import com.example.domain.model.dispatch.MessageDispatchFailureHealthReport
import com.example.domain.model.dispatch.MessageDispatchFailureSideEffects

internal fun messageDispatchFailureSideEffects(
    messageId: MessageDraftId,
    preferredChannel: MessageChannel,
    messageText: String,
    failure: ProviderDispatchFailure,
): MessageDispatchFailureSideEffects {
    return MessageDispatchFailureSideEffects(
        healthReport = MessageDispatchFailureHealthReport(
            context = MESSAGE_DISPATCH_HEALTH_CONTEXT,
            errorMessage = "Failed to send ${messageId.value} via ${preferredChannel.raw}: ${failure.errorType}",
        ),
        deadLetterCommand = if (failure.result == DispatchAttemptResult.FAILED_FINAL) {
            MessageDispatchDeadLetterCommand(
                messageId = messageId,
                payload = messageText,
                errorMessage = failure.redactedErrorMessage,
                errorType = failure.errorType,
                retryCount = 0,
            )
        } else {
            null
        },
    )
}

internal fun recordMessageDispatchFailureSideEffects(sideEffects: MessageDispatchFailureSideEffects) {
    HealthMonitor.recordError(
        sideEffects.healthReport.context,
        sideEffects.healthReport.errorMessage,
    )
    sideEffects.deadLetterCommand?.let { command ->
        DeadLetterQueue.enqueue(command.toDeadLetterEntry())
    }
}

private fun MessageDispatchDeadLetterCommand.toDeadLetterEntry(): DeadLetterEntry {
    return DeadLetterEntry(
        id = messageId.value,
        payload = payload,
        errorMessage = errorMessage,
        errorType = errorType,
        retryCount = retryCount,
    )
}

private const val MESSAGE_DISPATCH_HEALTH_CONTEXT = "MessageDispatcher.dispatch"
