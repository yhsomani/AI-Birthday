#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { lstatSync, readFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { verifyDistributionEvidenceAuthority } from './validate-distribution-evidence.mjs';

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
      process.exitCode = result.status ?? 1;
    }
    const afterValidation = stableMetadata(
      lstatSync(process.env.BIRTHDAY_STORE_SUBMISSION_FILE, { bigint: true }),
    );
    if (!sameMetadata(evidence.metadata, afterValidation)) {
      throw new Error('store release evidence changed during validation');
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
