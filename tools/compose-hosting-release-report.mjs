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

import { parseReleaseConfig } from '../backend/hosting/tools/release-config.mjs';
import { verifyHostingCurrentLiveObservation } from './create-hosting-current-live-observation.mjs';
import {
  verifyHostingDeploymentManifest,
  verifyHostingDeploymentProvenanceReport,
} from './create-hosting-deployment-provenance.mjs';
import { verifyHostingDeploymentArtifact } from './hosting-deployment-artifact.mjs';

const SHA256 = /^[0-9a-f]{64}$/u;
const REVISION = /^[0-9a-f]{40}$/u;
const MAXIMUM_REPORT_BYTES = 4 * 1024 * 1024;
const MAXIMUM_CONFIG_BYTES = 2 * 1024 * 1024;
const MAXIMUM_ARTIFACT_BYTES = 512 * 1024 * 1024;
const CLI_KEYS = new Set([
  'cloud-report',
  'store-report',
  'hosting-config',
  'deployment-artifact',
  'deployment-manifest',
  'deployment-provenance',
  'current-live-observation',
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

const exactKeys = (value, expected, label) => {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} must be an object`);
  }
  const keys = Object.keys(value);
  if (
    keys.length !== expected.length ||
    keys.some(key => !expected.includes(key))
  ) {
    throw new Error(`${label} fields do not match the exact report contract`);
  }
};

const readBoundedRegularFile = (file, maximumBytes, label) => {
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
    if (
      opened.dev !== before.dev ||
      opened.ino !== before.ino ||
      opened.size !== before.size
    ) {
      throw new Error(`${label} changed before it was read`);
    }
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    if (
      BigInt(bytes.byteLength) !== opened.size ||
      after.size !== opened.size
    ) {
      throw new Error(`${label} changed while it was read`);
    }
    return bytes;
  } finally {
    closeSync(descriptor);
  }
};

const parseJson = (bytes, label) => {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is not valid JSON`);
  }
};

export function composeHostingReleaseReport({
  cloudReport,
  storeReport,
  releaseConfig,
  deployedArtifactSha256,
  deploymentArtifact,
  deploymentArtifactBytes,
  deploymentManifest,
  deploymentManifestBytes,
  deploymentProvenance,
  deploymentProvenanceBytes,
  currentLiveObservation,
  currentLiveObservationBytes,
  cloudReportSha256,
  storeReportSha256,
  nowMs = Date.now(),
}) {
  exactKeys(
    cloudReport,
    [
      'schemaVersion',
      'product',
      'status',
      'sourceRevision',
      'evidenceSha256',
      'authorityPublicKeySpkiSha256',
      'validUntil',
      'project',
      'clientTrust',
      'hosting',
      'hostingReleaseControl',
    ],
    'cloud report',
  );
  exactKeys(
    storeReport,
    [
      'schemaVersion',
      'product',
      'status',
      'sourceRevision',
      'evidenceSha256',
      'approvalScopeSha256',
      'authorityPublicKeySpkiSha256',
      'validUntil',
      'releaseCoordinates',
      'publicIdentity',
      'artifactDigests',
      'hostingConfigSha256',
    ],
    'store report',
  );
  if (
    cloudReport.schemaVersion !== 1 ||
    cloudReport.product !== 'birthday-autopilot-cloud-release-verification' ||
    cloudReport.status !== 'verified' ||
    storeReport.schemaVersion !== 1 ||
    storeReport.product !== 'birthday-autopilot-store-release-verification' ||
    storeReport.status !== 'verified'
  ) {
    throw new Error('component report identity is invalid');
  }
  if (
    !REVISION.test(cloudReport.sourceRevision ?? '') ||
    cloudReport.sourceRevision !== storeReport.sourceRevision
  ) {
    throw new Error(
      'cloud and store reports do not bind the same source revision',
    );
  }
  if (
    !SHA256.test(cloudReport.authorityPublicKeySpkiSha256 ?? '') ||
    cloudReport.authorityPublicKeySpkiSha256 !==
      storeReport.authorityPublicKeySpkiSha256
  ) {
    throw new Error('cloud and store reports do not bind the same authority');
  }
  const configSha256 = sha256(releaseConfig.rawBytes);
  if (
    configSha256 !== cloudReport.hosting?.releaseConfigSha256 ||
    configSha256 !== storeReport.hostingConfigSha256
  ) {
    throw new Error('Hosting config bytes do not match cloud and store proof');
  }
  if (
    releaseConfig.parsed.publicBaseUrl !== cloudReport.hosting?.publicBaseUrl ||
    releaseConfig.parsed.publicBaseUrl !==
      storeReport.publicIdentity?.publicBaseUrl
  ) {
    throw new Error('Hosting origin does not match cloud and store proof');
  }
  if (
    !SHA256.test(deployedArtifactSha256) ||
    deployedArtifactSha256 !== cloudReport.hosting?.deployedArtifactSha256
  ) {
    throw new Error('deployed Hosting artifact does not match cloud proof');
  }
  verifyHostingDeploymentArtifact(deploymentArtifact);
  verifyHostingDeploymentManifest(
    deploymentManifest,
    deploymentManifestBytes,
    deploymentArtifact,
    deploymentArtifactBytes,
  );
  verifyHostingDeploymentProvenanceReport(deploymentProvenance);
  verifyHostingCurrentLiveObservation(currentLiveObservation, nowMs);
  exactKeys(
    cloudReport.hostingReleaseControl,
    [
      'repositoryId',
      'repositoryOwnerId',
      'releaseSecurityProjectId',
      'releaseSecurityProjectNumber',
      'applicationIamAnalysisScope',
      'releaseSecurityIamAnalysisScope',
      'observerServiceAccount',
      'observerWifProvider',
      'admissionReaderServiceAccount',
      'admissionReaderWifProvider',
      'deployServiceAccount',
      'deployWifProvider',
      'admissionBucketName',
      'admissionBucketMetageneration',
      'auditLogBucketName',
      'auditSinkName',
    ],
    'cloud Hosting release control',
  );
  if (
    cloudReport.hostingReleaseControl.releaseSecurityProjectId !==
      currentLiveObservation.admissionLease.securityProjectId ||
    cloudReport.hostingReleaseControl.repositoryId !==
      currentLiveObservation.provider.executionIdentity.repositoryId ||
    cloudReport.hostingReleaseControl.repositoryId !==
      deploymentProvenance.execution.admissionCheck.repositoryId ||
    cloudReport.hostingReleaseControl.repositoryOwnerId !==
      currentLiveObservation.provider.executionIdentity.repositoryOwnerId ||
    cloudReport.hostingReleaseControl.repositoryOwnerId !==
      deploymentProvenance.execution.admissionCheck.repositoryOwnerId ||
    cloudReport.hostingReleaseControl.releaseSecurityProjectId !==
      deploymentProvenance.execution.admissionCheck.securityProjectId ||
    cloudReport.hostingReleaseControl.releaseSecurityProjectNumber !==
      currentLiveObservation.admissionLease.securityProjectNumber ||
    cloudReport.hostingReleaseControl.releaseSecurityProjectNumber !==
      deploymentProvenance.execution.admissionCheck.securityProjectNumber ||
    cloudReport.hostingReleaseControl.observerServiceAccount !==
      currentLiveObservation.provider.executionIdentity.serviceAccount ||
    cloudReport.hostingReleaseControl.observerWifProvider !==
      currentLiveObservation.provider.executionIdentity
        .workloadIdentityProvider ||
    cloudReport.hostingReleaseControl.admissionReaderServiceAccount !==
      deploymentProvenance.execution.admissionReader.serviceAccount ||
    cloudReport.hostingReleaseControl.admissionReaderWifProvider !==
      deploymentProvenance.execution.admissionReader.workloadIdentityProvider ||
    cloudReport.hostingReleaseControl.deployServiceAccount !==
      deploymentProvenance.execution.deployer.serviceAccount ||
    cloudReport.hostingReleaseControl.deployWifProvider !==
      deploymentProvenance.execution.deployer.workloadIdentityProvider ||
    cloudReport.hostingReleaseControl.admissionBucketName !==
      currentLiveObservation.admissionLease.bucketName ||
    cloudReport.hostingReleaseControl.admissionBucketName !==
      deploymentProvenance.execution.admissionCheck.bucketName ||
    cloudReport.hostingReleaseControl.admissionBucketMetageneration !==
      currentLiveObservation.admissionLease.bucketMetageneration ||
    cloudReport.hostingReleaseControl.admissionBucketMetageneration !==
      deploymentProvenance.execution.admissionCheck.bucketMetageneration ||
    currentLiveObservation.admissionLease.retentionSeconds !==
      deploymentProvenance.execution.admissionCheck.retentionSeconds ||
    currentLiveObservation.admissionLease.lifecycleDeleteAgeDays !==
      deploymentProvenance.execution.admissionCheck.lifecycleDeleteAgeDays ||
    currentLiveObservation.provider.executionIdentity.serviceAccount ===
      deploymentProvenance.execution.admissionReader.serviceAccount ||
    currentLiveObservation.provider.executionIdentity.serviceAccount ===
      deploymentProvenance.execution.deployer.serviceAccount
  ) {
    throw new Error(
      'Hosting release-control identities, bucket, or policy differ from cloud/provider proof',
    );
  }
  if (
    sha256(deploymentArtifactBytes) !== deployedArtifactSha256 ||
    deploymentManifest.artifactSha256 !== deployedArtifactSha256 ||
    sha256(deploymentManifestBytes) !==
      cloudReport.hosting?.deploymentManifestSha256 ||
    sha256(deploymentProvenanceBytes) !==
      cloudReport.hosting?.deploymentProvenanceSha256 ||
    deploymentProvenance.artifact.sha256 !== deployedArtifactSha256 ||
    deploymentProvenance.artifact.manifestSha256 !==
      cloudReport.hosting?.deploymentManifestSha256 ||
    deploymentProvenance.artifact.bytes !==
      deploymentArtifactBytes.byteLength ||
    deploymentProvenance.artifact.manifestBytes !==
      deploymentManifestBytes.byteLength ||
    deploymentProvenance.artifact.manifestProjectionSha256 !==
      deploymentArtifact.manifestSha256 ||
    deploymentProvenance.artifact.hostingSourceTreeSha256 !==
      deploymentArtifact.hostingSourceTreeSha256 ||
    deploymentProvenance.artifact.sourceFirebaseConfigSha256 !==
      deploymentArtifact.sourceFirebaseConfigSha256 ||
    deploymentProvenance.artifact.releaseConfigSha256 !==
      deploymentArtifact.releaseConfigSha256 ||
    deploymentProvenance.artifact.deploymentConfigSha256 !==
      deploymentArtifact.deploymentConfigSha256 ||
    deploymentProvenance.artifact.publicTreeSha256 !==
      deploymentArtifact.publicTreeSha256 ||
    deploymentProvenance.deployment.firebaseCliVersion !==
      deploymentArtifact.firebaseCliVersion
  ) {
    throw new Error(
      'Hosting deployment artifact, manifest, provenance, and cloud proof differ',
    );
  }
  if (
    sha256(currentLiveObservationBytes) !==
      cloudReport.hosting?.currentLiveObservationSha256 ||
    currentLiveObservation.sourceRevision !== cloudReport.sourceRevision ||
    currentLiveObservation.projectId !== cloudReport.project?.projectId ||
    currentLiveObservation.projectNumber !==
      cloudReport.project?.projectNumber ||
    currentLiveObservation.webAppId !== cloudReport.project?.webAppId ||
    currentLiveObservation.siteId !== cloudReport.hosting?.siteId ||
    currentLiveObservation.publicBaseUrl !==
      cloudReport.hosting?.publicBaseUrl ||
    currentLiveObservation.live.versionId !==
      deploymentProvenance.deployment.versionId ||
    currentLiveObservation.live.versionId !==
      cloudReport.hosting?.deployedVersionId
  ) {
    throw new Error(
      'current live Hosting state does not match deployment/cloud proof',
    );
  }
  if (
    deploymentProvenance.projectNumber !== cloudReport.project?.projectNumber ||
    deploymentProvenance.webAppId !== cloudReport.project?.webAppId ||
    deploymentProvenance.provider.origin.originObservationSha256 !==
      cloudReport.hosting?.providerOriginObservationSha256 ||
    deploymentProvenance.provider.firebaseWebConfig.observationSha256 !==
      cloudReport.hosting?.firebaseWebConfigObservationSha256 ||
    currentLiveObservation.provider.firebaseWebConfig.projectId !==
      cloudReport.project?.projectId ||
    currentLiveObservation.provider.firebaseWebConfig.projectNumber !==
      cloudReport.project?.projectNumber ||
    currentLiveObservation.provider.firebaseWebConfig.webAppId !==
      cloudReport.project?.webAppId
  ) {
    throw new Error(
      'Hosting provider origin/web-app identity differs from cloud proof',
    );
  }
  if (
    deploymentArtifact.sourceRevision !== cloudReport.sourceRevision ||
    deploymentProvenance.sourceRevision !== cloudReport.sourceRevision ||
    deploymentArtifact.projectId !== cloudReport.project?.projectId ||
    deploymentProvenance.projectId !== cloudReport.project?.projectId ||
    deploymentArtifact.siteId !== cloudReport.hosting?.siteId ||
    deploymentProvenance.siteId !== cloudReport.hosting?.siteId ||
    deploymentProvenance.deployment.versionId !==
      cloudReport.hosting?.deployedVersionId ||
    deploymentArtifact.publicBaseUrl !== cloudReport.hosting?.publicBaseUrl ||
    deploymentProvenance.publicBaseUrl !== cloudReport.hosting?.publicBaseUrl
  ) {
    throw new Error(
      'Hosting deployment source/project/site/version/origin differs from cloud proof',
    );
  }
  if (
    deploymentArtifact.releaseConfigSha256 !== configSha256 ||
    deploymentArtifact.hostingSourceTreeSha256 !==
      cloudReport.hosting?.hostingSourceTreeSha256 ||
    deploymentArtifact.sourceFirebaseConfigSha256 !==
      cloudReport.hosting?.firebaseConfigSha256 ||
    deploymentArtifact.deploymentConfigSha256 !==
      cloudReport.hosting?.deploymentConfigSha256 ||
    deploymentArtifact.publicTreeSha256 !==
      cloudReport.hosting?.publicTreeSha256
  ) {
    throw new Error(
      'Hosting deployment configuration/source/public tree differs from cloud proof',
    );
  }
  if (!SHA256.test(cloudReportSha256) || !SHA256.test(storeReportSha256)) {
    throw new Error('component report byte digests are invalid');
  }
  const validUntil = [
    cloudReport.validUntil,
    storeReport.validUntil,
    currentLiveObservation.validUntil,
  ].reduce((earliest, candidate) =>
    Date.parse(candidate) < Date.parse(earliest) ? candidate : earliest,
  );
  const recaptchaEnterpriseSiteKeySha256 = sha256(
    Buffer.from(releaseConfig.parsed.recaptchaEnterpriseSiteKey, 'utf8'),
  );
  return {
    schemaVersion: 1,
    product: 'birthday-autopilot-hosting-release-verification',
    status: 'verified',
    sourceRevision: cloudReport.sourceRevision,
    authorityPublicKeySpkiSha256: cloudReport.authorityPublicKeySpkiSha256,
    validUntil,
    publicBaseUrl: releaseConfig.parsed.publicBaseUrl,
    projectId: deploymentProvenance.projectId,
    projectNumber: deploymentProvenance.projectNumber,
    webAppId: deploymentProvenance.webAppId,
    siteId: deploymentProvenance.siteId,
    deployedVersionId: deploymentProvenance.deployment.versionId,
    releaseName: deploymentProvenance.deployment.releaseName,
    versionName: deploymentProvenance.deployment.versionName,
    releaseTime: deploymentProvenance.deployment.releaseTime,
    providerOriginObservationSha256:
      deploymentProvenance.provider.origin.originObservationSha256,
    firebaseWebConfigObservationSha256:
      deploymentProvenance.provider.firebaseWebConfig.observationSha256,
    currentLiveObservationSha256: sha256(currentLiveObservationBytes),
    currentLiveCapturedAt: currentLiveObservation.capturedAt,
    currentLiveObserverServiceAccount:
      currentLiveObservation.provider.executionIdentity.serviceAccount,
    currentLiveObserverWifProvider:
      currentLiveObservation.provider.executionIdentity
        .workloadIdentityProvider,
    admissionSecurityProjectId:
      currentLiveObservation.admissionLease.securityProjectId,
    repositoryId: cloudReport.hostingReleaseControl.repositoryId,
    repositoryOwnerId: cloudReport.hostingReleaseControl.repositoryOwnerId,
    applicationIamAnalysisScope:
      cloudReport.hostingReleaseControl.applicationIamAnalysisScope,
    releaseSecurityIamAnalysisScope:
      cloudReport.hostingReleaseControl.releaseSecurityIamAnalysisScope,
    admissionSecurityProjectNumber:
      currentLiveObservation.admissionLease.securityProjectNumber,
    admissionBucketName: currentLiveObservation.admissionLease.bucketName,
    admissionBucketMetageneration:
      currentLiveObservation.admissionLease.bucketMetageneration,
    admissionObjectName: currentLiveObservation.admissionLease.objectName,
    admissionObjectGeneration:
      currentLiveObservation.admissionLease.objectGeneration,
    admissionObjectMetageneration:
      currentLiveObservation.admissionLease.objectMetageneration,
    admissionObjectTimeCreated:
      currentLiveObservation.admissionLease.objectTimeCreated,
    admissionObjectRetentionExpirationTime:
      currentLiveObservation.admissionLease.objectRetentionExpirationTime,
    admissionRetentionSeconds:
      currentLiveObservation.admissionLease.retentionSeconds,
    admissionLifecycleDeleteAgeDays:
      currentLiveObservation.admissionLease.lifecycleDeleteAgeDays,
    admissionReaderServiceAccount:
      deploymentProvenance.execution.admissionReader.serviceAccount,
    admissionReaderWifProvider:
      deploymentProvenance.execution.admissionReader.workloadIdentityProvider,
    admissionCheckCheckedAt:
      deploymentProvenance.execution.admissionCheck.checkedAt,
    admissionCheckPageCount:
      deploymentProvenance.execution.admissionCheck.pageCount,
    admissionCheckObjectCount:
      deploymentProvenance.execution.admissionCheck.objectCount,
    admissionCheckManifestSha256:
      deploymentProvenance.execution.admissionCheck.checkManifestSha256,
    admissionCheckSnapshotSha256:
      deploymentProvenance.execution.admissionCheck.snapshotSha256,
    admissionCheckRawRootSha256:
      deploymentProvenance.execution.admissionCheck.rawRootSha256,
    admissionCheckRawRootBytes:
      deploymentProvenance.execution.admissionCheck.rawRootBytes,
    hostingBuildArtifactId:
      deploymentProvenance.execution.admissionCheck.buildArtifactId,
    hostingBuildArtifactDigest:
      deploymentProvenance.execution.admissionCheck.buildArtifactDigest,
    hostingAdmissionArtifactId:
      deploymentProvenance.execution.deployer.admissionArtifactId,
    hostingAdmissionArtifactDigest:
      deploymentProvenance.execution.deployer.admissionArtifactDigest,
    hostingDeployServiceAccount:
      deploymentProvenance.execution.deployer.serviceAccount,
    hostingDeployWifProvider:
      deploymentProvenance.execution.deployer.workloadIdentityProvider,
    hostingDeployAuthenticatedAt:
      deploymentProvenance.execution.deployer.authenticatedAt,
    admissionAuditLogBucketName:
      cloudReport.hostingReleaseControl.auditLogBucketName,
    admissionAuditSinkName: cloudReport.hostingReleaseControl.auditSinkName,
    admissionRunId: currentLiveObservation.builder.runId,
    admissionRunAttempt: currentLiveObservation.builder.runAttempt,
    admissionValidUntil: currentLiveObservation.validUntil,
    admissionLeaseContentSha256:
      currentLiveObservation.admissionLease.contentSha256,
    admissionBucketObservationSha256:
      currentLiveObservation.admissionLease.bucketObservationSha256,
    admissionObjectObservationSha256:
      currentLiveObservation.admissionLease.objectObservationSha256,
    releaseConfigSha256: configSha256,
    deployedArtifactSha256,
    deploymentManifestSha256: sha256(deploymentManifestBytes),
    deploymentProvenanceSha256: sha256(deploymentProvenanceBytes),
    deploymentConfigSha256: deploymentArtifact.deploymentConfigSha256,
    publicTreeSha256: deploymentArtifact.publicTreeSha256,
    firebaseConfigSha256: deploymentArtifact.sourceFirebaseConfigSha256,
    recaptchaEnterpriseSiteKeySha256,
    hostingSourceTreeSha256: cloudReport.hosting.hostingSourceTreeSha256,
    cloudReportSha256,
    storeReportSha256,
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
  const cloudBytes = readBoundedRegularFile(
    args.get('cloud-report'),
    MAXIMUM_REPORT_BYTES,
    'cloud verification report',
  );
  const storeBytes = readBoundedRegularFile(
    args.get('store-report'),
    MAXIMUM_REPORT_BYTES,
    'store verification report',
  );
  const configBytes = readBoundedRegularFile(
    args.get('hosting-config'),
    MAXIMUM_CONFIG_BYTES,
    'Hosting release config',
  );
  const artifactBytes = readBoundedRegularFile(
    args.get('deployment-artifact'),
    MAXIMUM_ARTIFACT_BYTES,
    'deployed Hosting artifact',
  );
  const manifestBytes = readBoundedRegularFile(
    args.get('deployment-manifest'),
    MAXIMUM_REPORT_BYTES,
    'Hosting deployment manifest',
  );
  const provenanceBytes = readBoundedRegularFile(
    args.get('deployment-provenance'),
    MAXIMUM_REPORT_BYTES,
    'Hosting deployment provenance',
  );
  const currentLiveBytes = readBoundedRegularFile(
    args.get('current-live-observation'),
    MAXIMUM_REPORT_BYTES,
    'Hosting current-live observation',
  );
  const report = composeHostingReleaseReport({
    cloudReport: parseJson(cloudBytes, 'cloud verification report'),
    storeReport: parseJson(storeBytes, 'store verification report'),
    releaseConfig: {
      rawBytes: configBytes,
      parsed: parseReleaseConfig(
        parseJson(configBytes, 'Hosting release config'),
      ),
    },
    deployedArtifactSha256: sha256(artifactBytes),
    deploymentArtifact: parseJson(artifactBytes, 'Hosting deployment artifact'),
    deploymentArtifactBytes: artifactBytes,
    deploymentManifest: parseJson(manifestBytes, 'Hosting deployment manifest'),
    deploymentManifestBytes: manifestBytes,
    deploymentProvenance: parseJson(
      provenanceBytes,
      'Hosting deployment provenance',
    ),
    deploymentProvenanceBytes: provenanceBytes,
    currentLiveObservation: parseJson(
      currentLiveBytes,
      'Hosting current-live observation',
    ),
    currentLiveObservationBytes: currentLiveBytes,
    cloudReportSha256: sha256(cloudBytes),
    storeReportSha256: sha256(storeBytes),
  });
  writeFileSync(args.get('output'), `${stableJson(report)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  });
  process.stdout.write(
    `PASS Hosting release origin=${report.publicBaseUrl} source=${report.sourceRevision}\n`,
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
