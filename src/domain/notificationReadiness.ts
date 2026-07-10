import { buildReminderNotificationData, readNotificationRouteUrl } from './notificationRoutes';
import type { AppState, Screen } from './types';

export type NotificationReadinessStatus = 'Ready' | 'Warning' | 'Blocked';
export type NotificationReadinessSeverity = 'Info' | 'Warning' | 'Error';

export interface NotificationReadinessIssue {
  id: string;
  severity: NotificationReadinessSeverity;
  title: string;
  detail: string;
  actionLabel: string;
  targetScreen: Screen;
}

export interface NotificationReadinessReport {
  status: NotificationReadinessStatus;
  summary: string;
  schedulableCount: number;
  safeRouteCount: number;
  staleRouteCount: number;
  issues: NotificationReadinessIssue[];
  privacyNote: string;
}

const addIssue = (issues: NotificationReadinessIssue[], issue: NotificationReadinessIssue) => {
  issues.push(issue);
};

const compactSensitiveValues = (state: AppState) =>
  [
    ...state.contacts.flatMap(contact => [contact.name, contact.phone, contact.email]),
    ...state.messages.map(message => message.body),
    ...state.memories.filter(memory => memory.category === 'Private').map(memory => memory.body)
  ]
    .map(value => value?.trim())
    .filter((value): value is string => Boolean(value && value.length >= 4));

const payloadTextFor = (plan: AppState['reminderPlans'][number]) =>
  `${plan.title} ${plan.body} ${JSON.stringify(buildReminderNotificationData(plan))}`;

export const buildNotificationReadinessReport = (state: AppState, now = new Date()): NotificationReadinessReport => {
  const issues: NotificationReadinessIssue[] = [];
  const notificationDecision = state.privacy.permissionDecisions.Notifications;
  const notificationAuthorization = state.privacy.permissionRecords?.Notifications?.systemAuthorization;
  const sensitiveValues = compactSensitiveValues(state);
  let safeRouteCount = 0;
  let staleRouteCount = 0;
  let schedulableCount = 0;

  if (!state.settings.notificationsEnabled) {
    addIssue(issues, {
      id: 'notifications-disabled',
      severity: 'Warning',
      title: 'Notifications are off',
      detail: 'Reminder plans stay visible in Home, Events, and Messages until notifications are enabled.',
      actionLabel: 'Open settings',
      targetScreen: 'more'
    });
  }

  if (notificationAuthorization === 'restricted') {
    addIssue(issues, {
      id: 'notification-permission-restricted',
      severity: 'Warning',
      title: 'Notifications are restricted by device policy',
      detail:
        'RelateAI cannot change this restriction. Reminder work remains visible in-app; review device or managed-device settings.',
      actionLabel: 'Open privacy settings',
      targetScreen: 'more'
    });
  } else if (notificationAuthorization === 'unavailable') {
    addIssue(issues, {
      id: 'notification-permission-query-unavailable',
      severity: 'Warning',
      title: 'Notification status could not be checked',
      detail: 'The app will not change owned native schedules until live permission status can be read again.',
      actionLabel: 'Open privacy settings',
      targetScreen: 'more'
    });
  } else if (notificationDecision === 'Denied' || notificationDecision === 'Unavailable') {
    addIssue(issues, {
      id: 'notification-permission-blocked',
      severity: 'Warning',
      title: 'Notification permission unavailable',
      detail: 'The app will keep reminder and approval work visible in-app until permission is granted again.',
      actionLabel: 'Open privacy settings',
      targetScreen: 'more'
    });
  } else if (notificationDecision === 'Not requested') {
    addIssue(issues, {
      id: 'notification-permission-not-reviewed',
      severity: 'Warning',
      title: 'Notification permission not reviewed',
      detail:
        'Ask for notification permission only after explaining that reminders open review surfaces and never send messages.',
      actionLabel: 'Open privacy settings',
      targetScreen: 'more'
    });
  }

  if (state.reminderPlans.length === 0 && state.events.length > 0) {
    addIssue(issues, {
      id: 'reminders-not-planned',
      severity: 'Warning',
      title: 'Reminder plans are not prepared',
      detail: 'Plan reminders so upcoming events can be scheduled with safe notification routes.',
      actionLabel: 'Plan reminders',
      targetScreen: 'more'
    });
  }

  state.reminderPlans.forEach(plan => {
    const event = state.events.find(item => item.id === plan.eventId);
    const contact = state.contacts.find(item => item.id === plan.contactId);
    const triggerAt = new Date(plan.triggerAt);
    const notificationData = buildReminderNotificationData(plan);
    const routeUrl = readNotificationRouteUrl(notificationData);
    const payload = payloadTextFor(plan);

    if (!event || !contact) {
      staleRouteCount += 1;
      addIssue(issues, {
        id: `stale-route-${plan.id}`,
        severity: 'Error',
        title: 'Reminder route is stale',
        detail: 'A reminder references a missing contact or event. Re-plan reminders before device scheduling.',
        actionLabel: 'Plan reminders',
        targetScreen: 'more'
      });
      return;
    }

    if (!routeUrl) {
      addIssue(issues, {
        id: `unsafe-route-${plan.id}`,
        severity: 'Error',
        title: 'Reminder route is not safe',
        detail: 'Notification actions must only open review surfaces and must never send or mutate data directly.',
        actionLabel: 'Plan reminders',
        targetScreen: 'more'
      });
    } else {
      safeRouteCount += 1;
    }

    if (Number.isNaN(triggerAt.getTime())) {
      addIssue(issues, {
        id: `invalid-trigger-${plan.id}`,
        severity: 'Error',
        title: 'Reminder time is invalid',
        detail: 'A reminder has an unreadable trigger time. Re-plan reminders before scheduling.',
        actionLabel: 'Plan reminders',
        targetScreen: 'more'
      });
    } else if (triggerAt.getTime() <= now.getTime()) {
      addIssue(issues, {
        id: `past-trigger-${plan.id}`,
        severity: 'Warning',
        title: 'Reminder time has passed',
        detail: 'A reminder trigger is already in the past and will be skipped by native scheduling.',
        actionLabel: 'Plan reminders',
        targetScreen: 'more'
      });
    } else {
      schedulableCount += 1;
    }

    if (sensitiveValues.some(value => payload.includes(value))) {
      addIssue(issues, {
        id: `sensitive-payload-${plan.id}`,
        severity: 'Error',
        title: 'Notification payload exposes private data',
        detail:
          'Notification title, body, and route data must avoid contact names, phone numbers, emails, private notes, and message bodies.',
        actionLabel: 'Plan reminders',
        targetScreen: 'more'
      });
    }
  });

  const hasError = issues.some(issue => issue.severity === 'Error');
  const hasWarning = issues.some(issue => issue.severity === 'Warning');
  const status: NotificationReadinessStatus = hasError ? 'Blocked' : hasWarning ? 'Warning' : 'Ready';
  const summary =
    status === 'Blocked'
      ? `Notification readiness is blocked by ${issues.filter(issue => issue.severity === 'Error').length} issue(s).`
      : status === 'Warning'
        ? `Notification readiness needs review: ${schedulableCount} schedulable reminder(s), ${issues.filter(issue => issue.severity === 'Warning').length} warning(s).`
        : schedulableCount > 0
          ? `Notification readiness is ready with ${schedulableCount} safe reminder notification(s).`
          : 'No reminder notifications are currently needed.';

  return {
    status,
    summary,
    schedulableCount,
    safeRouteCount,
    staleRouteCount,
    issues,
    privacyNote:
      'Notification payloads open review routes and must avoid message bodies, contact routes, private notes, credentials, and direct send actions.'
  };
};
