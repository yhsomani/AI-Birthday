import assert from 'node:assert/strict';
import test from 'node:test';

import { validateIOSReleaseHistory } from './validate-ios-release-history.mjs';

const now = Date.parse('2026-07-12T00:00:00Z');
const expected = {
  bundleIdentifier: 'com.yashsomani.birthdayautopilot',
  version: '1.0',
  build: '1',
  protectedStoreSchemaVersion: 2,
  sourceRevision: 'a'.repeat(40),
};

const evidence = () => ({
  schemaVersion: 1,
  product: 'birthday-autopilot-ios',
  bundleIdentifier: expected.bundleIdentifier,
  candidateVersion: expected.version,
  candidateBuild: expected.build,
  protectedStoreSchemaVersion: 2,
  sourceRevision: expected.sourceRevision,
  allPreviouslyDistributedBuildsIncluded: true,
  schema1EverDistributed: false,
  minimumPreviouslyDistributedSchemaVersion: null,
  releaseHistory: [],
  approvalReference: 'release/change-123',
  approvalSha256: 'b'.repeat(64),
  approvedAt: '2026-07-11T00:00:00Z',
  validUntil: '2026-08-11T00:00:00Z',
});

test('accepts a signed-authority payload attesting schema 2 was the first distribution', () => {
  assert.deepEqual(
    validateIOSReleaseHistory(evidence(), expected, now).errors,
    [],
  );
});

test('fails closed when schema 1 was distributed or history is incomplete', () => {
  const value = evidence();
  value.allPreviouslyDistributedBuildsIncluded = false;
  value.schema1EverDistributed = true;
  value.minimumPreviouslyDistributedSchemaVersion = 1;
  value.releaseHistory = [
    {
      version: '0.9',
      build: '9',
      protectedStoreSchemaVersion: 1,
      artifactSha256: 'c'.repeat(64),
      distributedAt: '2026-06-01T00:00:00Z',
    },
  ];

  const errors = validateIOSReleaseHistory(value, expected, now).errors.join(
    '\n',
  );
  assert.match(errors, /attested as included/u);
  assert.match(errors, /attested false/u);
  assert.match(errors, /legacy migration is required/u);
  assert.match(errors, /older than 2/u);
});

test('rejects omitted, extra, mismatched, stale, and internally inconsistent evidence', () => {
  const value = evidence();
  value.extra = true;
  value.bundleIdentifier = 'com.example.wrong';
  value.sourceRevision = 'c'.repeat(40);
  value.minimumPreviouslyDistributedSchemaVersion = 2;
  value.validUntil = '2026-07-11T00:00:00Z';

  const errors = validateIOSReleaseHistory(value, expected, now).errors.join(
    '\n',
  );
  assert.match(errors, /exact schema/u);
  assert.match(errors, /bundleIdentifier/u);
  assert.match(errors, /sourceRevision/u);
  assert.match(errors, /must be null/u);
  assert.match(errors, /expired/u);
});
