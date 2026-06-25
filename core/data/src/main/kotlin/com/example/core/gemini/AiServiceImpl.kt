package com.example.core.gemini

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.resilience.StructuredLogger
import com.example.domain.service.AiService
import com.example.domain.service.ContactClassificationResult
import com.example.domain.service.MessageVariantsResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiServiceImpl @Inject constructor(
    private val geminiClient: GeminiClient
) : AiService {

    override suspend fun generateMessage(
        contact: ContactEntity,
        event: EventEntity,
        styleProfile: StyleProfileEntity?,
        previousMessages: List<SentMessageEntity>,
        memoryNotes: List<MemoryNoteEntity>,
        giftHistory: List<GiftHistoryEntity>,
    ): MessageVariantsResult {
        StructuredLogger.i(TAG, "Generating message", mapOf(
            "contactId" to contact.id,
            "eventId" to event.id,
            "previousMessages" to previousMessages.size.toString(),
        ))
        val prompter = PromptBuilder()
        val contextObj = prompter.buildContactContext(
            contact = contact,
            event = event,
            styleProfile = styleProfile,
            previousMessages = previousMessages,
            memoryNotes = memoryNotes,
            giftHistory = giftHistory,
        )

        RateLimiter.waitIfNeeded()
        val prompt = prompter.buildMessageGenerationPrompt(contextObj)
        val response = geminiClient.generate(prompt)
        val variants = ResponseParser.parseMessageVariants(response, eventType = event.type)

        StructuredLogger.d(TAG, "Message generated", mapOf(
            "recommended" to variants.recommended.take(50),
        ))
        return MessageVariantsResult(
            short = variants.short,
            standard = variants.standard,
            long = variants.long,
            formal = variants.formal,
            funny = variants.funny,
            emotional = variants.emotional,
            recommended = variants.recommended,
            isUsingFallback = variants.isUsingFallback
        )
    }

    override suspend fun regenerateMessage(
        previousMessage: String,
        contact: ContactEntity,
        event: EventEntity,
        styleProfile: StyleProfileEntity?,
        previousMessages: List<SentMessageEntity>,
        feedbackInstruction: String?,
        memoryNotes: List<MemoryNoteEntity>,
        giftHistory: List<GiftHistoryEntity>,
    ): MessageVariantsResult {
        StructuredLogger.i(TAG, "Regenerating message", mapOf(
            "contactId" to contact.id,
            "eventId" to event.id,
        ))
        val prompter = PromptBuilder()
        val contextObj = prompter.buildContactContext(
            contact = contact,
            event = event,
            styleProfile = styleProfile,
            previousMessages = previousMessages,
            memoryNotes = memoryNotes,
            giftHistory = giftHistory,
        )

        RateLimiter.waitIfNeeded()
        val prompt = prompter.buildRegenerationPrompt(previousMessage, contextObj, feedbackInstruction)
        val response = geminiClient.generate(prompt)
        val variants = ResponseParser.parseMessageVariants(response, eventType = event.type)

        return MessageVariantsResult(
            short = variants.short,
            standard = variants.standard,
            long = variants.long,
            formal = variants.formal,
            funny = variants.funny,
            emotional = variants.emotional,
            recommended = variants.recommended,
            isUsingFallback = variants.isUsingFallback
        )
    }

    override suspend fun classifyContact(contact: ContactEntity): ContactClassificationResult {
        StructuredLogger.i(TAG, "Classifying contact", mapOf(
            "contactId" to contact.id,
            "name" to contact.name,
        ))
        val prompter = PromptBuilder()
        val prompt = prompter.buildClassificationPrompt(contact)

        RateLimiter.waitIfNeeded()
        val response = geminiClient.generate(prompt)
        val result = ResponseParser.parseContactClassification(response)

        return ContactClassificationResult(
            type = result.type,
            subtype = result.subtype,
            language = result.language,
            formality = result.formality,
            communicationStyle = result.communicationStyle,
            confidence = result.confidence
        )
    }

    override suspend fun generateGiftSuggestions(
        contact: ContactEntity,
        history: List<GiftHistoryEntity>
    ): List<com.example.domain.service.GiftSuggestion> {
        val prompter = PromptBuilder()
        val prompt = prompter.buildGiftSuggestionsPrompt(contact, history)
        RateLimiter.waitIfNeeded()
        val response = geminiClient.generate(prompt)
        val raw = ResponseParser.parseGiftSuggestions(response)
        val budget = (contact.giftBudgetInr.takeIf { it > 0 } ?: 500)
        val filtered = raw.filter { it.estimatedCostInr in 1..budget }
        return if (filtered.isNotEmpty()) filtered else raw
    }

    companion object {
        private const val TAG = "AiServiceImpl"
    }
}
