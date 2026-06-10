package com.example.core.automation.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.core.data.R
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.gemini.MessageVariants

object NotificationHelper {
    const val APPROVAL = "approval_required"
    const val REVIVAL = "revival_suggestion"
    const val EVENT_REMINDERS = "event_reminders"
    const val SYSTEM = "system_alerts"
    const val DISPATCH_STATUS = "dispatch_status"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 1. Approval Required (High Importance, vibration+sound)
            if (manager.getNotificationChannel(APPROVAL) == null) {
                val channel = NotificationChannel(
                    APPROVAL,
                    context.getString(R.string.notification_channel_approval_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_approval_description)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
            
            // 2. Revival Suggestions (Default Importance)
            if (manager.getNotificationChannel(REVIVAL) == null) {
                val channel = NotificationChannel(
                    REVIVAL,
                    context.getString(R.string.notification_channel_revival_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_revival_description)
                }
                manager.createNotificationChannel(channel)
            }

            // 3. Event Reminders (High Importance)
            if (manager.getNotificationChannel(EVENT_REMINDERS) == null) {
                val channel = NotificationChannel(
                    EVENT_REMINDERS,
                    context.getString(R.string.notification_channel_event_reminders_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_event_reminders_description)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }

            // 4. System Alerts (High Importance)
            if (manager.getNotificationChannel(SYSTEM) == null) {
                val channel = NotificationChannel(
                    SYSTEM,
                    context.getString(R.string.notification_channel_system_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_system_description)
                }
                manager.createNotificationChannel(channel)
            }

            // 5. Dispatch Status (Low Importance, no sound)
            if (manager.getNotificationChannel(DISPATCH_STATUS) == null) {
                val channel = NotificationChannel(
                    DISPATCH_STATUS,
                    context.getString(R.string.notification_channel_dispatch_status_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_dispatch_status_description)
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun showApprovalNotification(
        context: Context,
        contact: ContactEntity,
        event: EventEntity,
        variants: MessageVariants,
        messageId: String
    ) {
        val approveIntent = PendingIntent.getBroadcast(
            context, event.id.hashCode() + 1,
            Intent(context, ApprovalReceiver::class.java).apply {
                action = "ACTION_APPROVE"
                putExtra("action", "ACTION_APPROVE")
                putExtra("event_id", event.id)
                putExtra("message_id", messageId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rejectIntent = PendingIntent.getBroadcast(
            context, event.id.hashCode() + 2,
            Intent(context, ApprovalReceiver::class.java).apply {
                action = "ACTION_REJECT"
                putExtra("action", "ACTION_REJECT")
                putExtra("event_id", event.id)
                putExtra("message_id", messageId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val editIntent = PendingIntent.getActivity(
            context, event.id.hashCode() + 3,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("relateai://wish/${contact.id}/$messageId")).apply {
                setClassName(context, "com.example.MainActivity")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, APPROVAL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.notification_approval_title, contact.name))
            .setContentText(variants.standard)
            .setStyle(NotificationCompat.BigTextStyle().bigText(variants.standard))
            .addAction(android.R.drawable.ic_input_add, context.getString(R.string.notification_action_approve), approveIntent)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.notification_action_reject), rejectIntent)
            .addAction(android.R.drawable.ic_menu_edit, context.getString(R.string.notification_action_edit), editIntent)
            .setContentIntent(editIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(messageId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission missing
        }
    }

    @SuppressLint("MissingPermission")
    fun showRevivalNotification(
        context: Context,
        contactName: String,
        daysSinceContact: Int,
        suggestionText: String,
        contactId: String
    ) {
        val appIntent = PendingIntent.getActivity(
            context, 100 + contactId.hashCode(),
            Intent().setClassName(context, "com.example.MainActivity"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, REVIVAL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.notification_revival_title, contactName))
            .setContentText(context.getString(R.string.notification_revival_text, daysSinceContact))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_revival_big_text, suggestionText)))
            .setContentIntent(appIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1000 + contactId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission missing
        }
    }

    @SuppressLint("MissingPermission")
    fun showSetupNotification(context: Context, title: String, message: String) {
        val appIntent = PendingIntent.getActivity(
            context, 999,
            Intent().setClassName(context, "com.example.MainActivity"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(appIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(999, notification)
        } catch (e: SecurityException) {
            // Permission missing
        }
    }

    @SuppressLint("MissingPermission")
    fun showEventReminderNotification(
        context: Context,
        contact: ContactEntity,
        event: EventEntity
    ) {
        val appIntent = PendingIntent.getActivity(
            context, event.id.hashCode() + 10,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("relateai://contact/${contact.id}")).apply {
                setClassName(context, "com.example.MainActivity")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, EVENT_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_event_today_title, contact.name))
            .setContentText(
                context.getString(
                    R.string.notification_event_today_text,
                    contact.name,
                    event.type.lowercase().replace('_', ' '),
                )
            )
            .setContentIntent(appIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(event.id.hashCode() + 10, notification)
        } catch (e: SecurityException) {
            // Permission missing
        }
    }

    @SuppressLint("MissingPermission")
    fun showSystemAlert(context: Context, title: String, message: String) {
        val appIntent = PendingIntent.getActivity(
            context, 888,
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("relateai://backup-restore")).apply {
                setClassName(context, "com.example.MainActivity")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(appIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(888, notification)
        } catch (e: SecurityException) {
            // Permission missing
        }
    }
}
