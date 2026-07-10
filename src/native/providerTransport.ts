export const AI_PROVIDER_RESPONSE_MAX_BYTES = 64 * 1024;
export const EMAIL_PROVIDER_RESPONSE_MAX_BYTES = 8 * 1024;

export type ProviderResponseHeaders = {
  get(name: string): string | null;
};

export type ProviderResponseLike = {
  ok: boolean;
  status: number;
  headers: ProviderResponseHeaders;
  text(): Promise<string>;
};

export type BoundedJsonResult =
  | { ok: true; value: unknown }
  | { ok: false; reason: 'content-type' | 'content-length' | 'body-too-large' | 'invalid-json' };

const utf8ByteLength = (value: string) => new TextEncoder().encode(value).byteLength;

export const readBoundedJsonResponse = async (
  response: ProviderResponseLike,
  maximumBytes: number
): Promise<BoundedJsonResult> => {
  const contentType = response.headers.get('content-type')?.split(';', 1)[0]?.trim().toLowerCase();
  if (contentType !== 'application/json' && contentType !== 'application/problem+json') {
    return { ok: false, reason: 'content-type' };
  }

  const declaredLength = response.headers.get('content-length');
  if (declaredLength !== null) {
    const parsedLength = Number(declaredLength);
    if (!Number.isInteger(parsedLength) || parsedLength < 0 || parsedLength > maximumBytes) {
      return { ok: false, reason: 'content-length' };
    }
  }

  const text = await response.text();
  if (utf8ByteLength(text) > maximumBytes) {
    return { ok: false, reason: 'body-too-large' };
  }

  try {
    return { ok: true, value: JSON.parse(text) as unknown };
  } catch {
    return { ok: false, reason: 'invalid-json' };
  }
};

export const staticJsonResponse = (
  value: unknown,
  options: { ok?: boolean; status?: number; contentType?: string; contentLength?: string } = {}
): ProviderResponseLike => {
  const body = JSON.stringify(value);
  const headers = new Map<string, string>([
    ['content-type', options.contentType ?? 'application/json'],
    ['content-length', options.contentLength ?? String(utf8ByteLength(body))]
  ]);
  return {
    ok: options.ok ?? true,
    status: options.status ?? 200,
    headers: {
      get: name => headers.get(name.toLowerCase()) ?? null
    },
    text: async () => body
  };
};
