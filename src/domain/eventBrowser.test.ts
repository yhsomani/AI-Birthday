import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import { buildEventMonthView, filterRelationshipEvents, shiftMonth } from './eventBrowser';

describe('event browser contract', () => {
  it('filters events by type and upcoming time range', () => {
    const state = createInitialState();
    const birthdayDate = state.events.find(event => event.id === 'e-asha-bday')?.date ?? '';
    const filtered = filterRelationshipEvents(state.events, {
      type: 'Birthday',
      time: 'Upcoming',
      nowIso: birthdayDate,
      monthIso: birthdayDate
    });

    assert.deepEqual(
      filtered.map(event => event.id),
      ['e-asha-bday']
    );
  });

  it('sorts events by date and supports past event recovery views', () => {
    const state = createInitialState();
    const pastState = {
      ...state,
      events: [
        {
          ...state.events[0],
          id: 'e-past',
          label: 'Past birthday',
          date: '2026-01-01T12:00:00.000Z'
        },
        ...state.events
      ]
    };
    const filtered = filterRelationshipEvents(pastState.events, {
      type: 'All',
      time: 'Past',
      nowIso: '2026-02-01T12:00:00.000Z',
      monthIso: '2026-02-01T12:00:00.000Z'
    });

    assert.equal(filtered[0].id, 'e-past');
    assert.ok(filtered.every(event => event.date < '2026-02-01T12:00:00.000Z'));
  });

  it('builds a six-week month grid with events grouped by day', () => {
    const state = createInitialState();
    const event = {
      ...state.events[0],
      id: 'e-month',
      date: '2026-07-09T12:00:00.000Z',
      label: 'Month grid birthday'
    };
    const month = buildEventMonthView([event], '2026-07-01T12:00:00.000Z');
    const day = month.days.find(item => item.dateKey === '2026-07-09');

    assert.equal(month.monthKey, '2026-07');
    assert.equal(month.days.length, 42);
    assert.equal(day?.events[0].id, 'e-month');
    assert.equal(day?.inMonth, true);
  });

  it('moves month anchors without drifting across time zones', () => {
    assert.equal(shiftMonth('2026-07-31T23:00:00.000Z', 1), '2026-08-01T12:00:00.000Z');
    assert.equal(shiftMonth('2026-07-01T01:00:00.000Z', -1), '2026-06-01T12:00:00.000Z');
  });
});
