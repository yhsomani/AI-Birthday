import type { Contact, EventType, RelationshipEvent } from './types';

export const manualEventTypes: EventType[] = [
  'Birthday',
  'Anniversary',
  'Work anniversary',
  'Custom',
  'Graduation',
  'Holiday',
  'Revival',
  'Follow-up'
];

export type ManualEventInput = {
  contactId?: string;
  newContactName?: string;
  eventType: EventType;
  label: string;
  date: string;
};

export type NormalizedManualEventInput = {
  contactId?: string;
  newContactName?: string;
  eventType: EventType;
  label: string;
  dateIso: string;
  dateKey: string;
};

export type ManualEventValidation =
  | {
      ok: true;
      normalized: NormalizedManualEventInput;
      warnings: string[];
    }
  | {
      ok: false;
      errors: string[];
      warnings: string[];
    };

const datePattern = /^(\d{4})-(\d{2})-(\d{2})$/;

const parseDate = (value: string) => {
  const trimmed = value.trim();
  const match = datePattern.exec(trimmed);
  if (!match) {
    return undefined;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day, 12, 0, 0));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return undefined;
  }

  return {
    dateIso: date.toISOString(),
    dateKey: trimmed
  };
};

const normalizeName = (value: string) => value.trim().replace(/\s+/g, ' ').toLowerCase();

export const validateManualEventInput = (
  input: ManualEventInput,
  contacts: Contact[],
  events: RelationshipEvent[]
): ManualEventValidation => {
  const errors: string[] = [];
  const warnings: string[] = [];
  const label = input.label.trim().replace(/\s+/g, ' ');
  const newContactName = input.newContactName?.trim().replace(/\s+/g, ' ');
  const selectedContact = input.contactId ? contacts.find(contact => contact.id === input.contactId) : undefined;

  if (!manualEventTypes.includes(input.eventType)) {
    errors.push('Choose a supported event type.');
  }

  if (!selectedContact && (!newContactName || newContactName.length < 2)) {
    errors.push('Choose an existing contact or enter a new contact name.');
  }

  if (input.contactId && !selectedContact) {
    errors.push('The selected contact is no longer available.');
  }

  if (label.length < 2) {
    errors.push('Enter a clear event label.');
  }

  const parsedDate = parseDate(input.date);
  if (!parsedDate) {
    errors.push('Enter a valid date in YYYY-MM-DD format.');
  }

  if (errors.length > 0 || !parsedDate) {
    return {
      ok: false,
      errors,
      warnings
    };
  }

  const contactName = selectedContact?.name ?? newContactName ?? '';
  const contactNameKey = normalizeName(contactName);
  const sameDayEvents = events.filter(event => event.date.slice(0, 10) === parsedDate.dateKey);
  const conflicts = sameDayEvents.filter(event => {
    if (selectedContact) {
      return event.contactId === selectedContact.id;
    }
    const existingContact = contacts.find(contact => contact.id === event.contactId);
    return existingContact ? normalizeName(existingContact.name) === contactNameKey : false;
  });

  const duplicate = conflicts.find(event => event.type === input.eventType);
  if (duplicate) {
    warnings.push(`Possible duplicate: ${duplicate.label} already exists for this contact on this date.`);
  } else if (conflicts.length > 0) {
    warnings.push('This contact already has another relationship event on this date.');
  }

  return {
    ok: true,
    normalized: {
      contactId: selectedContact?.id,
      newContactName,
      eventType: input.eventType,
      label,
      dateIso: parsedDate.dateIso,
      dateKey: parsedDate.dateKey
    },
    warnings
  };
};

export const buildDefaultEventChecklist = (eventType: EventType) => {
  const writeLabel =
    eventType === 'Follow-up'
      ? 'Write check-in'
      : eventType === 'Work anniversary'
        ? 'Prepare concise note'
        : 'Write or review wish';

  return [
    { id: 'confirm-date', label: 'Confirm date', done: true },
    { id: 'improve-context', label: 'Add one personal memory', done: false },
    { id: 'write-message', label: writeLabel, done: false },
    { id: 'choose-channel', label: 'Choose send channel', done: false }
  ];
};
