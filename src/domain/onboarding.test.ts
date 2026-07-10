import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import {
  buildOnboardingCompletionGate,
  buildOnboardingPlan,
  nextOnboardingStep,
  requiredOnboardingStepIds
} from './onboarding';

describe('onboarding contract', () => {
  it('starts first-run setup without enabling permissions silently', () => {
    const state = createTestState();
    const plan = buildOnboardingPlan(state);

    assert.equal(state.activeScreen, 'onboarding');
    assert.equal(plan.completed, false);
    assert.equal(plan.nextStep.id, 'intro');
    assert.equal(plan.steps.find(step => step.id === 'intro')?.canSkip, true);
    assert.equal(plan.steps.find(step => step.id === 'account')?.requiredForGoal, true);
    assert.equal(plan.steps.find(step => step.id === 'notifications')?.requirementSatisfied, false);
    assert.equal(plan.completionGate.canComplete, false);
    assert.equal(state.privacy.permissionDecisions.Contacts, 'Not requested');
    assert.equal(state.privacy.permissionDecisions.Notifications, 'Not requested');
  });

  it('allows optional education to be skipped but blocks required choices and permission decisions', () => {
    const state = createTestState();
    state.contacts = [];

    const account = relateReducer(state, { type: 'skipOnboardingStep', stepId: 'intro' });
    const accountSkipBlocked = relateReducer(account, { type: 'skipOnboardingStep', stepId: 'account' });
    const contacts = relateReducer(accountSkipBlocked, { type: 'advanceOnboarding' });
    const contactsAdvanceBlocked = relateReducer(contacts, { type: 'advanceOnboarding' });
    const withContact = { ...contactsAdvanceBlocked, contacts: createTestState().contacts };
    const notifications = relateReducer(withContact, { type: 'advanceOnboarding' });
    const notificationSkipBlocked = relateReducer(notifications, {
      type: 'skipOnboardingStep',
      stepId: 'notifications'
    });
    const notificationAdvanceBlocked = relateReducer(notificationSkipBlocked, { type: 'advanceOnboarding' });
    const denied = relateReducer(notificationAdvanceBlocked, {
      type: 'recordPermissionDecision',
      capability: 'Notifications',
      decision: 'Denied'
    });
    const ai = relateReducer(denied, { type: 'advanceOnboarding' });

    assert.equal(nextOnboardingStep('intro'), 'account');
    assert.equal(account.onboarding.currentStepId, 'account');
    assert.ok(account.onboarding.skippedStepIds.includes('intro'));
    assert.equal(accountSkipBlocked, account);
    assert.deepEqual(contacts.onboarding.completedStepIds, ['account']);
    assert.equal(contactsAdvanceBlocked, contacts);
    assert.equal(notifications.onboarding.currentStepId, 'notifications');
    assert.equal(notificationSkipBlocked, notifications);
    assert.equal(notificationAdvanceBlocked, notifications);
    assert.equal(ai.onboarding.currentStepId, 'ai');
    assert.ok(ai.onboarding.completedStepIds.includes('notifications'));
  });

  it('requires only the minimum steps for the selected goal and lets optional setup wait', () => {
    const manual = createTestState();
    manual.onboarding = {
      ...manual.onboarding,
      selectedGoal: 'Manual relationship manager',
      currentStepId: 'finish',
      completedStepIds: ['account', 'contacts', 'channels']
    };
    const manualComplete = relateReducer(manual, { type: 'completeOnboarding' });

    const aiBlocked = createTestState();
    aiBlocked.onboarding = {
      ...aiBlocked.onboarding,
      selectedGoal: 'AI wishes',
      currentStepId: 'finish',
      completedStepIds: ['account', 'contacts', 'ai']
    };
    const aiUnchanged = relateReducer(aiBlocked, { type: 'completeOnboarding' });
    const aiReady = { ...aiBlocked, aiProvider: { status: 'Ready' as const } };
    const aiComplete = relateReducer(aiReady, { type: 'completeOnboarding' });

    const reminders = createTestState();
    reminders.privacy.permissionDecisions.Notifications = 'Denied';
    reminders.onboarding = {
      ...reminders.onboarding,
      currentStepId: 'finish',
      completedStepIds: ['account', 'contacts', 'notifications']
    };
    const remindersComplete = relateReducer(reminders, { type: 'completeOnboarding' });

    assert.deepEqual(requiredOnboardingStepIds('Manual relationship manager'), ['account', 'contacts', 'channels']);
    assert.equal(buildOnboardingCompletionGate(manual).canComplete, true);
    assert.equal(manualComplete.onboarding.completed, true);
    assert.equal(manualComplete.activeScreen, 'home');
    assert.equal(aiUnchanged, aiBlocked);
    assert.match(buildOnboardingCompletionGate(aiBlocked).blockers[0]?.detail ?? '', /provider readiness test/i);
    assert.equal(aiComplete.onboarding.completed, true);
    assert.equal(remindersComplete.onboarding.completed, true);
  });

  it('prevents forward jumps while preserving explicit back navigation and goal-change progress', () => {
    const state = createTestState();
    const jumpBlocked = relateReducer(state, { type: 'setOnboardingStep', stepId: 'finish' });
    const account = relateReducer(state, { type: 'advanceOnboarding' });
    const intro = relateReducer(account, { type: 'setOnboardingStep', stepId: 'intro' });
    const accountAgain = relateReducer(intro, { type: 'setOnboardingStep', stepId: 'account' });

    const withSkippedAi = createTestState();
    withSkippedAi.onboarding.skippedStepIds = ['ai'];
    const aiGoal = relateReducer(withSkippedAi, { type: 'setOnboardingGoal', goal: 'AI wishes' });

    assert.equal(jumpBlocked, state);
    assert.equal(intro.onboarding.currentStepId, 'intro');
    assert.equal(accountAgain.onboarding.currentStepId, 'account');
    assert.deepEqual(accountAgain.onboarding.completedStepIds, ['intro']);
    assert.equal(aiGoal.onboarding.skippedStepIds.includes('ai'), false);
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
