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

    assert.deepEqual(rows.map(row => row.contact.id), ['c-mira']);
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
});
