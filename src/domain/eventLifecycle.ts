import { buildDefaultEventChecklist, validateManualEventInput } from './events';
import { lifecycleConfirmationToken } from './lifecycleConfirmation';
import { recurrenceFromDate } from './occasionDates';
import type { AppState, EventType, MessageDraft, RelationshipEvent } from './types';

export interface EventEditInput {
  contactId: string;
  eventType: EventType;
  label: string;
  date: string;
  verified: boolean;
}

export interface EventLifecycleImpact {
  activeMessageCount: number;
  historyMessageCount: number;
  reminderCount: number;
  calendarExportMayNeedReconciliation: boolean;
}

export type EventLifecycleFailure = {
  ok: false;
  reason: string;
};

export type EventEditPreview =
  | EventLifecycleFailure
  | {
      ok: true;
      eventId: string;
      normalized: {
        contactId: string;
        eventType: EventType;
        label: string;
        dateIso: string;
        verified: boolean;
      };
      changedFields: ('contactId' | 'eventType' | 'label' | 'date' | 'verified')[];
      conflicts: string[];
      conflictingEventIds: string[];
      impact: EventLifecycleImpact;
      requiresConfirmation: boolean;
      confirmationToken: string;
    };

export type EventDeletePreview =
  | EventLifecycleFailure
  | {
      ok: true;
      eventId: string;
      impact: EventLifecycleImpact;
      requiresConfirmation: true;
      confirmationToken: string;
    };

const activeMessageStatuses: MessageDraft['status'][] = ['Needs review', 'Draft', 'Scheduled', 'Blocked'];

const eventImpactRevision = (state: AppState, eventId: string) => ({
  event: state.events.find(event => event.id === eventId),
  messages: state.messages
    .filter(message => message.eventId === eventId)
    .map(message => ({
      id: message.id,
      contactId: message.contactId,
      status: message.status,
      scheduledFor: message.scheduledFor
    })),
  reminders: state.reminderPlans
    .filter(plan => plan.eventId === eventId)
    .map(plan => ({ id: plan.id, contactId: plan.contactId, triggerAt: plan.triggerAt })),
  lastCalendarExport: state.calendarSync.lastExportedAt
});

export const eventLifecycleImpact = (state: AppState, eventId: string): EventLifecycleImpact => ({
  activeMessageCount: state.messages.filter(
    message => message.eventId === eventId && activeMessageStatuses.includes(message.status)
  ).length,
  historyMessageCount: state.messages.filter(
    message => message.eventId === eventId && !activeMessageStatuses.includes(message.status)
  ).length,
  reminderCount: state.reminderPlans.filter(plan => plan.eventId === eventId).length,
  calendarExportMayNeedReconciliation: Boolean(state.calendarSync.lastExportedAt)
});

export const previewEventEdit = (state: AppState, eventId: string, input: EventEditInput): EventEditPreview => {
  const event = state.events.find(item => item.id === eventId);
  if (!event) return { ok: false, reason: 'Event could not be found.' };
  const contact = state.contacts.find(item => item.id === input.contactId);
  if (contact?.archivedAt) return { ok: false, reason: 'Restore the archived contact before assigning an event.' };
  const validation = validateManualEventInput(
    {
      contactId: input.contactId,
      eventType: input.eventType,
      label: input.label,
      date: input.date
    },
    state.contacts,
    state.events.filter(item => item.id !== eventId)
  );
  if (!validation.ok) return { ok: false, reason: validation.errors.join(' ') };
  if (!validation.normalized.contactId) return { ok: false, reason: 'The selected contact is no longer available.' };
  const normalized = {
    contactId: validation.normalized.contactId,
    eventType: validation.normalized.eventType,
    label: validation.normalized.label,
    dateIso: validation.normalized.dateIso,
    verified: input.verified
  };
  const changedFields: ('contactId' | 'eventType' | 'label' | 'date' | 'verified')[] = [];
  if (event.contactId !== normalized.contactId) changedFields.push('contactId');
  if (event.type !== normalized.eventType) changedFields.push('eventType');
  if (event.label !== normalized.label) changedFields.push('label');
  if (event.date !== normalized.dateIso) changedFields.push('date');
  if (event.verified !== normalized.verified) changedFields.push('verified');
  if (changedFields.length === 0) return { ok: false, reason: 'No event changes to save.' };
  const impact = eventLifecycleImpact(state, eventId);
  const requiresConfirmation =
    validation.warnings.length > 0 ||
    impact.activeMessageCount > 0 ||
    (changedFields.includes('contactId') && impact.historyMessageCount > 0) ||
    impact.reminderCount > 0 ||
    impact.calendarExportMayNeedReconciliation;
  return {
    ok: true,
    eventId,
    normalized,
    changedFields,
    conflicts: validation.warnings,
    conflictingEventIds: validation.conflictingEventIds,
    impact,
    requiresConfirmation,
    confirmationToken: lifecycleConfirmationToken('edit-event', {
      ...eventImpactRevision(state, eventId),
      normalized,
      changedFields,
      conflicts: validation.warnings,
      conflictingEventIds: validation.conflictingEventIds
    })
  };
};

export const previewEventDelete = (state: AppState, eventId: string): EventDeletePreview => {
  if (!state.events.some(event => event.id === eventId)) {
    return { ok: false, reason: 'Event could not be found.' };
  }
  return {
    ok: true,
    eventId,
    impact: eventLifecycleImpact(state, eventId),
    requiresConfirmation: true,
    confirmationToken: lifecycleConfirmationToken('delete-event', eventImpactRevision(state, eventId))
  };
};

const clearApproval = (message: MessageDraft): MessageDraft => {
  const {
    approvedAt: _approvedAt,
    approvalExpiresAt: _approvalExpiresAt,
    scheduledTimeZone: _scheduledTimeZone,
    ...remaining
  } = message;
  return remaining;
};

const reviewActiveMessage = (message: MessageDraft, reason: string): MessageDraft =>
  activeMessageStatuses.includes(message.status)
    ? {
        ...clearApproval(message),
        status: 'Needs review',
        readiness: 'Review after event lifecycle change',
        lastError: reason
      }
    : message;

const reconcileChecklist = (event: RelationshipEvent, nextType: EventType): RelationshipEvent['checklist'] => {
  const currentById = new Map(event.checklist.map(item => [item.id, item]));
  return buildDefaultEventChecklist(nextType).map(item => ({
    ...item,
    done: currentById.get(item.id)?.done ?? item.done
  }));
};

const markCalendarReconciliationNeeded = (state: AppState): AppState['calendarSync'] => {
  const { lastExportedAt: _lastExportedAt, ...calendarSync } = state.calendarSync;
  return calendarSync;
};

export const applyEventEdit = (
  state: AppState,
  eventId: string,
  normalized: Exclude<EventEditPreview, EventLifecycleFailure>['normalized']
): AppState => {
  const previous = state.events.find(event => event.id === eventId);
  if (!previous) return state;
  const contactChanged = previous.contactId !== normalized.contactId;
  return {
    ...state,
    events: state.events.map(event =>
      event.id === eventId
        ? {
            ...event,
            contactId: normalized.contactId,
            type: normalized.eventType,
            label: normalized.label,
            date: normalized.dateIso,
            recurrence: recurrenceFromDate(normalized.eventType, normalized.dateIso),
            verified: normalized.verified,
            checklist: reconcileChecklist(event, normalized.eventType)
          }
        : event
    ),
    messages: state.messages.map(message => {
      if (message.eventId !== eventId) return message;
      if (contactChanged) {
        const {
          eventId: _eventId,
          scheduledFor: _scheduledFor,
          scheduledTimeZone: _scheduledTimeZone,
          ...detached
        } = message;
        return reviewActiveMessage(
          detached,
          'The event moved to another contact. This message remains with its original recipient and needs review.'
        );
      }
      if (!activeMessageStatuses.includes(message.status)) return message;
      return reviewActiveMessage(
        { ...message, scheduledFor: normalized.dateIso },
        'Event details changed. Review the date and message before scheduling or sending.'
      );
    }),
    reminderPlans: state.reminderPlans.filter(plan => plan.eventId !== eventId),
    calendarSync: markCalendarReconciliationNeeded(state)
  };
};

export const applyEventDelete = (state: AppState, eventId: string): AppState => ({
  ...state,
  events: state.events.filter(event => event.id !== eventId),
  messages: state.messages.map(message => {
    if (message.eventId !== eventId) return message;
    const {
      eventId: _eventId,
      scheduledFor: _scheduledFor,
      scheduledTimeZone: _scheduledTimeZone,
      ...detached
    } = message;
    return reviewActiveMessage(
      detached,
      'The linked event was deleted. Choose new context before scheduling or sending.'
    );
  }),
  reminderPlans: state.reminderPlans.filter(plan => plan.eventId !== eventId),
  calendarSync: markCalendarReconciliationNeeded(state)
});
