import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildCheckInReminderQueue } from './checkIns';

describe('relationship check-in reminders', () => {
  it('identifies overdue contacts without treating snoozed reminders as contacted', () => {
    const base = createTestState();
    const state = {
      ...base,
      contacts: base.contacts.map(contact =>
        contact.id === 'c-mira'
          ? {
              ...contact,
              lastContactedAt: '2026-04-01T09:00:00.000Z',
              checkInSnoozedUntil: '2026-07-12T09:00:00.000Z'
            }
          : contact.id === 'c-rajesh'
            ? {
                ...contact,
                lastContactedAt: '2026-04-15T09:00:00.000Z'
              }
            : contact
      )
    };
    const queue = buildCheckInReminderQueue(state, new Date('2026-07-09T09:00:00.000Z'));

    assert.ok(queue.due.some(reminder => reminder.contactId === 'c-rajesh'));
    assert.equal(queue.snoozed[0]?.contactId, 'c-mira');
    assert.equal(queue.snoozed[0]?.lastContactedAt, '2026-04-01T09:00:00.000Z');
    assert.match(queue.snoozed[0]?.detail ?? '', /history is unchanged/i);
  });

  it('snoozes check-ins with a separate snooze date and keeps last-contact history truthful', () => {
    const state = createTestState();
    const before = state.contacts.find(contact => contact.id === 'c-mira')?.lastContactedAt;
    const snoozed = relateReducer(state, {
      type: 'snoozeCheckIn',
      contactId: 'c-mira',
      days: 14,
      nowIso: '2026-07-09T09:00:00.000Z'
    });
    const contact = snoozed.contacts.find(item => item.id === 'c-mira');

    assert.equal(contact?.lastContactedAt, before);
    assert.equal(contact?.checkInSnoozedUntil, '2026-07-23T09:00:00.000Z');
    assert.equal(snoozed.activity[0].title, 'Check-in snoozed');
  });

  it('lets the user mark a relationship contacted elsewhere without creating or sending a message', () => {
    const state = createTestState();
    const marked = relateReducer(
      {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === 'c-mira' ? { ...contact, checkInSnoozedUntil: '2026-07-23T09:00:00.000Z' } : contact
        )
      },
      {
        type: 'markContactedElsewhere',
        contactId: 'c-mira',
        nowIso: '2026-07-09T09:00:00.000Z'
      }
    );
    const contact = marked.contacts.find(item => item.id === 'c-mira');

    assert.equal(contact?.lastContactedAt, '2026-07-09T09:00:00.000Z');
    assert.equal(contact?.checkInSnoozedUntil, undefined);
    assert.equal(marked.messages.length, state.messages.length);
    assert.equal(marked.activity[0].title, 'Contact marked contacted');
  });
});
