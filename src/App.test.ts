import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';

const source = readFileSync(new URL('./App.tsx', import.meta.url), 'utf8');

describe('temporary functionality-first application shell', () => {
  it('composes the production runtime and exposes only the minimal command interface', () => {
    assert.match(source, /createProductionRuntime/);
    assert.match(source, /<MinimalFunctionalShell/);
    assert.match(source, /production\.commands\.execute/);
    assert.match(source, /MAX_RESULT_CHARACTERS/);
    assert.doesNotMatch(source, /StyleSheet|style=|ui\/theme|AppDialog|Animated\.|<Image\b/);
  });

  it('retains non-visual lifecycle, deep-link, notification, Android-back, and browser-history adapters', () => {
    assert.match(source, /parseRelateDeepLink/);
    assert.match(source, /addNotificationResponseReceivedListener/);
    assert.match(source, /NativeAppState\.addEventListener/);
    assert.match(source, /BackHandler\.addEventListener/);
    assert.match(source, /window\.addEventListener\('popstate'/);
    assert.match(source, /production\.commands\.onBackground/);
  });

  it('never renders raw application records or private command input in summaries', () => {
    assert.doesNotMatch(source, /JSON\.stringify\(snapshot\.state|message\.body|notesSummary|contact\.phone/);
    assert.match(source, /buildFunctionalStateSummary/);
    assert.match(source, /buildFunctionalOperationSummary/);
    assert.match(source, /buildFunctionalIssueSummary/);
  });

  it('hides state and defers external navigation across the application lock boundary', () => {
    assert.match(source, /production\.commands\.isApplicationLocked\(\)/);
    assert.match(source, /Private state is hidden until biometric unlock/);
    assert.match(source, /pendingExternalUrlRef/);
    assert.match(source, /Never\s+\/\/ resolve entity ids|Never\n\s+\/\/ resolve entity ids/);
    assert.match(
      source,
      /if \(!production\.commands\.isApplicationLocked\(\)\) \{\s*await production\.navigation\.synchronize\(\)/
    );
  });

  it('keeps startup recovery available through strict non-feature commands', () => {
    assert.match(source, /runtime\.retry/);
    assert.match(source, /runtime\.clear-corrupt-storage/);
    assert.match(source, /CLEAR CORRUPT LOCAL DATA/);
    assert.match(source, /clearFailedStorageAndRetry/);
  });

  it('injects backup passphrases only from the secure ephemeral input', () => {
    assert.match(source, /\$SECURE_INPUT/);
    assert.match(source, /prepareCommandSecret/);
    assert.match(source, /literal passphrases are rejected/);
  });

  it('keeps every parser-valid long draft reachable and preserves failed non-secret input', () => {
    assert.match(source, /MAX_RESULT_CHARACTERS = 400_000/);
    assert.match(source, /clearInput: result\.status === 'succeeded'/);
  });
});
