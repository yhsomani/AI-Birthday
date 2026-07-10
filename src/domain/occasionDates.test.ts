import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { RelationshipEvent } from './types';
import {
  eventOccurrenceIso,
  eventOccurrenceInYear,
  recurrenceForEvent,
  yearlyOccurrenceIso
} from './occasionDates';

const birthday = (date = '1990-07-09T12:00:00.000Z'): RelationshipEvent => ({
  id: 'birthday',
  contactId: 'contact',
  type: 'Birthday',
  label: 'Birthday',
  date,
  verified: true,
  source: 'Manual',
  checklist: []
});

describe('recurring occasion dates', () => {
  it('infers backward-compatible yearly recurrence and rolls into the next year', () => {
    const event = birthday();
    assert.deepEqual(recurrenceForEvent(event), {
      frequency: 'Yearly',
      month: 7,
      day: 9,
      originalYear: 1990,
      leapDayPolicy: 'February 28'
    });
    assert.equal(eventOccurrenceIso(event, new Date('2026-07-08T23:59:00.000Z')), '2026-07-09T12:00:00.000Z');
    assert.equal(eventOccurrenceIso(event, new Date('2026-07-10T00:00:00.000Z')), '2027-07-09T12:00:00.000Z');
  });

  it('keeps non-recurring custom events as their original occurrence', () => {
    const event = { ...birthday(), type: 'Custom' as const };
    assert.equal(recurrenceForEvent(event), undefined);
    assert.equal(eventOccurrenceIso(event, new Date('2099-01-01T00:00:00.000Z')), event.date);
  });

  it('applies an explicit leap-day policy across century boundaries', () => {
    const recurrence = {
      frequency: 'Yearly' as const,
      month: 2,
      day: 29,
      originalYear: 2000,
      leapDayPolicy: 'February 28' as const
    };
    assert.equal(yearlyOccurrenceIso(recurrence, 2028), '2028-02-29T12:00:00.000Z');
    assert.equal(yearlyOccurrenceIso(recurrence, 2100), '2100-02-28T12:00:00.000Z');
    assert.equal(eventOccurrenceInYear({ ...birthday('2000-02-29T12:00:00.000Z'), recurrence }, 2100)?.date, '2100-02-28T12:00:00.000Z');
  });

  it('produces a valid occurrence for every year in a twenty-year horizon', () => {
    const event = birthday('1984-12-31T12:00:00.000Z');
    for (let year = 2026; year < 2046; year += 1) {
      const occurrence = eventOccurrenceInYear(event, year);
      assert.equal(occurrence?.date, `${year}-12-31T12:00:00.000Z`);
    }
  });
});
