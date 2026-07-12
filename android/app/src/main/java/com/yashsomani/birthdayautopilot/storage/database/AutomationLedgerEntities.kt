package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.automation.state.TestJobState

@Entity(
  tableName = "birthday_occurrences_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = ContactSnapshotEntity::class,
      parentColumns = ["contactId"],
      childColumns = ["contactId"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = ApprovalSnapshotEntity::class,
      parentColumns = ["approvalId"],
      childColumns = ["approvalId"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = AutomationPolicyEntity::class,
      parentColumns = ["policyId"],
      childColumns = ["policyId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["accountId"]),
    Index(value = ["contactId"]),
    Index(value = ["approvalId"]),
    Index(value = ["policyId"]),
    Index(value = ["accountId", "idempotencyKey"], unique = true),
    Index(value = ["localDate"]),
    Index(value = ["state"]),
    Index(value = ["retentionUntilMillis"]),
  ],
)
data class BirthdayOccurrenceRecordEntity(
  @PrimaryKey val occurrenceId: String,
  val accountId: String,
  val contactId: String,
  val approvalId: String,
  val policyId: String,
  val localDate: String,
  val timeZoneId: String,
  val resolvedWindowStartMillis: Long,
  val resolvedWindowEndMillis: Long,
  val idempotencyKey: String,
  val destinationFingerprint: String,
  val channel: String,
  val payloadHash: String,
  val state: BirthdayJobState,
  val attemptNumber: Int,
  val revision: Long,
  val claimedBlockerRevision: Long?,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  val terminalAtMillis: Long?,
  val retentionUntilMillis: Long,
  val safeOutcomeCode: String?,
)

/**
 * A second local uniqueness fence independent of contact identity. It survives contact recreation
 * and is never released once [armedOrLater] becomes true.
 */
@Entity(
  tableName = "local_destination_guards_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = BirthdayOccurrenceRecordEntity::class,
      parentColumns = ["occurrenceId"],
      childColumns = ["occurrenceId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["occurrenceId"], unique = true),
    Index(
      value = ["accountId", "destinationFingerprint", "localDate", "channel"],
      unique = true,
    ),
    Index(value = ["retentionUntilMillis"]),
  ],
)
data class LocalDestinationGuardEntity(
  @PrimaryKey val guardId: String,
  val accountId: String,
  val occurrenceId: String,
  val destinationFingerprint: String,
  val localDate: String,
  val channel: String,
  val armedOrLater: Boolean,
  val createdAtMillis: Long,
  val armedAtMillis: Long?,
  val retentionUntilMillis: Long,
)

@Entity(
  tableName = "test_jobs_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = InstallationBindingEntity::class,
      parentColumns = ["installationId"],
      childColumns = ["installationId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["accountId"]),
    Index(value = ["installationId"]),
    Index(value = ["accountId", "testRequestId"], unique = true),
    Index(value = ["state"]),
    Index(value = ["retentionUntilMillis"]),
  ],
)
data class TestJobEntity(
  @PrimaryKey val testJobId: String,
  val accountId: String,
  val installationId: String,
  val senderEpoch: Long,
  val testRequestId: String,
  val configHash: String,
  val destinationPrehash: String,
  val normalizedDestination: String,
  val maskedDestination: String,
  val exactMessage: String,
  val payloadHash: String,
  val simPolicyKind: String,
  val resolvedSubscriptionId: Int,
  val segmentCount: Int,
  val messageEncoding: String,
  val orderedPartsHash: String,
  val buildBindingHash: String,
  val appCheckPolicyVersion: String,
  val state: TestJobState,
  val revision: Long,
  val foregroundConfirmationNonceHash: String,
  val foregroundConfirmedAtMillis: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  val terminalAtMillis: Long?,
  val invalidationReason: String?,
  val retentionUntilMillis: Long,
)

@Entity(
  tableName = "coordination_permits_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = InstallationBindingEntity::class,
      parentColumns = ["installationId"],
      childColumns = ["installationId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["accountId"]),
    Index(value = ["installationId"]),
    Index(value = ["purpose", "operationId", "attemptNumber"], unique = true),
    Index(value = ["claimRequestId"], unique = true),
    Index(value = ["armRequestId"], unique = true),
    Index(value = ["state"]),
    Index(value = ["retentionUntilMillis"]),
  ],
)
data class CoordinationPermitEntity(
  @PrimaryKey val permitId: String,
  val accountId: String,
  val installationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
  val purpose: OperationPurpose,
  val operationId: String,
  val attemptNumber: Int,
  val payloadHash: String,
  val opaqueClaimId: String,
  val opaqueDestinationGuardId: String?,
  val claimRequestId: String,
  val armRequestId: String?,
  val state: CoordinationPermitState,
  val armDispatched: Boolean,
  val armStartBlockerRevision: Long?,
  val claimExpiresAtMillis: Long,
  val maxPossibleSubmitNotAfterMillis: Long,
  val unresolvedArmCutoffMillis: Long,
  val trustedServerNowMillis: Long,
  val requestStartElapsedMillis: Long,
  val bootCount: Int,
  val serverSubmitNotAfterMillis: Long?,
  val effectiveSubmitNotAfterMillis: Long?,
  val noWriteReason: String?,
  val revision: Long,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  val barrierConsumedAtMillis: Long?,
  val retentionUntilMillis: Long,
)

@Entity(
  tableName = "send_attempts_v2",
  foreignKeys = [
    ForeignKey(
      entity = CoordinationPermitEntity::class,
      parentColumns = ["permitId"],
      childColumns = ["permitId"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = InstallationBindingEntity::class,
      parentColumns = ["installationId"],
      childColumns = ["installationId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["permitId"], unique = true),
    Index(value = ["installationId"]),
    Index(value = ["purpose", "operationId", "attemptNumber"], unique = true),
    Index(value = ["state"]),
    Index(value = ["retentionUntilMillis"]),
  ],
)
data class SendAttemptEntity(
  @PrimaryKey val sendAttemptId: String,
  val permitId: String,
  val installationId: String,
  val callbackGeneration: String,
  val purpose: OperationPurpose,
  val operationId: String,
  val attemptNumber: Int,
  val payloadHash: String,
  val resolvedSubscriptionId: Int,
  val expectedPartCount: Int,
  val state: SendAttemptState,
  val apiBoundaryStartedAtMillis: Long?,
  val submittedAtMillis: Long?,
  val sentWatchdogAtMillis: Long,
  val deliveryWatchdogAtMillis: Long?,
  val terminalAtMillis: Long?,
  val safeOutcomeCode: String?,
  val revision: Long,
  val retentionUntilMillis: Long,
)

@Entity(
  tableName = "callback_tokens_v2",
  foreignKeys = [
    ForeignKey(
      entity = SendAttemptEntity::class,
      parentColumns = ["sendAttemptId"],
      childColumns = ["sendAttemptId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["sendAttemptId"]),
    Index(value = ["callbackRequestCode"], unique = true),
    Index(value = ["installationId", "callbackGeneration", "action", "dataUri"], unique = true),
    Index(value = ["state"]),
    Index(value = ["expiresAtMillis"]),
  ],
)
data class CallbackTokenEntity(
  @PrimaryKey val callbackTokenId: String,
  val sendAttemptId: String,
  val installationId: String,
  val callbackGeneration: String,
  val attemptNumber: Int,
  val partIndex: Int,
  val kind: CallbackKind,
  val callbackRequestCode: Int,
  val action: String,
  val dataUri: String,
  val mutableForPlatformFillIn: Boolean,
  val state: CallbackTokenState,
  val createdAtMillis: Long,
  val observedAtMillis: Long?,
  val retiredAtMillis: Long?,
  val expiresAtMillis: Long,
)

@Entity(
  tableName = "delivery_events_v2",
  foreignKeys = [
    ForeignKey(
      entity = CallbackTokenEntity::class,
      parentColumns = ["callbackTokenId"],
      childColumns = ["callbackTokenId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["callbackTokenId"]),
    Index(value = ["callbackTokenId", "evidenceKey"], unique = true),
    Index(value = ["receivedAtMillis"]),
  ],
)
data class DeliveryEventEntity(
  @PrimaryKey val eventId: String,
  val callbackTokenId: String,
  val evidenceKey: String,
  val evidenceClass: DeliveryEvidenceClass,
  val androidResultCode: Int?,
  val modemStatus: Int?,
  val receivedAtMillis: Long,
)

@Entity(
  tableName = "outcome_projections_v2",
  primaryKeys = ["purpose", "operationId"],
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["accountId"]),
    Index(value = ["refinedAtMillis"]),
  ],
)
data class OutcomeProjectionEntity(
  val purpose: OperationPurpose,
  val operationId: String,
  val accountId: String,
  val immutableSafetyState: String,
  val visibleOutcome: String,
  val evidenceCompleteness: String,
  val sentEvidenceDeadlineMillis: Long?,
  val deliveryEvidenceDeadlineMillis: Long?,
  val refinedAtMillis: Long,
  val revision: Long,
)

@Entity(
  tableName = "test_receipts_v2",
  foreignKeys = [
    ForeignKey(
      entity = TestJobEntity::class,
      parentColumns = ["testJobId"],
      childColumns = ["testJobId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["testJobId"], unique = true),
    Index(value = ["accountId"]),
    Index(value = ["accountId", "bindingHash"], unique = true),
    Index(value = ["state"]),
  ],
)
data class TestReceiptEntity(
  @PrimaryKey val testReceiptId: String,
  val testJobId: String,
  val accountId: String,
  val bindingHash: String,
  val configHash: String,
  val destinationBindingHash: String,
  val maskedDestination: String,
  val exactTextHash: String,
  val segmentPlanHash: String,
  val resolvedSubscriptionId: Int,
  val installationId: String,
  val senderEpoch: Long,
  val buildBindingHash: String,
  val distributionChannel: String,
  val appCheckPolicyVersion: String,
  val smsPolicyVersion: String,
  val state: TestReceiptState,
  val passedAtMillis: Long,
  val invalidatedAtMillis: Long?,
  val invalidationReason: String?,
)

@Entity(
  tableName = "reset_safety_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["accountId"], unique = true)],
)
data class ResetSafetyEntity(
  @PrimaryKey val resetSafetyId: String,
  val accountId: String,
  val resetGeneration: Long,
  val resetAtMillis: Long,
  val resetLocalDate: String,
  val resetTimeZoneId: String,
  val birthdayAutomationNotBeforeMillis: Long,
  val status: ResetSafetyStatus,
  val overflowBlocked: Boolean,
  val revision: Long,
  val updatedAtMillis: Long,
)

@Entity(
  tableName = "reset_blocked_dates_v2",
  foreignKeys = [
    ForeignKey(
      entity = ResetSafetyEntity::class,
      parentColumns = ["resetSafetyId"],
      childColumns = ["resetSafetyId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["resetSafetyId"]),
    Index(value = ["resetSafetyId", "civilDate"], unique = true),
    Index(value = ["releaseAfterTrustedServerMillis"]),
  ],
)
data class ResetBlockedDateEntity(
  @PrimaryKey val blockedDateId: String,
  val resetSafetyId: String,
  val civilDate: String,
  val releaseAfterTrustedServerMillis: Long,
  val observedAtMillis: Long,
)

@Entity(
  tableName = "clock_trust_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class ClockTrustEntity(
  @PrimaryKey val accountId: String,
  val status: ClockTrustStatus,
  val greatestTrustedServerMillis: Long?,
  val lastDeviceWallMillis: Long?,
  val lastElapsedRealtimeMillis: Long?,
  val trustedBootCount: Int?,
  val lastVerificationMillis: Long?,
  val observedDriftMillis: Long?,
  val revision: Long,
)

/** Diagnostic projection only; permit issuance always re-reads authoritative records and OS state. */
@Entity(
  tableName = "readiness_state_v2",
  foreignKeys = [
    ForeignKey(
      entity = AccountRecordEntity::class,
      parentColumns = ["accountId"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class ReadinessStateEntity(
  @PrimaryKey val accountId: String,
  val distribution: String,
  val identity: String,
  val contactsSync: String,
  val standingConsent: String,
  val approvals: String,
  val smsPermission: String,
  val sim: String,
  val scheduler: String,
  val backgroundRestriction: String,
  val doze: String,
  val unusedAppRestriction: String,
  val dataSaver: String,
  val lowPowerStandby: String,
  val coordination: String,
  val network: String,
  val activeSender: String,
  val clockTrust: String,
  val resetSafety: String,
  val overall: String,
  val evaluatedAtMillis: Long,
  val revision: Long,
)
