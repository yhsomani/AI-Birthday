import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createProductionInitialState } from '../data/productionState';
import { createTestState } from '../test/testState';
import { buildHomePlanner } from './homePlanner';

describe('relationship home planner', () => {
  it('ranks recovery and review ahead of planning suggestions', () => {
    const state = createTestState();
    state.messages[0] = { ...state.messages[0], status: 'Delivery unknown' };
    const planner = buildHomePlanner(state, new Date('2026-07-09T12:00:00.000Z'));
    assert.equal(planner.actions[0]?.kind, 'recover-message');
    assert.ok(planner.counts['review-message'] > 0);
    assert.ok(planner.actions.length <= 12);
  });

  it('returns setup and backup actions for an empty fresh install', () => {
    const planner = buildHomePlanner(createProductionInitialState(), new Date('2026-07-09T12:00:00.000Z'));
    assert.equal(planner.actions.find(action => action.kind === 'create-backup')?.targetScreen, 'backup');
    assert.equal(planner.actions.find(action => action.kind === 'complete-setup')?.targetScreen, 'onboarding');

    const completed = createProductionInitialState();
    completed.onboarding.completed = true;
    const setupPlanner = buildHomePlanner(completed, new Date('2026-07-09T12:00:00.000Z'), {
      setupNeedsAction: true
    });
    assert.equal(setupPlanner.actions.find(action => action.kind === 'complete-setup')?.targetScreen, 'setupCheck');
  });

  it('does not rank archived contacts or their records', () => {
    const state = createTestState();
    state.contacts = state.contacts.map(contact => ({
      ...contact,
      archivedAt: '2026-07-09T00:00:00.000Z'
    }));
    const planner = buildHomePlanner(state, new Date('2026-07-09T12:00:00.000Z'));
    assert.equal(
      planner.actions.some(action => action.contactId),
      false
    );
  });
});
