import { describe, expect, it } from 'vitest';

import {
  decideArm,
  decideArmStatus,
  decideBeginDeletion,
  decideBeginTransfer,
  decideClaim,
  decideCompanionStatus,
  decideCompleteTransfer,
  decideRegistration,
  decideSafeRetry,
  decideTestReport,
  type ArmInput,
  type ArmSnapshot,
  type ClaimInput,
} from '../src/domain/decisions.js';
import {
  ARM_SPACING_MS,
  BIRTHDAY_ARM_CAP,
  BUDGET_WINDOW_MS,
  MAX_SUBMIT_AFTER_ARM_MS,
  SCHEMA_VERSION,
  TEST_ARM_CAP,
  type DeletionTombstone,
  type DestinationGuard,
  type OccurrenceKey,
} from '../src/domain/model.js';
import {
  INSTALLATION_ID,
  NOW_MS,
  SECOND_INSTALLATION_ID,
  binding,
  budget,
  claim,
  fence,
  globalControl,
  installation,
} from './fixtures.js';

const OCCURRENCE_KEY: OccurrenceKey = {
  schemaVersion: SCHEMA_VERSION,
  aliasKey: 'v1.occurrence',
  linkedClaimId: 'v1.claim-key',
  state: 'RESERVED',
  createdAtMs: NOW_MS,
  updatedAtMs: NOW_MS,
  cleanupAtMs: NOW_MS + BUDGET_WINDOW_MS,
};

const DESTINATION_GUARD: DestinationGuard = {
  schemaVersion: SCHEMA_VERSION,
  aliasKey: 'v1.destination',
  linkedClaimId: 'v1.claim-key',
  ownerEpoch: 4,
  state: 'RESERVED',
  createdAtMs: NOW_MS,
  updatedAtMs: NOW_MS,
  cleanupAtMs: NOW_MS + BUDGET_WINDOW_MS,
};

const TOMBSTONE: DeletionTombstone = {
  schemaVersion: SCHEMA_VERSION,
  requestKey: 'a'.repeat(64),
  stage: 'DRAINING',
  drainUntilMs: NOW_MS,
  nextSweepAtMs: NOW_MS + 1,
  sweepAttemptCount: 0,
  createdAtMs: NOW_MS,
  updatedAtMs: NOW_MS,
};

function armInput(overrides: Partial<ArmInput> = {}): ArmInput {
  return {
    ...binding(),
    purpose: 'BIRTHDAY',
    claimId: 'v1.claim-key',
    armRequestId: '00000000-0000-4000-8000-000000000010',
    attempt: 1,
    ...overrides,
  };
}

function armSnapshot(overrides: Partial<ArmSnapshot> = {}): ArmSnapshot {
  return {
    outcome: null,
    tombstone: null,
    fence: fence(),
    installation: installation(),
    claim: claim(),
    occurrenceKeys: [OCCURRENCE_KEY],
    destinationGuards: [DESTINATION_GUARD],
    budget: null,
    ...overrides,
  };
}

describe('registration and account fencing', () => {
  const request = {
    ledgerGeneration: 'ledger-generation-1',
    installationId: INSTALLATION_ID,
    appBuildNumber: 100,
    policyVersion: 7,
    distributionChannel: 'PLAY',
  } as const;

  it('assigns epoch one in TEST_ONLY and never activates Birthday implicitly', () => {
    const decision = decideRegistration(
      globalControl(),
      null,
      null,
      null,
      request,
      NOW_MS,
    );
    expect(decision.kind).toBe('REGISTERED_ACTIVE');
    if (decision.kind === 'REGISTERED_ACTIVE') {
      expect(decision.fence.mode).toBe('TEST_ONLY');
      expect(decision.fence.senderEpoch).toBe(1);
      expect(decision.installation.state).toBe('ACTIVE');
      expect(decision.fence.birthdayAutomationNotBeforeMs).toBeGreaterThan(
        NOW_MS,
      );
    }
  });

  it('registers a second device as STANDBY without changing ownership', () => {
    const currentFence = fence();
    const decision = decideRegistration(
      globalControl(),
      null,
      currentFence,
      null,
      { ...request, installationId: SECOND_INSTALLATION_ID },
      NOW_MS,
    );
    expect(decision.kind).toBe('REGISTERED_STANDBY');
    if (decision.kind === 'REGISTERED_STANDBY') {
      expect(decision.fence).toEqual(currentFence);
      expect(decision.installation.state).toBe('STANDBY');
      expect(decision.installation.epoch).toBe(0);
    }
  });

  it('lets an iOS-only deletion tombstone beat first Android registration', () => {
    expect(
      decideRegistration(
        globalControl(),
        TOMBSTONE,
        null,
        null,
        request,
        NOW_MS,
      ),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' });
  });

  it('fails closed on a missing or mismatched continuity generation', () => {
    expect(decideRegistration(null, null, null, null, request, NOW_MS)).toEqual(
      { kind: 'SUPPRESSED', reason: 'CONTINUITY_UNAVAILABLE' },
    );
    expect(
      decideRegistration(
        globalControl(),
        null,
        null,
        null,
        { ...request, ledgerGeneration: 'different-generation' },
        NOW_MS,
      ),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'LEDGER_GENERATION_MISMATCH' });
  });
});

describe('recipient, destination, and TEST claim namespaces', () => {
  const birthdayInput: ClaimInput = {
    ...binding(),
    purpose: 'BIRTHDAY',
    claimRequestId: '00000000-0000-4000-8000-000000000001',
    claimId: 'v1.claim-key',
    requestKey: 'v1.request-key',
    occurrenceAliasKeys: ['v1.occurrence'],
    destinationAliasKeys: ['v1.destination'],
    testMaterialAliasKeys: [],
  };
  const emptyCollisions = {
    requestRecord: null,
    requestClaim: null,
    occurrenceKeys: [],
    destinationGuards: [],
  } as const;

  it('atomically materializes both Birthday guard families', () => {
    const decision = decideClaim(
      globalControl(),
      null,
      fence(),
      installation(),
      emptyCollisions,
      birthdayInput,
      NOW_MS,
    );
    expect(decision.kind).toBe('CLAIMED');
    if (decision.kind === 'CLAIMED') {
      expect(decision.occurrenceKeys).toHaveLength(1);
      expect(decision.destinationGuards).toHaveLength(1);
      expect(decision.claim.claimExpiresAtMs - NOW_MS).toBe(10 * 60_000);
      expect(
        decision.claim.maxPossibleSubmitNotAfterMs -
          decision.claim.claimExpiresAtMs,
      ).toBe(MAX_SUBMIT_AFTER_ARM_MS);
    }
  });

  it('refuses either recipient or destination collisions', () => {
    expect(
      decideClaim(
        globalControl(),
        null,
        fence(),
        installation(),
        { ...emptyCollisions, occurrenceKeys: [OCCURRENCE_KEY] },
        birthdayInput,
        NOW_MS,
      ),
    ).toEqual({ kind: 'REFUSED', reason: 'OCCURRENCE_RESERVED' });
    expect(
      decideClaim(
        globalControl(),
        null,
        fence(),
        installation(),
        { ...emptyCollisions, destinationGuards: [DESTINATION_GUARD] },
        birthdayInput,
        NOW_MS,
      ),
    ).toEqual({ kind: 'REFUSED', reason: 'DESTINATION_RESERVED' });
  });

  it('replays the deterministic claim after the short request pointer is cleaned', () => {
    const existing = claim('BIRTHDAY', {
      claimId: birthdayInput.claimId,
      claimRequestId: birthdayInput.claimRequestId,
      state: 'ARMED',
    });
    expect(
      decideClaim(
        globalControl(),
        null,
        fence(),
        installation(),
        { ...emptyCollisions, requestClaim: existing },
        birthdayInput,
        NOW_MS,
      ),
    ).toEqual({ kind: 'REPLAYED', claim: existing });
  });

  it('keeps TEST claims out of Birthday occurrence and destination guards', () => {
    const testInput: ClaimInput = {
      ...birthdayInput,
      purpose: 'TEST',
      claimId: 'v1.test-key',
      occurrenceAliasKeys: [],
      destinationAliasKeys: [],
      testMaterialAliasKeys: ['v1.test-material'],
    };
    const decision = decideClaim(
      globalControl(),
      null,
      fence({ mode: 'TEST_ONLY' }),
      installation(),
      emptyCollisions,
      testInput,
      NOW_MS,
    );
    expect(decision.kind).toBe('CLAIMED');
    if (decision.kind === 'CLAIMED') {
      expect(decision.claim.purpose).toBe('TEST');
      expect(decision.occurrenceKeys).toEqual([]);
      expect(decision.destinationGuards).toEqual([]);
      expect(
        decideClaim(
          globalControl(),
          null,
          fence({ mode: 'TEST_ONLY' }),
          installation(),
          { ...emptyCollisions, requestClaim: decision.claim },
          testInput,
          NOW_MS,
        ),
      ).toEqual({ kind: 'REPLAYED', claim: decision.claim });
      expect(
        decideClaim(
          globalControl(),
          null,
          fence({ mode: 'TEST_ONLY' }),
          installation(),
          { ...emptyCollisions, requestClaim: decision.claim },
          { ...testInput, testMaterialAliasKeys: ['v2.different-material'] },
          NOW_MS,
        ),
      ).toEqual({ kind: 'REFUSED', reason: 'TEST_MATERIAL_MISMATCH' });
    }
  });
});

describe('claim-to-arm boundary', () => {
  it('arms once, creates an immutable deadline, advances spacing, and budgets once', () => {
    const decision = decideArm(
      globalControl(),
      armSnapshot(),
      armInput(),
      NOW_MS,
    );
    expect(decision.kind).toBe('ARMED');
    if (decision.kind === 'ARMED') {
      expect(decision.outcome.serverSubmitNotAfterMs).toBe(
        NOW_MS + MAX_SUBMIT_AFTER_ARM_MS,
      );
      expect(decision.fence.nextArmNotBeforeMs).toBe(
        decision.outcome.serverSubmitNotAfterMs + ARM_SPACING_MS,
      );
      expect(decision.claim.state).toBe('ARMED');
      expect(decision.destinationGuards[0]?.state).toBe('ARMED');
      expect(decision.budget.entries).toHaveLength(1);
    }
  });

  it('replays an existing outcome even after deletion starts', () => {
    const armed = decideArm(globalControl(), armSnapshot(), armInput(), NOW_MS);
    expect(armed.kind).toBe('ARMED');
    if (armed.kind === 'ARMED') {
      expect(
        decideArm(
          null,
          armSnapshot({
            outcome: armed.outcome,
            tombstone: TOMBSTONE,
            fence: fence({ mode: 'DELETING' }),
          }),
          armInput(),
          NOW_MS + 1,
        ),
      ).toEqual({ kind: 'REPLAYED', outcome: armed.outcome });
    }
  });

  it('never writes a false NO_WRITE outcome under deletion or missing history', () => {
    expect(
      decideArm(
        globalControl(),
        armSnapshot({ tombstone: TOMBSTONE }),
        armInput(),
        NOW_MS,
      ),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' });
    expect(
      decideArm(
        globalControl(),
        armSnapshot({ claim: null }),
        armInput(),
        NOW_MS,
      ),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'MISSING_CLAIM' });
  });

  it('transactionally seals initial expiry but preserves Armed guard history for retry expiry', () => {
    const initial = claim('BIRTHDAY', { claimExpiresAtMs: NOW_MS });
    const expiredInitial = decideArm(
      globalControl(),
      armSnapshot({ claim: initial }),
      armInput(),
      NOW_MS,
    );
    expect(expiredInitial.kind).toBe('NO_WRITE');
    if (expiredInitial.kind === 'NO_WRITE') {
      expect(expiredInitial.outcome.kind).toBe('NO_WRITE');
      expect(expiredInitial.outcome.reason).toBe('EXPIRED');
      expect(expiredInitial.claim?.state).toBe('EXPIRED_NO_ARM');
      expect(expiredInitial.destinationGuards?.[0]?.state).toBe(
        'EXPIRED_NO_ARM_RECLAIMABLE',
      );
    }

    const retry = claim('BIRTHDAY', {
      state: 'RETRY_CLAIMED',
      attempt: 2,
      claimExpiresAtMs: NOW_MS,
    });
    const armedOccurrence = { ...OCCURRENCE_KEY, state: 'ARMED' as const };
    const armedGuard = { ...DESTINATION_GUARD, state: 'ARMED' as const };
    const expiredRetry = decideArm(
      globalControl(),
      armSnapshot({
        claim: retry,
        occurrenceKeys: [armedOccurrence],
        destinationGuards: [armedGuard],
      }),
      armInput({ attempt: 2 }),
      NOW_MS,
    );
    expect(expiredRetry.kind).toBe('NO_WRITE');
    if (expiredRetry.kind === 'NO_WRITE') {
      expect(expiredRetry.outcome.reason).toBe('EXPIRED_RETRY');
      expect(expiredRetry.claim?.state).toBe('RETRY_EXPIRED_NO_ARM');
      expect(expiredRetry.destinationGuards?.[0]?.state).toBe('ARMED');
      expect(expiredRetry.occurrenceKeyState).toBeUndefined();
    }
  });

  it('enforces 20 Birthday and 3 TEST entries even while TTL lags', () => {
    const birthdayEntries = Array.from(
      { length: BIRTHDAY_ARM_CAP },
      (_, index) => ({
        id: `birthday-${String(index)}`,
        armedAtMs: NOW_MS - index,
      }),
    );
    const birthdayDecision = decideArm(
      globalControl(),
      armSnapshot({ budget: budget('BIRTHDAY', birthdayEntries) }),
      armInput(),
      NOW_MS,
    );
    expect(birthdayDecision.kind).toBe('NO_WRITE');
    if (birthdayDecision.kind === 'NO_WRITE') {
      expect(birthdayDecision.outcome.reason).toBe('BUDGET_EXCEEDED');
    }

    const testClaim = claim('TEST', {
      claimId: 'v1.test-key',
      occurrenceAliasKeys: [],
      destinationAliasKeys: [],
    });
    const testEntries = Array.from({ length: TEST_ARM_CAP }, (_, index) => ({
      id: `test-${String(index)}`,
      armedAtMs: NOW_MS - index,
    }));
    const testDecision = decideArm(
      globalControl(),
      armSnapshot({
        fence: fence({ mode: 'TEST_ONLY' }),
        claim: testClaim,
        occurrenceKeys: [],
        destinationGuards: [],
        budget: budget('TEST', testEntries),
      }),
      armInput({ purpose: 'TEST', claimId: 'v1.test-key' }),
      NOW_MS,
    );
    expect(testDecision.kind).toBe('NO_WRITE');
    if (testDecision.kind === 'NO_WRITE') {
      expect(testDecision.outcome.reason).toBe('BUDGET_EXCEEDED');
    }
  });

  it('keeps missing pre-expiry outcomes UNKNOWN and seals expiry in status transaction', () => {
    expect(decideArmStatus(armSnapshot(), armInput(), NOW_MS)).toEqual({
      kind: 'UNKNOWN',
    });
    const expired = decideArmStatus(
      armSnapshot({ claim: claim('BIRTHDAY', { claimExpiresAtMs: NOW_MS }) }),
      armInput(),
      NOW_MS,
    );
    expect(expired.kind).toBe('NO_WRITE');
    if (expired.kind === 'NO_WRITE') {
      expect(expired.outcome.reason).toBe('EXPIRED');
    }
  });

  it('never reclaims an already-Armed guard when outcome history is missing', () => {
    const inconsistent = armSnapshot({
      claim: claim('BIRTHDAY', { claimExpiresAtMs: NOW_MS }),
      occurrenceKeys: [{ ...OCCURRENCE_KEY, state: 'ARMED' }],
      destinationGuards: [{ ...DESTINATION_GUARD, state: 'ARMED' }],
    });
    expect(
      decideArm(globalControl(), inconsistent, armInput(), NOW_MS),
    ).toEqual({
      kind: 'SUPPRESSED',
      reason: 'UNKNOWN_HISTORY',
    });
    expect(decideArmStatus(inconsistent, armInput(), NOW_MS)).toEqual({
      kind: 'UNKNOWN',
    });
  });

  it('authorizes only one allowlisted safe retry and never attempt three', () => {
    const armed = claim('BIRTHDAY', { state: 'ARMED', attempt: 1 });
    const first = decideSafeRetry(
      fence(),
      null,
      armed,
      '10000000-0000-4000-8000-000000000001',
      'ALL_PARTS_NO_SERVICE',
      NOW_MS,
    );
    expect(first.kind).toBe('AUTHORIZED');
    if (first.kind === 'AUTHORIZED') {
      expect(first.claim.attempt).toBe(2);
      expect(first.claim.state).toBe('RETRY_CLAIMED');
      expect(
        decideSafeRetry(
          fence(),
          null,
          first.claim,
          '10000000-0000-4000-8000-000000000001',
          'ALL_PARTS_NO_SERVICE',
          NOW_MS + 1,
        ),
      ).toEqual({ kind: 'AUTHORIZED', claim: first.claim });
      expect(
        decideSafeRetry(
          fence(),
          null,
          first.claim,
          '20000000-0000-4000-8000-000000000002',
          'ALL_PARTS_NO_SERVICE',
          NOW_MS + 1,
        ),
      ).toEqual({ kind: 'REFUSED', reason: 'RETRY_REQUEST_MISMATCH' });
      expect(
        decideSafeRetry(
          fence(),
          null,
          first.claim,
          '10000000-0000-4000-8000-000000000001',
          'ALL_PARTS_RADIO_OFF',
          NOW_MS + 1,
        ),
      ).toEqual({ kind: 'REFUSED', reason: 'RETRY_REQUEST_MISMATCH' });
    }
    expect(
      decideSafeRetry(
        fence(),
        null,
        armed,
        '10000000-0000-4000-8000-000000000001',
        'OTHER',
        NOW_MS,
      ),
    ).toEqual({ kind: 'REFUSED', reason: 'UNSUPPORTED_ZERO_ACCEPTANCE_PROOF' });
  });

  it('mints activation-eligible TEST evidence only from an exact timely Armed outcome', () => {
    const testClaim = claim('TEST', {
      claimId: 'v1.test-key',
      state: 'ARMED',
      occurrenceAliasKeys: [],
      destinationAliasKeys: [],
    });
    const testArmInput = armInput({
      purpose: 'TEST',
      claimId: 'v1.test-key',
    });
    const armed = decideArm(
      globalControl(),
      armSnapshot({
        fence: fence({ mode: 'TEST_ONLY' }),
        claim: claim('TEST', {
          claimId: 'v1.test-key',
          occurrenceAliasKeys: [],
          destinationAliasKeys: [],
        }),
        occurrenceKeys: [],
        destinationGuards: [],
      }),
      testArmInput,
      NOW_MS,
    );
    expect(armed.kind).toBe('ARMED');
    if (armed.kind !== 'ARMED') {
      return;
    }
    const reportInput = {
      ...binding(),
      purpose: 'TEST' as const,
      testClaimId: 'v1.test-key',
      armRequestId: testArmInput.armRequestId,
      result: 'SENT_ALL_PARTS' as const,
    };
    const timely = decideTestReport(
      fence({ mode: 'TEST_ONLY' }),
      null,
      installation(),
      testClaim,
      armed.outcome,
      reportInput,
      armed.outcome.serverSubmitNotAfterMs + 15 * 60_000,
    );
    expect(timely.kind).toBe('RECORDED');
    if (timely.kind === 'RECORDED') {
      expect(timely.claim.testBarrierOutcome).toBe('SENT_ALL_PARTS_IN_WINDOW');
      expect(
        decideTestReport(
          fence({ mode: 'TEST_ONLY' }),
          null,
          installation(),
          timely.claim,
          armed.outcome,
          reportInput,
          NOW_MS,
        ),
      ).toEqual({ kind: 'REPLAYED', outcome: 'SENT_ALL_PARTS_IN_WINDOW' });
    }
    const late = decideTestReport(
      fence({ mode: 'TEST_ONLY' }),
      null,
      installation(),
      testClaim,
      armed.outcome,
      reportInput,
      armed.outcome.serverSubmitNotAfterMs + 15 * 60_000 + 1,
    );
    expect(late.kind).toBe('RECORDED');
    if (late.kind === 'RECORDED') {
      expect(late.claim.testBarrierOutcome).toBe('SENT_EVIDENCE_LATE');
    }
    expect(
      decideTestReport(
        fence({ mode: 'TEST_ONLY' }),
        TOMBSTONE,
        installation(),
        testClaim,
        armed.outcome,
        reportInput,
        NOW_MS,
      ),
    ).toEqual({ kind: 'SUPPRESSED', reason: 'DELETION_SUPPRESSED' });
  });
});

describe('transfer, deletion, and iOS coexistence', () => {
  const standby = installation({
    installationId: SECOND_INSTALLATION_ID,
    state: 'STANDBY',
    epoch: 0,
  });

  it('freezes transfer at the latest issued deadline and completes only strictly later', () => {
    const started = decideBeginTransfer(
      fence({ latestIssuedSubmitNotAfterMs: NOW_MS + 60_000 }),
      null,
      standby,
      SECOND_INSTALLATION_ID,
      NOW_MS,
    );
    expect(started.kind).toBe('STARTED');
    if (started.kind === 'STARTED') {
      expect(started.fence.mode).toBe('TRANSFER_PENDING');
      expect(started.fence.transferDrainUntilMs).toBe(NOW_MS + 60_000);
      expect(
        decideCompleteTransfer(
          started.fence,
          null,
          installation(),
          standby,
          NOW_MS + 60_000,
        ),
      ).toEqual({ kind: 'REFUSED', reason: 'DRAIN_NOT_COMPLETE' });
      const completed = decideCompleteTransfer(
        started.fence,
        null,
        installation(),
        standby,
        NOW_MS + 60_001,
      );
      expect(completed.kind).toBe('COMPLETED');
      if (completed.kind === 'COMPLETED') {
        expect(completed.fence.senderEpoch).toBe(5);
        expect(completed.fence.mode).toBe('TEST_ONLY');
        expect(completed.oldInstallation.state).toBe('REVOKED');
        expect(completed.targetInstallation.state).toBe('ACTIVE');
      }
    }
  });

  it('creates the same no-new-child deletion fence for iOS-only and Android accounts', () => {
    const iosOnly = decideBeginDeletion(
      null,
      null,
      TOMBSTONE.requestKey,
      NOW_MS,
    );
    expect(iosOnly.kind).toBe('STARTED');
    if (iosOnly.kind === 'STARTED') {
      expect(iosOnly.fence).toBeNull();
      expect(iosOnly.tombstone.stage).toBe('DRAINING');
    }
    const android = decideBeginDeletion(
      null,
      fence({ latestIssuedSubmitNotAfterMs: NOW_MS + 60_000 }),
      TOMBSTONE.requestKey,
      NOW_MS,
    );
    expect(android.kind).toBe('STARTED');
    if (android.kind === 'STARTED') {
      expect(android.fence?.mode).toBe('DELETING');
      expect(android.tombstone.drainUntilMs).toBe(NOW_MS + 60_000);
    }
  });

  it('does not regress a deletion tombstone after purging has begun', () => {
    const replayed = decideBeginDeletion(
      null,
      null,
      TOMBSTONE.requestKey,
      NOW_MS,
    );
    expect(replayed.kind).toBe('STARTED');
    if (replayed.kind === 'STARTED') {
      expect(
        decideBeginDeletion(
          { ...replayed.tombstone, stage: 'VERIFYING' },
          null,
          replayed.tombstone.requestKey,
          NOW_MS + 1,
        ).kind,
      ).toBe('REPLAYED');
      expect(
        decideBeginDeletion(
          { ...replayed.tombstone, stage: 'VERIFYING' },
          null,
          'b'.repeat(64),
          NOW_MS + 1,
        ),
      ).toEqual({ kind: 'REFUSED', reason: 'REQUEST_MISMATCH' });
    }
  });

  it('suppresses iOS globally for every Android mode, deletion, orphan, or unknown continuity', () => {
    for (const mode of [
      'TEST_ONLY',
      'PAUSED_REPAIR',
      'AUTOMATION_ACTIVE',
      'TRANSFER_PENDING',
      'DELETING',
    ] as const) {
      expect(
        decideCompanionStatus(
          globalControl(),
          'ledger-generation-1',
          null,
          fence({ mode }),
          false,
          NOW_MS,
        ).composerAllowed,
      ).toBe(false);
    }
    expect(
      decideCompanionStatus(
        globalControl(),
        'ledger-generation-1',
        TOMBSTONE,
        null,
        false,
        NOW_MS,
      ).state,
    ).toBe('DELETING');
    expect(
      decideCompanionStatus(
        globalControl(),
        'ledger-generation-1',
        null,
        null,
        true,
        NOW_MS,
      ).state,
    ).toBe('SAFETY_STATUS_UNAVAILABLE');
    expect(
      decideCompanionStatus(
        null,
        'ledger-generation-1',
        null,
        null,
        false,
        NOW_MS,
      ).state,
    ).toBe('SAFETY_STATUS_UNAVAILABLE');
  });

  it('allows the iOS composer only after fresh proof of globally absent Android state', () => {
    expect(
      decideCompanionStatus(
        globalControl(),
        'ledger-generation-1',
        null,
        null,
        false,
        NOW_MS,
      ),
    ).toEqual({
      composerAllowed: true,
      state: 'NO_ANDROID_STATE',
      serverNowMs: NOW_MS,
      ledgerGeneration: 'ledger-generation-1',
    });
  });
});
