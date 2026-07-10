import assert from 'node:assert/strict';
import { afterEach, describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { createFixedClock, type CommandDependencies, type IdGenerator } from './commandMetadata';
import { createRelateReducer, enrichRelateAction, relateTransition } from './relateReducer';

const originalTimeZone = process.env.TZ;

afterEach(() => {
  process.env.TZ = originalTimeZone;
});

const sequentialIds = (): IdGenerator => {
  let sequence = 0;
  return {
    nextId: kind => `${kind}-command-${++sequence}`
  };
};

const dependencies = (
  instantValue = '2026-07-10T09:30:00.000Z',
  localDateValue = '2026-07-10'
): CommandDependencies => ({
  clock: createFixedClock(instantValue, localDateValue),
  idGenerator: sequentialIds()
});

describe('deterministic reducer command boundary', () => {
  it('replays the same enriched action to byte-for-byte equivalent state', () => {
    const state = createTestState();
    const action = enrichRelateAction(
      {
        type: 'generateMessage',
        contactId: 'c-rajesh',
        eventId: 'e-rajesh-work',
        reason: 'Congratulations'
      },
      dependencies()
    );

    const first = relateTransition(state, action);
    const replay = relateTransition(state, action);

    assert.deepEqual(replay, first);
    assert.equal(first.messages[0].id, action.metadata.ids.message[0]);
    assert.equal(first.activity[0].id, action.metadata.ids.activity[0]);
    assert.equal(first.activity[0].createdAt, action.metadata.occurredAt);
  });

  it('keeps existing raw-action callers behind an injected enrichment adapter', () => {
    const reducer = createRelateReducer(dependencies());
    const next = reducer(createTestState(), {
      type: 'addMemory',
      contactId: 'c-mira',
      category: 'Preference',
      body: 'Prefers a quiet birthday dinner with close friends.'
    });

    assert.equal(next.memories[0].id, 'memory-command-2');
    assert.equal(next.memories[0].createdAt, '2026-07-10T09:30:00.000Z');
    assert.equal(next.activity[0].id, 'activity-command-1');
  });

  it('schedules a yearly event at the next local occurrence and configured send time', () => {
    process.env.TZ = 'Pacific/Kiritimati';
    const reducer = createRelateReducer(dependencies('2026-07-10T09:30:00.000Z', '2026-07-10'));
    const base = createTestState();
    const state = {
      ...base,
      settings: {
        ...base.settings,
        defaultSendTime: '09:15'
      },
      events: base.events.map(event =>
        event.id === 'e-asha-bday'
          ? {
              ...event,
              date: '2020-06-15',
              recurrence: {
                frequency: 'Yearly' as const,
                month: 6,
                day: 15,
                originalYear: 2020,
                leapDayPolicy: 'February 28' as const
              }
            }
          : event
      )
    };

    const next = reducer(state, {
      type: 'generateMessage',
      contactId: 'c-asha',
      eventId: 'e-asha-bday',
      reason: 'Birthday'
    });

    assert.equal(next.messages[0].scheduledFor, '2027-06-14T19:15:00.000Z');
    assert.equal(next.messages[0].status, 'Needs review');
  });

  it('rejects a draft when the selected event belongs to another contact', () => {
    const reducer = createRelateReducer(dependencies());
    const state = createTestState();

    const next = reducer(state, {
      type: 'generateMessage',
      contactId: 'c-mira',
      eventId: 'e-rajesh-work',
      reason: 'Congratulations'
    });

    assert.equal(next.messages.length, state.messages.length);
    assert.match(next.activity[0].detail, /no longer belong together/i);
    assert.equal(next.activity[0].severity, 'Error');
  });

  it('keeps approval review-safe when a channel blackout blocks the occurrence', () => {
    process.env.TZ = 'UTC';
    const reducer = createRelateReducer(dependencies());
    const base = createTestState();
    const state = {
      ...base,
      messages: base.messages.filter(message => message.eventId !== 'e-asha-bday'),
      settings: {
        ...base.settings,
        blackouts: [
          {
            id: 'blackout-sms-holiday',
            label: 'SMS holiday block',
            startDate: '2026-12-20',
            endDate: '2026-12-25',
            behavior: 'Block' as const,
            channels: ['SMS' as const]
          }
        ]
      },
      events: base.events.map(event =>
        event.id === 'e-asha-bday'
          ? {
              ...event,
              date: '2026-12-22',
              recurrence: {
                frequency: 'Yearly' as const,
                month: 12,
                day: 22,
                originalYear: 2026,
                leapDayPolicy: 'February 28' as const
              }
            }
          : event
      )
    };
    const drafted = reducer(state, {
      type: 'generateMessage',
      contactId: 'c-asha',
      eventId: 'e-asha-bday',
      reason: 'Birthday'
    });
    const messageId = drafted.messages[0].id;
    const approved = reducer(drafted, { type: 'approveMessage', messageId });

    assert.equal(drafted.messages[0].status, 'Needs review');
    assert.match(drafted.messages[0].lastError ?? '', /SMS holiday block/i);
    assert.equal(approved.messages[0].status, 'Blocked');
    assert.match(approved.messages[0].lastError ?? '', /SMS holiday block/i);
  });

  it('allocates distinct imported identities even when sanitized source IDs collide', () => {
    const reducer = createRelateReducer(dependencies());
    const state = reducer(createTestState(), {
      type: 'calendarImported',
      candidates: [
        {
          sourceId: 'same/source',
          title: 'Anika Birthday',
          startDate: '2026-11-10T09:00:00.000Z'
        },
        {
          sourceId: 'same?source',
          title: 'Dev Birthday',
          startDate: '2026-12-10T09:00:00.000Z'
        }
      ]
    });
    const importedEvents = state.events.filter(event => ['Anika Birthday', 'Dev Birthday'].includes(event.label));
    const importedContactIds = importedEvents.map(event => event.contactId);

    assert.equal(new Set(importedEvents.map(event => event.id)).size, 2);
    assert.equal(new Set(importedContactIds).size, 2);
  });

  it('keeps repeated calendar reducer imports idempotent even though each command allocates fresh ids', () => {
    const reducer = createRelateReducer(dependencies());
    const candidate = {
      sourceId: 'stable-calendar-reducer-source',
      title: 'Reducer Source Person Anniversary',
      startDate: '2018-06-12T12:00:00.000Z'
    };
    const first = reducer(createTestState(), { type: 'calendarImported', candidates: [candidate] });
    const imported = first.events.find(event => event.label === candidate.title);
    const second = reducer(first, {
      type: 'calendarImported',
      candidates: [{ ...candidate, startDate: '2027-06-12T12:00:00.000Z' }]
    });

    assert.ok(imported);
    assert.equal(second.events.length, first.events.length);
    assert.equal(second.contacts.length, first.contacts.length);
    assert.equal(second.events.filter(event => event.label === candidate.title).length, 1);
    assert.equal(second.events.find(event => event.label === candidate.title)?.id, imported?.id);
    assert.match(second.activity[0].detail, /0 event\(s\).*1 skipped, 0 need review/i);
  });

  it('uses injected LocalDate for calendar year and Instant for audit time across time zones', () => {
    const state = createTestState();
    const action = enrichRelateAction(
      {
        type: 'addGift',
        contactId: 'c-asha',
        name: 'Handmade album',
        category: 'Personal',
        occasion: 'New Year',
        cost: 1200
      },
      dependencies('2026-12-31T18:45:00.000Z', '2027-01-01')
    );

    process.env.TZ = 'Pacific/Kiritimati';
    const ahead = relateTransition(state, action);
    process.env.TZ = 'America/Adak';
    const behind = relateTransition(state, action);

    assert.deepEqual(behind, ahead);
    assert.equal(ahead.gifts[0].year, 2027);
    assert.equal(ahead.activity[0].createdAt, '2026-12-31T18:45:00.000Z');
  });

  it('adds elapsed command days without host time-zone or DST drift', () => {
    const state = createTestState();
    const action = enrichRelateAction(
      {
        type: 'snoozeCheckIn',
        contactId: 'c-mira',
        days: 1
      },
      dependencies('2026-03-08T09:30:00.000Z', '2026-03-08')
    );

    process.env.TZ = 'America/Los_Angeles';
    const pacific = relateTransition(state, action);
    process.env.TZ = 'Asia/Kolkata';
    const india = relateTransition(state, action);

    assert.deepEqual(india, pacific);
    assert.equal(
      pacific.contacts.find(contact => contact.id === 'c-mira')?.checkInSnoozedUntil,
      '2026-03-09T09:30:00.000Z'
    );
  });
});
