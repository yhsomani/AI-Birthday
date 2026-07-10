import {
  buildAiProviderObservation,
  classifyAiProviderStatus,
  normalizeAiDraftResponse,
  type AiDraftError,
  type AiDraftRequest,
  type AiDraftResponseResult
} from '../domain/aiDrafting';
import { evaluateProviderEndpointReadiness } from '../domain/providerEndpointReadiness';
import {
  AI_PROVIDER_RESPONSE_MAX_BYTES,
  readBoundedJsonResponse,
  type ProviderResponseLike
} from './providerTransport';

export type AiProviderConfig = {
  endpoint?: string;
  timeoutMs: number;
  maxRequestsPerMinute?: number;
  allowLocalProviderEndpoint?: boolean;
  /** Short-lived token supplied by an authenticated provider-session adapter, never an EXPO_PUBLIC secret. */
  sessionAccessToken?: string;
  sessionExpiresAt?: string;
};

type AiFetch = (
  input: string,
  init: {
    method: 'POST';
    headers: Record<string, string>;
    body: string;
    signal: AbortSignal;
  }
) => Promise<ProviderResponseLike>;

export const readAiProviderConfig = (): AiProviderConfig => ({
  endpoint: process.env.EXPO_PUBLIC_RELATE_AI_ENDPOINT?.trim() || undefined,
  timeoutMs: Number(process.env.EXPO_PUBLIC_RELATE_AI_TIMEOUT_MS) || 12000,
  maxRequestsPerMinute: Number(process.env.EXPO_PUBLIC_RELATE_AI_MAX_REQUESTS_PER_MINUTE) || 12,
  allowLocalProviderEndpoint:
    process.env.EXPO_PUBLIC_RELATE_ALLOW_LOCAL_PROVIDER_ENDPOINTS?.trim().toLowerCase() === 'true'
});

const timeoutError: AiDraftError = {
  kind: 'timeout',
  message: 'The AI provider took too long to respond. A local template can be used instead.'
};

const networkError: AiDraftError = {
  kind: 'network',
  message: 'The AI provider could not be reached. A local template can be used instead.'
};

const rateLimitWindowMs = 60_000;
const requestHistory: number[] = [];

const validSessionToken = (config: AiProviderConfig, nowMs: number) => {
  const token = config.sessionAccessToken?.trim();
  const expiresAt = config.sessionExpiresAt ? Date.parse(config.sessionExpiresAt) : Number.NaN;
  return Boolean(token && token.length >= 16 && token.length <= 4096 && expiresAt > nowMs + 30_000);
};

export const evaluateAiProviderRateLimit = (
  history: number[],
  nowMs: number,
  maxRequestsPerMinute: number
): {
  allowed: boolean;
  nextAllowedAt?: number;
} => {
  const recentHistory = history.filter(timestamp => nowMs - timestamp < rateLimitWindowMs);
  history.splice(0, history.length, ...recentHistory);
  if (maxRequestsPerMinute <= 0 || recentHistory.length < maxRequestsPerMinute) {
    return { allowed: true };
  }

  return {
    allowed: false,
    nextAllowedAt: recentHistory[0] + rateLimitWindowMs
  };
};

export const resetAiProviderRateLimitForTests = () => {
  requestHistory.splice(0, requestHistory.length);
};

const withObservation = (
  request: AiDraftRequest,
  result: AiDraftResponseResult,
  startedAt: number
): AiDraftResponseResult => ({
  ...result,
  observation: buildAiProviderObservation(request, result, Math.max(0, Date.now() - startedAt))
});

export const requestAiDraft = async (
  request: AiDraftRequest,
  config: AiProviderConfig = readAiProviderConfig(),
  fetcher: AiFetch = globalThis.fetch as AiFetch
): Promise<AiDraftResponseResult> => {
  const startedAt = Date.now();
  if (!config.endpoint) {
    return withObservation(request, {
      ok: false,
      error: {
        kind: 'not-configured',
        message: 'No secure AI endpoint is configured for drafting.'
      }
    }, startedAt);
  }
  const endpointReadiness = evaluateProviderEndpointReadiness(config.endpoint, {
    allowLocalDevelopment: config.allowLocalProviderEndpoint
  });
  if (!endpointReadiness.canUseProviderEndpoint) {
    return withObservation(request, {
      ok: false,
      error: {
        kind: 'not-configured',
        message: `AI provider endpoint is not safe to use. ${endpointReadiness.summary}`
      }
    }, startedAt);
  }
  if (endpointReadiness.productionReady && !validSessionToken(config, startedAt)) {
    return withObservation(
      request,
      {
        ok: false,
        error: {
          kind: 'auth',
          message: 'An authenticated, short-lived provider session is required before AI drafting can use this endpoint.'
        }
      },
      startedAt
    );
  }

  const maxRequestsPerMinute = config.maxRequestsPerMinute ?? 12;
  const rateLimit = evaluateAiProviderRateLimit(requestHistory, startedAt, maxRequestsPerMinute);
  if (!rateLimit.allowed) {
    const waitSeconds = Math.max(1, Math.ceil(((rateLimit.nextAllowedAt ?? startedAt) - startedAt) / 1000));
    return withObservation(request, {
      ok: false,
      error: {
        kind: 'quota',
        message: `AI drafting is paused by the local rate limit. Try again in about ${waitSeconds} second(s).`
      }
    }, startedAt);
  }
  requestHistory.push(startedAt);

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);

  try {
    const response = await fetcher(config.endpoint, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        ...(endpointReadiness.productionReady
          ? { authorization: `Bearer ${config.sessionAccessToken?.trim()}` }
          : {})
      },
      body: JSON.stringify(request),
      signal: controller.signal
    });

    if (!response.ok) {
      return withObservation(request, {
        ok: false,
        error: classifyAiProviderStatus(response.status)
      }, startedAt);
    }

    const payload = await readBoundedJsonResponse(response, AI_PROVIDER_RESPONSE_MAX_BYTES);
    if (!payload.ok) {
      return withObservation(
        request,
        {
          ok: false,
          error: {
            kind: 'invalid-response',
            message: 'The AI provider returned an unsupported or oversized response.'
          }
        },
        startedAt
      );
    }

    return withObservation(
      request,
      normalizeAiDraftResponse(payload.value, {
        expectedLanguage: request.contact.language as 'English' | 'Hinglish' | 'Hindi',
        previousMessages: request.priorApprovedMessages
      }),
      startedAt
    );
  } catch (error) {
    return withObservation(request, {
      ok: false,
      error: error instanceof Error && error.name === 'AbortError' ? timeoutError : networkError
    }, startedAt);
  } finally {
    clearTimeout(timeout);
  }
};
