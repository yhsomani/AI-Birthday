import {
  classifyAiProviderStatus,
  normalizeAiDraftResponse,
  type AiDraftError,
  type AiDraftRequest,
  type AiDraftResponseResult
} from '../domain/aiDrafting';

export type AiProviderConfig = {
  endpoint?: string;
  timeoutMs: number;
};

type AiFetch = (
  input: string,
  init: {
    method: 'POST';
    headers: Record<string, string>;
    body: string;
    signal: AbortSignal;
  }
) => Promise<{
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
}>;

export const readAiProviderConfig = (): AiProviderConfig => ({
  endpoint: process.env.EXPO_PUBLIC_RELATE_AI_ENDPOINT?.trim() || undefined,
  timeoutMs: Number(process.env.EXPO_PUBLIC_RELATE_AI_TIMEOUT_MS) || 12000
});

const timeoutError: AiDraftError = {
  kind: 'timeout',
  message: 'The AI provider took too long to respond. A local template can be used instead.'
};

const networkError: AiDraftError = {
  kind: 'network',
  message: 'The AI provider could not be reached. A local template can be used instead.'
};

export const requestAiDraft = async (
  request: AiDraftRequest,
  config: AiProviderConfig = readAiProviderConfig(),
  fetcher: AiFetch = globalThis.fetch as AiFetch
): Promise<AiDraftResponseResult> => {
  if (!config.endpoint) {
    return {
      ok: false,
      error: {
        kind: 'not-configured',
        message: 'No secure AI endpoint is configured for drafting.'
      }
    };
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);

  try {
    const response = await fetcher(config.endpoint, {
      method: 'POST',
      headers: {
        'content-type': 'application/json'
      },
      body: JSON.stringify(request),
      signal: controller.signal
    });

    if (!response.ok) {
      return {
        ok: false,
        error: classifyAiProviderStatus(response.status)
      };
    }

    return normalizeAiDraftResponse(await response.json());
  } catch (error) {
    return {
      ok: false,
      error: error instanceof Error && error.name === 'AbortError' ? timeoutError : networkError
    };
  } finally {
    clearTimeout(timeout);
  }
};
