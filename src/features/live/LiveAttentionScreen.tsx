import React, { useCallback, useState } from 'react';

import type {
  AndroidGateName,
  IosGateName,
  ReadinessIssue,
} from '../../domain/readiness/model';
import type { NativeProblem } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  Screen,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { TranslationKey } from '../../localization/resources';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
} from './LiveProjectionState';
import { nativeBridgeProblem } from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';

const gateKeys: Record<AndroidGateName | IosGateName, TranslationKey> = {
  test: 'live.settings.gate.test',
  activation: 'live.settings.gate.activation',
  birthday: 'live.settings.gate.birthday',
  composer: 'live.settings.gate.composer',
};

type AttentionCategory = 'account' | 'contacts' | 'approval' | 'platform';
type AppRepairRoute = 'people' | 'message' | 'automation' | 'settings';

const categoryFor = (issue: ReadinessIssue): AttentionCategory => {
  if (
    issue.code.startsWith('account-') ||
    issue.code === 'active-sender-other-device' ||
    issue.code === 'transfer-pending' ||
    issue.code === 'firebase-account-deleting' ||
    issue.code === 'coordination-unavailable'
  ) {
    return 'account';
  }
  if (
    issue.code.startsWith('contacts-') ||
    issue.code.startsWith('birthday-') ||
    issue.code.startsWith('phone-') ||
    issue.code === 'duplicate-destination' ||
    issue.code === 'leap-policy-required' ||
    issue.code === 'safe-given-name-missing' ||
    issue.code === 'source-contact-deleted' ||
    issue.code === 'stable-source-missing'
  ) {
    return 'contacts';
  }
  if (
    issue.code.startsWith('approval-') ||
    issue.code.startsWith('template-') ||
    issue.code === 'invalid-window' ||
    issue.code === 'invalid-daily-cap' ||
    issue.code === 'window-capacity-conflict'
  ) {
    return 'approval';
  }
  return 'platform';
};

const repairRouteFor = (issue: ReadinessIssue): AppRepairRoute | undefined => {
  const category = categoryFor(issue);
  if (category === 'contacts' || issue.code.startsWith('approval-')) {
    return 'people';
  }
  if (issue.code.startsWith('template-')) return 'message';
  if (category === 'approval' || issue.code.includes('sim')) {
    return 'automation';
  }
  if (category === 'account') return 'settings';
  return undefined;
};

const categoryKeys: Record<AttentionCategory, TranslationKey> = {
  account: 'live.attention.categoryAccount',
  contacts: 'live.attention.categoryContacts',
  approval: 'live.attention.categoryApproval',
  platform: 'live.attention.categoryPlatform',
};

const routeKeys: Record<AppRepairRoute, TranslationKey> = {
  people: 'live.attention.openPeople',
  message: 'live.attention.openMessage',
  automation: 'live.attention.openAutomation',
  settings: 'live.attention.openSettings',
};

export function LiveAttentionScreen({
  onBack,
  onOpenAutomation,
  onOpenMessage,
  onOpenPeople,
  onOpenSettings,
  port,
}: {
  onBack: () => void;
  onOpenAutomation: () => void;
  onOpenMessage: () => void;
  onOpenPeople: () => void;
  onOpenSettings: () => void;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const loadIssues = useCallback(() => port.listIssues(), [port]);
  const issues = useLiveProjection(loadIssues, port, ['readiness', 'activity']);
  const [pendingIssue, setPendingIssue] = useState<string>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const visibleIssues =
    issues.state.kind === 'ready'
      ? issues.state.result.envelope.value
      : ([] as readonly ReadinessIssue[]);
  const appRepairActions: Record<AppRepairRoute, () => void> = {
    people: onOpenPeople,
    message: onOpenMessage,
    automation: onOpenAutomation,
    settings: onOpenSettings,
  };

  const performIssueAction = async (issue: ReadinessIssue) => {
    if (!issue.action || issues.state.kind !== 'ready') {
      return;
    }
    const revision = issues.state.result.envelope.revision;
    setPendingIssue(issue.id);
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['performAction']>>;
    try {
      result = await port.performAction({
        handle: issue.action.handle,
        expectedRevision: revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await issues.reload();
      }
      setProblem(result.problem);
      setPendingIssue(undefined);
      return;
    }
    await issues.reload();
    setMessage(
      result.envelope.value.kind === 'opened'
        ? t('live.attention.opened')
        : t('live.attention.cancelled'),
    );
    setPendingIssue(undefined);
  };

  return (
    <Screen includeTopInset testID="live-attention-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-attention-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.attention.title')}
      </AppText>
      <AppText color="muted">{t('live.attention.body')}</AppText>
      <LiveActionFeedback problem={problem} message={message} />

      {issues.state.kind === 'loading' ? (
        <LiveLoading label={t('live.attention.loading')} />
      ) : null}
      {issues.state.kind === 'error' ? (
        <LiveError
          title={t('live.attention.unavailable')}
          problem={issues.state.problem}
          onRetry={() => issues.reload()}
        />
      ) : null}
      {issues.state.kind === 'ready' ? (
        <>
          {issues.state.refreshProblem ? (
            <LiveRefreshProblem problem={issues.state.refreshProblem} />
          ) : null}
          {visibleIssues.length === 0 ? (
            <Card>
              <AppText>{t('live.attention.empty')}</AppText>
            </Card>
          ) : (
            (Object.keys(categoryKeys) as AttentionCategory[]).map(category => {
              const grouped = visibleIssues.filter(
                issue => categoryFor(issue) === category,
              );
              if (grouped.length === 0) return null;
              return (
                <React.Fragment key={category}>
                  <SectionHeading title={t(categoryKeys[category])} />
                  {grouped.map(issue => {
                    const repairRoute = repairRouteFor(issue);
                    return (
                      <Card key={issue.id}>
                        <StatusRow
                          title={t(safeReasonMessageKey(issue.code))}
                          detail={t('live.common.blocks', {
                            value: issue.blocks
                              .map(block => t(gateKeys[block]))
                              .join(', '),
                          })}
                          tone={
                            issue.severity === 'blocking'
                              ? 'critical'
                              : 'warning'
                          }
                        />
                        <AppText color="muted" variant="caption">
                          {t('live.common.code', { value: issue.code })}
                        </AppText>
                        {issue.action ? (
                          <Button
                            label={
                              pendingIssue === issue.id
                                ? t('live.settings.opening')
                                : t('live.attention.openAction')
                            }
                            disabled={pendingIssue !== undefined}
                            onPress={() => performIssueAction(issue)}
                            variant="secondary"
                            testID={`live-attention-action-${issue.id}`}
                          />
                        ) : repairRoute ? (
                          <Button
                            label={t(routeKeys[repairRoute])}
                            disabled={pendingIssue !== undefined}
                            onPress={appRepairActions[repairRoute]}
                            variant="secondary"
                            testID={`live-attention-route-${issue.id}`}
                          />
                        ) : (
                          <AppText color="muted">
                            {t('live.attention.noAction')}
                          </AppText>
                        )}
                      </Card>
                    );
                  })}
                </React.Fragment>
              );
            })
          )}
          <Button
            label={
              issues.state.refreshing
                ? t('live.common.refreshing')
                : t('live.common.refresh')
            }
            disabled={issues.state.refreshing || pendingIssue !== undefined}
            onPress={() => issues.reload()}
            variant="secondary"
          />
        </>
      ) : null}
    </Screen>
  );
}
