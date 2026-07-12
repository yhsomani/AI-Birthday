import assert from 'node:assert/strict';
import test from 'node:test';

import {
  createNativeSbom,
  parseCocoaPodsLock,
  parseGradleLock,
} from './generate-native-sbom.mjs';

test('creates a deterministic CycloneDX component set from a Gradle lock', () => {
  const lock = Buffer.from(
    [
      '# This is a Gradle generated file for dependency locking.',
      'com.google.guava:guava:33.4.8-android=prodReleaseRuntimeClasspath',
      'androidx.core:core-ktx:1.17.0=prodReleaseRuntimeClasspath',
      'empty=lintChecks',
      '',
    ].join('\n'),
  );
  const bom = createNativeSbom({
    kind: 'gradle',
    lockBytes: lock,
    applicationName: 'Birthday Autopilot Android',
    version: '1.0',
  });
  assert.equal(bom.bomFormat, 'CycloneDX');
  assert.equal(bom.specVersion, '1.6');
  assert.deepEqual(
    bom.components.map(item => item.purl),
    [
      'pkg:maven/androidx.core/core-ktx@1.17.0',
      'pkg:maven/com.google.guava/guava@33.4.8-android',
    ],
  );
  assert.match(bom.metadata.properties[1].value, /^[0-9a-f]{64}$/u);
});

test('creates an exact configuration-scoped Gradle runtime SBOM', () => {
  const lock = Buffer.from(
    [
      'com.example:runtime:1.0=prodReleaseRuntimeClasspath,testRuntimeClasspath',
      'com.example:test-only:2.0=testRuntimeClasspath',
      '',
    ].join('\n'),
  );
  const bom = createNativeSbom({
    kind: 'gradle',
    lockBytes: lock,
    applicationName: 'Birthday Autopilot Android prod runtime',
    version: '1.0',
    configuration: 'prodReleaseRuntimeClasspath',
  });
  assert.deepEqual(
    bom.components.map(item => item.purl),
    ['pkg:maven/com.example/runtime@1.0'],
  );
  assert.deepEqual(bom.metadata.properties[2], {
    name: 'birthday:gradle-configuration',
    value: 'prodReleaseRuntimeClasspath',
  });
  assert.throws(
    () => parseGradleLock(lock.toString('utf8'), { configuration: 'missing' }),
    /no components for the selected configuration/u,
  );
});

test('extracts resolved root pods and canonicalizes subspecs', () => {
  const lock = `PODS:
  - Firebase/Auth (12.15.0):
    - Firebase/CoreOnly
  - "GoogleUtilities/NSData+zlib (8.1.2)":
    - GoogleUtilities/Privacy
  - FirebaseAuth (12.15.0)
DEPENDENCIES:
  - FirebaseAuth (= 12.15.0)
SPEC CHECKSUMS:
  FirebaseAuth: abc
`;
  assert.deepEqual(
    parseCocoaPodsLock(lock).map(item => item.purl),
    [
      'pkg:cocoapods/Firebase@12.15.0',
      'pkg:cocoapods/FirebaseAuth@12.15.0',
      'pkg:cocoapods/GoogleUtilities@8.1.2',
    ],
  );
});

test('fails closed on malformed or empty native locks', () => {
  assert.throws(() => parseGradleLock('not-a-coordinate=runtime'), /invalid/u);
  assert.throws(() => parseCocoaPodsLock('PODS:\n'), /no components/u);
});
