import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { applyContactMerge } from './contactLifecycle';
import { importContacts } from './contactImport';

describe('contact import rules', () => {
  it('deduplicates imported contacts by phone when the birthday recurrence agrees', () => {
    const state = createTestState();
    const existingBirthday = state.events.find(event => event.id === 'e-asha-bday')?.date;
    const result = importContacts(state, [
      {
        sourceId: 'dupe-asha',
        name: 'Asha Mehra',
        phone: '+91 98765 43210',
        birthday: existingBirthday
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

  it('requires review instead of merging or creating a same-name identity automatically', () => {
    const state = createTestState();
    const existingCount = state.contacts.length;
    const result = importContacts(state, [
      {
        sourceId: 'different-asha',
        name: 'Asha Mehra',
        email: 'another-asha@example.com'
      }
    ]);

    assert.equal(result.added, 0);
    assert.equal(result.updated, 0);
    assert.equal(result.contacts.length, existingCount);
    assert.equal(result.skipped, 0);
    assert.equal(result.unresolved, 1);
    assert.equal(result.reviewItems.length, 1);
    assert.equal(result.reviewItems[0].reason, 'same-name');
    assert.ok(result.reviewItems[0].candidateContactIds.includes('c-asha'));
  });

  it('applies nonambiguous imports while leaving unresolved same-name records staged', () => {
    const state = createTestState();
    const result = importContacts(state, [
      {
        sourceId: 'new-clear-record',
        name: 'Clear New Person',
        phone: '+919700001111'
      },
      {
        sourceId: 'same-name-staged',
        name: 'Asha Mehra',
        email: 'different-asha@example.com'
      }
    ]);

    assert.equal(result.added, 1);
    assert.equal(result.updated, 0);
    assert.equal(result.skipped, 0);
    assert.equal(result.unresolved, 1);
    assert.ok(result.contacts.some(contact => contact.name === 'Clear New Person'));
    assert.equal(
      result.contacts.some(contact =>
        contact.sourceIdentities?.some(identity => identity.sourceId === 'same-name-staged')
      ),
      false
    );
  });

  it('resolves same-name reviews only through merge, keep-separate, or skip', () => {
    const state = createTestState();
    const record = {
      sourceId: 'same-name-resolution',
      name: 'Asha Mehra',
      email: 'resolved-asha@example.com',
      birthday: state.events.find(event => event.id === 'e-asha-bday')?.date
    };
    const merged = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'merge', candidateContactId: 'c-asha' }
    });
    const separate = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'keep-separate' }
    });
    const skipped = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'skip' }
    });
    const stale = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'merge', candidateContactId: 'not-a-listed-candidate' }
    });

    assert.equal(merged.updated, 1);
    assert.equal(merged.added, 0);
    assert.ok(
      merged.contacts
        .find(contact => contact.id === 'c-asha')
        ?.sourceIdentities?.some(identity => identity.sourceId === record.sourceId)
    );
    assert.equal(separate.added, 1);
    assert.equal(separate.contacts.filter(contact => contact.name === 'Asha Mehra').length, 2);
    assert.equal(skipped.skipped, 1);
    assert.equal(skipped.unresolved, 0);
    assert.equal(stale.added, 0);
    assert.equal(stale.updated, 0);
    assert.equal(stale.unresolved, 1);
    assert.equal(stale.reviewItems[0].resolutionIssue, 'candidate-no-longer-listed');
  });

  it('never resolves multiple exact route matches to one candidate until the contacts are merged', () => {
    const state = createTestState();
    const record = {
      sourceId: 'multi-exact-resolution',
      name: 'Imported Multi Match',
      phones: ['+91 98765 43210'],
      emails: ['rajesh@example.com']
    };
    const unresolved = importContacts(state, [record]);
    const forbiddenMerge = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'merge', candidateContactId: 'c-asha' }
    });
    const forbiddenSeparate = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'keep-separate' }
    });
    const skipped = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'skip' }
    });

    assert.equal(unresolved.unresolved, 1);
    assert.deepEqual(new Set(unresolved.reviewItems[0].candidateContactIds), new Set(['c-asha', 'c-rajesh']));
    assert.equal(forbiddenMerge.updated, 0);
    assert.equal(forbiddenMerge.added, 0);
    assert.equal(forbiddenMerge.reviewItems[0].resolutionIssue, 'only-skip-allowed');
    assert.equal(forbiddenSeparate.added, 0);
    assert.equal(forbiddenSeparate.reviewItems[0].resolutionIssue, 'only-skip-allowed');
    assert.equal(skipped.skipped, 1);
    assert.equal(skipped.unresolved, 0);

    const consolidated = applyContactMerge(state, 'c-asha', 'c-rajesh');
    const afterContactMerge = importContacts(consolidated, [record]);
    assert.equal(afterContactMerge.updated, 1);
    assert.equal(afterContactMerge.unresolved, 0);
  });

  it('retains every normalized route and stable source identity across repeated imports', () => {
    const state = createTestState();
    const first = importContacts(state, [
      {
        sourceId: 'device-multi-route',
        name: 'Multiple Routes',
        phones: ['+91 90000 11111', '+91 90000 22222'],
        emails: ['one@example.com', 'two@example.com']
      }
    ]);
    const second = importContacts({ ...state, contacts: first.contacts, events: first.events }, [
      {
        sourceId: 'device-multi-route',
        name: 'Renamed on Device',
        phones: ['+91 90000 33333']
      }
    ]);
    const contact = second.contacts.find(item =>
      item.sourceIdentities?.some(identity => identity.sourceId === 'device-multi-route')
    );
    assert.equal(first.added, 1);
    assert.equal(second.updated, 1);
    assert.equal(contact?.routes?.filter(route => route.type === 'Phone').length, 3);
    assert.equal(contact?.routes?.filter(route => route.type === 'Email').length, 2);
  });

  it('stages an exact-identity birthday conflict without mutating the contact or saved event', () => {
    const state = createTestState();
    state.events = state.events.map(event =>
      event.id === 'e-asha-bday' ? { ...event, date: '1990-07-09T12:00:00.000Z', recurrence: undefined } : event
    );
    const record = {
      sourceId: 'device-asha-conflicting-birthday',
      name: 'Asha Mehra',
      phone: '+91 98765 43210',
      birthday: '1991-08-04T09:30:00.000Z'
    };
    const result = importContacts(state, [record]);

    assert.equal(result.updated, 0);
    assert.equal(result.added, 0);
    assert.equal(result.unresolved, 1);
    assert.equal(result.reviewItems[0].reason, 'conflicting-birthday');
    assert.deepEqual(result.reviewItems[0].conflictingEventIds, ['e-asha-bday']);
    assert.equal(result.reviewItems[0].importedBirthday, '1991-08-04T12:00:00.000Z');
    assert.equal(result.events.find(event => event.id === 'e-asha-bday')?.date, '1990-07-09T12:00:00.000Z');
    assert.equal(
      result.contacts
        .find(contact => contact.id === 'c-asha')
        ?.sourceIdentities?.some(identity => identity.sourceId === record.sourceId) ?? false,
      false
    );
  });

  it('requires explicit keep-existing, replace, or import-as-separate birthday conflict semantics', () => {
    const state = createTestState();
    state.events = state.events.map(event =>
      event.id === 'e-asha-bday' ? { ...event, date: '1990-07-09T12:00:00.000Z', recurrence: undefined } : event
    );
    const record = {
      sourceId: 'device-asha-birthday-resolution',
      name: 'Asha Mehra',
      phone: '+91 98765 43210',
      birthday: '1991-08-04T09:30:00.000Z'
    };
    const kept = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'keep-existing' }
    });
    const replaced = importContacts(state, [record], undefined, {
      [record.sourceId]: { action: 'replace', conflictingEventId: 'e-asha-bday' }
    });
    const separate = importContacts(
      state,
      [record],
      { contactIds: ['unused-contact'], eventIds: ['new-birthday'] },
      {
        [record.sourceId]: { action: 'import-as-separate' }
      }
    );

    assert.equal(kept.unresolved, 0);
    assert.equal(kept.events.find(event => event.id === 'e-asha-bday')?.date, '1990-07-09T12:00:00.000Z');
    assert.equal(replaced.unresolved, 0);
    assert.equal(replaced.events.find(event => event.id === 'e-asha-bday')?.date, '1991-08-04T12:00:00.000Z');
    assert.equal(replaced.events.find(event => event.id === 'e-asha-bday')?.verified, false);
    assert.ok(
      replaced.events
        .find(event => event.id === 'e-asha-bday')
        ?.sourceIdentities?.some(identity => identity.sourceId === record.sourceId)
    );
    assert.equal(separate.unresolved, 0);
    assert.equal(separate.events.length, state.events.length + 1);
    assert.equal(separate.events.find(event => event.id === 'new-birthday')?.date, '1991-08-04T12:00:00.000Z');
  });

  it('treats the same birthday recurrence across different years as the same imported occasion', () => {
    const state = createTestState();
    state.events = state.events.map(event =>
      event.id === 'e-asha-bday' ? { ...event, date: '1990-07-09T12:00:00.000Z', recurrence: undefined } : event
    );
    const result = importContacts(state, [
      {
        sourceId: 'device-asha-same-recurring-birthday',
        name: 'Asha Mehra',
        phone: '+91 98765 43210',
        birthday: '2027-07-09T00:00:00.000Z'
      }
    ]);

    assert.equal(result.updated, 1);
    assert.equal(result.unresolved, 0);
    assert.equal(result.events.length, state.events.length);
    assert.ok(
      result.events
        .find(event => event.id === 'e-asha-bday')
        ?.sourceIdentities?.some(identity => identity.sourceId === 'device-asha-same-recurring-birthday')
    );
  });

  it('stages malformed imported birthdays instead of creating an invalid event', () => {
    const state = createTestState();
    const result = importContacts(state, [
      {
        sourceId: 'invalid-imported-birthday',
        name: 'Invalid Birthday Person',
        email: 'invalid-birthday@example.com',
        birthday: '2027-02-30'
      }
    ]);

    assert.equal(result.added, 0);
    assert.equal(result.updated, 0);
    assert.equal(result.unresolved, 1);
    assert.equal(result.reviewItems[0].reason, 'invalid-birthday');
    assert.equal(
      result.contacts.some(contact => contact.name === 'Invalid Birthday Person'),
      false
    );
  });

  it('retains a nameless routable contact as an explicit review item', () => {
    const state = createTestState();
    const result = importContacts(state, [
      {
        sourceId: 'nameless-routable',
        name: '   ',
        phone: '+91 90000 12345'
      }
    ]);

    assert.equal(result.added, 0);
    assert.equal(result.skipped, 0);
    assert.equal(result.unresolved, 1);
    assert.equal(result.reviewItems[0].reason, 'missing-name');
    assert.deepEqual(result.reviewItems[0].candidateContactIds, []);
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
