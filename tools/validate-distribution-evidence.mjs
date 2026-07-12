#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import process from 'node:process';
import { pathToFileURL } from 'node:url';

const CHANNELS = new Set([
  'google-play',
  'managed-enterprise',
  'controlled-direct',
]);
const TOP_LEVEL_KEYS = new Set(['schemaVersion', 'approvals']);
const APPROVAL_KEYS = new Set([
  'tier',
  'status',
  'applicationId',
  'versionCode',
  'channel',
  'installerPackage',
  'signingCertificateSha256',
  'minimumCertifiedApi',
  'maximumCertifiedApi',
  'smsPermissionPolicyApproved',
  'installerAllowlistVerified',
  'physicalSmsMatrixPassed',
  'telephonyStatePermissionApproved',
  'appCheckEnforced',
  'privacyMaterialsApproved',
  'playUploadApproved',
  'approvalReference',
  'certifiedDeviceMatrixReference',
  'carrierMatrixReference',
  'legalReviewReference',
  'approvedAt',
  'validUntil',
  'launchCountries',
]);
const REQUIRED_TRUE_FIELDS = [
  'smsPermissionPolicyApproved',
  'installerAllowlistVerified',
  'physicalSmsMatrixPassed',
  'telephonyStatePermissionApproved',
  'appCheckEnforced',
  'privacyMaterialsApproved',
];
const MAXIMUM_EVIDENCE_BYTES = 64 * 1024;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const SAFE_REFERENCE = /^[A-Za-z0-9][A-Za-z0-9 ._:/#-]{0,255}$/u;

const fail = message => {
  process.stderr.write(`FAIL ${message}\n`);
  process.exitCode = 1;
};

const parseArguments = argv => {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!flag?.startsWith('--') || value === undefined) {
      throw new Error('arguments must be --name value pairs');
    }
    values.set(flag.slice(2), value);
  }
  return values;
};

const normalizeCertificate = value =>
  value.replaceAll(':', '').trim().toLowerCase();

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const assertExactKeys = (value, allowed, label, errors) => {
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) errors.push(`${label} has unsupported field ${key}`);
  }
};

const isSafeReference = value =>
  typeof value === 'string' &&
  value === value.trim() &&
  SAFE_REFERENCE.test(value);

const parseInstant = (value, label, errors) => {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) {
    errors.push(`${label} must be an RFC 3339 instant`);
    return null;
  }
  const epochMillis = Date.parse(value);
  if (!Number.isFinite(epochMillis)) {
    errors.push(`${label} must be a UTC RFC 3339 instant`);
    return null;
  }
  return epochMillis;
};

export function validateDistributionEvidence(
  document,
  expected,
  now = Date.now(),
) {
  const errors = [];
  if (!isObject(document)) return { errors: ['evidence must be an object'] };
  assertExactKeys(document, TOP_LEVEL_KEYS, 'evidence', errors);
  if (document.schemaVersion !== 1) errors.push('schemaVersion must be 1');
  if (
    !Array.isArray(document.approvals) ||
    document.approvals.length < 1 ||
    document.approvals.length > 2
  ) {
    return { errors: [...errors, 'approvals must contain one or two entries'] };
  }

  const tiers = document.approvals.map(entry =>
    isObject(entry) ? entry.tier : undefined,
  );
  if (new Set(tiers).size !== tiers.length)
    errors.push('approval tiers must be unique');

  const approval = document.approvals.find(
    candidate => isObject(candidate) && candidate.tier === expected.tier,
  );
  if (!approval)
    return { errors: [...errors, `${expected.tier} approval is missing`] };
  assertExactKeys(approval, APPROVAL_KEYS, `${expected.tier} approval`, errors);

  if (approval.status !== 'approved') errors.push('status is not approved');
  if (approval.applicationId !== expected.applicationId) {
    errors.push('applicationId does not match the artifact');
  }
  if (approval.versionCode !== expected.versionCode) {
    errors.push('versionCode does not match the artifact');
  }
  if (!CHANNELS.has(approval.channel)) errors.push('channel is unsupported');
  if (
    typeof approval.installerPackage !== 'string' ||
    !/^[A-Za-z0-9_.]{3,255}$/.test(approval.installerPackage)
  ) {
    errors.push('installerPackage is invalid');
  }
  if (
    approval.channel === 'google-play' &&
    approval.installerPackage !== 'com.android.vending'
  ) {
    errors.push('Google Play evidence must name com.android.vending');
  }

  if (
    typeof approval.signingCertificateSha256 !== 'string' ||
    !/^[0-9a-f]{64}$/u.test(
      normalizeCertificate(approval.signingCertificateSha256),
    ) ||
    normalizeCertificate(approval.signingCertificateSha256) !==
      normalizeCertificate(expected.signingCertificateSha256)
  ) {
    errors.push('signing certificate does not match the artifact');
  }
  if (
    !Number.isInteger(approval.minimumCertifiedApi) ||
    !Number.isInteger(approval.maximumCertifiedApi) ||
    approval.minimumCertifiedApi !== expected.minimumSupportedApi ||
    approval.maximumCertifiedApi < expected.targetApi ||
    approval.maximumCertifiedApi > 37 ||
    approval.maximumCertifiedApi < approval.minimumCertifiedApi
  ) {
    errors.push('certified API range is invalid');
  }
  for (const field of REQUIRED_TRUE_FIELDS) {
    if (approval[field] !== true) errors.push(`${field} is not verified`);
  }
  if (
    approval.channel === 'google-play' &&
    approval.playUploadApproved !== true
  ) {
    errors.push('Play upload approval is not verified');
  }
  if (!isSafeReference(approval.approvalReference)) {
    errors.push('approvalReference is invalid');
  }
  for (const field of [
    'certifiedDeviceMatrixReference',
    'carrierMatrixReference',
    'legalReviewReference',
  ]) {
    if (!isSafeReference(approval[field])) {
      errors.push(`${field} is invalid`);
    }
  }
  if (
    !Array.isArray(approval.launchCountries) ||
    !approval.launchCountries.includes('IN') ||
    approval.launchCountries.some(country => !/^[A-Z]{2}$/.test(country)) ||
    new Set(approval.launchCountries).size !== approval.launchCountries.length
  ) {
    errors.push('launchCountries must be unique ISO codes and include IN');
  }

  const approvedAt = parseInstant(approval.approvedAt, 'approvedAt', errors);
  const validUntil = parseInstant(approval.validUntil, 'validUntil', errors);
  if (approvedAt !== null && approvedAt > now + 5 * 60 * 1000) {
    errors.push('approvedAt is in the future');
  }
  if (validUntil !== null && validUntil <= now)
    errors.push('approval is expired');
  if (approvedAt !== null && validUntil !== null && validUntil <= approvedAt) {
    errors.push('approval validity is inverted');
  }
  if (
    approvedAt !== null &&
    validUntil !== null &&
    validUntil > approvedAt + 366 * 24 * 60 * 60 * 1000
  ) {
    errors.push('approval validity exceeds one year');
  }

  return errors.length === 0 ? { approval, errors } : { errors };
}

function run() {
  let argumentsByName;
  try {
    argumentsByName = parseArguments(process.argv.slice(2));
  } catch (error) {
    fail(error instanceof Error ? error.message : 'invalid arguments');
    return;
  }
  const required = ['file', 'tier', 'package', 'version-code', 'certificate'];
  const missing = required.filter(name => !argumentsByName.get(name));
  if (missing.length > 0) {
    fail(`missing arguments: ${missing.join(', ')}`);
    return;
  }

  let document;
  try {
    const rawDocument = readFileSync(argumentsByName.get('file'));
    if (
      rawDocument.byteLength === 0 ||
      rawDocument.byteLength > MAXIMUM_EVIDENCE_BYTES
    ) {
      throw new Error('invalid evidence size');
    }
    document = JSON.parse(rawDocument.toString('utf8'));
  } catch {
    fail('evidence file is missing, unreadable, or malformed');
    return;
  }
  const versionCode = Number(argumentsByName.get('version-code'));
  if (!Number.isSafeInteger(versionCode) || versionCode < 1) {
    fail('artifact version code is invalid');
    return;
  }
  const nowArgument = argumentsByName.get('now');
  const now = nowArgument === undefined ? Date.now() : Date.parse(nowArgument);
  if (!Number.isFinite(now)) {
    fail('validation time is invalid');
    return;
  }

  const result = validateDistributionEvidence(
    document,
    {
      tier: argumentsByName.get('tier'),
      applicationId: argumentsByName.get('package'),
      versionCode,
      signingCertificateSha256: argumentsByName.get('certificate'),
      minimumSupportedApi: 29,
      targetApi: 36,
    },
    now,
  );
  if (result.errors.length > 0) {
    for (const error of result.errors) fail(error);
    return;
  }
  process.stdout.write(
    `PASS tier=${result.approval.tier} channel=${result.approval.channel} installer=${result.approval.installerPackage}\n`,
  );
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  run();
}
