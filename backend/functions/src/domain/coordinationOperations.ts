import {
  COORDINATION_RECEIPT_RETENTION_MS,
  DAY_MS,
  SCHEMA_VERSION,
  type AccountFence,
  type CoordinationOperation,
  type CoordinationOperationReceipt,
  type DeletionTombstone,
  type Installation,
} from './model.js';
import { cleanupForInstallation, safeAddMs } from './policies.js';

export interface OperationIdentity {
  readonly accountKey: string;
  readonly requestKey: string;
  readonly requestFingerprint: string;
}

export interface SenderReleaseBinding {
  readonly installationId: string;
  readonly senderEpoch: number;
  readonly resetGeneration: number;
}

export type OperationRefusalReason =
  | 'DELETION_SUPPRESSED'
  | 'COORDINATION_OPERATION_IN_PROGRESS'
  | 'REQUEST_MISMATCH'
  | 'RESET_SUPPRESSED'
  | 'CONTINUITY_UNAVAILABLE'
  | 'GENERATION_EXHAUSTED';

export type BeginOperationDecision =
  | {
      readonly kind: 'STARTED';
      readonly operation: CoordinationOperation;
      readonly fence: AccountFence | null;
      readonly activeInstallation: Installation | null;
    }
  | {
      readonly kind: 'IN_PROGRESS';
      readonly operation: CoordinationOperation;
    }
  | {
      readonly kind: 'COMPLETED';
      readonly receipt: CoordinationOperationReceipt;
    }
  | { readonly kind: 'REFUSED'; readonly reason: OperationRefusalReason };

function sameRequest(
  value: Pick<
    CoordinationOperation | CoordinationOperationReceipt,
    'operation' | 'requestKey' | 'requestFingerprint' | 'accountKey'
  >,
  expectedOperation: CoordinationOperation['operation'],
  identity: OperationIdentity,
): boolean {
  return (
    value.operation === expectedOperation &&
    value.accountKey === identity.accountKey &&
    value.requestKey === identity.requestKey &&
    value.requestFingerprint === identity.requestFingerprint
  );
}

function replayOrConflict(
  existingOperation: CoordinationOperation | null,
  receipt: CoordinationOperationReceipt | null,
  expectedOperation: CoordinationOperation['operation'],
  identity: OperationIdentity,
):
  | Extract<BeginOperationDecision, { readonly kind: 'IN_PROGRESS' }>
  | Extract<BeginOperationDecision, { readonly kind: 'COMPLETED' }>
  | Extract<BeginOperationDecision, { readonly kind: 'REFUSED' }>
  | null {
  if (receipt !== null) {
    return sameRequest(receipt, expectedOperation, identity)
      ? { kind: 'COMPLETED', receipt }
      : { kind: 'REFUSED', reason: 'REQUEST_MISMATCH' };
  }
  if (existingOperation === null) {
    return null;
  }
  return sameRequest(existingOperation, expectedOperation, identity)
    ? { kind: 'IN_PROGRESS', operation: existingOperation }
    : { kind: 'REFUSED', reason: 'COORDINATION_OPERATION_IN_PROGRESS' };
}

function nextGeneration(current: number): number | null {
  return current < Number.MAX_SAFE_INTEGER ? current + 1 : null;
}

function androidBindingIsCoherent(
  fence: AccountFence,
  activeInstallation: Installation | null,
): activeInstallation is Installation {
  return (
    activeInstallation?.installationId === fence.activeInstallationId &&
    activeInstallation.state === 'ACTIVE' &&
    activeInstallation.epoch === fence.senderEpoch
  );
}

export function decideBeginContactDerivedReset(
  existingOperation: CoordinationOperation | null,
  receipt: CoordinationOperationReceipt | null,
  tombstone: DeletionTombstone | null,
  fence: AccountFence | null,
  activeInstallation: Installation | null,
  identity: OperationIdentity,
  nowMs: number,
): BeginOperationDecision {
  const replay = replayOrConflict(
    existingOperation,
    receipt,
    'CONTACT_DERIVED_RESET',
    identity,
  );
  if (replay !== null) {
    return replay;
  }
  if (tombstone !== null || fence?.mode === 'DELETING') {
    return { kind: 'REFUSED', reason: 'DELETION_SUPPRESSED' };
  }

  if (fence === null) {
    return {
      kind: 'STARTED',
      operation: {
        schemaVersion: SCHEMA_VERSION,
        operation: 'CONTACT_DERIVED_RESET',
        stage: 'RESET_PURGING',
        requestKey: identity.requestKey,
        requestFingerprint: identity.requestFingerprint,
        accountKey: identity.accountKey,
        androidStateExisted: false,
        nextSweepAtMs: nowMs,
        sweepAttemptCount: 0,
        createdAtMs: nowMs,
        updatedAtMs: nowMs,
      },
      fence: null,
      activeInstallation: null,
    };
  }
  if (!androidBindingIsCoherent(fence, activeInstallation)) {
    return { kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' };
  }
  const senderEpochAfter = nextGeneration(fence.senderEpoch);
  const resetGenerationAfter = nextGeneration(fence.resetGeneration);
  if (senderEpochAfter === null || resetGenerationAfter === null) {
    return { kind: 'REFUSED', reason: 'GENERATION_EXHAUSTED' };
  }
  const birthdayAutomationNotBeforeMs = Math.max(
    fence.birthdayAutomationNotBeforeMs,
    safeAddMs(nowMs, DAY_MS),
  );
  const drainUntilMs = Math.max(nowMs, fence.latestIssuedSubmitNotAfterMs);
  return {
    kind: 'STARTED',
    operation: {
      schemaVersion: SCHEMA_VERSION,
      operation: 'CONTACT_DERIVED_RESET',
      stage: 'RESET_DRAINING',
      requestKey: identity.requestKey,
      requestFingerprint: identity.requestFingerprint,
      accountKey: identity.accountKey,
      androidStateExisted: true,
      senderEpochAfter,
      resetGenerationAfter,
      birthdayAutomationNotBeforeMs,
      drainUntilMs,
      nextSweepAtMs: safeAddMs(drainUntilMs, 1),
      sweepAttemptCount: 0,
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    },
    fence: {
      ...fence,
      mode: 'PAUSED_REPAIR',
      senderEpoch: senderEpochAfter,
      resetGeneration: resetGenerationAfter,
      ownerLeaseUntilMs: nowMs,
      nextArmNotBeforeMs: Math.max(fence.nextArmNotBeforeMs, nowMs),
      birthdayAutomationNotBeforeMs,
      transferTargetInstallationId: undefined,
      transferDrainUntilMs: undefined,
      deletionDrainUntilMs: undefined,
      updatedAtMs: nowMs,
    },
    activeInstallation: {
      ...activeInstallation,
      state: 'ACTIVE',
      epoch: senderEpochAfter,
      lastSeenAtMs: nowMs,
      cleanupAtMs: undefined,
    },
  };
}

export type AdvanceResetDecision =
  | { readonly kind: 'WAIT'; readonly drainUntilMs: number }
  | {
      readonly kind: 'READY_TO_PURGE';
      readonly operation: CoordinationOperation;
    }
  | { readonly kind: 'INVALID_STATE' };

export function decideAdvanceContactDerivedReset(
  operation: CoordinationOperation,
  fence: AccountFence | null,
  activeInstallation: Installation | null,
  nowMs: number,
): AdvanceResetDecision {
  if (
    operation.operation !== 'CONTACT_DERIVED_RESET' ||
    operation.stage !== 'RESET_DRAINING' ||
    operation.drainUntilMs === undefined ||
    operation.senderEpochAfter === undefined ||
    operation.resetGenerationAfter === undefined ||
    fence === null ||
    !androidBindingIsCoherent(fence, activeInstallation) ||
    fence.mode !== 'PAUSED_REPAIR' ||
    fence.senderEpoch !== operation.senderEpochAfter ||
    fence.resetGeneration !== operation.resetGenerationAfter
  ) {
    return { kind: 'INVALID_STATE' };
  }
  if (nowMs <= operation.drainUntilMs) {
    return { kind: 'WAIT', drainUntilMs: operation.drainUntilMs };
  }
  return {
    kind: 'READY_TO_PURGE',
    operation: {
      ...operation,
      stage: 'RESET_PURGING',
      drainUntilMs: undefined,
      nextSweepAtMs: nowMs,
      sweepAttemptCount: 0,
      updatedAtMs: nowMs,
    },
  };
}

export function decideBeginSenderRelease(
  existingOperation: CoordinationOperation | null,
  receipt: CoordinationOperationReceipt | null,
  tombstone: DeletionTombstone | null,
  fence: AccountFence | null,
  activeInstallation: Installation | null,
  binding: SenderReleaseBinding,
  identity: OperationIdentity,
  nowMs: number,
): BeginOperationDecision {
  const replay = replayOrConflict(
    existingOperation,
    receipt,
    'SENDER_RELEASE',
    identity,
  );
  if (replay !== null) {
    return replay;
  }
  if (tombstone !== null || fence?.mode === 'DELETING') {
    return { kind: 'REFUSED', reason: 'DELETION_SUPPRESSED' };
  }
  if (
    fence === null ||
    !androidBindingIsCoherent(fence, activeInstallation) ||
    fence.activeInstallationId !== binding.installationId ||
    fence.senderEpoch !== binding.senderEpoch ||
    fence.resetGeneration !== binding.resetGeneration
  ) {
    return { kind: 'REFUSED', reason: 'RESET_SUPPRESSED' };
  }
  const senderEpochAfter = nextGeneration(fence.senderEpoch);
  if (senderEpochAfter === null) {
    return { kind: 'REFUSED', reason: 'GENERATION_EXHAUSTED' };
  }
  const drainUntilMs = Math.max(nowMs, fence.latestIssuedSubmitNotAfterMs);
  return {
    kind: 'STARTED',
    operation: {
      schemaVersion: SCHEMA_VERSION,
      operation: 'SENDER_RELEASE',
      stage: 'RELEASE_DRAINING',
      requestKey: identity.requestKey,
      requestFingerprint: identity.requestFingerprint,
      accountKey: identity.accountKey,
      androidStateExisted: true,
      senderEpochAfter,
      resetGenerationAfter: fence.resetGeneration,
      drainUntilMs,
      nextSweepAtMs: safeAddMs(drainUntilMs, 1),
      sweepAttemptCount: 0,
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    },
    fence: {
      ...fence,
      mode: 'PAUSED_REPAIR',
      ownerLeaseUntilMs: nowMs,
      transferTargetInstallationId: undefined,
      transferDrainUntilMs: undefined,
      deletionDrainUntilMs: undefined,
      updatedAtMs: nowMs,
    },
    activeInstallation,
  };
}

export type AdvanceReleaseDecision =
  | { readonly kind: 'WAIT'; readonly drainUntilMs: number }
  | {
      readonly kind: 'READY_TO_PURGE';
      readonly operation: CoordinationOperation;
      readonly fence: AccountFence;
      readonly activeInstallation: Installation;
    }
  | { readonly kind: 'INVALID_STATE' };

export function decideAdvanceSenderRelease(
  operation: CoordinationOperation,
  fence: AccountFence | null,
  activeInstallation: Installation | null,
  nowMs: number,
): AdvanceReleaseDecision {
  if (
    operation.operation !== 'SENDER_RELEASE' ||
    operation.stage !== 'RELEASE_DRAINING' ||
    operation.drainUntilMs === undefined ||
    operation.senderEpochAfter === undefined ||
    fence === null ||
    !androidBindingIsCoherent(fence, activeInstallation) ||
    operation.senderEpochAfter !== fence.senderEpoch + 1
  ) {
    return { kind: 'INVALID_STATE' };
  }
  if (nowMs <= operation.drainUntilMs) {
    return { kind: 'WAIT', drainUntilMs: operation.drainUntilMs };
  }
  return {
    kind: 'READY_TO_PURGE',
    operation: {
      ...operation,
      stage: 'RELEASE_PURGING',
      drainUntilMs: undefined,
      nextSweepAtMs: nowMs,
      sweepAttemptCount: 0,
      updatedAtMs: nowMs,
    },
    fence: {
      ...fence,
      mode: 'PAUSED_REPAIR',
      senderEpoch: operation.senderEpochAfter,
      ownerLeaseUntilMs: nowMs,
      updatedAtMs: nowMs,
    },
    activeInstallation: {
      ...activeInstallation,
      state: 'REVOKED',
      epoch: operation.senderEpochAfter,
      lastSeenAtMs: nowMs,
      cleanupAtMs: cleanupForInstallation('REVOKED', nowMs),
    },
  };
}

export function makeCoordinationOperationReceipt(
  operation: CoordinationOperation,
  nowMs: number,
): CoordinationOperationReceipt {
  const common = {
    schemaVersion: SCHEMA_VERSION,
    outcome: 'COMPLETED',
    requestKey: operation.requestKey,
    requestFingerprint: operation.requestFingerprint,
    accountKey: operation.accountKey,
    firebaseAuthPreserved: true,
    completedAtMs: nowMs,
    cleanupAtMs: safeAddMs(nowMs, COORDINATION_RECEIPT_RETENTION_MS),
  } as const;
  if (operation.operation === 'CONTACT_DERIVED_RESET') {
    if (!operation.androidStateExisted) {
      return {
        ...common,
        operation: 'CONTACT_DERIVED_RESET',
        androidStateExisted: false,
        contactDerivedStateErased: true,
      };
    }
    if (
      operation.senderEpochAfter === undefined ||
      operation.resetGenerationAfter === undefined ||
      operation.birthdayAutomationNotBeforeMs === undefined
    ) {
      throw new Error('INVALID_COORDINATION_OPERATION');
    }
    return {
      ...common,
      operation: 'CONTACT_DERIVED_RESET',
      androidStateExisted: true,
      senderEpochAfter: operation.senderEpochAfter,
      resetGenerationAfter: operation.resetGenerationAfter,
      birthdayAutomationNotBeforeMs: operation.birthdayAutomationNotBeforeMs,
      contactDerivedStateErased: true,
    };
  }
  if (
    !operation.androidStateExisted ||
    operation.senderEpochAfter === undefined ||
    operation.resetGenerationAfter === undefined
  ) {
    throw new Error('INVALID_COORDINATION_OPERATION');
  }
  return {
    ...common,
    operation: 'SENDER_RELEASE',
    androidStateExisted: true,
    senderEpochAfter: operation.senderEpochAfter,
    resetGenerationAfter: operation.resetGenerationAfter,
    androidSenderStateErased: true,
  };
}
