import type {
  ActivationReview,
  AutomationProjection,
  TestProjection,
  TestReview,
  TodayOccurrenceChoice,
  TodayOccurrenceReview,
} from '../../domain/automation/model';
import type { ApprovalBatchReview } from '../../domain/approvals/model';
import type {
  PolicyEditorProjection,
  PolicyPreview,
  WindowDraft,
} from '../../domain/birthdays/model';
import type {
  ActivationReviewHandle,
  ApprovalReviewHandle,
  ContactId,
  EphemeralPhoneInput,
  NativeRevision,
  OccurrenceId,
  PolicyReviewHandle,
  TestReviewHandle,
  TodayOccurrenceReviewHandle,
} from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';

export interface AutomationPort {
  getPolicyEditor(): Promise<NativeResult<PolicyEditorProjection>>;
  previewPolicy(input: {
    draft: WindowDraft;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<PolicyPreview>>;
  savePolicy(input: {
    handle: PolicyReviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<AutomationProjection>>;
  prepareApprovals(input: {
    contactIds: readonly ContactId[];
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<ApprovalBatchReview>>;
  confirmApprovals(input: {
    handle: ApprovalReviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<AutomationProjection>>;
  prepareTest(input: {
    destination: EphemeralPhoneInput;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<TestReview>>;
  startTest(input: {
    handle: TestReviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<TestProjection>>;
  prepareActivation(): Promise<NativeResult<ActivationReview>>;
  activate(input: {
    handle: ActivationReviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<AutomationProjection>>;
  pauseAll(input: {
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<AutomationProjection>>;
  prepareResume(): Promise<NativeResult<ActivationReview>>;
  resume(input: {
    handle: ActivationReviewHandle;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<AutomationProjection>>;
  prepareTodayOccurrence(input: {
    occurrenceId: OccurrenceId;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<TodayOccurrenceReview>>;
  confirmTodayOccurrence(input: {
    handle: TodayOccurrenceReviewHandle;
    choice: TodayOccurrenceChoice;
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<AutomationProjection>>;
}
