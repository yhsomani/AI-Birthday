import { createHash } from 'node:crypto';

import {
  IOS_COMPOSER_RESERVATION_MS,
  SCHEMA_VERSION,
  type AccountFence,
  type CoordinationOperation,
  type DeletionTombstone,
  type GlobalControl,
  type IOSComposerReservation,
} from './model.js';
import { safeAddMs } from './policies.js';

export type IOSComposerReservationRefusal =
  | 'DELETION_SUPPRESSED'
  | 'MANAGED_BY_ANDROID'
  | 'RESERVATION_HELD'
  | 'RESERVATION_MISMATCH'
  | 'RESERVATION_MISSING'
  | 'RESERVATION_EXPIRED'
  | 'STICKY_UNTIL_EXPIRY'
  | 'SAFETY_STATUS_UNAVAILABLE';

export type IOSComposerAcquireDecision =
  | {
      readonly kind: 'RESERVED';
      readonly reservation: IOSComposerReservation;
      readonly serverNowMs: number;
      readonly earlyReleaseAllowed: boolean;
    }
  | {
      readonly kind: 'REFUSED';
      readonly reason: IOSComposerReservationRefusal;
      readonly serverNowMs: number;
    };

export type IOSComposerCommitDecision =
  | {
      readonly kind: 'COMMITTED';
      readonly reservation: IOSComposerReservation;
      readonly serverNowMs: number;
    }
  | {
      readonly kind: 'REFUSED';
      readonly reason: IOSComposerReservationRefusal;
      readonly serverNowMs: number;
    };

export type IOSComposerReleaseDecision =
  | {
      readonly kind: 'RELEASED';
      readonly serverNowMs: number;
    }
  | {
      readonly kind: 'REFUSED';
      readonly reason: IOSComposerReservationRefusal;
      readonly serverNowMs: number;
    };

export function deriveIOSComposerReservationKey(
  uid: string,
  reservationId: string,
): string {
  return createHash('sha256')
    .update('birthday-autopilot:ios-composer-reservation:v1\0', 'utf8')
    .update(uid, 'utf8')
    .update('\0', 'utf8')
    .update(reservationId, 'utf8')
    .digest('hex');
}

export function isLiveIOSComposerReservation(
  reservation: IOSComposerReservation | null,
  nowMs: number,
): boolean {
  return reservation !== null && reservation.expiresAtMs > nowMs;
}

interface IOSComposerSafetySnapshot {
  readonly control: GlobalControl | null;
  readonly expectedLedgerGeneration: string;
  readonly tombstone: DeletionTombstone | null;
  readonly operation: CoordinationOperation | null;
  readonly fence: AccountFence | null;
  readonly hasPresence: boolean;
}

function safetyRefusal(
  snapshot: IOSComposerSafetySnapshot,
): IOSComposerReservationRefusal | null {
  if (snapshot.tombstone !== null || snapshot.fence?.mode === 'DELETING') {
    return 'DELETION_SUPPRESSED';
  }
  if (
    snapshot.control?.continuityState !== 'HEALTHY' ||
    snapshot.control.ledgerGeneration !== snapshot.expectedLedgerGeneration ||
    snapshot.operation !== null
  ) {
    return 'SAFETY_STATUS_UNAVAILABLE';
  }
  if (snapshot.fence !== null) {
    return 'MANAGED_BY_ANDROID';
  }
  if (snapshot.hasPresence) {
    return 'SAFETY_STATUS_UNAVAILABLE';
  }
  return null;
}

export function decideAcquireIOSComposerReservation(
  snapshot: IOSComposerSafetySnapshot,
  existing: IOSComposerReservation | null,
  reservationKey: string,
  nowMs: number,
): IOSComposerAcquireDecision {
  const refusal = safetyRefusal(snapshot);
  if (refusal !== null) {
    return { kind: 'REFUSED', reason: refusal, serverNowMs: nowMs };
  }
  if (
    isLiveIOSComposerReservation(existing, nowMs) &&
    existing?.reservationKey !== reservationKey
  ) {
    return {
      kind: 'REFUSED',
      reason: 'RESERVATION_HELD',
      serverNowMs: nowMs,
    };
  }
  const expiresAtMs = safeAddMs(nowMs, IOS_COMPOSER_RESERVATION_MS);
  const exactLiveReservation =
    isLiveIOSComposerReservation(existing, nowMs) &&
    existing?.reservationKey === reservationKey
      ? existing
      : null;
  const reservation: IOSComposerReservation = {
    schemaVersion: SCHEMA_VERSION,
    reservationKey,
    phase: exactLiveReservation?.phase ?? 'PREPARED',
    ledgerGeneration: snapshot.expectedLedgerGeneration,
    createdAtMs: exactLiveReservation?.createdAtMs ?? nowMs,
    updatedAtMs: nowMs,
    expiresAtMs,
    cleanupAtMs: expiresAtMs,
  };
  return {
    kind: 'RESERVED',
    reservation,
    serverNowMs: nowMs,
    earlyReleaseAllowed: reservation.phase === 'PREPARED',
  };
}

export function decideCommitIOSComposerReservation(
  snapshot: IOSComposerSafetySnapshot,
  existing: IOSComposerReservation | null,
  reservationKey: string,
  nowMs: number,
): IOSComposerCommitDecision {
  const refusal = safetyRefusal(snapshot);
  if (refusal !== null) {
    return { kind: 'REFUSED', reason: refusal, serverNowMs: nowMs };
  }
  if (existing === null) {
    return {
      kind: 'REFUSED',
      reason: 'RESERVATION_MISSING',
      serverNowMs: nowMs,
    };
  }
  if (existing.reservationKey !== reservationKey) {
    return {
      kind: 'REFUSED',
      reason: 'RESERVATION_MISMATCH',
      serverNowMs: nowMs,
    };
  }
  if (!isLiveIOSComposerReservation(existing, nowMs)) {
    return {
      kind: 'REFUSED',
      reason: 'RESERVATION_EXPIRED',
      serverNowMs: nowMs,
    };
  }
  const reservation: IOSComposerReservation = {
    ...existing,
    phase: 'COMMITTED',
    updatedAtMs: nowMs,
  };
  return { kind: 'COMMITTED', reservation, serverNowMs: nowMs };
}

export function decideReleaseIOSComposerReservation(
  tombstone: DeletionTombstone | null,
  existing: IOSComposerReservation | null,
  reservationKey: string,
  nowMs: number,
): IOSComposerReleaseDecision {
  if (tombstone !== null) {
    return {
      kind: 'REFUSED',
      reason: 'DELETION_SUPPRESSED',
      serverNowMs: nowMs,
    };
  }
  if (existing === null) {
    return {
      kind: 'REFUSED',
      reason: 'RESERVATION_MISSING',
      serverNowMs: nowMs,
    };
  }
  if (existing.reservationKey !== reservationKey) {
    return {
      kind: 'REFUSED',
      reason: 'RESERVATION_MISMATCH',
      serverNowMs: nowMs,
    };
  }
  if (
    existing.phase === 'COMMITTED' &&
    isLiveIOSComposerReservation(existing, nowMs)
  ) {
    return {
      kind: 'REFUSED',
      reason: 'STICKY_UNTIL_EXPIRY',
      serverNowMs: nowMs,
    };
  }
  return { kind: 'RELEASED', serverNowMs: nowMs };
}
