package com.example.domain.automation

import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttemptResult

enum class ExactSendRecoveryAction {
    WAIT_FOR_RECOVERY_GRACE,
    MARK_SENT,
    RESCHEDULE_RETRY,
    MARK_EXPIRED,
    FAIL_FOR_REVIEW,
}

data class ExactSendRecoveryFailure(
    val result: DispatchAttemptResult = DispatchAttemptResult.FAILED_FINAL,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.FAILED,
    val errorType: String = ExactSendRecoveryPolicy.INTERRUPTED_DISPATCH_ERROR_TYPE,
    val redactedErrorMessage: String = ExactSendRecoveryPolicy.INTERRUPTED_DISPATCH_MESSAGE,
    val deadLetter: Boolean = true,
)

data class ExactSendRecoveryDecision(
    val action: ExactSendRecoveryAction,
    val messageStatus: MessageStatus? = null,
    val scheduledForMs: Long? = null,
    val failure: ExactSendRecoveryFailure? = null,
)

object ExactSendRecoveryPolicy {
    const val INTERRUPTED_DISPATCH_ERROR_TYPE = "INTERRUPTED_DISPATCH"
    const val INTERRUPTED_DISPATCH_MESSAGE =
        "Dispatch was interrupted before RelateAI could confirm provider outcome. Review before retrying to avoid duplicate sends."

    fun evaluateInterruptedDispatch(
        result: DispatchAttemptResult,
        requestedAtMs: Long,
        nowMs: Long,
        staleDispatchingGraceMs: Long,
        nextRetryAtMs: Long?,
    ): ExactSendRecoveryDecision {
        val staleAttemptCutoffMs = nowMs - staleDispatchingGraceMs.coerceAtLeast(0L)
        if (requestedAtMs > staleAttemptCutoffMs) {
            return ExactSendRecoveryDecision(action = ExactSendRecoveryAction.WAIT_FOR_RECOVERY_GRACE)
        }

        return when (result) {
            DispatchAttemptResult.SENT,
            DispatchAttemptResult.DELIVERED,
            DispatchAttemptResult.PENDING_DELIVERY -> ExactSendRecoveryDecision(
                action = ExactSendRecoveryAction.MARK_SENT,
                messageStatus = MessageStatus.SENT,
            )

            DispatchAttemptResult.RETRY_QUEUED,
            DispatchAttemptResult.FAILED_RETRYABLE -> nextRetryAtMs?.let { retryAtMs ->
                ExactSendRecoveryDecision(
                    action = ExactSendRecoveryAction.RESCHEDULE_RETRY,
                    messageStatus = MessageStatus.APPROVED,
                    scheduledForMs = retryAtMs,
                )
            } ?: failedReviewDecision()

            DispatchAttemptResult.EXPIRED -> ExactSendRecoveryDecision(
                action = ExactSendRecoveryAction.MARK_EXPIRED,
                messageStatus = MessageStatus.EXPIRED,
            )

            DispatchAttemptResult.QUEUED,
            DispatchAttemptResult.DEFERRED,
            DispatchAttemptResult.NEEDS_APPROVAL,
            DispatchAttemptResult.BLOCKED,
            DispatchAttemptResult.FAILED_FINAL,
            DispatchAttemptResult.CANCELLED,
            DispatchAttemptResult.UNKNOWN -> failedReviewDecision()
        }
    }

    private fun failedReviewDecision(): ExactSendRecoveryDecision {
        return ExactSendRecoveryDecision(
            action = ExactSendRecoveryAction.FAIL_FOR_REVIEW,
            messageStatus = MessageStatus.FAILED,
            failure = ExactSendRecoveryFailure(),
        )
    }
}
