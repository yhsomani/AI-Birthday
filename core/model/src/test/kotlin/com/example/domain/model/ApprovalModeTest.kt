package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalModeTest {
    @Test
    fun `fromRaw normalizes known values and rejects unknown values`() {
        assertEquals(ApprovalMode.FULLY_AUTO, ApprovalMode.fromRaw(" fully_auto "))
        assertEquals(ApprovalMode.SMART_APPROVE, ApprovalMode.fromRaw("SMART_APPROVE"))
        assertEquals(ApprovalMode.UNKNOWN, ApprovalMode.fromRaw("manual_only"))
        assertEquals(ApprovalMode.UNKNOWN, ApprovalMode.fromRaw(null))
    }

    @Test
    fun `orDefault replaces unknown values only`() {
        assertEquals(ApprovalMode.VIP_APPROVE, ApprovalMode.VIP_APPROVE.orDefault())
        assertEquals(ApprovalMode.DEFAULT, ApprovalMode.UNKNOWN.orDefault())
        assertEquals(ApprovalMode.ALWAYS_ASK, ApprovalMode.UNKNOWN.orDefault(ApprovalMode.ALWAYS_ASK))
    }
}
