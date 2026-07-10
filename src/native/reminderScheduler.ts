import * as Notifications from 'expo-notifications';
import { buildReminderNotificationSchedulePlan } from '../domain/notificationScheduling';
import { buildReminderNotificationData } from '../domain/notificationRoutes';
import type { ReminderPlan } from '../domain/types';
import { reconcileReminderPlansWithAdapter } from './reminderSchedulerCore';

export interface ReminderScheduleResult {
  scheduled: number;
  skipped: number;
  cancelled: number;
  unchanged: number;
  authorization: 'authorized' | 'not-authorized';
}

const applyReminderSchedulePlan = async (
  plans: ReminderPlan[],
  authorization: ReminderScheduleResult['authorization']
): Promise<ReminderScheduleResult> => {
  const counts = await reconcileReminderPlansWithAdapter(plans, {
    getScheduledNotifications: Notifications.getAllScheduledNotificationsAsync,
    cancelScheduledNotification: Notifications.cancelScheduledNotificationAsync,
    scheduleReminder: async plan => {
      await Notifications.scheduleNotificationAsync({
        identifier: plan.id,
        content: {
          title: plan.title,
          body: plan.body,
          data: buildReminderNotificationData(plan)
        },
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.DATE,
          date: new Date(plan.triggerAt)
        }
      });
    }
  });
  return {
    ...counts,
    authorization
  };
};

const notificationPermissionIsUsable = (permission: { status?: string; granted?: boolean }) =>
  permission.granted === true || permission.status === 'granted' || permission.status === 'limited';

export const scheduleReminderPlans = async (plans: ReminderPlan[]): Promise<ReminderScheduleResult> => {
  const permission = await Notifications.requestPermissionsAsync();
  if (!notificationPermissionIsUsable(permission)) {
    throw new Error('Notification permission was not granted.');
  }
  return applyReminderSchedulePlan(plans, 'authorized');
};

/**
 * Lifecycle-safe reminder reconciliation. This path only reads authorization;
 * it never opens a system prompt. When access is denied it removes stale owned
 * reminders while leaving unrelated notifications untouched.
 */
export const reconcileReminderPlansWithoutPrompt = async (plans: ReminderPlan[]): Promise<ReminderScheduleResult> => {
  const permission = await Notifications.getPermissionsAsync();
  const authorized = notificationPermissionIsUsable(permission);
  return applyReminderSchedulePlan(authorized ? plans : [], authorized ? 'authorized' : 'not-authorized');
};

/** Cancels only notifications carrying RelateAI's owned reminder contract. */
export const cancelOwnedReminderNotifications = async (): Promise<number> => {
  const existingNotifications = await Notifications.getAllScheduledNotificationsAsync();
  const schedulePlan = buildReminderNotificationSchedulePlan([], existingNotifications);
  for (const identifier of schedulePlan.toCancel) {
    await Notifications.cancelScheduledNotificationAsync(identifier);
  }
  return schedulePlan.toCancel.length;
};
