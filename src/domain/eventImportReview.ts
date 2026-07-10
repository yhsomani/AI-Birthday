import type { CalendarImportCandidate } from './types';

export const MAX_STAGED_EVENT_IMPORT_CANDIDATES = 5_000;
export const MAX_STAGED_EVENT_SOURCE_ID_LENGTH = 256;
export const MAX_STAGED_EVENT_TITLE_LENGTH = 160;
export const MAX_STAGED_EVENT_DATE_LENGTH = 128;
export const MAX_STAGED_EVENT_NOTES_LENGTH = 2_000;

export interface StagedEventImportCandidate {
  reviewId: string;
  candidate: Readonly<CalendarImportCandidate>;
  valid: boolean;
  validationErrors: string[];
}

export interface EventImportStageRejection {
  index: number;
  reason: string;
}

export interface StagedEventImportBatch {
  items: StagedEventImportCandidate[];
  rejected: EventImportStageRejection[];
  overflowCount: number;
}

export type EventImportReviewDecision =
  { action: 'apply' } | { action: 'skip' } | { action: 'edit'; title: string; date: string; notes?: string };

export type EventImportReviewDecisions = Readonly<Record<string, EventImportReviewDecision | undefined>>;

export interface EventImportReviewIssue {
  reviewId: string;
  errors: string[];
}

export interface EventImportReviewResolution {
  candidatesToApply: CalendarImportCandidate[];
  skippedReviewIds: string[];
  unresolvedReviewIds: string[];
  issues: EventImportReviewIssue[];
  unknownDecisionReviewIds: string[];
}

type CandidateValidation =
  | { ok: true; candidate: CalendarImportCandidate }
  | { ok: false; candidate: CalendarImportCandidate; errors: string[] };

const normalizedDate = (value: string): string | undefined => {
  const trimmed = value.trim();
  const calendarDay = /^(\d{4})-(\d{2})-(\d{2})$/.exec(trimmed);
  if (calendarDay) {
    const year = Number(calendarDay[1]);
    const month = Number(calendarDay[2]);
    const day = Number(calendarDay[3]);
    const date = new Date(Date.UTC(year, month - 1, day, 12, 0, 0, 0));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
      return undefined;
    }
    return date.toISOString();
  }
  const date = new Date(trimmed);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
};

const normalizeCandidate = (candidate: CalendarImportCandidate): CandidateValidation => {
  const sourceId = candidate.sourceId.trim();
  const title = candidate.title.trim().replace(/\s+/g, ' ');
  const startDateInput = candidate.startDate.trim();
  const notes = candidate.notes?.trim().replace(/\s+/g, ' ') || undefined;
  const date = normalizedDate(startDateInput);
  const normalized: CalendarImportCandidate = {
    sourceId,
    title,
    startDate: date ?? startDateInput,
    ...(notes ? { notes } : {})
  };
  const errors: string[] = [];
  if (!sourceId) errors.push('The import source id is required.');
  if (sourceId.length > MAX_STAGED_EVENT_SOURCE_ID_LENGTH) {
    errors.push(`The import source id must be ${MAX_STAGED_EVENT_SOURCE_ID_LENGTH} characters or fewer.`);
  }
  if (title.length < 2) errors.push('The event title is required.');
  if (title.length > MAX_STAGED_EVENT_TITLE_LENGTH) {
    errors.push(`The event title must be ${MAX_STAGED_EVENT_TITLE_LENGTH} characters or fewer.`);
  }
  if (startDateInput.length > MAX_STAGED_EVENT_DATE_LENGTH) {
    errors.push(`The event date must be ${MAX_STAGED_EVENT_DATE_LENGTH} characters or fewer.`);
  } else if (!date) {
    errors.push('Enter a valid event date.');
  }
  if ((notes?.length ?? 0) > MAX_STAGED_EVENT_NOTES_LENGTH) {
    errors.push(`Event notes must be ${MAX_STAGED_EVENT_NOTES_LENGTH} characters or fewer.`);
  }
  return errors.length > 0 ? { ok: false, candidate: normalized, errors } : { ok: true, candidate: normalized };
};

const structurallyBounded = (candidate: CalendarImportCandidate) => {
  if (candidate.sourceId.length > MAX_STAGED_EVENT_SOURCE_ID_LENGTH) {
    return `The import source id must be ${MAX_STAGED_EVENT_SOURCE_ID_LENGTH} characters or fewer.`;
  }
  if (candidate.title.length > MAX_STAGED_EVENT_TITLE_LENGTH) {
    return `The event title must be ${MAX_STAGED_EVENT_TITLE_LENGTH} characters or fewer.`;
  }
  if (candidate.startDate.length > MAX_STAGED_EVENT_DATE_LENGTH) {
    return `The event date must be ${MAX_STAGED_EVENT_DATE_LENGTH} characters or fewer.`;
  }
  if ((candidate.notes?.length ?? 0) > MAX_STAGED_EVENT_NOTES_LENGTH) {
    return `Event notes must be ${MAX_STAGED_EVENT_NOTES_LENGTH} characters or fewer.`;
  }
  return undefined;
};

const fingerprint = (value: string): string => {
  let first = 2166136261;
  let second = 2246822519;
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    first = Math.imul(first ^ code, 16777619);
    second = Math.imul(second ^ code, 3266489917);
  }
  return `${(first >>> 0).toString(16).padStart(8, '0')}${(second >>> 0).toString(16).padStart(8, '0')}`;
};

const reviewIdFor = (candidate: CalendarImportCandidate) =>
  `event-review-${fingerprint(
    JSON.stringify([candidate.sourceId, candidate.title, candidate.startDate, candidate.notes ?? ''])
  )}`;

export const stageEventImportCandidates = (candidates: readonly CalendarImportCandidate[]): StagedEventImportBatch => {
  const items: StagedEventImportCandidate[] = [];
  const rejected: EventImportStageRejection[] = [];
  const reviewIdOccurrences = new Map<string, number>();
  const bounded = candidates.slice(0, MAX_STAGED_EVENT_IMPORT_CANDIDATES);

  bounded.forEach((candidate, index) => {
    const boundError = structurallyBounded(candidate);
    if (boundError) {
      rejected.push({ index, reason: boundError });
      return;
    }
    const validation = normalizeCandidate(candidate);
    const baseReviewId = reviewIdFor(validation.candidate);
    const occurrence = (reviewIdOccurrences.get(baseReviewId) ?? 0) + 1;
    reviewIdOccurrences.set(baseReviewId, occurrence);
    const reviewId = occurrence === 1 ? baseReviewId : `${baseReviewId}-${occurrence}`;
    items.push({
      reviewId,
      candidate: { ...validation.candidate },
      valid: validation.ok,
      validationErrors: validation.ok ? [] : [...validation.errors]
    });
  });

  return {
    items,
    rejected,
    overflowCount: Math.max(0, candidates.length - bounded.length)
  };
};

export const resolveEventImportReview = (
  batch: StagedEventImportBatch,
  decisions: EventImportReviewDecisions
): EventImportReviewResolution => {
  const candidatesToApply: CalendarImportCandidate[] = [];
  const skippedReviewIds: string[] = [];
  const unresolvedReviewIds: string[] = [];
  const issues: EventImportReviewIssue[] = [];
  const knownReviewIds = new Set(batch.items.map(item => item.reviewId));

  for (const item of batch.items) {
    const decision = decisions[item.reviewId];
    if (!decision) {
      unresolvedReviewIds.push(item.reviewId);
      continue;
    }
    if (decision.action === 'skip') {
      skippedReviewIds.push(item.reviewId);
      continue;
    }
    const candidate =
      decision.action === 'edit'
        ? {
            sourceId: item.candidate.sourceId,
            title: decision.title,
            startDate: decision.date,
            notes: decision.notes
          }
        : item.candidate;
    const validation = normalizeCandidate(candidate);
    if (!validation.ok) {
      unresolvedReviewIds.push(item.reviewId);
      issues.push({ reviewId: item.reviewId, errors: [...validation.errors] });
      continue;
    }
    candidatesToApply.push({ ...validation.candidate });
  }

  return {
    candidatesToApply,
    skippedReviewIds,
    unresolvedReviewIds,
    issues,
    unknownDecisionReviewIds: Object.keys(decisions).filter(reviewId => !knownReviewIds.has(reviewId))
  };
};
