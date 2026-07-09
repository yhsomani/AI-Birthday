package com.example.core.automation.sender

import com.example.core.resilience.HealthMonitor
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.dispatch.MessageDispatchFailureHealthReport
import com.example.domain.model.dispatch.MessageDispatchFailureSideEffects

internal fun messageDispatchFailureSideEffects(
    messageId: MessageDraftId,
    preferredChannel: MessageChannel,
    failure: ProviderDispatchFailure,
): MessageDispatchFailureSideEffects {
    return MessageDispatchFailureSideEffects(
        healthReport = MessageDispatchFailureHealthReport(
            context = MESSAGE_DISPATCH_HEALTH_CONTEXT,
            errorMessage = "Failed to send ${messageId.value} via ${preferredChannel.raw}: ${failure.errorType}",
        ),
    )
}

internal fun recordMessageDispatchFailureSideEffects(sideEffects: MessageDispatchFailureSideEffects) {
    HealthMonitor.recordError(
        sideEffects.healthReport.context,
        sideEffects.healthReport.errorMessage,
    )
}

private const val MESSAGE_DISPATCH_HEALTH_CONTEXT = "MessageDispatcher.dispatch"
