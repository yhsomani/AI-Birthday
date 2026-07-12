package com.yashsomani.birthdayautopilot.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.yashsomani.birthdayautopilot.AppGraph
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionNotifier
import com.yashsomani.birthdayautopilot.automation.sms.SmsCallbackCleanup
import com.yashsomani.birthdayautopilot.automation.sms.SmsCallbackCleanupResult
import com.yashsomani.birthdayautopilot.core.crypto.StorageKeyUnavailableException
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleJournalStatus
import com.yashsomani.birthdayautopilot.lifecycle.LifecycleStateStore
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase

internal data class DataRetentionRunResult(
  val deletedRows: Int,
  val redactedTestJobs: Int,
  val moreWork: Boolean,
)

/** Runs a finite number of small Room transactions and never targets Birthday safety ledgers. */
internal class AndroidDataRetention(
  private val database: BirthdayDatabase,
) {
  suspend fun prune(
    nowMillis: Long,
    maxBatches: Int = MAX_BATCHES_PER_RUN,
    batchSize: Int = BATCH_SIZE,
  ): DataRetentionRunResult {
    require(nowMillis >= 0) { "retention-now-invalid" }
    require(maxBatches in 1..MAX_BATCHES_PER_RUN) { "retention-batches-invalid" }
    require(batchSize in 1..BATCH_SIZE) { "retention-batch-size-invalid" }
    val cutoff = (nowMillis - ACTIVITY_RETENTION_MILLIS).coerceAtLeast(0)
    var deleted = 0
    var redacted = 0
    var moreWork = false
    repeat(maxBatches) {
      val result = database.retentionDao().pruneBatch(nowMillis, cutoff, batchSize)
      deleted = listOf(
        result.deletedActivityRows,
        result.deletedDeliveryEvents,
        result.deletedCallbackTokens,
        result.deletedTestAttempts,
        result.deletedTestPermits,
        result.deletedTestReceipts,
        result.deletedTestProjections,
        result.deletedTestJobs,
      ).fold(deleted, Math::addExact)
      redacted = Math.addExact(redacted, result.redactedTestJobs)
      moreWork = result.moreWork
      if (!moreWork) return DataRetentionRunResult(deleted, redacted, false)
    }
    return DataRetentionRunResult(deleted, redacted, moreWork)
  }

  internal companion object {
    const val BATCH_SIZE = 256
    const val MAX_BATCHES_PER_RUN = 8
    const val ACTIVITY_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
  }
}

/**
 * Cancels due PendingIntents before pruning their identities, then applies bounded local
 * retention. Expected backlog returns retry so a large import cannot turn one worker into an
 * unbounded database task.
 */
class DataRetentionWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
  private val appGraph: AppGraph,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result {
    return try {
      val lifecycle = LifecycleStateStore(applicationContext)
      if (lifecycle.journalStatus() == LifecycleJournalStatus.UNREADABLE) {
        return retryOrFailure("LIFECYCLE_JOURNAL_UNREADABLE")
      }
      val lifecycleOperation = lifecycle.latestOperation()
      if (
        lifecycleOperation != null &&
        lifecycleOperation.action in MUTATING_LIFECYCLE_ACTIONS &&
        lifecycleOperation.state !in setOf("complete", "failed")
      ) return Result.retry()
      // A device-wall rollback must not extend a previously reached privacy boundary. Server
      // time is monotonic in the ledger; a forward wall jump can only redact early and fail closed.
      val now = maxOf(
        System.currentTimeMillis().coerceAtLeast(0),
        appGraph.database.retentionDao().greatestTrustedServerMillis() ?: 0,
      )
      var callbackBacklog = false
      var callbackBatch = 0
      while (callbackBatch < MAX_CALLBACK_BATCHES_PER_RUN) {
        when (
          val cleanup = SmsCallbackCleanup(
            applicationContext,
            appGraph.database,
          ).cancelAndExpireDue(now, AndroidDataRetention.BATCH_SIZE)
        ) {
          is SmsCallbackCleanupResult.Completed -> {
            callbackBacklog = cleanup.tokenCount == AndroidDataRetention.BATCH_SIZE
            if (!callbackBacklog) break
          }
          is SmsCallbackCleanupResult.Refused -> return retryOrFailure(cleanup.safeCode)
        }
        callbackBatch += 1
      }
      val pruned = AndroidDataRetention(appGraph.database).prune(now)
      if (callbackBacklog || pruned.moreWork) {
        Result.retry()
      } else {
        Result.success(
          Data.Builder()
            .putString(SAFE_CODE, "DATA_RETENTION_COMPLETE")
            .putInt(DELETED_ROWS, pruned.deletedRows)
            .putInt(REDACTED_TEST_JOBS, pruned.redactedTestJobs)
            .build(),
        )
      }
    } catch (error: StorageKeyUnavailableException) {
      retryOrFailure(error.safeCode)
    } catch (_: ArithmeticException) {
      retryOrFailure("DATA_RETENTION_COUNT_OVERFLOW")
    } catch (_: Exception) {
      retryOrFailure("DATA_RETENTION_ATTEMPTS_EXHAUSTED")
    }
  }

  private fun retryOrFailure(code: String): Result {
    if (runAttemptCount < MAX_TRANSIENT_ATTEMPTS) return Result.retry()
    WorkerAttentionPolicy.failure(
      "DATA_RETENTION_ATTEMPTS_EXHAUSTED",
      terminal = true,
    )?.let { safeCode -> runCatching { AndroidAttentionNotifier(applicationContext).onSafeCode(safeCode) } }
    return Result.failure(Data.Builder().putString(SAFE_CODE, code).build())
  }

  private companion object {
    const val SAFE_CODE = "safeCode"
    const val DELETED_ROWS = "deletedRows"
    const val REDACTED_TEST_JOBS = "redactedTestJobs"
    const val MAX_CALLBACK_BATCHES_PER_RUN = 4
    const val MAX_TRANSIENT_ATTEMPTS = 3
    val MUTATING_LIFECYCLE_ACTIONS = setOf(
      "sender-transfer",
      "disconnect-contacts",
      "revoke-google-access",
      "sign-out-retain",
      "sign-out-wipe",
      "delete-account",
      "wipe-local-data",
      "clear-gemini-templates",
    )
  }
}
