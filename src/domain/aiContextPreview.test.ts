import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { buildAiDraftRequest } from './aiDrafting';
import { buildAiContextPreview } from './aiContextPreview';

describe('AI context preview contract', () => {
  it('shows selected public context while excluding private notes', () => {
    const state = createTestState();
    const preview = buildAiContextPreview(state, 'c-rajesh', 'e-rajesh-work');
    const serialized = JSON.stringify(preview);

    assert.equal(preview.privateMemoryCount, 1);
    assert.equal(preview.optionalMemories.length, 0);
    assert.match(preview.summary, /private memory item\(s\) excluded/);
    assert.doesNotMatch(serialized, /Private note excluded/i);
    assert.doesNotMatch(serialized, /rajesh@example\.com/i);
  });

  it('lets users exclude optional memories before regeneration', () => {
    const state = createTestState();
    const preview = buildAiContextPreview(state, 'c-mira', 'e-mira-checkin', {
      excludedMemoryIds: ['m-mira-1'],
      includePriorMessages: false
    });
    const request = buildAiDraftRequest(state, 'c-mira', 'e-mira-checkin', 'Check-in', {
      excludedMemoryIds: ['m-mira-1'],
      includePriorMessages: false
    });

    assert.equal(preview.optionalMemories[0].selected, false);
    assert.equal(preview.priorMessages.selected, false);
    assert.equal(request.ok, true);
    if (request.ok) {
      assert.equal(request.request.memories.length, 0);
      assert.equal(request.request.priorApprovedMessages.length, 0);
      assert.equal(request.request.privacy.excludedOptionalMemoryCount, 1);
    }
  });

  it('keeps prior message text out of the preview while reporting eligibility', () => {
    const approved = relateReducer(createTestState(), {
      type: 'approveMessage',
      messageId: 'msg-mira-checkin'
    });
    const state = relateReducer(approved, {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const preview = buildAiContextPreview(state, 'c-mira', 'e-mira-checkin');

    assert.equal(preview.priorMessages.count, 1);
    assert.equal(preview.priorMessages.selected, true);
    assert.doesNotMatch(JSON.stringify(preview), /Pune and the new design role/);
  });
});
