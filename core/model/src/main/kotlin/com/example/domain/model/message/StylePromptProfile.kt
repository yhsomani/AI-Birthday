package com.example.domain.model.message

data class StylePromptProfile(
    val sampleMessagesJson: String = "[]",
    val usesEmoji: Boolean = true,
    val avgMessageLength: Int = 120,
    val commonPhrasesJson: String = "[]",
)
