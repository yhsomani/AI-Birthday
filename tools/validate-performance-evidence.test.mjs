import assert from 'node:assert/strict';
import test from 'node:test';

import { validatePerformanceEvidence } from './validate-performance-evidence.mjs';

const SHA = 'a'.repeat(64);
const REVISION = 'b'.repeat(40);
const samples = (count, value) => Array.from({ length: count }, () => value);

const validEvidence = platform => ({
  schemaVersion: 1,
  platform,
  sourceRevision: REVISION,
  measuredAt: '2026-07-12T00:00:00Z',
  artifact: {
    applicationId: 'com.yashsomani.birthdayautopilot',
    version: '1.0',
    sha256: SHA,
    signedReleaseLike: true,
  },
  device: {
    model: platform === 'android' ? 'Reference Android' : 'Reference iPhone',
    osVersion: platform === 'android' ? 'Android 16' : 'iOS 26.5',
    ramMiB: platform === 'android' ? 6144 : 4096,
  },
  references: {
    protocolReference: 'evidence/performance-protocol-v1',
    protocolSha256: 'c'.repeat(64),
    rawResultsReference: `evidence/${platform}-raw-v1`,
    rawResultsSha256: 'd'.repeat(64),
  },
  shared: {
    coldStartHomeMs: samples(30, 2000),
    warmHomeMs: samples(30, 800),
    search10000Ms: samples(30, 120),
    normalizeCommit10000WallMs: samples(10, 4200),
    normalizeCommit10000PeakRssMiB: samples(10, 220),
    crashAnrOomCount: 0,
  },
  platformMetrics:
    platform === 'android'
      ? {
          noDueReconcileCpuMs: samples(10, 1500),
          claimArmLatencyMs: samples(100, 2000),
          batteryDeltaPercentagePoints: samples(10, 0.08),
          batteryBenchmarkHours: 24,
        }
      : {
          reminderNoChangeCpuMs: samples(10, 1500),
          reminderReplaceWallMs: samples(10, 1600),
          composerReadyLatencyMs: samples(30, 800),
        },
});

const validate = (document, platform) =>
  validatePerformanceEvidence(document, {
    expectedPlatform: platform,
    expectedSourceRevision: REVISION,
    expectedArtifactSha256: SHA,
    nowMillis: Date.parse('2026-07-12T12:00:00Z'),
  });

test('accepts complete Android and iOS evidence within every binding budget', () => {
  assert.deepEqual(validate(validEvidence('android'), 'android').errors, []);
  assert.deepEqual(validate(validEvidence('ios'), 'ios').errors, []);
});

test('computes nearest-rank tails and rejects a binding budget failure', () => {
  const evidence = validEvidence('android');
  evidence.shared.search10000Ms[28] = 151;
  evidence.shared.search10000Ms[29] = 500;
  const result = validate(evidence, 'android');
  assert.match(result.errors.join('\n'), /search10000Ms P95 151 exceeds 150/u);
});

test('rejects weak samples, stale source, wrong artifact, and debug builds', () => {
  const evidence = validEvidence('ios');
  evidence.shared.coldStartHomeMs = [1];
  evidence.sourceRevision = 'e'.repeat(40);
  evidence.artifact.sha256 = 'f'.repeat(64);
  evidence.artifact.signedReleaseLike = false;
  const result = validate(evidence, 'ios');
  assert.match(result.errors.join('\n'), /30 to 500/u);
  assert.match(result.errors.join('\n'), /does not match the release source/u);
  assert.match(
    result.errors.join('\n'),
    /does not match the measured release artifact/u,
  );
  assert.match(result.errors.join('\n'), /signed release-like artifact/u);
});

test('rejects a platform-specific metric set copied across editions', () => {
  const evidence = validEvidence('ios');
  evidence.platformMetrics = validEvidence('android').platformMetrics;
  const result = validate(evidence, 'ios');
  assert.match(
    result.errors.join('\n'),
    /unsupported field noDueReconcileCpuMs/u,
  );
  assert.match(result.errors.join('\n'), /missing reminderNoChangeCpuMs/u);
});

test('binds evidence to the production application and normalized references', () => {
  const evidence = validEvidence('ios');
  evidence.artifact.applicationId = 'com.yashsomani.birthdayautopilot.dev';
  evidence.references.rawResultsReference = 'evidence/ios-raw-v1 ';
  const result = validate(evidence, 'ios');
  assert.match(
    result.errors.join('\n'),
    /applicationId must be the ios production identifier/u,
  );
  assert.match(result.errors.join('\n'), /rawResultsReference is invalid/u);
});

test('rejects a normalized but nonexistent measurement calendar date', () => {
  const evidence = validEvidence('android');
  evidence.measuredAt = '2026-02-31T00:00:00Z';
  const result = validate(evidence, 'android');
  assert.match(result.errors.join('\n'), /RFC 3339 UTC instant/u);
});
