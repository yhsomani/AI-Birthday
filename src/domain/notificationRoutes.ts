import {
  notificationKindForPlan,
  type OwnedNotificationPlan,
  type OwnedNotificationPlanKind
} from './notificationPlans';

type OwnedNotificationDataBase = {
  owner: 'relateai';
  contractVersion: 1;
  route: OwnedNotificationPlanKind;
  safeAction: 'open-review';
};

export type ReminderNotificationData = OwnedNotificationDataBase & {
  route: 'event-reminder';
  eventId: string;
  contactId: string;
};

export type OwnedNotificationData =
  | ReminderNotificationData
  | (OwnedNotificationDataBase & {
      route: 'pending-approval' | 'fallback-review';
      messageId: string;
    })
  | (OwnedNotificationDataBase & {
      route: 'recovery-issue';
      messageId?: string;
    })
  | (OwnedNotificationDataBase & {
      route: 'check-in-suggestion';
      contactId: string;
    })
  | (OwnedNotificationDataBase & {
      route: 'setup-blocker';
    });

const baseData = (route: OwnedNotificationPlanKind): OwnedNotificationDataBase => ({
  owner: 'relateai',
  contractVersion: 1,
  route,
  safeAction: 'open-review'
});

export const buildReminderNotificationData = (plan: OwnedNotificationPlan): OwnedNotificationData => {
  const route = notificationKindForPlan(plan);
  switch (route) {
    case 'event-reminder':
      return {
        ...baseData(route),
        route,
        eventId: plan.eventId ?? '',
        contactId: plan.contactId ?? ''
      };
    case 'pending-approval':
    case 'fallback-review':
      return {
        ...baseData(route),
        route,
        messageId: plan.messageId ?? ''
      };
    case 'recovery-issue':
      return {
        ...baseData(route),
        route,
        ...(plan.messageId ? { messageId: plan.messageId } : {})
      };
    case 'check-in-suggestion':
      return {
        ...baseData(route),
        route,
        contactId: plan.contactId ?? ''
      };
    case 'setup-blocker':
      return {
        ...baseData(route),
        route
      };
  }
};

const safeReference = (value: unknown): value is string =>
  typeof value === 'string' && value.length > 0 && value.length <= 256 && !/[\u0000-\u001f\u007f]/.test(value);

/** Reconstructs canonical review URLs and ignores caller-provided URL data. */
export const readNotificationRouteUrl = (data: Record<string, unknown> | undefined): string | undefined => {
  if (!data || data.safeAction !== 'open-review') return undefined;
  switch (data.route) {
    case 'event-reminder':
      return safeReference(data.eventId) && safeReference(data.contactId)
        ? `relateai://events?eventId=${encodeURIComponent(data.eventId)}&contactId=${encodeURIComponent(data.contactId)}`
        : undefined;
    case 'pending-approval':
    case 'fallback-review':
      return safeReference(data.messageId) ? `relateai://message/${encodeURIComponent(data.messageId)}` : undefined;
    case 'recovery-issue':
      if (data.messageId === undefined) return 'relateai://setup';
      return safeReference(data.messageId) ? `relateai://message/${encodeURIComponent(data.messageId)}` : undefined;
    case 'check-in-suggestion':
      return safeReference(data.contactId) ? `relateai://contact/${encodeURIComponent(data.contactId)}` : undefined;
    case 'setup-blocker':
      return 'relateai://setup';
    default:
      return undefined;
  }
};

const knownOwnedRoute = (value: unknown): value is OwnedNotificationPlanKind =>
  value === 'event-reminder' ||
  value === 'pending-approval' ||
  value === 'fallback-review' ||
  value === 'setup-blocker' ||
  value === 'recovery-issue' ||
  value === 'check-in-suggestion';

/**
 * Ownership is explicit for current notifications. The narrow legacy branch
 * permits cleanup of event reminders created before the owner marker existed.
 */
export const isOwnedRelateAiNotificationData = (
  data: Record<string, unknown> | null | undefined,
  identifier?: string
) => {
  if (!data || data.safeAction !== 'open-review' || !knownOwnedRoute(data.route)) return false;
  if (data.owner === 'relateai' && data.contractVersion === 1) return true;
  return data.route === 'event-reminder' && Boolean(identifier?.startsWith('reminder-'));
};
