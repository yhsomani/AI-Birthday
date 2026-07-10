import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildAnalyticsCsvReport, buildAnalyticsDashboard, buildShareableAnalyticsSummary } from './analytics';

describe('analytics contract', () => {
  it('summarizes relationship health, coverage, delivery, and insights', () => {
    const approved = relateReducer(createTestState(), {
      type: 'approveMessage',
      messageId: 'msg-mira-checkin'
    });
    const sent = relateReducer(approved, {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const dashboard = buildAnalyticsDashboard(sent, 'Last 30 days', new Date('2026-07-09T12:00:00.000Z'));

    assert.equal(dashboard.contactCount, 3);
    assert.ok(dashboard.metrics.some(metric => metric.label === 'Relationship health' && /\d+%/.test(metric.value)));
    assert.ok(dashboard.metrics.some(metric => metric.label === 'Event coverage'));
    assert.ok(dashboard.metrics.some(metric => metric.label === 'Delivery success'));
    assert.ok(dashboard.relationshipDistribution.some(bucket => bucket.label === 'Family' && bucket.count === 1));
    assert.ok(dashboard.neglectedContacts.some(contact => contact.contactId === 'c-rajesh'));
    assert.ok(dashboard.insights.some(insight => insight.targetScreen === 'messages' || insight.targetScreen === 'contactDetail'));
  });

  it('provides useful sparse states without divide-by-zero percentages', () => {
    const empty = buildAnalyticsDashboard(
      {
        ...createTestState(),
        contacts: [],
        events: [],
        memories: [],
        gifts: [],
        messages: []
      },
      'This year',
      new Date('2026-07-09T12:00:00.000Z')
    );

    assert.match(empty.emptyState ?? '', /Add or import contacts/);
    assert.ok(empty.metrics.every(metric => metric.value !== 'NaN%'));
  });

  it('exports a redacted CSV report without private notes, contact routes, or raw message bodies', () => {
    const state = {
      ...createTestState(),
      contacts: [
        {
          ...createTestState().contacts[0],
          phone: '+911111111111',
          email: 'secret@example.com'
        }
      ],
      memories: [
        {
          id: 'private-memory',
          contactId: 'c-asha',
          category: 'Private' as const,
          body: 'Private family issue',
          pinned: false,
          createdAt: '2026-07-01T00:00:00.000Z'
        }
      ],
      messages: [
        {
          ...createTestState().messages[0],
          body: 'Raw message body should stay out'
        }
      ]
    };
    const csv = buildAnalyticsCsvReport(
      state,
      buildAnalyticsDashboard(state, 'All time', new Date('2026-07-09T12:00:00.000Z'))
    );

    assert.match(csv, /Contact summary/);
    assert.doesNotMatch(csv, /\+911111111111|secret@example\.com|Private family issue|Raw message body/);
  });

  it('builds a shareable relationship summary without private notes, routes, names, or message bodies', () => {
    const base = createTestState();
    const state = {
      ...base,
      contacts: [
        {
          ...base.contacts[0],
          phone: '+911111111111',
          email: 'secret@example.com'
        }
      ],
      memories: [
        {
          id: 'private-memory',
          contactId: 'c-asha',
          category: 'Private' as const,
          body: 'Private family issue',
          pinned: false,
          createdAt: '2026-07-01T00:00:00.000Z'
        }
      ],
      messages: [
        {
          ...base.messages[0],
          body: 'Raw message body should stay out'
        }
      ]
    };
    const dashboard = buildAnalyticsDashboard(state, 'Last 30 days', new Date('2026-07-09T12:00:00.000Z'));
    const summary = buildShareableAnalyticsSummary(dashboard, new Date('2026-07-09T12:00:00.000Z'));

    assert.equal(summary.redacted, true);
    assert.match(summary.body, /RelateAI relationship summary/);
    assert.match(summary.body, /Metrics:/);
    assert.match(summary.body, /Privacy:/);
    assert.ok(summary.lineCount > dashboard.metrics.length);
    assert.doesNotMatch(summary.body, /Asha|Mira|Rajesh|\+911111111111|secret@example\.com|Private family issue|Raw message body/);
  });
});
