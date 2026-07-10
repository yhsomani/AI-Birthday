export const throwIfAborted = (signal: AbortSignal | undefined): void => {
  if (!signal?.aborted) return;
  if (signal.reason instanceof Error) throw signal.reason;
  const error = new Error('The operation was cancelled.');
  error.name = 'AbortError';
  throw error;
};
