package com.example.domain.model.dispatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchAttemptTest {
    @Test
    fun `fromRaw normalizes eligibility records`() {
        assertEquals(DispatchEligibilityRecord.SEND_NOW, DispatchEligibilityRecord.fromRaw(" send_now "))
        assertEquals(DispatchEligibilityRecord.NEEDS_APPROVAL, DispatchEligibilityRecord.fromRaw("needs_approval"))
        assertEquals(DispatchEligibilityRecord.UNKNOWN, DispatchEligibilityRecord.fromRaw("WAITING"))
    }

    @Test
    fun `fromRaw normalizes dispatch results`() {
        assertEquals(DispatchAttemptResult.PENDING_DELIVERY, DispatchAttemptResult.fromRaw(" pending_delivery "))
        assertEquals(DispatchAttemptResult.FAILED_RETRYABLE, DispatchAttemptResult.fromRaw("failed_retryable"))
        assertEquals(DispatchAttemptResult.RETRY_QUEUED, DispatchAttemptResult.fromRaw("retry_queued"))
        assertEquals(DispatchAttemptResult.UNKNOWN, DispatchAttemptResult.fromRaw("BOUNCED"))
    }

    @Test
    fun `terminal results identify completed attempts`() {
        assertTrue(DispatchAttemptResult.SENT.isTerminal)
        assertTrue(DispatchAttemptResult.FAILED_FINAL.isTerminal)
        assertTrue(DispatchAttemptResult.CANCELLED.isTerminal)
        assertFalse(DispatchAttemptResult.QUEUED.isTerminal)
        assertFalse(DispatchAttemptResult.RETRY_QUEUED.isTerminal)
        assertFalse(DispatchAttemptResult.FAILED_RETRYABLE.isTerminal)
    }

    @Test
    fun `fromRaw normalizes dispatch attempt creators`() {
        assertEquals(DispatchAttemptCreator.USER, DispatchAttemptCreator.fromRaw(" user "))
        assertEquals(DispatchAttemptCreator.WORKER, DispatchAttemptCreator.fromRaw("worker"))
        assertEquals(DispatchAttemptCreator.UNKNOWN, DispatchAttemptCreator.fromRaw("scheduler"))
    }
}
