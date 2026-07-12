package com.yashsomani.birthdayautopilot.automation.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.yashsomani.birthdayautopilot.AppGraph
import com.yashsomani.birthdayautopilot.attention.AndroidAttentionNotifier
import com.yashsomani.birthdayautopilot.core.crypto.StorageKeyUnavailableException
import com.yashsomani.birthdayautopilot.people.PeopleBackgroundSyncPolicy

/** Performs a silent, bounded Google People refresh. It never owns a foreground resolution. */
class PeopleSyncWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
  private val appGraph: AppGraph,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result = try {
    val decision = PeopleBackgroundSyncPolicy.decide(
      appGraph.peopleSyncService.sync(interactiveAuthorization = false),
      runAttemptCount,
    )
    if (decision.reconcileAutomation) {
      AutomationScheduler.enqueueContactChange(applicationContext)
    }
    if (decision.notifyAttention) {
      runCatching {
        AndroidAttentionNotifier(applicationContext).onSafeCode(decision.safeCode)
      }
    }
    when {
      decision.retry -> Result.retry()
      else -> Result.success(safeData(decision.safeCode))
    }
  } catch (error: StorageKeyUnavailableException) {
    Result.failure(safeData(error.safeCode))
  } catch (_: Exception) {
    if (runAttemptCount + 1 < MAX_TRANSIENT_ATTEMPTS) {
      Result.retry()
    } else {
      Result.failure(safeData("CONTACTS_BACKGROUND_ATTEMPTS_EXHAUSTED"))
    }
  }

  private fun safeData(code: String): Data = Data.Builder()
    .putString(SAFE_CODE, code)
    .build()

  private companion object {
    const val SAFE_CODE = "safeCode"
    const val MAX_TRANSIENT_ATTEMPTS = 3
  }
}
