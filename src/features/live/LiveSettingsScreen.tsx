import React, { useCallback } from 'react';

import type { AccountProjection } from '../../domain/account/model';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Card,
  Screen,
  SectionHeading,
  SettingRow,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import { LiveAndroidDeviceControls } from './LiveAndroidDeviceControls';
import { nativePlatformMismatchProblem } from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';

const cleanupKeys: Record<
  Extract<AccountProjection, { kind: 'cleanup-pending' }>['operation'],
  TranslationKey
> = {
  disconnect: 'live.settings.cleanup.disconnect',
  revoke: 'live.settings.cleanup.revoke',
  'sign-out': 'live.settings.cleanup.signOut',
  delete: 'live.settings.cleanup.delete',
  repair: 'live.settings.cleanup.repair',
};

export function LiveSettingsScreen({
  capability,
  onOpenAutomation,
  onOpenHelpLegal,
  onOpenMessage,
  onOpenPrivacy,
  onOpenSchedule,
  port,
}: {
  capability: PlatformCapability;
  onOpenAutomation: () => void;
  onOpenHelpLegal: () => void;
  onOpenMessage: () => void;
  onOpenPrivacy: () => void;
  onOpenSchedule: () => void;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const loadAccount = useCallback(() => port.getAccount(), [port]);
  const account = useLiveProjection(loadAccount, port, ['account']);

  const accountLabel = (value: AccountProjection): string => {
    switch (value.kind) {
      case 'connected':
        return value.displayEmail;
      case 'signed-out':
        return t('live.settings.signedOut');
      case 'connecting':
        return t('live.settings.connecting');
      case 'reconnect-required':
        return t('live.settings.reconnectGeneric');
      case 'cleanup-pending':
        return t(cleanupKeys[value.operation]);
    }
  };

  const accountValue =
    account.state.kind === 'ready'
      ? account.state.result.envelope.value
      : undefined;
  const accountMatchesPlatform =
    accountValue?.kind !== 'connected' ||
    accountValue.sender.platform === capability.platform;
  const accountTrusted =
    account.state.kind === 'ready' &&
    !account.state.refreshing &&
    !account.state.refreshProblem &&
    accountMatchesPlatform;

  return (
    <Screen
      includeTopInset
      includeBottomInset={false}
      testID="live-settings-screen"
    >
      <AppText variant="title" accessibilityRole="header">
        {t('settings.title')}
      </AppText>

      <Card>
        <SectionHeading title={t('live.settings.birthdayPlan')} />
        <SettingRow
          title={t('live.settings.message')}
          detail={t('live.settings.messageDetail')}
          onPress={onOpenMessage}
          testID="live-settings-message"
        />
        <SettingRow
          title={t('live.settings.schedule')}
          detail={t('live.settings.scheduleDetail')}
          onPress={onOpenSchedule}
          testID="live-settings-schedule"
        />
        <SettingRow
          title={t(
            capability.platform === 'android'
              ? 'live.settings.androidSending'
              : 'live.settings.iosReminders',
          )}
          detail={t(
            capability.platform === 'android'
              ? 'live.settings.androidSendingDetail'
              : 'live.settings.iosRemindersDetail',
          )}
          onPress={onOpenAutomation}
          testID="live-settings-automation"
        />
      </Card>

      <Card>
        <SectionHeading title={t('live.settings.accountPrivacy')} />
        {account.state.kind === 'loading' ? (
          <LiveLoading label={t('live.settings.accountLoading')} />
        ) : null}
        {account.state.kind === 'error' ? (
          <LiveError
            title={t('live.settings.accountUnavailable')}
            problem={account.state.problem}
            onRetry={() => account.reload()}
          />
        ) : null}
        {account.state.kind === 'ready' && !accountMatchesPlatform ? (
          <LiveError
            title={t('live.settings.accountMismatch')}
            problem={nativePlatformMismatchProblem}
            onRetry={() => account.reload()}
          />
        ) : null}
        {account.state.kind === 'ready' && accountMatchesPlatform ? (
          <>
            {account.state.refreshing ? (
              <LiveLoading label={t('live.settings.accountLoading')} />
            ) : null}
            {account.state.refreshProblem ? (
              <LiveRefreshProblem problem={account.state.refreshProblem} />
            ) : null}
            {accountTrusted ? (
              <StatusRow
                title={t('live.settings.googleAccount')}
                detail={accountLabel(account.state.result.envelope.value)}
                tone={
                  account.state.result.envelope.value.kind === 'connected'
                    ? 'positive'
                    : 'warning'
                }
              />
            ) : null}
          </>
        ) : null}
        <SettingRow
          title={t('live.settings.openPrivacy')}
          detail={t('live.settings.privacyDetail')}
          onPress={onOpenPrivacy}
          testID="live-settings-privacy"
        />
      </Card>

      {capability.platform === 'android' &&
      account.state.kind === 'ready' &&
      accountMatchesPlatform ? (
        <LiveAndroidDeviceControls
          account={
            account.state.kind === 'ready'
              ? account.state.result.envelope
              : undefined
          }
          accountProjectionStable={accountTrusted}
          onAccountReload={account.reload}
          onOpenAutomation={onOpenAutomation}
          port={port}
          showNotifications={accountTrusted}
        />
      ) : null}

      <Card>
        <SectionHeading title={t('live.settings.help')} />
        <SettingRow
          title={t('live.settings.openHelpLegal')}
          detail={t('live.settings.helpLegalDetail')}
          onPress={onOpenHelpLegal}
          testID="live-settings-help-legal"
        />
      </Card>
    </Screen>
  );
}
