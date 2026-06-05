package com.example.core.gemini

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
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
        previousMessages: List<SentMessageEntity>
    ): MessageVariantsResult {
        val prompter = PromptBuilder()
        val contextObj = prompter.buildContactContext(contact, event, styleProfile, previousMessages)

        RateLimiter.waitIfNeeded()
        val prompt = prompter.buildMessageGenerationPrompt(contextObj)
        val response = geminiClient.generate(prompt)
        val variants = ResponseParser.parseMessageVariants(response)

        return MessageVariantsResult(
            short = variants.short,
            standard = variants.standard,
            long = variants.long,
            formal = variants.formal,
            funny = variants.funny,
            emotional = variants.emotional,
            recommended = variants.recommended
        )
    }

    override suspend fun regenerateMessage(
        previousMessage: String,
        contact: ContactEntity,
        event: EventEntity,
        styleProfile: StyleProfileEntity?,
        previousMessages: List<SentMessageEntity>
    ): MessageVariantsResult {
        val prompter = PromptBuilder()
        val contextObj = prompter.buildContactContext(contact, event, styleProfile, previousMessages)

        RateLimiter.waitIfNeeded()
        val prompt = prompter.buildRegenerationPrompt(previousMessage, contextObj)
        val response = geminiClient.generate(prompt)
        val variants = ResponseParser.parseMessageVariants(response)

        return MessageVariantsResult(
            short = variants.short,
            standard = variants.standard,
            long = variants.long,
            formal = variants.formal,
            funny = variants.funny,
            emotional = variants.emotional,
            recommended = variants.recommended
        )
    }

    override suspend fun classifyContact(contact: ContactEntity): ContactClassificationResult {
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
}
