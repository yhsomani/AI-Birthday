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
    fun `evaluate downgrades fallback fully auto messages to manual review`() {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.FULLY_AUTO,
            selectedMessage = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            isUsingFallback = true,
        )

        assertEquals(ApprovalMode.ALWAYS_ASK, decision.approvalMode)
        assertTrue(decision.qualityScore < 70)
        assertEquals("ai_fallback,generic_phrase", decision.downgradeReason)
    }

    @Test
    fun `evaluate downgrades generic fully auto messages to manual review`() {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.FULLY_AUTO,
            selectedMessage = "Wishing you all the best and hope you have a wonderful day.",
            isUsingFallback = false,
        )

        assertEquals(ApprovalMode.ALWAYS_ASK, decision.approvalMode)
        assertTrue(decision.qualityScore < 70)
        assertEquals("generic_phrase", decision.downgradeReason)
    }

    @Test
    fun `evaluate still blocks blank fully auto messages from automatic sending`() {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.FULLY_AUTO,
            selectedMessage = "",
            isUsingFallback = false,
        )

        assertEquals(ApprovalMode.ALWAYS_ASK, decision.approvalMode)
        assertTrue(decision.qualityScore < 70)
        assertEquals("blank_message", decision.downgradeReason)
    }

    @Test
    fun `evaluate downgrades weak smart approve messages to manual review`() {
        val decision = AiAutoSendQualityGate.evaluate(
            requestedMode = ApprovalMode.SMART_APPROVE,
            selectedMessage = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            isUsingFallback = true,
        )

        assertEquals(ApprovalMode.ALWAYS_ASK, decision.approvalMode)
        assertTrue(decision.qualityScore < 70)
        assertEquals("ai_fallback,generic_phrase", decision.downgradeReason)
    }

    @Test
    fun `evaluate does not downgrade already manual review modes`() {
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
