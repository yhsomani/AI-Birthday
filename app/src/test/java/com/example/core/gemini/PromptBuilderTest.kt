package com.example.core.gemini

import com.example.core.db.entities.ContactEntity
import com.example.domain.contact.toMessagePromptContact
import com.example.domain.message.buildMessagePromptContext
import com.example.domain.model.contact.ContactClassificationPromptContext
import com.example.domain.model.contact.ContactRelationshipPromptContext
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.MessageChannel
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.domain.model.message.MessagePromptContext
import com.example.domain.model.message.StylePromptProfile
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val contact = ContactClassificationPromptContext(
            id = ContactId("1"),
            displayName = "Alice",
            interactionFrequencyPerMonth = 5f,
        )
        val prompt = builder.buildClassificationPrompt(contact)
        assertTrue(prompt.contains("Alice"))
        assertTrue(prompt.contains("5.0 times/month"))
    }

    @Test
    fun `buildClassificationPrompt requests communication style parsed by ResponseParser`() {
        val contact = ContactClassificationPromptContext(
            id = ContactId("1"),
            displayName = "Alice",
        )

        val prompt = builder.buildClassificationPrompt(contact)

        assertTrue(prompt.contains("\"relationship_type\""))
        assertTrue(prompt.contains("\"relationship_subtype\""))
        assertFalse(prompt.contains("\"type\":"))
        assertTrue(prompt.contains("\"communication_style\""))
        assertTrue(prompt.contains("WARM|FUNNY|PROFESSIONAL|EMOTIONAL"))
    }

    @Test
    fun `buildMessagePromptContext handles birthday with year correctly`() {
        val contact = ContactEntity(
            id = "1", name = "Bob",
            birthdayYear = 1990,
            interestsJson = """["music", "hiking"]""",
            sharedHistoryJson = """["college trip"]""",
            lastInteractionDate = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L)
        )
        val event = occasion(
            id = "1_birthday", contactId = "1",
            type = "BIRTHDAY", dayOfMonth = 15, month = 6,
            year = 2026,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val ctx = buildMessagePromptContext(contact.toMessagePromptContact(), event, null, emptyList())

        assertEquals("Bob", ctx.firstName)
        assertEquals(listOf("music", "hiking"), ctx.interests)
        assertEquals(listOf("college trip"), ctx.sharedHistory)
        assertEquals(10, ctx.daysSinceLastContact)
        assertEquals(36, ctx.ageTurning) // 2026 - 1990
    }

    @Test
    fun `buildMessagePromptContext ageTurning null when year missing`() {
        val contact = ContactEntity(
            id = "2", name = "Charlie",
            birthdayYear = null,
            interestsJson = "[]",
            sharedHistoryJson = "[]"
        )
        val event = occasion(
            id = "2_birthday", contactId = "2",
            type = "BIRTHDAY", dayOfMonth = 20, month = 3,
            year = null,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val ctx = buildMessagePromptContext(contact.toMessagePromptContact(), event, null, emptyList())

        assertNull(ctx.ageTurning)
        assertEquals("BIRTHDAY", ctx.eventType)
    }

    @Test
    fun `buildMessagePromptContext parses style profile correctly`() {
        val contact = ContactEntity(
            id = "3", name = "Diana",
            interestsJson = "[]",
            sharedHistoryJson = "[]"
        )
        val event = occasion(
            id = "3_anniversary", contactId = "3",
            type = "ANNIVERSARY", dayOfMonth = 10, month = 12,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val profile = StylePromptProfile(
            sampleMessagesJson = """["Hey! Happy birthday!"]""",
            commonPhrasesJson = """["Hope you have"]""",
            usesEmoji = true,
            avgMessageLength = 100
        )
        val ctx = buildMessagePromptContext(contact.toMessagePromptContact(), event, profile, emptyList())

        assertEquals(listOf("Hey! Happy birthday!"), ctx.userStyleSamples)
        assertEquals(listOf("Hope you have"), ctx.commonPhrases)
        assertEquals(true, ctx.usesEmoji)
        assertEquals(100, ctx.avgMessageLength)
    }

    @Test
    fun `buildMessagePromptContext includes previous wishes`() {
        val contact = ContactEntity(
            id = "4", name = "Eve",
            interestsJson = "[]",
            sharedHistoryJson = "[]"
        )
        val event = occasion(
            id = "4_birthday", contactId = "4",
            type = "BIRTHDAY", dayOfMonth = 1, month = 1,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val previous = listOf(
            "Happy birthday!",
            "Have a great year!",
        )
        val ctx = buildMessagePromptContext(contact.toMessagePromptContact(), event, null, previous)

        assertEquals(2, ctx.previousWishes.size)
        assertTrue(ctx.previousWishes.contains("Happy birthday!"))
    }

    @Test
    fun `buildMessagePromptContext handles malformed JSON gracefully`() {
        val contact = ContactEntity(
            id = "5", name = "Frank",
            interestsJson = "not json",
            sharedHistoryJson = "[]",
            lastInteractionDate = null
        )
        val event = occasion(
            id = "5_birthday", contactId = "5",
            type = "BIRTHDAY", dayOfMonth = 5, month = 5,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000
        )
        val ctx = buildMessagePromptContext(contact.toMessagePromptContact(), event, null, emptyList())

        assertEquals(emptyList<String>(), ctx.interests)
        assertEquals(0, ctx.daysSinceLastContact)
    }

    @Test
    fun `buildMessagePromptContext maps preferred channel to typed prompt context`() {
        val contact = ContactEntity(
            id = "channel_email",
            name = "Emma",
            preferredChannel = MessageChannel.EMAIL.raw.lowercase(),
        )
        val event = occasion(
            id = "channel_email_birthday",
            contactId = "channel_email",
            type = "BIRTHDAY",
            dayOfMonth = 5,
            month = 5,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000,
        )

        val ctx = buildMessagePromptContext(contact.toMessagePromptContact(), event, null, emptyList())

        assertEquals(MessageChannel.EMAIL, ctx.preferredChannel)
    }

    @Test
    fun `buildMessagePromptContext falls back unsupported prompt channel to sms`() {
        val contact = ContactEntity(
            id = "channel_legacy",
            name = "Finn",
            preferredChannel = "LEGACY_CHANNEL",
        )
        val event = occasion(
            id = "channel_legacy_birthday",
            contactId = "channel_legacy",
            type = "BIRTHDAY",
            dayOfMonth = 5,
            month = 5,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000,
        )

        val ctx = buildMessagePromptContext(contact.toMessagePromptContact(), event, null, emptyList())

        assertEquals(MessageChannel.SMS, ctx.preferredChannel)
    }

    @Test
    fun `buildMessagePromptContext maps pure memory and gift records into prompt context`() {
        val contact = ContactEntity(
            id = "memory_gift_contact",
            name = "Nina",
            interestsJson = "[]",
            sharedHistoryJson = "[]",
        )
        val event = occasion(
            id = "memory_gift_event",
            contactId = "memory_gift_contact",
            type = "BIRTHDAY",
            dayOfMonth = 5,
            month = 5,
            nextOccurrenceMs = System.currentTimeMillis() + 86400000,
        )
        val memoryNotes = listOf(
            MemoryNoteRecord(
                id = MemoryNoteId("note_older"),
                contactId = ContactId("memory_gift_contact"),
                noteText = "Older detail",
                category = "GENERAL",
                dateMs = 1_000L,
                isPinned = false,
            ),
            MemoryNoteRecord(
                id = MemoryNoteId("note_pinned"),
                contactId = ContactId("memory_gift_contact"),
                noteText = "Private phone +91 99999 99999 and favorite tea",
                category = "PREFERENCE",
                dateMs = 500L,
                isPinned = true,
            ),
        )
        val giftHistory = listOf(
            GiftHistoryRecord(
                id = GiftHistoryId("gift_1"),
                contactId = ContactId("memory_gift_contact"),
                giftName = "Tea sampler",
                giftCategory = "Food",
                occasionType = "Birthday",
                year = 2025,
                approxCostInr = 1200,
                receivedWell = true,
                notes = "Used quickly",
            )
        )

        val ctx = buildMessagePromptContext(
            contact = contact.toMessagePromptContact(),
            event = event,
            styleProfile = null,
            previousWishes = emptyList(),
            memoryNotes = memoryNotes,
            giftHistory = giftHistory,
        )

        assertEquals(
            "PREFERENCE: Private phone [PHONE] and favorite tea",
            ctx.memoryNotes.first(),
        )
        assertEquals("2025: Tea sampler (Food, liked: true)", ctx.giftHistory.single())
    }

    @Test
    fun `buildMessageGenerationPrompt includes context`() {
        val ctx = MessagePromptContext(
            contactId = ContactId("prompt_contact"),
            eventId = OccasionId("prompt_event"),
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
            formalityLevel = "CASUAL",
            preferredChannel = MessageChannel.WHATSAPP,
        )
        val prompt = builder.buildMessageGenerationPrompt(ctx)

        assertTrue(prompt.contains("Grace"))
        assertTrue(prompt.contains("Gra"))
        assertTrue(prompt.contains("SISTER"))
        assertTrue(prompt.contains("photography"))
        assertTrue(prompt.contains("Paris trip"))
        assertTrue(prompt.contains("- Preferred send channel: ${MessageChannel.WHATSAPP.raw}"))
        assertTrue(prompt.contains("JSON"))
    }

    @Test
    fun `buildMessageGenerationPrompt does not demand invented specifics when context is sparse`() {
        val ctx = MessagePromptContext(
            contactId = ContactId("prompt_contact"),
            eventId = OccasionId("prompt_event"),
            firstName = "Grace", nickname = null,
            relationshipType = "FRIEND",
            knownSince = null, ageTurning = null,
            interests = emptyList(),
            sharedHistory = emptyList(),
            daysSinceLastContact = 0,
            eventType = "BIRTHDAY",
            eventOccurrenceNumber = null,
            preferredLanguage = "en",
            userStyleSamples = emptyList(),
            usesEmoji = true,
            avgMessageLength = 100,
            commonPhrases = emptyList(),
            previousWishes = emptyList(),
            formalityLevel = "CASUAL",
        )

        val prompt = builder.buildMessageGenerationPrompt(ctx)

        assertTrue(prompt.contains("Do not invent interests, memories, life events, or private details"))
        assertFalse(prompt.contains("Reference at least one real specific interest"))
    }

    @Test
    fun `buildReconnectPrompt includes contact name`() {
        val contact = ContactRelationshipPromptContext(
            id = ContactId("6"),
            displayName = "Henry",
            nickname = "Hen",
            relationshipType = "FRIEND",
        )
        val prompt = builder.buildReconnectPrompt(contact, 100)

        assertTrue(prompt.contains("Henry") || prompt.contains("Hen"))
        assertTrue(prompt.contains("100"))
    }

    @Test
    fun `buildReconnectPrompt includes relationship context and safety constraints`() {
        val contact = ContactRelationshipPromptContext(
            id = ContactId("6"),
            displayName = "Henry",
            relationshipType = "FRIEND",
            healthScore = 18,
            interactionFrequencyPerMonth = 2f,
            interestsJson = "[\"cricket\"]",
            hobbiesJson = "[\"guitar\"]",
            sharedHistoryJson = "[\"college roommates\"]",
            sensitiveTopicsJson = "[\"job search\"]",
            notesText = "Uses +91 99999 99999 for work.",
        )

        val prompt = builder.buildReconnectPrompt(contact, 100)

        assertTrue(prompt.contains("18/100"))
        assertTrue(prompt.contains("cricket"))
        assertTrue(prompt.contains("guitar"))
        assertTrue(prompt.contains("college roommates"))
        assertTrue(prompt.contains("job search"))
        assertTrue(prompt.contains("do not invent memories", ignoreCase = true))
        assertTrue(prompt.contains("[PHONE]"))
    }

    @Test
    fun `buildPostEventFollowUpPrompt uses relationship context and sanitizes original message`() {
        val contact = ContactRelationshipPromptContext(
            id = ContactId("7"),
            displayName = "Ira Shah",
            nickname = "Ira",
            relationshipType = "FRIEND",
            preferredLanguage = "en",
            formalityLevel = "CASUAL",
            interestsJson = "[\"running\"]",
        )

        val prompt = builder.buildPostEventFollowUpPrompt(
            contact = contact,
            originalMessage = "Call me at +91 99999 99999 after the party.",
            eventType = "BIRTHDAY",
            eventLabel = "birthday",
        )

        assertTrue(prompt.contains("Ira"))
        assertTrue(prompt.contains("birthday"))
        assertTrue(prompt.contains("running"))
        assertTrue(prompt.contains("[PHONE]"))
    }

    @Test
    fun `buildHolidayWishPrompt uses relationship context`() {
        val contact = ContactRelationshipPromptContext(
            id = ContactId("8"),
            displayName = "Kabir Singh",
            relationshipType = "COUSIN",
            preferredLanguage = "en",
            formalityLevel = "CASUAL",
            communicationStyle = "FUNNY",
            interestsJson = "[\"cricket\"]",
            sharedHistoryJson = "[\"college roommates\"]",
        )

        val prompt = builder.buildHolidayWishPrompt(
            contact = contact,
            holidayName = "Diwali",
            holidayTone = "warm",
        )

        assertTrue(prompt.contains("Diwali"))
        assertTrue(prompt.contains("COUSIN"))
        assertTrue(prompt.contains("FUNNY"))
        assertTrue(prompt.contains("cricket"))
        assertTrue(prompt.contains("college roommates"))
    }

    @Test
    fun `buildRegenerationPrompt includes original message`() {
        val ctx = MessagePromptContext(
            contactId = ContactId("prompt_contact"),
            eventId = OccasionId("prompt_event"),
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

    private fun occasion(
        id: String,
        contactId: String,
        type: String,
        label: String? = null,
        dayOfMonth: Int,
        month: Int,
        year: Int? = null,
        nextOccurrenceMs: Long,
        isActive: Boolean = true,
        notifyDaysBefore: Int = 1,
        source: String = "MANUAL",
        confidenceScore: Int = 100,
        isVerified: Boolean = true,
    ): Occasion {
        return Occasion(
            id = OccasionId(id),
            contactId = ContactId(contactId),
            type = OccasionType.fromRaw(type),
            label = label,
            date = OccasionDate(
                dayOfMonth = dayOfMonth,
                month = month,
                year = year,
            ),
            nextOccurrenceMs = nextOccurrenceMs,
            isActive = isActive,
            notifyDaysBefore = notifyDaysBefore,
            source = source,
            confidenceScore = confidenceScore,
            isVerified = isVerified,
        )
    }
}
