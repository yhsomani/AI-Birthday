package com.example.domain.automation

import com.example.domain.model.ApprovalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAutoSendQualityGateTest {

    @Test
    fun `evaluate keeps high quality fully auto messages fully automatic`() {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.FULLY_AUTO,
            selectedMessage = "Happy birthday Riya, still remember our Jaipur food walk. Hope this year brings more trips and good coffee.",
            isUsingFallback = false,
        )

        assertEquals(ApprovalMode.FULLY_AUTO, decision.approvalMode)
        assertEquals(100, decision.qualityScore)
        assertNull(decision.downgradeReason)
    }

    @Test
    fun `evaluate downgrades fallback fully auto messages to smart approve`() {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.FULLY_AUTO,
            selectedMessage = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            isUsingFallback = true,
        )

        assertEquals(ApprovalMode.SMART_APPROVE, decision.approvalMode)
        assertTrue(decision.qualityScore < 70)
        assertEquals("ai_fallback,generic_phrase", decision.downgradeReason)
    }

    @Test
    fun `evaluate does not downgrade already reviewable modes`() {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.ALWAYS_ASK,
            selectedMessage = "",
            isUsingFallback = true,
        )

        assertEquals(ApprovalMode.ALWAYS_ASK, decision.approvalMode)
        assertTrue(decision.qualityScore < 70)
        assertNull(decision.downgradeReason)
    }
}
