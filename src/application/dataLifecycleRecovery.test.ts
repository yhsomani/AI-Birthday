import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createProductionInitialState } from '../data/productionState';
import { createTestState } from '../test/testState';
import { loadState, PERSISTENCE_VERSION, saveState, type KeyValueStore } from '../state/persistence';
import { PersistenceCoordinator } from '../state/persistenceCoordinator';
import { relateReducer } from '../state/relateReducer';
import { AppRuntimeController } from './appRuntimeController';
import {
  DATA_LIFECYCLE_JOURNAL_KEY,
  clearLocalDataTransaction,
  recoverInterruptedDataLifecycle,
  restoreLocalDataTransaction,
  type DataLifecycleDependencies
} from './dataLifecycle';
import { DataLifecycleRecoveryCoordinator } from './dataLifecycleRecovery';
import { OperationalIssueQueue } from './operationalIssues';

class MemoryStore implements KeyValueStore {
  private readonly values = new Map<string, string>();
  private failJournalRemoval = false;

  failNextJournalRemoval() {
    this.failJournalRemoval = true;
  }

  async getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  async setItem(key: string, value: string) {
    this.values.set(key, value);
  }

  async removeItem(key: string) {
    if (key === DATA_LIFECYCLE_JOURNAL_KEY && this.failJournalRemoval) {
      this.failJournalRemoval = false;
      throw new Error('simulated journal cleanup interruption');
    }
    this.values.delete(key);
  }
}

describe('data lifecycle recovery coordination', () => {
  it('keeps failed recovery blocking across ordinary commits until explicit native reconciliation clears the journal', async () => {
    const store = new MemoryStore();
    const nativeCalls: string[] = [];
    let nativeAvailable = false;
    const lifecycle: DataLifecycleDependencies = {
      store,
      nowIso: () => '2026-07-10T12:00:00.000Z',
      createId: () => 'restore-operation-1',
      cancelOwnedReminders: async () => undefined,
      clearHomeWidget: async () => undefined,
      cleanupTemporaryBackups: async () => undefined,
      reconcileReminders: async () => {
        nativeCalls.push('reminders');
        if (!nativeAvailable) throw new Error('reminder service unavailable');
      },
      syncHomeWidget: async () => {
        nativeCalls.push('widget');
        if (!nativeAvailable) throw new Error('widget service unavailable');
      }
    };
    const restoredState = createProductionInitialState('en-IN');
    const initialRestore = await restoreLocalDataTransaction(lifecycle, restoredState);
    assert.equal(initialRestore.status, 'reconciliation-required');
    assert.notEqual(await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY), null);

    let issueSequence = 0;
    const issues = new OperationalIssueQueue({
      now: () => `2026-07-10T12:00:0${issueSequence}.000Z`,
      createId: () => `recovery-issue-${++issueSequence}`
    });
    const recovery = new DataLifecycleRecoveryCoordinator({
      store,
      recover: () => recoverInterruptedDataLifecycle(lifecycle),
      issues
    });

    const failedRecovery = await recovery.reconcile();
    assert.equal(failedRecovery.status, 'reconciliation-required');
    assert.equal(
      issues.active().find(issue => issue.code === 'data-lifecycle-recovery-required')?.severity,
      'blocking'
    );

    issues.report({
      code: 'persistence-failed',
      severity: 'blocking',
      summary: 'A regular protected-storage commit needs retry.',
      recovery: 'retry'
    });
    const persistence = new PersistenceCoordinator({
      save: state => saveState(store, state),
      inspect: async () => undefined,
      nowIso: () => '2026-07-10T12:01:00.000Z'
    });
    const runtime = new AppRuntimeController({
      loadState: async () => {
        const state = await loadState(store);
        return state
          ? { status: 'loaded' as const, state, migrated: false, version: PERSISTENCE_VERSION }
          : { status: 'missing' as const };
      },
      persistence,
      reduce: relateReducer,
      syncWidget: async () => undefined,
      issues
    });
    await runtime.start();
    await runtime.dispatchAndCommit({ type: 'navigate', screen: 'contacts' });

    assert.equal(
      issues.active().some(issue => issue.code === 'persistence-failed'),
      false
    );
    assert.equal(
      issues.active().some(issue => issue.code === 'data-lifecycle-recovery-required'),
      true
    );
    assert.notEqual(await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY), null);

    // Return the shell fields to the verified restore target so checksum-based
    // recovery can prove which dataset was committed before native repair.
    await runtime.dispatchAndCommit({ type: 'navigate', screen: 'onboarding' });
    assert.equal(
      issues.active().some(issue => issue.code === 'data-lifecycle-recovery-required'),
      true
    );

    nativeAvailable = true;
    const explicitRecovery = await recovery.reconcile();

    assert.deepEqual(explicitRecovery, { status: 'resolved', outcome: 'resumed', operation: 'restore' });
    assert.equal(nativeCalls.at(-2), 'reminders');
    assert.equal(nativeCalls.at(-1), 'widget');
    assert.equal(await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY), null);
    assert.equal(
      issues.active().some(issue => issue.code === 'data-lifecycle-recovery-required'),
      false
    );
  });

  it('installs a recovered clear before stale in-memory data can be persisted again', async () => {
    const store = new MemoryStore();
    const original = createTestState();
    await saveState(store, original);
    const issues = new OperationalIssueQueue({
      now: () => '2026-07-10T12:00:00.000Z',
      createId: () => 'clear-recovery-issue'
    });
    const persistence = new PersistenceCoordinator({
      save: state => saveState(store, state),
      inspect: async () => undefined,
      nowIso: () => '2026-07-10T12:00:00.000Z'
    });
    const runtime = new AppRuntimeController({
      loadState: async () => ({
        status: 'loaded' as const,
        state: (await loadState(store))!,
        migrated: false,
        version: PERSISTENCE_VERSION
      }),
      persistence,
      reduce: relateReducer,
      syncWidget: async () => undefined,
      issues
    });
    await runtime.start();
    assert.ok(runtime.getSnapshot().state.contacts.length > 0);

    const lifecycle: DataLifecycleDependencies = {
      store,
      nowIso: () => '2026-07-10T12:00:00.000Z',
      createId: () => 'clear-operation-1',
      cancelOwnedReminders: async () => undefined,
      clearHomeWidget: async () => undefined,
      cleanupTemporaryBackups: async () => undefined,
      reconcileReminders: async () => undefined,
      syncHomeWidget: async () => undefined
    };
    store.failNextJournalRemoval();
    await assert.rejects(clearLocalDataTransaction(lifecycle, original), /journal cleanup interruption/);
    assert.equal((await loadState(store))?.contacts.length, 0);
    assert.ok(runtime.getSnapshot().state.contacts.length > 0, 'the simulated process still has its stale snapshot');

    const recovery = new DataLifecycleRecoveryCoordinator({
      store,
      recover: () => recoverInterruptedDataLifecycle(lifecycle),
      issues
    });
    const result = await recovery.reconcile(async () => {
      const authoritativeState = await loadState(store);
      if (!authoritativeState) throw new Error('authoritative state missing');
      runtime.installVerifiedState(authoritativeState);
    });

    assert.deepEqual(result, { status: 'resolved', outcome: 'resumed', operation: 'clear' });
    assert.equal(runtime.getSnapshot().state.contacts.length, 0);
    await runtime.dispatchAndCommit({ type: 'navigate', screen: 'contacts' });
    assert.equal((await loadState(store))?.contacts.length, 0);
    assert.equal(await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY), null);
  });

  it('fails startup on corrupt lifecycle metadata and routes through confirmed destructive storage recovery', async () => {
    const store = new MemoryStore();
    await saveState(store, createTestState());
    const corruptJournal = JSON.stringify({
      version: 1,
      operation: 'clear',
      operationId: 'corrupt-clear-operation',
      phase: 'unknown-phase',
      startedAt: '2026-07-10T12:00:00.000Z',
      updatedAt: '2026-07-10T12:00:00.000Z'
    });
    await store.setItem(DATA_LIFECYCLE_JOURNAL_KEY, corruptJournal);
    const issues = new OperationalIssueQueue({
      now: () => '2026-07-10T12:00:00.000Z',
      createId: () => 'corrupt-journal-issue'
    });
    const lifecycle: DataLifecycleDependencies = {
      store,
      nowIso: () => '2026-07-10T12:00:00.000Z',
      createId: () => 'unused-operation',
      cancelOwnedReminders: async () => undefined,
      clearHomeWidget: async () => undefined,
      cleanupTemporaryBackups: async () => undefined,
      reconcileReminders: async () => undefined,
      syncHomeWidget: async () => undefined
    };
    const recovery = new DataLifecycleRecoveryCoordinator({
      store,
      recover: () => recoverInterruptedDataLifecycle(lifecycle),
      issues
    });
    const persistence = new PersistenceCoordinator({
      save: state => saveState(store, state),
      inspect: async () => undefined,
      nowIso: () => '2026-07-10T12:00:00.000Z'
    });
    const runtime = new AppRuntimeController({
      loadState: async () => {
        await recovery.reconcile();
        const state = await loadState(store);
        return state
          ? { status: 'loaded' as const, state, migrated: false, version: PERSISTENCE_VERSION }
          : { status: 'missing' as const };
      },
      resetFailedStorage: async () => {
        await store.removeItem(DATA_LIFECYCLE_JOURNAL_KEY);
        await saveState(store, createProductionInitialState());
      },
      persistence,
      reduce: relateReducer,
      syncWidget: async () => undefined,
      issues
    });

    await runtime.start();
    const failed = runtime.getSnapshot();
    assert.equal(failed.phase, 'failed');
    assert.equal(await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY), corruptJournal);
    assert.equal(
      issues.active().some(issue => issue.code === 'data-lifecycle-recovery-required'),
      true
    );

    const recovered = await runtime.clearFailedStorageAndRetry();
    assert.equal(recovered.phase, 'ready');
    assert.equal(recovered.state.contacts.length, 0);
    assert.equal(await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY), null);
  });

  it('fails startup when restore state matches neither checksum and requires confirmed destructive recovery', async () => {
    const store = new MemoryStore();
    const previous = createTestState();
    await saveState(store, previous);
    const restored = createProductionInitialState('en-IN');
    restored.onboarding.completed = true;
    const lifecycle: DataLifecycleDependencies = {
      store,
      nowIso: () => '2026-07-10T12:00:00.000Z',
      createId: () => 'mismatched-restore-operation',
      cancelOwnedReminders: async () => undefined,
      clearHomeWidget: async () => undefined,
      cleanupTemporaryBackups: async () => undefined,
      reconcileReminders: async () => {
        throw new Error('leave restore journal active');
      },
      syncHomeWidget: async () => undefined
    };
    const initialRestore = await restoreLocalDataTransaction(lifecycle, restored);
    assert.equal(initialRestore.status, 'reconciliation-required');
    const unrelated = createTestState();
    unrelated.settings.locale = 'hi-IN';
    await saveState(store, unrelated);

    const issues = new OperationalIssueQueue({
      now: () => '2026-07-10T12:00:00.000Z',
      createId: () => 'mismatched-restore-issue'
    });
    const recovery = new DataLifecycleRecoveryCoordinator({
      store,
      recover: () => recoverInterruptedDataLifecycle(lifecycle),
      issues
    });
    const persistence = new PersistenceCoordinator({
      save: state => saveState(store, state),
      inspect: async () => undefined,
      nowIso: () => '2026-07-10T12:00:00.000Z'
    });
    const runtime = new AppRuntimeController({
      loadState: async () => {
        await recovery.reconcile();
        const state = await loadState(store);
        return state
          ? { status: 'loaded' as const, state, migrated: false, version: PERSISTENCE_VERSION }
          : { status: 'missing' as const };
      },
      resetFailedStorage: async () => {
        await store.removeItem(DATA_LIFECYCLE_JOURNAL_KEY);
        await saveState(store, createProductionInitialState());
      },
      persistence,
      reduce: relateReducer,
      syncWidget: async () => undefined,
      issues
    });

    await runtime.start();
    assert.equal(runtime.getSnapshot().phase, 'failed');
    assert.notEqual(await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY), null);
    assert.equal(
      issues.active().some(issue => issue.code === 'data-lifecycle-recovery-required'),
      true
    );

    const recovered = await runtime.clearFailedStorageAndRetry();
    assert.equal(recovered.phase, 'ready');
    assert.equal(recovered.state.contacts.length, 0);
    assert.equal(await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY), null);
  });
});
