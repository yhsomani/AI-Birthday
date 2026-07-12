import React, { useCallback, useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import type {
  GeminiSuggestionsProjection,
  MessageEditorProjection,
  MessageLanguage,
  MessagePreview,
  MessageTone,
} from '../../domain/messages/model';
import { BUILT_IN_MESSAGE_TEMPLATES } from '../../domain/messages/model';
import type { NativeRevision } from '../../domain/shared/brand';
import type { NativeProblem } from '../../domain/shared/result';
import type { ProjectionEnvelope } from '../../domain/shared/result';
import { validateTemplateDraft } from '../../domain/validation/templateDraft';
import { AccessibleTextInput } from '../../design-system/components/AccessibleTextInput';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ChoiceChip,
  ReadinessBanner,
  Screen,
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
  preview: MessagePreview;
  revision: NativeRevision;
}>;

export function LiveMessageScreen({
  onBack,
  port,
}: {
  onBack: () => void;
  port: LiveAppPort;
}) {
  const { colors } = useAppTheme();
  const { t } = useAppLocalization();
  const loadEditor = useCallback(() => port.getMessageEditor(), [port]);
  const editor = useLiveProjection(loadEditor, port, ['messages']);
  const [language, setLanguage] = useState<MessageLanguage>();
  const [tone, setTone] = useState<MessageTone>();
  const [placeholderMode, setPlaceholderMode] = useState<
    'given-name' | 'generic'
  >();
  const [segmentCap, setSegmentCap] = useState<1 | 2>();
  const [text, setText] = useState('');
  const [preview, setPreview] = useState<PreviewState>();
  const [suggestions, setSuggestions] = useState<GeminiSuggestionsProjection>();
  const [pending, setPending] = useState<'preview' | 'save' | 'suggest'>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const [localIssue, setLocalIssue] = useState<string>();
  const [savedConflict, setSavedConflict] = useState(false);
  const dirtyRef = useRef(false);
  const sourceRevisionRef = useRef<NativeRevision | undefined>(undefined);

  const markDirty = () => {
    dirtyRef.current = true;
    setPreview(undefined);
  };

  const applySaved = useCallback(
    (envelope: ProjectionEnvelope<MessageEditorProjection>) => {
      const projection = envelope.value;
      if (projection.kind === 'configured') {
        setLanguage(projection.draft.language);
        setTone(projection.draft.tone);
        setPlaceholderMode(projection.draft.placeholderMode.kind);
        setSegmentCap(projection.draft.requestedSegmentCap);
        setText(projection.draft.text);
      } else {
        setLanguage(undefined);
        setTone(undefined);
        setPlaceholderMode(undefined);
        setSegmentCap(undefined);
        setText('');
      }
      sourceRevisionRef.current = envelope.revision;
      dirtyRef.current = false;
      setSavedConflict(false);
      setPreview(undefined);
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

  const draftFromFields = () => {
    if (!language || !tone || !placeholderMode || !segmentCap) {
      setLocalIssue(t('live.error.validation'));
      return undefined;
    }
    const validation = validateTemplateDraft({
      language,
      tone,
      placeholderMode,
      requestedSegmentCap: segmentCap,
      text,
    });
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
    let result: Awaited<ReturnType<LiveAppPort['previewMessage']>>;
    try {
      result = await port.previewMessage({
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
    let result: Awaited<ReturnType<LiveAppPort['saveMessage']>>;
    try {
      result = await port.saveMessage({
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
    setMessage(
      t('live.message.saved', {
        count: result.envelope.value.invalidatedApprovalCount,
      }),
    );
    if (refreshed.kind === 'ok') {
      applySaved(refreshed.envelope);
      setPreview(undefined);
    }
    setPending(undefined);
  };

  const generateSuggestions = async () => {
    if (!language || !tone || !placeholderMode || !segmentCap) {
      setLocalIssue(t('live.error.validation'));
      return;
    }
    setPending('suggest');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['generateSuggestions']>>;
    try {
      result = await port.generateSuggestions({
        language,
        tone,
        placeholderMode:
          placeholderMode === 'given-name'
            ? { kind: 'given-name', requiredCount: 1 }
            : { kind: 'generic', requiredCount: 0 },
        requestedSegmentCap: segmentCap,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else {
      setSuggestions(result.envelope.value);
    }
    setPending(undefined);
  };

  const reloadSaved = () => {
    if (editor.state.kind === 'ready') {
      applySaved(editor.state.result.envelope);
    }
  };

  const applyBuiltIn = (
    template: (typeof BUILT_IN_MESSAGE_TEMPLATES)[number],
  ) => {
    setLanguage(template.draft.language);
    setTone(template.draft.tone);
    setPlaceholderMode(template.draft.placeholderMode);
    setSegmentCap(template.draft.requestedSegmentCap as 1 | 2);
    setText(template.draft.text);
    setSuggestions(undefined);
    markDirty();
  };

  return (
    <Screen includeTopInset testID="live-message-screen">
      <Button label={t('live.common.back')} onPress={onBack} variant="ghost" />
      <AppText variant="title" accessibilityRole="header">
        {t('live.message.title')}
      </AppText>
      <AppText color="muted">{t('live.message.body')}</AppText>
      <LiveActionFeedback problem={problem} message={message} />

      {editor.state.kind === 'loading' ? (
        <LiveLoading label={t('live.message.loading')} />
      ) : null}
      {editor.state.kind === 'error' ? (
        <LiveError
          title={t('live.message.unavailable')}
          problem={editor.state.problem}
          onRetry={() => editor.reload()}
        />
      ) : null}
      {editor.state.kind === 'ready' ? (
        <>
          {editor.state.refreshProblem ? (
            <LiveRefreshProblem problem={editor.state.refreshProblem} />
          ) : null}
          {editor.state.result.envelope.value.kind === 'not-configured' ? (
            <StatusRow title={t('live.message.notConfigured')} tone="info" />
          ) : null}
          {savedConflict ? (
            <Card>
              <StatusRow
                title={t('live.message.savedChanged')}
                tone="warning"
              />
              <Button
                label={t('live.message.reloadSaved')}
                onPress={reloadSaved}
                variant="secondary"
                testID="live-message-reload-saved"
              />
            </Card>
          ) : null}

          <SectionHeading
            title={t('live.message.builtInTitle')}
            supporting={t('live.message.builtInBody')}
          />
          {BUILT_IN_MESSAGE_TEMPLATES.map(template => (
            <Card key={template.id}>
              <AppText variant="label">
                {t(
                  template.draft.language === 'hi'
                    ? 'live.message.hindi'
                    : 'live.message.english',
                )}{' '}
                ·{' '}
                {t(
                  template.draft.placeholderMode === 'given-name'
                    ? 'live.message.givenName'
                    : 'live.message.generic',
                )}
              </AppText>
              <AppText>{template.draft.text}</AppText>
              <Button
                label={t('live.message.useBuiltIn')}
                disabled={pending !== undefined}
                onPress={() => applyBuiltIn(template)}
                variant="secondary"
                testID={`live-message-built-in-${template.id}`}
              />
            </Card>
          ))}

          <SectionHeading title={t('live.message.language')} />
          <View accessibilityRole="radiogroup" style={styles.choices}>
            <ChoiceChip
              label={t('live.message.english')}
              selected={language === 'en'}
              onPress={() => {
                setLanguage('en');
                markDirty();
              }}
            />
            <ChoiceChip
              label={t('live.message.hindi')}
              selected={language === 'hi'}
              onPress={() => {
                setLanguage('hi');
                markDirty();
              }}
            />
          </View>
          <SectionHeading title={t('live.message.tone')} />
          <View accessibilityRole="radiogroup" style={styles.choices}>
            {(['warm', 'simple', 'cheerful'] as const).map(value => (
              <ChoiceChip
                key={value}
                label={t(`live.message.${value}`)}
                selected={tone === value}
                onPress={() => {
                  setTone(value);
                  markDirty();
                }}
              />
            ))}
          </View>
          <SectionHeading title={t('live.message.nameMode')} />
          <View accessibilityRole="radiogroup" style={styles.choices}>
            <ChoiceChip
              label={t('live.message.givenName')}
              selected={placeholderMode === 'given-name'}
              onPress={() => {
                setPlaceholderMode('given-name');
                markDirty();
              }}
            />
            <ChoiceChip
              label={t('live.message.generic')}
              selected={placeholderMode === 'generic'}
              onPress={() => {
                setPlaceholderMode('generic');
                markDirty();
              }}
            />
          </View>
          <SectionHeading title={t('live.message.segmentCap')} />
          <View accessibilityRole="radiogroup" style={styles.choices}>
            {[1, 2].map(value => (
              <ChoiceChip
                key={value}
                label={String(value)}
                selected={segmentCap === value}
                onPress={() => {
                  setSegmentCap(value as 1 | 2);
                  markDirty();
                }}
              />
            ))}
          </View>

          <SectionHeading title={t('live.message.text')} />
          <AccessibleTextInput
            accessibilityLabel={t('live.message.text')}
            accessibilityHint={t('live.message.textHint')}
            maxLength={1_000}
            multiline
            onChangeText={value => {
              setText(value);
              markDirty();
            }}
            style={[
              styles.input,
              {
                backgroundColor: colors.surface,
                borderColor: colors.border,
                color: colors.text,
              },
            ]}
            testID="live-message-input"
            value={text}
          />
          {localIssue ? (
            <LiveValidationError
              message={localIssue}
              testID="live-message-validation"
            />
          ) : null}
          <ReadinessBanner
            title={t('live.message.geminiPrivacyTitle')}
            detail={t('live.message.geminiPrivacyBody')}
            tone="info"
            testID="live-message-gemini-privacy"
          />
          <Button
            label={
              pending === 'suggest'
                ? t('live.message.suggesting')
                : t('live.message.suggest')
            }
            disabled={pending !== undefined}
            onPress={generateSuggestions}
            variant="secondary"
            testID="live-message-suggest"
          />
          {suggestions?.kind === 'candidates'
            ? suggestions.candidates.map((candidate, index) => (
                <Card key={index}>
                  <AppText>{candidate}</AppText>
                  <Button
                    label={t('live.message.useSuggestion')}
                    onPress={() => {
                      setText(candidate);
                      markDirty();
                    }}
                    variant="secondary"
                    testID={`live-message-suggestion-${index}`}
                  />
                </Card>
              ))
            : null}
          {suggestions?.kind === 'fallback' ||
          suggestions?.kind === 'failed' ? (
            <StatusRow
              title={t('live.message.suggestionUnavailable', {
                reason: t(safeReasonMessageKey(suggestions.reason)),
              })}
              tone="warning"
            />
          ) : null}
          <Button
            label={
              pending === 'preview'
                ? t('live.message.previewing')
                : t('live.message.preview')
            }
            disabled={pending !== undefined}
            onPress={preparePreview}
            testID="live-message-preview"
          />

          {preview?.preview.kind === 'invalid' ? (
            <Card>
              <AppText variant="heading">{t('live.message.invalid')}</AppText>
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
              <AppText variant="heading">
                {t('live.message.previewTitle')}
              </AppText>
              {preview.preview.examples.map((example, index) => (
                <View
                  key={`${index}-${example.displayName}`}
                  style={styles.example}
                >
                  <AppText variant="label">
                    {t('live.message.example', {
                      name: example.displayName,
                      segments: example.segmentCount,
                      encoding: example.encodingLabel,
                    })}
                  </AppText>
                  <AppText>{example.finalText}</AppText>
                </View>
              ))}
              <AppText color="muted">
                {t('live.message.affected', {
                  count: preview.preview.affectedRecipientCount,
                })}
              </AppText>
              <Button
                label={
                  pending === 'save'
                    ? t('live.message.saving')
                    : t('live.message.save')
                }
                disabled={pending !== undefined}
                onPress={savePreview}
                testID="live-message-save"
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
    </Screen>
  );
}

const styles = StyleSheet.create({
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  example: { gap: spacing.xs, paddingVertical: spacing.sm },
  input: {
    borderRadius: radii.md,
    borderWidth: 1,
    fontSize: 17,
    minHeight: minimumTargetSize * 3,
    padding: spacing.md,
    textAlignVertical: 'top',
  },
});
