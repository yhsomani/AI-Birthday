package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode
import kotlin.math.abs

@Dao
abstract class SafetyLedgerDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertAccount(account: AccountRecordEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertInstallation(installation: InstallationBindingEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertConsentReceipt(receipt: ConsentReceiptEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertAutomationPolicy(policy: AutomationPolicyEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun putContactSyncState(state: ContactSyncStateEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertContactSnapshot(contact: ContactSnapshotEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertContactPhone(phone: ContactPhoneEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertRecipientPolicy(policy: RecipientPolicyEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertDestinationBlock(block: DestinationBlockEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertMessageTemplate(template: MessageTemplateEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertApprovalSnapshot(approval: ApprovalSnapshotEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertTestJob(job: TestJobEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun putCoordinationState(state: CoordinationStateEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertResetSafety(reset: ResetSafetyEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertClockTrust(clock: ClockTrustEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun putReadinessState(readiness: ReadinessStateEntity)

  @Query("SELECT * FROM accounts_v2 WHERE accountId = :accountId")
  abstract suspend fun getAccount(accountId: String): AccountRecordEntity?

  @Query("SELECT * FROM installation_bindings_v2 WHERE installationId = :installationId")
  abstract suspend fun getInstallation(installationId: String): InstallationBindingEntity?

  @Query("SELECT * FROM approval_snapshots_v2 WHERE approvalId = :approvalId")
  abstract suspend fun getApproval(approvalId: String): ApprovalSnapshotEntity?

  @Query("SELECT * FROM automation_policies_v2 WHERE policyId = :policyId")
  abstract suspend fun getAutomationPolicy(policyId: String): AutomationPolicyEntity?

  @Query("SELECT * FROM birthday_occurrences_v2 WHERE occurrenceId = :occurrenceId")
  abstract suspend fun getBirthdayOccurrence(occurrenceId: String): BirthdayOccurrenceRecordEntity?

  @Query("SELECT * FROM test_jobs_v2 WHERE testJobId = :testJobId")
  abstract suspend fun getTestJob(testJobId: String): TestJobEntity?

  @Query("SELECT * FROM coordination_permits_v2 WHERE permitId = :permitId")
  abstract suspend fun getCoordinationPermit(permitId: String): CoordinationPermitEntity?

  @Query("SELECT * FROM send_attempts_v2 WHERE sendAttemptId = :sendAttemptId")
  abstract suspend fun getSendAttempt(sendAttemptId: String): SendAttemptEntity?

  @Query(
    "SELECT COUNT(*) FROM send_attempts_v2 WHERE purpose = :purpose AND operationId = :operationId",
  )
  abstract suspend fun countSendAttempts(
    purpose: OperationPurpose,
    operationId: String,
  ): Int

  @Query(
    "SELECT * FROM outcome_projections_v2 WHERE purpose = :purpose AND operationId = :operationId",
  )
  abstract suspend fun getOutcomeProjection(
    purpose: OperationPurpose,
    operationId: String,
  ): OutcomeProjectionEntity?

  @Query("SELECT * FROM app_control WHERE singletonId = 1")
  protected abstract suspend fun controlRow(): ControlEntity?

  @Query("SELECT * FROM coordination_state_v2 WHERE accountId = :accountId")
  protected abstract suspend fun coordinationRow(accountId: String): CoordinationStateEntity?

  @Query("SELECT * FROM reset_safety_v2 WHERE accountId = :accountId")
  protected abstract suspend fun resetRow(accountId: String): ResetSafetyEntity?

  @Query("SELECT * FROM clock_trust_v2 WHERE accountId = :accountId")
  protected abstract suspend fun clockRow(accountId: String): ClockTrustEntity?

  @Query("SELECT * FROM contact_sync_state_v2 WHERE accountId = :accountId")
  protected abstract suspend fun syncRow(accountId: String): ContactSyncStateEntity?

  @Query("SELECT * FROM recipient_policies_v2 WHERE contactId = :contactId")
  protected abstract suspend fun recipientPolicyRow(contactId: String): RecipientPolicyEntity?

  @Query("SELECT * FROM contact_snapshots_v2 WHERE contactId = :contactId")
  protected abstract suspend fun contactRow(contactId: String): ContactSnapshotEntity?

  @Query("SELECT * FROM contact_phones_v2 WHERE phoneId = :phoneId")
  protected abstract suspend fun phoneRow(phoneId: String): ContactPhoneEntity?

  @Query("SELECT * FROM automation_policies_v2 WHERE policyId = :policyId")
  protected abstract suspend fun policyRow(policyId: String): AutomationPolicyEntity?

  @Query(
    "SELECT * FROM consent_receipts_v2 WHERE accountId = :accountId AND kind = :kind ORDER BY sequence DESC LIMIT 1",
  )
  protected abstract suspend fun latestConsentRow(
    accountId: String,
    kind: ConsentKind,
  ): ConsentReceiptEntity?

  @Query(
    "SELECT COUNT(*) FROM destination_blocks_v2 WHERE accountId = :accountId AND destinationFingerprint = :fingerprint AND active = 1",
  )
  protected abstract suspend fun activeDestinationBlockCount(accountId: String, fingerprint: String): Int

  @Query("SELECT * FROM local_destination_guards_v2 WHERE occurrenceId = :occurrenceId")
  protected abstract suspend fun destinationGuardRow(occurrenceId: String): LocalDestinationGuardEntity?

  @Query("SELECT * FROM callback_tokens_v2 WHERE callbackTokenId = :tokenId")
  protected abstract suspend fun callbackTokenRow(tokenId: String): CallbackTokenEntity?

  @Query(
    """
    SELECT token.* FROM callback_tokens_v2 token
    JOIN installation_bindings_v2 installation
      ON installation.installationId = token.installationId
    WHERE token.action = :action
      AND token.dataUri = :dataUri
      AND token.kind = :kind
      AND token.state IN ('EXPECTED', 'OBSERVED')
      AND token.expiresAtMillis > :observedAtMillis
      AND installation.localSlot = 1
      AND installation.state = 'ACTIVE'
      AND installation.callbackGeneration = token.callbackGeneration
    LIMIT 1
    """,
  )
  abstract suspend fun findLiveCallbackToken(
    action: String,
    dataUri: String,
    kind: CallbackKind,
    observedAtMillis: Long,
  ): CallbackTokenEntity?

  @Query(
    "SELECT * FROM send_attempts_v2 WHERE purpose = :purpose AND operationId = :operationId AND attemptNumber = :attemptNumber",
  )
  protected abstract suspend fun sendAttemptRow(
    purpose: OperationPurpose,
    operationId: String,
    attemptNumber: Int,
  ): SendAttemptEntity?

  @Query(
    "SELECT COUNT(*) FROM callback_tokens_v2 WHERE sendAttemptId = :sendAttemptId AND kind = 'SENT'",
  )
  protected abstract suspend fun sentCallbackTokenCount(sendAttemptId: String): Int

  @Query(
    """
    SELECT COUNT(DISTINCT t.callbackTokenId)
    FROM callback_tokens_v2 t
    JOIN delivery_events_v2 e ON e.callbackTokenId = t.callbackTokenId
    WHERE t.sendAttemptId = :sendAttemptId
      AND t.kind = 'SENT'
      AND e.evidenceClass = 'SENT_SUCCESS'
    """,
  )
  protected abstract suspend fun successfulSentCallbackCount(sendAttemptId: String): Int

  @Query(
    """
    SELECT COUNT(*)
    FROM callback_tokens_v2 t
    JOIN delivery_events_v2 e ON e.callbackTokenId = t.callbackTokenId
    WHERE t.sendAttemptId = :sendAttemptId
      AND t.kind = 'SENT'
      AND e.evidenceClass != 'SENT_SUCCESS'
    """,
  )
  protected abstract suspend fun contradictorySentEvidenceCount(sendAttemptId: String): Int

  @Query(
    """
    SELECT COUNT(*) FROM callback_tokens_v2
    WHERE sendAttemptId = :sendAttemptId
      AND state = 'EXPECTED'
      AND callbackRequestCode > 0
      AND length(action) BETWEEN 1 AND :maxActionLength
      AND action LIKE :actionPrefixPattern
      AND length(dataUri) BETWEEN 1 AND :maxDataUriLength
      AND dataUri LIKE :dataUriPrefixPattern
      AND createdAtMillis >= 0
      AND createdAtMillis < expiresAtMillis
      AND ((kind = 'SENT' AND mutableForPlatformFillIn = 0)
        OR (kind = 'DELIVERY' AND mutableForPlatformFillIn = 1))
    """,
  )
  protected abstract suspend fun validExpectedCallbackTokenCount(
    sendAttemptId: String,
    maxActionLength: Int,
    actionPrefixPattern: String,
    maxDataUriLength: Int,
    dataUriPrefixPattern: String,
  ): Int

  @Query("SELECT COUNT(*) FROM reset_blocked_dates_v2 WHERE resetSafetyId = :resetSafetyId")
  protected abstract suspend fun blockedDateCount(resetSafetyId: String): Int

  @Query(
    "SELECT * FROM reset_blocked_dates_v2 WHERE resetSafetyId = :resetSafetyId AND civilDate = :civilDate",
  )
  protected abstract suspend fun blockedDateRow(
    resetSafetyId: String,
    civilDate: String,
  ): ResetBlockedDateEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertBirthdayOccurrenceRow(occurrence: BirthdayOccurrenceRecordEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertDestinationGuardRow(guard: LocalDestinationGuardEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertCoordinationPermitRow(permit: CoordinationPermitEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertSendAttemptRow(attempt: SendAttemptEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertCallbackTokensRows(tokens: List<CallbackTokenEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  protected abstract suspend fun insertDeliveryEventRow(event: DeliveryEventEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  protected abstract suspend fun putOutcomeProjectionRow(projection: OutcomeProjectionEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertTestReceiptRow(receipt: TestReceiptEntity)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  protected abstract suspend fun insertBlockedDateRow(date: ResetBlockedDateEntity)

  @Query(
    """
    UPDATE birthday_occurrences_v2
    SET state = :nextState,
        attemptNumber = :nextAttempt,
        revision = revision + 1,
        claimedBlockerRevision = :claimedBlockerRevision,
        updatedAtMillis = :updatedAtMillis,
        safeOutcomeCode = :safeOutcomeCode
    WHERE occurrenceId = :occurrenceId
      AND state = :expectedState
      AND attemptNumber = :expectedAttempt
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casBirthdayState(
    occurrenceId: String,
    expectedState: BirthdayJobState,
    expectedAttempt: Int,
    expectedRevision: Long,
    nextState: BirthdayJobState,
    nextAttempt: Int,
    claimedBlockerRevision: Long?,
    updatedAtMillis: Long,
    safeOutcomeCode: String?,
  ): Int

  @Query(
    """
    UPDATE test_jobs_v2
    SET state = :nextState,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis,
        terminalAtMillis = :terminalAtMillis,
        invalidationReason = :reason
    WHERE testJobId = :testJobId
      AND state = :expectedState
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casTestState(
    testJobId: String,
    expectedState: TestJobState,
    expectedRevision: Long,
    nextState: TestJobState,
    updatedAtMillis: Long,
    terminalAtMillis: Long?,
    reason: String?,
  ): Int

  @Query(
    """
    UPDATE coordination_permits_v2
    SET state = 'ARM_RECONCILING',
        armRequestId = :armRequestId,
        armDispatched = 1,
        armStartBlockerRevision = :blockerRevision,
        trustedServerNowMillis = :trustedNowMillis,
        requestStartElapsedMillis = :requestStartElapsedMillis,
        bootCount = :bootCount,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis
    WHERE permitId = :permitId
      AND state = 'CLOUD_CLAIMED'
      AND armDispatched = 0
      AND armRequestId IS NULL
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casBeginArm(
    permitId: String,
    expectedRevision: Long,
    armRequestId: String,
    blockerRevision: Long,
    trustedNowMillis: Long,
    requestStartElapsedMillis: Long,
    bootCount: Int,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_permits_v2
    SET state = 'CLOUD_ARMED',
        trustedServerNowMillis = :serverNowMillis,
        requestStartElapsedMillis = :evidenceObservedElapsedMillis,
        serverSubmitNotAfterMillis = :serverSubmitNotAfterMillis,
        effectiveSubmitNotAfterMillis = :effectiveSubmitNotAfterMillis,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis
    WHERE permitId = :permitId
      AND state = 'ARM_RECONCILING'
      AND armRequestId = :armRequestId
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casMarkCloudArmed(
    permitId: String,
    armRequestId: String,
    expectedRevision: Long,
    serverNowMillis: Long,
    evidenceObservedElapsedMillis: Long,
    serverSubmitNotAfterMillis: Long,
    effectiveSubmitNotAfterMillis: Long,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_permits_v2
    SET state = 'BARRIER_CONSUMED',
        barrierConsumedAtMillis = :consumedAtMillis,
        revision = revision + 1,
        updatedAtMillis = :consumedAtMillis
    WHERE permitId = :permitId
      AND state = 'CLOUD_ARMED'
      AND revision = :expectedRevision
      AND barrierConsumedAtMillis IS NULL
    """,
  )
  protected abstract suspend fun casConsumeBarrier(
    permitId: String,
    expectedRevision: Long,
    consumedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_permits_v2
    SET state = 'ARMED_SUPPRESSED',
        trustedServerNowMillis = :serverNowMillis,
        serverSubmitNotAfterMillis = :serverSubmitNotAfterMillis,
        effectiveSubmitNotAfterMillis = :effectiveSubmitNotAfterMillis,
        noWriteReason = :reason,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis
    WHERE permitId = :permitId
      AND state = 'ARM_RECONCILING'
      AND armRequestId = :armRequestId
    """,
  )
  protected abstract suspend fun suppressArmedPermitRow(
    permitId: String,
    armRequestId: String,
    serverNowMillis: Long,
    serverSubmitNotAfterMillis: Long,
    effectiveSubmitNotAfterMillis: Long?,
    reason: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE coordination_permits_v2
    SET state = 'NO_WRITE',
        noWriteReason = :reason,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis
    WHERE permitId = :permitId
      AND state = 'ARM_RECONCILING'
      AND armRequestId = :armRequestId
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casRecordNoWrite(
    permitId: String,
    armRequestId: String,
    expectedRevision: Long,
    reason: String,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE local_destination_guards_v2
    SET armedOrLater = 1, armedAtMillis = COALESCE(armedAtMillis, :armedAtMillis)
    WHERE occurrenceId = :occurrenceId
    """,
  )
  protected abstract suspend fun markDestinationGuardArmed(
    occurrenceId: String,
    armedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE send_attempts_v2
    SET state = 'API_CALL_STARTED',
        apiBoundaryStartedAtMillis = :startedAtMillis,
        sentWatchdogAtMillis = :sentWatchdogAtMillis,
        revision = revision + 1
    WHERE sendAttemptId = :sendAttemptId
      AND permitId = :permitId
      AND state = 'BARRIER_CONSUMED'
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casStartApiBoundary(
    sendAttemptId: String,
    permitId: String,
    expectedRevision: Long,
    startedAtMillis: Long,
    sentWatchdogAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE send_attempts_v2
    SET state = 'SUBMITTED',
        submittedAtMillis = :submittedAtMillis,
        revision = revision + 1
    WHERE sendAttemptId = :sendAttemptId
      AND permitId = :permitId
      AND state = 'API_CALL_STARTED'
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casMarkSubmitted(
    sendAttemptId: String,
    permitId: String,
    expectedRevision: Long,
    submittedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE send_attempts_v2
    SET state = 'SENT_FROM_DEVICE', revision = revision + 1
    WHERE sendAttemptId = :sendAttemptId
      AND state = 'SUBMITTED'
      AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casAttemptSentFromDevice(
    sendAttemptId: String,
    expectedRevision: Long,
  ): Int

  @Query(
    """
    UPDATE callback_tokens_v2
    SET state = 'OBSERVED', observedAtMillis = COALESCE(observedAtMillis, :observedAtMillis)
    WHERE callbackTokenId = :tokenId AND state IN ('EXPECTED', 'OBSERVED')
    """,
  )
  protected abstract suspend fun markCallbackObserved(tokenId: String, observedAtMillis: Long): Int

  @Query(
    """
    UPDATE clock_trust_v2
    SET greatestTrustedServerMillis = :serverMillis,
        lastDeviceWallMillis = :deviceWallMillis,
        lastElapsedRealtimeMillis = :elapsedMillis,
        trustedBootCount = :bootCount,
        lastVerificationMillis = :verifiedAtMillis,
        observedDriftMillis = :driftMillis,
        revision = revision + 1
    WHERE accountId = :accountId
      AND status = 'TRUSTED'
      AND (greatestTrustedServerMillis IS NULL OR greatestTrustedServerMillis <= :serverMillis)
    """,
  )
  protected abstract suspend fun advanceTrustedClockRow(
    accountId: String,
    serverMillis: Long,
    deviceWallMillis: Long,
    elapsedMillis: Long,
    bootCount: Int,
    verifiedAtMillis: Long,
    driftMillis: Long,
  ): Int

  @Query(
    """
    UPDATE clock_trust_v2
    SET status = :status,
        lastDeviceWallMillis = :deviceWallMillis,
        lastVerificationMillis = :observedAtMillis,
        observedDriftMillis = :observedDriftMillis,
        revision = revision + 1
    WHERE accountId = :accountId AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casClockStatus(
    accountId: String,
    expectedRevision: Long,
    status: ClockTrustStatus,
    deviceWallMillis: Long,
    observedAtMillis: Long,
    observedDriftMillis: Long?,
  ): Int

  @Query(
    """
    UPDATE reset_safety_v2
    SET status = :status,
        overflowBlocked = :overflowBlocked,
        revision = revision + 1,
        updatedAtMillis = :updatedAtMillis
    WHERE resetSafetyId = :resetSafetyId AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casResetStatus(
    resetSafetyId: String,
    expectedRevision: Long,
    status: ResetSafetyStatus,
    overflowBlocked: Boolean,
    updatedAtMillis: Long,
  ): Int

  @Query(
    """
    UPDATE app_control
    SET revision = revision + 1, blockerRevision = blockerRevision + 1
    WHERE singletonId = 1 AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun bumpBlockerRevision(expectedRevision: Long): Int

  @Query(
    """
    UPDATE app_control
    SET revision = revision + 1,
        blockerRevision = blockerRevision + 1,
        accountMode = :mode,
        automationDesired = :automationDesired
    WHERE singletonId = 1 AND revision = :expectedRevision
    """,
  )
  protected abstract suspend fun casBlockingControlMutation(
    expectedRevision: Long,
    mode: String,
    automationDesired: Boolean,
  ): Int

  @Transaction
  open suspend fun createPlannedBirthdayOccurrence(
    occurrence: BirthdayOccurrenceRecordEntity,
    guard: LocalDestinationGuardEntity,
  ) {
    require(occurrence.state == BirthdayJobState.PLANNED) { "occurrence-not-planned" }
    require(occurrence.attemptNumber == 0 && occurrence.revision == 0L) { "occurrence-not-new" }
    require(occurrence.resolvedWindowEndMillis > occurrence.resolvedWindowStartMillis) {
      "occurrence-window-invalid"
    }
    require(guard.occurrenceId == occurrence.occurrenceId) { "guard-occurrence-mismatch" }
    require(guard.accountId == occurrence.accountId) { "guard-account-mismatch" }
    require(guard.destinationFingerprint == occurrence.destinationFingerprint) {
      "guard-destination-mismatch"
    }
    require(guard.localDate == occurrence.localDate && guard.channel == occurrence.channel) {
      "guard-scope-mismatch"
    }
    require(!guard.armedOrLater) { "new-guard-already-armed" }
    val approval = getApproval(occurrence.approvalId)
      ?: throw IllegalStateException("approval-missing")
    require(approval.state == ApprovalRecordState.ACTIVE) { "approval-not-active" }
    require(approval.accountId == occurrence.accountId && approval.contactId == occurrence.contactId) {
      "approval-owner-mismatch"
    }
    require(approval.contentHash == occurrence.payloadHash) { "approval-payload-mismatch" }
    require(approval.destinationFingerprint == occurrence.destinationFingerprint) {
      "approval-destination-mismatch"
    }
    val policy = policyRow(occurrence.policyId) ?: throw IllegalStateException("policy-missing")
    require(policy.accountId == occurrence.accountId && policy.state == PolicyRecordState.ACTIVE) {
      "policy-not-active"
    }
    require(policy.revision == approval.policyRevision && policy.policyId == approval.policyId) {
      "approval-policy-mismatch"
    }
    val recipient = recipientPolicyRow(occurrence.contactId)
      ?: throw IllegalStateException("recipient-policy-missing")
    require(recipient.state == RecipientEnrollmentState.ENABLED) { "recipient-not-enabled" }
    require(recipient.chosenPhoneId == approval.phoneId && recipient.approvalId == approval.approvalId) {
      "recipient-material-mismatch"
    }
    insertBirthdayOccurrenceRow(occurrence)
    insertDestinationGuardRow(guard)
  }

  @Transaction
  open suspend fun claimBirthdayOccurrence(
    occurrenceId: String,
    expectedRevision: Long,
    trustedNowMillis: Long,
  ): Boolean {
    val occurrence = getBirthdayOccurrence(occurrenceId) ?: return false
    if (occurrence.revision != expectedRevision || occurrence.state != BirthdayJobState.SCHEDULED) {
      return false
    }
    val blockerRevision = validateBirthdayPreflight(occurrence, trustedNowMillis) ?: return false
    return casBirthdayState(
      occurrenceId = occurrenceId,
      expectedState = BirthdayJobState.SCHEDULED,
      expectedAttempt = occurrence.attemptNumber,
      expectedRevision = occurrence.revision,
      nextState = BirthdayJobState.CLAIMED,
      nextAttempt = 1,
      claimedBlockerRevision = blockerRevision,
      updatedAtMillis = trustedNowMillis,
      safeOutcomeCode = null,
    ) == 1
  }

  @Transaction
  open suspend fun recordCloudClaim(permit: CoordinationPermitEntity): Boolean {
    if (
      permit.state != CoordinationPermitState.CLOUD_CLAIMED ||
      permit.armDispatched ||
      permit.armRequestId != null ||
      permit.armStartBlockerRevision != null ||
      permit.serverSubmitNotAfterMillis != null ||
      permit.effectiveSubmitNotAfterMillis != null ||
      permit.claimExpiresAtMillis <= permit.trustedServerNowMillis ||
      permit.maxPossibleSubmitNotAfterMillis <= permit.claimExpiresAtMillis ||
      permit.unresolvedArmCutoffMillis > permit.maxPossibleSubmitNotAfterMillis
    ) return false

    val control = controlRow() ?: return false
    val installation = getInstallation(permit.installationId) ?: return false
    if (
      installation.accountId != permit.accountId ||
      installation.state != InstallationRecordState.ACTIVE ||
      installation.senderEpoch != permit.senderEpoch ||
      installation.resetGeneration != permit.resetGeneration ||
      control.blockerRevision < 0
    ) return false

    when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = getBirthdayOccurrence(permit.operationId) ?: return false
        if (
          occurrence.accountId != permit.accountId ||
          occurrence.state != BirthdayJobState.CLAIMED ||
          occurrence.attemptNumber != permit.attemptNumber ||
          occurrence.payloadHash != permit.payloadHash ||
          occurrence.claimedBlockerRevision != control.blockerRevision ||
          permit.unresolvedArmCutoffMillis != minOf(
            permit.maxPossibleSubmitNotAfterMillis,
            occurrence.resolvedWindowEndMillis,
          )
        ) return false
        if (casBirthdayState(
          occurrence.occurrenceId,
          BirthdayJobState.CLAIMED,
          occurrence.attemptNumber,
          occurrence.revision,
          BirthdayJobState.CLOUD_CLAIMED,
          occurrence.attemptNumber,
          occurrence.claimedBlockerRevision,
          permit.updatedAtMillis,
          null,
        ) != 1) return false
        insertCoordinationPermitRow(permit)
        return true
      }
      OperationPurpose.TEST -> {
        val test = getTestJob(permit.operationId) ?: return false
        if (
          test.accountId != permit.accountId ||
          test.installationId != permit.installationId ||
          test.senderEpoch != permit.senderEpoch ||
          test.state != TestJobState.PREPARED ||
          test.payloadHash != permit.payloadHash ||
          permit.attemptNumber != 1 ||
          permit.unresolvedArmCutoffMillis != permit.maxPossibleSubmitNotAfterMillis
        ) return false
        if (casTestState(
          test.testJobId,
          TestJobState.PREPARED,
          test.revision,
          TestJobState.CLOUD_CLAIMED,
          permit.updatedAtMillis,
          null,
          null,
        ) != 1) return false
        insertCoordinationPermitRow(permit)
        return true
      }
    }
  }

  @Transaction
  open suspend fun beginArmDispatch(
    permitId: String,
    expectedPermitRevision: Long,
    armRequestId: String,
    trustedNowMillis: Long,
    currentElapsedRealtimeMillis: Long? = null,
    currentBootCount: Int? = null,
  ): ArmDispatchResult {
    if (!UUID_SHAPE.matches(armRequestId)) return ArmDispatchResult.Rejected("arm-request-invalid")
    val permit = getCoordinationPermit(permitId)
      ?: return ArmDispatchResult.Rejected("permit-missing")
    if (
      permit.revision != expectedPermitRevision ||
      permit.state != CoordinationPermitState.CLOUD_CLAIMED ||
      permit.armDispatched ||
      (currentElapsedRealtimeMillis != null && currentElapsedRealtimeMillis < 0) ||
      (currentBootCount != null && currentBootCount < 0) ||
      trustedNowMillis >= permit.claimExpiresAtMillis ||
      trustedNowMillis >= permit.unresolvedArmCutoffMillis
    ) return ArmDispatchResult.Rejected("claim-not-armable")
    val control = controlRow() ?: return ArmDispatchResult.Rejected("control-missing")
    val valid = when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = getBirthdayOccurrence(permit.operationId)
        val expectedState = if (permit.attemptNumber == 1) {
          BirthdayJobState.CLOUD_CLAIMED
        } else {
          BirthdayJobState.RETRY_CLAIMED
        }
        occurrence != null &&
          permit.attemptNumber in 1..2 &&
          occurrence.state == expectedState &&
          occurrence.attemptNumber == permit.attemptNumber &&
          (permit.attemptNumber == 1 ||
            countSendAttempts(OperationPurpose.BIRTHDAY, occurrence.occurrenceId) == 1) &&
          validateBirthdayPreflight(
            occurrence,
            trustedNowMillis,
            requirePreviouslyArmedGuard = permit.attemptNumber == 2,
          ) == control.blockerRevision
      }
      OperationPurpose.TEST -> validateTestPreflight(permit, trustedNowMillis)
    }
    if (!valid) return ArmDispatchResult.Rejected("local-preflight-failed")
    if (
      casBeginArm(
        permit.permitId,
        permit.revision,
        armRequestId,
        control.blockerRevision,
        trustedNowMillis,
        currentElapsedRealtimeMillis ?: permit.requestStartElapsedMillis,
        currentBootCount ?: permit.bootCount,
        trustedNowMillis,
      ) != 1
    ) return ArmDispatchResult.Rejected("arm-cas-lost")

    val operationAdvanced = when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = checkNotNull(getBirthdayOccurrence(permit.operationId))
        val expectedState = if (permit.attemptNumber == 1) {
          BirthdayJobState.CLOUD_CLAIMED
        } else {
          BirthdayJobState.RETRY_CLAIMED
        }
        casBirthdayState(
          occurrence.occurrenceId,
          expectedState,
          occurrence.attemptNumber,
          occurrence.revision,
          BirthdayJobState.ARM_RECONCILING,
          occurrence.attemptNumber,
          occurrence.claimedBlockerRevision,
          trustedNowMillis,
          null,
        )
      }
      OperationPurpose.TEST -> {
        val test = checkNotNull(getTestJob(permit.operationId))
        casTestState(
          test.testJobId,
          TestJobState.CLOUD_CLAIMED,
          test.revision,
          TestJobState.ARM_RECONCILING,
          trustedNowMillis,
          null,
          null,
        )
      }
    }
    check(operationAdvanced == 1) { "arm-operation-cas-lost" }
    return ArmDispatchResult.Committed(control.blockerRevision)
  }

  @Transaction
  open suspend fun consumeAuthoritativeArmedEvidence(
    permitId: String,
    expectedPermitRevision: Long,
    evidence: AuthoritativeArmedEvidence,
    external: FinalExternalGateSnapshot,
    deviceWallMillis: Long,
    currentElapsedRealtimeMillis: Long,
    currentBootCount: Int,
    sendAttemptId: String,
    callbackGeneration: String,
    sentWatchdogAtMillis: Long,
    retentionUntilMillis: Long,
  ): PermitIssueResult {
    val permit = getCoordinationPermit(permitId)
      ?: return PermitIssueResult.Suppressed("permit-missing")
    if (
      permit.state != CoordinationPermitState.ARM_RECONCILING ||
      permit.revision != expectedPermitRevision ||
      permit.armRequestId != evidence.armRequestId ||
      !permit.armDispatched
    ) return PermitIssueResult.Suppressed("arm-evidence-mismatch")
    if (
      sendAttemptId.isBlank() ||
      callbackGeneration.isBlank() ||
      sentWatchdogAtMillis <= deviceWallMillis ||
      retentionUntilMillis <= sentWatchdogAtMillis
    ) return PermitIssueResult.Suppressed("attempt-metadata-invalid")

    val effectiveDeadline = when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = getBirthdayOccurrence(permit.operationId)
        if (occurrence == null) null else minOf(
          evidence.serverSubmitNotAfterMillis,
          occurrence.resolvedWindowEndMillis,
        )
      }
      OperationPurpose.TEST -> evidence.serverSubmitNotAfterMillis
    }

    suspend fun suppress(reason: String): PermitIssueResult.Suppressed {
      suppressAuthoritativeArm(
        permit,
        evidence,
        effectiveDeadline,
        reason,
        deviceWallMillis,
      )
      return PermitIssueResult.Suppressed(reason)
    }

    if (
      evidence.serverSubmitNotAfterMillis > permit.maxPossibleSubmitNotAfterMillis ||
      evidence.serverSubmitNotAfterMillis <= evidence.serverNowMillis ||
      effectiveDeadline == null ||
      effectiveDeadline <= evidence.serverNowMillis
    ) return suppress("armed-deadline-invalid-or-closed")
    val serverPermitLength = subtractExactOrNull(
      evidence.serverSubmitNotAfterMillis,
      evidence.serverNowMillis,
    ) ?: return suppress("server-deadline-overflow")
    if (serverPermitLength !in 1..MAX_SERVER_PERMIT_MILLIS) {
      return suppress("server-deadline-too-long")
    }
    val remainingPermitMillis = subtractExactOrNull(effectiveDeadline, evidence.serverNowMillis)
      ?: return suppress("effective-deadline-overflow")
    val effectiveElapsedDeadline = try {
      Math.addExact(external.observedAtElapsedRealtimeMillis, remainingPermitMillis)
    } catch (_: ArithmeticException) {
      return suppress("elapsed-deadline-overflow")
    }
    if (
      external.observedAtElapsedRealtimeMillis < 0 ||
      currentElapsedRealtimeMillis < 0 ||
      currentElapsedRealtimeMillis >= effectiveElapsedDeadline
    ) return suppress("elapsed-deadline-closed")

    val control = controlRow() ?: return suppress("control-missing")
    val blockerRevision = permit.armStartBlockerRevision
    if (blockerRevision == null || control.blockerRevision != blockerRevision) {
      return suppress("blocker-revision-changed")
    }
    val clock = clockRow(permit.accountId) ?: return suppress("clock-missing")
    if (
      clock.status != ClockTrustStatus.TRUSTED ||
      (clock.greatestTrustedServerMillis ?: Long.MIN_VALUE) > evidence.serverNowMillis ||
      !withinClockTolerance(deviceWallMillis, evidence.serverNowMillis)
    ) return suppress("clock-untrusted")

    val installation = getInstallation(permit.installationId)
      ?: return suppress("installation-missing")
    val coordination = coordinationRow(permit.accountId)
      ?: return suppress("coordination-state-missing")
    if (
      installation.state != InstallationRecordState.ACTIVE ||
      installation.accountId != permit.accountId ||
      installation.senderEpoch != permit.senderEpoch ||
      installation.resetGeneration != permit.resetGeneration ||
      installation.callbackGeneration != callbackGeneration ||
      coordination.activeInstallationId != permit.installationId ||
      coordination.senderEpoch != permit.senderEpoch ||
      coordination.resetGeneration != permit.resetGeneration ||
      (coordination.ownerLeaseUntilMillis ?: Long.MIN_VALUE) <= evidence.serverNowMillis
    ) return suppress("installation-or-epoch-changed")

    val gateAge = currentElapsedRealtimeMillis - external.observedAtElapsedRealtimeMillis
    if (
      currentBootCount != permit.bootCount ||
      external.bootCount != currentBootCount ||
      gateAge !in 0..MAX_EXTERNAL_GATE_AGE_MILLIS ||
      !external.distributionEligible ||
      !external.accountSessionValid ||
      !external.contactsAuthorizationValid ||
      !external.networkValidated ||
      !external.smsPermissionGranted ||
      !external.simReady ||
      external.payloadHash != permit.payloadHash
    ) return suppress("external-gate-failed")

    val operationValid = when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> validateBirthdayArmedBoundary(
        permit,
        external,
        evidence.serverNowMillis,
      )
      OperationPurpose.TEST -> validateTestArmedBoundary(
        permit,
        external,
        evidence.serverNowMillis,
      )
    }
    if (!operationValid) return suppress("operation-binding-failed")

    if (
      advanceTrustedClockRow(
        accountId = permit.accountId,
        serverMillis = evidence.serverNowMillis,
        deviceWallMillis = deviceWallMillis,
        elapsedMillis = external.observedAtElapsedRealtimeMillis,
        bootCount = external.bootCount,
        verifiedAtMillis = deviceWallMillis,
        driftMillis = checkNotNull(subtractExactOrNull(deviceWallMillis, evidence.serverNowMillis)),
      ) != 1
    ) return suppress("trusted-clock-cas-failed")

    if (
      casMarkCloudArmed(
        permit.permitId,
        evidence.armRequestId,
        permit.revision,
        evidence.serverNowMillis,
        external.observedAtElapsedRealtimeMillis,
        evidence.serverSubmitNotAfterMillis,
        effectiveDeadline,
        deviceWallMillis,
      ) != 1
    ) return PermitIssueResult.Suppressed("armed-cas-lost")

    val cloudArmed = checkNotNull(getCoordinationPermit(permit.permitId))
    check(cloudArmed.state == CoordinationPermitState.CLOUD_ARMED)
    check(casConsumeBarrier(cloudArmed.permitId, cloudArmed.revision, deviceWallMillis) == 1) {
      "barrier-cas-lost"
    }

    val operationAdvanced = when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = checkNotNull(getBirthdayOccurrence(permit.operationId))
        check(markDestinationGuardArmed(occurrence.occurrenceId, deviceWallMillis) == 1) {
          "destination-guard-missing"
        }
        casBirthdayState(
          occurrence.occurrenceId,
          BirthdayJobState.ARM_RECONCILING,
          occurrence.attemptNumber,
          occurrence.revision,
          BirthdayJobState.SUBMISSION_BARRIER_CONSUMED,
          occurrence.attemptNumber,
          occurrence.claimedBlockerRevision,
          deviceWallMillis,
          null,
        )
      }
      OperationPurpose.TEST -> {
        val test = checkNotNull(getTestJob(permit.operationId))
        casTestState(
          test.testJobId,
          TestJobState.ARM_RECONCILING,
          test.revision,
          TestJobState.BARRIER_CONSUMED,
          deviceWallMillis,
          null,
          null,
        )
      }
    }
    check(operationAdvanced == 1) { "barrier-operation-cas-lost" }

    val approvalPartCount = when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = checkNotNull(getBirthdayOccurrence(permit.operationId))
        checkNotNull(getApproval(occurrence.approvalId)).segmentCount
      }
      OperationPurpose.TEST -> checkNotNull(getTestJob(permit.operationId)).segmentCount
    }
    insertSendAttemptRow(
      SendAttemptEntity(
        sendAttemptId = sendAttemptId,
        permitId = permit.permitId,
        installationId = permit.installationId,
        callbackGeneration = callbackGeneration,
        purpose = permit.purpose,
        operationId = permit.operationId,
        attemptNumber = permit.attemptNumber,
        payloadHash = permit.payloadHash,
        resolvedSubscriptionId = external.currentSubscriptionId,
        expectedPartCount = approvalPartCount,
        state = SendAttemptState.BARRIER_CONSUMED,
        apiBoundaryStartedAtMillis = null,
        submittedAtMillis = null,
        sentWatchdogAtMillis = sentWatchdogAtMillis,
        deliveryWatchdogAtMillis = null,
        terminalAtMillis = null,
        safeOutcomeCode = null,
        revision = 0,
        retentionUntilMillis = retentionUntilMillis,
      ),
    )
    val consumed = checkNotNull(getCoordinationPermit(permit.permitId))
    return PermitIssueResult.Issued(
      ArmedAttemptPermitIssuer.issue(
        permit = consumed,
        sendAttemptId = sendAttemptId,
        foregroundConfirmationNonceHash = external.foregroundConfirmationNonceHash
          .takeIf { consumed.purpose == OperationPurpose.TEST },
      ),
    )
  }

  @Transaction
  open suspend fun recordAuthoritativeNoWrite(
    permitId: String,
    expectedPermitRevision: Long,
    armRequestId: String,
    typedReason: String,
    recordedAtMillis: Long,
  ): Boolean {
    if (typedReason !in NO_WRITE_REASONS) return false
    val permit = getCoordinationPermit(permitId) ?: return false
    if (
      permit.state != CoordinationPermitState.ARM_RECONCILING ||
      permit.armRequestId != armRequestId ||
      permit.revision != expectedPermitRevision
    ) return false
    if (casRecordNoWrite(permitId, armRequestId, expectedPermitRevision, typedReason, recordedAtMillis) != 1) {
      return false
    }
    when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = checkNotNull(getBirthdayOccurrence(permit.operationId)) {
          "no-write-birthday-operation-missing"
        }
        val next = when (typedReason) {
          "EXPIRED" -> BirthdayJobState.MISSED
          "EXPIRED_RETRY" -> BirthdayJobState.RETRY_EXHAUSTED
          else -> BirthdayJobState.CANCELLED
        }
        check(
          casBirthdayState(
            occurrence.occurrenceId,
            BirthdayJobState.ARM_RECONCILING,
            occurrence.attemptNumber,
            occurrence.revision,
            next,
            occurrence.attemptNumber,
            occurrence.claimedBlockerRevision,
            recordedAtMillis,
            "NO_WRITE_$typedReason",
          ) == 1,
        ) { "no-write-operation-cas-lost" }
      }
      OperationPurpose.TEST -> {
        val test = checkNotNull(getTestJob(permit.operationId)) {
          "no-write-test-operation-missing"
        }
        check(
          casTestState(
            test.testJobId,
            TestJobState.ARM_RECONCILING,
            test.revision,
            TestJobState.FAILED,
            recordedAtMillis,
            recordedAtMillis,
            "NO_WRITE_$typedReason",
          ) == 1,
        ) { "test-no-write-operation-cas-lost" }
      }
    }
    return true
  }

  /** Called by the sole SMS gateway immediately before its one SmsManager call. */
  @Transaction
  open suspend fun commitApiBoundary(
    permit: ArmedAttemptPermit,
    expectedAttemptRevision: Long,
    currentElapsedRealtimeMillis: Long,
    currentBootCount: Int,
    apiBoundaryWallMillis: Long,
    payloadHash: String,
    subscriptionId: Int,
  ): Boolean {
    if (
      permit.bootCount != currentBootCount ||
      currentElapsedRealtimeMillis >= permit.deadlineElapsedRealtimeMillis ||
      permit.payloadHash != payloadHash
    ) return false
    if (apiBoundaryWallMillis < 0) return false
    val durablePermit = getCoordinationPermit(permit.permitId) ?: return false
    val attempt = getSendAttempt(permit.sendAttemptId) ?: return false
    val maximumSentDeadline = try {
      Math.addExact(apiBoundaryWallMillis, SENT_WATCHDOG_MILLIS)
    } catch (_: ArithmeticException) {
      return false
    }
    val sentWatchdogAtMillis = when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> minOf(
        maximumSentDeadline,
        getBirthdayOccurrence(permit.operationId)?.resolvedWindowEndMillis ?: return false,
      )
      OperationPurpose.TEST -> maximumSentDeadline
    }
    if (
      durablePermit.state != CoordinationPermitState.BARRIER_CONSUMED ||
      durablePermit.barrierConsumedAtMillis == null ||
      durablePermit.operationId != permit.operationId ||
      durablePermit.attemptNumber != permit.attemptNumber ||
      durablePermit.installationId != permit.installationId ||
      durablePermit.senderEpoch != permit.senderEpoch ||
      durablePermit.payloadHash != payloadHash ||
      attempt.permitId != permit.permitId ||
      attempt.payloadHash != payloadHash ||
      attempt.resolvedSubscriptionId != subscriptionId ||
      attempt.revision != expectedAttemptRevision ||
      sentWatchdogAtMillis <= apiBoundaryWallMillis ||
      sentWatchdogAtMillis > attempt.retentionUntilMillis ||
      validExpectedCallbackTokenCount(
        attempt.sendAttemptId,
        MAX_CALLBACK_ACTION_LENGTH,
        "$CALLBACK_ACTION_PREFIX%",
        MAX_CALLBACK_DATA_URI_LENGTH,
        "$CALLBACK_DATA_URI_PREFIX%",
      ) != attempt.expectedPartCount * CallbackKind.entries.size
    ) return false
    return casStartApiBoundary(
      attempt.sendAttemptId,
      durablePermit.permitId,
      attempt.revision,
      apiBoundaryWallMillis,
      sentWatchdogAtMillis,
    ) == 1
  }

  @Transaction
  open suspend fun markSmsManagerAccepted(
    permit: ArmedAttemptPermit,
    expectedAttemptRevision: Long,
    submittedAtMillis: Long,
  ): Boolean {
    val attempt = getSendAttempt(permit.sendAttemptId) ?: return false
    if (
      attempt.state != SendAttemptState.API_CALL_STARTED ||
      attempt.revision != expectedAttemptRevision ||
      attempt.permitId != permit.permitId ||
      attempt.payloadHash != permit.payloadHash
    ) return false
    if (
      casMarkSubmitted(
        attempt.sendAttemptId,
        permit.permitId,
        attempt.revision,
        submittedAtMillis,
      ) != 1
    ) return false
    when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = checkNotNull(getBirthdayOccurrence(permit.operationId)) {
          "submitted-birthday-operation-missing"
        }
        check(
          casBirthdayState(
            occurrence.occurrenceId,
            BirthdayJobState.SUBMISSION_BARRIER_CONSUMED,
            occurrence.attemptNumber,
            occurrence.revision,
            BirthdayJobState.SUBMITTED,
            occurrence.attemptNumber,
            occurrence.claimedBlockerRevision,
            submittedAtMillis,
            null,
          ) == 1,
        ) { "submitted-operation-cas-lost" }
      }
      OperationPurpose.TEST -> {
        val test = checkNotNull(getTestJob(permit.operationId)) {
          "submitted-test-operation-missing"
        }
        check(
          casTestState(
            test.testJobId,
            TestJobState.BARRIER_CONSUMED,
            test.revision,
            TestJobState.SUBMITTED,
            submittedAtMillis,
            null,
            null,
          ) == 1,
        ) { "test-submitted-operation-cas-lost" }
      }
    }
    return true
  }

  @Transaction
  open suspend fun registerCallbackTokens(
    permit: ArmedAttemptPermit,
    tokens: List<CallbackTokenEntity>,
  ) {
    val attempt = getSendAttempt(permit.sendAttemptId)
      ?: throw IllegalStateException("send-attempt-missing")
    val durablePermit = getCoordinationPermit(permit.permitId)
      ?: throw IllegalStateException("coordination-permit-missing")
    val barrierConsumedAt = checkNotNull(durablePermit.barrierConsumedAtMillis) {
      "barrier-not-consumed"
    }
    require(tokens.size == attempt.expectedPartCount * 2) { "callback-token-count-invalid" }
    require(tokens.map { it.callbackRequestCode }.distinct().size == tokens.size) {
      "callback-request-code-duplicate"
    }
    val grouped = tokens.groupBy { it.partIndex }
    require(grouped.keys == (0 until attempt.expectedPartCount).toSet()) { "callback-parts-incomplete" }
    require(grouped.values.all { part -> part.map { it.kind }.toSet() == CallbackKind.entries.toSet() }) {
      "callback-kinds-incomplete"
    }
    require(tokens.all {
      it.sendAttemptId == attempt.sendAttemptId &&
        it.installationId == permit.installationId &&
        it.callbackGeneration == attempt.callbackGeneration &&
        it.attemptNumber == permit.attemptNumber &&
        it.callbackTokenId.isNotBlank() &&
        it.callbackRequestCode > 0 &&
        it.action.length in 1..MAX_CALLBACK_ACTION_LENGTH &&
        it.action.startsWith(CALLBACK_ACTION_PREFIX) &&
        it.dataUri.length in 1..MAX_CALLBACK_DATA_URI_LENGTH &&
        it.dataUri.startsWith(CALLBACK_DATA_URI_PREFIX) &&
        it.createdAtMillis >= barrierConsumedAt &&
        it.createdAtMillis < it.expiresAtMillis &&
        it.expiresAtMillis <= attempt.retentionUntilMillis &&
        it.state == CallbackTokenState.EXPECTED &&
        (it.kind == CallbackKind.DELIVERY) == it.mutableForPlatformFillIn
    }) { "callback-identity-mismatch" }
    insertCallbackTokensRows(tokens)
  }

  @Transaction
  open suspend fun recordDeliveryEvent(event: DeliveryEventEntity): Boolean {
    val token = callbackTokenRow(event.callbackTokenId) ?: return false
    if (token.state !in setOf(CallbackTokenState.EXPECTED, CallbackTokenState.OBSERVED)) return false
    if (
      !UUID_SHAPE.matches(event.eventId) ||
      !SHA256_SHAPE.matches(event.evidenceKey) ||
      event.receivedAtMillis !in token.createdAtMillis until token.expiresAtMillis ||
      event.callbackTokenId != token.callbackTokenId ||
      (token.kind == CallbackKind.SENT && event.evidenceClass.name.startsWith("DELIVERY_")) ||
      (token.kind == CallbackKind.DELIVERY && event.evidenceClass.name.startsWith("SENT_")) ||
      !validDeliveryEvidence(event)
    ) return false
    val inserted = insertDeliveryEventRow(event) != -1L
    if (inserted) check(markCallbackObserved(token.callbackTokenId, event.receivedAtMillis) == 1)
    return inserted
  }

  @Transaction
  open suspend fun mintPassingTestReceipt(
    testJobId: String,
    expectedTestRevision: Long,
    receipt: TestReceiptEntity,
    passedAtMillis: Long,
  ): Boolean {
    val test = getTestJob(testJobId) ?: return false
    if (test.state != TestJobState.SUBMITTED || test.revision != expectedTestRevision) return false
    val attempt = sendAttemptRow(OperationPurpose.TEST, testJobId, 1) ?: return false
    val installation = getInstallation(test.installationId) ?: return false
    val expectedExactTextHash = TestReceiptCanonicalHash.sha256Text(test.exactMessage)
    val expectedBindingHash = TestReceiptCanonicalHash.bindingHash(test, installation, receipt)
    if (
      attempt.state != SendAttemptState.SUBMITTED ||
      attempt.expectedPartCount != test.segmentCount ||
      sentCallbackTokenCount(attempt.sendAttemptId) != attempt.expectedPartCount ||
      successfulSentCallbackCount(attempt.sendAttemptId) != attempt.expectedPartCount ||
      contradictorySentEvidenceCount(attempt.sendAttemptId) != 0 ||
      receipt.testJobId != test.testJobId ||
      receipt.testReceiptId.isBlank() ||
      receipt.accountId != test.accountId ||
      receipt.configHash != test.configHash ||
      receipt.maskedDestination != test.maskedDestination ||
      receipt.segmentPlanHash != test.orderedPartsHash ||
      receipt.resolvedSubscriptionId != test.resolvedSubscriptionId ||
      receipt.installationId != test.installationId ||
      receipt.senderEpoch != test.senderEpoch ||
      receipt.buildBindingHash != test.buildBindingHash ||
      receipt.distributionChannel != installation.distributionChannel ||
      receipt.appCheckPolicyVersion != test.appCheckPolicyVersion ||
      receipt.state != TestReceiptState.VALID ||
      receipt.invalidatedAtMillis != null ||
      receipt.invalidationReason != null ||
      receipt.passedAtMillis != passedAtMillis ||
      passedAtMillis <= 0 ||
      receipt.bindingHash.isBlank() ||
      receipt.destinationBindingHash != test.destinationPrehash ||
      receipt.exactTextHash != expectedExactTextHash ||
      receipt.smsPolicyVersion.isBlank() ||
      !TestReceiptCanonicalHash.matches(receipt.bindingHash, expectedBindingHash)
    ) return false
    check(casAttemptSentFromDevice(attempt.sendAttemptId, attempt.revision) == 1) {
      "test-attempt-sent-cas-lost"
    }
    check(
      casTestState(
        test.testJobId,
        TestJobState.SUBMITTED,
        test.revision,
        TestJobState.SENT_FROM_DEVICE,
        passedAtMillis,
        null,
        null,
      ) == 1,
    ) { "test-sent-state-cas-lost" }
    val sent = checkNotNull(getTestJob(test.testJobId))
    check(
      casTestState(
        sent.testJobId,
        TestJobState.SENT_FROM_DEVICE,
        sent.revision,
        TestJobState.PASSED,
        passedAtMillis,
        passedAtMillis,
        null,
      ) == 1,
    ) { "test-passed-state-cas-lost" }
    insertTestReceiptRow(receipt)
    return true
  }

  @Transaction
  open suspend fun applyBlockingControlMutation(
    expectedControlRevision: Long,
    mode: AccountMode,
    automationDesired: Boolean,
  ): Boolean {
    if (mode == AccountMode.AUTOMATION_ACTIVE && !automationDesired) return false
    if (mode != AccountMode.AUTOMATION_ACTIVE && automationDesired) return false
    return casBlockingControlMutation(
      expectedControlRevision,
      mode.name,
      automationDesired,
    ) == 1
  }

  @Transaction
  open suspend fun addResetBlockedDate(
    accountId: String,
    blockedDate: ResetBlockedDateEntity,
    expectedControlRevision: Long,
    observedAtMillis: Long,
  ): Boolean {
    val reset = resetRow(accountId) ?: return false
    if (blockedDate.resetSafetyId != reset.resetSafetyId) return false
    if (blockedDateRow(reset.resetSafetyId, blockedDate.civilDate) != null) return true
    if (blockedDateCount(reset.resetSafetyId) >= MAX_LIVE_RESET_DATES) {
      if (!reset.overflowBlocked) {
        check(
          casResetStatus(
            reset.resetSafetyId,
            reset.revision,
            ResetSafetyStatus.OVERFLOW_BLOCKED,
            true,
            observedAtMillis,
          ) == 1,
        )
        check(bumpBlockerRevision(expectedControlRevision) == 1)
      }
      return false
    }
    insertBlockedDateRow(blockedDate)
    if (reset.status == ResetSafetyStatus.CLEAR) {
      check(
        casResetStatus(
          reset.resetSafetyId,
          reset.revision,
          ResetSafetyStatus.BLOCKED,
          false,
          observedAtMillis,
        ) == 1,
      )
      check(bumpBlockerRevision(expectedControlRevision) == 1)
    }
    return true
  }

  @Transaction
  open suspend fun persistBenignTrustedClockObservation(
    accountId: String,
    serverMillis: Long,
    deviceWallMillis: Long,
    elapsedMillis: Long,
    bootCount: Int,
    verifiedAtMillis: Long,
  ): Boolean {
    val reset = resetRow(accountId) ?: return false
    if (reset.status != ResetSafetyStatus.CLEAR || reset.overflowBlocked) return false
    if (!withinClockTolerance(deviceWallMillis, serverMillis)) return false
    val drift = subtractExactOrNull(deviceWallMillis, serverMillis) ?: return false
    return advanceTrustedClockRow(
      accountId,
      serverMillis,
      deviceWallMillis,
      elapsedMillis,
      bootCount,
      verifiedAtMillis,
      drift,
    ) == 1
  }

  /**
   * Entering or leaving a blocking clock status is never a benign refresh: it shares the durable
   * blocker revision so a later Arm response cannot survive a repair/revert ABA sequence.
   */
  @Transaction
  open suspend fun applyBlockingClockStatus(
    accountId: String,
    expectedClockRevision: Long,
    expectedControlRevision: Long,
    status: ClockTrustStatus,
    deviceWallMillis: Long,
    observedAtMillis: Long,
    observedDriftMillis: Long?,
  ): Boolean {
    val clock = clockRow(accountId) ?: return false
    if (clock.revision != expectedClockRevision || clock.status == status) return false
    if (
      casClockStatus(
        accountId,
        expectedClockRevision,
        status,
        deviceWallMillis,
        observedAtMillis,
        observedDriftMillis,
      ) != 1
    ) return false
    check(bumpBlockerRevision(expectedControlRevision) == 1) { "clock-blocker-cas-lost" }
    return true
  }

  private suspend fun validateBirthdayPreflight(
    occurrence: BirthdayOccurrenceRecordEntity,
    trustedNowMillis: Long,
    requirePreviouslyArmedGuard: Boolean = false,
  ): Long? {
    val control = controlRow() ?: return null
    if (
      control.accountMode != AccountMode.AUTOMATION_ACTIVE.name ||
      !control.automationDesired ||
      trustedNowMillis < occurrence.resolvedWindowStartMillis ||
      trustedNowMillis >= occurrence.resolvedWindowEndMillis
    ) return null
    val account = getAccount(occurrence.accountId) ?: return null
    if (account.state != AccountRecordState.ACTIVE || account.activeSlot != 1) return null
    val approval = getApproval(occurrence.approvalId) ?: return null
    val policy = policyRow(occurrence.policyId) ?: return null
    val contact = contactRow(occurrence.contactId) ?: return null
    val recipient = recipientPolicyRow(occurrence.contactId) ?: return null
    val phone = recipient.chosenPhoneId?.let { phoneRow(it) } ?: return null
    val sync = syncRow(occurrence.accountId) ?: return null
    val reset = resetRow(occurrence.accountId) ?: return null
    val clock = clockRow(occurrence.accountId) ?: return null
    val coordination = coordinationRow(occurrence.accountId) ?: return null
    val installationId = coordination.activeInstallationId ?: return null
    val installation = getInstallation(installationId) ?: return null
    if (
      approval.state != ApprovalRecordState.ACTIVE ||
      approval.accountId != occurrence.accountId ||
      approval.contactId != occurrence.contactId ||
      approval.contentHash != occurrence.payloadHash ||
      approval.destinationFingerprint != occurrence.destinationFingerprint ||
      approval.contactMaterialRevision != contact.materialRevision ||
      approval.phoneId != phone.phoneId ||
      approval.phoneMaterialRevision != phone.materialRevision ||
      approval.policyId != policy.policyId ||
      approval.policyRevision != policy.revision ||
      policy.state != PolicyRecordState.ACTIVE ||
      contact.state != ContactSnapshotState.ACTIVE ||
      recipient.state != RecipientEnrollmentState.ENABLED ||
      recipient.approvalId != approval.approvalId ||
      phone.state != PhoneRecordState.READY ||
      phone.destinationFingerprint != occurrence.destinationFingerprint ||
      sync.freshness !in setOf(SyncFreshness.FRESH, SyncFreshness.STALE_WARNING) ||
      reset.status != ResetSafetyStatus.CLEAR ||
      reset.overflowBlocked ||
      reset.resetGeneration != coordination.resetGeneration ||
      trustedNowMillis < reset.birthdayAutomationNotBeforeMillis ||
      clock.status != ClockTrustStatus.TRUSTED ||
      (clock.greatestTrustedServerMillis ?: Long.MIN_VALUE) > trustedNowMillis ||
      coordination.mode != AccountMode.AUTOMATION_ACTIVE ||
      (coordination.ownerLeaseUntilMillis ?: Long.MIN_VALUE) <= trustedNowMillis ||
      installation.state != InstallationRecordState.ACTIVE ||
      installation.accountMode != AccountMode.AUTOMATION_ACTIVE ||
      installation.accountId != occurrence.accountId ||
      installation.installationId != coordination.activeInstallationId ||
      installation.senderEpoch != coordination.senderEpoch ||
      installation.resetGeneration != coordination.resetGeneration ||
      latestConsentRow(occurrence.accountId, ConsentKind.CONTACTS_READONLY)?.decision !=
        ConsentDecision.GRANTED ||
      latestConsentRow(occurrence.accountId, ConsentKind.SMS_STANDING_APPROVAL)?.decision !=
        ConsentDecision.GRANTED ||
      activeDestinationBlockCount(occurrence.accountId, occurrence.destinationFingerprint) != 0
    ) return null
    val guard = destinationGuardRow(occurrence.occurrenceId) ?: return null
    if (
      guard.accountId != occurrence.accountId ||
      guard.destinationFingerprint != occurrence.destinationFingerprint ||
      guard.armedOrLater != requirePreviouslyArmedGuard
    ) return null
    return control.blockerRevision
  }

  private suspend fun validateTestPreflight(
    permit: CoordinationPermitEntity,
    trustedNowMillis: Long,
  ): Boolean {
    val control = controlRow() ?: return false
    val test = getTestJob(permit.operationId) ?: return false
    val installation = getInstallation(permit.installationId) ?: return false
    val coordination = coordinationRow(permit.accountId) ?: return false
    val clock = clockRow(permit.accountId) ?: return false
    return control.accountMode in TEST_ACCOUNT_MODES &&
      control.blockerRevision >= 0 &&
      test.state == TestJobState.CLOUD_CLAIMED &&
      test.payloadHash == permit.payloadHash &&
      test.installationId == permit.installationId &&
      test.senderEpoch == permit.senderEpoch &&
      installation.state == InstallationRecordState.ACTIVE &&
      installation.accountMode.name in TEST_ACCOUNT_MODES &&
      installation.senderEpoch == permit.senderEpoch &&
      coordination.mode.name in TEST_ACCOUNT_MODES &&
      coordination.activeInstallationId == permit.installationId &&
      coordination.senderEpoch == permit.senderEpoch &&
      (coordination.ownerLeaseUntilMillis ?: Long.MIN_VALUE) > trustedNowMillis &&
      clock.status == ClockTrustStatus.TRUSTED
  }

  private suspend fun validateBirthdayArmedBoundary(
    permit: CoordinationPermitEntity,
    external: FinalExternalGateSnapshot,
    trustedNowMillis: Long,
  ): Boolean {
    val occurrence = getBirthdayOccurrence(permit.operationId) ?: return false
    if (
      occurrence.state != BirthdayJobState.ARM_RECONCILING ||
      occurrence.attemptNumber != permit.attemptNumber ||
      occurrence.claimedBlockerRevision != permit.armStartBlockerRevision ||
      !external.backgroundAllowed
    ) return false
    val blocker = validateBirthdayPreflight(
      occurrence,
      trustedNowMillis,
      requirePreviouslyArmedGuard = permit.attemptNumber == 2,
    ) ?: return false
    val approval = getApproval(occurrence.approvalId) ?: return false
    return blocker == permit.armStartBlockerRevision &&
      external.currentSubscriptionId == approval.resolvedSubscriptionId &&
      external.orderedPartsHash == approval.orderedPartsHash
  }

  private suspend fun validateTestArmedBoundary(
    permit: CoordinationPermitEntity,
    external: FinalExternalGateSnapshot,
    trustedNowMillis: Long,
  ): Boolean {
    val test = getTestJob(permit.operationId) ?: return false
    val control = controlRow() ?: return false
    return test.state == TestJobState.ARM_RECONCILING &&
      validateTestPreflightForArmed(test, permit, trustedNowMillis) &&
      control.blockerRevision == permit.armStartBlockerRevision &&
      external.foregroundConfirmationValid &&
      external.foregroundConfirmationNonceHash == test.foregroundConfirmationNonceHash &&
      external.currentSubscriptionId == test.resolvedSubscriptionId &&
      external.orderedPartsHash == test.orderedPartsHash
  }

  private suspend fun validateTestPreflightForArmed(
    test: TestJobEntity,
    permit: CoordinationPermitEntity,
    trustedNowMillis: Long,
  ): Boolean {
    val installation = getInstallation(permit.installationId) ?: return false
    val coordination = coordinationRow(permit.accountId) ?: return false
    return test.accountId == permit.accountId &&
      test.installationId == permit.installationId &&
      test.senderEpoch == permit.senderEpoch &&
      test.payloadHash == permit.payloadHash &&
      installation.state == InstallationRecordState.ACTIVE &&
      installation.accountMode.name in TEST_ACCOUNT_MODES &&
      coordination.mode.name in TEST_ACCOUNT_MODES &&
      coordination.activeInstallationId == permit.installationId &&
      coordination.senderEpoch == permit.senderEpoch &&
      (coordination.ownerLeaseUntilMillis ?: Long.MIN_VALUE) > trustedNowMillis
  }

  private suspend fun suppressAuthoritativeArm(
    permit: CoordinationPermitEntity,
    evidence: AuthoritativeArmedEvidence,
    effectiveDeadline: Long?,
    reason: String,
    recordedAtMillis: Long,
  ) {
    if (
      suppressArmedPermitRow(
        permit.permitId,
        evidence.armRequestId,
        evidence.serverNowMillis,
        evidence.serverSubmitNotAfterMillis,
        effectiveDeadline,
        reason,
        recordedAtMillis,
      ) != 1
    ) return
    when (permit.purpose) {
      OperationPurpose.BIRTHDAY -> {
        val occurrence = checkNotNull(getBirthdayOccurrence(permit.operationId)) {
          "suppressed-birthday-operation-missing"
        }
        check(markDestinationGuardArmed(occurrence.occurrenceId, recordedAtMillis) == 1) {
          "suppressed-destination-guard-missing"
        }
        check(
          casBirthdayState(
            occurrence.occurrenceId,
            BirthdayJobState.ARM_RECONCILING,
            occurrence.attemptNumber,
            occurrence.revision,
            BirthdayJobState.ARMED_SUPPRESSED,
            occurrence.attemptNumber,
            occurrence.claimedBlockerRevision,
            recordedAtMillis,
            reason,
          ) == 1,
        ) { "suppressed-birthday-state-cas-lost" }
      }
      OperationPurpose.TEST -> {
        val test = checkNotNull(getTestJob(permit.operationId)) {
          "suppressed-test-operation-missing"
        }
        check(
          casTestState(
            test.testJobId,
            TestJobState.ARM_RECONCILING,
            test.revision,
            TestJobState.ARMED_SUPPRESSED,
            recordedAtMillis,
            recordedAtMillis,
            reason,
          ) == 1,
        ) { "suppressed-test-state-cas-lost" }
      }
    }
  }

  private fun validDeliveryEvidence(event: DeliveryEventEntity): Boolean = when (
    event.evidenceClass
  ) {
    DeliveryEvidenceClass.SENT_SUCCESS ->
      event.androidResultCode == ANDROID_RESULT_OK && event.modemStatus == null
    DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF ->
      event.androidResultCode == SMS_RESULT_RADIO_OFF && event.modemStatus == null
    DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_NO_SERVICE ->
      event.androidResultCode == SMS_RESULT_NO_SERVICE && event.modemStatus == null
    DeliveryEvidenceClass.SENT_FAILURE ->
      event.androidResultCode != null &&
        event.androidResultCode !in setOf(
          ANDROID_RESULT_OK,
          SMS_RESULT_RADIO_OFF,
          SMS_RESULT_NO_SERVICE,
        ) &&
        event.modemStatus == null
    DeliveryEvidenceClass.DELIVERY_COMPLETE ->
      event.modemStatus?.let { it in 0x00..0x1f } == true
    DeliveryEvidenceClass.DELIVERY_PENDING ->
      event.modemStatus?.let { it in 0x20..0x3f } == true
    DeliveryEvidenceClass.DELIVERY_FAILED ->
      event.modemStatus?.let { it in 0x40..0x7f } == true
    DeliveryEvidenceClass.DELIVERY_UNKNOWN ->
      event.modemStatus?.let { it !in 0x00..0x7f } != false
  }

  companion object {
    private const val CLOCK_TOLERANCE_MILLIS = 5 * 60 * 1_000L
    private const val MAX_SERVER_PERMIT_MILLIS = 60_000L
    private const val MAX_EXTERNAL_GATE_AGE_MILLIS = 5_000L
    private const val MAX_LIVE_RESET_DATES = 8
    private const val SENT_WATCHDOG_MILLIS = 15L * 60L * 1_000L
    private const val MAX_CALLBACK_ACTION_LENGTH = 200
    private const val MAX_CALLBACK_DATA_URI_LENGTH = 512
    // Stable platform callback result values (Activity.RESULT_OK and SmsManager errors).
    private const val ANDROID_RESULT_OK = -1
    private const val SMS_RESULT_RADIO_OFF = 2
    private const val SMS_RESULT_NO_SERVICE = 4
    private const val CALLBACK_ACTION_PREFIX =
      "com.yashsomani.birthdayautopilot.callback."
    private const val CALLBACK_DATA_URI_PREFIX = "birthday-autopilot://callback/"
    private val TEST_ACCOUNT_MODES = setOf(
      AccountMode.TEST_ONLY.name,
      AccountMode.PAUSED_REPAIR.name,
    )
    private val NO_WRITE_REASONS = setOf(
      "EXPIRED",
      "EXPIRED_RETRY",
      "POLICY_BLOCKED",
      "MODE_BLOCKED",
      "OLD_EPOCH",
      "RESET_SUPPRESSED",
      "DELETION_SUPPRESSED",
      "BUDGET_BLOCKED",
      "GUARD_BLOCKED",
    )
    private val UUID_SHAPE = Regex(
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
    )
    private val SHA256_SHAPE = Regex("^[0-9a-f]{64}$")

    private fun subtractExactOrNull(left: Long, right: Long): Long? = try {
      Math.subtractExact(left, right)
    } catch (_: ArithmeticException) {
      null
    }

    private fun withinClockTolerance(left: Long, right: Long): Boolean {
      val difference = subtractExactOrNull(left, right) ?: return false
      if (difference == Long.MIN_VALUE) return false
      return abs(difference) <= CLOCK_TOLERANCE_MILLIS
    }
  }
}
