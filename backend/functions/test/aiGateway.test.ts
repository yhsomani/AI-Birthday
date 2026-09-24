/**
 * Unit tests for the AI Gateway service: entitlement projection reads,
 * transactional quota reservation, provider failure compensation,
 * usage commit accounting and the global budget circuit breaker.
 *
 * Firestore is replaced with a minimal in-memory fake (documents + merge-set
 * semantics + runTransaction) so these run without the emulator.
 */

import { describe, expect, it } from 'vitest';

import type { Firestore } from 'firebase-admin/firestore';

import { AI_SCHEMA_VERSION, type AiSubscriptionRecord } from '../src/domain/aiModel.js';
import {
  AiProviderError,
  StubProviderAdapter,
  type AiGenerationRequest,
  type AiGenerationResult,
  type AiProviderAdapter,
} from '../src/domain/aiProviders.js';
import { AiGatewayService, GatewayBlockedError } from '../src/services/aiGateway.js';

// --- In-memory Firestore fake ----------------------------------------------------

type Data = Record<string, unknown>;

class FakeDoc {
  constructor(public data: Data | undefined) {}
}

class FakeDb {
  readonly docs = new Map<string, FakeDoc>();

  collection(name: string): unknown {
    const docs = this.docs;
    return {
      doc(id: string): unknown {
        const base = `${name}/${id}`;
        function ref(path: string): unknown {
          return {
            path,
            get: () => Promise.resolve({
              exists: docs.get(path)?.data !== undefined,
              data: () => docs.get(path)?.data,
            }),
            collection(child: string) {
              return {
                doc(childId: string) {
                  return ref(`${path}/${child}/${childId}`);
                },
              };
            },
          };
        }
        return ref(base);
      },
    };
  }

  async runTransaction(work: (tx: unknown) => Promise<void>): Promise<void> {
    const tx = {
      get: (ref: { path: string }) => Promise.resolve({
        data: () => this.docs.get(ref.path)?.data,
      }),
      set: (ref: { path: string }, data: Data, opts?: { merge?: boolean }) => {
        const prev = this.docs.get(ref.path)?.data;
        if (opts?.merge === true && prev !== undefined) {
          this.docs.set(ref.path, new FakeDoc({ ...prev, ...data }));
        } else {
          this.docs.set(ref.path, new FakeDoc({ ...(prev ?? {}), ...data }));
        }
      },
      create: (ref: { path: string }, data: Data) => {
        if (this.docs.get(ref.path)?.data !== undefined) {
          throw new Error('ALREADY_EXISTS');
        }
        this.docs.set(ref.path, new FakeDoc({ ...data }));
      },
    };
    await work(tx);
  }

  asFirestore(): Firestore {
    return this as unknown as Firestore;
  }
}

// --- Fixtures ---------------------------------------------------------------------

const NOW_MS = Date.UTC(2026, 8, 24, 12, 0, 0);
const PERIOD = '2026-09';
const TODAY = '2026-09-24';
const UID = 'uid-1';

function entitlementRecord(overrides: Partial<AiSubscriptionRecord> = {}): Data {
  const record: AiSubscriptionRecord = {
    schemaVersion: AI_SCHEMA_VERSION,
    uid: UID,
    plan: 'wishwell-plus',
    status: 'active',
    billingProvider: 'play-billing',
    externalSubscriptionId: 'tok',
    purchasedAtMs: NOW_MS - 1000,
    expiresAtMs: NOW_MS + 30 * 24 * 3600_000,
    cancelledAtMs: null,
    updatedAtMs: NOW_MS - 1000,
    ...overrides,
  };
  return { ...record };
}

function request(overrides: Partial<AiGenerationRequest> = {}): AiGenerationRequest {
  return {
    capability: 'message-drafting',
    prompt: 'Draft a warm birthday wish for Ana.',
    systemInstruction: 'You write concise birthday wishes.',
    maxOutputTokens: 200,
    temperature: 0.7,
    ...overrides,
  };
}

class FixedAdapter implements AiProviderAdapter {
  calls = 0;
  constructor(private readonly result: Partial<AiGenerationResult> = {}) {}
  readonly provider = 'gemini-cloud' as const;
  readonly model = 'gemini-2.0-flash';
  generate(req: AiGenerationRequest): Promise<AiGenerationResult> {
    void req;
    this.calls += 1;
    return Promise.resolve({
      provider: this.provider,
      model: this.model,
      text: 'Happy birthday Ana!',
      inputTokens: 1000,
      outputTokens: 500,
      estimatedCostMicros: 0, // force fallback to estimateCostMicros()
      ...this.result,
    });
  }
}

class ThrowingAdapter implements AiProviderAdapter {
  calls = 0;
  readonly provider = 'gemini-cloud' as const;
  readonly model = 'stub';
  generate(req: AiGenerationRequest): Promise<AiGenerationResult> {
    void req;
    this.calls += 1;
    return Promise.reject(new AiProviderError('unavailable', 'boom'));
  }
}

function makeService(
  db: FakeDb,
  adapter: AiProviderAdapter,
  budgetMicros = 10_000_000,
): AiGatewayService {
  return new AiGatewayService(
    db.asFirestore(),
    new Map([[adapter.provider, adapter]]),
    {
      globalMonthlyBudgetMicros: budgetMicros,
      nowMs: () => NOW_MS,
      idFactory: () => 'entry-fixed-id',
    },
  );
}

function summaryPath(): string {
  return `users/${UID}/aiUsage/summary-${PERIOD}`;
}

function ledgerPath(id: string): string {
  return `users/${UID}/aiUsage/${id}`;
}

// --- Tests -------------------------------------------------------------------------

describe('AiGatewayService.entitlementFor', () => {
  it('projects stored subscription records', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    const svc = makeService(db, new FixedAdapter());
    const p = await svc.entitlementFor(UID);
    expect(p.enabled).toBe(true);
    expect(p.plan).toBe('wishwell-plus');
  });

  it('degrades to free/disabled when the document is missing', async () => {
    const db = new FakeDb();
    const svc = makeService(db, new FixedAdapter());
    const p = await svc.entitlementFor(UID);
    expect(p.enabled).toBe(false);
    expect(p.plan).toBe('free');
  });

  it('rejects corrupt stored plans/statuses defensively', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc({
      ...entitlementRecord(),
      plan: 'diamond',
    }));
    const svc = makeService(db, new FixedAdapter());
    expect((await svc.entitlementFor(UID)).enabled).toBe(false);
  });

  it('coerces malformed numeric fields to safe defaults', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc({
      schemaVersion: AI_SCHEMA_VERSION,
      uid: UID,
      plan: 'wishwell-plus',
      status: 'active',
      billingProvider: 42,
      externalSubscriptionId: {},
      purchasedAtMs: 'yesterday',
      expiresAtMs: null,
      cancelledAtMs: null,
      updatedAtMs: -5,
    }));
    const svc = makeService(db, new FixedAdapter());
    const p = await svc.entitlementFor(UID);
    expect(p.enabled).toBe(true); // expiresAtMs null → non-expiring
  });
});

describe('AiGatewayService.decide (pure helper)', () => {
  it('mirrors decideAiEntitlement boundaries', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    const svc = makeService(db, new FixedAdapter());
    const ent = await svc.entitlementFor(UID);
    expect(svc.decide(ent, 0, 0, 'message-drafting').kind).toBe('allowed');
    expect(svc.decide(ent, 50, 0, 'message-drafting')).toEqual({
      kind: 'blocked',
      reason: 'ai-quota-exhausted',
    });
    expect(svc.decide(ent, 0, 300, 'message-drafting')).toEqual({
      kind: 'blocked',
      reason: 'ai-quota-exhausted',
    });
  });
});

describe('AiGatewayService.generate — happy path', () => {
  it('reserves, executes, commits ledger + summary + global budget', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    const adapter = new FixedAdapter();
    const svc = makeService(db, adapter);

    const outcome = await svc.generate(UID, request());

    expect(adapter.calls).toBe(1);
    expect(outcome.usageEntryId).toBe('entry-fixed-id');
    expect(outcome.result.text).toContain('birthday');

    // Ledger entry persisted with accounting fields (never the prompt text).
    const ledger = db.docs.get(ledgerPath('entry-fixed-id'))?.data;
    expect(ledger).toBeDefined();
    expect(ledger?.capability).toBe('message-drafting');
    expect(ledger?.estimatedCostMicros).toBeGreaterThan(0);
    expect(JSON.stringify(ledger)).not.toContain('Ana'); // privacy invariant

    // Summary counted the request once and the tokens/cost committed.
    const summary = db.docs.get(summaryPath())?.data;
    expect(summary?.requests).toBe(1);
    expect((summary?.days as Record<string, number>)[TODAY]).toBe(1);
    expect(summary?.tokens).toBe(1500);
    expect(summary?.costMicros).toBe(ledger?.estimatedCostMicros);

    // Global budget accrued the same cost.
    const budget = db.docs.get('aiGlobalBudget/current')?.data;
    expect(budget?.requests).toBe(1);
    expect(budget?.costMicros).toBe(ledger?.estimatedCostMicros);

    // Remaining counters reflect post-commit state.
    expect(outcome.remainingToday).toBe(49);
    expect(outcome.remainingInPeriod).toBe(299);
  });

  it('uses adapter-reported cost when > 0 instead of re-estimating', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    const adapter = new FixedAdapter({ estimatedCostMicros: 123 });
    const svc = makeService(db, adapter);
    const outcome = await svc.generate(UID, request());
    const ledger = db.docs.get(ledgerPath(outcome.usageEntryId))?.data;
    expect(ledger?.estimatedCostMicros).toBe(123);
  });
});

describe('AiGatewayService.generate — gates', () => {
  it('blocks unentitled users before touching the provider', async () => {
    const db = new FakeDb();
    const adapter = new FixedAdapter();
    const svc = makeService(db, adapter);
    await expect(svc.generate(UID, request())).rejects.toMatchObject({
      reason: 'ai-subscription-required',
    });
    expect(adapter.calls).toBe(0);
    // No reservation leaked for blocked calls.
    expect(db.docs.get(summaryPath())).toBeUndefined();
  });

  it('blocks at the monthly quota boundary inside the transaction', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    db.docs.set(summaryPath(), new FakeDoc({
      requests: 300,
      tokens: 0,
      costMicros: 0,
      days: {},
    }));
    const svc = makeService(db, new FixedAdapter());
    await expect(svc.generate(UID, request())).rejects.toBeInstanceOf(GatewayBlockedError);
    await expect(svc.generate(UID, request())).rejects.toMatchObject({
      reason: 'ai-quota-exhausted',
    });
  });

  it('blocks at the daily quota boundary', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    db.docs.set(summaryPath(), new FakeDoc({
      requests: 10,
      tokens: 0,
      costMicros: 0,
      days: { [TODAY]: 50 },
    }));
    const svc = makeService(db, new FixedAdapter());
    await expect(svc.generate(UID, request())).rejects.toMatchObject({
      reason: 'ai-quota-exhausted',
    });
  });

  it('circuit-breaks when the global monthly budget is exhausted', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    db.docs.set('aiGlobalBudget/current', new FakeDoc({
      requests: 999,
      tokens: 999,
      costMicros: 10_000_000,
      days: {},
    }));
    const adapter = new FixedAdapter();
    const svc = makeService(db, adapter, 10_000_000);
    await expect(svc.generate(UID, request())).rejects.toMatchObject({
      reason: 'ai-budget-exhausted',
    });
    expect(adapter.calls).toBe(0);
  });

  it('maps missing adapters to ai-provider-unavailable after reserving', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    const stub = new StubProviderAdapter('on-device'); // registered under wrong id
    const svc = makeService(db, stub);
    await expect(svc.generate(UID, request())).rejects.toMatchObject({
      reason: 'ai-provider-unavailable',
    });
  });
});

describe('AiGatewayService.generate — failure compensation', () => {
  it('releases the reservation when the provider throws', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    const adapter = new ThrowingAdapter();
    const svc = makeService(db, adapter);

    await expect(svc.generate(UID, request())).rejects.toMatchObject({
      reason: 'ai-provider-unavailable',
    });
    expect(adapter.calls).toBe(1);

    // Reservation incremented then compensated back down; no ledger entry.
    const summary = db.docs.get(summaryPath())?.data;
    expect(summary?.requests).toBe(0);
    expect((summary?.days as Record<string, number>)[TODAY]).toBe(0);
    expect(db.docs.get(ledgerPath('entry-fixed-id'))).toBeUndefined();
  });

  it('non-AiProviderError failures still map to provider-unavailable', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    const evil: AiProviderAdapter = {
      provider: 'gemini-cloud',
      model: 'evil',
      generate: () => Promise.reject(new Error('unexpected')),
    };
    const svc = makeService(db, evil);
    await expect(svc.generate(UID, request())).rejects.toBeInstanceOf(GatewayBlockedError);
  });
});

describe('readSummary robustness (via generate on corrupt data)', () => {
  it('treats negative/garbage counters as zero and still allows the call', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    db.docs.set(summaryPath(), new FakeDoc({
      requests: -7,
      tokens: 'NaN-ish',
      costMicros: undefined,
      days: { [TODAY]: -3, other: 'x' },
    }));
    const svc = makeService(db, new FixedAdapter());
    const outcome = await svc.generate(UID, request());
    // Corrupt data read as zeros → first successful request of the month.
    expect(outcome.remainingInPeriod).toBe(299);
    expect(outcome.remainingToday).toBe(49);
  });
});

describe('AiGatewayService.generate — defensive edges', () => {
  it('falls back to gemini-cloud routing when no provider is marked available', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc({
      ...entitlementRecord(),
      // Stale/corrupt record without a providers array → projection defaults.
      providers: undefined,
    }));
    const adapter = new FixedAdapter();
    const svc = makeService(db, adapter);
    const outcome = await svc.generate(UID, request());
    expect(adapter.calls).toBe(1);
    expect(outcome.result.provider).toBe('gemini-cloud');
  });

  it('never lets a compensation failure mask the original provider error', async () => {
    const db = new FakeDb();
    db.docs.set(`users/${UID}/meta/aiEntitlement`, new FakeDoc(entitlementRecord()));
    const evil: AiProviderAdapter = {
      provider: 'gemini-cloud',
      model: 'evil',
      generate: () => Promise.reject(new Error('unexpected')),
    };
    const svc = makeService(db, evil);
    // Poison the summary AFTER Phase-1 reservation so the compensating
    // transaction itself throws — the caller must still see the stable
    // GatewayBlockedError, not the internal corruption error.
    const origRunTransaction = db.runTransaction.bind(db);
    let poisoned = false;
    db.runTransaction = (work: (tx: unknown) => Promise<void>) => {
      if (poisoned) {
        return Promise.reject(new Error('compensation failed'));
      }
      return origRunTransaction(work);
    };
    // Run Phase 1 normally, then poison before Phase 2 executes.
    const origDocsSet = db.docs.set.bind(db.docs);
    db.docs.set = (key: string, value: FakeDoc) => {
      if (key === summaryPath()) {
        poisoned = true;
      }
      return origDocsSet(key, value);
    };
    await expect(svc.generate(UID, request())).rejects.toMatchObject({
      reason: 'ai-provider-unavailable',
    });
  });
});
