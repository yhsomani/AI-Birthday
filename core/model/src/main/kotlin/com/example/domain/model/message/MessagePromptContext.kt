package com.example.domain.model.message

import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId

data class MessagePromptContext(
    val contactId: ContactId,
    val eventId: OccasionId,
    val firstName: String,
    val nickname: String?,
    val relationshipType: String,
    val knownSince: String?,
    val ageTurning: Int?,
    val interests: List<String>,
    val sharedHistory: List<String>,
    val daysSinceLastContact: Int,
    val eventType: String,
    val eventOccurrenceNumber: Int?,
    val preferredLanguage: String,
    val userStyleSamples: List<String>,
    val usesEmoji: Boolean,
    val avgMessageLength: Int,
    val commonPhrases: List<String>,
    val previousWishes: List<String>,
    val formalityLevel: String,
    val memoryNotes: List<String> = emptyList(),
    val giftHistory: List<String> = emptyList(),
    val sensitiveTopics: List<String> = emptyList(),
    val currentLifePhase: String? = null,
    val preferredChannel: MessageChannel = MessageChannel.SMS,
)
