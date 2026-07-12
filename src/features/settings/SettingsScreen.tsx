import React from 'react';
import { StyleSheet, View } from 'react-native';
import { CompositeScreenProps } from '@react-navigation/native';
import { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import {
  MainTabParamList,
  RootStackParamList,
} from '../../app/navigation/types';
import { useFixture } from '../../app/providers/FixtureProvider';
import { useAppTheme } from '../../app/providers/ThemeProvider';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ChoiceChip,
  FixtureNotice,
  LabeledSwitch,
  Screen,
  SectionHeading,
  SettingRow,
  StatusRow,
} from '../../design-system/components/Primitives';
import { spacing } from '../../design-system/tokens/theme';
import { useAppLocalization } from '../../localization/LocalizationProvider';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList, 'Settings'>,
  NativeStackScreenProps<RootStackParamList>
>;

export function SettingsScreen({ navigation }: Props) {
  const {
    platform,
    planPaused,
    companionReminderEnabled,
    togglePlan,
    toggleReminder,
    resetFixture,
  } = useFixture();
  const { preference, setPreference } = useAppTheme();
  const { t } = useAppLocalization();

  const rootNavigation = navigation.getParent();

  return (
    <Screen testID="settings-screen">
      <FixtureNotice />
      <AppText variant="title" accessibilityRole="header">
        {t('settings.title')}
      </AppText>

      <Card>
        <SectionHeading title={t('settings.platform')} />
        <StatusRow
          title={
            platform === 'android' ? t('home.androidMode') : t('home.iosMode')
          }
          detail={
            platform === 'android'
              ? t('settings.androidPlatform')
              : t('settings.iosPlatform')
          }
          tone="info"
        />
      </Card>

      <SectionHeading title={t('settings.appearance')} />
      <View accessibilityRole="radiogroup" style={styles.chips}>
        <ChoiceChip
          label={t('settings.system')}
          selected={preference === 'system'}
          onPress={() => setPreference('system')}
          testID="appearance-system"
        />
        <ChoiceChip
          label={t('settings.light')}
          selected={preference === 'light'}
          onPress={() => setPreference('light')}
          testID="appearance-light"
        />
        <ChoiceChip
          label={t('settings.dark')}
          selected={preference === 'dark'}
          onPress={() => setPreference('dark')}
          testID="appearance-dark"
        />
      </View>

      <Card>
        {platform === 'ios' ? (
          <LabeledSwitch
            title={t('settings.reminders')}
            detail={t('settings.remindersBody')}
            value={companionReminderEnabled}
            onValueChange={toggleReminder}
            testID="settings-reminder-switch"
          />
        ) : (
          <LabeledSwitch
            title={t('settings.automation')}
            detail={t('settings.automationBody')}
            value={!planPaused}
            onValueChange={togglePlan}
            testID="settings-automation-switch"
          />
        )}
      </Card>

      <Card>
        <SettingRow
          title={t('settings.readiness')}
          detail={t('settings.readinessBody')}
          onPress={() => rootNavigation?.navigate('Attention')}
          testID="settings-open-attention"
        />
        <SettingRow
          title={t('settings.activity')}
          detail={t('settings.activityBody')}
          onPress={() => rootNavigation?.navigate('Activity')}
          testID="settings-open-activity"
        />
        <SettingRow
          title={t('settings.privacy')}
          detail={t('settings.privacyBody')}
          onPress={() => rootNavigation?.navigate('DataBoundary')}
          testID="settings-open-privacy"
          icon="lock"
        />
        <SettingRow
          title={t('live.settings.openHelpLegal')}
          detail={t('live.help.body')}
          onPress={() => rootNavigation?.navigate('HelpLegal')}
          testID="settings-open-help-legal"
          icon="info"
        />
      </Card>

      <Button
        label={t('settings.replay')}
        accessibilityHint={t('settings.replayHint')}
        onPress={resetFixture}
        variant="secondary"
        testID="settings-replay-setup"
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
});
