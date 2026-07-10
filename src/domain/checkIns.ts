import { resolveContactPreferencesForContact } from './contactPreferences';
import type { AppState, Contact } from './types';

export type CheckInReminderStatus = 'Due' | 'Snoozed' | 'Current';

export interface CheckInReminder {
  contactId: string;
  contactName: string;
  status: CheckInReminderStatus;
  cadenceDays: number;
  daysSinceContact?: number;
  overdueDays: number;
  lastContactedAt?: string;
  snoozedUntil?: string;
  title: string;
  detail: string;
  primaryActionLabel: string;
  secondaryActionLabel: string;
}

export interface CheckInReminderQueue {
  due: CheckInReminder[];
  snoozed: CheckInReminder[];
  current: CheckInReminder[];
  summary: string;
  emptyMessage?: string;
}

const parseDate = (iso: string | undefined) => {
  if (!iso) {
    return undefined;
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? undefined : date;
};

const daysSince = (iso: string | undefined, now: Date) => {
  const date = parseDate(iso);
  if (!date) {
    return undefined;
  }
  return Math.max(0, Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24)));
};

const buildReminder = (state: AppState, contact: Contact, now: Date): CheckInReminder => {
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const snoozedUntil = parseDate(contact.checkInSnoozedUntil);
  const isSnoozed = Boolean(snoozedUntil && snoozedUntil.getTime() > now.getTime());
  const days = daysSince(contact.lastContactedAt, now);
  const overdueDays =
    days === undefined ? preferences.checkInCadenceDays : Math.max(0, days - preferences.checkInCadenceDays);

  if (isSnoozed) {
    return {
      contactId: contact.id,
      contactName: contact.name,
      status: 'Snoozed',
      cadenceDays: preferences.checkInCadenceDays,
      daysSinceContact: days,
      overdueDays,
      lastContactedAt: contact.lastContactedAt,
      snoozedUntil: contact.checkInSnoozedUntil,
      title: `${contact.name} check-in snoozed`,
      detail: `Reminder is snoozed until ${contact.checkInSnoozedUntil}. Last-contact history is unchanged.`,
      primaryActionLabel: 'Write anyway',
      secondaryActionLabel: 'Mark contacted'
    };
  }

  const due = days === undefined || days >= preferences.checkInCadenceDays;
  return {
    contactId: contact.id,
    contactName: contact.name,
    status: due ? 'Due' : 'Current',
    cadenceDays: preferences.checkInCadenceDays,
    daysSinceContact: days,
    overdueDays,
    lastContactedAt: contact.lastContactedAt,
    title: due ? `${contact.name} needs a check-in` : `${contact.name} is current`,
    detail:
      days === undefined
        ? 'No recent contact date is saved. Write a check-in or mark that you contacted them elsewhere.'
        : `${days} day(s) since last contact; cadence is ${preferences.checkInCadenceDays} day(s).`,
    primaryActionLabel: due ? 'Write check-in' : 'Write anyway',
    secondaryActionLabel: 'Mark contacted'
  };
};

const contactPriority = (state: AppState, reminder: CheckInReminder) => {
  const contact = state.contacts.find(item => item.id === reminder.contactId);
  return {
    vip: contact?.isVip ? 1 : 0,
    health: contact?.healthScore ?? 0
  };
};

export const buildCheckInReminderQueue = (state: AppState, now = new Date()): CheckInReminderQueue => {
  const reminders = state.contacts
    .filter(contact => !contact.archivedAt)
    .map(contact => buildReminder(state, contact, now));
  const due = reminders
    .filter(reminder => reminder.status === 'Due')
    .sort((left, right) => {
      const leftPriority = contactPriority(state, left);
      const rightPriority = contactPriority(state, right);
      return (
        right.overdueDays - left.overdueDays ||
        rightPriority.vip - leftPriority.vip ||
        leftPriority.health - rightPriority.health ||
        left.contactName.localeCompare(right.contactName)
      );
    });
  const snoozed = reminders
    .filter(reminder => reminder.status === 'Snoozed')
    .sort((left, right) => (left.snoozedUntil ?? '').localeCompare(right.snoozedUntil ?? ''));
  const current = reminders
    .filter(reminder => reminder.status === 'Current')
    .sort((left, right) => {
      const leftDaysUntilDue = left.cadenceDays - (left.daysSinceContact ?? 0);
      const rightDaysUntilDue = right.cadenceDays - (right.daysSinceContact ?? 0);
      return leftDaysUntilDue - rightDaysUntilDue || left.contactName.localeCompare(right.contactName);
    });

  return {
    due,
    snoozed,
    current,
    summary:
      due.length > 0
        ? `${due.length} relationship check-in(s) need review.`
        : snoozed.length > 0
          ? `No check-ins are due; ${snoozed.length} reminder(s) are snoozed.`
          : 'No relationship check-ins are due.',
    emptyMessage:
      reminders.length === 0
        ? 'Add contacts and cadence preferences before check-in reminders can appear.'
        : due.length === 0
          ? 'No check-ins are due right now.'
          : undefined
  };
};
