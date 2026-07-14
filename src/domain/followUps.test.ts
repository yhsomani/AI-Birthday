import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildMessageFollowUpPlan } from './followUps';

const stateWithSentMiraMessage = () => {
  const state = createTestState();
  return {
    ...state,
    messages: state.messages.map(message =>
      message.id === 'msg-mira-checkin'
        ? { ...message, status: 'Sent' as const, sentAt: '2026-07-09T10:00:00.000Z' }
        : message
    )
  };
};

describe('post-send follow-up contract', () => {
  it('creates a reviewable follow-up event and reminder after a sent message', () => {
    const sentState = stateWithSentMiraMessage();
    const result = buildMessageFollowUpPlan(sentState, 'msg-mira-checkin', 1, '2026-07-09T12:00:00.000Z');

    assert.equal(result.ok, true);
    if (result.ok) {
      assert.equal(result.event.type, 'Follow-up');
      assert.equal(result.event.date, '2026-07-10T09:00:00.000Z');
      assert.equal(result.event.source, 'Manual');
      assert.equal(result.event.verified, true);
      assert.match(result.reminderPlan.body, /Review before sending/i);
    }
  });

  it('blocks follow-up scheduling before the user has sent the message', () => {
    const result = buildMessageFollowUpPlan(createTestState(), 'msg-asha-bday', 7, '2026-07-09T12:00:00.000Z');

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.match(result.reason, /after a message is sent/i);
    }
  });

  it('adds one follow-up through the reducer and prevents duplicate same-day reminders', () => {
    const sentState = stateWithSentMiraMessage();
    const scheduled = relateReducer(sentState, {
      type: 'scheduleMessageFollowUp',
      messageId: 'msg-mira-checkin',
      delayDays: 7,
      nowIso: '2026-07-10T12:00:00.000Z'
    });
    const duplicate = relateReducer(scheduled, {
      type: 'scheduleMessageFollowUp',
      messageId: 'msg-mira-checkin',
      delayDays: 7,
      nowIso: '2026-07-10T12:00:00.000Z'
    });

    assert.equal(scheduled.events.length, sentState.events.length + 1);
    assert.equal(scheduled.reminderPlans.length, sentState.reminderPlans.length + 1);
    assert.equal(duplicate.events.length, scheduled.events.length);
    assert.match(duplicate.activity[0].detail, /already exists/i);
  });
});
