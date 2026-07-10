import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';

const source = readFileSync(new URL('./AppErrorBoundary.tsx', import.meta.url), 'utf8');

describe('app error boundary component contract', () => {
  it('catches render errors and emits only a typed privacy-safe operational issue', () => {
    assert.match(source, /getDerivedStateFromError/);
    assert.match(source, /componentDidCatch/);
    assert.match(source, /code: 'unexpected-ui-error'/);
    assert.doesNotMatch(source, /error\.message|info\.componentStack/);
  });

  it('renders a minimal accessible recovery surface without visual styling', () => {
    assert.match(source, /accessibilityRole="alert"/);
    assert.match(source, /accessibilityRole="header"/);
    assert.match(source, /accessibilityRole="button"/);
    assert.doesNotMatch(source, /StyleSheet|style=|ui\/theme/);
  });
});
