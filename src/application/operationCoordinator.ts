export type OperationStatus = 'idle' | 'running' | 'succeeded' | 'failed' | 'unknown' | 'cancelled';

export type OperationError = Readonly<{
  code: string;
  retryable: boolean;
  summary: string;
}>;

export type OperationSnapshot = Readonly<{
  scope: string;
  requestId: string;
  status: OperationStatus;
  startedAt: string;
  completedAt?: string;
  attempt: number;
  error?: OperationError;
}>;

export type OperationTaskResult<T> =
  | { status: 'succeeded'; value: T }
  | { status: 'failed'; error: OperationError }
  | { status: 'unknown'; error: OperationError };

export type OperationRunResult<T> =
  OperationTaskResult<T> | { status: 'cancelled' } | { status: 'already-running'; requestId: string };

export type OperationTask<T> = (signal: AbortSignal) => Promise<OperationTaskResult<T>>;

export type OperationCoordinatorDependencies = {
  now(): string;
  createRequestId(): string;
};

type RetryRegistration<T> = {
  requestId: string;
  task: OperationTask<T>;
};

const MAX_SCOPE_LENGTH = 100;
const MAX_ERROR_CODE_LENGTH = 80;
const MAX_ERROR_SUMMARY_LENGTH = 240;
const privateContentPattern = /(?:https?:\/\/|[\w.+-]+@[\w.-]+\.[a-z]{2,}|\+?\d[\d\s().-]{7,}\d)/i;

const sanitizeError = (error: OperationError): OperationError => ({
  code: error.code.trim().slice(0, MAX_ERROR_CODE_LENGTH) || 'operation-failed',
  retryable: error.retryable,
  summary:
    error.summary.trim() && !privateContentPattern.test(error.summary)
      ? error.summary.replace(/\s+/g, ' ').trim().slice(0, MAX_ERROR_SUMMARY_LENGTH)
      : 'The operation did not complete. No relationship content was recorded.'
});

export class OperationCoordinator {
  private snapshots = new Map<string, OperationSnapshot>();
  private controllers = new Map<string, AbortController>();
  private retries = new Map<string, RetryRegistration<unknown>>();
  private listeners = new Set<() => void>();

  constructor(private readonly dependencies: OperationCoordinatorDependencies) {}

  private publish(snapshot: OperationSnapshot) {
    this.snapshots.set(snapshot.scope, Object.freeze(snapshot));
    for (const listener of [...this.listeners]) listener();
  }

  subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  snapshot(scope: string): OperationSnapshot | undefined {
    const current = this.snapshots.get(scope);
    return current ? Object.freeze({ ...current }) : undefined;
  }

  all(): readonly OperationSnapshot[] {
    return [...this.snapshots.values()].map(snapshot => Object.freeze({ ...snapshot }));
  }

  async run<T>(
    scope: string,
    task: OperationTask<T>,
    options: { cancelPrevious?: boolean; requestId?: string } = {}
  ): Promise<OperationRunResult<T>> {
    const normalizedScope = scope.trim();
    if (!normalizedScope || normalizedScope.length > MAX_SCOPE_LENGTH) {
      throw new Error('Operation scope is invalid.');
    }
    const active = this.snapshots.get(normalizedScope);
    if (active?.status === 'running') {
      if (!options.cancelPrevious) return { status: 'already-running', requestId: active.requestId };
      this.controllers.get(normalizedScope)?.abort();
    }

    const requestId = options.requestId?.trim() || this.dependencies.createRequestId();
    const attempt = (active?.attempt ?? 0) + 1;
    const controller = new AbortController();
    this.controllers.set(normalizedScope, controller);
    this.publish({
      scope: normalizedScope,
      requestId,
      status: 'running',
      startedAt: this.dependencies.now(),
      attempt
    });

    let result: OperationTaskResult<T>;
    try {
      result = await task(controller.signal);
    } catch {
      result = {
        status: 'failed',
        error: {
          code: 'unexpected-error',
          retryable: true,
          summary: 'The operation stopped unexpectedly.'
        }
      };
    }

    if (this.controllers.get(normalizedScope) !== controller || controller.signal.aborted) {
      return { status: 'cancelled' };
    }
    this.controllers.delete(normalizedScope);

    if (result.status === 'succeeded') {
      this.retries.delete(normalizedScope);
      this.publish({
        scope: normalizedScope,
        requestId,
        status: 'succeeded',
        startedAt: this.snapshots.get(normalizedScope)?.startedAt ?? this.dependencies.now(),
        completedAt: this.dependencies.now(),
        attempt
      });
      return result;
    }

    const error = sanitizeError(result.error);
    if (result.status === 'failed' && error.retryable) {
      this.retries.set(normalizedScope, { requestId, task: task as OperationTask<unknown> });
    } else {
      this.retries.delete(normalizedScope);
    }
    this.publish({
      scope: normalizedScope,
      requestId,
      status: result.status,
      startedAt: this.snapshots.get(normalizedScope)?.startedAt ?? this.dependencies.now(),
      completedAt: this.dependencies.now(),
      attempt,
      error
    });
    return { status: result.status, error };
  }

  cancel(scope: string): boolean {
    const controller = this.controllers.get(scope);
    if (!controller) return false;
    controller.abort();
    this.controllers.delete(scope);
    const current = this.snapshots.get(scope);
    if (current) {
      this.publish({ ...current, status: 'cancelled', completedAt: this.dependencies.now() });
    }
    return true;
  }

  retry<T>(scope: string): Promise<OperationRunResult<T>> {
    const registration = this.retries.get(scope);
    if (!registration) {
      return Promise.resolve({
        status: 'failed',
        error: { code: 'retry-unavailable', retryable: false, summary: 'This operation cannot be retried safely.' }
      });
    }
    return this.run(scope, registration.task as OperationTask<T>, { requestId: registration.requestId });
  }
}
