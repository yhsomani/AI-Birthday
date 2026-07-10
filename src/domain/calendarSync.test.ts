import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildCalendarExportEntries, buildCalendarExportPlan, calendarCandidatesToEvents } from './calendarSync';

describe('calendar sync rules', () => {
  it('builds calendar export entries that route users back to review', () => {
    const state = createTestState();
    const entries = buildCalendarExportEntries(state);

    assert.equal(entries.length, state.events.length);
    assert.ok(entries.every(entry => entry.notes.includes('Review in RelateAI')));
    assert.ok(entries.every(entry => entry.notes.includes(`RelateAI export: ${entry.id}`)));
    assert.ok(entries.every(entry => new Date(entry.endDate).getTime() > new Date(entry.startDate).getTime()));
    assert.ok(entries.every(entry => entry.allDay));
    assert.ok(entries.find(entry => entry.eventId === 'e-asha-bday')?.recurrenceRule);
  });

  it('plans first calendar export as creates and repeated export as unchanged', () => {
    const entries = buildCalendarExportEntries(createTestState());
    const firstPlan = buildCalendarExportPlan(entries, []);
    const repeatedPlan = buildCalendarExportPlan(
      entries,
      entries.map(entry => ({
        id: `device-${entry.id}`,
        title: entry.title,
        startDate: entry.startDate,
        endDate: entry.endDate,
        notes: entry.notes,
        allDay: entry.allDay,
        recurrenceRule: entry.recurrenceRule
      }))
    );

    assert.deepEqual(firstPlan.toCreate, entries);
    assert.equal(firstPlan.toUpdate.length, 0);
    assert.equal(firstPlan.unchangedCount, 0);
    assert.equal(repeatedPlan.toCreate.length, 0);
    assert.equal(repeatedPlan.toUpdate.length, 0);
    assert.equal(repeatedPlan.unchangedCount, entries.length);
  });

  it('updates changed mirrored events instead of creating duplicates', () => {
    const [entry] = buildCalendarExportEntries(createTestState());
    const plan = buildCalendarExportPlan([entry], [
      {
        id: 'device-existing',
        title: 'Old relationship event title',
        startDate: entry.startDate,
        endDate: entry.endDate,
        notes: entry.notes
      }
    ]);

    assert.equal(plan.toCreate.length, 0);
    assert.equal(plan.toUpdate.length, 1);
    assert.equal(plan.toUpdate[0].deviceEventId, 'device-existing');
    assert.equal(plan.toUpdate[0].entry.title, entry.title);
  });

  it('removes stale and duplicate RelateAI exports without deleting unrelated calendar events', () => {
    const [entry] = buildCalendarExportEntries(createTestState());
    const plan = buildCalendarExportPlan([entry], [
      {
        id: 'device-current',
        title: entry.title,
        startDate: entry.startDate,
        endDate: entry.endDate,
        notes: entry.notes,
        allDay: entry.allDay,
        recurrenceRule: entry.recurrenceRule
      },
      {
        id: 'device-duplicate-legacy',
        title: entry.title,
        startDate: entry.startDate,
        endDate: entry.endDate,
        notes: 'RelateAI reminder. Review in RelateAI before sending any message.'
      },
      {
        id: 'device-stale',
        title: 'Birthday: Deleted Contact',
        startDate: entry.startDate,
        endDate: entry.endDate,
        notes: 'RelateAI reminder.\nRelateAI export: calendar-export-deleted-event'
      },
      {
        id: 'device-unrelated',
        title: entry.title,
        startDate: entry.startDate,
        endDate: entry.endDate,
        notes: 'Personal calendar event'
      }
    ]);

    assert.equal(plan.toCreate.length, 0);
    assert.equal(plan.toUpdate.length, 0);
    assert.equal(plan.unchangedCount, 1);
    assert.deepEqual(
      plan.staleDeviceEventIds.sort(),
      ['device-duplicate-legacy', 'device-stale']
    );
  });

  it('imports calendar candidates as unverified review-needed events', () => {
    const state = createTestState();
    const result = calendarCandidatesToEvents(state, [
      {
        sourceId: 'calendar-dev-birthday',
        title: 'Dev Kapoor Birthday',
        startDate: new Date(new Date().getFullYear(), 10, 14).toISOString()
      }
    ]);

    const contact = result.contacts.find(item => item.name === 'Dev Kapoor');
    const event = result.events.find(item => item.contactId === contact?.id);

    assert.equal(result.addedContacts, 1);
    assert.equal(result.addedEvents, 1);
    assert.equal(event?.verified, false);
    assert.equal(event?.type, 'Birthday');
  });

  it('does not merge a calendar identity into an unrelated same-name contact', () => {
    const state = createTestState();
    const result = calendarCandidatesToEvents(state, [
      {
        sourceId: 'other-asha-event',
        title: 'Birthday: Asha Mehra',
        startDate: '2026-07-09T12:00:00.000Z'
      }
    ]);

    assert.equal(result.addedContacts, 1);
    assert.equal(result.contacts.filter(contact => contact.name === 'Asha Mehra').length, 2);
    assert.equal(result.events[0].contactId, 'calendar-other-asha-event');
  });

  it('classifies unknown imports as custom instead of silently calling them birthdays', () => {
    const result = calendarCandidatesToEvents(createTestState(), [
      {
        sourceId: 'custom-event',
        title: 'Community celebration: Alex',
        startDate: '2026-09-12T12:00:00.000Z'
      }
    ]);

    assert.equal(result.events[0].type, 'Custom');
    assert.equal(result.events[0].recurrence, undefined);
  });

  it('skips invalid calendar candidates and repeated source events', () => {
    const state = createTestState();
    const candidate = {
      sourceId: 'duplicate',
      title: 'Birthday: Repeated Contact',
      startDate: '2026-11-14T12:00:00.000Z'
    };
    const first = calendarCandidatesToEvents(state, [candidate]);
    const result = calendarCandidatesToEvents(
      {
        ...state,
        contacts: first.contacts,
        events: first.events
      },
      [
      {
        sourceId: 'blank-title',
        title: '   ',
        startDate: candidate.startDate
      },
        candidate
      ]
    );

    assert.equal(result.addedEvents, 0);
    assert.equal(result.skipped, 2);
  });
});
