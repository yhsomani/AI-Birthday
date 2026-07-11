import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createTestState } from '../test/testState';
import type { NavigationDestination } from '../navigation/navigationState';
import { NavigationRuntimeController } from './navigationRuntime';

const fixture = () => {
  const state = createTestState();
  state.activeScreen = 'home';
  state.selectedContactId = undefined;
  state.selectedEventId = undefined;
  state.selectedMessageId = undefined;
  const committed: NavigationDestination[] = [];
  const controller = new NavigationRuntimeController({
    getState: () => state,
    dispatchRoute: async destination => {
      committed.push(destination);
      state.activeScreen = destination.screen;
      state.selectedContactId = destination.contactId;
      state.selectedEventId = destination.eventId;
      state.selectedMessageId = destination.messageId;
    }
  });
  return { state, committed, controller };
};

describe('UI-independent navigation runtime', () => {
  it('preserves the real origin for leaf routes and commits back synchronously', async () => {
    const test = fixture();
    await test.controller.navigate({
      screen: 'contactDetail',
      contactId: test.state.contacts[0].id
    });
    const back = test.controller.back('android-hardware');
    assert.equal(back.outcome.back?.disposition, 'consumed');
    await test.controller.commit(back);
    assert.equal(test.state.activeScreen, 'home');
  });

  it('pushes secondary destinations while primary navigation still replaces the active route', async () => {
    const test = fixture();
    await test.controller.navigate({ screen: 'more' });
    await test.controller.navigate({ screen: 'settings' });
    assert.deepEqual(test.controller.snapshot().stack, [{ screen: 'more' }, { screen: 'settings' }]);

    const back = test.controller.back('ui');
    await test.controller.commit(back);
    assert.equal(test.state.activeScreen, 'more');

    await test.controller.navigate({ screen: 'events' });
    assert.deepEqual(test.controller.snapshot().stack, [{ screen: 'events' }]);
  });

  it('restores persisted navigation state through stale-safe entity validation', async () => {
    const test = fixture();
    await test.controller.restore({
      schemaVersion: 1,
      stack: [{ screen: 'contacts' }, { screen: 'contactDetail', contactId: 'deleted-contact' }]
    });
    assert.equal(test.state.activeScreen, 'contacts');
    assert.deepEqual(test.controller.snapshot().stack, [{ screen: 'contacts' }]);
  });

  it('reconciles stale entity references after data changes', async () => {
    const test = fixture();
    const contactId = test.state.contacts[0].id;
    await test.controller.navigate({ screen: 'contactDetail', contactId });
    test.state.contacts = test.state.contacts.filter(contact => contact.id !== contactId);
    await test.controller.synchronize();
    assert.equal(test.state.activeScreen, 'contacts');
  });

  it('commits an exact event notification target and recovers it after deletion', async () => {
    const test = fixture();
    const event = test.state.events[0];
    await test.controller.navigate({
      screen: 'events',
      eventId: event.id,
      contactId: event.contactId
    });
    assert.equal(test.state.selectedEventId, event.id);
    test.state.events = test.state.events.filter(item => item.id !== event.id);
    await test.controller.synchronize();
    assert.equal(test.state.activeScreen, 'events');
    assert.equal(test.state.selectedEventId, undefined);
  });
});
