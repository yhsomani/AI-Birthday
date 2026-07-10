import { createProductionInitialState } from '../data/productionState';
import type { AppState } from '../domain/types';
import type { RelateAction } from '../state/relateReducer';
import type { PersistenceLoadResult } from '../state/persistence';
import { PersistenceCoordinator, type PersistenceCommitResult } from '../state/persistenceCoordinator';
import type { AppVisibility, PermissionReminderCoordinator } from './permissionReminderCoordinator';
import type { OperationalIssueQueue } from './operationalIssues';

export type AppRuntimePhase = 'not-started' | 'hydrating' | 'ready' | 'failed';

export type AppRuntimeSnapshot = Readonly<{
  phase: AppRuntimePhase;
  state: AppState;
  revision: number;
}>;

export interface AppRuntimeDependencies {
  loadState(): Promise<PersistenceLoadResult>;
  persistence: PersistenceCoordinator;
  reduce(state: AppState, action: RelateAction): AppState;
  permissionReminders?: Pick<
    PermissionReminderCoordinator,
    'afterHydration' | 'onForeground' | 'afterCommittedChange'
  >;
  syncWidget(state: AppState): Promise<void>;
  issues: OperationalIssueQueue;
}

type DurableChange = 'events' | 'settings';

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
  private visibility: AppVisibility = 'foreground';
  private pendingDurableChanges = new Set<DurableChange>();

  constructor(private readonly dependencies: AppRuntimeDependencies) {}

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
    if (previous.settings !== next.settings) this.pendingDurableChanges.add('settings');
  }

  private internalReduce(action: RelateAction) {
    const next = this.dependencies.reduce(this.current.state, action);
    if (next !== this.current.state) this.publish(this.current.phase, next);
  }

  private async afterVerifiedCommit(result: Extract<PersistenceCommitResult, { status: 'persisted' }>) {
    const currentSnapshot = JSON.stringify(persistableState(this.current.state));
    if (result.snapshot !== currentSnapshot) return;

    this.internalReduce({
      type: 'persistenceSaved',
      savedAt: result.savedAt,
      storageHealth: result.storageHealth
    });
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

  private schedulePersistence() {
    const state = persistableState(this.current.state);
    void this.dependencies.persistence
      .schedule(state)
      .then(async result => {
        if (result.status === 'persisted') await this.afterVerifiedCommit(result);
      })
      .catch(() => {
        this.dependencies.issues.report({
          code: 'persistence-failed',
          severity: 'blocking',
          summary: 'The latest state could not be verified in protected local storage.',
          recovery: 'retry'
        });
        this.internalReduce({
          type: 'persistenceError',
          message: 'The latest state could not be verified in protected local storage.'
        });
      });
  }

  start(): Promise<void> {
    if (this.startPromise) return this.startPromise;
    this.publish('hydrating', this.current.state);
    this.startPromise = this.dependencies
      .loadState()
      .then(async result => {
        const state = result.status === 'loaded' ? result.state : createProductionInitialState();
        const readyState: AppState = { ...state, persistence: { status: 'Ready' } };
        this.dependencies.persistence.reset(JSON.stringify(persistableState(readyState)));
        this.publish('ready', readyState);
        if (result.status === 'recovered') {
          this.dependencies.issues.report({
            code: 'persistence-failed',
            severity: 'warning',
            summary: 'Stored records required selective recovery before the runtime started.',
            recovery: 'retry'
          });
        }
        try {
          const lifecycle = await this.dependencies.permissionReminders?.afterHydration(
            readyState,
            this.visibility
          );
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

  dispatch(action: RelateAction): AppState {
    if (this.current.phase !== 'ready') return this.current.state;
    const previous = this.current.state;
    const next = this.dependencies.reduce(previous, action);
    if (next === previous) return previous;
    this.recordChangeKinds(previous, next);
    this.publish('ready', next);
    this.schedulePersistence();
    return next;
  }

  /** Installs a state only after a transactional use case has durably verified it. */
  installVerifiedState(state: AppState) {
    const readyState: AppState = { ...state, persistence: { status: 'Ready' } };
    this.pendingDurableChanges.clear();
    this.dependencies.persistence.reset(JSON.stringify(persistableState(readyState)));
    this.publish('ready', readyState);
  }

  async setVisibility(visibility: AppVisibility) {
    this.visibility = visibility;
    if (visibility === 'background') {
      await this.dependencies.persistence.flush();
      return;
    }
    if (this.current.phase !== 'ready') return;
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
    return this.dependencies.persistence.flush();
  }
}
