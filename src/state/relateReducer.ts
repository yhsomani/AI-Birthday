import { initialState } from '../data/seed';
import type { AiDraftError, AiDraftVariants } from '../domain/aiDrafting';
import { calendarCandidatesToEvents } from '../domain/calendarSync';
import {
  buildEnrichmentMemoryBody,
  resolveContactEnrichmentPrompt,
  validateEnrichmentAnswer,
  type ContactEnrichmentPromptId
} from '../domain/contactEnrichment';
import { importContacts } from '../domain/contactImport';
import type { EmailDeliveryError } from '../domain/emailDelivery';
import { detectDuplicateMessageRisk } from '../domain/duplicateGuard';
import { buildDefaultEventChecklist, validateManualEventInput } from '../domain/events';
import { buildMessageFollowUpPlan, type FollowUpDelayDays } from '../domain/followUps';
import { buildTemplateDraft } from '../domain/messageTemplates';
import { buildReminderPlans } from '../domain/reminders';
import { analyzeManualStyleSamples, analyzeSentMessageStyle } from '../domain/styleCoach';
import type {
  ActivityItem,
  AppState,
  CalendarImportCandidate,
  ComposerReason,
  Contact,
  EventType,
  ImportedContactRecord,
  MemoryCategory,
  MessageChannel,
  MessageDraft,
  Screen,
  SupportedLocale,
  Tone
} from '../domain/types';

export type RelateAction =
  | { type: 'hydrate'; state: AppState }
  | { type: 'navigate'; screen: Screen; contactId?: string; messageId?: string }
  | { type: 'setSearch'; query: string }
  | { type: 'toggleChecklist'; eventId: string; itemId: string }
  | {
      type: 'addManualEvent';
      contactId?: string;
      newContactName?: string;
      eventType: EventType;
      label: string;
      date: string;
      confirmConflict?: boolean;
    }
  | { type: 'selectVariant'; messageId: string; variant: MessageDraft['selectedVariant'] }
  | { type: 'editMessage'; messageId: string; body: string }
  | { type: 'generateMessage'; contactId: string; eventId?: string; reason: ComposerReason; fallbackReason?: string }
  | { type: 'createTemplateDraft'; contactId: string; reason: ComposerReason; body: string; templateId?: string }
  | {
      type: 'createAiDraft';
      contactId: string;
      eventId?: string;
      reason: ComposerReason;
      variants: AiDraftVariants;
      privacySummary: string;
    }
  | { type: 'aiProviderReady'; privacySummary: string }
  | { type: 'aiProviderFailure'; error: AiDraftError; privacySummary?: string }
  | { type: 'approveMessage'; messageId: string }
  | { type: 'acknowledgeDuplicateRisk'; messageId: string }
  | { type: 'rejectMessage'; messageId: string }
  | { type: 'manualHandoff'; messageId: string; nowIso?: string }
  | { type: 'scheduleMessageFollowUp'; messageId: string; delayDays: FollowUpDelayDays; nowIso?: string }
  | { type: 'retryMessage'; messageId: string }
  | { type: 'addMemory'; contactId: string; category: MemoryCategory; body: string }
  | { type: 'answerEnrichmentPrompt'; contactId: string; promptId: ContactEnrichmentPromptId; body: string }
  | { type: 'addGift'; contactId: string; name: string; occasion: string; cost: number }
  | { type: 'updateContactTone'; contactId: string; tone: Tone }
  | { type: 'setContactChannel'; contactId: string; channel: MessageChannel }
  | { type: 'snoozeCheckIn'; contactId: string; days: number }
  | { type: 'setLocale'; locale: SupportedLocale }
  | { type: 'setEmailSender'; senderEmail: string }
  | { type: 'emailProviderReady' }
  | { type: 'emailProviderFailure'; error: EmailDeliveryError }
  | { type: 'emailSent'; messageId: string }
  | { type: 'toggleSetting'; key: keyof AppState['settings'] }
  | { type: 'importContacts'; records: ImportedContactRecord[] }
  | { type: 'planReminders' }
  | { type: 'calendarImported'; candidates: CalendarImportCandidate[] }
  | { type: 'calendarExported'; count: number }
  | { type: 'calendarError'; message: string }
  | { type: 'persistenceSaving' }
  | { type: 'persistenceSaved'; savedAt: string }
  | { type: 'persistenceError'; message: string }
  | { type: 'createBackup' }
  | { type: 'restoreBackup'; restoredState: AppState; recordCount: number }
  | { type: 'trainStyle' }
  | { type: 'trainStyleFromSamples'; samples: string }
  | { type: 'trainStyleFromSentMessages' }
  | { type: 'resetDemo' };

export const createInitialState = (): AppState => structuredClone(initialState);

const nowIso = () => new Date().toISOString();

const addDaysIso = (days: number) => {
  const next = new Date();
  next.setDate(next.getDate() + days);
  return next.toISOString();
};

const addActivity = (
  state: AppState,
  type: ActivityItem['type'],
  title: string,
  detail: string,
  severity: ActivityItem['severity'] = 'Info'
): ActivityItem[] => [
  {
    id: `activity-${Date.now()}-${state.activity.length}`,
    type,
    title,
    detail,
    severity,
    createdAt: nowIso()
  },
  ...state.activity
];

const findContact = (state: AppState, contactId: string) =>
  state.contacts.find(contact => contact.id === contactId);

const findEvent = (state: AppState, eventId?: string) =>
  eventId ? state.events.find(event => event.id === eventId) : undefined;

const getAiContext = (state: AppState, contactId: string) =>
  state.memories
    .filter(note => note.contactId === contactId && note.category !== 'Private')
    .map(note => note.body)
    .slice(0, 3);

const buildDraftText = (
  contact: Contact,
  reason: ComposerReason,
  context: string[],
  styleLength: number
) => {
  const contextLine = context.length > 0 ? ` I remembered: ${context[0]}` : '';
  const tone = contact.tone.includes('Formal') || contact.tone.includes('Respectful') ? 'thoughtful' : 'warm';
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

  if (base.length > styleLength + 80) {
    return base.slice(0, styleLength + 77).trimEnd() + '...';
  }
  return base;
};

const buildMessageDraft = (
  state: AppState,
  contactId: string,
  eventId: string | undefined,
  reason: ComposerReason,
  options: {
    providerVariants?: AiDraftVariants;
    fallbackReason?: string;
  } = {}
): MessageDraft | undefined => {
  const contact = findContact(state, contactId);
  const event = findEvent(state, eventId);
  if (!contact) {
    return undefined;
  }

  const context = getAiContext(state, contactId);
  const standard = options.providerVariants?.standard ?? buildDraftText(contact, reason, context, state.styleProfile.averageLength);
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
    channel: contact.preferredChannel,
    body: standard,
    variants: {
      short,
      standard,
      warm
    },
    selectedVariant: 'standard',
    scheduledFor: event?.date,
    quality,
    readiness: contact.dnd
      ? 'Blocked by do-not-disturb'
      : contact.preferredChannel === 'Manual'
        ? 'Use manual handoff'
        : options.providerVariants
          ? 'Provider draft ready for review'
          : 'Ready for review',
    lastError: options.fallbackReason
  };
};

const updateMessage = (
  state: AppState,
  messageId: string,
  updater: (message: MessageDraft) => MessageDraft
) => state.messages.map(message => (message.id === messageId ? updater(message) : message));

const normalizeLoadedState = (loadedState: AppState): AppState => {
  const defaults = createInitialState();
  return {
    ...defaults,
    ...loadedState,
    settings: {
      ...defaults.settings,
      ...loadedState.settings
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
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          selectedVariant: action.variant,
          body: message.variants[action.variant]
        }))
      };
    case 'editMessage':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          body: action.body,
          readiness: action.body.trim().length < 12 ? 'Write a longer message before approval' : message.readiness
        }))
      };
    case 'generateMessage': {
      const draft = buildMessageDraft(state, action.contactId, action.eventId, action.reason, {
        fallbackReason: action.fallbackReason
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
          action.fallbackReason ?? `${draft.reason} draft is ready for review.`,
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
        providerVariants: action.variants
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
          lastPrivacySummary: action.privacySummary
        },
        activity: addActivity(state, 'AI', 'AI draft created', `${reviewedDraft.reason} provider draft is ready for review.`)
      };
    }
    case 'aiProviderReady':
      return {
        ...state,
        aiProvider: {
          status: 'Ready',
          lastCheckedAt: nowIso(),
          lastPrivacySummary: action.privacySummary
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
          lastPrivacySummary: action.privacySummary
        },
        activity: addActivity(state, 'AI', 'AI provider unavailable', action.error.message, 'Warning')
      };
    case 'approveMessage':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => {
          if (message.body.trim().length < 12) {
            return {
              ...message,
              status: 'Blocked',
              readiness: 'Write a longer message before approval',
              lastError: 'Message is too short to approve.'
            };
          }
          if (message.duplicateWarning && !message.duplicateAcknowledged) {
            return {
              ...message,
              status: 'Blocked',
              readiness: 'Explicitly continue or edit before approval',
              lastError: message.duplicateWarning
            };
          }
          return {
            ...message,
            status: message.channel === 'Manual' ? 'Scheduled' : 'Scheduled',
            readiness: message.channel === 'Manual' ? 'Ready for manual handoff' : 'Approved and scheduled'
          };
        }),
        activity: addActivity(state, 'Message', 'Message approved', 'The message is approved for scheduled or manual send.')
      };
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
          'Warning'
        )
      };
    case 'rejectMessage':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          status: 'Rejected',
          readiness: 'Rejected by user'
        })),
        activity: addActivity(state, 'Message', 'Message rejected', 'The draft will not be sent.')
      };
    case 'manualHandoff': {
      const completedAt = action.nowIso ?? nowIso();
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          status: 'Sent',
          readiness: 'Marked sent after manual handoff',
          sentAt: completedAt
        })),
        contacts: state.contacts.map(contact => {
          const message = state.messages.find(item => item.id === action.messageId);
          return message?.contactId === contact.id ? { ...contact, lastContactedAt: completedAt, healthScore: Math.min(100, contact.healthScore + 5) } : contact;
        }),
        activity: addActivity(state, 'Message', 'Manual handoff completed', 'The user retained final control in the destination app.')
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
    case 'retryMessage':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          status: 'Needs review',
          readiness: 'Ready for review',
          lastError: undefined
        })),
        activity: addActivity(state, 'Message', 'Message retry prepared', 'Review the message before retrying.')
      };
    case 'addMemory':
      if (action.body.trim().length === 0) {
        return {
          ...state,
          activity: addActivity(state, 'Memory', 'Memory not saved', 'Write a note before saving it.', 'Warning')
        };
      }
      return {
        ...state,
        memories: [
          {
            id: `memory-${Date.now()}`,
            contactId: action.contactId,
            category: action.category,
            body: action.body.trim().slice(0, 500),
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
      if (action.name.trim().length === 0 || action.occasion.trim().length === 0 || action.cost < 0) {
        return {
          ...state,
          activity: addActivity(state, 'Gift', 'Gift not saved', 'Gift name, occasion, and valid cost are required.', 'Warning')
        };
      }
      return {
        ...state,
        gifts: [
          {
            id: `gift-${Date.now()}`,
            contactId: action.contactId,
            name: action.name.trim(),
            occasion: action.occasion.trim(),
            cost: action.cost,
            year: new Date().getFullYear(),
            feedback: 'Unknown',
            notes: 'Recorded from Gift Advisor.'
          },
          ...state.gifts
        ],
        activity: addActivity(state, 'Gift', 'Gift saved', `${action.name.trim()} was added to gift history.`)
      };
    case 'updateContactTone':
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId
            ? {
                ...contact,
                tone: contact.tone.includes(action.tone)
                  ? contact.tone.filter(tone => tone !== action.tone)
                  : [...contact.tone, action.tone]
              }
            : contact
        )
      };
    case 'setContactChannel':
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId ? { ...contact, preferredChannel: action.channel } : contact
        )
      };
    case 'snoozeCheckIn':
      return {
        ...state,
        contacts: state.contacts.map(contact =>
          contact.id === action.contactId ? { ...contact, lastContactedAt: addDaysIso(action.days) } : contact
        ),
        activity: addActivity(state, 'Contact', 'Check-in snoozed', `Reminder moved by ${action.days} day(s).`)
      };
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
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Error',
          lastCheckedAt: nowIso(),
          lastError: action.error.message
        },
        activity: addActivity(state, 'Setup', 'Email delivery failed', action.error.message, 'Warning')
      };
    case 'emailSent':
      return {
        ...state,
        messages: updateMessage(state, action.messageId, message => ({
          ...message,
          status: 'Sent',
          readiness: 'Email sent by configured provider',
          sentAt: nowIso()
        })),
        contacts: state.contacts.map(contact => {
          const message = state.messages.find(item => item.id === action.messageId);
          return message?.contactId === contact.id
            ? { ...contact, lastContactedAt: nowIso(), healthScore: Math.min(100, contact.healthScore + 5) }
            : contact;
        }),
        emailDelivery: {
          ...state.emailDelivery,
          status: 'Ready',
          lastCheckedAt: nowIso(),
          lastError: undefined
        },
        activity: addActivity(state, 'Message', 'Email sent', 'The approved email was sent by the configured provider.')
      };
    case 'toggleSetting': {
      const value = state.settings[action.key];
      if (typeof value !== 'boolean') {
        return state;
      }
      return {
        ...state,
        settings: {
          ...state.settings,
          [action.key]: !value
        },
        activity: addActivity(state, 'Setup', 'Setting updated', `${String(action.key)} changed.`)
      };
    }
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
      const reminderPlans = buildReminderPlans(state);
      return {
        ...state,
        reminderPlans,
        activity: addActivity(
          state,
          'Event',
          'Reminders planned',
          `${reminderPlans.length} reminder(s) are ready to schedule.`
        )
      };
    }
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
          lastSavedAt: action.savedAt
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
    case 'resetDemo':
      return createInitialState();
    default:
      return state;
  }
};
