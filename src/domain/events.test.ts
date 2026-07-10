import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { relateReducer } from '../state/relateReducer';
import { createTestState } from '../test/testState';
import { advancedManualEventTypes, manualEventTypes, primaryManualEventTypes, validateManualEventInput } from './events';

describe('manual event contract', () => {
  it('keeps birthday, anniversary, and custom as primary manual event choices', () => {
    assert.deepEqual(primaryManualEventTypes, ['Birthday', 'Anniversary', 'Custom']);
    assert.deepEqual(advancedManualEventTypes, [
      'Work anniversary',
      'Graduation',
      'Holiday',
      'Revival',
      'Follow-up'
    ]);
    assert.deepEqual(manualEventTypes, [...primaryManualEventTypes, ...advancedManualEventTypes]);
  });

  it('validates real calendar dates before saving', () => {
    const state = createTestState();
    const result = validateManualEventInput(
      {
        contactId: 'c-asha',
        eventType: 'Birthday',
        label: 'Invalid leap birthday',
        date: '2025-02-29'
      },
      state.contacts,
      state.events
    );

    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.match(result.errors.join(' '), /valid date/i);
    }
  });

  it('catches duplicate same-contact same-day events before save', () => {
    const state = createTestState();
    const existingDate = state.events.find(event => event.id === 'e-asha-bday')?.date.slice(0, 10) ?? '';
    const blocked = relateReducer(state, {
      type: 'addManualEvent',
      contactId: 'c-asha',
      eventType: 'Birthday',
      label: 'Asha birthday again',
      date: existingDate
    });
    const confirmed = relateReducer(state, {
      type: 'addManualEvent',
      contactId: 'c-asha',
      eventType: 'Birthday',
      label: 'Asha birthday again',
      date: existingDate,
      confirmConflict: true
    });

    assert.equal(blocked.events.length, state.events.length);
    assert.match(blocked.activity[0].title, /review event conflict/i);
    assert.equal(confirmed.events.length, state.events.length + 1);
    assert.equal(confirmed.activeScreen, 'events');
  });

  it('creates a new local contact when an event is added for a new person', () => {
    const state = createTestState();
    const next = relateReducer(state, {
      type: 'addManualEvent',
      newContactName: 'Nikhil Rao',
      eventType: 'Graduation',
      label: 'Nikhil graduation',
      date: '2026-08-15'
    });

    const contact = next.contacts.find(item => item.name === 'Nikhil Rao');
    const event = next.events.find(item => item.label === 'Nikhil graduation');

    assert.ok(contact);
    assert.ok(event);
    assert.equal(event?.contactId, contact?.id);
    assert.equal(event?.source, 'Manual');
    assert.equal(event?.verified, true);
  });
});
