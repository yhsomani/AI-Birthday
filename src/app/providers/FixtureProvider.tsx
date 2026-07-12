import React, {
  PropsWithChildren,
  createContext,
  useCallback,
  useContext,
  useMemo,
  useReducer,
} from 'react';

import { fixturePeople } from '../../features/fixtures/data';

export type FixturePlatform = 'android' | 'ios';

type FixtureState = {
  setupStep: number;
  setupComplete: boolean;
  connected: boolean;
  selectedPersonIds: string[];
  repairedPersonIds: string[];
  planPaused: boolean;
  attentionReviewed: boolean;
  companionReminderEnabled: boolean;
  composerFixtureRecorded: boolean;
};

type FixtureAction =
  | { type: 'next-setup' }
  | { type: 'previous-setup' }
  | { type: 'connect' }
  | { type: 'toggle-person'; personId: string }
  | { type: 'repair-person'; personId: string }
  | { type: 'complete-setup' }
  | { type: 'toggle-plan' }
  | { type: 'review-attention' }
  | { type: 'toggle-reminder' }
  | { type: 'record-composer' }
  | { type: 'reset' };

const initialState = (
  setupComplete: boolean,
  selectedPersonIds?: string[],
): FixtureState => ({
  setupStep: 0,
  setupComplete,
  connected: setupComplete,
  selectedPersonIds:
    selectedPersonIds ?? (setupComplete ? [fixturePeople[0]!.id] : []),
  repairedPersonIds: [],
  planPaused: false,
  attentionReviewed: false,
  companionReminderEnabled: false,
  composerFixtureRecorded: false,
});

const reducer = (state: FixtureState, action: FixtureAction): FixtureState => {
  switch (action.type) {
    case 'next-setup':
      return { ...state, setupStep: Math.min(3, state.setupStep + 1) };
    case 'previous-setup':
      return { ...state, setupStep: Math.max(0, state.setupStep - 1) };
    case 'connect':
      return { ...state, connected: true, setupStep: 2 };
    case 'toggle-person':
      if (
        !fixturePeople.some(
          person =>
            person.id === action.personId &&
            (person.status === 'ready' ||
              state.repairedPersonIds.includes(person.id)),
        )
      ) {
        return state;
      }
      return {
        ...state,
        selectedPersonIds: state.selectedPersonIds.includes(action.personId)
          ? state.selectedPersonIds.filter(id => id !== action.personId)
          : [...state.selectedPersonIds, action.personId],
      };
    case 'repair-person':
      return state.repairedPersonIds.includes(action.personId)
        ? state
        : {
            ...state,
            repairedPersonIds: [...state.repairedPersonIds, action.personId],
          };
    case 'complete-setup':
      return state.connected && state.selectedPersonIds.length > 0
        ? { ...state, setupComplete: true }
        : state;
    case 'toggle-plan':
      return { ...state, planPaused: !state.planPaused };
    case 'review-attention':
      return { ...state, attentionReviewed: true };
    case 'toggle-reminder':
      return {
        ...state,
        companionReminderEnabled: !state.companionReminderEnabled,
      };
    case 'record-composer':
      return { ...state, composerFixtureRecorded: true };
    case 'reset':
      return initialState(false);
  }
};

type FixtureContextValue = FixtureState & {
  platform: FixturePlatform;
  nextSetup: () => void;
  previousSetup: () => void;
  connectFixture: () => void;
  togglePerson: (personId: string) => void;
  repairPerson: (personId: string) => void;
  completeSetup: () => void;
  togglePlan: () => void;
  reviewAttention: () => void;
  toggleReminder: () => void;
  recordComposer: () => void;
  resetFixture: () => void;
};

const FixtureContext = createContext<FixtureContextValue | undefined>(
  undefined,
);

type FixtureProviderProps = PropsWithChildren<{
  platform: FixturePlatform;
  initialSetupComplete?: boolean;
  initialSelectedPersonIds?: string[];
}>;

export function FixtureProvider({
  children,
  platform,
  initialSetupComplete = false,
  initialSelectedPersonIds,
}: FixtureProviderProps) {
  const [state, dispatch] = useReducer(
    reducer,
    initialState(initialSetupComplete, initialSelectedPersonIds),
  );
  const nextSetup = useCallback(() => dispatch({ type: 'next-setup' }), []);
  const previousSetup = useCallback(
    () => dispatch({ type: 'previous-setup' }),
    [],
  );
  const connectFixture = useCallback(() => dispatch({ type: 'connect' }), []);
  const togglePerson = useCallback(
    (personId: string) => dispatch({ type: 'toggle-person', personId }),
    [],
  );
  const repairPerson = useCallback(
    (personId: string) => dispatch({ type: 'repair-person', personId }),
    [],
  );
  const completeSetup = useCallback(
    () => dispatch({ type: 'complete-setup' }),
    [],
  );
  const togglePlan = useCallback(() => dispatch({ type: 'toggle-plan' }), []);
  const reviewAttention = useCallback(
    () => dispatch({ type: 'review-attention' }),
    [],
  );
  const toggleReminder = useCallback(
    () => dispatch({ type: 'toggle-reminder' }),
    [],
  );
  const recordComposer = useCallback(
    () => dispatch({ type: 'record-composer' }),
    [],
  );
  const resetFixture = useCallback(() => dispatch({ type: 'reset' }), []);

  const value = useMemo(
    () => ({
      ...state,
      platform,
      nextSetup,
      previousSetup,
      connectFixture,
      togglePerson,
      repairPerson,
      completeSetup,
      togglePlan,
      reviewAttention,
      toggleReminder,
      recordComposer,
      resetFixture,
    }),
    [
      completeSetup,
      connectFixture,
      nextSetup,
      platform,
      previousSetup,
      recordComposer,
      repairPerson,
      resetFixture,
      reviewAttention,
      state,
      togglePerson,
      togglePlan,
      toggleReminder,
    ],
  );

  return (
    <FixtureContext.Provider value={value}>{children}</FixtureContext.Provider>
  );
}

export const useFixture = () => {
  const value = useContext(FixtureContext);
  if (!value) {
    throw new Error('useFixture must be used inside FixtureProvider');
  }
  return value;
};
