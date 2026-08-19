import React from 'react';
import { StyleSheet } from 'react-native';
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react-native';

import { appI18n } from '../localization/i18n';
import type { SafeSupportCode } from '../domain/shared/brand';
import type { LiveAppPort } from '../features/live/LiveAppPort';


jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

jest.mock('react-native-safe-area-context', () => {
  const TestReact = require('react');
  const { View } = require('react-native');
  const insets = { top: 0, right: 0, bottom: 0, left: 0 };
  const frame = { x: 0, y: 0, width: 390, height: 844 };
  const SafeAreaInsetsContext = TestReact.createContext(insets);
  const SafeAreaFrameContext = TestReact.createContext(frame);
  return {
    SafeAreaFrameContext,
    SafeAreaInsetsContext,
    SafeAreaProvider: (props: { children?: unknown }) =>
      TestReact.createElement(
        SafeAreaFrameContext.Provider,
        { value: frame },
        TestReact.createElement(
          SafeAreaInsetsContext.Provider,
          { value: insets },
          props.children,
        ),
      ),
    SafeAreaView: (props: { children?: unknown; [key: string]: unknown }) => {
      const { children, ...viewProps } = props;
      return TestReact.createElement(View, viewProps, children);
    },
    initialWindowMetrics: { frame, insets },
    useSafeAreaFrame: () => frame,
    useSafeAreaInsets: () => insets,
  };
});

jest.doMock('./navigation/RootNavigator', () => {
  const TestReact = require('react');
  const { Pressable, Text, View } = require('react-native');
  const { useFixture } =
    require('./providers/FixtureProvider') as typeof import('./providers/FixtureProvider');
  const { SetupJourneyScreen } =
    require('../features/setup/SetupJourneyScreen') as typeof import('../features/setup/SetupJourneyScreen');
  const { HomeScreen } =
    require('../features/home/HomeScreen') as typeof import('../features/home/HomeScreen');
  const { ApprovedMessageScreen } =
    require('../features/home/ApprovedMessageScreen') as typeof import('../features/home/ApprovedMessageScreen');
  const { PeopleScreen } =
    require('../features/people/PeopleScreen') as typeof import('../features/people/PeopleScreen');
  const { PersonDetailScreen } =
    require('../features/people/PersonDetailScreen') as typeof import('../features/people/PersonDetailScreen');
  const { ActivityScreen } =
    require('../features/activity/ActivityScreen') as typeof import('../features/activity/ActivityScreen');
  const { ActivityDetailScreen } =
    require('../features/activity/ActivityDetailScreen') as typeof import('../features/activity/ActivityDetailScreen');
  const { AttentionScreen } =
    require('../features/activity/AttentionScreen') as typeof import('../features/activity/AttentionScreen');
  const { SettingsScreen } =
    require('../features/settings/SettingsScreen') as typeof import('../features/settings/SettingsScreen');
  const { DataBoundaryScreen } =
    require('../features/settings/DataBoundaryScreen') as typeof import('../features/settings/DataBoundaryScreen');

  type TestRoute = {
    name:
      | 'Home'
      | 'People'
      | 'Settings'
      | 'ApprovedMessage'
      | 'PersonDetail'
      | 'Activity'
      | 'ActivityDetail'
      | 'Attention'
      | 'DataBoundary';
    params?: Record<string, string>;
  };

  const tabNames = ['Home', 'People', 'Settings'] as const;

  function RootNavigator() {
    const { setupComplete } = useFixture();
    const [route, setRoute] = TestReact.useState({
      name: 'Home',
    } as TestRoute);

    if (!setupComplete) {
      return TestReact.createElement(SetupJourneyScreen);
    }

    const rootNavigation = {
      navigate: (name: TestRoute['name'], params?: Record<string, string>) =>
        setRoute({ name, params }),
    };
    const tabNavigation = {
      navigate: (name: TestRoute['name']) => setRoute({ name }),
      getParent: () => rootNavigation,
    };
    const goHome = () => setRoute({ name: 'Home' });

    let activeScreen;
    switch (route.name) {
      case 'People':
        activeScreen = TestReact.createElement(PeopleScreen, {
          navigation: tabNavigation,
        });
        break;
      case 'Settings':
        activeScreen = TestReact.createElement(SettingsScreen, {
          navigation: tabNavigation,
        });
        break;
      case 'ApprovedMessage':
        activeScreen = TestReact.createElement(ApprovedMessageScreen, {
          navigation: { goBack: goHome },
        });
        break;
      case 'PersonDetail':
        activeScreen = TestReact.createElement(PersonDetailScreen, {
          navigation: rootNavigation,
          route: {
            params: { personId: route.params?.personId ?? 'person-asha' },
          },
        });
        break;
      case 'Activity':
        activeScreen = TestReact.createElement(ActivityScreen, {
          navigation: rootNavigation,
        });
        break;
      case 'ActivityDetail':
        activeScreen = TestReact.createElement(ActivityDetailScreen, {
          route: {
            params: { activityId: route.params?.activityId ?? 'missing' },
          },
        });
        break;
      case 'Attention':
        activeScreen = TestReact.createElement(AttentionScreen, {
          navigation: { navigate: goHome },
        });
        break;
      case 'DataBoundary':
        activeScreen = TestReact.createElement(DataBoundaryScreen);
        break;
      default:
        activeScreen = TestReact.createElement(HomeScreen, {
          navigation: tabNavigation,
        });
    }

    return TestReact.createElement(
      View,
      null,
      TestReact.createElement(
        View,
        null,
        ...tabNames.map(name =>
          TestReact.createElement(
            Pressable,
            {
              accessibilityRole: 'button',
              key: name,
              onPress: () => setRoute({ name }),
              testID: `tab-${name.toLowerCase()}`,
            },
            TestReact.createElement(Text, null, name),
          ),
        ),
      ),
      activeScreen,
    );
  }

  return { RootNavigator };
});

const { BirthdayAutopilotApp } =
  require('./AppRoot') as typeof import('./AppRoot');
const { FixturePreviewApp } =
  require('./FixturePreviewApp') as typeof import('./FixturePreviewApp');

afterEach(async () => {
  await cleanup();
  await appI18n.changeLanguage('en');
});

async function finishSetup(platform: 'android' | 'ios') {
  await render(
    <FixturePreviewApp platformOverride={platform} />,
  );

  expect(screen.getByTestId('setup-step-1')).toBeTruthy();
  await fireEvent.press(screen.getByTestId('setup-welcome-continue'));
  await fireEvent.press(screen.getByTestId('setup-connect-fixture'));

  const review = screen.getByTestId('setup-review-selection');
  expect(review.props.accessibilityState).toEqual({ disabled: true });
  await fireEvent.press(screen.getByTestId('setup-person-person-asha'));
  await fireEvent.press(review);
  await fireEvent.press(screen.getByTestId('setup-finish'));

  await waitFor(() => expect(screen.getByTestId('home-screen')).toBeTruthy());
}

describe('cross-platform fixture journey', () => {
  it('keeps Android automation claims truthful through setup and key routes', async () => {
    await finishSetup('android');

    expect(screen.getByText('Automation Edition')).toBeTruthy();
    expect(screen.getByText(/No text can be sent\./)).toBeTruthy();

    await fireEvent.press(screen.getByTestId('home-open-preview'));
    await waitFor(() =>
      expect(screen.getByTestId('approved-message-screen')).toBeTruthy(),
    );
    expect(screen.getByText(/1 Unicode segment/)).toBeTruthy();
    expect(
      screen.getByText(/Submission would not prove delivery/),
    ).toBeTruthy();
    expect(screen.queryByText(/^Send$/)).toBeNull();

    await fireEvent.press(
      screen.getByRole('button', { name: 'Close preview' }),
    );
    await fireEvent.press(
      screen.getByRole('button', { name: 'Review attention item' }),
    );
    await waitFor(() =>
      expect(screen.getByTestId('attention-screen')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('attention-recheck'));
    expect(screen.getByTestId('attention-recheck-result')).toBeTruthy();
    expect(screen.getByText(/Real device state was not checked/)).toBeTruthy();
    await fireEvent.press(screen.getByTestId('attention-return-home'));

    await waitFor(() => expect(screen.getByTestId('home-screen')).toBeTruthy());
    await fireEvent.press(screen.getByTestId('home-open-activity'));
    await waitFor(() =>
      expect(screen.getByTestId('activity-screen')).toBeTruthy(),
    );
    expect(screen.getByText('Sending from this phone')).toBeTruthy();
    expect(
      screen.getByText('Sent from this phone; delivery not confirmed.'),
    ).toBeTruthy();
    await fireEvent.press(screen.getByTestId('activity-row-activity-a1'));
    await waitFor(() =>
      expect(screen.getByTestId('activity-detail-screen')).toBeTruthy(),
    );
    expect(screen.getByText(/contains no name, number, birthday/)).toBeTruthy();
  });

  it('keeps iOS in user-confirmed Companion mode', async () => {
    await render(
      <FixturePreviewApp
        platformOverride="ios"
        initialSetupComplete
      />,
    );

    expect(screen.getByText('Companion mode')).toBeTruthy();
    expect(screen.getByText(/foreground review and Send action/)).toBeTruthy();

    await fireEvent.press(screen.getByTestId('home-open-preview'));
    await waitFor(() =>
      expect(screen.getByTestId('approved-message-screen')).toBeTruthy(),
    );
    expect(
      screen.getByText(
        /Messages and iOS control the available sender line and final transport/u,
      ),
    ).toBeTruthy();
    expect(
      screen.getByText(/app cannot select or guarantee either/u),
    ).toBeTruthy();
    await fireEvent.press(screen.getByTestId('record-composer-fixture'));
    expect(screen.getByTestId('composer-fixture-result')).toBeTruthy();
    expect(screen.getByText(/no message was sent/)).toBeTruthy();
  });

  it('opens People details and keeps fixture language device-driven', async () => {
    await render(
      <FixturePreviewApp
        platformOverride="android"
        initialSetupComplete
      />,
    );

    await fireEvent.press(screen.getByTestId('tab-people'));
    await waitFor(() =>
      expect(screen.getByTestId('people-screen')).toBeTruthy(),
    );
    await fireEvent.press(screen.getByTestId('people-row-person-asha'));
    await waitFor(() =>
      expect(screen.getByTestId('person-detail-screen')).toBeTruthy(),
    );
    expect(screen.getByText('Synthetic Google Contacts fixture')).toBeTruthy();

    await cleanup();
    await render(
      <FixturePreviewApp
        platformOverride="android"
        initialSetupComplete
      />,
    );
    await fireEvent.press(screen.getByTestId('tab-settings'));
    await waitFor(() =>
      expect(screen.getByTestId('settings-screen')).toBeTruthy(),
    );

    await fireEvent.press(screen.getByTestId('appearance-dark'));
    expect(
      screen.getByTestId('appearance-dark').props.accessibilityState,
    ).toEqual({ selected: true });

    expect(screen.queryByTestId('language-en')).toBeNull();
    expect(screen.queryByTestId('language-hi')).toBeNull();
    expect(screen.queryByTestId('language-pseudo')).toBeNull();
    expect(appI18n.resolvedLanguage).toBe('en');
    expect(
      StyleSheet.flatten(screen.getByTestId('app-direction-root').props.style)
        .direction,
    ).toBe('ltr');
  });
});

describe('production-safe data source selection', () => {
  it('defaults to the native boundary and fails closed when bootstrap is unavailable', async () => {
    const getBootstrap = jest.fn(async () => ({
      kind: 'error' as const,
      problem: {
        kind: 'internal' as const,
        supportCode: 'NATIVE_BRIDGE_UNAVAILABLE' as SafeSupportCode,
      },
    }));

    const nativeProjectionPort = {
      getBootstrap,
      subscribeInvalidations: () => () => undefined,
    } as unknown as LiveAppPort;

    await render(
      <BirthdayAutopilotApp
        nativeProjectionPort={nativeProjectionPort}
      />,
    );


    await waitFor(() =>
      expect(screen.getByTestId('native-bootstrap-unavailable')).toBeTruthy(),
    );
    expect(screen.queryByTestId('setup-step-1')).toBeNull();
    expect(screen.queryByText('Synthetic preview data')).toBeNull();
    expect(screen.getByText(/Nothing was changed/)).toBeTruthy();
    expect(
      screen.queryByText('Support reference: NATIVE_BRIDGE_UNAVAILABLE'),
    ).toBeNull();

    await fireEvent.press(screen.getByTestId('native-bootstrap-retry'));
    await waitFor(() => expect(getBootstrap).toHaveBeenCalledTimes(2));
  });

  it('mounts the synthetic journey only through explicit fixture opt-in', async () => {
    await render(
      <FixturePreviewApp platformOverride="android" />,
    );

    expect(screen.getByTestId('setup-step-1')).toBeTruthy();
    expect(screen.queryByTestId('native-bootstrap-unavailable')).toBeNull();
  });
});
