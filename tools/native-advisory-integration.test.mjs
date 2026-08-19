import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
);
const read = relativePath =>
  readFileSync(path.join(projectRoot, relativePath), 'utf8');

test('package exposes fail-closed native advisory gate for Android', () => {
  const packageDocument = JSON.parse(read('package.json'));
  assert.equal(
    packageDocument.scripts['security:native:android'],
    'node tools/run-native-advisory-gate.mjs --platform android',
  );
  assert.equal(packageDocument.scripts['security:native:ios'], undefined);
});

test('Android secure transitive selections remain explicit and evidence-locked', () => {
  const build = read('android/build.gradle');
  const refresh = read('tools/refresh-android-dependency-evidence.sh');
  const workflow = read('.github/workflows/ci.yml');
  for (const version of [
    '4.1.135.Final',
    '1.28.0',
    '0.9.6',
    '1.84',
    '2.0.6.1',
    '3.25.5',
    '1.21.2',
    '33.4.8-android',
  ]) {
    assert.ok(build.includes(`useVersion("${version}")`), `missing ${version}`);
  }
  assert.match(build, /lockMode = LockMode\.STRICT/u);
  assert.match(build, /e2eDebugAndroidTestRuntimeClasspath/u);
  assert.doesNotMatch(build, /smokeDebugAndroidTestRuntimeClasspath/u);
  for (const task of [
    ':app:testDevDebugOptimizedUnitTest',
    ':app:testStagingDebugOptimizedUnitTest',
    ':app:testE2eDebugUnitTest',
    ':app:compileE2eDebugAndroidTestKotlin',
    ':app:assembleE2eDebugAndroidTest',
    ':app:lintE2eDebug',
    ':app:lintSmokeDebug',
    ':app:assembleE2eDebug',
    ':app:assembleSmokeDebug',
  ]) {
    assert.ok(refresh.includes(task), `dependency refresh is missing ${task}`);
    if (task.includes('DebugOptimized')) {
      assert.ok(workflow.includes(task), `quality CI is missing ${task}`);
    }
  }
});

test('native release and incident documentation states the zero-result limitation', () => {
  const gate = read('docs/NATIVE_DEPENDENCY_ADVISORY_GATE.md');
  const readme = read('README.md');
  const operations = read('docs/OPERATIONS_RUNBOOK.md');
  const android = read('docs/ANDROID_RESTRICTED_RELEASE_EVIDENCE.md');
  assert.match(
    gate,
    /not proof[\s\S]{0,40}free of[\s\S]{0,20}vulnerabilities/u,
  );
  assert.match(gate, /Ordinary CI[\s\S]+exactly zero exceptions/u);
  assert.match(gate, /UNPROVISIONED/u);
  assert.match(readme, /NATIVE_DEPENDENCY_ADVISORY_GATE\.md/u);
  assert.match(
    operations,
    /Native dependency advisory or scan-service incident/u,
  );
  assert.match(android, /prodReleaseRuntimeClasspath/u);
});
