import React, { useEffect, useRef, useState } from 'react';
import { BackHandler, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { ActivityId, ContactId } from '../../domain/shared/brand';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import { Icon, type IconName } from '../../design-system/components/Icon';
import { minimumTargetSize, spacing } from '../../design-system/tokens/theme';
import { useAppTheme } from '../../app/providers/ThemeProvider';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { LiveAppPort, LiveCompanionPort } from './LiveAppPort';
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
import { LiveSettingsScreen } from './LiveSettingsScreen';

type LiveTab = 'home' | 'people' | 'settings';
type LiveRoute =
  | Readonly<{ kind: 'tab'; tab: LiveTab }>
  | Readonly<{ kind: 'person'; contactId: ContactId }>
  | Readonly<{ kind: 'activity' }>
  | Readonly<{ kind: 'activity-detail'; activityId: ActivityId }>
  | Readonly<{ kind: 'attention' }>
  | Readonly<{ kind: 'automation' }>
  | Readonly<{ kind: 'diagnostics' }>
  | Readonly<{ kind: 'help-legal' }>
  | Readonly<{ kind: 'message' }>
  | Readonly<{ kind: 'privacy' }>;

function LiveTabButton({
  icon,
  label,
  onPress,
  selected,
  testID,
}: {
  icon: IconName;
  label: string;
  onPress: () => void;
  selected: boolean;
  testID: string;
}) {
  const { colors } = useAppTheme();
  const color = selected ? colors.accent : colors.textMuted;
  return (
    <Pressable
      accessibilityRole="tab"
      accessibilityLabel={label}
      accessibilityState={{ selected }}
      onPress={onPress}
      testID={testID}
      style={({ pressed }) => [
        styles.tab,
        {
          backgroundColor: selected ? colors.infoSurface : colors.surface,
          opacity: pressed ? 0.72 : 1,
        },
      ]}
    >
      <Icon name={icon} color={color} size={24} />
      <AppText variant="caption" style={{ color }}>
        {label}
      </AppText>
    </Pressable>
  );
}

export function LiveAppShell({
  capability,
  companionPort,
  port,
}: {
  capability: PlatformCapability;
  companionPort: LiveCompanionPort;
  port: LiveAppPort;
}) {
  const { colors } = useAppTheme();
  const { t } = useAppLocalization();
  const [route, setRoute] = useState<LiveRoute>({ kind: 'tab', tab: 'home' });
  const coldRouteQueriedRef = useRef(false);
  const routeQueryInFlightRef = useRef(false);
  const routeQueryQueuedRef = useRef(false);

  useEffect(() => {
    const subscription = BackHandler.addEventListener(
      'hardwareBackPress',
      () => {
        if (route.kind === 'activity-detail') {
          setRoute({ kind: 'activity' });
          return true;
        }
        if (route.kind === 'person') {
          setRoute({ kind: 'tab', tab: 'people' });
          return true;
        }
        if (route.kind !== 'tab') {
          setRoute({ kind: 'tab', tab: 'home' });
          return true;
        }
        if (route.tab !== 'home') {
          setRoute({ kind: 'tab', tab: 'home' });
          return true;
        }
        return false;
      },
    );
    return () => subscription.remove();
  }, [route]);

  useEffect(() => {
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
          if (
            active &&
            result.kind === 'ok' &&
            result.envelope.value.kind === 'automation-review'
          ) {
            setRoute({ kind: 'automation' });
          } else if (
            active &&
            result.kind === 'ok' &&
            result.envelope.value.kind === 'attention'
          ) {
            setRoute({ kind: 'attention' });
          }
        } catch {
          // A route is advisory. Fail closed on the current screen.
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
      routeQueryQueuedRef.current = false;
      routeQueryInFlightRef.current = false;
      unsubscribe();
    };
  }, [port]);

  let content: React.ReactNode;
  if (route.kind === 'person') {
    content = (
      <LivePersonDetailScreen
        capability={capability}
        contactId={route.contactId}
        onBack={() => setRoute({ kind: 'tab', tab: 'people' })}
        port={port}
      />
    );
  } else if (route.kind === 'activity') {
    content = (
      <LiveActivityScreen
        onBack={() => setRoute({ kind: 'tab', tab: 'home' })}
        onOpenAttention={() => setRoute({ kind: 'attention' })}
        onOpenDetail={record =>
          setRoute({ kind: 'activity-detail', activityId: record.id })
        }
        port={port}
      />
    );
  } else if (route.kind === 'activity-detail') {
    content = (
      <LiveActivityDetailScreen
        activityId={route.activityId}
        onBack={() => setRoute({ kind: 'activity' })}
        port={port}
      />
    );
  } else if (route.kind === 'attention') {
    content = (
      <LiveAttentionScreen
        onBack={() => setRoute({ kind: 'tab', tab: 'home' })}
        port={port}
      />
    );
  } else if (route.kind === 'automation') {
    content = (
      <LiveAutomationScreen
        capability={capability}
        companionPort={companionPort}
        onBack={() => setRoute({ kind: 'tab', tab: 'home' })}
        port={port}
      />
    );
  } else if (route.kind === 'diagnostics') {
    content = (
      <LiveDiagnosticsScreen
        onBack={() => setRoute({ kind: 'tab', tab: 'settings' })}
        port={port}
      />
    );
  } else if (route.kind === 'help-legal') {
    content = (
      <LiveHelpLegalScreen
        onBack={() => setRoute({ kind: 'tab', tab: 'settings' })}
        platform={capability.platform}
        port={port}
      />
    );
  } else if (route.kind === 'message') {
    content = (
      <LiveMessageScreen
        onBack={() => setRoute({ kind: 'tab', tab: 'home' })}
        port={port}
      />
    );
  } else if (route.kind === 'privacy') {
    content = (
      <LivePrivacyScreen
        onBack={() => setRoute({ kind: 'tab', tab: 'settings' })}
        onOpenHelpLegal={() => setRoute({ kind: 'help-legal' })}
        platform={capability.platform}
        port={port}
      />
    );
  } else {
    switch (route.tab) {
      case 'home':
        content = (
          <LiveHomeScreen
            capability={capability}
            onOpenActivity={() => setRoute({ kind: 'activity' })}
            onOpenAttention={() => setRoute({ kind: 'attention' })}
            onOpenAutomation={() => setRoute({ kind: 'automation' })}
            onOpenMessage={() => setRoute({ kind: 'message' })}
            onOpenPeople={() => setRoute({ kind: 'tab', tab: 'people' })}
            port={port}
          />
        );
        break;
      case 'people':
        content = (
          <LivePeopleScreen
            onOpenPerson={contactId => setRoute({ kind: 'person', contactId })}
            port={port}
          />
        );
        break;
      case 'settings':
        content = (
          <LiveSettingsScreen
            capability={capability}
            onOpenActivity={() => setRoute({ kind: 'activity' })}
            onOpenAttention={() => setRoute({ kind: 'attention' })}
            onOpenAutomation={() => setRoute({ kind: 'automation' })}
            onOpenDiagnostics={() => setRoute({ kind: 'diagnostics' })}
            onOpenHelpLegal={() => setRoute({ kind: 'help-legal' })}
            onOpenPrivacy={() => setRoute({ kind: 'privacy' })}
            port={port}
          />
        );
        break;
    }
  }

  return (
    <View style={styles.shell} testID="live-app-shell">
      <View style={styles.content}>{content}</View>
      {route.kind === 'tab' ? (
        <SafeAreaView
          accessibilityRole="tablist"
          edges={['left', 'right', 'bottom']}
          style={[
            styles.tabBar,
            { backgroundColor: colors.surface, borderTopColor: colors.border },
          ]}
        >
          <LiveTabButton
            icon="home"
            label={t('tabs.home')}
            selected={route.tab === 'home'}
            onPress={() => setRoute({ kind: 'tab', tab: 'home' })}
            testID="live-tab-home"
          />
          <LiveTabButton
            icon="people"
            label={t('tabs.people')}
            selected={route.tab === 'people'}
            onPress={() => setRoute({ kind: 'tab', tab: 'people' })}
            testID="live-tab-people"
          />
          <LiveTabButton
            icon="settings"
            label={t('tabs.settings')}
            selected={route.tab === 'settings'}
            onPress={() => setRoute({ kind: 'tab', tab: 'settings' })}
            testID="live-tab-settings"
          />
        </SafeAreaView>
      ) : null}
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
