import { Timestamp } from 'firebase-admin/firestore';
import { z } from 'zod';

import {
  SCHEMA_VERSION,
  type AccountFence,
  type AccountDeletionReceipt,
  type ArmBudget,
  type ArmOutcome,
  type Claim,
  type ClaimRequestRecord,
  type CoordinationOperation,
  type CoordinationOperationReceipt,
  type CoordinationPresence,
  type DeletionTombstone,
  type DestinationGuard,
  type GlobalControl,
  type Installation,
  type IOSComposerReservation,
  type OccurrenceKey,
} from '../domain/model.js';

const time = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER);
const version = z.number().int().positive();
const schemaVersion = z.literal(SCHEMA_VERSION);
const opaque = z.string().min(1).max(128);

const globalControlSchema: z.ZodType<GlobalControl> = z.object({
  schemaVersion,
  armingEnabled: z.boolean(),
  continuityState: z.enum(['HEALTHY', 'FROZEN', 'RECOVERY_REQUIRED']),
  ledgerGeneration: opaque,
  minimumBuildNumber: version,
  minimumPolicyVersion: version,
  allowedDistributionChannels: z.array(opaque).max(8),
  reasonCode: opaque,
  updatedAtMs: time,
});

const accountFenceSchema: z.ZodType<AccountFence> = z.object({
  schemaVersion,
  mode: z.enum([
    'TEST_ONLY',
    'PAUSED_REPAIR',
    'AUTOMATION_ACTIVE',
    'TRANSFER_PENDING',
    'DELETING',
  ]),
  activeInstallationId: opaque,
  senderEpoch: version,
  ownerLeaseUntilMs: time,
  nextArmNotBeforeMs: time,
  latestIssuedSubmitNotAfterMs: time,
  resetGeneration: version,
  birthdayAutomationNotBeforeMs: time,
  transferTargetInstallationId: opaque.optional(),
  transferDrainUntilMs: time.optional(),
  deletionDrainUntilMs: time.optional(),
  createdAtMs: time,
  updatedAtMs: time,
});

const installationSchema: z.ZodType<Installation> = z.object({
  schemaVersion,
  installationId: opaque,
  state: z.enum(['ACTIVE', 'STANDBY', 'REVOKED']),
  epoch: z.number().int().nonnegative(),
  appBuildNumber: version,
  policyVersion: version,
  distributionChannel: opaque,
  lastSeenAtMs: time,
  cleanupAtMs: time.optional(),
});

const claimSchema: z.ZodType<Claim> = z.object({
  schemaVersion,
  claimId: opaque,
  purpose: z.enum(['BIRTHDAY', 'TEST']),
  claimRequestId: opaque,
  ownerInstallationId: opaque,
  ownerEpoch: version,
  resetGeneration: version,
  state: z.enum([
    'CLAIMED',
    'EXPIRED_NO_ARM',
    'ARMED',
    'RETRYABLE_ZERO',
    'RETRY_CLAIMED',
    'RETRY_EXPIRED_NO_ARM',
    'TERMINAL',
  ]),
  attempt: z.union([z.literal(1), z.literal(2)]),
  retryAuthorizationGeneration: z.number().int().nonnegative(),
  retryRequestId: z.uuid().optional(),
  retryProof: z
    .enum(['ALL_PARTS_RADIO_OFF', 'ALL_PARTS_NO_SERVICE'])
    .optional(),
  claimExpiresAtMs: time,
  maxPossibleSubmitNotAfterMs: time,
  serverSubmitNotAfterMs: time.optional(),
  testBarrierOutcome: z
    .enum([
      'SENT_ALL_PARTS_IN_WINDOW',
      'SENT_EVIDENCE_LATE',
      'FAILED_ZERO_ACCEPTED',
      'FAILED_OR_UNKNOWN',
      'CLEANUP_CANCELLED',
    ])
    .optional(),
  occurrenceAliasKeys: z.array(opaque).max(4),
  destinationAliasKeys: z.array(opaque).max(4),
  testMaterialAliasKeys: z.array(opaque).max(2),
  createdAtMs: time,
  updatedAtMs: time,
  cleanupAtMs: time,
});

const destinationGuardSchema: z.ZodType<DestinationGuard> = z.object({
  schemaVersion,
  aliasKey: opaque,
  linkedClaimId: opaque,
  ownerEpoch: version,
  state: z.enum(['RESERVED', 'EXPIRED_NO_ARM_RECLAIMABLE', 'ARMED']),
  createdAtMs: time,
  updatedAtMs: time,
  cleanupAtMs: time,
});

const occurrenceKeySchema: z.ZodType<OccurrenceKey> = z.object({
  schemaVersion,
  aliasKey: opaque,
  linkedClaimId: opaque,
  state: z.enum(['RESERVED', 'EXPIRED_NO_ARM_RECLAIMABLE', 'ARMED']),
  createdAtMs: time,
  updatedAtMs: time,
  cleanupAtMs: time,
});

const claimRequestRecordSchema: z.ZodType<ClaimRequestRecord> = z.object({
  schemaVersion,
  requestKey: opaque,
  purpose: z.enum(['BIRTHDAY', 'TEST']),
  linkedClaimId: opaque,
  createdAtMs: time,
  cleanupAtMs: time,
});

const budgetSchema: z.ZodType<ArmBudget> = z.object({
  schemaVersion,
  purpose: z.enum(['BIRTHDAY', 'TEST']),
  entries: z.array(z.object({ id: opaque, armedAtMs: time })).max(20),
  newestEntryAtMs: time,
  cleanupAtMs: time,
});

const outcomeSchema: z.ZodType<ArmOutcome> = z.discriminatedUnion('kind', [
  z.object({
    schemaVersion,
    armRequestId: opaque,
    purpose: z.enum(['BIRTHDAY', 'TEST']),
    claimId: opaque,
    ownerInstallationId: opaque,
    ownerEpoch: version,
    resetGeneration: version,
    attempt: z.union([z.literal(1), z.literal(2)]),
    kind: z.literal('ARMED'),
    serverSubmitNotAfterMs: time,
    resolvedAtMs: time,
    cleanupAtMs: time,
  }),
  z.object({
    schemaVersion,
    armRequestId: opaque,
    purpose: z.enum(['BIRTHDAY', 'TEST']),
    claimId: opaque,
    ownerInstallationId: opaque,
    ownerEpoch: version,
    resetGeneration: version,
    attempt: z.union([z.literal(1), z.literal(2)]),
    kind: z.literal('NO_WRITE'),
    reason: z.enum([
      'EXPIRED',
      'EXPIRED_RETRY',
      'GLOBAL_ARMING_DISABLED',
      'CONTINUITY_UNAVAILABLE',
      'LEDGER_GENERATION_MISMATCH',
      'BUILD_UNSUPPORTED',
      'POLICY_UNSUPPORTED',
      'CHANNEL_UNSUPPORTED',
      'MODE_BLOCKED',
      'LEASE_EXPIRED',
      'INSTALLATION_MISMATCH',
      'EPOCH_MISMATCH',
      'RESET_GENERATION_MISMATCH',
      'TOO_EARLY',
      'BIRTHDAY_RESET_FENCE',
      'BUDGET_EXCEEDED',
      'CLAIM_STATE_MISMATCH',
    ]),
    resolvedAtMs: time,
    cleanupAtMs: time,
  }),
]);

const tombstoneSchema: z.ZodType<DeletionTombstone> = z
  .object({
    schemaVersion,
    requestKey: z.string().regex(/^[a-f0-9]{64}$/u),
    stage: z.enum([
      'DRAINING',
      'PURGING',
      'AUTH_DELETION_PENDING',
      'VERIFYING',
    ]),
    drainUntilMs: time,
    createdAtMs: time,
    updatedAtMs: time,
    cleanupAtMs: time.optional(),
    cleanupAt: z.instanceof(Timestamp).optional(),
    nextSweepAtMs: time,
    sweepAttemptCount: z.number().int().min(0).max(30),
  })
  .strict()
  .refine(
    value =>
      value.createdAtMs <= value.updatedAtMs &&
      value.createdAtMs <= value.drainUntilMs &&
      (value.cleanupAtMs === undefined ||
        value.updatedAtMs <= value.cleanupAtMs),
    { message: 'Deletion tombstone timestamps are out of order' },
  )
  .transform(withoutTtlTimestamp);

const deletionReceiptSchema: z.ZodType<AccountDeletionReceipt> = z.union([
  z
    .object({
      schemaVersion,
      outcome: z.literal('IN_PROGRESS'),
      requestedAtMs: time,
      updatedAtMs: time,
    })
    .strict()
    .refine(value => value.requestedAtMs <= value.updatedAtMs, {
      message: 'In-progress deletion receipt timestamps are out of order',
    }),
  z
    .object({
      schemaVersion,
      outcome: z.literal('COMPLETED'),
      requestedAtMs: time,
      updatedAtMs: time,
      completedAtMs: time,
      cleanupAtMs: time,
      cleanupAt: z.instanceof(Timestamp).optional(),
    })
    .strict()
    .refine(
      value =>
        value.requestedAtMs <= value.updatedAtMs &&
        value.updatedAtMs <= value.completedAtMs &&
        value.completedAtMs <= value.cleanupAtMs,
      { message: 'Completed deletion receipt timestamps are out of order' },
    )
    .transform(withoutTtlTimestamp),
]);

const presenceSchema: z.ZodType<CoordinationPresence> = z.object({
  schemaVersion,
  state: z.enum(['ANDROID_STATE', 'DELETING']),
  ledgerGeneration: opaque,
  updatedAtMs: time,
});

const iosComposerReservationSchema: z.ZodType<IOSComposerReservation> = z
  .object({
    schemaVersion,
    reservationKey: z.string().regex(/^[a-f0-9]{64}$/u),
    phase: z.enum(['PREPARED', 'COMMITTED']),
    ledgerGeneration: opaque,
    createdAtMs: time,
    updatedAtMs: time,
    expiresAtMs: time,
    cleanupAtMs: time,
    cleanupAt: z.instanceof(Timestamp).optional(),
  })
  .strict()
  .refine(
    value =>
      value.createdAtMs <= value.updatedAtMs &&
      value.updatedAtMs < value.expiresAtMs &&
      value.expiresAtMs === value.cleanupAtMs,
    { message: 'iOS composer reservation timestamps are out of order' },
  )
  .transform(withoutTtlTimestamp);

const operationCommon = {
  schemaVersion,
  requestKey: z.string().regex(/^[a-f0-9]{64}$/u),
  requestFingerprint: z.string().regex(/^[a-f0-9]{64}$/u),
  accountKey: z.string().regex(/^[a-f0-9]{64}$/u),
  nextSweepAtMs: time,
  sweepAttemptCount: z.number().int().min(0).max(30),
  createdAtMs: time,
  updatedAtMs: time,
} as const;

const operationSchema: z.ZodType<CoordinationOperation> = z.union([
  z
    .object({
      ...operationCommon,
      operation: z.literal('CONTACT_DERIVED_RESET'),
      stage: z.literal('RESET_DRAINING'),
      androidStateExisted: z.literal(true),
      senderEpochAfter: version,
      resetGenerationAfter: version,
      birthdayAutomationNotBeforeMs: time,
      drainUntilMs: time,
    })
    .strict(),
  z
    .object({
      ...operationCommon,
      operation: z.literal('CONTACT_DERIVED_RESET'),
      stage: z.literal('RESET_PURGING'),
      androidStateExisted: z.literal(true),
      senderEpochAfter: version,
      resetGenerationAfter: version,
      birthdayAutomationNotBeforeMs: time,
    })
    .strict(),
  z
    .object({
      ...operationCommon,
      operation: z.literal('CONTACT_DERIVED_RESET'),
      stage: z.literal('RESET_PURGING'),
      androidStateExisted: z.literal(false),
    })
    .strict(),
  z
    .object({
      ...operationCommon,
      operation: z.literal('SENDER_RELEASE'),
      stage: z.literal('RELEASE_DRAINING'),
      androidStateExisted: z.literal(true),
      senderEpochAfter: version,
      resetGenerationAfter: version,
      drainUntilMs: time,
    })
    .strict(),
  z
    .object({
      ...operationCommon,
      operation: z.literal('SENDER_RELEASE'),
      stage: z.literal('RELEASE_PURGING'),
      androidStateExisted: z.literal(true),
      senderEpochAfter: version,
      resetGenerationAfter: version,
    })
    .strict(),
]);

const operationReceiptCommon = {
  schemaVersion,
  outcome: z.literal('COMPLETED'),
  requestKey: z.string().regex(/^[a-f0-9]{64}$/u),
  requestFingerprint: z.string().regex(/^[a-f0-9]{64}$/u),
  accountKey: z.string().regex(/^[a-f0-9]{64}$/u),
  firebaseAuthPreserved: z.literal(true),
  completedAtMs: time,
  cleanupAtMs: time,
  cleanupAt: z.instanceof(Timestamp).optional(),
} as const;

function withoutTtlTimestamp<
  T extends { readonly cleanupAt?: Timestamp | undefined },
>(value: T): Omit<T, 'cleanupAt'> {
  const { cleanupAt, ...document } = value;
  void cleanupAt;
  return document;
}

const operationReceiptSchema: z.ZodType<CoordinationOperationReceipt> = z.union(
  [
    z
      .object({
        ...operationReceiptCommon,
        operation: z.literal('CONTACT_DERIVED_RESET'),
        androidStateExisted: z.literal(true),
        senderEpochAfter: version,
        resetGenerationAfter: version,
        birthdayAutomationNotBeforeMs: time,
        contactDerivedStateErased: z.literal(true),
      })
      .strict()
      .transform(withoutTtlTimestamp),
    z
      .object({
        ...operationReceiptCommon,
        operation: z.literal('CONTACT_DERIVED_RESET'),
        androidStateExisted: z.literal(false),
        contactDerivedStateErased: z.literal(true),
      })
      .strict()
      .transform(withoutTtlTimestamp),
    z
      .object({
        ...operationReceiptCommon,
        operation: z.literal('SENDER_RELEASE'),
        androidStateExisted: z.literal(true),
        senderEpochAfter: version,
        resetGenerationAfter: version,
        androidSenderStateErased: z.literal(true),
      })
      .strict()
      .transform(withoutTtlTimestamp),
  ],
);

function decode<T>(schema: z.ZodType<T>, value: unknown): T | null {
  const result = schema.safeParse(value);
  return result.success ? result.data : null;
}

export const decodeGlobalControl = (value: unknown): GlobalControl | null =>
  decode(globalControlSchema, value);
export const decodeAccountFence = (value: unknown): AccountFence | null =>
  decode(accountFenceSchema, value);
export const decodeInstallation = (value: unknown): Installation | null =>
  decode(installationSchema, value);
export const decodeClaim = (value: unknown): Claim | null =>
  decode(claimSchema, value);
export const decodeDestinationGuard = (
  value: unknown,
): DestinationGuard | null => decode(destinationGuardSchema, value);
export const decodeOccurrenceKey = (value: unknown): OccurrenceKey | null =>
  decode(occurrenceKeySchema, value);
export const decodeClaimRequestRecord = (
  value: unknown,
): ClaimRequestRecord | null => decode(claimRequestRecordSchema, value);
export const decodeBudget = (value: unknown): ArmBudget | null =>
  decode(budgetSchema, value);
export const decodeOutcome = (value: unknown): ArmOutcome | null =>
  decode(outcomeSchema, value);
export const decodeTombstone = (value: unknown): DeletionTombstone | null =>
  decode(tombstoneSchema, value);
export const decodeAccountDeletionReceipt = (
  value: unknown,
): AccountDeletionReceipt | null => decode(deletionReceiptSchema, value);
export const decodePresence = (value: unknown): CoordinationPresence | null =>
  decode(presenceSchema, value);
export const decodeIOSComposerReservation = (
  value: unknown,
): IOSComposerReservation | null => decode(iosComposerReservationSchema, value);
export const decodeCoordinationOperation = (
  value: unknown,
): CoordinationOperation | null => decode(operationSchema, value);
export const decodeCoordinationOperationReceipt = (
  value: unknown,
): CoordinationOperationReceipt | null => decode(operationReceiptSchema, value);

function withoutUndefined(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(withoutUndefined);
  }
  if (value !== null && typeof value === 'object') {
    const entries = Object.entries(value).flatMap(([key, item]) =>
      item === undefined ? [] : [[key, withoutUndefined(item)] as const],
    );
    return Object.fromEntries(entries);
  }
  return value;
}

export function encodeDocument(value: object): Record<string, unknown> {
  const encoded = withoutUndefined(value) as Record<string, unknown>;
  if ('cleanupAtMs' in value && typeof value.cleanupAtMs === 'number') {
    encoded.cleanupAt = Timestamp.fromMillis(value.cleanupAtMs);
  }
  return encoded;
}
