import { isAccountModeAvailable, productAvailability } from '../config/productAvailability';
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
  requiredForGoal: boolean;
  requirementSatisfied: boolean;
  canSkip: boolean;
  actionLabel: string;
  targetScreen?: Screen;
}

export interface OnboardingCompletionBlocker {
  stepId: OnboardingStepId;
  title: string;
  detail: string;
}

export interface OnboardingCompletionGate {
  canComplete: boolean;
  blockers: OnboardingCompletionBlocker[];
}

export interface OnboardingPlan {
  completed: boolean;
  currentStepId: OnboardingStepId;
  progress: number;
  nextStep: OnboardingStep;
  steps: OnboardingStep[];
  completionGate: OnboardingCompletionGate;
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

const requiredStepsByGoal: Record<OnboardingGoal, readonly OnboardingStepId[]> = {
  'Reminders first': ['account', 'contacts', 'notifications'],
  'AI wishes': ['account', 'contacts', 'ai'],
  'Manual relationship manager': ['account', 'contacts', 'channels'],
  'Full setup': ['account', 'contacts', 'notifications', 'ai', 'channels']
};

export const requiredOnboardingStepIds = (goal: OnboardingGoal): readonly OnboardingStepId[] =>
  requiredStepsByGoal[goal];

export const nextOnboardingStep = (current: OnboardingStepId): OnboardingStepId => {
  const index = stepOrder.indexOf(current);
  return stepOrder[Math.min(stepOrder.length - 1, Math.max(0, index) + 1)];
};

const hasAnyRecipientRoute = (state: AppState) =>
  state.contacts.some(contact => {
    if (contact.archivedAt) return false;
    const preferences = resolveContactPreferencesForContact(state.settings, contact);
    return Boolean(contact.phone || contact.email || preferences.preferredChannel === 'Manual');
  });

const notificationDecisionMade = (state: AppState) =>
  state.privacy.permissionDecisions.Notifications !== 'Not requested';

export const onboardingStepRequirementSatisfied = (state: AppState, stepId: OnboardingStepId) => {
  switch (stepId) {
    case 'account':
      return isAccountModeAvailable(state.settings.accountMode);
    case 'contacts':
      return state.contacts.some(contact => !contact.archivedAt);
    case 'notifications':
      return notificationDecisionMade(state);
    case 'ai':
      return state.settings.aiEnabled && state.aiProvider.status === 'Ready';
    case 'channels':
      return hasAnyRecipientRoute(state);
    default:
      return true;
  }
};

const requirementDetail = (state: AppState, stepId: OnboardingStepId) => {
  switch (stepId) {
    case 'account':
      return 'Confirm an available account or local mode before continuing.';
    case 'contacts':
      return 'Add at least one contact manually or through an explicit import before continuing.';
    case 'notifications':
      return 'Choose whether to allow notifications after reviewing the reminder rationale. Granting permission is optional.';
    case 'ai':
      return 'Enable AI drafting and complete a successful provider readiness test, or choose a goal that uses local templates.';
    case 'channels':
      return 'Add at least one usable phone, email, or manual recipient route before continuing.';
    default:
      return 'Complete this required onboarding choice before continuing.';
  }
};

const requiredForGoal = (state: AppState, stepId: OnboardingStepId) =>
  requiredOnboardingStepIds(state.onboarding.selectedGoal).includes(stepId);

const buildOnboardingSteps = (state: AppState): OnboardingStep[] => {
  const notificationDecision = state.privacy.permissionDecisions.Notifications;
  const contactsDecision = state.privacy.permissionDecisions.Contacts;
  const baseSteps: Omit<OnboardingStep, 'requiredForGoal' | 'requirementSatisfied' | 'canSkip'>[] = [
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
      purpose: state.contacts.some(contact => !contact.archivedAt)
        ? `${state.contacts.filter(contact => !contact.archivedAt).length} contact(s) are available. You can import more or continue manually.`
        : contactsDecision === 'Denied'
          ? 'Contacts permission was denied. Manual contact creation remains available.'
          : 'Add or import contacts after reviewing why contact access helps reminders.',
      status: state.contacts.some(contact => !contact.archivedAt)
        ? 'Ready'
        : contactsDecision === 'Denied'
          ? 'Optional'
          : 'Needs action',
      actionLabel: state.contacts.some(contact => !contact.archivedAt) ? 'Review contacts' : 'Add contacts',
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
      targetScreen: 'settings'
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
      targetScreen: 'settings'
    },
    {
      id: 'style',
      title: 'Style Coach',
      purpose:
        state.styleProfile.sampleCount > 0
          ? state.styleProfile.enabledForAiDrafts
            ? `${state.styleProfile.sampleCount} writing sample(s) shape future AI draft tone.`
            : `${state.styleProfile.sampleCount} writing sample(s) are learned; use in future AI drafts is disabled.`
          : 'Style training is optional and can be done later with manual samples or sent messages.',
      status: state.styleProfile.sampleCount > 0 ? 'Ready' : 'Optional',
      actionLabel: 'Open Style Coach',
      targetScreen: 'styleCoach'
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
      targetScreen: 'backup'
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

  return baseSteps.map(step => {
    const required = requiredForGoal(state, step.id);
    const satisfied = onboardingStepRequirementSatisfied(state, step.id);
    return {
      ...step,
      status: required && !satisfied ? 'Needs action' : step.status,
      requiredForGoal: required,
      requirementSatisfied: satisfied,
      canSkip: step.id !== 'finish' && !required
    };
  });
};

export const buildOnboardingCompletionGate = (state: AppState): OnboardingCompletionGate => {
  const steps = buildOnboardingSteps(state);
  const completed = new Set(state.onboarding.completedStepIds);
  const blockers = steps
    .filter(step => step.requiredForGoal && (!completed.has(step.id) || !step.requirementSatisfied))
    .map(step => ({
      stepId: step.id,
      title: step.title,
      detail: !step.requirementSatisfied
        ? requirementDetail(state, step.id)
        : `Confirm ${step.title.toLowerCase()} with Continue before finishing onboarding.`
    }));

  if (state.onboarding.currentStepId !== 'finish') {
    blockers.push({
      stepId: state.onboarding.currentStepId,
      title: 'Onboarding flow',
      detail: 'Continue or skip each optional step until the finish step before completing onboarding.'
    });
  }

  return { canComplete: blockers.length === 0, blockers };
};

export type OnboardingTransition =
  | { type: 'set-step'; stepId: OnboardingStepId }
  | { type: 'advance' }
  | { type: 'skip'; stepId: OnboardingStepId }
  | { type: 'complete' };

export const onboardingTransitionIssue = (state: AppState, transition: OnboardingTransition): string | undefined => {
  const currentStepId = state.onboarding.currentStepId;
  const steps = buildOnboardingSteps(state);
  const current = steps.find(step => step.id === currentStepId);

  if (transition.type === 'complete' || (transition.type === 'advance' && currentStepId === 'finish')) {
    const gate = buildOnboardingCompletionGate(state);
    return gate.canComplete ? undefined : gate.blockers.map(blocker => blocker.detail).join(' ');
  }

  if (transition.type === 'skip') {
    if (transition.stepId !== currentStepId) {
      return 'Only the current onboarding step can be skipped.';
    }
    if (!current?.canSkip) {
      return `${current?.title ?? 'This step'} is required for ${state.onboarding.selectedGoal.toLowerCase()} and cannot be skipped.`;
    }
    return undefined;
  }

  if (current?.requiredForGoal && !current.requirementSatisfied) {
    return requirementDetail(state, current.id);
  }

  if (transition.type === 'set-step') {
    if (transition.stepId === currentStepId) return undefined;
    const targetIndex = stepOrder.indexOf(transition.stepId);
    const currentIndex = stepOrder.indexOf(currentStepId);
    const alreadyVisited =
      state.onboarding.completedStepIds.includes(transition.stepId) ||
      state.onboarding.skippedStepIds.includes(transition.stepId);
    const currentWasVisited =
      state.onboarding.completedStepIds.includes(currentStepId) ||
      state.onboarding.skippedStepIds.includes(currentStepId);
    const isRecordedNextStep = targetIndex === currentIndex + 1 && currentWasVisited;
    if (targetIndex > currentIndex && !alreadyVisited && !isRecordedNextStep) {
      return 'Use Continue or Skip for now so onboarding progress is preserved before moving forward.';
    }
  }

  return undefined;
};

export const buildOnboardingPlan = (state: AppState): OnboardingPlan => {
  const steps = buildOnboardingSteps(state);

  const completed = new Set(state.onboarding.completedStepIds);
  const skipped = new Set(state.onboarding.skippedStepIds);
  const visited = new Set([...completed, ...skipped]);
  const progress = Math.round((visited.size / stepOrder.length) * 100);
  const completionGate = buildOnboardingCompletionGate(state);
  const firstBlockingStep = completionGate.blockers
    .map(blocker => steps.find(step => step.id === blocker.stepId))
    .find((step): step is OnboardingStep => Boolean(step));
  const currentStep =
    steps.find(item => item.id === state.onboarding.currentStepId) ??
    steps.find(item => item.status === 'Needs action') ??
    steps[0];

  return {
    completed: state.onboarding.completed,
    currentStepId: currentStep.id,
    progress,
    nextStep: state.onboarding.currentStepId === 'finish' && firstBlockingStep ? firstBlockingStep : currentStep,
    steps,
    completionGate,
    summary: state.onboarding.completed
      ? 'Onboarding is complete. Setup gaps remain available from Home, Settings, and Setup Check.'
      : state.onboarding.currentStepId === 'finish' && !completionGate.canComplete
        ? `Required setup remains: ${completionGate.blockers.map(blocker => blocker.title).join(', ')}.`
        : `${progress}% complete. Current step: ${currentStep.title}.`
  };
};
