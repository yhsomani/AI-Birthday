package com.example.core.automation.notifications

import android.content.Context
import com.example.core.data.R
import com.example.domain.model.notification.SystemAlertNotificationReason
import com.example.domain.model.notification.SystemAlertNotificationRequest

internal fun Context.showSystemAlert(request: SystemAlertNotificationRequest) {
    NotificationHelper.showSystemAlert(this, request)
}

internal fun SystemAlertNotificationRequest.toSystemAlertNotificationCopy(
    context: Context,
): SystemAlertNotificationCopy {
    return when (reason) {
        SystemAlertNotificationReason.AI_FALLBACK_USED -> SystemAlertNotificationCopy(
            title = context.getString(R.string.notification_ai_fallback_title),
            message = context.getString(R.string.notification_ai_fallback_message),
        )
        SystemAlertNotificationReason.BACKUP_STALE -> SystemAlertNotificationCopy(
            title = context.getString(R.string.notification_backup_reminder_title),
            message = context.getString(R.string.notification_backup_reminder_message),
        )
    }
}

internal data class SystemAlertNotificationCopy(
    val title: String,
    val message: String,
)
