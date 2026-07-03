package com.example.domain.dispatch

import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.dispatch.DispatchAttemptResult

enum class SmsCallbackKind {
    SENT,
    DELIVERED,
}

data class SmsCallbackOutcome(
    val deliveryStatus: MessageDeliveryStatus,
    val dispatchAttemptResult: DispatchAttemptResult,
    val shouldMarkPendingFailed: Boolean = false,
    val failureType: String? = null,
    val failureMessage: String? = null,
    val deadLetter: Boolean = false,
)

object SmsCallbackOutcomePolicy {
    fun evaluate(
        kind: SmsCallbackKind,
        succeeded: Boolean,
    ): SmsCallbackOutcome {
        val deliveryStatus = when {
            succeeded && kind == SmsCallbackKind.SENT -> MessageDeliveryStatus.SENT
            succeeded && kind == SmsCallbackKind.DELIVERED -> MessageDeliveryStatus.DELIVERED
            else -> MessageDeliveryStatus.FAILED
        }
        val failed = deliveryStatus == MessageDeliveryStatus.FAILED
        return SmsCallbackOutcome(
            deliveryStatus = deliveryStatus,
            dispatchAttemptResult = deliveryStatus.toDispatchAttemptResult(),
            shouldMarkPendingFailed = failed,
            failureType = if (failed) kind.failureType else null,
            failureMessage = if (failed) kind.failureMessage else null,
            deadLetter = failed,
        )
    }

    private fun MessageDeliveryStatus.toDispatchAttemptResult(): DispatchAttemptResult {
        return when (this) {
            MessageDeliveryStatus.DELIVERED -> DispatchAttemptResult.DELIVERED
            MessageDeliveryStatus.SENT -> DispatchAttemptResult.SENT
            MessageDeliveryStatus.FAILED -> DispatchAttemptResult.FAILED_FINAL
            MessageDeliveryStatus.PENDING_DELIVERY -> DispatchAttemptResult.PENDING_DELIVERY
            MessageDeliveryStatus.UNKNOWN -> DispatchAttemptResult.UNKNOWN
        }
    }

    private val SmsCallbackKind.failureType: String
        get() = when (this) {
            SmsCallbackKind.SENT -> "SMS_SENT_CALLBACK_FAILED"
            SmsCallbackKind.DELIVERED -> "SMS_DELIVERY_CALLBACK_FAILED"
        }

    private val SmsCallbackKind.failureMessage: String
        get() = when (this) {
            SmsCallbackKind.SENT -> "Android SMS sent callback reported failure after send handoff."
            SmsCallbackKind.DELIVERED -> "Android SMS delivery callback reported failure after send handoff."
        }
}
