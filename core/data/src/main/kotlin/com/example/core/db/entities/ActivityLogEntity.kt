package com.example.core.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus

@Entity(
    tableName = "activity_logs",
    indices = [
        Index(value = ["createdAtMs"], name = "idx_activity_logs_createdAtMs"),
        Index(value = ["type"], name = "idx_activity_logs_type"),
        Index(value = ["contactId"], name = "idx_activity_logs_contactId"),
        Index(value = ["eventId"], name = "idx_activity_logs_eventId"),
        Index(value = ["messageId"], name = "idx_activity_logs_messageId"),
        Index(value = ["status"], name = "idx_activity_logs_status"),
        Index(value = ["severity"], name = "idx_activity_logs_severity"),
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
    @ColumnInfo(defaultValue = "'INFO'") val severity: String = ActivityLogSeverity.INFO.raw,
    @ColumnInfo(defaultValue = "'OPEN'") val status: String = ActivityLogStatus.OPEN.raw,
    val actionRoute: String? = null,
    @ColumnInfo(defaultValue = "'{}'") val metadataJson: String = "{}",
    val createdAtMs: Long = System.currentTimeMillis(),
)
