import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import type { DiagnosticsPreview } from '../../domain/activity/model';
import { SAFE_REASON_CODES } from '../../domain/shared/reasonCodes';
import type { NativeRevision } from '../../domain/shared/brand';
import type { NativeProblem } from '../../domain/shared/result';
import { isUtcInstant } from '../../domain/shared/temporal';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  KeyValue,
  Screen,
} from '../../design-system/components/Primitives';
import { formatLiveInstant } from '../../localization/formatLive';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import type { LiveAppPort } from './LiveAppPort';
import { LiveActionFeedback } from './LiveProjectionState';
import { nativeBridgeProblem, nativeContractProblem } from './nativeProblem';

type PreviewState = Readonly<{
  generation: number;
  preview: DiagnosticsPreview;
  revision: NativeRevision;
}>;

const safeReasonCodes = new Set<string>(SAFE_REASON_CODES);

const isOptionalUtcInstant = (value: unknown): boolean =>
  value === undefined || (typeof value === 'string' && isUtcInstant(value));

const isValidPrivatePreview = (
  preview: unknown,
): preview is DiagnosticsPreview => {
  if (typeof preview !== 'object' || preview === null) return false;
  const candidate = preview as Record<string, unknown>;
  return (
    candidate.excludesPrivateContent === true &&
    typeof candidate.buildLabel === 'string' &&
    candidate.buildLabel.trim().length > 0 &&
    typeof candidate.androidOrIosVersionLabel === 'string' &&
    candidate.androidOrIosVersionLabel.trim().length > 0 &&
    Number.isSafeInteger(candidate.transitionCount) &&
    (candidate.transitionCount as number) >= 0 &&
    Array.isArray(candidate.capabilityCodes) &&
    candidate.capabilityCodes.every(
      code => typeof code === 'string' && safeReasonCodes.has(code),
    ) &&
    isOptionalUtcInstant(candidate.schedulerHeartbeatAt) &&
    isOptionalUtcInstant(candidate.earliestEventAt) &&
    isOptionalUtcInstant(candidate.latestEventAt)
  );
};

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
  const mountedRef = useRef(true);
  const generationRef = useRef(0);
  const requestSequenceRef = useRef(0);
  const requestPendingRef = useRef(false);
  const previewRef = useRef<PreviewState | undefined>(undefined);
  previewRef.current = preview;

  const retireProtectedState = useCallback(() => {
    generationRef.current += 1;
    requestSequenceRef.current += 1;
    requestPendingRef.current = false;
    previewRef.current = undefined;
    if (!mountedRef.current) return;
    setPreview(undefined);
    setPending(undefined);
    setProblem(undefined);
    setMessage(undefined);
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      generationRef.current += 1;
      requestSequenceRef.current += 1;
      requestPendingRef.current = false;
      previewRef.current = undefined;
    };
  }, []);

  useEffect(
    () =>
      port.subscribeInvalidations(() => {
        retireProtectedState();
      }),
    [port, retireProtectedState],
  );

  useEffect(() => {
    const subscription = AppState.addEventListener('change', () => {
      retireProtectedState();
    });
    return () => subscription.remove();
  }, [retireProtectedState]);

  const isRequestCurrent = (generation: number, request: number): boolean =>
    mountedRef.current &&
    generation === generationRef.current &&
    request === requestSequenceRef.current &&
    requestPendingRef.current;

  const prepare = async () => {
    if (requestPendingRef.current) return;
    const generation = generationRef.current + 1;
    generationRef.current = generation;
    const request = requestSequenceRef.current + 1;
    requestSequenceRef.current = request;
    requestPendingRef.current = true;
    previewRef.current = undefined;
    setPreview(undefined);
    setPending('preview');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['previewDiagnostics']>>;
    try {
      result = await port.previewDiagnostics();
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (!isRequestCurrent(generation, request)) return;
    requestPendingRef.current = false;
    setPending(undefined);
    if (result.kind === 'error') {
      setProblem(result.problem);
      return;
    }
    if (!isValidPrivatePreview(result.envelope.value)) {
      setProblem(nativeContractProblem);
      return;
    }
    const nextPreview: PreviewState = {
      generation,
      preview: result.envelope.value,
      revision: result.envelope.revision,
    };
    previewRef.current = nextPreview;
    setPreview(nextPreview);
  };

  const share = async () => {
    const reviewedPreview = previewRef.current;
    if (
      !reviewedPreview ||
      reviewedPreview.generation !== generationRef.current ||
      requestPendingRef.current
    )
      return;
    const request = requestSequenceRef.current + 1;
    requestSequenceRef.current = request;
    requestPendingRef.current = true;
    setPending('share');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['shareDiagnostics']>>;
    try {
      result = await port.shareDiagnostics({
        expectedRevision: reviewedPreview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !isRequestCurrent(reviewedPreview.generation, request) ||
      previewRef.current !== reviewedPreview
    )
      return;
    requestPendingRef.current = false;
    setPending(undefined);
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        const staleProblem = result.problem;
        retireProtectedState();
        if (mountedRef.current) setProblem(staleProblem);
      } else {
        setProblem(result.problem);
      }
      return;
    }
    setMessage(
      t(
        result.envelope.value.kind === 'shared'
          ? 'live.diagnostics.shared'
          : 'live.diagnostics.cancelled',
      ),
    );
  };

  const handleBack = () => {
    retireProtectedState();
    onBack();
  };

  return (
    <Screen includeTopInset testID="live-diagnostics-screen">
      <Button
        label={t('live.common.back')}
        onPress={handleBack}
        variant="ghost"
        testID="live-diagnostics-back"
      />
      <AppText variant="title" accessibilityRole="header">
        {t('live.diagnostics.title')}
      </AppText>
      <AppText color="muted">{t('live.diagnostics.body')}</AppText>
      <LiveActionFeedback
        problem={problem}
        message={message}
        showSupportReference
      />
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
        <Card testID="live-diagnostics-review">
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
            disabled={pending !== undefined}
            onPress={retireProtectedState}
            variant="secondary"
            testID="live-diagnostics-close-review"
          />
        </Card>
      )}
    </Screen>
  );
}
