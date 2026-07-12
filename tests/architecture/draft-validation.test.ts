import fc from 'fast-check';

import { validateTemplateDraft } from '../../src/domain/validation/templateDraft';
import { validateWindowDraft } from '../../src/domain/validation/windowDraft';

const formatTime = (minutes: number): string => {
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return `${hours.toString().padStart(2, '0')}:${remainder
    .toString()
    .padStart(2, '0')}`;
};

describe('UI-only template draft validation', () => {
  it('accepts the one supported personalized placeholder', () => {
    const result = validateTemplateDraft({
      language: 'en',
      tone: 'warm',
      placeholderMode: 'given-name',
      text: 'Happy birthday, {firstName}! Wishing you a wonderful day.',
      requestedSegmentCap: 2,
    });

    expect(result.kind).toBe('valid');
    expect(result.authority).toBe('ui-only');
  });

  it('rejects unsupported placeholders, URLs, controls, and generic-name leakage', () => {
    const result = validateTemplateDraft({
      language: 'hi',
      tone: 'cheerful',
      placeholderMode: 'generic',
      text: 'See https://example.com/{lastName}\u0007 {firstName}',
      requestedSegmentCap: 2,
    });

    expect(result).toMatchObject({ kind: 'invalid', authority: 'ui-only' });
    if (result.kind === 'invalid') {
      expect(result.issues.map(value => value.code)).toEqual(
        expect.arrayContaining([
          'template-unsupported-placeholder',
          'template-placeholder-count',
          'template-control-character',
          'template-url-not-allowed',
        ]),
      );
    }
  });

  it('rejects every duplicated first-name placeholder', () => {
    fc.assert(
      fc.property(fc.string(), prefix => {
        const result = validateTemplateDraft({
          language: 'en',
          tone: 'simple',
          placeholderMode: 'given-name',
          text: `${prefix}{firstName}{firstName}`,
          requestedSegmentCap: 1,
        });
        expect(result.kind).toBe('invalid');
      }),
    );
  });
});

describe('UI-only window draft validation', () => {
  it('accepts every same-day primary window from 30 through 240 minutes', () => {
    const validWindow = fc.integer({ min: 0, max: 1409 }).chain(start =>
      fc
        .integer({ min: 30, max: Math.min(240, 1439 - start) })
        .map(duration => ({
          start,
          duration,
        })),
    );

    fc.assert(
      fc.property(validWindow, ({ start, duration }) => {
        const result = validateWindowDraft({
          primaryStart: formatTime(start),
          primaryEnd: formatTime(start + duration),
          dailyCap: 20,
        });
        expect(result.kind).toBe('valid');
        expect(result.authority).toBe('ui-only');
      }),
    );
  });

  it.each([
    { primaryStart: '09:00', primaryEnd: '09:29', dailyCap: 10 },
    { primaryStart: '09:00', primaryEnd: '13:01', dailyCap: 10 },
    { primaryStart: '23:30', primaryEnd: '00:30', dailyCap: 10 },
    {
      primaryStart: '09:00',
      primaryEnd: '11:00',
      graceEnd: '11:00',
      dailyCap: 10,
    },
    { primaryStart: '09:00', primaryEnd: '11:00', dailyCap: 0 },
    { primaryStart: '09:00', primaryEnd: '11:00', dailyCap: 21 },
    { primaryStart: '25:00', primaryEnd: '26:00', dailyCap: 10 },
  ])('rejects invalid or unsafe shape %#', draft => {
    const result = validateWindowDraft(draft);
    expect(result.kind).toBe('invalid');
    expect(result.authority).toBe('ui-only');
  });
});
