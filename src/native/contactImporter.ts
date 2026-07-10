import type {
  ContactDate,
  ContactField,
  ExistingDate,
  ExistingEmail,
  ExistingPhone,
  PartialContactDetails
} from 'expo-contacts';
import type { ImportedContactRecord } from '../domain/types';

const CONTACT_IMPORT_PAGE_SIZE = 250;

const CONTACT_IMPORT_FIELDS = [
  'fullName' as ContactField.FULL_NAME,
  'givenName' as ContactField.GIVEN_NAME,
  'familyName' as ContactField.FAMILY_NAME,
  'phones' as ContactField.PHONES,
  'emails' as ContactField.EMAILS,
  'birthday' as ContactField.BIRTHDAY,
  'dates' as ContactField.DATES
] as const;

type ContactImportDetails = PartialContactDetails<typeof CONTACT_IMPORT_FIELDS>;

export interface ContactsImportApi {
  requestPermissionsAsync(): Promise<{ status: string }>;
  Contact: {
    getAllDetails(
      fields: typeof CONTACT_IMPORT_FIELDS,
      options: { limit: number; offset: number }
    ): Promise<ContactImportDetails[]>;
  };
}

const buildBirthday = (birthday: ContactDate | null | undefined): string | undefined => {
  if (!birthday?.day || !birthday.month) {
    return undefined;
  }
  const year = birthday.year ?? new Date().getFullYear();
  return new Date(year, birthday.month - 1, birthday.day).toISOString();
};

const birthdayFrom = (contact: ContactImportDetails) => {
  if (contact.birthday) {
    return contact.birthday;
  }

  return contact.dates?.find(
    (date: ExistingDate) => date.label?.trim().toLowerCase() === 'birthday'
  )?.date;
};

const firstPhone = (phones: ExistingPhone[] | undefined) =>
  phones?.find(phone => Boolean(phone.number?.trim()))?.number;

const firstEmail = (emails: ExistingEmail[] | undefined) =>
  emails?.find(email => Boolean(email.address?.trim()))?.address;

const toImportedRecord = (contact: ContactImportDetails): ImportedContactRecord => {
  const composedName = [contact.givenName, contact.familyName].filter(Boolean).join(' ').trim();
  const name = contact.fullName?.trim() || composedName;

  return {
    sourceId: contact.id || `${name || 'contact'}-device`,
    name,
    phone: firstPhone(contact.phones),
    email: firstEmail(contact.emails),
    birthday: buildBirthday(birthdayFrom(contact)),
    relationship: undefined
  };
};

/**
 * Imports a complete, bounded page sequence through Expo 57's Contact class API.
 * Exported for executable adapter tests; production callers should use
 * `importDeviceContacts`, which supplies the installed native module.
 */
export const importDeviceContactsWithApi = async (
  Contacts: ContactsImportApi
): Promise<ImportedContactRecord[]> => {
  const permission = await Contacts.requestPermissionsAsync();
  if (permission.status !== 'granted') {
    throw new Error('Contacts permission was not granted.');
  }

  const imported: ImportedContactRecord[] = [];
  const seenContactIds = new Set<string>();
  let offset = 0;

  while (true) {
    const page = await Contacts.Contact.getAllDetails(CONTACT_IMPORT_FIELDS, {
      limit: CONTACT_IMPORT_PAGE_SIZE,
      offset
    });

    if (page.length === 0) {
      break;
    }

    let newRecords = 0;
    for (const contact of page) {
      if (seenContactIds.has(contact.id)) {
        continue;
      }
      seenContactIds.add(contact.id);
      imported.push(toImportedRecord(contact));
      newRecords += 1;
    }

    if (page.length >= CONTACT_IMPORT_PAGE_SIZE && newRecords === 0) {
      throw new Error('Contacts pagination did not advance. Import was stopped to avoid incomplete data.');
    }

    if (page.length < CONTACT_IMPORT_PAGE_SIZE) {
      break;
    }
    offset += page.length;
  }

  return imported;
};

export const importDeviceContacts = async (): Promise<ImportedContactRecord[]> => {
  const Contacts = await import('expo-contacts');
  return importDeviceContactsWithApi(Contacts);
};

export const sampleImportRecords = (): ImportedContactRecord[] => [
  {
    sourceId: 'sample-asha-duplicate',
    name: 'Asha Mehra',
    phone: '+91 98765 43210',
    birthday: new Date(new Date().getFullYear(), 7, 4).toISOString(),
    relationship: 'Sister'
  },
  {
    sourceId: 'sample-dev',
    name: 'Dev Kapoor',
    phone: '+91 90000 12345',
    email: 'dev@example.com',
    birthday: new Date(new Date().getFullYear(), 10, 14).toISOString(),
    relationship: 'Friend'
  }
];
