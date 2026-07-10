import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { entityCollectionNames, type RepositoryInspection } from '../domain/entityRepository';
import { notificationKindForPlan } from '../domain/notificationPlans';
import type { AppState } from '../domain/types';
import { createTestState } from '../test/testState';
import type { EntityRepositoryStatePort } from '../state/entityRepositoryPersistence';
import { clearState, loadState, saveState, type KeyValueStore } from '../state/persistence';
import {
  clearLocalDataTransaction,
  DATA_LIFECYCLE_JOURNAL_KEY,
  readDataLifecycleJournal,
  recoverInterruptedDataLifecycle,
  restoreLocalDataTransaction,
  type DataLifecycleDependencies
} from './dataLifecycle';

class MemoryStore implements KeyValueStore {
  values = new Map<string, string>();
  setKeys: string[] = [];
  removeKeys: string[] = [];
  async getItem(key: string) {
    return this.values.get(key) ?? null;
  }
  async setItem(key: string, value: string) {
    this.setKeys.push(key);
    this.values.set(key, value);
  }
  async removeItem(key: string) {
    this.removeKeys.push(key);
    this.values.delete(key);
  }
}

const repositoryInspection = (state?: AppState): RepositoryInspection => {
  const aggregateCounts = Object.fromEntries(
    entityCollectionNames.map(name => [name, state?.[name].length ?? 0])
  ) as RepositoryInspection['aggregateCounts'];
  return {
    status: state ? 'Ready' : 'Missing',
    ...(state ? { schemaVersion: 1, generation: 1, savedAt: '2026-07-10T12:00:00.000Z' } : {}),
    aggregateCounts,
    activeCounts: { ...aggregateCounts },
    archivedCounts: Object.fromEntries(
      entityCollectionNames.map(name => [name, 0])
    ) as RepositoryInspection['archivedCounts'],
    recordFileCount: entityCollectionNames.reduce((sum, name) => sum + aggregateCounts[name], 0),
    payloadBytes: state ? 4_096 : 0,
    largestRecordBytes: state ? 1_024 : 0,
    recoveredFromRollback: false
  };
};

class MemoryRepository implements EntityRepositoryStatePort {
  replacements: AppState[] = [];
  pruneCount = 0;
  failAfterNextReplacement = false;

  constructor(public current?: AppState) {}

  async loadState() {
    return this.current;
  }

  async replaceState(state: AppState) {
    this.replacements.push(state);
    this.current = state;
    if (this.failAfterNextReplacement) {
      this.failAfterNextReplacement = false;
      throw new Error('simulated process interruption after repository commit');
    }
    return repositoryInspection(state);
  }

  async inspect() {
    return repositoryInspection(this.current);
  }

  async pruneRollbackGenerations() {
    this.pruneCount += 1;
    return repositoryInspection(this.current);
  }
}

class ErasingMemoryRepository extends MemoryRepository {
  destroyCount = 0;

  async destroyAllData() {
    this.destroyCount += 1;
    this.current = undefined;
  }
}

const dependencies = (store: MemoryStore, log: string[]): DataLifecycleDependencies => ({
  store,
  nowIso: () => '2026-07-10T12:00:00.000Z',
  createId: () => 'operation-1',
  async cancelOwnedReminders() {
    log.push('cancel-reminders');
  },
  async clearHomeWidget() {
    log.push('clear-widget');
  },
  async cleanupTemporaryBackups() {
    log.push('cleanup-backups');
  },
  async reconcileReminders() {
    log.push('reconcile-reminders');
  },
  async syncHomeWidget() {
    log.push('sync-widget');
  }
});

const repositoryDependencies = (
  store: MemoryStore,
  log: string[],
  repository: MemoryRepository
): DataLifecycleDependencies => ({
  ...dependencies(store, log),
  repository,
  async clearLegacyState() {
    log.push('clear-legacy');
    await clearState(store);
  }
});

describe('transactional local data lifecycle', () => {
  it('cleans native artifacts before committing and verifies an empty durable state', async () => {
    const store = new MemoryStore();
    const log: string[] = [];
    const cleared = await clearLocalDataTransaction(dependencies(store, log), createTestState());

    assert.deepEqual(log, ['cancel-reminders', 'clear-widget', 'cleanup-backups']);
    assert.equal(cleared.contacts.length, 0);
    assert.equal(cleared.messages.length, 0);
    assert.equal(cleared.settings.locale, 'en-IN');
    assert.equal((await loadState(store))?.contacts.length, 0);
    assert.equal(store.values.has(DATA_LIFECYCLE_JOURNAL_KEY), false);
  });

  it('retains a resumable journal and old data when native cleanup fails', async () => {
    const store = new MemoryStore();
    const deps = dependencies(store, []);
    deps.cancelOwnedReminders = async () => {
      throw new Error('native failure');
    };

    await assert.rejects(() => clearLocalDataTransaction(deps, createTestState()), /native failure/);
    assert.equal((await readDataLifecycleJournal(store))?.phase, 'native-cleanup');
  });

  it('publishes restore only after storage verification and native reconciliation', async () => {
    const store = new MemoryStore();
    const log: string[] = [];
    const deps = dependencies(store, log);
    const reconciledKinds: string[] = [];
    deps.reconcileReminders = async plans => {
      log.push('reconcile-reminders');
      reconciledKinds.push(...plans.map(notificationKindForPlan));
    };
    const result = await restoreLocalDataTransaction(deps, createTestState());

    assert.equal(result.status, 'restored');
    assert.deepEqual(log, ['reconcile-reminders', 'sync-widget']);
    assert.ok(reconciledKinds.includes('pending-approval'));
    assert.ok(reconciledKinds.includes('setup-blocker'));
    assert.ok(reconciledKinds.includes('check-in-suggestion'));
    assert.equal((await loadState(store))?.contacts.length, createTestState().contacts.length);
    assert.equal(store.values.has(DATA_LIFECYCLE_JOURNAL_KEY), false);
  });

  it('reports incomplete native reconciliation without losing the verified restore', async () => {
    const store = new MemoryStore();
    const deps = dependencies(store, []);
    deps.reconcileReminders = async () => {
      throw new Error('scheduler unavailable');
    };
    const result = await restoreLocalDataTransaction(deps, createTestState());

    assert.equal(result.status, 'reconciliation-required');
    assert.equal((await loadState(store))?.contacts.length, createTestState().contacts.length);
    assert.equal((await readDataLifecycleJournal(store))?.phase, 'native-reconciliation');
  });

  it('resumes an interrupted clear before application hydration', async () => {
    const store = new MemoryStore();
    const log: string[] = [];
    const deps = dependencies(store, log);
    deps.cancelOwnedReminders = async () => {
      await store.setItem(
        DATA_LIFECYCLE_JOURNAL_KEY,
        JSON.stringify({
          version: 1,
          operation: 'clear',
          operationId: 'clear-resume',
          phase: 'native-cleanup',
          startedAt: '2026-07-10T11:00:00.000Z',
          updatedAt: '2026-07-10T11:00:00.000Z'
        })
      );
      throw new Error('interrupted');
    };
    await assert.rejects(() => clearLocalDataTransaction(deps, createTestState()), /interrupted/);

    const recovery = await recoverInterruptedDataLifecycle(dependencies(store, log));
    assert.deepEqual(recovery, { status: 'resumed', operation: 'clear' });
    assert.equal((await loadState(store))?.contacts.length, 0);
    assert.equal(await readDataLifecycleJournal(store), undefined);
  });

  it('finishes native reconciliation for a durably committed interrupted restore', async () => {
    const store = new MemoryStore();
    const deps = dependencies(store, []);
    deps.reconcileReminders = async () => {
      throw new Error('temporarily unavailable');
    };
    const first = await restoreLocalDataTransaction(deps, createTestState());
    assert.equal(first.status, 'reconciliation-required');

    const log: string[] = [];
    const recovered = await recoverInterruptedDataLifecycle(dependencies(store, log));
    assert.deepEqual(recovered, { status: 'resumed', operation: 'restore' });
    assert.deepEqual(log, ['reconcile-reminders', 'sync-widget']);
    assert.equal(await readDataLifecycleJournal(store), undefined);
  });

  it('atomically clears through the repository, removes legacy data, and prunes rollback generations', async () => {
    const store = new MemoryStore();
    const previous = createTestState();
    await saveState(store, previous);
    store.setKeys = [];
    const repository = new MemoryRepository(previous);
    const log: string[] = [];

    const cleared = await clearLocalDataTransaction(repositoryDependencies(store, log, repository), previous);

    assert.equal(repository.replacements.length, 1);
    assert.equal(repository.current?.contacts.length, 0);
    assert.equal(repository.pruneCount, 1);
    assert.equal(await loadState(store), undefined);
    assert.equal(cleared.settings.locale, previous.settings.locale);
    assert.deepEqual(log, ['cancel-reminders', 'clear-widget', 'cleanup-backups', 'clear-legacy']);
    assert.equal(
      store.setKeys.every(key => key === DATA_LIFECYCLE_JOURNAL_KEY),
      true
    );
    assert.equal(await readDataLifecycleJournal(store), undefined);
  });

  it('cryptographically erases a capable repository before creating the verified empty generation', async () => {
    const store = new MemoryStore();
    const previous = createTestState();
    await saveState(store, previous);
    const repository = new ErasingMemoryRepository(previous);
    const log: string[] = [];

    const cleared = await clearLocalDataTransaction(repositoryDependencies(store, log, repository), previous);

    assert.equal(repository.destroyCount, 1);
    assert.equal(repository.replacements.length, 1);
    assert.equal(repository.pruneCount, 0);
    assert.equal(repository.current?.contacts.length, 0);
    assert.equal(repository.current?.messages.length, 0);
    assert.equal(cleared.settings.locale, previous.settings.locale);
    assert.equal(await loadState(store), undefined);
    assert.equal(await readDataLifecycleJournal(store), undefined);
  });

  it('restores with a single repository replacement and never dual-writes monolithic state', async () => {
    const store = new MemoryStore();
    const repository = new MemoryRepository();
    const log: string[] = [];
    const restored = createTestState();

    const result = await restoreLocalDataTransaction(repositoryDependencies(store, log, repository), restored);

    assert.equal(result.status, 'restored');
    assert.equal(repository.replacements.length, 1);
    assert.equal(repository.current?.contacts.length, restored.contacts.length);
    assert.equal(repository.pruneCount, 0);
    assert.equal(await loadState(store), undefined);
    assert.equal(
      store.setKeys.every(key => key === DATA_LIFECYCLE_JOURNAL_KEY),
      true
    );
    assert.deepEqual(log, ['reconcile-reminders', 'sync-widget']);
  });

  it('resumes a clear interrupted after its repository replacement without reporting early completion', async () => {
    const store = new MemoryStore();
    const previous = createTestState();
    await saveState(store, previous);
    const repository = new MemoryRepository(previous);
    const interrupted = repositoryDependencies(store, [], repository);
    interrupted.clearLegacyState = async () => {
      throw new Error('simulated process interruption before legacy cleanup');
    };

    await assert.rejects(() => clearLocalDataTransaction(interrupted, previous), /process interruption/);
    assert.equal(repository.current?.contacts.length, 0);
    assert.equal(repository.pruneCount, 0);
    assert.equal((await readDataLifecycleJournal(store))?.phase, 'storage-commit');

    const recovered = await recoverInterruptedDataLifecycle(repositoryDependencies(store, [], repository));

    assert.deepEqual(recovered, { status: 'resumed', operation: 'clear' });
    assert.equal(repository.pruneCount, 1);
    assert.equal(await loadState(store), undefined);
    assert.equal(await readDataLifecycleJournal(store), undefined);
  });

  it('recognizes and reconciles a restore committed immediately before a process interruption', async () => {
    const store = new MemoryStore();
    const repository = new MemoryRepository();
    repository.failAfterNextReplacement = true;

    await assert.rejects(
      () => restoreLocalDataTransaction(repositoryDependencies(store, [], repository), createTestState()),
      /process interruption/
    );
    assert.equal(repository.replacements.length, 1);
    assert.equal((await readDataLifecycleJournal(store))?.phase, 'storage-commit');

    const log: string[] = [];
    const recovered = await recoverInterruptedDataLifecycle(repositoryDependencies(store, log, repository));

    assert.deepEqual(recovered, { status: 'resumed', operation: 'restore' });
    assert.equal(repository.replacements.length, 1);
    assert.deepEqual(log, ['reconcile-reminders', 'sync-widget']);
    assert.equal(await readDataLifecycleJournal(store), undefined);
  });
});
