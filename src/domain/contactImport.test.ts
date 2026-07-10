import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { importContacts } from './contactImport';

describe('contact import rules', () => {
  it('deduplicates imported contacts by phone and adds missing birthday events', () => {
    const state = createTestState();
    const result = importContacts(state, [
      {
        sourceId: 'dupe-asha',
        name: 'Asha Mehra',
        phone: '+91 98765 43210',
        birthday: new Date(new Date().getFullYear(), 1, 1).toISOString()
      }
    ]);

    assert.equal(result.added, 0);
    assert.equal(result.updated, 1);
    assert.equal(result.contacts.length, state.contacts.length);
  });

  it('adds new contacts and creates review-needed birthday events', () => {
    const state = createTestState();
    const result = importContacts(state, [
      {
        sourceId: 'new-person',
        name: 'Dev Kapoor',
        email: 'dev@example.com',
        birthday: new Date(Date.UTC(new Date().getFullYear(), 10, 14, 12)).toISOString()
      }
    ]);

    const contact = result.contacts.find(item => item.name === 'Dev Kapoor');
    const event = result.events.find(item => item.contactId === contact?.id);

    assert.equal(result.added, 1);
    assert.equal(contact?.preferredChannel, 'Email');
    assert.equal(event?.type, 'Birthday');
    assert.equal(event?.verified, false);
    assert.deepEqual(event?.recurrence, {
      frequency: 'Yearly',
      month: 11,
      day: 14,
      originalYear: new Date().getFullYear(),
      leapDayPolicy: 'February 28'
    });
  });

  it('never merges different people from a name-only match', () => {
    const state = createTestState();
    const existingCount = state.contacts.length;
    const result = importContacts(state, [
      {
        sourceId: 'different-asha',
        name: 'Asha Mehra',
        email: 'another-asha@example.com'
      }
    ]);

    assert.equal(result.added, 1);
    assert.equal(result.updated, 0);
    assert.equal(result.contacts.length, existingCount + 1);
    assert.equal(result.contacts.filter(contact => contact.name === 'Asha Mehra').length, 2);
  });

  it('skips blank imported contacts', () => {
    const state = createTestState();
    const result = importContacts(state, [
      {
        sourceId: 'blank',
        name: '   '
      }
    ]);

    assert.equal(result.skipped, 1);
    assert.equal(result.contacts.length, state.contacts.length);
  });
});
