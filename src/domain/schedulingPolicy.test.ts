import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildReminderPlanningResult } from './reminders';
import {
  adjustTriggerForSchedulingPolicy,
  buildSchedulingPolicySummary,
  validateBlackoutInput,
  validateQuietHours
} from './schedulingPolicy';

describe('scheduling policy contract', () => {
  it('moves reminder triggers out of overnight quiet hours', () => {
    const state = createTestState();
    const adjusted = adjustTriggerForSchedulingPolicy(new Date('2026-07-10T23:30:00'), state.settings);

    assert.equal(adjusted.triggerAt.getHours(), 8);
    assert.equal(adjusted.triggerAt.getMinutes(), 0);
    assert.ok(adjusted.adjustments.some(message => message.includes('quiet hours')));
  });

  it('moves reminders after blackout windows and reports the adjustment', () => {
    const state = createTestState();
    const result = buildReminderPlanningResult(
      {
        ...state,
        settings: {
          ...state.settings,
          quietHours: { start: '22:00', end: '08:00' },
          blackouts: [{ id: 'vacation', label: 'Vacation', startDate: '2099-01-01', endDate: '2099-01-03' }]
        },
        events: [
          {
            ...state.events[0],
            id: 'future-birthday',
            date: '2099-01-02T10:00:00.000Z',
            label: 'Future birthday'
          }
        ]
      },
      [1],
      new Date('2098-12-01T09:00:00')
    );

    assert.equal(result.plans.length, 1);
    assert.match(result.issues.map(issue => issue.detail).join(' '), /Vacation/);
    assert.ok(result.adjustedCount > 0);
    assert.equal(new Date(result.plans[0].triggerAt).getFullYear(), 2099);
    assert.equal(new Date(result.plans[0].triggerAt).getMonth(), 0);
    assert.equal(new Date(result.plans[0].triggerAt).getDate(), 4);
  });

  it('blocks notification scheduling when notifications or scheduling inputs are invalid', () => {
    const state = createTestState();
    const summary = buildSchedulingPolicySummary({
      ...state,
      settings: {
        ...state.settings,
        notificationsEnabled: false,
        quietHours: { start: '25:00', end: '08:00' }
      }
    });

    assert.equal(summary.canScheduleNotifications, false);
    assert.ok(summary.issues.some(issue => issue.id === 'notifications-disabled'));
    assert.ok(summary.issues.some(issue => issue.id === 'quiet-hours-invalid'));
  });

  it('validates quiet hours and blackout inputs before saving preferences', () => {
    assert.match(validateQuietHours({ start: '10:00', end: '10:00' }) ?? '', /different/);
    assert.equal(validateQuietHours({ start: '22:00', end: '08:00' }), undefined);

    assert.equal(validateBlackoutInput({ label: 'Trip', startDate: '2026-12-20', endDate: '2026-12-25' }).ok, true);
    assert.equal(validateBlackoutInput({ label: '', startDate: '2026-12-20', endDate: '2026-12-25' }).ok, false);
    assert.equal(validateBlackoutInput({ label: 'Trip', startDate: '2026-12-26', endDate: '2026-12-25' }).ok, false);
  });
});
