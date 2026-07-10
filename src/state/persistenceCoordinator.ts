import type { AppState, PersistenceStorageHealth } from '../domain/types';

export type PersistenceCommitResult =
  | {
      status: 'persisted';
      snapshot: string;
      savedAt: string;
      storageHealth?: PersistenceStorageHealth;
    }
  | {
      status: 'superseded' | 'unchanged';
      snapshot: string;
    };

export interface PersistenceCommitAdapter {
  save(state: AppState, previousState?: AppState): Promise<void>;
  inspect(): Promise<PersistenceStorageHealth | undefined>;
  nowIso(): string;
}

/**
 * Serializes durable writes and coalesces queued snapshots. An older write can
 * finish before a newer write, never after it, so stale state cannot win a race.
 */
export class PersistenceCoordinator {
  private tail: Promise<void> = Promise.resolve();
  private latestRequestedSnapshot = '';
  private lastPersistedSnapshot = '';
  private lastFailedSnapshot = '';
  private lastFailure: unknown;
  private lastPersistedState?: AppState;
  private pendingCount = 0;

  constructor(private readonly adapter: PersistenceCommitAdapter) {}

  schedule(state: AppState): Promise<PersistenceCommitResult> {
    const snapshot = JSON.stringify(state);
    if (this.pendingCount === 0 && (snapshot === this.lastPersistedSnapshot || snapshot === this.lastFailedSnapshot)) {
      return Promise.resolve({ status: 'unchanged', snapshot });
    }

    this.latestRequestedSnapshot = snapshot;
    this.pendingCount += 1;

    const operation = this.tail
      .then(async (): Promise<PersistenceCommitResult> => {
        if (snapshot !== this.latestRequestedSnapshot) {
          return { status: 'superseded', snapshot };
        }
        try {
          await this.adapter.save(state, this.lastPersistedState);
        } catch (error) {
          this.lastFailedSnapshot = snapshot;
          this.lastFailure = error;
          throw error;
        }
        const storageHealth = await this.adapter.inspect();
        this.lastPersistedSnapshot = snapshot;
        this.lastPersistedState = state;
        this.lastFailedSnapshot = '';
        this.lastFailure = undefined;
        return {
          status: 'persisted',
          snapshot,
          savedAt: this.adapter.nowIso(),
          storageHealth
        };
      })
      .finally(() => {
        this.pendingCount = Math.max(0, this.pendingCount - 1);
      });

    this.tail = operation.then(() => undefined).catch(() => undefined);

    return operation;
  }

  async flush(): Promise<void> {
    await this.tail;
    if (this.lastFailedSnapshot && this.lastFailedSnapshot === this.latestRequestedSnapshot) {
      throw this.lastFailure ?? new Error('The latest protected-storage write failed.');
    }
  }

  reset(snapshot = '', state?: AppState): void {
    this.latestRequestedSnapshot = snapshot;
    this.lastPersistedSnapshot = snapshot;
    this.lastFailedSnapshot = '';
    this.lastFailure = undefined;
    this.lastPersistedState = state;
  }
}
