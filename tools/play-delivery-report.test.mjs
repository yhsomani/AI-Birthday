import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import {
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';

import { createPlayDeliveredVerificationReport } from './create-play-delivered-verification-report.mjs';
import {
  createMobileScenarioFixture,
  createPerformanceEvidenceFixture,
  writeEvidenceFiles,
} from './release-evidence-test-fixtures.mjs';

const producer = readFileSync(
  'tools/create-play-delivered-verification-report.mjs',
  'utf8',
);
const nowMs = Date.parse('2026-07-11T12:00:00Z');
const sourceRevision = 'c0ffee12'.repeat(5);
const installedSha256 = 'ab'.repeat(32);
const installedSha1 = 'cd'.repeat(20);
const uploadSha256 = 'ef'.repeat(32);
const aabSha256 = '12'.repeat(32);
const baseSha256 = '34'.repeat(32);
const scenarioEvidence = evidenceKind =>
  createMobileScenarioFixture(evidenceKind, {
    sourceRevision,
    artifactSha256: baseSha256,
    signingCertificateSha256: installedSha256,
    artifactVersion: '1.0',
    evidenceSetId: 'android-play-2026-001',
    observedAt: '2026-07-11T12:00:00Z',
  });
const performanceEvidence = () =>
  createPerformanceEvidenceFixture('android', {
    sourceRevision,
    artifactSha256: baseSha256,
    applicationId: 'com.yashsomani.birthdayautopilot',
    version: '1.0',
    evidenceSetId: 'android-play-2026-001',
    measuredAt: '2026-07-11T12:00:00Z',
  });

const referenceFixture = field => {
  if (field === 'certifiedDeviceMatrix') {
    return scenarioEvidence('android-physical');
  }
  if (field === 'carrierMatrix') {
    return scenarioEvidence('android-carrier');
  }
  if (field === 'accessibilityEvidence') {
    return scenarioEvidence('android-accessibility');
  }
  if (field === 'performanceEvidence') return performanceEvidence();
  return undefined;
};

const fixture = () => {
  const evidenceRoot = mkdtempSync(join(tmpdir(), 'birthday-play-report-'));
  const references = [
    ['policyApproval', 'policy/review'],
    ['certifiedDeviceMatrix', 'device/matrix'],
    ['carrierMatrix', 'carrier/matrix'],
    ['performanceEvidence', 'performance/report'],
    ['accessibilityEvidence', 'accessibility/report'],
    ['supplyChainEvidence', 'supply-chain/report'],
    ['legalReview', 'legal/review'],
  ];
  const referenced = {};
  for (const [field, relative] of references) {
    const structured = referenceFixture(field);
    const bytes = Buffer.from(
      structured === undefined
        ? `${field} approved\n`
        : JSON.stringify(structured.document),
      'utf8',
    );
    const file = join(evidenceRoot, relative);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, bytes);
    if (structured !== undefined) {
      writeEvidenceFiles(evidenceRoot, structured.fileContents);
    }
    referenced[`${field}Reference`] = relative;
    referenced[`${field}Sha256`] = createHash('sha256')
      .update(bytes)
      .digest('hex');
  }
  const evidence = {
    schemaVersion: 4,
    approvals: [
      {
        tier: 'prod',
        status: 'approved',
        applicationId: 'com.yashsomani.birthdayautopilot',
        versionCode: 1,
        versionName: '1.0',
        sourceRevision,
        channel: 'google-play',
        installerPackage: 'com.android.vending',
        signingCertificateSha256: installedSha256,
        uploadSigningCertificateSha256: uploadSha256,
        minimumCertifiedApi: 29,
        maximumCertifiedApi: 36,
        smsPermissionPolicyApproved: true,
        installerAllowlistVerified: true,
        physicalSmsMatrixPassed: true,
        performanceBudgetsPassed: true,
        accessibilityMatrixPassed: true,
        nativeSupplyChainReviewed: true,
        telephonyStatePermissionApproved: true,
        appCheckEnforced: true,
        privacyMaterialsApproved: true,
        playUploadApproved: true,
        ...referenced,
        artifactApkSha256: baseSha256,
        artifactAabSha256: aabSha256,
        approvedAt: '2026-07-01T00:00:00Z',
        validUntil: '2026-08-01T00:00:00Z',
        launchCountries: ['US'],
      },
    ],
  };
  const evidenceBytes = Buffer.from(JSON.stringify(evidence), 'utf8');
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const publicKeyBytes = publicKey.export({ format: 'pem', type: 'spki' });
  const publicKeySpkiSha256 = createHash('sha256')
    .update(publicKey.export({ format: 'der', type: 'spki' }))
    .digest('hex');
  const observation = {
    schemaVersion: 1,
    observedAt: '2026-07-11T12:00:00.000Z',
    physicalDevice: true,
    deviceSerialSha256: '56'.repeat(32),
    deviceApi: 36,
    installerOfRecord: 'com.android.vending',
    applicationId: 'com.yashsomani.birthdayautopilot',
    versionCode: 1,
    versionName: '1.0',
    uploadAabSha256: aabSha256,
    deliveredBaseApkSha256: baseSha256,
    installedSigningCertificateSha1: installedSha1,
    installedSigningCertificateSha256: installedSha256,
    installedArtifacts: [
      {
        role: 'base',
        packagePath: '/data/app/release/base.apk',
        fileName: 'base.apk',
        bytes: 100,
        sha256: baseSha256,
        signingCertificateSha1: installedSha1,
        signingCertificateSha256: installedSha256,
      },
      {
        role: 'split',
        packagePath: '/data/app/release/split_config.arm64_v8a.apk',
        fileName: 'split_config.arm64_v8a.apk',
        bytes: 50,
        sha256: '78'.repeat(32),
        signingCertificateSha1: installedSha1,
        signingCertificateSha256: installedSha256,
      },
    ],
  };
  const input = {
    evidenceBytes,
    signatureBytes: sign(null, evidenceBytes, privateKey),
    publicKeyBytes,
    pinDocument: {
      schemaVersion: 1,
      algorithm: 'Ed25519',
      publicKeySpkiSha256,
    },
    evidenceRoot,
    tier: 'prod',
    observation,
    source: { errors: [], sourceRevision },
    nowMs,
  };
  return { evidenceRoot, input };
};

test('Play report producer revalidates authority, source, evidence root, and exact device observation', () => {
  assert.match(producer, /verifyDistributionEvidenceAuthority/u);
  assert.match(producer, /inspectCleanGitSource/u);
  assert.match(producer, /validateDistributionEvidence/u);
  assert.match(producer, /artifactMode: 'play-delivered-apk'/u);
  assert.match(producer, /installerOfRecord !== 'com\.android\.vending'/u);
  assert.match(producer, /observation\.physicalDevice !== true/u);
  assert.match(producer, /baseCount !== 1/u);
  assert.match(producer, /seenPaths\.has\(artifact\.packagePath\)/u);
});

test('Play report binds AAB, base and every split to both signer fingerprints', () => {
  for (const field of [
    'uploadAabSha256',
    'deliveredBaseApkSha256',
    'installedSigningCertificateSha1',
    'installedSigningCertificateSha256',
    'installedArtifacts',
    'signingCertificateSha1',
    'signingCertificateSha256',
    'signedEvidenceSha256',
    'authorityPublicKeySpkiSha256',
    'sourceRevision',
    'validUntil',
  ]) {
    assert.match(producer, new RegExp(field, 'u'));
  }
  assert.match(producer, /flag: 'wx'/u);
  assert.match(producer, /changed during report creation/u);
});

test('creates an authority-bound report for exact physical Play-installed bytes', () => {
  const { evidenceRoot, input } = fixture();
  try {
    const report = createPlayDeliveredVerificationReport(input);
    assert.equal(report.installerOfRecord, 'com.android.vending');
    assert.equal(report.uploadAabSha256, aabSha256);
    assert.equal(report.deliveredBaseApkSha256, baseSha256);
    assert.equal(report.installedSigningCertificateSha1, installedSha1);
    assert.equal(report.installedSigningCertificateSha256, installedSha256);
    assert.equal(report.installedArtifacts.length, 2);
    assert.equal(report.observedAt, '2026-07-11T12:00:00.000Z');
    assert.equal(report.validUntil, '2026-07-12T12:00:00.000Z');
  } finally {
    rmSync(evidenceRoot, { force: true, recursive: true });
  }
});

test('rejects fabricated installer/device/API/AAB/base/signer and split inventory', () => {
  const mutations = [
    observation => {
      observation.installerOfRecord = 'com.example.sideload';
    },
    observation => {
      observation.physicalDevice = false;
    },
    observation => {
      observation.deviceApi = 37;
    },
    observation => {
      observation.observedAt = '2026-07-10T11:59:59.000Z';
    },
    observation => {
      observation.observedAt = '2026-07-11T12:05:01.000Z';
    },
    observation => {
      observation.uploadAabSha256 = '90'.repeat(32);
    },
    observation => {
      observation.deliveredBaseApkSha256 = '90'.repeat(32);
    },
    observation => {
      observation.installedSigningCertificateSha256 = '90'.repeat(32);
    },
    observation => {
      observation.installedArtifacts[1].signingCertificateSha1 = '90'.repeat(
        20,
      );
    },
    observation => {
      observation.installedArtifacts.push(
        structuredClone(observation.installedArtifacts[0]),
      );
    },
  ];
  for (const mutate of mutations) {
    const { evidenceRoot, input } = fixture();
    try {
      mutate(input.observation);
      assert.throws(() => createPlayDeliveredVerificationReport(input));
    } finally {
      rmSync(evidenceRoot, { force: true, recursive: true });
    }
  }
});
