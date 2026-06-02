package com.example.domain.usecase

import android.content.Context
import com.example.automation.notifications.NotificationHelper
import com.example.automation.scheduler.DailyScheduler
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.dao.StyleProfileDao
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.PromptBuilder
import com.example.core.gemini.RateLimiter
import com.example.core.gemini.ResponseParser
import com.example.core.prefs.SecurePrefs
import com.example.domain.repository.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * Generates a personalised birthday/event message for a contact using Gemini.
 * - Loads contact, event, style profile, and previous messages for context
 * - Calls Gemini with anti-repetition guard (up to 2 retries)
 * - Persists a PendingMessageEntity in APPROVED or PENDING state based on approval mode
 * - Schedules an exact-time dispatch if approved
 * - Posts a notification for user review if pending
 */
@Singleton
class GenerateMessageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val eventDao: EventDao,
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao,
    private val styleProfileDao: StyleProfileDao,
    private val gemini: GeminiClient,
    private val contactRepository: ContactRepository,
    private val prefs: SecurePrefs
) {
    suspend operator fun invoke(eventId: String): GenerationOutcome {
        val event = eventDao.getEventsBefore(Long.MAX_VALUE).firstOrNull { it.id == eventId }
            ?: return GenerationOutcome.EventNotFound

        if (pendingMessageDao.existsForEvent(eventId)) {
            return GenerationOutcome.AlreadyExists
        }

        val contact = contactDao.getById(event.contactId) ?: return GenerationOutcome.ContactNotFound
        val styleProfile = styleProfileDao.get()
        val previousMessages = sentMessageDao.getByContact(contact.id)

        val prompter = PromptBuilder()
        val contextObj = prompter.buildContactContext(contact, event, styleProfile, previousMessages)

        RateLimiter.waitIfNeeded()
        var prompt = prompter.buildMessageGenerationPrompt(contextObj)
        var response = gemini.generate(prompt)
        var variants = ResponseParser.parseMessageVariants(response)

        var retries = 0
        while (retries < 2 && isPreviouslyUsed(contact.id, variants.standard)) {
            RateLimiter.waitIfNeeded()
            prompt = prompter.buildRegenerationPrompt(variants.standard, contextObj)
            response = gemini.generate(prompt)
            variants = ResponseParser.parseMessageVariants(response)
            retries++
        }

        val globalMode = prefs.getGlobalAutomationMode()
        val approvalMode = determineApprovalMode(contact.relationshipType, contact.automationMode, globalMode)
        val selectedVariantText = variants.get(variants.recommended)

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
            channel = contact.preferredChannel,
            scheduledForMs = event.nextOccurrenceMs,
            approvalMode = approvalMode,
            status = if (approvalMode == "FULLY_AUTO") "APPROVED" else "PENDING"
        )
        pendingMessageDao.insert(pending)

        if (approvalMode == "FULLY_AUTO") {
            DailyScheduler.scheduleExactSend(context, event.id)
        } else {
            NotificationHelper.showApprovalNotification(context, contact, event, variants)
        }

        return GenerationOutcome.Generated(pending.id, approvalMode, retries)
    }

    private fun determineApprovalMode(relationship: String, contactOverride: String, globalMode: String): String {
        if (contactOverride != "DEFAULT" && contactOverride.isNotEmpty()) return contactOverride
        return when (relationship) {
            "FAMILY", "BEST_FRIEND" -> "VIP_APPROVE"
            "CLOSE_FRIEND", "RELATIVE" -> if (globalMode == "ALWAYS_ASK") "ALWAYS_ASK" else "SMART_APPROVE"
            else -> globalMode
        }
    }

    private suspend fun isPreviouslyUsed(contactId: String, newMessage: String): Boolean {
        val previous = sentMessageDao.getByContact(contactId)
        val newWords = newMessage.lowercase().split(" ").toSet()
        return previous.any { sent ->
            val oldWords = sent.messageText.lowercase().split(" ").toSet()
            val intersection = newWords.intersect(oldWords).size
            val union = newWords.union(oldWords).size
            if (union == 0) false else (intersection.toFloat() / union) > 0.65f
        }
    }

    sealed class GenerationOutcome {
        data object EventNotFound : GenerationOutcome()
        data object ContactNotFound : GenerationOutcome()
        data object AlreadyExists : GenerationOutcome()
        data class Generated(val pendingId: String, val approvalMode: String, val retries: Int) : GenerationOutcome()
    }
}
