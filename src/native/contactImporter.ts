import type {
  ContactDate,
  ContactField,
  ExistingDate,
  ExistingEmail,
  ExistingPhone,
  PartialContactDetails
} from 'expo-contacts';
import type { ImportedContactRecord } from '../domain/types';
import { throwIfAborted } from './abort';

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

export interface DeviceContactImportOptions {
  fallbackBirthdayYear?: number;
  signal?: AbortSignal;
}

const INVALID_IMPORTED_BIRTHDAY = 'invalid-imported-birthday';

const buildBirthday = (birthday: ContactDate | null | undefined, fallbackBirthdayYear: number): string | undefined => {
  if (!birthday) return undefined;
  if (!birthday.day || !birthday.month) return INVALID_IMPORTED_BIRTHDAY;
  const year = birthday.year ?? fallbackBirthdayYear;
  if (!Number.isInteger(year) || year < 1 || year > 9999) {
    return INVALID_IMPORTED_BIRTHDAY;
  }
  const date = new Date(Date.UTC(year, birthday.month - 1, birthday.day, 12, 0, 0, 0));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== birthday.month - 1 ||
    date.getUTCDate() !== birthday.day
  ) {
    return INVALID_IMPORTED_BIRTHDAY;
  }
  return date.toISOString();
};

const birthdayFrom = (contact: ContactImportDetails) => {
  if (contact.birthday) {
    return contact.birthday;
  }

  return contact.dates?.find((date: ExistingDate) => date.label?.trim().toLowerCase() === 'birthday')?.date;
};

const allPhones = (phones: ExistingPhone[] | undefined) => [
  ...new Set(phones?.map(phone => phone.number?.trim()).filter((value): value is string => Boolean(value)) ?? [])
];

const allEmails = (emails: ExistingEmail[] | undefined) => [
  ...new Set(emails?.map(email => email.address?.trim()).filter((value): value is string => Boolean(value)) ?? [])
];

const toImportedRecord = (contact: ContactImportDetails, fallbackBirthdayYear: number): ImportedContactRecord => {
  const composedName = [contact.givenName, contact.familyName].filter(Boolean).join(' ').trim();
  const name = contact.fullName?.trim() || composedName;

  const phones = allPhones(contact.phones);
  const emails = allEmails(contact.emails);
  return {
    sourceId: contact.id || `${name || 'contact'}-device`,
    name,
    phone: phones[0],
    email: emails[0],
    phones,
    emails,
    birthday: buildBirthday(birthdayFrom(contact), fallbackBirthdayYear),
    relationship: undefined
  };
};

/**
 * Imports a complete, bounded page sequence through Expo 57's Contact class API.
 * Exported for executable adapter tests; production callers should use
 * `importDeviceContacts`, which supplies the installed native module.
 */
export const importDeviceContactsWithApi = async (
  Contacts: ContactsImportApi,
  options: DeviceContactImportOptions = {}
): Promise<ImportedContactRecord[]> => {
  throwIfAborted(options.signal);
  const permission = await Contacts.requestPermissionsAsync();
  throwIfAborted(options.signal);
  if (permission.status !== 'granted') {
    throw new Error('Contacts permission was not granted.');
  }

  const imported: ImportedContactRecord[] = [];
  const seenContactIds = new Set<string>();
  const fallbackBirthdayYear = options.fallbackBirthdayYear ?? new Date().getUTCFullYear();
  let offset = 0;

  while (true) {
    throwIfAborted(options.signal);
    const page = await Contacts.Contact.getAllDetails(CONTACT_IMPORT_FIELDS, {
      limit: CONTACT_IMPORT_PAGE_SIZE,
      offset
    });
    throwIfAborted(options.signal);

    if (page.length === 0) {
      break;
    }

    let newRecords = 0;
    for (const contact of page) {
      throwIfAborted(options.signal);
      if (seenContactIds.has(contact.id)) {
        continue;
      }
      seenContactIds.add(contact.id);
      imported.push(toImportedRecord(contact, fallbackBirthdayYear));
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

export const importDeviceContacts = async (
  options: DeviceContactImportOptions = {}
): Promise<ImportedContactRecord[]> => {
  const Contacts = await import('expo-contacts');
  return importDeviceContactsWithApi(Contacts, options);
};
