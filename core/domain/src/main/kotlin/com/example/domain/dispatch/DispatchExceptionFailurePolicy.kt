package com.example.domain.dispatch

import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.dispatch.DispatchAttemptResult

data class DispatchExceptionFailureDecision(
    val result: DispatchAttemptResult = DispatchAttemptResult.FAILED_FINAL,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.FAILED,
    val errorType: String,
    val errorCode: String? = null,
    val redactedErrorMessage: String = DispatchExceptionFailurePolicy.REDACTED_MESSAGE,
    val deadLetter: Boolean = true,
)

object DispatchExceptionFailurePolicy {
    const val DEFAULT_ERROR_TYPE = "DISPATCH_EXCEPTION"
    const val REDACTED_MESSAGE = "Dispatcher failed before completing send."

    fun evaluate(exception: Throwable): DispatchExceptionFailureDecision {
        return DispatchExceptionFailureDecision(
            errorType = exception::class.simpleName ?: DEFAULT_ERROR_TYPE,
        )
    }
}
