import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
} from 'react';
import { StyleSheet, View } from 'react-native';
import {
  DarkTheme,
  DefaultTheme,
  NavigationContainer,
  type NavigatorScreenParams,
  type NavigationProp,
  useIsFocused,
  useNavigation,
  useNavigationContainerRef,
} from '@react-navigation/native';
import {
  createBottomTabNavigator,
  type BottomTabBarProps,
  type BottomTabNavigationProp,
} from '@react-navigation/bottom-tabs';
import {
  createNativeStackNavigator,
  type NativeStackScreenProps,
} from '@react-navigation/native-stack';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { AccountProjection } from '../../domain/account/model';
import type { ActivityRecoveryRoute } from '../../domain/activity/model';
import type { ActivityId, ContactId } from '../../domain/shared/brand';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import { FocusablePressable } from '../../design-system/components/Primitives';
import { Icon, type IconName } from '../../design-system/components/Icon';
import { RouteAccessibilityFocus } from '../../design-system/components/RouteAccessibilityFocus';
import { minimumTargetSize, spacing } from '../../design-system/tokens/theme';
import { useAppTheme } from '../../app/providers/ThemeProvider';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveActivityDetailScreen,
  LiveActivityScreen,
} from './LiveActivityScreen';
import { LiveAttentionScreen } from './LiveAttentionScreen';
import { LiveAutomationScreen } from './LiveAutomationScreen';
import { LiveDiagnosticsScreen } from './LiveDiagnosticsScreen';
import { LiveHomeScreen } from './LiveHomeScreen';
import { LiveHelpLegalScreen } from './LiveHelpLegalScreen';
import { LiveMessageScreen } from './LiveMessageScreen';
import { LivePeopleScreen } from './LivePeopleScreen';
import { LivePersonDetailScreen } from './LivePersonDetailScreen';
import { LivePrivacyScreen } from './LivePrivacyScreen';
import { LiveScheduleScreen } from './LiveScheduleScreen';
import { LiveSettingsScreen } from './LiveSettingsScreen';

type LiveMainTabParamList = {
  Home: undefined;
  People: undefined;
  Settings: undefined;
};

type LiveRootStackParamList = {
  Main: NavigatorScreenParams<LiveMainTabParamList> | undefined;
  Person: Readonly<{ contactId: ContactId }>;
  Activity: undefined;
  ActivityDetail: Readonly<{ activityId: ActivityId }>;
  Attention: undefined;
  Automation: undefined;
  Diagnostics: undefined;
  HelpLegal: undefined;
  Message: undefined;
  Privacy: undefined;
  Schedule: undefined;
};

type LiveRootLeaf =
  | 'Activity'
  | 'Attention'
  | 'Automation'
  | 'Diagnostics'
  | 'HelpLegal'
  | 'Message'
  | 'Privacy'
  | 'Schedule';
type LiveRootNavigation = NavigationProp<LiveRootStackParamList>;

const Tabs = createBottomTabNavigator<LiveMainTabParamList>();
const Stack = createNativeStackNavigator<LiveRootStackParamList>();

type LiveNavigationDependencies = Readonly<{
  account: AccountProjection;
  capability: PlatformCapability;
  onContinueSetup: () => void;
  port: LiveAppPort;
  productSetupRequired: boolean;
  refreshBootstrap: () => Promise<unknown>;
}>;

const LiveNavigationContext = createContext<
  LiveNavigationDependencies | undefined
>(undefined);

function useLiveNavigationDependencies(): LiveNavigationDependencies {
  const value = useContext(LiveNavigationContext);
  if (!value) {
    throw new Error(
      'Live navigation screens must render inside LiveNavigationContext',
    );
  }
  return value;
}

function navigateToTab(
  navigation: Pick<LiveRootNavigation, 'navigate'>,
  tab: keyof LiveMainTabParamList,
) {
  navigation.navigate('Main', { screen: tab });
}

/** Keep the visible origin tab underneath a leaf so the native Back gesture,
 * Android system Back, and the screen's visible Back action agree. */
function navigateToLeafFromTab(
  navigation: Pick<LiveRootNavigation, 'navigate'>,
  tab: keyof LiveMainTabParamList,
  leaf: LiveRootLeaf,
) {
  navigation.navigate('Main', { screen: tab });
  navigation.navigate(leaf);
}

function navigateToLeafFromHome(
  navigation: Pick<LiveRootNavigation, 'navigate'>,
  leaf: LiveRootLeaf,
) {
  navigateToLeafFromTab(navigation, 'Home', leaf);
}

function navigateToPerson(
  navigation: Pick<LiveRootNavigation, 'navigate'>,
  contactId: ContactId,
) {
  navigation.navigate('Main', { screen: 'People' });
  navigation.navigate('Person', { contactId });
}

function navigateToActivityRecovery(
  navigation: Pick<LiveRootNavigation, 'navigate'>,
  route: ActivityRecoveryRoute,
) {
  switch (route) {
    case 'attention':
      navigateToLeafFromHome(navigation, 'Attention');
      return;
    case 'automation':
      navigateToLeafFromHome(navigation, 'Automation');
      return;
    case 'people':
      navigateToTab(navigation, 'People');
      return;
    case 'settings':
      navigateToTab(navigation, 'Settings');
  }
}

function useRootNavigation(): LiveRootNavigation {
  const tabNavigation =
    useNavigation<BottomTabNavigationProp<LiveMainTabParamList>>();
  const rootNavigation = tabNavigation.getParent<LiveRootNavigation>();
  if (!rootNavigation) {
    throw new Error('Live tabs require the live root stack');
  }
  return rootNavigation;
}

function LiveRouteFrame({
  announcement,
  children,
  routeKey,
}: React.PropsWithChildren<{
  announcement: string;
  routeKey: string;
}>) {
  const isFocused = useIsFocused();
  return (
    <View style={styles.content}>
      {isFocused ? (
        <RouteAccessibilityFocus
          announcement={announcement}
          routeKey={routeKey}
        />
      ) : null}
      <View style={styles.content}>{children}</View>
    </View>
  );
}

function LiveHomeRoute() {
  const { capability, onContinueSetup, port, productSetupRequired } =
    useLiveNavigationDependencies();
  const navigation = useRootNavigation();
  const { t } = useAppLocalization();

  return (
    <LiveRouteFrame announcement={t('tabs.home')} routeKey="tab:home">
      <LiveHomeScreen
        capability={capability}
        onOpenActivity={() => navigateToLeafFromHome(navigation, 'Activity')}
        onOpenAttention={() => navigateToLeafFromHome(navigation, 'Attention')}
        onOpenAutomation={() =>
          navigateToLeafFromHome(navigation, 'Automation')
        }
        onOpenPeople={() => navigateToTab(navigation, 'People')}
        onOpenPerson={contactId => navigateToPerson(navigation, contactId)}
        onContinueSetup={onContinueSetup}
        port={port}
        productSetupRequired={productSetupRequired}
      />
    </LiveRouteFrame>
  );
}

function LivePeopleRoute() {
  const { port } = useLiveNavigationDependencies();
  const navigation = useRootNavigation();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame announcement={t('tabs.people')} routeKey="tab:people">
      <LivePeopleScreen
        onOpenPerson={contactId => navigateToPerson(navigation, contactId)}
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveSettingsRoute() {
  const { capability, port } = useLiveNavigationDependencies();
  const navigation = useRootNavigation();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame announcement={t('tabs.settings')} routeKey="tab:settings">
      <LiveSettingsScreen
        capability={capability}
        onOpenAutomation={() =>
          navigateToLeafFromTab(navigation, 'Settings', 'Automation')
        }
        onOpenHelpLegal={() =>
          navigateToLeafFromTab(navigation, 'Settings', 'HelpLegal')
        }
        onOpenMessage={() =>
          navigateToLeafFromTab(navigation, 'Settings', 'Message')
        }
        onOpenPrivacy={() =>
          navigateToLeafFromTab(navigation, 'Settings', 'Privacy')
        }
        onOpenSchedule={() =>
          navigateToLeafFromTab(navigation, 'Settings', 'Schedule')
        }
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LivePersonRoute({
  navigation,
  route,
}: NativeStackScreenProps<LiveRootStackParamList, 'Person'>) {
  const { capability, port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame
      announcement={t('live.person.detailsTitle')}
      routeKey={`person:${route.params.contactId}`}
    >
      <LivePersonDetailScreen
        capability={capability}
        contactId={route.params.contactId}
        onBack={() => navigation.goBack()}
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveActivityRoute({
  navigation,
}: NativeStackScreenProps<LiveRootStackParamList, 'Activity'>) {
  const { capability, port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame announcement={t('live.activity.title')} routeKey="activity">
      <LiveActivityScreen
        capability={capability}
        onBack={() => navigation.goBack()}
        onOpenDetail={record =>
          navigation.navigate('ActivityDetail', { activityId: record.id })
        }
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveActivityDetailRoute({
  navigation,
  route,
}: NativeStackScreenProps<LiveRootStackParamList, 'ActivityDetail'>) {
  const { port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame
      announcement={t('live.activity.detailTitle')}
      routeKey={`activity:${route.params.activityId}`}
    >
      <LiveActivityDetailScreen
        activityId={route.params.activityId}
        onBack={() => navigation.goBack()}
        onOpenRecovery={recoveryRoute =>
          navigateToActivityRecovery(navigation, recoveryRoute)
        }
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveAttentionRoute({
  navigation,
}: NativeStackScreenProps<LiveRootStackParamList, 'Attention'>) {
  const { port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame
      announcement={t('live.attention.title')}
      routeKey="attention"
    >
      <LiveAttentionScreen
        onBack={() => navigation.goBack()}
        onOpenAutomation={() =>
          navigateToLeafFromHome(navigation, 'Automation')
        }
        onOpenMessage={() => navigateToLeafFromHome(navigation, 'Message')}
        onOpenPeople={() => navigateToTab(navigation, 'People')}
        onOpenSettings={() => navigateToTab(navigation, 'Settings')}
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveAutomationRoute({
  navigation,
}: NativeStackScreenProps<LiveRootStackParamList, 'Automation'>) {
  const { capability, port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame
      announcement={t('live.automation.title')}
      routeKey="automation"
    >
      <LiveAutomationScreen
        capability={capability}
        onBack={() => navigation.goBack()}
        onOpenMessage={() => navigation.navigate('Message')}
        onOpenSchedule={() => navigation.navigate('Schedule')}
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveDiagnosticsRoute({
  navigation,
}: NativeStackScreenProps<LiveRootStackParamList, 'Diagnostics'>) {
  const { port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame
      announcement={t('live.diagnostics.title')}
      routeKey="diagnostics"
    >
      <LiveDiagnosticsScreen onBack={() => navigation.goBack()} port={port} />
    </LiveRouteFrame>
  );
}

function LiveHelpLegalRoute({
  navigation,
}: NativeStackScreenProps<LiveRootStackParamList, 'HelpLegal'>) {
  const { capability, port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame announcement={t('live.help.title')} routeKey="help-legal">
      <LiveHelpLegalScreen
        onBack={() => navigation.goBack()}
        onOpenDiagnostics={() => navigation.navigate('Diagnostics')}
        platform={capability.platform}
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveMessageRoute({
  navigation,
}: NativeStackScreenProps<LiveRootStackParamList, 'Message'>) {
  const { port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame announcement={t('live.message.title')} routeKey="message">
      <LiveMessageScreen onBack={() => navigation.goBack()} port={port} />
    </LiveRouteFrame>
  );
}

function LivePrivacyRoute({
  navigation,
}: NativeStackScreenProps<LiveRootStackParamList, 'Privacy'>) {
  const { capability, port, refreshBootstrap } =
    useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame announcement={t('live.privacy.title')} routeKey="privacy">
      <LivePrivacyScreen
        onBack={() => navigation.goBack()}
        onLifecycleStateChange={refreshBootstrap}
        onOpenHelpLegal={() =>
          navigateToLeafFromTab(navigation, 'Settings', 'HelpLegal')
        }
        platform={capability.platform}
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveScheduleRoute({
  navigation,
}: NativeStackScreenProps<LiveRootStackParamList, 'Schedule'>) {
  const { capability, port } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  return (
    <LiveRouteFrame
      announcement={t('live.settings.schedule')}
      routeKey="schedule"
    >
      <LiveScheduleScreen
        onBack={() => navigation.goBack()}
        platform={capability.platform}
        port={port}
      />
    </LiveRouteFrame>
  );
}

function LiveTabButton({
  disabled,
  icon,
  label,
  onLongPress,
  onPress,
  selected,
  testID,
}: {
  disabled: boolean;
  icon: IconName;
  label: string;
  onLongPress: () => void;
  onPress: () => void;
  selected: boolean;
  testID: string;
}) {
  const { colors } = useAppTheme();
  const color = selected ? colors.accent : colors.textMuted;
  return (
    <FocusablePressable
      accessibilityRole="tab"
      accessibilityLabel={label}
      accessibilityState={{ disabled, selected }}
      disabled={disabled}
      onLongPress={onLongPress}
      onPress={onPress}
      testID={testID}
      style={({ pressed }) => [
        styles.tab,
        {
          backgroundColor: selected ? colors.infoSurface : colors.surface,
          opacity: disabled ? 0.45 : pressed ? 0.72 : 1,
        },
      ]}
    >
      <Icon name={icon} color={color} size={24} />
      <AppText variant="caption" style={{ color }}>
        {label}
      </AppText>
    </FocusablePressable>
  );
}

const tabPresentation = {
  Home: { icon: 'home', testID: 'live-tab-home' },
  People: { icon: 'people', testID: 'live-tab-people' },
  Settings: { icon: 'settings', testID: 'live-tab-settings' },
} as const satisfies Record<
  keyof LiveMainTabParamList,
  Readonly<{ icon: IconName; testID: string }>
>;

function LiveTabBar({ navigation, state }: BottomTabBarProps) {
  const { colors } = useAppTheme();
  const { productSetupRequired } = useLiveNavigationDependencies();
  const { t } = useAppLocalization();
  const labels: Record<keyof LiveMainTabParamList, string> = {
    Home: t('tabs.home'),
    People: t('tabs.people'),
    Settings: t('tabs.settings'),
  };

  return (
    <SafeAreaView
      accessibilityRole="tablist"
      edges={['left', 'right', 'bottom']}
      testID="live-tab-list"
      style={[
        styles.tabBar,
        { backgroundColor: colors.surface, borderTopColor: colors.border },
      ]}
    >
      {state.routes.map((route, index) => {
        const name = route.name as keyof LiveMainTabParamList;
        const presentation = tabPresentation[name];
        const selected = state.index === index;
        const disabled = productSetupRequired && name !== 'Home';
        return (
          <LiveTabButton
            disabled={disabled}
            icon={presentation.icon}
            key={route.key}
            label={labels[name]}
            onLongPress={() => {
              if (disabled) return;
              navigation.emit({ type: 'tabLongPress', target: route.key });
            }}
            onPress={() => {
              if (disabled) return;
              const event = navigation.emit({
                canPreventDefault: true,
                target: route.key,
                type: 'tabPress',
              });
              if (!selected && !event.defaultPrevented) {
                navigation.navigate(route.name, route.params);
              }
            }}
            selected={selected}
            testID={presentation.testID}
          />
        );
      })}
    </SafeAreaView>
  );
}

const renderLiveTabBar = (props: BottomTabBarProps) => (
  <LiveTabBar {...props} />
);

function LiveMainTabs() {
  const { colors } = useAppTheme();
  return (
    <Tabs.Navigator
      backBehavior="initialRoute"
      initialRouteName="Home"
      screenOptions={{
        animation: 'none',
        headerShown: false,
        sceneStyle: { backgroundColor: colors.background },
      }}
      tabBar={renderLiveTabBar}
    >
      <Tabs.Screen name="Home" component={LiveHomeRoute} />
      <Tabs.Screen name="People" component={LivePeopleRoute} />
      <Tabs.Screen name="Settings" component={LiveSettingsRoute} />
    </Tabs.Navigator>
  );
}

export function LiveAppShell({
  account,
  capability,
  onContinueSetup,
  port,
  productSetupRequired,
  refreshBootstrap,
}: LiveNavigationDependencies) {
  const theme = useAppTheme();
  const { language } = useAppLocalization();
  const navigationRef = useNavigationContainerRef<LiveRootStackParamList>();
  const coldRouteQueriedRef = useRef(false);
  const pendingLeafRef = useRef<LiveRootLeaf | undefined>(undefined);
  const routeQueryInFlightRef = useRef(false);
  const routeQueryQueuedRef = useRef(false);
  const dependencies = useMemo(
    () => ({
      account,
      capability,
      onContinueSetup,
      port,
      productSetupRequired,
      refreshBootstrap,
    }),
    [
      account,
      capability,
      onContinueSetup,
      port,
      productSetupRequired,
      refreshBootstrap,
    ],
  );
  const navigationTheme = useMemo(
    () => ({
      ...(theme.isDark ? DarkTheme : DefaultTheme),
      colors: {
        ...(theme.isDark ? DarkTheme.colors : DefaultTheme.colors),
        background: theme.colors.background,
        border: theme.colors.border,
        card: theme.colors.surface,
        notification: theme.colors.critical,
        primary: theme.colors.accent,
        text: theme.colors.text,
      },
    }),
    [theme.colors, theme.isDark],
  );
  const flushPendingLeaf = useCallback(() => {
    const leaf = pendingLeafRef.current;
    if (!leaf || !navigationRef.isReady()) return;
    pendingLeafRef.current = undefined;
    navigateToLeafFromHome(navigationRef, leaf);
  }, [navigationRef]);

  useEffect(() => {
    if (productSetupRequired) {
      pendingLeafRef.current = undefined;
      routeQueryQueuedRef.current = false;
      return undefined;
    }
    let active = true;
    const queryPendingRoute = async () => {
      if (routeQueryInFlightRef.current) {
        routeQueryQueuedRef.current = true;
        return;
      }
      routeQueryInFlightRef.current = true;
      do {
        routeQueryQueuedRef.current = false;
        try {
          const result = await port.getPendingRoute();
          if (active && result.kind === 'ok') {
            if (result.envelope.value.kind === 'attention') {
              pendingLeafRef.current = 'Attention';
              flushPendingLeaf();
            }
          }
        } catch {
          // A native route is advisory. Keep the current route on any failure.
        }
      } while (active && routeQueryQueuedRef.current);
      routeQueryInFlightRef.current = false;
    };

    const unsubscribe = port.subscribeRouteAvailable(() => {
      queryPendingRoute().catch(() => undefined);
    });
    if (!coldRouteQueriedRef.current) {
      coldRouteQueriedRef.current = true;
      queryPendingRoute().catch(() => undefined);
    }
    return () => {
      active = false;
      pendingLeafRef.current = undefined;
      routeQueryQueuedRef.current = false;
      routeQueryInFlightRef.current = false;
      unsubscribe();
    };
  }, [flushPendingLeaf, port, productSetupRequired]);

  return (
    <View style={styles.shell} testID="live-app-shell">
      <LiveNavigationContext.Provider value={dependencies}>
        <NavigationContainer
          direction={language === 'ar-XB' ? 'rtl' : 'ltr'}
          onReady={flushPendingLeaf}
          ref={navigationRef}
          theme={navigationTheme}
        >
          <Stack.Navigator
            initialRouteName="Main"
            screenOptions={{
              contentStyle: { backgroundColor: theme.colors.background },
              gestureEnabled: true,
              headerShown: false,
            }}
          >
            <Stack.Screen name="Main" component={LiveMainTabs} />
            <Stack.Screen name="Person" component={LivePersonRoute} />
            <Stack.Screen name="Activity" component={LiveActivityRoute} />
            <Stack.Screen
              name="ActivityDetail"
              component={LiveActivityDetailRoute}
            />
            <Stack.Screen name="Attention" component={LiveAttentionRoute} />
            <Stack.Screen name="Automation" component={LiveAutomationRoute} />
            <Stack.Screen name="Diagnostics" component={LiveDiagnosticsRoute} />
            <Stack.Screen name="HelpLegal" component={LiveHelpLegalRoute} />
            <Stack.Screen name="Message" component={LiveMessageRoute} />
            <Stack.Screen name="Privacy" component={LivePrivacyRoute} />
            <Stack.Screen name="Schedule" component={LiveScheduleRoute} />
          </Stack.Navigator>
        </NavigationContainer>
      </LiveNavigationContext.Provider>
    </View>
  );
}

const styles = StyleSheet.create({
  shell: { flex: 1 },
  content: { flex: 1 },
  tabBar: {
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    minHeight: 72,
    paddingBottom: spacing.xs,
    paddingHorizontal: spacing.xs,
    paddingTop: spacing.xs,
  },
  tab: {
    alignItems: 'center',
    borderRadius: 12,
    flex: 1,
    gap: spacing.xs,
    justifyContent: 'center',
    minHeight: minimumTargetSize,
    minWidth: minimumTargetSize,
    paddingHorizontal: spacing.xs,
    paddingVertical: spacing.xs,
  },
});
