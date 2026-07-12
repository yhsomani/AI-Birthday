package com.yashsomani.birthdayautopilot.storage.database

import java.util.Locale

/** Content-free scheduler state persisted in the existing diagnostic readiness row. */
internal enum class ReconcileHeartbeatStatus {
  RUNNING,
  SUCCEEDED,
  RETRYING,
  FAILED,
}

internal data class ReconcileHeartbeatLease(
  val accountId: String,
  /** Exact row revision written by begin; an older worker may not finish a newer heartbeat. */
  val revision: Long,
)

internal data class ReconcileHeartbeatSnapshot(
  val status: ReconcileHeartbeatStatus,
  val safeCode: String,
  val heartbeatAtMillis: Long,
)

/**
 * The table predates the scheduler heartbeat implementation and also reserves columns for a full
 * diagnostic readiness snapshot. Scheduler writes therefore update only scheduler/overall/time;
 * the remaining columns stay untouched once another diagnostic producer starts using them.
 */
internal object ReconcileHeartbeatPolicy {
  const val RUNNING_SAFE_CODE = "RECONCILE_RUNNING"
  const val RETRY_SAFE_CODE = "RECONCILE_RETRY_SCHEDULED"
  const val INTERRUPTED_SAFE_CODE = "RECONCILE_INTERRUPTED"
  const val INVALID_SAFE_CODE = "RECONCILE_SAFE_CODE_INVALID"
  private const val NOT_EVALUATED = "NOT_EVALUATED"
  private val SAFE_CODE = Regex("^[A-Z][A-Z0-9_]{0,63}$")

  fun normalizeSafeCode(raw: String): String {
    val normalized = raw.trim().uppercase(Locale.ROOT).replace('-', '_')
    return normalized.takeIf(SAFE_CODE::matches) ?: INVALID_SAFE_CODE
  }

  fun initialRow(accountId: String, atMillis: Long): ReadinessStateEntity = ReadinessStateEntity(
    accountId = accountId,
    distribution = NOT_EVALUATED,
    identity = NOT_EVALUATED,
    contactsSync = NOT_EVALUATED,
    standingConsent = NOT_EVALUATED,
    approvals = NOT_EVALUATED,
    smsPermission = NOT_EVALUATED,
    sim = NOT_EVALUATED,
    scheduler = ReconcileHeartbeatStatus.RUNNING.name,
    backgroundRestriction = NOT_EVALUATED,
    doze = NOT_EVALUATED,
    unusedAppRestriction = NOT_EVALUATED,
    dataSaver = NOT_EVALUATED,
    lowPowerStandby = NOT_EVALUATED,
    coordination = NOT_EVALUATED,
    network = NOT_EVALUATED,
    activeSender = NOT_EVALUATED,
    clockTrust = NOT_EVALUATED,
    resetSafety = NOT_EVALUATED,
    overall = RUNNING_SAFE_CODE,
    evaluatedAtMillis = atMillis,
    revision = 0,
  )

  fun snapshot(row: ReadinessStateEntity?): ReconcileHeartbeatSnapshot? {
    val value = row ?: return null
    val status = runCatching { ReconcileHeartbeatStatus.valueOf(value.scheduler) }.getOrNull()
      ?: return null
    if (value.evaluatedAtMillis < 0 || !SAFE_CODE.matches(value.overall)) return null
    return ReconcileHeartbeatSnapshot(status, value.overall, value.evaluatedAtMillis)
  }
}
