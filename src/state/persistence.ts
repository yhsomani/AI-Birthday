import { initialState } from '../data/seed';
import type { AppState } from '../domain/types';

export const PERSISTENCE_VERSION = 2;

export interface PersistedStateEnvelope {
  version: number;
  savedAt: string;
  state: AppState;
  migratedFrom?: number[];
}

export interface KeyValueStore {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

export const RELATE_STATE_KEY = 'relateai.secure.state.v1';
export const RELATE_CORRUPT_STATE_KEY = 'relateai.secure.state.corrupt.v1';

export type PersistenceLoadResult =
  | {
      status: 'missing';
    }
  | {
      status: 'loaded';
      state: AppState;
      migrated: boolean;
      version: number;
    }
  | {
      status: 'recovered';
      reason: string;
    };

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const cloneInitialState = (): AppState => structuredClone(initialState);

const normalizePersistedState = (state: unknown): AppState => {
  if (!isRecord(state)) {
    throw new Error('Persisted state payload is invalid.');
  }

  const defaults = cloneInitialState();
  const partial = state as Partial<AppState>;
  return {
    ...defaults,
    ...partial,
    contacts: Array.isArray(partial.contacts) ? partial.contacts : defaults.contacts,
    events: Array.isArray(partial.events) ? partial.events : defaults.events,
    memories: Array.isArray(partial.memories) ? partial.memories : defaults.memories,
    gifts: Array.isArray(partial.gifts) ? partial.gifts : defaults.gifts,
    messages: Array.isArray(partial.messages) ? partial.messages : defaults.messages,
    activity: Array.isArray(partial.activity) ? partial.activity : defaults.activity,
    settings: {
      ...defaults.settings,
      ...partial.settings
    },
    aiProvider: {
      ...defaults.aiProvider,
      ...partial.aiProvider
    },
    emailDelivery: {
      ...defaults.emailDelivery,
      ...partial.emailDelivery
    },
    calendarSync: {
      ...defaults.calendarSync,
      ...partial.calendarSync
    },
    persistence: {
      ...defaults.persistence,
      ...partial.persistence,
      status: 'Ready'
    }
  };
};

const parseEnvelope = (raw: string): PersistedStateEnvelope => {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error('Saved state is not valid JSON.');
  }

  if (!isRecord(parsed) || typeof parsed.version !== 'number' || !('state' in parsed)) {
    throw new Error('Saved state envelope is invalid.');
  }

  return parsed as unknown as PersistedStateEnvelope;
};

const migrateEnvelope = (envelope: PersistedStateEnvelope): PersistedStateEnvelope => {
  if (envelope.version > PERSISTENCE_VERSION) {
    throw new Error('Saved state was created by a newer app version.');
  }
  if (envelope.version < 1) {
    throw new Error('Unsupported persisted state version.');
  }

  const state = normalizePersistedState(envelope.state);
  return {
    version: PERSISTENCE_VERSION,
    savedAt: typeof envelope.savedAt === 'string' ? envelope.savedAt : new Date().toISOString(),
    migratedFrom:
      envelope.version === PERSISTENCE_VERSION
        ? envelope.migratedFrom
        : [...(envelope.migratedFrom ?? []), envelope.version],
    state
  };
};

export const serializeState = (state: AppState): string =>
  JSON.stringify({
    version: PERSISTENCE_VERSION,
    savedAt: new Date().toISOString(),
    state
  } satisfies PersistedStateEnvelope);

export const deserializeState = (raw: string): AppState => {
  return migrateEnvelope(parseEnvelope(raw)).state;
};

export const saveState = async (store: KeyValueStore, state: AppState) => {
  await store.setItem(RELATE_STATE_KEY, serializeState(state));
};

export const loadStateWithRecovery = async (store: KeyValueStore): Promise<PersistenceLoadResult> => {
  const raw = await store.getItem(RELATE_STATE_KEY);
  if (!raw) {
    return { status: 'missing' };
  }

  try {
    const envelope = parseEnvelope(raw);
    const migrated = migrateEnvelope(envelope);
    if (envelope.version !== migrated.version) {
      await store.setItem(RELATE_STATE_KEY, JSON.stringify(migrated));
    }
    return {
      status: 'loaded',
      state: migrated.state,
      migrated: envelope.version !== migrated.version,
      version: migrated.version
    };
  } catch (error) {
    const reason = error instanceof Error ? error.message : 'Saved state could not be loaded.';
    await store.setItem(
      RELATE_CORRUPT_STATE_KEY,
      JSON.stringify({
        quarantinedAt: new Date().toISOString(),
        reason,
        raw
      })
    );
    await store.removeItem(RELATE_STATE_KEY);
    return {
      status: 'recovered',
      reason
    };
  }
};

export const loadState = async (store: KeyValueStore): Promise<AppState | undefined> => {
  const result = await loadStateWithRecovery(store);
  return result.status === 'loaded' ? result.state : undefined;
};

export const clearState = async (store: KeyValueStore) => {
  await store.removeItem(RELATE_STATE_KEY);
};
