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
  PolicyEditorProjection,
  PolicyPreview,
} from '../../domain/birthdays/model';
import type {
  NativeRevision,
  PolicyReviewHandle,
  SafeSupportCode,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';
import type { UtcInstant } from '../../domain/shared/temporal';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort } from './LiveAppPort';
import { LivePolicyEditor } from './LivePolicyEditor';

jest.mock('react-native-localize', () => ({
  getLocales: () => [{ languageCode: 'en' }],
}));

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

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

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
});

afterEach(async () => {
  await cleanup();
  await appI18n.changeLanguage('en');
  jest.restoreAllMocks();
});

it('shows Preview or Save, never both, and hides both for refresh uncertainty', async () => {
  const appStateListeners = captureAppStateListeners();
  const refreshedEditor = deferred<NativeResult<PolicyEditorProjection>>();
  const getPolicyEditor = jest
    .fn()
    .mockResolvedValueOnce(ok({ kind: 'not-configured' as const }))
    .mockImplementationOnce(() => refreshedEditor.promise);
  const previewPolicy = jest.fn(async () =>
    ok(
      {
        kind: 'valid' as const,
        handle: 'policy-review' as PolicyReviewHandle,
        summary: '09:00–11:00',
        simulatedDays: 400 as const,
        maximumPlannedInLocalDay: 3,
        maximumPlannedInRolling24Hours: 3,
      },
      revision('7'),
    ),
  );
  const savePolicy = jest.fn();
  const port = {
    getPolicyEditor,
    previewPolicy,
    savePolicy,
    subscribeInvalidations: jest.fn(() => () => undefined),
  } as unknown as LiveAppPort;
  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LivePolicyEditor platform="android" port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );

  expect(await screen.findByTestId('live-policy-preview')).toBeTruthy();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();

  await fireEvent.press(screen.getByTestId('live-policy-preview'));
  expect(await screen.findByTestId('live-policy-save')).toBeTruthy();
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
  });
  await waitFor(() => expect(getPolicyEditor).toHaveBeenCalledTimes(2));
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();

  await act(async () => {
    refreshedEditor.resolve({
      kind: 'error',
      problem: {
        kind: 'internal',
        supportCode: 'POLICY_REFRESH_FAILED' as SafeSupportCode,
      },
    });
    await refreshedEditor.promise;
  });
  expect(await screen.findByTestId('live-policy-check-status')).toBeTruthy();
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
  expect(savePolicy).not.toHaveBeenCalled();
});

it('hides and guards Preview when saved policy truth has a refresh problem', async () => {
  const appStateListeners = captureAppStateListeners();
  const refreshedEditor = deferred<NativeResult<PolicyEditorProjection>>();
  const getPolicyEditor = jest
    .fn()
    .mockResolvedValueOnce(ok({ kind: 'not-configured' as const }))
    .mockImplementationOnce(() => refreshedEditor.promise);
  const previewPolicy = jest.fn();
  const savePolicy = jest.fn();
  const port = {
    getPolicyEditor,
    previewPolicy,
    savePolicy,
    subscribeInvalidations: jest.fn(() => () => undefined),
  } as unknown as LiveAppPort;
  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LivePolicyEditor platform="ios" port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );

  expect(await screen.findByTestId('live-policy-preview')).toBeTruthy();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();

  await act(async () => {
    appStateListeners.forEach(listener => listener('active'));
  });
  await waitFor(() => expect(getPolicyEditor).toHaveBeenCalledTimes(2));
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();

  await act(async () => {
    refreshedEditor.resolve({
      kind: 'error',
      problem: {
        kind: 'internal',
        supportCode: 'POLICY_REFRESH_FAILED' as SafeSupportCode,
      },
    });
    await refreshedEditor.promise;
  });
  expect(await screen.findByTestId('live-policy-check-status')).toBeTruthy();
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
  expect(previewPolicy).not.toHaveBeenCalled();
  expect(savePolicy).not.toHaveBeenCalled();
});

it('ignores a stale Preview result after the draft changes in flight', async () => {
  captureAppStateListeners();
  const staleHandle = 'policy-review-draft-a' as PolicyReviewHandle;
  const stalePreview = deferred<NativeResult<PolicyPreview>>();
  const getPolicyEditor = jest.fn(async () =>
    ok({ kind: 'not-configured' as const }),
  );
  const previewPolicy = jest.fn(
    (_input: Parameters<LiveAppPort['previewPolicy']>[0]) =>
      stalePreview.promise,
  );
  const savePolicy = jest.fn();
  const port = {
    getPolicyEditor,
    previewPolicy,
    savePolicy,
    subscribeInvalidations: jest.fn(() => () => undefined),
  } as unknown as LiveAppPort;
  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LivePolicyEditor platform="android" port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );

  fireEvent.press(await screen.findByTestId('live-policy-preview'));
  await waitFor(() => expect(previewPolicy).toHaveBeenCalledTimes(1));
  expect(previewPolicy.mock.calls[0]?.[0].draft.primaryStart).toBe('09:00');

  await fireEvent.changeText(screen.getByTestId('live-policy-start'), '10:00');
  expect(screen.getByTestId('live-policy-start').props.value).toBe('10:00');

  await act(async () => {
    stalePreview.resolve(
      ok(
        {
          kind: 'valid',
          handle: staleHandle,
          summary: 'STALE DRAFT A PREVIEW',
          simulatedDays: 400,
          maximumPlannedInLocalDay: 3,
          maximumPlannedInRolling24Hours: 3,
        },
        revision('7'),
      ),
    );
    await stalePreview.promise;
  });

  await waitFor(() =>
    expect(
      screen.getByTestId('live-policy-preview').props.accessibilityState,
    ).toEqual({ disabled: false }),
  );
  expect(screen.getByTestId('live-policy-start').props.value).toBe('10:00');
  expect(screen.queryByText('STALE DRAFT A PREVIEW')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
  expect(savePolicy).not.toHaveBeenCalledWith(
    expect.objectContaining({ handle: staleHandle }),
  );
  expect(savePolicy).not.toHaveBeenCalled();
});

it('ignores a stale Save completion after the draft changes in flight', async () => {
  captureAppStateListeners();
  const savedHandle = 'policy-review-before-edit' as PolicyReviewHandle;
  const staleSave = deferred<NativeResult<unknown>>();
  const getPolicyEditor = jest
    .fn()
    .mockResolvedValueOnce(ok({ kind: 'not-configured' as const }))
    .mockResolvedValue(ok({ kind: 'not-configured' as const }, revision('8')));
  const previewPolicy = jest.fn(async () =>
    ok(
      {
        kind: 'valid' as const,
        handle: savedHandle,
        summary: 'Draft ready to save',
        simulatedDays: 400 as const,
        maximumPlannedInLocalDay: 3,
        maximumPlannedInRolling24Hours: 3,
      },
      revision('7'),
    ),
  );
  const savePolicy = jest.fn(() => staleSave.promise);
  const port = {
    getPolicyEditor,
    previewPolicy,
    savePolicy,
    subscribeInvalidations: jest.fn(() => () => undefined),
  } as unknown as LiveAppPort;
  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LivePolicyEditor platform="android" port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );

  await fireEvent.press(await screen.findByTestId('live-policy-preview'));
  fireEvent.press(await screen.findByTestId('live-policy-save'));
  await waitFor(() => expect(savePolicy).toHaveBeenCalledTimes(1));
  expect(savePolicy).toHaveBeenCalledWith({
    handle: savedHandle,
    expectedRevision: '7',
  });

  await fireEvent.changeText(screen.getByTestId('live-policy-end'), '12:00');
  expect(screen.getByTestId('live-policy-end').props.value).toBe('12:00');

  await act(async () => {
    staleSave.resolve(ok(null, revision('8')));
    await staleSave.promise;
  });

  await waitFor(() => expect(getPolicyEditor).toHaveBeenCalledTimes(2));
  expect(await screen.findByTestId('live-policy-reload-saved')).toBeTruthy();
  expect(screen.getByTestId('live-policy-end').props.value).toBe('12:00');
  expect(
    screen.queryByText('The delivery policy was saved and checked again.'),
  ).toBeNull();
  expect(screen.queryByTestId('live-policy-preview')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
});

it('leads with the saved window and keeps grace and cap in Schedule options', async () => {
  captureAppStateListeners();
  const getPolicyEditor = jest.fn(async () =>
    ok({
      kind: 'configured' as const,
      draft: {
        primaryStart: '09:00' as const,
        primaryEnd: '11:00' as const,
        latePolicy: {
          kind: 'same-day-grace' as const,
          graceEnd: '12:00' as const,
        },
        dailyCap: 7,
      },
    }),
  );
  const port = {
    getPolicyEditor,
    previewPolicy: jest.fn(),
    savePolicy: jest.fn(),
    subscribeInvalidations: jest.fn(() => () => undefined),
  } as unknown as LiveAppPort;

  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LivePolicyEditor platform="android" port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );

  expect(await screen.findByTestId('live-policy-current-summary')).toBeTruthy();
  expect(screen.getByText('Start time')).toBeTruthy();
  expect(screen.getByText('End time')).toBeTruthy();
  expect(screen.getByTestId('live-policy-start').props.value).toBe('09:00');
  expect(screen.getByTestId('live-policy-start').props.accessibilityLabel).toBe(
    'Start time',
  );
  expect(screen.getByTestId('live-policy-start').props.keyboardType).toBe(
    'numbers-and-punctuation',
  );
  expect(screen.getByTestId('live-policy-end').props.value).toBe('11:00');
  expect(screen.getByTestId('live-policy-end').props.accessibilityLabel).toBe(
    'End time',
  );
  expect(screen.getByTestId('live-policy-end').props.keyboardType).toBe(
    'numbers-and-punctuation',
  );
  expect(screen.queryByTestId('live-policy-grace')).toBeNull();
  expect(screen.queryByTestId('live-policy-daily-cap')).toBeNull();

  await fireEvent.press(screen.getByTestId('live-policy-options-toggle'));
  expect(screen.getByTestId('live-policy-grace')).toBeTruthy();
  expect(
    screen.getByTestId('live-policy-late-policy-group').props,
  ).toMatchObject({
    accessible: false,
    accessibilityLabel: 'If the primary window is missed',
    accessibilityRole: 'radiogroup',
  });
  expect(screen.getByTestId('live-policy-grace-end').props.value).toBe('12:00');
  expect(screen.getByTestId('live-policy-daily-cap').props.value).toBe('7');

  await fireEvent.press(screen.getByTestId('live-policy-options-toggle'));
  expect(screen.queryByTestId('live-policy-grace')).toBeNull();
  await fireEvent.press(screen.getByTestId('live-policy-options-toggle'));
  expect(screen.getByTestId('live-policy-grace-end').props.value).toBe('12:00');
  expect(screen.getByTestId('live-policy-daily-cap').props.value).toBe('7');
});

it.each(['android', 'ios'] as const)(
  'reviews a localized %s draft for 400 days with explicit save consequences',
  async platform => {
    captureAppStateListeners();
    const previewPolicy = jest.fn(async () =>
      ok(
        {
          kind: 'valid' as const,
          handle: `policy-review-${platform}` as PolicyReviewHandle,
          summary: 'RAW NATIVE ENGLISH POLICY SUMMARY',
          simulatedDays: 400 as const,
          maximumPlannedInLocalDay: 3,
          maximumPlannedInRolling24Hours: 4,
        },
        revision('7'),
      ),
    );
    const port = {
      getPolicyEditor: jest.fn(async () =>
        ok({ kind: 'not-configured' as const }),
      ),
      previewPolicy,
      savePolicy: jest.fn(),
      subscribeInvalidations: jest.fn(() => () => undefined),
    } as unknown as LiveAppPort;

    await render(
      <LocalizationProvider>
        <ThemeProvider initialPreference="light">
          <LivePolicyEditor platform={platform} port={port} />
        </ThemeProvider>
      </LocalizationProvider>,
    );

    expect(await screen.findByTestId('live-policy-preview')).toBeTruthy();
    expect(screen.queryByTestId('live-policy-save')).toBeNull();
    await fireEvent.press(screen.getByTestId('live-policy-preview'));

    expect(await screen.findByTestId('live-policy-review')).toBeTruthy();
    expect(screen.getByTestId('live-policy-save-consequence')).toBeTruthy();
    expect(
      screen.getByText(
        platform === 'android'
          ? '400 days simulated'
          : /protected preview checked 400 days/u,
      ),
    ).toBeTruthy();
    expect(screen.queryByText('RAW NATIVE ENGLISH POLICY SUMMARY')).toBeNull();
    expect(screen.queryByTestId('live-policy-preview')).toBeNull();
    expect(screen.getByTestId('live-policy-save')).toBeTruthy();
  },
);

it('retires a consumed policy review and reports an uncertain post-save check honestly', async () => {
  captureAppStateListeners();
  const getPolicyEditor = jest
    .fn()
    .mockResolvedValueOnce(ok({ kind: 'not-configured' as const }))
    .mockResolvedValueOnce({
      kind: 'error',
      problem: {
        kind: 'internal' as const,
        supportCode: 'POLICY_RECHECK_FAILED' as SafeSupportCode,
      },
    });
  const previewPolicy = jest.fn(async () =>
    ok(
      {
        kind: 'valid' as const,
        handle: 'policy-review-consumed' as PolicyReviewHandle,
        summary: 'unused',
        simulatedDays: 400 as const,
        maximumPlannedInLocalDay: 2,
        maximumPlannedInRolling24Hours: 2,
      },
      revision('7'),
    ),
  );
  const savePolicy = jest.fn(async () => ok(null, revision('8')));
  const port = {
    getPolicyEditor,
    previewPolicy,
    savePolicy,
    subscribeInvalidations: jest.fn(() => () => undefined),
  } as unknown as LiveAppPort;

  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LivePolicyEditor platform="android" port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );

  await fireEvent.press(await screen.findByTestId('live-policy-preview'));
  await fireEvent.press(await screen.findByTestId('live-policy-save'));

  expect(
    await screen.findByText(
      /saved, but its refreshed status could not be checked/u,
    ),
  ).toBeTruthy();
  expect(screen.queryByText(/saved and checked again/u)).toBeNull();
  expect(screen.queryByTestId('live-policy-review')).toBeNull();
  expect(screen.queryByTestId('live-policy-save')).toBeNull();
  expect(await screen.findByTestId('live-policy-check-status')).toBeTruthy();
  expect(savePolicy).toHaveBeenCalledWith({
    handle: 'policy-review-consumed',
    expectedRevision: '7',
  });
});
