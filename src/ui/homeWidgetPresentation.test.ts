import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildHomeWidgetSummary } from '../domain/homeWidget';
import { createTestState } from '../test/testState';
import { localizeHomeWidgetSummary } from './homeWidgetPresentation';

describe('home widget presentation localization', () => {
  it('localizes summary, tile text, and accessibility labels for Hindi', () => {
    const state = createTestState();
    const summary = localizeHomeWidgetSummary(
      buildHomeWidgetSummary(
        {
          ...state,
          events: [
            {
              ...state.events[0],
              date: '2026-07-09T05:30:00.000Z'
            }
          ]
        },
        new Date('2026-07-09T10:00:00.000+05:30')
      ),
      'hi-IN'
    );

    assert.equal(summary.title, 'RelateAI आज');
    assert.match(summary.subtitle, /सुरक्षित शॉर्टकट/);
    assert.ok(summary.tiles.some(tile => /इवेंट/.test(tile.title)));
    assert.ok(summary.tiles.some(tile => /संदेश समीक्षा के लिए/.test(tile.title)));
    assert.ok(summary.tiles.some(tile => tile.accessibilityLabel.includes('Events खोलें')));
    assert.doesNotMatch(JSON.stringify(summary), /Open Events|message body|phone number/i);
  });

  it('localizes native-bound widget summary copy for Hinglish without changing safe routes', () => {
    const state = createTestState();
    const summary = localizeHomeWidgetSummary(
      buildHomeWidgetSummary(
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
      ),
      'en-Hinglish'
    );

    assert.equal(summary.title, 'RelateAI today');
    assert.match(summary.privacyNote, /private notes, aur send actions/);
    assert.ok(summary.tiles.every(tile => ['today-events', 'pending-approvals'].includes(tile.id)));
    assert.ok(summary.tiles.every(tile => ['events', 'messages'].includes(tile.route.screen)));
    assert.doesNotMatch(JSON.stringify(summary), /In-app reminders active|settings and privacy/i);
  });
});
