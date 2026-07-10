import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  importDeviceContactsWithApi,
  type ContactsImportApi
} from './contactImporter';

type ContactPage = Awaited<ReturnType<ContactsImportApi['Contact']['getAllDetails']>>;
type ContactDetails = ContactPage[number];

const contactDetails = (id: string): ContactDetails => ({
  id,
  fullName: `Contact ${id}`,
  givenName: 'Contact',
  familyName: id,
  phones: [{ id: `phone-${id}`, number: `+91 90000 ${id.padStart(5, '0')}` }],
  emails: [{ id: `email-${id}`, address: `${id}@example.com` }],
  birthday: null,
  dates: []
});

const contactsApi = (
  pages: Map<number, ContactPage>,
  status = 'granted'
) => {
  const offsets: number[] = [];
  const requestedFields: string[][] = [];
  const limits: number[] = [];
  const api: ContactsImportApi & { getContactsAsync(): never } = {
    requestPermissionsAsync: async () => ({ status }),
    Contact: {
      getAllDetails: async (fields, options) => {
        offsets.push(options.offset);
        requestedFields.push([...fields]);
        limits.push(options.limit);
        return pages.get(options.offset) ?? [];
      }
    },
    getContactsAsync: () => {
      throw new Error('Expo 57 root legacy shim must never be called.');
    }
  };
  return { api, limits, offsets, requestedFields };
};

describe('Expo 57 contacts adapter', () => {
  it('uses the Contact class API and reads every page without calling the throwing root shim', async () => {
    const firstPage = Array.from({ length: 250 }, (_, index) => contactDetails(String(index)));
    const directBirthday: ContactDetails = {
      ...contactDetails('250'),
      fullName: null,
      givenName: 'Asha',
      familyName: 'Mehra',
      birthday: { year: 1992, month: 8, day: 4 },
      phones: [
        { id: 'empty-phone', number: '   ' },
        { id: 'usable-phone', number: '+91 98765 43210' }
      ]
    };
    const androidBirthday: ContactDetails = {
      ...contactDetails('251'),
      birthday: null,
      dates: [
        { id: 'anniversary', label: 'anniversary', date: { year: 2010, month: 2, day: 2 } },
        { id: 'birthday', label: 'Birthday', date: { month: 11, day: 14 } }
      ],
      emails: [
        { id: 'empty-email', address: '' },
        { id: 'usable-email', address: 'dev@example.com' }
      ]
    };
    const { api, limits, offsets, requestedFields } = contactsApi(
      new Map([
        [0, firstPage],
        [250, [directBirthday, androidBirthday]]
      ])
    );

    const imported = await importDeviceContactsWithApi(api);

    assert.equal(imported.length, 252);
    assert.deepEqual(offsets, [0, 250]);
    assert.deepEqual(limits, [250, 250]);
    assert.ok(requestedFields.every(fields => fields.includes('dates')));
    assert.ok(requestedFields.every(fields => fields.includes('birthday')));
    assert.deepEqual(imported[250], {
      sourceId: '250',
      name: 'Asha Mehra',
      phone: '+91 98765 43210',
      email: '250@example.com',
      birthday: new Date(1992, 7, 4).toISOString(),
      relationship: undefined
    });
    assert.equal(imported[251].email, 'dev@example.com');
    assert.equal(
      imported[251].birthday,
      new Date(new Date().getFullYear(), 10, 14).toISOString()
    );
  });

  it('stops safely if a native page repeats instead of silently looping forever', async () => {
    const repeatedPage = Array.from({ length: 250 }, (_, index) => contactDetails(String(index)));
    const { api } = contactsApi(
      new Map([
        [0, repeatedPage],
        [250, repeatedPage]
      ])
    );

    await assert.rejects(
      importDeviceContactsWithApi(api),
      /Contacts pagination did not advance/
    );
  });

  it('does not query contacts when permission is denied', async () => {
    const { api, offsets } = contactsApi(new Map(), 'denied');

    await assert.rejects(
      importDeviceContactsWithApi(api),
      /Contacts permission was not granted/
    );
    assert.deepEqual(offsets, []);
  });
});
