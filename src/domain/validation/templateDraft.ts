import type {
  MessageDraft,
  MessageDraftInput,
  PlaceholderMode,
} from '../messages/model';
import type { PrivateMessageText } from '../shared/brand';
import type { FieldIssue, UiDraftValidation } from '../shared/result';

const FIRST_NAME_PLACEHOLDER = '{firstName}';
const PLACEHOLDER_PATTERN = /\{[^{}]+\}/gu;
const BIDI_CONTROL_PATTERN = /[\u061C\u200E\u200F\u202A-\u202E\u2066-\u2069]/u;
const URL_PATTERN = /(?:https?:\/\/|www\.)\S+/iu;

const hasUnsafeControlCharacter = (value: string): boolean =>
  Array.from(value).some(character => {
    const codePoint = character.codePointAt(0);
    return (
      codePoint !== undefined &&
      ((codePoint <= 0x1f &&
        codePoint !== 0x09 &&
        codePoint !== 0x0a &&
        codePoint !== 0x0d) ||
        codePoint === 0x7f)
    );
  });

const issue = (code: FieldIssue['code']): FieldIssue => ({
  field: 'template',
  code,
});

export const validateTemplateDraft = (
  input: MessageDraftInput,
): UiDraftValidation<MessageDraft> => {
  const issues: FieldIssue[] = [];
  const text = input.text;

  if (text.trim().length === 0) {
    issues.push(issue('template-empty'));
  }

  const placeholders = text.match(PLACEHOLDER_PATTERN) ?? [];
  const textWithoutCompletePlaceholders = text.replace(PLACEHOLDER_PATTERN, '');
  if (
    placeholders.some(value => value !== FIRST_NAME_PLACEHOLDER) ||
    /[{}]/u.test(textWithoutCompletePlaceholders)
  ) {
    issues.push(issue('template-unsupported-placeholder'));
  }

  const firstNameCount = placeholders.filter(
    value => value === FIRST_NAME_PLACEHOLDER,
  ).length;
  const requiredCount = input.placeholderMode === 'given-name' ? 1 : 0;
  if (firstNameCount !== requiredCount) {
    issues.push(issue('template-placeholder-count'));
  }

  if (hasUnsafeControlCharacter(text)) {
    issues.push(issue('template-control-character'));
  }
  if (BIDI_CONTROL_PATTERN.test(text)) {
    issues.push(issue('template-bidi-control'));
  }
  if (URL_PATTERN.test(text)) {
    issues.push(issue('template-url-not-allowed'));
  }
  if (input.requestedSegmentCap !== 1 && input.requestedSegmentCap !== 2) {
    issues.push(issue('invalid-segment-cap'));
  }

  if (issues.length > 0) {
    return { kind: 'invalid', authority: 'ui-only', issues };
  }

  const placeholderMode: PlaceholderMode =
    input.placeholderMode === 'given-name'
      ? { kind: 'given-name', requiredCount: 1 }
      : { kind: 'generic', requiredCount: 0 };
  const requestedSegmentCap = input.requestedSegmentCap as 1 | 2;

  return {
    kind: 'valid',
    authority: 'ui-only',
    value: {
      language: input.language,
      tone: input.tone,
      placeholderMode,
      text: text as PrivateMessageText,
      requestedSegmentCap,
    },
  };
};
