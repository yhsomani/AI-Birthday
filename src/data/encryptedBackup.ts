import { pbkdf2Async } from '@noble/hashes/pbkdf2.js';
import { sha256 } from '@noble/hashes/sha2.js';
import {
  AES_GCM_IV_BYTES,
  type CrossPlatformCryptoProvider,
  resolveCrossPlatformCryptoProvider
} from '../crypto/crossPlatformCrypto';
import type { AppState } from '../domain/types';
import { deserializeState, PERSISTENCE_VERSION, serializeState } from '../state/persistence';

export const BACKUP_FORMAT = 'relateai.encrypted-backup';
export const BACKUP_VERSION = 2;
export const BACKUP_FILE_EXTENSION = 'relateai-backup';
export const DEFAULT_BACKUP_KDF_ITERATIONS = 120_000;
export const MIN_BACKUP_KDF_ITERATIONS = 1_000;
export const MAX_BACKUP_KDF_ITERATIONS = 600_000;
export const MIN_BACKUP_PASSPHRASE_LENGTH = 12;
export const MAX_BACKUP_PASSPHRASE_LENGTH = 256;
export const MAX_BACKUP_RAW_BYTES = 9 * 1024 * 1024;
export const MAX_BACKUP_PLAINTEXT_BYTES = 6 * 1024 * 1024;
export const MAX_BACKUP_CIPHERTEXT_BYTES = MAX_BACKUP_PLAINTEXT_BYTES + 16;
export const MAX_BACKUP_RECORDS_PER_COLLECTION = 10_000;
export const MAX_BACKUP_TOTAL_RECORDS = 30_000;
export const MAX_BACKUP_FIELD_LENGTH = 32_768;

const BACKUP_SALT_BYTES = 16;
const BACKUP_IV_BYTES = AES_GCM_IV_BYTES;
const SHA_256_BYTES = 32;
const MAX_BACKUP_OBJECT_KEYS = 96;
const MAX_BACKUP_JSON_DEPTH = 12;
const MAX_BACKUP_NESTED_ARRAY_ITEMS = 10_000;

export type BackupRecordCounts = {
  contacts: number;
  events: number;
  memories: number;
  gifts: number;
  messages: number;
  activity: number;
};

type BackupEnvelopeBase = {
  format: typeof BACKUP_FORMAT;
  app: 'RelateAI';
  createdAt: string;
  encrypted: true;
  persistenceVersion: number;
  recordCounts: BackupRecordCounts;
  kdf: {
    algorithm: 'PBKDF2-SHA-256';
    iterations: number;
    salt: string;
  };
  cipher: {
    algorithm: 'AES-GCM';
    iv: string;
    ciphertext: string;
  };
};

export type EncryptedBackupEnvelopeV1 = BackupEnvelopeBase & {
  version: 1;
  checksum: {
    algorithm: 'SHA-256';
    ciphertext: string;
    plaintext: string;
  };
};

export type EncryptedBackupEnvelopeV2 = BackupEnvelopeBase & {
  version: 2;
  checksum: {
    algorithm: 'SHA-256';
    ciphertext: string;
  };
};

export type EncryptedBackupEnvelope = EncryptedBackupEnvelopeV1 | EncryptedBackupEnvelopeV2;

export type BackupPreview = {
  format: string;
  version: number;
  app: string;
  createdAt: string;
  encrypted: boolean;
  persistenceVersion: number;
  recordCounts: BackupRecordCounts;
  recordCount: number;
  warnings: string[];
};

export type BackupCryptoOptions = {
  iterations?: number;
  createdAt?: string;
  /** Test/migration fixture support only. Production exports default to v2. */
  formatVersion?: 1 | 2;
  cryptoProvider?: CrossPlatformCryptoProvider;
};

export type BackupDecryptOptions = {
  cryptoProvider?: CrossPlatformCryptoProvider;
};

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const assertExactKeys = (
  value: Record<string, unknown>,
  required: readonly string[],
  optional: readonly string[],
  label: string
) => {
  const allowed = new Set([...required, ...optional]);
  if (required.some(key => !(key in value)) || Object.keys(value).some(key => !allowed.has(key))) {
    throw new Error(`${label} has an invalid schema.`);
  }
};

const assertRecord = (value: unknown, label: string): Record<string, unknown> => {
  if (!isRecord(value)) {
    throw new Error(`${label} is invalid.`);
  }
  return value;
};

const assertString = (value: unknown, label: string, maximum = MAX_BACKUP_FIELD_LENGTH): string => {
  if (typeof value !== 'string' || value.length > maximum) {
    throw new Error(`${label} is invalid or too long.`);
  }
  return value;
};

const assertNumber = (
  value: unknown,
  label: string,
  options: { integer?: boolean; minimum?: number; maximum?: number } = {}
): number => {
  if (
    typeof value !== 'number' ||
    !Number.isFinite(value) ||
    (options.integer === true && !Number.isInteger(value)) ||
    (options.minimum !== undefined && value < options.minimum) ||
    (options.maximum !== undefined && value > options.maximum)
  ) {
    throw new Error(`${label} is invalid or outside the supported range.`);
  }
  return value;
};

const assertIsoTimestamp = (value: unknown, label: string): string => {
  const timestamp = assertString(value, label, 40);
  if (!Number.isFinite(Date.parse(timestamp))) {
    throw new Error(`${label} is invalid.`);
  }
  return timestamp;
};

const assertArray = (value: unknown, label: string, maximum = MAX_BACKUP_NESTED_ARRAY_ITEMS): unknown[] => {
  if (!Array.isArray(value) || value.length > maximum) {
    throw new Error(`${label} is invalid or contains too many items.`);
  }
  return value;
};

const assertBoundedJsonTree = (value: unknown, label: string, depth = 0): void => {
  if (depth > MAX_BACKUP_JSON_DEPTH) {
    throw new Error(`${label} is nested too deeply.`);
  }
  if (typeof value === 'string') {
    assertString(value, label);
    return;
  }
  if (typeof value === 'number') {
    assertNumber(value, label);
    return;
  }
  if (typeof value === 'boolean' || value === null || value === undefined) {
    return;
  }
  if (Array.isArray(value)) {
    assertArray(value, label);
    value.forEach((entry, index) => assertBoundedJsonTree(entry, `${label}[${index}]`, depth + 1));
    return;
  }
  if (isRecord(value)) {
    if (Object.keys(value).length > MAX_BACKUP_OBJECT_KEYS) {
      throw new Error(`${label} contains too many fields.`);
    }
    for (const [key, entry] of Object.entries(value)) {
      assertString(key, `${label} field name`, 128);
      assertBoundedJsonTree(entry, `${label}.${key}`, depth + 1);
    }
    return;
  }
  throw new Error(`${label} contains an unsupported value.`);
};

const requiredShapeFields = (
  value: unknown,
  label: string,
  fields: Readonly<Record<string, 'string' | 'number' | 'boolean' | 'array' | 'record'>>
): Record<string, unknown> => {
  const record = assertRecord(value, label);
  for (const [field, kind] of Object.entries(fields)) {
    const fieldValue = record[field];
    if (
      (kind === 'string' && typeof fieldValue !== 'string') ||
      (kind === 'number' && (typeof fieldValue !== 'number' || !Number.isFinite(fieldValue))) ||
      (kind === 'boolean' && typeof fieldValue !== 'boolean') ||
      (kind === 'array' && !Array.isArray(fieldValue)) ||
      (kind === 'record' && !isRecord(fieldValue))
    ) {
      throw new Error(`${label}.${field} is invalid.`);
    }
  }
  return record;
};

const validateRestoredAppState = (state: AppState): void => {
  const root = requiredShapeFields(state, 'Backup state', {
    activeScreen: 'string',
    contacts: 'array',
    events: 'array',
    memories: 'array',
    gifts: 'array',
    messages: 'array',
    activity: 'array',
    styleProfile: 'record',
    backups: 'array',
    settings: 'record',
    onboarding: 'record',
    privacy: 'record',
    aiProvider: 'record',
    emailDelivery: 'record',
    searchQuery: 'string',
    setupChecks: 'array',
    reminderPlans: 'array',
    calendarSync: 'record',
    persistence: 'record'
  });
  assertBoundedJsonTree(root, 'Backup state');

  const collectionSchemas: readonly {
    key: keyof Pick<AppState, 'contacts' | 'events' | 'memories' | 'gifts' | 'messages' | 'activity'>;
    fields: Readonly<Record<string, 'string' | 'number' | 'boolean' | 'array' | 'record'>>;
  }[] = [
    {
      key: 'contacts',
      fields: {
        id: 'string',
        name: 'string',
        relationship: 'string',
        group: 'string',
        preferredChannel: 'string',
        language: 'string',
        tone: 'array',
        healthScore: 'number',
        isVip: 'boolean',
        dnd: 'boolean',
        checkInCadenceDays: 'number',
        notesSummary: 'string',
        annualGiftBudget: 'number'
      }
    },
    {
      key: 'events',
      fields: {
        id: 'string',
        contactId: 'string',
        type: 'string',
        label: 'string',
        date: 'string',
        verified: 'boolean',
        source: 'string',
        checklist: 'array'
      }
    },
    {
      key: 'memories',
      fields: {
        id: 'string',
        contactId: 'string',
        category: 'string',
        body: 'string',
        pinned: 'boolean',
        createdAt: 'string'
      }
    },
    {
      key: 'gifts',
      fields: {
        id: 'string',
        contactId: 'string',
        name: 'string',
        category: 'string',
        occasion: 'string',
        cost: 'number',
        year: 'number',
        feedback: 'string',
        notes: 'string'
      }
    },
    {
      key: 'messages',
      fields: {
        id: 'string',
        contactId: 'string',
        reason: 'string',
        status: 'string',
        channel: 'string',
        body: 'string',
        variants: 'record',
        selectedVariant: 'string',
        quality: 'string',
        readiness: 'string'
      }
    },
    {
      key: 'activity',
      fields: {
        id: 'string',
        type: 'string',
        title: 'string',
        detail: 'string',
        severity: 'string',
        createdAt: 'string'
      }
    }
  ];

  for (const { key, fields } of collectionSchemas) {
    const collection = assertArray(root[key], `Backup state.${key}`, MAX_BACKUP_RECORDS_PER_COLLECTION);
    collection.forEach((record, index) => requiredShapeFields(record, `Backup state.${key}[${index}]`, fields));
  }

  assertArray(root.backups, 'Backup state.backups', MAX_BACKUP_RECORDS_PER_COLLECTION).forEach((record, index) =>
    requiredShapeFields(record, `Backup state.backups[${index}]`, {
      id: 'string',
      createdAt: 'string',
      recordCount: 'number',
      encrypted: 'boolean'
    })
  );
  assertArray(root.setupChecks, 'Backup state.setupChecks', MAX_BACKUP_RECORDS_PER_COLLECTION).forEach(
    (record, index) =>
      requiredShapeFields(record, `Backup state.setupChecks[${index}]`, {
        id: 'string',
        title: 'string',
        status: 'string',
        detail: 'string',
        action: 'string'
      })
  );
  assertArray(root.reminderPlans, 'Backup state.reminderPlans', MAX_BACKUP_RECORDS_PER_COLLECTION).forEach(
    (record, index) =>
      requiredShapeFields(record, `Backup state.reminderPlans[${index}]`, {
        id: 'string',
        eventId: 'string',
        contactId: 'string',
        title: 'string',
        body: 'string',
        triggerAt: 'string'
      })
  );

  const styleProfile = requiredShapeFields(root.styleProfile, 'Backup state.styleProfile', {
    confidence: 'string',
    formality: 'string',
    language: 'string',
    averageLength: 'number',
    emojiUse: 'string',
    sampleCount: 'number'
  });
  if (styleProfile.enabledForAiDrafts !== undefined && typeof styleProfile.enabledForAiDrafts !== 'boolean') {
    throw new Error('Backup state.styleProfile.enabledForAiDrafts is invalid.');
  }
  if (styleProfile.commonGreetings !== undefined) {
    assertArray(styleProfile.commonGreetings, 'Backup state.styleProfile.commonGreetings', 5).forEach(
      (greeting, index) => {
        const value = assertString(greeting, `Backup state.styleProfile.commonGreetings[${index}]`, 80);
        if (!value.trim()) throw new Error(`Backup state.styleProfile.commonGreetings[${index}] is invalid.`);
      }
    );
  }
  if (styleProfile.representativePreview !== undefined) {
    assertString(styleProfile.representativePreview, 'Backup state.styleProfile.representativePreview', 500);
  }
  const settings = requiredShapeFields(root.settings, 'Backup state.settings', {
    accountMode: 'string',
    locale: 'string',
    aiEnabled: 'boolean',
    notificationsEnabled: 'boolean',
    smsEnabled: 'boolean',
    whatsappHandoffEnabled: 'boolean',
    emailEnabled: 'boolean',
    biometricLockEnabled: 'boolean',
    automationMode: 'string',
    groupDefaults: 'record',
    quietHours: 'record',
    defaultSendTime: 'string',
    blackouts: 'array'
  });
  assertArray(settings.blackouts, 'Backup state.settings.blackouts', MAX_BACKUP_RECORDS_PER_COLLECTION).forEach(
    (record, index) =>
      requiredShapeFields(record, `Backup state.settings.blackouts[${index}]`, {
        id: 'string',
        label: 'string',
        startDate: 'string',
        endDate: 'string'
      })
  );
  requiredShapeFields(root.onboarding, 'Backup state.onboarding', {
    completed: 'boolean',
    currentStepId: 'string',
    selectedGoal: 'string',
    completedStepIds: 'array',
    skippedStepIds: 'array'
  });
  requiredShapeFields(root.privacy, 'Backup state.privacy', {
    permissionDecisions: 'record',
    whatsappHandoffConsent: 'boolean'
  });
  requiredShapeFields(root.aiProvider, 'Backup state.aiProvider', { status: 'string' });
  requiredShapeFields(root.emailDelivery, 'Backup state.emailDelivery', { status: 'string' });
  requiredShapeFields(root.calendarSync, 'Backup state.calendarSync', {
    exportedCount: 'number',
    importedCount: 'number'
  });
  requiredShapeFields(root.persistence, 'Backup state.persistence', { status: 'string' });
};

export const countBackupRecords = (state: AppState): BackupRecordCounts => ({
  contacts: state.contacts.length,
  events: state.events.length,
  memories: state.memories.length,
  gifts: state.gifts.length,
  messages: state.messages.length,
  activity: state.activity.length
});

export const totalBackupRecords = (counts: BackupRecordCounts) =>
  counts.contacts + counts.events + counts.memories + counts.gifts + counts.messages + counts.activity;

export const validateBackupPassphrase = (passphrase: string): string[] => {
  const problems: string[] = [];
  if (passphrase.trim().length < MIN_BACKUP_PASSPHRASE_LENGTH) {
    problems.push(`Use at least ${MIN_BACKUP_PASSPHRASE_LENGTH} characters.`);
  }
  if (passphrase.length > MAX_BACKUP_PASSPHRASE_LENGTH) {
    problems.push(`Use no more than ${MAX_BACKUP_PASSPHRASE_LENGTH} characters.`);
  }
  if (!/[A-Za-z]/.test(passphrase) || !/[0-9]/.test(passphrase)) {
    problems.push('Use both letters and numbers.');
  }
  return problems;
};

const assertRestorePassphraseBound = (passphrase: string) => {
  if (passphrase.length === 0 || passphrase.length > MAX_BACKUP_PASSPHRASE_LENGTH) {
    throw new Error('Backup passphrase length is invalid.');
  }
};

const bytesToBase64 = (bytes: Uint8Array) => {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  if (typeof btoa === 'function') {
    return btoa(binary);
  }
  return Buffer.from(bytes).toString('base64');
};

const decodedBase64Length = (value: string): number => {
  if (value.length === 0 || value.length % 4 !== 0 || !/^[A-Za-z0-9+/]+={0,2}$/.test(value)) {
    throw new Error('Backup file contains invalid base64 data.');
  }
  const padding = value.endsWith('==') ? 2 : value.endsWith('=') ? 1 : 0;
  return (value.length / 4) * 3 - padding;
};

const assertBase64 = (value: unknown, label: string, maximumBytes: number, exactBytes?: number): string => {
  const encoded = assertString(value, label, Math.ceil(maximumBytes / 3) * 4 + 4);
  const decodedLength = decodedBase64Length(encoded);
  if (decodedLength > maximumBytes || (exactBytes !== undefined && decodedLength !== exactBytes)) {
    throw new Error(`${label} has an invalid length.`);
  }
  return encoded;
};

const base64ToBytes = (value: string) => {
  let binary: string;
  try {
    binary = typeof atob === 'function' ? atob(value) : Buffer.from(value, 'base64').toString('binary');
  } catch {
    throw new Error('Backup file contains invalid base64 data.');
  }
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
};

const digestBase64 = async (bytes: Uint8Array, cryptoProvider: CrossPlatformCryptoProvider) =>
  bytesToBase64(await cryptoProvider.sha256(bytes));

const assertKdfIterations = (value: unknown): number =>
  assertNumber(value, 'Backup KDF iterations', {
    integer: true,
    minimum: MIN_BACKUP_KDF_ITERATIONS,
    maximum: MAX_BACKUP_KDF_ITERATIONS
  });

const deriveBackupKey = async (passphrase: string, salt: Uint8Array, iterations: number): Promise<Uint8Array> => {
  const passwordBytes = textEncoder.encode(passphrase);
  try {
    return await pbkdf2Async(sha256, passwordBytes, salt, {
      c: iterations,
      dkLen: 32,
      asyncTick: 8
    });
  } finally {
    passwordBytes.fill(0);
  }
};

const importBackupKey = async (cryptoProvider: CrossPlatformCryptoProvider, rawKey: Uint8Array) => {
  try {
    return await cryptoProvider.importAesGcmKey(rawKey);
  } finally {
    rawKey.fill(0);
  }
};

export const assertBackupRawInput = (raw: string): void => {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > MAX_BACKUP_RAW_BYTES) {
    throw new Error(`Backup file must be no larger than ${MAX_BACKUP_RAW_BYTES} bytes.`);
  }
  if (textEncoder.encode(raw).byteLength > MAX_BACKUP_RAW_BYTES) {
    throw new Error(`Backup file must be no larger than ${MAX_BACKUP_RAW_BYTES} bytes.`);
  }
};

const parseRecordCounts = (value: unknown): BackupRecordCounts => {
  const record = assertRecord(value, 'Backup record counts');
  const keys = ['contacts', 'events', 'memories', 'gifts', 'messages', 'activity'] as const;
  assertExactKeys(record, keys, [], 'Backup record counts');
  const counts = Object.fromEntries(
    keys.map(key => [
      key,
      assertNumber(record[key], `Backup ${key} count`, {
        integer: true,
        minimum: 0,
        maximum: MAX_BACKUP_RECORDS_PER_COLLECTION
      })
    ])
  ) as BackupRecordCounts;
  if (totalBackupRecords(counts) > MAX_BACKUP_TOTAL_RECORDS) {
    throw new Error('Backup contains too many records.');
  }
  return counts;
};

const parseEnvelope = (raw: string): EncryptedBackupEnvelope => {
  assertBackupRawInput(raw);
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error('Backup file is not valid JSON.');
  }
  const record = assertRecord(parsed, 'Backup file');
  assertExactKeys(
    record,
    [
      'format',
      'version',
      'app',
      'createdAt',
      'encrypted',
      'persistenceVersion',
      'recordCounts',
      'kdf',
      'cipher',
      'checksum'
    ],
    [],
    'Backup file'
  );
  if (record.format !== BACKUP_FORMAT || record.encrypted !== true || record.app !== 'RelateAI') {
    throw new Error('Backup file is not a RelateAI encrypted backup.');
  }
  const version = assertNumber(record.version, 'Backup version', {
    integer: true,
    minimum: 1,
    maximum: BACKUP_VERSION
  });
  const persistenceVersion = assertNumber(record.persistenceVersion, 'Backup data version', {
    integer: true,
    minimum: 1,
    maximum: Number.MAX_SAFE_INTEGER
  });
  const createdAt = assertIsoTimestamp(record.createdAt, 'Backup creation time');
  const recordCounts = parseRecordCounts(record.recordCounts);

  const kdf = assertRecord(record.kdf, 'Backup KDF metadata');
  assertExactKeys(kdf, ['algorithm', 'iterations', 'salt'], [], 'Backup KDF metadata');
  if (kdf.algorithm !== 'PBKDF2-SHA-256') {
    throw new Error('Backup KDF algorithm is not supported.');
  }
  const iterations = assertKdfIterations(kdf.iterations);
  const salt = assertBase64(kdf.salt, 'Backup KDF salt', BACKUP_SALT_BYTES, BACKUP_SALT_BYTES);

  const cipher = assertRecord(record.cipher, 'Backup cipher metadata');
  assertExactKeys(cipher, ['algorithm', 'iv', 'ciphertext'], [], 'Backup cipher metadata');
  if (cipher.algorithm !== 'AES-GCM') {
    throw new Error('Backup cipher algorithm is not supported.');
  }
  const iv = assertBase64(cipher.iv, 'Backup cipher IV', BACKUP_IV_BYTES, BACKUP_IV_BYTES);
  const ciphertext = assertBase64(cipher.ciphertext, 'Backup ciphertext', MAX_BACKUP_CIPHERTEXT_BYTES);

  const checksum = assertRecord(record.checksum, 'Backup checksum metadata');
  const ciphertextChecksum = assertBase64(
    checksum.ciphertext,
    'Backup ciphertext checksum',
    SHA_256_BYTES,
    SHA_256_BYTES
  );
  if (checksum.algorithm !== 'SHA-256') {
    throw new Error('Backup checksum algorithm is not supported.');
  }

  const base = {
    format: BACKUP_FORMAT as typeof BACKUP_FORMAT,
    app: 'RelateAI' as const,
    createdAt,
    encrypted: true as const,
    persistenceVersion,
    recordCounts,
    kdf: { algorithm: 'PBKDF2-SHA-256' as const, iterations, salt },
    cipher: { algorithm: 'AES-GCM' as const, iv, ciphertext }
  };
  if (version === 1) {
    assertExactKeys(checksum, ['algorithm', 'ciphertext', 'plaintext'], [], 'Backup checksum metadata');
    const plaintext = assertBase64(checksum.plaintext, 'Backup plaintext checksum', SHA_256_BYTES, SHA_256_BYTES);
    return {
      ...base,
      version: 1,
      checksum: { algorithm: 'SHA-256', ciphertext: ciphertextChecksum, plaintext }
    };
  }
  assertExactKeys(checksum, ['algorithm', 'ciphertext'], [], 'Backup checksum metadata');
  return {
    ...base,
    version: 2,
    checksum: { algorithm: 'SHA-256', ciphertext: ciphertextChecksum }
  };
};

const authenticatedMetadata = (envelope: EncryptedBackupEnvelopeV2): Uint8Array =>
  textEncoder.encode(
    JSON.stringify({
      format: envelope.format,
      version: envelope.version,
      app: envelope.app,
      createdAt: envelope.createdAt,
      encrypted: envelope.encrypted,
      persistenceVersion: envelope.persistenceVersion,
      recordCounts: envelope.recordCounts,
      kdf: envelope.kdf,
      cipher: { algorithm: envelope.cipher.algorithm, iv: envelope.cipher.iv }
    })
  );

export const previewEncryptedBackup = (raw: string): BackupPreview => {
  const envelope = parseEnvelope(raw);
  const warnings: string[] = [];
  if (envelope.persistenceVersion > PERSISTENCE_VERSION) {
    warnings.push('This backup uses a newer data version and cannot be restored here.');
  }
  return {
    format: envelope.format,
    version: envelope.version,
    app: envelope.app,
    createdAt: envelope.createdAt,
    encrypted: envelope.encrypted,
    persistenceVersion: envelope.persistenceVersion,
    recordCounts: envelope.recordCounts,
    recordCount: totalBackupRecords(envelope.recordCounts),
    warnings
  };
};

export const createEncryptedBackup = async (
  state: AppState,
  passphrase: string,
  options: BackupCryptoOptions = {}
): Promise<string> => {
  const problems = validateBackupPassphrase(passphrase);
  if (problems.length > 0) {
    throw new Error(`Backup passphrase is too weak. ${problems.join(' ')}`);
  }
  validateRestoredAppState(state);
  const recordCounts = countBackupRecords(state);
  parseRecordCounts(recordCounts);
  const iterations = assertKdfIterations(options.iterations ?? DEFAULT_BACKUP_KDF_ITERATIONS);
  const createdAt = assertIsoTimestamp(options.createdAt ?? new Date().toISOString(), 'Backup creation time');
  const version = options.formatVersion ?? BACKUP_VERSION;
  if (version !== 1 && version !== 2) {
    throw new Error('Backup format version is not supported.');
  }

  const cryptoProvider = options.cryptoProvider ?? resolveCrossPlatformCryptoProvider();
  const salt = await cryptoProvider.randomBytes(BACKUP_SALT_BYTES);
  const iv = await cryptoProvider.randomBytes(BACKUP_IV_BYTES);
  const plaintext = textEncoder.encode(serializeState(state));
  if (plaintext.byteLength > MAX_BACKUP_PLAINTEXT_BYTES) {
    throw new Error(`Backup payload must be no larger than ${MAX_BACKUP_PLAINTEXT_BYTES} bytes.`);
  }
  const rawKey = await deriveBackupKey(passphrase, salt, iterations);
  const key = await importBackupKey(cryptoProvider, rawKey);
  const saltBase64 = bytesToBase64(salt);
  const ivBase64 = bytesToBase64(iv);
  const common = {
    format: BACKUP_FORMAT as typeof BACKUP_FORMAT,
    app: 'RelateAI' as const,
    createdAt,
    encrypted: true as const,
    persistenceVersion: PERSISTENCE_VERSION,
    recordCounts,
    kdf: { algorithm: 'PBKDF2-SHA-256' as const, iterations, salt: saltBase64 },
    cipher: { algorithm: 'AES-GCM' as const, iv: ivBase64, ciphertext: '' }
  };
  const metadataEnvelope: EncryptedBackupEnvelopeV2 = {
    ...common,
    version: 2,
    checksum: { algorithm: 'SHA-256', ciphertext: '' }
  };
  const ciphertext = await key.encrypt(
    plaintext,
    iv,
    version === 2 ? authenticatedMetadata(metadataEnvelope) : undefined
  );
  if (ciphertext.byteLength > MAX_BACKUP_CIPHERTEXT_BYTES) {
    throw new Error('Encrypted backup exceeds the supported size.');
  }
  const ciphertextBase64 = bytesToBase64(ciphertext);
  const ciphertextChecksum = await digestBase64(ciphertext, cryptoProvider);

  const envelope: EncryptedBackupEnvelope =
    version === 1
      ? {
          ...common,
          version: 1,
          cipher: { ...common.cipher, ciphertext: ciphertextBase64 },
          checksum: {
            algorithm: 'SHA-256',
            ciphertext: ciphertextChecksum,
            plaintext: await digestBase64(plaintext, cryptoProvider)
          }
        }
      : {
          ...common,
          version: 2,
          cipher: { ...common.cipher, ciphertext: ciphertextBase64 },
          checksum: { algorithm: 'SHA-256', ciphertext: ciphertextChecksum }
        };
  const raw = JSON.stringify(envelope);
  assertBackupRawInput(raw);
  return raw;
};

const parseBoundedPersistedState = (plaintext: Uint8Array): AppState => {
  if (plaintext.byteLength > MAX_BACKUP_PLAINTEXT_BYTES) {
    throw new Error('Backup payload exceeds the supported size.');
  }
  const decoded = textDecoder.decode(plaintext);
  let persisted: unknown;
  try {
    persisted = JSON.parse(decoded);
  } catch {
    throw new Error('Backup payload is not valid JSON.');
  }
  const persistedEnvelope = assertRecord(persisted, 'Backup payload');
  assertExactKeys(persistedEnvelope, ['version', 'savedAt', 'state'], ['migratedFrom'], 'Backup payload');
  assertNumber(persistedEnvelope.version, 'Backup payload version', {
    integer: true,
    minimum: 1,
    maximum: PERSISTENCE_VERSION
  });
  assertIsoTimestamp(persistedEnvelope.savedAt, 'Backup payload save time');
  assertRecord(persistedEnvelope.state, 'Backup payload state');
  assertBoundedJsonTree(persistedEnvelope, 'Backup payload');

  const state = deserializeState(decoded);
  validateRestoredAppState(state);
  return state;
};

export const decryptEncryptedBackup = async (
  raw: string,
  passphrase: string,
  options: BackupDecryptOptions = {}
): Promise<AppState> => {
  assertRestorePassphraseBound(passphrase);
  const envelope = parseEnvelope(raw);
  if (envelope.persistenceVersion > PERSISTENCE_VERSION) {
    throw new Error('Backup data version is not supported.');
  }

  const cryptoProvider = options.cryptoProvider ?? resolveCrossPlatformCryptoProvider();
  const ciphertext = base64ToBytes(envelope.cipher.ciphertext);
  const ciphertextDigest = await digestBase64(ciphertext, cryptoProvider);
  if (ciphertextDigest !== envelope.checksum.ciphertext) {
    throw new Error('Backup integrity check failed.');
  }

  const rawKey = await deriveBackupKey(passphrase, base64ToBytes(envelope.kdf.salt), envelope.kdf.iterations);
  const key = await importBackupKey(cryptoProvider, rawKey);
  let plaintext: Uint8Array;
  try {
    plaintext = await key.decrypt(
      ciphertext,
      base64ToBytes(envelope.cipher.iv),
      envelope.version === 2 ? authenticatedMetadata(envelope) : undefined
    );
  } catch {
    throw new Error('Backup passphrase is incorrect or the file is damaged.');
  }

  if (envelope.version === 1) {
    const plaintextDigest = await digestBase64(plaintext, cryptoProvider);
    if (plaintextDigest !== envelope.checksum.plaintext) {
      throw new Error('Backup payload integrity check failed.');
    }
  }

  const state = parseBoundedPersistedState(plaintext);
  const actualCounts = countBackupRecords(state);
  if (
    (Object.keys(actualCounts) as (keyof BackupRecordCounts)[]).some(
      keyName => actualCounts[keyName] !== envelope.recordCounts[keyName]
    )
  ) {
    throw new Error('Backup record counts do not match the encrypted payload.');
  }
  return state;
};
