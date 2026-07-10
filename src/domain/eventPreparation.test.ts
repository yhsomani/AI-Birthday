import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
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
      { id: 'write-wish', label: 'Write or review wish', done: true },
      { id: 'choose-channel', label: 'Choose send channel', done: false }
    ]);

    assert.equal(normalized.find(item => item.id === 'write-message')?.done, true);
    assert.equal(normalized.find(item => item.id === 'schedule-reminder')?.done, false);
  });

  it('derives completion from event data without exposing private memories', () => {
    const state = createTestState();
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
            triggerAt: '2026-01-02T10:00:00.000Z'
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

    assert.equal(event?.checklist.some(item => item.id === 'write-message'), true);
    assert.equal(event?.checklist.find(item => item.id === 'write-message')?.done, true);
    assert.equal(event?.checklist.some(item => item.id === 'schedule-reminder'), true);
  });
});
