import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { parseRelateDeepLink, resolveDeepLinkDestination } from './deepLinks';

describe('deep link routing contract', () => {
  it('routes review and contact links to the intended safe screens', () => {
    const review = parseRelateDeepLink('relateai://review/msg-asha-bday');
    const contact = parseRelateDeepLink('relateai://contact/c-asha');

    assert.equal(review.ok, true);
    assert.equal(contact.ok, true);
    if (review.ok) {
      assert.equal(review.destination.screen, 'wishPreview');
      assert.equal(review.destination.messageId, 'msg-asha-bday');
    }
    if (contact.ok) {
      assert.equal(contact.destination.screen, 'contactDetail');
      assert.equal(contact.destination.contactId, 'c-asha');
    }
  });

  it('routes exact secondary links without collapsing them into More', () => {
    const expected = {
      analytics: 'analytics',
      settings: 'settings',
      backup: 'backup',
      style: 'styleCoach',
      activity: 'activityHistory',
      setup: 'setupCheck'
    } as const;

    for (const [route, screen] of Object.entries(expected)) {
      const parsed = parseRelateDeepLink(`relateai://${route}`);
      assert.equal(parsed.ok, true);
      if (parsed.ok) assert.equal(parsed.destination.screen, screen);
    }

    const invalidSecondary = parseRelateDeepLink('relateai://settings/unexpected-reference');
    assert.equal(invalidSecondary.ok, false);
    if (!invalidSecondary.ok) {
      assert.equal(invalidSecondary.fallback.screen, 'more');
      assert.match(invalidSecondary.message, /unexpected reference/i);
    }
  });

  it('recovers unsupported and stale links to useful screens', () => {
    const state = createTestState();
    const unsupported = parseRelateDeepLink('relateai://unknown/place');
    const stale = resolveDeepLinkDestination(state, {
      screen: 'wishPreview',
      messageId: 'missing-message'
    });

    assert.equal(unsupported.ok, false);
    if (!unsupported.ok) {
      assert.equal(unsupported.fallback.screen, 'home');
    }
    assert.equal(stale.ok, false);
    assert.equal(stale.destination.screen, 'messages');
  });

  it('parses and validates exact event notification targets', () => {
    const state = createTestState();
    const event = state.events[0];
    const parsed = parseRelateDeepLink(
      `relateai://events?eventId=${encodeURIComponent(event.id)}&contactId=${encodeURIComponent(event.contactId)}`
    );
    assert.equal(parsed.ok, true);
    if (!parsed.ok) return;
    assert.deepEqual(parsed.destination, {
      screen: 'events',
      eventId: event.id,
      contactId: event.contactId
    });
    assert.deepEqual(resolveDeepLinkDestination(state, parsed.destination), {
      ok: true,
      destination: {
        screen: 'events',
        eventId: event.id,
        contactId: event.contactId
      }
    });

    const stale = resolveDeepLinkDestination(state, {
      screen: 'events',
      eventId: 'deleted-event',
      contactId: event.contactId
    });
    assert.equal(stale.ok, false);
    assert.equal(stale.destination.screen, 'events');

    const mismatched = resolveDeepLinkDestination(state, {
      screen: 'events',
      eventId: event.id,
      contactId: state.contacts.find(contact => contact.id !== event.contactId)?.id
    });
    assert.equal(mismatched.ok, false);
    assert.match(mismatched.message, /no longer matches/i);
  });

  it('recovers message links when the referenced contact was deleted', () => {
    const state = createTestState();
    const missingContactState = {
      ...state,
      contacts: state.contacts.filter(contact => contact.id !== 'c-asha')
    };

    const resolution = resolveDeepLinkDestination(missingContactState, {
      screen: 'wishPreview',
      messageId: 'msg-asha-bday'
    });

    assert.equal(resolution.ok, false);
    assert.equal(resolution.destination.screen, 'messages');
    assert.match(resolution.message, /contact is no longer available/i);
  });

  it('does not open private routes for archived contacts', () => {
    const state = createTestState();
    state.contacts[0] = { ...state.contacts[0], archivedAt: '2026-07-10T00:00:00.000Z' };
    const resolution = resolveDeepLinkDestination(state, {
      screen: 'contactDetail',
      contactId: state.contacts[0].id
    });
    assert.equal(resolution.ok, false);
    assert.equal(resolution.destination.screen, 'contacts');
  });
});
