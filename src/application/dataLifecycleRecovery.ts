import type { KeyValueStore } from '../state/persistence';
import {
  DATA_LIFECYCLE_JOURNAL_KEY,
  type DataLifecycleOperation,
  type DataLifecycleStartupRecovery
} from './dataLifecycle';
import type { OperationalIssueQueue } from './operationalIssues';

export type DataLifecycleRecoveryResult =
  | {
      status: 'resolved';
      outcome: Exclude<DataLifecycleStartupRecovery['status'], 'reconciliation-required'>;
      operation?: DataLifecycleOperation;
    }
  | {
      status: 'reconciliation-required';
      operation?: DataLifecycleOperation;
    };

export interface DataLifecycleRecoveryDependencies {
  store: Pick<KeyValueStore, 'getItem'>;
  recover(): Promise<DataLifecycleStartupRecovery>;
  issues: OperationalIssueQueue;
}

/**
 * Owns the durable lifecycle-recovery signal independently from ordinary state
 * persistence. A recovery issue is resolved only after the recovery use case
 * has completed and protected storage verifies that its journal is absent.
 */
export class DataLifecycleRecoveryCoordinator {
  constructor(private readonly dependencies: DataLifecycleRecoveryDependencies) {}

  reportRequired(): void {
    this.dependencies.issues.report({
      code: 'data-lifecycle-recovery-required',
      severity: 'blocking',
      summary: 'An interrupted data operation still requires native reconciliation.',
      recovery: 'reconcile'
    });
  }

  async reportRequiredIfJournalPresent(): Promise<boolean> {
    try {
      const journalPresent = (await this.dependencies.store.getItem(DATA_LIFECYCLE_JOURNAL_KEY)) !== null;
      if (journalPresent) this.reportRequired();
      return journalPresent;
    } catch {
      // A journal that cannot be inspected cannot safely be treated as absent.
      this.reportRequired();
      return true;
    }
  }

  async reconcile(synchronizeDurableState?: () => void | Promise<void>): Promise<DataLifecycleRecoveryResult> {
    let recovery: DataLifecycleStartupRecovery;
    try {
      recovery = await this.dependencies.recover();
    } catch (error) {
      try {
        await synchronizeDurableState?.();
      } catch {
        // The original recovery failure remains authoritative; the blocking
        // issue prevents a failed synchronization from being treated as safe.
      }
      this.reportRequired();
      throw error;
    }

    try {
      // Explicit recovery may have replaced durable data. Synchronize the
      // application snapshot before the journal or issue can be reported as
      // resolved, including when native reconciliation still needs a retry.
      await synchronizeDurableState?.();
    } catch (error) {
      this.reportRequired();
      throw error;
    }

    const journalStillPresent = await this.reportRequiredIfJournalPresent();

    if (recovery.status === 'reconciliation-required' || journalStillPresent) {
      if (!journalStillPresent) this.reportRequired();
      return {
        status: 'reconciliation-required',
        ...(recovery.status === 'reconciliation-required' ? { operation: recovery.operation } : {})
      };
    }

    this.dependencies.issues.resolveCode('data-lifecycle-recovery-required');
    return {
      status: 'resolved',
      outcome: recovery.status,
      ...('operation' in recovery ? { operation: recovery.operation } : {})
    };
  }
}
