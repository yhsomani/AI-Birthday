import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';

const source = readFileSync(new URL('./MinimalFunctionalShell.tsx', import.meta.url), 'utf8');

describe('temporary minimal functional shell', () => {
  it('has one bounded command surface and no styling, theme, assets, icons, or animations', () => {
    assert.match(source, /functionalConsole\.commandJson/);
    assert.match(source, /execute\(rawCommand, commandSecret\)/);
    assert.match(source, /maxLength=\{maxCommandLength\}/);
    assert.match(source, /maxLength=\{maxSecretLength\}/);
    assert.match(source, /<Pressable/);
    assert.doesNotMatch(source, /functionalConsole\.examples|examples\.map/);
    assert.doesNotMatch(source, /TouchableOpacity|\bStyleSheet\b|style=|ui\/theme|\bAnimated\.|<Image\b|iconName=/);
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

  it('keeps lock-safe recovery commands submittable while the command runtime enforces the lock boundary', () => {
    assert.match(source, /phase === 'ready' \|\| phase === 'locked' \|\| phase === 'failed'/);
    assert.match(source, /disabled=\{running \|\| !commandEnabled\}/);
  });
});
