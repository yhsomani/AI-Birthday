import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  copyFileSync,
  linkSync,
  mkdirSync,
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
import {
  createMobileScenarioFixture,
  createPerformanceEvidenceFixture,
  mergeEvidenceMaps,
  writeEvidenceFiles,
} from './release-evidence-test-fixtures.mjs';
import { validateIOSApplicationPolicy } from './verify-ios-release-artifact.mjs';

const NOW = Date.parse('2026-07-12T12:00:00Z');
const SOURCE = 'ab'.repeat(20);
const TEAM = 'A1B2C3D4E5';
const BUNDLE = 'com.yashsomani.birthdayautopilot';
const digest = value => createHash('sha256').update(value).digest('hex');
const samples = (count, value) => Array.from({ length: count }, () => value);
const oauthClientId = '123456789-test.apps.googleusercontent.com';
const reversedClientId = 'com.googleusercontent.apps.123456789-test';
const scenarioEvidence = evidenceKind => ({
  ...createMobileScenarioFixture(evidenceKind, {
    sourceRevision: SOURCE,
    artifactSha256: digest('ipa'),
    signingCertificateSha256: digest('exported-certificate'),
    artifactVersion: '1.0 (1)',
    evidenceSetId: 'ios-2026-07-12',
    observedAt: '2026-07-12T00:00:00Z',
  }),
});
const performanceEvidence = () =>
  createPerformanceEvidenceFixture('ios', {
    sourceRevision: SOURCE,
    artifactSha256: digest('ipa'),
    applicationId: BUNDLE,
    version: '1.0',
    evidenceSetId: 'ios-2026-07-12',
    measuredAt: '2026-07-12T00:00:00Z',
  });
const structuredFixtures = () => ({
  physicalDeviceMatrix: scenarioEvidence('ios-physical'),
  performance: performanceEvidence(),
  accessibility: scenarioEvidence('ios-accessibility'),
});
const structuredEvidence = () => ({
  ...Object.fromEntries(
    Object.entries(structuredFixtures()).map(([name, fixture]) => [
      name,
      fixture.document,
    ]),
  ),
});
const supportingFileContents = () =>
  mergeEvidenceMaps(
    ...Object.values(structuredFixtures()).map(fixture => fixture.fileContents),
  );
const supportingEvidenceFiles = () =>
  mergeEvidenceMaps(
    ...Object.values(structuredFixtures()).map(
      fixture => fixture.evidenceFiles,
    ),
  );
const referenceContent = name => {
  const structured = structuredEvidence()[name];
  return structured === undefined ? name : JSON.stringify(structured);
};

const references = () =>
  Object.fromEntries(
    IOS_RELEASE_REFERENCE_NAMES.map(name => [
      name,
      { path: `${name}.json`, sha256: digest(referenceContent(name)) },
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
    IOS_RELEASE_REFERENCE_NAMES.map(name => [
      name,
      digest(referenceContent(name)),
    ]),
  );

test('accepts exact inspected bytes, signing identity, Firebase identity, evidence files, and live approvals', () => {
  assert.deepEqual(
    validateIOSReleaseEvidence(document(), {
      observed: observed(),
      referenceDigests: referenceDigests(),
      structuredEvidence: structuredEvidence(),
      evidenceFiles: supportingEvidenceFiles(),
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
    structuredEvidence: structuredEvidence(),
    evidenceFiles: supportingEvidenceFiles(),
    now: NOW,
  }).errors.join('\n');
  assert.match(errors, /sourceRevision does not match/u);
  assert.match(errors, /artifact\.ipaSha256 does not match/u);
  assert.match(errors, /firebase\.projectId does not match/u);
  assert.match(errors, /signing\.teamIdentifier does not match/u);
  assert.match(errors, /privacyReview\.sha256 does not match/u);
});

test('iOS artifact gate executes structured scenarios and performance budgets', () => {
  const evidence = structuredEvidence();
  evidence.performance.shared.search10000Ms = samples(30, 151);
  evidence.physicalDeviceMatrix.rows[0].deviceModel = 'fixture phone';
  const errors = validateIOSReleaseEvidence(document(), {
    observed: observed(),
    referenceDigests: referenceDigests(),
    structuredEvidence: evidence,
    evidenceFiles: supportingEvidenceFiles(),
    now: NOW,
  }).errors.join('\n');
  assert.match(
    errors,
    /references\.performance: search10000Ms P95 151 exceeds 150/u,
  );
  assert.match(
    errors,
    /references\.physicalDeviceMatrix: rows\[0\]\.deviceModel must identify a real observed/u,
  );
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

test('collects the exact primary, scenario-raw, and performance-support inventory and rejects extras or links', () => {
  const root = mkdtempSync(path.join(tmpdir(), 'birthday-ios-evidence-'));
  try {
    const refs = references();
    for (const name of IOS_RELEASE_REFERENCE_NAMES) {
      writeFileSync(path.join(root, refs[name].path), referenceContent(name));
    }
    writeEvidenceFiles(root, supportingFileContents());
    assert.deepEqual(
      collectIOSReleaseReferenceDigests(root, refs),
      referenceDigests(),
    );

    writeFileSync(path.join(root, 'unreferenced.json'), 'extra');
    assert.throws(
      () => collectIOSReleaseReferenceDigests(root, refs),
      /exactly the primary, scenario-raw, and performance-support files/u,
    );
    rmSync(path.join(root, 'unreferenced.json'));

    mkdirSync(path.join(root, 'unreferenced-directory'));
    assert.throws(
      () => collectIOSReleaseReferenceDigests(root, refs),
      /exactly the primary, scenario-raw, and performance-support files/u,
    );
    rmSync(path.join(root, 'unreferenced-directory'), { recursive: true });

    rmSync(path.join(root, refs.privacyReview.path));
    linkSync(
      path.join(root, refs.artifactProvenance.path),
      path.join(root, refs.privacyReview.path),
    );
    assert.throws(
      () => collectIOSReleaseReferenceDigests(root, refs),
      /must not be hard linked/u,
    );
    rmSync(path.join(root, refs.privacyReview.path));
    writeFileSync(
      path.join(root, refs.privacyReview.path),
      referenceContent('privacyReview'),
    );

    rmSync(path.join(root, refs.privacyReview.path));
    writeFileSync(
      path.join(root, 'real-privacy.json'),
      referenceContent('privacyReview'),
    );
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
  const buildCandidateJob = workflow.slice(
    workflow.indexOf('  build-candidate:'),
    workflow.indexOf('\n  verify-release:'),
  );
  const verifyReleaseJob = workflow.slice(
    workflow.indexOf('  verify-release:'),
  );
  const buildCandidateHeader = buildCandidateJob.slice(
    0,
    buildCandidateJob.indexOf('\n    steps:'),
  );
  const verifyReleaseHeader = verifyReleaseJob.slice(
    0,
    verifyReleaseJob.indexOf('\n    steps:'),
  );
  const installStep = buildCandidateJob.slice(
    buildCandidateJob.indexOf(
      '- name: Install locked mobile and iOS dependency graphs',
    ),
    buildCandidateJob.indexOf(
      '- name: Fail closed unless protected signing, provider, and review inputs exist',
    ),
  );
  const candidateDownloadStep = verifyReleaseJob.slice(
    verifyReleaseJob.indexOf(
      '- name: Verify candidate provenance and download exact candidate',
    ),
    verifyReleaseJob.indexOf(
      '- name: Download authority-reviewed supporting bytes',
    ),
  );
  const supportingDownloadStep = verifyReleaseJob.slice(
    verifyReleaseJob.indexOf(
      '- name: Download authority-reviewed supporting bytes',
    ),
    verifyReleaseJob.indexOf(
      '- name: Decode authority-signed final evidence and archive',
    ),
  );
  assert.equal(schema.properties.status.const, 'approved');
  assert.match(workflow, /candidate is not a releasable artifact/u);
  assert.match(workflow, /verify-authority-approved-artifacts/u);
  assert.match(workflow, /environment: ios-production-release/u);
  assert.match(workflow, /gh run download/u);
  assert.match(workflow, /gh release download/u);
  assert.match(workflow, /IOS_SUPPORTING_EVIDENCE_REPOSITORY/u);
  assert.match(workflow, /IOS_SUPPORTING_EVIDENCE_READ_TOKEN/u);
  assert.doesNotMatch(workflow, /ios-release-supporting-evidence/u);
  assert.match(
    workflow,
    /repos\/\$GITHUB_REPOSITORY\/actions\/runs\/\$INPUT_CANDIDATE_RUN_ID/u,
  );
  assert.match(workflow, /\.head_sha[\s\S]*?= "\$GITHUB_SHA"/u);
  assert.match(
    workflow,
    /\.head_repository\.full_name[\s\S]*?= "\$GITHUB_REPOSITORY"/u,
  );
  assert.match(workflow, /\.conclusion[\s\S]*?= success/u);
  assert.match(workflow, /\.path[\s\S]*?ios-release-evidence\.yml/u);
  assert.doesNotMatch(buildCandidateHeader, /\$\{\{\s*secrets\./u);
  assert.doesNotMatch(verifyReleaseHeader, /\$\{\{\s*secrets\./u);
  assert.match(installStep, /npm ci/u);
  assert.doesNotMatch(installStep, /\$\{\{\s*secrets\./u);
  assert.match(candidateDownloadStep, /GH_TOKEN: \$\{\{ github\.token \}\}/u);
  assert.doesNotMatch(
    candidateDownloadStep,
    /IOS_SUPPORTING_EVIDENCE_READ_TOKEN/u,
  );
  assert.match(supportingDownloadStep, /IOS_SUPPORTING_EVIDENCE_READ_TOKEN/u);
  assert.doesNotMatch(supportingDownloadStep, /github\.token/u);
  assert.equal(
    [...workflow.matchAll(/persist-credentials: false/gmu)].length,
    2,
  );
  assert.ok(
    buildCandidateJob.indexOf(
      '- name: Install locked mobile and iOS dependency graphs',
    ) <
      buildCandidateJob.indexOf(
        '- name: Decode protected inputs and validate signing coordinates',
      ),
  );
  assert.ok(
    buildCandidateJob.indexOf(
      '- name: Remove ephemeral signing and provider material',
    ) <
      buildCandidateJob.indexOf(
        '- name: Generate candidate-only supply-chain and provenance evidence',
      ),
  );
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
