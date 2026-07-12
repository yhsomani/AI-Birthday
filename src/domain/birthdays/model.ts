import type { PolicyReviewHandle } from '../shared/brand';
import type { FieldIssue } from '../shared/result';
import type { LocalDate, LocalTime } from '../shared/temporal';

export const LEAP_DAY_POLICIES = ['feb-28', 'mar-01', 'skip'] as const;
export type LeapDayPolicy = (typeof LEAP_DAY_POLICIES)[number];

export type LatePolicy =
  | Readonly<{ kind: 'none' }>
  | Readonly<{ kind: 'same-day-grace'; graceEnd: LocalTime }>;

export type WindowDraft = Readonly<{
  primaryStart: LocalTime;
  primaryEnd: LocalTime;
  latePolicy: LatePolicy;
  dailyCap: number;
}>;

export type WindowDraftInput = Readonly<{
  primaryStart: string;
  primaryEnd: string;
  graceEnd?: string | undefined;
  dailyCap: number;
}>;

export type PolicyEditorProjection =
  | Readonly<{ kind: 'not-configured' }>
  | Readonly<{ kind: 'configured'; draft: WindowDraft }>;

export type PolicyPreview =
  | Readonly<{
      kind: 'valid';
      handle: PolicyReviewHandle;
      summary: string;
      simulatedDays: 400;
      maximumPlannedInLocalDay: number;
      maximumPlannedInRolling24Hours: number;
    }>
  | Readonly<{
      kind: 'invalid';
      issues: readonly FieldIssue[];
      firstConflictDate?: LocalDate | undefined;
    }>;
