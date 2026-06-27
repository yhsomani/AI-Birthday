package com.example.core.automation.sender

internal data class MessageDispatchProviderFailureSelection(
    val selectedFailure: ProviderDispatchFailure?,
)

internal fun messageDispatchProviderFailureSelection(): MessageDispatchProviderFailureSelection {
    return MessageDispatchProviderFailureSelection(selectedFailure = null)
}

internal fun MessageDispatchProviderFailureSelection.select(
    candidate: ProviderDispatchFailure,
): MessageDispatchProviderFailureSelection {
    return copy(
        selectedFailure = DispatchProviderRetryPolicy.select(selectedFailure, candidate),
    )
}

internal fun MessageDispatchProviderFailureSelection.failureOrDispatchFailure(): ProviderDispatchFailure {
    return selectedFailure ?: DispatchProviderRetryPolicy.dispatchFailure()
}

internal fun smsDispatchExceptionFailure(throwable: Throwable): ProviderDispatchFailure {
    return DispatchProviderRetryPolicy.smsProviderException(throwable)
}

internal fun ProviderDispatchFailure.requiresSmsPermissionSetupNotification(): Boolean {
    return errorType == DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED
}
