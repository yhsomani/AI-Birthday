import assert from 'node:assert/strict';
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  ensureNativeEvidenceRoot,
  parsePlatformArguments,
  runNativeAdvisoryGate,
} from './run-native-advisory-gate.mjs';

test('parses only an explicit supported native advisory platform', () => {
  assert.equal(parsePlatformArguments(['--platform', 'android']), 'android');
  assert.equal(parsePlatformArguments(['--platform', 'ios']), 'ios');
  assert.equal(parsePlatformArguments(['--platform', 'all']), 'all');
  assert.throws(() => parsePlatformArguments([]), /usage/u);
  assert.throws(
    () => parsePlatformArguments(['--platform', 'windows']),
    /usage/u,
  );
});

test('rejects symlinked workspace and evidence roots before scanning', t => {
  const parent = mkdtempSync(path.join(tmpdir(), 'native-advisory-root-test-'));
  t.after(() => rmSync(parent, { recursive: true, force: true }));
  const workspace = path.join(parent, 'workspace');
  const outside = path.join(parent, 'outside');
  mkdirSync(workspace);
  mkdirSync(outside);
  const workspaceLink = path.join(parent, 'workspace-link');
  symlinkSync(workspace, workspaceLink);
  assert.throws(
    () => ensureNativeEvidenceRoot(workspaceLink),
    /workspace root must be a non-symlinked directory/u,
  );
  symlinkSync(outside, path.join(workspace, 'release-evidence'));
  assert.throws(
    () => ensureNativeEvidenceRoot(workspace),
    /evidence root must be a non-symlinked directory/u,
  );
});

test('the Android runner preserves complete, production-runtime, and build-plugin scopes', async t => {
  const root = mkdtempSync(path.join(tmpdir(), 'native-advisory-runner-test-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(path.join(root, 'android/app'), { recursive: true });
  mkdirSync(path.join(root, 'tools'));
  writeFileSync(
    path.join(root, 'package.json'),
    `${JSON.stringify({ version: '1.2.3' })}\n`,
  );
  writeFileSync(
    path.join(root, 'tools/native-advisory-exceptions.json'),
    '{"schemaVersion":1,"exceptions":[]}\n',
  );
  writeFileSync(
    path.join(root, 'android/app/gradle.lockfile'),
    [
      'com.example:runtime:1.0=prodReleaseRuntimeClasspath,testRuntimeClasspath',
      'com.example:test-only:2.0=testRuntimeClasspath',
      '',
    ].join('\n'),
  );
  writeFileSync(
    path.join(root, 'android/buildscript-gradle.lockfile'),
    'com.example:plugin:3.0=classpath\n',
  );
  const now = new Date('2026-07-12T12:00:00.000Z');
  let preparedTargets = null;
  const { report, output } = await runNativeAdvisoryGate({
    platform: 'android',
    root,
    now,
    buildReport: async ({ targets, exceptionDocument, exceptionBytes }) => {
      preparedTargets = targets;
      assert.deepEqual(exceptionDocument, {
        schemaVersion: 1,
        exceptions: [],
      });
      assert.ok(exceptionBytes.length > 0);
      return {
        schemaVersion: 1,
        dependencySets: targets.map(target => ({
          label: target.label,
          configuration: target.configuration,
          componentCount: target.componentCount,
        })),
        summary: {
          status: 'pass',
          dependencySetCount: targets.length,
          componentCount: targets.reduce(
            (sum, target) => sum + target.componentCount,
            0,
          ),
          findingCount: 0,
          exceptedCount: 0,
          unresolvedCount: 0,
        },
      };
    },
  });
  assert.deepEqual(
    preparedTargets.map(target => [
      target.label,
      target.configuration,
      target.componentCount,
    ]),
    [
      ['android-complete-graph', null, 2],
      ['android-prod-runtime', 'prodReleaseRuntimeClasspath', 1],
      ['android-build-plugins', null, 1],
    ],
  );
  assert.equal(report.summary.componentCount, 4);
  assert.match(
    output,
    /^release-evidence\/native-advisory-2026-07-12T12-00-00-000Z-android-[0-9]+\.json$/u,
  );
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(root, output), 'utf8')),
    report,
  );
});
