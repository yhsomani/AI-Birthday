import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { test } from 'node:test';

import {
  deletionStartProjection,
  deriveDeletionReceiptKey,
  isDeletionReceiptId,
  receiptProjection,
} from '../src/deletion-contract.js';

const receiptId = 'abcdef01-abcd-4def-8abc-abcdef001201';
const now = 1_800_000_000_000;

const requestKey = createHash('sha256')
  .update('birthday-deletion-receipt-v1\0', 'utf8')
  .update(receiptId, 'utf8')
  .digest('hex');

const accepted = {
  kind: 'STARTED',
  receiptId,
  tombstone: {
    schemaVersion: 1,
    requestKey,
    stage: 'DRAINING',
    drainUntilMs: now + 1,
    createdAtMs: now,
    updatedAtMs: now,
  },
  fence: {
    mode: 'DELETING',
    senderEpoch: 4,
    resetGeneration: 3,
    deletionDrainUntilMs: now + 1,
  },
};

test('requires a canonical lowercase UUIDv4 and derives the domain key with WebCrypto', async () => {
  assert.equal(isDeletionReceiptId(receiptId), true);
  assert.equal(
    isDeletionReceiptId('00000000-0000-1000-8000-000000001201'),
    false,
  );
  assert.equal(isDeletionReceiptId(receiptId.toUpperCase()), false);
  assert.equal(await deriveDeletionReceiptKey(receiptId), requestKey);
});

test('accepts only the exact bound deletion-start evidence projection', async () => {
  assert.deepEqual(await deletionStartProjection(accepted, receiptId), {
    kind: 'ACCEPTED',
    receiptId,
  });
  assert.deepEqual(
    await deletionStartProjection(
      { ...accepted, kind: 'REPLAYED', fence: null },
      receiptId,
    ),
    { kind: 'ACCEPTED', receiptId },
  );
  assert.deepEqual(
    await deletionStartProjection(
      {
        ...accepted,
        kind: 'REPLAYED',
        tombstone: {
          ...accepted.tombstone,
          stage: 'VERIFYING',
          updatedAtMs: now + 2,
          cleanupAtMs: now + 3,
        },
        fence: null,
      },
      receiptId,
    ),
    { kind: 'ACCEPTED', receiptId },
  );
  assert.deepEqual(
    await deletionStartProjection(
      { kind: 'REFUSED', reason: 'COORDINATION_OPERATION_IN_PROGRESS' },
      receiptId,
    ),
    { kind: 'BUSY' },
  );
  assert.deepEqual(
    await deletionStartProjection(
      { kind: 'REFUSED', reason: 'REQUEST_MISMATCH' },
      receiptId,
    ),
    { kind: 'MISMATCH' },
  );
  assert.deepEqual(
    await deletionStartProjection(
      { kind: 'REFUSED', reason: 'REQUEST_MISMATCH' },
      '00000000-0000-1000-8000-000000001201',
    ),
    { kind: 'UNKNOWN' },
  );

  const { fence: omittedFence, ...missingOuterFence } = accepted;
  void omittedFence;
  const { receiptId: omittedReceiptId, ...missingOuterReceiptId } = accepted;
  void omittedReceiptId;
  const { updatedAtMs: omittedUpdatedAt, ...missingUpdatedAt } =
    accepted.tombstone;
  void omittedUpdatedAt;

  for (const malformed of [
    { ...accepted, receiptId: '00000000-0000-4000-8000-000000001202' },
    { ...accepted, extra: true },
    missingOuterFence,
    missingOuterReceiptId,
    { ...accepted, tombstone: missingUpdatedAt },
    {
      ...accepted,
      tombstone: { ...accepted.tombstone, requestKey: 'a'.repeat(64) },
    },
    {
      ...accepted,
      tombstone: { ...accepted.tombstone, nextSweepAtMs: now + 2 },
    },
    {
      ...accepted,
      tombstone: { ...accepted.tombstone, updatedAtMs: now - 1 },
    },
    {
      ...accepted,
      tombstone: {
        ...accepted.tombstone,
        stage: 'VERIFYING',
        updatedAtMs: now + 2,
        cleanupAtMs: now + 1,
      },
      fence: null,
    },
    {
      ...accepted,
      fence: { ...accepted.fence, activeInstallationId: 'forbidden' },
    },
    {
      ...accepted,
      fence: { ...accepted.fence, deletionDrainUntilMs: now + 2 },
    },
    { kind: 'REFUSED', reason: 'REQUEST_MISMATCH', extra: true },
    { kind: 'REFUSED' },
  ]) {
    assert.deepEqual(await deletionStartProjection(malformed, receiptId), {
      kind: 'UNKNOWN',
    });
  }
});

test('requires exact receipt status keys, truth flags, and ordered timestamps', () => {
  assert.deepEqual(
    receiptProjection({
      kind: 'IN_PROGRESS',
      requestedAtMs: now,
      updatedAtMs: now + 1,
    }),
    { kind: 'IN_PROGRESS', requestedAtMs: now, updatedAtMs: now + 1 },
  );
  assert.deepEqual(
    receiptProjection({
      kind: 'COMPLETED',
      requestedAtMs: now,
      completedAtMs: now + 2,
      appAccountDeleted: true,
      serverDataDeleted: true,
      externalCopiesNotDeleted: true,
    }),
    { kind: 'COMPLETED', requestedAtMs: now, completedAtMs: now + 2 },
  );
  for (const malformed of [
    { kind: 'NOT_FOUND', extra: true },
    { kind: 'IN_PROGRESS', requestedAtMs: now + 1, updatedAtMs: now },
    {
      kind: 'COMPLETED',
      requestedAtMs: now + 1,
      completedAtMs: now,
      appAccountDeleted: true,
      serverDataDeleted: true,
      externalCopiesNotDeleted: true,
    },
    {
      kind: 'COMPLETED',
      requestedAtMs: now,
      completedAtMs: now + 1,
      appAccountDeleted: true,
      serverDataDeleted: true,
      externalCopiesNotDeleted: false,
    },
  ]) {
    assert.deepEqual(receiptProjection(malformed), { kind: 'UNKNOWN' });
  }
});
