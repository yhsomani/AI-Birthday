import { readFile } from 'node:fs/promises';

const unsafeHostnames = new Set([
  'example.com',
  'example.org',
  'example.net',
  'localhost',
  '127.0.0.1',
]);

function record(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value
    : null;
}

function nonBlank(value, field) {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`${field} must be a non-empty string`);
  }
  return value.trim();
}

function approvedReference(value, field) {
  const reference = nonBlank(value, field);
  if (
    reference.length < 8 ||
    /(?:todo|tbd|placeholder|pending)/iu.test(reference)
  ) {
    throw new Error(`${field} must identify completed review evidence`);
  }
  return reference;
}

function httpsUrl(value, field) {
  const text = nonBlank(value, field);
  let url;
  try {
    url = new URL(text);
  } catch {
    throw new Error(`${field} must be an absolute HTTPS URL`);
  }
  if (
    url.protocol !== 'https:' ||
    url.username !== '' ||
    url.password !== '' ||
    unsafeHostnames.has(url.hostname) ||
    url.hostname.endsWith('.example') ||
    url.hostname.endsWith('.invalid') ||
    url.hostname.endsWith('.test') ||
    url.hostname.endsWith('.localhost')
  ) {
    throw new Error(`${field} must be a provisioned public HTTPS URL`);
  }
  url.hash = '';
  return url.toString();
}

function httpsOrigin(value, field) {
  const text = httpsUrl(value, field);
  const url = new URL(text);
  if (url.pathname !== '/' || url.search !== '' || url.hash !== '') {
    throw new Error(`${field} must be an HTTPS origin without a path or query`);
  }
  return url.toString();
}

function isoDate(value, field) {
  const date = nonBlank(value, field);
  if (!/^\d{4}-\d{2}-\d{2}$/u.test(date)) {
    throw new Error(`${field} must use YYYY-MM-DD`);
  }
  const parsed = new Date(`${date}T00:00:00.000Z`);
  if (
    Number.isNaN(parsed.valueOf()) ||
    parsed.toISOString().slice(0, 10) !== date
  ) {
    throw new Error(`${field} must be a real calendar date`);
  }
  return date;
}

export function parseReleaseConfig(input) {
  const value = record(input);
  if (value === null || value.schemaVersion !== 1) {
    throw new Error('release config schemaVersion must be exactly 1');
  }

  const developerDisplayName = nonBlank(
    value.developerDisplayName,
    'developerDisplayName',
  );
  if (
    /(?:todo|tbd|placeholder|birthday autopilot developer)/iu.test(
      developerDisplayName,
    )
  ) {
    throw new Error(
      'developerDisplayName must be the approved public identity',
    );
  }

  const publicBaseUrl = httpsOrigin(value.publicBaseUrl, 'publicBaseUrl');
  const supportUrl = httpsUrl(value.supportUrl, 'supportUrl');
  const publicOrigin = new URL(publicBaseUrl).origin;
  if (new URL(supportUrl).origin === publicOrigin) {
    throw new Error(
      'supportUrl must lead to the separately provisioned identity-verified support workflow',
    );
  }

  const recaptchaEnterpriseSiteKey = nonBlank(
    value.recaptchaEnterpriseSiteKey,
    'recaptchaEnterpriseSiteKey',
  );
  if (
    recaptchaEnterpriseSiteKey.length < 20 ||
    /(?:todo|tbd|placeholder|replace|example)/iu.test(
      recaptchaEnterpriseSiteKey,
    )
  ) {
    throw new Error('recaptchaEnterpriseSiteKey is not provisioned');
  }

  approvedReference(value.legalApprovalReference, 'legalApprovalReference');
  approvedReference(value.privacyApprovalReference, 'privacyApprovalReference');
  approvedReference(
    value.hindiCopyApprovalReference,
    'hindiCopyApprovalReference',
  );
  approvedReference(
    value.adminDeletionRunbookReference,
    'adminDeletionRunbookReference',
  );

  if (value.verifiedAdminDeletionWorkflowTested !== true) {
    throw new Error('verifiedAdminDeletionWorkflowTested must be true');
  }
  if (value.productionFirebaseDeletionSagaTested !== true) {
    throw new Error('productionFirebaseDeletionSagaTested must be true');
  }

  return Object.freeze({
    schemaVersion: 1,
    publicBaseUrl,
    developerDisplayName,
    supportUrl,
    recaptchaEnterpriseSiteKey,
    privacyEffectiveDate: isoDate(
      value.privacyEffectiveDate,
      'privacyEffectiveDate',
    ),
    termsEffectiveDate: isoDate(value.termsEffectiveDate, 'termsEffectiveDate'),
    functionsRegion: 'asia-south1',
  });
}

export async function readReleaseConfig(path) {
  const source = await readFile(path, 'utf8');
  return parseReleaseConfig(JSON.parse(source));
}
