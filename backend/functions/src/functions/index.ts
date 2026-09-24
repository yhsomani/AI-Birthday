import { getApps, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, Timestamp } from 'firebase-admin/firestore';
import { defineSecret, defineJsonSecret, defineString } from 'firebase-functions/params';
import {
  HttpsError,
  onCall,
  type CallableRequest,
} from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import {
  onDocumentCreated,
  type FirestoreEvent,
  type QueryDocumentSnapshot,
} from 'firebase-functions/v2/firestore';
import type { z } from 'zod';

import { parseKeyRing } from '../domain/opaque.js';
import { deletionStartResponse } from '../domain/deletionReceipt.js';
import {
  usagePeriodKey,
  AI_SCHEMA_VERSION,
  type AiSubscriptionStatus,
} from '../domain/aiModel.js';
import {
  GeminiRestAdapter,
  StubProviderAdapter,
  type AiProviderAdapter,
} from '../domain/aiProviders.js';
import { CoordinationOperationOrchestrator } from '../services/coordinationOperationOrchestrator.js';
import { ControlPlaneService } from '../services/controlPlane.js';
import { DeletionOrchestrator } from '../services/deletionOrchestrator.js';
import {
  AiGatewayService,
  GatewayBlockedError,
} from '../services/aiGateway.js';
import { applyPlayBillingEvent } from '../services/subscriptionIngestion.js';
import {
  aiEntitlementPath,
  aiUsageSummary,
} from '../persistence/paths.js';
import {
  armSchema,
  accountModeSchema,
  aiEntitlementStatusSchema,
  aiGenerateDraftSchema,
  birthdayClaimSchema,
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
const GEMINI_API_KEY = defineSecret('AI_GATEWAY_GEMINI_API_KEY');
const AI_PROVIDER_MODE = defineString('AI_GATEWAY_PROVIDER_MODE', {
  // 'stub' keeps the gateway offline-deterministic in dev/emulator deploys.
  default: 'stub',
});
const AI_MONTHLY_BUDGET_MICROS = defineString('AI_GATEWAY_MONTHLY_BUDGET_MICROS', {
  default: '5000000000', // ₹5,000/month global cloud-cost ceiling.
});
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

const aiGatewayOptions = {
  ...commonOptions,
  secrets: [GEMINI_API_KEY],
};

function buildAiGateway(): AiGatewayService {
  const adapters = new Map<string, AiProviderAdapter>();
  if (AI_PROVIDER_MODE.value() === 'gemini-rest') {
    const apiKey = GEMINI_API_KEY.value();
    if (apiKey !== '') {
      const adapter = new GeminiRestAdapter(apiKey);
      adapters.set(adapter.provider, adapter);
    }
  }
  if (!adapters.has('gemini-cloud')) {
    const stub = new StubProviderAdapter('gemini-cloud');
    adapters.set(stub.provider, stub);
  }
  const budgetRaw = Number.parseInt(AI_MONTHLY_BUDGET_MICROS.value(), 10);
  return new AiGatewayService(db, adapters, {
    globalMonthlyBudgetMicros:
      Number.isSafeInteger(budgetRaw) && budgetRaw > 0 ? budgetRaw : 5_000_000_000,
  });
}

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


// --- AI Gateway callables -------------------------------------------------------
// These are ADDITIVE endpoints: no existing callable changes behaviour, and
// the Android app's native Gemini path keeps working untouched. The gateway
// is the entitlement-gated cloud execution lane described in
// "Ai gateway architecture.md" / "Ai gateway integration guide.md".

const AI_SYSTEM_INSTRUCTION =
  'You draft short, sincere birthday messages for a personal contacts app. ' +
  'Return only the message text. Never invent facts about the recipient, ' +
  'never mention that you are an AI, and keep each draft under 280 characters.';

function buildDraftPrompt(input: {
  recipientDisplayName: string;
  tone: string;
  relationshipHint?: string | undefined;
  additionalContext?: string | undefined;
}): string {
  const parts = [
    `Write one birthday message for "${input.recipientDisplayName}".`,
    `Tone: ${input.tone}.`,
  ];
  if (input.relationshipHint !== undefined) {
    parts.push(`Relationship: ${input.relationshipHint}.`);
  }
  if (input.additionalContext !== undefined) {
    parts.push(`Extra context from the sender: ${input.additionalContext}`);
  }
  return parts.join(' ');
}

function gatewayBlockedToHttps(error: unknown): HttpsError {
  if (error instanceof GatewayBlockedError) {
    switch (error.reason) {
      case 'ai-subscription-required':
        return new HttpsError('failed-precondition', 'AI_SUBSCRIPTION_REQUIRED', {
          reason: error.reason,
        });
      case 'ai-quota-exhausted':
      case 'ai-budget-exhausted':
        return new HttpsError('resource-exhausted', 'AI_QUOTA_EXHAUSTED', {
          reason: error.reason,
        });
      case 'ai-usage-period-mismatch':
        return new HttpsError('failed-precondition', 'AI_USAGE_PERIOD_MISMATCH', {
          reason: error.reason,
        });
      default:
        return new HttpsError('unavailable', 'AI_PROVIDER_UNAVAILABLE', {
          reason: error.reason,
        });
    }
  }
  throw error;
}

export const getAiEntitlementStatus = onCall(
  aiGatewayOptions,
  async request => {
    const uid = requireAuthenticated(request);
    parseRequest(aiEntitlementStatusSchema, request.data);
    const gateway = buildAiGateway();
    const nowMs = Timestamp.now().toMillis();
    try {
      const entitlement = await gateway.entitlementFor(uid);
      const summarySnap = await aiUsageSummary(
        db,
        uid,
        usagePeriodKey(nowMs),
      ).get();
      const data = summarySnap.data() as Record<string, unknown> | undefined;
      const usedInPeriod =
        typeof data?.requests === 'number' ? data.requests : 0;
      const days = (data?.days ?? {}) as Record<string, number>;
      const today = new Date(nowMs).toISOString().slice(0, 10);
      return {
        contractVersion: 1,
        enabled: entitlement.enabled,
        plan: entitlement.plan,
        capabilities: entitlement.capabilities,
        quota: entitlement.quota,
        providers: entitlement.providers,
        renewsOn: entitlement.renewsOn,
        usage: {
          period: 'calendar-month',
          usedToday: days[today] ?? 0,
          usedInPeriod,
        },
      };
    } catch (error) {
      throw gatewayBlockedToHttps(error);
    }
  },
);

export const generateBirthdayDraft = onCall(
  aiGatewayOptions,
  async request => {
    const uid = requireAuthenticated(request);
    const input = parseRequest(aiGenerateDraftSchema, request.data);
    const gateway = buildAiGateway();
    try {
      const outcome = await gateway.generate(uid, {
        capability: input.capability,
        prompt: buildDraftPrompt(input),
        systemInstruction: AI_SYSTEM_INSTRUCTION,
        maxOutputTokens: 300,
        temperature: 0.7,
      });
      return {
        contractVersion: 1,
        requestId: input.requestId,
        draft: outcome.result.text,
        provider: outcome.result.provider,
        model: outcome.result.model,
        remainingToday: outcome.remainingToday,
        remainingInPeriod: outcome.remainingInPeriod,
      };
    } catch (error) {
      throw gatewayBlockedToHttps(error);
    }
  },
);


// --- Billing ingestion (server-only) --------------------------------------------
// RTDN-normalised events are written by a small ingester (Cloud Run/Functions
// with Play Developer access) onto users/{uid}/events/playBilling/{eventId}.
// This Firestore trigger is the ONLY writer of the entitlement record.
// Unknown SKUs and replayed/out-of-order events are ignored — see
// services/subscriptionIngestion.ts for the reduction rules.

interface BillingEventParams { uid: string; eventId: string }

export const onPlayBillingEvent = onDocumentCreated(
  {
    region: REGION,
    timeoutSeconds: 30,
    memory: '256MiB',
    serviceAccount: SERVICE_ACCOUNT,
    document: 'users/{uid}/events/playBilling/{eventId}',
  },
  async (
    event: FirestoreEvent<
      QueryDocumentSnapshot | undefined,
      BillingEventParams
    >,
  ) => {
    // Params are typed as always-defined, but validate defensively since the
    // trigger fires on externally-authored documents.
    const uid: string = event.params.uid;
    if (typeof uid !== 'string' || uid.length === 0) {
      return;
    }
    await applyPlayBillingEvent(
      async (targetUid, record) => {
        await aiEntitlementPath(db, targetUid).set(
          record as unknown as Record<string, unknown>,
          { merge: false },
        );
      },
      async (targetUid) => {
        const snap = await aiEntitlementPath(db, targetUid).get();
        const data = snap.data() as Record<string, unknown> | undefined;
        if (data === undefined) {
          return null;
        }
        const plan = data.plan;
        const status = data.status;
        const updatedAtMs = data.updatedAtMs;
        const expiresAtMs = data.expiresAtMs;
        const purchasedAtMs = data.purchasedAtMs;
        const cancelledAtMs = data.cancelledAtMs;
        const externalId = data.externalSubscriptionId;
        const billingProvider = data.billingProvider;
        if (
          (plan !== 'free' && plan !== 'wishwell-plus') ||
          typeof status !== 'string' ||
          typeof updatedAtMs !== 'number'
        ) {
          return null;
        }
        return {
          schemaVersion: AI_SCHEMA_VERSION,
          uid: targetUid,
          plan,
          status: status as AiSubscriptionStatus,
          billingProvider:
            typeof billingProvider === 'string' ? billingProvider : 'unknown',
          externalSubscriptionId:
            typeof externalId === 'string' ? externalId : null,
          purchasedAtMs:
            typeof purchasedAtMs === 'number' ? purchasedAtMs : null,
          expiresAtMs: typeof expiresAtMs === 'number' ? expiresAtMs : null,
          cancelledAtMs:
            typeof cancelledAtMs === 'number' ? cancelledAtMs : null,
          updatedAtMs,
        };
      },
      uid,
      event.data,
    );
  },
);
