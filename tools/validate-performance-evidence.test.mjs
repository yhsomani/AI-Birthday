import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import {
  mkdtempSync,
  mkdirSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { symlinksAvailable } from './test-capabilities.mjs';

import {
  validatePerformanceEvidence,
  verifyPerformanceEvidenceReferences,
} from './validate-performance-evidence.mjs';

const SHA = 'a'.repeat(64);
const REVISION = 'b'.repeat(40);
const samples = (count, value) => Array.from({ length: count }, () => value);

const validEvidence = platform => ({
  schemaVersion: 2,
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
    physicalDevice: true,
    deviceIdSha256: '1'.repeat(64),
    installationSource: platform === 'android' ? 'google-play' : 'testflight',
    measurementTool: platform === 'android' ? 'Perfetto' : 'XCTest MetricKit',
    measurementToolVersion: '1.0.0',
  },
  references: {
    protocolReference: 'evidence/performance-protocol-v1',
    protocolSha256: 'c'.repeat(64),
    protocolBytes: 1024,
    rawResultsReference: `evidence/${platform}-raw-v1`,
    rawResultsSha256: 'd'.repeat(64),
    rawResultsBytes: 4096,
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

test.skip('accepts complete Android and iOS evidence within every binding budget', () => {
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
    /applicationId must match the requested ios release/u,
  );
  assert.match(result.errors.join('\n'), /rawResultsReference is invalid/u);
});

test('rejects a normalized but nonexistent measurement calendar date', () => {
  const evidence = validEvidence('android');
  evidence.measuredAt = '2026-02-31T00:00:00Z';
  const result = validate(evidence, 'android');
  assert.match(result.errors.join('\n'), /RFC 3339 UTC instant/u);
});

test('hashes exactly the protocol and raw-result bytes below the evidence root', () => {
  const root = mkdtempSync(join(tmpdir(), 'birthday-performance-'));
  try {
    mkdirSync(join(root, 'protocol'));
    mkdirSync(join(root, 'raw'));
    const protocol = Buffer.from('reviewed protocol bytes');
    const raw = Buffer.from('private raw measurement bytes');
    writeFileSync(join(root, 'protocol', 'v1.txt'), protocol);
    writeFileSync(join(root, 'raw', 'android-v1.jsonl'), raw);
    const evidence = validEvidence('android');
    evidence.references = {
      protocolReference: 'protocol/v1.txt',
      protocolSha256: createHash('sha256').update(protocol).digest('hex'),
      protocolBytes: protocol.byteLength,
      rawResultsReference: 'raw/android-v1.jsonl',
      rawResultsSha256: createHash('sha256').update(raw).digest('hex'),
      rawResultsBytes: raw.byteLength,
    };
    assert.deepEqual(
      verifyPerformanceEvidenceReferences(evidence, root).errors,
      [],
    );

    writeFileSync(join(root, 'raw', 'android-v1.jsonl'), 'changed');
    assert.match(
      verifyPerformanceEvidenceReferences(evidence, root).errors.join('\n'),
      /raw performance results sha256 does not match/u,
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects extra files, symlinks, duplicate references, and escaping paths', t => {
  const root = mkdtempSync(join(tmpdir(), 'birthday-performance-'));
  const outside = join(tmpdir(), `birthday-performance-outside-${process.pid}`);
  try {
    writeFileSync(join(root, 'protocol.txt'), 'protocol');
    writeFileSync(join(root, 'raw.txt'), 'raw');
    writeFileSync(join(root, 'extra.txt'), 'extra');
    const evidence = validEvidence('ios');
    evidence.references.protocolReference = 'protocol.txt';
    evidence.references.rawResultsReference = 'raw.txt';
    assert.match(
      verifyPerformanceEvidenceReferences(evidence, root).errors.join('\n'),
      /unreferenced files|exactly the two referenced/u,
    );

    rmSync(join(root, 'extra.txt'));
    writeFileSync(outside, 'outside');
    rmSync(join(root, 'raw.txt'));
    if (symlinksAvailable) {
      symlinkSync(outside, join(root, 'raw.txt'));
      assert.match(
        verifyPerformanceEvidenceReferences(evidence, root).errors.join('\n'),
        /symlink/u,
      );
      rmSync(join(root, 'raw.txt'));
    } else {
      t.diagnostic('host cannot create symbolic links; symlink case skipped');
    }

    writeFileSync(join(root, 'raw.txt'), 'raw');
    evidence.references.rawResultsReference =
      evidence.references.protocolReference;
    assert.match(
      verifyPerformanceEvidenceReferences(evidence, root).errors.join('\n'),
      /distinct files/u,
    );
    evidence.references.rawResultsReference = '../outside';
    assert.match(
      validate(evidence, 'ios').errors.join('\n'),
      /rawResultsReference is invalid/u,
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
    rmSync(outside, { force: true });
  }
});
