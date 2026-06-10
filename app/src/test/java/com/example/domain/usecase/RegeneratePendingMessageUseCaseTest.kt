package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.AiService
import com.example.domain.service.MessageVariantsResult
import com.example.domain.service.PreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegeneratePendingMessageUseCaseTest {
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val styleProfileRepository: StyleProfileRepository = mockk(relaxed = true)
    private val memoryNoteRepository: MemoryNoteRepository = mockk(relaxed = true)
    private val giftHistoryRepository: GiftHistoryRepository = mockk(relaxed = true)
    private val aiService: AiService = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)

    private val useCase = RegeneratePendingMessageUseCase(
        messageRepository,
        contactRepository,
        eventRepository,
        styleProfileRepository,
        memoryNoteRepository,
        giftHistoryRepository,
        aiService,
        preferencesRepository,
    )

    private fun pending() = PendingMessageEntity(
        id = "pm_1",
        contactId = "c_1",
        eventId = "e_1",
        shortVariant = "old short",
        standardVariant = "old standard",
        longVariant = "old long",
        formalVariant = "old formal",
        funnyVariant = "old funny",
        emotionalVariant = "old emotional",
        selectedVariant = "standard",
        selectedVariantText = "old standard",
        channel = "SMS",
        scheduledForMs = 1_700_000_000_000L,
        approvalMode = "MANUAL",
        status = "PENDING",
    )

    @Test
    fun `invoke returns AiDisabled before loading context when setting is off`() = runTest {
        every { preferencesRepository.isAiWishGenerationEnabled() } returns false

        val result = useCase("pm_1", "draft")

        assertEquals(RegeneratePendingMessageUseCase.Outcome.AiDisabled, result)
        coVerify(exactly = 0) { messageRepository.getPendingById(any()) }
    }

    @Test
    fun `invoke regenerates variants and saves same pending message`() = runTest {
        val pending = pending()
        val contact = ContactEntity(id = "c_1", name = "Riya")
        val event = EventEntity(
            id = "e_1",
            contactId = "c_1",
            type = "BIRTHDAY",
            label = "Birthday",
            dayOfMonth = 4,
            month = 9,
            nextOccurrenceMs = 1_700_000_000_000L,
        )
        val variants = MessageVariantsResult(
            short = "fresh short",
            standard = "fresh standard",
            long = "fresh long",
            formal = "fresh formal",
            funny = "fresh funny",
            emotional = "fresh emotional",
            recommended = "emotional",
            isUsingFallback = true,
        )
        val saved = slot<PendingMessageEntity>()

        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery { aiService.regenerateMessage("current draft", contact, event, null, emptyList(), null) } returns variants

        val result = useCase("pm_1", "current draft")

        assertTrue(result is RegeneratePendingMessageUseCase.Outcome.Regenerated)
        result as RegeneratePendingMessageUseCase.Outcome.Regenerated
        assertEquals("pm_1", result.pendingId)
        assertTrue(result.usedFallback)
        coVerify { messageRepository.insertPending(capture(saved)) }
        assertEquals("pm_1", saved.captured.id)
        assertEquals("fresh emotional", saved.captured.selectedVariantText)
        assertEquals("PENDING", saved.captured.status)
    }

    @Test
    fun `invoke forwards feedback instruction to AI regeneration`() = runTest {
        val pending = pending()
        val contact = ContactEntity(id = "c_1", name = "Riya")
        val event = EventEntity(
            id = "e_1",
            contactId = "c_1",
            type = "BIRTHDAY",
            label = "Birthday",
            dayOfMonth = 4,
            month = 9,
            nextOccurrenceMs = 1_700_000_000_000L,
        )
        val variants = MessageVariantsResult(
            short = "fresh short",
            standard = "fresh standard",
            long = "fresh long",
            formal = "fresh formal",
            funny = "fresh funny",
            emotional = "fresh emotional",
            recommended = "standard",
            isUsingFallback = false,
        )

        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery {
            aiService.regenerateMessage("current draft", contact, event, null, emptyList(), "Make it warmer")
        } returns variants

        useCase("pm_1", "current draft", "Make it warmer")

        coVerify {
            aiService.regenerateMessage("current draft", contact, event, null, emptyList(), "Make it warmer")
        }
    }
}
