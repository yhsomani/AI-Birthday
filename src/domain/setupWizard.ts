import { productAvailability } from '../config/productAvailability';
import { resolveContactPreferencesForContact } from './contactPreferences';
import { providerEndpointReadinessFromConfigured, type ProviderEndpointReadiness } from './providerEndpointReadiness';
import { buildSchedulingPolicySummary } from './schedulingPolicy';
import type { AppState, Screen, SetupCheck } from './types';

export type SetupGoal = 'Reminders only' | 'AI drafts' | 'Manual sends' | 'Automation';

export type SetupEnvironment = {
  aiEndpointConfigured?: boolean;
  emailEndpointConfigured?: boolean;
  aiEndpointReadiness?: ProviderEndpointReadiness;
  emailEndpointReadiness?: ProviderEndpointReadiness;
};

export type SetupStep = {
  id: string;
  title: string;
  detail: string;
  status: SetupCheck['status'];
  action: string;
  targetScreen?: Screen;
  command?: 'planReminders' | 'testAiProvider';
};

export type SetupWizardPlan = {
  goal: SetupGoal;
  summary: string;
  readyCount: number;
  totalCount: number;
  recommendedStep?: SetupStep;
  steps: SetupStep[];
};

export const setupGoals: SetupGoal[] = ['Reminders only', 'AI drafts', 'Manual sends', 'Automation'];

const statusRank: Record<SetupCheck['status'], number> = {
  'Needs action': 0,
  Optional: 1,
  Ready: 2
};

const step = (
  id: string,
  title: string,
  detail: string,
  status: SetupCheck['status'],
  action: string,
  options: Pick<SetupStep, 'targetScreen' | 'command'> = {}
): SetupStep => ({
  id,
  title,
  detail,
  status,
  action,
  ...options
});

const hasAnyRecipientRoute = (state: AppState) =>
  state.contacts.some(contact => {
    const preferences = resolveContactPreferencesForContact(state.settings, contact);
    return Boolean(contact.phone || contact.email || preferences.preferredChannel === 'Manual');
  });

const aiEndpointReadinessFor = (env: SetupEnvironment) =>
  env.aiEndpointReadiness ?? providerEndpointReadinessFromConfigured(env.aiEndpointConfigured);

const emailEndpointReadinessFor = (env: SetupEnvironment) =>
  env.emailEndpointReadiness ?? providerEndpointReadinessFromConfigured(env.emailEndpointConfigured);

const hasEmailProviderIntent = (state: AppState, endpointReadiness: ProviderEndpointReadiness) =>
  state.settings.emailEnabled ||
  endpointReadiness.configured ||
  state.emailDelivery.status !== 'Not configured' ||
  Boolean(state.emailDelivery.senderEmail);

const buildReminderSteps = (state: AppState): SetupStep[] => {
  const schedulingPolicy = buildSchedulingPolicySummary(state);
  const schedulingBlockers = schedulingPolicy.issues.filter(issue => issue.severity === 'Error');
  return [
    step(
      'notifications',
      'Notifications',
      state.settings.notificationsEnabled
        ? 'Reminder notifications are enabled.'
        : 'Enable notifications so reminders can reach you outside the app.',
      state.settings.notificationsEnabled ? 'Ready' : 'Needs action',
      state.settings.notificationsEnabled ? 'Configured' : 'Enable notifications',
      { targetScreen: 'more' }
    ),
    step(
      'scheduling-policy',
      'Quiet hours and blackouts',
      schedulingBlockers.length > 0
        ? schedulingBlockers.map(issue => `${issue.title}: ${issue.detail}`).join(' ')
        : `Reminder planning respects ${state.settings.quietHours.start}-${state.settings.quietHours.end} quiet hours and ${state.settings.blackouts.length} blackout window(s).`,
      schedulingBlockers.length > 0 ? 'Needs action' : 'Ready',
      'Review scheduling',
      { targetScreen: 'more' }
    ),
    step(
      'events',
      'Relationship events',
      state.events.length > 0
        ? `${state.events.length} event(s) are available for reminders.`
        : 'Add or import at least one birthday, anniversary, or custom event.',
      state.events.length > 0 ? 'Ready' : 'Needs action',
      state.events.length > 0 ? 'Review events' : 'Add event',
      { targetScreen: state.events.length > 0 ? 'events' : 'eventForm' }
    ),
    step(
      'reminder-plans',
      'Reminder plan',
      state.reminderPlans.length > 0
        ? `${state.reminderPlans.length} reminder plan(s) are ready.`
        : 'Plan reminders after events and notification preferences are ready.',
      state.reminderPlans.length > 0 ? 'Ready' : 'Needs action',
      state.reminderPlans.length > 0 ? 'Review reminders' : 'Plan reminders',
      state.reminderPlans.length > 0 ? { targetScreen: 'more' } : { command: 'planReminders' }
    )
  ];
};

const buildAiSteps = (state: AppState, env: SetupEnvironment): SetupStep[] => {
  const endpointReadiness = aiEndpointReadinessFor(env);
  const aiProviderDetail = !endpointReadiness.configured
    ? 'Configure a secure backend endpoint before relying on provider drafts.'
    : endpointReadiness.productionReady
      ? `Provider endpoint is configured. Current status: ${state.aiProvider.status}.`
      : endpointReadiness.status === 'Development only'
        ? 'Local provider endpoint is allowed for development only. Use HTTPS before release.'
        : 'Provider endpoint is configured but not safe for production. Use HTTPS without credentials, localhost, or private-network hosts.';
  const aiProviderReady = endpointReadiness.productionReady && state.aiProvider.status !== 'Error';

  return [
    step(
      'ai-toggle',
      'AI drafting',
      state.settings.aiEnabled
        ? 'AI drafting is enabled, with review-first fallback rules.'
        : 'Turn on AI drafting or use local templates instead.',
      state.settings.aiEnabled ? 'Ready' : 'Needs action',
      state.settings.aiEnabled ? 'Enabled' : 'Enable AI',
      { targetScreen: 'more' }
    ),
    step(
      'ai-provider',
      'AI provider endpoint',
      aiProviderDetail,
      aiProviderReady ? 'Ready' : 'Needs action',
      endpointReadiness.canUseProviderEndpoint ? 'Test AI provider' : 'Configure endpoint',
      endpointReadiness.canUseProviderEndpoint ? { command: 'testAiProvider' } : { targetScreen: 'more' }
    ),
    step(
      'personalization',
      'Personalization context',
      state.memories.some(memory => memory.category !== 'Private')
        ? 'At least one non-private memory can improve draft specificity.'
        : 'Add non-private memories or notes so drafts do not feel generic.',
      state.memories.some(memory => memory.category !== 'Private') ? 'Ready' : 'Optional',
      'Review contacts',
      { targetScreen: 'contacts' }
    )
  ];
};

const buildManualSendSteps = (state: AppState, env: SetupEnvironment): SetupStep[] => {
  const endpointReadiness = emailEndpointReadinessFor(env);
  const emailDetail = !endpointReadiness.configured
    ? 'Email handoff is available; provider delivery stays optional until you configure an endpoint.'
    : endpointReadiness.productionReady
      ? 'Email provider endpoint is configured for optional provider delivery.'
      : endpointReadiness.status === 'Development only'
        ? 'Configured email endpoint is local-development only. Use HTTPS before release.'
        : 'Configured email endpoint is not safe for production. Use HTTPS without credentials, localhost, or private-network hosts, or use handoff fallback.';
  const emailStatus: SetupCheck['status'] =
    endpointReadiness.configured && !endpointReadiness.productionReady
      ? 'Needs action'
      : endpointReadiness.productionReady
        ? 'Ready'
        : 'Optional';
  const steps = [
    step(
      'contacts',
      'Recipient details',
      hasAnyRecipientRoute(state)
        ? 'Contacts have at least one usable manual route.'
        : 'Add phone, email, or manual route details before sending.',
      hasAnyRecipientRoute(state) ? 'Ready' : 'Needs action',
      'Review contacts',
      { targetScreen: 'contacts' }
    ),
    step(
      'manual-whatsapp',
      'Manual WhatsApp handoff',
      state.settings.whatsappHandoffEnabled
        ? 'Manual WhatsApp handoff is enabled and remains user-controlled.'
        : 'Enable manual WhatsApp handoff if you want WhatsApp routing.',
      state.settings.whatsappHandoffEnabled ? 'Ready' : 'Optional',
      'Review channel settings',
      { targetScreen: 'more' }
    )
  ];

  if (hasEmailProviderIntent(state, endpointReadiness)) {
    steps.push(
      step('email-route', 'Email route', emailDetail, emailStatus, 'Review email settings', { targetScreen: 'more' })
    );
  }

  return steps;
};

const buildAutomationSteps = (state: AppState, env: SetupEnvironment): SetupStep[] => [
  ...buildReminderSteps(state),
  ...buildAiSteps(state, env),
  ...buildManualSendSteps(state, env),
  step(
    'automation-mode',
    'Review workflow',
    state.settings.automationMode === 'Fully auto'
      ? productAvailability.durableUnattendedAutomation.reason
      : state.settings.automationMode === 'Always ask'
        ? 'Every draft and send remains user-reviewed.'
        : `${state.settings.automationMode} changes review prioritization only; it does not send messages unattended.`,
    state.settings.automationMode === 'Fully auto'
      ? 'Needs action'
      : state.settings.automationMode === 'Always ask'
        ? 'Optional'
        : 'Ready',
    state.settings.automationMode === 'Fully auto' ? 'Use a review mode' : 'Review workflow settings',
    { targetScreen: 'more' }
  )
];

const uniqueSteps = (steps: SetupStep[]) => {
  const seen = new Set<string>();
  return steps.filter(item => {
    if (seen.has(item.id)) {
      return false;
    }
    seen.add(item.id);
    return true;
  });
};

export const buildSetupWizardPlan = (state: AppState, env: SetupEnvironment, goal: SetupGoal): SetupWizardPlan => {
  const steps =
    goal === 'Reminders only'
      ? buildReminderSteps(state)
      : goal === 'AI drafts'
        ? buildAiSteps(state, env)
        : goal === 'Manual sends'
          ? buildManualSendSteps(state, env)
          : buildAutomationSteps(state, env);
  const ordered = uniqueSteps(steps).sort((a, b) => statusRank[a.status] - statusRank[b.status]);
  const readyCount = ordered.filter(item => item.status === 'Ready').length;
  const recommendedStep =
    ordered.find(item => item.status === 'Needs action') ?? ordered.find(item => item.status === 'Optional');
  const goalLabel = goal === 'Automation' ? 'Review workflow' : goal;

  return {
    goal,
    summary:
      readyCount === ordered.length
        ? `${goalLabel} setup is ready.`
        : `${readyCount}/${ordered.length} setup step(s) ready for ${goalLabel.toLowerCase()}.`,
    readyCount,
    totalCount: ordered.length,
    recommendedStep,
    steps: ordered
  };
};
