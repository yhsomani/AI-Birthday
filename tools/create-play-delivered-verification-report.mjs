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
  validateDistributionEvidence,
  verifyDistributionEvidenceAuthority,
} from './validate-distribution-evidence.mjs';

const PROJECT_ROOT = fileURLToPath(new URL('../', import.meta.url));
const AUTHORITY_PIN = path.join(
  PROJECT_ROOT,
  'tools/distribution-authority-pin.json',
);
const CLI_KEYS = new Set([
  'evidence',
  'signature',
  'public-key',
  'evidence-root',
  'tier',
  'observation',
  'output',
]);
const SHA1 = /^[0-9a-f]{40}$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const VERSION = /^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$/u;
const PACKAGE_PATH = /^\/data\/[A-Za-z0-9._~+/=-]{1,1000}\.apk$/u;
const FILE_NAME = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,251}\.apk$/u;
const OBSERVATION_KEYS = [
  'schemaVersion',
  'observedAt',
  'physicalDevice',
  'deviceSerialSha256',
  'deviceApi',
  'installerOfRecord',
  'applicationId',
  'versionCode',
  'versionName',
  'uploadAabSha256',
  'deliveredBaseApkSha256',
  'installedSigningCertificateSha1',
  'installedSigningCertificateSha256',
  'installedArtifacts',
];
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const MAXIMUM_CLOCK_SKEW_MS = 5 * 60 * 1_000;
const MAXIMUM_DEVICE_PROOF_MS = 24 * 60 * 60 * 1_000;
const ARTIFACT_KEYS = [
  'role',
  'packagePath',
  'fileName',
  'bytes',
  'sha256',
  'signingCertificateSha1',
  'signingCertificateSha256',
];

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
const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);
const exactKeys = (value, keys) =>
  isObject(value) &&
  Object.keys(value).length === keys.length &&
  Object.keys(value).every(key => keys.includes(key));
const normalizeDigest = value =>
  typeof value === 'string'
    ? value.replaceAll(':', '').trim().toLowerCase()
    : '';

const metadata = value => ({
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

const readStableFile = (file, maximumBytes, label) => {
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
  const descriptor = openSync(
    canonical,
    // File-descriptor flags intentionally form a bit mask.
    // eslint-disable-next-line no-bitwise
    constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
  );
  try {
    const opened = fstatSync(descriptor, { bigint: true });
    if (!sameMetadata(metadata(before), metadata(opened))) {
      throw new Error(`${label} changed before it was read`);
    }
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    if (
      BigInt(bytes.byteLength) !== opened.size ||
      !sameMetadata(metadata(opened), metadata(after)) ||
      realpathSync(requested) !== canonical
    ) {
      throw new Error(`${label} changed while it was read`);
    }
    return { bytes, requested, canonical, observed: metadata(after) };
  } finally {
    closeSync(descriptor);
  }
};

const assertUnchanged = (record, label) => {
  const current = lstatSync(record.requested, { bigint: true });
  if (
    current.isSymbolicLink() ||
    !sameMetadata(record.observed, metadata(current)) ||
    realpathSync(record.requested) !== record.canonical
  ) {
    throw new Error(`${label} changed during report creation`);
  }
};

const parseJson = (bytes, label) => {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is malformed JSON`);
  }
};

export function createPlayDeliveredVerificationReport({
  evidenceBytes,
  signatureBytes,
  publicKeyBytes,
  pinDocument,
  evidenceRoot,
  tier,
  observation,
  source,
  nowMs = Date.now(),
}) {
  if (!['lab', 'prod'].includes(tier)) {
    throw new Error('Play delivery tier is invalid');
  }
  if (!exactKeys(observation, OBSERVATION_KEYS)) {
    throw new Error('Play device observation fields do not match the contract');
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

  const signingSha1 = normalizeDigest(
    observation.installedSigningCertificateSha1,
  );
  const signingSha256 = normalizeDigest(
    observation.installedSigningCertificateSha256,
  );
  const observedAtMs =
    typeof observation.observedAt === 'string' &&
    UTC_INSTANT.test(observation.observedAt)
      ? Date.parse(observation.observedAt)
      : Number.NaN;
  const approvalValidUntilMs = Date.parse(approval.validUntil);
  if (
    observation.schemaVersion !== 1 ||
    !Number.isFinite(observedAtMs) ||
    observedAtMs > nowMs + MAXIMUM_CLOCK_SKEW_MS ||
    observedAtMs < nowMs - MAXIMUM_CLOCK_SKEW_MS ||
    observation.physicalDevice !== true ||
    !SHA256.test(observation.deviceSerialSha256) ||
    /^0{64}$/u.test(observation.deviceSerialSha256) ||
    !Number.isSafeInteger(observation.deviceApi) ||
    observation.deviceApi < 29 ||
    observation.deviceApi > approval.maximumCertifiedApi ||
    observation.installerOfRecord !== 'com.android.vending' ||
    observation.applicationId !== approval.applicationId ||
    observation.versionCode !== approval.versionCode ||
    observation.versionName !== approval.versionName ||
    !VERSION.test(observation.versionName) ||
    observation.uploadAabSha256 !== approval.artifactAabSha256 ||
    observation.deliveredBaseApkSha256 !== approval.artifactApkSha256 ||
    !SHA1.test(signingSha1) ||
    !SHA256.test(signingSha256) ||
    signingSha256 !== normalizeDigest(approval.signingCertificateSha256) ||
    !Array.isArray(observation.installedArtifacts) ||
    observation.installedArtifacts.length < 1 ||
    observation.installedArtifacts.length > 256
  ) {
    throw new Error('Play device observation does not match approved release');
  }

  const seenPaths = new Set();
  let baseCount = 0;
  const installedArtifacts = observation.installedArtifacts.map(artifact => {
    if (
      !exactKeys(artifact, ARTIFACT_KEYS) ||
      !['base', 'split'].includes(artifact.role) ||
      !PACKAGE_PATH.test(artifact.packagePath) ||
      artifact.packagePath.includes('/../') ||
      !FILE_NAME.test(artifact.fileName) ||
      path.posix.basename(artifact.packagePath) !== artifact.fileName ||
      !Number.isSafeInteger(artifact.bytes) ||
      artifact.bytes <= 0 ||
      !SHA256.test(artifact.sha256) ||
      normalizeDigest(artifact.signingCertificateSha1) !== signingSha1 ||
      normalizeDigest(artifact.signingCertificateSha256) !== signingSha256 ||
      seenPaths.has(artifact.packagePath)
    ) {
      throw new Error('installed Play artifact inventory is invalid');
    }
    seenPaths.add(artifact.packagePath);
    if (artifact.role === 'base') {
      baseCount += 1;
      if (
        artifact.fileName !== 'base.apk' ||
        artifact.sha256 !== observation.deliveredBaseApkSha256
      ) {
        throw new Error('installed Play base APK does not match approval');
      }
    } else if (artifact.fileName === 'base.apk') {
      throw new Error('installed Play split inventory contains a second base');
    }
    return {
      ...artifact,
      signingCertificateSha1: signingSha1,
      signingCertificateSha256: signingSha256,
    };
  });
  if (baseCount !== 1) {
    throw new Error('installed Play artifact inventory must contain one base');
  }
  installedArtifacts.sort((left, right) =>
    left.packagePath.localeCompare(right.packagePath, 'en'),
  );

  const validation = validateDistributionEvidence(
    evidence,
    {
      tier,
      applicationId: observation.applicationId,
      versionCode: observation.versionCode,
      versionName: observation.versionName,
      artifactMode: 'play-delivered-apk',
      artifactSigningCertificateSha256: signingSha256,
      artifactSha256: observation.deliveredBaseApkSha256,
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

  const validUntil = new Date(
    Math.min(approvalValidUntilMs, observedAtMs + MAXIMUM_DEVICE_PROOF_MS),
  ).toISOString();

  return {
    schemaVersion: 1,
    product: 'birthday-autopilot-android-play-delivery-verification',
    status: 'verified',
    sourceRevision: source.sourceRevision,
    authorityPublicKeySpkiSha256: authority.publicKeySpkiSha256,
    observedAt: observation.observedAt,
    validUntil,
    tier,
    channel: approval.channel,
    physicalDevice: true,
    deviceSerialSha256: observation.deviceSerialSha256,
    deviceApi: observation.deviceApi,
    installerOfRecord: observation.installerOfRecord,
    applicationId: observation.applicationId,
    versionCode: observation.versionCode,
    versionName: observation.versionName,
    uploadAabSha256: observation.uploadAabSha256,
    deliveredBaseApkSha256: observation.deliveredBaseApkSha256,
    installedSigningCertificateSha1: signingSha1,
    installedSigningCertificateSha256: signingSha256,
    installedArtifacts,
    signedEvidenceSha256: sha256(evidenceBytes),
  };
}

const parseArguments = argv => {
  if (argv.length % 2 !== 0) throw new Error('arguments must be pairs');
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    const name = flag?.startsWith('--') ? flag.slice(2) : '';
    if (!CLI_KEYS.has(name) || value === undefined || values.has(name)) {
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
    'public key',
  );
  const pin = readStableFile(AUTHORITY_PIN, 1024, 'authority pin');
  const observation = readStableFile(
    args.get('observation'),
    4 * 1024 * 1024,
    'Play device observation',
  );
  const source = inspectCleanGitSource(PROJECT_ROOT, pin.bytes);
  const report = createPlayDeliveredVerificationReport({
    evidenceBytes: evidence.bytes,
    signatureBytes: signature.bytes,
    publicKeyBytes: publicKey.bytes,
    pinDocument: parseJson(pin.bytes, 'authority pin'),
    evidenceRoot: args.get('evidence-root'),
    tier: args.get('tier'),
    observation: parseJson(observation.bytes, 'Play device observation'),
    source,
  });
  for (const [record, label] of [
    [evidence, 'evidence'],
    [signature, 'signature'],
    [publicKey, 'public key'],
    [pin, 'authority pin'],
    [observation, 'Play device observation'],
  ]) {
    assertUnchanged(record, label);
  }
  const finalSource = inspectCleanGitSource(PROJECT_ROOT, pin.bytes);
  if (
    finalSource.errors.length > 0 ||
    finalSource.sourceRevision !== source.sourceRevision
  ) {
    throw new Error('Git source changed during Play report creation');
  }
  writeFileSync(args.get('output'), `${stableJson(report)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  });
  process.stdout.write(
    `PASS Play-delivered structured report source=${report.sourceRevision} artifacts=${report.installedArtifacts.length}\n`,
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
