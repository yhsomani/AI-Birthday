import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  createHash,
  generateKeyPairSync,
  sign as createSignature,
} from 'node:crypto';
import {
  copyFileSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';

import {
  hasUnsafeAuthorityPinIndexState,
  sha256File,
  validateDistributionEvidence,
  verifyDistributionEvidenceAuthority,
} from './validate-distribution-evidence.mjs';

const NOW = Date.parse('2026-07-11T12:00:00Z');
const INSTALLED_CERTIFICATE = 'ab'.repeat(32);
const UPLOAD_CERTIFICATE = 'cd'.repeat(32);
const SOURCE_REVISION = 'c0ffee12'.repeat(5);
const APK_SHA256 = 'fe'.repeat(32);
const AAB_SHA256 = 'ef'.repeat(32);
const DIGESTS = {
  policyApprovalSha256: '11'.repeat(32),
  certifiedDeviceMatrixSha256: '22'.repeat(32),
  carrierMatrixSha256: '33'.repeat(32),
  legalReviewSha256: '44'.repeat(32),
  performanceEvidenceSha256: '55'.repeat(32),
  accessibilityEvidenceSha256: '66'.repeat(32),
  supplyChainEvidenceSha256: '77'.repeat(32),
};
const expected = {
  tier: 'prod',
  applicationId: 'com.yashsomani.birthdayautopilot',
  versionCode: 1,
  versionName: '1.0',
  artifactMode: 'prepackage',
  artifactSigningCertificateSha256: UPLOAD_CERTIFICATE,
  sourceRevision: SOURCE_REVISION,
  minimumSupportedApi: 29,
  targetApi: 36,
};

const approval = (tier = 'prod') => ({
  tier,
  status: 'approved',
  applicationId:
    tier === 'lab'
      ? 'com.yashsomani.birthdayautopilot.lab'
      : 'com.yashsomani.birthdayautopilot',
  versionCode: 1,
  versionName: tier === 'lab' ? '1.0-lab' : '1.0',
  sourceRevision: SOURCE_REVISION,
  channel: 'google-play',
  installerPackage: 'com.android.vending',
  signingCertificateSha256: INSTALLED_CERTIFICATE,
  uploadSigningCertificateSha256: UPLOAD_CERTIFICATE,
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
  policyApprovalReference: 'policy/BA-2026-001',
  certifiedDeviceMatrixReference: 'device-matrix/BA-2026-001',
  carrierMatrixReference: 'carrier-matrix/BA-2026-001',
  performanceEvidenceReference: 'performance/BA-2026-001',
  accessibilityEvidenceReference: 'accessibility/BA-2026-001',
  supplyChainEvidenceReference: 'supply-chain/BA-2026-001',
  legalReviewReference: 'legal/BA-2026-001',
  ...DIGESTS,
  approvedAt: '2026-07-01T00:00:00Z',
  validUntil: '2026-08-01T00:00:00Z',
  launchCountries: ['IN'],
});

const validDocument = () => ({
  schemaVersion: 4,
  approvals: [approval()],
});

const directDocument = () => {
  const document = validDocument();
  const selected = document.approvals[0];
  selected.channel = 'controlled-direct';
  selected.installerPackage = 'com.example.approvedinstaller';
  delete selected.playUploadApproved;
  delete selected.uploadSigningCertificateSha256;
  return document;
};

const authorityFixture = rawEvidence => {
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const publicKeyBytes = publicKey.export({ format: 'pem', type: 'spki' });
  const spki = publicKey.export({ format: 'der', type: 'spki' });
  const publicKeySpkiSha256 = createHash('sha256').update(spki).digest('hex');
  return {
    detachedSignature: createSignature(null, rawEvidence, privateKey),
    pinDocument: {
      schemaVersion: 1,
      algorithm: 'Ed25519',
      publicKeySpkiSha256,
    },
    publicKeyBytes,
    publicKeySpkiSha256,
  };
};

test('accepts authority-signed exact raw evidence bytes with the pinned Ed25519 key', () => {
  const rawEvidence = Buffer.from(JSON.stringify(validDocument()));
  const fixture = authorityFixture(rawEvidence);
  const result = verifyDistributionEvidenceAuthority({
    rawEvidence,
    ...fixture,
  });
  assert.deepEqual(result.errors, []);
  assert.equal(result.publicKeySpkiSha256, fixture.publicKeySpkiSha256);
});

test('rejects a one-byte evidence change after detached signing', () => {
  const rawEvidence = Buffer.from(JSON.stringify(validDocument()));
  const fixture = authorityFixture(rawEvidence);
  const changedEvidence = Buffer.from(rawEvidence);
  changedEvidence[changedEvidence.length - 1] = 93;
  const result = verifyDistributionEvidenceAuthority({
    rawEvidence: changedEvidence,
    ...fixture,
  });
  assert.deepEqual(result.errors, [
    'detached distribution evidence signature is invalid',
  ]);
});

test('rejects an unprovisioned pin, a mismatched key, and non-raw signatures', () => {
  const rawEvidence = Buffer.from(JSON.stringify(validDocument()));
  const fixture = authorityFixture(rawEvidence);
  const unprovisioned = verifyDistributionEvidenceAuthority({
    rawEvidence,
    ...fixture,
    pinDocument: {
      schemaVersion: 1,
      algorithm: 'Ed25519',
      publicKeySpkiSha256: 'UNPROVISIONED',
    },
  });
  assert.ok(
    unprovisioned.errors.includes(
      'distribution authority public-key digest pin is unprovisioned',
    ),
  );

  const wrongPin = verifyDistributionEvidenceAuthority({
    rawEvidence,
    ...fixture,
    pinDocument: {
      ...fixture.pinDocument,
      publicKeySpkiSha256: '99'.repeat(32),
    },
  });
  assert.ok(
    wrongPin.errors.includes(
      'distribution authority public key does not match the tracked pin',
    ),
  );

  const textSignature = verifyDistributionEvidenceAuthority({
    rawEvidence,
    ...fixture,
    detachedSignature: Buffer.from(
      fixture.detachedSignature.toString('base64'),
    ),
  });
  assert.ok(
    textSignature.errors.includes(
      'detached Ed25519 signature must be exactly 64 raw bytes',
    ),
  );
});

test('rejects PKCS8 and private KeyObject inputs even when their public digest is pinned', () => {
  const rawEvidence = Buffer.from(JSON.stringify(validDocument()));
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const publicKeySpkiSha256 = createHash('sha256')
    .update(publicKey.export({ format: 'der', type: 'spki' }))
    .digest('hex');
  const common = {
    rawEvidence,
    detachedSignature: createSignature(null, rawEvidence, privateKey),
    pinDocument: {
      schemaVersion: 1,
      algorithm: 'Ed25519',
      publicKeySpkiSha256,
    },
  };

  for (const privateKeyInput of [
    privateKey,
    privateKey.export({ format: 'pem', type: 'pkcs8' }),
  ]) {
    const result = verifyDistributionEvidenceAuthority({
      ...common,
      publicKeyBytes: privateKeyInput,
    });
    assert.ok(
      result.errors.includes(
        'distribution authority public-key input must not contain private-key material',
      ),
    );
  }
});

test('rejects assume-unchanged, skip-worktree, and fsmonitor-valid pin index states', () => {
  const pinState = 'H tools/distribution-authority-pin.json';
  assert.equal(hasUnsafeAuthorityPinIndexState(pinState, pinState), false);
  assert.equal(
    hasUnsafeAuthorityPinIndexState(
      'h tools/distribution-authority-pin.json',
      pinState,
    ),
    true,
  );
  assert.equal(
    hasUnsafeAuthorityPinIndexState(
      'S tools/distribution-authority-pin.json',
      pinState,
    ),
    true,
  );
  assert.equal(
    hasUnsafeAuthorityPinIndexState(
      pinState,
      'h tools/distribution-authority-pin.json',
    ),
    true,
  );
});

test('accepts prepackage evidence bound to source, version, certificate, and reference digests', () => {
  const result = validateDistributionEvidence(validDocument(), expected, NOW);
  assert.deepEqual(result.errors, []);
  assert.equal(result.approval?.versionName, '1.0');
});

test('direct post-build validation binds the installed signer and exact APK SHA-256', () => {
  const directExpected = {
    ...expected,
    artifactMode: 'direct-apk',
    artifactSigningCertificateSha256: INSTALLED_CERTIFICATE,
    artifactSha256: APK_SHA256,
  };
  const missing = validateDistributionEvidence(
    directDocument(),
    directExpected,
    NOW,
  );
  assert.ok(
    missing.errors.includes(
      'artifactApkSha256 does not match the exact APK bytes',
    ),
  );

  const wrongDocument = directDocument();
  wrongDocument.approvals[0].artifactApkSha256 = 'aa'.repeat(32);
  assert.ok(
    validateDistributionEvidence(
      wrongDocument,
      directExpected,
      NOW,
    ).errors.includes('artifactApkSha256 does not match the exact APK bytes'),
  );

  const boundDocument = directDocument();
  boundDocument.approvals[0].artifactApkSha256 = APK_SHA256;
  assert.deepEqual(
    validateDistributionEvidence(boundDocument, directExpected, NOW).errors,
    [],
  );
});

test('Google Play separates upload-AAB and installed-APK certificate and digest proofs', () => {
  const prepackage = validateDistributionEvidence(
    validDocument(),
    expected,
    NOW,
  );
  assert.deepEqual(prepackage.errors, []);
  assert.equal(
    prepackage.approval?.signingCertificateSha256,
    INSTALLED_CERTIFICATE,
  );
  assert.equal(
    prepackage.approval?.uploadSigningCertificateSha256,
    UPLOAD_CERTIFICATE,
  );

  const aabDocument = validDocument();
  aabDocument.approvals[0].artifactAabSha256 = AAB_SHA256;
  assert.deepEqual(
    validateDistributionEvidence(
      aabDocument,
      {
        ...expected,
        artifactMode: 'play-aab',
        artifactSha256: AAB_SHA256,
      },
      NOW,
    ).errors,
    [],
  );

  const deliveredDocument = validDocument();
  deliveredDocument.approvals[0].artifactAabSha256 = AAB_SHA256;
  deliveredDocument.approvals[0].artifactApkSha256 = APK_SHA256;
  assert.deepEqual(
    validateDistributionEvidence(
      deliveredDocument,
      {
        ...expected,
        artifactMode: 'play-delivered-apk',
        artifactSigningCertificateSha256: INSTALLED_CERTIFICATE,
        artifactSha256: APK_SHA256,
      },
      NOW,
    ).errors,
    [],
  );
});

test('Google Play rejects an upload-key AAB or delivered APK signed by the wrong role', () => {
  const aabDocument = validDocument();
  aabDocument.approvals[0].artifactAabSha256 = AAB_SHA256;
  assert.ok(
    validateDistributionEvidence(
      aabDocument,
      {
        ...expected,
        artifactMode: 'play-aab',
        artifactSigningCertificateSha256: INSTALLED_CERTIFICATE,
        artifactSha256: AAB_SHA256,
      },
      NOW,
    ).errors.includes(
      'AAB signer does not match the approved Google Play upload certificate',
    ),
  );

  const deliveredDocument = validDocument();
  deliveredDocument.approvals[0].artifactAabSha256 = AAB_SHA256;
  deliveredDocument.approvals[0].artifactApkSha256 = APK_SHA256;
  assert.ok(
    validateDistributionEvidence(
      deliveredDocument,
      {
        ...expected,
        artifactMode: 'play-delivered-apk',
        artifactSigningCertificateSha256: UPLOAD_CERTIFICATE,
        artifactSha256: APK_SHA256,
      },
      NOW,
    ).errors.includes(
      'Play-delivered APK signer does not match the approved installed APK certificate',
    ),
  );
});

test('non-Play approval forbids Google Play-only upload and AAB fields', () => {
  const document = directDocument();
  document.approvals[0].uploadSigningCertificateSha256 = UPLOAD_CERTIFICATE;
  document.approvals[0].artifactAabSha256 = AAB_SHA256;
  const errors = validateDistributionEvidence(
    document,
    {
      ...expected,
      artifactSigningCertificateSha256: INSTALLED_CERTIFICATE,
    },
    NOW,
  ).errors;
  assert.ok(
    errors.includes(
      'prod approval non-Play approval must not contain uploadSigningCertificateSha256',
    ),
  );
  assert.ok(
    errors.includes(
      'prod approval non-Play approval must not contain artifactAabSha256',
    ),
  );
});

test('post-build file hashing consumes exact binary bytes', () => {
  const directory = mkdtempSync(join(tmpdir(), 'birthday-apk-hash-'));
  try {
    const apk = join(directory, 'candidate.apk');
    const bytes = Buffer.from([0, 1, 2, 13, 10, 255, 99]);
    writeFileSync(apk, bytes);
    assert.equal(
      sha256File(apk),
      createHash('sha256').update(bytes).digest('hex'),
    );
    assert.throws(() => sha256File(apk, bytes.byteLength - 1));
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

test('binds the selected approval to exact versionCode and versionName', () => {
  const wrongCode = validDocument();
  wrongCode.approvals[0].versionCode = 2;
  assert.ok(
    validateDistributionEvidence(wrongCode, expected, NOW).errors.includes(
      'versionCode does not match the artifact',
    ),
  );

  const wrongName = validDocument();
  wrongName.approvals[0].versionName = '1.0.1';
  assert.ok(
    validateDistributionEvidence(wrongName, expected, NOW).errors.includes(
      'versionName does not match the artifact',
    ),
  );
});

test('validates every approval globally even when another tier is selected', () => {
  const document = validDocument();
  const invalidLab = approval('lab');
  invalidLab.status = 'pending';
  invalidLab.versionCode = 9;
  invalidLab.versionName = '9.0-lab';
  invalidLab.sourceRevision = 'd'.repeat(40);
  invalidLab.carrierMatrixSha256 = 'AA'.repeat(32);
  invalidLab.telephonyStatePermissionApproved = false;
  document.approvals.unshift(invalidLab);

  const result = validateDistributionEvidence(document, expected, NOW);
  assert.ok(result.errors.includes('lab approval status is not approved'));
  assert.ok(
    result.errors.includes(
      'lab approval versionCode does not match the release coordinate',
    ),
  );
  assert.ok(
    result.errors.includes(
      'lab approval versionName does not match the release coordinate',
    ),
  );
  assert.ok(
    result.errors.includes(
      'lab approval sourceRevision does not match the clean Git revision',
    ),
  );
  assert.ok(
    result.errors.includes(
      'lab approval carrierMatrixSha256 must be a lowercase SHA-256 digest',
    ),
  );
  assert.ok(
    result.errors.includes(
      'lab approval telephonyStatePermissionApproved is not verified',
    ),
  );
});

test('binds only the selected tier build signer while validating every tier structurally', () => {
  const document = validDocument();
  document.approvals.unshift(approval('lab'));
  document.approvals[0].signingCertificateSha256 = 'de'.repeat(32);
  document.approvals[0].uploadSigningCertificateSha256 = 'ad'.repeat(32);
  assert.deepEqual(
    validateDistributionEvidence(document, expected, NOW).errors,
    [],
  );

  const selected = directDocument();
  selected.approvals[0].signingCertificateSha256 = 'de'.repeat(32);
  assert.ok(
    validateDistributionEvidence(
      selected,
      {
        ...expected,
        artifactSigningCertificateSha256: INSTALLED_CERTIFICATE,
      },
      NOW,
    ).errors.includes(
      'build keystore does not match the approved installed APK certificate',
    ),
  );
});

test('requires every policy, device, carrier, performance, accessibility, supply-chain, and legal digest', () => {
  for (const field of Object.keys(DIGESTS)) {
    const document = validDocument();
    delete document.approvals[0][field];
    const result = validateDistributionEvidence(document, expected, NOW);
    assert.ok(result.errors.includes(`prod approval is missing ${field}`));
    assert.ok(
      result.errors.includes(
        `prod approval ${field} must be a lowercase SHA-256 digest`,
      ),
    );
  }
});

test('rejects expired, inverted, and excessively long evidence', () => {
  const expired = validDocument();
  expired.approvals[0].validUntil = '2026-07-01T00:00:00Z';
  assert.ok(
    validateDistributionEvidence(expired, expected, NOW).errors.includes(
      'prod approval approval is expired',
    ),
  );

  const inverted = validDocument();
  inverted.approvals[0].approvedAt = '2026-07-10T00:00:00Z';
  inverted.approvals[0].validUntil = '2026-07-09T00:00:00Z';
  assert.ok(
    validateDistributionEvidence(inverted, expected, NOW).errors.includes(
      'prod approval approval validity is inverted',
    ),
  );

  const excessive = validDocument();
  excessive.approvals[0].validUntil = '2028-01-01T00:00:00Z';
  assert.ok(
    validateDistributionEvidence(excessive, expected, NOW).errors.includes(
      'prod approval approval validity exceeds one year',
    ),
  );
});

test('rejects normalized but nonexistent UTC calendar dates', () => {
  const evidence = validDocument();
  evidence.approvals[0].approvedAt = '2026-02-31T00:00:00Z';
  const result = validateDistributionEvidence(evidence, expected, NOW);
  assert.match(result.errors.join('\n'), /approvedAt must be an RFC 3339/u);
});

test('rejects unknown, missing, duplicate-tier, and malformed approval entries', () => {
  const unknown = validDocument();
  unknown.approvals[0].selfApproved = true;
  assert.ok(
    validateDistributionEvidence(unknown, expected, NOW).errors.includes(
      'prod approval has unsupported field selfApproved',
    ),
  );

  const missing = validDocument();
  delete missing.approvals[0].legalReviewReference;
  assert.ok(
    validateDistributionEvidence(missing, expected, NOW).errors.includes(
      'prod approval is missing legalReviewReference',
    ),
  );

  const duplicate = validDocument();
  duplicate.approvals.push(approval());
  assert.ok(
    validateDistributionEvidence(duplicate, expected, NOW).errors.includes(
      'approval tiers must be unique',
    ),
  );

  const malformed = validDocument();
  malformed.approvals[0] = null;
  assert.ok(
    validateDistributionEvidence(malformed, expected, NOW).errors.includes(
      'approval 1 must be an object',
    ),
  );
});

test('schema v4 declares split Play signers, AAB/APK digests, and every release evidence binding', () => {
  const schema = JSON.parse(
    readFileSync('tools/distribution-evidence.schema.json', 'utf8'),
  );
  assert.equal(schema.properties.schemaVersion.const, 4);
  const required = schema.properties.approvals.items.required;
  for (const field of [
    'versionName',
    'sourceRevision',
    ...Object.keys(DIGESTS),
  ]) {
    assert.ok(required.includes(field), `${field} must be required`);
  }
  assert.equal(
    schema.properties.approvals.items.properties.artifactApkSha256.$ref,
    '#/$defs/sha256',
  );
  assert.equal(
    schema.properties.approvals.items.properties.artifactAabSha256.$ref,
    '#/$defs/sha256',
  );
  assert.equal(
    schema.properties.approvals.items.properties.uploadSigningCertificateSha256
      .type,
    'string',
  );
  const playRule = schema.properties.approvals.items.allOf.at(-1);
  assert.ok(playRule.then.required.includes('uploadSigningCertificateSha256'));
});

test('CLI uses only the fixed repository pin and fails closed while it is unprovisioned', () => {
  const directory = mkdtempSync(join(tmpdir(), 'birthday-authority-cli-'));
  try {
    const rawEvidence = Buffer.from(JSON.stringify(validDocument()));
    const fixture = authorityFixture(rawEvidence);
    const evidenceFile = join(directory, 'evidence.json');
    const signatureFile = join(directory, 'evidence.sig');
    const publicKeyFile = join(directory, 'authority.pem');
    writeFileSync(evidenceFile, rawEvidence);
    writeFileSync(signatureFile, fixture.detachedSignature);
    writeFileSync(publicKeyFile, fixture.publicKeyBytes);
    const result = spawnSync(
      process.execPath,
      [
        resolve('tools/validate-distribution-evidence.mjs'),
        '--file',
        evidenceFile,
        '--signature',
        signatureFile,
        '--public-key',
        publicKeyFile,
        '--tier',
        'prod',
        '--package',
        expected.applicationId,
        '--version-code',
        String(expected.versionCode),
        '--version-name',
        expected.versionName,
        '--artifact-mode',
        'prepackage',
        '--artifact-signing-certificate',
        UPLOAD_CERTIFICATE,
      ],
      { encoding: 'utf8' },
    );
    assert.equal(result.status, 1);
    assert.match(result.stderr, /digest pin is unprovisioned/u);
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

test('CLI accepts a provisioned tracked pin only from its exact clean Git revision', () => {
  const directory = mkdtempSync(join(tmpdir(), 'birthday-authority-repo-'));
  try {
    const repository = join(directory, 'repository');
    const toolsDirectory = join(repository, 'tools');
    const externalEvidence = join(directory, 'release-evidence');
    mkdirSync(toolsDirectory, { recursive: true });
    mkdirSync(externalEvidence);
    copyFileSync(
      resolve('tools/validate-distribution-evidence.mjs'),
      join(toolsDirectory, 'validate-distribution-evidence.mjs'),
    );

    const { privateKey, publicKey } = generateKeyPairSync('ed25519');
    const publicKeyBytes = publicKey.export({ format: 'pem', type: 'spki' });
    const publicKeySpkiSha256 = createHash('sha256')
      .update(publicKey.export({ format: 'der', type: 'spki' }))
      .digest('hex');
    const pinFile = join(toolsDirectory, 'distribution-authority-pin.json');
    const pinBytes = Buffer.from(
      `${JSON.stringify({
        schemaVersion: 1,
        algorithm: 'Ed25519',
        publicKeySpkiSha256,
      })}\n`,
    );
    writeFileSync(pinFile, pinBytes);

    const git = arguments_ =>
      spawnSync('git', ['-C', repository, ...arguments_], {
        encoding: 'utf8',
      });
    assert.equal(git(['init', '--quiet']).status, 0);
    assert.equal(git(['config', 'user.name', 'Authority Gate Test']).status, 0);
    assert.equal(
      git(['config', 'user.email', 'authority-gate@example.invalid']).status,
      0,
    );
    assert.equal(git(['add', 'tools']).status, 0);
    assert.equal(git(['commit', '--quiet', '-m', 'pin authority']).status, 0);
    const sourceRevision = git(['rev-parse', 'HEAD']).stdout.trim();

    const document = validDocument();
    document.approvals[0].sourceRevision = sourceRevision;
    const validationTime = Date.now();
    document.approvals[0].approvedAt = new Date(
      validationTime - 24 * 60 * 60 * 1000,
    ).toISOString();
    document.approvals[0].validUntil = new Date(
      validationTime + 30 * 24 * 60 * 60 * 1000,
    ).toISOString();
    const rawEvidence = Buffer.from(JSON.stringify(document));
    const signature = createSignature(null, rawEvidence, privateKey);
    const evidenceFile = join(externalEvidence, 'evidence.json');
    const signatureFile = join(externalEvidence, 'evidence.sig');
    const publicKeyFile = join(externalEvidence, 'authority.pem');
    writeFileSync(evidenceFile, rawEvidence);
    writeFileSync(signatureFile, signature);
    writeFileSync(publicKeyFile, publicKeyBytes);

    const command = [
      join(toolsDirectory, 'validate-distribution-evidence.mjs'),
      '--file',
      evidenceFile,
      '--signature',
      signatureFile,
      '--public-key',
      publicKeyFile,
      '--tier',
      'prod',
      '--package',
      expected.applicationId,
      '--version-code',
      String(expected.versionCode),
      '--version-name',
      expected.versionName,
      '--artifact-mode',
      'prepackage',
      '--artifact-signing-certificate',
      UPLOAD_CERTIFICATE,
      '--output',
      'json',
    ];
    const runValidator = (environment = process.env) =>
      spawnSync(process.execPath, command, {
        encoding: 'utf8',
        env: environment,
      });
    const clean = runValidator({
      ...process.env,
      GIT_CONFIG_COUNT: '1',
      GIT_CONFIG_KEY_0: 'core.fsmonitor',
      GIT_CONFIG_VALUE_0: 'true',
      GIT_DIR: join(directory, 'attacker-git-dir'),
      GIT_INDEX_FILE: join(directory, 'attacker-index'),
      GIT_WORK_TREE: join(directory, 'attacker-work-tree'),
    });
    assert.equal(clean.status, 0, clean.stderr);
    assert.equal(JSON.parse(clean.stdout).sourceRevision, sourceRevision);

    const pinPath = 'tools/distribution-authority-pin.json';
    assert.equal(
      git(['update-index', '--assume-unchanged', pinPath]).status,
      0,
    );
    writeFileSync(pinFile, Buffer.concat([pinBytes, Buffer.from('\n')]));
    const hiddenPinChange = runValidator();
    assert.equal(hiddenPinChange.status, 1);
    assert.match(hiddenPinChange.stderr, /index flags/u);
    assert.match(hiddenPinChange.stderr, /bytes do not exactly match HEAD/u);
    assert.equal(
      git(['update-index', '--no-assume-unchanged', pinPath]).status,
      0,
    );
    writeFileSync(pinFile, pinBytes);

    assert.equal(git(['update-index', '--skip-worktree', pinPath]).status, 0);
    const skipWorktree = runValidator();
    assert.equal(skipWorktree.status, 1);
    assert.match(skipWorktree.stderr, /index flags/u);
    assert.equal(
      git(['update-index', '--no-skip-worktree', pinPath]).status,
      0,
    );

    writeFileSync(join(repository, 'untracked-source.txt'), 'dirty\n');
    const dirty = runValidator();
    assert.equal(dirty.status, 1);
    assert.match(dirty.stderr, /Git source tree is not clean/u);
  } finally {
    rmSync(directory, { force: true, recursive: true });
  }
});

test('Gradle prepackage and standalone post-build checks share the same fail-closed validator', () => {
  const gradle = readFileSync('android/app/build.gradle', 'utf8');
  const verifier = readFileSync('tools/verify-android-apk.sh', 'utf8');
  const validator = readFileSync(
    'tools/validate-distribution-evidence.mjs',
    'utf8',
  );
  const pin = JSON.parse(
    readFileSync('tools/distribution-authority-pin.json', 'utf8'),
  );

  assert.match(gradle, /providers\.exec/u);
  assert.match(gradle, /validate-distribution-evidence\.mjs/u);
  assert.match(gradle, /BIRTHDAY_DISTRIBUTION_EVIDENCE_SIGNATURE_FILE/u);
  assert.match(gradle, /BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY_FILE/u);
  assert.match(gradle, /def releaseVersionCode = 1/u);
  assert.match(gradle, /def releaseBaseVersionName = "1\.0"/u);
  assert.doesNotMatch(gradle, /evidenceApprovalKeys|safeEvidenceReference/u);
  assert.match(verifier, /validate-distribution-evidence\.mjs/u);
  assert.match(verifier, /--artifact-mode "\$artifact_mode"/u);
  assert.match(verifier, /--artifact-file "\$apk"/u);
  assert.match(gradle, /--artifact-mode[\s\S]*"prepackage"/u);
  assert.match(gradle, /approval\.signingCertificateSha256/u);
  assert.doesNotMatch(
    gradle,
    /APPROVED_SIGNING_CERTIFICATE_SHA256[\s\S]{0,160}actualSigningCertificateSha256/u,
  );
  assert.match(
    gradle,
    /Google Play approval authorizes only an upload-key-signed AAB/u,
  );
  assert.match(
    validator,
    /uploadSigningCertificateSha256[\s\S]*approved Google Play upload certificate/u,
  );
  assert.match(validator, /Git source tree is not clean/u);
  assert.match(validator, /ls-files/u);
  assert.match(validator, /GIT_CONFIG_NOSYSTEM/u);
  assert.match(validator, /bytes do not exactly match HEAD/u);
  assert.match(validator, /fsmonitor-valid index flags/u);
  assert.match(validator, /must not contain private-key material/u);
  assert.match(validator, /versionName: '1\.0-lab'/u);
  assert.match(validator, /versionName: '1\.0'/u);
  assert.deepEqual(pin, {
    schemaVersion: 1,
    algorithm: 'Ed25519',
    publicKeySpkiSha256: 'UNPROVISIONED',
  });
});
