package com.example.core.automation.sender

internal data class MessageDispatchRouteOutcome(
    val sent: Boolean,
    val successfulSentMessageInserted: Boolean,
    val failure: ProviderDispatchFailure?,
)

internal data class MessageDispatchRouteLoopState(
    val success: Boolean,
    val successfulSentMessageInserted: Boolean,
    val providerFailureSelection: MessageDispatchProviderFailureSelection,
)

internal fun messageDispatchRouteLoopState(): MessageDispatchRouteLoopState {
    return MessageDispatchRouteLoopState(
        success = false,
        successfulSentMessageInserted = false,
        providerFailureSelection = messageDispatchProviderFailureSelection(),
    )
}

internal fun WhatsAppDispatchRouteResult.toMessageDispatchRouteOutcome(): MessageDispatchRouteOutcome {
    return MessageDispatchRouteOutcome(
        sent = sent,
        successfulSentMessageInserted = false,
        failure = failure,
    )
}

internal fun SmsRouteDispatchOutcome.toMessageDispatchRouteOutcome(): MessageDispatchRouteOutcome {
    return MessageDispatchRouteOutcome(
        sent = sent,
        successfulSentMessageInserted = successfulSentMessageInserted,
        failure = failure,
    )
}

internal fun EmailDispatchRouteResult.toMessageDispatchRouteOutcome(): MessageDispatchRouteOutcome {
    return MessageDispatchRouteOutcome(
        sent = sent,
        successfulSentMessageInserted = false,
        failure = failure,
    )
}

internal fun MessageDispatchRouteLoopState.applyRouteOutcome(
    outcome: MessageDispatchRouteOutcome,
): MessageDispatchRouteLoopState {
    return copy(
        success = outcome.sent,
        successfulSentMessageInserted = successfulSentMessageInserted ||
            outcome.successfulSentMessageInserted,
        providerFailureSelection = outcome.failure?.let { failure ->
            providerFailureSelection.select(failure)
        } ?: providerFailureSelection,
    )
}
