import { createProductionInitialState } from '../data/productionState';
import type { AppState } from '../domain/types';
import {
  clearState,
  deserializeState,
  loadState,
  saveState,
  serializeState,
  type KeyValueStore
} from '../state/persistence';

export const DATA_LIFECYCLE_JOURNAL_KEY = 'relateai.secure.data-lifecycle.v1';

export type DataLifecycleOperation = 'clear' | 'restore';
export type DataLifecyclePhase =
  | 'intent-recorded'
  | 'native-cleanup'
  | 'storage-commit'
  | 'storage-verified'
  | 'native-reconciliation';

export interface DataLifecycleJournal {
  version: 1;
  operation: DataLifecycleOperation;
  operationId: string;
  phase: DataLifecyclePhase;
  startedAt: string;
  updatedAt: string;
}

export interface DataLifecycleDependencies {
  store: KeyValueStore;
  nowIso(): string;
  createId(): string;
  cancelOwnedReminders(): Promise<unknown>;
  clearHomeWidget(): Promise<unknown>;
  cleanupTemporaryBackups(): Promise<unknown>;
  reconcileReminders(plans: AppState['reminderPlans']): Promise<unknown>;
  syncHomeWidget(state: AppState): Promise<unknown>;
}

export type RestoreTransactionResult =
  | {
      status: 'restored';
      state: AppState;
    }
  | {
      status: 'reconciliation-required';
      state: AppState;
      message: string;
    };

const recordJournal = async (
  dependencies: DataLifecycleDependencies,
  journal: DataLifecycleJournal,
  phase: DataLifecyclePhase
) => {
  const next = {
    ...journal,
    phase,
    updatedAt: dependencies.nowIso()
  };
  await dependencies.store.setItem(DATA_LIFECYCLE_JOURNAL_KEY, JSON.stringify(next));
  return next;
};

const startJournal = async (
  dependencies: DataLifecycleDependencies,
  operation: DataLifecycleOperation
) => {
  const now = dependencies.nowIso();
  const journal: DataLifecycleJournal = {
    version: 1,
    operation,
    operationId: dependencies.createId(),
    phase: 'intent-recorded',
    startedAt: now,
    updatedAt: now
  };
  await dependencies.store.setItem(DATA_LIFECYCLE_JOURNAL_KEY, JSON.stringify(journal));
  return journal;
};

export const readDataLifecycleJournal = async (
  store: KeyValueStore
): Promise<DataLifecycleJournal | undefined> => {
  const raw = await store.getItem(DATA_LIFECYCLE_JOURNAL_KEY);
  if (!raw) {
    return undefined;
  }
  try {
    const value = JSON.parse(raw) as Partial<DataLifecycleJournal>;
    if (
      value.version === 1 &&
      (value.operation === 'clear' || value.operation === 'restore') &&
      typeof value.operationId === 'string' &&
      typeof value.phase === 'string' &&
      typeof value.startedAt === 'string' &&
      typeof value.updatedAt === 'string'
    ) {
      return value as DataLifecycleJournal;
    }
  } catch {
    // Invalid operational metadata is not user data and can be replaced by a new transaction.
  }
  return undefined;
};

const assertNoRelationshipRecords = (state: AppState | undefined) => {
  if (
    !state ||
    state.contacts.length > 0 ||
    state.events.length > 0 ||
    state.memories.length > 0 ||
    state.gifts.length > 0 ||
    state.messages.length > 0 ||
    state.backups.length > 0 ||
    state.reminderPlans.length > 0
  ) {
    throw new Error('Local data removal could not be verified. RelateAI did not report completion.');
  }
};

export const clearLocalDataTransaction = async (
  dependencies: DataLifecycleDependencies,
  previousState: AppState
): Promise<AppState> => {
  let journal = await startJournal(dependencies, 'clear');

  journal = await recordJournal(dependencies, journal, 'native-cleanup');
  await dependencies.cancelOwnedReminders();
  await dependencies.clearHomeWidget();
  await dependencies.cleanupTemporaryBackups();

  journal = await recordJournal(dependencies, journal, 'storage-commit');
  await clearState(dependencies.store);
  const clearedState = createProductionInitialState();
  clearedState.settings.locale = previousState.settings.locale;
  clearedState.privacy.localDataClearConfirmedAt = dependencies.nowIso();
  clearedState.persistence.status = 'Ready';
  await saveState(dependencies.store, clearedState);

  journal = await recordJournal(dependencies, journal, 'storage-verified');
  assertNoRelationshipRecords(await loadState(dependencies.store));
  await dependencies.store.removeItem(DATA_LIFECYCLE_JOURNAL_KEY);
  return clearedState;
};

export const restoreLocalDataTransaction = async (
  dependencies: DataLifecycleDependencies,
  restoredState: AppState
): Promise<RestoreTransactionResult> => {
  // Round-trip through the versioned persistence decoder before touching active storage.
  const canonicalState = deserializeState(serializeState(restoredState));
  canonicalState.persistence = { status: 'Ready' };
  let journal = await startJournal(dependencies, 'restore');

  journal = await recordJournal(dependencies, journal, 'storage-commit');
  await saveState(dependencies.store, canonicalState);

  journal = await recordJournal(dependencies, journal, 'storage-verified');
  const verified = await loadState(dependencies.store);
  if (!verified || JSON.stringify(verified) !== JSON.stringify(canonicalState)) {
    throw new Error('Restored data could not be verified. RelateAI did not report completion.');
  }

  journal = await recordJournal(dependencies, journal, 'native-reconciliation');
  try {
    await dependencies.reconcileReminders(canonicalState.reminderPlans);
    await dependencies.syncHomeWidget(canonicalState);
    await dependencies.store.removeItem(DATA_LIFECYCLE_JOURNAL_KEY);
    return { status: 'restored', state: canonicalState };
  } catch {
    return {
      status: 'reconciliation-required',
      state: canonicalState,
      message:
        'The backup is stored and verified, but device reminders or the widget still need reconciliation. Open Setup Check and retry before relying on them.'
    };
  }
};
