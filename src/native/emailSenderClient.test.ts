import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildEmailDeliveryRequest } from '../domain/emailDelivery';
import type { AppState } from '../domain/types';
import { createInitialState } from '../state/relateReducer';
import { sendEmailMessage } from './emailSenderClient';

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

describe('emailSenderClient', () => {
  it('posts approved email payloads to the configured endpoint', async () => {
    const request = buildEmailDeliveryRequest(emailReadyState(), 'msg-email-rajesh');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    let body = '';
    const result = await sendEmailMessage(
      request.request,
      {
        endpoint: 'https://email.example.test/send',
        timeoutMs: 1000
      },
      async (_input, init) => {
        body = init.body;
        return {
          ok: true,
          status: 200,
          json: async () => ({ deliveryId: 'delivery-1' })
        };
      }
    );

    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.deliveryId, 'delivery-1');
    }
    assert.match(body, /rajesh@example\.com/);
    assert.doesNotMatch(body, /Private note excluded/i);
    assert.doesNotMatch(body, /\+91/);
  });

  it('returns not-configured before attempting network access', async () => {
    const request = buildEmailDeliveryRequest(emailReadyState(), 'msg-email-rajesh');
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    const result = await sendEmailMessage(request.request, { timeoutMs: 1000 });

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.error.kind, 'not-configured');
    }
  });
});
