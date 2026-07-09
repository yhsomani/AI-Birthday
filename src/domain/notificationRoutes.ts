import type { ReminderPlan } from './types';

export type ReminderNotificationData = {
  route: 'event-reminder';
  safeAction: 'open-review';
  eventId: string;
  contactId: string;
  url: string;
};

export const buildReminderNotificationData = (plan: ReminderPlan): ReminderNotificationData => ({
  route: 'event-reminder',
  safeAction: 'open-review',
  eventId: plan.eventId,
  contactId: plan.contactId,
  url: `relateai://events?eventId=${encodeURIComponent(plan.eventId)}&contactId=${encodeURIComponent(plan.contactId)}`
});

export const readNotificationRouteUrl = (data: Record<string, unknown> | undefined): string | undefined => {
  if (!data || data.safeAction !== 'open-review' || typeof data.url !== 'string') {
    return undefined;
  }
  return data.url;
};
