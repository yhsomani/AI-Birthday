import type {
  AiProviderObservation,
  AppState,
  ComposerReason,
  MessageDraft,
  MessageRegenerationFeedback
} from './types';
import { resolveContactPreferencesForContact } from './contactPreferences';
import { eventOccurrenceIso } from './occasionDates';
import {
  buildMemoryPersonalizationContext,
  sanitizeRecipientVisiblePersonalizationText,
  type GenerationConstraintKind
} from './personalizationContextPolicy';

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
    enabled: boolean;
    confidence: string;
    formality: string;
    language: string;
    averageLength: number;
    emojiUse: string;
    commonGreetings: string[];
  };
  memories: {
    category: string;
    body: string;
  }[];
  giftHistory: {
    name: string;
    category: AppState['gifts'][number]['category'];
    occasion: string;
    year: number;
    feedback: AppState['gifts'][number]['feedback'];
  }[];
  generationConstraints: {
    kind: GenerationConstraintKind;
    instruction: string;
  }[];
  priorApprovedMessages: string[];
  regenerationFeedback?: MessageRegenerationFeedback;
  privacy: {
    includedMemoryCount: number;
    includedGenerationConstraintCount: number;
    excludedOptionalMemoryCount: number;
    excludedPrivateMemoryCount: number;
    excludedSensitiveMemoryCount: number;
    includedGiftHistoryCount: number;
    excludedGiftHistoryCount: number;
    excludedSensitiveGiftHistoryCount: number;
    includedPriorMessageCount: number;
    excludedSensitivePriorMessageCount: number;
    excludedSensitiveFeedbackCount: number;
    excludedFields: string[];
  };
  outputContract: {
    format: 'json';
    variants: (keyof AiDraftVariants)[];
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
const MAX_GIFT_HISTORY_ITEMS = 3;
const MAX_GIFT_FIELD_LENGTH = 100;
const MAX_PRIOR_MESSAGES = 3;
const MAX_VARIANT_LENGTH = 500;
const MIN_VARIANT_LENGTH = 12;

const cleanText = (value: unknown, maxLength = MAX_VARIANT_LENGTH) => {
  if (typeof value !== 'string') {
    return '';
  }
  return value.replace(/\s+/g, ' ').trim().slice(0, maxLength);
};

const routeLeakPattern =
  /(?:\+?\d[\d\s().-]{7,32}\d|[A-Z0-9._%+-]{1,254}@[A-Z0-9.-]{1,254}\.[A-Z]{2,63}|https?:\/\/|www\.|(?:mailto|tel|sms|smsto|whatsapp):|wa\.me\/)/i;
const secretLeakPattern =
  /\b(?:api[- ]?key|app password|authorization bearer|bearer token|client secret|credential|otp|passcode|password|secret token|access token|private key)\b/i;
const clearlyUnsafeOutputPatterns = [
  /\b(?:kill yourself|end your life|commit suicide|self[- ]?harm)\b/i,
  /\b(?:i hope you die|you deserve to die)\b/i,
  /\b(?:kill|murder|stab|shoot|attack|hurt)\s+(?:you|them|him|her)\b/i,
  /\b(?:send (?:me )?nudes|explicit sexual content)\b/i,
  /\b(?:blackmail|threaten)\s+(?:you|them|him|her)\b/i,
  /\b(?:you(?:'re| are) (?:worthless|disgusting|stupid|an? (?:idiot|moron|loser))|you idiot)\b/i,
  /\b(?:fuck you|go fuck yourself)\b/i
];
const unresolvedPlaceholderPattern =
  /(?:\[(?:contact[_ -]?name|name|relationship|occasion)\]|\{\{?(?:contact[_ -]?name|name|relationship|occasion)\}?\}|<(?:contact[_ -]?name|name|relationship|occasion)>|lorem ipsum|as an ai language model)/i;
const privateContextMarkerPattern =
  /\b(?:keep (?:this|it) private|do not share|don't share|confidential (?:detail|information)|secret (?:family|medical|financial|relationship|personal) (?:detail|information))\b/i;
const containsSensitiveProviderContent = (value: string) =>
  routeLeakPattern.test(value) || secretLeakPattern.test(value);
const containsProtectedProviderContext = (value: string) =>
  containsSensitiveProviderContent(value) || privateContextMarkerPattern.test(value);

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const normalizeOutputText = (value: unknown) =>
  typeof value === 'string'
    ? value
        .replace(/[\u0000-\u001f\u007f]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim()
    : '';

const cleanProviderContextText = (value: unknown, maxLength: number) => {
  const cleaned = cleanText(value, maxLength);
  return cleaned && !containsProtectedProviderContext(cleaned) ? cleaned : '';
};

const normalizeForSimilarity = (value: string) =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9\u0900-\u097f ]/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();

const wordSet = (value: string) =>
  new Set(
    normalizeForSimilarity(value)
      .split(' ')
      .filter(word => word.length > 2)
  );

const similarity = (left: string, right: string) => {
  const leftWords = wordSet(left);
  const rightWords = wordSet(right);
  if (leftWords.size === 0 || rightWords.size === 0) {
    return 0;
  }
  const overlap = [...leftWords].filter(word => rightWords.has(word)).length;
  return overlap / Math.max(leftWords.size, rightWords.size);
};

const hinglishWordPattern =
  /\b(?:aaj|aap|aapka|aapke|aapki|achchha|achchhe|achchhi|accha|acche|acchi|bahut|bohot|bas|dil|hamara|humara|isliye|kaisa|kaisi|khush|khushi|koi|liye|mujhe|poochna|rahe|rahi|raho|rishta|saath|sab|sach|theek|tum|tumhara|tumhare|tumhari|umeed|wala|wale|wali|yaad|yeh)\b/gi;
const englishWordPattern =
  /\b(?:and|birthday|checking|congratulations|day|follow|happy|hope|important|love|message|personal|reply|smile|special|support|thank|wishing|year|you|your)\b/i;

const hinglishMarkerCount = (value: string) => value.match(hinglishWordPattern)?.length ?? 0;

const matchesExpectedLanguage = (
  value: string,
  expectedLanguage: NonNullable<AiDraftResponseValidationOptions['expectedLanguage']>
) => {
  const devanagariCount = value.match(/[\u0900-\u097F]/g)?.length ?? 0;
  const latinCount = value.match(/[A-Za-z]/g)?.length ?? 0;
  const scriptLetterCount = devanagariCount + latinCount;
  if (expectedLanguage === 'Hindi') {
    return devanagariCount >= 3 && devanagariCount / Math.max(1, scriptLetterCount) >= 0.35;
  }
  if (expectedLanguage === 'English') {
    return (
      latinCount >= 3 && devanagariCount / Math.max(1, scriptLetterCount) < 0.2 && hinglishMarkerCount(value) === 0
    );
  }
  // Hinglish may be Romanized Hindi or a Devanagari/English mix, but plain
  // English must not pass only because both use Latin script.
  return (
    latinCount >= 3 && (hinglishMarkerCount(value) > 0 || (devanagariCount >= 3 && englishWordPattern.test(value)))
  );
};

const validateDraftSafety = (
  variants: AiDraftVariants,
  options: AiDraftResponseValidationOptions
): AiDraftError | undefined => {
  const values = Object.values(variants);
  if (values.some(containsSensitiveProviderContent)) {
    return {
      kind: 'content-safety',
      message: 'The AI provider returned sensitive routing or credential-like content. Use a local template or retry.'
    };
  }

  if (values.some(value => clearlyUnsafeOutputPatterns.some(pattern => pattern.test(value)))) {
    return {
      kind: 'content-safety',
      message: 'The AI provider returned content that did not pass the safety check. Use a local template or retry.'
    };
  }

  if (
    options.previousMessages?.some(previous =>
      values.some(
        value =>
          normalizeForSimilarity(previous) === normalizeForSimilarity(value) || similarity(previous, value) >= 0.82
      )
    )
  ) {
    return {
      kind: 'content-safety',
      message: 'The AI provider repeated an earlier message too closely. Regenerate with different context.'
    };
  }

  const expectedLanguage = options.expectedLanguage;
  if (expectedLanguage && values.some(value => !matchesExpectedLanguage(value, expectedLanguage))) {
    return {
      kind: 'wrong-language',
      message: 'The AI provider returned the draft in the wrong language. Regenerate or use a local template.'
    };
  }

  return undefined;
};

const validateDraftQuality = (variants: AiDraftVariants): AiDraftError | undefined => {
  const values = Object.values(variants);
  const normalizedVariants = values.map(normalizeForSimilarity);
  if (values.some(value => unresolvedPlaceholderPattern.test(value)) || new Set(normalizedVariants).size === 1) {
    return {
      kind: 'invalid-response',
      message:
        'The AI provider returned incomplete or insufficiently distinct draft variants. Use a local template or retry.'
    };
  }
  return undefined;
};

const buildGiftHistoryContext = (state: AppState, contactId: string) => {
  const gifts = state.gifts
    .filter(gift => gift.contactId === contactId)
    .sort((left, right) => right.year - left.year || left.id.localeCompare(right.id));
  let excludedSensitiveGiftHistoryCount = 0;
  const safeGifts = gifts.flatMap(gift => {
    const name = cleanText(sanitizeRecipientVisiblePersonalizationText(gift.name), MAX_GIFT_FIELD_LENGTH);
    const occasion = cleanText(sanitizeRecipientVisiblePersonalizationText(gift.occasion), MAX_GIFT_FIELD_LENGTH);
    if (!name || !occasion || containsProtectedProviderContext(name) || containsProtectedProviderContext(occasion)) {
      excludedSensitiveGiftHistoryCount += 1;
      return [];
    }
    return [
      {
        name,
        category: gift.category,
        occasion,
        year: gift.year,
        feedback: gift.feedback
      }
    ];
  });
  const giftHistory = safeGifts.slice(0, MAX_GIFT_HISTORY_ITEMS);
  return {
    giftHistory,
    excludedGiftHistoryCount: gifts.length - giftHistory.length,
    excludedSensitiveGiftHistoryCount
  };
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
  if (contact.archivedAt) {
    return failure('missing-contact', 'Restore the archived contact before creating a draft.');
  }
  const providerSafeContactName = cleanProviderContextText(contact.name, 120);
  if (!providerSafeContactName) {
    return failure('missing-contact', 'The selected contact does not have a provider-safe name for drafting.');
  }
  const providerSafeRelationship = cleanProviderContextText(contact.relationship, 120) || contact.group;

  const event = eventId ? state.events.find(item => item.id === eventId) : undefined;
  if (eventId && !event) {
    return failure('missing-event', 'The selected event could not be found.');
  }
  if (event && event.contactId !== contactId) {
    return failure('missing-event', 'The selected event does not belong to the selected contact.');
  }

  const preferences = resolveContactPreferencesForContact(state.settings, contact);
  const personalization = buildMemoryPersonalizationContext(state.memories, {
    contactId,
    excludedMemoryIds: options.excludedMemoryIds,
    maxMentionableItems: MAX_CONTEXT_ITEMS
  });
  const includedMemories = personalization.mentionableFacts.map(memory => ({
    category: memory.category,
    body: cleanText(memory.text, 240)
  }));
  const generationConstraints = personalization.generationConstraints.map(constraint => ({
    kind: constraint.kind,
    instruction: constraint.instruction
  }));
  const giftContext = buildGiftHistoryContext(state, contactId);
  const includePriorMessages = options.includePriorMessages ?? true;
  const eligiblePriorMessages = includePriorMessages
    ? state.messages.filter(message => message.contactId === contactId && message.status === 'Sent')
    : [];
  const safePriorMessages = eligiblePriorMessages
    .map(message => cleanProviderContextText(message.body, 220))
    .filter(Boolean);
  const priorApprovedMessages = safePriorMessages.slice(0, MAX_PRIOR_MESSAGES);
  const excludedSensitivePriorMessageCount = eligiblePriorMessages.length - safePriorMessages.length;
  let excludedSensitiveFeedbackCount = 0;
  const feedback = options.feedback
    ? {
        instructions: options.feedback.instructions
          .map(item => {
            const cleaned = cleanProviderContextText(item, 180);
            if (!cleaned && cleanText(item, 180)) excludedSensitiveFeedbackCount += 1;
            return cleaned;
          })
          .filter(Boolean),
        customInstruction: (() => {
          const cleaned = cleanProviderContextText(options.feedback?.customInstruction, 240);
          if (!cleaned && cleanText(options.feedback?.customInstruction, 240)) excludedSensitiveFeedbackCount += 1;
          return cleaned || undefined;
        })(),
        previousDraftExcerpt: (() => {
          const cleaned = cleanProviderContextText(options.feedback?.previousDraftExcerpt, 220);
          if (!cleaned && cleanText(options.feedback?.previousDraftExcerpt, 220)) excludedSensitiveFeedbackCount += 1;
          return cleaned || undefined;
        })()
      }
    : undefined;

  return {
    ok: true,
    request: {
      reason,
      contact: {
        name: providerSafeContactName,
        relationship: providerSafeRelationship,
        group: contact.group,
        language: contact.language,
        tone: preferences.tone,
        preferredChannel: preferences.preferredChannel,
        notesSummary: cleanText(sanitizeRecipientVisiblePersonalizationText(contact.notesSummary), 220)
      },
      event: event
        ? {
            type: event.type,
            label: cleanProviderContextText(event.label, 160) || event.type,
            date: eventOccurrenceIso(event) ?? event.date,
            verified: event.verified
          }
        : undefined,
      style: state.styleProfile.enabledForAiDrafts
        ? {
            enabled: true,
            confidence: state.styleProfile.confidence,
            formality: state.styleProfile.formality,
            language: state.styleProfile.language,
            averageLength: state.styleProfile.averageLength,
            emojiUse: state.styleProfile.emojiUse,
            commonGreetings: [...state.styleProfile.commonGreetings]
          }
        : {
            enabled: false,
            confidence: 'Disabled',
            formality: 'Use recipient-specific tone only',
            language: contact.language,
            averageLength: 160,
            emojiUse: 'Use recipient-specific tone only',
            commonGreetings: []
          },
      memories: includedMemories,
      giftHistory: giftContext.giftHistory,
      generationConstraints,
      priorApprovedMessages,
      regenerationFeedback:
        feedback && (feedback.instructions.length > 0 || feedback.customInstruction) ? feedback : undefined,
      privacy: {
        includedMemoryCount: includedMemories.length,
        includedGenerationConstraintCount: generationConstraints.length,
        excludedOptionalMemoryCount: personalization.excludedOptionalMemoryCount,
        excludedPrivateMemoryCount: personalization.excludedPrivateMemoryCount,
        excludedSensitiveMemoryCount: personalization.excludedSensitiveMemoryCount,
        includedGiftHistoryCount: giftContext.giftHistory.length,
        excludedGiftHistoryCount: giftContext.excludedGiftHistoryCount,
        excludedSensitiveGiftHistoryCount: giftContext.excludedSensitiveGiftHistoryCount,
        includedPriorMessageCount: priorApprovedMessages.length,
        excludedSensitivePriorMessageCount,
        excludedSensitiveFeedbackCount,
        excludedFields: [
          'phone',
          'email',
          'contact routes',
          'private memories',
          'sensitive memory content',
          'gift notes',
          'gift cost',
          'annual gift budget',
          'credentials',
          'raw contact provider ids',
          'activity and diagnostic logs'
        ]
      },
      outputContract: {
        format: 'json',
        variants: ['short', 'standard', 'warm'],
        maxCharactersPerVariant: MAX_VARIANT_LENGTH,
        mustRequireUserReview: true
      }
    },
    privacySummary: `${includedMemories.length} mentionable memory item(s) included; ${generationConstraints.length} generation constraint(s) included as instructions only; ${giftContext.giftHistory.length} bounded gift history item(s) included; ${giftContext.excludedGiftHistoryCount} gift history item(s) excluded, including ${giftContext.excludedSensitiveGiftHistoryCount} sensitive item(s); ${personalization.excludedOptionalMemoryCount} optional memory item(s) excluded; ${personalization.excludedPrivateMemoryCount} private item(s) excluded; ${personalization.excludedSensitiveMemoryCount} sensitive memory item(s) excluded; ${priorApprovedMessages.length} prior sent message(s) included; ${excludedSensitivePriorMessageCount} sensitive prior message(s) excluded; ${excludedSensitiveFeedbackCount} sensitive feedback field(s) excluded.`
  };
};

export const normalizeAiDraftResponse = (
  payload: unknown,
  options: AiDraftResponseValidationOptions = {}
): AiDraftResponseResult => {
  if (!isRecord(payload) || !isRecord(payload.variants)) {
    return {
      ok: false,
      error: {
        kind: 'invalid-response',
        message: 'The AI provider returned an unreadable response.'
      }
    };
  }

  const short = normalizeOutputText(payload.variants.short);
  const standard = normalizeOutputText(payload.variants.standard);
  const warm = normalizeOutputText(payload.variants.warm);

  if (
    standard.length < MIN_VARIANT_LENGTH ||
    short.length < MIN_VARIANT_LENGTH ||
    warm.length < MIN_VARIANT_LENGTH ||
    standard.length > MAX_VARIANT_LENGTH ||
    short.length > MAX_VARIANT_LENGTH ||
    warm.length > MAX_VARIANT_LENGTH
  ) {
    return {
      ok: false,
      error: {
        kind: 'invalid-response',
        message: 'The AI provider returned a missing, empty, malformed, or unsupported-length draft.'
      }
    };
  }

  const variants = {
    short,
    standard,
    warm
  };
  const safetyError = validateDraftSafety(variants, options);
  if (safetyError) {
    return {
      ok: false,
      error: safetyError
    };
  }
  const qualityError = validateDraftQuality(variants);
  return qualityError
    ? {
        ok: false,
        error: qualityError
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
