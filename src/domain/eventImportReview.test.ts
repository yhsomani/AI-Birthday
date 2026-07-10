import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { CalendarImportCandidate } from './types';
import {
  MAX_STAGED_EVENT_IMPORT_CANDIDATES,
  MAX_STAGED_EVENT_TITLE_LENGTH,
  resolveEventImportReview,
  stageEventImportCandidates
} from './eventImportReview';

const candidates: CalendarImportCandidate[] = [
  {
    sourceId: 'device-calendar-42',
    title: 'Nisha Rao Birthday',
    startDate: '2026-12-05T09:00:00.000Z',
    notes: 'Confirm before messaging'
  },
  {
    sourceId: 'file-row-7',
    title: 'Aman Shah Anniversary',
    startDate: '2026-11-20'
  }
];

describe('staged event import review', () => {
  it('creates stable opaque review ids for device-calendar and file candidates', () => {
    const first = stageEventImportCandidates(candidates);
    const second = stageEventImportCandidates(structuredClone(candidates));

    assert.deepEqual(
      first.items.map(item => item.reviewId),
      second.items.map(item => item.reviewId)
    );
    assert.ok(first.items.every(item => /^event-review-[0-9a-f]{16}$/.test(item.reviewId)));
    assert.ok(first.items.every(item => !item.reviewId.includes(item.candidate.sourceId)));
    assert.equal(first.items[1].candidate.startDate, '2026-11-20T12:00:00.000Z');
  });

  it('keeps decisions pure and returns only explicitly applied or valid edited candidates', () => {
    const batch = stageEventImportCandidates(candidates);
    const before = structuredClone(batch);
    const result = resolveEventImportReview(batch, {
      [batch.items[0].reviewId]: { action: 'skip' },
      [batch.items[1].reviewId]: {
        action: 'edit',
        title: 'Aman and Leena Anniversary',
        date: '2026-11-21',
        notes: 'Edited during review'
      }
    });

    assert.deepEqual(batch, before);
    assert.deepEqual(result.skippedReviewIds, [batch.items[0].reviewId]);
    assert.deepEqual(result.unresolvedReviewIds, []);
    assert.deepEqual(result.candidatesToApply, [
      {
        sourceId: 'file-row-7',
        title: 'Aman and Leena Anniversary',
        startDate: '2026-11-21T12:00:00.000Z',
        notes: 'Edited during review'
      }
    ]);
  });

  it('does not apply invalid originals or invalid edits and reports unknown decisions', () => {
    const batch = stageEventImportCandidates([
      { sourceId: 'invalid-date', title: 'Fix during review', startDate: 'not-a-date' }
    ]);
    assert.equal(batch.items[0].valid, false);

    const result = resolveEventImportReview(batch, {
      [batch.items[0].reviewId]: {
        action: 'edit',
        title: '',
        date: '2026-02-30',
        notes: 'Still invalid'
      },
      'event-review-unknown': { action: 'apply' }
    });

    assert.deepEqual(result.candidatesToApply, []);
    assert.deepEqual(result.unresolvedReviewIds, [batch.items[0].reviewId]);
    assert.equal(result.issues[0].errors.length, 2);
    assert.deepEqual(result.unknownDecisionReviewIds, ['event-review-unknown']);
  });

  it('bounds retained candidates and rejects oversized fields before assigning ids', () => {
    const overflow = Array.from({ length: MAX_STAGED_EVENT_IMPORT_CANDIDATES + 2 }, (_, index) => ({
      sourceId: `source-${index}`,
      title: `Event ${index}`,
      startDate: '2026-12-05'
    }));
    const bounded = stageEventImportCandidates(overflow);
    const oversized = stageEventImportCandidates([
      {
        sourceId: 'oversized-title',
        title: 'x'.repeat(MAX_STAGED_EVENT_TITLE_LENGTH + 1),
        startDate: '2026-12-05'
      }
    ]);

    assert.equal(bounded.items.length, MAX_STAGED_EVENT_IMPORT_CANDIDATES);
    assert.equal(bounded.overflowCount, 2);
    assert.equal(oversized.items.length, 0);
    assert.match(oversized.rejected[0].reason, /title/i);
  });
});
