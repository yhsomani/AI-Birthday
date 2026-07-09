package com.example.domain.message

import com.example.domain.model.MessageChannel
import com.example.domain.memory.MemoryNotePromptPolicy
import com.example.domain.model.contact.ContactMessagePromptContext
import com.example.domain.model.common.JsonTextCodec
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.domain.model.message.MessagePromptContext
import com.example.domain.model.message.PromptTextFormatter
import com.example.domain.model.message.StylePromptProfile
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionType

fun buildMessagePromptContext(
    contact: ContactMessagePromptContext,
    event: Occasion,
    styleProfile: StylePromptProfile?,
    previousWishes: List<String>,
    memoryNotes: List<MemoryNoteRecord> = emptyList(),
    giftHistory: List<GiftHistoryRecord> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
): MessagePromptContext {
    val lastInteraction = contact.lastInteractionAtMs
    val daysSince = if (lastInteraction != null) {
        ((nowMs - lastInteraction) / (1000 * 60 * 60 * 24)).toInt()
    } else {
        0
    }

    val birthdayYear = contact.birthdayYear
    val eventYear = event.date.year
    val ageTurning = if (event.type == OccasionType.BIRTHDAY && birthdayYear != null && eventYear != null) {
        eventYear - birthdayYear
    } else {
        null
    }

    return MessagePromptContext(
        contactId = contact.id,
        eventId = event.id,
        firstName = PromptTextFormatter.firstName(contact.displayName),
        nickname = contact.nickname,
        relationshipType = contact.relationshipType,
        knownSince = null,
        ageTurning = ageTurning,
        interests = parseJsonArray(contact.interestsJson),
        sharedHistory = parseJsonArray(contact.sharedHistoryJson),
        daysSinceLastContact = daysSince,
        eventType = event.type.raw,
        eventOccurrenceNumber = ageTurning,
        preferredLanguage = contact.preferredLanguage,
        userStyleSamples = parseJsonArray(styleProfile?.sampleMessagesJson ?: "[]"),
        usesEmoji = styleProfile?.usesEmoji ?: true,
        avgMessageLength = styleProfile?.avgMessageLength ?: 120,
        commonPhrases = parseJsonArray(styleProfile?.commonPhrasesJson ?: "[]"),
        previousWishes = previousWishes,
        formalityLevel = contact.formalityLevel,
        memoryNotes = memoryNotes
            .filter(MemoryNotePromptPolicy::canUseInAiPrompts)
            .sortedWith(compareByDescending<MemoryNoteRecord> { it.isPinned }.thenByDescending { it.dateMs })
            .take(6)
            .map { "${it.category}: ${PromptTextFormatter.sanitizeNotes(it.noteText).take(180)}" },
        giftHistory = giftHistory
            .sortedByDescending { it.year }
            .take(5)
            .map { "${it.year}: ${it.giftName} (${it.giftCategory}, liked: ${it.receivedWell ?: "unknown"})" },
        sensitiveTopics = parseJsonArray(contact.sensitiveTopicsJson),
        currentLifePhase = parseLifePhase(contact.currentLifePhaseJson),
        preferredChannel = MessageChannel.fromRaw(contact.preferredChannel).orDefault(),
    )
}

private fun parseJsonArray(raw: String): List<String> {
    return JsonTextCodec.parseStringArray(raw)
}

private fun parseLifePhase(raw: String): String? {
    return JsonTextCodec.readStringField(raw, "phase")
}
