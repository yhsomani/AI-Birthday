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
  toCreate: CalendarExportEntry[];
  toUpdate: Array<{
    deviceEventId: string;
    entry: CalendarExportEntry;
  }>;
  staleDeviceEventIds: string[];
  unchangedCount: number;
}

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

const toTime = (value: string | Date | null | undefined) =>
  value ? new Date(value).getTime() : Number.NaN;

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

export const buildCalendarExportEntries = (state: AppState, now: Date = new Date()): CalendarExportEntry[] =>
  state.events.map(event => {
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

export const buildCalendarExportPlan = (
  desiredEntries: CalendarExportEntry[],
  deviceEvents: MirroredCalendarEvent[]
): CalendarExportPlan => {
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

  deviceEvents.forEach(deviceEvent => {
    if (claimedDeviceIds.has(deviceEvent.id)) {
      return;
    }
    const token = extractExportToken(deviceEvent.notes);
    if (token && !desiredTokens.has(token)) {
      staleDeviceEventIds.add(deviceEvent.id);
    }
  });

  return {
    toCreate,
    toUpdate,
    staleDeviceEventIds: [...staleDeviceEventIds],
    unchangedCount
  };
};

export const calendarCandidatesToEvents = (
  state: AppState,
  candidates: CalendarImportCandidate[]
): {
  contacts: AppState['contacts'];
  events: AppState['events'];
  addedContacts: number;
  addedEvents: number;
  skipped: number;
} => {
  let contacts = [...state.contacts];
  let events = [...state.events];
  let addedContacts = 0;
  let addedEvents = 0;
  let skipped = 0;

  candidates.forEach(candidate => {
    const title = candidate.title.trim();
    if (!title || Number.isNaN(new Date(candidate.startDate).getTime())) {
      skipped += 1;
      return;
    }
    const contactName = nameFromTitle(title);
    const contactId = `calendar-${candidate.sourceId.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
    const existingContact = contacts.find(item => item.id === contactId);
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
          annualGiftBudget: 0
        },
        ...contacts
      ];
      addedContacts += 1;
    }

    const type = eventTypeFromTitle(title);
    const candidateRecurrence = recurrenceFromDate(type, candidate.startDate);
    const duplicate = events.some(item => {
      if (item.contactId !== contactId || item.type !== type) {
        return false;
      }
      const existingRecurrence = recurrenceForEvent(item);
      if (existingRecurrence && candidateRecurrence) {
        return (
          existingRecurrence.month === candidateRecurrence.month &&
          existingRecurrence.day === candidateRecurrence.day
        );
      }
      return item.date.slice(0, 10) === candidate.startDate.slice(0, 10);
    });
    if (duplicate) {
      skipped += 1;
      return;
    }

    events = [
      {
        id: `calendar-event-${candidate.sourceId.replace(/[^a-zA-Z0-9_-]/g, '-')}`,
        contactId,
        type,
        label: title,
        date: candidate.startDate,
        recurrence: candidateRecurrence,
        verified: false,
        source: 'Imported',
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
    skipped
  };
};
