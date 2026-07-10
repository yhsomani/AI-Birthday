import type { KeyValueStore } from '../state/persistence';

const LEGACY_FALLBACK_PREFIX = 'fallback.';

export type ProtectedStorageOperation = 'inspect' | 'read' | 'write' | 'remove';

export type ProtectedStorageErrorCode =
  | 'unavailable'
  | 'availability-check-failed'
  | 'read-failed'
  | 'write-failed'
  | 'write-verification-failed'
  | 'remove-failed'
  | 'legacy-inspection-failed'
  | 'legacy-plaintext-detected'
  | 'legacy-cleanup-failed';

const protectedStorageErrorMessages: Record<ProtectedStorageErrorCode, string> = {
  unavailable:
    'Protected storage is unavailable on this device. RelateAI did not read or write private data. Use a supported Android or iOS device with protected storage enabled.',
  'availability-check-failed':
    'Protected storage could not be verified. RelateAI did not access private data. Restart the app, unlock the device, and try again.',
  'read-failed':
    'Private data could not be read from protected storage. RelateAI did not use a plaintext fallback. Unlock the device and try again.',
  'write-failed':
    'Private data could not be saved to protected storage. No plaintext copy was created. Keep the app open, unlock the device, and try again.',
  'write-verification-failed':
    'Protected storage could not verify the saved private data. No legacy plaintext copy was removed. Keep the app open and try again.',
  'remove-failed':
    'Private data could not be removed from protected storage. RelateAI did not report the removal as complete. Unlock the device and try again.',
  'legacy-inspection-failed':
    'RelateAI could not verify whether obsolete plaintext data exists. Private state was not loaded. Restart the app and try again.',
  'legacy-plaintext-detected':
    'Unencrypted legacy data was detected and was not loaded. Migration or deletion must be explicitly confirmed before continuing.',
  'legacy-cleanup-failed':
    'An obsolete plaintext copy could not be removed. Private state was not loaded or reported as safely saved. Restart the app and try again.'
};

/**
 * An actionable, non-sensitive failure from the protected state boundary.
 *
 * Callers may use `code` to present recovery UI. The message intentionally
 * never contains a storage key, stored value, or native exception text.
 */
export class ProtectedStorageError extends Error {
  readonly code: ProtectedStorageErrorCode;
  readonly operation: ProtectedStorageOperation;

  constructor(code: ProtectedStorageErrorCode, operation: ProtectedStorageOperation, _cause?: unknown) {
    super(protectedStorageErrorMessages[code]);
    this.name = 'ProtectedStorageError';
    this.code = code;
    this.operation = operation;
  }
}

export interface ProtectedStorageStatus {
  available: boolean;
  protection: 'platform-protected' | 'unavailable';
  legacyPlaintext: 'none' | 'migration-required' | 'unknown';
  legacyPlaintextKeyCount?: number;
  errorCode?: ProtectedStorageErrorCode;
  message?: string;
}

export interface ProtectedStorageBackend {
  isAvailableAsync(): Promise<boolean>;
  getItemAsync(key: string): Promise<string | null>;
  setItemAsync(key: string, value: string, options?: { keychainAccessible?: number }): Promise<void>;
  deleteItemAsync(key: string): Promise<void>;
}

/**
 * Deliberately omits getItem/setItem. The active store can discover and purge
 * legacy fallback keys, but it has no capability to ingest or create plaintext.
 */
export interface LegacyPlaintextInventory {
  getAllKeys(): Promise<readonly string[]>;
  removeItem(key: string): Promise<void>;
}

export interface ProtectedKeyValueStore extends KeyValueStore {
  getProtectionStatus(): Promise<ProtectedStorageStatus>;
}

export interface CreateProtectedStateStoreOptions {
  protectedBackend: ProtectedStorageBackend;
  legacyInventory: LegacyPlaintextInventory;
  keychainAccessible: number;
}

const legacyKeyFor = (key: string): string => `${LEGACY_FALLBACK_PREFIX}${key}`;

export const createProtectedStateStore = ({
  protectedBackend,
  legacyInventory,
  keychainAccessible
}: CreateProtectedStateStoreOptions): ProtectedKeyValueStore => {
  let legacyKeyIndexPromise: Promise<Set<string>> | undefined;

  const assertAvailable = async (operation: ProtectedStorageOperation): Promise<void> => {
    let available: boolean;
    try {
      available = await protectedBackend.isAvailableAsync();
    } catch (error) {
      throw new ProtectedStorageError('availability-check-failed', operation, error);
    }
    if (!available) {
      throw new ProtectedStorageError('unavailable', operation);
    }
  };

  const readLegacyKeyIndex = async (operation: ProtectedStorageOperation): Promise<Set<string>> => {
    if (!legacyKeyIndexPromise) {
      legacyKeyIndexPromise = legacyInventory
        .getAllKeys()
        .then(keys => new Set(keys.filter(key => key.startsWith(LEGACY_FALLBACK_PREFIX))))
        .catch(error => {
          legacyKeyIndexPromise = undefined;
          throw new ProtectedStorageError('legacy-inspection-failed', operation, error);
        });
    }
    return legacyKeyIndexPromise;
  };

  return {
    async getItem(key: string) {
      await assertAvailable('read');

      const legacyKeys = await readLegacyKeyIndex('read');
      if (legacyKeys.has(legacyKeyFor(key))) {
        // The fallback may be newer than an existing protected value. Never
        // choose between them implicitly or let startup overwrite either one.
        throw new ProtectedStorageError('legacy-plaintext-detected', 'read');
      }

      let value: string | null;
      try {
        value = await protectedBackend.getItemAsync(key);
      } catch (error) {
        throw new ProtectedStorageError('read-failed', 'read', error);
      }

      return value;
    },

    async setItem(key: string, value: string) {
      await assertAvailable('write');

      const legacyKeys = await readLegacyKeyIndex('write');
      if (legacyKeys.has(legacyKeyFor(key))) {
        // Ordinary persistence must not become an implicit migration. The
        // caller must explicitly resolve or delete the legacy copy first.
        throw new ProtectedStorageError('legacy-plaintext-detected', 'write');
      }

      try {
        await protectedBackend.setItemAsync(key, value, { keychainAccessible });
      } catch (error) {
        throw new ProtectedStorageError('write-failed', 'write', error);
      }

      let verifiedValue: string | null;
      try {
        verifiedValue = await protectedBackend.getItemAsync(key);
      } catch (error) {
        throw new ProtectedStorageError('write-verification-failed', 'write', error);
      }
      if (verifiedValue !== value) {
        throw new ProtectedStorageError('write-verification-failed', 'write');
      }
    },

    async removeItem(key: string) {
      let protectedStorageError: ProtectedStorageError | undefined;
      try {
        await assertAvailable('remove');
        await protectedBackend.deleteItemAsync(key);
      } catch (error) {
        protectedStorageError =
          error instanceof ProtectedStorageError
            ? error
            : new ProtectedStorageError('remove-failed', 'remove', error);
      }

      // Always attempt to purge an obsolete fallback during a user-requested
      // removal, but never hide a failure to remove the protected copy.
      try {
        await legacyInventory.removeItem(legacyKeyFor(key));
        const legacyKeys = await legacyKeyIndexPromise;
        legacyKeys?.delete(legacyKeyFor(key));
      } catch (error) {
        if (!protectedStorageError) {
          protectedStorageError = new ProtectedStorageError('legacy-cleanup-failed', 'remove', error);
        }
      }

      if (protectedStorageError) {
        throw protectedStorageError;
      }
    },

    async getProtectionStatus() {
      let available: boolean;
      try {
        available = await protectedBackend.isAvailableAsync();
      } catch (error) {
        const protectedError = new ProtectedStorageError('availability-check-failed', 'inspect', error);
        return {
          available: false,
          protection: 'unavailable',
          legacyPlaintext: 'unknown',
          errorCode: protectedError.code,
          message: protectedError.message
        };
      }

      let legacyKeys: Set<string>;
      try {
        legacyKeys = await readLegacyKeyIndex('inspect');
      } catch (error) {
        const protectedError =
          error instanceof ProtectedStorageError
            ? error
            : new ProtectedStorageError('legacy-inspection-failed', 'inspect', error);
        return {
          available,
          protection: available ? 'platform-protected' : 'unavailable',
          legacyPlaintext: 'unknown',
          errorCode: protectedError.code,
          message: protectedError.message
        };
      }

      const legacyPlaintextKeyCount = legacyKeys.size;
      return {
        available,
        protection: available ? 'platform-protected' : 'unavailable',
        legacyPlaintext: legacyPlaintextKeyCount > 0 ? 'migration-required' : 'none',
        legacyPlaintextKeyCount,
        ...(!available
          ? {
              errorCode: 'unavailable' as const,
              message: protectedStorageErrorMessages.unavailable
            }
          : {})
      };
    }
  };
};
