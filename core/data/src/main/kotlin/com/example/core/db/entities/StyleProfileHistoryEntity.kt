package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "style_profile_history")
data class StyleProfileHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileJson: String,
    val savedAtMs: Long,
    val source: String = "MANUAL_TRAINING"
)
