import type { AccountProjection } from '../../domain/account/model';
import type {
  BirthdayJobProjection,
  TestProjection,
} from '../../domain/automation/model';
import type { ContactId, OccurrenceId } from '../../domain/shared/brand';
import type { HomeProjection } from '../../domain/home/model';
import type {
  DeviceEligibility,
  ReadinessProjection,
} from '../../domain/readiness/model';
import type { NativeResult } from '../../domain/shared/result';
import type {
  BootstrapProjection,
  SetupProjection,
} from '../../domain/setup/model';
import type { ApprovalProjection } from '../../domain/approvals/model';

export const PROJECTION_AREAS = [
  'bootstrap',
  'setup',
  'home',
  'eligibility',
  'readiness',
  'account',
  'contacts',
  'messages',
  'automation',
  'activity',
  'privacy',
  'route',
  'notifications',
] as const;

export type ProjectionArea = (typeof PROJECTION_AREAS)[number];

export type ProjectionInvalidation = Readonly<{
  revision: import('../../domain/shared/brand').NativeRevision;
  areas: readonly ProjectionArea[];
}>;

export interface AppProjectionPort {
  getBootstrap(): Promise<NativeResult<BootstrapProjection>>;
  getSetup(): Promise<NativeResult<SetupProjection>>;
  getHome(): Promise<NativeResult<HomeProjection>>;
  getEligibility(): Promise<NativeResult<DeviceEligibility>>;
  getReadiness(): Promise<NativeResult<ReadinessProjection>>;
  getAccount(): Promise<NativeResult<AccountProjection>>;
  getApproval(contactId: ContactId): Promise<NativeResult<ApprovalProjection>>;
  getBirthdayJob(
    occurrenceId: OccurrenceId,
  ): Promise<NativeResult<BirthdayJobProjection>>;
  getLatestTest(): Promise<NativeResult<TestProjection>>;
  subscribeInvalidations(
    listener: (event: ProjectionInvalidation) => void,
  ): () => void;
}
