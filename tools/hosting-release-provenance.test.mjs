import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import {
  linkSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { parseReleaseConfig } from '../backend/hosting/tools/release-config.mjs';
import {
  createHostingCurrentLiveObservation,
  verifyHostingCurrentLiveObservation,
} from './create-hosting-current-live-observation.mjs';
import {
  createHostingDeploymentProvenanceReport,
  verifyHostingDeploymentProvenanceReport,
} from './create-hosting-deployment-provenance.mjs';
import {
  createHostingDeploymentArtifact,
  createHostingDeploymentManifest,
  readStableRegularFile,
  stableJson,
  verifyHostingDeploymentArtifact,
} from './hosting-deployment-artifact.mjs';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const workflow = readFileSync(
  path.join(ROOT, '.github/workflows/hosting-production-deploy.yml'),
  'utf8',
);
const currentWorkflow = readFileSync(
  path.join(ROOT, '.github/workflows/hosting-current-live-observation.yml'),
  'utf8',
);
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const revision = '1'.repeat(40);

const bytesRecord = (filePath, bytes) => ({
  path: filePath,
  bytes: bytes.byteLength,
  sha256: digest(bytes),
  contentBase64: bytes.toString('base64'),
});

function deploymentExecutionFixture({ checkedAt, authenticatedAt }) {
  const admissionCheckFiles = [
    { path: 'bucket.json', bytes: Buffer.from('{"bucket":"observed"}\n') },
    { path: 'objects.json', bytes: Buffer.from('[]\n') },
    { path: 'page-1.json', bytes: Buffer.from('{"items":[]}\n') },
  ];
  const admissionCheckManifest = admissionCheckFiles.map(value => ({
    path: value.path,
    bytes: value.bytes.byteLength,
    sha256: digest(value.bytes),
  }));
  const admissionCheckManifestBytes = Buffer.from(
    `${stableJson(admissionCheckManifest)}\n`,
  );
  const admissionPass = {
    schemaVersion: 1,
    product: 'birthday-autopilot-hosting-admission-pass',
    status: 'passed',
    applicationProjectId: 'birthday-production',
    applicationProjectNumber: '123456789012',
    sourceRevision: revision,
    siteId: 'birthday-production',
    scannedPrefix: 'hosting-production-change-freezes/birthday-production/',
    repository: 'yhsomani/AI-Birthday',
    repositoryId: '123456789',
    repositoryOwnerId: '987654321',
    workflowPath: '.github/workflows/hosting-production-deploy.yml',
    runId: '123456',
    runAttempt: '1',
    buildArtifactId: '7654321',
    buildArtifactDigest: 'd'.repeat(64),
    securityProjectId: 'birthday-release-security',
    securityProjectNumber: '987654321098',
    bucketName: 'birthday-release-admission',
    bucketMetageneration: '7',
    retentionSeconds: 900,
    retentionLocked: true,
    publicAccessPrevention: 'enforced',
    uniformBucketLevelAccess: true,
    versioningEnabled: false,
    softDeleteEnabled: false,
    lifecycleDeleteAgeDays: 1,
    lifecyclePrefix: 'hosting-production-change-freezes/',
    checkedAt,
    pageCount: 1,
    objectCount: 0,
    checkManifestSha256: digest(admissionCheckManifestBytes),
    reader: {
      serviceAccount:
        'hosting-admission-reader@birthday-release-security.iam.gserviceaccount.com',
      workloadIdentityProvider:
        'projects/987654321098/locations/global/workloadIdentityPools/github/providers/hosting-admission-reader',
    },
  };
  return {
    admissionPass,
    admissionPassBytes: Buffer.from(`${stableJson(admissionPass)}\n`),
    admissionCheckManifest,
    admissionCheckManifestBytes,
    admissionCheckFiles,
    deployIdentity: {
      serviceAccount:
        'hosting-deploy@birthday-production.iam.gserviceaccount.com',
      workloadIdentityProvider:
        'projects/123456789012/locations/global/workloadIdentityPools/github/providers/hosting-deploy',
      authenticatedAt,
      buildArtifactId: '7654321',
      buildArtifactDigest: 'd'.repeat(64),
      admissionArtifactId: '7654322',
      admissionArtifactDigest: 'e'.repeat(64),
    },
  };
}

function fixture() {
  const releaseConfig = {
    schemaVersion: 1,
    developerDisplayName: 'Birthday Autopilot Team',
    publicBaseUrl: 'https://birthday.example.co/',
    supportUrl: 'https://support.vendor.org/birthday/',
    recaptchaEnterpriseSiteKey: 'provisioned-recaptcha-enterprise-site-key',
    legalApprovalReference: 'legal-approval-2026-01',
    privacyApprovalReference: 'privacy-approval-2026-01',
    hindiCopyApprovalReference: 'hindi-copy-approval-2026-01',
    adminDeletionRunbookReference: 'admin-deletion-runbook-2026-01',
    verifiedAdminDeletionWorkflowTested: true,
    productionFirebaseDeletionSagaTested: true,
    privacyEffectiveDate: '2026-01-01',
    termsEffectiveDate: '2026-01-01',
  };
  const releaseConfigBytes = Buffer.from(JSON.stringify(releaseConfig));
  const runtimeBytes = Buffer.from(
    `${JSON.stringify(parseReleaseConfig(releaseConfig))}\n`,
  );
  const firebaseConfigBytes = Buffer.from(
    JSON.stringify({
      hosting: {
        public: 'hosting/public',
        predeploy: ['npm --prefix hosting run build:release'],
        ignore: ['firebase.json', '**/.*', '**/node_modules/**'],
        cleanUrls: true,
        trailingSlash: true,
        headers: [],
      },
    }),
  );
  const artifact = createHostingDeploymentArtifact({
    sourceRevision: revision,
    projectId: 'birthday-production',
    siteId: 'birthday-production',
    hostingSourceTreeSha256: '2'.repeat(64),
    firebaseConfigBytes,
    releaseConfigBytes,
    publicFiles: [
      bytesRecord('hosting/public/z-last.txt', Buffer.from('last\n', 'utf8')),
      bytesRecord(
        'hosting/public/a+punctuation.txt',
        Buffer.from('punctuation\n', 'utf8'),
      ),
      bytesRecord('hosting/public/runtime-config.json', runtimeBytes),
    ],
  });
  const artifactBytes = Buffer.from(`${stableJson(artifact)}\n`);
  const manifest = createHostingDeploymentManifest(artifactBytes, artifact);
  const manifestBytes = Buffer.from(`${stableJson(manifest)}\n`);
  const versionName = 'sites/birthday-production/versions/version-20260713';
  const deployResult = {
    status: 'success',
    result: { hosting: versionName },
  };
  const deployResultBytes = Buffer.from(`${JSON.stringify(deployResult)}\n`);
  const input = {
    artifact,
    artifactBytes,
    manifest,
    manifestBytes,
    deployResult,
    deployResultBytes,
    release: {
      name: 'sites/birthday-production/releases/release-20260713',
      version: { name: versionName },
      type: 'DEPLOY',
      releaseTime: '2026-07-13T10:00:20Z',
    },
    version: {
      name: versionName,
      status: 'FINALIZED',
      createTime: '2026-07-13T10:00:02Z',
      finalizeTime: '2026-07-13T10:00:15Z',
      fileCount: '3',
      versionBytes: '4000',
    },
    projectNumber: '123456789012',
    webAppId: '1:123456789012:web:abcdef1234567890',
    siteObservation: {
      name: 'projects/birthday-production/sites/birthday-production',
      defaultUrl: 'https://birthday-production.web.app',
    },
    siteObservationBytes: Buffer.from(
      JSON.stringify({
        name: 'projects/birthday-production/sites/birthday-production',
        defaultUrl: 'https://birthday-production.web.app',
      }),
    ),
    originObservation: {
      name: 'projects/birthday-production/sites/birthday-production/customDomains/birthday.example.co',
      hostState: 'HOST_ACTIVE',
      ownershipState: 'OWNERSHIP_ACTIVE',
      issues: [],
    },
    originObservationBytes: Buffer.from(
      JSON.stringify({
        name: 'projects/birthday-production/sites/birthday-production/customDomains/birthday.example.co',
        hostState: 'HOST_ACTIVE',
        ownershipState: 'OWNERSHIP_ACTIVE',
        issues: [],
      }),
    ),
    webConfig: {
      projectId: 'birthday-production',
      messagingSenderId: '123456789012',
      appId: '1:123456789012:web:abcdef1234567890',
    },
    webConfigBytes: Buffer.from(
      JSON.stringify({
        projectId: 'birthday-production',
        messagingSenderId: '123456789012',
        appId: '1:123456789012:web:abcdef1234567890',
      }),
    ),
    ...deploymentExecutionFixture({
      checkedAt: '2026-07-13T09:59:50Z',
      authenticatedAt: '2026-07-13T09:59:55Z',
    }),
    builder: {
      repository: 'yhsomani/AI-Birthday',
      workflowPath: '.github/workflows/hosting-production-deploy.yml',
      runId: '123456',
      runAttempt: '1',
      deployStartedAt: '2026-07-13T10:00:00Z',
      deployCompletedAt: '2026-07-13T10:00:30Z',
    },
  };
  return input;
}

function currentFixture(channelQualified = false) {
  const deployed = fixture();
  const liveChannel = {
    name: 'sites/birthday-production/channels/live',
    release: {
      ...deployed.release,
      name: channelQualified
        ? 'sites/birthday-production/channels/live/releases/release-20260713'
        : deployed.release.name,
    },
  };
  const builder = {
    repository: 'yhsomani/AI-Birthday',
    workflowPath: '.github/workflows/hosting-current-live-observation.yml',
    runId: '123457',
    runAttempt: '1',
  };
  const admissionBucket = {
    name: 'birthday-release-admission',
    projectNumber: '987654321098',
    metageneration: '7',
    retentionPolicy: {
      retentionPeriod: '900',
      effectiveTime: '2026-07-01T00:00:00Z',
      isLocked: true,
    },
    iamConfiguration: {
      publicAccessPrevention: 'enforced',
      uniformBucketLevelAccess: { enabled: true },
    },
    lifecycle: {
      rule: [
        {
          action: { type: 'Delete' },
          condition: {
            age: 1,
            matchesPrefix: ['hosting-production-change-freezes/'],
          },
        },
      ],
    },
  };
  const admissionContentBytes = Buffer.from(
    stableJson({
      schemaVersion: 1,
      siteId: deployed.artifact.siteId,
      sourceRevision: revision,
      runId: builder.runId,
      runAttempt: builder.runAttempt,
      validUntil: '2026-07-13T10:20:00.000Z',
    }),
  );
  const admissionObject = {
    bucket: admissionBucket.name,
    name: `hosting-production-change-freezes/${deployed.artifact.siteId}/${revision}/${builder.runId}/${builder.runAttempt}.json`,
    generation: '1752401101000000',
    metageneration: '1',
    contentType: 'application/json',
    size: String(admissionContentBytes.byteLength),
    timeCreated: '2026-07-13T10:05:01Z',
    retentionExpirationTime: '2026-07-13T10:20:01Z',
  };
  return {
    sourceRevision: revision,
    projectId: deployed.artifact.projectId,
    projectNumber: deployed.projectNumber,
    admissionSecurityProjectId: 'birthday-release-security',
    webAppId: deployed.webAppId,
    siteId: deployed.artifact.siteId,
    publicBaseUrl: deployed.artifact.publicBaseUrl,
    capturedAt: '2026-07-13T10:05:00Z',
    siteObservation: deployed.siteObservation,
    siteObservationBytes: deployed.siteObservationBytes,
    originObservation: deployed.originObservation,
    originObservationBytes: deployed.originObservationBytes,
    webConfig: deployed.webConfig,
    webConfigBytes: deployed.webConfigBytes,
    liveChannel,
    liveChannelBytes: Buffer.from(JSON.stringify(liveChannel)),
    version: deployed.version,
    versionBytes: Buffer.from(JSON.stringify(deployed.version)),
    admissionBucket,
    admissionBucketBytes: Buffer.from(JSON.stringify(admissionBucket)),
    admissionObject,
    admissionObjectBytes: Buffer.from(JSON.stringify(admissionObject)),
    admissionContentBytes,
    executionIdentity: {
      serviceAccount:
        'hosting-current-live@birthday-production.iam.gserviceaccount.com',
      workloadIdentityProvider:
        'projects/123456789012/locations/global/workloadIdentityPools/github/providers/hosting-current-live',
      repositoryId: '123456789',
      repositoryOwnerId: '987654321',
    },
    builder,
  };
}

test('canonical Hosting artifact is deterministic and uses code-point file ordering', () => {
  const first = fixture();
  const second = fixture();
  assert.deepEqual(first.artifact, second.artifact);
  assert.deepEqual(
    first.artifact.files.map(file => file.path),
    [
      'hosting/public/a+punctuation.txt',
      'hosting/public/runtime-config.json',
      'hosting/public/z-last.txt',
    ],
  );
  assert.equal(verifyHostingDeploymentArtifact(first.artifact), first.artifact);
  assert.equal(first.manifest.artifactSha256, digest(first.artifactBytes));
});

test('artifact verifier rejects content, inventory, and deployment-target tampering', () => {
  const { artifact } = fixture();
  const changedContent = structuredClone(artifact);
  changedContent.files[0].contentBase64 =
    Buffer.from('changed').toString('base64');
  assert.throws(
    () => verifyHostingDeploymentArtifact(changedContent),
    /file bytes differ/u,
  );

  const changedTarget = structuredClone(artifact);
  changedTarget.siteId = 'other-production';
  assert.throws(
    () => verifyHostingDeploymentArtifact(changedTarget),
    /configuration target|manifest digest/u,
  );

  const reversed = structuredClone(artifact);
  reversed.files.reverse();
  assert.throws(
    () => verifyHostingDeploymentArtifact(reversed),
    /not canonical/u,
  );
});

test('provenance binds the Firebase CLI-created version to the observed release', () => {
  const input = fixture();
  const report = createHostingDeploymentProvenanceReport(input);
  assert.equal(
    report.deployment.firebaseDeployVersionName,
    report.deployment.versionName,
  );
  assert.equal(
    report.execution.admissionCheck.rawRootBytes,
    input.admissionCheckFiles.reduce(
      (total, value) => total + value.bytes.byteLength,
      0,
    ),
  );
  assert.equal(
    report.execution.deployer.serviceAccount,
    'hosting-deploy@birthday-production.iam.gserviceaccount.com',
  );
  assert.equal(verifyHostingDeploymentProvenanceReport(report), report);

  const unrelated = structuredClone(input);
  unrelated.deployResult.result.hosting =
    'sites/birthday-production/versions/unrelated-version';
  unrelated.deployResultBytes = Buffer.from(
    `${JSON.stringify(unrelated.deployResult)}\n`,
  );
  assert.throws(
    () => createHostingDeploymentProvenanceReport(unrelated),
    /finalized deploy/u,
  );

  const changedRaw = fixture();
  changedRaw.admissionCheckFiles[0].bytes = Buffer.from('changed');
  assert.throws(
    () => createHostingDeploymentProvenanceReport(changedRaw),
    /manifest/u,
  );

  const wrongScope = fixture();
  wrongScope.admissionPass.siteId = 'other-production';
  wrongScope.admissionPassBytes = Buffer.from(
    `${stableJson(wrongScope.admissionPass)}\n`,
  );
  assert.throws(
    () => createHostingDeploymentProvenanceReport(wrongScope),
    /identities|PASS/u,
  );
});

test('retained provenance revalidates builder, resource, size, and operation window', () => {
  const report = createHostingDeploymentProvenanceReport(fixture());
  for (const mutate of [
    value => {
      value.builder.repository = 'attacker/fork';
    },
    value => {
      value.deployment.releaseId = 'other';
    },
    value => {
      value.deployment.fileCount = '0';
    },
    value => {
      value.builder.deployCompletedAt = '2026-07-13T09:00:00Z';
    },
    value => {
      value.publicBaseUrl = 'http://birthday.example.co/';
    },
  ]) {
    const changed = structuredClone(report);
    mutate(changed);
    assert.throws(() => verifyHostingDeploymentProvenanceReport(changed));
  }
});

test('provider origin and Firebase web app require active owned matching state', () => {
  const valid = fixture();
  assert.doesNotThrow(() => createHostingDeploymentProvenanceReport(valid));
  for (const mutate of [
    value => {
      value.originObservation.name =
        'projects/birthday-production/sites/other/customDomains/birthday.example.co';
    },
    value => {
      value.originObservation.hostState = 'HOST_MISMATCH';
    },
    value => {
      value.originObservation.ownershipState = 'OWNERSHIP_MISSING';
    },
    value => {
      value.webConfig.appId = '1:123456789012:web:ffffffffffffffff';
    },
  ]) {
    const changed = structuredClone(valid);
    mutate(changed);
    assert.throws(() => createHostingDeploymentProvenanceReport(changed));
  }
});

test('current-live observation accepts both release names and expires in 15 minutes', () => {
  for (const channelQualified of [false, true]) {
    const report = createHostingCurrentLiveObservation(
      currentFixture(channelQualified),
    );
    assert.equal(report.live.versionId, 'version-20260713');
    assert.equal(
      report.admissionLease.bucketName,
      'birthday-release-admission',
    );
    assert.equal(report.admissionLease.securityProjectNumber, '987654321098');
    assert.deepEqual(JSON.parse(report.admissionLease.value), {
      runAttempt: '1',
      runId: '123457',
      schemaVersion: 1,
      siteId: 'birthday-production',
      sourceRevision: revision,
      validUntil: '2026-07-13T10:20:00.000Z',
    });
    assert.equal(
      verifyHostingCurrentLiveObservation(
        report,
        Date.parse('2026-07-13T10:10:00Z'),
      ),
      report,
    );
    assert.throws(
      () =>
        verifyHostingCurrentLiveObservation(
          report,
          Date.parse('2026-07-13T10:20:00Z'),
        ),
      /stale/u,
    );
    const mismatchedLease = structuredClone(report);
    mismatchedLease.builder.runId = '999999';
    assert.throws(
      () =>
        verifyHostingCurrentLiveObservation(
          mismatchedLease,
          Date.parse('2026-07-13T10:10:00Z'),
        ),
      /invalid|lease/u,
    );
  }
});

test('current-live admission rejects unlocked, in-project, overlong, or noncanonical leases', () => {
  for (const mutate of [
    value => {
      value.admissionBucket.retentionPolicy.isLocked = false;
    },
    value => {
      value.admissionBucket.projectNumber = value.projectNumber;
    },
    value => {
      value.admissionBucket.retentionPolicy.retentionPeriod = '901';
    },
    value => {
      value.admissionBucket.iamConfiguration.publicAccessPrevention =
        'inherited';
    },
  ]) {
    const changed = currentFixture();
    mutate(changed);
    changed.admissionBucketBytes = Buffer.from(
      JSON.stringify(changed.admissionBucket),
    );
    assert.throws(() => createHostingCurrentLiveObservation(changed));
  }

  const noncanonical = currentFixture();
  noncanonical.admissionContentBytes = Buffer.from(
    `${noncanonical.admissionContentBytes.toString('utf8')}\n`,
  );
  assert.throws(
    () => createHostingCurrentLiveObservation(noncanonical),
    /canonical/u,
  );
});

test('stable input reader rejects symlinks and hard links', () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'hosting-artifact-'));
  try {
    const regular = path.join(directory, 'regular.json');
    const symbolic = path.join(directory, 'symbolic.json');
    const hard = path.join(directory, 'hard.json');
    writeFileSync(regular, '{}\n');
    symlinkSync(regular, symbolic);
    linkSync(regular, hard);
    assert.throws(() => readStableRegularFile(symbolic), /non-linked/u);
    assert.throws(() => readStableRegularFile(regular), /non-linked/u);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test('Hosting deployment workflow is manual, protected, keyless, and pinned', () => {
  assert.match(workflow, /on:\n {2}workflow_dispatch:/u);
  assert.match(workflow, /environment: hosting-production-deploy/u);
  assert.match(workflow, /environment: hosting-production-build/u);
  assert.match(workflow, /environment: hosting-production-admission/u);
  assert.match(
    workflow,
    /build:[\s\S]*permissions:\n {6}contents: read\n {6}id-token: none/u,
  );
  assert.match(workflow, /test -z "\$\{ACTIONS_ID_TOKEN_REQUEST_URL:-\}"/u);
  assert.equal(
    (workflow.match(/test "\$GITHUB_REF" = 'refs\/heads\/main'/gu) ?? [])
      .length,
    3,
  );
  assert.equal(
    (
      workflow.match(
        /git merge-base --is-ancestor HEAD refs\/remotes\/origin\/main/gu,
      ) ?? []
    ).length,
    2,
  );
  assert.match(
    workflow,
    /admission:[\s\S]*permissions:\n {6}actions: read\n {6}id-token: write/u,
  );
  assert.match(workflow, /HOSTING_ADMISSION_SECURITY_PROJECT_NUMBER/u);
  assert.match(workflow, /HOSTING_ADMISSION_BUCKET/u);
  assert.match(workflow, /HOSTING_ADMISSION_READER_WIF_PROVIDER/u);
  assert.match(workflow, /HOSTING_ADMISSION_READER_SERVICE_ACCOUNT/u);
  assert.match(workflow, /retentionPolicy\.isLocked == true/u);
  assert.match(workflow, /retentionPeriod \| tostring\) == "900"/u);
  assert.match(workflow, /versions=true&maxResults=1000/u);
  assert.match(workflow, /GCS admission lease is active/u);
  assert.match(workflow, /--admission-check-root/u);
  assert.match(workflow, /object-\$\{object_key\}-metadata\.json/u);
  assert.doesNotMatch(workflow, /object-\$\{generation\}\.(?:json|lease)/u);
  assert.doesNotMatch(workflow, /HOSTING_FREEZE_VARIABLE|gh api/u);
  assert.match(
    workflow,
    /actions\/checkout@df4cb1c069e1874edd31b4311f1884172cec0e10 # v6/u,
  );
  assert.match(
    workflow,
    /google-github-actions\/auth@7c6bc770dae815cd3e89ee6cdf493a5fab2cc093 # v3\.0\.0/u,
  );
  assert.match(workflow, /firebase --version\)" = 15\.23\.0/u);
  assert.match(workflow, /version: 575\.0\.1/u);
  assert.doesNotMatch(workflow, /credentials_json|SERVICE_ACCOUNT_KEY/iu);
});

test('workflow deploys only extracted canonical bytes and captures exact version provenance', () => {
  const installIndex = workflow.indexOf('npm ci --prefix backend/hosting');
  const protectedConfigIndex = workflow.indexOf(
    'HOSTING_RELEASE_CONFIG_BASE64: ${{ secrets.',
  );
  const extractIndex = workflow.indexOf('--mode extract');
  const readerAuthIndex = workflow.indexOf(
    'Authenticate only as the external admission reader',
  );
  const admissionGateIndex = workflow.indexOf(
    'Fail closed on every retained Hosting admission lease',
  );
  const deployAuthIndex = workflow.indexOf(
    'Authenticate as the sole Hosting-mutating identity',
  );
  const deployIndex = workflow.indexOf('firebase deploy');
  assert.ok(installIndex >= 0 && installIndex < protectedConfigIndex);
  assert.ok(extractIndex >= 0 && extractIndex < deployIndex);
  assert.ok(
    extractIndex < readerAuthIndex &&
      readerAuthIndex < admissionGateIndex &&
      admissionGateIndex < deployAuthIndex &&
      deployAuthIndex < deployIndex,
  );
  const staleAttemptCheckIndex = workflow.indexOf(
    'Reject reused admission output before any artifact or source action',
  );
  const deployDownloadIndex = workflow.indexOf(
    'Download exact build and admission artifacts from this run',
  );
  const deployCheckoutIndex = workflow.indexOf(
    'Check out exact source only after admission PASS',
  );
  assert.ok(
    staleAttemptCheckIndex < deployDownloadIndex &&
      deployDownloadIndex < deployCheckoutIndex &&
      deployCheckoutIndex < deployAuthIndex,
  );
  assert.match(workflow, /test "\$PASS_RUN_ATTEMPT" = "\$GITHUB_RUN_ATTEMPT"/u);
  assert.match(workflow, /needs: \[build, admission\]/u);
  assert.equal(
    (workflow.match(/google-github-actions\/auth@/gu) ?? []).length,
    2,
  );
  const admissionJob = workflow.slice(
    workflow.indexOf('  admission:'),
    workflow.indexOf('  deploy:'),
  );
  assert.doesNotMatch(
    admissionJob,
    /actions\/checkout@|\bnode\b|\bnpm\b|hosting-deployment-artifact\.mjs/u,
  );
  assert.match(admissionJob, /environment: hosting-production-admission/u);
  assert.equal((workflow.match(/firebase deploy/g) ?? []).length, 1);
  assert.match(
    workflow,
    /--config "\$RUNNER_TEMP\/hosting-deploy-input\/firebase\.json"/u,
  );
  assert.match(workflow, /--only hosting/u);
  assert.match(workflow, /--message "\$message"/u);
  assert.match(workflow, /releases-before\.json/u);
  assert.match(workflow, /expected exactly one new workflow release/u);
  assert.match(workflow, /\.version\.name/u);
  assert.match(workflow, /--deployment-window/u);
  assert.match(workflow, /hosting-deployment-provenance\.json/u);
  assert.match(workflow, /compression-level: 0/u);
  assert.match(workflow, /retention-days: 90/u);
});

test('current-live workflow is protected, keyless, provider-bound, and creates only an immutable admission object', () => {
  const sharedConcurrency =
    'group: hosting-production-live-state-${{ inputs.hosting_site_id }}';
  assert.ok(workflow.includes(sharedConcurrency));
  assert.ok(currentWorkflow.includes(sharedConcurrency));
  assert.match(
    currentWorkflow,
    /environment: hosting-production-readonly-live/u,
  );
  assert.match(currentWorkflow, /HOSTING_ADMISSION_SECURITY_PROJECT_NUMBER/u);
  assert.match(currentWorkflow, /HOSTING_AUDIT_WIF_PROVIDER/u);
  assert.match(currentWorkflow, /test "\$GITHUB_REF" = 'refs\/heads\/main'/u);
  assert.match(
    currentWorkflow,
    /git merge-base --is-ancestor HEAD refs\/remotes\/origin\/main/u,
  );
  assert.match(currentWorkflow, /HOSTING_ADMISSION_BUCKET/u);
  assert.match(currentWorkflow, /id-token: write/u);
  assert.match(
    currentWorkflow,
    /sites\/\$\{HOSTING_SITE_ID\}\/channels\/live/u,
  );
  assert.match(currentWorkflow, /customDomains\/\$\{public_hostname\}/u);
  assert.match(currentWorkflow, /HOST_ACTIVE/u);
  assert.match(currentWorkflow, /OWNERSHIP_ACTIVE/u);
  assert.match(currentWorkflow, /__\/firebase\/init\.json/u);
  assert.doesNotMatch(
    currentWorkflow,
    /firebase deploy|firebasehosting\.googleapis\.com[\s\S]{0,300}request = "(?:POST|PATCH|DELETE|PUT)"/u,
  );
  assert.match(currentWorkflow, /uploadType=media&ifGenerationMatch=0&name=/u);
  assert.match(currentWorkflow, /retentionPolicy\.isLocked == true/u);
  assert.match(currentWorkflow, /hosting-admission-lease-readback\.json/u);
  assert.match(currentWorkflow, /cmp .*hosting-admission-lease\.json/u);
  assert.doesNotMatch(
    currentWorkflow,
    /HOSTING_FREEZE_VARIABLE|gh api|storage\.objects\.(?:delete|update)/u,
  );
  assert.match(currentWorkflow, /retention-days: 14/u);
  assert.match(currentWorkflow, /timeout-minutes: 30/u);
  assert.match(currentWorkflow, /id: upload-current-live/u);
  assert.match(
    currentWorkflow,
    /steps\.upload-current-live\.outputs\.artifact-id/u,
  );
  assert.match(
    currentWorkflow,
    /Hold the canonical Hosting deployment freeze through evidence expiry[\s\S]*\.validUntil[\s\S]*while true[\s\S]*sleep/u,
  );
});
