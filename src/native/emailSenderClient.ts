import {
  classifyEmailProviderStatus,
  type EmailDeliveryError,
  type EmailDeliveryRequest
} from '../domain/emailDelivery';

export type EmailSenderConfig = {
  endpoint?: string;
  timeoutMs: number;
};

export type EmailSendResult =
  | {
      ok: true;
      deliveryId?: string;
    }
  | {
      ok: false;
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
) => Promise<{
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
}>;

export const readEmailSenderConfig = (): EmailSenderConfig => ({
  endpoint: process.env.EXPO_PUBLIC_RELATE_EMAIL_ENDPOINT?.trim() || undefined,
  timeoutMs: Number(process.env.EXPO_PUBLIC_RELATE_EMAIL_TIMEOUT_MS) || 12000
});

const timeoutError: EmailDeliveryError = {
  kind: 'network',
  message: 'Email provider took too long to respond.'
};

const networkError: EmailDeliveryError = {
  kind: 'network',
  message: 'Email provider could not be reached.'
};

export const sendEmailMessage = async (
  request: EmailDeliveryRequest,
  config: EmailSenderConfig = readEmailSenderConfig(),
  fetcher: EmailFetch = globalThis.fetch as EmailFetch
): Promise<EmailSendResult> => {
  if (!config.endpoint) {
    return {
      ok: false,
      error: {
        kind: 'not-configured',
        message: 'No secure email delivery endpoint is configured.'
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
        error: classifyEmailProviderStatus(response.status)
      };
    }

    const payload = (await response.json()) as { deliveryId?: unknown };
    return {
      ok: true,
      deliveryId: typeof payload.deliveryId === 'string' ? payload.deliveryId : undefined
    };
  } catch (error) {
    return {
      ok: false,
      error: error instanceof Error && error.name === 'AbortError' ? timeoutError : networkError
    };
  } finally {
    clearTimeout(timeout);
  }
};
