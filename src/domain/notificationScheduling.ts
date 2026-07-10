import {
  privacyMinimizedNotificationContent,
  validateOwnedNotificationPlanStructure,
  type OwnedNotificationPlan
} from './notificationPlans';
import { buildReminderNotificationData, isOwnedRelateAiNotificationData } from './notificationRoutes';

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
  toSchedule: OwnedNotificationPlan[];
  toCancel: string[];
  unchangedCount: number;
  skippedPastCount: number;
  skippedUnsafeCount: number;
  identifierCollisionCount: number;
}

const notificationDataMatches = (
  scheduledData: Record<string, unknown> | null | undefined,
  plan: OwnedNotificationPlan
) => {
  const desiredData = buildReminderNotificationData(plan);
  if (!scheduledData) return false;
  const desiredEntries = Object.entries(desiredData);
  return (
    Object.keys(scheduledData).length === desiredEntries.length &&
    desiredEntries.every(([key, value]) => scheduledData[key] === value)
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
  isOwnedRelateAiNotificationData(notification.content?.data, notification.identifier);

const reminderNotificationMatchesPlan = (notification: ScheduledReminderNotification, plan: OwnedNotificationPlan) => {
  const content = privacyMinimizedNotificationContent(plan);
  return (
    notification.content?.title === content.title &&
    notification.content?.body === content.body &&
    notificationDataMatches(notification.content?.data, plan) &&
    triggerTimeFor(notification.trigger) === new Date(plan.triggerAt).getTime()
  );
};

export const buildReminderNotificationSchedulePlan = (
  plans: OwnedNotificationPlan[],
  scheduledNotifications: ScheduledReminderNotification[],
  now: Date = new Date()
): ReminderNotificationSchedulePlan => {
  const toSchedule: OwnedNotificationPlan[] = [];
  const toCancel = new Set<string>();
  let unchangedCount = 0;
  let skippedPastCount = 0;
  let skippedUnsafeCount = 0;
  let identifierCollisionCount = 0;

  const futurePlans = plans.filter(plan => {
    const triggerAt = new Date(plan.triggerAt).getTime();
    if (Number.isNaN(triggerAt) || triggerAt <= now.getTime()) {
      skippedPastCount += 1;
      return false;
    }
    if (!validateOwnedNotificationPlanStructure(plan).ok) {
      skippedUnsafeCount += 1;
      return false;
    }
    return true;
  });
  const desiredIds = new Set(futurePlans.map(plan => plan.id));

  for (const plan of futurePlans) {
    const matches = scheduledNotifications.filter(notification => notification.identifier === plan.id);
    const [primary] = matches;

    if (!primary) {
      toSchedule.push(plan);
      continue;
    }

    if (matches.some(notification => !isRelateAiReminderNotification(notification))) {
      identifierCollisionCount += 1;
      continue;
    }

    if (matches.length > 1) {
      toCancel.add(plan.id);
      toSchedule.push(plan);
      continue;
    }

    if (!reminderNotificationMatchesPlan(primary, plan)) {
      toCancel.add(primary.identifier);
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
    skippedPastCount,
    skippedUnsafeCount,
    identifierCollisionCount
  };
};
