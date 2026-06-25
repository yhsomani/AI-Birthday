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
import com.example.domain.service.SchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    private val schedulerService: SchedulerService = mockk(relaxed = true)

    private val useCase = RegeneratePendingMessageUseCase(
        messageRepository,
        contactRepository,
        eventRepository,
        styleProfileRepository,
        memoryNoteRepository,
        giftHistoryRepository,
        aiService,
        preferencesRepository,
        schedulerService,
    )

    private fun pending(
        approvalMode: String = "SMART_APPROVE",
        status: String = "PENDING",
        channel: String = "SMS",
        editedByUser: Boolean = false,
        userEditedText: String? = null,
    ) = PendingMessageEntity(
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
        channel = channel,
        scheduledForMs = 1_700_000_000_000L,
        approvalMode = approvalMode,
        status = status,
        editedByUser = editedByUser,
        userEditedText = userEditedText,
        qualityScore = 100,
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
        val contact = ContactEntity(id = "c_1", name = "Riya", primaryPhone = "+15551234567")
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
        every { preferencesRepository.getGlobalAutomationMode() } returns "SMART_APPROVE"
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery {
            aiService.regenerateMessage("current draft", contact, event, null, emptyList(), null, emptyList(), emptyList())
        } returns variants

        val result = useCase("pm_1", "current draft")

        assertTrue(result is RegeneratePendingMessageUseCase.Outcome.Regenerated)
        result as RegeneratePendingMessageUseCase.Outcome.Regenerated
        assertEquals("pm_1", result.pendingId)
        assertTrue(result.usedFallback)
        coVerify { messageRepository.insertPending(capture(saved)) }
        assertEquals("pm_1", saved.captured.id)
        assertEquals("fresh emotional", saved.captured.selectedVariantText)
        assertEquals("SMART_APPROVE", saved.captured.approvalMode)
        assertEquals("PENDING", saved.captured.status)
        assertEquals(35, saved.captured.qualityScore)
        assertEquals(false, saved.captured.editedByUser)
        assertEquals(null, saved.captured.userEditedText)
        verify { schedulerService.scheduleExactSend("pm_1") }
    }

    @Test
    fun `invoke forwards feedback instruction to AI regeneration`() = runTest {
        val pending = pending()
        val contact = ContactEntity(id = "c_1", name = "Riya", primaryPhone = "+15551234567")
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
        every { preferencesRepository.getGlobalAutomationMode() } returns "SMART_APPROVE"
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery {
            aiService.regenerateMessage(
                "current draft",
                contact,
                event,
                null,
                emptyList(),
                "Make it warmer",
                emptyList(),
                emptyList(),
            )
        } returns variants

        useCase("pm_1", "current draft", "Make it warmer")

        coVerify {
            aiService.regenerateMessage(
                "current draft",
                contact,
                event,
                null,
                emptyList(),
                "Make it warmer",
                emptyList(),
                emptyList(),
            )
        }
    }

    @Test
    fun `invoke downgrades fully auto fallback regeneration through quality gate`() = runTest {
        val pending = pending(approvalMode = "FULLY_AUTO", status = "APPROVED")
        val contact = ContactEntity(
            id = "c_1",
            name = "Riya",
            primaryPhone = "+15551234567",
            automationMode = "FULLY_AUTO",
        )
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
            short = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            standard = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            long = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            formal = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            funny = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            emotional = "Wishing you a very happy birthday! Hope you have a wonderful day!",
            recommended = "standard",
            isUsingFallback = true,
        )
        val saved = slot<PendingMessageEntity>()

        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.getGlobalAutomationMode() } returns "FULLY_AUTO"
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery {
            aiService.regenerateMessage("current draft", contact, event, null, emptyList(), null, emptyList(), emptyList())
        } returns variants

        useCase("pm_1", "current draft")

        coVerify { messageRepository.insertPending(capture(saved)) }
        assertEquals("SMART_APPROVE", saved.captured.approvalMode)
        assertEquals("PENDING", saved.captured.status)
        assertEquals(35, saved.captured.qualityScore)
        assertTrue(saved.captured.isUsingFallback)
        verify { schedulerService.scheduleExactSend("pm_1") }
    }

    @Test
    fun `invoke forces review and skips scheduling when regenerated draft has no route`() = runTest {
        val pending = pending(approvalMode = "FULLY_AUTO", status = "APPROVED")
        val contact = ContactEntity(
            id = "c_1",
            name = "Riya",
            automationMode = "FULLY_AUTO",
            preferredChannel = "SMS",
        )
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
            short = "Happy birthday Riya, hope you get a peaceful day and a great dinner.",
            standard = "Happy birthday Riya, hope you get a peaceful day and a great dinner.",
            long = "Happy birthday Riya, hope you get a peaceful day and a great dinner.",
            formal = "Happy birthday Riya, hope you get a peaceful day and a great dinner.",
            funny = "Happy birthday Riya, hope you get a peaceful day and a great dinner.",
            emotional = "Happy birthday Riya, hope you get a peaceful day and a great dinner.",
            recommended = "standard",
            isUsingFallback = false,
        )
        val saved = slot<PendingMessageEntity>()

        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.getGlobalAutomationMode() } returns "FULLY_AUTO"
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery {
            aiService.regenerateMessage("current draft", contact, event, null, emptyList(), null, emptyList(), emptyList())
        } returns variants

        useCase("pm_1", "current draft")

        coVerify { messageRepository.insertPending(capture(saved)) }
        assertEquals("ALWAYS_ASK", saved.captured.approvalMode)
        assertEquals("PENDING", saved.captured.status)
        assertEquals("SMS", saved.captured.channel)
        verify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
    }

    @Test
    fun `invoke clears stale user edited text by default`() = runTest {
        val pending = pending(editedByUser = true, userEditedText = "stale edited draft")
        val contact = ContactEntity(id = "c_1", name = "Riya", primaryPhone = "+15551234567")
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
        val saved = slot<PendingMessageEntity>()

        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.getGlobalAutomationMode() } returns "SMART_APPROVE"
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery {
            aiService.regenerateMessage("stale edited draft", contact, event, null, emptyList(), null, emptyList(), emptyList())
        } returns variants

        useCase("pm_1", "stale edited draft")

        coVerify { messageRepository.insertPending(capture(saved)) }
        assertEquals("fresh standard", saved.captured.selectedVariantText)
        assertEquals(false, saved.captured.editedByUser)
        assertEquals(null, saved.captured.userEditedText)
    }

    @Test
    fun `invoke preserves user edited text only when explicitly requested`() = runTest {
        val pending = pending(editedByUser = true, userEditedText = "keep this edit")
        val contact = ContactEntity(id = "c_1", name = "Riya", primaryPhone = "+15551234567")
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
        val saved = slot<PendingMessageEntity>()

        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.getGlobalAutomationMode() } returns "SMART_APPROVE"
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery {
            aiService.regenerateMessage("keep this edit", contact, event, null, emptyList(), null, emptyList(), emptyList())
        } returns variants

        useCase("pm_1", "keep this edit", preserveUserEditedText = true)

        coVerify { messageRepository.insertPending(capture(saved)) }
        assertEquals("keep this edit", saved.captured.selectedVariantText)
        assertEquals(true, saved.captured.editedByUser)
        assertEquals("keep this edit", saved.captured.userEditedText)
    }

    @Test
    fun `invoke preserves approved status only when explicitly requested`() = runTest {
        val pending = pending(approvalMode = "SMART_APPROVE", status = "APPROVED")
        val contact = ContactEntity(id = "c_1", name = "Riya", primaryPhone = "+15551234567")
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
        val saved = slot<PendingMessageEntity>()

        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.getGlobalAutomationMode() } returns "SMART_APPROVE"
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        coEvery { messageRepository.getPendingById("pm_1") } returns pending
        coEvery { contactRepository.getById("c_1") } returns contact
        coEvery { eventRepository.getEventsBefore(Long.MAX_VALUE) } returns listOf(event)
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c_1", 10) } returns emptyList()
        coEvery { memoryNoteRepository.getByContact("c_1") } returns emptyList()
        coEvery { giftHistoryRepository.getByContact("c_1") } returns emptyList()
        coEvery {
            aiService.regenerateMessage("approved draft", contact, event, null, emptyList(), null, emptyList(), emptyList())
        } returns variants

        useCase("pm_1", "approved draft", preserveApprovedStatus = true)

        coVerify { messageRepository.insertPending(capture(saved)) }
        assertEquals("APPROVED", saved.captured.status)
        verify { schedulerService.scheduleExactSend("pm_1") }
    }
}
