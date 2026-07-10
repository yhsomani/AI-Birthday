export type Screen =
  | 'onboarding'
  | 'home'
  | 'events'
  | 'eventForm'
  | 'messages'
  | 'contacts'
  | 'more'
  | 'contactDetail'
  | 'chatHistory'
  | 'wishPreview'
  | 'manualComposer';

export type RelationshipGroup = 'Family' | 'Friends' | 'Work' | 'Close friends' | 'Other';

export type EventType =
  | 'Birthday'
  | 'Anniversary'
  | 'Work anniversary'
  | 'Custom'
  | 'Graduation'
  | 'Holiday'
  | 'Revival'
  | 'Follow-up';

export type MessageChannel = 'SMS' | 'WhatsApp' | 'Email' | 'Manual';

export type MessageStatus =
  | 'Needs review'
  | 'Scheduled'
  | 'Blocked'
  | 'Sent'
  | 'Failed'
  | 'Delivery pending'
  | 'Delivery unknown'
  | 'Rejected'
  | 'Draft';

export type AutomationMode = 'Always ask' | 'Smart approve' | 'VIP approve' | 'Fully auto';
export type AccountMode = 'Local' | 'Google sync';
export type OnboardingStepId =
  | 'intro'
  | 'account'
  | 'contacts'
  | 'notifications'
  | 'ai'
  | 'style'
  | 'channels'
  | 'backup'
  | 'finish';
export type OnboardingGoal = 'Reminders first' | 'AI wishes' | 'Manual relationship manager' | 'Full setup';
export type PermissionCapability =
  | 'Contacts'
  | 'Notifications'
  | 'SMS'
  | 'Calendar'
  | 'Biometric lock'
  | 'AI provider'
  | 'Email provider'
  | 'WhatsApp handoff'
  | 'Backup export';
export type PermissionDecision = 'Not requested' | 'Granted' | 'Denied' | 'Unavailable';
export type SystemPermissionCapability =
  | 'Contacts'
  | 'Notifications'
  | 'Calendar'
  | 'Biometric lock';
export type SystemAuthorization =
  | 'granted'
  | 'limited'
  | 'denied'
  | 'restricted'
  | 'undetermined'
  | 'not-enrolled'
  | 'unavailable';
export type PermissionUserIntent = 'not-expressed' | 'allow' | 'decline';
export type PermissionPromptOutcome =
  | 'granted'
  | 'limited'
  | 'denied'
  | 'restricted'
  | 'undetermined';

/**
 * Consent history and live OS authorization are intentionally separate. A live
 * refresh must never erase what the user asked for or the last prompt result.
 */
export interface PermissionAuthorizationRecord {
  capability: SystemPermissionCapability;
  userIntent: PermissionUserIntent;
  userIntentUpdatedAt?: string;
  lastPromptOutcome?: PermissionPromptOutcome;
  lastPromptAt?: string;
  systemAuthorization: SystemAuthorization;
  lastKnownAuthorization?: Exclude<SystemAuthorization, 'unavailable'>;
  systemCheckedAt?: string;
  canAskAgain?: boolean;
  platformStatus?: string;
  queryIssue?: 'query-failed' | 'unsupported-status';
}

export interface ScheduleBlackout {
  id: string;
  label: string;
  startDate: string;
  endDate: string;
}

export interface OnboardingState {
  completed: boolean;
  currentStepId: OnboardingStepId;
  selectedGoal: OnboardingGoal;
  completedStepIds: OnboardingStepId[];
  skippedStepIds: OnboardingStepId[];
  lastUpdatedAt?: string;
}

export interface PrivacyState {
  permissionDecisions: Record<PermissionCapability, PermissionDecision>;
  permissionRecords?: Partial<Record<SystemPermissionCapability, PermissionAuthorizationRecord>>;
  whatsappHandoffConsent: boolean;
  localDataClearConfirmedAt?: string;
}

export type SupportedLocale = 'en-IN' | 'hi-IN' | 'en-Hinglish';

export type Tone =
  | 'Warm'
  | 'Respectful'
  | 'Playful'
  | 'Concise'
  | 'Formal'
  | 'Hinglish'
  | 'No emoji';

export type MemoryCategory = 'General' | 'Private' | 'Preference' | 'Event' | 'Gift' | 'Milestone';
export type GiftCategory = 'Experience' | 'Food' | 'Books' | 'Wellness' | 'Personal' | 'Other';

export type ComposerReason =
  | 'Birthday'
  | 'Check-in'
  | 'Thanks'
  | 'Congratulations'
  | 'Apology'
  | 'Follow-up'
  | 'Custom';

export interface Contact {
  id: string;
  name: string;
  relationship: string;
  group: RelationshipGroup;
  phone?: string;
  email?: string;
  preferredChannel: MessageChannel;
  language: 'English' | 'Hinglish' | 'Hindi';
  tone: Tone[];
  healthScore: number;
  isVip: boolean;
  dnd: boolean;
  checkInCadenceDays: number;
  preferenceOverrides?: ContactPreferenceOverrides;
  lastContactedAt?: string;
  checkInSnoozedUntil?: string;
  notesSummary: string;
  annualGiftBudget: number;
}

export interface ContactGroupDefaults {
  preferredChannel: MessageChannel;
  tone: Tone[];
  checkInCadenceDays: number;
  automationMode: AutomationMode;
}

export type ContactPreferenceOverrides = Partial<ContactGroupDefaults>;

export type RelationshipGroupDefaults = Record<RelationshipGroup, ContactGroupDefaults>;

export interface ImportedContactRecord {
  sourceId: string;
  name: string;
  phone?: string;
  email?: string;
  birthday?: string;
  relationship?: string;
}

export type LeapDayPolicy = 'February 28' | 'March 1';

export interface YearlyOccasionRecurrence {
  frequency: 'Yearly';
  month: number;
  day: number;
  originalYear?: number;
  leapDayPolicy: LeapDayPolicy;
}

export interface RelationshipEvent {
  id: string;
  contactId: string;
  type: EventType;
  label: string;
  /** Reference occurrence retained for backward-compatible backup and sync readers. */
  date: string;
  /** Local calendar recurrence for birthdays and anniversaries. */
  recurrence?: YearlyOccasionRecurrence;
  verified: boolean;
  source: 'Imported' | 'Manual' | 'AI suggested';
  checklist: EventChecklistItem[];
}

export interface EventChecklistItem {
  id: string;
  label: string;
  done: boolean;
}

export interface MemoryNote {
  id: string;
  contactId: string;
  category: MemoryCategory;
  body: string;
  pinned: boolean;
  createdAt: string;
}

export interface GiftRecord {
  id: string;
  contactId: string;
  name: string;
  category: GiftCategory;
  occasion: string;
  cost: number;
  year: number;
  feedback: 'Liked' | 'Disliked' | 'Unknown';
  notes: string;
}

export interface MessageDraft {
  id: string;
  contactId: string;
  eventId?: string;
  reason: ComposerReason;
  status: MessageStatus;
  channel: MessageChannel;
  body: string;
  variants: Record<'short' | 'standard' | 'warm', string>;
  selectedVariant: 'short' | 'standard' | 'warm';
  scheduledFor?: string;
  sentAt?: string;
  approvedAt?: string;
  approvalExpiresAt?: string;
  quality: 'AI draft' | 'Template fallback' | 'Needs more context';
  readiness: string;
  regenerationFeedback?: MessageRegenerationFeedback;
  duplicateWarning?: string;
  duplicateAcknowledged?: boolean;
  lastError?: string;
  emailDeliveryAttempt?: {
    idempotencyKey: string;
    status: 'Accepted' | 'Sent' | 'Failed' | 'Unknown';
    deliveryId?: string;
    updatedAt: string;
  };
}

export interface MessageRegenerationFeedback {
  instructions: string[];
  customInstruction?: string;
  previousDraftExcerpt?: string;
}

export type AiProviderState = {
  status: 'Not configured' | 'Ready' | 'Error';
  lastCheckedAt?: string;
  lastError?: string;
  lastPrivacySummary?: string;
  lastObservation?: AiProviderObservation;
};

export interface AiProviderObservation {
  redacted: true;
  ok: boolean;
  durationMs: number;
  reason: ComposerReason;
  contactLanguage: Contact['language'];
  includedMemoryCount: number;
  excludedPrivateMemoryCount: number;
  includedPriorMessageCount: number;
  errorKind?: string;
  variantLengths?: Record<'short' | 'standard' | 'warm', number>;
}

export type EmailDeliveryState = {
  status: 'Not configured' | 'Ready' | 'Error';
  senderEmail?: string;
  lastCheckedAt?: string;
  lastError?: string;
};

export interface ActivityItem {
  id: string;
  type: 'Message' | 'Event' | 'Contact' | 'Backup' | 'Setup' | 'AI' | 'Gift' | 'Memory' | 'Analytics';
  title: string;
  detail: string;
  severity: 'Info' | 'Warning' | 'Error';
  createdAt: string;
  targetScreen?: Screen;
  contactId?: string;
  messageId?: string;
  actionLabel?: string;
}

export interface StyleProfile {
  confidence: 'Not trained' | 'Starting' | 'Growing' | 'Strong';
  formality: string;
  language: string;
  averageLength: number;
  emojiUse: string;
  sampleCount: number;
}

export interface SetupCheck {
  id: string;
  title: string;
  status: 'Ready' | 'Needs action' | 'Optional';
  detail: string;
  action: string;
}

export interface ReminderPlan {
  id: string;
  eventId: string;
  contactId: string;
  title: string;
  body: string;
  triggerAt: string;
}

export interface CalendarExportEntry {
  id: string;
  eventId: string;
  title: string;
  startDate: string;
  endDate: string;
  notes: string;
  allDay?: boolean;
  recurrenceRule?: {
    frequency: 'yearly';
  };
}

export interface CalendarImportCandidate {
  sourceId: string;
  title: string;
  startDate: string;
  notes?: string;
}

export interface BackupSnapshot {
  id: string;
  createdAt: string;
  recordCount: number;
  encrypted: boolean;
}

export type PersistenceStorageFormat = 'Missing' | 'Direct envelope' | 'Legacy chunked' | 'Normalized' | 'Corrupt';

export interface PersistenceStorageHealth {
  status: 'Missing' | 'Ready' | 'Corrupt';
  storageFormat: PersistenceStorageFormat;
  payloadBytes: number;
  entryCount: number;
  chunkCount: number;
  largestEntryBytes: number;
  savedAt?: string;
  envelopeVersion?: number;
  lastVerifiedAt?: string;
  issue?: string;
}

export interface SettingsState {
  accountMode: AccountMode;
  locale: SupportedLocale;
  aiEnabled: boolean;
  notificationsEnabled: boolean;
  smsEnabled: boolean;
  whatsappHandoffEnabled: boolean;
  emailEnabled: boolean;
  biometricLockEnabled: boolean;
  automationMode: AutomationMode;
  groupDefaults: RelationshipGroupDefaults;
  quietHours: {
    start: string;
    end: string;
  };
  blackouts: ScheduleBlackout[];
}

export interface AppState {
  activeScreen: Screen;
  selectedContactId?: string;
  selectedMessageId?: string;
  contacts: Contact[];
  events: RelationshipEvent[];
  memories: MemoryNote[];
  gifts: GiftRecord[];
  messages: MessageDraft[];
  activity: ActivityItem[];
  styleProfile: StyleProfile;
  backups: BackupSnapshot[];
  settings: SettingsState;
  onboarding: OnboardingState;
  privacy: PrivacyState;
  aiProvider: AiProviderState;
  emailDelivery: EmailDeliveryState;
  searchQuery: string;
  setupChecks: SetupCheck[];
  reminderPlans: ReminderPlan[];
  calendarSync: {
    lastExportedAt?: string;
    lastImportedAt?: string;
    exportedCount: number;
    importedCount: number;
    lastError?: string;
  };
  persistence: {
    status: 'Loading' | 'Ready' | 'Saving' | 'Error';
    lastSavedAt?: string;
    error?: string;
    storageHealth?: PersistenceStorageHealth;
  };
}
