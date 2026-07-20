import { validateWindowDraft } from './windowDraft';

const validate = (
  primaryStart: string,
  primaryEnd: string,
  dailyCap: number,
  graceEnd?: string,
) =>
  validateWindowDraft({
    primaryStart,
    primaryEnd,
    dailyCap,
    ...(graceEnd === undefined ? {} : { graceEnd }),
  });

describe('validateWindowDraft schedule boundaries', () => {
  it('accepts a four-hour total window and rejects one minute more', () => {
    expect(validate('09:00', '12:00', 10, '13:00').kind).toBe('valid');
    expect(validate('09:00', '12:00', 10, '13:01')).toEqual(
      expect.objectContaining({
        kind: 'invalid',
        issues: expect.arrayContaining([
          expect.objectContaining({ code: 'invalid-window' }),
        ]),
      }),
    );
  });

  it('keeps grace on the same civil day and rejects an overnight value', () => {
    expect(validate('20:00', '23:00', 10, '23:59').kind).toBe('valid');
    expect(validate('20:00', '23:00', 10, '00:30')).toEqual(
      expect.objectContaining({
        kind: 'invalid',
        issues: expect.arrayContaining([
          expect.objectContaining({ code: 'invalid-window' }),
        ]),
      }),
    );
  });

  it.each([1, 20])('accepts the inclusive daily-cap boundary %i', dailyCap => {
    expect(validate('09:00', '11:00', dailyCap).kind).toBe('valid');
  });

  it.each([0, 21])('rejects the out-of-range daily cap %i', dailyCap => {
    expect(validate('09:00', '11:00', dailyCap)).toEqual(
      expect.objectContaining({
        kind: 'invalid',
        issues: expect.arrayContaining([
          expect.objectContaining({ code: 'invalid-daily-cap' }),
        ]),
      }),
    );
  });
});
