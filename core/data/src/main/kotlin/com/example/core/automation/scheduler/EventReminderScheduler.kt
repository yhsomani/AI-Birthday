package com.example.core.automation.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.core.automation.notifications.EventReminderReceiver
import com.example.core.db.AppDatabase
import com.example.core.prefs.SecurePrefs
import com.example.domain.automation.AutomationSchedulePolicy
import com.example.domain.event.toOccasion
import com.example.domain.model.notification.EventReminderScheduleRequest
import com.example.domain.notification.buildEventReminderScheduleRequest
import com.example.domain.service.EventReminderSchedulerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object EventReminderScheduler {
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_CONTACT_ID = "contact_id"

    fun schedule(context: Context, request: EventReminderScheduleRequest) {
        val prefs = SecurePrefs(context)
        val eventId = request.eventId.value
        if (!request.isActive || !prefs.isBirthdayRemindersEnabled()) {
            cancel(context, eventId)
            return
        }

        val nowMs = System.currentTimeMillis()
        if (request.nextOccurrenceMs < nowMs) {
            cancel(context, eventId)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAtMs = AutomationSchedulePolicy.reminderTimeMs(
            eventOccurrenceMs = request.nextOccurrenceMs,
            notifyDaysBefore = request.notifyDaysBefore,
            nowMs = nowMs,
        )
        val pendingIntent = buildPendingIntent(
            context = context,
            eventId = eventId,
            contactId = request.contactId.value,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        if (canScheduleExactAlarms(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pendingIntent,
            )
        }
    }

    fun cancel(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = buildPendingIntent(
            context = context,
            eventId = eventId,
            contactId = null,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun scheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            db.eventDao().getAllSync()
                .map { it.toOccasion() }
                .filter { it.isActive }
                .forEach { occasion -> schedule(context, buildEventReminderScheduleRequest(occasion)) }
        }
    }

    private fun buildPendingIntent(
        context: Context,
        eventId: String,
        contactId: String?,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
            contactId?.let { putExtra(EXTRA_CONTACT_ID, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            flags,
        )
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }
}

@Singleton
class EventReminderSchedulerServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : EventReminderSchedulerService {
    override fun scheduleReminder(request: EventReminderScheduleRequest) {
        EventReminderScheduler.schedule(context, request)
    }

    override fun cancelReminder(eventId: String) {
        EventReminderScheduler.cancel(context, eventId)
    }

    override fun rescheduleAll() {
        EventReminderScheduler.scheduleAll(context)
    }
}
