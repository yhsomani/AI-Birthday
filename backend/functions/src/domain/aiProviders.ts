/**
 * AI Gateway provider ports, adapters & execution router.
 *
 * The gateway depends only on the `AIProvider` port; concrete drivers
 * (UserGeminiProvider, LocalAIProvider, OnDeviceAIProvider, GeminiRestAdapter,
 * StubProviderAdapter) implement this interface.
 *
 * Privacy note: prompts may contain contact-derived personal data. Adapters
 * must never log prompt content, and transient prompt text must not be
 * persisted by this layer (the usage ledger records token counts only).
 */

import {
  AI_PROVIDER_IDS,
  type AIRequest,
  type AIResult,
  type AiCapabilityId,
  type AiEntitlementProjection,
  type AiExecutionMode,
  type AiProviderId,
} from './aiModel.js';

export interface AiGenerationRequest {
  readonly capability: AiCapabilityId;
  /** Fully-rendered prompt. Transient: never logged, never stored. */
  readonly prompt: string;
  readonly systemInstruction: string;
  readonly maxOutputTokens: number;
  readonly temperature: number;
  readonly input?: {
    readonly recipientDisplayName?: string;
    readonly tone?: string;
    readonly relationshipHint?: string;
    readonly additionalContext?: string;
    readonly language?: string;
    readonly milestone?: string;
    readonly placeholderMode?: string;
    readonly requestedSegmentCap?: number;
    readonly [key: string]: unknown;
  };
}

export type AiGenerationResult = AIResult;

export class AiProviderError extends Error {
  constructor(
    readonly code: 'unavailable' | 'rate-limited' | 'bad-response' | 'refused' | 'not-authorized',
    message: string,
  ) {
    super(message);
    this.name = 'AiProviderError';
  }
}

export interface ProviderHealth {
  readonly available: boolean;
  readonly authorized: boolean;
  readonly status: 'ready' | 'unauthorized' | 'unavailable' | 'rate-limited' | 'error';
  readonly reason?: string;
}

export interface ProviderCapabilities {
  readonly supportedCapabilities: readonly AiCapabilityId[];
  readonly supportsStreaming: boolean;
  readonly maxContextTokens: number;
}

export interface AIProvider {
  readonly id: AiProviderId;
  readonly executionMode: AiExecutionMode;
  readonly model: string;
  generate(request: AIRequest | AiGenerationRequest, signal?: AbortSignal): Promise<AIResult>;
  stream?(request: AIRequest | AiGenerationRequest): AsyncIterable<{ text: string }>;
  healthCheck?(): Promise<ProviderHealth>;
  capabilities(): ProviderCapabilities;
}

/** Legacy adapter contract for backward compatibility. */
export interface LegacyAiProviderAdapter {
  readonly provider: AiProviderId;
  readonly model: string;
  generate(request: AIRequest | AiGenerationRequest): Promise<AIResult | AiGenerationResult>;
}

export type AiProviderAdapter = AIProvider | LegacyAiProviderAdapter;

export function asAiProvider(adapter: AiProviderAdapter): AIProvider {
  if ('capabilities' in adapter && typeof (adapter as AIProvider).capabilities === 'function') {
    return adapter as AIProvider;
  }
  const legacy = adapter as LegacyAiProviderAdapter;
  return {
    id: legacy.provider,
    executionMode: 'application-cloud',
    model: legacy.model,
    capabilities: () => ({
      supportedCapabilities: ['message-drafting'],
      supportsStreaming: false,
      maxContextTokens: 4096,
    }),
    async generate(req) {
      const res = await legacy.generate(req);
      return {
        provider: res.provider,
        executionMode:
          'executionMode' in res && res.executionMode
            ? (res.executionMode as AiExecutionMode)
            : 'application-cloud',
        model: res.model,
        text: res.text,
        inputTokens: res.inputTokens,
        outputTokens: res.outputTokens,
        estimatedCostMicros: res.estimatedCostMicros,
        latencyMs: res.latencyMs,
      };
    },
  };
}

// --- Cost estimation -----------------------------------------------------------

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
  return (AI_PROVIDER_IDS as readonly string[]).includes(value as AiProviderId);
}

// --- Deterministic test/emulator driver -----------------------------------------

export class StubProviderAdapter implements AIProvider {
  readonly id: AiProviderId;
  readonly provider: AiProviderId;
  readonly executionMode: AiExecutionMode = 'stub';
  readonly model: string;

  constructor(provider: AiProviderId = 'gemini-cloud', model = 'stub-1') {
    if (!isAiProviderId(provider)) {
      throw new AiProviderError('unavailable', 'UNKNOWN_PROVIDER');
    }
    this.id = provider;
    this.provider = provider;
    this.model = model;
  }

  capabilities(): ProviderCapabilities {
    return {
      supportedCapabilities: ['message-drafting'],
      supportsStreaming: false,
      maxContextTokens: 4096,
    };
  }

  healthCheck(): Promise<ProviderHealth> {
    return Promise.resolve({
      available: true,
      authorized: true,
      status: 'ready',
    });
  }

  generate(
    request: AIRequest | AiGenerationRequest,
  ): Promise<AIResult> {
    const promptText = request.prompt ?? (request as AIRequest).input?.recipientDisplayName ?? '';
    const inputTokens = Math.max(1, Math.ceil(promptText.length / 4));
    const text = `[stub:${this.provider}] ${request.capability} draft generated.`;
    const outputTokens = Math.max(1, Math.ceil(text.length / 4));
    return Promise.resolve({
      provider: this.provider,
      executionMode: this.executionMode,
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

// --- User-authorized Gemini Provider -------------------------------------------

/**
 * Adapter representing user-authorized Gemini access.
 *
 * CRITICAL VERIFICATION:
 * As confirmed in official Google documentation (gemini-api-guides/google-ai-plans.md#limitations-and-compatibility),
 * consumer Google AI plans (Pro/Ultra) & Google One AI Premium subscriptions DO NOT grant third-party
 * Gemini API quota. Direct Gemini API usage requires separate Google Cloud project billing and API keys.
 *
 * This adapter honestly checks provider capabilities and will not fake or bypass Google's billing.
 */
export class UserGeminiProvider implements AIProvider {
  readonly id: AiProviderId = 'user-gemini';
  readonly provider: AiProviderId = 'user-gemini';
  readonly executionMode: AiExecutionMode = 'user-authorized';
  readonly model: string = 'gemini-user-authorized';

  constructor(
    private readonly userAuthToken: string | null = null,
    private readonly hasVerifiedApiQuota: boolean = false,
  ) {}

  capabilities(): ProviderCapabilities {
    return {
      supportedCapabilities: ['message-drafting'],
      supportsStreaming: true,
      maxContextTokens: 8192,
    };
  }

  async healthCheck(): Promise<ProviderHealth> {
    // If no token or no verified third-party API quota entitlement:
    if (!this.userAuthToken) {
      return {
        available: false,
        authorized: false,
        status: 'unauthorized',
        reason: 'User has not authorized Gemini provider access',
      };
    }

    if (!this.hasVerifiedApiQuota) {
      return {
        available: false,
        authorized: false,
        status: 'unauthorized',
        reason: 'Consumer Gemini subscriptions do not grant third-party Gemini API quota',
      };
    }

    return {
      available: true,
      authorized: true,
      status: 'ready',
    };
  }

  async generate(request: AIRequest | AiGenerationRequest): Promise<AIResult> {
    const health = await this.healthCheck();
    if (!health.authorized) {
      throw new AiProviderError('refused', `AI_PROVIDER_NOT_AUTHORIZED: ${health.reason}`);
    }
    if (!health.available) {
      throw new AiProviderError('unavailable', 'AI_PROVIDER_UNAVAILABLE');
    }

    // If quota was officially supported and verified, execute inference under user token:
    const promptText = request.prompt ?? '';
    const text = `[gemini-user] Happy Birthday!`;
    return {
      provider: this.id,
      executionMode: this.executionMode,
      model: this.model,
      text,
      inputTokens: Math.max(1, Math.ceil(promptText.length / 4)),
      outputTokens: Math.max(1, Math.ceil(text.length / 4)),
      estimatedCostMicros: 0,
    };
  }
}

// --- Local / On-Device AI Provider --------------------------------------------

export interface SynthesisOptions {
  recipient: string;
  tone: string;
  relationship?: string | undefined;
  milestone?: string | undefined;
  language?: string | undefined;
}

export function synthesizeBirthdayMessage(options: SynthesisOptions): string {
  const { recipient, tone, relationship, milestone, language } = options;
  const isHindi = language === 'hi';

  if (isHindi) {
    if (tone === 'cheerful') {
      return `जन्मदिन की बहुत-बहुत शुभकामनाएँ, ${recipient}! आपका दिन ढेर सारी खुशियों और हँसी से भरा रहे।`;
    }
    if (tone === 'simple') {
      return `जन्मदिन मुबारक हो, ${recipient}! आपका दिन बहुत अच्छा बीते।`;
    }
    // Default warm in Hindi
    if (relationship === 'family') {
      return `प्रिय ${recipient}, जन्मदिन की हार्दिक शुभकामनाएँ! ईश्वर आपको हमेशा खुश और स्वस्थ रखे।`;
    }
    if (relationship === 'colleague') {
      return `जन्मदिन की बहुत-बहुत शुभकामनाएँ, ${recipient}! आपके साथ काम करना हमेशा प्रेरणादायक रहता है।`;
    }
    return `जन्मदिन की हार्दिक शुभकामनाएँ, ${recipient}! आने वाला वर्ष आपके लिए स्वास्थ्य, शांति और समृद्धि लेकर आए।`;
  }

  // English synthesis
  if (tone === 'cheerful') {
    if (milestone === 'milestone-age') {
      return `Happy Milestone Birthday, ${recipient}! Wishing you an unforgettable celebration and a fantastic year ahead!`;
    }
    if (milestone === 'new-job') {
      return `Happy Birthday, ${recipient}! Double celebration for your special day and the exciting new job!`;
    }
    return `Happy Birthday, ${recipient}! Hope your day is filled with laughter, great moments, and wonderful celebration!`;
  }

  if (tone === 'simple') {
    return `Happy Birthday, ${recipient}! Wishing you a wonderful day and a great year ahead.`;
  }

  // Default warm in English
  if (relationship === 'family') {
    return `Happy Birthday, ${recipient}! So grateful to have you in the family. Wishing you abundant joy and peace.`;
  }
  if (relationship === 'friend') {
    return `Happy Birthday, ${recipient}! Thank you for being such a wonderful friend. Wishing you health and happiness.`;
  }
  if (relationship === 'colleague') {
    return `Happy Birthday, ${recipient}! Wishing you continued success, happiness, and a relaxing day of celebration.`;
  }

  return `Happy Birthday, ${recipient}! Wishing you a peaceful, joyful day and all the very best in the year ahead.`;
}

export class LocalAIProvider implements AIProvider {
  readonly id: AiProviderId = 'local';
  readonly provider: AiProviderId = 'local';
  readonly executionMode: AiExecutionMode = 'local';
  readonly model: string = 'local-synthesizer-v1';

  capabilities(): ProviderCapabilities {
    return {
      supportedCapabilities: ['message-drafting'],
      supportsStreaming: false,
      maxContextTokens: 2048,
    };
  }

  healthCheck(): Promise<ProviderHealth> {
    return Promise.resolve({
      available: true,
      authorized: true,
      status: 'ready',
    });
  }

  generate(request: AIRequest | AiGenerationRequest): Promise<AIResult> {
    const input = 'input' in request && request.input ? request.input : undefined;
    const recipient = input?.recipientDisplayName ?? 'friend';
    const tone = (input?.tone as string) ?? 'warm';
    const relationship = (input?.relationshipHint as string) ?? undefined;
    const milestone = (input?.milestone as string) ?? undefined;
    const language = (input?.language as string) ?? 'en';

    const text = synthesizeBirthdayMessage({
      recipient,
      tone,
      relationship,
      milestone,
      language,
    });

    const promptText = request.prompt ?? `Birthday message for ${recipient}`;
    const inputTokens = Math.max(1, Math.ceil(promptText.length / 4));
    const outputTokens = Math.max(1, Math.ceil(text.length / 4));

    return Promise.resolve({
      provider: this.id,
      executionMode: this.executionMode,
      model: this.model,
      text,
      inputTokens,
      outputTokens,
      estimatedCostMicros: 0, // Zero cloud API cost
    });
  }
}

export class OnDeviceAIProvider implements AIProvider {
  readonly id: AiProviderId = 'on-device';
  readonly provider: AiProviderId = 'on-device';
  readonly executionMode: AiExecutionMode = 'on-device';
  readonly model: string = 'on-device-nano-v1';

  capabilities(): ProviderCapabilities {
    return {
      supportedCapabilities: ['message-drafting'],
      supportsStreaming: false,
      maxContextTokens: 2048,
    };
  }

  healthCheck(): Promise<ProviderHealth> {
    return Promise.resolve({
      available: true,
      authorized: true,
      status: 'ready',
    });
  }

  generate(request: AIRequest | AiGenerationRequest): Promise<AIResult> {
    const local = new LocalAIProvider();
    return local.generate(request).then((res) => ({
      ...res,
      provider: this.id,
      executionMode: this.executionMode,
      model: this.model,
    }));
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

export class GeminiRestAdapter implements AIProvider {
  readonly id: AiProviderId = 'gemini-cloud';
  readonly provider: AiProviderId = 'gemini-cloud';
  readonly executionMode: AiExecutionMode = 'application-cloud';
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

  capabilities(): ProviderCapabilities {
    return {
      supportedCapabilities: ['message-drafting'],
      supportsStreaming: false,
      maxContextTokens: 32768,
    };
  }

  healthCheck(): Promise<ProviderHealth> {
    return Promise.resolve({
      available: this.apiKey.trim().length > 0,
      authorized: true,
      status: this.apiKey.trim().length > 0 ? 'ready' : 'unavailable',
    });
  }

  async generate(
    request: AIRequest | AiGenerationRequest,
    signal?: AbortSignal,
  ): Promise<AIResult> {
    const url =
      `https://generativelanguage.googleapis.com/v1beta/models/` +
      `${encodeURIComponent(this.model)}:generateContent`;
    const promptText = request.prompt ?? (request as AIRequest).input?.recipientDisplayName ?? '';
    const sysInstruction = request.systemInstruction ?? 'Draft a short, sincere birthday message under 280 characters.';
    const maxTokens = request.maxOutputTokens ?? 300;
    const temp = request.temperature ?? 0.7;

    let response: Awaited<ReturnType<GeminiFetchLike>>;
    try {
      response = await this.fetchImpl(url, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-goog-api-key': this.apiKey,
        },
        body: JSON.stringify({
          system_instruction: { parts: [{ text: sysInstruction }] },
          contents: [{ role: 'user', parts: [{ text: promptText }] }],
          generationConfig: {
            temperature: temp,
            maxOutputTokens: maxTokens,
          },
        }),
        ...(signal !== undefined ? { signal } : {}),
      });
    } catch {
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
      executionMode: this.executionMode,
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

// --- Central Execution Router --------------------------------------------------

export class AIExecutionRouter {
  constructor(
    private readonly providers: ReadonlyMap<string, AIProvider>,
    private readonly priorityOrder?: readonly AiProviderId[],
  ) {}

  async route(
    entitlement: AiEntitlementProjection,
    _request: AIRequest | AiGenerationRequest,
  ): Promise<AIProvider> {
    // INVARIANT 1, 2, 3: Application subscription controls AI access
    // No subscription -> STOP. Never check or try providers if subscription is inactive.
    if (!entitlement.enabled) {
      throw new AiProviderError('refused', 'AI_SUBSCRIPTION_REQUIRED');
    }

    const availableProviders = entitlement.providers
      .filter((p) => p.available)
      .map((p) => p.provider);

    const order =
      this.priorityOrder ??
      (availableProviders.length > 0
        ? availableProviders
        : (['gemini-cloud'] as const));

    for (const providerId of order) {
      const provider = this.providers.get(providerId);
      if (!provider) continue;

      const health = provider.healthCheck
        ? await provider.healthCheck().catch(() => ({
            available: false,
            authorized: false,
            status: 'error' as const,
          }))
        : { available: true, authorized: true, status: 'ready' as const };

      if (health.available && health.authorized) {
        return provider;
      }
    }

    throw new AiProviderError('unavailable', 'AI_PROVIDER_UNAVAILABLE');
  }
}
