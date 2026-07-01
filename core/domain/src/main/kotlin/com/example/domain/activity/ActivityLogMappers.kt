package com.example.domain.activity

import com.example.core.db.entities.ActivityLogEntity
import com.example.domain.model.activity.ActivityLogRecord

fun ActivityLogEntity.toRecord(): ActivityLogRecord {
    return ActivityLogRecord(
        id = id,
        type = type,
        title = title,
        detail = detail,
        contactId = contactId,
        eventId = eventId,
        messageId = messageId,
        severity = severity,
        status = status,
        actionRoute = actionRoute,
        metadataJson = metadataJson,
        createdAtMs = createdAtMs,
    )
}

fun ActivityLogRecord.toEntity(): ActivityLogEntity {
    return ActivityLogEntity(
        id = id,
        type = type,
        title = title,
        detail = detail,
        contactId = contactId,
        eventId = eventId,
        messageId = messageId,
        severity = severity,
        status = status,
        actionRoute = actionRoute,
        metadataJson = metadataJson,
        createdAtMs = createdAtMs,
    )
}
