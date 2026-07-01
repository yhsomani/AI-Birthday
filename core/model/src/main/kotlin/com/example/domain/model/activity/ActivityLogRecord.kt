package com.example.domain.model.activity

import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus

data class ActivityLogRecord(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val contactId: String? = null,
    val eventId: String? = null,
    val messageId: String? = null,
    val severity: String = ActivityLogSeverity.INFO.raw,
    val status: String = ActivityLogStatus.OPEN.raw,
    val actionRoute: String? = null,
    val metadataJson: String = "{}",
    val createdAtMs: Long = System.currentTimeMillis(),
)
