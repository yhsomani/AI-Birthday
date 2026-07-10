import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  buildReactNativeReleaseEvidence,
  defaultDeviceEvidence,
  defaultReleaseEvidenceCommands,
  setupDoctorReleaseEvidenceFromReport,
  type EasConfigLike,
  type ExpoAppConfigLike,
  type PackageJsonLike,
  type ReleaseEvidenceCommand,
  type ReleaseEvidenceProvenance
} from './releaseEvidence';

const packageJson: PackageJsonLike = {
  name: 'relateai-react-native',
  version: '1.0.0',
  main: 'index.js',
  scripts: {
    test: 'tsx --test --test-isolation=none src/**/*.test.ts'
  }
};

const appConfig: ExpoAppConfigLike = {
  expo: {
    name: 'RelateAI',
    slug: 'relateai',
    version: '1.0.0',
    runtimeVersion: { policy: 'appVersion' },
    scheme: 'relateai',
    plugins: ['./plugins/with-relateai-shortcuts', './plugins/with-relateai-home-widget'],
    android: {
      package: 'com.relateai.app',
      versionCode: 1,
      permissions: ['READ_CONTACTS', 'READ_CALENDAR', 'WRITE_CALENDAR', 'POST_NOTIFICATIONS', 'USE_BIOMETRIC'],
      blockedPermissions: [
        'android.permission.SEND_SMS',
        'android.permission.READ_SMS',
        'android.permission.RECEIVE_SMS',
        'android.permission.READ_CALL_LOG',
        'android.permission.READ_PHONE_NUMBERS',
        'android.permission.USE_EXACT_ALARM',
        'android.permission.SCHEDULE_EXACT_ALARM',
        'android.permission.BIND_ACCESSIBILITY_SERVICE'
      ]
    },
    ios: {
      bundleIdentifier: 'com.relateai.app',
      buildNumber: '1'
    }
  }
};

const easConfig: EasConfigLike = {
  cli: {
    appVersionSource: 'local'
  },
  build: {
    development: { developmentClient: true },
    preview: { distribution: 'internal', android: { buildType: 'apk' } },
    production: {
      autoIncrement: true,
      android: { buildType: 'app-bundle' },
      ios: { simulator: false }
    }
  },
  submit: {
    production: {}
  }
};

const provenance: ReleaseEvidenceProvenance = {
  schemaVersion: 2,
  commitSha: 'a'.repeat(40),
  dirty: false,
  workingTreeSha256: 'b'.repeat(64),
  lockfileSha256: 'c'.repeat(64),
  nodeVersion: 'v24.18.0',
  npmVersion: '11.6.0',
  platform: 'linux',
  architecture: 'x64',
  runner: 'github-actions',
  ci: {
    repository: 'example/relateai',
    runId: '123',
    runAttempt: '1',
    workflowRef: 'example/relateai/.github/workflows/android.yml@refs/heads/main'
  }
};

const passedCommands: ReleaseEvidenceCommand[] = defaultReleaseEvidenceCommands.map(command => ({
  ...command,
  status: 'Passed',
  exitCode: 0,
  startedAt: '2026-07-10T00:00:00.000Z',
  completedAt: '2026-07-10T00:00:01.000Z',
  durationMs: 1000,
  outputSha256: 'd'.repeat(64),
  detail: command.id === 'test' ? '348 tests across 66 suites' : undefined
}));

describe('React Native release evidence contract', () => {
  it('builds an RN-only release evidence record that excludes the Kotlin tree from the active surface', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands
    });

    assert.equal(evidence.app.entrypoint, 'index.js');
    assert.equal(evidence.app.androidPackage, 'com.relateai.app');
    assert.equal(evidence.app.iosBundleIdentifier, 'com.relateai.app');
    assert.equal(evidence.activeReleaseSurface.platform, 'React Native / Expo');
    assert.equal(evidence.activeReleaseSurface.legacyKotlinGradleStatus, 'Reference only');
    assert.match(evidence.activeReleaseSurface.legacyKotlinGradleReleaseRole, /Excluded from RN release evidence/);
    assert.equal(evidence.activeReleaseSurface.legacyKotlinGradleArtifactPaths, null);
    assert.equal(evidence.releaseConfig.npmTestUsesFullNonIsolatedSuite, true);
    assert.equal(evidence.releaseConfig.productionAndroidBuildType, 'app-bundle');
    assert.equal(evidence.releaseConfig.productionIosSimulator, false);
    assert.deepEqual(evidence.blockers, []);
    assert.ok(evidence.warnings.some(warning => /signed-android-build/.test(warning)));
  });

  it('records legacy Kotlin and Gradle removal when no legacy artifacts are present', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: []
    });

    assert.equal(evidence.activeReleaseSurface.legacyKotlinGradleStatus, 'Removed from repository');
    assert.deepEqual(evidence.activeReleaseSurface.legacyKotlinGradleArtifactPaths, []);
    assert.doesNotMatch(evidence.warnings.join('\n'), /legacy-archive-decision/);
  });

  it('records pending legacy archive evidence when Android artifact paths remain', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: ['app', 'core', 'gradle', 'gradlew']
    });

    assert.equal(evidence.activeReleaseSurface.legacyKotlinGradleStatus, 'Reference only');
    assert.deepEqual(evidence.activeReleaseSurface.legacyKotlinGradleArtifactPaths, [
      'app',
      'core',
      'gradle',
      'gradlew'
    ]);
    assert.ok(evidence.warnings.some(warning => /app, core, gradle, gradlew/.test(warning)));
  });

  it('adapts release evidence for Setup Check without sharing mutable report arrays', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: ['app', 'core']
    });

    const setupEvidence = setupDoctorReleaseEvidenceFromReport(evidence);
    setupEvidence.blockers.push('mutated blocker');
    setupEvidence.warnings.push('mutated warning');
    setupEvidence.legacyKotlinGradleArtifactPaths?.push('mutated path');

    assert.deepEqual(evidence.blockers, []);
    assert.equal(
      evidence.warnings.some(warning => warning === 'mutated warning'),
      false
    );
    assert.deepEqual(evidence.activeReleaseSurface.legacyKotlinGradleArtifactPaths, ['app', 'core']);
  });

  it('keeps SMS, SMS inbox, call-log, phone-number, exact-alarm, and AccessibilityService permissions out of release evidence', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      deviceEvidence: defaultDeviceEvidence.map(item => ({ ...item, status: 'Attached' }))
    });

    assert.equal(evidence.permissions.forbiddenAndroidPermissionsAbsent, true);
    assert.equal(evidence.permissions.forbiddenAndroidPermissionsBlocked, true);
    assert.equal(evidence.warnings.length, 0);
  });

  it('blocks AccessibilityService drift in the active React Native release config', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig: {
        expo: {
          ...appConfig.expo,
          android: {
            ...appConfig.expo?.android,
            permissions: [
              ...(appConfig.expo?.android?.permissions ?? []),
              'android.permission.BIND_ACCESSIBILITY_SERVICE'
            ],
            blockedPermissions: (appConfig.expo?.android?.blockedPermissions ?? []).filter(
              permission => permission !== 'android.permission.BIND_ACCESSIBILITY_SERVICE'
            )
          }
        }
      },
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      deviceEvidence: defaultDeviceEvidence.map(item => ({ ...item, status: 'Attached' }))
    });

    assert.equal(evidence.permissions.forbiddenAndroidPermissionsAbsent, false);
    assert.equal(evidence.permissions.forbiddenAndroidPermissionsBlocked, false);
    assert.ok(evidence.blockers.some(blocker => /AccessibilityService permission/i.test(blocker)));
    assert.ok(evidence.blockers.some(blocker => /does not block all forbidden Android permissions/i.test(blocker)));
  });

  it('blocks exact-alarm permission drift in the active React Native release config', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig: {
        expo: {
          ...appConfig.expo,
          android: {
            ...appConfig.expo?.android,
            permissions: [
              ...(appConfig.expo?.android?.permissions ?? []),
              'android.permission.USE_EXACT_ALARM',
              'android.permission.SCHEDULE_EXACT_ALARM'
            ],
            blockedPermissions: (appConfig.expo?.android?.blockedPermissions ?? []).filter(
              permission =>
                permission !== 'android.permission.USE_EXACT_ALARM' &&
                permission !== 'android.permission.SCHEDULE_EXACT_ALARM'
            )
          }
        }
      },
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      deviceEvidence: defaultDeviceEvidence.map(item => ({ ...item, status: 'Attached' }))
    });

    assert.equal(evidence.permissions.forbiddenAndroidPermissionsAbsent, false);
    assert.equal(evidence.permissions.forbiddenAndroidPermissionsBlocked, false);
    assert.ok(evidence.blockers.some(blocker => /exact-alarm/i.test(blocker)));
    assert.ok(evidence.blockers.some(blocker => /does not block all forbidden Android permissions/i.test(blocker)));
  });

  it('surfaces release blockers when validation commands, EAS profiles, or permission policy drift', () => {
    const failingCommands: ReleaseEvidenceCommand[] = passedCommands.map(command =>
      command.id === 'typecheck' ? { ...command, status: 'Failed', exitCode: 1 } : command
    );
    const brokenEvidence = buildReactNativeReleaseEvidence({
      packageJson: {
        ...packageJson,
        scripts: { test: 'tsx --test src/**/*.test.ts' }
      },
      appConfig: {
        expo: {
          ...appConfig.expo,
          android: {
            ...appConfig.expo?.android,
            permissions: [
              ...(appConfig.expo?.android?.permissions ?? []),
              'SEND_SMS',
              'android.permission.USE_EXACT_ALARM',
              'android.permission.BIND_ACCESSIBILITY_SERVICE'
            ],
            blockedPermissions: []
          }
        }
      },
      easConfig: {
        ...easConfig,
        build: {
          ...easConfig.build,
          production: {
            android: { buildType: 'apk' },
            ios: { simulator: true }
          }
        },
        submit: {}
      },
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: failingCommands
    });

    assert.ok(brokenEvidence.blockers.some(blocker => /full React Native source-contract suite/i.test(blocker)));
    assert.ok(brokenEvidence.blockers.some(blocker => /npm run typecheck evidence is failing/i.test(blocker)));
    assert.ok(brokenEvidence.blockers.some(blocker => /app bundle/i.test(blocker)));
    assert.ok(brokenEvidence.blockers.some(blocker => /physical devices/i.test(blocker)));
    assert.ok(brokenEvidence.blockers.some(blocker => /Production EAS submit/i.test(blocker)));
    assert.ok(brokenEvidence.blockers.some(blocker => /directly requests a forbidden/i.test(blocker)));
    assert.ok(brokenEvidence.blockers.some(blocker => /exact-alarm/i.test(blocker)));
    assert.ok(brokenEvidence.blockers.some(blocker => /AccessibilityService permission/i.test(blocker)));
    assert.ok(
      brokenEvidence.blockers.some(blocker => /does not block all forbidden Android permissions/i.test(blocker))
    );
    assert.ok(brokenEvidence.blockers.some(blocker => /npm run typecheck evidence is failing/i.test(blocker)));
  });

  it('fails closed when provenance or executed command proof is missing', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      commands: defaultReleaseEvidenceCommands.map(command => ({ ...command, status: 'Passed' }))
    });

    assert.ok(evidence.blockers.some(blocker => /provenance is missing/i.test(blocker)));
    assert.ok(evidence.blockers.some(blocker => /missing exit-code, timing, or output-hash proof/i.test(blocker)));
  });

  it('blocks evidence generated from a dirty tree or for a substituted command', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance: { ...provenance, dirty: true },
      commands: passedCommands.map(command => (command.id === 'audit' ? { ...command, command: 'true' } : command)),
      deviceEvidence: defaultDeviceEvidence.map(item => ({ ...item, status: 'Attached' }))
    });

    assert.ok(evidence.blockers.some(blocker => /dirty working tree/i.test(blocker)));
    assert.ok(evidence.blockers.some(blocker => /audit evidence did not execute the required command/i.test(blocker)));
  });
});
