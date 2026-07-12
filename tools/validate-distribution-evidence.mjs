#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import {
  createHash,
  createPrivateKey,
  createPublicKey,
  verify as verifySignature,
} from 'node:crypto';
import {
  closeSync,
  fstatSync,
  lstatSync,
  openSync,
  readFileSync,
  readSync,
  realpathSync,
} from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const CHANNELS = new Set([
  'google-play',
  'managed-enterprise',
  'controlled-direct',
]);
const ARTIFACT_MODES = new Set([
  'prepackage',
  'direct-apk',
  'play-aab',
  'play-delivered-apk',
]);
const RELEASE_COORDINATES_BY_TIER = new Map([
  [
    'lab',
    {
      applicationId: 'com.yashsomani.birthdayautopilot.lab',
      versionCode: 1,
      versionName: '1.0-lab',
    },
  ],
  [
    'prod',
    {
      applicationId: 'com.yashsomani.birthdayautopilot',
      versionCode: 1,
      versionName: '1.0',
    },
  ],
]);
const TOP_LEVEL_KEYS = new Set(['schemaVersion', 'approvals']);
const REQUIRED_APPROVAL_KEYS = [
  'tier',
  'status',
  'applicationId',
  'versionCode',
  'versionName',
  'sourceRevision',
  'channel',
  'installerPackage',
  'signingCertificateSha256',
  'minimumCertifiedApi',
  'maximumCertifiedApi',
  'smsPermissionPolicyApproved',
  'installerAllowlistVerified',
  'physicalSmsMatrixPassed',
  'performanceBudgetsPassed',
  'accessibilityMatrixPassed',
  'nativeSupplyChainReviewed',
  'telephonyStatePermissionApproved',
  'appCheckEnforced',
  'privacyMaterialsApproved',
  'policyApprovalReference',
  'policyApprovalSha256',
  'certifiedDeviceMatrixReference',
  'certifiedDeviceMatrixSha256',
  'carrierMatrixReference',
  'carrierMatrixSha256',
  'performanceEvidenceReference',
  'performanceEvidenceSha256',
  'accessibilityEvidenceReference',
  'accessibilityEvidenceSha256',
  'supplyChainEvidenceReference',
  'supplyChainEvidenceSha256',
  'legalReviewReference',
  'legalReviewSha256',
  'approvedAt',
  'validUntil',
  'launchCountries',
];
const APPROVAL_KEYS = new Set([
  ...REQUIRED_APPROVAL_KEYS,
  'playUploadApproved',
  'uploadSigningCertificateSha256',
  'artifactApkSha256',
  'artifactAabSha256',
]);
const REQUIRED_TRUE_FIELDS = [
  'smsPermissionPolicyApproved',
  'installerAllowlistVerified',
  'physicalSmsMatrixPassed',
  'performanceBudgetsPassed',
  'accessibilityMatrixPassed',
  'nativeSupplyChainReviewed',
  'telephonyStatePermissionApproved',
  'appCheckEnforced',
  'privacyMaterialsApproved',
];
const REFERENCED_EVIDENCE = [
  ['policyApprovalReference', 'policyApprovalSha256'],
  ['certifiedDeviceMatrixReference', 'certifiedDeviceMatrixSha256'],
  ['carrierMatrixReference', 'carrierMatrixSha256'],
  ['performanceEvidenceReference', 'performanceEvidenceSha256'],
  ['accessibilityEvidenceReference', 'accessibilityEvidenceSha256'],
  ['supplyChainEvidenceReference', 'supplyChainEvidenceSha256'],
  ['legalReviewReference', 'legalReviewSha256'],
];
const MAXIMUM_EVIDENCE_BYTES = 64 * 1024;
const MAXIMUM_PUBLIC_KEY_BYTES = 8 * 1024;
const MAXIMUM_PIN_BYTES = 1024;
const MAXIMUM_ARTIFACT_BYTES = 1024 * 1024 * 1024;
const ED25519_SIGNATURE_BYTES = 64;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const SAFE_REFERENCE = /^[A-Za-z0-9][A-Za-z0-9 ._:/#-]{0,255}$/u;
const SAFE_VERSION_NAME = /^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const GIT_REVISION = /^[0-9a-f]{40}$/u;
const PIN_KEYS = new Set(['schemaVersion', 'algorithm', 'publicKeySpkiSha256']);
const SCRIPT_FILE = fileURLToPath(import.meta.url);
const SCRIPT_DIRECTORY = dirname(SCRIPT_FILE);
const PROJECT_ROOT = resolve(SCRIPT_DIRECTORY, '..');
const AUTHORITY_PIN_RELATIVE_PATH = 'tools/distribution-authority-pin.json';
const AUTHORITY_PIN_FILE = resolve(PROJECT_ROOT, AUTHORITY_PIN_RELATIVE_PATH);
const NULL_DEVICE = process.platform === 'win32' ? 'NUL' : '/dev/null';
const SANITIZED_GIT_ENVIRONMENT = Object.fromEntries(
  Object.entries(process.env).filter(([name]) => !name.startsWith('GIT_')),
);
Object.assign(SANITIZED_GIT_ENVIRONMENT, {
  GIT_CONFIG_COUNT: '0',
  GIT_CONFIG_GLOBAL: NULL_DEVICE,
  GIT_CONFIG_NOSYSTEM: '1',
  GIT_LITERAL_PATHSPECS: '1',
  GIT_OPTIONAL_LOCKS: '0',
});
const CLI_ARGUMENTS = new Set([
  'file',
  'signature',
  'public-key',
  'tier',
  'package',
  'version-code',
  'version-name',
  'artifact-mode',
  'artifact-signing-certificate',
  'artifact-file',
  'output',
]);

const fail = message => {
  process.stderr.write(`FAIL ${message}\n`);
  process.exitCode = 1;
};

const parseArguments = argv => {
  if (argv.length % 2 !== 0) {
    throw new Error('arguments must be --name value pairs');
  }
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!flag?.startsWith('--') || value === undefined) {
      throw new Error('arguments must be --name value pairs');
    }
    const name = flag.slice(2);
    if (!CLI_ARGUMENTS.has(name))
      throw new Error(`unsupported argument ${flag}`);
    if (values.has(name)) throw new Error(`duplicate argument ${flag}`);
    values.set(name, value);
  }
  return values;
};

const normalizeCertificate = value =>
  typeof value === 'string'
    ? value.replaceAll(':', '').trim().toLowerCase()
    : '';

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const assertExactKeys = (value, allowed, label, errors) => {
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) errors.push(`${label} has unsupported field ${key}`);
  }
};

const assertRequiredKeys = (value, required, label, errors) => {
  for (const key of required) {
    if (!Object.hasOwn(value, key)) errors.push(`${label} is missing ${key}`);
  }
};

const isSafeReference = value =>
  typeof value === 'string' &&
  value === value.trim() &&
  SAFE_REFERENCE.test(value);

const hasExactUtcCalendarFields = (value, epochMillis) => {
  const instant = new Date(epochMillis);
  return (
    instant.getUTCFullYear() === Number(value.slice(0, 4)) &&
    instant.getUTCMonth() + 1 === Number(value.slice(5, 7)) &&
    instant.getUTCDate() === Number(value.slice(8, 10)) &&
    instant.getUTCHours() === Number(value.slice(11, 13)) &&
    instant.getUTCMinutes() === Number(value.slice(14, 16)) &&
    instant.getUTCSeconds() === Number(value.slice(17, 19))
  );
};

const parseInstant = (value, label, errors) => {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  const epochMillis = Date.parse(value);
  if (
    !Number.isFinite(epochMillis) ||
    !hasExactUtcCalendarFields(value, epochMillis)
  ) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  return epochMillis;
};

const sha256 = value => createHash('sha256').update(value).digest('hex');

export function sha256File(path, maximumBytes = MAXIMUM_ARTIFACT_BYTES) {
  let descriptor;
  try {
    descriptor = openSync(path, 'r');
    const before = fstatSync(descriptor, { bigint: true });
    if (
      !before.isFile() ||
      before.size <= 0n ||
      before.size > BigInt(maximumBytes)
    ) {
      throw new Error('file has an invalid size or type');
    }
    const digest = createHash('sha256');
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    let bytesRead;
    do {
      bytesRead = readSync(descriptor, buffer, 0, buffer.byteLength, null);
      if (bytesRead > 0) digest.update(buffer.subarray(0, bytesRead));
    } while (bytesRead > 0);

    const after = fstatSync(descriptor, { bigint: true });
    if (
      after.dev !== before.dev ||
      after.ino !== before.ino ||
      after.size !== before.size ||
      after.mtimeNs !== before.mtimeNs ||
      after.ctimeNs !== before.ctimeNs
    ) {
      throw new Error('file changed while it was being hashed');
    }
    return digest.digest('hex');
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
}

export function verifyDistributionEvidenceAuthority({
  rawEvidence,
  detachedSignature,
  publicKeyBytes,
  pinDocument,
}) {
  const errors = [];
  if (!Buffer.isBuffer(rawEvidence) || rawEvidence.byteLength === 0) {
    errors.push('raw evidence bytes are missing');
  }
  if (
    !Buffer.isBuffer(detachedSignature) ||
    detachedSignature.byteLength !== ED25519_SIGNATURE_BYTES
  ) {
    errors.push('detached Ed25519 signature must be exactly 64 raw bytes');
  }
  if (!isObject(pinDocument)) {
    errors.push('distribution authority pin is malformed');
    return { errors };
  }
  assertExactKeys(pinDocument, PIN_KEYS, 'distribution authority pin', errors);
  assertRequiredKeys(
    pinDocument,
    [...PIN_KEYS],
    'distribution authority pin',
    errors,
  );
  if (pinDocument.schemaVersion !== 1) {
    errors.push('distribution authority pin schemaVersion must be 1');
  }
  if (pinDocument.algorithm !== 'Ed25519') {
    errors.push('distribution authority algorithm must be Ed25519');
  }
  if (pinDocument.publicKeySpkiSha256 === 'UNPROVISIONED') {
    errors.push(
      'distribution authority public-key digest pin is unprovisioned',
    );
  } else if (!SHA256.test(pinDocument.publicKeySpkiSha256)) {
    errors.push('distribution authority public-key digest pin is invalid');
  }

  let publicKey;
  let publicKeyDigest;
  let privateKeyInput =
    publicKeyBytes?.type === 'private' &&
    typeof publicKeyBytes?.asymmetricKeyType === 'string';
  if (!privateKeyInput) {
    try {
      createPrivateKey(publicKeyBytes);
      privateKeyInput = true;
    } catch {
      privateKeyInput = false;
    }
  }
  if (privateKeyInput) {
    errors.push(
      'distribution authority public-key input must not contain private-key material',
    );
  }
  if (!privateKeyInput) {
    try {
      publicKey = createPublicKey(publicKeyBytes);
      if (publicKey.asymmetricKeyType !== 'ed25519') {
        throw new Error('wrong public-key algorithm');
      }
      const spki = publicKey.export({ format: 'der', type: 'spki' });
      publicKeyDigest = sha256(spki);
    } catch {
      errors.push(
        'distribution authority public key is not a valid Ed25519 key',
      );
    }
  }
  if (
    publicKeyDigest !== undefined &&
    SHA256.test(pinDocument.publicKeySpkiSha256) &&
    publicKeyDigest !== pinDocument.publicKeySpkiSha256
  ) {
    errors.push(
      'distribution authority public key does not match the tracked pin',
    );
  }

  if (errors.length > 0) return { errors };
  if (!verifySignature(null, rawEvidence, publicKey, detachedSignature)) {
    return { errors: ['detached distribution evidence signature is invalid'] };
  }
  return { errors: [], publicKeySpkiSha256: publicKeyDigest };
}

const validateApproval = (approval, expected, now, label) => {
  const errors = [];
  assertExactKeys(approval, APPROVAL_KEYS, label, errors);
  assertRequiredKeys(approval, REQUIRED_APPROVAL_KEYS, label, errors);

  const expectedCoordinates = RELEASE_COORDINATES_BY_TIER.get(approval.tier);
  if (expectedCoordinates === undefined) {
    errors.push(`${label} tier is unsupported`);
  } else if (approval.applicationId !== expectedCoordinates.applicationId) {
    errors.push(
      `${label} applicationId must be ${expectedCoordinates.applicationId}`,
    );
  }
  if (approval.status !== 'approved')
    errors.push(`${label} status is not approved`);
  if (!Number.isSafeInteger(approval.versionCode) || approval.versionCode < 1) {
    errors.push(`${label} versionCode is invalid`);
  } else if (approval.versionCode !== expectedCoordinates?.versionCode) {
    errors.push(`${label} versionCode does not match the release coordinate`);
  }
  if (
    typeof approval.versionName !== 'string' ||
    !SAFE_VERSION_NAME.test(approval.versionName)
  ) {
    errors.push(`${label} versionName is invalid`);
  } else if (approval.versionName !== expectedCoordinates?.versionName) {
    errors.push(`${label} versionName does not match the release coordinate`);
  }
  if (!GIT_REVISION.test(approval.sourceRevision ?? '')) {
    errors.push(`${label} sourceRevision is invalid`);
  } else if (approval.sourceRevision !== expected.sourceRevision) {
    errors.push(
      `${label} sourceRevision does not match the clean Git revision`,
    );
  }
  if (!CHANNELS.has(approval.channel))
    errors.push(`${label} channel is unsupported`);
  if (
    typeof approval.installerPackage !== 'string' ||
    !/^[A-Za-z0-9_.]{3,255}$/u.test(approval.installerPackage)
  ) {
    errors.push(`${label} installerPackage is invalid`);
  }
  if (
    approval.channel === 'google-play' &&
    approval.installerPackage !== 'com.android.vending'
  ) {
    errors.push(`${label} Google Play evidence must name com.android.vending`);
  }
  if (
    Object.hasOwn(approval, 'playUploadApproved') &&
    typeof approval.playUploadApproved !== 'boolean'
  ) {
    errors.push(`${label} playUploadApproved must be boolean`);
  }
  if (
    approval.channel === 'google-play' &&
    approval.playUploadApproved !== true
  ) {
    errors.push(`${label} Play upload approval is not verified`);
  }

  const installedCertificate =
    typeof approval.signingCertificateSha256 === 'string'
      ? normalizeCertificate(approval.signingCertificateSha256)
      : '';
  if (!SHA256.test(installedCertificate)) {
    errors.push(`${label} installed signing certificate digest is invalid`);
  }
  const hasUploadCertificate = Object.hasOwn(
    approval,
    'uploadSigningCertificateSha256',
  );
  const uploadCertificate =
    typeof approval.uploadSigningCertificateSha256 === 'string'
      ? normalizeCertificate(approval.uploadSigningCertificateSha256)
      : '';
  if (approval.channel === 'google-play') {
    if (!hasUploadCertificate || !SHA256.test(uploadCertificate)) {
      errors.push(
        `${label} Google Play upload signing certificate digest is invalid`,
      );
    }
  } else {
    if (hasUploadCertificate) {
      errors.push(
        `${label} non-Play approval must not contain uploadSigningCertificateSha256`,
      );
    }
    if (Object.hasOwn(approval, 'playUploadApproved')) {
      errors.push(
        `${label} non-Play approval must not contain playUploadApproved`,
      );
    }
    if (Object.hasOwn(approval, 'artifactAabSha256')) {
      errors.push(
        `${label} non-Play approval must not contain artifactAabSha256`,
      );
    }
  }
  if (
    !Number.isInteger(approval.minimumCertifiedApi) ||
    !Number.isInteger(approval.maximumCertifiedApi) ||
    approval.minimumCertifiedApi !== expected.minimumSupportedApi ||
    approval.maximumCertifiedApi < expected.targetApi ||
    approval.maximumCertifiedApi > 37 ||
    approval.maximumCertifiedApi < approval.minimumCertifiedApi
  ) {
    errors.push(`${label} certified API range is invalid`);
  }
  for (const field of REQUIRED_TRUE_FIELDS) {
    if (approval[field] !== true)
      errors.push(`${label} ${field} is not verified`);
  }
  for (const [referenceField, digestField] of REFERENCED_EVIDENCE) {
    if (!isSafeReference(approval[referenceField])) {
      errors.push(`${label} ${referenceField} is invalid`);
    }
    if (!SHA256.test(approval[digestField] ?? '')) {
      errors.push(`${label} ${digestField} must be a lowercase SHA-256 digest`);
    }
  }
  if (
    Object.hasOwn(approval, 'artifactApkSha256') &&
    !SHA256.test(approval.artifactApkSha256)
  ) {
    errors.push(
      `${label} artifactApkSha256 must be a lowercase SHA-256 digest`,
    );
  }
  if (
    Object.hasOwn(approval, 'artifactAabSha256') &&
    !SHA256.test(approval.artifactAabSha256)
  ) {
    errors.push(
      `${label} artifactAabSha256 must be a lowercase SHA-256 digest`,
    );
  }
  if (
    !Array.isArray(approval.launchCountries) ||
    !approval.launchCountries.includes('IN') ||
    approval.launchCountries.some(country => !/^[A-Z]{2}$/u.test(country)) ||
    new Set(approval.launchCountries).size !== approval.launchCountries.length
  ) {
    errors.push(
      `${label} launchCountries must be unique ISO codes and include IN`,
    );
  }

  const approvedAt = parseInstant(
    approval.approvedAt,
    `${label} approvedAt`,
    errors,
  );
  const validUntil = parseInstant(
    approval.validUntil,
    `${label} validUntil`,
    errors,
  );
  if (approvedAt !== null && approvedAt > now + 5 * 60 * 1000) {
    errors.push(`${label} approvedAt is in the future`);
  }
  if (validUntil !== null && validUntil <= now) {
    errors.push(`${label} approval is expired`);
  }
  if (approvedAt !== null && validUntil !== null && validUntil <= approvedAt) {
    errors.push(`${label} approval validity is inverted`);
  }
  if (
    approvedAt !== null &&
    validUntil !== null &&
    validUntil > approvedAt + 366 * 24 * 60 * 60 * 1000
  ) {
    errors.push(`${label} approval validity exceeds one year`);
  }
  return errors;
};

export function validateDistributionEvidence(
  document,
  expected,
  now = Date.now(),
) {
  const errors = [];
  if (!isObject(document)) return { errors: ['evidence must be an object'] };
  assertExactKeys(document, TOP_LEVEL_KEYS, 'evidence', errors);
  assertRequiredKeys(document, [...TOP_LEVEL_KEYS], 'evidence', errors);
  if (document.schemaVersion !== 4) errors.push('schemaVersion must be 4');
  if (!GIT_REVISION.test(expected.sourceRevision ?? '')) {
    errors.push('expected clean Git source revision is invalid');
  }
  const artifactMode = expected.artifactMode ?? 'prepackage';
  if (!ARTIFACT_MODES.has(artifactMode)) {
    errors.push('expected artifact mode is invalid');
  }
  const artifactSigningCertificate = normalizeCertificate(
    expected.artifactSigningCertificateSha256 ??
      expected.signingCertificateSha256 ??
      '',
  );
  if (!SHA256.test(artifactSigningCertificate)) {
    errors.push('expected artifact signing certificate digest is invalid');
  }
  const artifactSha256 = expected.artifactSha256 ?? expected.apkSha256;
  if (artifactMode === 'prepackage' && artifactSha256 !== undefined) {
    errors.push('prepackage validation must not receive artifact bytes');
  } else if (
    artifactMode !== 'prepackage' &&
    !SHA256.test(artifactSha256 ?? '')
  ) {
    errors.push('expected artifact digest is invalid');
  }
  if (
    !Array.isArray(document.approvals) ||
    document.approvals.length < 1 ||
    document.approvals.length > 2
  ) {
    return { errors: [...errors, 'approvals must contain one or two entries'] };
  }

  const tiers = [];
  for (const [index, candidate] of document.approvals.entries()) {
    if (!isObject(candidate)) {
      errors.push(`approval ${index + 1} must be an object`);
      continue;
    }
    const label = `${String(candidate.tier ?? `entry-${index + 1}`)} approval`;
    tiers.push(candidate.tier);
    errors.push(...validateApproval(candidate, expected, now, label));
  }
  if (new Set(tiers).size !== tiers.length) {
    errors.push('approval tiers must be unique');
  }

  const expectedTierCoordinates = RELEASE_COORDINATES_BY_TIER.get(
    expected.tier,
  );
  if (expectedTierCoordinates === undefined) {
    errors.push(`artifact tier ${String(expected.tier)} is unsupported`);
  } else if (expected.applicationId !== expectedTierCoordinates.applicationId) {
    errors.push(
      `artifact applicationId for ${expected.tier} must be ${expectedTierCoordinates.applicationId}`,
    );
  } else if (
    expected.versionCode !== expectedTierCoordinates.versionCode ||
    expected.versionName !== expectedTierCoordinates.versionName
  ) {
    errors.push(
      `artifact version for ${expected.tier} must be ${expectedTierCoordinates.versionName} (${expectedTierCoordinates.versionCode})`,
    );
  }
  const approval = document.approvals.find(
    candidate => isObject(candidate) && candidate.tier === expected.tier,
  );
  if (!approval) {
    errors.push(`${expected.tier} approval is missing`);
  } else {
    if (approval.applicationId !== expected.applicationId) {
      errors.push('applicationId does not match the artifact');
    }
    if (approval.versionCode !== expected.versionCode) {
      errors.push('versionCode does not match the artifact');
    }
    if (approval.versionName !== expected.versionName) {
      errors.push('versionName does not match the artifact');
    }
    const installedCertificate = normalizeCertificate(
      typeof approval.signingCertificateSha256 === 'string'
        ? approval.signingCertificateSha256
        : '',
    );
    const uploadCertificate = normalizeCertificate(
      typeof approval.uploadSigningCertificateSha256 === 'string'
        ? approval.uploadSigningCertificateSha256
        : '',
    );
    const isPlay = approval.channel === 'google-play';
    if (artifactMode === 'prepackage') {
      const expectedBuildCertificate = isPlay
        ? uploadCertificate
        : installedCertificate;
      if (artifactSigningCertificate !== expectedBuildCertificate) {
        errors.push(
          isPlay
            ? 'build keystore does not match the approved Google Play upload certificate'
            : 'build keystore does not match the approved installed APK certificate',
        );
      }
    } else if (artifactMode === 'direct-apk') {
      if (isPlay) {
        errors.push('direct APK verification cannot use Google Play approval');
      }
      if (artifactSigningCertificate !== installedCertificate) {
        errors.push(
          'direct APK signer does not match the approved installed APK certificate',
        );
      }
      if (approval.artifactApkSha256 !== artifactSha256) {
        errors.push('artifactApkSha256 does not match the exact APK bytes');
      }
    } else if (artifactMode === 'play-aab') {
      if (!isPlay) {
        errors.push('Play AAB verification requires Google Play approval');
      }
      if (artifactSigningCertificate !== uploadCertificate) {
        errors.push(
          'AAB signer does not match the approved Google Play upload certificate',
        );
      }
      if (approval.artifactAabSha256 !== artifactSha256) {
        errors.push('artifactAabSha256 does not match the exact AAB bytes');
      }
    } else if (artifactMode === 'play-delivered-apk') {
      if (!isPlay) {
        errors.push(
          'Play-delivered APK verification requires Google Play approval',
        );
      }
      if (artifactSigningCertificate !== installedCertificate) {
        errors.push(
          'Play-delivered APK signer does not match the approved installed APK certificate',
        );
      }
      if (!SHA256.test(approval.artifactAabSha256 ?? '')) {
        errors.push(
          'Play-delivered APK evidence must retain the exact approved AAB digest',
        );
      }
      if (approval.artifactApkSha256 !== artifactSha256) {
        errors.push('artifactApkSha256 does not match the exact APK bytes');
      }
    }
  }

  const normalizedApproval = approval
    ? {
        ...approval,
        signingCertificateSha256: normalizeCertificate(
          typeof approval.signingCertificateSha256 === 'string'
            ? approval.signingCertificateSha256
            : '',
        ),
        ...(typeof approval.uploadSigningCertificateSha256 !== 'string'
          ? {}
          : {
              uploadSigningCertificateSha256: normalizeCertificate(
                approval.uploadSigningCertificateSha256,
              ),
            }),
      }
    : undefined;
  return errors.length === 0
    ? { approval: normalizedApproval, errors }
    : { errors };
}

const gitBytes = (repositoryRoot, arguments_) =>
  execFileSync(
    'git',
    [
      '-c',
      'core.fsmonitor=false',
      '-c',
      'core.untrackedCache=false',
      '-c',
      `core.excludesFile=${NULL_DEVICE}`,
      '-C',
      repositoryRoot,
      ...arguments_,
    ],
    {
      env: SANITIZED_GIT_ENVIRONMENT,
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  );

const git = (repositoryRoot, arguments_) =>
  gitBytes(repositoryRoot, arguments_).toString('utf8').trim();

export function hasUnsafeAuthorityPinIndexState(
  assumeOrSkipState,
  fsmonitorState,
) {
  const expectedNormalState = `H ${AUTHORITY_PIN_RELATIVE_PATH}`;
  return (
    assumeOrSkipState !== expectedNormalState ||
    fsmonitorState !== expectedNormalState
  );
}

export function inspectCleanGitSource(
  repositoryRoot = PROJECT_ROOT,
  observedPinBytes,
) {
  const errors = [];
  let canonicalRoot;
  let sourceRevision;
  try {
    canonicalRoot = realpathSync(repositoryRoot);
    const discoveredRoot = realpathSync(
      git(canonicalRoot, ['rev-parse', '--show-toplevel']),
    );
    if (discoveredRoot !== canonicalRoot) {
      errors.push(
        'distribution evidence must be validated from the repository root',
      );
    }
    sourceRevision = git(canonicalRoot, ['rev-parse', '--verify', 'HEAD']);
    if (!GIT_REVISION.test(sourceRevision)) {
      errors.push('Git HEAD is not a full lowercase SHA-1 revision');
    }
    const status = git(canonicalRoot, [
      'status',
      '--porcelain=v1',
      '--untracked-files=all',
    ]);
    if (status.length > 0) {
      errors.push('Git source tree is not clean');
    }
    const trackedPin = git(canonicalRoot, [
      'ls-files',
      '--stage',
      '--',
      AUTHORITY_PIN_RELATIVE_PATH,
    ]);
    if (
      !/^100644 [0-9a-f]{40} 0\ttools\/distribution-authority-pin\.json$/u.test(
        trackedPin,
      )
    ) {
      errors.push(
        'distribution authority digest pin must be a tracked regular file',
      );
    }
    const assumeOrSkipState = git(canonicalRoot, [
      'ls-files',
      '-v',
      '--',
      AUTHORITY_PIN_RELATIVE_PATH,
    ]);
    const fsmonitorState = git(canonicalRoot, [
      'ls-files',
      '-f',
      '--',
      AUTHORITY_PIN_RELATIVE_PATH,
    ]);
    if (hasUnsafeAuthorityPinIndexState(assumeOrSkipState, fsmonitorState)) {
      errors.push(
        'distribution authority digest pin must not use assume-unchanged, skip-worktree, or fsmonitor-valid index flags',
      );
    }

    const pinPath = resolve(canonicalRoot, AUTHORITY_PIN_RELATIVE_PATH);
    const relativePin = relative(canonicalRoot, realpathSync(pinPath));
    if (
      relativePin.startsWith(`..${sep}`) ||
      relativePin === '..' ||
      lstatSync(pinPath).isSymbolicLink()
    ) {
      errors.push('distribution authority digest pin must not be a symlink');
    }
    const pinBytes = observedPinBytes ?? readFileSync(pinPath);
    const committedPinBytes = gitBytes(canonicalRoot, [
      'show',
      `${sourceRevision}:${AUTHORITY_PIN_RELATIVE_PATH}`,
    ]);
    if (!Buffer.isBuffer(pinBytes) || !pinBytes.equals(committedPinBytes)) {
      errors.push(
        'distribution authority digest pin bytes do not exactly match HEAD',
      );
    }
    if (!Buffer.isBuffer(pinBytes) || !pinBytes.equals(readFileSync(pinPath))) {
      errors.push(
        'distribution authority digest pin changed during validation',
      );
    }
    if (
      git(canonicalRoot, ['rev-parse', '--verify', 'HEAD']) !== sourceRevision
    ) {
      errors.push('Git HEAD changed during distribution evidence validation');
    }
  } catch {
    errors.push('clean Git source revision cannot be established');
  }
  return errors.length === 0
    ? { errors, sourceRevision, repositoryRoot: canonicalRoot }
    : { errors };
}

const readBoundedFile = (path, maximumBytes, label) => {
  const value = readFileSync(path);
  if (value.byteLength === 0 || value.byteLength > maximumBytes) {
    throw new Error(`${label} has an invalid size`);
  }
  return value;
};

function run() {
  let argumentsByName;
  try {
    argumentsByName = parseArguments(process.argv.slice(2));
  } catch (error) {
    fail(error instanceof Error ? error.message : 'invalid arguments');
    return;
  }
  const required = [
    'file',
    'signature',
    'public-key',
    'tier',
    'package',
    'version-code',
    'version-name',
    'artifact-mode',
    'artifact-signing-certificate',
  ];
  const missing = required.filter(name => !argumentsByName.get(name));
  if (missing.length > 0) {
    fail(`missing arguments: ${missing.join(', ')}`);
    return;
  }
  const output = argumentsByName.get('output') ?? 'text';
  if (output !== 'text' && output !== 'json') {
    fail('output must be text or json');
    return;
  }

  let rawEvidence;
  let detachedSignature;
  let publicKeyBytes;
  let rawPin;
  let pinDocument;
  try {
    rawEvidence = readBoundedFile(
      argumentsByName.get('file'),
      MAXIMUM_EVIDENCE_BYTES,
      'evidence file',
    );
    detachedSignature = readBoundedFile(
      argumentsByName.get('signature'),
      ED25519_SIGNATURE_BYTES,
      'detached signature',
    );
    publicKeyBytes = readBoundedFile(
      argumentsByName.get('public-key'),
      MAXIMUM_PUBLIC_KEY_BYTES,
      'authority public key',
    );
    rawPin = readBoundedFile(
      AUTHORITY_PIN_FILE,
      MAXIMUM_PIN_BYTES,
      'authority pin',
    );
    pinDocument = JSON.parse(rawPin.toString('utf8'));
  } catch (error) {
    fail(
      error instanceof Error
        ? error.message
        : 'authority inputs are unreadable',
    );
    return;
  }

  const authority = verifyDistributionEvidenceAuthority({
    rawEvidence,
    detachedSignature,
    publicKeyBytes,
    pinDocument,
  });
  if (authority.errors.length > 0) {
    for (const error of authority.errors) fail(error);
    return;
  }

  const source = inspectCleanGitSource(PROJECT_ROOT, rawPin);
  if (source.errors.length > 0) {
    for (const error of source.errors) fail(error);
    return;
  }

  let document;
  try {
    document = JSON.parse(rawEvidence.toString('utf8'));
  } catch {
    fail('evidence file is malformed JSON');
    return;
  }
  const versionCode = Number(argumentsByName.get('version-code'));
  if (!Number.isSafeInteger(versionCode) || versionCode < 1) {
    fail('artifact version code is invalid');
    return;
  }
  const versionName = argumentsByName.get('version-name');
  if (!SAFE_VERSION_NAME.test(versionName)) {
    fail('artifact version name is invalid');
    return;
  }
  const artifactMode = argumentsByName.get('artifact-mode');
  if (!ARTIFACT_MODES.has(artifactMode)) {
    fail('artifact mode is invalid');
    return;
  }
  const artifactSigningCertificate = normalizeCertificate(
    argumentsByName.get('artifact-signing-certificate'),
  );
  if (!SHA256.test(artifactSigningCertificate)) {
    fail('artifact signing certificate digest is invalid');
    return;
  }
  const artifactFile = argumentsByName.get('artifact-file');
  if (artifactMode === 'prepackage' && artifactFile !== undefined) {
    fail('prepackage validation must not receive an artifact file');
    return;
  }
  if (artifactMode !== 'prepackage' && artifactFile === undefined) {
    fail(`${artifactMode} validation requires an artifact file`);
    return;
  }
  let artifactSha256;
  if (artifactFile !== undefined) {
    try {
      artifactSha256 = sha256File(artifactFile);
    } catch {
      fail('artifact is missing, invalid, or changed while hashing');
      return;
    }
  }
  const now = Date.now();

  const result = validateDistributionEvidence(
    document,
    {
      tier: argumentsByName.get('tier'),
      applicationId: argumentsByName.get('package'),
      versionCode,
      versionName,
      artifactMode,
      artifactSigningCertificateSha256: artifactSigningCertificate,
      sourceRevision: source.sourceRevision,
      minimumSupportedApi: 29,
      targetApi: 36,
      ...(artifactSha256 === undefined ? {} : { artifactSha256 }),
    },
    now,
  );
  if (result.errors.length > 0) {
    for (const error of result.errors) fail(error);
    return;
  }
  if (output === 'json') {
    process.stdout.write(
      `${JSON.stringify({
        approval: result.approval,
        authorityPublicKeySpkiSha256: authority.publicKeySpkiSha256,
        sourceRevision: source.sourceRevision,
      })}\n`,
    );
    return;
  }
  process.stdout.write(
    `PASS tier=${result.approval.tier} channel=${result.approval.channel} mode=${artifactMode} installer=${result.approval.installerPackage} source=${source.sourceRevision}\n`,
  );
}

if (
  process.argv[1] &&
  realpathSync(SCRIPT_FILE) === realpathSync(process.argv[1])
) {
  run();
}
