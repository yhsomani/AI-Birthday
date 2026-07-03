package com.example.core.automation.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.core.data.R
import com.example.core.gemini.MessageVariants
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipActionReadinessPolicy
import com.example.domain.readiness.RelationshipReadinessAction
import com.example.domain.model.notification.ApprovalNotificationRequest
import com.example.domain.model.notification.EventReminderNotificationRequest
import com.example.domain.model.notification.RevivalNotificationRequest
import com.example.domain.model.notification.SetupNotificationReason
import com.example.domain.model.notification.SetupNotificationRequest
import com.example.domain.model.notification.SystemAlertNotificationRequest
import com.example.domain.navigation.RelateDeepLinks

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
        request: ApprovalNotificationRequest,
        variants: MessageVariants,
    ) {
        val contactId = request.contactId.value
        val eventId = request.eventId.value
        val messageId = request.messageId.value
        val copy = request.toApprovalNotificationCopy(context, variants)
        val approveIntent = PendingIntent.getBroadcast(
            context, eventId.hashCode() + 1,
            Intent(context, ApprovalReceiver::class.java).apply {
                action = "ACTION_APPROVE"
                putExtra("action", "ACTION_APPROVE")
                putExtra("event_id", eventId)
                putExtra("message_id", messageId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rejectIntent = PendingIntent.getBroadcast(
            context, eventId.hashCode() + 2,
            Intent(context, ApprovalReceiver::class.java).apply {
                action = "ACTION_REJECT"
                putExtra("action", "ACTION_REJECT")
                putExtra("event_id", eventId)
                putExtra("message_id", messageId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val editIntent = PendingIntent.getActivity(
            context, eventId.hashCode() + 3,
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(RelateDeepLinks.Wish.uri(contactId, messageId)),
            ).apply {
                setClassName(context, "com.example.MainActivity")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, APPROVAL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(copy.title)
            .setContentText(copy.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.message))
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
        request: RevivalNotificationRequest,
    ) {
        val contactId = request.contactId.value
        val copy = request.toRevivalNotificationCopy(context)
        val appIntent = PendingIntent.getActivity(
            context, 100 + contactId.hashCode(),
            Intent().setClassName(context, "com.example.MainActivity"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, REVIVAL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(copy.title)
            .setContentText(copy.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.bigText))
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
    fun showSetupNotification(
        context: Context,
        request: SetupNotificationRequest,
    ) {
        val copy = request.toSetupNotificationCopy(context)
        val contentUri = request.toSetupNotificationContentUri()
        val appIntent = PendingIntent.getActivity(
            context, 999,
            Intent(Intent.ACTION_VIEW, Uri.parse(contentUri)).apply {
                setClassName(context, "com.example.MainActivity")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(copy.title)
            .setContentText(copy.message)
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
        request: EventReminderNotificationRequest,
    ) {
        val contactId = request.contactId.value
        val eventId = request.eventId.value
        val copy = request.toEventReminderNotificationCopy(context)
        val appIntent = PendingIntent.getActivity(
            context, eventId.hashCode() + 10,
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(RelateDeepLinks.Contact.uri(contactId)),
            ).apply {
                setClassName(context, "com.example.MainActivity")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, EVENT_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(copy.title)
            .setContentText(copy.message)
            .setContentIntent(appIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(eventId.hashCode() + 10, notification)
        } catch (e: SecurityException) {
            // Permission missing
        }
    }

    @SuppressLint("MissingPermission")
    fun showSystemAlert(
        context: Context,
        request: SystemAlertNotificationRequest,
    ) {
        val copy = request.toSystemAlertNotificationCopy(context)
        val contentUri = request.toSystemAlertNotificationContentUri()
        val appIntent = PendingIntent.getActivity(
            context, 888,
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(contentUri),
            ).apply {
                setClassName(context, "com.example.MainActivity")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, SYSTEM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(copy.title)
            .setContentText(copy.message)
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

internal fun SetupNotificationRequest.toSetupNotificationContentUri(): String {
    val readiness = RelationshipActionReadinessPolicy.fromSetupNotificationRequest(this)
    return readiness.toNotificationContentUri()
}

internal fun SystemAlertNotificationRequest.toSystemAlertNotificationContentUri(): String {
    val readiness = RelationshipActionReadinessPolicy.fromSystemAlertNotificationRequest(this)
    return readiness.toNotificationContentUri()
}

private fun RelationshipActionReadiness.toNotificationContentUri(): String {
    return when (primaryAction) {
        RelationshipReadinessAction.REVIEW_MESSAGE,
        RelationshipReadinessAction.EDIT_DRAFT -> {
            val contactId = relatedContactId
            val messageId = relatedMessageId
            if (contactId != null && messageId != null) {
                RelateDeepLinks.Wish.uri(contactId, messageId)
            } else {
                RelateDeepLinks.Messages.uri
            }
        }
        RelationshipReadinessAction.REVIEW_MESSAGES -> RelateDeepLinks.Messages.uri
        RelationshipReadinessAction.OPEN_CONTACT -> relatedContactId?.let(RelateDeepLinks.Contact::uri)
            ?: RelateDeepLinks.Contacts.uri
        RelationshipReadinessAction.CONFIGURE_CHANNEL,
        RelationshipReadinessAction.CONFIGURE_EMAIL,
        RelationshipReadinessAction.OPEN_SETUP,
        RelationshipReadinessAction.CHECK_SETUP,
        RelationshipReadinessAction.CONNECT_AI,
        RelationshipReadinessAction.ENABLE_AI_GENERATION -> RelateDeepLinks.AutomationSetup.uri
        RelationshipReadinessAction.FIX_CONTACT_SYNC,
        RelationshipReadinessAction.SYNC_CONTACTS -> RelateDeepLinks.Contacts.uri
        RelationshipReadinessAction.CREATE_BACKUP,
        RelationshipReadinessAction.REFRESH_BACKUP -> RelateDeepLinks.BackupRestore.uri
        RelationshipReadinessAction.NONE,
        RelationshipReadinessAction.WAIT -> RelateDeepLinks.Home.uri
    }
}

internal fun SetupNotificationRequest.toSetupNotificationCopy(context: Context): SetupNotificationCopy {
    val displayName = contactDisplayName.orEmpty()
    return when (reason) {
        SetupNotificationReason.SMS_PERMISSION_MISSING -> SetupNotificationCopy(
            title = context.getString(R.string.notification_setup_sms_permission_title),
            message = context.getString(R.string.notification_setup_sms_permission_message, displayName),
        )
        SetupNotificationReason.MESSAGE_EXPIRED -> SetupNotificationCopy(
            title = context.getString(R.string.notification_setup_message_expired_title),
            message = context.getString(R.string.notification_setup_message_expired_message, displayName),
        )
        SetupNotificationReason.DOUBLE_SEND_GUARD -> SetupNotificationCopy(
            title = context.getString(R.string.notification_setup_double_send_title),
            message = context.getString(R.string.notification_setup_double_send_message, displayName),
        )
        SetupNotificationReason.AI_PROVIDER_MISSING -> SetupNotificationCopy(
            title = context.getString(R.string.notification_setup_ai_title),
            message = context.getString(R.string.notification_setup_ai_message),
        )
        SetupNotificationReason.REVIVAL_AI_PROVIDER_MISSING -> SetupNotificationCopy(
            title = context.getString(R.string.notification_setup_ai_title),
            message = context.getString(R.string.notification_setup_revival_ai_message),
        )
        SetupNotificationReason.EXACT_ALARM_PERMISSION_MISSING -> SetupNotificationCopy(
            title = context.getString(R.string.notification_setup_exact_alarm_title),
            message = context.getString(R.string.notification_setup_exact_alarm_message),
        )
    }
}

internal data class SetupNotificationCopy(
    val title: String,
    val message: String,
)
