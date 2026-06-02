package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "style_profile")
data class StyleProfileEntity(
    @PrimaryKey val id: Int = 1,
    val sampleMessagesJson: String = "[]",
    val usesEmoji: Boolean = true,
    val avgMessageLength: Int = 120,
    val commonPhrasesJson: String = "[]",
    val commonGreetingsJson: String = "[]",
    val formalityLevel: String = "CASUAL",
    val preferredLanguage: String = "en",
    val emojiSetJson: String = "[]",         // Most used emojis
    val avoidPhrasesJson: String = "[]",     // Phrases user dislikes
    val toneDescriptors: String = "[]",      // ["warm","direct","uses_yaar"]
    val sampleCount: Int = 0,                // Number of samples learned from
    val updatedAtMs: Long = System.currentTimeMillis()
)
