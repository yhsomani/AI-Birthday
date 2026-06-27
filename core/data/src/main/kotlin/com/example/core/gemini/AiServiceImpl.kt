package com.example.core.gemini

import com.example.core.resilience.StructuredLogger
import com.example.domain.model.contact.ContactClassificationPromptContext
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.message.MessagePromptContext
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
        context: MessagePromptContext,
    ): MessageVariantsResult {
        StructuredLogger.i(TAG, "Generating message", mapOf(
            "contactId" to context.contactId.value,
            "eventId" to context.eventId.value,
            "previousMessages" to context.previousWishes.size.toString(),
        ))
        val prompter = PromptBuilder()

        RateLimiter.waitIfNeeded()
        val prompt = prompter.buildMessageGenerationPrompt(context)
        val response = geminiClient.generate(prompt)
        val variants = ResponseParser.parseMessageVariants(response, eventType = context.eventType)
        logParseFallbackIfNeeded("generate", context, variants)

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
        context: MessagePromptContext,
        feedbackInstruction: String?,
    ): MessageVariantsResult {
        StructuredLogger.i(TAG, "Regenerating message", mapOf(
            "contactId" to context.contactId.value,
            "eventId" to context.eventId.value,
        ))
        val prompter = PromptBuilder()

        RateLimiter.waitIfNeeded()
        val prompt = prompter.buildRegenerationPrompt(previousMessage, context, feedbackInstruction)
        val response = geminiClient.generate(prompt)
        val variants = ResponseParser.parseMessageVariants(response, eventType = context.eventType)
        logParseFallbackIfNeeded("regenerate", context, variants)

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

    override suspend fun classifyContact(contact: ContactClassificationPromptContext): ContactClassificationResult {
        StructuredLogger.i(TAG, "Classifying contact", mapOf(
            "contactId" to contact.id.value,
            "name" to contact.displayName,
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
        contact: ContactGiftAdvisorProfile,
        history: List<GiftHistoryRecord>
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

    private fun logParseFallbackIfNeeded(
        operation: String,
        context: MessagePromptContext,
        variants: MessageVariants
    ) {
        if (!variants.isUsingFallback) return

        StructuredLogger.w(
            TAG,
            "AI message response parsed with fallback",
            extras = mapOf(
                "operation" to operation,
                "eventId" to context.eventId.value,
                "eventType" to context.eventType,
                "fallbackReason" to variants.parseMetadata.fallbackReason.code,
            )
        )
    }

    companion object {
        private const val TAG = "AiServiceImpl"
    }
}
