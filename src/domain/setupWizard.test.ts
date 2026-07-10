import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { evaluateProviderEndpointReadiness } from './providerEndpointReadiness';
import { buildSetupWizardPlan } from './setupWizard';

describe('setup wizard contract', () => {
  it('shows only reminder-related steps for reminders-only setup', () => {
    const plan = buildSetupWizardPlan(
      createTestState(),
      { aiEndpointConfigured: false, emailEndpointConfigured: false },
      'Reminders only'
    );

    assert.deepEqual(
      plan.steps.map(step => step.id),
      ['reminder-plans', 'notifications', 'scheduling-policy', 'events']
    );
    assert.equal(plan.recommendedStep?.command, 'planReminders');
  });

  it('routes AI draft setup to provider testing when an endpoint exists', () => {
    const state = {
      ...createTestState(),
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

  it('keeps local provider endpoints as development-only setup blockers', () => {
    const plan = buildSetupWizardPlan(
      createTestState(),
      {
        aiEndpointReadiness: evaluateProviderEndpointReadiness('http://localhost:8787/draft', {
          allowLocalDevelopment: true
        }),
        emailEndpointConfigured: false
      },
      'AI drafts'
    );
    const providerStep = plan.steps.find(step => step.id === 'ai-provider');

    assert.equal(providerStep?.status, 'Needs action');
    assert.equal(providerStep?.command, 'testAiProvider');
    assert.match(providerStep?.detail ?? '', /development only/i);
  });

  it('omits provider email setup from manual sends until email is chosen or configured', () => {
    const plan = buildSetupWizardPlan(
      createTestState(),
      { aiEndpointConfigured: false, emailEndpointConfigured: false },
      'Manual sends'
    );

    assert.deepEqual(
      plan.steps.map(step => step.id),
      ['contacts', 'manual-whatsapp']
    );
    assert.equal(plan.recommendedStep, undefined);
  });

  it('keeps manual send setup focused on contact and channel routes without requiring provider email', () => {
    const state = {
      ...createTestState(),
      contacts: [],
      settings: {
        ...createTestState().settings,
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
      ['contacts', 'manual-whatsapp', 'email-route']
    );
    const emailStep = plan.steps.find(step => step.id === 'email-route');

    assert.equal(plan.steps[0].status, 'Needs action');
    assert.equal(emailStep?.status, 'Optional');
    assert.match(emailStep?.detail ?? '', /provider delivery stays optional/i);
  });

  it('blocks unsafe email endpoints once provider email is chosen', () => {
    const initial = createTestState();
    const state = {
      ...initial,
      settings: {
        ...initial.settings,
        emailEnabled: true
      }
    };
    const plan = buildSetupWizardPlan(
      state,
      {
        aiEndpointConfigured: false,
        emailEndpointReadiness: evaluateProviderEndpointReadiness('https://user:secret@email.example.test/send')
      },
      'Manual sends'
    );
    const emailStep = plan.steps.find(step => step.id === 'email-route');

    assert.equal(emailStep?.status, 'Needs action');
    assert.match(emailStep?.detail ?? '', /not safe for production/i);
    assert.doesNotMatch(emailStep?.detail ?? '', /secret|email\.example/);
  });

  it('surfaces configured unsafe email endpoints even when email delivery is off', () => {
    const plan = buildSetupWizardPlan(
      createTestState(),
      {
        aiEndpointConfigured: false,
        emailEndpointReadiness: evaluateProviderEndpointReadiness('https://user:secret@email.example.test/send')
      },
      'Manual sends'
    );
    const emailStep = plan.steps.find(step => step.id === 'email-route');

    assert.equal(plan.steps[0].id, 'email-route');
    assert.equal(emailStep?.status, 'Needs action');
    assert.match(emailStep?.detail ?? '', /not safe for production/i);
    assert.doesNotMatch(emailStep?.detail ?? '', /secret|email\.example|send/);
  });

  it('combines setup dependencies for automation without duplicate step rows', () => {
    const plan = buildSetupWizardPlan(
      createTestState(),
      { aiEndpointConfigured: false, emailEndpointConfigured: false },
      'Automation'
    );
    const ids = plan.steps.map(step => step.id);

    assert.equal(new Set(ids).size, ids.length);
    assert.ok(ids.includes('automation-mode'));
    assert.ok(ids.includes('ai-provider'));
    assert.ok(ids.includes('reminder-plans'));
    assert.match(plan.summary, /review workflow/i);
    assert.doesNotMatch(plan.summary, /automation setup/i);
    assert.equal(plan.steps.find(step => step.id === 'automation-mode')?.title, 'Review workflow');
  });
});
