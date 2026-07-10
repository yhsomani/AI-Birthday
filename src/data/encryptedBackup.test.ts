import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  createExpoCryptoProvider,
  createWebCryptoProvider,
  resolveCrossPlatformCryptoProvider,
  type ExpoCryptoBridge
} from '../crypto/crossPlatformCrypto';
import { createTestState } from '../test/testState';
import {
  BACKUP_VERSION,
  MAX_BACKUP_FIELD_LENGTH,
  MAX_BACKUP_KDF_ITERATIONS,
  MAX_BACKUP_PASSPHRASE_LENGTH,
  MAX_BACKUP_RAW_BYTES,
  createEncryptedBackup,
  decryptEncryptedBackup,
  previewEncryptedBackup,
  validateBackupPassphrase
} from './encryptedBackup';

const strongPassphrase = 'CorrectHorse123';

type MutableBackupEnvelope = {
  format: string;
  version: number;
  app: string;
  createdAt: string;
  encrypted: boolean;
  persistenceVersion: number;
  recordCounts: Record<string, number>;
  kdf: { algorithm: string; iterations: number; salt: string };
  cipher: { algorithm: string; iv: string; ciphertext: string };
  checksum: { algorithm: string; ciphertext: string; plaintext?: string };
};

const asArrayBuffer = (bytes: Uint8Array): ArrayBuffer =>
  bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;

const fromBase64 = (value: string): Uint8Array => Uint8Array.from(Buffer.from(value, 'base64'));

const decryptWithLegacyWebCrypto = async (raw: string, passphrase: string): Promise<string> => {
  const envelope = JSON.parse(raw) as MutableBackupEnvelope;
  const baseKey = await globalThis.crypto.subtle.importKey(
    'raw',
    asArrayBuffer(new TextEncoder().encode(passphrase)),
    'PBKDF2',
    false,
    ['deriveKey']
  );
  const key = await globalThis.crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: asArrayBuffer(fromBase64(envelope.kdf.salt)),
      iterations: envelope.kdf.iterations,
      hash: 'SHA-256'
    },
    baseKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt']
  );
  const metadata = new TextEncoder().encode(
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
  const plaintext = await globalThis.crypto.subtle.decrypt(
    {
      name: 'AES-GCM',
      iv: asArrayBuffer(fromBase64(envelope.cipher.iv)),
      additionalData: asArrayBuffer(metadata)
    },
    key,
    asArrayBuffer(fromBase64(envelope.cipher.ciphertext))
  );
  return new TextDecoder().decode(plaintext);
};

describe('encrypted backup contract', () => {
  it('validates backup passphrases before export', () => {
    assert.ok(validateBackupPassphrase('short1').length > 0);
    assert.ok(validateBackupPassphrase('longbutnonumeric').length > 0);
    assert.deepEqual(validateBackupPassphrase(strongPassphrase), []);
  });

  it('exports encrypted state, previews metadata, and restores with the passphrase', async () => {
    const state = createTestState();
    const raw = await createEncryptedBackup(state, strongPassphrase, {
      iterations: 1000,
      createdAt: '2026-07-09T00:00:00.000Z'
    });
    const preview = previewEncryptedBackup(raw);
    const restored = await decryptEncryptedBackup(raw, strongPassphrase);

    assert.equal(preview.app, 'RelateAI');
    assert.equal(preview.version, BACKUP_VERSION);
    assert.equal(preview.encrypted, true);
    assert.equal(preview.recordCounts.contacts, state.contacts.length);
    assert.equal(restored.contacts[0].id, state.contacts[0].id);
    assert.equal(restored.messages.length, state.messages.length);
    assert.doesNotMatch(raw, /Asha Mehra/);
    assert.doesNotMatch(raw, /mango lassi/);
    assert.doesNotMatch(raw, /"plaintext"/);
  });

  it('rejects wrong passphrases and tampered files', async () => {
    const state = createTestState();
    const raw = await createEncryptedBackup(state, strongPassphrase, { iterations: 1000 });
    const tampered = JSON.parse(raw) as {
      cipher: {
        ciphertext: string;
      };
    };
    tampered.cipher.ciphertext = `${tampered.cipher.ciphertext[0] === 'A' ? 'B' : 'A'}${tampered.cipher.ciphertext.slice(1)}`;

    await assert.rejects(() => decryptEncryptedBackup(raw, 'WrongPassphrase123'), /incorrect|damaged/i);
    await assert.rejects(() => decryptEncryptedBackup(JSON.stringify(tampered), strongPassphrase), /integrity/i);
  });

  it('keeps version 1 encrypted backups readable while exporting version 2 by default', async () => {
    const state = createTestState();
    const legacyRaw = await createEncryptedBackup(state, strongPassphrase, {
      iterations: 1000,
      formatVersion: 1,
      createdAt: '2026-07-09T00:00:00.000Z'
    });
    const legacyEnvelope = JSON.parse(legacyRaw) as { version: number; checksum: { plaintext?: string } };

    assert.equal(legacyEnvelope.version, 1);
    assert.equal(typeof legacyEnvelope.checksum.plaintext, 'string');
    const restored = await decryptEncryptedBackup(legacyRaw, strongPassphrase);
    assert.equal(restored.contacts.length, state.contacts.length);
  });

  it('keeps Noble PBKDF2 backups interoperable across injected Expo and Web Crypto paths', async () => {
    const web = createWebCryptoProvider(globalThis.crypto);
    const calls = { random: 0, digest: 0, importKey: 0 };
    const bridge: ExpoCryptoBridge = {
      async randomBytes(byteCount) {
        calls.random += 1;
        return web.randomBytes(byteCount);
      },
      async sha256(data) {
        calls.digest += 1;
        return web.sha256(data);
      },
      async importAesGcmKey(rawKey) {
        calls.importKey += 1;
        return web.importAesGcmKey(rawKey);
      }
    };
    const native = resolveCrossPlatformCryptoProvider({
      runtime: 'native',
      expoProvider: createExpoCryptoProvider(async () => bridge)
    });
    const state = createTestState();

    const nativeRaw = await createEncryptedBackup(state, strongPassphrase, {
      iterations: 1000,
      createdAt: '2026-07-09T00:00:00.000Z',
      cryptoProvider: native
    });
    const webRestored = await decryptEncryptedBackup(nativeRaw, strongPassphrase, { cryptoProvider: web });
    const webRaw = await createEncryptedBackup(state, strongPassphrase, {
      iterations: 1000,
      createdAt: '2026-07-09T00:00:00.000Z',
      cryptoProvider: web
    });
    const nativeRestored = await decryptEncryptedBackup(webRaw, strongPassphrase, { cryptoProvider: native });
    const legacyPlaintext = await decryptWithLegacyWebCrypto(nativeRaw, strongPassphrase);

    assert.equal(native.kind, 'expo-crypto');
    assert.equal(webRestored.contacts[0].id, state.contacts[0].id);
    assert.equal(nativeRestored.messages[0].id, state.messages[0].id);
    assert.match(legacyPlaintext, new RegExp(state.contacts[0].id));
    assert.ok(calls.random >= 2);
    assert.ok(calls.digest >= 2);
    assert.ok(calls.importKey >= 2);
  });

  it('rejects hostile iteration, record-count, schema, and base64 metadata before restore', async () => {
    const raw = await createEncryptedBackup(createTestState(), strongPassphrase, { iterations: 1000 });
    const base = JSON.parse(raw) as MutableBackupEnvelope;

    const excessiveIterations = structuredClone(base);
    excessiveIterations.kdf.iterations = MAX_BACKUP_KDF_ITERATIONS + 1;
    await assert.rejects(
      () => decryptEncryptedBackup(JSON.stringify(excessiveIterations), strongPassphrase),
      /iterations.*range/i
    );

    const excessiveRecords = structuredClone(base);
    excessiveRecords.recordCounts.contacts = 10_001;
    assert.throws(() => previewEncryptedBackup(JSON.stringify(excessiveRecords)), /count.*range/i);

    const unknownField = { ...structuredClone(base), unexpectedPrivateField: 'not allowed' };
    assert.throws(() => previewEncryptedBackup(JSON.stringify(unknownField)), /schema/i);

    const malformedBase64 = structuredClone(base);
    malformedBase64.cipher.iv = '***not-base64***';
    assert.throws(() => previewEncryptedBackup(JSON.stringify(malformedBase64)), /base64/i);

    const invalidSaltLength = structuredClone(base);
    invalidSaltLength.kdf.salt = 'AAAA';
    assert.throws(() => previewEncryptedBackup(JSON.stringify(invalidSaltLength)), /salt.*length/i);

    const invalidChecksumShape = structuredClone(base);
    invalidChecksumShape.checksum.plaintext = invalidChecksumShape.checksum.ciphertext;
    assert.throws(() => previewEncryptedBackup(JSON.stringify(invalidChecksumShape)), /checksum.*schema/i);

    const invalidAlgorithm = structuredClone(base);
    invalidAlgorithm.cipher.algorithm = 'AES-CBC';
    assert.throws(() => previewEncryptedBackup(JSON.stringify(invalidAlgorithm)), /algorithm.*supported/i);

    const invalidTimestamp = structuredClone(base);
    invalidTimestamp.createdAt = 'not-a-date';
    assert.throws(() => previewEncryptedBackup(JSON.stringify(invalidTimestamp)), /creation time.*invalid/i);
  });

  it('authenticates v2 record metadata and verifies v1 record counts after decryption', async () => {
    const state = createTestState();
    const current = JSON.parse(
      await createEncryptedBackup(state, strongPassphrase, { iterations: 1000 })
    ) as MutableBackupEnvelope;
    current.recordCounts.contacts += 1;
    await assert.rejects(() => decryptEncryptedBackup(JSON.stringify(current), strongPassphrase), /incorrect|damaged/i);

    const legacy = JSON.parse(
      await createEncryptedBackup(state, strongPassphrase, { iterations: 1000, formatVersion: 1 })
    ) as MutableBackupEnvelope;
    legacy.recordCounts.contacts += 1;
    await assert.rejects(
      () => decryptEncryptedBackup(JSON.stringify(legacy), strongPassphrase),
      /record counts do not match/i
    );
  });

  it('bounds raw input, passphrases, plaintext fields, and export KDF settings', async () => {
    assert.throws(() => previewEncryptedBackup('x'.repeat(MAX_BACKUP_RAW_BYTES + 1)), /no larger/i);

    const raw = await createEncryptedBackup(createTestState(), strongPassphrase, { iterations: 1000 });
    await assert.rejects(
      () => decryptEncryptedBackup(raw, `A1${'x'.repeat(MAX_BACKUP_PASSPHRASE_LENGTH)}`),
      /passphrase length/i
    );

    const invalidState = createTestState();
    invalidState.contacts[0].notesSummary = 'x'.repeat(MAX_BACKUP_FIELD_LENGTH + 1);
    await assert.rejects(
      () => createEncryptedBackup(invalidState, strongPassphrase, { iterations: 1000 }),
      /too long/i
    );
    await assert.rejects(
      () => createEncryptedBackup(createTestState(), strongPassphrase, { iterations: 999 }),
      /iterations.*range/i
    );
  });
});
