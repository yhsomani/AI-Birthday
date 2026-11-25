export async function runBounded<T>(
  values: readonly T[],
  concurrency: number,
  operation: (value: T) => Promise<void>,
): Promise<void> {
  const requestedConcurrency = Number.isFinite(concurrency)
    ? Math.floor(concurrency)
    : 1;
  const workerCount = Math.min(
    values.length,
    Math.max(1, requestedConcurrency),
  );
  const iterator = values[Symbol.iterator]();
  const results = await Promise.allSettled(
    Array.from({ length: workerCount }, async () => {
      for (;;) {
        const next = iterator.next();
        if (next.done) {
          return;
        }
        await operation(next.value);
      }
    }),
  );
  const failure = results.find(
    (result): result is PromiseRejectedResult => result.status === 'rejected',
  );
  if (failure !== undefined) {
    throw failure.reason;
  }
}
