import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import {
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

import { composeHostingReleaseReport } from './compose-hosting-release-report.mjs';
import { createHostingCurrentLiveObservation } from './create-hosting-current-live-observation.mjs';
import { createHostingDeploymentProvenanceReport } from './create-hosting-deployment-provenance.mjs';
import {
  createHostingDeploymentArtifact,
  createHostingDeploymentManifest,
  stableJson,
} from './hosting-deployment-artifact.mjs';
import { parseReleaseConfig } from '../backend/hosting/tools/release-config.mjs';
import { verifyDistributionEvidenceAuthority } from './validate-distribution-evidence.mjs';
import {
  loadInstalledPlayApkArtifacts,
  loadProductionClosureReports,
  validateProductionReleaseClosure,
  validateProductionReleaseClosureTemplate,
} from './validate-production-release-closure.mjs';

const revision = '1'.repeat(40);
const authorityDigest = '2'.repeat(64);
const androidDigest = '3'.repeat(64);
const iosDigest = '4'.repeat(64);
const certificateDigest = '5'.repeat(64);
const certificateSha1 = 'a'.repeat(40);
const deliveredBaseDigest = 'b'.repeat(64);
const iosCertificateDigest = '6'.repeat(64);
const hostingConfigDigest = '7'.repeat(64);
const hostingArtifactDigest = '8'.repeat(64);
const genericDigest = '9'.repeat(64);
const nowMs = Date.parse('2026-01-10T00:00:00Z');
const componentValidUntil = '2026-01-25T00:00:00Z';
const playProofValidUntil = '2026-01-11T00:00:00Z';

const bytesOf = value => Buffer.from(`${JSON.stringify(value)}\n`, 'utf8');
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const admissionLeaseDigest = ({
  siteId,
  sourceRevision,
  runId,
  runAttempt,
  validUntil,
}) =>
  digest(
    Buffer.from(
      stableJson({
        schemaVersion: 1,
        siteId,
        sourceRevision,
        runId,
        runAttempt,
        validUntil,
      }),
      'utf8',
    ),
  );
const recaptchaEnterpriseSiteKey = 'provisioned-recaptcha-enterprise-site-key';
const recaptchaEnterpriseSiteKeySha256 = digest(
  Buffer.from(recaptchaEnterpriseSiteKey, 'utf8'),
);
const clone = value => structuredClone(value);

function fixture() {
  const reports = {
    androidDistribution: {
      schemaVersion: 1,
      product: 'birthday-autopilot-android-release-verification',
      status: 'verified',
      authorityPublicKeySpkiSha256: authorityDigest,
      sourceRevision: revision,
      validUntil: componentValidUntil,
      tier: 'prod',
      channel: 'google-play',
      fullVerifierKind: 'play-aab',
      applicationId: 'com.yashsomani.birthdayautopilot',
      versionCode: 11,
      versionName: '1.1.0',
      artifactFileName: 'birthday-1.1.0.aab',
      artifactSha256: androidDigest,
      artifactSigningCertificateSha256: genericDigest,
      installedSigningCertificateSha256: certificateDigest,
      firebase: {
        projectId: 'birthday-production',
        projectNumber: '123456789012',
        androidAppId: '1:123456789012:android:abcdef1234567890',
        webOauthClientId: '123456789012-web.apps.googleusercontent.com',
      },
      signedEvidenceSha256: genericDigest,
      fullVerificationReportSha256: genericDigest,
      verificationManifestSha256: genericDigest,
    },
    androidPlayDelivery: {
      schemaVersion: 1,
      product: 'birthday-autopilot-android-play-delivery-verification',
      status: 'verified',
      sourceRevision: revision,
      authorityPublicKeySpkiSha256: authorityDigest,
      observedAt: '2026-01-10T00:00:00Z',
      validUntil: playProofValidUntil,
      tier: 'prod',
      channel: 'google-play',
      physicalDevice: true,
      deviceSerialSha256: genericDigest,
      deviceApi: 36,
      installerOfRecord: 'com.android.vending',
      applicationId: 'com.yashsomani.birthdayautopilot',
      versionCode: 11,
      versionName: '1.1.0',
      uploadAabSha256: androidDigest,
      deliveredBaseApkSha256: deliveredBaseDigest,
      installedSigningCertificateSha1: certificateSha1,
      installedSigningCertificateSha256: certificateDigest,
      installedArtifacts: [
        {
          role: 'base',
          packagePath: '/data/app/release/base.apk',
          fileName: 'base.apk',
          bytes: 123456,
          sha256: deliveredBaseDigest,
          signingCertificateSha1: certificateSha1,
          signingCertificateSha256: certificateDigest,
        },
        {
          role: 'split',
          packagePath: '/data/app/release/split_config.arm64_v8a.apk',
          fileName: 'split_config.arm64_v8a.apk',
          bytes: 23456,
          sha256: genericDigest,
          signingCertificateSha1: certificateSha1,
          signingCertificateSha256: certificateDigest,
        },
      ],
      signedEvidenceSha256: genericDigest,
    },
    iosRelease: {
      schemaVersion: 1,
      product: 'birthday-autopilot-ios-release-verification',
      status: 'verified',
      sourceRevision: revision,
      validUntil: componentValidUntil,
      evidenceSha256: genericDigest,
      evidenceAuthorityPublicKeySpkiSha256: authorityDigest,
      observed: {
        sourceRevision: revision,
        artifact: {
          bundleIdentifier: 'com.yashsomani.birthdayautopilot',
          marketingVersion: '1.1.0',
          buildNumber: '11',
          ipaSha256: iosDigest,
        },
        firebase: {
          environment: 'prod',
          projectId: 'birthday-production',
          projectNumber: '123456789012',
          googleAppId: '1:123456789012:ios:abcdef1234567890',
          oauthClientId: '123456789012-ios.apps.googleusercontent.com',
          reversedClientId: 'com.googleusercontent.apps.123456789012-ios',
        },
        signing: {
          exportedCertificateSha256: iosCertificateDigest,
          teamIdentifier: 'ABCDEFGHIJ',
        },
      },
      referenceDigests: {},
    },
    cloud: {
      schemaVersion: 1,
      product: 'birthday-autopilot-cloud-release-verification',
      status: 'verified',
      sourceRevision: revision,
      evidenceSha256: genericDigest,
      authorityPublicKeySpkiSha256: authorityDigest,
      validUntil: componentValidUntil,
      project: {
        environment: 'production',
        projectId: 'birthday-production',
        projectNumber: '123456789012',
        androidAppId: '1:123456789012:android:abcdef1234567890',
        iosAppId: '1:123456789012:ios:abcdef1234567890',
        webAppId: '1:123456789012:web:abcdef1234567890',
        androidPackage: 'com.yashsomani.birthdayautopilot',
        iosBundle: 'com.yashsomani.birthdayautopilot',
      },
      clientTrust: {
        androidGooglePlay: {
          appCheckSigningCertificateSha256: certificateDigest,
          oauthAndroidClientId:
            '123456789012-android.apps.googleusercontent.com',
          oauthSigningCertificateSha1: certificateSha1,
          webOauthClientId: '123456789012-web.apps.googleusercontent.com',
        },
        ios: {
          oauthClientId: '123456789012-ios.apps.googleusercontent.com',
          reversedClientId: 'com.googleusercontent.apps.123456789012-ios',
          teamId: 'ABCDEFGHIJ',
        },
        web: {
          firebaseAppId: '1:123456789012:web:abcdef1234567890',
          recaptchaEnterpriseSiteKeySha256,
        },
      },
      hosting: {
        siteId: 'birthday-production',
        deployedVersionId: 'version-20260110',
        publicBaseUrl: 'https://birthday-autopilot.example.co/',
        releaseConfigSha256: hostingConfigDigest,
        deployedArtifactSha256: hostingArtifactDigest,
        deploymentManifestSha256: genericDigest,
        deploymentProvenanceSha256: genericDigest,
        deploymentConfigSha256: genericDigest,
        publicTreeSha256: genericDigest,
        providerOriginObservationSha256: genericDigest,
        firebaseWebConfigObservationSha256: genericDigest,
        currentLiveObservationSha256: genericDigest,
        firebaseConfigSha256: genericDigest,
        hostingSourceTreeSha256: genericDigest,
      },
      hostingReleaseControl: {
        repositoryId: '123456789',
        repositoryOwnerId: '987654321',
        releaseSecurityProjectId: 'birthday-release-security',
        releaseSecurityProjectNumber: '987654321098',
        applicationIamAnalysisScope: 'organizations/555555555555',
        releaseSecurityIamAnalysisScope: 'organizations/555555555555',
        observerServiceAccount:
          'hosting-current-live@birthday-production.iam.gserviceaccount.com',
        observerWifProvider:
          'projects/123456789012/locations/global/workloadIdentityPools/github/providers/hosting-current-live',
        admissionReaderServiceAccount:
          'hosting-admission-reader@birthday-release-security.iam.gserviceaccount.com',
        admissionReaderWifProvider:
          'projects/987654321098/locations/global/workloadIdentityPools/github/providers/hosting-admission-reader',
        deployServiceAccount:
          'hosting-deploy@birthday-production.iam.gserviceaccount.com',
        deployWifProvider:
          'projects/123456789012/locations/global/workloadIdentityPools/github/providers/hosting-deploy',
        admissionBucketName: 'birthday-release-admission',
        admissionBucketMetageneration: '7',
        auditLogBucketName:
          'projects/birthday-release-security/locations/global/buckets/birthday-release-audit-logs',
        auditSinkName:
          'projects/birthday-release-security/sinks/hosting-admission-audit',
      },
    },
    store: {
      schemaVersion: 1,
      product: 'birthday-autopilot-store-release-verification',
      status: 'verified',
      sourceRevision: revision,
      evidenceSha256: genericDigest,
      approvalScopeSha256: genericDigest,
      authorityPublicKeySpkiSha256: authorityDigest,
      validUntil: componentValidUntil,
      releaseCoordinates: {
        android: {
          applicationId: 'com.yashsomani.birthdayautopilot',
          versionCode: 11,
          versionName: '1.1.0',
          artifactKind: 'aab',
          artifactFileName: 'birthday-1.1.0.aab',
          artifactSha256: androidDigest,
          signingCertificateSha256: certificateDigest,
        },
        ios: {
          bundleId: 'com.yashsomani.birthdayautopilot',
          shortVersion: '1.1.0',
          buildNumber: '11',
          artifactKind: 'ipa',
          artifactFileName: 'birthday-1.1.0.ipa',
          artifactSha256: iosDigest,
          distributionCertificateSha256: iosCertificateDigest,
        },
      },
      publicIdentity: {
        publicBaseUrl: 'https://birthday-autopilot.example.co/',
        privacyUrl: 'https://birthday-autopilot.example.co/privacy/',
        termsUrl: 'https://birthday-autopilot.example.co/terms/',
        deletionUrl: 'https://birthday-autopilot.example.co/delete/',
      },
      artifactDigests: { android: androidDigest, ios: iosDigest },
      hostingConfigSha256: hostingConfigDigest,
    },
  };
  const cloudBytes = bytesOf(reports.cloud);
  const storeBytes = bytesOf(reports.store);
  reports.hosting = {
    schemaVersion: 1,
    product: 'birthday-autopilot-hosting-release-verification',
    status: 'verified',
    sourceRevision: revision,
    authorityPublicKeySpkiSha256: authorityDigest,
    validUntil: '2026-01-10T00:15:40Z',
    publicBaseUrl: 'https://birthday-autopilot.example.co/',
    projectId: 'birthday-production',
    projectNumber: '123456789012',
    admissionSecurityProjectId: 'birthday-release-security',
    applicationIamAnalysisScope: 'organizations/555555555555',
    releaseSecurityIamAnalysisScope: 'organizations/555555555555',
    webAppId: '1:123456789012:web:abcdef1234567890',
    siteId: 'birthday-production',
    deployedVersionId: 'version-20260110',
    releaseName: 'sites/birthday-production/releases/release-20260110',
    versionName: 'sites/birthday-production/versions/version-20260110',
    releaseTime: '2026-01-10T00:00:30Z',
    providerOriginObservationSha256: genericDigest,
    firebaseWebConfigObservationSha256: genericDigest,
    currentLiveObservationSha256: genericDigest,
    currentLiveCapturedAt: '2026-01-10T00:00:40Z',
    currentLiveObserverServiceAccount:
      'hosting-current-live@birthday-production.iam.gserviceaccount.com',
    currentLiveObserverWifProvider:
      'projects/123456789012/locations/global/workloadIdentityPools/github/providers/hosting-current-live',
    repositoryId: '123456789',
    repositoryOwnerId: '987654321',
    admissionSecurityProjectNumber: '987654321098',
    admissionBucketName: 'birthday-release-admission',
    admissionBucketMetageneration: '7',
    admissionObjectName: `hosting-production-change-freezes/birthday-production/${revision}/123456789/1.json`,
    admissionObjectGeneration: '1768003241000000',
    admissionObjectMetageneration: '1',
    admissionObjectTimeCreated: '2026-01-10T00:00:41Z',
    admissionObjectRetentionExpirationTime: '2026-01-10T00:15:41Z',
    admissionRetentionSeconds: 900,
    admissionLifecycleDeleteAgeDays: 1,
    admissionReaderServiceAccount:
      'hosting-admission-reader@birthday-release-security.iam.gserviceaccount.com',
    admissionReaderWifProvider:
      'projects/987654321098/locations/global/workloadIdentityPools/github/providers/hosting-admission-reader',
    admissionCheckCheckedAt: '2026-01-09T23:59:50Z',
    admissionCheckPageCount: 1,
    admissionCheckObjectCount: 0,
    admissionCheckManifestSha256: genericDigest,
    admissionCheckSnapshotSha256: genericDigest,
    admissionCheckRawRootSha256: genericDigest,
    admissionCheckRawRootBytes: 123,
    hostingBuildArtifactId: '7654321',
    hostingBuildArtifactDigest: 'd'.repeat(64),
    hostingAdmissionArtifactId: '7654322',
    hostingAdmissionArtifactDigest: 'e'.repeat(64),
    hostingDeployServiceAccount:
      'hosting-deploy@birthday-production.iam.gserviceaccount.com',
    hostingDeployWifProvider:
      'projects/123456789012/locations/global/workloadIdentityPools/github/providers/hosting-deploy',
    hostingDeployAuthenticatedAt: '2026-01-09T23:59:55Z',
    admissionAuditLogBucketName:
      'projects/birthday-release-security/locations/global/buckets/birthday-release-audit-logs',
    admissionAuditSinkName:
      'projects/birthday-release-security/sinks/hosting-admission-audit',
    admissionRunId: '123456789',
    admissionRunAttempt: '1',
    admissionValidUntil: '2026-01-10T00:15:40Z',
    admissionLeaseContentSha256: admissionLeaseDigest({
      siteId: 'birthday-production',
      sourceRevision: revision,
      runId: '123456789',
      runAttempt: '1',
      validUntil: '2026-01-10T00:15:40Z',
    }),
    admissionBucketObservationSha256: genericDigest,
    admissionObjectObservationSha256: genericDigest,
    releaseConfigSha256: hostingConfigDigest,
    deployedArtifactSha256: hostingArtifactDigest,
    deploymentManifestSha256: genericDigest,
    deploymentProvenanceSha256: genericDigest,
    deploymentConfigSha256: genericDigest,
    publicTreeSha256: genericDigest,
    firebaseConfigSha256: genericDigest,
    recaptchaEnterpriseSiteKeySha256,
    hostingSourceTreeSha256: genericDigest,
    cloudReportSha256: digest(cloudBytes),
    storeReportSha256: digest(storeBytes),
  };

  const names = [
    'androidDistribution',
    'androidPlayDelivery',
    'iosRelease',
    'cloud',
    'store',
    'hosting',
  ];
  const reportFiles = Object.fromEntries(
    names.map(name => {
      const bytes = bytesOf(reports[name]);
      return [name, { bytes: bytes.byteLength, sha256: digest(bytes) }];
    }),
  );
  const document = {
    $schema: './production-release-closure.schema.json',
    schemaVersion: 1,
    product: 'birthday-autopilot-production-release-closure',
    status: 'approved',
    source: {
      revision,
      repository: 'https://github.com/yhsomani/AI-Birthday.git',
      clean: true,
      authorityPublicKeySpkiSha256: authorityDigest,
    },
    validity: {
      generatedAt: '2026-01-09T00:00:00Z',
      validUntil: '2026-01-10T00:14:00Z',
    },
    release: {
      environment: 'production',
      firebase: {
        projectId: 'birthday-production',
        projectNumber: '123456789012',
        androidAppId: '1:123456789012:android:abcdef1234567890',
        iosAppId: '1:123456789012:ios:abcdef1234567890',
      },
      android: {
        applicationId: 'com.yashsomani.birthdayautopilot',
        versionCode: 11,
        versionName: '1.1.0',
        artifactKind: 'aab',
        artifactFileName: 'birthday-1.1.0.aab',
        artifactSha256: androidDigest,
        artifactSigningCertificateSha256: genericDigest,
        installedSigningCertificateSha1: certificateSha1,
        installedSigningCertificateSha256: certificateDigest,
        deliveredBaseApkFileName: 'play-device-base.apk',
        deliveredBaseApkSha256: deliveredBaseDigest,
      },
      ios: {
        bundleId: 'com.yashsomani.birthdayautopilot',
        shortVersion: '1.1.0',
        buildNumber: '11',
        artifactFileName: 'birthday-1.1.0.ipa',
        artifactSha256: iosDigest,
        distributionCertificateSha256: iosCertificateDigest,
      },
      hosting: {
        publicBaseUrl: 'https://birthday-autopilot.example.co/',
        releaseConfigSha256: hostingConfigDigest,
        deployedArtifactSha256: hostingArtifactDigest,
      },
    },
    components: Object.fromEntries(
      names.map(name => [
        name,
        {
          path: `reports/${name}.json`,
          sha256: reportFiles[name].sha256,
          bytes: reportFiles[name].bytes,
        },
      ]),
    ),
    finalApproval: {
      decision: 'approved',
      approvedAt: '2026-01-09T00:00:00Z',
      validUntil: '2026-01-25T00:00:00Z',
    },
  };
  const context = {
    nowMs,
    sourceRevision: revision,
    sourceClean: true,
    authorityPublicKeySpkiSha256: authorityDigest,
    reports,
    reportFiles,
    artifacts: {
      android: { sha256: androidDigest, fileName: 'birthday-1.1.0.aab' },
      androidDeliveredBase: {
        sha256: deliveredBaseDigest,
        fileName: 'play-device-base.apk',
      },
      ios: { sha256: iosDigest, fileName: 'birthday-1.1.0.ipa' },
      hosting: { sha256: hostingArtifactDigest },
    },
  };
  return { document, context };
}

test('one final closure composes AAB, physical Play, and all platform proofs', () => {
  const { document, context } = fixture();
  assert.deepEqual(validateProductionReleaseClosure(document, context), []);
});

test('closure independently hashes the retained Play base and every split byte', () => {
  const { context } = fixture();
  const root = mkdtempSync(path.join(tmpdir(), 'birthday-installed-apks-'));
  try {
    const artifacts = context.reports.androidPlayDelivery.installedArtifacts;
    for (const artifact of artifacts) {
      const bytes = Buffer.alloc(
        artifact.bytes,
        artifact.role === 'base' ? 1 : 2,
      );
      artifact.sha256 = digest(bytes);
      if (artifact.role === 'base') {
        context.reports.androidPlayDelivery.deliveredBaseApkSha256 =
          artifact.sha256;
      }
      writeFileSync(path.join(root, artifact.fileName), bytes);
    }
    const loaded = loadInstalledPlayApkArtifacts(
      root,
      context.reports.androidPlayDelivery,
    );
    assert.equal(loaded.records.length, 2);

    writeFileSync(path.join(root, 'unexpected.apk'), 'extra');
    assert.throws(
      () =>
        loadInstalledPlayApkArtifacts(
          root,
          context.reports.androidPlayDelivery,
        ),
      /exactly the reported base and split/u,
    );
    rmSync(path.join(root, 'unexpected.apk'));
    writeFileSync(path.join(root, artifacts[1].fileName), 'changed split');
    assert.throws(
      () =>
        loadInstalledPlayApkArtifacts(
          root,
          context.reports.androidPlayDelivery,
        ),
      /bytes do not match the report/u,
    );
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

test('closure fails closed across source, authority, project, artifact, Hosting, expiry, and report bytes', () => {
  const mutations = [
    ({ context }) => {
      context.reports.cloud.sourceRevision = 'a'.repeat(40);
    },
    ({ context }) => {
      context.reports.store.authorityPublicKeySpkiSha256 = 'a'.repeat(64);
    },
    ({ document }) => {
      document.release.firebase.projectId = 'different-production';
    },
    ({ context }) => {
      context.reports.androidDistribution.firebase.androidAppId =
        '1:123456789012:android:1111111111111111';
    },
    ({ context }) => {
      context.reports.androidDistribution.firebase.webOauthClientId =
        '123456789012-other.apps.googleusercontent.com';
    },
    ({ context }) => {
      context.artifacts.android.sha256 = 'a'.repeat(64);
    },
    ({ context }) => {
      context.artifacts.androidDeliveredBase.sha256 = 'a'.repeat(64);
    },
    ({ context }) => {
      context.artifacts.androidDeliveredBase.fileName = 'renamed-base.apk';
    },
    ({ document }) => {
      document.release.hosting.publicBaseUrl = 'https://other.example.co/';
    },
    ({ context }) => {
      context.reports.hosting.admissionRunId = '987654321';
    },
    ({ context }) => {
      context.reports.hosting.admissionLeaseContentSha256 = genericDigest;
    },
    ({ context }) => {
      context.reports.iosRelease.validUntil = '2026-01-09T00:00:00Z';
    },
    ({ document }) => {
      document.components.cloud.sha256 = 'a'.repeat(64);
    },
  ];
  for (const mutate of mutations) {
    const candidate = fixture();
    mutate(candidate);
    assert.notDeepEqual(
      validateProductionReleaseClosure(candidate.document, candidate.context),
      [],
    );
  }
});

test('closure requires exact physical Play installer, inventory, and SHA-1/SHA-256 trust', () => {
  const mutations = [
    ({ context }) => {
      context.reports.androidPlayDelivery.installerOfRecord =
        'com.example.sideload';
    },
    ({ context }) => {
      context.reports.androidPlayDelivery.physicalDevice = false;
    },
    ({ context }) => {
      context.reports.androidPlayDelivery.observedAt = '2026-01-08T00:00:00Z';
    },
    ({ context }) => {
      context.reports.androidPlayDelivery.validUntil = '2026-01-12T00:00:00Z';
    },
    ({ context }) => {
      context.reports.androidPlayDelivery.uploadAabSha256 = genericDigest;
    },
    ({ context }) => {
      context.reports.androidPlayDelivery.installedArtifacts[0].sha256 =
        genericDigest;
    },
    ({ context }) => {
      context.reports.androidPlayDelivery.installedArtifacts.push(
        clone(context.reports.androidPlayDelivery.installedArtifacts[0]),
      );
    },
    ({ context }) => {
      context.reports.cloud.clientTrust.androidGooglePlay.oauthSigningCertificateSha1 =
        'c'.repeat(40);
    },
    ({ context }) => {
      context.reports.cloud.clientTrust.androidGooglePlay.appCheckSigningCertificateSha256 =
        genericDigest;
    },
    ({ context }) => {
      context.reports.store.releaseCoordinates.android.signingCertificateSha256 =
        genericDigest;
    },
  ];
  for (const mutate of mutations) {
    const candidate = fixture();
    mutate(candidate);
    assert.notDeepEqual(
      validateProductionReleaseClosure(candidate.document, candidate.context),
      [],
    );
  }
});

test('unknown manifest fields and unsafe component paths are rejected', () => {
  const unknown = fixture();
  unknown.document.release.android.extra = true;
  assert.match(
    validateProductionReleaseClosure(unknown.document, unknown.context).join(
      ';',
    ),
    /release\.android fields/u,
  );

  const unsafe = fixture();
  unsafe.document.components.cloud.path = '../cloud.json';
  assert.match(
    validateProductionReleaseClosure(unsafe.document, unsafe.context).join(';'),
    /path is unsafe/u,
  );
});

test('report inventory is exact, distinct, and rejects links or empty/extra paths', () => {
  const { document, context } = fixture();
  const root = mkdtempSync(path.join(tmpdir(), 'birthday-release-closure-'));
  try {
    for (const [name, reference] of Object.entries(document.components)) {
      const absolute = path.join(root, reference.path);
      mkdirSync(path.dirname(absolute), { recursive: true });
      writeFileSync(absolute, bytesOf(context.reports[name]));
    }
    const loaded = loadProductionClosureReports(root, document.components);
    assert.equal(Object.keys(loaded.reports).length, 6);

    const duplicate = clone(document.components);
    duplicate.iosRelease.path = duplicate.androidDistribution.path;
    assert.throws(
      () => loadProductionClosureReports(root, duplicate),
      /six distinct paths/u,
    );

    writeFileSync(path.join(root, 'reports/extra.json'), '{}\n');
    assert.throws(
      () => loadProductionClosureReports(root, document.components),
      /exactly the six referenced reports/u,
    );
    rmSync(path.join(root, 'reports/extra.json'));

    mkdirSync(path.join(root, 'reports/empty'));
    assert.throws(
      () => loadProductionClosureReports(root, document.components),
      /unreferenced directory/u,
    );
    rmSync(path.join(root, 'reports/empty'), { recursive: true });

    const hardlink = path.join(root, document.components.cloud.path);
    const original = `${hardlink}.original`;
    writeFileSync(original, readFileSync(hardlink));
    rmSync(hardlink);
    linkSync(original, hardlink);
    assert.throws(
      () => loadProductionClosureReports(root, document.components),
      /non-hard-linked/u,
    );
    rmSync(hardlink);
    rmSync(original);
    writeFileSync(hardlink, bytesOf(context.reports.cloud));

    if (process.platform !== 'win32') {
      const realReports = path.join(root, 'real-reports');
      const linkedRoot = path.join(root, 'linked-root');
      mkdirSync(realReports);
      symlinkSync(realReports, linkedRoot, 'dir');
      assert.throws(
        () => loadProductionClosureReports(linkedRoot, document.components),
        /non-symlinked directory/u,
      );
    }
  } finally {
    rmSync(root, { force: true, recursive: true });
  }
});

test('reserved example domains and their subdomains can never be release origins', () => {
  for (const origin of [
    'https://example.com/',
    'https://birthday.example.org/',
    'https://nested.example.net/',
  ]) {
    const { document, context } = fixture();
    document.release.hosting.publicBaseUrl = origin;
    assert.match(
      validateProductionReleaseClosure(document, context).join(';'),
      /provisioned HTTPS origin/u,
    );
  }
});

test('checked-in template is intentionally unable to authorize production', () => {
  const template = JSON.parse(
    readFileSync('tools/production-release-closure.template.json', 'utf8'),
  );
  assert.deepEqual(validateProductionReleaseClosureTemplate(template), []);
  assert.notDeepEqual(
    validateProductionReleaseClosure(template, { nowMs }),
    [],
  );

  const schema = JSON.parse(
    readFileSync('tools/production-release-closure.schema.json', 'utf8'),
  );
  assert.deepEqual(new Set(schema.required), new Set(Object.keys(template)));
  assert.equal(schema.properties.status.const, 'approved');
  assert.equal(template.status, 'pending');
});

test('final manifest signature is bound to the same Ed25519 authority pin', () => {
  const { publicKey, privateKey } = generateKeyPairSync('ed25519');
  const publicKeyBytes = publicKey.export({ type: 'spki', format: 'pem' });
  const spki = publicKey.export({ type: 'spki', format: 'der' });
  const rawEvidence = Buffer.from('{"release":"exact"}\n', 'utf8');
  const signature = sign(null, rawEvidence, privateKey);
  const result = verifyDistributionEvidenceAuthority({
    rawEvidence,
    detachedSignature: signature,
    publicKeyBytes,
    pinDocument: {
      schemaVersion: 1,
      algorithm: 'Ed25519',
      publicKeySpkiSha256: digest(spki),
    },
  });
  assert.deepEqual(result.errors, []);
  assert.equal(result.publicKeySpkiSha256, digest(spki));

  const changed = verifyDistributionEvidenceAuthority({
    rawEvidence: Buffer.from('{"release":"changed"}\n', 'utf8'),
    detachedSignature: signature,
    publicKeyBytes,
    pinDocument: {
      schemaVersion: 1,
      algorithm: 'Ed25519',
      publicKeySpkiSha256: digest(spki),
    },
  });
  assert.notDeepEqual(changed.errors, []);
});

test('Hosting report deterministically composes exact cloud, store, config, and deployed bytes', () => {
  const { context } = fixture();
  const config = {
    schemaVersion: 1,
    developerDisplayName: 'Birthday Autopilot Team',
    publicBaseUrl: 'https://birthday-autopilot.example.co/',
    supportUrl: 'https://support.vendor.org/birthday/',
    recaptchaEnterpriseSiteKey,
    legalApprovalReference: 'legal-approval-2026-01',
    privacyApprovalReference: 'privacy-approval-2026-01',
    hindiCopyApprovalReference: 'hindi-copy-approval-2026-01',
    adminDeletionRunbookReference: 'admin-deletion-runbook-2026-01',
    verifiedAdminDeletionWorkflowTested: true,
    productionFirebaseDeletionSagaTested: true,
    privacyEffectiveDate: '2026-01-01',
    termsEffectiveDate: '2026-01-01',
  };
  const configBytes = Buffer.from(JSON.stringify(config), 'utf8');
  const parsedConfig = parseReleaseConfig(config);
  const runtimeBytes = Buffer.from(`${JSON.stringify(parsedConfig)}\n`, 'utf8');
  const firebaseConfigBytes = Buffer.from(
    JSON.stringify({
      hosting: {
        public: 'hosting/public',
        predeploy: ['npm --prefix hosting run build:release'],
        ignore: ['firebase.json', '**/.*', '**/node_modules/**'],
        cleanUrls: true,
        trailingSlash: true,
        headers: [],
      },
    }),
    'utf8',
  );
  const artifact = createHostingDeploymentArtifact({
    sourceRevision: revision,
    projectId: 'birthday-production',
    siteId: 'birthday-production',
    hostingSourceTreeSha256: genericDigest,
    firebaseConfigBytes,
    releaseConfigBytes: configBytes,
    publicFiles: [
      {
        path: 'hosting/public/index.html',
        bytes: 15,
        sha256: digest(Buffer.from('<h1>Hello</h1>\n')),
        contentBase64: Buffer.from('<h1>Hello</h1>\n').toString('base64'),
      },
      {
        path: 'hosting/public/runtime-config.json',
        bytes: runtimeBytes.byteLength,
        sha256: digest(runtimeBytes),
        contentBase64: runtimeBytes.toString('base64'),
      },
    ],
  });
  const artifactBytes = Buffer.from(`${stableJson(artifact)}\n`, 'utf8');
  const manifest = createHostingDeploymentManifest(artifactBytes, artifact);
  const manifestBytes = Buffer.from(`${stableJson(manifest)}\n`, 'utf8');
  const deployResult = {
    status: 'success',
    result: {
      hosting: 'sites/birthday-production/versions/version-20260110',
    },
  };
  const deployResultBytes = Buffer.from(`${JSON.stringify(deployResult)}\n`);
  const siteObservation = {
    name: 'projects/birthday-production/sites/birthday-production',
    defaultUrl: 'https://birthday-production.web.app',
  };
  const originObservation = {
    name: 'projects/birthday-production/sites/birthday-production/customDomains/birthday-autopilot.example.co',
    hostState: 'HOST_ACTIVE',
    ownershipState: 'OWNERSHIP_ACTIVE',
    issues: [],
  };
  const webConfig = {
    projectId: 'birthday-production',
    messagingSenderId: '123456789012',
    appId: '1:123456789012:web:abcdef1234567890',
  };
  const siteObservationBytes = Buffer.from(JSON.stringify(siteObservation));
  const originObservationBytes = Buffer.from(JSON.stringify(originObservation));
  const webConfigBytes = Buffer.from(JSON.stringify(webConfig));
  const admissionCheckFiles = [
    { path: 'bucket.json', bytes: Buffer.from('{"bucket":"observed"}\n') },
    { path: 'objects.json', bytes: Buffer.from('[]\n') },
    { path: 'page-1.json', bytes: Buffer.from('{"items":[]}\n') },
  ];
  const admissionCheckManifest = admissionCheckFiles.map(value => ({
    path: value.path,
    bytes: value.bytes.byteLength,
    sha256: digest(value.bytes),
  }));
  const admissionCheckManifestBytes = Buffer.from(
    `${stableJson(admissionCheckManifest)}\n`,
  );
  const admissionPass = {
    schemaVersion: 1,
    product: 'birthday-autopilot-hosting-admission-pass',
    status: 'passed',
    applicationProjectId: 'birthday-production',
    applicationProjectNumber: '123456789012',
    sourceRevision: revision,
    siteId: 'birthday-production',
    scannedPrefix: 'hosting-production-change-freezes/birthday-production/',
    repository: 'yhsomani/AI-Birthday',
    repositoryId: '123456789',
    repositoryOwnerId: '987654321',
    workflowPath: '.github/workflows/hosting-production-deploy.yml',
    runId: '1234',
    runAttempt: '1',
    buildArtifactId: '7654321',
    buildArtifactDigest: 'd'.repeat(64),
    securityProjectId: 'birthday-release-security',
    securityProjectNumber: '987654321098',
    bucketName: 'birthday-release-admission',
    bucketMetageneration: '7',
    retentionSeconds: 900,
    retentionLocked: true,
    publicAccessPrevention: 'enforced',
    uniformBucketLevelAccess: true,
    versioningEnabled: false,
    softDeleteEnabled: false,
    lifecycleDeleteAgeDays: 1,
    lifecyclePrefix: 'hosting-production-change-freezes/',
    checkedAt: '2026-01-09T23:59:50Z',
    pageCount: 1,
    objectCount: 0,
    checkManifestSha256: digest(admissionCheckManifestBytes),
    reader: {
      serviceAccount:
        'hosting-admission-reader@birthday-release-security.iam.gserviceaccount.com',
      workloadIdentityProvider:
        'projects/987654321098/locations/global/workloadIdentityPools/github/providers/hosting-admission-reader',
    },
  };
  const provenance = createHostingDeploymentProvenanceReport({
    artifact,
    artifactBytes,
    manifest,
    manifestBytes,
    deployResult,
    deployResultBytes,
    release: {
      name: 'sites/birthday-production/releases/release-20260110',
      version: {
        name: 'sites/birthday-production/versions/version-20260110',
      },
      type: 'DEPLOY',
      releaseTime: '2026-01-10T00:00:30Z',
    },
    version: {
      name: 'sites/birthday-production/versions/version-20260110',
      status: 'FINALIZED',
      createTime: '2026-01-10T00:00:05Z',
      finalizeTime: '2026-01-10T00:00:20Z',
      fileCount: '2',
      versionBytes: String(runtimeBytes.byteLength + 15),
    },
    projectNumber: '123456789012',
    webAppId: '1:123456789012:web:abcdef1234567890',
    siteObservation,
    siteObservationBytes,
    originObservation,
    originObservationBytes,
    webConfig,
    webConfigBytes,
    admissionPass,
    admissionPassBytes: Buffer.from(`${stableJson(admissionPass)}\n`),
    admissionCheckManifest,
    admissionCheckManifestBytes,
    admissionCheckFiles,
    deployIdentity: {
      serviceAccount:
        'hosting-deploy@birthday-production.iam.gserviceaccount.com',
      workloadIdentityProvider:
        'projects/123456789012/locations/global/workloadIdentityPools/github/providers/hosting-deploy',
      authenticatedAt: '2026-01-09T23:59:55Z',
      buildArtifactId: '7654321',
      buildArtifactDigest: 'd'.repeat(64),
      admissionArtifactId: '7654322',
      admissionArtifactDigest: 'e'.repeat(64),
    },
    builder: {
      repository: 'yhsomani/AI-Birthday',
      workflowPath: '.github/workflows/hosting-production-deploy.yml',
      runId: '1234',
      runAttempt: '1',
      deployStartedAt: '2026-01-10T00:00:00Z',
      deployCompletedAt: '2026-01-10T00:00:40Z',
    },
  });
  const provenanceBytes = Buffer.from(`${stableJson(provenance)}\n`, 'utf8');
  const liveChannel = {
    name: 'sites/birthday-production/channels/live',
    release: {
      name: 'sites/birthday-production/releases/release-20260110',
      version: {
        name: 'sites/birthday-production/versions/version-20260110',
      },
      type: 'DEPLOY',
      releaseTime: '2026-01-10T00:00:30Z',
    },
  };
  const liveVersion = {
    name: 'sites/birthday-production/versions/version-20260110',
    status: 'FINALIZED',
    createTime: '2026-01-10T00:00:05Z',
    finalizeTime: '2026-01-10T00:00:20Z',
    fileCount: '2',
    versionBytes: String(runtimeBytes.byteLength + 15),
  };
  const currentLiveBuilder = {
    repository: 'yhsomani/AI-Birthday',
    workflowPath: '.github/workflows/hosting-current-live-observation.yml',
    runId: '1235',
    runAttempt: '1',
  };
  const admissionBucket = {
    name: 'birthday-release-admission',
    projectNumber: '987654321098',
    metageneration: '7',
    retentionPolicy: {
      retentionPeriod: '900',
      effectiveTime: '2026-01-01T00:00:00Z',
      isLocked: true,
    },
    iamConfiguration: {
      publicAccessPrevention: 'enforced',
      uniformBucketLevelAccess: { enabled: true },
    },
    lifecycle: {
      rule: [
        {
          action: { type: 'Delete' },
          condition: {
            age: 1,
            matchesPrefix: ['hosting-production-change-freezes/'],
          },
        },
      ],
    },
  };
  const admissionContentBytes = Buffer.from(
    stableJson({
      schemaVersion: 1,
      siteId: 'birthday-production',
      sourceRevision: revision,
      runId: currentLiveBuilder.runId,
      runAttempt: currentLiveBuilder.runAttempt,
      validUntil: '2026-01-10T00:15:40.000Z',
    }),
  );
  const admissionObject = {
    bucket: admissionBucket.name,
    name: `hosting-production-change-freezes/birthday-production/${revision}/1235/1.json`,
    generation: '1768003241000000',
    metageneration: '1',
    contentType: 'application/json',
    size: String(admissionContentBytes.byteLength),
    timeCreated: '2026-01-10T00:00:41Z',
    retentionExpirationTime: '2026-01-10T00:15:41Z',
  };
  const currentLive = createHostingCurrentLiveObservation({
    sourceRevision: revision,
    projectId: 'birthday-production',
    projectNumber: '123456789012',
    admissionSecurityProjectId: 'birthday-release-security',
    webAppId: '1:123456789012:web:abcdef1234567890',
    siteId: 'birthday-production',
    publicBaseUrl: 'https://birthday-autopilot.example.co/',
    capturedAt: '2026-01-10T00:00:40Z',
    siteObservation,
    siteObservationBytes,
    originObservation,
    originObservationBytes,
    webConfig,
    webConfigBytes,
    liveChannel,
    liveChannelBytes: Buffer.from(JSON.stringify(liveChannel)),
    version: liveVersion,
    versionBytes: Buffer.from(JSON.stringify(liveVersion)),
    admissionBucket,
    admissionBucketBytes: Buffer.from(JSON.stringify(admissionBucket)),
    admissionObject,
    admissionObjectBytes: Buffer.from(JSON.stringify(admissionObject)),
    admissionContentBytes,
    executionIdentity: {
      serviceAccount:
        'hosting-current-live@birthday-production.iam.gserviceaccount.com',
      workloadIdentityProvider:
        'projects/123456789012/locations/global/workloadIdentityPools/github/providers/hosting-current-live',
      repositoryId: '123456789',
      repositoryOwnerId: '987654321',
    },
    builder: currentLiveBuilder,
  });
  const currentLiveBytes = Buffer.from(`${stableJson(currentLive)}\n`, 'utf8');
  context.reports.cloud.hosting.releaseConfigSha256 = digest(configBytes);
  context.reports.cloud.hosting.deployedArtifactSha256 = digest(artifactBytes);
  context.reports.cloud.hosting.deploymentManifestSha256 =
    digest(manifestBytes);
  context.reports.cloud.hosting.deploymentProvenanceSha256 =
    digest(provenanceBytes);
  context.reports.cloud.hosting.deploymentConfigSha256 =
    artifact.deploymentConfigSha256;
  context.reports.cloud.hosting.publicTreeSha256 = artifact.publicTreeSha256;
  context.reports.cloud.hosting.firebaseConfigSha256 =
    artifact.sourceFirebaseConfigSha256;
  context.reports.cloud.hosting.providerOriginObservationSha256 =
    provenance.provider.origin.originObservationSha256;
  context.reports.cloud.hosting.firebaseWebConfigObservationSha256 =
    provenance.provider.firebaseWebConfig.observationSha256;
  context.reports.cloud.hosting.currentLiveObservationSha256 =
    digest(currentLiveBytes);
  context.reports.store.hostingConfigSha256 = digest(configBytes);
  const composeInputs = {
    cloudReport: context.reports.cloud,
    storeReport: context.reports.store,
    releaseConfig: {
      rawBytes: configBytes,
      parsed: {
        publicBaseUrl: 'https://birthday-autopilot.example.co/',
        recaptchaEnterpriseSiteKey,
      },
    },
    deployedArtifactSha256: digest(artifactBytes),
    deploymentArtifact: artifact,
    deploymentArtifactBytes: artifactBytes,
    deploymentManifest: manifest,
    deploymentManifestBytes: manifestBytes,
    deploymentProvenance: provenance,
    deploymentProvenanceBytes: provenanceBytes,
    currentLiveObservation: currentLive,
    currentLiveObservationBytes: currentLiveBytes,
    cloudReportSha256: genericDigest,
    storeReportSha256: genericDigest,
    nowMs: Date.parse('2026-01-10T00:05:00Z'),
  };
  const report = composeHostingReleaseReport(composeInputs);
  assert.equal(report.releaseConfigSha256, digest(configBytes));
  assert.equal(report.deployedArtifactSha256, digest(artifactBytes));
  assert.equal(report.admissionBucketName, 'birthday-release-admission');
  assert.equal(report.admissionSecurityProjectNumber, '987654321098');
  assert.equal(
    report.currentLiveObserverServiceAccount,
    context.reports.cloud.hostingReleaseControl.observerServiceAccount,
  );
  assert.equal(
    report.admissionReaderWifProvider,
    context.reports.cloud.hostingReleaseControl.admissionReaderWifProvider,
  );
  assert.equal(report.admissionRunId, '1235');
  assert.equal(report.admissionRunAttempt, '1');
  assert.equal(report.admissionValidUntil, '2026-01-10T00:15:40.000Z');
  assert.equal(
    report.admissionLeaseContentSha256,
    currentLive.admissionLease.contentSha256,
  );

  const superseded = structuredClone(currentLive);
  superseded.live.versionId = 'newer-version';
  superseded.live.versionName =
    'sites/birthday-production/versions/newer-version';
  assert.throws(
    () =>
      composeHostingReleaseReport({
        ...composeInputs,
        currentLiveObservation: superseded,
      }),
    /current live Hosting state/u,
  );
  assert.throws(
    () =>
      composeHostingReleaseReport({
        ...composeInputs,
        nowMs: Date.parse('2026-01-10T00:20:00Z'),
      }),
    /stale/u,
  );

  const weakerCloudIdentity = structuredClone(context.reports.cloud);
  weakerCloudIdentity.hostingReleaseControl.deployWifProvider =
    'projects/123456789012/locations/global/workloadIdentityPools/weaker/providers/hosting-deploy';
  assert.throws(
    () =>
      composeHostingReleaseReport({
        ...composeInputs,
        cloudReport: weakerCloudIdentity,
      }),
    /release-control/u,
  );

  assert.throws(
    () =>
      composeHostingReleaseReport({
        cloudReport: context.reports.cloud,
        storeReport: context.reports.store,
        releaseConfig: {
          rawBytes: Buffer.from('changed', 'utf8'),
          parsed: {
            publicBaseUrl: 'https://birthday-autopilot.example.co/',
            recaptchaEnterpriseSiteKey,
          },
        },
        deployedArtifactSha256: hostingArtifactDigest,
        deploymentArtifact: artifact,
        deploymentArtifactBytes: artifactBytes,
        deploymentManifest: manifest,
        deploymentManifestBytes: manifestBytes,
        deploymentProvenance: provenance,
        deploymentProvenanceBytes: provenanceBytes,
        currentLiveObservation: currentLive,
        currentLiveObservationBytes: currentLiveBytes,
        cloudReportSha256: genericDigest,
        storeReportSha256: genericDigest,
        nowMs: Date.parse('2026-01-10T00:05:00Z'),
      }),
    /config bytes/u,
  );
});
