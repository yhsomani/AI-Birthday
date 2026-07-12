#!/usr/bin/env node

import { readFileSync, realpathSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  inspectCleanGitSource,
  verifyDistributionEvidenceAuthority,
} from './validate-distribution-evidence.mjs';

const SCRIPT_FILE = fileURLToPath(import.meta.url);
const PROJECT_ROOT = resolve(dirname(SCRIPT_FILE), '..');
const PIN_FILE = resolve(PROJECT_ROOT, 'tools/distribution-authority-pin.json');
const MAXIMUM_EVIDENCE_BYTES = 64 * 1024;
const MAXIMUM_PUBLIC_KEY_BYTES = 8 * 1024;
const ED25519_SIGNATURE_BYTES = 64;
const SHA256 = /^[0-9a-f]{64}$/u;
const GIT_REVISION = /^[0-9a-f]{40}$/u;
const SAFE_VERSION = /^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$/u;
const SAFE_BUILD = /^[1-9][0-9]{0,17}$/u;
const SAFE_REFERENCE = /^[A-Za-z0-9][A-Za-z0-9 ._:/#-]{0,255}$/u;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const TOP_LEVEL_KEYS = new Set([
  'allPreviouslyDistributedBuildsIncluded',
  'approvalReference',
  'approvalSha256',
  'approvedAt',
  'bundleIdentifier',
  'candidateBuild',
  'candidateVersion',
  'minimumPreviouslyDistributedSchemaVersion',
  'product',
  'protectedStoreSchemaVersion',
  'releaseHistory',
  'schema1EverDistributed',
  'schemaVersion',
  'sourceRevision',
  'validUntil',
]);
const HISTORY_KEYS = new Set([
  'artifactSha256',
  'build',
  'distributedAt',
  'protectedStoreSchemaVersion',
  'version',
]);

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const exactKeys = (value, expected) =>
  Object.keys(value).length === expected.size &&
  Object.keys(value).every(key => expected.has(key));

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
  const canonical = new Date(parsed);
  if (
    canonical.getUTCFullYear() !== Number(value.slice(0, 4)) ||
    canonical.getUTCMonth() + 1 !== Number(value.slice(5, 7)) ||
    canonical.getUTCDate() !== Number(value.slice(8, 10)) ||
    canonical.getUTCHours() !== Number(value.slice(11, 13)) ||
    canonical.getUTCMinutes() !== Number(value.slice(14, 16)) ||
    canonical.getUTCSeconds() !== Number(value.slice(17, 19))
  ) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  return parsed;
};

export function validateIOSReleaseHistory(
  document,
  expected,
  now = Date.now(),
) {
  const errors = [];
  if (!isObject(document)) return { errors: ['evidence must be an object'] };
  if (!exactKeys(document, TOP_LEVEL_KEYS)) {
    errors.push('evidence fields do not match the exact schema');
  }
  if (document.schemaVersion !== 1) errors.push('schemaVersion must be 1');
  if (document.product !== 'birthday-autopilot-ios') {
    errors.push('product must be birthday-autopilot-ios');
  }
  if (document.bundleIdentifier !== expected.bundleIdentifier) {
    errors.push('bundleIdentifier does not match the candidate');
  }
  if (document.candidateVersion !== expected.version) {
    errors.push('candidateVersion does not match the candidate');
  }
  if (document.candidateBuild !== expected.build) {
    errors.push('candidateBuild does not match the candidate');
  }
  if (!SAFE_VERSION.test(document.candidateVersion ?? '')) {
    errors.push('candidateVersion is invalid');
  }
  if (!SAFE_BUILD.test(document.candidateBuild ?? '')) {
    errors.push('candidateBuild is invalid');
  }
  if (document.sourceRevision !== expected.sourceRevision) {
    errors.push('sourceRevision does not match the clean candidate');
  }
  if (!GIT_REVISION.test(document.sourceRevision ?? '')) {
    errors.push('sourceRevision is invalid');
  }
  if (
    document.protectedStoreSchemaVersion !== 2 ||
    expected.protectedStoreSchemaVersion !== 2
  ) {
    errors.push('the candidate protected-store schema must be exactly 2');
  }
  if (document.allPreviouslyDistributedBuildsIncluded !== true) {
    errors.push(
      'all previously distributed builds must be attested as included',
    );
  }
  if (document.schema1EverDistributed !== false) {
    errors.push('schema-1 distribution must be explicitly attested false');
  }
  if (!SAFE_REFERENCE.test(document.approvalReference ?? '')) {
    errors.push('approvalReference is invalid');
  }
  if (!SHA256.test(document.approvalSha256 ?? '')) {
    errors.push('approvalSha256 is invalid');
  }

  const history = document.releaseHistory;
  if (!Array.isArray(history) || history.length > 1_000) {
    errors.push('releaseHistory must be an array of at most 1000 prior builds');
  } else {
    const identities = new Set();
    let minimumSchema = null;
    for (const [index, entry] of history.entries()) {
      const label = `releaseHistory[${index}]`;
      if (!isObject(entry) || !exactKeys(entry, HISTORY_KEYS)) {
        errors.push(`${label} fields do not match the exact schema`);
        continue;
      }
      if (!SAFE_VERSION.test(entry.version ?? ''))
        errors.push(`${label}.version is invalid`);
      if (!SAFE_BUILD.test(entry.build ?? ''))
        errors.push(`${label}.build is invalid`);
      const identity = `${entry.version ?? ''}:${entry.build ?? ''}`;
      if (identities.has(identity))
        errors.push(`${label} duplicates a prior build`);
      identities.add(identity);
      if (!Number.isInteger(entry.protectedStoreSchemaVersion)) {
        errors.push(`${label}.protectedStoreSchemaVersion is invalid`);
      } else {
        minimumSchema = Math.min(
          minimumSchema ?? entry.protectedStoreSchemaVersion,
          entry.protectedStoreSchemaVersion,
        );
        if (entry.protectedStoreSchemaVersion < 2) {
          errors.push(`${label} proves a legacy migration is required`);
        }
      }
      if (!SHA256.test(entry.artifactSha256 ?? '')) {
        errors.push(`${label}.artifactSha256 is invalid`);
      }
      const distributedAt = parseInstant(
        entry.distributedAt,
        `${label}.distributedAt`,
        errors,
      );
      if (distributedAt !== null && distributedAt > now) {
        errors.push(`${label}.distributedAt is in the future`);
      }
    }
    const declaredMinimum = document.minimumPreviouslyDistributedSchemaVersion;
    if (history.length === 0 && declaredMinimum !== null) {
      errors.push(
        'minimumPreviouslyDistributedSchemaVersion must be null without prior builds',
      );
    }
    if (history.length > 0 && declaredMinimum !== minimumSchema) {
      errors.push(
        'minimumPreviouslyDistributedSchemaVersion does not match releaseHistory',
      );
    }
    if (minimumSchema !== null && minimumSchema < 2) {
      errors.push('prior distribution includes a schema older than 2');
    }
  }

  const approvedAt = parseInstant(document.approvedAt, 'approvedAt', errors);
  const validUntil = parseInstant(document.validUntil, 'validUntil', errors);
  if (approvedAt !== null && approvedAt > now + 5 * 60 * 1_000) {
    errors.push('approvedAt is in the future');
  }
  if (validUntil !== null && validUntil <= now)
    errors.push('approval is expired');
  if (approvedAt !== null && validUntil !== null) {
    if (validUntil <= approvedAt) errors.push('approval validity is inverted');
    if (validUntil > approvedAt + 366 * 24 * 60 * 60 * 1_000) {
      errors.push('approval validity exceeds one year');
    }
  }
  return { errors };
}

const readBounded = (path, maximumBytes, label) => {
  const value = readFileSync(path);
  if (value.byteLength === 0 || value.byteLength > maximumBytes) {
    throw new Error(`${label} has an invalid size`);
  }
  return value;
};

const fail = message => {
  process.stderr.write(`FAIL ${message}\n`);
  process.exitCode = 1;
};

const argumentsByName = argv => {
  const result = new Map();
  if (argv.length % 2 !== 0)
    throw new Error('arguments must be --name value pairs');
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (
      !flag?.startsWith('--') ||
      value === undefined ||
      result.has(flag.slice(2))
    ) {
      throw new Error('arguments must be unique --name value pairs');
    }
    result.set(flag.slice(2), value);
  }
  return result;
};

function run() {
  let values;
  try {
    values = argumentsByName(process.argv.slice(2));
  } catch (error) {
    fail(error instanceof Error ? error.message : 'invalid arguments');
    return;
  }
  const required = [
    'file',
    'signature',
    'public-key',
    'bundle',
    'version',
    'build',
  ];
  const missing = required.filter(name => !values.get(name));
  if (missing.length > 0) {
    fail(`missing arguments: ${missing.join(', ')}`);
    return;
  }

  let rawEvidence;
  let signature;
  let publicKey;
  let rawPin;
  let pin;
  try {
    rawEvidence = readBounded(
      values.get('file'),
      MAXIMUM_EVIDENCE_BYTES,
      'evidence',
    );
    signature = readBounded(
      values.get('signature'),
      ED25519_SIGNATURE_BYTES,
      'detached signature',
    );
    publicKey = readBounded(
      values.get('public-key'),
      MAXIMUM_PUBLIC_KEY_BYTES,
      'public key',
    );
    rawPin = readBounded(PIN_FILE, 1_024, 'authority pin');
    pin = JSON.parse(rawPin.toString('utf8'));
  } catch (error) {
    fail(
      error instanceof Error ? error.message : 'evidence inputs are unreadable',
    );
    return;
  }
  const authority = verifyDistributionEvidenceAuthority({
    rawEvidence,
    detachedSignature: signature,
    publicKeyBytes: publicKey,
    pinDocument: pin,
  });
  if (authority.errors.length > 0) {
    authority.errors.forEach(fail);
    return;
  }
  const source = inspectCleanGitSource(PROJECT_ROOT, rawPin);
  if (source.errors.length > 0) {
    source.errors.forEach(fail);
    return;
  }
  let document;
  try {
    document = JSON.parse(rawEvidence.toString('utf8'));
  } catch {
    fail('evidence is malformed JSON');
    return;
  }
  const result = validateIOSReleaseHistory(document, {
    bundleIdentifier: values.get('bundle'),
    version: values.get('version'),
    build: values.get('build'),
    protectedStoreSchemaVersion: 2,
    sourceRevision: source.sourceRevision,
  });
  if (result.errors.length > 0) {
    result.errors.forEach(fail);
    return;
  }
  process.stdout.write(
    `PASS iOS protected-store release history source=${source.sourceRevision}\n`,
  );
}

if (
  process.argv[1] &&
  realpathSync(SCRIPT_FILE) === realpathSync(process.argv[1])
) {
  run();
}
