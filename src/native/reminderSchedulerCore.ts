import { buildReminderNotificationSchedulePlan } from '../domain/notificationScheduling';
import type { ScheduledReminderNotification } from '../domain/notificationScheduling';
import type { ReminderPlan } from '../domain/types';

export interface ReminderSchedulerAdapter {
  getScheduledNotifications(): Promise<ScheduledReminderNotification[]>;
  cancelScheduledNotification(identifier: string): Promise<void>;
  scheduleReminder(plan: ReminderPlan): Promise<void>;
}

export interface ReminderScheduleCounts {
  scheduled: number;
  skipped: number;
  cancelled: number;
  unchanged: number;
}

/** Applies the pure desired-vs-actual diff and never requests permission. */
export const reconcileReminderPlansWithAdapter = async (
  plans: ReminderPlan[],
  adapter: ReminderSchedulerAdapter,
  now: Date = new Date()
): Promise<ReminderScheduleCounts> => {
  const existingNotifications = await adapter.getScheduledNotifications();
  const schedulePlan = buildReminderNotificationSchedulePlan(plans, existingNotifications, now);

  for (const identifier of schedulePlan.toCancel) {
    await adapter.cancelScheduledNotification(identifier);
  }

  for (const plan of schedulePlan.toSchedule) {
    await adapter.scheduleReminder(plan);
  }

  return {
    scheduled: schedulePlan.toSchedule.length,
    skipped: schedulePlan.skippedPastCount,
    cancelled: schedulePlan.toCancel.length,
    unchanged: schedulePlan.unchangedCount
  };
};
