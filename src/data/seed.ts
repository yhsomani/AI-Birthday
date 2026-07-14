import { defaultRelationshipGroupDefaults } from '../domain/contactPreferences';
import type { AppState } from '../domain/types';

const today = new Date('2026-07-09T10:00:00.000Z');

const isoFromNow = (days: number) => {
  const next = new Date(today);
  next.setDate(today.getDate() + days);
  return next.toISOString();
};

export const initialState: AppState = {
  activeScreen: 'onboarding',
  selectedContactId: undefined,
  selectedMessageId: undefined,
  searchQuery: '',
  contacts: [
    {
      id: 'c-asha',
      name: 'Asha Mehra',
      relationship: 'Sister',
      group: 'Family',
      phone: '+919876543210',
      preferredChannel: 'SMS',
      language: 'Hinglish',
      tone: ['Warm', 'Playful', 'Hinglish'],
      healthScore: 86,
      isVip: true,
      dnd: false,
      checkInCadenceDays: 30,
      lastContactedAt: isoFromNow(-16),
      notesSummary: 'Likes mango lassi, short warm messages, and family jokes.',
      annualGiftBudget: 8000
    },
    {
      id: 'c-rajesh',
      name: 'Rajesh Nair',
      relationship: 'Manager',
      group: 'Work',
      email: 'rajesh@example.com',
      preferredChannel: 'Email',
      language: 'English',
      tone: ['Respectful', 'Formal', 'Concise'],
      healthScore: 64,
      isVip: false,
      dnd: false,
      checkInCadenceDays: 60,
      lastContactedAt: isoFromNow(-72),
      notesSummary: 'Prefers concise professional notes and no emoji.',
      annualGiftBudget: 2500
    },
    {
      id: 'c-mira',
      name: 'Mira Shah',
      relationship: 'College friend',
      group: 'Close friends',
      phone: '+919812345678',
      preferredChannel: 'Manual',
      language: 'English',
      tone: ['Warm', 'Playful'],
      healthScore: 48,
      isVip: false,
      dnd: false,
      checkInCadenceDays: 45,
      lastContactedAt: isoFromNow(-94),
      notesSummary: 'Recently moved cities; loves books and coffee.',
      annualGiftBudget: 5000
    }
  ],
  events: [
    {
      id: 'e-asha-bday',
      contactId: 'c-asha',
      type: 'Birthday',
      label: 'Asha birthday',
      date: isoFromNow(5),
      verified: true,
      source: 'Imported',
      checklist: [
        { id: 'confirm-date', label: 'Confirm date', done: true },
        { id: 'improve-context', label: 'Add one personal memory', done: true },
        { id: 'write-wish', label: 'Write or review wish', done: false },
        { id: 'choose-channel', label: 'Choose send channel', done: true }
      ]
    },
    {
      id: 'e-rajesh-work',
      contactId: 'c-rajesh',
      type: 'Work anniversary',
      label: 'Rajesh work anniversary',
      date: isoFromNow(18),
      verified: true,
      source: 'Manual',
      checklist: [
        { id: 'confirm-date', label: 'Confirm date', done: true },
        { id: 'write-wish', label: 'Prepare concise note', done: false },
        { id: 'choose-channel', label: 'Confirm email route', done: false }
      ]
    },
    {
      id: 'e-mira-checkin',
      contactId: 'c-mira',
      type: 'Follow-up',
      label: 'Check in after move',
      date: isoFromNow(2),
      verified: true,
      source: 'AI suggested',
      checklist: [
        { id: 'write-checkin', label: 'Write check-in', done: false },
        { id: 'manual-handoff', label: 'Use manual send handoff', done: false }
      ]
    }
  ],
  memories: [
    {
      id: 'm-asha-1',
      contactId: 'c-asha',
      category: 'Preference',
      body: 'Favorite dessert is mango lassi. Mention family jokes lightly.',
      pinned: true,
      createdAt: isoFromNow(-40)
    },
    {
      id: 'm-mira-1',
      contactId: 'c-mira',
      category: 'Milestone',
      body: 'Moved to Pune for a new design role.',
      pinned: true,
      createdAt: isoFromNow(-10)
    },
    {
      id: 'm-rajesh-private',
      contactId: 'c-rajesh',
      category: 'Private',
      body: 'Private note excluded from AI prompts.',
      pinned: false,
      createdAt: isoFromNow(-6)
    }
  ],
  gifts: [
    {
      id: 'g-asha-1',
      contactId: 'c-asha',
      name: 'Ceramic tea set',
      category: 'Personal',
      occasion: 'Birthday',
      cost: 2200,
      year: 2025,
      feedback: 'Liked',
      notes: 'Avoid repeating kitchenware this year.'
    }
  ],
  messages: [
    {
      id: 'msg-asha-bday',
      contactId: 'c-asha',
      eventId: 'e-asha-bday',
      reason: 'Birthday',
      status: 'Needs review',
      channel: 'SMS',
      body: 'Happy birthday Asha! Hope your day is full of mango lassi, laughter, and all the family chaos you secretly enjoy. Wishing you a beautiful year ahead.',
      selectedVariant: 'standard',
      variants: {
        short: 'Happy birthday Asha! Hope your day is full of laughter, mango lassi, and a lot of love.',
        standard:
          'Happy birthday Asha! Hope your day is full of mango lassi, laughter, and all the family chaos you secretly enjoy. Wishing you a beautiful year ahead.',
        warm: 'Happy birthday Asha. You make family life warmer, funnier, and lighter. Hope this year brings you joy, rest, and many mango-lassi-level good moments.'
      },
      scheduledFor: isoFromNow(5),
      quality: 'AI draft',
      readiness: 'Ready for review'
    },
    {
      id: 'msg-mira-checkin',
      contactId: 'c-mira',
      eventId: 'e-mira-checkin',
      reason: 'Check-in',
      status: 'Draft',
      channel: 'Manual',
      body: 'Hey Mira, how is Pune treating you so far? I was thinking of you and hope the new design role is starting well.',
      selectedVariant: 'standard',
      variants: {
        short: 'Hey Mira, how is Pune treating you? Hope the new role is starting well.',
        standard:
          'Hey Mira, how is Pune treating you so far? I was thinking of you and hope the new design role is starting well.',
        warm: 'Hey Mira, just wanted to check in. Moving cities is a lot, and I hope Pune and the new design role are slowly starting to feel like home.'
      },
      quality: 'AI draft',
      readiness: 'Use manual handoff'
    }
  ],
  activity: [
    {
      id: 'a-1',
      type: 'Setup',
      title: 'Review-first automation enabled',
      detail: 'Messages require approval before scheduling or sending.',
      severity: 'Info',
      createdAt: isoFromNow(-1)
    },
    {
      id: 'a-2',
      type: 'Message',
      title: 'Draft created',
      detail: 'Birthday wish for Asha is ready for review.',
      severity: 'Info',
      createdAt: isoFromNow(-1)
    }
  ],
  styleProfile: {
    confidence: 'Growing',
    formality: 'Mixed: casual for family, formal for work',
    language: 'English with Hinglish allowed per contact',
    averageLength: 142,
    emojiUse: 'Light',
    sampleCount: 8,
    enabledForAiDrafts: true,
    commonGreetings: ['Hi', 'Hey'],
    representativePreview: 'Hey! Thinking of you and sending warm wishes.'
  },
  backups: [
    {
      id: 'backup-local-demo',
      createdAt: isoFromNow(-12),
      recordCount: 22,
      encrypted: true
    }
  ],
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
  setupChecks: [
    {
      id: 'setup-ai',
      title: 'AI drafts',
      status: 'Ready',
      detail: 'Drafting is enabled. Low-confidence drafts still require review.',
      action: 'Review drafts'
    },
    {
      id: 'setup-manual-send',
      title: 'Manual send handoff',
      status: 'Ready',
      detail: 'Manual channel opens the user-approved text for final send control.',
      action: 'Use handoff'
    },
    {
      id: 'setup-email',
      title: 'Email sending',
      status: 'Optional',
      detail: 'Email is secondary and can be configured later for work contacts.',
      action: 'Configure later'
    },
    {
      id: 'setup-whatsapp',
      title: 'Manual WhatsApp handoff',
      status: 'Optional',
      detail: 'Open approved WhatsApp text manually and keep the final send in the destination app.',
      action: 'Keep manual'
    }
  ],
  reminderPlans: [],
  calendarSync: {
    exportedCount: 0,
    importedCount: 0
  },
  persistence: {
    status: 'Ready'
  }
};
