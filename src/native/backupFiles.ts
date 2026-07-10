import {
  BACKUP_FILE_EXTENSION,
  MAX_BACKUP_RAW_BYTES,
  assertBackupRawInput,
  createEncryptedBackup,
  decryptEncryptedBackup,
  previewEncryptedBackup,
  type BackupPreview
} from '../data/encryptedBackup';
import type { AppState } from '../domain/types';

export type BackupFileExportResult = {
  uri: string;
  fileName: string;
  byteCount: number;
  shared: boolean;
  preview: BackupPreview;
  disposition: 'temporary-shared' | 'saved-export';
  temporaryFileRemoved: boolean;
  /** True only after a user-accessible destination was durably verified. */
  verifiedPortableCopy: boolean;
};

export type BackupFilePlatform = 'android' | 'ios' | 'web' | 'macos' | 'windows' | 'other';

export type BackupFilePickResult = {
  name: string;
  uri: string;
  raw: string;
  preview: BackupPreview;
  temporaryFileRemoved: boolean;
};

export interface BackupDocumentAsset {
  name: string;
  uri: string;
  size?: number;
  mimeType?: string;
}

export interface BackupDocumentPicker {
  getDocumentAsync(options: {
    type: string[];
    copyToCacheDirectory: true;
    multiple: false;
    base64: false;
  }): Promise<{ canceled: boolean; assets?: BackupDocumentAsset[] | null }>;
}

export interface BackupFileSystem {
  documentDirectory: string | null;
  cacheDirectory: string | null;
  utf8Encoding: 'utf8';
  writeAsStringAsync(uri: string, value: string, options: { encoding: 'utf8' }): Promise<void>;
  readAsStringAsync(uri: string, options: { encoding: 'utf8' }): Promise<string>;
  deleteAsync(uri: string, options: { idempotent: true }): Promise<void>;
  getInfoAsync(uri: string): Promise<{ exists: boolean; isDirectory?: boolean; size?: number }>;
}

export type BackupDirectoryPermissionResult = { granted: false } | { granted: true; directoryUri: string };

export interface BackupStorageAccessFramework {
  requestDirectoryPermissionsAsync(initialFileUrl?: string | null): Promise<BackupDirectoryPermissionResult>;
  createFileAsync(directoryUri: string, fileName: string, mimeType: string): Promise<string>;
}

export interface BackupSharing {
  isAvailableAsync(): Promise<boolean>;
  shareAsync(uri: string, options: { dialogTitle: string; mimeType: string; UTI: string }): Promise<void>;
}

export interface BackupFileCodec {
  create(state: AppState, passphrase: string): Promise<string>;
  preview(raw: string): BackupPreview;
  decrypt(raw: string, passphrase: string): Promise<AppState>;
}

export interface BackupFileDependencies {
  documentPicker: BackupDocumentPicker;
  fileSystem: BackupFileSystem;
  sharing: BackupSharing;
  platform?: BackupFilePlatform;
  storageAccessFramework?: BackupStorageAccessFramework;
  codec?: BackupFileCodec;
  now?: () => Date;
}

export interface BackupFileService {
  exportEncryptedBackupFile(state: AppState, passphrase: string): Promise<BackupFileExportResult>;
  saveEncryptedBackupFile(state: AppState, passphrase: string): Promise<BackupFileExportResult>;
  pickEncryptedBackupFile(): Promise<BackupFilePickResult | undefined>;
  cleanupTemporaryBackupFile(uri: string): Promise<boolean>;
  restoreEncryptedBackupFile(raw: string, passphrase: string): Promise<AppState>;
}

const supportedBackupMimeTypes = new Set(['application/json', 'application/octet-stream', 'text/json', 'text/plain']);
const generatedBackupPrefix = 'relateai-backup-';
const maximumBackupFilenameLength = 255;
const maximumBackupUriLength = 4096;

const defaultCodec: BackupFileCodec = {
  create: createEncryptedBackup,
  preview: previewEncryptedBackup,
  decrypt: decryptEncryptedBackup
};

const safeTimestamp = (date: Date) => date.toISOString().replace(/[:.]/g, '-');

const hasTraversalSegment = (uri: string): boolean => {
  let decoded = uri;
  try {
    decoded = decodeURIComponent(uri);
  } catch {
    return true;
  }
  return decoded.split('/').some(segment => segment === '..');
};

const isUriInsideDirectory = (uri: string, directory: string | null): boolean =>
  Boolean(directory) &&
  uri.length <= maximumBackupUriLength &&
  !hasTraversalSegment(uri) &&
  uri.startsWith(directory as string) &&
  uri.length > (directory as string).length;

const hasSupportedBackupFilename = (name: string): boolean => {
  if (name.length === 0 || name.length > maximumBackupFilenameLength || name.includes('/') || name.includes('\\')) {
    return false;
  }
  const normalized = name.toLowerCase();
  return normalized.endsWith(`.${BACKUP_FILE_EXTENSION}`) || normalized.endsWith('.json');
};

const assertSupportedBackupAsset = async (asset: BackupDocumentAsset, fileSystem: BackupFileSystem): Promise<void> => {
  if (!hasSupportedBackupFilename(asset.name)) {
    throw new Error(`Choose a .${BACKUP_FILE_EXTENSION} or .json RelateAI backup file.`);
  }
  if (asset.mimeType) {
    const normalizedMimeType = asset.mimeType.split(';', 1)[0].trim().toLowerCase();
    if (!supportedBackupMimeTypes.has(normalizedMimeType)) {
      throw new Error('The selected file type is not a supported RelateAI backup.');
    }
  }
  if (
    typeof asset.uri !== 'string' ||
    asset.uri.length === 0 ||
    asset.uri.length > maximumBackupUriLength ||
    hasTraversalSegment(asset.uri)
  ) {
    throw new Error('The selected backup location is invalid.');
  }

  let size = asset.size;
  if (size === undefined) {
    const info = await fileSystem.getInfoAsync(asset.uri);
    if (info.exists && info.isDirectory !== true) {
      size = info.size;
    }
  }
  if (typeof size !== 'number' || !Number.isSafeInteger(size) || size < 1) {
    throw new Error('The selected backup size could not be verified safely.');
  }
  if (size > MAX_BACKUP_RAW_BYTES) {
    throw new Error(`Backup file must be no larger than ${MAX_BACKUP_RAW_BYTES} bytes.`);
  }
};

export const createBackupFileService = ({
  documentPicker,
  fileSystem,
  sharing,
  platform = 'other',
  storageAccessFramework,
  codec = defaultCodec,
  now = () => new Date()
}: BackupFileDependencies): BackupFileService => {
  let exportSequence = 0;

  const nextBackupFilename = () => {
    exportSequence += 1;
    return `${generatedBackupPrefix}${safeTimestamp(now())}-${exportSequence}.${BACKUP_FILE_EXTENSION}`;
  };

  const writeBackup = async (
    state: AppState,
    passphrase: string,
    directory: string
  ): Promise<{ uri: string; fileName: string; byteCount: number; preview: BackupPreview }> => {
    const raw = await codec.create(state, passphrase);
    assertBackupRawInput(raw);
    const preview = codec.preview(raw);
    const fileName = nextBackupFilename();
    const uri = `${directory}${fileName}`;
    await fileSystem.writeAsStringAsync(uri, raw, { encoding: fileSystem.utf8Encoding });
    return {
      uri,
      fileName,
      byteCount: new TextEncoder().encode(raw).byteLength,
      preview
    };
  };

  const createBoundedBackup = async (
    state: AppState,
    passphrase: string
  ): Promise<{ raw: string; preview: BackupPreview; byteCount: number }> => {
    const raw = await codec.create(state, passphrase);
    assertBackupRawInput(raw);
    const preview = codec.preview(raw);
    return { raw, preview, byteCount: new TextEncoder().encode(raw).byteLength };
  };

  const assertStorageAccessUri = (uri: string, label: string): void => {
    if (
      typeof uri !== 'string' ||
      uri.length === 0 ||
      uri.length > maximumBackupUriLength ||
      !uri.startsWith('content://') ||
      hasTraversalSegment(uri)
    ) {
      throw new Error(`${label} is invalid.`);
    }
  };

  const saveAndroidPortableBackup = async (state: AppState, passphrase: string): Promise<BackupFileExportResult> => {
    if (!storageAccessFramework) {
      throw new Error('User-selected backup export storage is not available on this Android device.');
    }

    const permission = await storageAccessFramework.requestDirectoryPermissionsAsync();
    if (!permission.granted) {
      throw new Error('Backup export was cancelled before a destination was selected.');
    }
    assertStorageAccessUri(permission.directoryUri, 'The selected backup directory');

    const { raw, preview, byteCount } = await createBoundedBackup(state, passphrase);
    const fileName = nextBackupFilename();
    const uri = await storageAccessFramework.createFileAsync(permission.directoryUri, fileName, 'application/json');
    assertStorageAccessUri(uri, 'The created backup file location');

    await fileSystem.writeAsStringAsync(uri, raw, { encoding: fileSystem.utf8Encoding });
    const info = await fileSystem.getInfoAsync(uri);
    if (!info.exists || info.isDirectory === true) {
      throw new Error('The encrypted backup write could not be verified at the selected destination.');
    }

    const writtenRaw = await fileSystem.readAsStringAsync(uri, { encoding: fileSystem.utf8Encoding });
    assertBackupRawInput(writtenRaw);
    if (writtenRaw !== raw) {
      throw new Error('The encrypted backup read-back did not match the exported file.');
    }
    codec.preview(writtenRaw);

    return {
      uri,
      fileName,
      byteCount,
      shared: false,
      preview,
      disposition: 'saved-export',
      temporaryFileRemoved: false,
      verifiedPortableCopy: true
    };
  };

  const removeOwnedCacheFile = async (uri: string, generatedOnly: boolean): Promise<boolean> => {
    if (!isUriInsideDirectory(uri, fileSystem.cacheDirectory)) {
      return false;
    }
    const filename = uri.slice((fileSystem.cacheDirectory as string).length);
    if (generatedOnly && !filename.startsWith(generatedBackupPrefix)) {
      return false;
    }
    await fileSystem.deleteAsync(uri, { idempotent: true });
    return true;
  };

  const saveEncryptedBackupFile = async (state: AppState, passphrase: string): Promise<BackupFileExportResult> => {
    if (platform === 'android') {
      return saveAndroidPortableBackup(state, passphrase);
    }
    if (!fileSystem.documentDirectory) {
      throw new Error('Persistent backup export storage is not available on this device.');
    }
    const { uri, fileName, byteCount, preview } = await writeBackup(state, passphrase, fileSystem.documentDirectory);
    return {
      uri,
      fileName,
      byteCount,
      shared: false,
      preview,
      disposition: 'saved-export',
      temporaryFileRemoved: false,
      // The app document directory is durable but not a user-held export.
      verifiedPortableCopy: false
    };
  };

  return {
    async exportEncryptedBackupFile(state: AppState, passphrase: string) {
      if (!(await sharing.isAvailableAsync())) {
        // Export remains useful without a share sheet, but this is an explicit
        // persistent save and is never handled by temporary cleanup.
        return saveEncryptedBackupFile(state, passphrase);
      }
      if (!fileSystem.cacheDirectory) {
        throw new Error('Temporary backup sharing storage is not available on this device.');
      }

      const { uri, fileName, byteCount, preview } = await writeBackup(state, passphrase, fileSystem.cacheDirectory);
      let temporaryFileRemoved = false;
      try {
        await sharing.shareAsync(uri, {
          dialogTitle: 'Export RelateAI encrypted backup',
          mimeType: 'application/json',
          UTI: 'public.json'
        });
      } finally {
        // Expo resolves shareAsync only after the platform share flow closes.
        // Cleanup in finally also covers dismissal and share-target failure.
        temporaryFileRemoved = await removeOwnedCacheFile(uri, true);
      }

      return {
        uri,
        fileName,
        byteCount,
        shared: true,
        preview,
        disposition: 'temporary-shared',
        temporaryFileRemoved,
        // Expo Sharing cannot distinguish a completed save from dismissal.
        verifiedPortableCopy: false
      };
    },

    saveEncryptedBackupFile,

    async pickEncryptedBackupFile() {
      const result = await documentPicker.getDocumentAsync({
        type: ['application/json', 'application/octet-stream', 'text/json', 'text/plain'],
        copyToCacheDirectory: true,
        multiple: false,
        base64: false
      });
      if (result.canceled) {
        return undefined;
      }

      const asset = result.assets?.[0];
      if (!asset) {
        return undefined;
      }

      let temporaryFileRemoved = false;
      try {
        await assertSupportedBackupAsset(asset, fileSystem);
        const raw = await fileSystem.readAsStringAsync(asset.uri, { encoding: fileSystem.utf8Encoding });
        assertBackupRawInput(raw);
        const preview = codec.preview(raw);
        temporaryFileRemoved = await removeOwnedCacheFile(asset.uri, false);
        return {
          name: asset.name,
          uri: asset.uri,
          raw,
          preview,
          temporaryFileRemoved
        };
      } finally {
        if (!temporaryFileRemoved) {
          // Only copied cache content is app-owned. Never delete the user's
          // original document URI when a platform does not make a cache copy.
          await removeOwnedCacheFile(asset.uri, false);
        }
      }
    },

    async cleanupTemporaryBackupFile(uri: string) {
      return removeOwnedCacheFile(uri, true);
    },

    async restoreEncryptedBackupFile(raw: string, passphrase: string) {
      assertBackupRawInput(raw);
      return codec.decrypt(raw, passphrase);
    }
  };
};

let defaultServicePromise: Promise<BackupFileService> | undefined;

const loadDefaultBackupFileService = (): Promise<BackupFileService> => {
  if (!defaultServicePromise) {
    defaultServicePromise = Promise.all([
      import('expo-document-picker'),
      import('expo-file-system/legacy'),
      import('expo-sharing'),
      import('react-native')
    ]).then(([documentPicker, fileSystem, sharing, reactNative]) =>
      createBackupFileService({
        documentPicker,
        platform: reactNative.Platform.OS,
        fileSystem: {
          documentDirectory: fileSystem.documentDirectory,
          cacheDirectory: fileSystem.cacheDirectory,
          utf8Encoding: fileSystem.EncodingType.UTF8,
          writeAsStringAsync: fileSystem.writeAsStringAsync,
          readAsStringAsync: fileSystem.readAsStringAsync,
          deleteAsync: fileSystem.deleteAsync,
          getInfoAsync: fileSystem.getInfoAsync
        },
        storageAccessFramework:
          reactNative.Platform.OS === 'android'
            ? {
                requestDirectoryPermissionsAsync: fileSystem.StorageAccessFramework.requestDirectoryPermissionsAsync,
                createFileAsync: fileSystem.StorageAccessFramework.createFileAsync
              }
            : undefined,
        sharing
      })
    );
  }
  return defaultServicePromise;
};

export const exportEncryptedBackupFile = async (state: AppState, passphrase: string): Promise<BackupFileExportResult> =>
  (await loadDefaultBackupFileService()).exportEncryptedBackupFile(state, passphrase);

export const saveEncryptedBackupFile = async (state: AppState, passphrase: string): Promise<BackupFileExportResult> =>
  (await loadDefaultBackupFileService()).saveEncryptedBackupFile(state, passphrase);

export const pickEncryptedBackupFile = async (): Promise<BackupFilePickResult | undefined> =>
  (await loadDefaultBackupFileService()).pickEncryptedBackupFile();

export const cleanupTemporaryBackupFile = async (uri: string): Promise<boolean> =>
  (await loadDefaultBackupFileService()).cleanupTemporaryBackupFile(uri);

export const restoreEncryptedBackupFile = async (raw: string, passphrase: string): Promise<AppState> =>
  (await loadDefaultBackupFileService()).restoreEncryptedBackupFile(raw, passphrase);
