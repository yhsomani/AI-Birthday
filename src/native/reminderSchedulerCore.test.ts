import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildReminderNotificationData } from '../domain/notificationRoutes';
import type { ReminderPlan } from '../domain/types';
import { reconcileReminderPlansWithAdapter } from './reminderSchedulerCore';

const desired: ReminderPlan = {
  id: 'reminder-event-1-1',
  eventId: 'event-1',
  contactId: 'contact-1',
  title: 'RelateAI reminder',
  body: 'Open RelateAI to review.',
  triggerAt: '2026-07-20T09:00:00.000Z'
};

describe('executable reminder scheduler adapter', () => {
  it('cancels only stale owned reminders and schedules the desired diff', async () => {
    const cancelled: string[] = [];
    const scheduled: string[] = [];

    const result = await reconcileReminderPlansWithAdapter(
      [desired],
      {
        getScheduledNotifications: async () => [
          {
            identifier: 'stale-owned',
            content: {
              title: 'RelateAI reminder',
              body: 'Old',
              data: {
                route: 'event-reminder',
                safeAction: 'open-review',
                eventId: 'old-event',
                contactId: 'old-contact'
              }
            },
            trigger: '2026-07-15T09:00:00.000Z'
          },
          {
            identifier: 'unrelated-app',
            content: { data: { route: 'somewhere-else' } },
            trigger: '2026-07-15T09:00:00.000Z'
          }
        ],
        cancelScheduledNotification: async identifier => {
          cancelled.push(identifier);
        },
        scheduleReminder: async plan => {
          scheduled.push(plan.id);
        }
      },
      new Date('2026-07-10T00:00:00.000Z')
    );

    assert.deepEqual(cancelled, ['stale-owned']);
    assert.deepEqual(scheduled, [desired.id]);
    assert.deepEqual(result, {
      scheduled: 1,
      skipped: 0,
      cancelled: 1,
      unchanged: 0
    });
  });

  it('leaves an exact owned reminder unchanged', async () => {
    let mutations = 0;
    const result = await reconcileReminderPlansWithAdapter(
      [desired],
      {
        getScheduledNotifications: async () => [
          {
            identifier: desired.id,
            content: {
              title: desired.title,
              body: desired.body,
              data: buildReminderNotificationData(desired)
            },
            trigger: desired.triggerAt
          }
        ],
        cancelScheduledNotification: async () => {
          mutations += 1;
        },
        scheduleReminder: async () => {
          mutations += 1;
        }
      },
      new Date('2026-07-10T00:00:00.000Z')
    );

    assert.equal(mutations, 0);
    assert.equal(result.unchanged, 1);
  });
});
