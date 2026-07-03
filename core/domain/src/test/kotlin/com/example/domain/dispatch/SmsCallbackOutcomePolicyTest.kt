package com.example.domain.dispatch

import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.dispatch.DispatchAttemptResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCallbackOutcomePolicyTest {
    @Test
    fun `sent success maps to sent dispatch outcome`() {
        val outcome = SmsCallbackOutcomePolicy.evaluate(
            kind = SmsCallbackKind.SENT,
            succeeded = true,
        )

        assertEquals(MessageDeliveryStatus.SENT, outcome.deliveryStatus)
        assertEquals(DispatchAttemptResult.SENT, outcome.dispatchAttemptResult)
        assertFalse(outcome.shouldMarkPendingFailed)
        assertFalse(outcome.deadLetter)
        assertNull(outcome.failureType)
        assertNull(outcome.failureMessage)
    }

    @Test
    fun `delivered success maps to delivered dispatch outcome`() {
        val outcome = SmsCallbackOutcomePolicy.evaluate(
            kind = SmsCallbackKind.DELIVERED,
            succeeded = true,
        )

        assertEquals(MessageDeliveryStatus.DELIVERED, outcome.deliveryStatus)
        assertEquals(DispatchAttemptResult.DELIVERED, outcome.dispatchAttemptResult)
        assertFalse(outcome.shouldMarkPendingFailed)
        assertFalse(outcome.deadLetter)
    }

    @Test
    fun `sent failure maps to final failure and sent callback metadata`() {
        val outcome = SmsCallbackOutcomePolicy.evaluate(
            kind = SmsCallbackKind.SENT,
            succeeded = false,
        )

        assertEquals(MessageDeliveryStatus.FAILED, outcome.deliveryStatus)
        assertEquals(DispatchAttemptResult.FAILED_FINAL, outcome.dispatchAttemptResult)
        assertTrue(outcome.shouldMarkPendingFailed)
        assertTrue(outcome.deadLetter)
        assertEquals("SMS_SENT_CALLBACK_FAILED", outcome.failureType)
        assertEquals(
            "Android SMS sent callback reported failure after send handoff.",
            outcome.failureMessage,
        )
    }

    @Test
    fun `delivered failure maps to final failure and delivery callback metadata`() {
        val outcome = SmsCallbackOutcomePolicy.evaluate(
            kind = SmsCallbackKind.DELIVERED,
            succeeded = false,
        )

        assertEquals(MessageDeliveryStatus.FAILED, outcome.deliveryStatus)
        assertEquals(DispatchAttemptResult.FAILED_FINAL, outcome.dispatchAttemptResult)
        assertTrue(outcome.shouldMarkPendingFailed)
        assertTrue(outcome.deadLetter)
        assertEquals("SMS_DELIVERY_CALLBACK_FAILED", outcome.failureType)
        assertEquals(
            "Android SMS delivery callback reported failure after send handoff.",
            outcome.failureMessage,
        )
    }
}
