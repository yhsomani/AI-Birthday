import type { AppState, PermissionPromptOutcome, PermissionUserIntent, PrivacyState } from '../domain/types';
import type { PermissionRequestResult, RequestableSystemPermissionCapability } from '../native/permissionRequest';
import {
  createPermissionAuthorizationRecords,
  permissionDecisionsFromRecords,
  recordPermissionPromptOutcome,
  recordPermissionUserIntent,
  systemPermissionCapabilities,
  type PermissionAuthorizationRecords
} from './permissionReminderCoordinator';

export interface ExplicitPermissionRequest {
  capability: RequestableSystemPermissionCapability;
  userIntent: Extract<PermissionUserIntent, 'allow' | 'decline'>;
}

export type PermissionRequestPersistencePhase = 'intent' | 'prompt-outcome';

export interface PermissionRequestCoordinatorDependencies {
  requestPermission(capability: RequestableSystemPermissionCapability): Promise<PermissionRequestResult>;
  now?: () => Date;
  onPermissionStateChanged?: (
    records: PermissionAuthorizationRecords,
    decisions: PrivacyState['permissionDecisions'],
    phase: PermissionRequestPersistencePhase
  ) => void | Promise<void>;
  onError?: (capability: RequestableSystemPermissionCapability, error: unknown) => void | Promise<void>;
}

export interface ExplicitPermissionRequestResult {
  status: 'granted' | 'limited' | 'denied' | 'restricted' | 'undetermined' | 'declined' | 'request-failed';
  capability: RequestableSystemPermissionCapability;
  records: PermissionAuthorizationRecords;
  decisions: PrivacyState['permissionDecisions'];
  request?: PermissionRequestResult;
}

const cloneRecords = (records: PermissionAuthorizationRecords): PermissionAuthorizationRecords =>
  Object.fromEntries(
    systemPermissionCapabilities.map(capability => [capability, { ...records[capability] }])
  ) as PermissionAuthorizationRecords;

export class PermissionRequestCoordinator {
  private records?: PermissionAuthorizationRecords;
  private tail: Promise<void> = Promise.resolve();

  constructor(private readonly dependencies: PermissionRequestCoordinatorDependencies) {}

  private nowIso() {
    return (this.dependencies.now ?? (() => new Date()))().toISOString();
  }

  private enqueue<T>(work: () => Promise<T>): Promise<T> {
    const result = this.tail.then(work, work);
    this.tail = result.then(
      () => undefined,
      () => undefined
    );
    return result;
  }

  private ensureRecords(state: AppState) {
    this.records ??= createPermissionAuthorizationRecords(state.privacy);
    return this.records;
  }

  private snapshot(state: AppState) {
    const records = cloneRecords(this.ensureRecords(state));
    return {
      records,
      decisions: permissionDecisionsFromRecords(records, state.privacy.permissionDecisions)
    };
  }

  private async publish(state: AppState, phase: PermissionRequestPersistencePhase) {
    const snapshot = this.snapshot(state);
    await this.dependencies.onPermissionStateChanged?.(snapshot.records, snapshot.decisions, phase);
    return snapshot;
  }

  request(state: AppState, request: ExplicitPermissionRequest): Promise<ExplicitPermissionRequestResult> {
    return this.enqueue(async () => {
      this.records = recordPermissionUserIntent(
        this.ensureRecords(state),
        request.capability,
        request.userIntent,
        this.nowIso()
      );
      const intentSnapshot = await this.publish(state, 'intent');

      if (request.userIntent === 'decline') {
        return {
          status: 'declined',
          capability: request.capability,
          ...intentSnapshot
        };
      }

      try {
        const nativeResult = await this.dependencies.requestPermission(request.capability);
        this.records = recordPermissionPromptOutcome(
          this.records,
          request.capability,
          nativeResult.outcome as PermissionPromptOutcome,
          nativeResult.promptedAt
        );
        this.records = {
          ...this.records,
          [request.capability]: {
            ...this.records[request.capability],
            canAskAgain: nativeResult.canAskAgain,
            platformStatus: nativeResult.platformStatus,
            systemCheckedAt: nativeResult.promptedAt,
            queryIssue: undefined
          }
        };
        const outcomeSnapshot = await this.publish(state, 'prompt-outcome');
        return {
          status: nativeResult.outcome,
          capability: request.capability,
          ...outcomeSnapshot,
          request: nativeResult
        };
      } catch (error) {
        await this.dependencies.onError?.(request.capability, error);
        return {
          status: 'request-failed',
          capability: request.capability,
          ...this.snapshot(state)
        };
      }
    });
  }
}
