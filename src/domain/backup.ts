import type { AppState } from './types';
import { deserializeState, PERSISTENCE_VERSION, serializeState } from '../state/persistence';

export const BACKUP_FORMAT = 'relateai.encrypted-backup';
export const BACKUP_VERSION = 1;
export const BACKUP_FILE_EXTENSION = 'relateai-backup';
export const DEFAULT_BACKUP_KDF_ITERATIONS = 120000;
export const MIN_BACKUP_PASSPHRASE_LENGTH = 12;

export type BackupRecordCounts = {
  contacts: number;
  events: number;
  memories: number;
  gifts: number;
  messages: number;
  activity: number;
};

export type EncryptedBackupEnvelope = {
  format: typeof BACKUP_FORMAT;
  version: number;
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
  checksum: {
    algorithm: 'SHA-256';
    ciphertext: string;
    plaintext: string;
  };
};

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
};

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

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
  if (!/[A-Za-z]/.test(passphrase) || !/[0-9]/.test(passphrase)) {
    problems.push('Use both letters and numbers.');
  }
  return problems;
};

const requireBackupCrypto = () => {
  if (!globalThis.crypto?.subtle || typeof globalThis.crypto.getRandomValues !== 'function') {
    throw new Error('Encrypted backup is not available on this platform.');
  }
  return globalThis.crypto;
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

const base64ToBytes = (value: string) => {
  const binary = typeof atob === 'function' ? atob(value) : Buffer.from(value, 'base64').toString('binary');
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
};

const bytesToArrayBuffer = (bytes: Uint8Array): ArrayBuffer =>
  bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;

const digestBase64 = async (bytes: Uint8Array) => {
  const crypto = requireBackupCrypto();
  const digest = await crypto.subtle.digest('SHA-256', bytesToArrayBuffer(bytes));
  return bytesToBase64(new Uint8Array(digest));
};

const deriveBackupKey = async (passphrase: string, salt: Uint8Array, iterations: number) => {
  const crypto = requireBackupCrypto();
  const baseKey = await crypto.subtle.importKey('raw', bytesToArrayBuffer(textEncoder.encode(passphrase)), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: bytesToArrayBuffer(salt),
      iterations,
      hash: 'SHA-256'
    },
    baseKey,
    {
      name: 'AES-GCM',
      length: 256
    },
    false,
    ['encrypt', 'decrypt']
  );
};

const parseEnvelope = (raw: string): EncryptedBackupEnvelope => {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error('Backup file is not valid JSON.');
  }
  if (!parsed || typeof parsed !== 'object') {
    throw new Error('Backup file is invalid.');
  }
  const envelope = parsed as EncryptedBackupEnvelope;
  if (envelope.format !== BACKUP_FORMAT || envelope.encrypted !== true) {
    throw new Error('Backup file is not a RelateAI encrypted backup.');
  }
  if (!envelope.cipher?.ciphertext || !envelope.cipher.iv || !envelope.kdf?.salt) {
    throw new Error('Backup file is missing encryption metadata.');
  }
  return envelope;
};

export const previewEncryptedBackup = (raw: string): BackupPreview => {
  const envelope = parseEnvelope(raw);
  const warnings: string[] = [];
  if (envelope.version > BACKUP_VERSION) {
    warnings.push('This backup was created by a newer app version and cannot be restored here.');
  }
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

  const crypto = requireBackupCrypto();
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const iterations = options.iterations ?? DEFAULT_BACKUP_KDF_ITERATIONS;
  const plaintext = textEncoder.encode(serializeState(state));
  const key = await deriveBackupKey(passphrase, salt, iterations);
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv: bytesToArrayBuffer(iv)
      },
      key,
      bytesToArrayBuffer(plaintext)
    )
  );

  const envelope: EncryptedBackupEnvelope = {
    format: BACKUP_FORMAT,
    version: BACKUP_VERSION,
    app: 'RelateAI',
    createdAt: options.createdAt ?? new Date().toISOString(),
    encrypted: true,
    persistenceVersion: PERSISTENCE_VERSION,
    recordCounts: countBackupRecords(state),
    kdf: {
      algorithm: 'PBKDF2-SHA-256',
      iterations,
      salt: bytesToBase64(salt)
    },
    cipher: {
      algorithm: 'AES-GCM',
      iv: bytesToBase64(iv),
      ciphertext: bytesToBase64(ciphertext)
    },
    checksum: {
      algorithm: 'SHA-256',
      ciphertext: await digestBase64(ciphertext),
      plaintext: await digestBase64(plaintext)
    }
  };

  return JSON.stringify(envelope);
};

export const decryptEncryptedBackup = async (raw: string, passphrase: string): Promise<AppState> => {
  const envelope = parseEnvelope(raw);
  if (envelope.version > BACKUP_VERSION) {
    throw new Error('Backup was created by a newer app version.');
  }
  if (envelope.persistenceVersion > PERSISTENCE_VERSION) {
    throw new Error('Backup data version is not supported.');
  }

  const ciphertext = base64ToBytes(envelope.cipher.ciphertext);
  const ciphertextDigest = await digestBase64(ciphertext);
  if (ciphertextDigest !== envelope.checksum.ciphertext) {
    throw new Error('Backup integrity check failed.');
  }

  const key = await deriveBackupKey(
    passphrase,
    base64ToBytes(envelope.kdf.salt),
    envelope.kdf.iterations
  );
  let plaintext: Uint8Array;
  try {
    plaintext = new Uint8Array(
      await requireBackupCrypto().subtle.decrypt(
        {
          name: 'AES-GCM',
          iv: bytesToArrayBuffer(base64ToBytes(envelope.cipher.iv))
        },
        key,
        bytesToArrayBuffer(ciphertext)
      )
    );
  } catch {
    throw new Error('Backup passphrase is incorrect or the file is damaged.');
  }

  const plaintextDigest = await digestBase64(plaintext);
  if (plaintextDigest !== envelope.checksum.plaintext) {
    throw new Error('Backup payload integrity check failed.');
  }

  return deserializeState(textDecoder.decode(plaintext));
};
