package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DispatchActivityDecisionTest {
    @Test
    fun `fromRaw normalizes dispatch activity decisions`() {
        assertEquals(DispatchActivityDecision.DEFERRED, DispatchActivityDecision.fromRaw(" deferred "))
        assertEquals(DispatchActivityDecision.NEEDS_APPROVAL, DispatchActivityDecision.fromRaw("needs_approval"))
        assertEquals(DispatchActivityDecision.EXPIRED, DispatchActivityDecision.fromRaw("EXPIRED"))
        assertEquals(DispatchActivityDecision.BLOCKED, DispatchActivityDecision.fromRaw(" blocked "))
        assertEquals(DispatchActivityDecision.SENT, DispatchActivityDecision.fromRaw("sent"))
        assertEquals(DispatchActivityDecision.UNKNOWN, DispatchActivityDecision.fromRaw("CONTACT_NOT_FOUND"))
    }
}
