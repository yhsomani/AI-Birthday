package com.example.domain.dispatch

import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.dispatch.DispatchAttemptResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchExceptionFailurePolicyTest {
    @Test
    fun `exception maps to final failed dead-letter decision`() {
        val decision = DispatchExceptionFailurePolicy.evaluate(
            IllegalStateException("provider details stay redacted"),
        )

        assertEquals(DispatchAttemptResult.FAILED_FINAL, decision.result)
        assertEquals(MessageDeliveryStatus.FAILED, decision.deliveryStatus)
        assertEquals("IllegalStateException", decision.errorType)
        assertEquals(null, decision.errorCode)
        assertEquals(DispatchExceptionFailurePolicy.REDACTED_MESSAGE, decision.redactedErrorMessage)
        assertTrue(decision.deadLetter)
    }

    @Test
    fun `anonymous exception uses generic dispatch error type`() {
        val exception = object : RuntimeException("anonymous failure") {}

        val decision = DispatchExceptionFailurePolicy.evaluate(exception)

        assertEquals(DispatchExceptionFailurePolicy.DEFAULT_ERROR_TYPE, decision.errorType)
    }
}
