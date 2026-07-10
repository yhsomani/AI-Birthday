import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildMessageBulkActionReport, buildMessageInbox, findNextReviewMessageId } from './messageInbox';

describe('message inbox contract', () => {
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
    const approved = relateReducer(createTestState(), {
      type: 'approveMessage',
      messageId: 'msg-mira-checkin'
    });
    const state = relateReducer(approved, {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
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
    assert.deepEqual(today.rows.map(row => row.message.id), ['today']);
    assert.deepEqual(failed.rows.map(row => row.message.id), ['failed']);
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

    assert.deepEqual(blockedInbox.rows.map(row => row.message.status), ['Blocked']);
    assert.deepEqual(failedInbox.rows.map(row => row.message.status), ['Failed']);
    assert.equal(blockedRecoveryById['msg-asha-bday'], 'Message needs editing');
    assert.equal(failedRecoveryById['email-failed'], 'Email provider not configured');
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

    const report = buildMessageBulkActionReport(
      smsBulkState,
      ['msg-asha-bday', 'msg-mira-checkin'],
      'Approve'
    );

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
});
