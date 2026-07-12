import { getApps, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { defineJsonSecret, defineString } from 'firebase-functions/params';
import {
  HttpsError,
  onCall,
  type CallableRequest,
} from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import type { z } from 'zod';

import { parseKeyRing } from '../domain/opaque.js';
import { deletionStartResponse } from '../domain/deletionReceipt.js';
import { CoordinationOperationOrchestrator } from '../services/coordinationOperationOrchestrator.js';
import { ControlPlaneService } from '../services/controlPlane.js';
import { DeletionOrchestrator } from '../services/deletionOrchestrator.js';
import {
  armSchema,
  accountModeSchema,
  birthdayClaimSchema,
  companionStatusSchema,
  contactDerivedResetSchema,
  coordinationLifecycleStatusSchema,
  deletionSchema,
  deletionReceiptSchema,
  leaseSchema,
  registrationSchema,
  retrySchema,
  senderReleaseSchema,
  testClaimSchema,
  testReportSchema,
  transferSchema,
} from '../transport/schemas.js';

if (getApps().length === 0) {
  initializeApp();
}

const REGION = 'asia-south1';
const HMAC_KEYRING = defineJsonSecret('COORDINATION_HMAC_KEYRING');
const SERVICE_ACCOUNT = defineString('CONTROL_PLANE_SERVICE_ACCOUNT');
const db = getFirestore();

const commonOptions = {
  region: REGION,
  enforceAppCheck: true,
  consumeAppCheckToken: true,
  timeoutSeconds: 30,
  memory: '256MiB' as const,
  minInstances: 0,
  maxInstances: 20,
  concurrency: 20,
  serviceAccount: SERVICE_ACCOUNT,
};

const secretOptions = {
  ...commonOptions,
  secrets: [HMAC_KEYRING],
};

function requireAuthenticated(request: CallableRequest<unknown>): string {
  if (request.auth === undefined || request.app === undefined) {
    throw new HttpsError('unauthenticated', 'AUTHENTICATION_REQUIRED');
  }
  return request.auth.uid;
}

function requireAppChecked(request: CallableRequest<unknown>): void {
  if (request.app === undefined) {
    throw new HttpsError('unauthenticated', 'APP_CHECK_REQUIRED');
  }
}

function requireSignedOutAppChecked(request: CallableRequest<unknown>): void {
  requireAppChecked(request);
  if (request.auth !== undefined) {
    throw new HttpsError('failed-precondition', 'SIGNED_OUT_REQUIRED');
  }
}

function requireRecentAuthentication(
  request: CallableRequest<unknown>,
  nowMs: number,
): void {
  if (request.auth === undefined) {
    throw new HttpsError('unauthenticated', 'AUTHENTICATION_REQUIRED');
  }
  const token = request.auth.token as Readonly<Record<string, unknown>>;
  const authTime = token.auth_time;
  if (
    typeof authTime !== 'number' ||
    !Number.isSafeInteger(authTime) ||
    authTime * 1_000 > nowMs + 60_000 ||
    nowMs - authTime * 1_000 > 5 * 60_000
  ) {
    throw new HttpsError(
      'failed-precondition',
      'RECENT_AUTHENTICATION_REQUIRED',
    );
  }
}

function requireRecentGoogleAuthentication(
  request: CallableRequest<unknown>,
  nowMs: number,
): void {
  requireRecentAuthentication(request, nowMs);
  const token = request.auth?.token as Readonly<Record<string, unknown>>;
  const firebase = token.firebase;
  if (typeof firebase !== 'object' || firebase === null) {
    throw new HttpsError(
      'failed-precondition',
      'RECENT_GOOGLE_REAUTHENTICATION_REQUIRED',
    );
  }
  const firebaseClaims = firebase as Readonly<Record<string, unknown>>;
  const identities = firebaseClaims.identities;
  const googleIdentities: unknown =
    typeof identities === 'object' && identities !== null
      ? (identities as Readonly<Record<string, unknown>>)['google.com']
      : undefined;
  if (
    firebaseClaims.sign_in_provider !== 'google.com' ||
    !Array.isArray(googleIdentities) ||
    googleIdentities.length !== 1 ||
    typeof googleIdentities[0] !== 'string' ||
    googleIdentities[0].length === 0
  ) {
    throw new HttpsError(
      'failed-precondition',
      'RECENT_GOOGLE_REAUTHENTICATION_REQUIRED',
    );
  }
}

function parseRequest<T>(schema: z.ZodType<T>, data: unknown): T {
  const result = schema.safeParse(data);
  if (!result.success) {
    throw new HttpsError('invalid-argument', 'INVALID_REQUEST');
  }
  return result.data;
}

function withoutSecret(): ControlPlaneService {
  return new ControlPlaneService(db);
}

function withSecret(): ControlPlaneService {
  return new ControlPlaneService(db, parseKeyRing(HMAC_KEYRING.value()));
}

async function safeCall<T>(operation: () => Promise<T>): Promise<T> {
  try {
    return await operation();
  } catch (error) {
    if (error instanceof HttpsError) {
      throw error;
    }
    // Transport/internal failure is deliberately ambiguous. It must never be
    // converted into armWritten=false or any other no-write proof.
    throw new HttpsError('unavailable', 'COORDINATION_UNAVAILABLE');
  }
}

export const registerAndroidInstallation = onCall(
  commonOptions,
  async request => {
    const uid = requireAuthenticated(request);
    const input = parseRequest(registrationSchema, request.data);
    return safeCall(() =>
      withoutSecret().registerAndroidInstallation(uid, input),
    );
  },
);

export const renewSenderLease = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(leaseSchema, request.data);
  return safeCall(() => withoutSecret().renewLease(uid, input));
});

export const changeAccountMode = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(accountModeSchema, request.data);
  return safeCall(() => withoutSecret().changeAccountMode(uid, input));
});

export const claimOccurrence = onCall(secretOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(birthdayClaimSchema, request.data);
  return safeCall(() => withSecret().claimBirthdayOccurrence(uid, input));
});

export const claimTest = onCall(secretOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(testClaimSchema, request.data);
  return safeCall(() => withSecret().claimTest(uid, input));
});

export const armAttempt = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(armSchema, request.data);
  return safeCall(() => withoutSecret().armAttempt(uid, input));
});

export const getArmStatus = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(armSchema, request.data);
  return safeCall(() => withoutSecret().getArmStatus(uid, input));
});

export const reportTestOutcome = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(testReportSchema, request.data);
  return safeCall(() => withoutSecret().reportTestOutcome(uid, input));
});

export const authorizeSafeRetry = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(retrySchema, request.data);
  return safeCall(() => withoutSecret().authorizeSafeRetry(uid, input));
});

export const beginSenderTransfer = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const nowMs = Timestamp.now().toMillis();
  requireRecentGoogleAuthentication(request, nowMs);
  const input = parseRequest(transferSchema, request.data);
  return safeCall(() => withoutSecret().beginTransfer(uid, input));
});

export const completeSenderTransfer = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const nowMs = Timestamp.now().toMillis();
  requireRecentGoogleAuthentication(request, nowMs);
  const input = parseRequest(transferSchema, request.data);
  return safeCall(() => withoutSecret().completeTransfer(uid, input));
});

export const requestAccountDeletion = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const nowMs = Timestamp.now().toMillis();
  requireRecentGoogleAuthentication(request, nowMs);
  const input = parseRequest(deletionSchema, request.data);
  return safeCall(async () => {
    const decision = await withoutSecret().beginDeletion(uid, input);
    if (
      decision.kind !== 'STARTED' &&
      decision.kind !== 'REPLAYED' &&
      decision.kind !== 'REFUSED'
    ) {
      throw new Error('INVALID_DELETION_START_STATE');
    }
    return deletionStartResponse(decision, input.requestId);
  });
});

export const accountDeletionReceipt = onCall(commonOptions, async request => {
  requireSignedOutAppChecked(request);
  const input = parseRequest(deletionReceiptSchema, request.data);
  return safeCall(() => withoutSecret().accountDeletionReceipt(input));
});

export const resetContactDerivedState = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const nowMs = Timestamp.now().toMillis();
  requireRecentGoogleAuthentication(request, nowMs);
  const input = parseRequest(contactDerivedResetSchema, request.data);
  return safeCall(() => withoutSecret().requestContactDerivedReset(uid, input));
});

export const releaseAndroidSender = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const nowMs = Timestamp.now().toMillis();
  requireRecentGoogleAuthentication(request, nowMs);
  const input = parseRequest(senderReleaseSchema, request.data);
  return safeCall(() => withoutSecret().requestSenderRelease(uid, input));
});

export const coordinationLifecycleStatus = onCall(
  commonOptions,
  async request => {
    const uid = requireAuthenticated(request);
    const input = parseRequest(coordinationLifecycleStatusSchema, request.data);
    return safeCall(() =>
      withoutSecret().coordinationLifecycleStatus(uid, input),
    );
  },
);

export const companionStatus = onCall(commonOptions, async request => {
  const uid = requireAuthenticated(request);
  const input = parseRequest(companionStatusSchema, request.data);
  return safeCall(() => withoutSecret().companionStatus(uid, input));
});

export const sweepDeletionDrains = onSchedule(
  {
    schedule: 'every 1 minutes',
    timeZone: 'Etc/UTC',
    region: REGION,
    timeoutSeconds: 300,
    memory: '256MiB',
    maxInstances: 1,
    serviceAccount: SERVICE_ACCOUNT,
  },
  async () => {
    const orchestrator = new DeletionOrchestrator(db, getAuth(), () =>
      Timestamp.now().toMillis(),
    );
    await orchestrator.sweep();
  },
);

export const sweepCoordinationOperations = onSchedule(
  {
    schedule: 'every 1 minutes',
    timeZone: 'Etc/UTC',
    region: REGION,
    timeoutSeconds: 300,
    memory: '256MiB',
    maxInstances: 1,
    serviceAccount: SERVICE_ACCOUNT,
  },
  async () => {
    const orchestrator = new CoordinationOperationOrchestrator(db, () =>
      Timestamp.now().toMillis(),
    );
    await orchestrator.sweep();
  },
);
