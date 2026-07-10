import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { ActivityItem } from './types';
import { buildActivityHistory } from './activityHistory';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';

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
    assert.equal(result.rows[0].status, 'Open');
  });

  it('derives and filters all four activity statuses deterministically', () => {
    const state = createTestState();
    const statusActivities: ActivityItem[] = [
      {
        id: 'a-open',
        type: 'AI',
        title: 'Open warning',
        detail: 'Needs user attention.',
        severity: 'Warning',
        createdAt: '2026-07-09T08:00:00.000Z'
      },
      {
        id: 'a-completed',
        type: 'Backup',
        title: 'Completed backup',
        detail: 'Backup completed.',
        severity: 'Info',
        createdAt: '2026-07-09T07:00:00.000Z'
      },
      {
        id: 'a-resolved',
        type: 'Setup',
        title: 'Resolved setup issue',
        detail: 'The issue was resolved.',
        severity: 'Error',
        status: 'Resolved',
        resolvedAt: '2026-07-09T09:00:00.000Z',
        createdAt: '2026-07-09T06:00:00.000Z'
      },
      {
        id: 'a-obsolete',
        type: 'Message',
        title: 'Stale message target',
        detail: 'The linked draft no longer exists.',
        severity: 'Error',
        status: 'Resolved',
        targetScreen: 'wishPreview',
        messageId: 'missing-message',
        createdAt: '2026-07-09T05:00:00.000Z'
      }
    ];

    const all = buildActivityHistory(statusActivities, { state });
    assert.deepEqual(Object.fromEntries(all.rows.map(row => [row.item.id, row.status])), {
      'a-open': 'Open',
      'a-completed': 'Completed',
      'a-resolved': 'Resolved',
      'a-obsolete': 'Obsolete'
    });
    for (const status of ['Open', 'Resolved', 'Obsolete', 'Completed'] as const) {
      const filtered = buildActivityHistory(statusActivities, { state, status });
      assert.deepEqual(
        filtered.rows.map(row => row.status),
        [status]
      );
    }
  });

  it('routes activity actions to safe recovery surfaces', () => {
    const result = buildActivityHistory(activities);
    const targets = Object.fromEntries(result.rows.map(row => [row.item.id, row.targetScreen]));

    assert.equal(targets['a-message'], 'messages');
    assert.equal(targets['a-ai-error'], 'setupCheck');
    assert.equal(targets['a-backup-old'], 'backup');
  });

  it('uses explicit recovery targets when linked records still exist', () => {
    const state = relateReducer(createTestState(), {
      type: 'approveMessage',
      messageId: 'msg-mira-checkin'
    });
    const result = buildActivityHistory(state.activity, { state });
    const row = result.rows[0];

    assert.equal(row.item.title, 'Message approved');
    assert.equal(row.targetScreen, 'wishPreview');
    assert.equal(row.messageId, 'msg-mira-checkin');
    assert.equal(row.contactId, 'c-mira');
    assert.equal(row.recoveryState, 'ready');
    assert.match(row.recoveryDetail, /available/i);
  });

  it('falls back safely when an explicit activity target is stale', () => {
    const activity: ActivityItem[] = [
      {
        id: 'a-stale-message',
        type: 'Message',
        title: 'Message retry prepared',
        detail: 'Review the message before retrying.',
        severity: 'Warning',
        createdAt: '2026-07-09T08:00:00.000Z',
        targetScreen: 'wishPreview',
        messageId: 'missing-message',
        actionLabel: 'Review retry'
      }
    ];
    const state = {
      ...createTestState(),
      messages: []
    };
    const result = buildActivityHistory(activity, { state });
    const row = result.rows[0];

    assert.equal(row.targetScreen, 'messages');
    assert.equal(row.messageId, undefined);
    assert.equal(row.recoveryState, 'fallback');
    assert.equal(row.status, 'Obsolete');
    assert.equal(row.isOpenIssue, false);
    assert.match(row.recoveryDetail, /linked message is no longer available/i);
  });

  it('resolves only an open issue and prepends a content-free completed audit record', () => {
    const state = createTestState();
    state.activity = [
      {
        id: 'a-open-private-title',
        type: 'AI',
        title: 'Private provider context must not be copied',
        detail: 'Private diagnostic detail must not be copied',
        severity: 'Error',
        createdAt: '2026-07-09T08:00:00.000Z'
      }
    ];

    const resolved = relateReducer(state, { type: 'resolveActivity', activityId: 'a-open-private-title' });
    const target = resolved.activity.find(item => item.id === 'a-open-private-title');
    assert.equal(target?.status, 'Resolved');
    assert.ok(target?.resolvedAt && Number.isFinite(Date.parse(target.resolvedAt)));
    assert.equal(resolved.activity[0].status, 'Completed');
    assert.match(resolved.activity[0].title, /activity issue resolved/i);
    assert.doesNotMatch(JSON.stringify(resolved.activity[0]), /Private provider|Private diagnostic/);

    const repeated = relateReducer(resolved, { type: 'resolveActivity', activityId: 'a-open-private-title' });
    assert.equal(repeated, resolved);
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

  it('evaluates Today using the device calendar day rather than a UTC string prefix', () => {
    const previousTimeZone = process.env.TZ;
    process.env.TZ = 'America/Los_Angeles';
    try {
      const result = buildActivityHistory(
        [{ ...activities[0], id: 'a-local-day', createdAt: '2026-07-10T01:00:00.000Z' }],
        { date: 'Today', nowIso: '2026-07-09T23:00:00.000Z' }
      );
      assert.deepEqual(
        result.rows.map(row => row.item.id),
        ['a-local-day']
      );
    } finally {
      process.env.TZ = previousTimeZone;
    }
  });
});
