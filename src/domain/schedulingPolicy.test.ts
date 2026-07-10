import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildReminderPlanningResult } from './reminders';
import {
  adjustTriggerForSchedulingPolicy,
  buildSchedulingPolicySummary,
  currentScheduleTimeZone,
  messageDispatchTimingIssue,
  normalizeScheduleTimeZone,
  scheduleMessageForEvent,
  scheduleTimeZonesMatch,
  validateBlackoutInput,
  validateDefaultSendTime,
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

  it('resolves long chains of valid defer blackouts without falling through inside a blocked day', () => {
    const state = createTestState();
    const blackouts = Array.from({ length: 12 }, (_, index) => {
      const day = String(index + 1).padStart(2, '0');
      return {
        id: `defer-${day}`,
        label: `Deferred day ${day}`,
        startDate: `2027-01-${day}`,
        endDate: `2027-01-${day}`,
        behavior: 'Defer' as const
      };
    });

    const adjusted = adjustTriggerForSchedulingPolicy(new Date(2027, 0, 1, 9), {
      ...state.settings,
      blackouts
    });

    assert.equal(adjusted.blockedBy, undefined);
    assert.equal(adjusted.triggerAt.getFullYear(), 2027);
    assert.equal(adjusted.triggerAt.getMonth(), 0);
    assert.equal(adjusted.triggerAt.getDate(), 13);
    assert.equal(adjusted.adjustments.length, 12);
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
    assert.equal(validateDefaultSendTime('09:30'), undefined);
    assert.match(validateDefaultSendTime('25:00') ?? '', /HH:mm/);

    assert.equal(validateBlackoutInput({ label: 'Trip', startDate: '2026-12-20', endDate: '2026-12-25' }).ok, true);
    assert.equal(validateBlackoutInput({ label: '', startDate: '2026-12-20', endDate: '2026-12-25' }).ok, false);
    assert.equal(validateBlackoutInput({ label: 'Trip', startDate: '2026-12-26', endDate: '2026-12-25' }).ok, false);
    assert.equal(
      validateBlackoutInput({
        label: 'No work email',
        startDate: '2026-12-20',
        endDate: '2026-12-25',
        behavior: 'Block',
        channels: ['Email']
      }).ok,
      true
    );
  });

  it('uses the next local yearly occurrence, default send time, and channel-specific blackout', () => {
    const state = createTestState();
    const event = {
      ...state.events[0],
      date: '1990-07-20T12:00:00.000Z',
      recurrence: {
        frequency: 'Yearly' as const,
        month: 7,
        day: 20,
        originalYear: 1990,
        leapDayPolicy: 'February 28' as const
      }
    };
    const settings = {
      ...state.settings,
      defaultSendTime: '09:30',
      quietHours: { start: '22:00', end: '08:00' },
      blackouts: [
        {
          id: 'email-only',
          label: 'Email pause',
          startDate: '2026-07-20',
          endDate: '2026-07-20',
          behavior: 'Block' as const,
          channels: ['Email' as const]
        }
      ]
    };
    const email = scheduleMessageForEvent(event, settings, 'Email', new Date(2026, 6, 10, 12));
    const sms = scheduleMessageForEvent(event, settings, 'SMS', new Date(2026, 6, 10, 12));
    assert.match(email.issue ?? '', /Email pause/);
    assert.equal(sms.scheduledTimeZone, currentScheduleTimeZone());
    assert.equal(new Date(sms.scheduledFor ?? '').getFullYear(), 2026);
    assert.equal(new Date(sms.scheduledFor ?? '').getMonth(), 6);
    assert.equal(new Date(sms.scheduledFor ?? '').getDate(), 20);
    assert.equal(new Date(sms.scheduledFor ?? '').getHours(), 9);
    assert.equal(new Date(sms.scheduledFor ?? '').getMinutes(), 30);
  });

  it('uses contact send time while keeping global quiet hours authoritative', () => {
    const state = createTestState();
    const event = {
      ...state.events[0],
      date: '1990-07-20T12:00:00.000Z',
      recurrence: {
        frequency: 'Yearly' as const,
        month: 7,
        day: 20,
        originalYear: 1990,
        leapDayPolicy: 'February 28' as const
      }
    };
    const settings = {
      ...state.settings,
      quietHours: { start: '22:00', end: '08:00' }
    };
    const reference = new Date(2026, 6, 10, 12);

    const custom = scheduleMessageForEvent(event, settings, 'SMS', reference, {
      customSendTime: '18:45',
      quietHoursBehavior: 'Defer'
    });
    assert.equal(custom.issue, undefined);
    assert.equal(new Date(custom.scheduledFor ?? '').getHours(), 18);
    assert.equal(new Date(custom.scheduledFor ?? '').getMinutes(), 45);

    const deferred = scheduleMessageForEvent(event, settings, 'SMS', reference, {
      customSendTime: '23:15',
      quietHoursBehavior: 'Defer'
    });
    assert.equal(deferred.issue, undefined);
    assert.equal(new Date(deferred.scheduledFor ?? '').getHours(), 8);
    assert.match(deferred.adjustments.join(' '), /quiet hours/i);

    const blocked = scheduleMessageForEvent(event, settings, 'SMS', reference, {
      customSendTime: '23:15',
      quietHoursBehavior: 'Block'
    });
    assert.match(blocked.issue ?? '', /contact quiet-hours preference blocks/i);
  });

  it('rechecks due time, quiet hours, and blackouts at the actual dispatch moment', () => {
    const state = createTestState();
    const message = {
      ...state.messages[0],
      status: 'Scheduled' as const,
      scheduledFor: '2026-07-20T09:00:00.000Z',
      scheduledTimeZone: currentScheduleTimeZone()
    };
    assert.match(messageDispatchTimingIssue(state, message, new Date('2026-07-19T09:00:00.000Z')) ?? '', /not due/i);

    const quietState = {
      ...state,
      settings: { ...state.settings, quietHours: { start: '22:00', end: '08:00' } }
    };
    assert.match(
      messageDispatchTimingIssue(
        quietState,
        { ...message, scheduledFor: '2026-07-18T09:00:00.000Z' },
        new Date('2026-07-20T23:00:00')
      ) ?? '',
      /deferred/i
    );

    quietState.contacts[0] = { ...quietState.contacts[0], quietHoursBehavior: 'Block' };
    assert.match(
      messageDispatchTimingIssue(
        quietState,
        { ...message, contactId: quietState.contacts[0].id, scheduledFor: undefined },
        new Date('2026-07-20T23:00:00')
      ) ?? '',
      /contact quiet-hours preference blocks/i
    );
  });

  it('fails dispatch closed when an identified scheduled instant belongs to another device time zone', () => {
    const state = createTestState();
    const currentTimeZone = currentScheduleTimeZone();
    const otherTimeZone = scheduleTimeZonesMatch(currentTimeZone, 'UTC') ? 'America/New_York' : 'UTC';
    const scheduled = {
      ...state.messages[0],
      status: 'Scheduled' as const,
      scheduledFor: '2026-07-10T08:00:00.000Z'
    };

    assert.match(
      messageDispatchTimingIssue(
        state,
        { ...scheduled, scheduledTimeZone: otherTimeZone },
        new Date('2026-07-10T09:00:00.000Z')
      ) ?? '',
      /device time zone changed.*review/i
    );
    assert.equal(normalizeScheduleTimeZone('Not/A-Time-Zone'), undefined);
  });
});
