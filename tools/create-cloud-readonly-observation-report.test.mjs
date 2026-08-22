import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import {
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  createCloudReadonlyObservationReport,
  validateCloudReadonlyObservationReport,
  verifyCloudReadonlyArchive,
} from './create-cloud-readonly-observation-report.mjs';
import { createEvidenceManifest } from './create-evidence-manifest.mjs';
import {
  collectCloudEvidenceFiles,
  loadCloudReadonlyObservationReport,
} from './validate-cloud-release-evidence.mjs';

const revision = 'a'.repeat(40);
const projectId = 'birthday-prod-12345';
const projectNumber = '123456789012';
const androidAppId = `1:${projectNumber}:android:abcdef1234567890`;
const iosAppId = `1:${projectNumber}:ios:abcdef1234567890`;
const webAppId = `1:${projectNumber}:web:abcdef1234567890`;
const siteId = 'birthday-prod-site';
const runtime = `birthday-runtime@${projectId}.iam.gserviceaccount.com`;
const audit = `birthday-audit@${projectId}.iam.gserviceaccount.com`;
const securityProjectId = 'birthday-release-security';
const securityProjectNumber = '987654321098';
const observer = `hosting-observer@${projectId}.iam.gserviceaccount.com`;
const reader = `admission-reader@${securityProjectId}.iam.gserviceaccount.com`;
const deployer = `hosting-deploy@${projectId}.iam.gserviceaccount.com`;
const observerProvider = `projects/${projectNumber}/locations/global/workloadIdentityPools/hosting-observer-pool/providers/github-main`;
const readerProvider = `projects/${securityProjectNumber}/locations/global/workloadIdentityPools/admission-reader-pool/providers/github-main`;
const deployProvider = `projects/${projectNumber}/locations/global/workloadIdentityPools/hosting-deploy-pool/providers/github-main`;
const admissionBucket = 'birthday-release-admission';
const repositoryId = '24681012';
const repositoryOwnerId = '1357911';
const observedAt = '2026-07-12T10:00:00Z';
const releaseAdmissionCheck = 'Release admission for exact source SHA';
const githubActionsAppId = 15368;
const ciCheckRunId = 555555555;
const ciCheckSuiteId = 666666666;
const ciWorkflowRunId = 777777777;

const condition = (workflowPath, environment) =>
  `assertion.repository=='yhsomani/AI-Birthday' && assertion.repository_id=='${repositoryId}' && assertion.repository_owner_id=='${repositoryOwnerId}' && assertion.workflow_ref=='yhsomani/AI-Birthday/${workflowPath}@refs/heads/main' && assertion.ref=='refs/heads/main' && assertion.sub=='repo:yhsomani/AI-Birthday:environment:${environment}'`;
const mapping = {
  'google.subject': 'assertion.sub',
  'attribute.repository': 'assertion.repository',
  'attribute.repository_id': 'assertion.repository_id',
  'attribute.repository_owner_id': 'assertion.repository_owner_id',
  'attribute.workflow_ref': 'assertion.workflow_ref',
  'attribute.ref': 'assertion.ref',
};
const analysis = (entries, scope = 'organizations/555555555555') => ({
  scope,
  response: {
    fullyExplored: true,
    mainAnalysis: {
      analysisQuery: { scope },
      fullyExplored: true,
      analysisResults: entries.map(({ member, permissions }) => ({
        iamBinding: { role: 'roles/custom.observed', members: [member] },
        identityList: { identities: [{ name: member }] },
        accessControlLists: [
          { accesses: permissions.map(permission => ({ permission })) },
        ],
        fullyExplored: true,
      })),
    },
  },
});

const tarHeader = ({ name, content = Buffer.alloc(0), directory = false }) => {
  const header = Buffer.alloc(512);
  const writeText = (value, offset, length) => {
    const bytes = Buffer.from(value, 'ascii');
    assert.ok(bytes.byteLength <= length);
    bytes.copy(header, offset);
  };
  const writeOctal = (value, offset, length) =>
    writeText(
      `${value.toString(8).padStart(length - 1, '0')}\0`,
      offset,
      length,
    );
  writeText(name, 0, 100);
  writeOctal(directory ? 0o700 : 0o600, 100, 8);
  writeOctal(0, 108, 8);
  writeOctal(0, 116, 8);
  writeOctal(content.byteLength, 124, 12);
  writeOctal(0, 136, 12);
  header.fill(0x20, 148, 156);
  header[156] = directory ? 0x35 : 0x30;
  writeText('ustar\0', 257, 6);
  writeText('00', 263, 2);
  const checksum = header.reduce((sum, byte) => sum + byte, 0);
  writeText(`${checksum.toString(8).padStart(6, '0')}\0 `, 148, 8);
  const padding = Buffer.alloc(
    Math.ceil(content.byteLength / 512) * 512 - content.byteLength,
  );
  return Buffer.concat([header, content, padding]);
};

const ustar = entries =>
  Buffer.concat([...entries.map(tarHeader), Buffer.alloc(1024)]);

test('CI and cloud workflows retain the exact-source release-admission chain', () => {
  const ci = readFileSync(
    new URL('../.github/workflows/ci.yml', import.meta.url),
    'utf8',
  );
  const aggregateStart = ci.indexOf('\n  release-admission:\n');
  assert.ok(aggregateStart > 0);
  const aggregate = ci.slice(aggregateStart);
  assert.match(aggregate, /name: Release admission for exact source SHA/u);
  assert.match(aggregate, /if: \$\{\{ always\(\) \}\}/u);
  for (const dependency of [
    'history-secrets',
    'backend-node22',
    'android-instrumentation',
    'android-device-e2e',
    'quality-and-android',
  ]) {
    assert.match(aggregate, new RegExp(`^      - ${dependency}$`, 'mu'));
  }

  assert.match(aggregate, /jq -e 'all\(\.\[\]; \.result == "success"\)'/u);

  const cloud = readFileSync(
    new URL(
      '../.github/workflows/cloud-readonly-evidence.yml',
      import.meta.url,
    ),
    'utf8',
  );
  assert.match(cloud, /permission-actions: read/u);
  assert.match(cloud, /permission-checks: read/u);
  assert.match(
    cloud,
    /commits\/\$EXPECTED_SOURCE_REVISION\/check-runs\?filter=latest&per_page=100/u,
  );
  assert.match(
    cloud,
    /actions\/workflows\/ci\.yml\/runs\?branch=main&event=push&head_sha=\$EXPECTED_SOURCE_REVISION&status=success&per_page=100/u,
  );

  const schema = JSON.parse(
    readFileSync(
      new URL(
        './cloud-readonly-observation-report.schema.json',
        import.meta.url,
      ),
      'utf8',
    ),
  );
  assert.ok(schema.required.includes('hostingReleaseControl'));
  assert.equal(
    schema.$defs.sourceCi.properties.aggregateCheckName.const,
    releaseAdmissionCheck,
  );
  assert.equal(
    schema.$defs.sourceCi.properties.workflowPath.const,
    '.github/workflows/ci.yml',
  );
  assert.equal(schema.$defs.sourceCi.properties.conclusion.const, 'success');
});

const fixture = t => {
  const root = mkdtempSync(
    path.join(tmpdir(), 'birthday-cloud-readonly-report-'),
  );
  t.after(() => rmSync(root, { force: true, recursive: true }));
  const rawRoot = path.join(
    root,
    'release-evidence/cloud-production-readonly/raw',
  );
  mkdirSync(rawRoot, { recursive: true });
  const writeJson = (name, value) =>
    writeFileSync(path.join(rawRoot, name), `${JSON.stringify(value)}\n`);
  writeJson('workflow-context.json', {
    schemaVersion: 1,
    sourceRevision: revision,
    projectId,
    projectNumber,
    androidAppId,
    iosAppId,
    webAppId,
    runtimeServiceAccount: runtime,
    auditServiceAccount: audit,
    hostingSiteId: siteId,
    loggingLocation: 'asia-south1',
    releaseSecurityProjectId: securityProjectId,
    releaseSecurityProjectNumber: securityProjectNumber,
    applicationIamAnalysisScope: 'organizations/555555555555',
    releaseSecurityIamAnalysisScope: 'organizations/555555555555',
    admissionBucketName: admissionBucket,
    hostingObserverServiceAccount: observer,
    hostingObserverWifProvider: observerProvider,
    admissionReaderServiceAccount: reader,
    admissionReaderWifProvider: readerProvider,
    hostingDeployServiceAccount: deployer,
    hostingDeployWifProvider: deployProvider,
    releaseSecurityLogSinkName: 'admission-audit-sink',
    releaseSecurityLogBucketName: 'admission-audit',
    releaseSecurityLoggingLocation: 'global',
    retainedProjectAssignment: 'production',
    repository: 'yhsomani/AI-Birthday',
    repositoryId,
    repositoryOwnerId,
    runId: '123456789',
    runAttempt: '1',
    workflowRef:
      'yhsomani/AI-Birthday/.github/workflows/cloud-readonly-evidence.yml@refs/heads/main',
    workflowRun:
      'https://github.com/yhsomani/AI-Birthday/actions/runs/123456789',
    observedAt,
    mutationAuthorized: false,
  });
  writeJson('github-repository.json', {
    id: repositoryId,
    full_name: 'yhsomani/AI-Birthday',
    owner: { id: repositoryOwnerId, login: 'yhsomani', type: 'Organization' },
  });
  writeJson('github-main-branch-protection.json', {
    enforce_admins: { enabled: true },
    required_status_checks: {
      strict: true,
      contexts: [releaseAdmissionCheck],
      checks: [{ context: releaseAdmissionCheck, app_id: githubActionsAppId }],
    },
    required_pull_request_reviews: {
      dismiss_stale_reviews: true,
      require_code_owner_reviews: true,
      require_last_push_approval: true,
      required_approving_review_count: 1,
      bypass_pull_request_allowances: { users: [], teams: [], apps: [] },
    },
    allow_force_pushes: { enabled: false },
    allow_deletions: { enabled: false },
  });
  writeJson('github-release-source-check-runs.json', {
    total_count: 1,
    check_runs: [
      {
        id: ciCheckRunId,
        name: releaseAdmissionCheck,
        head_sha: revision,
        url: `https://api.github.com/repos/yhsomani/AI-Birthday/check-runs/${ciCheckRunId}`,
        html_url: `https://github.com/yhsomani/AI-Birthday/actions/runs/${ciWorkflowRunId}/job/${ciCheckRunId}`,
        status: 'completed',
        conclusion: 'success',
        check_suite: { id: ciCheckSuiteId },
        app: {
          id: githubActionsAppId,
          slug: 'github-actions',
          html_url: 'https://github.com/apps/github-actions',
          owner: { login: 'github', type: 'Organization' },
        },
      },
    ],
  });
  writeJson('github-release-source-ci-runs.json', {
    total_count: 1,
    workflow_runs: [
      {
        id: ciWorkflowRunId,
        name: 'CI',
        path: '.github/workflows/ci.yml@main',
        head_sha: revision,
        head_branch: 'main',
        event: 'push',
        status: 'completed',
        conclusion: 'success',
        check_suite_id: ciCheckSuiteId,
        run_attempt: 1,
        url: `https://api.github.com/repos/yhsomani/AI-Birthday/actions/runs/${ciWorkflowRunId}`,
        html_url: `https://github.com/yhsomani/AI-Birthday/actions/runs/${ciWorkflowRunId}`,
        repository: {
          id: repositoryId,
          full_name: 'yhsomani/AI-Birthday',
          owner: { id: repositoryOwnerId },
        },
      },
    ],
  });
  const governedEnvironments = [
    'cloud-production-readonly-audit',
    'hosting-production-readonly-live',
    'hosting-production-build',
    'hosting-production-admission',
    'hosting-production-deploy',
  ];
  const governanceAudit = [];
  for (const [index, environment] of governedEnvironments.entries()) {
    const environmentId = String(1001 + index);
    writeJson(`github-environment-${environment}.json`, {
      id: environmentId,
      name: environment,
      protection_rules: [
        {
          id: 2001 + index,
          type: 'required_reviewers',
          prevent_self_review: true,
          reviewers: [
            { type: 'Team', reviewer: { id: 424242, slug: 'release' } },
          ],
        },
        { id: 3001 + index, type: 'branch_policy' },
      ],
      deployment_branch_policy: {
        protected_branches: false,
        custom_branch_policies: true,
      },
    });
    writeJson(`github-environment-${environment}-branch-policies.json`, {
      total_count: 1,
      branch_policies: [{ id: 4001 + index, name: 'main' }],
    });
    governanceAudit.push({
      '@timestamp': 1_752_000_000_000 + index,
      _document_id: `environment-event-${index + 1}`,
      action: 'environment.update_protection_rule',
      org: 'yhsomani',
      org_id: repositoryOwnerId,
      repo: 'yhsomani/AI-Birthday',
      repo_id: repositoryId,
      environment_id: environmentId,
      environment_name: environment,
      can_admins_bypass: false,
      prevent_self_review: true,
      approvers: [424242],
    });
  }
  writeJson('github-environment-audit-log.json', governanceAudit);
  writeJson('project.json', {
    name: 'WishWell Production',
    projectId,
    projectNumber,
  });
  writeJson('runtime-service-account.json', { email: runtime });
  writeJson('audit-service-account.json', { email: audit });
  writeJson('release-security-project.json', {
    projectId: securityProjectId,
    projectNumber: securityProjectNumber,
  });
  writeJson('application-project-ancestors.json', [
    { type: 'project', id: projectNumber },
    { type: 'folder', id: '666666666666' },
    { type: 'organization', id: '555555555555' },
  ]);
  writeJson('release-security-project-ancestors.json', [
    { type: 'project', id: securityProjectNumber },
    { type: 'folder', id: '777777777777' },
    { type: 'organization', id: '555555555555' },
  ]);
  writeJson('release-security-ancestor-iam-policies.json', [
    {
      type: 'folder',
      id: '777777777777',
      policy: { bindings: [], auditConfigs: [] },
    },
    {
      type: 'organization',
      id: '555555555555',
      policy: { bindings: [], auditConfigs: [] },
    },
  ]);
  writeJson('application-cross-project-sa-org-policy.json', {
    spec: { rules: [{ enforce: true }] },
  });
  writeJson('release-security-cross-project-sa-org-policy.json', {
    spec: { rules: [{ enforce: true }] },
  });
  writeJson('application-resource-assets.json', []);
  writeJson('release-security-resource-assets.json', []);
  const identities = [
    {
      prefix: 'hosting-observer',
      account: observer,
      provider: observerProvider,
      workflowPath: '.github/workflows/hosting-current-live-observation.yml',
      environment: 'hosting-production-readonly-live',
    },
    {
      prefix: 'admission-reader',
      account: reader,
      provider: readerProvider,
      workflowPath: '.github/workflows/hosting-production-deploy.yml',
      environment: 'hosting-production-admission',
    },
    {
      prefix: 'hosting-deploy',
      account: deployer,
      provider: deployProvider,
      workflowPath: '.github/workflows/hosting-production-deploy.yml',
      environment: 'hosting-production-deploy',
    },
  ];
  for (const identity of identities) {
    writeJson(`${identity.prefix}-service-account.json`, {
      email: identity.account,
    });
    writeJson(`${identity.prefix}-user-managed-keys.json`, []);
    const poolResource = identity.provider.split('/providers/')[0];
    writeJson(`${identity.prefix}-wif-pool.json`, {
      name: poolResource,
      state: 'ACTIVE',
    });
    writeJson(`${identity.prefix}-wif-providers.json`, [
      { name: identity.provider, state: 'ACTIVE' },
    ]);
    const subject = `repo:yhsomani/AI-Birthday:environment:${identity.environment}`;
    const pool = identity.provider.split('/providers/')[0];
    writeJson(`${identity.prefix}-service-account-iam.json`, {
      bindings: [
        {
          role: 'roles/iam.workloadIdentityUser',
          members: [
            `principal://iam.googleapis.com/${pool}/subject/${subject}`,
          ],
        },
      ],
    });
    writeJson(
      `${identity.prefix}-impersonation-access-analysis.json`,
      analysis([
        {
          member: `principal://iam.googleapis.com/${pool}/subject/${subject}`,
          permissions: [
            'iam.serviceAccounts.getAccessToken',
            'iam.serviceAccounts.getOpenIdToken',
          ],
        },
      ]),
    );
    writeJson(`${identity.prefix}-wif-provider.json`, {
      name: identity.provider,
      state: 'ACTIVE',
      oidc: { issuerUri: 'https://token.actions.githubusercontent.com' },
      attributeMapping: mapping,
      attributeCondition: condition(
        identity.workflowPath,
        identity.environment,
      ),
    });
  }
  writeJson('application-project-buckets.json', [
    {
      name: 'gcf-v2-sources-123456789012-asia-south1',
      projectNumber,
    },
  ]);
  writeJson('firebase-project.json', {
    projectId,
    projectNumber,
    resources: { hostingSite: siteId },
  });
  writeJson('application-project-iam.json', { bindings: [] });
  writeJson('release-security-buckets.json', [
    { name: admissionBucket, projectNumber: securityProjectNumber },
  ]);
  writeJson('admission-bucket.json', {
    name: admissionBucket,
    projectNumber: securityProjectNumber,
    metageneration: '7',
    iamConfiguration: {
      publicAccessPrevention: 'enforced',
      uniformBucketLevelAccess: { enabled: true },
    },
    versioning: { enabled: false },
    softDeletePolicy: { retentionDurationSeconds: '0' },
    retentionPolicy: { retentionPeriod: '900', isLocked: true },
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
  });
  writeJson('admission-bucket-iam.json', { bindings: [] });
  writeJson(
    'admission-bucket-access-analysis.json',
    analysis([
      {
        member: `serviceAccount:${observer}`,
        permissions: [
          'storage.buckets.get',
          'storage.objects.create',
          'storage.objects.get',
        ],
      },
      {
        member: `serviceAccount:${reader}`,
        permissions: [
          'storage.buckets.get',
          'storage.objects.get',
          'storage.objects.list',
        ],
      },
    ]),
  );
  const hostingPermissions = [
    'firebasehosting.sites.create',
    'firebasehosting.sites.delete',
    'firebasehosting.sites.update',
  ];
  writeJson(
    'hosting-mutation-access-analysis.json',
    analysis([
      {
        member: `serviceAccount:${deployer}`,
        permissions: hostingPermissions,
      },
    ]),
  );
  writeJson('release-security-project-iam.json', {
    bindings: [],
    auditConfigs: [
      {
        service: 'storage.googleapis.com',
        auditLogConfigs: [
          { logType: 'ADMIN_READ' },
          { logType: 'DATA_READ' },
          { logType: 'DATA_WRITE' },
        ],
      },
    ],
  });
  const logBucketName = `projects/${securityProjectId}/locations/global/buckets/admission-audit`;
  writeJson('release-security-log-bucket.json', {
    name: logBucketName,
    retentionDays: 30,
    locked: true,
  });
  writeJson('release-security-logging-sinks.json', [
    {
      name: 'admission-audit-sink',
      destination: `logging.googleapis.com/${logBucketName}`,
      filter: `resource.type="gcs_bucket" AND resource.labels.bucket_name="${admissionBucket}"`,
      disabled: false,
    },
  ]);
  writeJson('firebase-apps.json', {
    status: 'success',
    result: [
      {
        platform: 'ANDROID',
        appId: androidAppId,
        name: `projects/${projectNumber}/androidApps/abcdef1234567890`,
      },
      {
        platform: 'IOS',
        appId: iosAppId,
        name: `projects/${projectNumber}/iosApps/abcdef1234567890`,
      },
      {
        platform: 'WEB',
        appId: webAppId,
        name: `projects/${projectNumber}/webApps/abcdef1234567890`,
      },
    ],
  });
  writeJson('firebase-hosting-sites.json', {
    status: 'success',
    result: {
      sites: [{ name: `projects/${projectId}/sites/${siteId}` }],
    },
  });
  const manifest = createEvidenceManifest({
    base: root,
    inputs: ['release-evidence/cloud-production-readonly/raw'],
    label: 'cloud-production-readonly',
    provenance: {
      sourceRevision: revision,
      sourceCommittedAt: '2026-07-12T09:00:00Z',
      builder: {
        kind: 'github-actions',
        platform: 'linux',
        architecture: 'x64',
        nodeVersion: '24.18.0',
        npmVersion: '11.6.0',
        workflowRef:
          'yhsomani/AI-Birthday/.github/workflows/cloud-readonly-evidence.yml@refs/heads/main',
        runId: '123456789',
        runAttempt: '1',
      },
    },
  });
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest)}\n`, 'utf8');
  const archiveBytes = ustar([
    { name: 'evidence-manifest.json', content: manifestBytes },
    { name: 'raw/', directory: true },
    ...readdirSync(rawRoot)
      .sort()
      .map(name => ({
        name: `raw/${name}`,
        content: readFileSync(path.join(rawRoot, name)),
      })),
  ]);
  return {
    root,
    rawRoot,
    manifestBytes,
    archiveBytes,
  };
};

test('workflow report binds the actual schema-v3 manifest and parsed raw observations', t => {
  const input = fixture(t);
  const report = createCloudReadonlyObservationReport({
    ...input,
    manifestFileName: 'evidence-manifest.json',
    archiveFileName: 'cloud-readonly-observation.tar',
  });
  assert.equal(report.sourceRevision, revision);
  assert.equal(report.project.projectId, projectId);
  assert.equal(report.observed.firebaseApps.length, 3);
  assert.deepEqual(
    Object.keys(report.hostingReleaseControl.githubGovernance.environmentIds),
    [
      'cloud-production-readonly-audit',
      'hosting-production-readonly-live',
      'hosting-production-build',
      'hosting-production-admission',
      'hosting-production-deploy',
    ],
  );
  assert.equal(
    report.hostingReleaseControl.githubGovernance.sourceCi.sourceRevision,
    revision,
  );
  assert.equal(
    report.hostingReleaseControl.githubGovernance.sourceCi.workflowRunId,
    String(ciWorkflowRunId),
  );
  assert.equal(report.evidenceManifest.bytes, input.manifestBytes.byteLength);
  assert.equal(report.rawArchive.bytes, input.archiveBytes.byteLength);
  assert.doesNotThrow(() => verifyCloudReadonlyArchive(input));
  const unrelated = ustar([
    {
      name: 'evidence-manifest.json',
      content: input.manifestBytes,
    },
    { name: 'raw/', directory: true },
  ]);
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...input,
        archiveBytes: unrelated,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /archive inventory is not exact/u,
  );
});

test('final cloud loader reparses retained manifest and archive bytes', t => {
  const input = fixture(t);
  const retained = path.join(input.root, 'retained');
  mkdirSync(retained);
  const report = createCloudReadonlyObservationReport({
    ...input,
    manifestFileName: 'evidence-manifest.json',
    archiveFileName: 'cloud-readonly-observation.tar',
  });
  const reportBytes = Buffer.from(JSON.stringify(report), 'utf8');
  writeFileSync(
    path.join(retained, 'cloud-readonly-observation-report.json'),
    reportBytes,
  );
  writeFileSync(
    path.join(retained, report.evidenceManifest.path),
    input.manifestBytes,
  );
  writeFileSync(
    path.join(retained, report.rawArchive.path),
    input.archiveBytes,
  );
  const evidenceFiles = collectCloudEvidenceFiles(retained);
  const loaded = loadCloudReadonlyObservationReport(
    retained,
    {
      evidenceReferences: [
        {
          id: 'live-readonly-audit',
          path: 'cloud-readonly-observation-report.json',
        },
      ],
    },
    evidenceFiles,
  );
  assert.deepEqual(loaded.readonlyObservationReport, report);
  assert.deepEqual(
    loaded.allowedCompanionEvidencePaths,
    new Set([report.evidenceManifest.path, report.rawArchive.path]),
  );
  const changedArchive = Buffer.from(input.archiveBytes);
  changedArchive[600] = changedArchive[600] === 0 ? 1 : 0;
  writeFileSync(path.join(retained, report.rawArchive.path), changedArchive);
  assert.throws(
    () =>
      loadCloudReadonlyObservationReport(
        retained,
        {
          evidenceReferences: [
            {
              id: 'live-readonly-audit',
              path: 'cloud-readonly-observation-report.json',
            },
          ],
        },
        evidenceFiles,
      ),
    /companion changed after collection/u,
  );
});

test('semantic report validation rejects other-project and stale relabeling', t => {
  const input = fixture(t);
  const report = createCloudReadonlyObservationReport({
    ...input,
    manifestFileName: 'evidence-manifest.json',
    archiveFileName: 'cloud-readonly-observation.tar',
  });
  const reportBytes = Buffer.from(JSON.stringify(report), 'utf8');
  const reference = {
    capturedAt: observedAt,
    kind: 'attestation',
    sha256: createHash('sha256').update(reportBytes).digest('hex'),
  };
  const evidenceFiles = new Map([
    [report.evidenceManifest.path, report.evidenceManifest],
    [report.rawArchive.path, report.rawArchive],
  ]);
  const signedIdentity = role => {
    const { impersonationPrincipal: _ignored, ...identity } =
      report.hostingReleaseControl.identities[role];
    return identity;
  };
  const { permissions: _ignoredPermissions, ...signedMutation } =
    report.hostingReleaseControl.hostingMutation;
  const context = {
    reference,
    document: {
      source: { revision },
      project: { projectId, projectNumber, androidAppId, iosAppId, webAppId },
      hosting: { siteId },
      functions: { runtimeServiceAccount: runtime },
      iam: { auditServiceAccount: audit },
      prohibitedServices: { applicationProjectCloudStorageEnabled: false },
      hostingReleaseControl: {
        repository: 'yhsomani/AI-Birthday',
        repositoryId,
        repositoryOwnerId,
        productionRef: 'refs/heads/main',
        releaseSecurityProjectId: securityProjectId,
        releaseSecurityProjectNumber: securityProjectNumber,
        applicationIamAnalysisScope: 'organizations/555555555555',
        releaseSecurityIamAnalysisScope: 'organizations/555555555555',
        observer: signedIdentity('observer'),
        admissionReader: signedIdentity('admissionReader'),
        deployer: signedIdentity('deployer'),
        identitiesDistinct: true,
        admissionBucket: report.hostingReleaseControl.admissionBucket,
        applicationAndClientBucketAccessCount: 0,
        hostingMutation: signedMutation,
        auditLogging: report.hostingReleaseControl.auditLogging,
        evidenceId: 'live-readonly-audit',
      },
    },
    expectedSource: { revision },
    reportSha256: reference.sha256,
    reportBytes: reportBytes.byteLength,
    evidenceFiles,
    allowedCompanionPaths: new Set(evidenceFiles.keys()),
  };
  assert.deepEqual(validateCloudReadonlyObservationReport(report, context), []);
  const relabeled = structuredClone(report);
  relabeled.project.projectId = 'other-prod-12345';
  assert.match(
    validateCloudReadonlyObservationReport(relabeled, context).join('\n'),
    /project\/apps\/site/u,
  );
});

test('raw parser rejects incomplete inherited IAM, alternate writers, and weaker provider conditions', t => {
  const input = fixture(t);
  const analysisPath = path.join(
    input.rawRoot,
    'admission-bucket-access-analysis.json',
  );
  const originalAnalysis = readFileSync(analysisPath);
  const incomplete = JSON.parse(originalAnalysis);
  incomplete.response.mainAnalysis.fullyExplored = false;
  writeFileSync(analysisPath, JSON.stringify(incomplete));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...input,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /not fully explored/u,
  );
  writeFileSync(analysisPath, originalAnalysis);

  const mutationPath = path.join(
    input.rawRoot,
    'hosting-mutation-access-analysis.json',
  );
  const mutation = JSON.parse(readFileSync(mutationPath));
  mutation.response.mainAnalysis.analysisResults.push(
    analysis([
      {
        member: 'user:alternate@example.com',
        permissions: ['firebasehosting.sites.update'],
      },
    ]).response.mainAnalysis.analysisResults[0],
  );
  writeFileSync(mutationPath, JSON.stringify(mutation));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...input,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /exact effective access set/u,
  );

  const restored = fixture(t);
  const providerPath = path.join(
    restored.rawRoot,
    'hosting-observer-wif-provider.json',
  );
  const provider = JSON.parse(readFileSync(providerPath));
  provider.attributeCondition = "assertion.repository=='yhsomani/AI-Birthday'";
  writeFileSync(providerPath, JSON.stringify(provider));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...restored,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /provider is not exact and active/u,
  );

  const actAsInput = fixture(t);
  const actAsPath = path.join(
    actAsInput.rawRoot,
    'hosting-deploy-impersonation-access-analysis.json',
  );
  const actAs = JSON.parse(readFileSync(actAsPath));
  actAs.response.mainAnalysis.analysisResults.push(
    analysis([
      {
        member: 'user:alternate@example.com',
        permissions: ['iam.serviceAccounts.actAs'],
      },
    ]).response.mainAnalysis.analysisResults[0],
  );
  writeFileSync(actAsPath, JSON.stringify(actAs));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...actAsInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /effective impersonation analysis does not project the exact effective access set/u,
  );

  const sinkInput = fixture(t);
  const sinkPath = path.join(
    sinkInput.rawRoot,
    'release-security-logging-sinks.json',
  );
  const sinks = JSON.parse(readFileSync(sinkPath));
  sinks[0].exclusions = [{ name: 'drop-all', filter: 'true', disabled: false }];
  writeFileSync(sinkPath, JSON.stringify(sinks));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...sinkInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /log sink is missing, duplicated, or misconfigured/u,
  );

  const exemptionInput = fixture(t);
  const hierarchyPath = path.join(
    exemptionInput.rawRoot,
    'release-security-ancestor-iam-policies.json',
  );
  const hierarchy = JSON.parse(readFileSync(hierarchyPath));
  hierarchy[1].policy.auditConfigs = [
    {
      service: 'allServices',
      auditLogConfigs: [
        {
          logType: 'DATA_READ',
          exemptedMembers: [`serviceAccount:${reader}`],
        },
      ],
    },
  ];
  writeFileSync(hierarchyPath, JSON.stringify(hierarchy));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...exemptionInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /audit logs are incomplete or exempt principals/u,
  );

  const siblingInput = fixture(t);
  const providersPath = path.join(
    siblingInput.rawRoot,
    'hosting-deploy-wif-providers.json',
  );
  const providers = JSON.parse(readFileSync(providersPath));
  providers.push({
    name: `${deployProvider.split('/providers/')[0]}/providers/weaker-sibling`,
    state: 'ACTIVE',
  });
  writeFileSync(providersPath, JSON.stringify(providers));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...siblingInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /contain exactly the approved provider/u,
  );

  const attachedInput = fixture(t);
  writeFileSync(
    path.join(attachedInput.rawRoot, 'application-resource-assets.json'),
    JSON.stringify([
      {
        assetType: 'run.googleapis.com/Service',
        resource: {
          data: {
            template: {
              serviceAccount: `projects/${projectId}/serviceAccounts/${deployer}`,
            },
          },
        },
      },
    ]),
  );
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...attachedInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /attached to a compute\/runtime resource/u,
  );
});

test('raw GitHub governance rejects branch bypass and administrator bypass', t => {
  const branchInput = fixture(t);
  const branchPath = path.join(
    branchInput.rawRoot,
    'github-main-branch-protection.json',
  );
  const branch = JSON.parse(readFileSync(branchPath));
  branch.required_pull_request_reviews.bypass_pull_request_allowances.users = [
    { id: 919191, login: 'alternate-writer' },
  ];
  writeFileSync(branchPath, JSON.stringify(branch));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...branchInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /main branch protection is not fail-closed/u,
  );

  const environmentInput = fixture(t);
  const auditPath = path.join(
    environmentInput.rawRoot,
    'github-environment-audit-log.json',
  );
  const governanceAuditEvents = JSON.parse(readFileSync(auditPath));
  const event = governanceAuditEvents.find(
    candidate =>
      candidate.environment_name === 'cloud-production-readonly-audit',
  );
  event.can_admins_bypass = true;
  writeFileSync(auditPath, JSON.stringify(governanceAuditEvents));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...environmentInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /lacks current no-bypass audit proof/u,
  );
});

test('raw GitHub governance requires the exact strict release-admission status check', t => {
  const input = fixture(t);
  const protectionPath = path.join(
    input.rawRoot,
    'github-main-branch-protection.json',
  );
  const protection = JSON.parse(readFileSync(protectionPath));
  protection.required_status_checks.strict = false;
  writeFileSync(protectionPath, JSON.stringify(protection));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...input,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /strictly require the exact release-admission check/u,
  );
});

test('raw GitHub governance rejects a failed or foreign aggregate check', t => {
  const failedInput = fixture(t);
  const failedPath = path.join(
    failedInput.rawRoot,
    'github-release-source-check-runs.json',
  );
  const failedChecks = JSON.parse(readFileSync(failedPath));
  failedChecks.check_runs[0].conclusion = 'failure';
  writeFileSync(failedPath, JSON.stringify(failedChecks));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...failedInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /aggregate CI check is not an exact successful GitHub Actions check/u,
  );

  const foreignInput = fixture(t);
  const foreignPath = path.join(
    foreignInput.rawRoot,
    'github-release-source-check-runs.json',
  );
  const foreignChecks = JSON.parse(readFileSync(foreignPath));
  foreignChecks.check_runs[0].app.slug = 'untrusted-checks';
  writeFileSync(foreignPath, JSON.stringify(foreignChecks));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...foreignInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /aggregate CI check is not an exact successful GitHub Actions check/u,
  );
});

test('raw GitHub governance rejects a CI run from another SHA or check suite', t => {
  const shaInput = fixture(t);
  const shaPath = path.join(
    shaInput.rawRoot,
    'github-release-source-ci-runs.json',
  );
  const shaRuns = JSON.parse(readFileSync(shaPath));
  shaRuns.workflow_runs[0].head_sha = 'b'.repeat(40);
  writeFileSync(shaPath, JSON.stringify(shaRuns));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...shaInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /CI run is not the exact successful main workflow run/u,
  );

  const suiteInput = fixture(t);
  const suitePath = path.join(
    suiteInput.rawRoot,
    'github-release-source-ci-runs.json',
  );
  const suiteRuns = JSON.parse(readFileSync(suitePath));
  suiteRuns.workflow_runs[0].check_suite_id = 999999999;
  writeFileSync(suitePath, JSON.stringify(suiteRuns));
  assert.throws(
    () =>
      createCloudReadonlyObservationReport({
        ...suiteInput,
        manifestFileName: 'evidence-manifest.json',
        archiveFileName: 'cloud-readonly-observation.tar',
      }),
    /CI run is not the exact successful main workflow run/u,
  );
});
