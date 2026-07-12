import type {
  LatePolicy,
  WindowDraft,
  WindowDraftInput,
} from '../birthdays/model';
import type { FieldIssue, UiDraftValidation } from '../shared/result';
import {
  isLocalTime,
  localTimeToMinutes,
  type LocalTime,
} from '../shared/temporal';

const windowIssue = (): FieldIssue => ({
  field: 'window',
  code: 'invalid-window',
});

export const validateWindowDraft = (
  input: WindowDraftInput,
): UiDraftValidation<WindowDraft> => {
  const issues: FieldIssue[] = [];
  const startValid = isLocalTime(input.primaryStart);
  const endValid = isLocalTime(input.primaryEnd);
  const graceValid =
    input.graceEnd === undefined || isLocalTime(input.graceEnd);

  if (!startValid || !endValid || !graceValid) {
    issues.push(windowIssue());
  }

  if (
    !Number.isInteger(input.dailyCap) ||
    input.dailyCap < 1 ||
    input.dailyCap > 20
  ) {
    issues.push({ field: 'dailyCap', code: 'invalid-daily-cap' });
  }

  if (startValid && endValid && graceValid) {
    const start = localTimeToMinutes(input.primaryStart);
    const end = localTimeToMinutes(input.primaryEnd);
    const finalEnd =
      input.graceEnd === undefined ? end : localTimeToMinutes(input.graceEnd);
    const primaryMinutes = end - start;
    const totalMinutes = finalEnd - start;

    if (
      primaryMinutes < 30 ||
      primaryMinutes > 240 ||
      totalMinutes < primaryMinutes ||
      totalMinutes > 240 ||
      start >= end ||
      (input.graceEnd !== undefined && finalEnd <= end)
    ) {
      issues.push(windowIssue());
    }
  }

  if (issues.length > 0) {
    return {
      kind: 'invalid',
      authority: 'ui-only',
      issues: issues.filter(
        (candidate, index, all) =>
          all.findIndex(
            value =>
              value.field === candidate.field && value.code === candidate.code,
          ) === index,
      ),
    };
  }

  const primaryStart = input.primaryStart as LocalTime;
  const primaryEnd = input.primaryEnd as LocalTime;
  const latePolicy: LatePolicy =
    input.graceEnd === undefined
      ? { kind: 'none' }
      : {
          kind: 'same-day-grace',
          graceEnd: input.graceEnd as LocalTime,
        };

  return {
    kind: 'valid',
    authority: 'ui-only',
    value: {
      primaryStart,
      primaryEnd,
      latePolicy,
      dailyCap: input.dailyCap,
    },
  };
};
