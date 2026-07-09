import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
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

  it('recovers unsupported and stale links to useful screens', () => {
    const state = createInitialState();
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
});
