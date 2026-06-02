package com.example.automation.scheduler

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.automation.workers.ContactSyncWorker
import com.example.automation.workers.EventDiscoveryWorker
import com.example.automation.workers.MessageGenerationWorker
import com.example.automation.workers.RevivalWorker
import com.example.automation.workers.StyleAnalysisWorker
import com.example.core.prefs.SecurePrefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    private const val TAG = "WorkerScheduler"

    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val prefs = SecurePrefs(context)
        
        val isSetupComplete = try {
            prefs.getGeminiApiKey().isNotEmpty()
        } catch (e: Exception) {
            false
        }

        if (!isSetupComplete) {
            Log.i(TAG, "Setup not complete; deferring worker schedule by 1h.")
        }

        val fallbackDelay = TimeUnit.HOURS.toMillis(1)
        val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val backoff = BackoffPolicy.EXPONENTIAL
        val minBackoffSeconds = 30L

        workManager.enqueueUniquePeriodicWork(
            "contact_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ContactSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .setInitialDelay(
                    if (isSetupComplete) maxOf(calculateDelayUntilMidnight(), 0) else fallbackDelay,
                    TimeUnit.MILLISECONDS
                )
                .setBackoffCriteria(backoff, minBackoffSeconds, TimeUnit.SECONDS)
                .build()
        )

        workManager.enqueueUniquePeriodicWork(
            "event_discovery",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EventDiscoveryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(
                    if (isSetupComplete) maxOf(calculateDelayUntil(0, 5), 0) else fallbackDelay,
                    TimeUnit.MILLISECONDS
                )
                .setBackoffCriteria(backoff, minBackoffSeconds, TimeUnit.SECONDS)
                .build()
        )

        workManager.enqueueUniquePeriodicWork(
            "message_generation",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MessageGenerationWorker>(24, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .setInitialDelay(
                    if (isSetupComplete) maxOf(calculateDelayUntil(1, 0), 0) else fallbackDelay,
                    TimeUnit.MILLISECONDS
                )
                .setBackoffCriteria(backoff, minBackoffSeconds, TimeUnit.SECONDS)
                .build()
        )

        workManager.enqueueUniquePeriodicWork(
            "revival_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RevivalWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(2, TimeUnit.DAYS)
                .setBackoffCriteria(backoff, minBackoffSeconds, TimeUnit.SECONDS)
                .build()
        )

        workManager.enqueueUniquePeriodicWork(
            "style_analysis",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StyleAnalysisWorker>(14, TimeUnit.DAYS)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(backoff, minBackoffSeconds, TimeUnit.SECONDS)
                .build()
        )
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
