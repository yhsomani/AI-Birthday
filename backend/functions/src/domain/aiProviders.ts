/**
 * AI Gateway provider ports & drivers.
 *
 * The gateway depends only on the `AiProviderAdapter` port; concrete drivers
 * (Gemini over REST, deterministic stub for tests/emulators) live beside it.
 * Swapping or adding a vendor (OpenAI, Anthropic, local) means adding one
 * adapter file and registering it — no changes to entitlement, quota or
 * routing logic (see "Ai gateway architecture.md").
 *
 * Privacy note: prompts may contain contact-derived personal data. Adapters
 * must never log prompt content, and transient prompt text must not be
 * persisted by this layer (the usage ledger records token counts only).
 */

import {
  AI_PROVIDER_IDS,
  type AiCapabilityId,
  type AiProviderId,
} from './aiModel.js';

export interface AiGenerationRequest {
  readonly capability: AiCapabilityId;
  /** Fully-rendered prompt. Transient: never logged, never stored. */
  readonly prompt: string;
  readonly systemInstruction: string;
  readonly maxOutputTokens: number;
  readonly temperature: number;
}

export interface AiGenerationResult {
  readonly provider: AiProviderId;
  readonly model: string;
  readonly text: string;
  readonly inputTokens: number;
  readonly outputTokens: number;
  /** Estimated cost in integer microcurrency units (₹ × 1,000,000). */
  readonly estimatedCostMicros: number;
}

export class AiProviderError extends Error {
  constructor(
    readonly code: 'unavailable' | 'rate-limited' | 'bad-response' | 'refused',
    message: string,
  ) {
    super(message);
    this.name = 'AiProviderError';
  }
}

export interface AiProviderAdapter {
  readonly provider: AiProviderId;
  readonly model: string;
  generate(request: AiGenerationRequest, signal?: AbortSignal): Promise<AiGenerationResult>;
}

// --- Cost estimation -----------------------------------------------------------

/**
 * Rough price table in micro-rupees per 1k tokens
 * (input, output). Deliberately conservative; used for budget telemetry,
 * not billing. Unknown models fall back to the priciest known row so cost
 * alarms trip early rather than late.
 */
const PRICE_PER_1K_MICROS: Readonly<Record<string, readonly [number, number]>> = {
  'gemini-2.0-flash': [1000, 4000],
  'gemini-2.5-flash': [2500, 10000],
};

export function estimateCostMicros(
  model: string,
  inputTokens: number,
  outputTokens: number,
): number {
  const rows = Object.values(PRICE_PER_1K_MICROS);
  const fallback = rows.reduce(
    (worst, row) => (row[0] + row[1] > worst[0] + worst[1] ? row : worst),
    rows[0] ?? [0, 0],
  );
  const [inPer1k, outPer1k] = PRICE_PER_1K_MICROS[model] ?? fallback;
  return Math.ceil((inputTokens / 1000) * inPer1k + (outputTokens / 1000) * outPer1k);
}

export function isAiProviderId(value: string): value is AiProviderId {
  return (AI_PROVIDER_IDS as readonly string[]).includes(value);
}

// --- Deterministic test/emulator driver -----------------------------------------

/**
 * Deterministic offline adapter used by unit tests and the emulator flow.
 * Produces a valid draft envelope without any network access; token counts
 * are approximated from character length (÷4, minimum 1).
 */
export class StubProviderAdapter implements AiProviderAdapter {
  readonly provider: AiProviderId;
  readonly model: string;

  constructor(provider: AiProviderId = 'gemini-cloud', model = 'stub-1') {
    if (!isAiProviderId(provider)) {
      throw new AiProviderError('unavailable', 'UNKNOWN_PROVIDER');
    }
    this.provider = provider;
    this.model = model;
  }

  generate(
    request: AiGenerationRequest,
  ): Promise<AiGenerationResult> {
    const inputTokens = Math.max(1, Math.ceil(request.prompt.length / 4));
    const text = `[stub:${this.provider}] ${request.capability} draft generated.`;
    const outputTokens = Math.max(1, Math.ceil(text.length / 4));
    return Promise.resolve({
      provider: this.provider,
      model: this.model,
      text,
      inputTokens,
      outputTokens,
      estimatedCostMicros: estimateCostMicros(
        this.model,
        inputTokens,
        outputTokens,
      ),
    });
  }
}

// --- Gemini REST driver (application-owned credentials) --------------------------

export type GeminiFetchLike = (input: string, init?: Record<string, unknown>) => Promise<{
    ok: boolean;
    status: number;
    json(): Promise<unknown>;
  }>;

interface GeminiGenerateContentResponse {
  readonly candidates?: readonly {
    readonly content?: { readonly parts?: readonly { readonly text?: string }[] };
    readonly finishReason?: string;
  }[];
  readonly usageMetadata?: {
    readonly promptTokenCount?: number;
    readonly candidatesTokenCount?: number;
  };
}

function readNumber(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? Math.floor(value)
    : 0;
}

/**
 * Minimal REST driver for the Gemini generateContent API. The API key is
 * injected via a Secret Manager-backed environment variable and NEVER
 * appears in logs, responses or the usage ledger. This satisfies the
 * "application-owned" authorization mode: users never see or supply it.
 */
export class GeminiRestAdapter implements AiProviderAdapter {
  readonly provider = 'gemini-cloud' as const;
  readonly model: string;

  constructor(
    private readonly apiKey: string,
    model = 'gemini-2.0-flash',
    private readonly fetchImpl: GeminiFetchLike = fetch,
  ) {
    if (apiKey.trim().length === 0) {
      throw new AiProviderError('unavailable', 'MISSING_CREDENTIAL');
    }
    this.model = model;
  }

  async generate(
    request: AiGenerationRequest,
    signal?: AbortSignal,
  ): Promise<AiGenerationResult> {
    const url =
      `https://generativelanguage.googleapis.com/v1beta/models/` +
      `${encodeURIComponent(this.model)}:generateContent`;
    let response: Awaited<ReturnType<GeminiFetchLike>>;
    try {
      response = await this.fetchImpl(url, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-goog-api-key': this.apiKey,
        },
        body: JSON.stringify({
          system_instruction: { parts: [{ text: request.systemInstruction }] },
          contents: [{ role: 'user', parts: [{ text: request.prompt }] }],
          generationConfig: {
            temperature: request.temperature,
            maxOutputTokens: request.maxOutputTokens,
          },
        }),
        ...(signal !== undefined ? { signal } : {}),
      });
    } catch {
      // Never surface upstream error details (may echo request content).
      throw new AiProviderError('unavailable', 'PROVIDER_UNREACHABLE');
    }
    if (response.status === 429) {
      throw new AiProviderError('rate-limited', 'PROVIDER_RATE_LIMITED');
    }
    if (!response.ok) {
      throw new AiProviderError('bad-response', 'PROVIDER_ERROR_STATUS');
    }
    const payload = (await response.json()) as GeminiGenerateContentResponse;
    const candidate = payload.candidates?.[0];
    if (candidate?.finishReason === 'SAFETY' || candidate?.finishReason === 'PROHIBITED_CONTENT') {
      throw new AiProviderError('refused', 'PROVIDER_REFUSED');
    }
    const text = (candidate?.content?.parts ?? [])
      .map((part) => part.text ?? '')
      .join('')
      .trim();
    if (text.length === 0) {
      throw new AiProviderError('bad-response', 'PROVIDER_EMPTY_RESPONSE');
    }
    const inputTokens = readNumber(payload.usageMetadata?.promptTokenCount);
    const outputTokens = readNumber(payload.usageMetadata?.candidatesTokenCount);
    return {
      provider: this.provider,
      model: this.model,
      text,
      inputTokens,
      outputTokens,
      estimatedCostMicros: estimateCostMicros(
        this.model,
        inputTokens,
        outputTokens,
      ),
    };
  }
}
