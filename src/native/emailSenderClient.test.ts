import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildEmailDeliveryRequest } from '../domain/emailDelivery';
import type { AppState } from '../domain/types';
import { createTestState } from '../test/testState';
import { reconcileEmailDelivery, sendEmailMessage } from './emailSenderClient';
import { staticJsonResponse } from './providerTransport';

const authenticatedSession = {
  sessionAccessToken: 'test-session-token-1234567890',
  sessionExpiresAt: '2999-01-01T00:00:00.000Z'
};

const validApproval = {
  approvedAt: '2026-07-09T09:00:00.000Z',
  approvalExpiresAt: '2999-07-16T09:00:00.000Z'
};
const deliveryNow = new Date('2026-07-10T09:00:00.000Z');

const emailReadyState = (): AppState => {
  const state = createTestState();
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
        ...validApproval,
        scheduledFor: '2026-07-10T08:00:00.000Z',
        body: 'Congratulations Rajesh, wishing you continued success and a meaningful year ahead.'
      },
      ...state.messages
    ]
  };
};

describe('emailSenderClient', () => {
  it('posts approved email payloads to the configured endpoint', async () => {
    const request = buildEmailDeliveryRequest(emailReadyState(), 'msg-email-rajesh', deliveryNow);
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    let body = '';
    let idempotencyHeader = '';
    let authorizationHeader = '';
    const result = await sendEmailMessage(
      request.request,
      {
        endpoint: 'https://email.example.test/send',
        timeoutMs: 1000,
        ...authenticatedSession
      },
      async (_input, init) => {
        body = init.body;
        idempotencyHeader = init.headers['idempotency-key'];
        authorizationHeader = init.headers.authorization;
        return staticJsonResponse({ deliveryId: 'delivery-1', status: 'sent' });
      }
    );

    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.deliveryId, 'delivery-1');
      assert.equal(result.status, 'sent');
    }
    assert.equal(idempotencyHeader, request.request.idempotencyKey);
    assert.equal(authorizationHeader, `Bearer ${authenticatedSession.sessionAccessToken}`);
    assert.match(body, /rajesh@example\.com/);
    assert.doesNotMatch(body, /Private note excluded/i);
    assert.doesNotMatch(body, /\+91/);
  });

  it('returns an unknown outcome with the same idempotency key when the response is lost', async () => {
    const request = buildEmailDeliveryRequest(emailReadyState(), 'msg-email-rajesh', deliveryNow);
    assert.equal(request.ok, true);
    if (!request.ok) return;

    const result = await sendEmailMessage(
      request.request,
      { endpoint: 'https://email.example.test/send', timeoutMs: 1000, ...authenticatedSession },
      async () => {
        throw new TypeError('connection reset after accept');
      }
    );

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.outcome, 'unknown');
      assert.equal(result.error.kind, 'delivery-unknown');
      assert.equal(result.idempotencyKey, request.request.idempotencyKey);
      assert.match(result.error.message, /do not retry/i);
    }
  });

  it('returns not-configured before attempting network access', async () => {
    const request = buildEmailDeliveryRequest(emailReadyState(), 'msg-email-rajesh', deliveryNow);
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

  it('rejects unsafe configured endpoints before attempting network access', async () => {
    const request = buildEmailDeliveryRequest(emailReadyState(), 'msg-email-rajesh', deliveryNow);
    assert.equal(request.ok, true);
    if (!request.ok) {
      return;
    }

    let calls = 0;
    const result = await sendEmailMessage(
      request.request,
      {
        endpoint: 'https://token:secret@email.example.test/send',
        timeoutMs: 1000
      },
      async () => {
        calls += 1;
        return staticJsonResponse({ deliveryId: 'delivery-unsafe', status: 'sent' });
      }
    );

    assert.equal(result.ok, false);
    assert.equal(calls, 0);
    if (!result.ok) {
      assert.equal(result.error.kind, 'not-configured');
      assert.match(result.error.message, /not safe to use/i);
      assert.doesNotMatch(result.error.message, /secret|email\.example|send/);
    }
  });

  it('requires an authenticated short-lived session for public provider endpoints', async () => {
    const request = buildEmailDeliveryRequest(emailReadyState(), 'msg-email-rajesh', deliveryNow);
    assert.equal(request.ok, true);
    if (!request.ok) return;

    let calls = 0;
    const result = await sendEmailMessage(
      request.request,
      { endpoint: 'https://email.example.test/send', timeoutMs: 1000 },
      async () => {
        calls += 1;
        return staticJsonResponse({});
      }
    );
    assert.equal(result.ok, false);
    assert.equal(calls, 0);
    if (!result.ok) assert.equal(result.error.kind, 'auth');
  });

  it('treats non-JSON success bodies as delivery unknown', async () => {
    const request = buildEmailDeliveryRequest(emailReadyState(), 'msg-email-rajesh', deliveryNow);
    assert.equal(request.ok, true);
    if (!request.ok) return;

    const result = await sendEmailMessage(
      request.request,
      { endpoint: 'https://email.example.test/send', timeoutMs: 1000, ...authenticatedSession },
      async () => staticJsonResponse({ status: 'sent' }, { contentType: 'text/html' })
    );
    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.outcome, 'unknown');
      assert.equal(result.error.kind, 'invalid-response');
    }
  });

  it('reconciles an existing idempotent attempt without resending message content', async () => {
    let body = '';
    const result = await reconcileEmailDelivery(
      { idempotencyKey: 'attempt-key-1', deliveryId: 'delivery-1' },
      {
        statusEndpoint: 'https://email.example.test/status',
        timeoutMs: 1000,
        ...authenticatedSession
      },
      async (_input, init) => {
        body = init.body;
        return staticJsonResponse({ status: 'sent', deliveryId: 'delivery-1' });
      }
    );
    assert.deepEqual(result, { ok: true, status: 'sent', deliveryId: 'delivery-1' });
    assert.deepEqual(JSON.parse(body), {
      idempotencyKey: 'attempt-key-1',
      deliveryId: 'delivery-1'
    });
    assert.doesNotMatch(body, /recipient|message|body|subject/i);
  });

  it('keeps ambiguous reconciliation outcomes unknown and non-retryable as sends', async () => {
    const result = await reconcileEmailDelivery(
      { idempotencyKey: 'attempt-key-2' },
      {
        statusEndpoint: 'https://email.example.test/status',
        timeoutMs: 1000,
        ...authenticatedSession
      },
      async () => staticJsonResponse({ status: 'processing' })
    );
    assert.equal(result.ok, false);
    if (!result.ok) assert.equal(result.outcome, 'unknown');
  });
});
