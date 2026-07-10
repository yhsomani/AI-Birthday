import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildHandoffTarget } from './channelHandoff';

describe('channel handoff rules', () => {
  it('builds SMS URLs when a phone number is available', () => {
    const state = createTestState();
    const message = state.messages.find(item => item.id === 'msg-asha-bday');
    const contact = state.contacts.find(item => item.id === message?.contactId);

    assert.ok(message);
    const target = buildHandoffTarget(contact, message);
    assert.match(target.url ?? '', /^sms:/);
    assert.equal(target.shareFallback, true);
    assert.equal(target.label, 'Open SMS');
    assert.equal(target.fallbackLabel, 'Copy/share message');
    assert.match(target.privacyNote, /approved text only/i);
    assert.match(target.completionMessage, /Mark sent here only after/i);
    assert.equal(target.markSentLabel, 'I sent it');
  });

  it('falls back to share when channel details are missing', () => {
    const state = createTestState();
    const message = state.messages.find(item => item.id === 'msg-mira-checkin');
    const contact = state.contacts.find(item => item.id === message?.contactId);

    assert.ok(message);
    const target = buildHandoffTarget(contact, { ...message, channel: 'Email' });
    assert.equal(target.url, undefined);
    assert.equal(target.shareFallback, true);
    assert.equal(target.label, 'Copy/share message');
    assert.match(target.reason ?? '', /Email address is missing/);
  });

  it('keeps WhatsApp handoff user-controlled with a copy/share fallback', () => {
    const state = createTestState();
    const message = state.messages.find(item => item.id === 'msg-mira-checkin');
    const contact = state.contacts.find(item => item.id === message?.contactId);

    assert.ok(message);
    const target = buildHandoffTarget(contact, { ...message, channel: 'WhatsApp' });

    assert.match(target.url ?? '', /^whatsapp:/);
    assert.equal(target.label, 'Open WhatsApp');
    assert.equal(target.fallbackLabel, 'Copy/share message');
    assert.equal(target.dismissLabel, 'Not yet');
  });
});
