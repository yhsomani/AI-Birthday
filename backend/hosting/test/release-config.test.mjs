import assert from 'node:assert/strict';
import { test } from 'node:test';

import { parseReleaseConfig } from '../tools/release-config.mjs';

function validConfig(overrides = {}) {
  return {
    schemaVersion: 1,
    publicBaseUrl: 'https://configured-release-site.web.app/',
    developerDisplayName: 'Approved Public Developer',
    supportUrl: 'https://verified-account-support.web.app/delete-request',
    recaptchaEnterpriseSiteKey: '6Lc_release_test_site_key_1234567890',
    privacyEffectiveDate: '2026-07-12',
    termsEffectiveDate: '2026-07-12',
    legalApprovalReference: 'LEGAL-APPROVED-2026-0001',
    privacyApprovalReference: 'PRIVACY-APPROVED-2026-0001',
    hindiCopyApprovalReference: 'HINDI-REVIEWED-2026-0001',
    adminDeletionRunbookReference: 'DELETE-RUNBOOK-2026-0001',
    verifiedAdminDeletionWorkflowTested: true,
    productionFirebaseDeletionSagaTested: true,
    ...overrides,
  };
}

test('emits only public runtime values from approved release evidence', () => {
  const result = parseReleaseConfig(validConfig());
  assert.deepEqual(result, {
    schemaVersion: 1,
    publicBaseUrl: 'https://configured-release-site.web.app/',
    developerDisplayName: 'Approved Public Developer',
    supportUrl: 'https://verified-account-support.web.app/delete-request',
    recaptchaEnterpriseSiteKey: '6Lc_release_test_site_key_1234567890',
    privacyEffectiveDate: '2026-07-12',
    termsEffectiveDate: '2026-07-12',
    functionsRegion: 'asia-south1',
  });
  assert.equal('legalApprovalReference' in result, false);
  assert.equal('adminDeletionRunbookReference' in result, false);
});

test('rejects placeholder identity and reserved or insecure origins', () => {
  assert.throws(() =>
    parseReleaseConfig(validConfig({ developerDisplayName: 'TBD' })),
  );
  assert.throws(() =>
    parseReleaseConfig(validConfig({ publicBaseUrl: 'https://site.invalid/' })),
  );
  assert.throws(() =>
    parseReleaseConfig(
      validConfig({
        publicBaseUrl: 'https://configured-release-site.web.app/app',
      }),
    ),
  );
  assert.throws(() =>
    parseReleaseConfig(
      validConfig({ supportUrl: 'http://support.example.org/' }),
    ),
  );
});

test('requires a separately provisioned verified-admin support workflow', () => {
  assert.throws(() =>
    parseReleaseConfig(
      validConfig({
        supportUrl: 'https://configured-release-site.web.app/support/',
      }),
    ),
  );
  assert.throws(() =>
    parseReleaseConfig(
      validConfig({ verifiedAdminDeletionWorkflowTested: false }),
    ),
  );
  assert.throws(() =>
    parseReleaseConfig(
      validConfig({ adminDeletionRunbookReference: 'pending' }),
    ),
  );
});

test('requires proven production saga and reviewed legal/localized copy', () => {
  assert.throws(() =>
    parseReleaseConfig(
      validConfig({ productionFirebaseDeletionSagaTested: false }),
    ),
  );
  assert.throws(() =>
    parseReleaseConfig(validConfig({ legalApprovalReference: 'TODO' })),
  );
  assert.throws(() =>
    parseReleaseConfig(validConfig({ hindiCopyApprovalReference: '' })),
  );
});
