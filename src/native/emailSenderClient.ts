import {
  classifyEmailProviderStatus,
  type EmailDeliveryError,
  type EmailDeliveryRequest
} from '../domain/emailDelivery';
import { evaluateProviderEndpointReadiness } from '../domain/providerEndpointReadiness';
import {
  EMAIL_PROVIDER_RESPONSE_MAX_BYTES,
  fetchProviderResponse,
  readBoundedJsonResponse,
  type ProviderResponseLike
} from './providerTransport';
import { buildAllowsLocalProviderEndpoints } from './providerDevelopmentMode';

export type EmailSenderConfig = {
  endpoint?: string;
  statusEndpoint?: string;
  timeoutMs: number;
  allowLocalProviderEndpoint?: boolean;
  /** Short-lived token supplied by an authenticated provider-session adapter, never an EXPO_PUBLIC secret. */
  sessionAccessToken?: string;
  sessionExpiresAt?: string;
};

export type EmailSendResult =
  | {
      ok: true;
      status: 'accepted' | 'sent';
      deliveryId?: string;
    }
  | {
      ok: false;
      outcome: 'failed' | 'unknown';
      idempotencyKey: string;
      error: EmailDeliveryError;
    };

export type EmailReconciliationResult =
  | { ok: true; status: 'accepted' | 'sent'; deliveryId?: string }
  | {
      ok: false;
      outcome: 'failed' | 'unknown';
      error: EmailDeliveryError;
    };

type EmailFetch = (
  input: string,
  init: {
    method: 'POST';
    headers: Record<string, string>;
    body: string;
    signal: AbortSignal;
  }
) => Promise<ProviderResponseLike>;

export const readEmailSenderConfig = (): EmailSenderConfig => ({
  endpoint: process.env.EXPO_PUBLIC_RELATE_EMAIL_ENDPOINT?.trim() || undefined,
  statusEndpoint: process.env.EXPO_PUBLIC_RELATE_EMAIL_STATUS_ENDPOINT?.trim() || undefined,
  timeoutMs: Number(process.env.EXPO_PUBLIC_RELATE_EMAIL_TIMEOUT_MS) || 12000,
  allowLocalProviderEndpoint: buildAllowsLocalProviderEndpoints(
    process.env.EXPO_PUBLIC_RELATE_ALLOW_LOCAL_PROVIDER_ENDPOINTS
  )
});

export const reconcileEmailDelivery = async (
  attempt: { idempotencyKey: string; deliveryId?: string },
  config: EmailSenderConfig = readEmailSenderConfig(),
  fetcher: EmailFetch = fetchProviderResponse
): Promise<EmailReconciliationResult> => {
  if (
    !attempt.idempotencyKey ||
    attempt.idempotencyKey.length > 512 ||
    (attempt.deliveryId !== undefined && (attempt.deliveryId.length === 0 || attempt.deliveryId.length > 256))
  ) {
    return {
      ok: false,
      outcome: 'failed',
      error: { kind: 'invalid-response', message: 'The saved delivery attempt is invalid.' }
    };
  }
  const endpointReadiness = evaluateProviderEndpointReadiness(config.statusEndpoint, {
    allowLocalDevelopment: config.allowLocalProviderEndpoint
  });
  if (!endpointReadiness.canUseProviderEndpoint) {
    return {
      ok: false,
      outcome: 'failed',
      error: {
        kind: 'not-configured',
        message: 'No safe email delivery status endpoint is configured.'
      }
    };
  }
  if (endpointReadiness.productionReady && !validSessionToken(config, Date.now())) {
    return {
      ok: false,
      outcome: 'failed',
      error: {
        kind: 'auth',
        message: 'An authenticated, short-lived provider session is required before delivery reconciliation.'
      }
    };
  }
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);
  try {
    const response = await fetcher(config.statusEndpoint!, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'idempotency-key': attempt.idempotencyKey,
        ...(endpointReadiness.productionReady ? { authorization: `Bearer ${config.sessionAccessToken?.trim()}` } : {})
      },
      body: JSON.stringify(attempt),
      signal: controller.signal
    });
    if (!response.ok) {
      return {
        ok: false,
        outcome: response.status >= 500 ? 'unknown' : 'failed',
        error: classifyEmailProviderStatus(response.status)
      };
    }
    const responseBody = await readBoundedJsonResponse(response, EMAIL_PROVIDER_RESPONSE_MAX_BYTES);
    if (!responseBody.ok || !responseBody.value || typeof responseBody.value !== 'object') {
      return {
        ok: false,
        outcome: 'unknown',
        error: { kind: 'invalid-response', message: 'The provider returned no verifiable delivery status.' }
      };
    }
    const payload = responseBody.value as { deliveryId?: unknown; status?: unknown };
    if (payload.status === 'failed') {
      return {
        ok: false,
        outcome: 'failed',
        error: { kind: 'server', message: 'The provider confirmed that delivery failed.' }
      };
    }
    if (
      (payload.status !== 'accepted' && payload.status !== 'sent') ||
      (payload.deliveryId !== undefined && (typeof payload.deliveryId !== 'string' || payload.deliveryId.length > 256))
    ) {
      return {
        ok: false,
        outcome: 'unknown',
        error: { kind: 'invalid-response', message: 'The provider returned an unsupported delivery status.' }
      };
    }
    return {
      ok: true,
      status: payload.status,
      deliveryId: typeof payload.deliveryId === 'string' ? payload.deliveryId : attempt.deliveryId
    };
  } catch (error) {
    return {
      ok: false,
      outcome: 'unknown',
      error: error instanceof Error && error.name === 'AbortError' ? timeoutError : networkError
    };
  } finally {
    clearTimeout(timeout);
  }
};

const timeoutError: EmailDeliveryError = {
  kind: 'delivery-unknown',
  message:
    'The email provider may have accepted this message before the timeout. Delivery is unknown; do not retry until its status is reconciled.'
};

const networkError: EmailDeliveryError = {
  kind: 'delivery-unknown',
  message:
    'The connection ended without a delivery result. Delivery is unknown; do not retry until its status is reconciled.'
};

const validSessionToken = (config: EmailSenderConfig, nowMs: number) => {
  const token = config.sessionAccessToken?.trim();
  const expiresAt = config.sessionExpiresAt ? Date.parse(config.sessionExpiresAt) : Number.NaN;
  return Boolean(token && token.length >= 16 && token.length <= 4096 && expiresAt > nowMs + 30_000);
};

export const sendEmailMessage = async (
  request: EmailDeliveryRequest,
  config: EmailSenderConfig = readEmailSenderConfig(),
  fetcher: EmailFetch = fetchProviderResponse
): Promise<EmailSendResult> => {
  if (!config.endpoint) {
    return {
      ok: false,
      outcome: 'failed',
      idempotencyKey: request.idempotencyKey,
      error: {
        kind: 'not-configured',
        message: 'No secure email delivery endpoint is configured.'
      }
    };
  }
  const endpointReadiness = evaluateProviderEndpointReadiness(config.endpoint, {
    allowLocalDevelopment: config.allowLocalProviderEndpoint
  });
  if (!endpointReadiness.canUseProviderEndpoint) {
    return {
      ok: false,
      outcome: 'failed',
      idempotencyKey: request.idempotencyKey,
      error: {
        kind: 'not-configured',
        message: `Email delivery endpoint is not safe to use. ${endpointReadiness.summary}`
      }
    };
  }
  if (endpointReadiness.productionReady && !validSessionToken(config, Date.now())) {
    return {
      ok: false,
      outcome: 'failed',
      idempotencyKey: request.idempotencyKey,
      error: {
        kind: 'auth',
        message:
          'An authenticated, short-lived provider session is required before email delivery can use this endpoint.'
      }
    };
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);

  try {
    const response = await fetcher(config.endpoint, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'idempotency-key': request.idempotencyKey,
        ...(endpointReadiness.productionReady ? { authorization: `Bearer ${config.sessionAccessToken?.trim()}` } : {})
      },
      body: JSON.stringify(request),
      signal: controller.signal
    });

    if (!response.ok) {
      return {
        ok: false,
        outcome: 'failed',
        idempotencyKey: request.idempotencyKey,
        error: classifyEmailProviderStatus(response.status)
      };
    }

    const responseBody = await readBoundedJsonResponse(response, EMAIL_PROVIDER_RESPONSE_MAX_BYTES);
    if (!responseBody.ok || !responseBody.value || typeof responseBody.value !== 'object') {
      return {
        ok: false,
        outcome: 'unknown',
        idempotencyKey: request.idempotencyKey,
        error: {
          kind: 'invalid-response',
          message:
            'The provider returned no bounded, verifiable JSON delivery status. Do not retry until the attempt is reconciled.'
        }
      };
    }
    const payload = responseBody.value as { deliveryId?: unknown; status?: unknown };
    if (typeof payload.deliveryId !== 'string' || (payload.status !== 'accepted' && payload.status !== 'sent')) {
      return {
        ok: false,
        outcome: 'unknown',
        idempotencyKey: request.idempotencyKey,
        error: {
          kind: 'invalid-response',
          message:
            'The provider accepted the request but returned no verifiable delivery status. Do not retry until the attempt is reconciled.'
        }
      };
    }
    return {
      ok: true,
      status: payload.status,
      deliveryId: payload.deliveryId
    };
  } catch (error) {
    return {
      ok: false,
      outcome: 'unknown',
      idempotencyKey: request.idempotencyKey,
      error: error instanceof Error && error.name === 'AbortError' ? timeoutError : networkError
    };
  } finally {
    clearTimeout(timeout);
  }
};
