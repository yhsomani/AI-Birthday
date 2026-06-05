package com.example.domain.service

import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity

interface AiService {
    suspend fun generateMessage(
        contact: ContactEntity,
        event: EventEntity,
        styleProfile: StyleProfileEntity?,
        previousMessages: List<SentMessageEntity>
    ): MessageVariantsResult

    suspend fun regenerateMessage(
        previousMessage: String,
        contact: ContactEntity,
        event: EventEntity,
        styleProfile: StyleProfileEntity?,
        previousMessages: List<SentMessageEntity>
    ): MessageVariantsResult

    suspend fun classifyContact(contact: ContactEntity): ContactClassificationResult
}

data class MessageVariantsResult(
    val short: String,
    val standard: String,
    val long: String,
    val formal: String,
    val funny: String,
    val emotional: String,
    val recommended: String
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
