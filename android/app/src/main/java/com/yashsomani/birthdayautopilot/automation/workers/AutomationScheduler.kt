package com.yashsomani.birthdayautopilot.automation.workers

import android.annotation.SuppressLint
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
import java.util.UUID
import java.util.concurrent.TimeUnit

// The KTX edit helper intentionally discards SharedPreferences.Editor.commit()'s Boolean result.
// This durable wake ledger must fail closed when a synchronous disk commit is rejected, so keep
// the explicit Editor calls and their checked return values.
@SuppressLint("UseKtx")
object AutomationScheduler {
  fun ensureScheduled(context: Context) {
    recoverNetworkAttempts(context)
    SmsOutcomeWorkScheduler.ensureReconstructionScheduled(context)
    ensureDataRetentionScheduled(context)
    ensurePeopleSyncScheduled(context)
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

  private fun ensureDataRetentionScheduled(context: Context) {
    val manager = WorkManager.getInstance(context)
    val periodic = PeriodicWorkRequestBuilder<DataRetentionWorker>(
      DATA_RETENTION_INTERVAL_HOURS,
      TimeUnit.HOURS,
    )
      .addTag(DATA_RETENTION_TAG)
      .build()
    manager.enqueueUniquePeriodicWork(
      DATA_RETENTION_PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      periodic,
    )
    val startup = OneTimeWorkRequestBuilder<DataRetentionWorker>()
      .addTag(DATA_RETENTION_TAG)
      .build()
    manager.enqueueUniqueWork(
      DATA_RETENTION_STARTUP_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      startup,
    )
  }

  private fun ensurePeopleSyncScheduled(context: Context) {
    val request = PeriodicWorkRequestBuilder<PeopleSyncWorker>(
      PEOPLE_SYNC_INTERVAL_HOURS,
      TimeUnit.HOURS,
      PEOPLE_SYNC_FLEX_HOURS,
      TimeUnit.HOURS,
    )
      .setConstraints(
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
      )
      .addTag(PEOPLE_SYNC_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      PEOPLE_SYNC_WORK_NAME,
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

  /**
   * Account attachment must produce a successor even when a pre-login foreground reconciliation
   * is already running. A separate appendable chain prevents local KEEP work from swallowing it.
   */
  fun enqueueAccountChange(context: Context) {
    enqueueStateChange(context, "ACCOUNT_CHANGED")
  }

  /** Contact commits invalidate local approvals and must converge remote sender state promptly. */
  fun enqueueContactChange(context: Context) {
    enqueueStateChange(context, "CONTACTS_CHANGED")
  }

  private fun enqueueStateChange(context: Context, trigger: String) {
    val request = OneTimeWorkRequestBuilder<ReconcileWorker>()
      .setInputData(triggerData(trigger))
      .addTag(RECONCILE_TAG)
      .addTag(STATE_CHANGE_RECONCILE_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      STATE_CHANGE_RECONCILE_WORK_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }

  /** A subscription signal has its own appendable queue and cannot be hidden by local KEEP work. */
  fun enqueueSubscriptionChange(context: Context) {
    val request = OneTimeWorkRequestBuilder<ReconcileWorker>()
      .setInputData(triggerData("SIM_CHANGED"))
      .addTag(RECONCILE_TAG)
      .addTag(SUBSCRIPTION_RECONCILE_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      SUBSCRIPTION_RECONCILE_WORK_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }

  fun scheduleNetworkAttempt(
    context: Context,
    operationKey: String?,
    runAtMillis: Long?,
  ) {
    val stableSuffix = operationKey
      ?.takeIf { it.matches(OPAQUE_OPERATION_KEY) }
      ?: NEXT_WINDOW_SUFFIX
    val now = System.currentTimeMillis()
    val requestedRunAtMillis = runAtMillis ?: now
    synchronized(NETWORK_ATTEMPT_LOCK) {
      val preferences = networkAttemptPreferences(context)
      val targetKey = targetPreferenceKey(stableSuffix)
      val tokenKey = tokenPreferenceKey(stableSuffix)
      val existingTarget = if (preferences.contains(targetKey)) {
        preferences.getLong(targetKey, requestedRunAtMillis)
      } else {
        requestedRunAtMillis
      }
      // A later reconciliation must never postpone an already-required earlier wake.
      val effectiveRunAtMillis = minOf(existingTarget, requestedRunAtMillis)
      val token = UUID.randomUUID().toString().lowercase()
      check(
        preferences.edit()
          .putLong(targetKey, effectiveRunAtMillis)
          .putString(tokenKey, token)
          .commit(),
      ) { "network-attempt-coalescer-unavailable" }
      // Do not cancel an actively reconciling worker. It will enqueue this durable earliest target
      // from its finally block after all CAS/network recovery logic has completed.
      if (!preferences.contains(inFlightPreferenceKey(stableSuffix))) {
        enqueueNetworkAttempt(context, stableSuffix, effectiveRunAtMillis, token, now)
      }
    }
  }

  /** Retires only the exact durable wake that started; a replaced predecessor cannot erase it. */
  internal fun consumeNetworkAttempt(context: Context, input: Data) {
    if (input.getString(INPUT_TRIGGER) != "NEXT_WINDOW") return
    val stableSuffix = input.getString(INPUT_ATTEMPT_SUFFIX)?.takeIf(::validAttemptSuffix) ?: return
    val token = input.getString(INPUT_ATTEMPT_TOKEN)
      ?.takeIf { it.matches(ATTEMPT_TOKEN) }
      ?: return
    synchronized(NETWORK_ATTEMPT_LOCK) {
      val preferences = networkAttemptPreferences(context)
      val tokenKey = tokenPreferenceKey(stableSuffix)
      if (preferences.getString(tokenKey, null) != token) return
      check(
        preferences.edit()
          .remove(targetPreferenceKey(stableSuffix))
          .remove(tokenKey)
          .putString(inFlightPreferenceKey(stableSuffix), token)
          .commit(),
      ) { "network-attempt-coalescer-unavailable" }
    }
  }

  /** Enqueues any wake requested during this worker only after its reconciliation is complete. */
  internal fun completeNetworkAttempt(context: Context, input: Data) {
    if (input.getString(INPUT_TRIGGER) != "NEXT_WINDOW") return
    val stableSuffix = input.getString(INPUT_ATTEMPT_SUFFIX)?.takeIf(::validAttemptSuffix) ?: return
    val token = input.getString(INPUT_ATTEMPT_TOKEN)
      ?.takeIf { it.matches(ATTEMPT_TOKEN) }
      ?: return
    synchronized(NETWORK_ATTEMPT_LOCK) {
      val preferences = networkAttemptPreferences(context)
      val inFlightKey = inFlightPreferenceKey(stableSuffix)
      if (preferences.getString(inFlightKey, null) != token) return
      check(preferences.edit().remove(inFlightKey).commit()) {
        "network-attempt-coalescer-unavailable"
      }
      pendingNetworkAttempt(preferences, stableSuffix)?.let { pending ->
        enqueueNetworkAttempt(
          context,
          stableSuffix,
          pending.first,
          pending.second,
          System.currentTimeMillis(),
        )
      }
    }
  }

  internal fun resetNetworkAttemptCoalescerForTests(context: Context) {
    synchronized(NETWORK_ATTEMPT_LOCK) {
      check(networkAttemptPreferences(context).edit().clear().commit())
    }
  }

  private fun triggerData(trigger: String): Data = Data.Builder()
    .putString(INPUT_TRIGGER, trigger)
    .build()

  const val RECONCILE_WORK_NAME = "birthday-reconcile-v1"
  const val RECONCILE_TAG = "birthday-reconcile"
  const val INPUT_TRIGGER = "reconcileTrigger"
  internal const val INPUT_ATTEMPT_RUN_AT_MILLIS = "networkAttemptRunAtMillis"
  internal const val INPUT_ATTEMPT_SUFFIX = "networkAttemptSuffix"
  internal const val INPUT_ATTEMPT_TOKEN = "networkAttemptToken"
  const val PEOPLE_SYNC_WORK_NAME = "birthday-people-sync-v1"
  const val PEOPLE_SYNC_TAG = "birthday-people-sync"
  const val DATA_RETENTION_PERIODIC_WORK_NAME = "birthday-data-retention-periodic-v1"
  const val DATA_RETENTION_STARTUP_WORK_NAME = "birthday-data-retention-startup-v1"
  const val DATA_RETENTION_TAG = "birthday-data-retention"
  internal const val STATE_CHANGE_RECONCILE_WORK_NAME = "birthday-state-change-reconcile-v1"
  internal const val STATE_CHANGE_RECONCILE_TAG = "birthday-state-change-reconcile"
  private const val PEOPLE_SYNC_INTERVAL_HOURS = 24L
  private const val PEOPLE_SYNC_FLEX_HOURS = 6L
  private const val DATA_RETENTION_INTERVAL_HOURS = 24L
  private const val LOCAL_RECONCILE_WORK_NAME = "birthday-local-reconcile-v1"
  private const val LOCAL_RECONCILE_TAG = "birthday-local-reconcile"
  private const val SUBSCRIPTION_RECONCILE_WORK_NAME = "birthday-subscription-reconcile-v1"
  private const val SUBSCRIPTION_RECONCILE_TAG = "birthday-subscription-reconcile"
  private const val ATTEMPT_WORK_PREFIX = "birthday-attempt-v1-"
  private const val ATTEMPT_TAG = "birthday-network-attempt"
  private const val NEXT_WINDOW_SUFFIX = "next-window"
  private const val NETWORK_ATTEMPT_PREFERENCES = "birthday-network-attempt-v1"
  private const val TARGET_KEY_PREFIX = "target:"
  private const val TOKEN_KEY_PREFIX = "token:"
  private const val IN_FLIGHT_KEY_PREFIX = "in-flight:"
  private val OPAQUE_OPERATION_KEY = Regex("^[a-z]+_[a-f0-9]{64}$")
  private val ATTEMPT_TOKEN = Regex("^[a-f0-9-]{36}$")
  private val NETWORK_ATTEMPT_LOCK = Any()

  private fun networkAttemptPreferences(context: Context) =
    context.applicationContext.getSharedPreferences(NETWORK_ATTEMPT_PREFERENCES, Context.MODE_PRIVATE)

  private fun validAttemptSuffix(value: String): Boolean =
    value == NEXT_WINDOW_SUFFIX || value.matches(OPAQUE_OPERATION_KEY)

  private fun targetPreferenceKey(suffix: String) = "$TARGET_KEY_PREFIX$suffix"
  private fun tokenPreferenceKey(suffix: String) = "$TOKEN_KEY_PREFIX$suffix"
  private fun inFlightPreferenceKey(suffix: String) = "$IN_FLIGHT_KEY_PREFIX$suffix"

  private fun pendingNetworkAttempt(
    preferences: android.content.SharedPreferences,
    suffix: String,
  ): Pair<Long, String>? {
    val targetKey = targetPreferenceKey(suffix)
    val tokenKey = tokenPreferenceKey(suffix)
    if (!preferences.contains(targetKey)) return null
    val token = preferences.getString(tokenKey, null)?.takeIf { it.matches(ATTEMPT_TOKEN) }
      ?: return null
    return preferences.getLong(targetKey, 0L) to token
  }

  private fun enqueueNetworkAttempt(
    context: Context,
    suffix: String,
    runAtMillis: Long,
    token: String,
    nowMillis: Long,
  ) {
    val request = OneTimeWorkRequestBuilder<ReconcileWorker>()
      .setConstraints(
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
      )
      .setInitialDelay(subtractOrZero(runAtMillis, nowMillis), TimeUnit.MILLISECONDS)
      .setInputData(
        Data.Builder()
          .putAll(triggerData("NEXT_WINDOW"))
          .putString(INPUT_ATTEMPT_SUFFIX, suffix)
          .putString(INPUT_ATTEMPT_TOKEN, token)
          .putLong(INPUT_ATTEMPT_RUN_AT_MILLIS, runAtMillis)
          .build(),
      )
      .addTag(RECONCILE_TAG)
      .addTag(ATTEMPT_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      "$ATTEMPT_WORK_PREFIX$suffix",
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  /** Replays a persisted wake after process death and clears orphaned in-flight markers. */
  private fun recoverNetworkAttempts(context: Context) {
    synchronized(NETWORK_ATTEMPT_LOCK) {
      val preferences = networkAttemptPreferences(context)
      val suffixes = preferences.all.keys
        .filter { it.startsWith(TARGET_KEY_PREFIX) }
        .map { it.removePrefix(TARGET_KEY_PREFIX) }
        .filter(::validAttemptSuffix)
        .distinct()
      val editor = preferences.edit()
      preferences.all.keys.filter { it.startsWith(IN_FLIGHT_KEY_PREFIX) }.forEach(editor::remove)
      check(editor.commit()) { "network-attempt-coalescer-unavailable" }
      val now = System.currentTimeMillis()
      suffixes.forEach { suffix ->
        pendingNetworkAttempt(preferences, suffix)?.let { pending ->
          enqueueNetworkAttempt(context, suffix, pending.first, pending.second, now)
        }
      }
    }
  }

  private fun subtractOrZero(later: Long, earlier: Long): Long = try {
    Math.subtractExact(later, earlier).coerceAtLeast(0)
  } catch (_: ArithmeticException) {
    if (later > earlier) Long.MAX_VALUE else 0
  }
}
