import assert from 'node:assert/strict';
import { performance } from 'node:perf_hooks';
import { describe, it } from 'node:test';
import {
  createExpoCryptoProvider,
  createWebCryptoProvider,
  resolveCrossPlatformCryptoProvider,
  type ExpoCryptoBridge
} from '../crypto/crossPlatformCrypto';
import type { AppState } from '../domain/types';
import { deserializeState, serializeState } from '../state/persistence';
import { createTestState } from '../test/testState';
import {
  ENTITY_STORE_CHECKPOINT_FILES,
  ENTITY_STORE_MASTER_KEY,
  EncryptedTransactionalEntityStore,
  EntityStoreError,
  type EncryptedStoreFileAdapter,
  type ProtectedRepositoryKeyStore
} from './encryptedEntityStoreCore';

class MemoryFiles implements EncryptedStoreFileAdapter {
  readonly values = new Map<string, string>();
  reads = 0;
  writes = 0;
  removes = 0;
  writtenNames: string[] = [];
  tearNextCheckpoint = false;

  async read(name: string) {
    this.reads += 1;
    return this.values.get(name) ?? null;
  }

  async write(name: string, contents: string) {
    this.writes += 1;
    this.writtenNames.push(name);
    if (this.tearNextCheckpoint && ENTITY_STORE_CHECKPOINT_FILES.includes(name as never)) {
      this.tearNextCheckpoint = false;
      this.values.set(name, '{torn');
      throw new Error('simulated checkpoint interruption');
    }
    this.values.set(name, contents);
  }

  async remove(name: string) {
    this.removes += 1;
    this.values.delete(name);
  }

  async list() {
    return [...this.values.keys()];
  }

  resetIo() {
    this.reads = 0;
    this.writes = 0;
    this.removes = 0;
    this.writtenNames = [];
  }
}

class MemoryProtectedStore implements ProtectedRepositoryKeyStore {
  readonly values = new Map<string, string>();
  available = true;
  writes = 0;

  async getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  async setItem(key: string, value: string) {
    this.writes += 1;
    this.values.set(key, value);
  }

  async removeItem(key: string) {
    this.values.delete(key);
  }

  async getProtectionStatus() {
    return {
      available: this.available,
      protection: this.available ? ('platform-protected' as const) : ('unavailable' as const),
      legacyPlaintext: 'none' as const
    };
  }
}

const createHarness = () => {
  const files = new MemoryFiles();
  const protectedStore = new MemoryProtectedStore();
  let transactionSequence = 0;
  const makeStore = (options: Partial<ConstructorParameters<typeof EncryptedTransactionalEntityStore>[0]> = {}) =>
    new EncryptedTransactionalEntityStore({
      files,
      protectedStore,
      now: () => '2026-07-10T10:00:00.000Z',
      transactionId: () => `transaction-${String(++transactionSequence).padStart(8, '0')}`,
      ...options
    });
  return { files, protectedStore, makeStore };
};

const stateWithManyActivities = (count: number): AppState => {
  const state = createTestState();
  return {
    ...state,
    activity: Array.from({ length: count }, (_, index) => ({
      id: `activity-scale-${index}`,
      type: 'Setup' as const,
      title: `Operational event ${index}`,
      detail: `Redacted operational detail ${index}`,
      severity: 'Info' as const,
      createdAt: new Date(Date.UTC(2026, 0, 1, 0, index % 60, 0)).toISOString()
    }))
  };
};

const canonicalState = (state: AppState): AppState => deserializeState(serializeState(state));

describe('encrypted transactional entity store', () => {
  it('keeps the master key protected and supports indexed cursor pagination without plaintext files', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    const state = createTestState();

    const health = await repository.replaceState(state);
    const first = await repository.query('events', {
      where: [{ index: 'contactId', equalTo: state.events[0].contactId }],
      orderBy: 'date',
      direction: 'asc',
      limit: 1
    });
    const second = first.nextCursor
      ? await repository.query('events', {
          where: [{ index: 'contactId', equalTo: state.events[0].contactId }],
          orderBy: 'date',
          direction: 'asc',
          limit: 1,
          cursor: first.nextCursor
        })
      : undefined;
    const restored = await repository.loadState();

    assert.equal(health.aggregateCounts.contacts, state.contacts.length);
    assert.equal(first.items.length, 1);
    assert.equal(first.matchedCount >= first.items.length, true);
    assert.equal(second === undefined || second.items[0]?.id !== first.items[0].id, true);
    assert.deepEqual(restored, canonicalState(state));
    assert.ok(harness.protectedStore.values.has(ENTITY_STORE_MASTER_KEY));
    assert.equal(harness.protectedStore.writes, 1);
    const atRest = [...harness.files.values.values()].join('\n');
    assert.doesNotMatch(atRest, new RegExp(state.contacts[0].name));
    assert.doesNotMatch(atRest, new RegExp(state.messages[0].body.slice(0, 24)));
    assert.equal(atRest.includes(harness.protectedStore.values.get(ENTITY_STORE_MASTER_KEY)!), false);
  });

  it('destroys every app-owned generation and protected key only through the explicit recovery API', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    await repository.replaceState(createTestState());
    assert.ok(harness.files.values.size > 0);
    assert.equal(harness.protectedStore.values.has(ENTITY_STORE_MASTER_KEY), true);

    await repository.destroyAllData();

    assert.equal(harness.files.values.size, 0);
    assert.equal(harness.protectedStore.values.has(ENTITY_STORE_MASTER_KEY), false);
    assert.equal(await repository.loadState(), undefined);
  });

  it('writes through the injected Expo crypto path and reopens with Web Crypto', async () => {
    const harness = createHarness();
    const web = createWebCryptoProvider(globalThis.crypto);
    const calls = { random: 0, digest: 0, importKey: 0 };
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
    const native = resolveCrossPlatformCryptoProvider({
      runtime: 'native',
      expoProvider: createExpoCryptoProvider(async () => bridge)
    });
    const state = createTestState();

    await harness.makeStore({ cryptoProvider: native }).replaceState(state);
    const restored = await harness.makeStore({ cryptoProvider: web }).loadState();

    assert.equal(native.kind, 'expo-crypto');
    assert.deepEqual(restored, canonicalState(state));
    assert.ok(calls.random > 1);
    assert.ok(calls.digest > 1);
    assert.equal(calls.importKey, 1);
  });

  it('fails closed without writing a key or private files when native crypto is unavailable', async () => {
    const harness = createHarness();
    const unavailableNative = resolveCrossPlatformCryptoProvider({
      runtime: 'native',
      expoProvider: createExpoCryptoProvider(async () => {
        throw new Error('simulated missing native crypto module');
      })
    });

    await assert.rejects(
      () => harness.makeStore({ cryptoProvider: unavailableNative }).replaceState(createTestState()),
      (error: unknown) => error instanceof EntityStoreError && error.code === 'crypto-unavailable'
    );
    assert.equal(harness.protectedStore.values.has(ENTITY_STORE_MASTER_KEY), false);
    assert.equal(harness.files.values.size, 0);
  });

  it('writes only a dirty entity plus manifest and checkpoint while preserving all other records', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    const state = stateWithManyActivities(300);
    await repository.replaceState(state);
    harness.files.resetIo();
    const changed: AppState = {
      ...state,
      contacts: state.contacts.map((contact, index) =>
        index === 0 ? { ...contact, notesSummary: 'Updated exactly once.' } : contact
      )
    };

    await repository.writeDirty({
      state: changed,
      collections: { contacts: [changed.contacts[0].id] }
    });
    const restored = await repository.loadState();

    assert.equal(harness.files.writtenNames.filter(name => name.startsWith('record-')).length, 1);
    assert.equal(harness.files.writtenNames.filter(name => name.startsWith('manifest-')).length, 1);
    assert.equal(harness.files.writtenNames.filter(name => name.startsWith('checkpoint-')).length, 1);
    assert.equal(restored?.contacts[0].notesSummary, 'Updated exactly once.');
    assert.equal(restored?.activity.length, state.activity.length);
  });

  it('rejects undeclared mutations instead of silently persisting a partial state', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    const state = createTestState();
    await repository.replaceState(state);
    const changed = {
      ...state,
      contacts: state.contacts.map((contact, index) =>
        index === 0 ? { ...contact, notesSummary: 'Undeclared.' } : contact
      )
    };

    await assert.rejects(
      () => repository.writeDirty({ state: changed, collections: { events: [] } }),
      /not declared dirty/i
    );
    assert.deepEqual(await repository.loadState(), canonicalState(state));
  });

  it('keeps the previous generation readable when a checkpoint write is torn', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    const state = createTestState();
    await repository.replaceState(state);
    const changed: AppState = { ...state, searchQuery: 'new query' };
    harness.files.tearNextCheckpoint = true;

    await assert.rejects(() => repository.writeDirty({ state: changed, shell: true }), /interruption/i);
    const reopened = harness.makeStore();

    assert.deepEqual(await reopened.loadState(), canonicalState(state));
    assert.equal((await reopened.inspect()).generation, 1);
  });

  it('rolls back to the retained generation when a committed record becomes corrupt', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    const state = createTestState();
    await repository.replaceState(state);
    harness.files.resetIo();
    const changed: AppState = {
      ...state,
      contacts: state.contacts.map((contact, index) =>
        index === 0 ? { ...contact, notesSummary: 'Second generation.' } : contact
      )
    };
    await repository.writeDirty({ state: changed, collections: { contacts: [changed.contacts[0].id] } });
    const changedRecord = harness.files.writtenNames.find(name => name.startsWith('record-'));
    assert.ok(changedRecord);
    harness.files.values.set(changedRecord, '{corrupt');

    const reopened = harness.makeStore();
    const restored = await reopened.loadState();
    const health = await reopened.inspect();

    assert.equal(restored?.contacts[0].notesSummary, state.contacts[0].notesSummary);
    assert.equal(health.recoveredFromRollback, true);
    assert.equal(health.stateChecksum?.length, 64);
    assert.equal(health.manifestChecksum?.length, 64);
  });

  it('keeps checkpoint-slot rollback roots when a later dirty commit reuses a damaged record', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    const first = createTestState();
    await repository.replaceState(first);
    harness.files.resetIo();
    const second: AppState = {
      ...first,
      contacts: first.contacts.map((contact, index) =>
        index === 0 ? { ...contact, notesSummary: 'Second generation.' } : contact
      )
    };
    await repository.writeDirty({ state: second, collections: { contacts: [second.contacts[0].id] } });
    const damaged = harness.files.writtenNames.find(name => name.startsWith('record-'));
    assert.ok(damaged);
    harness.files.values.set(damaged, '{damaged');

    const third: AppState = { ...second, searchQuery: 'third generation shell' };
    await repository.writeDirty({ state: third, shell: true });
    const reopened = harness.makeStore();
    const recovered = await reopened.loadState();

    assert.equal(recovered?.contacts[0].notesSummary, canonicalState(first).contacts[0].notesSummary);
    assert.equal(recovered?.searchQuery, first.searchQuery);
    assert.equal((await reopened.inspect()).recoveredFromRollback, true);
  });

  it('rotates both rollback roots after a verified destructive replacement', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    await repository.replaceState(createTestState());
    const cleared = createTestState();
    cleared.contacts = [];
    cleared.events = [];
    cleared.memories = [];
    cleared.gifts = [];
    cleared.messages = [];
    cleared.activity = [];
    cleared.backups = [];
    cleared.setupChecks = [];
    cleared.reminderPlans = [];
    const beforePrune = await repository.replaceState(cleared);
    const retainedRecordFiles = [...harness.files.values.keys()].filter(name => name.startsWith('record-')).length;

    const pruned = await repository.pruneRollbackGenerations();
    const remainingRecordFiles = [...harness.files.values.keys()].filter(name => name.startsWith('record-')).length;

    assert.ok(retainedRecordFiles > beforePrune.recordFileCount);
    assert.equal(remainingRecordFiles, pruned.recordFileCount);
    assert.deepEqual(await repository.loadState(), canonicalState(cleared));
  });

  it('runs ordered schema migrations transactionally and preserves the old schema on interruption', async () => {
    const harness = createHarness();
    const versionOne = harness.makeStore({ targetSchemaVersion: 1 });
    const state = createTestState();
    await versionOne.replaceState(state);
    const migration = {
      fromVersion: 1,
      toVersion: 2,
      migrate: (input: AppState): AppState => ({
        ...input,
        contacts: input.contacts.map((contact, index) =>
          index === 0 ? { ...contact, notesSummary: `${contact.notesSummary} Migrated.` } : contact
        )
      })
    };
    harness.files.tearNextCheckpoint = true;
    const interrupted = harness.makeStore({ targetSchemaVersion: 2, migrations: [migration] });

    await assert.rejects(() => interrupted.loadState(), /interruption/i);
    const oldReader = harness.makeStore({ targetSchemaVersion: 1 });
    assert.equal((await oldReader.inspect()).schemaVersion, 1);

    const resumed = harness.makeStore({ targetSchemaVersion: 2, migrations: [migration] });
    const migrated = await resumed.loadState();
    assert.match(migrated?.contacts[0].notesSummary ?? '', /Migrated\.$/);
    assert.equal((await resumed.inspect()).schemaVersion, 2);
  });

  it('archives bounded operational history without removing relationship history', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    const state = createTestState();
    const oldMessage = {
      ...state.messages[0],
      id: 'message-retention-old',
      status: 'Sent' as const,
      sentAt: '2025-01-01T00:00:00.000Z'
    };
    const retained: AppState = {
      ...state,
      messages: [oldMessage, ...state.messages],
      activity: [
        {
          id: 'activity-new',
          type: 'Setup',
          title: 'New',
          detail: 'New operational record.',
          severity: 'Info',
          createdAt: '2026-07-09T00:00:00.000Z'
        },
        {
          id: 'activity-old',
          type: 'Setup',
          title: 'Old',
          detail: 'Old operational record.',
          severity: 'Info',
          createdAt: '2025-01-01T00:00:00.000Z'
        }
      ]
    };
    await repository.replaceState(retained);

    const report = await repository.applyRetentionPolicy(
      {
        activity: { activeDays: 90, maximumActive: 1 },
        terminalMessages: { archiveAfterDays: 180 }
      },
      '2026-07-10T00:00:00.000Z'
    );
    const activeActivity = await repository.query('activity', { limit: 20 });
    const allActivity = await repository.query('activity', { limit: 20, includeArchived: true });
    const activeMessages = await repository.query('messages', { limit: 20 });
    const restored = await repository.loadState();

    assert.equal(report.archivedActivity, 1);
    assert.equal(report.archivedMessages, 1);
    assert.deepEqual(
      activeActivity.items.map(item => item.id),
      ['activity-new']
    );
    assert.equal(allActivity.items.length, 2);
    assert.equal(
      activeMessages.items.some(message => message.id === oldMessage.id),
      false
    );
    assert.equal(
      restored?.messages.some(message => message.id === oldMessage.id),
      true
    );
    assert.equal(report.retainedRelationshipHistory, retained.messages.length);
  });

  it('meets bounded I/O budgets for paged reads and one-entity writes at scale', async () => {
    const harness = createHarness();
    const repository = harness.makeStore();
    const state = stateWithManyActivities(600);
    await repository.replaceState(state);
    const reopened = harness.makeStore();
    harness.files.resetIo();
    const queryStarted = performance.now();
    const page = await reopened.query('activity', {
      orderBy: 'createdAt',
      direction: 'desc',
      limit: 25
    });
    const queryDuration = performance.now() - queryStarted;

    assert.equal(page.items.length, 25);
    assert.ok(harness.files.reads <= 29, `paged query used ${harness.files.reads} file reads`);
    assert.ok(queryDuration < 1500, `paged query took ${queryDuration.toFixed(1)}ms`);

    harness.files.resetIo();
    const changed: AppState = {
      ...state,
      activity: state.activity.map((item, index) =>
        index === 0 ? { ...item, detail: 'One dirty operational detail.' } : item
      )
    };
    const writeStarted = performance.now();
    await reopened.writeDirty({ state: changed, collections: { activity: [changed.activity[0].id] } });
    const writeDuration = performance.now() - writeStarted;
    assert.equal(harness.files.writtenNames.filter(name => name.startsWith('record-')).length, 1);
    assert.ok(harness.files.writes <= 3, `dirty write used ${harness.files.writes} file writes`);
    assert.ok(writeDuration < 1500, `dirty write took ${writeDuration.toFixed(1)}ms`);
    assert.ok(page.nextCursor);
    await assert.rejects(
      () =>
        reopened.query('activity', {
          orderBy: 'createdAt',
          direction: 'desc',
          limit: 25,
          cursor: page.nextCursor
        }),
      /older generation/i
    );
  });

  it('fails closed before creating a master key when protected storage is unavailable', async () => {
    const harness = createHarness();
    harness.protectedStore.available = false;
    const repository = harness.makeStore();

    await assert.rejects(
      () => repository.replaceState(createTestState()),
      (error: unknown) => error instanceof EntityStoreError && error.code === 'protected-storage-unavailable'
    );
    assert.equal(harness.files.values.size, 0);
    assert.equal(harness.protectedStore.values.size, 0);
  });

  it('never creates a replacement key over an existing encrypted repository', async () => {
    const harness = createHarness();
    await harness.makeStore().replaceState(createTestState());
    harness.protectedStore.values.delete(ENTITY_STORE_MASTER_KEY);
    harness.protectedStore.writes = 0;

    await assert.rejects(
      () => harness.makeStore().loadState(),
      (error: unknown) => error instanceof EntityStoreError && error.code === 'master-key-invalid'
    );
    assert.equal(harness.protectedStore.writes, 0);
    assert.equal(harness.protectedStore.values.has(ENTITY_STORE_MASTER_KEY), false);
  });
});
