package com.example.core.automation.scheduler

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.automation.workers.DailyTriggerWorker
import com.example.core.automation.workers.RevivalWorker
import com.example.core.automation.workers.StyleAnalysisWorker
import java.util.concurrent.TimeUnit

internal enum class BootRecoveryRecurringWorkCommand(
    val uniqueWorkName: String,
    val tag: String,
    val repeatInterval: Long,
    val repeatIntervalUnit: TimeUnit,
    val requiresConnectedNetwork: Boolean,
) {
    DAILY_TRIGGER(
        uniqueWorkName = "daily_trigger",
        tag = "daily_trigger",
        repeatInterval = 24,
        repeatIntervalUnit = TimeUnit.HOURS,
        requiresConnectedNetwork = false,
    ),
    REVIVAL(
        uniqueWorkName = "revival_check",
        tag = "revival",
        repeatInterval = 7,
        repeatIntervalUnit = TimeUnit.DAYS,
        requiresConnectedNetwork = false,
    ),
    STYLE_ANALYSIS(
        uniqueWorkName = "style_analysis",
        tag = "style_analysis",
        repeatInterval = 14,
        repeatIntervalUnit = TimeUnit.DAYS,
        requiresConnectedNetwork = true,
    ),
}

internal fun bootRecoveryRecurringWorkCommands(): List<BootRecoveryRecurringWorkCommand> {
    return listOf(
        BootRecoveryRecurringWorkCommand.DAILY_TRIGGER,
        BootRecoveryRecurringWorkCommand.REVIVAL,
        BootRecoveryRecurringWorkCommand.STYLE_ANALYSIS,
    )
}

internal fun WorkManager.reconcileBootRecoveryRecurringWork(command: BootRecoveryRecurringWorkCommand) {
    if (getWorkInfosByTag(command.tag).get().any { it.state.isBootRecoveryRecurringWorkActive() }) {
        return
    }
    enqueueUniquePeriodicWork(
        command.uniqueWorkName,
        ExistingPeriodicWorkPolicy.KEEP,
        command.toPeriodicWorkRequest(),
    )
}

internal fun WorkInfo.State.isBootRecoveryRecurringWorkActive(): Boolean {
    return this == WorkInfo.State.ENQUEUED || this == WorkInfo.State.RUNNING
}

internal fun BootRecoveryRecurringWorkCommand.toPeriodicWorkRequest(): PeriodicWorkRequest {
    return when (this) {
        BootRecoveryRecurringWorkCommand.DAILY_TRIGGER -> buildPeriodicWorkRequest<DailyTriggerWorker>()
        BootRecoveryRecurringWorkCommand.REVIVAL -> buildPeriodicWorkRequest<RevivalWorker>()
        BootRecoveryRecurringWorkCommand.STYLE_ANALYSIS -> buildPeriodicWorkRequest<StyleAnalysisWorker>()
    }
}

private inline fun <reified W : ListenableWorker> BootRecoveryRecurringWorkCommand.buildPeriodicWorkRequest(): PeriodicWorkRequest {
    return PeriodicWorkRequestBuilder<W>(repeatInterval, repeatIntervalUnit)
        .setConstraints(toConstraints())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag(tag)
        .build()
}

private fun BootRecoveryRecurringWorkCommand.toConstraints(): Constraints {
    val builder = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
    if (requiresConnectedNetwork) {
        builder.setRequiredNetworkType(NetworkType.CONNECTED)
    }
    return builder.build()
}
