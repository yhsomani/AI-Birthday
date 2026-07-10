import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildEmailDeliveryRequest, classifyEmailProviderStatus, isValidEmailAddress } from './emailDelivery';
import { assessDuplicateMessageRisk } from './duplicateGuard';
import { MESSAGE_BODY_LIMITS } from './messageBodyPolicy';
import type { AppState } from './types';

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

describe('email delivery contract', () => {
  it('validates email addresses conservatively', () => {
    assert.equal(isValidEmailAddress('me@example.com'), true);
    assert.equal(isValidEmailAddress('bad-address'), false);
    assert.equal(isValidEmailAddress(''), false);
  });

  it('builds provider requests only for approved email messages', () => {
    const state = emailReadyState();
    const result = buildEmailDeliveryRequest(state, 'msg-email-rajesh', deliveryNow);

    assert.equal(result.ok, true);
    if (!result.ok) {
      return;
    }

    const serialized = JSON.stringify(result.request);
    assert.equal(result.request.senderEmail, 'me@example.com');
    assert.equal(result.request.recipientEmail, 'rajesh@example.com');
    assert.match(result.request.idempotencyKey, /^relateai-email-v1:msg-email-rajesh:/);
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
      'msg-email-rajesh',
      deliveryNow
    );
    const unapproved = buildEmailDeliveryRequest(
      {
        ...ready,
        messages: [{ ...ready.messages[0], status: 'Needs review' }, ...ready.messages.slice(1)]
      },
      'msg-email-rajesh',
      deliveryNow
    );
    const invalidSender = buildEmailDeliveryRequest(
      {
        ...ready,
        emailDelivery: {
          ...ready.emailDelivery,
          senderEmail: 'bad'
        }
      },
      'msg-email-rajesh',
      deliveryNow
    );
    const missingRecipient = buildEmailDeliveryRequest(
      {
        ...ready,
        contacts: ready.contacts.map(contact =>
          contact.id === 'c-rajesh' ? { ...contact, email: undefined } : contact
        )
      },
      'msg-email-rajesh',
      deliveryNow
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

  it('keeps provider email blocked for DND while manual handoff remains a separate user-controlled path', () => {
    const ready = emailReadyState();
    const result = buildEmailDeliveryRequest(
      {
        ...ready,
        contacts: ready.contacts.map(contact => (contact.id === 'c-rajesh' ? { ...contact, dnd: true } : contact))
      },
      'msg-email-rajesh',
      deliveryNow
    );

    assert.equal(result.ok, false);
    if (!result.ok) assert.match(result.error.message, /manual handoff/i);
  });

  it('rejects expired approval windows before provider delivery', () => {
    const ready = emailReadyState();
    const expired = buildEmailDeliveryRequest(
      {
        ...ready,
        messages: [
          {
            ...ready.messages[0],
            approvedAt: '2026-07-01T09:00:00.000Z',
            approvalExpiresAt: '2026-07-02T09:00:00.000Z'
          },
          ...ready.messages.slice(1)
        ]
      },
      'msg-email-rajesh',
      deliveryNow
    );

    assert.equal(expired.ok, false);
    if (!expired.ok) {
      assert.equal(expired.error.kind, 'not-approved');
      assert.match(expired.error.message, /expired/i);
    }
  });

  it('rejects email bodies that exceed the channel length cap', () => {
    const ready = emailReadyState();
    const result = buildEmailDeliveryRequest(
      {
        ...ready,
        messages: [
          {
            ...ready.messages[0],
            body: 'A'.repeat(MESSAGE_BODY_LIMITS.Email + 1)
          },
          ...ready.messages.slice(1)
        ]
      },
      'msg-email-rajesh',
      deliveryNow
    );

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.error.kind, 'invalid-body');
      assert.match(result.error.message, /Shorten the message or switch channel/i);
    }
  });

  it('rechecks the current duplicate fingerprint immediately before provider dispatch', () => {
    const ready = emailReadyState();
    const target = {
      ...ready.messages[0],
      occurrenceDate: '2026-07-20'
    };
    const newlySent = {
      ...target,
      id: 'newly-sent-same-occurrence',
      status: 'Sent' as const,
      sentAt: '2026-07-10T08:30:00.000Z',
      approvedAt: undefined,
      approvalExpiresAt: undefined
    };
    const changed = { ...ready, messages: [target, newlySent, ...ready.messages.slice(1)] };

    const blocked = buildEmailDeliveryRequest(changed, target.id, deliveryNow);
    assert.equal(blocked.ok, false);
    if (!blocked.ok) {
      assert.equal(blocked.error.kind, 'duplicate-risk');
      assert.match(blocked.error.message, /changed after approval/i);
    }

    const assessment = assessDuplicateMessageRisk(changed, target);
    assert.equal(assessment.risk.risk, true);
    assert.ok(assessment.fingerprint);
    const acknowledged = {
      ...changed,
      messages: changed.messages.map(message =>
        message.id === target.id
          ? {
              ...message,
              duplicateWarning: assessment.risk.risk ? assessment.risk.message : undefined,
              duplicateAcknowledged: true,
              duplicateAcknowledgementFingerprint: assessment.fingerprint
            }
          : message
      )
    };
    assert.equal(buildEmailDeliveryRequest(acknowledged, target.id, deliveryNow).ok, true);
  });

  it('classifies provider failures into actionable categories', () => {
    assert.equal(classifyEmailProviderStatus(401).kind, 'auth');
    assert.equal(classifyEmailProviderStatus(429).kind, 'quota');
    assert.equal(classifyEmailProviderStatus(503).kind, 'server');
    assert.equal(classifyEmailProviderStatus(418).kind, 'network');
  });
});
