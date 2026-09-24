/**
 * Path-contract tests for the persistence layer: every collection/doc path a
 * function writes to is pinned here so accidental schema moves fail loudly.
 */

import { describe, expect, it } from 'vitest';

import type { Firestore } from 'firebase-admin/firestore';

import {
  accountPaths,
  aiEntitlementPath,
  aiGlobalBudgetPath,
  aiUsageLedger,
  aiUsageSummary,
  deletionReceiptPath,
  globalControlPath,
} from '../src/persistence/paths.js';

// Minimal structural fake: paths are built purely from string segments, so we
// only need doc()/collection() chaining that records the walked path.
function fakeDb(): Firestore {
  const node = (path: string): unknown => ({
    path,
    collection: (name: string) => node(`${path}/${name}`),
    doc: (id: string) => node(`${path}/${id}`),
  });
  return {
    collection: (name: string) => node(name),
  } as unknown as Firestore;
}

function p(ref: unknown): string {
  return (ref as { path: string }).path;
}

describe('accountPaths', () => {
  const db = fakeDb();
  const paths = accountPaths(db, 'uid-1');

  it('roots everything under accounts/{uid}', () => {
    expect(p(paths.account)).toBe('accounts/uid-1');
    expect(p(paths.installations)).toBe('accounts/uid-1/installations');
    expect(p(paths.installation('inst-9'))).toBe(
      'accounts/uid-1/installations/inst-9',
    );
    expect(p(paths.budget('BIRTHDAY'))).toBe('accounts/uid-1/armBudgets/birthday');
    expect(p(paths.claim('TEST', 'c-1'))).toBe('accounts/uid-1/testClaims/c-1');
  });

  it('keeps server-only coordination ledgers at top level', () => {
    expect(p(paths.tombstone)).toBe('deletionTombstones/uid-1');
    expect(p(paths.presence)).toBe('coordinationPresence/uid-1');
    expect(p(paths.operation)).toBe('coordinationOperationFences/uid-1');
    expect(p(paths.latestOperationReceipt)).toBe(
      'coordinationLatestReceipts/uid-1',
    );
  });

  it('pins every remaining derived path builder', () => {
    expect(p(paths.operationReceipt('rk-1'))).toBe(
      'coordinationOperationReceipts/rk-1',
    );
    expect(p(paths.occurrenceClaims)).toBe('accounts/uid-1/occurrenceClaims');
    expect(p(paths.testClaims)).toBe('accounts/uid-1/testClaims');
    expect(p(paths.occurrenceKeys)).toBe('accounts/uid-1/occurrenceKeys');
    expect(p(paths.destinationGuards)).toBe('accounts/uid-1/destinationGuards');
    expect(p(paths.claimRequests)).toBe('accounts/uid-1/claimRequests');
    expect(p(paths.armOutcomes)).toBe('accounts/uid-1/armOutcomes');
    expect(p(paths.armBudgets)).toBe('accounts/uid-1/armBudgets');
    expect(p(paths.occurrenceKey('ak-1'))).toBe(
      'accounts/uid-1/occurrenceKeys/ak-1',
    );
    expect(p(paths.destinationGuard('ak-1'))).toBe(
      'accounts/uid-1/destinationGuards/ak-1',
    );
    expect(p(paths.request('rq-1'))).toBe(
      'accounts/uid-1/claimRequests/rq-1',
    );
    expect(p(paths.outcome('ok-1'))).toBe('accounts/uid-1/armOutcomes/ok-1');
    expect(p(paths.budget('TEST'))).toBe('accounts/uid-1/armBudgets/test');
  });
});

describe('AI gateway paths', () => {
  const db = fakeDb();

  it('materialises the entitlement under users/{uid}/meta', () => {
    expect(p(aiEntitlementPath(db, 'uid-1'))).toBe(
      'users/uid-1/meta/aiEntitlement',
    );
  });

  it('keeps the usage ledger and its summary docs co-located', () => {
    expect(p(aiUsageLedger(db, 'uid-1'))).toBe('users/uid-1/aiUsage');
    expect(p(aiUsageSummary(db, 'uid-1', '2026-09'))).toBe(
      'users/uid-1/aiUsage/summary-2026-09',
    );
  });

  it('uses a single global budget document', () => {
    expect(p(aiGlobalBudgetPath(db))).toBe('aiGlobalBudget/current');
  });
});

describe('misc paths', () => {
  const db = fakeDb();

  it('pins the global control doc', () => {
    expect(p(globalControlPath(db))).toBe('globalControl/current');
  });

  it('pins deletion receipts by key', () => {
    expect(p(deletionReceiptPath(db, 'rk-1'))).toBe('deletionReceipts/rk-1');
  });
});
