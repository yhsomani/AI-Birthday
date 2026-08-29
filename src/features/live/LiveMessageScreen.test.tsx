import React from 'react';
import { AccessibilityInfo, AppState, type AppStateStatus } from 'react-native';
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react-native';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import type {
  GeminiSuggestionsProjection,
  MessageEditorProjection,
  MessagePreview,
} from '../../domain/messages/model';
import type {
  MessagePreviewHandle,
  NativeRevision,
  PrivateDisplayName,
  PrivateMessageText,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';
import type { UtcInstant } from '../../domain/shared/temporal';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort } from './LiveAppPort';
import { LiveMessageScreen } from './LiveMessageScreen';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

jest.mock('react-native-safe-area-context', () => {
  const TestReact = require('react');
  const { View } = require('react-native');
  return {
    SafeAreaView: (props: { children?: unknown; [key: string]: unknown }) => {
      const { children, ...viewProps } = props;
      return TestReact.createElement(View, viewProps, children);
    },
  };
});

jest.setTimeout(20000);

const generatedAt = '2026-07-19T07:00:00Z' as UtcInstant;
const revision = (value: string) => value as NativeRevision;

const ok = <Value,>(
  value: Value,
  currentRevision = revision('1'),
): NativeResult<Value> => ({
  kind: 'ok',
  envelope: {
    contractVersion: 1,
    generatedAt,
    revision: currentRevision,
    value,
  },
});

const internalError = <Value,>(supportCode: string): NativeResult<Value> => ({
  kind: 'error',
  problem: {
    kind: 'internal',
    supportCode: supportCode as SafeSupportCode,
  },
});

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

const configuredMessage = (
  messageText = 'Happy birthday!' as PrivateMessageText,
): Extract<MessageEditorProjection, { kind: 'configured' }> => ({
  kind: 'configured',
  draft: {
    language: 'en',
    tone: 'warm',
    placeholderMode: { kind: 'generic', requiredCount: 0 },
    text: messageText,
    requestedSegmentCap: 2,
  },
});

const validPreview = (
  handle = 'message-review-1' as MessagePreviewHandle,
): MessagePreview => ({
  kind: 'valid',
  handle,
  examples: [
    {
      displayName: 'Asha (अशा)' as PrivateDisplayName,
      finalText: 'Happy birthday! Have a wonderful day.' as PrivateMessageText,
      characterCount: 37,
      segmentCount: 1,
      encodingLabel: 'unicode',
    },
  ],
  maximumSegmentCount: 1,
  affectedRecipientCount: 2,
});

const createPort = (
  overrides: Partial<LiveAppPort> = {},
): {
  port: LiveAppPort;
  generateSuggestions: jest.Mock;
  getMessageEditor: jest.Mock;
  previewMessage: jest.Mock;
  saveMessage: jest.Mock;
} => {
  const getMessageEditor = jest.fn(async () => ok(configuredMessage()));
  const previewMessage = jest.fn(async () => ok(validPreview(), revision('7')));
  const saveMessage = jest.fn(async () =>
    ok({
      draft: configuredMessage().draft,
      affectedRecipientCount: 2,
      invalidatedApprovalCount: 2,
    }),
  );
  const generateSuggestions = jest.fn(async () =>
    ok({
      kind: 'candidates' as const,
      candidates: ['A safe generic suggestion' as PrivateMessageText],
    }),
  );
  return {
    port: {
      getMessageEditor,
      previewMessage,
      saveMessage,
      generateSuggestions,
      subscribeInvalidations: jest.fn(() => () => undefined),
      ...overrides,
    } as unknown as LiveAppPort,
    generateSuggestions:
      (overrides.generateSuggestions as jest.Mock | undefined) ??
      generateSuggestions,
    getMessageEditor:
      (overrides.getMessageEditor as jest.Mock | undefined) ?? getMessageEditor,
    previewMessage:
      (overrides.previewMessage as jest.Mock | undefined) ?? previewMessage,
    saveMessage:
      (overrides.saveMessage as jest.Mock | undefined) ?? saveMessage,
  };
};

const renderMessage = async (port: LiveAppPort) =>
  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveMessageScreen onBack={jest.fn()} port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );

const captureAppStateListeners = () => {
  const listeners: Array<(state: AppStateStatus) => void> = [];
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_type, listener) => {
      listeners.push(listener);
      return { remove: jest.fn() };
    });
  return listeners;
};

beforeEach(() => {
  jest.clearAllMocks();
  jest
    .spyOn(AccessibilityInfo, 'isHighTextContrastEnabled')
    .mockResolvedValue(false);
  jest
    .spyOn(AccessibilityInfo, 'isReduceMotionEnabled')
    .mockResolvedValue(false);
  jest
    .spyOn(AccessibilityInfo, 'isScreenReaderEnabled')
    .mockResolvedValue(false);
});

afterEach(async () => {
  await cleanup();
  await appI18n.changeLanguage('en');
  jest.restoreAllMocks();
});

it('keeps optional controls closed with safe defaults and preserves choices across collapse', async () => {
  const getMessageEditor = jest.fn(async () =>
    ok({ kind: 'not-configured' as const }),
  );
  const harness = createPort({ getMessageEditor });
  await renderMessage(harness.port);

  expect(await screen.findByTestId('live-message-input')).toBeTruthy();
  expect(screen.getByText('No birthday message is saved yet.')).toBeTruthy();
  expect(
    screen.getByTestId('live-message-language-en').props.accessibilityState,
  ).toEqual({ selected: true });
  expect(screen.getByTestId('live-message-language-group').props).toMatchObject(
    {
      accessible: false,
      accessibilityLabel: 'Language',
      accessibilityRole: 'radiogroup',
    },
  );
  expect(screen.queryByTestId('live-message-help')).toBeNull();
  expect(screen.queryByTestId('live-message-options')).toBeNull();
  expect(screen.queryByTestId('live-message-gemini-privacy')).toBeNull();
  expect(screen.queryByTestId('live-message-built-in-en-generic')).toBeNull();
  expect(harness.generateSuggestions).not.toHaveBeenCalled();

  await fireEvent.press(screen.getByTestId('live-message-help-toggle'));
  expect(screen.getByTestId('live-message-help')).toBeTruthy();
  expect(screen.getByTestId('live-message-gemini-privacy')).toBeTruthy();
  expect(
    screen.getByTestId('live-message-tone-warm').props.accessibilityState,
  ).toEqual({ selected: true });
  expect(screen.getByTestId('live-message-tone-group').props).toMatchObject({
    accessible: false,
    accessibilityLabel: 'Tone',
    accessibilityRole: 'radiogroup',
  });
  expect(screen.getByTestId('live-message-tone-warm-sample')).toBeTruthy();
  expect(harness.generateSuggestions).not.toHaveBeenCalled();

  await fireEvent.press(screen.getByTestId('live-message-tone-cheerful'));
  await fireEvent.press(screen.getByTestId('live-message-help-toggle'));
  await fireEvent.press(screen.getByTestId('live-message-help-toggle'));
  expect(
    screen.getByTestId('live-message-tone-cheerful').props.accessibilityState,
  ).toEqual({ selected: true });

  await fireEvent.press(screen.getByTestId('live-message-options-toggle'));
  expect(
    screen.getByTestId('live-message-name-given').props.accessibilityState,
  ).toEqual({ selected: true });
  expect(
    screen.getByTestId('live-message-segment-2').props.accessibilityState,
  ).toEqual({ selected: true });
  expect(screen.getByTestId('live-message-name-group').props).toMatchObject({
    accessible: false,
    accessibilityLabel: 'Name style',
    accessibilityRole: 'radiogroup',
  });
  expect(screen.getByTestId('live-message-segment-group').props).toMatchObject({
    accessible: false,
    accessibilityLabel: 'Maximum SMS parts',
    accessibilityRole: 'radiogroup',
  });
  expect(screen.getByTestId('live-message-built-in-en-generic')).toBeTruthy();

  await fireEvent.press(screen.getByTestId('live-message-name-generic'));
  await fireEvent.press(screen.getByTestId('live-message-segment-1'));
  await fireEvent.press(screen.getByTestId('live-message-options-toggle'));
  await fireEvent.press(screen.getByTestId('live-message-options-toggle'));
  expect(
    screen.getByTestId('live-message-name-generic').props.accessibilityState,
  ).toEqual({ selected: true });
  expect(
    screen.getByTestId('live-message-segment-1').props.accessibilityState,
  ).toEqual({ selected: true });
});

it('sends Gemini only the allowed authoring choices', async () => {
  const harness = createPort();
  await renderMessage(harness.port);

  await fireEvent.changeText(
    await screen.findByTestId('live-message-input'),
    'Secret draft mentioning Ravi +91 98765 43210',
  );
  await fireEvent.press(screen.getByTestId('live-message-help-toggle'));
  await fireEvent.press(await screen.findByTestId('live-message-suggest'));

  await waitFor(() =>
    expect(harness.generateSuggestions).toHaveBeenCalledTimes(1),
  );
  expect(harness.generateSuggestions).toHaveBeenCalledWith({
    language: 'en',
    tone: 'warm',
    placeholderMode: { kind: 'generic', requiredCount: 0 },
    requestedSegmentCap: 2,
    relationship: undefined,
    milestone: undefined,
  });
  expect(await screen.findByTestId('live-message-suggestion-0')).toBeTruthy();
});

it('sends Gemini selected relationship and milestone choices', async () => {
  const harness = createPort();
  await renderMessage(harness.port);

  await fireEvent.press(screen.getByTestId('live-message-help-toggle'));

  // Select relationship chip
  await fireEvent.press(
    await screen.findByTestId('live-message-relationship-friend'),
  );

  // Select milestone chip
  await fireEvent.press(
    await screen.findByTestId('live-message-milestone-new-job'),
  );

  await fireEvent.press(await screen.findByTestId('live-message-suggest'));

  await waitFor(() =>
    expect(harness.generateSuggestions).toHaveBeenCalledTimes(1),
  );
  expect(harness.generateSuggestions).toHaveBeenCalledWith({
    language: 'en',
    tone: 'warm',
    placeholderMode: { kind: 'generic', requiredCount: 0 },
    requestedSegmentCap: 2,
    relationship: 'friend',
    milestone: 'new-job',
  });
});

it('ignores an in-flight Gemini result after a material edit', async () => {
  const suggestion = deferred<NativeResult<GeminiSuggestionsProjection>>();
  const generateSuggestions = jest.fn(() => suggestion.promise);
  const harness = createPort({ generateSuggestions });
  await renderMessage(harness.port);

  await fireEvent.press(await screen.findByTestId('live-message-help-toggle'));
  fireEvent.press(screen.getByTestId('live-message-suggest'));
  await waitFor(() => expect(generateSuggestions).toHaveBeenCalledTimes(1));
  await fireEvent.changeText(
    screen.getByTestId('live-message-input'),
    'Happy birthday after the request',
  );

  await act(async () => {
    suggestion.resolve(
      ok({
        kind: 'candidates' as const,
        candidates: ['STALE GEMINI CANDIDATE' as PrivateMessageText],
      }),
    );
    await suggestion.promise;
  });

  expect(screen.queryByText('STALE GEMINI CANDIDATE')).toBeNull();
  expect(screen.queryByTestId('live-message-suggestion-0')).toBeNull();
});

it('shows an exact handle-bound review and saves with the preview revision', async () => {
  const savedProjection = configuredMessage(
    'Happy birthday! Have a wonderful day.' as PrivateMessageText,
  );
  const getMessageEditor = jest
    .fn()
    .mockResolvedValueOnce(ok(configuredMessage(), revision('1')))
    .mockResolvedValueOnce(ok(savedProjection, revision('8')));
  const previewMessage = jest.fn(async () => ok(validPreview(), revision('7')));
  const saveMessage = jest.fn(async () =>
    ok(
      {
        draft: savedProjection.draft,
        affectedRecipientCount: 2,
        invalidatedApprovalCount: 2,
      },
      revision('8'),
    ),
  );
  const harness = createPort({ getMessageEditor, previewMessage, saveMessage });
  await renderMessage(harness.port);

  await fireEvent.press(await screen.findByTestId('live-message-preview'));
  expect(await screen.findByTestId('live-message-review')).toBeTruthy();
  expect(screen.queryByTestId('live-message-preview')).toBeNull();
  expect(screen.getByTestId('live-message-save')).toBeTruthy();
  expect(screen.getByTestId('live-message-save').props.accessibilityLabel).toBe(
    'Save message',
  );
  expect(
    screen.getByTestId('live-message-review-focus').props.accessibilityLabel,
  ).toBe('Confirm this message?');
  expect(screen.getByText('\u2068Asha (अशा)\u2069')).toBeTruthy();
  expect(
    screen.getByText('Happy birthday! Have a wonderful day.'),
  ).toBeTruthy();
  expect(screen.getByText('Characters')).toBeTruthy();
  expect(screen.getByText('37')).toBeTruthy();
  expect(screen.getByText('SMS encoding')).toBeTruthy();
  expect(screen.getByText('unicode')).toBeTruthy();
  expect(screen.getByText('Most SMS parts in this preview')).toBeTruthy();
  expect(screen.getByText('Saved SMS-part limit')).toBeTruthy();
  expect(screen.getByText('Passed')).toBeTruthy();
  expect(screen.getByTestId('live-message-approval-consequence')).toBeTruthy();

  await fireEvent.press(screen.getByTestId('live-message-save'));
  await waitFor(() => expect(saveMessage).toHaveBeenCalledTimes(1));
  expect(previewMessage).toHaveBeenCalledWith({
    draft: configuredMessage().draft,
    expectedRevision: '1',
  });
  expect(saveMessage).toHaveBeenCalledWith({
    handle: 'message-review-1',
    expectedRevision: '7',
  });
  expect(
    await screen.findByText(
      'The message was saved and checked again. 2 old approvals were cleared.',
    ),
  ).toBeTruthy();
  expect(screen.queryByTestId('live-message-review')).toBeNull();
});

it('clears a consumed review before recheck and reports recheck failure separately', async () => {
  const refreshedEditor = deferred<NativeResult<MessageEditorProjection>>();
  const getMessageEditor = jest
    .fn()
    .mockResolvedValueOnce(ok(configuredMessage()))
    .mockImplementationOnce(() => refreshedEditor.promise);
  const saveMessage = jest.fn(async () =>
    ok({
      draft: configuredMessage().draft,
      affectedRecipientCount: 2,
      invalidatedApprovalCount: 1,
    }),
  );
  const harness = createPort({ getMessageEditor, saveMessage });
  await renderMessage(harness.port);

  await fireEvent.press(await screen.findByTestId('live-message-preview'));
  await act(async () => {
    fireEvent.press(await screen.findByTestId('live-message-save'));
    await Promise.resolve();
    await Promise.resolve();
  });
  await waitFor(() => expect(getMessageEditor).toHaveBeenCalledTimes(2));
  expect(screen.queryByTestId('live-message-review')).toBeNull();

  await act(async () => {
    refreshedEditor.resolve(internalError('MESSAGE_RECHECK_FAILED'));
    await refreshedEditor.promise;
  });

  expect(
    await screen.findByText(
      'The message was saved and 1 old approval was cleared, but the latest saved state could not be checked. Check again before making another change.',
    ),
  ).toBeTruthy();
  expect(screen.getByTestId('live-message-check-status')).toBeTruthy();
  expect(screen.queryByTestId('live-message-preview')).toBeNull();
  expect(screen.queryByTestId('live-message-save')).toBeNull();
});

it('fails closed while saved message truth is refreshing or refresh-failed', async () => {
  const appStateListeners = captureAppStateListeners();
  const refreshedEditor = deferred<NativeResult<MessageEditorProjection>>();
  const getMessageEditor = jest
    .fn()
    .mockResolvedValueOnce(ok(configuredMessage()))
    .mockImplementationOnce(() => refreshedEditor.promise);
  const harness = createPort({ getMessageEditor });
  await renderMessage(harness.port);

  await fireEvent.press(await screen.findByTestId('live-message-preview'));
  expect(await screen.findByTestId('live-message-save')).toBeTruthy();

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
  });
  await waitFor(() =>
    expect(screen.queryByTestId('live-message-save')).toBeNull(),
  );
  expect(screen.queryByTestId('live-message-preview')).toBeNull();

  await act(async () => {
    refreshedEditor.resolve(internalError('MESSAGE_REFRESH_FAILED'));
    await refreshedEditor.promise;
  });
  expect(await screen.findByTestId('live-message-check-status')).toBeTruthy();
  expect(screen.queryByTestId('live-message-preview')).toBeNull();
  expect(screen.queryByTestId('live-message-save')).toBeNull();
});

it('ignores an in-flight Preview result after the message changes', async () => {
  const stalePreview = deferred<NativeResult<MessagePreview>>();
  const previewMessage = jest.fn(() => stalePreview.promise);
  const harness = createPort({ previewMessage });
  await renderMessage(harness.port);

  fireEvent.press(await screen.findByTestId('live-message-preview'));
  await waitFor(() => expect(previewMessage).toHaveBeenCalledTimes(1));
  await fireEvent.changeText(
    screen.getByTestId('live-message-input'),
    'Happy birthday after Preview started',
  );

  await act(async () => {
    stalePreview.resolve(
      ok(
        validPreview('stale-message-review' as MessagePreviewHandle),
        revision('7'),
      ),
    );
    await stalePreview.promise;
  });

  expect(screen.queryByTestId('live-message-review')).toBeNull();
  expect(screen.queryByTestId('live-message-save')).toBeNull();
  expect(await screen.findByTestId('live-message-preview')).toBeTruthy();
});

it('keeps a new local edit when an older Save completes', async () => {
  const staleSave = deferred<Awaited<ReturnType<LiveAppPort['saveMessage']>>>();
  const getMessageEditor = jest
    .fn()
    .mockResolvedValueOnce(ok(configuredMessage(), revision('1')))
    .mockResolvedValueOnce(
      ok(
        configuredMessage(
          'Message saved before the newer edit' as PrivateMessageText,
        ),
        revision('8'),
      ),
    );
  const saveMessage = jest.fn(() => staleSave.promise);
  const harness = createPort({ getMessageEditor, saveMessage });
  await renderMessage(harness.port);

  await fireEvent.press(await screen.findByTestId('live-message-preview'));
  fireEvent.press(await screen.findByTestId('live-message-save'));
  await waitFor(() => expect(saveMessage).toHaveBeenCalledTimes(1));
  await fireEvent.changeText(
    screen.getByTestId('live-message-input'),
    'My newer unsaved message',
  );

  await act(async () => {
    staleSave.resolve(
      ok({
        draft: configuredMessage().draft,
        affectedRecipientCount: 2,
        invalidatedApprovalCount: 2,
      }),
    );
    await staleSave.promise;
  });

  await waitFor(() => expect(getMessageEditor).toHaveBeenCalledTimes(2));
  expect(screen.getByTestId('live-message-input').props.value).toBe(
    'My newer unsaved message',
  );
  expect(await screen.findByTestId('live-message-reload-saved')).toBeTruthy();
  expect(screen.queryByText(/message was saved and checked again/u)).toBeNull();
  expect(screen.queryByTestId('live-message-save')).toBeNull();
});
