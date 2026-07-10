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
            short: 'Happy birthday Asha! Aaj ka din warmth aur khushi se bhara rahe.',
            standard: 'Happy birthday Asha! Umeed hai aaj ka special day joyful ho aur aane wala year bahut lovely ho.',
            warm: 'Happy birthday Asha! Aap hamare liye bahut special ho; umeed hai aaj love aur good chaos se bhara rahe.'
          }
        });
      }
    );

    assert.equal(result.ok, true);
    assert.equal(result.observation?.redacted, true);
    assert.equal(result.observation?.ok, true);
    assert.equal(result.observation?.includedMemoryCount, request.request.privacy.includedMemoryCount);
    assert.equal(result.observation?.variantLengths?.standard, result.ok ? result.variants.standard.length : undefined);
    const posted = JSON.parse(postedBody) as typeof request.request;
    assert.deepEqual(posted.giftHistory, [
      {
        name: 'Ceramic tea set',
        category: 'Personal',
        occasion: 'Birthday',
        year: 2025,
        feedback: 'Liked'
      }
    ]);
    assert.doesNotMatch(postedBody, /\+919876543210/);
    assert.doesNotMatch(postedBody, /Private note excluded/i);
    assert.doesNotMatch(postedBody, /Avoid repeating kitchenware|"cost"|annualGiftBudget/i);
    assert.doesNotMatch(JSON.stringify(result.observation), /Asha|Happy birthday|98765|Ceramic tea set/i);
  });

  it('rejects unsafe provider output and records only content-free diagnostics', async () => {
    resetAiProviderRateLimitForTests();
    const request = buildAiDraftRequest(createTestState(), 'c-asha', 'e-asha-bday', 'Birthday');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    const unsafeText = 'Happy birthday, and I hope you die before the day is over.';
    const result = await requestAiDraft(
      request.request,
      {
        endpoint: 'https://ai.example.test/draft',
        timeoutMs: 1000,
        ...authenticatedSession
      },
      async () =>
        staticJsonResponse({
          variants: {
            short: unsafeText,
            standard: 'Happy birthday! Wishing you a personal, joyful day and a thoughtful year ahead.',
            warm: 'Happy birthday! Hope today feels personal, warm, and full of love.'
          }
        })
    );

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.error.kind, 'content-safety');
      assert.equal(result.observation?.errorKind, 'content-safety');
      assert.doesNotMatch(result.error.message, /hope you die/i);
      assert.doesNotMatch(JSON.stringify(result.observation), /hope you die|Asha|Ceramic tea set/i);
    }
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
            short: 'Happy birthday Asha! Aaj ka din warmth aur khushi se bhara rahe.',
            standard: 'Happy birthday Asha! Umeed hai aaj ka special day joyful ho aur aane wala year bahut lovely ho.',
            warm: 'Happy birthday Asha! Aap hamare liye bahut special ho; umeed hai aaj love aur good chaos se bhara rahe.'
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
          short: 'Hi Asha, bas check in karna tha. Umeed hai sab theek hai.',
          standard:
            'Hi Asha, aaj aapki yaad aayi, isliye bas check in karna tha. Umeed hai week achchha chal raha hai.',
          warm: 'Hi Asha, aapki yaad aayi aur poochna tha ki sab kaisa hai. Umeed hai aaj ka din calm aur kind ho.'
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
