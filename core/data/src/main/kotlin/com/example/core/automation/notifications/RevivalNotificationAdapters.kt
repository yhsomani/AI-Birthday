package com.example.core.automation.notifications

import android.content.Context
import com.example.core.data.R
import com.example.domain.model.notification.RevivalNotificationRequest

internal fun Context.showRevivalNotification(request: RevivalNotificationRequest) {
    NotificationHelper.showRevivalNotification(this, request)
}

internal fun RevivalNotificationRequest.toRevivalNotificationCopy(
    context: Context,
): RevivalNotificationCopy {
    return RevivalNotificationCopy(
        title = context.getString(R.string.notification_revival_title, contactDisplayName),
        message = context.getString(R.string.notification_revival_text, daysSinceContact),
        bigText = context.getString(R.string.notification_revival_big_text, suggestionText),
    )
}

internal data class RevivalNotificationCopy(
    val title: String,
    val message: String,
    val bigText: String,
)
