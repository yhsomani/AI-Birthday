package com.example.domain.message

import com.example.core.db.entities.StyleProfileEntity
import com.example.domain.model.message.StylePromptProfile

fun StyleProfileEntity.toStylePromptProfile(): StylePromptProfile {
    return StylePromptProfile(
        sampleMessagesJson = sampleMessagesJson,
        usesEmoji = usesEmoji,
        avgMessageLength = avgMessageLength,
        commonPhrasesJson = commonPhrasesJson,
    )
}
