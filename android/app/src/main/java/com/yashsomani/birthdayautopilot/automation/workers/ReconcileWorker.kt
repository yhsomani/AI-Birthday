package com.yashsomani.birthdayautopilot.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.yashsomani.birthdayautopilot.AppGraph
import com.yashsomani.birthdayautopilot.automation.orchestration.ReconciliationTrigger
import com.yashsomani.birthdayautopilot.core.crypto.StorageKeyUnavailableException
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionNotifier
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleJournalStatus
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleStateStore
import com.yashsomani.birthdayautopilot.storage.database.ReconcileHeartbeatLease
import com.yashsomani.birthdayautopilot.storage.database.ReconcileHeartbeatPolicy
import com.yashsomani.birthdayautopilot.storage.database.ReconcileHeartbeatStatus

class ReconcileWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
  private val appGraph: AppGraph,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result {
    AutomationScheduler.consumeNetworkAttempt(applicationContext, inputData)
    return try {
      reconcileOnce()
    } finally {
      AutomationScheduler.completeNetworkAttempt(applicationContext, inputData)
    }
  }

  private suspend fun reconcileOnce(): Result {
    val lifecycle = LifecycleStateStore(applicationContext)
    if (lifecycle.journalStatus() == LifecycleJournalStatus.UNREADABLE) {
      return terminalFailure("LIFECYCLE_JOURNAL_UNREADABLE")
    }
    val operation = lifecycle.latestOperation()
    if (
      operation != null &&
      operation.action in SEND_BLOCKING_LIFECYCLE_ACTIONS &&
      operation.state !in setOf("complete", "failed") &&
      operation.remoteDrainUntilMillis == null
    ) {
      val code = "LIFECYCLE_OPERATION_PENDING"
      notify(WorkerAttentionPolicy.successful(code))
      return Result.success(safeData(code))
    }
    var heartbeatLease: ReconcileHeartbeatLease? = null
    var heartbeatStatus = ReconcileHeartbeatStatus.FAILED
    var heartbeatSafeCode = ReconcileHeartbeatPolicy.INTERRUPTED_SAFE_CODE
    return try {
      // Accessing the graph opens and initializes the encrypted database with the durable,
      // backup-excluded callback generation before any orchestration query runs.
      appGraph.database
      heartbeatLease = appGraph.peopleSyncDao.activeAccount()?.let { account ->
        appGraph.safetyLedgerDao.beginReconcileHeartbeat(
          accountId = account.accountId,
          atMillis = System.currentTimeMillis(),
        )
      }
      val trigger = inputData.getString(AutomationScheduler.INPUT_TRIGGER)
        ?.let { runCatching { ReconciliationTrigger.valueOf(it) }.getOrNull() }
        ?: ReconciliationTrigger.PERIODIC
      val result = appGraph.automationOrchestrator.reconcile(trigger)
      val notificationPrimary = if (
        result.safeCode in ACCOUNT_ABSENCE_CODES &&
        appGraph.peopleSyncDao.activeAccount() == null
      ) {
        "RECONCILE_IDLE"
      } else {
        result.safeCode
      }
      notify(
        WorkerAttentionPolicy.successful(
          notificationPrimary,
          listOfNotNull(result.attentionSafeCode),
        ),
      )
      if (result.nextWakeAtMillis != null || result.retryRecommended) {
        AutomationScheduler.scheduleNetworkAttempt(
          applicationContext,
          result.operationKey,
          if (result.retryRecommended) {
            maxOf(
              result.nextWakeAtMillis ?: Long.MIN_VALUE,
              System.currentTimeMillis() + MIN_RETRY_DELAY_MILLIS,
            )
          } else {
            result.nextWakeAtMillis
          },
        )
      }
      heartbeatStatus = if (result.retryRecommended) {
        ReconcileHeartbeatStatus.RETRYING
      } else {
        ReconcileHeartbeatStatus.SUCCEEDED
      }
      heartbeatSafeCode = result.safeCode
      // Retry is represented by one uniquely named successor, not a second anonymous WorkManager
      // retry chain that could race the exact operation worker.
      Result.success(safeData(result.safeCode))
    } catch (error: StorageKeyUnavailableException) {
      heartbeatStatus = ReconcileHeartbeatStatus.FAILED
      heartbeatSafeCode = error.safeCode
      terminalFailure(error.safeCode)
    } catch (_: Exception) {
      if (runAttemptCount < MAX_TRANSIENT_ATTEMPTS) {
        heartbeatStatus = ReconcileHeartbeatStatus.RETRYING
        heartbeatSafeCode = ReconcileHeartbeatPolicy.RETRY_SAFE_CODE
        Result.retry()
      } else {
        heartbeatStatus = ReconcileHeartbeatStatus.FAILED
        heartbeatSafeCode = "RECONCILE_ATTEMPTS_EXHAUSTED"
        terminalFailure("RECONCILE_ATTEMPTS_EXHAUSTED")
      }
    } finally {
      heartbeatLease?.let { lease ->
        try {
          appGraph.safetyLedgerDao.finishReconcileHeartbeat(
            lease = lease,
            status = heartbeatStatus,
            safeCode = heartbeatSafeCode,
            atMillis = System.currentTimeMillis(),
          )
        } catch (_: Exception) {
          // Heartbeat is diagnostic evidence only and must never change the worker's send result.
        }
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

  private fun safeData(code: String): Data = Data.Builder()
    .putString(SAFE_CODE, code)
    .build()

  private companion object {
    const val SAFE_CODE = "safeCode"
    const val MAX_TRANSIENT_ATTEMPTS = 3
    const val MIN_RETRY_DELAY_MILLIS = 30_000L
    val SEND_BLOCKING_LIFECYCLE_ACTIONS = setOf(
      "sender-transfer",
      "disconnect-contacts",
      "revoke-google-access",
      "sign-out-retain",
      "sign-out-wipe",
      "delete-account",
      "wipe-local-data",
      "clear-gemini-templates",
    )
    val ACCOUNT_ABSENCE_CODES = setOf(
      "ACCOUNT_NOT_CONNECTED",
      "SENDER_REGISTRATION_UNAVAILABLE",
    )
  }
}
