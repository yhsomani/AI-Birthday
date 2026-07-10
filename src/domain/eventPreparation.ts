import type {
  AppState,
  Contact,
  EventChecklistItem,
  EventType,
  MessageChannel,
  RelationshipEvent,
  Screen
} from './types';
import { messageTargetsEventOccurrence } from './duplicateGuard';
import {
  eventOccurrenceLocalDateKey,
  localDateKey,
  recurrenceForEvent,
  utcDateKey,
  yearlyOccurrenceDateKey
} from './occasionDates';

export type EventPreparationStepId =
  'confirm-date' | 'improve-context' | 'write-message' | 'decide-gift' | 'choose-channel' | 'schedule-reminder';

export type EventPreparationStatus = 'Done' | 'Needs action';

export interface EventPreparationStep {
  id: EventPreparationStepId;
  label: string;
  done: boolean;
  status: EventPreparationStatus;
  detail: string;
  actionLabel: string;
  targetScreen?: Screen;
  canToggle: boolean;
}

export type EventPreparationPlan =
  | {
      ok: true;
      event: RelationshipEvent;
      contact?: Contact;
      steps: EventPreparationStep[];
      completedCount: number;
      totalCount: number;
      isComplete: boolean;
      nextStep?: EventPreparationStep;
      summary: string;
    }
  | {
      ok: false;
      steps: EventPreparationStep[];
      completedCount: 0;
      totalCount: 0;
      isComplete: false;
      summary: string;
      error: string;
    };

const writeMessageLabelFor = (eventType: EventType) =>
  eventType === 'Follow-up'
    ? 'Write check-in'
    : eventType === 'Work anniversary'
      ? 'Prepare concise note'
      : 'Write or review wish';

const giftRelevantEventTypes: EventType[] = ['Birthday', 'Anniversary', 'Graduation', 'Holiday', 'Custom'];

const checklistAliases: Record<EventPreparationStepId, string[]> = {
  'confirm-date': ['confirm-date'],
  'improve-context': ['improve-context'],
  'write-message': ['write-message', 'write-wish', 'write-checkin'],
  'decide-gift': ['decide-gift', 'choose-gift', 'prepare-gift'],
  'choose-channel': ['choose-channel'],
  'schedule-reminder': ['schedule-reminder', 'plan-reminder']
};

const checklistLabelAliases: Record<EventPreparationStepId, string[]> = {
  'confirm-date': ['confirm date', 'confirm imported date'],
  'improve-context': ['add one personal memory', 'add memory', 'improve context'],
  'write-message': ['write or review wish', 'write wish', 'write check-in', 'prepare concise note'],
  'decide-gift': ['decide gift idea', 'choose gift', 'prepare gift'],
  'choose-channel': ['choose send channel', 'choose channel', 'confirm email route', 'use manual send handoff'],
  'schedule-reminder': ['schedule reminder', 'plan reminder']
};

const normalizedChecklistLabel = (value: string) => value.trim().toLocaleLowerCase('en-US');

const checklistItemMatchesStep = (item: EventChecklistItem, stepId: EventPreparationStepId) =>
  checklistAliases[stepId].includes(item.id) ||
  checklistLabelAliases[stepId].includes(normalizedChecklistLabel(item.label));

const isKnownChecklistItem = (item: EventChecklistItem) =>
  (Object.keys(checklistAliases) as EventPreparationStepId[]).some(stepId => checklistItemMatchesStep(item, stepId));

const localDatePattern = /^\d{4}-\d{2}-\d{2}$/;

const isValidLocalDateKey = (value: string | undefined): value is string => {
  if (!value || !localDatePattern.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  return parsed.getUTCFullYear() === year && parsed.getUTCMonth() === month - 1 && parsed.getUTCDate() === day;
};

/** Resolves the one local-calendar occurrence whose preparation is currently actionable. */
export const eventPreparationOccurrenceKey = (
  event: RelationshipEvent,
  reference: Date | string = new Date()
): string | undefined => {
  if (reference instanceof Date) return eventOccurrenceLocalDateKey(event, reference);
  if (!isValidLocalDateKey(reference)) return undefined;
  const recurrence = recurrenceForEvent(event);
  if (!recurrence) return utcDateKey(event.date);
  const year = Number(reference.slice(0, 4));
  const currentYear = yearlyOccurrenceDateKey(recurrence, year);
  if (currentYear && currentYear >= reference) return currentYear;
  return yearlyOccurrenceDateKey(recurrence, year + 1);
};

const explicitChecklistItemDoneForOccurrence = (
  event: RelationshipEvent,
  item: EventChecklistItem,
  occurrenceKey: string | undefined
) => {
  if (!item.done || !occurrenceKey) return false;
  if (item.completedForOccurrence !== undefined) {
    return item.completedForOccurrence === occurrenceKey;
  }
  // Legacy checklist records did not carry an occurrence key. For recurring
  // events, their explicit completion belongs only to the saved reference
  // occurrence instead of silently carrying into every future year.
  return recurrenceForEvent(event) ? utcDateKey(event.date) === occurrenceKey : true;
};

const withExplicitCompletion = (
  item: EventChecklistItem,
  done: boolean,
  occurrenceKey?: string
): EventChecklistItem => {
  const { completedForOccurrence: _previousOccurrence, ...base } = item;
  return {
    ...base,
    done,
    ...(done && occurrenceKey ? { completedForOccurrence: occurrenceKey } : {})
  };
};

export const eventPreparationStepDefinitions = (eventType: EventType): EventChecklistItem[] => {
  const steps: EventChecklistItem[] = [
    { id: 'confirm-date', label: 'Confirm date', done: false },
    { id: 'improve-context', label: 'Add one personal memory', done: false },
    { id: 'write-message', label: writeMessageLabelFor(eventType), done: false }
  ];

  if (giftRelevantEventTypes.includes(eventType)) {
    steps.push({ id: 'decide-gift', label: 'Decide gift idea', done: false });
  }

  steps.push(
    { id: 'choose-channel', label: 'Choose send channel', done: false },
    { id: 'schedule-reminder', label: 'Schedule reminder', done: false }
  );

  return steps;
};

export const buildDefaultEventPreparationChecklist = (eventType: EventType): EventChecklistItem[] =>
  eventPreparationStepDefinitions(eventType).map(item => ({
    ...item,
    done: item.id === 'confirm-date'
  }));

export const normalizeEventPreparationChecklist = (
  eventType: EventType,
  checklist: EventChecklistItem[]
): EventChecklistItem[] =>
  eventPreparationStepDefinitions(eventType).map(definition => {
    const existing = checklist.find(item => checklistItemMatchesStep(item, definition.id as EventPreparationStepId));
    return {
      ...definition,
      done: existing?.done ?? false,
      ...(existing?.completedForOccurrence ? { completedForOccurrence: existing.completedForOccurrence } : {})
    };
  });

export const toggleEventPreparationChecklistStep = (
  eventType: EventType,
  checklist: EventChecklistItem[],
  stepId: EventPreparationStepId,
  occurrenceKey?: string,
  legacyOccurrenceKey?: string
): EventChecklistItem[] => {
  const normalized = normalizeEventPreparationChecklist(eventType, checklist).map(item => {
    if (item.id !== stepId) return item;
    const doneForOccurrence = occurrenceKey
      ? item.done &&
        (item.completedForOccurrence === occurrenceKey ||
          (item.completedForOccurrence === undefined && legacyOccurrenceKey === occurrenceKey))
      : item.done;
    return withExplicitCompletion(item, !doneForOccurrence, occurrenceKey);
  });
  const customItems = checklist.filter(item => !isKnownChecklistItem(item));
  return [...normalized, ...customItems];
};

/** Compatibility toggle for callers that still address a legacy checklist id directly. */
export const toggleEventChecklistItemForOccurrence = (
  event: RelationshipEvent,
  itemId: string,
  referenceLocalDate: string
): EventChecklistItem[] => {
  const occurrenceKey = eventPreparationOccurrenceKey(event, referenceLocalDate);
  return event.checklist.map(item => {
    if (item.id !== itemId) return item;
    const doneForOccurrence = explicitChecklistItemDoneForOccurrence(event, item, occurrenceKey);
    return withExplicitCompletion(item, !doneForOccurrence, occurrenceKey);
  });
};

const channelDetail = (
  state: AppState,
  contact: Contact | undefined,
  channel: MessageChannel
): { ready: boolean; detail: string } => {
  if (!contact) {
    return { ready: false, detail: 'Contact details are unavailable.' };
  }

  if (channel === 'Manual') {
    return { ready: true, detail: 'Manual handoff is available after review.' };
  }

  if (channel === 'SMS') {
    if (!state.settings.smsEnabled) {
      return { ready: false, detail: 'SMS is disabled in Settings.' };
    }
    return contact.phone
      ? { ready: true, detail: 'SMS route has a phone number.' }
      : { ready: false, detail: 'Add a phone number or choose another channel.' };
  }

  if (channel === 'WhatsApp') {
    if (!state.settings.whatsappHandoffEnabled) {
      return { ready: false, detail: 'WhatsApp handoff is disabled in Settings.' };
    }
    if (!state.privacy.whatsappHandoffConsent) {
      return { ready: false, detail: 'Grant explicit WhatsApp handoff consent first.' };
    }
    return contact.phone
      ? { ready: true, detail: 'WhatsApp handoff has a phone number.' }
      : { ready: false, detail: 'Add a phone number or choose another channel.' };
  }

  return contact.email
    ? {
        ready: true,
        detail: state.settings.emailEnabled
          ? 'Email route can use configured delivery or manual mail handoff.'
          : 'Manual mail handoff is available; provider delivery remains optional.'
      }
    : { ready: false, detail: 'Add an email address or choose another channel.' };
};

const statusFor = (done: boolean): EventPreparationStatus => (done ? 'Done' : 'Needs action');

const dayNumber = (dateKey: string) => Date.parse(`${dateKey}T12:00:00.000Z`) / 86_400_000;

const reminderTargetsOccurrence = (
  plan: AppState['reminderPlans'][number],
  event: RelationshipEvent,
  occurrenceKey: string | undefined
) => {
  if (!occurrenceKey || plan.eventId !== event.id) return false;
  const trigger = new Date(plan.triggerAt);
  const triggerKey = Number.isNaN(trigger.getTime()) ? undefined : localDateKey(trigger);
  if (!triggerKey) return false;
  const configuredDays = Number(plan.id.slice(`reminder-${event.id}-`.length));
  const maximumLeadDays =
    plan.id.startsWith(`reminder-${event.id}-`) && Number.isInteger(configuredDays) && configuredDays >= 0
      ? configuredDays
      : 31;
  const daysUntilOccurrence = dayNumber(occurrenceKey) - dayNumber(triggerKey);
  // Reminder policy may defer a trigger through quiet hours or blackouts, but
  // a stale plan from a prior annual occurrence must not complete this step.
  return daysUntilOccurrence <= maximumLeadDays && daysUntilOccurrence >= -31;
};

export const buildEventPreparationPlan = (
  state: AppState,
  eventId: string,
  reference: Date = new Date()
): EventPreparationPlan => {
  const event = state.events.find(item => item.id === eventId);
  if (!event) {
    return {
      ok: false,
      steps: [],
      completedCount: 0,
      totalCount: 0,
      isComplete: false,
      summary: 'Event preparation is unavailable because the event could not be found.',
      error: 'The selected event could not be found.'
    };
  }

  const contact = state.contacts.find(item => item.id === event.contactId);
  const occurrenceKey = eventPreparationOccurrenceKey(event, reference);
  const checklist = normalizeEventPreparationChecklist(event.type, event.checklist);
  const checklistDone = (stepId: EventPreparationStepId) =>
    explicitChecklistItemDoneForOccurrence(
      event,
      checklist.find(item => item.id === stepId) ?? { id: stepId, label: stepId, done: false },
      occurrenceKey
    );
  const publicMemoryCount = state.memories.filter(
    memory => memory.contactId === event.contactId && memory.category !== 'Private'
  ).length;
  const messageReady = state.messages.some(
    message => message.status !== 'Rejected' && messageTargetsEventOccurrence(state, message, event.id, reference)
  );
  const giftHistoryCount = state.gifts.filter(gift => gift.contactId === event.contactId).length;
  const reminderReady = state.reminderPlans.some(plan => reminderTargetsOccurrence(plan, event, occurrenceKey));
  const route = channelDetail(state, contact, contact?.preferredChannel ?? 'Manual');

  const steps: EventPreparationStep[] = checklist.map(item => {
    const id = item.id as EventPreparationStepId;
    if (id === 'confirm-date') {
      const done = checklistDone(id) || event.verified;
      return {
        id,
        label: item.label,
        done,
        status: statusFor(done),
        detail: done ? 'Event date is verified for reminders.' : 'Review and confirm the event date before drafting.',
        actionLabel: 'Review event',
        targetScreen: 'events',
        canToggle: true
      };
    }

    if (id === 'improve-context') {
      const done = checklistDone(id) || publicMemoryCount > 0;
      return {
        id,
        label: item.label,
        done,
        status: statusFor(done),
        detail: done
          ? `${publicMemoryCount} non-private memory item(s) can improve the message.`
          : 'Add one non-private memory so the message feels specific.',
        actionLabel: 'Open contact',
        targetScreen: 'contactDetail',
        canToggle: true
      };
    }

    if (id === 'write-message') {
      const done = checklistDone(id) || messageReady;
      return {
        id,
        label: item.label,
        done,
        status: statusFor(done),
        detail: done
          ? 'A draft or checklist mark exists for this occurrence.'
          : 'Create a review-first draft for this occurrence.',
        actionLabel: 'Write message',
        targetScreen: 'wishPreview',
        canToggle: true
      };
    }

    if (id === 'decide-gift') {
      const done = checklistDone(id);
      return {
        id,
        label: item.label,
        done,
        status: statusFor(done),
        detail:
          giftHistoryCount > 0
            ? `${giftHistoryCount} gift record(s) available; decide whether to reuse, avoid, or update the idea.`
            : 'Review gift ideas or explicitly skip gifting for this event.',
        actionLabel: 'Open gift ideas',
        targetScreen: 'contactDetail',
        canToggle: true
      };
    }

    if (id === 'choose-channel') {
      const done = checklistDone(id) || route.ready;
      return {
        id,
        label: item.label,
        done,
        status: statusFor(done),
        detail: route.detail,
        actionLabel: 'Open contact',
        targetScreen: 'contactDetail',
        canToggle: true
      };
    }

    const done = checklistDone('schedule-reminder') || reminderReady;
    return {
      id,
      label: item.label,
      done,
      status: statusFor(done),
      detail: done
        ? 'A reminder plan exists or the reminder step is complete.'
        : 'Plan reminders before the event day.',
      actionLabel: 'Plan reminders',
      targetScreen: 'setupCheck',
      canToggle: true
    };
  });

  const completedCount = steps.filter(step => step.done).length;
  const totalCount = steps.length;
  const nextStep = steps.find(step => !step.done);
  const isComplete = completedCount === totalCount;

  return {
    ok: true,
    event,
    contact,
    steps,
    completedCount,
    totalCount,
    isComplete,
    nextStep,
    summary: isComplete
      ? 'Event preparation is complete; review any message before sending.'
      : `${completedCount}/${totalCount} preparation steps complete. Next: ${nextStep?.label ?? 'review event'}.`
  };
};
