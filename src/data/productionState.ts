import { defaultRelationshipGroupDefaults } from '../domain/contactPreferences';
import type { AppState } from '../domain/types';
import { resolveLocale } from '../i18n/i18n';

/**
 * The only state used for a fresh production install and persistence recovery.
 * Rich relationship data belongs in test fixtures, never in the application bundle's
 * initialization path.
 */
export const productionInitialState: AppState = {
  activeScreen: 'onboarding',
  selectedContactId: undefined,
  selectedEventId: undefined,
  selectedMessageId: undefined,
  searchQuery: '',
  contacts: [],
  events: [],
  memories: [],
  gifts: [],
  messages: [],
  activity: [],
  styleProfile: {
    confidence: 'Not trained',
    formality: 'Not trained',
    language: 'Not trained',
    averageLength: 0,
    emojiUse: 'Unknown',
    sampleCount: 0,
    enabledForAiDrafts: true,
    commonGreetings: [],
    representativePreview: ''
  },
  backups: [],
  settings: {
    accountMode: 'Local',
    locale: 'en-IN',
    aiEnabled: true,
    notificationsEnabled: true,
    smsEnabled: true,
    whatsappHandoffEnabled: true,
    emailEnabled: false,
    biometricLockEnabled: false,
    automationMode: 'Always ask',
    groupDefaults: defaultRelationshipGroupDefaults,
    quietHours: {
      start: '22:00',
      end: '08:00'
    },
    defaultSendTime: '09:00',
    blackouts: []
  },
  onboarding: {
    completed: false,
    currentStepId: 'intro',
    selectedGoal: 'Reminders first',
    completedStepIds: [],
    skippedStepIds: [],
    lastUpdatedAt: undefined
  },
  privacy: {
    permissionDecisions: {
      Contacts: 'Not requested',
      Notifications: 'Not requested',
      SMS: 'Not requested',
      Calendar: 'Not requested',
      'Biometric lock': 'Not requested',
      'AI provider': 'Not requested',
      'Email provider': 'Not requested',
      'WhatsApp handoff': 'Not requested',
      'Backup export': 'Not requested'
    },
    whatsappHandoffConsent: false,
    localDataClearConfirmedAt: undefined
  },
  aiProvider: {
    status: 'Not configured'
  },
  emailDelivery: {
    status: 'Not configured'
  },
  setupChecks: [],
  reminderPlans: [],
  calendarSync: {
    exportedCount: 0,
    importedCount: 0
  },
  persistence: {
    status: 'Loading'
  }
};

const deviceLocale = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().locale;
  } catch {
    return undefined;
  }
};

export const createProductionInitialState = (locale: string | undefined = deviceLocale()): AppState => {
  const state = structuredClone(productionInitialState);
  state.settings.locale = resolveLocale(locale);
  return state;
};
