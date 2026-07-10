import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { buildContactTimeline } from './contactTimeline';

describe('contact timeline contract', () => {
  it('combines events, memories, gifts, and sent messages for a contact', () => {
    const initial = createTestState();
    const state = {
      ...initial,
      messages: initial.messages.map(message =>
        message.id === 'msg-asha-bday'
          ? { ...message, status: 'Sent' as const, sentAt: '2026-07-09T10:00:00.000Z' }
          : message
      )
    };
    const timeline = buildContactTimeline(state, 'c-asha');
    const types = new Set(timeline.entries.map(entry => entry.type));

    assert.ok(types.has('Events'));
    assert.ok(types.has('Memories'));
    assert.ok(types.has('Gifts'));
    assert.ok(types.has('Messages'));
    assert.ok(
      timeline.entries.every((entry, index, entries) => index === 0 || entries[index - 1].dateIso >= entry.dateIso)
    );
  });

  it('filters sent message history without exposing unsent drafts', () => {
    const initial = createTestState();
    const state = {
      ...initial,
      messages: initial.messages.map(message =>
        message.id === 'msg-mira-checkin'
          ? { ...message, status: 'Sent' as const, sentAt: '2026-07-09T10:00:00.000Z' }
          : message
      )
    };
    const timeline = buildContactTimeline(state, 'c-mira', 'Messages');

    assert.equal(timeline.entries.length, 1);
    assert.equal(timeline.entries[0].id, 'msg-mira-checkin');
    assert.equal(timeline.entries[0].targetScreen, 'chatHistory');
  });

  it('returns a useful empty state for missing contacts and empty filters', () => {
    const state = createTestState();
    const missing = buildContactTimeline(state, 'missing-contact');
    const gifts = buildContactTimeline(state, 'c-mira', 'Gifts');

    assert.deepEqual(missing.entries, []);
    assert.match(missing.emptyMessage, /no longer available/i);
    assert.deepEqual(gifts.entries, []);
    assert.match(gifts.emptyMessage, /gifts/i);
  });
});
