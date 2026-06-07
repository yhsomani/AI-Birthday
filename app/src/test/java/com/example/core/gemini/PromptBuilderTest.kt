package com.example.core.gemini

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptBuilderTest {

    private val builder = PromptBuilder()

    @Test
    fun `buildClassificationPrompt includes contact name`() {
        val contact = ContactEntity(
            id = "1", name = "Alice",
            interactionFrequencyPerMonth = 5f
        )
        val prompt = builder.buildClassificationPrompt(contact)
        assertTrue(prompt.contains("Alice"))
        assertTrue(prompt.contains("5.0 times/month"))
    }

    @Test
    fun `buildContactContext handles birthday with year correctly`() {
        val contact = ContactEntity(
            id = "1", name = "Bob",
            birthdayYear = 1990,
            interestsJson = """["music", "hiking"]""",
            sharedHistoryJson = """["college trip"]""",
            lastInteractionDate = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L)
        )
        val event = EventEntity(
            id = "1_birthday", contactId = "1",
            type = "BIRTHDAY", dayOfMonth = 15, month = 6,
            year = 2026,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val ctx = builder.buildContactContext(contact, event, null, emptyList())

        assertEquals("Bob", ctx.firstName)
        assertEquals(listOf("music", "hiking"), ctx.interests)
        assertEquals(listOf("college trip"), ctx.sharedHistory)
        assertEquals(10, ctx.daysSinceLastContact)
        assertEquals(36, ctx.ageTurning) // 2026 - 1990
    }

    @Test
    fun `buildContactContext ageTurning null when year missing`() {
        val contact = ContactEntity(
            id = "2", name = "Charlie",
            birthdayYear = null,
            interestsJson = "[]",
            sharedHistoryJson = "[]"
        )
        val event = EventEntity(
            id = "2_birthday", contactId = "2",
            type = "BIRTHDAY", dayOfMonth = 20, month = 3,
            year = null,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val ctx = builder.buildContactContext(contact, event, null, emptyList())

        assertNull(ctx.ageTurning)
        assertEquals("BIRTHDAY", ctx.eventType)
    }

    @Test
    fun `buildContactContext parses style profile correctly`() {
        val contact = ContactEntity(
            id = "3", name = "Diana",
            interestsJson = "[]",
            sharedHistoryJson = "[]"
        )
        val event = EventEntity(
            id = "3_anniversary", contactId = "3",
            type = "ANNIVERSARY", dayOfMonth = 10, month = 12,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val profile = StyleProfileEntity(
            id = 1,
            sampleMessagesJson = """["Hey! Happy birthday!"]""",
            commonPhrasesJson = """["Hope you have"]""",
            usesEmoji = true,
            avgMessageLength = 100
        )
        val ctx = builder.buildContactContext(contact, event, profile, emptyList())

        assertEquals(listOf("Hey! Happy birthday!"), ctx.userStyleSamples)
        assertEquals(listOf("Hope you have"), ctx.commonPhrases)
        assertEquals(true, ctx.usesEmoji)
        assertEquals(100, ctx.avgMessageLength)
    }

    @Test
    fun `buildContactContext includes previous wishes`() {
        val contact = ContactEntity(
            id = "4", name = "Eve",
            interestsJson = "[]",
            sharedHistoryJson = "[]"
        )
        val event = EventEntity(
            id = "4_birthday", contactId = "4",
            type = "BIRTHDAY", dayOfMonth = 1, month = 1,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val previous = listOf(
            SentMessageEntity(
                id = "m1",
                contactId = "4",
                eventType = "4_birthday",
                eventYear = 2025,
                messageText = "Happy birthday!",
                channel = "SMS",
                sentAtMs = 1704067200000L,
                deliveryStatus = "SENT"
            ),
            SentMessageEntity(
                id = "m2",
                contactId = "4",
                eventType = "4_birthday",
                eventYear = 2024,
                messageText = "Have a great year!",
                channel = "SMS",
                sentAtMs = 1672531200000L,
                deliveryStatus = "SENT"
            )
        )
        val ctx = builder.buildContactContext(contact, event, null, previous)

        assertEquals(2, ctx.previousWishes.size)
        assertTrue(ctx.previousWishes.contains("Happy birthday!"))
    }

    @Test
    fun `buildContactContext handles malformed JSON gracefully`() {
        val contact = ContactEntity(
            id = "5", name = "Frank",
            interestsJson = "not json",
            sharedHistoryJson = "[]",
            lastInteractionDate = null
        )
        val event = EventEntity(
            id = "5_birthday", contactId = "5",
            type = "BIRTHDAY", dayOfMonth = 5, month = 5,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val ctx = builder.buildContactContext(contact, event, null, emptyList())

        assertEquals(emptyList<String>(), ctx.interests)
        assertEquals(0, ctx.daysSinceLastContact)
    }

    @Test
    fun `buildMessageGenerationPrompt includes context`() {
        val ctx = ContactContextObject(
            firstName = "Grace", nickname = "Gra",
            relationshipType = "SISTER",
            knownSince = null, ageTurning = 25,
            interests = listOf("photography"),
            sharedHistory = listOf("Paris trip"),
            daysSinceLastContact = 30,
            eventType = "BIRTHDAY",
            eventOccurrenceNumber = 25,
            preferredLanguage = "en",
            userStyleSamples = listOf("Happy bday sis!"),
            usesEmoji = true,
            avgMessageLength = 80,
            commonPhrases = listOf("Love you"),
            previousWishes = listOf("Happy birthday last year"),
            formalityLevel = "CASUAL"
        )
        val prompt = builder.buildMessageGenerationPrompt(ctx)

        assertTrue(prompt.contains("Grace"))
        assertTrue(prompt.contains("Gra"))
        assertTrue(prompt.contains("SISTER"))
        assertTrue(prompt.contains("photography"))
        assertTrue(prompt.contains("Paris trip"))
        assertTrue(prompt.contains("JSON"))
    }

    @Test
    fun `buildReconnectPrompt includes contact name`() {
        val contact = ContactEntity(
            id = "6", name = "Henry", nickname = "Hen",
            relationshipType = "FRIEND",
            lastInteractionDate = System.currentTimeMillis() - (100 * 24 * 60 * 60 * 1000L)
        )
        val prompt = builder.buildReconnectPrompt(contact, 100)

        assertTrue(prompt.contains("Henry") || prompt.contains("Hen"))
        assertTrue(prompt.contains("100"))
    }

    @Test
    fun `buildRegenerationPrompt includes original message`() {
        val ctx = ContactContextObject(
            firstName = "Ivy", nickname = null,
            relationshipType = "FRIEND",
            knownSince = null, ageTurning = null,
            interests = emptyList(),
            sharedHistory = emptyList(),
            daysSinceLastContact = 5,
            eventType = "BIRTHDAY",
            eventOccurrenceNumber = null,
            preferredLanguage = "en",
            userStyleSamples = emptyList(),
            usesEmoji = true,
            avgMessageLength = 100,
            commonPhrases = emptyList(),
            previousWishes = emptyList(),
            formalityLevel = "CASUAL"
        )
        val prompt = builder.buildRegenerationPrompt("Happy birthday!", ctx)

        assertTrue(prompt.contains("Happy birthday!"))
    }
}
