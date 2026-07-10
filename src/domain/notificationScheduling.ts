import { buildReminderNotificationData } from './notificationRoutes';
import type { ReminderPlan } from './types';

export interface ScheduledReminderNotification {
  identifier: string;
  content?: {
    title?: string | null;
    body?: string | null;
    data?: Record<string, unknown> | null;
  };
  trigger?: unknown;
}

export interface ReminderNotificationSchedulePlan {
  toSchedule: ReminderPlan[];
  toCancel: string[];
  unchangedCount: number;
  skippedPastCount: number;
}

const notificationDataMatches = (
  scheduledData: Record<string, unknown> | null | undefined,
  plan: ReminderPlan
) => {
  const desiredData = buildReminderNotificationData(plan);
  return (
    scheduledData?.route === desiredData.route &&
    scheduledData.safeAction === desiredData.safeAction &&
    scheduledData.eventId === desiredData.eventId &&
    scheduledData.contactId === desiredData.contactId &&
    scheduledData.url === desiredData.url
  );
};

const triggerTimeFor = (trigger: unknown): number => {
  if (trigger instanceof Date || typeof trigger === 'string' || typeof trigger === 'number') {
    return new Date(trigger).getTime();
  }
  if (!trigger || typeof trigger !== 'object') {
    return Number.NaN;
  }
  const value = trigger as { date?: unknown; value?: unknown; timestamp?: unknown };
  if (value.date instanceof Date || typeof value.date === 'string' || typeof value.date === 'number') {
    return new Date(value.date).getTime();
  }
  if (value.value instanceof Date || typeof value.value === 'string' || typeof value.value === 'number') {
    return new Date(value.value).getTime();
  }
  if (value.timestamp instanceof Date || typeof value.timestamp === 'string' || typeof value.timestamp === 'number') {
    return new Date(value.timestamp).getTime();
  }
  return Number.NaN;
};

const isRelateAiReminderNotification = (notification: ScheduledReminderNotification) =>
  notification.content?.data?.route === 'event-reminder' &&
  notification.content.data.safeAction === 'open-review';

const reminderNotificationMatchesPlan = (
  notification: ScheduledReminderNotification,
  plan: ReminderPlan
) =>
  notification.content?.title === plan.title &&
  notification.content?.body === plan.body &&
  notificationDataMatches(notification.content?.data, plan) &&
  triggerTimeFor(notification.trigger) === new Date(plan.triggerAt).getTime();

export const buildReminderNotificationSchedulePlan = (
  plans: ReminderPlan[],
  scheduledNotifications: ScheduledReminderNotification[],
  now: Date = new Date()
): ReminderNotificationSchedulePlan => {
  const toSchedule: ReminderPlan[] = [];
  const toCancel = new Set<string>();
  let unchangedCount = 0;
  let skippedPastCount = 0;

  const futurePlans = plans.filter(plan => {
    const triggerAt = new Date(plan.triggerAt).getTime();
    if (Number.isNaN(triggerAt) || triggerAt <= now.getTime()) {
      skippedPastCount += 1;
      return false;
    }
    return true;
  });
  const desiredIds = new Set(futurePlans.map(plan => plan.id));

  for (const plan of futurePlans) {
    const matches = scheduledNotifications.filter(notification => notification.identifier === plan.id);
    const [primary, ...duplicates] = matches;
    duplicates.forEach(duplicate => {
      if (isRelateAiReminderNotification(duplicate)) {
        toCancel.add(duplicate.identifier);
      }
    });

    if (!primary) {
      toSchedule.push(plan);
      continue;
    }

    if (!reminderNotificationMatchesPlan(primary, plan)) {
      if (isRelateAiReminderNotification(primary)) {
        toCancel.add(primary.identifier);
      }
      toSchedule.push(plan);
    } else {
      unchangedCount += 1;
    }
  }

  scheduledNotifications.forEach(notification => {
    if (desiredIds.has(notification.identifier)) {
      return;
    }
    if (isRelateAiReminderNotification(notification)) {
      toCancel.add(notification.identifier);
    }
  });

  return {
    toSchedule,
    toCancel: [...toCancel],
    unchangedCount,
    skippedPastCount
  };
};
