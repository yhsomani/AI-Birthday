import type { AnalyticsRange } from '../domain/analytics';
import type {
  AiDraftRequest,
  AiDraftResponseResult,
  AiDraftContextOptions
} from '../domain/aiDrafting';
import type { EmailDeliveryRequest } from '../domain/emailDelivery';
import type { EventImportFormat } from '../domain/eventImport';
import type {
  HandoffExecutionInput,
  HandoffExecutionResult
} from '../domain/channelHandoffExecution';
import type { SetupDoctorEnvironment } from '../domain/setupDoctor';
import type {
  AppState,
  CalendarImportCandidate,
  ComposerReason,
  ImportedContactRecord,
  SystemPermissionCapability
} from '../domain/types';
import type { BackupFileExportResult } from '../native/backupFiles';
import type { EmailSendResult } from '../native/emailSenderClient';
import type { RelateAction } from '../state/relateReducer';
import type { RestoreTransactionResult } from './dataLifecycle';
import type { OperationCoordinator, OperationError, OperationSnapshot } from './operationCoordinator';
import type {
  PermissionAuthorizationRecords,
  PermissionOperationCheck,
  ReminderLifecycleResult
} from './permissionReminderCoordinator';

export type HarnessDomainActionType =
  | 'navigate'
  | 'setSearch'
  | 'toggleChecklist'
  | 'addManualEvent'
  | 'editMessage'
  | 'approveMessage'
  | 'rejectMessage'
  | 'revokeMessage'
  | 'setLocale'
  | 'toggleSetting'
  | 'setQuietHours';

export type HarnessDomainAction = Extract<
  RelateAction,
  { type: HarnessDomainActionType }
>;

export type ReminderRuntimeReason =
  | 'manual'
  | 'hydration'
  | 'foreground'
  | 'permission-change'
  | 'events-committed'
  | 'settings-committed';

export type HarnessCommand =
  | { type: 'domain.dispatch'; action: HarnessDomainAction }
  | { type: 'contacts.import' }
  | { type: 'calendar.import' }
  | { type: 'calendar.export' }
  | { type: 'reminders.reconcile'; reason: ReminderRuntimeReason }
  | {
      type: 'ai.draft';
      contactId: string;
      eventId?: string;
      reason: ComposerReason;
      options: AiDraftContextOptions;
    }
  | { type: 'email.deliver'; messageId: string }
  | { type: 'handoff.open'; messageId: string; preferFallback: boolean }
  | { type: 'handoff.confirm'; messageId: string; sent: boolean }
  | { type: 'events.import-text'; raw: string; format: EventImportFormat }
  | { type: 'backup.export'; passphrase: string; destination: 'share' | 'save' }
  | { type: 'backup.restore'; raw: string; passphrase: string }
  | { type: 'data.clear'; confirmation: 'CLEAR LOCAL DATA' }
  | { type: 'permissions.refresh' }
  | { type: 'permissions.preflight'; capability: SystemPermissionCapability }
  | { type: 'biometric.unlock' }
  | { type: 'analytics.inspect'; range: AnalyticsRange }
  | { type: 'setup.inspect' };

export type CommandParseError = Readonly<{
  code: 'invalid-command' | 'command-too-large';
  summary: string;
}>;

export type CommandParseResult =
  | { ok: true; command: HarnessCommand }
  | { ok: false; error: CommandParseError };

export type RedactedCommandValue =
  | {
      kind: 'domain-action';
      actionType: HarnessDomainActionType;
      stateChanged: boolean;
    }
  | {
      kind: 'contact-import';
      received: number;
      added: number;
      updated: number;
      skipped: number;
    }
  | {
      kind: 'calendar-import';
      received: number;
      addedContacts: number;
      addedEvents: number;
      skipped: number;
    }
  | { kind: 'calendar-export'; reconciled: number }
  | {
      kind: 'reminder-reconciliation';
      status: ReminderLifecycleResult['status'];
      planned: number;
      desiredNative: number;
      scheduled: number;
      cancelled: number;
      unchanged: number;
      skipped: number;
    }
  | {
      kind: 'ai-draft';
      created: true;
      includedMemoryCount: number;
      excludedPrivateMemoryCount: number;
      includedPriorMessageCount: number;
    }
  | { kind: 'email-delivery'; status: 'accepted' | 'sent'; deliveryRecorded: true }
  | {
      kind: 'handoff-open';
      outcome: HandoffExecutionResult['outcome'];
      usedFallback: boolean;
      confirmationRequired: boolean;
    }
  | { kind: 'handoff-confirmation'; markedSent: boolean }
  | {
      kind: 'event-text-import';
      candidates: number;
      addedContacts: number;
      addedEvents: number;
      skipped: number;
      errorCount: number;
    }
  | {
      kind: 'backup-export';
      destination: 'temporary-shared' | 'saved-export';
      shared: boolean;
      temporaryFileRemoved: boolean;
      recordCount: number;
      createdAt: string;
    }
  | {
      kind: 'backup-restore';
      status: RestoreTransactionResult['status'];
      recordCount: number;
      nativeReconciliationRequired: boolean;
    }
  | { kind: 'data-clear'; cleared: true }
  | {
      kind: 'permission-refresh';
      statuses: Array<{
        capability: SystemPermissionCapability;
        authorization: PermissionAuthorizationRecords[SystemPermissionCapability]['systemAuthorization'];
        canAskAgain?: boolean;
      }>;
    }
  | {
      kind: 'permission-preflight';
      capability: SystemPermissionCapability;
      authorization: PermissionOperationCheck['authorization'];
      allowed: boolean;
      canAskAgain?: boolean;
    }
  | { kind: 'biometric-unlock'; unlocked: boolean }
  | {
      kind: 'analytics-inspection';
      range: AnalyticsRange;
      contactCount: number;
      metrics: Array<{ label: string; value: string }>;
      relationshipDistribution: Array<{ label: string; count: number }>;
      healthBuckets: Array<{ label: string; count: number }>;
      overdueContactCount: number;
      insightCount: number;
      empty: boolean;
      redacted: true;
    }
  | {
      kind: 'setup-inspection';
      readyCount: number;
      totalCount: number;
      needsActionCount: number;
      warningCount: number;
      recommendedTitle?: string;
      safe: true;
      redacted: true;
    };

export type CommandExecutionResult =
  | {
      status: 'succeeded';
      commandType: HarnessCommand['type'];
      value: RedactedCommandValue;
      operation: OperationSnapshot;
    }
  | {
      status: 'failed' | 'unknown';
      commandType: HarnessCommand['type'];
      error: OperationError;
      operation: OperationSnapshot;
    }
  | {
      status: 'already-running';
      commandType: HarnessCommand['type'];
      requestId: string;
      operation?: OperationSnapshot;
    }
  | {
      status: 'conflict' | 'cancelled';
      commandType: HarnessCommand['type'];
      error: OperationError;
      operation?: OperationSnapshot;
    }
  | {
      status: 'invalid';
      error: CommandParseError;
    };

export interface CommandRuntimeDependencies {
  getState(): AppState;
  dispatch(action: RelateAction): void | Promise<void>;
  operations: OperationCoordinator;
  now(): Date;
  importContacts(signal: AbortSignal): Promise<ImportedContactRecord[]>;
  importCalendar(signal: AbortSignal): Promise<CalendarImportCandidate[]>;
  exportCalendar(state: AppState, signal: AbortSignal): Promise<number>;
  reconcileReminders(
    state: AppState,
    reason: ReminderRuntimeReason,
    signal: AbortSignal
  ): Promise<ReminderLifecycleResult>;
  requestAiDraft(request: AiDraftRequest, signal: AbortSignal): Promise<AiDraftResponseResult>;
  sendEmail(request: EmailDeliveryRequest, signal: AbortSignal): Promise<EmailSendResult>;
  openHandoff(input: HandoffExecutionInput, signal: AbortSignal): Promise<HandoffExecutionResult>;
  exportBackup(
    state: AppState,
    passphrase: string,
    destination: 'share' | 'save',
    signal: AbortSignal
  ): Promise<BackupFileExportResult>;
  decryptBackup(raw: string, passphrase: string, signal: AbortSignal): Promise<AppState>;
  restoreData(restoredState: AppState, signal: AbortSignal): Promise<RestoreTransactionResult>;
  clearData(previousState: AppState, signal: AbortSignal): Promise<AppState>;
  refreshPermissions(
    state: AppState,
    signal: AbortSignal
  ): Promise<PermissionAuthorizationRecords>;
  preflightPermission(
    state: AppState,
    capability: SystemPermissionCapability,
    signal: AbortSignal
  ): Promise<PermissionOperationCheck>;
  authenticateBiometric(signal: AbortSignal): Promise<boolean>;
  setupEnvironment(signal: AbortSignal): SetupDoctorEnvironment | Promise<SetupDoctorEnvironment>;
}
