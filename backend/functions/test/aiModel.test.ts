/**
 * Unit tests for the pure AI entitlement domain model: plan quotas,
 * subscription projection, the entitlement gate, usage aggregation and
 * period keys. No Firebase, no networking — deterministic clocks only.
 */

import { describe, expect, it } from 'vitest';

import {
  AI_SCHEMA_VERSION,
  aggregateAiUsage,
  aiCapabilitiesForPlan,
  aiQuotaForPlan,
  decideAiEntitlement,
  projectAiEntitlement,
  usagePeriodKey,
  usageSnapshotFromTotals,
  type AiSubscriptionRecord,
  type AiUsageEntry,
} from '../src/domain/aiModel.js';

const NOW_MS = Date.UTC(2026, 8, 24, 12, 0, 0); // 2026-09-24T12:00:00Z

function record(overrides: Partial<AiSubscriptionRecord> = {}): AiSubscriptionRecord {
  return {
    schemaVersion: AI_SCHEMA_VERSION,
    uid: 'uid-1',
    plan: 'wishwell-plus',
    status: 'active',
    billingProvider: 'play-billing',
    externalSubscriptionId: 'token-prefix',
    purchasedAtMs: NOW_MS - 1000,
    expiresAtMs: NOW_MS + 30 * 24 * 3600_000,
    cancelledAtMs: null,
    updatedAtMs: NOW_MS - 1000,
    ...overrides,
  };
}

function entry(overrides: Partial<AiUsageEntry> = {}): AiUsageEntry {
  return {
    id: 'e-1',
    capability: 'message-drafting',
    provider: 'gemini-cloud',
    model: 'gemini-2.0-flash',
    inputTokens: 100,
    outputTokens: 50,
    estimatedCostMicros: 300,
    latencyMs: 42,
    createdAtMs: NOW_MS,
    civilDate: '2026-09-24',
    period: 'calendar-month',
    periodKey: '2026-09',
    ...overrides,
  };
}

describe('plan quotas & capabilities', () => {
  it('free plan has zero quota and no AI capabilities', () => {
    expect(aiQuotaForPlan('free')).toEqual({
      period: 'calendar-month',
      requestsPerDay: 0,
      requestsPerMonth: 0,
    });
    expect(aiCapabilitiesForPlan('free')).toEqual([]);
  });

  it('wishwell-plus grants message-drafting with daily/monthly limits', () => {
    expect(aiQuotaForPlan('wishwell-plus').requestsPerDay).toBe(50);
    expect(aiQuotaForPlan('wishwell-plus').requestsPerMonth).toBe(300);
    expect(aiCapabilitiesForPlan('wishwell-plus')).toContain('message-drafting');
  });
});

describe('projectAiEntitlement', () => {
  it('null record projects to disabled free tier', () => {
    const p = projectAiEntitlement(null, NOW_MS);
    expect(p.enabled).toBe(false);
    expect(p.plan).toBe('free');
    expect(p.capabilities).toEqual([]);
    expect(p.providers.every((x) => !x.available)).toBe(true);
    expect(p.renewsOn).toBeNull();
  });

  it('active plus record enables AI and advertises application-owned gemini', () => {
    const p = projectAiEntitlement(record(), NOW_MS);
    expect(p.enabled).toBe(true);
    expect(p.plan).toBe('wishwell-plus');
    expect(p.capabilities).toEqual(['message-drafting']);
    expect(p.providers).toEqual([
      {
        provider: 'gemini-cloud',
        authorizationMode: 'application-owned',
        available: true,
      },
    ]);
    expect(p.renewsOn).toBe('2026-10-24');
  });

  it.each(['cancelled', 'expired', 'past_due'] as const)(
    '%s status disables AI',
    (status) => {
      expect(projectAiEntitlement(record({ status }), NOW_MS).enabled).toBe(false);
    },
  );

  it('trialing status keeps AI enabled', () => {
    expect(
      projectAiEntitlement(record({ status: 'trialing' }), NOW_MS).enabled,
    ).toBe(true);
  });

  it('expired expiry timestamp disables AI even when status says active', () => {
    const p = projectAiEntitlement(
      record({ expiresAtMs: NOW_MS - 1 }),
      NOW_MS,
    );
    expect(p.enabled).toBe(false);
  });

  it('null expiry is treated as non-expiring (still enabled)', () => {
    const p = projectAiEntitlement(record({ expiresAtMs: null }), NOW_MS);
    expect(p.enabled).toBe(true);
    expect(p.renewsOn).toBeNull();
  });
});

describe('decideAiEntitlement', () => {
  const plus = projectAiEntitlement(record(), NOW_MS);

  it('blocks unknown-capability / disabled entitlements first', () => {
    const disabled = projectAiEntitlement(null, NOW_MS);
    expect(
      decideAiEntitlement(disabled, { period: 'calendar-month', usedToday: 0, usedInPeriod: 0 }, 'message-drafting'),
    ).toEqual({ kind: 'blocked', reason: 'ai-subscription-required' });
  });

  it('blocks on usage-period mismatch (defensive guard against stored data)', () => {
    // Today both sides are typed 'calendar-month'; simulate a legacy/corrupt
    // usage document written under a different period vocabulary.
    const staleUsage = {
      period: 'rolling-30d',
      usedToday: 0,
      usedInPeriod: 0,
    } as unknown as Parameters<typeof decideAiEntitlement>[1];
    expect(
      decideAiEntitlement(plus, staleUsage, 'message-drafting'),
    ).toEqual({ kind: 'blocked', reason: 'ai-usage-period-mismatch' });
  });

  it('matching period with zero usage is allowed', () => {
    expect(
      decideAiEntitlement(
        plus,
        { period: 'calendar-month', usedToday: 0, usedInPeriod: 0 },
        'message-drafting',
      ).kind,
    ).toBe('allowed');
  });

  it('blocks monthly exhaustion at the boundary (>= limit)', () => {
    expect(
      decideAiEntitlement(plus, { period: 'calendar-month', usedToday: 0, usedInPeriod: 300 }, 'message-drafting'),
    ).toEqual({ kind: 'blocked', reason: 'ai-quota-exhausted' });
  });

  it('blocks daily exhaustion at the boundary (>= limit)', () => {
    expect(
      decideAiEntitlement(plus, { period: 'calendar-month', usedToday: 50, usedInPeriod: 100 }, 'message-drafting'),
    ).toEqual({ kind: 'blocked', reason: 'ai-quota-exhausted' });
  });

  it('allows within both limits and echoes the quota', () => {
    const d = decideAiEntitlement(
      plus,
      { period: 'calendar-month', usedToday: 49, usedInPeriod: 299 },
      'message-drafting',
    );
    expect(d.kind).toBe('allowed');
    if (d.kind === 'allowed') {
      expect(d.quota).toEqual(plus.quota);
    }
  });
});

describe('usagePeriodKey / civil date behaviour', () => {
  it('formats UTC calendar-month keys', () => {
    expect(usagePeriodKey(Date.UTC(2026, 0, 1))).toBe('2026-01');
    expect(usagePeriodKey(NOW_MS)).toBe('2026-09');
  });
});

describe('aggregateAiUsage', () => {
  it('ignores entries from other periods', () => {
    const totals = aggregateAiUsage(
      [entry(), entry({ id: 'old', periodKey: '2026-08', civilDate: '2026-08-31' })],
      NOW_MS,
    );
    expect(totals.usedInPeriod).toBe(1);
    expect(totals.usedToday).toBe(1);
    expect(totals.tokensInPeriod).toBe(150);
    expect(totals.estimatedCostMicrosInPeriod).toBe(300);
  });

  it('counts same-period entries from previous days in period but not today', () => {
    const totals = aggregateAiUsage(
      [entry(), entry({ id: 'yesterday', civilDate: '2026-09-23' })],
      NOW_MS,
    );
    expect(totals.usedInPeriod).toBe(2);
    expect(totals.usedToday).toBe(1);
  });

  it('empty ledger aggregates to zeroes', () => {
    expect(aggregateAiUsage([], NOW_MS)).toEqual({
      usedToday: 0,
      usedInPeriod: 0,
      tokensInPeriod: 0,
      estimatedCostMicrosInPeriod: 0,
    });
  });
});

describe('usageSnapshotFromTotals', () => {
  it('clamps negative counters to zero', () => {
    const snap = usageSnapshotFromTotals(
      { usedToday: -5, usedInPeriod: -100, tokensInPeriod: 0, estimatedCostMicrosInPeriod: 0 },
      NOW_MS,
    );
    expect(snap).toEqual({ period: 'calendar-month', usedToday: 0, usedInPeriod: 0 });
  });

  it('passes positive totals through unchanged', () => {
    const snap = usageSnapshotFromTotals(
      { usedToday: 3, usedInPeriod: 47, tokensInPeriod: 10, estimatedCostMicrosInPeriod: 10 },
      NOW_MS,
    );
    expect(snap.usedToday).toBe(3);
    expect(snap.usedInPeriod).toBe(47);
  });
});
