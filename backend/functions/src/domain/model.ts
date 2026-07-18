export const SCHEMA_VERSION = 1 as const;

export const SECOND_MS = 1_000;
export const MINUTE_MS = 60 * SECOND_MS;
export const HOUR_MS = 60 * MINUTE_MS;
export const DAY_MS = 24 * HOUR_MS;

export const MAX_LEASE_MS = 10 * MINUTE_MS;
export const CLAIM_AUTHORIZATION_MS = 10 * MINUTE_MS;
export const MAX_SUBMIT_AFTER_ARM_MS = MINUTE_MS;
export const ARM_SPACING_MS = 5 * MINUTE_MS;
export const BIRTHDAY_RETENTION_MS = 400 * DAY_MS;
export const TEST_RETENTION_MS = 30 * DAY_MS;
export const NEVER_ARMED_CLEANUP_MS = DAY_MS;
export const BUDGET_WINDOW_MS = DAY_MS;
export const STANDBY_RETENTION_MS = 90 * DAY_MS;
export const REVOKED_RETENTION_MS = 30 * DAY_MS;
export const COORDINATION_RECEIPT_RETENTION_MS = 30 * DAY_MS;
export const DELETION_RECEIPT_RETENTION_MS = 365 * DAY_MS;
// An iOS MessageUI sheet can remain open while the user edits. Seventy-two
// hours is deliberately longer than the worst possible remaining lifetime of
// the device's current civil date (UTC+14 through the end of that date in
// UTC-12, plus guard time). The native client dismisses before this deadline;
// logical expiry, rather than Firestore TTL cleanup, authorizes Android again.
export const IOS_COMPOSER_RESERVATION_MS = 72 * HOUR_MS;
export const BIRTHDAY_ARM_CAP = 20;
export const TEST_ARM_CAP = 3;

export type Purpose = 'BIRTHDAY' | 'TEST';
export type AccountMode =
  | 'TEST_ONLY'
  | 'PAUSED_REPAIR'
  | 'AUTOMATION_ACTIVE'
  | 'TRANSFER_PENDING'
  | 'DELETING';
export type InstallationState = 'ACTIVE' | 'STANDBY' | 'REVOKED';
export type ContinuityState = 'HEALTHY' | 'FROZEN' | 'RECOVERY_REQUIRED';
export type ClaimState =
  | 'CLAIMED'
  | 'EXPIRED_NO_ARM'
  | 'ARMED'
  | 'RETRYABLE_ZERO'
  | 'RETRY_CLAIMED'
  | 'RETRY_EXPIRED_NO_ARM'
  | 'TERMINAL';
export type TestBarrierOutcome =
  | 'SENT_ALL_PARTS_IN_WINDOW'
  | 'SENT_EVIDENCE_LATE'
  | 'FAILED_ZERO_ACCEPTED'
  | 'FAILED_OR_UNKNOWN'
  | 'CLEANUP_CANCELLED';
export type GuardState = 'RESERVED' | 'EXPIRED_NO_ARM_RECLAIMABLE' | 'ARMED';
export type DeletionStage =
  | 'DRAINING'
  | 'PURGING'
  | 'AUTH_DELETION_PENDING'
  | 'VERIFYING';
export type CoordinationOperationKind =
  | 'CONTACT_DERIVED_RESET'
  | 'SENDER_RELEASE';
export type CoordinationOperationStage =
  | 'RESET_DRAINING'
  | 'RESET_PURGING'
  | 'RELEASE_DRAINING'
  | 'RELEASE_PURGING';

export interface GlobalControl {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly armingEnabled: boolean;
  readonly continuityState: ContinuityState;
  readonly ledgerGeneration: string;
  readonly minimumBuildNumber: number;
  readonly minimumPolicyVersion: number;
  readonly allowedDistributionChannels: readonly string[];
  readonly reasonCode: string;
  readonly updatedAtMs: number;
}

export interface AccountFence {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly mode: AccountMode;
  readonly activeInstallationId: string;
  readonly senderEpoch: number;
  readonly ownerLeaseUntilMs: number;
  readonly nextArmNotBeforeMs: number;
  readonly latestIssuedSubmitNotAfterMs: number;
  readonly resetGeneration: number;
  readonly birthdayAutomationNotBeforeMs: number;
  readonly transferTargetInstallationId?: string | undefined;
  readonly transferDrainUntilMs?: number | undefined;
  readonly deletionDrainUntilMs?: number | undefined;
  readonly createdAtMs: number;
  readonly updatedAtMs: number;
}

export interface Installation {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly installationId: string;
  readonly state: InstallationState;
  readonly epoch: number;
  readonly appBuildNumber: number;
  readonly policyVersion: number;
  readonly distributionChannel: string;
  readonly lastSeenAtMs: number;
  readonly cleanupAtMs?: number | undefined;
}

export interface BudgetEntry {
  readonly id: string;
  readonly armedAtMs: number;
}

export interface ArmBudget {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly purpose: Purpose;
  readonly entries: readonly BudgetEntry[];
  readonly newestEntryAtMs: number;
  readonly cleanupAtMs: number;
}

export interface Claim {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly claimId: string;
  readonly purpose: Purpose;
  readonly claimRequestId: string;
  readonly ownerInstallationId: string;
  readonly ownerEpoch: number;
  readonly resetGeneration: number;
  readonly state: ClaimState;
  readonly attempt: 1 | 2;
  readonly retryAuthorizationGeneration: number;
  readonly retryRequestId?: string | undefined;
  readonly retryProof?:
    | 'ALL_PARTS_RADIO_OFF'
    | 'ALL_PARTS_NO_SERVICE'
    | undefined;
  readonly claimExpiresAtMs: number;
  readonly maxPossibleSubmitNotAfterMs: number;
  readonly serverSubmitNotAfterMs?: number | undefined;
  readonly testBarrierOutcome?: TestBarrierOutcome | undefined;
  readonly occurrenceAliasKeys: readonly string[];
  readonly destinationAliasKeys: readonly string[];
  readonly testMaterialAliasKeys: readonly string[];
  readonly createdAtMs: number;
  readonly updatedAtMs: number;
  readonly cleanupAtMs: number;
}

export interface DestinationGuard {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly aliasKey: string;
  readonly linkedClaimId: string;
  readonly ownerEpoch: number;
  readonly state: GuardState;
  readonly createdAtMs: number;
  readonly updatedAtMs: number;
  readonly cleanupAtMs: number;
}

export interface OccurrenceKey {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly aliasKey: string;
  readonly linkedClaimId: string;
  readonly state: GuardState;
  readonly createdAtMs: number;
  readonly updatedAtMs: number;
  readonly cleanupAtMs: number;
}

export interface ClaimRequestRecord {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestKey: string;
  readonly purpose: Purpose;
  readonly linkedClaimId: string;
  readonly createdAtMs: number;
  readonly cleanupAtMs: number;
}

export type NoWriteReason =
  | 'EXPIRED'
  | 'EXPIRED_RETRY'
  | 'GLOBAL_ARMING_DISABLED'
  | 'CONTINUITY_UNAVAILABLE'
  | 'LEDGER_GENERATION_MISMATCH'
  | 'BUILD_UNSUPPORTED'
  | 'POLICY_UNSUPPORTED'
  | 'CHANNEL_UNSUPPORTED'
  | 'MODE_BLOCKED'
  | 'LEASE_EXPIRED'
  | 'INSTALLATION_MISMATCH'
  | 'EPOCH_MISMATCH'
  | 'RESET_GENERATION_MISMATCH'
  | 'TOO_EARLY'
  | 'BIRTHDAY_RESET_FENCE'
  | 'BUDGET_EXCEEDED'
  | 'CLAIM_STATE_MISMATCH';

export interface ArmedOutcome {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly armRequestId: string;
  readonly purpose: Purpose;
  readonly claimId: string;
  readonly ownerInstallationId: string;
  readonly ownerEpoch: number;
  readonly resetGeneration: number;
  readonly attempt: 1 | 2;
  readonly kind: 'ARMED';
  readonly serverSubmitNotAfterMs: number;
  readonly resolvedAtMs: number;
  readonly cleanupAtMs: number;
}

export interface NoWriteOutcome {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly armRequestId: string;
  readonly purpose: Purpose;
  readonly claimId: string;
  readonly ownerInstallationId: string;
  readonly ownerEpoch: number;
  readonly resetGeneration: number;
  readonly attempt: 1 | 2;
  readonly kind: 'NO_WRITE';
  readonly reason: NoWriteReason;
  readonly resolvedAtMs: number;
  readonly cleanupAtMs: number;
}

export type ArmOutcome = ArmedOutcome | NoWriteOutcome;

export interface DeletionTombstone {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly requestKey: string;
  readonly stage: DeletionStage;
  readonly drainUntilMs: number;
  readonly createdAtMs: number;
  readonly updatedAtMs: number;
  readonly cleanupAtMs?: number | undefined;
  readonly nextSweepAtMs: number;
  readonly sweepAttemptCount: number;
}

/**
 * Account-unlinkable bearer evidence for an account-deletion request. The
 * document ID is a domain-separated SHA-256 hash of the random request UUID.
 * The raw UUID, Firebase UID, Google subject, and email are never persisted.
 */
export type AccountDeletionReceipt =
  | {
      readonly schemaVersion: typeof SCHEMA_VERSION;
      readonly outcome: 'IN_PROGRESS';
      readonly requestedAtMs: number;
      readonly updatedAtMs: number;
    }
  | {
      readonly schemaVersion: typeof SCHEMA_VERSION;
      readonly outcome: 'COMPLETED';
      readonly requestedAtMs: number;
      readonly updatedAtMs: number;
      readonly completedAtMs: number;
      readonly cleanupAtMs: number;
    };

export interface CoordinationPresence {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly state: 'ANDROID_STATE' | 'DELETING';
  readonly ledgerGeneration: string;
  readonly updatedAtMs: number;
}

/**
 * Content-free, account-global exclusion between iOS MessageUI and the Android
 * sender. The document lives outside accounts/{uid}, because sender release
 * deletes that complete tree. `reservationKey` is a domain-separated SHA-256
 * digest of the authenticated UID and an app-minted random UUID.
 */
export interface IOSComposerReservation {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly reservationKey: string;
  readonly phase: 'PREPARED' | 'COMMITTED';
  readonly ledgerGeneration: string;
  readonly createdAtMs: number;
  readonly updatedAtMs: number;
  readonly expiresAtMs: number;
  readonly cleanupAtMs: number;
}

/**
 * A short-lived, account-global mutation fence. It intentionally lives outside
 * accounts/{uid}; sender release must be able to delete that complete tree while
 * the fence remains authoritative.
 */
export interface CoordinationOperation {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly operation: CoordinationOperationKind;
  readonly stage: CoordinationOperationStage;
  readonly requestKey: string;
  readonly requestFingerprint: string;
  readonly accountKey: string;
  readonly androidStateExisted: boolean;
  readonly senderEpochAfter?: number | undefined;
  readonly resetGenerationAfter?: number | undefined;
  readonly birthdayAutomationNotBeforeMs?: number | undefined;
  readonly drainUntilMs?: number | undefined;
  readonly nextSweepAtMs: number;
  readonly sweepAttemptCount: number;
  readonly createdAtMs: number;
  readonly updatedAtMs: number;
}

/**
 * Content-free evidence for replaying an ambiguous destructive request. The
 * document key is a one-way hash of Firebase UID, operation kind, and random
 * request UUID; no provider subject, email, token, contact, phone, or message is
 * stored in the document.
 */
interface CoordinationOperationReceiptBase {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly outcome: 'COMPLETED';
  readonly requestKey: string;
  readonly requestFingerprint: string;
  readonly accountKey: string;
  readonly firebaseAuthPreserved: true;
  readonly completedAtMs: number;
  readonly cleanupAtMs: number;
}

export type CoordinationOperationReceipt =
  | (CoordinationOperationReceiptBase & {
      readonly operation: 'CONTACT_DERIVED_RESET';
      readonly androidStateExisted: true;
      readonly senderEpochAfter: number;
      readonly resetGenerationAfter: number;
      readonly birthdayAutomationNotBeforeMs: number;
      readonly contactDerivedStateErased: true;
    })
  | (CoordinationOperationReceiptBase & {
      readonly operation: 'CONTACT_DERIVED_RESET';
      readonly androidStateExisted: false;
      readonly senderEpochAfter?: never;
      readonly resetGenerationAfter?: never;
      readonly birthdayAutomationNotBeforeMs?: never;
      readonly contactDerivedStateErased: true;
    })
  | (CoordinationOperationReceiptBase & {
      readonly operation: 'SENDER_RELEASE';
      readonly androidStateExisted: true;
      readonly senderEpochAfter: number;
      readonly resetGenerationAfter: number;
      readonly birthdayAutomationNotBeforeMs?: never;
      readonly androidSenderStateErased: true;
    });

export interface BindingInput {
  readonly ledgerGeneration: string;
  readonly installationId: string;
  readonly senderEpoch: number;
  readonly resetGeneration: number;
  readonly appBuildNumber: number;
  readonly policyVersion: number;
  readonly distributionChannel: string;
}

export type SuppressionReason =
  | 'DELETION_SUPPRESSED'
  | 'RESET_SUPPRESSED'
  | 'MISSING_FENCE'
  | 'MISSING_CLAIM'
  | 'UNKNOWN_HISTORY'
  | 'IOS_COMPOSER_RESERVED';

export type CompanionStatus =
  | {
      readonly composerAllowed: true;
      readonly state: 'NO_ANDROID_STATE';
      readonly serverNowMs: number;
      readonly ledgerGeneration: string;
    }
  | {
      readonly composerAllowed: false;
      readonly state:
        | 'MANAGED_BY_ANDROID'
        | 'DELETING'
        | 'SAFETY_STATUS_UNAVAILABLE';
      readonly serverNowMs: number;
      readonly ledgerGeneration?: string;
    };

export interface KeyRingEntry {
  readonly version: string;
  readonly key: Uint8Array;
}

export interface KeyRing {
  readonly current: KeyRingEntry;
  readonly previous?: KeyRingEntry;
}
