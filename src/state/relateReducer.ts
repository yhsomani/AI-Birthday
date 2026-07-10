import { createProductionInitialState } from '../data/productionState';
import { isAccountModeAvailable, isAutomationModeAvailable, productAvailability } from '../config/productAvailability';
import type { AiDraftContextOptions, AiDraftError, AiDraftVariants } from '../domain/aiDrafting';
import { buildActivityHistory } from '../domain/activityHistory';
import { calendarCandidatesToEvents, type CalendarImportResolutions } from '../domain/calendarSync';
import {
  applyResolvedPreferencesToContact,
  contactAllowsAutomaticDraftGeneration,
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
import {
  applyContactArchive,
  applyContactDelete,
  applyContactEdit,
  applyContactMerge,
  applyContactRestore,
  buildStandaloneContact,
  previewContactArchive,
  previewContactDelete,
  previewContactEdit,
  previewContactMerge,
  type StandaloneContactInput
} from '../domain/contactLifecycle';
import { importContacts, type ContactImportResolutions } from '../domain/contactImport';
import {
  buildEmailDeliveryRequest,
  isValidEmailAddress,
  normalizeEmailAddress,
  type EmailDeliveryError
} from '../domain/emailDelivery';
import {
  assessDuplicateMessageRisk,
  detectDuplicateMessageRisk,
  messageDraftRevision,
  messageOccurrenceDate
} from '../domain/duplicateGuard';
import {
  eventPreparationOccurrenceKey,
  toggleEventChecklistItemForOccurrence,
  toggleEventPreparationChecklistStep,
  type EventPreparationStepId
} from '../domain/eventPreparation';
import {
  applyEventDelete,
  applyEventEdit,
  previewEventDelete,
  previewEventEdit,
  type EventEditInput
} from '../domain/eventLifecycle';
import { applyEventMerge, previewEventMerge } from '../domain/eventConflictLifecycle';
import { buildDefaultEventChecklist, validateManualEventInput } from '../domain/events';
import { buildMessageFollowUpPlan, type FollowUpDelayDays } from '../domain/followUps';
import { validateGiftBudgetInput, validateGiftInput } from '../domain/giftAdvisor';
import {
  buildMessageApprovalWindow,
  messageApprovalWindowIssue,
  messageLifecycleTransitionIssue
} from '../domain/messageApproval';
import { validateMessageBodyForChannel } from '../domain/messageBodyPolicy';
import {
  buildMessageBulkActionReport,
  findNextReviewMessageId,
  messageApprovalRouteIssue,
  type MessageBulkAction
} from '../domain/messageInbox';
import { buildMessageTestPlan } from '../domain/messageTesting';
import { buildLocalTemplateFallback, buildTemplateDraft } from '../domain/messageTemplates';
import { validateMemoryNoteInput } from '../domain/memoryVault';
import { nextOnboardingStep, onboardingTransitionIssue, requiredOnboardingStepIds } from '../domain/onboarding';
import { eventOccurrenceLocalDateKey, recurrenceFromDate } from '../domain/occasionDates';
import { firstMentionableMemoryTextForContact } from '../domain/personalizationContextPolicy';
import { buildReminderPlanningResult } from '../domain/reminders';
import { checkInCadenceOptions, relationshipGroupOptions } from '../domain/relationshipHealth';
import {
  adjustTriggerForSchedulingPolicy,
  automationModes,
  messageDispatchTimingIssue,
  normalizeScheduleTimeZone,
  scheduleMessageForEvent,
  scheduleTimeZonesMatch,
  validateBlackoutInput,
  validateDefaultSendTime,
  validateQuietHours
} from '../domain/schedulingPolicy';
import { analyzeManualStyleSamples, analyzeSentMessageStyle } from '../domain/styleCoach';
import type {
  AppState,
  AccountMode,
  AutomationMode,
  AiProviderObservation,
  CalendarImportCandidate,
  ComposerReason,
  Contact,
  ContactGroupDefaults,
  ContactQuietHoursBehavior,
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
import { prependActivity as addActivity } from './activityTransitions';
import {
  allocateCommandMetadata,
  commandId,
  localCalendarYear,
  systemCommandDependencies,
  type CommandDependencies,
  type CommandIdCounts,
  type CommandMetadata
} from './commandMetadata';

export type RelateAction =
  | { type: 'hydrate'; state: AppState }
  | { type: 'reconcileScheduledMessageTimeZone'; timeZone: string }
  | { type: 'navigate'; screen: Screen; contactId?: string; eventId?: string; messageId?: string }
  | { type: 'setSearch'; query: string }
  | { type: 'resolveActivity'; activityId: string }
  | { type: 'addContact'; input: StandaloneContactInput }
  | { type: 'editContact'; contactId: string; input: ContactEssentialsInput; confirmationToken: string }
  | { type: 'archiveContact'; contactId: string; confirmationToken: string }
  | { type: 'restoreContact'; contactId: string }
  | { type: 'deleteContact'; contactId: string; confirmationToken: string }
  | {
      type: 'mergeContacts';
      survivorContactId: string;
      mergedContactId: string;
      confirmationToken: string;
    }
  | { type: 'toggleChecklist'; eventId: string; itemId: string }
  | { type: 'togglePreparationStep'; eventId: string; stepId: EventPreparationStepId }
  | { type: 'editEvent'; eventId: string; input: EventEditInput; confirmationToken?: string }
  | { type: 'deleteEvent'; eventId: string; confirmationToken: string }
  | {
      type: 'mergeEvents';
      survivorEventId: string;
      mergedEventId: string;
      confirmationToken: string;
    }
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
  | { type: 'setMessageChannel'; messageId: string; channel: MessageChannel }
  | {
      type: 'generateMessage';
      contactId: string;
      eventId?: string;
      reason: ComposerReason;
      fallbackReason?: string;
      excludedMemoryIds?: string[];
      includePriorMessages?: boolean;
      feedback?: MessageRegenerationFeedback;
      regenerationSource?: MessageRegenerationSource;
      generationOrigin?: 'User requested' | 'Automatic';
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
      regenerationSource?: MessageRegenerationSource;
      generationOrigin?: 'User requested' | 'Automatic';
    }
  | { type: 'aiProviderReady'; privacySummary: string; observation?: AiProviderObservation }
  | { type: 'aiProviderFailure'; error: AiDraftError; privacySummary?: string; observation?: AiProviderObservation }
  | { type: 'approveMessage'; messageId: string; nowIso?: string; reviewNext?: boolean }
  | { type: 'acknowledgeDuplicateRisk'; messageId: string }
  | { type: 'rejectMessage'; messageId: string; reviewNext?: boolean }
  | { type: 'revokeMessage'; messageId: string }
  | { type: 'bulkMessageAction'; action: MessageBulkAction; messageIds: string[]; nowIso?: string }
  | { type: 'testMessageRoute'; messageId: string }
  | { type: 'manualHandoff'; messageId: string; nowIso?: string; shareFallbackUsed?: boolean }
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
  | { type: 'setContactCustomSendTime'; contactId: string; time?: string }
  | { type: 'setContactQuietHoursBehavior'; contactId: string; behavior: ContactQuietHoursBehavior }
  | { type: 'setContactSkipAuto'; contactId: string; enabled: boolean }
  | { type: 'useGroupDefaultsForContact'; contactId: string }
  | { type: 'snoozeCheckIn'; contactId: string; days: number; nowIso?: string }
  | { type: 'markContactedElsewhere'; contactId: string; nowIso?: string }
  | { type: 'setLocale'; locale: SupportedLocale }
  | { type: 'setEmailSender'; senderEmail: string }
  | { type: 'emailProviderReady' }
  | { type: 'emailProviderFailure'; error: EmailDeliveryError; messageId?: string }
  | { type: 'emailDeliveryAccepted'; messageId: string; idempotencyKey: string; deliveryId: string }
  | { type: 'emailDeliveryUnknown'; error: EmailDeliveryError; messageId: string; idempotencyKey: string }
  | {
      type: 'emailDeliveryReconciled';
      messageId: string;
      idempotencyKey: string;
      status: 'accepted' | 'sent' | 'failed';
      deliveryId?: string;
    }
  | { type: 'emailSent'; messageId: string; idempotencyKey?: string; deliveryId?: string; nowIso?: string }
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
  | { type: 'setDefaultSendTime'; time: string }
  | {
      type: 'addBlackout';
      label: string;
      startDate: string;
      endDate: string;
      behavior?: 'Block' | 'Defer';
      channels?: MessageChannel[];
    }
  | { type: 'removeBlackout'; blackoutId: string }
  | { type: 'importContacts'; records: ImportedContactRecord[]; resolutions?: ContactImportResolutions }
  | { type: 'planReminders' }
  | { type: 'reminderPlansReconciled'; plans: ReminderPlan[] }
  | { type: 'calendarImported'; candidates: CalendarImportCandidate[]; resolutions?: CalendarImportResolutions }
  | { type: 'calendarExported'; count: number }
  | { type: 'calendarError'; message: string }
  | { type: 'persistenceSaving' }
  | { type: 'persistenceSaved'; savedAt: string; storageHealth?: PersistenceStorageHealth }
  | { type: 'persistenceError'; message: string }
  | { type: 'createBackup' }
  | { type: 'restoreBackup'; restoredState: AppState; recordCount: number }
  | { type: 'analyticsExported'; rowCount: number; format?: 'CSV report' | 'Summary' }
  | { type: 'setupDoctorDryRunRecorded'; detail: string }
  | { type: 'setStyleEnabled'; enabled: boolean }
  | { type: 'trainStyle' }
  | { type: 'trainStyleFromSamples'; samples: string }
  | { type: 'trainStyleFromSentMessages' };

export type MessageRegenerationSource = Readonly<{
  messageId: string;
  expectedRevision: string;
}>;

type WithCommandMetadata<Action> = Action extends RelateAction ? Action & { metadata: CommandMetadata } : never;

export type EnrichedRelateAction = WithCommandMetadata<RelateAction>;

const commandIdCounts = (action: RelateAction): CommandIdCounts => {
  const counts: CommandIdCounts = { activity: 1 };
  switch (action.type) {
    case 'addContact':
      return { ...counts, contact: 1 };
    case 'addManualEvent':
      return { ...counts, contact: 1, event: 1 };
    case 'generateMessage':
    case 'createTemplateDraft':
    case 'createAiDraft':
      return { ...counts, message: 1 };
    case 'scheduleMessageFollowUp':
      return { ...counts, event: 1, reminder: 1 };
    case 'addMemory':
    case 'answerEnrichmentPrompt':
      return { ...counts, memory: 1 };
    case 'addGift':
      return { ...counts, gift: 1 };
    case 'addBlackout':
      return { ...counts, blackout: 1 };
    case 'importContacts':
      return { ...counts, contact: action.records.length, event: action.records.length };
    case 'calendarImported':
      return { ...counts, contact: action.candidates.length, event: action.candidates.length };
    case 'createBackup':
      return { ...counts, backup: 1 };
    default:
      return counts;
  }
};

export const enrichRelateAction = (
  action: RelateAction,
  dependencies: CommandDependencies = systemCommandDependencies
): EnrichedRelateAction => {
  const occurredAtOverride = 'nowIso' in action ? action.nowIso : undefined;
  return {
    ...action,
    metadata: allocateCommandMetadata(dependencies, commandIdCounts(action), occurredAtOverride)
  } as EnrichedRelateAction;
};

export { createProductionInitialState } from '../data/productionState';

const addDaysIso = (days: number, fromIso: string) => {
  const next = new Date(fromIso);
  next.setUTCDate(next.getUTCDate() + days);
  return next.toISOString();
};

const findContact = (state: AppState, contactId: string) =>
  state.contacts.find(contact => contact.id === contactId && !contact.archivedAt);

const findEvent = (state: AppState, eventId?: string) =>
  eventId ? state.events.find(event => event.id === eventId) : undefined;

const explicitPreferenceOverridesForContact = (state: AppState, contact: Contact): Contact['preferenceOverrides'] => ({
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
  const text = firstMentionableMemoryTextForContact(state, contactId, options.excludedMemoryIds);
  return text ? [text] : [];
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

  const instructionText = [...(feedback?.instructions ?? []), feedback?.customInstruction ?? '']
    .join(' ')
    .toLowerCase();
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
  metadata: CommandMetadata,
  state: AppState,
  contactId: string,
  eventId: string | undefined,
  reason: ComposerReason,
  options: {
    providerVariants?: AiDraftVariants;
    fallbackReason?: string;
    contextOptions?: AiDraftContextOptions;
    feedback?: MessageRegenerationFeedback;
    occurrenceDate?: string;
  } = {}
): MessageDraft | undefined => {
  const contact = findContact(state, contactId);
  const event = findEvent(state, eventId);
  if (!contact || (eventId !== undefined && (!event || event.contactId !== contactId))) {
    return undefined;
  }

  const context = getAiContext(state, contactId, options.contextOptions);
  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const localFallback = options.fallbackReason
    ? buildLocalTemplateFallback(state, contactId, reason, {
        excludedMemoryIds: options.contextOptions?.excludedMemoryIds,
        feedback: options.feedback,
        averageLength: state.styleProfile.enabledForAiDrafts ? state.styleProfile.averageLength : 160
      })
    : undefined;
  if (localFallback && !localFallback.ok) return undefined;
  const standard =
    options.providerVariants?.standard ??
    (localFallback?.ok
      ? localFallback.body
      : buildDraftText(
          contact,
          reason,
          context,
          state.styleProfile.enabledForAiDrafts ? state.styleProfile.averageLength : 160,
          preferences.tone,
          options.feedback
        ));
  const short =
    options.providerVariants?.short ??
    (localFallback?.ok
      ? localFallback.variants.short
      : standard.length > 88
        ? `${standard.slice(0, 85).trimEnd()}...`
        : standard);
  const warm =
    options.providerVariants?.warm ??
    (localFallback?.ok
      ? localFallback.variants.warm
      : `${standard} ${contact.isVip ? 'You are important to me, and I hope this feels personal.' : 'Hope this brings a small smile.'}`);
  const quality = options.providerVariants
    ? 'AI draft'
    : options.fallbackReason
      ? 'Template fallback'
      : context.length > 0
        ? 'AI draft'
        : 'Needs more context';
  const schedule = event
    ? scheduleMessageForEvent(event, state.settings, preferences.preferredChannel, new Date(metadata.occurredAt), {
        customSendTime: preferences.customSendTime,
        quietHoursBehavior: preferences.quietHoursBehavior
      })
    : { adjustments: [] as string[] };

  return {
    id: commandId(metadata, 'message'),
    contactId,
    eventId,
    occurrenceDate:
      options.occurrenceDate ?? (event ? eventOccurrenceLocalDateKey(event, new Date(metadata.occurredAt)) : undefined),
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
    scheduledFor: schedule.scheduledFor,
    quality,
    readiness: schedule.issue
      ? 'Schedule needs review before approval'
      : options.feedback
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
    lastError: schedule.issue ?? options.fallbackReason
  };
};

const updateMessage = (state: AppState, messageId: string, updater: (message: MessageDraft) => MessageDraft) =>
  state.messages.map(message => (message.id === messageId ? updater(message) : message));

const clearApprovalMetadata = (message: MessageDraft): MessageDraft => {
  const {
    approvedAt: _approvedAt,
    approvalExpiresAt: _approvalExpiresAt,
    scheduledTimeZone: _scheduledTimeZone,
    duplicateAcknowledged: _duplicateAcknowledged,
    duplicateAcknowledgementFingerprint: _duplicateAcknowledgementFingerprint,
    ...withoutApproval
  } = message;
  return withoutApproval;
};

const withCurrentDuplicateRisk = (state: AppState, message: MessageDraft): MessageDraft => {
  const assessment = assessDuplicateMessageRisk(state, message);
  if (!assessment.risk.risk) {
    const {
      duplicateWarning: _duplicateWarning,
      duplicateAcknowledged: _duplicateAcknowledged,
      duplicateAcknowledgementFingerprint: _duplicateAcknowledgementFingerprint,
      ...withoutDuplicateRisk
    } = message;
    return withoutDuplicateRisk;
  }
  if (!assessment.acknowledged) {
    const {
      duplicateAcknowledged: _duplicateAcknowledged,
      duplicateAcknowledgementFingerprint: _duplicateAcknowledgementFingerprint,
      ...withoutStaleAcknowledgement
    } = message;
    return {
      ...withoutStaleAcknowledgement,
      duplicateWarning: assessment.risk.message
    };
  }
  return {
    ...message,
    duplicateWarning: assessment.risk.message,
    duplicateAcknowledged: true,
    duplicateAcknowledgementFingerprint: assessment.fingerprint
  };
};

type DraftInsertionResult = { ok: true; draft: MessageDraft; messages: MessageDraft[] } | { ok: false; reason: string };

const insertDraftForReview = (
  state: AppState,
  draft: MessageDraft,
  regenerationSource?: MessageRegenerationSource
): DraftInsertionResult => {
  let riskState = state;
  if (regenerationSource) {
    const source = state.messages.find(message => message.id === regenerationSource.messageId);
    if (
      !source ||
      messageDraftRevision(source) !== regenerationSource.expectedRevision ||
      !['Needs review', 'Draft', 'Blocked', 'Failed'].includes(source.status) ||
      source.contactId !== draft.contactId ||
      source.eventId !== draft.eventId ||
      source.reason !== draft.reason ||
      (source.eventId !== undefined && messageOccurrenceDate(state, source) !== draft.occurrenceDate)
    ) {
      return {
        ok: false,
        reason: 'The source draft changed while regeneration was running. Review the current draft and try again.'
      };
    }
    const clearedSource = clearApprovalMetadata(source);
    const { duplicateWarning: _duplicateWarning, lastError: _lastError, ...sourceHistory } = clearedSource;
    riskState = {
      ...state,
      messages: state.messages.map(message =>
        message.id === source.id
          ? {
              ...sourceHistory,
              status: 'Rejected',
              readiness: 'Superseded by regenerated draft'
            }
          : message
      )
    };
  }

  const duplicateRisk = detectDuplicateMessageRisk(riskState, draft);
  const reviewedDraft = duplicateRisk.risk
    ? {
        ...draft,
        duplicateWarning: duplicateRisk.message,
        readiness: 'Duplicate risk needs review before approval'
      }
    : draft;
  return { ok: true, draft: reviewedDraft, messages: [reviewedDraft, ...riskState.messages] };
};

const withChangedMessageBody = (state: AppState, message: MessageDraft, body: string): MessageDraft => {
  const changed = clearApprovalMetadata({
    ...message,
    body
  });
  return withCurrentDuplicateRisk(state, changed);
};

const DND_APPROVAL_WARNING =
  'Contact is in do-not-disturb. Disable DND or complete a deliberate manual handoff before scheduling.';

const approveMessageTransition = (
  state: AppState,
  message: MessageDraft,
  routeIssue: string | undefined,
  approvedAtIso: string
): MessageDraft => {
  let scheduledMessage = message;
  let scheduleIssue: string | undefined;
  if (message.eventId) {
    const event = state.events.find(item => item.id === message.eventId && item.contactId === message.contactId);
    if (!event) {
      scheduleIssue = 'The linked event is no longer valid for this recipient. Return the message to review.';
    } else {
      const approvalTime = new Date(approvedAtIso);
      const currentOccurrence = eventOccurrenceLocalDateKey(event, approvalTime);
      if (message.occurrenceDate && message.occurrenceDate !== currentOccurrence) {
        scheduleIssue =
          'This draft targets an event occurrence that has passed. Regenerate it for the current occurrence before approval.';
      } else {
        const contact = state.contacts.find(item => item.id === message.contactId);
        const preferences = contact ? resolveContactPreferencesForContact(state.settings, contact) : undefined;
        const schedule = scheduleMessageForEvent(event, state.settings, message.channel, approvalTime, {
          customSendTime: preferences?.customSendTime,
          quietHoursBehavior: preferences?.quietHoursBehavior
        });
        scheduleIssue = schedule.issue;
        scheduledMessage = {
          ...message,
          occurrenceDate: message.occurrenceDate ?? currentOccurrence,
          scheduledFor: schedule.scheduledFor,
          scheduledTimeZone: schedule.scheduledTimeZone
        };
      }
    }
  } else if (message.scheduledFor) {
    scheduleIssue =
      'This draft has a scheduled time without event context. Clear the stale time or link an event before approval.';
  }
  const bodyPolicy = validateMessageBodyForChannel(scheduledMessage);
  if (!bodyPolicy.ok) {
    return {
      ...clearApprovalMetadata(scheduledMessage),
      status: 'Blocked',
      readiness: bodyPolicy.message,
      lastError: bodyPolicy.message
    };
  }
  if (scheduledMessage.duplicateWarning && !scheduledMessage.duplicateAcknowledged) {
    return {
      ...clearApprovalMetadata(scheduledMessage),
      status: 'Blocked',
      readiness: 'Explicitly continue or edit before approval',
      lastError: scheduledMessage.duplicateWarning
    };
  }
  if (scheduleIssue) {
    return {
      ...clearApprovalMetadata(scheduledMessage),
      status: 'Blocked',
      readiness: 'Fix schedule before approval',
      lastError: scheduleIssue
    };
  }
  if (routeIssue) {
    const blockedByDnd = /do-not-disturb/i.test(routeIssue);
    return {
      ...clearApprovalMetadata(scheduledMessage),
      status: 'Blocked',
      readiness: blockedByDnd ? 'Blocked by contact do-not-disturb' : 'Fix delivery route before approval',
      lastError: blockedByDnd ? DND_APPROVAL_WARNING : routeIssue
    };
  }
  return {
    ...scheduledMessage,
    ...buildMessageApprovalWindow(approvedAtIso),
    status: 'Scheduled',
    readiness:
      bodyPolicy.warning ??
      (state.contacts.find(contact => contact.id === scheduledMessage.contactId)?.dnd
        ? 'Contact do-not-disturb: approved only for a deliberate manual handoff'
        : scheduledMessage.channel === 'Manual'
          ? 'Ready for manual handoff'
          : 'Approved and scheduled'),
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
  channel: MessageChannel,
  reason = channelDisabledReviewWarning(channel)
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
        lastError: reason
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
      const policy = Number.isNaN(scheduledFor.getTime())
        ? undefined
        : adjustTriggerForSchedulingPolicy(scheduledFor, settings, message.channel);
      const adjustments = Number.isNaN(scheduledFor.getTime())
        ? ['Scheduled time is invalid.']
        : policy?.blockedBy
          ? [`Blocked by blackout: ${policy.blockedBy}.`]
          : (policy?.adjustments ?? []);
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

const TIME_ZONE_REVIEW_WARNING =
  'The saved schedule time zone is missing or no longer matches this device. Review and approve again to recalculate the intended local send time.';

const markChangedTimeZoneSchedulesForReview = (
  messages: MessageDraft[],
  currentTimeZone: string
): { messages: MessageDraft[]; count: number } => {
  let count = 0;
  return {
    messages: messages.map(message => {
      if (
        message.status !== 'Scheduled' ||
        !message.scheduledFor ||
        scheduleTimeZonesMatch(message.scheduledTimeZone, currentTimeZone)
      ) {
        return message;
      }
      count += 1;
      const {
        scheduledFor: _scheduledFor,
        scheduledTimeZone: _scheduledTimeZone,
        ...withoutStaleSchedule
      } = clearApprovalMetadata(message);
      return {
        ...withoutStaleSchedule,
        status: 'Needs review',
        readiness: 'Review after device time-zone change',
        lastError: TIME_ZONE_REVIEW_WARNING
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
  state: AppState,
  message: MessageDraft,
  action: MessageBulkAction,
  routeIssue: string | undefined,
  approvedAtIso: string
): MessageDraft => {
  switch (action) {
    case 'Approve': {
      const currentMessage = withCurrentDuplicateRisk(state, message);
      return approveMessageTransition(state, currentMessage, routeIssue, approvedAtIso);
    }
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

const manualHandoffIssue = (state: AppState, message: MessageDraft, nowIsoValue: string, shareFallbackUsed = false) => {
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
  const duplicateRisk = assessDuplicateMessageRisk(state, message);
  if (duplicateRisk.risk.risk && !duplicateRisk.acknowledged) {
    return 'Duplicate risk changed after approval. Review and explicitly acknowledge the current warning before sending.';
  }
  return (
    messageApprovalRouteIssue(state, message, {
      allowDndManualControl: true,
      allowShareFallback: shareFallbackUsed
    }) ?? messageDispatchTimingIssue(state, message, new Date(nowIsoValue))
  );
};

const bulkActivityTitle = (action: MessageBulkAction, skippedCount: number) => {
  const suffix = skippedCount > 0 ? 'partially applied' : 'applied';
  return `Bulk ${action.toLowerCase()} ${suffix}`;
};

const normalizeLoadedState = (loadedState: AppState): AppState => {
  const defaults = createProductionInitialState();
  const normalizedGroupDefaults = normalizeRelationshipGroupDefaults(loadedState.settings?.groupDefaults);
  const safeGroupDefaults = Object.fromEntries(
    Object.entries(normalizedGroupDefaults).map(([group, groupDefaults]) => {
      const restoredMode = loadedState.settings?.groupDefaults?.[group as RelationshipGroup]?.automationMode;
      return [
        group,
        {
          ...groupDefaults,
          automationMode:
            restoredMode !== undefined && !isAutomationModeAvailable(restoredMode)
              ? 'Always ask'
              : isAutomationModeAvailable(groupDefaults.automationMode)
                ? groupDefaults.automationMode
                : 'Always ask'
        }
      ];
    })
  ) as AppState['settings']['groupDefaults'];
  return {
    ...defaults,
    ...loadedState,
    styleProfile: {
      ...defaults.styleProfile,
      ...loadedState.styleProfile,
      commonGreetings: Array.isArray(loadedState.styleProfile?.commonGreetings)
        ? loadedState.styleProfile.commonGreetings.slice(0, 5)
        : defaults.styleProfile.commonGreetings
    },
    contacts: loadedState.contacts.map(contact => {
      const { customSendTime, ...withoutCustomSendTime } = contact;
      return {
        ...withoutCustomSendTime,
        ...(customSendTime && !validateDefaultSendTime(customSendTime) ? { customSendTime } : {}),
        quietHoursBehavior: contact.quietHoursBehavior === 'Block' ? 'Block' : 'Defer',
        skipAuto: contact.skipAuto ?? false,
        ...(contact.preferenceOverrides?.automationMode &&
        !isAutomationModeAvailable(contact.preferenceOverrides.automationMode)
          ? {
              preferenceOverrides: {
                ...contact.preferenceOverrides,
                automationMode: 'Always ask' as const
              }
            }
          : {})
      };
    }),
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
      groupDefaults: safeGroupDefaults,
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

const createClearedLocalState = (previousState: AppState, metadata: CommandMetadata): AppState => {
  const cleared = createProductionInitialState();
  const next: AppState = {
    ...cleared,
    activeScreen: 'onboarding',
    selectedContactId: undefined,
    selectedEventId: undefined,
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
      localDataClearConfirmedAt: metadata.occurredAt
    },
    persistence: {
      status: 'Ready'
    }
  };
  return {
    ...next,
    activity: addActivity(
      metadata,
      next,
      'Setup',
      'Local data cleared',
      'Contacts, events, messages, memories, gifts, and backups were cleared.'
    )
  };
};

export const relateTransition = (state: AppState, action: EnrichedRelateAction): AppState => {
  switch (action.type) {
    case 'hydrate':
      return normalizeLoadedState(action.state);
    case 'reconcileScheduledMessageTimeZone': {
      const currentTimeZone = normalizeScheduleTimeZone(action.timeZone);
      if (!currentTimeZone) return state;
      const reviewUpdate = markChangedTimeZoneSchedulesForReview(state.messages, currentTimeZone);
      if (reviewUpdate.count === 0) return state;
      return {
        ...state,
        messages: reviewUpdate.messages,
        activity: addActivity(
          action.metadata,
          state,
          'Message',
          'Scheduled messages need time review',
          `${reviewUpdate.count} scheduled message(s) returned to review after a device time-zone change. No message was sent; approve again to recalculate the intended local time.`,
          'Warning',
          {
            targetScreen: 'messages',
            actionLabel: 'Review messages'
          }
        )
      };
    }
    case 'navigate':
      return {
        ...state,
        activeScreen: action.screen,
        selectedContactId: action.contactId,
        selectedEventId: action.eventId,
        selectedMessageId: action.messageId
      };
    case 'setSearch':
      return { ...state, searchQuery: action.query };
    case 'resolveActivity': {
      const target = state.activity.find(item => item.id === action.activityId);
      const row = target ? buildActivityHistory([target], { state }).rows[0] : undefined;
      if (!target || row?.status !== 'Open') return state;
      const resolvedActivity = state.activity.map(item =>
        item.id === target.id
          ? {
              ...item,
              status: 'Resolved' as const,
              resolvedAt: action.metadata.occurredAt
            }
          : item
      );
      return {
        ...state,
        activity: addActivity(
          action.metadata,
          { activity: resolvedActivity },
          'Setup',
          'Activity issue resolved',
          'An open Activity History issue was marked resolved by the user.',
          'Info',
          {
            targetScreen: 'activityHistory',
            actionLabel: 'View activity'
          }
        )
      };
    }
    case 'addContact': {
      const result = buildStandaloneContact(state, action.input, commandId(action.metadata, 'contact'));
      if (!result.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Contact', 'Contact not added', result.reason, 'Warning', {
            targetScreen: 'contacts',
            actionLabel: 'Review contacts'
          })
        };
      }
      return {
        ...state,
        activeScreen: 'contactDetail',
        selectedContactId: result.contact.id,
        contacts: [result.contact, ...state.contacts],
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Contact added',
          'A standalone local contact was added and is ready for profile review.',
          'Info',
          {
            targetScreen: 'contactDetail',
            contactId: result.contact.id,
            actionLabel: 'Open contact'
          }
        )
      };
    }
    case 'editContact': {
      const preview = previewContactEdit(state, action.contactId, action.input);
      if (!preview.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Contact', 'Contact not changed', preview.reason, 'Warning')
        };
      }
      if (preview.exactIdentityCandidateIds.length > 0) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Contact change needs identity review',
            'The edited phone or email already belongs to another contact. Merge the contacts or keep the existing identity unchanged.',
            'Warning',
            { targetScreen: 'contactDetail', contactId: action.contactId, actionLabel: 'Review identity conflict' }
          )
        };
      }
      if (preview.confirmationToken !== action.confirmationToken) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Contact change needs review',
            'The contact or its linked records changed after preview. Review the impact again before saving.',
            'Warning',
            { targetScreen: 'contactDetail', contactId: action.contactId, actionLabel: 'Review contact' }
          )
        };
      }
      const next = applyContactEdit(state, action.contactId, preview.normalized);
      return {
        ...next,
        activity: addActivity(
          action.metadata,
          next,
          'Contact',
          'Contact saved after review',
          `${preview.changedFields.length} field(s) changed. ${preview.impact.activeMessageCount} active message(s) were rechecked.`,
          'Info',
          { targetScreen: 'contactDetail', contactId: action.contactId, actionLabel: 'Open contact' }
        )
      };
    }
    case 'archiveContact': {
      const preview = previewContactArchive(state, action.contactId);
      if (!preview.ok || preview.confirmationToken !== action.confirmationToken) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Contact not archived',
            preview.ok
              ? 'The contact or its linked records changed after preview. Review the impact again.'
              : preview.reason,
            'Warning'
          )
        };
      }
      const next = applyContactArchive(state, action.contactId, action.metadata.occurredAt);
      return {
        ...next,
        activity: addActivity(
          action.metadata,
          next,
          'Contact',
          'Contact archived',
          `Relationship history was preserved. ${preview.impact.reminderCount} reminder(s) were removed and ${preview.impact.activeMessageCount} active message(s) require review.`
        )
      };
    }
    case 'restoreContact': {
      const contact = state.contacts.find(item => item.id === action.contactId);
      if (!contact?.archivedAt) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Contact not restored',
            contact ? 'Contact is not archived.' : 'Contact could not be found.',
            'Warning'
          )
        };
      }
      const next = applyContactRestore(state, action.contactId);
      return {
        ...next,
        activity: addActivity(
          action.metadata,
          next,
          'Contact',
          'Contact restored',
          'The contact is active again. Reminder planning remains review-controlled.',
          'Info',
          { targetScreen: 'contactDetail', contactId: action.contactId, actionLabel: 'Open contact' }
        )
      };
    }
    case 'deleteContact': {
      const preview = previewContactDelete(state, action.contactId);
      if (!preview.ok || preview.confirmationToken !== action.confirmationToken) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Contact not deleted',
            preview.ok
              ? 'The contact or its linked records changed after preview. Review the impact again.'
              : preview.reason,
            'Warning'
          )
        };
      }
      if (!preview.deletionAllowed) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Contact deletion blocked',
            `${preview.relationshipHistoryCount} relationship-history record(s) must be preserved. Archive this contact instead.`,
            'Warning',
            { targetScreen: 'contactDetail', contactId: action.contactId, actionLabel: 'Review archive' }
          )
        };
      }
      const next = applyContactDelete(state, action.contactId);
      return {
        ...next,
        activity: addActivity(
          action.metadata,
          next,
          'Contact',
          'Contact deleted',
          `${preview.impact.eventCount} event(s), ${preview.impact.reminderCount} reminder(s), and ${preview.impact.activeMessageCount} active message(s) were removed after confirmation.`
        )
      };
    }
    case 'mergeContacts': {
      const preview = previewContactMerge(state, action.survivorContactId, action.mergedContactId);
      if (!preview.ok || preview.confirmationToken !== action.confirmationToken) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Contacts not merged',
            preview.ok
              ? 'The contacts or their linked records changed after preview. Review both profiles again.'
              : preview.reason,
            'Warning'
          )
        };
      }
      const next = applyContactMerge(state, action.survivorContactId, action.mergedContactId);
      return {
        ...next,
        activeScreen: 'contactDetail',
        selectedContactId: action.survivorContactId,
        activity: addActivity(
          action.metadata,
          next,
          'Contact',
          'Contacts merged after review',
          `${preview.impact.eventCount} event(s), ${preview.impact.activeMessageCount + preview.impact.historyMessageCount} message(s), ${preview.impact.memoryCount} memory record(s), and ${preview.impact.giftCount} gift record(s) were preserved.`,
          'Info',
          {
            targetScreen: 'contactDetail',
            contactId: action.survivorContactId,
            actionLabel: 'Open merged contact'
          }
        )
      };
    }
    case 'toggleChecklist':
      return {
        ...state,
        events: state.events.map(event =>
          event.id === action.eventId
            ? {
                ...event,
                checklist: toggleEventChecklistItemForOccurrence(event, action.itemId, action.metadata.localDate)
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
                checklist: toggleEventPreparationChecklistStep(
                  event.type,
                  event.checklist,
                  action.stepId,
                  eventPreparationOccurrenceKey(event, action.metadata.localDate),
                  event.date.slice(0, 10)
                )
              }
            : event
        )
      };
    case 'editEvent': {
      const preview = previewEventEdit(state, action.eventId, action.input);
      if (!preview.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Event', 'Event not changed', preview.reason, 'Warning')
        };
      }
      if (preview.requiresConfirmation && preview.confirmationToken !== action.confirmationToken) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Event',
            'Event change needs review',
            'Linked reminders, messages, calendar state, or conflicts changed. Review the event impact again.',
            'Warning',
            { targetScreen: 'events', actionLabel: 'Review event' }
          )
        };
      }
      const next = applyEventEdit(state, action.eventId, preview.normalized);
      return {
        ...next,
        activeScreen: 'events',
        selectedContactId: preview.normalized.contactId,
        activity: addActivity(
          action.metadata,
          next,
          'Event',
          'Event changed after review',
          `${preview.changedFields.length} field(s) changed. ${preview.impact.reminderCount} reminder(s) were cleared for reconciliation and ${preview.impact.activeMessageCount} active message(s) require review.`
        )
      };
    }
    case 'deleteEvent': {
      const preview = previewEventDelete(state, action.eventId);
      if (!preview.ok || preview.confirmationToken !== action.confirmationToken) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Event',
            'Event not deleted',
            preview.ok
              ? 'The event or its linked records changed after preview. Review the impact again.'
              : preview.reason,
            'Warning'
          )
        };
      }
      const next = applyEventDelete(state, action.eventId);
      return {
        ...next,
        activeScreen: 'events',
        activity: addActivity(
          action.metadata,
          next,
          'Event',
          'Event deleted',
          `${preview.impact.reminderCount} reminder(s) were removed. ${preview.impact.activeMessageCount + preview.impact.historyMessageCount} linked message(s) were preserved without the deleted event reference.`
        )
      };
    }
    case 'mergeEvents': {
      const preview = previewEventMerge(state, action.survivorEventId, action.mergedEventId);
      if (!preview.ok || preview.confirmationToken !== action.confirmationToken) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Event',
            'Event merge needs review',
            preview.ok ? 'The event conflict changed. Review the impact again.' : preview.reason,
            'Warning'
          )
        };
      }
      const next = applyEventMerge(state, action.survivorEventId, action.mergedEventId);
      return {
        ...next,
        activity: addActivity(
          action.metadata,
          next,
          'Event',
          'Event conflicts merged',
          `${preview.activeMessageCount} active message(s) require review and ${preview.reminderCount} reminder(s) were cleared for reconciliation.`
        )
      };
    }
    case 'addManualEvent': {
      const validation = validateManualEventInput(action, state.contacts, state.events);
      if (!validation.ok) {
        return {
          ...state,
          activeScreen: 'eventForm',
          activity: addActivity(
            action.metadata,
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
            action.metadata,
            state,
            'Event',
            'Review event conflict',
            validation.warnings.join(' '),
            'Warning'
          )
        };
      }

      const createdContact: Contact | undefined = validation.normalized.contactId
        ? undefined
        : {
            id: commandId(action.metadata, 'contact'),
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
          activity: addActivity(action.metadata, state, 'Event', 'Event not saved', 'A contact is required.', 'Warning')
        };
      }

      const event = {
        id: commandId(action.metadata, 'event'),
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
          action.metadata,
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
          if (messageLifecycleTransitionIssue(message, 'select-variant')) {
            return message;
          }
          if (message.selectedVariant === action.variant) {
            return message;
          }
          const hasEditedBody = message.body !== message.variants[message.selectedVariant];
          if (hasEditedBody && !action.discardEditedBody) {
            return message;
          }
          return withChangedMessageBody(
            state,
            {
              ...message,
              selectedVariant: action.variant
            },
            message.variants[action.variant]
          );
        })
      };
    case 'editMessage':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => {
          if (messageLifecycleTransitionIssue(message, 'edit') || message.body === action.body) {
            return message;
          }
          const nextMessage = withChangedMessageBody(state, message, action.body);
          const bodyPolicy = validateMessageBodyForChannel(nextMessage);
          return {
            ...nextMessage,
            readiness: !bodyPolicy.ok ? bodyPolicy.message : (bodyPolicy.warning ?? message.readiness)
          };
        })
      };
    case 'setMessageChannel': {
      const target = state.messages.find(message => message.id === action.messageId);
      if (!target || messageLifecycleTransitionIssue(target, 'edit') || target.channel === action.channel) {
        return state;
      }
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => {
          const { scheduledFor: _scheduledFor, ...withoutStaleSchedule } = clearApprovalMetadata(message);
          const changed = withCurrentDuplicateRisk(state, {
            ...withoutStaleSchedule,
            channel: action.channel,
            status: 'Needs review'
          });
          const bodyPolicy = validateMessageBodyForChannel(changed);
          return {
            ...changed,
            readiness: bodyPolicy.ok
              ? (bodyPolicy.warning ?? 'Channel changed; review before approval')
              : bodyPolicy.message,
            lastError: bodyPolicy.ok ? undefined : bodyPolicy.message
          };
        }),
        activity: addActivity(
          action.metadata,
          state,
          'Message',
          'Message channel changed',
          `Channel changed to ${action.channel}. Review the message before approval.`
        )
      };
    }
    case 'generateMessage': {
      if ((action.feedback === undefined) !== (action.regenerationSource === undefined)) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'AI',
            'Message regeneration not applied',
            'Regeneration must be bound to the exact active source draft.',
            'Warning'
          )
        };
      }
      const regenerationSourceMessage = action.regenerationSource
        ? state.messages.find(message => message.id === action.regenerationSource?.messageId)
        : undefined;
      const generationContact = findContact(state, action.contactId);
      if (
        action.generationOrigin === 'Automatic' &&
        generationContact &&
        !contactAllowsAutomaticDraftGeneration(generationContact)
      ) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'AI',
            'Automatic draft skipped',
            'This contact opted out of proactive draft generation. A user-requested draft remains available.',
            'Info'
          )
        };
      }
      const draft = buildMessageDraft(action.metadata, state, action.contactId, action.eventId, action.reason, {
        fallbackReason: action.fallbackReason,
        contextOptions: {
          excludedMemoryIds: action.excludedMemoryIds,
          includePriorMessages: action.includePriorMessages
        },
        feedback: action.feedback,
        occurrenceDate: regenerationSourceMessage ? messageOccurrenceDate(state, regenerationSourceMessage) : undefined
      });
      if (!draft) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'AI',
            'Draft not created',
            action.eventId
              ? 'The selected active contact and event are unavailable or no longer belong together.'
              : 'The selected active contact could not be found.',
            'Error'
          )
        };
      }
      const insertion = insertDraftForReview(state, draft, action.regenerationSource);
      if (!insertion.ok) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'AI',
            'Message regeneration not applied',
            insertion.reason,
            'Warning'
          )
        };
      }
      const reviewedDraft = insertion.draft;
      return {
        ...state,
        messages: insertion.messages,
        activeScreen: 'wishPreview',
        selectedMessageId: reviewedDraft.id,
        selectedContactId: reviewedDraft.contactId,
        activity: addActivity(
          action.metadata,
          state,
          'AI',
          action.feedback
            ? action.fallbackReason
              ? 'Message regeneration fallback created'
              : 'Draft regenerated'
            : action.fallbackReason
              ? 'Template fallback created'
              : 'Draft created',
          action.fallbackReason ??
            (action.feedback
              ? `${draft.reason} draft was regenerated with feedback and is ready for review.`
              : `${draft.reason} draft is ready for review.`),
          action.fallbackReason ? 'Warning' : 'Info'
        )
      };
    }
    case 'createTemplateDraft': {
      const result = buildTemplateDraft(state, action, commandId(action.metadata, 'message'));
      if (!result.ok) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            'Template draft not created',
            result.reason,
            'Warning'
          )
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
          action.metadata,
          state,
          'Message',
          'Template draft created',
          `${reviewedDraft.reason} template is ready for review.`
        )
      };
    }
    case 'createAiDraft': {
      if ((action.feedback === undefined) !== (action.regenerationSource === undefined)) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'AI',
            'Message regeneration not applied',
            'Regeneration must be bound to the exact active source draft.',
            'Warning'
          )
        };
      }
      const regenerationSourceMessage = action.regenerationSource
        ? state.messages.find(message => message.id === action.regenerationSource?.messageId)
        : undefined;
      const generationContact = findContact(state, action.contactId);
      if (
        action.generationOrigin === 'Automatic' &&
        generationContact &&
        !contactAllowsAutomaticDraftGeneration(generationContact)
      ) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'AI',
            'Automatic draft skipped',
            'This contact opted out of proactive draft generation. A user-requested draft remains available.',
            'Info'
          )
        };
      }
      const draft = buildMessageDraft(action.metadata, state, action.contactId, action.eventId, action.reason, {
        providerVariants: action.variants,
        feedback: action.feedback,
        occurrenceDate: regenerationSourceMessage ? messageOccurrenceDate(state, regenerationSourceMessage) : undefined
      });
      if (!draft) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'AI',
            'AI draft not created',
            'The selected contact could not be found.',
            'Error'
          )
        };
      }
      const insertion = insertDraftForReview(state, draft, action.regenerationSource);
      if (!insertion.ok) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'AI',
            'Message regeneration not applied',
            insertion.reason,
            'Warning'
          )
        };
      }
      const reviewedDraft = insertion.draft;
      return {
        ...state,
        messages: insertion.messages,
        activeScreen: 'wishPreview',
        selectedMessageId: reviewedDraft.id,
        selectedContactId: reviewedDraft.contactId,
        aiProvider: {
          status: 'Ready',
          lastCheckedAt: action.metadata.occurredAt,
          lastPrivacySummary: action.privacySummary,
          lastObservation: action.observation
        },
        activity: addActivity(
          action.metadata,
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
          lastCheckedAt: action.metadata.occurredAt,
          lastPrivacySummary: action.privacySummary,
          lastObservation: action.observation
        },
        activity: addActivity(action.metadata, state, 'AI', 'AI provider ready', action.privacySummary)
      };
    case 'aiProviderFailure':
      return {
        ...state,
        aiProvider: {
          status: 'Error',
          lastCheckedAt: action.metadata.occurredAt,
          lastError: action.error.message,
          lastPrivacySummary: action.privacySummary,
          lastObservation: action.observation
        },
        activity: addActivity(action.metadata, state, 'AI', 'AI provider unavailable', action.error.message, 'Warning')
      };
    case 'approveMessage': {
      const message = state.messages.find(item => item.id === action.messageId);
      if (!message) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            'Message not approved',
            'This message is no longer available.',
            'Warning'
          )
        };
      }
      const lifecycleIssue = messageLifecycleTransitionIssue(message, 'approve');
      if (lifecycleIssue) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Message', 'Message not approved', lifecycleIssue, 'Warning', {
            targetScreen: 'wishPreview',
            messageId: message.id,
            contactId: message.contactId,
            actionLabel: 'Review message'
          })
        };
      }
      const currentMessage = withCurrentDuplicateRisk(state, message);
      const approvedMessage = approveMessageTransition(
        state,
        currentMessage,
        messageApprovalRouteIssue(state, currentMessage, { allowDndManualControl: true }),
        action.metadata.occurredAt
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
          action.metadata,
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
    case 'acknowledgeDuplicateRisk': {
      const message = state.messages.find(item => item.id === action.messageId);
      const lifecycleIssue = message
        ? messageLifecycleTransitionIssue(message, 'acknowledge-duplicate')
        : 'This message is no longer available.';
      const assessment = message ? assessDuplicateMessageRisk(state, message) : undefined;
      if (!message || lifecycleIssue || !assessment?.risk.risk || !assessment.fingerprint) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            'Duplicate risk not acknowledged',
            lifecycleIssue ?? 'No current duplicate risk requires acknowledgement.',
            'Warning',
            message
              ? {
                  targetScreen: 'wishPreview',
                  messageId: message.id,
                  contactId: message.contactId,
                  actionLabel: 'Review message'
                }
              : undefined
          )
        };
      }
      const wasBlockedByDuplicate =
        message.status === 'Blocked' &&
        (message.lastError === message.duplicateWarning || /similar message/i.test(message.lastError ?? ''));
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          duplicateWarning: assessment.risk.risk ? assessment.risk.message : message.duplicateWarning,
          duplicateAcknowledged: true,
          duplicateAcknowledgementFingerprint: assessment.fingerprint,
          status: wasBlockedByDuplicate ? 'Needs review' : message.status,
          readiness: 'Duplicate risk acknowledged; review once more before approval',
          lastError: wasBlockedByDuplicate ? undefined : message.lastError
        })),
        activity: addActivity(
          action.metadata,
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
    }
    case 'rejectMessage': {
      const message = state.messages.find(item => item.id === action.messageId);
      const lifecycleIssue = message
        ? messageLifecycleTransitionIssue(message, 'reject')
        : 'This message is no longer available.';
      if (!message || lifecycleIssue) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            'Message not rejected',
            lifecycleIssue ?? 'This message is no longer available.',
            'Warning'
          )
        };
      }
      const nextState = {
        ...state,
        messages: updateMessage(state, action.messageId, rejectMessageTransition)
      };
      return {
        ...nextState,
        ...(action.reviewNext ? reviewNextNavigation(nextState, action.messageId) : {}),
        activity: addActivity(
          action.metadata,
          state,
          'Message',
          'Message rejected',
          'The draft will not be sent.',
          'Info',
          {
            targetScreen: 'messages',
            messageId: action.messageId,
            actionLabel: 'Open messages'
          }
        )
      };
    }
    case 'revokeMessage': {
      const message = state.messages.find(item => item.id === action.messageId);
      const lifecycleIssue = message
        ? messageLifecycleTransitionIssue(message, 'revoke')
        : 'This message is no longer available.';
      if (!message || lifecycleIssue) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            'Message approval not revoked',
            lifecycleIssue ?? 'This message is no longer available.',
            'Warning'
          )
        };
      }
      return {
        ...state,
        messages: updateMessage(state, action.messageId, revokeMessageTransition),
        activity: addActivity(
          action.metadata,
          state,
          'Message',
          'Message approval revoked',
          'Review the message before scheduling again.',
          'Info',
          {
            targetScreen: 'wishPreview',
            messageId: action.messageId,
            actionLabel: 'Review message'
          }
        )
      };
    }
    case 'bulkMessageAction': {
      const report = buildMessageBulkActionReport(
        state,
        action.messageIds,
        action.action,
        new Date(action.metadata.occurredAt)
      );
      if (report.eligibleIds.length === 0) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            bulkActivityTitle(action.action, report.skipped.length),
            report.confirmation,
            'Warning'
          )
        };
      }

      const eligibleIds = new Set(report.eligibleIds);
      const approvedAtIso = action.metadata.occurredAt;
      return {
        ...state,
        messages: state.messages.map(message =>
          eligibleIds.has(message.id)
            ? applyBulkMessageTransition(
                state,
                message,
                action.action,
                messageApprovalRouteIssue(state, message),
                approvedAtIso
              )
            : message
        ),
        activity: addActivity(
          action.metadata,
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
            action.metadata,
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
          action.metadata,
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
            action.metadata,
            state,
            'Message',
            'Manual handoff not completed',
            'This message is no longer available.',
            'Warning'
          )
        };
      }
      const completedAt = action.metadata.occurredAt;
      const issue = manualHandoffIssue(state, message, completedAt, action.shareFallbackUsed);
      if (issue) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Message', 'Manual handoff not completed', issue, 'Warning', {
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
          return message?.contactId === contact.id
            ? { ...contact, lastContactedAt: completedAt, healthScore: Math.min(100, contact.healthScore + 5) }
            : contact;
        }),
        activity: addActivity(
          action.metadata,
          state,
          'Message',
          'Manual handoff completed',
          'The user retained final control in the destination app.',
          'Info',
          {
            targetScreen: 'chatHistory',
            messageId: action.messageId,
            contactId: message.contactId,
            actionLabel: 'Open chat history'
          }
        )
      };
    }
    case 'scheduleMessageFollowUp': {
      const plan = buildMessageFollowUpPlan(state, action.messageId, action.delayDays, action.metadata.occurredAt, {
        eventId: commandId(action.metadata, 'event'),
        reminderId: commandId(action.metadata, 'reminder')
      });
      if (!plan.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Event', 'Follow-up not scheduled', plan.reason, 'Warning')
        };
      }

      return {
        ...state,
        activeScreen: 'events',
        selectedContactId: plan.event.contactId,
        events: [plan.event, ...state.events],
        reminderPlans: [plan.reminderPlan, ...state.reminderPlans],
        activity: addActivity(
          action.metadata,
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
            action.metadata,
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
        activity: addActivity(
          action.metadata,
          state,
          'Message',
          'Message retry prepared',
          'Review the message before retrying.',
          'Info',
          {
            targetScreen: 'wishPreview',
            messageId: action.messageId,
            actionLabel: 'Review retry'
          }
        )
      };
    }
    case 'addMemory': {
      const validation = validateMemoryNoteInput(state, action.contactId, action.body);
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Memory', 'Memory not saved', validation.message, 'Warning')
        };
      }
      return {
        ...state,
        memories: [
          {
            id: commandId(action.metadata, 'memory'),
            contactId: action.contactId,
            category: action.category,
            body: validation.value.body,
            pinned: action.category !== 'Private',
            createdAt: action.metadata.occurredAt
          },
          ...state.memories
        ],
        activity: addActivity(
          action.metadata,
          state,
          'Memory',
          'Memory saved',
          action.category === 'Private'
            ? 'Private memory is excluded from AI context.'
            : 'Memory can improve future drafts.'
        )
      };
    }
    case 'editMemory': {
      const memory = state.memories.find(item => item.id === action.memoryId);
      if (!memory) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Memory',
            'Memory not updated',
            'This note is no longer available.',
            'Warning'
          )
        };
      }
      const validation = validateMemoryNoteInput(state, memory.contactId, action.body);
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Memory', 'Memory not updated', validation.message, 'Warning')
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
          action.metadata,
          state,
          'Memory',
          'Memory updated',
          action.category === 'Private'
            ? 'Private memory is excluded from AI context.'
            : 'Memory can improve future drafts.'
        )
      };
    }
    case 'toggleMemoryPin': {
      const memory = state.memories.find(item => item.id === action.memoryId);
      if (!memory) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Memory',
            'Memory not pinned',
            'This note is no longer available.',
            'Warning'
          )
        };
      }
      return {
        ...state,
        memories: state.memories.map(item => (item.id === action.memoryId ? { ...item, pinned: !item.pinned } : item)),
        activity: addActivity(
          action.metadata,
          state,
          'Memory',
          memory.pinned ? 'Memory unpinned' : 'Memory pinned',
          memory.pinned
            ? 'The note remains searchable in recent memories.'
            : 'Pinned notes appear first in Memory Vault.'
        )
      };
    }
    case 'deleteMemory': {
      const memory = state.memories.find(item => item.id === action.memoryId);
      if (!memory) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Memory',
            'Memory not deleted',
            'This note is no longer available.',
            'Warning'
          )
        };
      }
      return {
        ...state,
        memories: state.memories.filter(item => item.id !== action.memoryId),
        activity: addActivity(
          action.metadata,
          state,
          'Memory',
          'Memory deleted',
          'The note was removed from this contact.'
        )
      };
    }
    case 'answerEnrichmentPrompt': {
      const contact = findContact(state, action.contactId);
      if (!contact) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Memory',
            'Enrichment not saved',
            'Contact could not be found.',
            'Warning'
          )
        };
      }
      const prompt = resolveContactEnrichmentPrompt(state, action.contactId, action.promptId);
      if (!prompt) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
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
          activity: addActivity(action.metadata, state, 'Memory', 'Enrichment not saved', validation.message, 'Warning')
        };
      }
      return {
        ...state,
        contacts: state.contacts.map(item =>
          item.id === action.contactId ? { ...item, healthScore: Math.min(100, item.healthScore + 4) } : item
        ),
        memories: [
          {
            id: commandId(action.metadata, 'memory'),
            contactId: action.contactId,
            category: prompt.category,
            body: buildEnrichmentMemoryBody(prompt, validation.value),
            pinned: prompt.category !== 'Private',
            createdAt: action.metadata.occurredAt
          },
          ...state.memories
        ],
        activity: addActivity(
          action.metadata,
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
          activity: addActivity(
            action.metadata,
            state,
            'Gift',
            'Gift not saved',
            'Contact could not be found.',
            'Warning'
          )
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
          activity: addActivity(action.metadata, state, 'Gift', 'Gift not saved', giftValidation.message, 'Warning')
        };
      }
      return {
        ...state,
        gifts: [
          {
            id: commandId(action.metadata, 'gift'),
            contactId: action.contactId,
            name: giftValidation.value.name,
            category: giftValidation.value.category,
            occasion: giftValidation.value.occasion,
            cost: giftValidation.value.cost,
            year: localCalendarYear(action.metadata.localDate),
            feedback: giftValidation.value.feedback ?? 'Unknown',
            notes: giftValidation.value.notes || 'Recorded from Gift Advisor.'
          },
          ...state.gifts
        ],
        activity: addActivity(
          action.metadata,
          state,
          'Gift',
          'Gift saved',
          `${giftValidation.value.name} was added to gift history.`
        )
      };
    case 'deleteGift': {
      const gift = state.gifts.find(item => item.id === action.giftId);
      if (!gift) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Gift',
            'Gift not deleted',
            'This gift record is no longer available.',
            'Warning'
          )
        };
      }
      return {
        ...state,
        gifts: state.gifts.filter(item => item.id !== action.giftId),
        activity: addActivity(
          action.metadata,
          state,
          'Gift',
          'Gift deleted',
          `${gift.name} was removed from gift history.`
        )
      };
    }
    case 'updateGiftBudget': {
      const contact = findContact(state, action.contactId);
      if (!contact) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Gift',
            'Gift budget not saved',
            'Contact could not be found.',
            'Warning'
          )
        };
      }
      const validation = validateGiftBudgetInput({ annualGiftBudget: action.annualGiftBudget });
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Gift', 'Gift budget not saved', validation.message, 'Warning')
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
          action.metadata,
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
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Contact not saved',
            'Contact could not be found.',
            'Warning'
          )
        };
      }
      const preferences = resolveContactPreferencesForContact(state.settings, contact);
      const validation = validateContactEssentials(action.input, preferences.preferredChannel);
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Contact', 'Contact not saved', validation.message, 'Warning')
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
          action.metadata,
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
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Relationship group updated',
          `Contact moved to ${action.group}.`
        )
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
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Relationship group defaults updated',
          `${action.group} defaults changed.`
        )
      };
    }
    case 'toggleContactVip':
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId ? { ...contact, isVip: !contact.isVip } : contact
        ),
        activity: addActivity(action.metadata, state, 'Contact', 'VIP setting updated', 'Contact priority was changed.')
      };
    case 'toggleContactDnd': {
      const reviewUpdate = markContactMessagesForReview(state.messages, [action.contactId]);
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId ? { ...contact, dnd: !contact.dnd } : contact
        ),
        messages: reviewUpdate.messages,
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Do-not-disturb updated',
          'Contact automation preference was changed.'
        )
      };
    }
    case 'setCheckInCadence':
      if (!checkInCadenceOptions.includes(action.days)) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Check-in cadence not saved',
            'Choose a supported cadence.',
            'Warning'
          )
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
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Check-in cadence updated',
          `Cadence changed to ${action.days} day(s).`
        )
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
            action.metadata,
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
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Contact automation override updated',
          `${action.mode} selected for this contact.`
        )
      };
    case 'setContactCustomSendTime': {
      const contact = findContact(state, action.contactId);
      const validationIssue = action.time === undefined ? undefined : validateDefaultSendTime(action.time);
      if (!contact || validationIssue) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Custom send time not saved',
            validationIssue ?? 'Contact could not be found.',
            'Warning'
          )
        };
      }
      const reviewUpdate = markContactMessagesForReview(state.messages, [contact.id]);
      return {
        ...state,
        contacts: state.contacts.map(item => {
          if (item.id !== contact.id) return item;
          const { customSendTime: _customSendTime, ...withoutCustomSendTime } = item;
          return action.time ? { ...withoutCustomSendTime, customSendTime: action.time } : withoutCustomSendTime;
        }),
        messages: reviewUpdate.messages,
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Custom send time updated',
          action.time
            ? `Event-linked drafts for this contact use ${action.time} before global safety adjustments.`
            : 'Event-linked drafts for this contact now use the global default send time.'
        )
      };
    }
    case 'setContactQuietHoursBehavior': {
      const contact = findContact(state, action.contactId);
      if (!contact || (action.behavior !== 'Defer' && action.behavior !== 'Block')) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Contact',
            'Quiet-hours behavior not saved',
            contact ? 'Choose Defer or Block.' : 'Contact could not be found.',
            'Warning'
          )
        };
      }
      const reviewUpdate = markContactMessagesForReview(state.messages, [contact.id]);
      return {
        ...state,
        contacts: state.contacts.map(item =>
          item.id === contact.id ? { ...item, quietHoursBehavior: action.behavior } : item
        ),
        messages: reviewUpdate.messages,
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Quiet-hours behavior updated',
          action.behavior === 'Block'
            ? 'Intended send times inside global quiet hours are blocked for review.'
            : 'Global quiet hours safely defer intended send times for this contact.'
        )
      };
    }
    case 'setContactSkipAuto': {
      const contact = findContact(state, action.contactId);
      if (!contact) return state;
      return {
        ...state,
        contacts: state.contacts.map(item => (item.id === contact.id ? { ...item, skipAuto: action.enabled } : item)),
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Proactive drafting preference updated',
          action.enabled
            ? 'Automatic draft preparation is skipped; user-requested drafting remains available.'
            : 'Proactive draft preparation is allowed under the active review-first policy.'
        )
      };
    }
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
          return applyResolvedPreferencesToContact(
            inherited,
            resolveContactPreferencesForContact(state.settings, inherited)
          );
        }),
        messages: reviewUpdate.messages,
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Group defaults applied',
          'Contact now inherits group preferences.'
        )
      };
    }
    case 'snoozeCheckIn':
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId
            ? { ...contact, checkInSnoozedUntil: addDaysIso(action.days, action.metadata.occurredAt) }
            : contact
        ),
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Check-in snoozed',
          `Reminder moved by ${action.days} day(s).`
        )
      };
    case 'markContactedElsewhere': {
      const contactedAt = action.metadata.occurredAt;
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
          action.metadata,
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
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Language updated',
          `Locale changed to ${action.locale}.`
        )
      };
    case 'setEmailSender': {
      const senderEmail = normalizeEmailAddress(action.senderEmail);
      if (senderEmail.length > 0 && !isValidEmailAddress(senderEmail)) return state;
      if ((state.emailDelivery.senderEmail ?? '') === senderEmail) return state;
      const senderChanged = normalizeEmailAddress(state.emailDelivery.senderEmail) !== senderEmail;
      const reviewUpdate = senderChanged
        ? markScheduledChannelMessagesForReview(
            state.messages,
            'Email',
            'Email sender configuration changed. Review before scheduling or sending.'
          )
        : { messages: state.messages, count: 0 };
      return {
        ...state,
        messages: reviewUpdate.messages,
        emailDelivery: {
          ...state.emailDelivery,
          senderEmail: senderEmail || undefined,
          status: senderEmail ? state.emailDelivery.status : 'Not configured',
          lastCheckedAt: senderEmail ? state.emailDelivery.lastCheckedAt : undefined,
          lastError: undefined
        },
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Email sender updated',
          reviewUpdate.count > 0
            ? `Email sender configuration changed. ${reviewUpdate.count} scheduled email message(s) returned to review.`
            : 'Email sender configuration changed.',
          reviewUpdate.count > 0 ? 'Warning' : 'Info'
        )
      };
    }
    case 'emailProviderReady':
      return {
        ...state,
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Ready',
          lastCheckedAt: action.metadata.occurredAt,
          lastError: undefined
        },
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Email provider ready',
          'Email delivery endpoint accepted the message.'
        )
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
          lastCheckedAt: action.metadata.occurredAt,
          lastError: action.error.message
        },
        activity: addActivity(
          action.metadata,
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
      const acceptedAt = action.metadata.occurredAt;
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
          action.metadata,
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
      const attemptedAt = action.metadata.occurredAt;
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
          action.metadata,
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
    case 'emailDeliveryReconciled': {
      const message = state.messages.find(item => item.id === action.messageId);
      const attempt = message?.emailDeliveryAttempt;
      if (
        !message ||
        !attempt ||
        attempt.idempotencyKey !== action.idempotencyKey ||
        (message.status !== 'Delivery pending' && message.status !== 'Delivery unknown')
      ) {
        return {
          ...state,
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            'Email reconciliation ignored',
            'The saved delivery attempt changed before reconciliation completed.',
            'Warning'
          )
        };
      }
      const reconciledAt = action.metadata.occurredAt;
      if (action.status === 'accepted') {
        return {
          ...state,
          messages: updateMessage(state, action.messageId, current => ({
            ...current,
            status: 'Delivery pending',
            readiness: 'Provider still reports accepted; delivery remains pending',
            lastError: undefined,
            emailDeliveryAttempt: {
              idempotencyKey: action.idempotencyKey,
              status: 'Accepted',
              deliveryId: action.deliveryId ?? attempt.deliveryId,
              updatedAt: reconciledAt
            }
          })),
          emailDelivery: {
            ...state.emailDelivery,
            status: 'Ready',
            lastCheckedAt: reconciledAt,
            lastError: undefined
          }
        };
      }
      if (action.status === 'failed') {
        return {
          ...state,
          messages: updateMessage(state, action.messageId, current => ({
            ...current,
            status: 'Failed',
            readiness: 'Provider confirmed delivery failure; review before retrying',
            lastError: 'The provider confirmed that this delivery attempt failed.',
            emailDeliveryAttempt: {
              idempotencyKey: action.idempotencyKey,
              status: 'Failed',
              deliveryId: action.deliveryId ?? attempt.deliveryId,
              updatedAt: reconciledAt
            }
          })),
          emailDelivery: {
            ...state.emailDelivery,
            status: 'Error',
            lastCheckedAt: reconciledAt,
            lastError: 'The provider confirmed a delivery failure.'
          },
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            'Email failure reconciled',
            'The provider confirmed failure. A new attempt requires review.',
            'Warning',
            { targetScreen: 'wishPreview', messageId: action.messageId, actionLabel: 'Review email' }
          )
        };
      }
      return {
        ...state,
        messages: updateMessage(state, action.messageId, current => ({
          ...current,
          status: 'Sent',
          readiness: 'Email delivery confirmed by provider reconciliation',
          lastError: undefined,
          sentAt: reconciledAt,
          emailDeliveryAttempt: {
            idempotencyKey: action.idempotencyKey,
            status: 'Sent',
            deliveryId: action.deliveryId ?? attempt.deliveryId,
            updatedAt: reconciledAt
          }
        })),
        contacts: state.contacts.map(contact =>
          contact.id === message.contactId
            ? {
                ...contact,
                lastContactedAt: reconciledAt,
                healthScore: Math.min(100, contact.healthScore + 5)
              }
            : contact
        ),
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Ready',
          lastCheckedAt: reconciledAt,
          lastError: undefined
        },
        activity: addActivity(
          action.metadata,
          state,
          'Message',
          'Email sent status reconciled',
          'The provider confirmed that the idempotent delivery attempt was sent.',
          'Info',
          {
            targetScreen: 'chatHistory',
            messageId: action.messageId,
            contactId: message.contactId,
            actionLabel: 'Open chat history'
          }
        )
      };
    }
    case 'emailSent': {
      const requestedSentAt = action.nowIso ?? action.metadata.occurredAt;
      const sentAt = Number.isNaN(Date.parse(requestedSentAt)) ? action.metadata.occurredAt : requestedSentAt;
      const request = buildEmailDeliveryRequest(state, action.messageId, new Date(sentAt));
      if (!request.ok) {
        return {
          ...state,
          emailDelivery: {
            ...state.emailDelivery,
            status: 'Error',
            lastCheckedAt: action.metadata.occurredAt,
            lastError: request.error.message
          },
          activity: addActivity(
            action.metadata,
            state,
            'Message',
            'Email sent status not recorded',
            request.error.message,
            'Warning',
            {
              targetScreen: 'messages',
              messageId: action.messageId,
              actionLabel: 'Open messages'
            }
          )
        };
      }
      const sentMessage = state.messages.find(item => item.id === action.messageId);
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
        activity: addActivity(
          action.metadata,
          state,
          'Message',
          'Email sent',
          'The approved email was sent by the configured provider.',
          'Info',
          {
            targetScreen: sentMessage ? 'chatHistory' : 'messages',
            messageId: action.messageId,
            contactId: sentMessage?.contactId,
            actionLabel: sentMessage ? 'Open chat history' : 'Open messages'
          }
        )
      };
    }
    case 'setOnboardingGoal': {
      const newlyRequired = requiredOnboardingStepIds(action.goal);
      return {
        ...state,
        onboarding: {
          ...state.onboarding,
          selectedGoal: action.goal,
          skippedStepIds: state.onboarding.skippedStepIds.filter(stepId => !newlyRequired.includes(stepId)),
          lastUpdatedAt: action.metadata.occurredAt
        },
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Onboarding goal updated',
          `Goal changed to ${action.goal}.`
        )
      };
    }
    case 'setOnboardingStep': {
      if (onboardingTransitionIssue(state, { type: 'set-step', stepId: action.stepId })) return state;
      return {
        ...state,
        activeScreen: 'onboarding',
        onboarding: {
          ...state.onboarding,
          currentStepId: action.stepId,
          lastUpdatedAt: action.metadata.occurredAt
        }
      };
    }
    case 'advanceOnboarding': {
      const currentStepId = state.onboarding.currentStepId;
      if (onboardingTransitionIssue(state, { type: 'advance' })) return state;
      if (currentStepId === 'finish') {
        return relateTransition(state, { type: 'completeOnboarding', metadata: action.metadata });
      }
      return {
        ...state,
        onboarding: {
          ...state.onboarding,
          currentStepId: nextOnboardingStep(currentStepId),
          completedStepIds: uniqueSteps([...state.onboarding.completedStepIds, currentStepId]),
          skippedStepIds: state.onboarding.skippedStepIds.filter(stepId => stepId !== currentStepId),
          lastUpdatedAt: action.metadata.occurredAt
        }
      };
    }
    case 'skipOnboardingStep': {
      if (onboardingTransitionIssue(state, { type: 'skip', stepId: action.stepId })) return state;
      return {
        ...state,
        onboarding: {
          ...state.onboarding,
          currentStepId: nextOnboardingStep(action.stepId),
          completedStepIds: state.onboarding.completedStepIds.filter(stepId => stepId !== action.stepId),
          skippedStepIds: uniqueSteps([...state.onboarding.skippedStepIds, action.stepId]),
          lastUpdatedAt: action.metadata.occurredAt
        },
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Onboarding step skipped',
          `${action.stepId} can be completed later.`
        )
      };
    }
    case 'completeOnboarding':
      if (onboardingTransitionIssue(state, { type: 'complete' })) return state;
      return {
        ...state,
        activeScreen: 'home',
        onboarding: {
          ...state.onboarding,
          completed: true,
          currentStepId: 'finish',
          completedStepIds: uniqueSteps([...state.onboarding.completedStepIds, 'finish']),
          skippedStepIds: state.onboarding.skippedStepIds.filter(stepId => stepId !== 'finish'),
          lastUpdatedAt: action.metadata.occurredAt
        },
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Onboarding completed',
          'Home is ready; setup gaps remain available from Settings and Setup Check.'
        )
      };
    case 'reopenOnboarding':
      return {
        ...state,
        activeScreen: 'onboarding',
        onboarding: {
          ...state.onboarding,
          lastUpdatedAt: action.metadata.occurredAt
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
            action.metadata,
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
          lastUpdatedAt: action.metadata.occurredAt
        },
        activity: addActivity(
          action.metadata,
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
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Account disconnected',
          'Provider sync was disconnected while local data was retained.'
        )
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
          action.metadata,
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
          whatsappHandoffConsent: !state.privacy.whatsappHandoffConsent,
          permissionDecisions: {
            ...state.privacy.permissionDecisions,
            'WhatsApp handoff': state.privacy.whatsappHandoffConsent ? 'Denied' : 'Granted'
          }
        },
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Manual WhatsApp handoff consent updated',
          state.privacy.whatsappHandoffConsent
            ? 'Manual WhatsApp handoff consent was revoked.'
            : 'Manual WhatsApp handoff consent was granted for approved handoff only.'
        )
      };
    case 'clearLocalDataConfirmed':
      return createClearedLocalState(state, action.metadata);
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
          action.metadata,
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
            action.metadata,
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
          action.metadata,
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
          activity: addActivity(action.metadata, state, 'Setup', 'Quiet hours not saved', problem, 'Warning')
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
          action.metadata,
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
    case 'setDefaultSendTime': {
      const time = action.time.trim();
      const problem = validateDefaultSendTime(time);
      if (problem) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Setup', 'Default send time not saved', problem, 'Warning')
        };
      }
      let count = 0;
      const messages = state.messages.map(message => {
        if (message.status !== 'Scheduled' || !message.eventId) return message;
        count += 1;
        return {
          ...clearApprovalMetadata(message),
          status: 'Needs review' as const,
          readiness: 'Review after default send time changed',
          lastError: SCHEDULE_POLICY_REVIEW_WARNING
        };
      });
      return {
        ...state,
        settings: { ...state.settings, defaultSendTime: time },
        messages,
        activity: addActivity(
          action.metadata,
          state,
          'Setup',
          'Default send time updated',
          `${time}.${count > 0 ? ` ${count} scheduled message(s) returned to review.` : ''}`,
          count > 0 ? 'Warning' : 'Info'
        )
      };
    }
    case 'addBlackout': {
      const validation = validateBlackoutInput({
        label: action.label,
        startDate: action.startDate,
        endDate: action.endDate,
        behavior: action.behavior,
        channels: action.channels
      });
      if (!validation.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'Setup', 'Blackout not saved', validation.message, 'Warning')
        };
      }
      const settings = {
        ...state.settings,
        blackouts: [
          {
            id: commandId(action.metadata, 'blackout'),
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
          action.metadata,
          state,
          'Setup',
          'Blackout added',
          `${validation.value.label} will ${validation.value.behavior === 'Block' ? 'block' : 'defer'} ${
            validation.value.channels?.join(', ') ?? 'all reminder and message'
          } activity.${
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
        activity: addActivity(action.metadata, state, 'Setup', 'Blackout removed', 'Reminder blackout window removed.')
      };
    case 'importContacts': {
      const result = importContacts(
        state,
        action.records,
        {
          contactIds: action.metadata.ids.contact,
          eventIds: action.metadata.ids.event
        },
        action.resolutions
      );
      return {
        ...state,
        contacts: result.contacts,
        events: result.events,
        activity: addActivity(
          action.metadata,
          state,
          'Contact',
          'Contacts imported',
          `${result.added} added, ${result.updated} updated, ${result.skipped} skipped, ${result.unresolved} need review. Review imported birthdays before sending.`
        )
      };
    }
    case 'planReminders': {
      const planning = buildReminderPlanningResult(state, [7, 1, 0], new Date(action.metadata.occurredAt));
      const issueDetail =
        planning.issues.length > 0
          ? ` ${planning.issues.map(issue => `${issue.title}: ${issue.detail}`).join(' ')}`
          : '';
      return {
        ...state,
        reminderPlans: planning.plans,
        activity: addActivity(
          action.metadata,
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
      const result = calendarCandidatesToEvents(
        state,
        action.candidates,
        {
          contactIds: action.metadata.ids.contact,
          eventIds: action.metadata.ids.event
        },
        action.resolutions
      );
      return {
        ...state,
        contacts: result.contacts,
        events: result.events,
        calendarSync: {
          ...state.calendarSync,
          lastImportedAt: action.metadata.occurredAt,
          importedCount: state.calendarSync.importedCount + result.addedEvents,
          lastError: undefined
        },
        activity: addActivity(
          action.metadata,
          state,
          'Event',
          'Calendar events imported',
          `${result.addedEvents} event(s), ${result.addedContacts} contact(s), ${result.skipped} skipped, ${result.unresolved} need review.`
        )
      };
    }
    case 'calendarExported':
      return {
        ...state,
        calendarSync: {
          ...state.calendarSync,
          lastExportedAt: action.metadata.occurredAt,
          exportedCount: state.calendarSync.exportedCount + action.count,
          lastError: undefined
        },
        activity: addActivity(
          action.metadata,
          state,
          'Event',
          'Events exported to calendar',
          `${action.count} event(s) exported.`
        )
      };
    case 'calendarError':
      return {
        ...state,
        calendarSync: {
          ...state.calendarSync,
          lastError: action.message
        },
        activity: addActivity(action.metadata, state, 'Event', 'Calendar sync failed', action.message, 'Warning')
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
            id: commandId(action.metadata, 'backup'),
            createdAt: action.metadata.occurredAt,
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
        activity: addActivity(
          action.metadata,
          state,
          'Backup',
          'Encrypted backup created',
          'Backup file export completed.'
        )
      };
    case 'restoreBackup': {
      const restoredState = normalizeLoadedState(action.restoredState);
      return {
        ...restoredState,
        activeScreen: 'more',
        selectedContactId: undefined,
        selectedEventId: undefined,
        selectedMessageId: undefined,
        activity: addActivity(
          action.metadata,
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
          action.metadata,
          state,
          'Analytics',
          analyticsFormat === 'Summary' ? 'Analytics summary shared' : 'Analytics report prepared',
          `${action.rowCount} redacted ${analyticsFormat === 'Summary' ? 'summary line(s) shared' : 'report row(s) prepared for user-controlled sharing'}.`
        )
      };
    case 'setupDoctorDryRunRecorded':
      return {
        ...state,
        activity: addActivity(action.metadata, state, 'Setup', 'Setup Check dry run completed', action.detail, 'Info', {
          targetScreen: 'setupCheck',
          actionLabel: 'Open Setup Check'
        })
      };
    case 'setStyleEnabled':
      if (state.styleProfile.enabledForAiDrafts === action.enabled) return state;
      return {
        ...state,
        styleProfile: {
          ...state.styleProfile,
          enabledForAiDrafts: action.enabled
        },
        activity: addActivity(
          action.metadata,
          state,
          'AI',
          `Style use ${action.enabled ? 'enabled' : 'disabled'}`,
          action.enabled
            ? 'The current Style Coach profile will shape future AI drafts.'
            : 'Future AI drafts will use contact preferences without the Style Coach profile.'
        )
      };
    case 'trainStyle':
    case 'trainStyleFromSentMessages': {
      const result = analyzeSentMessageStyle(state);
      if (!result.ok) {
        return {
          ...state,
          activity: addActivity(action.metadata, state, 'AI', 'Style profile not updated', result.message, 'Warning')
        };
      }
      return {
        ...state,
        styleProfile: {
          ...result.profile,
          enabledForAiDrafts: state.styleProfile.enabledForAiDrafts
        },
        activity: addActivity(
          action.metadata,
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
          activity: addActivity(action.metadata, state, 'AI', 'Style profile not updated', result.message, 'Warning')
        };
      }
      return {
        ...state,
        styleProfile: {
          ...result.profile,
          enabledForAiDrafts: state.styleProfile.enabledForAiDrafts
        },
        activity: addActivity(
          action.metadata,
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

const hasCommandMetadata = (action: RelateAction | EnrichedRelateAction): action is EnrichedRelateAction =>
  'metadata' in action;

export const createRelateReducer =
  (dependencies: CommandDependencies) =>
  (state: AppState, action: RelateAction | EnrichedRelateAction): AppState =>
    relateTransition(state, hasCommandMetadata(action) ? action : enrichRelateAction(action, dependencies));

/** Compatibility reducer for existing UI and test callers. */
export const relateReducer = createRelateReducer(systemCommandDependencies);
