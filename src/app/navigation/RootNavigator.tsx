import React from 'react';
import {
  DarkTheme,
  DefaultTheme,
  NavigationContainer,
} from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { useFixture } from '../providers/FixtureProvider';
import { useAppTheme } from '../providers/ThemeProvider';
import { Icon } from '../../design-system/components/Icon';
import { minimumTargetSize } from '../../design-system/tokens/theme';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { ActivityDetailScreen } from '../../features/activity/ActivityDetailScreen';
import { ActivityScreen } from '../../features/activity/ActivityScreen';
import { AttentionScreen } from '../../features/activity/AttentionScreen';
import { ApprovedMessageScreen } from '../../features/home/ApprovedMessageScreen';
import { HomeScreen } from '../../features/home/HomeScreen';
import { PeopleScreen } from '../../features/people/PeopleScreen';
import { PersonDetailScreen } from '../../features/people/PersonDetailScreen';
import { DataBoundaryScreen } from '../../features/settings/DataBoundaryScreen';
import { SettingsScreen } from '../../features/settings/SettingsScreen';
import { SetupJourneyScreen } from '../../features/setup/SetupJourneyScreen';
import { MainTabParamList, RootStackParamList } from './types';

const Tabs = createBottomTabNavigator<MainTabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();

const HomeTabIcon = ({ color }: { color: string }) => (
  <Icon name="home" color={color} size={24} />
);
const PeopleTabIcon = ({ color }: { color: string }) => (
  <Icon name="people" color={color} size={24} />
);
const SettingsTabIcon = ({ color }: { color: string }) => (
  <Icon name="settings" color={color} size={24} />
);

function MainTabs() {
  const { colors } = useAppTheme();
  const { t } = useAppLocalization();
  return (
    <Tabs.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.accent,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.border,
          minHeight: 64,
        },
        tabBarItemStyle: { minHeight: minimumTargetSize },
        tabBarLabelStyle: { fontSize: 13, fontWeight: '600' },
        sceneStyle: { backgroundColor: colors.background },
      }}
    >
      <Tabs.Screen
        name="Home"
        component={HomeScreen}
        options={{
          title: t('tabs.home'),
          tabBarButtonTestID: 'tab-home',
          tabBarAccessibilityLabel: t('tabs.home'),
          tabBarIcon: HomeTabIcon,
        }}
      />
      <Tabs.Screen
        name="People"
        component={PeopleScreen}
        options={{
          title: t('tabs.people'),
          tabBarButtonTestID: 'tab-people',
          tabBarAccessibilityLabel: t('tabs.people'),
          tabBarIcon: PeopleTabIcon,
        }}
      />
      <Tabs.Screen
        name="Settings"
        component={SettingsScreen}
        options={{
          title: t('tabs.settings'),
          tabBarButtonTestID: 'tab-settings',
          tabBarAccessibilityLabel: t('tabs.settings'),
          tabBarIcon: SettingsTabIcon,
        }}
      />
    </Tabs.Navigator>
  );
}

export function RootNavigator() {
  const { setupComplete } = useFixture();
  const theme = useAppTheme();
  const { t } = useAppLocalization();
  const navigationTheme = {
    ...(theme.isDark ? DarkTheme : DefaultTheme),
    colors: {
      ...(theme.isDark ? DarkTheme.colors : DefaultTheme.colors),
      primary: theme.colors.accent,
      background: theme.colors.background,
      card: theme.colors.surface,
      text: theme.colors.text,
      border: theme.colors.border,
      notification: theme.colors.critical,
    },
  };

  if (!setupComplete) {
    return <SetupJourneyScreen />;
  }

  return (
    <NavigationContainer theme={navigationTheme}>
      <Stack.Navigator
        screenOptions={{
          headerBackTitle: t('common.back'),
          headerShadowVisible: false,
          headerStyle: { backgroundColor: theme.colors.surface },
          headerTintColor: theme.colors.text,
          contentStyle: { backgroundColor: theme.colors.background },
        }}
      >
        <Stack.Screen
          name="Main"
          component={MainTabs}
          options={{ headerShown: false }}
        />
        <Stack.Screen
          name="Activity"
          component={ActivityScreen}
          options={{ title: t('activity.title') }}
        />
        <Stack.Screen
          name="ActivityDetail"
          component={ActivityDetailScreen}
          options={{ title: t('activity.detailTitle') }}
        />
        <Stack.Screen
          name="Attention"
          component={AttentionScreen}
          options={{ title: t('attention.title') }}
        />
        <Stack.Screen
          name="ApprovedMessage"
          component={ApprovedMessageScreen}
          options={{ title: t('message.title') }}
        />
        <Stack.Screen
          name="PersonDetail"
          component={PersonDetailScreen}
          options={{ title: t('people.title') }}
        />
        <Stack.Screen
          name="DataBoundary"
          component={DataBoundaryScreen}
          options={{ title: t('privacy.title') }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
