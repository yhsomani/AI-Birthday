import { buildReminderPlanningResult } from '../domain/reminders';
import {
  buildOwnedNotificationPlans,
  validateOwnedNotificationPlanForState,
  type OwnedNotificationPlan
} from '../domain/notificationPlans';
import type {
  AppState,
  PermissionAuthorizationRecord,
  PermissionDecision,
  PermissionPromptOutcome,
  PermissionUserIntent,
  PrivacyState,
  ReminderPlan,
  SystemAuthorization,
  SystemPermissionCapability
} from '../domain/types';
import type {
  BiometricLiveCapabilityStatus,
  LivePermissionSnapshot,
  LivePermissionStatus
} from '../native/permissionStatus';

export const systemPermissionCapabilities: readonly SystemPermissionCapability[] = [
  'Contacts',
  'Notifications',
  'Calendar',
  'Biometric lock'
];

export type PermissionAuthorizationRecords = Record<SystemPermissionCapability, PermissionAuthorizationRecord>;

export type PermissionRefreshReason =
  | 'hydration'
  | 'foreground'
  | 'before-operation'
  | 'permission-change'
  | 'committed-event-change'
  | 'committed-contact-change'
  | 'committed-message-change'
  | 'committed-setup-change'
  | 'committed-backup-change'
  | 'committed-settings-change';

/** State aggregates whose verified persistence can change the desired native reminder set. */
export type ReminderAffectingCommittedChange = 'events' | 'contacts' | 'messages' | 'setup' | 'backups' | 'settings';

type ReminderLifecycleRefreshReason = Exclude<PermissionRefreshReason, 'before-operation'>;

export type AppVisibility = 'foreground' | 'background';

export interface ReminderNativeReconciliationResult {
  scheduled: number;
  skipped: number;
  cancelled: number;
  unchanged: number;
  authorization?: 'authorized' | 'not-authorized';
}

export interface PermissionReminderCoordinatorDependencies {
  /** Read-only: implementations must not call any request/authenticate API. */
  readPermissionSnapshot(): Promise<LivePermissionSnapshot>;
  /** Read-only permission check plus diff-based reconciliation of owned notifications. */
  reconcileReminderNotifications(plans: OwnedNotificationPlan[]): Promise<ReminderNativeReconciliationResult>;
  now?: () => Date;
  onPermissionRecordsChanged?: (
    records: PermissionAuthorizationRecords,
    reason: PermissionRefreshReason
  ) => void | Promise<void>;
  onReminderPlansChanged?: (plans: ReminderPlan[]) => void | Promise<void>;
  onError?: (
    stage: 'permission-query' | 'reminder-reconciliation',
    error: unknown,
    reason: PermissionRefreshReason
  ) => void | Promise<void>;
}

export interface ReminderLifecycleResult {
  status: 'reconciled' | 'deferred-background' | 'permission-status-unavailable' | 'reconciliation-failed';
  reason: ReminderLifecycleRefreshReason;
  records: PermissionAuthorizationRecords;
  plannedReminders: ReminderPlan[];
  plannedNotifications?: OwnedNotificationPlan[];
  desiredNativeReminders: OwnedNotificationPlan[];
  blockedNativeNotificationCount?: number;
  nativeResult?: ReminderNativeReconciliationResult;
}

export interface PermissionOperationCheck {
  capability: SystemPermissionCapability;
  allowed: boolean;
  authorization: SystemAuthorization;
  checkedAt?: string;
  record: PermissionAuthorizationRecord;
  records: PermissionAuthorizationRecords;
}

const legacyRecord = (
  capability: SystemPermissionCapability,
  decision: PermissionDecision
): PermissionAuthorizationRecord => {
  if (decision === 'Granted') {
    return {
      capability,
      userIntent: 'allow',
      lastPromptOutcome: 'granted',
      systemAuthorization: 'granted',
      lastKnownAuthorization: 'granted'
    };
  }
  if (decision === 'Denied') {
    return {
      capability,
      userIntent: 'not-expressed',
      lastPromptOutcome: 'denied',
      systemAuthorization: 'denied',
      lastKnownAuthorization: 'denied'
    };
  }
  if (decision === 'Unavailable') {
    return {
      capability,
      userIntent: 'not-expressed',
      systemAuthorization: 'unavailable'
    };
  }
  return {
    capability,
    userIntent: 'not-expressed',
    systemAuthorization: 'undetermined',
    lastKnownAuthorization: 'undetermined'
  };
};

const recordIsForCapability = (
  value: PermissionAuthorizationRecord | undefined,
  capability: SystemPermissionCapability
): value is PermissionAuthorizationRecord => value?.capability === capability;

export const createPermissionAuthorizationRecords = (
  privacy: Pick<PrivacyState, 'permissionDecisions' | 'permissionRecords'>
): PermissionAuthorizationRecords =>
  Object.fromEntries(
    systemPermissionCapabilities.map(capability => {
      const stored = privacy.permissionRecords?.[capability];
      return [
        capability,
        recordIsForCapability(stored, capability)
          ? { ...legacyRecord(capability, privacy.permissionDecisions[capability]), ...stored }
          : legacyRecord(capability, privacy.permissionDecisions[capability])
      ];
    })
  ) as PermissionAuthorizationRecords;

const cloneRecords = (records: PermissionAuthorizationRecords): PermissionAuthorizationRecords =>
  Object.fromEntries(
    systemPermissionCapabilities.map(capability => [capability, { ...records[capability] }])
  ) as PermissionAuthorizationRecords;

const permissionRecordFromLive = (
  previous: PermissionAuthorizationRecord,
  live: LivePermissionStatus,
  checkedAt: string,
  platformStatus?: string
): PermissionAuthorizationRecord => ({
  ...previous,
  systemAuthorization: live.state,
  lastKnownAuthorization: live.state === 'unavailable' ? previous.lastKnownAuthorization : live.state,
  systemCheckedAt: checkedAt,
  canAskAgain: live.canAskAgain,
  platformStatus: platformStatus ?? live.rawStatus,
  queryIssue: live.issue
});

const biometricRecordFromLive = (
  previous: PermissionAuthorizationRecord,
  live: BiometricLiveCapabilityStatus,
  checkedAt: string
): PermissionAuthorizationRecord => {
  const authorization: SystemAuthorization =
    live.state === 'granted' ? 'granted' : live.state === 'not-enrolled' ? 'not-enrolled' : 'unavailable';
  return {
    ...previous,
    systemAuthorization: authorization,
    lastKnownAuthorization: authorization === 'unavailable' ? previous.lastKnownAuthorization : authorization,
    systemCheckedAt: checkedAt,
    platformStatus: live.securityLevel ? `${live.reason}; security=${live.securityLevel}` : live.reason,
    queryIssue: live.reason === 'query-failed' ? 'query-failed' : undefined
  };
};

export const reconcilePermissionSnapshot = (
  records: PermissionAuthorizationRecords,
  snapshot: LivePermissionSnapshot
): PermissionAuthorizationRecords => ({
  Contacts: permissionRecordFromLive(
    records.Contacts,
    snapshot.contacts,
    snapshot.checkedAt,
    snapshot.contacts.accessPrivileges
      ? `${snapshot.contacts.rawStatus ?? snapshot.contacts.state}; access=${snapshot.contacts.accessPrivileges}`
      : snapshot.contacts.rawStatus
  ),
  Notifications: permissionRecordFromLive(
    records.Notifications,
    snapshot.notifications,
    snapshot.checkedAt,
    snapshot.notifications.iosAuthorization
      ? `${snapshot.notifications.rawStatus ?? snapshot.notifications.state}; ios=${snapshot.notifications.iosAuthorization}`
      : snapshot.notifications.rawStatus
  ),
  Calendar: permissionRecordFromLive(records.Calendar, snapshot.calendar, snapshot.checkedAt),
  'Biometric lock': biometricRecordFromLive(records['Biometric lock'], snapshot.biometric, snapshot.checkedAt)
});

export const recordPermissionUserIntent = (
  records: PermissionAuthorizationRecords,
  capability: SystemPermissionCapability,
  userIntent: PermissionUserIntent,
  at: string
): PermissionAuthorizationRecords => ({
  ...records,
  [capability]: {
    ...records[capability],
    userIntent,
    userIntentUpdatedAt: at
  }
});

export const recordPermissionPromptOutcome = (
  records: PermissionAuthorizationRecords,
  capability: SystemPermissionCapability,
  outcome: PermissionPromptOutcome,
  at: string
): PermissionAuthorizationRecords => ({
  ...records,
  [capability]: {
    ...records[capability],
    userIntent: records[capability].userIntent === 'not-expressed' ? 'allow' : records[capability].userIntent,
    lastPromptOutcome: outcome,
    lastPromptAt: at,
    systemAuthorization: outcome,
    lastKnownAuthorization: outcome
  }
});

export const permissionDecisionForRecord = (record: PermissionAuthorizationRecord): PermissionDecision => {
  if (record.systemAuthorization === 'granted' || record.systemAuthorization === 'limited') {
    return 'Granted';
  }
  if (record.systemAuthorization === 'denied' || record.systemAuthorization === 'restricted') {
    return 'Denied';
  }
  if (record.systemAuthorization === 'unavailable' || record.systemAuthorization === 'not-enrolled') {
    return 'Unavailable';
  }
  return 'Not requested';
};

export const permissionDecisionsFromRecords = (
  records: PermissionAuthorizationRecords,
  current: PrivacyState['permissionDecisions']
): PrivacyState['permissionDecisions'] => {
  const next = { ...current };
  systemPermissionCapabilities.forEach(capability => {
    next[capability] = permissionDecisionForRecord(records[capability]);
  });
  return next;
};

const permissionIsUsable = (authorization: SystemAuthorization) =>
  authorization === 'granted' || authorization === 'limited';

const unavailableSnapshot = (checkedAt: string): LivePermissionSnapshot => {
  const unavailablePermission: LivePermissionStatus = {
    kind: 'permission',
    state: 'unavailable',
    granted: false,
    issue: 'query-failed'
  };
  return {
    schemaVersion: 1,
    checkedAt,
    contacts: { ...unavailablePermission },
    calendar: { ...unavailablePermission },
    notifications: { ...unavailablePermission },
    biometric: {
      kind: 'capability',
      state: 'unavailable',
      ready: false,
      reason: 'query-failed',
      modalities: [],
      rawAuthenticationTypes: [],
      queryComplete: false
    }
  };
};

/**
 * Serializes live permission reads and owned-notification reconciliation. It has
 * no permission-request dependency, so lifecycle/background calls cannot prompt.
 */
export class PermissionReminderCoordinator {
  private records?: PermissionAuthorizationRecords;
  private tail: Promise<void> = Promise.resolve();

  constructor(private readonly dependencies: PermissionReminderCoordinatorDependencies) {}

  private now() {
    return (this.dependencies.now ?? (() => new Date()))();
  }

  private enqueue<T>(work: () => Promise<T>): Promise<T> {
    const result = this.tail.then(work, work);
    this.tail = result.then(
      () => undefined,
      () => undefined
    );
    return result;
  }

  private ensureRecords(state: AppState, replace = false) {
    if (!this.records || replace) {
      this.records = createPermissionAuthorizationRecords(state.privacy);
    }
    return this.records;
  }

  private async refreshPermissions(state: AppState, reason: PermissionRefreshReason, replaceHistory = false) {
    const current = this.ensureRecords(state, replaceHistory);
    let snapshot: LivePermissionSnapshot;
    let queryFailed = false;
    try {
      snapshot = await this.dependencies.readPermissionSnapshot();
    } catch (error) {
      queryFailed = true;
      snapshot = unavailableSnapshot(this.now().toISOString());
      await this.dependencies.onError?.('permission-query', error, reason);
    }
    this.records = reconcilePermissionSnapshot(current, snapshot);
    await this.dependencies.onPermissionRecordsChanged?.(cloneRecords(this.records), reason);
    return { records: this.records, queryFailed };
  }

  private async reconcileLifecycle(
    state: AppState,
    reason: ReminderLifecycleRefreshReason,
    visibility: AppVisibility,
    replaceHistory = false
  ): Promise<ReminderLifecycleResult> {
    this.ensureRecords(state, replaceHistory);
    if (visibility === 'background') {
      const plannedNotifications = buildOwnedNotificationPlans(state, state.reminderPlans, this.now());
      return {
        status: 'deferred-background',
        reason,
        records: cloneRecords(this.records!),
        plannedReminders: state.reminderPlans,
        plannedNotifications,
        desiredNativeReminders: [],
        blockedNativeNotificationCount: plannedNotifications.length
      };
    }

    const refreshed = await this.refreshPermissions(state, reason, replaceHistory);
    const planning = buildReminderPlanningResult(state, [7, 1, 0], this.now());
    if (JSON.stringify(planning.plans) !== JSON.stringify(state.reminderPlans)) {
      await this.dependencies.onReminderPlansChanged?.(planning.plans);
    }

    const plannedNotifications = buildOwnedNotificationPlans(state, planning.plans, this.now());
    const validNotifications = plannedNotifications.filter(
      plan => validateOwnedNotificationPlanForState(state, plan, this.now()).ok
    );
    const blockedNativeNotificationCount = plannedNotifications.length - validNotifications.length;

    const notificationAuthorization = refreshed.records.Notifications.systemAuthorization;
    const schedulingPolicyBlocked = planning.issues.some(issue => issue.severity === 'Error');
    const desiredNativeReminders =
      state.settings.notificationsEnabled && !schedulingPolicyBlocked && permissionIsUsable(notificationAuthorization)
        ? validNotifications
        : [];

    if (state.settings.notificationsEnabled && (notificationAuthorization === 'unavailable' || refreshed.queryFailed)) {
      return {
        status: 'permission-status-unavailable',
        reason,
        records: cloneRecords(refreshed.records),
        plannedReminders: planning.plans,
        plannedNotifications,
        desiredNativeReminders,
        blockedNativeNotificationCount
      };
    }

    try {
      const nativeResult = await this.dependencies.reconcileReminderNotifications(desiredNativeReminders);
      return {
        status: 'reconciled',
        reason,
        records: cloneRecords(refreshed.records),
        plannedReminders: planning.plans,
        plannedNotifications,
        desiredNativeReminders,
        blockedNativeNotificationCount,
        nativeResult
      };
    } catch (error) {
      await this.dependencies.onError?.('reminder-reconciliation', error, reason);
      return {
        status: 'reconciliation-failed',
        reason,
        records: cloneRecords(refreshed.records),
        plannedReminders: planning.plans,
        plannedNotifications,
        desiredNativeReminders,
        blockedNativeNotificationCount
      };
    }
  }

  afterHydration(state: AppState, visibility: AppVisibility = 'foreground'): Promise<ReminderLifecycleResult> {
    return this.enqueue(() => this.reconcileLifecycle(state, 'hydration', visibility, true));
  }

  onForeground(state: AppState): Promise<ReminderLifecycleResult> {
    return this.enqueue(() => this.reconcileLifecycle(state, 'foreground', 'foreground'));
  }

  afterPermissionStatusChange(
    state: AppState,
    visibility: AppVisibility = 'foreground'
  ): Promise<ReminderLifecycleResult> {
    return this.enqueue(() => this.reconcileLifecycle(state, 'permission-change', visibility));
  }

  afterCommittedChange(
    state: AppState,
    change: ReminderAffectingCommittedChange,
    visibility: AppVisibility = 'foreground'
  ): Promise<ReminderLifecycleResult> {
    const reasons: Record<ReminderAffectingCommittedChange, ReminderLifecycleRefreshReason> = {
      events: 'committed-event-change',
      contacts: 'committed-contact-change',
      messages: 'committed-message-change',
      setup: 'committed-setup-change',
      backups: 'committed-backup-change',
      settings: 'committed-settings-change'
    };
    const reason = reasons[change];
    return this.enqueue(() => this.reconcileLifecycle(state, reason, visibility));
  }

  beforeOperation(state: AppState, capability: SystemPermissionCapability): Promise<PermissionOperationCheck> {
    return this.enqueue(async () => {
      const refreshed = await this.refreshPermissions(state, 'before-operation');
      const record = refreshed.records[capability];
      return {
        capability,
        allowed: permissionIsUsable(record.systemAuthorization),
        authorization: record.systemAuthorization,
        checkedAt: record.systemCheckedAt,
        record: { ...record },
        records: cloneRecords(refreshed.records)
      };
    });
  }

  recordUserIntent(
    state: AppState,
    capability: SystemPermissionCapability,
    userIntent: PermissionUserIntent,
    at = this.now().toISOString()
  ) {
    this.records = recordPermissionUserIntent(this.ensureRecords(state), capability, userIntent, at);
    return cloneRecords(this.records);
  }

  recordPromptOutcome(
    state: AppState,
    capability: SystemPermissionCapability,
    outcome: PermissionPromptOutcome,
    at = this.now().toISOString()
  ) {
    this.records = recordPermissionPromptOutcome(this.ensureRecords(state), capability, outcome, at);
    return cloneRecords(this.records);
  }

  currentRecords(state: AppState) {
    return cloneRecords(this.ensureRecords(state));
  }
}
