package com.example.core.gemini

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.resilience.LogLevel
import com.example.core.resilience.StructuredLogger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiServiceImplTest {

    private val geminiClient = mockk<GeminiClient>()
    private val service = AiServiceImpl(geminiClient)

    @Test
    fun `generateMessage uses event type for fallback copy`() = runTest {
        coEvery { geminiClient.generate(any()) } returns "not json"

        val result = service.generateMessage(
            contact = contact(),
            event = event(type = "ANNIVERSARY"),
            styleProfile = null,
            previousMessages = emptyList(),
            memoryNotes = emptyList(),
            giftHistory = emptyList(),
        )

        assertEquals("Happy Anniversary! Wishing you both a lifetime of love and happiness.", result.standard)
        assertTrue(result.isUsingFallback)
    }

    @Test
    fun `regenerateMessage uses event type for fallback copy`() = runTest {
        coEvery { geminiClient.generate(any()) } returns """{"error":"quota"}"""

        val result = service.regenerateMessage(
            previousMessage = "old draft",
            contact = contact(),
            event = event(type = "WORK_ANNIVERSARY"),
            styleProfile = null,
            previousMessages = emptyList(),
            feedbackInstruction = "Make it warmer",
            memoryNotes = emptyList(),
            giftHistory = emptyList(),
        )

        assertEquals("Congratulations on your work anniversary! Thank you for your hard work and dedication.", result.standard)
        assertTrue(result.isUsingFallback)
    }

    @Test
    fun `generateMessage logs fallback reason without raw AI response`() = runTest {
        StructuredLogger.clearForTests()
        coEvery { geminiClient.generate(any()) } returns
            "not json: phone +91 98765 43210 email private@example.com"

        val result = service.generateMessage(
            contact = contact(),
            event = event(type = "BIRTHDAY"),
            styleProfile = null,
            previousMessages = emptyList(),
            memoryNotes = emptyList(),
            giftHistory = emptyList(),
        )

        assertTrue(result.isUsingFallback)
        val entry = StructuredLogger.getRecent()
            .single { it.message == "AI message response parsed with fallback" }
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("generate", entry.extras["operation"])
        assertEquals("BIRTHDAY", entry.extras["eventType"])
        assertEquals("malformed_json", entry.extras["fallbackReason"])

        val loggedText = StructuredLogger.getRecent().joinToString("\n") { log ->
            log.message + " " + log.extras.entries.joinToString(" ") { "${it.key}=${it.value}" }
        }
        assertFalse(loggedText.contains("98765"))
        assertFalse(loggedText.contains("private@example.com"))
        assertFalse(loggedText.contains("not json"))
    }

    private fun contact() = ContactEntity(
        id = "contact_1",
        name = "Asha Rao",
    )

    private fun event(type: String) = EventEntity(
        id = "event_1",
        contactId = "contact_1",
        type = type,
        dayOfMonth = 1,
        month = 1,
        nextOccurrenceMs = System.currentTimeMillis() + 86_400_000L,
    )
}
