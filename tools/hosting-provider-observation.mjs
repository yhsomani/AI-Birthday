import { createHash } from 'node:crypto';

const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const PROJECT_NUMBER = /^[1-9][0-9]{5,19}$/u;
const SITE_ID = /^[a-z0-9][a-z0-9-]{4,62}$/u;
const WEB_APP_ID = /^1:[1-9][0-9]{5,19}:web:[0-9a-f]{8,64}$/u;

export const digestProviderBytes = bytes =>
  createHash('sha256').update(bytes).digest('hex');

const object = (value, label) => {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be an object`);
  }
  return value;
};

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

export const validateObservedJsonBytes = (value, bytes, label) => {
  let parsed;
  try {
    parsed = JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} bytes are not valid JSON`);
  }
  if (stableJson(parsed) !== stableJson(value)) {
    throw new Error(`${label} bytes do not match the observed value`);
  }
};

const publicOrigin = value => {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error('Hosting public origin is invalid');
  }
  if (
    url.protocol !== 'https:' ||
    url.username !== '' ||
    url.password !== '' ||
    url.pathname !== '/' ||
    url.search !== '' ||
    url.hash !== '' ||
    url.hostname !== url.hostname.toLowerCase()
  ) {
    throw new Error('Hosting public origin is invalid');
  }
  return url;
};

export function validateHostingProviderOrigin({
  projectId,
  siteId,
  publicBaseUrl,
  siteObservation,
  siteObservationBytes,
  originObservation,
  originObservationBytes,
}) {
  if (!PROJECT_ID.test(projectId) || !SITE_ID.test(siteId)) {
    throw new Error('Hosting provider project/site identity is invalid');
  }
  const url = publicOrigin(publicBaseUrl);
  const site = object(siteObservation, 'Firebase Hosting site observation');
  validateObservedJsonBytes(
    site,
    siteObservationBytes,
    'Firebase Hosting site observation',
  );
  const expectedSiteName = `projects/${projectId}/sites/${siteId}`;
  if (site.name !== expectedSiteName) {
    throw new Error('Firebase Hosting site observation does not match target');
  }
  let defaultUrl;
  try {
    defaultUrl = new URL(site.defaultUrl);
  } catch {
    throw new Error('Firebase Hosting site default URL is invalid');
  }
  if (
    defaultUrl.protocol !== 'https:' ||
    defaultUrl.hostname !== `${siteId}.web.app`
  ) {
    throw new Error('Firebase Hosting site default URL does not match target');
  }

  const defaultHostnames = new Set([
    `${siteId}.web.app`,
    `${siteId}.firebaseapp.com`,
  ]);
  const isDefault = defaultHostnames.has(url.hostname);
  const origin = object(
    originObservation,
    'Firebase Hosting origin observation',
  );
  validateObservedJsonBytes(
    origin,
    originObservationBytes,
    'Firebase Hosting origin observation',
  );
  if (isDefault) {
    if (origin.name !== expectedSiteName) {
      throw new Error('Firebase default origin does not match Hosting site');
    }
  } else {
    const expectedCustomName = `${expectedSiteName}/customDomains/${url.hostname}`;
    if (
      origin.name !== expectedCustomName ||
      origin.hostState !== 'HOST_ACTIVE' ||
      origin.ownershipState !== 'OWNERSHIP_ACTIVE' ||
      (origin.deleteTime !== undefined && origin.deleteTime !== null) ||
      (origin.redirectTarget !== undefined && origin.redirectTarget !== '') ||
      (origin.issues !== undefined &&
        (!Array.isArray(origin.issues) || origin.issues.length !== 0))
    ) {
      throw new Error(
        'Firebase custom domain is mismatched, inactive, unowned, deleted, redirected, or has issues',
      );
    }
  }
  return {
    kind: isDefault ? 'firebase-default-domain' : 'firebase-custom-domain',
    hostname: url.hostname,
    siteResourceName: expectedSiteName,
    originResourceName: origin.name,
    hostState: isDefault ? null : origin.hostState,
    ownershipState: isDefault ? null : origin.ownershipState,
    deleteTimeAbsent:
      origin.deleteTime === undefined || origin.deleteTime === null,
    redirectTargetAbsent:
      origin.redirectTarget === undefined || origin.redirectTarget === '',
    issuesAbsent:
      origin.issues === undefined ||
      (Array.isArray(origin.issues) && origin.issues.length === 0),
    siteObservationSha256: digestProviderBytes(siteObservationBytes),
    originObservationSha256: digestProviderBytes(originObservationBytes),
  };
}

export function validateFirebaseWebConfig({
  projectId,
  projectNumber,
  webAppId,
  webConfig,
  webConfigBytes,
}) {
  if (
    !PROJECT_ID.test(projectId) ||
    !PROJECT_NUMBER.test(projectNumber) ||
    !WEB_APP_ID.test(webAppId) ||
    !webAppId.startsWith(`1:${projectNumber}:web:`)
  ) {
    throw new Error('expected Firebase web identity is invalid');
  }
  const config = object(webConfig, 'Firebase reserved web config');
  validateObservedJsonBytes(
    config,
    webConfigBytes,
    'Firebase reserved web config',
  );
  if (
    config.projectId !== projectId ||
    String(config.messagingSenderId) !== projectNumber ||
    config.appId !== webAppId
  ) {
    throw new Error(
      'Firebase reserved web config does not match project number/app identity',
    );
  }
  return {
    projectId,
    projectNumber,
    webAppId,
    observationSha256: digestProviderBytes(webConfigBytes),
  };
}
