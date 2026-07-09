import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createInitialState, relateReducer } from '../state/relateReducer';
import { buildContactTimeline } from './contactTimeline';

describe('contact timeline contract', () => {
  it('combines events, memories, gifts, and sent messages for a contact', () => {
    const state = relateReducer(createInitialState(), {
      type: 'manualHandoff',
      messageId: 'msg-asha-bday',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const timeline = buildContactTimeline(state, 'c-asha');
    const types = new Set(timeline.entries.map(entry => entry.type));

    assert.ok(types.has('Events'));
    assert.ok(types.has('Memories'));
    assert.ok(types.has('Gifts'));
    assert.ok(types.has('Messages'));
    assert.ok(timeline.entries.every((entry, index, entries) => index === 0 || entries[index - 1].dateIso >= entry.dateIso));
  });

  it('filters sent message history without exposing unsent drafts', () => {
    const state = relateReducer(createInitialState(), {
      type: 'manualHandoff',
      messageId: 'msg-mira-checkin',
      nowIso: '2026-07-09T10:00:00.000Z'
    });
    const timeline = buildContactTimeline(state, 'c-mira', 'Messages');

    assert.equal(timeline.entries.length, 1);
    assert.equal(timeline.entries[0].id, 'msg-mira-checkin');
    assert.equal(timeline.entries[0].targetScreen, 'chatHistory');
  });

  it('returns a useful empty state for missing contacts and empty filters', () => {
    const state = createInitialState();
    const missing = buildContactTimeline(state, 'missing-contact');
    const gifts = buildContactTimeline(state, 'c-mira', 'Gifts');

    assert.deepEqual(missing.entries, []);
    assert.match(missing.emptyMessage, /no longer available/i);
    assert.deepEqual(gifts.entries, []);
    assert.match(gifts.emptyMessage, /gifts/i);
  });
});
