import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const node = process.execPath;

test('the store release hook requires the pinned authority over exact evidence bytes', () => {
  const source = readFileSync('tools/check-store-release.mjs', 'utf8');
  assert.match(source, /verifyDistributionEvidenceAuthority/u);
  assert.match(source, /distribution-authority-pin\.json/u);
  assert.match(source, /BIRTHDAY_STORE_EVIDENCE_SIGNATURE/u);
  assert.match(source, /BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY/u);
  assert.match(source, /store release evidence changed during validation/u);
  assert.match(source, /nlink !== 1n/u);
});

test('the hard release hook cannot run with only semantic evidence inputs', () => {
  const result = spawnSync(node, ['tools/check-store-release.mjs'], {
    cwd: process.cwd(),
    encoding: 'utf8',
    env: {
      PATH: process.env.PATH,
      BIRTHDAY_STORE_SUBMISSION_FILE: '/tmp/evidence.json',
      BIRTHDAY_STORE_ANDROID_AAB: '/tmp/app.aab',
      BIRTHDAY_STORE_IOS_IPA: '/tmp/app.ipa',
      BIRTHDAY_STORE_ASSET_ROOT: '/tmp/assets',
      BIRTHDAY_STORE_EVIDENCE_ROOT: '/tmp/evidence',
      BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH: '/tmp/hosting.json',
    },
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /BIRTHDAY_STORE_EVIDENCE_SIGNATURE/u);
  assert.match(result.stderr, /BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY/u);
});

test('the shared authority pin is either fail-closed or provisioned with a digest', () => {
  const pin = JSON.parse(
    readFileSync('tools/distribution-authority-pin.json', 'utf8'),
  );
  assert.deepEqual(Object.keys(pin).sort(), [
    'algorithm',
    'publicKeySpkiSha256',
    'schemaVersion',
  ]);
  assert.equal(pin.schemaVersion, 1);
  assert.equal(pin.algorithm, 'Ed25519');
  assert.match(pin.publicKeySpkiSha256, /^(?:UNPROVISIONED|[0-9a-f]{64})$/u);
});
