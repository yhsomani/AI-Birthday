import type {
  ActivityPage,
  ActivityQuery,
  DiagnosticsPreview,
} from '../../domain/activity/model';
import type { ReadinessIssue } from '../../domain/readiness/model';
import type { NativeRevision } from '../../domain/shared/brand';
import type { NativeResult } from '../../domain/shared/result';

export interface ActivityPort {
  listActivity(query: ActivityQuery): Promise<NativeResult<ActivityPage>>;
  listIssues(): Promise<NativeResult<readonly ReadinessIssue[]>>;
  previewDiagnostics(): Promise<NativeResult<DiagnosticsPreview>>;
  shareDiagnostics(input: {
    expectedRevision: NativeRevision;
  }): Promise<NativeResult<{ kind: 'shared' | 'cancelled' }>>;
}
