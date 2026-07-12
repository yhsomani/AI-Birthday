package com.yashsomani.birthdayautopilot.automation.orchestration

import android.content.Context
import com.yashsomani.birthdayautopilot.BuildConfig
import com.yashsomani.birthdayautopilot.automation.sms.AndroidSmsGateway
import com.yashsomani.birthdayautopilot.automation.sms.SmsSubmissionResult
import com.yashsomani.birthdayautopilot.automation.sms.SubmissionGate
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.coordination.ArmDecisionOutcome
import com.yashsomani.birthdayautopilot.coordination.ArmStatusOutcome
import com.yashsomani.birthdayautopilot.coordination.AuthoritativeArmOutcome
import com.yashsomani.birthdayautopilot.coordination.ClaimOutcome
import com.yashsomani.birthdayautopilot.coordination.CoordinationPurpose
import com.yashsomani.birthdayautopilot.coordination.CoordinationServerReason
import com.yashsomani.birthdayautopilot.coordination.DistributionChannel
import com.yashsomani.birthdayautopilot.coordination.LeaseOutcome
import com.yashsomani.birthdayautopilot.coordination.RegistrationOutcome
import com.yashsomani.birthdayautopilot.coordination.ServerAccountMode
import com.yashsomani.birthdayautopilot.coordination.ServerClaimState
import com.yashsomani.birthdayautopilot.coordination.ServerInstallationState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.planning.BirthdayRule
import com.yashsomani.birthdayautopilot.planning.LeapDayPolicy
import com.yashsomani.birthdayautopilot.planning.RecurrencePlanner
import com.yashsomani.birthdayautopilot.readiness.AndroidReadinessProbe
import com.yashsomani.birthdayautopilot.readiness.EligibilityKind
import com.yashsomani.birthdayautopilot.storage.database.AuthoritativeArmedEvidence
import com.yashsomani.birthdayautopilot.storage.database.BirthdayOccurrenceRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustEntity
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustStatus
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitState
import com.yashsomani.birthdayautopilot.storage.database.CoordinationStateEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.LocalDestinationGuardEntity
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.PermitIssueResult
import com.yashsomani.birthdayautopilot.storage.database.ResetBlockedDateEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyStatus
import com.yashsomani.birthdayautopilot.storage.database.SafetyLedgerDao
import com.yashsomani.birthdayautopilot.storage.database.SyncFreshness
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class ReconciliationTrigger {
  PERIODIC,
  NEXT_WINDOW,
  BOOT_OR_CLOCK,
  APP_REPLACED,
  FOREGROUND,
  CALLBACK,
}

internal data class AutomationReconcileResult(
  val safeCode: String,
  val retryRecommended: Boolean,
  val nextWakeAtMillis: Long?,
  val operationKey: String? = null,
  val attentionSafeCode: String? = null,
) {
  override fun toString(): String =
    "AutomationReconcileResult(code=$safeCode,retry=$retryRecommended,hasNext=${nextWakeAtMillis != null})"
}

/**
 * Native-only durable automation coordinator. Every network wait occurs outside SubmissionGate;
 * every Arm request is committed once before dispatch and is subsequently status-query-only.
 */
internal class AndroidAutomationOrchestrator(
  context: Context,
  private val dao: AutomationOrchestrationDao,
  private val ledger: SafetyLedgerDao,
  private val coordination: AutomationCoordinationPort,
  private val recurrencePlanner: RecurrencePlanner,
  private val readinessProbe: AndroidReadinessProbe,
  private val identitySessionMatches: suspend (String) -> Boolean,
  private val submissionGate: SubmissionGate,
  private val smsGateway: AndroidSmsGateway,
  private val finalGateSource: AndroidFinalExternalGateSource,
  private val timeSource: AutomationTimeSource,
  private val installationIdentityStore: NoBackupInstallationIdentityStore,
) {
  private val appContext = context.applicationContext

  suspend fun reconcile(trigger: ReconciliationTrigger): AutomationReconcileResult =
    globalOrchestrationMutex.withLock {
      reconcileSerial(trigger)
    }

  /** Lifecycle reconciliation may query registration, but never redispatches transfer mutation. */
  suspend fun refreshRegistrationForLifecycle(): InstallationBindingEntity? =
    globalOrchestrationMutex.withLock {
      ensureRegisteredBinding(
        allowCachedOnUnavailable = false,
        acceptAuthoritativeStandby = true,
      )
    }

  suspend fun applyCompletedSenderTransfer(
    outcome: com.yashsomani.birthdayautopilot.coordination.SenderTransferOutcome.Completed,
  ): Boolean = globalOrchestrationMutex.withLock {
    val account = dao.activeAccount() ?: return@withLock false
    val local = dao.localInstallation() ?: return@withLock false
    if (
      local.installationId != outcome.targetInstallationId ||
      outcome.binding.activeInstallationId != local.installationId ||
      outcome.binding.mode != ServerAccountMode.TEST_ONLY
    ) return@withLock false
    applyRegistration(
      account.accountId,
      local,
      RegistrationOutcome.Registered(
        disposition = RegistrationOutcome.Disposition.REPLAYED,
        binding = outcome.binding,
        installationState = ServerInstallationState.ACTIVE,
        installationEpoch = outcome.binding.senderEpoch,
      ),
    ) != null
  }

  /** Foreground-only entry for a previously persisted immutable TestJob. */
  suspend fun submitForegroundTest(
    testJobId: String,
    foregroundConfirmationNonceHash: String,
  ): AutomationReconcileResult = globalOrchestrationMutex.withLock {
    val localRecovery = submissionGate.withExclusiveBoundary {
      dao.reconstructConsumedBarriers(timeSource.wallMillis())
    }
    if (localRecovery > 0) {
      return@withLock result("SUBMISSION_OUTCOME_UNKNOWN", false)
    }
    val binding = ensureRegisteredBinding()
      ?: return@withLock result("SENDER_REGISTRATION_UNAVAILABLE", true)
    if (!ensureLease(binding, CoordinationPurpose.TEST)) {
      return@withLock result("TEST_LEASE_UNAVAILABLE", true)
    }
    val test = dao.testJob(testJobId)
      ?: return@withLock result("TEST_JOB_UNAVAILABLE", false)
    if (
      test.installationId != binding.installationId ||
      test.senderEpoch != binding.senderEpoch ||
      test.foregroundConfirmationNonceHash != foregroundConfirmationNonceHash
    ) return@withLock result("TEST_BINDING_INVALID", false)
    val existing = findPermit(OperationPurpose.TEST, testJobId)
    val permit = existing ?: claimTest(binding, testJobId)
      ?: return@withLock result("TEST_CLAIM_UNAVAILABLE", true)
    advancePermit(permit, foregroundConfirmationNonceHash)
  }

  private suspend fun reconcileSerial(
    @Suppress("UNUSED_PARAMETER") trigger: ReconciliationTrigger,
  ): AutomationReconcileResult {
    val wallNow = timeSource.wallMillis()
    val reconstructed = submissionGate.withExclusiveBoundary {
      dao.reconstructConsumedBarriers(wallNow)
    }
    if (reconstructed > 0) return result("SUBMISSION_OUTCOME_UNKNOWN", false)

    closeBootLostArmReconciling()
    val account = dao.activeAccount() ?: return result("ACCOUNT_NOT_CONNECTED", false)
    val binding = ensureRegisteredBinding()
    auditWallClock(account.accountId)
    val trustedNow = TrustedTimeEstimator.estimate(
      dao.clockTrust(account.accountId),
      timeSource.elapsedRealtimeMillis(),
      timeSource.bootCount(),
    )
    var attentionSafeCode: String? = null
    if (trustedNow != null) {
      extendLiveResetFence(account.accountId, trustedNow)
      val expired = submissionGate.withExclusiveBoundary {
        dao.expireUnarmedBirthdays(trustedNow, timeSource.wallMillis())
      }
      if (expired > 0) attentionSafeCode = "BIRTHDAY_MISSED"
      planBirthdayOccurrences(trustedNow)
    }
    fun withAttention(result: AutomationReconcileResult): AutomationReconcileResult =
      if (attentionSafeCode == null) result else result.copy(attentionSafeCode = attentionSafeCode)

    val recoverable = dao.recoverablePermits(1).firstOrNull()
    if (recoverable != null) {
      if (binding == null) return withAttention(result("SENDER_REGISTRATION_UNAVAILABLE", true))
      ensureLease(binding, recoverable.purpose.toCoordinationPurpose())
      return withAttention(advancePermit(recoverable, null))
    }

    if (trustedNow == null) return withAttention(result("CLOCK_TRUST_UNAVAILABLE", true))
    if (binding == null) return withAttention(result("SENDER_REGISTRATION_UNAVAILABLE", true))
    if (binding.accountMode != AccountMode.AUTOMATION_ACTIVE) {
      val code = when (binding.accountMode) {
        AccountMode.TRANSFER_PENDING -> "SENDER_TRANSFER_PENDING"
        AccountMode.DELETING -> "ACCOUNT_DELETING"
        AccountMode.STANDBY -> "ACTIVE_SENDER_OTHER_DEVICE"
        else -> "AUTOMATION_NOT_ACTIVE"
      }
      return withAttention(result(code, false, nextWake(trustedNow)))
    }
    if (!ensureLease(binding, CoordinationPurpose.BIRTHDAY)) {
      return withAttention(result("BIRTHDAY_LEASE_UNAVAILABLE", true, nextWake(trustedNow)))
    }
    val due = dao.nextDueBirthday(trustedNow)
      ?: return withAttention(result("RECONCILE_IDLE", false, nextWake(trustedNow)))
    val permit = claimBirthday(binding, due, trustedNow)
      ?: return withAttention(result("BIRTHDAY_CLAIM_PENDING", true))
    return withAttention(advancePermit(permit, null))
  }

  private suspend fun ensureRegisteredBinding(
    allowCachedOnUnavailable: Boolean = true,
    acceptAuthoritativeStandby: Boolean = false,
  ): InstallationBindingEntity? {
    val account = dao.activeAccount() ?: return null
    if (!identitySessionMatches(account.accountId)) return null
    val identity = installationIdentityStore.getOrCreate() ?: return null
    val now = timeSource.wallMillis()
    val candidate = InstallationBindingEntity(
      installationId = identity.installationId,
      accountId = account.accountId,
      localSlot = 1,
      callbackGeneration = identity.callbackGeneration,
      state = InstallationRecordState.STANDBY,
      accountMode = AccountMode.STANDBY,
      senderEpoch = null,
      resetGeneration = 1,
      ownerLeaseUntilMillis = null,
      appVersionCode = BuildConfig.VERSION_CODE.toLong(),
      distributionChannel = distributionChannel().name,
      signingCertificateSha256 = BuildConfig.APPROVED_SIGNING_CERTIFICATE_SHA256
        .ifBlank { "unverified" },
      lastVerifiedServerMillis = null,
      revision = 0,
      createdAtMillis = now,
      updatedAtMillis = now,
    )
    val local = submissionGate.withExclusiveBoundary { dao.ensureLocalInstallation(candidate) }
      ?: return null
    if (!networkValidated()) {
      return local.takeIf(::isRegisteredLocalBinding).takeIf { allowCachedOnUnavailable }
    }

    val registration = coordination.register(
      RegistrationSpec(
        ledgerGeneration = LEDGER_GENERATION,
        installationId = local.installationId,
        appBuildNumber = BuildConfig.VERSION_CODE,
        policyVersion = COORDINATION_POLICY_VERSION,
        distributionChannel = distributionChannel(),
      ),
    )
    return when (registration) {
      is OrchestrationCall.Unavailable -> {
        dao.recordSafeCode(account.accountId, registration.safeCode, now)
        local.takeIf(::isRegisteredLocalBinding).takeIf { allowCachedOnUnavailable }
      }
      is OrchestrationCall.Authoritative -> when (val outcome = registration.value) {
        is RegistrationOutcome.Suppressed -> {
          dao.recordSafeCode(account.accountId, "REGISTRATION_${outcome.reason.name}", now)
          null
        }
        is RegistrationOutcome.Registered -> applyRegistration(account.accountId, local, outcome)
          ?.takeIf { row ->
            acceptAuthoritativeStandby || isRegisteredLocalBinding(row)
          }
      }
    }
  }

  private suspend fun applyRegistration(
    accountId: String,
    local: InstallationBindingEntity,
    outcome: RegistrationOutcome.Registered,
  ): InstallationBindingEntity? {
    val binding = outcome.binding
    val serverMode = binding.mode.toLocalMode()
    val active = outcome.installationState == ServerInstallationState.ACTIVE
    val localState = if (active) InstallationRecordState.ACTIVE else InstallationRecordState.STANDBY
    val localMode = when {
      active -> serverMode
      serverMode == AccountMode.TRANSFER_PENDING &&
        binding.transferTargetInstallationId == local.installationId -> AccountMode.TRANSFER_PENDING
      else -> AccountMode.STANDBY
    }
    val epoch = outcome.installationEpoch.takeIf { active }
    val existingCoordination = dao.coordinationState(accountId)
    val now = timeSource.wallMillis()
    val zone = ZoneId.systemDefault()
    val civilDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val resetId = AutomationOpaqueIds.prefixed(
      "reset",
      "ResetSafety.v1",
      accountId,
      binding.resetGeneration.toString(),
    )
    val releaseAfter = resetReleaseAfter(civilDate, binding.birthdayAutomationNotBeforeMillis)
      ?: return null
    val reset = ResetSafetyEntity(
      resetSafetyId = resetId,
      accountId = accountId,
      resetGeneration = binding.resetGeneration,
      resetAtMillis = binding.serverObservedAtMillis,
      resetLocalDate = civilDate.toString(),
      resetTimeZoneId = zone.id,
      birthdayAutomationNotBeforeMillis = binding.birthdayAutomationNotBeforeMillis,
      status = ResetSafetyStatus.BLOCKED,
      overflowBlocked = false,
      revision = 0,
      updatedAtMillis = now,
    )
    val blockedDate = ResetBlockedDateEntity(
      blockedDateId = AutomationOpaqueIds.prefixed(
        "date",
        "ResetBlockedDate.v1",
        resetId,
        civilDate.toString(),
      ),
      resetSafetyId = resetId,
      civilDate = civilDate.toString(),
      releaseAfterTrustedServerMillis = releaseAfter,
      observedAtMillis = now,
    )
    val coordinationState = CoordinationStateEntity(
      accountId = accountId,
      mode = serverMode,
      activeInstallationId = binding.activeInstallationId,
      senderEpoch = binding.senderEpoch,
      resetGeneration = binding.resetGeneration,
      continuityGeneration = existingCoordination?.continuityGeneration ?: 1,
      ownerLeaseUntilMillis = binding.ownerLeaseUntilMillis,
      nextArmNotBeforeMillis = binding.nextArmNotBeforeMillis,
      latestIssuedSubmitNotAfterMillis = binding.latestIssuedSubmitNotAfterMillis,
      birthdayAutomationNotBeforeMillis = binding.birthdayAutomationNotBeforeMillis,
      transferDrainUntilMillis = binding.transferDrainUntilMillis,
      deletionDrainUntilMillis = binding.deletionDrainUntilMillis,
      lastSuccessfulCoordinationMillis = binding.serverObservedAtMillis,
      lastSafeCode = null,
      revision = (existingCoordination?.revision ?: -1) + 1,
      updatedAtMillis = now,
    )
    val applied = submissionGate.withExclusiveBoundary {
      val success = dao.applyRegistration(
        accountId = accountId,
        installationId = local.installationId,
        installationState = localState,
        serverMode = serverMode,
        localMode = localMode,
        senderEpoch = epoch,
        resetGeneration = binding.resetGeneration,
        ownerLeaseUntilMillis = binding.ownerLeaseUntilMillis.takeIf { active },
        serverNowMillis = binding.serverObservedAtMillis,
        coordination = coordinationState,
        reset = reset,
        blockedDate = blockedDate,
        deviceWallMillis = now,
      )
      if (!success) return@withExclusiveBoundary false
      dao.releaseExpiredResetDates(accountId, binding.serverObservedAtMillis, now)
      observeServerTime(accountId, binding.serverObservedAtMillis)
      true
    }
    return if (applied) dao.localInstallation() else null
  }

  private suspend fun observeServerTime(accountId: String, serverNowMillis: Long): Boolean {
    val wall = timeSource.wallMillis()
    val elapsed = timeSource.elapsedRealtimeMillis()
    val boot = timeSource.bootCount() ?: return false
    val drift = subtractExactOrNull(wall, serverNowMillis) ?: return false
    val current = dao.clockTrust(accountId)
    if (current == null) {
      return dao.insertClockTrustIfAbsent(
        ClockTrustEntity(
          accountId = accountId,
          status = if (safeAbsolute(drift) <= CLOCK_TOLERANCE_MILLIS) {
            ClockTrustStatus.TRUSTED
          } else {
            ClockTrustStatus.DRIFTED
          },
          greatestTrustedServerMillis = serverNowMillis,
          lastDeviceWallMillis = wall,
          lastElapsedRealtimeMillis = elapsed,
          trustedBootCount = boot,
          lastVerificationMillis = wall,
          observedDriftMillis = drift,
          revision = 0,
        ),
      ) != -1L
    }
    if (current.status != ClockTrustStatus.TRUSTED) return false
    if (safeAbsolute(drift) > CLOCK_TOLERANCE_MILLIS) {
      val control = dao.control() ?: return false
      return ledger.applyBlockingClockStatus(
        accountId,
        current.revision,
        control.revision,
        ClockTrustStatus.DRIFTED,
        wall,
        wall,
        drift,
      )
    }
    return ledger.persistBenignTrustedClockObservation(
      accountId,
      serverNowMillis,
      wall,
      elapsed,
      boot,
      wall,
    )
  }

  private suspend fun auditWallClock(accountId: String) {
    val trust = dao.clockTrust(accountId) ?: return
    val estimated = TrustedTimeEstimator.estimate(
      trust,
      timeSource.elapsedRealtimeMillis(),
      timeSource.bootCount(),
    ) ?: return
    val drift = subtractExactOrNull(timeSource.wallMillis(), estimated) ?: return
    if (safeAbsolute(drift) <= CLOCK_TOLERANCE_MILLIS) return
    val control = dao.control() ?: return
    submissionGate.withExclusiveBoundary {
      ledger.applyBlockingClockStatus(
        accountId,
        trust.revision,
        control.revision,
        ClockTrustStatus.DRIFTED,
        timeSource.wallMillis(),
        timeSource.wallMillis(),
        drift,
      )
    }
  }

  private suspend fun extendLiveResetFence(accountId: String, trustedNowMillis: Long) {
    val reset = dao.resetSafety(accountId) ?: return
    if (
      reset.status == ResetSafetyStatus.CLEAR &&
      trustedNowMillis >= reset.birthdayAutomationNotBeforeMillis
    ) return
    val control = dao.control() ?: return
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timeSource.wallMillis()).atZone(zone).toLocalDate()
    val releaseAfter = resetReleaseAfter(date, reset.birthdayAutomationNotBeforeMillis) ?: return
    val blocked = ResetBlockedDateEntity(
      blockedDateId = AutomationOpaqueIds.prefixed(
        "date",
        "ResetBlockedDate.v1",
        reset.resetSafetyId,
        date.toString(),
      ),
      resetSafetyId = reset.resetSafetyId,
      civilDate = date.toString(),
      releaseAfterTrustedServerMillis = releaseAfter,
      observedAtMillis = timeSource.wallMillis(),
    )
    submissionGate.withExclusiveBoundary {
      ledger.addResetBlockedDate(
        accountId,
        blocked,
        control.revision,
        timeSource.wallMillis(),
      )
    }
  }

  private suspend fun ensureLease(
    binding: InstallationBindingEntity,
    purpose: CoordinationPurpose,
  ): Boolean {
    val epoch = binding.senderEpoch ?: return false
    if (binding.state != InstallationRecordState.ACTIVE) return false
    val account = dao.activeAccount() ?: return false
    val trust = dao.clockTrust(account.accountId)
    val trustedNow = TrustedTimeEstimator.estimate(
      trust,
      timeSource.elapsedRealtimeMillis(),
      timeSource.bootCount(),
    ) ?: return false
    val coordinationState = dao.coordinationState(account.accountId) ?: return false
    val leaseUntil = minOf(
      binding.ownerLeaseUntilMillis ?: Long.MIN_VALUE,
      coordinationState.ownerLeaseUntilMillis ?: Long.MIN_VALUE,
    )
    val leaseRemaining = subtractExactOrNull(leaseUntil, trustedNow) ?: Long.MIN_VALUE
    if (leaseRemaining > LEASE_RENEW_MARGIN_MILLIS) return true
    if (!leasePreflightReady(binding, purpose) || !networkValidated()) return false
    val spec = bindingSpec(binding)
    return when (val call = coordination.renewLease(spec, purpose)) {
      is OrchestrationCall.Unavailable -> {
        dao.recordSafeCode(account.accountId, call.safeCode, timeSource.wallMillis())
        false
      }
      is OrchestrationCall.Authoritative -> when (val outcome = call.value) {
        is LeaseOutcome.Refused -> {
          dao.recordSafeCode(
            account.accountId,
            "LEASE_${outcome.reason.name}",
            timeSource.wallMillis(),
          )
          false
        }
        is LeaseOutcome.Renewed -> submissionGate.withExclusiveBoundary {
          dao.persistRenewedLease(
            account.accountId,
            binding.installationId,
            epoch,
            outcome.leaseUntilMillis,
            trustedNow,
            timeSource.wallMillis(),
          )
        }
      }
    }
  }

  private suspend fun leasePreflightReady(
    binding: InstallationBindingEntity,
    purpose: CoordinationPurpose,
  ): Boolean {
    val account = dao.activeAccount() ?: return false
    if (!identitySessionMatches(account.accountId)) return false
    val snapshot = readinessProbe.read()
    val sync = dao.contactSyncState(account.accountId)
    val reset = dao.resetSafety(account.accountId)
    val clock = dao.clockTrust(account.accountId)
    val base = snapshot.eligibility.kind == EligibilityKind.SUPPORTED &&
      snapshot.smsPermissionGranted == true &&
      snapshot.signals.simReady == true &&
      snapshot.signals.networkValidated == true &&
      clock?.status == ClockTrustStatus.TRUSTED &&
      binding.state == InstallationRecordState.ACTIVE
    if (!base) return false
    if (purpose == CoordinationPurpose.TEST) {
      return binding.accountMode in setOf(AccountMode.TEST_ONLY, AccountMode.PAUSED_REPAIR)
    }
    return binding.accountMode == AccountMode.AUTOMATION_ACTIVE &&
      sync?.freshness in setOf(SyncFreshness.FRESH, SyncFreshness.STALE_WARNING) &&
      reset?.status == ResetSafetyStatus.CLEAR &&
      !reset.overflowBlocked &&
      snapshot.signals.backgroundRestricted == false &&
      snapshot.signals.dozeAllowlisted == true &&
      snapshot.signals.unusedAppRestrictionsDisabled == true &&
      snapshot.signals.dataSaverAllowsBackground == true &&
      snapshot.signals.lowPowerStandbySafe == true
  }

  private suspend fun planBirthdayOccurrences(trustedNowMillis: Long) {
    val zoneNow = Instant.ofEpochMilli(trustedNowMillis).atZone(ZoneId.systemDefault())
    val start = zoneNow.toLocalDate()
    val end = start.plusDays(PLANNING_HORIZON_DAYS)
    val currentMonthDay = start.monthValue * 100 + start.dayOfMonth
    var hasCursor = false
    var afterUpcomingBucket = 0
    var afterMonthDay = 0
    var afterContactId = ""
    while (true) {
      val seeds = dao.planningSeeds(
        horizonStartDate = start.toString(),
        horizonEndDate = end.toString(),
        currentTimeZoneId = ZoneId.systemDefault().id,
        currentMonthDay = currentMonthDay,
        hasCursor = hasCursor,
        afterUpcomingBucket = afterUpcomingBucket,
        afterMonthDay = afterMonthDay,
        afterContactId = afterContactId,
        limit = PLANNING_PAGE_SIZE,
      )
      if (seeds.isEmpty()) return
      seeds.forEach { seed ->
        val rule = birthdayRule(seed) ?: return@forEach
        for (year in start.year..end.year) {
          val date = try {
            recurrencePlanner.occurrenceInYear(year, rule)
          } catch (_: IllegalArgumentException) {
            null
          } ?: continue
          if (date.isBefore(start) || date.isAfter(end)) continue
          createOccurrenceIfAbsent(seed, date, trustedNowMillis)
        }
      }
      val last = seeds.last()
      val lastMonthDay = last.birthdayMonth * 100 + last.birthdayDay
      afterUpcomingBucket = if (lastMonthDay >= currentMonthDay) 0 else 1
      afterMonthDay = lastMonthDay
      afterContactId = last.contactId
      hasCursor = true
      // Planning keeps only one bounded page in memory and remains cancellation-cooperative for
      // accounts larger than the People API page size.
      if (seeds.size < PLANNING_PAGE_SIZE) return
      yield()
    }
  }

  private suspend fun createOccurrenceIfAbsent(
    seed: BirthdayPlanningSeed,
    date: LocalDate,
    trustedNowMillis: Long,
  ) {
    val zone = ZoneId.systemDefault()
    val endMinute = seed.graceEndMinute ?: seed.windowEndMinute
    if (
      seed.windowStartMinute !in 0 until MINUTES_PER_DAY ||
      seed.windowEndMinute !in 1..MINUTES_PER_DAY ||
      endMinute !in 1..MINUTES_PER_DAY ||
      endMinute <= seed.windowStartMinute
    ) return
    val start = resolveMinute(date, seed.windowStartMinute, zone) ?: return
    val end = resolveMinute(date, endMinute, zone) ?: return
    val startMillis = start.toInstant().toEpochMilli()
    val endMillis = end.toInstant().toEpochMilli()
    if (endMillis <= startMillis) return
    val localDate = date.toString()
    val occurrenceId = AutomationOpaqueIds.prefixed(
      "occ",
      "BirthdayOccurrence.v1",
      seed.accountId,
      seed.contactId,
      localDate,
      SMS_CHANNEL,
    )
    val existing = ledger.getBirthdayOccurrence(occurrenceId)
    if (existing != null) {
      if (
        existing.approvalId == seed.approvalId &&
        existing.policyId == seed.policyId &&
        existing.payloadHash == seed.payloadHash &&
        existing.destinationFingerprint == seed.destinationFingerprint
      ) {
        dao.replanUnclaimedOccurrence(
          occurrenceId,
          zone.id,
          startMillis,
          endMillis,
          trustedNowMillis,
        )
      } else {
        try {
          dao.replaceUnclaimedOccurrenceMaterial(
            occurrenceId = occurrenceId,
            approvalId = seed.approvalId,
            policyId = seed.policyId,
            timeZoneId = zone.id,
            windowStartMillis = startMillis,
            windowEndMillis = endMillis,
            destinationFingerprint = seed.destinationFingerprint,
            payloadHash = seed.payloadHash,
            updatedAtMillis = trustedNowMillis,
          )
        } catch (_: RuntimeException) {
          // A destination uniqueness conflict leaves both occurrences blocked for explicit repair.
        }
      }
      return
    }
    val retention = try {
      Math.addExact(endMillis, TimeUnit.DAYS.toMillis(BIRTHDAY_RETENTION_DAYS))
    } catch (_: ArithmeticException) {
      return
    }
    val occurrence = BirthdayOccurrenceRecordEntity(
      occurrenceId = occurrenceId,
      accountId = seed.accountId,
      contactId = seed.contactId,
      approvalId = seed.approvalId,
      policyId = seed.policyId,
      localDate = localDate,
      timeZoneId = zone.id,
      resolvedWindowStartMillis = startMillis,
      resolvedWindowEndMillis = endMillis,
      idempotencyKey = AutomationOpaqueIds.sha256(
        "BirthdayIdempotency.v1",
        seed.accountId,
        seed.contactId,
        localDate,
        SMS_CHANNEL,
      ),
      destinationFingerprint = seed.destinationFingerprint,
      channel = SMS_CHANNEL,
      payloadHash = seed.payloadHash,
      state = BirthdayJobState.PLANNED,
      attemptNumber = 0,
      revision = 0,
      claimedBlockerRevision = null,
      createdAtMillis = trustedNowMillis,
      updatedAtMillis = trustedNowMillis,
      terminalAtMillis = null,
      retentionUntilMillis = retention,
      safeOutcomeCode = null,
    )
    val guard = LocalDestinationGuardEntity(
      guardId = AutomationOpaqueIds.prefixed(
        "guard",
        "LocalDestinationGuard.v1",
        seed.accountId,
        seed.destinationFingerprint,
        localDate,
        SMS_CHANNEL,
      ),
      accountId = seed.accountId,
      occurrenceId = occurrenceId,
      destinationFingerprint = seed.destinationFingerprint,
      localDate = localDate,
      channel = SMS_CHANNEL,
      armedOrLater = false,
      createdAtMillis = trustedNowMillis,
      armedAtMillis = null,
      retentionUntilMillis = retention,
    )
    try {
      ledger.createPlannedBirthdayOccurrence(occurrence, guard)
      dao.scheduleNewOccurrence(occurrenceId, trustedNowMillis)
    } catch (_: RuntimeException) {
      // A concurrent deterministic insert is harmless; all other failures remain fail-closed.
    }
  }

  private suspend fun claimBirthday(
    binding: InstallationBindingEntity,
    due: BirthdayOccurrenceRecordEntity,
    trustedNowMillis: Long,
  ): CoordinationPermitEntity? {
    if (due.state == BirthdayJobState.SCHEDULED) {
      val claimed = submissionGate.withExclusiveBoundary {
        ledger.claimBirthdayOccurrence(due.occurrenceId, due.revision, trustedNowMillis)
      }
      if (!claimed) return null
    } else if (due.state != BirthdayJobState.CLAIMED) {
      return null
    }
    val claimed = ledger.getBirthdayOccurrence(due.occurrenceId) ?: return null
    val material = dao.claimMaterial(due.occurrenceId) ?: return null
    val requestId = AutomationOpaqueIds.uuid(
      "BirthdayClaimRequest.v1",
      due.accountId,
      due.occurrenceId,
    )
    val startElapsed = timeSource.elapsedRealtimeMillis()
    val boot = timeSource.bootCount() ?: return null
    val call = coordination.claimBirthday(
      BirthdayClaimSpec(
        binding = bindingSpec(binding),
        claimRequestId = requestId,
        recipientPrehashAliases = listOf(
          AutomationOpaqueIds.sha256(
            "BirthdayRecipientBasis.v1",
            material.sourceFingerprint,
            material.localDate,
            material.channel,
          ),
        ),
        destinationPrehashAliases = listOf(
          AutomationOpaqueIds.sha256(
            "BirthdayDestinationBasis.v1",
            material.normalizedPhoneE164,
            material.localDate,
            material.channel,
          ),
        ),
      ),
    )
    return when (call) {
      is OrchestrationCall.Unavailable -> {
        dao.recordSafeCode(due.accountId, call.safeCode, timeSource.wallMillis())
        null
      }
      is OrchestrationCall.Authoritative -> when (val outcome = call.value) {
        is ClaimOutcome.Refused -> {
          if (!outcome.reason.isTransientClaimRefusal()) {
            submissionGate.withExclusiveBoundary {
              dao.terminalizeUnclaimedBirthday(
                due.occurrenceId,
                BirthdayJobState.CANCELLED,
                "CLAIM_${outcome.reason.name}",
                timeSource.wallMillis(),
              )
            }
          }
          null
        }
        is ClaimOutcome.Accepted -> {
          val cloud = outcome.claim
          if (
            cloud.state != ServerClaimState.CLAIMED ||
            cloud.attempt != 1 ||
            cloud.ownerInstallationId != binding.installationId ||
            cloud.ownerEpoch != binding.senderEpoch ||
            cloud.resetGeneration != binding.resetGeneration
          ) return null
          val now = timeSource.wallMillis()
          val permit = CoordinationPermitEntity(
            permitId = AutomationOpaqueIds.prefixed(
              "permit",
              "CoordinationPermit.v1",
              OperationPurpose.BIRTHDAY.name,
              due.occurrenceId,
              "1",
            ),
            accountId = due.accountId,
            installationId = binding.installationId,
            senderEpoch = checkNotNull(binding.senderEpoch),
            resetGeneration = binding.resetGeneration,
            purpose = OperationPurpose.BIRTHDAY,
            operationId = due.occurrenceId,
            attemptNumber = 1,
            payloadHash = due.payloadHash,
            opaqueClaimId = cloud.claimId,
            opaqueDestinationGuardId = null,
            claimRequestId = requestId,
            armRequestId = null,
            state = CoordinationPermitState.CLOUD_CLAIMED,
            armDispatched = false,
            armStartBlockerRevision = null,
            claimExpiresAtMillis = cloud.claimExpiresAtMillis,
            maxPossibleSubmitNotAfterMillis = cloud.maxPossibleSubmitNotAfterMillis,
            unresolvedArmCutoffMillis = minOf(
              cloud.maxPossibleSubmitNotAfterMillis,
              claimed.resolvedWindowEndMillis,
            ),
            trustedServerNowMillis = cloud.serverObservedAtMillis,
            requestStartElapsedMillis = startElapsed,
            bootCount = boot,
            serverSubmitNotAfterMillis = null,
            effectiveSubmitNotAfterMillis = null,
            noWriteReason = null,
            revision = 0,
            createdAtMillis = now,
            updatedAtMillis = now,
            barrierConsumedAtMillis = null,
            retentionUntilMillis = due.retentionUntilMillis,
          )
          val stored = submissionGate.withExclusiveBoundary {
            observeServerTime(due.accountId, cloud.serverObservedAtMillis)
            ledger.recordCloudClaim(permit)
          }
          if (stored) ledger.getCoordinationPermit(permit.permitId) else null
        }
      }
    }
  }

  private suspend fun claimTest(
    binding: InstallationBindingEntity,
    testJobId: String,
  ): CoordinationPermitEntity? {
    val test = dao.testJob(testJobId) ?: return null
    val startElapsed = timeSource.elapsedRealtimeMillis()
    val boot = timeSource.bootCount() ?: return null
    val call = coordination.claimTest(
      TestClaimSpec(
        binding = bindingSpec(binding),
        testRequestId = test.testRequestId,
        configurationPrehash = test.configHash,
        destinationPrehash = test.destinationPrehash,
      ),
    )
    val accepted = (call as? OrchestrationCall.Authoritative)?.value as? ClaimOutcome.Accepted
      ?: return null
    val cloud = accepted.claim
    if (
      cloud.state != ServerClaimState.CLAIMED ||
      cloud.attempt != 1 ||
      cloud.ownerInstallationId != binding.installationId ||
      cloud.ownerEpoch != binding.senderEpoch ||
      cloud.resetGeneration != binding.resetGeneration
    ) return null
    val now = timeSource.wallMillis()
    val permit = CoordinationPermitEntity(
      permitId = AutomationOpaqueIds.prefixed(
        "permit",
        "CoordinationPermit.v1",
        OperationPurpose.TEST.name,
        test.testJobId,
        "1",
      ),
      accountId = test.accountId,
      installationId = binding.installationId,
      senderEpoch = checkNotNull(binding.senderEpoch),
      resetGeneration = binding.resetGeneration,
      purpose = OperationPurpose.TEST,
      operationId = test.testJobId,
      attemptNumber = 1,
      payloadHash = test.payloadHash,
      opaqueClaimId = cloud.claimId,
      opaqueDestinationGuardId = null,
      claimRequestId = test.testRequestId,
      armRequestId = null,
      state = CoordinationPermitState.CLOUD_CLAIMED,
      armDispatched = false,
      armStartBlockerRevision = null,
      claimExpiresAtMillis = cloud.claimExpiresAtMillis,
      maxPossibleSubmitNotAfterMillis = cloud.maxPossibleSubmitNotAfterMillis,
      unresolvedArmCutoffMillis = cloud.maxPossibleSubmitNotAfterMillis,
      trustedServerNowMillis = cloud.serverObservedAtMillis,
      requestStartElapsedMillis = startElapsed,
      bootCount = boot,
      serverSubmitNotAfterMillis = null,
      effectiveSubmitNotAfterMillis = null,
      noWriteReason = null,
      revision = 0,
      createdAtMillis = now,
      updatedAtMillis = now,
      barrierConsumedAtMillis = null,
      retentionUntilMillis = test.retentionUntilMillis,
    )
    val stored = submissionGate.withExclusiveBoundary {
      observeServerTime(test.accountId, cloud.serverObservedAtMillis)
      ledger.recordCloudClaim(permit)
    }
    return if (stored) ledger.getCoordinationPermit(permit.permitId) else null
  }

  private suspend fun advancePermit(
    original: CoordinationPermitEntity,
    foregroundConfirmationNonceHash: String?,
  ): AutomationReconcileResult {
    var permit = ledger.getCoordinationPermit(original.permitId) ?: original
    val action = ArmRecoveryPolicy.decide(
      permit,
      timeSource.bootCount(),
      timeSource.elapsedRealtimeMillis(),
    )
    if (action == ArmRecoveryAction.CLOSE_UNKNOWN) {
      closeUnknown(permit, "ARM_ANCHOR_UNTRUSTED")
      return result("ARM_COORDINATION_UNKNOWN", false, operationKey = permit.permitId)
    }
    if (!networkValidated()) {
      if (action == ArmRecoveryAction.QUERY_EXACT_STATUS_THEN_CLOSE) {
        closeUnknown(permit, "ARM_STATUS_UNAVAILABLE_AT_CUTOFF")
        return result("ARM_COORDINATION_UNKNOWN", false, operationKey = permit.permitId)
      }
      return result("COORDINATION_NETWORK_UNAVAILABLE", true, operationKey = permit.permitId)
    }
    if (action == ArmRecoveryAction.DISPATCH_ONCE) {
      val trustedNow = TrustedTimeEstimator.estimate(
        dao.clockTrust(permit.accountId),
        timeSource.elapsedRealtimeMillis(),
        timeSource.bootCount(),
      ) ?: return result("CLOCK_TRUST_UNAVAILABLE", true, operationKey = permit.permitId)
      val armRequestId = AutomationOpaqueIds.uuid(
        "ArmRequest.v1",
        permit.permitId,
        permit.attemptNumber.toString(),
      )
      val dispatch = submissionGate.withExclusiveBoundary {
        ledger.beginArmDispatch(
          permit.permitId,
          permit.revision,
          armRequestId,
          trustedNow,
          timeSource.elapsedRealtimeMillis(),
          timeSource.bootCount(),
        )
      }
      if (dispatch !is com.yashsomani.birthdayautopilot.storage.database.ArmDispatchResult.Committed) {
        return result("ARM_LOCAL_DISPATCH_REJECTED", false, operationKey = permit.permitId)
      }
      permit = ledger.getCoordinationPermit(permit.permitId) ?: return result(
        "ARM_PERMIT_LOST",
        false,
      )
      val call = coordination.arm(armSpec(permit))
      return handleArmDecision(permit, call, foregroundConfirmationNonceHash)
    }
    if (action in setOf(
        ArmRecoveryAction.QUERY_EXACT_STATUS,
        ArmRecoveryAction.QUERY_EXACT_STATUS_THEN_CLOSE,
      )
    ) {
      val call = coordination.getArmStatus(armSpec(permit))
      return handleArmStatus(
        permit,
        call,
        foregroundConfirmationNonceHash,
        closeIfUnresolved = action == ArmRecoveryAction.QUERY_EXACT_STATUS_THEN_CLOSE,
      )
    }
    return result("PERMIT_NOT_ACTIONABLE", false, operationKey = permit.permitId)
  }

  private suspend fun handleArmDecision(
    permit: CoordinationPermitEntity,
    call: OrchestrationCall<ArmDecisionOutcome>,
    foregroundConfirmationNonceHash: String?,
  ): AutomationReconcileResult = when (call) {
    is OrchestrationCall.Unavailable -> result(
      call.safeCode,
      true,
      operationKey = permit.permitId,
    )
    is OrchestrationCall.Authoritative -> when (val value = call.value) {
      is ArmDecisionOutcome.Armed -> consumeArmed(
        permit,
        value.outcome,
        foregroundConfirmationNonceHash,
      )
      is ArmDecisionOutcome.NoWrite -> recordNoWrite(permit, value.outcome)
      is ArmDecisionOutcome.Replayed -> when (val outcome = value.outcome) {
        is AuthoritativeArmOutcome.Armed -> consumeArmed(
          permit,
          outcome,
          foregroundConfirmationNonceHash,
        )
        is AuthoritativeArmOutcome.NoWrite -> recordNoWrite(permit, outcome)
      }
      is ArmDecisionOutcome.Suppressed -> {
        closeUnknown(permit, "ARM_${value.reason.name}")
        result("ARM_COORDINATION_UNKNOWN", false, operationKey = permit.permitId)
      }
    }
  }

  private suspend fun handleArmStatus(
    permit: CoordinationPermitEntity,
    call: OrchestrationCall<ArmStatusOutcome>,
    foregroundConfirmationNonceHash: String?,
    closeIfUnresolved: Boolean,
  ): AutomationReconcileResult {
    if (call is OrchestrationCall.Unavailable) {
      if (closeIfUnresolved) closeUnknown(permit, "ARM_STATUS_UNAVAILABLE_AT_CUTOFF")
      return result(
        if (closeIfUnresolved) "ARM_COORDINATION_UNKNOWN" else call.safeCode,
        !closeIfUnresolved,
        operationKey = permit.permitId,
      )
    }
    return when (val value = (call as OrchestrationCall.Authoritative).value) {
      ArmStatusOutcome.Unknown -> {
        if (closeIfUnresolved) closeUnknown(permit, "ARM_STATUS_UNKNOWN_AT_CUTOFF")
        result(
          if (closeIfUnresolved) "ARM_COORDINATION_UNKNOWN" else "ARM_STATUS_PENDING",
          !closeIfUnresolved,
          operationKey = permit.permitId,
        )
      }
      is ArmStatusOutcome.NoWrite -> recordNoWrite(permit, value.outcome)
      is ArmStatusOutcome.Replayed -> when (val outcome = value.outcome) {
        is AuthoritativeArmOutcome.Armed -> consumeArmed(
          permit,
          outcome,
          foregroundConfirmationNonceHash,
        )
        is AuthoritativeArmOutcome.NoWrite -> recordNoWrite(permit, outcome)
      }
      is ArmStatusOutcome.Suppressed -> {
        closeUnknown(permit, "ARM_STATUS_${value.reason.name}")
        result("ARM_COORDINATION_UNKNOWN", false, operationKey = permit.permitId)
      }
    }
  }

  private suspend fun recordNoWrite(
    permit: CoordinationPermitEntity,
    outcome: AuthoritativeArmOutcome.NoWrite,
  ): AutomationReconcileResult {
    if (!outcome.matches(permit)) {
      closeUnknown(permit, "ARM_NO_WRITE_BINDING_INVALID")
      return result("ARM_COORDINATION_UNKNOWN", false, operationKey = permit.permitId)
    }
    val recorded = submissionGate.withExclusiveBoundary {
      observeServerTime(permit.accountId, outcome.resolvedAtMillis)
      ledger.recordAuthoritativeNoWrite(
        permit.permitId,
        permit.revision,
        outcome.armRequestId,
        outcome.reason.toLocalNoWriteReason(),
        timeSource.wallMillis(),
      )
    }
    return result(
      if (recorded) "ARM_AUTHORITATIVE_NO_WRITE" else "ARM_NO_WRITE_REJECTED",
      false,
      operationKey = permit.permitId,
    )
  }

  private suspend fun consumeArmed(
    permit: CoordinationPermitEntity,
    outcome: AuthoritativeArmOutcome.Armed,
    foregroundConfirmationNonceHash: String?,
  ): AutomationReconcileResult {
    if (!outcome.matches(permit)) {
      closeUnknown(permit, "ARMED_BINDING_INVALID")
      return result("ARM_COORDINATION_UNKNOWN", false, operationKey = permit.permitId)
    }
    val installation = dao.localInstallation()
      ?: return result("INSTALLATION_BINDING_UNAVAILABLE", false)
    val external = finalGateSource.snapshot(
      GatePermitReference(permit.purpose, permit.operationId, permit.payloadHash),
      foregroundConfirmationNonceHash,
    )
    val sendAttemptId = AutomationOpaqueIds.prefixed(
      "attempt",
      "SendAttempt.v1",
      permit.permitId,
      permit.attemptNumber.toString(),
    )
    val now = timeSource.wallMillis()
    val nextArmNotBefore = safeAdd(
      outcome.serverSubmitNotAfterMillis,
      ARM_SPACING_MILLIS,
    ) ?: return result("ARM_SPACING_OVERFLOW", false, operationKey = permit.permitId)
    val issue = submissionGate.withExclusiveBoundary {
      val fenceAdvanced = dao.advanceLocalArmFence(
        accountId = permit.accountId,
        installationId = permit.installationId,
        senderEpoch = permit.senderEpoch,
        serverSubmitNotAfterMillis = outcome.serverSubmitNotAfterMillis,
        nextArmNotBeforeMillis = nextArmNotBefore,
        serverNowMillis = outcome.resolvedAtMillis,
        deviceWallMillis = now,
      )
      if (fenceAdvanced != 1) {
        return@withExclusiveBoundary PermitIssueResult.Suppressed("arm-fence-binding-invalid")
      }
      ledger.consumeAuthoritativeArmedEvidence(
        permitId = permit.permitId,
        expectedPermitRevision = permit.revision,
        evidence = AuthoritativeArmedEvidence(
          armRequestId = outcome.armRequestId,
          serverNowMillis = outcome.resolvedAtMillis,
          serverSubmitNotAfterMillis = outcome.serverSubmitNotAfterMillis,
        ),
        external = external,
        deviceWallMillis = now,
        currentElapsedRealtimeMillis = timeSource.elapsedRealtimeMillis(),
        currentBootCount = timeSource.bootCount() ?: -1,
        sendAttemptId = sendAttemptId,
        callbackGeneration = installation.callbackGeneration,
        sentWatchdogAtMillis = safeAdd(now, SENT_WATCHDOG_MILLIS) ?: Long.MIN_VALUE,
        retentionUntilMillis = permit.retentionUntilMillis,
      )
    }
    if (issue !is PermitIssueResult.Issued) {
      return result("ARMED_SUPPRESSED", false, operationKey = permit.permitId)
    }
    // Same coroutine, after the network-free gated transaction. AndroidSmsGateway owns the sole
    // second gated boundary and the only SmsManager call site.
    return when (val submitted = smsGateway.submit(issue.permit)) {
      SmsSubmissionResult.Submitted -> result(
        "SMS_SUBMITTED",
        false,
        operationKey = permit.permitId,
      )
      is SmsSubmissionResult.OutcomeUnknown -> result(
        submitted.safeCode,
        false,
        operationKey = permit.permitId,
      )
      is SmsSubmissionResult.Refused -> {
        submissionGate.withExclusiveBoundary {
          dao.reconstructConsumedBarriers(timeSource.wallMillis())
        }
        result(submitted.safeCode, false, operationKey = permit.permitId)
      }
    }
  }

  private suspend fun closeBootLostArmReconciling() {
    val currentBootCount = timeSource.bootCount()
    val currentElapsedRealtimeMillis = timeSource.elapsedRealtimeMillis()
    var hasCursor = false
    var afterUpdatedAtMillis = Long.MIN_VALUE
    var afterPermitId = ""
    while (true) {
      val page = dao.bootLostArmReconcilingPermits(
        currentBootCount,
        currentElapsedRealtimeMillis,
        hasCursor,
        afterUpdatedAtMillis,
        afterPermitId,
        RECOVERY_PAGE_SIZE,
      )
      if (page.isEmpty()) return
      page.forEach { permit ->
        val action = ArmRecoveryPolicy.decide(
          permit,
          currentBootCount,
          currentElapsedRealtimeMillis,
        )
        if (action == ArmRecoveryAction.CLOSE_UNKNOWN) {
          closeUnknown(permit, "ARM_ANCHOR_UNTRUSTED")
        }
      }
      val last = page.last()
      afterUpdatedAtMillis = last.updatedAtMillis
      afterPermitId = last.permitId
      hasCursor = true
      if (page.size < RECOVERY_PAGE_SIZE) return
      yield()
    }
  }

  private suspend fun closeUnknown(
    permit: CoordinationPermitEntity,
    safeCode: String,
  ): Boolean = submissionGate.withExclusiveBoundary {
    dao.markCoordinationUnknown(
      permit.permitId,
      permit.revision,
      safeCode,
      timeSource.wallMillis(),
    )
  }

  private suspend fun findPermit(
    purpose: OperationPurpose,
    operationId: String,
  ): CoordinationPermitEntity? = dao.recoverablePermitForOperation(purpose, operationId)

  private fun armSpec(permit: CoordinationPermitEntity): ArmSpec = ArmSpec(
    binding = CoordinationBindingSpec(
      ledgerGeneration = LEDGER_GENERATION,
      installationId = permit.installationId,
      senderEpoch = permit.senderEpoch,
      resetGeneration = permit.resetGeneration,
      appBuildNumber = BuildConfig.VERSION_CODE,
      policyVersion = COORDINATION_POLICY_VERSION,
      distributionChannel = distributionChannel(),
    ),
    purpose = permit.purpose.toCoordinationPurpose(),
    claimId = permit.opaqueClaimId,
    armRequestId = checkNotNull(permit.armRequestId),
    attempt = permit.attemptNumber,
  )

  private fun bindingSpec(binding: InstallationBindingEntity) = CoordinationBindingSpec(
    ledgerGeneration = LEDGER_GENERATION,
    installationId = binding.installationId,
    senderEpoch = checkNotNull(binding.senderEpoch),
    resetGeneration = binding.resetGeneration,
    appBuildNumber = BuildConfig.VERSION_CODE,
    policyVersion = COORDINATION_POLICY_VERSION,
    distributionChannel = distributionChannel(),
  )

  private fun distributionChannel(): DistributionChannel = when (BuildConfig.APP_ENV) {
    "dev" -> DistributionChannel.DEV
    "staging" -> DistributionChannel.STAGING
    "lab" -> DistributionChannel.RESTRICTED_LAB
    "prod" -> if (BuildConfig.APPROVED_DISTRIBUTION_CHANNEL == "google-play") {
      DistributionChannel.PLAY
    } else {
      DistributionChannel.DIRECT_MANAGED
    }
    else -> DistributionChannel.DEV
  }

  private fun isRegisteredLocalBinding(row: InstallationBindingEntity): Boolean =
    row.state == InstallationRecordState.ACTIVE &&
      row.senderEpoch != null &&
      row.resetGeneration > 0

  private fun networkValidated(): Boolean = readinessProbe.read().signals.networkValidated == true

  private suspend fun nextWake(trustedNowMillis: Long): Long? {
    val local = dao.nextScheduledWindowMillis(trustedNowMillis)
    val server = dao.activeAccount()?.let { dao.coordinationState(it.accountId) }
      ?.nextArmNotBeforeMillis
    return listOfNotNull(local, server).maxOrNull()
  }

  private fun birthdayRule(seed: BirthdayPlanningSeed): BirthdayRule? {
    val leap = seed.leapDayPolicy?.let {
      try {
        LeapDayPolicy.valueOf(it)
      } catch (_: IllegalArgumentException) {
        return null
      }
    }
    return try {
      BirthdayRule(seed.birthdayMonth, seed.birthdayDay, leap).also {
        recurrencePlanner.occurrenceInYear(2024, it)
      }
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  private fun resolveMinute(date: LocalDate, minute: Int, zone: ZoneId): ZonedDateTime? {
    val normalizedDate = if (minute == MINUTES_PER_DAY) date.plusDays(1) else date
    val normalizedMinute = if (minute == MINUTES_PER_DAY) 0 else minute
    return try {
      normalizedDate.atTime(LocalTime.of(normalizedMinute / 60, normalizedMinute % 60)).atZone(zone)
    } catch (_: DateTimeException) {
      null
    }
  }

  private fun resetReleaseAfter(date: LocalDate, serverNotBefore: Long): Long? = try {
    val worstZoneEnd = date.plusDays(1)
      .atStartOfDay(ZoneOffset.ofHours(-12))
      .toInstant()
      .toEpochMilli()
    maxOf(
      serverNotBefore,
      Math.addExact(worstZoneEnd, CLOCK_TOLERANCE_MILLIS),
    )
  } catch (_: ArithmeticException) {
    null
  }

  private fun result(
    safeCode: String,
    retry: Boolean,
    nextWakeAtMillis: Long? = null,
    operationKey: String? = null,
  ) = AutomationReconcileResult(safeCode, retry, nextWakeAtMillis, operationKey)

  private fun OperationPurpose.toCoordinationPurpose(): CoordinationPurpose = when (this) {
    OperationPurpose.BIRTHDAY -> CoordinationPurpose.BIRTHDAY
    OperationPurpose.TEST -> CoordinationPurpose.TEST
  }

  private fun ServerAccountMode.toLocalMode(): AccountMode = when (this) {
    ServerAccountMode.TEST_ONLY -> AccountMode.TEST_ONLY
    ServerAccountMode.PAUSED_REPAIR -> AccountMode.PAUSED_REPAIR
    ServerAccountMode.AUTOMATION_ACTIVE -> AccountMode.AUTOMATION_ACTIVE
    ServerAccountMode.TRANSFER_PENDING -> AccountMode.TRANSFER_PENDING
    ServerAccountMode.DELETING -> AccountMode.DELETING
  }

  private fun AuthoritativeArmOutcome.matches(permit: CoordinationPermitEntity): Boolean =
    armRequestId == permit.armRequestId &&
      purpose == permit.purpose.toCoordinationPurpose() &&
      claimId == permit.opaqueClaimId &&
      ownerInstallationId == permit.installationId &&
      ownerEpoch == permit.senderEpoch &&
      resetGeneration == permit.resetGeneration &&
      attempt == permit.attemptNumber

  private fun CoordinationServerReason.toLocalNoWriteReason(): String = when (this) {
    CoordinationServerReason.EXPIRED -> "EXPIRED"
    CoordinationServerReason.EXPIRED_RETRY -> "EXPIRED_RETRY"
    CoordinationServerReason.MODE_BLOCKED,
    CoordinationServerReason.LEASE_EXPIRED,
    CoordinationServerReason.TOO_EARLY,
    -> "MODE_BLOCKED"
    CoordinationServerReason.EPOCH_MISMATCH,
    CoordinationServerReason.INSTALLATION_MISMATCH,
    -> "OLD_EPOCH"
    CoordinationServerReason.RESET_SUPPRESSED,
    CoordinationServerReason.RESET_GENERATION_MISMATCH,
    CoordinationServerReason.BIRTHDAY_RESET_FENCE,
    -> "RESET_SUPPRESSED"
    CoordinationServerReason.DELETION_SUPPRESSED -> "DELETION_SUPPRESSED"
    CoordinationServerReason.BUDGET_EXCEEDED -> "BUDGET_BLOCKED"
    CoordinationServerReason.OCCURRENCE_RESERVED,
    CoordinationServerReason.DESTINATION_RESERVED,
    CoordinationServerReason.CLAIM_STATE_MISMATCH,
    -> "GUARD_BLOCKED"
    else -> "POLICY_BLOCKED"
  }

  private fun CoordinationServerReason.isTransientClaimRefusal(): Boolean = this in setOf(
    CoordinationServerReason.LEASE_EXPIRED,
    CoordinationServerReason.TOO_EARLY,
  )

  private fun safeAdd(left: Long, right: Long): Long? = try {
    Math.addExact(left, right)
  } catch (_: ArithmeticException) {
    null
  }

  private fun subtractExactOrNull(left: Long, right: Long): Long? = try {
    Math.subtractExact(left, right)
  } catch (_: ArithmeticException) {
    null
  }

  private fun safeAbsolute(value: Long): Long = if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)

  private companion object {
    const val LEDGER_GENERATION = "birthday-ledger-v1"
    const val COORDINATION_POLICY_VERSION = 1
    const val SMS_CHANNEL = "SMS"
    const val MINUTES_PER_DAY = 24 * 60
    const val PLANNING_PAGE_SIZE = 1_000
    const val RECOVERY_PAGE_SIZE = 32
    const val PLANNING_HORIZON_DAYS = 400L
    const val BIRTHDAY_RETENTION_DAYS = 400L
    const val CLOCK_TOLERANCE_MILLIS = 5 * 60 * 1_000L
    const val LEASE_RENEW_MARGIN_MILLIS = 2 * 60 * 1_000L
    const val SENT_WATCHDOG_MILLIS = 15 * 60 * 1_000L
    const val ARM_SPACING_MILLIS = 5 * 60 * 1_000L
    val globalOrchestrationMutex = Mutex()
  }
}
