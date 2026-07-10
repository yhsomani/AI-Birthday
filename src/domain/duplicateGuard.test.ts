import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { assessDuplicateMessageRisk, detectDuplicateMessageRisk, duplicateRiskFingerprint } from './duplicateGuard';

const duplicateOfFirstMessage = () => {
  const state = createTestState();
  return {
    state,
    draft: {
      ...state.messages[0],
      id: 'new-duplicate-draft',
      status: 'Needs review' as const,
      approvedAt: undefined,
      approvalExpiresAt: undefined
    }
  };
};

describe('duplicate send guardrail', () => {
  it('detects an existing draft for the same contact and event', () => {
    const { state, draft } = duplicateOfFirstMessage();
    const risk = detectDuplicateMessageRisk(state, draft);

    assert.equal(risk.risk, true);
    if (risk.risk) {
      assert.equal(risk.severity, 'Draft');
      assert.match(risk.message, /similar message draft/i);
    }
  });

  it('prioritizes sent and scheduled lifecycle risks', () => {
    const { state, draft } = duplicateOfFirstMessage();
    const scheduled = {
      ...state.messages[0],
      id: 'scheduled-duplicate',
      status: 'Scheduled' as const,
      approvedAt: '2026-07-09T09:00:00.000Z',
      approvalExpiresAt: '2026-07-16T09:00:00.000Z'
    };
    const sentState = {
      ...state,
      messages: [
        scheduled,
        ...state.messages.map(message =>
          message.id === 'msg-asha-bday'
            ? { ...message, status: 'Sent' as const, sentAt: '2026-07-10T10:00:00.000Z' }
            : message
        )
      ]
    };
    const risk = detectDuplicateMessageRisk(sentState, draft);

    assert.equal(risk.risk, true);
    if (risk.risk) {
      assert.equal(risk.severity, 'Sent');
      assert.equal(risk.matchId, 'msg-asha-bday');
      assert.match(risk.message, /already sent/i);
    }
  });

  it('scopes recurring event duplicates to one local-calendar occurrence', () => {
    const { state, draft } = duplicateOfFirstMessage();
    const event = state.events.find(item => item.id === draft.eventId);
    assert.ok(event);
    const recurringState = {
      ...state,
      events: state.events.map(item =>
        item.id === event.id
          ? {
              ...item,
              date: '1990-07-15T12:00:00.000Z',
              recurrence: {
                frequency: 'Yearly' as const,
                month: 7,
                day: 15,
                originalYear: 1990,
                leapDayPolicy: 'February 28' as const
              }
            }
          : item
      ),
      messages: state.messages.map(message =>
        message.id === 'msg-asha-bday'
          ? {
              ...message,
              status: 'Sent' as const,
              occurrenceDate: '2025-07-15',
              sentAt: '2025-07-15T08:00:00.000Z'
            }
          : message
      )
    };

    assert.deepEqual(
      detectDuplicateMessageRisk(recurringState, {
        ...draft,
        occurrenceDate: '2026-07-15'
      }),
      { risk: false }
    );
    assert.equal(
      detectDuplicateMessageRisk(recurringState, {
        ...draft,
        occurrenceDate: '2025-07-15'
      }).risk,
      true
    );
  });

  it('requires actual text similarity for eventless manual messages', () => {
    const state = createTestState();
    const prior = {
      ...state.messages[1],
      id: 'manual-prior',
      eventId: undefined,
      reason: 'Check-in' as const,
      status: 'Sent' as const,
      body: 'Congratulations on the marathon finish and your excellent training effort.',
      sentAt: '2026-07-01T09:00:00.000Z'
    };
    const draft = {
      ...prior,
      id: 'manual-current',
      status: 'Needs review' as const,
      sentAt: undefined,
      body: 'How are things going after the move? I would love to catch up soon.'
    };
    const manualState = { ...state, messages: [prior] };

    assert.deepEqual(detectDuplicateMessageRisk(manualState, draft), { risk: false });
    assert.equal(
      detectDuplicateMessageRisk(manualState, {
        ...draft,
        body: 'Congratulations on your marathon finish and all that excellent training effort.'
      }).risk,
      true
    );
  });

  it('does not invalidate eventless acknowledgement for an unrelated manual message', () => {
    const state = createTestState();
    const prior = {
      ...state.messages[1],
      id: 'manual-similar-prior',
      eventId: undefined,
      status: 'Sent' as const,
      sentAt: '2026-07-01T09:00:00.000Z',
      body: 'Congratulations on your marathon finish and all that excellent training effort.'
    };
    const draft = {
      ...prior,
      id: 'manual-similar-current',
      status: 'Needs review' as const,
      sentAt: undefined,
      body: 'Congratulations on the marathon finish and your excellent training effort.'
    };
    const manualState = { ...state, messages: [prior] };
    const risk = detectDuplicateMessageRisk(manualState, draft);
    assert.equal(risk.risk, true);
    if (!risk.risk) return;
    const acknowledged = {
      ...draft,
      duplicateAcknowledged: true,
      duplicateAcknowledgementFingerprint: duplicateRiskFingerprint(manualState, draft, risk)
    };
    const unrelated = {
      ...prior,
      id: 'manual-unrelated',
      body: 'Would you like to meet for tea next Thursday afternoon?'
    };

    assert.equal(
      assessDuplicateMessageRisk({ ...manualState, messages: [unrelated, prior] }, acknowledged).acknowledged,
      true
    );
  });

  it('keeps fingerprints stable across queue ordering and changes them with reviewed content', () => {
    const { state, draft } = duplicateOfFirstMessage();
    const risk = detectDuplicateMessageRisk(state, draft);
    assert.equal(risk.risk, true);
    if (!risk.risk) return;

    const original = duplicateRiskFingerprint(state, draft, risk);
    const reordered = { ...state, messages: [...state.messages].reverse() };
    const reorderedRisk = detectDuplicateMessageRisk(reordered, draft);
    assert.equal(reorderedRisk.risk, true);
    if (!reorderedRisk.risk) return;
    assert.equal(duplicateRiskFingerprint(reordered, draft, reorderedRisk), original);

    const changedDraft = { ...draft, body: `${draft.body} Changed.` };
    const changedRisk = detectDuplicateMessageRisk(state, changedDraft);
    assert.equal(changedRisk.risk, true);
    if (!changedRisk.risk) return;
    assert.notEqual(duplicateRiskFingerprint(state, changedDraft, changedRisk), original);
  });

  it('accepts only an acknowledgement bound to the current risk fingerprint', () => {
    const { state, draft } = duplicateOfFirstMessage();
    const risk = detectDuplicateMessageRisk(state, draft);
    assert.equal(risk.risk, true);
    if (!risk.risk) return;
    const fingerprint = duplicateRiskFingerprint(state, draft, risk);

    assert.equal(
      assessDuplicateMessageRisk(state, {
        ...draft,
        duplicateAcknowledged: true,
        duplicateAcknowledgementFingerprint: fingerprint
      }).acknowledged,
      true
    );
    assert.equal(
      assessDuplicateMessageRisk(state, {
        ...draft,
        duplicateAcknowledged: true,
        duplicateAcknowledgementFingerprint: 'stale-fingerprint'
      }).acknowledged,
      false
    );
  });
});
