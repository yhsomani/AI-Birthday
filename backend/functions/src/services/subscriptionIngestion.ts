/**
 * Subscription ingestion service — the ONLY writer of
 * `users/{uid}/meta/aiEntitlement`.
 *
 * Sources: Google Play Real-Time Developer Notifications (RTDN) pushed via a
 * Firestore trigger, and (optionally, Phase 2+) Stripe webhooks. Both funnel
 * through this module so entitlement semantics stay in one place.
 *
 * Critical invariant: an external AI vendor subscription (e.g. a consumer
 * Google AI Pro plan on the user's personal account) is NOT a WishWell
 * entitlement. Only purchases of WishWell's own SKUs flip `enabled` here.
 */

/** Minimal structural view of a Firestore document snapshot. */
export interface BillingEventSnapshot {
  data(): Record<string, unknown> | undefined;
}

import {
  AI_SCHEMA_VERSION,
  type AiPlanId,
  type AiSubscriptionRecord,
  type AiSubscriptionStatus,
} from '../domain/aiModel.js';

/** Maps purchased product/SKU identifiers onto WishWell plans. */
export const SKU_TO_PLAN: Readonly<Record<string, AiPlanId>> = {
  'wishwell_plus_monthly': 'wishwell-plus',
  'wishwell_plus_yearly': 'wishwell-plus',
  // Test-track SKUs used by the internal lab build.
  'wishwell_plus_test': 'wishwell-plus',
};

export interface PlaySubscriptionEvent {
  readonly uid: string;
  readonly purchaseToken: string;
  readonly productId: string;
  /** Epoch ms expiry reported by Play (`expiryTimeMillis`). Null → unknown. */
  readonly expiresAtMs: number | null;
  readonly status: AiSubscriptionStatus;
  readonly occurredAtMs: number;
}

const ACTIVE_STATUSES: readonly AiSubscriptionStatus[] = ['active', 'trialing'];

/**
 * Applies one Play RTDN event to the entitlement record with last-write-wins
 * ordering guarded by `updatedAtMs`. Unknown SKUs never grant anything.
 */
export function reducePlayEvent(
  previous: AiSubscriptionRecord | null,
  event: PlaySubscriptionEvent,
): AiSubscriptionRecord | null {
  const plan = SKU_TO_PLAN[event.productId];
  if (plan === undefined) {
    return previous;
  }
  const isActive =
    ACTIVE_STATUSES.includes(event.status) &&
    (event.expiresAtMs === null || event.expiresAtMs > event.occurredAtMs);
  return {
    schemaVersion: AI_SCHEMA_VERSION,
    uid: event.uid,
    plan: isActive ? plan : 'free',
    status: isActive ? event.status : event.status === 'past_due' ? 'past_due' : 'expired',
    billingProvider: 'play-billing',
    externalSubscriptionId: event.purchaseToken.slice(0, 16),
    purchasedAtMs: previous?.purchasedAtMs ?? event.occurredAtMs,
    expiresAtMs: event.expiresAtMs,
    cancelledAtMs: isActive ? previous?.cancelledAtMs ?? null : event.occurredAtMs,
    updatedAtMs: event.occurredAtMs,
  };
}

/** Firestore-trigger entry point: `users/{uid}/events/playBilling/{eventId}`. */
export async function applyPlayBillingEvent(
  write: (uid: string, record: AiSubscriptionRecord) => Promise<void>,
  read: (uid: string) => Promise<AiSubscriptionRecord | null>,
  uid: string,
  raw: BillingEventSnapshot | undefined,
): Promise<'applied' | 'ignored'> {
  const data = raw?.data();
  if (data === undefined) {
    return 'ignored';
  }
  const productId = data.productId;
  const purchaseToken = data.purchaseToken;
  const status = data.status;
  const expiresAtMs = data.expiresAtMs;
  const occurredAtMs = data.occurredAtMs;
  if (
    typeof productId !== 'string' ||
    typeof purchaseToken !== 'string' ||
    typeof status !== 'string' ||
    typeof occurredAtMs !== 'number' ||
    !Number.isSafeInteger(occurredAtMs) ||
    // Absent field is treated as null (open-ended expiry); an explicit value
    // must be a safe integer.
    (expiresAtMs !== undefined &&
      expiresAtMs !== null &&
      !(typeof expiresAtMs === 'number' && Number.isSafeInteger(expiresAtMs)))
  ) {
    return 'ignored';
  }
  if (
    status !== 'active' &&
    status !== 'trialing' &&
    status !== 'past_due' &&
    status !== 'cancelled' &&
    status !== 'expired'
  ) {
    return 'ignored';
  }
  const previous = await read(uid);
  if (previous !== null && previous.updatedAtMs >= occurredAtMs) {
    // Out-of-order or replayed notification: newest state already applied.
    return 'ignored';
  }
  const next = reducePlayEvent(previous, {
    uid,
    purchaseToken,
    productId,
    expiresAtMs: typeof expiresAtMs === 'number' ? expiresAtMs : null,
    status,
    occurredAtMs,
  });
  if (next === null || (previous !== null && next.updatedAtMs === previous.updatedAtMs)) {
    return 'ignored';
  }
  await write(uid, next);
  return 'applied';
}

// --- Stripe Billing Ingestion --------------------------------------------------

/** Maps Stripe Price IDs onto WishWell plans. */
export const STRIPE_PRICE_TO_PLAN: Readonly<Record<string, AiPlanId>> = {
  'price_wishwell_plus_monthly': 'wishwell-plus',
  'price_wishwell_plus_yearly': 'wishwell-plus',
  'price_wishwell_plus_test': 'wishwell-plus',
};

export interface StripeSubscriptionEvent {
  readonly uid: string;
  readonly subscriptionId: string;
  readonly priceId: string;
  readonly expiresAtMs: number | null;
  readonly status: AiSubscriptionStatus;
  readonly occurredAtMs: number;
}

export function reduceStripeEvent(
  previous: AiSubscriptionRecord | null,
  event: StripeSubscriptionEvent,
): AiSubscriptionRecord | null {
  const plan = STRIPE_PRICE_TO_PLAN[event.priceId];
  if (plan === undefined) {
    return previous;
  }
  const isActive =
    ACTIVE_STATUSES.includes(event.status) &&
    (event.expiresAtMs === null || event.expiresAtMs > event.occurredAtMs);
  return {
    schemaVersion: AI_SCHEMA_VERSION,
    uid: event.uid,
    plan: isActive ? plan : 'free',
    status: isActive
      ? event.status
      : event.status === 'past_due'
      ? 'past_due'
      : event.status === 'revoked'
      ? 'revoked'
      : 'expired',
    billingProvider: 'stripe',
    externalSubscriptionId: event.subscriptionId.slice(0, 32),
    purchasedAtMs: previous?.purchasedAtMs ?? event.occurredAtMs,
    expiresAtMs: event.expiresAtMs,
    cancelledAtMs: isActive ? previous?.cancelledAtMs ?? null : event.occurredAtMs,
    updatedAtMs: event.occurredAtMs,
  };
}

/** Firestore-trigger entry point: `users/{uid}/events/stripeBilling/{eventId}`. */
export async function applyStripeBillingEvent(
  write: (uid: string, record: AiSubscriptionRecord) => Promise<void>,
  read: (uid: string) => Promise<AiSubscriptionRecord | null>,
  uid: string,
  raw: BillingEventSnapshot | undefined,
): Promise<'applied' | 'ignored'> {
  const data = raw?.data();
  if (data === undefined) {
    return 'ignored';
  }
  const priceId = data.priceId;
  const subscriptionId = data.subscriptionId;
  const status = data.status;
  const expiresAtMs = data.expiresAtMs;
  const occurredAtMs = data.occurredAtMs;
  if (
    typeof priceId !== 'string' ||
    typeof subscriptionId !== 'string' ||
    typeof status !== 'string' ||
    typeof occurredAtMs !== 'number' ||
    !Number.isSafeInteger(occurredAtMs) ||
    (expiresAtMs !== undefined &&
      expiresAtMs !== null &&
      !(typeof expiresAtMs === 'number' && Number.isSafeInteger(expiresAtMs)))
  ) {
    return 'ignored';
  }
  if (
    status !== 'active' &&
    status !== 'trialing' &&
    status !== 'past_due' &&
    status !== 'cancelled' &&
    status !== 'expired' &&
    status !== 'revoked'
  ) {
    return 'ignored';
  }
  const previous = await read(uid);
  if (previous !== null && previous.updatedAtMs >= occurredAtMs) {
    return 'ignored';
  }
  const next = reduceStripeEvent(previous, {
    uid,
    subscriptionId,
    priceId,
    expiresAtMs: typeof expiresAtMs === 'number' ? expiresAtMs : null,
    status: status as AiSubscriptionStatus,
    occurredAtMs,
  });
  if (next === null || (previous !== null && next.updatedAtMs === previous.updatedAtMs)) {
    return 'ignored';
  }
  await write(uid, next);
  return 'applied';
}

