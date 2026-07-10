import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { relateReducer } from '../state/relateReducer';
import { applyEventMerge, previewEventMerge } from './eventConflictLifecycle';

describe('event conflict merge lifecycle', () => {
  it('requires same-contact same-date events and a reviewed token', () => {
    const state = createTestState();
    const original = {
      ...state.events[0],
      sourceIdentities: [{ provider: 'Calendar' as const, sourceId: 'calendar-survivor' }]
    };
    state.events[0] = original;
    state.events = [
      {
        ...original,
        id: 'duplicate-event',
        label: 'Imported duplicate',
        verified: false,
        sourceIdentities: [{ provider: 'Device contacts', sourceId: 'device-merged' }]
      },
      ...state.events
    ];
    const preview = previewEventMerge(state, original.id, 'duplicate-event');
    assert.equal(preview.ok, true);
    if (!preview.ok) return;
    assert.ok(preview.matchReasons.includes('same-date'));
    const merged = applyEventMerge(state, original.id, 'duplicate-event');
    assert.equal(
      merged.events.some(event => event.id === 'duplicate-event'),
      false
    );
    assert.equal(merged.events.find(event => event.id === original.id)?.verified, true);
    assert.deepEqual(merged.events.find(event => event.id === original.id)?.sourceIdentities, [
      { provider: 'Calendar', sourceId: 'calendar-survivor' },
      { provider: 'Device contacts', sourceId: 'device-merged' }
    ]);

    const stale = relateReducer(state, {
      type: 'mergeEvents',
      survivorEventId: original.id,
      mergedEventId: 'duplicate-event',
      confirmationToken: 'stale-token'
    });
    const accepted = relateReducer(state, {
      type: 'mergeEvents',
      survivorEventId: original.id,
      mergedEventId: 'duplicate-event',
      confirmationToken: preview.confirmationToken
    });
    assert.equal(
      stale.events.some(event => event.id === 'duplicate-event'),
      true
    );
    assert.equal(
      accepted.events.some(event => event.id === 'duplicate-event'),
      false
    );
  });

  it('blocks unrelated dates and different contacts', () => {
    const state = createTestState();
    assert.equal(previewEventMerge(state, state.events[0].id, state.events[1].id).ok, false);
    const original = state.events[0];
    state.events.push({ ...original, id: 'different-date', date: '2026-12-01T12:00:00.000Z' });
    assert.equal(previewEventMerge(state, original.id, 'different-date').ok, false);
  });
});
