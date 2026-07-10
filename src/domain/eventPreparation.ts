import type {
  AppState,
  Contact,
  EventChecklistItem,
  EventType,
  MessageChannel,
  RelationshipEvent,
  Screen
} from './types';

export type EventPreparationStepId =
  | 'confirm-date'
  | 'improve-context'
  | 'write-message'
  | 'decide-gift'
  | 'choose-channel'
  | 'schedule-reminder';

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

const knownChecklistIds = new Set(Object.values(checklistAliases).flat());

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
    const aliasIds = checklistAliases[definition.id as EventPreparationStepId];
    const existing = checklist.find(item => aliasIds.includes(item.id));
    return {
      ...definition,
      done: existing?.done ?? false
    };
  });

export const toggleEventPreparationChecklistStep = (
  eventType: EventType,
  checklist: EventChecklistItem[],
  stepId: EventPreparationStepId
): EventChecklistItem[] => {
  const normalized = normalizeEventPreparationChecklist(eventType, checklist).map(item =>
    item.id === stepId ? { ...item, done: !item.done } : item
  );
  const customItems = checklist.filter(item => !knownChecklistIds.has(item.id));
  return [...normalized, ...customItems];
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
    return contact.phone
      ? { ready: true, detail: 'WhatsApp handoff has a phone number.' }
      : { ready: false, detail: 'Add a phone number or choose another channel.' };
  }

  if (!state.settings.emailEnabled) {
    return { ready: false, detail: 'Email is disabled in Settings.' };
  }
  return contact.email
    ? { ready: true, detail: 'Email route has an email address.' }
    : { ready: false, detail: 'Add an email address or choose another channel.' };
};

const statusFor = (done: boolean): EventPreparationStatus => (done ? 'Done' : 'Needs action');

export const buildEventPreparationPlan = (state: AppState, eventId: string): EventPreparationPlan => {
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
  const checklist = normalizeEventPreparationChecklist(event.type, event.checklist);
  const checklistDone = (stepId: EventPreparationStepId) =>
    checklist.find(item => item.id === stepId)?.done ?? false;
  const publicMemoryCount = state.memories.filter(
    memory => memory.contactId === event.contactId && memory.category !== 'Private'
  ).length;
  const messageReady = state.messages.some(message => message.eventId === event.id && message.status !== 'Rejected');
  const giftHistoryCount = state.gifts.filter(gift => gift.contactId === event.contactId).length;
  const reminderReady = state.reminderPlans.some(plan => plan.eventId === event.id);
  const route = channelDetail(state, contact, contact?.preferredChannel ?? 'Manual');

  const steps: EventPreparationStep[] = checklist.map(item => {
    const id = item.id as EventPreparationStepId;
    if (id === 'confirm-date') {
      const done = item.done || event.verified;
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
      const done = item.done || publicMemoryCount > 0;
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
      const done = item.done || messageReady;
      return {
        id,
        label: item.label,
        done,
        status: statusFor(done),
        detail: done ? 'A draft or checklist mark exists for this event.' : 'Create a review-first draft for this event.',
        actionLabel: 'Write message',
        targetScreen: 'wishPreview',
        canToggle: true
      };
    }

    if (id === 'decide-gift') {
      const done = item.done;
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
      const done = item.done || route.ready;
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
      detail: done ? 'A reminder plan exists or the reminder step is complete.' : 'Plan reminders before the event day.',
      actionLabel: 'Plan reminders',
      targetScreen: 'more',
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
