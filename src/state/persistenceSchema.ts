import { productionInitialState } from '../data/productionState';
import { withCanonicalRecurrence } from '../domain/occasionDates';
import { normalizeScheduleTimeZone } from '../domain/schedulingPolicy';
import type {
  ActivityItem,
  AiProviderObservation,
  AppState,
  BackupSnapshot,
  Contact,
  ContactGroupDefaults,
  ContactPreferenceOverrides,
  GiftRecord,
  MemoryNote,
  MessageChannel,
  MessageDraft,
  PersistenceStorageHealth,
  RelationshipEvent,
  RelationshipGroup,
  ReminderPlan,
  ScheduleBlackout,
  SetupCheck
} from '../domain/types';

export const PERSISTED_STATE_SCHEMA_VERSION = 6;
export const MAX_PERSISTED_RECORDS_PER_AGGREGATE = 10_000;
export const MAX_PERSISTED_TOTAL_RECORDS = 30_000;
export const MAX_PERSISTED_FIELD_LENGTH = 32_768;
export const MAX_PERSISTED_RECOVERY_ISSUES = 200;

export type PersistedAggregateName =
  | 'shell'
  | 'contacts'
  | 'events'
  | 'memories'
  | 'gifts'
  | 'messages'
  | 'activity'
  | 'backups'
  | 'setupChecks'
  | 'reminderPlans'
  | 'styleProfile'
  | 'settings'
  | 'onboarding'
  | 'privacy'
  | 'aiProvider'
  | 'emailDelivery'
  | 'calendarSync'
  | 'persistence';

export type PersistenceRecoveryIssueCode =
  | 'invalid-aggregate'
  | 'invalid-record'
  | 'invalid-field'
  | 'duplicate-id'
  | 'record-limit-exceeded'
  | 'missing-reference'
  | 'reference-cleared'
  | 'missing-entry'
  | 'invalid-entry';

export interface PersistenceRecoveryIssue {
  aggregate: PersistedAggregateName;
  code: PersistenceRecoveryIssueCode;
  recordIndex?: number;
  field?: string;
}

export interface PersistenceRecoveryManifest {
  format: 'relateai.persistence-recovery';
  version: 1;
  redacted: true;
  recoveredAt: string;
  sourceVersion: number;
  outcome: 'selective' | 'unrecoverable';
  issueCount: number;
  excludedRecordCount: number;
  defaultedAggregates: PersistedAggregateName[];
  issues: PersistenceRecoveryIssue[];
  issuesTruncated: boolean;
}

export interface PersistedStateDecodeResult {
  state: AppState;
  issueCount: number;
  excludedRecordCount: number;
  defaultedAggregates: PersistedAggregateName[];
  issues: PersistenceRecoveryIssue[];
}

export class PersistedStateValidationError extends Error {
  readonly issueCount: number;

  constructor(issueCount: number) {
    super(`Persisted state failed bounded runtime validation with ${issueCount} issue(s).`);
    this.name = 'PersistedStateValidationError';
    this.issueCount = issueCount;
  }
}

const screens = new Set([
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
]);
const relationshipGroups: readonly RelationshipGroup[] = ['Family', 'Friends', 'Work', 'Close friends', 'Other'];
const relationshipGroupSet = new Set<string>(relationshipGroups);
const eventTypes = new Set([
  'Birthday',
  'Anniversary',
  'Work anniversary',
  'Custom',
  'Graduation',
  'Holiday',
  'Revival',
  'Follow-up'
]);
const messageChannels = new Set(['SMS', 'WhatsApp', 'Email', 'Manual']);
const messageStatuses = new Set([
  'Needs review',
  'Scheduled',
  'Blocked',
  'Sent',
  'Failed',
  'Delivery pending',
  'Delivery unknown',
  'Rejected',
  'Draft'
]);
const automationModes = new Set(['Always ask', 'Smart approve', 'VIP approve', 'Fully auto']);
const locales = new Set(['en-IN', 'hi-IN', 'en-Hinglish']);
const tones = new Set(['Warm', 'Respectful', 'Playful', 'Concise', 'Formal', 'Hinglish', 'No emoji']);
const contactLanguages = new Set(['English', 'Hinglish', 'Hindi']);
const contactQuietHoursBehaviors = new Set(['Defer', 'Block']);
const localTimePattern = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const eventSources = new Set(['Imported', 'Manual', 'AI suggested']);
const memoryCategories = new Set(['General', 'Private', 'Preference', 'Event', 'Gift', 'Milestone']);
const giftCategories = new Set(['Experience', 'Food', 'Books', 'Wellness', 'Personal', 'Other']);
const giftFeedback = new Set(['Liked', 'Disliked', 'Unknown']);
const composerReasons = new Set([
  'Birthday',
  'Check-in',
  'Thanks',
  'Congratulations',
  'Apology',
  'Follow-up',
  'Custom'
]);
const messageQualities = new Set(['AI draft', 'Template fallback', 'Needs more context']);
const messageVariants = new Set(['short', 'standard', 'warm']);
const activityTypes = new Set(['Message', 'Event', 'Contact', 'Backup', 'Setup', 'AI', 'Gift', 'Memory', 'Analytics']);
const activitySeverities = new Set(['Info', 'Warning', 'Error']);
const activityStatuses = new Set(['Open', 'Resolved', 'Obsolete', 'Completed']);
const onboardingSteps = new Set([
  'intro',
  'account',
  'contacts',
  'notifications',
  'ai',
  'style',
  'channels',
  'backup',
  'finish'
]);
const onboardingGoals = new Set(['Reminders first', 'AI wishes', 'Manual relationship manager', 'Full setup']);
const permissionCapabilities = [
  'Contacts',
  'Notifications',
  'SMS',
  'Calendar',
  'Biometric lock',
  'AI provider',
  'Email provider',
  'WhatsApp handoff',
  'Backup export'
] as const;
const permissionDecisions = new Set(['Not requested', 'Granted', 'Denied', 'Unavailable']);

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const boundedString = (value: unknown, maximum = MAX_PERSISTED_FIELD_LENGTH, allowEmpty = true): value is string =>
  typeof value === 'string' && value.length <= maximum && (allowEmpty || value.trim().length > 0);

const finiteNumber = (value: unknown, minimum: number, maximum: number, integer = false): value is number =>
  typeof value === 'number' &&
  Number.isFinite(value) &&
  value >= minimum &&
  value <= maximum &&
  (!integer || Number.isInteger(value));

const optionalString = (value: unknown, maximum = MAX_PERSISTED_FIELD_LENGTH): value is string | undefined =>
  value === undefined || boundedString(value, maximum);

const validLocalDateKey = (value: string) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  return parsed.getUTCFullYear() === year && parsed.getUTCMonth() === month - 1 && parsed.getUTCDate() === day;
};

class DecodeContext {
  readonly issues: PersistenceRecoveryIssue[] = [];
  issueCount = 0;
  excludedRecordCount = 0;
  readonly defaultedAggregates = new Set<PersistedAggregateName>();
  remainingRecordBudget = MAX_PERSISTED_TOTAL_RECORDS;

  issue(aggregate: PersistedAggregateName, code: PersistenceRecoveryIssueCode, recordIndex?: number, field?: string) {
    this.issueCount += 1;
    if (this.issues.length < MAX_PERSISTED_RECOVERY_ISSUES) {
      this.issues.push({
        aggregate,
        code,
        ...(recordIndex !== undefined ? { recordIndex } : {}),
        ...(field ? { field } : {})
      });
    }
  }

  exclude(aggregate: PersistedAggregateName, code: PersistenceRecoveryIssueCode, recordIndex: number, field?: string) {
    this.excludedRecordCount += 1;
    this.issue(aggregate, code, recordIndex, field);
  }

  defaultAggregate(aggregate: PersistedAggregateName, code: PersistenceRecoveryIssueCode) {
    this.defaultedAggregates.add(aggregate);
    this.issue(aggregate, code);
  }
}

const invalidRequired = (
  context: DecodeContext,
  aggregate: PersistedAggregateName,
  index: number,
  record: Record<string, unknown>,
  fields: Readonly<Record<string, (value: unknown) => boolean>>
): boolean => {
  for (const [field, predicate] of Object.entries(fields)) {
    if (!predicate(record[field])) {
      context.exclude(aggregate, 'invalid-field', index, field);
      return true;
    }
  }
  return false;
};

const decodeOptionalString = (
  context: DecodeContext,
  aggregate: PersistedAggregateName,
  index: number,
  record: Record<string, unknown>,
  field: string,
  maximum = MAX_PERSISTED_FIELD_LENGTH
): string | undefined => {
  const value = record[field];
  if (value === undefined) {
    return undefined;
  }
  if (!boundedString(value, maximum)) {
    context.issue(aggregate, 'invalid-field', index, field);
    return undefined;
  }
  return value;
};

const decodeStringArray = (
  value: unknown,
  allowed: ReadonlySet<string>,
  maximumItems: number
): string[] | undefined => {
  if (!Array.isArray(value) || value.length > maximumItems) {
    return undefined;
  }
  const result: string[] = [];
  for (const item of value) {
    if (!boundedString(item, 100, false) || !allowed.has(item) || result.includes(item)) {
      return undefined;
    }
    result.push(item);
  }
  return result;
};

const decodeGroupPreferences = (value: unknown): ContactPreferenceOverrides | undefined => {
  if (!isRecord(value)) {
    return undefined;
  }
  const result: ContactPreferenceOverrides = {};
  if (value.preferredChannel !== undefined) {
    if (!boundedString(value.preferredChannel, 20) || !messageChannels.has(value.preferredChannel)) {
      return undefined;
    }
    result.preferredChannel = value.preferredChannel as ContactPreferenceOverrides['preferredChannel'];
  }
  if (value.tone !== undefined) {
    const decodedTone = decodeStringArray(value.tone, tones, 7);
    if (!decodedTone) {
      return undefined;
    }
    result.tone = decodedTone as ContactPreferenceOverrides['tone'];
  }
  if (value.checkInCadenceDays !== undefined) {
    if (!finiteNumber(value.checkInCadenceDays, 1, 3650, true)) {
      return undefined;
    }
    result.checkInCadenceDays = value.checkInCadenceDays;
  }
  if (value.automationMode !== undefined) {
    if (!boundedString(value.automationMode, 30) || !automationModes.has(value.automationMode)) {
      return undefined;
    }
    result.automationMode = value.automationMode as ContactPreferenceOverrides['automationMode'];
  }
  return result;
};

const decodeContactRoutes = (value: unknown): Contact['routes'] | undefined => {
  if (value === undefined) return undefined;
  if (!Array.isArray(value) || value.length > 100) return undefined;
  const identities = new Set<string>();
  const routes: NonNullable<Contact['routes']> = [];
  for (const item of value) {
    if (
      !isRecord(item) ||
      !boundedString(item.id, 256, false) ||
      (item.type !== 'Phone' && item.type !== 'Email') ||
      !boundedString(item.value, 512, false) ||
      (item.label !== undefined && !boundedString(item.label, 160)) ||
      typeof item.primary !== 'boolean' ||
      typeof item.verified !== 'boolean'
    ) {
      return undefined;
    }
    const identity = `${item.type}:${item.value}`;
    if (identities.has(identity)) return undefined;
    identities.add(identity);
    routes.push({
      id: item.id,
      type: item.type,
      value: item.value,
      ...(item.label ? { label: item.label as string } : {}),
      primary: item.primary,
      verified: item.verified
    });
  }
  return routes;
};

const decodeContactSourceIdentities = (value: unknown): Contact['sourceIdentities'] | undefined => {
  if (value === undefined) return undefined;
  if (!Array.isArray(value) || value.length > 100) return undefined;
  const identities = new Set<string>();
  const result: NonNullable<Contact['sourceIdentities']> = [];
  for (const item of value) {
    if (
      !isRecord(item) ||
      (item.provider !== 'Device contacts' && item.provider !== 'Calendar' && item.provider !== 'Local') ||
      !boundedString(item.sourceId, 256, false)
    ) {
      return undefined;
    }
    const identity = `${item.provider}:${item.sourceId}`;
    if (identities.has(identity)) continue;
    identities.add(identity);
    result.push({ provider: item.provider, sourceId: item.sourceId });
  }
  return result;
};

const decodeContact = (value: unknown, index: number, context: DecodeContext): Contact | undefined => {
  const aggregate = 'contacts';
  if (!isRecord(value)) {
    context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  if (
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      name: candidate => boundedString(candidate, 500, false),
      relationship: candidate => boundedString(candidate, 500),
      group: candidate => boundedString(candidate, 30) && relationshipGroupSet.has(candidate),
      preferredChannel: candidate => boundedString(candidate, 20) && messageChannels.has(candidate),
      language: candidate => boundedString(candidate, 20) && contactLanguages.has(candidate),
      tone: candidate => decodeStringArray(candidate, tones, 7) !== undefined,
      healthScore: candidate => finiteNumber(candidate, 0, 100),
      isVip: candidate => typeof candidate === 'boolean',
      dnd: candidate => typeof candidate === 'boolean',
      checkInCadenceDays: candidate => finiteNumber(candidate, 1, 3650, true),
      notesSummary: candidate => boundedString(candidate),
      annualGiftBudget: candidate => finiteNumber(candidate, 0, 1_000_000_000)
    })
  ) {
    return undefined;
  }
  const preferenceOverrides =
    value.preferenceOverrides === undefined ? undefined : decodeGroupPreferences(value.preferenceOverrides);
  if (value.preferenceOverrides !== undefined && !preferenceOverrides) {
    context.issue(aggregate, 'invalid-field', index, 'preferenceOverrides');
  }
  const routes = decodeContactRoutes(value.routes);
  const sourceIdentities = decodeContactSourceIdentities(value.sourceIdentities);
  if (value.routes !== undefined && !routes) {
    context.issue(aggregate, 'invalid-field', index, 'routes');
  }
  if (value.sourceIdentities !== undefined && !sourceIdentities) {
    context.issue(aggregate, 'invalid-field', index, 'sourceIdentities');
  }
  const relationshipSubtype = decodeOptionalString(context, aggregate, index, value, 'relationshipSubtype', 80);
  const jobTitle = decodeOptionalString(context, aggregate, index, value, 'jobTitle', 120);
  let customSendTime: string | undefined;
  if (value.customSendTime !== undefined) {
    if (boundedString(value.customSendTime, 5, false) && localTimePattern.test(value.customSendTime)) {
      customSendTime = value.customSendTime;
    } else {
      context.issue(aggregate, 'invalid-field', index, 'customSendTime');
    }
  }
  let quietHoursBehavior: Contact['quietHoursBehavior'] = 'Defer';
  if (value.quietHoursBehavior !== undefined) {
    if (
      boundedString(value.quietHoursBehavior, 10, false) &&
      contactQuietHoursBehaviors.has(value.quietHoursBehavior)
    ) {
      quietHoursBehavior = value.quietHoursBehavior as Contact['quietHoursBehavior'];
    } else {
      context.issue(aggregate, 'invalid-field', index, 'quietHoursBehavior');
    }
  }
  let skipAuto = false;
  if (value.skipAuto !== undefined) {
    if (typeof value.skipAuto === 'boolean') {
      skipAuto = value.skipAuto;
    } else {
      context.issue(aggregate, 'invalid-field', index, 'skipAuto');
    }
  }
  return {
    id: value.id as string,
    name: value.name as string,
    relationship: value.relationship as string,
    ...(relationshipSubtype?.trim() ? { relationshipSubtype } : {}),
    ...(jobTitle?.trim() ? { jobTitle } : {}),
    group: value.group as Contact['group'],
    ...(decodeOptionalString(context, aggregate, index, value, 'phone', 512) ? { phone: value.phone as string } : {}),
    ...(decodeOptionalString(context, aggregate, index, value, 'email', 512) ? { email: value.email as string } : {}),
    preferredChannel: value.preferredChannel as Contact['preferredChannel'],
    language: value.language as Contact['language'],
    tone: decodeStringArray(value.tone, tones, 7) as Contact['tone'],
    healthScore: value.healthScore as number,
    isVip: value.isVip as boolean,
    dnd: value.dnd as boolean,
    checkInCadenceDays: value.checkInCadenceDays as number,
    ...(customSendTime ? { customSendTime } : {}),
    quietHoursBehavior,
    skipAuto,
    ...(preferenceOverrides ? { preferenceOverrides } : {}),
    ...(decodeOptionalString(context, aggregate, index, value, 'lastContactedAt', 64)
      ? { lastContactedAt: value.lastContactedAt as string }
      : {}),
    ...(decodeOptionalString(context, aggregate, index, value, 'checkInSnoozedUntil', 64)
      ? { checkInSnoozedUntil: value.checkInSnoozedUntil as string }
      : {}),
    ...(routes ? { routes } : {}),
    ...(sourceIdentities ? { sourceIdentities } : {}),
    ...(decodeOptionalString(context, aggregate, index, value, 'archivedAt', 64)
      ? { archivedAt: value.archivedAt as string }
      : {}),
    notesSummary: value.notesSummary as string,
    annualGiftBudget: value.annualGiftBudget as number
  };
};

const decodeChecklist = (value: unknown): RelationshipEvent['checklist'] | undefined => {
  if (!Array.isArray(value) || value.length > 100) {
    return undefined;
  }
  const ids = new Set<string>();
  const result: RelationshipEvent['checklist'] = [];
  for (const item of value) {
    if (
      !isRecord(item) ||
      !boundedString(item.id, 256, false) ||
      ids.has(item.id) ||
      !boundedString(item.label, 500, false) ||
      typeof item.done !== 'boolean' ||
      (item.completedForOccurrence !== undefined &&
        (typeof item.completedForOccurrence !== 'string' || !validLocalDateKey(item.completedForOccurrence)))
    ) {
      return undefined;
    }
    ids.add(item.id);
    result.push({
      id: item.id,
      label: item.label,
      done: item.done,
      ...(item.completedForOccurrence !== undefined
        ? { completedForOccurrence: item.completedForOccurrence as string }
        : {})
    });
  }
  return result;
};

const decodeRecurrence = (value: unknown): RelationshipEvent['recurrence'] | undefined | false => {
  if (value === undefined) {
    return undefined;
  }
  if (
    !isRecord(value) ||
    value.frequency !== 'Yearly' ||
    !finiteNumber(value.month, 1, 12, true) ||
    !finiteNumber(value.day, 1, 31, true) ||
    (value.originalYear !== undefined && !finiteNumber(value.originalYear, 1, 9999, true)) ||
    (value.leapDayPolicy !== 'February 28' && value.leapDayPolicy !== 'March 1')
  ) {
    return false;
  }
  return {
    frequency: 'Yearly',
    month: value.month,
    day: value.day,
    ...(value.originalYear !== undefined ? { originalYear: value.originalYear as number } : {}),
    leapDayPolicy: value.leapDayPolicy
  };
};

const decodeEventSourceIdentities = (value: unknown): RelationshipEvent['sourceIdentities'] | undefined => {
  if (value === undefined) return undefined;
  if (!Array.isArray(value) || value.length > 100) return undefined;
  const identities = new Set<string>();
  const result: NonNullable<RelationshipEvent['sourceIdentities']> = [];
  for (const item of value) {
    if (
      !isRecord(item) ||
      (item.provider !== 'Device contacts' && item.provider !== 'Calendar' && item.provider !== 'Local') ||
      !boundedString(item.sourceId, 256, false)
    ) {
      return undefined;
    }
    const identity = `${item.provider}:${item.sourceId}`;
    if (identities.has(identity)) continue;
    identities.add(identity);
    result.push({ provider: item.provider, sourceId: item.sourceId });
  }
  return result;
};

const decodeEvent = (value: unknown, index: number, context: DecodeContext): RelationshipEvent | undefined => {
  const aggregate = 'events';
  if (!isRecord(value)) {
    context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  const checklist = decodeChecklist(value.checklist);
  const recurrence = decodeRecurrence(value.recurrence);
  const sourceIdentities = decodeEventSourceIdentities(value.sourceIdentities);
  if (
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      contactId: candidate => boundedString(candidate, 256, false),
      type: candidate => boundedString(candidate, 30) && eventTypes.has(candidate),
      label: candidate => boundedString(candidate, 1000, false),
      date: candidate => boundedString(candidate, 64, false) && Number.isFinite(Date.parse(candidate)),
      verified: candidate => typeof candidate === 'boolean',
      source: candidate => boundedString(candidate, 30) && eventSources.has(candidate),
      checklist: () => checklist !== undefined
    }) ||
    recurrence === false
  ) {
    if (recurrence === false) {
      context.exclude(aggregate, 'invalid-field', index, 'recurrence');
    }
    return undefined;
  }
  if (value.sourceIdentities !== undefined && !sourceIdentities) {
    context.issue(aggregate, 'invalid-field', index, 'sourceIdentities');
  }
  const decoded: RelationshipEvent = {
    id: value.id as string,
    contactId: value.contactId as string,
    type: value.type as RelationshipEvent['type'],
    label: value.label as string,
    date: value.date as string,
    ...(recurrence ? { recurrence } : {}),
    verified: value.verified as boolean,
    source: value.source as RelationshipEvent['source'],
    ...(sourceIdentities ? { sourceIdentities } : {}),
    checklist: checklist as RelationshipEvent['checklist']
  };
  try {
    return withCanonicalRecurrence(decoded);
  } catch {
    context.exclude(aggregate, 'invalid-field', index, 'recurrence');
    return undefined;
  }
};

const decodeMemory = (value: unknown, index: number, context: DecodeContext): MemoryNote | undefined => {
  const aggregate = 'memories';
  if (
    !isRecord(value) ||
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      contactId: candidate => boundedString(candidate, 256, false),
      category: candidate => boundedString(candidate, 30) && memoryCategories.has(candidate),
      body: candidate => boundedString(candidate, MAX_PERSISTED_FIELD_LENGTH, false),
      pinned: candidate => typeof candidate === 'boolean',
      createdAt: candidate => boundedString(candidate, 64, false) && Number.isFinite(Date.parse(candidate))
    })
  ) {
    if (!isRecord(value)) context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  return {
    id: value.id as string,
    contactId: value.contactId as string,
    category: value.category as MemoryNote['category'],
    body: value.body as string,
    pinned: value.pinned as boolean,
    createdAt: value.createdAt as string
  };
};

const decodeGift = (value: unknown, index: number, context: DecodeContext): GiftRecord | undefined => {
  const aggregate = 'gifts';
  if (
    !isRecord(value) ||
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      contactId: candidate => boundedString(candidate, 256, false),
      name: candidate => boundedString(candidate, 1000, false),
      category: candidate => boundedString(candidate, 30) && giftCategories.has(candidate),
      occasion: candidate => boundedString(candidate, 1000),
      cost: candidate => finiteNumber(candidate, 0, 1_000_000_000),
      year: candidate => finiteNumber(candidate, 1, 9999, true),
      feedback: candidate => boundedString(candidate, 20) && giftFeedback.has(candidate),
      notes: candidate => boundedString(candidate)
    })
  ) {
    if (!isRecord(value)) context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  return {
    id: value.id as string,
    contactId: value.contactId as string,
    name: value.name as string,
    category: value.category as GiftRecord['category'],
    occasion: value.occasion as string,
    cost: value.cost as number,
    year: value.year as number,
    feedback: value.feedback as GiftRecord['feedback'],
    notes: value.notes as string
  };
};

const decodeMessage = (
  value: unknown,
  index: number,
  context: DecodeContext,
  sourceVersion: number
): MessageDraft | undefined => {
  const aggregate = 'messages';
  if (!isRecord(value)) {
    context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  const variants = value.variants;
  const validVariants =
    isRecord(variants) &&
    boundedString(variants.short) &&
    boundedString(variants.standard) &&
    boundedString(variants.warm);
  if (
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      contactId: candidate => boundedString(candidate, 256, false),
      reason: candidate => boundedString(candidate, 30) && composerReasons.has(candidate),
      status: candidate => boundedString(candidate, 30) && messageStatuses.has(candidate),
      channel: candidate => boundedString(candidate, 20) && messageChannels.has(candidate),
      body: candidate => boundedString(candidate),
      variants: () => validVariants,
      selectedVariant: candidate => boundedString(candidate, 20) && messageVariants.has(candidate),
      quality: candidate => boundedString(candidate, 30) && messageQualities.has(candidate),
      readiness: candidate => boundedString(candidate, 2000)
    })
  ) {
    return undefined;
  }
  const result: MessageDraft = {
    id: value.id as string,
    contactId: value.contactId as string,
    ...(decodeOptionalString(context, aggregate, index, value, 'eventId', 256)
      ? { eventId: value.eventId as string }
      : {}),
    reason: value.reason as MessageDraft['reason'],
    status: value.status as MessageDraft['status'],
    channel: value.channel as MessageDraft['channel'],
    body: value.body as string,
    variants: {
      short: (variants as Record<string, unknown>).short as string,
      standard: (variants as Record<string, unknown>).standard as string,
      warm: (variants as Record<string, unknown>).warm as string
    },
    selectedVariant: value.selectedVariant as MessageDraft['selectedVariant'],
    quality: value.quality as MessageDraft['quality'],
    readiness: value.readiness as string
  };
  const occurrenceDate = decodeOptionalString(context, aggregate, index, value, 'occurrenceDate', 10);
  if (occurrenceDate !== undefined) {
    if (validLocalDateKey(occurrenceDate)) {
      result.occurrenceDate = occurrenceDate;
    } else {
      context.issue(aggregate, 'invalid-field', index, 'occurrenceDate');
    }
  }
  for (const field of [
    'scheduledFor',
    'sentAt',
    'approvedAt',
    'approvalExpiresAt',
    'duplicateWarning',
    'duplicateAcknowledgementFingerprint',
    'lastError'
  ] as const) {
    const decoded = decodeOptionalString(
      context,
      aggregate,
      index,
      value,
      field,
      field.includes('At') || field === 'scheduledFor' ? 64 : 2000
    );
    if (decoded !== undefined) {
      result[field] = decoded;
    }
  }
  if (value.scheduledTimeZone !== undefined) {
    const scheduledTimeZone = normalizeScheduleTimeZone(value.scheduledTimeZone);
    if (scheduledTimeZone) result.scheduledTimeZone = scheduledTimeZone;
    else context.issue(aggregate, 'invalid-field', index, 'scheduledTimeZone');
  }
  if (typeof value.duplicateAcknowledged === 'boolean') {
    if (value.duplicateAcknowledged && result.duplicateAcknowledgementFingerprint) {
      result.duplicateAcknowledged = true;
    } else if (value.duplicateAcknowledged) {
      context.issue(aggregate, 'reference-cleared', index, 'duplicateAcknowledged');
    }
  } else if (value.duplicateAcknowledged !== undefined) {
    context.issue(aggregate, 'invalid-field', index, 'duplicateAcknowledged');
  }
  if (value.regenerationFeedback !== undefined) {
    if (
      isRecord(value.regenerationFeedback) &&
      Array.isArray(value.regenerationFeedback.instructions) &&
      value.regenerationFeedback.instructions.length <= 20 &&
      value.regenerationFeedback.instructions.every(item => boundedString(item, 1000, false)) &&
      optionalString(value.regenerationFeedback.customInstruction, 2000) &&
      optionalString(value.regenerationFeedback.previousDraftExcerpt, 2000)
    ) {
      result.regenerationFeedback = {
        instructions: [...value.regenerationFeedback.instructions] as string[],
        ...(value.regenerationFeedback.customInstruction !== undefined
          ? { customInstruction: value.regenerationFeedback.customInstruction as string }
          : {}),
        ...(value.regenerationFeedback.previousDraftExcerpt !== undefined
          ? { previousDraftExcerpt: value.regenerationFeedback.previousDraftExcerpt as string }
          : {})
      };
    } else {
      context.issue(aggregate, 'invalid-field', index, 'regenerationFeedback');
    }
  }
  if (value.emailDeliveryAttempt !== undefined) {
    const attempt = value.emailDeliveryAttempt;
    if (
      isRecord(attempt) &&
      boundedString(attempt.idempotencyKey, 256, false) &&
      (attempt.status === 'Accepted' ||
        attempt.status === 'Sent' ||
        attempt.status === 'Failed' ||
        attempt.status === 'Unknown') &&
      optionalString(attempt.deliveryId, 256) &&
      boundedString(attempt.updatedAt, 64, false) &&
      Number.isFinite(Date.parse(attempt.updatedAt))
    ) {
      result.emailDeliveryAttempt = {
        idempotencyKey: attempt.idempotencyKey,
        status: attempt.status,
        ...(attempt.deliveryId !== undefined ? { deliveryId: attempt.deliveryId as string } : {}),
        updatedAt: attempt.updatedAt
      };
    } else {
      context.issue(aggregate, 'invalid-field', index, 'emailDeliveryAttempt');
    }
  }
  if (result.status === 'Scheduled' && result.scheduledFor && !result.scheduledTimeZone) {
    if (sourceVersion >= PERSISTED_STATE_SCHEMA_VERSION && value.scheduledTimeZone === undefined) {
      context.issue(aggregate, 'missing-entry', index, 'scheduledTimeZone');
    }
    result.status = 'Needs review';
    result.readiness = 'Review legacy schedule time zone';
    result.lastError =
      'The saved schedule has no trusted time-zone identity. Review and approve again to recalculate the intended local send time.';
    delete result.scheduledFor;
    delete result.scheduledTimeZone;
    delete result.approvedAt;
    delete result.approvalExpiresAt;
    delete result.duplicateAcknowledged;
    delete result.duplicateAcknowledgementFingerprint;
  }
  if (result.scheduledTimeZone && !result.scheduledFor) {
    context.issue(aggregate, 'invalid-field', index, 'scheduledTimeZone');
    delete result.scheduledTimeZone;
  }
  return result;
};

const decodeActivity = (value: unknown, index: number, context: DecodeContext): ActivityItem | undefined => {
  const aggregate = 'activity';
  if (
    !isRecord(value) ||
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      type: candidate => boundedString(candidate, 30) && activityTypes.has(candidate),
      title: candidate => boundedString(candidate, 1000, false),
      detail: candidate => boundedString(candidate),
      severity: candidate => boundedString(candidate, 20) && activitySeverities.has(candidate),
      createdAt: candidate => boundedString(candidate, 64, false) && Number.isFinite(Date.parse(candidate))
    })
  ) {
    if (!isRecord(value)) context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  const result: ActivityItem = {
    id: value.id as string,
    type: value.type as ActivityItem['type'],
    title: value.title as string,
    detail: value.detail as string,
    severity: value.severity as ActivityItem['severity'],
    status:
      typeof value.status === 'string' && activityStatuses.has(value.status)
        ? (value.status as ActivityItem['status'])
        : value.severity === 'Info'
          ? 'Completed'
          : 'Open',
    createdAt: value.createdAt as string
  };
  if (value.status !== undefined && !(typeof value.status === 'string' && activityStatuses.has(value.status))) {
    context.issue(aggregate, 'invalid-field', index, 'status');
  }
  if (
    value.resolvedAt !== undefined &&
    boundedString(value.resolvedAt, 64, false) &&
    Number.isFinite(Date.parse(value.resolvedAt)) &&
    result.status === 'Resolved'
  ) {
    result.resolvedAt = value.resolvedAt;
  } else if (value.resolvedAt !== undefined) {
    context.issue(aggregate, 'invalid-field', index, 'resolvedAt');
  }
  if (boundedString(value.targetScreen, 30) && screens.has(value.targetScreen))
    result.targetScreen = value.targetScreen as ActivityItem['targetScreen'];
  else if (value.targetScreen !== undefined) context.issue(aggregate, 'invalid-field', index, 'targetScreen');
  for (const field of ['contactId', 'messageId', 'actionLabel'] as const) {
    const decoded = decodeOptionalString(context, aggregate, index, value, field, field === 'actionLabel' ? 500 : 256);
    if (decoded !== undefined) result[field] = decoded;
  }
  return result;
};

const decodeBackup = (value: unknown, index: number, context: DecodeContext): BackupSnapshot | undefined => {
  const aggregate = 'backups';
  if (
    !isRecord(value) ||
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      createdAt: candidate => boundedString(candidate, 64, false) && Number.isFinite(Date.parse(candidate)),
      recordCount: candidate => finiteNumber(candidate, 0, MAX_PERSISTED_TOTAL_RECORDS, true),
      encrypted: candidate => typeof candidate === 'boolean'
    })
  ) {
    if (!isRecord(value)) context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  return {
    id: value.id as string,
    createdAt: value.createdAt as string,
    recordCount: value.recordCount as number,
    encrypted: value.encrypted as boolean
  };
};

const decodeSetupCheck = (value: unknown, index: number, context: DecodeContext): SetupCheck | undefined => {
  const aggregate = 'setupChecks';
  if (
    !isRecord(value) ||
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      title: candidate => boundedString(candidate, 1000, false),
      status: candidate => candidate === 'Ready' || candidate === 'Needs action' || candidate === 'Optional',
      detail: candidate => boundedString(candidate, 4000),
      action: candidate => boundedString(candidate, 1000)
    })
  ) {
    if (!isRecord(value)) context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  return {
    id: value.id as string,
    title: value.title as string,
    status: value.status as SetupCheck['status'],
    detail: value.detail as string,
    action: value.action as string
  };
};

const decodeReminder = (value: unknown, index: number, context: DecodeContext): ReminderPlan | undefined => {
  const aggregate = 'reminderPlans';
  if (
    !isRecord(value) ||
    invalidRequired(context, aggregate, index, value, {
      id: candidate => boundedString(candidate, 256, false),
      eventId: candidate => boundedString(candidate, 256, false),
      contactId: candidate => boundedString(candidate, 256, false),
      title: candidate => boundedString(candidate, 1000, false),
      body: candidate => boundedString(candidate, 4000),
      triggerAt: candidate => boundedString(candidate, 64, false) && Number.isFinite(Date.parse(candidate))
    })
  ) {
    if (!isRecord(value)) context.exclude(aggregate, 'invalid-record', index);
    return undefined;
  }
  return {
    id: value.id as string,
    eventId: value.eventId as string,
    contactId: value.contactId as string,
    title: value.title as string,
    body: value.body as string,
    triggerAt: value.triggerAt as string
  };
};

type CollectionKey =
  'contacts' | 'events' | 'memories' | 'gifts' | 'messages' | 'activity' | 'backups' | 'setupChecks' | 'reminderPlans';

const decodeCollection = <T>(
  source: Record<string, unknown>,
  key: CollectionKey,
  sourceVersion: number,
  context: DecodeContext,
  decoder: (value: unknown, index: number, context: DecodeContext) => T | undefined
): T[] => {
  const value = source[key];
  if (value === undefined && sourceVersion < PERSISTED_STATE_SCHEMA_VERSION) {
    return [];
  }
  if (!Array.isArray(value)) {
    context.defaultAggregate(key, 'invalid-aggregate');
    return [];
  }
  const bounded = value.slice(0, MAX_PERSISTED_RECORDS_PER_AGGREGATE);
  if (value.length > bounded.length) {
    context.excludedRecordCount += value.length - bounded.length;
    context.issue(key, 'record-limit-exceeded');
  }
  const ids = new Set<string>();
  const decoded: T[] = [];
  bounded.forEach((record, index) => {
    const result = decoder(record, index, context);
    if (!result) return;
    const id = (result as { id?: unknown }).id;
    if (typeof id === 'string') {
      if (ids.has(id)) {
        context.exclude(key, 'duplicate-id', index);
        return;
      }
      ids.add(id);
    }
    decoded.push(result);
  });
  const withinTotalBudget = decoded.slice(0, context.remainingRecordBudget);
  if (withinTotalBudget.length < decoded.length) {
    context.excludedRecordCount += decoded.length - withinTotalBudget.length;
    context.issue(key, 'record-limit-exceeded');
  }
  context.remainingRecordBudget -= withinTotalBudget.length;
  return withinTotalBudget;
};

const singletonRecord = (
  source: Record<string, unknown>,
  key: PersistedAggregateName,
  sourceVersion: number,
  context: DecodeContext
): Record<string, unknown> | undefined => {
  const value = source[key];
  if (isRecord(value)) return value;
  if (value !== undefined || sourceVersion >= PERSISTED_STATE_SCHEMA_VERSION) {
    context.defaultAggregate(key, value === undefined ? 'missing-entry' : 'invalid-aggregate');
  }
  return undefined;
};

const decodeStyleProfile = (
  source: Record<string, unknown>,
  version: number,
  context: DecodeContext
): AppState['styleProfile'] => {
  const fallback = structuredClone(productionInitialState.styleProfile);
  const value = singletonRecord(source, 'styleProfile', version, context);
  if (!value) return fallback;
  const enabledForAiDrafts =
    value.enabledForAiDrafts === undefined
      ? fallback.enabledForAiDrafts
      : typeof value.enabledForAiDrafts === 'boolean'
        ? value.enabledForAiDrafts
        : undefined;
  const commonGreetings =
    value.commonGreetings === undefined
      ? fallback.commonGreetings
      : Array.isArray(value.commonGreetings) &&
          value.commonGreetings.length <= 5 &&
          value.commonGreetings.every(greeting => boundedString(greeting, 80, false))
        ? (value.commonGreetings as string[])
        : undefined;
  const representativePreview =
    value.representativePreview === undefined
      ? fallback.representativePreview
      : boundedString(value.representativePreview, 500)
        ? value.representativePreview
        : undefined;
  if (
    (value.confidence !== 'Not trained' &&
      value.confidence !== 'Starting' &&
      value.confidence !== 'Growing' &&
      value.confidence !== 'Strong') ||
    !boundedString(value.formality, 500) ||
    !boundedString(value.language, 500) ||
    !finiteNumber(value.averageLength, 0, 100_000) ||
    !boundedString(value.emojiUse, 500) ||
    !finiteNumber(value.sampleCount, 0, 1_000_000, true) ||
    enabledForAiDrafts === undefined ||
    commonGreetings === undefined ||
    representativePreview === undefined
  ) {
    context.defaultAggregate('styleProfile', 'invalid-field');
    return fallback;
  }
  return {
    confidence: value.confidence,
    formality: value.formality,
    language: value.language,
    averageLength: value.averageLength,
    emojiUse: value.emojiUse,
    sampleCount: value.sampleCount,
    enabledForAiDrafts,
    commonGreetings,
    representativePreview
  };
};

const decodeGroupDefaults = (value: unknown): AppState['settings']['groupDefaults'] | undefined => {
  if (!isRecord(value)) return undefined;
  const result = {} as AppState['settings']['groupDefaults'];
  for (const group of relationshipGroups) {
    const decoded = decodeGroupPreferences(value[group]);
    if (
      !decoded ||
      decoded.preferredChannel === undefined ||
      decoded.tone === undefined ||
      decoded.checkInCadenceDays === undefined ||
      decoded.automationMode === undefined
    )
      return undefined;
    result[group] = decoded as ContactGroupDefaults;
  }
  return result;
};

const decodeSettings = (
  source: Record<string, unknown>,
  version: number,
  context: DecodeContext
): AppState['settings'] => {
  const fallback = structuredClone(productionInitialState.settings);
  const value = singletonRecord(source, 'settings', version, context);
  if (!value) return fallback;
  const validTime = (candidate: unknown) => {
    if (!boundedString(candidate, 5) || !/^\d{2}:\d{2}$/.test(candidate)) return false;
    const [hour, minute] = candidate.split(':').map(Number);
    return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
  };
  const decodeField = <T>(field: string, candidate: unknown, valid: (item: unknown) => boolean, defaultValue: T): T => {
    if (candidate === undefined && version < PERSISTED_STATE_SCHEMA_VERSION) return defaultValue;
    if (valid(candidate)) return candidate as T;
    context.defaultedAggregates.add('settings');
    context.issue('settings', 'invalid-field', undefined, field);
    return defaultValue;
  };
  const accountMode = decodeField(
    'accountMode',
    value.accountMode,
    candidate => candidate === 'Local' || candidate === 'Google sync',
    fallback.accountMode
  );
  const locale = decodeField(
    'locale',
    value.locale,
    candidate => boundedString(candidate, 30) && locales.has(candidate),
    fallback.locale
  );
  const aiEnabled = decodeField(
    'aiEnabled',
    value.aiEnabled,
    candidate => typeof candidate === 'boolean',
    fallback.aiEnabled
  );
  const notificationsEnabled = decodeField(
    'notificationsEnabled',
    value.notificationsEnabled,
    candidate => typeof candidate === 'boolean',
    fallback.notificationsEnabled
  );
  const smsEnabled = decodeField(
    'smsEnabled',
    value.smsEnabled,
    candidate => typeof candidate === 'boolean',
    fallback.smsEnabled
  );
  const whatsappHandoffEnabled = decodeField(
    'whatsappHandoffEnabled',
    value.whatsappHandoffEnabled,
    candidate => typeof candidate === 'boolean',
    fallback.whatsappHandoffEnabled
  );
  const emailEnabled = decodeField(
    'emailEnabled',
    value.emailEnabled,
    candidate => typeof candidate === 'boolean',
    fallback.emailEnabled
  );
  const biometricLockEnabled = decodeField(
    'biometricLockEnabled',
    value.biometricLockEnabled,
    candidate => typeof candidate === 'boolean',
    fallback.biometricLockEnabled
  );
  const automationMode = decodeField(
    'automationMode',
    value.automationMode,
    candidate => boundedString(candidate, 30) && automationModes.has(candidate),
    fallback.automationMode
  );
  const groupDefaults = decodeField(
    'groupDefaults',
    value.groupDefaults,
    candidate => decodeGroupDefaults(candidate) !== undefined,
    fallback.groupDefaults
  );
  const decodedGroupDefaults = decodeGroupDefaults(groupDefaults) ?? fallback.groupDefaults;
  const quietHours = isRecord(value.quietHours) ? value.quietHours : {};
  const quietStart = decodeField('quietHours.start', quietHours.start, validTime, fallback.quietHours.start);
  const quietEnd = decodeField('quietHours.end', quietHours.end, validTime, fallback.quietHours.end);
  const defaultSendTime = decodeField('defaultSendTime', value.defaultSendTime, validTime, fallback.defaultSendTime);
  const blackouts: ScheduleBlackout[] = [];
  if (Array.isArray(value.blackouts)) {
    value.blackouts.slice(0, MAX_PERSISTED_RECORDS_PER_AGGREGATE).forEach((blackout, index) => {
      const rawChannels = isRecord(blackout) && Array.isArray(blackout.channels) ? blackout.channels : undefined;
      const channels = rawChannels
        ? rawChannels.filter(
            (channel): channel is MessageChannel =>
              channel === 'SMS' || channel === 'WhatsApp' || channel === 'Email' || channel === 'Manual'
          )
        : undefined;
      if (
        isRecord(blackout) &&
        boundedString(blackout.id, 256, false) &&
        boundedString(blackout.label, 1000, false) &&
        boundedString(blackout.startDate, 64, false) &&
        boundedString(blackout.endDate, 64, false) &&
        (blackout.behavior === undefined || blackout.behavior === 'Block' || blackout.behavior === 'Defer') &&
        (blackout.channels === undefined ||
          (channels !== undefined &&
            channels.length > 0 &&
            channels.length === rawChannels?.length &&
            new Set(channels).size === channels.length))
      )
        blackouts.push({
          id: blackout.id,
          label: blackout.label,
          startDate: blackout.startDate,
          endDate: blackout.endDate,
          behavior: blackout.behavior === 'Block' ? 'Block' : 'Defer',
          ...(channels ? { channels } : {})
        });
      else {
        context.defaultedAggregates.add('settings');
        context.issue('settings', 'invalid-field', index, 'blackouts');
      }
    });
  } else if (value.blackouts !== undefined || version >= PERSISTED_STATE_SCHEMA_VERSION) {
    context.defaultedAggregates.add('settings');
    context.issue('settings', 'invalid-field', undefined, 'blackouts');
  }
  return {
    accountMode,
    locale,
    aiEnabled,
    notificationsEnabled,
    smsEnabled,
    whatsappHandoffEnabled,
    emailEnabled,
    biometricLockEnabled,
    automationMode,
    groupDefaults: decodedGroupDefaults,
    quietHours: { start: quietStart, end: quietEnd },
    defaultSendTime,
    blackouts
  };
};

const decodeOnboarding = (
  source: Record<string, unknown>,
  version: number,
  context: DecodeContext
): AppState['onboarding'] => {
  const fallback = structuredClone(productionInitialState.onboarding);
  const value = singletonRecord(source, 'onboarding', version, context);
  if (!value) return fallback;
  const decodeField = <T>(field: string, candidate: unknown, valid: (item: unknown) => boolean, defaultValue: T): T => {
    if (candidate === undefined && version < PERSISTED_STATE_SCHEMA_VERSION) return defaultValue;
    if (valid(candidate)) return candidate as T;
    context.defaultedAggregates.add('onboarding');
    context.issue('onboarding', 'invalid-field', undefined, field);
    return defaultValue;
  };
  const completed = decodeField(
    'completed',
    value.completed,
    candidate => typeof candidate === 'boolean',
    fallback.completed
  );
  const currentStepId = decodeField(
    'currentStepId',
    value.currentStepId,
    candidate => boundedString(candidate, 30) && onboardingSteps.has(candidate),
    fallback.currentStepId
  );
  const selectedGoal = decodeField(
    'selectedGoal',
    value.selectedGoal,
    candidate => boundedString(candidate, 100) && onboardingGoals.has(candidate),
    fallback.selectedGoal
  );
  const completedStepIds = decodeField(
    'completedStepIds',
    value.completedStepIds,
    candidate => decodeStringArray(candidate, onboardingSteps, 9) !== undefined,
    fallback.completedStepIds
  );
  const skippedStepIds = decodeField(
    'skippedStepIds',
    value.skippedStepIds,
    candidate => decodeStringArray(candidate, onboardingSteps, 9) !== undefined,
    fallback.skippedStepIds
  );
  if (value.lastUpdatedAt !== undefined && !boundedString(value.lastUpdatedAt, 64)) {
    context.defaultedAggregates.add('onboarding');
    context.issue('onboarding', 'invalid-field', undefined, 'lastUpdatedAt');
  }
  return {
    completed,
    currentStepId,
    selectedGoal,
    completedStepIds: (decodeStringArray(completedStepIds, onboardingSteps, 9) ??
      fallback.completedStepIds) as AppState['onboarding']['completedStepIds'],
    skippedStepIds: (decodeStringArray(skippedStepIds, onboardingSteps, 9) ??
      fallback.skippedStepIds) as AppState['onboarding']['skippedStepIds'],
    ...(boundedString(value.lastUpdatedAt, 64) ? { lastUpdatedAt: value.lastUpdatedAt } : {})
  };
};

const decodePrivacy = (
  source: Record<string, unknown>,
  version: number,
  context: DecodeContext
): AppState['privacy'] => {
  const fallback = structuredClone(productionInitialState.privacy);
  const value = singletonRecord(source, 'privacy', version, context);
  if (!value) return fallback;
  const rawDecisions = isRecord(value.permissionDecisions) ? value.permissionDecisions : {};
  const decisions = { ...fallback.permissionDecisions };
  for (const capability of permissionCapabilities) {
    const decision = rawDecisions[capability];
    if (decision === undefined && version < PERSISTED_STATE_SCHEMA_VERSION) continue;
    if (!boundedString(decision, 30) || !permissionDecisions.has(decision)) {
      context.issue('privacy', 'invalid-field', undefined, `permissionDecisions.${capability}`);
      continue;
    }
    decisions[capability] = decision as AppState['privacy']['permissionDecisions'][typeof capability];
  }
  const whatsappHandoffConsent =
    typeof value.whatsappHandoffConsent === 'boolean'
      ? value.whatsappHandoffConsent
      : value.whatsappAutomationConsent === true;
  if (
    typeof value.whatsappHandoffConsent !== 'boolean' &&
    value.whatsappAutomationConsent === undefined &&
    version >= 3
  ) {
    context.issue('privacy', 'invalid-field', undefined, 'whatsappHandoffConsent');
  }
  if (value.localDataClearConfirmedAt !== undefined && !boundedString(value.localDataClearConfirmedAt, 64)) {
    context.issue('privacy', 'invalid-field', undefined, 'localDataClearConfirmedAt');
  }
  return {
    permissionDecisions: decisions,
    whatsappHandoffConsent,
    ...(boundedString(value.localDataClearConfirmedAt, 64)
      ? { localDataClearConfirmedAt: value.localDataClearConfirmedAt }
      : {})
  };
};

const decodeAiProvider = (
  source: Record<string, unknown>,
  version: number,
  context: DecodeContext
): AppState['aiProvider'] => {
  const fallback = structuredClone(productionInitialState.aiProvider);
  const value = singletonRecord(source, 'aiProvider', version, context);
  if (!value) return fallback;
  if (value.status !== 'Not configured' && value.status !== 'Ready' && value.status !== 'Error') {
    context.defaultAggregate('aiProvider', 'invalid-field');
    return fallback;
  }
  const result: AppState['aiProvider'] = { status: value.status };
  for (const field of ['lastCheckedAt', 'lastError', 'lastPrivacySummary'] as const) {
    if (boundedString(value[field], field === 'lastCheckedAt' ? 64 : 4000)) result[field] = value[field];
    else if (value[field] !== undefined) context.issue('aiProvider', 'invalid-field', undefined, field);
  }
  const observation = value.lastObservation;
  if (observation !== undefined) {
    const variantLengths = observation && isRecord(observation) ? observation.variantLengths : undefined;
    const validVariantLengths =
      variantLengths === undefined ||
      (isRecord(variantLengths) &&
        finiteNumber(variantLengths.short, 0, MAX_PERSISTED_FIELD_LENGTH, true) &&
        finiteNumber(variantLengths.standard, 0, MAX_PERSISTED_FIELD_LENGTH, true) &&
        finiteNumber(variantLengths.warm, 0, MAX_PERSISTED_FIELD_LENGTH, true));
    if (
      isRecord(observation) &&
      observation.redacted === true &&
      typeof observation.ok === 'boolean' &&
      finiteNumber(observation.durationMs, 0, 86_400_000) &&
      boundedString(observation.reason, 30) &&
      composerReasons.has(observation.reason) &&
      boundedString(observation.contactLanguage, 20) &&
      contactLanguages.has(observation.contactLanguage) &&
      finiteNumber(observation.includedMemoryCount, 0, MAX_PERSISTED_RECORDS_PER_AGGREGATE, true) &&
      finiteNumber(observation.excludedPrivateMemoryCount, 0, MAX_PERSISTED_RECORDS_PER_AGGREGATE, true) &&
      finiteNumber(observation.includedPriorMessageCount, 0, MAX_PERSISTED_RECORDS_PER_AGGREGATE, true) &&
      optionalString(observation.errorKind, 100) &&
      validVariantLengths
    )
      result.lastObservation = {
        redacted: true,
        ok: observation.ok,
        durationMs: observation.durationMs,
        reason: observation.reason as AiProviderObservation['reason'],
        contactLanguage: observation.contactLanguage as AiProviderObservation['contactLanguage'],
        includedMemoryCount: observation.includedMemoryCount,
        excludedPrivateMemoryCount: observation.excludedPrivateMemoryCount,
        includedPriorMessageCount: observation.includedPriorMessageCount,
        ...(observation.errorKind !== undefined ? { errorKind: observation.errorKind } : {}),
        ...(isRecord(variantLengths)
          ? {
              variantLengths: {
                short: variantLengths.short as number,
                standard: variantLengths.standard as number,
                warm: variantLengths.warm as number
              }
            }
          : {})
      };
    else context.issue('aiProvider', 'invalid-field', undefined, 'lastObservation');
  }
  return result;
};

const decodeEmailDelivery = (
  source: Record<string, unknown>,
  version: number,
  context: DecodeContext
): AppState['emailDelivery'] => {
  const fallback = structuredClone(productionInitialState.emailDelivery);
  const value = singletonRecord(source, 'emailDelivery', version, context);
  if (!value) return fallback;
  if (value.status !== 'Not configured' && value.status !== 'Ready' && value.status !== 'Error') {
    context.defaultAggregate('emailDelivery', 'invalid-field');
    return fallback;
  }
  const result: AppState['emailDelivery'] = { status: value.status };
  for (const field of ['senderEmail', 'lastCheckedAt', 'lastError'] as const) {
    if (boundedString(value[field], field === 'lastError' ? 4000 : 512)) result[field] = value[field];
    else if (value[field] !== undefined) context.issue('emailDelivery', 'invalid-field', undefined, field);
  }
  return result;
};

const decodeCalendarSync = (
  source: Record<string, unknown>,
  version: number,
  context: DecodeContext
): AppState['calendarSync'] => {
  const fallback = structuredClone(productionInitialState.calendarSync);
  const value = singletonRecord(source, 'calendarSync', version, context);
  if (!value) return fallback;
  if (
    !finiteNumber(value.exportedCount, 0, MAX_PERSISTED_TOTAL_RECORDS, true) ||
    !finiteNumber(value.importedCount, 0, MAX_PERSISTED_TOTAL_RECORDS, true)
  ) {
    context.defaultAggregate('calendarSync', 'invalid-field');
    return fallback;
  }
  for (const [field, maximum] of [
    ['lastExportedAt', 64],
    ['lastImportedAt', 64],
    ['lastError', 4000]
  ] as const) {
    if (value[field] !== undefined && !boundedString(value[field], maximum)) {
      context.issue('calendarSync', 'invalid-field', undefined, field);
    }
  }
  return {
    exportedCount: value.exportedCount,
    importedCount: value.importedCount,
    ...(boundedString(value.lastExportedAt, 64) ? { lastExportedAt: value.lastExportedAt } : {}),
    ...(boundedString(value.lastImportedAt, 64) ? { lastImportedAt: value.lastImportedAt } : {}),
    ...(boundedString(value.lastError, 4000) ? { lastError: value.lastError } : {})
  };
};

const decodeStorageHealth = (value: unknown): PersistenceStorageHealth | undefined => {
  if (!isRecord(value)) return undefined;
  if (
    (value.status !== 'Missing' && value.status !== 'Ready' && value.status !== 'Corrupt') ||
    (value.storageFormat !== 'Missing' &&
      value.storageFormat !== 'Direct envelope' &&
      value.storageFormat !== 'Legacy chunked' &&
      value.storageFormat !== 'Normalized' &&
      value.storageFormat !== 'Encrypted entity repository' &&
      value.storageFormat !== 'Corrupt') ||
    !finiteNumber(value.payloadBytes, 0, 100_000_000, true) ||
    !finiteNumber(value.entryCount, 0, 100_000, true) ||
    !finiteNumber(value.chunkCount, 0, 1_000_000, true) ||
    !finiteNumber(value.largestEntryBytes, 0, 100_000_000, true) ||
    !optionalString(value.savedAt, 64) ||
    (value.envelopeVersion !== undefined && !finiteNumber(value.envelopeVersion, 1, 1000, true)) ||
    !optionalString(value.lastVerifiedAt, 64) ||
    !optionalString(value.issue, 4000)
  )
    return undefined;
  return {
    status: value.status,
    storageFormat: value.storageFormat,
    payloadBytes: value.payloadBytes,
    entryCount: value.entryCount,
    chunkCount: value.chunkCount,
    largestEntryBytes: value.largestEntryBytes,
    ...(value.savedAt !== undefined ? { savedAt: value.savedAt as string } : {}),
    ...(value.envelopeVersion !== undefined ? { envelopeVersion: value.envelopeVersion as number } : {}),
    ...(value.lastVerifiedAt !== undefined ? { lastVerifiedAt: value.lastVerifiedAt as string } : {}),
    ...(value.issue !== undefined ? { issue: value.issue as string } : {})
  };
};

const decodePersistence = (
  source: Record<string, unknown>,
  version: number,
  context: DecodeContext
): AppState['persistence'] => {
  const value = singletonRecord(source, 'persistence', version, context);
  if (!value) return { status: 'Ready' };
  if (value.status !== 'Loading' && value.status !== 'Ready' && value.status !== 'Saving' && value.status !== 'Error') {
    context.issue('persistence', 'invalid-field', undefined, 'status');
  }
  const result: AppState['persistence'] = { status: 'Ready' };
  if (boundedString(value.lastSavedAt, 64)) result.lastSavedAt = value.lastSavedAt;
  else if (value.lastSavedAt !== undefined) context.issue('persistence', 'invalid-field', undefined, 'lastSavedAt');
  if (boundedString(value.error, 4000)) result.error = value.error;
  else if (value.error !== undefined) context.issue('persistence', 'invalid-field', undefined, 'error');
  if (value.storageHealth !== undefined) {
    const health = decodeStorageHealth(value.storageHealth);
    if (health) result.storageHealth = health;
    else context.issue('persistence', 'invalid-field', undefined, 'storageHealth');
  }
  return result;
};

const clearStaleOptionalReferences = (state: AppState, context: DecodeContext): AppState => {
  const contactIds = new Set(state.contacts.map(contact => contact.id));

  const events = state.events.filter((event, index) => {
    if (contactIds.has(event.contactId)) return true;
    context.exclude('events', 'missing-reference', index, 'contactId');
    return false;
  });
  const validEventIds = new Set(events.map(event => event.id));
  const memories = state.memories.filter((memory, index) => {
    if (contactIds.has(memory.contactId)) return true;
    context.exclude('memories', 'missing-reference', index, 'contactId');
    return false;
  });
  const gifts = state.gifts.filter((gift, index) => {
    if (contactIds.has(gift.contactId)) return true;
    context.exclude('gifts', 'missing-reference', index, 'contactId');
    return false;
  });
  const messages = state.messages.flatMap((message, index) => {
    if (!contactIds.has(message.contactId)) {
      context.exclude('messages', 'missing-reference', index, 'contactId');
      return [];
    }
    if (message.eventId && !validEventIds.has(message.eventId)) {
      context.issue('messages', 'reference-cleared', index, 'eventId');
      const { eventId: _eventId, ...withoutEvent } = message;
      return [withoutEvent];
    }
    return [message];
  });
  const validMessageIds = new Set(messages.map(message => message.id));
  const reminderPlans = state.reminderPlans.filter((plan, index) => {
    const event = events.find(candidate => candidate.id === plan.eventId);
    if (contactIds.has(plan.contactId) && event?.contactId === plan.contactId) return true;
    context.exclude('reminderPlans', 'missing-reference', index, event ? 'contactId' : 'eventId');
    return false;
  });
  const activity = state.activity.map((item, index) => {
    let result = item;
    if (item.contactId && !contactIds.has(item.contactId)) {
      context.issue('activity', 'reference-cleared', index, 'contactId');
      const { contactId: _contactId, resolvedAt: _resolvedAt, ...withoutContact } = result;
      result = { ...withoutContact, status: 'Obsolete' };
    }
    if (result.messageId && !validMessageIds.has(result.messageId)) {
      context.issue('activity', 'reference-cleared', index, 'messageId');
      const { messageId: _messageId, resolvedAt: _resolvedAt, ...withoutMessage } = result;
      result = { ...withoutMessage, status: 'Obsolete' };
    }
    return result;
  });

  let selectedContactId = state.selectedContactId;
  if (selectedContactId && !contactIds.has(selectedContactId)) {
    context.issue('shell', 'reference-cleared', undefined, 'selectedContactId');
    selectedContactId = undefined;
  }
  let selectedMessageId = state.selectedMessageId;
  if (selectedMessageId && !validMessageIds.has(selectedMessageId)) {
    context.issue('shell', 'reference-cleared', undefined, 'selectedMessageId');
    selectedMessageId = undefined;
  }
  let selectedEventId = state.selectedEventId;
  if (selectedEventId && !validEventIds.has(selectedEventId)) {
    context.issue('shell', 'reference-cleared', undefined, 'selectedEventId');
    selectedEventId = undefined;
  }

  return {
    ...state,
    ...(selectedContactId ? { selectedContactId } : { selectedContactId: undefined }),
    ...(selectedEventId ? { selectedEventId } : { selectedEventId: undefined }),
    ...(selectedMessageId ? { selectedMessageId } : { selectedMessageId: undefined }),
    events,
    memories,
    gifts,
    messages,
    activity,
    reminderPlans
  };
};

export const decodePersistedState = (state: unknown, sourceVersion: number): PersistedStateDecodeResult => {
  if (!isRecord(state)) {
    throw new PersistedStateValidationError(1);
  }
  if (!finiteNumber(sourceVersion, 1, PERSISTED_STATE_SCHEMA_VERSION, true)) {
    throw new Error('Persisted state schema version is not supported.');
  }
  const context = new DecodeContext();
  const defaults = structuredClone(productionInitialState);

  const contacts = decodeCollection(state, 'contacts', sourceVersion, context, decodeContact);
  const events = decodeCollection(state, 'events', sourceVersion, context, decodeEvent);
  const memories = decodeCollection(state, 'memories', sourceVersion, context, decodeMemory);
  const gifts = decodeCollection(state, 'gifts', sourceVersion, context, decodeGift);
  const messages = decodeCollection(state, 'messages', sourceVersion, context, (value, index, decodeContext) =>
    decodeMessage(value, index, decodeContext, sourceVersion)
  );
  const activity = decodeCollection(state, 'activity', sourceVersion, context, decodeActivity);
  const backups = decodeCollection(state, 'backups', sourceVersion, context, decodeBackup);
  const setupChecks = decodeCollection(state, 'setupChecks', sourceVersion, context, decodeSetupCheck);
  const reminderPlans = decodeCollection(state, 'reminderPlans', sourceVersion, context, decodeReminder);

  const activeScreen =
    boundedString(state.activeScreen, 30) && screens.has(state.activeScreen)
      ? (state.activeScreen as AppState['activeScreen'])
      : defaults.activeScreen;
  if (activeScreen !== state.activeScreen && (state.activeScreen !== undefined || sourceVersion >= 3)) {
    context.issue('shell', 'invalid-field', undefined, 'activeScreen');
  }
  const searchQuery = boundedString(state.searchQuery, 1000) ? state.searchQuery : defaults.searchQuery;
  if (searchQuery !== state.searchQuery && (state.searchQuery !== undefined || sourceVersion >= 3)) {
    context.issue('shell', 'invalid-field', undefined, 'searchQuery');
  }
  if (state.selectedContactId !== undefined && !boundedString(state.selectedContactId, 256, false)) {
    context.issue('shell', 'invalid-field', undefined, 'selectedContactId');
  }
  if (state.selectedEventId !== undefined && !boundedString(state.selectedEventId, 256, false)) {
    context.issue('shell', 'invalid-field', undefined, 'selectedEventId');
  }
  if (state.selectedMessageId !== undefined && !boundedString(state.selectedMessageId, 256, false)) {
    context.issue('shell', 'invalid-field', undefined, 'selectedMessageId');
  }

  let decoded: AppState = {
    activeScreen,
    ...(boundedString(state.selectedContactId, 256, false) ? { selectedContactId: state.selectedContactId } : {}),
    ...(boundedString(state.selectedEventId, 256, false) ? { selectedEventId: state.selectedEventId } : {}),
    ...(boundedString(state.selectedMessageId, 256, false) ? { selectedMessageId: state.selectedMessageId } : {}),
    searchQuery,
    contacts,
    events,
    memories,
    gifts,
    messages,
    activity,
    backups,
    setupChecks,
    reminderPlans,
    styleProfile: decodeStyleProfile(state, sourceVersion, context),
    settings: decodeSettings(state, sourceVersion, context),
    onboarding: decodeOnboarding(state, sourceVersion, context),
    privacy: decodePrivacy(state, sourceVersion, context),
    aiProvider: decodeAiProvider(state, sourceVersion, context),
    emailDelivery: decodeEmailDelivery(state, sourceVersion, context),
    calendarSync: decodeCalendarSync(state, sourceVersion, context),
    persistence: decodePersistence(state, sourceVersion, context)
  };
  decoded = clearStaleOptionalReferences(decoded, context);

  return {
    state: decoded,
    issueCount: context.issueCount,
    excludedRecordCount: context.excludedRecordCount,
    defaultedAggregates: [...context.defaultedAggregates].sort(),
    issues: context.issues
  };
};

export const assertValidPersistedState = (state: unknown, sourceVersion: number): AppState => {
  const result = decodePersistedState(state, sourceVersion);
  if (result.issueCount > 0) {
    throw new PersistedStateValidationError(result.issueCount);
  }
  return result.state;
};

export const createPersistenceRecoveryManifest = (
  result: Pick<PersistedStateDecodeResult, 'issueCount' | 'excludedRecordCount' | 'defaultedAggregates' | 'issues'>,
  sourceVersion: number,
  recoveredAt = new Date().toISOString()
): PersistenceRecoveryManifest => ({
  format: 'relateai.persistence-recovery',
  version: 1,
  redacted: true,
  recoveredAt,
  sourceVersion,
  outcome: 'selective',
  issueCount: result.issueCount,
  excludedRecordCount: result.excludedRecordCount,
  defaultedAggregates: [...result.defaultedAggregates],
  issues: result.issues.map(issue => ({ ...issue })),
  issuesTruncated: result.issueCount > result.issues.length
});
