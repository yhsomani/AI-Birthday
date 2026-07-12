import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import { validateDecodedAabManifest } from './inspect-android-aab-manifest.mjs';

const expected = Object.freeze({
  expectedPackage: 'com.yashsomani.birthdayautopilot',
  tier: 'prod',
});

const validManifest = `<manifest xmlns:android="http://schemas.android.com/apk/res/android" android:compileSdkVersion="36" android:versionCode="1" android:versionName="1.0" package="com.yashsomani.birthdayautopilot">
  <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/>
  <uses-permission android:name="android.permission.INTERNET"/>
  <uses-permission android:name="android.permission.SEND_SMS"/>
  <uses-permission android:name="android.permission.READ_PHONE_STATE"/>
  <uses-feature android:name="android.hardware.telephony.messaging" android:required="true"/>
  <application android:allowBackup="false" android:debuggable="false" android:extractNativeLibs="false" android:usesCleartextTraffic="false">
    <activity android:exported="true" android:name="com.yashsomani.birthdayautopilot.MainActivity"/>
  </application>
</manifest>`;

test('accepts one exact restricted production AAB manifest decoded by bundletool', () => {
  const result = validateDecodedAabManifest(validManifest, expected);
  assert.deepEqual(
    {
      applicationId: result.applicationId,
      minimumSdk: result.minimumSdk,
      targetSdk: result.targetSdk,
      versionCode: result.versionCode,
      versionName: result.versionName,
    },
    {
      applicationId: 'com.yashsomani.birthdayautopilot',
      minimumSdk: '29',
      targetSdk: '36',
      versionCode: '1',
      versionName: '1.0',
    },
  );
  assert.deepEqual(result.permissions, [
    'android.permission.INTERNET',
    'android.permission.READ_PHONE_STATE',
    'android.permission.SEND_SMS',
  ]);
});

test('binds the lab manifest to its distinct package and version name', () => {
  const labManifest = validManifest
    .replaceAll(
      'com.yashsomani.birthdayautopilot',
      'com.yashsomani.birthdayautopilot.lab',
    )
    .replace('versionName="1.0"', 'versionName="1.0-lab"');
  const result = validateDecodedAabManifest(labManifest, {
    expectedPackage: 'com.yashsomani.birthdayautopilot.lab',
    tier: 'lab',
  });
  assert.equal(result.applicationId, 'com.yashsomani.birthdayautopilot.lab');
  assert.equal(result.versionName, '1.0-lab');
});

test('rejects tampered package, version and SDK coordinates', () => {
  for (const [label, manifest] of [
    [
      'package',
      validManifest.replace(
        'package="com.yashsomani.birthdayautopilot"',
        'package="com.attacker.wrong"',
      ),
    ],
    [
      'versionCode',
      validManifest.replace('versionCode="1"', 'versionCode="2"'),
    ],
    [
      'versionName',
      validManifest.replace('versionName="1.0"', 'versionName="2.0"'),
    ],
    [
      'compileSdkVersion',
      validManifest.replace('compileSdkVersion="36"', 'compileSdkVersion="35"'),
    ],
    [
      'minSdkVersion',
      validManifest.replace('minSdkVersion="29"', 'minSdkVersion="28"'),
    ],
    [
      'targetSdkVersion',
      validManifest.replace('targetSdkVersion="36"', 'targetSdkVersion="35"'),
    ],
  ]) {
    assert.throws(
      () => validateDecodedAabManifest(manifest, expected),
      new RegExp(label),
    );
  }
});

test('rejects debug, test, cleartext, backup, extraction and profiling release drift', () => {
  const mutations = [
    validManifest.replace('debuggable="false"', 'debuggable="true"'),
    validManifest.replace(
      'android:debuggable="false"',
      'android:debuggable="false" android:testOnly="true"',
    ),
    validManifest.replace(
      'usesCleartextTraffic="false"',
      'usesCleartextTraffic="true"',
    ),
    validManifest.replace('allowBackup="false"', 'allowBackup="true"'),
    validManifest.replace(
      'extractNativeLibs="false"',
      'extractNativeLibs="true"',
    ),
    validManifest.replace(
      '  </application>',
      '    <profileable android:shell="true"/>\n  </application>',
    ),
  ];
  for (const manifest of mutations) {
    assert.throws(() => validateDecodedAabManifest(manifest, expected));
  }
});

test('requires restricted SMS permissions and rejects private-data permissions', () => {
  for (const requiredPermission of [
    'android.permission.SEND_SMS',
    'android.permission.READ_PHONE_STATE',
  ]) {
    const withoutRequired = validManifest.replace(
      `  <uses-permission android:name="${requiredPermission}"/>\n`,
      '',
    );
    assert.throws(
      () => validateDecodedAabManifest(withoutRequired, expected),
      /missing a restricted-SMS permission/u,
    );
  }

  const withForbidden = validManifest.replace(
    '  <uses-permission android:name="android.permission.INTERNET"/>',
    '  <uses-permission android:name="android.permission.INTERNET"/>\n  <uses-permission-sdk-23 android:name="android.permission.READ_CONTACTS"/>',
  );
  assert.throws(
    () => validateDecodedAabManifest(withForbidden, expected),
    /forbidden permission/u,
  );
});

test('requires an unambiguous messaging-capable canonical base manifest', () => {
  const withoutMessaging = validManifest.replace(
    '  <uses-feature android:name="android.hardware.telephony.messaging" android:required="true"/>\n',
    '',
  );
  assert.throws(
    () => validateDecodedAabManifest(withoutMessaging, expected),
    /telephony messaging feature/u,
  );
  assert.throws(
    () =>
      validateDecodedAabManifest(
        validManifest.replace('</manifest>', '<application/>\n</manifest>'),
        expected,
      ),
    /ambiguous release structure/u,
  );
  assert.throws(
    () =>
      validateDecodedAabManifest(
        `<!DOCTYPE manifest SYSTEM "attacker">${validManifest}`,
        expected,
      ),
    /non-canonical/u,
  );
});

test('AAB inspection uses only locked checksum-verified bundletool 1.18.1 offline', () => {
  const gradle = readFileSync('android/build.gradle', 'utf8');
  const lock = readFileSync('android/buildscript-gradle.lockfile', 'utf8');
  const verification = readFileSync(
    'android/gradle/verification-metadata.xml',
    'utf8',
  );
  const inspector = readFileSync(
    'tools/inspect-android-aab-manifest.mjs',
    'utf8',
  );

  assert.match(
    gradle,
    /classpath\("com\.android\.tools\.build:bundletool:1\.18\.1"\)/u,
  );
  assert.match(gradle, /dumpBirthdayAabManifest/u);
  assert.match(
    gradle,
    /mainClass = "com\.android\.tools\.build\.bundletool\.BundleToolMain"/u,
  );
  assert.match(gradle, /resolvedArtifacts/u);
  assert.match(
    lock,
    /com\.android\.tools\.build:bundletool:1\.18\.1=classpath/u,
  );
  assert.match(
    verification,
    /<component group="com\.android\.tools\.build" name="bundletool" version="1\.18\.1">[\s\S]*?<artifact name="bundletool-1\.18\.1\.jar">[\s\S]*?<sha256 value="a73341a7945abcb0e6b8971c7b1b2801bd765006447ca0d2437a4260d572ceac"/u,
  );
  assert.match(inspector, /'--offline'/u);
  assert.match(inspector, /'--no-daemon'/u);
  assert.match(inspector, /'--no-configuration-cache'/u);
  assert.match(inspector, /sameStableFile/u);
});
