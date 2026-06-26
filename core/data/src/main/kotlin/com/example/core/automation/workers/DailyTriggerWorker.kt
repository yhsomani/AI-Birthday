package com.example.core.automation.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.automation.scheduler.WorkerScheduler
import com.example.core.data.R
import com.example.core.prefs.SecurePrefs
import com.example.domain.service.EventReminderSchedulerService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DailyTriggerWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val prefs: SecurePrefs,
    private val eventReminderSchedulerService: EventReminderSchedulerService,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val lastBackupMs = prefs.getLastBackupMs()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val lastReminderMs = prefs.getLastBackupReminderMs()
        val backupReferenceMs = lastBackupMs.takeIf { it > 0L } ?: lastReminderMs

        if (lastBackupMs == 0L && lastReminderMs == 0L) {
            prefs.setLastBackupReminderMs(now)
        } else if (
            backupReferenceMs > 0L &&
            now - backupReferenceMs > thirtyDaysMs &&
            (lastReminderMs == 0L || now - lastReminderMs > thirtyDaysMs)
        ) {
            NotificationHelper.showSystemAlert(
                applicationContext,
                applicationContext.getString(R.string.notification_backup_reminder_title),
                applicationContext.getString(R.string.notification_backup_reminder_message),
            )
            prefs.setLastBackupReminderMs(now)
        }

        WorkerScheduler.scheduleDailyAutomationChain(applicationContext)
        eventReminderSchedulerService.rescheduleAll()

        return Result.success()
    }
}
