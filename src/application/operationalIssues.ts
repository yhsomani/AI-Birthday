export type OperationalIssueCode =
  | 'storage-unavailable'
  | 'persistence-failed'
  | 'data-lifecycle-recovery-required'
  | 'reminder-reconciliation-failed'
  | 'widget-sync-failed'
  | 'permission-refresh-failed'
  | 'navigation-link-failed'
  | 'provider-delivery-unknown'
  | 'unexpected-ui-error';

export type OperationalIssueSeverity = 'warning' | 'blocking';

export type OperationalIssue = Readonly<{
  id: string;
  correlationId: string;
  code: OperationalIssueCode;
  severity: OperationalIssueSeverity;
  occurredAt: string;
  summary: string;
  recovery: 'retry' | 'open-settings' | 'reconcile' | 'restart-screen' | 'none';
  attempts: number;
  resolvedAt?: string;
}>;

export type OperationalIssueInput = Pick<OperationalIssue, 'code' | 'severity' | 'summary' | 'recovery'>;

export type OperationalIssueDependencies = {
  now(): string;
  createId(): string;
};

const MAX_ACTIVE_ISSUES = 50;
const MAX_RESOLVED_ISSUES = 20;
const MAX_SUMMARY_LENGTH = 180;
const privateContentPattern = /(?:https?:\/\/|[\w.+-]+@[\w.-]+\.[a-z]{2,}|\+?\d[\d\s().-]{7,}\d)/i;

const safeSummary = (summary: string) => {
  const normalized = summary.replace(/\s+/g, ' ').trim().slice(0, MAX_SUMMARY_LENGTH);
  return normalized && !privateContentPattern.test(normalized)
    ? normalized
    : 'An operational step needs attention. No relationship content was recorded.';
};

export class OperationalIssueQueue {
  private issues: OperationalIssue[] = [];
  private listeners = new Set<() => void>();

  constructor(private readonly dependencies: OperationalIssueDependencies) {}

  report(input: OperationalIssueInput): OperationalIssue {
    const existing = this.issues.find(issue => issue.code === input.code && !issue.resolvedAt);
    if (existing) {
      const updated = Object.freeze({
        ...existing,
        severity: input.severity,
        summary: safeSummary(input.summary),
        recovery: input.recovery,
        occurredAt: this.dependencies.now(),
        attempts: existing.attempts + 1
      });
      this.issues = [updated, ...this.issues.filter(issue => issue.id !== existing.id)];
      this.emit();
      return updated;
    }

    const id = this.dependencies.createId();
    const issue = Object.freeze({
      id,
      correlationId: `relateai-${id}`,
      code: input.code,
      severity: input.severity,
      occurredAt: this.dependencies.now(),
      summary: safeSummary(input.summary),
      recovery: input.recovery,
      attempts: 1
    });
    this.issues = [
      issue,
      ...this.issues.filter(item => !item.resolvedAt).slice(0, MAX_ACTIVE_ISSUES - 1),
      ...this.issues.filter(item => item.resolvedAt).slice(0, MAX_RESOLVED_ISSUES)
    ];
    this.emit();
    return issue;
  }

  resolve(id: string): OperationalIssue | undefined {
    const current = this.issues.find(issue => issue.id === id && !issue.resolvedAt);
    if (!current) return undefined;
    const resolved = Object.freeze({ ...current, resolvedAt: this.dependencies.now() });
    this.issues = this.issues.map(issue => (issue.id === id ? resolved : issue));
    this.emit();
    return resolved;
  }

  resolveCode(code: OperationalIssueCode): OperationalIssue | undefined {
    const current = this.issues.find(issue => issue.code === code && !issue.resolvedAt);
    return current ? this.resolve(current.id) : undefined;
  }

  active(): readonly OperationalIssue[] {
    return this.issues.filter(issue => !issue.resolvedAt);
  }

  snapshot(): readonly OperationalIssue[] {
    return this.issues.map(issue => Object.freeze({ ...issue }));
  }

  subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit() {
    for (const listener of [...this.listeners]) listener();
  }
}

let fallbackIssueSequence = 0;
export const appOperationalIssues = new OperationalIssueQueue({
  now: () => new Date().toISOString(),
  createId: () => globalThis.crypto?.randomUUID?.() ?? `issue-${Date.now()}-${++fallbackIssueSequence}`
});
