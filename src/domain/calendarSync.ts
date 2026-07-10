import type { AppState, CalendarExportEntry, CalendarImportCandidate, RelationshipEvent } from './types';
import { eventOccurrenceIso, recurrenceForEvent, recurrenceFromDate } from './occasionDates';

export interface MirroredCalendarEvent {
  id: string;
  title?: string | null;
  startDate: string | Date;
  endDate?: string | Date | null;
  notes?: string | null;
  allDay?: boolean;
  recurrenceRule?: {
    frequency: 'yearly';
  };
}

export interface CalendarExportPlan {
  mode: CalendarExportMode;
  toCreate: CalendarExportEntry[];
  toUpdate: {
    deviceEventId: string;
    entry: CalendarExportEntry;
  }[];
  staleDeviceEventIds: string[];
  unchangedCount: number;
}

export type CalendarExportMode = 'full' | 'selected';

export type CalendarExportSelection =
  | {
      ok: true;
      mode: CalendarExportMode;
      entries: CalendarExportEntry[];
      eligibleCount: number;
      selectedCount: number;
    }
  | {
      ok: false;
      reason: 'empty-selection' | 'duplicate-selection' | 'event-not-exportable';
    };

export type CalendarImportReviewReason =
  'same-name' | 'multiple-source-matches' | 'source-content-conflict' | 'conflicting-date';

export interface CalendarImportReviewItem {
  sourceId: string;
  reason: CalendarImportReviewReason;
  candidateContactIds: string[];
  conflictingEventIds: string[];
}

export type CalendarImportResolution =
  | { action: 'skip' }
  | { action: 'create-separate' }
  | { action: 'merge-contact'; candidateContactId: string }
  | { action: 'merge-event'; candidateEventId: string };

/** Explicit review choices keyed by the stable calendar event source id. */
export type CalendarImportResolutions = Readonly<Record<string, CalendarImportResolution | undefined>>;

const oneDayAfter = (iso: string) => {
  const date = new Date(iso);
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString();
};

const exportTokenLine = (entryId: string) => `RelateAI export: ${entryId}`;

const extractExportToken = (notes: string | null | undefined) => {
  const line = notes
    ?.split('\n')
    .map(item => item.trim())
    .find(item => item.startsWith('RelateAI export:'));
  return line?.replace('RelateAI export:', '').trim();
};

const toTime = (value: string | Date | null | undefined) => (value ? new Date(value).getTime() : Number.NaN);

const sameInstant = (left: string | Date | null | undefined, right: string | Date | null | undefined) =>
  toTime(left) === toTime(right);

const sameExportContent = (deviceEvent: MirroredCalendarEvent, entry: CalendarExportEntry) =>
  (deviceEvent.title ?? '') === entry.title &&
  sameInstant(deviceEvent.startDate, entry.startDate) &&
  sameInstant(deviceEvent.endDate, entry.endDate) &&
  (deviceEvent.allDay ?? false) === (entry.allDay ?? false) &&
  deviceEvent.recurrenceRule?.frequency === entry.recurrenceRule?.frequency &&
  (deviceEvent.notes ?? '') === entry.notes;

const isRelateExportEvent = (deviceEvent: MirroredCalendarEvent) =>
  Boolean(extractExportToken(deviceEvent.notes)) || Boolean(deviceEvent.notes?.includes('RelateAI reminder.'));

const legacyRelateExportMatches = (deviceEvent: MirroredCalendarEvent, entry: CalendarExportEntry) =>
  isRelateExportEvent(deviceEvent) &&
  !extractExportToken(deviceEvent.notes) &&
  (deviceEvent.title ?? '') === entry.title &&
  sameInstant(deviceEvent.startDate, entry.startDate);

const eventTypeFromTitle = (title: string): RelationshipEvent['type'] => {
  const normalized = title.toLowerCase();
  if (normalized.includes('work anniversary')) {
    return 'Work anniversary';
  }
  if (normalized.includes('anniversary')) {
    return 'Anniversary';
  }
  if (normalized.includes('birthday')) {
    return 'Birthday';
  }
  if (normalized.includes('graduation')) {
    return 'Graduation';
  }
  if (normalized.includes('follow')) {
    return 'Follow-up';
  }
  return 'Custom';
};

const nameFromTitle = (title: string) =>
  title
    .replace(/work anniversary|anniversary|birthday|graduation|follow-up|follow up/gi, '')
    .replace(/[:\-–—]/g, ' ')
    .trim()
    .replace(/\s+/g, ' ') || title.trim();

const normalizedName = (value: string) => value.trim().replace(/\s+/g, ' ').toLowerCase();

const hasCalendarIdentity = (
  identities: RelationshipEvent['sourceIdentities'] | AppState['contacts'][number]['sourceIdentities'],
  sourceId: string
) => identities?.some(identity => identity.provider === 'Calendar' && identity.sourceId === sourceId) ?? false;

const addCalendarIdentity = <
  T extends { sourceIdentities?: { provider: 'Device contacts' | 'Calendar' | 'Local'; sourceId: string }[] }
>(
  value: T,
  sourceId: string
): T =>
  hasCalendarIdentity(value.sourceIdentities, sourceId)
    ? value
    : {
        ...value,
        sourceIdentities: [...(value.sourceIdentities ?? []), { provider: 'Calendar' as const, sourceId }]
      };

const sameImportedOccurrence = (event: RelationshipEvent, type: RelationshipEvent['type'], date: string) => {
  if (event.type !== type) return false;
  const existingRecurrence = recurrenceForEvent(event);
  const candidateRecurrence = recurrenceFromDate(type, date);
  if (existingRecurrence && candidateRecurrence) {
    return existingRecurrence.month === candidateRecurrence.month && existingRecurrence.day === candidateRecurrence.day;
  }
  return event.date.slice(0, 10) === date.slice(0, 10);
};

export const buildCalendarExportEntries = (state: AppState, now: Date = new Date()): CalendarExportEntry[] =>
  state.events
    .filter(event => {
      const contact = state.contacts.find(item => item.id === event.contactId);
      return Boolean(contact && !contact.archivedAt);
    })
    .map(event => {
      const contact = state.contacts.find(item => item.id === event.contactId);
      const checklistDone = event.checklist.filter(item => item.done).length;
      const occurrence = eventOccurrenceIso(event, now) ?? event.date;
      const recurring = Boolean(recurrenceForEvent(event));
      return {
        id: `calendar-export-${event.id}`,
        eventId: event.id,
        title: `${event.type}: ${contact?.name ?? event.label}`,
        startDate: occurrence,
        endDate: oneDayAfter(occurrence),
        allDay: true,
        recurrenceRule: recurring ? { frequency: 'yearly' as const } : undefined,
        notes: [
          `RelateAI reminder. Checklist ${checklistDone}/${event.checklist.length}. Review in RelateAI before sending any message.`,
          exportTokenLine(`calendar-export-${event.id}`)
        ].join('\n')
      };
    });

export const resolveCalendarExportSelection = (
  state: AppState,
  eventIds: readonly string[] | undefined,
  now: Date = new Date()
): CalendarExportSelection => {
  const eligibleEntries = buildCalendarExportEntries(state, now);
  if (eventIds === undefined) {
    return {
      ok: true,
      mode: 'full',
      entries: eligibleEntries,
      eligibleCount: eligibleEntries.length,
      selectedCount: 0
    };
  }
  if (eventIds.length === 0) return { ok: false, reason: 'empty-selection' };
  if (new Set(eventIds).size !== eventIds.length) return { ok: false, reason: 'duplicate-selection' };

  const entriesByEventId = new Map(eligibleEntries.map(entry => [entry.eventId, entry]));
  const selectedEntries: CalendarExportEntry[] = [];
  for (const eventId of eventIds) {
    const entry = entriesByEventId.get(eventId);
    if (!entry) return { ok: false, reason: 'event-not-exportable' };
    selectedEntries.push(entry);
  }

  return {
    ok: true,
    mode: 'selected',
    entries: selectedEntries,
    eligibleCount: eligibleEntries.length,
    selectedCount: selectedEntries.length
  };
};

export const buildCalendarExportPlan = (
  desiredEntries: CalendarExportEntry[],
  deviceEvents: MirroredCalendarEvent[],
  options: { mode?: CalendarExportMode } = {}
): CalendarExportPlan => {
  const mode = options.mode ?? 'full';
  const desiredTokens = new Set(desiredEntries.map(entry => entry.id));
  const claimedDeviceIds = new Set<string>();
  const staleDeviceEventIds = new Set<string>();
  const toCreate: CalendarExportEntry[] = [];
  const toUpdate: CalendarExportPlan['toUpdate'] = [];
  let unchangedCount = 0;

  for (const entry of desiredEntries) {
    const tokenMatches = deviceEvents.filter(deviceEvent => extractExportToken(deviceEvent.notes) === entry.id);
    const legacyMatches = deviceEvents.filter(
      deviceEvent =>
        legacyRelateExportMatches(deviceEvent, entry) && !tokenMatches.some(match => match.id === deviceEvent.id)
    );
    const candidates = [...tokenMatches, ...legacyMatches];
    const [primary, ...duplicates] = candidates;

    duplicates.forEach(duplicate => {
      if (isRelateExportEvent(duplicate)) {
        staleDeviceEventIds.add(duplicate.id);
      }
    });

    if (!primary) {
      toCreate.push(entry);
      continue;
    }

    claimedDeviceIds.add(primary.id);
    if (sameExportContent(primary, entry)) {
      unchangedCount += 1;
    } else {
      toUpdate.push({
        deviceEventId: primary.id,
        entry
      });
    }
  }

  if (mode === 'full') {
    deviceEvents.forEach(deviceEvent => {
      if (claimedDeviceIds.has(deviceEvent.id)) {
        return;
      }
      const token = extractExportToken(deviceEvent.notes);
      if (token && !desiredTokens.has(token)) {
        staleDeviceEventIds.add(deviceEvent.id);
      }
    });
  }

  return {
    mode,
    toCreate,
    toUpdate,
    staleDeviceEventIds: [...staleDeviceEventIds],
    unchangedCount
  };
};

export const calendarCandidatesToEvents = (
  state: AppState,
  candidates: CalendarImportCandidate[],
  identities?: {
    contactIds: readonly string[];
    eventIds: readonly string[];
  },
  resolutions: CalendarImportResolutions = {}
): {
  contacts: AppState['contacts'];
  events: AppState['events'];
  addedContacts: number;
  addedEvents: number;
  skipped: number;
  unresolved: number;
  reviewItems: CalendarImportReviewItem[];
} => {
  let contacts = [...state.contacts];
  let events = [...state.events];
  let addedContacts = 0;
  let addedEvents = 0;
  let skipped = 0;
  const reviewItems: CalendarImportReviewItem[] = [];

  const stageReview = (
    sourceId: string,
    reason: CalendarImportReviewReason,
    candidateContactIds: readonly string[],
    conflictingEventIds: readonly string[]
  ) => {
    reviewItems.push({
      sourceId,
      reason,
      candidateContactIds: [...new Set(candidateContactIds)].sort(),
      conflictingEventIds: [...new Set(conflictingEventIds)].sort()
    });
  };

  const persistIdentityOnContact = (contactId: string, sourceId: string) => {
    contacts = contacts.map(contact => (contact.id === contactId ? addCalendarIdentity(contact, sourceId) : contact));
  };

  const persistIdentityOnEvent = (eventId: string, sourceId: string) => {
    events = events.map(event => (event.id === eventId ? addCalendarIdentity(event, sourceId) : event));
  };

  candidates.forEach((candidate, index) => {
    const sourceId = candidate.sourceId.trim();
    const title = candidate.title.trim();
    if (!sourceId || !title || Number.isNaN(new Date(candidate.startDate).getTime())) {
      skipped += 1;
      return;
    }
    const resolution = resolutions[sourceId];
    if (resolution?.action === 'skip') {
      skipped += 1;
      return;
    }
    const contactName = nameFromTitle(title);
    const type = eventTypeFromTitle(title);
    const sourceEventMatches = events.filter(event => hasCalendarIdentity(event.sourceIdentities, sourceId));
    if (sourceEventMatches.length > 1) {
      stageReview(
        sourceId,
        'multiple-source-matches',
        sourceEventMatches.map(event => event.contactId),
        sourceEventMatches.map(event => event.id)
      );
      return;
    }
    const sourceEvent = sourceEventMatches[0];
    if (sourceEvent) {
      const sourceContact = contacts.find(contact => contact.id === sourceEvent.contactId);
      const sameMeaning =
        Boolean(sourceContact) &&
        normalizedName(sourceContact?.name ?? '') === normalizedName(contactName) &&
        sameImportedOccurrence(sourceEvent, type, candidate.startDate);
      if (!sameMeaning) {
        stageReview(sourceId, 'source-content-conflict', [sourceEvent.contactId], [sourceEvent.id]);
        return;
      }
      persistIdentityOnEvent(sourceEvent.id, sourceId);
      persistIdentityOnContact(sourceEvent.contactId, sourceId);
      skipped += 1;
      return;
    }

    if (resolution?.action === 'merge-event') {
      const selectedEvent = events.find(event => event.id === resolution.candidateEventId);
      const selectedContact = selectedEvent
        ? contacts.find(contact => contact.id === selectedEvent.contactId)
        : undefined;
      if (
        selectedEvent &&
        selectedContact &&
        normalizedName(selectedContact.name) === normalizedName(contactName) &&
        sameImportedOccurrence(selectedEvent, type, candidate.startDate)
      ) {
        persistIdentityOnEvent(selectedEvent.id, sourceId);
        persistIdentityOnContact(selectedContact.id, sourceId);
        skipped += 1;
        return;
      }
    }

    const sourceContactMatches = contacts.filter(contact => hasCalendarIdentity(contact.sourceIdentities, sourceId));
    if (sourceContactMatches.length > 1) {
      stageReview(
        sourceId,
        'multiple-source-matches',
        sourceContactMatches.map(contact => contact.id),
        []
      );
      return;
    }

    const sameNameContacts = contacts.filter(contact => normalizedName(contact.name) === normalizedName(contactName));
    let existingContact: AppState['contacts'][number] | undefined = sourceContactMatches[0];
    if (!existingContact && resolution?.action === 'merge-contact') {
      existingContact = sameNameContacts.find(contact => contact.id === resolution.candidateContactId);
    }
    const createSeparate = resolution?.action === 'create-separate';
    if (!existingContact && sameNameContacts.length > 0 && !createSeparate) {
      const sameNameEventIds = events
        .filter(
          event =>
            sameNameContacts.some(contact => contact.id === event.contactId) &&
            sameImportedOccurrence(event, type, candidate.startDate)
        )
        .map(event => event.id);
      stageReview(
        sourceId,
        sameNameEventIds.length > 0 ? 'conflicting-date' : 'same-name',
        sameNameContacts.map(contact => contact.id),
        sameNameEventIds
      );
      return;
    }

    const contactId =
      existingContact?.id ?? identities?.contactIds[index] ?? `calendar-${sourceId.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
    if (!existingContact) {
      contacts = [
        {
          id: contactId,
          name: contactName,
          relationship: 'Contact',
          group: 'Other',
          preferredChannel: 'Manual',
          language: 'English',
          tone: ['Warm'],
          healthScore: 50,
          isVip: false,
          dnd: false,
          checkInCadenceDays: 60,
          notesSummary: 'Imported from calendar. Confirm event details before sending.',
          annualGiftBudget: 0,
          sourceIdentities: [{ provider: 'Calendar', sourceId }]
        },
        ...contacts
      ];
      addedContacts += 1;
    }

    const candidateRecurrence = recurrenceFromDate(type, candidate.startDate);
    const dateConflicts = events.filter(
      event => event.contactId === contactId && sameImportedOccurrence(event, type, candidate.startDate)
    );
    if (dateConflicts.length > 0) {
      stageReview(
        sourceId,
        'conflicting-date',
        [contactId],
        dateConflicts.map(event => event.id)
      );
      return;
    }
    if (existingContact) persistIdentityOnContact(existingContact.id, sourceId);

    events = [
      {
        id: identities?.eventIds[index] ?? `calendar-event-${sourceId.replace(/[^a-zA-Z0-9_-]/g, '-')}`,
        contactId,
        type,
        label: title,
        date: candidate.startDate,
        recurrence: candidateRecurrence,
        verified: false,
        source: 'Imported',
        sourceIdentities: [{ provider: 'Calendar', sourceId }],
        checklist: [
          { id: 'confirm-date', label: 'Confirm imported date', done: false },
          { id: 'improve-context', label: 'Add personal context', done: false },
          { id: 'write-wish', label: 'Write or review message', done: false }
        ]
      },
      ...events
    ];
    addedEvents += 1;
  });

  return {
    contacts,
    events,
    addedContacts,
    addedEvents,
    skipped,
    unresolved: reviewItems.length,
    reviewItems
  };
};
