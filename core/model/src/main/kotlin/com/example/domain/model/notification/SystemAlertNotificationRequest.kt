package com.example.domain.model.notification

enum class SystemAlertNotificationReason {
    AI_FALLBACK_USED,
    BACKUP_STALE,
}

data class SystemAlertNotificationRequest(
    val reason: SystemAlertNotificationReason,
)
