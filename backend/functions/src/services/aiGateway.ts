/**
 * AI Gateway service — entitlement gate, quota enforcement, provider routing
 * and usage accounting for WishWell's server-side AI execution.
 *
 * Pipeline (see "Ai gateway architecture.md"):
 *   authenticate → checkEntitlement → reserveQuota → route → execute →
 *   recordUsage → return
 *
 * Invariants enforced here:
 *  - The ONLY thing that enables AI is WishWell's own subscription record
 *    (`users/{uid}/meta/aiEntitlement`), written exclusively by billing
 *    ingestion. External/provider subscriptions are never consulted.
 *  - Quota reservation is transactional against a per-account monthly
 *    summary document, so concurrent calls cannot exceed the plan limits.
 *  - A global monthly cost budget circuit-breaks cloud generation when
 *    exceeded (fail-closed), protecting against runaway spend.
 *  - Prompt text is transient: it is never persisted, logged, or echoed in
 *    errors. The ledger stores token counts and cost estimates only.
 */

import { randomUUID } from 'node:crypto';

import type {
  Firestore,
  Transaction,
} from 'firebase-admin/firestore';

import {
  decideAiEntitlement,
  projectAiEntitlement,
  usagePeriodKey,
  AI_SCHEMA_VERSION,
  type AIRequest,
  type AIResult,
  type AiCapabilityId,
  type AiEntitlementDecision,
  type AiEntitlementProjection,
  type AiErrorCode,
  type AiProviderId,
  type AiSubscriptionRecord,
  type AiUsageEntry,
} from '../domain/aiModel.js';
import {
  AIExecutionRouter,
  AiProviderError,
  asAiProvider,
  estimateCostMicros,
  type AIProvider,
  type AiGenerationRequest,
  type AiProviderAdapter,
} from '../domain/aiProviders.js';
import {
  aiEntitlementPath,
  aiGlobalBudgetPath,
  aiUsageLedger,
  aiUsageSummary,
} from '../persistence/paths.js';

export class GatewayBlockedError extends Error {
  readonly errorCode: AiErrorCode;

  constructor(
    readonly reason:
      | 'ai-subscription-required'
      | 'ai-quota-exhausted'
      | 'ai-usage-period-mismatch'
      | 'ai-provider-unavailable'
      | 'ai-budget-exhausted',
    errorCode?: AiErrorCode,
  ) {
    super(reason);
    this.name = 'GatewayBlockedError';
    this.errorCode =
      errorCode ??
      (reason === 'ai-subscription-required'
        ? 'AI_SUBSCRIPTION_REQUIRED'
        : reason === 'ai-quota-exhausted' || reason === 'ai-budget-exhausted'
        ? 'AI_QUOTA_EXCEEDED'
        : reason === 'ai-usage-period-mismatch'
        ? 'AI_CONFIGURATION_ERROR'
        : 'AI_PROVIDER_UNAVAILABLE');
  }
}

export interface GenerateOutcome {
  readonly result: AIResult;
  readonly usageEntryId: string;
  readonly remainingToday: number;
  readonly remainingInPeriod: number;
}

interface SummaryDoc {
  readonly requests: number;
  readonly tokens: number;
  readonly costMicros: number;
  /** Day counters keyed by UTC civil date `YYYY-MM-DD`. */
  readonly days: Readonly<Record<string, number>>;
}

const EMPTY_SUMMARY: SummaryDoc = {
  requests: 0,
  tokens: 0,
  costMicros: 0,
  days: {},
};

function asNumber(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? value
    : 0;
}

function readSummary(data: Record<string, unknown> | undefined): SummaryDoc {
  if (data === undefined) {
    return EMPTY_SUMMARY;
  }
  const rawDays = data.days;
  const days: Record<string, number> = {};
  if (typeof rawDays === 'object' && rawDays !== null) {
    for (const [key, value] of Object.entries(rawDays as Record<string, unknown>)) {
      days[key] = asNumber(value);
    }
  }
  return {
    requests: asNumber(data.requests),
    tokens: asNumber(data.tokens),
    costMicros: asNumber(data.costMicros),
    days,
  };
}

function readSubscriptionRecord(
  uid: string,
  data: Record<string, unknown> | undefined,
): AiSubscriptionRecord | null {
  if (data === undefined) {
    return null;
  }
  const plan = data.plan;
  const status = data.status;
  if (plan !== 'free' && plan !== 'wishwell-plus') {
    return null;
  }
  if (
    status !== 'active' &&
    status !== 'trialing' &&
    status !== 'past_due' &&
    status !== 'cancelled' &&
    status !== 'expired' &&
    status !== 'revoked'
  ) {
    return null;
  }
  const expiresAtMs = data.expiresAtMs;
  const purchasedAtMs = data.purchasedAtMs;
  const cancelledAtMs = data.cancelledAtMs;
  const externalId = data.externalSubscriptionId;
  const billingProvider = data.billingProvider;
  return {
    schemaVersion: AI_SCHEMA_VERSION,
    uid,
    plan,
    status,
    billingProvider: typeof billingProvider === 'string' ? billingProvider : 'unknown',
    externalSubscriptionId:
      typeof externalId === 'string' ? externalId : null,
    purchasedAtMs:
      typeof purchasedAtMs === 'number' && Number.isSafeInteger(purchasedAtMs)
        ? purchasedAtMs
        : null,
    expiresAtMs:
      typeof expiresAtMs === 'number' && Number.isSafeInteger(expiresAtMs)
        ? expiresAtMs
        : null,
    cancelledAtMs:
      typeof cancelledAtMs === 'number' && Number.isSafeInteger(cancelledAtMs)
        ? cancelledAtMs
        : null,
    updatedAtMs: asNumber(data.updatedAtMs),
  };
}

function civilDateUTC(nowMs: number): string {
  return new Date(nowMs).toISOString().slice(0, 10);
}

export interface AiGatewayOptions {
  /** Monthly global cloud-cost ceiling in micro-rupees (₹ × 1e6). */
  readonly globalMonthlyBudgetMicros: number;
  readonly applicationId?: string;
  readonly priorityOrder?: readonly AiProviderId[];
  readonly nowMs?: () => number;
  readonly idFactory?: () => string;
}

export class AiGatewayService {
  private readonly router: AIExecutionRouter;

  constructor(
    private readonly db: Firestore,
    adapters: ReadonlyMap<string, AIProvider | AiProviderAdapter>,
    private readonly options: AiGatewayOptions,
  ) {
    const normalizedAdapters = new Map<string, AIProvider>();
    for (const [id, adapter] of adapters.entries()) {
      normalizedAdapters.set(id, asAiProvider(adapter));
    }
    this.router = new AIExecutionRouter(
      normalizedAdapters,
      this.options.priorityOrder,
    );
  }

  private nowMs(): number {
    return this.options.nowMs?.() ?? Date.now();
  }

  private newId(): string {
    return this.options.idFactory?.() ?? randomUUID();
  }

  /** Reads WishWell's own subscription record and projects the entitlement. */
  async entitlementFor(uid: string): Promise<AiEntitlementProjection> {
    const snap = await aiEntitlementPath(this.db, uid).get();
    const record = readSubscriptionRecord(
      uid,
      snap.data(),
    );
    return projectAiEntitlement(record, this.nowMs());
  }

  /** Pure decision helper exposed for tests and clients' pre-flight checks. */
  decide(
    entitlement: AiEntitlementProjection,
    usedToday: number,
    usedInPeriod: number,
    capability: AiCapabilityId,
  ): AiEntitlementDecision {
    return decideAiEntitlement(
      entitlement,
      { period: entitlement.quota.period, usedToday, usedInPeriod },
      capability,
    );
  }

  /**
   * Full generate pipeline. Throws GatewayBlockedError with a stable reason
   * code on any gate failure; provider failures map to
   * `ai-provider-unavailable` without leaking upstream detail.
   */
  async generate(
    uid: string,
    request: AIRequest | AiGenerationRequest,
  ): Promise<GenerateOutcome> {
    const nowMs = this.nowMs();
    const entitlement = await this.entitlementFor(uid);
    const periodKey = usagePeriodKey(nowMs);
    const today = civilDateUTC(nowMs);

    const summaryRef = aiUsageSummary(this.db, uid, periodKey);
    const budgetRef = aiGlobalBudgetPath(this.db);

    // Phase 1: transactional reservation (quota + global budget).
    await this.db.runTransaction(async (tx) => {
      const [summarySnap, budgetSnap] = await Promise.all([
        tx.get(summaryRef),
        tx.get(budgetRef),
      ]);
      const summary = readSummary(
        summarySnap.data(),
      );
      const usedToday = summary.days[today] ?? 0;
      const decision = decideAiEntitlement(
        entitlement,
        { period: 'calendar-month', usedToday, usedInPeriod: summary.requests },
        request.capability,
      );
      if (decision.kind === 'blocked') {
        throw new GatewayBlockedError(
          decision.reason,
          decision.reason === 'ai-subscription-required'
            ? 'AI_SUBSCRIPTION_REQUIRED'
            : decision.reason === 'ai-quota-exhausted'
            ? 'AI_QUOTA_EXCEEDED'
            : 'AI_CONFIGURATION_ERROR',
        );
      }

      const budget = readSummary(
        budgetSnap.data(),
      );
      if (budget.costMicros >= this.options.globalMonthlyBudgetMicros) {
        throw new GatewayBlockedError('ai-budget-exhausted', 'AI_QUOTA_EXCEEDED');
      }

      tx.set(
        summaryRef,
        {
          requests: summary.requests + 1,
          tokens: summary.tokens,
          costMicros: summary.costMicros,
          days: { ...summary.days, [today]: usedToday + 1 },
          periodKey,
          updatedAtMs: nowMs,
        },
        { merge: true },
      );
    });

    // Phase 2: Route via AIExecutionRouter outside transaction (only after entitlement validated)
    let adapter: AIProvider;
    try {
      adapter = await this.router.route(entitlement, request);
    } catch (routeError) {
      await this.compensateReservation(uid, periodKey, today).catch(
        () => undefined,
      );
      if (
        routeError instanceof AiProviderError &&
        routeError.message.includes('AI_SUBSCRIPTION_REQUIRED')
      ) {
        throw new GatewayBlockedError('ai-subscription-required', 'AI_SUBSCRIPTION_REQUIRED');
      }
      throw new GatewayBlockedError('ai-provider-unavailable', 'AI_PROVIDER_UNAVAILABLE');
    }

    const startedAt = Date.now();
    let result: AIResult;
    try {
      result = await adapter.generate(request);
    } catch (error) {
      // Compensate: release the reservation so failed calls don't burn quota.
      await this.compensateReservation(uid, periodKey, today).catch(
        () => undefined,
      );
      throw error instanceof AiProviderError
        ? new GatewayBlockedError('ai-provider-unavailable', 'AI_PROVIDER_UNAVAILABLE')
        : new GatewayBlockedError('ai-provider-unavailable', 'AI_PROVIDER_UNAVAILABLE');
    }
    const latencyMs = Math.max(0, Date.now() - startedAt);

    // Phase 3: commit actual usage to the ledger + summary + global budget.
    const entryId = this.newId();
    const finalCostMicros =
      result.estimatedCostMicros > 0
        ? result.estimatedCostMicros
        : estimateCostMicros(
            result.model,
            result.inputTokens,
            result.outputTokens,
          );
    await this.commitUsage(uid, periodKey, today, {
      id: entryId,
      capability: request.capability,
      provider: result.provider,
      model: result.model,
      inputTokens: result.inputTokens,
      outputTokens: result.outputTokens,
      estimatedCostMicros: finalCostMicros,
      latencyMs,
      createdAtMs: nowMs,
      civilDate: today,
      period: 'calendar-month',
      periodKey,
    });

    const after = readSummary(
      (await summaryRef.get()).data(),
    );
    return {
      result,
      usageEntryId: entryId,
      remainingToday: Math.max(
        0,
        entitlement.quota.requestsPerDay - (after.days[today] ?? 0),
      ),
      remainingInPeriod: Math.max(
        0,
        entitlement.quota.requestsPerMonth - after.requests,
      ),
    };
  }

  private async compensateReservation(
    uid: string,
    periodKey: string,
    today: string,
  ): Promise<void> {
    const summaryRef = aiUsageSummary(this.db, uid, periodKey);
    await this.db.runTransaction(async (tx: Transaction) => {
      const snap = await tx.get(summaryRef);
      const summary = readSummary(
        snap.data(),
      );
      const usedToday = summary.days[today] ?? 0;
      tx.set(
        summaryRef,
        {
          requests: Math.max(0, summary.requests - 1),
          tokens: summary.tokens,
          costMicros: summary.costMicros,
          days: { ...summary.days, [today]: Math.max(0, usedToday - 1) },
          updatedAtMs: Date.now(),
        },
        { merge: true },
      );
    });
  }

  private async commitUsage(
    uid: string,
    periodKey: string,
    today: string,
    entry: AiUsageEntry,
  ): Promise<void> {
    const ledgerRef = aiUsageLedger(this.db, uid).doc(entry.id);
    const summaryRef = aiUsageSummary(this.db, uid, periodKey);
    const budgetRef = aiGlobalBudgetPath(this.db);
    await this.db.runTransaction(async (tx) => {
      const [summarySnap, budgetSnap] = await Promise.all([
        tx.get(summaryRef),
        tx.get(budgetRef),
      ]);
      const summary = readSummary(
        summarySnap.data(),
      );
      const budget = readSummary(
        budgetSnap.data(),
      );
      const usedToday = summary.days[today] ?? 0;
      tx.create(ledgerRef, { ...entry });
      tx.set(
        summaryRef,
        {
          requests: summary.requests,
          tokens: summary.tokens + entry.inputTokens + entry.outputTokens,
          costMicros: summary.costMicros + entry.estimatedCostMicros,
          days: { ...summary.days, [today]: usedToday },
          updatedAtMs: entry.createdAtMs,
        },
        { merge: true },
      );
      tx.set(
        budgetRef,
        {
          periodKey,
          requests: budget.requests + 1,
          tokens: budget.tokens + entry.inputTokens + entry.outputTokens,
          costMicros: budget.costMicros + entry.estimatedCostMicros,
          days: budget.days,
          updatedAtMs: entry.createdAtMs,
        },
        { merge: true },
      );
    });
  }
}
