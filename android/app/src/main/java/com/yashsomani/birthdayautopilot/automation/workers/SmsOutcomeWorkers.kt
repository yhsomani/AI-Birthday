package com.yashsomani.birthdayautopilot.automation.workers

import android.content.Context
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yashsomani.birthdayautopilot.AppGraph
import com.yashsomani.birthdayautopilot.automation.sms.FirebaseSmsOutcomeCoordinationPort
import com.yashsomani.birthdayautopilot.automation.sms.SmsCallbackCleanup
import com.yashsomani.birthdayautopilot.automation.sms.SmsCallbackCleanupResult
import com.yashsomani.birthdayautopilot.automation.sms.SmsOutcomeNetworkProcessor
import com.yashsomani.birthdayautopilot.automation.sms.SmsOutcomeProcessingResult
import com.yashsomani.birthdayautopilot.automation.sms.SmsOutcomeProcessor
import com.yashsomani.birthdayautopilot.core.crypto.StorageKeyUnavailableException
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionNotifier
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleJournalStatus
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleStateStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit

class SmsEvidenceWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
  private val appGraph: AppGraph,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result {
    if (
      LifecycleStateStore(applicationContext).journalStatus() ==
      LifecycleJournalStatus.UNREADABLE
    ) return terminalFailure("LIFECYCLE_JOURNAL_UNREADABLE")
    return try {
      val processor = SmsOutcomeProcessor(appGraph.database)
      val now = System.currentTimeMillis()
      when (val cleanup = SmsCallbackCleanup(
        applicationContext,
        appGraph.database,
      ).cancelAndExpireDue(now)) {
        is SmsCallbackCleanupResult.Completed -> Unit
        is SmsCallbackCleanupResult.Refused -> return if (runAttemptCount < MAX_LOCAL_ATTEMPTS) {
          Result.retry()
        } else {
          terminalFailure(cleanup.safeCode)
        }
      }
      val attemptId = inputData.getString(SmsOutcomeWorkScheduler.INPUT_ATTEMPT_ID)
        ?.takeIf { SmsOutcomeWorkScheduler.validAttemptId(it) }
      val results = if (attemptId == null) {
        processor.reconstruct(now)
      } else {
        listOf(processor.processAttempt(attemptId, now))
      }
      results.forEach { SmsOutcomeWorkScheduler.scheduleFrom(applicationContext, it) }
      notify(
        WorkerAttentionPolicy.successful(
          "SMS_EVIDENCE_REDUCED",
          results.mapNotNull(SmsOutcomeProcessingResult::attentionSafeCode),
        ),
      )
      if (results.isNotEmpty()) {
        AutomationScheduler.enqueueImmediateLocal(applicationContext, "CALLBACK")
      }
      Result.success(safeData("SMS_EVIDENCE_REDUCED"))
    } catch (error: StorageKeyUnavailableException) {
      terminalFailure(error.safeCode)
    } catch (_: Exception) {
      if (runAttemptCount < MAX_LOCAL_ATTEMPTS) {
        Result.retry()
      } else {
        terminalFailure("SMS_EVIDENCE_ATTEMPTS_EXHAUSTED")
      }
    }
  }

  private fun terminalFailure(code: String): Result {
    WorkerAttentionPolicy.failure(code, terminal = true)?.let { notify(listOf(it)) }
    return Result.failure(safeData(code))
  }

  private fun notify(codes: Iterable<String>) {
    val notifier = AndroidAttentionNotifier(applicationContext)
    codes.forEach { code -> runCatching { notifier.onSafeCode(code) } }
  }

  private fun safeData(code: String): Data = Data.Builder().putString(SAFE_CODE, code).build()

  private companion object {
    const val SAFE_CODE = "safeCode"
    const val MAX_LOCAL_ATTEMPTS = 3
  }
}

class SmsOutcomeReportWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
  private val appGraph: AppGraph,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result {
    if (
      LifecycleStateStore(applicationContext).journalStatus() ==
      LifecycleJournalStatus.UNREADABLE
    ) return terminalFailure("LIFECYCLE_JOURNAL_UNREADABLE")
    return try {
      val attemptId = inputData.getString(SmsOutcomeWorkScheduler.INPUT_ATTEMPT_ID)
        ?.takeIf { SmsOutcomeWorkScheduler.validAttemptId(it) }
        ?: return Result.failure(safeData("SMS_REPORT_ATTEMPT_INVALID"))
      val processor = SmsOutcomeNetworkProcessor(
        database = appGraph.database,
        coordination = FirebaseSmsOutcomeCoordinationPort(appGraph.coordinationRuntime),
      )
      val result = processor.process(
        sendAttemptId = attemptId,
        wallNowMillis = System.currentTimeMillis(),
        elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime(),
        bootCount = bootCount(),
      )
      if (result.localStateChanged) {
        AutomationScheduler.enqueueImmediateLocal(applicationContext, "CALLBACK")
        SmsOutcomeWorkScheduler.enqueueEvidenceNow(applicationContext, attemptId)
      }
      when {
        result.retryRecommended && runAttemptCount < MAX_NETWORK_ATTEMPTS -> Result.retry()
        result.retryRecommended -> terminalFailure("SMS_REPORT_ATTEMPTS_EXHAUSTED")
        else -> {
          notify(WorkerAttentionPolicy.successful(result.safeCode))
          Result.success(safeData(result.safeCode))
        }
      }
    } catch (error: StorageKeyUnavailableException) {
      terminalFailure(error.safeCode)
    } catch (_: Exception) {
      if (runAttemptCount < MAX_NETWORK_ATTEMPTS) {
        Result.retry()
      } else {
        terminalFailure("SMS_REPORT_ATTEMPTS_EXHAUSTED")
      }
    }
  }

  private fun terminalFailure(code: String): Result {
    WorkerAttentionPolicy.failure(code, terminal = true)?.let { notify(listOf(it)) }
    return Result.failure(safeData(code))
  }

  private fun notify(codes: Iterable<String>) {
    val notifier = AndroidAttentionNotifier(applicationContext)
    codes.forEach { code -> runCatching { notifier.onSafeCode(code) } }
  }

  private fun bootCount(): Int? = try {
    Settings.Global.getInt(applicationContext.contentResolver, Settings.Global.BOOT_COUNT)
      .takeIf { it >= 0 }
  } catch (_: RuntimeException) {
    null
  }

  private fun safeData(code: String): Data = Data.Builder().putString(SAFE_CODE, code).build()

  private companion object {
    const val SAFE_CODE = "safeCode"
    const val MAX_NETWORK_ATTEMPTS = 6
  }
}

internal object SmsOutcomeWorkScheduler {
  fun ensureReconstructionScheduled(context: Context) {
    val request = PeriodicWorkRequestBuilder<SmsEvidenceWorker>(Duration.ofMinutes(15))
      .addTag(EVIDENCE_TAG)
      .addTag(RECONSTRUCTION_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      RECONSTRUCTION_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun enqueueEvidenceNow(context: Context, sendAttemptId: String) {
    if (!validAttemptId(sendAttemptId)) return
    val request = OneTimeWorkRequestBuilder<SmsEvidenceWorker>()
      .setInputData(attemptData(sendAttemptId))
      .addTag(EVIDENCE_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      "$EVIDENCE_NOW_PREFIX${opaqueSuffix(sendAttemptId)}",
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  fun scheduleFrom(context: Context, result: SmsOutcomeProcessingResult) {
    result.nextLocalWakeAtMillis?.let { deadline ->
      enqueueDeadline(context, result.sendAttemptId, deadline)
    }
    if (result.needsNetworkFollowUp) enqueueNetwork(context, result.sendAttemptId)
  }

  private fun enqueueDeadline(context: Context, sendAttemptId: String, deadlineMillis: Long) {
    if (!validAttemptId(sendAttemptId) || deadlineMillis <= 0) return
    val delay = (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val request = OneTimeWorkRequestBuilder<SmsEvidenceWorker>()
      .setInitialDelay(delay, TimeUnit.MILLISECONDS)
      .setInputData(attemptData(sendAttemptId))
      .addTag(EVIDENCE_TAG)
      .addTag(WATCHDOG_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      "$WATCHDOG_PREFIX${opaqueSuffix(sendAttemptId)}-$deadlineMillis",
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  private fun enqueueNetwork(context: Context, sendAttemptId: String) {
    if (!validAttemptId(sendAttemptId)) return
    val request = OneTimeWorkRequestBuilder<SmsOutcomeReportWorker>()
      .setConstraints(
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .setInputData(attemptData(sendAttemptId))
      .addTag(REPORT_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      "$REPORT_PREFIX${opaqueSuffix(sendAttemptId)}",
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  internal fun validAttemptId(value: String): Boolean = ATTEMPT_ID.matches(value)

  private fun attemptData(sendAttemptId: String): Data = Data.Builder()
    .putString(INPUT_ATTEMPT_ID, sendAttemptId)
    .build()

  private fun opaqueSuffix(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.US_ASCII))
    .take(16)
    .joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff) }

  const val INPUT_ATTEMPT_ID = "sendAttemptId"
  private const val RECONSTRUCTION_WORK_NAME = "sms-outcome-reconstruction-v1"
  private const val EVIDENCE_NOW_PREFIX = "sms-evidence-v1-"
  private const val WATCHDOG_PREFIX = "sms-watchdog-v1-"
  private const val REPORT_PREFIX = "sms-report-v1-"
  private const val EVIDENCE_TAG = "sms-evidence"
  private const val RECONSTRUCTION_TAG = "sms-reconstruction"
  private const val WATCHDOG_TAG = "sms-watchdog"
  private const val REPORT_TAG = "sms-report"
  private val ATTEMPT_ID = Regex("^[A-Za-z0-9._-]{1,96}$")
}
