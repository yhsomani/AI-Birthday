package com.example.core.automation.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.core.automation.workers.MessageDispatchWorkRequests
import com.example.core.data.R
import com.example.core.db.AppDatabase
import com.example.core.prefs.SecurePrefs
import com.example.domain.automation.AutomationSchedulePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DailyScheduler {
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleExactSend(context: Context, pendingMessageId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val db = AppDatabase.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            val pending = db.pendingMessageDao().getById(pendingMessageId)
            if (pending != null) {
                val prefs = SecurePrefs(context)
                val scheduledForMs = AutomationSchedulePolicy.nextAllowedSendMs(
                    candidateMs = pending.scheduledForMs,
                    quietHoursStart = prefs.getQuietHoursStart(),
                    quietHoursEnd = prefs.getQuietHoursEnd(),
                    blackoutDatesJson = prefs.getBlackoutDates(),
                )
                if (scheduledForMs != pending.scheduledForMs) {
                    db.pendingMessageDao().insert(pending.copy(scheduledForMs = scheduledForMs))
                }
                val pendingIntent = buildDispatchPendingIntent(
                    context = context,
                    requestCode = pending.id.hashCode(),
                    pendingMessageId = pending.id,
                    eventId = pending.eventId,
                    flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                if (scheduledForMs <= System.currentTimeMillis()) {
                    androidx.work.WorkManager.getInstance(context)
                        .enqueue(MessageDispatchWorkRequests.create(pending.id, pending.eventId))
                    return@launch
                }

                if (canScheduleExactAlarms(alarmManager)) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(scheduledForMs, pendingIntent),
                        pendingIntent
                    )
                } else {
                    com.example.core.automation.notifications.NotificationHelper.showSetupNotification(
                        context,
                        context.getString(R.string.notification_setup_exact_alarm_title),
                        context.getString(R.string.notification_setup_exact_alarm_message),
                    )
                    androidx.work.WorkManager.getInstance(context)
                        .enqueue(MessageDispatchWorkRequests.create(pending.id, pending.eventId))
                }
            }
        }
    }

    fun cancelExactSend(context: Context, pendingMessageId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        cancelPendingIntent(context, alarmManager, pendingMessageId.hashCode(), pendingMessageId, null)
    }

    fun cancelLegacyExactSend(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        cancelPendingIntent(context, alarmManager, eventId.hashCode(), null, eventId)
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun buildDispatchPendingIntent(
        context: Context,
        requestCode: Int,
        pendingMessageId: String?,
        eventId: String?,
        flags: Int,
    ): PendingIntent {
        val intent = Intent(context, MessageDispatchReceiver::class.java).apply {
            pendingMessageId?.let { putExtra(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID, it) }
            eventId?.let { putExtra(MessageDispatchWorkRequests.KEY_EVENT_ID, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flags,
        )
    }

    private fun cancelPendingIntent(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        pendingMessageId: String?,
        eventId: String?,
    ) {
        val pendingIntent = buildDispatchPendingIntent(
            context = context,
            requestCode = requestCode,
            pendingMessageId = pendingMessageId,
            eventId = eventId,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}

class MessageDispatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingMessageId = intent.getStringExtra(MessageDispatchWorkRequests.KEY_PENDING_MESSAGE_ID)
        val eventId = intent.getStringExtra(MessageDispatchWorkRequests.KEY_EVENT_ID)
        if (pendingMessageId.isNullOrBlank() && eventId.isNullOrBlank()) return
        
        val workManager = androidx.work.WorkManager.getInstance(context)
        if (!pendingMessageId.isNullOrBlank()) {
            workManager.enqueue(MessageDispatchWorkRequests.create(pendingMessageId, eventId))
        } else if (!eventId.isNullOrBlank()) {
            workManager.enqueue(MessageDispatchWorkRequests.createForEvent(eventId))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val db = AppDatabase.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Reschedule approved alarms
                    val pending = db.pendingMessageDao().getAllApproved()
                    pending.forEach { msg ->
                        DailyScheduler.scheduleExactSend(context, msg.id)
                    }
                    EventReminderScheduler.scheduleAll(context)
                    
                    // 2. Reschedule periodic workers conditionally
                    val workManager = androidx.work.WorkManager.getInstance(context)

                    val hasDailyTrigger = workManager.getWorkInfosByTag("daily_trigger").get().any {
                        it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING
                    }
                    val hasRevival = workManager.getWorkInfosByTag("revival").get().any {
                        it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING
                    }
                    val hasStyle = workManager.getWorkInfosByTag("style_analysis").get().any {
                        it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING
                    }

                    val constraints = androidx.work.Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()

                    if (!hasDailyTrigger) {
                        val request = androidx.work.PeriodicWorkRequestBuilder<com.example.core.automation.workers.DailyTriggerWorker>(24, java.util.concurrent.TimeUnit.HOURS)
                            .setConstraints(constraints)
                            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                            .addTag("daily_trigger")
                            .build()
                        workManager.enqueueUniquePeriodicWork("daily_trigger", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
                    }

                    if (!hasRevival) {
                        val request = androidx.work.PeriodicWorkRequestBuilder<com.example.core.automation.workers.RevivalWorker>(7, java.util.concurrent.TimeUnit.DAYS)
                            .setConstraints(constraints)
                            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                            .addTag("revival")
                            .build()
                        workManager.enqueueUniquePeriodicWork("revival_check", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
                    }

                    if (!hasStyle) {
                        val styleConstraints = androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .setRequiresStorageNotLow(true)
                            .build()
                        val request = androidx.work.PeriodicWorkRequestBuilder<com.example.core.automation.workers.StyleAnalysisWorker>(14, java.util.concurrent.TimeUnit.DAYS)
                            .setConstraints(styleConstraints)
                            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                            .addTag("style_analysis")
                            .build()
                        workManager.enqueueUniquePeriodicWork("style_analysis", androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
