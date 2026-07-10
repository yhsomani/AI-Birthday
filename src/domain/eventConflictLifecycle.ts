import { lifecycleConfirmationToken } from './lifecycleConfirmation';
import { recurrenceForEvent } from './occasionDates';
import type { AppState, MessageDraft, RelationshipEvent } from './types';

export type EventMergePreview =
  | { ok: false; reason: string }
  | {
      ok: true;
      survivorEventId: string;
      mergedEventId: string;
      matchReasons: ('same-date' | 'same-type' | 'same-label')[];
      activeMessageCount: number;
      historyMessageCount: number;
      reminderCount: number;
      requiresConfirmation: true;
      confirmationToken: string;
    };

const activeStatuses: MessageDraft['status'][] = ['Needs review', 'Draft', 'Scheduled', 'Blocked'];

const dateIdentity = (event: RelationshipEvent) => {
  const recurrence = recurrenceForEvent(event);
  return recurrence ? `yearly:${recurrence.month}-${recurrence.day}` : event.date.slice(0, 10);
};

const revision = (state: AppState, eventId: string) => ({
  event: state.events.find(event => event.id === eventId),
  messages: state.messages
    .filter(message => message.eventId === eventId)
    .map(message => ({ id: message.id, status: message.status, contactId: message.contactId })),
  reminders: state.reminderPlans
    .filter(plan => plan.eventId === eventId)
    .map(plan => ({ id: plan.id, triggerAt: plan.triggerAt }))
});

export const previewEventMerge = (
  state: AppState,
  survivorEventId: string,
  mergedEventId: string
): EventMergePreview => {
  if (survivorEventId === mergedEventId) {
    return { ok: false, reason: 'Choose two different events to merge.' };
  }
  const survivor = state.events.find(event => event.id === survivorEventId);
  const merged = state.events.find(event => event.id === mergedEventId);
  if (!survivor || !merged) return { ok: false, reason: 'Both events must still exist before merging.' };
  if (survivor.contactId !== merged.contactId) {
    return { ok: false, reason: 'Only events for the same contact can be merged.' };
  }
  const matchReasons: ('same-date' | 'same-type' | 'same-label')[] = [];
  if (dateIdentity(survivor) === dateIdentity(merged)) matchReasons.push('same-date');
  if (survivor.type === merged.type) matchReasons.push('same-type');
  if (survivor.label.trim().toLowerCase() === merged.label.trim().toLowerCase()) {
    matchReasons.push('same-label');
  }
  if (!matchReasons.includes('same-date')) {
    return { ok: false, reason: 'Events must have the same calendar date before they can be merged.' };
  }
  const linkedMessages = state.messages.filter(
    message => message.eventId === survivorEventId || message.eventId === mergedEventId
  );
  const reminderCount = state.reminderPlans.filter(
    plan => plan.eventId === survivorEventId || plan.eventId === mergedEventId
  ).length;
  return {
    ok: true,
    survivorEventId,
    mergedEventId,
    matchReasons,
    activeMessageCount: linkedMessages.filter(message => activeStatuses.includes(message.status)).length,
    historyMessageCount: linkedMessages.filter(message => !activeStatuses.includes(message.status)).length,
    reminderCount,
    requiresConfirmation: true,
    confirmationToken: lifecycleConfirmationToken('merge-events', {
      survivor: revision(state, survivorEventId),
      merged: revision(state, mergedEventId),
      matchReasons
    })
  };
};

const clearApproval = (message: MessageDraft): MessageDraft => {
  const { approvedAt: _approvedAt, approvalExpiresAt: _approvalExpiresAt, ...remaining } = message;
  return remaining;
};

const mergedSourceIdentities = (
  survivor: RelationshipEvent,
  merged: RelationshipEvent
): RelationshipEvent['sourceIdentities'] => {
  const identities = new Map<string, NonNullable<RelationshipEvent['sourceIdentities']>[number]>();
  for (const identity of [...(survivor.sourceIdentities ?? []), ...(merged.sourceIdentities ?? [])]) {
    identities.set(`${identity.provider}:${identity.sourceId}`, identity);
  }
  return identities.size > 0 ? [...identities.values()] : undefined;
};

export const applyEventMerge = (state: AppState, survivorEventId: string, mergedEventId: string): AppState => {
  const survivor = state.events.find(event => event.id === survivorEventId);
  const merged = state.events.find(event => event.id === mergedEventId);
  if (!survivor || !merged) return state;
  const checklist = new Map(survivor.checklist.map(item => [item.id, { ...item }]));
  for (const item of merged.checklist) {
    const existing = checklist.get(item.id);
    checklist.set(item.id, existing ? { ...existing, done: existing.done || item.done } : { ...item });
  }
  const { lastExportedAt: _lastExportedAt, ...calendarSync } = state.calendarSync;
  return {
    ...state,
    events: state.events
      .filter(event => event.id !== mergedEventId)
      .map(event =>
        event.id === survivorEventId
          ? {
              ...event,
              verified: event.verified || merged.verified,
              sourceIdentities: mergedSourceIdentities(event, merged),
              checklist: [...checklist.values()]
            }
          : event
      ),
    messages: state.messages.map(message => {
      if (message.eventId !== mergedEventId) return message;
      if (!activeStatuses.includes(message.status)) {
        return { ...message, eventId: survivorEventId };
      }
      return {
        ...clearApproval(message),
        eventId: survivorEventId,
        status: 'Needs review',
        readiness: 'Review after event merge',
        lastError: 'Event conflicts were merged. Review this message before scheduling or sending.'
      };
    }),
    reminderPlans: state.reminderPlans.filter(
      plan => plan.eventId !== survivorEventId && plan.eventId !== mergedEventId
    ),
    calendarSync
  };
};
