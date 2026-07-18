#!/usr/bin/env node

import { realpathSync, writeFileSync } from 'node:fs';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  readStableRegularFile,
  sha256,
  stableJson,
} from './hosting-deployment-artifact.mjs';
import {
  validateFirebaseWebConfig,
  validateHostingProviderOrigin,
  validateObservedJsonBytes,
} from './hosting-provider-observation.mjs';

const REVISION = /^[0-9a-f]{40}$/u;
const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const PROJECT_NUMBER = /^[1-9][0-9]{5,19}$/u;
const SITE_ID = /^[a-z0-9][a-z0-9-]{4,62}$/u;
const WEB_APP_ID = /^1:[1-9][0-9]{5,19}:web:[0-9a-f]{8,64}$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const UNSIGNED_INTEGER = /^(?:0|[1-9][0-9]{0,19})$/u;
const POSITIVE_INTEGER = /^[1-9][0-9]{0,19}$/u;
const BUCKET_NAME = /^[a-z0-9][a-z0-9-]{4,61}[a-z0-9]$/u;
const SERVICE_ACCOUNT =
  /^[a-z][a-z0-9-]{2,62}@[a-z][a-z0-9-]{4,28}[a-z0-9]\.iam\.gserviceaccount\.com$/u;
const WIF_PROVIDER =
  /^projects\/[1-9][0-9]{5,19}\/locations\/global\/workloadIdentityPools\/[a-z0-9-]{4,32}\/providers\/[a-z0-9-]{4,32}$/u;
const RFC3339 =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const MAXIMUM_INPUT_BYTES = 64 * 1024 * 1024;
export const HOSTING_LIVE_OBSERVATION_MAX_AGE_MS = 15 * 60 * 1_000;
export const HOSTING_ADMISSION_RETENTION_SECONDS = 900;
export const HOSTING_ADMISSION_OBJECT_PREFIX =
  'hosting-production-change-freezes/';

const exactKeys = (value, expected, label) => {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be an object`);
  }
  const keys = Object.keys(value);
  if (
    keys.length !== expected.length ||
    keys.some(key => !expected.includes(key))
  ) {
    throw new Error(`${label} fields do not match the exact contract`);
  }
};

const validInstant = value =>
  typeof value === 'string' &&
  RFC3339.test(value) &&
  Number.isFinite(Date.parse(value));

const resource = (value, expectedPrefix, label) => {
  if (
    typeof value !== 'string' ||
    !value.startsWith(expectedPrefix) ||
    !/^[A-Za-z0-9._~-]{1,128}$/u.test(value.slice(expectedPrefix.length))
  ) {
    throw new Error(`${label} resource name is invalid`);
  }
  return { name: value, id: value.slice(expectedPrefix.length) };
};

const expectedAdmissionObjectName = ({
  siteId,
  sourceRevision,
  runId,
  runAttempt,
}) =>
  `${HOSTING_ADMISSION_OBJECT_PREFIX}${siteId}/${sourceRevision}/${runId}/${runAttempt}.json`;

const validateAdmissionBucket = ({
  bucket,
  bucketBytes,
  applicationProjectNumber,
}) => {
  validateObservedJsonBytes(
    bucket,
    bucketBytes,
    'Hosting admission bucket observation',
  );
  const lifecycleRule = bucket.lifecycle?.rule;
  if (
    !BUCKET_NAME.test(bucket.name ?? '') ||
    !PROJECT_NUMBER.test(String(bucket.projectNumber ?? '')) ||
    String(bucket.projectNumber) === applicationProjectNumber ||
    !POSITIVE_INTEGER.test(String(bucket.metageneration ?? '')) ||
    String(bucket.retentionPolicy?.retentionPeriod ?? '') !==
      String(HOSTING_ADMISSION_RETENTION_SECONDS) ||
    bucket.retentionPolicy?.isLocked !== true ||
    !validInstant(bucket.retentionPolicy?.effectiveTime) ||
    bucket.iamConfiguration?.publicAccessPrevention !== 'enforced' ||
    bucket.iamConfiguration?.uniformBucketLevelAccess?.enabled !== true ||
    bucket.versioning?.enabled === true ||
    String(bucket.softDeletePolicy?.retentionDurationSeconds ?? '0') !== '0' ||
    !Array.isArray(lifecycleRule) ||
    lifecycleRule.length !== 1 ||
    lifecycleRule[0]?.action?.type !== 'Delete' ||
    lifecycleRule[0]?.condition?.age !== 1 ||
    !Array.isArray(lifecycleRule[0]?.condition?.matchesPrefix) ||
    lifecycleRule[0].condition.matchesPrefix.length !== 1 ||
    lifecycleRule[0].condition.matchesPrefix[0] !==
      HOSTING_ADMISSION_OBJECT_PREFIX
  ) {
    throw new Error(
      'Hosting admission bucket is not a private, external, locked 900-second release-control bucket',
    );
  }
  return bucket;
};

const validateAdmissionObject = ({
  object,
  objectBytes,
  bucket,
  contentBytes,
  capturedAt,
  validUntil,
  siteId,
  sourceRevision,
  builder,
}) => {
  validateObservedJsonBytes(
    object,
    objectBytes,
    'Hosting admission object observation',
  );
  const expectedName = expectedAdmissionObjectName({
    siteId,
    sourceRevision,
    runId: builder.runId,
    runAttempt: builder.runAttempt,
  });
  const timeCreatedMs = Date.parse(object.timeCreated);
  const retentionExpirationMs = Date.parse(object.retentionExpirationTime);
  if (
    object.bucket !== bucket.name ||
    object.name !== expectedName ||
    !POSITIVE_INTEGER.test(String(object.generation ?? '')) ||
    !POSITIVE_INTEGER.test(String(object.metageneration ?? '')) ||
    object.contentType !== 'application/json' ||
    String(object.size ?? '') !== String(contentBytes.byteLength) ||
    !validInstant(object.timeCreated) ||
    !validInstant(object.retentionExpirationTime) ||
    retentionExpirationMs - timeCreatedMs !==
      HOSTING_ADMISSION_RETENTION_SECONDS * 1_000 ||
    timeCreatedMs < Date.parse(capturedAt) ||
    Date.parse(validUntil) <= timeCreatedMs ||
    Date.parse(validUntil) > retentionExpirationMs ||
    object.temporaryHold === true ||
    object.eventBasedHold === true
  ) {
    throw new Error(
      'Hosting admission object is not the exact retained release-control lease',
    );
  }
  return object;
};

export function createHostingCurrentLiveObservation({
  sourceRevision,
  projectId,
  projectNumber,
  admissionSecurityProjectId,
  webAppId,
  siteId,
  publicBaseUrl,
  capturedAt,
  siteObservation,
  siteObservationBytes,
  originObservation,
  originObservationBytes,
  webConfig,
  webConfigBytes,
  liveChannel,
  liveChannelBytes,
  version,
  versionBytes,
  admissionBucket,
  admissionBucketBytes,
  admissionObject,
  admissionObjectBytes,
  admissionContentBytes,
  executionIdentity,
  builder,
}) {
  if (
    !REVISION.test(sourceRevision) ||
    !PROJECT_ID.test(admissionSecurityProjectId ?? '') ||
    admissionSecurityProjectId === projectId ||
    !validInstant(capturedAt) ||
    liveChannel === null ||
    typeof liveChannel !== 'object' ||
    version === null ||
    typeof version !== 'object'
  ) {
    throw new Error('Hosting current-live observation identity is invalid');
  }
  const providerOrigin = validateHostingProviderOrigin({
    projectId,
    siteId,
    publicBaseUrl,
    siteObservation,
    siteObservationBytes,
    originObservation,
    originObservationBytes,
  });
  const firebaseWebConfig = validateFirebaseWebConfig({
    projectId,
    projectNumber,
    webAppId,
    webConfig,
    webConfigBytes,
  });
  const release = liveChannel.release;
  validateObservedJsonBytes(
    liveChannel,
    liveChannelBytes,
    'Firebase Hosting live channel observation',
  );
  validateObservedJsonBytes(
    version,
    versionBytes,
    'Firebase Hosting version observation',
  );
  if (
    liveChannel.name !== `sites/${siteId}/channels/live` ||
    release === null ||
    typeof release !== 'object' ||
    release.type !== 'DEPLOY' ||
    !validInstant(release.releaseTime)
  ) {
    throw new Error('Firebase Hosting live channel is not a DEPLOY release');
  }
  const releasePrefix = [
    `sites/${siteId}/releases/`,
    `sites/${siteId}/channels/live/releases/`,
  ].find(prefix => release.name?.startsWith(prefix));
  if (releasePrefix === undefined) {
    throw new Error('live release resource name is invalid');
  }
  const releaseResource = resource(release.name, releasePrefix, 'live release');
  const versionName =
    typeof release.version === 'string'
      ? release.version
      : release.version?.name;
  const versionResource = resource(
    version.name,
    `sites/${siteId}/versions/`,
    'live version',
  );
  if (
    versionName !== versionResource.name ||
    version.status !== 'FINALIZED' ||
    !validInstant(version.createTime) ||
    !validInstant(version.finalizeTime) ||
    Date.parse(version.createTime) > Date.parse(version.finalizeTime) ||
    Date.parse(version.finalizeTime) > Date.parse(release.releaseTime) ||
    !UNSIGNED_INTEGER.test(String(version.fileCount ?? '')) ||
    Number(version.fileCount) <= 0 ||
    !UNSIGNED_INTEGER.test(String(version.versionBytes ?? '')) ||
    Number(version.versionBytes) <= 0
  ) {
    throw new Error('Firebase Hosting live version is not finalized');
  }
  exactKeys(
    builder,
    ['repository', 'workflowPath', 'runId', 'runAttempt'],
    'Hosting current-live builder',
  );
  if (
    builder.repository !== 'yhsomani/AI-Birthday' ||
    builder.workflowPath !==
      '.github/workflows/hosting-current-live-observation.yml' ||
    !/^[1-9][0-9]{0,19}$/u.test(builder.runId ?? '') ||
    !/^[1-9][0-9]{0,9}$/u.test(builder.runAttempt ?? '')
  ) {
    throw new Error('Hosting current-live builder identity is invalid');
  }
  const validUntil = new Date(
    Date.parse(capturedAt) + HOSTING_LIVE_OBSERVATION_MAX_AGE_MS,
  ).toISOString();
  const admissionLeaseValue = stableJson({
    schemaVersion: 1,
    siteId,
    sourceRevision,
    runId: builder.runId,
    runAttempt: builder.runAttempt,
    validUntil,
  });
  if (
    !Buffer.isBuffer(admissionContentBytes) ||
    admissionContentBytes.byteLength === 0 ||
    admissionContentBytes.toString('utf8') !== admissionLeaseValue
  ) {
    throw new Error('Hosting admission lease content is not canonical');
  }
  validateAdmissionBucket({
    bucket: admissionBucket,
    bucketBytes: admissionBucketBytes,
    applicationProjectNumber: projectNumber,
  });
  validateAdmissionObject({
    object: admissionObject,
    objectBytes: admissionObjectBytes,
    bucket: admissionBucket,
    contentBytes: admissionContentBytes,
    capturedAt,
    validUntil,
    siteId,
    sourceRevision,
    builder,
  });
  exactKeys(
    executionIdentity,
    [
      'serviceAccount',
      'workloadIdentityProvider',
      'repositoryId',
      'repositoryOwnerId',
    ],
    'Hosting current-live execution identity',
  );
  if (
    !SERVICE_ACCOUNT.test(executionIdentity.serviceAccount ?? '') ||
    !executionIdentity.serviceAccount.endsWith(
      `@${projectId}.iam.gserviceaccount.com`,
    ) ||
    !POSITIVE_INTEGER.test(executionIdentity.repositoryId ?? '') ||
    !POSITIVE_INTEGER.test(executionIdentity.repositoryOwnerId ?? '') ||
    !WIF_PROVIDER.test(executionIdentity.workloadIdentityProvider ?? '') ||
    !executionIdentity.workloadIdentityProvider.startsWith(
      `projects/${projectNumber}/`,
    )
  ) {
    throw new Error('Hosting current-live execution identity is invalid');
  }
  return {
    schemaVersion: 1,
    product: 'birthday-autopilot-hosting-current-live-observation',
    status: 'current',
    sourceRevision,
    capturedAt,
    validUntil,
    projectId,
    projectNumber,
    webAppId,
    siteId,
    publicBaseUrl,
    provider: {
      origin: providerOrigin,
      firebaseWebConfig,
      executionIdentity,
    },
    live: {
      channelName: liveChannel.name,
      channelObservationSha256: sha256(liveChannelBytes),
      releaseName: releaseResource.name,
      releaseId: releaseResource.id,
      releaseType: release.type,
      releaseTime: release.releaseTime,
      versionName: versionResource.name,
      versionId: versionResource.id,
      versionStatus: version.status,
      versionCreateTime: version.createTime,
      versionFinalizeTime: version.finalizeTime,
      versionObservationSha256: sha256(versionBytes),
      fileCount: String(version.fileCount),
      versionBytes: String(version.versionBytes),
    },
    admissionLease: {
      securityProjectId: admissionSecurityProjectId,
      securityProjectNumber: String(admissionBucket.projectNumber),
      bucketName: admissionBucket.name,
      bucketMetageneration: String(admissionBucket.metageneration),
      objectName: admissionObject.name,
      objectGeneration: String(admissionObject.generation),
      objectMetageneration: String(admissionObject.metageneration),
      objectTimeCreated: admissionObject.timeCreated,
      objectRetentionExpirationTime: admissionObject.retentionExpirationTime,
      retentionSeconds: HOSTING_ADMISSION_RETENTION_SECONDS,
      lifecycleDeleteAgeDays: 1,
      value: admissionLeaseValue,
      contentSha256: sha256(admissionContentBytes),
      bucketObservationSha256: sha256(admissionBucketBytes),
      objectObservationSha256: sha256(admissionObjectBytes),
    },
    builder,
  };
}

export function verifyHostingCurrentLiveObservation(
  report,
  nowMs = Date.now(),
) {
  exactKeys(
    report,
    [
      'schemaVersion',
      'product',
      'status',
      'sourceRevision',
      'capturedAt',
      'validUntil',
      'projectId',
      'projectNumber',
      'webAppId',
      'siteId',
      'publicBaseUrl',
      'provider',
      'live',
      'admissionLease',
      'builder',
    ],
    'Hosting current-live observation',
  );
  exactKeys(
    report.provider,
    ['origin', 'firebaseWebConfig', 'executionIdentity'],
    'provider',
  );
  exactKeys(
    report.provider.origin,
    [
      'kind',
      'hostname',
      'siteResourceName',
      'originResourceName',
      'hostState',
      'ownershipState',
      'deleteTimeAbsent',
      'redirectTargetAbsent',
      'issuesAbsent',
      'siteObservationSha256',
      'originObservationSha256',
    ],
    'provider origin',
  );
  exactKeys(
    report.provider.firebaseWebConfig,
    ['projectId', 'projectNumber', 'webAppId', 'observationSha256'],
    'provider Firebase web config',
  );
  exactKeys(
    report.provider.executionIdentity,
    [
      'serviceAccount',
      'workloadIdentityProvider',
      'repositoryId',
      'repositoryOwnerId',
    ],
    'provider execution identity',
  );
  exactKeys(
    report.live,
    [
      'channelName',
      'channelObservationSha256',
      'releaseName',
      'releaseId',
      'releaseType',
      'releaseTime',
      'versionName',
      'versionId',
      'versionStatus',
      'versionCreateTime',
      'versionFinalizeTime',
      'versionObservationSha256',
      'fileCount',
      'versionBytes',
    ],
    'current live state',
  );
  exactKeys(
    report.builder,
    ['repository', 'workflowPath', 'runId', 'runAttempt'],
    'current-live builder',
  );
  exactKeys(
    report.admissionLease,
    [
      'securityProjectNumber',
      'securityProjectId',
      'bucketName',
      'bucketMetageneration',
      'objectName',
      'objectGeneration',
      'objectMetageneration',
      'objectTimeCreated',
      'objectRetentionExpirationTime',
      'retentionSeconds',
      'lifecycleDeleteAgeDays',
      'value',
      'contentSha256',
      'bucketObservationSha256',
      'objectObservationSha256',
    ],
    'current-live admission lease',
  );
  let publicUrl;
  try {
    publicUrl = new URL(report.publicBaseUrl);
  } catch {
    throw new Error('Hosting current-live origin is invalid');
  }
  const capturedAtMs = Date.parse(report.capturedAt);
  const validUntilMs = Date.parse(report.validUntil);
  const defaultHosts = new Set([
    `${report.siteId}.web.app`,
    `${report.siteId}.firebaseapp.com`,
  ]);
  if (
    report.schemaVersion !== 1 ||
    report.product !== 'birthday-autopilot-hosting-current-live-observation' ||
    report.status !== 'current' ||
    !REVISION.test(report.sourceRevision ?? '') ||
    !PROJECT_ID.test(report.projectId ?? '') ||
    !PROJECT_NUMBER.test(report.projectNumber ?? '') ||
    !WEB_APP_ID.test(report.webAppId ?? '') ||
    !report.webAppId.startsWith(`1:${report.projectNumber}:web:`) ||
    !SITE_ID.test(report.siteId ?? '') ||
    !validInstant(report.capturedAt) ||
    !validInstant(report.validUntil) ||
    validUntilMs - capturedAtMs !== HOSTING_LIVE_OBSERVATION_MAX_AGE_MS ||
    capturedAtMs > nowMs + 5 * 60 * 1_000 ||
    nowMs >= validUntilMs ||
    publicUrl.protocol !== 'https:' ||
    publicUrl.username !== '' ||
    publicUrl.password !== '' ||
    publicUrl.pathname !== '/' ||
    publicUrl.search !== '' ||
    publicUrl.hash !== '' ||
    report.live.channelName !== `sites/${report.siteId}/channels/live` ||
    report.live.releaseType !== 'DEPLOY' ||
    report.live.versionStatus !== 'FINALIZED' ||
    ![
      `sites/${report.siteId}/releases/${report.live.releaseId}`,
      `sites/${report.siteId}/channels/live/releases/${report.live.releaseId}`,
    ].includes(report.live.releaseName) ||
    report.live.versionName !==
      `sites/${report.siteId}/versions/${report.live.versionId}` ||
    !validInstant(report.live.releaseTime) ||
    !validInstant(report.live.versionCreateTime) ||
    !validInstant(report.live.versionFinalizeTime) ||
    Date.parse(report.live.versionCreateTime) >
      Date.parse(report.live.versionFinalizeTime) ||
    Date.parse(report.live.versionFinalizeTime) >
      Date.parse(report.live.releaseTime) ||
    !UNSIGNED_INTEGER.test(report.live.fileCount ?? '') ||
    Number(report.live.fileCount) <= 0 ||
    !UNSIGNED_INTEGER.test(report.live.versionBytes ?? '') ||
    Number(report.live.versionBytes) <= 0 ||
    report.provider.origin.hostname !== publicUrl.hostname ||
    report.provider.origin.siteResourceName !==
      `projects/${report.projectId}/sites/${report.siteId}` ||
    !['firebase-default-domain', 'firebase-custom-domain'].includes(
      report.provider.origin.kind,
    ) ||
    (report.provider.origin.kind === 'firebase-default-domain' &&
      (!defaultHosts.has(publicUrl.hostname) ||
        report.provider.origin.originResourceName !==
          report.provider.origin.siteResourceName ||
        report.provider.origin.hostState !== null ||
        report.provider.origin.ownershipState !== null)) ||
    (report.provider.origin.kind === 'firebase-custom-domain' &&
      (defaultHosts.has(publicUrl.hostname) ||
        report.provider.origin.originResourceName !==
          `projects/${report.projectId}/sites/${report.siteId}/customDomains/${publicUrl.hostname}` ||
        report.provider.origin.hostState !== 'HOST_ACTIVE' ||
        report.provider.origin.ownershipState !== 'OWNERSHIP_ACTIVE')) ||
    report.provider.origin.deleteTimeAbsent !== true ||
    report.provider.origin.redirectTargetAbsent !== true ||
    report.provider.origin.issuesAbsent !== true ||
    report.provider.firebaseWebConfig.projectId !== report.projectId ||
    report.provider.firebaseWebConfig.projectNumber !== report.projectNumber ||
    report.provider.firebaseWebConfig.webAppId !== report.webAppId ||
    !SERVICE_ACCOUNT.test(
      report.provider.executionIdentity.serviceAccount ?? '',
    ) ||
    !report.provider.executionIdentity.serviceAccount.endsWith(
      `@${report.projectId}.iam.gserviceaccount.com`,
    ) ||
    !POSITIVE_INTEGER.test(
      report.provider.executionIdentity.repositoryId ?? '',
    ) ||
    !POSITIVE_INTEGER.test(
      report.provider.executionIdentity.repositoryOwnerId ?? '',
    ) ||
    !WIF_PROVIDER.test(
      report.provider.executionIdentity.workloadIdentityProvider ?? '',
    ) ||
    !report.provider.executionIdentity.workloadIdentityProvider.startsWith(
      `projects/${report.projectNumber}/`,
    ) ||
    !PROJECT_NUMBER.test(report.admissionLease.securityProjectNumber ?? '') ||
    !PROJECT_ID.test(report.admissionLease.securityProjectId ?? '') ||
    report.admissionLease.securityProjectId === report.projectId ||
    report.admissionLease.securityProjectNumber === report.projectNumber ||
    !BUCKET_NAME.test(report.admissionLease.bucketName ?? '') ||
    !POSITIVE_INTEGER.test(report.admissionLease.bucketMetageneration ?? '') ||
    report.admissionLease.objectName !==
      expectedAdmissionObjectName({
        siteId: report.siteId,
        sourceRevision: report.sourceRevision,
        runId: report.builder.runId,
        runAttempt: report.builder.runAttempt,
      }) ||
    !POSITIVE_INTEGER.test(report.admissionLease.objectGeneration ?? '') ||
    !POSITIVE_INTEGER.test(report.admissionLease.objectMetageneration ?? '') ||
    !validInstant(report.admissionLease.objectTimeCreated) ||
    !validInstant(report.admissionLease.objectRetentionExpirationTime) ||
    Date.parse(report.admissionLease.objectTimeCreated) < capturedAtMs ||
    Date.parse(report.admissionLease.objectRetentionExpirationTime) -
      Date.parse(report.admissionLease.objectTimeCreated) !==
      HOSTING_ADMISSION_RETENTION_SECONDS * 1_000 ||
    validUntilMs <= Date.parse(report.admissionLease.objectTimeCreated) ||
    validUntilMs >
      Date.parse(report.admissionLease.objectRetentionExpirationTime) ||
    report.admissionLease.retentionSeconds !==
      HOSTING_ADMISSION_RETENTION_SECONDS ||
    report.admissionLease.lifecycleDeleteAgeDays !== 1 ||
    typeof report.admissionLease.value !== 'string' ||
    report.admissionLease.contentSha256 !==
      sha256(Buffer.from(report.admissionLease.value ?? '', 'utf8')) ||
    report.builder.repository !== 'yhsomani/AI-Birthday' ||
    report.builder.workflowPath !==
      '.github/workflows/hosting-current-live-observation.yml' ||
    !/^[1-9][0-9]{0,19}$/u.test(report.builder.runId ?? '') ||
    !/^[1-9][0-9]{0,9}$/u.test(report.builder.runAttempt ?? '')
  ) {
    throw new Error('Hosting current-live observation is invalid or stale');
  }
  let lease;
  try {
    lease = JSON.parse(report.admissionLease.value);
  } catch {
    throw new Error('Hosting current-live admission lease is invalid');
  }
  exactKeys(
    lease,
    [
      'schemaVersion',
      'runAttempt',
      'runId',
      'siteId',
      'sourceRevision',
      'validUntil',
    ],
    'current-live admission lease value',
  );
  if (
    stableJson(lease) !== report.admissionLease.value ||
    lease.schemaVersion !== 1 ||
    lease.siteId !== report.siteId ||
    lease.sourceRevision !== report.sourceRevision ||
    lease.runId !== report.builder.runId ||
    lease.runAttempt !== report.builder.runAttempt ||
    lease.validUntil !== report.validUntil
  ) {
    throw new Error('Hosting current-live admission lease is not report-bound');
  }
  for (const value of [
    report.provider.origin.siteObservationSha256,
    report.provider.origin.originObservationSha256,
    report.provider.firebaseWebConfig.observationSha256,
    report.live.channelObservationSha256,
    report.live.versionObservationSha256,
    report.admissionLease.contentSha256,
    report.admissionLease.bucketObservationSha256,
    report.admissionLease.objectObservationSha256,
  ]) {
    if (!SHA256.test(value ?? '')) {
      throw new Error('Hosting current-live observation digest is invalid');
    }
  }
  return report;
}

const parseJson = (bytes, label) => {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is not valid JSON`);
  }
};

const requiredArguments = [
  'source-revision',
  'project-id',
  'project-number',
  'admission-security-project-id',
  'web-app-id',
  'site-id',
  'public-base-url',
  'captured-at',
  'site-observation',
  'origin-observation',
  'firebase-web-config',
  'live-channel-observation',
  'version-observation',
  'admission-bucket-observation',
  'admission-object-observation',
  'admission-content',
  'output',
];

const parseArgs = argv => {
  if (argv.length !== requiredArguments.length * 2) {
    throw new Error('Hosting current-live arguments are incomplete');
  }
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index]?.slice(2);
    if (
      !argv[index]?.startsWith('--') ||
      !requiredArguments.includes(key) ||
      values.has(key)
    ) {
      throw new Error(`unsupported or duplicate argument ${argv[index]}`);
    }
    values.set(key, argv[index + 1]);
  }
  return values;
};

const main = () => {
  if (process.env.GITHUB_ACTIONS !== 'true') {
    throw new Error('current-live evidence is produced only by GitHub Actions');
  }
  const args = parseArgs(process.argv.slice(2));
  const read = key =>
    readStableRegularFile(args.get(key), MAXIMUM_INPUT_BYTES, `Hosting ${key}`);
  const bytes = Object.fromEntries(
    [
      'site-observation',
      'origin-observation',
      'firebase-web-config',
      'live-channel-observation',
      'version-observation',
      'admission-bucket-observation',
      'admission-object-observation',
    ].map(key => [key, read(key)]),
  );
  const admissionContentBytes = read('admission-content');
  const report = createHostingCurrentLiveObservation({
    sourceRevision: args.get('source-revision'),
    projectId: args.get('project-id'),
    projectNumber: args.get('project-number'),
    admissionSecurityProjectId: args.get('admission-security-project-id'),
    webAppId: args.get('web-app-id'),
    siteId: args.get('site-id'),
    publicBaseUrl: args.get('public-base-url'),
    capturedAt: args.get('captured-at'),
    siteObservation: parseJson(bytes['site-observation'], 'site observation'),
    siteObservationBytes: bytes['site-observation'],
    originObservation: parseJson(
      bytes['origin-observation'],
      'origin observation',
    ),
    originObservationBytes: bytes['origin-observation'],
    webConfig: parseJson(bytes['firebase-web-config'], 'Firebase web config'),
    webConfigBytes: bytes['firebase-web-config'],
    liveChannel: parseJson(
      bytes['live-channel-observation'],
      'live channel observation',
    ),
    liveChannelBytes: bytes['live-channel-observation'],
    version: parseJson(bytes['version-observation'], 'version observation'),
    versionBytes: bytes['version-observation'],
    admissionBucket: parseJson(
      bytes['admission-bucket-observation'],
      'admission bucket observation',
    ),
    admissionBucketBytes: bytes['admission-bucket-observation'],
    admissionObject: parseJson(
      bytes['admission-object-observation'],
      'admission object observation',
    ),
    admissionObjectBytes: bytes['admission-object-observation'],
    admissionContentBytes,
    executionIdentity: {
      serviceAccount: process.env.HOSTING_AUDIT_SERVICE_ACCOUNT,
      workloadIdentityProvider: process.env.HOSTING_AUDIT_WIF_PROVIDER,
      repositoryId: process.env.GITHUB_REPOSITORY_ID,
      repositoryOwnerId: process.env.GITHUB_REPOSITORY_OWNER_ID,
    },
    builder: {
      repository: process.env.GITHUB_REPOSITORY,
      workflowPath: '.github/workflows/hosting-current-live-observation.yml',
      runId: process.env.GITHUB_RUN_ID,
      runAttempt: process.env.GITHUB_RUN_ATTEMPT,
    },
  });
  verifyHostingCurrentLiveObservation(report, Date.parse(report.capturedAt));
  writeFileSync(args.get('output'), `${stableJson(report)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  });
};

if (
  process.argv[1] !== undefined &&
  realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))
) {
  try {
    main();
  } catch (error) {
    process.stderr.write(
      `FAIL ${error instanceof Error ? error.message : String(error)}\n`,
    );
    process.exitCode = 1;
  }
}
