import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import { importContacts } from './contactImport';

describe('contact import rules', () => {
  it('deduplicates imported contacts by phone and adds missing birthday events', () => {
    const state = createInitialState();
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
    const state = createInitialState();
    const result = importContacts(state, [
      {
        sourceId: 'new-person',
        name: 'Dev Kapoor',
        email: 'dev@example.com',
        birthday: new Date(new Date().getFullYear(), 10, 14).toISOString()
      }
    ]);

    const contact = result.contacts.find(item => item.name === 'Dev Kapoor');
    const event = result.events.find(item => item.contactId === contact?.id);

    assert.equal(result.added, 1);
    assert.equal(contact?.preferredChannel, 'Email');
    assert.equal(event?.type, 'Birthday');
    assert.equal(event?.verified, false);
  });

  it('skips blank imported contacts', () => {
    const state = createInitialState();
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
