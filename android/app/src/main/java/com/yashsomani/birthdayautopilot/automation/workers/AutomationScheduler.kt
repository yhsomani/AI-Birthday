package com.yashsomani.birthdayautopilot.automation.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

object AutomationScheduler {
  fun ensureScheduled(context: Context) {
    SmsOutcomeWorkScheduler.ensureReconstructionScheduled(context)
    val request = PeriodicWorkRequestBuilder<ReconcileWorker>(Duration.ofMinutes(15))
      // Local expiry, clock/boot reconstruction and barrier recovery must run while offline.
      .setInputData(triggerData("PERIODIC"))
      .addTag(RECONCILE_TAG)
      .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      RECONCILE_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun enqueueImmediateLocal(context: Context, trigger: String) {
    val request = OneTimeWorkRequestBuilder<ReconcileWorker>()
      .setInputData(triggerData(trigger))
      .addTag(RECONCILE_TAG)
      .addTag(LOCAL_RECONCILE_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      LOCAL_RECONCILE_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  fun scheduleNetworkAttempt(
    context: Context,
    operationKey: String?,
    runAtMillis: Long?,
  ) {
    val now = System.currentTimeMillis()
    val delay = runAtMillis?.let { (it - now).coerceAtLeast(0) } ?: 0
    val stableSuffix = operationKey
      ?.takeIf { it.matches(OPAQUE_OPERATION_KEY) }
      ?: NEXT_WINDOW_SUFFIX
    val request = OneTimeWorkRequestBuilder<ReconcileWorker>()
      .setConstraints(
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
      )
      .setInitialDelay(delay, TimeUnit.MILLISECONDS)
      .setInputData(triggerData("NEXT_WINDOW"))
      .addTag(RECONCILE_TAG)
      .addTag(ATTEMPT_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      "$ATTEMPT_WORK_PREFIX$stableSuffix",
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }

  private fun triggerData(trigger: String): Data = Data.Builder()
    .putString(INPUT_TRIGGER, trigger)
    .build()

  const val RECONCILE_WORK_NAME = "birthday-reconcile-v1"
  const val RECONCILE_TAG = "birthday-reconcile"
  const val INPUT_TRIGGER = "reconcileTrigger"
  private const val LOCAL_RECONCILE_WORK_NAME = "birthday-local-reconcile-v1"
  private const val LOCAL_RECONCILE_TAG = "birthday-local-reconcile"
  private const val ATTEMPT_WORK_PREFIX = "birthday-attempt-v1-"
  private const val ATTEMPT_TAG = "birthday-network-attempt"
  private const val NEXT_WINDOW_SUFFIX = "next-window"
  private val OPAQUE_OPERATION_KEY = Regex("^[a-z]+_[a-f0-9]{64}$")
}
