import * as Notifications from 'expo-notifications';
import { buildReminderNotificationData } from '../domain/notificationRoutes';
import type { ReminderPlan } from '../domain/types';

export interface ReminderScheduleResult {
  scheduled: number;
  skipped: number;
}

export const scheduleReminderPlans = async (plans: ReminderPlan[]): Promise<ReminderScheduleResult> => {
  const permission = await Notifications.requestPermissionsAsync();
  if (permission.status !== 'granted') {
    throw new Error('Notification permission was not granted.');
  }

  await Notifications.cancelAllScheduledNotificationsAsync();

  let scheduled = 0;
  let skipped = 0;
  for (const plan of plans) {
    const triggerDate = new Date(plan.triggerAt);
    if (triggerDate.getTime() <= Date.now()) {
      skipped += 1;
      continue;
    }
    await Notifications.scheduleNotificationAsync({
      identifier: plan.id,
      content: {
        title: plan.title,
        body: plan.body,
        data: buildReminderNotificationData(plan)
      },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.DATE,
        date: triggerDate
      }
    });
    scheduled += 1;
  }

  return { scheduled, skipped };
};
