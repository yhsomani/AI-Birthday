package com.example.domain.automation

import com.example.domain.model.ApprovalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalModeResolverTest {

    @Test
    fun `resolve uses explicit contact override before global mode`() {
        val result = ApprovalModeResolver.resolve(
            relationship = "FRIEND",
            contactOverride = "FULLY_AUTO",
            globalMode = "ALWAYS_ASK",
        )

        assertEquals(ApprovalMode.FULLY_AUTO, result)
    }

    @Test
    fun `resolve falls back from invalid legacy modes to smart approve`() {
        val result = ApprovalModeResolver.resolve(
            relationship = "FRIEND",
            contactOverride = "MANUAL",
            globalMode = "MANUAL",
        )

        assertEquals(ApprovalMode.SMART_APPROVE, result)
    }

    @Test
    fun `resolve protects family contacts from global fully auto`() {
        val result = ApprovalModeResolver.resolve(
            relationship = "FAMILY",
            contactOverride = "DEFAULT",
            globalMode = "FULLY_AUTO",
        )

        assertEquals(ApprovalMode.VIP_APPROVE, result)
    }

    @Test
    fun `resolve keeps close contacts in reviewable smart approval unless global always asks`() {
        val smartResult = ApprovalModeResolver.resolve(
            relationship = "CLOSE_FRIEND",
            contactOverride = "DEFAULT",
            globalMode = "FULLY_AUTO",
        )
        val alwaysAskResult = ApprovalModeResolver.resolve(
            relationship = "RELATIVE",
            contactOverride = "DEFAULT",
            globalMode = "ALWAYS_ASK",
        )

        assertEquals(ApprovalMode.SMART_APPROVE, smartResult)
        assertEquals(ApprovalMode.ALWAYS_ASK, alwaysAskResult)
    }

    @Test
    fun `resolve forces manual approval when contact skipped automatic wishes`() {
        val result = ApprovalModeResolver.resolve(
            relationship = "FRIEND",
            contactOverride = "FULLY_AUTO",
            globalMode = "FULLY_AUTO",
            skipAutoWish = true,
        )

        assertEquals(ApprovalMode.ALWAYS_ASK, result)
    }

    @Test
    fun `automatic dispatch is limited to fully auto and smart approve`() {
        assertTrue(ApprovalModeResolver.schedulesAutomaticDispatch(ApprovalMode.FULLY_AUTO))
        assertTrue(ApprovalModeResolver.schedulesAutomaticDispatch(ApprovalMode.SMART_APPROVE))
        assertFalse(ApprovalModeResolver.schedulesAutomaticDispatch(ApprovalMode.VIP_APPROVE))
        assertFalse(ApprovalModeResolver.schedulesAutomaticDispatch(ApprovalMode.ALWAYS_ASK))
    }
}
