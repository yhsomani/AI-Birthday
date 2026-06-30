package com.example.core.automation.sender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmailSenderTest {

    @Test
    fun validatedEmailAddress_trimsAndValidatesAddress() {
        val address = validatedEmailAddress("  sender@example.com  ", EmailAddressField.SENDER)

        assertEquals("sender@example.com", address.address)
    }

    @Test
    fun validatedEmailAddress_rejectsBlankAddressWithField() {
        val exception = assertThrows(EmailAddressValidationException::class.java) {
            validatedEmailAddress("   ", EmailAddressField.RECIPIENT)
        }

        assertEquals(EmailAddressField.RECIPIENT, exception.field)
    }

    @Test
    fun validatedEmailAddress_rejectsMalformedAddressWithField() {
        val exception = assertThrows(EmailAddressValidationException::class.java) {
            validatedEmailAddress("not an email", EmailAddressField.SENDER)
        }

        assertEquals(EmailAddressField.SENDER, exception.field)
    }

    @Test
    fun gmailSmtpProperties_configuresGmailTransportAndTimeouts() {
        val props = gmailSmtpProperties(timeoutMs = 12_345)

        assertEquals("true", props.getProperty("mail.smtp.auth"))
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"))
        assertEquals("smtp.gmail.com", props.getProperty("mail.smtp.host"))
        assertEquals("587", props.getProperty("mail.smtp.port"))
        assertEquals("12345", props.getProperty("mail.smtp.connectiontimeout"))
        assertEquals("12345", props.getProperty("mail.smtp.timeout"))
        assertEquals("12345", props.getProperty("mail.smtp.writetimeout"))
    }

    @Test
    fun gmailSmtpProperties_clampsInvalidTimeoutToOneMillisecond() {
        val props = gmailSmtpProperties(timeoutMs = 0)

        assertEquals("1", props.getProperty("mail.smtp.connectiontimeout"))
        assertEquals("1", props.getProperty("mail.smtp.timeout"))
        assertEquals("1", props.getProperty("mail.smtp.writetimeout"))
    }
}
