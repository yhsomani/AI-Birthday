import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildAiDraftRequest } from '../domain/aiDrafting';
import { createTestState } from '../test/testState';
import { evaluateAiProviderRateLimit, requestAiDraft, resetAiProviderRateLimitForTests } from './aiProviderClient';
import { staticJsonResponse } from './providerTransport';

const authenticatedSession = {
  sessionAccessToken: 'test-session-token-1234567890',
  sessionExpiresAt: '2999-01-01T00:00:00.000Z'
};

describe('aiProviderClient', () => {
  it('posts the sanitized request to the configured endpoint', async () => {
    resetAiProviderRateLimitForTests();
    const state = createTestState();
    const request = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    let postedBody = '';
    const result = await requestAiDraft(
      request.request,
      {
        endpoint: 'https://ai.example.test/draft',
        timeoutMs: 1000,
        ...authenticatedSession
      },
      async (_input, init) => {
        postedBody = init.body;
        return staticJsonResponse({
            variants: {
              short: 'Happy birthday Asha! Hope your day is full of warmth.',
              standard: 'Happy birthday Asha! Wishing you a personal, joyful day and a beautiful year ahead.',
              warm: 'Happy birthday Asha! I hope today feels easy, loved, and full of the good chaos you enjoy.'
            }
          });
      }
    );

    assert.equal(result.ok, true);
    assert.equal(result.observation?.redacted, true);
    assert.equal(result.observation?.ok, true);
    assert.equal(result.observation?.includedMemoryCount, request.request.privacy.includedMemoryCount);
    assert.equal(result.observation?.variantLengths?.standard, result.ok ? result.variants.standard.length : undefined);
    assert.doesNotMatch(postedBody, /\+919876543210/);
    assert.doesNotMatch(postedBody, /Private note excluded/i);
    assert.doesNotMatch(JSON.stringify(result.observation), /Asha|Happy birthday|98765/i);
  });

  it('returns not-configured before attempting network access', async () => {
    resetAiProviderRateLimitForTests();
    const state = createTestState();
    const request = buildAiDraftRequest(state, 'c-asha', undefined, 'Check-in');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    const result = await requestAiDraft(request.request, { timeoutMs: 1000 });

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.error.kind, 'not-configured');
      assert.equal(result.observation?.errorKind, 'not-configured');
    }
  });

  it('rejects unsafe configured endpoints before attempting network access', async () => {
    resetAiProviderRateLimitForTests();
    const state = createTestState();
    const request = buildAiDraftRequest(state, 'c-asha', undefined, 'Check-in');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    let calls = 0;
    const result = await requestAiDraft(
      request.request,
      {
        endpoint: 'http://provider.example.test/draft',
        timeoutMs: 1000
      },
      async () => {
        calls += 1;
        return staticJsonResponse({});
      }
    );

    assert.equal(result.ok, false);
    assert.equal(calls, 0);
    if (!result.ok) {
      assert.equal(result.error.kind, 'not-configured');
      assert.match(result.error.message, /not safe to use/i);
      assert.doesNotMatch(result.error.message, /provider\.example|draft/);
    }
  });

  it('allows explicitly approved local development endpoints without marking them production-ready', async () => {
    resetAiProviderRateLimitForTests();
    const state = createTestState();
    const request = buildAiDraftRequest(state, 'c-asha', 'e-asha-bday', 'Birthday');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    let calls = 0;
    const result = await requestAiDraft(
      request.request,
      {
        endpoint: 'http://localhost:8787/draft',
        timeoutMs: 1000,
        allowLocalProviderEndpoint: true
      },
      async () => {
        calls += 1;
        return staticJsonResponse({
            variants: {
              short: 'Happy birthday Asha! Hope your day is full of warmth.',
              standard: 'Happy birthday Asha! Wishing you a personal, joyful day and a beautiful year ahead.',
              warm: 'Happy birthday Asha! I hope today feels easy, loved, and full of the good chaos you enjoy.'
            }
          });
      }
    );

    assert.equal(result.ok, true);
    assert.equal(calls, 1);
  });

  it('applies a local per-minute rate limit before contacting the provider', async () => {
    resetAiProviderRateLimitForTests();
    const state = createTestState();
    const request = buildAiDraftRequest(state, 'c-asha', undefined, 'Check-in');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    let calls = 0;
    const fetcher = async () => {
      calls += 1;
      return staticJsonResponse({
          variants: {
            short: 'Hope you are doing well Asha. Thinking of you today.',
            standard: 'Hope you are doing well Asha. Just checking in and sending warm wishes for your week.',
            warm: 'Hope you are doing well Asha. Thinking of you and hoping today feels calm and kind.'
          }
        });
    };

    const config = {
      endpoint: 'https://ai.example.test/draft',
      timeoutMs: 1000,
      maxRequestsPerMinute: 1,
      ...authenticatedSession
    };
    const first = await requestAiDraft(request.request, config, fetcher);
    const second = await requestAiDraft(request.request, config, fetcher);

    assert.equal(first.ok, true);
    assert.equal(second.ok, false);
    assert.equal(calls, 1);
    if (!second.ok) {
      assert.equal(second.error.kind, 'quota');
      assert.equal(second.observation?.errorKind, 'quota');
    }
  });

  it('requires an authenticated short-lived session for public provider endpoints', async () => {
    resetAiProviderRateLimitForTests();
    const request = buildAiDraftRequest(createTestState(), 'c-asha', undefined, 'Check-in');
    assert.equal(request.ok, true);
    if (!request.ok) return;

    let calls = 0;
    const result = await requestAiDraft(
      request.request,
      { endpoint: 'https://ai.example.test/draft', timeoutMs: 1000 },
      async () => {
        calls += 1;
        return staticJsonResponse({});
      }
    );

    assert.equal(result.ok, false);
    assert.equal(calls, 0);
    if (!result.ok) assert.equal(result.error.kind, 'auth');
  });

  it('rejects oversized or non-JSON successful responses', async () => {
    resetAiProviderRateLimitForTests();
    const request = buildAiDraftRequest(createTestState(), 'c-asha', undefined, 'Check-in');
    assert.equal(request.ok, true);
    if (!request.ok) return;

    const result = await requestAiDraft(
      request.request,
      { endpoint: 'https://ai.example.test/draft', timeoutMs: 1000, ...authenticatedSession },
      async () => staticJsonResponse({ variants: {} }, { contentType: 'text/html' })
    );
    assert.equal(result.ok, false);
    if (!result.ok) assert.equal(result.error.kind, 'invalid-response');
  });

  it('evaluates rate limit windows without retaining stale timestamps', () => {
    const history = [0, 10_000, 61_000];
    const allowed = evaluateAiProviderRateLimit(history, 62_000, 3);
    assert.equal(allowed.allowed, true);
    assert.deepEqual(history, [10_000, 61_000]);

    const denied = evaluateAiProviderRateLimit(history, 62_500, 2);
    assert.equal(denied.allowed, false);
    assert.equal(denied.nextAllowedAt, 70_000);
  });
});
