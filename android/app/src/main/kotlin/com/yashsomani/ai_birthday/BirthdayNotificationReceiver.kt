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
import org.json.JSONArray

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
        const val PREFS_NAME = "scheduled_reminders_prefs"
        const val KEY_SCHEDULED_ALARM_IDS = "scheduled_alarm_ids"
        const val KEY_SCHEDULED_TRIGGERS_JSON = "scheduled_triggers_json"

        fun reschedulePersisted(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                ?: return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SCHEDULED_TRIGGERS_JSON, null) ?: return
            val triggers = try {
                JSONArray(raw)
            } catch (e: Exception) {
                Log.e(TAG, "Persisted reminder payload is invalid; clearing it.", e)
                prefs.edit().remove(KEY_SCHEDULED_TRIGGERS_JSON).remove(KEY_SCHEDULED_ALARM_IDS).apply()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission unavailable after reboot; reminders remain unscheduled.")
                return
            }

            val now = System.currentTimeMillis()
            val restoredIds = mutableSetOf<String>()

            for (index in 0 until triggers.length()) {
                val item = triggers.optJSONObject(index) ?: continue
                val id = item.optInt(EXTRA_ID, -1)
                val timestampMs = item.optLong("timestampMs", 0L)
                if (id < 0 || timestampMs <= now) continue

                val reminderIntent = Intent(context, BirthdayNotificationReceiver::class.java).apply {
                    action = ACTION_BIRTHDAY_REMINDER
                    putExtra(EXTRA_ID, id)
                    putExtra(EXTRA_TITLE, item.optString(EXTRA_TITLE, "Birthday Reminder"))
                    putExtra(EXTRA_BODY, item.optString(EXTRA_BODY, ""))
                    if (item.has(EXTRA_PERSON_ID)) {
                        putExtra(EXTRA_PERSON_ID, item.optString(EXTRA_PERSON_ID))
                    }
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    id,
                    reminderIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            timestampMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.set(
                            android.app.AlarmManager.RTC_WAKEUP,
                            timestampMs,
                            pendingIntent
                        )
                    }
                    restoredIds.add(id.toString())
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to restore reminder id=$id after reboot.", e)
                }
            }

            prefs.edit().putStringSet(KEY_SCHEDULED_ALARM_IDS, restoredIds).apply()
            Log.i(TAG, "Restored ${restoredIds.size} scheduled reminders after system restart.")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "onReceive: action=$action")

        // AlarmManager entries are cleared by Android across reboot/package replacement.
        // Restore the persisted future reminder payloads before returning.
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            reschedulePersisted(context)
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
