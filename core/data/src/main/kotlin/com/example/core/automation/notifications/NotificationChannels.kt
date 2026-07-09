package com.example.core.automation.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.core.data.R

internal fun createRelateNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationChannelDefinitions(context).forEach { definition ->
        if (manager.getNotificationChannel(definition.id) == null) {
            manager.createNotificationChannel(definition.toChannel())
        }
    }
}

private fun notificationChannelDefinitions(context: Context): List<RelateNotificationChannelDefinition> {
    return listOf(
        RelateNotificationChannelDefinition(
            id = NotificationHelper.APPROVAL,
            name = context.getString(R.string.notification_channel_approval_name),
            description = context.getString(R.string.notification_channel_approval_description),
            importance = NotificationManager.IMPORTANCE_HIGH,
            vibrates = true,
        ),
        RelateNotificationChannelDefinition(
            id = NotificationHelper.REVIVAL,
            name = context.getString(R.string.notification_channel_revival_name),
            description = context.getString(R.string.notification_channel_revival_description),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        ),
        RelateNotificationChannelDefinition(
            id = NotificationHelper.EVENT_REMINDERS,
            name = context.getString(R.string.notification_channel_event_reminders_name),
            description = context.getString(R.string.notification_channel_event_reminders_description),
            importance = NotificationManager.IMPORTANCE_HIGH,
            vibrates = true,
        ),
        RelateNotificationChannelDefinition(
            id = NotificationHelper.SYSTEM,
            name = context.getString(R.string.notification_channel_system_name),
            description = context.getString(R.string.notification_channel_system_description),
            importance = NotificationManager.IMPORTANCE_HIGH,
        ),
        RelateNotificationChannelDefinition(
            id = NotificationHelper.DISPATCH_STATUS,
            name = context.getString(R.string.notification_channel_dispatch_status_name),
            description = context.getString(R.string.notification_channel_dispatch_status_description),
            importance = NotificationManager.IMPORTANCE_LOW,
            vibrates = false,
            soundEnabled = false,
        ),
    )
}

private data class RelateNotificationChannelDefinition(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
    val vibrates: Boolean = false,
    val soundEnabled: Boolean = true,
) {
    fun toChannel(): NotificationChannel {
        return NotificationChannel(id, name, importance).apply {
            description = this@RelateNotificationChannelDefinition.description
            enableVibration(vibrates)
            if (!soundEnabled) {
                setSound(null, null)
            }
        }
    }
}
