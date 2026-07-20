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
import type { Fiber, TestInstance } from 'test-renderer';

import { ThemeProvider } from '../../app/providers/ThemeProvider';
import type { ProjectionInvalidation } from '../../application/ports/AppProjectionPort';
import type { DiagnosticsPreview } from '../../domain/activity/model';
import type { NativeRevision } from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';
import type { UtcInstant } from '../../domain/shared/temporal';
import { LocalizationProvider } from '../../localization/LocalizationProvider';
import { appI18n } from '../../localization/i18n';
import type { LiveAppPort } from './LiveAppPort';
import { LiveDiagnosticsScreen } from './LiveDiagnosticsScreen';

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

const validPreview = (
  overrides: Partial<DiagnosticsPreview> = {},
): DiagnosticsPreview => ({
  buildLabel: '0.1.0 test',
  androidOrIosVersionLabel: 'Android 16',
  capabilityCodes: ['distribution-channel-unapproved'],
  transitionCount: 4,
  schedulerHeartbeatAt: '2026-07-19T06:59:00Z' as UtcInstant,
  earliestEventAt: '2026-07-10T07:00:00Z' as UtcInstant,
  latestEventAt: '2026-07-12T08:30:00Z' as UtcInstant,
  excludesPrivateContent: true,
  ...overrides,
});

const deferred = <Value,>() => {
  let resolve!: (value: Value) => void;
  const promise = new Promise<Value>(resolver => {
    resolve = resolver;
  });
  return { promise, resolve };
};

type DiagnosticsHarness = Readonly<{
  emit(event: ProjectionInvalidation): void;
  port: LiveAppPort;
  previewDiagnostics: jest.Mock;
  shareDiagnostics: jest.Mock;
}>;

const createHarness = ({
  previewDiagnostics = jest.fn(async () => ok(validPreview(), revision('37'))),
  shareDiagnostics = jest.fn(async () =>
    ok({ kind: 'cancelled' as const }, revision('38')),
  ),
}: Partial<{
  previewDiagnostics: jest.Mock;
  shareDiagnostics: jest.Mock;
}> = {}): DiagnosticsHarness => {
  const listeners = new Set<(event: ProjectionInvalidation) => void>();
  const port = {
    previewDiagnostics,
    shareDiagnostics,
    subscribeInvalidations: (
      listener: (event: ProjectionInvalidation) => void,
    ) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
  } as unknown as LiveAppPort;
  return {
    emit: event => listeners.forEach(listener => listener(event)),
    port,
    previewDiagnostics,
    shareDiagnostics,
  };
};

const renderDiagnostics = async (port: LiveAppPort) =>
  await render(
    <LocalizationProvider>
      <ThemeProvider initialPreference="light">
        <LiveDiagnosticsScreen onBack={jest.fn()} port={port} />
      </ThemeProvider>
    </LocalizationProvider>,
  );

const handlerFromFiber = (initialFiber: Fiber | null): unknown => {
  let fiber = initialFiber;
  while (fiber?.memoizedProps) {
    const props = fiber.memoizedProps as Record<string, unknown>;
    if (typeof props.onPress === 'function') return props.onPress;
    if (fiber.return === null || typeof fiber.return.type === 'string') {
      return undefined;
    }
    fiber = fiber.return;
  }
  return undefined;
};

// This mirrors fireEvent's composite-fiber lookup without awaiting the
// intentionally deferred native promise.
const getAsyncButtonHandler = (
  initialInstance: TestInstance,
): (() => Promise<void>) => {
  let instance: TestInstance | null = initialInstance;
  while (instance) {
    const handler =
      typeof instance.props.onPress === 'function'
        ? instance.props.onPress
        : handlerFromFiber(instance.unstable_fiber);
    if (typeof handler === 'function') {
      return handler as () => Promise<void>;
    }
    instance = instance.parent;
  }
  throw new Error('Missing async Button press handler');
};

let appStateListeners: Array<(state: AppStateStatus) => void> = [];

beforeEach(() => {
  jest.clearAllMocks();
  appStateListeners = [];
  jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation((_type, listener) => {
      appStateListeners.push(listener);
      return { remove: jest.fn() };
    });
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

it('withholds Share and technical codes until a valid PII-free preview is reviewed', async () => {
  const harness = createHarness();
  await renderDiagnostics(harness.port);

  expect(await screen.findByTestId('live-diagnostics-preview')).toBeTruthy();
  expect(screen.queryByTestId('live-diagnostics-review')).toBeNull();
  expect(screen.queryByTestId('live-diagnostics-share')).toBeNull();
  expect(screen.queryByText('distribution-channel-unapproved')).toBeNull();
  expect(screen.getAllByText(/excludes names, phone numbers/u)).toHaveLength(1);

  await fireEvent.press(screen.getByTestId('live-diagnostics-preview'));

  expect(await screen.findByTestId('live-diagnostics-review')).toBeTruthy();
  expect(screen.getByText('distribution-channel-unapproved')).toBeTruthy();
  await fireEvent.press(screen.getByTestId('live-diagnostics-share'));

  await waitFor(() =>
    expect(harness.shareDiagnostics).toHaveBeenCalledWith({
      expectedRevision: revision('37'),
    }),
  );
  expect(harness.shareDiagnostics).toHaveBeenCalledTimes(1);
  expect(
    await screen.findByText('Diagnostics sharing was cancelled.'),
  ).toBeTruthy();
});

it.each([
  [
    'a false PII-exclusion assertion',
    { ...validPreview(), excludesPrivateContent: false },
  ],
  [
    'a non-array code collection',
    { ...validPreview(), capabilityCodes: 'not-an-array' },
  ],
  ['a malformed code', { ...validPreview(), capabilityCodes: [42] }],
  [
    'an unknown code',
    { ...validPreview(), capabilityCodes: ['unknown-diagnostics-code'] },
  ],
  [
    'an invalid scheduler timestamp',
    { ...validPreview(), schedulerHeartbeatAt: '2026-99-19T06:59:00Z' },
  ],
  [
    'an invalid earliest-event timestamp',
    { ...validPreview(), earliestEventAt: 42 },
  ],
  [
    'an invalid latest-event timestamp',
    { ...validPreview(), latestEventAt: 'not-an-instant' },
  ],
  ['an empty build label', { ...validPreview(), buildLabel: '   ' }],
  [
    'an empty system label',
    { ...validPreview(), androidOrIosVersionLabel: '' },
  ],
] as const)(
  'fails closed when native preview returns %s',
  async (_description, malformedPreview) => {
    const previewDiagnostics = jest.fn(async () =>
      ok(malformedPreview, revision('37')),
    );
    const harness = createHarness({ previewDiagnostics });
    await renderDiagnostics(harness.port);

    await fireEvent.press(
      await screen.findByTestId('live-diagnostics-preview'),
    );

    expect(await screen.findByText(/NATIVE_CONTRACT_INVALID/u)).toBeTruthy();
    expect(screen.queryByTestId('live-diagnostics-review')).toBeNull();
    expect(screen.queryByTestId('live-diagnostics-share')).toBeNull();
    expect(screen.getByTestId('live-diagnostics-preview')).toBeTruthy();
    expect(harness.shareDiagnostics).not.toHaveBeenCalled();
  },
);

it.each(['invalidation', 'AppState'] as const)(
  'retires a valid preview after %s changes protected truth',
  async invalidationSource => {
    const harness = createHarness();
    await renderDiagnostics(harness.port);
    await fireEvent.press(
      await screen.findByTestId('live-diagnostics-preview'),
    );
    expect(await screen.findByTestId('live-diagnostics-share')).toBeTruthy();

    await act(async () => {
      if (invalidationSource === 'invalidation') {
        harness.emit({ revision: revision('38'), areas: ['activity'] });
      } else {
        appStateListeners.forEach(listener => listener('active'));
      }
    });

    expect(screen.queryByTestId('live-diagnostics-review')).toBeNull();
    expect(screen.queryByTestId('live-diagnostics-share')).toBeNull();
    expect(screen.getByTestId('live-diagnostics-preview')).toBeTruthy();
    expect(harness.shareDiagnostics).not.toHaveBeenCalled();
  },
);

it('does not materialize a late preview after native invalidation', async () => {
  const pendingPreview =
    deferred<Awaited<ReturnType<LiveAppPort['previewDiagnostics']>>>();
  const previewDiagnostics = jest.fn(() => pendingPreview.promise);
  const harness = createHarness({ previewDiagnostics });
  await renderDiagnostics(harness.port);

  const previewButton = await screen.findByTestId('live-diagnostics-preview');
  let previewRequest!: Promise<void>;
  await act(() => {
    previewRequest = getAsyncButtonHandler(previewButton)();
  });
  await waitFor(() => expect(previewDiagnostics).toHaveBeenCalledTimes(1));
  await act(async () => {
    harness.emit({ revision: revision('38'), areas: ['activity'] });
  });

  await act(async () => {
    pendingPreview.resolve(ok(validPreview(), revision('37')));
    await previewRequest;
  });

  expect(screen.queryByTestId('live-diagnostics-review')).toBeNull();
  expect(screen.queryByTestId('live-diagnostics-share')).toBeNull();
  expect(screen.getByTestId('live-diagnostics-preview')).toBeTruthy();
  expect(harness.shareDiagnostics).not.toHaveBeenCalled();
});

it('collapses duplicate Preview and Share presses into one native call each', async () => {
  const pendingPreview =
    deferred<Awaited<ReturnType<LiveAppPort['previewDiagnostics']>>>();
  const previewDiagnostics = jest.fn(() => pendingPreview.promise);
  const pendingShare =
    deferred<Awaited<ReturnType<LiveAppPort['shareDiagnostics']>>>();
  const shareDiagnostics = jest.fn(() => pendingShare.promise);
  const harness = createHarness({ previewDiagnostics, shareDiagnostics });
  await renderDiagnostics(harness.port);

  const previewButton = await screen.findByTestId('live-diagnostics-preview');
  let previewRequests!: readonly [Promise<void>, Promise<void>];
  await act(() => {
    const prepare = getAsyncButtonHandler(previewButton);
    previewRequests = [prepare(), prepare()];
  });
  expect(previewDiagnostics).toHaveBeenCalledTimes(1);

  await act(async () => {
    pendingPreview.resolve(ok(validPreview(), revision('37')));
    await Promise.all(previewRequests);
  });
  const shareButton = await screen.findByTestId('live-diagnostics-share');
  let shareRequests!: readonly [Promise<void>, Promise<void>];
  await act(() => {
    const share = getAsyncButtonHandler(shareButton);
    shareRequests = [share(), share()];
  });
  expect(shareDiagnostics).toHaveBeenCalledTimes(1);
  expect(shareDiagnostics).toHaveBeenCalledWith({
    expectedRevision: revision('37'),
  });

  await act(async () => {
    pendingShare.resolve(ok({ kind: 'shared' }, revision('38')));
    await Promise.all(shareRequests);
  });
  expect(
    await screen.findByText('The diagnostics share sheet opened.'),
  ).toBeTruthy();
  expect(previewDiagnostics).toHaveBeenCalledTimes(1);
  expect(shareDiagnostics).toHaveBeenCalledTimes(1);
});
