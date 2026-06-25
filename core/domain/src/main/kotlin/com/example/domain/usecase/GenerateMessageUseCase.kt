package com.example.domain.usecase

import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.automation.AiAutoSendQualityGate
import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.automation.ApprovalModeResolver
import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus
import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.AiService
import com.example.domain.service.NotificationService
import com.example.domain.service.PreferencesRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar
import java.util.UUID

/**
 * Generates a personalised birthday/event message for a contact using Gemini.
 * - Loads contact, event, style profile, and previous messages for context
 * - Calls Gemini with anti-repetition guard (up to 2 retries)
 * - Persists a PendingMessageEntity in APPROVED or PENDING state based on approval mode
 * - Schedules exact-time dispatch for fully automatic and smart-approve messages
 * - Posts a notification for user review if pending
 */
@Singleton
class GenerateMessageUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val messageRepository: MessageRepository,
    private val styleProfileRepository: StyleProfileRepository,
    private val memoryNoteRepository: MemoryNoteRepository,
    private val giftHistoryRepository: GiftHistoryRepository,
    private val aiService: AiService,
    private val preferencesRepository: PreferencesRepository,
    private val schedulerService: SchedulerService,
    private val notificationService: NotificationService
) {
    suspend operator fun invoke(eventId: String): GenerationOutcome {
        val event = eventRepository.getEventsBefore(Long.MAX_VALUE).firstOrNull { it.id == eventId }
            ?: return GenerationOutcome.EventNotFound

        val scheduledYear = scheduledYearFor(event.nextOccurrenceMs)
        if (messageRepository.pendingExistsForEventOccurrence(event.contactId, event.id, scheduledYear)) {
            return GenerationOutcome.AlreadyExists
        }

        val contact = contactRepository.getById(event.contactId) ?: return GenerationOutcome.ContactNotFound
        if (!preferencesRepository.isAiWishGenerationEnabled()) {
            return GenerationOutcome.AiDisabled
        }
        val styleProfile = styleProfileRepository.getProfileOnce()
        val previousMessages = messageRepository.getSentByContact(contact.id, 10)
        val memoryNotes = memoryNoteRepository.getByContact(contact.id)
        val giftHistory = giftHistoryRepository.getByContact(contact.id)

        var variants = aiService.generateMessage(
            contact = contact,
            event = event,
            styleProfile = styleProfile,
            previousMessages = previousMessages,
            memoryNotes = memoryNotes,
            giftHistory = giftHistory,
        )

        var retries = 0
        while (retries < 2 && isPreviouslyUsed(variants.standard, previousMessages)) {
            variants = aiService.regenerateMessage(
                previousMessage = variants.standard,
                contact = contact,
                event = event,
                styleProfile = styleProfile,
                previousMessages = previousMessages,
                memoryNotes = memoryNotes,
                giftHistory = giftHistory,
            )
            retries++
        }

        val globalMode = preferencesRepository.getGlobalAutomationMode()
        val requestedApprovalMode = ApprovalModeResolver.resolve(
            relationship = contact.relationshipType,
            contactOverride = contact.automationMode,
            globalMode = globalMode,
            skipAutoWish = contact.skipAutoWish,
        )
        val selectedVariantText = variants.get(variants.recommended)
        val qualityDecision = AiAutoSendQualityGate.evaluate(
            requestedMode = requestedApprovalMode,
            selectedMessage = selectedVariantText,
            isUsingFallback = variants.isUsingFallback,
        )
        val approvalMode = qualityDecision.approvalMode
        val scheduledForMs = AutomationSchedulePolicy.messageSendTimeMs(
            eventOccurrenceMs = event.nextOccurrenceMs,
            customHour = contact.customSendTimeHour,
            customMinute = contact.customSendTimeMinute,
            quietHoursStart = preferencesRepository.getQuietHoursStart(),
            quietHoursEnd = preferencesRepository.getQuietHoursEnd(),
            blackoutDatesJson = preferencesRepository.getBlackoutDates(),
        )
        val selectedChannel = AutoSendChannelSelector.select(
            contact = contact,
            previousMessages = previousMessages,
            channelBlackoutJson = preferencesRepository.getChannelBlackout(),
            senderEmail = preferencesRepository.getSenderEmail(),
            senderEmailPassword = preferencesRepository.getSenderEmailPassword(),
        )

        val pending = PendingMessageEntity(
            id = UUID.randomUUID().toString(),
            contactId = contact.id,
            eventId = event.id,
            shortVariant = variants.short,
            standardVariant = variants.standard,
            longVariant = variants.long,
            formalVariant = variants.formal,
            funnyVariant = variants.funny,
            emotionalVariant = variants.emotional,
            selectedVariant = variants.recommended,
            selectedVariantText = selectedVariantText,
            channel = selectedChannel,
            scheduledForMs = scheduledForMs,
            approvalMode = approvalMode.raw,
            status = if (approvalMode == ApprovalMode.FULLY_AUTO) MessageStatus.APPROVED.raw else MessageStatus.PENDING.raw,
            qualityScore = qualityDecision.qualityScore,
            scheduledYear = scheduledYear,
            isUsingFallback = variants.isUsingFallback
        )
        messageRepository.insertPending(pending)

        if (ApprovalModeResolver.schedulesAutomaticDispatch(approvalMode)) {
            schedulerService.scheduleExactSend(pending.id)
        }
        if (ApprovalModeResolver.needsReviewNotification(approvalMode)) {
            notificationService.showApprovalNotification(contact, event, variants, pending.id)
        }

        return GenerationOutcome.Generated(pending.id, approvalMode.raw, retries)
    }

    private fun isPreviouslyUsed(newMessage: String, previous: List<SentMessageEntity>): Boolean {
        val newWords = newMessage.lowercase().split(" ").toSet()
        return previous.any { sent ->
            val oldWords = sent.messageText.lowercase().split(" ").toSet()
            val intersection = newWords.intersect(oldWords).size
            val union = newWords.union(oldWords).size
            if (union == 0) false else (intersection.toFloat() / union) > 0.65f
        }
    }

    private fun scheduledYearFor(timestampMs: Long): Int {
        return Calendar.getInstance().apply { timeInMillis = timestampMs }.get(Calendar.YEAR)
    }

    sealed class GenerationOutcome {
        data object EventNotFound : GenerationOutcome()
        data object ContactNotFound : GenerationOutcome()
        data object AlreadyExists : GenerationOutcome()
        data object AiDisabled : GenerationOutcome()
        data class Generated(val pendingId: String, val approvalMode: String, val retries: Int) : GenerationOutcome()
    }
}
