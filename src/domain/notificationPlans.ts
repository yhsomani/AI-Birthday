import { t } from '../i18n/i18n';
import { buildCheckInReminderQueue } from './checkIns';
import { adjustTriggerForSchedulingPolicy } from './schedulingPolicy';
import type { AppState, MessageDraft, ReminderPlan, SupportedLocale } from './types';

export type OwnedNotificationPlanKind =
  | 'event-reminder'
  | 'pending-approval'
  | 'fallback-review'
  | 'setup-blocker'
  | 'recovery-issue'
  | 'check-in-suggestion';

/**
 * Ephemeral native-notification contract. Persisted ReminderPlan records remain
 * event-only and are structurally compatible with this shape.
 */
export interface OwnedNotificationPlan {
  id: string;
  kind?: OwnedNotificationPlanKind;
  title: string;
  body: string;
  triggerAt: string;
  eventId?: string;
  contactId?: string;
  messageId?: string;
  locale?: SupportedLocale;
}

export interface NotificationPlanValidation {
  ok: boolean;
  reason?: 'invalid-plan' | 'stale-event' | 'stale-message' | 'stale-contact' | 'stale-state';
}

const DAY_MS = 24 * 60 * 60 * 1_000;
const MAX_ENTITY_NOTIFICATIONS_PER_KIND = 3;
const referencePattern = /^[^\u0000-\u001f\u007f]{1,256}$/;

const localDateKey = (date: Date) => {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const stableReferenceHash = (value: string) => {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
};

const nextDailyTrigger = (state: AppState, now: Date, minuteOffset: number) => {
  const candidate = new Date(now);
  candidate.setHours(9, minuteOffset, 0, 0);
  let adjusted = adjustTriggerForSchedulingPolicy(candidate, state.settings);
  if (adjusted.blockedBy || !Number.isFinite(adjusted.triggerAt.getTime())) return undefined;
  if (adjusted.triggerAt.getTime() <= now.getTime()) {
    candidate.setDate(candidate.getDate() + 1);
    adjusted = adjustTriggerForSchedulingPolicy(candidate, state.settings);
    if (adjusted.blockedBy || !Number.isFinite(adjusted.triggerAt.getTime())) return undefined;
  }
  return adjusted.triggerAt;
};

const planId = (kind: OwnedNotificationPlanKind, reference: string, triggerAt: Date) =>
  `relateai-${kind}-${stableReferenceHash(reference)}-${localDateKey(triggerAt)}`;

const buildPlan = (
  state: AppState,
  now: Date,
  input: Omit<OwnedNotificationPlan, 'id' | 'triggerAt'> & {
    kind: Exclude<OwnedNotificationPlanKind, 'event-reminder'>;
    reference: string;
    minuteOffset: number;
  }
): OwnedNotificationPlan | undefined => {
  const triggerAt = nextDailyTrigger(state, now, input.minuteOffset);
  if (!triggerAt) return undefined;
  const { reference, minuteOffset: _minuteOffset, ...plan } = input;
  return {
    ...plan,
    id: planId(input.kind, reference, triggerAt),
    locale: state.settings.locale,
    triggerAt: triggerAt.toISOString()
  };
};

const isPendingApproval = (message: MessageDraft) => message.status === 'Needs review' || message.status === 'Draft';
const isRecoveryMessage = (message: MessageDraft) =>
  message.status === 'Failed' || message.status === 'Blocked' || message.status === 'Delivery unknown';

const activeContactIdsFor = (state: AppState) =>
  new Set(state.contacts.filter(contact => !contact.archivedAt).map(contact => contact.id));

const messageEventReferenceIsUsable = (state: AppState, message: MessageDraft) => {
  if (!message.eventId) return true;
  const event = state.events.find(item => item.id === message.eventId);
  return event?.contactId === message.contactId;
};

const backupNeedsAttention = (state: AppState, now: Date) => {
  const newest = state.backups
    .map(backup => Date.parse(backup.createdAt))
    .filter(Number.isFinite)
    .sort((left, right) => right - left)[0];
  return newest === undefined || now.getTime() - newest > 30 * DAY_MS;
};

const liveStateSetupNeedsAttention = (state: AppState) => {
  const notificationAuthorization = state.privacy.permissionRecords?.Notifications?.systemAuthorization;
  const notificationDecision = state.privacy.permissionDecisions.Notifications;
  const notificationPermissionNeedsAction =
    state.settings.notificationsEnabled &&
    (notificationDecision === 'Not requested' ||
      notificationDecision === 'Denied' ||
      notificationDecision === 'Unavailable' ||
      notificationAuthorization === 'denied' ||
      notificationAuthorization === 'restricted' ||
      notificationAuthorization === 'unavailable');
  return (
    (state.settings.aiEnabled && state.aiProvider.status !== 'Ready') ||
    (state.settings.emailEnabled && state.emailDelivery.status === 'Error') ||
    state.persistence.status === 'Error' ||
    state.persistence.storageHealth?.status === 'Corrupt' ||
    notificationPermissionNeedsAction
  );
};

const setupNeedsAttention = (state: AppState, now: Date) =>
  !state.onboarding.completed ||
  state.setupChecks.some(check => check.status === 'Needs action') ||
  liveStateSetupNeedsAttention(state) ||
  backupNeedsAttention(state, now);

const genericRecoveryNeedsAttention = (state: AppState) =>
  state.persistence.status === 'Error' ||
  state.persistence.storageHealth?.status === 'Corrupt' ||
  Boolean(state.calendarSync.lastError) ||
  state.aiProvider.status === 'Error' ||
  state.emailDelivery.status === 'Error';

export const notificationKindForPlan = (plan: OwnedNotificationPlan): OwnedNotificationPlanKind =>
  plan.kind ?? 'event-reminder';

export const asOwnedEventNotificationPlan = (
  plan: ReminderPlan,
  locale: SupportedLocale = 'en-IN'
): OwnedNotificationPlan => ({
  ...plan,
  kind: 'event-reminder',
  locale
});

/**
 * Native copy is fixed per notification purpose and never includes relationship
 * names, message text, contact routes, notes, provider errors, or setup detail.
 */
export const privacyMinimizedNotificationContent = (
  plan: OwnedNotificationPlan
): Pick<OwnedNotificationPlan, 'title' | 'body'> => {
  const locale = plan.locale ?? 'en-IN';
  switch (notificationKindForPlan(plan)) {
    case 'event-reminder':
      return {
        title: t(locale, 'notification.event.title'),
        body: t(locale, 'notification.event.body')
      };
    case 'pending-approval':
      return {
        title: t(locale, 'notification.approval.title'),
        body: t(locale, 'notification.approval.body')
      };
    case 'fallback-review':
      return {
        title: t(locale, 'notification.fallback.title'),
        body: t(locale, 'notification.fallback.body')
      };
    case 'setup-blocker':
      return {
        title: t(locale, 'notification.setup.title'),
        body: t(locale, 'notification.setup.body')
      };
    case 'recovery-issue':
      return {
        title: t(locale, 'notification.recovery.title'),
        body: t(locale, 'notification.recovery.body')
      };
    case 'check-in-suggestion':
      return {
        title: t(locale, 'notification.checkIn.title'),
        body: t(locale, 'notification.checkIn.body')
      };
  }
};

/** Plans non-event coverage without persisting OS-specific scheduling state. */
export const buildSupplementalNotificationPlans = (
  state: AppState,
  now: Date = new Date()
): OwnedNotificationPlan[] => {
  const activeContactIds = activeContactIdsFor(state);
  const plans: OwnedNotificationPlan[] = [];

  state.messages
    .filter(message => activeContactIds.has(message.contactId) && isPendingApproval(message))
    .sort((left, right) => left.id.localeCompare(right.id))
    .slice(0, MAX_ENTITY_NOTIFICATIONS_PER_KIND)
    .forEach(message => {
      const kind = message.quality === 'Template fallback' ? 'fallback-review' : 'pending-approval';
      const content = privacyMinimizedNotificationContent({
        id: '',
        kind,
        title: '',
        body: '',
        triggerAt: ''
      });
      const plan = buildPlan(state, now, {
        kind,
        reference: message.id,
        minuteOffset: kind === 'fallback-review' ? 7 : 5,
        ...content,
        messageId: message.id,
        contactId: message.contactId
      });
      if (plan) plans.push(plan);
    });

  state.messages
    .filter(message => activeContactIds.has(message.contactId) && isRecoveryMessage(message))
    .sort((left, right) => left.id.localeCompare(right.id))
    .slice(0, MAX_ENTITY_NOTIFICATIONS_PER_KIND)
    .forEach(message => {
      const content = privacyMinimizedNotificationContent({
        id: '',
        kind: 'recovery-issue',
        title: '',
        body: '',
        triggerAt: ''
      });
      const plan = buildPlan(state, now, {
        kind: 'recovery-issue',
        reference: `message:${message.id}`,
        minuteOffset: 10,
        ...content,
        messageId: message.id,
        contactId: message.contactId
      });
      if (plan) plans.push(plan);
    });

  if (setupNeedsAttention(state, now)) {
    const content = privacyMinimizedNotificationContent({
      id: '',
      kind: 'setup-blocker',
      title: '',
      body: '',
      triggerAt: ''
    });
    const plan = buildPlan(state, now, {
      kind: 'setup-blocker',
      reference: 'setup',
      minuteOffset: 15,
      ...content
    });
    if (plan) plans.push(plan);
  }

  if (genericRecoveryNeedsAttention(state)) {
    const content = privacyMinimizedNotificationContent({
      id: '',
      kind: 'recovery-issue',
      title: '',
      body: '',
      triggerAt: ''
    });
    const plan = buildPlan(state, now, {
      kind: 'recovery-issue',
      reference: 'generic-recovery',
      minuteOffset: 17,
      ...content
    });
    if (plan) plans.push(plan);
  }

  buildCheckInReminderQueue(state, now)
    .due.slice(0, MAX_ENTITY_NOTIFICATIONS_PER_KIND)
    .forEach(reminder => {
      const content = privacyMinimizedNotificationContent({
        id: '',
        kind: 'check-in-suggestion',
        title: '',
        body: '',
        triggerAt: ''
      });
      const plan = buildPlan(state, now, {
        kind: 'check-in-suggestion',
        reference: reminder.contactId,
        minuteOffset: 20,
        ...content,
        contactId: reminder.contactId
      });
      if (plan) plans.push(plan);
    });

  return plans.sort(
    (left, right) =>
      new Date(left.triggerAt).getTime() - new Date(right.triggerAt).getTime() || left.id.localeCompare(right.id)
  );
};

export const buildOwnedNotificationPlans = (
  state: AppState,
  eventPlans: ReminderPlan[],
  now: Date = new Date()
): OwnedNotificationPlan[] => [
  ...eventPlans.map(plan => asOwnedEventNotificationPlan(plan, state.settings.locale)),
  ...buildSupplementalNotificationPlans(state, now)
];

export const validateOwnedNotificationPlanStructure = (
  plan: OwnedNotificationPlan,
  now?: Date
): NotificationPlanValidation => {
  const nativeContent = privacyMinimizedNotificationContent(plan);
  if (
    !referencePattern.test(plan.id) ||
    !referencePattern.test(nativeContent.title) ||
    nativeContent.title.length > 120 ||
    !referencePattern.test(nativeContent.body) ||
    nativeContent.body.length > 240 ||
    !Number.isFinite(Date.parse(plan.triggerAt)) ||
    (now && Date.parse(plan.triggerAt) <= now.getTime())
  ) {
    return { ok: false, reason: 'invalid-plan' };
  }
  const kind = notificationKindForPlan(plan);
  const validReference = (value: string | undefined) => Boolean(value && referencePattern.test(value));
  switch (kind) {
    case 'event-reminder':
      return validReference(plan.eventId) && validReference(plan.contactId)
        ? { ok: true }
        : { ok: false, reason: 'invalid-plan' };
    case 'pending-approval':
    case 'fallback-review':
      return validReference(plan.messageId) && validReference(plan.contactId)
        ? { ok: true }
        : { ok: false, reason: 'invalid-plan' };
    case 'check-in-suggestion':
      return validReference(plan.contactId) ? { ok: true } : { ok: false, reason: 'invalid-plan' };
    case 'setup-blocker':
      return plan.eventId || plan.messageId || plan.contactId ? { ok: false, reason: 'invalid-plan' } : { ok: true };
    case 'recovery-issue':
      return plan.eventId ||
        (plan.messageId && !validReference(plan.messageId)) ||
        (plan.contactId && !validReference(plan.contactId))
        ? { ok: false, reason: 'invalid-plan' }
        : { ok: true };
  }
};

/** Blocks deleted, archived, mismatched, or already-resolved targets before native scheduling. */
export const validateOwnedNotificationPlanForState = (
  state: AppState,
  plan: OwnedNotificationPlan,
  now: Date = new Date()
): NotificationPlanValidation => {
  const structure = validateOwnedNotificationPlanStructure(plan);
  if (!structure.ok) return structure;

  const activeContactIds = activeContactIdsFor(state);
  switch (notificationKindForPlan(plan)) {
    case 'event-reminder': {
      const event = state.events.find(item => item.id === plan.eventId);
      return event && event.contactId === plan.contactId && activeContactIds.has(event.contactId)
        ? { ok: true }
        : { ok: false, reason: 'stale-event' };
    }
    case 'pending-approval': {
      const message = state.messages.find(item => item.id === plan.messageId);
      return message &&
        message.contactId === plan.contactId &&
        activeContactIds.has(message.contactId) &&
        isPendingApproval(message) &&
        messageEventReferenceIsUsable(state, message) &&
        message.quality !== 'Template fallback'
        ? { ok: true }
        : { ok: false, reason: 'stale-message' };
    }
    case 'fallback-review': {
      const message = state.messages.find(item => item.id === plan.messageId);
      return message &&
        message.contactId === plan.contactId &&
        activeContactIds.has(message.contactId) &&
        isPendingApproval(message) &&
        messageEventReferenceIsUsable(state, message) &&
        message.quality === 'Template fallback'
        ? { ok: true }
        : { ok: false, reason: 'stale-message' };
    }
    case 'recovery-issue': {
      if (!plan.messageId) {
        return genericRecoveryNeedsAttention(state) ? { ok: true } : { ok: false, reason: 'stale-state' };
      }
      const message = state.messages.find(item => item.id === plan.messageId);
      return message &&
        message.contactId === plan.contactId &&
        activeContactIds.has(message.contactId) &&
        isRecoveryMessage(message)
        ? { ok: true }
        : { ok: false, reason: 'stale-message' };
    }
    case 'setup-blocker':
      return setupNeedsAttention(state, now) ? { ok: true } : { ok: false, reason: 'stale-state' };
    case 'check-in-suggestion': {
      const dueContactIds = new Set(buildCheckInReminderQueue(state, now).due.map(item => item.contactId));
      return plan.contactId && activeContactIds.has(plan.contactId) && dueContactIds.has(plan.contactId)
        ? { ok: true }
        : { ok: false, reason: 'stale-contact' };
    }
  }
};
