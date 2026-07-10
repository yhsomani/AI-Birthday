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
      { aiEndpointConfigured: true, emailEndpointConfigured: false, aiProviderSessionReady: true },
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

  it('requires a live authenticated session and a successful provider test before AI is ready', () => {
    const state = createTestState();
    state.aiProvider = { status: 'Not configured' };
    const endpoint = evaluateProviderEndpointReadiness('https://ai.example.test/draft');

    const missingSession = buildSetupWizardPlan(
      state,
      { aiEndpointReadiness: endpoint, aiProviderSessionReady: false },
      'AI drafts'
    );
    const untested = buildSetupWizardPlan(
      state,
      { aiEndpointReadiness: endpoint, aiProviderSessionReady: true },
      'AI drafts'
    );
    state.aiProvider = { status: 'Ready', lastCheckedAt: '2026-07-10T10:00:00.000Z' };
    const tested = buildSetupWizardPlan(
      state,
      { aiEndpointReadiness: endpoint, aiProviderSessionReady: true },
      'AI drafts'
    );

    const missingSessionStep = missingSession.steps.find(step => step.id === 'ai-provider');
    const untestedStep = untested.steps.find(step => step.id === 'ai-provider');
    const testedStep = tested.steps.find(step => step.id === 'ai-provider');
    assert.equal(missingSessionStep?.status, 'Needs action');
    assert.equal(missingSessionStep?.command, undefined);
    assert.match(missingSessionStep?.detail ?? '', /authenticated provider session/i);
    assert.equal(untestedStep?.status, 'Needs action');
    assert.equal(untestedStep?.command, 'testAiProvider');
    assert.equal(testedStep?.status, 'Ready');
  });

  it('does not offer reminder planning until its event and notification prerequisites are ready', () => {
    const state = createTestState();
    state.events = [];
    state.reminderPlans = [];

    const plan = buildSetupWizardPlan(state, {}, 'Reminders only');
    const reminderStep = plan.steps.find(step => step.id === 'reminder-plans');

    assert.equal(plan.recommendedStep?.id, 'events');
    assert.equal(reminderStep?.command, undefined);
    assert.equal(reminderStep?.targetScreen, 'setupCheck');
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

  it('keeps provider email actionable until its authenticated session is live', () => {
    const state = createTestState();
    state.settings.emailEnabled = true;
    const endpoint = evaluateProviderEndpointReadiness('https://email.example.test/send');
    const missingSession = buildSetupWizardPlan(
      state,
      { emailEndpointReadiness: endpoint, emailProviderSessionReady: false },
      'Manual sends'
    );
    const readySession = buildSetupWizardPlan(
      state,
      { emailEndpointReadiness: endpoint, emailProviderSessionReady: true },
      'Manual sends'
    );

    const missingStep = missingSession.steps.find(step => step.id === 'email-route');
    const readyStep = readySession.steps.find(step => step.id === 'email-route');
    assert.equal(missingStep?.status, 'Needs action');
    assert.match(missingStep?.detail ?? '', /authenticated session/i);
    assert.equal(readyStep?.status, 'Ready');
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
