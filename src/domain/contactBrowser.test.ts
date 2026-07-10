import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildContactBrowserRows } from './contactBrowser';

describe('contact browser contract', () => {
  it('searches contact identity and notes while applying group filters', () => {
    const state = createTestState();
    const rows = buildContactBrowserRows(
      state,
      {
        query: 'coffee',
        group: 'Close friends',
        quality: 'All',
        sort: 'Name'
      },
      new Date('2026-07-09T00:00:00.000Z')
    );

    assert.deepEqual(
      rows.map(row => row.contact.id),
      ['c-mira']
    );
  });

  it('labels and filters contacts with missing delivery details', () => {
    const state = createTestState();
    const rows = buildContactBrowserRows(
      {
        ...state,
        contacts: [
          {
            ...state.contacts[1],
            id: 'c-no-phone',
            phone: undefined,
            email: undefined,
            preferredChannel: 'SMS'
          }
        ],
        events: []
      },
      {
        query: '',
        group: 'All',
        quality: 'Missing channel',
        sort: 'Name'
      },
      new Date('2026-07-09T00:00:00.000Z')
    );

    assert.equal(rows.length, 1);
    assert.ok(rows[0].qualityLabels.includes('Missing channel'));
    assert.ok(rows[0].qualityLabels.includes('Missing event'));
  });

  it('sorts by relationship health priority', () => {
    const state = createTestState();
    const rows = buildContactBrowserRows(
      state,
      {
        query: '',
        group: 'All',
        quality: 'All',
        sort: 'Health priority'
      },
      new Date('2026-07-09T00:00:00.000Z')
    );

    assert.equal(rows[0].contact.id, 'c-mira');
    assert.ok(rows[0].contact.healthScore < rows[1].contact.healthScore);
  });

  it('searches every contact route and excludes archived contacts by default', () => {
    const state = createTestState();
    state.contacts[0] = {
      ...state.contacts[0],
      routes: [
        {
          id: 'route-phone',
          type: 'Phone',
          value: '+91 98765 43210',
          primary: false,
          verified: false
        },
        {
          id: 'route-email',
          type: 'Email',
          value: 'alternate@example.com',
          primary: false,
          verified: false
        }
      ]
    };
    state.contacts[1] = { ...state.contacts[1], archivedAt: '2026-07-10T00:00:00.000Z' };
    const byPhone = buildContactBrowserRows(state, {
      query: '9876543210',
      group: 'All',
      quality: 'All',
      sort: 'Name'
    });
    const all = buildContactBrowserRows(state, {
      query: '',
      group: 'All',
      quality: 'All',
      sort: 'Name'
    });
    assert.deepEqual(
      byPhone.map(row => row.contact.id),
      [state.contacts[0].id]
    );
    assert.equal(
      all.some(row => row.contact.id === state.contacts[1].id),
      false
    );
  });
});
