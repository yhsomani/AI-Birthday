package com.example.core.automation.scheduler

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.core.automation.workers.ContactSyncWorker
import com.example.core.automation.workers.EventDiscoveryWorker
import com.example.core.automation.workers.MessageGenerationWorker
import com.example.core.automation.workers.RevivalWorker
import com.example.core.automation.workers.StyleAnalysisWorker
import com.example.core.prefs.SecurePrefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    private const val TAG = "WorkerScheduler"

    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        // 1. Daily Trigger Worker (every 24 hours)
        val dailyTrigger = PeriodicWorkRequestBuilder<com.example.core.automation.workers.DailyTriggerWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("daily_trigger")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "daily_trigger",
            ExistingPeriodicWorkPolicy.KEEP,
            dailyTrigger
        )

        // 2. Revival Worker (every 7 days)
        val revival = PeriodicWorkRequestBuilder<com.example.core.automation.workers.RevivalWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("revival")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "revival_check",
            ExistingPeriodicWorkPolicy.KEEP,
            revival
        )

        // 3. Style Analysis Worker (every 14 days)
        val styleConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val styleAnalysis = PeriodicWorkRequestBuilder<com.example.core.automation.workers.StyleAnalysisWorker>(14, TimeUnit.DAYS)
            .setConstraints(styleConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("style_analysis")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "style_analysis",
            ExistingPeriodicWorkPolicy.KEEP,
            styleAnalysis
        )

        EventReminderScheduler.scheduleAll(context)
    }

    fun scheduleDailyAutomationChain(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val localConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val contactSyncRequest = OneTimeWorkRequestBuilder<com.example.core.automation.workers.ContactSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("initial_sync")
            .build()

        val eventDiscoveryRequest = OneTimeWorkRequestBuilder<com.example.core.automation.workers.EventDiscoveryWorker>()
            .setConstraints(localConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val messageGenRequest = OneTimeWorkRequestBuilder<com.example.core.automation.workers.MessageGenerationWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val holidayWishRequest = OneTimeWorkRequestBuilder<com.example.core.automation.workers.HolidayWishWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("holiday_wishes")
            .build()

        val followUpRequest = OneTimeWorkRequestBuilder<com.example.core.automation.workers.PostEventFollowUpWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("post_event_follow_up")
            .build()

        workManager.beginUniqueWork(
            "daily_automation_chain",
            ExistingWorkPolicy.KEEP,
            contactSyncRequest
        ).then(eventDiscoveryRequest)
         .then(messageGenRequest)
         .then(holidayWishRequest)
         .then(followUpRequest)
         .enqueue()
    }

    private fun calculateDelayUntilMidnight(): Long {
        return calculateDelayUntil(0, 0)
    }

    private fun calculateDelayUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
