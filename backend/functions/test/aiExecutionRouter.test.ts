import { describe, expect, it } from 'vitest';

import {
  aiQuotaForPlan,
  decideAiEntitlement,
  isCachedEntitlementValid,
  projectAiEntitlement,
  AI_SCHEMA_VERSION,
  OFFLINE_ENTITLEMENT_GRACE_PERIOD_MS,
  type AiEntitlementProjection,
  type AIRequest,
  type AiSubscriptionRecord,
  type CachedAiEntitlement,
} from '../src/domain/aiModel.js';
import {
  AIExecutionRouter,
  LocalAIProvider,
  OnDeviceAIProvider,
  StubProviderAdapter,
  UserGeminiProvider,
  synthesizeBirthdayMessage,
  type AIProvider,
} from '../src/domain/aiProviders.js';
import {
  reducePlayEvent,
  reduceStripeEvent,
} from '../src/services/subscriptionIngestion.js';

function activeEntitlement(
  overrides: Partial<AiEntitlementProjection> = {},
): AiEntitlementProjection {
  return {
    enabled: true,
    plan: 'wishwell-plus',
    capabilities: ['message-drafting'],
    quota: aiQuotaForPlan('wishwell-plus'),
    providers: [
      {
        provider: 'gemini-cloud',
        authorizationMode: 'application-owned',
        available: true,
      },
    ],
    renewsOn: '2026-10-24',
    ...overrides,
  };
}

function inactiveEntitlement(
  overrides: Partial<AiEntitlementProjection> = {},
): AiEntitlementProjection {
  return {
    enabled: false,
    plan: 'free',
    capabilities: [],
    quota: aiQuotaForPlan('free'),
    providers: [
      {
        provider: 'gemini-cloud',
        authorizationMode: 'application-owned',
        available: false,
      },
    ],
    renewsOn: null,
    ...overrides,
  };
}

const sampleRequest: AIRequest = {
  capability: 'message-drafting',
  input: {
    recipientDisplayName: 'Priya',
    tone: 'warm',
    relationshipHint: 'friend',
    language: 'en',
  },
  prompt: 'Write a birthday message for Priya. Tone: warm.',
};

describe('AIExecutionRouter — Absolute Subscription Invariant', () => {
  it('MANDATORY REGRESSION TEST: NO SUBSCRIPTION + LOCAL AI = BLOCKED', async () => {
    const local = new LocalAIProvider();
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([['local', local]]),
      ['local'],
    );

    await expect(
      router.route(inactiveEntitlement(), sampleRequest),
    ).rejects.toThrow('AI_SUBSCRIPTION_REQUIRED');
  });

  it('NO SUBSCRIPTION + ON-DEVICE AI = BLOCKED', async () => {
    const onDevice = new OnDeviceAIProvider();
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([['on-device', onDevice]]),
      ['on-device'],
    );

    await expect(
      router.route(inactiveEntitlement(), sampleRequest),
    ).rejects.toThrow('AI_SUBSCRIPTION_REQUIRED');
  });

  it('NO SUBSCRIPTION + GEMINI = BLOCKED', async () => {
    const userGemini = new UserGeminiProvider('mock-token', true);
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([['user-gemini', userGemini]]),
      ['user-gemini'],
    );

    await expect(
      router.route(inactiveEntitlement(), sampleRequest),
    ).rejects.toThrow('AI_SUBSCRIPTION_REQUIRED');
  });

  it('NO SUBSCRIPTION + ALL PROVIDERS READY = BLOCKED BEFORE CHECKING PROVIDERS', async () => {
    let providerChecked = false;
    const trackingProvider: AIProvider = {
      id: 'local',
      executionMode: 'local',
      model: 'tracking',
      capabilities: () => ({
        supportedCapabilities: ['message-drafting'],
        supportsStreaming: false,
        maxContextTokens: 1000,
      }),
      healthCheck: () => {
        providerChecked = true;
        return Promise.resolve({ available: true, authorized: true, status: 'ready' });
      },
      generate: () => {
        throw new Error('should never run');
      },
    };

    const router = new AIExecutionRouter(
      new Map([['local', trackingProvider]]),
      ['local'],
    );

    await expect(
      router.route(inactiveEntitlement(), sampleRequest),
    ).rejects.toThrow('AI_SUBSCRIPTION_REQUIRED');

    // Providers must NOT even be health-checked or consulted when subscription is inactive
    expect(providerChecked).toBe(false);
  });
});

describe('AIExecutionRouter — Priority Routing & Fallback', () => {
  it('Subscribed user + Gemini unauthorized -> Falls back to Local AI', async () => {
    const userGemini = new UserGeminiProvider('user-oauth-token', false); // No verified 3rd-party API quota
    const local = new LocalAIProvider();
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([
        ['user-gemini', userGemini],
        ['local', local],
      ]),
      ['user-gemini', 'local'],
    );

    const selected = await router.route(activeEntitlement(), sampleRequest);
    expect(selected.id).toBe('local');
    expect(selected.executionMode).toBe('local');

    const result = await selected.generate(sampleRequest);
    expect(result.provider).toBe('local');
    expect(result.text).toContain('Priya');
    expect(result.estimatedCostMicros).toBe(0);
  });

  it('Subscribed user + User Gemini authorized -> Uses User Gemini', async () => {
    const userGemini = new UserGeminiProvider('valid-token', true); // Verified API quota
    const local = new LocalAIProvider();
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([
        ['user-gemini', userGemini],
        ['local', local],
      ]),
      ['user-gemini', 'local'],
    );

    const selected = await router.route(activeEntitlement(), sampleRequest);
    expect(selected.id).toBe('user-gemini');
    expect(selected.executionMode).toBe('user-authorized');
  });

  it('Subscribed user + Gemini unavailable + Local AI unavailable -> AI_PROVIDER_UNAVAILABLE', async () => {
    const userGemini = new UserGeminiProvider(null, false);
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([['user-gemini', userGemini]]),
      ['user-gemini', 'local'],
    );

    await expect(
      router.route(activeEntitlement(), sampleRequest),
    ).rejects.toThrow('AI_PROVIDER_UNAVAILABLE');
  });
});

describe('UserGeminiProvider — Google/Gemini Capability Verification', () => {
  it('honestly rejects consumer Gemini subscriptions without 3rd-party API quota', async () => {
    const provider = new UserGeminiProvider('user-access-token', false);
    const health = await provider.healthCheck();

    expect(health.authorized).toBe(false);
    expect(health.available).toBe(false);
    expect(health.status).toBe('unauthorized');
    expect(health.reason).toContain('Consumer Gemini subscriptions do not grant third-party Gemini API quota');

    await expect(provider.generate(sampleRequest)).rejects.toThrow(
      'AI_PROVIDER_NOT_AUTHORIZED',
    );
  });

  it('rejects calls when no user auth token is present', async () => {
    const provider = new UserGeminiProvider(null, false);
    const health = await provider.healthCheck();

    expect(health.authorized).toBe(false);
    expect(health.available).toBe(false);
  });
});

describe('LocalAIProvider — On-Device Execution & Synthesis', () => {
  it('generates personalized English messages respecting tone, relationship, and milestone', async () => {
    const provider = new LocalAIProvider();
    const result = await provider.generate({
      capability: 'message-drafting',
      input: {
        recipientDisplayName: 'Ananya',
        tone: 'cheerful',
        milestone: 'milestone-age',
        language: 'en',
      },
    });

    expect(result.provider).toBe('local');
    expect(result.executionMode).toBe('local');
    expect(result.text).toContain('Ananya');
    expect(result.text).toContain('Milestone');
    expect(result.estimatedCostMicros).toBe(0);
    expect(result.text.length).toBeLessThan(280);
  });

  it('generates respectful Hindi messages respecting tone and relationship', async () => {
    const provider = new LocalAIProvider();
    const result = await provider.generate({
      capability: 'message-drafting',
      input: {
        recipientDisplayName: 'रोहन',
        tone: 'warm',
        relationshipHint: 'family',
        language: 'hi',
      },
    });

    expect(result.provider).toBe('local');
    expect(result.text).toContain('रोहन');
    expect(result.text).toContain('शुभकामनाएँ');
    expect(result.text.length).toBeLessThan(280);
  });
});

describe('synthesizeBirthdayMessage helper', () => {
  it('produces distinct messages for cheerful, simple, and warm tones', () => {
    const warm = synthesizeBirthdayMessage({ recipient: 'Sam', tone: 'warm', language: 'en' });
    const cheerful = synthesizeBirthdayMessage({ recipient: 'Sam', tone: 'cheerful', language: 'en' });
    const simple = synthesizeBirthdayMessage({ recipient: 'Sam', tone: 'simple', language: 'en' });

    expect(warm).not.toEqual(cheerful);
    expect(cheerful).not.toEqual(simple);
    expect(warm).toContain('Sam');
    expect(cheerful).toContain('Sam');
    expect(simple).toContain('Sam');
  });
});

describe('Scenarios 1–7 End-to-End Invariant Verification', () => {
  const NOW = Date.UTC(2026, 8, 24, 12, 0, 0);

  it('Scenario 1: Unsubscribed user attempts cloud Gemini -> BLOCKED (AI_SUBSCRIPTION_REQUIRED)', async () => {
    const unsubscribed = inactiveEntitlement();
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([['gemini-cloud', new StubProviderAdapter('gemini-cloud')]]),
      ['gemini-cloud'],
    );
    await expect(router.route(unsubscribed, sampleRequest)).rejects.toThrow(
      'AI_SUBSCRIPTION_REQUIRED',
    );
  });

  it('Scenario 2: Unsubscribed user attempts local/on-device AI -> BLOCKED (AI_SUBSCRIPTION_REQUIRED)', async () => {
    const unsubscribed = inactiveEntitlement();
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([
        ['local', new LocalAIProvider()],
        ['on-device', new OnDeviceAIProvider()],
      ]),
      ['local', 'on-device'],
    );
    // Local AI is NEVER a free tier or subscription bypass
    await expect(router.route(unsubscribed, sampleRequest)).rejects.toThrow(
      'AI_SUBSCRIPTION_REQUIRED',
    );
  });

  it('Scenario 3: Subscribed user generates via cloud pooled Gemini -> ALLOWED', async () => {
    const subscribed = activeEntitlement();
    const stub = new StubProviderAdapter('gemini-cloud');
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([['gemini-cloud', stub]]),
      ['gemini-cloud'],
    );
    const provider = await router.route(subscribed, sampleRequest);
    expect(provider.id).toBe('gemini-cloud');
    const result = await provider.generate(sampleRequest);
    expect(result.text).toContain('draft generated');
  });

  it('Scenario 4: Subscribed user generates via local/on-device AI -> ALLOWED (0 cloud cost)', async () => {
    const subscribed = activeEntitlement();
    const router = new AIExecutionRouter(
      new Map<string, AIProvider>([['local', new LocalAIProvider()]]),
      ['local'],
    );
    const provider = await router.route(subscribed, sampleRequest);
    expect(provider.id).toBe('local');
    const result = await provider.generate(sampleRequest);
    expect(result.estimatedCostMicros).toBe(0);
    expect(result.text).toContain('Priya');
  });

  it('Scenario 5: Subscribed user quota exhaustion -> BLOCKED (ai-quota-exhausted)', () => {
    const subscribed = activeEntitlement();
    const dailyExhausted = decideAiEntitlement(
      subscribed,
      { period: 'calendar-month', usedToday: 50, usedInPeriod: 100 },
      'message-drafting',
    );
    expect(dailyExhausted).toEqual({ kind: 'blocked', reason: 'ai-quota-exhausted' });

    const monthlyExhausted = decideAiEntitlement(
      subscribed,
      { period: 'calendar-month', usedToday: 10, usedInPeriod: 300 },
      'message-drafting',
    );
    expect(monthlyExhausted).toEqual({ kind: 'blocked', reason: 'ai-quota-exhausted' });
  });

  it('Scenario 6: Subscription expiration & offline cache grace period expiry', () => {
    const expiredRecord: AiSubscriptionRecord = {
      schemaVersion: AI_SCHEMA_VERSION,
      uid: 'uid-exp',
      plan: 'wishwell-plus',
      status: 'expired',
      billingProvider: 'play-billing',
      externalSubscriptionId: 'tok-exp',
      purchasedAtMs: NOW - 60 * 24 * 3600_000,
      expiresAtMs: NOW - 1000,
      cancelledAtMs: NOW - 1000,
      updatedAtMs: NOW - 1000,
    };
    const projection = projectAiEntitlement(expiredRecord, NOW);
    expect(projection.enabled).toBe(false);
    expect(projection.capabilities).toHaveLength(0);

    // Offline cache validity
    const validCache: CachedAiEntitlement = {
      uid: 'uid-exp',
      plan: 'wishwell-plus',
      status: 'active',
      enabled: true,
      lastVerifiedAtMs: NOW - 24 * 3600_000, // 24h ago (within 72h grace)
      expiresAtMs: NOW + 10 * 24 * 3600_000,
      gracePeriodMs: OFFLINE_ENTITLEMENT_GRACE_PERIOD_MS,
    };
    expect(isCachedEntitlementValid(validCache, NOW).valid).toBe(true);

    // Past 72h ceiling -> invalid
    const staleCache: CachedAiEntitlement = {
      ...validCache,
      lastVerifiedAtMs: NOW - 73 * 3600_000, // 73h ago
    };
    expect(isCachedEntitlementValid(staleCache, NOW).valid).toBe(false);

    // Expired subscription in cache -> invalid
    const expiredCache: CachedAiEntitlement = {
      ...validCache,
      expiresAtMs: NOW - 1000,
    };
    expect(isCachedEntitlementValid(expiredCache, NOW).valid).toBe(false);
  });

  it('Scenario 7: Renewal via Play Billing or Stripe unlocks AI again', () => {
    const expiredRecord: AiSubscriptionRecord = {
      schemaVersion: AI_SCHEMA_VERSION,
      uid: 'uid-renew',
      plan: 'free',
      status: 'expired',
      billingProvider: 'play-billing',
      externalSubscriptionId: 'tok-renew',
      purchasedAtMs: NOW - 60 * 24 * 3600_000,
      expiresAtMs: NOW - 1000,
      cancelledAtMs: NOW - 1000,
      updatedAtMs: NOW - 1000,
    };
    expect(projectAiEntitlement(expiredRecord, NOW).enabled).toBe(false);

    // Play Billing renewal
    const playRenewed = reducePlayEvent(expiredRecord, {
      uid: 'uid-renew',
      purchaseToken: 'new-tok-play',
      productId: 'wishwell_plus_monthly',
      expiresAtMs: NOW + 30 * 24 * 3600_000,
      status: 'active',
      occurredAtMs: NOW,
    });
    expect(playRenewed?.plan).toBe('wishwell-plus');
    expect(projectAiEntitlement(playRenewed, NOW).enabled).toBe(true);

    // Stripe renewal
    const stripeRenewed = reduceStripeEvent(expiredRecord, {
      uid: 'uid-renew',
      subscriptionId: 'sub_stripe_renewed',
      priceId: 'price_wishwell_plus_yearly',
      expiresAtMs: NOW + 365 * 24 * 3600_000,
      status: 'active',
      occurredAtMs: NOW,
    });
    expect(stripeRenewed?.plan).toBe('wishwell-plus');
    expect(projectAiEntitlement(stripeRenewed, NOW).enabled).toBe(true);
  });
});

