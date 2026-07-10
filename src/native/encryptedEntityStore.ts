import type { EntityRepository } from '../domain/entityRepository';
import {
  EncryptedTransactionalEntityStore,
  type EncryptedEntityStoreOptions,
  type EncryptedStoreFileAdapter,
  type ProtectedRepositoryKeyStore
} from './encryptedEntityStoreCore';
import { createDualReadSingleWriteEntityRepository } from './normalizedStoreMigration';

const ENTITY_STORE_DIRECTORY = 'relateai-entity-store-v1/';

export interface ExpoEntityStoreFileSystem {
  documentDirectory: string | null;
  utf8Encoding: 'utf8';
  getInfoAsync(uri: string): Promise<{ exists: boolean; isDirectory?: boolean }>;
  makeDirectoryAsync(uri: string, options: { intermediates: true }): Promise<void>;
  readDirectoryAsync(uri: string): Promise<string[]>;
  readAsStringAsync(uri: string, options: { encoding: 'utf8' }): Promise<string>;
  writeAsStringAsync(uri: string, contents: string, options: { encoding: 'utf8' }): Promise<void>;
  deleteAsync(uri: string, options: { idempotent: true }): Promise<void>;
}

const assertAdapterFileName = (name: string): void => {
  if (!/^[a-z0-9][a-z0-9.-]{0,180}$/i.test(name) || name.includes('..')) {
    throw new Error('Encrypted entity-store file name is invalid.');
  }
};

export const createExpoEntityStoreFileAdapter = (fileSystem: ExpoEntityStoreFileSystem): EncryptedStoreFileAdapter => {
  if (!fileSystem.documentDirectory) {
    throw new Error('Encrypted entity storage is unavailable because the application document directory is missing.');
  }
  const directory = `${fileSystem.documentDirectory}${ENTITY_STORE_DIRECTORY}`;
  let directoryPromise: Promise<void> | undefined;
  const ensureDirectory = () => {
    directoryPromise ??= fileSystem
      .getInfoAsync(directory)
      .then(info =>
        info.exists && info.isDirectory !== false
          ? undefined
          : fileSystem.makeDirectoryAsync(directory, { intermediates: true })
      );
    return directoryPromise;
  };
  const uriFor = (name: string) => {
    assertAdapterFileName(name);
    return `${directory}${name}`;
  };

  return {
    async read(name) {
      await ensureDirectory();
      const uri = uriFor(name);
      const info = await fileSystem.getInfoAsync(uri);
      if (!info.exists || info.isDirectory === true) return null;
      return fileSystem.readAsStringAsync(uri, { encoding: fileSystem.utf8Encoding });
    },

    async write(name, contents) {
      await ensureDirectory();
      await fileSystem.writeAsStringAsync(uriFor(name), contents, {
        encoding: fileSystem.utf8Encoding
      });
    },

    async remove(name) {
      await ensureDirectory();
      await fileSystem.deleteAsync(uriFor(name), { idempotent: true });
    },

    async list() {
      await ensureDirectory();
      return (await fileSystem.readDirectoryAsync(directory)).filter(name => {
        try {
          assertAdapterFileName(name);
          return true;
        } catch {
          return false;
        }
      });
    }
  };
};

export const createNativeEncryptedEntityRepository = (
  options: Omit<EncryptedEntityStoreOptions, 'protectedStore'>,
  protectedStore: ProtectedRepositoryKeyStore
): EntityRepository =>
  new EncryptedTransactionalEntityStore({
    ...options,
    protectedStore
  });

let defaultRepositoryPromise: Promise<EntityRepository> | undefined;

export const loadDefaultEncryptedEntityRepository = (): Promise<EntityRepository> => {
  if (!defaultRepositoryPromise) {
    defaultRepositoryPromise = Promise.all([import('expo-file-system/legacy'), import('./secureStateStore')]).then(
      ([fileSystem, protectedStorage]) =>
        createNativeEncryptedEntityRepository(
          {
            files: createExpoEntityStoreFileAdapter({
              documentDirectory: fileSystem.documentDirectory,
              utf8Encoding: fileSystem.EncodingType.UTF8,
              getInfoAsync: fileSystem.getInfoAsync,
              makeDirectoryAsync: fileSystem.makeDirectoryAsync,
              readDirectoryAsync: fileSystem.readDirectoryAsync,
              readAsStringAsync: fileSystem.readAsStringAsync,
              writeAsStringAsync: fileSystem.writeAsStringAsync,
              deleteAsync: fileSystem.deleteAsync
            })
          },
          protectedStorage.secureStateStore
        )
    );
  }
  return defaultRepositoryPromise;
};

let defaultMigratingRepositoryPromise: Promise<EntityRepository> | undefined;

/**
 * Rollout composition: normalized SecureStore remains the read-only source
 * until protected count/checksum checkpoints commit the encrypted repository.
 */
export const loadDefaultMigratingEntityRepository = (): Promise<EntityRepository> => {
  if (!defaultMigratingRepositoryPromise) {
    defaultMigratingRepositoryPromise = Promise.all([
      loadDefaultEncryptedEntityRepository(),
      import('./secureStateStore')
    ]).then(([repository, protectedStorage]) =>
      createDualReadSingleWriteEntityRepository({
        legacyStore: protectedStorage.secureStateStore,
        repository,
        protectedStore: protectedStorage.secureStateStore
      })
    );
  }
  return defaultMigratingRepositoryPromise;
};
