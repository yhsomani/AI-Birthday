export function safeErrorCategory(error: unknown): string {
  if (error instanceof TypeError) {
    return 'TypeError';
  }
  if (error instanceof RangeError) {
    return 'RangeError';
  }
  if (error instanceof SyntaxError) {
    return 'SyntaxError';
  }
  return error instanceof Error ? 'Error' : 'Unknown';
}
