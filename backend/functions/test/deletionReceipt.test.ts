import { createHash } from 'node:crypto';
import { describe, expect, it } from 'vitest';

import {
  completedDeletionReceipt,
  deletionReceiptResponse,
  deletionStartResponse,
  deriveDeletionReceiptKey,
  inProgressDeletionReceipt,
} from '../src/domain/deletionReceipt.js';
import {
  DELETION_RECEIPT_RETENTION_MS,
  SCHEMA_VERSION,
  type DeletionTombstone,
} from '../src/domain/model.js';
import { fence, NOW_MS } from './fixtures.js';

const receiptId = '00000000-0000-4000-8000-000000001101';

describe('content-free account-deletion receipt', () => {
  it('uses an explicit domain-separated one-way key', () => {
    const expected = createHash('sha256')
      .update('birthday-deletion-receipt-v1\0', 'utf8')
      .update(receiptId, 'utf8')
      .digest('hex');
    expect(deriveDeletionReceiptKey(receiptId)).toBe(expected);
    expect(deriveDeletionReceiptKey(receiptId)).toMatch(/^[a-f0-9]{64}$/u);
    expect(deriveDeletionReceiptKey(receiptId)).not.toContain(receiptId);
    expect(deriveDeletionReceiptKey(receiptId)).not.toBe(
      createHash('sha256').update(receiptId).digest('hex'),
    );
  });

  it('returns strict non-terminal and terminal projections', () => {
    const pending = inProgressDeletionReceipt(NOW_MS, NOW_MS + 1);
    expect(deletionReceiptResponse(null)).toEqual({ kind: 'NOT_FOUND' });
    expect(deletionReceiptResponse(pending)).toEqual({
      kind: 'IN_PROGRESS',
      requestedAtMs: NOW_MS,
      updatedAtMs: NOW_MS + 1,
    });
    const completed = completedDeletionReceipt(pending, NOW_MS + 2);
    expect(completed).toMatchObject({
      outcome: 'COMPLETED',
      completedAtMs: NOW_MS + 2,
      cleanupAtMs: NOW_MS + 2 + DELETION_RECEIPT_RETENTION_MS,
    });
    expect(deletionReceiptResponse(completed)).toEqual({
      kind: 'COMPLETED',
      requestedAtMs: NOW_MS,
      completedAtMs: NOW_MS + 2,
      appAccountDeleted: true,
      serverDataDeleted: true,
      externalCopiesNotDeleted: true,
    });
  });

  it('returns only the frozen minimal deletion-start projection', () => {
    const baseTombstone: DeletionTombstone = {
      schemaVersion: SCHEMA_VERSION,
      requestKey: deriveDeletionReceiptKey(receiptId),
      stage: 'DRAINING',
      drainUntilMs: NOW_MS + 1,
      createdAtMs: NOW_MS,
      updatedAtMs: NOW_MS,
      nextSweepAtMs: NOW_MS + 2,
      sweepAttemptCount: 7,
    };
    for (const kind of ['STARTED', 'REPLAYED'] as const) {
      const tombstone: DeletionTombstone =
        kind === 'STARTED'
          ? baseTombstone
          : {
              ...baseTombstone,
              stage: 'VERIFYING',
              updatedAtMs: NOW_MS + 2,
              cleanupAtMs: NOW_MS + 3,
            };
      const expectedTombstone = {
        schemaVersion: 1 as const,
        requestKey: deriveDeletionReceiptKey(receiptId),
        stage: tombstone.stage,
        drainUntilMs: NOW_MS + 1,
        createdAtMs: NOW_MS,
        updatedAtMs: tombstone.updatedAtMs,
        ...(tombstone.cleanupAtMs === undefined
          ? {}
          : { cleanupAtMs: tombstone.cleanupAtMs }),
      };

      const withoutAndroid = deletionStartResponse(
        { kind, tombstone, fence: null },
        receiptId,
      );
      expect(withoutAndroid).toEqual({
        kind,
        receiptId,
        tombstone: expectedTombstone,
        fence: null,
      });

      const withAndroid = deletionStartResponse(
        {
          kind,
          tombstone,
          fence: fence({
            mode: 'DELETING',
            deletionDrainUntilMs: tombstone.drainUntilMs,
          }),
        },
        receiptId,
      );
      expect(withAndroid).toEqual({
        kind,
        receiptId,
        tombstone: expectedTombstone,
        fence: {
          mode: 'DELETING',
          senderEpoch: 4,
          resetGeneration: 3,
          deletionDrainUntilMs: tombstone.drainUntilMs,
        },
      });
      expect(JSON.stringify(withAndroid)).not.toMatch(
        /(?:activeInstallationId|nextSweepAtMs|sweepAttemptCount)/u,
      );
      expect(JSON.stringify(withAndroid).split(receiptId)).toHaveLength(2);
    }

    expect(
      deletionStartResponse(
        { kind: 'REFUSED', reason: 'COORDINATION_OPERATION_IN_PROGRESS' },
        receiptId,
      ),
    ).toEqual({
      kind: 'REFUSED',
      reason: 'COORDINATION_OPERATION_IN_PROGRESS',
    });
    expect(
      deletionStartResponse(
        { kind: 'REFUSED', reason: 'REQUEST_MISMATCH' },
        receiptId,
      ),
    ).toEqual({ kind: 'REFUSED', reason: 'REQUEST_MISMATCH' });
  });

  it('rejects malformed start evidence and out-of-order receipt times', () => {
    const tombstone: DeletionTombstone = {
      schemaVersion: SCHEMA_VERSION,
      requestKey: deriveDeletionReceiptKey(receiptId),
      stage: 'DRAINING',
      drainUntilMs: NOW_MS,
      createdAtMs: NOW_MS,
      updatedAtMs: NOW_MS,
      nextSweepAtMs: NOW_MS + 1,
      sweepAttemptCount: 0,
    };
    expect(() =>
      deletionStartResponse(
        {
          kind: 'STARTED',
          tombstone: { ...tombstone, requestKey: 'a'.repeat(64) },
          fence: null,
        },
        receiptId,
      ),
    ).toThrow('INVALID_DELETION_TOMBSTONE_PROJECTION');
    expect(() => inProgressDeletionReceipt(NOW_MS + 1, NOW_MS)).toThrow(
      'INVALID_DELETION_RECEIPT_TIMESTAMPS',
    );
    expect(() =>
      completedDeletionReceipt(
        inProgressDeletionReceipt(NOW_MS, NOW_MS + 2),
        NOW_MS + 1,
      ),
    ).toThrow('INVALID_DELETION_RECEIPT_TIMESTAMPS');
  });

  it('contains no account identity or raw bearer field', () => {
    const receipt = completedDeletionReceipt(
      inProgressDeletionReceipt(NOW_MS, NOW_MS),
      NOW_MS + 1,
    );
    const serialized = JSON.stringify(receipt);
    expect(serialized).not.toContain(receiptId);
    expect(serialized).not.toMatch(
      /(?:uid|email|subject|provider|token|receiptId|requestId)/iu,
    );
  });
});
