import React, { useEffect, useReducer, useRef, useState, useSyncExternalStore } from 'react';
import * as Notifications from 'expo-notifications';
import { AppState as NativeAppState, BackHandler, Linking, Platform } from 'react-native';
import { MinimalFunctionalShell, type FunctionalCommandExample } from './app/MinimalFunctionalShell';
import { createProductionRuntime, type ProductionRuntime } from './application/createProductionRuntime';
import {
  buildFunctionalIssueSummary,
  buildFunctionalOperationSummary,
  buildFunctionalStateSummary
} from './application/functionalSummary';
import type { CommandExecutionResult } from './application/commandRuntimeTypes';
import { parseRelateDeepLink, resolveDeepLinkDestination } from './domain/deepLinks';
import { readNotificationRouteUrl } from './domain/notificationRoutes';
import { buildBrowserNavigationHistoryState, readBrowserNavigationHistoryState } from './navigation/navigationState';
import { AppErrorBoundary } from './ui/AppErrorBoundary';

// Command validation already caps private query output at 96 KiB and message
// bodies/variants at 10,000 characters each. Keep the only review surface
// above that valid envelope so a long draft never becomes unreachable.
const MAX_RESULT_CHARACTERS = 400_000;
const CLEAR_FAILED_STORAGE_CONFIRMATION = 'CLEAR CORRUPT LOCAL DATA';
const SECURE_COMMAND_SECRET_PLACEHOLDER = '$SECURE_INPUT';
const sensitiveSecretCommands = new Set(['backup.export', 'backup.restore-preview', 'backup.restore-preview-selected']);

type RuntimeRecoveryResult = Readonly<{
  status: 'succeeded' | 'failed';
  kind: 'runtime-retry' | 'corrupt-storage-clear';
  phase: string;
  summary: string;
}>;

const parseRuntimeRecoveryCommand = (
  raw: string
): { type: 'runtime.retry' } | { type: 'runtime.clear-corrupt-storage'; confirmation: string } | undefined => {
  if (raw.length === 0 || raw.length > 512) return undefined;
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    if (value.type === 'runtime.retry' && Object.keys(value).length === 1) return { type: 'runtime.retry' };
    if (
      value.type === 'runtime.clear-corrupt-storage' &&
      Object.keys(value).length === 2 &&
      typeof value.confirmation === 'string' &&
      value.confirmation.length <= 64
    ) {
      return { type: value.type, confirmation: value.confirmation };
    }
  } catch {
    return undefined;
  }
  return undefined;
};

const prepareCommandSecret = (
  raw: string,
  secret: string
): { ok: true; command: string } | { ok: false; message: string } => {
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    if (typeof value.type !== 'string' || !sensitiveSecretCommands.has(value.type)) {
      return { ok: true, command: raw };
    }
    if (value.passphrase !== SECURE_COMMAND_SECRET_PLACEHOLDER) {
      return {
        ok: false,
        message: 'Use the secure secret field and the $SECURE_INPUT placeholder; literal passphrases are rejected.'
      };
    }
    if (!secret) return { ok: false, message: 'Enter the backup passphrase in the secure secret field.' };
    return { ok: true, command: JSON.stringify({ ...value, passphrase: secret }) };
  } catch {
    return { ok: true, command: raw };
  }
};

const commandExamples: readonly FunctionalCommandExample[] = [
  {
    id: 'system.catalog',
    description: 'List every strict command and the core preview, review, recovery, and backup workflows.',
    input: JSON.stringify({ type: 'system.catalog' })
  },
  {
    id: 'home.inspect',
    description: 'Inspect current metrics, upcoming work, readiness, backup state, and next actions.',
    input: JSON.stringify({ type: 'home.inspect' })
  },
  {
    id: 'contacts.query',
    description: 'Find active contact ids before opening private detail or editing a profile.',
    input: JSON.stringify({ type: 'contacts.query', sort: 'Name', limit: 20 })
  },
  {
    id: 'events.query',
    description: 'Review events and occurrence-specific preparation state.',
    input: JSON.stringify({ type: 'events.query', sort: 'Date', limit: 20 })
  },
  {
    id: 'messages.query',
    description: 'Review live inbox counts and actionable draft or recovery rows.',
    input: JSON.stringify({
      type: 'messages.query',
      tab: 'Review',
      channel: 'All',
      query: '',
      sort: 'Scheduled',
      limit: 20
    })
  },
  {
    id: 'setup.inspect',
    description: 'Inspect production setup without exposing configured endpoints or relationship content.',
    input: JSON.stringify({ type: 'setup.inspect' })
  },
  {
    id: 'permissions.refresh',
    description: 'Read current system authorization without opening a permission prompt.',
    input: JSON.stringify({ type: 'permissions.refresh' })
  },
  {
    id: 'contacts.import',
    description: 'Review live authorization, request access when eligible, and import exact contact identities.',
    input: JSON.stringify({ type: 'contacts.import' })
  },
  {
    id: 'calendar.import',
    description: 'Import supported relationship events from the device calendar.',
    input: JSON.stringify({ type: 'calendar.import' })
  },
  {
    id: 'events.import-file',
    description: 'Choose a bounded CSV or vCard and stage every candidate for review before applying it.',
    input: JSON.stringify({ type: 'events.import-file' })
  },
  {
    id: 'calendar.export',
    description: 'Reconcile the owned recurring calendar series.',
    input: JSON.stringify({ type: 'calendar.export' })
  },
  {
    id: 'reminders.reconcile',
    description: 'Recompute reminder plans and reconcile only app-owned notifications.',
    input: JSON.stringify({ type: 'reminders.reconcile', reason: 'manual' })
  },
  {
    id: 'event.add',
    description: 'Add a manual event through the same validated reducer used by production.',
    input: JSON.stringify({
      type: 'domain.dispatch',
      action: {
        type: 'addManualEvent',
        eventType: 'Birthday',
        label: 'Birthday',
        date: '2026-12-31',
        newContactName: 'Functional test contact',
        confirmConflict: false
      }
    })
  },
  {
    id: 'biometric.unlock',
    description: 'Open a one-shot biometric authorization window for a sensitive command.',
    input: JSON.stringify({ type: 'biometric.unlock' })
  },
  {
    id: 'analytics.inspect',
    description: 'Return secondary aggregate, redacted relationship metrics.',
    input: JSON.stringify({ type: 'analytics.inspect', range: 'Last 30 days' })
  },
  {
    id: 'runtime.retry',
    description: 'Retry opening protected storage after a transient startup failure.',
    input: JSON.stringify({ type: 'runtime.retry' })
  },
  {
    id: 'runtime.clear-corrupt-storage',
    description: 'Destructively clear unrecoverable app-owned storage only after explicit confirmation.',
    input: JSON.stringify({
      type: 'runtime.clear-corrupt-storage',
      confirmation: CLEAR_FAILED_STORAGE_CONFIRMATION
    })
  }
];

const formatCommandResult = (result: CommandExecutionResult | RuntimeRecoveryResult) => {
  const serialized = JSON.stringify(result, null, 2);
  if (serialized.length <= MAX_RESULT_CHARACTERS) return serialized;
  return JSON.stringify({
    status: result.status,
    summary: 'The redacted command result exceeded the temporary console output limit.'
  });
};

const executionOutput = (
  result: CommandExecutionResult | RuntimeRecoveryResult
): Readonly<{ output: string; clearInput: boolean }> => ({
  output: formatCommandResult(result),
  clearInput: result.status === 'succeeded'
});

const reportLifecycleFailure = (production: ProductionRuntime) => {
  production.issues.report({
    code: 'persistence-failed',
    severity: 'blocking',
    summary: 'The runtime could not finish a lifecycle persistence step.',
    recovery: 'retry'
  });
};

export default function App() {
  const [production] = useState(createProductionRuntime);
  const snapshot = useSyncExternalStore(
    production.runtime.subscribe,
    production.runtime.getSnapshot,
    production.runtime.getSnapshot
  );
  const [externalRevision, refreshExternalSummaries] = useReducer(value => value + 1, 0);
  const [shellEpoch, resetSensitiveShell] = useReducer(value => value + 1, 0);
  const browserHistoryDepthRef = useRef(0);
  const pendingExternalUrlRef = useRef<string | undefined>(undefined);
  const routeExternalUrlRef = useRef<(url: string) => void>(() => undefined);

  useEffect(() => {
    const unsubscribeOperations = production.commands.subscribeOperations(refreshExternalSummaries);
    const unsubscribeIssues = production.issues.subscribe(refreshExternalSummaries);
    return () => {
      unsubscribeOperations();
      unsubscribeIssues();
    };
  }, [production]);

  useEffect(() => {
    const writeBrowserNavigationState = (mode: 'push' | 'replace') => {
      if (Platform.OS !== 'web' || typeof window === 'undefined') return;
      const value = buildBrowserNavigationHistoryState(
        window.history.state,
        production.navigation.snapshot(),
        browserHistoryDepthRef.current
      );
      if (mode === 'push') window.history.pushState(value, '');
      else window.history.replaceState(value, '');
    };

    const navigateFromUrl = async (url: string) => {
      if (production.commands.isApplicationLocked()) {
        // Retain only the latest opaque route until the user unlocks. Never
        // resolve entity ids or mutate private navigation state while locked.
        pendingExternalUrlRef.current = url;
        production.issues.report({
          code: 'navigation-link-failed',
          severity: 'warning',
          summary: 'A requested destination is waiting for biometric unlock.',
          recovery: 'none'
        });
        return;
      }
      const parsed = parseRelateDeepLink(url);
      const requested = parsed.ok ? parsed.destination : parsed.fallback;
      const resolved = resolveDeepLinkDestination(production.runtime.getSnapshot().state, requested);
      if (!parsed.ok || !resolved.ok) {
        production.issues.report({
          code: 'navigation-link-failed',
          severity: 'warning',
          summary: 'A link target was invalid or unavailable, so a safe fallback was selected.',
          recovery: 'none'
        });
      } else {
        production.issues.resolveCode('navigation-link-failed');
      }
      const transition = await production.navigation.navigate(resolved.destination);
      if (transition.outcome.changed) {
        browserHistoryDepthRef.current += 1;
        writeBrowserNavigationState('push');
      }
    };

    const startThenNavigate = (url: string | null | undefined) => {
      if (!url) return;
      void production.runtime
        .start()
        .then(() => navigateFromUrl(url))
        .catch(() => undefined);
    };
    routeExternalUrlRef.current = url => startThenNavigate(url);

    void production.runtime
      .start()
      .then(async () => {
        if (!production.commands.isApplicationLocked()) {
          await production.navigation.synchronize();
          writeBrowserNavigationState('replace');
        }
        const [initialUrl, notification] = await Promise.all([
          Linking.getInitialURL(),
          Notifications.getLastNotificationResponseAsync()
        ]);
        startThenNavigate(initialUrl);
        startThenNavigate(readNotificationRouteUrl(notification?.notification.request.content.data));
      })
      .catch(() => undefined);

    const linkSubscription = Linking.addEventListener('url', event => startThenNavigate(event.url));
    const notificationSubscription = Notifications.addNotificationResponseReceivedListener(response => {
      startThenNavigate(readNotificationRouteUrl(response.notification.request.content.data));
    });
    const appStateSubscription = NativeAppState.addEventListener('change', nextState => {
      if (nextState === 'active') {
        void production.runtime.setVisibility('foreground').catch(() => reportLifecycleFailure(production));
        return;
      }
      production.commands.onBackground();
      resetSensitiveShell();
      void production.runtime.setVisibility('background').catch(() => reportLifecycleFailure(production));
    });
    const backSubscription =
      Platform.OS === 'android'
        ? BackHandler.addEventListener('hardwareBackPress', () => {
            if (production.commands.isApplicationLocked()) return false;
            const transition = production.navigation.back('android-hardware');
            if (transition.outcome.back?.disposition === 'consumed') {
              void production.navigation.commit(transition).catch(() => reportLifecycleFailure(production));
              return true;
            }
            return false;
          })
        : undefined;

    const handleBrowserHistory = (event: PopStateEvent) => {
      if (production.commands.isApplicationLocked()) return;
      const state = production.runtime.getSnapshot().state;
      const entities = {
        contactIds: state.contacts.filter(contact => !contact.archivedAt).map(contact => contact.id),
        messages: state.messages.map(message => ({ id: message.id, contactId: message.contactId })),
        events: state.events.map(event => ({ id: event.id, contactId: event.contactId }))
      };
      const restored = readBrowserNavigationHistoryState(event.state, entities);
      if (restored) {
        browserHistoryDepthRef.current = restored.depth;
        void production.navigation.restore(restored.navigation).catch(() => reportLifecycleFailure(production));
        return;
      }
      const transition = production.navigation.back('browser-history');
      if (transition.outcome.changed) {
        void production.navigation.commit(transition).catch(() => reportLifecycleFailure(production));
      }
    };
    if (Platform.OS === 'web' && typeof window !== 'undefined') {
      window.addEventListener('popstate', handleBrowserHistory);
    }

    return () => {
      linkSubscription.remove();
      notificationSubscription.remove();
      appStateSubscription.remove();
      backSubscription?.remove();
      if (Platform.OS === 'web' && typeof window !== 'undefined') {
        window.removeEventListener('popstate', handleBrowserHistory);
      }
      production.commands.onBackground();
      routeExternalUrlRef.current = () => undefined;
      void production.runtime.flush().catch(() => reportLifecycleFailure(production));
    };
  }, [production]);

  useEffect(() => {
    if (snapshot.phase !== 'ready' || production.commands.isApplicationLocked()) return;
    void production.navigation.synchronize().catch(() => reportLifecycleFailure(production));
  }, [
    externalRevision,
    production,
    snapshot.phase,
    snapshot.state.contacts,
    snapshot.state.events,
    snapshot.state.messages
  ]);

  // The revision is intentionally consumed here; operation and issue queues
  // publish it independently of the persisted runtime snapshot.
  const applicationLocked = snapshot.phase === 'ready' && production.commands.isApplicationLocked();
  const stateSummary = applicationLocked
    ? ['Private state is hidden until biometric unlock.']
    : buildFunctionalStateSummary(snapshot.state);
  const operationSummary = buildFunctionalOperationSummary(production.commands.operationSnapshots());
  const issueSummary = buildFunctionalIssueSummary(production.issues.active());

  return (
    <AppErrorBoundary onOperationalIssue={issue => production.issues.report(issue)}>
      <MinimalFunctionalShell
        key={shellEpoch}
        examples={commandExamples}
        locale={snapshot.state.settings.locale}
        execute={async (raw, secret) => {
          const recovery = parseRuntimeRecoveryCommand(raw);
          if (recovery?.type === 'runtime.retry') {
            const recovered = await production.runtime.retryFailedStart();
            return executionOutput({
              status: recovered.phase === 'ready' ? 'succeeded' : 'failed',
              kind: 'runtime-retry',
              phase: recovered.phase,
              summary:
                recovered.phase === 'ready'
                  ? 'Protected storage opened successfully.'
                  : 'Protected storage is still unavailable; no local data was changed.'
            });
          }
          if (recovery?.type === 'runtime.clear-corrupt-storage') {
            if (recovery.confirmation !== CLEAR_FAILED_STORAGE_CONFIRMATION) {
              return executionOutput({
                status: 'failed',
                kind: 'corrupt-storage-clear',
                phase: snapshot.phase,
                summary: 'The destructive recovery confirmation did not match. No local data was changed.'
              });
            }
            const recovered = await production.runtime.clearFailedStorageAndRetry();
            return executionOutput({
              status: recovered.phase === 'ready' ? 'succeeded' : 'failed',
              kind: 'corrupt-storage-clear',
              phase: recovered.phase,
              summary:
                recovered.phase === 'ready'
                  ? 'Unrecoverable app-owned storage was cleared and a fresh local state opened.'
                  : 'Storage recovery did not complete; the runtime remains fail-closed.'
            });
          }
          if (snapshot.phase === 'failed') {
            return {
              output: JSON.stringify({
                status: 'failed',
                summary: 'Protected storage is fail-closed. Run runtime.retry or confirmed corrupt-storage recovery.'
              }),
              clearInput: false
            };
          }
          const prepared = prepareCommandSecret(raw, secret);
          if (!prepared.ok) {
            return {
              output: JSON.stringify({ status: 'invalid', summary: prepared.message }),
              clearInput: false
            };
          }
          const result = await production.commands.execute(prepared.command);
          if (!production.commands.isApplicationLocked() && pendingExternalUrlRef.current) {
            const pendingUrl = pendingExternalUrlRef.current;
            pendingExternalUrlRef.current = undefined;
            routeExternalUrlRef.current(pendingUrl);
          }
          return executionOutput(result);
        }}
        issues={issueSummary}
        operations={operationSummary}
        phase={applicationLocked ? 'locked' : snapshot.phase}
        summary={stateSummary}
      />
    </AppErrorBoundary>
  );
}
