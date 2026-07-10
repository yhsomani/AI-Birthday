import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import {
  buildCalendarExportEntries,
  buildCalendarExportPlan,
  calendarCandidatesToEvents,
  resolveCalendarExportSelection
} from './calendarSync';

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

  it('excludes events belonging to archived contacts from desired calendar exports', () => {
    const state = createTestState();
    const archivedId = state.contacts[0].id;
    state.contacts[0] = { ...state.contacts[0], archivedAt: '2026-07-10T00:00:00.000Z' };
    const entries = buildCalendarExportEntries(state);
    assert.equal(
      entries.some(entry => state.events.find(event => event.id === entry.eventId)?.contactId === archivedId),
      false
    );
  });

  it('resolves only unique existing exportable event selections while omission means full reconciliation', () => {
    const state = createTestState();
    const entries = buildCalendarExportEntries(state);
    const eventIds = entries.slice(0, 2).map(entry => entry.eventId);

    const full = resolveCalendarExportSelection(state, undefined);
    const selected = resolveCalendarExportSelection(state, [...eventIds].reverse());

    assert.equal(full.ok && full.mode, 'full');
    assert.equal(full.ok && full.eligibleCount, entries.length);
    assert.equal(full.ok && full.selectedCount, 0);
    assert.equal(selected.ok && selected.mode, 'selected');
    assert.deepEqual(selected.ok ? selected.entries.map(entry => entry.eventId) : [], [...eventIds].reverse());
    assert.deepEqual(resolveCalendarExportSelection(state, []), { ok: false, reason: 'empty-selection' });
    assert.deepEqual(resolveCalendarExportSelection(state, [eventIds[0], eventIds[0]]), {
      ok: false,
      reason: 'duplicate-selection'
    });
    assert.deepEqual(resolveCalendarExportSelection(state, ['missing-event']), {
      ok: false,
      reason: 'event-not-exportable'
    });

    const archived = structuredClone(state);
    const selectedEvent = archived.events.find(event => event.id === eventIds[0]);
    const selectedContact = archived.contacts.find(contact => contact.id === selectedEvent?.contactId);
    assert.ok(selectedContact);
    selectedContact.archivedAt = '2026-07-10T00:00:00.000Z';
    assert.deepEqual(resolveCalendarExportSelection(archived, [eventIds[0]]), {
      ok: false,
      reason: 'event-not-exportable'
    });
  });

  it('updates changed mirrored events instead of creating duplicates', () => {
    const [entry] = buildCalendarExportEntries(createTestState());
    const plan = buildCalendarExportPlan(
      [entry],
      [
        {
          id: 'device-existing',
          title: 'Old relationship event title',
          startDate: entry.startDate,
          endDate: entry.endDate,
          notes: entry.notes
        }
      ]
    );

    assert.equal(plan.toCreate.length, 0);
    assert.equal(plan.toUpdate.length, 1);
    assert.equal(plan.toUpdate[0].deviceEventId, 'device-existing');
    assert.equal(plan.toUpdate[0].entry.title, entry.title);
  });

  it('removes stale and duplicate RelateAI exports without deleting unrelated calendar events', () => {
    const [entry] = buildCalendarExportEntries(createTestState());
    const plan = buildCalendarExportPlan(
      [entry],
      [
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
      ]
    );

    assert.equal(plan.toCreate.length, 0);
    assert.equal(plan.toUpdate.length, 0);
    assert.equal(plan.unchangedCount, 1);
    assert.deepEqual(plan.staleDeviceEventIds.sort(), ['device-duplicate-legacy', 'device-stale']);
  });

  it('deduplicates selected entries without removing unselected or unrelated device events', () => {
    const [selected, unselected] = buildCalendarExportEntries(createTestState());
    const plan = buildCalendarExportPlan(
      [selected],
      [
        {
          id: 'device-selected',
          title: selected.title,
          startDate: selected.startDate,
          endDate: selected.endDate,
          notes: selected.notes,
          allDay: selected.allDay,
          recurrenceRule: selected.recurrenceRule
        },
        {
          id: 'device-selected-duplicate',
          title: selected.title,
          startDate: selected.startDate,
          endDate: selected.endDate,
          notes: 'RelateAI reminder. Review in RelateAI before sending any message.'
        },
        {
          id: 'device-unselected',
          title: unselected.title,
          startDate: unselected.startDate,
          endDate: unselected.endDate,
          notes: unselected.notes
        },
        {
          id: 'device-stale-unselected',
          title: 'Old RelateAI reminder',
          startDate: selected.startDate,
          notes: 'RelateAI reminder.\nRelateAI export: calendar-export-old-unselected'
        },
        {
          id: 'device-unrelated',
          title: selected.title,
          startDate: selected.startDate,
          notes: 'Personal calendar event'
        }
      ],
      { mode: 'selected' }
    );

    assert.equal(plan.mode, 'selected');
    assert.equal(plan.unchangedCount, 1);
    assert.deepEqual(plan.staleDeviceEventIds, ['device-selected-duplicate']);
    assert.equal(plan.staleDeviceEventIds.includes('device-unselected'), false);
    assert.equal(plan.staleDeviceEventIds.includes('device-stale-unselected'), false);
    assert.equal(plan.staleDeviceEventIds.includes('device-unrelated'), false);
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
    assert.deepEqual(contact?.sourceIdentities, [{ provider: 'Calendar', sourceId: 'calendar-dev-birthday' }]);
    assert.deepEqual(event?.sourceIdentities, [{ provider: 'Calendar', sourceId: 'calendar-dev-birthday' }]);
  });

  it('stages an unrelated same-name calendar identity instead of merging it automatically', () => {
    const state = createTestState();
    const result = calendarCandidatesToEvents(state, [
      {
        sourceId: 'other-asha-event',
        title: 'Birthday: Asha Mehra',
        startDate: '2026-07-09T12:00:00.000Z'
      }
    ]);

    assert.equal(result.addedContacts, 0);
    assert.equal(result.addedEvents, 0);
    assert.equal(result.unresolved, 1);
    assert.equal(result.contacts.filter(contact => contact.name === 'Asha Mehra').length, 1);
    assert.equal(result.reviewItems[0].sourceId, 'other-asha-event');
    assert.ok(['same-name', 'conflicting-date'].includes(result.reviewItems[0].reason));
    assert.deepEqual(result.reviewItems[0].candidateContactIds, ['c-asha']);
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

  it('uses persisted calendar source identity when fresh reducer id pools are supplied on every import', () => {
    const candidate = {
      sourceId: 'stable-device-event-42',
      title: 'Fresh Pool Person Birthday',
      startDate: '1991-11-14T12:00:00.000Z'
    };
    const state = createTestState();
    const first = calendarCandidatesToEvents(state, [candidate], {
      contactIds: ['contact-first-pool'],
      eventIds: ['event-first-pool']
    });
    const second = calendarCandidatesToEvents(
      { ...state, contacts: first.contacts, events: first.events },
      [{ ...candidate, startDate: '2027-11-14T12:00:00.000Z' }],
      { contactIds: ['contact-second-pool'], eventIds: ['event-second-pool'] }
    );

    assert.equal(first.addedContacts, 1);
    assert.equal(first.addedEvents, 1);
    assert.equal(second.addedContacts, 0);
    assert.equal(second.addedEvents, 0);
    assert.equal(second.skipped, 1);
    assert.equal(second.unresolved, 0);
    assert.equal(
      second.contacts.some(contact => contact.id === 'contact-second-pool'),
      false
    );
    assert.equal(
      second.events.some(event => event.id === 'event-second-pool'),
      false
    );
    assert.equal(second.events.filter(event => event.id === 'event-first-pool').length, 1);
  });

  it('stages a changed date for an existing calendar source identity', () => {
    const candidate = {
      sourceId: 'calendar-source-date-conflict',
      title: 'Calendar Conflict Person Anniversary',
      startDate: '2019-04-08T12:00:00.000Z'
    };
    const state = createTestState();
    const first = calendarCandidatesToEvents(state, [candidate]);
    const second = calendarCandidatesToEvents({ ...state, contacts: first.contacts, events: first.events }, [
      { ...candidate, startDate: '2027-04-09T12:00:00.000Z' }
    ]);

    assert.equal(second.addedEvents, 0);
    assert.equal(second.unresolved, 1);
    assert.equal(second.reviewItems[0].reason, 'source-content-conflict');
    assert.deepEqual(second.reviewItems[0].conflictingEventIds, [first.events[0].id]);
  });
});
