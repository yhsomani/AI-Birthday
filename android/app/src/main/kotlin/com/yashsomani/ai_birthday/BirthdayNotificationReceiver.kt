package com.yashsomani.ai_birthday

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class BirthdayNotificationReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "BirthdayNotification"
        const val CHANNEL_ID = "birthday_reminders"
        const val CHANNEL_NAME = "Birthday Reminders"
        const val CHANNEL_DESCRIPTION = "Alerts and reminders for upcoming birthdays"
        const val ACTION_BIRTHDAY_REMINDER = "com.yashsomani.ai_birthday.BIRTHDAY_REMINDER"
        const val EXTRA_ID = "notification_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_PERSON_ID = "person_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "onReceive: action=$action")

        // Never post notifications for system lifecycle broadcasts (boot / package update).
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Received system boot/update event. Ignoring direct notification delivery.")
            return
        }

        // Only deliver for intentional birthday reminder actions with valid payload
        if (action != null && action != ACTION_BIRTHDAY_REMINDER) {
            Log.w(TAG, "Ignoring unrecognized broadcast action: $action")
            return
        }

        val notificationId = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val personId = intent.getStringExtra(EXTRA_PERSON_ID)
        Log.i(TAG, "onReceive: notificationId=$notificationId title=$title")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PERSON_ID, personId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.i(TAG, "Posted notification successfully: id=$notificationId")
    }
}
