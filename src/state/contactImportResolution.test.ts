import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { relateReducer } from './relateReducer';

describe('contact import resolution reducer boundary', () => {
  it('applies resolved and nonambiguous records while retaining unresolved records for review', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'importContacts',
      records: [
        {
          sourceId: 'reducer-keep-separate',
          name: 'Asha Mehra',
          email: 'separate-asha@example.com'
        },
        {
          sourceId: 'reducer-unresolved',
          name: 'Rajesh Nair',
          phone: '+919700002222'
        },
        {
          sourceId: 'reducer-clear',
          name: 'Reducer Clear Person',
          phone: '+919700003333'
        }
      ],
      resolutions: {
        'reducer-keep-separate': { action: 'keep-separate' }
      }
    });

    assert.equal(next.contacts.length, state.contacts.length + 2);
    assert.ok(
      next.contacts.some(contact =>
        contact.sourceIdentities?.some(identity => identity.sourceId === 'reducer-keep-separate')
      )
    );
    assert.ok(
      next.contacts.some(contact => contact.sourceIdentities?.some(identity => identity.sourceId === 'reducer-clear'))
    );
    assert.equal(
      next.contacts.some(contact =>
        contact.sourceIdentities?.some(identity => identity.sourceId === 'reducer-unresolved')
      ),
      false
    );
    assert.match(next.activity[0].detail, /2 added, 0 updated, 0 skipped, 1 need review/i);
  });

  it('does not accept a candidate choice for a multiple-route ambiguity', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'importContacts',
      records: [
        {
          sourceId: 'reducer-multiple-route',
          name: 'Reducer Multi Route',
          phones: ['+91 98765 43210'],
          emails: ['rajesh@example.com']
        }
      ],
      resolutions: {
        'reducer-multiple-route': { action: 'merge', candidateContactId: 'c-asha' }
      }
    });

    assert.equal(next.contacts.length, state.contacts.length);
    assert.match(next.activity[0].detail, /0 added, 0 updated, 0 skipped, 1 need review/i);
  });
});
