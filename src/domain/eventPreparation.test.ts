import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createFixedClock } from '../state/commandMetadata';
import { createRelateReducer, relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import {
  buildDefaultEventPreparationChecklist,
  buildEventPreparationPlan,
  normalizeEventPreparationChecklist
} from './eventPreparation';

describe('event preparation checklist contract', () => {
  it('builds the ideal preparation steps for gift-relevant events', () => {
    const checklist = buildDefaultEventPreparationChecklist('Birthday');

    assert.deepEqual(
      checklist.map(item => item.id),
      ['confirm-date', 'improve-context', 'write-message', 'decide-gift', 'choose-channel', 'schedule-reminder']
    );
    assert.equal(checklist.find(item => item.id === 'confirm-date')?.done, true);
    assert.equal(checklist.find(item => item.id === 'decide-gift')?.done, false);
  });

  it('omits gift prep for follow-up events so users complete only relevant items', () => {
    const checklist = buildDefaultEventPreparationChecklist('Follow-up');

    assert.deepEqual(
      checklist.map(item => item.id),
      ['confirm-date', 'improve-context', 'write-message', 'choose-channel', 'schedule-reminder']
    );
    assert.equal(checklist.find(item => item.id === 'write-message')?.label, 'Write check-in');
  });

  it('normalizes legacy checklist ids into the canonical preparation plan', () => {
    const normalized = normalizeEventPreparationChecklist('Birthday', [
      { id: 'confirm-date', label: 'Confirm date', done: true },
      { id: 'older-write-row', label: 'Write or review wish', done: true },
      { id: 'older-route-row', label: 'Use manual send handoff', done: true }
    ]);

    assert.equal(normalized.find(item => item.id === 'write-message')?.done, true);
    assert.equal(normalized.find(item => item.id === 'choose-channel')?.done, true);
    assert.equal(normalized.find(item => item.id === 'schedule-reminder')?.done, false);
  });

  it('derives completion from event data without exposing private memories', () => {
    const state = createTestState();
    const event = state.events.find(item => item.id === 'e-asha-bday');
    assert.ok(event);
    const plan = buildEventPreparationPlan(
      {
        ...state,
        memories: [
          ...state.memories,
          {
            id: 'private-asha',
            contactId: 'c-asha',
            category: 'Private',
            body: 'Private party detail.',
            pinned: false,
            createdAt: '2026-01-01T00:00:00.000Z'
          }
        ],
        reminderPlans: [
          {
            id: 'reminder-asha',
            eventId: 'e-asha-bday',
            contactId: 'c-asha',
            title: 'Review Asha birthday',
            body: 'Open RelateAI to review.',
            triggerAt: event.date
          }
        ]
      },
      'e-asha-bday'
    );

    assert.equal(plan.ok, true);
    if (plan.ok) {
      assert.equal(plan.steps.find(step => step.id === 'improve-context')?.done, true);
      assert.match(plan.steps.find(step => step.id === 'improve-context')?.detail ?? '', /1 non-private memory/i);
      assert.doesNotMatch(plan.steps.find(step => step.id === 'improve-context')?.detail ?? '', /Private party/i);
      assert.equal(plan.steps.find(step => step.id === 'write-message')?.done, true);
      assert.equal(plan.steps.find(step => step.id === 'choose-channel')?.done, true);
      assert.equal(plan.steps.find(step => step.id === 'schedule-reminder')?.done, true);
      assert.equal(plan.nextStep?.id, 'decide-gift');
    }
  });

  it('toggles a canonical prep step on older events with legacy checklist ids', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'togglePreparationStep',
      eventId: 'e-asha-bday',
      stepId: 'write-message'
    });
    const event = next.events.find(item => item.id === 'e-asha-bday');

    assert.equal(
      event?.checklist.some(item => item.id === 'write-message'),
      true
    );
    assert.equal(event?.checklist.find(item => item.id === 'write-message')?.done, true);
    assert.equal(
      event?.checklist.some(item => item.id === 'schedule-reminder'),
      true
    );
  });

  it('binds explicit completion to one recurring occurrence through the reducer', () => {
    const base = createTestState();
    const state = {
      ...base,
      events: base.events.map(event =>
        event.id === 'e-asha-bday'
          ? {
              ...event,
              date: '2025-01-10T12:00:00.000Z',
              recurrence: {
                frequency: 'Yearly' as const,
                month: 1,
                day: 10,
                originalYear: 2025,
                leapDayPolicy: 'February 28' as const
              },
              checklist: [{ id: 'write-wish', label: 'Write the wish', done: false }]
            }
          : event
      ),
      messages: [],
      reminderPlans: []
    };
    const reducer = createRelateReducer({
      clock: createFixedClock('2026-01-01T09:00:00.000Z', '2026-01-01'),
      idGenerator: { nextId: kind => `${kind}-unused` }
    });

    const completed = reducer(state, {
      type: 'togglePreparationStep',
      eventId: 'e-asha-bday',
      stepId: 'write-message'
    });
    const storedStep = completed.events
      .find(event => event.id === 'e-asha-bday')
      ?.checklist.find(item => item.id === 'write-message');
    const thisOccurrence = buildEventPreparationPlan(completed, 'e-asha-bday', new Date(2026, 0, 1, 12));
    const nextOccurrence = buildEventPreparationPlan(completed, 'e-asha-bday', new Date(2026, 0, 11, 12));

    assert.equal(storedStep?.completedForOccurrence, '2026-01-10');
    assert.equal(thisOccurrence.ok && thisOccurrence.steps.find(step => step.id === 'write-message')?.done, true);
    assert.equal(nextOccurrence.ok && nextOccurrence.steps.find(step => step.id === 'write-message')?.done, false);
  });

  it('keeps legacy ids compatible without carrying their completion into another year', () => {
    const base = createTestState();
    const state = {
      ...base,
      events: base.events.map(event =>
        event.id === 'e-asha-bday'
          ? {
              ...event,
              date: '2026-04-20T12:00:00.000Z',
              recurrence: {
                frequency: 'Yearly' as const,
                month: 4,
                day: 20,
                originalYear: 2026,
                leapDayPolicy: 'February 28' as const
              },
              checklist: [{ id: 'write-wish', label: 'Legacy wish label', done: true }]
            }
          : event
      ),
      messages: []
    };

    const legacyOccurrence = buildEventPreparationPlan(state, 'e-asha-bday', new Date(2026, 3, 1, 12));
    const followingOccurrence = buildEventPreparationPlan(state, 'e-asha-bday', new Date(2026, 3, 21, 12));

    assert.equal(legacyOccurrence.ok && legacyOccurrence.steps.find(step => step.id === 'write-message')?.done, true);
    assert.equal(
      followingOccurrence.ok && followingOccurrence.steps.find(step => step.id === 'write-message')?.done,
      false
    );
  });

  it('only accepts a message and reminder for the current event occurrence', () => {
    const base = createTestState();
    const event = {
      ...base.events.find(item => item.id === 'e-asha-bday')!,
      date: '2025-06-15T12:00:00.000Z',
      recurrence: {
        frequency: 'Yearly' as const,
        month: 6,
        day: 15,
        originalYear: 2025,
        leapDayPolicy: 'February 28' as const
      },
      checklist: buildDefaultEventPreparationChecklist('Birthday').map(item => ({ ...item, done: false }))
    };
    const oldWork = {
      ...base,
      events: base.events.map(item => (item.id === event.id ? event : item)),
      messages: [
        {
          ...base.messages[0],
          eventId: event.id,
          occurrenceDate: '2025-06-15',
          scheduledFor: '2025-06-15T09:00:00.000Z'
        }
      ],
      reminderPlans: [
        {
          id: `reminder-${event.id}-7`,
          eventId: event.id,
          contactId: event.contactId,
          title: 'Old reminder',
          body: 'Old reminder',
          triggerAt: '2025-06-08T09:00:00.000Z'
        }
      ]
    };
    const oldPlan = buildEventPreparationPlan(oldWork, event.id, new Date(2026, 5, 1, 12));
    const currentWork = {
      ...oldWork,
      messages: oldWork.messages.map(message => ({
        ...message,
        occurrenceDate: '2026-06-15',
        scheduledFor: '2026-06-15T09:00:00.000Z'
      })),
      reminderPlans: oldWork.reminderPlans.map(plan => ({
        ...plan,
        triggerAt: '2026-06-08T09:00:00.000Z'
      }))
    };
    const currentPlan = buildEventPreparationPlan(currentWork, event.id, new Date(2026, 5, 1, 12));

    assert.equal(oldPlan.ok && oldPlan.steps.find(step => step.id === 'write-message')?.done, false);
    assert.equal(oldPlan.ok && oldPlan.steps.find(step => step.id === 'schedule-reminder')?.done, false);
    assert.equal(currentPlan.ok && currentPlan.steps.find(step => step.id === 'write-message')?.done, true);
    assert.equal(currentPlan.ok && currentPlan.steps.find(step => step.id === 'schedule-reminder')?.done, true);
  });
});
