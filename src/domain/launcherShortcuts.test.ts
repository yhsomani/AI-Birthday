import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import { launcherShortcuts, resolveLauncherShortcut, validateLauncherShortcutContract } from './launcherShortcuts';

describe('launcher shortcut contract', () => {
  it('keeps every retained launcher shortcut navigation-only and safe', () => {
    const state = createTestState();
    const validation = validateLauncherShortcutContract(state);

    assert.equal(validation.ok, true, validation.errors.join('\n'));
    assert.deepEqual(
      launcherShortcuts.map(shortcut => shortcut.id),
      ['review-messages', 'add-event']
    );
    assert.ok(launcherShortcuts.every(shortcut => shortcut.effect === 'navigate'));
  });

  it('routes shortcuts to review-first RN workflows', () => {
    const state = createTestState();
    const review = resolveLauncherShortcut(state, 'review-messages');
    const addEvent = resolveLauncherShortcut(state, 'add-event');

    assert.equal(review.ok, true);
    assert.equal(addEvent.ok, true);
    if (review.ok) {
      assert.equal(review.destination.screen, 'messages');
    }
    if (addEvent.ok) {
      assert.equal(addEvent.destination.screen, 'eventForm');
    }
  });

  it('recovers removed shortcut ids to the home dashboard', () => {
    const result = resolveLauncherShortcut(createTestState(), 'send-now');

    assert.equal(result.ok, false);
    assert.equal(result.destination.screen, 'home');
  });
});
