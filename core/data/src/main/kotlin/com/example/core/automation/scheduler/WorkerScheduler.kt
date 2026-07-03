package com.example.core.automation.scheduler

import android.content.Context
import androidx.work.*
import com.example.core.automation.sender.SmsDeliveryStatusRecovery
import com.example.core.automation.workers.ContactSyncWorker
import com.example.core.automation.workers.EventDiscoveryWorker
import com.example.core.automation.workers.HolidayWishWorker
import com.example.core.automation.workers.MessageGenerationWorker
import com.example.core.automation.workers.PostEventFollowUpWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    private const val TAG = "WorkerScheduler"

    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)

        bootRecoveryRecurringWorkCommands().forEach { command ->
            workManager.enqueueRecurringAutomationWork(command)
        }
        ExactSendRecovery.recoverAsync(context)
        SmsDeliveryStatusRecovery.recoverAsync(context)
        scheduleDailyAutomationChain(context)

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

        val contactSyncRequest = OneTimeWorkRequestBuilder<ContactSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("initial_sync")
            .build()

        val eventDiscoveryRequest = OneTimeWorkRequestBuilder<EventDiscoveryWorker>()
            .setConstraints(localConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val messageGenRequest = OneTimeWorkRequestBuilder<MessageGenerationWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val holidayWishRequest = OneTimeWorkRequestBuilder<HolidayWishWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("holiday_wishes")
            .build()

        val followUpRequest = OneTimeWorkRequestBuilder<PostEventFollowUpWorker>()
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
