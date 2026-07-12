package com.yashsomani.birthdayautopilot.automation.orchestration

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import com.yashsomani.birthdayautopilot.storage.database.AccountRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.BirthdayOccurrenceRecordEntity
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustEntity
import com.yashsomani.birthdayautopilot.storage.database.ContactSyncStateEntity
import com.yashsomani.birthdayautopilot.storage.database.ControlEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitState
import com.yashsomani.birthdayautopilot.storage.database.CoordinationStateEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationBindingEntity
import com.yashsomani.birthdayautopilot.storage.database.InstallationRecordState
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import com.yashsomani.birthdayautopilot.storage.database.ResetBlockedDateEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyEntity
import com.yashsomani.birthdayautopilot.storage.database.ResetSafetyStatus
import com.yashsomani.birthdayautopilot.storage.database.SendAttemptState
import com.yashsomani.birthdayautopilot.storage.database.TestJobEntity

/** Minimal, content-private projection needed to create deterministic annual occurrences. */
data class BirthdayPlanningSeed(
  val accountId: String,
  val contactId: String,
  val sourceFingerprint: String,
  val approvalId: String,
  val policyId: String,
  val birthdayMonth: Int,
  val birthdayDay: Int,
  val leapDayPolicy: String?,
  val destinationFingerprint: String,
  val normalizedPhoneE164: String,
  val payloadHash: String,
  val timeZoneId: String,
  val windowStartMinute: Int,
  val windowEndMinute: Int,
  val graceEndMinute: Int?,
)

/** Private claim material. This value must never be bridged or logged. */
data class BirthdayClaimMaterial(
  val occurrenceId: String,
  val sourceFingerprint: String,
  val normalizedPhoneE164: String,
  val localDate: String,
  val channel: String,
)

/**
 * Narrow orchestration DAO. The irreversible Arm/barrier/SMS mutations stay exclusively in
 * SafetyLedgerDao; this DAO only discovers work and persists coordination/recovery projections.
 */
@Dao
abstract class AutomationOrchestrationDao {
  @Query("SELECT * FROM accounts_v2 WHERE activeSlot = 1 LIMIT 1")
  abstract suspend fun activeAccount(): AccountRecordEntity?

  @Query("SELECT * FROM app_control WHERE singletonId = 1")
  abstract suspend fun control(): ControlEntity?

  @Query("SELECT * FROM installation_bindings_v2 WHERE localSlot = 1 LIMIT 1")
  abstract suspend fun localInstallation(): InstallationBindingEntity?

  @Query("SELECT generation FROM callback_counter WHERE singletonId = 1")
  abstract suspend fun callbackCounterGeneration(): String?

  @Query("SELECT * FROM coordination_state_v2 WHERE accountId = :accountId")
  abstract suspend fun coordinationState(accountId: String): CoordinationStateEntity?

  @Query("SELECT * FROM clock_trust_v2 WHERE accountId = :accountId")
  abstract suspend fun clockTrust(accountId: String): ClockTrustEntity?

  @Query("SELECT * FROM reset_safety_v2 WHERE accountId = :accountId")
  abstract suspend fun resetSafety(accountId: String): ResetSafetyEntity?

  @Query("SELECT * FROM contact_sync_state_v2 WHERE accountId = :accountId")
  abstract suspend fun contactSyncState(accountId: String): ContactSyncStateEntity?

  @Query("SELECT * FROM test_jobs_v2 WHERE testJobId = :testJobId")
  abstract suspend fun testJob(testJobId: String): TestJobEntity?

  @Query("SELECT * FROM coordination_permits_v2 WHERE permitId = :permitId")
  abstract suspend fun permit(permitId: String): CoordinationPermitEntity?

  @Query(
    """
    SELECT a.accountId AS accountId,
      c.contactId AS contactId,
      c.sourceFingerprint AS sourceFingerprint,
      approval.approvalId AS approvalId,
      policy.policyId AS policyId,
      approval.birthdayMonth AS birthdayMonth,
      approval.birthdayDay AS birthdayDay,
      approval.leapDayPolicy AS leapDayPolicy,
      approval.destinationFingerprint AS destinationFingerprint,
      approval.normalizedPhoneE164 AS normalizedPhoneE164,
      approval.contentHash AS payloadHash,
      policy.timeZoneId AS timeZoneId,
      policy.windowStartMinute AS windowStartMinute,
      policy.windowEndMinute AS windowEndMinute,
      policy.graceEndMinute AS graceEndMinute
    FROM accounts_v2 a
    JOIN contact_snapshots_v2 c ON c.accountId = a.accountId
    JOIN recipient_policies_v2 recipient ON recipient.contactId = c.contactId
    JOIN approval_snapshots_v2 approval ON approval.approvalId = recipient.approvalId
    JOIN automation_policies_v2 policy ON policy.policyId = approval.policyId
    JOIN contact_phones_v2 phone ON phone.phoneId = approval.phoneId
    WHERE a.activeSlot = 1
      AND a.state = 'ACTIVE'
      AND c.state = 'ACTIVE'
      AND recipient.state = 'ENABLED'
      AND approval.state = 'ACTIVE'
      AND policy.state = 'ACTIVE'
      AND phone.state = 'READY'
      AND c.materialRevision = approval.contactMaterialRevision
      AND phone.materialRevision = approval.phoneMaterialRevision
      AND policy.revision = approval.policyRevision
      AND approval.birthdayMonth BETWEEN 1 AND 12
      AND approval.birthdayDay BETWEEN 1 AND 31
      AND (
        :hasCursor = 0
        OR (CASE WHEN (approval.birthdayMonth * 100 + approval.birthdayDay) >= :currentMonthDay
          THEN 0 ELSE 1 END) > :afterUpcomingBucket
        OR (
          (CASE WHEN (approval.birthdayMonth * 100 + approval.birthdayDay) >= :currentMonthDay
            THEN 0 ELSE 1 END) = :afterUpcomingBucket
          AND (approval.birthdayMonth * 100 + approval.birthdayDay) > :afterMonthDay
        )
        OR (
          (CASE WHEN (approval.birthdayMonth * 100 + approval.birthdayDay) >= :currentMonthDay
            THEN 0 ELSE 1 END) = :afterUpcomingBucket
          AND (approval.birthdayMonth * 100 + approval.birthdayDay) = :afterMonthDay
          AND c.contactId > :afterContactId
        )
      )
      AND (
        NOT EXISTS (
          SELECT 1 FROM birthday_occurrences_v2 occurrence
          WHERE occurrence.contactId = c.contactId
            AND occurrence.localDate BETWEEN :horizonStartDate AND :horizonEndDate
        )
        OR EXISTS (
          SELECT 1 FROM birthday_occurrences_v2 occurrence
          WHERE occurrence.contactId = c.contactId
            AND occurrence.localDate BETWEEN :horizonStartDate AND :horizonEndDate
            AND occurrence.state IN ('PLANNED', 'PREPARED', 'SCHEDULED', 'COORDINATION_BLOCKED')
            AND (
              occurrence.approvalId != approval.approvalId
              OR occurrence.policyId != policy.policyId
              OR occurrence.payloadHash != approval.contentHash
              OR occurrence.destinationFingerprint != approval.destinationFingerprint
              OR occurrence.timeZoneId != :currentTimeZoneId
            )
        )
      )
    ORDER BY
      CASE WHEN (approval.birthdayMonth * 100 + approval.birthdayDay) >= :currentMonthDay
        THEN 0 ELSE 1 END,
      approval.birthdayMonth,
      approval.birthdayDay,
      c.contactId
    LIMIT :limit
    """,
  )
  abstract suspend fun planningSeeds(
    horizonStartDate: String,
    horizonEndDate: String,
    currentTimeZoneId: String,
    currentMonthDay: Int,
    hasCursor: Boolean,
    afterUpcomingBucket: Int,
    afterMonthDay: Int,
    afterContactId: String,
    limit: Int,
  ): List<BirthdayPlanningSeed>

  @Query(
    """
    SELECT occurrence.occurrenceId AS occurrenceId,
      contact.sourceFingerprint AS sourceFingerprint,
      approval.normalizedPhoneE164 AS normalizedPhoneE164,
      occurrence.localDate AS localDate,
      occurrence.channel AS channel
    FROM birthday_occurrences_v2 occurrence
    JOIN contact_snapshots_v2 contact ON contact.contactId = occurrence.contactId
    JOIN approval_snapshots_v2 approval ON approval.approvalId = occurrence.approvalId
    WHERE occurrence.occurrenceId = :occurrenceId
    LIMIT 1
    """,
  )
  abstract suspend fun claimMaterial(occurrenceId: String): BirthdayClaimMaterial?

  @Query(
    """
    SELECT * FROM birthday_occurrences_v2
    WHERE state IN ('SCHEDULED', 'CLAIMED')
      AND resolvedWindowStartMillis <= :trustedNowMillis
      AND resolvedWindowEndMillis > :trustedNowMillis
    ORDER BY resolvedWindowStartMillis, occurrenceId
    LIMIT 1
    """,
  )
  abstract suspend fun nextDueBirthday(trustedNowMillis: Long): BirthdayOccurrenceRecordEntity?

  @Query(
    """
    SELECT * FROM birthday_occurrences_v2
    WHERE state IN ('PLANNED', 'PREPARED', 'SCHEDULED', 'CLAIMED', 'COORDINATION_BLOCKED', 'CLOUD_CLAIMED')
      AND resolvedWindowEndMillis <= :trustedNowMillis
    ORDER BY resolvedWindowEndMillis, occurrenceId
    LIMIT :limit
    """,
  )
  protected abstract suspend fun expiredUnarmedBirthdays(
    trustedNowMillis: Long,
    limit: Int,
  ): List<BirthdayOccurrenceRecordEntity>

  @Query(
    """
    SELECT MIN(resolvedWindowStartMillis) FROM birthday_occurrences_v2
    WHERE state = 'SCHEDULED' AND resolvedWindowEndMillis > :trustedNowMillis
    """,
  )
  abstract suspend fun nextScheduledWindowMillis(trustedNowMillis: Long): Long?

  @Query(
    """
    SELECT * FROM coordination_permits_v2
    WHERE state IN ('ARM_RECONCILING', 'CLOUD_CLAIMED')
    ORDER BY updatedAtMillis, permitId
    LIMIT :limit
    """,
  )
  abstract suspend fun recoverablePermits(limit: Int): List<CoordinationPermitEntity>

  @Query(
    """
    SELECT * FROM coordination_permits_v2
    WHERE purpose = :purpose
      AND operationId = :operationId
      AND state IN ('ARM_RECONCILING', 'CLOUD_CLAIMED')
    ORDER BY attemptNumber DESC
    LIMIT 1
    """,
  )
  abstract suspend fun recoverablePermitForOperation(
    purpose: OperationPurpose,
    operationId: String,
  ): CoordinationPermitEntity?

  @Query(
    """
    SELECT * FROM coordination_permits_v2
    WHERE state = 'ARM_RECONCILING'
      AND (
        :currentBootCount IS NULL
        OR bootCount != :currentBootCount
        OR requestStartElapsedMillis > :currentElapsedRealtimeMillis
      )
      AND (
        :hasCursor = 0
        OR updatedAtMillis > :afterUpdatedAtMillis
        OR (updatedAtMillis = :afterUpdatedAtMillis AND permitId > :afterPermitId)
      )
    ORDER BY updatedAtMillis, permitId
    LIMIT :limit
    """,
  )
  abstract suspend fun bootLostArmReconcilingPermits(
    currentBootCount: Int?,
    currentElapsedRealtimeMillis: Long,
    hasCursor: Boolean,
    afterUpdatedAtMillis: Long,
    afterPermitId: String,
    limit: Int,
  ): List<CoordinationPermitEntity>

  @Query(
    """
    SELECT COUNT(*) FROM coordination_permits_v2
    WHERE state IN ('ARM_RECONCILING', 'CLOUD_ARMED', 'BARRIER_CONSUMED', 'COORDINATION_UNKNOWN')
    """,
  )
  abstract suspend fun unresolvedPermitCount(): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  protected abstract suspend fun insertInstallationIfAbsent(row: InstallationBindingEntity): Long

  @Upsert
  protected abstract suspend fun upsertCoordinationState(row: CoordinationStateEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertClockTrustIfAbsent(row: ClockTrustEntity): Long

  // A reset-generation replacement intentionally deletes the old blocked-date children first.
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  protected abstract suspend fun replaceResetSafety(row: ResetSafetyEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  protected abstract suspend fun insertBlockedDateIfAbsent(row: ResetBlockedDateEntity): Long

  @Query(
    """
    UPDATE installation_bindings_v2
    SET state = :state,
        accountMode = :accountMode,
        senderEpoch = :senderEpoch,
        resetGeneration = :resetGeneration,
        ownerLeaseUntilMillis = :ownerLeaseUntilMillis,
        lastVerifiedServerMillis = :serverNowMillis,
        revision = revision + 1,
        updatedAtMillis = :deviceWallMillis
    WHERE installationId = :installationId
      AND accountId = :accountId
      AND localSlot = 1
      AND state != 'REVOKED'
    """,
  )
  protected abstract suspend fun updateRegisteredInstallation(
    installationId: String,
    accountId: String,
    state: InstallationRecordState,
    accountMode: AccountMode,
    senderEpoch: Long?,
    resetGeneration: Long,
    ownerLeaseUntilMillis: Long?,
    serverNowMillis: Long,
    deviceWallMillis: Long,
  ): Int

  @Query(
    """
    UPDATE app_control
    SET accountMode = :accountMode,
        automationDesired = CASE WHEN :accountMode = 'AUTOMATION_ACTIVE' THEN automationDesired ELSE 0 END,
        activeInstallationEpoch = :senderEpoch,
        revision = revision + 1,
        blockerRevision = blockerRevision + 1
    WHERE singletonId = 1
      AND revision < 9223372036854775807
      AND blockerRevision < 9223372036854775807
    """,
  )
  protected abstract suspend fun bindControlToRegistration(
    accountMode: String,
    senderEpoch: Long?,
  ): Int

  @Query(
    """
    UPDATE installation_bindings_v2
    SET accountMode = :mode,
        ownerLeaseUntilMillis = NULL,
        lastVerifiedServerMillis = :serverObservedAtMillis,
        revision = revision + 1,
        updatedAtMillis = :deviceWallMillis
    WHERE installationId = :localInstallationId
      AND accountId = :accountId
      AND localSlot = 1
      AND state != 'REVOKED'
    """,
  )
  protected abstract suspend fun updateLocalLifecycleMode(
    accountId: String,
    localInstallationId: String,
    mode: AccountMode,
    serverObservedAtMillis: Long,
    deviceWallMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_state_v2
    SET mode = :mode,
        activeInstallationId = :activeInstallationId,
        senderEpoch = :senderEpoch,
        resetGeneration = :resetGeneration,
        ownerLeaseUntilMillis = :ownerLeaseUntilMillis,
        nextArmNotBeforeMillis = :nextArmNotBeforeMillis,
        latestIssuedSubmitNotAfterMillis = :latestIssuedSubmitNotAfterMillis,
        birthdayAutomationNotBeforeMillis = :birthdayAutomationNotBeforeMillis,
        transferDrainUntilMillis = :transferDrainUntilMillis,
        deletionDrainUntilMillis = :deletionDrainUntilMillis,
        lastSuccessfulCoordinationMillis = :serverObservedAtMillis,
        lastSafeCode = NULL,
        revision = revision + 1,
        updatedAtMillis = :deviceWallMillis
    WHERE accountId = :accountId
      AND resetGeneration = :resetGeneration
    """,
  )
  protected abstract suspend fun updateCoordinationLifecycleMode(
    accountId: String,
    mode: AccountMode,
    activeInstallationId: String,
    senderEpoch: Long,
    resetGeneration: Long,
    ownerLeaseUntilMillis: Long,
    nextArmNotBeforeMillis: Long,
    latestIssuedSubmitNotAfterMillis: Long,
    birthdayAutomationNotBeforeMillis: Long,
    transferDrainUntilMillis: Long?,
    deletionDrainUntilMillis: Long?,
    serverObservedAtMillis: Long,
    deviceWallMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_state_v2
    SET mode = 'DELETING',
        ownerLeaseUntilMillis = NULL,
        transferDrainUntilMillis = NULL,
        deletionDrainUntilMillis = :deletionDrainUntilMillis,
        lastSuccessfulCoordinationMillis = MAX(
          COALESCE(lastSuccessfulCoordinationMillis, 0),
          :serverObservedAtMillis
        ),
        lastSafeCode = NULL,
        revision = revision + 1,
        updatedAtMillis = :deviceWallMillis
    WHERE accountId = :accountId
      AND senderEpoch = :senderEpoch
      AND resetGeneration = :resetGeneration
    """,
  )
  protected abstract suspend fun updateCoordinationDeletionFence(
    accountId: String,
    senderEpoch: Long,
    resetGeneration: Long,
    deletionDrainUntilMillis: Long,
    serverObservedAtMillis: Long,
    deviceWallMillis: Long,
  ): Int

  @Query(
    """
    UPDATE installation_bindings_v2
    SET ownerLeaseUntilMillis = :leaseUntilMillis,
        revision = revision + 1,
        updatedAtMillis = :deviceWallMillis
    WHERE installationId = :installationId
      AND state = 'ACTIVE'
      AND senderEpoch = :senderEpoch
    """,
  )
  protected abstract suspend fun updateInstallationLease(
    installationId: String,
    senderEpoch: Long,
    leaseUntilMillis: Long,
    deviceWallMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_state_v2
    SET ownerLeaseUntilMillis = :leaseUntilMillis,
        lastSafeCode = NULL,
        revision = revision + 1,
        updatedAtMillis = :deviceWallMillis
    WHERE accountId = :accountId
      AND activeInstallationId = :installationId
      AND senderEpoch = :senderEpoch
      AND mode NOT IN ('TRANSFER_PENDING', 'DELETING')
    """,
  )
  protected abstract suspend fun updateCoordinationLease(
    accountId: String,
    installationId: String,
    senderEpoch: Long,
    leaseUntilMillis: Long,
    deviceWallMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_state_v2
    SET lastSafeCode = :safeCode,
        revision = revision + 1,
        updatedAtMillis = :deviceWallMillis
    WHERE accountId = :accountId
    """,
  )
  abstract suspend fun recordSafeCode(
    accountId: String,
    safeCode: String,
    deviceWallMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_state_v2
    SET nextArmNotBeforeMillis = MAX(COALESCE(nextArmNotBeforeMillis, 0), :nextArmNotBeforeMillis),
        latestIssuedSubmitNotAfterMillis = MAX(
          COALESCE(latestIssuedSubmitNotAfterMillis, 0),
          :serverSubmitNotAfterMillis
        ),
        lastSuccessfulCoordinationMillis = MAX(
          COALESCE(lastSuccessfulCoordinationMillis, 0),
          :serverNowMillis
        ),
        lastSafeCode = NULL,
        revision = revision + 1,
        updatedAtMillis = :deviceWallMillis
    WHERE accountId = :accountId
      AND activeInstallationId = :installationId
      AND senderEpoch = :senderEpoch
    """,
  )
  abstract suspend fun advanceLocalArmFence(
    accountId: String,
    installationId: String,
    senderEpoch: Long,
    serverSubmitNotAfterMillis: Long,
    nextArmNotBeforeMillis: Long,
    serverNowMillis: Long,
    deviceWallMillis: Long,
  ): Int

  @Query(
    """
    UPDATE app_control SET revision = revision + 1
    WHERE singletonId = 1 AND revision < 9223372036854775807
    """,
  )
  abstract suspend fun touchProjection(): Int

  @Query(
    """
    UPDATE birthday_occurrences_v2
    SET state = :nextState,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis,
        terminalAtMillis = :terminalAtMillis,
        safeOutcomeCode = :safeCode
    WHERE occurrenceId = :occurrenceId
      AND state = :expectedState
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casBirthdayState(
    occurrenceId: String,
    expectedState: BirthdayJobState,
    expectedRevision: Long,
    nextState: BirthdayJobState,
    updatedAtMillis: Long,
    terminalAtMillis: Long?,
    safeCode: String?,
  ): Int

  @Query(
    """
    UPDATE birthday_occurrences_v2
    SET timeZoneId = :timeZoneId,
        resolvedWindowStartMillis = :windowStartMillis,
        resolvedWindowEndMillis = :windowEndMillis,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis,
        safeOutcomeCode = NULL
    WHERE occurrenceId = :occurrenceId
      AND state IN ('PLANNED', 'PREPARED', 'SCHEDULED', 'COORDINATION_BLOCKED')
      AND resolvedWindowEndMillis > :updatedAtMillis
    """,
  )
  abstract suspend fun replanUnclaimedOccurrence(
    occurrenceId: String,
    timeZoneId: String,
    windowStartMillis: Long,
    windowEndMillis: Long,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE local_destination_guards_v2
    SET destinationFingerprint = :destinationFingerprint
    WHERE occurrenceId = :occurrenceId AND armedOrLater = 0
    """,
  )
  protected abstract suspend fun updateUnarmedDestinationGuard(
    occurrenceId: String,
    destinationFingerprint: String,
  ): Int

  @Query(
    """
    UPDATE birthday_occurrences_v2
    SET approvalId = :approvalId,
        policyId = :policyId,
        timeZoneId = :timeZoneId,
        resolvedWindowStartMillis = :windowStartMillis,
        resolvedWindowEndMillis = :windowEndMillis,
        destinationFingerprint = :destinationFingerprint,
        payloadHash = :payloadHash,
        state = 'SCHEDULED',
        revision = revision + 1,
        claimedBlockerRevision = NULL,
        updatedAtMillis = :updatedAtMillis,
        terminalAtMillis = NULL,
        safeOutcomeCode = NULL
    WHERE occurrenceId = :occurrenceId
      AND state IN ('PLANNED', 'PREPARED', 'SCHEDULED', 'COORDINATION_BLOCKED')
    """,
  )
  protected abstract suspend fun updateUnclaimedOccurrenceMaterial(
    occurrenceId: String,
    approvalId: String,
    policyId: String,
    timeZoneId: String,
    windowStartMillis: Long,
    windowEndMillis: Long,
    destinationFingerprint: String,
    payloadHash: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_permits_v2
    SET state = 'COORDINATION_UNKNOWN',
        noWriteReason = :safeCode,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis
    WHERE permitId = :permitId
      AND state = 'ARM_RECONCILING'
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casPermitUnknown(
    permitId: String,
    expectedRevision: Long,
    safeCode: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_permits_v2
    SET state = 'CANCELLED',
        noWriteReason = :safeCode,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis
    WHERE operationId = :operationId AND state = 'CLOUD_CLAIMED' AND armDispatched = 0
    """,
  )
  protected abstract suspend fun cancelNeverDispatchedPermit(
    operationId: String,
    safeCode: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE test_jobs_v2
    SET state = 'COORDINATION_UNKNOWN',
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis,
        terminalAtMillis = :updatedAtMillis,
        invalidationReason = :safeCode
    WHERE testJobId = :testJobId AND state = 'ARM_RECONCILING'
    """,
  )
  protected abstract suspend fun markTestCoordinationUnknown(
    testJobId: String,
    safeCode: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE send_attempts_v2
    SET state = 'UNKNOWN',
        safeOutcomeCode = :safeCode,
        terminalAtMillis = :updatedAtMillis,
        revision = revision + 1
    WHERE permitId = :permitId
      AND state IN ('BARRIER_CONSUMED', 'API_CALL_STARTED')
    """,
  )
  protected abstract suspend fun markAttemptUnknown(
    permitId: String,
    safeCode: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE birthday_occurrences_v2
    SET state = 'SUBMISSION_UNKNOWN',
        safeOutcomeCode = :safeCode,
        terminalAtMillis = :updatedAtMillis,
        updatedAtMillis = :updatedAtMillis,
        revision = revision + 1
    WHERE occurrenceId = :operationId AND state = 'SUBMISSION_BARRIER_CONSUMED'
    """,
  )
  protected abstract suspend fun markBirthdaySubmissionUnknown(
    operationId: String,
    safeCode: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE test_jobs_v2
    SET state = 'UNKNOWN',
        invalidationReason = :safeCode,
        terminalAtMillis = :updatedAtMillis,
        updatedAtMillis = :updatedAtMillis,
        revision = revision + 1
    WHERE testJobId = :operationId AND state = 'BARRIER_CONSUMED'
    """,
  )
  protected abstract suspend fun markTestSubmissionUnknown(
    operationId: String,
    safeCode: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    SELECT * FROM coordination_permits_v2
    WHERE state = 'BARRIER_CONSUMED'
    ORDER BY updatedAtMillis, permitId
    LIMIT :limit
    """,
  )
  protected abstract suspend fun barrierConsumedPermits(limit: Int): List<CoordinationPermitEntity>

  @Query(
    """
    UPDATE reset_safety_v2
    SET status = :status,
        overflowBlocked = 0,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis
    WHERE accountId = :accountId
    """,
  )
  protected abstract suspend fun updateResetStatus(
    accountId: String,
    status: ResetSafetyStatus,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    DELETE FROM reset_blocked_dates_v2
    WHERE resetSafetyId = :resetSafetyId
      AND releaseAfterTrustedServerMillis < :trustedServerNowMillis
    """,
  )
  protected abstract suspend fun deleteReleasedResetDates(
    resetSafetyId: String,
    trustedServerNowMillis: Long,
  ): Int

  @Query("SELECT COUNT(*) FROM reset_blocked_dates_v2 WHERE resetSafetyId = :resetSafetyId")
  protected abstract suspend fun blockedResetDateCount(resetSafetyId: String): Int

  @Query(
    """
    UPDATE app_control
    SET revision = revision + 1, blockerRevision = blockerRevision + 1
    WHERE singletonId = 1
      AND revision < 9223372036854775807
      AND blockerRevision < 9223372036854775807
    """,
  )
  protected abstract suspend fun bumpBlockerRevision(): Int

  @Transaction
  open suspend fun ensureLocalInstallation(row: InstallationBindingEntity): InstallationBindingEntity? {
    val account = activeAccount() ?: return null
    val existing = localInstallation()
    if (existing != null) {
      return existing.takeIf {
        it.accountId == account.accountId &&
          it.installationId == row.installationId &&
          it.callbackGeneration == row.callbackGeneration &&
          it.state != InstallationRecordState.REVOKED
      }
    }
    if (
      row.accountId != account.accountId ||
      row.localSlot != 1 ||
      row.state != InstallationRecordState.STANDBY ||
      row.senderEpoch != null
    ) return null
    insertInstallationIfAbsent(row)
    return localInstallation()?.takeIf { it.installationId == row.installationId }
  }

  @Transaction
  open suspend fun applyRegistration(
    accountId: String,
    installationId: String,
    installationState: InstallationRecordState,
    serverMode: AccountMode,
    localMode: AccountMode,
    senderEpoch: Long?,
    resetGeneration: Long,
    ownerLeaseUntilMillis: Long?,
    serverNowMillis: Long,
    coordination: CoordinationStateEntity,
    reset: ResetSafetyEntity,
    blockedDate: ResetBlockedDateEntity?,
    deviceWallMillis: Long,
  ): Boolean {
    val account = activeAccount() ?: return false
    if (
      account.accountId != accountId ||
      coordination.accountId != accountId ||
      coordination.mode != serverMode ||
      coordination.resetGeneration != resetGeneration ||
      reset.accountId != accountId ||
      reset.resetGeneration != resetGeneration ||
      (installationState == InstallationRecordState.ACTIVE && senderEpoch == null) ||
      (installationState == InstallationRecordState.STANDBY && senderEpoch != null)
    ) return false
    val existingInstallation = localInstallation() ?: return false
    val existingCoordination = coordinationState(accountId)
    val bindingChanged =
      existingInstallation.state != installationState ||
        existingInstallation.accountMode != localMode ||
        existingInstallation.senderEpoch != senderEpoch ||
        existingInstallation.resetGeneration != resetGeneration ||
        existingCoordination?.mode != serverMode ||
        existingCoordination?.activeInstallationId != coordination.activeInstallationId ||
        existingCoordination?.senderEpoch != coordination.senderEpoch ||
        existingCoordination?.resetGeneration != coordination.resetGeneration
    if (
      updateRegisteredInstallation(
        installationId,
        accountId,
        installationState,
        localMode,
        senderEpoch,
        resetGeneration,
        ownerLeaseUntilMillis,
        serverNowMillis,
        deviceWallMillis,
      ) != 1
    ) return false
    upsertCoordinationState(coordination)
    val currentReset = resetSafety(accountId)
    if (currentReset == null || currentReset.resetGeneration != resetGeneration) {
      replaceResetSafety(reset)
      blockedDate?.let { insertBlockedDateIfAbsent(it) }
    }
    if (bindingChanged) {
      check(bindControlToRegistration(localMode.name, senderEpoch) == 1) {
        "registration-control-binding-failed"
      }
    } else {
      check(touchProjection() == 1) { "registration-projection-touch-failed" }
    }
    return true
  }

  @Transaction
  open suspend fun applyRemoteLifecycleMode(
    accountId: String,
    localInstallationId: String,
    mode: AccountMode,
    activeInstallationId: String,
    senderEpoch: Long,
    resetGeneration: Long,
    ownerLeaseUntilMillis: Long,
    nextArmNotBeforeMillis: Long,
    latestIssuedSubmitNotAfterMillis: Long,
    birthdayAutomationNotBeforeMillis: Long,
    transferTargetInstallationId: String?,
    transferDrainUntilMillis: Long?,
    deletionDrainUntilMillis: Long?,
    serverObservedAtMillis: Long,
    deviceWallMillis: Long,
  ): Boolean {
    val account = activeAccount() ?: return false
    val local = localInstallation() ?: return false
    val coordination = coordinationState(accountId) ?: return false
    if (
      account.accountId != accountId ||
      local.accountId != accountId ||
      local.installationId != localInstallationId ||
      coordination.resetGeneration != resetGeneration ||
      senderEpoch <= 0 ||
      (mode == AccountMode.TRANSFER_PENDING &&
        (transferTargetInstallationId != localInstallationId || transferDrainUntilMillis == null)) ||
      (mode == AccountMode.DELETING && deletionDrainUntilMillis == null) ||
      mode !in setOf(AccountMode.TRANSFER_PENDING, AccountMode.DELETING)
    ) return false
    if (
      updateLocalLifecycleMode(
        accountId,
        localInstallationId,
        mode,
        serverObservedAtMillis,
        deviceWallMillis,
      ) != 1
    ) return false
    if (
      updateCoordinationLifecycleMode(
        accountId,
        mode,
        activeInstallationId,
        senderEpoch,
        resetGeneration,
        ownerLeaseUntilMillis,
        nextArmNotBeforeMillis,
        latestIssuedSubmitNotAfterMillis,
        birthdayAutomationNotBeforeMillis,
        transferDrainUntilMillis,
        deletionDrainUntilMillis,
        serverObservedAtMillis,
        deviceWallMillis,
      ) != 1
    ) return false
    check(bindControlToRegistration(mode.name, senderEpoch) == 1) {
      "lifecycle-control-binding-failed"
    }
    return true
  }

  @Transaction
  open suspend fun applyRemoteDeletionFence(
    accountId: String,
    localInstallationId: String,
    senderEpoch: Long,
    resetGeneration: Long,
    deletionDrainUntilMillis: Long,
    serverObservedAtMillis: Long,
    deviceWallMillis: Long,
  ): Boolean {
    val account = activeAccount() ?: return false
    val local = localInstallation() ?: return false
    val coordination = coordinationState(accountId) ?: return false
    if (
      account.accountId != accountId ||
      local.accountId != accountId ||
      local.installationId != localInstallationId ||
      local.resetGeneration != resetGeneration ||
      coordination.senderEpoch != senderEpoch ||
      coordination.resetGeneration != resetGeneration ||
      senderEpoch <= 0 ||
      resetGeneration <= 0 ||
      deletionDrainUntilMillis < 0 ||
      serverObservedAtMillis < 0
    ) return false
    if (
      updateLocalLifecycleMode(
        accountId,
        localInstallationId,
        AccountMode.DELETING,
        serverObservedAtMillis,
        deviceWallMillis,
      ) != 1
    ) return false
    if (
      updateCoordinationDeletionFence(
        accountId,
        senderEpoch,
        resetGeneration,
        deletionDrainUntilMillis,
        serverObservedAtMillis,
        deviceWallMillis,
      ) != 1
    ) return false
    check(bindControlToRegistration(AccountMode.DELETING.name, senderEpoch) == 1) {
      "deletion-control-binding-failed"
    }
    return true
  }

  @Transaction
  open suspend fun persistRenewedLease(
    accountId: String,
    installationId: String,
    senderEpoch: Long,
    leaseUntilMillis: Long,
    lastTrustedServerMillis: Long,
    deviceWallMillis: Long,
  ): Boolean {
    if (leaseUntilMillis <= lastTrustedServerMillis) return false
    if (
      updateInstallationLease(
        installationId,
        senderEpoch,
        leaseUntilMillis,
        deviceWallMillis,
      ) != 1
    ) return false
    check(
      updateCoordinationLease(
        accountId,
        installationId,
        senderEpoch,
        leaseUntilMillis,
        deviceWallMillis,
      ) == 1,
    ) { "coordination-lease-binding-failed" }
    check(touchProjection() == 1) { "lease-projection-touch-failed" }
    return true
  }

  @Transaction
  open suspend fun scheduleNewOccurrence(occurrenceId: String, nowMillis: Long): Boolean {
    val planned = getBirthdayOccurrence(occurrenceId) ?: return false
    if (planned.state != BirthdayJobState.PLANNED) return planned.state == BirthdayJobState.SCHEDULED
    if (
      casBirthdayState(
        occurrenceId,
        BirthdayJobState.PLANNED,
        planned.revision,
        BirthdayJobState.PREPARED,
        nowMillis,
        null,
        null,
      ) != 1
    ) return false
    val prepared = getBirthdayOccurrence(occurrenceId) ?: return false
    check(
      casBirthdayState(
        occurrenceId,
        BirthdayJobState.PREPARED,
        prepared.revision,
        BirthdayJobState.SCHEDULED,
        nowMillis,
        null,
        null,
      ) == 1,
    ) { "occurrence-schedule-cas-failed" }
    check(touchProjection() == 1) { "occurrence-projection-touch-failed" }
    return true
  }

  /**
   * Re-enters a user-reviewed, unarmed occurrence through the normal scheduler. This method can
   * only move states that have never crossed Arm; it never claims, arms, consumes a barrier, or
   * calls SmsManager.
   */
  @Transaction
  open suspend fun scheduleReviewedOccurrence(
    occurrenceId: String,
    expectedOccurrenceRevision: Long,
    trustedNowMillis: Long,
  ): Boolean {
    val row = getBirthdayOccurrence(occurrenceId) ?: return false
    if (
      row.revision != expectedOccurrenceRevision ||
      trustedNowMillis < row.resolvedWindowStartMillis ||
      trustedNowMillis >= row.resolvedWindowEndMillis
    ) return false
    val changed = when (row.state) {
      BirthdayJobState.PLANNED -> {
        if (
          casBirthdayState(
            occurrenceId,
            BirthdayJobState.PLANNED,
            row.revision,
            BirthdayJobState.PREPARED,
            trustedNowMillis,
            null,
            null,
          ) != 1
        ) return false
        val prepared = getBirthdayOccurrence(occurrenceId) ?: return false
        casBirthdayState(
          occurrenceId,
          BirthdayJobState.PREPARED,
          prepared.revision,
          BirthdayJobState.SCHEDULED,
          trustedNowMillis,
          null,
          null,
        ) == 1
      }
      BirthdayJobState.PREPARED,
      BirthdayJobState.COORDINATION_BLOCKED,
      -> casBirthdayState(
        occurrenceId,
        row.state,
        row.revision,
        BirthdayJobState.SCHEDULED,
        trustedNowMillis,
        null,
        null,
      ) == 1
      BirthdayJobState.SCHEDULED -> true
      else -> false
    }
    if (!changed) return false
    check(touchProjection() == 1) { "reviewed-occurrence-projection-touch-failed" }
    return true
  }

  /** Explicitly retires today's unarmed occurrence; the annual planner may create next year. */
  @Transaction
  open suspend fun deferReviewedOccurrenceToNextYear(
    occurrenceId: String,
    expectedOccurrenceRevision: Long,
    nowMillis: Long,
  ): Boolean {
    val row = getBirthdayOccurrence(occurrenceId) ?: return false
    if (
      row.revision != expectedOccurrenceRevision ||
      row.state !in setOf(
        BirthdayJobState.PLANNED,
        BirthdayJobState.PREPARED,
        BirthdayJobState.SCHEDULED,
        BirthdayJobState.COORDINATION_BLOCKED,
      )
    ) return false
    if (
      casBirthdayState(
        occurrenceId,
        row.state,
        row.revision,
        BirthdayJobState.CANCELLED,
        nowMillis,
        nowMillis,
        "USER_CHOSE_NEXT_YEAR",
      ) != 1
    ) return false
    check(touchProjection() == 1) { "deferred-occurrence-projection-touch-failed" }
    return true
  }

  @Transaction
  open suspend fun replaceUnclaimedOccurrenceMaterial(
    occurrenceId: String,
    approvalId: String,
    policyId: String,
    timeZoneId: String,
    windowStartMillis: Long,
    windowEndMillis: Long,
    destinationFingerprint: String,
    payloadHash: String,
    updatedAtMillis: Long,
  ): Boolean {
    if (
      approvalId.isBlank() ||
      policyId.isBlank() ||
      destinationFingerprint.isBlank() ||
      payloadHash.isBlank() ||
      windowEndMillis <= windowStartMillis
    ) return false
    val row = getBirthdayOccurrence(occurrenceId) ?: return false
    if (row.state !in setOf(
        BirthdayJobState.PLANNED,
        BirthdayJobState.PREPARED,
        BirthdayJobState.SCHEDULED,
        BirthdayJobState.COORDINATION_BLOCKED,
      )
    ) return false
    if (updateUnarmedDestinationGuard(occurrenceId, destinationFingerprint) != 1) return false
    check(
      updateUnclaimedOccurrenceMaterial(
        occurrenceId,
        approvalId,
        policyId,
        timeZoneId,
        windowStartMillis,
        windowEndMillis,
        destinationFingerprint,
        payloadHash,
        updatedAtMillis,
      ) == 1,
    ) { "unclaimed-occurrence-material-cas-failed" }
    check(touchProjection() == 1) { "occurrence-material-projection-touch-failed" }
    return true
  }

  @Query("SELECT * FROM birthday_occurrences_v2 WHERE occurrenceId = :occurrenceId")
  protected abstract suspend fun getBirthdayOccurrence(
    occurrenceId: String,
  ): BirthdayOccurrenceRecordEntity?

  @Transaction
  open suspend fun terminalizeUnclaimedBirthday(
    occurrenceId: String,
    nextState: BirthdayJobState,
    safeCode: String,
    nowMillis: Long,
  ): Boolean {
    if (nextState !in setOf(BirthdayJobState.MISSED, BirthdayJobState.CANCELLED)) return false
    val row = getBirthdayOccurrence(occurrenceId) ?: return false
    if (row.state !in setOf(BirthdayJobState.SCHEDULED, BirthdayJobState.CLAIMED)) return false
    return casBirthdayState(
      row.occurrenceId,
      row.state,
      row.revision,
      nextState,
      nowMillis,
      nowMillis,
      safeCode,
    ) == 1
  }

  @Transaction
  open suspend fun expireUnarmedBirthdays(trustedNowMillis: Long, deviceWallMillis: Long): Int {
    var changed = 0
    expiredUnarmedBirthdays(trustedNowMillis, 64).forEach { row ->
      if (
        casBirthdayState(
          row.occurrenceId,
          row.state,
          row.revision,
          BirthdayJobState.MISSED,
          deviceWallMillis,
          deviceWallMillis,
          "WINDOW_CLOSED",
        ) == 1
      ) {
        if (row.state == BirthdayJobState.CLOUD_CLAIMED) {
          check(
            cancelNeverDispatchedPermit(row.occurrenceId, "WINDOW_CLOSED", deviceWallMillis) == 1,
          ) { "never-dispatched-permit-close-failed" }
        }
        changed += 1
      }
    }
    if (changed > 0) check(touchProjection() == 1) { "expiry-projection-touch-failed" }
    return changed
  }

  @Transaction
  open suspend fun markCoordinationUnknown(
    permitId: String,
    expectedRevision: Long,
    safeCode: String,
    nowMillis: Long,
  ): Boolean {
    val row = permit(permitId) ?: return false
    if (
      row.revision != expectedRevision ||
      row.state != CoordinationPermitState.ARM_RECONCILING ||
      casPermitUnknown(permitId, expectedRevision, safeCode, nowMillis) != 1
    ) return false
    when (row.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = getBirthdayOccurrence(row.operationId)
          ?: error("coordination-unknown-operation-missing")
        check(
          casBirthdayState(
            occurrence.occurrenceId,
            BirthdayJobState.ARM_RECONCILING,
            occurrence.revision,
            BirthdayJobState.COORDINATION_UNKNOWN,
            nowMillis,
            nowMillis,
            safeCode,
          ) == 1,
        ) { "coordination-unknown-operation-cas-failed" }
      }
      OperationPurpose.TEST -> check(
        markTestCoordinationUnknown(row.operationId, safeCode, nowMillis) == 1,
      ) { "test-coordination-unknown-cas-failed" }
    }
    check(touchProjection() == 1) { "coordination-unknown-projection-touch-failed" }
    return true
  }

  @Transaction
  open suspend fun reconstructConsumedBarriers(nowMillis: Long): Int {
    var changed = 0
    barrierConsumedPermits(32).forEach { row ->
      if (markAttemptUnknown(row.permitId, "PROCESS_DIED_AFTER_BARRIER", nowMillis) != 1) {
        return@forEach
      }
      when (row.purpose) {
        OperationPurpose.BIRTHDAY -> check(
          markBirthdaySubmissionUnknown(
            row.operationId,
            "PROCESS_DIED_AFTER_BARRIER",
            nowMillis,
          ) == 1,
        ) { "birthday-barrier-reconstruction-failed" }
        OperationPurpose.TEST -> check(
          markTestSubmissionUnknown(
            row.operationId,
            "PROCESS_DIED_AFTER_BARRIER",
            nowMillis,
          ) == 1,
        ) { "test-barrier-reconstruction-failed" }
      }
      changed += 1
    }
    if (changed > 0) check(touchProjection() == 1) { "barrier-projection-touch-failed" }
    return changed
  }

  @Transaction
  open suspend fun releaseExpiredResetDates(
    accountId: String,
    trustedServerNowMillis: Long,
    updatedAtMillis: Long,
  ): Boolean {
    val reset = resetSafety(accountId) ?: return false
    deleteReleasedResetDates(reset.resetSafetyId, trustedServerNowMillis)
    if (blockedResetDateCount(reset.resetSafetyId) != 0 || reset.overflowBlocked) return false
    if (reset.status == ResetSafetyStatus.CLEAR) return true
    check(updateResetStatus(accountId, ResetSafetyStatus.CLEAR, updatedAtMillis) == 1) {
      "reset-clear-cas-failed"
    }
    check(bumpBlockerRevision() == 1) { "reset-clear-blocker-failed" }
    return true
  }
}
