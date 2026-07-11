import { createProductionInitialState } from '../data/productionState';
import { currentScheduleTimeZone, normalizeScheduleTimeZone } from '../domain/schedulingPolicy';
import type { AppState } from '../domain/types';
import type { RelateAction } from '../state/relateReducer';
import type { PersistenceLoadResult } from '../state/persistence';
import { PersistenceCoordinator, type PersistenceCommitResult } from '../state/persistenceCoordinator';
import type {
  AppVisibility,
  PermissionReminderCoordinator,
  ReminderAffectingCommittedChange
} from './permissionReminderCoordinator';
import type { OperationalIssueQueue } from './operationalIssues';

export type AppRuntimePhase = 'not-started' | 'hydrating' | 'ready' | 'failed';

export type AppRuntimeSnapshot = Readonly<{
  phase: AppRuntimePhase;
  state: AppState;
  revision: number;
}>;

export interface AppRuntimeDependencies {
  loadState(): Promise<PersistenceLoadResult>;
  resetFailedStorage?(): Promise<void>;
  persistence: PersistenceCoordinator;
  reduce(state: AppState, action: RelateAction): AppState;
  permissionReminders?: Pick<
    PermissionReminderCoordinator,
    'afterHydration' | 'onForeground' | 'afterCommittedChange' | 'flush'
  >;
  getCurrentTimeZone?(): string;
  syncWidget(state: AppState): Promise<void>;
  issues: OperationalIssueQueue;
}

type DurableChange = ReminderAffectingCommittedChange;

const persistableState = (state: AppState): AppState => ({
  ...state,
  persistence: { status: 'Ready' }
});

export class AppRuntimeController {
  private current: AppRuntimeSnapshot = Object.freeze({
    phase: 'not-started',
    state: createProductionInitialState(),
    revision: 0
  });
  private listeners = new Set<() => void>();
  private startPromise?: Promise<void>;
  private commitTail: Promise<void> = Promise.resolve();
  private visibility: AppVisibility = 'foreground';
  private pendingDurableChanges = new Set<DurableChange>();
  private lastVerifiedState: AppState = createProductionInitialState();
  private dataReplacementPreparing = false;
  private dataReplacementActive = false;
  private dataReplacementStateInstalled = false;
  private dataReplacementCompletion: Promise<void> = Promise.resolve();
  private completeDataReplacement?: () => void;

  constructor(private readonly dependencies: AppRuntimeDependencies) {}

  private async waitForDataReplacement(): Promise<void> {
    while (this.dataReplacementPreparing || this.dataReplacementActive) {
      await this.dataReplacementCompletion;
    }
  }

  private currentTimeZone() {
    try {
      return normalizeScheduleTimeZone(this.dependencies.getCurrentTimeZone?.()) ?? currentScheduleTimeZone();
    } catch {
      return currentScheduleTimeZone();
    }
  }

  getSnapshot = (): AppRuntimeSnapshot => this.current;

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  private publish(phase: AppRuntimePhase, state: AppState) {
    this.current = Object.freeze({
      phase,
      state,
      revision: this.current.revision + 1
    });
    for (const listener of [...this.listeners]) listener();
  }

  private recordChangeKinds(previous: AppState, next: AppState) {
    if (previous.events !== next.events) this.pendingDurableChanges.add('events');
    if (previous.contacts !== next.contacts) this.pendingDurableChanges.add('contacts');
    if (previous.messages !== next.messages) this.pendingDurableChanges.add('messages');
    if (
      previous.onboarding !== next.onboarding ||
      previous.setupChecks !== next.setupChecks ||
      previous.aiProvider !== next.aiProvider ||
      previous.emailDelivery !== next.emailDelivery ||
      previous.calendarSync !== next.calendarSync
    ) {
      this.pendingDurableChanges.add('setup');
    }
    if (previous.backups !== next.backups) this.pendingDurableChanges.add('backups');
    if (previous.settings !== next.settings) this.pendingDurableChanges.add('settings');
  }

  private internalReduce(action: RelateAction) {
    const next = this.dependencies.reduce(this.current.state, action);
    if (next !== this.current.state) this.publish(this.current.phase, next);
  }

  private async afterVerifiedCommit(
    result: Extract<PersistenceCommitResult, { status: 'persisted' }>,
    committedState: AppState
  ) {
    this.lastVerifiedState = {
      ...committedState,
      persistence: {
        status: 'Ready',
        lastSavedAt: result.savedAt,
        storageHealth: result.storageHealth
      }
    };
    const currentSnapshot = JSON.stringify(persistableState(this.current.state));
    if (result.snapshot !== currentSnapshot) return;

    this.internalReduce({
      type: 'persistenceSaved',
      savedAt: result.savedAt,
      storageHealth: result.storageHealth
    });
    this.lastVerifiedState = this.current.state;
    this.dependencies.issues.resolveCode('persistence-failed');

    try {
      await this.dependencies.syncWidget(this.current.state);
      this.dependencies.issues.resolveCode('widget-sync-failed');
    } catch {
      this.dependencies.issues.report({
        code: 'widget-sync-failed',
        severity: 'warning',
        summary: 'The widget could not be synchronized after a verified local commit.',
        recovery: 'reconcile'
      });
    }

    if (this.visibility === 'background') return;
    const changes = [...this.pendingDurableChanges];
    this.pendingDurableChanges.clear();
    for (const change of changes) {
      const lifecycle = await this.dependencies.permissionReminders?.afterCommittedChange(
        this.current.state,
        change,
        this.visibility
      );
      if (lifecycle?.status === 'reconciliation-failed') {
        this.dependencies.issues.report({
          code: 'reminder-reconciliation-failed',
          severity: 'warning',
          summary: 'Owned reminders could not be reconciled after a verified local change.',
          recovery: 'reconcile'
        });
      } else if (lifecycle?.status === 'reconciled') {
        this.dependencies.issues.resolveCode('reminder-reconciliation-failed');
      }
    }
  }

  private schedulePersistence(): Promise<void> {
    const state = persistableState(this.current.state);
    const operation = this.dependencies.persistence
      .schedule(state)
      .then(async result => {
        if (result.status === 'persisted') await this.afterVerifiedCommit(result, state);
      })
      .catch(() => {
        this.dependencies.issues.report({
          code: 'persistence-failed',
          severity: 'blocking',
          summary: 'The latest state could not be verified in protected local storage.',
          recovery: 'retry'
        });
        // A command must never remain live after its durable write failed. If
        // this is still the latest requested snapshot, return to the last
        // verified state and reset the writer baseline so an explicit retry is
        // a real retry rather than an accidental no-op.
        if (JSON.stringify(persistableState(this.current.state)) === JSON.stringify(state)) {
          this.pendingDurableChanges.clear();
          const rolledBackState: AppState = {
            ...this.lastVerifiedState,
            persistence: {
              ...this.lastVerifiedState.persistence,
              status: 'Error',
              error: 'The latest state could not be verified in protected local storage.'
            }
          };
          const durableBaseline = persistableState(this.lastVerifiedState);
          this.dependencies.persistence.reset(JSON.stringify(durableBaseline), durableBaseline);
          this.publish('ready', rolledBackState);
        }
        throw new Error('The latest protected-storage write failed.');
      });
    this.commitTail = operation.then(
      () => undefined,
      () => undefined
    );
    return operation;
  }

  start(): Promise<void> {
    if (this.startPromise) return this.startPromise;
    this.publish('hydrating', this.current.state);
    this.startPromise = this.dependencies
      .loadState()
      .then(async result => {
        const loadedState = result.status === 'loaded' ? result.state : undefined;
        const hydratedState = loadedState
          ? this.dependencies.reduce(createProductionInitialState(), { type: 'hydrate', state: loadedState })
          : createProductionInitialState();
        const state = this.dependencies.reduce(hydratedState, {
          type: 'reconcileScheduledMessageTimeZone',
          timeZone: this.currentTimeZone()
        });
        let readyState: AppState = {
          ...state,
          persistence: { ...state.persistence, status: 'Ready', error: undefined }
        };
        const persistedState = persistableState(readyState);
        if (loadedState) {
          const loadedBaseline = persistableState({
            ...loadedState,
            persistence: { ...loadedState.persistence, status: 'Ready', error: undefined }
          });
          if (JSON.stringify(loadedBaseline) !== JSON.stringify(persistedState)) {
            // Availability and migration normalization must reach protected
            // storage before the runtime publishes the normalized state.
            this.dependencies.persistence.reset(JSON.stringify(loadedBaseline), loadedBaseline);
            const normalizedCommit = await this.dependencies.persistence.schedule(persistedState);
            await this.dependencies.persistence.flush();
            if (normalizedCommit.status === 'persisted') {
              readyState = {
                ...readyState,
                persistence: {
                  status: 'Ready',
                  lastSavedAt: normalizedCommit.savedAt,
                  storageHealth: normalizedCommit.storageHealth ?? readyState.persistence.storageHealth
                }
              };
            }
          } else {
            this.dependencies.persistence.reset(JSON.stringify(persistedState), persistedState);
          }
        } else {
          this.dependencies.persistence.reset(JSON.stringify(persistedState), persistedState);
        }
        this.lastVerifiedState = readyState;
        this.publish('ready', readyState);
        this.dependencies.issues.resolveCode('storage-unavailable');
        if (result.status === 'recovered') {
          this.dependencies.issues.report({
            code: 'persistence-failed',
            severity: 'warning',
            summary: 'Stored records required selective recovery before the runtime started.',
            recovery: 'retry'
          });
        }
        try {
          const lifecycle = await this.dependencies.permissionReminders?.afterHydration(readyState, this.visibility);
          if (lifecycle?.status !== 'reconciliation-failed') return;
          this.dependencies.issues.report({
            code: 'reminder-reconciliation-failed',
            severity: 'warning',
            summary: 'Owned reminders could not be reconciled after hydration.',
            recovery: 'reconcile'
          });
        } catch {
          this.dependencies.issues.report({
            code: 'permission-refresh-failed',
            severity: 'warning',
            summary: 'Live permissions or reminders could not be refreshed after hydration.',
            recovery: 'reconcile'
          });
        }
      })
      .catch(() => {
        this.dependencies.issues.report({
          code: 'storage-unavailable',
          severity: 'blocking',
          summary: 'Protected local storage could not be opened. The runtime is fail-closed.',
          recovery: 'retry'
        });
        this.publish('failed', {
          ...createProductionInitialState(),
          persistence: { status: 'Error', error: 'Protected local storage is unavailable.' }
        });
      });
    return this.startPromise;
  }

  async retryFailedStart(): Promise<AppRuntimeSnapshot> {
    if (this.current.phase !== 'failed') return this.current;
    this.startPromise = undefined;
    await this.start();
    return this.current;
  }

  async clearFailedStorageAndRetry(): Promise<AppRuntimeSnapshot> {
    if (this.current.phase !== 'failed') {
      throw new Error('Destructive storage recovery is available only after protected storage failed to open.');
    }
    if (!this.dependencies.resetFailedStorage) {
      throw new Error('Destructive storage recovery is unavailable. No local data was removed.');
    }
    await this.dependencies.resetFailedStorage();
    this.dependencies.persistence.reset();
    this.lastVerifiedState = createProductionInitialState();
    this.startPromise = undefined;
    await this.start();
    return this.current;
  }

  dispatch(action: RelateAction): AppState {
    if (this.current.phase !== 'ready' || this.dataReplacementActive) return this.current.state;
    const previous = this.current.state;
    const next = this.dependencies.reduce(previous, action);
    if (next === previous) return previous;
    this.recordChangeKinds(previous, next);
    this.publish('ready', next);
    void this.schedulePersistence().catch(() => undefined);
    return next;
  }

  /** Applies one action and resolves only after its latest state is durably verified. */
  async dispatchAndCommit(action: RelateAction): Promise<AppState> {
    if (this.dataReplacementActive) await this.dataReplacementCompletion;
    if (this.current.phase !== 'ready') {
      throw new Error('The application runtime is not ready for a durable command.');
    }
    const previous = this.current.state;
    const next = this.dependencies.reduce(previous, action);
    if (next === previous) return previous;
    this.recordChangeKinds(previous, next);
    this.publish('ready', next);
    await this.schedulePersistence();
    await this.dependencies.persistence.flush();
    await this.commitTail;
    return this.current.state;
  }

  /** Installs a state only after a transactional use case has durably verified it. */
  installVerifiedState(state: AppState) {
    const readyState: AppState = {
      ...state,
      persistence: { ...state.persistence, status: 'Ready', error: undefined }
    };
    this.pendingDurableChanges.clear();
    const persistedState = persistableState(readyState);
    this.dependencies.persistence.reset(JSON.stringify(persistedState), persistedState);
    this.lastVerifiedState = readyState;
    if (this.dataReplacementActive) this.dataReplacementStateInstalled = true;
    this.publish('ready', readyState);
  }

  /**
   * Prevents ordinary persistence from interleaving with a journaled repository
   * replacement. The operation must install its authoritative verified state
   * before the barrier opens again; otherwise the runtime fails closed.
   */
  async runDataReplacement<T>(operation: () => Promise<T>): Promise<T> {
    if (this.current.phase !== 'ready') {
      throw new Error('The application runtime is not ready for a protected data replacement.');
    }
    if (this.dataReplacementPreparing || this.dataReplacementActive) {
      throw new Error('Another protected data replacement is already active.');
    }

    this.dataReplacementCompletion = new Promise<void>(resolve => {
      this.completeDataReplacement = resolve;
    });
    this.dataReplacementPreparing = true;
    try {
      // Foreground permission reads can commit derived records and schedule
      // notifications from the state they captured. Drain that whole queue
      // before replacing its source state so stale native work cannot finish
      // after a clear or restore.
      await this.dependencies.permissionReminders?.flush();
      // Let any already-verified commit finish all of its reconciliation work
      // before closing the barrier. Repeat if that work queued another commit.
      // Once quiescent, JavaScript cannot interleave another dispatch before
      // the synchronous active flag below is set.
      while (true) {
        const observedCommit = this.commitTail;
        await this.dependencies.persistence.flush();
        await observedCommit;
        if (observedCommit === this.commitTail) break;
      }
      this.dataReplacementActive = true;
      this.dataReplacementPreparing = false;
      this.dataReplacementStateInstalled = false;
      const result = await operation();
      if (!this.dataReplacementStateInstalled) {
        throw new Error('Protected data replacement did not install its verified state.');
      }
      return result;
    } catch (error) {
      this.pendingDurableChanges.clear();
      this.dependencies.issues.report({
        code: 'data-lifecycle-recovery-required',
        severity: 'blocking',
        summary: 'Protected data replacement did not complete. The runtime is fail-closed.',
        recovery: 'reconcile'
      });
      this.publish('failed', {
        ...createProductionInitialState(),
        persistence: { status: 'Error', error: 'Protected data replacement requires recovery.' }
      });
      throw error;
    } finally {
      this.dataReplacementPreparing = false;
      this.dataReplacementActive = false;
      this.dataReplacementStateInstalled = false;
      this.completeDataReplacement?.();
      this.completeDataReplacement = undefined;
    }
  }

  async setVisibility(visibility: AppVisibility) {
    this.visibility = visibility;
    await this.waitForDataReplacement();
    if (this.visibility !== visibility) return;
    if (visibility === 'background') {
      await this.dependencies.persistence.flush();
      return;
    }
    if (this.current.phase !== 'ready') return;
    await this.dispatchAndCommit({
      type: 'reconcileScheduledMessageTimeZone',
      timeZone: this.currentTimeZone()
    });
    // A replacement may have begun while the time-zone commit was settling.
    // Capture state for foreground native work only after that replacement has
    // published its authoritative snapshot.
    await this.waitForDataReplacement();
    if (this.current.phase !== 'ready' || this.visibility !== visibility) return;
    const result = await this.dependencies.permissionReminders?.onForeground(this.current.state);
    if (result?.status === 'reconciliation-failed') {
      this.dependencies.issues.report({
        code: 'reminder-reconciliation-failed',
        severity: 'warning',
        summary: 'Owned reminders could not be reconciled on foreground.',
        recovery: 'reconcile'
      });
    } else if (result?.status === 'reconciled') {
      this.dependencies.issues.resolveCode('reminder-reconciliation-failed');
    }
  }

  flush() {
    return this.dependencies.persistence.flush().then(() => this.commitTail);
  }
}
