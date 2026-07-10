import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  entityCollectionNames,
  type DirtyStateWrite,
  type EntityRepository,
  type RepositoryInspection
} from '../domain/entityRepository';
import type { AppState } from '../domain/types';
import { createTestState } from '../test/testState';
import {
  computeDirtyStateWrite,
  createEntityRepositoryPersistenceAdapter,
  hasDirtyStateWrite,
  loadEntityRepositoryState,
  repositoryInspectionToStorageHealth
} from './entityRepositoryPersistence';

const counts = () =>
  Object.fromEntries(entityCollectionNames.map(name => [name, 0])) as RepositoryInspection['aggregateCounts'];

const inspection = (status: RepositoryInspection['status'] = 'Ready'): RepositoryInspection => ({
  status,
  ...(status === 'Ready'
    ? {
        schemaVersion: 1,
        generation: 4,
        stateChecksum: 'state-checksum',
        manifestChecksum: 'manifest-checksum',
        savedAt: '2026-07-10T09:00:00.000Z'
      }
    : {}),
  aggregateCounts: counts(),
  activeCounts: counts(),
  archivedCounts: counts(),
  recordFileCount: status === 'Ready' ? 14 : 0,
  payloadBytes: status === 'Ready' ? 8_192 : 0,
  largestRecordBytes: status === 'Ready' ? 1_024 : 0,
  recoveredFromRollback: false
});

type FakeRepository = EntityRepository & {
  current?: AppState;
  health: RepositoryInspection;
  replacements: AppState[];
  dirtyWrites: DirtyStateWrite[];
  inspectCalls: number;
};

const fakeRepository = (state?: AppState, health = inspection()): FakeRepository => {
  const repository = {
    current: state,
    health,
    replacements: [] as AppState[],
    dirtyWrites: [] as DirtyStateWrite[],
    inspectCalls: 0,
    async loadState() {
      return this.current;
    },
    async replaceState(next: AppState) {
      this.replacements.push(next);
      this.current = next;
      this.health = inspection();
      return this.health;
    },
    async writeDirty(write: DirtyStateWrite) {
      this.dirtyWrites.push(write);
      this.current = write.state;
      return this.health;
    },
    async query() {
      return { items: [], matchedCount: 0 };
    },
    async setArchiveState() {
      return this.health;
    },
    async applyRetentionPolicy() {
      return {
        archivedActivity: 0,
        archivedMessages: 0,
        purgedActivity: 0,
        retainedRelationshipHistory: 0,
        appliedAt: '2026-07-10T10:00:00.000Z'
      };
    },
    async pruneRollbackGenerations() {
      return this.health;
    },
    async inspect() {
      this.inspectCalls += 1;
      return this.health;
    }
  };
  return repository as unknown as FakeRepository;
};

describe('entity repository persistence integration', () => {
  it('maps verified repository inspection into runtime storage health during hydration', async () => {
    const repository = fakeRepository(createTestState());
    repository.health = { ...repository.health, recoveredFromRollback: true };

    const result = await loadEntityRepositoryState(repository, () => '2026-07-10T10:00:00.000Z');

    assert.equal(result.status, 'loaded');
    if (result.status !== 'loaded') return;
    assert.deepEqual(result.state.persistence.storageHealth, {
      status: 'Ready',
      storageFormat: 'Encrypted entity repository',
      payloadBytes: 8_192,
      entryCount: 14,
      chunkCount: 0,
      largestEntryBytes: 1_024,
      savedAt: '2026-07-10T09:00:00.000Z',
      envelopeVersion: 1,
      lastVerifiedAt: '2026-07-10T10:00:00.000Z',
      issue: 'Recovered the previous verified encrypted repository generation.'
    });
  });

  it('declares exact changed, deleted, reordered, singleton, and shell identities', () => {
    const previous = createTestState();
    const changedContact = { ...previous.contacts[0], notesSummary: 'Changed once.' };
    const insertedContact = { ...previous.contacts[1], id: 'contact-new-exact-dirty-id' };
    const deletedMessageId = previous.messages[0].id;
    const next: AppState = {
      ...previous,
      searchQuery: 'family',
      contacts: [previous.contacts[1], changedContact, ...previous.contacts.slice(2), insertedContact],
      messages: previous.messages.slice(1),
      settings: {
        ...previous.settings,
        quietHours: { start: '21:00', end: '07:00' }
      }
    };

    const write = computeDirtyStateWrite(previous, next);

    assert.deepEqual(write.collections, {
      contacts: [changedContact.id, insertedContact.id],
      messages: [deletedMessageId]
    });
    assert.deepEqual(write.singletons, ['settings']);
    assert.equal(write.shell, true);
    assert.equal(hasDirtyStateWrite(write), true);

    const reorderOnly = computeDirtyStateWrite(previous, {
      ...previous,
      events: [...previous.events].reverse()
    });
    assert.deepEqual(reorderOnly.collections, { events: [] });
    assert.equal(hasDirtyStateWrite(reorderOnly), true);
  });

  it('single-writes normal commits through writeDirty and uses replacement only to bootstrap a missing repository', async () => {
    const previous = createTestState();
    const next = { ...previous, searchQuery: 'next' };
    const repository = fakeRepository(previous);
    const adapter = createEntityRepositoryPersistenceAdapter({
      repository,
      nowIso: () => '2026-07-10T10:00:00.000Z'
    });

    await adapter.save(next, previous);

    assert.equal(repository.replacements.length, 0);
    assert.equal(repository.dirtyWrites.length, 1);
    assert.equal(repository.dirtyWrites[0].shell, true);
    const third = { ...next, searchQuery: 'third' };
    await adapter.save(third, next);
    await adapter.inspect();
    assert.equal(repository.dirtyWrites.length, 2);
    assert.equal(repository.inspectCalls, 1);

    const missing = fakeRepository(undefined, inspection('Missing'));
    const bootstrap = createEntityRepositoryPersistenceAdapter({
      repository: missing,
      nowIso: () => '2026-07-10T10:00:00.000Z'
    });
    await bootstrap.save(next, previous);

    assert.deepEqual(missing.replacements, [next]);
    assert.equal(missing.dirtyWrites.length, 0);
  });

  it('does not write an unchanged verified state', async () => {
    const state = createTestState();
    const repository = fakeRepository(state);
    const adapter = createEntityRepositoryPersistenceAdapter({
      repository,
      nowIso: () => '2026-07-10T10:00:00.000Z'
    });

    await adapter.save(structuredClone(state), state);

    assert.equal(repository.replacements.length, 0);
    assert.equal(repository.dirtyWrites.length, 0);
  });

  it('fails closed on repository read/write inconsistencies', async () => {
    const repository = fakeRepository(undefined, inspection());
    await assert.rejects(
      () => loadEntityRepositoryState(repository, () => '2026-07-10T10:00:00.000Z'),
      /state could not be loaded/
    );

    repository.writeDirty = async () => {
      throw new Error('encrypted checkpoint unavailable');
    };
    const state = createTestState();
    const adapter = createEntityRepositoryPersistenceAdapter({
      repository,
      nowIso: () => '2026-07-10T10:00:00.000Z'
    });
    await assert.rejects(() => adapter.save({ ...state, searchQuery: 'changed' }, state), /checkpoint unavailable/);
  });

  it('reports a bounded missing health record without inventing repository metadata', () => {
    assert.deepEqual(repositoryInspectionToStorageHealth(inspection('Missing'), 'verified-now'), {
      status: 'Missing',
      storageFormat: 'Missing',
      payloadBytes: 0,
      entryCount: 0,
      chunkCount: 0,
      largestEntryBytes: 0,
      lastVerifiedAt: 'verified-now'
    });
  });
});
