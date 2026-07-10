export type ProviderSessionKind = 'ai' | 'email';

export type ProviderSession = Readonly<{
  accessToken: string;
  expiresAt: string;
}>;

export interface ProviderSessionSource {
  /** Fetches an authenticated short-lived token; implementations must never persist it in public config. */
  getActiveSession(kind: ProviderSessionKind, signal: AbortSignal): Promise<ProviderSession | undefined>;
  /** Reports readiness without returning or logging token material. */
  hasActiveSession(kind: ProviderSessionKind): boolean | Promise<boolean>;
}

export const unavailableProviderSessions: ProviderSessionSource = {
  getActiveSession: async () => undefined,
  hasActiveSession: () => false
};

export const readValidatedProviderSession = async (
  source: ProviderSessionSource,
  kind: ProviderSessionKind,
  signal: AbortSignal,
  now = new Date()
): Promise<ProviderSession | undefined> => {
  if (signal.aborted) throw new Error('Operation cancelled.');
  const session = await source.getActiveSession(kind, signal);
  if (signal.aborted) throw new Error('Operation cancelled.');
  if (!session) return undefined;
  const token = session.accessToken.trim();
  const expiresAt = Date.parse(session.expiresAt);
  if (token.length < 16 || token.length > 4_096 || !Number.isFinite(expiresAt) || expiresAt <= now.getTime() + 30_000) {
    return undefined;
  }
  return { accessToken: token, expiresAt: new Date(expiresAt).toISOString() };
};
