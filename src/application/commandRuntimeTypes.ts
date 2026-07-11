import type { AiDraftErrorKind, AiDraftRequest, AiDraftResponseResult } from '../domain/aiDrafting';
import type { BackupPreview } from '../data/encryptedBackup';
import type { AnalyticsRange, AnalyticsShareSummary } from '../domain/analytics';
import type { HandoffExecutionInput, HandoffExecutionResult } from '../domain/channelHandoffExecution';
import type { ContactEssentialsInput } from '../domain/contactEssentials';
import type { StandaloneContactInput } from '../domain/contactLifecycle';
import type { EmailDeliveryRequest } from '../domain/emailDelivery';
import type { EventEditInput } from '../domain/eventLifecycle';
import type { EventImportFormat } from '../domain/eventImport';
import type { HomePlannerActionKind } from '../domain/homePlanner';
import type { ActivityDateFilter, ActivityStatusFilter } from '../domain/activityHistory';
import type { ContactEnrichmentPromptId } from '../domain/contactEnrichment';
import type { EventPreparationStepId } from '../domain/eventPreparation';
import type { ContactTimelineFilter } from '../domain/contactTimeline';
import type { ChatHistoryChannelFilter } from '../domain/chatHistory';
import type {
  MessageBulkAction,
  MessageInboxChannelFilter,
  MessageInboxSort,
  MessageInboxTab
} from '../domain/messageInbox';
import type { LocalTemplateSelection } from '../domain/messageTemplates';
import type { ExplicitPermissionRequestResult } from './permissionRequestCoordinator';
import type { SetupDoctorEnvironment } from '../domain/setupDoctor';
import type { SetupGoal } from '../domain/setupWizard';
import type {
  AppState,
  AutomationMode,
  CalendarImportCandidate,
  ComposerReason,
  EventType,
  GiftCategory,
  ImportedContactRecord,
  MemoryCategory,
  MessageDraft,
  OnboardingGoal,
  OnboardingStepId,
  RelationshipGroup,
  SupportedLocale,
  SystemPermissionCapability,
  Tone
} from '../domain/types';
import type { BackupFileExportResult, BackupFilePickResult } from '../native/backupFiles';
import type { EmailReconciliationResult, EmailSendResult } from '../native/emailSenderClient';
import type { RelateAction } from '../state/relateReducer';
import type { RestoreTransactionResult } from './dataLifecycle';
import type { DataLifecycleRecoveryResult } from './dataLifecycleRecovery';
import type { OperationCoordinator, OperationError, OperationSnapshot } from './operationCoordinator';
import type {
  PermissionAuthorizationRecords,
  PermissionOperationCheck,
  ReminderLifecycleResult
} from './permissionReminderCoordinator';

export type HarnessDomainActionType =
  | 'navigate'
  | 'setSearch'
  | 'toggleChecklist'
  | 'addManualEvent'
  | 'editMessage'
  | 'approveMessage'
  | 'rejectMessage'
  | 'revokeMessage'
  | 'setLocale'
  | 'toggleSetting'
  | 'setQuietHours'
  | 'setDefaultSendTime'
  | 'addBlackout'
  | 'removeBlackout';

export type HarnessDomainAction = Extract<RelateAction, { type: HarnessDomainActionType }>;

export type ReminderRuntimeReason =
  'manual' | 'hydration' | 'foreground' | 'permission-change' | 'events-committed' | 'settings-committed';

export type PageRequest = Readonly<{
  cursor?: string;
  limit: number;
  includeArchived: boolean;
}>;

export type ContactLifecycleAction = 'add' | 'edit' | 'archive' | 'restore' | 'delete' | 'merge';
export type EventLifecycleAction = 'add' | 'edit' | 'delete' | 'merge';
export type MessageReviewAction =
  | 'edit'
  | 'set-channel'
  | 'select-variant'
  | 'acknowledge-duplicate'
  | 'approve'
  | 'reject'
  | 'revoke'
  | 'schedule-follow-up';

export type HarnessCommand =
  | { type: 'system.catalog' }
  | { type: 'domain.dispatch'; action: HarnessDomainAction }
  | ({
      type: 'contacts.query';
      query?: string;
      group?: RelationshipGroup;
      vip?: boolean;
      missingEvent?: boolean;
      missingChannel?: boolean;
      lowHealth?: boolean;
      needsPersonalization?: boolean;
      sort: 'Name' | 'Health' | 'Upcoming event';
    } & PageRequest)
  | ({
      type: 'events.query';
      eventType?: EventType;
      query?: string;
      month?: string;
      sort: 'Date' | 'Contact' | 'Type';
    } & PageRequest)
  | ({
      type: 'messages.query';
      status?: MessageDraft['status'];
      tab: MessageInboxTab;
      channel: MessageInboxChannelFilter;
      query: string;
      sort: MessageInboxSort;
    } & PageRequest)
  | { type: 'contacts.inspect'; contactId: string }
  | { type: 'contacts.preferences.inspect'; contactId: string }
  | { type: 'contacts.preferences.set-tone'; contactId: string; tone: Tone; enabled: boolean }
  | { type: 'contacts.preferences.set-group'; contactId: string; group: RelationshipGroup }
  | { type: 'contacts.preferences.set-channel'; contactId: string; channel: MessageDraft['channel'] }
  | { type: 'contacts.preferences.set-vip'; contactId: string; enabled: boolean }
  | { type: 'contacts.preferences.set-dnd'; contactId: string; enabled: boolean }
  | { type: 'contacts.preferences.set-cadence'; contactId: string; days: 14 | 30 | 45 | 60 | 90 }
  | { type: 'contacts.preferences.set-automation'; contactId: string; mode: Exclude<AutomationMode, 'Fully auto'> }
  | { type: 'contacts.preferences.set-send-time'; contactId: string; time: string | null }
  | { type: 'contacts.preferences.set-quiet-hours'; contactId: string; behavior: 'Defer' | 'Block' }
  | { type: 'contacts.preferences.set-skip-auto'; contactId: string; enabled: boolean }
  | { type: 'contacts.preferences.use-group-defaults'; contactId: string }
  | { type: 'groups.inspect' }
  | {
      type: 'groups.set-default';
      group: RelationshipGroup;
      defaults: {
        preferredChannel?: MessageDraft['channel'];
        tone?: Tone[];
        checkInCadenceDays?: 14 | 30 | 45 | 60 | 90;
        automationMode?: Exclude<AutomationMode, 'Fully auto'>;
      };
    }
  | { type: 'contacts.enrichment.inspect'; contactId: string }
  | { type: 'contacts.enrichment.answer'; contactId: string; promptId: ContactEnrichmentPromptId; body: string }
  | { type: 'contacts.add'; input: StandaloneContactInput }
  | { type: 'contacts.edit-preview'; contactId: string; input: ContactEssentialsInput }
  | {
      type: 'contacts.edit-apply';
      contactId: string;
      input: ContactEssentialsInput;
      confirmationToken: string;
    }
  | { type: 'contacts.archive-preview'; contactId: string }
  | { type: 'contacts.archive-apply'; contactId: string; confirmationToken: string }
  | { type: 'contacts.restore'; contactId: string }
  | { type: 'contacts.delete-preview'; contactId: string }
  | { type: 'contacts.delete-apply'; contactId: string; confirmationToken: string }
  | { type: 'contacts.merge-preview'; survivorContactId: string; mergedContactId: string }
  | {
      type: 'contacts.merge-apply';
      survivorContactId: string;
      mergedContactId: string;
      confirmationToken: string;
    }
  | {
      type: 'events.add';
      contactId?: string;
      newContactName?: string;
      eventType: EventType;
      label: string;
      date: string;
      confirmConflict: boolean;
    }
  | { type: 'events.preparation.inspect'; eventId: string }
  | { type: 'events.preparation.toggle'; eventId: string; stepId: EventPreparationStepId }
  | { type: 'events.edit-preview'; eventId: string; input: EventEditInput }
  | { type: 'events.edit-apply'; eventId: string; input: EventEditInput; confirmationToken: string }
  | { type: 'events.delete-preview'; eventId: string }
  | { type: 'events.delete-apply'; eventId: string; confirmationToken: string }
  | { type: 'events.merge-preview'; survivorEventId: string; mergedEventId: string }
  | {
      type: 'events.merge-apply';
      survivorEventId: string;
      mergedEventId: string;
      confirmationToken: string;
    }
  | ({ type: 'checkins.query'; status?: 'Due' | 'Snoozed' | 'Current' } & PageRequest)
  | { type: 'checkins.snooze'; contactId: string; days: 1 | 7 | 14 | 30 }
  | { type: 'checkins.mark-contacted'; contactId: string }
  | {
      type: 'composer.inspect';
      contactId: string;
      reason: ComposerReason;
      draftBody?: string;
      templateId?: string;
    }
  | {
      type: 'composer.create-template';
      contactId: string;
      reason: ComposerReason;
      body?: string;
      templateId?: string;
    }
  | { type: 'messages.edit'; messageId: string; body: string }
  | { type: 'messages.set-channel'; messageId: string; channel: MessageDraft['channel'] }
  | {
      type: 'messages.select-variant';
      messageId: string;
      variant: MessageDraft['selectedVariant'];
      discardEditedBody: boolean;
    }
  | { type: 'messages.acknowledge-duplicate'; messageId: string }
  | { type: 'messages.approve'; messageId: string; reviewNext: boolean }
  | { type: 'messages.reject'; messageId: string; reviewNext: boolean }
  | { type: 'messages.revoke'; messageId: string }
  | { type: 'messages.schedule-follow-up'; messageId: string; delayDays: 1 | 7 }
  | { type: 'messages.preview'; messageId: string; excludedMemoryIds?: string[]; includePriorMessages: boolean }
  | {
      type: 'messages.regenerate';
      messageId: string;
      instructions: string[];
      customInstruction?: string;
      excludedMemoryIds?: string[];
      includePriorMessages: boolean;
    }
  | { type: 'messages.test-route'; messageId: string }
  | { type: 'messages.retry'; messageId: string }
  | { type: 'messages.bulk-preview'; action: MessageBulkAction; messageIds: string[] }
  | { type: 'messages.bulk-apply'; confirmationToken: string }
  | {
      type: 'templates.inspect';
      contactId?: string;
      reason: ComposerReason;
      tone?: Tone;
      templateId?: string;
      draftBody?: string;
    }
  | ({ type: 'memories.query'; contactId: string; query: string } & Omit<PageRequest, 'includeArchived'>)
  | { type: 'memories.add'; contactId: string; category: MemoryCategory; body: string }
  | { type: 'memories.edit'; memoryId: string; category: MemoryCategory; body: string }
  | { type: 'memories.set-pinned'; memoryId: string; pinned: boolean }
  | { type: 'memories.delete'; memoryId: string; confirmation: 'DELETE MEMORY' }
  | ({ type: 'timeline.query'; contactId: string; filter: ContactTimelineFilter } & Omit<
      PageRequest,
      'includeArchived'
    >)
  | ({
      type: 'chat.query';
      contactId: string;
      query: string;
      channel: ChatHistoryChannelFilter;
    } & Omit<PageRequest, 'includeArchived'>)
  | ({ type: 'gifts.inspect'; contactId: string; occasion: string } & Omit<PageRequest, 'includeArchived'>)
  | {
      type: 'gifts.add';
      contactId: string;
      name: string;
      category: GiftCategory;
      occasion: string;
      cost: number;
      feedback: 'Liked' | 'Disliked' | 'Unknown';
      notes: string;
    }
  | { type: 'gifts.delete'; giftId: string; confirmation: 'DELETE GIFT' }
  | { type: 'gifts.set-budget'; contactId: string; annualGiftBudget: number }
  | { type: 'onboarding.inspect' }
  | { type: 'onboarding.set-goal'; goal: OnboardingGoal }
  | { type: 'onboarding.set-step'; stepId: OnboardingStepId }
  | { type: 'onboarding.advance' }
  | { type: 'onboarding.skip'; stepId: OnboardingStepId }
  | { type: 'onboarding.complete' }
  | { type: 'onboarding.reopen' }
  | { type: 'account.inspect' }
  | { type: 'account.use-local' }
  | { type: 'account.disconnect'; confirmation: 'DISCONNECT ACCOUNT' }
  | { type: 'privacy.inspect' }
  | { type: 'privacy.set-whatsapp-consent'; enabled: boolean }
  | { type: 'settings.inspect' }
  | {
      type: 'settings.set-boolean';
      key: 'aiEnabled' | 'notificationsEnabled' | 'smsEnabled' | 'whatsappHandoffEnabled' | 'emailEnabled';
      enabled: boolean;
    }
  | { type: 'settings.set-automation'; mode: Exclude<AutomationMode, 'Fully auto'> }
  | { type: 'settings.set-locale'; locale: SupportedLocale }
  | { type: 'settings.set-email-sender'; senderEmail: string }
  | { type: 'settings.set-quiet-hours'; start: string; end: string }
  | { type: 'settings.set-default-send-time'; time: string }
  | {
      type: 'settings.add-blackout';
      label: string;
      startDate: string;
      endDate: string;
      behavior?: 'Block' | 'Defer';
      channels?: MessageDraft['channel'][];
    }
  | { type: 'settings.remove-blackout'; blackoutId: string }
  | { type: 'setup.wizard.inspect'; goal: SetupGoal }
  | { type: 'setup.wizard.run-action'; goal: SetupGoal; stepId: string }
  | { type: 'style.inspect' }
  | { type: 'style.set-enabled'; enabled: boolean }
  | { type: 'style.train-samples'; samples: string }
  | { type: 'style.train-sent' }
  | ({
      type: 'activity.query';
      query: string;
      activityType?: AppState['activity'][number]['type'];
      severity?: AppState['activity'][number]['severity'];
      status?: Exclude<ActivityStatusFilter, 'All'>;
      date: ActivityDateFilter;
    } & Omit<PageRequest, 'includeArchived'>)
  | { type: 'activity.open-action'; activityId: string }
  | { type: 'activity.resolve'; activityId: string }
  | { type: 'home.open-action'; actionId: string }
  | { type: 'contacts.import' }
  | { type: 'contacts.import-preview' }
  | {
      type: 'contacts.import-apply';
      sessionToken: string;
      decisions: {
        reviewItemId: string;
        action: 'merge' | 'keep-separate' | 'skip' | 'keep-existing' | 'replace' | 'import-as-separate';
        candidateContactId?: string;
        conflictingEventId?: string;
      }[];
    }
  | { type: 'calendar.import' }
  | { type: 'calendar.import-preview' }
  | {
      type: 'calendar.import-apply';
      sessionToken: string;
      decisions: (
        | { reviewId: string; action: 'apply' | 'skip' }
        | { reviewId: string; action: 'edit'; title: string; date: string; notes?: string }
        | { reviewId: string; action: 'create-separate' }
        | { reviewId: string; action: 'merge-contact'; candidateContactId: string }
        | { reviewId: string; action: 'merge-event'; candidateEventId: string }
      )[];
    }
  | { type: 'calendar.export'; eventIds?: string[] }
  | { type: 'events.import-file' }
  | { type: 'reminders.reconcile'; reason: ReminderRuntimeReason }
  | {
      type: 'ai.draft';
      contactId: string;
      eventId?: string;
      reason: ComposerReason;
      excludedMemoryIds?: string[];
      includePriorMessages: boolean;
    }
  | { type: 'email.deliver'; messageId: string }
  | { type: 'email.reconcile'; messageId: string }
  | { type: 'handoff.open'; messageId: string; preferFallback: boolean }
  | { type: 'handoff.confirm'; messageId: string; sent: boolean }
  | { type: 'events.import-text'; raw: string; format: EventImportFormat }
  | { type: 'backup.export'; passphrase: string; destination: 'share' | 'save' }
  | { type: 'backup.export-confirm'; backupConfirmationToken: string }
  | { type: 'backup.select-file' }
  | { type: 'backup.restore-preview'; raw: string; passphrase: string }
  | { type: 'backup.restore-preview-selected'; selectionToken: string; passphrase: string }
  | { type: 'backup.restore-confirm'; confirmationToken: string }
  | { type: 'data.clear'; confirmation: 'CLEAR LOCAL DATA' }
  | { type: 'data.recover' }
  | { type: 'permissions.refresh' }
  | { type: 'permissions.preflight'; capability: SystemPermissionCapability }
  | {
      type: 'permissions.request';
      capability: 'Contacts' | 'Notifications' | 'Calendar';
      userIntent: 'allow' | 'decline';
    }
  | { type: 'biometric.enable' }
  | { type: 'biometric.disable' }
  | { type: 'biometric.unlock' }
  | { type: 'analytics.inspect'; range: AnalyticsRange }
  | { type: 'analytics.open-action'; range: AnalyticsRange; insightId: string }
  | { type: 'analytics.share-summary'; range: AnalyticsRange }
  | { type: 'analytics.export-preview'; range: AnalyticsRange }
  | { type: 'analytics.export-confirm'; confirmationToken: string }
  | { type: 'home.inspect' }
  | { type: 'setup.inspect' }
  | { type: 'setup.open-action'; checkId: string }
  | { type: 'operation.cancel'; scope: string };

export type CommandParseError = Readonly<{
  code: 'invalid-command' | 'command-too-large';
  summary: string;
}>;

export type CommandParseResult = { ok: true; command: HarnessCommand } | { ok: false; error: CommandParseError };

export type BoundedActionOutcome = Readonly<{
  outcome: 'applied' | 'blocked';
  affectedIds: string[];
  affectedCount: number;
}>;

export type ContactQueryItem = Readonly<{
  id: string;
  name: string;
  relationship: string;
  relationshipSubtype?: string;
  jobTitle?: string;
  routes: ContactReviewRoute[];
  archived: boolean;
  archivedAt?: string;
  group: AppState['contacts'][number]['group'];
  preferredChannel: AppState['contacts'][number]['preferredChannel'];
  language: AppState['contacts'][number]['language'];
  isVip: boolean;
  dnd: boolean;
  checkInCadenceDays: number;
  healthScore: number;
  personalizationScore: number;
  qualityLabels: ('VIP' | 'Missing event' | 'Missing channel' | 'Low health' | 'Needs details')[];
  nextEvent?: {
    id: string;
    eventType: EventType;
    label: string;
    occurrence: string;
  };
}>;

export type ContactReviewRoute = Readonly<{
  type: 'Phone' | 'Email';
  value: string;
  label?: string;
  primary: boolean;
  verified: boolean;
}>;

export type EventQueryItem = Readonly<{
  id: string;
  contactId: string;
  contactName: string;
  contactArchived: boolean;
  eventType: EventType;
  label: string;
  date: string;
  verified: boolean;
  source: AppState['events'][number]['source'];
  checklist: {
    id: string;
    label: string;
    done: boolean;
  }[];
}>;

export type MessageQueryItem = Readonly<{
  id: string;
  contactId: string;
  contactName: string;
  eventId?: string;
  eventLabel?: string;
  contactArchived: boolean;
  reason: ComposerReason;
  status: MessageDraft['status'];
  channel: MessageDraft['channel'];
  body: string;
  variants: MessageDraft['variants'];
  selectedVariant: MessageDraft['selectedVariant'];
  quality: MessageDraft['quality'];
  readiness: string;
  error?: string;
  issues: string[];
  duplicateWarning?: string;
  scheduledFor?: string;
  scheduledTimeZone?: string;
  sentAt?: string;
  approvedAt?: string;
  approvalExpiresAt?: string;
  duplicateRisk: boolean;
  duplicateAcknowledged: boolean;
  recovery?: {
    title: string;
    detail: string;
    actionLabel: string;
    targetScreen: AppState['activeScreen'];
  };
}>;

export type CheckInQueryItem = Readonly<{
  contactId: string;
  contactArchived: boolean;
  status: 'Due' | 'Snoozed' | 'Current';
  cadenceDays: number;
  daysSinceContact?: number;
  overdueDays: number;
  lastContactedAt?: string;
  snoozedUntil?: string;
}>;

export type PageMetadata = Readonly<{
  nextCursor?: string;
  hasMore: boolean;
  returnedCount: number;
  totalCount: number;
  includeArchived: boolean;
}>;

export type RedactedCommandValue =
  | {
      kind: 'command-catalog';
      commandCount: number;
      supportedTypes: HarnessCommand['type'][];
      workflows: {
        id: string;
        purpose: string;
        examples: string[];
      }[];
      guidance: string[];
    }
  | ({
      kind: 'domain-action';
      actionType: HarnessDomainActionType;
    } & BoundedActionOutcome)
  | ({ kind: 'contacts-page'; items: ContactQueryItem[] } & PageMetadata)
  | ({ kind: 'events-page'; items: EventQueryItem[] } & PageMetadata)
  | ({
      kind: 'messages-page';
      items: MessageQueryItem[];
      counts: Record<MessageInboxTab, number>;
      emptyState: 'No messages yet' | 'No matching messages' | undefined;
    } & PageMetadata)
  | {
      kind: 'bulk-message-preview';
      action: MessageBulkAction;
      selectedCount: number;
      eligibleCount: number;
      skippedCount: number;
      eligibleMessageIds: string[];
      skipped: { messageId: string; reason: string }[];
      summary: string;
      verificationGuidance?: string;
      requiresConfirmation: true;
      confirmationToken: string;
      confirmationFingerprint: string;
      expiresAt: string;
      redacted: true;
    }
  | {
      kind: 'bulk-message-apply';
      action: MessageBulkAction;
      selectedCount: number;
      appliedCount: number;
      skippedCount: number;
      appliedMessageIds: string[];
      skipped: { messageId: string; reason: string }[];
      summary: string;
      confirmationFingerprint: string;
      redacted: true;
    }
  | ({ kind: 'checkins-page'; items: CheckInQueryItem[] } & PageMetadata)
  | ({
      kind: 'feature-page';
      feature: 'memories' | 'timeline' | 'chat' | 'gifts' | 'activity';
      items: Record<string, unknown>[];
      summary?: Record<string, unknown>;
    } & Omit<PageMetadata, 'includeArchived'>)
  | {
      kind: 'feature-inspection';
      feature:
        | 'contact-detail'
        | 'contact-preferences'
        | 'group-defaults'
        | 'contact-enrichment'
        | 'event-preparation'
        | 'message-preview'
        | 'template-library'
        | 'onboarding'
        | 'account'
        | 'privacy'
        | 'settings'
        | 'setup-wizard'
        | 'style';
      data: Record<string, unknown>;
    }
  | ({
      kind: 'feature-action';
      feature:
        | 'contact-preferences'
        | 'group-defaults'
        | 'contact-enrichment'
        | 'event-preparation'
        | 'message'
        | 'memory'
        | 'gift'
        | 'onboarding'
        | 'account'
        | 'privacy'
        | 'settings'
        | 'style'
        | 'activity'
        | 'home';
      action: string;
      createdId?: string;
    } & BoundedActionOutcome)
  | {
      kind: 'activity-navigation';
      activityId?: string;
      outcome: 'target' | 'fallback';
      targetScreen: AppState['activeScreen'];
      contactId?: string;
      messageId?: string;
    }
  | {
      kind: 'setup-action';
      checkId?: string;
      outcome: 'navigation' | 'fallback' | 'reminders-reconciled' | 'ai-provider-ready' | 'ai-provider-failed';
      targetScreen?: AppState['activeScreen'];
      aiTest?: {
        ok: boolean;
        errorKind?: AiDraftErrorKind;
        syntheticContext: true;
        redacted: true;
      };
    }
  | ({
      kind: 'contact-action';
      action: ContactLifecycleAction;
      createdContactId?: string;
      blockedReason?: 'exact-identity-collision';
      exactIdentityCandidateIds?: string[];
    } & BoundedActionOutcome)
  | ({
      kind: 'event-action';
      action: EventLifecycleAction;
      createdEventId?: string;
      createdContactId?: string;
    } & BoundedActionOutcome)
  | ({
      kind: 'message-action';
      action: MessageReviewAction | 'create-template-draft';
      createdMessageId?: string;
    } & BoundedActionOutcome)
  | ({
      kind: 'checkin-action';
      action: 'snooze' | 'mark-contacted';
    } & BoundedActionOutcome)
  | {
      kind: 'contact-lifecycle-preview';
      action: 'edit' | 'archive' | 'delete' | 'merge';
      confirmationToken: string;
      affectedIds: string[];
      requiresConfirmation: true;
      changedFields?: string[];
      impact: {
        eventCount: number;
        reminderCount: number;
        activeMessageCount: number;
        historyMessageCount: number;
        memoryCount: number;
        giftCount: number;
        linkedActivityCount: number;
      };
      relationshipHistoryCount?: number;
      deletionAllowed?: boolean;
      recommendedAction?: 'archive' | 'delete';
      matchReasons?: ('source-identity' | 'phone' | 'email' | 'same-name')[];
      exactIdentityMatch?: boolean;
      exactIdentityCandidateIds?: string[];
      conflictingFields?: string[];
    }
  | {
      kind: 'event-lifecycle-preview';
      action: 'edit' | 'delete';
      confirmationToken: string;
      affectedIds: string[];
      requiresConfirmation: boolean;
      changedFields?: string[];
      conflictCount: number;
      impact: {
        activeMessageCount: number;
        historyMessageCount: number;
        reminderCount: number;
        calendarExportMayNeedReconciliation: boolean;
      };
    }
  | {
      kind: 'event-merge-preview';
      action: 'merge';
      confirmationToken: string;
      affectedIds: string[];
      requiresConfirmation: true;
      matchReasons: ('same-date' | 'same-type' | 'same-label')[];
      impact: {
        activeMessageCount: number;
        historyMessageCount: number;
        reminderCount: number;
      };
    }
  | {
      kind: 'composer-inspection';
      outcome: 'ready' | 'blocked';
      contactId: string;
      reason: ComposerReason;
      templateIds: string[];
      templates: { id: string; title: string }[];
      selectedTemplateId?: string;
      selectedTemplateTitle?: string;
      renderedTemplateBody: string;
      error?: string;
      characterCount: number;
      languageTarget?: AppState['contacts'][number]['language'];
      requestedTones: Tone[];
      templateSelection?: LocalTemplateSelection;
      contextSource: 'memory' | 'notes' | 'none';
      contextDetail: string;
      includedMemoryCount: number;
      excludedGuidanceMemoryCount: number;
      excludedPrivateMemoryCount: number;
      excludedSensitiveMemoryCount: number;
      templateActionStatus: 'Ready' | 'Warning' | 'Blocked';
      aiActionStatus: 'Ready' | 'Warning' | 'Blocked';
      templateAction: { status: 'Ready' | 'Warning' | 'Blocked'; enabled: boolean; label: string; detail: string };
      aiAction: { status: 'Ready' | 'Warning' | 'Blocked'; enabled: boolean; label: string; detail: string };
    }
  | {
      kind: 'operation-cancellation';
      targetScope: string;
      cancelled: boolean;
    }
  | {
      kind: 'contact-import-preview';
      sessionToken: string;
      expiresAt: string;
      received: number;
      added: number;
      updated: number;
      skipped: number;
      unresolved: number;
      reviewItems: {
        reviewItemId: string;
        candidateContactIds: string[];
        candidateName: string;
        candidateRoutes: ContactReviewRoute[];
        candidateBirthday?: string;
        importedBirthday?: string;
        conflictingEvents: { eventId: string; label: string; date: string; eventType: EventType }[];
        validationErrors: string[];
        reason: 'same-name' | 'multiple-route-matches' | 'missing-name' | 'invalid-birthday' | 'conflicting-birthday';
        resolutionIssue?: 'candidate-no-longer-listed' | 'only-skip-allowed' | 'conflicting-event-no-longer-listed';
      }[];
    }
  | {
      kind: 'contact-import-apply';
      received: number;
      added: number;
      updated: number;
      skipped: number;
      unresolved: number;
      reviewItems: {
        reviewItemId: string;
        candidateContactIds: string[];
        candidateName: string;
        candidateRoutes: ContactReviewRoute[];
        candidateBirthday?: string;
        importedBirthday?: string;
        conflictingEvents: { eventId: string; label: string; date: string; eventType: EventType }[];
        validationErrors: string[];
        reason: 'same-name' | 'multiple-route-matches' | 'missing-name' | 'invalid-birthday' | 'conflicting-birthday';
        resolutionIssue?: 'candidate-no-longer-listed' | 'only-skip-allowed' | 'conflicting-event-no-longer-listed';
      }[];
      sessionToken?: string;
      expiresAt?: string;
    }
  | {
      kind: 'contact-import';
      received: number;
      added: number;
      updated: number;
      skipped: number;
    }
  | {
      kind: 'calendar-import';
      received: number;
      addedContacts: number;
      addedEvents: number;
      skipped: number;
    }
  | {
      kind: 'calendar-import-preview';
      sessionToken: string;
      expiresAt: string;
      received: number;
      staged: number;
      rejected: number;
      overflow: number;
      invalid: number;
      parseErrorCount: number;
      conflictCount: number;
      reviewItems: {
        reviewId: string;
        title: string;
        date: string;
        valid: boolean;
        validationErrorCount: number;
        validationErrors: string[];
        conflictReason?: 'same-name' | 'multiple-source-matches' | 'source-content-conflict' | 'conflicting-date';
        allowedConflictActions: ('skip' | 'create-separate' | 'merge-contact' | 'merge-event')[];
        candidateContacts: { contactId: string; name: string; routes: ContactReviewRoute[] }[];
        conflictingEvents: { eventId: string; label: string; date: string; eventType: EventType }[];
      }[];
    }
  | {
      kind: 'calendar-import-apply';
      requested: number;
      applied: number;
      skipped: number;
      unresolved: number;
      issueCount: number;
      unresolvedReviewIds: string[];
      addedContacts: number;
      addedEvents: number;
      duplicateSkipped: number;
      sessionToken?: string;
      expiresAt?: string;
      reviewItems: {
        reviewId: string;
        title: string;
        date: string;
        valid: boolean;
        validationErrorCount: number;
        validationErrors: string[];
        conflictReason?: 'same-name' | 'multiple-source-matches' | 'source-content-conflict' | 'conflicting-date';
        allowedConflictActions: ('skip' | 'create-separate' | 'merge-contact' | 'merge-event')[];
        candidateContacts: { contactId: string; name: string; routes: ContactReviewRoute[] }[];
        conflictingEvents: { eventId: string; label: string; date: string; eventType: EventType }[];
      }[];
    }
  | {
      kind: 'calendar-export';
      mode: 'full' | 'selected';
      selectedCount: number;
      eligibleCount: number;
      reconciled: number;
    }
  | {
      kind: 'reminder-reconciliation';
      status: ReminderLifecycleResult['status'];
      planned: number;
      desiredNative: number;
      scheduled: number;
      cancelled: number;
      unchanged: number;
      skipped: number;
    }
  | {
      kind: 'ai-draft';
      created: true;
      source: 'ai' | 'local-template-fallback';
      createdMessageId: string;
      includedMemoryCount: number;
      excludedPrivateMemoryCount: number;
      includedPriorMessageCount: number;
    }
  | { kind: 'email-delivery'; status: 'accepted' | 'sent'; deliveryRecorded: true }
  | { kind: 'email-reconciliation'; status: 'accepted' | 'sent' | 'failed'; deliveryRecorded: true }
  | {
      kind: 'handoff-open';
      outcome: HandoffExecutionResult['outcome'];
      usedFallback: boolean;
      confirmationRequired: boolean;
    }
  | { kind: 'handoff-confirmation'; markedSent: boolean }
  | {
      kind: 'event-text-import';
      candidates: number;
      addedContacts: number;
      addedEvents: number;
      skipped: number;
      errorCount: number;
    }
  | {
      kind: 'backup-export';
      destination: 'temporary-shared' | 'saved-export';
      shared: boolean;
      temporaryFileRemoved: boolean;
      recordCount: number;
      createdAt: string;
      fileName: string;
      byteCount: number;
      verifiedPortableCopy: boolean;
      freshnessRecorded: boolean;
      backupConfirmationToken?: string;
      confirmationExpiresAt?: string;
    }
  | { kind: 'backup-export-confirmation'; freshnessRecorded: true }
  | {
      kind: 'backup-file-selection';
      selectionToken: string;
      expiresAt: string;
      version: number;
      format: string;
      app: string;
      persistenceVersion: number;
      createdAt: string;
      recordCount: number;
      recordCounts: BackupPreview['recordCounts'];
      warningCount: number;
      temporaryFileRemoved: boolean;
    }
  | {
      kind: 'backup-restore-preview';
      confirmationToken: string;
      expiresAt: string;
      mode: 'replace';
      version: number;
      format: string;
      app: string;
      persistenceVersion: number;
      createdAt: string;
      recordCount: number;
      recordCounts: BackupPreview['recordCounts'];
      warningCount: number;
    }
  | {
      kind: 'backup-restore';
      status: RestoreTransactionResult['status'];
      recordCount: number;
      nativeReconciliationRequired: boolean;
    }
  | { kind: 'data-clear'; cleared: true }
  | {
      kind: 'data-lifecycle-recovery';
      status: 'resolved';
      outcome: Extract<DataLifecycleRecoveryResult, { status: 'resolved' }>['outcome'];
      operation?: Extract<DataLifecycleRecoveryResult, { status: 'resolved' }>['operation'];
      journalCleared: true;
    }
  | {
      kind: 'permission-refresh';
      statuses: {
        capability: SystemPermissionCapability;
        authorization: PermissionAuthorizationRecords[SystemPermissionCapability]['systemAuthorization'];
        canAskAgain?: boolean;
      }[];
    }
  | {
      kind: 'permission-preflight';
      capability: SystemPermissionCapability;
      authorization: PermissionOperationCheck['authorization'];
      allowed: boolean;
      canAskAgain?: boolean;
    }
  | {
      kind: 'permission-request';
      capability: 'Contacts' | 'Notifications' | 'Calendar';
      userIntent: 'allow' | 'decline';
      status: ExplicitPermissionRequestResult['status'];
      authorization: PermissionAuthorizationRecords['Contacts' | 'Notifications' | 'Calendar']['systemAuthorization'];
      canAskAgain?: boolean;
    }
  | {
      kind: 'biometric-setting';
      action: 'enable' | 'disable';
      outcome: 'applied' | 'blocked';
      authorization: PermissionOperationCheck['authorization'];
      recoveryUsed: boolean;
    }
  | { kind: 'biometric-unlock'; unlocked: boolean }
  | {
      kind: 'analytics-inspection';
      range: AnalyticsRange;
      contactCount: number;
      metrics: { label: string; value: string }[];
      relationshipDistribution: { label: string; count: number }[];
      healthBuckets: { label: string; count: number }[];
      overdueContactCount: number;
      insightCount: number;
      insights: {
        id: string;
        title: string;
        detail: string;
        actionLabel: string;
        targetScreen: AppState['activeScreen'];
        contactId?: string;
      }[];
      empty: boolean;
      emptyState?: string;
      redacted: true;
    }
  | {
      kind: 'analytics-action';
      outcome: 'navigation' | 'fallback';
      targetScreen: AppState['activeScreen'];
      contactId?: string;
    }
  | {
      kind: 'analytics-summary-share';
      outcome: 'shared' | 'dismissed';
      lineCount: number;
      redacted: true;
    }
  | {
      kind: 'analytics-export-preview';
      range: AnalyticsRange;
      rowCount: number;
      confirmationToken: string;
      expiresAt: string;
      warning: string;
      redacted: true;
    }
  | {
      kind: 'analytics-export';
      range: AnalyticsRange;
      rowCount: number;
      shareOpened: true;
      temporaryFileRemoved: true;
      redacted: true;
    }
  | {
      kind: 'home-inspection';
      generatedAt: string;
      summaryCode: 'actions-due' | 'no-actions-due';
      summary: string;
      actionCount: number;
      actions: {
        id: string;
        kind: HomePlannerActionKind;
        priority: number;
        title: string;
        detail: string;
        targetScreen: AppState['activeScreen'];
        contactId?: string;
        messageId?: string;
        eventId?: string;
      }[];
      counts: Record<HomePlannerActionKind, number>;
      metrics: {
        activeContacts: number;
        upcomingEvents: number;
        pendingReview: number;
        failedOrBlocked: number;
        backups: number;
      };
      upcoming: {
        eventId: string;
        contactId: string;
        eventType: EventType;
        label: string;
        occurrence: string;
        verified: boolean;
      }[];
      setupNeedsAction: boolean;
      onboardingCompleted: boolean;
      backup: {
        status: 'never' | 'fresh' | 'stale';
        latestCreatedAt?: string;
        ageDays?: number;
      };
      redacted: true;
    }
  | {
      kind: 'setup-inspection';
      readyCount: number;
      totalCount: number;
      needsActionCount: number;
      warningCount: number;
      recommendedTitle?: string;
      summary: string;
      checksByGroup: {
        group: 'Required' | 'Quality' | 'Reliability' | 'Recovery';
        checks: {
          id: string;
          status: 'Ready' | 'Needs action' | 'Warning';
          title: string;
          impact: string;
          actionLabel: string;
          targetScreen?: AppState['activeScreen'];
          contactId?: string;
          command?: 'testAiProvider' | 'planReminders';
        }[];
      }[];
      safe: true;
      redacted: true;
    };

export type CommandExecutionResult =
  | {
      status: 'succeeded';
      commandType: HarnessCommand['type'];
      value: RedactedCommandValue;
      operation: OperationSnapshot;
    }
  | {
      status: 'failed' | 'unknown';
      commandType: HarnessCommand['type'];
      error: OperationError;
      operation: OperationSnapshot;
    }
  | {
      status: 'already-running';
      commandType: HarnessCommand['type'];
      requestId: string;
      operation?: OperationSnapshot;
    }
  | {
      status: 'conflict' | 'cancelled' | 'locked';
      commandType: HarnessCommand['type'];
      error: OperationError;
      operation?: OperationSnapshot;
    }
  | {
      status: 'invalid';
      error: CommandParseError;
    };

export interface CommandRuntimeDependencies {
  getState(): AppState;
  dispatch(action: RelateAction): void | Promise<void>;
  installVerifiedState(state: AppState): void | Promise<void>;
  runDataReplacement<T>(operation: () => Promise<T>): Promise<T>;
  operations: OperationCoordinator;
  createConfirmationToken(): string;
  now(): Date;
  importContacts(signal: AbortSignal): Promise<ImportedContactRecord[]>;
  importCalendar(signal: AbortSignal): Promise<CalendarImportCandidate[]>;
  exportCalendar(
    state: AppState,
    request: { mode: 'full' } | { mode: 'selected'; eventIds: string[] },
    signal: AbortSignal
  ): Promise<number>;
  pickEventImportFile(signal: AbortSignal): Promise<{ name: string; raw: string } | undefined>;
  reconcileReminders(
    state: AppState,
    reason: ReminderRuntimeReason,
    signal: AbortSignal
  ): Promise<ReminderLifecycleResult>;
  requestAiDraft(request: AiDraftRequest, signal: AbortSignal): Promise<AiDraftResponseResult>;
  sendEmail(request: EmailDeliveryRequest, signal: AbortSignal): Promise<EmailSendResult>;
  reconcileEmail(
    attempt: { idempotencyKey: string; deliveryId?: string },
    signal: AbortSignal
  ): Promise<EmailReconciliationResult>;
  openHandoff(input: HandoffExecutionInput, signal: AbortSignal): Promise<HandoffExecutionResult>;
  exportBackup(
    state: AppState,
    passphrase: string,
    destination: 'share' | 'save',
    signal: AbortSignal
  ): Promise<BackupFileExportResult>;
  selectBackup(signal: AbortSignal): Promise<BackupFilePickResult | undefined>;
  decryptBackup(raw: string, passphrase: string, signal: AbortSignal): Promise<AppState>;
  restoreData(restoredState: AppState, signal: AbortSignal): Promise<RestoreTransactionResult>;
  clearData(previousState: AppState, signal: AbortSignal): Promise<AppState>;
  recoverDataLifecycle(signal: AbortSignal): Promise<DataLifecycleRecoveryResult>;
  refreshPermissions(state: AppState, signal: AbortSignal): Promise<PermissionAuthorizationRecords>;
  preflightPermission(
    state: AppState,
    capability: SystemPermissionCapability,
    signal: AbortSignal
  ): Promise<PermissionOperationCheck>;
  requestPermission(
    state: AppState,
    request: {
      capability: 'Contacts' | 'Notifications' | 'Calendar';
      userIntent: 'allow' | 'decline';
    }
  ): Promise<ExplicitPermissionRequestResult>;
  authenticateBiometric(signal: AbortSignal): Promise<boolean>;
  shareAnalyticsSummary(summary: AnalyticsShareSummary, signal: AbortSignal): Promise<'shared' | 'dismissed'>;
  shareAnalyticsCsv(csv: string, signal: AbortSignal): Promise<{ opened: true; temporaryFileRemoved: true }>;
  setupEnvironment(signal: AbortSignal): SetupDoctorEnvironment | Promise<SetupDoctorEnvironment>;
}
