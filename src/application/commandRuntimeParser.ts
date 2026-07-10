import { analyticsRanges } from '../domain/analytics';
import { MAX_BACKUP_PASSPHRASE_LENGTH, MAX_BACKUP_RAW_BYTES } from '../data/encryptedBackup';
import { MAX_EVENT_IMPORT_BYTES, type EventImportFormat } from '../domain/eventImport';
import type { ContactEssentialsInput } from '../domain/contactEssentials';
import type { StandaloneContactInput } from '../domain/contactLifecycle';
import type { EventEditInput } from '../domain/eventLifecycle';
import { isValidEmailAddress, normalizeEmailAddress } from '../domain/emailDelivery';
import type { MessageBulkAction } from '../domain/messageInbox';
import type {
  AutomationMode,
  ComposerReason,
  EventType,
  GiftCategory,
  MemoryCategory,
  MessageDraft,
  OnboardingGoal,
  OnboardingStepId,
  RelationshipGroup,
  Screen,
  SupportedLocale,
  SystemPermissionCapability,
  Tone
} from '../domain/types';
import type { CommandParseResult, HarnessCommand, HarnessDomainAction, PageRequest } from './commandRuntimeTypes';

export const MAX_RUNTIME_COMMAND_BYTES = MAX_BACKUP_RAW_BYTES * 2 + 64 * 1024;
const MAX_ID_LENGTH = 160;
const MAX_OPERATION_SCOPE_LENGTH = 100;
const MAX_SEARCH_LENGTH = 240;
const MAX_MESSAGE_BODY_LENGTH = 10_000;
const MAX_LABEL_LENGTH = 240;
const MAX_EMAIL_ADDRESS_LENGTH = 254;
const MAX_EXCLUDED_MEMORY_IDS = 25;
const MAX_REGENERATION_INSTRUCTIONS = 8;
const MAX_INSTRUCTION_LENGTH = 180;
const MAX_STYLE_SAMPLE_LENGTH = 20_000;
const MAX_IMPORT_DECISIONS = 500;
const MAX_CALENDAR_EXPORT_EVENT_IDS = 500;
const MAX_BULK_MESSAGE_IDS = 100;
const DEFAULT_PAGE_LIMIT = 25;
const MAX_PAGE_LIMIT = 100;

const screens: readonly Screen[] = [
  'onboarding',
  'home',
  'events',
  'eventForm',
  'messages',
  'contacts',
  'more',
  'analytics',
  'settings',
  'backup',
  'styleCoach',
  'activityHistory',
  'setupCheck',
  'contactDetail',
  'chatHistory',
  'wishPreview',
  'manualComposer'
];
const eventTypes: readonly EventType[] = [
  'Birthday',
  'Anniversary',
  'Work anniversary',
  'Custom',
  'Graduation',
  'Holiday',
  'Revival',
  'Follow-up'
];
const composerReasons: readonly ComposerReason[] = [
  'Birthday',
  'Check-in',
  'Thanks',
  'Congratulations',
  'Apology',
  'Follow-up',
  'Custom'
];
const locales: readonly SupportedLocale[] = ['en-IN', 'hi-IN', 'en-Hinglish'];
const contactLanguages: readonly ContactEssentialsInput['language'][] = ['English', 'Hinglish', 'Hindi'];
const contactGroups: readonly StandaloneContactInput['group'][] = [
  'Family',
  'Friends',
  'Work',
  'Close friends',
  'Other'
];
const messageChannels: readonly StandaloneContactInput['preferredChannel'][] = ['SMS', 'WhatsApp', 'Email', 'Manual'];
const messageStatuses: readonly MessageDraft['status'][] = [
  'Needs review',
  'Scheduled',
  'Blocked',
  'Sent',
  'Failed',
  'Delivery pending',
  'Delivery unknown',
  'Rejected',
  'Draft'
];
const messageVariants: readonly MessageDraft['selectedVariant'][] = ['short', 'standard', 'warm'];
const checkInStatuses = ['Due', 'Snoozed', 'Current'] as const;
const checkInSnoozeDays = [1, 7, 14, 30] as const;
const permissionCapabilities: readonly SystemPermissionCapability[] = [
  'Contacts',
  'Notifications',
  'Calendar',
  'Biometric lock'
];
const reminderReasons = [
  'manual',
  'hydration',
  'foreground',
  'permission-change',
  'events-committed',
  'settings-committed'
] as const;
const booleanSettingKeys = [
  'aiEnabled',
  'notificationsEnabled',
  'smsEnabled',
  'whatsappHandoffEnabled',
  'emailEnabled'
] as const;
const relationshipGroups: readonly RelationshipGroup[] = ['Family', 'Friends', 'Work', 'Close friends', 'Other'];
const tones: readonly Tone[] = ['Warm', 'Respectful', 'Playful', 'Concise', 'Formal', 'Hinglish', 'No emoji'];
const cadences = [14, 30, 45, 60, 90] as const;
const availableAutomationModes: readonly Exclude<AutomationMode, 'Fully auto'>[] = [
  'Always ask',
  'Smart approve',
  'VIP approve'
];
const memoryCategories: readonly MemoryCategory[] = ['General', 'Private', 'Preference', 'Event', 'Gift', 'Milestone'];
const giftCategories: readonly GiftCategory[] = ['Experience', 'Food', 'Books', 'Wellness', 'Personal', 'Other'];
const giftFeedback = ['Liked', 'Disliked', 'Unknown'] as const;
const enrichmentPromptIds = ['relationship-context', 'message-mention', 'message-avoid', 'language-style'] as const;
const preparationStepIds = [
  'confirm-date',
  'improve-context',
  'write-message',
  'decide-gift',
  'choose-channel',
  'schedule-reminder'
] as const;
const timelineFilters = ['All', 'Events', 'Memories', 'Gifts', 'Messages'] as const;
const inboxTabs = ['All', 'Review', 'Today', 'Scheduled', 'Blocked', 'Failed', 'Sent', 'Rejected'] as const;
const inboxChannels = ['All', ...messageChannels] as const;
const inboxSorts = ['Newest', 'Scheduled', 'Contact', 'Status'] as const;
const messageBulkActions: readonly MessageBulkAction[] = ['Approve', 'Reject', 'Retry', 'Revoke approval'];
const onboardingGoals: readonly OnboardingGoal[] = [
  'Reminders first',
  'AI wishes',
  'Manual relationship manager',
  'Full setup'
];
const onboardingSteps: readonly OnboardingStepId[] = [
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
const activityTypes = ['Message', 'Event', 'Contact', 'Backup', 'Setup', 'AI', 'Gift', 'Memory', 'Analytics'] as const;
const activitySeverities = ['Info', 'Warning', 'Error'] as const;
const activityStatuses = ['Open', 'Resolved', 'Obsolete', 'Completed'] as const;
const activityDates = ['All', 'Today', 'Last 7 days'] as const;
const setupGoals = ['Reminders only', 'AI drafts', 'Manual sends', 'Automation'] as const;

const invalid = (): CommandParseResult => ({
  ok: false,
  error: {
    code: 'invalid-command',
    summary: 'The command is invalid or contains unsupported fields.'
  }
});

const tooLarge = (): CommandParseResult => ({
  ok: false,
  error: {
    code: 'command-too-large',
    summary: 'The command exceeds the supported bounded input size.'
  }
});

const isRecord = (value: unknown): value is Record<string, unknown> => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
};

const hasOnlyKeys = (value: Record<string, unknown>, required: readonly string[], optional: readonly string[] = []) => {
  const keys = Object.keys(value);
  return (
    required.every(key => keys.includes(key)) && keys.every(key => required.includes(key) || optional.includes(key))
  );
};

const boundedString = (
  value: unknown,
  maximum: number,
  options: { allowEmpty?: boolean; trim?: boolean } = {}
): string | undefined => {
  if (typeof value !== 'string' || value.length > maximum) return undefined;
  const normalized = options.trim === false ? value : value.trim();
  if (!options.allowEmpty && normalized.length === 0) return undefined;
  return normalized;
};

const id = (value: unknown) => {
  const candidate = boundedString(value, MAX_ID_LENGTH);
  return candidate && /^[A-Za-z0-9][A-Za-z0-9._:-]*$/.test(candidate) ? candidate : undefined;
};

const operationScope = (value: unknown) => {
  const candidate = boundedString(value, MAX_OPERATION_SCOPE_LENGTH);
  return candidate && /^[A-Za-z0-9][A-Za-z0-9:._-]*$/.test(candidate) ? candidate : undefined;
};

const optionalId = (value: unknown) => (value === undefined ? undefined : id(value));

const optionalBoolean = (value: unknown, fallback = false) =>
  value === undefined ? fallback : typeof value === 'boolean' ? value : undefined;

const exactInteger = (value: unknown, minimum: number, maximum: number) =>
  Number.isSafeInteger(value) && (value as number) >= minimum && (value as number) <= maximum
    ? (value as number)
    : undefined;

const parsePageRequest = (value: Record<string, unknown>): PageRequest | undefined => {
  const cursor = optionalId(value.cursor);
  const limit = value.limit === undefined ? DEFAULT_PAGE_LIMIT : exactInteger(value.limit, 1, MAX_PAGE_LIMIT);
  const includeArchived = optionalBoolean(value.includeArchived);
  if ((value.cursor !== undefined && !cursor) || limit === undefined || includeArchived === undefined) return undefined;
  return { cursor, limit, includeArchived };
};

const parsePrivatePageRequest = (value: Record<string, unknown>) => {
  const cursor = optionalId(value.cursor);
  const limit = value.limit === undefined ? DEFAULT_PAGE_LIMIT : exactInteger(value.limit, 1, MAX_PAGE_LIMIT);
  if ((value.cursor !== undefined && !cursor) || limit === undefined) return undefined;
  return { cursor, limit };
};

const parseOptionalIdList = (value: unknown): string[] | undefined => {
  if (value === undefined) return undefined;
  if (!Array.isArray(value) || value.length > MAX_EXCLUDED_MEMORY_IDS) return undefined;
  const parsed = value.map(id);
  if (parsed.some(item => !item) || new Set(parsed).size !== parsed.length) return undefined;
  return parsed as string[];
};

const parseTime = (value: unknown) => {
  const time = boundedString(value, 5);
  return time && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(time) ? time : undefined;
};

const parseDateKey = (value: unknown) => {
  const date = boundedString(value, 10);
  return date && /^\d{4}-\d{2}-\d{2}$/.test(date) ? date : undefined;
};

const parseContactEssentials = (value: unknown): ContactEssentialsInput | undefined => {
  if (
    !isRecord(value) ||
    !hasOnlyKeys(
      value,
      ['name', 'relationship', 'language', 'notesSummary'],
      ['relationshipSubtype', 'jobTitle', 'phone', 'email']
    )
  ) {
    return undefined;
  }
  const name = boundedString(value.name, 160);
  const relationship = boundedString(value.relationship, 160);
  const relationshipSubtype =
    value.relationshipSubtype === undefined
      ? undefined
      : boundedString(value.relationshipSubtype, 80, { allowEmpty: true, trim: false });
  const jobTitle =
    value.jobTitle === undefined ? undefined : boundedString(value.jobTitle, 120, { allowEmpty: true, trim: false });
  const phone =
    value.phone === undefined ? undefined : boundedString(value.phone, 80, { allowEmpty: true, trim: false });
  const email =
    value.email === undefined ? undefined : boundedString(value.email, 320, { allowEmpty: true, trim: false });
  const notesSummary = boundedString(value.notesSummary, 500, { allowEmpty: true, trim: false });
  if (
    !name ||
    !relationship ||
    (value.relationshipSubtype !== undefined && relationshipSubtype === undefined) ||
    (value.jobTitle !== undefined && jobTitle === undefined) ||
    (value.phone !== undefined && phone === undefined) ||
    (value.email !== undefined && email === undefined) ||
    notesSummary === undefined ||
    !contactLanguages.includes(value.language as ContactEssentialsInput['language'])
  ) {
    return undefined;
  }
  return {
    name,
    relationship,
    relationshipSubtype,
    jobTitle,
    phone,
    email,
    language: value.language as ContactEssentialsInput['language'],
    notesSummary
  };
};

const parseStandaloneContact = (value: unknown): StandaloneContactInput | undefined => {
  if (
    !isRecord(value) ||
    !hasOnlyKeys(
      value,
      ['name', 'relationship', 'group', 'preferredChannel', 'language', 'notesSummary'],
      ['relationshipSubtype', 'jobTitle', 'phone', 'email']
    )
  ) {
    return undefined;
  }
  const essentials = parseContactEssentials({
    name: value.name,
    relationship: value.relationship,
    relationshipSubtype: value.relationshipSubtype,
    jobTitle: value.jobTitle,
    language: value.language,
    notesSummary: value.notesSummary,
    ...(value.phone === undefined ? {} : { phone: value.phone }),
    ...(value.email === undefined ? {} : { email: value.email })
  });
  if (
    !essentials ||
    !contactGroups.includes(value.group as StandaloneContactInput['group']) ||
    !messageChannels.includes(value.preferredChannel as StandaloneContactInput['preferredChannel'])
  ) {
    return undefined;
  }
  return {
    ...essentials,
    group: value.group as StandaloneContactInput['group'],
    preferredChannel: value.preferredChannel as StandaloneContactInput['preferredChannel']
  };
};

const parseEventEditInput = (value: unknown): EventEditInput | undefined => {
  if (!isRecord(value) || !hasOnlyKeys(value, ['contactId', 'eventType', 'label', 'date', 'verified']))
    return undefined;
  const contactId = id(value.contactId);
  const label = boundedString(value.label, MAX_LABEL_LENGTH);
  const date = boundedString(value.date, 10);
  if (
    !contactId ||
    !eventTypes.includes(value.eventType as EventType) ||
    !label ||
    !date ||
    !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
    typeof value.verified !== 'boolean'
  ) {
    return undefined;
  }
  return {
    contactId,
    eventType: value.eventType as EventType,
    label,
    date,
    verified: value.verified
  };
};

const parseImportDecisions = (
  value: unknown
): Extract<HarnessCommand, { type: 'contacts.import-apply' }>['decisions'] | undefined => {
  if (!Array.isArray(value) || value.length > MAX_IMPORT_DECISIONS) return undefined;
  const decisions: Extract<HarnessCommand, { type: 'contacts.import-apply' }>['decisions'] = [];
  const seen = new Set<string>();
  for (const item of value) {
    if (!isRecord(item) || !hasOnlyKeys(item, ['reviewItemId', 'action'], ['candidateContactId', 'conflictingEventId']))
      return undefined;
    const reviewItemId = id(item.reviewItemId);
    if (!reviewItemId || seen.has(reviewItemId)) return undefined;
    seen.add(reviewItemId);
    if (item.action === 'merge') {
      const candidateContactId = id(item.candidateContactId);
      if (!candidateContactId || item.conflictingEventId !== undefined) return undefined;
      decisions.push({ reviewItemId, action: 'merge', candidateContactId });
      continue;
    }
    if (
      (item.action === 'keep-separate' || item.action === 'skip') &&
      item.candidateContactId === undefined &&
      item.conflictingEventId === undefined
    ) {
      decisions.push({ reviewItemId, action: item.action });
      continue;
    }
    if (item.action === 'keep-existing' || item.action === 'import-as-separate') {
      const candidateContactId = optionalId(item.candidateContactId);
      if ((item.candidateContactId !== undefined && !candidateContactId) || item.conflictingEventId !== undefined) {
        return undefined;
      }
      decisions.push({ reviewItemId, action: item.action, candidateContactId });
      continue;
    }
    if (item.action === 'replace') {
      const candidateContactId = optionalId(item.candidateContactId);
      const conflictingEventId = id(item.conflictingEventId);
      if ((item.candidateContactId !== undefined && !candidateContactId) || !conflictingEventId) return undefined;
      decisions.push({ reviewItemId, action: 'replace', candidateContactId, conflictingEventId });
      continue;
    }
    return undefined;
  }
  return decisions;
};

const parseCalendarImportDecisions = (
  value: unknown
): Extract<HarnessCommand, { type: 'calendar.import-apply' }>['decisions'] | undefined => {
  if (!Array.isArray(value) || value.length > MAX_IMPORT_DECISIONS) return undefined;
  const decisions: Extract<HarnessCommand, { type: 'calendar.import-apply' }>['decisions'] = [];
  const seen = new Set<string>();
  for (const item of value) {
    if (!isRecord(item) || typeof item.action !== 'string') return undefined;
    const reviewId = id(item.reviewId);
    if (!reviewId || seen.has(reviewId)) return undefined;
    seen.add(reviewId);
    if (item.action === 'apply' || item.action === 'skip') {
      if (!hasOnlyKeys(item, ['reviewId', 'action'])) return undefined;
      decisions.push({ reviewId, action: item.action });
      continue;
    }
    if (item.action === 'edit') {
      if (!hasOnlyKeys(item, ['reviewId', 'action', 'title', 'date'], ['notes'])) return undefined;
      const title = boundedString(item.title, 160);
      const date = boundedString(item.date, 128);
      const notes =
        item.notes === undefined ? undefined : boundedString(item.notes, 2_000, { allowEmpty: true, trim: false });
      if (!title || !date || (item.notes !== undefined && notes === undefined)) return undefined;
      decisions.push({ reviewId, action: 'edit', title, date, notes });
      continue;
    }
    if (item.action === 'create-separate') {
      if (!hasOnlyKeys(item, ['reviewId', 'action'])) return undefined;
      decisions.push({ reviewId, action: 'create-separate' });
      continue;
    }
    if (item.action === 'merge-contact') {
      if (!hasOnlyKeys(item, ['reviewId', 'action', 'candidateContactId'])) return undefined;
      const candidateContactId = id(item.candidateContactId);
      if (!candidateContactId) return undefined;
      decisions.push({ reviewId, action: 'merge-contact', candidateContactId });
      continue;
    }
    if (item.action === 'merge-event') {
      if (!hasOnlyKeys(item, ['reviewId', 'action', 'candidateEventId'])) return undefined;
      const candidateEventId = id(item.candidateEventId);
      if (!candidateEventId) return undefined;
      decisions.push({ reviewId, action: 'merge-event', candidateEventId });
      continue;
    }
    return undefined;
  }
  return decisions;
};

const parseDomainAction = (value: unknown): HarnessDomainAction | undefined => {
  if (!isRecord(value) || typeof value.type !== 'string') return undefined;

  if (value.type === 'navigate') {
    if (!hasOnlyKeys(value, ['type', 'screen'], ['contactId', 'eventId', 'messageId'])) return undefined;
    if (!screens.includes(value.screen as Screen)) return undefined;
    const contactId = optionalId(value.contactId);
    const eventId = optionalId(value.eventId);
    const messageId = optionalId(value.messageId);
    if (
      (value.contactId !== undefined && !contactId) ||
      (value.eventId !== undefined && !eventId) ||
      (value.messageId !== undefined && !messageId)
    )
      return undefined;
    return { type: 'navigate', screen: value.screen as Screen, contactId, eventId, messageId };
  }

  if (value.type === 'setSearch') {
    if (!hasOnlyKeys(value, ['type', 'query'])) return undefined;
    const query = boundedString(value.query, MAX_SEARCH_LENGTH, { allowEmpty: true, trim: false });
    return query === undefined ? undefined : { type: 'setSearch', query };
  }

  if (value.type === 'toggleChecklist') {
    if (!hasOnlyKeys(value, ['type', 'eventId', 'itemId'])) return undefined;
    const eventId = id(value.eventId);
    const itemId = id(value.itemId);
    return eventId && itemId ? { type: 'toggleChecklist', eventId, itemId } : undefined;
  }

  if (value.type === 'addManualEvent') {
    if (
      !hasOnlyKeys(value, ['type', 'eventType', 'label', 'date'], ['contactId', 'newContactName', 'confirmConflict'])
    ) {
      return undefined;
    }
    if (!eventTypes.includes(value.eventType as EventType)) return undefined;
    const contactId = optionalId(value.contactId);
    const newContactName = value.newContactName === undefined ? undefined : boundedString(value.newContactName, 160);
    const label = boundedString(value.label, MAX_LABEL_LENGTH);
    const date = boundedString(value.date, 80);
    const confirmConflict = optionalBoolean(value.confirmConflict);
    if (
      (value.contactId !== undefined && !contactId) ||
      (value.newContactName !== undefined && !newContactName) ||
      !label ||
      !date ||
      confirmConflict === undefined
    )
      return undefined;
    return {
      type: 'addManualEvent',
      eventType: value.eventType as EventType,
      label,
      date,
      contactId,
      newContactName,
      confirmConflict
    };
  }

  if (value.type === 'editMessage') {
    if (!hasOnlyKeys(value, ['type', 'messageId', 'body'])) return undefined;
    const messageId = id(value.messageId);
    const body = boundedString(value.body, MAX_MESSAGE_BODY_LENGTH, { allowEmpty: true, trim: false });
    return messageId && body !== undefined ? { type: 'editMessage', messageId, body } : undefined;
  }

  if (value.type === 'approveMessage' || value.type === 'rejectMessage') {
    if (!hasOnlyKeys(value, ['type', 'messageId'], ['reviewNext'])) return undefined;
    const messageId = id(value.messageId);
    const reviewNext = optionalBoolean(value.reviewNext);
    if (!messageId || reviewNext === undefined) return undefined;
    return value.type === 'approveMessage'
      ? { type: 'approveMessage', messageId, reviewNext }
      : { type: 'rejectMessage', messageId, reviewNext };
  }

  if (value.type === 'revokeMessage') {
    if (!hasOnlyKeys(value, ['type', 'messageId'])) return undefined;
    const messageId = id(value.messageId);
    return messageId ? { type: 'revokeMessage', messageId } : undefined;
  }

  if (value.type === 'setLocale') {
    if (!hasOnlyKeys(value, ['type', 'locale']) || !locales.includes(value.locale as SupportedLocale)) return undefined;
    return { type: 'setLocale', locale: value.locale as SupportedLocale };
  }

  if (value.type === 'toggleSetting') {
    if (!hasOnlyKeys(value, ['type', 'key']) || !booleanSettingKeys.includes(value.key as never)) return undefined;
    return { type: 'toggleSetting', key: value.key as (typeof booleanSettingKeys)[number] };
  }

  if (value.type === 'setQuietHours') {
    if (!hasOnlyKeys(value, ['type', 'start', 'end'])) return undefined;
    const start = boundedString(value.start, 5);
    const end = boundedString(value.end, 5);
    const timePattern = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
    return start && end && timePattern.test(start) && timePattern.test(end)
      ? { type: 'setQuietHours', start, end }
      : undefined;
  }

  if (value.type === 'setDefaultSendTime') {
    if (!hasOnlyKeys(value, ['type', 'time'])) return undefined;
    const time = boundedString(value.time, 5);
    return time && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(time) ? { type: 'setDefaultSendTime', time } : undefined;
  }

  if (value.type === 'addBlackout') {
    if (!hasOnlyKeys(value, ['type', 'label', 'startDate', 'endDate'], ['behavior', 'channels'])) return undefined;
    const label = boundedString(value.label, 80);
    const startDate = boundedString(value.startDate, 10);
    const endDate = boundedString(value.endDate, 10);
    const behavior = value.behavior;
    const channels = value.channels;
    if (
      !label ||
      !startDate ||
      !endDate ||
      !/^\d{4}-\d{2}-\d{2}$/.test(startDate) ||
      !/^\d{4}-\d{2}-\d{2}$/.test(endDate) ||
      (behavior !== undefined && behavior !== 'Block' && behavior !== 'Defer') ||
      (channels !== undefined &&
        (!Array.isArray(channels) ||
          channels.length === 0 ||
          channels.length > messageChannels.length ||
          new Set(channels).size !== channels.length ||
          channels.some(channel => !messageChannels.includes(channel as StandaloneContactInput['preferredChannel']))))
    ) {
      return undefined;
    }
    return {
      type: 'addBlackout',
      label,
      startDate,
      endDate,
      behavior: behavior as 'Block' | 'Defer' | undefined,
      channels: channels as StandaloneContactInput['preferredChannel'][] | undefined
    };
  }

  if (value.type === 'removeBlackout') {
    if (!hasOnlyKeys(value, ['type', 'blackoutId'])) return undefined;
    const blackoutId = id(value.blackoutId);
    return blackoutId ? { type: 'removeBlackout', blackoutId } : undefined;
  }

  return undefined;
};

const parseCommandRecord = (value: Record<string, unknown>): HarnessCommand | undefined => {
  if (typeof value.type !== 'string') return undefined;

  if (value.type === 'system.catalog') {
    return hasOnlyKeys(value, ['type']) ? { type: 'system.catalog' } : undefined;
  }

  if (value.type === 'domain.dispatch') {
    if (!hasOnlyKeys(value, ['type', 'action'])) return undefined;
    const action = parseDomainAction(value.action);
    return action ? { type: 'domain.dispatch', action } : undefined;
  }

  if (value.type === 'contacts.query' || value.type === 'events.query' || value.type === 'messages.query') {
    const optionalKeys =
      value.type === 'contacts.query'
        ? [
            'cursor',
            'limit',
            'includeArchived',
            'query',
            'group',
            'vip',
            'missingEvent',
            'missingChannel',
            'lowHealth',
            'needsPersonalization',
            'sort'
          ]
        : value.type === 'events.query'
          ? ['cursor', 'limit', 'includeArchived', 'eventType', 'query', 'month', 'sort']
          : ['cursor', 'limit', 'includeArchived', 'status', 'tab', 'channel', 'query', 'sort'];
    if (!hasOnlyKeys(value, ['type'], optionalKeys)) return undefined;
    const page = parsePageRequest(value);
    if (!page) return undefined;
    if (value.type === 'events.query') {
      if (value.eventType !== undefined && !eventTypes.includes(value.eventType as EventType)) return undefined;
      const query =
        value.query === undefined
          ? undefined
          : boundedString(value.query, MAX_SEARCH_LENGTH, { allowEmpty: true, trim: false });
      const month = value.month === undefined ? undefined : boundedString(value.month, 7);
      const sort = value.sort ?? 'Date';
      if (
        (value.query !== undefined && query === undefined) ||
        (value.month !== undefined && (!month || !/^\d{4}-(?:0[1-9]|1[0-2])$/.test(month))) ||
        !['Date', 'Contact', 'Type'].includes(sort as string)
      ) {
        return undefined;
      }
      return {
        type: 'events.query',
        ...page,
        eventType: value.eventType as EventType | undefined,
        query,
        month,
        sort: sort as 'Date' | 'Contact' | 'Type'
      };
    }
    if (value.type === 'messages.query') {
      if (value.status !== undefined && !messageStatuses.includes(value.status as MessageDraft['status']))
        return undefined;
      const tab = value.tab ?? 'All';
      const channel = value.channel ?? 'All';
      const sort = value.sort ?? 'Newest';
      const query =
        value.query === undefined
          ? ''
          : boundedString(value.query, MAX_SEARCH_LENGTH, { allowEmpty: true, trim: false });
      if (
        query === undefined ||
        !inboxTabs.includes(tab as never) ||
        !inboxChannels.includes(channel as never) ||
        !inboxSorts.includes(sort as never)
      ) {
        return undefined;
      }
      return {
        type: 'messages.query',
        ...page,
        status: value.status as MessageDraft['status'] | undefined,
        tab: tab as (typeof inboxTabs)[number],
        channel: channel as (typeof inboxChannels)[number],
        query,
        sort: sort as (typeof inboxSorts)[number]
      };
    }
    const query =
      value.query === undefined
        ? undefined
        : boundedString(value.query, MAX_SEARCH_LENGTH, { allowEmpty: true, trim: false });
    const sort = value.sort ?? 'Name';
    const optionalFlags = [
      value.vip,
      value.missingEvent,
      value.missingChannel,
      value.lowHealth,
      value.needsPersonalization
    ];
    if (
      (value.query !== undefined && query === undefined) ||
      (value.group !== undefined && !relationshipGroups.includes(value.group as RelationshipGroup)) ||
      optionalFlags.some(flag => flag !== undefined && typeof flag !== 'boolean') ||
      !['Name', 'Health', 'Upcoming event'].includes(sort as string)
    ) {
      return undefined;
    }
    return {
      type: 'contacts.query',
      ...page,
      query,
      group: value.group as RelationshipGroup | undefined,
      vip: value.vip as boolean | undefined,
      missingEvent: value.missingEvent as boolean | undefined,
      missingChannel: value.missingChannel as boolean | undefined,
      lowHealth: value.lowHealth as boolean | undefined,
      needsPersonalization: value.needsPersonalization as boolean | undefined,
      sort: sort as 'Name' | 'Health' | 'Upcoming event'
    };
  }

  if (value.type === 'checkins.query') {
    if (!hasOnlyKeys(value, ['type'], ['cursor', 'limit', 'includeArchived', 'status'])) return undefined;
    const page = parsePageRequest(value);
    if (!page || (value.status !== undefined && !checkInStatuses.includes(value.status as never))) return undefined;
    return { type: 'checkins.query', ...page, status: value.status as (typeof checkInStatuses)[number] | undefined };
  }

  if (
    value.type === 'contacts.inspect' ||
    value.type === 'contacts.preferences.inspect' ||
    value.type === 'contacts.preferences.use-group-defaults' ||
    value.type === 'contacts.enrichment.inspect'
  ) {
    if (!hasOnlyKeys(value, ['type', 'contactId'])) return undefined;
    const contactId = id(value.contactId);
    return contactId ? ({ type: value.type, contactId } as HarnessCommand) : undefined;
  }

  if (value.type === 'contacts.preferences.set-tone') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'tone', 'enabled']) || typeof value.enabled !== 'boolean') {
      return undefined;
    }
    const contactId = id(value.contactId);
    return contactId && tones.includes(value.tone as Tone)
      ? { type: value.type, contactId, tone: value.tone as Tone, enabled: value.enabled }
      : undefined;
  }

  if (value.type === 'contacts.preferences.set-group') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'group'])) return undefined;
    const contactId = id(value.contactId);
    return contactId && relationshipGroups.includes(value.group as RelationshipGroup)
      ? { type: value.type, contactId, group: value.group as RelationshipGroup }
      : undefined;
  }

  if (value.type === 'contacts.preferences.set-channel') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'channel'])) return undefined;
    const contactId = id(value.contactId);
    return contactId && messageChannels.includes(value.channel as never)
      ? { type: value.type, contactId, channel: value.channel as MessageDraft['channel'] }
      : undefined;
  }

  if (value.type === 'contacts.preferences.set-vip' || value.type === 'contacts.preferences.set-dnd') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'enabled']) || typeof value.enabled !== 'boolean') return undefined;
    const contactId = id(value.contactId);
    return contactId ? { type: value.type, contactId, enabled: value.enabled } : undefined;
  }

  if (value.type === 'contacts.preferences.set-cadence') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'days'])) return undefined;
    const contactId = id(value.contactId);
    return contactId && cadences.includes(value.days as never)
      ? { type: value.type, contactId, days: value.days as (typeof cadences)[number] }
      : undefined;
  }

  if (value.type === 'contacts.preferences.set-automation') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'mode'])) return undefined;
    const contactId = id(value.contactId);
    return contactId && availableAutomationModes.includes(value.mode as never)
      ? {
          type: value.type,
          contactId,
          mode: value.mode as (typeof availableAutomationModes)[number]
        }
      : undefined;
  }

  if (value.type === 'contacts.preferences.set-send-time') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'time'])) return undefined;
    const contactId = id(value.contactId);
    const time = value.time === null ? null : parseTime(value.time);
    return contactId && time !== undefined ? { type: value.type, contactId, time } : undefined;
  }

  if (value.type === 'contacts.preferences.set-quiet-hours') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'behavior'])) return undefined;
    const contactId = id(value.contactId);
    return contactId && (value.behavior === 'Defer' || value.behavior === 'Block')
      ? { type: value.type, contactId, behavior: value.behavior }
      : undefined;
  }

  if (value.type === 'contacts.preferences.set-skip-auto') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'enabled']) || typeof value.enabled !== 'boolean') {
      return undefined;
    }
    const contactId = id(value.contactId);
    return contactId ? { type: value.type, contactId, enabled: value.enabled } : undefined;
  }

  if (value.type === 'groups.inspect') {
    return hasOnlyKeys(value, ['type']) ? { type: value.type } : undefined;
  }

  if (value.type === 'groups.set-default') {
    if (!hasOnlyKeys(value, ['type', 'group', 'defaults']) || !isRecord(value.defaults)) return undefined;
    if (
      !relationshipGroups.includes(value.group as RelationshipGroup) ||
      !hasOnlyKeys(value.defaults, [], ['preferredChannel', 'tone', 'checkInCadenceDays', 'automationMode']) ||
      Object.keys(value.defaults).length === 0
    ) {
      return undefined;
    }
    const preferredChannel = value.defaults.preferredChannel;
    const tone = value.defaults.tone;
    const checkInCadenceDays = value.defaults.checkInCadenceDays;
    const automationMode = value.defaults.automationMode;
    if (
      (preferredChannel !== undefined && !messageChannels.includes(preferredChannel as never)) ||
      (tone !== undefined &&
        (!Array.isArray(tone) ||
          tone.length === 0 ||
          tone.length > tones.length ||
          new Set(tone).size !== tone.length ||
          tone.some(item => !tones.includes(item as Tone)))) ||
      (checkInCadenceDays !== undefined && !cadences.includes(checkInCadenceDays as never)) ||
      (automationMode !== undefined && !availableAutomationModes.includes(automationMode as never))
    ) {
      return undefined;
    }
    return {
      type: value.type,
      group: value.group as RelationshipGroup,
      defaults: {
        preferredChannel: preferredChannel as MessageDraft['channel'] | undefined,
        tone: tone as Tone[] | undefined,
        checkInCadenceDays: checkInCadenceDays as (typeof cadences)[number] | undefined,
        automationMode: automationMode as (typeof availableAutomationModes)[number] | undefined
      }
    };
  }

  if (value.type === 'contacts.enrichment.answer') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'promptId', 'body'])) return undefined;
    const contactId = id(value.contactId);
    const body = boundedString(value.body, 500, { trim: false });
    return contactId && body && enrichmentPromptIds.includes(value.promptId as never)
      ? {
          type: value.type,
          contactId,
          promptId: value.promptId as (typeof enrichmentPromptIds)[number],
          body
        }
      : undefined;
  }

  if (value.type === 'contacts.import-apply') {
    if (!hasOnlyKeys(value, ['type', 'sessionToken', 'decisions'])) return undefined;
    const sessionToken = id(value.sessionToken);
    const decisions = parseImportDecisions(value.decisions);
    return sessionToken && decisions ? { type: value.type, sessionToken, decisions } : undefined;
  }

  if (value.type === 'calendar.import-apply') {
    if (!hasOnlyKeys(value, ['type', 'sessionToken', 'decisions'])) return undefined;
    const sessionToken = id(value.sessionToken);
    const decisions = parseCalendarImportDecisions(value.decisions);
    return sessionToken && decisions ? { type: value.type, sessionToken, decisions } : undefined;
  }

  if (value.type === 'contacts.add') {
    if (!hasOnlyKeys(value, ['type', 'input'])) return undefined;
    const input = parseStandaloneContact(value.input);
    return input ? { type: 'contacts.add', input } : undefined;
  }

  if (value.type === 'contacts.edit-preview' || value.type === 'contacts.edit-apply') {
    if (
      !hasOnlyKeys(
        value,
        ['type', 'contactId', 'input'],
        value.type === 'contacts.edit-apply' ? ['confirmationToken'] : []
      )
    ) {
      return undefined;
    }
    if (value.type === 'contacts.edit-apply' && !Object.hasOwn(value, 'confirmationToken')) return undefined;
    const contactId = id(value.contactId);
    const input = parseContactEssentials(value.input);
    if (!contactId || !input) return undefined;
    if (value.type === 'contacts.edit-preview') return { type: value.type, contactId, input };
    const confirmationToken = id(value.confirmationToken);
    return confirmationToken ? { type: value.type, contactId, input, confirmationToken } : undefined;
  }

  if (
    value.type === 'contacts.archive-preview' ||
    value.type === 'contacts.delete-preview' ||
    value.type === 'contacts.restore'
  ) {
    if (!hasOnlyKeys(value, ['type', 'contactId'])) return undefined;
    const contactId = id(value.contactId);
    return contactId ? ({ type: value.type, contactId } as HarnessCommand) : undefined;
  }

  if (value.type === 'contacts.archive-apply' || value.type === 'contacts.delete-apply') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'confirmationToken'])) return undefined;
    const contactId = id(value.contactId);
    const confirmationToken = id(value.confirmationToken);
    return contactId && confirmationToken ? { type: value.type, contactId, confirmationToken } : undefined;
  }

  if (value.type === 'contacts.merge-preview' || value.type === 'contacts.merge-apply') {
    if (
      !hasOnlyKeys(value, [
        'type',
        'survivorContactId',
        'mergedContactId',
        ...(value.type === 'contacts.merge-apply' ? ['confirmationToken'] : [])
      ])
    )
      return undefined;
    const survivorContactId = id(value.survivorContactId);
    const mergedContactId = id(value.mergedContactId);
    if (!survivorContactId || !mergedContactId) return undefined;
    if (value.type === 'contacts.merge-preview') return { type: value.type, survivorContactId, mergedContactId };
    const confirmationToken = id(value.confirmationToken);
    return confirmationToken ? { type: value.type, survivorContactId, mergedContactId, confirmationToken } : undefined;
  }

  if (value.type === 'events.add') {
    if (
      !hasOnlyKeys(value, ['type', 'eventType', 'label', 'date'], ['contactId', 'newContactName', 'confirmConflict'])
    ) {
      return undefined;
    }
    const contactId = optionalId(value.contactId);
    const newContactName = value.newContactName === undefined ? undefined : boundedString(value.newContactName, 160);
    const label = boundedString(value.label, MAX_LABEL_LENGTH);
    const date = boundedString(value.date, 10);
    const confirmConflict = optionalBoolean(value.confirmConflict);
    const hasExactlyOneContact = Boolean(contactId) !== Boolean(newContactName);
    if (
      !hasExactlyOneContact ||
      !eventTypes.includes(value.eventType as EventType) ||
      !label ||
      !date ||
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      confirmConflict === undefined
    )
      return undefined;
    return {
      type: 'events.add',
      contactId,
      newContactName,
      eventType: value.eventType as EventType,
      label,
      date,
      confirmConflict
    };
  }

  if (value.type === 'events.preparation.inspect') {
    if (!hasOnlyKeys(value, ['type', 'eventId'])) return undefined;
    const eventId = id(value.eventId);
    return eventId ? { type: value.type, eventId } : undefined;
  }

  if (value.type === 'events.preparation.toggle') {
    if (!hasOnlyKeys(value, ['type', 'eventId', 'stepId'])) return undefined;
    const eventId = id(value.eventId);
    return eventId && preparationStepIds.includes(value.stepId as never)
      ? { type: value.type, eventId, stepId: value.stepId as (typeof preparationStepIds)[number] }
      : undefined;
  }

  if (value.type === 'events.edit-preview' || value.type === 'events.edit-apply') {
    if (
      !hasOnlyKeys(value, [
        'type',
        'eventId',
        'input',
        ...(value.type === 'events.edit-apply' ? ['confirmationToken'] : [])
      ])
    ) {
      return undefined;
    }
    const eventId = id(value.eventId);
    const input = parseEventEditInput(value.input);
    if (!eventId || !input) return undefined;
    if (value.type === 'events.edit-preview') return { type: value.type, eventId, input };
    const confirmationToken = id(value.confirmationToken);
    return confirmationToken ? { type: value.type, eventId, input, confirmationToken } : undefined;
  }

  if (value.type === 'events.delete-preview' || value.type === 'events.delete-apply') {
    if (
      !hasOnlyKeys(value, ['type', 'eventId', ...(value.type === 'events.delete-apply' ? ['confirmationToken'] : [])])
    ) {
      return undefined;
    }
    const eventId = id(value.eventId);
    if (!eventId) return undefined;
    if (value.type === 'events.delete-preview') return { type: value.type, eventId };
    const confirmationToken = id(value.confirmationToken);
    return confirmationToken ? { type: value.type, eventId, confirmationToken } : undefined;
  }

  if (value.type === 'events.merge-preview' || value.type === 'events.merge-apply') {
    if (
      !hasOnlyKeys(value, [
        'type',
        'survivorEventId',
        'mergedEventId',
        ...(value.type === 'events.merge-apply' ? ['confirmationToken'] : [])
      ])
    )
      return undefined;
    const survivorEventId = id(value.survivorEventId);
    const mergedEventId = id(value.mergedEventId);
    if (!survivorEventId || !mergedEventId) return undefined;
    if (value.type === 'events.merge-preview') return { type: value.type, survivorEventId, mergedEventId };
    const confirmationToken = id(value.confirmationToken);
    return confirmationToken ? { type: value.type, survivorEventId, mergedEventId, confirmationToken } : undefined;
  }

  if (value.type === 'checkins.snooze') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'days'])) return undefined;
    const contactId = id(value.contactId);
    return contactId && checkInSnoozeDays.includes(value.days as never)
      ? { type: value.type, contactId, days: value.days as (typeof checkInSnoozeDays)[number] }
      : undefined;
  }

  if (value.type === 'checkins.mark-contacted') {
    if (!hasOnlyKeys(value, ['type', 'contactId'])) return undefined;
    const contactId = id(value.contactId);
    return contactId ? { type: value.type, contactId } : undefined;
  }

  if (value.type === 'composer.inspect' || value.type === 'composer.create-template') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'reason'], ['draftBody', 'body', 'templateId'])) return undefined;
    if (value.type === 'composer.inspect' && Object.hasOwn(value, 'body')) return undefined;
    if (value.type === 'composer.create-template' && Object.hasOwn(value, 'draftBody')) return undefined;
    const contactId = id(value.contactId);
    const templateId = optionalId(value.templateId);
    const bodyKey = value.type === 'composer.inspect' ? 'draftBody' : 'body';
    const bodyValue = value[bodyKey];
    const body =
      bodyValue === undefined
        ? undefined
        : boundedString(bodyValue, MAX_MESSAGE_BODY_LENGTH, { allowEmpty: true, trim: false });
    if (
      !contactId ||
      !composerReasons.includes(value.reason as ComposerReason) ||
      (value.templateId !== undefined && !templateId) ||
      (bodyValue !== undefined && body === undefined)
    )
      return undefined;
    return value.type === 'composer.inspect'
      ? { type: value.type, contactId, reason: value.reason as ComposerReason, draftBody: body, templateId }
      : { type: value.type, contactId, reason: value.reason as ComposerReason, body, templateId };
  }

  if (value.type === 'messages.edit') {
    if (!hasOnlyKeys(value, ['type', 'messageId', 'body'])) return undefined;
    const messageId = id(value.messageId);
    const body = boundedString(value.body, MAX_MESSAGE_BODY_LENGTH, { allowEmpty: true, trim: false });
    return messageId && body !== undefined ? { type: value.type, messageId, body } : undefined;
  }

  if (value.type === 'messages.set-channel') {
    if (!hasOnlyKeys(value, ['type', 'messageId', 'channel'])) return undefined;
    const messageId = id(value.messageId);
    return messageId && messageChannels.includes(value.channel as never)
      ? { type: value.type, messageId, channel: value.channel as MessageDraft['channel'] }
      : undefined;
  }

  if (value.type === 'messages.select-variant') {
    if (!hasOnlyKeys(value, ['type', 'messageId', 'variant'], ['discardEditedBody'])) return undefined;
    const messageId = id(value.messageId);
    const discardEditedBody = optionalBoolean(value.discardEditedBody);
    return messageId && messageVariants.includes(value.variant as never) && discardEditedBody !== undefined
      ? {
          type: value.type,
          messageId,
          variant: value.variant as MessageDraft['selectedVariant'],
          discardEditedBody
        }
      : undefined;
  }

  if (value.type === 'messages.acknowledge-duplicate' || value.type === 'messages.revoke') {
    if (!hasOnlyKeys(value, ['type', 'messageId'])) return undefined;
    const messageId = id(value.messageId);
    return messageId ? ({ type: value.type, messageId } as HarnessCommand) : undefined;
  }

  if (value.type === 'messages.bulk-preview') {
    if (!hasOnlyKeys(value, ['type', 'action', 'messageIds'])) return undefined;
    if (
      !messageBulkActions.includes(value.action as MessageBulkAction) ||
      !Array.isArray(value.messageIds) ||
      value.messageIds.length === 0 ||
      value.messageIds.length > MAX_BULK_MESSAGE_IDS
    ) {
      return undefined;
    }
    const messageIds = value.messageIds.map(id);
    if (messageIds.some(messageId => !messageId) || new Set(messageIds).size !== messageIds.length) return undefined;
    return {
      type: value.type,
      action: value.action as MessageBulkAction,
      messageIds: messageIds as string[]
    };
  }

  if (value.type === 'messages.bulk-apply') {
    if (!hasOnlyKeys(value, ['type', 'confirmationToken'])) return undefined;
    const confirmationToken = id(value.confirmationToken);
    return confirmationToken ? { type: value.type, confirmationToken } : undefined;
  }

  if (value.type === 'messages.approve' || value.type === 'messages.reject') {
    if (!hasOnlyKeys(value, ['type', 'messageId'], ['reviewNext'])) return undefined;
    const messageId = id(value.messageId);
    const reviewNext = optionalBoolean(value.reviewNext);
    return messageId && reviewNext !== undefined ? { type: value.type, messageId, reviewNext } : undefined;
  }

  if (value.type === 'messages.schedule-follow-up') {
    if (!hasOnlyKeys(value, ['type', 'messageId', 'delayDays'])) return undefined;
    const messageId = id(value.messageId);
    return messageId && (value.delayDays === 1 || value.delayDays === 7)
      ? { type: value.type, messageId, delayDays: value.delayDays }
      : undefined;
  }

  if (value.type === 'messages.preview') {
    if (!hasOnlyKeys(value, ['type', 'messageId'], ['excludedMemoryIds', 'includePriorMessages'])) return undefined;
    const messageId = id(value.messageId);
    const excludedMemoryIds = parseOptionalIdList(value.excludedMemoryIds);
    const includePriorMessages = optionalBoolean(value.includePriorMessages, true);
    if (
      !messageId ||
      (value.excludedMemoryIds !== undefined && excludedMemoryIds === undefined) ||
      includePriorMessages === undefined
    ) {
      return undefined;
    }
    return { type: value.type, messageId, excludedMemoryIds, includePriorMessages };
  }

  if (value.type === 'messages.regenerate') {
    if (
      !hasOnlyKeys(
        value,
        ['type', 'messageId', 'instructions'],
        ['customInstruction', 'excludedMemoryIds', 'includePriorMessages']
      ) ||
      !Array.isArray(value.instructions) ||
      value.instructions.length > MAX_REGENERATION_INSTRUCTIONS
    ) {
      return undefined;
    }
    const messageId = id(value.messageId);
    const instructions = value.instructions.map(item => boundedString(item, MAX_INSTRUCTION_LENGTH));
    const customInstruction =
      value.customInstruction === undefined
        ? undefined
        : boundedString(value.customInstruction, 240, { allowEmpty: true, trim: false });
    const excludedMemoryIds = parseOptionalIdList(value.excludedMemoryIds);
    const includePriorMessages = optionalBoolean(value.includePriorMessages, true);
    if (
      !messageId ||
      instructions.some(item => !item) ||
      new Set(instructions).size !== instructions.length ||
      (value.customInstruction !== undefined && customInstruction === undefined) ||
      (value.excludedMemoryIds !== undefined && excludedMemoryIds === undefined) ||
      includePriorMessages === undefined ||
      (instructions.length === 0 && !customInstruction?.trim())
    ) {
      return undefined;
    }
    return {
      type: value.type,
      messageId,
      instructions: instructions as string[],
      customInstruction: customInstruction?.trim() || undefined,
      excludedMemoryIds,
      includePriorMessages
    };
  }

  if (value.type === 'messages.test-route' || value.type === 'messages.retry') {
    if (!hasOnlyKeys(value, ['type', 'messageId'])) return undefined;
    const messageId = id(value.messageId);
    return messageId ? { type: value.type, messageId } : undefined;
  }

  if (value.type === 'templates.inspect') {
    if (
      !hasOnlyKeys(value, ['type', 'reason'], ['contactId', 'tone', 'templateId', 'draftBody']) ||
      !composerReasons.includes(value.reason as ComposerReason)
    ) {
      return undefined;
    }
    const contactId = optionalId(value.contactId);
    const templateId = optionalId(value.templateId);
    const draftBody =
      value.draftBody === undefined
        ? undefined
        : boundedString(value.draftBody, MAX_MESSAGE_BODY_LENGTH, { allowEmpty: true, trim: false });
    if (
      (value.contactId !== undefined && !contactId) ||
      (value.templateId !== undefined && !templateId) ||
      (value.tone !== undefined && !tones.includes(value.tone as Tone)) ||
      (value.draftBody !== undefined && draftBody === undefined)
    ) {
      return undefined;
    }
    return {
      type: value.type,
      contactId,
      reason: value.reason as ComposerReason,
      tone: value.tone as Tone | undefined,
      templateId,
      draftBody
    };
  }

  if (value.type === 'memories.query') {
    if (!hasOnlyKeys(value, ['type', 'contactId'], ['query', 'cursor', 'limit'])) return undefined;
    const page = parsePrivatePageRequest(value);
    const contactId = id(value.contactId);
    const query =
      value.query === undefined ? '' : boundedString(value.query, MAX_SEARCH_LENGTH, { allowEmpty: true, trim: false });
    return page && contactId && query !== undefined ? { type: value.type, contactId, query, ...page } : undefined;
  }

  if (value.type === 'memories.add') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'category', 'body'])) return undefined;
    const contactId = id(value.contactId);
    const body = boundedString(value.body, 500, { trim: false });
    return contactId && body && memoryCategories.includes(value.category as MemoryCategory)
      ? { type: value.type, contactId, category: value.category as MemoryCategory, body }
      : undefined;
  }

  if (value.type === 'memories.edit') {
    if (!hasOnlyKeys(value, ['type', 'memoryId', 'category', 'body'])) return undefined;
    const memoryId = id(value.memoryId);
    const body = boundedString(value.body, 500, { trim: false });
    return memoryId && body && memoryCategories.includes(value.category as MemoryCategory)
      ? { type: value.type, memoryId, category: value.category as MemoryCategory, body }
      : undefined;
  }

  if (value.type === 'memories.set-pinned') {
    if (!hasOnlyKeys(value, ['type', 'memoryId', 'pinned']) || typeof value.pinned !== 'boolean') return undefined;
    const memoryId = id(value.memoryId);
    return memoryId ? { type: value.type, memoryId, pinned: value.pinned } : undefined;
  }

  if (value.type === 'memories.delete') {
    if (!hasOnlyKeys(value, ['type', 'memoryId', 'confirmation']) || value.confirmation !== 'DELETE MEMORY') {
      return undefined;
    }
    const memoryId = id(value.memoryId);
    return memoryId ? { type: value.type, memoryId, confirmation: 'DELETE MEMORY' } : undefined;
  }

  if (value.type === 'timeline.query') {
    if (!hasOnlyKeys(value, ['type', 'contactId'], ['filter', 'cursor', 'limit'])) return undefined;
    const page = parsePrivatePageRequest(value);
    const contactId = id(value.contactId);
    const filter = value.filter ?? 'All';
    return page && contactId && timelineFilters.includes(filter as never)
      ? { type: value.type, contactId, filter: filter as (typeof timelineFilters)[number], ...page }
      : undefined;
  }

  if (value.type === 'chat.query') {
    if (!hasOnlyKeys(value, ['type', 'contactId'], ['query', 'channel', 'cursor', 'limit'])) return undefined;
    const page = parsePrivatePageRequest(value);
    const contactId = id(value.contactId);
    const query =
      value.query === undefined ? '' : boundedString(value.query, MAX_SEARCH_LENGTH, { allowEmpty: true, trim: false });
    const channel = value.channel ?? 'All';
    return page && contactId && query !== undefined && inboxChannels.includes(channel as never)
      ? { type: value.type, contactId, query, channel: channel as (typeof inboxChannels)[number], ...page }
      : undefined;
  }

  if (value.type === 'gifts.inspect') {
    if (!hasOnlyKeys(value, ['type', 'contactId'], ['occasion', 'cursor', 'limit'])) return undefined;
    const page = parsePrivatePageRequest(value);
    const contactId = id(value.contactId);
    const occasion = value.occasion === undefined ? 'Next event' : boundedString(value.occasion, 120, { trim: false });
    return page && contactId && occasion ? { type: value.type, contactId, occasion, ...page } : undefined;
  }

  if (value.type === 'gifts.add') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'name', 'category', 'occasion', 'cost'], ['feedback', 'notes'])) {
      return undefined;
    }
    const contactId = id(value.contactId);
    const name = boundedString(value.name, 120, { trim: false });
    const occasion = boundedString(value.occasion, 120, { trim: false });
    const notes = value.notes === undefined ? '' : boundedString(value.notes, 500, { allowEmpty: true, trim: false });
    const feedback = value.feedback ?? 'Unknown';
    if (
      !contactId ||
      !name ||
      !occasion ||
      notes === undefined ||
      !giftCategories.includes(value.category as GiftCategory) ||
      !giftFeedback.includes(feedback as never) ||
      typeof value.cost !== 'number' ||
      !Number.isFinite(value.cost) ||
      value.cost < 0 ||
      value.cost > 1_000_000
    ) {
      return undefined;
    }
    return {
      type: value.type,
      contactId,
      name,
      category: value.category as GiftCategory,
      occasion,
      cost: value.cost,
      feedback: feedback as (typeof giftFeedback)[number],
      notes
    };
  }

  if (value.type === 'gifts.delete') {
    if (!hasOnlyKeys(value, ['type', 'giftId', 'confirmation']) || value.confirmation !== 'DELETE GIFT') {
      return undefined;
    }
    const giftId = id(value.giftId);
    return giftId ? { type: value.type, giftId, confirmation: 'DELETE GIFT' } : undefined;
  }

  if (value.type === 'gifts.set-budget') {
    if (!hasOnlyKeys(value, ['type', 'contactId', 'annualGiftBudget'])) return undefined;
    const contactId = id(value.contactId);
    return contactId &&
      typeof value.annualGiftBudget === 'number' &&
      Number.isFinite(value.annualGiftBudget) &&
      value.annualGiftBudget >= 0 &&
      value.annualGiftBudget <= 500_000
      ? { type: value.type, contactId, annualGiftBudget: value.annualGiftBudget }
      : undefined;
  }

  if (value.type === 'onboarding.set-goal') {
    return hasOnlyKeys(value, ['type', 'goal']) && onboardingGoals.includes(value.goal as OnboardingGoal)
      ? { type: value.type, goal: value.goal as OnboardingGoal }
      : undefined;
  }

  if (value.type === 'onboarding.set-step' || value.type === 'onboarding.skip') {
    if (!hasOnlyKeys(value, ['type', 'stepId']) || !onboardingSteps.includes(value.stepId as OnboardingStepId)) {
      return undefined;
    }
    return { type: value.type, stepId: value.stepId as OnboardingStepId };
  }

  if (value.type === 'account.disconnect') {
    return hasOnlyKeys(value, ['type', 'confirmation']) && value.confirmation === 'DISCONNECT ACCOUNT'
      ? { type: value.type, confirmation: 'DISCONNECT ACCOUNT' }
      : undefined;
  }

  if (value.type === 'privacy.set-whatsapp-consent') {
    return hasOnlyKeys(value, ['type', 'enabled']) && typeof value.enabled === 'boolean'
      ? { type: value.type, enabled: value.enabled }
      : undefined;
  }

  if (value.type === 'settings.set-boolean') {
    if (
      !hasOnlyKeys(value, ['type', 'key', 'enabled']) ||
      !booleanSettingKeys.includes(value.key as never) ||
      typeof value.enabled !== 'boolean'
    ) {
      return undefined;
    }
    return {
      type: value.type,
      key: value.key as (typeof booleanSettingKeys)[number],
      enabled: value.enabled
    };
  }

  if (value.type === 'settings.set-locale') {
    return hasOnlyKeys(value, ['type', 'locale']) && locales.includes(value.locale as SupportedLocale)
      ? { type: value.type, locale: value.locale as SupportedLocale }
      : undefined;
  }

  if (value.type === 'settings.set-email-sender') {
    if (!hasOnlyKeys(value, ['type', 'senderEmail'])) return undefined;
    const senderEmail = boundedString(value.senderEmail, MAX_EMAIL_ADDRESS_LENGTH, { allowEmpty: true });
    if (senderEmail === undefined) return undefined;
    const normalized = normalizeEmailAddress(senderEmail);
    return normalized.length === 0 || isValidEmailAddress(normalized)
      ? { type: value.type, senderEmail: normalized }
      : undefined;
  }

  if (value.type === 'settings.set-automation') {
    return hasOnlyKeys(value, ['type', 'mode']) && availableAutomationModes.includes(value.mode as never)
      ? { type: value.type, mode: value.mode as (typeof availableAutomationModes)[number] }
      : undefined;
  }

  if (value.type === 'settings.set-quiet-hours') {
    if (!hasOnlyKeys(value, ['type', 'start', 'end'])) return undefined;
    const start = parseTime(value.start);
    const end = parseTime(value.end);
    return start && end ? { type: value.type, start, end } : undefined;
  }

  if (value.type === 'settings.set-default-send-time') {
    if (!hasOnlyKeys(value, ['type', 'time'])) return undefined;
    const time = parseTime(value.time);
    return time ? { type: value.type, time } : undefined;
  }

  if (value.type === 'settings.add-blackout') {
    if (!hasOnlyKeys(value, ['type', 'label', 'startDate', 'endDate'], ['behavior', 'channels'])) return undefined;
    const label = boundedString(value.label, 80);
    const startDate = parseDateKey(value.startDate);
    const endDate = parseDateKey(value.endDate);
    const channels = value.channels;
    if (
      !label ||
      !startDate ||
      !endDate ||
      (value.behavior !== undefined && value.behavior !== 'Block' && value.behavior !== 'Defer') ||
      (channels !== undefined &&
        (!Array.isArray(channels) ||
          channels.length === 0 ||
          channels.length > messageChannels.length ||
          new Set(channels).size !== channels.length ||
          channels.some(channel => !messageChannels.includes(channel as never))))
    ) {
      return undefined;
    }
    return {
      type: value.type,
      label,
      startDate,
      endDate,
      behavior: value.behavior as 'Block' | 'Defer' | undefined,
      channels: channels as MessageDraft['channel'][] | undefined
    };
  }

  if (value.type === 'settings.remove-blackout') {
    if (!hasOnlyKeys(value, ['type', 'blackoutId'])) return undefined;
    const blackoutId = id(value.blackoutId);
    return blackoutId ? { type: value.type, blackoutId } : undefined;
  }

  if (value.type === 'style.train-samples') {
    if (!hasOnlyKeys(value, ['type', 'samples'])) return undefined;
    const samples = boundedString(value.samples, MAX_STYLE_SAMPLE_LENGTH, { trim: false });
    return samples ? { type: value.type, samples } : undefined;
  }

  if (value.type === 'style.set-enabled') {
    return hasOnlyKeys(value, ['type', 'enabled']) && typeof value.enabled === 'boolean'
      ? { type: value.type, enabled: value.enabled }
      : undefined;
  }

  if (value.type === 'activity.query') {
    if (!hasOnlyKeys(value, ['type'], ['query', 'activityType', 'severity', 'status', 'date', 'cursor', 'limit'])) {
      return undefined;
    }
    const page = parsePrivatePageRequest(value);
    const query =
      value.query === undefined ? '' : boundedString(value.query, MAX_SEARCH_LENGTH, { allowEmpty: true, trim: false });
    const date = value.date ?? 'All';
    if (
      !page ||
      query === undefined ||
      (value.activityType !== undefined && !activityTypes.includes(value.activityType as never)) ||
      (value.severity !== undefined && !activitySeverities.includes(value.severity as never)) ||
      (value.status !== undefined && !activityStatuses.includes(value.status as never)) ||
      !activityDates.includes(date as never)
    ) {
      return undefined;
    }
    return {
      type: value.type,
      query,
      activityType: value.activityType as (typeof activityTypes)[number] | undefined,
      severity: value.severity as (typeof activitySeverities)[number] | undefined,
      status: value.status as (typeof activityStatuses)[number] | undefined,
      date: date as (typeof activityDates)[number],
      ...page
    };
  }

  if (value.type === 'activity.open-action') {
    if (!hasOnlyKeys(value, ['type', 'activityId'])) return undefined;
    const activityId = id(value.activityId);
    return activityId ? { type: value.type, activityId } : undefined;
  }

  if (value.type === 'activity.resolve') {
    if (!hasOnlyKeys(value, ['type', 'activityId'])) return undefined;
    const activityId = id(value.activityId);
    return activityId ? { type: value.type, activityId } : undefined;
  }

  if (value.type === 'home.open-action') {
    if (!hasOnlyKeys(value, ['type', 'actionId'])) return undefined;
    const actionId = id(value.actionId);
    return actionId ? { type: value.type, actionId } : undefined;
  }

  if (value.type === 'setup.wizard.inspect') {
    return hasOnlyKeys(value, ['type', 'goal']) && setupGoals.includes(value.goal as never)
      ? { type: value.type, goal: value.goal as (typeof setupGoals)[number] }
      : undefined;
  }

  if (value.type === 'setup.wizard.run-action') {
    if (!hasOnlyKeys(value, ['type', 'goal', 'stepId']) || !setupGoals.includes(value.goal as never)) {
      return undefined;
    }
    const stepId = id(value.stepId);
    return stepId ? { type: value.type, goal: value.goal as (typeof setupGoals)[number], stepId } : undefined;
  }

  if (value.type === 'setup.open-action') {
    if (!hasOnlyKeys(value, ['type', 'checkId'])) return undefined;
    const checkId = id(value.checkId);
    return checkId ? { type: value.type, checkId } : undefined;
  }

  if (value.type === 'calendar.export') {
    if (!hasOnlyKeys(value, ['type'], ['eventIds'])) return undefined;
    if (!Object.prototype.hasOwnProperty.call(value, 'eventIds')) return { type: value.type };
    if (
      !Array.isArray(value.eventIds) ||
      value.eventIds.length === 0 ||
      value.eventIds.length > MAX_CALENDAR_EXPORT_EVENT_IDS
    ) {
      return undefined;
    }
    const eventIds = value.eventIds.map(id);
    if (eventIds.some(eventId => !eventId) || new Set(eventIds).size !== eventIds.length) return undefined;
    return { type: value.type, eventIds: eventIds as string[] };
  }

  if (
    value.type === 'contacts.import' ||
    value.type === 'contacts.import-preview' ||
    value.type === 'calendar.import' ||
    value.type === 'calendar.import-preview' ||
    value.type === 'events.import-file' ||
    value.type === 'permissions.refresh' ||
    value.type === 'biometric.enable' ||
    value.type === 'biometric.unlock' ||
    value.type === 'backup.select-file' ||
    value.type === 'home.inspect' ||
    value.type === 'setup.inspect' ||
    value.type === 'onboarding.inspect' ||
    value.type === 'onboarding.advance' ||
    value.type === 'onboarding.complete' ||
    value.type === 'onboarding.reopen' ||
    value.type === 'account.inspect' ||
    value.type === 'account.use-local' ||
    value.type === 'privacy.inspect' ||
    value.type === 'settings.inspect' ||
    value.type === 'style.inspect' ||
    value.type === 'style.train-sent'
  ) {
    return hasOnlyKeys(value, ['type']) ? ({ type: value.type } as HarnessCommand) : undefined;
  }

  if (value.type === 'biometric.disable') {
    return hasOnlyKeys(value, ['type']) ? { type: 'biometric.disable' } : undefined;
  }

  if (value.type === 'operation.cancel') {
    if (!hasOnlyKeys(value, ['type', 'scope'])) return undefined;
    const scope = operationScope(value.scope);
    return scope ? { type: value.type, scope } : undefined;
  }

  if (value.type === 'reminders.reconcile') {
    if (!hasOnlyKeys(value, ['type'], ['reason'])) return undefined;
    const reason = value.reason ?? 'manual';
    return reminderReasons.includes(reason as (typeof reminderReasons)[number])
      ? { type: 'reminders.reconcile', reason: reason as (typeof reminderReasons)[number] }
      : undefined;
  }

  if (value.type === 'ai.draft') {
    if (
      !hasOnlyKeys(value, ['type', 'contactId', 'reason'], ['eventId', 'excludedMemoryIds', 'includePriorMessages'])
    ) {
      return undefined;
    }
    const contactId = id(value.contactId);
    const eventId = optionalId(value.eventId);
    if (
      !contactId ||
      (value.eventId !== undefined && !eventId) ||
      !composerReasons.includes(value.reason as ComposerReason)
    ) {
      return undefined;
    }
    const includePriorMessages = optionalBoolean(value.includePriorMessages, true);
    if (includePriorMessages === undefined) return undefined;
    let excludedMemoryIds: string[] | undefined;
    if (value.excludedMemoryIds !== undefined) {
      if (!Array.isArray(value.excludedMemoryIds) || value.excludedMemoryIds.length > MAX_EXCLUDED_MEMORY_IDS)
        return undefined;
      const parsedIds = value.excludedMemoryIds.map(id);
      if (parsedIds.some(item => !item)) return undefined;
      excludedMemoryIds = parsedIds as string[];
    }
    return {
      type: 'ai.draft',
      contactId,
      eventId,
      reason: value.reason as ComposerReason,
      excludedMemoryIds,
      includePriorMessages
    };
  }

  if (value.type === 'email.deliver' || value.type === 'email.reconcile') {
    if (!hasOnlyKeys(value, ['type', 'messageId'])) return undefined;
    const messageId = id(value.messageId);
    return messageId ? { type: value.type, messageId } : undefined;
  }

  if (value.type === 'handoff.open') {
    if (!hasOnlyKeys(value, ['type', 'messageId'], ['preferFallback'])) return undefined;
    const messageId = id(value.messageId);
    const preferFallback = optionalBoolean(value.preferFallback);
    return messageId && preferFallback !== undefined ? { type: 'handoff.open', messageId, preferFallback } : undefined;
  }

  if (value.type === 'handoff.confirm') {
    if (!hasOnlyKeys(value, ['type', 'messageId', 'sent']) || typeof value.sent !== 'boolean') return undefined;
    const messageId = id(value.messageId);
    return messageId ? { type: 'handoff.confirm', messageId, sent: value.sent } : undefined;
  }

  if (value.type === 'events.import-text') {
    if (!hasOnlyKeys(value, ['type', 'raw'], ['format'])) return undefined;
    const raw = boundedString(value.raw, MAX_EVENT_IMPORT_BYTES, { trim: false });
    const format = value.format ?? 'auto';
    if (!raw || !['auto', 'csv', 'vcard'].includes(format as EventImportFormat)) return undefined;
    if (new TextEncoder().encode(raw).byteLength > MAX_EVENT_IMPORT_BYTES) return undefined;
    return { type: 'events.import-text', raw, format: format as EventImportFormat };
  }

  if (value.type === 'backup.export') {
    if (!hasOnlyKeys(value, ['type', 'passphrase'], ['destination'])) return undefined;
    const passphrase = boundedString(value.passphrase, MAX_BACKUP_PASSPHRASE_LENGTH, { trim: false });
    const destination = value.destination ?? 'share';
    return passphrase && (destination === 'share' || destination === 'save')
      ? { type: 'backup.export', passphrase, destination }
      : undefined;
  }

  if (value.type === 'backup.export-confirm') {
    if (!hasOnlyKeys(value, ['type', 'backupConfirmationToken'])) return undefined;
    const backupConfirmationToken = id(value.backupConfirmationToken);
    return backupConfirmationToken ? { type: value.type, backupConfirmationToken } : undefined;
  }

  if (value.type === 'backup.restore-preview') {
    if (!hasOnlyKeys(value, ['type', 'raw', 'passphrase'])) return undefined;
    const raw = boundedString(value.raw, MAX_BACKUP_RAW_BYTES, { trim: false });
    const passphrase = boundedString(value.passphrase, MAX_BACKUP_PASSPHRASE_LENGTH, { trim: false });
    if (!raw || !passphrase || new TextEncoder().encode(raw).byteLength > MAX_BACKUP_RAW_BYTES) return undefined;
    return { type: 'backup.restore-preview', raw, passphrase };
  }

  if (value.type === 'backup.restore-preview-selected') {
    if (!hasOnlyKeys(value, ['type', 'selectionToken', 'passphrase'])) return undefined;
    const selectionToken = id(value.selectionToken);
    const passphrase = boundedString(value.passphrase, MAX_BACKUP_PASSPHRASE_LENGTH, { trim: false });
    return selectionToken && passphrase ? { type: value.type, selectionToken, passphrase } : undefined;
  }

  if (value.type === 'backup.restore-confirm') {
    if (!hasOnlyKeys(value, ['type', 'confirmationToken'])) return undefined;
    const confirmationToken = id(value.confirmationToken);
    return confirmationToken ? { type: 'backup.restore-confirm', confirmationToken } : undefined;
  }

  if (value.type === 'data.clear') {
    return hasOnlyKeys(value, ['type', 'confirmation']) && value.confirmation === 'CLEAR LOCAL DATA'
      ? { type: 'data.clear', confirmation: 'CLEAR LOCAL DATA' }
      : undefined;
  }

  if (value.type === 'permissions.preflight') {
    if (
      !hasOnlyKeys(value, ['type', 'capability']) ||
      !permissionCapabilities.includes(value.capability as SystemPermissionCapability)
    ) {
      return undefined;
    }
    return { type: 'permissions.preflight', capability: value.capability as SystemPermissionCapability };
  }

  if (value.type === 'permissions.request') {
    if (!hasOnlyKeys(value, ['type', 'capability', 'userIntent'])) return undefined;
    if (
      !['Contacts', 'Notifications', 'Calendar'].includes(value.capability as string) ||
      (value.userIntent !== 'allow' && value.userIntent !== 'decline')
    )
      return undefined;
    return {
      type: value.type,
      capability: value.capability as 'Contacts' | 'Notifications' | 'Calendar',
      userIntent: value.userIntent
    };
  }

  if (
    value.type === 'analytics.inspect' ||
    value.type === 'analytics.share-summary' ||
    value.type === 'analytics.export-preview'
  ) {
    if (!hasOnlyKeys(value, ['type'], ['range'])) return undefined;
    const range = value.range ?? 'Last 30 days';
    return analyticsRanges.includes(range as (typeof analyticsRanges)[number])
      ? { type: value.type, range: range as (typeof analyticsRanges)[number] }
      : undefined;
  }

  if (value.type === 'analytics.open-action') {
    if (!hasOnlyKeys(value, ['type', 'insightId'], ['range'])) return undefined;
    const insightId = id(value.insightId);
    const range = value.range ?? 'Last 30 days';
    return insightId && analyticsRanges.includes(range as (typeof analyticsRanges)[number])
      ? { type: value.type, range: range as (typeof analyticsRanges)[number], insightId }
      : undefined;
  }

  if (value.type === 'analytics.export-confirm') {
    if (!hasOnlyKeys(value, ['type', 'confirmationToken'])) return undefined;
    const confirmationToken = id(value.confirmationToken);
    return confirmationToken ? { type: value.type, confirmationToken } : undefined;
  }

  return undefined;
};

const serializeInput = (value: unknown): string | undefined => {
  try {
    const serialized = typeof value === 'string' ? value : JSON.stringify(value);
    return typeof serialized === 'string' ? serialized : undefined;
  } catch {
    return undefined;
  }
};

export const parseHarnessCommand = (input: unknown): CommandParseResult => {
  const serialized = serializeInput(input);
  if (serialized === undefined) return invalid();
  if (new TextEncoder().encode(serialized).byteLength > MAX_RUNTIME_COMMAND_BYTES) return tooLarge();

  let value: unknown;
  try {
    // Execute only the canonical JSON value. Prototypes, accessors, functions,
    // cycles, and second-read mutations cannot cross this boundary.
    value = JSON.parse(serialized);
  } catch {
    return invalid();
  }
  if (!isRecord(value)) return invalid();
  const command = parseCommandRecord(value);
  return command ? { ok: true, command } : invalid();
};
