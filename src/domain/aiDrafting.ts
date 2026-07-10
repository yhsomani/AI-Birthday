import type {
  AiProviderObservation,
  AppState,
  ComposerReason,
  MessageDraft,
  MessageRegenerationFeedback
} from './types';
import { resolveContactPreferencesForContact } from './contactPreferences';
import { eventOccurrenceIso } from './occasionDates';

export type AiDraftVariants = MessageDraft['variants'];

export type AiDraftContextOptions = {
  excludedMemoryIds?: string[];
  includePriorMessages?: boolean;
  feedback?: MessageRegenerationFeedback;
};

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
  | 'content-safety'
  | 'wrong-language'
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
  regenerationFeedback?: MessageRegenerationFeedback;
  privacy: {
    includedMemoryCount: number;
    excludedOptionalMemoryCount: number;
    excludedPrivateMemoryCount: number;
    includedPriorMessageCount: number;
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
      observation?: AiProviderObservation;
    }
  | {
      ok: false;
      error: AiDraftError;
      observation?: AiProviderObservation;
    };

export type AiDraftResponseValidationOptions = {
  expectedLanguage?: AppState['contacts'][number]['language'];
  previousMessages?: string[];
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

const routeLeakPattern = /(?:\+?\d[\d\s().-]{7,}\d|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|https?:\/\/|www\.)/i;
const secretLeakPattern = /\b(?:api key|app password|credential|otp|passcode|password|secret token)\b/i;
const hindiScriptPattern = /[\u0900-\u097F]/;
const latinScriptPattern = /[A-Za-z]/;

const normalizeForSimilarity = (value: string) =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9\u0900-\u097f ]/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();

const wordSet = (value: string) => new Set(normalizeForSimilarity(value).split(' ').filter(word => word.length > 2));

const similarity = (left: string, right: string) => {
  const leftWords = wordSet(left);
  const rightWords = wordSet(right);
  if (leftWords.size === 0 || rightWords.size === 0) {
    return 0;
  }
  const overlap = [...leftWords].filter(word => rightWords.has(word)).length;
  return overlap / Math.max(leftWords.size, rightWords.size);
};

const validateDraftSafety = (
  variants: AiDraftVariants,
  options: AiDraftResponseValidationOptions
): AiDraftError | undefined => {
  const values = Object.values(variants);
  if (values.some(value => routeLeakPattern.test(value) || secretLeakPattern.test(value))) {
    return {
      kind: 'content-safety',
      message: 'The AI provider returned sensitive routing or credential-like content. Use a local template or retry.'
    };
  }

  if (
    options.previousMessages?.some(previous =>
      values.some(value => normalizeForSimilarity(previous) === normalizeForSimilarity(value) || similarity(previous, value) >= 0.82)
    )
  ) {
    return {
      kind: 'content-safety',
      message: 'The AI provider repeated an earlier message too closely. Regenerate with different context.'
    };
  }

  const combined = values.join(' ');
  if (options.expectedLanguage === 'Hindi' && !hindiScriptPattern.test(combined)) {
    return {
      kind: 'wrong-language',
      message: 'The AI provider returned the draft in the wrong language. Regenerate or use a local template.'
    };
  }
  if (options.expectedLanguage === 'English' && hindiScriptPattern.test(combined)) {
    return {
      kind: 'wrong-language',
      message: 'The AI provider returned the draft in the wrong language. Regenerate or use a local template.'
    };
  }
  if (options.expectedLanguage === 'Hinglish' && !latinScriptPattern.test(combined)) {
    return {
      kind: 'wrong-language',
      message: 'The AI provider returned the draft in the wrong language. Regenerate or use a local template.'
    };
  }

  return undefined;
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
  reason: ComposerReason,
  options: AiDraftContextOptions = {}
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

  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const contactMemories = state.memories.filter(memory => memory.contactId === contactId);
  const excludedMemoryIds = new Set(options.excludedMemoryIds ?? []);
  const includedMemories = contactMemories
    .filter(memory => memory.category !== 'Private' && !excludedMemoryIds.has(memory.id))
    .slice(0, MAX_CONTEXT_ITEMS)
    .map(memory => ({
      category: memory.category,
      body: cleanText(memory.body, 240)
    }))
    .filter(memory => memory.body.length > 0);
  const excludedPrivateMemoryCount = contactMemories.filter(memory => memory.category === 'Private').length;
  const excludedOptionalMemoryCount = contactMemories.filter(
    memory => memory.category !== 'Private' && excludedMemoryIds.has(memory.id)
  ).length;
  const includePriorMessages = options.includePriorMessages ?? true;
  const priorApprovedMessages = includePriorMessages
    ? state.messages
        .filter(message => message.contactId === contactId && message.status === 'Sent')
        .slice(0, MAX_PRIOR_MESSAGES)
        .map(message => cleanText(message.body, 220))
        .filter(Boolean)
    : [];
  const feedback = options.feedback
    ? {
        instructions: options.feedback.instructions.map(item => cleanText(item, 180)).filter(Boolean),
        customInstruction: cleanText(options.feedback.customInstruction, 240) || undefined,
        previousDraftExcerpt: cleanText(options.feedback.previousDraftExcerpt, 220) || undefined
      }
    : undefined;

  return {
    ok: true,
    request: {
      reason,
      contact: {
        name: contact.name,
        relationship: contact.relationship,
        group: contact.group,
        language: contact.language,
        tone: preferences.tone,
        preferredChannel: preferences.preferredChannel,
        notesSummary: cleanText(contact.notesSummary, 220)
      },
      event: event
        ? {
            type: event.type,
            label: event.label,
            date: eventOccurrenceIso(event) ?? event.date,
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
      regenerationFeedback:
        feedback && (feedback.instructions.length > 0 || feedback.customInstruction)
          ? feedback
          : undefined,
      privacy: {
        includedMemoryCount: includedMemories.length,
        excludedOptionalMemoryCount,
        excludedPrivateMemoryCount,
        includedPriorMessageCount: priorApprovedMessages.length,
        excludedFields: ['phone', 'email', 'private memories', 'credentials', 'raw contact provider ids']
      },
      outputContract: {
        format: 'json',
        variants: ['short', 'standard', 'warm'],
        maxCharactersPerVariant: MAX_VARIANT_LENGTH,
        mustRequireUserReview: true
      }
    },
    privacySummary: `${includedMemories.length} memory item(s) included; ${excludedOptionalMemoryCount} optional memory item(s) excluded; ${excludedPrivateMemoryCount} private item(s) excluded; ${priorApprovedMessages.length} prior sent message(s) included.`
  };
};

export const normalizeAiDraftResponse = (
  payload: unknown,
  options: AiDraftResponseValidationOptions = {}
): AiDraftResponseResult => {
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

  const variants = {
    short,
    standard,
    warm
  };
  const safetyError = validateDraftSafety(variants, options);
  return safetyError
    ? {
        ok: false,
        error: safetyError
      }
    : {
        ok: true,
        variants
      };
};

export const buildAiProviderObservation = (
  request: AiDraftRequest,
  result: AiDraftResponseResult,
  durationMs: number
): AiProviderObservation => ({
  redacted: true,
  ok: result.ok,
  durationMs,
  reason: request.reason,
  contactLanguage: request.contact.language as AiProviderObservation['contactLanguage'],
  includedMemoryCount: request.privacy.includedMemoryCount,
  excludedPrivateMemoryCount: request.privacy.excludedPrivateMemoryCount,
  includedPriorMessageCount: request.privacy.includedPriorMessageCount,
  errorKind: result.ok ? undefined : result.error.kind,
  variantLengths: result.ok
    ? {
        short: result.variants.short.length,
        standard: result.variants.standard.length,
        warm: result.variants.warm.length
      }
    : undefined
});

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
