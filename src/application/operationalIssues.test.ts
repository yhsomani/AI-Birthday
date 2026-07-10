import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { OperationalIssueQueue } from './operationalIssues';

const queueFixture = () => {
  let id = 0;
  let minute = 0;
  return new OperationalIssueQueue({
    createId: () => `issue-${++id}`,
    now: () => `2026-07-10T10:${String(minute++).padStart(2, '0')}:00.000Z`
  });
};

describe('privacy-safe operational issue queue', () => {
  it('deduplicates an active issue while retaining attempts and a stable correlation id', () => {
    const queue = queueFixture();
    const first = queue.report({
      code: 'widget-sync-failed',
      severity: 'warning',
      summary: 'Widget refresh failed.',
      recovery: 'reconcile'
    });
    const repeated = queue.report({
      code: 'widget-sync-failed',
      severity: 'warning',
      summary: 'Widget refresh failed again.',
      recovery: 'reconcile'
    });

    assert.equal(queue.active().length, 1);
    assert.equal(repeated.id, first.id);
    assert.equal(repeated.correlationId, first.correlationId);
    assert.equal(repeated.attempts, 2);
  });

  it('redacts route-like private details and resolves only known active issues', () => {
    const queue = queueFixture();
    const issue = queue.report({
      code: 'provider-delivery-unknown',
      severity: 'blocking',
      summary: 'Could not send to person@example.com or +91 98765 43210.',
      recovery: 'reconcile'
    });

    assert.doesNotMatch(issue.summary, /example|98765/);
    assert.equal(queue.resolve('missing'), undefined);
    assert.equal(queue.resolve(issue.id)?.resolvedAt, '2026-07-10T10:01:00.000Z');
    assert.equal(queue.active().length, 0);
  });

  it('resolves a current issue by typed code after reconciliation succeeds', () => {
    const queue = queueFixture();
    queue.report({
      code: 'reminder-reconciliation-failed',
      severity: 'warning',
      summary: 'Reminder reconciliation needs attention.',
      recovery: 'reconcile'
    });
    assert.equal(queue.resolveCode('reminder-reconciliation-failed')?.code, 'reminder-reconciliation-failed');
    assert.equal(queue.resolveCode('reminder-reconciliation-failed'), undefined);
  });

  it('returns immutable snapshots instead of exposing queue mutation', () => {
    const queue = queueFixture();
    queue.report({
      code: 'persistence-failed',
      severity: 'blocking',
      summary: 'Protected storage is unavailable.',
      recovery: 'retry'
    });
    const snapshot = queue.snapshot();
    assert.equal(Object.isFrozen(snapshot[0]), true);
    assert.notEqual(snapshot, queue.snapshot());
  });
});
