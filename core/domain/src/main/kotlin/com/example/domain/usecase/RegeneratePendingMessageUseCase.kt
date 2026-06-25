package com.example.domain.usecase

import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.GiftHistoryRepository
import com.example.domain.repository.MemoryNoteRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.automation.AiAutoSendQualityGate
import com.example.domain.automation.ApprovalModeResolver
import com.example.domain.automation.AutoSendChannelSelector
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageStatus
import com.example.domain.service.AiService
import com.example.domain.service.PreferencesRepository
import com.example.domain.service.SchedulerService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegeneratePendingMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val styleProfileRepository: StyleProfileRepository,
    private val memoryNoteRepository: MemoryNoteRepository,
    private val giftHistoryRepository: GiftHistoryRepository,
    private val aiService: AiService,
    private val preferencesRepository: PreferencesRepository,
    private val schedulerService: SchedulerService,
) {
    suspend operator fun invoke(
        pendingMessageId: String,
        currentDraft: String,
        feedbackInstruction: String? = null,
        preserveUserEditedText: Boolean = false,
        preserveApprovedStatus: Boolean = false,
    ): Outcome {
        if (!preferencesRepository.isAiWishGenerationEnabled()) {
            return Outcome.AiDisabled
        }

        val pending = messageRepository.getPendingById(pendingMessageId)
            ?: return Outcome.PendingNotFound
        val contact = contactRepository.getById(pending.contactId)
            ?: return Outcome.ContextNotFound
        val event = eventRepository.getEventsBefore(Long.MAX_VALUE)
            .firstOrNull { it.id == pending.eventId }
            ?: return Outcome.ContextNotFound
        val styleProfile = styleProfileRepository.getProfileOnce()
        val previousMessages = messageRepository.getSentByContact(contact.id, 10)
        val memoryNotes = memoryNoteRepository.getByContact(contact.id)
        val giftHistory = giftHistoryRepository.getByContact(contact.id)

        val variants = aiService.regenerateMessage(
            previousMessage = currentDraft.ifBlank { pending.selectedVariantText },
            contact = contact,
            event = event,
            styleProfile = styleProfile,
            previousMessages = previousMessages,
            feedbackInstruction = feedbackInstruction,
            memoryNotes = memoryNotes,
            giftHistory = giftHistory,
        )
        val regeneratedText = variants.get(variants.recommended)
        val shouldPreserveUserEdit = preserveUserEditedText && currentDraft.isNotBlank()
        val selectedText = if (shouldPreserveUserEdit) currentDraft else regeneratedText
        val requestedApprovalMode = ApprovalModeResolver.resolve(
            relationship = contact.relationshipType,
            contactOverride = contact.automationMode,
            globalMode = preferencesRepository.getGlobalAutomationMode(),
            skipAutoWish = contact.skipAutoWish,
        )
        val qualityDecision = AiAutoSendQualityGate.evaluate(
            requestedMode = requestedApprovalMode,
            selectedMessage = selectedText,
            isUsingFallback = variants.isUsingFallback,
        )
        val channelSelection = AutoSendChannelSelector.selectRoute(
            contact = contact,
            previousMessages = previousMessages,
            channelBlackoutJson = preferencesRepository.getChannelBlackout(),
            senderEmail = preferencesRepository.getSenderEmail(),
            senderEmailPassword = preferencesRepository.getSenderEmailPassword(),
        )
        val approvalMode = if (channelSelection.hasAvailableRoute) {
            qualityDecision.approvalMode
        } else {
            ApprovalMode.ALWAYS_ASK
        }
        val status = regeneratedStatus(
            previousStatus = pending.status,
            approvalMode = approvalMode,
            hasAvailableRoute = channelSelection.hasAvailableRoute,
            preserveApprovedStatus = preserveApprovedStatus,
        )
        val updated = pending.copy(
            shortVariant = variants.short,
            standardVariant = variants.standard,
            longVariant = variants.long,
            formalVariant = variants.formal,
            funnyVariant = variants.funny,
            emotionalVariant = variants.emotional,
            selectedVariant = variants.recommended,
            selectedVariantText = selectedText,
            channel = channelSelection.channel,
            approvalMode = approvalMode.raw,
            status = status.raw,
            editedByUser = shouldPreserveUserEdit,
            userEditedText = if (shouldPreserveUserEdit) selectedText else null,
            qualityScore = qualityDecision.qualityScore,
            isUsingFallback = variants.isUsingFallback,
        )
        messageRepository.insertPending(updated)
        if (updated.status == MessageStatus.APPROVED.raw || ApprovalModeResolver.schedulesAutomaticDispatch(approvalMode)) {
            schedulerService.scheduleExactSend(updated.id)
        }

        return Outcome.Regenerated(updated.id, variants.isUsingFallback)
    }

    private fun regeneratedStatus(
        previousStatus: String,
        approvalMode: ApprovalMode,
        hasAvailableRoute: Boolean,
        preserveApprovedStatus: Boolean,
    ): MessageStatus {
        if (!hasAvailableRoute) return MessageStatus.PENDING
        if (preserveApprovedStatus && MessageStatus.fromRaw(previousStatus) == MessageStatus.APPROVED) {
            return MessageStatus.APPROVED
        }
        return if (approvalMode == ApprovalMode.FULLY_AUTO) {
            MessageStatus.APPROVED
        } else {
            MessageStatus.PENDING
        }
    }

    sealed class Outcome {
        data object PendingNotFound : Outcome()
        data object ContextNotFound : Outcome()
        data object AiDisabled : Outcome()
        data class Regenerated(val pendingId: String, val usedFallback: Boolean) : Outcome()
    }
}
