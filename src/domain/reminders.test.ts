import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildReminderPlans } from './reminders';

describe('reminder planning', () => {
  it('creates upcoming reminder plans for known events', () => {
    const state = createTestState();
    const plans = buildReminderPlans(state, [1]);
    const serializedPlans = JSON.stringify(plans);

    assert.ok(plans.length > 0);
    assert.ok(plans.every(plan => plan.title.length > 0));
    assert.ok(plans.every(plan => new Date(plan.triggerAt).getTime() > Date.now()));
    state.contacts.forEach(contact => {
      assert.equal(serializedPlans.includes(contact.name), false);
      if (contact.phone) assert.equal(serializedPlans.includes(contact.phone), false);
      if (contact.email) assert.equal(serializedPlans.includes(contact.email), false);
    });
  });

  it('skips reminder plans that would trigger in the past', () => {
    const state = createTestState();
    const plans = buildReminderPlans(state, [365]);

    assert.equal(plans.length, 0);
  });
});
