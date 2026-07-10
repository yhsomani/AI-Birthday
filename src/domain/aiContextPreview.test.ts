import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
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

  it('previews guidance as instructions rather than recipient-mentionable memories', () => {
    const base = createTestState();
    const state = {
      ...base,
      memories: [
        {
          ...base.memories[0],
          id: 'fact',
          category: 'Milestone' as const,
          body: 'Finished a first marathon in May.'
        },
        {
          ...base.memories[0],
          id: 'avoid',
          body: 'Avoid in messages: office gossip.'
        },
        {
          ...base.memories[0],
          id: 'language',
          body: 'Preferred language/style: formal Hindi.'
        },
        {
          ...base.memories[0],
          id: 'private',
          category: 'Private' as const,
          body: 'Secret family detail.'
        }
      ]
    };
    const preview = buildAiContextPreview(state, 'c-asha', 'e-asha-bday');

    assert.deepEqual(
      preview.optionalMemories.map(memory => memory.body),
      ['Finished a first marathon in May.']
    );
    assert.deepEqual(
      preview.generationConstraints.map(constraint => constraint.kind),
      ['avoid', 'language-style']
    );
    assert.equal(preview.privateMemoryCount, 1);
    assert.doesNotMatch(JSON.stringify(preview.optionalMemories), /office gossip|formal Hindi/i);
    assert.doesNotMatch(JSON.stringify(preview), /Secret family detail/i);
    assert.match(preview.summary, /generation constraint\(s\) selected as instructions only/i);
  });

  it('keeps prior message text out of the preview while reporting eligibility', () => {
    const base = createTestState();
    const state = {
      ...base,
      messages: base.messages.map(message =>
        message.id === 'msg-mira-checkin'
          ? {
              ...message,
              status: 'Sent' as const,
              sentAt: '2026-07-09T10:00:00.000Z'
            }
          : message
      )
    };
    const preview = buildAiContextPreview(state, 'c-mira', 'e-mira-checkin');

    assert.equal(preview.priorMessages.count, 1);
    assert.equal(preview.priorMessages.selected, true);
    assert.doesNotMatch(JSON.stringify(preview), /Pune and the new design role/);
  });
});
