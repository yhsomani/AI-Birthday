import * as Contacts from 'expo-contacts';
import type { ImportedContactRecord } from '../domain/types';

const buildBirthday = (birthday: Contacts.Date | undefined): string | undefined => {
  if (!birthday?.day || !birthday.month) {
    return undefined;
  }
  const year = birthday.year ?? new Date().getFullYear();
  return new Date(year, birthday.month - 1, birthday.day).toISOString();
};

export const importDeviceContacts = async (): Promise<ImportedContactRecord[]> => {
  const permission = await Contacts.requestPermissionsAsync();
  if (permission.status !== 'granted') {
    throw new Error('Contacts permission was not granted.');
  }

  const result = await Contacts.getContactsAsync({
    fields: [
      Contacts.Fields.Name,
      Contacts.Fields.PhoneNumbers,
      Contacts.Fields.Emails,
      Contacts.Fields.Birthday
    ],
    pageSize: 500
  });

  return result.data.map(contact => ({
    sourceId: contact.id ?? `${contact.name ?? 'contact'}-device`,
    name: contact.name ?? [contact.firstName, contact.lastName].filter(Boolean).join(' '),
    phone: contact.phoneNumbers?.[0]?.number,
    email: contact.emails?.[0]?.email,
    birthday: buildBirthday(contact.birthday),
    relationship: undefined
  }));
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
