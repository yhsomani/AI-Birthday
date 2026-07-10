import type { Screen } from '../domain/types';

export const NAVIGATION_SCREENS = [
  'onboarding',
  'home',
  'events',
  'eventForm',
  'messages',
  'contacts',
  'more',
  'analytics',
  'settings',
  'backup',
  'styleCoach',
  'activityHistory',
  'setupCheck',
  'contactDetail',
  'chatHistory',
  'wishPreview',
  'manualComposer'
] as const satisfies readonly Screen[];

type AssertNoMissingScreen<T extends never> = T;
type _AllScreenRoutesCovered = AssertNoMissingScreen<Exclude<Screen, (typeof NAVIGATION_SCREENS)[number]>>;

type StaticScreen =
  | 'onboarding'
  | 'home'
  | 'eventForm'
  | 'messages'
  | 'contacts'
  | 'more'
  | 'analytics'
  | 'settings'
  | 'backup'
  | 'styleCoach'
  | 'activityHistory'
  | 'setupCheck';

type ContactBoundScreen = 'contactDetail' | 'chatHistory' | 'manualComposer';

export type NavigationRoute =
  | { screen: StaticScreen }
  | { screen: 'events'; eventId?: string; contactId?: string }
  | { screen: ContactBoundScreen; contactId: string }
  | {
      screen: 'wishPreview';
      messageId: string;
      contactId: string;
    };

/** Compatible with the existing navigate action and DeepLinkDestination shape. */
export interface NavigationDestination {
  screen: Screen;
  contactId?: string;
  eventId?: string;
  messageId?: string;
}

export interface NavigationEntities {
  contactIds: readonly string[];
  messages: readonly {
    id: string;
    contactId: string;
  }[];
  events: readonly {
    id: string;
    contactId: string;
  }[];
}

export type NavigationRecoveryReason =
  | 'missing-contact-reference'
  | 'stale-contact'
  | 'missing-message-reference'
  | 'stale-message'
  | 'stale-message-contact'
  | 'message-contact-corrected'
  | 'stale-event'
  | 'stale-event-contact'
  | 'event-contact-mismatch';

export interface NavigationRouteResolution {
  route: NavigationRoute;
  recovered: boolean;
  reason?: NavigationRecoveryReason;
}

export interface NavigationState {
  schemaVersion: 1;
  stack: readonly NavigationRoute[];
}

export type NavigationBackSource = 'ui' | 'android-hardware' | 'browser-history';
export type NavigationBackDisposition = 'consumed' | 'exit-app' | 'delegate-to-browser' | 'unhandled';

export type NavigationAction =
  | { type: 'push'; destination: NavigationDestination }
  | { type: 'replace'; destination: NavigationDestination }
  | { type: 'back'; source: NavigationBackSource }
  | { type: 'reconcile' };

export interface NavigationTransitionOutcome {
  action: NavigationAction['type'];
  changed: boolean;
  recovery?: NavigationRecoveryReason;
  recoveredCount?: number;
  back?: {
    source: NavigationBackSource;
    disposition: NavigationBackDisposition;
    usedCanonicalParent: boolean;
  };
}

export interface NavigationTransition {
  state: NavigationState;
  outcome: NavigationTransitionOutcome;
}

const hasContact = (entities: NavigationEntities, contactId: string) => entities.contactIds.includes(contactId);

const recovered = (
  screen: 'contacts' | 'events' | 'messages' | 'home',
  reason: NavigationRecoveryReason
): NavigationRouteResolution => ({
  route: { screen },
  recovered: true,
  reason
});

/**
 * Resolves a legacy/deep-link-shaped destination into a valid typed route.
 * Entity references are checked at the transition boundary and stale targets
 * recover to their owning list rather than rendering an empty detail screen.
 */
export const resolveNavigationDestination = (
  destination: NavigationDestination,
  entities: NavigationEntities
): NavigationRouteResolution => {
  switch (destination.screen) {
    case 'events': {
      if (!destination.eventId) {
        return { route: { screen: 'events' }, recovered: false };
      }
      const event = entities.events.find(item => item.id === destination.eventId);
      if (!event) return recovered('events', 'stale-event');
      if (!hasContact(entities, event.contactId)) return recovered('events', 'stale-event-contact');
      if (destination.contactId && destination.contactId !== event.contactId) {
        return recovered('events', 'event-contact-mismatch');
      }
      return {
        route: { screen: 'events', eventId: event.id, contactId: event.contactId },
        recovered: false
      };
    }

    case 'contactDetail':
    case 'chatHistory':
    case 'manualComposer': {
      if (!destination.contactId) {
        return recovered('contacts', 'missing-contact-reference');
      }
      if (!hasContact(entities, destination.contactId)) {
        return recovered('contacts', 'stale-contact');
      }
      return {
        route: {
          screen: destination.screen,
          contactId: destination.contactId
        },
        recovered: false
      };
    }

    case 'wishPreview': {
      if (!destination.messageId) {
        return recovered('messages', 'missing-message-reference');
      }
      const message = entities.messages.find(item => item.id === destination.messageId);
      if (!message) {
        return recovered('messages', 'stale-message');
      }
      if (!hasContact(entities, message.contactId)) {
        return recovered('messages', 'stale-message-contact');
      }
      return {
        route: {
          screen: 'wishPreview',
          messageId: message.id,
          contactId: message.contactId
        },
        recovered: Boolean(destination.contactId && destination.contactId !== message.contactId),
        reason:
          destination.contactId && destination.contactId !== message.contactId ? 'message-contact-corrected' : undefined
      };
    }

    case 'onboarding':
    case 'home':
    case 'eventForm':
    case 'messages':
    case 'contacts':
    case 'more':
    case 'analytics':
    case 'settings':
    case 'backup':
    case 'styleCoach':
    case 'activityHistory':
    case 'setupCheck':
      return {
        route: { screen: destination.screen },
        recovered: false
      };
  }
};

export const navigationRouteEquals = (left: NavigationRoute, right: NavigationRoute) =>
  left.screen === right.screen &&
  ('contactId' in left ? left.contactId : undefined) === ('contactId' in right ? right.contactId : undefined) &&
  ('messageId' in left ? left.messageId : undefined) === ('messageId' in right ? right.messageId : undefined) &&
  ('eventId' in left ? left.eventId : undefined) === ('eventId' in right ? right.eventId : undefined);

const compactAdjacentRoutes = (routes: readonly NavigationRoute[]) => {
  const compacted: NavigationRoute[] = [];
  for (const route of routes) {
    const previous = compacted.at(-1);
    if (!previous || !navigationRouteEquals(previous, route)) {
      compacted.push(route);
    }
  }
  return compacted;
};

const canonicalParentFor = (route: NavigationRoute): NavigationDestination | undefined => {
  switch (route.screen) {
    case 'events':
      return route.eventId ? { screen: 'events' } : undefined;
    case 'wishPreview':
      return { screen: 'messages' };
    case 'manualComposer':
    case 'contactDetail':
      return { screen: 'contacts' };
    case 'chatHistory':
      return { screen: 'contactDetail', contactId: route.contactId };
    case 'eventForm':
      return { screen: 'events' };
    case 'analytics':
    case 'settings':
    case 'backup':
    case 'styleCoach':
    case 'activityHistory':
    case 'setupCheck':
      return { screen: 'more' };
    case 'onboarding':
    case 'home':
    case 'messages':
    case 'contacts':
    case 'more':
      return undefined;
  }
};

const rootBackDisposition = (source: NavigationBackSource): NavigationBackDisposition => {
  switch (source) {
    case 'android-hardware':
      return 'exit-app';
    case 'browser-history':
      return 'delegate-to-browser';
    case 'ui':
      return 'unhandled';
  }
};

export const createNavigationState = (
  destination: NavigationDestination,
  entities: NavigationEntities
): NavigationState => ({
  schemaVersion: 1,
  stack: [resolveNavigationDestination(destination, entities).route]
});

export const currentNavigationRoute = (state: NavigationState): NavigationRoute =>
  state.stack.at(-1) ?? { screen: 'home' };

/** Pure transition reducer; the result explicitly tells platform adapters how to handle back. */
export const reduceNavigation = (
  state: NavigationState,
  action: NavigationAction,
  entities: NavigationEntities
): NavigationTransition => {
  switch (action.type) {
    case 'push': {
      const resolution = resolveNavigationDestination(action.destination, entities);
      const current = currentNavigationRoute(state);
      if (navigationRouteEquals(current, resolution.route)) {
        return {
          state,
          outcome: {
            action: 'push',
            changed: false,
            recovery: resolution.reason
          }
        };
      }
      return {
        state: {
          schemaVersion: 1,
          stack: [...state.stack, resolution.route]
        },
        outcome: {
          action: 'push',
          changed: true,
          recovery: resolution.reason
        }
      };
    }

    case 'replace': {
      const resolution = resolveNavigationDestination(action.destination, entities);
      const current = currentNavigationRoute(state);
      if (navigationRouteEquals(current, resolution.route)) {
        return {
          state,
          outcome: {
            action: 'replace',
            changed: false,
            recovery: resolution.reason
          }
        };
      }
      return {
        state: {
          schemaVersion: 1,
          stack: compactAdjacentRoutes([...state.stack.slice(0, -1), resolution.route])
        },
        outcome: {
          action: 'replace',
          changed: true,
          recovery: resolution.reason
        }
      };
    }

    case 'back': {
      if (state.stack.length > 1) {
        const popped = state.stack.slice(0, -1);
        const exposed = popped.at(-1) ?? { screen: 'home' as const };
        const resolution = resolveNavigationDestination(exposed, entities);
        const stack = compactAdjacentRoutes([...popped.slice(0, -1), resolution.route]);
        return {
          state: { schemaVersion: 1, stack },
          outcome: {
            action: 'back',
            changed: true,
            recovery: resolution.reason,
            back: {
              source: action.source,
              disposition: 'consumed',
              usedCanonicalParent: false
            }
          }
        };
      }

      const current = currentNavigationRoute(state);
      const parent = canonicalParentFor(current);
      if (parent) {
        const resolution = resolveNavigationDestination(parent, entities);
        return {
          state: { schemaVersion: 1, stack: [resolution.route] },
          outcome: {
            action: 'back',
            changed: true,
            recovery: resolution.reason,
            back: {
              source: action.source,
              disposition: 'consumed',
              usedCanonicalParent: true
            }
          }
        };
      }

      return {
        state,
        outcome: {
          action: 'back',
          changed: false,
          back: {
            source: action.source,
            disposition: rootBackDisposition(action.source),
            usedCanonicalParent: false
          }
        }
      };
    }

    case 'reconcile': {
      const resolutions = state.stack.map(route => resolveNavigationDestination(route, entities));
      const stack = compactAdjacentRoutes(resolutions.map(item => item.route));
      const nextStack = stack.length ? stack : [{ screen: 'home' as const }];
      const recoveredResolutions = resolutions.filter(item => item.recovered);
      const changed =
        nextStack.length !== state.stack.length ||
        nextStack.some((route, index) =>
          state.stack[index] ? !navigationRouteEquals(route, state.stack[index]) : true
        );
      return {
        state: changed ? { schemaVersion: 1, stack: nextStack } : state,
        outcome: {
          action: 'reconcile',
          changed,
          recovery: recoveredResolutions[0]?.reason,
          recoveredCount: recoveredResolutions.length
        }
      };
    }
  }
};

const navigationScreenSet = new Set<string>(NAVIGATION_SCREENS);

const destinationFromUnknown = (value: unknown): NavigationDestination | undefined => {
  if (!value || typeof value !== 'object') return undefined;
  const candidate = value as Record<string, unknown>;
  if (typeof candidate.screen !== 'string' || !navigationScreenSet.has(candidate.screen)) {
    return undefined;
  }
  return {
    screen: candidate.screen as Screen,
    contactId: typeof candidate.contactId === 'string' ? candidate.contactId : undefined,
    eventId: typeof candidate.eventId === 'string' ? candidate.eventId : undefined,
    messageId: typeof candidate.messageId === 'string' ? candidate.messageId : undefined
  };
};

/** Validates and reconciles JSON-decoded navigation state without trusting stored references. */
export const restoreNavigationState = (value: unknown, entities: NavigationEntities): NavigationState => {
  if (!value || typeof value !== 'object') {
    return createNavigationState({ screen: 'home' }, entities);
  }
  const candidate = value as { schemaVersion?: unknown; stack?: unknown };
  if (candidate.schemaVersion !== 1 || !Array.isArray(candidate.stack)) {
    return createNavigationState({ screen: 'home' }, entities);
  }

  const restored = candidate.stack
    .map(destinationFromUnknown)
    .filter((destination): destination is NavigationDestination => Boolean(destination))
    .map(destination => resolveNavigationDestination(destination, entities).route);
  const stack = compactAdjacentRoutes(restored);
  return {
    schemaVersion: 1,
    stack: stack.length ? stack : [{ screen: 'home' }]
  };
};

export interface BrowserNavigationSnapshot {
  schemaVersion: 1;
  depth: number;
  navigation: NavigationState;
}

const BROWSER_NAVIGATION_STATE_KEY = 'relateAINavigation';

/** Preserves unrelated browser history state while attaching serializable app navigation. */
export const buildBrowserNavigationHistoryState = (
  existingState: unknown,
  navigation: NavigationState,
  depth: number
): Record<string, unknown> => {
  const existing =
    existingState && typeof existingState === 'object' && !Array.isArray(existingState)
      ? (existingState as Record<string, unknown>)
      : {};
  return {
    ...existing,
    [BROWSER_NAVIGATION_STATE_KEY]: {
      schemaVersion: 1,
      depth: Math.max(0, Math.trunc(depth)),
      navigation
    } satisfies BrowserNavigationSnapshot
  };
};

/** Reads browser back/forward state through the same stale-safe route restoration boundary. */
export const readBrowserNavigationHistoryState = (
  value: unknown,
  entities: NavigationEntities
): BrowserNavigationSnapshot | undefined => {
  if (!value || typeof value !== 'object') return undefined;
  const marker = (value as Record<string, unknown>)[BROWSER_NAVIGATION_STATE_KEY];
  if (!marker || typeof marker !== 'object') return undefined;
  const candidate = marker as Record<string, unknown>;
  if (
    candidate.schemaVersion !== 1 ||
    typeof candidate.depth !== 'number' ||
    !Number.isFinite(candidate.depth) ||
    candidate.depth < 0
  ) {
    return undefined;
  }
  return {
    schemaVersion: 1,
    depth: Math.trunc(candidate.depth),
    navigation: restoreNavigationState(candidate.navigation, entities)
  };
};
