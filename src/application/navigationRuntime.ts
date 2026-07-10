import type { AppState } from '../domain/types';
import {
  createNavigationState,
  currentNavigationRoute,
  navigationRouteEquals,
  reduceNavigation,
  resolveNavigationDestination,
  restoreNavigationState,
  type NavigationAction,
  type NavigationDestination,
  type NavigationEntities,
  type NavigationState,
  type NavigationTransition
} from '../navigation/navigationState';

export type NavigationRuntimeDependencies = {
  getState(): AppState;
  dispatchRoute(destination: NavigationDestination): Promise<void>;
};

const preservesOrigin = (destination: NavigationDestination) =>
  destination.screen === 'contactDetail' ||
  destination.screen === 'chatHistory' ||
  destination.screen === 'wishPreview' ||
  destination.screen === 'manualComposer' ||
  destination.screen === 'eventForm' ||
  destination.screen === 'analytics' ||
  destination.screen === 'settings' ||
  destination.screen === 'backup' ||
  destination.screen === 'styleCoach' ||
  destination.screen === 'activityHistory' ||
  destination.screen === 'setupCheck';

const entitiesFrom = (state: AppState): NavigationEntities => ({
  contactIds: state.contacts.filter(contact => !contact.archivedAt).map(contact => contact.id),
  messages: state.messages.map(message => ({ id: message.id, contactId: message.contactId })),
  events: state.events.map(event => ({ id: event.id, contactId: event.contactId }))
});

const destinationFrom = (state: AppState): NavigationDestination => ({
  screen: state.activeScreen,
  contactId: state.selectedContactId,
  eventId: state.selectedEventId,
  messageId: state.selectedMessageId
});

const destinationForRoute = (route: ReturnType<typeof currentNavigationRoute>): NavigationDestination => ({
  screen: route.screen,
  ...('contactId' in route ? { contactId: route.contactId } : {}),
  ...('eventId' in route ? { eventId: route.eventId } : {}),
  ...('messageId' in route ? { messageId: route.messageId } : {})
});

/**
 * UI-independent typed navigation history. Platform adapters may use the
 * synchronous transition outcome immediately, while route persistence settles
 * through `commit` before commands report success.
 */
export class NavigationRuntimeController {
  private navigation?: NavigationState;

  constructor(private readonly dependencies: NavigationRuntimeDependencies) {}

  private entities() {
    return entitiesFrom(this.dependencies.getState());
  }

  private ensureNavigation() {
    if (!this.navigation) {
      const state = this.dependencies.getState();
      this.navigation = createNavigationState(destinationFrom(state), entitiesFrom(state));
    }
    return this.navigation;
  }

  snapshot(): NavigationState {
    const state = this.ensureNavigation();
    return { schemaVersion: 1, stack: state.stack.map(route => ({ ...route })) };
  }

  transition(action: NavigationAction): NavigationTransition {
    const transition = reduceNavigation(this.ensureNavigation(), action, this.entities());
    this.navigation = transition.state;
    return transition;
  }

  async commit(transition: NavigationTransition): Promise<void> {
    if (!transition.outcome.changed) return;
    await this.dependencies.dispatchRoute(destinationForRoute(currentNavigationRoute(transition.state)));
  }

  async navigate(destination: NavigationDestination): Promise<NavigationTransition> {
    const transition = this.transition({
      type: preservesOrigin(destination) ? 'push' : 'replace',
      destination
    });
    await this.commit(transition);
    return transition;
  }

  back(source: Extract<NavigationAction, { type: 'back' }>['source']): NavigationTransition {
    return this.transition({ type: 'back', source });
  }

  async synchronize(): Promise<NavigationTransition> {
    const state = this.dependencies.getState();
    const entities = entitiesFrom(state);
    const reconciled = reduceNavigation(this.ensureNavigation(), { type: 'reconcile' }, entities);
    let navigation = reconciled.state;
    let changed = reconciled.outcome.changed;
    let recovery = reconciled.outcome.recovery;
    const desired = resolveNavigationDestination(destinationFrom(state), entities).route;
    const current = currentNavigationRoute(navigation);

    if (!navigationRouteEquals(current, desired)) {
      const aligned = reduceNavigation(
        navigation,
        {
          type: preservesOrigin(desired) ? 'push' : 'replace',
          destination: destinationForRoute(desired)
        },
        entities
      );
      navigation = aligned.state;
      changed ||= aligned.outcome.changed;
      recovery ??= aligned.outcome.recovery;
    }

    this.navigation = navigation;
    const transition: NavigationTransition = {
      state: navigation,
      outcome: {
        action: 'reconcile',
        changed,
        recovery,
        recoveredCount: reconciled.outcome.recoveredCount
      }
    };
    const route = currentNavigationRoute(navigation);
    const currentDestination = destinationFrom(state);
    const appMatchesRoute =
      currentDestination.screen === route.screen &&
      currentDestination.contactId === ('contactId' in route ? route.contactId : undefined) &&
      currentDestination.eventId === ('eventId' in route ? route.eventId : undefined) &&
      currentDestination.messageId === ('messageId' in route ? route.messageId : undefined);
    if (!appMatchesRoute) await this.commit({ ...transition, outcome: { ...transition.outcome, changed: true } });
    return transition;
  }

  async restore(value: unknown): Promise<NavigationState> {
    this.navigation = restoreNavigationState(value, this.entities());
    await this.dependencies.dispatchRoute(destinationForRoute(currentNavigationRoute(this.navigation)));
    return this.snapshot();
  }
}
