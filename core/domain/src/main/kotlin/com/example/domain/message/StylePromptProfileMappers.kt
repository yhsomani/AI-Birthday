package com.example.domain.message

import com.example.domain.model.message.StylePromptProfile
import com.example.domain.model.style.StyleProfileRecord

fun StyleProfileRecord.toStylePromptProfile(): StylePromptProfile {
    return StylePromptProfile(
        sampleMessagesJson = sampleMessagesJson,
        usesEmoji = usesEmoji,
        avgMessageLength = avgMessageLength,
        commonPhrasesJson = commonPhrasesJson,
    )
}
