package com.example.core.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_logs",
    indices = [
        Index(value = ["createdAtMs"], name = "idx_activity_logs_createdAtMs"),
        Index(value = ["type"], name = "idx_activity_logs_type"),
        Index(value = ["contactId"], name = "idx_activity_logs_contactId"),
        Index(value = ["eventId"], name = "idx_activity_logs_eventId"),
        Index(value = ["messageId"], name = "idx_activity_logs_messageId"),
    ],
)
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val contactId: String? = null,
    val eventId: String? = null,
    val messageId: String? = null,
    val severity: String = "INFO",
    val status: String = "OPEN",
    val actionRoute: String? = null,
    val metadataJson: String = "{}",
    val createdAtMs: Long = System.currentTimeMillis(),
)
