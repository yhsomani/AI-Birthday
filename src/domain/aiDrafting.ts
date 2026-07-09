import type { AppState, ComposerReason, MessageDraft } from './types';

export type AiDraftVariants = MessageDraft['variants'];

export type AiDraftErrorKind =
  | 'disabled'
  | 'missing-contact'
  | 'missing-event'
  | 'not-configured'
  | 'auth'
  | 'quota'
  | 'network'
  | 'timeout'
  | 'invalid-response'
  | 'server';

export type AiDraftError = {
  kind: AiDraftErrorKind;
  message: string;
};

export type AiDraftRequest = {
  reason: ComposerReason;
  contact: {
    name: string;
    relationship: string;
    group: string;
    language: string;
    tone: string[];
    preferredChannel: string;
    notesSummary: string;
  };
  event?: {
    type: string;
    label: string;
    date: string;
    verified: boolean;
  };
  style: {
    confidence: string;
    formality: string;
    language: string;
    averageLength: number;
    emojiUse: string;
  };
  memories: Array<{
    category: string;
    body: string;
  }>;
  priorApprovedMessages: string[];
  privacy: {
    includedMemoryCount: number;
    excludedPrivateMemoryCount: number;
    excludedFields: string[];
  };
  outputContract: {
    format: 'json';
    variants: Array<keyof AiDraftVariants>;
    maxCharactersPerVariant: number;
    mustRequireUserReview: true;
  };
};

export type AiDraftRequestResult =
  | {
      ok: true;
      request: AiDraftRequest;
      privacySummary: string;
    }
  | {
      ok: false;
      error: AiDraftError;
    };

export type AiDraftResponseResult =
  | {
      ok: true;
      variants: AiDraftVariants;
    }
  | {
      ok: false;
      error: AiDraftError;
    };

const MAX_CONTEXT_ITEMS = 5;
const MAX_PRIOR_MESSAGES = 3;
const MAX_VARIANT_LENGTH = 500;
const MIN_VARIANT_LENGTH = 12;

const cleanText = (value: unknown, maxLength = MAX_VARIANT_LENGTH) => {
  if (typeof value !== 'string') {
    return '';
  }
  return value.replace(/\s+/g, ' ').trim().slice(0, maxLength);
};

const failure = (kind: AiDraftErrorKind, message: string): AiDraftRequestResult => ({
  ok: false,
  error: {
    kind,
    message
  }
});

export const buildAiDraftRequest = (
  state: AppState,
  contactId: string,
  eventId: string | undefined,
  reason: ComposerReason
): AiDraftRequestResult => {
  if (!state.settings.aiEnabled) {
    return failure('disabled', 'AI drafting is disabled. A local template can still be used.');
  }

  const contact = state.contacts.find(item => item.id === contactId);
  if (!contact) {
    return failure('missing-contact', 'The selected contact could not be found.');
  }

  const event = eventId ? state.events.find(item => item.id === eventId) : undefined;
  if (eventId && !event) {
    return failure('missing-event', 'The selected event could not be found.');
  }

  const contactMemories = state.memories.filter(memory => memory.contactId === contactId);
  const includedMemories = contactMemories
    .filter(memory => memory.category !== 'Private')
    .slice(0, MAX_CONTEXT_ITEMS)
    .map(memory => ({
      category: memory.category,
      body: cleanText(memory.body, 240)
    }))
    .filter(memory => memory.body.length > 0);
  const excludedPrivateMemoryCount = contactMemories.filter(memory => memory.category === 'Private').length;
  const priorApprovedMessages = state.messages
    .filter(message => message.contactId === contactId && message.status === 'Sent')
    .slice(0, MAX_PRIOR_MESSAGES)
    .map(message => cleanText(message.body, 220))
    .filter(Boolean);

  return {
    ok: true,
    request: {
      reason,
      contact: {
        name: contact.name,
        relationship: contact.relationship,
        group: contact.group,
        language: contact.language,
        tone: contact.tone,
        preferredChannel: contact.preferredChannel,
        notesSummary: cleanText(contact.notesSummary, 220)
      },
      event: event
        ? {
            type: event.type,
            label: event.label,
            date: event.date,
            verified: event.verified
          }
        : undefined,
      style: {
        confidence: state.styleProfile.confidence,
        formality: state.styleProfile.formality,
        language: state.styleProfile.language,
        averageLength: state.styleProfile.averageLength,
        emojiUse: state.styleProfile.emojiUse
      },
      memories: includedMemories,
      priorApprovedMessages,
      privacy: {
        includedMemoryCount: includedMemories.length,
        excludedPrivateMemoryCount,
        excludedFields: ['phone', 'email', 'private memories', 'credentials', 'raw contact provider ids']
      },
      outputContract: {
        format: 'json',
        variants: ['short', 'standard', 'warm'],
        maxCharactersPerVariant: MAX_VARIANT_LENGTH,
        mustRequireUserReview: true
      }
    },
    privacySummary: `${includedMemories.length} memory item(s) included; ${excludedPrivateMemoryCount} private item(s) excluded.`
  };
};

export const normalizeAiDraftResponse = (payload: unknown): AiDraftResponseResult => {
  if (!payload || typeof payload !== 'object') {
    return {
      ok: false,
      error: {
        kind: 'invalid-response',
        message: 'The AI provider returned an unreadable response.'
      }
    };
  }

  const data = payload as {
    variants?: Partial<Record<keyof AiDraftVariants, unknown>>;
    short?: unknown;
    standard?: unknown;
    warm?: unknown;
    text?: unknown;
    message?: unknown;
  };
  const source = data.variants ?? data;
  const standard = cleanText(source.standard ?? data.text ?? data.message);
  const short = cleanText(source.short ?? standard);
  const warm = cleanText(source.warm ?? standard);

  if (
    standard.length < MIN_VARIANT_LENGTH ||
    short.length < MIN_VARIANT_LENGTH ||
    warm.length < MIN_VARIANT_LENGTH
  ) {
    return {
      ok: false,
      error: {
        kind: 'invalid-response',
        message: 'The AI provider returned an empty or too-short draft.'
      }
    };
  }

  return {
    ok: true,
    variants: {
      short,
      standard,
      warm
    }
  };
};

export const classifyAiProviderStatus = (status: number): AiDraftError => {
  if (status === 401 || status === 403) {
    return {
      kind: 'auth',
      message: 'AI provider authentication failed. Check the secure endpoint configuration.'
    };
  }
  if (status === 429) {
    return {
      kind: 'quota',
      message: 'The AI provider is rate limited or out of quota. Try again later.'
    };
  }
  if (status >= 500) {
    return {
      kind: 'server',
      message: 'The AI provider is temporarily unavailable.'
    };
  }
  return {
    kind: 'network',
    message: `The AI provider returned HTTP ${status}.`
  };
};
