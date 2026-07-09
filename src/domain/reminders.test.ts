import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import { buildReminderPlans } from './reminders';

describe('reminder planning', () => {
  it('creates upcoming reminder plans for known events', () => {
    const state = createInitialState();
    const plans = buildReminderPlans(state, [1]);

    assert.ok(plans.length > 0);
    assert.ok(plans.every(plan => plan.title.length > 0));
    assert.ok(plans.every(plan => new Date(plan.triggerAt).getTime() > Date.now()));
  });

  it('skips reminder plans that would trigger in the past', () => {
    const state = createInitialState();
    const plans = buildReminderPlans(state, [365]);

    assert.equal(plans.length, 0);
  });
});
