import type { AppState, ReminderPlan, RelationshipEvent } from './types';

const startOfDayAtNine = (iso: string) => {
  const date = new Date(iso);
  date.setHours(9, 0, 0, 0);
  return date;
};

const daysBefore = (iso: string, days: number) => {
  const date = startOfDayAtNine(iso);
  date.setDate(date.getDate() - days);
  return date;
};

const buildPlan = (state: AppState, event: RelationshipEvent, days: number): ReminderPlan | undefined => {
  const contact = state.contacts.find(item => item.id === event.contactId);
  if (!contact) {
    return undefined;
  }
  const triggerAt = daysBefore(event.date, days);
  if (triggerAt.getTime() <= Date.now()) {
    return undefined;
  }
  const prefix = days === 0 ? 'Today' : `${days} day${days === 1 ? '' : 's'} left`;
  return {
    id: `reminder-${event.id}-${days}`,
    eventId: event.id,
    contactId: event.contactId,
    title: `${prefix}: ${event.type} for ${contact.name}`,
    body: 'Review the checklist, add context, and prepare a message before sending.',
    triggerAt: triggerAt.toISOString()
  };
};

export const buildReminderPlans = (state: AppState, daysBeforeEvent = [7, 1, 0]): ReminderPlan[] =>
  state.events
    .flatMap(event => daysBeforeEvent.map(days => buildPlan(state, event, days)))
    .filter((plan): plan is ReminderPlan => Boolean(plan))
    .sort((a, b) => new Date(a.triggerAt).getTime() - new Date(b.triggerAt).getTime());
