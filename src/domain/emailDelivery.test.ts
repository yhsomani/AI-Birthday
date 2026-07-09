import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import { buildEmailDeliveryRequest, classifyEmailProviderStatus, isValidEmailAddress } from './emailDelivery';
import type { AppState } from './types';

const emailReadyState = (): AppState => {
  const state = createInitialState();
  return {
    ...state,
    settings: {
      ...state.settings,
      emailEnabled: true
    },
    emailDelivery: {
      ...state.emailDelivery,
      senderEmail: 'me@example.com'
    },
    messages: [
      {
        ...state.messages[0],
        id: 'msg-email-rajesh',
        contactId: 'c-rajesh',
        eventId: 'e-rajesh-work',
        reason: 'Congratulations',
        channel: 'Email',
        status: 'Scheduled',
        body: 'Congratulations Rajesh, wishing you continued success and a meaningful year ahead.'
      },
      ...state.messages
    ]
  };
};

describe('email delivery contract', () => {
  it('validates email addresses conservatively', () => {
    assert.equal(isValidEmailAddress('me@example.com'), true);
    assert.equal(isValidEmailAddress('bad-address'), false);
    assert.equal(isValidEmailAddress(''), false);
  });

  it('builds provider requests only for approved email messages', () => {
    const state = emailReadyState();
    const result = buildEmailDeliveryRequest(state, 'msg-email-rajesh');

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    const serialized = JSON.stringify(result.request);
    assert.equal(result.request.senderEmail, 'me@example.com');
    assert.equal(result.request.recipientEmail, 'rajesh@example.com');
    assert.match(result.request.subject, /congratulations/i);
    assert.doesNotMatch(serialized, /Private note excluded/i);
    assert.doesNotMatch(serialized, /\+91/);
  });

  it('rejects disabled, unapproved, invalid sender, and missing recipient cases', () => {
    const ready = emailReadyState();
    const disabled = buildEmailDeliveryRequest(
      {
        ...ready,
        settings: {
          ...ready.settings,
          emailEnabled: false
        }
      },
      'msg-email-rajesh'
    );
    const unapproved = buildEmailDeliveryRequest(
      {
        ...ready,
        messages: [{ ...ready.messages[0], status: 'Needs review' }, ...ready.messages.slice(1)]
      },
      'msg-email-rajesh'
    );
    const invalidSender = buildEmailDeliveryRequest(
      {
        ...ready,
        emailDelivery: {
          ...ready.emailDelivery,
          senderEmail: 'bad'
        }
      },
      'msg-email-rajesh'
    );
    const missingRecipient = buildEmailDeliveryRequest(
      {
        ...ready,
        contacts: ready.contacts.map(contact =>
          contact.id === 'c-rajesh' ? { ...contact, email: undefined } : contact
        )
      },
      'msg-email-rajesh'
    );

    assert.equal(disabled.ok, false);
    assert.equal(unapproved.ok, false);
    assert.equal(invalidSender.ok, false);
    assert.equal(missingRecipient.ok, false);
    if (!disabled.ok) assert.equal(disabled.error.kind, 'disabled');
    if (!unapproved.ok) assert.equal(unapproved.error.kind, 'not-approved');
    if (!invalidSender.ok) assert.equal(invalidSender.error.kind, 'invalid-sender');
    if (!missingRecipient.ok) assert.equal(missingRecipient.error.kind, 'missing-recipient');
  });

  it('classifies provider failures into actionable categories', () => {
    assert.equal(classifyEmailProviderStatus(401).kind, 'auth');
    assert.equal(classifyEmailProviderStatus(429).kind, 'quota');
    assert.equal(classifyEmailProviderStatus(503).kind, 'server');
    assert.equal(classifyEmailProviderStatus(418).kind, 'network');
  });
});
