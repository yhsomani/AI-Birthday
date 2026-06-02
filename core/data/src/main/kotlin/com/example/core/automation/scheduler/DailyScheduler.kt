package com.example.automation.scheduler

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
        CoroutineScope(Dispatchers.IO).launch {
            val pending = db.pendingMessageDao().getByEventId(eventId)
            if (pending != null) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(pending.scheduledForMs, pendingIntent),
                    pendingIntent
                )
            }
        }
    }
}

class MessageDispatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("event_id") ?: return
        
        val workManager = androidx.work.WorkManager.getInstance(context)
        val data = androidx.work.Data.Builder().putString("event_id", eventId).build()
        val request = androidx.work.OneTimeWorkRequestBuilder<com.example.automation.workers.MessageDispatchWorker>()
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
                    
                    // 2. Reschedule periodic workers
                    com.example.automation.scheduler.WorkerScheduler.scheduleAll(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
