import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { resolveEventImportReview, stageEventImportCandidates } from '../domain/eventImportReview';
import { createTestState } from '../test/testState';
import { relateReducer } from './relateReducer';

describe('reviewed event import reducer boundary', () => {
  it('does not mutate state during staging and applies only selected or edited candidates', () => {
    const state = createTestState();
    const before = structuredClone(state);
    const batch = stageEventImportCandidates([
      {
        sourceId: 'device-selected',
        title: 'Selected Person Birthday',
        startDate: '2026-12-05'
      },
      {
        sourceId: 'file-skipped',
        title: 'Skipped Person Birthday',
        startDate: '2026-12-06'
      }
    ]);
    const reviewed = resolveEventImportReview(batch, {
      [batch.items[0].reviewId]: {
        action: 'edit',
        title: 'Selected Person Anniversary',
        date: '2026-12-07',
        notes: 'Reviewed before import'
      },
      [batch.items[1].reviewId]: { action: 'skip' }
    });

    assert.deepEqual(state, before);
    const next = relateReducer(state, {
      type: 'calendarImported',
      candidates: reviewed.candidatesToApply
    });

    assert.equal(next.events.length, state.events.length + 1);
    assert.equal(next.contacts.length, state.contacts.length + 1);
    assert.equal(next.events[0].label, 'Selected Person Anniversary');
    assert.equal(next.events[0].date, '2026-12-07T12:00:00.000Z');
    assert.equal(
      next.events.some(event => event.label.includes('Skipped Person')),
      false
    );
    assert.equal(next.calendarSync.importedCount, state.calendarSync.importedCount + 1);
  });

  it('keeps state-aware same-name conflicts staged until an explicit separate-contact resolution', () => {
    const state = createTestState();
    const candidate = {
      sourceId: 'calendar-same-name-review',
      title: 'Asha Mehra Birthday',
      startDate: state.events.find(event => event.id === 'e-asha-bday')?.date ?? '2027-07-09T12:00:00.000Z'
    };
    const staged = relateReducer(state, { type: 'calendarImported', candidates: [candidate] });
    const resolved = relateReducer(state, {
      type: 'calendarImported',
      candidates: [candidate],
      resolutions: {
        [candidate.sourceId]: { action: 'create-separate' }
      }
    });

    assert.equal(staged.contacts.length, state.contacts.length);
    assert.equal(staged.events.length, state.events.length);
    assert.match(staged.activity[0].detail, /1 need review/i);
    assert.equal(resolved.contacts.length, state.contacts.length + 1);
    assert.equal(resolved.events.length, state.events.length + 1);
    assert.equal(
      resolved.events[0].sourceIdentities?.some(identity => identity.sourceId === candidate.sourceId),
      true
    );
  });
});
