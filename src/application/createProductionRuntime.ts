import { buildHomeWidgetSummary } from '../domain/homeWidget';
import { evaluateProviderEndpointReadiness } from '../domain/providerEndpointReadiness';
import { requestAiDraft, readAiProviderConfig } from '../native/aiProviderClient';
import { authenticateWithBiometrics } from '../native/biometricAuth';
import {
  exportEncryptedBackupFile,
  pickEncryptedBackupFile,
  restoreEncryptedBackupFile,
  saveEncryptedBackupFile
} from '../native/backupFiles';
import { exportEventsToDeviceCalendar, importEventsFromDeviceCalendar } from '../native/calendarBridge';
import { openManualHandoffTarget } from '../native/channelHandoffBridge';
import { shareAnalyticsCsv, shareAnalyticsSummary } from '../native/analyticsSharing';
import { importDeviceContacts } from '../native/contactImporter';
import { pickEventImportFile } from '../native/eventImportFiles';
import { readEmailSenderConfig, reconcileEmailDelivery, sendEmailMessage } from '../native/emailSenderClient';
import { loadDefaultMigratingEntityRepository } from '../native/encryptedEntityStore';
import { clearState } from '../state/persistence';
import {
  createEntityRepositoryPersistenceAdapter,
  loadEntityRepositoryState,
  type EntityRepositoryStatePort
} from '../state/entityRepositoryPersistence';
import { PersistenceCoordinator } from '../state/persistenceCoordinator';
import { relateReducer } from '../state/relateReducer';
import { clearHomeWidgetSummary, syncHomeWidgetSummary } from '../native/homeWidgetBridge';
import { cancelOwnedReminderNotifications, reconcileReminderPlansWithoutPrompt } from '../native/reminderScheduler';
import { secureStateStore } from '../native/secureStateStore';
import { localizeHomeWidgetSummary } from '../ui/homeWidgetPresentation';
import { AppRuntimeController } from './appRuntimeController';
import { HarnessCommandRuntime } from './commandRuntime';
import {
  clearLocalDataTransaction,
  DATA_LIFECYCLE_JOURNAL_KEY,
  recoverInterruptedDataLifecycle,
  restoreLocalDataTransaction,
  type DataLifecycleDependencies
} from './dataLifecycle';
import { DataLifecycleRecoveryCoordinator } from './dataLifecycleRecovery';
import { createNativePermissionReminderCoordinator } from './nativePermissionReminderCoordinator';
import { createNativePermissionRequestCoordinator } from './nativePermissionRequestCoordinator';
import { NavigationRuntimeController } from './navigationRuntime';
import { appOperationalIssues } from './operationalIssues';
import { OperationCoordinator } from './operationCoordinator';
import { permissionDecisionsFromRecords } from './permissionReminderCoordinator';
import {
  readValidatedProviderSession,
  unavailableProviderSessions,
  type ProviderSessionSource
} from './providerSessions';

let fallbackRequestSequence = 0;
const createRequestId = () => globalThis.crypto?.randomUUID?.() ?? `request-${Date.now()}-${++fallbackRequestSequence}`;

const syncPrivacyFilteredWidget = async (state: Parameters<typeof buildHomeWidgetSummary>[0]) => {
  await syncHomeWidgetSummary(localizeHomeWidgetSummary(buildHomeWidgetSummary(state), state.settings.locale));
};

export type ProductionRuntimeOptions = {
  providerSessions?: ProviderSessionSource;
};

export const createProductionRuntime = (options: ProductionRuntimeOptions = {}) => {
  const providerSessions = options.providerSessions ?? unavailableProviderSessions;
  const nowIso = () => new Date().toISOString();
  const repositoryPromise = loadDefaultMigratingEntityRepository();
  const repository: EntityRepositoryStatePort = {
    loadState: async () => (await repositoryPromise).loadState(),
    replaceState: async state => (await repositoryPromise).replaceState(state),
    inspect: async () => (await repositoryPromise).inspect(),
    pruneRollbackGenerations: async () => (await repositoryPromise).pruneRollbackGenerations(),
    destroyAllData: async () => {
      const target = await repositoryPromise;
      if (!target.destroyAllData) throw new Error('Protected repository erasure is unavailable.');
      await target.destroyAllData();
    }
  };
  const persistence = new PersistenceCoordinator(
    createEntityRepositoryPersistenceAdapter({ repository: repositoryPromise, nowIso })
  );
  const dataLifecycleReference = {} as { current: DataLifecycleDependencies };
  const dataLifecycleRecovery = new DataLifecycleRecoveryCoordinator({
    store: secureStateStore,
    recover: () => recoverInterruptedDataLifecycle(dataLifecycleReference.current),
    issues: appOperationalIssues
  });

  const runtimeReference = {} as { current: AppRuntimeController };
  const permissionReminders = createNativePermissionReminderCoordinator({
    onPermissionRecordsChanged: async records => {
      const current = runtimeReference.current.getSnapshot().state;
      await runtimeReference.current.dispatchAndCommit({
        type: 'permissionsReconciled',
        records,
        decisions: permissionDecisionsFromRecords(records, current.privacy.permissionDecisions)
      });
    },
    onReminderPlansChanged: async plans => {
      await runtimeReference.current.dispatchAndCommit({ type: 'reminderPlansReconciled', plans });
    },
    onError: stage => {
      appOperationalIssues.report({
        code: stage === 'permission-query' ? 'permission-refresh-failed' : 'reminder-reconciliation-failed',
        severity: 'warning',
        summary:
          stage === 'permission-query'
            ? 'Live system authorization could not be refreshed.'
            : 'Owned reminder notifications could not be reconciled.',
        recovery: 'reconcile'
      });
    }
  });

  const runtime = new AppRuntimeController({
    loadState: async () => {
      await dataLifecycleRecovery.reconcile();
      return loadEntityRepositoryState(repositoryPromise, nowIso);
    },
    resetFailedStorage: async () => {
      await cancelOwnedReminderNotifications();
      await clearHomeWidgetSummary();
      const target = await repositoryPromise;
      if (!target.destroyAllData) {
        throw new Error('Protected repository recovery is unavailable. No local data was removed.');
      }
      await target.destroyAllData();
      await secureStateStore.removeItem(DATA_LIFECYCLE_JOURNAL_KEY);
    },
    persistence,
    reduce: relateReducer,
    permissionReminders,
    syncWidget: syncPrivacyFilteredWidget,
    issues: appOperationalIssues
  });
  runtimeReference.current = runtime;

  const operations = new OperationCoordinator({
    now: () => new Date().toISOString(),
    createRequestId
  });
  const permissionRequests = createNativePermissionRequestCoordinator({
    onPermissionStateChanged: async (records, decisions) => {
      await runtime.dispatchAndCommit({ type: 'permissionsReconciled', records, decisions });
    },
    onError: capability => {
      appOperationalIssues.report({
        code: 'permission-refresh-failed',
        severity: 'warning',
        summary: `${capability} authorization could not be requested. No unrelated capability was changed.`,
        recovery: 'open-settings'
      });
    }
  });
  const navigation = new NavigationRuntimeController({
    getState: () => runtime.getSnapshot().state,
    dispatchRoute: async destination => {
      await runtime.dispatchAndCommit({ type: 'navigate', ...destination });
    }
  });

  const dataLifecycleDependencies: DataLifecycleDependencies = {
    store: secureStateStore,
    repository,
    clearLegacyState: () => clearState(secureStateStore),
    nowIso,
    createId: createRequestId,
    cancelOwnedReminders: cancelOwnedReminderNotifications,
    clearHomeWidget: clearHomeWidgetSummary,
    // Temporary share/import files are deleted by the backup service in a
    // finally block. There is no persistent cache inventory to sweep here.
    cleanupTemporaryBackups: async () => undefined,
    reconcileReminders: reconcileReminderPlansWithoutPrompt,
    syncHomeWidget: syncPrivacyFilteredWidget
  };
  dataLifecycleReference.current = dataLifecycleDependencies;

  const commands = new HarnessCommandRuntime({
    getState: () => runtime.getSnapshot().state,
    dispatch: async action => {
      if (action.type === 'navigate') {
        await navigation.navigate(action);
        return;
      }
      await runtime.dispatchAndCommit(action);
      await navigation.synchronize();
    },
    installVerifiedState: state => runtime.installVerifiedState(state),
    runDataReplacement: operation => runtime.runDataReplacement(operation),
    operations,
    createConfirmationToken: createRequestId,
    now: () => new Date(),
    importContacts: signal => importDeviceContacts({ signal }),
    importCalendar: signal => importEventsFromDeviceCalendar({ signal }),
    exportCalendar: (state, request, signal) =>
      exportEventsToDeviceCalendar(state, {
        signal,
        ...(request.mode === 'selected' ? { eventIds: request.eventIds } : {})
      }),
    pickEventImportFile: async signal => {
      if (signal.aborted) throw signal.reason instanceof Error ? signal.reason : new Error('Operation cancelled.');
      const selected = await pickEventImportFile();
      if (signal.aborted) throw signal.reason instanceof Error ? signal.reason : new Error('Operation cancelled.');
      return selected ? { name: selected.name, raw: selected.raw } : undefined;
    },
    reconcileReminders: async (state, reason) => {
      switch (reason) {
        case 'hydration':
          return permissionReminders.afterHydration(state);
        case 'foreground':
          return permissionReminders.onForeground(state);
        case 'events-committed':
          return permissionReminders.afterCommittedChange(state, 'events');
        case 'settings-committed':
          return permissionReminders.afterCommittedChange(state, 'settings');
        case 'manual':
        case 'permission-change':
          return permissionReminders.afterPermissionStatusChange(state);
      }
    },
    requestAiDraft: async (request, signal) => {
      if (signal.aborted) throw new Error('Operation cancelled.');
      const config = readAiProviderConfig();
      const session = await readValidatedProviderSession(providerSessions, 'ai', signal);
      const response = await requestAiDraft(
        request,
        {
          ...config,
          sessionAccessToken: session?.accessToken,
          sessionExpiresAt: session?.expiresAt
        },
        undefined,
        signal
      );
      if (signal.aborted) throw new Error('Operation cancelled.');
      return response;
    },
    // Email delivery is intentionally allowed to settle even when a caller
    // loses interest; its idempotent result must be committed or marked unknown.
    sendEmail: async (request, signal) => {
      const config = readEmailSenderConfig();
      const session = await readValidatedProviderSession(providerSessions, 'email', signal);
      return sendEmailMessage(request, {
        ...config,
        sessionAccessToken: session?.accessToken,
        sessionExpiresAt: session?.expiresAt
      });
    },
    // Reconciliation is idempotent and must be allowed to settle after the
    // provider request starts so an unknown delivery is never retried blindly.
    reconcileEmail: async (attempt, signal) => {
      const config = readEmailSenderConfig();
      const session = await readValidatedProviderSession(providerSessions, 'email', signal);
      return reconcileEmailDelivery(attempt, {
        ...config,
        sessionAccessToken: session?.accessToken,
        sessionExpiresAt: session?.expiresAt
      });
    },
    openHandoff: input => openManualHandoffTarget(input),
    exportBackup: (state, passphrase, destination) =>
      destination === 'share'
        ? exportEncryptedBackupFile(state, passphrase)
        : saveEncryptedBackupFile(state, passphrase),
    selectBackup: async signal => {
      if (signal.aborted) throw new Error('Operation cancelled.');
      const selected = await pickEncryptedBackupFile();
      if (signal.aborted) throw new Error('Operation cancelled.');
      return selected;
    },
    decryptBackup: async (raw, passphrase, signal) => {
      if (signal.aborted) throw new Error('Operation cancelled.');
      const state = await restoreEncryptedBackupFile(raw, passphrase);
      if (signal.aborted) throw new Error('Operation cancelled.');
      return state;
    },
    restoreData: async restoredState => {
      try {
        const result = await restoreLocalDataTransaction(dataLifecycleDependencies, restoredState);
        if (result.status === 'reconciliation-required') {
          dataLifecycleRecovery.reportRequired();
        } else {
          await dataLifecycleRecovery.reconcile();
        }
        return result;
      } catch (error) {
        await dataLifecycleRecovery.reportRequiredIfJournalPresent();
        throw error;
      }
    },
    clearData: async previousState => {
      try {
        const clearedState = await clearLocalDataTransaction(dataLifecycleDependencies, previousState);
        await dataLifecycleRecovery.reconcile();
        return clearedState;
      } catch (error) {
        await dataLifecycleRecovery.reportRequiredIfJournalPresent();
        throw error;
      }
    },
    recoverDataLifecycle: signal => {
      if (signal.aborted) throw new Error('Operation cancelled.');
      return dataLifecycleRecovery
        .reconcile(async () => {
          const authoritativeState = await repository.loadState();
          if (!authoritativeState) {
            throw new Error('Recovered protected storage did not contain a verified application state.');
          }
          runtime.installVerifiedState(authoritativeState);
        })
        .then(result => {
          if (signal.aborted) throw new Error('Operation cancelled.');
          return result;
        });
    },
    refreshPermissions: async state => (await permissionReminders.onForeground(state)).records,
    preflightPermission: (state, capability) => permissionReminders.beforeOperation(state, capability),
    requestPermission: (state, request) => permissionRequests.request(state, request),
    authenticateBiometric: async signal => {
      if (signal.aborted) throw new Error('Operation cancelled.');
      const authenticated = await authenticateWithBiometrics();
      if (signal.aborted) throw new Error('Operation cancelled.');
      return authenticated;
    },
    shareAnalyticsSummary: (summary, signal) => shareAnalyticsSummary(summary, signal),
    shareAnalyticsCsv: (csv, signal) => shareAnalyticsCsv(csv, signal),
    setupEnvironment: async () => {
      const ai = readAiProviderConfig();
      const email = readEmailSenderConfig();
      const [aiProviderSessionReady, emailProviderSessionReady] = await Promise.all([
        providerSessions.hasActiveSession('ai'),
        providerSessions.hasActiveSession('email')
      ]);
      return {
        aiEndpointReadiness: evaluateProviderEndpointReadiness(ai.endpoint, {
          allowLocalDevelopment: ai.allowLocalProviderEndpoint
        }),
        emailEndpointReadiness: evaluateProviderEndpointReadiness(email.endpoint, {
          allowLocalDevelopment: email.allowLocalProviderEndpoint
        }),
        aiProviderSessionReady,
        emailProviderSessionReady
      };
    }
  });

  return {
    runtime,
    commands,
    navigation,
    operations,
    permissionReminders,
    permissionRequests,
    persistence,
    dataLifecycleRecovery,
    issues: appOperationalIssues
  };
};

export type ProductionRuntime = ReturnType<typeof createProductionRuntime>;
