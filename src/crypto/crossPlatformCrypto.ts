export const AES_GCM_KEY_BYTES = 32;
export const AES_GCM_IV_BYTES = 12;
export const AES_GCM_TAG_BYTES = 16;

export type CryptoProviderKind = 'web-crypto' | 'expo-crypto';

export interface AesGcmKey {
  encrypt(plaintext: Uint8Array, iv: Uint8Array, additionalData?: Uint8Array): Promise<Uint8Array>;
  decrypt(ciphertextWithTag: Uint8Array, iv: Uint8Array, additionalData?: Uint8Array): Promise<Uint8Array>;
}

export interface CrossPlatformCryptoProvider {
  readonly kind: CryptoProviderKind;
  randomBytes(byteCount: number): Promise<Uint8Array>;
  sha256(data: Uint8Array): Promise<Uint8Array>;
  importAesGcmKey(rawKey: Uint8Array): Promise<AesGcmKey>;
}

export class CrossPlatformCryptoError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = 'CrossPlatformCryptoError';
  }
}

const asArrayBuffer = (bytes: Uint8Array): ArrayBuffer =>
  bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;

const copyBytes = (value: ArrayBuffer | Uint8Array): Uint8Array =>
  value instanceof Uint8Array ? Uint8Array.from(value) : new Uint8Array(value.slice(0));

const assertByteCount = (byteCount: number): void => {
  if (!Number.isInteger(byteCount) || byteCount < 1 || byteCount > 1024) {
    throw new CrossPlatformCryptoError('Secure random byte count is outside the supported range.');
  }
};

const assertAesKey = (rawKey: Uint8Array): void => {
  if (rawKey.byteLength !== AES_GCM_KEY_BYTES) {
    throw new CrossPlatformCryptoError('AES-GCM requires a 256-bit key.');
  }
};

const assertAesIv = (iv: Uint8Array): void => {
  if (iv.byteLength !== AES_GCM_IV_BYTES) {
    throw new CrossPlatformCryptoError('AES-GCM requires a 96-bit nonce.');
  }
};

const usableWebCrypto = (candidate: Crypto | null | undefined): candidate is Crypto =>
  Boolean(
    candidate?.subtle &&
    typeof candidate.getRandomValues === 'function' &&
    typeof candidate.subtle.digest === 'function' &&
    typeof candidate.subtle.importKey === 'function'
  );

export const createWebCryptoProvider = (webCrypto: Crypto): CrossPlatformCryptoProvider => {
  if (!usableWebCrypto(webCrypto)) {
    throw new CrossPlatformCryptoError('Web Crypto is unavailable.');
  }
  return {
    kind: 'web-crypto',
    async randomBytes(byteCount) {
      assertByteCount(byteCount);
      return webCrypto.getRandomValues(new Uint8Array(byteCount));
    },
    async sha256(data) {
      return new Uint8Array(await webCrypto.subtle.digest('SHA-256', asArrayBuffer(data)));
    },
    async importAesGcmKey(rawKey) {
      assertAesKey(rawKey);
      const key = await webCrypto.subtle.importKey('raw', asArrayBuffer(rawKey), { name: 'AES-GCM' }, false, [
        'encrypt',
        'decrypt'
      ]);
      return {
        async encrypt(plaintext, iv, additionalData) {
          assertAesIv(iv);
          const encrypted = await webCrypto.subtle.encrypt(
            {
              name: 'AES-GCM',
              iv: asArrayBuffer(iv),
              ...(additionalData ? { additionalData: asArrayBuffer(additionalData) } : {}),
              tagLength: AES_GCM_TAG_BYTES * 8
            },
            key,
            asArrayBuffer(plaintext)
          );
          return new Uint8Array(encrypted);
        },
        async decrypt(ciphertextWithTag, iv, additionalData) {
          assertAesIv(iv);
          const plaintext = await webCrypto.subtle.decrypt(
            {
              name: 'AES-GCM',
              iv: asArrayBuffer(iv),
              ...(additionalData ? { additionalData: asArrayBuffer(additionalData) } : {}),
              tagLength: AES_GCM_TAG_BYTES * 8
            },
            key,
            asArrayBuffer(ciphertextWithTag)
          );
          return new Uint8Array(plaintext);
        }
      };
    }
  };
};

/**
 * Narrow bridge around Expo Crypto's native API. Tests inject this boundary so
 * the native provider path can be exercised without loading a native module in Node.
 */
export interface ExpoCryptoBridge {
  randomBytes(byteCount: number): Promise<Uint8Array>;
  sha256(data: Uint8Array): Promise<Uint8Array>;
  importAesGcmKey(rawKey: Uint8Array): Promise<AesGcmKey>;
}

export type ExpoCryptoBridgeLoader = () => Promise<ExpoCryptoBridge>;

const loadOfficialExpoCryptoBridge: ExpoCryptoBridgeLoader = async () => {
  try {
    const Crypto = await import('expo-crypto');
    return {
      async randomBytes(byteCount) {
        return copyBytes(await Crypto.getRandomBytesAsync(byteCount));
      },
      async sha256(data) {
        return new Uint8Array(await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, asArrayBuffer(data)));
      },
      async importAesGcmKey(rawKey) {
        assertAesKey(rawKey);
        const nativeKeyBytes = Uint8Array.from(rawKey);
        let key: Awaited<ReturnType<typeof Crypto.AESEncryptionKey.import>>;
        try {
          key = await Crypto.AESEncryptionKey.import(nativeKeyBytes);
        } finally {
          nativeKeyBytes.fill(0);
        }
        return {
          async encrypt(plaintext, iv, additionalData) {
            assertAesIv(iv);
            const sealed = await Crypto.aesEncryptAsync(Uint8Array.from(plaintext), key, {
              nonce: { bytes: Uint8Array.from(iv) },
              tagLength: AES_GCM_TAG_BYTES,
              ...(additionalData ? { additionalData: Uint8Array.from(additionalData) } : {})
            });
            return copyBytes(await sealed.ciphertext({ encoding: 'bytes', includeTag: true }));
          },
          async decrypt(ciphertextWithTag, iv, additionalData) {
            assertAesIv(iv);
            const sealed = Crypto.AESSealedData.fromParts(
              Uint8Array.from(iv),
              Uint8Array.from(ciphertextWithTag),
              AES_GCM_TAG_BYTES
            );
            return copyBytes(
              await Crypto.aesDecryptAsync(sealed, key, {
                output: 'bytes',
                ...(additionalData ? { additionalData: Uint8Array.from(additionalData) } : {})
              })
            );
          }
        };
      }
    };
  } catch (error) {
    throw new CrossPlatformCryptoError('Expo Crypto native services are unavailable.', { cause: error });
  }
};

export const createExpoCryptoProvider = (
  loadBridge: ExpoCryptoBridgeLoader = loadOfficialExpoCryptoBridge
): CrossPlatformCryptoProvider => {
  let bridgePromise: Promise<ExpoCryptoBridge> | undefined;
  const bridge = () => {
    bridgePromise ??= loadBridge().catch(error => {
      bridgePromise = undefined;
      throw error instanceof CrossPlatformCryptoError
        ? error
        : new CrossPlatformCryptoError('Expo Crypto native services are unavailable.', { cause: error });
    });
    return bridgePromise;
  };
  return {
    kind: 'expo-crypto',
    async randomBytes(byteCount) {
      assertByteCount(byteCount);
      const bytes = copyBytes(await (await bridge()).randomBytes(byteCount));
      if (bytes.byteLength !== byteCount) {
        throw new CrossPlatformCryptoError('Expo Crypto returned an invalid random byte count.');
      }
      return bytes;
    },
    async sha256(data) {
      const digest = copyBytes(await (await bridge()).sha256(Uint8Array.from(data)));
      if (digest.byteLength !== 32) {
        throw new CrossPlatformCryptoError('Expo Crypto returned an invalid SHA-256 digest.');
      }
      return digest;
    },
    async importAesGcmKey(rawKey) {
      assertAesKey(rawKey);
      const keyCopy = Uint8Array.from(rawKey);
      try {
        return await (await bridge()).importAesGcmKey(keyCopy);
      } finally {
        keyCopy.fill(0);
      }
    }
  };
};

export type CrossPlatformCryptoResolution = {
  /** `null` explicitly removes a Web Crypto candidate for fail-closed tests. */
  webCrypto?: Crypto | null;
  expoProvider?: CrossPlatformCryptoProvider;
  runtime?: 'auto' | 'native' | 'web';
};

const isDirectWebCryptoRuntime = (): boolean =>
  typeof document !== 'undefined' ||
  (typeof process !== 'undefined' && process.release?.name === 'node' && typeof process.versions?.node === 'string');

export const resolveCrossPlatformCryptoProvider = (
  options: CrossPlatformCryptoResolution = {}
): CrossPlatformCryptoProvider => {
  const webCrypto = options.webCrypto === undefined ? globalThis.crypto : options.webCrypto;
  const runtime = options.runtime ?? 'auto';
  // React Native must use the installed Expo module even if an unrelated
  // package happens to publish a partial global crypto object. Direct
  // WebCrypto is selected only for explicit web use or a real DOM/Node host.
  if (runtime !== 'native' && (runtime === 'web' || isDirectWebCryptoRuntime()) && usableWebCrypto(webCrypto)) {
    return createWebCryptoProvider(webCrypto);
  }
  return options.expoProvider ?? createExpoCryptoProvider();
};
