import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  copyFileSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  collectIOSReleaseReferenceDigests,
  IOS_RELEASE_REFERENCE_NAMES,
  validateIOSReleaseEvidence,
} from './ios-release-evidence.mjs';
import { validateIOSApplicationPolicy } from './verify-ios-release-artifact.mjs';

const NOW = Date.parse('2026-07-12T12:00:00Z');
const SOURCE = 'ab'.repeat(20);
const TEAM = 'A1B2C3D4E5';
const BUNDLE = 'com.yashsomani.birthdayautopilot';
const digest = value => createHash('sha256').update(value).digest('hex');
const oauthClientId = '123456789-test.apps.googleusercontent.com';
const reversedClientId = 'com.googleusercontent.apps.123456789-test';

const references = () =>
  Object.fromEntries(
    IOS_RELEASE_REFERENCE_NAMES.map(name => [
      name,
      { path: `${name}.json`, sha256: digest(name) },
    ]),
  );

const observed = () => ({
  sourceRevision: SOURCE,
  artifact: {
    archiveTreeSha256: digest('archive'),
    ipaSha256: digest('ipa'),
    exportOptionsSha256: digest('export-options'),
    bundleIdentifier: BUNDLE,
    marketingVersion: '1.0',
    buildNumber: '1',
    minimumOSVersion: '15.1',
    platform: 'iphoneos',
    appBinarySha256: digest('app-binary'),
    embeddedFrameworksManifestSha256: digest('frameworks'),
  },
  firebase: {
    environment: 'prod',
    projectId: 'birthday-prod',
    projectNumber: '123456789',
    googleAppId: '1:123456789:ios:abcdef1234567890',
    oauthClientId,
    reversedClientId,
    configSha256: digest('firebase-config'),
    apiKeySha256: digest('firebase-api-key'),
  },
  signing: {
    distributionMethod: 'app-store-connect',
    teamIdentifier: TEAM,
    archiveCertificateExpiration: '2026-10-01T00:00:00.000Z',
    archiveCertificateSha256: digest('archive-certificate'),
    exportedCertificateExpiration: '2026-10-01T00:00:00.000Z',
    exportedCertificateSha256: digest('exported-certificate'),
    archiveProvisioningProfileUuid: 'AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE',
    archiveProvisioningProfileName: 'Birthday Archive Distribution',
    archiveProvisioningProfileExpiration: '2026-10-01T00:00:00.000Z',
    exportedProvisioningProfileUuid: '11111111-2222-4333-8444-555555555555',
    exportedProvisioningProfileName: 'Birthday App Store Distribution',
    exportedProvisioningProfileExpiration: '2026-10-01T00:00:00.000Z',
    applicationIdentifier: `${TEAM}.${BUNDLE}`,
  },
  security: {
    entitlementsSha256: digest('entitlements'),
    infoPlistSha256: digest('info'),
    privacyManifestSha256: digest('privacy'),
    arm64Only: true,
    debugEntitlementAbsent: true,
    appAttestProduction: true,
    noForbiddenCapabilities: true,
    noForbiddenUrlSchemes: true,
  },
});

const document = () => {
  const artifactObservation = observed();
  return {
    $schema: './ios-release-evidence.schema.json',
    schemaVersion: 1,
    product: 'birthday-autopilot-ios',
    status: 'approved',
    sourceRevision: SOURCE,
    artifact: artifactObservation.artifact,
    firebase: artifactObservation.firebase,
    signing: artifactObservation.signing,
    security: {
      ...artifactObservation.security,
      backupExclusionVerified: true,
      protectedStoreVerified: true,
      debugProviderAbsent: true,
      privacyManifestReviewed: true,
    },
    references: references(),
    approvals: {
      approvedAt: '2026-07-12T00:00:00Z',
      validUntil: '2026-08-12T00:00:00Z',
      nativeSupplyChainReviewed: true,
      firebaseAndAppCheckVerified: true,
      protectedStorageAndBackupVerified: true,
      physicalDeviceMatrixPassed: true,
      performanceBudgetsPassed: true,
      accessibilityPassed: true,
      privacyMaterialsApproved: true,
      appStoreSubmissionMaterialsApproved: true,
      loginServicesRationaleApproved: true,
      accountDeletionVerified: true,
      rollbackAndIncidentReady: true,
      companionOnlyClaimsApproved: true,
      noBackgroundSmsVerified: true,
    },
  };
};

const referenceDigests = () =>
  Object.fromEntries(
    IOS_RELEASE_REFERENCE_NAMES.map(name => [name, digest(name)]),
  );

test('accepts exact inspected bytes, signing identity, Firebase identity, evidence files, and live approvals', () => {
  assert.deepEqual(
    validateIOSReleaseEvidence(document(), {
      observed: observed(),
      referenceDigests: referenceDigests(),
      now: NOW,
    }).errors,
    [],
  );
});

test('fails closed on artifact, source, signing, Firebase, or referenced-byte drift', () => {
  const value = document();
  const inspection = observed();
  inspection.sourceRevision = 'cd'.repeat(20);
  inspection.artifact.ipaSha256 = digest('changed-ipa');
  inspection.firebase.projectId = 'different-project';
  inspection.signing.teamIdentifier = 'Z9Y8X7W6V5';
  const digests = referenceDigests();
  digests.privacyReview = digest('changed-privacy-review');

  const errors = validateIOSReleaseEvidence(value, {
    observed: inspection,
    referenceDigests: digests,
    now: NOW,
  }).errors.join('\n');
  assert.match(errors, /sourceRevision does not match/u);
  assert.match(errors, /artifact\.ipaSha256 does not match/u);
  assert.match(errors, /firebase\.projectId does not match/u);
  assert.match(errors, /signing\.teamIdentifier does not match/u);
  assert.match(errors, /privacyReview\.sha256 does not match/u);
});

test('rejects template sentinels, stale or overlong approval, and approval beyond profile expiry', () => {
  const template = JSON.parse(
    readFileSync('tools/ios-release-evidence.template.json', 'utf8'),
  );
  const templateErrors = validateIOSReleaseEvidence(template, {
    now: NOW,
  }).errors.join('\n');
  assert.match(templateErrors, /status must be approved/u);
  assert.match(templateErrors, /template zero digest/u);
  assert.match(templateErrors, /must be true/u);

  const value = document();
  value.approvals.approvedAt = '2026-01-01T00:00:00Z';
  value.approvals.validUntil = '2026-11-01T00:00:00Z';
  const errors = validateIOSReleaseEvidence(value, { now: NOW }).errors.join(
    '\n',
  );
  assert.match(errors, /exceeds 90 days/u);
  assert.match(errors, /outlives the .* provisioning profile/u);
});

test('collects only stable in-root regular supporting evidence and rejects symlinks', () => {
  const root = mkdtempSync(path.join(tmpdir(), 'birthday-ios-evidence-'));
  try {
    const refs = references();
    for (const name of IOS_RELEASE_REFERENCE_NAMES) {
      writeFileSync(path.join(root, refs[name].path), name);
    }
    assert.deepEqual(
      collectIOSReleaseReferenceDigests(root, refs),
      referenceDigests(),
    );

    rmSync(path.join(root, refs.privacyReview.path));
    writeFileSync(path.join(root, 'real-privacy.json'), 'privacyReview');
    symlinkSync('real-privacy.json', path.join(root, refs.privacyReview.path));
    assert.throws(
      () => collectIOSReleaseReferenceDigests(root, refs),
      /must not contain symbolic links/u,
    );
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

const safeApplication = () => {
  const leafCertificate = Buffer.from('distribution-leaf');
  const certificateSha256 = digest(leafCertificate);
  return {
    info: {
      CFBundleIdentifier: BUNDLE,
      BirthdaySourceRevision: SOURCE,
      BirthdayFirebaseEnvironment: 'prod',
      BirthdayExpectedFirebaseProjectID: 'birthday-prod',
      BirthdayGoogleReversedClientID: reversedClientId,
      MinimumOSVersion: '15.1',
      DTPlatformName: 'iphoneos',
      CFBundleSupportedPlatforms: ['iPhoneOS'],
      UIRequiredDeviceCapabilities: ['arm64'],
      UIBackgroundModes: ['fetch'],
      BGTaskSchedulerPermittedIdentifiers: [
        'com.yashsomani.birthdayautopilot.people-refresh',
      ],
      NSAppTransportSecurity: {
        NSAllowsArbitraryLoads: false,
        NSAllowsLocalNetworking: false,
      },
      CFBundleURLTypes: [
        {
          CFBundleTypeRole: 'Editor',
          CFBundleURLSchemes: [reversedClientId],
        },
      ],
      FirebaseAppCheckTokenAutoRefreshEnabled: true,
      RCTNewArchEnabled: true,
    },
    privacy: {
      NSPrivacyTracking: false,
      NSPrivacyAccessedAPITypes: [],
    },
    entitlements: {
      'application-identifier': `${TEAM}.${BUNDLE}`,
      'com.apple.developer.default-data-protection': 'NSFileProtectionComplete',
      'com.apple.developer.team-identifier': TEAM,
      'com.apple.developer.devicecheck.appattest-environment': 'production',
      'keychain-access-groups': [`${TEAM}.${BUNDLE}`],
    },
    profile: {
      TeamIdentifier: [TEAM],
      ApplicationIdentifierPrefix: [TEAM],
      ExpirationDate: '2026-10-01T00:00:00Z',
      DeveloperCertificates: [leafCertificate.toString('base64')],
      Entitlements: {
        'application-identifier': `${TEAM}.${BUNDLE}`,
        'com.apple.developer.default-data-protection':
          'NSFileProtectionComplete',
        'com.apple.developer.devicecheck.appattest-environment': 'production',
        'get-task-allow': false,
      },
    },
    signature: { teamIdentifier: TEAM, certificateSha256 },
    firebase: {
      BUNDLE_ID: BUNDLE,
      PROJECT_ID: 'birthday-prod',
      GCM_SENDER_ID: '123456789',
      GOOGLE_APP_ID: '1:123456789:ios:abcdef1234567890',
      CLIENT_ID: oauthClientId,
      REVERSED_CLIENT_ID: reversedClientId,
      API_KEY: 'test-placeholder-not-a-secret-0001',
    },
    architectures: ['arm64'],
    frameworks: [
      { path: 'Frameworks/hermes.framework', architectures: ['arm64'] },
    ],
    forbiddenBundleEntries: [],
  };
};

test('accepts the minimal production Companion app signing and privacy policy', () => {
  assert.deepEqual(
    validateIOSApplicationPolicy(safeApplication(), NOW).errors,
    [],
  );
});

test(
  'macOS codesign writes parseable entitlement XML to the requested file',
  { skip: process.platform !== 'darwin' },
  () => {
    const root = mkdtempSync(path.join(tmpdir(), 'birthday-codesign-xml-'));
    const binary = path.join(root, 'fixture');
    const input = path.join(root, 'input.plist');
    const output = path.join(root, 'output.plist');
    try {
      copyFileSync('/bin/echo', binary);
      writeFileSync(
        input,
        `<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n<plist version="1.0"><dict><key>application-identifier</key><string>${TEAM}.${BUNDLE}</string></dict></plist>\n`,
      );
      const sign = spawnSync(
        '/usr/bin/codesign',
        ['-f', '-s', '-', '--entitlements', input, binary],
        { encoding: 'utf8' },
      );
      assert.equal(sign.status, 0, sign.stderr);
      const extract = spawnSync(
        '/usr/bin/codesign',
        ['-d', '--entitlements', output, '--xml', binary],
        { encoding: 'utf8' },
      );
      assert.equal(extract.status, 0, extract.stderr);
      const lint = spawnSync('/usr/bin/plutil', ['-lint', output], {
        encoding: 'utf8',
      });
      assert.equal(lint.status, 0, lint.stderr);
      assert.match(readFileSync(output, 'utf8'), /<plist version="1\.0">/u);
    } finally {
      rmSync(root, { force: true, recursive: true });
    }
  },
);

test('rejects debug/forbidden capabilities, extra URL schemes, simulator slices, and device profiles', () => {
  const value = safeApplication();
  value.entitlements['get-task-allow'] = true;
  value.entitlements['aps-environment'] = 'production';
  value.entitlements['com.apple.developer.default-data-protection'] =
    'NSFileProtectionCompleteUnlessOpen';
  value.info.CFBundleURLTypes[0].CFBundleURLSchemes.push('birthday-autopilot');
  value.architectures.push('x86_64');
  value.frameworks[0].architectures.push('x86_64');
  value.profile.ProvisionedDevices = ['device-id'];
  value.forbiddenBundleEntries.push('PlugIns');
  const errors = validateIOSApplicationPolicy(value, NOW).errors.join('\n');
  assert.match(errors, /get-task-allow/u);
  assert.match(errors, /aps-environment/u);
  assert.match(errors, /complete data protection/u);
  assert.match(errors, /URL schemes/u);
  assert.match(errors, /application binary must contain only arm64/u);
  assert.match(errors, /hermes\.framework must contain only arm64/u);
  assert.match(errors, /not an App Store distribution profile/u);
  assert.match(errors, /unapproved extension/u);
});

test('requires the App Store profile to authorize production App Attest', () => {
  const value = safeApplication();
  delete value.profile.Entitlements[
    'com.apple.developer.devicecheck.appattest-environment'
  ];
  assert.match(
    validateIOSApplicationPolicy(value, NOW).errors.join('\n'),
    /profile.*production App Attest/u,
  );
});

test('repository contract keeps the template unusable and release verification separate from unsigned CI', () => {
  const schema = JSON.parse(
    readFileSync('tools/ios-release-evidence.schema.json', 'utf8'),
  );
  const workflow = readFileSync(
    '.github/workflows/ios-release-evidence.yml',
    'utf8',
  );
  const verifier = readFileSync(
    'tools/verify-ios-release-artifact.mjs',
    'utf8',
  );
  const ci = readFileSync('.github/workflows/ci.yml', 'utf8');
  assert.equal(schema.properties.status.const, 'approved');
  assert.match(workflow, /candidate is not a releasable artifact/u);
  assert.match(workflow, /verify-authority-approved-artifacts/u);
  assert.match(workflow, /environment: ios-production-release/u);
  assert.match(workflow, /gh run download/u);
  assert.match(workflow, /npm run ios:release:inspect/u);
  assert.match(workflow, /candidate-observation-not-release\.json/u);
  assert.match(workflow, /npm run ios:release:verify/u);
  assert.match(verifier, /\/usr\/bin\/codesign/u);
  assert.match(verifier, /'--entitlements',[\s\S]*?'--xml'/u);
  assert.match(verifier, /\/usr\/bin\/security/u);
  assert.match(verifier, /\/usr\/bin\/plutil/u);
  assert.match(verifier, /\/usr\/bin\/unzip/u);
  assert.match(verifier, /\/usr\/bin\/ditto/u);
  assert.match(verifier, /\/usr\/bin\/lipo/u);
  assert.match(ci, /name: iOS unsigned simulator build/u);
  assert.doesNotMatch(ci, /ios-production-release/u);
  const actionReferences = [...workflow.matchAll(/^\s*uses:\s+(\S+)/gmu)].map(
    match => match[1],
  );
  assert.ok(actionReferences.length > 0);
  for (const reference of actionReferences) {
    assert.match(reference, /@[a-f0-9]{40}$/u);
  }
});
