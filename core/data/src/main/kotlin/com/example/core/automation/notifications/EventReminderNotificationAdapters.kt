package com.example.core.automation.notifications

import android.content.Context
import com.example.core.data.R
import com.example.domain.model.notification.EventReminderNotificationRequest

internal fun Context.showEventReminderNotification(request: EventReminderNotificationRequest) {
    NotificationHelper.showEventReminderNotification(this, request)
}

internal fun EventReminderNotificationRequest.toEventReminderNotificationCopy(
    context: Context,
): EventReminderNotificationCopy {
    val eventLabel = eventType.lowercase().replace('_', ' ')
    return EventReminderNotificationCopy(
        title = context.getString(R.string.notification_event_reminder_title, contactDisplayName),
        message = context.getString(
            R.string.notification_event_reminder_text,
            contactDisplayName,
            eventLabel,
        ),
    )
}

internal data class EventReminderNotificationCopy(
    val title: String,
    val message: String,
)
