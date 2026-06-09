package com.example.core.resilience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveLogRedactorTest {

    @Test
    fun redact_removesEmailBearerTokenAndSensitiveQueryValues() {
        val input = "user=aarav@example.com Authorization=Bearer ya29.secret-token " +
            "url=https://people.googleapis.com/v1/people/me/connections?personFields=names&syncToken=sync-secret&pageToken=page-secret"

        val redacted = SensitiveLogRedactor.redact(input)

        assertFalse(redacted.contains("aarav@example.com"))
        assertFalse(redacted.contains("ya29.secret-token"))
        assertFalse(redacted.contains("sync-secret"))
        assertFalse(redacted.contains("page-secret"))
        assertTrue(redacted.contains("[REDACTED_EMAIL]"))
        assertTrue(redacted.contains("Bearer [REDACTED]"))
        assertTrue(redacted.contains("connections?[REDACTED_QUERY]"))
    }

    @Test
    fun googleContactsHttpErrorSummary_returnsSafeGenericMessages() {
        assertEquals(
            "HTTP 403: Google Contacts access is disabled or permission was denied",
            SensitiveLogRedactor.googleContactsHttpErrorSummary(403),
        )
        assertEquals(
            "HTTP 500: Google Contacts service is temporarily unavailable",
            SensitiveLogRedactor.googleContactsHttpErrorSummary(500),
        )
    }
}
