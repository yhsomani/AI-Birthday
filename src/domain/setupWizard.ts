import type { AppState, Screen, SetupCheck } from './types';

export type SetupGoal = 'Reminders only' | 'AI drafts' | 'Manual sends' | 'Automation';

export type SetupEnvironment = {
  aiEndpointConfigured: boolean;
  emailEndpointConfigured: boolean;
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
  state.contacts.some(contact => Boolean(contact.phone || contact.email || contact.preferredChannel === 'Manual'));

const buildReminderSteps = (state: AppState): SetupStep[] => [
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

const buildAiSteps = (state: AppState, env: SetupEnvironment): SetupStep[] => [
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
    env.aiEndpointConfigured
      ? `Provider endpoint is configured. Current status: ${state.aiProvider.status}.`
      : 'Configure a secure backend endpoint before relying on provider drafts.',
    env.aiEndpointConfigured ? (state.aiProvider.status === 'Error' ? 'Needs action' : 'Ready') : 'Needs action',
    env.aiEndpointConfigured ? 'Test AI provider' : 'Configure endpoint',
    env.aiEndpointConfigured ? { command: 'testAiProvider' } : { targetScreen: 'more' }
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

const buildManualSendSteps = (state: AppState, env: SetupEnvironment): SetupStep[] => [
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
  ),
  step(
    'email-route',
    'Email route',
    state.settings.emailEnabled
      ? env.emailEndpointConfigured
        ? 'Email is enabled and provider endpoint is configured.'
        : 'Email is enabled; configure provider endpoint or use handoff fallback.'
      : 'Email can stay off unless you need provider delivery.',
    state.settings.emailEnabled && !env.emailEndpointConfigured ? 'Needs action' : 'Optional',
    'Review email settings',
    { targetScreen: 'more' }
  )
];

const buildAutomationSteps = (state: AppState, env: SetupEnvironment): SetupStep[] => [
  ...buildReminderSteps(state),
  ...buildAiSteps(state, env),
  ...buildManualSendSteps(state, env),
  step(
    'automation-mode',
    'Automation mode',
    state.settings.automationMode === 'Always ask'
      ? 'Always ask is safest while setup is incomplete.'
      : `${state.settings.automationMode} is selected. Review queued messages before trusting automation.`,
    state.settings.automationMode === 'Always ask' ? 'Optional' : 'Ready',
    'Review automation settings',
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

export const buildSetupWizardPlan = (
  state: AppState,
  env: SetupEnvironment,
  goal: SetupGoal
): SetupWizardPlan => {
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
  const recommendedStep = ordered.find(item => item.status === 'Needs action') ?? ordered.find(item => item.status === 'Optional');

  return {
    goal,
    summary:
      readyCount === ordered.length
        ? `${goal} setup is ready.`
        : `${readyCount}/${ordered.length} setup step(s) ready for ${goal.toLowerCase()}.`,
    readyCount,
    totalCount: ordered.length,
    recommendedStep,
    steps: ordered
  };
};
