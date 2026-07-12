package com.yashsomani.birthdayautopilot.storage.database

import androidx.room.TypeConverter
import com.yashsomani.birthdayautopilot.automation.state.BirthdayJobState
import com.yashsomani.birthdayautopilot.automation.state.TestJobState
import com.yashsomani.birthdayautopilot.core.model.AccountMode

enum class AccountRecordState {
  ACTIVE,
  RETAINED_SIGNED_OUT,
  DELETING,
  MIGRATION_REVIEW_REQUIRED,
}

enum class InstallationRecordState { ACTIVE, STANDBY, REVOKED }

enum class ConsentKind {
  CONTACTS_DISCLOSURE,
  CONTACTS_READONLY,
  SMS_STANDING_APPROVAL,
  CARRIER_COST,
  TERMS,
}

enum class ConsentDecision { GRANTED, DECLINED, REVOKED }

enum class SyncFreshness {
  NEVER_SYNCED,
  FRESH,
  STALE_WARNING,
  SAFETY_PAUSED,
  AUTH_ACTION_REQUIRED,
}

enum class ContactSnapshotState { ACTIVE, DELETED, MIGRATED_REVIEW_REQUIRED }

enum class PhoneRecordState {
  READY,
  NEEDS_REGION,
  INVALID,
  UNSAFE_DESTINATION,
  NON_SMS,
  DELETED,
}

enum class RecipientEnrollmentState { OFF, ENABLED, PAUSED, EXCLUDED, BLOCKED, NEEDS_REVIEW }

enum class TemplateSource { BUILT_IN, USER, GEMINI }

enum class TemplateValidationState { VALID, INVALID, SUPERSEDED }

enum class ApprovalRecordState { ACTIVE, INVALIDATED, REVOKED, MIGRATED_REVIEW_REQUIRED }

enum class PolicyRecordState { ACTIVE, SUPERSEDED, INVALIDATED }

enum class OperationPurpose { BIRTHDAY, TEST }

enum class CoordinationPermitState {
  LOCAL_CLAIMED,
  CLOUD_CLAIMED,
  ARM_RECONCILING,
  CLOUD_ARMED,
  BARRIER_CONSUMED,
  NO_WRITE,
  COORDINATION_UNKNOWN,
  ARMED_SUPPRESSED,
  CANCELLED,
}

enum class SendAttemptState {
  BARRIER_CONSUMED,
  API_CALL_STARTED,
  SUBMITTED,
  SENT_FROM_DEVICE,
  RETRYABLE_ZERO,
  PERMANENT_FAILURE,
  PARTIAL_UNKNOWN,
  UNKNOWN,
  TERMINAL,
}

enum class CallbackKind { SENT, DELIVERY }

enum class CallbackTokenState { EXPECTED, OBSERVED, RETIRED, EXPIRED }

enum class DeliveryEvidenceClass {
  SENT_SUCCESS,
  SENT_ZERO_ACCEPTANCE_RADIO_OFF,
  SENT_ZERO_ACCEPTANCE_NO_SERVICE,
  SENT_FAILURE,
  DELIVERY_PENDING,
  DELIVERY_COMPLETE,
  DELIVERY_FAILED,
  DELIVERY_UNKNOWN,
}

enum class TestReceiptState { VALID, INVALIDATED }

enum class ResetSafetyStatus { CLEAR, BLOCKED, OVERFLOW_BLOCKED, REPAIR_REQUIRED }

enum class ClockTrustStatus { TRUSTED, UNVERIFIED, DRIFTED, BOOT_ANCHOR_LOST, REPAIR_REQUIRED }

/**
 * Room persists enums as their exact names. Unknown database values deliberately throw during
 * decoding instead of being coerced to a permissive state.
 */
class SafetyLedgerConverters {
  private inline fun <reified T : Enum<T>> decode(value: String): T = enumValueOf(value)

  @TypeConverter fun accountRecordState(value: AccountRecordState): String = value.name
  @TypeConverter fun accountRecordState(value: String): AccountRecordState = decode(value)
  @TypeConverter fun installationRecordState(value: InstallationRecordState): String = value.name
  @TypeConverter fun installationRecordState(value: String): InstallationRecordState = decode(value)
  @TypeConverter fun accountMode(value: AccountMode): String = value.name
  @TypeConverter fun accountMode(value: String): AccountMode = decode(value)
  @TypeConverter fun consentKind(value: ConsentKind): String = value.name
  @TypeConverter fun consentKind(value: String): ConsentKind = decode(value)
  @TypeConverter fun consentDecision(value: ConsentDecision): String = value.name
  @TypeConverter fun consentDecision(value: String): ConsentDecision = decode(value)
  @TypeConverter fun syncFreshness(value: SyncFreshness): String = value.name
  @TypeConverter fun syncFreshness(value: String): SyncFreshness = decode(value)
  @TypeConverter fun contactSnapshotState(value: ContactSnapshotState): String = value.name
  @TypeConverter fun contactSnapshotState(value: String): ContactSnapshotState = decode(value)
  @TypeConverter fun phoneRecordState(value: PhoneRecordState): String = value.name
  @TypeConverter fun phoneRecordState(value: String): PhoneRecordState = decode(value)
  @TypeConverter fun recipientEnrollmentState(value: RecipientEnrollmentState): String = value.name
  @TypeConverter fun recipientEnrollmentState(value: String): RecipientEnrollmentState = decode(value)
  @TypeConverter fun templateSource(value: TemplateSource): String = value.name
  @TypeConverter fun templateSource(value: String): TemplateSource = decode(value)
  @TypeConverter fun templateValidationState(value: TemplateValidationState): String = value.name
  @TypeConverter fun templateValidationState(value: String): TemplateValidationState = decode(value)
  @TypeConverter fun approvalRecordState(value: ApprovalRecordState): String = value.name
  @TypeConverter fun approvalRecordState(value: String): ApprovalRecordState = decode(value)
  @TypeConverter fun policyRecordState(value: PolicyRecordState): String = value.name
  @TypeConverter fun policyRecordState(value: String): PolicyRecordState = decode(value)
  @TypeConverter fun birthdayJobState(value: BirthdayJobState): String = value.name
  @TypeConverter fun birthdayJobState(value: String): BirthdayJobState = decode(value)
  @TypeConverter fun testJobState(value: TestJobState): String = value.name
  @TypeConverter fun testJobState(value: String): TestJobState = decode(value)
  @TypeConverter fun operationPurpose(value: OperationPurpose): String = value.name
  @TypeConverter fun operationPurpose(value: String): OperationPurpose = decode(value)
  @TypeConverter fun coordinationPermitState(value: CoordinationPermitState): String = value.name
  @TypeConverter fun coordinationPermitState(value: String): CoordinationPermitState = decode(value)
  @TypeConverter fun sendAttemptState(value: SendAttemptState): String = value.name
  @TypeConverter fun sendAttemptState(value: String): SendAttemptState = decode(value)
  @TypeConverter fun callbackKind(value: CallbackKind): String = value.name
  @TypeConverter fun callbackKind(value: String): CallbackKind = decode(value)
  @TypeConverter fun callbackTokenState(value: CallbackTokenState): String = value.name
  @TypeConverter fun callbackTokenState(value: String): CallbackTokenState = decode(value)
  @TypeConverter fun deliveryEvidenceClass(value: DeliveryEvidenceClass): String = value.name
  @TypeConverter fun deliveryEvidenceClass(value: String): DeliveryEvidenceClass = decode(value)
  @TypeConverter fun testReceiptState(value: TestReceiptState): String = value.name
  @TypeConverter fun testReceiptState(value: String): TestReceiptState = decode(value)
  @TypeConverter fun resetSafetyStatus(value: ResetSafetyStatus): String = value.name
  @TypeConverter fun resetSafetyStatus(value: String): ResetSafetyStatus = decode(value)
  @TypeConverter fun clockTrustStatus(value: ClockTrustStatus): String = value.name
  @TypeConverter fun clockTrustStatus(value: String): ClockTrustStatus = decode(value)
}
