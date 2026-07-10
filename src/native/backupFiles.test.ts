import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { MAX_BACKUP_RAW_BYTES, type BackupPreview } from '../data/encryptedBackup';
import { createTestState } from '../test/testState';
import { createBackupFileService, type BackupDocumentAsset, type BackupFileDependencies } from './backupFiles';

const preview: BackupPreview = {
  format: 'relateai.encrypted-backup',
  version: 2,
  app: 'RelateAI',
  createdAt: '2026-07-10T00:00:00.000Z',
  encrypted: true,
  persistenceVersion: 3,
  recordCounts: { contacts: 0, events: 0, memories: 0, gifts: 0, messages: 0, activity: 0 },
  recordCount: 0,
  warnings: []
};

const createHarness = (asset?: BackupDocumentAsset) => {
  const calls: string[] = [];
  const dependencies: BackupFileDependencies = {
    documentPicker: {
      async getDocumentAsync() {
        calls.push('pick');
        return asset ? { canceled: false, assets: [asset] } : { canceled: true, assets: null };
      }
    },
    fileSystem: {
      documentDirectory: 'file:///documents/',
      cacheDirectory: 'file:///cache/',
      utf8Encoding: 'utf8',
      async writeAsStringAsync(uri) {
        calls.push(`write:${uri}`);
      },
      async readAsStringAsync(uri) {
        calls.push(`read:${uri}`);
        return '{"encrypted":true}';
      },
      async deleteAsync(uri) {
        calls.push(`delete:${uri}`);
      },
      async getInfoAsync(uri) {
        calls.push(`info:${uri}`);
        return { exists: true, isDirectory: false, size: 18 };
      }
    },
    sharing: {
      async isAvailableAsync() {
        calls.push('share:available');
        return true;
      },
      async shareAsync(uri) {
        calls.push(`share:${uri}`);
      }
    },
    codec: {
      async create() {
        calls.push('codec:create');
        return '{"encrypted":true}';
      },
      preview(raw) {
        calls.push(`codec:preview:${raw.length}`);
        return preview;
      },
      async decrypt() {
        calls.push('codec:decrypt');
        return createTestState();
      }
    },
    now: () => new Date('2026-07-10T00:00:00.000Z')
  };
  return { calls, dependencies };
};

describe('backup file lifecycle', () => {
  it('shares from cache and deletes the temporary artifact only after sharing completes', async () => {
    const harness = createHarness();
    const service = createBackupFileService(harness.dependencies);

    const result = await service.exportEncryptedBackupFile(createTestState(), 'CorrectHorse123');

    assert.equal(result.shared, true);
    assert.equal(result.disposition, 'temporary-shared');
    assert.equal(result.temporaryFileRemoved, true);
    assert.equal(result.verifiedPortableCopy, false);
    assert.match(result.fileName, /^relateai-backup-.*\.relateai-backup$/);
    assert.equal(result.byteCount, new TextEncoder().encode('{"encrypted":true}').byteLength);
    assert.match(result.uri, /^file:\/\/\/cache\/relateai-backup-/);
    const shareIndex = harness.calls.findIndex(call => call.startsWith('share:file:'));
    const deleteIndex = harness.calls.findIndex(call => call.startsWith('delete:file:'));
    assert.ok(shareIndex >= 0);
    assert.ok(deleteIndex > shareIndex);
    assert.equal(
      harness.calls.some(call => call.includes('file:///documents/')),
      false
    );
  });

  it('cleans temporary exports even when the share target rejects', async () => {
    const harness = createHarness();
    harness.dependencies.sharing.shareAsync = async uri => {
      harness.calls.push(`share:${uri}`);
      throw new Error('share target failed');
    };
    const service = createBackupFileService(harness.dependencies);

    await assert.rejects(
      () => service.exportEncryptedBackupFile(createTestState(), 'CorrectHorse123'),
      /share target failed/i
    );
    assert.equal(
      harness.calls.some(call => call.startsWith('delete:file:///cache/')),
      true
    );
  });

  it('retains an intentional document export when platform sharing is unavailable', async () => {
    const harness = createHarness();
    harness.dependencies.sharing.isAvailableAsync = async () => false;
    const service = createBackupFileService(harness.dependencies);

    const result = await service.exportEncryptedBackupFile(createTestState(), 'CorrectHorse123');

    assert.equal(result.shared, false);
    assert.equal(result.disposition, 'saved-export');
    assert.equal(result.temporaryFileRemoved, false);
    assert.equal(result.verifiedPortableCopy, false);
    assert.match(result.fileName, /^relateai-backup-.*\.relateai-backup$/);
    assert.equal(result.byteCount, new TextEncoder().encode('{"encrypted":true}').byteLength);
    assert.match(result.uri, /^file:\/\/\/documents\/relateai-backup-/);
    assert.equal(
      harness.calls.some(call => call.startsWith('delete:')),
      false
    );
  });

  it('writes an Android export to an explicitly selected SAF directory and verifies the exact read-back', async () => {
    const harness = createHarness();
    const directoryUri = 'content://provider/tree/primary%3ADocuments';
    const destinationUri = 'content://provider/document/primary%3ADocuments%2Frelateai-backup';
    let written = '';
    let requestedFileName = '';
    harness.dependencies.platform = 'android';
    harness.dependencies.storageAccessFramework = {
      async requestDirectoryPermissionsAsync() {
        harness.calls.push('saf:select-directory');
        return { granted: true, directoryUri };
      },
      async createFileAsync(selectedDirectoryUri, name, mimeType) {
        requestedFileName = name;
        harness.calls.push(`saf:create:${selectedDirectoryUri}:${name}:${mimeType}`);
        return destinationUri;
      }
    };
    harness.dependencies.fileSystem.writeAsStringAsync = async (uri, value) => {
      harness.calls.push(`write:${uri}`);
      written = value;
    };
    harness.dependencies.fileSystem.getInfoAsync = async uri => {
      harness.calls.push(`info:${uri}`);
      return { exists: true, isDirectory: false, size: new TextEncoder().encode(written).byteLength };
    };
    harness.dependencies.fileSystem.readAsStringAsync = async uri => {
      harness.calls.push(`read:${uri}`);
      return written;
    };
    const service = createBackupFileService(harness.dependencies);

    const result = await service.saveEncryptedBackupFile(createTestState(), 'CorrectHorse123');

    assert.equal(result.uri, destinationUri);
    assert.equal(result.shared, false);
    assert.equal(result.disposition, 'saved-export');
    assert.equal(result.verifiedPortableCopy, true);
    assert.equal(result.temporaryFileRemoved, false);
    assert.equal(result.fileName, requestedFileName);
    assert.equal(result.byteCount, new TextEncoder().encode(written).byteLength);
    assert.ok(harness.calls[0] === 'saf:select-directory');
    assert.ok(harness.calls.some(call => call.includes(`saf:create:${directoryUri}:relateai-backup-`)));
    const writeIndex = harness.calls.indexOf(`write:${destinationUri}`);
    const infoIndex = harness.calls.indexOf(`info:${destinationUri}`);
    const readIndex = harness.calls.indexOf(`read:${destinationUri}`);
    assert.ok(writeIndex >= 0 && infoIndex > writeIndex && readIndex > infoIndex);
    assert.equal(
      harness.calls.some(call => call.startsWith('delete:')),
      false
    );
    assert.equal(
      harness.calls.some(call => call.includes('file:///documents/')),
      false
    );
  });

  it('treats Android SAF directory-picker cancellation as a cancelled export without creating data', async () => {
    const harness = createHarness();
    harness.dependencies.platform = 'android';
    harness.dependencies.storageAccessFramework = {
      async requestDirectoryPermissionsAsync() {
        harness.calls.push('saf:select-directory');
        return { granted: false };
      },
      async createFileAsync() {
        harness.calls.push('saf:create');
        return 'content://provider/document/unexpected';
      }
    };
    const service = createBackupFileService(harness.dependencies);

    await assert.rejects(
      () => service.saveEncryptedBackupFile(createTestState(), 'CorrectHorse123'),
      /cancelled.*destination/i
    );
    assert.deepEqual(harness.calls, ['saf:select-directory']);
  });

  it('rejects an oversized encrypted Android export before creating or writing a destination file', async () => {
    const harness = createHarness();
    harness.dependencies.platform = 'android';
    harness.dependencies.storageAccessFramework = {
      async requestDirectoryPermissionsAsync() {
        harness.calls.push('saf:select-directory');
        return { granted: true, directoryUri: 'content://provider/tree/primary%3ADocuments' };
      },
      async createFileAsync() {
        harness.calls.push('saf:create');
        return 'content://provider/document/unexpected';
      }
    };
    harness.dependencies.codec = {
      ...harness.dependencies.codec!,
      async create() {
        harness.calls.push('codec:create:oversized');
        return 'x'.repeat(MAX_BACKUP_RAW_BYTES + 1);
      }
    };
    const service = createBackupFileService(harness.dependencies);

    await assert.rejects(() => service.saveEncryptedBackupFile(createTestState(), 'CorrectHorse123'), /no larger/i);
    assert.deepEqual(harness.calls, ['saf:select-directory', 'codec:create:oversized']);
  });

  it('does not report or delete an Android user-owned file after a write failure', async () => {
    const harness = createHarness();
    const destinationUri = 'content://provider/document/primary%3ADocuments%2Fpartial-backup';
    harness.dependencies.platform = 'android';
    harness.dependencies.storageAccessFramework = {
      async requestDirectoryPermissionsAsync() {
        return { granted: true, directoryUri: 'content://provider/tree/primary%3ADocuments' };
      },
      async createFileAsync() {
        harness.calls.push(`saf:create:${destinationUri}`);
        return destinationUri;
      }
    };
    harness.dependencies.fileSystem.writeAsStringAsync = async uri => {
      harness.calls.push(`write:${uri}`);
      throw new Error('provider write failed');
    };
    const service = createBackupFileService(harness.dependencies);

    await assert.rejects(
      () => service.saveEncryptedBackupFile(createTestState(), 'CorrectHorse123'),
      /provider write failed/i
    );
    assert.equal(harness.calls.includes(`read:${destinationUri}`), false);
    assert.equal(
      harness.calls.some(call => call.startsWith('delete:')),
      false
    );
  });

  it('rejects a mismatched Android read-back and never deletes the user-owned destination', async () => {
    const harness = createHarness();
    const destinationUri = 'content://provider/document/primary%3ADocuments%2Fchanged-backup';
    harness.dependencies.platform = 'android';
    harness.dependencies.storageAccessFramework = {
      async requestDirectoryPermissionsAsync() {
        return { granted: true, directoryUri: 'content://provider/tree/primary%3ADocuments' };
      },
      async createFileAsync() {
        return destinationUri;
      }
    };
    harness.dependencies.fileSystem.getInfoAsync = async uri => {
      harness.calls.push(`info:${uri}`);
      return { exists: true, isDirectory: false };
    };
    harness.dependencies.fileSystem.readAsStringAsync = async uri => {
      harness.calls.push(`read:${uri}`);
      return '{"encrypted":false}';
    };
    const service = createBackupFileService(harness.dependencies);

    await assert.rejects(
      () => service.saveEncryptedBackupFile(createTestState(), 'CorrectHorse123'),
      /read-back did not match/i
    );
    assert.equal(
      harness.calls.some(call => call.startsWith('delete:')),
      false
    );
  });

  it('does not report or delete an Android user-owned destination when read-back fails', async () => {
    const harness = createHarness();
    const destinationUri = 'content://provider/document/primary%3ADocuments%2Funreadable-backup';
    harness.dependencies.platform = 'android';
    harness.dependencies.storageAccessFramework = {
      async requestDirectoryPermissionsAsync() {
        return { granted: true, directoryUri: 'content://provider/tree/primary%3ADocuments' };
      },
      async createFileAsync() {
        return destinationUri;
      }
    };
    harness.dependencies.fileSystem.getInfoAsync = async () => ({
      exists: true,
      isDirectory: false
    });
    harness.dependencies.fileSystem.readAsStringAsync = async uri => {
      harness.calls.push(`read:${uri}`);
      throw new Error('provider read failed');
    };
    const service = createBackupFileService(harness.dependencies);

    await assert.rejects(
      () => service.saveEncryptedBackupFile(createTestState(), 'CorrectHorse123'),
      /provider read failed/i
    );
    assert.equal(harness.calls.includes(`read:${destinationUri}`), true);
    assert.equal(
      harness.calls.some(call => call.startsWith('delete:')),
      false
    );
  });

  it('fails closed when Android SAF is unavailable and keeps non-Android document saves unverified', async () => {
    const androidHarness = createHarness();
    androidHarness.dependencies.platform = 'android';
    const androidService = createBackupFileService(androidHarness.dependencies);
    await assert.rejects(
      () => androidService.saveEncryptedBackupFile(createTestState(), 'CorrectHorse123'),
      /user-selected.*not available/i
    );
    assert.equal(androidHarness.calls.length, 0);

    const webHarness = createHarness();
    webHarness.dependencies.platform = 'web';
    const webService = createBackupFileService(webHarness.dependencies);
    const webResult = await webService.saveEncryptedBackupFile(createTestState(), 'CorrectHorse123');
    assert.equal(webResult.verifiedPortableCopy, false);
    assert.match(webResult.uri, /^file:\/\/\/documents\//);
  });

  it('deletes a validated picker cache copy after reading it into bounded memory', async () => {
    const asset = {
      name: 'family.relateai-backup',
      uri: 'file:///cache/picked-family.relateai-backup',
      size: 18,
      mimeType: 'application/json'
    };
    const harness = createHarness(asset);
    const service = createBackupFileService(harness.dependencies);

    const result = await service.pickEncryptedBackupFile();

    assert.equal(result?.raw, '{"encrypted":true}');
    assert.equal(result?.temporaryFileRemoved, true);
    const readIndex = harness.calls.indexOf(`read:${asset.uri}`);
    const deleteIndex = harness.calls.indexOf(`delete:${asset.uri}`);
    assert.ok(readIndex >= 0);
    assert.ok(deleteIndex > readIndex);
  });

  it('rejects oversized picker files before reading and still removes the cache copy', async () => {
    const asset = {
      name: 'oversized.relateai-backup',
      uri: 'file:///cache/oversized.relateai-backup',
      size: MAX_BACKUP_RAW_BYTES + 1,
      mimeType: 'application/octet-stream'
    };
    const harness = createHarness(asset);
    const service = createBackupFileService(harness.dependencies);

    await assert.rejects(() => service.pickEncryptedBackupFile(), /no larger/i);
    assert.equal(harness.calls.includes(`read:${asset.uri}`), false);
    assert.equal(harness.calls.includes(`delete:${asset.uri}`), true);
  });

  it('requires a verified file size and supported advisory file type before reading', async () => {
    const missingSizeAsset = {
      name: 'family.relateai-backup',
      uri: 'file:///cache/family.relateai-backup',
      mimeType: 'application/json'
    };
    const sizeHarness = createHarness(missingSizeAsset);
    const sizeService = createBackupFileService(sizeHarness.dependencies);

    const picked = await sizeService.pickEncryptedBackupFile();
    assert.ok(picked);
    assert.ok(sizeHarness.calls.includes(`info:${missingSizeAsset.uri}`));
    assert.ok(sizeHarness.calls.includes(`read:${missingSizeAsset.uri}`));

    const unsupportedAsset = {
      name: 'photo.png',
      uri: 'file:///cache/photo.png',
      size: 18,
      mimeType: 'image/png'
    };
    const typeHarness = createHarness(unsupportedAsset);
    const typeService = createBackupFileService(typeHarness.dependencies);
    await assert.rejects(() => typeService.pickEncryptedBackupFile(), /choose a .*backup file/i);
    assert.equal(typeHarness.calls.includes(`read:${unsupportedAsset.uri}`), false);
    assert.equal(typeHarness.calls.includes(`delete:${unsupportedAsset.uri}`), true);
  });

  it('never deletes a user-owned source URI outside the app cache', async () => {
    const asset = {
      name: 'family.json',
      uri: 'file:///user-documents/family.json',
      size: 18,
      mimeType: 'application/json'
    };
    const harness = createHarness(asset);
    const service = createBackupFileService(harness.dependencies);

    const result = await service.pickEncryptedBackupFile();

    assert.equal(result?.temporaryFileRemoved, false);
    assert.equal(
      harness.calls.some(call => call.startsWith('delete:')),
      false
    );
  });

  it('cleanup API is restricted to generated RelateAI cache files', async () => {
    const harness = createHarness();
    const service = createBackupFileService(harness.dependencies);

    assert.equal(
      await service.cleanupTemporaryBackupFile('file:///documents/relateai-backup-a.relateai-backup'),
      false
    );
    assert.equal(await service.cleanupTemporaryBackupFile('file:///cache/unrelated.json'), false);
    assert.equal(
      await service.cleanupTemporaryBackupFile('file:///cache/../documents/relateai-backup-a.relateai-backup'),
      false
    );
    assert.equal(await service.cleanupTemporaryBackupFile('file:///cache/relateai-backup-a.relateai-backup'), true);
    assert.deepEqual(
      harness.calls.filter(call => call.startsWith('delete:')),
      ['delete:file:///cache/relateai-backup-a.relateai-backup']
    );
  });
});
