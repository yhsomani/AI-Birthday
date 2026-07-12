import { createHash } from 'node:crypto';

import {
  DELETION_RECEIPT_RETENTION_MS,
  SCHEMA_VERSION,
  type AccountFence,
  type AccountDeletionReceipt,
  type DeletionTombstone,
} from './model.js';
import { safeAddMs } from './policies.js';

const RECEIPT_KEY_DOMAIN = 'birthday-deletion-receipt-v1\0';
const RECEIPT_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u;

function isSafeTime(value: number): boolean {
  return Number.isSafeInteger(value) && value >= 0;
}

export function deriveDeletionReceiptKey(receiptId: string): string {
  return createHash('sha256')
    .update(RECEIPT_KEY_DOMAIN, 'utf8')
    .update(receiptId, 'utf8')
    .digest('hex');
}

export function inProgressDeletionReceipt(
  requestedAtMs: number,
  updatedAtMs: number,
): Extract<AccountDeletionReceipt, { readonly outcome: 'IN_PROGRESS' }> {
  if (
    !isSafeTime(requestedAtMs) ||
    !isSafeTime(updatedAtMs) ||
    requestedAtMs > updatedAtMs
  ) {
    throw new Error('INVALID_DELETION_RECEIPT_TIMESTAMPS');
  }
  return {
    schemaVersion: SCHEMA_VERSION,
    outcome: 'IN_PROGRESS',
    requestedAtMs,
    updatedAtMs,
  };
}

export function completedDeletionReceipt(
  receipt: Extract<AccountDeletionReceipt, { readonly outcome: 'IN_PROGRESS' }>,
  completedAtMs: number,
): Extract<AccountDeletionReceipt, { readonly outcome: 'COMPLETED' }> {
  if (
    !isSafeTime(completedAtMs) ||
    !isSafeTime(receipt.requestedAtMs) ||
    !isSafeTime(receipt.updatedAtMs) ||
    receipt.requestedAtMs > receipt.updatedAtMs ||
    receipt.updatedAtMs > completedAtMs
  ) {
    throw new Error('INVALID_DELETION_RECEIPT_TIMESTAMPS');
  }
  return {
    schemaVersion: SCHEMA_VERSION,
    outcome: 'COMPLETED',
    requestedAtMs: receipt.requestedAtMs,
    updatedAtMs: completedAtMs,
    completedAtMs,
    cleanupAtMs: safeAddMs(completedAtMs, DELETION_RECEIPT_RETENTION_MS),
  };
}

export type AccountDeletionReceiptResponse =
  | {
      readonly kind: 'IN_PROGRESS';
      readonly requestedAtMs: number;
      readonly updatedAtMs: number;
    }
  | {
      readonly kind: 'COMPLETED';
      readonly requestedAtMs: number;
      readonly completedAtMs: number;
      readonly appAccountDeleted: true;
      readonly serverDataDeleted: true;
      readonly externalCopiesNotDeleted: true;
    }
  | { readonly kind: 'NOT_FOUND' };

export function deletionReceiptResponse(
  receipt: AccountDeletionReceipt | null,
): AccountDeletionReceiptResponse {
  if (receipt === null) {
    return { kind: 'NOT_FOUND' };
  }
  if (receipt.outcome === 'IN_PROGRESS') {
    return {
      kind: 'IN_PROGRESS',
      requestedAtMs: receipt.requestedAtMs,
      updatedAtMs: receipt.updatedAtMs,
    };
  }
  return {
    kind: 'COMPLETED',
    requestedAtMs: receipt.requestedAtMs,
    completedAtMs: receipt.completedAtMs,
    appAccountDeleted: true,
    serverDataDeleted: true,
    externalCopiesNotDeleted: true,
  };
}

export type AccountDeletionStartResponse =
  | {
      readonly kind: 'STARTED' | 'REPLAYED';
      readonly receiptId: string;
      readonly tombstone: {
        readonly schemaVersion: typeof SCHEMA_VERSION;
        readonly requestKey: string;
        readonly stage: DeletionTombstone['stage'];
        readonly drainUntilMs: number;
        readonly createdAtMs: number;
        readonly updatedAtMs: number;
        readonly cleanupAtMs?: number | undefined;
      };
      readonly fence: {
        readonly mode: 'DELETING';
        readonly senderEpoch: number;
        readonly resetGeneration: number;
        readonly deletionDrainUntilMs: number;
      } | null;
    }
  | {
      readonly kind: 'REFUSED';
      readonly reason:
        | 'COORDINATION_OPERATION_IN_PROGRESS'
        | 'REQUEST_MISMATCH';
    };

function deletionFenceProjection(
  fence: AccountFence | null,
  expectedDrainUntilMs: number,
): {
  readonly mode: 'DELETING';
  readonly senderEpoch: number;
  readonly resetGeneration: number;
  readonly deletionDrainUntilMs: number;
} | null {
  if (fence === null) {
    return null;
  }
  const deletionDrainUntilMs = fence.deletionDrainUntilMs;
  if (
    fence.mode !== 'DELETING' ||
    deletionDrainUntilMs === undefined ||
    deletionDrainUntilMs !== expectedDrainUntilMs ||
    !Number.isSafeInteger(fence.senderEpoch) ||
    fence.senderEpoch <= 0 ||
    !Number.isSafeInteger(fence.resetGeneration) ||
    fence.resetGeneration <= 0
  ) {
    throw new Error('INVALID_DELETION_FENCE');
  }
  return {
    mode: 'DELETING',
    senderEpoch: fence.senderEpoch,
    resetGeneration: fence.resetGeneration,
    deletionDrainUntilMs,
  };
}

export function deletionStartResponse(
  decision:
    | {
        readonly kind: 'STARTED' | 'REPLAYED';
        readonly tombstone: DeletionTombstone;
        readonly fence: AccountFence | null;
      }
    | {
        readonly kind: 'REFUSED';
        readonly reason:
          | 'COORDINATION_OPERATION_IN_PROGRESS'
          | 'REQUEST_MISMATCH';
      },
  receiptId: string,
): AccountDeletionStartResponse {
  if (decision.kind === 'REFUSED') {
    return decision;
  }
  const tombstone = decision.tombstone;
  if (
    !RECEIPT_ID_PATTERN.test(receiptId) ||
    tombstone.requestKey !== deriveDeletionReceiptKey(receiptId) ||
    !isSafeTime(tombstone.createdAtMs) ||
    !isSafeTime(tombstone.updatedAtMs) ||
    !isSafeTime(tombstone.drainUntilMs) ||
    tombstone.createdAtMs > tombstone.updatedAtMs ||
    tombstone.createdAtMs > tombstone.drainUntilMs ||
    (tombstone.cleanupAtMs !== undefined &&
      (!isSafeTime(tombstone.cleanupAtMs) ||
        tombstone.updatedAtMs > tombstone.cleanupAtMs))
  ) {
    throw new Error('INVALID_DELETION_TOMBSTONE_PROJECTION');
  }
  return {
    kind: decision.kind,
    receiptId,
    tombstone: {
      schemaVersion: SCHEMA_VERSION,
      requestKey: tombstone.requestKey,
      stage: tombstone.stage,
      drainUntilMs: tombstone.drainUntilMs,
      createdAtMs: tombstone.createdAtMs,
      updatedAtMs: tombstone.updatedAtMs,
      ...(tombstone.cleanupAtMs === undefined
        ? {}
        : { cleanupAtMs: tombstone.cleanupAtMs }),
    },
    fence: deletionFenceProjection(decision.fence, tombstone.drainUntilMs),
  };
}
