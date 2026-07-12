import { describe, expect, it } from 'vitest';
import { Timestamp } from 'firebase-admin/firestore';

import {
  decideAdvanceContactDerivedReset,
  decideAdvanceSenderRelease,
  decideBeginContactDerivedReset,
  decideBeginSenderRelease,
  makeCoordinationOperationReceipt,
} from '../src/domain/coordinationOperations.js';
import { deriveOperationIdentity } from '../src/domain/operationIdentity.js';
import { DAY_MS, SCHEMA_VERSION } from '../src/domain/model.js';
import {
  decodeCoordinationOperation,
  decodeCoordinationOperationReceipt,
} from '../src/persistence/codecs.js';
import { INSTALLATION_ID, NOW_MS, fence, installation } from './fixtures.js';

const RESET_REQUEST_ID = '00000000-0000-4000-8000-000000000501';
const RELEASE_REQUEST_ID = '00000000-0000-4000-8000-000000000502';

describe('contact-derived reset decisions', () => {
  it('atomically advances both generations, pauses, and installs a 24-hour fence', () => {
    const identity = deriveOperationIdentity(
      'uid-reset',
      'CONTACT_DERIVED_RESET',
      RESET_REQUEST_ID,
    );
    const decision = decideBeginContactDerivedReset(
      null,
      null,
      null,
      fence({
        mode: 'TRANSFER_PENDING',
        transferTargetInstallationId: 'b'.repeat(32),
        transferDrainUntilMs: NOW_MS + 1_000,
      }),
      installation(),
      identity,
      NOW_MS,
    );
    expect(decision.kind).toBe('STARTED');
    if (decision.kind !== 'STARTED') {
      return;
    }
    expect(decision.operation).toMatchObject({
      operation: 'CONTACT_DERIVED_RESET',
      stage: 'RESET_DRAINING',
      androidStateExisted: true,
      senderEpochAfter: 5,
      resetGenerationAfter: 4,
      birthdayAutomationNotBeforeMs: NOW_MS + DAY_MS,
    });
    expect(decision.fence).toMatchObject({
      mode: 'PAUSED_REPAIR',
      senderEpoch: 5,
      resetGeneration: 4,
      ownerLeaseUntilMs: NOW_MS,
      birthdayAutomationNotBeforeMs: NOW_MS + DAY_MS,
    });
    expect(decision.fence?.transferTargetInstallationId).toBeUndefined();
    expect(decision.fence?.transferDrainUntilMs).toBeUndefined();
    expect(decision.activeInstallation).toMatchObject({
      state: 'ACTIVE',
      epoch: 5,
    });
    expect(
      decideAdvanceContactDerivedReset(
        decision.operation,
        decision.fence,
        decision.activeInstallation,
        decision.operation.drainUntilMs ?? NOW_MS,
      ),
    ).toEqual({
      kind: 'WAIT',
      drainUntilMs: decision.operation.drainUntilMs,
    });
  });

  it('supports iOS-only cleanup without inventing an Android sender binding', () => {
    const identity = deriveOperationIdentity(
      'uid-ios-only',
      'CONTACT_DERIVED_RESET',
      RESET_REQUEST_ID,
    );
    const decision = decideBeginContactDerivedReset(
      null,
      null,
      null,
      null,
      null,
      identity,
      NOW_MS,
    );
    expect(decision).toMatchObject({
      kind: 'STARTED',
      fence: null,
      activeInstallation: null,
      operation: {
        operation: 'CONTACT_DERIVED_RESET',
        androidStateExisted: false,
      },
    });
  });

  it('replays the exact operation and rejects a different request while fenced', () => {
    const identity = deriveOperationIdentity(
      'uid-reset-replay',
      'CONTACT_DERIVED_RESET',
      RESET_REQUEST_ID,
    );
    const started = decideBeginContactDerivedReset(
      null,
      null,
      null,
      fence(),
      installation(),
      identity,
      NOW_MS,
    );
    expect(started.kind).toBe('STARTED');
    if (started.kind !== 'STARTED') {
      return;
    }
    expect(
      decideBeginContactDerivedReset(
        started.operation,
        null,
        null,
        started.fence,
        started.activeInstallation,
        identity,
        NOW_MS + 1,
      ),
    ).toEqual({ kind: 'IN_PROGRESS', operation: started.operation });
    const other = deriveOperationIdentity(
      'uid-reset-replay',
      'CONTACT_DERIVED_RESET',
      '00000000-0000-4000-8000-000000000503',
    );
    expect(
      decideBeginContactDerivedReset(
        started.operation,
        null,
        null,
        started.fence,
        started.activeInstallation,
        other,
        NOW_MS + 1,
      ),
    ).toEqual({
      kind: 'REFUSED',
      reason: 'COORDINATION_OPERATION_IN_PROGRESS',
    });
  });

  it('fails closed instead of wrapping an exhausted generation', () => {
    const identity = deriveOperationIdentity(
      'uid-reset-overflow',
      'CONTACT_DERIVED_RESET',
      RESET_REQUEST_ID,
    );
    expect(
      decideBeginContactDerivedReset(
        null,
        null,
        null,
        fence({ senderEpoch: Number.MAX_SAFE_INTEGER }),
        installation({ epoch: Number.MAX_SAFE_INTEGER }),
        identity,
        NOW_MS,
      ),
    ).toEqual({ kind: 'REFUSED', reason: 'GENERATION_EXHAUSTED' });
  });
});

describe('sender-release decisions', () => {
  it('freezes immediately but does not revoke until strictly after the permit drain', () => {
    const identity = deriveOperationIdentity(
      'uid-release',
      'SENDER_RELEASE',
      RELEASE_REQUEST_ID,
      [INSTALLATION_ID, '4', '3'],
    );
    const latestIssuedSubmitNotAfterMs = NOW_MS + 60_000;
    const started = decideBeginSenderRelease(
      null,
      null,
      null,
      fence({ latestIssuedSubmitNotAfterMs }),
      installation(),
      {
        installationId: INSTALLATION_ID,
        senderEpoch: 4,
        resetGeneration: 3,
      },
      identity,
      NOW_MS,
    );
    expect(started.kind).toBe('STARTED');
    if (started.kind !== 'STARTED' || started.fence === null) {
      return;
    }
    expect(started.fence).toMatchObject({
      mode: 'PAUSED_REPAIR',
      senderEpoch: 4,
      ownerLeaseUntilMs: NOW_MS,
    });
    expect(started.activeInstallation).toEqual(installation());
    expect(started.operation.drainUntilMs).toBe(latestIssuedSubmitNotAfterMs);
    expect(
      decideAdvanceSenderRelease(
        started.operation,
        started.fence,
        started.activeInstallation,
        latestIssuedSubmitNotAfterMs,
      ),
    ).toEqual({ kind: 'WAIT', drainUntilMs: latestIssuedSubmitNotAfterMs });
    const ready = decideAdvanceSenderRelease(
      started.operation,
      started.fence,
      started.activeInstallation,
      latestIssuedSubmitNotAfterMs + 1,
    );
    expect(ready.kind).toBe('READY_TO_PURGE');
    if (ready.kind === 'READY_TO_PURGE') {
      expect(ready.fence.senderEpoch).toBe(5);
      expect(ready.activeInstallation).toMatchObject({
        state: 'REVOKED',
        epoch: 5,
      });
      expect(ready.operation.stage).toBe('RELEASE_PURGING');
    }
  });

  it('binds a UUID to the exact installation generations', () => {
    const first = deriveOperationIdentity(
      'uid-release-binding',
      'SENDER_RELEASE',
      RELEASE_REQUEST_ID,
      [INSTALLATION_ID, '4', '3'],
    );
    const changed = deriveOperationIdentity(
      'uid-release-binding',
      'SENDER_RELEASE',
      RELEASE_REQUEST_ID,
      [INSTALLATION_ID, '5', '3'],
    );
    expect(changed.requestKey).toBe(first.requestKey);
    expect(changed.requestFingerprint).not.toBe(first.requestFingerprint);
  });

  it('creates a bounded content-free completion receipt', () => {
    const receipt = makeCoordinationOperationReceipt(
      {
        schemaVersion: SCHEMA_VERSION,
        operation: 'SENDER_RELEASE',
        stage: 'RELEASE_PURGING',
        requestKey: 'a'.repeat(64),
        requestFingerprint: 'b'.repeat(64),
        accountKey: 'c'.repeat(64),
        androidStateExisted: true,
        senderEpochAfter: 5,
        resetGenerationAfter: 3,
        drainUntilMs: NOW_MS,
        nextSweepAtMs: NOW_MS,
        sweepAttemptCount: 0,
        createdAtMs: NOW_MS,
        updatedAtMs: NOW_MS,
      },
      NOW_MS + 1,
    );
    expect(receipt).toMatchObject({
      outcome: 'COMPLETED',
      androidSenderStateErased: true,
      firebaseAuthPreserved: true,
      cleanupAtMs: NOW_MS + 1 + 30 * DAY_MS,
    });
    expect(JSON.stringify(receipt)).not.toMatch(
      /email|phone|message|contact|token|providerSubject/iu,
    );
  });
});

describe('strict destructive-operation persistence codecs', () => {
  const common = {
    schemaVersion: SCHEMA_VERSION,
    requestKey: 'a'.repeat(64),
    requestFingerprint: 'b'.repeat(64),
    accountKey: 'c'.repeat(64),
    nextSweepAtMs: NOW_MS,
    sweepAttemptCount: 0,
    createdAtMs: NOW_MS,
    updatedAtMs: NOW_MS,
  } as const;

  it('rejects cross-operation stages and invalid drain field combinations', () => {
    expect(
      decodeCoordinationOperation({
        ...common,
        operation: 'SENDER_RELEASE',
        stage: 'RESET_DRAINING',
        androidStateExisted: true,
        senderEpochAfter: 5,
        resetGenerationAfter: 3,
        drainUntilMs: NOW_MS,
      }),
    ).toBeNull();
    expect(
      decodeCoordinationOperation({
        ...common,
        operation: 'SENDER_RELEASE',
        stage: 'RELEASE_DRAINING',
        androidStateExisted: true,
        senderEpochAfter: 5,
        resetGenerationAfter: 3,
      }),
    ).toBeNull();
    expect(
      decodeCoordinationOperation({
        ...common,
        operation: 'SENDER_RELEASE',
        stage: 'RELEASE_PURGING',
        androidStateExisted: true,
        senderEpochAfter: 5,
        resetGenerationAfter: 3,
        drainUntilMs: NOW_MS,
      }),
    ).toBeNull();
  });

  it('requires exact Android-reset evidence and forbids it for iOS-only receipts', () => {
    const receiptCommon = {
      schemaVersion: SCHEMA_VERSION,
      operation: 'CONTACT_DERIVED_RESET',
      outcome: 'COMPLETED',
      requestKey: 'a'.repeat(64),
      requestFingerprint: 'b'.repeat(64),
      accountKey: 'c'.repeat(64),
      contactDerivedStateErased: true,
      firebaseAuthPreserved: true,
      completedAtMs: NOW_MS,
      cleanupAtMs: NOW_MS + DAY_MS,
      cleanupAt: Timestamp.fromMillis(NOW_MS + DAY_MS),
    } as const;
    expect(
      decodeCoordinationOperationReceipt({
        ...receiptCommon,
        androidStateExisted: true,
      }),
    ).toBeNull();
    expect(
      decodeCoordinationOperationReceipt({
        ...receiptCommon,
        androidStateExisted: false,
        senderEpochAfter: 5,
        resetGenerationAfter: 4,
        birthdayAutomationNotBeforeMs: NOW_MS + DAY_MS,
      }),
    ).toBeNull();
    expect(
      decodeCoordinationOperationReceipt({
        ...receiptCommon,
        androidStateExisted: true,
        senderEpochAfter: 5,
        resetGenerationAfter: 4,
        birthdayAutomationNotBeforeMs: NOW_MS + DAY_MS,
      }),
    ).toMatchObject({
      androidStateExisted: true,
      senderEpochAfter: 5,
      resetGenerationAfter: 4,
    });
  });
});
