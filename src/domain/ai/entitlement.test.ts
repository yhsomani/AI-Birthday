import {
  aiQuotaForPlan,
  decideAiEntitlement,
  isAiSignInProvider,
  type AiEntitlementProjection,
  type AiUsageSnapshot,
} from './model';

function entitlement(
  overrides: Partial<AiEntitlementProjection> = {},
): AiEntitlementProjection {
  const plan = overrides.plan ?? 'wishwell-plus';
  return {
    enabled: plan !== 'free',
    plan,
    capabilities: plan === 'free' ? [] : ['message-drafting'],
    quota: aiQuotaForPlan(plan),
    providers: [
      {
        provider: 'gemini-cloud',
        authorizationMode: 'application-owned',
        connectionState: null,
      },
    ],
    renewsOn: null,
    ...overrides,
  };
}

function usage(overrides: Partial<AiUsageSnapshot> = {}): AiUsageSnapshot {
  return { period: 'calendar-month', usedToday: 0, usedInPeriod: 0, ...overrides };
}

describe('decideAiEntitlement (app subscription is the ONLY gate)', () => {
  it('blocks free-plan accounts with ai-subscription-required', () => {
    expect(decideAiEntitlement(entitlement({ plan: 'free', enabled: false }), usage())).toEqual({
      kind: 'blocked',
      reason: 'ai-subscription-required',
    });
  });

  it('blocks even when AI is marked enabled but the capability is missing', () => {
    const decision = decideAiEntitlement(
      entitlement({ capabilities: [] }),
      usage(),
    );
    expect(decision).toEqual({ kind: 'blocked', reason: 'ai-subscription-required' });
  });

  it('allows an active Plus subscription within quota', () => {
    const decision = decideAiEntitlement(entitlement(), usage({ usedToday: 1, usedInPeriod: 2 }));
    expect(decision.kind).toBe('allowed');
    if (decision.kind === 'allowed') {
      expect(decision.quota).toEqual(aiQuotaForPlan('wishwell-plus'));
    }
  });

  it('blocks at the daily boundary (usedToday >= requestsPerDay)', () => {
    expect(
      decideAiEntitlement(
        entitlement(),
        usage({ usedToday: aiQuotaForPlan('wishwell-plus').requestsPerDay }),
      ),
    ).toEqual({ kind: 'blocked', reason: 'ai-quota-exhausted' });
  });

  it('blocks at the monthly boundary (usedInPeriod >= requestsPerMonth)', () => {
    expect(
      decideAiEntitlement(
        entitlement(),
        usage({ usedInPeriod: aiQuotaForPlan('wishwell-plus').requestsPerMonth }),
      ),
    ).toEqual({ kind: 'blocked', reason: 'ai-quota-exhausted' });
  });

  it('fails closed on a usage-period mismatch', () => {
    const wrongPeriod: AiUsageSnapshot = {
      ...usage(),
      period: 'rolling-week' as unknown as AiUsageSnapshot['period'],
    };
    expect(decideAiEntitlement(entitlement(), wrongPeriod)).toEqual({
      kind: 'blocked',
      reason: 'ai-usage-period-mismatch',
    });
  });

  it('never consults external provider state: a connected sign-in changes nothing', () => {
    const withSignIn = entitlement({
      providers: [
        { provider: 'gemini-cloud', authorizationMode: 'provider-sign-in', connectionState: 'connected' },
      ],
    });
    expect(decideAiEntitlement(withSignIn, usage()).kind).toBe('allowed');
    // Same connection, but subscription lapsed → still blocked. Sign-in is
    // never an entitlement source.
    expect(
      decideAiEntitlement({ ...withSignIn, enabled: false }, usage()),
    ).toEqual({ kind: 'blocked', reason: 'ai-subscription-required' });
  });
});

describe('aiQuotaForPlan', () => {
  it('ships zero quotas for the free plan (defensive)', () => {
    expect(aiQuotaForPlan('free')).toEqual({
      period: 'calendar-month',
      requestsPerDay: 0,
      requestsPerMonth: 0,
    });
  });
});

describe('isAiSignInProvider', () => {
  it('accepts the closed sign-in provider set', () => {
    expect(isAiSignInProvider('gemini-cloud')).toBe(true);
  });

  it('rejects unknown values and non-strings (no API-key providers exist)', () => {
    expect(isAiSignInProvider('byok-openai')).toBe(false);
    expect(isAiSignInProvider(42)).toBe(false);
    expect(isAiSignInProvider(undefined)).toBe(false);
  });
});
