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
import type {
  NativeProblem,
  ProjectionEnvelope,
} from '../../domain/shared/result';
import { validateTemplateDraft } from '../../domain/validation/templateDraft';
import { AccessibleTextInput } from '../../design-system/components/AccessibleTextInput';
import { AppText } from '../../design-system/components/AppText';
import {
  Button,
  Card,
  ChoiceChip,
  InlineReviewCard,
  KeyValue,
  ReadinessBanner,
  Screen,
  SectionHeading,
  SingleChoiceGroup,
  StatusRow,
} from '../../design-system/components/Primitives';
import {
  minimumTargetSize,
  radii,
  spacing,
} from '../../design-system/tokens/theme';
import { useAppTheme } from '../../app/providers/ThemeProvider';
import { useAppLocalization } from '../../localization/LocalizationProvider';
import { bidiIsolate } from '../../localization/bidi';
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

const defaultFields = {
  language: 'en',
  tone: 'warm',
  placeholderMode: 'given-name',
  segmentCap: 2,
} as const;

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
  const [language, setLanguage] = useState<MessageLanguage>(
    defaultFields.language,
  );
  const [tone, setTone] = useState<MessageTone>(defaultFields.tone);
  const [placeholderMode, setPlaceholderMode] = useState<
    'given-name' | 'generic'
  >(defaultFields.placeholderMode);
  const [segmentCap, setSegmentCap] = useState<1 | 2>(defaultFields.segmentCap);
  const [text, setText] = useState('');
  const [helpExpanded, setHelpExpanded] = useState(false);
  const [optionsExpanded, setOptionsExpanded] = useState(false);
  const [preview, setPreview] = useState<PreviewState>();
  const [suggestions, setSuggestions] = useState<GeminiSuggestionsProjection>();
  const [pending, setPending] = useState<'preview' | 'save' | 'suggest'>();
  const [problem, setProblem] = useState<NativeProblem>();
  const [message, setMessage] = useState<string>();
  const [localIssue, setLocalIssue] = useState<string>();
  const [savedConflict, setSavedConflict] = useState(false);
  const [sourceRevision, setSourceRevision] = useState<NativeRevision>();
  const dirtyRef = useRef(false);
  const sourceRevisionRef = useRef<NativeRevision | undefined>(undefined);
  const editGenerationRef = useRef(0);
  const previewRequestRef = useRef(0);
  const saveRequestRef = useRef(0);
  const suggestionRequestRef = useRef(0);
  const mountedRef = useRef(true);
  const editorTrusted =
    editor.state.kind === 'ready' &&
    !editor.state.refreshing &&
    !editor.state.refreshProblem;
  const editorActionsTrusted =
    editorTrusted &&
    editor.state.kind === 'ready' &&
    sourceRevision === editor.state.result.envelope.revision &&
    !savedConflict;

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      previewRequestRef.current += 1;
      saveRequestRef.current += 1;
      suggestionRequestRef.current += 1;
    };
  }, []);

  const applySaved = useCallback(
    (envelope: ProjectionEnvelope<MessageEditorProjection>) => {
      if (envelope.value.kind === 'configured') {
        setLanguage(envelope.value.draft.language);
        setTone(envelope.value.draft.tone);
        setPlaceholderMode(envelope.value.draft.placeholderMode.kind);
        setSegmentCap(envelope.value.draft.requestedSegmentCap);
        setText(envelope.value.draft.text);
      } else {
        setLanguage(defaultFields.language);
        setTone(defaultFields.tone);
        setPlaceholderMode(defaultFields.placeholderMode);
        setSegmentCap(defaultFields.segmentCap);
        setText('');
      }
      editGenerationRef.current += 1;
      previewRequestRef.current += 1;
      saveRequestRef.current += 1;
      suggestionRequestRef.current += 1;
      sourceRevisionRef.current = envelope.revision;
      setSourceRevision(envelope.revision);
      dirtyRef.current = false;
      setSavedConflict(false);
      setPreview(undefined);
      setSuggestions(undefined);
      setLocalIssue(undefined);
      setProblem(undefined);
    },
    [],
  );

  useEffect(() => {
    if (editor.state.kind !== 'ready' || pending === 'save') {
      return;
    }
    const envelope = editor.state.result.envelope;
    if (sourceRevision === envelope.revision) {
      return;
    }
    if (sourceRevision === undefined || !dirtyRef.current) {
      applySaved(envelope);
    } else {
      setSavedConflict(true);
    }
  }, [applySaved, editor.state, pending, sourceRevision]);

  const markDirty = () => {
    dirtyRef.current = true;
    editGenerationRef.current += 1;
    previewRequestRef.current += 1;
    saveRequestRef.current += 1;
    suggestionRequestRef.current += 1;
    setPreview(undefined);
    setSuggestions(undefined);
    setProblem(undefined);
    setMessage(undefined);
    setLocalIssue(undefined);
    setPending(current => (current === 'save' ? current : undefined));
  };

  useEffect(() => {
    if (editorActionsTrusted || pending === 'save') return;
    previewRequestRef.current += 1;
    saveRequestRef.current += 1;
    suggestionRequestRef.current += 1;
    setPreview(undefined);
    setSuggestions(undefined);
    setPending(undefined);
  }, [editorActionsTrusted, pending]);

  const draftFromFields = () => {
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
    if (
      pending !== undefined ||
      !editorActionsTrusted ||
      editor.state.kind !== 'ready'
    ) {
      return;
    }
    const draft = draftFromFields();
    if (!draft) {
      return;
    }
    const request = previewRequestRef.current + 1;
    previewRequestRef.current = request;
    const editGeneration = editGenerationRef.current;
    const expectedSourceRevision = editor.state.result.envelope.revision;
    setPending('preview');
    setProblem(undefined);
    setMessage(undefined);
    let result: Awaited<ReturnType<LiveAppPort['previewMessage']>>;
    try {
      result = await port.previewMessage({
        draft,
        expectedRevision: expectedSourceRevision,
      });
    } catch {
      result = { kind: 'error', problem: nativeBridgeProblem };
    }
    if (
      !mountedRef.current ||
      request !== previewRequestRef.current ||
      editGeneration !== editGenerationRef.current ||
      expectedSourceRevision !== sourceRevisionRef.current
    ) {
      if (mountedRef.current) {
        setPending(current => (current === 'preview' ? undefined : current));
      }
      return;
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
      setPending(undefined);
      if (result.problem.kind === 'stale-revision') {
        setPreview(undefined);
        await editor.reload();
      }
      return;
    }
    setPreview({
      preview: result.envelope.value,
      revision: result.envelope.revision,
    });
    setPending(undefined);
  };

  const savePreview = async () => {
    if (
      pending !== undefined ||
      !editorActionsTrusted ||
      editor.state.kind !== 'ready' ||
      !preview ||
      preview.preview.kind !== 'valid'
    ) {
      return;
    }
    const request = saveRequestRef.current + 1;
    saveRequestRef.current = request;
    const editGeneration = editGenerationRef.current;
    const expectedSourceRevision = editor.state.result.envelope.revision;
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
    if (
      !mountedRef.current ||
      request !== saveRequestRef.current ||
      editGeneration !== editGenerationRef.current ||
      expectedSourceRevision !== sourceRevisionRef.current
    ) {
      if (!mountedRef.current) return;
      if (result.kind === 'ok' || result.problem.kind === 'stale-revision') {
        await editor.reload();
        if (!mountedRef.current) return;
        setSavedConflict(true);
      }
      setPending(current => (current === 'save' ? undefined : current));
      return;
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
      setPending(undefined);
      if (result.problem.kind === 'stale-revision') {
        setPreview(undefined);
        await editor.reload();
      }
      return;
    }

    const invalidatedApprovalCount =
      result.envelope.value.invalidatedApprovalCount;
    dirtyRef.current = false;
    previewRequestRef.current += 1;
    suggestionRequestRef.current += 1;
    setPreview(undefined);
    setSuggestions(undefined);
    const refreshed = await editor.reload();
    if (
      !mountedRef.current ||
      request !== saveRequestRef.current ||
      editGeneration !== editGenerationRef.current
    ) {
      if (mountedRef.current) {
        dirtyRef.current = true;
        setSavedConflict(true);
        setPending(current => (current === 'save' ? undefined : current));
      }
      return;
    }
    if (refreshed.kind === 'ok') {
      applySaved(refreshed.envelope);
      setMessage(t('live.message.saved', { count: invalidatedApprovalCount }));
    } else {
      setMessage(
        t('live.message.savedRecheckFailed', {
          count: invalidatedApprovalCount,
        }),
      );
    }
    setPending(undefined);
  };

  const generateSuggestions = async () => {
    if (
      pending !== undefined ||
      !editorActionsTrusted ||
      editor.state.kind !== 'ready'
    ) {
      return;
    }
    const request = suggestionRequestRef.current + 1;
    suggestionRequestRef.current = request;
    const editGeneration = editGenerationRef.current;
    const expectedSourceRevision = editor.state.result.envelope.revision;
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
    if (
      !mountedRef.current ||
      request !== suggestionRequestRef.current ||
      editGeneration !== editGenerationRef.current ||
      expectedSourceRevision !== sourceRevisionRef.current
    ) {
      if (mountedRef.current) {
        setPending(current => (current === 'suggest' ? undefined : current));
      }
      return;
    }
    if (result.kind === 'error') {
      setProblem(result.problem);
    } else {
      setSuggestions(result.envelope.value);
    }
    setPending(undefined);
  };

  const applyBuiltIn = (
    template: (typeof BUILT_IN_MESSAGE_TEMPLATES)[number],
  ) => {
    setLanguage(template.draft.language);
    setTone(template.draft.tone);
    setPlaceholderMode(template.draft.placeholderMode);
    setSegmentCap(template.draft.requestedSegmentCap as 1 | 2);
    setText(template.draft.text);
    markDirty();
  };

  const matchingBuiltIns = BUILT_IN_MESSAGE_TEMPLATES.filter(
    template => template.draft.language === language,
  );
  const suggestionNeedsFallback =
    suggestions?.kind === 'fallback' || suggestions?.kind === 'failed';
  const renderBuiltIns = (testIdPrefix: string) => (
    <>
      <SectionHeading
        title={t('live.message.builtInTitle')}
        supporting={t('live.message.builtInBody')}
      />
      {matchingBuiltIns.map(template => (
        <Card key={`${testIdPrefix}-${template.id}`}>
          <AppText variant="label">
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
    </>
  );

  return (
    <Screen includeTopInset testID="live-message-screen">
      <Button
        label={t('live.common.back')}
        onPress={onBack}
        variant="ghost"
        testID="live-message-back"
      />
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
            <>
              <LiveRefreshProblem problem={editor.state.refreshProblem} />
              <Button
                label={t('live.common.tryAgain')}
                disabled={editor.state.refreshing || pending !== undefined}
                onPress={() => editor.reload()}
                testID="live-message-check-status"
              />
            </>
          ) : null}

          <SectionHeading title={t('live.message.currentTitle')} />
          <Card testID="live-message-current">
            <StatusRow
              title={
                editor.state.refreshing
                  ? t('live.message.currentChecking')
                  : editor.state.refreshProblem
                  ? t('live.message.currentUnverified')
                  : editor.state.result.envelope.value.kind === 'configured'
                  ? t('live.message.currentSaved')
                  : t('live.message.notConfigured')
              }
              tone={
                editor.state.refreshProblem
                  ? 'warning'
                  : editor.state.refreshing
                  ? 'neutral'
                  : editor.state.result.envelope.value.kind === 'configured'
                  ? 'positive'
                  : 'info'
              }
              testID="live-message-current-status"
            />
            {editor.state.result.envelope.value.kind === 'configured' ? (
              <AppText>{editor.state.result.envelope.value.draft.text}</AppText>
            ) : null}
          </Card>

          {savedConflict ? (
            <Card>
              <StatusRow
                title={t('live.message.savedChanged')}
                tone="warning"
              />
              <Button
                label={t('live.message.reloadSaved')}
                disabled={pending !== undefined}
                onPress={() => {
                  if (editor.state.kind === 'ready') {
                    applySaved(editor.state.result.envelope);
                  }
                }}
                variant="secondary"
                testID="live-message-reload-saved"
              />
            </Card>
          ) : null}

          <SectionHeading title={t('live.message.language')} />
          <SingleChoiceGroup
            label={t('live.message.language')}
            testID="live-message-language-group"
          >
            <View style={styles.choices}>
              <ChoiceChip
                label={t('live.message.english')}
                selected={language === 'en'}
                onPress={() => {
                  setLanguage('en');
                  markDirty();
                }}
                testID="live-message-language-en"
              />
              <ChoiceChip
                label={t('live.message.hindi')}
                selected={language === 'hi'}
                onPress={() => {
                  setLanguage('hi');
                  markDirty();
                }}
                testID="live-message-language-hi"
              />
            </View>
          </SingleChoiceGroup>

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

          {editorActionsTrusted && preview?.preview.kind !== 'valid' ? (
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
          ) : null}

          {editorActionsTrusted && preview?.preview.kind === 'invalid' ? (
            <Card testID="live-message-invalid-review">
              <AppText variant="heading">{t('live.message.invalid')}</AppText>
              <KeyValue
                label={t('live.message.validation')}
                value={t('live.message.validationFailed')}
              />
              {preview.preview.issues.map(issue => (
                <StatusRow
                  key={`${issue.field}-${issue.code}`}
                  title={t(safeReasonMessageKey(issue.code))}
                  tone="warning"
                />
              ))}
            </Card>
          ) : null}
          {editorActionsTrusted && preview?.preview.kind === 'valid' ? (
            <InlineReviewCard
              reviewKey={preview.preview.handle}
              testID="live-message-review"
              title={t('live.message.previewTitle')}
            >
              {preview.preview.examples.map((example, index) => (
                <View
                  key={`${index}-${example.displayName}`}
                  style={styles.example}
                  testID={`live-message-example-${index}`}
                >
                  <KeyValue
                    label={t('live.message.exampleName')}
                    value={bidiIsolate(example.displayName)}
                  />
                  <KeyValue
                    label={t('live.message.finalText')}
                    value={example.finalText}
                  />
                  <KeyValue
                    label={t('live.message.characterCount')}
                    value={String(example.characterCount)}
                  />
                  <KeyValue
                    label={t('live.message.encoding')}
                    value={example.encodingLabel}
                  />
                  <KeyValue
                    label={t('live.message.segmentCount')}
                    value={t('live.common.parts', {
                      count: example.segmentCount,
                    })}
                  />
                </View>
              ))}
              <KeyValue
                label={t('live.message.maximumUsed')}
                value={t('live.common.parts', {
                  count: preview.preview.maximumSegmentCount,
                })}
              />
              <KeyValue
                label={t('live.message.maximumCap')}
                value={t('live.common.parts', { count: segmentCap })}
              />
              <KeyValue
                label={t('live.message.validation')}
                value={t('live.message.validationPassed')}
              />
              <AppText color="muted">
                {t('live.message.affected', {
                  count: preview.preview.affectedRecipientCount,
                })}
              </AppText>
              <ReadinessBanner
                title={t('live.message.approvalConsequenceTitle')}
                detail={t('live.message.approvalConsequenceBody')}
                tone="warning"
                testID="live-message-approval-consequence"
              />
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
                testID="live-message-cancel-review"
              />
            </InlineReviewCard>
          ) : null}

          <Button
            label={
              helpExpanded
                ? t('live.message.hideHelp')
                : t('live.message.showHelp')
            }
            onPress={() => setHelpExpanded(current => !current)}
            variant="secondary"
            testID="live-message-help-toggle"
          />
          {helpExpanded ? (
            <Card testID="live-message-help">
              <SectionHeading
                title={t('live.message.helpTitle')}
                supporting={t('live.message.helpBody')}
              />
              <AppText variant="label">{t('live.message.tone')}</AppText>
              <SingleChoiceGroup
                label={t('live.message.tone')}
                testID="live-message-tone-group"
              >
                {(['warm', 'simple', 'cheerful'] as const).map(value => (
                  <View key={value} style={styles.toneChoice}>
                    <ChoiceChip
                      label={t(`live.message.${value}`)}
                      selected={tone === value}
                      onPress={() => {
                        setTone(value);
                        markDirty();
                      }}
                      testID={`live-message-tone-${value}`}
                    />
                    <AppText
                      color="muted"
                      variant="caption"
                      testID={`live-message-tone-${value}-sample`}
                    >
                      {t(`live.message.${value}Sample`)}
                    </AppText>
                  </View>
                ))}
              </SingleChoiceGroup>
              <ReadinessBanner
                title={t('live.message.geminiPrivacyTitle')}
                detail={t('live.message.geminiPrivacyBody')}
                tone="info"
                testID="live-message-gemini-privacy"
              />
              {editorActionsTrusted ? (
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
              ) : null}
              {suggestions?.kind === 'candidates'
                ? suggestions.candidates.map((candidate, index) => (
                    <Card key={index}>
                      <AppText>{candidate}</AppText>
                      <Button
                        label={t('live.message.useSuggestion')}
                        disabled={pending !== undefined}
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
              {suggestionNeedsFallback && suggestions ? (
                <>
                  <StatusRow
                    title={t('live.message.suggestionUnavailable', {
                      reason: t(safeReasonMessageKey(suggestions.reason)),
                    })}
                    tone="warning"
                  />
                  {renderBuiltIns('fallback')}
                </>
              ) : null}
            </Card>
          ) : null}

          <Button
            label={
              optionsExpanded
                ? t('live.message.hideOptions')
                : t('live.message.showOptions')
            }
            onPress={() => setOptionsExpanded(current => !current)}
            variant="secondary"
            testID="live-message-options-toggle"
          />
          {optionsExpanded ? (
            <Card testID="live-message-options">
              <SectionHeading
                title={t('live.message.optionsTitle')}
                supporting={t('live.message.optionsBody')}
              />
              <AppText variant="label">{t('live.message.nameMode')}</AppText>
              <SingleChoiceGroup
                label={t('live.message.nameMode')}
                testID="live-message-name-group"
              >
                <View style={styles.choices}>
                  <ChoiceChip
                    label={t('live.message.givenName')}
                    selected={placeholderMode === 'given-name'}
                    onPress={() => {
                      setPlaceholderMode('given-name');
                      markDirty();
                    }}
                    testID="live-message-name-given"
                  />
                  <ChoiceChip
                    label={t('live.message.generic')}
                    selected={placeholderMode === 'generic'}
                    onPress={() => {
                      setPlaceholderMode('generic');
                      markDirty();
                    }}
                    testID="live-message-name-generic"
                  />
                </View>
              </SingleChoiceGroup>
              <AppText variant="label">{t('live.message.segmentCap')}</AppText>
              <SingleChoiceGroup
                label={t('live.message.segmentCap')}
                testID="live-message-segment-group"
              >
                <View style={styles.choices}>
                  {([1, 2] as const).map(value => (
                    <ChoiceChip
                      key={value}
                      label={t('live.message.segmentCapChoice', {
                        count: value,
                      })}
                      selected={segmentCap === value}
                      onPress={() => {
                        setSegmentCap(value);
                        markDirty();
                      }}
                      testID={`live-message-segment-${value}`}
                    />
                  ))}
                </View>
              </SingleChoiceGroup>
              {!suggestionNeedsFallback ? renderBuiltIns('options') : null}
            </Card>
          ) : null}
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  choices: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  toneChoice: { alignItems: 'flex-start', gap: spacing.xs },
  example: { gap: spacing.sm, paddingVertical: spacing.sm },
  input: {
    borderRadius: radii.md,
    borderWidth: 1,
    fontSize: 17,
    minHeight: minimumTargetSize * 3,
    padding: spacing.md,
    textAlignVertical: 'top',
  },
});
