package com.example.domain.usecase

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.notification.ApprovalNotificationRequest
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.slot

class GenerateMessageUseCaseTest {

    private val contactRepository: ContactRepository = mockk(relaxed = true)
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val styleProfileRepository: StyleProfileRepository = mockk(relaxed = true)
    private val memoryNoteRepository: MemoryNoteRepository = mockk(relaxed = true)
    private val giftHistoryRepository: GiftHistoryRepository = mockk(relaxed = true)
    private val aiService: AiService = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val notificationService: NotificationService = mockk(relaxed = true)

    private val useCase = GenerateMessageUseCase(
        contactRepository,
        eventRepository,
        messageRepository,
        styleProfileRepository,
        memoryNoteRepository,
        giftHistoryRepository,
        aiService,
        preferencesRepository,
        schedulerService,
        notificationService
    )

    init {
        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.getGlobalAutomationMode() } returns ApprovalMode.SMART_APPROVE
    }

    @Test
    fun `invoke with missing event returns EventNotFound`() = runTest {
        coEvery { eventRepository.getOccasionById("e1") } returns null

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.EventNotFound, result)
    }

    @Test
    fun `invoke with existing pending message returns AlreadyExists`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns pendingMessage()

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.AlreadyExists, result)
    }

    @Test
    fun `invoke with missing contact returns ContactNotFound`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns null

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.ContactNotFound, result)
    }

    @Test
    fun `invoke returns AiDisabled before calling Gemini when disabled in preferences`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(id = "c1", name = "John")
        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns contact
        every { preferencesRepository.isAiWishGenerationEnabled() } returns false

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.AiDisabled, result)
        coVerify(exactly = 0) { aiService.generateMessage(any()) }
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
    }

    @Test
    fun `invoke with unknown approval mode falls back to smart approval`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            relationshipType = "FRIEND",
            primaryPhone = "+15551234567",
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = "MANUAL",
        )
        val draft = "Happy birthday John, hope the year ahead brings relaxed weekends, good coffee, and more time with friends."
        val variants = MessageVariantsResult(draft, draft, draft, draft, draft, draft, "standard")

        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getGenerationHistoryByContact("c1", 10) } returns MessageGenerationHistory()
        coEvery { memoryNoteRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { giftHistoryRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { aiService.generateMessage(any()) } returns variants
        every { preferencesRepository.getGlobalAutomationMode() } returns ApprovalMode.UNKNOWN

        val result = useCase("e1")

        assertTrue(result is GenerateMessageUseCase.GenerationOutcome.Generated)
        val generated = result as GenerateMessageUseCase.GenerationOutcome.Generated
        assertEquals(ApprovalMode.SMART_APPROVE, generated.approvalMode)
        assertEquals(0, generated.retries)

        coVerify { messageRepository.insertPending(any()) }
        coVerify {
            notificationService.showApprovalNotification(
                request = match<ApprovalNotificationRequest> {
                    it.contactId.value == "c1" &&
                        it.contactDisplayName == "John" &&
                        it.eventId.value == "e1"
                },
                variants = variants,
            )
        }
        coVerify { schedulerService.scheduleExactSend(any()) }
    }

    @Test
    fun `invoke with FULLY_AUTO mode schedules exact dispatch`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            relationshipType = "FRIEND",
            primaryPhone = "+15551234567",
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = "FULLY_AUTO",
        )
        val draft = "Happy birthday John, hope the new year brings more weekend rides and good coffee catchups."
        val variants = MessageVariantsResult(draft, draft, draft, draft, draft, draft, "standard")

        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getGenerationHistoryByContact("c1", 10) } returns MessageGenerationHistory()
        coEvery { memoryNoteRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { giftHistoryRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { aiService.generateMessage(any()) } returns variants

        val result = useCase("e1")

        assertTrue(result is GenerateMessageUseCase.GenerationOutcome.Generated)
        val generated = result as GenerateMessageUseCase.GenerationOutcome.Generated
        assertEquals(ApprovalMode.FULLY_AUTO, generated.approvalMode)

        coVerify { messageRepository.insertPending(any()) }
        coVerify { schedulerService.scheduleExactSend(any()) }
        coVerify(exactly = 0) { notificationService.showApprovalNotification(any(), any()) }
    }

    @Test
    fun `invoke forces review and skips scheduling when no delivery route is available`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            relationshipType = "FRIEND",
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = "FULLY_AUTO",
        )
        val draft = "Happy birthday John, hope the day gives you time to relax and enjoy a great coffee."
        val variants = MessageVariantsResult(draft, draft, draft, draft, draft, draft, "standard")
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getGenerationHistoryByContact("c1", 10) } returns MessageGenerationHistory()
        coEvery { memoryNoteRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { giftHistoryRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { aiService.generateMessage(any()) } returns variants
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""

        val result = useCase("e1")

        assertTrue(result is GenerateMessageUseCase.GenerationOutcome.Generated)
        assertEquals(ApprovalMode.ALWAYS_ASK, (result as GenerateMessageUseCase.GenerationOutcome.Generated).approvalMode)
        coVerify { messageRepository.insertPending(capture(pendingSlot)) }
        assertEquals("ALWAYS_ASK", pendingSlot.captured.approvalMode)
        assertEquals("PENDING", pendingSlot.captured.status)
        assertEquals(MessageChannel.SMS.raw, pendingSlot.captured.channel)
        coVerify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
        coVerify {
            notificationService.showApprovalNotification(
                request = match<ApprovalNotificationRequest> {
                    it.contactId.value == "c1" &&
                        it.eventId.value == "e1" &&
                        it.messageId.value == pendingSlot.captured.id
                },
                variants = variants,
            )
        }
    }

    @Test
    fun `invoke downgrades fallback fully auto draft to smart approve before scheduling`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            relationshipType = "FRIEND",
            primaryPhone = "+15551234567",
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = "FULLY_AUTO",
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
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getGenerationHistoryByContact("c1", 10) } returns MessageGenerationHistory()
        coEvery { memoryNoteRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { giftHistoryRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { aiService.generateMessage(any()) } returns variants

        val result = useCase("e1")

        assertTrue(result is GenerateMessageUseCase.GenerationOutcome.Generated)
        assertEquals(ApprovalMode.SMART_APPROVE, (result as GenerateMessageUseCase.GenerationOutcome.Generated).approvalMode)
        coVerify { messageRepository.insertPending(capture(pendingSlot)) }
        assertEquals("SMART_APPROVE", pendingSlot.captured.approvalMode)
        assertEquals("PENDING", pendingSlot.captured.status)
        assertEquals(35, pendingSlot.captured.qualityScore)
        assertTrue(pendingSlot.captured.isUsingFallback)
        verify { notificationService.showAiFallbackAlert() }
        coVerify { schedulerService.scheduleExactSend(any()) }
        coVerify {
            notificationService.showApprovalNotification(
                request = match<ApprovalNotificationRequest> {
                    it.contactId.value == "c1" &&
                        it.eventId.value == "e1"
                },
                variants = variants,
            )
        }
    }

    @Test
    fun `invoke stores selected available channel instead of unavailable preferred channel`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            relationshipType = "FRIEND",
            primaryPhone = "+15551234567",
            primaryEmail = null,
            preferredChannel = MessageChannel.EMAIL.raw,
            automationMode = "SMART_APPROVE",
        )
        val draft = "Happy birthday John, hope you get a relaxed day and a good coffee catchup soon."
        val variants = MessageVariantsResult(draft, draft, draft, draft, draft, draft, "standard")
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getGenerationHistoryByContact("c1", 10) } returns MessageGenerationHistory()
        coEvery { memoryNoteRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { giftHistoryRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { aiService.generateMessage(any()) } returns variants
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""

        useCase("e1")

        coVerify { messageRepository.insertPending(capture(pendingSlot)) }
        assertEquals(MessageChannel.SMS.raw, pendingSlot.captured.channel)
    }

    @Test
    fun `invoke uses contact custom send time for pending schedule`() = runTest {
        val eventMs = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 3)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = eventMs)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = "ALWAYS_ASK",
            customSendTimeHour = 14,
            customSendTimeMinute = 45,
        )
        val draft = "Happy birthday John, hope the year ahead brings relaxed weekends, good coffee, and more time with friends."
        val variants = MessageVariantsResult(draft, draft, draft, draft, draft, draft, "standard")
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getGenerationHistoryByContact("c1", 10) } returns MessageGenerationHistory()
        coEvery { memoryNoteRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { giftHistoryRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { aiService.generateMessage(any()) } returns variants
        every { preferencesRepository.getQuietHoursStart() } returns 0
        every { preferencesRepository.getQuietHoursEnd() } returns 0
        every { preferencesRepository.getBlackoutDates() } returns "[]"

        useCase("e1")

        coVerify { messageRepository.insertPending(capture(pendingSlot)) }
        val scheduled = java.util.Calendar.getInstance().apply { timeInMillis = pendingSlot.captured.scheduledForMs }
        assertEquals(14, scheduled.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(45, scheduled.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `invoke forces approval when skip auto wish is enabled`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            relationshipType = "FRIEND",
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = "FULLY_AUTO",
            skipAutoWish = true,
        )
        val variants = MessageVariantsResult("sh", "std", "lg", "fr", "fn", "em", "standard")

        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns null
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getGenerationHistoryByContact("c1", 10) } returns MessageGenerationHistory()
        coEvery { memoryNoteRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { giftHistoryRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { aiService.generateMessage(any()) } returns variants

        val result = useCase("e1")

        assertTrue(result is GenerateMessageUseCase.GenerationOutcome.Generated)
        assertEquals(ApprovalMode.ALWAYS_ASK, (result as GenerateMessageUseCase.GenerationOutcome.Generated).approvalMode)
        coVerify {
            notificationService.showApprovalNotification(
                request = match<ApprovalNotificationRequest> {
                    it.contactId.value == "c1" &&
                        it.eventId.value == "e1"
                },
                variants = variants,
            )
        }
        coVerify(exactly = 0) { schedulerService.scheduleExactSend(any()) }
    }

    @Test
    fun `invoke does not replace failed pending message by default`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns pendingMessage(
            id = "failed_1",
            status = "FAILED"
        )

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.AlreadyExists, result)
        coVerify(exactly = 0) { aiService.generateMessage(any()) }
        coVerify(exactly = 0) { messageRepository.insertPending(any()) }
    }

    @Test
    fun `invoke with worker retry request replaces failed occurrence`() = runTest {
        val event = occasion(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            relationshipType = "FRIEND",
            primaryPhone = "+15551234567",
            preferredChannel = MessageChannel.SMS.raw,
            automationMode = "FULLY_AUTO",
        )
        val draft = "Happy birthday John, hope the year ahead brings relaxed weekends, good coffee, and more time with friends."
        val variants = MessageVariantsResult(draft, draft, draft, draft, draft, draft, "standard")
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", any()) } returns pendingMessage(
            id = "failed_1",
            status = "FAILED"
        )
        coEvery { contactRepository.getById("c1") } returns contact
        coEvery { styleProfileRepository.getProfileOnce() } returns null
        coEvery { messageRepository.getGenerationHistoryByContact("c1", 10) } returns MessageGenerationHistory()
        coEvery { memoryNoteRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { giftHistoryRepository.getRecordsByContact("c1") } returns emptyList()
        coEvery { aiService.generateMessage(any()) } returns variants

        val result = useCase(
            GenerateMessageUseCase.Request(
                eventId = "e1",
                regenerateFailedOccurrence = true
            )
        )

        assertTrue(result is GenerateMessageUseCase.GenerationOutcome.Generated)
        assertEquals("failed_1", (result as GenerateMessageUseCase.GenerationOutcome.Generated).pendingId)
        coVerify { messageRepository.insertPending(capture(pendingSlot)) }
        assertEquals("failed_1", pendingSlot.captured.id)
        assertEquals("APPROVED", pendingSlot.captured.status)
        coVerify { schedulerService.scheduleExactSend("failed_1") }
    }

    @Test
    fun `invoke checks duplicate messages by scheduled occurrence year`() = runTest {
        val event = occasion(
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
        coEvery { eventRepository.getOccasionById("e1") } returns event
        coEvery { messageRepository.getPendingForEventOccurrence("c1", "e1", 2027) } returns pendingMessage(
            scheduledYear = 2027
        )

        val result = useCase("e1")

        assertEquals(GenerateMessageUseCase.GenerationOutcome.AlreadyExists, result)
    }

    private fun pendingMessage(
        id: String = "pending_1",
        contactId: String = "c1",
        eventId: String = "e1",
        scheduledYear: Int = 1970,
        status: String = "PENDING"
    ): PendingMessageEntity {
        return PendingMessageEntity(
            id = id,
            contactId = contactId,
            eventId = eventId,
            shortVariant = "short",
            standardVariant = "standard",
            longVariant = "long",
            formalVariant = "formal",
            funnyVariant = "funny",
            emotionalVariant = "emotional",
            selectedVariant = "standard",
            selectedVariantText = "standard",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = 0L,
            approvalMode = "SMART_APPROVE",
            status = status,
            scheduledYear = scheduledYear
        )
    }

    private fun occasion(
        id: String = "e1",
        contactId: String = "c1",
        type: String = OccasionType.BIRTHDAY.raw,
        label: String? = "Test",
        dayOfMonth: Int = 1,
        month: Int = 1,
        year: Int? = null,
        nextOccurrenceMs: Long = 1000L,
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
