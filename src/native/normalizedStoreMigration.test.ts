import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { AppState } from '../domain/types';
import { RELATE_STATE_KEY, saveState, type KeyValueStore } from '../state/persistence';
import { createTestState } from '../test/testState';
import {
  EncryptedTransactionalEntityStore,
  type EncryptedStoreFileAdapter,
  type ProtectedRepositoryKeyStore
} from './encryptedEntityStoreCore';
import { DualReadSingleWriteEntityRepository, NormalizedStoreMigrationCoordinator } from './normalizedStoreMigration';

class TrackedKeyValueStore implements KeyValueStore {
  readonly values = new Map<string, string>();
  writes = 0;
  removes = 0;

  async getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  async setItem(key: string, value: string) {
    this.writes += 1;
    this.values.set(key, value);
  }

  async removeItem(key: string) {
    this.removes += 1;
    this.values.delete(key);
  }

  resetCounts() {
    this.writes = 0;
    this.removes = 0;
  }
}

class ProtectedStore extends TrackedKeyValueStore implements ProtectedRepositoryKeyStore {
  async getProtectionStatus() {
    return {
      available: true,
      protection: 'platform-protected' as const,
      legacyPlaintext: 'none' as const
    };
  }
}

class MemoryFiles implements EncryptedStoreFileAdapter {
  readonly values = new Map<string, string>();

  async read(name: string) {
    return this.values.get(name) ?? null;
  }

  async write(name: string, contents: string) {
    this.values.set(name, contents);
  }

  async remove(name: string) {
    this.values.delete(name);
  }

  async list() {
    return [...this.values.keys()];
  }
}

const createRepositoryHarness = (protectedStore: ProtectedStore) => {
  const files = new MemoryFiles();
  let sequence = 0;
  const repository = new EncryptedTransactionalEntityStore({
    files,
    protectedStore,
    now: () => '2026-07-10T10:00:00.000Z',
    transactionId: () => `migration-${String(++sequence).padStart(8, '0')}`
  });
  return { files, repository };
};

describe('normalized SecureStore to encrypted repository migration', () => {
  it('commits a verified migration when the production facade performs its startup load', async () => {
    const legacy = new TrackedKeyValueStore();
    const protectedStore = new ProtectedStore();
    const state = createTestState();
    await saveState(legacy, state);
    legacy.resetCounts();
    const { repository } = createRepositoryHarness(protectedStore);
    const migration = new NormalizedStoreMigrationCoordinator({
      legacyStore: legacy,
      repository,
      protectedStore,
      now: () => '2026-07-10T10:00:00.000Z'
    });
    const facade = new DualReadSingleWriteEntityRepository(migration, repository);

    const loaded = await facade.loadState();

    assert.equal(loaded?.contacts.length, state.contacts.length);
    assert.equal((await migration.checkpoint())?.phase, 'committed');
    assert.equal((await repository.inspect()).status, 'Ready');
    assert.equal(legacy.writes, 0);
    assert.equal(legacy.removes, 0);
  });

  it('dual-reads the untouched source after interruption, then resumes verified single-write migration', async () => {
    const legacy = new TrackedKeyValueStore();
    const protectedStore = new ProtectedStore();
    const state = createTestState();
    await saveState(legacy, state);
    assert.ok(legacy.values.has(RELATE_STATE_KEY));
    legacy.resetCounts();
    const sourceSnapshot = new Map(legacy.values);
    const { repository } = createRepositoryHarness(protectedStore);
    let interrupt = true;
    const interrupted = new NormalizedStoreMigrationCoordinator({
      legacyStore: legacy,
      repository,
      protectedStore,
      now: () => '2026-07-10T10:00:00.000Z',
      faults: {
        afterCopy() {
          if (interrupt) {
            interrupt = false;
            throw new Error('simulated migration interruption');
          }
        }
      }
    });

    await assert.rejects(() => interrupted.migrate(), /interruption/i);
    assert.equal((await interrupted.checkpoint())?.phase, 'copied');
    const dualRead = await interrupted.loadState();
    assert.equal(dualRead?.contacts.length, state.contacts.length);
    assert.deepEqual(legacy.values, sourceSnapshot);
    assert.equal(legacy.writes, 0);
    assert.equal(legacy.removes, 0);

    const resumed = new NormalizedStoreMigrationCoordinator({
      legacyStore: legacy,
      repository,
      protectedStore,
      now: () => '2026-07-10T10:01:00.000Z'
    });
    const result = await resumed.migrate();
    assert.equal(result.status, 'committed');
    assert.equal((await resumed.checkpoint())?.phase, 'committed');
    const health = await repository.inspect();
    assert.equal(health.aggregateCounts.contacts, state.contacts.length);
    assert.equal(health.stateChecksum, result.status === 'committed' ? result.checkpoint.sourceChecksum : undefined);
    assert.deepEqual(legacy.values, sourceSnapshot);

    const facade = new DualReadSingleWriteEntityRepository(resumed, repository);
    const migrated = (await facade.loadState())!;
    const next: AppState = { ...migrated, searchQuery: 'repository only' };
    await facade.replaceState(next);
    assert.equal((await facade.loadState())?.searchQuery, 'repository only');
    assert.equal(legacy.writes, 0);
    assert.equal(legacy.removes, 0);
  });

  it('does not mutate an unreadable normalized source while reporting migration failure', async () => {
    const legacy = new TrackedKeyValueStore();
    await legacy.setItem(RELATE_STATE_KEY, '{invalid');
    legacy.resetCounts();
    const protectedStore = new ProtectedStore();
    const { repository } = createRepositoryHarness(protectedStore);
    const migration = new NormalizedStoreMigrationCoordinator({ legacyStore: legacy, repository, protectedStore });

    await assert.rejects(() => migration.migrate(), /left unchanged/i);
    assert.equal(legacy.values.get(RELATE_STATE_KEY), '{invalid');
    assert.equal(legacy.writes, 0);
    assert.equal(legacy.removes, 0);
    assert.equal((await repository.inspect()).status, 'Missing');
  });
});
