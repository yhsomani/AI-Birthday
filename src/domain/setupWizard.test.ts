import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState } from '../state/relateReducer';
import { buildSetupWizardPlan } from './setupWizard';

describe('setup wizard contract', () => {
  it('shows only reminder-related steps for reminders-only setup', () => {
    const plan = buildSetupWizardPlan(
      createInitialState(),
      { aiEndpointConfigured: false, emailEndpointConfigured: false },
      'Reminders only'
    );

    assert.deepEqual(
      plan.steps.map(step => step.id),
      ['reminder-plans', 'notifications', 'events']
    );
    assert.equal(plan.recommendedStep?.command, 'planReminders');
  });

  it('routes AI draft setup to provider testing when an endpoint exists', () => {
    const state = {
      ...createInitialState(),
      aiProvider: {
        status: 'Error' as const,
        lastError: 'Provider rejected credentials.'
      }
    };
    const plan = buildSetupWizardPlan(
      state,
      { aiEndpointConfigured: true, emailEndpointConfigured: false },
      'AI drafts'
    );

    assert.equal(plan.steps[0].id, 'ai-provider');
    assert.equal(plan.steps[0].status, 'Needs action');
    assert.equal(plan.steps[0].command, 'testAiProvider');
  });

  it('keeps manual send setup focused on contact and channel routes', () => {
    const state = {
      ...createInitialState(),
      contacts: [],
      settings: {
        ...createInitialState().settings,
        whatsappHandoffEnabled: false,
        emailEnabled: true
      }
    };
    const plan = buildSetupWizardPlan(
      state,
      { aiEndpointConfigured: false, emailEndpointConfigured: false },
      'Manual sends'
    );

    assert.deepEqual(
      plan.steps.map(step => step.id),
      ['contacts', 'email-route', 'manual-whatsapp']
    );
    assert.equal(plan.steps[0].status, 'Needs action');
    assert.equal(plan.steps[1].status, 'Needs action');
  });

  it('combines setup dependencies for automation without duplicate step rows', () => {
    const plan = buildSetupWizardPlan(
      createInitialState(),
      { aiEndpointConfigured: false, emailEndpointConfigured: false },
      'Automation'
    );
    const ids = plan.steps.map(step => step.id);

    assert.equal(new Set(ids).size, ids.length);
    assert.ok(ids.includes('automation-mode'));
    assert.ok(ids.includes('ai-provider'));
    assert.ok(ids.includes('reminder-plans'));
  });
});
