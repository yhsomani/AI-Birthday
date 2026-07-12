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
  assert.match(workflow, /test "\$IOS_APP_CHECK_PROVIDER" = 'app-attest'/u);
  assert.doesNotMatch(workflow, /device-check|deviceCheckConfig/u);
  assert.match(
    workflow,
    /test -z "\$\(git status --porcelain=v1 --untracked-files=all\)"/u,
  );
});

test('cloud collection contains no deploy or remotely mutating command surface', () => {
  assert.doesNotMatch(
    workflow,
    /^\s*(?:gcloud|firebase|backend\/functions\/node_modules\/\.bin\/firebase)\s+[^\n]*(?:\bdeploy\b|\bcreate\b|\bdelete\b|\bupdate\b|\bpatch\b|\bset\b|\badd\b|\bremove\b|\benable\b|\bdisable\b|\brestore\b|\bimport\b|\bexport\b)/imu,
  );
  assert.doesNotMatch(
    workflow,
    /secrets\s+versions\s+access|secretmanager\.versions\.access|--data(?:-binary|-raw)?\b|--upload-file\b/iu,
  );
  assert.doesNotMatch(workflow, /firebase\s+deploy|firestore:delete/iu);
  assert.match(workflow, /request = "GET"/u);
  assert.equal((workflow.match(/^ {10}curl_readonly \\/gmu) ?? []).length, 4);
  assert.doesNotMatch(workflow, /curl[^\n]*Authorization: Bearer/iu);
  assert.match(workflow, /mutationAuthorized:false/u);
});

test('read-only artifact is source-bound, manifested, short-lived, and cannot itself approve release', () => {
  assert.match(workflow, /create-evidence-manifest\.mjs/u);
  assert.match(workflow, /cloud-readonly-observation\.tar/u);
  assert.match(workflow, /--sort=name/u);
  assert.match(workflow, /sha256sum/u);
  assert.match(workflow, /git diff --exit-code/u);
  assert.match(workflow, /retention-days: 14/u);
  assert.match(workflow, /name: cloud-production-readonly-/u);
  assert.doesNotMatch(workflow, /validate-cloud-release-evidence\.mjs/u);
});
