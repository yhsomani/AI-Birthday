import fc from 'fast-check';
import { describe, expect, it } from 'vitest';

import {
  BIRTHDAY_ARM_CAP,
  BIRTHDAY_RETENTION_MS,
  BUDGET_WINDOW_MS,
  HOUR_MS,
  MINUTE_MS,
  TEST_ARM_CAP,
  TEST_RETENTION_MS,
  type ArmBudget,
} from '../src/domain/model.js';
import {
  appendBudgetEntry,
  boundedSweepAttempt,
  capForPurpose,
  nextRepairSweepAtMs,
  pruneBudgetEntries,
  retentionMs,
  safeAddMs,
} from '../src/domain/policies.js';
import { NOW_MS, budget } from './fixtures.js';

describe('rolling arm budgets', () => {
  it('enforces separate immutable Birthday and TEST caps', () => {
    expect(capForPurpose('BIRTHDAY')).toBe(BIRTHDAY_ARM_CAP);
    expect(capForPurpose('TEST')).toBe(TEST_ARM_CAP);
    expect(retentionMs('BIRTHDAY')).toBe(BIRTHDAY_RETENTION_MS);
    expect(retentionMs('TEST')).toBe(TEST_RETENTION_MS);
  });

  it('ignores logical expiry even when TTL has not physically deleted entries', () => {
    const staleAtBoundary = NOW_MS - BUDGET_WINDOW_MS;
    const stillLive = staleAtBoundary + 1;
    expect(
      pruneBudgetEntries(
        [
          { id: 'stale', armedAtMs: staleAtBoundary },
          { id: 'live', armedAtMs: stillLive },
          { id: 'future', armedAtMs: NOW_MS + 1 },
        ],
        NOW_MS,
      ),
    ).toEqual([{ id: 'live', armedAtMs: stillLive }]);
  });

  it('never grows a Birthday budget beyond 20 distinct occurrences', () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.uuid(), { minLength: 1, maxLength: 80 }),
        ids => {
          let current: ArmBudget | null = null;
          let accepted = 0;
          for (const [index, id] of ids.entries()) {
            const next = appendBudgetEntry(
              current,
              'BIRTHDAY',
              id,
              NOW_MS + index,
            );
            if (next !== null) {
              current = next;
              accepted += 1;
            }
          }
          expect(accepted).toBe(Math.min(ids.length, BIRTHDAY_ARM_CAP));
          expect(current?.entries.length ?? 0).toBeLessThanOrEqual(
            BIRTHDAY_ARM_CAP,
          );
        },
      ),
    );
  });

  it('replaying the same budget key is idempotent', () => {
    const existing = budget('TEST', [{ id: 'same', armedAtMs: NOW_MS }]);
    expect(
      appendBudgetEntry(existing, 'TEST', 'same', NOW_MS)?.entries,
    ).toEqual(existing.entries);
    expect(TEST_ARM_CAP).toBe(3);
  });
});

describe('bounded trusted-time arithmetic', () => {
  it('rejects unsafe or overflowing timestamps', () => {
    expect(() => safeAddMs(Number.MAX_SAFE_INTEGER, 1)).toThrow(
      'TIME_OVERFLOW',
    );
    expect(() => safeAddMs(NOW_MS, -1)).toThrow('INVALID_TIME');
  });

  it('caps repair metadata and exponential retry delay', () => {
    expect(boundedSweepAttempt(0)).toBe(1);
    expect(boundedSweepAttempt(30)).toBe(30);
    expect(boundedSweepAttempt(Number.MAX_SAFE_INTEGER)).toBe(30);
    expect(nextRepairSweepAtMs(NOW_MS, 0)).toBe(NOW_MS + MINUTE_MS);
    expect(nextRepairSweepAtMs(NOW_MS, 30)).toBe(NOW_MS + HOUR_MS);
  });
});
