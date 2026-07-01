package com.example.core.prefs

import com.example.domain.model.ApprovalMode
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalAutomationModePrefsMapperTest {
    @Test
    fun `toSupportedApprovalMode maps supported raw values`() {
        assertEquals(
            ApprovalMode.VIP_APPROVE,
            GlobalAutomationModePrefsMapper.toSupportedApprovalMode("VIP_APPROVE")
        )
    }

    @Test
    fun `toSupportedApprovalMode falls back to always ask for unsupported raw values`() {
        assertEquals(
            ApprovalMode.ALWAYS_ASK,
            GlobalAutomationModePrefsMapper.toSupportedApprovalMode("MANUAL")
        )
    }

    @Test
    fun `toSupportedRaw stores supported mode raw value`() {
        assertEquals(
            ApprovalMode.ALWAYS_ASK.raw,
            GlobalAutomationModePrefsMapper.toSupportedRaw(ApprovalMode.ALWAYS_ASK)
        )
    }

    @Test
    fun `toSupportedRaw falls back to always ask for unsupported modes`() {
        assertEquals(
            ApprovalMode.ALWAYS_ASK.raw,
            GlobalAutomationModePrefsMapper.toSupportedRaw(ApprovalMode.UNKNOWN)
        )
    }
}
