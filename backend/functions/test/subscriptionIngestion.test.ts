/**
 * Unit tests for subscription ingestion: SKU→plan mapping, last-write-wins
 * reduction of Play RTDN events, and the Firestore-trigger entry point with
 * injected read/write ports (no emulator needed).
 */

import { describe, expect, it } from 'vitest';

import { AI_SCHEMA_VERSION, type AiSubscriptionRecord } from '../src/domain/aiModel.js';
import {
  SKU_TO_PLAN,
  applyPlayBillingEvent,
  reducePlayEvent,
  type BillingEventSnapshot,
  type PlaySubscriptionEvent,
} from '../src/services/subscriptionIngestion.js';

const T0 = Date.UTC(2026, 8, 1, 0, 0, 0);

function event(overrides: Partial<PlaySubscriptionEvent> = {}): PlaySubscriptionEvent {
  return {
    uid: 'uid-1',
    purchaseToken: 'purchase-token-abcdefghij',
    productId: 'wishwell_plus_monthly',
    expiresAtMs: T0 + 30 * 24 * 3600_000,
    status: 'active',
    occurredAtMs: T0,
    ...overrides,
  };
}

function snapshot(data: Record<string, unknown> | undefined): BillingEventSnapshot {
  return { data: () => data };
}

describe('SKU_TO_PLAN', () => {
  it('maps all wishwell-plus SKUs onto the plus plan', () => {
    expect(SKU_TO_PLAN.wishwell_plus_monthly).toBe('wishwell-plus');
    expect(SKU_TO_PLAN.wishwell_plus_yearly).toBe('wishwell-plus');
    expect(SKU_TO_PLAN.wishwell_plus_test).toBe('wishwell-plus');
  });

  it('does not map arbitrary third-party SKUs', () => {
    expect(SKU_TO_PLAN.some_other_app_sku).toBeUndefined();
  });
});

describe('reducePlayEvent', () => {
  it('unknown SKUs never change the record (return previous untouched)', () => {
    const previous: AiSubscriptionRecord = {
      schemaVersion: AI_SCHEMA_VERSION,
      uid: 'uid-1',
      plan: 'wishwell-plus',
      status: 'active',
      billingProvider: 'play-billing',
      externalSubscriptionId: 'old',
      purchasedAtMs: T0 - 5000,
      expiresAtMs: T0 + 1000,
      cancelledAtMs: null,
      updatedAtMs: T0 - 5000,
    };
    expect(reducePlayEvent(previous, event({ productId: 'other-sku' }))).toBe(previous);
    expect(reducePlayEvent(null, event({ productId: 'other-sku' }))).toBeNull();
  });

  it('active purchase grants the plus plan with play-billing provenance', () => {
    const next = reducePlayEvent(null, event());
    expect(next).not.toBeNull();
    expect(next?.plan).toBe('wishwell-plus');
    expect(next?.status).toBe('active');
    expect(next?.billingProvider).toBe('play-billing');
    expect(next?.externalSubscriptionId).toBe('purchase-token-a'); // truncated token
    expect(next?.purchasedAtMs).toBe(T0);
    expect(next?.updatedAtMs).toBe(T0);
  });

  it('cancellation downgrades to free but keeps purchase history', () => {
    const active = reducePlayEvent(null, event());
    const cancelled = reducePlayEvent(active, event({
      status: 'cancelled',
      occurredAtMs: T0 + 10,
      expiresAtMs: null,
    }));
    expect(cancelled?.plan).toBe('free');
    expect(cancelled?.status).toBe('expired');
    expect(cancelled?.purchasedAtMs).toBe(T0); // preserved from previous
    expect(cancelled?.cancelledAtMs).toBe(T0 + 10);
  });

  it('past_due keeps the past_due status but drops the paid plan', () => {
    const active = reducePlayEvent(null, event());
    const pastDue = reducePlayEvent(active, event({
      status: 'past_due',
      occurredAtMs: T0 + 20,
    }));
    expect(pastDue?.plan).toBe('free');
    expect(pastDue?.status).toBe('past_due');
  });

  it('expiry in the past deactivates even an "active" notification', () => {
    const next = reducePlayEvent(null, event({
      expiresAtMs: T0 - 1, // already expired at event time
    }));
    expect(next?.plan).toBe('free');
    expect(next?.status).toBe('expired');
  });

  it('null expiry is treated as open-ended (stays active)', () => {
    const next = reducePlayEvent(null, event({ expiresAtMs: null }));
    expect(next?.plan).toBe('wishwell-plus');
    expect(next?.status).toBe('active');
  });
});

describe('applyPlayBillingEvent (trigger entry point)', () => {
  const validData = {
    productId: 'wishwell_plus_monthly',
    purchaseToken: 'tok-1234567890abcdef',
    status: 'active',
    occurredAtMs: T0,
    expiresAtMs: T0 + 1000,
  };

  async function runWith(
    data: Record<string, unknown> | undefined,
    previous: AiSubscriptionRecord | null,
  ): Promise<{
    outcome: 'applied' | 'ignored';
    written: AiSubscriptionRecord | null;
  }> {
    let written: AiSubscriptionRecord | null = null;
    const outcome = await applyPlayBillingEvent(
      (_uid, record) => {
        written = record;
        return Promise.resolve();
      },
      () => Promise.resolve(previous),
      'uid-1',
      data === undefined ? undefined : snapshot(data),
    );
    return { outcome, written };
  }

  it('ignores missing documents', async () => {
    const { outcome, written } = await runWith(undefined, null);
    expect(outcome).toBe('ignored');
    expect(written).toBeNull();
  });

  it.each([
    ['non-string productId', { ...validData, productId: 42 }],
    ['non-string purchaseToken', { ...validData, purchaseToken: null }],
    ['non-string status', { ...validData, status: 7 }],
    ['non-number occurredAtMs', { ...validData, occurredAtMs: 'soon' }],
    ['unsafe-integer occurredAtMs', { ...validData, occurredAtMs: Number.MAX_SAFE_INTEGER + 1 }],
    ['non-null number expiresAtMs', { ...validData, expiresAtMs: 'never' }],
    ['unknown status vocabulary', { ...validData, status: 'grace-period' }],
  ])('ignores malformed events (%s)', async (_name, data) => {
    const { outcome } = await runWith(data, null);
    expect(outcome).toBe('ignored');
  });

  it('applies a well-formed first event', async () => {
    const { outcome, written } = await runWith(validData, null);
    expect(outcome).toBe('applied');
    expect(written?.plan).toBe('wishwell-plus');
    expect(written?.updatedAtMs).toBe(T0);
  });

  it('ignores out-of-order / replayed notifications (updatedAtMs guard)', async () => {
    const previous: AiSubscriptionRecord = {
      schemaVersion: AI_SCHEMA_VERSION,
      uid: 'uid-1',
      plan: 'wishwell-plus',
      status: 'active',
      billingProvider: 'play-billing',
      externalSubscriptionId: 'tok-123456789',
      purchasedAtMs: T0 - 100,
      expiresAtMs: T0 + 5000,
      cancelledAtMs: null,
      updatedAtMs: T0, // same timestamp as incoming event → stale/replay
    };
    const { outcome } = await runWith(validData, previous);
    expect(outcome).toBe('ignored');
  });

  it('applies strictly-newer events over existing records', async () => {
    const previous: AiSubscriptionRecord = {
      schemaVersion: AI_SCHEMA_VERSION,
      uid: 'uid-1',
      plan: 'wishwell-plus',
      status: 'active',
      billingProvider: 'play-billing',
      externalSubscriptionId: 'tok-old',
      purchasedAtMs: T0 - 100,
      expiresAtMs: T0 + 5000,
      cancelledAtMs: null,
      updatedAtMs: T0 - 50,
    };
    const { outcome, written } = await runWith(validData, previous);
    expect(outcome).toBe('applied');
    expect(written?.updatedAtMs).toBe(T0);
    expect(written?.purchasedAtMs).toBe(T0 - 100); // preserved
  });

  it('treats absent expiresAtMs field as null (open-ended)', async () => {
    const { outcome, written } = await runWith(
      { ...validData, expiresAtMs: undefined },
      null,
    );
    expect(outcome).toBe('applied');
    expect(written?.expiresAtMs ?? null).toBeNull();
  });
});
