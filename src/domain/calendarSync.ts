import type { AppState, CalendarExportEntry, CalendarImportCandidate, RelationshipEvent } from './types';

const oneHourAfter = (iso: string) => {
  const date = new Date(iso);
  date.setHours(date.getHours() + 1);
  return date.toISOString();
};

const eventTypeFromTitle = (title: string): RelationshipEvent['type'] => {
  const normalized = title.toLowerCase();
  if (normalized.includes('anniversary')) {
    return normalized.includes('work') ? 'Work anniversary' : 'Anniversary';
  }
  if (normalized.includes('graduation')) {
    return 'Graduation';
  }
  if (normalized.includes('follow')) {
    return 'Follow-up';
  }
  return 'Birthday';
};

const nameFromTitle = (title: string) =>
  title
    .replace(/birthday|anniversary|work anniversary|graduation|follow-up|follow up/gi, '')
    .replace(/[:\-–—]/g, ' ')
    .trim()
    .replace(/\s+/g, ' ') || title.trim();

export const buildCalendarExportEntries = (state: AppState): CalendarExportEntry[] =>
  state.events.map(event => {
    const contact = state.contacts.find(item => item.id === event.contactId);
    const checklistDone = event.checklist.filter(item => item.done).length;
    return {
      id: `calendar-export-${event.id}`,
      eventId: event.id,
      title: `${event.type}: ${contact?.name ?? event.label}`,
      startDate: event.date,
      endDate: oneHourAfter(event.date),
      notes: `RelateAI reminder. Checklist ${checklistDone}/${event.checklist.length}. Review in RelateAI before sending any message.`
    };
  });

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
    const existingContact = contacts.find(item => item.name.toLowerCase() === contactName.toLowerCase());
    const contactId = existingContact?.id ?? `calendar-${candidate.sourceId.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
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
    const duplicate = events.some(
      item =>
        item.contactId === contactId &&
        item.type === type &&
        new Date(item.date).toDateString() === new Date(candidate.startDate).toDateString()
    );
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
        verified: false,
        source: 'Manual',
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
