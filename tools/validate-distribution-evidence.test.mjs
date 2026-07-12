import assert from 'node:assert/strict';
import test from 'node:test';

import { validateDistributionEvidence } from './validate-distribution-evidence.mjs';

const NOW = Date.parse('2026-07-11T12:00:00Z');
const CERTIFICATE = 'ab'.repeat(32);
const expected = {
  tier: 'prod',
  applicationId: 'com.yashsomani.birthdayautopilot',
  versionCode: 1,
  signingCertificateSha256: CERTIFICATE,
  minimumSupportedApi: 29,
  targetApi: 36,
};

const validDocument = () => ({
  schemaVersion: 1,
  approvals: [
    {
      tier: 'prod',
      status: 'approved',
      applicationId: 'com.yashsomani.birthdayautopilot',
      versionCode: 1,
      channel: 'google-play',
      installerPackage: 'com.android.vending',
      signingCertificateSha256: CERTIFICATE,
      minimumCertifiedApi: 29,
      maximumCertifiedApi: 36,
      smsPermissionPolicyApproved: true,
      installerAllowlistVerified: true,
      physicalSmsMatrixPassed: true,
      telephonyStatePermissionApproved: true,
      appCheckEnforced: true,
      privacyMaterialsApproved: true,
      playUploadApproved: true,
      approvalReference: 'release-board/BA-2026-001',
      certifiedDeviceMatrixReference: 'device-matrix/BA-2026-001',
      carrierMatrixReference: 'carrier-matrix/BA-2026-001',
      legalReviewReference: 'legal/BA-2026-001',
      approvedAt: '2026-07-01T00:00:00Z',
      validUntil: '2026-08-01T00:00:00Z',
      launchCountries: ['IN'],
    },
  ],
});

test('accepts evidence bound to the exact package, version, certificate, and channel', () => {
  const result = validateDistributionEvidence(validDocument(), expected, NOW);
  assert.deepEqual(result.errors, []);
  assert.equal(result.approval?.installerPackage, 'com.android.vending');
});

test('rejects a different signing certificate', () => {
  const result = validateDistributionEvidence(
    validDocument(),
    { ...expected, signingCertificateSha256: 'cd'.repeat(32) },
    NOW,
  );
  assert.ok(
    result.errors.includes('signing certificate does not match the artifact'),
  );
});

test('rejects expired or excessively long evidence', () => {
  const expired = validDocument();
  expired.approvals[0].validUntil = '2026-07-01T00:00:00Z';
  assert.ok(
    validateDistributionEvidence(expired, expected, NOW).errors.includes(
      'approval is expired',
    ),
  );

  const excessive = validDocument();
  excessive.approvals[0].validUntil = '2028-01-01T00:00:00Z';
  assert.ok(
    validateDistributionEvidence(excessive, expected, NOW).errors.includes(
      'approval validity exceeds one year',
    ),
  );
});

test('rejects an inverted or uncertified Android API range', () => {
  const document = validDocument();
  document.approvals[0].minimumCertifiedApi = 36;
  document.approvals[0].maximumCertifiedApi = 29;
  assert.ok(
    validateDistributionEvidence(document, expected, NOW).errors.includes(
      'certified API range is invalid',
    ),
  );
});

test('requires evidence for every supported API through the target API', () => {
  const missingMinimum = validDocument();
  missingMinimum.approvals[0].minimumCertifiedApi = 30;
  assert.ok(
    validateDistributionEvidence(missingMinimum, expected, NOW).errors.includes(
      'certified API range is invalid',
    ),
  );

  const missingTarget = validDocument();
  missingTarget.approvals[0].maximumCertifiedApi = 35;
  assert.ok(
    validateDistributionEvidence(missingTarget, expected, NOW).errors.includes(
      'certified API range is invalid',
    ),
  );
});

test('rejects inverted timestamps and a mismatched Google Play installer', () => {
  const inverted = validDocument();
  inverted.approvals[0].approvedAt = '2026-08-02T00:00:00Z';
  inverted.approvals[0].validUntil = '2026-08-01T00:00:00Z';
  const invertedResult = validateDistributionEvidence(
    inverted,
    expected,
    Date.parse('2026-07-31T23:59:00Z'),
  );
  assert.ok(invertedResult.errors.includes('approval validity is inverted'));

  const wrongInstaller = validDocument();
  wrongInstaller.approvals[0].installerPackage = 'com.example.installer';
  assert.ok(
    validateDistributionEvidence(wrongInstaller, expected, NOW).errors.includes(
      'Google Play evidence must name com.android.vending',
    ),
  );
});

test('rejects unverified policy, installer, physical, privacy, and App Check gates', () => {
  for (const field of [
    'smsPermissionPolicyApproved',
    'installerAllowlistVerified',
    'physicalSmsMatrixPassed',
    'telephonyStatePermissionApproved',
    'appCheckEnforced',
    'privacyMaterialsApproved',
    'playUploadApproved',
  ]) {
    const document = validDocument();
    document.approvals[0][field] = false;
    assert.notEqual(
      validateDistributionEvidence(document, expected, NOW).errors.length,
      0,
      field,
    );
  }
});

test('rejects unknown fields and duplicate tier records', () => {
  const document = validDocument();
  document.approvals[0].unexpected = true;
  document.approvals.push({ ...document.approvals[0] });
  const result = validateDistributionEvidence(document, expected, NOW);
  assert.ok(result.errors.includes('approval tiers must be unique'));
  assert.ok(
    result.errors.some(error => error.includes('unsupported field unexpected')),
  );
});

test('rejects unsafe or whitespace-padded evidence references', () => {
  const document = validDocument();
  document.approvals[0].legalReviewReference = ' legal/BA-2026-001 ';
  assert.ok(
    validateDistributionEvidence(document, expected, NOW).errors.includes(
      'legalReviewReference is invalid',
    ),
  );
});
