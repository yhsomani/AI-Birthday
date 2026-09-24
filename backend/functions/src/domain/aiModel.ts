/**
 * AI Gateway domain model — provider-agnostic entitlement vocabulary.
 *
 * This is the SERVER-SIDE mirror of the mobile entitlement model
 * (`src/domain/ai/model.ts` in the app package). The two must stay aligned;
 * the authoritative specification lives in `AI_ENTITLEMENT_ARCHITECTURE.md`
 * and `Ai gateway architecture.md` at the repository root.
 *
 * Architectural invariants:
 *  - Identity, application subscription, provider authorisation and AI
 *    execution are FOUR SEPARATE concepts.
 *  - A user's external/provider subscription (e.g. a consumer Google AI
 *    plan) is NEVER an application entitlement. Only WishWell's own
 *    subscription state flips `enabled`.
 *  - Provider authorisation for end users is OAuth sign-in only
 *    ("use my AI login") or application-owned project credentials.
 *    Bring-your-own-key (BYOK) is deliberately absent.
 *  - This module is pure: no Firebase, no networking, no provider SDKs.
 */

export const AI_SCHEMA_VERSION = 1 as const;

// --- Closed vocabularies -----------------------------------------------------

export const AI_PLAN_IDS = ['free', 'wishwell-plus'] as const;
export type AiPlanId = (typeof AI_PLAN_IDS)[number];

export const AI_PROVIDER_IDS = [
  'gemini-cloud',
  'on-device',
  'local',
  'user-gemini',
  'stub',
] as const;
export type AiProviderId = (typeof AI_PROVIDER_IDS)[number];

export const AI_CAPABILITY_IDS = ['message-drafting'] as const;
export type AiCapabilityId = (typeof AI_CAPABILITY_IDS)[number];

export const AI_EXECUTION_MODES = [
  'user-authorized',
  'local',
  'on-device',
  'application-cloud',
  'stub',
] as const;
export type AiExecutionMode = (typeof AI_EXECUTION_MODES)[number];

export const AI_AUTHORIZATION_MODES = [
  /** Application-owned project credentials. Invisible to users. */
  'application-owned',
  /** OAuth "use my AI login". Users never paste API keys. */
  'provider-sign-in',
  /** No provider credential; inference runs entirely on the device. */
  'on-device',
  /** Local model or generative synthesis on-device. */
  'local',
] as const;
export type AiAuthorizationMode =
  (typeof AI_AUTHORIZATION_MODES)[number];

export const AI_USAGE_PERIODS = ['calendar-month'] as const;
export type AiUsagePeriod = (typeof AI_USAGE_PERIODS)[number];

export const AI_SUBSCRIPTION_STATUSES = [
  'active',
  'trialing',
  'past_due',
  'cancelled',
  'expired',
  'revoked',
] as const;
export type AiSubscriptionStatus = (typeof AI_SUBSCRIPTION_STATUSES)[number];

/** Machine-readable error codes across the AI architecture. */
export const AI_ERROR_CODES = [
  'AI_SUBSCRIPTION_REQUIRED',
  'AI_PROVIDER_UNAVAILABLE',
  'AI_PROVIDER_NOT_AUTHORIZED',
  'AI_PROVIDER_AUTH_EXPIRED',
  'AI_FEATURE_NOT_SUPPORTED',
  'AI_QUOTA_EXCEEDED',
  'AI_RATE_LIMITED',
  'AI_UNAVAILABLE',
  'AI_EXECUTION_FAILED',
  'AI_CONFIGURATION_ERROR',
] as const;
export type AiErrorCode = (typeof AI_ERROR_CODES)[number];

/** Reason codes surfaced to clients. Closed vocabulary, stable strings. */
export const AI_REASON_CODES = [
  'ai-subscription-required',
  'ai-quota-exhausted',
  'ai-usage-period-mismatch',
  'ai-provider-unavailable',
  'ai-provider-not-authorized',
  'ai-provider-auth-expired',
  'ai-feature-not-supported',
  'ai-rate-limited',
  'ai-unavailable',
  'ai-execution-failed',
  'ai-configuration-error',
] as const;
export type AiReasonCode = (typeof AI_REASON_CODES)[number];

// --- Generic AI Request / Result contracts ------------------------------------

export interface AIRequest {
  readonly capability: AiCapabilityId;
  readonly preferredProvider?: AiProviderId | undefined;
  readonly executionPreference?: AiExecutionMode | undefined;
  readonly input: {
    readonly recipientDisplayName?: string | undefined;
    readonly tone?: string | undefined;
    readonly relationshipHint?: string | undefined;
    readonly additionalContext?: string | undefined;
    readonly language?: string | undefined;
    readonly milestone?: string | undefined;
    readonly placeholderMode?: string | undefined;
    readonly requestedSegmentCap?: number | undefined;
    readonly [key: string]: unknown;
  };
  readonly prompt?: string | undefined;
  readonly systemInstruction?: string | undefined;
  readonly maxOutputTokens?: number | undefined;
  readonly temperature?: number | undefined;
  readonly options?: Record<string, unknown> | undefined;
}

export interface AIResult {
  readonly provider: AiProviderId;
  readonly executionMode: AiExecutionMode;
  readonly model: string;
  readonly text: string;
  readonly inputTokens: number;
  readonly outputTokens: number;
  /** Estimated cost in integer microcurrency units (₹ × 1,000,000). */
  readonly estimatedCostMicros: number;
  readonly latencyMs?: number | undefined;
}

// --- Quotas -------------------------------------------------------------------

export interface AiQuota {
  readonly period: AiUsagePeriod;
  readonly requestsPerDay: number;
  readonly requestsPerMonth: number;
}

const PLAN_QUOTAS: Readonly<Record<AiPlanId, AiQuota>> = {
  // Free tier ships with AI disabled; the quota below is defensive only.
  free: { period: 'calendar-month', requestsPerDay: 0, requestsPerMonth: 0 },
  'wishwell-plus': {
    period: 'calendar-month',
    requestsPerDay: 50,
    requestsPerMonth: 300,
  },
};

export function aiQuotaForPlan(plan: AiPlanId): AiQuota {
  return PLAN_QUOTAS[plan];
}

/** Capability matrix per plan. Free has no AI capabilities at all. */
const PLAN_CAPABILITIES: Readonly<Record<AiPlanId, readonly AiCapabilityId[]>> =
  {
    free: [],
    'wishwell-plus': ['message-drafting'],
  };

export function aiCapabilitiesForPlan(plan: AiPlanId): readonly AiCapabilityId[] {
  return PLAN_CAPABILITIES[plan];
}

// --- Persisted subscription projection ----------------------------------------

/**
 * Server-of-record view of WishWell's OWN subscription for one account,
 * materialised by billing webhooks (Play Billing / Stripe) onto
 * `users/{uid}/meta/aiEntitlement`. External provider subscriptions never
 * write here.
 */
export interface AiSubscriptionRecord {
  readonly schemaVersion: typeof AI_SCHEMA_VERSION;
  readonly uid: string;
  readonly plan: AiPlanId;
  readonly status: AiSubscriptionStatus;
  /** Billing provider that owns this record ('play-billing' | 'stripe'). */
  readonly billingProvider: string;
  readonly externalSubscriptionId: string | null;
  readonly purchasedAtMs: number | null;
  readonly expiresAtMs: number | null;
  readonly cancelledAtMs: number | null;
  readonly updatedAtMs: number;
}

// --- Entitlement decision ------------------------------------------------------

export interface AiUsageSnapshot {
  readonly period: AiUsagePeriod;
  readonly usedToday: number;
  readonly usedInPeriod: number;
}

export interface AiProviderAvailability {
  readonly provider: AiProviderId;
  readonly authorizationMode: AiAuthorizationMode;
  readonly available: boolean;
}

export interface AiEntitlementProjection {
  readonly enabled: boolean;
  readonly plan: AiPlanId;
  readonly capabilities: readonly AiCapabilityId[];
  readonly quota: AiQuota;
  readonly providers: readonly AiProviderAvailability[];
  readonly renewsOn: string | null;
}

export type AiEntitlementDecision =
  | { readonly kind: 'allowed'; readonly quota: AiQuota }
  | {
      readonly kind: 'blocked';
      readonly reason: Extract<
        AiReasonCode,
        'ai-subscription-required'
      >;
    }
  | {
      readonly kind: 'blocked';
      readonly reason: Extract<
        AiReasonCode,
        'ai-quota-exhausted' | 'ai-usage-period-mismatch'
      >;
    };

const ACTIVE_STATUSES: readonly AiSubscriptionStatus[] = [
  'active',
  'trialing',
];

/**
 * Materialises the persisted subscription into the entitlement projection
 * the gate consumes. Only WishWell's own subscription state can flip
 * `enabled`; nothing a user holds with an external AI vendor is consulted.
 */
export function projectAiEntitlement(
  record: AiSubscriptionRecord | null,
  nowMs: number,
): AiEntitlementProjection {
  const plan: AiPlanId = record?.plan ?? 'free';
  const active =
    record !== null &&
    ACTIVE_STATUSES.includes(record.status) &&
    (record.expiresAtMs === null || record.expiresAtMs > nowMs);
  const capabilities = active ? aiCapabilitiesForPlan(plan) : [];
  return {
    enabled: active && capabilities.length > 0,
    plan,
    capabilities,
    quota: aiQuotaForPlan(plan),
    providers: [
      {
        provider: 'gemini-cloud',
        authorizationMode: 'application-owned',
        available: active,
      },
    ],
    renewsOn:
      record !== null && record.expiresAtMs !== null
        ? new Date(record.expiresAtMs).toISOString().slice(0, 10)
        : null,
  };
}

// --- Offline Entitlement Cache & Grace Period Policy ---------------------------

export interface CachedAiEntitlement {
  readonly uid: string;
  readonly plan: AiPlanId;
  readonly status: AiSubscriptionStatus;
  readonly enabled: boolean;
  readonly lastVerifiedAtMs: number;
  readonly expiresAtMs: number | null;
  /** Max allowed offline grace period in ms (default: 72 hours). */
  readonly gracePeriodMs: number;
}

export const OFFLINE_ENTITLEMENT_GRACE_PERIOD_MS = 72 * 60 * 60 * 1000; // 72h max

export function isCachedEntitlementValid(
  cached: CachedAiEntitlement | null,
  nowMs: number,
): { valid: boolean; reason?: 'no-cache' | 'expired' | 'grace-period-exceeded' | 'inactive' } {
  if (!cached || !cached.enabled || (cached.status !== 'active' && cached.status !== 'trialing')) {
    return { valid: false, reason: 'inactive' };
  }
  if (cached.expiresAtMs !== null && nowMs > cached.expiresAtMs) {
    return { valid: false, reason: 'expired' };
  }
  if (nowMs - cached.lastVerifiedAtMs > cached.gracePeriodMs) {
    return { valid: false, reason: 'grace-period-exceeded' };
  }
  return { valid: true };
}

function civilDateKeyUTC(nowMs: number): string {
  return new Date(nowMs).toISOString().slice(0, 10);
}

/** Calendar-month key (UTC) used for usage documents. */
export function usagePeriodKey(nowMs: number): string {
  return new Date(nowMs).toISOString().slice(0, 7);
}

/**
 * Pure entitlement gate. Order of checks is deliberate:
 * subscription first, then period alignment, then daily/monthly quotas.
 */
export function decideAiEntitlement(
  entitlement: AiEntitlementProjection,
  usage: AiUsageSnapshot,
  capability: AiCapabilityId,
): AiEntitlementDecision {
  if (!entitlement.enabled || !entitlement.capabilities.includes(capability)) {
    return { kind: 'blocked', reason: 'ai-subscription-required' };
  }
  // Defensive runtime check: both sides are typed 'calendar-month' today, but
  // usage documents may have been written by an older/newer schema version.
  // eslint-disable-next-line @typescript-eslint/no-unnecessary-condition
  if (usage.period !== entitlement.quota.period) {
    return { kind: 'blocked', reason: 'ai-usage-period-mismatch' };
  }
  if (usage.usedInPeriod >= entitlement.quota.requestsPerMonth) {
    return { kind: 'blocked', reason: 'ai-quota-exhausted' };
  }
  if (usage.usedToday >= entitlement.quota.requestsPerDay) {
    return { kind: 'blocked', reason: 'ai-quota-exhausted' };
  }
  return { kind: 'allowed', quota: entitlement.quota };
}

// --- Usage ledger ---------------------------------------------------------------

export interface AiUsageEntry {
  readonly id: string;
  readonly capability: AiCapabilityId;
  readonly provider: AiProviderId;
  readonly model: string;
  readonly inputTokens: number;
  readonly outputTokens: number;
  /** Estimated cost in integer microcurrency units (₹ × 1,000,000). */
  readonly estimatedCostMicros: number;
  readonly latencyMs: number;
  readonly createdAtMs: number;
  readonly civilDate: string;
  readonly period: AiUsagePeriod;
  readonly periodKey: string;
}

export interface AiUsageTotals {
  readonly usedToday: number;
  readonly usedInPeriod: number;
  readonly tokensInPeriod: number;
  readonly estimatedCostMicrosInPeriod: number;
}

/**
 * Aggregates immutable usage entries into a quota snapshot. Day counting uses
 * UTC civil date; period counting uses the calendar-month key. Entries from
 * other periods are ignored (they belong to prior quotas).
 */
export function aggregateAiUsage(
  entries: readonly AiUsageEntry[],
  nowMs: number,
): AiUsageTotals {
  const today = civilDateKeyUTC(nowMs);
  const periodKey = usagePeriodKey(nowMs);
  let usedToday = 0;
  let usedInPeriod = 0;
  let tokensInPeriod = 0;
  let cost = 0;
  for (const entry of entries) {
    if (entry.periodKey !== periodKey) {
      continue;
    }
    usedInPeriod += 1;
    tokensInPeriod += entry.inputTokens + entry.outputTokens;
    cost += entry.estimatedCostMicros;
    if (entry.civilDate === today) {
      usedToday += 1;
    }
  }
  return {
    usedToday,
    usedInPeriod,
    tokensInPeriod,
    estimatedCostMicrosInPeriod: cost,
  };
}

/**
 * Derives the quota-check snapshot from aggregated totals. Guards against
 * negative counters so a corrupt ledger degrades to "no usage" rather than
 * blocking everything. `nowMs` is kept in the signature for symmetry with
 * aggregateAiUsage and future per-period snapshots.
 */
export function usageSnapshotFromTotals(
  totals: AiUsageTotals,
  nowMs: number,
): AiUsageSnapshot {
  void usagePeriodKey(nowMs);
  return {
    period: 'calendar-month',
    usedToday: Math.max(0, totals.usedToday),
    usedInPeriod: Math.max(0, totals.usedInPeriod),
  };
}
