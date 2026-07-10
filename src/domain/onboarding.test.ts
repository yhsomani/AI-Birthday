import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildOnboardingPlan, nextOnboardingStep } from './onboarding';

describe('onboarding contract', () => {
  it('starts first-run setup without enabling permissions silently', () => {
    const state = createTestState();
    const plan = buildOnboardingPlan(state);

    assert.equal(state.activeScreen, 'onboarding');
    assert.equal(plan.completed, false);
    assert.equal(plan.nextStep.id, 'intro');
    assert.equal(state.privacy.permissionDecisions.Contacts, 'Not requested');
    assert.equal(state.privacy.permissionDecisions.Notifications, 'Not requested');
  });

  it('preserves onboarding progress across continue, skip, and completion actions', () => {
    const state = createTestState();
    const account = relateReducer(state, { type: 'advanceOnboarding' });
    const contacts = relateReducer(account, { type: 'advanceOnboarding' });
    const notifications = relateReducer(contacts, { type: 'skipOnboardingStep', stepId: 'contacts' });
    const completed = relateReducer(notifications, { type: 'completeOnboarding' });

    assert.equal(nextOnboardingStep('intro'), 'account');
    assert.equal(account.onboarding.currentStepId, 'account');
    assert.deepEqual(contacts.onboarding.completedStepIds, ['intro', 'account']);
    assert.ok(notifications.onboarding.skippedStepIds.includes('contacts'));
    assert.equal(completed.onboarding.completed, true);
    assert.equal(completed.activeScreen, 'home');
  });

  it('keeps unavailable Google sync out of active account state', () => {
    const state = createTestState();
    const google = relateReducer(state, { type: 'setAccountMode', mode: 'Google sync' });
    const local = relateReducer(google, { type: 'disconnectAccount' });

    assert.equal(google.settings.accountMode, 'Local');
    assert.match(google.activity[0].detail, /not available/i);
    assert.equal(local.settings.accountMode, 'Local');
    assert.equal(local.contacts.length, state.contacts.length);
  });
});
