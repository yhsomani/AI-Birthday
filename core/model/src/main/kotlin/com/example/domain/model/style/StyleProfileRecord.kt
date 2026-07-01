package com.example.domain.model.style

data class StyleProfileRecord(
    val id: Int = 1,
    val sampleMessagesJson: String = "[]",
    val usesEmoji: Boolean = true,
    val avgMessageLength: Int = 120,
    val commonPhrasesJson: String = "[]",
    val commonGreetingsJson: String = "[]",
    val formalityLevel: String = "CASUAL",
    val preferredLanguage: String = "en",
    val emojiSetJson: String = "[]",
    val avoidPhrasesJson: String = "[]",
    val toneDescriptors: String = "[]",
    val sampleCount: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

data class StyleProfileHistoryRecord(
    val id: Int = 0,
    val profileJson: String,
    val savedAtMs: Long,
    val source: String = "MANUAL_TRAINING",
)
