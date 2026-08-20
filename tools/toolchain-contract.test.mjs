import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import { TOOLCHAIN_VERSIONS } from './toolchain-versions.mjs';

const read = file => readFileSync(file, 'utf8').replace(/\r\n/gu, '\n');

test('mobile Node and npm pins agree across install contracts', () => {
  const packageJson = JSON.parse(read('package.json'));
  const packageLock = JSON.parse(read('package-lock.json'));
  const hostingPackage = JSON.parse(read('backend/hosting/package.json'));

  assert.equal(read('.nvmrc').trim(), TOOLCHAIN_VERSIONS.node);
  assert.equal(packageJson.packageManager, `npm@${TOOLCHAIN_VERSIONS.npm}`);
  assert.deepEqual(packageJson.engines, {
    node: TOOLCHAIN_VERSIONS.node,
    npm: TOOLCHAIN_VERSIONS.npm,
  });
  assert.deepEqual(packageLock.packages[''].engines, packageJson.engines);
  assert.deepEqual(hostingPackage.engines, packageJson.engines);
  assert.equal(
    packageJson.scripts['codegen:check'],
    'node tools/patch-react-native-codegen.mjs --check',
  );
  assert.equal(
    packageJson.scripts['check:portable'],
    'npm run check && npm run backend:check && npm run hosting:check',
  );
  assert.equal(packageJson.scripts['check:all'], undefined);

  assert.match(
    read('android/gradle/wrapper/gradle-wrapper.properties'),
    /distributionSha256Sum=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06/u,
  );
});

test('Android application, build plugins, test utility and host tools are locked and byte-pinned', () => {
  const applicationLock = read('android/app/gradle.lockfile');
  const buildscriptLock = read('android/buildscript-gradle.lockfile');
  const settingsLock = read('android/settings-gradle.lockfile');
  const verification = read('android/gradle/verification-metadata.xml');
  const rootBuild = read('android/build.gradle');
  const applicationBuild = read('android/app/build.gradle');
  const workflow = read('.github/workflows/ci.yml');

  assert.match(applicationLock, /firebase-config:23\.1\.0=/u);
  assert.match(applicationLock, /firebase-ai:17\.13\.0=/u);
  assert.match(applicationLock, /orchestrator:1\.6\.1=androidTestUtil/u);
  assert.match(
    applicationBuild,
    /implementation\("org\.jetbrains\.kotlinx:kotlinx-serialization-json:1\.8\.1"\)/u,
  );
  assert.match(
    applicationBuild,
    /testImplementation\("com\.squareup\.okhttp3:mockwebserver:4\.12\.0"\)/u,
  );
  assert.match(
    applicationLock,
    /com\.squareup\.okhttp3:mockwebserver:4\.12\.0=[^\n]*devDebugUnitTestRuntimeClasspath[^\n]*prodReleaseUnitTestRuntimeClasspath/u,
  );
  assert.match(
    verification,
    /<component group="com\.squareup\.okhttp3" name="mockwebserver" version="4\.12\.0">[\s\S]*?<artifact name="mockwebserver-4\.12\.0\.jar">[\s\S]*?<sha256 value="[0-9a-f]{64}"/u,
  );
  for (const artifact of [
    'kotlinx-serialization-core-jvm',
    'kotlinx-serialization-json-jvm',
  ]) {
    assert.match(
      applicationLock,
      new RegExp(
        `org\\.jetbrains\\.kotlinx:${artifact}:1\\.8\\.1=[^\\n]*devDebugAndroidTestRuntimeClasspath`,
        'u',
      ),
    );
  }
  for (const configuration of [
    'devDebugRuntimeClasspath',
    'devReleaseRuntimeClasspath',
    'stagingDebugRuntimeClasspath',
    'stagingReleaseRuntimeClasspath',
    'labReleaseRuntimeClasspath',
    'prodReleaseRuntimeClasspath',
  ]) {
    assert.match(applicationLock, new RegExp(configuration, 'u'));
  }
  assert.match(
    buildscriptLock,
    /com\.android\.tools\.build:gradle:8\.12\.0=classpath/u,
  );
  assert.match(
    buildscriptLock,
    /com\.android\.tools\.build:bundletool:1\.18\.1=classpath/u,
  );
  assert.match(
    rootBuild,
    /classpath\("com\.android\.tools\.build:bundletool:1\.18\.1"\)/u,
  );
  assert.match(
    verification,
    /bundletool-1\.18\.1\.jar[\s\S]*?a73341a7945abcb0e6b8971c7b1b2801bd765006447ca0d2437a4260d572ceac/u,
  );
  assert.match(
    buildscriptLock,
    /com\.google\.gms:google-services:4\.5\.0=classpath/u,
  );
  assert.match(settingsLock, /empty=incomingCatalogForLibs0/u);
  assert.match(rootBuild, /lockMode = LockMode\.STRICT/u);
  assert.match(verification, /<verify-metadata>true<\/verify-metadata>/u);
  assert.match(
    verification,
    /aapt2-8\.12\.0-13700139-linux\.jar[\s\S]*?df85593b92c25199515750a30b14744acc42537b2d1176be61b248111484134f/u,
  );
  assert.match(verification, /aapt2-8\.12\.0-13700139-osx\.jar/u);
  assert.match(workflow, /npm run check:portable/u);
  assert.doesNotMatch(workflow, /npm run check:all/u);
  for (const task of [
    ':app:testDevDebugUnitTest',
    ':app:testDevReleaseUnitTest',
    ':app:testStagingDebugUnitTest',
    ':app:testStagingReleaseUnitTest',
    ':app:testLabReleaseUnitTest',
    ':app:testProdReleaseUnitTest',
    ':app:compileDevDebugAndroidTestKotlin',
    ':app:compileStagingDebugAndroidTestKotlin',
    ':app:assembleDevDebugAndroidTest',
    ':app:assembleStagingDebugAndroidTest',
    ':app:lintDevDebug',
    ':app:lintDevRelease',
    ':app:lintStagingDebug',
    ':app:lintStagingRelease',
    ':app:lintLabRelease',
    ':app:lintProdRelease',
    ':app:assembleDevDebug',
    ':app:assembleDevRelease',
    ':app:assembleStagingDebug',
    ':app:assembleStagingRelease',
  ]) {
    assert.match(workflow, new RegExp(task, 'u'));
  }
});

test('Functions compile and run against the deployed Node 22 runtime line', () => {
  const functionsPackage = JSON.parse(read('backend/functions/package.json'));
  const firebaseConfig = JSON.parse(read('backend/firebase.json'));
  const workflow = read('.github/workflows/ci.yml');

  assert.equal(
    read('backend/functions/.nvmrc').trim(),
    TOOLCHAIN_VERSIONS.functionsNode,
  );
  assert.match(functionsPackage.devDependencies['@types/node'], /^22\./u);
  assert.deepEqual(functionsPackage.engines, {
    node: '>=22 <25',
    npm: TOOLCHAIN_VERSIONS.npm,
  });
  assert.match(read('backend/functions/.npmrc'), /^engine-strict=true$/mu);
  assert.match(read('backend/hosting/.npmrc'), /^engine-strict=true$/mu);
  assert.equal(firebaseConfig.functions.runtime, 'nodejs22');
  assert.match(workflow, /name: Backend on deployed Node 22 runtime/u);
  assert.match(workflow, /node-version-file: backend\/functions\/\.nvmrc/u);
  assert.match(workflow, /npm run backend:test:emulator/u);
});

test('every third-party CI action is pinned to an immutable commit', () => {
  const workflow = read('.github/workflows/ci.yml');
  const actionReferences = [...workflow.matchAll(/^\s*uses:\s+(\S+)/gmu)].map(
    match => match[1],
  );

  assert.ok(actionReferences.length > 0);
  for (const reference of actionReferences) {
    assert.match(reference, /@[a-f0-9]{40}$/u);
  }
});

test('CI hashes complete retained candidate trees instead of entry points', () => {
  const workflow = read('.github/workflows/ci.yml');
  const manifestTool = read('tools/create-evidence-manifest.mjs');
  assert.equal(
    (workflow.match(/node tools\/create-evidence-manifest\.mjs/gu) ?? [])
      .length,
    2,
  );
  assert.equal(
    (
      workflow.match(/test -s release-evidence\/.+\/sha256-manifest\.json/gu) ??
      []
    ).length,
    2,
  );
  assert.match(workflow, /backend\/functions\/lib/u);
  assert.match(workflow, /backend\/hosting\/public/u);
  assert.match(workflow, /android\/app\/gradle\.lockfile/u);
  assert.match(workflow, /android\/buildscript-gradle\.lockfile/u);
  assert.match(workflow, /android\/settings-gradle\.lockfile/u);
  assert.doesNotMatch(workflow, /android\/gradle\.lockfile/u);
  assert.match(workflow, /android\/gradle\/verification-metadata\.xml/u);
  assert.match(workflow, /android-complete-graph\.cdx\.json/u);
  assert.match(workflow, /android-prod-runtime\.cdx\.json/u);
  assert.match(workflow, /android-build-plugins-native\.cdx\.json/u);
  assert.match(workflow, /android-native-osv\.json/u);
  assert.match(workflow, /javascript-licenses\.json/u);
  assert.match(manifestTool, /mode: portableMode\(stableFile\.metadata\)/u);
  assert.match(manifestTool, /fstatSync\(descriptor, \{ bigint: true \}\)/u);
  assert.match(manifestTool, /constants\.O_NOFOLLOW/u);
  assert.match(manifestTool, /schemaVersion: 3/u);
  assert.match(manifestTool, /collectEvidenceProvenance/u);
  assert.match(manifestTool, /sourceRevision/u);
  assert.match(manifestTool, /sourceCommittedAt/u);
  assert.match(manifestTool, /nodeVersion/u);
  assert.match(manifestTool, /npmVersion/u);
  assert.doesNotMatch(
    workflow,
    /backend\/functions\/lib\/functions\/index\.js/u,
  );
  assert.doesNotMatch(workflow, /sha256sum/u);
});

test('formatting ignores generated Bundler gems but not repository-owned vendor source', () => {
  const ignored = new Set(
    read('.prettierignore').split(/\r?\n/u).filter(Boolean),
  );

  assert.ok(ignored.has('/vendor/bundle/'));
  assert.ok(!ignored.has('vendor/'));
  assert.ok(!ignored.has('/vendor/'));
});
