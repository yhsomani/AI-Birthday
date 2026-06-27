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
import com.example.domain.model.message.ExactSendCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DailyScheduler {
    fun scheduleExactSendCommand(context: Context, command: ExactSendCommand) {
        scheduleExactSend(context, command.messageId.value)
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleExactSend(context: Context, pendingMessageId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val db = AppDatabase.getInstance(context)
        CoroutineScope(Dispatchers.IO).launch {
            val pendingMessageDao = db.pendingMessageDao()
            val scheduleState = pendingMessageDao.getExactSendScheduleState(pendingMessageId)
            if (scheduleState != null) {
                val prefs = SecurePrefs(context)
                val scheduledForMs = AutomationSchedulePolicy.nextAllowedSendMs(
                    candidateMs = scheduleState.scheduledForMs,
                    quietHoursStart = prefs.getQuietHoursStart(),
                    quietHoursEnd = prefs.getQuietHoursEnd(),
                    blackoutDatesJson = prefs.getBlackoutDates(),
                )
                if (scheduledForMs != scheduleState.scheduledForMs) {
                    pendingMessageDao.saveExactSendScheduleUpdate(scheduleState.scheduleUpdate(scheduledForMs))
                }
                val pendingIntent = buildDispatchPendingIntent(
                    context = context,
                    requestCode = scheduleState.messageId.value.hashCode(),
                    pendingMessageId = scheduleState.messageId.value,
                    eventId = scheduleState.occasionId.value,
                    flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val nowMs = System.currentTimeMillis()
                if (scheduledForMs <= nowMs) {
                    androidx.work.WorkManager.getInstance(context)
                        .enqueue(
                            MessageDispatchWorkRequests.create(
                                pendingMessageId = scheduleState.messageId.value,
                                eventId = scheduleState.occasionId.value,
                            )
                        )
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
                        .enqueue(
                            MessageDispatchWorkRequests.create(
                                pendingMessageId = scheduleState.messageId.value,
                                eventId = scheduleState.occasionId.value,
                                initialDelayMs = scheduledForMs - nowMs,
                            )
                        )
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
        val command = intent.toMessageDispatchReceiverWorkCommand() ?: return
        androidx.work.WorkManager.getInstance(context).enqueueMessageDispatchReceiverWork(command)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val db = AppDatabase.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Reschedule alarms that can send without more user action.
                    val exactSendCommands = db.pendingMessageDao().getBootRecoverableExactSendCommands()
                    exactSendCommands.forEach { command ->
                        DailyScheduler.scheduleExactSendCommand(context, command)
                    }
                    EventReminderScheduler.scheduleAll(context)
                    
                    // 2. Reschedule periodic workers conditionally
                    val workManager = androidx.work.WorkManager.getInstance(context)
                    bootRecoveryRecurringWorkCommands().forEach { command ->
                        workManager.reconcileBootRecoveryRecurringWork(command)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
