export type Screen =
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
  | 'Rejected'
  | 'Draft';

export type AutomationMode = 'Always ask' | 'Smart approve' | 'VIP approve' | 'Fully auto';

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
  lastContactedAt?: string;
  notesSummary: string;
  annualGiftBudget: number;
}

export interface ImportedContactRecord {
  sourceId: string;
  name: string;
  phone?: string;
  email?: string;
  birthday?: string;
  relationship?: string;
}

export interface RelationshipEvent {
  id: string;
  contactId: string;
  type: EventType;
  label: string;
  date: string;
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
  quality: 'AI draft' | 'Template fallback' | 'Needs more context';
  readiness: string;
  duplicateWarning?: string;
  duplicateAcknowledged?: boolean;
  lastError?: string;
}

export type AiProviderState = {
  status: 'Not configured' | 'Ready' | 'Error';
  lastCheckedAt?: string;
  lastError?: string;
  lastPrivacySummary?: string;
};

export type EmailDeliveryState = {
  status: 'Not configured' | 'Ready' | 'Error';
  senderEmail?: string;
  lastCheckedAt?: string;
  lastError?: string;
};

export interface ActivityItem {
  id: string;
  type: 'Message' | 'Event' | 'Contact' | 'Backup' | 'Setup' | 'AI' | 'Gift' | 'Memory';
  title: string;
  detail: string;
  severity: 'Info' | 'Warning' | 'Error';
  createdAt: string;
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

export interface SettingsState {
  accountMode: 'Local' | 'Google sync';
  locale: SupportedLocale;
  aiEnabled: boolean;
  notificationsEnabled: boolean;
  smsEnabled: boolean;
  whatsappHandoffEnabled: boolean;
  emailEnabled: boolean;
  biometricLockEnabled: boolean;
  automationMode: AutomationMode;
  quietHours: {
    start: string;
    end: string;
  };
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
  };
}
