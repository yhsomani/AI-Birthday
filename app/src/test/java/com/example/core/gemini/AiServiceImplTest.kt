package com.example.core.gemini

import com.example.core.resilience.LogLevel
import com.example.core.resilience.StructuredLogger
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessagePromptContext
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
            context = promptContext(eventType = "ANNIVERSARY"),
        )

        assertEquals("Happy Anniversary! Wishing you both a lifetime of love and happiness.", result.standard)
        assertTrue(result.isUsingFallback)
    }

    @Test
    fun `regenerateMessage uses event type for fallback copy`() = runTest {
        coEvery { geminiClient.generate(any()) } returns """{"error":"quota"}"""

        val result = service.regenerateMessage(
            previousMessage = "old draft",
            context = promptContext(eventType = "WORK_ANNIVERSARY"),
            feedbackInstruction = "Make it warmer",
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
            context = promptContext(eventType = "BIRTHDAY"),
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

    private fun promptContext(eventType: String) = MessagePromptContext(
        contactId = ContactId("contact_1"),
        eventId = OccasionId("event_1"),
        firstName = "Asha",
        nickname = null,
        relationshipType = "FRIEND",
        knownSince = null,
        ageTurning = null,
        interests = emptyList(),
        sharedHistory = emptyList(),
        daysSinceLastContact = 0,
        eventType = eventType,
        eventOccurrenceNumber = null,
        preferredLanguage = "en",
        userStyleSamples = emptyList(),
        usesEmoji = true,
        avgMessageLength = 120,
        commonPhrases = emptyList(),
        previousWishes = emptyList(),
        formalityLevel = "CASUAL",
        preferredChannel = MessageChannel.SMS,
    )
}
