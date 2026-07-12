import React, { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import type {
  PolicyEditorProjection,
  PolicyPreview,
  WindowDraftInput,
} from '../../domain/birthdays/model';
import type { NativeRevision } from '../../domain/shared/brand';
import type {
  NativeProblem,
  ProjectionEnvelope,
} from '../../domain/shared/result';
import type { PlatformCapability } from '../../domain/shared/platform';
import { validateWindowDraft } from '../../domain/validation/windowDraft';
import { AccessibleTextInput } from '../../design-system/components/AccessibleTextInput';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ChoiceChip,
  KeyValue,
  ReadinessBanner,
  SectionHeading,
  StatusRow,
} from '../../design-system/components/Primitives';
import {
  minimumTargetSize,
  radii,
  spacing,
} from '../../design-system/tokens/theme';
import { useAppTheme } from '../../app/providers/ThemeProvider';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { safeReasonMessageKey } from '../../localization/reasonCopy';
import type { LiveAppPort } from './LiveAppPort';
import {
  LiveActionFeedback,
  LiveError,
  LiveLoading,
  LiveRefreshProblem,
  LiveValidationError,
} from './LiveProjectionState';
import { nativeBridgeProblem } from './nativeProblem';
import { useLiveProjection } from './useLiveProjection';

type PreviewState = Readonly<{
  preview: PolicyPreview;
  revision: NativeRevision;
}>;

const defaultFields: WindowDraftInput = {
  primaryStart: '09:00',
  primaryEnd: '11:00',
  dailyCap: 10,
};

export function LivePolicyEditor({
  platform,
  port,
}: {
  platform: PlatformCapability['platform'];
  port: LiveAppPort;
}) {
  const { colors } = useAppTheme();
  const { t } = useAppLocalization();
  const loadEditor = useCallback(() => port.getPolicyEditor(), [port]);
  const editor = useLiveProjection(loadEditor, port, ['automation']);
  const [primaryStart, setPrimaryStart] = useState(defaultFields.primaryStart);
  const [primaryEnd, setPrimaryEnd] = useState(defaultFields.primaryEnd);
  const [graceMode, setGraceMode] = useState<'none' | 'same-day-grace'>('none');
  const [graceEnd, setGraceEnd] = useState('');
  const [dailyCap, setDailyCap] = useState(String(defaultFields.dailyCap));
  const [preview, setPreview] = useState<PreviewState>();
  const [pending, setPending] = useState<'preview' | 'save'>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const [localIssue, setLocalIssue] = useState<string>();
  const [savedConflict, setSavedConflict] = useState(false);
  const sourceRevisionRef = useRef<NativeRevision | undefined>(undefined);
  const dirtyRef = useRef(false);

  const applySaved = useCallback(
    (envelope: ProjectionEnvelope<PolicyEditorProjection>) => {
      if (envelope.value.kind === 'configured') {
        setPrimaryStart(envelope.value.draft.primaryStart);
        setPrimaryEnd(envelope.value.draft.primaryEnd);
        setDailyCap(String(envelope.value.draft.dailyCap));
        if (envelope.value.draft.latePolicy.kind === 'same-day-grace') {
          setGraceMode('same-day-grace');
          setGraceEnd(envelope.value.draft.latePolicy.graceEnd);
        } else {
          setGraceMode('none');
          setGraceEnd('');
        }
      } else {
        setPrimaryStart(defaultFields.primaryStart);
        setPrimaryEnd(defaultFields.primaryEnd);
        setDailyCap(String(defaultFields.dailyCap));
        setGraceMode('none');
        setGraceEnd('');
      }
      sourceRevisionRef.current = envelope.revision;
      dirtyRef.current = false;
      setSavedConflict(false);
      setPreview(undefined);
      setLocalIssue(undefined);
    },
    [],
  );

  useEffect(() => {
    if (editor.state.kind !== 'ready') {
      return;
    }
    const envelope = editor.state.result.envelope;
    if (sourceRevisionRef.current === envelope.revision) {
      return;
    }
    if (sourceRevisionRef.current === undefined || !dirtyRef.current) {
      applySaved(envelope);
    } else {
      setSavedConflict(true);
    }
  }, [applySaved, editor.state]);

  const markDirty = () => {
    dirtyRef.current = true;
    setPreview(undefined);
    setMessage(undefined);
  };

  const draftFromFields = () => {
    const parsedCap = Number(dailyCap);
    const input: WindowDraftInput = {
      primaryStart,
      primaryEnd,
      dailyCap: parsedCap,
      ...(graceMode === 'same-day-grace' ? { graceEnd } : {}),
    };
    const validation = validateWindowDraft(input);
    if (validation.kind === 'invalid') {
      setLocalIssue(
        validation.issues
          .map(issue => t(safeReasonMessageKey(issue.code)))
          .join('\n'),
      );
      return undefined;
    }
    setLocalIssue(undefined);
    return validation.value;
  };

  const preparePreview = async () => {
    if (editor.state.kind !== 'ready') {
      return;
    }
    const draft = draftFromFields();
    if (!draft) {
      return;
    }
    setPending('preview');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['previewPolicy']>>;
    try {
      result = await port.previewPolicy({
        draft,
        expectedRevision: editor.state.result.envelope.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await editor.reload();
      }
      setProblem(result.problem);
      setPending(undefined);
      return;
    }
    setPreview({
      preview: result.envelope.value,
      revision: result.envelope.revision,
    });
    setPending(undefined);
  };

  const savePreview = async () => {
    if (!preview || preview.preview.kind !== 'valid') {
      return;
    }
    setPending('save');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['savePolicy']>>;
    try {
      result = await port.savePolicy({
        handle: preview.preview.handle,
        expectedRevision: preview.revision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      if (result.problem.kind === 'stale-revision') {
        await editor.reload();
        setPreview(undefined);
      }
      setProblem(result.problem);
      setPending(undefined);
      return;
    }
    dirtyRef.current = false;
    const refreshed = await editor.reload();
    if (refreshed.kind === 'ok') {
      applySaved(refreshed.envelope);
    }
    setMessage(t('live.policy.saved'));
    setPending(undefined);
  };

  return (
    <View style={styles.stack} testID="live-policy-editor">
      <SectionHeading
        title={t('live.policy.title')}
        supporting={t(
          platform === 'android'
            ? 'live.policy.androidBody'
            : 'live.policy.iosBody',
        )}
      />
      <LiveActionFeedback problem={problem} message={message} />
      {editor.state.kind === 'loading' ? (
        <LiveLoading label={t('live.policy.loading')} />
      ) : null}
      {editor.state.kind === 'error' ? (
        <LiveError
          title={t('live.policy.unavailable')}
          problem={editor.state.problem}
          onRetry={() => editor.reload()}
        />
      ) : null}
      {editor.state.kind === 'ready' ? (
        <>
          {editor.state.refreshProblem ? (
            <LiveRefreshProblem problem={editor.state.refreshProblem} />
          ) : null}
          {savedConflict ? (
            <Card>
              <StatusRow title={t('live.policy.savedChanged')} tone="warning" />
              <Button
                label={t('live.policy.reloadSaved')}
                onPress={() => {
                  if (editor.state.kind === 'ready') {
                    applySaved(editor.state.result.envelope);
                  }
                }}
                variant="secondary"
                testID="live-policy-reload-saved"
              />
            </Card>
          ) : null}

          <SectionHeading title={t('live.policy.stepOne')} />
          <Card>
            <AppText variant="label">{t('live.policy.primaryWindow')}</AppText>
            <View style={styles.row}>
              <AccessibleTextInput
                accessibilityLabel={t('live.policy.start')}
                maxLength={5}
                onChangeText={value => {
                  setPrimaryStart(value);
                  markDirty();
                }}
                placeholder="09:00"
                placeholderTextColor={colors.textMuted}
                style={[
                  styles.input,
                  {
                    backgroundColor: colors.surface,
                    borderColor: colors.border,
                    color: colors.text,
                  },
                ]}
                testID="live-policy-start"
                value={primaryStart}
              />
              <AccessibleTextInput
                accessibilityLabel={t('live.policy.end')}
                maxLength={5}
                onChangeText={value => {
                  setPrimaryEnd(value);
                  markDirty();
                }}
                placeholder="11:00"
                placeholderTextColor={colors.textMuted}
                style={[
                  styles.input,
                  {
                    backgroundColor: colors.surface,
                    borderColor: colors.border,
                    color: colors.text,
                  },
                ]}
                testID="live-policy-end"
                value={primaryEnd}
              />
            </View>
            <AppText color="muted" variant="caption">
              {t('live.policy.timeFormat')}
            </AppText>

            <AppText variant="label">{t('live.policy.latePolicy')}</AppText>
            <View accessibilityRole="radiogroup" style={styles.row}>
              <ChoiceChip
                label={t(
                  platform === 'android'
                    ? 'live.policy.noGrace'
                    : 'live.policy.noGraceIos',
                )}
                selected={graceMode === 'none'}
                onPress={() => {
                  setGraceMode('none');
                  setGraceEnd('');
                  markDirty();
                }}
                testID="live-policy-no-grace"
              />
              <ChoiceChip
                label={t('live.policy.sameDayGrace')}
                selected={graceMode === 'same-day-grace'}
                onPress={() => {
                  setGraceMode('same-day-grace');
                  markDirty();
                }}
                testID="live-policy-grace"
              />
            </View>
            {graceMode === 'same-day-grace' ? (
              <AccessibleTextInput
                accessibilityLabel={t('live.policy.graceEnd')}
                maxLength={5}
                onChangeText={value => {
                  setGraceEnd(value);
                  markDirty();
                }}
                placeholder="12:00"
                placeholderTextColor={colors.textMuted}
                style={[
                  styles.input,
                  {
                    backgroundColor: colors.surface,
                    borderColor: colors.border,
                    color: colors.text,
                  },
                ]}
                testID="live-policy-grace-end"
                value={graceEnd}
              />
            ) : null}

            {platform === 'android' ? (
              <>
                <AppText variant="label">{t('live.policy.dailyCap')}</AppText>
                <AccessibleTextInput
                  accessibilityLabel={t('live.policy.dailyCap')}
                  keyboardType="number-pad"
                  maxLength={2}
                  onChangeText={value => {
                    setDailyCap(value);
                    markDirty();
                  }}
                  placeholder="10"
                  placeholderTextColor={colors.textMuted}
                  style={[
                    styles.input,
                    {
                      backgroundColor: colors.surface,
                      borderColor: colors.border,
                      color: colors.text,
                    },
                  ]}
                  testID="live-policy-daily-cap"
                  value={dailyCap}
                />
              </>
            ) : (
              <ReadinessBanner
                title={t('live.policy.iosReminderPolicy')}
                detail={t('live.policy.iosReminderPolicyBody')}
                tone="info"
              />
            )}
          </Card>
          {localIssue ? (
            <LiveValidationError
              message={localIssue}
              testID="live-policy-validation"
            />
          ) : null}
          <Button
            label={
              pending === 'preview'
                ? t('live.policy.previewing')
                : t(
                    platform === 'android'
                      ? 'live.policy.preview'
                      : 'live.policy.previewIos',
                  )
            }
            disabled={pending !== undefined}
            onPress={preparePreview}
            testID="live-policy-preview"
          />

          {preview?.preview.kind === 'invalid' ? (
            <Card>
              <AppText variant="heading">{t('live.policy.invalid')}</AppText>
              {preview.preview.issues.map(issue => (
                <StatusRow
                  key={`${issue.field}-${issue.code}`}
                  title={t(safeReasonMessageKey(issue.code))}
                  tone="warning"
                />
              ))}
            </Card>
          ) : null}
          {preview?.preview.kind === 'valid' ? (
            <Card>
              <SectionHeading title={t('live.policy.stepTwo')} />
              {platform === 'android' ? (
                <>
                  <KeyValue
                    label={t('live.policy.summary')}
                    value={preview.preview.summary}
                  />
                  <KeyValue
                    label={t('live.policy.maximumDaily')}
                    value={String(preview.preview.maximumPlannedInLocalDay)}
                  />
                  <KeyValue
                    label={t('live.policy.maximumRolling')}
                    value={String(
                      preview.preview.maximumPlannedInRolling24Hours,
                    )}
                  />
                  <AppText color="muted">
                    {t('live.policy.simulatedDays', {
                      count: preview.preview.simulatedDays,
                    })}
                  </AppText>
                </>
              ) : (
                <ReadinessBanner
                  title={t('live.policy.iosHorizonTitle')}
                  detail={t('live.policy.iosHorizonBody', {
                    count: preview.preview.simulatedDays,
                  })}
                  tone="info"
                />
              )}
              <Button
                label={
                  pending === 'save'
                    ? t('live.policy.saving')
                    : t(
                        platform === 'android'
                          ? 'live.policy.save'
                          : 'live.policy.saveIos',
                      )
                }
                disabled={pending !== undefined}
                onPress={savePreview}
                testID="live-policy-save"
              />
              <Button
                label={t('live.common.cancel')}
                disabled={pending !== undefined}
                onPress={() => setPreview(undefined)}
                variant="secondary"
              />
            </Card>
          ) : null}
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  input: {
    borderRadius: radii.md,
    borderWidth: 1,
    flexGrow: 1,
    fontSize: 17,
    minHeight: minimumTargetSize,
    minWidth: 112,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  stack: { gap: spacing.md },
});
