package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityChecksTest {

    @Test
    fun certificatePinsPassReleaseGate_requiresMoreThanSixtyDaysBeforeExpiry() {
        val expiryDate = SecurityChecks.certificatePinExpiryDate()

        assertTrue(SecurityChecks.certificatePinsPassReleaseGate(asOfDate = expiryDate.minusDays(61)))
        assertFalse(SecurityChecks.certificatePinsPassReleaseGate(asOfDate = expiryDate.minusDays(60)))
        assertFalse(SecurityChecks.certificatePinsPassReleaseGate(asOfDate = expiryDate))
    }

    @Test
    fun daysUntilCertificatePinExpiry_usesUtcCalendarDates() {
        val expiryDate = SecurityChecks.certificatePinExpiryDate()

        assertEquals(
            61L,
            SecurityChecks.daysUntilCertificatePinExpiry(asOfDate = expiryDate.minusDays(61)),
        )
    }

    @Test
    fun certificatePinExpiryMessage_namesDateAndReleaseGate() {
        val message = SecurityChecks.certificatePinExpiryMessage(daysUntilExpiry = 60)

        assertTrue(message.contains(SecurityChecks.PIN_EXPIRY_DATE))
        assertTrue(message.contains(SecurityChecks.PIN_EXPIRY_RELEASE_GATE_DAYS.toString()))
    }
}
