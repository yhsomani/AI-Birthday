package com.example.domain.automation

import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.dispatch.DispatchAttemptResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExactSendRecoveryPolicyTest {
    private val nowMs = 2_000_000L
    private val staleGraceMs = 30 * 60 * 1000L

    @Test
    fun `fresh interrupted dispatch waits for recovery grace`() {
        val decision = evaluate(
            result = DispatchAttemptResult.QUEUED,
            requestedAtMs = nowMs - 10_000L,
        )

        assertEquals(ExactSendRecoveryAction.WAIT_FOR_RECOVERY_GRACE, decision.action)
        assertNull(decision.messageStatus)
        assertNull(decision.failure)
    }

    @Test
    fun `stale provider accepted result marks message sent`() {
        listOf(
            DispatchAttemptResult.SENT,
            DispatchAttemptResult.DELIVERED,
            DispatchAttemptResult.PENDING_DELIVERY,
        ).forEach { result ->
            val decision = evaluate(result = result)

            assertEquals(ExactSendRecoveryAction.MARK_SENT, decision.action)
            assertEquals(MessageStatus.SENT, decision.messageStatus)
        }
    }

    @Test
    fun `stale retryable result with retry time reschedules message`() {
        val retryAtMs = nowMs + 60_000L

        val decision = evaluate(
            result = DispatchAttemptResult.FAILED_RETRYABLE,
            nextRetryAtMs = retryAtMs,
        )

        assertEquals(ExactSendRecoveryAction.RESCHEDULE_RETRY, decision.action)
        assertEquals(MessageStatus.APPROVED, decision.messageStatus)
        assertEquals(retryAtMs, decision.scheduledForMs)
    }

    @Test
    fun `stale retryable result without retry time fails for review`() {
        val decision = evaluate(result = DispatchAttemptResult.RETRY_QUEUED)

        assertEquals(ExactSendRecoveryAction.FAIL_FOR_REVIEW, decision.action)
        assertEquals(MessageStatus.FAILED, decision.messageStatus)
        assertEquals(DispatchAttemptResult.FAILED_FINAL, decision.failure?.result)
        assertEquals(MessageDeliveryStatus.FAILED, decision.failure?.deliveryStatus)
        assertEquals(ExactSendRecoveryPolicy.INTERRUPTED_DISPATCH_ERROR_TYPE, decision.failure?.errorType)
    }

    @Test
    fun `stale expired result expires message`() {
        val decision = evaluate(result = DispatchAttemptResult.EXPIRED)

        assertEquals(ExactSendRecoveryAction.MARK_EXPIRED, decision.action)
        assertEquals(MessageStatus.EXPIRED, decision.messageStatus)
    }

    @Test
    fun `stale unresolved result fails for review`() {
        val decision = evaluate(result = DispatchAttemptResult.QUEUED)

        assertEquals(ExactSendRecoveryAction.FAIL_FOR_REVIEW, decision.action)
        assertEquals(MessageStatus.FAILED, decision.messageStatus)
        assertEquals(ExactSendRecoveryPolicy.INTERRUPTED_DISPATCH_MESSAGE, decision.failure?.redactedErrorMessage)
    }

    private fun evaluate(
        result: DispatchAttemptResult,
        requestedAtMs: Long = nowMs - staleGraceMs - 1L,
        nextRetryAtMs: Long? = null,
    ): ExactSendRecoveryDecision {
        return ExactSendRecoveryPolicy.evaluateInterruptedDispatch(
            result = result,
            requestedAtMs = requestedAtMs,
            nowMs = nowMs,
            staleDispatchingGraceMs = staleGraceMs,
            nextRetryAtMs = nextRetryAtMs,
        )
    }
}
