package com.example.domain.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAddressSyntaxPolicyTest {
    @Test
    fun `isUsableAddress accepts trimmed normal email addresses`() {
        assertTrue(EmailAddressSyntaxPolicy.isUsableAddress("  asha@example.com  "))
    }

    @Test
    fun `isUsableAddress rejects blank malformed and whitespace addresses`() {
        assertFalse(EmailAddressSyntaxPolicy.isUsableAddress(""))
        assertFalse(EmailAddressSyntaxPolicy.isUsableAddress("not an email"))
        assertFalse(EmailAddressSyntaxPolicy.isUsableAddress("asha @example.com"))
        assertFalse(EmailAddressSyntaxPolicy.isUsableAddress("asha@example"))
    }

    @Test
    fun `isConfiguredSender requires valid sender email and password`() {
        assertTrue(EmailAddressSyntaxPolicy.isConfiguredSender("sender@example.com", "app-password"))
        assertFalse(EmailAddressSyntaxPolicy.isConfiguredSender("sender@example.com", ""))
        assertFalse(EmailAddressSyntaxPolicy.isConfiguredSender("sender", "app-password"))
    }
}
