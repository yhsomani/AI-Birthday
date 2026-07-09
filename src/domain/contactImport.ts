import type { AppState, Contact, ImportedContactRecord, RelationshipEvent } from './types';

const normalizePhone = (phone?: string) => phone?.replace(/[^\d+]/g, '').replace(/^00/, '+');

const normalizeEmail = (email?: string) => email?.trim().toLowerCase();

const normalizeName = (name: string) => name.trim().replace(/\s+/g, ' ').toLowerCase();

const makeContactId = (record: ImportedContactRecord) =>
  `import-${record.sourceId.replace(/[^a-zA-Z0-9_-]/g, '-')}`;

const canMerge = (contact: Contact, record: ImportedContactRecord) => {
  const phone = normalizePhone(record.phone);
  const email = normalizeEmail(record.email);
  if (phone && normalizePhone(contact.phone) === phone) {
    return true;
  }
  if (email && normalizeEmail(contact.email) === email) {
    return true;
  }
  return normalizeName(contact.name) === normalizeName(record.name);
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
    verified: false,
    source: 'Imported',
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
  records: ImportedContactRecord[]
): {
  contacts: Contact[];
  events: RelationshipEvent[];
  added: number;
  updated: number;
  skipped: number;
} => {
  let contacts = [...state.contacts];
  let events = [...state.events];
  let added = 0;
  let updated = 0;
  let skipped = 0;

  records.forEach(record => {
    const name = record.name.trim();
    if (!name) {
      skipped += 1;
      return;
    }

    const existing = contacts.find(contact => canMerge(contact, record));
    if (existing) {
      contacts = contacts.map(contact =>
        contact.id === existing.id
          ? {
              ...contact,
              phone: contact.phone ?? normalizePhone(record.phone),
              email: contact.email ?? normalizeEmail(record.email),
              relationship: contact.relationship === 'Contact' && record.relationship ? record.relationship : contact.relationship,
              notesSummary:
                contact.notesSummary || 'Imported contact. Add personal context before relying on AI drafts.'
            }
          : contact
      );
      const event = buildEvent(existing.id, record);
      if (event && !events.some(item => item.contactId === existing.id && item.type === 'Birthday')) {
        events = [event, ...events];
      }
      updated += 1;
      return;
    }

    const contactId = makeContactId(record);
    const contact: Contact = {
      id: contactId,
      name,
      relationship: record.relationship ?? 'Contact',
      group: 'Other',
      phone: normalizePhone(record.phone),
      email: normalizeEmail(record.email),
      preferredChannel: record.phone ? 'Manual' : record.email ? 'Email' : 'Manual',
      language: 'English',
      tone: ['Warm'],
      healthScore: 50,
      isVip: false,
      dnd: false,
      checkInCadenceDays: 60,
      notesSummary: 'Imported contact. Add personal context before relying on AI drafts.',
      annualGiftBudget: 0
    };
    contacts = [contact, ...contacts];
    const event = buildEvent(contactId, record);
    if (event) {
      events = [event, ...events];
    }
    added += 1;
  });

  return {
    contacts,
    events,
    added,
    updated,
    skipped
  };
};
