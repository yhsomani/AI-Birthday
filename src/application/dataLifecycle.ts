import { createProductionInitialState } from '../data/productionState';
import { buildOwnedNotificationPlans, type OwnedNotificationPlan } from '../domain/notificationPlans';
import type { AppState } from '../domain/types';
import type { EntityRepositoryStatePort } from '../state/entityRepositoryPersistence';
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
  'intent-recorded' | 'native-cleanup' | 'storage-commit' | 'storage-verified' | 'native-reconciliation';

export interface DataLifecycleJournal {
  version: 1;
  operation: DataLifecycleOperation;
  operationId: string;
  phase: DataLifecyclePhase;
  startedAt: string;
  updatedAt: string;
  targetStateChecksum?: string;
  previousStateChecksum?: string;
}

export interface DataLifecycleDependencies {
  /** Protected metadata store used only for the resumable lifecycle journal. */
  store: KeyValueStore;
  /** Production data path. Omitted only by legacy-adapter tests and compatibility callers. */
  repository?: EntityRepositoryStatePort;
  /** Removes the former monolithic payload after an encrypted repository clear commits. */
  clearLegacyState?(): Promise<void>;
  nowIso(): string;
  createId(): string;
  cancelOwnedReminders(): Promise<unknown>;
  clearHomeWidget(): Promise<unknown>;
  cleanupTemporaryBackups(): Promise<unknown>;
  reconcileReminders(plans: OwnedNotificationPlan[]): Promise<unknown>;
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
  operation: DataLifecycleOperation,
  stateChecksums: Pick<DataLifecycleJournal, 'targetStateChecksum' | 'previousStateChecksum'> = {}
) => {
  const now = dependencies.nowIso();
  const journal: DataLifecycleJournal = {
    version: 1,
    operation,
    operationId: dependencies.createId(),
    phase: 'intent-recorded',
    startedAt: now,
    updatedAt: now,
    ...stateChecksums
  };
  await dependencies.store.setItem(DATA_LIFECYCLE_JOURNAL_KEY, JSON.stringify(journal));
  return journal;
};

export const readDataLifecycleJournal = async (store: KeyValueStore): Promise<DataLifecycleJournal | undefined> => {
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
      typeof value.updatedAt === 'string' &&
      (value.targetStateChecksum === undefined || typeof value.targetStateChecksum === 'string') &&
      (value.previousStateChecksum === undefined || typeof value.previousStateChecksum === 'string')
    ) {
      return value as DataLifecycleJournal;
    }
  } catch {
    // Invalid operational metadata is not user data and can be replaced by a new transaction.
  }
  return undefined;
};

const stateChecksum = (state: AppState) => {
  const value = JSON.stringify(state);
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
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

const loadDurableState = (dependencies: DataLifecycleDependencies) =>
  dependencies.repository?.loadState() ?? loadState(dependencies.store);

const replaceDurableState = async (dependencies: DataLifecycleDependencies, state: AppState): Promise<void> => {
  if (dependencies.repository) {
    await dependencies.repository.replaceState(state);
    return;
  }
  await saveState(dependencies.store, state);
};

const createClearedState = (dependencies: DataLifecycleDependencies, previousState: AppState | undefined): AppState => {
  const clearedState = createProductionInitialState();
  if (previousState) clearedState.settings.locale = previousState.settings.locale;
  clearedState.privacy.localDataClearConfirmedAt = dependencies.nowIso();
  clearedState.persistence.status = 'Ready';
  return clearedState;
};

const ownedNotificationPlansForState = (dependencies: DataLifecycleDependencies, state: AppState) => {
  const now = new Date(dependencies.nowIso());
  if (Number.isNaN(now.getTime())) {
    throw new Error('Native notification reconciliation requires a valid current time.');
  }
  return buildOwnedNotificationPlans(state, state.reminderPlans, now);
};

const commitClearedState = async (
  dependencies: DataLifecycleDependencies,
  previousState: AppState | undefined
): Promise<AppState> => {
  const clearedState = createClearedState(dependencies, previousState);
  const cryptographicallyErased = Boolean(dependencies.repository?.destroyAllData);
  if (dependencies.repository?.destroyAllData) {
    // Removing the repository key makes any filesystem remnants of the old
    // relationship records unrecoverable before a fresh empty generation is created.
    await dependencies.repository.destroyAllData();
  } else if (!dependencies.repository) {
    await clearState(dependencies.store);
  }
  await replaceDurableState(dependencies, clearedState);
  if (dependencies.repository) await dependencies.clearLegacyState?.();
  assertNoRelationshipRecords(await loadDurableState(dependencies));
  if (dependencies.repository && !cryptographicallyErased) {
    await dependencies.repository.pruneRollbackGenerations();
  }
  assertNoRelationshipRecords(await loadDurableState(dependencies));
  return clearedState;
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
  const clearedState = await commitClearedState(dependencies, previousState);

  await recordJournal(dependencies, journal, 'storage-verified');
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
  const previousState = await loadDurableState(dependencies);
  let journal = await startJournal(dependencies, 'restore', {
    targetStateChecksum: stateChecksum(canonicalState),
    previousStateChecksum: previousState ? stateChecksum(previousState) : undefined
  });

  journal = await recordJournal(dependencies, journal, 'storage-commit');
  await replaceDurableState(dependencies, canonicalState);

  const verified = await loadDurableState(dependencies);
  if (!verified || JSON.stringify(verified) !== JSON.stringify(canonicalState)) {
    throw new Error('Restored data could not be verified. RelateAI did not report completion.');
  }
  journal = await recordJournal(dependencies, journal, 'storage-verified');

  await recordJournal(dependencies, journal, 'native-reconciliation');
  try {
    await dependencies.reconcileReminders(ownedNotificationPlansForState(dependencies, canonicalState));
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

export type DataLifecycleStartupRecovery =
  | { status: 'none' }
  | { status: 'resumed'; operation: DataLifecycleOperation }
  | { status: 'aborted-before-commit'; operation: 'restore' }
  | { status: 'reconciliation-required'; operation: DataLifecycleOperation; message: string };

/** Resumes a previously authorized clear/restore transaction before hydration. */
export const recoverInterruptedDataLifecycle = async (
  dependencies: DataLifecycleDependencies
): Promise<DataLifecycleStartupRecovery> => {
  const initialJournal = await readDataLifecycleJournal(dependencies.store);
  if (!initialJournal) return { status: 'none' };

  if (initialJournal.operation === 'clear') {
    let journal = initialJournal;
    if (journal.phase === 'intent-recorded' || journal.phase === 'native-cleanup') {
      journal = await recordJournal(dependencies, journal, 'native-cleanup');
      await dependencies.cancelOwnedReminders();
      await dependencies.clearHomeWidget();
      await dependencies.cleanupTemporaryBackups();
    }

    const previous = await loadDurableState(dependencies);
    journal = await recordJournal(dependencies, journal, 'storage-commit');
    await commitClearedState(dependencies, previous);
    await recordJournal(dependencies, journal, 'storage-verified');
    await dependencies.store.removeItem(DATA_LIFECYCLE_JOURNAL_KEY);
    return { status: 'resumed', operation: 'clear' };
  }

  const current = await loadDurableState(dependencies);
  const currentChecksum = current ? stateChecksum(current) : undefined;
  if (initialJournal.phase === 'intent-recorded' || currentChecksum === initialJournal.previousStateChecksum) {
    await dependencies.store.removeItem(DATA_LIFECYCLE_JOURNAL_KEY);
    return { status: 'aborted-before-commit', operation: 'restore' };
  }
  if (!current || !initialJournal.targetStateChecksum || currentChecksum !== initialJournal.targetStateChecksum) {
    return {
      status: 'reconciliation-required',
      operation: 'restore',
      message: 'An interrupted restore could not be matched to either the previous or replacement dataset.'
    };
  }

  try {
    const journal = await recordJournal(dependencies, initialJournal, 'native-reconciliation');
    await dependencies.reconcileReminders(ownedNotificationPlansForState(dependencies, current));
    await dependencies.syncHomeWidget(current);
    await dependencies.store.removeItem(DATA_LIFECYCLE_JOURNAL_KEY);
    return { status: 'resumed', operation: journal.operation };
  } catch {
    return {
      status: 'reconciliation-required',
      operation: 'restore',
      message: 'Restored data is durable, but native reminders or the widget still require reconciliation.'
    };
  }
};
