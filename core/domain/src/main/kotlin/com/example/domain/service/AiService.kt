package com.example.domain.service

import com.example.domain.model.contact.ContactClassificationPromptContext
import com.example.domain.model.contact.ContactGiftAdvisorProfile
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.message.MessagePromptContext

interface AiService {
    suspend fun generateMessage(
        context: MessagePromptContext,
    ): MessageVariantsResult

    suspend fun regenerateMessage(
        previousMessage: String,
        context: MessagePromptContext,
        feedbackInstruction: String? = null,
    ): MessageVariantsResult

    suspend fun classifyContact(contact: ContactClassificationPromptContext): ContactClassificationResult

    suspend fun generateGiftSuggestions(
        contact: ContactGiftAdvisorProfile,
        history: List<GiftHistoryRecord>
    ): List<GiftSuggestion>
}

data class GiftSuggestion(
    val name: String,
    val reason: String,
    val estimatedCostInr: Int
)

data class MessageVariantsResult(
    val short: String,
    val standard: String,
    val long: String,
    val formal: String,
    val funny: String,
    val emotional: String,
    val recommended: String,
    val isUsingFallback: Boolean = false
) {
    fun get(variantName: String): String {
        return when (variantName.lowercase()) {
            "short" -> short
            "long" -> long
            "funny" -> funny
            "formal" -> formal
            "emotional" -> emotional
            else -> standard
        }
    }
}

data class ContactClassificationResult(
    val type: String,
    val subtype: String?,
    val language: String,
    val formality: String,
    val communicationStyle: String,
    val confidence: Double
)
