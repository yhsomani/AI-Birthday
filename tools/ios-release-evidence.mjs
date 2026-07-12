import { createHash } from 'node:crypto';
import {
  closeSync,
  fstatSync,
  lstatSync,
  openSync,
  readSync,
  realpathSync,
} from 'node:fs';
import path from 'node:path';

export const IOS_RELEASE_REFERENCE_NAMES = Object.freeze([
  'artifactProvenance',
  'mobileSbom',
  'nativeSbom',
  'dependencyAudit',
  'firebaseReview',
  'protectedStorageBackup',
  'physicalDeviceMatrix',
  'performance',
  'accessibility',
  'privacyReview',
  'appStoreSubmission',
  'loginServicesReview',
  'accountDeletion',
  'incidentRollback',
]);

const REQUIRED_APPROVALS = Object.freeze([
  'nativeSupplyChainReviewed',
  'firebaseAndAppCheckVerified',
  'protectedStorageAndBackupVerified',
  'physicalDeviceMatrixPassed',
  'performanceBudgetsPassed',
  'accessibilityPassed',
  'privacyMaterialsApproved',
  'appStoreSubmissionMaterialsApproved',
  'loginServicesRationaleApproved',
  'accountDeletionVerified',
  'rollbackAndIncidentReady',
  'companionOnlyClaimsApproved',
  'noBackgroundSmsVerified',
]);

const TOP_LEVEL_KEYS = new Set([
  '$schema',
  'schemaVersion',
  'product',
  'status',
  'sourceRevision',
  'artifact',
  'firebase',
  'signing',
  'security',
  'references',
  'approvals',
]);
const ARTIFACT_KEYS = new Set([
  'archiveTreeSha256',
  'ipaSha256',
  'exportOptionsSha256',
  'bundleIdentifier',
  'marketingVersion',
  'buildNumber',
  'minimumOSVersion',
  'platform',
  'appBinarySha256',
  'embeddedFrameworksManifestSha256',
]);
const FIREBASE_KEYS = new Set([
  'environment',
  'projectId',
  'projectNumber',
  'googleAppId',
  'oauthClientId',
  'reversedClientId',
  'configSha256',
  'apiKeySha256',
]);
const SIGNING_KEYS = new Set([
  'distributionMethod',
  'teamIdentifier',
  'archiveCertificateExpiration',
  'archiveCertificateSha256',
  'exportedCertificateExpiration',
  'exportedCertificateSha256',
  'archiveProvisioningProfileUuid',
  'archiveProvisioningProfileName',
  'archiveProvisioningProfileExpiration',
  'exportedProvisioningProfileUuid',
  'exportedProvisioningProfileName',
  'exportedProvisioningProfileExpiration',
  'applicationIdentifier',
]);
const SECURITY_KEYS = new Set([
  'entitlementsSha256',
  'infoPlistSha256',
  'privacyManifestSha256',
  'arm64Only',
  'debugEntitlementAbsent',
  'appAttestProduction',
  'backupExclusionVerified',
  'protectedStoreVerified',
  'debugProviderAbsent',
  'noForbiddenCapabilities',
  'noForbiddenUrlSchemes',
  'privacyManifestReviewed',
]);
const OBSERVED_SECURITY_KEYS = new Set([
  'entitlementsSha256',
  'infoPlistSha256',
  'privacyManifestSha256',
  'arm64Only',
  'debugEntitlementAbsent',
  'appAttestProduction',
  'noForbiddenCapabilities',
  'noForbiddenUrlSchemes',
]);
const APPROVAL_KEYS = new Set([
  'approvedAt',
  'validUntil',
  ...REQUIRED_APPROVALS,
]);
const REFERENCE_KEYS = new Set(['path', 'sha256']);
const SHA256 = /^[0-9a-f]{64}$/u;
const REVISION = /^[0-9a-f]{40}$/u;
const SAFE_IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$/u;
const SAFE_VERSION = /^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$/u;
const BUILD_NUMBER = /^[1-9][0-9]{0,17}$/u;
const PROJECT_NUMBER = /^[1-9][0-9]{5,19}$/u;
const GOOGLE_APP_ID = /^1:[1-9][0-9]{5,19}:ios:[0-9a-f]{8,64}$/u;
const OAUTH_CLIENT = /^[0-9]+-[a-z0-9-]+\.apps\.googleusercontent\.com$/u;
const REVERSED_CLIENT = /^com\.googleusercontent\.apps\.[A-Za-z0-9-]+$/u;
const TEAM_IDENTIFIER = /^[A-Z0-9]{10}$/u;
const PROFILE_UUID =
  /^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$/u;
const SAFE_RELATIVE_PATH = /^[A-Za-z0-9][A-Za-z0-9._/-]{0,511}$/u;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const MAXIMUM_REFERENCE_BYTES = 1024 * 1024 * 1024;

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const exactKeys = (value, expected, label, errors) => {
  if (!isObject(value)) {
    errors.push(`${label} must be an object`);
    return false;
  }
  const actual = Object.keys(value);
  if (
    actual.length !== expected.size ||
    actual.some(key => !expected.has(key))
  ) {
    errors.push(`${label} fields do not match the exact schema`);
    return false;
  }
  return true;
};

const parseInstant = (value, label, errors) => {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  const date = new Date(parsed);
  if (
    date.getUTCFullYear() !== Number(value.slice(0, 4)) ||
    date.getUTCMonth() + 1 !== Number(value.slice(5, 7)) ||
    date.getUTCDate() !== Number(value.slice(8, 10)) ||
    date.getUTCHours() !== Number(value.slice(11, 13)) ||
    date.getUTCMinutes() !== Number(value.slice(14, 16)) ||
    date.getUTCSeconds() !== Number(value.slice(17, 19))
  ) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  return parsed;
};

const requireString = (value, pattern, label, errors) => {
  if (typeof value !== 'string' || !pattern.test(value)) {
    errors.push(`${label} is invalid`);
  }
};

const requireDigest = (value, label, errors) => {
  if (typeof value !== 'string' || !SHA256.test(value)) {
    errors.push(`${label} must be a lowercase SHA-256 digest`);
  } else if (/^0{64}$/u.test(value)) {
    errors.push(`${label} must not use the template zero digest`);
  }
};

const isSafeProfileName = value =>
  typeof value === 'string' &&
  value.length >= 1 &&
  value.length <= 128 &&
  [...value].every(character => {
    const codePoint = character.codePointAt(0);
    return codePoint !== undefined && codePoint >= 32 && codePoint !== 127;
  });

export const stableJson = value => {
  if (Array.isArray(value)) {
    return `[${value.map(stableJson).join(',')}]`;
  }
  if (isObject(value)) {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
};

export const canonicalSha256 = value =>
  createHash('sha256').update(stableJson(value), 'utf8').digest('hex');

const validateArtifact = (artifact, errors) => {
  if (!exactKeys(artifact, ARTIFACT_KEYS, 'artifact', errors)) return;
  for (const field of [
    'archiveTreeSha256',
    'ipaSha256',
    'exportOptionsSha256',
    'appBinarySha256',
    'embeddedFrameworksManifestSha256',
  ]) {
    requireDigest(artifact[field], `artifact.${field}`, errors);
  }
  if (artifact.bundleIdentifier !== 'com.yashsomani.birthdayautopilot') {
    errors.push('artifact.bundleIdentifier must be the production bundle');
  }
  requireString(
    artifact.marketingVersion,
    SAFE_VERSION,
    'artifact.marketingVersion',
    errors,
  );
  requireString(
    artifact.buildNumber,
    BUILD_NUMBER,
    'artifact.buildNumber',
    errors,
  );
  if (artifact.minimumOSVersion !== '15.1') {
    errors.push('artifact.minimumOSVersion must be 15.1');
  }
  if (artifact.platform !== 'iphoneos') {
    errors.push('artifact.platform must be iphoneos');
  }
};

const validateFirebase = (firebase, errors) => {
  if (!exactKeys(firebase, FIREBASE_KEYS, 'firebase', errors)) return;
  if (firebase.environment !== 'prod') {
    errors.push('firebase.environment must be prod');
  }
  requireString(
    firebase.projectId,
    SAFE_IDENTIFIER,
    'firebase.projectId',
    errors,
  );
  requireString(
    firebase.projectNumber,
    PROJECT_NUMBER,
    'firebase.projectNumber',
    errors,
  );
  requireString(
    firebase.googleAppId,
    GOOGLE_APP_ID,
    'firebase.googleAppId',
    errors,
  );
  requireString(
    firebase.oauthClientId,
    OAUTH_CLIENT,
    'firebase.oauthClientId',
    errors,
  );
  requireString(
    firebase.reversedClientId,
    REVERSED_CLIENT,
    'firebase.reversedClientId',
    errors,
  );
  const expectedReversed =
    typeof firebase.oauthClientId === 'string'
      ? firebase.oauthClientId.split('.').reverse().join('.')
      : '';
  if (firebase.reversedClientId !== expectedReversed) {
    errors.push('firebase.reversedClientId does not match oauthClientId');
  }
  if (
    typeof firebase.googleAppId === 'string' &&
    typeof firebase.projectNumber === 'string' &&
    !firebase.googleAppId.startsWith(`1:${firebase.projectNumber}:ios:`)
  ) {
    errors.push('firebase.googleAppId does not match projectNumber');
  }
  requireDigest(firebase.configSha256, 'firebase.configSha256', errors);
  requireDigest(firebase.apiKeySha256, 'firebase.apiKeySha256', errors);
};

const validateSigning = (signing, errors, now) => {
  if (!exactKeys(signing, SIGNING_KEYS, 'signing', errors)) return null;
  if (signing.distributionMethod !== 'app-store-connect') {
    errors.push('signing.distributionMethod must be app-store-connect');
  }
  requireString(
    signing.teamIdentifier,
    TEAM_IDENTIFIER,
    'signing.teamIdentifier',
    errors,
  );
  for (const field of [
    'archiveCertificateSha256',
    'exportedCertificateSha256',
  ]) {
    requireDigest(signing[field], `signing.${field}`, errors);
  }
  const archiveCertificateExpiration = parseInstant(
    signing.archiveCertificateExpiration,
    'signing.archiveCertificateExpiration',
    errors,
  );
  const exportedCertificateExpiration = parseInstant(
    signing.exportedCertificateExpiration,
    'signing.exportedCertificateExpiration',
    errors,
  );
  for (const field of [
    'archiveProvisioningProfileUuid',
    'exportedProvisioningProfileUuid',
  ]) {
    requireString(signing[field], PROFILE_UUID, `signing.${field}`, errors);
  }
  for (const field of [
    'archiveProvisioningProfileName',
    'exportedProvisioningProfileName',
  ]) {
    if (!isSafeProfileName(signing[field])) {
      errors.push(`signing.${field} is invalid`);
    }
  }
  const archiveExpiration = parseInstant(
    signing.archiveProvisioningProfileExpiration,
    'signing.archiveProvisioningProfileExpiration',
    errors,
  );
  const exportedExpiration = parseInstant(
    signing.exportedProvisioningProfileExpiration,
    'signing.exportedProvisioningProfileExpiration',
    errors,
  );
  if (archiveExpiration !== null && archiveExpiration <= now) {
    errors.push('archive provisioning profile is expired');
  }
  if (exportedExpiration !== null && exportedExpiration <= now) {
    errors.push('exported provisioning profile is expired');
  }
  if (
    archiveCertificateExpiration !== null &&
    archiveCertificateExpiration <= now
  ) {
    errors.push('archive signing certificate is expired');
  }
  if (
    exportedCertificateExpiration !== null &&
    exportedCertificateExpiration <= now
  ) {
    errors.push('exported signing certificate is expired');
  }
  const expectedApplicationIdentifier = `${String(
    signing.teamIdentifier,
  )}.com.yashsomani.birthdayautopilot`;
  if (signing.applicationIdentifier !== expectedApplicationIdentifier) {
    errors.push('signing.applicationIdentifier does not match team and bundle');
  }
  return {
    'archive signing certificate': archiveCertificateExpiration,
    'archive provisioning profile': archiveExpiration,
    'exported signing certificate': exportedCertificateExpiration,
    'exported provisioning profile': exportedExpiration,
  };
};

const validateSecurity = (security, errors) => {
  if (!exactKeys(security, SECURITY_KEYS, 'security', errors)) return;
  for (const field of [
    'entitlementsSha256',
    'infoPlistSha256',
    'privacyManifestSha256',
  ]) {
    requireDigest(security[field], `security.${field}`, errors);
  }
  for (const field of [...SECURITY_KEYS].filter(
    securityField => !securityField.endsWith('Sha256'),
  )) {
    if (security[field] !== true) {
      errors.push(`security.${field} must be true`);
    }
  }
};

const validateReferences = (references, referenceDigests, errors) => {
  const expected = new Set(IOS_RELEASE_REFERENCE_NAMES);
  if (!exactKeys(references, expected, 'references', errors)) return;
  const referencedPaths = new Set();
  for (const name of IOS_RELEASE_REFERENCE_NAMES) {
    const reference = references[name];
    const label = `references.${name}`;
    if (!exactKeys(reference, REFERENCE_KEYS, label, errors)) continue;
    if (
      typeof reference.path !== 'string' ||
      !SAFE_RELATIVE_PATH.test(reference.path) ||
      path.isAbsolute(reference.path) ||
      reference.path.split('/').some(part => part === '' || part === '..') ||
      reference.path.includes('\\')
    ) {
      errors.push(`${label}.path must be a normalized relative path`);
    } else if (referencedPaths.has(reference.path)) {
      errors.push(`${label}.path duplicates another evidence reference`);
    } else {
      referencedPaths.add(reference.path);
    }
    requireDigest(reference.sha256, `${label}.sha256`, errors);
    if (referenceDigests !== undefined) {
      if (!Object.hasOwn(referenceDigests, name)) {
        errors.push(`${label} bytes were not supplied`);
      } else if (referenceDigests[name] !== reference.sha256) {
        errors.push(`${label}.sha256 does not match the referenced bytes`);
      }
    }
  }
};

const validateApprovals = (approvals, errors, now, validityLimits) => {
  if (!exactKeys(approvals, APPROVAL_KEYS, 'approvals', errors)) return;
  const approvedAt = parseInstant(
    approvals.approvedAt,
    'approvals.approvedAt',
    errors,
  );
  const validUntil = parseInstant(
    approvals.validUntil,
    'approvals.validUntil',
    errors,
  );
  if (approvedAt !== null && approvedAt > now + 5 * 60 * 1_000) {
    errors.push('approvals.approvedAt is in the future');
  }
  if (validUntil !== null && validUntil <= now) {
    errors.push('iOS release approval is expired');
  }
  if (approvedAt !== null && validUntil !== null) {
    if (validUntil <= approvedAt) {
      errors.push('iOS release approval validity is inverted');
    }
    if (validUntil > approvedAt + 90 * 24 * 60 * 60 * 1_000) {
      errors.push('iOS release approval validity exceeds 90 days');
    }
  }
  if (validUntil !== null && validityLimits !== null) {
    for (const [label, expiration] of Object.entries(validityLimits)) {
      if (expiration !== null && validUntil > expiration) {
        errors.push(`approval outlives the ${label}`);
      }
    }
  }
  for (const field of REQUIRED_APPROVALS) {
    if (approvals[field] !== true) {
      errors.push(`approvals.${field} must be true`);
    }
  }
};

const compareObserved = (document, observed, errors) => {
  if (observed === undefined) return;
  const comparisons = [
    ['sourceRevision', document.sourceRevision, observed.sourceRevision],
    ...[...ARTIFACT_KEYS].map(field => [
      `artifact.${field}`,
      document.artifact?.[field],
      observed.artifact?.[field],
    ]),
    ...[...FIREBASE_KEYS].map(field => [
      `firebase.${field}`,
      document.firebase?.[field],
      observed.firebase?.[field],
    ]),
    ...[...SIGNING_KEYS].map(field => [
      `signing.${field}`,
      document.signing?.[field],
      observed.signing?.[field],
    ]),
    ...[...OBSERVED_SECURITY_KEYS].map(field => [
      `security.${field}`,
      document.security?.[field],
      observed.security?.[field],
    ]),
  ];
  for (const [label, expected, actual] of comparisons) {
    if (expected !== actual) {
      errors.push(`${label} does not match the inspected archive and IPA`);
    }
  }
};

export function validateIOSReleaseEvidence(
  document,
  { observed, referenceDigests, now = Date.now() } = {},
) {
  const errors = [];
  if (!exactKeys(document, TOP_LEVEL_KEYS, 'evidence', errors)) {
    return { errors };
  }
  if (document.$schema !== './ios-release-evidence.schema.json') {
    errors.push('$schema must reference ./ios-release-evidence.schema.json');
  }
  if (document.schemaVersion !== 1) errors.push('schemaVersion must be 1');
  if (document.product !== 'birthday-autopilot-ios') {
    errors.push('product must be birthday-autopilot-ios');
  }
  if (document.status !== 'approved') errors.push('status must be approved');
  requireString(document.sourceRevision, REVISION, 'sourceRevision', errors);
  validateArtifact(document.artifact, errors);
  validateFirebase(document.firebase, errors);
  const validityLimits = validateSigning(document.signing, errors, now);
  validateSecurity(document.security, errors);
  validateReferences(document.references, referenceDigests, errors);
  validateApprovals(document.approvals, errors, now, validityLimits);
  compareObserved(document, observed, errors);
  return { errors };
}

const assertWithinRoot = (root, candidate, label) => {
  const relative = path.relative(root, candidate);
  if (
    relative === '' ||
    relative === '..' ||
    relative.startsWith(`..${path.sep}`) ||
    path.isAbsolute(relative)
  ) {
    throw new Error(`${label} escapes the supporting-evidence root`);
  }
};

const resolveRegularReference = (root, referencePath) => {
  const parts = referencePath.split('/');
  let current = root;
  for (const part of parts) {
    current = path.join(current, part);
    const metadata = lstatSync(current, { bigint: true });
    if (metadata.isSymbolicLink()) {
      throw new Error(`${referencePath} must not contain symbolic links`);
    }
  }
  const resolved = realpathSync(current);
  assertWithinRoot(root, resolved, referencePath);
  const metadata = lstatSync(resolved, { bigint: true });
  if (
    !metadata.isFile() ||
    metadata.size <= 0n ||
    metadata.size > BigInt(MAXIMUM_REFERENCE_BYTES)
  ) {
    throw new Error(
      `${referencePath} must be a non-empty bounded regular file`,
    );
  }
  return { path: resolved, metadata };
};

const hashStableFile = (file, expectedMetadata) => {
  let descriptor;
  try {
    descriptor = openSync(file, 'r');
    const before = fstatSync(descriptor, { bigint: true });
    if (
      !before.isFile() ||
      before.dev !== expectedMetadata.dev ||
      before.ino !== expectedMetadata.ino ||
      before.size !== expectedMetadata.size ||
      before.mtimeNs !== expectedMetadata.mtimeNs ||
      before.ctimeNs !== expectedMetadata.ctimeNs
    ) {
      throw new Error('supporting evidence changed before hashing');
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
      throw new Error('supporting evidence changed while hashing');
    }
    return digest.digest('hex');
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
};

export function collectIOSReleaseReferenceDigests(rootPath, references) {
  const root = realpathSync(rootPath);
  if (!lstatSync(root).isDirectory()) {
    throw new Error('supporting-evidence root must be a directory');
  }
  const result = {};
  for (const name of IOS_RELEASE_REFERENCE_NAMES) {
    const reference = references?.[name];
    if (!isObject(reference) || typeof reference.path !== 'string') {
      throw new Error(`references.${name} is missing`);
    }
    const resolved = resolveRegularReference(root, reference.path);
    result[name] = hashStableFile(resolved.path, resolved.metadata);
  }
  return result;
}
