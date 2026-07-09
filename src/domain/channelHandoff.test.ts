import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import { buildHandoffTarget } from './channelHandoff';

describe('channel handoff rules', () => {
  it('builds SMS URLs when a phone number is available', () => {
    const state = createInitialState();
    const message = state.messages.find(item => item.id === 'msg-asha-bday');
    const contact = state.contacts.find(item => item.id === message?.contactId);

    assert.ok(message);
    const target = buildHandoffTarget(contact, message);
    assert.match(target.url ?? '', /^sms:/);
    assert.equal(target.shareFallback, true);
  });

  it('falls back to share when channel details are missing', () => {
    const state = createInitialState();
    const message = state.messages.find(item => item.id === 'msg-mira-checkin');
    const contact = state.contacts.find(item => item.id === message?.contactId);

    assert.ok(message);
    const target = buildHandoffTarget(contact, { ...message, channel: 'Email' });
    assert.equal(target.url, undefined);
    assert.equal(target.shareFallback, true);
    assert.match(target.reason ?? '', /Email address is missing/);
  });
});
