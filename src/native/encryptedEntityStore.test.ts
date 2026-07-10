import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createExpoEntityStoreFileAdapter, type ExpoEntityStoreFileSystem } from './encryptedEntityStore';

describe('Expo encrypted entity-store file adapter', () => {
  it('confines encrypted artifacts to one app-owned directory and never creates plaintext sidecars', async () => {
    const values = new Map<string, string>();
    const calls: string[] = [];
    const fileSystem: ExpoEntityStoreFileSystem = {
      documentDirectory: 'file:///documents/',
      utf8Encoding: 'utf8',
      async getInfoAsync(uri) {
        calls.push(`info:${uri}`);
        if (uri.endsWith('/relateai-entity-store-v1/')) {
          return { exists: values.has(uri), isDirectory: values.has(uri) };
        }
        return { exists: values.has(uri), isDirectory: false };
      },
      async makeDirectoryAsync(uri) {
        calls.push(`mkdir:${uri}`);
        values.set(uri, '<directory>');
      },
      async readDirectoryAsync(uri) {
        calls.push(`list:${uri}`);
        return [...values.keys()].filter(key => key.startsWith(uri) && key !== uri).map(key => key.slice(uri.length));
      },
      async readAsStringAsync(uri) {
        calls.push(`read:${uri}`);
        return values.get(uri)!;
      },
      async writeAsStringAsync(uri, contents) {
        calls.push(`write:${uri}`);
        values.set(uri, contents);
      },
      async deleteAsync(uri) {
        calls.push(`delete:${uri}`);
        values.delete(uri);
      }
    };
    const adapter = createExpoEntityStoreFileAdapter(fileSystem);

    await adapter.write('record-opaque.enc', '{"ciphertext":"opaque"}');
    assert.equal(await adapter.read('record-opaque.enc'), '{"ciphertext":"opaque"}');
    assert.deepEqual(await adapter.list(), ['record-opaque.enc']);
    await adapter.remove('record-opaque.enc');

    assert.ok(calls.every(call => !call.includes('.tmp') && !call.includes('plaintext')));
    assert.ok(
      calls
        .filter(call => /write:|read:|delete:/.test(call))
        .every(call => call.includes('file:///documents/relateai-entity-store-v1/'))
    );
    await assert.rejects(() => adapter.write('../escape', 'x'), /file name is invalid/i);
  });
});
