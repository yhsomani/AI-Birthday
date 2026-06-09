package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.AiService
import com.example.domain.service.MessageVariantsResult
import com.example.domain.service.NotificationService
import com.example.domain.service.PreferencesRepository
import com.example.domain.service.SchedulerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateMessageUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val styleProfileRepository: StyleProfileRepository = mockk(relaxed = true)
    private val aiService: AiService = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val notificationService: NotificationService = mockk(relaxed = true)

    private val useCase = GenerateMessageUseCase(
        contactRepository,
        eventRepository,
        messageRepository,
        styleProfileRepository,
        aiService,
        preferencesRepository,
        schedulerService,
        notificationService
    )

    init {
        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
    }

    @Test
    fun `invoke with missing event returns EventNotFound`() = runTest {
        coEvery { eventRepository.getEventsBefore(any()) } returns emptyList()

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.EventNotFound, result)
    }

    @Test
    fun `invoke with existing pending message returns AlreadyExists`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(event)
        coEvery { messageRepository.pendingExistsForEventOccurrence("c1", "e1", any()) } returns true

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.AlreadyExists, result)
    }

    @Test
    fun `invoke with missing contact returns ContactNotFound`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(event)
        coEvery { messageRepository.pendingExistsForEventOccurrence("c1", "e1", any()) } returns false
        coEvery { contactRepository.getById("c1") } returns null

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.ContactNotFound, result)
    }

    @Test
    fun `invoke returns AiDisabled before calling Gemini when disabled in preferences`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(id = "c1", name = "John")
        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(event)
        coEvery { messageRepository.pendingExistsForEventOccurrence("c1", "e1", any()) } returns false
        coEvery { contactRepository.getById("c1") } returns contact
        every { preferencesRepository.isAiWishGenerationEnabled() } returns false

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.AiDisabled, result)
        coVerify(exactly = 0) { aiService.generateMessage(any(), any(), any(), any()) }
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
    }

    @Test
    fun `invoke with valid parameters generates variants and saves in MANUAL mode`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(id = "c1", name = "John", relationshipType = "FRIEND", preferredChannel = "SMS", automationMode = "MANUAL")
        val variants = MessageVariantsResult("sh", "std", "lg", "fr", "fn", "em", "standard")

        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(event)
        coEvery { messageRepository.pendingExistsForEventOccurrence("c1", "e1", any()) } returns false
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c1", 10) } returns emptyList()
        coEvery { aiService.generateMessage(any(), any(), any(), any()) } returns variants
        coEvery { preferencesRepository.getGlobalAutomationMode() } returns "MANUAL"

        val result = useCase("e1")

        assertTrue(result is GenerateMessageUseCase.GenerationOutcome.Generated)
        val generated = result as GenerateMessageUseCase.GenerationOutcome.Generated
        assertEquals("MANUAL", generated.approvalMode)
        assertEquals(0, generated.retries)

        coVerify { messageRepository.insertPending(any()) }
        coVerify { notificationService.showApprovalNotification(contact, event, variants, any()) }
        coVerify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
    }

    @Test
    fun `invoke with FULLY_AUTO mode schedules exact dispatch`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(id = "c1", name = "John", relationshipType = "FRIEND", preferredChannel = "SMS", automationMode = "FULLY_AUTO")
        val variants = MessageVariantsResult("sh", "std", "lg", "fr", "fn", "em", "standard")

        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(event)
        coEvery { messageRepository.pendingExistsForEventOccurrence("c1", "e1", any()) } returns false
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getSentByContact("c1", 10) } returns emptyList()
        coEvery { aiService.generateMessage(any(), any(), any(), any()) } returns variants

        val result = useCase("e1")

        assertTrue(result is GenerateMessageUseCase.GenerationOutcome.Generated)
        val generated = result as GenerateMessageUseCase.GenerationOutcome.Generated
        assertEquals("FULLY_AUTO", generated.approvalMode)

        coVerify { messageRepository.insertPending(any()) }
        coVerify { schedulerService.scheduleExactSend(any()) }
        coVerify(exactly = 0) { notificationService.showApprovalNotification(any(), any(), any(), any()) }
    }

    @Test
    fun `invoke checks duplicate messages by scheduled occurrence year`() = runTest {
        val event = EventEntity(
            id = "e1",
            contactId = "c1",
            type = "BIRTHDAY",
            label = "Test",
            dayOfMonth = 1,
            month = 1,
            nextOccurrenceMs = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, 2027)
                set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
                set(java.util.Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
        )
        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(event)
        coEvery { messageRepository.pendingExistsForEventOccurrence("c1", "e1", 2027) } returns true

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.AlreadyExists, result)
    }
}
