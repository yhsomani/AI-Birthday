import type {
  MessagePreviewHandle,
  PrivateDisplayName,
  PrivateMessageText,
} from '../shared/brand';
import type { FieldIssue } from '../shared/result';

export const MESSAGE_LANGUAGES = ['en', 'hi'] as const;
export const MESSAGE_TONES = ['warm', 'simple', 'cheerful'] as const;

export type MessageLanguage = (typeof MESSAGE_LANGUAGES)[number];
export type MessageTone = (typeof MESSAGE_TONES)[number];

export type PlaceholderMode =
  | Readonly<{ kind: 'given-name'; requiredCount: 1 }>
  | Readonly<{ kind: 'generic'; requiredCount: 0 }>;

export type MessageDraft = Readonly<{
  language: MessageLanguage;
  tone: MessageTone;
  placeholderMode: PlaceholderMode;
  text: PrivateMessageText;
  requestedSegmentCap: 1 | 2;
}>;

export type MessageDraftInput = Readonly<{
  language: MessageLanguage;
  tone: MessageTone;
  placeholderMode: 'given-name' | 'generic';
  text: string;
  requestedSegmentCap: number;
}>;

export type BuiltInMessageTemplate = Readonly<{
  id: 'en-personalized' | 'en-generic' | 'hi-personalized' | 'hi-generic';
  draft: MessageDraftInput;
}>;

export const BUILT_IN_MESSAGE_TEMPLATES: readonly BuiltInMessageTemplate[] = [
  {
    id: 'en-personalized',
    draft: {
      language: 'en',
      tone: 'warm',
      placeholderMode: 'given-name',
      text: 'Happy birthday, {firstName}! Wishing you a wonderful day.',
      requestedSegmentCap: 2,
    },
  },
  {
    id: 'en-generic',
    draft: {
      language: 'en',
      tone: 'warm',
      placeholderMode: 'generic',
      text: 'Happy birthday! Wishing you a wonderful day.',
      requestedSegmentCap: 2,
    },
  },
  {
    id: 'hi-personalized',
    draft: {
      language: 'hi',
      tone: 'warm',
      placeholderMode: 'given-name',
      text: 'जन्मदिन मुबारक हो, {firstName}! आपका दिन शानदार हो।',
      requestedSegmentCap: 2,
    },
  },
  {
    id: 'hi-generic',
    draft: {
      language: 'hi',
      tone: 'warm',
      placeholderMode: 'generic',
      text: 'जन्मदिन मुबारक हो! आपका दिन शानदार हो।',
      requestedSegmentCap: 2,
    },
  },
] as const;

export type MessagePreview =
  | Readonly<{
      kind: 'valid';
      handle: MessagePreviewHandle;
      examples: readonly Readonly<{
        displayName: PrivateDisplayName;
        finalText: PrivateMessageText;
        characterCount: number;
        segmentCount: number;
        encodingLabel: 'gsm-7' | 'unicode';
      }>[];
      maximumSegmentCount: number;
      affectedRecipientCount: number;
    }>
  | Readonly<{
      kind: 'invalid';
      issues: readonly FieldIssue[];
      affectedRecipientCount: number;
    }>;

export type MessageEditorProjection =
  | Readonly<{ kind: 'not-configured' }>
  | Readonly<{ kind: 'configured'; draft: MessageDraft }>;

export type MessageRelationship =
  | 'friend'
  | 'family'
  | 'colleague'
  | 'partner'
  | 'casual';
export type MessageMilestone =
  | 'none'
  | 'new-job'
  | 'graduation'
  | 'moved'
  | 'new-baby'
  | 'milestone-age';

export type GeminiRequest = Readonly<{
  language: MessageLanguage;
  tone: MessageTone;
  placeholderMode: PlaceholderMode;
  requestedSegmentCap: 1 | 2;
  relationship?: MessageRelationship | undefined;
  milestone?: MessageMilestone | undefined;
}>;

export type GeminiSuggestionsProjection =
  | Readonly<{ kind: 'requesting' }>
  | Readonly<{
      kind: 'candidates';
      candidates: readonly PrivateMessageText[];
    }>
  | Readonly<{
      kind: 'fallback';
      reason:
        | 'network-offline'
        | 'coordination-unavailable'
        | 'policy-suspended';
    }>
  | Readonly<{
      kind: 'failed';
      reason: 'unknown-native-value' | 'internal-contract-invalid';
    }>;
