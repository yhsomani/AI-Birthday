import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildHomeWidgetSummary, nativeHomeWidgetRouteScreen, serializeHomeWidgetSummaryForNative } from './homeWidget';

describe('home widget summary contract', () => {
  it('summarizes only today events and pending approvals without sensitive details', () => {
    const state = createTestState();
    const now = new Date('2026-07-09T10:00:00.000+05:30');
    const summary = buildHomeWidgetSummary(
      {
        ...state,
        contacts: [
          {
            ...state.contacts[0],
            phone: '+919999999999',
            email: 'secret@example.com'
          }
        ],
        events: [
          {
            ...state.events[0],
            id: 'today-private-label',
            label: 'Asha private birthday plan',
            date: '2026-07-09T05:30:00.000Z'
          },
          {
            ...state.events[1],
            id: 'future-work',
            label: 'Confidential manager milestone',
            date: '2026-07-12T05:30:00.000Z'
          }
        ],
        messages: [
          {
            ...state.messages[0],
            body: 'This raw message body should never appear in the widget summary.'
          }
        ],
        memories: [
          {
            id: 'private-note',
            contactId: 'c-asha',
            category: 'Private',
            body: 'Private memory text',
            pinned: false,
            createdAt: '2026-07-09T00:00:00.000Z'
          }
        ]
      },
      now
    );

    const joined = JSON.stringify(summary);
    assert.ok(summary.tiles.some(tile => tile.id === 'today-events'));
    assert.ok(summary.tiles.some(tile => tile.id === 'pending-approvals'));
    assert.deepEqual(summary.tiles.map(tile => tile.id).sort(), ['pending-approvals', 'today-events']);
    assert.doesNotMatch(joined, /raw message body|Private memory text|9999999999|secret@example/i);
    assert.doesNotMatch(joined, /private birthday plan|Confidential manager/i);
    assert.ok(summary.tiles.every(tile => !tile.route.messageId && !tile.route.contactId));
  });

  it('routes widget counts only to safe app destinations', () => {
    const summary = buildHomeWidgetSummary(createTestState(), new Date('2026-07-09T10:00:00.000+05:30'));

    assert.ok(summary.tiles.length > 0);
    assert.ok(summary.tiles.every(tile => ['events', 'messages'].includes(tile.route.screen)));
    assert.doesNotMatch(JSON.stringify(summary.tiles), /send|delete|handoff/i);
  });

  it('keeps notification setup state out of the widget surface', () => {
    const state = createTestState();
    const summary = buildHomeWidgetSummary(
      {
        ...state,
        settings: {
          ...state.settings,
          notificationsEnabled: false
        },
        privacy: {
          ...state.privacy,
          permissionDecisions: {
            ...state.privacy.permissionDecisions,
            Notifications: 'Denied'
          }
        }
      },
      new Date('2026-07-09T10:00:00.000+05:30')
    );

    assert.ok(summary.tiles.every(tile => ['today-events', 'pending-approvals'].includes(tile.id)));
    assert.doesNotMatch(JSON.stringify(summary), /Notifications are unavailable|settings and privacy/i);
  });

  it('serializes only safe native widget routes and strips record identifiers', () => {
    const summary = buildHomeWidgetSummary(createTestState(), new Date('2026-07-09T10:00:00.000+05:30'));
    const nativeSummary = serializeHomeWidgetSummaryForNative({
      ...summary,
      tiles: [
        {
          ...summary.tiles[0],
          route: {
            screen: 'wishPreview',
            messageId: 'message-secret',
            contactId: 'contact-secret'
          }
        },
        ...summary.tiles.slice(1)
      ]
    });

    assert.equal(nativeSummary.tiles[0].route.screen, 'home');
    assert.doesNotMatch(JSON.stringify(nativeSummary), /message-secret|contact-secret/);
    assert.equal(nativeHomeWidgetRouteScreen('messages'), 'messages');
    assert.equal(nativeHomeWidgetRouteScreen('contactDetail'), 'home');
  });
});
