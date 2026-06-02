package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "mood_logs",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MoodLogEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val mood: String, // HAPPY, STRESSED, BUSY, NEUTRAL, TENSE
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "MANUAL" // MANUAL, AI_INFERRED
)
