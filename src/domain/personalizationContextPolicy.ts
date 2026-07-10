import type { AppState, MemoryCategory, MemoryNote } from './types';

export const MAX_MENTIONABLE_MEMORY_ITEMS = 5;
export const MAX_MENTIONABLE_MEMORY_CHARACTERS = 240;
export const MAX_GENERATION_CONSTRAINTS = 5;
export const MAX_GENERATION_CONSTRAINT_CHARACTERS = 180;

export type PersonalizationMemoryClassification =
  'mentionable-fact' | 'generation-guidance' | 'mixed' | 'private' | 'excluded-sensitive' | 'empty';

export type GenerationConstraintKind = 'avoid' | 'language-style' | 'tone-style' | 'other';

export interface MentionableMemoryFact {
  memoryId: string;
  category: MemoryCategory;
  text: string;
}

export interface MemoryGenerationConstraint {
  memoryId: string;
  kind: GenerationConstraintKind;
  instruction: string;
}

export interface ClassifiedPersonalizationMemory {
  memoryId: string;
  classification: PersonalizationMemoryClassification;
  mentionableText?: string;
  constraints: MemoryGenerationConstraint[];
  sensitiveContentExcluded: boolean;
}

export interface MemoryPersonalizationContext {
  mentionableFacts: MentionableMemoryFact[];
  generationConstraints: MemoryGenerationConstraint[];
  includedMentionableMemoryCount: number;
  includedGuidanceMemoryCount: number;
  excludedOptionalMemoryCount: number;
  excludedPrivateMemoryCount: number;
  excludedSensitiveMemoryCount: number;
}

export interface MemoryPersonalizationContextOptions {
  contactId?: string;
  excludedMemoryIds?: readonly string[];
  maxMentionableItems?: number;
  maxGenerationConstraints?: number;
}

type TextClassification = {
  mentionableSegments: string[];
  constraints: {
    kind: GenerationConstraintKind;
    instruction: string;
  }[];
  sensitiveContentExcluded: boolean;
};

const controlCharacters = /[\u0000-\u001f\u007f]/g;
const recipientRoutePattern = /(?:\+?\d[\d\s().-]{7,}\d|[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|https?:\/\/|www\.)/i;
const credentialPattern =
  /\b(?:api[- ]?key|app password|credential|otp|passcode|password|secret token|access token|private key)\b/i;

const explicitMentionPrefix =
  /^(?:message should mention|messages? (?:can|may) mention|mention in messages?|safe to mention|recipient-visible fact|relationship context)\s*[:\-]\s*/i;
const explicitAvoidPrefix =
  /^(?:avoidance|avoid(?: in (?:messages?|wishes?|drafts?))?|do not mention|don't mention|never mention|omit from (?:messages?|wishes?|drafts?))\s*[:\-]?\s*/i;
const explicitLanguageStylePrefix =
  /^(?:preferred language(?:\s*\/\s*style)?|language(?: preference)?|writing language|message language)\s*[:\-]\s*/i;
const explicitToneStylePrefix =
  /^(?:preferred (?:tone|style)|(?:message )?(?:tone|style)(?: preference| constraint)?)\s*[:\-]\s*/i;
const genericGuidancePrefix = /^(?:instruction|guidance|message rule|writing rule)\s*[:\-]\s*/i;

const avoidGuidancePattern =
  /^(?:please )?(?:avoid|do not|don't|never|omit|exclude)\b|\b(?:never mention|do not mention|don't mention|should not mention)\b/i;
const languageGuidancePattern =
  /\b(?:preferred language|language preference|message language|writing language)\b|^(?:write|draft|respond)\s+(?:in|using)\b/i;
const toneStyleGuidancePattern =
  /\bno emojis?\b|\b(?:formal|informal|concise|playful|warm|respectful)\s+(?:tone|style|messages?|notes?|wishes?|drafts?)\b|\b(?:tone|style|language|formality|emoji use|messages?|notes?|wishes?|drafts?)\s+(?:should|must|needs? to|ought to)\b|^(?:keep|make)\s+(?:it|the (?:message|wish|draft)|messages?|wishes?|drafts?)\b/i;
const imperativeGuidancePattern = /^(?:please )?(?:mention|include|omit|exclude|keep|make|use|write|draft)\b/i;

const normalize = (value: string, maxCharacters: number) =>
  value.replace(controlCharacters, ' ').replace(/\s+/g, ' ').trim().slice(0, maxCharacters).trim();

const containsSensitiveContent = (value: string) => recipientRoutePattern.test(value) || credentialPattern.test(value);

const guidanceKind = (segment: string): GenerationConstraintKind | undefined => {
  if (explicitAvoidPrefix.test(segment) || avoidGuidancePattern.test(segment)) {
    return 'avoid';
  }
  if (explicitLanguageStylePrefix.test(segment) || languageGuidancePattern.test(segment)) {
    return 'language-style';
  }
  if (explicitToneStylePrefix.test(segment) || toneStyleGuidancePattern.test(segment)) {
    return 'tone-style';
  }
  if (genericGuidancePrefix.test(segment) || imperativeGuidancePattern.test(segment)) {
    return 'other';
  }
  return undefined;
};

const splitSegments = (value: string) =>
  value
    .split(/(?<=[.!?])\s+|\n+|\s*;\s*/)
    .map(segment => normalize(segment, MAX_MENTIONABLE_MEMORY_CHARACTERS))
    .filter(Boolean);

const classifyText = (value: string): TextClassification => {
  const normalized = normalize(value, 500);
  if (!normalized) {
    return {
      mentionableSegments: [],
      constraints: [],
      sensitiveContentExcluded: false
    };
  }

  if (containsSensitiveContent(normalized)) {
    return {
      mentionableSegments: [],
      constraints: [],
      sensitiveContentExcluded: true
    };
  }

  const explicitMention = normalized.match(explicitMentionPrefix);
  if (explicitMention) {
    const segments = splitSegments(normalized.slice(explicitMention[0].length));
    const fact = segments[0];
    const trailing = segments.slice(1);
    const constraints = trailing
      .map(segment => {
        const kind = guidanceKind(segment);
        return kind
          ? {
              kind,
              instruction: normalize(segment, MAX_GENERATION_CONSTRAINT_CHARACTERS)
            }
          : undefined;
      })
      .filter((constraint): constraint is NonNullable<typeof constraint> => Boolean(constraint));
    const trailingFacts = trailing.filter(segment => !guidanceKind(segment));
    return {
      mentionableSegments: [fact, ...trailingFacts].filter(Boolean),
      constraints,
      sensitiveContentExcluded: false
    };
  }

  const wholeTextGuidanceKind = guidanceKind(normalized);
  if (
    explicitAvoidPrefix.test(normalized) ||
    explicitLanguageStylePrefix.test(normalized) ||
    explicitToneStylePrefix.test(normalized) ||
    genericGuidancePrefix.test(normalized)
  ) {
    return {
      mentionableSegments: [],
      constraints: wholeTextGuidanceKind
        ? [
            {
              kind: wholeTextGuidanceKind,
              instruction: normalize(normalized, MAX_GENERATION_CONSTRAINT_CHARACTERS)
            }
          ]
        : [],
      sensitiveContentExcluded: false
    };
  }

  const mentionableSegments: string[] = [];
  const constraints: TextClassification['constraints'] = [];
  for (const segment of splitSegments(normalized)) {
    if (containsSensitiveContent(segment)) {
      return {
        mentionableSegments: [],
        constraints: [],
        sensitiveContentExcluded: true
      };
    }
    const kind = guidanceKind(segment);
    if (kind) {
      constraints.push({
        kind,
        instruction: normalize(segment, MAX_GENERATION_CONSTRAINT_CHARACTERS)
      });
    } else {
      mentionableSegments.push(segment);
    }
  }

  return {
    mentionableSegments,
    constraints,
    sensitiveContentExcluded: false
  };
};

export const sanitizeRecipientVisiblePersonalizationText = (value: string | undefined) => {
  if (!value) {
    return undefined;
  }
  const classification = classifyText(value);
  const mentionableText = normalize(classification.mentionableSegments.join(' '), MAX_MENTIONABLE_MEMORY_CHARACTERS);
  return mentionableText || undefined;
};

export const classifyMemoryForPersonalization = (memory: MemoryNote): ClassifiedPersonalizationMemory => {
  if (memory.category === 'Private') {
    return {
      memoryId: memory.id,
      classification: 'private',
      constraints: [],
      sensitiveContentExcluded: false
    };
  }

  const text = classifyText(memory.body);
  if (text.sensitiveContentExcluded) {
    return {
      memoryId: memory.id,
      classification: 'excluded-sensitive',
      constraints: [],
      sensitiveContentExcluded: true
    };
  }

  const mentionableText = normalize(text.mentionableSegments.join(' '), MAX_MENTIONABLE_MEMORY_CHARACTERS);
  const constraints = text.constraints
    .filter(constraint => constraint.instruction.length > 0)
    .map(constraint => ({
      memoryId: memory.id,
      ...constraint
    }));
  const classification: PersonalizationMemoryClassification =
    mentionableText && constraints.length > 0
      ? 'mixed'
      : mentionableText
        ? 'mentionable-fact'
        : constraints.length > 0
          ? 'generation-guidance'
          : 'empty';

  return {
    memoryId: memory.id,
    classification,
    mentionableText: mentionableText || undefined,
    constraints,
    sensitiveContentExcluded: false
  };
};

export const buildMemoryPersonalizationContext = (
  memories: readonly MemoryNote[],
  options: MemoryPersonalizationContextOptions = {}
): MemoryPersonalizationContext => {
  const excludedMemoryIds = new Set(options.excludedMemoryIds ?? []);
  const maxMentionableItems = Math.max(
    0,
    Math.min(options.maxMentionableItems ?? MAX_MENTIONABLE_MEMORY_ITEMS, MAX_MENTIONABLE_MEMORY_ITEMS)
  );
  const maxGenerationConstraints = Math.max(
    0,
    Math.min(options.maxGenerationConstraints ?? MAX_GENERATION_CONSTRAINTS, MAX_GENERATION_CONSTRAINTS)
  );
  const mentionableFacts: MentionableMemoryFact[] = [];
  const generationConstraints: MemoryGenerationConstraint[] = [];
  const guidanceMemoryIds = new Set<string>();
  let excludedOptionalMemoryCount = 0;
  let excludedPrivateMemoryCount = 0;
  let excludedSensitiveMemoryCount = 0;

  for (const memory of memories) {
    if (options.contactId && memory.contactId !== options.contactId) {
      continue;
    }
    if (memory.category === 'Private') {
      excludedPrivateMemoryCount += 1;
      continue;
    }
    if (excludedMemoryIds.has(memory.id)) {
      excludedOptionalMemoryCount += 1;
      continue;
    }

    const classified = classifyMemoryForPersonalization(memory);
    if (classified.sensitiveContentExcluded) {
      excludedSensitiveMemoryCount += 1;
      continue;
    }
    if (classified.mentionableText && mentionableFacts.length < maxMentionableItems) {
      mentionableFacts.push({
        memoryId: memory.id,
        category: memory.category,
        text: classified.mentionableText
      });
    }
    if (classified.constraints.length > 0) {
      guidanceMemoryIds.add(memory.id);
      const remaining = maxGenerationConstraints - generationConstraints.length;
      if (remaining > 0) {
        generationConstraints.push(...classified.constraints.slice(0, remaining));
      }
    }
  }

  return {
    mentionableFacts,
    generationConstraints,
    includedMentionableMemoryCount: mentionableFacts.length,
    includedGuidanceMemoryCount: guidanceMemoryIds.size,
    excludedOptionalMemoryCount,
    excludedPrivateMemoryCount,
    excludedSensitiveMemoryCount
  };
};

/** Reducer-safe integration helper: returns only recipient-mentionable memory text. */
export const firstMentionableMemoryTextForContact = (
  state: Pick<AppState, 'memories'>,
  contactId: string,
  excludedMemoryIds: readonly string[] = []
) =>
  buildMemoryPersonalizationContext(state.memories, {
    contactId,
    excludedMemoryIds,
    maxMentionableItems: 1,
    maxGenerationConstraints: 0
  }).mentionableFacts[0]?.text;
