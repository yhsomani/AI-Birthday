import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';

const source = readFileSync(new URL('./MinimalFunctionalShell.tsx', import.meta.url), 'utf8');

describe('temporary minimal functional shell', () => {
  it('has one bounded command surface and no styling, theme, assets, icons, or animations', () => {
    assert.match(source, /functionalConsole\.commandJson/);
    assert.match(source, /execute\(rawCommand, commandSecret\)/);
    assert.doesNotMatch(source, /\bStyleSheet\b|style=|ui\/theme|\bAnimated\.|<Image\b|iconName=/);
  });

  it('shows only redacted state, operation, issue, and result summaries', () => {
    assert.match(source, /functionalConsole\.stateSummary/);
    assert.match(source, /functionalConsole\.operations/);
    assert.match(source, /functionalConsole\.issues/);
    assert.doesNotMatch(source, /JSON\.stringify\(state|message\.body|notesSummary|phone|email/);
  });

  it('keeps failed non-secret input retryable while always clearing secrets', () => {
    assert.match(source, /if \(execution\.clearInput\) setRawCommand\(''\)/);
    assert.match(source, /setCommandSecret\(''\)/);
    assert.match(source, /secureTextEntry/);
    assert.match(source, /Non-secret text remains available after/);
  });

  it('keeps only the unlock command boundary executable while the runtime reports locked', () => {
    assert.match(source, /phase === 'ready' \|\| phase === 'locked' \|\| phase === 'failed'/);
    assert.match(source, /disabled=\{running \|\| !commandEnabled\}/);
  });
});
