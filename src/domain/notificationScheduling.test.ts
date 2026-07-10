import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { privacyMinimizedNotificationContent } from './notificationPlans';
import { buildReminderNotificationData } from './notificationRoutes';
import { buildReminderNotificationSchedulePlan, type ScheduledReminderNotification } from './notificationScheduling';
import type { ReminderPlan } from './types';

const futureIso = '2026-07-10T10:00:00.000Z';
const now = new Date('2026-07-09T10:00:00.000Z');

const reminderPlan = (overrides: Partial<ReminderPlan> = {}): ReminderPlan => ({
  id: 'reminder-1',
  eventId: 'event-1',
  contactId: 'contact-1',
  title: 'RelateAI reminder',
  body: 'Open RelateAI to review.',
  triggerAt: futureIso,
  ...overrides
});

const scheduledFor = (
  plan: ReminderPlan,
  overrides: Partial<ScheduledReminderNotification> = {}
): ScheduledReminderNotification => {
  const content = privacyMinimizedNotificationContent(plan);
  return {
    identifier: plan.id,
    content: {
      title: content.title,
      body: content.body,
      data: buildReminderNotificationData(plan)
    },
    trigger: {
      date: plan.triggerAt
    },
    ...overrides
  };
};

describe('notification scheduling reconciliation', () => {
  it('schedules future missing reminders and skips past reminders', () => {
    const future = reminderPlan();
    const past = reminderPlan({ id: 'reminder-past', triggerAt: '2026-07-01T10:00:00.000Z' });
    const unrelated = {
      identifier: 'other-notification',
      content: {
        title: 'Other',
        body: 'Do not touch this',
        data: { route: 'other' }
      },
      trigger: { date: futureIso }
    };
    const plan = buildReminderNotificationSchedulePlan([future, past], [unrelated], now);

    assert.deepEqual(
      plan.toSchedule.map(item => item.id),
      ['reminder-1']
    );
    assert.equal(plan.skippedPastCount, 1);
    assert.equal(plan.unchangedCount, 0);
    assert.deepEqual(plan.toCancel, []);
  });

  it('leaves unchanged RelateAI reminders scheduled without duplicate writes', () => {
    const future = reminderPlan();
    const plan = buildReminderNotificationSchedulePlan([future], [scheduledFor(future)], now);

    assert.equal(plan.toSchedule.length, 0);
    assert.equal(plan.toCancel.length, 0);
    assert.equal(plan.unchangedCount, 1);
  });

  it('replaces changed RelateAI reminders by canceling the stale request and scheduling the desired one', () => {
    const future = reminderPlan();
    const plan = buildReminderNotificationSchedulePlan(
      [future],
      [
        scheduledFor(future, {
          content: {
            title: 'Old title',
            body: privacyMinimizedNotificationContent(future).body,
            data: buildReminderNotificationData(future)
          }
        })
      ],
      now
    );

    assert.deepEqual(plan.toCancel, ['reminder-1']);
    assert.deepEqual(
      plan.toSchedule.map(item => item.id),
      ['reminder-1']
    );
    assert.equal(plan.unchangedCount, 0);
  });

  it('cancels stale RelateAI reminders without touching unrelated scheduled notifications', () => {
    const future = reminderPlan();
    const stale = reminderPlan({ id: 'reminder-stale', eventId: 'deleted-event' });
    const plan = buildReminderNotificationSchedulePlan(
      [future],
      [
        scheduledFor(stale),
        {
          identifier: 'other-notification',
          content: {
            title: 'Other',
            body: 'Do not touch this',
            data: { route: 'other', safeAction: 'open-review' }
          },
          trigger: { date: futureIso }
        }
      ],
      now
    );

    assert.deepEqual(plan.toCancel, ['reminder-stale']);
    assert.deepEqual(
      plan.toSchedule.map(item => item.id),
      ['reminder-1']
    );
  });

  it('does not overwrite an unrelated notification that collides with an owned identifier', () => {
    const future = reminderPlan();
    const plan = buildReminderNotificationSchedulePlan(
      [future],
      [
        {
          identifier: future.id,
          content: { title: 'Other app feature', data: { route: 'other' } },
          trigger: { date: future.triggerAt }
        }
      ],
      now
    );

    assert.deepEqual(plan.toCancel, []);
    assert.deepEqual(plan.toSchedule, []);
    assert.equal(plan.identifierCollisionCount, 1);
  });

  it('collapses duplicate owned requests by replacing them with one desired request', () => {
    const future = reminderPlan();
    const scheduled = scheduledFor(future);
    const plan = buildReminderNotificationSchedulePlan([future], [scheduled, scheduled], now);

    assert.deepEqual(plan.toCancel, [future.id]);
    assert.deepEqual(
      plan.toSchedule.map(item => item.id),
      [future.id]
    );
    assert.equal(plan.unchangedCount, 0);
  });
});
