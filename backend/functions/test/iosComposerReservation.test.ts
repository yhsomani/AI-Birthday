import { describe, expect, it } from 'vitest';

import {
  decideAcquireIOSComposerReservation,
  decideCommitIOSComposerReservation,
  decideReleaseIOSComposerReservation,
  deriveIOSComposerReservationKey,
  isLiveIOSComposerReservation,
} from '../src/domain/iosComposerReservation.js';
import {
  IOS_COMPOSER_RESERVATION_MS,
  SCHEMA_VERSION,
  type IOSComposerReservation,
} from '../src/domain/model.js';
import { NOW_MS, fence, globalControl } from './fixtures.js';

const reservationId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaa801';
const reservationKey = deriveIOSComposerReservationKey(
  'composer-account',
  reservationId,
);

function clearSnapshot() {
  return {
    control: globalControl(),
    expectedLedgerGeneration: 'ledger-generation-1',
    tombstone: null,
    operation: null,
    fence: null,
    hasPresence: false,
  } as const;
}

function prepared(
  overrides: Partial<IOSComposerReservation> = {},
): IOSComposerReservation {
  return {
    schemaVersion: SCHEMA_VERSION,
    reservationKey,
    phase: 'PREPARED',
    ledgerGeneration: 'ledger-generation-1',
    createdAtMs: NOW_MS,
    updatedAtMs: NOW_MS,
    expiresAtMs: NOW_MS + IOS_COMPOSER_RESERVATION_MS,
    cleanupAtMs: NOW_MS + IOS_COMPOSER_RESERVATION_MS,
    ...overrides,
  };
}

describe('account-global iOS composer reservation', () => {
  it('creates only content-free, bounded server-time state', () => {
    const decision = decideAcquireIOSComposerReservation(
      clearSnapshot(),
      null,
      reservationKey,
      NOW_MS,
    );
    expect(decision.kind).toBe('RESERVED');
    if (decision.kind !== 'RESERVED') return;
    expect(decision.earlyReleaseAllowed).toBe(true);
    expect(decision.reservation).toEqual(prepared());
    expect(Object.keys(decision.reservation).sort()).toEqual([
      'cleanupAtMs',
      'createdAtMs',
      'expiresAtMs',
      'ledgerGeneration',
      'phase',
      'reservationKey',
      'schemaVersion',
      'updatedAtMs',
    ]);
  });

  it('allows only the exact owner key to revalidate a live reservation', () => {
    const otherKey = deriveIOSComposerReservationKey(
      'composer-account',
      'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbb802',
    );
    expect(
      decideAcquireIOSComposerReservation(
        clearSnapshot(),
        prepared(),
        otherKey,
        NOW_MS + 1,
      ),
    ).toMatchObject({ kind: 'REFUSED', reason: 'RESERVATION_HELD' });

    const replay = decideAcquireIOSComposerReservation(
      clearSnapshot(),
      prepared({ phase: 'COMMITTED' }),
      reservationKey,
      NOW_MS + 1,
    );
    expect(replay).toMatchObject({
      kind: 'RESERVED',
      earlyReleaseAllowed: false,
      reservation: { phase: 'COMMITTED', reservationKey },
    });
  });

  it('fails closed for Android, deletion, operation, orphan, and continuity state', () => {
    const cases = [
      { ...clearSnapshot(), fence: fence() },
      {
        ...clearSnapshot(),
        tombstone: {
          schemaVersion: SCHEMA_VERSION,
          requestKey: 'a'.repeat(64),
          stage: 'DRAINING' as const,
          drainUntilMs: NOW_MS,
          nextSweepAtMs: NOW_MS,
          sweepAttemptCount: 0,
          createdAtMs: NOW_MS,
          updatedAtMs: NOW_MS,
        },
      },
      {
        ...clearSnapshot(),
        operation: {
          schemaVersion: SCHEMA_VERSION,
          operation: 'CONTACT_DERIVED_RESET' as const,
          stage: 'RESET_PURGING' as const,
          requestKey: 'a'.repeat(64),
          requestFingerprint: 'b'.repeat(64),
          accountKey: 'c'.repeat(64),
          androidStateExisted: false as const,
          nextSweepAtMs: NOW_MS,
          sweepAttemptCount: 0,
          createdAtMs: NOW_MS,
          updatedAtMs: NOW_MS,
        },
      },
      { ...clearSnapshot(), hasPresence: true },
      {
        ...clearSnapshot(),
        control: globalControl({ continuityState: 'FROZEN' }),
      },
      {
        ...clearSnapshot(),
        expectedLedgerGeneration: 'another-ledger',
      },
    ];
    for (const snapshot of cases) {
      expect(
        decideAcquireIOSComposerReservation(
          snapshot,
          null,
          reservationKey,
          NOW_MS,
        ).kind,
      ).toBe('REFUSED');
    }
  });

  it('makes commit sticky and never lets a later cancellation release it', () => {
    const committed = decideCommitIOSComposerReservation(
      clearSnapshot(),
      prepared(),
      reservationKey,
      NOW_MS + 1,
    );
    expect(committed).toMatchObject({
      kind: 'COMMITTED',
      reservation: { phase: 'COMMITTED' },
    });
    if (committed.kind !== 'COMMITTED') return;
    expect(
      decideReleaseIOSComposerReservation(
        null,
        committed.reservation,
        reservationKey,
        NOW_MS + 2,
      ),
    ).toMatchObject({ kind: 'REFUSED', reason: 'STICKY_UNTIL_EXPIRY' });
  });

  it('releases an exact prepared reservation and treats logical expiry as inactive', () => {
    expect(
      decideReleaseIOSComposerReservation(
        null,
        prepared(),
        reservationKey,
        NOW_MS + 1,
      ),
    ).toMatchObject({ kind: 'RELEASED' });
    expect(
      decideReleaseIOSComposerReservation(
        null,
        prepared(),
        'f'.repeat(64),
        NOW_MS + 1,
      ),
    ).toMatchObject({ kind: 'REFUSED', reason: 'RESERVATION_MISMATCH' });
    expect(
      isLiveIOSComposerReservation(
        prepared({ expiresAtMs: NOW_MS, cleanupAtMs: NOW_MS }),
        NOW_MS,
      ),
    ).toBe(false);
  });
});
