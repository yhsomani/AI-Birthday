import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import { buildCalendarExportEntries, calendarCandidatesToEvents } from './calendarSync';

describe('calendar sync rules', () => {
  it('builds calendar export entries that route users back to review', () => {
    const state = createInitialState();
    const entries = buildCalendarExportEntries(state);

    assert.equal(entries.length, state.events.length);
    assert.ok(entries.every(entry => entry.notes.includes('Review in RelateAI')));
    assert.ok(entries.every(entry => new Date(entry.endDate).getTime() > new Date(entry.startDate).getTime()));
  });

  it('imports calendar candidates as unverified review-needed events', () => {
    const state = createInitialState();
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

  it('skips invalid calendar candidates and duplicate same-day events', () => {
    const state = createInitialState();
    const existing = state.events[0];
    const contact = state.contacts.find(item => item.id === existing.contactId);
    const result = calendarCandidatesToEvents(state, [
      {
        sourceId: 'blank-title',
        title: '   ',
        startDate: existing.date
      },
      {
        sourceId: 'duplicate',
        title: `${contact?.name ?? 'Contact'} ${existing.type}`,
        startDate: existing.date
      }
    ]);

    assert.equal(result.addedEvents, 0);
    assert.equal(result.skipped, 2);
  });
});
