import {
  buildAiDraftRequest,
  normalizeAiDraftResponse,
  type AiDraftError,
  type AiDraftErrorKind,
  type AiDraftRequest
} from '../domain/aiDrafting';
import { buildActivityHistory } from '../domain/activityHistory';
import { buildAnalyticsCsvReport, buildAnalyticsDashboard, buildShareableAnalyticsSummary } from '../domain/analytics';
import {
  assertBackupRawInput,
  countBackupRecords,
  previewEncryptedBackup,
  totalBackupRecords,
  validateBackupPassphrase
} from '../data/encryptedBackup';
import {
  calendarCandidatesToEvents,
  resolveCalendarExportSelection,
  type CalendarImportResolution,
  type CalendarImportReviewItem
} from '../domain/calendarSync';
import { buildCheckInReminderQueue } from '../domain/checkIns';
import { buildChatHistory } from '../domain/chatHistory';
import { buildHandoffTarget } from '../domain/channelHandoff';
import {
  importContacts as previewContactImport,
  type ContactImportResolution,
  type ContactImportResolutions,
  type ContactImportReviewItem
} from '../domain/contactImport';
import { allContactRoutes, importedContactRoutes } from '../domain/contactIdentity';
import { buildContactEnrichmentPlan, buildContactEnrichmentPlans } from '../domain/contactEnrichment';
import { resolveContactPreferencesForContact } from '../domain/contactPreferences';
import { buildContactTimeline } from '../domain/contactTimeline';
import {
  previewContactArchive,
  previewContactDelete,
  previewContactEdit,
  previewContactMerge
} from '../domain/contactLifecycle';
import { buildEmailDeliveryRequest, type EmailDeliveryError } from '../domain/emailDelivery';
import { previewEventMerge } from '../domain/eventConflictLifecycle';
import { previewEventDelete, previewEventEdit } from '../domain/eventLifecycle';
import { buildEventPreparationPlan } from '../domain/eventPreparation';
import { assessDuplicateMessageRisk, messageDraftRevision } from '../domain/duplicateGuard';
import { MAX_EVENT_IMPORT_BYTES, parseEventImportText } from '../domain/eventImport';
import {
  resolveEventImportReview,
  stageEventImportCandidates,
  type EventImportReviewDecisions,
  type StagedEventImportBatch
} from '../domain/eventImportReview';
import { buildHomePlanner } from '../domain/homePlanner';
import { buildGiftBudgetSummary, buildGiftSuggestions } from '../domain/giftAdvisor';
import { buildManualComposerState } from '../domain/manualComposer';
import { buildMemoryVaultReport } from '../domain/memoryVault';
import { buildRelationshipHealthInsight, buildRelationshipHealthInsights } from '../domain/relationshipHealth';
import { messageApprovalWindowIssue } from '../domain/messageApproval';
import { validateMessageBodyForChannel } from '../domain/messageBodyPolicy';
import {
  buildMessageBulkActionReport,
  buildMessageInbox,
  messageApprovalRouteIssue,
  type MessageBulkAction,
  type MessageBulkActionReport
} from '../domain/messageInbox';
import {
  buildMessageTemplateLibrary,
  buildTemplateDraft,
  firstRenderedTemplateForContact
} from '../domain/messageTemplates';
import { buildMessageTestPlan } from '../domain/messageTesting';
import { buildOnboardingPlan, onboardingTransitionIssue } from '../domain/onboarding';
import { messageDispatchTimingIssue, normalizeScheduleTimeZone } from '../domain/schedulingPolicy';
import { buildSetupDoctorDryRunSnapshot, buildSetupDoctorReport } from '../domain/setupDoctor';
import { buildSetupWizardPlan } from '../domain/setupWizard';
import { analyzeManualStyleSamples, analyzeSentMessageStyle } from '../domain/styleCoach';
import { eventOccurrenceInYear, eventOccurrenceIso } from '../domain/occasionDates';
import { productAvailability } from '../config/productAvailability';
import { buildCommandCatalog } from './commandCatalog';
import type {
  AppState,
  CalendarImportCandidate,
  ImportedContactRecord,
  MessageDraft,
  SystemAuthorization
} from '../domain/types';
import type { EmailSendResult } from '../native/emailSenderClient';
import { relateReducer, type MessageRegenerationSource, type RelateAction } from '../state/relateReducer';
import { parseHarnessCommand } from './commandRuntimeParser';
import type {
  CommandExecutionResult,
  CommandRuntimeDependencies,
  ContactQueryItem,
  ContactReviewRoute,
  EventQueryItem,
  HarnessCommand,
  MessageQueryItem,
  PageMetadata,
  RedactedCommandValue
} from './commandRuntimeTypes';
import type { OperationError, OperationTaskResult } from './operationCoordinator';
import {
  permissionDecisionsFromRecords,
  systemPermissionCapabilities,
  type PermissionAuthorizationRecords
} from './permissionReminderCoordinator';

const MAX_IMPORTED_RECORDS = 10_000;
const MAX_PROVIDER_IDENTIFIER_LENGTH = 256;
const SENSITIVE_UNLOCK_WINDOW_MS = 2 * 60 * 1_000;
const RESTORE_CONFIRMATION_WINDOW_MS = 5 * 60 * 1_000;
const TRANSIENT_SELECTION_WINDOW_MS = 5 * 60 * 1_000;
const CONTACT_IMPORT_SESSION_WINDOW_MS = 5 * 60 * 1_000;
const HANDOFF_CONFIRMATION_WINDOW_MS = 5 * 60 * 1_000;
const BULK_MESSAGE_CONFIRMATION_WINDOW_MS = 5 * 60 * 1_000;
const MAX_AFFECTED_IDS = 100;
const MAX_REVIEW_CANDIDATE_IDS = 100;
const MAX_CONTACT_NAME_LENGTH = 240;
const MAX_CONTACT_ROUTES = 40;
const MAX_ROUTE_LABEL_LENGTH = 80;
const MAX_EVENT_LABEL_LENGTH = 240;
const MAX_EVENT_CHECKLIST_ITEMS = 100;
const MAX_EVENT_CHECKLIST_LABEL_LENGTH = 240;
const MAX_REVIEW_MESSAGE_LENGTH = 10_000;
const MAX_READINESS_LENGTH = 500;
const MAX_REVIEW_ERROR_LENGTH = 1_000;
const MAX_TEMPLATE_TITLE_LENGTH = 160;
const MAX_VALIDATION_ERRORS = 20;
const MAX_VALIDATION_ERROR_LENGTH = 240;
const MAX_FEATURE_OUTPUT_BYTES = 96 * 1024;
const MAX_ACTIVITY_TEXT_LENGTH = 1_000;
const opaqueIdPattern = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$/;
const privateOperationalContentPattern = /(?:https?:\/\/|[\w.+-]+@[\w.-]+\.[a-z]{2,}|\+?\d[\d\s().-]{7,}\d)/i;

const safeOperationalSummary = (value: string, fallback: string) => {
  const normalized = value.replace(/\s+/g, ' ').trim();
  return normalized && !privateOperationalContentPattern.test(normalized) ? normalized.slice(0, 240) : fallback;
};

const AI_PROVIDER_TEST_PRIVACY_SUMMARY =
  'Provider readiness test used synthetic context only; no contacts, events, memories, gifts, prior messages, routes, notes, credentials, or screen contents were included.';

const aiProviderTestFailureMessages: Record<AiDraftErrorKind, string> = {
  disabled: 'AI drafting must be enabled before the provider readiness test can run.',
  'missing-contact': 'The provider readiness test request could not be prepared.',
  'missing-event': 'The provider readiness test request could not be prepared.',
  'not-configured': 'Configure an approved AI provider endpoint before testing.',
  auth: 'The authenticated provider session is missing, expired, or was rejected. Reconnect it and try again.',
  quota: 'The AI provider is rate limited or out of quota. Try the readiness test again later.',
  network: 'The AI provider could not be reached. Check connectivity and try again.',
  timeout: 'The AI provider readiness test timed out. Try again.',
  'invalid-response': 'The AI provider returned an unsupported draft contract. Review the provider integration.',
  'content-safety': 'The AI provider response did not pass the safety contract. Review the provider integration.',
  'wrong-language': 'The AI provider response did not satisfy the requested language contract.',
  server: 'The AI provider is temporarily unavailable. Try the readiness test again later.'
};

const buildSyntheticAiProviderTestRequest = (): AiDraftRequest => ({
  reason: 'Check-in',
  contact: {
    name: 'Test recipient',
    relationship: 'Test relationship',
    group: 'Other',
    language: 'English',
    tone: ['Warm'],
    preferredChannel: 'Manual',
    notesSummary: ''
  },
  style: {
    enabled: false,
    confidence: 'Not trained',
    formality: 'Neutral',
    language: 'English',
    averageLength: 80,
    emojiUse: 'None',
    commonGreetings: []
  },
  memories: [],
  giftHistory: [],
  generationConstraints: [],
  priorApprovedMessages: [],
  privacy: {
    includedMemoryCount: 0,
    includedGenerationConstraintCount: 0,
    excludedOptionalMemoryCount: 0,
    excludedPrivateMemoryCount: 0,
    excludedSensitiveMemoryCount: 0,
    includedGiftHistoryCount: 0,
    excludedGiftHistoryCount: 0,
    excludedSensitiveGiftHistoryCount: 0,
    includedPriorMessageCount: 0,
    excludedSensitivePriorMessageCount: 0,
    excludedSensitiveFeedbackCount: 0,
    excludedFields: [
      'contacts',
      'events',
      'memories',
      'gifts',
      'prior messages',
      'contact routes',
      'private notes',
      'credentials',
      'activity and diagnostic logs',
      'screen contents'
    ]
  },
  outputContract: {
    format: 'json',
    variants: ['short', 'standard', 'warm'],
    maxCharactersPerVariant: 500,
    mustRequireUserReview: true
  }
});

const failure = (code: string, summary: string, retryable = false): OperationTaskResult<never> => ({
  status: 'failed',
  error: { code, retryable, summary }
});

const unknown = (code: string, summary: string): OperationTaskResult<never> => ({
  status: 'unknown',
  error: { code, retryable: false, summary }
});

const succeeded = (value: RedactedCommandValue): OperationTaskResult<RedactedCommandValue> => ({
  status: 'succeeded',
  value
});

const safeConflictError = (summary: string): OperationError => ({
  code: 'operation-conflict',
  retryable: true,
  summary
});

const applicationLockedError = (): OperationError => ({
  code: 'application-locked',
  retryable: false,
  summary: 'Unlock the application before running this command.'
});

const commandMutatesState = (command: HarnessCommand) =>
  ![
    'system.catalog',
    'contacts.query',
    'events.query',
    'messages.query',
    'messages.bulk-preview',
    'checkins.query',
    'contacts.inspect',
    'contacts.preferences.inspect',
    'groups.inspect',
    'contacts.enrichment.inspect',
    'events.preparation.inspect',
    'messages.preview',
    'templates.inspect',
    'memories.query',
    'timeline.query',
    'chat.query',
    'gifts.inspect',
    'onboarding.inspect',
    'account.inspect',
    'privacy.inspect',
    'settings.inspect',
    'setup.wizard.inspect',
    'style.inspect',
    'activity.query',
    'contacts.edit-preview',
    'contacts.archive-preview',
    'contacts.delete-preview',
    'contacts.merge-preview',
    'events.edit-preview',
    'events.delete-preview',
    'events.merge-preview',
    'composer.inspect',
    'backup.select-file',
    'backup.restore-preview',
    'backup.restore-preview-selected',
    'biometric.unlock',
    'analytics.inspect',
    'analytics.export-preview',
    'home.inspect',
    'operation.cancel'
  ].includes(command.type);

const commandIsExclusive = (command: HarnessCommand) =>
  command.type === 'backup.restore-confirm' || command.type === 'data.clear' || command.type === 'data.recover';

const fnvFingerprint = (value: string, seed = 2166136261) => {
  let hash = seed;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16);
};

const contentFreeMaterialFingerprint = (state: AppState) => {
  const material = materialStateFingerprint(state);
  return `${fnvFingerprint(material)}${fnvFingerprint(material, 0x9e3779b9)}`;
};

const safeOpaqueId = (value: string | undefined): string | undefined =>
  value && opaqueIdPattern.test(value) ? value : undefined;

const safeIso = (value: string | undefined): string | undefined => {
  if (!value || value.length > 64 || Number.isNaN(Date.parse(value))) return undefined;
  return value;
};

const normalizedSearchText = (value: string) =>
  value
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase()
    .replace(/\s+/g, ' ')
    .trim();

const boundedPrivateString = (value: unknown, maximum: number, allowEmpty = false): value is string =>
  typeof value === 'string' && value.length <= maximum && (allowEmpty || value.length > 0);

const safeContactRoutes = (
  routes: ReturnType<typeof allContactRoutes> | ReturnType<typeof importedContactRoutes>
): ContactReviewRoute[] | undefined => {
  if (routes.length > MAX_CONTACT_ROUTES) return undefined;
  const safe = routes.map(route => {
    const maximum = route.type === 'Phone' ? 80 : 320;
    if (
      !boundedPrivateString(route.value, maximum) ||
      (route.label !== undefined && !boundedPrivateString(route.label, MAX_ROUTE_LABEL_LENGTH, true))
    ) {
      return undefined;
    }
    return {
      type: route.type,
      value: route.value,
      label: route.label,
      primary: route.primary,
      verified: route.verified
    };
  });
  return safe.some(route => !route) ? undefined : (safe as ContactReviewRoute[]);
};

const boundedAffectedIds = (ids: Iterable<string>) => {
  const all = [...new Set([...ids].map(safeOpaqueId).filter((item): item is string => Boolean(item)))];
  return {
    affectedIds: all.slice(0, MAX_AFFECTED_IDS),
    affectedCount: all.length
  };
};

const entityRevisions = (state: AppState) => {
  const revisions = new Map<string, string>();
  const add = (prefix: string, records: readonly { id: string }[]) => {
    for (const record of records) {
      if (safeOpaqueId(record.id)) revisions.set(`${prefix}:${record.id}`, JSON.stringify(record));
    }
  };
  add('contact', state.contacts);
  add('event', state.events);
  add('message', state.messages);
  add('memory', state.memories);
  add('gift', state.gifts);
  add('reminder', state.reminderPlans);
  add('backup', state.backups);
  return revisions;
};

const changedEntityIds = (before: AppState, after: AppState) => {
  const left = entityRevisions(before);
  const right = entityRevisions(after);
  const changed: string[] = [];
  for (const key of new Set([...left.keys(), ...right.keys()])) {
    if (left.get(key) !== right.get(key)) changed.push(key.slice(key.indexOf(':') + 1));
  }
  return boundedAffectedIds(changed);
};

const materialStateFingerprint = (state: AppState) => {
  const { activity: _activity, persistence: _persistence, ...material } = state;
  return JSON.stringify(material);
};

const safeMessageBulkReport = (report: MessageBulkActionReport) => {
  const eligibleMessageIds = report.eligibleIds.map(safeOpaqueId);
  const skipped = report.skipped.map(item => ({
    messageId: safeOpaqueId(item.messageId),
    reason: boundedPrivateString(item.reason, MAX_REVIEW_ERROR_LENGTH) ? item.reason : undefined
  }));
  const allIds = [...eligibleMessageIds, ...skipped.map(item => item.messageId)];
  if (
    !Number.isInteger(report.selectedCount) ||
    report.selectedCount < 1 ||
    report.selectedCount > MAX_AFFECTED_IDS ||
    eligibleMessageIds.some(messageId => !messageId) ||
    skipped.some(item => !item.messageId || !item.reason) ||
    allIds.length !== report.selectedCount ||
    new Set(allIds).size !== allIds.length ||
    !boundedPrivateString(report.summary, MAX_READINESS_LENGTH) ||
    (report.verificationGuidance !== undefined &&
      !boundedPrivateString(report.verificationGuidance, MAX_REVIEW_ERROR_LENGTH))
  ) {
    return undefined;
  }
  return {
    action: report.action,
    selectedCount: report.selectedCount,
    eligibleMessageIds: eligibleMessageIds as string[],
    skipped: skipped as { messageId: string; reason: string }[],
    summary: report.summary,
    verificationGuidance: report.verificationGuidance
  };
};

const messageBulkReportFingerprint = (report: NonNullable<ReturnType<typeof safeMessageBulkReport>>) => {
  const payload = JSON.stringify(report);
  return `${fnvFingerprint(payload)}${fnvFingerprint(payload, 0x9e3779b9)}`;
};

const bulkMessageTransitionApplied = (action: MessageBulkAction, message: MessageDraft | undefined) => {
  if (!message) return false;
  switch (action) {
    case 'Approve':
      return message.status === 'Scheduled';
    case 'Reject':
      return message.status === 'Rejected';
    case 'Retry':
    case 'Revoke approval':
      return message.status === 'Needs review';
  }
};

type SafePage<T> = PageMetadata & { items: T[] };
type FeatureInspectionName = Extract<RedactedCommandValue, { kind: 'feature-inspection' }>['feature'];
type FeaturePageName = Extract<RedactedCommandValue, { kind: 'feature-page' }>['feature'];
type FeatureActionName = Extract<RedactedCommandValue, { kind: 'feature-action' }>['feature'];

const pageByOpaqueId = <T>(
  items: T[],
  cursor: string | undefined,
  limit: number,
  includeArchived: boolean,
  getId: (item: T) => string,
  compare: (left: T, right: T) => number = (left, right) => getId(left).localeCompare(getId(right))
): SafePage<T> | undefined => {
  if (items.some(item => !safeOpaqueId(getId(item)))) return undefined;
  const sorted = [...items].sort((left, right) => compare(left, right) || getId(left).localeCompare(getId(right)));
  const start = cursor === undefined ? 0 : sorted.findIndex(item => getId(item) === cursor) + 1;
  if (cursor !== undefined && start === 0) return undefined;
  const page = sorted.slice(start, start + limit);
  const hasMore = start + page.length < sorted.length;
  return {
    items: page,
    nextCursor: hasMore && page.length > 0 ? getId(page[page.length - 1]) : undefined,
    hasMore,
    returnedCount: page.length,
    totalCount: sorted.length,
    includeArchived
  };
};

const localActionResult = (before: AppState, after: AppState, applied: boolean) => ({
  outcome: applied ? ('applied' as const) : ('blocked' as const),
  ...changedEntityIds(before, after)
});

const scopeFor = (command: HarnessCommand): string => {
  switch (command.type) {
    case 'system.catalog':
      return 'system:catalog';
    case 'domain.dispatch':
      return `domain:${command.action.type}`;
    case 'contacts.query':
      return 'contacts:query';
    case 'events.query':
      return 'events:query';
    case 'messages.query':
      return 'messages:query';
    case 'messages.bulk-preview':
      return 'messages:bulk-preview';
    case 'messages.bulk-apply':
      return 'messages:bulk-apply';
    case 'checkins.query':
      return 'checkins:query';
    case 'contacts.inspect':
    case 'contacts.preferences.inspect':
    case 'contacts.preferences.set-tone':
    case 'contacts.preferences.set-group':
    case 'contacts.preferences.set-channel':
    case 'contacts.preferences.set-vip':
    case 'contacts.preferences.set-dnd':
    case 'contacts.preferences.set-cadence':
    case 'contacts.preferences.set-automation':
    case 'contacts.preferences.set-send-time':
    case 'contacts.preferences.set-quiet-hours':
    case 'contacts.preferences.set-skip-auto':
    case 'contacts.preferences.use-group-defaults':
    case 'contacts.enrichment.inspect':
    case 'contacts.enrichment.answer':
      return `contacts:feature:${fnvFingerprint(command.contactId)}`;
    case 'groups.inspect':
    case 'groups.set-default':
      return `groups:${command.type === 'groups.inspect' ? 'inspect' : 'update'}`;
    case 'contacts.add':
      return 'contacts:add';
    case 'contacts.edit-preview':
    case 'contacts.edit-apply':
      return `contacts:edit:${fnvFingerprint(command.contactId)}`;
    case 'contacts.archive-preview':
    case 'contacts.archive-apply':
      return `contacts:archive:${fnvFingerprint(command.contactId)}`;
    case 'contacts.restore':
      return `contacts:restore:${fnvFingerprint(command.contactId)}`;
    case 'contacts.delete-preview':
    case 'contacts.delete-apply':
      return `contacts:delete:${fnvFingerprint(command.contactId)}`;
    case 'contacts.merge-preview':
    case 'contacts.merge-apply':
      return `contacts:merge:${fnvFingerprint(`${command.survivorContactId}:${command.mergedContactId}`)}`;
    case 'events.add':
      return 'events:add';
    case 'events.preparation.inspect':
    case 'events.preparation.toggle':
      return `events:prepare:${fnvFingerprint(command.eventId)}`;
    case 'events.edit-preview':
    case 'events.edit-apply':
      return `events:edit:${fnvFingerprint(command.eventId)}`;
    case 'events.delete-preview':
    case 'events.delete-apply':
      return `events:delete:${fnvFingerprint(command.eventId)}`;
    case 'events.merge-preview':
    case 'events.merge-apply':
      return `events:merge:${fnvFingerprint(`${command.survivorEventId}:${command.mergedEventId}`)}`;
    case 'checkins.snooze':
    case 'checkins.mark-contacted':
      return `checkins:${fnvFingerprint(command.contactId)}`;
    case 'composer.inspect':
      return `composer:inspect:${fnvFingerprint(command.contactId)}`;
    case 'composer.create-template':
      return `composer:create:${fnvFingerprint(command.contactId)}`;
    case 'messages.edit':
    case 'messages.set-channel':
    case 'messages.select-variant':
    case 'messages.acknowledge-duplicate':
    case 'messages.approve':
    case 'messages.reject':
    case 'messages.revoke':
    case 'messages.schedule-follow-up':
    case 'messages.preview':
    case 'messages.regenerate':
    case 'messages.test-route':
    case 'messages.retry':
      return `messages:review:${fnvFingerprint(command.messageId)}`;
    case 'templates.inspect':
      return `templates:inspect:${fnvFingerprint(command.contactId ?? command.reason)}`;
    case 'memories.query':
    case 'memories.add':
      return `memories:${fnvFingerprint(command.contactId)}`;
    case 'memories.edit':
    case 'memories.set-pinned':
    case 'memories.delete':
      return `memories:${fnvFingerprint(command.memoryId)}`;
    case 'timeline.query':
    case 'chat.query':
    case 'gifts.inspect':
    case 'gifts.add':
    case 'gifts.set-budget':
      return `${command.type.split('.')[0]}:${fnvFingerprint(command.contactId)}`;
    case 'gifts.delete':
      return `gifts:${fnvFingerprint(command.giftId)}`;
    case 'onboarding.inspect':
    case 'onboarding.set-goal':
    case 'onboarding.set-step':
    case 'onboarding.advance':
    case 'onboarding.skip':
    case 'onboarding.complete':
    case 'onboarding.reopen':
      return `onboarding:${command.type.split('.')[1]}`;
    case 'account.inspect':
    case 'account.use-local':
    case 'account.disconnect':
      return `account:${command.type.split('.')[1]}`;
    case 'privacy.inspect':
    case 'privacy.set-whatsapp-consent':
      return `privacy:${command.type.split('.')[1]}`;
    case 'settings.inspect':
    case 'settings.set-boolean':
    case 'settings.set-automation':
    case 'settings.set-locale':
    case 'settings.set-email-sender':
    case 'settings.set-quiet-hours':
    case 'settings.set-default-send-time':
    case 'settings.add-blackout':
    case 'settings.remove-blackout':
      return `settings:${command.type.split('.')[1]}`;
    case 'setup.wizard.inspect':
      return 'setup:wizard';
    case 'setup.wizard.run-action':
      return command.stepId === 'ai-provider'
        ? 'setup:ai-provider-test'
        : command.stepId === 'reminder-plans'
          ? 'reminders:reconcile'
          : `setup:wizard-action:${fnvFingerprint(`${command.goal}:${command.stepId}`)}`;
    case 'style.inspect':
    case 'style.set-enabled':
    case 'style.train-samples':
    case 'style.train-sent':
      return `style:${command.type.split('.')[1]}`;
    case 'activity.query':
      return 'activity:query';
    case 'activity.open-action':
      return `activity:action:${fnvFingerprint(command.activityId)}`;
    case 'activity.resolve':
      return `activity:resolve:${fnvFingerprint(command.activityId)}`;
    case 'home.open-action':
      return `home:action:${fnvFingerprint(command.actionId)}`;
    case 'contacts.import':
    case 'contacts.import-preview':
      return 'contacts:import';
    case 'contacts.import-apply':
      return 'contacts:import-apply';
    case 'calendar.import':
    case 'calendar.import-preview':
      return 'calendar:import';
    case 'calendar.import-apply':
      return 'calendar:import-apply';
    case 'calendar.export':
      return 'calendar:export';
    case 'reminders.reconcile':
      return 'reminders:reconcile';
    case 'ai.draft':
      return `ai:${fnvFingerprint(command.contactId)}`;
    case 'email.deliver':
      return `email:${fnvFingerprint(command.messageId)}`;
    case 'email.reconcile':
      return `email:reconcile:${fnvFingerprint(command.messageId)}`;
    case 'handoff.open':
    case 'handoff.confirm':
      return `handoff:${fnvFingerprint(command.messageId)}`;
    case 'events.import-text':
      return 'events:import-text';
    case 'events.import-file':
      return 'events:import-file';
    case 'backup.export':
      return 'backup:export';
    case 'backup.export-confirm':
      return 'backup:export-confirm';
    case 'backup.select-file':
      return 'backup:select-file';
    case 'backup.restore-preview':
    case 'backup.restore-preview-selected':
      return 'backup:restore-preview';
    case 'backup.restore-confirm':
      return 'data:restore';
    case 'data.clear':
      return 'data:clear';
    case 'data.recover':
      return 'data:recover';
    case 'permissions.refresh':
      return 'permissions:refresh';
    case 'permissions.preflight':
      return `permissions:${command.capability.toLowerCase().replace(/\s+/g, '-')}`;
    case 'permissions.request':
      return `permissions:request:${command.capability.toLowerCase()}`;
    case 'biometric.enable':
      return 'biometric:enable';
    case 'biometric.disable':
      return 'biometric:disable';
    case 'biometric.unlock':
      return 'biometric:unlock';
    case 'analytics.inspect':
      return 'analytics:inspect';
    case 'analytics.open-action':
      return `analytics:action:${fnvFingerprint(command.insightId)}`;
    case 'analytics.share-summary':
      return 'analytics:share-summary';
    case 'analytics.export-preview':
    case 'analytics.export-confirm':
      return 'analytics:export';
    case 'home.inspect':
      return 'home:inspect';
    case 'setup.inspect':
      return 'setup:inspect';
    case 'setup.open-action':
      return command.checkId === 'ai-provider'
        ? 'setup:ai-provider-test'
        : command.checkId === 'reminders'
          ? 'reminders:reconcile'
          : `setup:action:${fnvFingerprint(command.checkId)}`;
    case 'operation.cancel':
      return `operation:cancel:${fnvFingerprint(command.scope)}`;
  }
};

const isBoundedOptionalString = (value: unknown, maximum: number) =>
  value === undefined || (typeof value === 'string' && value.length <= maximum);

const isBoundedOptionalStringArray = (value: unknown, maximumItems: number, maximumItemLength: number) =>
  value === undefined ||
  (Array.isArray(value) &&
    value.length <= maximumItems &&
    value.every(item => typeof item === 'string' && item.length > 0 && item.length <= maximumItemLength));

const validateImportedContacts = (records: ImportedContactRecord[]): ImportedContactRecord[] | undefined => {
  if (!Array.isArray(records) || records.length > MAX_IMPORTED_RECORDS) return undefined;
  const sourceIds = new Set<string>();
  const valid = records.every(record => {
    if (!record || typeof record !== 'object' || typeof record.sourceId !== 'string') return false;
    if (sourceIds.has(record.sourceId)) return false;
    sourceIds.add(record.sourceId);
    return (
      typeof record === 'object' &&
      record.sourceId.length > 0 &&
      record.sourceId.length <= 160 &&
      typeof record.name === 'string' &&
      record.name.length <= 240 &&
      isBoundedOptionalString(record.phone, 80) &&
      isBoundedOptionalString(record.email, 320) &&
      isBoundedOptionalStringArray(record.phones, 20, 80) &&
      isBoundedOptionalStringArray(record.emails, 20, 320) &&
      isBoundedOptionalString(record.birthday, 80) &&
      isBoundedOptionalString(record.relationship, 160)
    );
  });
  return valid
    ? records.map(record => ({
        ...record,
        phones: record.phones ? [...record.phones] : undefined,
        emails: record.emails ? [...record.emails] : undefined
      }))
    : undefined;
};

const validateCalendarCandidates = (candidates: CalendarImportCandidate[]): CalendarImportCandidate[] | undefined => {
  if (!Array.isArray(candidates) || candidates.length > MAX_IMPORTED_RECORDS) return undefined;
  const valid = candidates.every(
    candidate =>
      candidate &&
      typeof candidate === 'object' &&
      typeof candidate.sourceId === 'string' &&
      candidate.sourceId.length > 0 &&
      candidate.sourceId.length <= 160 &&
      typeof candidate.title === 'string' &&
      candidate.title.length <= 240 &&
      typeof candidate.startDate === 'string' &&
      candidate.startDate.length <= 80 &&
      isBoundedOptionalString(candidate.notes, 4_096)
  );
  return valid ? candidates.map(candidate => ({ ...candidate })) : undefined;
};

const retryableEmailError = (error: EmailDeliveryError) => error.kind === 'quota' || error.kind === 'server';

const handoffFingerprint = (message: MessageDraft) =>
  fnvFingerprint(`${message.id}|${message.channel}|${message.status}|${message.approvedAt ?? ''}|${message.body}`);

const permissionCanStartPromptingOperation = (authorization: SystemAuthorization, canAskAgain: boolean | undefined) =>
  authorization === 'granted' ||
  authorization === 'limited' ||
  authorization === 'undetermined' ||
  (authorization === 'denied' && canAskAgain === true);

const assertNotAborted = (signal: AbortSignal) => {
  if (signal.aborted) throw new Error('Operation cancelled.');
};

export class HarnessCommandRuntime {
  private activeScopes = new Set<string>();
  private activeMutationScope?: string;
  private activeExclusiveScope?: string;
  private applicationUnlocked = false;
  private sensitiveUnlockedUntil = 0;
  private handoffConfirmations = new Map<
    string,
    Readonly<{
      messageFingerprint: string;
      usedFallback: boolean;
      expiresAt: number;
    }>
  >();
  private emailDeliveryLocks = new Set<string>();
  private pendingRestore?: {
    confirmationToken: string;
    restoredState: AppState;
    baseStateFingerprint: string;
    expiresAt: number;
  };
  private pendingBackupSelection?: {
    selectionToken: string;
    raw: string;
    expiresAt: number;
  };
  private pendingBackupExport?: {
    confirmationToken: string;
    baseStateFingerprint: string;
    expiresAt: number;
  };
  private pendingAnalyticsExport?: {
    confirmationToken: string;
    baseStateFingerprint: string;
    range: Extract<HarnessCommand, { type: 'analytics.export-preview' }>['range'];
    generatedAt: string;
    rowCount: number;
    expiresAt: number;
  };
  private pendingContactImport?: {
    sessionToken: string;
    records: ImportedContactRecord[];
    expiresAt: number;
    reviewItems: {
      reviewItemId: string;
      sourceId: string;
      candidateContactIds: string[];
      conflictingEventIds: string[];
      reason: ContactImportReviewItem['reason'];
      resolutionIssue?: ContactImportReviewItem['resolutionIssue'];
    }[];
    resolutions: Record<string, ContactImportResolution>;
  };
  private pendingCalendarImport?: {
    sessionToken: string;
    batch: StagedEventImportBatch;
    received: number;
    expiresAt: number;
    decisions: Record<string, NonNullable<EventImportReviewDecisions[string]>>;
    resolutions: Record<string, CalendarImportResolution>;
  };
  private pendingBulkMessageAction?: Readonly<{
    confirmationToken: string;
    confirmationFingerprint: string;
    action: MessageBulkAction;
    messageIds: string[];
    baseStateFingerprint: string;
    previewReportFingerprint: string;
    expiresAt: number;
  }>;

  constructor(private readonly dependencies: CommandRuntimeDependencies) {}

  subscribeOperations(listener: () => void) {
    return this.dependencies.operations.subscribe(listener);
  }

  operationSnapshots() {
    return this.dependencies.operations.all();
  }

  operationSnapshot(scope: string) {
    return this.dependencies.operations.snapshot(scope);
  }

  isApplicationLocked() {
    return this.dependencies.getState().settings.biometricLockEnabled && !this.applicationUnlocked;
  }

  private pruneExpiredTransientSessions() {
    const now = this.dependencies.now().getTime();
    if (this.pendingRestore && now > this.pendingRestore.expiresAt) this.pendingRestore = undefined;
    if (this.pendingBackupSelection && now > this.pendingBackupSelection.expiresAt) {
      this.pendingBackupSelection = undefined;
    }
    if (this.pendingBackupExport && now > this.pendingBackupExport.expiresAt) {
      this.pendingBackupExport = undefined;
    }
    if (this.pendingAnalyticsExport && now > this.pendingAnalyticsExport.expiresAt) {
      this.pendingAnalyticsExport = undefined;
    }
    if (this.pendingContactImport && now > this.pendingContactImport.expiresAt) {
      this.pendingContactImport = undefined;
    }
    if (this.pendingCalendarImport && now > this.pendingCalendarImport.expiresAt) {
      this.pendingCalendarImport = undefined;
    }
    if (this.pendingBulkMessageAction && now > this.pendingBulkMessageAction.expiresAt) {
      this.pendingBulkMessageAction = undefined;
    }
    for (const [messageId, confirmation] of this.handoffConfirmations) {
      if (now > confirmation.expiresAt) this.handoffConfirmations.delete(messageId);
    }
  }

  cancelOperation(scope: string) {
    if (!/^[A-Za-z0-9][A-Za-z0-9:._-]{0,99}$/.test(scope)) return false;
    const runtimeSafe =
      scope === 'contacts:import' ||
      scope === 'calendar:import' ||
      scope === 'events:import-file' ||
      /^ai:[a-f0-9]{1,8}$/.test(scope);
    if (!runtimeSafe) return false;
    return this.dependencies.operations.cancel(scope);
  }

  private clearBackgroundSensitiveSessions() {
    this.applicationUnlocked = false;
    this.sensitiveUnlockedUntil = 0;
    this.pendingRestore = undefined;
    this.pendingBackupSelection = undefined;
    this.pendingAnalyticsExport = undefined;
    this.pendingContactImport = undefined;
    this.pendingCalendarImport = undefined;
    this.pendingBulkMessageAction = undefined;
  }

  private clearDataReplacementSessions() {
    this.clearBackgroundSensitiveSessions();
    this.pendingBackupExport = undefined;
    this.handoffConfirmations.clear();
    this.emailDeliveryLocks.clear();
  }

  lockSensitiveSession() {
    this.clearBackgroundSensitiveSessions();
    this.pendingBackupExport = undefined;
    this.handoffConfirmations.clear();
  }

  onBackground() {
    // The OS backgrounds the app while a destination app or share sheet is
    // open. Keep only the bounded, content-free confirmation fingerprint so
    // the user can explicitly confirm after returning and unlocking.
    this.clearBackgroundSensitiveSessions();
    this.pruneExpiredTransientSessions();
  }

  private async dispatch(action: RelateAction) {
    // Any committed data change invalidates outstanding manual-handoff
    // confirmations. The manualHandoff action consumes its entry immediately.
    this.handoffConfirmations.clear();
    this.pendingBackupExport = undefined;
    this.pendingAnalyticsExport = undefined;
    this.pendingBulkMessageAction = undefined;
    await this.dependencies.dispatch(action);
  }

  private boundedFeatureValue(
    value: Extract<RedactedCommandValue, { kind: 'feature-inspection' | 'feature-page' | 'feature-action' }>
  ) {
    return this.boundedPrivateValue(value);
  }

  private boundedPrivateValue(value: RedactedCommandValue) {
    try {
      if (new TextEncoder().encode(JSON.stringify(value)).byteLength > MAX_FEATURE_OUTPUT_BYTES) {
        return failure('feature-output-too-large', 'The private result exceeds the supported bounded output size.');
      }
      return succeeded(value);
    } catch {
      return failure('feature-output-invalid', 'The private result could not be represented safely.');
    }
  }

  private featureInspection(feature: FeatureInspectionName, data: Record<string, unknown>) {
    return this.boundedFeatureValue({ kind: 'feature-inspection', feature, data });
  }

  private featureAction(
    feature: FeatureActionName,
    action: string,
    before: AppState,
    after: AppState,
    applied: boolean,
    createdId?: string
  ) {
    return this.boundedFeatureValue({
      kind: 'feature-action',
      feature,
      action,
      createdId: safeOpaqueId(createdId),
      ...localActionResult(before, after, applied)
    });
  }

  private activeContact(contactId: string) {
    return this.dependencies.getState().contacts.find(contact => contact.id === contactId && !contact.archivedAt);
  }

  private featurePage<T extends Record<string, unknown>>(
    feature: FeaturePageName,
    items: T[],
    cursor: string | undefined,
    limit: number,
    getId: (item: T) => string,
    compare?: (left: T, right: T) => number,
    summary?: Record<string, unknown>
  ) {
    const page = pageByOpaqueId(items, cursor, limit, false, getId, compare);
    if (!page) return failure('query-cursor-invalid', 'The query cursor or stored identifiers are no longer valid.');
    const { includeArchived: _includeArchived, ...boundedPage } = page;
    return this.boundedFeatureValue({ kind: 'feature-page', feature, ...boundedPage, summary });
  }

  private async persistPermissionRecords(records: PermissionAuthorizationRecords) {
    const state = this.dependencies.getState();
    await this.dispatch({
      type: 'permissionsReconciled',
      records,
      decisions: permissionDecisionsFromRecords(records, state.privacy.permissionDecisions)
    });
  }

  private async preflightPromptingOperation(state: AppState, capability: 'Contacts' | 'Calendar', signal: AbortSignal) {
    const check = await this.dependencies.preflightPermission(state, capability, signal);
    await this.persistPermissionRecords(check.records);
    return {
      check,
      allowed: permissionCanStartPromptingOperation(check.authorization, check.record.canAskAgain)
    };
  }

  private async refreshPermissionsAfterPrompt() {
    try {
      const records = await this.dependencies.refreshPermissions(
        this.dependencies.getState(),
        new AbortController().signal
      );
      await this.persistPermissionRecords(records);
      return records;
    } catch {
      // A foreground refresh will retry. Callers still return a bounded recovery
      // instead of exposing the native adapter error.
      return undefined;
    }
  }

  private permissionRecoveryFailure(
    capability: 'Contacts' | 'Calendar',
    operation: 'import' | 'export',
    previousAuthorization: SystemAuthorization,
    nativePermissionDenied = false
  ) {
    const authorization =
      this.dependencies.getState().privacy.permissionRecords?.[capability]?.systemAuthorization ?? 'unavailable';
    const previouslyUsable = previousAuthorization === 'granted' || previousAuthorization === 'limited';
    const prefix = capability === 'Contacts' ? 'contacts' : `calendar-${operation}`;
    if (authorization === 'unavailable' || authorization === 'not-enrolled') {
      return failure(
        `${prefix}-permission-status-unavailable`,
        capability === 'Contacts'
          ? 'Live Contacts authorization could not be verified. Use contacts.add now, or retry the import after permission status is available.'
          : operation === 'import'
            ? 'Live Calendar authorization could not be verified. Use events.add or events.import-text now, or retry later.'
            : 'Live Calendar authorization could not be verified. Events remain local; refresh permissions and retry later.'
      );
    }
    if (nativePermissionDenied && authorization === 'undetermined') {
      return failure(
        `${prefix}-permission-cancelled`,
        capability === 'Contacts'
          ? 'Contacts access was not completed. Use contacts.add now, or start the import again when ready.'
          : operation === 'import'
            ? 'Calendar access was not completed. Use events.add or events.import-text now, or start the import again.'
            : 'Calendar access was not completed. Events remain local; start the export again when ready.'
      );
    }
    const denied =
      authorization === 'denied' ||
      authorization === 'restricted' ||
      (nativePermissionDenied && (authorization === 'granted' || authorization === 'limited'));
    const revoked = denied && previouslyUsable;
    if (denied) {
      return failure(
        `${prefix}-permission-${revoked ? 'revoked' : 'denied'}`,
        capability === 'Contacts'
          ? `Contacts permission was ${revoked ? 'revoked' : 'denied'}. Existing data is unchanged; use contacts.add or restore Contacts access in device settings and retry.`
          : operation === 'import'
            ? `Calendar permission was ${revoked ? 'revoked' : 'denied'}. Existing data is unchanged; use events.add or events.import-text, or restore Calendar access and retry.`
            : `Calendar permission was ${revoked ? 'revoked' : 'denied'}. Events remain local; restore Calendar access in device settings and retry.`
      );
    }
    return failure(
      `${prefix}-native-failed`,
      capability === 'Contacts'
        ? 'Device contact import did not complete. Existing data is unchanged; retry or use contacts.add.'
        : operation === 'import'
          ? 'Device calendar import did not complete. Existing data is unchanged; retry or use events.add or events.import-text.'
          : 'Device calendar export did not complete. Events remain local and reconciliation is safe to retry.',
      true
    );
  }

  private sensitiveAuthorizationAvailable(state: AppState) {
    if (!state.settings.biometricLockEnabled) return true;
    if (this.dependencies.now().getTime() > this.sensitiveUnlockedUntil) return false;
    this.sensitiveUnlockedUntil = 0;
    return true;
  }

  private emailAttemptIsLocked(state: AppState, messageId: string) {
    if (this.emailDeliveryLocks.has(messageId)) return true;
    const message = state.messages.find(item => item.id === messageId);
    return (
      message?.status === 'Delivery pending' ||
      message?.status === 'Delivery unknown' ||
      message?.status === 'Sent' ||
      message?.emailDeliveryAttempt?.status === 'Accepted' ||
      message?.emailDeliveryAttempt?.status === 'Unknown' ||
      message?.emailDeliveryAttempt?.status === 'Sent'
    );
  }

  private createTransientToken() {
    const token = this.dependencies.createConfirmationToken().trim();
    return /^[A-Za-z0-9][A-Za-z0-9:._-]{15,159}$/.test(token) ? token : undefined;
  }

  private domainActionApplied(
    before: AppState,
    after: AppState,
    action: Extract<HarnessCommand, { type: 'domain.dispatch' }>['action']
  ) {
    switch (action.type) {
      case 'addManualEvent':
        return after.events.some(event => !before.events.some(previous => previous.id === event.id));
      case 'toggleChecklist':
        return (
          JSON.stringify(before.events.find(event => event.id === action.eventId)) !==
          JSON.stringify(after.events.find(event => event.id === action.eventId))
        );
      case 'editMessage':
        return (
          before.messages.find(message => message.id === action.messageId)?.body !==
          after.messages.find(message => message.id === action.messageId)?.body
        );
      case 'approveMessage':
        return after.messages.find(message => message.id === action.messageId)?.status === 'Scheduled';
      case 'rejectMessage':
        return (
          before.messages.find(message => message.id === action.messageId)?.status !== 'Rejected' &&
          after.messages.find(message => message.id === action.messageId)?.status === 'Rejected'
        );
      case 'revokeMessage':
        return (
          before.messages.find(message => message.id === action.messageId)?.status !== 'Needs review' &&
          after.messages.find(message => message.id === action.messageId)?.status === 'Needs review'
        );
      default:
        return materialStateFingerprint(before) !== materialStateFingerprint(after);
    }
  }

  private async runDomainAction(command: Extract<HarnessCommand, { type: 'domain.dispatch' }>, signal: AbortSignal) {
    const before = this.dependencies.getState();
    assertNotAborted(signal);
    await this.dispatch(command.action);
    const after = this.dependencies.getState();
    return succeeded({
      kind: 'domain-action',
      actionType: command.action.type,
      ...localActionResult(before, after, this.domainActionApplied(before, after, command.action))
    });
  }

  private runContactsQuery(command: Extract<HarnessCommand, { type: 'contacts.query' }>) {
    const state = this.dependencies.getState();
    const query = normalizedSearchText(command.query ?? '');
    const now = this.dependencies.now();
    const healthByContact = buildRelationshipHealthInsights(state, now);
    const enrichmentByContact = buildContactEnrichmentPlans(state);
    const contactIdsWithEvents = new Set<string>();
    const nextEventByContact = new Map<string, { event: AppState['events'][number]; occurrence: string }>();
    for (const event of state.events) {
      contactIdsWithEvents.add(event.contactId);
      const occurrence = eventOccurrenceIso(event, now);
      if (!occurrence) continue;
      const existing = nextEventByContact.get(event.contactId);
      if (!existing || occurrence < existing.occurrence) {
        nextEventByContact.set(event.contactId, { event, occurrence });
      }
    }
    const channelMissing = (contact: AppState['contacts'][number]) => {
      const channel = resolveContactPreferencesForContact(state.settings, contact).preferredChannel;
      const routes = allContactRoutes(contact);
      return channel === 'SMS' || channel === 'WhatsApp'
        ? !routes.some(route => route.type === 'Phone')
        : channel === 'Email'
          ? !routes.some(route => route.type === 'Email')
          : false;
    };
    const items: ContactQueryItem[] = [];
    for (const contact of state.contacts) {
      if (!command.includeArchived && contact.archivedAt) continue;
      if (command.group && contact.group !== command.group) continue;
      if (command.vip !== undefined && contact.isVip !== command.vip) continue;
      const missingEvent = !contactIdsWithEvents.has(contact.id);
      if (command.missingEvent !== undefined && missingEvent !== command.missingEvent) continue;
      const isChannelMissing = channelMissing(contact);
      if (command.missingChannel !== undefined && isChannelMissing !== command.missingChannel) continue;
      const relationshipHealth = healthByContact.get(contact.id);
      if (!relationshipHealth) {
        return failure('query-state-invalid', 'Relationship health could not be derived for a stored contact.');
      }
      if (command.lowHealth !== undefined && relationshipHealth.score < 60 !== command.lowHealth) continue;
      if (
        command.needsPersonalization !== undefined &&
        (enrichmentByContact.get(contact.id)?.score ?? 0) < 50 !== command.needsPersonalization
      ) {
        continue;
      }
      const routes = safeContactRoutes(allContactRoutes(contact));
      if (contact.archivedAt && !safeIso(contact.archivedAt)) {
        return failure(
          'query-state-invalid',
          'Stored contact metadata is not safe to expose through the command boundary.'
        );
      }
      if (
        !boundedPrivateString(contact.name, MAX_CONTACT_NAME_LENGTH) ||
        !boundedPrivateString(contact.relationship, MAX_CONTACT_NAME_LENGTH, true) ||
        (contact.relationshipSubtype !== undefined &&
          !boundedPrivateString(contact.relationshipSubtype, MAX_CONTACT_NAME_LENGTH, true)) ||
        (contact.jobTitle !== undefined && !boundedPrivateString(contact.jobTitle, MAX_CONTACT_NAME_LENGTH, true)) ||
        !routes
      ) {
        return failure(
          'query-state-invalid',
          'Stored contact review data exceeds the supported private output bounds.'
        );
      }
      if (
        query &&
        !normalizedSearchText(
          [
            contact.name,
            contact.relationship,
            contact.relationshipSubtype,
            contact.jobTitle,
            contact.phone,
            contact.email,
            contact.notesSummary,
            ...(contact.routes?.map(route => route.value) ?? [])
          ]
            .filter(Boolean)
            .join(' ')
        ).includes(query)
      ) {
        continue;
      }
      const enrichmentScore = enrichmentByContact.get(contact.id)?.score ?? 0;
      const nextEvent = nextEventByContact.get(contact.id);
      const preferences = resolveContactPreferencesForContact(state.settings, contact);
      const qualityLabels: ContactQueryItem['qualityLabels'] = [];
      if (contact.isVip) qualityLabels.push('VIP');
      if (!nextEvent) qualityLabels.push('Missing event');
      if (isChannelMissing) qualityLabels.push('Missing channel');
      if (relationshipHealth.score < 60) qualityLabels.push('Low health');
      if (enrichmentScore < 50) qualityLabels.push('Needs details');
      items.push({
        id: contact.id,
        name: contact.name,
        relationship: contact.relationship,
        relationshipSubtype: contact.relationshipSubtype,
        jobTitle: contact.jobTitle,
        routes,
        archived: Boolean(contact.archivedAt),
        archivedAt: safeIso(contact.archivedAt),
        group: contact.group,
        preferredChannel: preferences.preferredChannel,
        language: contact.language,
        isVip: contact.isVip,
        dnd: contact.dnd,
        checkInCadenceDays: preferences.checkInCadenceDays,
        healthScore: relationshipHealth.score,
        personalizationScore: enrichmentScore,
        qualityLabels,
        nextEvent: nextEvent
          ? {
              id: nextEvent.event.id,
              eventType: nextEvent.event.type,
              label: nextEvent.event.label,
              occurrence: nextEvent.occurrence
            }
          : undefined
      });
    }
    const page = pageByOpaqueId(
      items,
      command.cursor,
      command.limit,
      command.includeArchived,
      item => item.id,
      (left, right) => {
        if (command.sort === 'Health') {
          return left.healthScore - right.healthScore;
        }
        if (command.sort === 'Upcoming event') {
          const leftOccurrence = left.nextEvent?.occurrence;
          const rightOccurrence = right.nextEvent?.occurrence;
          if (!leftOccurrence) return rightOccurrence ? 1 : 0;
          if (!rightOccurrence) return -1;
          return leftOccurrence.localeCompare(rightOccurrence);
        }
        return normalizedSearchText(left.name).localeCompare(normalizedSearchText(right.name));
      }
    );
    return page
      ? this.boundedPrivateValue({ kind: 'contacts-page', ...page })
      : failure('query-cursor-invalid', 'The query cursor or stored identifiers are no longer valid.');
  }

  private runEventsQuery(command: Extract<HarnessCommand, { type: 'events.query' }>) {
    const state = this.dependencies.getState();
    const contacts = new Map(state.contacts.map(contact => [contact.id, contact]));
    const query = normalizedSearchText(command.query ?? '');
    const requestedYear = command.month ? Number(command.month.slice(0, 4)) : undefined;
    const items: EventQueryItem[] = [];
    for (const event of state.events) {
      const contact = contacts.get(event.contactId);
      const presentedEvent = requestedYear === undefined ? event : eventOccurrenceInYear(event, requestedYear);
      const preparationReference =
        requestedYear === undefined
          ? this.dependencies.now()
          : new Date(`${presentedEvent?.date ?? `${requestedYear}-01-01`}T12:00:00`);
      const preparation = buildEventPreparationPlan(state, event.id, preparationReference);
      if (command.month && (!presentedEvent || presentedEvent.date.slice(0, 7) !== command.month)) continue;
      if (!presentedEvent || !contact || !safeOpaqueId(event.contactId) || !safeIso(presentedEvent.date)) {
        return failure(
          'query-state-invalid',
          'Stored event metadata is not safe to expose through the command boundary.'
        );
      }
      if (!command.includeArchived && contact.archivedAt) continue;
      if (command.eventType && event.type !== command.eventType) continue;
      if (query && !normalizedSearchText(`${event.label} ${event.type} ${contact.name}`).includes(query)) {
        continue;
      }
      if (
        !boundedPrivateString(contact.name, MAX_CONTACT_NAME_LENGTH) ||
        !boundedPrivateString(event.label, MAX_EVENT_LABEL_LENGTH) ||
        !preparation.ok ||
        preparation.steps.length > MAX_EVENT_CHECKLIST_ITEMS ||
        preparation.steps.some(
          item =>
            !safeOpaqueId(item.id) ||
            !boundedPrivateString(item.label, MAX_EVENT_CHECKLIST_LABEL_LENGTH, true) ||
            typeof item.done !== 'boolean'
        )
      ) {
        return failure('query-state-invalid', 'Stored event review data exceeds the supported private output bounds.');
      }
      items.push({
        id: event.id,
        contactId: event.contactId,
        contactName: contact.name,
        contactArchived: Boolean(contact.archivedAt),
        eventType: event.type,
        label: event.label,
        date: presentedEvent.date,
        verified: event.verified,
        source: event.source,
        checklist: preparation.steps.map(item => ({ id: item.id, label: item.label, done: item.done }))
      });
    }
    const page = pageByOpaqueId(
      items,
      command.cursor,
      command.limit,
      command.includeArchived,
      item => item.id,
      (left, right) => {
        if (command.sort === 'Contact') {
          return normalizedSearchText(contacts.get(left.contactId)?.name ?? '').localeCompare(
            normalizedSearchText(contacts.get(right.contactId)?.name ?? '')
          );
        }
        if (command.sort === 'Type') return left.eventType.localeCompare(right.eventType);
        return left.date.localeCompare(right.date);
      }
    );
    return page
      ? this.boundedPrivateValue({ kind: 'events-page', ...page })
      : failure('query-cursor-invalid', 'The query cursor or stored identifiers are no longer valid.');
  }

  private runMessagesQuery(command: Extract<HarnessCommand, { type: 'messages.query' }>) {
    const state = this.dependencies.getState();
    const contacts = new Map(state.contacts.map(contact => [contact.id, contact]));
    const inboxState = command.includeArchived
      ? state
      : {
          ...state,
          messages: state.messages.filter(message => !contacts.get(message.contactId)?.archivedAt)
        };
    const inbox = buildMessageInbox(inboxState, {
      tab: command.tab,
      channel: command.channel,
      query: command.query,
      sort: command.sort,
      emailEndpointConfigured: productAvailability.authenticatedEmailProvider.available,
      nowIso: this.dependencies.now().toISOString()
    });
    const inboxOrder = new Map(inbox.rows.map((row, index) => [row.message.id, index]));
    const inboxRows = new Map(inbox.rows.map(row => [row.message.id, row]));
    const items: MessageQueryItem[] = [];
    for (const message of state.messages) {
      const contact = contacts.get(message.contactId);
      if (!contact || !safeOpaqueId(message.contactId) || (message.eventId && !safeOpaqueId(message.eventId))) {
        return failure(
          'query-state-invalid',
          'Stored message metadata is not safe to expose through the command boundary.'
        );
      }
      const dates = [message.scheduledFor, message.sentAt, message.approvedAt, message.approvalExpiresAt];
      if (dates.some(value => value !== undefined && !safeIso(value))) {
        return failure(
          'query-state-invalid',
          'Stored message dates are not safe to expose through the command boundary.'
        );
      }
      if (message.scheduledTimeZone !== undefined && !normalizeScheduleTimeZone(message.scheduledTimeZone)) {
        return failure(
          'query-state-invalid',
          'Stored message schedule time-zone metadata is not safe to expose through the command boundary.'
        );
      }
      if (!command.includeArchived && contact.archivedAt) continue;
      if (command.status && message.status !== command.status) continue;
      if (!inboxOrder.has(message.id)) continue;
      const inboxRow = inboxRows.get(message.id);
      if (
        !inboxRow ||
        !boundedPrivateString(inboxRow.contactName, MAX_CONTACT_NAME_LENGTH) ||
        (inboxRow.eventLabel !== undefined && !boundedPrivateString(inboxRow.eventLabel, MAX_EVENT_LABEL_LENGTH)) ||
        (inboxRow.recovery !== undefined &&
          (!boundedPrivateString(inboxRow.recovery.title, MAX_READINESS_LENGTH) ||
            !boundedPrivateString(inboxRow.recovery.detail, MAX_REVIEW_ERROR_LENGTH) ||
            !boundedPrivateString(inboxRow.recovery.actionLabel, MAX_READINESS_LENGTH) ||
            !parseHarnessCommand(inboxRow.recovery.command).ok))
      ) {
        return failure('query-state-invalid', 'Stored message inbox context exceeds safe output bounds.');
      }
      const bodyPolicy = validateMessageBodyForChannel(message);
      const issues = [
        bodyPolicy.ok ? bodyPolicy.warning : bodyPolicy.message,
        messageApprovalWindowIssue(message, this.dependencies.now().toISOString()),
        messageApprovalRouteIssue(state, message),
        message.status === 'Scheduled' ? messageDispatchTimingIssue(state, message, this.dependencies.now()) : undefined
      ].filter((issue): issue is string => Boolean(issue));
      if (
        !boundedPrivateString(message.body, MAX_REVIEW_MESSAGE_LENGTH, true) ||
        !boundedPrivateString(message.variants.short, MAX_REVIEW_MESSAGE_LENGTH, true) ||
        !boundedPrivateString(message.variants.standard, MAX_REVIEW_MESSAGE_LENGTH, true) ||
        !boundedPrivateString(message.variants.warm, MAX_REVIEW_MESSAGE_LENGTH, true) ||
        !boundedPrivateString(message.readiness, MAX_READINESS_LENGTH, true) ||
        (message.lastError !== undefined && !boundedPrivateString(message.lastError, MAX_REVIEW_ERROR_LENGTH, true)) ||
        (message.duplicateWarning !== undefined &&
          !boundedPrivateString(message.duplicateWarning, MAX_REVIEW_ERROR_LENGTH, true)) ||
        issues.some(issue => !boundedPrivateString(issue, MAX_REVIEW_ERROR_LENGTH, true))
      ) {
        return failure(
          'query-state-invalid',
          'Stored message review data exceeds the supported private output bounds.'
        );
      }
      items.push({
        id: message.id,
        contactId: message.contactId,
        contactName: inboxRow.contactName,
        eventId: message.eventId,
        eventLabel: inboxRow.eventLabel,
        contactArchived: Boolean(contact.archivedAt),
        reason: message.reason,
        status: message.status,
        channel: message.channel,
        body: message.body,
        variants: { ...message.variants },
        selectedVariant: message.selectedVariant,
        quality: message.quality,
        readiness: message.readiness,
        error: message.lastError,
        issues: [...new Set(issues)],
        duplicateWarning: message.duplicateWarning,
        scheduledFor: message.scheduledFor,
        scheduledTimeZone: message.scheduledTimeZone,
        sentAt: message.sentAt,
        approvedAt: message.approvedAt,
        approvalExpiresAt: message.approvalExpiresAt,
        duplicateRisk: Boolean(message.duplicateWarning),
        duplicateAcknowledged: Boolean(message.duplicateAcknowledged),
        recovery: inboxRow.recovery ? { ...inboxRow.recovery } : undefined
      });
    }
    const page = pageByOpaqueId(
      items,
      command.cursor,
      command.limit,
      command.includeArchived,
      item => item.id,
      (left, right) =>
        (inboxOrder.get(left.id) ?? Number.MAX_SAFE_INTEGER) - (inboxOrder.get(right.id) ?? Number.MAX_SAFE_INTEGER)
    );
    return page
      ? this.boundedPrivateValue({
          kind: 'messages-page',
          ...page,
          counts: { ...inbox.counts },
          emptyState: inbox.emptyState
        })
      : failure('query-cursor-invalid', 'The query cursor or stored identifiers are no longer valid.');
  }

  private runBulkMessagePreview(command: Extract<HarnessCommand, { type: 'messages.bulk-preview' }>) {
    const state = this.dependencies.getState();
    const report = safeMessageBulkReport(
      buildMessageBulkActionReport(state, command.messageIds, command.action, this.dependencies.now())
    );
    if (!report) {
      this.pendingBulkMessageAction = undefined;
      return failure('bulk-preview-invalid', 'The selected bulk action could not be represented safely.');
    }
    const confirmationToken = this.createTransientToken();
    if (!confirmationToken) {
      this.pendingBulkMessageAction = undefined;
      return failure('bulk-preview-unavailable', 'A safe temporary bulk confirmation could not be created.');
    }
    const previewReportFingerprint = messageBulkReportFingerprint(report);
    const fingerprintPayload = `${confirmationToken}|${command.action}|${command.messageIds.join('|')}|${previewReportFingerprint}`;
    const confirmationFingerprint = `bulk-message-v1-${fnvFingerprint(fingerprintPayload)}${fnvFingerprint(
      fingerprintPayload,
      0x9e3779b9
    )}`;
    const expiresAt = this.dependencies.now().getTime() + BULK_MESSAGE_CONFIRMATION_WINDOW_MS;
    this.pendingBulkMessageAction = {
      confirmationToken,
      confirmationFingerprint,
      action: command.action,
      messageIds: [...command.messageIds],
      baseStateFingerprint: contentFreeMaterialFingerprint(state),
      previewReportFingerprint,
      expiresAt
    };
    return this.boundedPrivateValue({
      kind: 'bulk-message-preview',
      action: report.action,
      selectedCount: report.selectedCount,
      eligibleCount: report.eligibleMessageIds.length,
      skippedCount: report.skipped.length,
      eligibleMessageIds: report.eligibleMessageIds,
      skipped: report.skipped,
      summary: report.summary,
      verificationGuidance: report.verificationGuidance,
      requiresConfirmation: true,
      confirmationToken,
      confirmationFingerprint,
      expiresAt: new Date(expiresAt).toISOString(),
      redacted: true
    });
  }

  private async runBulkMessageApply(
    command: Extract<HarnessCommand, { type: 'messages.bulk-apply' }>,
    signal: AbortSignal
  ) {
    const pending = this.pendingBulkMessageAction;
    if (
      !pending ||
      pending.confirmationToken !== command.confirmationToken ||
      this.dependencies.now().getTime() > pending.expiresAt
    ) {
      return failure(
        'bulk-preview-stale',
        'The bulk confirmation is missing, expired, or no longer current. Preview the selection again.'
      );
    }
    const before = this.dependencies.getState();
    if (contentFreeMaterialFingerprint(before) !== pending.baseStateFingerprint) {
      this.pendingBulkMessageAction = undefined;
      return failure(
        'bulk-preview-stale',
        'Message or delivery state changed after preview. Preview the selection again before applying it.'
      );
    }
    const now = this.dependencies.now();
    const report = safeMessageBulkReport(buildMessageBulkActionReport(before, pending.messageIds, pending.action, now));
    if (!report || messageBulkReportFingerprint(report) !== pending.previewReportFingerprint) {
      this.pendingBulkMessageAction = undefined;
      return failure(
        'bulk-preview-stale',
        'Bulk eligibility changed after preview. Preview the selection again before applying it.'
      );
    }

    this.pendingBulkMessageAction = undefined;
    assertNotAborted(signal);
    await this.dispatch({
      type: 'bulkMessageAction',
      action: pending.action,
      messageIds: pending.messageIds,
      nowIso: now.toISOString()
    });
    const after = this.dependencies.getState();
    const appliedMessageIds: string[] = [];
    const skipped = [...report.skipped];
    for (const messageId of report.eligibleMessageIds) {
      const message = after.messages.find(item => item.id === messageId);
      if (bulkMessageTransitionApplied(pending.action, message)) {
        appliedMessageIds.push(messageId);
        continue;
      }
      const reason = message?.lastError ?? message?.readiness;
      skipped.push({
        messageId,
        reason: boundedPrivateString(reason, MAX_REVIEW_ERROR_LENGTH)
          ? reason
          : 'The message no longer satisfied the selected bulk action.'
      });
    }
    if (appliedMessageIds.length + skipped.length !== report.selectedCount) {
      return failure('bulk-apply-result-invalid', 'The committed bulk result could not be represented safely.');
    }
    const summary = `${appliedMessageIds.length}/${report.selectedCount} selected message(s) were processed; ${skipped.length} skipped.`;
    return this.boundedPrivateValue({
      kind: 'bulk-message-apply',
      action: pending.action,
      selectedCount: report.selectedCount,
      appliedCount: appliedMessageIds.length,
      skippedCount: skipped.length,
      appliedMessageIds,
      skipped,
      summary,
      confirmationFingerprint: pending.confirmationFingerprint,
      redacted: true
    });
  }

  private runCheckInsQuery(command: Extract<HarnessCommand, { type: 'checkins.query' }>) {
    const state = this.dependencies.getState();
    const contacts = new Map(state.contacts.map(contact => [contact.id, contact]));
    const queue = buildCheckInReminderQueue(state, this.dependencies.now());
    const items = [...queue.due, ...queue.snoozed, ...queue.current]
      .filter(reminder => !command.status || reminder.status === command.status)
      .filter(reminder => command.includeArchived || !contacts.get(reminder.contactId)?.archivedAt)
      .map(reminder => ({
        contactId: reminder.contactId,
        contactArchived: Boolean(contacts.get(reminder.contactId)?.archivedAt),
        status: reminder.status,
        cadenceDays: reminder.cadenceDays,
        daysSinceContact: reminder.daysSinceContact,
        overdueDays: reminder.overdueDays,
        lastContactedAt: safeIso(reminder.lastContactedAt),
        snoozedUntil: safeIso(reminder.snoozedUntil)
      }));
    if (
      items.some(
        item =>
          !contacts.has(item.contactId) ||
          !safeOpaqueId(item.contactId) ||
          !Number.isSafeInteger(item.cadenceDays) ||
          !Number.isSafeInteger(item.overdueDays)
      )
    ) {
      return failure(
        'query-state-invalid',
        'Stored check-in metadata is not safe to expose through the command boundary.'
      );
    }
    const page = pageByOpaqueId(items, command.cursor, command.limit, command.includeArchived, item => item.contactId);
    return page
      ? this.boundedPrivateValue({ kind: 'checkins-page', ...page })
      : failure('query-cursor-invalid', 'The query cursor or stored identifiers are no longer valid.');
  }

  private runContactInspection(command: Extract<HarnessCommand, { type: 'contacts.inspect' }>) {
    const state = this.dependencies.getState();
    const contact = this.activeContact(command.contactId);
    if (!contact) return failure('contact-unavailable', 'Choose an active contact before reviewing private details.');
    const routes = safeContactRoutes(allContactRoutes(contact));
    const enrichment = buildContactEnrichmentPlan(state, contact.id);
    const relationshipHealth = buildRelationshipHealthInsight(state, contact.id, this.dependencies.now());
    const preferences = resolveContactPreferencesForContact(state.settings, contact);
    const relatedEvents = state.events.filter(event => event.contactId === contact.id);
    const nextEvent = relatedEvents
      .map(event => ({ event, occurrence: eventOccurrenceIso(event, this.dependencies.now()) }))
      .filter((item): item is { event: (typeof relatedEvents)[number]; occurrence: string } => Boolean(item.occurrence))
      .sort((left, right) => left.occurrence.localeCompare(right.occurrence))[0];
    if (
      !routes ||
      !boundedPrivateString(contact.name, MAX_CONTACT_NAME_LENGTH) ||
      (contact.relationshipSubtype !== undefined &&
        !boundedPrivateString(contact.relationshipSubtype, MAX_CONTACT_NAME_LENGTH, true)) ||
      (contact.jobTitle !== undefined && !boundedPrivateString(contact.jobTitle, MAX_CONTACT_NAME_LENGTH, true)) ||
      !relationshipHealth ||
      !Number.isSafeInteger(relationshipHealth.score) ||
      relationshipHealth.reasons.some(reason => !boundedPrivateString(reason, MAX_REVIEW_ERROR_LENGTH))
    ) {
      return failure('contact-output-invalid', 'Stored contact details exceed the private output bounds.');
    }
    return this.featureInspection('contact-detail', {
      id: contact.id,
      name: contact.name,
      relationship: contact.relationship,
      relationshipSubtype: contact.relationshipSubtype,
      jobTitle: contact.jobTitle,
      group: contact.group,
      routes,
      language: contact.language,
      notesSummary: contact.notesSummary,
      healthScore: relationshipHealth.score,
      relationshipHealth,
      isVip: contact.isVip,
      dnd: contact.dnd,
      annualGiftBudget: contact.annualGiftBudget,
      lastContactedAt: safeIso(contact.lastContactedAt),
      checkInSnoozedUntil: safeIso(contact.checkInSnoozedUntil),
      preferences,
      personalization: enrichment
        ? { score: enrichment.score, label: enrichment.label, missingSignals: enrichment.missingSignals }
        : undefined,
      relatedCounts: {
        events: relatedEvents.length,
        memories: state.memories.filter(memory => memory.contactId === contact.id).length,
        gifts: state.gifts.filter(gift => gift.contactId === contact.id).length,
        messages: state.messages.filter(message => message.contactId === contact.id).length
      },
      nextEvent: nextEvent
        ? {
            id: nextEvent.event.id,
            type: nextEvent.event.type,
            label: nextEvent.event.label,
            occurrence: nextEvent.occurrence,
            verified: nextEvent.event.verified
          }
        : undefined
    });
  }

  private runContactPreferencesInspection(command: Extract<HarnessCommand, { type: 'contacts.preferences.inspect' }>) {
    const contact = this.activeContact(command.contactId);
    if (!contact) return failure('contact-unavailable', 'Choose an active contact before reviewing preferences.');
    const resolved = resolveContactPreferencesForContact(this.dependencies.getState().settings, contact);
    return this.featureInspection('contact-preferences', {
      contactId: contact.id,
      group: contact.group,
      isVip: contact.isVip,
      dnd: contact.dnd,
      language: contact.language,
      customSendTime: resolved.customSendTime,
      effectiveSendTime: resolved.customSendTime ?? this.dependencies.getState().settings.defaultSendTime,
      quietHoursBehavior: resolved.quietHoursBehavior,
      skipAuto: resolved.skipAuto,
      resolved,
      explicitOverrides: contact.preferenceOverrides ?? null,
      supported: {
        tones: ['Warm', 'Respectful', 'Playful', 'Concise', 'Formal', 'Hinglish', 'No emoji'],
        channels: ['SMS', 'WhatsApp', 'Email', 'Manual'],
        cadences: [14, 30, 45, 60, 90],
        automationModes: ['Always ask', 'Smart approve', 'VIP approve']
      },
      consequences: {
        customSendTime: 'Applies to event-linked schedules before global quiet hours and blackouts.',
        quietHoursBehavior:
          resolved.quietHoursBehavior === 'Block'
            ? 'Blocks intended send times inside global quiet hours.'
            : 'Defers intended send times to the next globally allowed window.',
        skipAuto: resolved.skipAuto
          ? 'Skips proactive draft generation; explicit user requests still create review-first drafts.'
          : 'Allows proactive draft preparation under the active review-first policy.'
      }
    });
  }

  private runGroupDefaultsInspection() {
    const settings = this.dependencies.getState().settings;
    return this.featureInspection('group-defaults', {
      groups: Object.entries(settings.groupDefaults).map(([group, defaults]) => ({ group, ...defaults })),
      fullyAutoAvailable: productAvailability.durableUnattendedAutomation.available,
      unavailableReason: productAvailability.durableUnattendedAutomation.reason
    });
  }

  private runEnrichmentInspection(command: Extract<HarnessCommand, { type: 'contacts.enrichment.inspect' }>) {
    const contact = this.activeContact(command.contactId);
    if (!contact) return failure('contact-unavailable', 'Restore or choose an active contact before enrichment.');
    const plan = buildContactEnrichmentPlan(this.dependencies.getState(), contact.id);
    if (!plan) return failure('enrichment-unavailable', 'Contact enrichment is no longer available.');
    return this.featureInspection('contact-enrichment', {
      contactId: plan.contactId,
      score: plan.score,
      label: plan.label,
      summary: plan.summary,
      completedSignals: plan.completedSignals,
      missingSignals: plan.missingSignals,
      prompts: plan.prompts.map(prompt => ({
        id: prompt.id,
        question: prompt.question,
        reason: prompt.reason,
        category: prompt.category,
        improvesSignal: prompt.improvesSignal
      }))
    });
  }

  private runEventPreparationInspection(command: Extract<HarnessCommand, { type: 'events.preparation.inspect' }>) {
    const state = this.dependencies.getState();
    const event = state.events.find(item => item.id === command.eventId);
    if (!event || !this.activeContact(event.contactId)) {
      return failure('event-unavailable', 'Choose an event linked to an active contact before preparation.');
    }
    const plan = buildEventPreparationPlan(state, event.id);
    if (!plan.ok) return failure('event-preparation-unavailable', plan.error);
    return this.featureInspection('event-preparation', {
      eventId: event.id,
      contactId: event.contactId,
      completedCount: plan.completedCount,
      totalCount: plan.totalCount,
      isComplete: plan.isComplete,
      summary: plan.summary,
      nextStepId: plan.nextStep?.id,
      steps: plan.steps.map(step => ({
        id: step.id,
        label: step.label,
        done: step.done,
        status: step.status,
        detail: step.detail,
        actionLabel: step.actionLabel,
        targetScreen: step.targetScreen,
        canToggle: step.canToggle
      }))
    });
  }

  private runMessagePreview(command: Extract<HarnessCommand, { type: 'messages.preview' }>) {
    const state = this.dependencies.getState();
    const message = state.messages.find(item => item.id === command.messageId);
    const contact = message ? this.activeContact(message.contactId) : undefined;
    if (!message || !contact)
      return failure('message-unavailable', 'Choose a message for an active contact before preview.');
    if (
      message.eventId &&
      !state.events.some(event => event.id === message.eventId && event.contactId === contact.id)
    ) {
      return failure('message-context-stale', 'The message event context is no longer valid.');
    }
    const excludedIds = new Set(command.excludedMemoryIds ?? []);
    if (
      [...excludedIds].some(
        memoryId => !state.memories.some(memory => memory.id === memoryId && memory.contactId === contact.id)
      )
    ) {
      return failure('message-context-stale', 'One or more excluded memory choices are no longer valid.');
    }
    const eligibleMemories = state.memories.filter(
      memory => memory.contactId === contact.id && memory.category !== 'Private' && !excludedIds.has(memory.id)
    );
    const preferences = resolveContactPreferencesForContact(state.settings, contact);
    const bodyPolicy = validateMessageBodyForChannel(message);
    const routeTest = buildMessageTestPlan(state, message);
    return this.featureInspection('message-preview', {
      messageId: message.id,
      contactId: contact.id,
      eventId: message.eventId,
      body: message.body,
      variants: { ...message.variants },
      selectedVariant: message.selectedVariant,
      status: message.status,
      channel: message.channel,
      quality: message.quality,
      readiness: message.readiness,
      effectiveTone: preferences.tone,
      languageTarget: contact.language,
      preferenceSources: preferences.sources,
      bodyPolicy,
      routeTest,
      approvalIssue: messageApprovalWindowIssue(message, this.dependencies.now().toISOString()),
      privacy: {
        includedMemoryIds: eligibleMemories.slice(0, 5).map(memory => memory.id),
        includedMemoryCount: Math.min(5, eligibleMemories.length),
        excludedOptionalMemoryCount: excludedIds.size,
        excludedPrivateMemoryCount: state.memories.filter(
          memory => memory.contactId === contact.id && memory.category === 'Private'
        ).length,
        includePriorMessages: command.includePriorMessages,
        includedPriorMessageCount: command.includePriorMessages
          ? Math.min(3, state.messages.filter(item => item.contactId === contact.id && item.status === 'Sent').length)
          : 0,
        fields: ['relationship', 'event', 'style profile', 'selected non-private memories', 'prior sent messages']
      }
    });
  }

  private runTemplateInspection(command: Extract<HarnessCommand, { type: 'templates.inspect' }>) {
    const state = this.dependencies.getState();
    const contactId = command.contactId ?? state.contacts.find(contact => !contact.archivedAt)?.id;
    if (contactId && !this.activeContact(contactId)) {
      return failure('contact-unavailable', 'Choose an active contact before personalizing a template.');
    }
    const library = buildMessageTemplateLibrary(state, {
      contactId,
      reason: command.reason,
      tone: command.tone,
      selectedTemplateId: command.templateId,
      draftBody: command.draftBody
    });
    return this.featureInspection('template-library', {
      outcome: library.ok ? 'ready' : 'blocked',
      contactId: library.ok ? library.contact.id : contactId,
      reason: library.reason,
      selectedTone: library.selectedTone,
      toneOptions: library.toneOptions,
      templates: library.templates.map(template => ({
        id: template.id,
        title: template.title,
        tone: template.tone,
        language: template.language
      })),
      selectedTemplateId: library.ok ? library.selectedTemplate.id : undefined,
      templateSelection: library.ok ? { ...library.templateSelection } : undefined,
      renderedBody: library.renderedBody,
      characterCount: library.characterCount,
      contextDetail: library.contextDetail,
      action: library.action,
      error: library.ok ? undefined : library.error
    });
  }

  private runMemoryQuery(command: Extract<HarnessCommand, { type: 'memories.query' }>) {
    if (!this.activeContact(command.contactId)) {
      return failure('contact-unavailable', 'Memory Vault is available only for active contacts.');
    }
    const report = buildMemoryVaultReport(this.dependencies.getState(), command.contactId, command.query);
    const items = report.notes.map(item => ({
      id: item.note.id,
      contactId: item.note.contactId,
      category: item.note.category,
      body: item.note.body,
      pinned: item.note.pinned,
      createdAt: item.note.createdAt,
      aiUseLabel: item.aiUseLabel
    }));
    return this.featurePage('memories', items, command.cursor, command.limit, item => item.id as string, undefined, {
      query: report.query,
      totalCount: report.totalCount,
      visibleCount: report.visibleCount,
      pinnedCount: report.pinnedCount,
      privateCount: report.privateCount,
      aiEligibleCount: report.aiEligibleCount,
      emptyMessage: report.emptyMessage
    });
  }

  private runTimelineQuery(command: Extract<HarnessCommand, { type: 'timeline.query' }>) {
    if (!this.activeContact(command.contactId)) {
      return failure('contact-unavailable', 'Relationship timeline is available only for active contacts.');
    }
    const timeline = buildContactTimeline(this.dependencies.getState(), command.contactId, command.filter);
    const items = timeline.entries.map(entry => ({ ...entry }));
    return this.featurePage(
      'timeline',
      items,
      command.cursor,
      command.limit,
      item => item.id as string,
      (left, right) => String(right.dateIso).localeCompare(String(left.dateIso)),
      { filter: command.filter, emptyMessage: timeline.emptyMessage }
    );
  }

  private runChatQuery(command: Extract<HarnessCommand, { type: 'chat.query' }>) {
    if (!this.activeContact(command.contactId)) {
      return failure('contact-unavailable', 'Chat history is available only for active contacts.');
    }
    const history = buildChatHistory(this.dependencies.getState(), {
      contactId: command.contactId,
      searchQuery: command.query,
      channel: command.channel
    });
    const items = history.messages.map(message => ({
      id: message.id,
      contactId: message.contactId,
      reason: message.reason,
      channel: message.channel,
      body: message.body,
      sentAt: message.sentAt,
      quality: message.quality
    }));
    return this.featurePage(
      'chat',
      items,
      command.cursor,
      command.limit,
      item => item.id as string,
      (left, right) => String(right.sentAt ?? '').localeCompare(String(left.sentAt ?? '')),
      { query: command.query, channel: command.channel, emptyState: history.emptyState }
    );
  }

  private runGiftInspection(command: Extract<HarnessCommand, { type: 'gifts.inspect' }>) {
    const state = this.dependencies.getState();
    const contact = this.activeContact(command.contactId);
    if (!contact) return failure('contact-unavailable', 'Gift Advisor is available only for active contacts.');
    const gifts = state.gifts.filter(gift => gift.contactId === contact.id).map(gift => ({ ...gift }));
    return this.featurePage(
      'gifts',
      gifts,
      command.cursor,
      command.limit,
      item => item.id as string,
      (left, right) => Number(right.year) - Number(left.year),
      {
        budget: buildGiftBudgetSummary(contact, state.gifts, this.dependencies.now().getFullYear()),
        occasion: command.occasion,
        suggestions: buildGiftSuggestions(state, contact.id, command.occasion)
      }
    );
  }

  private runOnboardingInspection() {
    const plan = buildOnboardingPlan(this.dependencies.getState());
    return this.featureInspection('onboarding', {
      completed: plan.completed,
      currentStepId: plan.currentStepId,
      progress: plan.progress,
      summary: plan.summary,
      nextStep: plan.nextStep,
      steps: plan.steps,
      completionGate: plan.completionGate,
      goals: ['Reminders first', 'AI wishes', 'Manual relationship manager', 'Full setup'],
      selectedGoal: this.dependencies.getState().onboarding.selectedGoal
    });
  }

  private async runSetupWizardInspection(
    command: Extract<HarnessCommand, { type: 'setup.wizard.inspect' }>,
    signal: AbortSignal
  ) {
    const environment = await this.dependencies.setupEnvironment(signal);
    assertNotAborted(signal);
    const plan = buildSetupWizardPlan(this.dependencies.getState(), environment, command.goal);
    return this.featureInspection('setup-wizard', {
      goal: plan.goal,
      summary: plan.summary,
      readyCount: plan.readyCount,
      totalCount: plan.totalCount,
      recommendedStep: plan.recommendedStep
        ? {
            ...plan.recommendedStep,
            runCommand: {
              type: 'setup.wizard.run-action',
              goal: plan.goal,
              stepId: plan.recommendedStep.id
            }
          }
        : undefined,
      steps: plan.steps.map(step => ({
        id: step.id,
        title: step.title,
        detail: step.detail,
        status: step.status,
        action: step.action,
        targetScreen: step.targetScreen,
        command: step.command,
        runCommand: {
          type: 'setup.wizard.run-action',
          goal: plan.goal,
          stepId: step.id
        }
      })),
      providerAvailability: {
        authenticatedAiProvider: productAvailability.authenticatedAiProvider,
        authenticatedEmailProvider: productAvailability.authenticatedEmailProvider,
        durableUnattendedAutomation: productAvailability.durableUnattendedAutomation
      }
    });
  }

  private async runAiProviderReadinessTest(signal: AbortSignal) {
    const request = buildSyntheticAiProviderTestRequest();
    let response;
    try {
      response = await this.dependencies.requestAiDraft(request, signal);
      assertNotAborted(signal);
    } catch {
      if (signal.aborted) throw new Error('Operation cancelled.');
      const error: AiDraftError = {
        kind: 'network',
        message: aiProviderTestFailureMessages.network
      };
      await this.dispatch({
        type: 'aiProviderFailure',
        error,
        privacySummary: AI_PROVIDER_TEST_PRIVACY_SUMMARY
      });
      return succeeded({
        kind: 'setup-action',
        checkId: 'ai-provider',
        outcome: 'ai-provider-failed',
        aiTest: { ok: false, errorKind: error.kind, syntheticContext: true, redacted: true }
      });
    }

    if (response.ok) {
      await this.dispatch({
        type: 'aiProviderReady',
        privacySummary: AI_PROVIDER_TEST_PRIVACY_SUMMARY,
        observation: response.observation
      });
      return succeeded({
        kind: 'setup-action',
        checkId: 'ai-provider',
        outcome: 'ai-provider-ready',
        aiTest: { ok: true, syntheticContext: true, redacted: true }
      });
    }

    const error: AiDraftError = {
      kind: response.error.kind,
      message: aiProviderTestFailureMessages[response.error.kind]
    };
    await this.dispatch({
      type: 'aiProviderFailure',
      error,
      privacySummary: AI_PROVIDER_TEST_PRIVACY_SUMMARY,
      observation: response.observation
    });
    return succeeded({
      kind: 'setup-action',
      checkId: 'ai-provider',
      outcome: 'ai-provider-failed',
      aiTest: { ok: false, errorKind: error.kind, syntheticContext: true, redacted: true }
    });
  }

  private async runSetupWizardAction(
    command: Extract<HarnessCommand, { type: 'setup.wizard.run-action' }>,
    signal: AbortSignal
  ) {
    const environment = await this.dependencies.setupEnvironment(signal);
    assertNotAborted(signal);
    const plan = buildSetupWizardPlan(this.dependencies.getState(), environment, command.goal);
    const step = plan.steps.find(item => item.id === command.stepId);
    if (!step) {
      await this.dispatch({ type: 'navigate', screen: 'more' });
      return succeeded({ kind: 'setup-action', outcome: 'fallback', targetScreen: 'more' });
    }
    if (step.command === 'planReminders') {
      const reconciled = await this.runReminderReconciliation(
        { type: 'reminders.reconcile', reason: 'manual' },
        signal
      );
      if (reconciled.status !== 'succeeded') return reconciled;
      return succeeded({
        kind: 'setup-action',
        checkId: step.id,
        outcome: 'reminders-reconciled'
      });
    }
    if (step.command === 'testAiProvider') {
      return this.runAiProviderReadinessTest(signal);
    }
    const targetScreen = step.targetScreen ?? 'more';
    await this.dispatch({ type: 'navigate', screen: targetScreen });
    return succeeded({
      kind: 'setup-action',
      checkId: step.id,
      outcome: step.targetScreen ? 'navigation' : 'fallback',
      targetScreen
    });
  }

  private runAccountInspection() {
    const state = this.dependencies.getState();
    return this.featureInspection('account', {
      mode: state.settings.accountMode,
      availableModes: ['Local'],
      localModeUsable: true,
      googleSyncAvailable: productAvailability.googleSync.available,
      googleSyncReason: productAvailability.googleSync.reason,
      authenticatedAiProviderAvailable: productAvailability.authenticatedAiProvider.available,
      authenticatedAiProviderReason: productAvailability.authenticatedAiProvider.reason,
      authenticatedEmailProviderAvailable: productAvailability.authenticatedEmailProvider.available,
      authenticatedEmailProviderReason: productAvailability.authenticatedEmailProvider.reason,
      localDataRetainedOnDisconnect: true
    });
  }

  private runPrivacyInspection() {
    const state = this.dependencies.getState();
    return this.featureInspection('privacy', {
      permissionDecisions: { ...state.privacy.permissionDecisions },
      permissionRecords: state.privacy.permissionRecords ?? {},
      whatsappHandoffConsent: state.privacy.whatsappHandoffConsent,
      biometricLockEnabled: state.settings.biometricLockEnabled,
      encryptedLocalRepository: true,
      privateMemoriesExcludedFromAi: true,
      unattendedWhatsAppAvailable: false,
      backupPassphraseStored: false
    });
  }

  private runSettingsInspection() {
    const state = this.dependencies.getState();
    const settings = state.settings;
    const senderConfigured = Boolean(state.emailDelivery.senderEmail?.trim());
    return this.featureInspection('settings', {
      locale: settings.locale,
      supportedLocales: ['en-IN', 'hi-IN', 'en-Hinglish'],
      accountMode: settings.accountMode,
      aiEnabled: settings.aiEnabled,
      notificationsEnabled: settings.notificationsEnabled,
      smsEnabled: settings.smsEnabled,
      whatsappHandoffEnabled: settings.whatsappHandoffEnabled,
      emailEnabled: settings.emailEnabled,
      biometricLockEnabled: settings.biometricLockEnabled,
      automationMode: settings.automationMode,
      supportedAutomationModes: ['Always ask', 'Smart approve', 'VIP approve'],
      quietHours: { ...settings.quietHours },
      defaultSendTime: settings.defaultSendTime,
      emailProviderConfiguration: {
        senderConfigured,
        providerDeliveryEnabled: settings.emailEnabled,
        status: state.emailDelivery.status,
        ready: settings.emailEnabled && senderConfigured && state.emailDelivery.status === 'Ready',
        hasError: state.emailDelivery.status === 'Error' || Boolean(state.emailDelivery.lastError)
      },
      blackouts: settings.blackouts.map(blackout => ({
        ...blackout,
        channels: blackout.channels && [...blackout.channels]
      })),
      availability: productAvailability
    });
  }

  private runStyleInspection() {
    const state = this.dependencies.getState();
    const eligibleSentCount = state.messages.filter(
      message => message.status === 'Sent' && message.body.trim().length >= 24
    ).length;
    return this.featureInspection('style', {
      profile: {
        ...state.styleProfile,
        commonGreetings: [...state.styleProfile.commonGreetings]
      },
      eligibleSentCount: Math.min(8, eligibleSentCount),
      canTrainFromSent: eligibleSentCount >= 2,
      futureAiDraftUse: state.styleProfile.enabledForAiDrafts ? 'Enabled' : 'Disabled',
      improvementGuidance:
        state.styleProfile.confidence === 'Not trained' || state.styleProfile.confidence === 'Starting'
          ? 'Add more representative samples to improve Style Coach confidence.'
          : 'Use Improve my style when the current profile no longer represents your writing.',
      rawSamplesRetained: false,
      profileHistoryExposed: false
    });
  }

  private runActivityQuery(command: Extract<HarnessCommand, { type: 'activity.query' }>) {
    const state = this.dependencies.getState();
    const activeContactIds = new Set(state.contacts.filter(contact => !contact.archivedAt).map(contact => contact.id));
    const recoveryState: AppState = {
      ...state,
      contacts: state.contacts.filter(contact => activeContactIds.has(contact.id)),
      messages: state.messages.filter(message => activeContactIds.has(message.contactId))
    };
    const history = buildActivityHistory(state.activity, {
      query: command.query,
      type: command.activityType,
      severity: command.severity,
      status: command.status,
      date: command.date,
      nowIso: this.dependencies.now().toISOString(),
      state: recoveryState
    });
    const items: Record<string, unknown>[] = [];
    for (const row of history.rows) {
      const activity = row.item;
      if (
        !safeOpaqueId(activity.id) ||
        !safeIso(activity.createdAt) ||
        !boundedPrivateString(activity.title, MAX_ACTIVITY_TEXT_LENGTH) ||
        !boundedPrivateString(activity.detail, MAX_ACTIVITY_TEXT_LENGTH, true) ||
        !boundedPrivateString(row.actionLabel, MAX_ACTIVITY_TEXT_LENGTH) ||
        !boundedPrivateString(row.recoveryDetail, MAX_ACTIVITY_TEXT_LENGTH)
      ) {
        return failure('activity-output-invalid', 'Stored activity exceeds the private output bounds.');
      }
      items.push({
        id: activity.id,
        type: activity.type,
        title: activity.title,
        detail: activity.detail,
        severity: activity.severity,
        status: row.status,
        resolvedAt: safeIso(activity.resolvedAt),
        createdAt: activity.createdAt,
        targetScreen: row.targetScreen,
        contactId: safeOpaqueId(row.contactId),
        messageId: safeOpaqueId(row.messageId),
        actionLabel: row.actionLabel,
        isOpenIssue: row.isOpenIssue,
        recoveryState: row.recoveryState,
        recoveryDetail: row.recoveryDetail
      });
    }
    return this.featurePage(
      'activity',
      items,
      command.cursor,
      command.limit,
      item => item.id as string,
      (left, right) => String(right.createdAt).localeCompare(String(left.createdAt)),
      { query: command.query, date: command.date, status: command.status ?? 'All', emptyState: history.emptyState }
    );
  }

  private async runActivityResolve(
    command: Extract<HarnessCommand, { type: 'activity.resolve' }>,
    signal: AbortSignal
  ) {
    const before = this.dependencies.getState();
    const activity = before.activity.find(item => item.id === command.activityId);
    if (!activity) {
      return failure('activity-not-found', 'The selected activity no longer exists.');
    }
    const row = buildActivityHistory([activity], { state: before }).rows[0];
    if (!row || row.status !== 'Open') {
      return failure(
        'activity-not-open',
        row?.status === 'Obsolete'
          ? 'The activity target is obsolete and cannot be marked resolved.'
          : 'Only an open Activity History issue can be marked resolved.'
      );
    }
    assertNotAborted(signal);
    await this.dispatch({ type: 'resolveActivity', activityId: activity.id });
    const after = this.dependencies.getState();
    const resolved = after.activity.find(item => item.id === activity.id);
    if (resolved?.status !== 'Resolved' || !safeIso(resolved.resolvedAt)) {
      return failure(
        'activity-resolution-conflict',
        'The activity changed before resolution could be committed.',
        true
      );
    }
    return this.featureAction('activity', 'resolve', before, after, true);
  }

  private async runActivityOpenAction(
    command: Extract<HarnessCommand, { type: 'activity.open-action' }>,
    signal: AbortSignal
  ) {
    const state = this.dependencies.getState();
    const activity = state.activity.find(item => item.id === command.activityId);
    if (!activity) {
      assertNotAborted(signal);
      await this.dispatch({ type: 'navigate', screen: 'home' });
      return this.boundedPrivateValue({
        kind: 'activity-navigation',
        outcome: 'fallback',
        targetScreen: 'home'
      });
    }
    const activeContactIds = new Set(state.contacts.filter(contact => !contact.archivedAt).map(contact => contact.id));
    const recoveryState: AppState = {
      ...state,
      contacts: state.contacts.filter(contact => activeContactIds.has(contact.id)),
      messages: state.messages.filter(message => activeContactIds.has(message.contactId))
    };
    const row = buildActivityHistory([activity], { state: recoveryState }).rows[0];
    if (!row) return failure('activity-action-unavailable', 'The activity action is no longer available.');
    const leafTargetMissingContext =
      (row.targetScreen === 'wishPreview' && !row.messageId) ||
      (['contactDetail', 'chatHistory', 'manualComposer'].includes(row.targetScreen) && !row.contactId);
    const fallbackScreen =
      activity.type === 'Message'
        ? 'messages'
        : activity.type === 'Event'
          ? 'events'
          : activity.type === 'Contact' || activity.type === 'Gift' || activity.type === 'Memory'
            ? 'contacts'
            : 'more';
    const fallback = row.recoveryState === 'fallback' || leafTargetMissingContext;
    const targetScreen = fallback ? fallbackScreen : row.targetScreen;
    assertNotAborted(signal);
    await this.dispatch({
      type: 'navigate',
      screen: targetScreen,
      contactId: fallback ? undefined : row.contactId,
      messageId: fallback ? undefined : row.messageId
    });
    return this.boundedPrivateValue({
      kind: 'activity-navigation',
      activityId: activity.id,
      outcome: fallback ? 'fallback' : 'target',
      targetScreen,
      contactId: fallback ? undefined : safeOpaqueId(row.contactId),
      messageId: fallback ? undefined : safeOpaqueId(row.messageId)
    });
  }

  private async runReachableFeatureAction(command: HarnessCommand, signal: AbortSignal) {
    const before = this.dependencies.getState();
    assertNotAborted(signal);

    if (
      command.type === 'contacts.preferences.set-tone' ||
      command.type === 'contacts.preferences.set-group' ||
      command.type === 'contacts.preferences.set-channel' ||
      command.type === 'contacts.preferences.set-vip' ||
      command.type === 'contacts.preferences.set-dnd' ||
      command.type === 'contacts.preferences.set-cadence' ||
      command.type === 'contacts.preferences.set-automation' ||
      command.type === 'contacts.preferences.set-send-time' ||
      command.type === 'contacts.preferences.set-quiet-hours' ||
      command.type === 'contacts.preferences.set-skip-auto' ||
      command.type === 'contacts.preferences.use-group-defaults'
    ) {
      const contact = before.contacts.find(item => item.id === command.contactId && !item.archivedAt);
      if (!contact) {
        return this.featureAction('contact-preferences', command.type, before, before, false);
      }
      if (command.type === 'contacts.preferences.set-tone') {
        const alreadyEnabled = contact.tone.includes(command.tone);
        if (!command.enabled && alreadyEnabled && contact.tone.length === 1) {
          return this.featureAction('contact-preferences', 'set-tone', before, before, false);
        }
        if (alreadyEnabled !== command.enabled) {
          await this.dispatch({ type: 'updateContactTone', contactId: contact.id, tone: command.tone });
        }
      } else if (command.type === 'contacts.preferences.set-group') {
        if (contact.group !== command.group) {
          await this.dispatch({ type: 'setContactGroup', contactId: contact.id, group: command.group });
        }
      } else if (command.type === 'contacts.preferences.set-channel') {
        const current = resolveContactPreferencesForContact(before.settings, contact).preferredChannel;
        if (current !== command.channel) {
          await this.dispatch({ type: 'setContactChannel', contactId: contact.id, channel: command.channel });
        }
      } else if (command.type === 'contacts.preferences.set-vip') {
        if (contact.isVip !== command.enabled) await this.dispatch({ type: 'toggleContactVip', contactId: contact.id });
      } else if (command.type === 'contacts.preferences.set-dnd') {
        if (contact.dnd !== command.enabled) await this.dispatch({ type: 'toggleContactDnd', contactId: contact.id });
      } else if (command.type === 'contacts.preferences.set-cadence') {
        const current = resolveContactPreferencesForContact(before.settings, contact).checkInCadenceDays;
        if (current !== command.days) {
          await this.dispatch({ type: 'setCheckInCadence', contactId: contact.id, days: command.days });
        }
      } else if (command.type === 'contacts.preferences.set-automation') {
        const current = resolveContactPreferencesForContact(before.settings, contact).automationMode;
        if (current !== command.mode) {
          await this.dispatch({ type: 'setContactAutomationMode', contactId: contact.id, mode: command.mode });
        }
      } else if (command.type === 'contacts.preferences.set-send-time') {
        if ((contact.customSendTime ?? null) !== command.time) {
          await this.dispatch({
            type: 'setContactCustomSendTime',
            contactId: contact.id,
            time: command.time ?? undefined
          });
        }
      } else if (command.type === 'contacts.preferences.set-quiet-hours') {
        if ((contact.quietHoursBehavior ?? 'Defer') !== command.behavior) {
          await this.dispatch({
            type: 'setContactQuietHoursBehavior',
            contactId: contact.id,
            behavior: command.behavior
          });
        }
      } else if (command.type === 'contacts.preferences.set-skip-auto') {
        if ((contact.skipAuto ?? false) !== command.enabled) {
          await this.dispatch({ type: 'setContactSkipAuto', contactId: contact.id, enabled: command.enabled });
        }
      } else if (command.type === 'contacts.preferences.use-group-defaults') {
        if (contact.preferenceOverrides === undefined || Object.keys(contact.preferenceOverrides).length > 0) {
          await this.dispatch({ type: 'useGroupDefaultsForContact', contactId: contact.id });
        }
      }
      const after = this.dependencies.getState();
      const current = after.contacts.find(item => item.id === contact.id && !item.archivedAt);
      const applied =
        Boolean(current) &&
        (() => {
          if (!current) return false;
          switch (command.type) {
            case 'contacts.preferences.set-tone':
              return current.tone.includes(command.tone) === command.enabled;
            case 'contacts.preferences.set-group':
              return current.group === command.group;
            case 'contacts.preferences.set-channel':
              return resolveContactPreferencesForContact(after.settings, current).preferredChannel === command.channel;
            case 'contacts.preferences.set-vip':
              return current.isVip === command.enabled;
            case 'contacts.preferences.set-dnd':
              return current.dnd === command.enabled;
            case 'contacts.preferences.set-cadence':
              return resolveContactPreferencesForContact(after.settings, current).checkInCadenceDays === command.days;
            case 'contacts.preferences.set-automation':
              return resolveContactPreferencesForContact(after.settings, current).automationMode === command.mode;
            case 'contacts.preferences.set-send-time':
              return (current.customSendTime ?? null) === command.time;
            case 'contacts.preferences.set-quiet-hours':
              return (current.quietHoursBehavior ?? 'Defer') === command.behavior;
            case 'contacts.preferences.set-skip-auto':
              return (current.skipAuto ?? false) === command.enabled;
            case 'contacts.preferences.use-group-defaults':
              return current.preferenceOverrides !== undefined && Object.keys(current.preferenceOverrides).length === 0;
          }
        })();
      return this.featureAction(
        'contact-preferences',
        command.type.split('.').at(-1) ?? command.type,
        before,
        after,
        applied
      );
    }

    if (command.type === 'groups.set-default') {
      await this.dispatch({ type: 'setRelationshipGroupDefault', group: command.group, defaults: command.defaults });
      const after = this.dependencies.getState();
      const current = after.settings.groupDefaults[command.group];
      const applied = Object.entries(command.defaults).every(
        ([key, value]) => JSON.stringify(current[key as keyof typeof current]) === JSON.stringify(value)
      );
      return this.featureAction('group-defaults', 'set-default', before, after, applied);
    }

    if (command.type === 'contacts.enrichment.answer') {
      const contact = before.contacts.find(item => item.id === command.contactId && !item.archivedAt);
      const plan = contact ? buildContactEnrichmentPlan(before, contact.id) : undefined;
      if (!contact || !plan?.prompts.some(prompt => prompt.id === command.promptId)) {
        return this.featureAction('contact-enrichment', 'answer', before, before, false);
      }
      await this.dispatch({
        type: 'answerEnrichmentPrompt',
        contactId: contact.id,
        promptId: command.promptId,
        body: command.body
      });
      const after = this.dependencies.getState();
      const created = after.memories.find(memory => !before.memories.some(previous => previous.id === memory.id));
      return this.featureAction('contact-enrichment', 'answer', before, after, Boolean(created), created?.id);
    }

    if (command.type === 'events.preparation.toggle') {
      const event = before.events.find(item => item.id === command.eventId);
      const plan =
        event && this.activeContact(event.contactId) ? buildEventPreparationPlan(before, event.id) : undefined;
      if (!event || !plan?.ok || !plan.steps.some(step => step.id === command.stepId && step.canToggle)) {
        return this.featureAction('event-preparation', 'toggle', before, before, false);
      }
      await this.dispatch({ type: 'togglePreparationStep', eventId: event.id, stepId: command.stepId });
      const after = this.dependencies.getState();
      const applied =
        JSON.stringify(before.events.find(item => item.id === event.id)) !==
        JSON.stringify(after.events.find(item => item.id === event.id));
      return this.featureAction('event-preparation', 'toggle', before, after, applied);
    }

    if (command.type === 'messages.test-route' || command.type === 'messages.retry') {
      const message = before.messages.find(item => item.id === command.messageId);
      if (!message || !this.activeContact(message.contactId)) {
        return this.featureAction('message', command.type.split('.')[1], before, before, false);
      }
      if (command.type === 'messages.test-route') {
        if (!buildMessageTestPlan(before, message).ok) {
          return this.featureAction('message', 'test-route', before, before, false);
        }
        await this.dispatch({ type: 'testMessageRoute', messageId: message.id });
      } else {
        if (!['Failed', 'Blocked'].includes(message.status) || message.emailDeliveryAttempt?.status === 'Unknown') {
          return this.featureAction('message', 'retry', before, before, false);
        }
        await this.dispatch({ type: 'retryMessage', messageId: message.id });
      }
      const after = this.dependencies.getState();
      const current = after.messages.find(item => item.id === message.id);
      const applied =
        command.type === 'messages.retry'
          ? current?.status === 'Needs review'
          : Boolean(current && current.readiness !== message.readiness);
      return this.featureAction('message', command.type.split('.')[1], before, after, applied);
    }

    if (
      command.type === 'memories.add' ||
      command.type === 'memories.edit' ||
      command.type === 'memories.set-pinned' ||
      command.type === 'memories.delete'
    ) {
      if (command.type === 'memories.add') {
        if (!before.contacts.some(contact => contact.id === command.contactId && !contact.archivedAt)) {
          return this.featureAction('memory', 'add', before, before, false);
        }
        await this.dispatch({
          type: 'addMemory',
          contactId: command.contactId,
          category: command.category,
          body: command.body
        });
      } else {
        const memory = before.memories.find(item => item.id === command.memoryId);
        if (!memory || !before.contacts.some(contact => contact.id === memory.contactId && !contact.archivedAt)) {
          return this.featureAction('memory', command.type.split('.')[1], before, before, false);
        }
        if (command.type === 'memories.edit') {
          await this.dispatch({
            type: 'editMemory',
            memoryId: memory.id,
            category: command.category,
            body: command.body
          });
        } else if (command.type === 'memories.set-pinned') {
          if (memory.pinned !== command.pinned) await this.dispatch({ type: 'toggleMemoryPin', memoryId: memory.id });
        } else {
          await this.dispatch({ type: 'deleteMemory', memoryId: memory.id });
        }
      }
      const after = this.dependencies.getState();
      const created =
        command.type === 'memories.add'
          ? after.memories.find(memory => !before.memories.some(previous => previous.id === memory.id))
          : undefined;
      const applied =
        command.type === 'memories.add'
          ? Boolean(created)
          : command.type === 'memories.delete'
            ? !after.memories.some(memory => memory.id === command.memoryId)
            : command.type === 'memories.set-pinned'
              ? after.memories.find(memory => memory.id === command.memoryId)?.pinned === command.pinned
              : JSON.stringify(before.memories.find(memory => memory.id === command.memoryId)) !==
                JSON.stringify(after.memories.find(memory => memory.id === command.memoryId));
      return this.featureAction('memory', command.type.split('.')[1], before, after, applied, created?.id);
    }

    if (command.type === 'gifts.add' || command.type === 'gifts.delete' || command.type === 'gifts.set-budget') {
      if (command.type === 'gifts.add') {
        if (!before.contacts.some(contact => contact.id === command.contactId && !contact.archivedAt)) {
          return this.featureAction('gift', 'add', before, before, false);
        }
        await this.dispatch({
          type: 'addGift',
          contactId: command.contactId,
          name: command.name,
          category: command.category,
          occasion: command.occasion,
          cost: command.cost,
          feedback: command.feedback,
          notes: command.notes
        });
      } else if (command.type === 'gifts.delete') {
        const gift = before.gifts.find(item => item.id === command.giftId);
        if (!gift || !before.contacts.some(contact => contact.id === gift.contactId && !contact.archivedAt)) {
          return this.featureAction('gift', 'delete', before, before, false);
        }
        await this.dispatch({ type: 'deleteGift', giftId: gift.id });
      } else {
        if (!before.contacts.some(contact => contact.id === command.contactId && !contact.archivedAt)) {
          return this.featureAction('gift', 'set-budget', before, before, false);
        }
        await this.dispatch({
          type: 'updateGiftBudget',
          contactId: command.contactId,
          annualGiftBudget: command.annualGiftBudget
        });
      }
      const after = this.dependencies.getState();
      const created =
        command.type === 'gifts.add'
          ? after.gifts.find(gift => !before.gifts.some(previous => previous.id === gift.id))
          : undefined;
      const applied =
        command.type === 'gifts.add'
          ? Boolean(created)
          : command.type === 'gifts.delete'
            ? !after.gifts.some(gift => gift.id === command.giftId)
            : after.contacts.find(contact => contact.id === command.contactId)?.annualGiftBudget ===
              Math.round(command.annualGiftBudget);
      return this.featureAction('gift', command.type.split('.')[1], before, after, applied, created?.id);
    }

    if (
      command.type === 'onboarding.set-goal' ||
      command.type === 'onboarding.set-step' ||
      command.type === 'onboarding.advance' ||
      command.type === 'onboarding.skip' ||
      command.type === 'onboarding.complete' ||
      command.type === 'onboarding.reopen'
    ) {
      const transitionIssue =
        command.type === 'onboarding.set-step'
          ? onboardingTransitionIssue(before, { type: 'set-step', stepId: command.stepId })
          : command.type === 'onboarding.advance'
            ? onboardingTransitionIssue(before, { type: 'advance' })
            : command.type === 'onboarding.skip'
              ? onboardingTransitionIssue(before, { type: 'skip', stepId: command.stepId })
              : command.type === 'onboarding.complete'
                ? onboardingTransitionIssue(before, { type: 'complete' })
                : undefined;
      if (transitionIssue) {
        return failure('onboarding-transition-blocked', transitionIssue);
      }

      if (
        command.type === 'onboarding.complete' ||
        (command.type === 'onboarding.advance' && before.onboarding.currentStepId === 'finish')
      ) {
        const setupGoal =
          before.onboarding.selectedGoal === 'Reminders first'
            ? 'Reminders only'
            : before.onboarding.selectedGoal === 'AI wishes'
              ? 'AI drafts'
              : before.onboarding.selectedGoal === 'Manual relationship manager'
                ? 'Manual sends'
                : 'Automation';
        const environment = await this.dependencies.setupEnvironment(signal);
        assertNotAborted(signal);
        const setupPlan = buildSetupWizardPlan(before, environment, setupGoal);
        const requiredSetup = setupPlan.steps.filter(step => step.status === 'Needs action');
        if (requiredSetup.length > 0) {
          return failure(
            'onboarding-required-setup',
            `Complete the required ${setupGoal.toLowerCase()} setup before finishing: ${requiredSetup
              .map(step => step.title)
              .join(', ')}.`
          );
        }
      }

      switch (command.type) {
        case 'onboarding.set-goal':
          await this.dispatch({ type: 'setOnboardingGoal', goal: command.goal });
          break;
        case 'onboarding.set-step':
          await this.dispatch({ type: 'setOnboardingStep', stepId: command.stepId });
          break;
        case 'onboarding.advance':
          await this.dispatch({ type: 'advanceOnboarding' });
          break;
        case 'onboarding.skip':
          await this.dispatch({ type: 'skipOnboardingStep', stepId: command.stepId });
          break;
        case 'onboarding.complete':
          await this.dispatch({ type: 'completeOnboarding' });
          break;
        case 'onboarding.reopen':
          await this.dispatch({ type: 'reopenOnboarding' });
          break;
      }
      const after = this.dependencies.getState();
      return this.featureAction(
        'onboarding',
        command.type.split('.')[1],
        before,
        after,
        JSON.stringify(before.onboarding) !== JSON.stringify(after.onboarding) ||
          (command.type === 'onboarding.reopen' && after.activeScreen === 'onboarding')
      );
    }

    if (command.type === 'account.use-local' || command.type === 'account.disconnect') {
      if (command.type === 'account.disconnect' && before.settings.accountMode === 'Local') {
        return this.featureAction('account', 'disconnect', before, before, false);
      }
      if (command.type === 'account.use-local') {
        await this.dispatch({ type: 'setAccountMode', mode: 'Local' });
      } else {
        await this.dispatch({ type: 'disconnectAccount' });
      }
      const after = this.dependencies.getState();
      return this.featureAction(
        'account',
        command.type.split('.')[1],
        before,
        after,
        after.settings.accountMode === 'Local'
      );
    }

    if (command.type === 'privacy.set-whatsapp-consent') {
      if (before.privacy.whatsappHandoffConsent !== command.enabled) {
        await this.dispatch({ type: 'toggleWhatsAppHandoffConsent' });
      }
      const after = this.dependencies.getState();
      return this.featureAction(
        'privacy',
        'set-whatsapp-consent',
        before,
        after,
        after.privacy.whatsappHandoffConsent === command.enabled
      );
    }

    if (
      command.type === 'settings.set-boolean' ||
      command.type === 'settings.set-automation' ||
      command.type === 'settings.set-locale' ||
      command.type === 'settings.set-email-sender' ||
      command.type === 'settings.set-quiet-hours' ||
      command.type === 'settings.set-default-send-time' ||
      command.type === 'settings.add-blackout' ||
      command.type === 'settings.remove-blackout'
    ) {
      if (command.type === 'settings.set-boolean') {
        if (before.settings[command.key] !== command.enabled) {
          await this.dispatch({ type: 'toggleSetting', key: command.key });
        }
      } else if (command.type === 'settings.set-automation') {
        if (before.settings.automationMode !== command.mode) {
          await this.dispatch({ type: 'setAutomationMode', mode: command.mode });
        }
      } else if (command.type === 'settings.set-locale') {
        if (before.settings.locale !== command.locale) {
          await this.dispatch({ type: 'setLocale', locale: command.locale });
        }
      } else if (command.type === 'settings.set-email-sender') {
        if ((before.emailDelivery.senderEmail ?? '') !== command.senderEmail) {
          await this.dispatch({ type: 'setEmailSender', senderEmail: command.senderEmail });
        }
      } else if (command.type === 'settings.set-quiet-hours') {
        await this.dispatch({ type: 'setQuietHours', start: command.start, end: command.end });
      } else if (command.type === 'settings.set-default-send-time') {
        await this.dispatch({ type: 'setDefaultSendTime', time: command.time });
      } else if (command.type === 'settings.add-blackout') {
        await this.dispatch({
          type: 'addBlackout',
          label: command.label,
          startDate: command.startDate,
          endDate: command.endDate,
          behavior: command.behavior,
          channels: command.channels
        });
      } else {
        if (!before.settings.blackouts.some(blackout => blackout.id === command.blackoutId)) {
          return this.featureAction('settings', 'remove-blackout', before, before, false);
        }
        await this.dispatch({ type: 'removeBlackout', blackoutId: command.blackoutId });
      }
      const after = this.dependencies.getState();
      let applied = false;
      switch (command.type) {
        case 'settings.set-boolean':
          applied = after.settings[command.key] === command.enabled;
          break;
        case 'settings.set-automation':
          applied = after.settings.automationMode === command.mode;
          break;
        case 'settings.set-locale':
          applied = after.settings.locale === command.locale;
          break;
        case 'settings.set-email-sender':
          applied = command.senderEmail
            ? after.emailDelivery.senderEmail === command.senderEmail
            : !after.emailDelivery.senderEmail && after.emailDelivery.status === 'Not configured';
          break;
        case 'settings.set-quiet-hours':
          applied = after.settings.quietHours.start === command.start && after.settings.quietHours.end === command.end;
          break;
        case 'settings.set-default-send-time':
          applied = after.settings.defaultSendTime === command.time;
          break;
        case 'settings.add-blackout':
          applied = after.settings.blackouts.length === before.settings.blackouts.length + 1;
          break;
        case 'settings.remove-blackout':
          applied = !after.settings.blackouts.some(blackout => blackout.id === command.blackoutId);
          break;
      }
      return this.featureAction('settings', command.type.split('.')[1], before, after, applied);
    }

    if (command.type === 'style.set-enabled') {
      if (before.styleProfile.enabledForAiDrafts === command.enabled) {
        return this.featureAction('style', 'set-enabled', before, before, false);
      }
      await this.dispatch({ type: 'setStyleEnabled', enabled: command.enabled });
      const after = this.dependencies.getState();
      return this.featureAction(
        'style',
        'set-enabled',
        before,
        after,
        after.styleProfile.enabledForAiDrafts === command.enabled
      );
    }

    if (command.type === 'style.train-samples' || command.type === 'style.train-sent') {
      const preview =
        command.type === 'style.train-samples'
          ? analyzeManualStyleSamples(command.samples)
          : analyzeSentMessageStyle(before);
      if (!preview.ok) return failure('style-analysis-failed', preview.message, true);
      await this.dispatch(
        command.type === 'style.train-samples'
          ? { type: 'trainStyleFromSamples', samples: command.samples }
          : { type: 'trainStyleFromSentMessages' }
      );
      const after = this.dependencies.getState();
      return this.featureAction(
        'style',
        command.type.split('.')[1],
        before,
        after,
        JSON.stringify(before.styleProfile) !== JSON.stringify(after.styleProfile)
      );
    }

    if (command.type === 'home.open-action') {
      const planner = buildHomePlanner(before, this.dependencies.now(), {
        setupNeedsAction: await this.liveSetupNeedsAction(signal)
      });
      const action = planner.actions.find(item => item.id === command.actionId);
      if (
        !action ||
        (action.contactId &&
          !before.contacts.some(contact => contact.id === action.contactId && !contact.archivedAt)) ||
        (action.messageId && !before.messages.some(message => message.id === action.messageId)) ||
        (action.eventId && !before.events.some(event => event.id === action.eventId))
      ) {
        return this.featureAction('home', 'open-action', before, before, false);
      }
      await this.dispatch({
        type: 'navigate',
        screen: action.targetScreen,
        contactId: action.contactId,
        eventId: action.eventId,
        messageId: action.messageId
      });
      const after = this.dependencies.getState();
      return this.featureAction('home', 'open-action', before, after, after.activeScreen === action.targetScreen);
    }

    return failure('feature-command-unhandled', 'The supported feature command could not be executed.');
  }

  private async runMessageRegeneration(
    command: Extract<HarnessCommand, { type: 'messages.regenerate' }>,
    signal: AbortSignal
  ) {
    const state = this.dependencies.getState();
    const message = state.messages.find(item => item.id === command.messageId);
    if (
      !message ||
      !this.activeContact(message.contactId) ||
      !['Needs review', 'Draft', 'Blocked', 'Failed'].includes(message.status)
    ) {
      return failure('message-regeneration-blocked', 'Only an active unsent review message can be regenerated.');
    }
    if (
      message.eventId &&
      !state.events.some(event => event.id === message.eventId && event.contactId === message.contactId)
    ) {
      return failure('message-context-stale', 'The message event context is no longer valid.');
    }
    if (
      command.excludedMemoryIds?.some(
        memoryId => !state.memories.some(memory => memory.id === memoryId && memory.contactId === message.contactId)
      )
    ) {
      return failure('message-context-stale', 'One or more excluded memory choices are no longer valid.');
    }
    return this.runAiDraft(
      {
        type: 'ai.draft',
        contactId: message.contactId,
        eventId: message.eventId,
        reason: message.reason,
        excludedMemoryIds: command.excludedMemoryIds,
        includePriorMessages: command.includePriorMessages
      },
      signal,
      {
        instructions: command.instructions,
        customInstruction: command.customInstruction,
        previousDraftExcerpt: message.body.slice(0, 220)
      },
      {
        messageId: message.id,
        expectedRevision: messageDraftRevision(message)
      }
    );
  }

  private async runContactLifecycle(
    command: Extract<
      HarnessCommand,
      {
        type:
          | 'contacts.add'
          | 'contacts.edit-preview'
          | 'contacts.edit-apply'
          | 'contacts.archive-preview'
          | 'contacts.archive-apply'
          | 'contacts.restore'
          | 'contacts.delete-preview'
          | 'contacts.delete-apply'
          | 'contacts.merge-preview'
          | 'contacts.merge-apply';
      }
    >,
    signal: AbortSignal
  ) {
    const before = this.dependencies.getState();
    if (command.type === 'contacts.edit-preview') {
      const preview = previewContactEdit(before, command.contactId, command.input);
      if (!preview.ok)
        return failure('contact-preview-blocked', 'The contact edit cannot be previewed in its current state.');
      const exactIdentityCandidateIds = preview.exactIdentityCandidateIds
        .map(safeOpaqueId)
        .filter((candidateId): candidateId is string => Boolean(candidateId));
      if (
        exactIdentityCandidateIds.length !== preview.exactIdentityCandidateIds.length ||
        exactIdentityCandidateIds.length > MAX_REVIEW_CANDIDATE_IDS
      ) {
        return failure('contact-preview-blocked', 'Exact contact identity conflicts exceed safe review bounds.');
      }
      return succeeded({
        kind: 'contact-lifecycle-preview',
        action: 'edit',
        confirmationToken: preview.confirmationToken,
        affectedIds: [preview.contactId],
        requiresConfirmation: true,
        changedFields: preview.changedFields,
        exactIdentityCandidateIds,
        impact: preview.impact
      });
    }
    if (command.type === 'contacts.archive-preview' || command.type === 'contacts.delete-preview') {
      const preview =
        command.type === 'contacts.archive-preview'
          ? previewContactArchive(before, command.contactId)
          : previewContactDelete(before, command.contactId);
      if (!preview.ok)
        return failure('contact-preview-blocked', 'The contact removal cannot be previewed in its current state.');
      return succeeded({
        kind: 'contact-lifecycle-preview',
        action: preview.operation,
        confirmationToken: preview.confirmationToken,
        affectedIds: [preview.contactId],
        requiresConfirmation: true,
        impact: preview.impact,
        relationshipHistoryCount: preview.relationshipHistoryCount,
        deletionAllowed: preview.deletionAllowed,
        recommendedAction: preview.recommendedAction
      });
    }
    if (command.type === 'contacts.merge-preview') {
      const preview = previewContactMerge(before, command.survivorContactId, command.mergedContactId);
      if (!preview.ok)
        return failure('contact-preview-blocked', 'The contact merge cannot be previewed in its current state.');
      return succeeded({
        kind: 'contact-lifecycle-preview',
        action: 'merge',
        confirmationToken: preview.confirmationToken,
        affectedIds: [preview.survivorContactId, preview.mergedContactId],
        requiresConfirmation: true,
        impact: preview.impact,
        matchReasons: preview.matchReasons,
        exactIdentityMatch: preview.exactIdentityMatch,
        conflictingFields: preview.conflictingFields
      });
    }

    assertNotAborted(signal);
    if (command.type === 'contacts.add') {
      await this.dispatch({ type: 'addContact', input: command.input });
      const after = this.dependencies.getState();
      const created = after.contacts.find(contact => !before.contacts.some(previous => previous.id === contact.id));
      return succeeded({
        kind: 'contact-action',
        action: 'add',
        createdContactId: safeOpaqueId(created?.id),
        ...localActionResult(before, after, Boolean(created))
      });
    }
    if (command.type === 'contacts.edit-apply') {
      const preview = previewContactEdit(before, command.contactId, command.input);
      if (preview.ok && preview.exactIdentityCandidateIds.length > 0) {
        const exactIdentityCandidateIds = preview.exactIdentityCandidateIds
          .map(safeOpaqueId)
          .filter((candidateId): candidateId is string => Boolean(candidateId));
        if (
          exactIdentityCandidateIds.length !== preview.exactIdentityCandidateIds.length ||
          exactIdentityCandidateIds.length > MAX_REVIEW_CANDIDATE_IDS
        ) {
          return failure(
            'contact-edit-collision-invalid',
            'Exact contact identity conflicts exceed safe review bounds.'
          );
        }
        return succeeded({
          kind: 'contact-action',
          action: 'edit',
          outcome: 'blocked',
          affectedIds: [],
          affectedCount: 0,
          blockedReason: 'exact-identity-collision',
          exactIdentityCandidateIds
        });
      }
      const eligible = preview.ok && preview.confirmationToken === command.confirmationToken;
      await this.dispatch({
        type: 'editContact',
        contactId: command.contactId,
        input: command.input,
        confirmationToken: command.confirmationToken
      });
      const after = this.dependencies.getState();
      const changed =
        JSON.stringify(before.contacts.find(item => item.id === command.contactId)) !==
        JSON.stringify(after.contacts.find(item => item.id === command.contactId));
      return succeeded({
        kind: 'contact-action',
        action: 'edit',
        ...localActionResult(before, after, eligible && changed)
      });
    }
    if (command.type === 'contacts.archive-apply') {
      const preview = previewContactArchive(before, command.contactId);
      const eligible = preview.ok && preview.confirmationToken === command.confirmationToken;
      await this.dispatch({
        type: 'archiveContact',
        contactId: command.contactId,
        confirmationToken: command.confirmationToken
      });
      const after = this.dependencies.getState();
      const archived = Boolean(after.contacts.find(item => item.id === command.contactId)?.archivedAt);
      return succeeded({
        kind: 'contact-action',
        action: 'archive',
        ...localActionResult(before, after, eligible && archived)
      });
    }
    if (command.type === 'contacts.restore') {
      const wasArchived = Boolean(before.contacts.find(item => item.id === command.contactId)?.archivedAt);
      await this.dispatch({ type: 'restoreContact', contactId: command.contactId });
      const after = this.dependencies.getState();
      const restored =
        Boolean(after.contacts.find(item => item.id === command.contactId)) &&
        !after.contacts.find(item => item.id === command.contactId)?.archivedAt;
      return succeeded({
        kind: 'contact-action',
        action: 'restore',
        ...localActionResult(before, after, wasArchived && restored)
      });
    }
    if (command.type === 'contacts.delete-apply') {
      const preview = previewContactDelete(before, command.contactId);
      const eligible = preview.ok && preview.deletionAllowed && preview.confirmationToken === command.confirmationToken;
      await this.dispatch({
        type: 'deleteContact',
        contactId: command.contactId,
        confirmationToken: command.confirmationToken
      });
      const after = this.dependencies.getState();
      const deleted = !after.contacts.some(item => item.id === command.contactId);
      return succeeded({
        kind: 'contact-action',
        action: 'delete',
        ...localActionResult(before, after, eligible && deleted)
      });
    }
    const preview = previewContactMerge(before, command.survivorContactId, command.mergedContactId);
    const eligible = preview.ok && preview.confirmationToken === command.confirmationToken;
    await this.dispatch({
      type: 'mergeContacts',
      survivorContactId: command.survivorContactId,
      mergedContactId: command.mergedContactId,
      confirmationToken: command.confirmationToken
    });
    const after = this.dependencies.getState();
    const merged =
      after.contacts.some(item => item.id === command.survivorContactId) &&
      !after.contacts.some(item => item.id === command.mergedContactId);
    return succeeded({
      kind: 'contact-action',
      action: 'merge',
      ...localActionResult(before, after, eligible && merged)
    });
  }

  private async runEventLifecycle(
    command: Extract<
      HarnessCommand,
      {
        type:
          | 'events.add'
          | 'events.edit-preview'
          | 'events.edit-apply'
          | 'events.delete-preview'
          | 'events.delete-apply'
          | 'events.merge-preview'
          | 'events.merge-apply';
      }
    >,
    signal: AbortSignal
  ) {
    const before = this.dependencies.getState();
    if (command.type === 'events.edit-preview') {
      const preview = previewEventEdit(before, command.eventId, command.input);
      if (!preview.ok)
        return failure('event-preview-blocked', 'The event edit cannot be previewed in its current state.');
      return succeeded({
        kind: 'event-lifecycle-preview',
        action: 'edit',
        confirmationToken: preview.confirmationToken,
        affectedIds: [preview.eventId],
        requiresConfirmation: preview.requiresConfirmation,
        changedFields: preview.changedFields,
        conflictCount: preview.conflicts.length,
        impact: preview.impact
      });
    }
    if (command.type === 'events.delete-preview') {
      const preview = previewEventDelete(before, command.eventId);
      if (!preview.ok)
        return failure('event-preview-blocked', 'The event deletion cannot be previewed in its current state.');
      return succeeded({
        kind: 'event-lifecycle-preview',
        action: 'delete',
        confirmationToken: preview.confirmationToken,
        affectedIds: [preview.eventId],
        requiresConfirmation: true,
        conflictCount: 0,
        impact: preview.impact
      });
    }
    if (command.type === 'events.merge-preview') {
      const preview = previewEventMerge(before, command.survivorEventId, command.mergedEventId);
      if (!preview.ok)
        return failure('event-preview-blocked', 'The event merge cannot be previewed in its current state.');
      return succeeded({
        kind: 'event-merge-preview',
        action: 'merge',
        confirmationToken: preview.confirmationToken,
        affectedIds: [preview.survivorEventId, preview.mergedEventId],
        requiresConfirmation: true,
        matchReasons: preview.matchReasons,
        impact: {
          activeMessageCount: preview.activeMessageCount,
          historyMessageCount: preview.historyMessageCount,
          reminderCount: preview.reminderCount
        }
      });
    }

    assertNotAborted(signal);
    if (command.type === 'events.add') {
      await this.dispatch({
        type: 'addManualEvent',
        contactId: command.contactId,
        newContactName: command.newContactName,
        eventType: command.eventType,
        label: command.label,
        date: command.date,
        confirmConflict: command.confirmConflict
      });
      const after = this.dependencies.getState();
      const createdEvent = after.events.find(event => !before.events.some(previous => previous.id === event.id));
      const createdContact = after.contacts.find(
        contact => !before.contacts.some(previous => previous.id === contact.id)
      );
      return succeeded({
        kind: 'event-action',
        action: 'add',
        createdEventId: safeOpaqueId(createdEvent?.id),
        createdContactId: safeOpaqueId(createdContact?.id),
        ...localActionResult(before, after, Boolean(createdEvent))
      });
    }
    if (command.type === 'events.edit-apply') {
      const preview = previewEventEdit(before, command.eventId, command.input);
      const eligible = preview.ok && preview.confirmationToken === command.confirmationToken;
      await this.dispatch({
        type: 'editEvent',
        eventId: command.eventId,
        input: command.input,
        confirmationToken: command.confirmationToken
      });
      const after = this.dependencies.getState();
      const changed =
        JSON.stringify(before.events.find(item => item.id === command.eventId)) !==
        JSON.stringify(after.events.find(item => item.id === command.eventId));
      return succeeded({
        kind: 'event-action',
        action: 'edit',
        ...localActionResult(before, after, eligible && changed)
      });
    }
    if (command.type === 'events.delete-apply') {
      const preview = previewEventDelete(before, command.eventId);
      const eligible = preview.ok && preview.confirmationToken === command.confirmationToken;
      await this.dispatch({
        type: 'deleteEvent',
        eventId: command.eventId,
        confirmationToken: command.confirmationToken
      });
      const after = this.dependencies.getState();
      return succeeded({
        kind: 'event-action',
        action: 'delete',
        ...localActionResult(before, after, eligible && !after.events.some(item => item.id === command.eventId))
      });
    }
    const preview = previewEventMerge(before, command.survivorEventId, command.mergedEventId);
    const eligible = preview.ok && preview.confirmationToken === command.confirmationToken;
    await this.dispatch({
      type: 'mergeEvents',
      survivorEventId: command.survivorEventId,
      mergedEventId: command.mergedEventId,
      confirmationToken: command.confirmationToken
    });
    const after = this.dependencies.getState();
    const merged =
      after.events.some(item => item.id === command.survivorEventId) &&
      !after.events.some(item => item.id === command.mergedEventId);
    return succeeded({
      kind: 'event-action',
      action: 'merge',
      ...localActionResult(before, after, eligible && merged)
    });
  }

  private safeImportReviewItems(
    reviewItems: ContactImportReviewItem[],
    records: ImportedContactRecord[],
    existing?: Map<string, string>
  ) {
    const recordIndex = new Map(records.map((record, index) => [record.sourceId, index]));
    const state = this.dependencies.getState();
    const stateEvents = new Map(state.events.map(event => [event.id, event]));
    const safe = reviewItems.map(item => {
      const index = recordIndex.get(item.sourceId);
      const record = index === undefined ? undefined : records[index];
      const reviewItemId =
        existing?.get(item.sourceId) ??
        (index === undefined ? undefined : `review-${index}-${fnvFingerprint(item.sourceId)}`);
      const candidateName = record?.name.trim().replace(/\s+/g, ' ') ?? '';
      const candidateRoutes = record ? safeContactRoutes(importedContactRoutes(record)) : undefined;
      const candidateBirthday = record?.birthday;
      const importedBirthday = safeIso(item.importedBirthday);
      const candidateContactIds = item.candidateContactIds
        .map(safeOpaqueId)
        .filter((candidate): candidate is string => Boolean(candidate));
      const conflictingEventIds = (item.conflictingEventIds ?? [])
        .map(safeOpaqueId)
        .filter((eventId): eventId is string => Boolean(eventId));
      const existingBirthdays = new Map((item.existingBirthdays ?? []).map(event => [event.eventId, event.date]));
      const conflictingEvents = conflictingEventIds.map(eventId => {
        const event = stateEvents.get(eventId);
        const date = existingBirthdays.get(eventId) ?? event?.date;
        if (!event || !date || !safeIso(date) || !boundedPrivateString(event.label, MAX_EVENT_LABEL_LENGTH)) {
          return undefined;
        }
        return { eventId, label: event.label, date, eventType: event.type };
      });
      const validationErrors =
        item.reason === 'invalid-birthday'
          ? ['The imported birthday is invalid. Skip it or keep an existing contact birthday.']
          : item.reason === 'missing-name'
            ? ['This routable contact has no recoverable name. Add it manually with contacts.add, then skip this item.']
            : [];
      if (
        !reviewItemId ||
        !safeOpaqueId(reviewItemId) ||
        (item.reason !== 'missing-name' && !candidateName) ||
        !boundedPrivateString(candidateName, MAX_CONTACT_NAME_LENGTH, item.reason === 'missing-name') ||
        !candidateRoutes ||
        (candidateBirthday !== undefined && !boundedPrivateString(candidateBirthday, 80, true)) ||
        (item.importedBirthday !== undefined && !importedBirthday) ||
        candidateContactIds.length !== item.candidateContactIds.length ||
        candidateContactIds.length > MAX_REVIEW_CANDIDATE_IDS ||
        conflictingEventIds.length !== (item.conflictingEventIds ?? []).length ||
        conflictingEventIds.length > MAX_REVIEW_CANDIDATE_IDS ||
        conflictingEvents.some(event => !event) ||
        validationErrors.some(error => !boundedPrivateString(error, MAX_VALIDATION_ERROR_LENGTH))
      ) {
        return undefined;
      }
      return {
        reviewItemId,
        sourceId: item.sourceId,
        candidateContactIds,
        candidateName,
        candidateRoutes,
        candidateBirthday,
        importedBirthday,
        conflictingEventIds,
        conflictingEvents: conflictingEvents as NonNullable<(typeof conflictingEvents)[number]>[],
        validationErrors,
        reason: item.reason,
        resolutionIssue: item.resolutionIssue
      };
    });
    return safe.some(item => !item) ? undefined : (safe as NonNullable<(typeof safe)[number]>[]);
  }

  private safeCalendarReviewItems(
    batch: StagedEventImportBatch,
    domainReviewItems: CalendarImportReviewItem[],
    contacts: AppState['contacts'],
    events: AppState['events']
  ) {
    const domainBySource = new Map(domainReviewItems.map(item => [item.sourceId, item]));
    const contactsById = new Map(contacts.map(contact => [contact.id, contact]));
    const eventsById = new Map(events.map(event => [event.id, event]));
    const safe = batch.items.map(item => {
      const conflict = domainBySource.get(item.candidate.sourceId);
      const candidateContactIds = (conflict?.candidateContactIds ?? [])
        .map(safeOpaqueId)
        .filter((contactId): contactId is string => Boolean(contactId));
      const conflictingEventIds = (conflict?.conflictingEventIds ?? [])
        .map(safeOpaqueId)
        .filter((eventId): eventId is string => Boolean(eventId));
      const candidateContacts = candidateContactIds.map(contactId => {
        const contact = contactsById.get(contactId);
        const routes = contact ? safeContactRoutes(allContactRoutes(contact)) : undefined;
        if (!contact || !routes || !boundedPrivateString(contact.name, MAX_CONTACT_NAME_LENGTH)) return undefined;
        return { contactId, name: contact.name, routes };
      });
      const conflictingEvents = conflictingEventIds.map(eventId => {
        const event = eventsById.get(eventId);
        if (!event || !safeIso(event.date) || !boundedPrivateString(event.label, MAX_EVENT_LABEL_LENGTH))
          return undefined;
        return { eventId, label: event.label, date: event.date, eventType: event.type };
      });
      const allowedConflictActions = !conflict
        ? []
        : conflict.reason === 'same-name'
          ? (['skip', 'create-separate', 'merge-contact'] as const)
          : conflict.reason === 'conflicting-date'
            ? (['skip', 'merge-event'] as const)
            : (['skip'] as const);
      if (
        !safeOpaqueId(item.reviewId) ||
        !boundedPrivateString(item.candidate.title, MAX_TEMPLATE_TITLE_LENGTH, true) ||
        !boundedPrivateString(item.candidate.startDate, 128, true) ||
        item.validationErrors.length > MAX_VALIDATION_ERRORS ||
        item.validationErrors.some(error => !boundedPrivateString(error, MAX_VALIDATION_ERROR_LENGTH)) ||
        candidateContactIds.length !== (conflict?.candidateContactIds.length ?? 0) ||
        conflictingEventIds.length !== (conflict?.conflictingEventIds.length ?? 0) ||
        candidateContactIds.length > MAX_REVIEW_CANDIDATE_IDS ||
        conflictingEventIds.length > MAX_REVIEW_CANDIDATE_IDS ||
        candidateContacts.some(contact => !contact) ||
        conflictingEvents.some(event => !event)
      )
        return undefined;
      return {
        reviewId: item.reviewId,
        title: item.candidate.title,
        date: item.candidate.startDate,
        valid: item.valid,
        validationErrorCount: item.validationErrors.length,
        validationErrors: [...item.validationErrors],
        conflictReason: conflict?.reason,
        allowedConflictActions: [...allowedConflictActions],
        candidateContacts: candidateContacts as NonNullable<(typeof candidateContacts)[number]>[],
        conflictingEvents: conflictingEvents as NonNullable<(typeof conflictingEvents)[number]>[]
      };
    });
    return safe.some(item => !item) ? undefined : (safe as NonNullable<(typeof safe)[number]>[]);
  }

  private async runContactImportPreview(signal: AbortSignal) {
    const state = this.dependencies.getState();
    const previousAuthorization =
      state.privacy.permissionRecords?.Contacts?.systemAuthorization ??
      (state.privacy.permissionDecisions.Contacts === 'Granted' ? 'granted' : 'undetermined');
    let preflight: Awaited<ReturnType<HarnessCommandRuntime['preflightPromptingOperation']>>;
    try {
      preflight = await this.preflightPromptingOperation(state, 'Contacts', signal);
    } catch {
      await this.refreshPermissionsAfterPrompt();
      return this.permissionRecoveryFailure('Contacts', 'import', previousAuthorization);
    }
    if (!preflight.allowed) {
      return this.permissionRecoveryFailure(
        'Contacts',
        'import',
        previousAuthorization,
        preflight.check.authorization === 'denied' || preflight.check.authorization === 'restricted'
      );
    }
    let rawRecords: ImportedContactRecord[];
    try {
      rawRecords = await this.dependencies.importContacts(signal);
    } catch (error) {
      await this.refreshPermissionsAfterPrompt();
      if (signal.aborted) throw error;
      return this.permissionRecoveryFailure(
        'Contacts',
        'import',
        previousAuthorization,
        error instanceof Error && /Contacts permission was not granted/i.test(error.message)
      );
    }
    await this.refreshPermissionsAfterPrompt();
    assertNotAborted(signal);
    const refreshedAuthorization =
      this.dependencies.getState().privacy.permissionRecords?.Contacts?.systemAuthorization ?? 'unavailable';
    if (refreshedAuthorization !== 'granted' && refreshedAuthorization !== 'limited') {
      return this.permissionRecoveryFailure('Contacts', 'import', previousAuthorization);
    }
    const records = validateImportedContacts(rawRecords);
    if (!records) return failure('contacts-invalid-result', 'Contact import returned an unsupported bounded result.');
    const preview = previewContactImport(this.dependencies.getState(), records);
    const sessionToken = this.createTransientToken();
    const reviewItems = this.safeImportReviewItems(preview.reviewItems, records);
    if (!sessionToken || !reviewItems) {
      return failure(
        'contact-import-session-unavailable',
        'A safe bounded contact review session could not be created.'
      );
    }
    const expiresAt = this.dependencies.now().getTime() + CONTACT_IMPORT_SESSION_WINDOW_MS;
    this.pendingContactImport = { sessionToken, records, expiresAt, reviewItems, resolutions: {} };
    return succeeded({
      kind: 'contact-import-preview',
      sessionToken,
      expiresAt: new Date(expiresAt).toISOString(),
      received: records.length,
      added: preview.added,
      updated: preview.updated,
      skipped: preview.skipped,
      unresolved: preview.unresolved,
      reviewItems: reviewItems.map(
        ({ sourceId: _sourceId, conflictingEventIds: _conflictingEventIds, ...item }) => item
      )
    });
  }

  private async runContactImportApply(
    command: Extract<HarnessCommand, { type: 'contacts.import-apply' }>,
    signal: AbortSignal
  ) {
    const pending = this.pendingContactImport;
    if (
      !pending ||
      pending.sessionToken !== command.sessionToken ||
      this.dependencies.now().getTime() > pending.expiresAt
    ) {
      this.pendingContactImport = undefined;
      return failure(
        'contact-import-preview-required',
        'Create a fresh contact import preview before applying review decisions.'
      );
    }
    const byReviewId = new Map(pending.reviewItems.map(item => [item.reviewItemId, item]));
    const resolutions: Record<string, ContactImportResolution> = { ...pending.resolutions };
    for (const decision of command.decisions) {
      const item = byReviewId.get(decision.reviewItemId);
      if (!item)
        return failure(
          'contact-import-decision-invalid',
          'A contact import decision does not match the active preview.'
        );
      if (decision.action === 'skip') {
        resolutions[item.sourceId] = { action: 'skip' };
        continue;
      }
      if (item.reason === 'multiple-route-matches') {
        return failure(
          'contact-import-decision-invalid',
          'Only skip is allowed for a multiple-identity contact conflict.'
        );
      }
      if (item.reason === 'missing-name') {
        return failure(
          'contact-import-name-required',
          'This routable contact needs a name. Add it with contacts.add, then skip this import review item.'
        );
      }
      if (item.reason === 'same-name') {
        if (decision.action === 'keep-separate') {
          resolutions[item.sourceId] = { action: 'keep-separate' };
          continue;
        }
        if (
          decision.action === 'merge' &&
          decision.candidateContactId &&
          item.candidateContactIds.includes(decision.candidateContactId)
        ) {
          resolutions[item.sourceId] = { action: 'merge', candidateContactId: decision.candidateContactId };
          continue;
        }
        return failure(
          'contact-import-decision-invalid',
          'The same-name contact decision is not allowed by the active preview.'
        );
      }
      if (
        decision.candidateContactId !== undefined &&
        !item.candidateContactIds.includes(decision.candidateContactId)
      ) {
        return failure(
          'contact-import-decision-invalid',
          'The selected contact no longer matches the active birthday review.'
        );
      }
      if (item.reason === 'invalid-birthday') {
        if (decision.action === 'keep-existing' && item.candidateContactIds.length > 0) {
          resolutions[item.sourceId] = {
            action: 'keep-existing',
            candidateContactId: decision.candidateContactId
          };
          continue;
        }
        return failure(
          'contact-import-decision-invalid',
          'An invalid imported birthday can only be skipped or keep an existing contact birthday.'
        );
      }
      if (decision.action === 'keep-existing' || decision.action === 'import-as-separate') {
        resolutions[item.sourceId] = {
          action: decision.action,
          candidateContactId: decision.candidateContactId
        };
        continue;
      }
      if (
        decision.action === 'replace' &&
        decision.conflictingEventId &&
        item.conflictingEventIds.includes(decision.conflictingEventId)
      ) {
        resolutions[item.sourceId] = {
          action: 'replace',
          conflictingEventId: decision.conflictingEventId,
          candidateContactId: decision.candidateContactId
        };
        continue;
      }
      return failure(
        'contact-import-decision-invalid',
        'The birthday conflict decision is not allowed by the active preview.'
      );
    }
    const before = this.dependencies.getState();
    const preview = previewContactImport(before, pending.records, undefined, resolutions as ContactImportResolutions);
    const existingIds = new Map(pending.reviewItems.map(item => [item.sourceId, item.reviewItemId]));
    const reviewItems = this.safeImportReviewItems(preview.reviewItems, pending.records, existingIds);
    if (!reviewItems)
      return failure('contact-import-result-invalid', 'The contact review result exceeded safe output bounds.');
    assertNotAborted(signal);
    await this.dispatch({ type: 'importContacts', records: pending.records, resolutions });
    this.pendingContactImport =
      preview.unresolved > 0
        ? {
            ...pending,
            reviewItems,
            resolutions,
            expiresAt: pending.expiresAt
          }
        : undefined;
    return succeeded({
      kind: 'contact-import-apply',
      received: pending.records.length,
      added: preview.added,
      updated: preview.updated,
      skipped: preview.skipped,
      unresolved: preview.unresolved,
      reviewItems: reviewItems.map(
        ({ sourceId: _sourceId, conflictingEventIds: _conflictingEventIds, ...item }) => item
      ),
      sessionToken: preview.unresolved > 0 ? pending.sessionToken : undefined,
      expiresAt: preview.unresolved > 0 ? new Date(pending.expiresAt).toISOString() : undefined
    });
  }

  private createCalendarImportReviewSession(
    candidates: CalendarImportCandidate[],
    received: number,
    signal: AbortSignal,
    parseErrorCount = 0,
    additionalRejected = 0
  ) {
    assertNotAborted(signal);
    const batch = stageEventImportCandidates(candidates);
    if (new Set(batch.items.map(item => item.candidate.sourceId)).size !== batch.items.length) {
      return failure('calendar-review-invalid', 'Event import returned duplicate source identities.');
    }
    const validCandidates = batch.items.filter(item => item.valid).map(item => item.candidate);
    const domainPreview = calendarCandidatesToEvents(this.dependencies.getState(), validCandidates);
    const reviewItems = this.safeCalendarReviewItems(
      batch,
      domainPreview.reviewItems,
      domainPreview.contacts,
      domainPreview.events
    );
    if (!reviewItems) {
      return failure('calendar-review-invalid', 'Event import review data exceeded safe output bounds.');
    }
    const sessionToken = this.createTransientToken();
    if (!sessionToken) {
      return failure('calendar-review-unavailable', 'A safe temporary event review session could not be created.');
    }
    const expiresAt = this.dependencies.now().getTime() + CONTACT_IMPORT_SESSION_WINDOW_MS;
    this.pendingCalendarImport = {
      sessionToken,
      batch,
      received,
      expiresAt,
      decisions: {},
      resolutions: {}
    };
    return succeeded({
      kind: 'calendar-import-preview',
      sessionToken,
      expiresAt: new Date(expiresAt).toISOString(),
      received,
      staged: batch.items.length,
      rejected: batch.rejected.length + additionalRejected,
      overflow: batch.overflowCount,
      invalid: batch.items.filter(item => !item.valid).length,
      parseErrorCount,
      conflictCount: domainPreview.unresolved,
      reviewItems
    });
  }

  private async runCalendarImportPreview(signal: AbortSignal) {
    const state = this.dependencies.getState();
    const previousAuthorization =
      state.privacy.permissionRecords?.Calendar?.systemAuthorization ??
      (state.privacy.permissionDecisions.Calendar === 'Granted' ? 'granted' : 'undetermined');
    let preflight: Awaited<ReturnType<HarnessCommandRuntime['preflightPromptingOperation']>>;
    try {
      preflight = await this.preflightPromptingOperation(state, 'Calendar', signal);
    } catch {
      await this.refreshPermissionsAfterPrompt();
      return this.permissionRecoveryFailure('Calendar', 'import', previousAuthorization);
    }
    if (!preflight.allowed) {
      return this.permissionRecoveryFailure(
        'Calendar',
        'import',
        previousAuthorization,
        preflight.check.authorization === 'denied' || preflight.check.authorization === 'restricted'
      );
    }
    let rawCandidates: CalendarImportCandidate[];
    try {
      rawCandidates = await this.dependencies.importCalendar(signal);
    } catch (error) {
      await this.refreshPermissionsAfterPrompt();
      if (signal.aborted) throw error;
      return this.permissionRecoveryFailure(
        'Calendar',
        'import',
        previousAuthorization,
        error instanceof Error && /Calendar permission was not granted/i.test(error.message)
      );
    }
    await this.refreshPermissionsAfterPrompt();
    assertNotAborted(signal);
    const refreshedAuthorization =
      this.dependencies.getState().privacy.permissionRecords?.Calendar?.systemAuthorization ?? 'unavailable';
    if (refreshedAuthorization !== 'granted' && refreshedAuthorization !== 'limited') {
      return this.permissionRecoveryFailure('Calendar', 'import', previousAuthorization);
    }
    const candidates = validateCalendarCandidates(rawCandidates);
    if (!candidates)
      return failure('calendar-invalid-result', 'Calendar import returned an unsupported bounded result.');
    const result = this.createCalendarImportReviewSession(candidates, candidates.length, signal);
    if (result.status !== 'succeeded') return result;
    return result;
  }

  private async runCalendarImportApply(
    command: Extract<HarnessCommand, { type: 'calendar.import-apply' }>,
    signal: AbortSignal
  ) {
    const pending = this.pendingCalendarImport;
    if (
      !pending ||
      pending.sessionToken !== command.sessionToken ||
      this.dependencies.now().getTime() > pending.expiresAt
    ) {
      this.pendingCalendarImport = undefined;
      return failure(
        'calendar-import-preview-required',
        'Create a fresh calendar import preview before applying decisions.'
      );
    }
    const before = this.dependencies.getState();
    const itemByReviewId = new Map(pending.batch.items.map(item => [item.reviewId, item]));
    const currentDomainPreview = calendarCandidatesToEvents(
      before,
      pending.batch.items.filter(item => item.valid).map(item => item.candidate),
      undefined,
      pending.resolutions
    );
    const conflictBySourceId = new Map(currentDomainPreview.reviewItems.map(item => [item.sourceId, item]));
    const decisions: Record<string, NonNullable<EventImportReviewDecisions[string]>> = { ...pending.decisions };
    const domainResolutions: Record<string, CalendarImportResolution> = { ...pending.resolutions };
    for (const decision of command.decisions) {
      const item = itemByReviewId.get(decision.reviewId);
      if (!item) {
        return failure(
          'calendar-import-decision-invalid',
          'A calendar import decision does not match the active preview.'
        );
      }
      if (decision.action === 'apply' || decision.action === 'skip') {
        decisions[decision.reviewId] = { action: decision.action };
        if (decision.action === 'skip') domainResolutions[item.candidate.sourceId] = { action: 'skip' };
        continue;
      }
      if (decision.action === 'edit') {
        decisions[decision.reviewId] = {
          action: 'edit',
          title: decision.title,
          date: decision.date,
          notes: decision.notes
        };
        continue;
      }
      if (!item.valid) {
        return failure(
          'calendar-import-decision-invalid',
          'Fix or skip invalid event data before choosing a conflict resolution.'
        );
      }
      const conflict = conflictBySourceId.get(item.candidate.sourceId);
      if (!conflict) {
        return failure(
          'calendar-import-decision-invalid',
          'The calendar event no longer has the conflict referenced by this decision.'
        );
      }
      if (decision.action === 'create-separate' && conflict.reason === 'same-name') {
        decisions[decision.reviewId] = { action: 'apply' };
        domainResolutions[item.candidate.sourceId] = { action: 'create-separate' };
        continue;
      }
      if (
        decision.action === 'merge-contact' &&
        conflict.reason === 'same-name' &&
        conflict.candidateContactIds.includes(decision.candidateContactId)
      ) {
        decisions[decision.reviewId] = { action: 'apply' };
        domainResolutions[item.candidate.sourceId] = {
          action: 'merge-contact',
          candidateContactId: decision.candidateContactId
        };
        continue;
      }
      if (
        decision.action === 'merge-event' &&
        conflict.reason === 'conflicting-date' &&
        conflict.conflictingEventIds.includes(decision.candidateEventId)
      ) {
        decisions[decision.reviewId] = { action: 'apply' };
        domainResolutions[item.candidate.sourceId] = {
          action: 'merge-event',
          candidateEventId: decision.candidateEventId
        };
        continue;
      }
      return failure(
        'calendar-import-decision-invalid',
        'The selected calendar conflict resolution is not allowed by the active preview.'
      );
    }
    const resolution = resolveEventImportReview(pending.batch, decisions);
    if (resolution.unknownDecisionReviewIds.length > 0) {
      return failure(
        'calendar-import-decision-invalid',
        'A calendar import decision does not match the active preview.'
      );
    }
    const candidates = validateCalendarCandidates(resolution.candidatesToApply);
    if (!candidates)
      return failure('calendar-import-result-invalid', 'Reviewed calendar candidates exceeded safe bounds.');
    const preview = calendarCandidatesToEvents(before, candidates, undefined, domainResolutions);
    const issueByReviewId = new Map(resolution.issues.map(issue => [issue.reviewId, issue.errors]));
    const reviewedBatch: StagedEventImportBatch = {
      ...pending.batch,
      items: pending.batch.items.map(item => {
        const decision = decisions[item.reviewId];
        const validationErrors = issueByReviewId.get(item.reviewId) ?? item.validationErrors;
        return decision?.action === 'edit'
          ? {
              ...item,
              candidate: {
                ...item.candidate,
                title: decision.title,
                startDate: decision.date,
                notes: decision.notes
              },
              valid: validationErrors.length === 0,
              validationErrors
            }
          : { ...item, valid: validationErrors.length === 0, validationErrors };
      })
    };
    const allReviewItems = this.safeCalendarReviewItems(
      reviewedBatch,
      preview.reviewItems,
      preview.contacts,
      preview.events
    );
    if (!allReviewItems) {
      return failure('calendar-import-result-invalid', 'Calendar conflict review data exceeded safe bounds.');
    }
    const domainReviewIds = new Set(
      preview.reviewItems
        .map(item => pending.batch.items.find(batchItem => batchItem.candidate.sourceId === item.sourceId)?.reviewId)
        .filter((reviewId): reviewId is string => Boolean(reviewId))
    );
    const unresolvedReviewIds = [
      ...new Set([
        ...resolution.unresolvedReviewIds,
        ...resolution.issues.map(issue => issue.reviewId),
        ...domainReviewIds
      ])
    ];
    const unresolvedSet = new Set(unresolvedReviewIds);
    const reviewItems = allReviewItems.filter(item => unresolvedSet.has(item.reviewId));
    assertNotAborted(signal);
    if (candidates.length > 0) {
      await this.dispatch({ type: 'calendarImported', candidates, resolutions: domainResolutions });
    }
    this.pendingCalendarImport =
      unresolvedReviewIds.length > 0 ? { ...pending, decisions, resolutions: domainResolutions } : undefined;
    return succeeded({
      kind: 'calendar-import-apply',
      requested: command.decisions.length,
      applied: Math.max(0, candidates.length - preview.unresolved),
      skipped: resolution.skippedReviewIds.length + preview.skipped,
      unresolved: unresolvedReviewIds.length,
      issueCount: resolution.issues.length + preview.unresolved,
      unresolvedReviewIds: unresolvedReviewIds.slice(0, 5_000),
      addedContacts: preview.addedContacts,
      addedEvents: preview.addedEvents,
      duplicateSkipped: preview.skipped,
      sessionToken: unresolvedReviewIds.length > 0 ? pending.sessionToken : undefined,
      expiresAt: unresolvedReviewIds.length > 0 ? new Date(pending.expiresAt).toISOString() : undefined,
      reviewItems
    });
  }

  private async runCalendarExport(command: Extract<HarnessCommand, { type: 'calendar.export' }>, signal: AbortSignal) {
    const state = this.dependencies.getState();
    const selection = resolveCalendarExportSelection(state, command.eventIds, this.dependencies.now());
    if (!selection.ok) {
      return failure(
        'calendar-export-selection-invalid',
        'Every selected event must still exist and belong to an active contact. Refresh Events and select at least one event.'
      );
    }
    const previousAuthorization =
      state.privacy.permissionRecords?.Calendar?.systemAuthorization ??
      (state.privacy.permissionDecisions.Calendar === 'Granted' ? 'granted' : 'undetermined');
    let preflight: Awaited<ReturnType<HarnessCommandRuntime['preflightPromptingOperation']>>;
    try {
      preflight = await this.preflightPromptingOperation(state, 'Calendar', signal);
    } catch {
      await this.refreshPermissionsAfterPrompt();
      return this.permissionRecoveryFailure('Calendar', 'export', previousAuthorization);
    }
    if (!preflight.allowed) {
      return this.permissionRecoveryFailure(
        'Calendar',
        'export',
        previousAuthorization,
        preflight.check.authorization === 'denied' || preflight.check.authorization === 'restricted'
      );
    }
    const desiredCount = selection.entries.length;
    const request =
      selection.mode === 'selected'
        ? { mode: 'selected' as const, eventIds: selection.entries.map(entry => entry.eventId) }
        : { mode: 'full' as const };
    let reconciled: number;
    try {
      reconciled = await this.dependencies.exportCalendar(state, request, new AbortController().signal);
    } catch (error) {
      await this.refreshPermissionsAfterPrompt();
      return this.permissionRecoveryFailure(
        'Calendar',
        'export',
        previousAuthorization,
        error instanceof Error && /Calendar permission was not granted/i.test(error.message)
      );
    }
    await this.refreshPermissionsAfterPrompt();
    const refreshedAuthorization =
      this.dependencies.getState().privacy.permissionRecords?.Calendar?.systemAuthorization ?? 'unavailable';
    if (refreshedAuthorization !== 'granted' && refreshedAuthorization !== 'limited') {
      return this.permissionRecoveryFailure('Calendar', 'export', previousAuthorization);
    }
    if (!Number.isSafeInteger(reconciled) || reconciled < 0 || reconciled > desiredCount) {
      return failure('calendar-invalid-result', 'Calendar export returned an unsupported reconciliation count.');
    }
    await this.dispatch({ type: 'calendarExported', count: reconciled });
    return succeeded({
      kind: 'calendar-export',
      mode: selection.mode,
      selectedCount: selection.selectedCount,
      eligibleCount: selection.eligibleCount,
      reconciled
    });
  }

  private async runReminderReconciliation(
    command: Extract<HarnessCommand, { type: 'reminders.reconcile' }>,
    _signal: AbortSignal
  ) {
    const result = await this.dependencies.reconcileReminders(
      this.dependencies.getState(),
      command.reason,
      new AbortController().signal
    );
    await this.persistPermissionRecords(result.records);
    await this.dispatch({ type: 'reminderPlansReconciled', plans: result.plannedReminders });
    if (result.status === 'reconciliation-failed') {
      return failure(
        'reminder-reconciliation-failed',
        'Owned reminder notifications could not be reconciled. The in-app plan remains available for retry.',
        true
      );
    }
    const native = result.nativeResult;
    return succeeded({
      kind: 'reminder-reconciliation',
      status: result.status,
      planned: result.plannedReminders.length,
      desiredNative: result.desiredNativeReminders.length,
      scheduled: native?.scheduled ?? 0,
      cancelled: native?.cancelled ?? 0,
      unchanged: native?.unchanged ?? 0,
      skipped: native?.skipped ?? 0
    });
  }

  private runComposerInspection(command: Extract<HarnessCommand, { type: 'composer.inspect' }>) {
    const selectedContact = this.dependencies.getState().contacts.find(contact => contact.id === command.contactId);
    if (selectedContact?.archivedAt) {
      return failure(
        'composer-contact-archived',
        'Restore the archived contact before inspecting or creating a draft.'
      );
    }
    const composer = buildManualComposerState(
      this.dependencies.getState(),
      command.contactId,
      command.reason,
      command.draftBody,
      command.templateId
    );
    const templateIds = composer.templates
      .map(template => safeOpaqueId(template.id))
      .filter((templateId): templateId is string => Boolean(templateId))
      .slice(0, 25);
    const templates = composer.templates.map(template => ({ id: template.id, title: template.title }));
    if (
      templateIds.length !== composer.templates.length ||
      !safeOpaqueId(command.contactId) ||
      templates.some(template => !boundedPrivateString(template.title, MAX_TEMPLATE_TITLE_LENGTH)) ||
      !boundedPrivateString(composer.renderedTemplateBody, MAX_REVIEW_MESSAGE_LENGTH, true) ||
      !boundedPrivateString(composer.context.detail, MAX_REVIEW_ERROR_LENGTH) ||
      !boundedPrivateString(composer.templateAction.label, MAX_TEMPLATE_TITLE_LENGTH) ||
      !boundedPrivateString(composer.templateAction.detail, MAX_REVIEW_ERROR_LENGTH) ||
      !boundedPrivateString(composer.aiAction.label, MAX_TEMPLATE_TITLE_LENGTH) ||
      !boundedPrivateString(composer.aiAction.detail, MAX_REVIEW_ERROR_LENGTH) ||
      (!composer.ok && !boundedPrivateString(composer.error, MAX_REVIEW_ERROR_LENGTH))
    ) {
      return failure('composer-result-invalid', 'Composer metadata exceeded safe output bounds.');
    }
    return succeeded({
      kind: 'composer-inspection',
      outcome: composer.ok ? 'ready' : 'blocked',
      contactId: command.contactId,
      reason: command.reason,
      templateIds,
      templates,
      selectedTemplateId: composer.ok ? composer.selectedTemplateId : undefined,
      selectedTemplateTitle: composer.ok ? composer.selectedTemplate?.title : undefined,
      renderedTemplateBody: composer.renderedTemplateBody,
      error: composer.ok ? undefined : composer.error,
      characterCount: composer.characterCount,
      languageTarget: composer.ok ? composer.contact.language : undefined,
      requestedTones: composer.ok ? [...composer.preferences.tone] : [],
      templateSelection: composer.ok ? { ...composer.templateSelection } : undefined,
      contextSource: composer.context.contextSource,
      contextDetail: composer.context.detail,
      includedMemoryCount: composer.context.includedMemoryCount,
      excludedGuidanceMemoryCount: composer.context.excludedGuidanceMemoryCount,
      excludedPrivateMemoryCount: composer.context.excludedPrivateMemoryCount,
      excludedSensitiveMemoryCount: composer.context.excludedSensitiveMemoryCount,
      templateActionStatus: composer.templateAction.status,
      aiActionStatus: composer.aiAction.status,
      templateAction: { ...composer.templateAction },
      aiAction: { ...composer.aiAction }
    });
  }

  private async runComposerCreateTemplate(
    command: Extract<HarnessCommand, { type: 'composer.create-template' }>,
    signal: AbortSignal
  ) {
    const before = this.dependencies.getState();
    const composer = buildManualComposerState(
      before,
      command.contactId,
      command.reason,
      command.body,
      command.templateId
    );
    const selectedTemplateMatches =
      !command.templateId || (composer.ok && composer.selectedTemplateId === command.templateId);
    const body = command.body ?? composer.renderedTemplateBody;
    const preview = buildTemplateDraft(
      before,
      { contactId: command.contactId, reason: command.reason, body, templateId: command.templateId },
      'command-template-preview'
    );
    if (!composer.ok || !selectedTemplateMatches || !composer.templateAction.enabled || !preview.ok) {
      return succeeded({
        kind: 'message-action',
        action: 'create-template-draft',
        outcome: 'blocked',
        affectedIds: [],
        affectedCount: 0
      });
    }
    assertNotAborted(signal);
    await this.dispatch({
      type: 'createTemplateDraft',
      contactId: command.contactId,
      reason: command.reason,
      body,
      templateId: composer.selectedTemplateId
    });
    const after = this.dependencies.getState();
    const created = after.messages.find(message => !before.messages.some(previous => previous.id === message.id));
    return succeeded({
      kind: 'message-action',
      action: 'create-template-draft',
      createdMessageId: safeOpaqueId(created?.id),
      ...localActionResult(before, after, Boolean(created))
    });
  }

  private async runCheckInAction(
    command: Extract<HarnessCommand, { type: 'checkins.snooze' | 'checkins.mark-contacted' }>,
    signal: AbortSignal
  ) {
    const before = this.dependencies.getState();
    const previous = before.contacts.find(contact => contact.id === command.contactId && !contact.archivedAt);
    if (!previous) {
      return succeeded({
        kind: 'checkin-action',
        action: command.type === 'checkins.snooze' ? 'snooze' : 'mark-contacted',
        outcome: 'blocked',
        affectedIds: [],
        affectedCount: 0
      });
    }
    assertNotAborted(signal);
    if (command.type === 'checkins.snooze') {
      await this.dispatch({
        type: 'snoozeCheckIn',
        contactId: command.contactId,
        days: command.days,
        nowIso: this.dependencies.now().toISOString()
      });
    } else {
      await this.dispatch({
        type: 'markContactedElsewhere',
        contactId: command.contactId,
        nowIso: this.dependencies.now().toISOString()
      });
    }
    const after = this.dependencies.getState();
    const current = after.contacts.find(contact => contact.id === command.contactId);
    const applied = Boolean(previous && current && JSON.stringify(previous) !== JSON.stringify(current));
    return succeeded({
      kind: 'checkin-action',
      action: command.type === 'checkins.snooze' ? 'snooze' : 'mark-contacted',
      ...localActionResult(before, after, applied)
    });
  }

  private async runMessageReviewAction(
    command: Extract<
      HarnessCommand,
      {
        type:
          | 'messages.edit'
          | 'messages.set-channel'
          | 'messages.select-variant'
          | 'messages.acknowledge-duplicate'
          | 'messages.approve'
          | 'messages.reject'
          | 'messages.revoke'
          | 'messages.schedule-follow-up';
      }
    >,
    signal: AbortSignal
  ) {
    const before = this.dependencies.getState();
    const previous = before.messages.find(message => message.id === command.messageId);
    const editableStatuses: MessageDraft['status'][] = ['Needs review', 'Draft'];
    const eligible = (() => {
      if (!previous) return false;
      switch (command.type) {
        case 'messages.edit':
        case 'messages.set-channel':
        case 'messages.select-variant':
        case 'messages.approve':
          return editableStatuses.includes(previous.status);
        case 'messages.acknowledge-duplicate':
          return Boolean(previous.duplicateWarning && !previous.duplicateAcknowledged);
        case 'messages.reject':
          return !['Scheduled', 'Delivery pending', 'Delivery unknown', 'Sent', 'Rejected'].includes(previous.status);
        case 'messages.revoke':
          return previous.status === 'Scheduled';
        case 'messages.schedule-follow-up':
          return previous.status === 'Sent';
      }
    })();
    if (!eligible) {
      return succeeded({
        kind: 'message-action',
        action: command.type.replace('messages.', '') as
          | 'edit'
          | 'set-channel'
          | 'select-variant'
          | 'acknowledge-duplicate'
          | 'approve'
          | 'reject'
          | 'revoke'
          | 'schedule-follow-up',
        outcome: 'blocked',
        affectedIds: [],
        affectedCount: 0
      });
    }
    assertNotAborted(signal);
    switch (command.type) {
      case 'messages.edit':
        await this.dispatch({ type: 'editMessage', messageId: command.messageId, body: command.body });
        break;
      case 'messages.set-channel':
        await this.dispatch({
          type: 'setMessageChannel',
          messageId: command.messageId,
          channel: command.channel
        });
        break;
      case 'messages.select-variant':
        await this.dispatch({
          type: 'selectVariant',
          messageId: command.messageId,
          variant: command.variant,
          discardEditedBody: command.discardEditedBody
        });
        break;
      case 'messages.acknowledge-duplicate':
        await this.dispatch({ type: 'acknowledgeDuplicateRisk', messageId: command.messageId });
        break;
      case 'messages.approve':
        await this.dispatch({
          type: 'approveMessage',
          messageId: command.messageId,
          nowIso: this.dependencies.now().toISOString(),
          reviewNext: command.reviewNext
        });
        break;
      case 'messages.reject':
        await this.dispatch({ type: 'rejectMessage', messageId: command.messageId, reviewNext: command.reviewNext });
        break;
      case 'messages.revoke':
        await this.dispatch({ type: 'revokeMessage', messageId: command.messageId });
        break;
      case 'messages.schedule-follow-up':
        await this.dispatch({
          type: 'scheduleMessageFollowUp',
          messageId: command.messageId,
          delayDays: command.delayDays,
          nowIso: this.dependencies.now().toISOString()
        });
        break;
    }
    const after = this.dependencies.getState();
    const current = after.messages.find(message => message.id === command.messageId);
    let applied = false;
    switch (command.type) {
      case 'messages.approve':
        applied = current?.status === 'Scheduled';
        break;
      case 'messages.reject':
        applied = previous?.status !== 'Rejected' && current?.status === 'Rejected';
        break;
      case 'messages.revoke':
        applied = previous?.status === 'Scheduled' && current?.status === 'Needs review';
        break;
      case 'messages.schedule-follow-up':
        applied = after.events.some(event => !before.events.some(previousEvent => previousEvent.id === event.id));
        break;
      default:
        applied = Boolean(previous && current && JSON.stringify(previous) !== JSON.stringify(current));
    }
    const action = command.type.replace('messages.', '') as
      | 'edit'
      | 'set-channel'
      | 'select-variant'
      | 'acknowledge-duplicate'
      | 'approve'
      | 'reject'
      | 'revoke'
      | 'schedule-follow-up';
    return succeeded({ kind: 'message-action', action, ...localActionResult(before, after, applied) });
  }

  private async liveSetupNeedsAction(signal: AbortSignal) {
    try {
      const environment = await this.dependencies.setupEnvironment(signal);
      assertNotAborted(signal);
      const report = buildSetupDoctorReport(this.dependencies.getState(), environment, this.dependencies.now());
      return report.checksByGroup.some(group => group.checks.some(check => check.status === 'Needs action'));
    } catch {
      if (signal.aborted) throw new Error('Operation cancelled.');
      // An unreadable live provider/permission environment is itself a setup
      // issue; Home must not hide it because legacy saved rows looked ready.
      return true;
    }
  }

  private async runHomeInspection(signal: AbortSignal) {
    const state = this.dependencies.getState();
    const now = this.dependencies.now();
    const archived = new Set(state.contacts.filter(contact => contact.archivedAt).map(contact => contact.id));
    const setupNeedsAction = await this.liveSetupNeedsAction(signal);
    const planner = buildHomePlanner(state, now, {
      setupNeedsAction
    });
    const actions = planner.actions
      .filter(action => !action.contactId || !archived.has(action.contactId))
      .map(action => ({
        id: safeOpaqueId(action.id),
        kind: action.kind,
        priority: action.priority,
        title: action.title,
        detail: action.detail,
        targetScreen: action.targetScreen,
        contactId: safeOpaqueId(action.contactId),
        messageId: safeOpaqueId(action.messageId),
        eventId: safeOpaqueId(action.eventId)
      }));
    const upcomingCandidates = state.events
      .filter(event => !archived.has(event.contactId))
      .map(event => ({ event, occurrence: eventOccurrenceIso(event, now) }))
      .filter((item): item is { event: AppState['events'][number]; occurrence: string } =>
        Boolean(item.occurrence && Date.parse(item.occurrence) >= now.getTime())
      )
      .sort(
        (left, right) => left.occurrence.localeCompare(right.occurrence) || left.event.id.localeCompare(right.event.id)
      );
    const upcoming = upcomingCandidates.slice(0, 5).map(({ event, occurrence }) => ({
      eventId: event.id,
      contactId: event.contactId,
      eventType: event.type,
      label: event.label,
      occurrence,
      verified: event.verified
    }));
    const newestBackup = [...state.backups]
      .filter(backup => safeIso(backup.createdAt))
      .sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0];
    const backupAgeDays = newestBackup
      ? Math.max(0, Math.floor((now.getTime() - Date.parse(newestBackup.createdAt)) / 86_400_000))
      : undefined;
    if (
      !safeIso(planner.generatedAt) ||
      actions.some(
        action =>
          !action.id ||
          !Number.isSafeInteger(action.priority) ||
          action.priority < 0 ||
          action.priority > 1_000 ||
          !boundedPrivateString(action.title, MAX_READINESS_LENGTH) ||
          !boundedPrivateString(action.detail, MAX_REVIEW_ERROR_LENGTH)
      ) ||
      upcoming.some(
        item =>
          !safeOpaqueId(item.eventId) ||
          !safeOpaqueId(item.contactId) ||
          !safeIso(item.occurrence) ||
          !boundedPrivateString(item.label, MAX_EVENT_LABEL_LENGTH)
      )
    ) {
      return failure('home-inspection-invalid', 'Home planning metadata exceeded safe output bounds.');
    }
    return succeeded({
      kind: 'home-inspection',
      generatedAt: planner.generatedAt,
      summaryCode: actions.length > 0 ? 'actions-due' : 'no-actions-due',
      summary: planner.summary,
      actionCount: actions.length,
      actions: actions.map(action => ({ ...action, id: action.id as string })),
      counts: { ...planner.counts },
      metrics: {
        activeContacts: state.contacts.length - archived.size,
        upcomingEvents: upcomingCandidates.length,
        pendingReview: state.messages.filter(
          message =>
            !archived.has(message.contactId) && (message.status === 'Needs review' || message.status === 'Draft')
        ).length,
        failedOrBlocked: state.messages.filter(
          message =>
            !archived.has(message.contactId) && ['Failed', 'Blocked', 'Delivery unknown'].includes(message.status)
        ).length,
        backups: state.backups.length
      },
      upcoming,
      setupNeedsAction,
      onboardingCompleted: state.onboarding.completed,
      backup: {
        status: backupAgeDays === undefined ? 'never' : backupAgeDays > 30 ? 'stale' : 'fresh',
        latestCreatedAt: newestBackup?.createdAt,
        ageDays: backupAgeDays
      },
      redacted: true
    });
  }

  private async runAiFallback(
    command: Extract<HarnessCommand, { type: 'ai.draft' }>,
    privacy: {
      includedMemoryCount: number;
      excludedPrivateMemoryCount: number;
      includedPriorMessageCount: number;
    },
    signal: AbortSignal,
    feedback?: MessageDraft['regenerationFeedback'],
    regenerationSource?: MessageRegenerationSource
  ) {
    const before = this.dependencies.getState();
    const body = firstRenderedTemplateForContact(before, command.contactId, command.reason);
    if (!body) {
      return failure(
        'ai-fallback-unavailable',
        'Neither AI nor a local review-first template is available for this request.'
      );
    }
    const preview = buildTemplateDraft(
      before,
      { contactId: command.contactId, reason: command.reason, body },
      'ai-fallback-preview'
    );
    if (!preview.ok) {
      return failure(
        'ai-fallback-unavailable',
        'Neither AI nor a valid local review-first template can create this draft.'
      );
    }
    assertNotAborted(signal);
    await this.dispatch({
      type: 'generateMessage',
      contactId: command.contactId,
      eventId: command.eventId,
      reason: command.reason,
      fallbackReason: feedback
        ? 'AI regeneration used a local review-first fallback.'
        : 'AI drafting used a local review-first fallback.',
      excludedMemoryIds: command.excludedMemoryIds,
      includePriorMessages: command.includePriorMessages,
      feedback,
      regenerationSource,
      generationOrigin: 'User requested'
    });
    const after = this.dependencies.getState();
    const created = after.messages.find(message => !before.messages.some(previous => previous.id === message.id));
    const createdMessageId = safeOpaqueId(created?.id);
    if (!createdMessageId) {
      return regenerationSource
        ? failure(
            'message-regeneration-stale',
            'The source draft changed while regeneration was running. Review the current draft and try again.'
          )
        : failure('ai-fallback-unavailable', 'The local review-first template draft could not be verified.');
    }
    return succeeded({
      kind: 'ai-draft',
      created: true,
      source: 'local-template-fallback',
      createdMessageId,
      ...privacy
    });
  }

  private async runAiDraft(
    command: Extract<HarnessCommand, { type: 'ai.draft' }>,
    signal: AbortSignal,
    feedback?: MessageDraft['regenerationFeedback'],
    regenerationSource?: MessageRegenerationSource
  ) {
    const state = this.dependencies.getState();
    if (command.eventId) {
      const event = state.events.find(item => item.id === command.eventId);
      if (!event || event.contactId !== command.contactId) {
        return failure(
          'ai-event-contact-mismatch',
          'The selected event does not belong to the selected contact. No draft was created.'
        );
      }
    }
    const request = buildAiDraftRequest(state, command.contactId, command.eventId, command.reason, {
      excludedMemoryIds: command.excludedMemoryIds,
      includePriorMessages: command.includePriorMessages,
      feedback
    });
    const emptyPrivacy = {
      includedMemoryCount: 0,
      excludedPrivateMemoryCount: state.memories.filter(
        memory => memory.contactId === command.contactId && memory.category === 'Private'
      ).length,
      includedPriorMessageCount: 0
    };
    if (!request.ok) {
      return request.error.kind === 'disabled'
        ? this.runAiFallback(command, emptyPrivacy, signal, feedback, regenerationSource)
        : failure(`ai-${request.error.kind}`, 'The selected contact or event is unavailable. No draft was created.');
    }

    const privacy = {
      includedMemoryCount: request.request.privacy.includedMemoryCount,
      excludedPrivateMemoryCount: request.request.privacy.excludedPrivateMemoryCount,
      includedPriorMessageCount: request.request.privacy.includedPriorMessageCount
    };
    let response;
    try {
      response = await this.dependencies.requestAiDraft(request.request, signal);
      assertNotAborted(signal);
    } catch {
      if (signal.aborted) throw new Error('Operation cancelled.');
      await this.dispatch({
        type: 'aiProviderFailure',
        error: {
          kind: 'network',
          message: 'AI drafting did not complete. A local review-first template was used instead.'
        },
        privacySummary: request.privacySummary
      });
      return this.runAiFallback(command, privacy, signal, feedback, regenerationSource);
    }
    if (!response.ok) {
      const safeError = {
        ...response.error,
        message: safeOperationalSummary(
          response.error.message,
          'AI drafting did not complete. A local review-first template was used instead.'
        )
      };
      await this.dispatch({
        type: 'aiProviderFailure',
        error: safeError,
        privacySummary: request.privacySummary,
        observation: response.observation
      });
      return this.runAiFallback(command, privacy, signal, feedback, regenerationSource);
    }
    const contact = state.contacts.find(item => item.id === command.contactId);
    const validated = normalizeAiDraftResponse(
      { variants: response.variants },
      {
        expectedLanguage: contact?.language,
        previousMessages: request.request.priorApprovedMessages
      }
    );
    if (!validated.ok) {
      await this.dispatch({
        type: 'aiProviderFailure',
        error: validated.error,
        privacySummary: request.privacySummary,
        observation: response.observation
      });
      return this.runAiFallback(command, privacy, signal, feedback, regenerationSource);
    }
    const before = this.dependencies.getState();
    await this.dispatch({
      type: 'createAiDraft',
      contactId: command.contactId,
      eventId: command.eventId,
      reason: command.reason,
      variants: validated.variants,
      privacySummary: request.privacySummary,
      observation: response.observation,
      feedback,
      regenerationSource,
      generationOrigin: 'User requested'
    });
    const after = this.dependencies.getState();
    const created = after.messages.find(message => !before.messages.some(previous => previous.id === message.id));
    const createdMessageId = safeOpaqueId(created?.id);
    if (!createdMessageId) {
      return regenerationSource
        ? failure(
            'message-regeneration-stale',
            'The source draft changed while regeneration was running. Review the current draft and try again.'
          )
        : failure('ai-draft-unverified', 'The review-first AI draft could not be verified.');
    }
    return succeeded({
      kind: 'ai-draft',
      created: true,
      source: 'ai',
      createdMessageId,
      ...privacy
    });
  }

  private async runEmailDelivery(command: Extract<HarnessCommand, { type: 'email.deliver' }>, signal: AbortSignal) {
    const state = this.dependencies.getState();
    if (this.emailAttemptIsLocked(state, command.messageId)) {
      return failure(
        'email-attempt-locked',
        'This email already has a pending, sent, or unknown provider attempt. Reconcile it before any new delivery.'
      );
    }
    const request = buildEmailDeliveryRequest(state, command.messageId, this.dependencies.now());
    if (!request.ok) return failure(`email-${request.error.kind}`, request.error.message);
    let result: EmailSendResult;
    try {
      result = await this.dependencies.sendEmail(request.request, signal);
    } catch {
      const error: EmailDeliveryError = {
        kind: 'delivery-unknown',
        message: 'The delivery response was interrupted. Reconcile this idempotent attempt before retrying.'
      };
      this.emailDeliveryLocks.add(command.messageId);
      await this.dispatch({
        type: 'emailDeliveryUnknown',
        messageId: command.messageId,
        idempotencyKey: request.request.idempotencyKey,
        error
      });
      return unknown('email-delivery-unknown', error.message);
    }

    if (!result.ok) {
      const safeError: EmailDeliveryError = {
        ...result.error,
        message: safeOperationalSummary(
          result.error.message,
          'Email delivery did not return a safe diagnostic. Review provider setup before continuing.'
        )
      };
      if (result.outcome === 'unknown') {
        this.emailDeliveryLocks.add(command.messageId);
        await this.dispatch({
          type: 'emailDeliveryUnknown',
          messageId: command.messageId,
          idempotencyKey: result.idempotencyKey,
          error: safeError
        });
        return unknown('email-delivery-unknown', safeError.message);
      }
      await this.dispatch({ type: 'emailProviderFailure', error: safeError, messageId: command.messageId });
      return failure(`email-${safeError.kind}`, safeError.message, retryableEmailError(safeError));
    }

    if (result.status === 'accepted') {
      if (
        typeof result.deliveryId !== 'string' ||
        result.deliveryId.length === 0 ||
        result.deliveryId.length > MAX_PROVIDER_IDENTIFIER_LENGTH
      ) {
        const error: EmailDeliveryError = {
          kind: 'invalid-response',
          message: 'The accepted delivery did not include a bounded provider identifier. Reconcile before retrying.'
        };
        this.emailDeliveryLocks.add(command.messageId);
        await this.dispatch({
          type: 'emailDeliveryUnknown',
          messageId: command.messageId,
          idempotencyKey: request.request.idempotencyKey,
          error
        });
        return unknown('email-delivery-unknown', error.message);
      }
      this.emailDeliveryLocks.add(command.messageId);
      await this.dispatch({
        type: 'emailDeliveryAccepted',
        messageId: command.messageId,
        idempotencyKey: request.request.idempotencyKey,
        deliveryId: result.deliveryId
      });
      return succeeded({ kind: 'email-delivery', status: 'accepted', deliveryRecorded: true });
    }

    if (result.deliveryId && result.deliveryId.length > MAX_PROVIDER_IDENTIFIER_LENGTH) {
      const error: EmailDeliveryError = {
        kind: 'invalid-response',
        message: 'The provider returned an unsupported delivery identifier. Reconcile before retrying.'
      };
      this.emailDeliveryLocks.add(command.messageId);
      await this.dispatch({
        type: 'emailDeliveryUnknown',
        messageId: command.messageId,
        idempotencyKey: request.request.idempotencyKey,
        error
      });
      return unknown('email-delivery-unknown', error.message);
    }
    this.emailDeliveryLocks.add(command.messageId);
    await this.dispatch({
      type: 'emailSent',
      messageId: command.messageId,
      idempotencyKey: request.request.idempotencyKey,
      deliveryId: result.deliveryId,
      nowIso: this.dependencies.now().toISOString()
    });
    return succeeded({ kind: 'email-delivery', status: 'sent', deliveryRecorded: true });
  }

  private async runEmailReconciliation(command: Extract<HarnessCommand, { type: 'email.reconcile' }>) {
    const state = this.dependencies.getState();
    const message = state.messages.find(item => item.id === command.messageId);
    const attempt = message?.emailDeliveryAttempt;
    if (
      !message ||
      message.channel !== 'Email' ||
      (message.status !== 'Delivery pending' && message.status !== 'Delivery unknown') ||
      !attempt ||
      (attempt.status !== 'Accepted' && attempt.status !== 'Unknown') ||
      !attempt.idempotencyKey ||
      attempt.idempotencyKey.length > 512 ||
      (attempt.deliveryId !== undefined &&
        (attempt.deliveryId.length === 0 || attempt.deliveryId.length > MAX_PROVIDER_IDENTIFIER_LENGTH))
    ) {
      return failure(
        'email-reconciliation-unavailable',
        'Only a saved accepted or unknown email attempt can be reconciled without resending.'
      );
    }

    let result;
    try {
      result = await this.dependencies.reconcileEmail(
        { idempotencyKey: attempt.idempotencyKey, deliveryId: attempt.deliveryId },
        new AbortController().signal
      );
    } catch {
      const error: EmailDeliveryError = {
        kind: 'delivery-unknown',
        message: 'Email delivery status could not be verified. The saved attempt remains unknown and was not resent.'
      };
      this.emailDeliveryLocks.add(command.messageId);
      await this.dispatch({
        type: 'emailDeliveryUnknown',
        messageId: command.messageId,
        idempotencyKey: attempt.idempotencyKey,
        error
      });
      return unknown('email-reconciliation-unknown', error.message);
    }

    if (!result.ok && result.outcome === 'unknown') {
      const error: EmailDeliveryError = {
        ...result.error,
        message: safeOperationalSummary(
          result.error.message,
          'Email delivery status remains unknown. The saved attempt was not resent.'
        )
      };
      this.emailDeliveryLocks.add(command.messageId);
      await this.dispatch({
        type: 'emailDeliveryUnknown',
        messageId: command.messageId,
        idempotencyKey: attempt.idempotencyKey,
        error
      });
      return unknown('email-reconciliation-unknown', error.message);
    }

    const deliveryId = result.ok ? result.deliveryId : attempt.deliveryId;
    if (deliveryId !== undefined && (deliveryId.length === 0 || deliveryId.length > MAX_PROVIDER_IDENTIFIER_LENGTH)) {
      this.emailDeliveryLocks.add(command.messageId);
      return unknown(
        'email-reconciliation-unknown',
        'Email delivery status returned an unsupported identifier. The saved attempt was not resent.'
      );
    }
    const status = result.ok ? result.status : 'failed';
    await this.dispatch({
      type: 'emailDeliveryReconciled',
      messageId: command.messageId,
      idempotencyKey: attempt.idempotencyKey,
      status,
      deliveryId
    });
    if (status === 'failed') this.emailDeliveryLocks.delete(command.messageId);
    else this.emailDeliveryLocks.add(command.messageId);
    return succeeded({ kind: 'email-reconciliation', status, deliveryRecorded: true });
  }

  private handoffIssue(state: AppState, message: MessageDraft | undefined, allowShareFallback = false) {
    if (!message) return 'The selected message is unavailable.';
    if (message.status !== 'Scheduled') return 'Approve the message before opening a manual handoff.';
    const body = validateMessageBodyForChannel(message);
    if (!body.ok) return body.message;
    const duplicateRisk = assessDuplicateMessageRisk(state, message);
    if (duplicateRisk.risk.risk && !duplicateRisk.acknowledged) {
      return 'Duplicate risk changed after approval. Review and explicitly acknowledge the current warning before sending.';
    }
    return (
      messageApprovalWindowIssue(message, this.dependencies.now().toISOString()) ??
      messageApprovalRouteIssue(state, message, {
        allowDndManualControl: true,
        allowShareFallback
      }) ??
      messageDispatchTimingIssue(state, message, this.dependencies.now())
    );
  }

  private async runHandoffOpen(command: Extract<HarnessCommand, { type: 'handoff.open' }>, _signal: AbortSignal) {
    const state = this.dependencies.getState();
    const message = state.messages.find(item => item.id === command.messageId);
    const issue = this.handoffIssue(state, message, command.preferFallback);
    if (issue || !message) return failure('handoff-not-ready', issue ?? 'The message is unavailable.');
    const contact = state.contacts.find(item => item.id === message.contactId);
    const target = buildHandoffTarget(contact, message);
    const result = await this.dependencies.openHandoff(
      {
        target,
        body: message.body,
        contactName: contact?.name,
        preferFallback: command.preferFallback
      },
      new AbortController().signal
    );
    if (result.outcome === 'failed') {
      return failure('handoff-open-failed', result.errorMessage ?? 'The manual handoff could not be opened.', true);
    }
    if (result.needsSentConfirmation) {
      this.handoffConfirmations.set(command.messageId, {
        messageFingerprint: handoffFingerprint(message),
        usedFallback: result.usedFallback,
        expiresAt: this.dependencies.now().getTime() + HANDOFF_CONFIRMATION_WINDOW_MS
      });
    } else {
      this.handoffConfirmations.delete(command.messageId);
    }
    return succeeded({
      kind: 'handoff-open',
      outcome: result.outcome,
      usedFallback: result.usedFallback,
      confirmationRequired: result.needsSentConfirmation
    });
  }

  private async runHandoffConfirmation(
    command: Extract<HarnessCommand, { type: 'handoff.confirm' }>,
    signal: AbortSignal
  ) {
    if (!command.sent) {
      this.handoffConfirmations.delete(command.messageId);
      return succeeded({ kind: 'handoff-confirmation', markedSent: false });
    }
    const state = this.dependencies.getState();
    const message = state.messages.find(item => item.id === command.messageId);
    const expected = this.handoffConfirmations.get(command.messageId);
    if (
      !message ||
      !expected ||
      this.dependencies.now().getTime() > expected.expiresAt ||
      expected.messageFingerprint !== handoffFingerprint(message)
    ) {
      this.handoffConfirmations.delete(command.messageId);
      return failure(
        'handoff-confirmation-stale',
        'Open the current approved message in its destination before confirming it as sent.'
      );
    }
    const issue = this.handoffIssue(state, message, expected.usedFallback);
    if (issue) {
      this.handoffConfirmations.delete(command.messageId);
      return failure('handoff-confirmation-blocked', issue);
    }
    const action: RelateAction = {
      type: 'manualHandoff',
      messageId: command.messageId,
      shareFallbackUsed: expected.usedFallback,
      nowIso: this.dependencies.now().toISOString()
    };
    const predicted = relateReducer(state, action);
    const predictedMessage = predicted.messages.find(item => item.id === command.messageId);
    if (predictedMessage?.status !== 'Sent') {
      this.handoffConfirmations.delete(command.messageId);
      return failure('handoff-confirmation-blocked', 'The message is no longer eligible to be marked sent.');
    }
    assertNotAborted(signal);
    await this.dispatch(action);
    this.handoffConfirmations.delete(command.messageId);
    return succeeded({ kind: 'handoff-confirmation', markedSent: true });
  }

  private async runEventTextImport(
    command: Extract<HarnessCommand, { type: 'events.import-text' }>,
    signal: AbortSignal
  ) {
    const parsed = parseEventImportText(command.raw, command.format, this.dependencies.now());
    const invalidCandidates: CalendarImportCandidate[] = parsed.errors.map((error, index) => ({
      sourceId: `text-import-invalid-${fnvFingerprint(`${index}:${error}`)}`,
      title: `Import item ${index + 1} needs review`,
      startDate: '',
      notes: error.slice(0, 500)
    }));
    const candidates = validateCalendarCandidates([...parsed.candidates, ...invalidCandidates]);
    if (!candidates) return failure('event-import-invalid-result', 'Event import exceeded supported bounds.');
    if (candidates.length === 0) {
      return failure('event-import-empty', 'No event candidates or editable import issues were found.');
    }
    assertNotAborted(signal);
    return this.createCalendarImportReviewSession(
      candidates,
      parsed.candidates.length + Math.max(parsed.skipped, parsed.errors.length),
      signal,
      parsed.errors.length,
      Math.max(0, parsed.skipped - parsed.errors.length)
    );
  }

  private async runEventFileImport(signal: AbortSignal) {
    let selected: Awaited<ReturnType<CommandRuntimeDependencies['pickEventImportFile']>>;
    try {
      selected = await this.dependencies.pickEventImportFile(signal);
    } catch (error) {
      if (signal.aborted) throw error;
      return failure(
        'event-file-import-failed',
        'Event file selection did not complete. Retry with a CSV or vCard file, or use events.import-text.',
        true
      );
    }
    assertNotAborted(signal);
    if (!selected) {
      return failure(
        'event-file-import-cancelled',
        'No event file was selected. Select a CSV or vCard file again, or use events.import-text.'
      );
    }
    if (
      !boundedPrivateString(selected.name, 255) ||
      !boundedPrivateString(selected.raw, MAX_EVENT_IMPORT_BYTES, true) ||
      new TextEncoder().encode(selected.raw).byteLength > MAX_EVENT_IMPORT_BYTES
    ) {
      return failure(
        'event-file-import-invalid-result',
        'The selected event file exceeded safe import bounds. Choose a smaller CSV or vCard file, or use events.import-text.'
      );
    }
    const lowerName = selected.name.toLocaleLowerCase();
    const format = lowerName.endsWith('.csv')
      ? ('csv' as const)
      : lowerName.endsWith('.vcf') || lowerName.endsWith('.vcard')
        ? ('vcard' as const)
        : undefined;
    if (!format) {
      return failure(
        'event-file-import-unsupported',
        'Choose a .csv, .vcf, or .vcard event file, or use events.import-text.'
      );
    }
    return this.runEventTextImport({ type: 'events.import-text', raw: selected.raw, format }, signal);
  }

  private async runBackupExport(command: Extract<HarnessCommand, { type: 'backup.export' }>, _signal: AbortSignal) {
    const state = this.dependencies.getState();
    if (!this.sensitiveAuthorizationAvailable(state)) {
      return failure('fresh-unlock-required', 'Unlock RelateAI again before exporting private data.');
    }
    if (validateBackupPassphrase(command.passphrase).length > 0) {
      return failure('backup-passphrase-invalid', 'The backup passphrase does not meet the required policy.');
    }
    const result = await this.dependencies.exportBackup(
      state,
      command.passphrase,
      command.destination,
      new AbortController().signal
    );
    if (
      !result.preview ||
      !Number.isSafeInteger(result.preview.recordCount) ||
      result.preview.recordCount < 0 ||
      Number.isNaN(Date.parse(result.preview.createdAt)) ||
      typeof result.verifiedPortableCopy !== 'boolean' ||
      !/^[^/\\\u0000-\u001f]{1,240}$/.test(result.fileName) ||
      !Number.isSafeInteger(result.byteCount) ||
      result.byteCount <= 0 ||
      result.byteCount > 256 * 1024 * 1024
    ) {
      return failure('backup-invalid-result', 'Backup export returned unsupported verification metadata.');
    }
    const verifiedPortableCopy = result.disposition === 'saved-export' && result.verifiedPortableCopy;
    let backupConfirmationToken: string | undefined;
    let confirmationExpiresAt: string | undefined;
    if (verifiedPortableCopy) {
      await this.dispatch({ type: 'createBackup' });
    } else {
      backupConfirmationToken = this.createTransientToken();
      if (!backupConfirmationToken) {
        return failure(
          'backup-confirmation-unavailable',
          'The export completed, but a safe backup confirmation could not be created.'
        );
      }
      const expiresAt = this.dependencies.now().getTime() + TRANSIENT_SELECTION_WINDOW_MS;
      this.pendingBackupExport = {
        confirmationToken: backupConfirmationToken,
        baseStateFingerprint: fnvFingerprint(materialStateFingerprint(state)),
        expiresAt
      };
      confirmationExpiresAt = new Date(expiresAt).toISOString();
    }
    return succeeded({
      kind: 'backup-export',
      destination: result.disposition,
      shared: result.shared,
      temporaryFileRemoved: result.temporaryFileRemoved,
      recordCount: result.preview.recordCount,
      createdAt: result.preview.createdAt,
      fileName: result.fileName,
      byteCount: result.byteCount,
      verifiedPortableCopy,
      freshnessRecorded: verifiedPortableCopy,
      backupConfirmationToken,
      confirmationExpiresAt
    });
  }

  private async runBackupExportConfirmation(command: Extract<HarnessCommand, { type: 'backup.export-confirm' }>) {
    const pending = this.pendingBackupExport;
    this.pendingBackupExport = undefined;
    if (
      !pending ||
      pending.confirmationToken !== command.backupConfirmationToken ||
      this.dependencies.now().getTime() > pending.expiresAt
    ) {
      return failure('backup-confirmation-stale', 'Export a fresh encrypted backup before confirming a portable copy.');
    }
    if (pending.baseStateFingerprint !== fnvFingerprint(materialStateFingerprint(this.dependencies.getState()))) {
      return failure(
        'backup-confirmation-stale',
        'Local data changed after export. Create and confirm a fresh encrypted backup.'
      );
    }
    await this.dispatch({ type: 'createBackup' });
    return succeeded({ kind: 'backup-export-confirmation', freshnessRecorded: true });
  }

  private async runBackupSelect(signal: AbortSignal) {
    const selected = await this.dependencies.selectBackup(signal);
    assertNotAborted(signal);
    if (!selected) return failure('backup-selection-cancelled', 'No encrypted backup file was selected.');
    assertBackupRawInput(selected.raw);
    const preview = previewEncryptedBackup(selected.raw);
    const selectionToken = this.createTransientToken();
    if (!selectionToken) {
      return failure('backup-selection-unavailable', 'A safe temporary backup selection session could not be created.');
    }
    const expiresAt = this.dependencies.now().getTime() + TRANSIENT_SELECTION_WINDOW_MS;
    this.pendingBackupSelection = { selectionToken, raw: selected.raw, expiresAt };
    return succeeded({
      kind: 'backup-file-selection',
      selectionToken,
      expiresAt: new Date(expiresAt).toISOString(),
      version: preview.version,
      format: preview.format,
      app: preview.app,
      persistenceVersion: preview.persistenceVersion,
      createdAt: preview.createdAt,
      recordCount: preview.recordCount,
      recordCounts: { ...preview.recordCounts },
      warningCount: preview.warnings.length,
      temporaryFileRemoved: selected.temporaryFileRemoved
    });
  }

  private async runBackupRestorePreviewRaw(raw: string, passphrase: string, signal: AbortSignal) {
    const current = this.dependencies.getState();
    if (!this.sensitiveAuthorizationAvailable(current)) {
      return failure('fresh-unlock-required', 'Unlock RelateAI again before previewing private backup data.');
    }
    assertBackupRawInput(raw);
    const preview = previewEncryptedBackup(raw);
    const restored = await this.dependencies.decryptBackup(raw, passphrase, signal);
    assertNotAborted(signal);
    const confirmationToken = this.createTransientToken();
    if (!confirmationToken) {
      return failure('restore-confirmation-unavailable', 'A secure restore confirmation could not be created.');
    }
    const expiresAt = this.dependencies.now().getTime() + RESTORE_CONFIRMATION_WINDOW_MS;
    this.pendingRestore = {
      confirmationToken,
      restoredState: restored,
      baseStateFingerprint: fnvFingerprint(JSON.stringify(current)),
      expiresAt
    };
    return succeeded({
      kind: 'backup-restore-preview',
      confirmationToken,
      expiresAt: new Date(expiresAt).toISOString(),
      mode: 'replace',
      version: preview.version,
      format: preview.format,
      app: preview.app,
      persistenceVersion: preview.persistenceVersion,
      createdAt: preview.createdAt,
      recordCount: preview.recordCount,
      recordCounts: { ...preview.recordCounts },
      warningCount: preview.warnings.length
    });
  }

  private async runBackupRestorePreview(
    command: Extract<HarnessCommand, { type: 'backup.restore-preview' | 'backup.restore-preview-selected' }>,
    signal: AbortSignal
  ) {
    if (command.type === 'backup.restore-preview') {
      return this.runBackupRestorePreviewRaw(command.raw, command.passphrase, signal);
    }
    const selected = this.pendingBackupSelection;
    if (
      !selected ||
      selected.selectionToken !== command.selectionToken ||
      this.dependencies.now().getTime() > selected.expiresAt
    ) {
      this.pendingBackupSelection = undefined;
      return failure('backup-selection-required', 'Select a fresh encrypted backup file before previewing restore.');
    }
    const result = await this.runBackupRestorePreviewRaw(selected.raw, command.passphrase, signal);
    if (result.status === 'succeeded') this.pendingBackupSelection = undefined;
    return result;
  }

  private async runBackupRestoreConfirm(command: Extract<HarnessCommand, { type: 'backup.restore-confirm' }>) {
    const current = this.dependencies.getState();
    if (!this.sensitiveAuthorizationAvailable(current)) {
      return failure('fresh-unlock-required', 'Unlock RelateAI again before confirming data replacement.');
    }
    const pending = this.pendingRestore;
    if (
      !pending ||
      pending.confirmationToken !== command.confirmationToken ||
      this.dependencies.now().getTime() > pending.expiresAt
    ) {
      this.pendingRestore = undefined;
      return failure(
        'restore-preview-required',
        'Create a fresh verified restore preview before confirming replacement.'
      );
    }
    if (pending.baseStateFingerprint !== fnvFingerprint(JSON.stringify(current))) {
      this.pendingRestore = undefined;
      return failure(
        'restore-preview-stale',
        'Local data changed after preview. Create a new restore preview before replacing it.'
      );
    }
    this.pendingRestore = undefined;
    return this.dependencies.runDataReplacement(async () => {
      // The replacement barrier can wait for an already-started lifecycle or
      // navigation commit. Revalidate against the now-quiescent authoritative
      // snapshot before the restore journal records any write intent.
      const verifiedCurrent = this.dependencies.getState();
      if (pending.baseStateFingerprint !== fnvFingerprint(JSON.stringify(verifiedCurrent))) {
        await this.dependencies.installVerifiedState(verifiedCurrent);
        return failure(
          'restore-preview-stale',
          'Local data changed after preview. Create a new restore preview before replacing it.'
        );
      }
      // Once restore starts its journaled durable transaction must settle even
      // if a caller drops interest in the operation result.
      const result = await this.dependencies.restoreData(pending.restoredState, new AbortController().signal);
      await this.dependencies.installVerifiedState(result.state);
      this.clearDataReplacementSessions();
      return succeeded({
        kind: 'backup-restore',
        status: result.status,
        recordCount: totalBackupRecords(countBackupRecords(result.state)),
        nativeReconciliationRequired: result.status === 'reconciliation-required'
      });
    });
  }

  private async runDataClear(_signal: AbortSignal) {
    const state = this.dependencies.getState();
    if (!this.isApplicationLocked() && !this.sensitiveAuthorizationAvailable(state)) {
      return failure('fresh-unlock-required', 'Unlock RelateAI again before clearing private data.');
    }
    return this.dependencies.runDataReplacement(async () => {
      // Clear is journaled and non-cancellable after intent is recorded.
      const cleared = await this.dependencies.clearData(state, new AbortController().signal);
      if (
        cleared.contacts.length > 0 ||
        cleared.events.length > 0 ||
        cleared.memories.length > 0 ||
        cleared.gifts.length > 0 ||
        cleared.messages.length > 0 ||
        cleared.backups.length > 0 ||
        cleared.reminderPlans.length > 0
      ) {
        throw new Error('Transactional clear did not return a verified empty state.');
      }
      await this.dependencies.installVerifiedState(cleared);
      this.clearDataReplacementSessions();
      return succeeded({ kind: 'data-clear', cleared: true });
    });
  }

  private async runDataLifecycleRecovery(signal: AbortSignal) {
    assertNotAborted(signal);
    return this.dependencies.runDataReplacement(async () => {
      const result = await this.dependencies.recoverDataLifecycle(new AbortController().signal);
      this.clearDataReplacementSessions();
      if (result.status === 'reconciliation-required') {
        return failure(
          'data-lifecycle-recovery-required',
          'The interrupted data operation still needs reminder or widget reconciliation. Retry when native services are available.',
          true
        );
      }
      return succeeded({
        kind: 'data-lifecycle-recovery',
        status: 'resolved',
        outcome: result.outcome,
        operation: result.operation,
        journalCleared: true
      });
    });
  }

  private async runPermissionRefresh(signal: AbortSignal) {
    const records = await this.dependencies.refreshPermissions(this.dependencies.getState(), signal);
    assertNotAborted(signal);
    await this.persistPermissionRecords(records);
    return succeeded({
      kind: 'permission-refresh',
      statuses: systemPermissionCapabilities.map(capability => ({
        capability,
        authorization: records[capability].systemAuthorization,
        canAskAgain: records[capability].canAskAgain
      }))
    });
  }

  private async runPermissionPreflight(
    command: Extract<HarnessCommand, { type: 'permissions.preflight' }>,
    signal: AbortSignal
  ) {
    const check = await this.dependencies.preflightPermission(this.dependencies.getState(), command.capability, signal);
    assertNotAborted(signal);
    await this.persistPermissionRecords(check.records);
    return succeeded({
      kind: 'permission-preflight',
      capability: command.capability,
      authorization: check.authorization,
      allowed: check.allowed,
      canAskAgain: check.record.canAskAgain
    });
  }

  private async runPermissionRequest(command: Extract<HarnessCommand, { type: 'permissions.request' }>) {
    const result = await this.dependencies.requestPermission(this.dependencies.getState(), {
      capability: command.capability,
      userIntent: command.userIntent
    });
    await this.persistPermissionRecords(result.records);
    const record = result.records[command.capability];
    return succeeded({
      kind: 'permission-request',
      capability: command.capability,
      userIntent: command.userIntent,
      status: result.status,
      authorization: record.systemAuthorization,
      canAskAgain: record.canAskAgain
    });
  }

  private async runBiometricSetting(
    command: Extract<HarnessCommand, { type: 'biometric.enable' | 'biometric.disable' }>,
    signal: AbortSignal
  ) {
    const enabling = command.type === 'biometric.enable';
    const initial = this.dependencies.getState();
    const check = await this.dependencies.preflightPermission(initial, 'Biometric lock', signal);
    assertNotAborted(signal);
    await this.persistPermissionRecords(check.records);

    if (initial.settings.biometricLockEnabled === enabling) {
      return succeeded({
        kind: 'biometric-setting',
        action: enabling ? 'enable' : 'disable',
        outcome: 'blocked',
        authorization: check.authorization,
        recoveryUsed: false
      });
    }

    if (check.allowed) {
      const authenticated = await this.dependencies.authenticateBiometric(signal);
      assertNotAborted(signal);
      if (!authenticated) {
        return failure(
          'biometric-authentication-failed',
          enabling
            ? 'Biometric lock was not enabled because authentication did not complete.'
            : 'Biometric lock remains enabled because authentication did not complete.'
        );
      }
    } else {
      return failure(
        enabling ? 'biometric-enable-unavailable' : 'biometric-disable-recovery-required',
        enabling
          ? 'Biometric lock requires available, enrolled device authentication.'
          : 'Biometric lock cannot be disabled without live device authentication. Re-enroll biometrics or use the confirmed local-data clear recovery, which does not reveal private data.'
      );
    }

    assertNotAborted(signal);
    await this.dispatch({ type: 'toggleSetting', key: 'biometricLockEnabled' });
    const applied = this.dependencies.getState().settings.biometricLockEnabled === enabling;
    if (applied && enabling) {
      this.applicationUnlocked = true;
      this.sensitiveUnlockedUntil = this.dependencies.now().getTime() + SENSITIVE_UNLOCK_WINDOW_MS;
    }
    if (applied && !enabling) {
      this.applicationUnlocked = false;
      this.sensitiveUnlockedUntil = 0;
    }
    return succeeded({
      kind: 'biometric-setting',
      action: enabling ? 'enable' : 'disable',
      outcome: applied ? 'applied' : 'blocked',
      authorization: check.authorization,
      recoveryUsed: false
    });
  }

  private async runBiometricUnlock(signal: AbortSignal) {
    const unlocked = await this.dependencies.authenticateBiometric(signal);
    assertNotAborted(signal);
    this.applicationUnlocked = unlocked;
    this.sensitiveUnlockedUntil = unlocked ? this.dependencies.now().getTime() + SENSITIVE_UNLOCK_WINDOW_MS : 0;
    return succeeded({ kind: 'biometric-unlock', unlocked });
  }

  private analyticsDashboard(range: Extract<HarnessCommand, { type: 'analytics.inspect' }>['range'], now: Date) {
    return buildAnalyticsDashboard(this.dependencies.getState(), range, now);
  }

  private safeAnalyticsInsights(dashboard: ReturnType<typeof buildAnalyticsDashboard>) {
    const insights = dashboard.insights.map(insight => ({
      id: safeOpaqueId(insight.id),
      title: insight.title,
      detail: insight.contactId
        ? 'Open this contact to review the current relationship recommendation.'
        : insight.detail,
      actionLabel: insight.actionLabel,
      targetScreen: insight.targetScreen,
      contactId: safeOpaqueId(insight.contactId)
    }));
    return insights.some(
      insight =>
        !insight.id ||
        !boundedPrivateString(insight.title, MAX_ACTIVITY_TEXT_LENGTH) ||
        !boundedPrivateString(insight.detail, MAX_ACTIVITY_TEXT_LENGTH) ||
        !boundedPrivateString(insight.actionLabel, MAX_ACTIVITY_TEXT_LENGTH)
    )
      ? undefined
      : insights.map(insight => ({ ...insight, id: insight.id as string }));
  }

  private runAnalyticsInspection(command: Extract<HarnessCommand, { type: 'analytics.inspect' }>) {
    const dashboard = this.analyticsDashboard(command.range, this.dependencies.now());
    const insights = this.safeAnalyticsInsights(dashboard);
    if (
      !insights ||
      (dashboard.emptyState !== undefined &&
        !boundedPrivateString(dashboard.emptyState, MAX_ACTIVITY_TEXT_LENGTH, true))
    ) {
      return failure('analytics-output-invalid', 'Analytics insight data exceeded safe output bounds.');
    }
    return this.boundedPrivateValue({
      kind: 'analytics-inspection',
      range: dashboard.range,
      contactCount: dashboard.contactCount,
      metrics: dashboard.metrics.map(metric => ({ label: metric.label, value: metric.value })),
      relationshipDistribution: dashboard.relationshipDistribution.map(bucket => ({ ...bucket })),
      healthBuckets: dashboard.healthBuckets.map(bucket => ({ ...bucket })),
      overdueContactCount: dashboard.neglectedContacts.length,
      insightCount: insights.length,
      insights,
      empty: Boolean(dashboard.emptyState),
      emptyState: dashboard.emptyState,
      redacted: true
    });
  }

  private async runAnalyticsOpenAction(command: Extract<HarnessCommand, { type: 'analytics.open-action' }>) {
    const state = this.dependencies.getState();
    const dashboard = this.analyticsDashboard(command.range, this.dependencies.now());
    const insight = dashboard.insights.find(item => item.id === command.insightId);
    const activeContact = insight?.contactId
      ? state.contacts.find(contact => contact.id === insight.contactId && !contact.archivedAt)
      : undefined;
    const fallback = !insight || (Boolean(insight.contactId) && !activeContact);
    const targetScreen = fallback ? (insight?.contactId ? 'contacts' : 'more') : insight.targetScreen;
    await this.dispatch({
      type: 'navigate',
      screen: targetScreen,
      contactId: fallback ? undefined : activeContact?.id
    });
    return this.boundedPrivateValue({
      kind: 'analytics-action',
      outcome: fallback ? 'fallback' : 'navigation',
      targetScreen,
      contactId: fallback ? undefined : activeContact?.id
    });
  }

  private async runAnalyticsSummaryShare(
    command: Extract<HarnessCommand, { type: 'analytics.share-summary' }>,
    signal: AbortSignal
  ) {
    const generatedAt = this.dependencies.now();
    const dashboard = this.analyticsDashboard(command.range, generatedAt);
    const summary = buildShareableAnalyticsSummary(dashboard, generatedAt);
    let outcome: Awaited<ReturnType<CommandRuntimeDependencies['shareAnalyticsSummary']>>;
    try {
      outcome = await this.dependencies.shareAnalyticsSummary(summary, signal);
      assertNotAborted(signal);
    } catch {
      if (signal.aborted) throw new Error('Operation cancelled.');
      return failure('analytics-summary-share-failed', 'The redacted relationship summary could not be shared.', true);
    }
    if (outcome === 'shared') {
      await this.dispatch({ type: 'analyticsExported', rowCount: summary.lineCount, format: 'Summary' });
    }
    return succeeded({
      kind: 'analytics-summary-share',
      outcome,
      lineCount: summary.lineCount,
      redacted: true
    });
  }

  private runAnalyticsExportPreview(command: Extract<HarnessCommand, { type: 'analytics.export-preview' }>) {
    const state = this.dependencies.getState();
    const generatedAt = this.dependencies.now();
    const dashboard = buildAnalyticsDashboard(state, command.range, generatedAt);
    const csv = buildAnalyticsCsvReport(state, dashboard, generatedAt);
    const rowCount = csv.split('\n').length;
    const confirmationToken = this.createTransientToken();
    if (!confirmationToken || !Number.isSafeInteger(rowCount) || rowCount < 1) {
      return failure('analytics-export-preview-failed', 'The analytics report preview could not be prepared.');
    }
    const expiresAt = generatedAt.getTime() + TRANSIENT_SELECTION_WINDOW_MS;
    this.pendingAnalyticsExport = {
      confirmationToken,
      baseStateFingerprint: fnvFingerprint(materialStateFingerprint(state)),
      range: command.range,
      generatedAt: generatedAt.toISOString(),
      rowCount,
      expiresAt
    };
    return succeeded({
      kind: 'analytics-export-preview',
      range: command.range,
      rowCount,
      confirmationToken,
      expiresAt: new Date(expiresAt).toISOString(),
      warning: 'This CSV contains contact names and relationship metrics. Review the destination before sharing.',
      redacted: true
    });
  }

  private async runAnalyticsExportConfirm(
    command: Extract<HarnessCommand, { type: 'analytics.export-confirm' }>,
    signal: AbortSignal
  ) {
    const pending = this.pendingAnalyticsExport;
    this.pendingAnalyticsExport = undefined;
    const state = this.dependencies.getState();
    if (
      !pending ||
      pending.confirmationToken !== command.confirmationToken ||
      this.dependencies.now().getTime() > pending.expiresAt ||
      fnvFingerprint(materialStateFingerprint(state)) !== pending.baseStateFingerprint
    ) {
      return failure(
        'analytics-export-confirmation-stale',
        'The analytics export preview expired or the local dataset changed. Preview the report again.'
      );
    }

    const generatedAt = new Date(pending.generatedAt);
    const dashboard = buildAnalyticsDashboard(state, pending.range, generatedAt);
    const csv = buildAnalyticsCsvReport(state, dashboard, generatedAt);
    const rowCount = csv.split('\n').length;
    if (rowCount !== pending.rowCount) {
      return failure('analytics-export-confirmation-stale', 'The analytics report changed. Preview it again.');
    }
    let result: Awaited<ReturnType<CommandRuntimeDependencies['shareAnalyticsCsv']>>;
    try {
      result = await this.dependencies.shareAnalyticsCsv(csv, signal);
      assertNotAborted(signal);
    } catch {
      if (signal.aborted) throw new Error('Operation cancelled.');
      return failure('analytics-export-failed', 'The confirmed analytics CSV could not be shared.', true);
    }
    await this.dispatch({ type: 'analyticsExported', rowCount });
    return succeeded({
      kind: 'analytics-export',
      range: pending.range,
      rowCount,
      shareOpened: result.opened,
      temporaryFileRemoved: result.temporaryFileRemoved,
      redacted: true
    });
  }

  private async runSetupInspection(signal: AbortSignal) {
    const environment = await this.dependencies.setupEnvironment(signal);
    assertNotAborted(signal);
    const report = buildSetupDoctorReport(this.dependencies.getState(), environment, this.dependencies.now());
    const snapshot = buildSetupDoctorDryRunSnapshot(report);
    const checksByGroup = report.checksByGroup.map(group => ({
      group: group.group,
      checks: group.checks.map(check => ({
        id: check.id,
        status: check.status,
        title: check.title,
        impact: check.impact,
        actionLabel: check.actionLabel,
        targetScreen: check.targetScreen,
        contactId: safeOpaqueId(check.contactId),
        command: check.command
      }))
    }));
    if (
      checksByGroup.some(group =>
        group.checks.some(
          check =>
            !safeOpaqueId(check.id) ||
            !boundedPrivateString(check.title, MAX_ACTIVITY_TEXT_LENGTH) ||
            !boundedPrivateString(check.impact, MAX_ACTIVITY_TEXT_LENGTH) ||
            !boundedPrivateString(check.actionLabel, MAX_ACTIVITY_TEXT_LENGTH)
        )
      )
    ) {
      return failure('setup-output-invalid', 'Setup Check cards exceed the supported bounded output size.');
    }
    await this.dispatch({ type: 'setupDoctorDryRunRecorded', detail: snapshot.activityDetail });
    return this.boundedPrivateValue({
      kind: 'setup-inspection',
      readyCount: snapshot.readyCount,
      totalCount: snapshot.totalCount,
      needsActionCount: snapshot.needsActionCount,
      warningCount: snapshot.warningCount,
      recommendedTitle: snapshot.recommendedTitle,
      summary: snapshot.summary,
      checksByGroup,
      safe: true,
      redacted: true
    });
  }

  private async runSetupOpenAction(
    command: Extract<HarnessCommand, { type: 'setup.open-action' }>,
    signal: AbortSignal
  ) {
    const environment = await this.dependencies.setupEnvironment(signal);
    assertNotAborted(signal);
    const state = this.dependencies.getState();
    const report = buildSetupDoctorReport(state, environment, this.dependencies.now());
    const check = report.checksByGroup.flatMap(group => group.checks).find(item => item.id === command.checkId);
    if (!check) {
      await this.dispatch({ type: 'navigate', screen: 'more' });
      return this.boundedPrivateValue({
        kind: 'setup-action',
        outcome: 'fallback',
        targetScreen: 'more'
      });
    }
    if (check.command === 'planReminders') {
      const reconciled = await this.runReminderReconciliation(
        { type: 'reminders.reconcile', reason: 'manual' },
        signal
      );
      if (reconciled.status !== 'succeeded') return reconciled;
      return this.boundedPrivateValue({
        kind: 'setup-action',
        checkId: check.id,
        outcome: 'reminders-reconciled'
      });
    }
    if (check.command === 'testAiProvider') {
      return this.runAiProviderReadinessTest(signal);
    }
    const activeContact = check.contactId
      ? state.contacts.find(contact => contact.id === check.contactId && !contact.archivedAt)
      : undefined;
    const fallback = Boolean(check.contactId) && !activeContact;
    const targetScreen = fallback ? 'more' : (check.targetScreen ?? 'more');
    await this.dispatch({
      type: 'navigate',
      screen: targetScreen,
      contactId: fallback ? undefined : activeContact?.id
    });
    return this.boundedPrivateValue({
      kind: 'setup-action',
      checkId: check.id,
      outcome: fallback ? 'fallback' : 'navigation',
      targetScreen
    });
  }

  private async perform(
    command: HarnessCommand,
    signal: AbortSignal
  ): Promise<OperationTaskResult<RedactedCommandValue>> {
    try {
      switch (command.type) {
        case 'system.catalog':
          return succeeded(buildCommandCatalog());
        case 'domain.dispatch':
          return await this.runDomainAction(command, signal);
        case 'contacts.query':
          return this.runContactsQuery(command);
        case 'events.query':
          return this.runEventsQuery(command);
        case 'messages.query':
          return this.runMessagesQuery(command);
        case 'messages.bulk-preview':
          return this.runBulkMessagePreview(command);
        case 'messages.bulk-apply':
          return await this.runBulkMessageApply(command, signal);
        case 'checkins.query':
          return this.runCheckInsQuery(command);
        case 'contacts.inspect':
          return this.runContactInspection(command);
        case 'contacts.preferences.inspect':
          return this.runContactPreferencesInspection(command);
        case 'groups.inspect':
          return this.runGroupDefaultsInspection();
        case 'contacts.enrichment.inspect':
          return this.runEnrichmentInspection(command);
        case 'events.preparation.inspect':
          return this.runEventPreparationInspection(command);
        case 'messages.preview':
          return this.runMessagePreview(command);
        case 'templates.inspect':
          return this.runTemplateInspection(command);
        case 'memories.query':
          return this.runMemoryQuery(command);
        case 'timeline.query':
          return this.runTimelineQuery(command);
        case 'chat.query':
          return this.runChatQuery(command);
        case 'gifts.inspect':
          return this.runGiftInspection(command);
        case 'onboarding.inspect':
          return this.runOnboardingInspection();
        case 'account.inspect':
          return this.runAccountInspection();
        case 'privacy.inspect':
          return this.runPrivacyInspection();
        case 'settings.inspect':
          return this.runSettingsInspection();
        case 'setup.wizard.inspect':
          return await this.runSetupWizardInspection(command, signal);
        case 'setup.wizard.run-action':
          return await this.runSetupWizardAction(command, signal);
        case 'style.inspect':
          return this.runStyleInspection();
        case 'activity.query':
          return this.runActivityQuery(command);
        case 'activity.open-action':
          return await this.runActivityOpenAction(command, signal);
        case 'activity.resolve':
          return await this.runActivityResolve(command, signal);
        case 'contacts.preferences.set-tone':
        case 'contacts.preferences.set-group':
        case 'contacts.preferences.set-channel':
        case 'contacts.preferences.set-vip':
        case 'contacts.preferences.set-dnd':
        case 'contacts.preferences.set-cadence':
        case 'contacts.preferences.set-automation':
        case 'contacts.preferences.set-send-time':
        case 'contacts.preferences.set-quiet-hours':
        case 'contacts.preferences.set-skip-auto':
        case 'contacts.preferences.use-group-defaults':
        case 'groups.set-default':
        case 'contacts.enrichment.answer':
        case 'events.preparation.toggle':
        case 'messages.test-route':
        case 'messages.retry':
        case 'memories.add':
        case 'memories.edit':
        case 'memories.set-pinned':
        case 'memories.delete':
        case 'gifts.add':
        case 'gifts.delete':
        case 'gifts.set-budget':
        case 'onboarding.set-goal':
        case 'onboarding.set-step':
        case 'onboarding.advance':
        case 'onboarding.skip':
        case 'onboarding.complete':
        case 'onboarding.reopen':
        case 'account.use-local':
        case 'account.disconnect':
        case 'privacy.set-whatsapp-consent':
        case 'settings.set-boolean':
        case 'settings.set-automation':
        case 'settings.set-locale':
        case 'settings.set-email-sender':
        case 'settings.set-quiet-hours':
        case 'settings.set-default-send-time':
        case 'settings.add-blackout':
        case 'settings.remove-blackout':
        case 'style.train-samples':
        case 'style.train-sent':
        case 'style.set-enabled':
        case 'home.open-action':
          return await this.runReachableFeatureAction(command, signal);
        case 'messages.regenerate':
          return await this.runMessageRegeneration(command, signal);
        case 'contacts.add':
        case 'contacts.edit-preview':
        case 'contacts.edit-apply':
        case 'contacts.archive-preview':
        case 'contacts.archive-apply':
        case 'contacts.restore':
        case 'contacts.delete-preview':
        case 'contacts.delete-apply':
        case 'contacts.merge-preview':
        case 'contacts.merge-apply':
          return await this.runContactLifecycle(command, signal);
        case 'events.add':
        case 'events.edit-preview':
        case 'events.edit-apply':
        case 'events.delete-preview':
        case 'events.delete-apply':
        case 'events.merge-preview':
        case 'events.merge-apply':
          return await this.runEventLifecycle(command, signal);
        case 'checkins.snooze':
        case 'checkins.mark-contacted':
          return await this.runCheckInAction(command, signal);
        case 'composer.inspect':
          return this.runComposerInspection(command);
        case 'composer.create-template':
          return await this.runComposerCreateTemplate(command, signal);
        case 'messages.edit':
        case 'messages.set-channel':
        case 'messages.select-variant':
        case 'messages.acknowledge-duplicate':
        case 'messages.approve':
        case 'messages.reject':
        case 'messages.revoke':
        case 'messages.schedule-follow-up':
          return await this.runMessageReviewAction(command, signal);
        case 'contacts.import':
        case 'contacts.import-preview':
          return await this.runContactImportPreview(signal);
        case 'contacts.import-apply':
          return await this.runContactImportApply(command, signal);
        case 'calendar.import':
        case 'calendar.import-preview':
          return await this.runCalendarImportPreview(signal);
        case 'calendar.import-apply':
          return await this.runCalendarImportApply(command, signal);
        case 'calendar.export':
          return await this.runCalendarExport(command, signal);
        case 'reminders.reconcile':
          return await this.runReminderReconciliation(command, signal);
        case 'ai.draft':
          return await this.runAiDraft(command, signal);
        case 'email.deliver':
          return await this.runEmailDelivery(command, signal);
        case 'email.reconcile':
          return await this.runEmailReconciliation(command);
        case 'handoff.open':
          return await this.runHandoffOpen(command, signal);
        case 'handoff.confirm':
          return await this.runHandoffConfirmation(command, signal);
        case 'events.import-text':
          return await this.runEventTextImport(command, signal);
        case 'events.import-file':
          return await this.runEventFileImport(signal);
        case 'backup.export':
          return await this.runBackupExport(command, signal);
        case 'backup.export-confirm':
          return await this.runBackupExportConfirmation(command);
        case 'backup.select-file':
          return await this.runBackupSelect(signal);
        case 'backup.restore-preview':
        case 'backup.restore-preview-selected':
          return await this.runBackupRestorePreview(command, signal);
        case 'backup.restore-confirm':
          return await this.runBackupRestoreConfirm(command);
        case 'data.clear':
          return await this.runDataClear(signal);
        case 'data.recover':
          return await this.runDataLifecycleRecovery(signal);
        case 'permissions.refresh':
          return await this.runPermissionRefresh(signal);
        case 'permissions.preflight':
          return await this.runPermissionPreflight(command, signal);
        case 'permissions.request':
          return await this.runPermissionRequest(command);
        case 'biometric.enable':
        case 'biometric.disable':
          return await this.runBiometricSetting(command, signal);
        case 'biometric.unlock':
          return await this.runBiometricUnlock(signal);
        case 'analytics.inspect':
          return this.runAnalyticsInspection(command);
        case 'analytics.open-action':
          return await this.runAnalyticsOpenAction(command);
        case 'analytics.share-summary':
          return await this.runAnalyticsSummaryShare(command, signal);
        case 'analytics.export-preview':
          return this.runAnalyticsExportPreview(command);
        case 'analytics.export-confirm':
          return await this.runAnalyticsExportConfirm(command, signal);
        case 'home.inspect':
          return await this.runHomeInspection(signal);
        case 'setup.inspect':
          return await this.runSetupInspection(signal);
        case 'setup.open-action':
          return await this.runSetupOpenAction(command, signal);
        case 'operation.cancel':
          return succeeded({
            kind: 'operation-cancellation',
            targetScope: command.scope,
            cancelled: this.cancelOperation(command.scope)
          });
      }
    } catch {
      return failure(
        'command-execution-failed',
        'The command did not complete. No private input or relationship content was recorded in the error.',
        false
      );
    }
  }

  async execute(input: unknown): Promise<CommandExecutionResult> {
    const parsed = parseHarnessCommand(input);
    if (!parsed.ok) return { status: 'invalid', error: parsed.error };
    this.pruneExpiredTransientSessions();
    const command = parsed.command;
    if (
      command.type !== 'biometric.unlock' &&
      command.type !== 'biometric.disable' &&
      command.type !== 'data.clear' &&
      command.type !== 'data.recover' &&
      command.type !== 'system.catalog' &&
      this.isApplicationLocked()
    ) {
      return {
        status: 'locked',
        commandType: command.type,
        error: applicationLockedError()
      };
    }
    const scope = scopeFor(command);
    const running = this.dependencies.operations.snapshot(scope);
    if (running?.status === 'running') {
      return {
        status: 'already-running',
        commandType: command.type,
        requestId: running.requestId,
        operation: running
      };
    }

    const mutatesState = commandMutatesState(command);
    const exclusive = commandIsExclusive(command);
    if (
      this.activeExclusiveScope ||
      (exclusive && this.activeScopes.size > 0) ||
      (mutatesState && this.activeMutationScope)
    ) {
      return {
        status: 'conflict',
        commandType: command.type,
        error: safeConflictError(
          exclusive || this.activeExclusiveScope
            ? 'An exclusive data operation is already active. Wait for it to finish.'
            : 'Another state-changing operation is active. Wait for its committed result.'
        )
      };
    }

    this.activeScopes.add(scope);
    if (mutatesState) this.activeMutationScope = scope;
    if (exclusive) this.activeExclusiveScope = scope;
    try {
      const result = await this.dependencies.operations.run(scope, signal => this.perform(command, signal));
      const operation = this.dependencies.operations.snapshot(scope);
      if (result.status === 'already-running') {
        return {
          status: 'already-running',
          commandType: command.type,
          requestId: result.requestId,
          operation
        };
      }
      if (result.status === 'cancelled') {
        return {
          status: 'cancelled',
          commandType: command.type,
          error: {
            code: 'operation-cancelled',
            retryable: true,
            summary: 'The operation was cancelled before its result was committed.'
          },
          operation
        };
      }
      if (!operation) {
        return {
          status: 'cancelled',
          commandType: command.type,
          error: {
            code: 'operation-state-missing',
            retryable: true,
            summary: 'The operation result could not be correlated with runtime state.'
          }
        };
      }
      if (result.status === 'succeeded') {
        return {
          status: 'succeeded',
          commandType: command.type,
          value: result.value,
          operation
        };
      }
      return {
        status: result.status,
        commandType: command.type,
        error: result.error,
        operation
      };
    } finally {
      this.activeScopes.delete(scope);
      if (this.activeMutationScope === scope) this.activeMutationScope = undefined;
      if (this.activeExclusiveScope === scope) this.activeExclusiveScope = undefined;
    }
  }
}
