package com.example.domain.automation

import com.example.domain.model.MessageDeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsDeliveryStatusRecoveryPolicyTest {
    @Test
    fun `stale pending delivery decision marks records older than recovery window unknown`() {
        val decision = SmsDeliveryStatusRecoveryPolicy.stalePendingDeliveryDecision(
            nowMs = 2_000L,
            stalePendingDeliveryMs = 500L,
        )

        assertEquals(1_500L, decision.cutoffMs)
        assertEquals(MessageDeliveryStatus.UNKNOWN, decision.recoveredStatus)
    }

    @Test
    fun `negative recovery window clamps cutoff to now`() {
        val decision = SmsDeliveryStatusRecoveryPolicy.stalePendingDeliveryDecision(
            nowMs = 2_000L,
            stalePendingDeliveryMs = -1L,
        )

        assertEquals(2_000L, decision.cutoffMs)
        assertEquals(MessageDeliveryStatus.UNKNOWN, decision.recoveredStatus)
    }
}
