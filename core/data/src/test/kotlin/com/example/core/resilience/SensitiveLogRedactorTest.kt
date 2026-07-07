package com.example.core.resilience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34])
class SensitiveLogRedactorTest {

    @Test
    fun redact_removesEmailBearerTokenAndSensitiveQueryValues() {
        val input = "user=aarav@example.com Authorization=Bearer ya29.secret-token " +
            "url=https://people.googleapis.com/v1/people/me/connections?personFields=names&syncToken=sync-secret&pageToken=page-secret " +
            "phone=+91 98765 43210 apiKey=AIzaSyFakeFakeFakeFakeFakeFake password=hunter2"

        val redacted = SensitiveLogRedactor.redact(input)

        assertFalse(redacted.contains("aarav@example.com"))
        assertFalse(redacted.contains("ya29.secret-token"))
        assertFalse(redacted.contains("sync-secret"))
        assertFalse(redacted.contains("page-secret"))
        assertFalse(redacted.contains("+91 98765 43210"))
        assertFalse(redacted.contains("AIzaSyFakeFakeFakeFakeFakeFake"))
        assertFalse(redacted.contains("hunter2"))
        assertTrue(redacted.contains("[REDACTED_EMAIL]"))
        assertTrue(redacted.contains("Bearer [REDACTED]"))
        assertTrue(redacted.contains("connections?[REDACTED_QUERY]"))
        assertTrue(redacted.contains("[REDACTED_PHONE]"))
        assertTrue(redacted.contains("apiKey=[REDACTED]"))
        assertTrue(redacted.contains("password=[REDACTED]"))
    }

    @Test
    fun redact_removesInlineMessageBodyAssignments() {
        val input = "messageText=\"Happy birthday Aarav, I hope your family dinner is warm\" " +
            "draftText=Personalized-memory-note recommendedVariantText=\"Private selected wish\" " +
            "payload='Private recovery wish text' messageId=msg_1 recommendedVariantName=standard"

        val redacted = SensitiveLogRedactor.redact(input)

        assertFalse(redacted.contains("Happy birthday Aarav"))
        assertFalse(redacted.contains("Personalized-memory-note"))
        assertFalse(redacted.contains("Private selected wish"))
        assertFalse(redacted.contains("Private recovery wish text"))
        assertTrue(redacted.contains("messageText=[REDACTED_MESSAGE_BODY]"))
        assertTrue(redacted.contains("draftText=[REDACTED_MESSAGE_BODY]"))
        assertTrue(redacted.contains("recommendedVariantText=[REDACTED_MESSAGE_BODY]"))
        assertTrue(redacted.contains("payload=[REDACTED_MESSAGE_BODY]"))
        assertTrue(redacted.contains("messageId=msg_1"))
        assertTrue(redacted.contains("recommendedVariantName=standard"))
    }

    @Test
    fun redact_removesCompoundKeyValues() {
        val input = "user=aarav@example.com senderEmailPassword=hunter2 userToken=secret-token myApiKey=AIzaSyFake"

        val redacted = SensitiveLogRedactor.redact(input)

        assertFalse(redacted.contains("hunter2"))
        assertFalse(redacted.contains("secret-token"))
        assertFalse(redacted.contains("AIzaSyFake"))
        assertTrue(redacted.contains("senderEmailPassword=[REDACTED]"))
        assertTrue(redacted.contains("userToken=[REDACTED]"))
        assertTrue(redacted.contains("myApiKey=[REDACTED]"))
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

    @Test
    fun healthMonitor_redactsSensitiveErrorSnapshots() {
        HealthMonitor.recordError(
            context = "GeminiClient.generate user=aarav@example.com",
            error = "apiKey=AIzaSyFakeFakeFakeFakeFakeFake password=hunter2 token=secret-token",
        )

        val recentErrors = HealthMonitor.snapshot().recentErrors.joinToString("\n")

        assertFalse(recentErrors.contains("aarav@example.com"))
        assertFalse(recentErrors.contains("AIzaSyFakeFakeFakeFakeFakeFake"))
        assertFalse(recentErrors.contains("hunter2"))
        assertFalse(recentErrors.contains("secret-token"))
        assertTrue(recentErrors.contains("[REDACTED_EMAIL]"))
        assertTrue(recentErrors.contains("apiKey=[REDACTED]"))
        assertTrue(recentErrors.contains("password=[REDACTED]"))
        assertTrue(recentErrors.contains("token=[REDACTED]"))
    }

    @Test
    fun structuredLogger_redactsThrowableMessagesAndDropsThrowableReferences() {
        StructuredLogger.clearForTests()
        val sensitiveMessage = "apiKey=AIzaSyFakeFakeFakeFakeFakeFake password=hunter2 user=aarav@example.com"

        StructuredLogger.e(
            tag = "SensitiveLogRedactorTest",
            message = "Operation failed: $sensitiveMessage",
            throwable = IllegalStateException(sensitiveMessage),
            extras = mapOf("raw" to sensitiveMessage),
        )

        val entry = StructuredLogger.getRecent(1).single()
        val flattened = entry.message + " " + entry.extras.values.joinToString(" ")

        assertNull(entry.throwable)
        assertEquals("IllegalStateException", entry.extras["exception"])
        assertFalse(flattened.contains("AIzaSyFakeFakeFakeFakeFakeFake"))
        assertFalse(flattened.contains("hunter2"))
        assertFalse(flattened.contains("aarav@example.com"))
        assertTrue(flattened.contains("apiKey=[REDACTED]"))
        assertTrue(flattened.contains("password=[REDACTED]"))
        assertTrue(flattened.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun structuredLogger_redactsMessageBodyExtrasByKey() {
        StructuredLogger.clearForTests()

        StructuredLogger.i(
            tag = "SensitiveLogRedactorTest",
            message = "Dispatch payload prepared",
            extras = mapOf(
                "messageText" to "Happy birthday Aarav, your private family note is included.",
                "recommendedVariantText" to "Aarav, your private memory means a lot.",
                "recommendedVariantName" to "emotional",
                "messageId" to "msg_1",
            ),
        )

        val entry = StructuredLogger.getRecent(1).single()

        assertEquals("[REDACTED_MESSAGE_BODY]", entry.extras["messageText"])
        assertEquals("[REDACTED_MESSAGE_BODY]", entry.extras["recommendedVariantText"])
        assertEquals("emotional", entry.extras["recommendedVariantName"])
        assertEquals("msg_1", entry.extras["messageId"])
        assertFalse(entry.extras.values.joinToString(" ").contains("Happy birthday Aarav"))
        assertFalse(entry.extras.values.joinToString(" ").contains("private family note"))
        assertFalse(entry.extras.values.joinToString(" ").contains("private memory"))
    }

    @Test
    fun fallbackOrchestrator_redactsProviderFailureLogs() = runTest {
        ShadowLog.clear()
        val sensitiveMessage = "user=aarav@example.com apiKey=AIzaSyFakeFakeFakeFakeFakeFake token=secret-token"
        val orchestrator = FallbackOrchestrator(
            providers = listOf(
                object : FallbackProvider<String> {
                    override suspend fun primary(): String {
                        throw IllegalStateException(sensitiveMessage)
                    }

                    override suspend fun fallback(): String {
                        throw IllegalStateException(sensitiveMessage)
                    }
                },
            ),
            name = "apiKey=AIzaSyFakeFakeFakeFakeFakeFake",
        )

        runCatching { orchestrator.execute() }

        val logs = ShadowLog.getLogs().joinToString("\n") { "${it.tag}: ${it.msg}" }
        assertFalse(logs.contains("aarav@example.com"))
        assertFalse(logs.contains("AIzaSyFakeFakeFakeFakeFakeFake"))
        assertFalse(logs.contains("secret-token"))
        assertTrue(logs.contains("[REDACTED_EMAIL]"))
        assertTrue(logs.contains("apiKey=[REDACTED]"))
        assertTrue(logs.contains("token=[REDACTED]"))
    }
}
