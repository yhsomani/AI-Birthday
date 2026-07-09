import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import {
  BACKUP_FILE_EXTENSION,
  createEncryptedBackup,
  decryptEncryptedBackup,
  previewEncryptedBackup,
  type BackupPreview
} from '../domain/backup';
import type { AppState } from '../domain/types';

export type BackupFileExportResult = {
  uri: string;
  shared: boolean;
  preview: BackupPreview;
};

export type BackupFilePickResult = {
  name: string;
  uri: string;
  raw: string;
  preview: BackupPreview;
};

const safeTimestamp = () => new Date().toISOString().replace(/[:.]/g, '-');

export const exportEncryptedBackupFile = async (
  state: AppState,
  passphrase: string
): Promise<BackupFileExportResult> => {
  const raw = await createEncryptedBackup(state, passphrase);
  const preview = previewEncryptedBackup(raw);
  const directory = FileSystem.documentDirectory ?? FileSystem.cacheDirectory;
  if (!directory) {
    throw new Error('Backup export storage is not available on this device.');
  }

  const uri = `${directory}relateai-backup-${safeTimestamp()}.${BACKUP_FILE_EXTENSION}`;
  await FileSystem.writeAsStringAsync(uri, raw, {
    encoding: FileSystem.EncodingType.UTF8
  });

  let shared = false;
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(uri, {
      dialogTitle: 'Export RelateAI encrypted backup',
      mimeType: 'application/json',
      UTI: 'public.json'
    });
    shared = true;
  }

  return {
    uri,
    shared,
    preview
  };
};

export const pickEncryptedBackupFile = async (): Promise<BackupFilePickResult | undefined> => {
  const result = await DocumentPicker.getDocumentAsync({
    type: '*/*',
    copyToCacheDirectory: true,
    multiple: false,
    base64: false
  });
  if (result.canceled) {
    return undefined;
  }

  const asset = result.assets[0];
  if (!asset) {
    return undefined;
  }

  const raw = await FileSystem.readAsStringAsync(asset.uri, {
    encoding: FileSystem.EncodingType.UTF8
  });

  return {
    name: asset.name,
    uri: asset.uri,
    raw,
    preview: previewEncryptedBackup(raw)
  };
};

export const restoreEncryptedBackupFile = async (raw: string, passphrase: string): Promise<AppState> =>
  decryptEncryptedBackup(raw, passphrase);
