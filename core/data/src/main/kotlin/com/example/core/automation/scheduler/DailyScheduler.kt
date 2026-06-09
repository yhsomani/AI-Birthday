package com.example.core.automation.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DailyScheduler {
    fun scheduleExactSend(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, MessageDispatchReceiver::class.java).apply {
            putExtra("event_id", eventId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val db = AppDatabase.getInstance(context)
        // Fire and forget, but ideally this should be managed by a WorkManager or injected scope.
        // We'll use GlobalScope for fire and forget broadcast scheduling (as context might die).
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val pending = db.pendingMessageDao().getByEventId(eventId)
            if (pending != null) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(pending.scheduledForMs, pendingIntent),
                    pendingIntent
                )
            }
        }
    }

    fun cancelExactSend(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, MessageDispatchReceiver::class.java).apply {
            putExtra("event_id", eventId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

class MessageDispatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("event_id") ?: return
        
        val workManager = androidx.work.WorkManager.getInstance(context)
        val data = androidx.work.Data.Builder().putString("event_id", eventId).build()
        val request = androidx.work.OneTimeWorkRequestBuilder<com.example.core.automation.workers.MessageDispatchWorker>()
            .setInputData(data)
            .build()
            
        workManager.enqueue(request)
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
                        DailyScheduler.scheduleExactSend(context, msg.eventId)
                    }
                    
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
