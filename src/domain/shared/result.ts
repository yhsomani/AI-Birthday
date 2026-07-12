import type { IssueId, NativeRevision, SafeSupportCode } from './brand';
import type { SafeReasonCode } from './reasonCodes';
import type { UtcInstant } from './temporal';

export type FieldName =
  | 'birthday'
  | 'confirmation'
  | 'dailyCap'
  | 'phone'
  | 'sim'
  | 'template'
  | 'window';

export type FieldIssue = Readonly<{
  field: FieldName;
  code: SafeReasonCode;
}>;

export type NativeProblem =
  | Readonly<{ kind: 'cancelled'; source: 'user' | 'system' }>
  | Readonly<{ kind: 'stale-revision'; latestRevision: NativeRevision }>
  | Readonly<{ kind: 'validation'; issues: readonly FieldIssue[] }>
  | Readonly<{ kind: 'action-required'; issueIds: readonly IssueId[] }>
  | Readonly<{
      kind: 'temporarily-unavailable';
      code: SafeReasonCode;
      retryAfterSeconds?: number | undefined;
    }>
  | Readonly<{ kind: 'conflict'; code: SafeReasonCode }>
  | Readonly<{ kind: 'unsupported'; code: SafeReasonCode }>
  | Readonly<{ kind: 'internal'; supportCode: SafeSupportCode }>;

export type ProjectionEnvelope<Value> = Readonly<{
  contractVersion: 1;
  revision: NativeRevision;
  generatedAt: UtcInstant;
  value: Value;
}>;

export type NativeResult<Value> =
  | Readonly<{ kind: 'ok'; envelope: ProjectionEnvelope<Value> }>
  | Readonly<{ kind: 'error'; problem: NativeProblem }>;

export type UiDraftValidation<Value> =
  | Readonly<{
      kind: 'valid';
      authority: 'ui-only';
      value: Value;
    }>
  | Readonly<{
      kind: 'invalid';
      authority: 'ui-only';
      issues: readonly FieldIssue[];
    }>;
