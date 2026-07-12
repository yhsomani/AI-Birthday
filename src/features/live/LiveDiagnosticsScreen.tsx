import React, { useState } from 'react';

import type { DiagnosticsPreview } from '../../domain/activity/model';
import type { NativeProblem } from '../../domain/shared/result';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  KeyValue,
  ReadinessBanner,
  Screen,
} from '../../design-system/components/Primitives';
import { formatLiveInstant } from '../../localization/formatLive';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { LiveAppPort } from './LiveAppPort';
import { LiveActionFeedback } from './LiveProjectionState';
import { nativeBridgeProblem } from './nativeProblem';

type PreviewState = Readonly<{
  preview: DiagnosticsPreview;
  revision: import('../../domain/shared/brand').NativeRevision;
}>;

export function LiveDiagnosticsScreen({
  onBack,
  port,
}: {
  onBack: () => void;
  port: LiveAppPort;
}) {
  const { language, t } = useAppLocalization();
  const [preview, setPreview] = useState<PreviewState>();
  const [pending, setPending] = useState<'preview' | 'share'>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();

  const prepare = async () => {
    setPending('preview');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['previewDiagnostics']>>;
    try {
      result = await port.previewDiagnostics();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else {
      setPreview({
        preview: result.envelope.value,
        revision: result.envelope.revision,
      });
    }
    setPending(undefined);
  };

  const share = async () => {
    if (!preview) {
      return;
    }
    setPending('share');
    setProblem(undefined);
    let result: Awaited<ReturnType<LiveAppPort['shareDiagnostics']>>;
    try {
      result = await port.shareDiagnostics({
        expectedRevision: preview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
      if (result.problem.kind === 'stale-revision') {
        setPreview(undefined);
      }
    } else {
      setMessage(
        t(
          result.envelope.value.kind === 'shared'
            ? 'live.diagnostics.shared'
            : 'live.diagnostics.cancelled',
        ),
      );
    }
    setPending(undefined);
  };

  return (
    <Screen includeTopInset testID="live-diagnostics-screen">
      <Button label={t('live.common.back')} onPress={onBack} variant="ghost" />
      <AppText variant="title" accessibilityRole="header">
        {t('live.diagnostics.title')}
      </AppText>
      <AppText color="muted">{t('live.diagnostics.body')}</AppText>
      <LiveActionFeedback problem={problem} message={message} />
      {!preview ? (
        <Button
          label={
            pending === 'preview'
              ? t('live.diagnostics.preparing')
              : t('live.diagnostics.preview')
          }
          disabled={pending !== undefined}
          onPress={prepare}
          testID="live-diagnostics-preview"
        />
      ) : (
        <Card>
          <ReadinessBanner
            title={t('live.diagnostics.title')}
            detail={t('live.diagnostics.body')}
            tone="info"
          />
          <KeyValue
            label={t('live.diagnostics.build')}
            value={preview.preview.buildLabel}
          />
          <KeyValue
            label={t('live.diagnostics.system')}
            value={preview.preview.androidOrIosVersionLabel}
          />
          <KeyValue
            label={t('live.diagnostics.transitions')}
            value={String(preview.preview.transitionCount)}
          />
          <KeyValue
            label={t('live.diagnostics.health')}
            value={t(
              preview.preview.capabilityCodes.length === 0
                ? 'live.diagnostics.healthClear'
                : 'live.diagnostics.healthReported',
              { count: preview.preview.capabilityCodes.length },
            )}
          />
          {preview.preview.schedulerHeartbeatAt ? (
            <KeyValue
              label={t('live.diagnostics.schedulerHeartbeat')}
              value={formatLiveInstant(
                preview.preview.schedulerHeartbeatAt,
                language,
              )}
            />
          ) : null}
          {preview.preview.earliestEventAt ? (
            <KeyValue
              label={t('live.diagnostics.earliestEvent')}
              value={formatLiveInstant(
                preview.preview.earliestEventAt,
                language,
              )}
            />
          ) : null}
          {preview.preview.latestEventAt ? (
            <KeyValue
              label={t('live.diagnostics.latestEvent')}
              value={formatLiveInstant(preview.preview.latestEventAt, language)}
            />
          ) : null}
          <KeyValue
            label={t('live.diagnostics.capabilities')}
            value={
              preview.preview.capabilityCodes.join(', ') ||
              t('live.common.none')
            }
          />
          <Button
            label={
              pending === 'share'
                ? t('live.diagnostics.sharing')
                : t('live.diagnostics.share')
            }
            disabled={pending !== undefined}
            onPress={share}
            testID="live-diagnostics-share"
          />
          <Button
            label={t('live.common.close')}
            onPress={() => setPreview(undefined)}
            variant="secondary"
          />
        </Card>
      )}
    </Screen>
  );
}
