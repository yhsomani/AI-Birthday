import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { loadState, type KeyValueStore } from '../state/persistence';
import {
  clearLocalDataTransaction,
  DATA_LIFECYCLE_JOURNAL_KEY,
  readDataLifecycleJournal,
  restoreLocalDataTransaction,
  type DataLifecycleDependencies
} from './dataLifecycle';

class MemoryStore implements KeyValueStore {
  values = new Map<string, string>();
  async getItem(key: string) {
    return this.values.get(key) ?? null;
  }
  async setItem(key: string, value: string) {
    this.values.set(key, value);
  }
  async removeItem(key: string) {
    this.values.delete(key);
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
    const result = await restoreLocalDataTransaction(dependencies(store, log), createTestState());

    assert.equal(result.status, 'restored');
    assert.deepEqual(log, ['reconcile-reminders', 'sync-widget']);
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
});
