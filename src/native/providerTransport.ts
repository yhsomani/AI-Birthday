export const AI_PROVIDER_RESPONSE_MAX_BYTES = 64 * 1024;
export const EMAIL_PROVIDER_RESPONSE_MAX_BYTES = 8 * 1024;

export type ProviderResponseHeaders = {
  get(name: string): string | null;
};

export type ProviderResponseBody = {
  getReader(): {
    read(): Promise<{ done: boolean; value?: Uint8Array }>;
    cancel?(reason?: unknown): Promise<void>;
  };
};

export type ProviderResponseLike = {
  ok: boolean;
  status: number;
  headers: ProviderResponseHeaders;
  body?: ProviderResponseBody | null;
  text(): Promise<string>;
};

export type BoundedJsonResult =
  | { ok: true; value: unknown }
  | { ok: false; reason: 'content-type' | 'content-length' | 'body-too-large' | 'invalid-json' };

const utf8ByteLength = (value: string) => new TextEncoder().encode(value).byteLength;

const readBoundedStream = async (body: ProviderResponseBody, maximumBytes: number): Promise<string | undefined> => {
  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  let chunkCount = 0;
  while (true) {
    const chunk = await reader.read();
    if (chunk.done) break;
    if (!(chunk.value instanceof Uint8Array) || ++chunkCount > 4096) {
      await reader.cancel?.('invalid provider response stream');
      return undefined;
    }
    totalBytes += chunk.value.byteLength;
    if (totalBytes > maximumBytes) {
      await reader.cancel?.('provider response exceeded byte limit');
      return undefined;
    }
    chunks.push(chunk.value);
  }
  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return '';
  }
};

export const fetchProviderResponse = async (
  input: string,
  init: {
    method: 'POST';
    headers: Record<string, string>;
    body: string;
    signal: AbortSignal;
  }
): Promise<ProviderResponseLike> => {
  // Expo's native fetch exposes a ReadableStream response body, allowing the
  // byte ceiling below to stop allocation before a full provider body exists.
  const { fetch } = await import('expo/fetch');
  return fetch(input, init) as unknown as Promise<ProviderResponseLike>;
};

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

  let text: string;
  if (response.body?.getReader) {
    const streamed = await readBoundedStream(response.body, maximumBytes);
    if (streamed === undefined) return { ok: false, reason: 'body-too-large' };
    text = streamed;
  } else {
    // Non-streaming test/compatibility transports must at least provide a
    // bounded declared size before their all-at-once text allocation is used.
    if (declaredLength === null) return { ok: false, reason: 'content-length' };
    text = await response.text();
    if (utf8ByteLength(text) > maximumBytes) {
      return { ok: false, reason: 'body-too-large' };
    }
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
