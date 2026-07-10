import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  createExpoCryptoProvider,
  createWebCryptoProvider,
  resolveCrossPlatformCryptoProvider,
  type ExpoCryptoBridge
} from './crossPlatformCrypto';

const createInjectedExpoProvider = () => {
  const web = createWebCryptoProvider(globalThis.crypto);
  const calls = { load: 0, random: 0, digest: 0, importKey: 0 };
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
  const expo = createExpoCryptoProvider(async () => {
    calls.load += 1;
    return bridge;
  });
  return { expo, web, calls };
};

describe('cross-platform crypto provider', () => {
  it('selects the injected Expo provider for native runtime even when Web Crypto exists', async () => {
    const { expo, calls } = createInjectedExpoProvider();
    const resolved = resolveCrossPlatformCryptoProvider({ runtime: 'native', expoProvider: expo });

    assert.equal(resolveCrossPlatformCryptoProvider().kind, 'web-crypto');
    assert.equal(resolved.kind, 'expo-crypto');
    assert.equal((await resolved.randomBytes(16)).byteLength, 16);
    assert.equal((await resolved.sha256(new TextEncoder().encode('native-path'))).byteLength, 32);
    assert.equal(calls.load, 1);
    assert.equal(calls.random, 1);
    assert.equal(calls.digest, 1);
  });

  it('keeps Expo-path AES-GCM ciphertext interoperable with Web Crypto', async () => {
    const { expo, web, calls } = createInjectedExpoProvider();
    const rawKey = Uint8Array.from({ length: 32 }, (_, index) => index + 1);
    const iv = Uint8Array.from({ length: 12 }, (_, index) => index + 20);
    const plaintext = new TextEncoder().encode('same AES-GCM envelope');
    const aad = new TextEncoder().encode('authenticated metadata');
    const expoKey = await expo.importAesGcmKey(rawKey);
    const webKey = await web.importAesGcmKey(rawKey);

    const ciphertext = await expoKey.encrypt(plaintext, iv, aad);
    const restored = await webKey.decrypt(ciphertext, iv, aad);
    const webCiphertext = await webKey.encrypt(plaintext, iv, aad);
    const expoRestored = await expoKey.decrypt(webCiphertext, iv, aad);

    assert.deepEqual(restored, plaintext);
    assert.deepEqual(expoRestored, plaintext);
    assert.deepEqual(ciphertext, webCiphertext);
    assert.equal(ciphertext.byteLength, plaintext.byteLength + 16);
    assert.equal(calls.importKey, 1);
  });

  it('rejects malformed native digest and random results', async () => {
    const invalid = createExpoCryptoProvider(async () => ({
      async randomBytes() {
        return new Uint8Array(1);
      },
      async sha256() {
        return new Uint8Array(31);
      },
      async importAesGcmKey() {
        throw new Error('not reached');
      }
    }));

    await assert.rejects(() => invalid.randomBytes(16), /invalid random byte count/i);
    await assert.rejects(() => invalid.sha256(new Uint8Array([1])), /invalid SHA-256 digest/i);
  });
});
