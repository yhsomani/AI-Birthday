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

export function LiveAttentionScreen({
  onBack,
  port,
}: {
  onBack: () => void;
  port: LiveAppPort;
}) {
  const { t } = useAppLocalization();
  const loadIssues = useCallback(() => port.listIssues(), [port]);
  const issues = useLiveProjection(loadIssues, port, ['readiness', 'activity']);
  const [pendingIssue, setPendingIssue] = useState<string>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();

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
      <Button label={t('live.common.back')} onPress={onBack} variant="ghost" />
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
          {issues.state.result.envelope.value.length === 0 ? (
            <Card>
              <AppText>{t('live.attention.empty')}</AppText>
            </Card>
          ) : (
            issues.state.result.envelope.value.map(issue => (
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
                ) : (
                  <AppText color="muted">
                    {t('live.attention.noAction')}
                  </AppText>
                )}
              </Card>
            ))
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
