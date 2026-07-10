import type { AppState, Contact, ImportedContactRecord, RelationshipEvent } from './types';
import { recurrenceForEvent, recurrenceFromDate } from './occasionDates';
import {
  contactMatchesImportedRoute,
  contactMatchesImportedSource,
  importedContactRoutes,
  importedSourceIdentity,
  mergeImportedIdentity,
  normalizedContactName
} from './contactIdentity';

const makeContactId = (record: ImportedContactRecord) => `import-${record.sourceId.replace(/[^a-zA-Z0-9_-]/g, '-')}`;

export type ContactImportReviewItem = {
  sourceId: string;
  candidateContactIds: string[];
  reason: 'same-name' | 'multiple-route-matches' | 'missing-name' | 'invalid-birthday' | 'conflicting-birthday';
  conflictingEventIds?: string[];
  importedBirthday?: string;
  existingBirthdays?: { eventId: string; date: string }[];
  resolutionIssue?: 'candidate-no-longer-listed' | 'only-skip-allowed' | 'conflicting-event-no-longer-listed';
};

export type ContactImportResolution =
  | { action: 'merge'; candidateContactId: string }
  | { action: 'keep-separate' }
  | { action: 'skip' }
  | { action: 'keep-existing'; candidateContactId?: string }
  | { action: 'replace'; conflictingEventId: string; candidateContactId?: string }
  | { action: 'import-as-separate'; candidateContactId?: string };

/** Explicit review choices keyed by the stable provider source id. */
export type ContactImportResolutions = Readonly<Record<string, ContactImportResolution | undefined>>;

const normalizeImportedBirthday = (birthday: string): string | undefined => {
  const trimmed = birthday.trim();
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:$|T)/.exec(trimmed);
  if (!match) return undefined;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day, 12, 0, 0, 0));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    return undefined;
  }
  if (trimmed.includes('T') && Number.isNaN(new Date(trimmed).getTime())) return undefined;
  return date.toISOString();
};

const withImportedBirthday = (record: ImportedContactRecord, birthday: string | undefined): ImportedContactRecord => {
  const { birthday: _birthday, ...withoutBirthday } = record;
  return birthday ? { ...withoutBirthday, birthday } : withoutBirthday;
};

const importedEventIdentity = (record: ImportedContactRecord) => ({
  provider: 'Device contacts' as const,
  sourceId: record.sourceId
});

const addImportedEventIdentity = (event: RelationshipEvent, record: ImportedContactRecord): RelationshipEvent => {
  const identity = importedEventIdentity(record);
  return event.sourceIdentities?.some(
    item => item.provider === identity.provider && item.sourceId === identity.sourceId
  )
    ? event
    : { ...event, sourceIdentities: [...(event.sourceIdentities ?? []), identity] };
};

const buildEvent = (contactId: string, record: ImportedContactRecord): RelationshipEvent | undefined => {
  if (!record.birthday) {
    return undefined;
  }
  return {
    id: `event-${contactId}-birthday`,
    contactId,
    type: 'Birthday',
    label: `${record.name.trim()} birthday`,
    date: record.birthday,
    recurrence: recurrenceFromDate('Birthday', record.birthday),
    verified: false,
    source: 'Imported',
    sourceIdentities: [importedEventIdentity(record)],
    checklist: [
      { id: 'confirm-date', label: 'Confirm date', done: false },
      { id: 'improve-context', label: 'Add one personal detail', done: false },
      { id: 'write-wish', label: 'Write or review wish', done: false },
      { id: 'choose-channel', label: 'Choose send channel', done: false }
    ]
  };
};

export const importContacts = (
  state: AppState,
  records: ImportedContactRecord[],
  identities?: {
    contactIds: readonly string[];
    eventIds: readonly string[];
  },
  resolutions: ContactImportResolutions = {}
): {
  contacts: Contact[];
  events: RelationshipEvent[];
  added: number;
  updated: number;
  skipped: number;
  unresolved: number;
  reviewItems: ContactImportReviewItem[];
} => {
  let contacts = [...state.contacts];
  let events = [...state.events];
  let added = 0;
  let updated = 0;
  let skipped = 0;
  const reviewItems: ContactImportReviewItem[] = [];

  const commitContactUpdate = (existing: Contact, record: ImportedContactRecord) => {
    contacts = contacts.map(contact =>
      contact.id === existing.id
        ? {
            ...mergeImportedIdentity(contact, record),
            relationship:
              contact.relationship === 'Contact' && record.relationship ? record.relationship : contact.relationship,
            notesSummary: contact.notesSummary || 'Imported contact. Add personal context before relying on AI drafts.'
          }
        : contact
    );
  };

  const stageBirthdayConflict = (
    existing: Contact,
    record: ImportedContactRecord,
    birthdayEvents: RelationshipEvent[],
    resolutionIssue?: ContactImportReviewItem['resolutionIssue']
  ) => {
    reviewItems.push({
      sourceId: record.sourceId,
      candidateContactIds: [existing.id],
      reason: 'conflicting-birthday',
      conflictingEventIds: birthdayEvents.map(event => event.id).sort(),
      importedBirthday: record.birthday,
      existingBirthdays: birthdayEvents
        .map(event => ({ eventId: event.id, date: event.date }))
        .sort((left, right) => left.eventId.localeCompare(right.eventId)),
      ...(resolutionIssue ? { resolutionIssue } : {})
    });
  };

  const updateExisting = (
    existing: Contact,
    record: ImportedContactRecord,
    index: number,
    resolution: ContactImportResolution | undefined
  ) => {
    const event = buildEvent(existing.id, record);
    if (!event) {
      commitContactUpdate(existing, record);
      updated += 1;
      return;
    }

    const existingBirthdays = events.filter(item => item.contactId === existing.id && item.type === 'Birthday');
    const importedRecurrence = recurrenceForEvent(event);
    const sameBirthday = existingBirthdays.find(item => {
      const recurrence = recurrenceForEvent(item);
      return (
        recurrence &&
        importedRecurrence &&
        recurrence.month === importedRecurrence.month &&
        recurrence.day === importedRecurrence.day
      );
    });
    if (sameBirthday) {
      commitContactUpdate(existing, record);
      events = events.map(item => (item.id === sameBirthday.id ? addImportedEventIdentity(item, record) : item));
      updated += 1;
      return;
    }

    if (existingBirthdays.length > 0) {
      if (resolution?.action === 'keep-existing') {
        commitContactUpdate(existing, record);
        updated += 1;
        return;
      }
      if (resolution?.action === 'replace') {
        const replaced = existingBirthdays.find(item => item.id === resolution.conflictingEventId);
        if (!replaced) {
          stageBirthdayConflict(existing, record, existingBirthdays, 'conflicting-event-no-longer-listed');
          return;
        }
        commitContactUpdate(existing, record);
        events = events.map(item =>
          item.id === replaced.id
            ? addImportedEventIdentity(
                {
                  ...item,
                  date: event.date,
                  recurrence: event.recurrence,
                  verified: false,
                  checklist: item.checklist.map(checklistItem =>
                    checklistItem.id === 'confirm-date' ? { ...checklistItem, done: false } : checklistItem
                  )
                },
                record
              )
            : item
        );
        updated += 1;
        return;
      }
      if (resolution?.action === 'import-as-separate') {
        commitContactUpdate(existing, record);
        events = [{ ...event, id: identities?.eventIds[index] ?? event.id }, ...events];
        updated += 1;
        return;
      }
      stageBirthdayConflict(existing, record, existingBirthdays);
      return;
    }

    commitContactUpdate(existing, record);
    events = [{ ...event, id: identities?.eventIds[index] ?? event.id }, ...events];
    updated += 1;
  };

  const addSeparate = (record: ImportedContactRecord, index: number, name: string) => {
    const contactId = identities?.contactIds[index] ?? makeContactId(record);
    const routes = importedContactRoutes(record);
    const contact: Contact = {
      id: contactId,
      name,
      relationship: record.relationship ?? 'Contact',
      group: 'Other',
      phone: routes.find(route => route.type === 'Phone' && route.primary)?.value,
      email: routes.find(route => route.type === 'Email' && route.primary)?.value,
      preferredChannel: routes.some(route => route.type === 'Phone')
        ? 'Manual'
        : routes.some(route => route.type === 'Email')
          ? 'Email'
          : 'Manual',
      language: 'English',
      tone: ['Warm'],
      healthScore: 50,
      isVip: false,
      dnd: false,
      checkInCadenceDays: 60,
      notesSummary: 'Imported contact. Add personal context before relying on AI drafts.',
      annualGiftBudget: 0,
      routes,
      sourceIdentities: [importedSourceIdentity(record)]
    };
    contacts = [contact, ...contacts];
    const event = buildEvent(contactId, record);
    if (event) {
      events = [{ ...event, id: identities?.eventIds[index] ?? event.id }, ...events];
    }
    added += 1;
  };

  records.forEach((record, index) => {
    const name = record.name.trim();
    if (!name) {
      const routes = importedContactRoutes(record);
      if (routes.length === 0) {
        skipped += 1;
        return;
      }
      if (resolutions[record.sourceId]?.action === 'skip') {
        skipped += 1;
        return;
      }
      const candidateContactIds = contacts
        .filter(
          contact => contactMatchesImportedSource(contact, record) || contactMatchesImportedRoute(contact, record)
        )
        .map(contact => contact.id);
      reviewItems.push({
        sourceId: record.sourceId,
        candidateContactIds,
        reason: 'missing-name'
      });
      return;
    }
    const resolution = resolutions[record.sourceId];
    if (resolution?.action === 'skip') {
      skipped += 1;
      return;
    }

    const birthday = record.birthday === undefined ? undefined : normalizeImportedBirthday(record.birthday);
    const normalizedRecord = withImportedBirthday(record, birthday);

    const sourceMatches = contacts.filter(contact => contactMatchesImportedSource(contact, normalizedRecord));
    const routeMatches = contacts.filter(contact => contactMatchesImportedRoute(contact, normalizedRecord));
    const exactMatches = [
      ...new Map([...sourceMatches, ...routeMatches].map(contact => [contact.id, contact])).values()
    ];
    if (exactMatches.length > 1) {
      reviewItems.push({
        sourceId: record.sourceId,
        candidateContactIds: exactMatches.map(contact => contact.id),
        reason: 'multiple-route-matches',
        ...(resolution ? { resolutionIssue: 'only-skip-allowed' as const } : {})
      });
      return;
    }
    const existing = exactMatches[0];
    if (record.birthday !== undefined && !birthday) {
      if (existing && resolution?.action === 'keep-existing') {
        updateExisting(existing, normalizedRecord, index, resolution);
        return;
      }
      reviewItems.push({
        sourceId: record.sourceId,
        candidateContactIds: existing ? [existing.id] : [],
        reason: 'invalid-birthday'
      });
      return;
    }
    if (existing) {
      updateExisting(existing, normalizedRecord, index, resolution);
      return;
    }

    const sameNameCandidates = contacts.filter(
      contact => normalizedContactName(contact.name) === normalizedContactName(name)
    );
    if (sameNameCandidates.length > 0) {
      if (resolution?.action === 'keep-separate') {
        addSeparate(normalizedRecord, index, name);
        return;
      }
      if (resolution?.action === 'merge') {
        const selected = sameNameCandidates.find(contact => contact.id === resolution.candidateContactId);
        if (selected) {
          updateExisting(selected, normalizedRecord, index, resolution);
          return;
        }
      }
      if (
        resolution?.action === 'keep-existing' ||
        resolution?.action === 'replace' ||
        resolution?.action === 'import-as-separate'
      ) {
        const selected = resolution.candidateContactId
          ? sameNameCandidates.find(contact => contact.id === resolution.candidateContactId)
          : undefined;
        if (selected) {
          updateExisting(selected, normalizedRecord, index, resolution);
          return;
        }
      }
      reviewItems.push({
        sourceId: record.sourceId,
        candidateContactIds: sameNameCandidates.map(contact => contact.id),
        reason: 'same-name',
        ...(resolution?.action === 'merge' ||
        resolution?.action === 'keep-existing' ||
        resolution?.action === 'replace' ||
        resolution?.action === 'import-as-separate'
          ? { resolutionIssue: 'candidate-no-longer-listed' as const }
          : {})
      });
      return;
    }

    if (resolution?.action === 'merge') {
      reviewItems.push({
        sourceId: record.sourceId,
        candidateContactIds: [],
        reason: 'same-name',
        resolutionIssue: 'candidate-no-longer-listed'
      });
      return;
    }

    addSeparate(normalizedRecord, index, name);
  });

  return {
    contacts,
    events,
    added,
    updated,
    skipped,
    unresolved: reviewItems.length,
    reviewItems
  };
};
