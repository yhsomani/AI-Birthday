import { createProductionInitialState } from '../data/productionState';
import {
  isAccountModeAvailable,
  isAutomationModeAvailable,
  productAvailability
} from '../config/productAvailability';
import type { AiDraftContextOptions, AiDraftError, AiDraftVariants } from '../domain/aiDrafting';
import { calendarCandidatesToEvents } from '../domain/calendarSync';
import {
  applyResolvedPreferencesToContact,
  normalizeRelationshipGroupDefaults,
  resolveContactPreferencesForContact
} from '../domain/contactPreferences';
import { validateContactEssentials, type ContactEssentialsInput } from '../domain/contactEssentials';
import {
  buildEnrichmentMemoryBody,
  resolveContactEnrichmentPrompt,
  validateEnrichmentAnswer,
  type ContactEnrichmentPromptId
} from '../domain/contactEnrichment';
import { importContacts } from '../domain/contactImport';
import { buildEmailDeliveryRequest, type EmailDeliveryError } from '../domain/emailDelivery';
import { detectDuplicateMessageRisk } from '../domain/duplicateGuard';
import { toggleEventPreparationChecklistStep, type EventPreparationStepId } from '../domain/eventPreparation';
import { buildDefaultEventChecklist, validateManualEventInput } from '../domain/events';
import { buildMessageFollowUpPlan, type FollowUpDelayDays } from '../domain/followUps';
import { validateGiftBudgetInput, validateGiftInput } from '../domain/giftAdvisor';
import { buildMessageApprovalWindow, messageApprovalWindowIssue } from '../domain/messageApproval';
import { validateMessageBodyForChannel } from '../domain/messageBodyPolicy';
import {
  buildMessageBulkActionReport,
  findNextReviewMessageId,
  messageApprovalRouteIssue,
  type MessageBulkAction
} from '../domain/messageInbox';
import { buildMessageTestPlan } from '../domain/messageTesting';
import { buildTemplateDraft } from '../domain/messageTemplates';
import { validateMemoryNoteInput } from '../domain/memoryVault';
import { nextOnboardingStep } from '../domain/onboarding';
import { recurrenceFromDate } from '../domain/occasionDates';
import { buildReminderPlanningResult } from '../domain/reminders';
import { checkInCadenceOptions, relationshipGroupOptions } from '../domain/relationshipHealth';
import {
  adjustTriggerForSchedulingPolicy,
  automationModes,
  validateBlackoutInput,
  validateQuietHours
} from '../domain/schedulingPolicy';
import { analyzeManualStyleSamples, analyzeSentMessageStyle } from '../domain/styleCoach';
import type {
  ActivityItem,
  AppState,
  AccountMode,
  AutomationMode,
  AiProviderObservation,
  CalendarImportCandidate,
  ComposerReason,
  Contact,
  ContactGroupDefaults,
  EventType,
  GiftCategory,
  GiftRecord,
  ImportedContactRecord,
  MemoryCategory,
  MessageChannel,
  MessageDraft,
  MessageRegenerationFeedback,
  OnboardingGoal,
  OnboardingStepId,
  PermissionAuthorizationRecord,
  PermissionCapability,
  PermissionDecision,
  PersistenceStorageHealth,
  RelationshipGroup,
  ReminderPlan,
  Screen,
  SupportedLocale,
  SystemPermissionCapability,
  Tone
} from '../domain/types';

export type RelateAction =
  | { type: 'hydrate'; state: AppState }
  | { type: 'navigate'; screen: Screen; contactId?: string; messageId?: string }
  | { type: 'setSearch'; query: string }
  | { type: 'toggleChecklist'; eventId: string; itemId: string }
  | { type: 'togglePreparationStep'; eventId: string; stepId: EventPreparationStepId }
  | {
      type: 'addManualEvent';
      contactId?: string;
      newContactName?: string;
      eventType: EventType;
      label: string;
      date: string;
      confirmConflict?: boolean;
    }
  | {
      type: 'selectVariant';
      messageId: string;
      variant: MessageDraft['selectedVariant'];
      discardEditedBody?: boolean;
    }
  | { type: 'editMessage'; messageId: string; body: string }
  | {
      type: 'generateMessage';
      contactId: string;
      eventId?: string;
      reason: ComposerReason;
      fallbackReason?: string;
      excludedMemoryIds?: string[];
      includePriorMessages?: boolean;
      feedback?: MessageRegenerationFeedback;
    }
  | { type: 'createTemplateDraft'; contactId: string; reason: ComposerReason; body: string; templateId?: string }
  | {
      type: 'createAiDraft';
      contactId: string;
      eventId?: string;
      reason: ComposerReason;
      variants: AiDraftVariants;
      privacySummary: string;
      observation?: AiProviderObservation;
      feedback?: MessageRegenerationFeedback;
    }
  | { type: 'aiProviderReady'; privacySummary: string; observation?: AiProviderObservation }
  | { type: 'aiProviderFailure'; error: AiDraftError; privacySummary?: string; observation?: AiProviderObservation }
  | { type: 'approveMessage'; messageId: string; nowIso?: string; reviewNext?: boolean }
  | { type: 'acknowledgeDuplicateRisk'; messageId: string }
  | { type: 'rejectMessage'; messageId: string; reviewNext?: boolean }
  | { type: 'revokeMessage'; messageId: string }
  | { type: 'bulkMessageAction'; action: MessageBulkAction; messageIds: string[] }
  | { type: 'testMessageRoute'; messageId: string }
  | { type: 'manualHandoff'; messageId: string; nowIso?: string }
  | { type: 'scheduleMessageFollowUp'; messageId: string; delayDays: FollowUpDelayDays; nowIso?: string }
  | { type: 'retryMessage'; messageId: string }
  | { type: 'addMemory'; contactId: string; category: MemoryCategory; body: string }
  | { type: 'editMemory'; memoryId: string; category: MemoryCategory; body: string }
  | { type: 'toggleMemoryPin'; memoryId: string }
  | { type: 'deleteMemory'; memoryId: string }
  | { type: 'answerEnrichmentPrompt'; contactId: string; promptId: ContactEnrichmentPromptId; body: string }
  | {
      type: 'addGift';
      contactId: string;
      name: string;
      category: GiftCategory;
      occasion: string;
      cost: number;
      feedback?: GiftRecord['feedback'];
      notes?: string;
    }
  | { type: 'deleteGift'; giftId: string }
  | { type: 'updateGiftBudget'; contactId: string; annualGiftBudget: number | string }
  | { type: 'updateContactTone'; contactId: string; tone: Tone }
  | { type: 'updateContactEssentials'; contactId: string; input: ContactEssentialsInput }
  | { type: 'setContactGroup'; contactId: string; group: RelationshipGroup }
  | { type: 'setRelationshipGroupDefault'; group: RelationshipGroup; defaults: Partial<ContactGroupDefaults> }
  | { type: 'toggleContactVip'; contactId: string }
  | { type: 'toggleContactDnd'; contactId: string }
  | { type: 'setCheckInCadence'; contactId: string; days: number }
  | { type: 'setContactChannel'; contactId: string; channel: MessageChannel }
  | { type: 'setContactAutomationMode'; contactId: string; mode: AutomationMode }
  | { type: 'useGroupDefaultsForContact'; contactId: string }
  | { type: 'snoozeCheckIn'; contactId: string; days: number; nowIso?: string }
  | { type: 'markContactedElsewhere'; contactId: string; nowIso?: string }
  | { type: 'setLocale'; locale: SupportedLocale }
  | { type: 'setEmailSender'; senderEmail: string }
  | { type: 'emailProviderReady' }
  | { type: 'emailProviderFailure'; error: EmailDeliveryError; messageId?: string }
  | { type: 'emailDeliveryAccepted'; messageId: string; idempotencyKey: string; deliveryId: string }
  | { type: 'emailDeliveryUnknown'; error: EmailDeliveryError; messageId: string; idempotencyKey: string }
  | { type: 'emailSent'; messageId: string; idempotencyKey?: string; deliveryId?: string }
  | { type: 'setOnboardingGoal'; goal: OnboardingGoal }
  | { type: 'setOnboardingStep'; stepId: OnboardingStepId }
  | { type: 'advanceOnboarding' }
  | { type: 'skipOnboardingStep'; stepId: OnboardingStepId }
  | { type: 'completeOnboarding' }
  | { type: 'reopenOnboarding' }
  | { type: 'setAccountMode'; mode: AccountMode }
  | { type: 'disconnectAccount' }
  | { type: 'recordPermissionDecision'; capability: PermissionCapability; decision: PermissionDecision }
  | {
      type: 'permissionsReconciled';
      records: Record<SystemPermissionCapability, PermissionAuthorizationRecord>;
      decisions: AppState['privacy']['permissionDecisions'];
    }
  | { type: 'toggleWhatsAppHandoffConsent' }
  | { type: 'clearLocalDataConfirmed' }
  | { type: 'toggleSetting'; key: keyof AppState['settings'] }
  | { type: 'setAutomationMode'; mode: AutomationMode }
  | { type: 'setQuietHours'; start: string; end: string }
  | { type: 'addBlackout'; label: string; startDate: string; endDate: string }
  | { type: 'removeBlackout'; blackoutId: string }
  | { type: 'importContacts'; records: ImportedContactRecord[] }
  | { type: 'planReminders' }
  | { type: 'reminderPlansReconciled'; plans: ReminderPlan[] }
  | { type: 'calendarImported'; candidates: CalendarImportCandidate[] }
  | { type: 'calendarExported'; count: number }
  | { type: 'calendarError'; message: string }
  | { type: 'persistenceSaving' }
  | { type: 'persistenceSaved'; savedAt: string; storageHealth?: PersistenceStorageHealth }
  | { type: 'persistenceError'; message: string }
  | { type: 'createBackup' }
  | { type: 'restoreBackup'; restoredState: AppState; recordCount: number }
  | { type: 'analyticsExported'; rowCount: number; format?: 'CSV report' | 'Summary' }
  | { type: 'setupDoctorDryRunRecorded'; detail: string }
  | { type: 'trainStyle' }
  | { type: 'trainStyleFromSamples'; samples: string }
  | { type: 'trainStyleFromSentMessages' };

export { createProductionInitialState } from '../data/productionState';

const nowIso = () => new Date().toISOString();

const addDaysIso = (days: number, fromIso?: string) => {
  const next = fromIso ? new Date(fromIso) : new Date();
  next.setDate(next.getDate() + days);
  return next.toISOString();
};

const addActivity = (
  state: AppState,
  type: ActivityItem['type'],
  title: string,
  detail: string,
  severity: ActivityItem['severity'] = 'Info',
  target?: Pick<ActivityItem, 'targetScreen' | 'contactId' | 'messageId' | 'actionLabel'>
): ActivityItem[] => [
  {
    id: `activity-${Date.now()}-${state.activity.length}`,
    type,
    title,
    detail,
    severity,
    createdAt: nowIso(),
    ...target
  },
  ...state.activity
];

const findContact = (state: AppState, contactId: string) =>
  state.contacts.find(contact => contact.id === contactId);

const findEvent = (state: AppState, eventId?: string) =>
  eventId ? state.events.find(event => event.id === eventId) : undefined;

const explicitPreferenceOverridesForContact = (
  state: AppState,
  contact: Contact
): Contact['preferenceOverrides'] => ({
  preferredChannel: contact.preferredChannel,
  tone: contact.tone,
  checkInCadenceDays: contact.checkInCadenceDays,
  automationMode: state.settings.automationMode
});

const updateContactPreferenceOverride = (
  state: AppState,
  contact: Contact,
  overrides: NonNullable<Contact['preferenceOverrides']>
): Contact => {
  const nextOverrides = {
    ...(contact.preferenceOverrides ?? explicitPreferenceOverridesForContact(state, contact)),
    ...overrides
  };
  return {
    ...contact,
    preferredChannel: nextOverrides.preferredChannel ?? contact.preferredChannel,
    tone: nextOverrides.tone ?? contact.tone,
    checkInCadenceDays: nextOverrides.checkInCadenceDays ?? contact.checkInCadenceDays,
    preferenceOverrides: nextOverrides
  };
};

const getAiContext = (state: AppState, contactId: string, options: AiDraftContextOptions = {}) => {
  const excludedMemoryIds = new Set(options.excludedMemoryIds ?? []);
  return state.memories
    .filter(note => note.contactId === contactId && note.category !== 'Private' && !excludedMemoryIds.has(note.id))
    .map(note => note.body)
    .slice(0, 3);
};

const buildDraftText = (
  contact: Contact,
  reason: ComposerReason,
  context: string[],
  styleLength: number,
  tonePreferences: Tone[] = contact.tone,
  feedback?: MessageRegenerationFeedback
) => {
  const contextLine = context.length > 0 ? ` I remembered: ${context[0]}` : '';
  const tone = tonePreferences.includes('Formal') || tonePreferences.includes('Respectful') ? 'thoughtful' : 'warm';
  const base =
    reason === 'Check-in'
      ? `Hi ${contact.name}, I was thinking of you and wanted to check in. Hope things are going well.${contextLine}`
      : reason === 'Thanks'
        ? `Hi ${contact.name}, thank you for being part of my life. I really appreciate you.${contextLine}`
        : reason === 'Congratulations'
          ? `Congratulations ${contact.name}! This is a lovely milestone, and I am genuinely happy for you.${contextLine}`
          : reason === 'Apology'
            ? `Hi ${contact.name}, I wanted to say sorry and acknowledge this properly. You matter to me.${contextLine}`
            : reason === 'Follow-up'
              ? `Hi ${contact.name}, just following up and hoping everything went smoothly.${contextLine}`
              : `Happy birthday ${contact.name}! Wishing you a ${tone} day and a year filled with good moments.${contextLine}`;

  const instructionText = [...(feedback?.instructions ?? []), feedback?.customInstruction ?? ''].join(' ').toLowerCase();
  const feedbackLine =
    instructionText.includes('less generic') ||
    instructionText.includes('more specific') ||
    instructionText.includes('more personal')
      ? ' I tried to make this feel more specific to you.'
      : '';
  const adjusted = `${base}${feedbackLine}`;
  const lengthCap = instructionText.includes('shorter') ? Math.min(styleLength, 120) : styleLength + 80;

  if (adjusted.length > lengthCap) {
    return adjusted.slice(0, Math.max(12, lengthCap - 3)).trimEnd() + '...';
  }
  return adjusted;
};

const buildMessageDraft = (
  state: AppState,
  contactId: string,
  eventId: string | undefined,
  reason: ComposerReason,
  options: {
    providerVariants?: AiDraftVariants;
    fallbackReason?: string;
    contextOptions?: AiDraftContextOptions;
    feedback?: MessageRegenerationFeedback;
  } = {}
): MessageDraft | undefined => {
  const contact = findContact(state, contactId);
  const event = findEvent(state, eventId);
  if (!contact) {
    return undefined;
  }

  const context = getAiContext(state, contactId, options.contextOptions);
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const standard =
    options.providerVariants?.standard ??
    buildDraftText(contact, reason, context, state.styleProfile.averageLength, preferences.tone, options.feedback);
  const short =
    options.providerVariants?.short ?? (standard.length > 88 ? `${standard.slice(0, 85).trimEnd()}...` : standard);
  const warm =
    options.providerVariants?.warm ??
    `${standard} ${contact.isVip ? 'You are important to me, and I hope this feels personal.' : 'Hope this brings a small smile.'}`;
  const quality = options.providerVariants
    ? 'AI draft'
    : options.fallbackReason
      ? 'Template fallback'
      : context.length > 0
        ? 'AI draft'
        : 'Needs more context';

  return {
    id: `msg-${Date.now()}-${state.messages.length}`,
    contactId,
    eventId,
    reason,
    status: 'Needs review',
    channel: preferences.preferredChannel,
    body: standard,
    variants: {
      short,
      standard,
      warm
    },
    selectedVariant: 'standard',
    scheduledFor: event?.date,
    quality,
    readiness: options.feedback
      ? 'Regenerated with feedback for review'
      : contact.dnd
        ? 'Blocked by do-not-disturb'
        : preferences.preferredChannel === 'Manual'
          ? 'Use manual handoff'
          : options.providerVariants
            ? 'Provider draft ready for review'
            : preferences.automationMode === 'Fully auto'
              ? 'Eligible for full automation after review safeguards'
              : preferences.automationMode === 'Smart approve'
                ? 'Smart approval eligible after review'
                : preferences.automationMode === 'VIP approve'
                  ? contact.isVip
                    ? 'VIP review required'
                    : 'Review required by group policy'
                  : 'Ready for review',
    regenerationFeedback: options.feedback,
    lastError: options.fallbackReason
  };
};

const updateMessage = (
  state: AppState,
  messageId: string,
  updater: (message: MessageDraft) => MessageDraft
) => state.messages.map(message => (message.id === messageId ? updater(message) : message));

const clearApprovalMetadata = (message: MessageDraft): MessageDraft => {
  const { approvedAt, approvalExpiresAt, ...withoutApproval } = message;
  return withoutApproval;
};

const DND_APPROVAL_WARNING =
  'Contact is in do-not-disturb. Disable DND or complete a deliberate manual handoff before scheduling.';

const approveMessageTransition = (
  message: MessageDraft,
  routeIssue?: string,
  approvedAtIso = nowIso()
): MessageDraft => {
  const bodyPolicy = validateMessageBodyForChannel(message);
  if (!bodyPolicy.ok) {
    return {
      ...clearApprovalMetadata(message),
      status: 'Blocked',
      readiness: bodyPolicy.message,
      lastError: bodyPolicy.message
    };
  }
  if (message.duplicateWarning && !message.duplicateAcknowledged) {
    return {
      ...clearApprovalMetadata(message),
      status: 'Blocked',
      readiness: 'Explicitly continue or edit before approval',
      lastError: message.duplicateWarning
    };
  }
  if (routeIssue) {
    const blockedByDnd = /do-not-disturb/i.test(routeIssue);
    return {
      ...clearApprovalMetadata(message),
      status: 'Blocked',
      readiness: blockedByDnd ? 'Blocked by contact do-not-disturb' : 'Fix delivery route before approval',
      lastError: blockedByDnd ? DND_APPROVAL_WARNING : routeIssue
    };
  }
  return {
    ...message,
    ...buildMessageApprovalWindow(approvedAtIso),
    status: 'Scheduled',
    readiness: bodyPolicy.warning ?? (message.channel === 'Manual' ? 'Ready for manual handoff' : 'Approved and scheduled'),
    lastError: undefined
  };
};

const rejectMessageTransition = (message: MessageDraft): MessageDraft => ({
  ...clearApprovalMetadata(message),
  status: 'Rejected',
  readiness: 'Rejected by user'
});

const retryMessageTransition = (message: MessageDraft): MessageDraft => ({
  ...clearApprovalMetadata(message),
  status: 'Needs review',
  readiness: 'Ready for review',
  lastError: undefined
});

const revokeMessageTransition = (message: MessageDraft): MessageDraft => ({
  ...clearApprovalMetadata(message),
  status: 'Needs review',
  readiness: 'Approval revoked; review before scheduling again',
  lastError: undefined
});

const CONTACT_UPDATE_REVIEW_WARNING =
  'Contact profile or preferences changed after this draft was created. Review before scheduling or sending.';

const markContactMessagesForReview = (
  messages: MessageDraft[],
  contactIds: string[],
  reason = CONTACT_UPDATE_REVIEW_WARNING
): { messages: MessageDraft[]; count: number } => {
  const affectedContactIds = new Set(contactIds);
  const reviewableStatuses: MessageDraft['status'][] = ['Needs review', 'Draft', 'Scheduled'];
  let count = 0;
  return {
    messages: messages.map(message => {
      if (!affectedContactIds.has(message.contactId) || !reviewableStatuses.includes(message.status)) {
        return message;
      }
      count += 1;
      return {
        ...clearApprovalMetadata(message),
        status: 'Needs review',
        readiness: 'Review after contact update',
        lastError: reason
      };
    }),
    count
  };
};

const settingDeliveryChannelMap: Partial<Record<keyof AppState['settings'], MessageChannel>> = {
  smsEnabled: 'SMS',
  whatsappHandoffEnabled: 'WhatsApp',
  emailEnabled: 'Email'
};

const channelDisabledReviewWarning = (channel: MessageChannel) =>
  `${channel} was disabled in Settings. Review before scheduling or sending.`;

const markScheduledChannelMessagesForReview = (
  messages: MessageDraft[],
  channel: MessageChannel
): { messages: MessageDraft[]; count: number } => {
  let count = 0;
  return {
    messages: messages.map(message => {
      if (message.channel !== channel || message.status !== 'Scheduled') {
        return message;
      }
      count += 1;
      return {
        ...clearApprovalMetadata(message),
        status: 'Needs review',
        readiness: 'Review after channel setting changed',
        lastError: channelDisabledReviewWarning(channel)
      };
    }),
    count
  };
};

const SCHEDULE_POLICY_REVIEW_WARNING =
  'Schedule settings changed after this message was approved. Review the timing before scheduling or sending.';

const markSchedulePolicyConflictsForReview = (
  messages: MessageDraft[],
  settings: AppState['settings']
): { messages: MessageDraft[]; count: number } => {
  let count = 0;
  return {
    messages: messages.map(message => {
      if (message.status !== 'Scheduled' || !message.scheduledFor) {
        return message;
      }
      const scheduledFor = new Date(message.scheduledFor);
      const adjustments = Number.isNaN(scheduledFor.getTime())
        ? ['Scheduled time is invalid.']
        : adjustTriggerForSchedulingPolicy(scheduledFor, settings).adjustments;
      if (adjustments.length === 0) {
        return message;
      }
      count += 1;
      return {
        ...clearApprovalMetadata(message),
        status: 'Needs review',
        readiness: 'Review after schedule settings changed',
        lastError: `${SCHEDULE_POLICY_REVIEW_WARNING} ${adjustments[0]}`
      };
    }),
    count
  };
};

const AUTOMATION_MODE_REVIEW_WARNING =
  'Automation mode changed after this message was approved. Review before scheduling or sending.';

const markScheduledAutomationMessagesForReview = (
  messages: MessageDraft[]
): { messages: MessageDraft[]; count: number } => {
  let count = 0;
  return {
    messages: messages.map(message => {
      if (message.status !== 'Scheduled') {
        return message;
      }
      count += 1;
      return {
        ...clearApprovalMetadata(message),
        status: 'Needs review',
        readiness: 'Review after automation mode changed',
        lastError: AUTOMATION_MODE_REVIEW_WARNING
      };
    }),
    count
  };
};

const countAutomationModeContactImpacts = (state: AppState, nextMode: AutomationMode) => {
  const nextSettings = {
    ...state.settings,
    automationMode: nextMode
  };
  return state.contacts.filter(contact => {
    const current = resolveContactPreferencesForContact(state.settings, contact).automationMode;
    const next = resolveContactPreferencesForContact(nextSettings, contact).automationMode;
    return current !== next;
  }).length;
};

const AI_DISABLED_REVIEW_WARNING =
  'AI drafting was disabled in Settings. Review this AI-created message manually before scheduling or sending.';

const markAiDraftMessagesForReview = (messages: MessageDraft[]): { messages: MessageDraft[]; count: number } => {
  const reviewableStatuses: MessageDraft['status'][] = ['Needs review', 'Draft', 'Scheduled'];
  let count = 0;
  return {
    messages: messages.map(message => {
      if (message.quality !== 'AI draft' || !reviewableStatuses.includes(message.status)) {
        return message;
      }
      count += 1;
      return {
        ...clearApprovalMetadata(message),
        status: 'Needs review',
        readiness: 'Review manually after AI was disabled',
        lastError: AI_DISABLED_REVIEW_WARNING
      };
    }),
    count
  };
};

const applyBulkMessageTransition = (
  message: MessageDraft,
  action: MessageBulkAction,
  routeIssue?: string,
  approvedAtIso?: string
): MessageDraft => {
  switch (action) {
    case 'Approve':
      return approveMessageTransition(message, routeIssue, approvedAtIso);
    case 'Reject':
      return rejectMessageTransition(message);
    case 'Retry':
      return retryMessageTransition(message);
    case 'Revoke approval':
      return revokeMessageTransition(message);
  }
};

const reviewNextNavigation = (state: AppState, currentMessageId: string) => {
  const nextMessageId = findNextReviewMessageId(state, currentMessageId);
  const nextMessage = nextMessageId ? state.messages.find(message => message.id === nextMessageId) : undefined;
  return nextMessage
    ? {
        activeScreen: 'wishPreview' as const,
        selectedMessageId: nextMessage.id,
        selectedContactId: nextMessage.contactId
      }
    : {
        activeScreen: 'messages' as const,
        selectedMessageId: undefined,
        selectedContactId: undefined
      };
};

const emailDeliveryFailureTransition = (message: MessageDraft, error: EmailDeliveryError): MessageDraft =>
  message.status === 'Sent'
    ? message
    : {
        ...message,
        status: 'Failed',
        readiness: 'Email delivery failed; review recovery options',
        lastError: error.message
      };

const manualHandoffIssue = (state: AppState, message: MessageDraft, nowIsoValue = nowIso()) => {
  if (message.status !== 'Scheduled') {
    return 'Approve the message before manual handoff.';
  }
  const bodyPolicy = validateMessageBodyForChannel(message);
  if (!bodyPolicy.ok) {
    return bodyPolicy.message;
  }
  const approvalIssue = messageApprovalWindowIssue(message, nowIsoValue);
  if (approvalIssue) {
    return approvalIssue;
  }
  return messageApprovalRouteIssue(state, message);
};

const bulkActivityTitle = (action: MessageBulkAction, skippedCount: number) => {
  const suffix = skippedCount > 0 ? 'partially applied' : 'applied';
  return `Bulk ${action.toLowerCase()} ${suffix}`;
};

const normalizeLoadedState = (loadedState: AppState): AppState => {
  const defaults = createProductionInitialState();
  return {
    ...defaults,
    ...loadedState,
    settings: {
      ...defaults.settings,
      ...loadedState.settings,
      accountMode: isAccountModeAvailable(loadedState.settings?.accountMode)
        ? loadedState.settings.accountMode
        : 'Local',
      automationMode: isAutomationModeAvailable(loadedState.settings?.automationMode)
        ? loadedState.settings.automationMode
        : 'Always ask',
      quietHours: {
        ...defaults.settings.quietHours,
        ...loadedState.settings?.quietHours
      },
      groupDefaults: normalizeRelationshipGroupDefaults(loadedState.settings?.groupDefaults),
      blackouts: Array.isArray(loadedState.settings?.blackouts)
        ? loadedState.settings.blackouts
        : defaults.settings.blackouts
    },
    onboarding: {
      ...defaults.onboarding,
      ...loadedState.onboarding,
      completedStepIds: Array.isArray(loadedState.onboarding?.completedStepIds)
        ? loadedState.onboarding.completedStepIds
        : defaults.onboarding.completedStepIds,
      skippedStepIds: Array.isArray(loadedState.onboarding?.skippedStepIds)
        ? loadedState.onboarding.skippedStepIds
        : defaults.onboarding.skippedStepIds
    },
    privacy: {
      ...defaults.privacy,
      ...loadedState.privacy,
      permissionDecisions: {
        ...defaults.privacy.permissionDecisions,
        ...loadedState.privacy?.permissionDecisions
      }
    },
    aiProvider: {
      ...defaults.aiProvider,
      ...loadedState.aiProvider
    },
    emailDelivery: {
      ...defaults.emailDelivery,
      ...loadedState.emailDelivery
    },
    calendarSync: {
      ...defaults.calendarSync,
      ...loadedState.calendarSync
    },
    persistence: {
      ...loadedState.persistence,
      status: 'Ready'
    }
  };
};

const uniqueSteps = (steps: OnboardingStepId[]) => [...new Set(steps)];

const createClearedLocalState = (previousState: AppState): AppState => {
  const cleared = createProductionInitialState();
  const next: AppState = {
    ...cleared,
    activeScreen: 'onboarding',
    selectedContactId: undefined,
    selectedMessageId: undefined,
    contacts: [],
    events: [],
    memories: [],
    gifts: [],
    messages: [],
    reminderPlans: [],
    backups: [],
    settings: {
      ...cleared.settings,
      locale: previousState.settings.locale,
      accountMode: 'Local'
    },
    privacy: {
      ...cleared.privacy,
      localDataClearConfirmedAt: nowIso()
    },
    persistence: {
      status: 'Ready'
    }
  };
  return {
    ...next,
    activity: addActivity(next, 'Setup', 'Local data cleared', 'Contacts, events, messages, memories, gifts, and backups were cleared.')
  };
};

export const relateReducer = (state: AppState, action: RelateAction): AppState => {
  switch (action.type) {
    case 'hydrate':
      return normalizeLoadedState(action.state);
    case 'navigate':
      return {
        ...state,
        activeScreen: action.screen,
        selectedContactId: action.contactId ?? state.selectedContactId,
        selectedMessageId: action.messageId ?? state.selectedMessageId
      };
    case 'setSearch':
      return { ...state, searchQuery: action.query };
    case 'toggleChecklist':
      return {
        ...state,
        events: state.events.map(event =>
          event.id === action.eventId
            ? {
                ...event,
                checklist: event.checklist.map(item =>
                  item.id === action.itemId ? { ...item, done: !item.done } : item
                )
              }
            : event
        )
      };
    case 'togglePreparationStep':
      return {
        ...state,
        events: state.events.map(event =>
          event.id === action.eventId
            ? {
                ...event,
                checklist: toggleEventPreparationChecklistStep(event.type, event.checklist, action.stepId)
              }
            : event
        )
      };
    case 'addManualEvent': {
      const validation = validateManualEventInput(action, state.contacts, state.events);
      if (!validation.ok) {
        return {
          ...state,
          activeScreen: 'eventForm',
          activity: addActivity(
            state,
            'Event',
            'Event not saved',
            validation.errors.join(' '),
            'Warning'
          )
        };
      }

      if (validation.warnings.length > 0 && !action.confirmConflict) {
        return {
          ...state,
          activeScreen: 'eventForm',
          activity: addActivity(
            state,
            'Event',
            'Review event conflict',
            validation.warnings.join(' '),
            'Warning'
          )
        };
      }

      const timestamp = Date.now();
      const createdContact: Contact | undefined = validation.normalized.contactId
        ? undefined
        : {
            id: `contact-${timestamp}-${state.contacts.length}`,
            name: validation.normalized.newContactName ?? 'New contact',
            relationship: 'Other',
            group: 'Other',
            preferredChannel: 'Manual',
            language: 'English',
            tone: ['Warm'],
            healthScore: 40,
            isVip: false,
            dnd: false,
            checkInCadenceDays: 45,
            notesSummary: 'Added from manual event.',
            annualGiftBudget: 0
          };
      const contactId = validation.normalized.contactId ?? createdContact?.id;
      if (!contactId) {
        return {
          ...state,
          activeScreen: 'eventForm',
          activity: addActivity(state, 'Event', 'Event not saved', 'A contact is required.', 'Warning')
        };
      }

      const event = {
        id: `event-${timestamp}-${state.events.length}`,
        contactId,
        type: validation.normalized.eventType,
        label: validation.normalized.label,
        date: validation.normalized.dateIso,
        recurrence: recurrenceFromDate(validation.normalized.eventType, validation.normalized.dateIso),
        verified: true,
        source: 'Manual' as const,
        checklist: buildDefaultEventChecklist(validation.normalized.eventType)
      };

      return {
        ...state,
        activeScreen: 'events',
        selectedContactId: contactId,
        contacts: createdContact ? [createdContact, ...state.contacts] : state.contacts,
        events: [event, ...state.events],
        activity: addActivity(
          state,
          'Event',
          'Event saved',
          validation.warnings.length > 0
            ? `${event.label} was kept as a separate event after review.`
            : `${event.label} was added to Events.`
        )
      };
    }
    case 'selectVariant':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => {
          if (message.selectedVariant === action.variant) {
            return message;
          }
          const hasEditedBody = message.body !== message.variants[message.selectedVariant];
          if (hasEditedBody && !action.discardEditedBody) {
            return message;
          }
          return {
            ...message,
            selectedVariant: action.variant,
            body: message.variants[action.variant]
          };
        })
      };
    case 'editMessage':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => {
          const nextMessage = {
            ...message,
            body: action.body
          };
          const bodyPolicy = validateMessageBodyForChannel(nextMessage);
          return {
            ...nextMessage,
            readiness: !bodyPolicy.ok ? bodyPolicy.message : bodyPolicy.warning ?? message.readiness
          };
        })
      };
    case 'generateMessage': {
      const draft = buildMessageDraft(state, action.contactId, action.eventId, action.reason, {
        fallbackReason: action.fallbackReason,
        contextOptions: {
          excludedMemoryIds: action.excludedMemoryIds,
          includePriorMessages: action.includePriorMessages
        },
        feedback: action.feedback
      });
      if (!draft) {
        return {
          ...state,
          activity: addActivity(state, 'AI', 'Draft not created', 'The selected contact could not be found.', 'Error')
        };
      }
      const duplicateRisk = detectDuplicateMessageRisk(state, draft);
      const reviewedDraft = duplicateRisk.risk
        ? {
            ...draft,
            duplicateWarning: duplicateRisk.message,
            readiness: 'Duplicate risk needs review before approval'
          }
        : draft;
      return {
        ...state,
        messages: [reviewedDraft, ...state.messages],
        activeScreen: 'wishPreview',
        selectedMessageId: reviewedDraft.id,
        selectedContactId: reviewedDraft.contactId,
        activity: addActivity(
          state,
          'AI',
          action.fallbackReason ? 'Template fallback created' : 'Draft created',
          action.fallbackReason ??
            (action.feedback
              ? `${draft.reason} draft was regenerated with feedback and is ready for review.`
              : `${draft.reason} draft is ready for review.`),
          action.fallbackReason ? 'Warning' : 'Info'
        )
      };
    }
    case 'createTemplateDraft': {
      const result = buildTemplateDraft(state, action);
      if (!result.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Message', 'Template draft not created', result.reason, 'Warning')
        };
      }
      const duplicateRisk = detectDuplicateMessageRisk(state, result.draft);
      const reviewedDraft = duplicateRisk.risk
        ? {
            ...result.draft,
            duplicateWarning: duplicateRisk.message,
            readiness: 'Duplicate risk needs review before approval'
          }
        : result.draft;

      return {
        ...state,
        messages: [reviewedDraft, ...state.messages],
        activeScreen: 'wishPreview',
        selectedMessageId: reviewedDraft.id,
        selectedContactId: reviewedDraft.contactId,
        activity: addActivity(
          state,
          'Message',
          'Template draft created',
          `${reviewedDraft.reason} template is ready for review.`
        )
      };
    }
    case 'createAiDraft': {
      const draft = buildMessageDraft(state, action.contactId, action.eventId, action.reason, {
        providerVariants: action.variants,
        feedback: action.feedback
      });
      if (!draft) {
        return {
          ...state,
          activity: addActivity(state, 'AI', 'AI draft not created', 'The selected contact could not be found.', 'Error')
        };
      }
      const duplicateRisk = detectDuplicateMessageRisk(state, draft);
      const reviewedDraft = duplicateRisk.risk
        ? {
            ...draft,
            duplicateWarning: duplicateRisk.message,
            readiness: 'Duplicate risk needs review before approval'
          }
        : draft;
      return {
        ...state,
        messages: [reviewedDraft, ...state.messages],
        activeScreen: 'wishPreview',
        selectedMessageId: reviewedDraft.id,
        selectedContactId: reviewedDraft.contactId,
        aiProvider: {
          status: 'Ready',
          lastCheckedAt: nowIso(),
          lastPrivacySummary: action.privacySummary,
          lastObservation: action.observation
        },
        activity: addActivity(
          state,
          'AI',
          action.feedback ? 'AI draft regenerated' : 'AI draft created',
          action.feedback
            ? `${reviewedDraft.reason} provider draft used feedback guidance and is ready for review.`
            : `${reviewedDraft.reason} provider draft is ready for review.`
        )
      };
    }
    case 'aiProviderReady':
      return {
        ...state,
        aiProvider: {
          status: 'Ready',
          lastCheckedAt: nowIso(),
          lastPrivacySummary: action.privacySummary,
          lastObservation: action.observation
        },
        activity: addActivity(state, 'AI', 'AI provider ready', action.privacySummary)
      };
    case 'aiProviderFailure':
      return {
        ...state,
        aiProvider: {
          status: 'Error',
          lastCheckedAt: nowIso(),
          lastError: action.error.message,
          lastPrivacySummary: action.privacySummary,
          lastObservation: action.observation
        },
        activity: addActivity(state, 'AI', 'AI provider unavailable', action.error.message, 'Warning')
      };
    case 'approveMessage': {
      const message = state.messages.find(item => item.id === action.messageId);
      if (!message) {
        return {
          ...state,
          activity: addActivity(
            state,
            'Message',
            'Message not approved',
            'This message is no longer available.',
            'Warning'
          )
        };
      }
      const approvedMessage = approveMessageTransition(
        message,
        messageApprovalRouteIssue(state, message),
        action.nowIso ?? nowIso()
      );
      const nextMessages = updateMessage(state, action.messageId, () => approvedMessage);
      const nextState = {
        ...state,
        messages: nextMessages
      };
      return {
        ...nextState,
        ...(action.reviewNext && approvedMessage.status !== 'Blocked'
          ? reviewNextNavigation(nextState, action.messageId)
          : {}),
        activity: addActivity(
          state,
          'Message',
          approvedMessage.status === 'Blocked' ? 'Message approval blocked' : 'Message approved',
          approvedMessage.status === 'Blocked'
            ? (approvedMessage.lastError ?? approvedMessage.readiness)
            : 'The message is approved for scheduled or manual send.',
          approvedMessage.status === 'Blocked' ? 'Warning' : 'Info',
          {
            targetScreen: 'wishPreview',
            messageId: approvedMessage.id,
            contactId: approvedMessage.contactId,
            actionLabel: approvedMessage.status === 'Blocked' ? 'Review blocker' : 'Open approved message'
          }
        )
      };
    }
    case 'acknowledgeDuplicateRisk':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          duplicateAcknowledged: true,
          status: message.status === 'Blocked' ? 'Needs review' : message.status,
          readiness: 'Duplicate risk acknowledged; review once more before approval',
          lastError: message.lastError === message.duplicateWarning ? undefined : message.lastError
        })),
        activity: addActivity(
          state,
          'Message',
          'Duplicate risk acknowledged',
          'The user explicitly chose to continue after reviewing the duplicate warning.',
          'Warning',
          {
            targetScreen: 'wishPreview',
            messageId: action.messageId,
            actionLabel: 'Review duplicate'
          }
        )
      };
    case 'rejectMessage': {
      const nextState = {
        ...state,
        messages: updateMessage(state, action.messageId, rejectMessageTransition)
      };
      return {
        ...nextState,
        ...(action.reviewNext ? reviewNextNavigation(nextState, action.messageId) : {}),
        activity: addActivity(state, 'Message', 'Message rejected', 'The draft will not be sent.', 'Info', {
          targetScreen: 'messages',
          messageId: action.messageId,
          actionLabel: 'Open messages'
        })
      };
    }
    case 'revokeMessage':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, revokeMessageTransition),
        activity: addActivity(state, 'Message', 'Message approval revoked', 'Review the message before scheduling again.', 'Info', {
          targetScreen: 'wishPreview',
          messageId: action.messageId,
          actionLabel: 'Review message'
        })
      };
    case 'bulkMessageAction': {
      const report = buildMessageBulkActionReport(state, action.messageIds, action.action);
      if (report.eligibleIds.length === 0) {
        return {
          ...state,
          activity: addActivity(
            state,
            'Message',
            bulkActivityTitle(action.action, report.skipped.length),
            report.confirmation,
            'Warning'
          )
        };
      }

      const eligibleIds = new Set(report.eligibleIds);
      const approvedAtIso = nowIso();
      return {
        ...state,
        messages: state.messages.map(message =>
          eligibleIds.has(message.id)
            ? applyBulkMessageTransition(message, action.action, messageApprovalRouteIssue(state, message), approvedAtIso)
            : message
        ),
        activity: addActivity(
          state,
          'Message',
          bulkActivityTitle(action.action, report.skipped.length),
          report.confirmation,
          report.skipped.length > 0 ? 'Warning' : 'Info'
        )
      };
    }
    case 'testMessageRoute': {
      const message = state.messages.find(item => item.id === action.messageId);
      if (!message) {
        return {
          ...state,
          activity: addActivity(
            state,
            'Message',
            'Test send blocked',
            'The selected message could not be found.',
            'Warning'
          )
        };
      }
      const plan = buildMessageTestPlan(state, message);
      return {
        ...state,
        messages: state.messages.map(item =>
          item.id === action.messageId
            ? {
                ...item,
                readiness: plan.ok ? plan.detail : 'Test send blocked',
                lastError: plan.ok ? undefined : plan.issue
              }
            : item
        ),
        activity: addActivity(
          state,
          'Message',
          plan.title,
          plan.ok ? plan.detail : plan.issue,
          plan.ok ? 'Info' : 'Warning'
        )
      };
    }
    case 'manualHandoff': {
      const message = state.messages.find(item => item.id === action.messageId);
      if (!message) {
        return {
          ...state,
          activity: addActivity(
            state,
            'Message',
            'Manual handoff not completed',
            'This message is no longer available.',
            'Warning'
          )
        };
      }
      const completedAt = action.nowIso ?? nowIso();
      const issue = manualHandoffIssue(state, message, completedAt);
      if (issue) {
        return {
          ...state,
        activity: addActivity(state, 'Message', 'Manual handoff not completed', issue, 'Warning', {
          targetScreen: 'wishPreview',
          messageId: action.messageId,
          contactId: message.contactId,
          actionLabel: 'Review handoff'
        })
      };
      }
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          status: 'Sent',
          readiness: 'Marked sent after manual handoff',
          sentAt: completedAt
        })),
        contacts: state.contacts.map(contact => {
          return message?.contactId === contact.id ? { ...contact, lastContactedAt: completedAt, healthScore: Math.min(100, contact.healthScore + 5) } : contact;
        }),
        activity: addActivity(state, 'Message', 'Manual handoff completed', 'The user retained final control in the destination app.', 'Info', {
          targetScreen: 'chatHistory',
          messageId: action.messageId,
          contactId: message.contactId,
          actionLabel: 'Open chat history'
        })
      };
    }
    case 'scheduleMessageFollowUp': {
      const plan = buildMessageFollowUpPlan(state, action.messageId, action.delayDays, action.nowIso);
      if (!plan.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Event', 'Follow-up not scheduled', plan.reason, 'Warning')
        };
      }

      return {
        ...state,
        activeScreen: 'events',
        selectedContactId: plan.event.contactId,
        events: [plan.event, ...state.events],
        reminderPlans: [plan.reminderPlan, ...state.reminderPlans],
        activity: addActivity(
          state,
          'Event',
          'Follow-up scheduled',
          `${plan.event.label} is ready in Events and reminders.`
        )
      };
    }
    case 'retryMessage': {
      const retryTarget = state.messages.find(message => message.id === action.messageId);
      if (
        !retryTarget ||
        (retryTarget.status !== 'Failed' && retryTarget.status !== 'Blocked') ||
        retryTarget.emailDeliveryAttempt?.status === 'Unknown'
      ) {
        return {
          ...state,
          activity: addActivity(
            state,
            'Message',
            'Message retry blocked',
            retryTarget?.status === 'Delivery unknown'
              ? 'Reconcile the existing provider attempt before retrying.'
              : 'Only failed or blocked messages can be prepared for retry.',
            'Warning',
            {
              targetScreen: 'wishPreview',
              messageId: action.messageId,
              actionLabel: 'Review message'
            }
          )
        };
      }
      return {
        ...state,
        messages: updateMessage(state, action.messageId, retryMessageTransition),
        activity: addActivity(state, 'Message', 'Message retry prepared', 'Review the message before retrying.', 'Info', {
          targetScreen: 'wishPreview',
          messageId: action.messageId,
          actionLabel: 'Review retry'
        })
      };
    }
    case 'addMemory': {
      const validation = validateMemoryNoteInput(state, action.contactId, action.body);
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Memory', 'Memory not saved', validation.message, 'Warning')
        };
      }
      return {
        ...state,
        memories: [
          {
            id: `memory-${Date.now()}-${state.memories.length}`,
            contactId: action.contactId,
            category: action.category,
            body: validation.value.body,
            pinned: action.category !== 'Private',
            createdAt: nowIso()
          },
          ...state.memories
        ],
        activity: addActivity(
          state,
          'Memory',
          'Memory saved',
          action.category === 'Private' ? 'Private memory is excluded from AI context.' : 'Memory can improve future drafts.'
        )
      };
    }
    case 'editMemory': {
      const memory = state.memories.find(item => item.id === action.memoryId);
      if (!memory) {
        return {
          ...state,
          activity: addActivity(state, 'Memory', 'Memory not updated', 'This note is no longer available.', 'Warning')
        };
      }
      const validation = validateMemoryNoteInput(state, memory.contactId, action.body);
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Memory', 'Memory not updated', validation.message, 'Warning')
        };
      }
      return {
        ...state,
        memories: state.memories.map(item =>
          item.id === action.memoryId
            ? {
                ...item,
                category: action.category,
                body: validation.value.body,
                pinned: action.category === 'Private' ? false : item.pinned
              }
            : item
        ),
        activity: addActivity(
          state,
          'Memory',
          'Memory updated',
          action.category === 'Private' ? 'Private memory is excluded from AI context.' : 'Memory can improve future drafts.'
        )
      };
    }
    case 'toggleMemoryPin': {
      const memory = state.memories.find(item => item.id === action.memoryId);
      if (!memory) {
        return {
          ...state,
          activity: addActivity(state, 'Memory', 'Memory not pinned', 'This note is no longer available.', 'Warning')
        };
      }
      return {
        ...state,
        memories: state.memories.map(item => (item.id === action.memoryId ? { ...item, pinned: !item.pinned } : item)),
        activity: addActivity(
          state,
          'Memory',
          memory.pinned ? 'Memory unpinned' : 'Memory pinned',
          memory.pinned ? 'The note remains searchable in recent memories.' : 'Pinned notes appear first in Memory Vault.'
        )
      };
    }
    case 'deleteMemory': {
      const memory = state.memories.find(item => item.id === action.memoryId);
      if (!memory) {
        return {
          ...state,
          activity: addActivity(state, 'Memory', 'Memory not deleted', 'This note is no longer available.', 'Warning')
        };
      }
      return {
        ...state,
        memories: state.memories.filter(item => item.id !== action.memoryId),
        activity: addActivity(state, 'Memory', 'Memory deleted', 'The note was removed from this contact.')
      };
    }
    case 'answerEnrichmentPrompt': {
      const contact = findContact(state, action.contactId);
      if (!contact) {
        return {
          ...state,
          activity: addActivity(state, 'Memory', 'Enrichment not saved', 'Contact could not be found.', 'Warning')
        };
      }
      const prompt = resolveContactEnrichmentPrompt(state, action.contactId, action.promptId);
      if (!prompt) {
        return {
          ...state,
          activity: addActivity(
            state,
            'Memory',
            'Enrichment not saved',
            'This enrichment prompt is no longer available.',
            'Warning'
          )
        };
      }
      const validation = validateEnrichmentAnswer(action.body);
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Memory', 'Enrichment not saved', validation.message, 'Warning')
        };
      }
      return {
        ...state,
        contacts: state.contacts.map(item =>
          item.id === action.contactId ? { ...item, healthScore: Math.min(100, item.healthScore + 4) } : item
        ),
        memories: [
          {
            id: `memory-${Date.now()}`,
            contactId: action.contactId,
            category: prompt.category,
            body: buildEnrichmentMemoryBody(prompt, validation.value),
            pinned: prompt.category !== 'Private',
            createdAt: nowIso()
          },
          ...state.memories
        ],
        activity: addActivity(
          state,
          'Memory',
          'Enrichment saved',
          `${prompt.category} context saved for ${contact.name}.`
        )
      };
    }
    case 'addGift':
      if (!findContact(state, action.contactId)) {
        return {
          ...state,
          activity: addActivity(state, 'Gift', 'Gift not saved', 'Contact could not be found.', 'Warning')
        };
      }
      const giftValidation = validateGiftInput({
        name: action.name,
        category: action.category,
        occasion: action.occasion,
        cost: action.cost,
        feedback: action.feedback,
        notes: action.notes
      });
      if (!giftValidation.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Gift', 'Gift not saved', giftValidation.message, 'Warning')
        };
      }
      return {
        ...state,
        gifts: [
          {
            id: `gift-${Date.now()}`,
            contactId: action.contactId,
            name: giftValidation.value.name,
            category: giftValidation.value.category,
            occasion: giftValidation.value.occasion,
            cost: giftValidation.value.cost,
            year: new Date().getFullYear(),
            feedback: giftValidation.value.feedback ?? 'Unknown',
            notes: giftValidation.value.notes || 'Recorded from Gift Advisor.'
          },
          ...state.gifts
        ],
        activity: addActivity(state, 'Gift', 'Gift saved', `${giftValidation.value.name} was added to gift history.`)
      };
    case 'deleteGift': {
      const gift = state.gifts.find(item => item.id === action.giftId);
      if (!gift) {
        return {
          ...state,
          activity: addActivity(state, 'Gift', 'Gift not deleted', 'This gift record is no longer available.', 'Warning')
        };
      }
      return {
        ...state,
        gifts: state.gifts.filter(item => item.id !== action.giftId),
        activity: addActivity(state, 'Gift', 'Gift deleted', `${gift.name} was removed from gift history.`)
      };
    }
    case 'updateGiftBudget': {
      const contact = findContact(state, action.contactId);
      if (!contact) {
        return {
          ...state,
          activity: addActivity(state, 'Gift', 'Gift budget not saved', 'Contact could not be found.', 'Warning')
        };
      }
      const validation = validateGiftBudgetInput({ annualGiftBudget: action.annualGiftBudget });
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Gift', 'Gift budget not saved', validation.message, 'Warning')
        };
      }
      return {
        ...state,
        contacts: state.contacts.map(item =>
          item.id === action.contactId
            ? {
                ...item,
                annualGiftBudget: validation.value
              }
            : item
        ),
        activity: addActivity(
          state,
          'Gift',
          'Gift budget saved',
          `Annual gift budget updated for ${contact.name}.`
        )
      };
    }
    case 'updateContactEssentials': {
      const contact = findContact(state, action.contactId);
      if (!contact) {
        return {
          ...state,
          activity: addActivity(state, 'Contact', 'Contact not saved', 'Contact could not be found.', 'Warning')
        };
      }
      const preferences = resolveContactPreferencesForContact(state.settings, contact);
      const validation = validateContactEssentials(action.input, preferences.preferredChannel);
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Contact', 'Contact not saved', validation.message, 'Warning')
        };
      }
      const reviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(item =>
          item.id === action.contactId
            ? {
                ...item,
                ...validation.value
              }
            : item
        ),
        messages: reviewUpdate.messages,
        activity: addActivity(
          state,
          'Contact',
          'Contact saved',
          `Profile updated for ${validation.value.name}.${
            reviewUpdate.count > 0 ? ` ${reviewUpdate.count} unsent message(s) returned to review.` : ''
          }`
        )
      };
    }
    case 'updateContactTone': {
      const reviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId
            ? updateContactPreferenceOverride(state, contact, {
                tone: contact.tone.includes(action.tone)
                  ? contact.tone.filter(tone => tone !== action.tone)
                  : [...contact.tone, action.tone]
              })
            : contact
        ),
        messages: reviewUpdate.messages
      };
    }
    case 'setContactGroup':
      if (!relationshipGroupOptions.includes(action.group)) {
        return state;
      }
      const groupReviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId
            ? contact.preferenceOverrides === undefined
              ? { ...contact, group: action.group }
              : applyResolvedPreferencesToContact(
                  { ...contact, group: action.group },
                  resolveContactPreferencesForContact(state.settings, { ...contact, group: action.group })
                )
            : contact
        ),
        messages: groupReviewUpdate.messages,
        activity: addActivity(state, 'Contact', 'Relationship group updated', `Contact moved to ${action.group}.`)
      };
    case 'setRelationshipGroupDefault': {
      if (!relationshipGroupOptions.includes(action.group)) {
        return state;
      }
      const groupDefaults = normalizeRelationshipGroupDefaults({
        ...state.settings.groupDefaults,
        [action.group]: {
          ...state.settings.groupDefaults[action.group],
          ...action.defaults
        }
      });
      const settings = {
        ...state.settings,
        groupDefaults
      };
      const affectedContactIds = state.contacts
        .filter(contact => contact.group === action.group && contact.preferenceOverrides !== undefined)
        .map(contact => contact.id);
      const reviewUpdate = markContactMessagesForReview(state.messages, affectedContactIds);
      return {
        ...state,
        settings,
        contacts: state.contacts.map(contact =>
          contact.group === action.group && contact.preferenceOverrides !== undefined
            ? applyResolvedPreferencesToContact(contact, resolveContactPreferencesForContact(settings, contact))
            : contact
        ),
        messages: reviewUpdate.messages,
        activity: addActivity(state, 'Contact', 'Relationship group defaults updated', `${action.group} defaults changed.`)
      };
    }
    case 'toggleContactVip':
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId ? { ...contact, isVip: !contact.isVip } : contact
        ),
        activity: addActivity(state, 'Contact', 'VIP setting updated', 'Contact priority was changed.')
      };
    case 'toggleContactDnd': {
      const reviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId ? { ...contact, dnd: !contact.dnd } : contact
        ),
        messages: reviewUpdate.messages,
        activity: addActivity(state, 'Contact', 'Do-not-disturb updated', 'Contact automation preference was changed.')
      };
    }
    case 'setCheckInCadence':
      if (!checkInCadenceOptions.includes(action.days)) {
        return {
          ...state,
          activity: addActivity(state, 'Contact', 'Check-in cadence not saved', 'Choose a supported cadence.', 'Warning')
        };
      }
      const cadenceReviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId
            ? updateContactPreferenceOverride(state, contact, { checkInCadenceDays: action.days })
            : contact
        ),
        messages: cadenceReviewUpdate.messages,
        activity: addActivity(state, 'Contact', 'Check-in cadence updated', `Cadence changed to ${action.days} day(s).`)
      };
    case 'setContactChannel': {
      const reviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId
            ? updateContactPreferenceOverride(state, contact, { preferredChannel: action.channel })
            : contact
        ),
        messages: reviewUpdate.messages
      };
    }
    case 'setContactAutomationMode':
      if (!isAutomationModeAvailable(action.mode)) {
        return {
          ...state,
          activity: addActivity(
            state,
            'Setup',
            'Unattended automation unavailable',
            productAvailability.durableUnattendedAutomation.reason,
            'Warning'
          )
        };
      }
      if (!automationModes.includes(action.mode)) {
        return state;
      }
      const automationReviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId
            ? updateContactPreferenceOverride(state, contact, { automationMode: action.mode })
            : contact
        ),
        messages: automationReviewUpdate.messages,
        activity: addActivity(state, 'Contact', 'Contact automation override updated', `${action.mode} selected for this contact.`)
      };
    case 'useGroupDefaultsForContact': {
      const reviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(contact => {
          if (contact.id !== action.contactId) {
            return contact;
          }
          const inherited = {
            ...contact,
            preferenceOverrides: {}
          };
          return applyResolvedPreferencesToContact(inherited, resolveContactPreferencesForContact(state.settings, inherited));
        }),
        messages: reviewUpdate.messages,
        activity: addActivity(state, 'Contact', 'Group defaults applied', 'Contact now inherits group preferences.')
      };
    }
    case 'snoozeCheckIn':
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId ? { ...contact, checkInSnoozedUntil: addDaysIso(action.days, action.nowIso) } : contact
        ),
        activity: addActivity(state, 'Contact', 'Check-in snoozed', `Reminder moved by ${action.days} day(s).`)
      };
    case 'markContactedElsewhere': {
      const contactedAt = action.nowIso ?? nowIso();
      return {
        ...state,
        contacts: state.contacts.map(contact => {
          if (contact.id !== action.contactId) {
            return contact;
          }
          return {
            ...contact,
            checkInSnoozedUntil: undefined,
            lastContactedAt: contactedAt,
            healthScore: Math.min(100, contact.healthScore + 3)
          };
        }),
        activity: addActivity(
          state,
          'Contact',
          'Contact marked contacted',
          'Check-in history was updated without creating or sending a message.'
        )
      };
    }
    case 'setLocale':
      return {
        ...state,
        settings: {
          ...state.settings,
          locale: action.locale
        },
        activity: addActivity(state, 'Setup', 'Language updated', `Locale changed to ${action.locale}.`)
      };
    case 'setEmailSender':
      return {
        ...state,
        emailDelivery: {
          ...state.emailDelivery,
          senderEmail: action.senderEmail.trim(),
          status: action.senderEmail.trim().length > 0 ? state.emailDelivery.status : 'Not configured',
          lastError: undefined
        },
        activity: addActivity(state, 'Setup', 'Email sender updated', 'Email sender configuration changed.')
      };
    case 'emailProviderReady':
      return {
        ...state,
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Ready',
          lastCheckedAt: nowIso(),
          lastError: undefined
        },
        activity: addActivity(state, 'Setup', 'Email provider ready', 'Email delivery endpoint accepted the message.')
      };
    case 'emailProviderFailure':
      return {
        ...state,
        messages: action.messageId
          ? updateMessage(state, action.messageId, message => emailDeliveryFailureTransition(message, action.error))
          : state.messages,
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Error',
          lastCheckedAt: nowIso(),
          lastError: action.error.message
        },
        activity: addActivity(
          state,
          'Setup',
          'Email delivery failed',
          action.error.message,
          'Warning',
          action.messageId
            ? {
                targetScreen: 'wishPreview',
                messageId: action.messageId,
                actionLabel: 'Review email'
              }
            : undefined
        )
      };
    case 'emailDeliveryAccepted': {
      const acceptedAt = nowIso();
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          status: 'Delivery pending',
          readiness: 'Provider accepted the email; awaiting confirmed delivery',
          lastError: undefined,
          emailDeliveryAttempt: {
            idempotencyKey: action.idempotencyKey,
            status: 'Accepted',
            deliveryId: action.deliveryId,
            updatedAt: acceptedAt
          }
        })),
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Ready',
          lastCheckedAt: acceptedAt,
          lastError: undefined
        },
        activity: addActivity(
          state,
          'Message',
          'Email accepted by provider',
          'The provider accepted this idempotent attempt; sent status is still pending.',
          'Info',
          {
            targetScreen: 'wishPreview',
            messageId: action.messageId,
            actionLabel: 'Review email'
          }
        )
      };
    }
    case 'emailDeliveryUnknown': {
      const attemptedAt = nowIso();
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message =>
          message.status === 'Sent'
            ? message
            : {
                ...message,
                status: 'Delivery unknown',
                readiness: 'Delivery status unknown; reconcile before retrying',
                lastError: action.error.message,
                emailDeliveryAttempt: {
                  idempotencyKey: action.idempotencyKey,
                  status: 'Unknown',
                  updatedAt: attemptedAt
                }
              }
        ),
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Error',
          lastCheckedAt: attemptedAt,
          lastError: action.error.message
        },
        activity: addActivity(
          state,
          'Message',
          'Email delivery status unknown',
          'The provider result was not received. Reconcile this attempt before retrying.',
          'Warning',
          {
            targetScreen: 'wishPreview',
            messageId: action.messageId,
            actionLabel: 'Review email'
          }
        )
      };
    }
    case 'emailSent': {
      const request = buildEmailDeliveryRequest(state, action.messageId);
      if (!request.ok) {
        return {
          ...state,
          emailDelivery: {
            ...state.emailDelivery,
            status: 'Error',
            lastCheckedAt: nowIso(),
            lastError: request.error.message
          },
          activity: addActivity(state, 'Message', 'Email sent status not recorded', request.error.message, 'Warning', {
            targetScreen: 'messages',
            messageId: action.messageId,
            actionLabel: 'Open messages'
          })
        };
      }
      const sentMessage = state.messages.find(item => item.id === action.messageId);
      const sentAt = nowIso();
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          status: 'Sent',
          readiness: 'Email sent by configured provider',
          sentAt,
          emailDeliveryAttempt: {
            idempotencyKey: action.idempotencyKey ?? request.request.idempotencyKey,
            status: 'Sent',
            deliveryId: action.deliveryId,
            updatedAt: sentAt
          }
        })),
        contacts: state.contacts.map(contact => {
          return sentMessage?.contactId === contact.id
            ? { ...contact, lastContactedAt: sentAt, healthScore: Math.min(100, contact.healthScore + 5) }
            : contact;
        }),
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Ready',
          lastCheckedAt: sentAt,
          lastError: undefined
        },
        activity: addActivity(state, 'Message', 'Email sent', 'The approved email was sent by the configured provider.', 'Info', {
          targetScreen: sentMessage ? 'chatHistory' : 'messages',
          messageId: action.messageId,
          contactId: sentMessage?.contactId,
          actionLabel: sentMessage ? 'Open chat history' : 'Open messages'
        })
      };
    }
    case 'setOnboardingGoal':
      return {
        ...state,
        onboarding: {
          ...state.onboarding,
          selectedGoal: action.goal,
          lastUpdatedAt: nowIso()
        },
        activity: addActivity(state, 'Setup', 'Onboarding goal updated', `Goal changed to ${action.goal}.`)
      };
    case 'setOnboardingStep':
      return {
        ...state,
        activeScreen: 'onboarding',
        onboarding: {
          ...state.onboarding,
          currentStepId: action.stepId,
          lastUpdatedAt: nowIso()
        }
      };
    case 'advanceOnboarding': {
      const currentStepId = state.onboarding.currentStepId;
      if (currentStepId === 'finish') {
        return relateReducer(state, { type: 'completeOnboarding' });
      }
      return {
        ...state,
        onboarding: {
          ...state.onboarding,
          currentStepId: nextOnboardingStep(currentStepId),
          completedStepIds: uniqueSteps([...state.onboarding.completedStepIds, currentStepId]),
          lastUpdatedAt: nowIso()
        }
      };
    }
    case 'skipOnboardingStep':
      return {
        ...state,
        onboarding: {
          ...state.onboarding,
          currentStepId: nextOnboardingStep(action.stepId),
          skippedStepIds: uniqueSteps([...state.onboarding.skippedStepIds, action.stepId]),
          lastUpdatedAt: nowIso()
        },
        activity: addActivity(state, 'Setup', 'Onboarding step skipped', `${action.stepId} can be completed later.`)
      };
    case 'completeOnboarding':
      return {
        ...state,
        activeScreen: 'home',
        onboarding: {
          ...state.onboarding,
          completed: true,
          currentStepId: 'finish',
          completedStepIds: uniqueSteps([...state.onboarding.completedStepIds, 'finish']),
          lastUpdatedAt: nowIso()
        },
        activity: addActivity(state, 'Setup', 'Onboarding completed', 'Home is ready; setup gaps remain available from Settings and Setup Check.')
      };
    case 'reopenOnboarding':
      return {
        ...state,
        activeScreen: 'onboarding',
        onboarding: {
          ...state.onboarding,
          lastUpdatedAt: nowIso()
        }
      };
    case 'setAccountMode':
      if (!isAccountModeAvailable(action.mode)) {
        return {
          ...state,
          settings: {
            ...state.settings,
            accountMode: 'Local'
          },
          activity: addActivity(
            state,
            'Setup',
            'Google sync unavailable',
            productAvailability.googleSync.reason,
            'Warning'
          )
        };
      }
      return {
        ...state,
        settings: {
          ...state.settings,
          accountMode: action.mode
        },
        onboarding: {
          ...state.onboarding,
          completedStepIds: uniqueSteps([...state.onboarding.completedStepIds, 'account']),
          lastUpdatedAt: nowIso()
        },
        activity: addActivity(
          state,
          'Setup',
          'Account mode updated',
          action.mode === 'Local'
            ? 'Local mode selected; provider sync will stay off until explicitly connected.'
            : 'Google sync mode selected; provider sign-in must still be completed explicitly.'
        )
      };
    case 'disconnectAccount':
      return {
        ...state,
        settings: {
          ...state.settings,
          accountMode: 'Local'
        },
        activity: addActivity(state, 'Setup', 'Account disconnected', 'Provider sync was disconnected while local data was retained.')
      };
    case 'recordPermissionDecision': {
      const notificationsBlocked =
        action.capability === 'Notifications' && (action.decision === 'Denied' || action.decision === 'Unavailable');
      const clearedReminderCount = notificationsBlocked ? state.reminderPlans.length : 0;
      const detail =
        clearedReminderCount > 0
          ? `${action.capability} capability is now marked ${action.decision}. ${clearedReminderCount} notification reminder plan(s) cleared; reminders remain visible in-app.`
          : `${action.capability} capability is now marked ${action.decision}.`;
      return {
        ...state,
        privacy: {
          ...state.privacy,
          permissionDecisions: {
            ...state.privacy.permissionDecisions,
            [action.capability]: action.decision
          }
        },
        reminderPlans: clearedReminderCount > 0 ? [] : state.reminderPlans,
        activity: addActivity(
          state,
          'Setup',
          `${action.capability} permission ${action.decision.toLowerCase()}`,
          detail,
          clearedReminderCount > 0 ? 'Warning' : 'Info'
        )
      };
    }
    case 'permissionsReconciled':
      return {
        ...state,
        privacy: {
          ...state.privacy,
          permissionRecords: action.records,
          permissionDecisions: action.decisions
        }
      };
    case 'toggleWhatsAppHandoffConsent':
      return {
        ...state,
        privacy: {
          ...state.privacy,
          whatsappHandoffConsent: !state.privacy.whatsappHandoffConsent
        },
        activity: addActivity(
          state,
          'Setup',
          'Manual WhatsApp handoff consent updated',
          state.privacy.whatsappHandoffConsent
            ? 'Manual WhatsApp handoff consent was revoked.'
            : 'Manual WhatsApp handoff consent was granted for approved handoff only.'
        )
      };
    case 'clearLocalDataConfirmed':
      return createClearedLocalState(state);
    case 'toggleSetting': {
      const value = state.settings[action.key];
      if (typeof value !== 'boolean') {
        return state;
      }
      const nextValue = !value;
      const deliveryChannel = settingDeliveryChannelMap[action.key];
      const reviewUpdate =
        deliveryChannel && nextValue === false
          ? markScheduledChannelMessagesForReview(state.messages, deliveryChannel)
          : action.key === 'aiEnabled' && nextValue === false
            ? markAiDraftMessagesForReview(state.messages)
          : { messages: state.messages, count: 0 };
      const clearedReminderCount =
        action.key === 'notificationsEnabled' && nextValue === false ? state.reminderPlans.length : 0;
      const reminderPlans = clearedReminderCount > 0 ? [] : state.reminderPlans;
      const detail =
        reviewUpdate.count > 0 && deliveryChannel
          ? `${String(action.key)} changed. ${reviewUpdate.count} scheduled ${deliveryChannel} message(s) returned to review.`
          : reviewUpdate.count > 0 && action.key === 'aiEnabled'
            ? `${String(action.key)} changed. ${reviewUpdate.count} unsent AI draft message(s) flagged for manual review.`
          : clearedReminderCount > 0
            ? `${String(action.key)} changed. ${clearedReminderCount} notification reminder plan(s) cleared; reminders remain visible in-app.`
          : `${String(action.key)} changed.`;
      return {
        ...state,
        settings: {
          ...state.settings,
          [action.key]: nextValue
        },
        messages: reviewUpdate.messages,
        reminderPlans,
        activity: addActivity(
          state,
          'Setup',
          'Setting updated',
          detail,
          reviewUpdate.count > 0 || clearedReminderCount > 0 ? 'Warning' : 'Info'
        )
      };
    }
    case 'setAutomationMode':
      if (!isAutomationModeAvailable(action.mode)) {
        return {
          ...state,
          activity: addActivity(
            state,
            'Setup',
            'Unattended automation unavailable',
            productAvailability.durableUnattendedAutomation.reason,
            'Warning'
          )
        };
      }
      if (!automationModes.includes(action.mode)) {
        return state;
      }
      const automationModeChanged = action.mode !== state.settings.automationMode;
      const reviewUpdate = automationModeChanged
        ? markScheduledAutomationMessagesForReview(state.messages)
        : { messages: state.messages, count: 0 };
      const contactImpactCount = automationModeChanged ? countAutomationModeContactImpacts(state, action.mode) : 0;
      return {
        ...state,
        settings: {
          ...state.settings,
          automationMode: action.mode
        },
        messages: reviewUpdate.messages,
        activity: addActivity(
          state,
          'Setup',
          'Automation mode updated',
          automationModeChanged
            ? `Automation mode changed to ${action.mode}. ${contactImpactCount} contact(s) changed effective automation mode. ${reviewUpdate.count} scheduled message(s) returned to review.`
            : `Automation mode remains ${action.mode}.`,
          reviewUpdate.count > 0 ? 'Warning' : 'Info'
        )
      };
    case 'setQuietHours': {
      const quietHours = {
        start: action.start.trim(),
        end: action.end.trim()
      };
      const problem = validateQuietHours(quietHours);
      if (problem) {
        return {
          ...state,
          activity: addActivity(state, 'Setup', 'Quiet hours not saved', problem, 'Warning')
        };
      }
      const settings = {
        ...state.settings,
        quietHours
      };
      const reviewUpdate = markSchedulePolicyConflictsForReview(state.messages, settings);
      return {
        ...state,
        settings,
        messages: reviewUpdate.messages,
        activity: addActivity(
          state,
          'Setup',
          'Quiet hours updated',
          `${quietHours.start} to ${quietHours.end}.${
            reviewUpdate.count > 0 ? ` ${reviewUpdate.count} scheduled message(s) returned to review.` : ''
          }`,
          reviewUpdate.count > 0 ? 'Warning' : 'Info'
        )
      };
    }
    case 'addBlackout': {
      const validation = validateBlackoutInput({
        label: action.label,
        startDate: action.startDate,
        endDate: action.endDate
      });
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(state, 'Setup', 'Blackout not saved', validation.message, 'Warning')
        };
      }
      const settings = {
        ...state.settings,
        blackouts: [
          {
            id: `blackout-${Date.now()}`,
            ...validation.value
          },
          ...state.settings.blackouts
        ]
      };
      const reviewUpdate = markSchedulePolicyConflictsForReview(state.messages, settings);
      return {
        ...state,
        settings,
        messages: reviewUpdate.messages,
        activity: addActivity(
          state,
          'Setup',
          'Blackout added',
          `${validation.value.label} will pause reminders.${
            reviewUpdate.count > 0 ? ` ${reviewUpdate.count} scheduled message(s) returned to review.` : ''
          }`,
          reviewUpdate.count > 0 ? 'Warning' : 'Info'
        )
      };
    }
    case 'removeBlackout':
      return {
        ...state,
        settings: {
          ...state.settings,
          blackouts: state.settings.blackouts.filter(blackout => blackout.id !== action.blackoutId)
        },
        activity: addActivity(state, 'Setup', 'Blackout removed', 'Reminder blackout window removed.')
      };
    case 'importContacts': {
      const result = importContacts(state, action.records);
      return {
        ...state,
        contacts: result.contacts,
        events: result.events,
        activity: addActivity(
          state,
          'Contact',
          'Contacts imported',
          `${result.added} added, ${result.updated} updated, ${result.skipped} skipped. Review imported birthdays before sending.`
        )
      };
    }
    case 'planReminders': {
      const planning = buildReminderPlanningResult(state);
      const issueDetail =
        planning.issues.length > 0
          ? ` ${planning.issues.map(issue => `${issue.title}: ${issue.detail}`).join(' ')}`
          : '';
      return {
        ...state,
        reminderPlans: planning.plans,
        activity: addActivity(
          state,
          'Event',
          planning.plans.length > 0 ? 'Reminders planned' : 'Reminders need setup',
          `${planning.plans.length} reminder(s) are ready to schedule. ${planning.adjustedCount} adjusted, ${planning.skippedCount} skipped.${issueDetail}`,
          planning.plans.length > 0 ? 'Info' : 'Warning'
        )
      };
    }
    case 'reminderPlansReconciled':
      return {
        ...state,
        reminderPlans: action.plans
      };
    case 'calendarImported': {
      const result = calendarCandidatesToEvents(state, action.candidates);
      return {
        ...state,
        contacts: result.contacts,
        events: result.events,
        calendarSync: {
          ...state.calendarSync,
          lastImportedAt: nowIso(),
          importedCount: state.calendarSync.importedCount + result.addedEvents,
          lastError: undefined
        },
        activity: addActivity(
          state,
          'Event',
          'Calendar events imported',
          `${result.addedEvents} event(s), ${result.addedContacts} contact(s), ${result.skipped} skipped.`
        )
      };
    }
    case 'calendarExported':
      return {
        ...state,
        calendarSync: {
          ...state.calendarSync,
          lastExportedAt: nowIso(),
          exportedCount: state.calendarSync.exportedCount + action.count,
          lastError: undefined
        },
        activity: addActivity(state, 'Event', 'Events exported to calendar', `${action.count} event(s) exported.`)
      };
    case 'calendarError':
      return {
        ...state,
        calendarSync: {
          ...state.calendarSync,
          lastError: action.message
        },
        activity: addActivity(state, 'Event', 'Calendar sync failed', action.message, 'Warning')
      };
    case 'persistenceSaving':
      return {
        ...state,
        persistence: {
          ...state.persistence,
          status: 'Saving',
          error: undefined
        }
      };
    case 'persistenceSaved':
      return {
        ...state,
        persistence: {
          status: 'Ready',
          lastSavedAt: action.savedAt,
          storageHealth: action.storageHealth
        }
      };
    case 'persistenceError':
      return {
        ...state,
        persistence: {
          ...state.persistence,
          status: 'Error',
          error: action.message
        }
      };
    case 'createBackup':
      return {
        ...state,
        backups: [
          {
            id: `backup-${Date.now()}`,
            createdAt: nowIso(),
            recordCount:
              state.contacts.length +
              state.events.length +
              state.messages.length +
              state.memories.length +
              state.gifts.length +
              state.activity.length,
            encrypted: true
          },
          ...state.backups
        ],
        activity: addActivity(state, 'Backup', 'Encrypted backup created', 'Backup file export completed.')
      };
    case 'restoreBackup': {
      const restoredState = normalizeLoadedState(action.restoredState);
      return {
        ...restoredState,
        activeScreen: 'more',
        selectedContactId: undefined,
        selectedMessageId: undefined,
        activity: addActivity(
          restoredState,
          'Backup',
          'Encrypted backup restored',
          `${action.recordCount} record(s) restored from the selected backup.`
        )
      };
    }
    case 'analyticsExported':
      const analyticsFormat = action.format ?? 'CSV report';
      return {
        ...state,
        activity: addActivity(
          state,
          'Analytics',
          'Analytics report exported',
          `${action.rowCount} redacted ${analyticsFormat === 'Summary' ? 'summary line' : 'report row'}(s) shared.`
        )
      };
    case 'setupDoctorDryRunRecorded':
      return {
        ...state,
        activity: addActivity(state, 'Setup', 'Setup Check dry run completed', action.detail, 'Info', {
          targetScreen: 'more',
          actionLabel: 'Open Setup Check'
        })
      };
    case 'trainStyle':
    case 'trainStyleFromSentMessages': {
      const result = analyzeSentMessageStyle(state);
      if (!result.ok) {
        return {
          ...state,
          activity: addActivity(state, 'AI', 'Style profile not updated', result.message, 'Warning')
        };
      }
      return {
        ...state,
        styleProfile: result.profile,
        activity: addActivity(
          state,
          'AI',
          'Style profile updated',
          `${result.source} analyzed; ${result.profile.sampleCount} sample(s), ${result.profile.confidence} confidence.`
        )
      };
    }
    case 'trainStyleFromSamples': {
      const result = analyzeManualStyleSamples(action.samples);
      if (!result.ok) {
        return {
          ...state,
          activity: addActivity(state, 'AI', 'Style profile not updated', result.message, 'Warning')
        };
      }
      return {
        ...state,
        styleProfile: result.profile,
        activity: addActivity(
          state,
          'AI',
          'Style profile updated',
          `${result.source} analyzed; ${result.profile.sampleCount} sample(s), ${result.profile.confidence} confidence.`
        )
      };
    }
    default:
      return state;
  }
};
