import type { Auth } from 'firebase-admin/auth';
import type { Firestore } from 'firebase-admin/firestore';
import { logger } from 'firebase-functions/logger';

import { safeAddMs } from '../domain/policies.js';
import { safeErrorCategory } from './safeErrorCategory.js';
import { ControlPlaneService } from './controlPlane.js';
import { runBounded } from './runBounded.js';

const SWEEP_CONCURRENCY = 4;
const MAX_SWEEP_ITEMS = 20;

function errorCode(error: unknown): string | null {
  if (typeof error !== 'object' || error === null || !('code' in error)) {
    return null;
  }
  const code = Reflect.get(error, 'code');
  return typeof code === 'string' ? code : null;
}

function isUserNotFound(error: unknown): boolean {
  return errorCode(error) === 'auth/user-not-found';
}

export interface DeletionSweepResult {
  readonly drainsAdvanced: number;
  readonly authDeletionsVerified: number;
  readonly tombstonesFinalized: number;
  readonly deferred: number;
  readonly failed: number;
}

export class DeletionOrchestrator {
  private readonly controlPlane: ControlPlaneService;

  public constructor(
    private readonly db: Firestore,
    private readonly auth: Auth,
    private readonly clock: () => number,
  ) {
    this.controlPlane = new ControlPlaneService(db, undefined, clock);
  }

  private async ensureAuthDeleted(uid: string): Promise<boolean> {
    try {
      await this.auth.deleteUser(uid);
    } catch (error) {
      if (!isUserNotFound(error)) {
        return false;
      }
    }
    try {
      await this.auth.getUser(uid);
      return false;
    } catch (error) {
      return isUserNotFound(error);
    }
  }

  public async sweep(limit = 20): Promise<DeletionSweepResult> {
    const nowMs = this.clock();
    const boundedLimit = Number.isFinite(limit)
      ? Math.min(Math.max(Math.floor(limit), 1), MAX_SWEEP_ITEMS)
      : MAX_SWEEP_ITEMS;
    const tombstones = this.db.collection('deletionTombstones');
    const due = await tombstones
      .where('nextSweepAtMs', '<=', nowMs)
      .orderBy('nextSweepAtMs', 'asc')
      .limit(boundedLimit)
      .get();

    let drainsAdvanced = 0;
    let authDeletionsVerified = 0;
    let tombstonesFinalized = 0;
    let deferred = 0;
    let failed = 0;
    await runBounded(due.docs, SWEEP_CONCURRENCY, async document => {
      const requestKeyValue = document.get('requestKey') as unknown;
      const expectedRequestKey =
        typeof requestKeyValue === 'string' ? requestKeyValue : null;
      try {
        const stageValue = document.get('stage') as unknown;
        if (stageValue === 'DRAINING' || stageValue === 'PURGING') {
          const drainUntilValue = document.get('drainUntilMs') as unknown;
          if (
            stageValue === 'DRAINING' &&
            typeof drainUntilValue === 'number' &&
            nowMs <= drainUntilValue
          ) {
            await this.controlPlane.deferDeletionSweep(
              document.id,
              expectedRequestKey,
              safeAddMs(drainUntilValue, 1),
            );
            deferred += 1;
            return;
          }
          if (
            (await this.controlPlane.advanceDeletion(document.id)) ===
            'ADVANCED'
          ) {
            drainsAdvanced += 1;
          } else {
            await this.controlPlane.deferDeletionSweep(
              document.id,
              expectedRequestKey,
              nowMs,
            );
            deferred += 1;
          }
          return;
        }
        if (stageValue === 'AUTH_DELETION_PENDING') {
          if (await this.ensureAuthDeleted(document.id)) {
            await this.controlPlane.markAuthDeleted(document.id);
            authDeletionsVerified += 1;
          } else {
            await this.controlPlane.deferDeletionSweep(
              document.id,
              expectedRequestKey,
              nowMs,
            );
            deferred += 1;
          }
          return;
        }
        if (stageValue === 'VERIFYING') {
          const cleanupAtValue = document.get('cleanupAtMs') as unknown;
          if (typeof cleanupAtValue === 'number' && cleanupAtValue > nowMs) {
            await this.controlPlane.deferDeletionSweep(
              document.id,
              expectedRequestKey,
              cleanupAtValue,
            );
            deferred += 1;
            return;
          }
          if (
            (await this.ensureAuthDeleted(document.id)) &&
            (await this.controlPlane.finalizeDeletionTombstone(document.id))
          ) {
            tombstonesFinalized += 1;
          } else {
            await this.controlPlane.deferDeletionSweep(
              document.id,
              expectedRequestKey,
              nowMs,
            );
            deferred += 1;
          }
          return;
        }
        await this.controlPlane.deferDeletionSweep(
          document.id,
          expectedRequestKey,
          nowMs,
        );
        deferred += 1;
      } catch (error) {
        failed += 1;
        logger.error('deletion_sweep_item_failed', {
          errorType: safeErrorCategory(error),
        });
        try {
          await this.controlPlane.deferDeletionSweep(
            document.id,
            expectedRequestKey,
            nowMs,
          );
        } catch {
          logger.error('deletion_sweep_defer_failed');
          // The next scheduled run retries the still-fenced record. No account
          // identifier or request content is logged.
        }
      }
    });

    return {
      drainsAdvanced,
      authDeletionsVerified,
      tombstonesFinalized,
      deferred,
      failed,
    };
  }
}
