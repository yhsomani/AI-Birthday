import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { assessDuplicateMessageRisk } from '../domain/duplicateGuard';
import { buildMessageBulkActionReport } from '../domain/messageInbox';
import type { MessageDraft, MessageStatus } from '../domain/types';
import { createTestState } from '../test/testState';
import { relateReducer, type RelateAction } from './relateReducer';

const targetId = 'msg-asha-bday';

const messageWithStatus = (status: MessageStatus): MessageDraft => {
  const message = createTestState().messages.find(item => item.id === targetId);
  assert.ok(message);
  return {
    ...message,
    status,
    approvedAt: '2026-07-09T09:00:00.000Z',
    approvalExpiresAt: '2026-07-16T09:00:00.000Z',
    duplicateAcknowledged: true,
    duplicateAcknowledgementFingerprint: 'stale-fingerprint'
  };
};

const targetAfter = (status: MessageStatus, action: RelateAction) => {
  const base = createTestState();
  const original = messageWithStatus(status);
  const state = {
    ...base,
    messages: base.messages.map(message => (message.id === targetId ? original : message))
  };
  const next = relateReducer(state, action);
  return { original, message: next.messages.find(item => item.id === targetId), next };
};

const duplicateDraftState = () =>
  relateReducer(createTestState(), {
    type: 'generateMessage',
    contactId: 'c-asha',
    eventId: 'e-asha-bday',
    reason: 'Birthday'
  });

const acknowledgeDuplicate = () => {
  const generated = duplicateDraftState();
  const draftId = generated.messages[0].id;
  const blocked = relateReducer(generated, { type: 'approveMessage', messageId: draftId });
  return {
    draftId,
    blocked,
    state: relateReducer(blocked, { type: 'acknowledgeDuplicateRisk', messageId: draftId })
  };
};

describe('message lifecycle safety', () => {
  it('does not reopen terminal or in-flight messages through review actions', () => {
    const actions: RelateAction[] = [
      { type: 'editMessage', messageId: targetId, body: 'A materially changed but valid message body.' },
      { type: 'selectVariant', messageId: targetId, variant: 'warm', discardEditedBody: true },
      { type: 'approveMessage', messageId: targetId },
      { type: 'acknowledgeDuplicateRisk', messageId: targetId },
      { type: 'rejectMessage', messageId: targetId },
      { type: 'revokeMessage', messageId: targetId },
      { type: 'retryMessage', messageId: targetId }
    ];

    for (const status of ['Sent', 'Rejected', 'Delivery pending', 'Delivery unknown'] as const) {
      for (const action of actions) {
        const result = targetAfter(status, action);
        assert.deepEqual(result.message, result.original, `${status} changed after ${action.type}`);
      }
    }
  });

  it('allows edit and variant changes only from review or draft and revokes stale consent metadata', () => {
    for (const status of ['Needs review', 'Draft'] as const) {
      const edited = targetAfter(status, {
        type: 'editMessage',
        messageId: targetId,
        body: 'Asha, this replacement body is intentionally different and still long enough for review.'
      }).message;
      assert.match(edited?.body ?? '', /replacement body/);
      assert.equal(edited?.approvedAt, undefined);
      assert.equal(edited?.approvalExpiresAt, undefined);
      assert.equal(edited?.duplicateAcknowledged, undefined);
      assert.equal(edited?.duplicateAcknowledgementFingerprint, undefined);

      const selected = targetAfter(status, {
        type: 'selectVariant',
        messageId: targetId,
        variant: 'warm',
        discardEditedBody: true
      }).message;
      assert.equal(selected?.selectedVariant, 'warm');
      assert.equal(selected?.body, selected?.variants.warm);
      assert.equal(selected?.approvedAt, undefined);
      assert.equal(selected?.duplicateAcknowledged, undefined);
      assert.equal(selected?.duplicateAcknowledgementFingerprint, undefined);
    }

    for (const status of ['Scheduled', 'Blocked', 'Failed'] as const) {
      const edited = targetAfter(status, {
        type: 'editMessage',
        messageId: targetId,
        body: 'This edit must not be applied.'
      });
      assert.deepEqual(edited.message, edited.original);
    }
  });

  it('approves only review or draft messages and leaves invalid source states untouched', () => {
    for (const status of ['Needs review', 'Draft'] as const) {
      const approved = targetAfter(status, { type: 'approveMessage', messageId: targetId }).message;
      assert.equal(approved?.status, 'Scheduled');
      assert.ok(approved?.approvedAt);
      assert.ok(approved?.approvalExpiresAt);
    }

    for (const status of ['Scheduled', 'Blocked', 'Failed'] as const) {
      const result = targetAfter(status, { type: 'approveMessage', messageId: targetId });
      assert.deepEqual(result.message, result.original);
      assert.match(result.next.activity[0].detail, /Only review or draft/i);
    }
  });

  it('rejects only eligible unsent states and revokes only scheduled approval', () => {
    for (const status of ['Needs review', 'Draft', 'Blocked', 'Failed'] as const) {
      const rejected = targetAfter(status, { type: 'rejectMessage', messageId: targetId }).message;
      assert.equal(rejected?.status, 'Rejected');
      assert.equal(rejected?.approvedAt, undefined);
      assert.equal(rejected?.duplicateAcknowledgementFingerprint, undefined);
    }

    const scheduled = targetAfter('Scheduled', { type: 'revokeMessage', messageId: targetId }).message;
    assert.equal(scheduled?.status, 'Needs review');
    assert.equal(scheduled?.approvedAt, undefined);
    assert.equal(scheduled?.duplicateAcknowledged, undefined);

    for (const status of ['Needs review', 'Draft', 'Blocked', 'Failed'] as const) {
      const result = targetAfter(status, { type: 'revokeMessage', messageId: targetId });
      assert.deepEqual(result.message, result.original);
      assert.match(result.next.activity[0].detail, /Only scheduled/i);
    }
  });

  it('requires retry before a blocked or failed body can be edited', () => {
    for (const status of ['Blocked', 'Failed'] as const) {
      const base = createTestState();
      const original = messageWithStatus(status);
      const state = {
        ...base,
        messages: base.messages.map(message => (message.id === targetId ? original : message))
      };
      const retried = relateReducer(state, { type: 'retryMessage', messageId: targetId });
      const edited = relateReducer(retried, {
        type: 'editMessage',
        messageId: targetId,
        body: 'A repaired message body that is ready for another explicit review.'
      });
      const message = edited.messages.find(item => item.id === targetId);

      assert.equal(message?.status, 'Needs review');
      assert.match(message?.body ?? '', /repaired message/);
      assert.equal(message?.duplicateAcknowledgementFingerprint, undefined);
    }
  });
});

describe('duplicate approval lifecycle safety', () => {
  it('blocks approval until current duplicate risk is explicitly acknowledged', () => {
    const acknowledged = acknowledgeDuplicate();
    const approved = relateReducer(acknowledged.state, {
      type: 'approveMessage',
      messageId: acknowledged.draftId
    });

    assert.equal(acknowledged.blocked.messages[0].status, 'Blocked');
    assert.match(acknowledged.blocked.messages[0].lastError ?? '', /similar message draft/i);
    assert.equal(acknowledged.state.messages[0].status, 'Needs review');
    assert.equal(acknowledged.state.messages[0].duplicateAcknowledged, true);
    assert.match(acknowledged.state.messages[0].duplicateAcknowledgementFingerprint ?? '', /^duplicate-risk-v2-/);
    assert.equal(approved.messages[0].status, 'Scheduled');
  });

  it('recomputes risk when warning metadata is absent or legacy consent lacks a fingerprint', () => {
    const generated = duplicateDraftState();
    const draftId = generated.messages[0].id;
    const stale = {
      ...generated,
      messages: generated.messages.map(message =>
        message.id === draftId
          ? {
              ...message,
              duplicateWarning: undefined,
              duplicateAcknowledged: true,
              duplicateAcknowledgementFingerprint: undefined
            }
          : message
      )
    };
    const approved = relateReducer(stale, { type: 'approveMessage', messageId: draftId });
    const message = approved.messages.find(item => item.id === draftId);

    assert.equal(message?.status, 'Blocked');
    assert.match(message?.duplicateWarning ?? '', /similar message/i);
    assert.equal(message?.duplicateAcknowledged, undefined);
    assert.equal(message?.duplicateAcknowledgementFingerprint, undefined);
  });

  it('revokes duplicate acknowledgement when message content changes', () => {
    const acknowledged = acknowledgeDuplicate();
    const before = acknowledged.state.messages.find(message => message.id === acknowledged.draftId);
    const edited = relateReducer(acknowledged.state, {
      type: 'editMessage',
      messageId: acknowledged.draftId,
      body: `${before?.body ?? ''} This intentional follow-up changes the reviewed content.`
    });
    const message = edited.messages.find(item => item.id === acknowledged.draftId);

    assert.equal(message?.status, 'Needs review');
    assert.equal(message?.duplicateAcknowledged, undefined);
    assert.equal(message?.duplicateAcknowledgementFingerprint, undefined);
    assert.match(message?.duplicateWarning ?? '', /similar message/i);
  });

  it('invalidates consent when a relevant queue entry changes after acknowledgement', () => {
    const acknowledged = acknowledgeDuplicate();
    const changedQueue = {
      ...acknowledged.state,
      messages: acknowledged.state.messages.map(message =>
        message.id === targetId
          ? {
              ...message,
              status: 'Scheduled' as const,
              approvedAt: '2026-07-10T09:00:00.000Z',
              approvalExpiresAt: '2026-07-17T09:00:00.000Z'
            }
          : message
      )
    };
    const approved = relateReducer(changedQueue, {
      type: 'approveMessage',
      messageId: acknowledged.draftId
    });
    const message = approved.messages.find(item => item.id === acknowledged.draftId);

    assert.equal(message?.status, 'Blocked');
    assert.match(message?.duplicateWarning ?? '', /already scheduled/i);
    assert.equal(message?.duplicateAcknowledged, undefined);
    assert.equal(message?.duplicateAcknowledgementFingerprint, undefined);
  });

  it('bulk approval preflight rejects a stale acknowledgement fingerprint', () => {
    const acknowledged = acknowledgeDuplicate();
    const current = acknowledged.state.messages.find(message => message.id === acknowledged.draftId);
    assert.ok(current);
    assert.equal(assessDuplicateMessageRisk(acknowledged.state, current).acknowledged, true);

    const changedQueue = {
      ...acknowledged.state,
      messages: acknowledged.state.messages.map(message =>
        message.id === targetId ? { ...message, body: `${message.body} Changed queue content.` } : message
      )
    };
    const report = buildMessageBulkActionReport(changedQueue, [acknowledged.draftId], 'Approve');
    const bulk = relateReducer(changedQueue, {
      type: 'bulkMessageAction',
      action: 'Approve',
      messageIds: [acknowledged.draftId]
    });

    assert.deepEqual(report.eligibleIds, []);
    assert.match(report.skipped[0]?.reason ?? '', /acknowledged/i);
    assert.equal(bulk.messages.find(message => message.id === acknowledged.draftId)?.status, 'Needs review');
  });
});
