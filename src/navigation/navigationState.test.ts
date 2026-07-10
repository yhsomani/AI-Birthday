import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { Screen } from '../domain/types';
import {
  NAVIGATION_SCREENS,
  buildBrowserNavigationHistoryState,
  createNavigationState,
  currentNavigationRoute,
  reduceNavigation,
  readBrowserNavigationHistoryState,
  resolveNavigationDestination,
  restoreNavigationState,
  type NavigationEntities,
  type NavigationState
} from './navigationState';

const entities: NavigationEntities = {
  contactIds: ['contact-asha', 'contact-dev'],
  messages: [
    { id: 'message-asha', contactId: 'contact-asha' },
    { id: 'message-dev', contactId: 'contact-dev' }
  ]
};

const push = (
  state: NavigationState,
  destination: Parameters<typeof resolveNavigationDestination>[0],
  currentEntities = entities
) => reduceNavigation(state, { type: 'push', destination }, currentEntities).state;

describe('typed navigation history', () => {
  it('keeps the complete existing Screen vocabulary stable', () => {
    const expected: readonly Screen[] = [
      'onboarding',
      'home',
      'events',
      'eventForm',
      'messages',
      'contacts',
      'more',
      'contactDetail',
      'chatHistory',
      'wishPreview',
      'manualComposer'
    ];
    assert.deepEqual(NAVIGATION_SCREENS, expected);
  });

  it('returns Wish Preview to its real origin instead of a hardcoded screen', () => {
    const messages = createNavigationState({ screen: 'messages' }, entities);
    const fromMessages = push(messages, {
      screen: 'wishPreview',
      messageId: 'message-asha'
    });
    const messagesBack = reduceNavigation(
      fromMessages,
      { type: 'back', source: 'ui' },
      entities
    );
    assert.deepEqual(currentNavigationRoute(messagesBack.state), { screen: 'messages' });

    const home = createNavigationState({ screen: 'home' }, entities);
    const fromHome = push(home, {
      screen: 'wishPreview',
      messageId: 'message-asha'
    });
    const homeBack = reduceNavigation(
      fromHome,
      { type: 'back', source: 'android-hardware' },
      entities
    );
    assert.deepEqual(currentNavigationRoute(homeBack.state), { screen: 'home' });
    assert.equal(homeBack.outcome.back?.disposition, 'consumed');
  });

  it('preserves contact origins through composer, detail, and chat-history flows', () => {
    let state = createNavigationState({ screen: 'contacts' }, entities);
    state = push(state, { screen: 'contactDetail', contactId: 'contact-asha' });
    state = push(state, { screen: 'manualComposer', contactId: 'contact-asha' });

    let transition = reduceNavigation(state, { type: 'back', source: 'ui' }, entities);
    assert.deepEqual(currentNavigationRoute(transition.state), {
      screen: 'contactDetail',
      contactId: 'contact-asha'
    });

    state = push(transition.state, {
      screen: 'chatHistory',
      contactId: 'contact-asha'
    });
    transition = reduceNavigation(
      state,
      { type: 'back', source: 'browser-history' },
      entities
    );
    assert.deepEqual(currentNavigationRoute(transition.state), {
      screen: 'contactDetail',
      contactId: 'contact-asha'
    });

    transition = reduceNavigation(
      transition.state,
      { type: 'back', source: 'ui' },
      entities
    );
    assert.deepEqual(currentNavigationRoute(transition.state), { screen: 'contacts' });

    const events = createNavigationState({ screen: 'events' }, entities);
    const detailFromEvents = push(events, {
      screen: 'contactDetail',
      contactId: 'contact-dev'
    });
    const detailBack = reduceNavigation(
      detailFromEvents,
      { type: 'back', source: 'ui' },
      entities
    );
    assert.deepEqual(currentNavigationRoute(detailBack.state), { screen: 'events' });

    const home = createNavigationState({ screen: 'home' }, entities);
    const composerFromHome = push(home, {
      screen: 'manualComposer',
      contactId: 'contact-dev'
    });
    const composerBack = reduceNavigation(
      composerFromHome,
      { type: 'back', source: 'ui' },
      entities
    );
    assert.deepEqual(currentNavigationRoute(composerBack.state), { screen: 'home' });
  });

  it('uses safe canonical parents for directly restored leaf routes', () => {
    const preview = createNavigationState(
      { screen: 'wishPreview', messageId: 'message-asha' },
      entities
    );
    const previewBack = reduceNavigation(
      preview,
      { type: 'back', source: 'android-hardware' },
      entities
    );
    assert.deepEqual(currentNavigationRoute(previewBack.state), { screen: 'messages' });
    assert.equal(previewBack.outcome.back?.usedCanonicalParent, true);

    const chat = createNavigationState(
      { screen: 'chatHistory', contactId: 'contact-asha' },
      entities
    );
    const chatBack = reduceNavigation(chat, { type: 'back', source: 'ui' }, entities);
    assert.deepEqual(currentNavigationRoute(chatBack.state), {
      screen: 'contactDetail',
      contactId: 'contact-asha'
    });

    const composer = createNavigationState(
      { screen: 'manualComposer', contactId: 'contact-asha' },
      entities
    );
    const composerBack = reduceNavigation(
      composer,
      { type: 'back', source: 'ui' },
      entities
    );
    assert.deepEqual(currentNavigationRoute(composerBack.state), { screen: 'contacts' });

    const form = createNavigationState({ screen: 'eventForm' }, entities);
    const formBack = reduceNavigation(form, { type: 'back', source: 'ui' }, entities);
    assert.deepEqual(currentNavigationRoute(formBack.state), { screen: 'events' });
  });

  it('supports push and replace without duplicating the active route', () => {
    const home = createNavigationState({ screen: 'home' }, entities);
    const pushed = reduceNavigation(home, {
      type: 'push',
      destination: { screen: 'events' }
    }, entities);
    assert.equal(pushed.state.stack.length, 2);

    const duplicate = reduceNavigation(pushed.state, {
      type: 'push',
      destination: { screen: 'events' }
    }, entities);
    assert.equal(duplicate.outcome.changed, false);
    assert.equal(duplicate.state, pushed.state);

    const replaced = reduceNavigation(pushed.state, {
      type: 'replace',
      destination: { screen: 'more' }
    }, entities);
    assert.deepEqual(replaced.state.stack, [{ screen: 'home' }, { screen: 'more' }]);

    const events = createNavigationState({ screen: 'events' }, entities);
    const form = push(events, { screen: 'eventForm' });
    const completedForm = reduceNavigation(
      form,
      { type: 'replace', destination: { screen: 'events' } },
      entities
    );
    assert.deepEqual(completedForm.state.stack, [{ screen: 'events' }]);
  });

  it('returns new stack values without mutating frozen input state', () => {
    const stack = Object.freeze([{ screen: 'home' as const }]);
    const state = Object.freeze({ schemaVersion: 1 as const, stack });
    const transition = reduceNavigation(
      state,
      { type: 'push', destination: { screen: 'events' } },
      entities
    );

    assert.deepEqual(state.stack, [{ screen: 'home' }]);
    assert.deepEqual(transition.state.stack, [{ screen: 'home' }, { screen: 'events' }]);
    assert.notEqual(transition.state, state);
    assert.notEqual(transition.state.stack, state.stack);
  });

  it('recovers stale entity destinations and never trusts a mismatched message contact', () => {
    assert.deepEqual(
      resolveNavigationDestination(
        { screen: 'contactDetail', contactId: 'deleted-contact' },
        entities
      ),
      {
        route: { screen: 'contacts' },
        recovered: true,
        reason: 'stale-contact'
      }
    );
    assert.deepEqual(
      resolveNavigationDestination(
        { screen: 'wishPreview', messageId: 'deleted-message' },
        entities
      ).route,
      { screen: 'messages' }
    );

    const corrected = resolveNavigationDestination(
      {
        screen: 'wishPreview',
        messageId: 'message-asha',
        contactId: 'contact-dev'
      },
      entities
    );
    assert.deepEqual(corrected, {
      route: {
        screen: 'wishPreview',
        messageId: 'message-asha',
        contactId: 'contact-asha'
      },
      recovered: true,
      reason: 'message-contact-corrected'
    });

    const missingContact = resolveNavigationDestination(
      { screen: 'wishPreview', messageId: 'message-asha' },
      { contactIds: ['contact-dev'], messages: entities.messages }
    );
    assert.equal(missingContact.route.screen, 'messages');
    assert.equal(missingContact.reason, 'stale-message-contact');
  });

  it('reconciles stale entries throughout a saved stack and compacts safe fallbacks', () => {
    const state: NavigationState = {
      schemaVersion: 1,
      stack: [
        { screen: 'contacts' },
        { screen: 'contactDetail', contactId: 'contact-asha' },
        { screen: 'chatHistory', contactId: 'contact-asha' }
      ]
    };
    const transition = reduceNavigation(
      state,
      { type: 'reconcile' },
      { contactIds: [], messages: [] }
    );

    assert.deepEqual(transition.state.stack, [{ screen: 'contacts' }]);
    assert.equal(transition.outcome.recoveredCount, 2);
  });

  it('models Android and browser root-back intents without mutating navigation', () => {
    const state = createNavigationState({ screen: 'home' }, entities);
    const android = reduceNavigation(
      state,
      { type: 'back', source: 'android-hardware' },
      entities
    );
    const browser = reduceNavigation(
      state,
      { type: 'back', source: 'browser-history' },
      entities
    );
    const ui = reduceNavigation(state, { type: 'back', source: 'ui' }, entities);

    assert.equal(android.state, state);
    assert.equal(android.outcome.back?.disposition, 'exit-app');
    assert.equal(browser.outcome.back?.disposition, 'delegate-to-browser');
    assert.equal(ui.outcome.back?.disposition, 'unhandled');
  });

  it('round-trips serializable state and safely restores malformed or stale routes', () => {
    let state = createNavigationState({ screen: 'messages' }, entities);
    state = push(state, { screen: 'wishPreview', messageId: 'message-asha' });
    const decoded: unknown = JSON.parse(JSON.stringify(state));
    assert.deepEqual(restoreNavigationState(decoded, entities), state);

    assert.deepEqual(
      restoreNavigationState({ schemaVersion: 99, stack: [] }, entities),
      { schemaVersion: 1, stack: [{ screen: 'home' }] }
    );
    assert.deepEqual(
      restoreNavigationState(
        {
          schemaVersion: 1,
          stack: [
            { screen: 'not-a-route' },
            { screen: 'contactDetail', contactId: 'deleted-contact' }
          ]
        },
        entities
      ),
      { schemaVersion: 1, stack: [{ screen: 'contacts' }] }
    );
  });

  it('round-trips browser back/forward snapshots while preserving unrelated history state', () => {
    let navigation = createNavigationState({ screen: 'messages' }, entities);
    navigation = push(navigation, {
      screen: 'wishPreview',
      messageId: 'message-asha'
    });
    const browserState = buildBrowserNavigationHistoryState(
      { routerKey: 'keep-me' },
      navigation,
      2
    );
    const decoded: unknown = JSON.parse(JSON.stringify(browserState));
    const restored = readBrowserNavigationHistoryState(decoded, entities);

    assert.equal(browserState.routerKey, 'keep-me');
    assert.equal(restored?.depth, 2);
    assert.deepEqual(restored?.navigation, navigation);
    assert.equal(readBrowserNavigationHistoryState({ unrelated: true }, entities), undefined);
  });
});
