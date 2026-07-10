import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { MAX_EVENT_IMPORT_BYTES } from '../domain/eventImport';
import {
  createEventImportFileService,
  type EventImportDocumentAsset,
  type EventImportFileSystem
} from './eventImportFiles';

const fixture = (asset: EventImportDocumentAsset, raw = 'name,date\nAsha,2026-08-01') => {
  const deleted: string[] = [];
  let reads = 0;
  const fileSystem: EventImportFileSystem = {
    cacheDirectory: 'file:///cache/',
    utf8Encoding: 'utf8',
    readAsStringAsync: async () => {
      reads += 1;
      return raw;
    },
    getInfoAsync: async () => ({ exists: true, size: raw.length }),
    deleteAsync: async uri => {
      deleted.push(uri);
    }
  };
  const service = createEventImportFileService(
    { getDocumentAsync: async () => ({ canceled: false, assets: [asset] }) },
    fileSystem
  );
  return { service, deleted, reads: () => reads };
};

describe('bounded event import file transport', () => {
  it('reads a verified UTF-8 CSV cache copy and removes the temporary artifact', async () => {
    const uri = 'file:///cache/events.csv';
    const test = fixture({ name: 'events.csv', uri, size: 28, mimeType: 'text/csv' });
    const result = await test.service.pickEventImportFile();
    assert.equal(result?.raw, 'name,date\nAsha,2026-08-01');
    assert.equal(result?.temporaryFileRemoved, true);
    assert.deepEqual(test.deleted, [uri]);
  });

  it('rejects oversized assets before reading and cleans owned cache copies', async () => {
    const uri = 'file:///cache/huge.vcf';
    const test = fixture({ name: 'huge.vcf', uri, size: MAX_EVENT_IMPORT_BYTES + 1 });
    await assert.rejects(test.service.pickEventImportFile(), /no larger/i);
    assert.equal(test.reads(), 0);
    assert.deepEqual(test.deleted, [uri]);
  });

  it('requires a supported extension, advisory MIME, verified size, and valid UTF-8', async () => {
    const wrongType = fixture({ name: 'events.txt', uri: 'file:///cache/events.txt', size: 10 });
    await assert.rejects(wrongType.service.pickEventImportFile(), /\.csv/);

    const wrongMime = fixture({ name: 'events.csv', uri: 'file:///cache/events.csv', size: 10, mimeType: 'image/png' });
    await assert.rejects(wrongMime.service.pickEventImportFile(), /file type/i);

    const unknownSize = fixture({ name: 'events.csv', uri: 'file:///cache/events.csv' });
    const result = await unknownSize.service.pickEventImportFile();
    assert.ok(result);

    const invalidUtf8 = fixture(
      { name: 'events.vcf', uri: 'file:///cache/events.vcf', size: 8 },
      'BEGIN:\uFFFD'
    );
    await assert.rejects(invalidUtf8.service.pickEventImportFile(), /UTF-8/);
  });

  it('never removes a user-owned source URI outside the app cache', async () => {
    const uri = 'content://documents/events.csv';
    const test = fixture({ name: 'events.csv', uri, size: 28 });
    const result = await test.service.pickEventImportFile();
    assert.equal(result?.temporaryFileRemoved, false);
    assert.deepEqual(test.deleted, []);
  });
});
