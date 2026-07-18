#!/usr/bin/env node

import { lstatSync, readdirSync, realpathSync, writeFileSync } from 'node:fs';
import nodePath from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  readStableRegularFile,
  sha256,
  stableJson,
  verifyHostingDeploymentArtifact,
} from './hosting-deployment-artifact.mjs';
import {
  validateFirebaseWebConfig,
  validateHostingProviderOrigin,
} from './hosting-provider-observation.mjs';

const REVISION = /^[0-9a-f]{40}$/u;
const SHA256 = /^[0-9a-f]{64}$/u;
const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const PROJECT_NUMBER = /^[1-9][0-9]{5,19}$/u;
const WEB_APP_ID = /^1:[1-9][0-9]{5,19}:web:[0-9a-f]{8,64}$/u;
const SITE_ID = /^[a-z0-9][a-z0-9-]{4,62}$/u;
const BUCKET_NAME = /^[a-z0-9][a-z0-9-]{4,61}[a-z0-9]$/u;
const SERVICE_ACCOUNT =
  /^[a-z][a-z0-9-]{2,62}@[a-z][a-z0-9-]{4,28}[a-z0-9]\.iam\.gserviceaccount\.com$/u;
const WIF_PROVIDER =
  /^projects\/[1-9][0-9]{5,19}\/locations\/global\/workloadIdentityPools\/[a-z0-9-]{4,32}\/providers\/[a-z0-9-]{4,32}$/u;
const UNSIGNED_INTEGER = /^(?:0|[1-9][0-9]{0,19})$/u;
const POSITIVE_INTEGER = /^[1-9][0-9]{0,19}$/u;
const RFC3339 =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const MAXIMUM_INPUT_BYTES = 256 * 1024 * 1024;
const MANIFEST_KEYS = [
  'schemaVersion',
  'product',
  'artifactSchemaVersion',
  'artifactProduct',
  'artifactFileName',
  'artifactSha256',
  'artifactBytes',
  'manifestSha256',
  'sourceRevision',
  'projectId',
  'siteId',
  'publicBaseUrl',
  'firebaseCliVersion',
  'hostingSourceTreeSha256',
  'sourceFirebaseConfigSha256',
  'releaseConfigSha256',
  'deploymentConfigSha256',
  'publicTreeSha256',
  'files',
];

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

const parseJson = (bytes, label) => {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is not valid JSON`);
  }
};

const validInstant = value =>
  typeof value === 'string' &&
  RFC3339.test(value) &&
  Number.isFinite(Date.parse(value));

const resourceName = (value, siteId, kind) => {
  if (typeof value !== 'string') throw new Error(`${kind} name is invalid`);
  const pattern = new RegExp(
    `^sites/${siteId}/${kind}/([A-Za-z0-9._~-]{1,128})$`,
    'u',
  );
  const match = pattern.exec(value);
  if (match === null) throw new Error(`${kind} name is invalid`);
  return { name: value, id: match[1] };
};

const validateAdmissionCheckManifest = (
  manifest,
  manifestBytes,
  admissionCheckFiles,
  expectedPageCount,
  expectedObjectCount,
) => {
  if (!Array.isArray(manifest) || manifest.length < 3) {
    throw new Error('Hosting admission check manifest is incomplete');
  }
  if (
    !Array.isArray(admissionCheckFiles) ||
    admissionCheckFiles.length !== manifest.length
  ) {
    throw new Error('Hosting admission raw inventory is incomplete');
  }
  let previousPath = '';
  for (let index = 0; index < manifest.length; index += 1) {
    const entry = manifest[index];
    const retained = admissionCheckFiles[index];
    exactKeys(entry, ['path', 'bytes', 'sha256'], 'admission check entry');
    if (
      typeof entry.path !== 'string' ||
      entry.path.length === 0 ||
      entry.path.startsWith('/') ||
      entry.path.includes('/') ||
      entry.path.includes('..') ||
      entry.path <= previousPath ||
      !Number.isSafeInteger(entry.bytes) ||
      entry.bytes <= 0 ||
      !SHA256.test(entry.sha256 ?? '') ||
      retained?.path !== entry.path ||
      !Buffer.isBuffer(retained?.bytes) ||
      retained.bytes.byteLength !== entry.bytes ||
      sha256(retained.bytes) !== entry.sha256
    ) {
      throw new Error('Hosting admission check manifest is not canonical');
    }
    previousPath = entry.path;
  }
  const paths = manifest.map(entry => entry.path);
  const expectedPaths = [
    'bucket.json',
    'objects.json',
    ...Array.from(
      { length: expectedPageCount },
      (_, index) => `page-${index + 1}.json`,
    ),
    ...Array.from({ length: expectedObjectCount }, (_, index) => {
      const key = String(index + 1).padStart(6, '0');
      return [`object-${key}-metadata.json`, `object-${key}-content.json`];
    }).flat(),
  ].sort();
  if (
    paths.length !== expectedPaths.length ||
    paths.some((value, index) => value !== expectedPaths[index])
  ) {
    throw new Error('Hosting admission raw inventory counts are invalid');
  }
  return {
    sha256: sha256(manifestBytes),
    bytes: manifestBytes.byteLength,
    rawRootSha256: sha256(Buffer.from(stableJson(manifest), 'utf8')),
    rawRootBytes: manifest.reduce((sum, entry) => sum + entry.bytes, 0),
  };
};

const validateExecution = ({
  admissionPass,
  admissionPassBytes,
  admissionCheckManifest,
  admissionCheckManifestBytes,
  admissionCheckFiles,
  deployIdentity,
  applicationProjectId,
  applicationProjectNumber,
  expectedSourceRevision,
  expectedSiteId,
  builder,
}) => {
  exactKeys(
    admissionPass,
    [
      'schemaVersion',
      'product',
      'status',
      'applicationProjectId',
      'applicationProjectNumber',
      'sourceRevision',
      'siteId',
      'scannedPrefix',
      'repository',
      'repositoryId',
      'repositoryOwnerId',
      'workflowPath',
      'runId',
      'runAttempt',
      'buildArtifactId',
      'buildArtifactDigest',
      'securityProjectId',
      'securityProjectNumber',
      'bucketName',
      'bucketMetageneration',
      'retentionSeconds',
      'retentionLocked',
      'publicAccessPrevention',
      'uniformBucketLevelAccess',
      'versioningEnabled',
      'softDeleteEnabled',
      'lifecycleDeleteAgeDays',
      'lifecyclePrefix',
      'checkedAt',
      'pageCount',
      'objectCount',
      'checkManifestSha256',
      'reader',
    ],
    'Hosting admission PASS snapshot',
  );
  exactKeys(
    admissionPass.reader,
    ['serviceAccount', 'workloadIdentityProvider'],
    'Hosting admission reader',
  );
  exactKeys(
    deployIdentity,
    [
      'serviceAccount',
      'workloadIdentityProvider',
      'authenticatedAt',
      'buildArtifactId',
      'buildArtifactDigest',
      'admissionArtifactId',
      'admissionArtifactDigest',
    ],
    'Hosting deploy identity',
  );
  const checkManifestIdentity = validateAdmissionCheckManifest(
    admissionCheckManifest,
    admissionCheckManifestBytes,
    admissionCheckFiles,
    admissionPass.pageCount,
    admissionPass.objectCount,
  );
  if (
    admissionPass.schemaVersion !== 1 ||
    admissionPass.product !== 'birthday-autopilot-hosting-admission-pass' ||
    admissionPass.status !== 'passed' ||
    admissionPass.applicationProjectId !== applicationProjectId ||
    admissionPass.applicationProjectNumber !== applicationProjectNumber ||
    admissionPass.sourceRevision !== expectedSourceRevision ||
    admissionPass.siteId !== expectedSiteId ||
    admissionPass.scannedPrefix !==
      `hosting-production-change-freezes/${expectedSiteId}/` ||
    admissionPass.repository !== builder.repository ||
    !POSITIVE_INTEGER.test(admissionPass.repositoryId ?? '') ||
    !POSITIVE_INTEGER.test(admissionPass.repositoryOwnerId ?? '') ||
    admissionPass.workflowPath !== builder.workflowPath ||
    admissionPass.runId !== builder.runId ||
    admissionPass.runAttempt !== builder.runAttempt ||
    !POSITIVE_INTEGER.test(admissionPass.buildArtifactId ?? '') ||
    !SHA256.test(admissionPass.buildArtifactDigest ?? '') ||
    !PROJECT_ID.test(admissionPass.securityProjectId ?? '') ||
    admissionPass.securityProjectId === applicationProjectId ||
    !PROJECT_NUMBER.test(admissionPass.securityProjectNumber ?? '') ||
    admissionPass.securityProjectNumber === applicationProjectNumber ||
    !BUCKET_NAME.test(admissionPass.bucketName ?? '') ||
    !POSITIVE_INTEGER.test(admissionPass.bucketMetageneration ?? '') ||
    admissionPass.retentionSeconds !== 900 ||
    admissionPass.retentionLocked !== true ||
    admissionPass.publicAccessPrevention !== 'enforced' ||
    admissionPass.uniformBucketLevelAccess !== true ||
    admissionPass.versioningEnabled !== false ||
    admissionPass.softDeleteEnabled !== false ||
    admissionPass.lifecycleDeleteAgeDays !== 1 ||
    admissionPass.lifecyclePrefix !== 'hosting-production-change-freezes/' ||
    !validInstant(admissionPass.checkedAt) ||
    !Number.isSafeInteger(admissionPass.pageCount) ||
    admissionPass.pageCount <= 0 ||
    !Number.isSafeInteger(admissionPass.objectCount) ||
    admissionPass.objectCount < 0 ||
    admissionPass.checkManifestSha256 !== checkManifestIdentity.sha256 ||
    !SERVICE_ACCOUNT.test(admissionPass.reader.serviceAccount ?? '') ||
    !admissionPass.reader.serviceAccount.endsWith(
      `@${admissionPass.securityProjectId}.iam.gserviceaccount.com`,
    ) ||
    !WIF_PROVIDER.test(admissionPass.reader.workloadIdentityProvider ?? '') ||
    !admissionPass.reader.workloadIdentityProvider.startsWith(
      `projects/${admissionPass.securityProjectNumber}/`,
    ) ||
    !SERVICE_ACCOUNT.test(deployIdentity.serviceAccount ?? '') ||
    !deployIdentity.serviceAccount.endsWith(
      `@${applicationProjectId}.iam.gserviceaccount.com`,
    ) ||
    !WIF_PROVIDER.test(deployIdentity.workloadIdentityProvider ?? '') ||
    !deployIdentity.workloadIdentityProvider.startsWith(
      `projects/${applicationProjectNumber}/`,
    ) ||
    deployIdentity.serviceAccount === admissionPass.reader.serviceAccount ||
    deployIdentity.workloadIdentityProvider ===
      admissionPass.reader.workloadIdentityProvider ||
    !validInstant(deployIdentity.authenticatedAt) ||
    deployIdentity.buildArtifactId !== admissionPass.buildArtifactId ||
    deployIdentity.buildArtifactDigest !== admissionPass.buildArtifactDigest ||
    !POSITIVE_INTEGER.test(deployIdentity.admissionArtifactId ?? '') ||
    !SHA256.test(deployIdentity.admissionArtifactDigest ?? '') ||
    Date.parse(admissionPass.checkedAt) >
      Date.parse(deployIdentity.authenticatedAt) ||
    Date.parse(deployIdentity.authenticatedAt) >
      Date.parse(builder.deployStartedAt)
  ) {
    throw new Error(
      'Hosting admission PASS or executed deployment identities are invalid',
    );
  }
  return {
    admissionReader: admissionPass.reader,
    admissionCheck: {
      applicationProjectId: admissionPass.applicationProjectId,
      applicationProjectNumber: admissionPass.applicationProjectNumber,
      sourceRevision: admissionPass.sourceRevision,
      siteId: admissionPass.siteId,
      scannedPrefix: admissionPass.scannedPrefix,
      repository: admissionPass.repository,
      repositoryId: admissionPass.repositoryId,
      repositoryOwnerId: admissionPass.repositoryOwnerId,
      workflowPath: admissionPass.workflowPath,
      runId: admissionPass.runId,
      runAttempt: admissionPass.runAttempt,
      buildArtifactId: admissionPass.buildArtifactId,
      buildArtifactDigest: admissionPass.buildArtifactDigest,
      securityProjectId: admissionPass.securityProjectId,
      securityProjectNumber: admissionPass.securityProjectNumber,
      bucketName: admissionPass.bucketName,
      bucketMetageneration: admissionPass.bucketMetageneration,
      retentionSeconds: admissionPass.retentionSeconds,
      retentionLocked: admissionPass.retentionLocked,
      publicAccessPrevention: admissionPass.publicAccessPrevention,
      uniformBucketLevelAccess: admissionPass.uniformBucketLevelAccess,
      versioningEnabled: admissionPass.versioningEnabled,
      softDeleteEnabled: admissionPass.softDeleteEnabled,
      lifecycleDeleteAgeDays: admissionPass.lifecycleDeleteAgeDays,
      lifecyclePrefix: admissionPass.lifecyclePrefix,
      checkedAt: admissionPass.checkedAt,
      pageCount: admissionPass.pageCount,
      objectCount: admissionPass.objectCount,
      checkManifestSha256: admissionPass.checkManifestSha256,
      checkManifestBytes: checkManifestIdentity.bytes,
      rawRootSha256: checkManifestIdentity.rawRootSha256,
      rawRootBytes: checkManifestIdentity.rawRootBytes,
      snapshotSha256: sha256(admissionPassBytes),
    },
    deployer: deployIdentity,
  };
};

const manifestProjection = artifact => ({
  artifactSchemaVersion: artifact.schemaVersion,
  artifactProduct: artifact.product,
  sourceRevision: artifact.sourceRevision,
  projectId: artifact.projectId,
  siteId: artifact.siteId,
  publicBaseUrl: artifact.publicBaseUrl,
  firebaseCliVersion: artifact.firebaseCliVersion,
  hostingSourceTreeSha256: artifact.hostingSourceTreeSha256,
  sourceFirebaseConfigSha256: artifact.sourceFirebaseConfigSha256,
  releaseConfigSha256: artifact.releaseConfigSha256,
  deploymentConfigSha256: artifact.deploymentConfigSha256,
  publicTreeSha256: artifact.publicTreeSha256,
  files: artifact.files.map(({ path, bytes, sha256: fileSha256 }) => ({
    path,
    bytes,
    sha256: fileSha256,
  })),
});

export function verifyHostingDeploymentManifest(
  manifest,
  manifestBytes,
  artifact,
  artifactBytes,
) {
  exactKeys(manifest, MANIFEST_KEYS, 'Hosting deployment manifest');
  if (
    manifest.schemaVersion !== 1 ||
    manifest.product !== 'birthday-autopilot-hosting-deployment-manifest' ||
    manifest.artifactFileName !== 'hosting-deployment-artifact.json' ||
    manifest.artifactSha256 !== sha256(artifactBytes) ||
    manifest.artifactBytes !== artifactBytes.byteLength ||
    manifest.manifestSha256 !== artifact.manifestSha256
  ) {
    throw new Error('Hosting deployment manifest does not bind the artifact');
  }
  const expectedProjection = manifestProjection(artifact);
  for (const [key, expected] of Object.entries(expectedProjection)) {
    if (stableJson(manifest[key]) !== stableJson(expected)) {
      throw new Error(`Hosting deployment manifest ${key} differs`);
    }
  }
  return {
    sha256: sha256(manifestBytes),
    bytes: manifestBytes.byteLength,
  };
}

export function createHostingDeploymentProvenanceReport({
  artifact,
  artifactBytes,
  manifest,
  manifestBytes,
  deployResult,
  deployResultBytes,
  release,
  version,
  projectNumber,
  webAppId,
  siteObservation,
  siteObservationBytes,
  originObservation,
  originObservationBytes,
  webConfig,
  webConfigBytes,
  admissionPass,
  admissionPassBytes,
  admissionCheckManifest,
  admissionCheckManifestBytes,
  admissionCheckFiles,
  deployIdentity,
  builder,
}) {
  verifyHostingDeploymentArtifact(artifact);
  const manifestIdentity = verifyHostingDeploymentManifest(
    manifest,
    manifestBytes,
    artifact,
    artifactBytes,
  );
  if (
    deployResult === null ||
    typeof deployResult !== 'object' ||
    Array.isArray(deployResult) ||
    deployResult.status !== 'success' ||
    deployResult.result === null ||
    typeof deployResult.result !== 'object' ||
    Array.isArray(deployResult.result) ||
    Object.keys(deployResult.result).length !== 1 ||
    typeof deployResult.result.hosting !== 'string'
  ) {
    throw new Error('Firebase CLI did not report a successful deployment');
  }
  if (
    release === null ||
    typeof release !== 'object' ||
    Array.isArray(release) ||
    version === null ||
    typeof version !== 'object' ||
    Array.isArray(version)
  ) {
    throw new Error('Firebase Hosting release/version observation is invalid');
  }
  const releaseResource = resourceName(
    release.name,
    artifact.siteId,
    'releases',
  );
  const releaseVersionName =
    typeof release.version === 'string'
      ? release.version
      : release.version?.name;
  const versionResource = resourceName(
    version.name,
    artifact.siteId,
    'versions',
  );
  if (
    deployResult.result.hosting !== versionResource.name ||
    releaseVersionName !== versionResource.name ||
    release.type !== 'DEPLOY' ||
    version.status !== 'FINALIZED' ||
    !validInstant(release.releaseTime) ||
    !validInstant(version.createTime) ||
    !validInstant(version.finalizeTime) ||
    Date.parse(version.createTime) > Date.parse(version.finalizeTime) ||
    Date.parse(version.finalizeTime) > Date.parse(release.releaseTime) ||
    !UNSIGNED_INTEGER.test(String(version.fileCount ?? '')) ||
    Number(version.fileCount) <= 0 ||
    !UNSIGNED_INTEGER.test(String(version.versionBytes ?? '')) ||
    Number(version.versionBytes) <= 0
  ) {
    throw new Error(
      'Firebase Hosting release/version state is not a finalized deploy',
    );
  }
  exactKeys(
    builder,
    [
      'repository',
      'workflowPath',
      'runId',
      'runAttempt',
      'deployStartedAt',
      'deployCompletedAt',
    ],
    'Hosting deployment builder',
  );
  if (
    builder.repository !== 'yhsomani/AI-Birthday' ||
    builder.workflowPath !==
      '.github/workflows/hosting-production-deploy.yml' ||
    !/^[1-9][0-9]{0,19}$/u.test(builder.runId ?? '') ||
    !/^[1-9][0-9]{0,9}$/u.test(builder.runAttempt ?? '') ||
    !validInstant(builder.deployStartedAt) ||
    !validInstant(builder.deployCompletedAt) ||
    Date.parse(builder.deployCompletedAt) <
      Date.parse(builder.deployStartedAt) ||
    Date.parse(builder.deployCompletedAt) -
      Date.parse(builder.deployStartedAt) >
      30 * 60 * 1_000 ||
    Date.parse(release.releaseTime) < Date.parse(builder.deployStartedAt) ||
    Date.parse(release.releaseTime) > Date.parse(builder.deployCompletedAt)
  ) {
    throw new Error('Hosting deployment builder identity is invalid');
  }
  const providerOrigin = validateHostingProviderOrigin({
    projectId: artifact.projectId,
    siteId: artifact.siteId,
    publicBaseUrl: artifact.publicBaseUrl,
    siteObservation,
    siteObservationBytes,
    originObservation,
    originObservationBytes,
  });
  const firebaseWebConfig = validateFirebaseWebConfig({
    projectId: artifact.projectId,
    projectNumber,
    webAppId,
    webConfig,
    webConfigBytes,
  });
  const execution = validateExecution({
    admissionPass,
    admissionPassBytes,
    admissionCheckManifest,
    admissionCheckManifestBytes,
    admissionCheckFiles,
    deployIdentity,
    applicationProjectId: artifact.projectId,
    applicationProjectNumber: projectNumber,
    expectedSourceRevision: artifact.sourceRevision,
    expectedSiteId: artifact.siteId,
    builder,
  });
  return {
    schemaVersion: 1,
    product: 'birthday-autopilot-hosting-deployment-provenance',
    status: 'deployed',
    sourceRevision: artifact.sourceRevision,
    projectId: artifact.projectId,
    projectNumber,
    webAppId,
    siteId: artifact.siteId,
    publicBaseUrl: artifact.publicBaseUrl,
    provider: {
      origin: providerOrigin,
      firebaseWebConfig,
    },
    execution,
    builder,
    artifact: {
      fileName: manifest.artifactFileName,
      sha256: manifest.artifactSha256,
      bytes: manifest.artifactBytes,
      manifestFileName: 'hosting-deployment-manifest.json',
      manifestSha256: manifestIdentity.sha256,
      manifestBytes: manifestIdentity.bytes,
      manifestProjectionSha256: artifact.manifestSha256,
      hostingSourceTreeSha256: artifact.hostingSourceTreeSha256,
      sourceFirebaseConfigSha256: artifact.sourceFirebaseConfigSha256,
      releaseConfigSha256: artifact.releaseConfigSha256,
      deploymentConfigSha256: artifact.deploymentConfigSha256,
      publicTreeSha256: artifact.publicTreeSha256,
    },
    deployment: {
      firebaseCliVersion: artifact.firebaseCliVersion,
      firebaseDeployResultSha256: sha256(deployResultBytes),
      firebaseDeployVersionName: deployResult.result.hosting,
      releaseName: releaseResource.name,
      releaseId: releaseResource.id,
      releaseType: release.type,
      releaseTime: release.releaseTime,
      versionName: versionResource.name,
      versionId: versionResource.id,
      versionStatus: version.status,
      versionCreateTime: version.createTime,
      versionFinalizeTime: version.finalizeTime,
      fileCount: String(version.fileCount),
      versionBytes: String(version.versionBytes),
    },
  };
}

export function verifyHostingDeploymentProvenanceReport(report) {
  exactKeys(
    report,
    [
      'schemaVersion',
      'product',
      'status',
      'sourceRevision',
      'projectId',
      'projectNumber',
      'webAppId',
      'siteId',
      'publicBaseUrl',
      'provider',
      'execution',
      'builder',
      'artifact',
      'deployment',
    ],
    'Hosting deployment provenance report',
  );
  exactKeys(
    report.provider,
    ['origin', 'firebaseWebConfig'],
    'Hosting deployment provenance provider',
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
    'Hosting deployment provenance provider origin',
  );
  exactKeys(
    report.provider.firebaseWebConfig,
    ['projectId', 'projectNumber', 'webAppId', 'observationSha256'],
    'Hosting deployment provenance Firebase web config',
  );
  exactKeys(
    report.execution,
    ['admissionReader', 'admissionCheck', 'deployer'],
    'Hosting deployment execution',
  );
  exactKeys(
    report.execution.admissionReader,
    ['serviceAccount', 'workloadIdentityProvider'],
    'Hosting deployment admission reader',
  );
  exactKeys(
    report.execution.admissionCheck,
    [
      'applicationProjectId',
      'applicationProjectNumber',
      'sourceRevision',
      'siteId',
      'scannedPrefix',
      'repository',
      'repositoryId',
      'repositoryOwnerId',
      'workflowPath',
      'runId',
      'runAttempt',
      'buildArtifactId',
      'buildArtifactDigest',
      'securityProjectNumber',
      'securityProjectId',
      'bucketName',
      'bucketMetageneration',
      'retentionSeconds',
      'retentionLocked',
      'publicAccessPrevention',
      'uniformBucketLevelAccess',
      'versioningEnabled',
      'softDeleteEnabled',
      'lifecycleDeleteAgeDays',
      'lifecyclePrefix',
      'checkedAt',
      'pageCount',
      'objectCount',
      'checkManifestSha256',
      'checkManifestBytes',
      'rawRootSha256',
      'rawRootBytes',
      'snapshotSha256',
    ],
    'Hosting deployment admission check',
  );
  exactKeys(
    report.execution.deployer,
    [
      'serviceAccount',
      'workloadIdentityProvider',
      'authenticatedAt',
      'buildArtifactId',
      'buildArtifactDigest',
      'admissionArtifactId',
      'admissionArtifactDigest',
    ],
    'Hosting deployment identity',
  );
  exactKeys(
    report.builder,
    [
      'repository',
      'workflowPath',
      'runId',
      'runAttempt',
      'deployStartedAt',
      'deployCompletedAt',
    ],
    'Hosting deployment provenance builder',
  );
  exactKeys(
    report.artifact,
    [
      'fileName',
      'sha256',
      'bytes',
      'manifestFileName',
      'manifestSha256',
      'manifestBytes',
      'manifestProjectionSha256',
      'hostingSourceTreeSha256',
      'sourceFirebaseConfigSha256',
      'releaseConfigSha256',
      'deploymentConfigSha256',
      'publicTreeSha256',
    ],
    'Hosting deployment provenance artifact',
  );
  exactKeys(
    report.deployment,
    [
      'firebaseCliVersion',
      'firebaseDeployResultSha256',
      'firebaseDeployVersionName',
      'releaseName',
      'releaseId',
      'releaseType',
      'releaseTime',
      'versionName',
      'versionId',
      'versionStatus',
      'versionCreateTime',
      'versionFinalizeTime',
      'fileCount',
      'versionBytes',
    ],
    'Hosting deployment provenance deployment',
  );
  if (
    report.schemaVersion !== 1 ||
    report.product !== 'birthday-autopilot-hosting-deployment-provenance' ||
    report.status !== 'deployed' ||
    !REVISION.test(report.sourceRevision ?? '') ||
    !PROJECT_ID.test(report.projectId ?? '') ||
    !PROJECT_NUMBER.test(report.projectNumber ?? '') ||
    !WEB_APP_ID.test(report.webAppId ?? '') ||
    !report.webAppId.startsWith(`1:${report.projectNumber}:web:`) ||
    !SITE_ID.test(report.siteId ?? '') ||
    report.artifact.fileName !== 'hosting-deployment-artifact.json' ||
    report.artifact.manifestFileName !== 'hosting-deployment-manifest.json' ||
    !Number.isSafeInteger(report.artifact.bytes) ||
    report.artifact.bytes <= 0 ||
    !Number.isSafeInteger(report.artifact.manifestBytes) ||
    report.artifact.manifestBytes <= 0 ||
    report.deployment.firebaseCliVersion !== '15.23.0' ||
    report.deployment.firebaseDeployVersionName !==
      report.deployment.versionName ||
    report.deployment.releaseType !== 'DEPLOY' ||
    report.deployment.versionStatus !== 'FINALIZED'
  ) {
    throw new Error('Hosting deployment provenance report identity is invalid');
  }
  let publicUrl;
  try {
    publicUrl = new URL(report.publicBaseUrl);
  } catch {
    throw new Error('Hosting deployment provenance origin is invalid');
  }
  if (
    publicUrl.protocol !== 'https:' ||
    publicUrl.username !== '' ||
    publicUrl.password !== '' ||
    publicUrl.pathname !== '/' ||
    publicUrl.search !== '' ||
    publicUrl.hash !== '' ||
    report.builder.repository !== 'yhsomani/AI-Birthday' ||
    report.builder.workflowPath !==
      '.github/workflows/hosting-production-deploy.yml' ||
    !/^[1-9][0-9]{0,19}$/u.test(report.builder.runId ?? '') ||
    !/^[1-9][0-9]{0,9}$/u.test(report.builder.runAttempt ?? '') ||
    report.execution.admissionCheck.applicationProjectId !== report.projectId ||
    report.execution.admissionCheck.applicationProjectNumber !==
      report.projectNumber ||
    report.execution.admissionCheck.sourceRevision !== report.sourceRevision ||
    report.execution.admissionCheck.siteId !== report.siteId ||
    report.execution.admissionCheck.scannedPrefix !==
      `hosting-production-change-freezes/${report.siteId}/` ||
    report.execution.admissionCheck.repository !== report.builder.repository ||
    !POSITIVE_INTEGER.test(
      report.execution.admissionCheck.repositoryId ?? '',
    ) ||
    !POSITIVE_INTEGER.test(
      report.execution.admissionCheck.repositoryOwnerId ?? '',
    ) ||
    report.execution.admissionCheck.workflowPath !==
      report.builder.workflowPath ||
    report.execution.admissionCheck.runId !== report.builder.runId ||
    report.execution.admissionCheck.runAttempt !== report.builder.runAttempt ||
    !POSITIVE_INTEGER.test(
      report.execution.admissionCheck.buildArtifactId ?? '',
    ) ||
    !SHA256.test(report.execution.admissionCheck.buildArtifactDigest ?? '') ||
    !PROJECT_NUMBER.test(
      report.execution.admissionCheck.securityProjectNumber ?? '',
    ) ||
    !PROJECT_ID.test(report.execution.admissionCheck.securityProjectId ?? '') ||
    report.execution.admissionCheck.securityProjectId === report.projectId ||
    report.execution.admissionCheck.securityProjectNumber ===
      report.projectNumber ||
    !BUCKET_NAME.test(report.execution.admissionCheck.bucketName ?? '') ||
    !POSITIVE_INTEGER.test(
      report.execution.admissionCheck.bucketMetageneration ?? '',
    ) ||
    report.execution.admissionCheck.retentionSeconds !== 900 ||
    report.execution.admissionCheck.retentionLocked !== true ||
    report.execution.admissionCheck.publicAccessPrevention !== 'enforced' ||
    report.execution.admissionCheck.uniformBucketLevelAccess !== true ||
    report.execution.admissionCheck.versioningEnabled !== false ||
    report.execution.admissionCheck.softDeleteEnabled !== false ||
    report.execution.admissionCheck.lifecycleDeleteAgeDays !== 1 ||
    report.execution.admissionCheck.lifecyclePrefix !==
      'hosting-production-change-freezes/' ||
    !validInstant(report.execution.admissionCheck.checkedAt) ||
    !Number.isSafeInteger(report.execution.admissionCheck.pageCount) ||
    report.execution.admissionCheck.pageCount <= 0 ||
    !Number.isSafeInteger(report.execution.admissionCheck.objectCount) ||
    report.execution.admissionCheck.objectCount < 0 ||
    !Number.isSafeInteger(report.execution.admissionCheck.checkManifestBytes) ||
    report.execution.admissionCheck.checkManifestBytes <= 0 ||
    !Number.isSafeInteger(report.execution.admissionCheck.rawRootBytes) ||
    report.execution.admissionCheck.rawRootBytes <= 0 ||
    !SERVICE_ACCOUNT.test(
      report.execution.admissionReader.serviceAccount ?? '',
    ) ||
    !report.execution.admissionReader.serviceAccount.endsWith(
      `@${report.execution.admissionCheck.securityProjectId}.iam.gserviceaccount.com`,
    ) ||
    !WIF_PROVIDER.test(
      report.execution.admissionReader.workloadIdentityProvider ?? '',
    ) ||
    !report.execution.admissionReader.workloadIdentityProvider.startsWith(
      `projects/${report.execution.admissionCheck.securityProjectNumber}/`,
    ) ||
    !SERVICE_ACCOUNT.test(report.execution.deployer.serviceAccount ?? '') ||
    !report.execution.deployer.serviceAccount.endsWith(
      `@${report.projectId}.iam.gserviceaccount.com`,
    ) ||
    !WIF_PROVIDER.test(
      report.execution.deployer.workloadIdentityProvider ?? '',
    ) ||
    !report.execution.deployer.workloadIdentityProvider.startsWith(
      `projects/${report.projectNumber}/`,
    ) ||
    report.execution.deployer.serviceAccount ===
      report.execution.admissionReader.serviceAccount ||
    report.execution.deployer.workloadIdentityProvider ===
      report.execution.admissionReader.workloadIdentityProvider ||
    !validInstant(report.execution.deployer.authenticatedAt) ||
    report.execution.deployer.buildArtifactId !==
      report.execution.admissionCheck.buildArtifactId ||
    report.execution.deployer.buildArtifactDigest !==
      report.execution.admissionCheck.buildArtifactDigest ||
    !POSITIVE_INTEGER.test(
      report.execution.deployer.admissionArtifactId ?? '',
    ) ||
    !SHA256.test(report.execution.deployer.admissionArtifactDigest ?? '') ||
    Date.parse(report.execution.admissionCheck.checkedAt) >
      Date.parse(report.execution.deployer.authenticatedAt) ||
    Date.parse(report.execution.deployer.authenticatedAt) >
      Date.parse(report.builder.deployStartedAt)
  ) {
    throw new Error('Hosting deployment provenance builder/origin is invalid');
  }
  const expectedDefaultHosts = new Set([
    `${report.siteId}.web.app`,
    `${report.siteId}.firebaseapp.com`,
  ]);
  if (
    report.provider.origin.hostname !== publicUrl.hostname ||
    report.provider.origin.siteResourceName !==
      `projects/${report.projectId}/sites/${report.siteId}` ||
    !['firebase-default-domain', 'firebase-custom-domain'].includes(
      report.provider.origin.kind,
    ) ||
    (report.provider.origin.kind === 'firebase-default-domain' &&
      (!expectedDefaultHosts.has(publicUrl.hostname) ||
        report.provider.origin.hostState !== null ||
        report.provider.origin.ownershipState !== null)) ||
    (report.provider.origin.kind === 'firebase-custom-domain' &&
      (expectedDefaultHosts.has(publicUrl.hostname) ||
        report.provider.origin.originResourceName !==
          `projects/${report.projectId}/sites/${report.siteId}/customDomains/${publicUrl.hostname}` ||
        report.provider.origin.hostState !== 'HOST_ACTIVE' ||
        report.provider.origin.ownershipState !== 'OWNERSHIP_ACTIVE')) ||
    report.provider.origin.deleteTimeAbsent !== true ||
    report.provider.origin.redirectTargetAbsent !== true ||
    report.provider.origin.issuesAbsent !== true ||
    report.provider.firebaseWebConfig.projectId !== report.projectId ||
    report.provider.firebaseWebConfig.projectNumber !== report.projectNumber ||
    report.provider.firebaseWebConfig.webAppId !== report.webAppId
  ) {
    throw new Error('Hosting deployment provider identity is invalid');
  }
  for (const value of [
    report.artifact.sha256,
    report.artifact.manifestSha256,
    report.artifact.manifestProjectionSha256,
    report.artifact.hostingSourceTreeSha256,
    report.artifact.sourceFirebaseConfigSha256,
    report.artifact.releaseConfigSha256,
    report.artifact.deploymentConfigSha256,
    report.artifact.publicTreeSha256,
    report.deployment.firebaseDeployResultSha256,
    report.provider.origin.siteObservationSha256,
    report.provider.origin.originObservationSha256,
    report.provider.firebaseWebConfig.observationSha256,
    report.execution.admissionCheck.checkManifestSha256,
    report.execution.admissionCheck.rawRootSha256,
    report.execution.admissionCheck.snapshotSha256,
  ]) {
    if (!SHA256.test(value ?? '')) {
      throw new Error('Hosting deployment provenance digest is invalid');
    }
  }
  const releaseResource = resourceName(
    report.deployment.releaseName,
    report.siteId,
    'releases',
  );
  const versionResource = resourceName(
    report.deployment.versionName,
    report.siteId,
    'versions',
  );
  if (
    releaseResource.id !== report.deployment.releaseId ||
    versionResource.id !== report.deployment.versionId ||
    report.deployment.firebaseDeployVersionName !==
      report.deployment.versionName ||
    !validInstant(report.deployment.releaseTime) ||
    !validInstant(report.deployment.versionCreateTime) ||
    !validInstant(report.deployment.versionFinalizeTime) ||
    !validInstant(report.builder.deployStartedAt) ||
    !validInstant(report.builder.deployCompletedAt) ||
    !UNSIGNED_INTEGER.test(report.deployment.fileCount ?? '') ||
    Number(report.deployment.fileCount) <= 0 ||
    !UNSIGNED_INTEGER.test(report.deployment.versionBytes ?? '') ||
    Number(report.deployment.versionBytes) <= 0 ||
    Date.parse(report.deployment.versionCreateTime) >
      Date.parse(report.deployment.versionFinalizeTime) ||
    Date.parse(report.deployment.versionFinalizeTime) >
      Date.parse(report.deployment.releaseTime) ||
    Date.parse(report.builder.deployStartedAt) >
      Date.parse(report.builder.deployCompletedAt) ||
    Date.parse(report.builder.deployCompletedAt) -
      Date.parse(report.builder.deployStartedAt) >
      30 * 60 * 1_000 ||
    Date.parse(report.deployment.releaseTime) <
      Date.parse(report.builder.deployStartedAt) ||
    Date.parse(report.deployment.releaseTime) >
      Date.parse(report.builder.deployCompletedAt)
  ) {
    throw new Error(
      'Hosting deployment provenance resource, size, or timestamp is invalid',
    );
  }
  return report;
}

const readAdmissionCheckFiles = root => {
  const requested = nodePath.resolve(root);
  const rootStatus = lstatSync(requested);
  if (rootStatus.isSymbolicLink() || !rootStatus.isDirectory()) {
    throw new Error('Hosting admission check root must be a real directory');
  }
  const canonicalRoot = realpathSync(requested);
  return readdirSync(canonicalRoot, { withFileTypes: true })
    .sort((left, right) =>
      left.name < right.name ? -1 : left.name > right.name ? 1 : 0,
    )
    .map(entry => {
      if (!entry.isFile() || entry.isSymbolicLink()) {
        throw new Error('Hosting admission check root has an unsafe entry');
      }
      return {
        path: entry.name,
        bytes: readStableRegularFile(
          nodePath.join(canonicalRoot, entry.name),
          MAXIMUM_INPUT_BYTES,
          `Hosting admission check ${entry.name}`,
        ),
      };
    });
};

const parseArgs = argv => {
  const required = [
    'artifact',
    'manifest',
    'deploy-result',
    'deployment-window',
    'release-observation',
    'version-observation',
    'site-observation',
    'origin-observation',
    'firebase-web-config',
    'admission-pass',
    'admission-check-manifest',
    'admission-check-root',
    'deploy-identity',
    'project-number',
    'web-app-id',
    'output',
  ];
  if (argv.length !== required.length * 2) {
    throw new Error('Hosting provenance arguments are incomplete');
  }
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const key = flag?.slice(2);
    if (!flag?.startsWith('--') || !required.includes(key) || values.has(key)) {
      throw new Error(`unsupported or duplicate argument ${flag}`);
    }
    values.set(key, argv[index + 1]);
  }
  return values;
};

const main = () => {
  if (process.env.GITHUB_ACTIONS !== 'true') {
    throw new Error(
      'Hosting deployment provenance is produced only by GitHub Actions',
    );
  }
  const args = parseArgs(process.argv.slice(2));
  const read = (key, label) =>
    readStableRegularFile(args.get(key), MAXIMUM_INPUT_BYTES, label);
  const artifactBytes = read('artifact', 'Hosting deployment artifact');
  const manifestBytes = read('manifest', 'Hosting deployment manifest');
  const deployResultBytes = read('deploy-result', 'Firebase deploy result');
  const admissionPassBytes = read(
    'admission-pass',
    'Hosting admission PASS snapshot',
  );
  const admissionCheckManifestBytes = read(
    'admission-check-manifest',
    'Hosting admission check manifest',
  );
  const report = createHostingDeploymentProvenanceReport({
    artifact: parseJson(artifactBytes, 'Hosting deployment artifact'),
    artifactBytes,
    manifest: parseJson(manifestBytes, 'Hosting deployment manifest'),
    manifestBytes,
    deployResult: parseJson(deployResultBytes, 'Firebase deploy result'),
    deployResultBytes,
    release: parseJson(
      read('release-observation', 'Firebase Hosting release observation'),
      'Firebase Hosting release observation',
    ),
    version: parseJson(
      read('version-observation', 'Firebase Hosting version observation'),
      'Firebase Hosting version observation',
    ),
    projectNumber: args.get('project-number'),
    webAppId: args.get('web-app-id'),
    siteObservation: parseJson(
      read('site-observation', 'Firebase Hosting site observation'),
      'Firebase Hosting site observation',
    ),
    siteObservationBytes: read(
      'site-observation',
      'Firebase Hosting site observation',
    ),
    originObservation: parseJson(
      read('origin-observation', 'Firebase Hosting origin observation'),
      'Firebase Hosting origin observation',
    ),
    originObservationBytes: read(
      'origin-observation',
      'Firebase Hosting origin observation',
    ),
    webConfig: parseJson(
      read('firebase-web-config', 'Firebase reserved web config'),
      'Firebase reserved web config',
    ),
    webConfigBytes: read('firebase-web-config', 'Firebase reserved web config'),
    admissionPass: parseJson(
      admissionPassBytes,
      'Hosting admission PASS snapshot',
    ),
    admissionPassBytes,
    admissionCheckManifest: parseJson(
      admissionCheckManifestBytes,
      'Hosting admission check manifest',
    ),
    admissionCheckManifestBytes,
    admissionCheckFiles: readAdmissionCheckFiles(
      args.get('admission-check-root'),
    ),
    deployIdentity: parseJson(
      read('deploy-identity', 'Hosting deploy identity'),
      'Hosting deploy identity',
    ),
    builder: {
      repository: process.env.GITHUB_REPOSITORY,
      workflowPath: '.github/workflows/hosting-production-deploy.yml',
      runId: process.env.GITHUB_RUN_ID,
      runAttempt: process.env.GITHUB_RUN_ATTEMPT,
      ...parseJson(
        read('deployment-window', 'Hosting deployment operation window'),
        'Hosting deployment operation window',
      ),
    },
  });
  writeFileSync(args.get('output'), `${stableJson(report)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  });
  process.stdout.write(
    `PASS Hosting deployment provenance release=${report.deployment.releaseName} artifact=${report.artifact.sha256}\n`,
  );
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
