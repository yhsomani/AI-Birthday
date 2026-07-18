#!/usr/bin/env node

import { createHash } from 'node:crypto';
import {
  closeSync,
  constants,
  fstatSync,
  lstatSync,
  openSync,
  readFileSync,
  realpathSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  inspectCleanGitSource,
  sha256File,
  validateDistributionEvidence,
  verifyDistributionEvidenceAuthority,
} from './validate-distribution-evidence.mjs';

const PROJECT_ROOT = fileURLToPath(new URL('../', import.meta.url));
const AUTHORITY_PIN = path.join(
  PROJECT_ROOT,
  'tools/distribution-authority-pin.json',
);
const SHA256 = /^[0-9a-f]{64}$/u;
const MODES = new Set(['play-aab', 'direct-apk']);
const CLI_KEYS = new Set([
  'evidence',
  'signature',
  'public-key',
  'evidence-root',
  'tier',
  'artifact-mode',
  'artifact',
  'verification-report',
  'verification-manifest',
  'output',
]);

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const stableJson = value => {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value !== null && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
};

const stableMetadata = value => ({
  dev: value.dev,
  ino: value.ino,
  mode: value.mode,
  nlink: value.nlink,
  size: value.size,
  mtimeNs: value.mtimeNs,
  ctimeNs: value.ctimeNs,
});

const sameMetadata = (left, right) =>
  Object.keys(left).every(key => left[key] === right[key]);

const observeStablePath = (file, maximumBytes, label) => {
  const requested = path.resolve(file);
  const before = lstatSync(requested, { bigint: true });
  if (
    before.isSymbolicLink() ||
    !before.isFile() ||
    before.nlink !== 1n ||
    before.size <= 0n ||
    before.size > BigInt(maximumBytes)
  ) {
    throw new Error(`${label} must be a bounded, non-linked regular file`);
  }
  const canonical = realpathSync(requested);
  return {
    requested,
    canonical,
    requestedMetadata: stableMetadata(before),
  };
};

const assertUnchanged = (record, label) => {
  const requested = lstatSync(record.requested, { bigint: true });
  const canonical = lstatSync(record.canonical, { bigint: true });
  if (
    requested.isSymbolicLink() ||
    canonical.isSymbolicLink() ||
    !sameMetadata(record.requestedMetadata, stableMetadata(requested)) ||
    (record.canonicalMetadata !== undefined &&
      !sameMetadata(record.canonicalMetadata, stableMetadata(canonical))) ||
    realpathSync(record.requested) !== record.canonical
  ) {
    throw new Error(`${label} changed during Android report creation`);
  }
};

const readStableFile = (file, maximumBytes, label) => {
  const record = observeStablePath(file, maximumBytes, label);
  const descriptor = openSync(
    record.canonical,
    // File-descriptor flags intentionally form a bit mask.
    // eslint-disable-next-line no-bitwise
    constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
  );
  try {
    const opened = fstatSync(descriptor, { bigint: true });
    if (!sameMetadata(record.requestedMetadata, stableMetadata(opened))) {
      throw new Error(`${label} changed before it was read`);
    }
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    record.canonicalMetadata = stableMetadata(after);
    if (
      BigInt(bytes.byteLength) !== opened.size ||
      !sameMetadata(stableMetadata(opened), stableMetadata(after))
    ) {
      throw new Error(`${label} changed while it was read`);
    }
    assertUnchanged(record, label);
    return { ...record, bytes };
  } finally {
    closeSync(descriptor);
  }
};

const parseJson = (bytes, label) => {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is malformed JSON`);
  }
};

const normalizeDigest = value =>
  typeof value === 'string'
    ? value.replaceAll(':', '').trim().toLowerCase()
    : '';

export const parseAndroidFirebaseVerification = reportText => {
  const matches = [
    ...reportText.matchAll(
      /^PASS Android Firebase project=([a-z][a-z0-9-]{4,28}[a-z0-9]) number=([1-9][0-9]{5,19}) app-id=(1:[1-9][0-9]{5,19}:android:[0-9a-f]{8,64}) web-oauth-client=([0-9]+-[a-z0-9-]+\.apps\.googleusercontent\.com)$/gmu,
    ),
  ];
  if (matches.length !== 1) {
    throw new Error(
      'full Android verifier must contain one exact Firebase projection',
    );
  }
  const [, projectId, projectNumber, androidAppId, webOauthClientId] =
    matches[0];
  if (
    !androidAppId.startsWith(`1:${projectNumber}:android:`) ||
    !webOauthClientId.startsWith(`${projectNumber}-`)
  ) {
    throw new Error(
      'full Android verifier Firebase projection is inconsistent',
    );
  }
  return Object.freeze({
    projectId,
    projectNumber,
    androidAppId,
    webOauthClientId,
  });
};

const findManifestEntry = (manifest, suffix, label) => {
  const entries = manifest.entries.filter(
    entry => entry.kind === 'file' && entry.path.endsWith(suffix),
  );
  if (entries.length !== 1) {
    throw new Error(`verification manifest does not bind one exact ${label}`);
  }
  return entries[0];
};

export function createAndroidReleaseVerificationReport({
  evidenceBytes,
  signatureBytes,
  publicKeyBytes,
  pinDocument,
  evidenceRoot,
  tier,
  artifactMode,
  artifactFileName,
  artifactSha256,
  verificationReportBytes,
  verificationManifestBytes,
  source,
  nowMs = Date.now(),
}) {
  if (!['lab', 'prod'].includes(tier) || !MODES.has(artifactMode)) {
    throw new Error('Android verification tier/mode is invalid');
  }
  const authority = verifyDistributionEvidenceAuthority({
    rawEvidence: evidenceBytes,
    detachedSignature: signatureBytes,
    publicKeyBytes,
    pinDocument,
  });
  if (authority.errors.length > 0) throw new Error(authority.errors.join('; '));
  if (source.errors.length > 0) throw new Error(source.errors.join('; '));
  const evidence = parseJson(evidenceBytes, 'Android distribution evidence');
  const approval = evidence.approvals?.find(
    candidate => candidate.tier === tier,
  );
  if (approval === undefined)
    throw new Error('Android tier approval is missing');
  const artifactSigningCertificateSha256 = normalizeDigest(
    artifactMode === 'play-aab'
      ? approval.uploadSigningCertificateSha256
      : approval.signingCertificateSha256,
  );
  const validation = validateDistributionEvidence(
    evidence,
    {
      tier,
      applicationId: approval.applicationId,
      versionCode: approval.versionCode,
      versionName: approval.versionName,
      artifactMode,
      artifactSigningCertificateSha256,
      artifactSha256,
      sourceRevision: source.sourceRevision,
      minimumSupportedApi: 29,
      targetApi: 36,
      evidenceRoot,
    },
    nowMs,
  );
  if (validation.errors.length > 0) {
    throw new Error(validation.errors.join('; '));
  }
  const reportText = verificationReportBytes.toString('utf8');
  if (reportText.includes('FAIL ')) {
    throw new Error('full Android verifier report contains a failure');
  }
  const policyLine = new RegExp(
    `^PASS tier=${tier} channel=${approval.channel} mode=${artifactMode} .* source=${source.sourceRevision}$`,
    'mu',
  );
  const fullLine =
    artifactMode === 'play-aab'
      ? new RegExp(
          `^PASS ${approval.applicationId.replaceAll('.', '\\.')} AAB version=${
            approval.versionCode
          } min=29 target=36 upload-signature=verified arm64-libs=[1-9][0-9]* load-segments=[1-9][0-9]*$`,
          'mu',
        )
      : new RegExp(
          `^PASS ${approval.applicationId.replaceAll('.', '\\.')} version=${
            approval.versionCode
          } min=29 target=36 signature=verified arm64-libs=[1-9][0-9]* load-segments=[1-9][0-9]*$`,
          'mu',
        );
  if (!policyLine.test(reportText) || !fullLine.test(reportText)) {
    throw new Error('full Android verifier PASS contract is incomplete');
  }
  const firebase =
    artifactMode === 'play-aab'
      ? parseAndroidFirebaseVerification(reportText)
      : null;
  const manifest = parseJson(
    verificationManifestBytes,
    'Android verification manifest',
  );
  if (
    manifest.schemaVersion !== 3 ||
    manifest.base !== 'android-authority-verified-release' ||
    manifest.provenance?.sourceRevision !== source.sourceRevision ||
    !Array.isArray(manifest.entries)
  ) {
    throw new Error('Android verification manifest provenance is invalid');
  }
  const artifactSuffix = `/${artifactFileName}`;
  const reportEntry = findManifestEntry(
    manifest,
    '/verification-report.txt',
    'full verifier report',
  );
  const artifactEntry = findManifestEntry(
    manifest,
    artifactSuffix,
    'Android artifact',
  );
  const evidenceEntry = findManifestEntry(
    manifest,
    '/final-evidence.json',
    'signed distribution evidence',
  );
  if (
    reportEntry.sha256 !== sha256(verificationReportBytes) ||
    reportEntry.bytes !== verificationReportBytes.byteLength ||
    artifactEntry.sha256 !== artifactSha256 ||
    evidenceEntry.sha256 !== sha256(evidenceBytes) ||
    evidenceEntry.bytes !== evidenceBytes.byteLength
  ) {
    throw new Error('Android verification manifest bytes do not match');
  }
  return {
    schemaVersion: 1,
    product: 'birthday-autopilot-android-release-verification',
    status: 'verified',
    sourceRevision: source.sourceRevision,
    authorityPublicKeySpkiSha256: authority.publicKeySpkiSha256,
    validUntil: approval.validUntil,
    tier,
    channel: approval.channel,
    fullVerifierKind: artifactMode,
    applicationId: approval.applicationId,
    versionCode: approval.versionCode,
    versionName: approval.versionName,
    artifactFileName,
    artifactSha256,
    artifactSigningCertificateSha256,
    installedSigningCertificateSha256: normalizeDigest(
      approval.signingCertificateSha256,
    ),
    firebase,
    signedEvidenceSha256: sha256(evidenceBytes),
    fullVerificationReportSha256: sha256(verificationReportBytes),
    verificationManifestSha256: sha256(verificationManifestBytes),
  };
}

const parseArguments = argv => {
  if (argv.length % 2 !== 0)
    throw new Error('arguments must be --name value pairs');
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!flag?.startsWith('--') || value === undefined) {
      throw new Error('arguments must be --name value pairs');
    }
    const name = flag.slice(2);
    if (!CLI_KEYS.has(name) || values.has(name)) {
      throw new Error(`unsupported or duplicate argument ${flag}`);
    }
    values.set(name, value);
  }
  const missing = [...CLI_KEYS].filter(name => !values.has(name));
  if (missing.length > 0)
    throw new Error(`missing arguments: ${missing.join(', ')}`);
  return values;
};

const run = () => {
  const args = parseArguments(process.argv.slice(2));
  const evidence = readStableFile(args.get('evidence'), 64 * 1024, 'evidence');
  const signature = readStableFile(args.get('signature'), 64, 'signature');
  const publicKey = readStableFile(
    args.get('public-key'),
    8 * 1024,
    'authority public key',
  );
  const pin = readStableFile(AUTHORITY_PIN, 1024, 'authority pin');
  const artifact = observeStablePath(
    args.get('artifact'),
    1024 * 1024 * 1024,
    'Android artifact',
  );
  const artifactMode = args.get('artifact-mode');
  const expectedExtension = artifactMode === 'play-aab' ? '.aab' : '.apk';
  if (!artifact.canonical.endsWith(expectedExtension)) {
    throw new Error('Android artifact type/path is invalid');
  }
  const verificationReport = readStableFile(
    args.get('verification-report'),
    1024 * 1024,
    'full verifier report',
  );
  const verificationManifest = readStableFile(
    args.get('verification-manifest'),
    4 * 1024 * 1024,
    'verification manifest',
  );
  const source = inspectCleanGitSource(PROJECT_ROOT, pin.bytes);
  const artifactSha256 = sha256File(artifact.canonical);
  artifact.canonicalMetadata = stableMetadata(
    lstatSync(artifact.canonical, { bigint: true }),
  );
  assertUnchanged(artifact, 'Android artifact');
  const report = createAndroidReleaseVerificationReport({
    evidenceBytes: evidence.bytes,
    signatureBytes: signature.bytes,
    publicKeyBytes: publicKey.bytes,
    pinDocument: parseJson(pin.bytes, 'authority pin'),
    evidenceRoot: args.get('evidence-root'),
    tier: args.get('tier'),
    artifactMode,
    artifactFileName: path.basename(artifact.canonical),
    artifactSha256,
    verificationReportBytes: verificationReport.bytes,
    verificationManifestBytes: verificationManifest.bytes,
    source,
  });
  if (!SHA256.test(report.artifactSha256)) {
    throw new Error('Android artifact digest is invalid');
  }
  for (const [record, label] of [
    [evidence, 'evidence'],
    [signature, 'signature'],
    [publicKey, 'authority public key'],
    [pin, 'authority pin'],
    [artifact, 'Android artifact'],
    [verificationReport, 'full verifier report'],
    [verificationManifest, 'verification manifest'],
  ]) {
    assertUnchanged(record, label);
  }
  const finalSource = inspectCleanGitSource(PROJECT_ROOT, pin.bytes);
  if (
    finalSource.errors.length > 0 ||
    finalSource.sourceRevision !== source.sourceRevision
  ) {
    throw new Error('Git source changed during Android report creation');
  }
  writeFileSync(args.get('output'), `${stableJson(report)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  });
  process.stdout.write(
    `PASS Android structured release report kind=${report.fullVerifierKind} source=${report.sourceRevision}\n`,
  );
};

if (
  process.argv[1] !== undefined &&
  realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))
) {
  try {
    run();
  } catch (error) {
    process.stderr.write(
      `FAIL ${error instanceof Error ? error.message : String(error)}\n`,
    );
    process.exitCode = 1;
  }
}
