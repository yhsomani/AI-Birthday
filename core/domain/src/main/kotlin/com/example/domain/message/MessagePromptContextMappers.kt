package com.example.domain.message

import com.example.domain.model.MessageChannel
import com.example.domain.model.contact.ContactMessagePromptContext
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.domain.model.message.MessagePromptContext
import com.example.domain.model.message.StylePromptProfile
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionType
import org.json.JSONArray
import org.json.JSONObject

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
        firstName = firstName(contact.displayName),
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
            .sortedWith(compareByDescending<MemoryNoteRecord> { it.isPinned }.thenByDescending { it.dateMs })
            .take(6)
            .map { "${it.category}: ${sanitizeNotes(it.noteText).take(180)}" },
        giftHistory = giftHistory
            .sortedByDescending { it.year }
            .take(5)
            .map { "${it.year}: ${it.giftName} (${it.giftCategory}, liked: ${it.receivedWell ?: "unknown"})" },
        sensitiveTopics = parseJsonArray(contact.sensitiveTopicsJson),
        currentLifePhase = parseLifePhase(contact.currentLifePhaseJson),
        preferredChannel = contact.preferredChannel.toSupportedMessageChannel(),
    )
}

private fun firstName(fullName: String): String {
    val trimmed = fullName.trim()
    val spaceIdx = trimmed.indexOf(' ')
    return if (spaceIdx == -1) trimmed else trimmed.substring(0, spaceIdx)
}

private fun parseJsonArray(raw: String): List<String> {
    return try {
        val arr = JSONArray(raw)
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun parseLifePhase(raw: String): String? {
    return try {
        JSONObject(raw).optString("phase").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}

private fun sanitizeNotes(notes: String): String {
    return notes
        .replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[EMAIL]")
        .replace(Regex("\\+?\\d{1,4}?[-.\\s]?\\(?\\d{1,3}?\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}"), "[PHONE]")
}

private fun String.toSupportedMessageChannel(): MessageChannel {
    return MessageChannel.fromRaw(this)
        .takeIf { it != MessageChannel.UNKNOWN }
        ?: MessageChannel.SMS
}
