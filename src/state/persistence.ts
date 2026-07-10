import type { AppState, PersistenceStorageHealth } from '../domain/types';
import {
  MAX_PERSISTED_RECOVERY_ISSUES,
  MAX_PERSISTED_RECORDS_PER_AGGREGATE,
  MAX_PERSISTED_TOTAL_RECORDS,
  PERSISTED_STATE_SCHEMA_VERSION,
  PersistedStateValidationError,
  assertValidPersistedState,
  createPersistenceRecoveryManifest,
  decodePersistedState,
  type PersistedAggregateName,
  type PersistedStateDecodeResult,
  type PersistenceRecoveryIssue,
  type PersistenceRecoveryManifest
} from './persistenceSchema';

export const PERSISTENCE_VERSION = PERSISTED_STATE_SCHEMA_VERSION;

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
export const MAX_DIRECT_STATE_STORAGE_LENGTH = 1800;
export const STATE_STORAGE_CHUNK_LENGTH = 1400;

const CHUNKED_STATE_STORAGE_FORMAT = 'relateai.chunked-state.v1';
export const NORMALIZED_STATE_STORAGE_FORMAT = 'relateai.normalized-state.v1';
export const MAX_PERSISTED_ENVELOPE_LENGTH = 12 * 1024 * 1024;
export const MAX_NORMALIZED_ENTRY_LENGTH = 256 * 1024;
export const MAX_NORMALIZED_MANIFEST_ENTRIES = MAX_PERSISTED_TOTAL_RECORDS + 32;

interface ChunkedStateManifest {
  storage: typeof CHUNKED_STATE_STORAGE_FORMAT;
  storageVersion: 1;
  envelopeVersion: number;
  savedAt: string;
  chunkPrefix: string;
  chunkCount: number;
  chunkLength: number;
  rawLength: number;
  checksum: string;
}

const NORMALIZED_COLLECTION_KEYS = [
  'contacts',
  'events',
  'memories',
  'gifts',
  'messages',
  'activity',
  'backups',
  'setupChecks',
  'reminderPlans'
] as const;

const NORMALIZED_SINGLETON_KEYS = [
  'styleProfile',
  'settings',
  'onboarding',
  'privacy',
  'aiProvider',
  'emailDelivery',
  'calendarSync',
  'persistence'
] as const;

type NormalizedCollectionKey = (typeof NORMALIZED_COLLECTION_KEYS)[number];
type NormalizedSingletonKey = (typeof NORMALIZED_SINGLETON_KEYS)[number];
type NormalizedEntryName = NormalizedCollectionKey | NormalizedSingletonKey | 'shell';

let normalizedSaveSequence = 0;

interface NormalizedStateEntry {
  name: NormalizedEntryName;
  kind: 'singleton' | 'collectionItem';
  index?: number;
  id?: string;
  key?: string;
  chunkPrefix?: string;
  chunkCount?: number;
  chunkLength?: number;
  rawLength: number;
  checksum: string;
}

interface NormalizedStateManifest {
  storage: typeof NORMALIZED_STATE_STORAGE_FORMAT;
  storageVersion: 1;
  envelopeVersion: number;
  savedAt: string;
  migratedFrom?: number[];
  entryPrefix: string;
  entries: NormalizedStateEntry[];
}

export type PersistenceLoadResult =
  | {
      status: 'missing';
    }
  | {
      status: 'loaded';
      state: AppState;
      migrated: boolean;
      version: number;
      recovery?: PersistenceRecoveryManifest;
    }
  | {
      status: 'recovered';
      reason: string;
    };

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const isProtectedStorageFailure = (error: unknown): error is Error =>
  error instanceof Error && error.name === 'ProtectedStorageError';

const checksumString = (value: string): string => {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
};

const isNormalizedCollectionKey = (value: unknown): value is NormalizedCollectionKey =>
  typeof value === 'string' && NORMALIZED_COLLECTION_KEYS.includes(value as NormalizedCollectionKey);

const isNormalizedSingletonKey = (value: unknown): value is NormalizedSingletonKey =>
  typeof value === 'string' && NORMALIZED_SINGLETON_KEYS.includes(value as NormalizedSingletonKey);

const isNormalizedEntryName = (value: unknown): value is NormalizedEntryName =>
  value === 'shell' || isNormalizedCollectionKey(value) || isNormalizedSingletonKey(value);

const parseChunkedStateManifest = (raw: string): ChunkedStateManifest | undefined => {
  if (raw.length > MAX_PERSISTED_ENVELOPE_LENGTH) {
    throw new Error('Chunked saved state manifest exceeds the supported size.');
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return undefined;
  }

  if (!isRecord(parsed) || parsed.storage !== CHUNKED_STATE_STORAGE_FORMAT) {
    return undefined;
  }

  if (
    parsed.storageVersion !== 1 ||
    typeof parsed.envelopeVersion !== 'number' ||
    typeof parsed.savedAt !== 'string' ||
    typeof parsed.chunkPrefix !== 'string' ||
    !parsed.chunkPrefix.startsWith(`${RELATE_STATE_KEY}.chunk.`) ||
    typeof parsed.chunkCount !== 'number' ||
    !Number.isInteger(parsed.chunkCount) ||
    parsed.chunkCount < 1 ||
    parsed.chunkCount > Math.ceil(MAX_PERSISTED_ENVELOPE_LENGTH / STATE_STORAGE_CHUNK_LENGTH) ||
    typeof parsed.chunkLength !== 'number' ||
    !Number.isInteger(parsed.chunkLength) ||
    parsed.chunkLength < 1 ||
    typeof parsed.rawLength !== 'number' ||
    !Number.isInteger(parsed.rawLength) ||
    parsed.rawLength < 1 ||
    parsed.rawLength > MAX_PERSISTED_ENVELOPE_LENGTH ||
    typeof parsed.checksum !== 'string'
  ) {
    throw new Error('Chunked saved state manifest is invalid.');
  }

  return parsed as unknown as ChunkedStateManifest;
};

const parseNormalizedStateManifest = (raw: string): NormalizedStateManifest | undefined => {
  if (raw.length > MAX_PERSISTED_ENVELOPE_LENGTH) {
    throw new Error('Normalized saved state manifest exceeds the supported size.');
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return undefined;
  }

  if (!isRecord(parsed) || parsed.storage !== NORMALIZED_STATE_STORAGE_FORMAT) {
    return undefined;
  }

  if (
    parsed.storageVersion !== 1 ||
    typeof parsed.envelopeVersion !== 'number' ||
    typeof parsed.savedAt !== 'string' ||
    typeof parsed.entryPrefix !== 'string' ||
    !parsed.entryPrefix.startsWith(`${RELATE_STATE_KEY}.entry.`) ||
    (parsed.migratedFrom !== undefined &&
      (!Array.isArray(parsed.migratedFrom) || parsed.migratedFrom.some(version => typeof version !== 'number'))) ||
    !Array.isArray(parsed.entries)
  ) {
    throw new Error('Normalized saved state manifest is invalid.');
  }

  if (parsed.entries.length > MAX_NORMALIZED_MANIFEST_ENTRIES) {
    throw new Error('Normalized saved state contains too many entries.');
  }

  const entryNames = new Set<string>();
  for (const entry of parsed.entries) {
    if (!isRecord(entry) || !isNormalizedEntryName(entry.name)) {
      throw new Error('Normalized saved state entry is invalid.');
    }
    if (entry.kind !== 'singleton' && entry.kind !== 'collectionItem') {
      throw new Error('Normalized saved state entry kind is invalid.');
    }
    if (entry.kind === 'singleton' && isNormalizedCollectionKey(entry.name)) {
      throw new Error('Normalized saved state singleton entry is invalid.');
    }
    if (
      entry.kind === 'collectionItem' &&
      (!isNormalizedCollectionKey(entry.name) ||
        typeof entry.index !== 'number' ||
        !Number.isInteger(entry.index) ||
        entry.index < 0 ||
        entry.index >= MAX_PERSISTED_RECORDS_PER_AGGREGATE)
    ) {
      throw new Error('Normalized saved state collection entry is invalid.');
    }
    if (
      typeof entry.rawLength !== 'number' ||
      !Number.isInteger(entry.rawLength) ||
      entry.rawLength < 0 ||
      entry.rawLength > MAX_NORMALIZED_ENTRY_LENGTH ||
      typeof entry.checksum !== 'string'
    ) {
      throw new Error('Normalized saved state entry integrity metadata is invalid.');
    }

    const hasSingleKey = typeof entry.key === 'string';
    const hasChunks =
      typeof entry.chunkPrefix === 'string' &&
      typeof entry.chunkCount === 'number' &&
      Number.isInteger(entry.chunkCount) &&
      entry.chunkCount > 0 &&
      entry.chunkCount <= Math.ceil(MAX_NORMALIZED_ENTRY_LENGTH / STATE_STORAGE_CHUNK_LENGTH) &&
      typeof entry.chunkLength === 'number' &&
      Number.isInteger(entry.chunkLength) &&
      entry.chunkLength > 0 &&
      entry.chunkLength <= STATE_STORAGE_CHUNK_LENGTH;
    if (hasSingleKey === hasChunks) {
      throw new Error('Normalized saved state entry storage metadata is invalid.');
    }
    if (typeof entry.key === 'string' && !entry.key.startsWith(parsed.entryPrefix)) {
      throw new Error('Normalized saved state entry key is invalid.');
    }
    if (typeof entry.chunkPrefix === 'string' && !entry.chunkPrefix.startsWith(parsed.entryPrefix)) {
      throw new Error('Normalized saved state chunk key is invalid.');
    }

    const duplicateKey = `${entry.kind}:${entry.name}:${entry.kind === 'collectionItem' ? entry.index : 'singleton'}`;
    if (entryNames.has(duplicateKey)) {
      throw new Error('Normalized saved state contains duplicate entries.');
    }
    entryNames.add(duplicateKey);
  }

  const totalRawLength = parsed.entries.reduce(
    (sum, entry) => sum + (isRecord(entry) && typeof entry.rawLength === 'number' ? entry.rawLength : 0),
    0
  );
  if (totalRawLength > MAX_PERSISTED_ENVELOPE_LENGTH) {
    throw new Error('Normalized saved state payload exceeds the supported size.');
  }

  return parsed as unknown as NormalizedStateManifest;
};

const getChunkKey = (manifest: ChunkedStateManifest, index: number) => `${manifest.chunkPrefix}${index}`;

const removeChunkedPayload = async (store: KeyValueStore, manifest: ChunkedStateManifest) => {
  await Promise.all(
    Array.from({ length: manifest.chunkCount }, async (_, index) => store.removeItem(getChunkKey(manifest, index)))
  );
};

const removePreviousChunkedPayload = async (store: KeyValueStore, previousRaw: string | null) => {
  if (!previousRaw) {
    return;
  }

  let previousManifest: ChunkedStateManifest | undefined;
  try {
    previousManifest = parseChunkedStateManifest(previousRaw);
  } catch {
    return;
  }

  if (!previousManifest) {
    return;
  }

  await removeChunkedPayload(store, previousManifest);
};

const getNormalizedEntryChunkKey = (entry: Pick<NormalizedStateEntry, 'chunkPrefix'>, index: number) =>
  `${entry.chunkPrefix}${index}`;

const removeNormalizedEntryPayload = async (store: KeyValueStore, entry: NormalizedStateEntry) => {
  if (entry.key) {
    await store.removeItem(entry.key);
    return;
  }
  await Promise.all(
    Array.from({ length: entry.chunkCount ?? 0 }, async (_, index) =>
      store.removeItem(getNormalizedEntryChunkKey(entry, index))
    )
  );
};

const removeNormalizedPayload = async (store: KeyValueStore, manifest: NormalizedStateManifest) => {
  await Promise.all(manifest.entries.map(entry => removeNormalizedEntryPayload(store, entry)));
};

const removePreviousNormalizedPayload = async (
  store: KeyValueStore,
  previousRaw: string | null,
  nextEntryPrefix?: string
) => {
  if (!previousRaw) {
    return;
  }

  let previousManifest: NormalizedStateManifest | undefined;
  try {
    previousManifest = parseNormalizedStateManifest(previousRaw);
  } catch {
    return;
  }

  if (!previousManifest || previousManifest.entryPrefix === nextEntryPrefix) {
    return;
  }

  await removeNormalizedPayload(store, previousManifest);
};

const writeNormalizedEntryPayload = async (
  store: KeyValueStore,
  entryPrefix: string,
  entryNumber: number,
  raw: string
): Promise<Pick<NormalizedStateEntry, 'key' | 'chunkPrefix' | 'chunkCount' | 'chunkLength' | 'rawLength' | 'checksum'>> => {
  const rawLength = raw.length;
  const checksum = checksumString(raw);
  const key = `${entryPrefix}${entryNumber}`;
  if (rawLength <= MAX_DIRECT_STATE_STORAGE_LENGTH) {
    await store.setItem(key, raw);
    return {
      key,
      rawLength,
      checksum
    };
  }

  const chunkPrefix = `${key}.chunk.`;
  const chunkCount = Math.ceil(rawLength / STATE_STORAGE_CHUNK_LENGTH);
  await Promise.all(
    Array.from({ length: chunkCount }, async (_, index) => {
      const start = index * STATE_STORAGE_CHUNK_LENGTH;
      await store.setItem(
        getNormalizedEntryChunkKey({ chunkPrefix }, index),
        raw.slice(start, start + STATE_STORAGE_CHUNK_LENGTH)
      );
    })
  );
  return {
    chunkPrefix,
    chunkCount,
    chunkLength: STATE_STORAGE_CHUNK_LENGTH,
    rawLength,
    checksum
  };
};

const readNormalizedEntryPayload = async (store: KeyValueStore, entry: NormalizedStateEntry): Promise<string> => {
  let raw: string;
  if (entry.key) {
    const value = await store.getItem(entry.key);
    if (value === null) {
      throw new Error(`Saved state entry ${entry.name} is missing.`);
    }
    raw = value;
  } else {
    const chunks: string[] = [];
    for (let index = 0; index < (entry.chunkCount ?? 0); index += 1) {
      const chunk = await store.getItem(getNormalizedEntryChunkKey(entry, index));
      if (chunk === null) {
        throw new Error(`Saved state entry ${entry.name} chunk ${index + 1} is missing.`);
      }
      chunks.push(chunk);
    }
    raw = chunks.join('');
  }

  if (raw.length !== entry.rawLength) {
    throw new Error(`Saved state entry ${entry.name} length does not match its manifest.`);
  }
  if (checksumString(raw) !== entry.checksum) {
    throw new Error(`Saved state entry ${entry.name} integrity check failed.`);
  }

  return raw;
};

const parseNormalizedEntryValue = (entry: NormalizedStateEntry, raw: string): unknown => {
  try {
    return JSON.parse(raw);
  } catch {
    throw new Error(`Saved state entry ${entry.name} is not valid JSON.`);
  }
};

const buildShellState = (state: AppState) => ({
  activeScreen: state.activeScreen,
  selectedContactId: state.selectedContactId,
  selectedMessageId: state.selectedMessageId,
  searchQuery: state.searchQuery
});

const saveEnvelope = async (store: KeyValueStore, envelope: PersistedStateEnvelope, previousRaw?: string | null) => {
  const existingRaw = previousRaw === undefined ? await store.getItem(RELATE_STATE_KEY) : previousRaw;
  normalizedSaveSequence += 1;
  const entryPrefix = `${RELATE_STATE_KEY}.entry.${Date.now()}.${normalizedSaveSequence}.${checksumString(
    envelope.savedAt
  )}.`;
  const entries: NormalizedStateEntry[] = [];
  let entryNumber = 0;

  const addEntry = async (
    entry: Omit<NormalizedStateEntry, 'key' | 'chunkPrefix' | 'chunkCount' | 'chunkLength' | 'rawLength' | 'checksum'>,
    value: unknown
  ) => {
    const storage = await writeNormalizedEntryPayload(store, entryPrefix, entryNumber, JSON.stringify(value));
    entryNumber += 1;
    entries.push({
      ...entry,
      ...storage
    });
  };

  await addEntry({ name: 'shell', kind: 'singleton' }, buildShellState(envelope.state));
  for (const name of NORMALIZED_SINGLETON_KEYS) {
    await addEntry({ name, kind: 'singleton' }, envelope.state[name]);
  }
  for (const name of NORMALIZED_COLLECTION_KEYS) {
    const collection = envelope.state[name];
    await Promise.all(
      collection.map(async (record, index) => {
        const currentEntryNumber = entryNumber;
        entryNumber += 1;
        const storage = await writeNormalizedEntryPayload(store, entryPrefix, currentEntryNumber, JSON.stringify(record));
        entries.push({
          name,
          kind: 'collectionItem',
          index,
          id: isRecord(record) && typeof record.id === 'string' ? record.id : undefined,
          ...storage
        });
      })
    );
  }
  entries.sort((left, right) => {
    const leftIndex = left.index ?? -1;
    const rightIndex = right.index ?? -1;
    return left.name.localeCompare(right.name) || leftIndex - rightIndex;
  });

  const manifest: NormalizedStateManifest = {
    storage: NORMALIZED_STATE_STORAGE_FORMAT,
    storageVersion: 1,
    envelopeVersion: envelope.version,
    savedAt: envelope.savedAt,
    migratedFrom: envelope.migratedFrom,
    entryPrefix,
    entries
  };

  await store.setItem(RELATE_STATE_KEY, JSON.stringify(manifest));
  await removePreviousChunkedPayload(store, existingRaw);
  await removePreviousNormalizedPayload(store, existingRaw, manifest.entryPrefix);
};

const readStoredStatePayload = async (store: KeyValueStore, activeRaw: string): Promise<string> => {
  const manifest = parseChunkedStateManifest(activeRaw);
  if (!manifest) {
    return activeRaw;
  }

  const chunks: string[] = [];
  for (let index = 0; index < manifest.chunkCount; index += 1) {
    const chunk = await store.getItem(getChunkKey(manifest, index));
    if (chunk === null) {
      throw new Error(`Saved state chunk ${index + 1} of ${manifest.chunkCount} is missing.`);
    }
    chunks.push(chunk);
  }

  const raw = chunks.join('');
  if (raw.length !== manifest.rawLength) {
    throw new Error('Saved state chunk length does not match its manifest.');
  }
  if (checksumString(raw) !== manifest.checksum) {
    throw new Error('Saved state chunk integrity check failed.');
  }

  return raw;
};

interface EntryRecoveryAccumulator {
  issueCount: number;
  excludedRecordCount: number;
  defaultedAggregates: Set<PersistedAggregateName>;
  issues: PersistenceRecoveryIssue[];
}

interface ReadStateEnvelopeResult {
  envelope: PersistedStateEnvelope;
  entryRecovery: EntryRecoveryAccumulator;
}

const emptyEntryRecovery = (): EntryRecoveryAccumulator => ({
  issueCount: 0,
  excludedRecordCount: 0,
  defaultedAggregates: new Set(),
  issues: []
});

const recordEntryRecovery = (
  recovery: EntryRecoveryAccumulator,
  entry: NormalizedStateEntry,
  code: 'missing-entry' | 'invalid-entry'
) => {
  recovery.issueCount += 1;
  if (entry.kind === 'collectionItem') {
    recovery.excludedRecordCount += 1;
  } else {
    recovery.defaultedAggregates.add(entry.name);
  }
  if (recovery.issues.length < MAX_PERSISTED_RECOVERY_ISSUES) {
    recovery.issues.push({
      aggregate: entry.name,
      code,
      ...(entry.index !== undefined ? { recordIndex: entry.index } : {})
    });
  }
};

const readNormalizedStateEnvelope = async (
  store: KeyValueStore,
  manifest: NormalizedStateManifest
): Promise<ReadStateEnvelopeResult> => {
  if (manifest.envelopeVersion > PERSISTENCE_VERSION) {
    throw new Error('Saved state was created by a newer app version.');
  }

  const partialState: Record<string, unknown> = {};
  const entryRecovery = emptyEntryRecovery();
  const collectionState = new Map<NormalizedCollectionKey, unknown[]>();
  for (const name of NORMALIZED_COLLECTION_KEYS) {
    collectionState.set(name, []);
    const indexes = manifest.entries
      .filter(entry => entry.kind === 'collectionItem' && entry.name === name)
      .map(entry => entry.index as number)
      .sort((left, right) => left - right);
    const maximumIndex = indexes.at(-1) ?? -1;
    const presentIndexes = new Set(indexes);
    for (let index = 0; index <= maximumIndex; index += 1) {
      if (!presentIndexes.has(index)) {
        entryRecovery.issueCount += 1;
        entryRecovery.excludedRecordCount += 1;
        if (entryRecovery.issues.length < MAX_PERSISTED_RECOVERY_ISSUES) {
          entryRecovery.issues.push({ aggregate: name, code: 'missing-entry', recordIndex: index });
        }
      }
    }
  }

  for (const entry of manifest.entries) {
    let value: unknown;
    try {
      const raw = await readNormalizedEntryPayload(store, entry);
      value = parseNormalizedEntryValue(entry, raw);
    } catch (error) {
      if (isProtectedStorageFailure(error)) {
        throw error;
      }
      const code = error instanceof Error && /missing/i.test(error.message) ? 'missing-entry' : 'invalid-entry';
      recordEntryRecovery(entryRecovery, entry, code);
      continue;
    }
    if (entry.name === 'shell') {
      if (!isRecord(value)) {
        recordEntryRecovery(entryRecovery, entry, 'invalid-entry');
        continue;
      }
      Object.assign(partialState, value);
      continue;
    }
    if (entry.kind === 'singleton') {
      partialState[entry.name] = value;
      continue;
    }

    if (!isNormalizedCollectionKey(entry.name) || entry.index === undefined) {
      throw new Error('Saved state collection entry is invalid.');
    }
    const collection = collectionState.get(entry.name);
    if (!collection) {
      throw new Error('Saved state collection entry is invalid.');
    }
    collection[entry.index] = value;
  }

  for (const [name, collection] of collectionState.entries()) {
    partialState[name] = collection.filter((value): value is unknown => value !== undefined);
  }

  return {
    envelope: {
      version: manifest.envelopeVersion,
      savedAt: manifest.savedAt,
      migratedFrom: manifest.migratedFrom,
      state: partialState as unknown as AppState
    },
    entryRecovery
  };
};

const readStoredStateEnvelope = async (store: KeyValueStore, activeRaw: string): Promise<ReadStateEnvelopeResult> => {
  const normalizedManifest = parseNormalizedStateManifest(activeRaw);
  if (normalizedManifest) {
    return readNormalizedStateEnvelope(store, normalizedManifest);
  }

  const raw = await readStoredStatePayload(store, activeRaw);
  return {
    envelope: parseEnvelope(raw),
    entryRecovery: emptyEntryRecovery()
  };
};

const removeStoredStatePayload = async (store: KeyValueStore, activeRaw: string) => {
  await store.removeItem(RELATE_STATE_KEY);
  await removePreviousChunkedPayload(store, activeRaw);
  await removePreviousNormalizedPayload(store, activeRaw);
};

const writeUnrecoverableManifest = async (store: KeyValueStore, sourceVersion: number) => {
  const manifest: PersistenceRecoveryManifest = {
    format: 'relateai.persistence-recovery',
    version: 1,
    redacted: true,
    recoveredAt: new Date().toISOString(),
    sourceVersion,
    outcome: 'unrecoverable',
    issueCount: 1,
    excludedRecordCount: 0,
    defaultedAggregates: ['shell'],
    issues: [{ aggregate: 'shell', code: 'invalid-entry' }],
    issuesTruncated: false
  };
  await store.setItem(RELATE_CORRUPT_STATE_KEY, JSON.stringify(manifest));
};

const combineRecovery = (
  decoded: PersistedStateDecodeResult,
  entries: EntryRecoveryAccumulator
): PersistedStateDecodeResult => ({
  state: decoded.state,
  issueCount: decoded.issueCount + entries.issueCount,
  excludedRecordCount: decoded.excludedRecordCount + entries.excludedRecordCount,
  defaultedAggregates: [...new Set([...decoded.defaultedAggregates, ...entries.defaultedAggregates])].sort(),
  issues: [...entries.issues, ...decoded.issues].slice(0, MAX_PERSISTED_RECOVERY_ISSUES)
});

const parseEnvelope = (raw: string): PersistedStateEnvelope => {
  if (raw.length === 0 || raw.length > 12 * 1024 * 1024) {
    throw new Error('Saved state envelope exceeds the supported size.');
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error('Saved state is not valid JSON.');
  }

  if (
    !isRecord(parsed) ||
    typeof parsed.version !== 'number' ||
    !Number.isInteger(parsed.version) ||
    typeof parsed.savedAt !== 'string' ||
    parsed.savedAt.length > 64 ||
    !Number.isFinite(Date.parse(parsed.savedAt)) ||
    !isRecord(parsed.state) ||
    (parsed.migratedFrom !== undefined &&
      (!Array.isArray(parsed.migratedFrom) ||
        parsed.migratedFrom.length > 50 ||
        parsed.migratedFrom.some(version => typeof version !== 'number' || !Number.isInteger(version))))
  ) {
    throw new Error('Saved state envelope is invalid.');
  }

  return parsed as unknown as PersistedStateEnvelope;
};

const decodeEnvelope = (
  envelope: PersistedStateEnvelope,
  mode: 'strict' | 'recover'
): { envelope: PersistedStateEnvelope; decoded: PersistedStateDecodeResult } => {
  if (envelope.version > PERSISTENCE_VERSION) {
    throw new Error('Saved state was created by a newer app version.');
  }
  if (envelope.version < 1) {
    throw new Error('Unsupported persisted state version.');
  }

  const decoded = decodePersistedState(envelope.state, envelope.version);
  if (mode === 'strict' && decoded.issueCount > 0) {
    throw new PersistedStateValidationError(decoded.issueCount);
  }
  return {
    envelope: {
      version: PERSISTENCE_VERSION,
      savedAt: envelope.savedAt,
      migratedFrom:
        envelope.version === PERSISTENCE_VERSION
          ? envelope.migratedFrom
          : [...(envelope.migratedFrom ?? []), envelope.version],
      state: decoded.state
    },
    decoded
  };
};

export const serializeState = (state: AppState): string => {
  const canonicalState = assertValidPersistedState(state, PERSISTENCE_VERSION);
  return JSON.stringify({
    version: PERSISTENCE_VERSION,
    savedAt: new Date().toISOString(),
    state: canonicalState
  } satisfies PersistedStateEnvelope);
};

export const deserializeState = (raw: string): AppState => {
  return decodeEnvelope(parseEnvelope(raw), 'strict').envelope.state;
};

const verifiedAt = () => new Date().toISOString();

export const inspectPersistedState = async (store: KeyValueStore): Promise<PersistenceStorageHealth> => {
  const activeRaw = await store.getItem(RELATE_STATE_KEY);
  const lastVerifiedAt = verifiedAt();
  if (!activeRaw) {
    return {
      status: 'Missing',
      storageFormat: 'Missing',
      payloadBytes: 0,
      entryCount: 0,
      chunkCount: 0,
      largestEntryBytes: 0,
      lastVerifiedAt
    };
  }

  try {
    const normalizedManifest = parseNormalizedStateManifest(activeRaw);
    if (normalizedManifest) {
      const payloads = await Promise.all(
        normalizedManifest.entries.map(entry => readNormalizedEntryPayload(store, entry))
      );
      const read = await readNormalizedStateEnvelope(store, normalizedManifest);
      const migrated = decodeEnvelope(read.envelope, 'recover');
      const decoded = combineRecovery(migrated.decoded, read.entryRecovery);
      if (decoded.issueCount > 0) {
        return {
          status: 'Corrupt',
          storageFormat: 'Corrupt',
          payloadBytes: payloads.reduce((sum, raw) => sum + raw.length, 0),
          entryCount: normalizedManifest.entries.length,
          chunkCount: normalizedManifest.entries.reduce((sum, entry) => sum + (entry.chunkCount ?? 0), 0),
          largestEntryBytes: payloads.reduce((largest, raw) => Math.max(largest, raw.length), 0),
          savedAt: normalizedManifest.savedAt,
          envelopeVersion: normalizedManifest.envelopeVersion,
          lastVerifiedAt,
          issue: `Saved state contains ${decoded.issueCount} redacted validation issue(s).`
        };
      }
      return {
        status: 'Ready',
        storageFormat: 'Normalized',
        payloadBytes: payloads.reduce((sum, raw) => sum + raw.length, 0),
        entryCount: normalizedManifest.entries.length,
        chunkCount: normalizedManifest.entries.reduce((sum, entry) => sum + (entry.chunkCount ?? 0), 0),
        largestEntryBytes: payloads.reduce((largest, raw) => Math.max(largest, raw.length), 0),
        savedAt: normalizedManifest.savedAt,
        envelopeVersion: normalizedManifest.envelopeVersion,
        lastVerifiedAt
      };
    }

    const chunkedManifest = parseChunkedStateManifest(activeRaw);
    if (chunkedManifest) {
      const raw = await readStoredStatePayload(store, activeRaw);
      const decoded = decodeEnvelope(parseEnvelope(raw), 'recover').decoded;
      if (decoded.issueCount > 0) {
        return {
          status: 'Corrupt',
          storageFormat: 'Corrupt',
          payloadBytes: raw.length,
          entryCount: 1,
          chunkCount: chunkedManifest.chunkCount,
          largestEntryBytes: raw.length,
          savedAt: chunkedManifest.savedAt,
          envelopeVersion: chunkedManifest.envelopeVersion,
          lastVerifiedAt,
          issue: `Saved state contains ${decoded.issueCount} redacted validation issue(s).`
        };
      }
      return {
        status: 'Ready',
        storageFormat: 'Legacy chunked',
        payloadBytes: raw.length,
        entryCount: 1,
        chunkCount: chunkedManifest.chunkCount,
        largestEntryBytes: raw.length,
        savedAt: chunkedManifest.savedAt,
        envelopeVersion: chunkedManifest.envelopeVersion,
        lastVerifiedAt
      };
    }

    const envelope = parseEnvelope(activeRaw);
    if (envelope.version > PERSISTENCE_VERSION) {
      throw new Error('Saved state was created by a newer app version.');
    }
    const decoded = decodeEnvelope(envelope, 'recover').decoded;
    if (decoded.issueCount > 0) {
      return {
        status: 'Corrupt',
        storageFormat: 'Corrupt',
        payloadBytes: activeRaw.length,
        entryCount: 1,
        chunkCount: 0,
        largestEntryBytes: activeRaw.length,
        savedAt: envelope.savedAt,
        envelopeVersion: envelope.version,
        lastVerifiedAt,
        issue: `Saved state contains ${decoded.issueCount} redacted validation issue(s).`
      };
    }
    return {
      status: 'Ready',
      storageFormat: 'Direct envelope',
      payloadBytes: activeRaw.length,
      entryCount: 1,
      chunkCount: 0,
      largestEntryBytes: activeRaw.length,
      savedAt: typeof envelope.savedAt === 'string' ? envelope.savedAt : undefined,
      envelopeVersion: envelope.version,
      lastVerifiedAt
    };
  } catch (error) {
    return {
      status: 'Corrupt',
      storageFormat: 'Corrupt',
      payloadBytes: activeRaw.length,
      entryCount: 0,
      chunkCount: 0,
      largestEntryBytes: 0,
      lastVerifiedAt,
      issue: error instanceof Error ? error.message : 'Stored state could not be inspected.'
    };
  }
};

export const saveState = async (store: KeyValueStore, state: AppState) => {
  const canonicalState = assertValidPersistedState(state, PERSISTENCE_VERSION);
  await saveEnvelope(store, {
    version: PERSISTENCE_VERSION,
    savedAt: new Date().toISOString(),
    state: canonicalState
  });
};

const safeUnrecoverableReason = (error: unknown): string => {
  if (error instanceof Error && /newer app version/i.test(error.message)) {
    return 'Saved state was created by a newer app version.';
  }
  if (error instanceof PersistedStateValidationError) {
    return 'Saved state did not pass bounded runtime validation.';
  }
  return 'Saved state could not be safely decoded.';
};

export const loadStateWithRecovery = async (store: KeyValueStore): Promise<PersistenceLoadResult> => {
  const activeRaw = await store.getItem(RELATE_STATE_KEY);
  if (!activeRaw) {
    return { status: 'missing' };
  }

  try {
    const read = await readStoredStateEnvelope(store, activeRaw);
    const migrated = decodeEnvelope(read.envelope, 'recover');
    const decoded = combineRecovery(migrated.decoded, read.entryRecovery);
    const versionMigrated = read.envelope.version !== migrated.envelope.version;
    let recovery: PersistenceRecoveryManifest | undefined;
    if (decoded.issueCount > 0) {
      recovery = createPersistenceRecoveryManifest(decoded, read.envelope.version);
      await store.setItem(RELATE_CORRUPT_STATE_KEY, JSON.stringify(recovery));
    } else {
      if (versionMigrated) {
        await saveEnvelope(store, migrated.envelope, activeRaw);
      }
      await store.removeItem(RELATE_CORRUPT_STATE_KEY);
    }
    return {
      status: 'loaded',
      state: decoded.state,
      migrated: versionMigrated,
      version: migrated.envelope.version,
      ...(recovery ? { recovery } : {})
    };
  } catch (error) {
    if (isProtectedStorageFailure(error)) {
      throw error;
    }
    const reason = safeUnrecoverableReason(error);
    await writeUnrecoverableManifest(store, 0);
    await removeStoredStatePayload(store, activeRaw);
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
  const activeRaw = await store.getItem(RELATE_STATE_KEY);
  if (!activeRaw) {
    await store.removeItem(RELATE_STATE_KEY);
    await store.removeItem(RELATE_CORRUPT_STATE_KEY);
    return;
  }
  await removeStoredStatePayload(store, activeRaw);
  await store.removeItem(RELATE_CORRUPT_STATE_KEY);
};
