/**
 * Unit tests for the provider port & drivers: cost estimation, the
 * deterministic stub adapter and the Gemini REST driver (exercised through an
 * injected fake fetch — no network access).
 */

import { describe, expect, it } from 'vitest';

import {
  AiProviderError,
  GeminiRestAdapter,
  StubProviderAdapter,
  estimateCostMicros,
  isAiProviderId,
  type AiGenerationRequest,
  type GeminiFetchLike,
} from '../src/domain/aiProviders.js';

function request(overrides: Partial<AiGenerationRequest> = {}): AiGenerationRequest {
  return {
    capability: 'message-drafting',
    prompt: 'Draft a birthday wish for Ana who loves gardening.',
    systemInstruction: 'You write warm, brief birthday messages.',
    maxOutputTokens: 200,
    temperature: 0.7,
    ...overrides,
  };
}

function fakeFetch(
  impl: (url: string, init?: Record<string, unknown>) => Promise<{
    ok: boolean;
    status: number;
    json(): Promise<unknown>;
  }>,
): { fetch: GeminiFetchLike; calls: { url: string; init?: Record<string, unknown> }[] } {
  const calls: { url: string; init?: Record<string, unknown> }[] = [];
  return {
    calls,
    fetch: (url, init) => {
      calls.push({ url, ...(init !== undefined ? { init } : {}) });
      // Synchronous wrapper: the fake never awaits, so no require-await noise.
      return impl(url, init);
    },
  };
}

describe('estimateCostMicros', () => {
  it('prices known models from the table', () => {
    // gemini-2.0-flash: [1000, 4000] micro per 1k tokens.
    expect(estimateCostMicros('gemini-2.0-flash', 1000, 1000)).toBe(5000);
    expect(estimateCostMicros('gemini-2.5-flash', 1000, 1000)).toBe(12500);
  });

  it('rounds up so costs are never under-estimated', () => {
    expect(estimateCostMicros('gemini-2.0-flash', 1, 1)).toBe(5);
  });

  it('falls back to the priciest known row for unknown models', () => {
    // Unknown model must cost at least as much as gemini-2.5-flash.
    expect(estimateCostMicros('mystery-model', 1000, 1000)).toBe(
      estimateCostMicros('gemini-2.5-flash', 1000, 1000),
    );
  });
});

describe('isAiProviderId', () => {
  it('accepts known providers only', () => {
    expect(isAiProviderId('gemini-cloud')).toBe(true);
    expect(isAiProviderId('on-device')).toBe(true);
    expect(isAiProviderId('openai')).toBe(false);
  });
});

describe('StubProviderAdapter', () => {
  it('produces a deterministic, fully-populated result offline', async () => {
    const adapter = new StubProviderAdapter();
    const first = await adapter.generate(request());
    const second = await adapter.generate(request());
    expect(first).toEqual(second);
    expect(first.provider).toBe('gemini-cloud');
    expect(first.text).toContain('message-drafting');
    expect(first.inputTokens).toBeGreaterThan(0);
    expect(first.outputTokens).toBeGreaterThan(0);
    expect(first.estimatedCostMicros).toBeGreaterThan(0);
  });

  it('rejects unknown provider ids at construction', () => {
    expect(
      () => new StubProviderAdapter('openai' as never),
    ).toThrow(AiProviderError);
  });
});

describe('GeminiRestAdapter', () => {
  const okPayload = {
    candidates: [
      { content: { parts: [{ text: 'Happy birthday Ana! 🎂' }] } },
    ],
    usageMetadata: { promptTokenCount: 21, candidatesTokenCount: 9 },
  };

  it('refuses to construct without a credential', () => {
    const { fetch } = fakeFetch(() => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(okPayload) }));
    expect(() => new GeminiRestAdapter('   ', 'gemini-2.0-flash', fetch)).toThrow(
      AiProviderError,
    );
  });

  it('posts the rendered prompt with the API key header and parses usage', async () => {
    const { fetch, calls } = fakeFetch(() => Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve(okPayload),
    }));
    const adapter = new GeminiRestAdapter('test-key', 'gemini-2.0-flash', fetch);
    const result = await adapter.generate(request());
    expect(result.text).toBe('Happy birthday Ana! 🎂');
    expect(result.inputTokens).toBe(21);
    expect(result.outputTokens).toBe(9);
    expect(result.estimatedCostMicros).toBeGreaterThan(0);
    expect(calls[0]?.url).toContain('gemini-2.0-flash:generateContent');
    const headers = (calls[0]?.init?.headers ?? {}) as Record<string, string>;
    expect(headers['x-goog-api-key']).toBe('test-key');
  });

  it('maps transport failures to a non-leaking unavailable error', async () => {
    const { fetch } = fakeFetch(() => {
      throw new Error('socket hang up: /v1beta/models');
    });
    const adapter = new GeminiRestAdapter('test-key', 'gemini-2.0-flash', fetch);
    await expect(adapter.generate(request())).rejects.toSatisfy((err: unknown) => {
      return (
        err instanceof AiProviderError &&
        err.code === 'unavailable' &&
        !err.message.includes('generativelanguage')
      );
    });
  });

  it.each([
    [429, 'rate-limited'],
    [500, 'bad-response'],
  ] as const)('maps HTTP %i to %s', async (status, code) => {
    const { fetch } = fakeFetch(() => Promise.resolve({
      ok: status < 400,
      status,
      json: () => Promise.resolve({}),
    }));
    const adapter = new GeminiRestAdapter('test-key', 'gemini-2.0-flash', fetch);
    await expect(adapter.generate(request())).rejects.toSatisfy(
      (err: unknown) => err instanceof AiProviderError && err.code === code,
    );
  });

  it('treats safety-blocked candidates as refusals', async () => {
    const { fetch } = fakeFetch(() => Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ candidates: [{ finishReason: 'SAFETY' }] }),
    }));
    const adapter = new GeminiRestAdapter('test-key', 'gemini-2.0-flash', fetch);
    await expect(adapter.generate(request())).rejects.toSatisfy(
      (err: unknown) => err instanceof AiProviderError && err.code === 'refused',
    );
  });

  it('treats empty candidate text as a bad response', async () => {
    const { fetch } = fakeFetch(() => Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ candidates: [{ content: { parts: [{ text: '   ' }] } }] }),
    }));
    const adapter = new GeminiRestAdapter('test-key', 'gemini-2.0-flash', fetch);
    await expect(adapter.generate(request())).rejects.toSatisfy(
      (err: unknown) => err instanceof AiProviderError && err.code === 'bad-response',
    );
  });

  it('defaults missing usage metadata to zero tokens', async () => {
    const { fetch } = fakeFetch(() => Promise.resolve({
      ok: true,
      status: 200,
      json: () =>
        Promise.resolve({
          candidates: [{ content: { parts: [{ text: 'Hi!' }] } }],
        }),
    }));
    const adapter = new GeminiRestAdapter('test-key', 'gemini-2.0-flash', fetch);
    const result = await adapter.generate(request());
    expect(result.inputTokens).toBe(0);
    expect(result.outputTokens).toBe(0);
  });
});
