import React, { useCallback, useMemo, useState } from 'react';

import type { AccountProjection } from '../../domain/account/model';
import type {
  AndroidGateName,
  IosGateName,
  ReadinessIssue,
  ReadinessProjection,
} from '../../domain/readiness/model';
import type { NativeProblem } from '../../domain/shared/result';
import type { PlatformCapability } from '../../domain/shared/platform';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ReadinessBanner,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { formatLiveInstant } from '../../localization/formatLive';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { LiveAppPort } from './LiveAppPort';
import type { TranslationKey } from '../../localization/resources';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import {
  nativeBridgeProblem,
  nativePlatformMismatchProblem,
} from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';
import { LivePrivacyInventory } from './LivePrivacyInventory';
import { LiveAndroidDeviceControls } from './LiveAndroidDeviceControls';

const uniqueIssues = (
  readiness: ReadinessProjection,
): readonly ReadinessIssue[] => {
  const gates =
    readiness.platform === 'android'
      ? [readiness.test, readiness.activation, readiness.birthday]
      : [readiness.composer];
  const byId = new Map<string, ReadinessIssue>();
  gates.forEach(gate => {
    if (gate.kind === 'blocked') {
      gate.issues.forEach(issue => byId.set(issue.id, issue));
    }
  });
  return [...byId.values()];
};

const gateKeys: Record<AndroidGateName | IosGateName, TranslationKey> = {
  test: 'live.settings.gate.test',
  activation: 'live.settings.gate.activation',
  birthday: 'live.settings.gate.birthday',
  composer: 'live.settings.gate.composer',
};

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
  onOpenActivity,
  onOpenAttention,
  onOpenDiagnostics,
  onOpenHelpLegal,
  onOpenAutomation,
  onOpenPrivacy,
  port,
}: {
  capability: PlatformCapability;
  onOpenActivity: () => void;
  onOpenAttention: () => void;
  onOpenDiagnostics: () => void;
  onOpenHelpLegal: () => void;
  onOpenAutomation: () => void;
  onOpenPrivacy: () => void;
  port: LiveAppPort;
}) {
  const { language, t } = useAppLocalization();
  const loadAccount = useCallback(() => port.getAccount(), [port]);
  const loadReadiness = useCallback(() => port.getReadiness(), [port]);
  const loadInventory = useCallback(() => port.getInventory(), [port]);
  const account = useLiveProjection(loadAccount, port, ['account']);
  const readiness = useLiveProjection(loadReadiness, port, ['readiness']);
  const inventory = useLiveProjection(loadInventory, port, ['privacy']);
  const [actionPending, setActionPending] = useState<string | undefined>();
  const [actionProblem, setActionProblem] = useState<
    NativeProblem | undefined
  >();
  const [actionMessage, setActionMessage] = useState<string | undefined>();

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

  const issues = useMemo(
    () =>
      readiness.state.kind === 'ready'
        ? uniqueIssues(readiness.state.result.envelope.value)
        : [],
    [readiness.state],
  );

  const performRepair = async (issue: ReadinessIssue) => {
    if (!issue.action || readiness.state.kind !== 'ready') {
      return;
    }
    setActionPending(issue.id);
    setActionProblem(undefined);
    setActionMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['performAction']>>;
    try {
      result = await port.performAction({
        handle: issue.action.handle,
        expectedRevision: readiness.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await readiness.reload();
      }
      setActionProblem(result.problem);
      setActionPending(undefined);
      return;
    }
    await readiness.reload();
    setActionMessage(
      result.envelope.value.kind === 'opened'
        ? t('live.attention.opened')
        : t('live.attention.cancelled'),
    );
    setActionPending(undefined);
  };

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
        <SectionHeading title={t('live.settings.platform')} />
        <StatusRow
          title={
            capability.platform === 'android'
              ? t('live.common.androidEdition')
              : t('live.common.iosEdition')
          }
          detail={
            capability.platform === 'android'
              ? t('live.settings.androidBody')
              : t('live.settings.iosBody')
          }
          tone="info"
        />
      </Card>

      <Card>
        <StatusRow
          title={t('live.settings.phoneAppearance')}
          detail={t('live.settings.phoneAppearanceBody')}
          tone="info"
        />
        <StatusRow
          title={t('live.settings.phoneLanguage')}
          detail={t('live.settings.phoneLanguageBody')}
          tone="info"
        />
      </Card>

      <SectionHeading title={t('live.settings.account')} />
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
      {account.state.kind === 'ready' ? (
        account.state.result.envelope.value.kind === 'connected' &&
        account.state.result.envelope.value.sender.platform !==
          capability.platform ? (
          <LiveError
            title={t('live.settings.accountMismatch')}
            problem={nativePlatformMismatchProblem}
            onRetry={() => account.reload()}
          />
        ) : (
          <Card>
            {account.state.refreshProblem ? (
              <LiveRefreshProblem problem={account.state.refreshProblem} />
            ) : null}
            <StatusRow
              title={t('live.settings.googleAccount')}
              detail={accountLabel(account.state.result.envelope.value)}
              tone={
                account.state.result.envelope.value.kind === 'connected'
                  ? 'positive'
                  : 'warning'
              }
            />
          </Card>
        )
      ) : null}

      {capability.platform === 'android' ? (
        <LiveAndroidDeviceControls
          account={
            account.state.kind === 'ready'
              ? account.state.result.envelope
              : undefined
          }
          onAccountReload={account.reload}
          onOpenAutomation={onOpenAutomation}
          port={port}
        />
      ) : null}

      <SectionHeading title={t('live.settings.readiness')} />
      {readiness.state.kind === 'loading' ? (
        <LiveLoading label={t('live.settings.readinessLoading')} />
      ) : null}
      {readiness.state.kind === 'error' ? (
        <LiveError
          title={t('live.settings.readinessUnavailable')}
          problem={readiness.state.problem}
          onRetry={() => readiness.reload()}
        />
      ) : null}
      {readiness.state.kind === 'ready' ? (
        readiness.state.result.envelope.value.platform !==
        capability.platform ? (
          <LiveError
            title={t('live.settings.readinessMismatch')}
            problem={nativePlatformMismatchProblem}
            onRetry={() => readiness.reload()}
          />
        ) : (
          <>
            {readiness.state.refreshProblem ? (
              <LiveRefreshProblem problem={readiness.state.refreshProblem} />
            ) : null}
            <LiveActionFeedback
              problem={actionProblem}
              message={actionMessage}
            />
            <Card>
              <StatusRow
                title={t('live.settings.currentGates')}
                detail={
                  issues.length === 0
                    ? t('live.settings.noBlockers')
                    : t('live.settings.blockerCount', {
                        count: issues.length,
                      })
                }
                tone={issues.length === 0 ? 'positive' : 'warning'}
              />
              <AppText color="muted" variant="caption">
                {t('live.common.projectionTime', {
                  time: formatLiveInstant(
                    readiness.state.result.envelope.value.lastCheckedAt,
                    language,
                  ),
                })}
              </AppText>
            </Card>
            {issues.map(issue => (
              <Card key={issue.id}>
                <StatusRow
                  title={t(safeReasonMessageKey(issue.code))}
                  detail={t('live.common.blocks', {
                    value: issue.blocks
                      .map(block => t(gateKeys[block]))
                      .join(', '),
                  })}
                  tone={issue.severity === 'blocking' ? 'critical' : 'warning'}
                />
                {issue.action ? (
                  <Button
                    label={
                      actionPending === issue.id
                        ? t('live.settings.opening')
                        : t('live.attention.openAction')
                    }
                    disabled={actionPending !== undefined}
                    onPress={() => performRepair(issue)}
                    variant="secondary"
                    testID={`live-readiness-action-${issue.id}`}
                  />
                ) : (
                  <AppText color="muted">{t('live.settings.noRepair')}</AppText>
                )}
                <AppText color="muted" variant="caption">
                  {t('live.common.code', { value: issue.code })}
                </AppText>
              </Card>
            ))}
          </>
        )
      ) : null}

      <SectionHeading title={t('live.settings.inventory')} />
      {inventory.state.kind === 'loading' ? (
        <LiveLoading label={t('live.settings.inventoryLoading')} />
      ) : null}
      {inventory.state.kind === 'error' ? (
        <>
          <LiveError
            title={t('live.settings.inventoryUnavailable')}
            problem={inventory.state.problem}
            onRetry={() => inventory.reload()}
          />
          <ReadinessBanner
            title={t('live.settings.privacyUnavailable')}
            detail={t('live.settings.privacyUnavailableBody')}
            tone="warning"
          />
        </>
      ) : null}
      {inventory.state.kind === 'ready' ? (
        <>
          {inventory.state.refreshProblem ? (
            <LiveRefreshProblem problem={inventory.state.refreshProblem} />
          ) : null}
          <LivePrivacyInventory
            inventory={inventory.state.result.envelope.value}
            platform={capability.platform}
          />
        </>
      ) : null}
      <Button
        label={t('live.nav.attention')}
        onPress={onOpenAttention}
        variant="secondary"
      />
      <Button
        label={t('live.nav.activity')}
        onPress={onOpenActivity}
        variant="secondary"
      />
      <Button
        label={t('live.settings.openPrivacy')}
        onPress={onOpenPrivacy}
        variant="secondary"
        testID="live-settings-privacy"
      />
      <Button
        label={t('live.settings.openDiagnostics')}
        onPress={onOpenDiagnostics}
        variant="secondary"
        testID="live-settings-diagnostics"
      />
      <Button
        label={t('live.settings.openHelpLegal')}
        onPress={onOpenHelpLegal}
        variant="secondary"
        testID="live-settings-help-legal"
      />
      <Button
        label={t('live.settings.refresh')}
        onPress={() => {
          account.reload().catch(() => undefined);
          readiness.reload().catch(() => undefined);
          inventory.reload().catch(() => undefined);
        }}
        variant="secondary"
        testID="live-settings-refresh"
      />
    </Screen>
  );
}
