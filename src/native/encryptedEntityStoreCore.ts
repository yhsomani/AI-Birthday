import {
  entityCollectionNames,
  entitySingletonNames,
  type DirtyStateWrite,
  type EntityArchiveTarget,
  type EntityCollectionName,
  type EntityPage,
  type EntityPageRequest,
  type EntityRepository,
  type EntitySingletonName,
  type RepositoryEntity,
  type RepositoryIndexName,
  type RepositoryIndexValue,
  type RepositoryInspection,
  type RetentionPolicy,
  type RetentionReport
} from '../domain/entityRepository';
import type { AppState } from '../domain/types';
import { MAX_NORMALIZED_ENTRY_LENGTH, MAX_PERSISTED_ENVELOPE_LENGTH, PERSISTENCE_VERSION } from '../state/persistence';
import { assertValidPersistedState } from '../state/persistenceSchema';
import {
  AES_GCM_IV_BYTES,
  AES_GCM_KEY_BYTES,
  type AesGcmKey,
  type CrossPlatformCryptoProvider,
  resolveCrossPlatformCryptoProvider
} from '../crypto/crossPlatformCrypto';

export const ENTITY_STORE_FORMAT = 'relateai.encrypted-entity-store';
export const ENTITY_STORE_ENVELOPE_VERSION = 1;
export const ENTITY_STORE_SCHEMA_VERSION = 1;
export const ENTITY_STORE_MASTER_KEY = 'relateai.entity-store.master-key.v1';
export const ENTITY_STORE_CHECKPOINT_FILES = ['checkpoint-0.enc', 'checkpoint-1.enc'] as const;

const MANIFEST_FORMAT = 'relateai.entity-store.manifest';
const CHECKPOINT_FORMAT = 'relateai.entity-store.checkpoint';
const MAX_PAGE_SIZE = 250;
const MAX_CHECKPOINT_BYTES = 64 * 1024;
const MAX_MANIFEST_BYTES = MAX_PERSISTED_ENVELOPE_LENGTH;
const MASTER_KEY_BYTES = AES_GCM_KEY_BYTES;
const repositoryIndexNames: readonly RepositoryIndexName[] = [
  'id',
  'contactId',
  'eventId',
  'date',
  'createdAt',
  'sentAt',
  'scheduledFor',
  'triggerAt',
  'status',
  'type',
  'group',
  'severity',
  'name'
];

export type EntityStoreErrorCode =
  | 'protected-storage-unavailable'
  | 'master-key-invalid'
  | 'crypto-unavailable'
  | 'corrupt-store'
  | 'newer-schema'
  | 'migration-missing'
  | 'invalid-write'
  | 'invalid-query';

export class EntityStoreError extends Error {
  readonly code: EntityStoreErrorCode;

  constructor(code: EntityStoreErrorCode, message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = 'EntityStoreError';
    this.code = code;
  }
}

export interface EncryptedStoreFileAdapter {
  read(name: string): Promise<string | null>;
  write(name: string, contents: string): Promise<void>;
  remove(name: string): Promise<void>;
  list(): Promise<readonly string[]>;
}

export interface ProtectedRepositoryKeyStore {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
  getProtectionStatus(): Promise<{
    available: boolean;
    protection: 'platform-protected' | 'unavailable';
    legacyPlaintext: 'none' | 'migration-required' | 'unknown';
  }>;
}

export interface EntityStoreMigration {
  fromVersion: number;
  toVersion: number;
  migrate(state: AppState): AppState | Promise<AppState>;
}

export interface EncryptedEntityStoreOptions {
  files: EncryptedStoreFileAdapter;
  protectedStore: ProtectedRepositoryKeyStore;
  cryptoProvider?: CrossPlatformCryptoProvider;
  now?: () => string;
  transactionId?: () => string | Promise<string>;
  targetSchemaVersion?: number;
  migrations?: readonly EntityStoreMigration[];
}

type RecordKind = 'collection' | 'singleton' | 'shell';

type StoredRecordDescriptor = {
  key: string;
  kind: RecordKind;
  collection?: EntityCollectionName;
  singleton?: EntitySingletonName;
  id: string;
  ordinal: number;
  file: string;
  rawBytes: number;
  rawChecksum: string;
  fingerprint: string;
  cipherBytes: number;
  cipherChecksum: string;
  indexes: Partial<Record<RepositoryIndexName, RepositoryIndexValue>>;
  archivedAt?: string;
};

type AggregateCounts = Record<EntityCollectionName, number>;

type StoredManifest = {
  format: typeof MANIFEST_FORMAT;
  version: 1;
  schemaVersion: number;
  generation: number;
  createdAt: string;
  stateChecksum: string;
  records: StoredRecordDescriptor[];
  aggregateCounts: AggregateCounts;
  activeCounts: AggregateCounts;
  archivedCounts: AggregateCounts;
  migrationHistory: number[];
  logicalChecksum: string;
};

type ManifestReference = {
  file: string;
  generation: number;
  cipherBytes: number;
  cipherChecksum: string;
};

type StoredCheckpoint = {
  format: typeof CHECKPOINT_FORMAT;
  version: 1;
  sequence: number;
  current: ManifestReference;
  previous?: ManifestReference;
};

type EncryptedEnvelope = {
  format: typeof ENTITY_STORE_FORMAT;
  version: typeof ENTITY_STORE_ENVELOPE_VERSION;
  algorithm: 'AES-GCM';
  iv: string;
  ciphertext: string;
};

type ResolvedManifest = {
  manifest: StoredManifest;
  reference: ManifestReference;
  recoveredFromRollback: boolean;
  checkpointSequence: number;
};

const emptyCounts = (): AggregateCounts =>
  Object.fromEntries(entityCollectionNames.map(name => [name, 0])) as AggregateCounts;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const utf8 = new TextEncoder();
const utf8Decoder = new TextDecoder();
const BASE64_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

const bytesToBase64 = (bytes: Uint8Array): string => {
  let encoded = '';
  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index];
    const second = bytes[index + 1];
    const third = bytes[index + 2];
    const combined = (first << 16) | ((second ?? 0) << 8) | (third ?? 0);
    encoded += BASE64_ALPHABET[(combined >>> 18) & 63];
    encoded += BASE64_ALPHABET[(combined >>> 12) & 63];
    encoded += second === undefined ? '=' : BASE64_ALPHABET[(combined >>> 6) & 63];
    encoded += third === undefined ? '=' : BASE64_ALPHABET[combined & 63];
  }
  return encoded;
};

const base64ToBytes = (value: string): Uint8Array => {
  if (value.length === 0 || value.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(value)) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity data contains invalid base64.');
  }
  const padding = value.endsWith('==') ? 2 : value.endsWith('=') ? 1 : 0;
  const bytes = new Uint8Array((value.length / 4) * 3 - padding);
  let output = 0;
  for (let index = 0; index < value.length; index += 4) {
    const first = BASE64_ALPHABET.indexOf(value[index]);
    const second = BASE64_ALPHABET.indexOf(value[index + 1]);
    const third = value[index + 2] === '=' ? 0 : BASE64_ALPHABET.indexOf(value[index + 2]);
    const fourth = value[index + 3] === '=' ? 0 : BASE64_ALPHABET.indexOf(value[index + 3]);
    if (first < 0 || second < 0 || third < 0 || fourth < 0) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity data contains invalid base64.');
    }
    const combined = (first << 18) | (second << 12) | (third << 6) | fourth;
    if (output < bytes.length) bytes[output++] = (combined >>> 16) & 255;
    if (output < bytes.length) bytes[output++] = (combined >>> 8) & 255;
    if (output < bytes.length) bytes[output++] = combined & 255;
  }
  return bytes;
};

const sha256 = async (value: string, cryptoProvider: CrossPlatformCryptoProvider): Promise<string> => {
  try {
    const digest = await cryptoProvider.sha256(utf8.encode(value));
    return [...digest].map(byte => byte.toString(16).padStart(2, '0')).join('');
  } catch (error) {
    throw new EntityStoreError('crypto-unavailable', 'Encrypted entity storage cannot compute a secure digest.', {
      cause: error
    });
  }
};

const fingerprint = (value: string): string => {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
};

const safeJson = (value: unknown): string => {
  const raw = JSON.stringify(value);
  if (typeof raw !== 'string') {
    throw new EntityStoreError('invalid-write', 'Repository values must be JSON serializable.');
  }
  const size = utf8.encode(raw).byteLength;
  if (size > MAX_NORMALIZED_ENTRY_LENGTH) {
    throw new EntityStoreError('invalid-write', 'A repository entity exceeds the supported size.');
  }
  return raw;
};

const stableJson = (value: unknown): string => {
  const normalize = (candidate: unknown): unknown => {
    if (Array.isArray(candidate)) {
      return candidate.map(normalize);
    }
    if (isRecord(candidate)) {
      return Object.fromEntries(
        Object.keys(candidate)
          .sort()
          .filter(key => candidate[key] !== undefined)
          .map(key => [key, normalize(candidate[key])])
      );
    }
    return candidate;
  };
  return JSON.stringify(normalize(value));
};

export const computeEntityStoreStateChecksum = async (
  state: AppState,
  cryptoProvider: CrossPlatformCryptoProvider = resolveCrossPlatformCryptoProvider()
): Promise<string> => sha256(stableJson(assertValidPersistedState(state, PERSISTENCE_VERSION)), cryptoProvider);

const defaultTransactionId = async (cryptoProvider: CrossPlatformCryptoProvider): Promise<string> => {
  const bytes = await cryptoProvider.randomBytes(16);
  return [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
};

const assertSafeFileName = (name: string): void => {
  if (!/^[a-z0-9][a-z0-9.-]{0,180}$/i.test(name) || name.includes('..')) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity storage contains an invalid file reference.');
  }
};

const encryptValue = async (
  cryptoProvider: CrossPlatformCryptoProvider,
  key: AesGcmKey,
  value: unknown,
  aad: string
): Promise<string> => {
  const iv = await cryptoProvider.randomBytes(AES_GCM_IV_BYTES);
  const plaintext = utf8.encode(JSON.stringify(value));
  const encrypted = await key.encrypt(plaintext, iv, utf8.encode(aad));
  const envelope: EncryptedEnvelope = {
    format: ENTITY_STORE_FORMAT,
    version: ENTITY_STORE_ENVELOPE_VERSION,
    algorithm: 'AES-GCM',
    iv: bytesToBase64(iv),
    ciphertext: bytesToBase64(encrypted)
  };
  return JSON.stringify(envelope);
};

const parseEncryptedEnvelope = (raw: string, maximumBytes: number): EncryptedEnvelope => {
  if (utf8.encode(raw).byteLength > maximumBytes) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity storage exceeds its supported size.');
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity storage is not valid JSON.', { cause: error });
  }
  if (
    !isRecord(parsed) ||
    parsed.format !== ENTITY_STORE_FORMAT ||
    parsed.version !== ENTITY_STORE_ENVELOPE_VERSION ||
    parsed.algorithm !== 'AES-GCM' ||
    typeof parsed.iv !== 'string' ||
    typeof parsed.ciphertext !== 'string'
  ) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity storage has an unsupported envelope.');
  }
  return parsed as EncryptedEnvelope;
};

const decryptValue = async (key: AesGcmKey, raw: string, aad: string, maximumBytes: number): Promise<unknown> => {
  const envelope = parseEncryptedEnvelope(raw, maximumBytes);
  const iv = base64ToBytes(envelope.iv);
  if (iv.byteLength !== AES_GCM_IV_BYTES) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity storage has an invalid nonce.');
  }
  try {
    const plaintext = await key.decrypt(base64ToBytes(envelope.ciphertext), iv, utf8.encode(aad));
    return JSON.parse(utf8Decoder.decode(plaintext));
  } catch (error) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity storage failed authenticated decryption.', {
      cause: error
    });
  }
};

const recordKey = (collection: EntityCollectionName, id: string): string => `collection:${collection}:${id}`;
const singletonKey = (singleton: EntitySingletonName): string => `singleton:${singleton}`;
const shellKey = 'shell';

const shellValue = (state: AppState) => ({
  activeScreen: state.activeScreen,
  selectedContactId: state.selectedContactId,
  selectedEventId: state.selectedEventId,
  selectedMessageId: state.selectedMessageId,
  searchQuery: state.searchQuery
});

const valueId = (value: unknown): string | undefined =>
  isRecord(value) && typeof value.id === 'string' && value.id.length > 0 ? value.id : undefined;

const indexValue = (value: unknown): RepositoryIndexValue | undefined =>
  typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' ? value : undefined;

const buildIndexes = (
  collection: EntityCollectionName,
  value: unknown,
  id: string
): StoredRecordDescriptor['indexes'] => {
  const indexes: StoredRecordDescriptor['indexes'] = { id };
  if (!isRecord(value)) {
    return indexes;
  }
  const common: RepositoryIndexName[] = [
    'contactId',
    'eventId',
    'date',
    'createdAt',
    'sentAt',
    'scheduledFor',
    'triggerAt',
    'status',
    'type',
    'group',
    'severity'
  ];
  for (const name of common) {
    const candidate = indexValue(value[name]);
    if (candidate !== undefined) {
      indexes[name] = candidate;
    }
  }
  if (collection === 'contacts' && typeof value.name === 'string') {
    indexes.name = value.name.trim().toLocaleLowerCase('en-IN');
  }
  return indexes;
};

const manifestWithoutChecksum = (manifest: Omit<StoredManifest, 'logicalChecksum'> | StoredManifest) => {
  const { logicalChecksum: _logicalChecksum, ...withoutChecksum } = manifest as StoredManifest;
  return withoutChecksum;
};

const computeCounts = (records: readonly StoredRecordDescriptor[]) => {
  const aggregateCounts = emptyCounts();
  const activeCounts = emptyCounts();
  const archivedCounts = emptyCounts();
  for (const descriptor of records) {
    if (descriptor.kind !== 'collection' || !descriptor.collection) {
      continue;
    }
    aggregateCounts[descriptor.collection] += 1;
    if (descriptor.archivedAt) {
      archivedCounts[descriptor.collection] += 1;
    } else {
      activeCounts[descriptor.collection] += 1;
    }
  }
  return { aggregateCounts, activeCounts, archivedCounts };
};

const descriptorOrder = (left: StoredRecordDescriptor, right: StoredRecordDescriptor): number =>
  left.kind.localeCompare(right.kind) ||
  (left.collection ?? left.singleton ?? '').localeCompare(right.collection ?? right.singleton ?? '') ||
  left.ordinal - right.ordinal ||
  left.id.localeCompare(right.id);

const isHexChecksum = (value: unknown): value is string => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);

const parseDescriptor = (value: unknown): StoredRecordDescriptor => {
  if (
    !isRecord(value) ||
    typeof value.key !== 'string' ||
    (value.kind !== 'collection' && value.kind !== 'singleton' && value.kind !== 'shell') ||
    typeof value.id !== 'string' ||
    typeof value.ordinal !== 'number' ||
    !Number.isInteger(value.ordinal) ||
    value.ordinal < 0 ||
    typeof value.file !== 'string' ||
    typeof value.rawBytes !== 'number' ||
    !Number.isInteger(value.rawBytes) ||
    value.rawBytes < 0 ||
    value.rawBytes > MAX_NORMALIZED_ENTRY_LENGTH ||
    !isHexChecksum(value.rawChecksum) ||
    typeof value.fingerprint !== 'string' ||
    !/^[a-f0-9]{8}$/.test(value.fingerprint) ||
    typeof value.cipherBytes !== 'number' ||
    !Number.isInteger(value.cipherBytes) ||
    value.cipherBytes < 1 ||
    value.cipherBytes > MAX_NORMALIZED_ENTRY_LENGTH * 2 ||
    !isHexChecksum(value.cipherChecksum) ||
    !isRecord(value.indexes) ||
    (value.archivedAt !== undefined && typeof value.archivedAt !== 'string')
  ) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest contains an invalid record.');
  }
  assertSafeFileName(value.file);
  const descriptor = value as unknown as StoredRecordDescriptor;
  for (const [name, index] of Object.entries(descriptor.indexes)) {
    if (
      !repositoryIndexNames.includes(name as RepositoryIndexName) ||
      (typeof index !== 'string' && typeof index !== 'number' && typeof index !== 'boolean')
    ) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest contains an invalid index.');
    }
  }
  if (descriptor.kind === 'collection') {
    if (!entityCollectionNames.includes(value.collection as EntityCollectionName)) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest contains an unknown aggregate.');
    }
    if (
      descriptor.key !== recordKey(descriptor.collection!, descriptor.id) ||
      descriptor.indexes.id !== descriptor.id
    ) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest record identity does not match.');
    }
  } else if (descriptor.kind === 'singleton') {
    if (!entitySingletonNames.includes(value.singleton as EntitySingletonName)) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest contains an unknown singleton.');
    }
    if (descriptor.key !== singletonKey(descriptor.singleton!) || descriptor.id !== descriptor.singleton) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest singleton identity does not match.');
    }
  } else if (descriptor.key !== shellKey || descriptor.id !== 'shell') {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest shell identity does not match.');
  }
  return descriptor;
};

const parseCounts = (value: unknown): AggregateCounts => {
  if (!isRecord(value)) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest counts are invalid.');
  }
  const counts = emptyCounts();
  for (const name of entityCollectionNames) {
    const count = value[name];
    if (typeof count !== 'number' || !Number.isInteger(count) || count < 0 || count > 10_000) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest counts are invalid.');
    }
    counts[name] = count;
  }
  return counts;
};

const parseManifest = async (value: unknown, cryptoProvider: CrossPlatformCryptoProvider): Promise<StoredManifest> => {
  if (
    !isRecord(value) ||
    value.format !== MANIFEST_FORMAT ||
    value.version !== 1 ||
    typeof value.schemaVersion !== 'number' ||
    !Number.isInteger(value.schemaVersion) ||
    value.schemaVersion < 1 ||
    typeof value.generation !== 'number' ||
    !Number.isInteger(value.generation) ||
    value.generation < 1 ||
    typeof value.createdAt !== 'string' ||
    !isHexChecksum(value.stateChecksum) ||
    !Array.isArray(value.records) ||
    value.records.length > 30_032 ||
    !Array.isArray(value.migrationHistory) ||
    value.migrationHistory.some(item => typeof item !== 'number' || !Number.isInteger(item)) ||
    !isHexChecksum(value.logicalChecksum)
  ) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest is invalid.');
  }
  const records = value.records.map(parseDescriptor);
  const keys = new Set<string>();
  for (const descriptor of records) {
    if (keys.has(descriptor.key)) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest contains duplicate records.');
    }
    keys.add(descriptor.key);
  }
  const manifest: StoredManifest = {
    ...(value as unknown as StoredManifest),
    records,
    aggregateCounts: parseCounts(value.aggregateCounts),
    activeCounts: parseCounts(value.activeCounts),
    archivedCounts: parseCounts(value.archivedCounts)
  };
  const expectedCounts = computeCounts(records);
  for (const name of entityCollectionNames) {
    if (
      manifest.aggregateCounts[name] !== expectedCounts.aggregateCounts[name] ||
      manifest.activeCounts[name] !== expectedCounts.activeCounts[name] ||
      manifest.archivedCounts[name] !== expectedCounts.archivedCounts[name]
    ) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest counts do not match its records.');
    }
  }
  const expectedChecksum = await sha256(JSON.stringify(manifestWithoutChecksum(manifest)), cryptoProvider);
  if (expectedChecksum !== manifest.logicalChecksum) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest checksum does not match.');
  }
  return manifest;
};

const parseManifestReference = (value: unknown): ManifestReference => {
  if (
    !isRecord(value) ||
    typeof value.file !== 'string' ||
    typeof value.generation !== 'number' ||
    !Number.isInteger(value.generation) ||
    value.generation < 1 ||
    typeof value.cipherBytes !== 'number' ||
    !Number.isInteger(value.cipherBytes) ||
    value.cipherBytes < 1 ||
    value.cipherBytes > MAX_MANIFEST_BYTES ||
    !isHexChecksum(value.cipherChecksum)
  ) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity checkpoint contains an invalid manifest reference.');
  }
  assertSafeFileName(value.file);
  return value as unknown as ManifestReference;
};

const parseCheckpoint = (value: unknown): StoredCheckpoint => {
  if (
    !isRecord(value) ||
    value.format !== CHECKPOINT_FORMAT ||
    value.version !== 1 ||
    typeof value.sequence !== 'number' ||
    !Number.isInteger(value.sequence) ||
    value.sequence < 1
  ) {
    throw new EntityStoreError('corrupt-store', 'Encrypted entity checkpoint is invalid.');
  }
  return {
    format: CHECKPOINT_FORMAT,
    version: 1,
    sequence: value.sequence,
    current: parseManifestReference(value.current),
    ...(value.previous ? { previous: parseManifestReference(value.previous) } : {})
  };
};

const recordAad = (descriptor: Pick<StoredRecordDescriptor, 'key' | 'rawChecksum'>): string =>
  `${ENTITY_STORE_FORMAT}:record:${descriptor.key}:${descriptor.rawChecksum}`;
const manifestAad = (generation: number): string => `${ENTITY_STORE_FORMAT}:manifest:${generation}`;
const checkpointAad = (slot: number): string => `${ENTITY_STORE_FORMAT}:checkpoint:${slot}`;

const compareIndexValues = (left: RepositoryIndexValue | undefined, right: RepositoryIndexValue | undefined) => {
  if (left === right) return 0;
  if (left === undefined) return 1;
  if (right === undefined) return -1;
  if (typeof left === 'number' && typeof right === 'number') return left - right;
  return String(left).localeCompare(String(right));
};

const encodeCursor = (generation: number, offset: number): string =>
  bytesToBase64(utf8.encode(`v1:${generation}:${offset}`));

const decodeCursor = (cursor: string | undefined): { generation?: number; offset: number } => {
  if (!cursor) return { offset: 0 };
  let value: string;
  try {
    value = utf8Decoder.decode(base64ToBytes(cursor));
  } catch (error) {
    throw new EntityStoreError('invalid-query', 'Repository page cursor is invalid.', { cause: error });
  }
  const match = /^v1:(\d+):(\d+)$/.exec(value);
  const generation = match ? Number(match[1]) : Number.NaN;
  const offset = match ? Number(match[2]) : Number.NaN;
  if (!Number.isSafeInteger(generation) || generation < 1 || !Number.isSafeInteger(offset) || offset < 0) {
    throw new EntityStoreError('invalid-query', 'Repository page cursor is invalid.');
  }
  return { generation, offset };
};

const descriptorMatches = (descriptor: StoredRecordDescriptor, request: EntityPageRequest): boolean => {
  if (!request.includeArchived && descriptor.archivedAt) {
    return false;
  }
  return (request.where ?? []).every(where => {
    const value = descriptor.indexes[where.index];
    if (where.equalTo !== undefined && compareIndexValues(value, where.equalTo) !== 0) return false;
    if (where.from !== undefined && compareIndexValues(value, where.from) < 0) return false;
    if (where.to !== undefined && compareIndexValues(value, where.to) > 0) return false;
    return value !== undefined;
  });
};

const ageInDays = (iso: string | undefined, nowMs: number): number | undefined => {
  if (!iso) return undefined;
  const then = new Date(iso).getTime();
  return Number.isFinite(then) ? Math.floor((nowMs - then) / 86_400_000) : undefined;
};

export class EncryptedTransactionalEntityStore implements EntityRepository {
  private readonly files: EncryptedStoreFileAdapter;
  private readonly protectedStore: ProtectedRepositoryKeyStore;
  private readonly cryptoProvider: CrossPlatformCryptoProvider;
  private readonly now: () => string;
  private readonly transactionId: () => string | Promise<string>;
  private readonly targetSchemaVersion: number;
  private readonly migrations: readonly EntityStoreMigration[];
  private masterKeyPromise?: Promise<AesGcmKey>;
  private operationTail: Promise<void> = Promise.resolve();
  private indexCache?: {
    manifestChecksum: string;
    indexes: Map<string, Map<string, StoredRecordDescriptor[]>>;
  };

  constructor(options: EncryptedEntityStoreOptions) {
    this.files = options.files;
    this.protectedStore = options.protectedStore;
    this.cryptoProvider = options.cryptoProvider ?? resolveCrossPlatformCryptoProvider();
    this.now = options.now ?? (() => new Date().toISOString());
    this.transactionId = options.transactionId ?? (() => defaultTransactionId(this.cryptoProvider));
    this.targetSchemaVersion = options.targetSchemaVersion ?? ENTITY_STORE_SCHEMA_VERSION;
    this.migrations = options.migrations ?? [];
  }

  private exclusive<Result>(operation: () => Promise<Result>): Promise<Result> {
    const result = this.operationTail.then(operation, operation);
    this.operationTail = result.then(
      () => undefined,
      () => undefined
    );
    return result;
  }

  private async masterKey(): Promise<AesGcmKey> {
    if (!this.masterKeyPromise) {
      this.masterKeyPromise = this.loadMasterKey().catch(error => {
        this.masterKeyPromise = undefined;
        throw error;
      });
    }
    return this.masterKeyPromise;
  }

  private async loadMasterKey(): Promise<AesGcmKey> {
    const status = await this.protectedStore.getProtectionStatus();
    if (!status.available || status.protection !== 'platform-protected' || status.legacyPlaintext === 'unknown') {
      throw new EntityStoreError(
        'protected-storage-unavailable',
        'The entity-store master key requires verified platform-protected storage. No plaintext fallback was used.'
      );
    }
    let encoded = await this.protectedStore.getItem(ENTITY_STORE_MASTER_KEY);
    if (encoded === null) {
      const existingFiles = await this.files.list();
      if (
        existingFiles.some(file =>
          /^(checkpoint-[01]\.enc|manifest-[a-z0-9-]+\.enc|record-[a-z0-9-]+\.enc)$/i.test(file)
        )
      ) {
        throw new EntityStoreError(
          'master-key-invalid',
          'The encrypted entity store exists but its protected master key is unavailable. A replacement key was not created.'
        );
      }
      let generated: Uint8Array;
      try {
        generated = await this.cryptoProvider.randomBytes(MASTER_KEY_BYTES);
      } catch (error) {
        throw new EntityStoreError('crypto-unavailable', 'A secure entity-store master key could not be generated.', {
          cause: error
        });
      }
      try {
        encoded = bytesToBase64(generated);
        await this.protectedStore.setItem(ENTITY_STORE_MASTER_KEY, encoded);
        const verified = await this.protectedStore.getItem(ENTITY_STORE_MASTER_KEY);
        if (verified !== encoded) {
          throw new EntityStoreError(
            'master-key-invalid',
            'The protected entity-store master key could not be verified.'
          );
        }
      } finally {
        generated.fill(0);
      }
    }
    const raw = base64ToBytes(encoded);
    if (raw.byteLength !== MASTER_KEY_BYTES) {
      raw.fill(0);
      throw new EntityStoreError('master-key-invalid', 'The protected entity-store master key is invalid.');
    }
    try {
      const key = await this.cryptoProvider.importAesGcmKey(raw);
      raw.fill(0);
      return key;
    } catch (error) {
      raw.fill(0);
      throw new EntityStoreError('crypto-unavailable', 'The entity-store master key could not be imported securely.', {
        cause: error
      });
    }
  }

  private async readCheckpoints(key: AesGcmKey): Promise<{ slot: number; checkpoint: StoredCheckpoint }[]> {
    const checkpoints: { slot: number; checkpoint: StoredCheckpoint }[] = [];
    for (let slot = 0; slot < ENTITY_STORE_CHECKPOINT_FILES.length; slot += 1) {
      const raw = await this.files.read(ENTITY_STORE_CHECKPOINT_FILES[slot]);
      if (raw === null) continue;
      try {
        checkpoints.push({
          slot,
          checkpoint: parseCheckpoint(await decryptValue(key, raw, checkpointAad(slot), MAX_CHECKPOINT_BYTES))
        });
      } catch {
        // A torn checkpoint slot is ignored; the other slot remains the rollback boundary.
      }
    }
    return checkpoints.sort((left, right) => right.checkpoint.sequence - left.checkpoint.sequence);
  }

  private async readManifest(key: AesGcmKey, reference: ManifestReference): Promise<StoredManifest> {
    const raw = await this.files.read(reference.file);
    if (raw === null || utf8.encode(raw).byteLength !== reference.cipherBytes) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest is missing or truncated.');
    }
    if ((await sha256(raw, this.cryptoProvider)) !== reference.cipherChecksum) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest ciphertext checksum does not match.');
    }
    const manifest = await parseManifest(
      await decryptValue(key, raw, manifestAad(reference.generation), MAX_MANIFEST_BYTES),
      this.cryptoProvider
    );
    if (manifest.generation !== reference.generation) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity manifest generation does not match.');
    }
    return manifest;
  }

  private async candidates(): Promise<{ key: AesGcmKey; manifests: ResolvedManifest[]; hasCheckpoint: boolean }> {
    const key = await this.masterKey();
    const checkpoints = await this.readCheckpoints(key);
    const manifests: ResolvedManifest[] = [];
    const seen = new Set<string>();
    const highestSequence = checkpoints[0]?.checkpoint.sequence ?? 0;
    for (const { checkpoint } of checkpoints) {
      for (const [position, reference] of [checkpoint.current, checkpoint.previous].entries()) {
        if (!reference || seen.has(reference.file)) continue;
        seen.add(reference.file);
        try {
          manifests.push({
            manifest: await this.readManifest(key, reference),
            reference,
            recoveredFromRollback: checkpoint.sequence !== highestSequence || position > 0,
            checkpointSequence: checkpoint.sequence
          });
        } catch {
          // Try the previous manifest and the other checkpoint slot.
        }
      }
    }
    return { key, manifests, hasCheckpoint: checkpoints.length > 0 };
  }

  private async current(): Promise<{ key: AesGcmKey; resolved?: ResolvedManifest }> {
    const candidates = await this.candidates();
    const resolved = candidates.manifests[0];
    if (!resolved && candidates.hasCheckpoint) {
      throw new EntityStoreError('corrupt-store', 'No verified encrypted entity-store generation is available.');
    }
    return { key: candidates.key, resolved };
  }

  private async readRecord(key: AesGcmKey, descriptor: StoredRecordDescriptor): Promise<unknown> {
    const raw = await this.files.read(descriptor.file);
    if (raw === null || utf8.encode(raw).byteLength !== descriptor.cipherBytes) {
      throw new EntityStoreError('corrupt-store', 'An encrypted entity record is missing or truncated.');
    }
    if ((await sha256(raw, this.cryptoProvider)) !== descriptor.cipherChecksum) {
      throw new EntityStoreError('corrupt-store', 'An encrypted entity ciphertext checksum does not match.');
    }
    const value = await decryptValue(key, raw, recordAad(descriptor), MAX_NORMALIZED_ENTRY_LENGTH * 2);
    const plaintext = safeJson(value);
    if (
      utf8.encode(plaintext).byteLength !== descriptor.rawBytes ||
      fingerprint(stableJson(value)) !== descriptor.fingerprint ||
      (await sha256(stableJson(value), this.cryptoProvider)) !== descriptor.rawChecksum
    ) {
      throw new EntityStoreError('corrupt-store', 'An encrypted entity record checksum does not match.');
    }
    return value;
  }

  private async stateFromManifest(key: AesGcmKey, manifest: StoredManifest): Promise<AppState> {
    const values = new Map<string, unknown>();
    await Promise.all(
      manifest.records.map(async descriptor => {
        values.set(descriptor.key, await this.readRecord(key, descriptor));
      })
    );
    const shell = values.get(shellKey);
    if (!isRecord(shell)) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity storage is missing its application shell.');
    }
    const state = {
      ...shell,
      ...Object.fromEntries(entitySingletonNames.map(name => [name, values.get(singletonKey(name))])),
      ...Object.fromEntries(
        entityCollectionNames.map(name => [
          name,
          manifest.records
            .filter(descriptor => descriptor.kind === 'collection' && descriptor.collection === name)
            .sort((left, right) => left.ordinal - right.ordinal)
            .map(descriptor => values.get(descriptor.key))
        ])
      )
    };
    const canonical = assertValidPersistedState(state, PERSISTENCE_VERSION);
    if ((await sha256(stableJson(canonical), this.cryptoProvider)) !== manifest.stateChecksum) {
      throw new EntityStoreError('corrupt-store', 'Encrypted entity state checksum does not match.');
    }
    return canonical;
  }

  private async loadVerifiedState(): Promise<{ state?: AppState; resolved?: ResolvedManifest; key: AesGcmKey }> {
    const candidates = await this.candidates();
    for (const resolved of candidates.manifests) {
      try {
        return {
          state: await this.stateFromManifest(candidates.key, resolved.manifest),
          resolved,
          key: candidates.key
        };
      } catch {
        // A damaged current generation falls back to the retained previous generation.
      }
    }
    if (candidates.hasCheckpoint) {
      throw new EntityStoreError('corrupt-store', 'No complete encrypted entity-store generation is available.');
    }
    return { key: candidates.key };
  }

  private async writeRecord(
    key: AesGcmKey,
    descriptor: Omit<
      StoredRecordDescriptor,
      'file' | 'rawBytes' | 'rawChecksum' | 'fingerprint' | 'cipherBytes' | 'cipherChecksum'
    >,
    value: unknown,
    file: string
  ): Promise<StoredRecordDescriptor> {
    const plaintext = safeJson(value);
    const rawChecksum = await sha256(stableJson(value), this.cryptoProvider);
    const base = {
      ...descriptor,
      file,
      rawBytes: utf8.encode(plaintext).byteLength,
      rawChecksum,
      fingerprint: fingerprint(stableJson(value))
    };
    const encrypted = await encryptValue(this.cryptoProvider, key, value, recordAad(base));
    await this.files.write(file, encrypted);
    const stored: StoredRecordDescriptor = {
      ...base,
      cipherBytes: utf8.encode(encrypted).byteLength,
      cipherChecksum: await sha256(encrypted, this.cryptoProvider)
    };
    await this.readRecord(key, stored);
    return stored;
  }

  private inspection(manifest: StoredManifest | undefined, recoveredFromRollback: boolean): RepositoryInspection {
    if (!manifest) {
      return {
        status: 'Missing',
        aggregateCounts: emptyCounts(),
        activeCounts: emptyCounts(),
        archivedCounts: emptyCounts(),
        recordFileCount: 0,
        payloadBytes: 0,
        largestRecordBytes: 0,
        recoveredFromRollback: false
      };
    }
    return {
      status: 'Ready',
      schemaVersion: manifest.schemaVersion,
      generation: manifest.generation,
      aggregateCounts: { ...manifest.aggregateCounts },
      activeCounts: { ...manifest.activeCounts },
      archivedCounts: { ...manifest.archivedCounts },
      stateChecksum: manifest.stateChecksum,
      manifestChecksum: manifest.logicalChecksum,
      recordFileCount: manifest.records.length,
      payloadBytes: manifest.records.reduce((sum, descriptor) => sum + descriptor.cipherBytes, 0),
      largestRecordBytes: manifest.records.reduce(
        (largest, descriptor) => Math.max(largest, descriptor.cipherBytes),
        0
      ),
      savedAt: manifest.createdAt,
      recoveredFromRollback
    };
  }

  private async buildDescriptor(
    key: AesGcmKey,
    tx: string,
    ordinalInTransaction: number,
    kind: RecordKind,
    id: string,
    ordinal: number,
    value: unknown,
    collection?: EntityCollectionName,
    singleton?: EntitySingletonName,
    previous?: StoredRecordDescriptor
  ): Promise<StoredRecordDescriptor> {
    const plaintext = safeJson(value);
    const nextFingerprint = fingerprint(stableJson(value));
    const nextRawChecksum = await sha256(stableJson(value), this.cryptoProvider);
    const indexes = collection ? buildIndexes(collection, value, id) : {};
    const entityArchivedAt = isRecord(value) && typeof value.archivedAt === 'string' ? value.archivedAt : undefined;
    if (
      previous &&
      previous.fingerprint === nextFingerprint &&
      previous.rawChecksum === nextRawChecksum &&
      previous.rawBytes === utf8.encode(plaintext).byteLength
    ) {
      return { ...previous, ordinal, indexes };
    }
    return this.writeRecord(
      key,
      {
        key:
          kind === 'collection' && collection
            ? recordKey(collection, id)
            : kind === 'singleton' && singleton
              ? singletonKey(singleton)
              : shellKey,
        kind,
        ...(collection ? { collection } : {}),
        ...(singleton ? { singleton } : {}),
        id,
        ordinal,
        indexes,
        ...((entityArchivedAt ?? previous?.archivedAt) ? { archivedAt: entityArchivedAt ?? previous?.archivedAt } : {})
      },
      value,
      `record-${tx}-${ordinalInTransaction}.enc`
    );
  }

  private async assertDirtyDeclaration(
    state: AppState,
    previousRecords: Map<string, StoredRecordDescriptor>,
    write: DirtyStateWrite
  ): Promise<void> {
    const dirtyCollections = write.collections ?? {};
    for (const collection of entityCollectionNames) {
      const declared = new Set(dirtyCollections[collection] ?? []);
      const collectionDeclared = Object.prototype.hasOwnProperty.call(dirtyCollections, collection);
      const desired = new Map(
        state[collection].map(value => {
          const id = valueId(value);
          if (!id) throw new EntityStoreError('invalid-write', `${collection} contains a record without an ID.`);
          return [id, value] as const;
        })
      );
      const existing = [...previousRecords.values()].filter(
        descriptor => descriptor.kind === 'collection' && descriptor.collection === collection
      );
      if (
        !collectionDeclared &&
        existing
          .sort((left, right) => left.ordinal - right.ordinal)
          .some((descriptor, index) => descriptor.id !== state[collection][index]?.id)
      ) {
        throw new EntityStoreError('invalid-write', `Reordering ${collection} was not declared dirty.`);
      }
      for (const descriptor of existing) {
        if (!desired.has(descriptor.id) && !declared.has(descriptor.id)) {
          throw new EntityStoreError(
            'invalid-write',
            `Deletion of ${collection}/${descriptor.id} was not declared dirty.`
          );
        }
      }
      for (const [id, value] of desired) {
        const previous = previousRecords.get(recordKey(collection, id));
        if (!previous && !declared.has(id)) {
          throw new EntityStoreError('invalid-write', `Insertion of ${collection}/${id} was not declared dirty.`);
        }
        if (previous && !declared.has(id)) {
          const raw = safeJson(value);
          if (
            previous.fingerprint !== fingerprint(stableJson(value)) ||
            previous.rawBytes !== utf8.encode(raw).byteLength ||
            previous.rawChecksum !== (await sha256(stableJson(value), this.cryptoProvider))
          ) {
            throw new EntityStoreError('invalid-write', `Change to ${collection}/${id} was not declared dirty.`);
          }
        }
      }
    }
  }

  private async commitState(
    key: AesGcmKey,
    stateInput: AppState,
    previous: ResolvedManifest | undefined,
    write?: DirtyStateWrite,
    schemaVersion = this.targetSchemaVersion,
    migrationHistory: number[] = previous?.manifest.migrationHistory ?? [],
    archiveChanges: ReadonlyMap<string, string | undefined> = new Map()
  ): Promise<ResolvedManifest> {
    const state = assertValidPersistedState(stateInput, PERSISTENCE_VERSION);
    const tx = await this.transactionId();
    if (!/^[a-z0-9-]{8,80}$/i.test(tx)) {
      throw new EntityStoreError('invalid-write', 'Repository transaction identity is invalid.');
    }
    const previousRecords = new Map(previous?.manifest.records.map(descriptor => [descriptor.key, descriptor]) ?? []);
    if (write && previous) {
      await this.assertDirtyDeclaration(state, previousRecords, write);
    }
    const nextRecords = new Map(previousRecords);
    const writtenFiles: string[] = [];
    let fileOrdinal = 0;
    const isFullReplace = !write || !previous;
    const dirtyIds = new Map(
      entityCollectionNames.map(collection => [collection, new Set(write?.collections?.[collection] ?? [])])
    );

    const shouldWriteCollection = (collection: EntityCollectionName, id: string) =>
      isFullReplace || dirtyIds.get(collection)?.has(id) === true;

    try {
      const previousShell = previousRecords.get(shellKey);
      const nextShellChecksum = await sha256(stableJson(shellValue(state)), this.cryptoProvider);
      if (isFullReplace || write?.shell || !previousShell || previousShell.rawChecksum !== nextShellChecksum) {
        const descriptor = await this.buildDescriptor(
          key,
          tx,
          fileOrdinal++,
          'shell',
          'shell',
          0,
          shellValue(state),
          undefined,
          undefined,
          previousShell
        );
        if (descriptor.file !== previousShell?.file) writtenFiles.push(descriptor.file);
        nextRecords.set(shellKey, descriptor);
      }

      for (const singleton of entitySingletonNames) {
        const descriptorKey = singletonKey(singleton);
        const old = previousRecords.get(descriptorKey);
        const value = state[singleton];
        const declared = write?.singletons?.includes(singleton) ?? false;
        if (
          !isFullReplace &&
          !declared &&
          old &&
          old.rawChecksum !== (await sha256(stableJson(value), this.cryptoProvider))
        ) {
          throw new EntityStoreError('invalid-write', `Change to singleton ${singleton} was not declared dirty.`);
        }
        if (isFullReplace || declared || !old) {
          const descriptor = await this.buildDescriptor(
            key,
            tx,
            fileOrdinal++,
            'singleton',
            singleton,
            0,
            value,
            undefined,
            singleton,
            old
          );
          if (descriptor.file !== old?.file) writtenFiles.push(descriptor.file);
          nextRecords.set(descriptorKey, descriptor);
        }
      }

      for (const collection of entityCollectionNames) {
        const desiredIds = new Set<string>();
        for (let ordinal = 0; ordinal < state[collection].length; ordinal += 1) {
          const value = state[collection][ordinal];
          const id = valueId(value);
          if (!id) throw new EntityStoreError('invalid-write', `${collection} contains a record without an ID.`);
          if (desiredIds.has(id)) throw new EntityStoreError('invalid-write', `${collection} contains a duplicate ID.`);
          desiredIds.add(id);
          const descriptorKey = recordKey(collection, id);
          const old = previousRecords.get(descriptorKey);
          if (shouldWriteCollection(collection, id) || !old) {
            const descriptor = await this.buildDescriptor(
              key,
              tx,
              fileOrdinal++,
              'collection',
              id,
              ordinal,
              value,
              collection,
              undefined,
              old
            );
            if (descriptor.file !== old?.file) writtenFiles.push(descriptor.file);
            nextRecords.set(descriptorKey, descriptor);
          } else if (old.ordinal !== ordinal) {
            nextRecords.set(descriptorKey, { ...old, ordinal });
          }
        }
        for (const descriptor of previousRecords.values()) {
          if (
            descriptor.kind === 'collection' &&
            descriptor.collection === collection &&
            !desiredIds.has(descriptor.id)
          ) {
            nextRecords.delete(descriptor.key);
          }
        }
      }

      for (const [descriptorKey, archivedAt] of archiveChanges) {
        const descriptor = nextRecords.get(descriptorKey);
        if (!descriptor || descriptor.kind !== 'collection') continue;
        nextRecords.set(descriptorKey, {
          ...descriptor,
          ...(archivedAt ? { archivedAt } : { archivedAt: undefined })
        });
      }

      const records = [...nextRecords.values()].sort(descriptorOrder);
      const counts = computeCounts(records);
      const manifestBase: Omit<StoredManifest, 'logicalChecksum'> = {
        format: MANIFEST_FORMAT,
        version: 1,
        schemaVersion,
        generation: (previous?.manifest.generation ?? 0) + 1,
        createdAt: this.now(),
        stateChecksum: await computeEntityStoreStateChecksum(state, this.cryptoProvider),
        records,
        ...counts,
        migrationHistory
      };
      const manifest: StoredManifest = {
        ...manifestBase,
        logicalChecksum: await sha256(JSON.stringify(manifestBase), this.cryptoProvider)
      };
      const manifestFile = `manifest-${tx}.enc`;
      const encryptedManifest = await encryptValue(
        this.cryptoProvider,
        key,
        manifest,
        manifestAad(manifest.generation)
      );
      await this.files.write(manifestFile, encryptedManifest);
      writtenFiles.push(manifestFile);
      const reference: ManifestReference = {
        file: manifestFile,
        generation: manifest.generation,
        cipherBytes: utf8.encode(encryptedManifest).byteLength,
        cipherChecksum: await sha256(encryptedManifest, this.cryptoProvider)
      };
      await this.readManifest(key, reference);

      const checkpoints = await this.readCheckpoints(key);
      const latestSequence = checkpoints[0]?.checkpoint.sequence ?? 0;
      const expectedSequence = previous?.checkpointSequence ?? 0;
      if (latestSequence !== expectedSequence) {
        throw new EntityStoreError(
          'invalid-write',
          'Encrypted repository changed during this transaction; retry from the latest committed state.'
        );
      }
      const nextSequence = latestSequence + 1;
      const slot = nextSequence % ENTITY_STORE_CHECKPOINT_FILES.length;
      const checkpoint: StoredCheckpoint = {
        format: CHECKPOINT_FORMAT,
        version: 1,
        sequence: nextSequence,
        current: reference,
        ...(previous ? { previous: previous.reference } : {})
      };
      const encryptedCheckpoint = await encryptValue(this.cryptoProvider, key, checkpoint, checkpointAad(slot));
      await this.files.write(ENTITY_STORE_CHECKPOINT_FILES[slot], encryptedCheckpoint);
      const verifiedCheckpoint = parseCheckpoint(
        await decryptValue(key, encryptedCheckpoint, checkpointAad(slot), MAX_CHECKPOINT_BYTES)
      );
      if (verifiedCheckpoint.current.cipherChecksum !== reference.cipherChecksum) {
        throw new EntityStoreError('corrupt-store', 'Encrypted entity checkpoint verification failed.');
      }

      const resolved: ResolvedManifest = {
        manifest,
        reference,
        recoveredFromRollback: false,
        checkpointSequence: nextSequence
      };
      await this.cleanup(key, resolved, previous);
      return resolved;
    } catch (error) {
      await Promise.all(
        writtenFiles.map(async file => {
          try {
            await this.files.remove(file);
          } catch {
            // A real process interruption can leave orphans; checkpoints never reference them.
          }
        })
      );
      throw error;
    }
  }

  private async commitArchiveChanges(
    key: AesGcmKey,
    previous: ResolvedManifest,
    archiveChanges: ReadonlyMap<string, string | undefined>
  ): Promise<ResolvedManifest> {
    const state = await this.stateFromManifest(key, previous.manifest);
    return this.commitState(
      key,
      state,
      previous,
      {
        state,
        collections: {},
        singletons: [],
        shell: false
      },
      previous.manifest.schemaVersion,
      previous.manifest.migrationHistory,
      archiveChanges
    );
  }

  private async cleanup(
    key: AesGcmKey,
    current: ResolvedManifest,
    previous: ResolvedManifest | undefined
  ): Promise<void> {
    const keep = new Set<string>([
      ...ENTITY_STORE_CHECKPOINT_FILES,
      current.reference.file,
      ...current.manifest.records.map(descriptor => descriptor.file)
    ]);
    if (previous) {
      keep.add(previous.reference.file);
      previous.manifest.records.forEach(descriptor => keep.add(descriptor.file));
    }
    try {
      // Both physical checkpoint slots are recovery boundaries. Retain every
      // valid manifest and record referenced by either slot before collecting
      // older or interrupted orphans.
      const checkpoints = await this.readCheckpoints(key);
      for (const { checkpoint } of checkpoints) {
        for (const reference of [checkpoint.current, checkpoint.previous]) {
          if (!reference) continue;
          try {
            const manifest = await this.readManifest(key, reference);
            keep.add(reference.file);
            manifest.records.forEach(descriptor => keep.add(descriptor.file));
          } catch {
            // Invalid checkpoint references are not roots for cleanup.
          }
        }
      }
      const files = await this.files.list();
      await Promise.all(
        files.filter(file => /^(record|manifest)-/.test(file) && !keep.has(file)).map(file => this.files.remove(file))
      );
    } catch {
      // Cleanup is post-commit. A cleanup failure must never roll back verified data.
    }
  }

  private async migrateIfNeeded(
    key: AesGcmKey,
    resolved: ResolvedManifest | undefined
  ): Promise<ResolvedManifest | undefined> {
    if (!resolved) return undefined;
    if (resolved.manifest.schemaVersion > this.targetSchemaVersion) {
      throw new EntityStoreError('newer-schema', 'Encrypted entity storage was created by a newer app version.');
    }
    let current = resolved;
    while (current.manifest.schemaVersion < this.targetSchemaVersion) {
      const migration = this.migrations.find(item => item.fromVersion === current.manifest.schemaVersion);
      if (!migration || migration.toVersion !== migration.fromVersion + 1) {
        throw new EntityStoreError('migration-missing', 'A required encrypted entity-store migration is missing.');
      }
      const state = assertValidPersistedState(
        await migration.migrate(await this.stateFromManifest(key, current.manifest)),
        PERSISTENCE_VERSION
      );
      current = await this.commitState(key, state, current, undefined, migration.toVersion, [
        ...current.manifest.migrationHistory,
        migration.fromVersion
      ]);
    }
    return current;
  }

  private indexedDescriptors(
    manifest: StoredManifest,
    collection: EntityCollectionName,
    request: EntityPageRequest
  ): StoredRecordDescriptor[] {
    if (!this.indexCache || this.indexCache.manifestChecksum !== manifest.logicalChecksum) {
      this.indexCache = { manifestChecksum: manifest.logicalChecksum, indexes: new Map() };
    }
    const equality = request.where?.find(where => where.equalTo !== undefined);
    if (!equality) {
      return manifest.records.filter(
        descriptor => descriptor.kind === 'collection' && descriptor.collection === collection
      );
    }
    const cacheKey = `${collection}:${equality.index}`;
    let index = this.indexCache.indexes.get(cacheKey);
    if (!index) {
      index = new Map();
      for (const descriptor of manifest.records) {
        if (descriptor.kind !== 'collection' || descriptor.collection !== collection) continue;
        const value = descriptor.indexes[equality.index];
        if (value === undefined) continue;
        const valueKey = `${typeof value}:${String(value)}`;
        const bucket = index.get(valueKey) ?? [];
        bucket.push(descriptor);
        index.set(valueKey, bucket);
      }
      this.indexCache.indexes.set(cacheKey, index);
    }
    return index.get(`${typeof equality.equalTo}:${String(equality.equalTo)}`) ?? [];
  }

  async loadState(): Promise<AppState | undefined> {
    return this.exclusive(async () => {
      const loaded = await this.loadVerifiedState();
      const resolved = await this.migrateIfNeeded(loaded.key, loaded.resolved);
      return resolved && resolved !== loaded.resolved
        ? this.stateFromManifest(loaded.key, resolved.manifest)
        : loaded.state;
    });
  }

  async replaceState(state: AppState): Promise<RepositoryInspection> {
    return this.exclusive(async () => {
      const current = await this.current();
      const resolved = await this.migrateIfNeeded(current.key, current.resolved);
      const committed = await this.commitState(current.key, state, resolved);
      return this.inspection(committed.manifest, false);
    });
  }

  async writeDirty(write: DirtyStateWrite): Promise<RepositoryInspection> {
    return this.exclusive(async () => {
      const current = await this.current();
      const resolved = await this.migrateIfNeeded(current.key, current.resolved);
      if (!resolved) {
        throw new EntityStoreError('invalid-write', 'Dirty writes require an existing repository generation.');
      }
      const committed = await this.commitState(current.key, write.state, resolved, write);
      return this.inspection(committed.manifest, false);
    });
  }

  async query<Name extends EntityCollectionName>(
    collection: Name,
    request: EntityPageRequest
  ): Promise<EntityPage<RepositoryEntity<Name>>> {
    if (!Number.isInteger(request.limit) || request.limit < 1 || request.limit > MAX_PAGE_SIZE) {
      throw new EntityStoreError('invalid-query', `Repository page limit must be between 1 and ${MAX_PAGE_SIZE}.`);
    }
    const cursor = decodeCursor(request.cursor);
    return this.exclusive(async () => {
      const current = await this.current();
      const resolved = await this.migrateIfNeeded(current.key, current.resolved);
      if (!resolved) return { items: [], matchedCount: 0 };
      if (cursor.generation !== undefined && cursor.generation !== resolved.manifest.generation) {
        throw new EntityStoreError('invalid-query', 'Repository page cursor belongs to an older generation.');
      }
      const descriptors = this.indexedDescriptors(resolved.manifest, collection, request)
        .filter(descriptor => descriptorMatches(descriptor, request))
        .sort((left, right) => {
          const order = request.orderBy
            ? compareIndexValues(left.indexes[request.orderBy], right.indexes[request.orderBy])
            : left.ordinal - right.ordinal;
          const stable = order || left.id.localeCompare(right.id);
          return request.direction === 'desc' ? -stable : stable;
        });
      const pageDescriptors = descriptors.slice(cursor.offset, cursor.offset + request.limit);
      const items = (await Promise.all(
        pageDescriptors.map(descriptor => this.readRecord(current.key, descriptor))
      )) as RepositoryEntity<Name>[];
      const nextOffset = cursor.offset + items.length;
      return {
        items,
        matchedCount: descriptors.length,
        ...(nextOffset < descriptors.length
          ? { nextCursor: encodeCursor(resolved.manifest.generation, nextOffset) }
          : {})
      };
    });
  }

  async setArchiveState(targets: readonly EntityArchiveTarget[]): Promise<RepositoryInspection> {
    return this.exclusive(async () => {
      const current = await this.current();
      const resolved = await this.migrateIfNeeded(current.key, current.resolved);
      if (!resolved) return this.inspection(undefined, false);
      const archiveChanges = new Map<string, string | undefined>();
      targets.forEach(target => archiveChanges.set(recordKey(target.collection, target.id), target.archivedAt));
      const committed = await this.commitArchiveChanges(current.key, resolved, archiveChanges);
      return this.inspection(committed.manifest, false);
    });
  }

  async applyRetentionPolicy(policy: RetentionPolicy, nowIso: string): Promise<RetentionReport> {
    const nowMs = new Date(nowIso).getTime();
    if (
      !Number.isFinite(nowMs) ||
      !Number.isInteger(policy.activity.activeDays) ||
      policy.activity.activeDays < 1 ||
      !Number.isInteger(policy.activity.maximumActive) ||
      policy.activity.maximumActive < 1 ||
      !Number.isInteger(policy.terminalMessages.archiveAfterDays) ||
      policy.terminalMessages.archiveAfterDays < 1 ||
      (policy.activity.purgeArchivedAfterDays !== undefined &&
        (!Number.isInteger(policy.activity.purgeArchivedAfterDays) || policy.activity.purgeArchivedAfterDays < 1))
    ) {
      throw new EntityStoreError('invalid-write', 'Repository retention policy is invalid.');
    }
    return this.exclusive(async () => {
      const current = await this.current();
      const resolved = await this.migrateIfNeeded(current.key, current.resolved);
      if (!resolved) {
        return {
          archivedActivity: 0,
          archivedMessages: 0,
          purgedActivity: 0,
          retainedRelationshipHistory: 0,
          appliedAt: nowIso
        };
      }
      const archiveChanges = new Map<string, string | undefined>();
      const activeActivity = resolved.manifest.records
        .filter(descriptor => descriptor.collection === 'activity' && !descriptor.archivedAt)
        .sort((left, right) => compareIndexValues(right.indexes.createdAt, left.indexes.createdAt));
      activeActivity.forEach((descriptor, index) => {
        const tooOld =
          (ageInDays(String(descriptor.indexes.createdAt ?? ''), nowMs) ?? -1) >= policy.activity.activeDays;
        if (tooOld || index >= policy.activity.maximumActive) {
          archiveChanges.set(descriptor.key, nowIso);
        }
      });
      const terminalStatuses = new Set(['Sent', 'Rejected']);
      const terminalMessages = resolved.manifest.records.filter(
        descriptor =>
          descriptor.collection === 'messages' &&
          !descriptor.archivedAt &&
          terminalStatuses.has(String(descriptor.indexes.status)) &&
          (ageInDays(String(descriptor.indexes.sentAt ?? descriptor.indexes.scheduledFor ?? ''), nowMs) ?? -1) >=
            policy.terminalMessages.archiveAfterDays
      );
      terminalMessages.forEach(descriptor => archiveChanges.set(descriptor.key, nowIso));

      const purgeIds = new Set(
        policy.activity.purgeArchivedAfterDays === undefined
          ? []
          : resolved.manifest.records
              .filter(
                descriptor =>
                  descriptor.collection === 'activity' &&
                  Boolean(descriptor.archivedAt) &&
                  (ageInDays(descriptor.archivedAt, nowMs) ?? -1) >= policy.activity.purgeArchivedAfterDays!
              )
              .map(descriptor => descriptor.id)
      );
      let committed: ResolvedManifest;
      if (purgeIds.size > 0) {
        const state = await this.stateFromManifest(current.key, resolved.manifest);
        const nextState = { ...state, activity: state.activity.filter(item => !purgeIds.has(item.id)) };
        committed = await this.commitState(
          current.key,
          nextState,
          resolved,
          undefined,
          resolved.manifest.schemaVersion,
          resolved.manifest.migrationHistory,
          archiveChanges
        );
      } else if (archiveChanges.size > 0) {
        committed = await this.commitArchiveChanges(current.key, resolved, archiveChanges);
      } else {
        committed = resolved;
      }
      return {
        archivedActivity: [...archiveChanges.keys()].filter(key => key.startsWith('collection:activity:')).length,
        archivedMessages: terminalMessages.length,
        purgedActivity: purgeIds.size,
        retainedRelationshipHistory: committed.manifest.aggregateCounts.messages,
        appliedAt: nowIso
      };
    });
  }

  async pruneRollbackGenerations(): Promise<RepositoryInspection> {
    return this.exclusive(async () => {
      const loaded = await this.loadVerifiedState();
      let resolved = await this.migrateIfNeeded(loaded.key, loaded.resolved);
      if (!resolved || !loaded.state) return this.inspection(undefined, false);
      const state =
        resolved === loaded.resolved ? loaded.state : await this.stateFromManifest(loaded.key, resolved.manifest);
      // Two manifest-only generations rotate both checkpoint slots. Since all
      // current records are reused, this is bounded regardless of entity count.
      resolved = await this.commitState(loaded.key, state, resolved);
      resolved = await this.commitState(loaded.key, state, resolved);
      return this.inspection(resolved.manifest, false);
    });
  }

  async destroyAllData(): Promise<void> {
    await this.exclusive(async () => {
      const files = await this.files.list();
      await Promise.all(files.map(file => this.files.remove(file)));
      await this.protectedStore.removeItem(ENTITY_STORE_MASTER_KEY);
      this.masterKeyPromise = undefined;
      this.indexCache = undefined;
    });
  }

  async inspect(): Promise<RepositoryInspection> {
    return this.exclusive(async () => {
      const loaded = await this.loadVerifiedState();
      const resolved = await this.migrateIfNeeded(loaded.key, loaded.resolved);
      return this.inspection(resolved?.manifest, resolved?.recoveredFromRollback ?? false);
    });
  }
}

export const createEncryptedTransactionalEntityStore = (options: EncryptedEntityStoreOptions): EntityRepository =>
  new EncryptedTransactionalEntityStore(options);
