import type { Firestore } from 'firebase-admin/firestore';
import { logger } from 'firebase-functions/logger';

import { safeAddMs } from '../domain/policies.js';
import { safeErrorCategory } from './safeErrorCategory.js';
import { ControlPlaneService } from './controlPlane.js';
import { runBounded } from './runBounded.js';

const SWEEP_CONCURRENCY = 4;
const MAX_SWEEP_ITEMS = 20;

export interface CoordinationOperationSweepResult {
  readonly completed: number;
  readonly pending: number;
  readonly failed: number;
}

/**
 * Repair worker for an interrupted reset/release saga. Failures remain fenced
 * and are retried by a later sweep; request bodies and account identifiers are
 * deliberately never logged by application code.
 */
export class CoordinationOperationOrchestrator {
  private readonly controlPlane: ControlPlaneService;

  public constructor(
    private readonly db: Firestore,
    private readonly clock: () => number,
  ) {
    this.controlPlane = new ControlPlaneService(db, undefined, this.clock);
  }

  public async sweep(limit = 20): Promise<CoordinationOperationSweepResult> {
    const nowMs = this.clock();
    const boundedLimit = Number.isFinite(limit)
      ? Math.min(Math.max(Math.floor(limit), 1), MAX_SWEEP_ITEMS)
      : MAX_SWEEP_ITEMS;
    const operations = await this.db
      .collection('coordinationOperationFences')
      .where('nextSweepAtMs', '<=', nowMs)
      .orderBy('nextSweepAtMs', 'asc')
      .limit(boundedLimit)
      .get();
    let completed = 0;
    let pending = 0;
    let failed = 0;
    await runBounded(operations.docs, SWEEP_CONCURRENCY, async operation => {
      const requestKeyValue = operation.get('requestKey') as unknown;
      const expectedRequestKey =
        typeof requestKeyValue === 'string' &&
        /^[a-f0-9]{64}$/u.test(requestKeyValue)
          ? requestKeyValue
          : null;
      try {
        const result = await this.controlPlane.advanceCoordinationOperation(
          operation.id,
          expectedRequestKey ?? undefined,
        );
        if (result.kind === 'COMPLETED') {
          completed += 1;
        } else {
          pending += 1;
          await this.controlPlane.deferCoordinationOperation(
            operation.id,
            expectedRequestKey,
            result.kind === 'IN_PROGRESS' && result.drainUntilMs !== undefined
              ? safeAddMs(result.drainUntilMs, 1)
              : nowMs,
          );
        }
      } catch (error) {
        failed += 1;
        logger.error('coordination_operation_sweep_item_failed', {
          errorType: safeErrorCategory(error),
        });
        await this.controlPlane.deferCoordinationOperation(
          operation.id,
          expectedRequestKey,
          nowMs,
        );
      }
    });
    return { completed, pending, failed };
  }
}
