import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import {
  buildMessageBulkActionReport,
  buildMessageInbox,
  findNextReviewMessageId,
  messageApprovalRouteIssue
} from './messageInbox';

describe('message inbox contract', () => {
  it('enforces WhatsApp consent while keeping manual email handoff independent of provider delivery', () => {
    const state = createTestState();
    const whatsapp = {
      ...state.messages[0],
      contactId: 'c-asha',
      channel: 'WhatsApp' as const
    };
    state.settings.whatsappHandoffEnabled = true;
    state.privacy.whatsappHandoffConsent = false;
    assert.match(messageApprovalRouteIssue(state, whatsapp) ?? '', /explicit consent/i);
    state.privacy.whatsappHandoffConsent = true;
    assert.equal(messageApprovalRouteIssue(state, whatsapp), undefined);

    const email = {
      ...state.messages[0],
      contactId: 'c-rajesh',
      channel: 'Email' as const
    };
    state.settings.emailEnabled = false;
    assert.equal(messageApprovalRouteIssue(state, email), undefined);

    const missingRoute = { ...whatsapp, channel: 'SMS' as const };
    const withoutRoute = {
      ...state,
      contacts: state.contacts.map(contact =>
        contact.id === missingRoute.contactId ? { ...contact, phone: undefined, routes: [] } : contact
      )
    };
    assert.match(messageApprovalRouteIssue(withoutRoute, missingRoute) ?? '', /phone number/i);
    assert.equal(
      messageApprovalRouteIssue(withoutRoute, missingRoute, {
        allowDndManualControl: true,
        allowShareFallback: true
      }),
      undefined
    );
  });

  it('builds status tab counts and filters review messages', () => {
    const state = createTestState();
    const inbox = buildMessageInbox(state, {
      tab: 'Review',
      channel: 'All',
      query: '',
      sort: 'Newest'
    });

    assert.equal(inbox.counts.All, state.messages.length);
    assert.equal(inbox.counts.Review, 2);
    assert.ok(inbox.rows.every(row => row.message.status === 'Needs review' || row.message.status === 'Draft'));
  });

  it('searches message body/contact context and applies channel filters', () => {
    const initial = createTestState();
    const state = {
      ...initial,
      messages: initial.messages.map(message =>
        message.id === 'msg-mira-checkin'
          ? { ...message, status: 'Sent' as const, sentAt: '2026-07-09T10:00:00.000Z' }
          : message
      )
    };
    const inbox = buildMessageInbox(state, {
      tab: 'Sent',
      channel: 'Manual',
      query: 'pune',
      sort: 'Newest'
    });

    assert.equal(inbox.rows.length, 1);
    assert.equal(inbox.rows[0].message.id, 'msg-mira-checkin');
    assert.equal(inbox.rows[0].contactName, 'Mira Shah');
  });

  it('sorts scheduled messages by scheduled date', () => {
    const state = createTestState();
    const scheduled = {
      ...state,
      messages: [
        { ...state.messages[0], id: 'later', status: 'Scheduled' as const, scheduledFor: '2026-08-10T00:00:00.000Z' },
        { ...state.messages[1], id: 'earlier', status: 'Scheduled' as const, scheduledFor: '2026-07-10T00:00:00.000Z' }
      ]
    };
    const inbox = buildMessageInbox(scheduled, {
      tab: 'Scheduled',
      channel: 'All',
      query: '',
      sort: 'Scheduled'
    });

    assert.deepEqual(
      inbox.rows.map(row => row.message.id),
      ['earlier', 'later']
    );
  });

  it('surfaces today and failed as first-class inbox tabs', () => {
    const state = createTestState();
    const inboxState = {
      ...state,
      messages: [
        {
          ...state.messages[0],
          id: 'today',
          status: 'Scheduled' as const,
          scheduledFor: '2026-07-10T09:00:00.000Z'
        },
        {
          ...state.messages[0],
          id: 'tomorrow',
          status: 'Scheduled' as const,
          scheduledFor: '2026-07-11T09:00:00.000Z'
        },
        {
          ...state.messages[0],
          id: 'blocked',
          status: 'Blocked' as const,
          readiness: 'Missing route.'
        },
        {
          ...state.messages[0],
          id: 'failed',
          status: 'Failed' as const,
          lastError: 'Provider timeout.'
        }
      ]
    };

    const today = buildMessageInbox(inboxState, {
      tab: 'Today',
      channel: 'All',
      query: '',
      sort: 'Scheduled',
      nowIso: '2026-07-10T12:00:00.000Z'
    });
    const failed = buildMessageInbox(inboxState, {
      tab: 'Failed',
      channel: 'All',
      query: '',
      sort: 'Newest',
      nowIso: '2026-07-10T12:00:00.000Z'
    });

    assert.equal(today.counts.All, 4);
    assert.equal(today.counts.Today, 1);
    assert.equal(today.counts.Scheduled, 2);
    assert.equal(today.counts.Blocked, 1);
    assert.equal(today.counts.Failed, 1);
    assert.deepEqual(
      today.rows.map(row => row.message.id),
      ['today']
    );
    assert.deepEqual(
      failed.rows.map(row => row.message.id),
      ['failed']
    );
  });

  it('matches Today by the device calendar date across a UTC boundary', () => {
    const previousTimeZone = process.env.TZ;
    process.env.TZ = 'America/Los_Angeles';
    try {
      const state = createTestState();
      const result = buildMessageInbox(
        {
          ...state,
          messages: [
            {
              ...state.messages[0],
              id: 'local-today',
              status: 'Scheduled' as const,
              scheduledFor: '2026-07-10T01:00:00.000Z'
            }
          ]
        },
        {
          tab: 'Today',
          channel: 'All',
          query: '',
          sort: 'Scheduled',
          nowIso: '2026-07-09T23:00:00.000Z'
        }
      );
      assert.deepEqual(
        result.rows.map(row => row.message.id),
        ['local-today']
      );
    } finally {
      process.env.TZ = previousTimeZone;
    }
  });

  it('provides specific recovery guidance for blocked and failed messages', () => {
    const tooShort = relateReducer(createTestState(), {
      type: 'editMessage',
      messageId: 'msg-asha-bday',
      body: 'Hi'
    });
    const blocked = relateReducer(tooShort, {
      type: 'approveMessage',
      messageId: 'msg-asha-bday'
    });
    const emailFailed = {
      ...blocked,
      messages: [
        {
          ...blocked.messages[0],
          id: 'email-failed',
          contactId: 'c-rajesh',
          channel: 'Email' as const,
          status: 'Failed' as const,
          body: 'A long enough message for retry guidance.',
          lastError: 'Provider unavailable.'
        },
        ...blocked.messages
      ]
    };
    const blockedInbox = buildMessageInbox(emailFailed, {
      tab: 'Blocked',
      channel: 'All',
      query: '',
      sort: 'Status',
      emailEndpointConfigured: false
    });
    const failedInbox = buildMessageInbox(emailFailed, {
      tab: 'Failed',
      channel: 'All',
      query: '',
      sort: 'Status',
      emailEndpointConfigured: false
    });
    const blockedRecoveryById = Object.fromEntries(blockedInbox.rows.map(row => [row.message.id, row.recovery?.title]));
    const failedRecoveryById = Object.fromEntries(failedInbox.rows.map(row => [row.message.id, row.recovery?.title]));

    assert.deepEqual(
      blockedInbox.rows.map(row => row.message.status),
      ['Blocked']
    );
    assert.deepEqual(
      failedInbox.rows.map(row => row.message.status),
      ['Failed']
    );
    assert.equal(blockedRecoveryById['msg-asha-bday'], 'Message needs editing');
    assert.equal(failedRecoveryById['email-failed'], 'Email provider not configured');
    assert.deepEqual(blockedInbox.rows[0].recovery?.command, {
      type: 'messages.retry',
      messageId: 'msg-asha-bday'
    });
  });

  it('distinguishes empty inboxes from no-result filters', () => {
    const empty = buildMessageInbox(
      { ...createTestState(), messages: [] },
      { tab: 'All', channel: 'All', query: '', sort: 'Newest' }
    );
    const none = buildMessageInbox(createTestState(), {
      tab: 'Sent',
      channel: 'All',
      query: 'not found',
      sort: 'Newest'
    });

    assert.equal(empty.emptyState, 'No messages yet');
    assert.equal(none.emptyState, 'No matching messages');
  });

  it('finds the next remaining review message without returning the handled draft', () => {
    const state = createTestState();
    const next = findNextReviewMessageId(state, 'msg-asha-bday');
    const none = findNextReviewMessageId(
      {
        ...state,
        messages: state.messages.map(message => ({ ...message, status: 'Scheduled' as const }))
      },
      'msg-asha-bday'
    );

    assert.equal(next, 'msg-mira-checkin');
    assert.equal(none, undefined);
  });

  it('reports bulk action eligibility and partial skips before changing messages', () => {
    const base = createTestState();
    const dndState = {
      ...base,
      contacts: base.contacts.map(contact => (contact.id === 'c-rajesh' ? { ...contact, dnd: true } : contact)),
      messages: [
        ...base.messages,
        {
          ...base.messages[0],
          id: 'msg-rajesh-dnd',
          contactId: 'c-rajesh',
          eventId: 'e-rajesh-work',
          channel: 'Email' as const,
          body: 'Congratulations Rajesh, wishing you continued success and a meaningful year ahead.'
        }
      ]
    };
    const edited = relateReducer(dndState, {
      type: 'editMessage',
      messageId: 'msg-asha-bday',
      body: 'Hi'
    });
    const report = buildMessageBulkActionReport(
      edited,
      ['msg-asha-bday', 'msg-mira-checkin', 'msg-rajesh-dnd', 'missing-message'],
      'Approve'
    );

    assert.deepEqual(report.eligibleIds, ['msg-mira-checkin']);
    assert.equal(report.skipped.length, 3);
    assert.match(report.summary, /1\/4 selected/i);
    assert.match(report.confirmation, /too short/i);
    assert.match(report.confirmation, /do-not-disturb/i);
    assert.equal(report.requiresConfirmation, true);

    const asha = edited.messages.find(message => message.id === 'msg-asha-bday');
    assert.equal(asha?.status, 'Needs review');
  });

  it('encourages one low-risk channel send before multi-message bulk approval', () => {
    const base = createTestState();
    const smsBulkState = {
      ...base,
      messages: base.messages.map(message =>
        message.id === 'msg-mira-checkin'
          ? {
              ...message,
              status: 'Needs review' as const,
              channel: 'SMS' as const,
              readiness: 'Ready for review'
            }
          : message
      )
    };

    const report = buildMessageBulkActionReport(smsBulkState, ['msg-asha-bday', 'msg-mira-checkin'], 'Approve');

    assert.deepEqual(report.eligibleIds, ['msg-asha-bday', 'msg-mira-checkin']);
    assert.match(report.verificationGuidance ?? '', /Before bulk approval on SMS/i);
    assert.match(report.verificationGuidance ?? '', /one low-risk message/i);
    assert.match(report.confirmation, /channel is verified/i);
    assert.equal(report.requiresConfirmation, true);

    const verifiedState = {
      ...smsBulkState,
      messages: [
        ...smsBulkState.messages,
        {
          ...smsBulkState.messages[0],
          id: 'msg-sms-proof',
          status: 'Sent' as const,
          sentAt: '2026-07-10T10:00:00.000Z'
        }
      ]
    };
    const verifiedReport = buildMessageBulkActionReport(
      verifiedState,
      ['msg-asha-bday', 'msg-mira-checkin'],
      'Approve'
    );

    assert.equal(verifiedReport.verificationGuidance, undefined);
  });

  it('includes event occurrence and scheduling validity in bulk approval eligibility', () => {
    const base = createTestState();
    const state = {
      ...base,
      messages: [
        {
          ...base.messages[0],
          id: 'stale-occurrence',
          occurrenceDate: '2025-08-12'
        },
        {
          ...base.messages[1],
          id: 'missing-event',
          eventId: 'event-no-longer-present',
          status: 'Needs review' as const
        }
      ]
    };
    const report = buildMessageBulkActionReport(
      state,
      ['stale-occurrence', 'missing-event'],
      'Approve',
      new Date('2026-07-10T10:00:00.000Z')
    );

    assert.equal(report.eligibleIds.length, 0);
    assert.match(
      report.skipped.find(item => item.messageId === 'stale-occurrence')?.reason ?? '',
      /occurrence.*passed/i
    );
    assert.match(
      report.skipped.find(item => item.messageId === 'missing-event')?.reason ?? '',
      /event.*no longer valid/i
    );
  });
});
