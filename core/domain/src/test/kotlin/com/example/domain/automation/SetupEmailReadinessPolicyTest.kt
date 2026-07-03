package com.example.domain.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupEmailReadinessPolicyTest {

    @Test
    fun `isSenderReady requires valid sender address and password`() {
        assertTrue(
            SetupEmailReadinessPolicy.isSenderReady(
                senderEmail = " sender@example.com ",
                senderEmailPassword = " app-password ",
            ),
        )
        assertFalse(
            SetupEmailReadinessPolicy.isSenderReady(
                senderEmail = "not-an-email",
                senderEmailPassword = "app-password",
            ),
        )
        assertFalse(
            SetupEmailReadinessPolicy.isSenderReady(
                senderEmail = "sender@example.com",
                senderEmailPassword = " ",
            ),
        )
    }

    @Test
    fun `evaluate blocks invalid saved sender before contact usage`() {
        val readiness = SetupEmailReadinessPolicy.evaluate(
            senderEmail = "not-an-email",
            senderEmailPassword = "app-password",
            emailSelfTestVerified = false,
            emailPreferredContactCount = 0,
        )

        assertEquals(SetupEmailReadinessReason.INVALID_SENDER, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
        assertEquals(SetupReadinessGroup.REQUIRED, readiness.group)
    }

    @Test
    fun `evaluate passes verified configured sender`() {
        val readiness = SetupEmailReadinessPolicy.evaluate(
            senderEmail = "sender@example.com",
            senderEmailPassword = "app-password",
            emailSelfTestVerified = true,
            emailPreferredContactCount = 2,
        )

        assertEquals(SetupEmailReadinessReason.VERIFIED, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }

    @Test
    fun `evaluate warns when configured sender has not been verified`() {
        val readiness = SetupEmailReadinessPolicy.evaluate(
            senderEmail = "sender@example.com",
            senderEmailPassword = "app-password",
            emailSelfTestVerified = false,
            emailPreferredContactCount = 0,
        )

        assertEquals(SetupEmailReadinessReason.UNVERIFIED, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
    }

    @Test
    fun `evaluate requires setup when contacts prefer email and sender is missing`() {
        val readiness = SetupEmailReadinessPolicy.evaluate(
            senderEmail = "",
            senderEmailPassword = "",
            emailSelfTestVerified = false,
            emailPreferredContactCount = 3,
        )

        assertEquals(SetupEmailReadinessReason.MISSING_FOR_CONTACTS, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
        assertEquals(3, readiness.emailPreferredContactCount)
    }

    @Test
    fun `evaluate treats unused email as optional warning`() {
        val readiness = SetupEmailReadinessPolicy.evaluate(
            senderEmail = "",
            senderEmailPassword = "",
            emailSelfTestVerified = false,
            emailPreferredContactCount = 0,
        )

        assertEquals(SetupEmailReadinessReason.OPTIONAL, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
    }
}
