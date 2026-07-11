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
  type ReleaseEvidenceDeviceItem,
  type ReleaseEvidenceProvenance
} from './releaseEvidence';

const packageJson: PackageJsonLike = {
  name: 'relateai-react-native',
  version: '1.0.0',
  main: 'index.js',
  packageManager: 'npm@11.6.0',
  scripts: {
    typecheck: 'tsc --noEmit',
    lint: 'eslint src',
    'format:check': 'prettier --check "src/**/*.{ts,tsx}" "*.{json,js,cjs}"',
    test: 'tsx --test --test-isolation=none "src/**/*.test.ts"',
    'test:coverage':
      'tsx --test --test-isolation=none --experimental-test-coverage --test-coverage-lines=90 --test-coverage-branches=80 --test-coverage-functions=90 "src/**/*.test.ts"',
    'test:native-prebuild': 'node scripts/verify_native_prebuild.js',
    'release:evidence': 'node --import tsx src/config/releaseEvidenceCli.ts'
  }
};

const appConfig: ExpoAppConfigLike = {
  expo: {
    name: 'RelateAI',
    slug: 'relateai',
    version: '1.0.0',
    runtimeVersion: { policy: 'appVersion' },
    scheme: 'relateai',
    platforms: ['android', 'ios'],
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
  detail: command.id === 'test-coverage' ? 'Coverage thresholds passed' : undefined
}));

const androidArtifactSha256 = '1'.repeat(64);
const iosArtifactSha256 = '2'.repeat(64);
const attachedDeviceEvidence: ReleaseEvidenceDeviceItem[] = defaultDeviceEvidence.map(item => {
  if (item.id === 'legacy-archive-decision') {
    return { ...item, status: 'Attached' };
  }
  const attachment: NonNullable<ReleaseEvidenceDeviceItem['attachment']> = {
    schemaVersion: 1,
    evidenceId: `release-${item.id}`,
    recordedAt: '2026-07-09T12:00:00.000Z',
    owner: 'release-owner',
    sourceUrl: `https://evidence.example.test/${item.id}`,
    candidate: {
      commitSha: provenance.commitSha,
      workingTreeSha256: provenance.workingTreeSha256,
      appVersion: '1.0.0'
    },
    artifacts:
      item.id === 'signed-android-build' || item.id === 'android-device-smoke'
        ? { androidSha256: androidArtifactSha256 }
        : item.id === 'signed-ios-build' || item.id === 'ios-device-smoke'
          ? { iosSha256: iosArtifactSha256 }
          : { androidSha256: androidArtifactSha256, iosSha256: iosArtifactSha256 }
  };
  if (item.id === 'android-device-smoke' || item.id === 'ios-device-smoke') {
    attachment.deviceTest = {
      platform: item.id === 'android-device-smoke' ? 'android' : 'ios',
      deviceModel: item.id === 'android-device-smoke' ? 'Pixel release device' : 'iPhone release device',
      osVersion: item.id === 'android-device-smoke' ? 'Android 16' : 'iOS 20',
      testRunId: `smoke-${item.id}`
    };
  }
  if (item.id === 'store-submission') {
    attachment.storeSubmission = {
      googlePlayRecordId: 'google-play-release-1',
      appStoreConnectRecordId: 'app-store-connect-release-1'
    };
  }
  return { ...item, status: 'Attached', attachment };
});

describe('React Native release evidence contract', () => {
  it('builds an RN-only release evidence record that excludes the Kotlin tree from the active surface', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance,
      commands: passedCommands
    });

    assert.equal(evidence.app.entrypoint, 'index.js');
    assert.equal(evidence.app.androidPackage, 'com.relateai.app');
    assert.equal(evidence.app.iosBundleIdentifier, 'com.relateai.app');
    assert.equal(evidence.activeReleaseSurface.platform, 'React Native / Expo (Android and iOS)');
    assert.deepEqual(evidence.activeReleaseSurface.platforms, ['android', 'ios']);
    assert.equal(evidence.activeReleaseSurface.legacyKotlinGradleStatus, 'Reference only');
    assert.match(evidence.activeReleaseSurface.legacyKotlinGradleReleaseRole, /Excluded from RN release evidence/);
    assert.equal(evidence.activeReleaseSurface.legacyKotlinGradleArtifactPaths, null);
    assert.equal(evidence.releaseConfig.npmTestUsesFullNonIsolatedSuite, true);
    assert.deepEqual(evidence.releaseConfig.expoPlatforms, ['android', 'ios']);
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
      assessmentMode: 'source-only',
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
      assessmentMode: 'source-only',
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
      assessmentMode: 'source-only',
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
      deviceEvidence: attachedDeviceEvidence
    });

    assert.equal(evidence.permissions.forbiddenAndroidPermissionsAbsent, true);
    assert.equal(evidence.permissions.forbiddenAndroidPermissionsBlocked, true);
    assert.deepEqual(evidence.blockers, []);
    assert.equal(evidence.warnings.length, 0);
  });

  it('blocks required signed-build, device-smoke, and store evidence in production assessments', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: [],
      deviceEvidence: defaultDeviceEvidence.map(item =>
        item.id === 'android-device-smoke' ? { ...item, status: 'Failed' } : item
      )
    });

    assert.equal(evidence.assessmentMode, 'production');
    for (const id of [
      'signed-android-build',
      'signed-ios-build',
      'android-device-smoke',
      'ios-device-smoke',
      'store-submission'
    ]) {
      assert.ok(
        evidence.blockers.some(blocker => blocker.includes(id)),
        `${id} must block production evidence`
      );
    }
    assert.ok(evidence.blockers.some(blocker => /android-device-smoke \(Failed\)/.test(blocker)));
  });

  it('keeps pending external evidence explicit but non-blocking in source-only assessments', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: []
    });

    assert.equal(evidence.assessmentMode, 'source-only');
    assert.deepEqual(evidence.blockers, []);
    assert.ok(evidence.warnings.some(warning => warning.includes('signed-android-build (Pending)')));
    assert.ok(evidence.warnings.some(warning => warning.includes('store-submission (Pending)')));
  });

  it('fails closed for malformed, duplicate, or missing external device evidence', () => {
    const malformedEvidence = [
      ...defaultDeviceEvidence
        .filter(item => item.id !== 'store-submission')
        .map(item =>
          item.id === 'android-device-smoke'
            ? { ...item, status: 'unsupported', detail: '' }
            : { ...item, status: 'Attached' }
        ),
      { ...defaultDeviceEvidence[0], status: 'Attached' }
    ] as unknown as ReleaseEvidenceDeviceItem[];
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance,
      commands: passedCommands,
      deviceEvidence: malformedEvidence,
      legacyKotlinGradleArtifactPaths: []
    });

    assert.ok(evidence.blockers.some(blocker => /duplicate signed-android-build/i.test(blocker)));
    assert.ok(evidence.blockers.some(blocker => /android-device-smoke.*unsupported status/i.test(blocker)));
    assert.ok(evidence.blockers.some(blocker => /android-device-smoke.*missing detail/i.test(blocker)));
    assert.ok(evidence.blockers.some(blocker => /store-submission.*missing/i.test(blocker)));
  });

  it('rejects free-form Attached claims without candidate-bound evidence records', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance,
      commands: passedCommands,
      deviceEvidence: defaultDeviceEvidence.map(item => ({ ...item, status: 'Attached' })),
      legacyKotlinGradleArtifactPaths: []
    });

    for (const id of [
      'signed-android-build',
      'signed-ios-build',
      'android-device-smoke',
      'ios-device-smoke',
      'store-submission'
    ]) {
      assert.ok(evidence.blockers.some(blocker => blocker.includes(`${id} is marked Attached without a structured`)));
    }
  });

  it('cross-checks release commit, evidence URL, and device/store artifact identities', () => {
    const mismatchedEvidence = attachedDeviceEvidence.map(item => {
      if (item.id === 'signed-android-build') {
        return {
          ...item,
          attachment: {
            ...item.attachment!,
            candidate: { ...item.attachment!.candidate, commitSha: 'f'.repeat(40) }
          }
        };
      }
      if (item.id === 'android-device-smoke') {
        return {
          ...item,
          attachment: {
            ...item.attachment!,
            artifacts: { androidSha256: '3'.repeat(64) }
          }
        };
      }
      if (item.id === 'store-submission') {
        return {
          ...item,
          attachment: { ...item.attachment!, sourceUrl: 'http://untrusted.invalid/evidence' }
        };
      }
      return item;
    });
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      deviceEvidence: mismatchedEvidence
    });

    assert.ok(
      evidence.blockers.some(blocker => /signed-android-build.*not bound to the release commit/i.test(blocker))
    );
    assert.ok(evidence.blockers.some(blocker => /sourceUrl must be.*HTTPS/i.test(blocker)));
    assert.ok(
      evidence.blockers.some(blocker => /Android device-smoke.*not bound to the signed Android/i.test(blocker))
    );
  });

  it('rejects evidence URLs that could persist query credentials or fragment data', () => {
    const unsafeUrlEvidence = attachedDeviceEvidence.map(item => {
      if (item.id === 'signed-android-build') {
        return {
          ...item,
          attachment: {
            ...item.attachment!,
            sourceUrl: 'https://evidence.example.test/android?token=must-not-persist'
          }
        };
      }
      if (item.id === 'signed-ios-build') {
        return {
          ...item,
          attachment: {
            ...item.attachment!,
            sourceUrl: 'https://evidence.example.test/ios#private-fragment'
          }
        };
      }
      return item;
    });
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      provenance,
      commands: passedCommands,
      deviceEvidence: unsafeUrlEvidence
    });

    assert.ok(
      evidence.blockers.some(blocker => /signed-android-build.*credential-free HTTPS evidence URL/i.test(blocker))
    );
    assert.ok(evidence.blockers.some(blocker => /signed-ios-build.*credential-free HTTPS evidence URL/i.test(blocker)));
  });

  it('blocks a weakened coverage script even when the reported coverage command passed', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson: {
        ...packageJson,
        scripts: {
          ...packageJson.scripts,
          'test:coverage': 'tsx --test --test-isolation=none "src/**/*.test.ts"'
        }
      },
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: []
    });

    assert.ok(
      evidence.blockers.some(blocker => /package\.json script "test:coverage" must exactly match/i.test(blocker))
    );
  });

  it('blocks a no-op native prebuild script even when the reported native command passed', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson: {
        ...packageJson,
        scripts: {
          ...packageJson.scripts,
          'test:native-prebuild': 'node -e "process.exit(0)"'
        }
      },
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: []
    });

    assert.ok(
      evidence.blockers.some(blocker => /package\.json script "test:native-prebuild" must exactly match/i.test(blocker))
    );
  });

  it('blocks a no-op release-evidence alias even when the checked-in CLI is invoked directly', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson: {
        ...packageJson,
        scripts: {
          ...packageJson.scripts,
          'release:evidence': 'node -e "process.exit(0)"'
        }
      },
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: []
    });

    assert.ok(
      evidence.blockers.some(blocker => /package\.json script "release:evidence" must exactly match/i.test(blocker))
    );
  });

  it('blocks release evidence produced with an npm version other than the exact packageManager pin', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig,
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance: { ...provenance, npmVersion: '11.16.0' },
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: []
    });

    assert.ok(evidence.blockers.some(blocker => /used npm 11\.16\.0.*pins npm 11\.6\.0/i.test(blocker)));
  });

  it('blocks web or incomplete platform drift from the mobile release surface', () => {
    const evidence = buildReactNativeReleaseEvidence({
      packageJson,
      appConfig: {
        expo: {
          ...appConfig.expo,
          platforms: ['android', 'ios', 'web']
        }
      },
      easConfig,
      generatedAt: '2026-07-10T00:00:00.000Z',
      assessmentMode: 'source-only',
      provenance,
      commands: passedCommands,
      legacyKotlinGradleArtifactPaths: []
    });

    assert.ok(evidence.blockers.some(blocker => /exactly Android and iOS.*web is not a supported/i.test(blocker)));
    assert.deepEqual(evidence.releaseConfig.expoPlatforms, ['android', 'ios', 'web']);
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
      deviceEvidence: attachedDeviceEvidence
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
      deviceEvidence: attachedDeviceEvidence
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

    assert.ok(brokenEvidence.blockers.some(blocker => /package\.json script "test" must exactly match/i.test(blocker)));
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
      deviceEvidence: attachedDeviceEvidence
    });

    assert.ok(evidence.blockers.some(blocker => /dirty working tree/i.test(blocker)));
    assert.ok(evidence.blockers.some(blocker => /audit evidence did not execute the required command/i.test(blocker)));
  });
});
