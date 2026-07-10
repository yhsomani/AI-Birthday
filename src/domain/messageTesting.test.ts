import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { MESSAGE_BODY_LIMITS } from './messageBodyPolicy';
import { buildMessageTestPlan } from './messageTesting';

describe('message test-send contract', () => {
  it('builds a safe route test without contacting the recipient', () => {
    const state = createTestState();
    const message = state.messages.find(item => item.id === 'msg-asha-bday')!;
    const plan = buildMessageTestPlan(state, message);

    assert.equal(plan.ok, true);
    assert.equal(plan.sentToRecipient, false);
    assert.match(plan.targetLabel, /SMS handoff/i);
    assert.match(plan.detail, /No message was sent/i);
    assert.doesNotMatch(JSON.stringify(plan), /\+91/);
  });

  it('blocks route tests when channel setup is unavailable', () => {
    const base = createTestState();
    const state = {
      ...base,
      settings: {
        ...base.settings,
        smsEnabled: false
      }
    };
    const message = state.messages.find(item => item.id === 'msg-asha-bday')!;
    const plan = buildMessageTestPlan(state, message);

    assert.equal(plan.ok, false);
    assert.equal(plan.sentToRecipient, false);
    assert.match(plan.issue, /SMS is disabled/i);
    assert.equal(plan.targetLabel, 'No recipient contacted');
  });

  it('blocks route tests when the message is too long for the selected channel', () => {
    const state = createTestState();
    const message = {
      ...state.messages.find(item => item.id === 'msg-asha-bday')!,
      body: 'A'.repeat(MESSAGE_BODY_LIMITS.SMS + 1)
    };
    const plan = buildMessageTestPlan(state, message);

    assert.equal(plan.ok, false);
    assert.equal(plan.sentToRecipient, false);
    assert.match(plan.issue, /Shorten the message or switch channel/i);
  });

  it('keeps SMS multipart route tests safe and visible to the user', () => {
    const state = createTestState();
    const message = {
      ...state.messages.find(item => item.id === 'msg-asha-bday')!,
      body: 'A'.repeat(170)
    };
    const plan = buildMessageTestPlan(state, message);

    assert.equal(plan.ok, true);
    if (plan.ok) {
      assert.equal(plan.sentToRecipient, false);
      assert.match(plan.detail, /2 parts/i);
      assert.match(plan.detail, /No message was sent/i);
    }
  });
});
