package com.yashsomani.birthdayautopilot.coordination

enum class DistributionChannel {
  DEV,
  STAGING,
  RESTRICTED_LAB,
  PLAY,
  DIRECT_MANAGED,
}

enum class CoordinationPurpose { BIRTHDAY, TEST }

enum class AccountModeAction { PAUSE_FOR_REPAIR, ACTIVATE_AUTOMATION }

enum class RetryProof { ALL_PARTS_RADIO_OFF, ALL_PARTS_NO_SERVICE, OTHER }

enum class TestReportResult {
  SENT_ALL_PARTS,
  FAILED_ZERO_ACCEPTED,
  FAILED_OR_UNKNOWN,
  CLEANUP_CANCELLED,
}

enum class ServerAccountMode {
  TEST_ONLY,
  PAUSED_REPAIR,
  AUTOMATION_ACTIVE,
  TRANSFER_PENDING,
  DELETING,
}

enum class ServerInstallationState { ACTIVE, STANDBY, REVOKED }

enum class ServerClaimState {
  CLAIMED,
  EXPIRED_NO_ARM,
  ARMED,
  RETRYABLE_ZERO,
  RETRY_CLAIMED,
  RETRY_EXPIRED_NO_ARM,
  TERMINAL,
}

enum class TestBarrierOutcome {
  SENT_ALL_PARTS_IN_WINDOW,
  SENT_EVIDENCE_LATE,
  FAILED_ZERO_ACCEPTED,
  FAILED_OR_UNKNOWN,
  CLEANUP_CANCELLED,
}

/** Every value is an explicit backend contract value. Unknown values fail response parsing. */
enum class CoordinationServerReason {
  EXPIRED,
  EXPIRED_RETRY,
  GLOBAL_ARMING_DISABLED,
  CONTINUITY_UNAVAILABLE,
  LEDGER_GENERATION_MISMATCH,
  BUILD_UNSUPPORTED,
  POLICY_UNSUPPORTED,
  CHANNEL_UNSUPPORTED,
  MODE_BLOCKED,
  LEASE_EXPIRED,
  INSTALLATION_MISMATCH,
  EPOCH_MISMATCH,
  RESET_GENERATION_MISMATCH,
  TOO_EARLY,
  BIRTHDAY_RESET_FENCE,
  BUDGET_EXCEEDED,
  CLAIM_STATE_MISMATCH,
  DELETION_SUPPRESSED,
  RESET_SUPPRESSED,
  MISSING_FENCE,
  MISSING_CLAIM,
  UNKNOWN_HISTORY,
  BINDING_MISMATCH,
  TEST_LEASE_OR_MODE_INVALID,
  BOUND_TEST_RECEIPT_REQUIRED,
  OCCURRENCE_RESERVED,
  DESTINATION_RESERVED,
  TEST_MATERIAL_MISMATCH,
  REQUEST_RECORD_CORRUPT,
  NOT_ARMED_ATTEMPT_ONE,
  UNSUPPORTED_ZERO_ACCEPTANCE_PROOF,
  RETRY_REQUEST_MISMATCH,
  ARMED_OUTCOME_REQUIRED,
  TARGET_NOT_STANDBY,
  WRONG_MODE,
  DRAIN_NOT_COMPLETE,
  COORDINATION_OPERATION_IN_PROGRESS,
  REQUEST_MISMATCH,
  GENERATION_EXHAUSTED,
}

enum class RequestInvalidReason {
  LEDGER_GENERATION,
  INSTALLATION_ID,
  APP_BUILD_NUMBER,
  POLICY_VERSION,
  SENDER_EPOCH,
  RESET_GENERATION,
  REQUEST_ID,
  CLAIM_ID,
  PREHASH,
  PREHASH_ALIASES,
  ATTEMPT,
  READINESS_CONTRACT_VERSION,
}

enum class CoordinationUnavailableReason {
  NOT_AUTHENTICATED,
  ACCOUNT_MISMATCH,
  APP_CHECK_UNAVAILABLE,
  NATIVE_PREFLIGHT_UNAVAILABLE,
  AMBIGUOUS_CALL,
  INVALID_SERVER_RESPONSE,
}

sealed interface RequestBuildResult<out T> {
  data class Ready<T>(val request: T) : RequestBuildResult<T> {
    override fun toString(): String = "RequestBuildResult.Ready(<redacted>)"
  }

  data class Invalid(val reason: RequestInvalidReason) : RequestBuildResult<Nothing>
}

sealed interface CoordinationCallResult<out T> {
  data class Authoritative<T>(val outcome: T) : CoordinationCallResult<T> {
    override fun toString(): String = "CoordinationCallResult.Authoritative(<sanitized>)"
  }

  data class Unavailable(
    val reason: CoordinationUnavailableReason,
  ) : CoordinationCallResult<Nothing>
}

data class ServerBinding(
  val activeInstallationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
  val mode: ServerAccountMode,
  val ownerLeaseUntilMillis: Long,
  val nextArmNotBeforeMillis: Long,
  val latestIssuedSubmitNotAfterMillis: Long,
  val birthdayAutomationNotBeforeMillis: Long,
  val serverObservedAtMillis: Long,
  val transferTargetInstallationId: String? = null,
  val transferDrainUntilMillis: Long? = null,
  val deletionDrainUntilMillis: Long? = null,
) {
  override fun toString(): String = "ServerBinding(<redacted>)"
}

data class ServerClaim(
  val claimId: String,
  val purpose: CoordinationPurpose,
  val ownerInstallationId: String,
  val ownerEpoch: Long,
  val resetGeneration: Long,
  val state: ServerClaimState,
  val attempt: Int,
  val claimExpiresAtMillis: Long,
  val maxPossibleSubmitNotAfterMillis: Long,
  val serverSubmitNotAfterMillis: Long?,
  val testBarrierOutcome: TestBarrierOutcome?,
  val serverObservedAtMillis: Long,
  val retryRequestId: String? = null,
  val retryProof: RetryProof? = null,
) {
  override fun toString(): String = "ServerClaim(<redacted>)"
}

sealed interface RegistrationOutcome {
  enum class Disposition { REGISTERED_ACTIVE, REGISTERED_STANDBY, REPLAYED }

  data class Registered(
    val disposition: Disposition,
    val binding: ServerBinding,
    val installationState: ServerInstallationState,
    val installationEpoch: Long,
  ) : RegistrationOutcome {
    override fun toString(): String = "RegistrationOutcome.Registered(<redacted>)"
  }

  data class Suppressed(val reason: CoordinationServerReason) : RegistrationOutcome
}

sealed interface LeaseOutcome {
  data class Renewed(val leaseUntilMillis: Long) : LeaseOutcome

  data class Refused(val reason: CoordinationServerReason) : LeaseOutcome
}

sealed interface AccountModeOutcome {
  data class Changed(val mode: ServerAccountMode) : AccountModeOutcome

  data class Refused(val reason: CoordinationServerReason) : AccountModeOutcome
}

sealed interface SenderTransferOutcome {
  data class Started(val binding: ServerBinding) : SenderTransferOutcome

  data class Completed(
    val binding: ServerBinding,
    val oldInstallationId: String,
    val targetInstallationId: String,
  ) : SenderTransferOutcome

  data class Refused(val reason: CoordinationServerReason) : SenderTransferOutcome
}

enum class DeletionStage {
  DRAINING,
  PURGING,
  AUTH_DELETION_PENDING,
  VERIFYING,
}

data class AccountDeletionAcceptance(
  val disposition: Disposition,
  val requestId: String,
  val stage: DeletionStage,
  val drainUntilMillis: Long,
  val serverObservedAtMillis: Long,
  val deletingFence: AccountDeletionFence?,
) {
  enum class Disposition { STARTED, REPLAYED }

  override fun toString(): String = "AccountDeletionAcceptance(<sanitized>)"
}

data class AccountDeletionFence(
  val senderEpoch: Long,
  val resetGeneration: Long,
  val deletionDrainUntilMillis: Long,
)

/** Content-free receipt evidence available after Firebase Auth has been deleted. */
sealed interface AccountDeletionReceiptOutcome {
  data class InProgress(
    val requestedAtMillis: Long,
    val updatedAtMillis: Long,
  ) : AccountDeletionReceiptOutcome

  data class Completed(
    val requestedAtMillis: Long,
    val completedAtMillis: Long,
  ) : AccountDeletionReceiptOutcome

  data object NotFound : AccountDeletionReceiptOutcome
}

enum class CoordinationOperationKind {
  CONTACT_DERIVED_RESET,
  SENDER_RELEASE,
}

enum class CoordinationOperationStage {
  RESET_DRAINING,
  RESET_PURGING,
  RELEASE_DRAINING,
  RELEASE_PURGING,
}

/**
 * Content-free progress evidence for a destructive coordination operation. Field combinations are
 * validated against [operation] and [stage] by the response parser before this value is created.
 */
data class CoordinationOperationProgress(
  val operation: CoordinationOperationKind,
  val stage: CoordinationOperationStage,
  val androidStateExisted: Boolean,
  val senderEpochAfter: Long?,
  val resetGenerationAfter: Long?,
  val birthdayAutomationNotBeforeMillis: Long?,
  val drainUntilMillis: Long?,
) {
  override fun toString(): String = "CoordinationOperationProgress(<sanitized>)"
}

/** Terminal backend evidence. The parser verifies the matching erased/preserved literal fields. */
sealed interface CoordinationCompletion {
  val operation: CoordinationOperationKind
  val androidStateExisted: Boolean
  val completedAtMillis: Long

  data class ContactDerivedReset(
    override val androidStateExisted: Boolean,
    val senderEpochAfter: Long?,
    val resetGenerationAfter: Long?,
    val birthdayAutomationNotBeforeMillis: Long?,
    override val completedAtMillis: Long,
  ) : CoordinationCompletion {
    override val operation: CoordinationOperationKind =
      CoordinationOperationKind.CONTACT_DERIVED_RESET

    override fun toString(): String = "CoordinationCompletion.ContactDerivedReset(<sanitized>)"
  }

  data class SenderRelease(
    val senderEpochAfter: Long,
    val resetGenerationAfter: Long,
    override val completedAtMillis: Long,
  ) : CoordinationCompletion {
    override val operation: CoordinationOperationKind = CoordinationOperationKind.SENDER_RELEASE
    override val androidStateExisted: Boolean = true

    override fun toString(): String = "CoordinationCompletion.SenderRelease(<sanitized>)"
  }
}

sealed interface CoordinationOperationOutcome {
  data class InProgress(
    val progress: CoordinationOperationProgress,
  ) : CoordinationOperationOutcome

  data class Completed(
    val completion: CoordinationCompletion,
  ) : CoordinationOperationOutcome

  data class Refused(
    val reason: CoordinationServerReason,
  ) : CoordinationOperationOutcome
}

data class CoordinationAndroidState(
  val mode: ServerAccountMode,
  val activeInstallationId: String,
  val senderEpoch: Long,
  val resetGeneration: Long,
  val ownerLeaseUntilMillis: Long,
  val latestIssuedSubmitNotAfterMillis: Long,
  val birthdayAutomationNotBeforeMillis: Long,
  val transferTargetInstallationId: String?,
  val transferDrainUntilMillis: Long?,
) {
  override fun toString(): String = "CoordinationAndroidState(<redacted>)"
}

sealed interface CoordinationLifecycleStatusOutcome {
  val serverNowMillis: Long

  data class OperationInProgress(
    override val serverNowMillis: Long,
    val progress: CoordinationOperationProgress,
  ) : CoordinationLifecycleStatusOutcome

  data class AccountDeletionInProgress(
    override val serverNowMillis: Long,
    val stage: DeletionStage,
    val drainUntilMillis: Long,
  ) : CoordinationLifecycleStatusOutcome

  data class AndroidState(
    override val serverNowMillis: Long,
    val state: CoordinationAndroidState,
    val latestCompletion: CoordinationCompletion?,
  ) : CoordinationLifecycleStatusOutcome

  data class NoAndroidState(
    override val serverNowMillis: Long,
    val latestCompletion: CoordinationCompletion?,
  ) : CoordinationLifecycleStatusOutcome

  data class SafetyStatusUnavailable(
    override val serverNowMillis: Long,
  ) : CoordinationLifecycleStatusOutcome
}

sealed interface ClaimOutcome {
  enum class Disposition { CLAIMED, REPLAYED }

  data class Accepted(
    val disposition: Disposition,
    val claim: ServerClaim,
  ) : ClaimOutcome {
    override fun toString(): String = "ClaimOutcome.Accepted(<redacted>)"
  }

  data class Refused(val reason: CoordinationServerReason) : ClaimOutcome
}

sealed interface AuthoritativeArmOutcome {
  val armRequestId: String
  val purpose: CoordinationPurpose
  val claimId: String
  val ownerInstallationId: String
  val ownerEpoch: Long
  val resetGeneration: Long
  val attempt: Int
  val resolvedAtMillis: Long

  data class Armed(
    override val armRequestId: String,
    override val purpose: CoordinationPurpose,
    override val claimId: String,
    override val ownerInstallationId: String,
    override val ownerEpoch: Long,
    override val resetGeneration: Long,
    override val attempt: Int,
    val serverSubmitNotAfterMillis: Long,
    override val resolvedAtMillis: Long,
  ) : AuthoritativeArmOutcome {
    override fun toString(): String = "AuthoritativeArmOutcome.Armed(<redacted>)"
  }

  data class NoWrite(
    override val armRequestId: String,
    override val purpose: CoordinationPurpose,
    override val claimId: String,
    override val ownerInstallationId: String,
    override val ownerEpoch: Long,
    override val resetGeneration: Long,
    override val attempt: Int,
    val reason: CoordinationServerReason,
    override val resolvedAtMillis: Long,
  ) : AuthoritativeArmOutcome {
    override fun toString(): String = "AuthoritativeArmOutcome.NoWrite(reason=$reason)"
  }
}

sealed interface ArmDecisionOutcome {
  data class Armed(val outcome: AuthoritativeArmOutcome.Armed) : ArmDecisionOutcome

  data class NoWrite(val outcome: AuthoritativeArmOutcome.NoWrite) : ArmDecisionOutcome

  data class Replayed(val outcome: AuthoritativeArmOutcome) : ArmDecisionOutcome

  data class Suppressed(val reason: CoordinationServerReason) : ArmDecisionOutcome
}

sealed interface ArmStatusOutcome {
  data class Replayed(val outcome: AuthoritativeArmOutcome) : ArmStatusOutcome

  data class NoWrite(val outcome: AuthoritativeArmOutcome.NoWrite) : ArmStatusOutcome

  data class Suppressed(val reason: CoordinationServerReason) : ArmStatusOutcome

  data object Unknown : ArmStatusOutcome
}

sealed interface RetryOutcome {
  data class Authorized(val claim: ServerClaim) : RetryOutcome {
    override fun toString(): String = "RetryOutcome.Authorized(<redacted>)"
  }

  data class Refused(val reason: CoordinationServerReason) : RetryOutcome
}

sealed interface TestReportOutcome {
  data class Recorded(val outcome: TestBarrierOutcome) : TestReportOutcome

  data class Replayed(val outcome: TestBarrierOutcome) : TestReportOutcome

  data class Suppressed(val reason: CoordinationServerReason) : TestReportOutcome

  data class Refused(val reason: CoordinationServerReason) : TestReportOutcome
}

enum class CompanionSafetyState {
  NO_ANDROID_STATE,
  MANAGED_BY_ANDROID,
  DELETING,
  SAFETY_STATUS_UNAVAILABLE,
}

data class CompanionStatusOutcome(
  val composerAllowed: Boolean,
  val state: CompanionSafetyState,
  val serverNowMillis: Long,
  val ledgerGeneration: String?,
)
