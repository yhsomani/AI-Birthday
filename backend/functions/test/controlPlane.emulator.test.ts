import { deleteApp, initializeApp } from 'firebase-admin/app';
import type { Auth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { SCHEMA_VERSION } from '../src/domain/model.js';
import { deriveDeletionReceiptKey } from '../src/domain/deletionReceipt.js';
import { deriveOperationIdentity } from '../src/domain/operationIdentity.js';
import { parseKeyRing } from '../src/domain/opaque.js';
import { ControlPlaneService } from '../src/services/controlPlane.js';
import { CoordinationOperationOrchestrator } from '../src/services/coordinationOperationOrchestrator.js';
import { DeletionOrchestrator } from '../src/services/deletionOrchestrator.js';
import { INSTALLATION_ID, NOW_MS, SECOND_INSTALLATION_ID } from './fixtures.js';

const projectId = 'demo-birthday-autopilot';
const app = initializeApp({ projectId }, 'control-plane-emulator-tests');
const db = getFirestore(app);
const keyRing = parseKeyRing({
  current: {
    version: 'test-v1',
    keyBase64: Buffer.alloc(32, 7).toString('base64'),
  },
});

const nowMs = NOW_MS;
const service = new ControlPlaneService(db, keyRing, () => nowMs);

async function seedGlobalControl(): Promise<void> {
  await db
    .collection('globalControl')
    .doc('current')
    .set({
      schemaVersion: SCHEMA_VERSION,
      armingEnabled: true,
      continuityState: 'HEALTHY',
      ledgerGeneration: 'ledger-generation-1',
      minimumBuildNumber: 100,
      minimumPolicyVersion: 7,
      allowedDistributionChannels: ['PLAY'],
      reasonCode: 'OK',
      updatedAtMs: nowMs,
    });
}

function registration(installationId: string) {
  return {
    contractVersion: 1 as const,
    ledgerGeneration: 'ledger-generation-1',
    installationId,
    appBuildNumber: 100,
    policyVersion: 7,
    distributionChannel: 'PLAY' as const,
  };
}

beforeAll(seedGlobalControl);

afterAll(async () => {
  await deleteApp(app);
});

describe('Firestore transaction adapter', () => {
  it('fences one active installation and makes a second installation standby', async () => {
    const uid = 'emulator-registration';
    const active = await service.registerAndroidInstallation(
      uid,
      registration(INSTALLATION_ID),
    );
    expect(active.kind).toBe('REGISTERED_ACTIVE');
    const standby = await service.registerAndroidInstallation(
      uid,
      registration(SECOND_INSTALLATION_ID),
    );
    expect(standby.kind).toBe('REGISTERED_STANDBY');
    const account = await db.collection('accounts').doc(uid).get();
    expect(account.get('activeInstallationId')).toBe(INSTALLATION_ID);
    expect(account.get('mode')).toBe('TEST_ONLY');
  });

  it('persists a TEST claim and immutable Arm outcome without Birthday guards', async () => {
    const uid = 'emulator-test-arm';
    const registered = await service.registerAndroidInstallation(
      uid,
      registration(INSTALLATION_ID),
    );
    expect(registered.kind).toBe('REGISTERED_ACTIVE');
    if (registered.kind !== 'REGISTERED_ACTIVE') {
      return;
    }
    const binding = {
      contractVersion: 1 as const,
      ledgerGeneration: 'ledger-generation-1',
      installationId: INSTALLATION_ID,
      senderEpoch: registered.fence.senderEpoch,
      resetGeneration: registered.fence.resetGeneration,
      appBuildNumber: 100,
      policyVersion: 7,
      distributionChannel: 'PLAY' as const,
    };
    const claimed = await service.claimTest(uid, {
      ...binding,
      purpose: 'TEST',
      testRequestId: '00000000-0000-4000-8000-000000000201',
      testConfigurationPrehash: '12'.repeat(32),
      testDestinationPrehash: '34'.repeat(32),
    });
    expect(claimed.kind).toBe('CLAIMED');
    if (claimed.kind !== 'CLAIMED') {
      return;
    }
    const request = {
      ...binding,
      purpose: 'TEST' as const,
      claimId: claimed.claim.claimId,
      armRequestId: '00000000-0000-4000-8000-000000000202',
      attempt: 1 as const,
    };
    const armed = await service.armAttempt(uid, request);
    expect(armed.kind).toBe('ARMED');
    const replay = await service.armAttempt(uid, request);
    expect(replay.kind).toBe('REPLAYED');
    if (armed.kind === 'ARMED' && replay.kind === 'REPLAYED') {
      expect(replay.outcome).toEqual(armed.outcome);
    }
    const reported = await service.reportTestOutcome(uid, {
      ...binding,
      purpose: 'TEST',
      testClaimId: claimed.claim.claimId,
      armRequestId: request.armRequestId,
      result: 'SENT_ALL_PARTS',
    });
    expect(reported.kind).toBe('RECORDED');
    if (reported.kind === 'RECORDED') {
      expect(reported.claim.testBarrierOutcome).toBe(
        'SENT_ALL_PARTS_IN_WINDOW',
      );
    }
    expect(
      await service.changeAccountMode(uid, {
        ...binding,
        action: 'ACTIVATE_AUTOMATION',
        testClaimId: claimed.claim.claimId,
        boundTestReceiptPrehash: '56'.repeat(32),
        readinessContractVersion: 1,
      }),
    ).toEqual({ kind: 'CHANGED', mode: 'AUTOMATION_ACTIVE' });
    expect(
      (
        await db
          .collection('accounts')
          .doc(uid)
          .collection('destinationGuards')
          .get()
      ).empty,
    ).toBe(true);
  });

  it('covers both serialized outcomes of iOS deletion versus first registration', async () => {
    const deletionFirstUid = 'emulator-deletion-first';
    await service.beginDeletion(deletionFirstUid, {
      contractVersion: 1,
      requestId: '00000000-0000-4000-8000-000000000301',
    });
    expect(
      await service.registerAndroidInstallation(
        deletionFirstUid,
        registration(INSTALLATION_ID),
      ),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' });

    const registrationFirstUid = 'emulator-registration-first';
    await service.registerAndroidInstallation(
      registrationFirstUid,
      registration(INSTALLATION_ID),
    );
    await service.beginDeletion(registrationFirstUid, {
      contractVersion: 1,
      requestId: '00000000-0000-4000-8000-000000000302',
    });
    const account = await db
      .collection('accounts')
      .doc(registrationFirstUid)
      .get();
    expect(account.get('mode')).toBe('DELETING');
    const laterRegistration = await service.registerAndroidInstallation(
      registrationFirstUid,
      registration(SECOND_INSTALLATION_ID),
    );
    expect(laterRegistration).toEqual({
      kind: 'SUPPRESSED',
      reason: 'DELETION_SUPPRESSED',
    });
  });

  it('keeps a signed-out bearer receipt unlinkable and completes it only with the deletion saga', async () => {
    const uid = 'emulator-deletion-public-receipt';
    const requestId = '00000000-0000-4000-8000-000000001102';
    const replacementRequestId = '00000000-0000-4000-8000-000000001103';
    const requestedAtMs = nowMs - 10 * 24 * 60 * 60_000;
    let receiptNowMs = requestedAtMs;
    const receiptService = new ControlPlaneService(
      db,
      keyRing,
      () => receiptNowMs,
    );
    expect(
      await receiptService.accountDeletionReceipt({
        contractVersion: 1,
        receiptId: requestId,
      }),
    ).toEqual({ kind: 'NOT_FOUND' });

    const started = await receiptService.beginDeletion(uid, {
      contractVersion: 1,
      requestId,
    });
    expect(started).toMatchObject({
      kind: 'STARTED',
      tombstone: { requestKey: deriveDeletionReceiptKey(requestId) },
    });
    const storedTombstone = await db
      .collection('deletionTombstones')
      .doc(uid)
      .get();
    expect(storedTombstone.get('requestKey')).toBe(
      deriveDeletionReceiptKey(requestId),
    );
    expect(storedTombstone.data()).not.toHaveProperty('requestId');
    expect(
      await receiptService.accountDeletionReceipt({
        contractVersion: 1,
        receiptId: requestId,
      }),
    ).toEqual({
      kind: 'IN_PROGRESS',
      requestedAtMs: receiptNowMs,
      updatedAtMs: receiptNowMs,
    });

    const replay = await receiptService.beginDeletion(uid, {
      contractVersion: 1,
      requestId: replacementRequestId,
    });
    expect(replay).toEqual({ kind: 'REFUSED', reason: 'REQUEST_MISMATCH' });
    expect(
      await receiptService.accountDeletionReceipt({
        contractVersion: 1,
        receiptId: replacementRequestId,
      }),
    ).toEqual({ kind: 'NOT_FOUND' });

    const receiptKey = deriveDeletionReceiptKey(requestId);
    const stored = await db
      .collection('deletionReceipts')
      .doc(receiptKey)
      .get();
    expect(stored.exists).toBe(true);
    expect(stored.id).toMatch(/^[a-f0-9]{64}$/u);
    expect(stored.id).not.toContain(requestId);
    expect(JSON.stringify(stored.data())).not.toContain(requestId);
    expect(stored.data()).not.toHaveProperty('uid');
    expect(stored.data()).not.toHaveProperty('email');

    const userNotFoundError = Object.assign(new Error('USER_NOT_FOUND'), {
      code: 'auth/user-not-found',
    });
    const fakeAuth = {
      deleteUser: (): Promise<void> => Promise.resolve(),
      getUser: (): Promise<never> => Promise.reject(userNotFoundError),
    } as unknown as Auth;
    const orchestrator = new DeletionOrchestrator(
      db,
      fakeAuth,
      () => receiptNowMs,
    );
    receiptNowMs += 1;
    expect(await orchestrator.sweep(20)).toMatchObject({ drainsAdvanced: 1 });
    expect(
      await receiptService.accountDeletionReceipt({
        contractVersion: 1,
        receiptId: requestId,
      }),
    ).toMatchObject({ kind: 'IN_PROGRESS' });
    expect(await orchestrator.sweep(20)).toMatchObject({
      authDeletionsVerified: 1,
    });
    receiptNowMs += 24 * 60 * 60_000 + 1;
    expect(await orchestrator.sweep(20)).toMatchObject({
      tombstonesFinalized: 1,
    });

    expect(
      (await db.collection('deletionTombstones').doc(uid).get()).exists,
    ).toBe(false);
    expect(
      await receiptService.accountDeletionReceipt({
        contractVersion: 1,
        receiptId: requestId,
      }),
    ).toEqual({
      kind: 'COMPLETED',
      requestedAtMs,
      completedAtMs: receiptNowMs,
      appAccountDeleted: true,
      serverDataDeleted: true,
      externalCopiesNotDeleted: true,
    });
    const completed = await db
      .collection('deletionReceipts')
      .doc(receiptKey)
      .get();
    expect(completed.get('cleanupAtMs')).toBe(
      receiptNowMs + 365 * 24 * 60 * 60_000,
    );
    expect(completed.get('cleanupAt')).toBeDefined();
  });

  it('keeps account-global iOS status fail-closed through Android and deletion state', async () => {
    const uid = 'emulator-companion';
    expect(
      await service.companionStatus(uid, {
        contractVersion: 1,
        ledgerGeneration: 'ledger-generation-1',
      }),
    ).toMatchObject({ composerAllowed: true, state: 'NO_ANDROID_STATE' });
    await service.registerAndroidInstallation(
      uid,
      registration(INSTALLATION_ID),
    );
    expect(
      await service.companionStatus(uid, {
        contractVersion: 1,
        ledgerGeneration: 'ledger-generation-1',
      }),
    ).toMatchObject({ composerAllowed: false, state: 'MANAGED_BY_ANDROID' });
    await service.beginDeletion(uid, {
      contractVersion: 1,
      requestId: '00000000-0000-4000-8000-000000000401',
    });
    expect(
      await service.companionStatus(uid, {
        contractVersion: 1,
        ledgerGeneration: 'ledger-generation-1',
      }),
    ).toMatchObject({ composerAllowed: false, state: 'DELETING' });
  });

  it('atomically fences Android behind an exact sticky iOS composer reservation', async () => {
    const uid = 'emulator-ios-composer-reservation';
    const request = {
      contractVersion: 1 as const,
      ledgerGeneration: 'ledger-generation-1',
      reservationId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaa801',
    };
    const reserved = await service.acquireIOSComposerReservation(uid, request);
    expect(reserved).toMatchObject({
      kind: 'RESERVED',
      earlyReleaseAllowed: true,
      serverNowMs: nowMs,
    });
    expect(Object.keys(reserved).sort()).toEqual([
      'earlyReleaseAllowed',
      'kind',
      'reservationExpiresAtMs',
      'serverNowMs',
    ]);
    const stored = await db
      .collection('iosComposerReservations')
      .doc(uid)
      .get();
    expect(stored.exists).toBe(true);
    expect(Object.keys(stored.data() ?? {}).sort()).toEqual([
      'cleanupAt',
      'cleanupAtMs',
      'createdAtMs',
      'expiresAtMs',
      'ledgerGeneration',
      'phase',
      'reservationKey',
      'schemaVersion',
      'updatedAtMs',
    ]);
    expect(
      await service.registerAndroidInstallation(
        uid,
        registration(INSTALLATION_ID),
      ),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'IOS_COMPOSER_RESERVED' });
    expect(
      await service.acquireIOSComposerReservation(uid, {
        ...request,
        reservationId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbb802',
      }),
    ).toMatchObject({ kind: 'REFUSED', reason: 'RESERVATION_HELD' });

    expect(
      await service.commitIOSComposerReservation(uid, request),
    ).toMatchObject({ kind: 'COMMITTED' });
    expect(
      await service.releaseIOSComposerReservation(uid, {
        contractVersion: 1,
        reservationId: request.reservationId,
      }),
    ).toMatchObject({ kind: 'REFUSED', reason: 'STICKY_UNTIL_EXPIRY' });

    expect(
      await service.beginDeletion(uid, {
        contractVersion: 1,
        requestId: 'cccccccc-cccc-4ccc-8ccc-ccccccccc803',
      }),
    ).toMatchObject({ kind: 'STARTED' });
    expect(
      (await db.collection('iosComposerReservations').doc(uid).get()).exists,
    ).toBe(false);
  });

  it('serializes both composer-versus-registration orders and authorizes after logical expiry', async () => {
    const androidFirstUid = 'emulator-ios-composer-android-first';
    expect(
      await service.registerAndroidInstallation(
        androidFirstUid,
        registration(INSTALLATION_ID),
      ),
    ).toMatchObject({ kind: 'REGISTERED_ACTIVE' });
    expect(
      await service.acquireIOSComposerReservation(androidFirstUid, {
        contractVersion: 1,
        ledgerGeneration: 'ledger-generation-1',
        reservationId: 'dddddddd-dddd-4ddd-8ddd-ddddddddd804',
      }),
    ).toMatchObject({ kind: 'REFUSED', reason: 'MANAGED_BY_ANDROID' });

    const expiryUid = 'emulator-ios-composer-expiry';
    await service.acquireIOSComposerReservation(expiryUid, {
      contractVersion: 1,
      ledgerGeneration: 'ledger-generation-1',
      reservationId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeee805',
    });
    const afterExpiry = new ControlPlaneService(
      db,
      keyRing,
      () => nowMs + 72 * 60 * 60_000 + 1,
    );
    expect(
      await afterExpiry.registerAndroidInstallation(
        expiryUid,
        registration(INSTALLATION_ID),
      ),
    ).toMatchObject({ kind: 'REGISTERED_ACTIVE' });
  });

  it('blocks every Android sender mutation while the iOS reservation is live', async () => {
    const uid = 'emulator-ios-composer-all-mutations';
    await service.acquireIOSComposerReservation(uid, {
      contractVersion: 1,
      ledgerGeneration: 'ledger-generation-1',
      reservationId: 'ffffffff-ffff-4fff-8fff-fffffffff806',
    });
    const bound = {
      contractVersion: 1 as const,
      ledgerGeneration: 'ledger-generation-1',
      installationId: INSTALLATION_ID,
      senderEpoch: 1,
      resetGeneration: 1,
      appBuildNumber: 100,
      policyVersion: 7,
      distributionChannel: 'PLAY' as const,
    };
    expect(
      await service.renewLease(uid, { ...bound, purpose: 'TEST' }),
    ).toEqual({ kind: 'REFUSED', reason: 'IOS_COMPOSER_RESERVED' });
    expect(
      await service.changeAccountMode(uid, {
        ...bound,
        action: 'PAUSE_FOR_REPAIR',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'IOS_COMPOSER_RESERVED' });
    expect(
      await service.claimTest(uid, {
        ...bound,
        purpose: 'TEST',
        testRequestId: '00000000-0000-4000-8000-000000000807',
        testConfigurationPrehash: '12'.repeat(32),
        testDestinationPrehash: '34'.repeat(32),
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'IOS_COMPOSER_RESERVED' });
    const arm = {
      ...bound,
      purpose: 'TEST' as const,
      claimId: 'v1.missing-test',
      armRequestId: '00000000-0000-4000-8000-000000000808',
      attempt: 1 as const,
    };
    expect(await service.armAttempt(uid, arm)).toEqual({
      kind: 'SUPPRESSED',
      reason: 'IOS_COMPOSER_RESERVED',
    });
    expect(await service.getArmStatus(uid, arm)).toEqual({
      kind: 'SUPPRESSED',
      reason: 'IOS_COMPOSER_RESERVED',
    });
    expect(
      await service.authorizeSafeRetry(uid, {
        ...bound,
        purpose: 'BIRTHDAY',
        claimId: 'v1.missing-birthday',
        retryRequestId: '00000000-0000-4000-8000-000000000809',
        proof: 'ALL_PARTS_NO_SERVICE',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'IOS_COMPOSER_RESERVED' });
    expect(
      await service.reportTestOutcome(uid, {
        ...bound,
        purpose: 'TEST',
        testClaimId: 'v1.missing-test',
        armRequestId: arm.armRequestId,
        result: 'CLEANUP_CANCELLED',
      }),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'IOS_COMPOSER_RESERVED' });
    const transfer = {
      ...bound,
      targetInstallationId: SECOND_INSTALLATION_ID,
    };
    expect(await service.beginTransfer(uid, transfer)).toEqual({
      kind: 'REFUSED',
      reason: 'IOS_COMPOSER_RESERVED',
    });
    expect(await service.completeTransfer(uid, transfer)).toEqual({
      kind: 'REFUSED',
      reason: 'IOS_COMPOSER_RESERVED',
    });
    expect(
      await service.requestContactDerivedReset(uid, {
        contractVersion: 1,
        requestId: '11111111-1111-4111-8111-111111111810',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'IOS_COMPOSER_RESERVED' });
    expect(
      await service.requestSenderRelease(uid, {
        contractVersion: 1,
        requestId: '22222222-2222-4222-8222-222222222811',
        installationId: INSTALLATION_ID,
        senderEpoch: 1,
        resetGeneration: 1,
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'IOS_COMPOSER_RESERVED' });
  });

  it('lets exactly one platform win a concurrent first-registration race', async () => {
    const uid = 'emulator-ios-composer-concurrent-registration';
    const [composer, android] = await Promise.all([
      service.acquireIOSComposerReservation(uid, {
        contractVersion: 1,
        ledgerGeneration: 'ledger-generation-1',
        reservationId: '33333333-3333-4333-8333-333333333812',
      }),
      service.registerAndroidInstallation(uid, registration(INSTALLATION_ID)),
    ]);
    const composerWon = composer.kind === 'RESERVED';
    const androidWon = android.kind === 'REGISTERED_ACTIVE';
    expect(Number(composerWon) + Number(androidWon)).toBe(1);
    if (composerWon) {
      expect(android).toEqual({
        kind: 'SUPPRESSED',
        reason: 'IOS_COMPOSER_RESERVED',
      });
    } else {
      expect(composer).toMatchObject({
        kind: 'REFUSED',
        reason: 'MANAGED_BY_ANDROID',
      });
    }
  });

  it('refuses destructive coordination on orphaned or stale-ledger presence', async () => {
    const orphanedUid = 'emulator-reset-orphaned-presence';
    await db.collection('coordinationPresence').doc(orphanedUid).set({
      schemaVersion: SCHEMA_VERSION,
      state: 'ANDROID_STATE',
      ledgerGeneration: 'ledger-generation-1',
      updatedAtMs: nowMs,
    });
    expect(
      await service.requestContactDerivedReset(orphanedUid, {
        contractVersion: 1,
        requestId: '00000000-0000-4000-8000-000000000751',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' });
    expect(
      (
        await db
          .collection('coordinationOperationFences')
          .doc(orphanedUid)
          .get()
      ).exists,
    ).toBe(false);

    const staleUid = 'emulator-reset-stale-ledger';
    const registered = await service.registerAndroidInstallation(
      staleUid,
      registration(INSTALLATION_ID),
    );
    expect(registered.kind).toBe('REGISTERED_ACTIVE');
    if (registered.kind !== 'REGISTERED_ACTIVE') {
      return;
    }
    await db.collection('coordinationPresence').doc(staleUid).update({
      ledgerGeneration: 'stale-ledger-generation',
    });
    expect(
      await service.requestContactDerivedReset(staleUid, {
        contractVersion: 1,
        requestId: '00000000-0000-4000-8000-000000000752',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' });
    expect(
      await service.requestSenderRelease(staleUid, {
        contractVersion: 1,
        requestId: '00000000-0000-4000-8000-000000000753',
        installationId: INSTALLATION_ID,
        senderEpoch: registered.fence.senderEpoch,
        resetGeneration: registered.fence.resetGeneration,
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'CONTINUITY_UNAVAILABLE' });
  });

  it('cleans an iOS-only reset without creating Android sender state', async () => {
    const uid = 'emulator-ios-only-contact-reset';
    const completed = await service.requestContactDerivedReset(uid, {
      contractVersion: 1,
      requestId: '00000000-0000-4000-8000-000000000754',
    });
    expect(completed).toMatchObject({
      kind: 'COMPLETED',
      operation: 'CONTACT_DERIVED_RESET',
      androidStateExisted: false,
      contactDerivedStateErased: true,
      firebaseAuthPreserved: true,
    });
    expect(completed).not.toHaveProperty('senderEpochAfter');
    expect(completed).not.toHaveProperty('resetGenerationAfter');
    expect(completed).not.toHaveProperty('birthdayAutomationNotBeforeMs');
    expect((await db.collection('accounts').doc(uid).get()).exists).toBe(false);
    expect(
      (await db.collection('coordinationPresence').doc(uid).get()).exists,
    ).toBe(false);
  });

  it('resets contact-derived state without resetting budgets or TEST evidence', async () => {
    const uid = 'emulator-contact-derived-reset';
    let resetNowMs = nowMs;
    const resetService = new ControlPlaneService(db, keyRing, () => resetNowMs);
    const registered = await resetService.registerAndroidInstallation(
      uid,
      registration(INSTALLATION_ID),
    );
    expect(registered.kind).toBe('REGISTERED_ACTIVE');
    if (registered.kind !== 'REGISTERED_ACTIVE') {
      return;
    }
    const account = db.collection('accounts').doc(uid);
    const birthdayClaimId = '00000000-0000-4000-8000-000000000801';
    const testClaimId = '00000000-0000-4000-8000-000000000802';
    const birthdayClaim = {
      schemaVersion: SCHEMA_VERSION,
      claimId: birthdayClaimId,
      purpose: 'BIRTHDAY',
      claimRequestId: birthdayClaimId,
      ownerInstallationId: INSTALLATION_ID,
      ownerEpoch: 1,
      resetGeneration: 1,
      state: 'ARMED',
      attempt: 1,
      retryAuthorizationGeneration: 0,
      claimExpiresAtMs: nowMs + 60_000,
      maxPossibleSubmitNotAfterMs: nowMs + 120_000,
      serverSubmitNotAfterMs: nowMs + 60_000,
      occurrenceAliasKeys: ['v1.occurrence-reset'],
      destinationAliasKeys: ['v1.destination-reset'],
      testMaterialAliasKeys: [],
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
      cleanupAtMs: nowMs + 400 * 24 * 60 * 60_000,
    } as const;
    const testClaim = {
      ...birthdayClaim,
      claimId: testClaimId,
      purpose: 'TEST',
      claimRequestId: testClaimId,
      state: 'TERMINAL',
      testBarrierOutcome: 'SENT_ALL_PARTS_IN_WINDOW',
      occurrenceAliasKeys: [],
      destinationAliasKeys: [],
      testMaterialAliasKeys: ['v1.test-reset'],
    } as const;
    await Promise.all([
      account.update({ latestIssuedSubmitNotAfterMs: nowMs + 60_000 }),
      account
        .collection('occurrenceClaims')
        .doc(birthdayClaimId)
        .set(birthdayClaim),
      account.collection('testClaims').doc(testClaimId).set(testClaim),
      account.collection('occurrenceKeys').doc('v1.occurrence-reset').set({
        schemaVersion: SCHEMA_VERSION,
        aliasKey: 'v1.occurrence-reset',
        linkedClaimId: birthdayClaimId,
        state: 'ARMED',
        createdAtMs: nowMs,
        updatedAtMs: nowMs,
        cleanupAtMs: birthdayClaim.cleanupAtMs,
      }),
      account.collection('destinationGuards').doc('v1.destination-reset').set({
        schemaVersion: SCHEMA_VERSION,
        aliasKey: 'v1.destination-reset',
        linkedClaimId: birthdayClaimId,
        ownerEpoch: 1,
        state: 'ARMED',
        createdAtMs: nowMs,
        updatedAtMs: nowMs,
        cleanupAtMs: birthdayClaim.cleanupAtMs,
      }),
      account.collection('claimRequests').doc(birthdayClaimId).set({
        schemaVersion: SCHEMA_VERSION,
        requestKey: birthdayClaimId,
        purpose: 'BIRTHDAY',
        linkedClaimId: birthdayClaimId,
        createdAtMs: nowMs,
        cleanupAtMs: birthdayClaim.cleanupAtMs,
      }),
      account.collection('claimRequests').doc(testClaimId).set({
        schemaVersion: SCHEMA_VERSION,
        requestKey: testClaimId,
        purpose: 'TEST',
        linkedClaimId: testClaimId,
        createdAtMs: nowMs,
        cleanupAtMs: birthdayClaim.cleanupAtMs,
      }),
      account
        .collection('armOutcomes')
        .doc(birthdayClaimId)
        .set({
          schemaVersion: SCHEMA_VERSION,
          armRequestId: birthdayClaimId,
          purpose: 'BIRTHDAY',
          claimId: birthdayClaimId,
          ownerInstallationId: INSTALLATION_ID,
          ownerEpoch: 1,
          resetGeneration: 1,
          attempt: 1,
          kind: 'ARMED',
          serverSubmitNotAfterMs: nowMs + 60_000,
          resolvedAtMs: nowMs,
          cleanupAtMs: birthdayClaim.cleanupAtMs,
        }),
      account
        .collection('armOutcomes')
        .doc(testClaimId)
        .set({
          schemaVersion: SCHEMA_VERSION,
          armRequestId: testClaimId,
          purpose: 'TEST',
          claimId: testClaimId,
          ownerInstallationId: INSTALLATION_ID,
          ownerEpoch: 1,
          resetGeneration: 1,
          attempt: 1,
          kind: 'ARMED',
          serverSubmitNotAfterMs: nowMs + 60_000,
          resolvedAtMs: nowMs,
          cleanupAtMs: birthdayClaim.cleanupAtMs,
        }),
      account
        .collection('armBudgets')
        .doc('birthday')
        .set({
          schemaVersion: SCHEMA_VERSION,
          purpose: 'BIRTHDAY',
          entries: [{ id: birthdayClaimId, armedAtMs: nowMs }],
          newestEntryAtMs: nowMs,
          cleanupAtMs: nowMs + 24 * 60 * 60_000,
        }),
      account
        .collection('armBudgets')
        .doc('test')
        .set({
          schemaVersion: SCHEMA_VERSION,
          purpose: 'TEST',
          entries: [{ id: testClaimId, armedAtMs: nowMs }],
          newestEntryAtMs: nowMs,
          cleanupAtMs: nowMs + 24 * 60 * 60_000,
        }),
    ]);

    const request = {
      contractVersion: 1 as const,
      requestId: '00000000-0000-4000-8000-000000000803',
    };
    const draining = await resetService.requestContactDerivedReset(
      uid,
      request,
    );
    expect(draining).toMatchObject({
      kind: 'IN_PROGRESS',
      operation: 'CONTACT_DERIVED_RESET',
      stage: 'RESET_DRAINING',
      androidStateExisted: true,
      senderEpochAfter: 2,
      resetGenerationAfter: 2,
      birthdayAutomationNotBeforeMs: nowMs + 24 * 60 * 60_000,
      drainUntilMs: nowMs + 60_000,
    });
    expect(
      await resetService.coordinationLifecycleStatus(uid, {
        contractVersion: 1,
      }),
    ).toMatchObject({
      kind: 'OPERATION_IN_PROGRESS',
      operation: 'CONTACT_DERIVED_RESET',
      stage: 'RESET_DRAINING',
      senderEpochAfter: 2,
      resetGenerationAfter: 2,
    });
    const [fenceAfter, installationAfter] = await Promise.all([
      account.get(),
      account.collection('installations').doc(INSTALLATION_ID).get(),
    ]);
    expect(fenceAfter.data()).toMatchObject({
      mode: 'PAUSED_REPAIR',
      senderEpoch: 2,
      resetGeneration: 2,
      birthdayAutomationNotBeforeMs: nowMs + 24 * 60 * 60_000,
    });
    expect(installationAfter.data()).toMatchObject({
      state: 'ACTIVE',
      epoch: 2,
    });
    expect(
      (await account.collection('occurrenceClaims').doc(birthdayClaimId).get())
        .exists,
    ).toBe(true);
    resetNowMs = nowMs + 60_000;
    expect(await resetService.requestContactDerivedReset(uid, request)).toEqual(
      draining,
    );
    resetNowMs += 1;
    const reset = await resetService.requestContactDerivedReset(uid, request);
    expect(reset).toMatchObject({
      kind: 'COMPLETED',
      operation: 'CONTACT_DERIVED_RESET',
      contactDerivedStateErased: true,
      firebaseAuthPreserved: true,
    });
    expect(
      await resetService.coordinationLifecycleStatus(uid, {
        contractVersion: 1,
      }),
    ).toMatchObject({
      kind: 'ANDROID_STATE',
      mode: 'PAUSED_REPAIR',
      activeInstallationId: INSTALLATION_ID,
      senderEpoch: 2,
      resetGeneration: 2,
      latestCompletion: {
        kind: 'COMPLETED',
        operation: 'CONTACT_DERIVED_RESET',
        contactDerivedStateErased: true,
      },
    });
    for (const collection of [
      'occurrenceClaims',
      'occurrenceKeys',
      'destinationGuards',
    ]) {
      expect((await account.collection(collection).get()).empty).toBe(true);
    }
    expect(
      (
        await account
          .collection('claimRequests')
          .where('purpose', '==', 'BIRTHDAY')
          .get()
      ).empty,
    ).toBe(true);
    expect(
      (
        await account
          .collection('armOutcomes')
          .where('purpose', '==', 'BIRTHDAY')
          .get()
      ).empty,
    ).toBe(true);
    expect(
      (await account.collection('testClaims').doc(testClaimId).get()).exists,
    ).toBe(true);
    expect(
      (await account.collection('claimRequests').doc(testClaimId).get()).exists,
    ).toBe(true);
    expect(
      (await account.collection('armOutcomes').doc(testClaimId).get()).exists,
    ).toBe(true);
    expect((await account.collection('armBudgets').get()).size).toBe(2);
    expect(await resetService.requestContactDerivedReset(uid, request)).toEqual(
      reset,
    );
    expect(
      await resetService.renewLease(uid, {
        ...registration(INSTALLATION_ID),
        purpose: 'TEST',
        senderEpoch: 1,
        resetGeneration: 1,
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'BINDING_MISMATCH' });
  });

  it('keeps sender release fenced through its strict drain and replays completion', async () => {
    const uid = 'emulator-sender-release';
    let releaseNowMs = nowMs;
    const releaseService = new ControlPlaneService(
      db,
      keyRing,
      () => releaseNowMs,
    );
    const registered = await releaseService.registerAndroidInstallation(
      uid,
      registration(INSTALLATION_ID),
    );
    expect(registered.kind).toBe('REGISTERED_ACTIVE');
    if (registered.kind !== 'REGISTERED_ACTIVE') {
      return;
    }
    expect(
      (
        await releaseService.registerAndroidInstallation(
          uid,
          registration(SECOND_INSTALLATION_ID),
        )
      ).kind,
    ).toBe('REGISTERED_STANDBY');
    const request = {
      contractVersion: 1 as const,
      requestId: '00000000-0000-4000-8000-000000000901',
      installationId: INSTALLATION_ID,
      senderEpoch: registered.fence.senderEpoch,
      resetGeneration: registered.fence.resetGeneration,
    };
    const draining = await releaseService.requestSenderRelease(uid, request);
    expect(draining).toMatchObject({
      kind: 'IN_PROGRESS',
      operation: 'SENDER_RELEASE',
      stage: 'RELEASE_DRAINING',
      drainUntilMs: releaseNowMs,
    });
    expect(
      await releaseService.registerAndroidInstallation(
        uid,
        registration(SECOND_INSTALLATION_ID),
      ),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'RESET_SUPPRESSED' });
    expect(
      await releaseService.beginDeletion(uid, {
        contractVersion: 1,
        requestId: '00000000-0000-4000-8000-000000000902',
      }),
    ).toEqual({
      kind: 'REFUSED',
      reason: 'COORDINATION_OPERATION_IN_PROGRESS',
    });
    const fakeArm = {
      ...registration(INSTALLATION_ID),
      purpose: 'TEST' as const,
      senderEpoch: request.senderEpoch,
      resetGeneration: request.resetGeneration,
      claimId: 'v1.missing-claim',
      armRequestId: '00000000-0000-4000-8000-000000000903',
      attempt: 1 as const,
    };
    expect(await releaseService.armAttempt(uid, fakeArm)).toEqual({
      kind: 'SUPPRESSED',
      reason: 'RESET_SUPPRESSED',
    });
    expect(await releaseService.getArmStatus(uid, fakeArm)).toEqual({
      kind: 'SUPPRESSED',
      reason: 'RESET_SUPPRESSED',
    });
    const boundRequest = {
      ...registration(INSTALLATION_ID),
      senderEpoch: request.senderEpoch,
      resetGeneration: request.resetGeneration,
    };
    expect(
      await releaseService.renewLease(uid, {
        ...boundRequest,
        purpose: 'TEST',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'RESET_SUPPRESSED' });
    expect(
      await releaseService.changeAccountMode(uid, {
        ...boundRequest,
        action: 'PAUSE_FOR_REPAIR',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'RESET_SUPPRESSED' });
    expect(
      await releaseService.claimTest(uid, {
        ...boundRequest,
        purpose: 'TEST',
        testRequestId: '00000000-0000-4000-8000-000000000904',
        testConfigurationPrehash: '12'.repeat(32),
        testDestinationPrehash: '34'.repeat(32),
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'RESET_SUPPRESSED' });
    expect(
      await releaseService.claimBirthdayOccurrence(uid, {
        ...boundRequest,
        purpose: 'BIRTHDAY',
        claimRequestId: '00000000-0000-4000-8000-000000000905',
        recipientPrehashAliases: ['56'.repeat(32)],
        destinationPrehashAliases: ['78'.repeat(32)],
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'RESET_SUPPRESSED' });
    expect(
      await releaseService.reportTestOutcome(uid, {
        ...boundRequest,
        purpose: 'TEST',
        testClaimId: 'v1.missing-test',
        armRequestId: fakeArm.armRequestId,
        result: 'CLEANUP_CANCELLED',
      }),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'RESET_SUPPRESSED' });
    expect(
      await releaseService.authorizeSafeRetry(uid, {
        ...boundRequest,
        purpose: 'BIRTHDAY',
        claimId: 'v1.missing-birthday',
        retryRequestId: '00000000-0000-4000-8000-000000000906',
        proof: 'ALL_PARTS_NO_SERVICE',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'RESET_SUPPRESSED' });
    const transfer = {
      ...boundRequest,
      targetInstallationId: SECOND_INSTALLATION_ID,
    };
    expect(await releaseService.beginTransfer(uid, transfer)).toEqual({
      kind: 'REFUSED',
      reason: 'RESET_SUPPRESSED',
    });
    expect(await releaseService.completeTransfer(uid, transfer)).toEqual({
      kind: 'REFUSED',
      reason: 'RESET_SUPPRESSED',
    });
    expect(
      await releaseService.companionStatus(uid, {
        contractVersion: 1,
        ledgerGeneration: 'ledger-generation-1',
      }),
    ).toMatchObject({
      composerAllowed: false,
      state: 'SAFETY_STATUS_UNAVAILABLE',
    });
    expect(
      (await db.collection('accounts').doc(uid).collection('armOutcomes').get())
        .empty,
    ).toBe(true);
    expect(
      (
        await db
          .collection('accounts')
          .doc(uid)
          .collection('claimRequests')
          .get()
      ).empty,
    ).toBe(true);

    releaseNowMs += 1;
    const completed = await releaseService.requestSenderRelease(uid, request);
    expect(completed).toMatchObject({
      kind: 'COMPLETED',
      operation: 'SENDER_RELEASE',
      senderEpochAfter: request.senderEpoch + 1,
      androidSenderStateErased: true,
      firebaseAuthPreserved: true,
    });
    expect(
      await releaseService.coordinationLifecycleStatus(uid, {
        contractVersion: 1,
      }),
    ).toMatchObject({
      kind: 'NO_ANDROID_STATE',
      latestCompletion: {
        kind: 'COMPLETED',
        operation: 'SENDER_RELEASE',
        androidSenderStateErased: true,
      },
    });
    expect((await db.collection('accounts').doc(uid).get()).exists).toBe(false);
    expect(
      (await db.collection('coordinationPresence').doc(uid).get()).exists,
    ).toBe(false);

    const composerReservation = {
      contractVersion: 1 as const,
      ledgerGeneration: 'ledger-generation-1',
      reservationId: '99999999-9999-4999-8999-999999999907',
    };
    expect(
      await releaseService.acquireIOSComposerReservation(
        uid,
        composerReservation,
      ),
    ).toMatchObject({ kind: 'RESERVED', earlyReleaseAllowed: true });
    expect(await releaseService.requestSenderRelease(uid, request)).toEqual(
      completed,
    );
    expect(
      await releaseService.requestSenderRelease(uid, {
        ...request,
        requestId: '99999999-9999-4999-8999-999999999908',
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'IOS_COMPOSER_RESERVED' });
    expect(
      await releaseService.requestSenderRelease(uid, {
        ...request,
        senderEpoch: request.senderEpoch + 1,
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'REQUEST_MISMATCH' });
    expect(
      await releaseService.releaseIOSComposerReservation(uid, {
        contractVersion: 1,
        reservationId: composerReservation.reservationId,
      }),
    ).toMatchObject({ kind: 'RELEASED' });

    const replacement = await releaseService.registerAndroidInstallation(
      uid,
      registration(SECOND_INSTALLATION_ID),
    );
    expect(replacement.kind).toBe('REGISTERED_ACTIVE');
    expect(
      await releaseService.coordinationLifecycleStatus(uid, {
        contractVersion: 1,
      }),
    ).toMatchObject({
      kind: 'ANDROID_STATE',
      activeInstallationId: SECOND_INSTALLATION_ID,
      latestCompletion: { operation: 'SENDER_RELEASE' },
    });
    expect(await releaseService.requestSenderRelease(uid, request)).toEqual(
      completed,
    );
    expect((await db.collection('accounts').doc(uid).get()).exists).toBe(true);
    expect(
      await releaseService.requestSenderRelease(uid, {
        ...request,
        senderEpoch: request.senderEpoch + 1,
      }),
    ).toEqual({ kind: 'REFUSED', reason: 'REQUEST_MISMATCH' });

    const identity = deriveOperationIdentity(
      uid,
      'SENDER_RELEASE',
      request.requestId,
      [
        request.installationId,
        String(request.senderEpoch),
        String(request.resetGeneration),
      ],
    );
    const receipt = await db
      .collection('coordinationOperationReceipts')
      .doc(identity.requestKey)
      .get();
    expect(receipt.data()).toMatchObject({
      outcome: 'COMPLETED',
      androidSenderStateErased: true,
      firebaseAuthPreserved: true,
    });
  });

  it('never replays a malformed destructive-operation receipt as success', async () => {
    const uid = 'emulator-malformed-reset-receipt';
    const request = {
      contractVersion: 1 as const,
      requestId: '00000000-0000-4000-8000-000000000971',
    };
    const identity = deriveOperationIdentity(
      uid,
      'CONTACT_DERIVED_RESET',
      request.requestId,
    );
    await db
      .collection('coordinationOperationReceipts')
      .doc(identity.requestKey)
      .set({
        schemaVersion: SCHEMA_VERSION,
        operation: 'CONTACT_DERIVED_RESET',
        outcome: 'COMPLETED',
        requestKey: identity.requestKey,
        requestFingerprint: identity.requestFingerprint,
        accountKey: identity.accountKey,
        androidStateExisted: true,
        contactDerivedStateErased: true,
        firebaseAuthPreserved: true,
        completedAtMs: nowMs,
        cleanupAtMs: nowMs + 60_000,
      });
    await expect(
      service.requestContactDerivedReset(uid, request),
    ).rejects.toThrow('LEDGER_CORRUPT');
  });

  it('reconciles transfer state without a local operation UUID', async () => {
    const uid = 'emulator-lifecycle-transfer-status';
    let transferNowMs = nowMs;
    const transferService = new ControlPlaneService(
      db,
      keyRing,
      () => transferNowMs,
    );
    const active = await transferService.registerAndroidInstallation(
      uid,
      registration(INSTALLATION_ID),
    );
    expect(active.kind).toBe('REGISTERED_ACTIVE');
    if (active.kind !== 'REGISTERED_ACTIVE') {
      return;
    }
    expect(
      (
        await transferService.registerAndroidInstallation(
          uid,
          registration(SECOND_INSTALLATION_ID),
        )
      ).kind,
    ).toBe('REGISTERED_STANDBY');
    const request = {
      ...registration(INSTALLATION_ID),
      senderEpoch: active.fence.senderEpoch,
      resetGeneration: active.fence.resetGeneration,
      targetInstallationId: SECOND_INSTALLATION_ID,
    };
    expect((await transferService.beginTransfer(uid, request)).kind).toBe(
      'STARTED',
    );
    expect(
      await transferService.coordinationLifecycleStatus(uid, {
        contractVersion: 1,
      }),
    ).toMatchObject({
      kind: 'ANDROID_STATE',
      mode: 'TRANSFER_PENDING',
      activeInstallationId: INSTALLATION_ID,
      transferTargetInstallationId: SECOND_INSTALLATION_ID,
      transferDrainUntilMs: transferNowMs,
    });
    transferNowMs += 1;
    expect((await transferService.completeTransfer(uid, request)).kind).toBe(
      'COMPLETED',
    );
    expect(
      await transferService.coordinationLifecycleStatus(uid, {
        contractVersion: 1,
      }),
    ).toMatchObject({
      kind: 'ANDROID_STATE',
      mode: 'TEST_ONLY',
      activeInstallationId: SECOND_INSTALLATION_ID,
      senderEpoch: active.fence.senderEpoch + 1,
    });
  });

  it('reports deletion without an operation UUID and purges lifecycle receipts', async () => {
    const uid = 'emulator-lifecycle-deletion-status';
    let deletionNowMs = nowMs;
    const deletionService = new ControlPlaneService(
      db,
      keyRing,
      () => deletionNowMs,
    );
    const resetRequest = {
      contractVersion: 1 as const,
      requestId: '00000000-0000-4000-8000-000000000972',
    };
    expect(
      await deletionService.requestContactDerivedReset(uid, resetRequest),
    ).toMatchObject({ kind: 'COMPLETED' });
    const resetIdentity = deriveOperationIdentity(
      uid,
      'CONTACT_DERIVED_RESET',
      resetRequest.requestId,
    );
    expect(
      (
        await db
          .collection('coordinationOperationReceipts')
          .doc(resetIdentity.requestKey)
          .get()
      ).exists,
    ).toBe(true);
    expect(
      await deletionService.beginDeletion(uid, {
        contractVersion: 1,
        requestId: '00000000-0000-4000-8000-000000000973',
      }),
    ).toMatchObject({ kind: 'STARTED' });
    expect(
      await deletionService.coordinationLifecycleStatus(uid, {
        contractVersion: 1,
      }),
    ).toMatchObject({
      kind: 'ACCOUNT_DELETION_IN_PROGRESS',
      stage: 'DRAINING',
    });
    deletionNowMs += 1;
    expect(await deletionService.advanceDeletion(uid)).toBe('ADVANCED');
    expect(
      (
        await db
          .collection('coordinationOperationReceipts')
          .doc(resetIdentity.requestKey)
          .get()
      ).exists,
    ).toBe(false);
    expect(
      (await db.collection('coordinationLatestReceipts').doc(uid).get()).exists,
    ).toBe(false);
    expect(
      await deletionService.coordinationLifecycleStatus(uid, {
        contractVersion: 1,
      }),
    ).toMatchObject({
      kind: 'ACCOUNT_DELETION_IN_PROGRESS',
      stage: 'AUTH_DELETION_PENDING',
    });
  });

  it('rotates failed reset/release repairs so work beyond the page limit completes', async () => {
    const operationCollection = db.collection('coordinationOperationFences');
    for (let index = 0; index < 21; index += 1) {
      await operationCollection
        .doc(`scheduler-operation-a${String(index).padStart(2, '0')}`)
        .set({
          schemaVersion: SCHEMA_VERSION,
          operation: 'SENDER_RELEASE',
          stage: 'RELEASE_DRAINING',
          requestKey: index.toString(16).padStart(64, '0'),
          requestFingerprint: (index + 100).toString(16).padStart(64, '0'),
          accountKey: (index + 200).toString(16).padStart(64, '0'),
          androidStateExisted: true,
          senderEpochAfter: 2,
          resetGenerationAfter: 1,
          drainUntilMs: nowMs - 1,
          nextSweepAtMs: nowMs,
          sweepAttemptCount: 0,
          createdAtMs: nowMs,
          updatedAtMs: nowMs,
        });
    }
    const readyUid = 'scheduler-operation-z-ready';
    await operationCollection.doc(readyUid).set({
      schemaVersion: SCHEMA_VERSION,
      operation: 'CONTACT_DERIVED_RESET',
      stage: 'RESET_PURGING',
      requestKey: 'e'.repeat(64),
      requestFingerprint: 'f'.repeat(64),
      accountKey: 'd'.repeat(64),
      androidStateExisted: false,
      nextSweepAtMs: nowMs,
      sweepAttemptCount: 0,
      createdAtMs: nowMs,
      updatedAtMs: nowMs,
    });
    const orchestrator = new CoordinationOperationOrchestrator(db, () => nowMs);
    expect(await orchestrator.sweep(20)).toEqual({
      completed: 0,
      pending: 0,
      failed: 20,
    });
    expect((await operationCollection.doc(readyUid).get()).exists).toBe(true);
    expect(await orchestrator.sweep(20)).toEqual({
      completed: 1,
      pending: 0,
      failed: 1,
    });
    expect((await operationCollection.doc(readyUid).get()).exists).toBe(false);
  });

  it('rotates every deletion stage so later users are verified', async () => {
    const tombstones = db.collection('deletionTombstones');
    for (let index = 0; index < 21; index += 1) {
      const stage =
        index % 3 === 0
          ? 'DRAINING'
          : index % 3 === 1
          ? 'AUTH_DELETION_PENDING'
          : 'VERIFYING';
      await tombstones
        .doc(`scheduler-deletion-a${String(index).padStart(2, '0')}`)
        .set({
          schemaVersion: SCHEMA_VERSION,
          requestKey: deriveDeletionReceiptKey(
            `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
          ),
          stage,
          drainUntilMs: stage === 'DRAINING' ? nowMs + 60_000 : nowMs - 1,
          ...(stage === 'VERIFYING' ? { cleanupAtMs: nowMs + 60_000 } : {}),
          nextSweepAtMs: nowMs,
          sweepAttemptCount: 0,
          createdAtMs: stage === 'DRAINING' ? nowMs : nowMs - 2,
          updatedAtMs: nowMs,
        });
    }
    const readyUid = 'scheduler-deletion-z-ready';
    await tombstones.doc(readyUid).set({
      schemaVersion: SCHEMA_VERSION,
      requestKey: deriveDeletionReceiptKey(
        '00000000-0000-4000-8000-000000000999',
      ),
      stage: 'AUTH_DELETION_PENDING',
      drainUntilMs: nowMs - 1,
      nextSweepAtMs: nowMs,
      sweepAttemptCount: 0,
      createdAtMs: nowMs - 2,
      updatedAtMs: nowMs,
    });
    const internalAuthError = Object.assign(new Error('AUTH_INTERNAL'), {
      code: 'auth/internal-error',
    });
    const userNotFoundError = Object.assign(new Error('USER_NOT_FOUND'), {
      code: 'auth/user-not-found',
    });
    const fakeAuth = {
      deleteUser: (uid: string): Promise<void> =>
        uid === readyUid
          ? Promise.resolve()
          : Promise.reject(internalAuthError),
      getUser: (): Promise<never> => Promise.reject(userNotFoundError),
    } as unknown as Auth;
    const orchestrator = new DeletionOrchestrator(db, fakeAuth, () => nowMs);
    expect(await orchestrator.sweep(20)).toEqual({
      drainsAdvanced: 0,
      authDeletionsVerified: 0,
      tombstonesFinalized: 0,
      deferred: 20,
      failed: 0,
    });
    expect((await tombstones.doc(readyUid).get()).get('stage')).toBe(
      'AUTH_DELETION_PENDING',
    );
    expect(await orchestrator.sweep(20)).toEqual({
      drainsAdvanced: 0,
      authDeletionsVerified: 1,
      tombstonesFinalized: 0,
      deferred: 1,
      failed: 0,
    });
    expect((await tombstones.doc(readyUid).get()).get('stage')).toBe(
      'VERIFYING',
    );
  });

  it('repairs a crash after deletion entered PURGING', async () => {
    const uid = 'emulator-deletion-purge-recovery';
    await db
      .collection('deletionTombstones')
      .doc(uid)
      .set({
        schemaVersion: SCHEMA_VERSION,
        requestKey: deriveDeletionReceiptKey(
          '00000000-0000-4000-8000-000000001001',
        ),
        stage: 'PURGING',
        drainUntilMs: nowMs - 1,
        nextSweepAtMs: nowMs,
        sweepAttemptCount: 0,
        createdAtMs: nowMs - 2,
        updatedAtMs: nowMs,
      });
    await db
      .collection('accounts')
      .doc(uid)
      .collection('testClaims')
      .doc('orphan-to-delete')
      .set({ orphan: true });
    const neverUsedAuth = {} as Auth;
    const orchestrator = new DeletionOrchestrator(
      db,
      neverUsedAuth,
      () => nowMs,
    );
    expect(await orchestrator.sweep(20)).toMatchObject({ drainsAdvanced: 1 });
    expect((await db.collection('accounts').doc(uid).get()).exists).toBe(false);
    expect(
      (await db.collection('accounts').doc(uid).collection('testClaims').get())
        .empty,
    ).toBe(true);
    expect(
      (await db.collection('deletionTombstones').doc(uid).get()).get('stage'),
    ).toBe('AUTH_DELETION_PENDING');
  });
});
