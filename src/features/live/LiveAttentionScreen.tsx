import React, { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import type {
  AndroidGateName,
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
import { spacing } from '../../design-system/tokens/theme';
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
import { nativeBridgeProblem, nativeProblemReference } from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';

type AttentionCategory = 'account' | 'contacts' | 'approval' | 'platform';
type AppRepairRoute = 'people' | 'message' | 'automation' | 'settings';

const phoneStatePermissionCodes: ReadonlySet<ReadinessIssue['code']> = new Set([
  'phone-state-permission-denied',
  'phone-state-permission-permanently-denied',
]);

const automationPermissionCodes: ReadonlySet<ReadinessIssue['code']> = new Set([
  'permission-denied',
  'permission-permanently-denied',
  'sms-permission-denied',
  'sms-permission-permanently-denied',
]);

const retryOnlyIssueCodes: ReadonlySet<ReadinessIssue['code']> = new Set([
  'coordination-unavailable',
  'network-offline',
  'scheduler-delayed',
  'native-bridge-unavailable',
  'stale-revision',
  'unknown-native-value',
]);

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
    (issue.code.startsWith('phone-') &&
      !phoneStatePermissionCodes.has(issue.code)) ||
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
    issue.code === 'invalid-segment-cap' ||
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
  if (
    issue.code.startsWith('template-') ||
    issue.code === 'invalid-segment-cap'
  ) {
    return 'message';
  }
  if (
    category === 'approval' ||
    phoneStatePermissionCodes.has(issue.code) ||
    automationPermissionCodes.has(issue.code) ||
    issue.code === 'no-active-sim' ||
    issue.code === 'sim-changed' ||
    issue.code === 'sim-invalid'
  ) {
    return 'automation';
  }
  if (category === 'account' && !retryOnlyIssueCodes.has(issue.code)) {
    return 'settings';
  }
  return undefined;
};

const categoryKeys: Record<AttentionCategory, TranslationKey> = {
  account: 'live.attention.categoryAccount',
  contacts: 'live.attention.categoryContacts',
  approval: 'live.attention.categoryApproval',
  platform: 'live.attention.categoryPlatform',
};

const supportGateKeys: Record<AndroidGateName, TranslationKey> = {
  test: 'live.settings.gate.test',
  activation: 'live.settings.gate.activation',
  birthday: 'live.settings.gate.birthday',
};

const routeKeys: Record<AppRepairRoute, TranslationKey> = {
  people: 'live.attention.openPeople',
  message: 'live.attention.openMessage',
  automation: 'live.attention.openAutomation',
  settings: 'live.attention.openSettings',
};

const severityStateKeys: Record<ReadinessIssue['severity'], TranslationKey> = {
  blocking: 'live.attention.stateBlocking',
  warning: 'live.attention.stateWarning',
  info: 'live.attention.stateInfo',
};

const consequenceKeys: Record<AndroidGateName, TranslationKey> = {
  test: 'live.attention.consequenceTest',
  activation: 'live.attention.consequenceActivation',
  birthday: 'live.attention.consequenceBirthday',
};

const toneFor = (severity: ReadinessIssue['severity']) => {
  switch (severity) {
    case 'blocking':
      return 'critical' as const;
    case 'warning':
      return 'warning' as const;
    case 'info':
      return 'info' as const;
  }
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
  const [supportExpanded, setSupportExpanded] = useState(false);
  const visibleIssues =
    issues.state.kind === 'ready'
      ? issues.state.result.envelope.value
      : ([] as readonly ReadinessIssue[]);
  const loadProblem =
    issues.state.kind === 'error' ? issues.state.problem : undefined;
  const supportAvailable =
    visibleIssues.length > 0 ||
    loadProblem !== undefined ||
    problem !== undefined;
  const appRepairActions: Record<AppRepairRoute, () => void> = {
    people: onOpenPeople,
    message: onOpenMessage,
    automation: onOpenAutomation,
    settings: onOpenSettings,
  };
  const consequenceFor = (issue: ReadinessIssue) => {
    const state = t(severityStateKeys[issue.severity]);
    return issue.blocks.length > 0
      ? t('live.attention.consequence', {
          state,
          actions: issue.blocks
            .map(block =>
              t(consequenceKeys[block] ?? 'live.attention.consequenceBirthday'),
            )
            .join(', '),
        })
      : state;
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
      <LiveActionFeedback
        problem={problem}
        message={message}
        showSupportReference={false}
      />

      {issues.state.kind === 'loading' ? (
        <LiveLoading label={t('live.attention.loading')} />
      ) : null}
      {issues.state.kind === 'error' ? (
        <LiveError
          title={t('live.attention.unavailable')}
          problem={issues.state.problem}
          onRetry={() => issues.reload()}
          showSupportReference={false}
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
                <View
                  key={category}
                  style={styles.group}
                  testID={`live-attention-category-${category}`}
                >
                  <SectionHeading title={t(categoryKeys[category])} />
                  {grouped.map(issue => {
                    const repairRoute = repairRouteFor(issue);
                    return (
                      <Card
                        key={issue.id}
                        testID={`live-attention-issue-${issue.id}`}
                      >
                        <StatusRow
                          title={t(safeReasonMessageKey(issue.code))}
                          detail={consequenceFor(issue)}
                          tone={toneFor(issue.severity)}
                          testID={`live-attention-status-${issue.id}`}
                        />
                        {issue.action ? (
                          <Button
                            label={
                              pendingIssue === issue.id
                                ? t('live.attention.openingAction')
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
                          <AppText
                            color="muted"
                            testID={`live-attention-no-action-${issue.id}`}
                          >
                            {t(
                              retryOnlyIssueCodes.has(issue.code)
                                ? 'live.attention.noActionRetry'
                                : 'live.attention.noActionHelp',
                            )}
                          </AppText>
                        )}
                      </Card>
                    );
                  })}
                </View>
              );
            })
          )}
          <Button
            label={
              issues.state.refreshing
                ? t('live.attention.checkingAgain')
                : t('live.attention.checkAgain')
            }
            disabled={issues.state.refreshing || pendingIssue !== undefined}
            onPress={() => issues.reload()}
            variant="secondary"
            testID="live-attention-check-again"
          />
        </>
      ) : null}
      {supportAvailable ? (
        <>
          <Button
            label={t(
              supportExpanded
                ? 'live.attention.hideSupportDetails'
                : 'live.attention.showSupportDetails',
            )}
            onPress={() => setSupportExpanded(expanded => !expanded)}
            variant="secondary"
            testID="live-attention-support-toggle"
          />
          {supportExpanded ? (
            <View style={styles.group} testID="live-attention-support-details">
              <AppText color="muted">
                {t('live.attention.supportDetailsBody')}
              </AppText>
              {visibleIssues.map(issue => (
                <Card
                  key={issue.id}
                  testID={`live-attention-support-${issue.id}`}
                >
                  <AppText variant="label">
                    {t(safeReasonMessageKey(issue.code))}
                  </AppText>
                  <AppText color="muted" variant="caption">
                    {t('live.common.code', { value: issue.code })}
                  </AppText>
                  {issue.blocks.length > 0 ? (
                    <AppText color="muted" variant="caption">
                      {t('live.common.blocks', {
                        value: issue.blocks
                          .map(block =>
                            t(
                              supportGateKeys[block] ??
                                'live.settings.gate.birthday',
                            ),
                          )
                          .join(', '),
                      })}
                    </AppText>
                  ) : null}

                  <AppText color="muted" variant="caption">
                    {t('live.common.reference', { reference: issue.id })}
                  </AppText>
                </Card>
              ))}
              {loadProblem ? (
                <Card testID="live-attention-support-load-error">
                  <AppText variant="label">
                    {t('live.attention.unavailable')}
                  </AppText>
                  <AppText color="muted" variant="caption">
                    {t('live.common.reference', {
                      reference: nativeProblemReference(loadProblem),
                    })}
                  </AppText>
                </Card>
              ) : null}
              {problem ? (
                <Card testID="live-attention-support-action-error">
                  <AppText variant="label">
                    {t('live.error.actionTitle')}
                  </AppText>
                  <AppText color="muted" variant="caption">
                    {t('live.common.reference', {
                      reference: nativeProblemReference(problem),
                    })}
                  </AppText>
                </Card>
              ) : null}
            </View>
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  group: { gap: spacing.md },
});
