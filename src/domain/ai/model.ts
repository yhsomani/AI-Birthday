/**
 * Provider-agnostic AI entitlement domain model.
 *
 * Architectural invariants (see AI_ENTITLEMENT_ARCHITECTURE.md):
 *  - Identity, application subscription, provider authorisation and AI execution
 *    are FOUR SEPARATE concepts. This module models only the entitlement layer:
 *    "may this account use AI features, under which limits, through which
 *    authorisation mode?"
 *  - A user's external/provider subscription (e.g. a consumer Google AI plan)
 *    is NEVER an application entitlement. Only the project's own subscription
 *    state flips `enabled`.
 *  - Provider authorisation for end users is OAUTH SIGN-IN ONLY
 *    ("use my AI login"). Users never paste API keys: bring-your-own-key
 *    (BYOK) has been removed from this architecture. The only credential-
 *    bearing mode left is application-owned project credentials, which are
 *    invisible to users and never user-supplied.
 *  - This module is pure: no Firebase, no networking, no React Native imports.
 */

import type { SafeReasonCode } from '../shared/reasonCodes';

// --- Closed vocabularies -----------------------------------------------------

export const AI_PLAN_IDS = ['free', 'wishwell-plus'] as const;
export type AiPlanId = (typeof AI_PLAN_IDS)[number];

// Adapter ids name the EXECUTION target, not how the user authenticates to
// it — provider sign-in (OAuth) is an authorization-mode concern layered on
// top of these ids (see AI_AUTHORIZATION_MODES).
export const AI_PROVIDER_IDS = ['gemini-cloud', 'on-device'] as const;
export type AiProviderId = (typeof AI_PROVIDER_IDS)[number];

export const AI_CAPABILITY_IDS = ['message-drafting'] as const;
export type AiCapabilityId = (typeof AI_CAPABILITY_IDS)[number];

export const AI_EXECUTION_TARGETS = ['cloud', 'on-device'] as const;
export type AiExecutionTarget = (typeof AI_EXECUTION_TARGETS)[number];

export const AI_AUTHORIZATION_MODES = [
  /** Application-owned project credentials. Invisible to users; never user-supplied. */
  'application-owned',
  /**
   * The user signs into the AI provider with their own account (OAuth 2.0
   * authorization code + PKCE). This is the "use my AI login" mode: no API
   * keys are ever pasted, stored or handled by the app.
   */
  'provider-sign-in',
  /** No provider credential at all; inference runs entirely on the device. */
  'on-device',
] as const;
export type AiAuthorizationMode = (typeof AI_AUTHORIZATION_MODES)[number];

/** Lifecycle of a provider sign-in connection (replaces the old API-key states). */
export const AI_CONNECTION_STATES = [
  'disconnected',
  'connecting',
  'connected',
  'expired',
  'failed',
] as const;
export type AiConnectionState = (typeof AI_CONNECTION_STATES)[number];

export const AI_USAGE_PERIODS = ['calendar-month'] as const;
export type AiUsagePeriod = (typeof AI_USAGE_PERIODS)[number];

// --- Quotas -------------------------------------------------------------------

export type AiQuota = Readonly<{
  period: AiUsagePeriod;
  requestsPerDay: number;
  requestsPerMonth: number;
}>;

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

// --- Entitlement decision ------------------------------------------------------

export type AiUsageSnapshot = Readonly<{
  period: AiUsagePeriod;
  usedToday: number;
  usedInPeriod: number;
}>;

export type AiEntitlementDecision =
  | Readonly<{ kind: 'allowed'; quota: AiQuota }>
  | Readonly<{
      kind: 'blocked';
      reason: Extract<SafeReasonCode, 'ai-subscription-required'>;
    }>
  | Readonly<{
      kind: 'blocked';
      reason: Extract<
        SafeReasonCode,
        'ai-quota-exhausted' | 'ai-usage-period-mismatch'
      >;
    }>;

/**
 * Pure entitlement gate. The ONLY thing that enables AI here is the project's
 * own subscription state (`enabled`), never any external provider subscription.
 */
export function decideAiEntitlement(
  entitlement: AiEntitlementProjection,
  usage: AiUsageSnapshot,
): AiEntitlementDecision {
  if (
    !entitlement.enabled ||
    !entitlement.capabilities.includes('message-drafting')
  ) {
    return { kind: 'blocked', reason: 'ai-subscription-required' };
  }
  if (usage.period !== entitlement.quota.period) {
    return { kind: 'blocked', reason: 'ai-usage-period-mismatch' };
  }
  if (
    usage.usedToday >= entitlement.quota.requestsPerDay ||
    usage.usedInPeriod >= entitlement.quota.requestsPerMonth
  ) {
    return { kind: 'blocked', reason: 'ai-quota-exhausted' };
  }
  return { kind: 'allowed', quota: entitlement.quota };
}

// --- Projections (what the UI renders) -----------------------------------------

export type AiProviderAccessProjection = Readonly<{
  provider: AiProviderId;
  authorizationMode: AiAuthorizationMode;
  /**
   * Sign-in lifecycle for `provider-sign-in` mode; null when the mode needs
   * no user-level connection (application-owned, on-device).
   */
  connectionState: AiConnectionState | null;
}>;

export type AiEntitlementProjection = Readonly<{
  /** Single source of truth for "is AI on": derived from the app subscription. */
  enabled: boolean;
  plan: AiPlanId;
  capabilities: readonly AiCapabilityId[];
  quota: AiQuota;
  providers: readonly AiProviderAccessProjection[];
  /** ISO date the subscription renews/expires, or null while active. */
  renewsOn: string | null;
}>;

export type AiSettingsProjection =
  | Readonly<{ kind: 'not-configured' }>
  | Readonly<{
      kind: 'configured';
      entitlement: AiEntitlementProjection;
      usage: AiUsageSnapshot | null;
    }>;

// --- Provider sign-in (OAuth) lifecycle intents ---------------------------------
// "Bring your own AI — via login, not keys." The user authenticates to the
// provider with their own account through OAuth 2.0 authorization code + PKCE
// in a Custom Tab / ASWebAuthenticationSession. Tokens live only in device
// secure storage (EncryptedSharedPreferences backed by the Android keystore);
// they never cross the bridge to JavaScript and are never pasted or stored as
// long-lived API keys.

/** Providers reachable through user sign-in today. Adding one = one adapter. */
export const AI_SIGNIN_PROVIDERS = ['gemini-cloud'] as const;
export type AiSignInProvider = (typeof AI_SIGNIN_PROVIDERS)[number];

export type AiSignInResult =
  | 'connected'
  | 'cancelled-by-user'
  | 'rejected-by-provider'
  | 'network-offline'
  | 'internal-contract-invalid';

export function isAiSignInProvider(value: unknown): value is AiSignInProvider {
  return (
    typeof value === 'string' &&
    (AI_SIGNIN_PROVIDERS as readonly string[]).includes(value)
  );
}
