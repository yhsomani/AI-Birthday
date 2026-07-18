import { Timestamp } from 'firebase-admin/firestore';
import { describe, expect, it } from 'vitest';

import { makeCoordinationOperationReceipt } from '../src/domain/coordinationOperations.js';
import { SCHEMA_VERSION } from '../src/domain/model.js';
import {
  decodeAccountFence,
  decodeAccountDeletionReceipt,
  decodeBudget,
  decodeClaim,
  decodeClaimRequestRecord,
  decodeCoordinationOperation,
  decodeCoordinationOperationReceipt,
  decodeDestinationGuard,
  decodeGlobalControl,
  decodeInstallation,
  decodeIOSComposerReservation,
  decodeOccurrenceKey,
  decodeOutcome,
  decodePresence,
  decodeTombstone,
  encodeDocument,
} from '../src/persistence/codecs.js';
import {
  INSTALLATION_ID,
  NOW_MS,
  budget,
  claim,
  fence,
  globalControl,
  installation,
} from './fixtures.js';

describe('strict Firestore persistence codecs', () => {
  it('decodes every content-free ledger record and rejects malformed input', () => {
    const birthdayClaim = claim();
    const occurrence = {
      schemaVersion: SCHEMA_VERSION,
      aliasKey: 'v1.occurrence',
      linkedClaimId: birthdayClaim.claimId,
      state: 'RESERVED',
      createdAtMs: NOW_MS,
      updatedAtMs: NOW_MS,
      cleanupAtMs: NOW_MS + 60_000,
    } as const;
    const destination = {
      ...occurrence,
      aliasKey: 'v1.destination',
      ownerEpoch: birthdayClaim.ownerEpoch,
    } as const;
    const request = {
      schemaVersion: SCHEMA_VERSION,
      requestKey: birthdayClaim.claimRequestId,
      purpose: 'BIRTHDAY',
      linkedClaimId: birthdayClaim.claimId,
      createdAtMs: NOW_MS,
      cleanupAtMs: NOW_MS + 60_000,
    } as const;
    const outcome = {
      schemaVersion: SCHEMA_VERSION,
      armRequestId: '00000000-0000-4000-8000-000000000011',
      purpose: 'BIRTHDAY',
      claimId: birthdayClaim.claimId,
      ownerInstallationId: INSTALLATION_ID,
      ownerEpoch: birthdayClaim.ownerEpoch,
      resetGeneration: birthdayClaim.resetGeneration,
      attempt: 1,
      kind: 'ARMED',
      serverSubmitNotAfterMs: NOW_MS + 60_000,
      resolvedAtMs: NOW_MS,
      cleanupAtMs: NOW_MS + 60_000,
    } as const;
    const tombstone = {
      schemaVersion: SCHEMA_VERSION,
      requestKey: 'd'.repeat(64),
      stage: 'DRAINING',
      drainUntilMs: NOW_MS,
      nextSweepAtMs: NOW_MS + 1,
      sweepAttemptCount: 0,
      createdAtMs: NOW_MS,
      updatedAtMs: NOW_MS,
    } as const;
    const presence = {
      schemaVersion: SCHEMA_VERSION,
      state: 'ANDROID_STATE',
      ledgerGeneration: 'ledger-generation-1',
      updatedAtMs: NOW_MS,
    } as const;
    const operation = {
      schemaVersion: SCHEMA_VERSION,
      operation: 'CONTACT_DERIVED_RESET',
      stage: 'RESET_DRAINING',
      requestKey: 'a'.repeat(64),
      requestFingerprint: 'b'.repeat(64),
      accountKey: 'c'.repeat(64),
      androidStateExisted: true,
      senderEpochAfter: 5,
      resetGenerationAfter: 4,
      birthdayAutomationNotBeforeMs: NOW_MS + 24 * 60 * 60_000,
      drainUntilMs: NOW_MS + 60_000,
      nextSweepAtMs: NOW_MS + 60_001,
      sweepAttemptCount: 0,
      createdAtMs: NOW_MS,
      updatedAtMs: NOW_MS,
    } as const;
    const receipt = makeCoordinationOperationReceipt(
      { ...operation, stage: 'RESET_PURGING', drainUntilMs: undefined },
      NOW_MS + 60_001,
    );
    const iosComposerReservation = {
      schemaVersion: SCHEMA_VERSION,
      reservationKey: 'e'.repeat(64),
      phase: 'COMMITTED',
      ledgerGeneration: 'ledger-generation-1',
      createdAtMs: NOW_MS,
      updatedAtMs: NOW_MS + 1,
      expiresAtMs: NOW_MS + 72 * 60 * 60_000,
      cleanupAtMs: NOW_MS + 72 * 60 * 60_000,
    } as const;

    expect(decodeGlobalControl(globalControl())).toEqual(globalControl());
    expect(decodeAccountFence(fence())).toEqual(fence());
    expect(decodeInstallation(installation())).toEqual(installation());
    expect(decodeClaim(birthdayClaim)).toEqual(birthdayClaim);
    expect(decodeOccurrenceKey(occurrence)).toEqual(occurrence);
    expect(decodeDestinationGuard(destination)).toEqual(destination);
    expect(decodeClaimRequestRecord(request)).toEqual(request);
    expect(decodeBudget(budget('BIRTHDAY', []))).toEqual(
      budget('BIRTHDAY', []),
    );
    expect(decodeOutcome(outcome)).toEqual(outcome);
    expect(decodeTombstone(tombstone)).toEqual(tombstone);
    expect(
      decodeAccountDeletionReceipt({
        schemaVersion: SCHEMA_VERSION,
        outcome: 'IN_PROGRESS',
        requestedAtMs: NOW_MS,
        updatedAtMs: NOW_MS,
      }),
    ).toEqual({
      schemaVersion: SCHEMA_VERSION,
      outcome: 'IN_PROGRESS',
      requestedAtMs: NOW_MS,
      updatedAtMs: NOW_MS,
    });
    expect(
      decodeAccountDeletionReceipt(
        encodeDocument({
          schemaVersion: SCHEMA_VERSION,
          outcome: 'COMPLETED',
          requestedAtMs: NOW_MS,
          updatedAtMs: NOW_MS + 1,
          completedAtMs: NOW_MS + 1,
          cleanupAtMs: NOW_MS + 365 * 24 * 60 * 60_000,
        }),
      ),
    ).toMatchObject({ outcome: 'COMPLETED', completedAtMs: NOW_MS + 1 });
    expect(
      decodeAccountDeletionReceipt({
        schemaVersion: SCHEMA_VERSION,
        outcome: 'IN_PROGRESS',
        requestedAtMs: NOW_MS,
        updatedAtMs: NOW_MS,
        uid: 'forbidden',
      }),
    ).toBeNull();
    expect(
      decodeAccountDeletionReceipt({
        schemaVersion: SCHEMA_VERSION,
        outcome: 'IN_PROGRESS',
        requestedAtMs: NOW_MS + 1,
        updatedAtMs: NOW_MS,
      }),
    ).toBeNull();
    expect(
      decodeAccountDeletionReceipt({
        schemaVersion: SCHEMA_VERSION,
        outcome: 'COMPLETED',
        requestedAtMs: NOW_MS,
        updatedAtMs: NOW_MS + 2,
        completedAtMs: NOW_MS + 1,
        cleanupAtMs: NOW_MS + 3,
      }),
    ).toBeNull();
    expect(
      decodeTombstone({
        ...tombstone,
        updatedAtMs: NOW_MS + 2,
        cleanupAtMs: NOW_MS + 1,
      }),
    ).toBeNull();
    expect(decodePresence(presence)).toEqual(presence);
    expect(
      decodeIOSComposerReservation(encodeDocument(iosComposerReservation)),
    ).toEqual(iosComposerReservation);
    expect(
      decodeIOSComposerReservation({
        ...iosComposerReservation,
        destination: '+919999999999',
      }),
    ).toBeNull();
    expect(
      decodeIOSComposerReservation({
        ...iosComposerReservation,
        expiresAtMs: NOW_MS,
      }),
    ).toBeNull();
    expect(decodeCoordinationOperation(operation)).toEqual(operation);
    expect(decodeCoordinationOperationReceipt(encodeDocument(receipt))).toEqual(
      receipt,
    );
    expect(decodeClaim({ schemaVersion: 999 })).toBeNull();
  });

  it('removes undefined recursively and emits the TTL timestamp only on encode', () => {
    const encoded = encodeDocument({
      schemaVersion: SCHEMA_VERSION,
      cleanupAtMs: NOW_MS,
      absent: undefined,
      nested: { absent: undefined, present: true },
      entries: [{ absent: undefined, present: 1 }],
    });
    expect(encoded).not.toHaveProperty('absent');
    expect(encoded.nested).toEqual({ present: true });
    expect(encoded.entries).toEqual([{ present: 1 }]);
    expect(encoded.cleanupAt).toEqual(Timestamp.fromMillis(NOW_MS));
  });
});
