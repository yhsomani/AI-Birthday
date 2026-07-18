import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const workflow = readFileSync(
  path.join(ROOT, '.github/workflows/cloud-readonly-evidence.yml'),
  'utf8',
);

test('cloud observation is manual, protected, keyless, and has minimal GitHub permissions', () => {
  assert.match(workflow, /on:\n {2}workflow_dispatch:/u);
  assert.doesNotMatch(workflow, /^ {2}(?:push|pull_request|schedule):/mu);
  assert.match(
    workflow,
    /permissions:\n {2}contents: read\n {2}id-token: write/u,
  );
  assert.match(workflow, /environment: cloud-production-readonly-audit/u);
  assert.match(workflow, /workload_identity_provider:/u);
  assert.match(workflow, /service_account:/u);
  assert.doesNotMatch(workflow, /credentials_json|SERVICE_ACCOUNT_KEY/iu);
  assert.ok(
    workflow.indexOf(
      'git merge-base --is-ancestor HEAD refs/remotes/origin/main',
    ) < workflow.indexOf('actions/create-github-app-token@'),
    'protected-main ancestry must be established before minting governance credentials',
  );
  assert.ok(
    workflow.indexOf('actions/create-github-app-token@') <
      workflow.indexOf('actions/setup-node@'),
    'governance collection must finish before repository tooling is installed',
  );
});

test('every third-party action and cloud CLI is immutably pinned', () => {
  assert.match(
    workflow,
    /actions\/checkout@df4cb1c069e1874edd31b4311f1884172cec0e10 # v6/u,
  );
  assert.match(
    workflow,
    /actions\/setup-node@48b55a011bda9f5d6aeb4c2d9c7362e8dae4041e # v6/u,
  );
  assert.match(
    workflow,
    /actions\/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1 # v3\.2\.0/u,
  );
  assert.match(
    workflow,
    /google-github-actions\/auth@7c6bc770dae815cd3e89ee6cdf493a5fab2cc093 # v3\.0\.0/u,
  );
  assert.match(
    workflow,
    /google-github-actions\/setup-gcloud@aa5489c8933f4cc7a4f7d45035b3b1440c9c10db # v3\.0\.1/u,
  );
  assert.match(workflow, /version: 575\.0\.1/u);
  assert.match(workflow, /firebase --version\)" = 15\.23\.0/u);
  assert.match(
    workflow,
    /actions\/upload-artifact@b7c566a772e6b6bfb58ed0dc250532a479d7789f # v6/u,
  );
});

test('all project, app, billing, runtime, Hosting, and source coordinates are explicit inputs', () => {
  for (const input of [
    'source_revision',
    'project_id',
    'project_number',
    'release_security_project_id',
    'release_security_project_number',
    'billing_account_id',
    'firebase_android_app_id',
    'firebase_ios_app_id',
    'firebase_web_app_id',
    'runtime_service_account',
    'hosting_site_id',
    'logging_location',
    'ios_app_check_provider',
    'retained_project_assignment',
  ]) {
    assert.match(workflow, new RegExp(`^      ${input}:`, 'mu'));
  }
  assert.match(
    workflow,
    /test "\$\(git rev-parse --verify HEAD\)" = "\$EXPECTED_SOURCE_REVISION"/u,
  );
  assert.match(workflow, /ref: \$\{\{ inputs\.source_revision \}\}/u);
  assert.match(workflow, /fetch-depth: 0/u);
  assert.match(
    workflow,
    /git merge-base --is-ancestor HEAD refs\/remotes\/origin\/main/u,
  );
  assert.match(workflow, /test "\$IOS_APP_CHECK_PROVIDER" = 'app-attest'/u);
  assert.doesNotMatch(workflow, /device-check|deviceCheckConfig/u);
  assert.match(
    workflow,
    /test -z "\$\(git status --porcelain=v1 --untracked-files=all\)"/u,
  );
});

test('release-control observation is raw, inherited-effective, identity/provider-bound, and read-only', () => {
  for (const variable of [
    'HOSTING_ADMISSION_BUCKET',
    'HOSTING_AUDIT_WIF_PROVIDER',
    'HOSTING_AUDIT_SERVICE_ACCOUNT',
    'HOSTING_ADMISSION_READER_WIF_PROVIDER',
    'HOSTING_ADMISSION_READER_SERVICE_ACCOUNT',
    'HOSTING_DEPLOY_WIF_PROVIDER',
    'HOSTING_DEPLOY_SERVICE_ACCOUNT',
    'RELEASE_SECURITY_LOG_SINK',
    'RELEASE_SECURITY_LOG_BUCKET',
    'RELEASE_SECURITY_LOGGING_LOCATION',
  ]) {
    assert.match(workflow, new RegExp(`^      ${variable}:`, 'mu'));
  }
  assert.match(workflow, /gcloud asset analyze-iam-policy/u);
  assert.match(workflow, /--expand-groups --expand-roles --show-response/u);
  assert.match(workflow, /admission-bucket-access-analysis\.json/u);
  assert.match(workflow, /hosting-mutation-access-analysis\.json/u);
  assert.match(workflow, /impersonation-access-analysis\.json/u);
  assert.match(workflow, /release-security-project-iam\.json/u);
  assert.match(workflow, /release-security-log-bucket\.json/u);
  assert.match(workflow, /workload-identity-pools providers describe/u);
  assert.match(workflow, /workload-identity-pools providers list/u);
  assert.match(workflow, /workload-identity-pools describe/u);
  assert.match(workflow, /user-managed-keys\.json/u);
  assert.match(workflow, /github\.repository_id/u);
  assert.match(workflow, /github\.repository_owner_id/u);
  assert.match(workflow, /iam\.disableCrossProjectServiceAccountUsage/u);
  assert.match(workflow, /gcloud asset list/u);
  assert.match(workflow, /cloud-production-readonly-audit/u);
  assert.match(workflow, /permission-organization-administration: read/u);
  assert.match(
    workflow,
    /client-id: \$\{\{ vars\.GITHUB_GOVERNANCE_APP_CLIENT_ID \}\}/u,
  );
  assert.doesNotMatch(workflow, /\bapp-id:/u);
  assert.doesNotMatch(workflow, /secrets\.GITHUB_GOVERNANCE_AUDIT_TOKEN/u);
  assert.match(workflow, /length < 100/u);
  assert.doesNotMatch(workflow, /audit-log\?[^\n]*[?&]page=/u);
  assert.match(workflow, /EXPECTED_GITHUB_GOVERNANCE_DIGEST/u);
  assert.doesNotMatch(workflow, /search-all-resources/u);
});

test('cloud collection contains no deploy or remotely mutating command surface', () => {
  const forbiddenVerbs = new Set([
    'deploy',
    'create',
    'delete',
    'update',
    'patch',
    'set',
    'add',
    'remove',
    'enable',
    'disable',
    'restore',
    'import',
    'export',
  ]);
  for (const line of workflow.split('\n')) {
    const trimmed = line.trim();
    if (
      !/^(?:gcloud|firebase|backend\/functions\/node_modules\/\.bin\/firebase)\s/u.test(
        trimmed,
      )
    )
      continue;
    const observed = [];
    for (const token of trimmed.split(/\s+/u).slice(1)) {
      if (token.startsWith('-') || token.includes('$')) break;
      observed.push(token);
    }
    assert.equal(
      observed.some(token => forbiddenVerbs.has(token)),
      false,
      `mutating cloud command: ${trimmed}`,
    );
  }
  assert.doesNotMatch(
    workflow,
    /secrets\s+versions\s+access|secretmanager\.versions\.access|--data(?:-binary|-raw)?\b|--upload-file\b/iu,
  );
  assert.doesNotMatch(workflow, /firebase\s+deploy|firestore:delete/iu);
  assert.match(workflow, /request = "GET"/u);
  assert.equal((workflow.match(/^ {10}curl_readonly \\/gmu) ?? []).length, 5);
  assert.doesNotMatch(workflow, /curl[^\n]*Authorization: Bearer/iu);
  assert.match(workflow, /mutationAuthorized:false/u);
});

test('read-only artifact is source-bound, manifested, short-lived, and cannot itself approve release', () => {
  assert.match(workflow, /create-evidence-manifest\.mjs/u);
  assert.match(workflow, /cloud-readonly-observation\.tar/u);
  assert.match(workflow, /create-cloud-readonly-observation-report\.mjs/u);
  assert.match(workflow, /cloud-readonly-observation-report\.json/u);
  for (const binding of [
    'repository',
    'runId',
    'runAttempt',
    'workflowRef',
    'workflowRun',
  ]) {
    assert.match(workflow, new RegExp(`--arg ${binding}`, 'u'));
  }
  assert.match(workflow, /--sort=name/u);
  assert.match(workflow, /sha256sum/u);
  assert.match(workflow, /git diff --exit-code/u);
  assert.match(workflow, /retention-days: 14/u);
  assert.match(workflow, /name: cloud-production-readonly-/u);
  assert.doesNotMatch(workflow, /validate-cloud-release-evidence\.mjs/u);
});
