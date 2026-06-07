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
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.gemini.MessageVariants

object NotificationHelper {
    const val APPROVAL = "approval_required"
    const val REVIVAL = "revival_suggestion"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(APPROVAL) == null) {
                val approvalChannel = NotificationChannel(
                    APPROVAL,
                    "Approval Required",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for when your approval is needed to send a message."
                }
                manager.createNotificationChannel(approvalChannel)
            }
            if (manager.getNotificationChannel(REVIVAL) == null) {
                val revivalChannel = NotificationChannel(
                    REVIVAL,
                    "Revival Suggestions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Suggestions to reconnect with contacts you haven't spoken to in a while."
                }
                manager.createNotificationChannel(revivalChannel)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun showApprovalNotification(
        context: Context,
        contact: ContactEntity,
        event: EventEntity,
        variants: MessageVariants
    ) {
        val approveIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ApprovalReceiver::class.java).apply {
                putExtra("action", "APPROVE")
                putExtra("event_id", event.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val skipIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, ApprovalReceiver::class.java).apply {
                putExtra("action", "SKIP")
                putExtra("event_id", event.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val appIntent = PendingIntent.getActivity(
            context, 2,
            Intent().setClassName(context, "com.example.MainActivity"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, APPROVAL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("\uD83C\uDF82 ${contact.name}'s event tomorrow")
            .setContentText(variants.standard)
            .setStyle(NotificationCompat.BigTextStyle().bigText(variants.standard))
            .addAction(android.R.drawable.ic_input_add, "Send ✓", approveIntent)
            .addAction(android.R.drawable.ic_delete, "Skip", skipIntent)
            .setContentIntent(appIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
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
            .setContentTitle("Reconnect with $contactName")
            .setContentText("You haven't spoken in ${daysSinceContact} days")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Suggested message:\n\n$suggestionText\n\nYou can edit and approve this in the Messages screen."))
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
        val notification = NotificationCompat.Builder(context, APPROVAL)
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
}
