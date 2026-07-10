import { buildCheckInReminderQueue } from './checkIns';
import { buildContactEnrichmentPlan } from './contactEnrichment';
import { eventOccurrenceIso } from './occasionDates';
import type { AppState, Screen } from './types';

export type HomePlannerActionKind =
  | 'recover-message'
  | 'review-message'
  | 'prepare-event'
  | 'relationship-check-in'
  | 'create-backup'
  | 'complete-setup'
  | 'enrich-contact';

export type HomePlannerAction = Readonly<{
  id: string;
  kind: HomePlannerActionKind;
  priority: number;
  title: string;
  detail: string;
  targetScreen: Screen;
  contactId?: string;
  messageId?: string;
  eventId?: string;
}>;

export type HomePlanner = Readonly<{
  generatedAt: string;
  actions: readonly HomePlannerAction[];
  counts: Readonly<Record<HomePlannerActionKind, number>>;
  summary: string;
}>;

export type HomePlannerContext = Readonly<{
  /** Latest environment-aware Setup Doctor result; legacy saved checks are only the fallback. */
  setupNeedsAction?: boolean;
}>;

const DAY_MS = 24 * 60 * 60 * 1_000;
const BACKUP_STALE_DAYS = 30;
const UPCOMING_EVENT_DAYS = 14;

const daysUntil = (iso: string, now: Date) => Math.ceil((new Date(iso).getTime() - now.getTime()) / DAY_MS);

export const buildHomePlanner = (state: AppState, now = new Date(), context: HomePlannerContext = {}): HomePlanner => {
  const activeContacts = state.contacts.filter(contact => !contact.archivedAt);
  const activeContactIds = new Set(activeContacts.map(contact => contact.id));
  const actions: HomePlannerAction[] = [];

  state.messages
    .filter(
      message =>
        activeContactIds.has(message.contactId) && ['Failed', 'Blocked', 'Delivery unknown'].includes(message.status)
    )
    .slice(0, 5)
    .forEach(message => {
      actions.push({
        id: `recover-${message.id}`,
        kind: 'recover-message',
        priority: 100,
        title: 'Recover a message delivery',
        detail: `${message.status} message requires an explicit recovery decision.`,
        targetScreen: 'messages',
        contactId: message.contactId,
        messageId: message.id
      });
    });

  state.messages
    .filter(
      message =>
        activeContactIds.has(message.contactId) && (message.status === 'Needs review' || message.status === 'Draft')
    )
    .slice(0, 5)
    .forEach(message => {
      actions.push({
        id: `review-${message.id}`,
        kind: 'review-message',
        priority: 90,
        title: 'Review a prepared message',
        detail: 'Approval is required before scheduling, handoff, or delivery.',
        targetScreen: 'wishPreview',
        contactId: message.contactId,
        messageId: message.id
      });
    });

  state.events
    .filter(event => activeContactIds.has(event.contactId))
    .map(event => ({ event, occurrence: eventOccurrenceIso(event, now) }))
    .filter((item): item is { event: (typeof state.events)[number]; occurrence: string } => Boolean(item.occurrence))
    .map(item => ({ ...item, days: daysUntil(item.occurrence, now) }))
    .filter(item => item.days >= 0 && item.days <= UPCOMING_EVENT_DAYS)
    .sort((left, right) => left.days - right.days || left.event.id.localeCompare(right.event.id))
    .slice(0, 5)
    .forEach(({ event, days }) => {
      actions.push({
        id: `prepare-${event.id}`,
        kind: 'prepare-event',
        priority: 80 - Math.min(days, UPCOMING_EVENT_DAYS),
        title: 'Prepare for an upcoming event',
        detail: `Event preparation is due in ${days} day(s).`,
        targetScreen: 'events',
        contactId: event.contactId,
        eventId: event.id
      });
    });

  buildCheckInReminderQueue(state, now)
    .due.slice(0, 5)
    .forEach(reminder => {
      actions.push({
        id: `check-in-${reminder.contactId}`,
        kind: 'relationship-check-in',
        priority: 65 + Math.min(reminder.overdueDays, 10),
        title: 'Review an overdue check-in',
        detail: `${reminder.overdueDays} day(s) beyond the saved cadence.`,
        targetScreen: 'manualComposer',
        contactId: reminder.contactId
      });
    });

  const newestBackupTime = state.backups
    .map(backup => Date.parse(backup.createdAt))
    .filter(Number.isFinite)
    .sort((left, right) => right - left)[0];
  const backupAgeDays = newestBackupTime
    ? Math.floor((now.getTime() - newestBackupTime) / DAY_MS)
    : Number.POSITIVE_INFINITY;
  if (backupAgeDays > BACKUP_STALE_DAYS) {
    actions.push({
      id: 'create-backup',
      kind: 'create-backup',
      priority: 60,
      title: 'Create an encrypted backup',
      detail: Number.isFinite(backupAgeDays)
        ? `The newest backup is ${backupAgeDays} day(s) old.`
        : 'No encrypted backup has been recorded.',
      targetScreen: 'backup'
    });
  }

  const setupNeedsAction = context.setupNeedsAction ?? state.setupChecks.some(check => check.status === 'Needs action');
  if (!state.onboarding.completed || setupNeedsAction) {
    actions.push({
      id: 'complete-setup',
      kind: 'complete-setup',
      priority: 55,
      title: 'Complete required setup',
      detail: 'At least one setup dependency still needs an explicit decision.',
      targetScreen: state.onboarding.completed ? 'setupCheck' : 'onboarding'
    });
  }

  const weakestEnrichment = activeContacts
    .map(contact => buildContactEnrichmentPlan(state, contact.id))
    .filter((plan): plan is NonNullable<typeof plan> => Boolean(plan))
    .filter(plan => plan.score < 50)
    .sort((left, right) => left.score - right.score || left.contactId.localeCompare(right.contactId))[0];
  if (weakestEnrichment) {
    actions.push({
      id: `enrich-${weakestEnrichment.contactId}`,
      kind: 'enrich-contact',
      priority: 45,
      title: 'Improve relationship context',
      detail: 'The contact profile has a high-value missing detail.',
      targetScreen: 'contactDetail',
      contactId: weakestEnrichment.contactId
    });
  }

  actions.sort((left, right) => right.priority - left.priority || left.id.localeCompare(right.id));
  const kinds: HomePlannerActionKind[] = [
    'recover-message',
    'review-message',
    'prepare-event',
    'relationship-check-in',
    'create-backup',
    'complete-setup',
    'enrich-contact'
  ];
  const counts = Object.fromEntries(
    kinds.map(kind => [kind, actions.filter(action => action.kind === kind).length])
  ) as Record<HomePlannerActionKind, number>;
  return {
    generatedAt: now.toISOString(),
    actions: actions.slice(0, 12),
    counts,
    summary:
      actions.length > 0
        ? `${actions.length} relationship action(s) ranked; start with ${actions[0].kind}.`
        : 'No relationship action is currently due.'
  };
};
