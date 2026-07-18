#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  closeSync,
  constants,
  fstatSync,
  lstatSync,
  openSync,
  readFileSync,
  readSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { verifyDistributionEvidenceAuthority } from './validate-distribution-evidence.mjs';
import { calculateApprovalScopeSha256 } from './validate-store-submission-evidence.mjs';

const PROJECT_ROOT = fileURLToPath(new URL('../', import.meta.url));
const AUTHORITY_PIN = path.join(
  PROJECT_ROOT,
  'tools/distribution-authority-pin.json',
);

const REQUIRED_ENVIRONMENT = Object.freeze({
  BIRTHDAY_STORE_SUBMISSION_FILE: '--file',
  BIRTHDAY_STORE_ANDROID_AAB: '--android-artifact',
  BIRTHDAY_STORE_IOS_IPA: '--ios-artifact',
  BIRTHDAY_STORE_ASSET_ROOT: '--asset-root',
  BIRTHDAY_STORE_EVIDENCE_ROOT: '--evidence-root',
  BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH: '--hosting-config',
});

const SIGNED_INPUTS = Object.freeze({
  BIRTHDAY_STORE_EVIDENCE_SIGNATURE: null,
  BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY: null,
});

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

const stableMetadata = metadata => ({
  dev: metadata.dev,
  ino: metadata.ino,
  mode: metadata.mode,
  nlink: metadata.nlink,
  size: metadata.size,
  mtimeNs: metadata.mtimeNs,
  ctimeNs: metadata.ctimeNs,
});

const sameMetadata = (left, right) =>
  Object.keys(left).every(key => left[key] === right[key]);

const readBoundedRegularFile = (file, maximumBytes, label) => {
  const before = lstatSync(file, { bigint: true });
  if (
    !before.isFile() ||
    before.isSymbolicLink() ||
    before.nlink !== 1n ||
    before.size <= 0n ||
    before.size > BigInt(maximumBytes)
  ) {
    throw new Error(`${label} must be a bounded, non-linked regular file`);
  }
  const bytes = readFileSync(file);
  const after = lstatSync(file, { bigint: true });
  if (
    BigInt(bytes.byteLength) !== before.size ||
    !sameMetadata(stableMetadata(before), stableMetadata(after))
  ) {
    throw new Error(`${label} changed while it was read`);
  }
  return { bytes, metadata: stableMetadata(after) };
};

const hashBoundedRegularFile = (file, maximumBytes, label) => {
  const before = lstatSync(file, { bigint: true });
  if (
    before.isSymbolicLink() ||
    !before.isFile() ||
    before.nlink !== 1n ||
    before.size <= 0n ||
    before.size > BigInt(maximumBytes)
  ) {
    throw new Error(`${label} must be a bounded, non-linked regular file`);
  }
  const descriptor = openSync(
    file,
    // File-descriptor flags intentionally form a bit mask.
    // eslint-disable-next-line no-bitwise
    constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
  );
  try {
    const opened = fstatSync(descriptor, { bigint: true });
    if (!sameMetadata(stableMetadata(before), stableMetadata(opened))) {
      throw new Error(`${label} changed before it was hashed`);
    }
    const digest = createHash('sha256');
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    let bytesRead;
    do {
      bytesRead = readSync(descriptor, buffer, 0, buffer.byteLength, null);
      if (bytesRead > 0) digest.update(buffer.subarray(0, bytesRead));
    } while (bytesRead > 0);
    const after = fstatSync(descriptor, { bigint: true });
    const pathAfter = lstatSync(file, { bigint: true });
    if (
      !sameMetadata(stableMetadata(opened), stableMetadata(after)) ||
      !sameMetadata(stableMetadata(opened), stableMetadata(pathAfter))
    ) {
      throw new Error(`${label} changed while it was hashed`);
    }
    return digest.digest('hex');
  } finally {
    closeSync(descriptor);
  }
};

const missing = [
  ...Object.keys(REQUIRED_ENVIRONMENT),
  ...Object.keys(SIGNED_INPUTS),
].filter(
  name =>
    typeof process.env[name] !== 'string' || process.env[name].trim() === '',
);
if (missing.length > 0) {
  process.stderr.write(
    `FAIL store release evidence is not configured; missing ${missing.join(
      ', ',
    )}\n`,
  );
  process.exitCode = 1;
} else {
  try {
    const evidence = readBoundedRegularFile(
      process.env.BIRTHDAY_STORE_SUBMISSION_FILE,
      2 * 1024 * 1024,
      'store release evidence',
    );
    const signature = readBoundedRegularFile(
      process.env.BIRTHDAY_STORE_EVIDENCE_SIGNATURE,
      64,
      'store release signature',
    ).bytes;
    const publicKey = readBoundedRegularFile(
      process.env.BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY,
      8 * 1024,
      'distribution authority public key',
    ).bytes;
    const pin = readBoundedRegularFile(
      AUTHORITY_PIN,
      1024,
      'distribution authority pin',
    ).bytes;
    let pinDocument;
    try {
      pinDocument = JSON.parse(pin.toString('utf8'));
    } catch {
      throw new Error('distribution authority pin is malformed');
    }
    const authority = verifyDistributionEvidenceAuthority({
      rawEvidence: evidence.bytes,
      detachedSignature: signature,
      publicKeyBytes: publicKey,
      pinDocument,
    });
    if (authority.errors.length > 0) {
      throw new Error(authority.errors.join('; '));
    }

    const validator = fileURLToPath(
      new URL('./validate-store-submission-evidence.mjs', import.meta.url),
    );
    const argumentsList = [validator, '--mode', 'release'];
    for (const [name, flag] of Object.entries(REQUIRED_ENVIRONMENT)) {
      argumentsList.push(flag, process.env[name]);
    }
    const result = spawnSync(process.execPath, argumentsList, {
      env: process.env,
      stdio: 'inherit',
    });
    if (result.error !== undefined || result.status !== 0) {
      throw new Error('store submission validator failed');
    }
    const afterValidation = stableMetadata(
      lstatSync(process.env.BIRTHDAY_STORE_SUBMISSION_FILE, { bigint: true }),
    );
    if (!sameMetadata(evidence.metadata, afterValidation)) {
      throw new Error('store release evidence changed during validation');
    }
    const reportPath = process.env.BIRTHDAY_STORE_VERIFICATION_REPORT;
    if (typeof reportPath === 'string' && reportPath.trim() !== '') {
      const document = JSON.parse(evidence.bytes.toString('utf8'));
      const androidArtifactSha256 = hashBoundedRegularFile(
        process.env.BIRTHDAY_STORE_ANDROID_AAB,
        1024 * 1024 * 1024,
        'store Android artifact',
      );
      const iosArtifactSha256 = hashBoundedRegularFile(
        process.env.BIRTHDAY_STORE_IOS_IPA,
        1024 * 1024 * 1024,
        'store iOS artifact',
      );
      const hostingConfig = readBoundedRegularFile(
        process.env.BIRTHDAY_HOSTING_RELEASE_CONFIG_PATH,
        2 * 1024 * 1024,
        'Hosting release config',
      ).bytes;
      const validUntil = [
        document.validUntil,
        ...document.approvals.map(approval => approval.validUntil),
      ].reduce((earliest, candidate) =>
        Date.parse(candidate) < Date.parse(earliest) ? candidate : earliest,
      );
      const report = {
        schemaVersion: 1,
        product: 'birthday-autopilot-store-release-verification',
        status: 'verified',
        sourceRevision: document.sourceRevision,
        evidenceSha256: sha256(evidence.bytes),
        approvalScopeSha256: calculateApprovalScopeSha256(document),
        authorityPublicKeySpkiSha256: authority.publicKeySpkiSha256,
        validUntil,
        releaseCoordinates: document.releaseCoordinates,
        publicIdentity: {
          publicBaseUrl: document.publicIdentity.publicSiteBaseUrl,
          privacyUrl: document.publicIdentity.privacyUrl,
          termsUrl: document.publicIdentity.termsUrl,
          deletionUrl: document.publicIdentity.deletionUrl,
        },
        artifactDigests: {
          android: androidArtifactSha256,
          ios: iosArtifactSha256,
        },
        hostingConfigSha256: sha256(hostingConfig),
      };
      writeFileSync(reportPath, `${stableJson(report)}\n`, {
        encoding: 'utf8',
        flag: 'wx',
        mode: 0o600,
      });
    }
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error
          ? error.message
          : 'store release authority verification failed'
      }\n`,
    );
    process.exitCode = 1;
  }
}
