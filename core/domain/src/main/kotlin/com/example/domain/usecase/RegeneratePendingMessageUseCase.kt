package com.example.domain.usecase

import com.example.domain.repository.ContactRepository
import com.example.domain.repository.EventRepository
import com.example.domain.repository.MessageRepository
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.service.AiService
import com.example.domain.service.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegeneratePendingMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val eventRepository: EventRepository,
    private val styleProfileRepository: StyleProfileRepository,
    private val aiService: AiService,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend operator fun invoke(pendingMessageId: String, currentDraft: String): Outcome {
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

        val variants = aiService.regenerateMessage(
            previousMessage = currentDraft.ifBlank { pending.selectedVariantText },
            contact = contact,
            event = event,
            styleProfile = styleProfile,
            previousMessages = previousMessages,
        )
        val selectedText = variants.get(variants.recommended)
        val updated = pending.copy(
            shortVariant = variants.short,
            standardVariant = variants.standard,
            longVariant = variants.long,
            formalVariant = variants.formal,
            funnyVariant = variants.funny,
            emotionalVariant = variants.emotional,
            selectedVariant = variants.recommended,
            selectedVariantText = selectedText,
            editedByUser = false,
            userEditedText = null,
            isUsingFallback = variants.isUsingFallback,
        )
        messageRepository.insertPending(updated)

        return Outcome.Regenerated(updated.id, variants.isUsingFallback)
    }

    sealed class Outcome {
        data object PendingNotFound : Outcome()
        data object ContextNotFound : Outcome()
        data object AiDisabled : Outcome()
        data class Regenerated(val pendingId: String, val usedFallback: Boolean) : Outcome()
    }
}
