import type { MessageDraftInput } from '../messages/model';
import { BUILT_IN_MESSAGE_TEMPLATES } from '../messages/model';
import {
  BIRTHDAY_MESSAGE_CONTENT_CATEGORIES,
  BIRTHDAY_MESSAGE_SEMANTIC_POLICY_VERSION,
  classifyBirthdayMessageContent,
  validateTemplateDraft,
} from './templateDraft';

type FileSystem = Readonly<{
  readFileSync(file: string, encoding: 'utf8'): string;
}>;
type PathApi = Readonly<{
  resolve(...parts: string[]): string;
}>;
type SemanticPolicyFixture = Readonly<{
  schemaVersion: number;
  policyVersion: string;
  cases: readonly Readonly<{
    id: string;
    language: MessageDraftInput['language'];
    text: string;
    expectedCategories: readonly string[];
  }>[];
}>;
declare const __dirname: string;
const fs = require('fs') as FileSystem;
const path = require('path') as PathApi;

const draft = (
  text: string,
  language: MessageDraftInput['language'] = 'en',
): MessageDraftInput => ({
  language,
  tone: 'warm' as const,
  placeholderMode: 'given-name' as const,
  text,
  requestedSegmentCap: 2,
});

describe('templateDraft content safety', () => {
  it('classifies every shared cross-platform semantic fixture identically', () => {
    const fixture = JSON.parse(
      fs.readFileSync(
        path.resolve(
          __dirname,
          '../../../contracts/birthday-message-semantic-policy-v2.json',
        ),
        'utf8',
      ),
    ) as SemanticPolicyFixture;

    expect(fixture.schemaVersion).toBe(1);
    expect(fixture.policyVersion).toBe(
      BIRTHDAY_MESSAGE_SEMANTIC_POLICY_VERSION,
    );
    const knownCategories = new Set<string>(
      BIRTHDAY_MESSAGE_CONTENT_CATEGORIES,
    );
    expect(
      fixture.cases.every(fixtureCase =>
        fixtureCase.expectedCategories.every(category =>
          knownCategories.has(category),
        ),
      ),
    ).toBe(true);
    const mismatches = fixture.cases.flatMap(fixtureCase => {
      const actual = classifyBirthdayMessageContent(
        fixtureCase.text,
        fixtureCase.language,
      );
      return JSON.stringify(actual) ===
        JSON.stringify(fixtureCase.expectedCategories)
        ? []
        : [
            {
              id: fixtureCase.id,
              expected: fixtureCase.expectedCategories,
              actual,
            },
          ];
    });
    expect(mismatches).toEqual([]);
  });

  it.each([
    ['Limited offer for {firstName}', 'template-promotional-content'],
    [
      'Happy birthday, {firstName}! #bestfriend',
      'template-tracking-not-allowed',
    ],
    ['Happy 30th birthday, {firstName}', 'template-sensitive-content'],
    ['Remember when, {firstName}', 'template-sensitive-content'],
    [
      'Wishing you a wonderful day, {firstName}!',
      'template-birthday-intent-required',
    ],
    [
      'Happy birthday, {firstName}! Email winner@example.com',
      'template-url-not-allowed',
    ],
    [
      'Happy birthday, {firstName}! Use my referral code: PARTY',
      'template-tracking-not-allowed',
    ],
    [
      'Happy birthday, {firstName}! As a woman, you inspire everyone.',
      'template-sensitive-content',
    ],
    [
      'Happy birthday to my best friend, {firstName}!',
      'template-sensitive-content',
    ],
    [
      'Happy birthday, {firstName}! Send me a nude photo.',
      'template-sensitive-content',
    ],
    [
      'Happy birthday, {firstName}! You should kill yourself.',
      'template-sensitive-content',
    ],
    ['Happy birthday, {firstName}!\u200B', 'template-control-character'],
    [
      'Happy birthday, {firstName}!\nHave a wonderful day.',
      'template-control-character',
    ],
  ] as const)('rejects %s with an actionable reason', (text, expectedCode) => {
    const result = validateTemplateDraft(draft(text));

    expect(result.kind).toBe('invalid');
    if (result.kind === 'invalid') {
      expect(result.issues.map(issue => issue.code)).toContain(expectedCode);
    }
  });

  it.each([
    'Happy birthday, {firstName}! Wishing you good health and joy.',
    'Happy birthday, my friend {firstName}! Enjoy every gift and smile.',
    'Happy birthday, {firstName}! Keep your faith in yourself.',
    'Happy birthday, {firstName}! Remember to celebrate your wonderful day.',
    'Have a killer smile and a fantastic birthday, {firstName}!',
    'Many happy returns, {firstName}! Wishing you a lovely day.',
  ])('preserves benign near-match: %s', text => {
    expect(validateTemplateDraft(draft(text))).toMatchObject({ kind: 'valid' });
  });

  it('applies the same semantic and script policy to Hindi', () => {
    const safe = validateTemplateDraft(
      draft(
        'जन्मदिन मुबारक हो, {firstName}! अच्छे स्वास्थ्य और खुशियों की शुभकामनाएँ।',
        'hi',
      ),
    );
    const unsafe = validateTemplateDraft(
      draft('जन्मदिन मुबारक हो, {firstName}! आपकी बीमारी याद है।', 'hi'),
    );

    expect(safe).toMatchObject({ kind: 'valid' });
    expect(unsafe).toMatchObject({ kind: 'invalid' });
    if (unsafe.kind === 'invalid') {
      expect(unsafe.issues.map(issue => issue.code)).toContain(
        'template-sensitive-content',
      );
    }
  });

  it('rejects a declared-language mismatch', () => {
    const result = validateTemplateDraft(
      draft('Happy birthday, {firstName}!', 'hi'),
    );

    expect(result).toMatchObject({ kind: 'invalid' });
    if (result.kind === 'invalid') {
      expect(result.issues.map(issue => issue.code)).toContain(
        'template-language-mismatch',
      );
    }
  });

  it('keeps every reliable built-in template valid', () => {
    for (const template of BUILT_IN_MESSAGE_TEMPLATES) {
      expect(validateTemplateDraft(template.draft)).toMatchObject({
        kind: 'valid',
      });
    }
  });
});
