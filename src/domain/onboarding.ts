import { productAvailability } from '../config/productAvailability';
import { resolveContactPreferencesForContact } from './contactPreferences';
import type { AppState, OnboardingGoal, OnboardingStepId, Screen } from './types';

export const onboardingGoals: OnboardingGoal[] = [
  'Reminders first',
  'AI wishes',
  'Manual relationship manager',
  'Full setup'
];

export interface OnboardingStep {
  id: OnboardingStepId;
  title: string;
  purpose: string;
  status: 'Ready' | 'Needs action' | 'Optional';
  actionLabel: string;
  targetScreen?: Screen;
}

export interface OnboardingPlan {
  completed: boolean;
  currentStepId: OnboardingStepId;
  progress: number;
  nextStep: OnboardingStep;
  steps: OnboardingStep[];
  summary: string;
}

const stepOrder: OnboardingStepId[] = [
  'intro',
  'account',
  'contacts',
  'notifications',
  'ai',
  'style',
  'channels',
  'backup',
  'finish'
];

export const nextOnboardingStep = (current: OnboardingStepId): OnboardingStepId => {
  const index = stepOrder.indexOf(current);
  return stepOrder[Math.min(stepOrder.length - 1, Math.max(0, index) + 1)];
};

const hasAnyRecipientRoute = (state: AppState) =>
  state.contacts.some(contact => {
    const preferences = resolveContactPreferencesForContact(state.settings, contact);
    return Boolean(contact.phone || contact.email || preferences.preferredChannel === 'Manual');
  });

export const buildOnboardingPlan = (state: AppState): OnboardingPlan => {
  const notificationDecision = state.privacy.permissionDecisions.Notifications;
  const contactsDecision = state.privacy.permissionDecisions.Contacts;
  const steps: OnboardingStep[] = [
    {
      id: 'intro',
      title: 'Relationship assistant basics',
      purpose: 'Understand reminders, review-first messages, private memories, and user-controlled automation.',
      status: 'Ready',
      actionLabel: 'Continue'
    },
    {
      id: 'account',
      title: 'Account or local mode',
      purpose:
        state.settings.accountMode === 'Local'
          ? 'Local mode keeps relationship data on this device unless you explicitly use a provider or export.'
          : productAvailability.googleSync.reason,
      status: state.settings.accountMode === 'Local' ? 'Ready' : 'Needs action',
      actionLabel: state.settings.accountMode === 'Local' ? 'Local mode selected' : 'Use Local mode'
    },
    {
      id: 'contacts',
      title: 'Contacts',
      purpose:
        state.contacts.length > 0
          ? `${state.contacts.length} contact(s) are available. You can import more or continue manually.`
          : contactsDecision === 'Denied'
            ? 'Contacts permission was denied. Manual contact creation remains available.'
            : 'Add or import contacts after reviewing why contact access helps reminders.',
      status: state.contacts.length > 0 ? 'Ready' : contactsDecision === 'Denied' ? 'Optional' : 'Needs action',
      actionLabel: state.contacts.length > 0 ? 'Review contacts' : 'Add contacts',
      targetScreen: 'contacts'
    },
    {
      id: 'notifications',
      title: 'Notifications',
      purpose:
        notificationDecision === 'Granted'
          ? 'Notification reminders can bring you back to review surfaces.'
          : notificationDecision === 'Denied'
            ? 'Notifications were denied. Reminders still appear in the app.'
            : 'Review the reminder purpose before requesting notification permission.',
      status:
        notificationDecision === 'Granted' ? 'Ready' : notificationDecision === 'Denied' ? 'Optional' : 'Needs action',
      actionLabel: 'Review reminders',
      targetScreen: 'more'
    },
    {
      id: 'ai',
      title: 'AI drafting',
      purpose:
        state.settings.aiEnabled && state.aiProvider.status === 'Ready'
          ? 'AI drafting is ready and excludes private memories.'
          : state.settings.aiEnabled
            ? 'AI drafting can stay enabled, but provider setup can be completed later.'
            : 'Local templates are available while AI drafting is off.',
      status: state.settings.aiEnabled && state.aiProvider.status !== 'Ready' ? 'Optional' : 'Ready',
      actionLabel: 'Review AI setup',
      targetScreen: 'more'
    },
    {
      id: 'style',
      title: 'Style Coach',
      purpose:
        state.styleProfile.sampleCount > 0
          ? `${state.styleProfile.sampleCount} writing sample(s) shape draft tone.`
          : 'Style training is optional and can be done later with manual samples or sent messages.',
      status: state.styleProfile.sampleCount > 0 ? 'Ready' : 'Optional',
      actionLabel: 'Open Style Coach',
      targetScreen: 'more'
    },
    {
      id: 'channels',
      title: 'Delivery channels',
      purpose: hasAnyRecipientRoute(state)
        ? 'At least one manual delivery route is available. Provider delivery can be configured later.'
        : 'Add phone, email, or manual route details before relying on sends.',
      status: hasAnyRecipientRoute(state) ? 'Ready' : 'Needs action',
      actionLabel: 'Review channels',
      targetScreen: 'contacts'
    },
    {
      id: 'backup',
      title: 'Backup',
      purpose:
        state.backups.length > 0
          ? 'An encrypted backup snapshot exists. Export a fresh file before risky changes.'
          : 'Create an encrypted backup when this becomes your main relationship record.',
      status: state.backups.length > 0 ? 'Ready' : 'Optional',
      actionLabel: 'Open backup',
      targetScreen: 'more'
    },
    {
      id: 'finish',
      title: 'Start using RelateAI',
      purpose: 'Land on Home with setup gaps still available from Settings and Setup Check.',
      status: 'Ready',
      actionLabel: 'Go to Home',
      targetScreen: 'home'
    }
  ];

  const completed = new Set(state.onboarding.completedStepIds);
  const skipped = new Set(state.onboarding.skippedStepIds);
  const progress = Math.round(((completed.size + skipped.size) / stepOrder.length) * 100);
  const currentStep =
    steps.find(item => item.id === state.onboarding.currentStepId) ??
    steps.find(item => item.status === 'Needs action') ??
    steps[0];

  return {
    completed: state.onboarding.completed,
    currentStepId: currentStep.id,
    progress,
    nextStep: currentStep,
    steps,
    summary: state.onboarding.completed
      ? 'Onboarding is complete. Setup gaps remain available from Home, Settings, and Setup Check.'
      : `${progress}% complete. Current step: ${currentStep.title}.`
  };
};
