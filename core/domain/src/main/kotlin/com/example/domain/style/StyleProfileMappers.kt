package com.example.domain.style

import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.domain.model.style.StyleProfileHistoryRecord
import com.example.domain.model.style.StyleProfileRecord

fun StyleProfileEntity.toRecord(): StyleProfileRecord {
    return StyleProfileRecord(
        id = id,
        sampleMessagesJson = sampleMessagesJson,
        usesEmoji = usesEmoji,
        avgMessageLength = avgMessageLength,
        commonPhrasesJson = commonPhrasesJson,
        commonGreetingsJson = commonGreetingsJson,
        formalityLevel = formalityLevel,
        preferredLanguage = preferredLanguage,
        emojiSetJson = emojiSetJson,
        avoidPhrasesJson = avoidPhrasesJson,
        toneDescriptors = toneDescriptors,
        sampleCount = sampleCount,
        updatedAtMs = updatedAtMs,
    )
}

fun StyleProfileRecord.toEntity(): StyleProfileEntity {
    return StyleProfileEntity(
        id = id,
        sampleMessagesJson = sampleMessagesJson,
        usesEmoji = usesEmoji,
        avgMessageLength = avgMessageLength,
        commonPhrasesJson = commonPhrasesJson,
        commonGreetingsJson = commonGreetingsJson,
        formalityLevel = formalityLevel,
        preferredLanguage = preferredLanguage,
        emojiSetJson = emojiSetJson,
        avoidPhrasesJson = avoidPhrasesJson,
        toneDescriptors = toneDescriptors,
        sampleCount = sampleCount,
        updatedAtMs = updatedAtMs,
    )
}

fun StyleProfileHistoryEntity.toRecord(): StyleProfileHistoryRecord {
    return StyleProfileHistoryRecord(
        id = id,
        profileJson = profileJson,
        savedAtMs = savedAtMs,
        source = source,
    )
}

fun StyleProfileHistoryRecord.toEntity(): StyleProfileHistoryEntity {
    return StyleProfileHistoryEntity(
        id = id,
        profileJson = profileJson,
        savedAtMs = savedAtMs,
        source = source,
    )
}
