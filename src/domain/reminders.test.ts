import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildReminderPlanningResult, buildReminderPlans } from './reminders';

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

  it('constructs yearly reminder dates in the device calendar zone without UTC-day drift', () => {
    const originalTimeZone = process.env.TZ;
    process.env.TZ = 'Pacific/Kiritimati';
    try {
      const state = createTestState();
      state.events = [
        {
          ...state.events[0],
          date: '1990-07-20T12:00:00.000Z',
          recurrence: {
            frequency: 'Yearly',
            month: 7,
            day: 20,
            originalYear: 1990,
            leapDayPolicy: 'February 28'
          }
        }
      ];
      const result = buildReminderPlanningResult(state, [0], new Date(2026, 6, 10, 12));
      const trigger = new Date(result.plans[0].triggerAt);
      assert.equal(trigger.getFullYear(), 2026);
      assert.equal(trigger.getMonth(), 6);
      assert.equal(trigger.getDate(), 20);
      assert.equal(trigger.getHours(), 9);
    } finally {
      if (originalTimeZone === undefined) delete process.env.TZ;
      else process.env.TZ = originalTimeZone;
    }
  });
});
