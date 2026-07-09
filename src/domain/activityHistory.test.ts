import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { ActivityItem } from './types';
import { buildActivityHistory } from './activityHistory';

const activities: ActivityItem[] = [
  {
    id: 'a-message',
    type: 'Message',
    title: 'Message approved',
    detail: 'The message is approved for scheduled or manual send.',
    severity: 'Info',
    createdAt: '2026-07-09T08:00:00.000Z'
  },
  {
    id: 'a-ai-error',
    type: 'AI',
    title: 'AI provider unavailable',
    detail: 'Provider credentials were rejected.',
    severity: 'Warning',
    createdAt: '2026-07-08T08:00:00.000Z'
  },
  {
    id: 'a-backup-old',
    type: 'Backup',
    title: 'Encrypted backup created',
    detail: 'Backup file export completed.',
    severity: 'Info',
    createdAt: '2026-06-01T08:00:00.000Z'
  }
];

describe('activity history contract', () => {
  it('filters by text, type, severity, and date range together', () => {
    const result = buildActivityHistory(activities, {
      query: 'provider',
      type: 'AI',
      severity: 'Warning',
      date: 'Last 7 days',
      nowIso: '2026-07-09T12:00:00.000Z'
    });

    assert.deepEqual(
      result.rows.map(row => row.item.id),
      ['a-ai-error']
    );
    assert.equal(result.rows[0].isOpenIssue, true);
  });

  it('routes activity actions to safe recovery surfaces', () => {
    const result = buildActivityHistory(activities);
    const targets = Object.fromEntries(result.rows.map(row => [row.item.id, row.targetScreen]));

    assert.equal(targets['a-message'], 'messages');
    assert.equal(targets['a-ai-error'], 'more');
    assert.equal(targets['a-backup-old'], 'more');
  });

  it('distinguishes empty history from no matching activity', () => {
    const empty = buildActivityHistory([]);
    const none = buildActivityHistory(activities, { query: 'missing query' });

    assert.equal(empty.emptyState, 'No activity yet');
    assert.equal(none.emptyState, 'No matching activity');
  });

  it('keeps newest activity first after filtering', () => {
    const result = buildActivityHistory(activities, {
      date: 'All',
      nowIso: '2026-07-09T12:00:00.000Z'
    });

    assert.deepEqual(
      result.rows.map(row => row.item.id),
      ['a-message', 'a-ai-error', 'a-backup-old']
    );
  });
});
