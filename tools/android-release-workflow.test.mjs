import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const workflow = readFileSync(
  new URL('../.github/workflows/android-release-evidence.yml', import.meta.url),
  'utf8',
);
const closureReporter = readFileSync(
  new URL('./create-android-release-verification-report.mjs', import.meta.url),
  'utf8',
);
const buildJob = workflow.split('\n  verify-release:\n', 1)[0];
const verifyJob = workflow.split('\n  verify-release:\n', 2)[1];
const jobHeader = job => job.split('\n    steps:\n', 1)[0];

test('Android release workflow separates a short-lived candidate from final authority verification', () => {
  assert.match(
    workflow,
    /build-signed-candidate-not-release[\s\S]*verify-authority-approved-artifact/u,
  );
  assert.match(
    buildJob,
    /environment: android-\$\{\{ inputs\.tier \}\}-\$\{\{ inputs\.channel \}\}-signing-candidate/u,
  );
  assert.match(
    verifyJob,
    /environment: android-\$\{\{ inputs\.tier \}\}-\$\{\{ inputs\.channel \}\}-release/u,
  );
  assert.match(
    buildJob,
    /name: android-signed-candidate-not-release[\s\S]*retention-days: 14/u,
  );
  assert.doesNotMatch(
    buildJob,
    /name: android-authority-verified-release-evidence/u,
  );
  assert.match(
    verifyJob,
    /name: android-authority-verified-release-evidence[\s\S]*retention-days: 90/u,
  );
  assert.doesNotMatch(
    verifyJob,
    /assembleLab|assembleProd|bundleLab|bundleProd/u,
  );
  assert.match(
    verifyJob,
    /if: inputs\.channel == 'google-play'[\s\S]*\.\/gradlew -q help[\s\S]*--no-configuration-cache/u,
  );
});

test('candidate build fails closed on signing, Firebase, authority, and referenced evidence inputs', () => {
  for (const input of [
    'ANDROID_RELEASE_KEYSTORE_BASE64',
    'ANDROID_RELEASE_KEYSTORE_PASSWORD',
    'ANDROID_RELEASE_KEY_ALIAS',
    'ANDROID_RELEASE_KEY_PASSWORD',
    'ANDROID_RELEASE_SIGNING_CERT_SHA256',
    'ANDROID_FIREBASE_CONFIG_BASE64',
    'ANDROID_PREPACKAGE_EVIDENCE_BASE64',
    'ANDROID_PREPACKAGE_EVIDENCE_SIGNATURE_BASE64',
    'DISTRIBUTION_AUTHORITY_PUBLIC_KEY_BASE64',
  ]) {
    assert.match(buildJob, new RegExp(`test -n .*|${input}`, 'u'));
  }
  assert.match(
    buildJob,
    /GH_TOKEN="\$ANDROID_SUPPORTING_EVIDENCE_READ_TOKEN" gh release download[\s\S]*--repo "\$ANDROID_SUPPORTING_EVIDENCE_REPOSITORY"[\s\S]*BIRTHDAY_DISTRIBUTION_EVIDENCE_ROOT: \$\{\{ runner\.temp \}\}\/supporting-evidence/u,
  );
  assert.match(
    buildJob,
    /BIRTHDAY_DISTRIBUTION_EVIDENCE_FILE:[\s\S]*BIRTHDAY_DISTRIBUTION_EVIDENCE_SIGNATURE_FILE:[\s\S]*BIRTHDAY_DISTRIBUTION_AUTHORITY_PUBLIC_KEY_FILE:/u,
  );
  assert.match(buildJob, /test -z "\$INPUT_CANDIDATE_RUN_ID"/u);
  assert.match(
    buildJob,
    /selected\?\.channel !== process\.env\.INPUT_CHANNEL[\s\S]*prepackage evidence channel/u,
  );
});

test('protected material is step-scoped and absent during dependency installation', () => {
  const candidateSecrets = [
    'ANDROID_RELEASE_KEYSTORE_BASE64',
    'ANDROID_RELEASE_KEYSTORE_PASSWORD',
    'ANDROID_RELEASE_KEY_ALIAS',
    'ANDROID_RELEASE_KEY_PASSWORD',
    'ANDROID_RELEASE_SIGNING_CERT_SHA256',
    'ANDROID_FIREBASE_CONFIG_BASE64',
    'ANDROID_PREPACKAGE_EVIDENCE_BASE64',
    'ANDROID_PREPACKAGE_EVIDENCE_SIGNATURE_BASE64',
    'DISTRIBUTION_AUTHORITY_PUBLIC_KEY_BASE64',
    'ANDROID_SUPPORTING_EVIDENCE_READ_TOKEN',
  ];
  for (const secret of candidateSecrets) {
    assert.doesNotMatch(jobHeader(buildJob), new RegExp(secret, 'u'));
  }
  for (const secret of [
    'ANDROID_FINAL_EVIDENCE_BASE64',
    'ANDROID_FINAL_EVIDENCE_SIGNATURE_BASE64',
    'DISTRIBUTION_AUTHORITY_PUBLIC_KEY_BASE64',
    'ANDROID_SUPPORTING_EVIDENCE_READ_TOKEN',
  ]) {
    assert.doesNotMatch(jobHeader(verifyJob), new RegExp(secret, 'u'));
  }
  const candidateInstall = buildJob.match(
    /- name: Install locked mobile dependency graph[\s\S]*?(?=\n {6}- name:)/u,
  )?.[0];
  assert.ok(candidateInstall);
  assert.doesNotMatch(
    candidateInstall,
    /secrets\.|KEYSTORE|FIREBASE|AUTHORITY/u,
  );
  assert.ok(
    buildJob.indexOf(
      'Install locked mobile dependency graph without protected material',
    ) < buildJob.indexOf('Download exact authority-reviewed supporting bytes'),
  );
  assert.ok(
    buildJob.indexOf(
      'Install locked mobile dependency graph without protected material',
    ) < buildJob.indexOf('Decode protected build inputs'),
  );
  assert.ok(
    buildJob.indexOf('Remove protected build inputs before evidence tooling') <
      buildJob.indexOf(
        'Generate candidate-only supply-chain and provenance evidence',
      ),
  );
  assert.match(
    buildJob,
    /env:[\s\S]*ANDROID_SUPPORTING_EVIDENCE_READ_TOKEN:[\s\S]*run:[\s\S]*GH_TOKEN="\$ANDROID_SUPPORTING_EVIDENCE_READ_TOKEN" gh release download/u,
  );
});

test('channel selection can build only a Play AAB or a direct/managed APK', () => {
  assert.match(
    buildJob,
    /lab:google-play\)[\s\S]*:app:bundleLabRelease[\s\S]*app-lab-release\.aab/u,
  );
  assert.match(
    buildJob,
    /prod:google-play\)[\s\S]*:app:bundleProdRelease[\s\S]*app-prod-release\.aab/u,
  );
  assert.match(
    buildJob,
    /lab:managed-enterprise\|lab:controlled-direct\)[\s\S]*:app:assembleLabRelease[\s\S]*app-lab-release\.apk/u,
  );
  assert.match(
    buildJob,
    /prod:managed-enterprise\|prod:controlled-direct\)[\s\S]*:app:assembleProdRelease[\s\S]*app-prod-release\.apk/u,
  );
  assert.doesNotMatch(buildJob, /assembleRelease|bundleRelease/u);
});

test('final operation immutably binds run provenance, candidate bytes, and supporting evidence', () => {
  for (const field of [
    '.head_sha',
    '.head_repository.full_name',
    '.status',
    '.conclusion',
  ]) {
    assert.match(verifyJob, new RegExp(field.replaceAll('.', '\\.'), 'u'));
  }
  assert.match(
    verifyJob,
    /\.path[\s\S]*\.github\/workflows\/android-release-evidence\.yml/u,
  );
  assert.match(
    verifyJob,
    /--name android-signed-candidate-not-release[\s\S]*GH_TOKEN="\$ANDROID_SUPPORTING_EVIDENCE_READ_TOKEN" gh release download[\s\S]*--repo "\$ANDROID_SUPPORTING_EVIDENCE_REPOSITORY"/u,
  );
  assert.match(
    verifyJob,
    /artifactSha256: process\.env\.EXPECTED_ARTIFACT_SHA256/u,
  );
  assert.match(
    verifyJob,
    /supportingEvidenceReleaseTag: process\.env\.INPUT_SUPPORTING_EVIDENCE_RELEASE_TAG/u,
  );
  assert.match(
    buildJob,
    /hash-distribution-evidence-root\.mjs[\s\S]*supportingEvidenceInventorySha256: process\.env\.SUPPORTING_EVIDENCE_SHA256/u,
  );
  assert.match(
    verifyJob,
    /hash-distribution-evidence-root\.mjs[\s\S]*supportingEvidenceInventorySha256:[\s\S]*EXPECTED_SUPPORTING_EVIDENCE_SHA256/u,
  );
  assert.match(
    verifyJob,
    /value\.builder\?\.runId !== process\.env\.INPUT_CANDIDATE_RUN_ID/u,
  );
  assert.match(
    verifyJob,
    /\.run_attempt[\s\S]*EXPECTED_CANDIDATE_RUN_ATTEMPT[\s\S]*value\.builder\?\.runAttempt/u,
  );
  assert.match(
    verifyJob,
    /selected\?\.channel !== process\.env\.INPUT_CHANNEL[\s\S]*final evidence channel/u,
  );
});

test('hosted final verification uses existing channel-specific verifiers with an evidence root', () => {
  assert.match(
    verifyJob,
    /tools\/verify-android-aab\.sh[\s\S]*--play-evidence[\s\S]*release-authority-public\.pem[\s\S]*supporting-evidence[\s\S]*"\$INPUT_TIER"/u,
  );
  assert.match(
    verifyJob,
    /tools\/verify-android-apk\.sh[\s\S]*--restricted-evidence[\s\S]*release-authority-public\.pem[\s\S]*supporting-evidence[\s\S]*"\$INPUT_TIER"/u,
  );
  assert.doesNotMatch(verifyJob, /--play-delivered-evidence/u);
});

test('final workflow emits closure input only after the full channel verifier and manifest succeed', () => {
  const verifier = verifyJob.indexOf('tools/verify-android-aab.sh');
  const manifest = verifyJob.indexOf('tools/create-evidence-manifest.mjs');
  const structured = verifyJob.indexOf(
    'tools/create-android-release-verification-report.mjs',
  );
  assert.ok(verifier >= 0 && manifest > verifier && structured > manifest);
  assert.match(
    verifyJob,
    /create-android-release-verification-report\.mjs[\s\S]*--artifact-mode "\$artifact_mode"[\s\S]*--verification-report "\$output\/verification-report\.txt"[\s\S]*--verification-manifest "\$output\/verification-manifest\.json"[\s\S]*--output "\$output\/release-closure-report\.json"/u,
  );
  assert.match(
    verifyJob,
    /name: android-authority-verified-release-evidence[\s\S]*path: release-evidence\/android-authority-verified-release\//u,
  );
});

test('structured Android closure report binds the full verifier, manifest, authority, expiry, and exact artifact', () => {
  assert.match(closureReporter, /verifyDistributionEvidenceAuthority/u);
  assert.match(closureReporter, /validateDistributionEvidence/u);
  assert.match(
    closureReporter,
    /full Android verifier PASS contract is incomplete/u,
  );
  assert.match(
    closureReporter,
    /reportEntry\.sha256 !== sha256\(verificationReportBytes\)[\s\S]*artifactEntry\.sha256 !== artifactSha256[\s\S]*evidenceEntry\.sha256 !== sha256\(evidenceBytes\)/u,
  );
  for (const field of [
    'fullVerifierKind',
    'artifactSha256',
    'artifactSigningCertificateSha256',
    'installedSigningCertificateSha256',
    'firebase',
    'authorityPublicKeySpkiSha256',
    'validUntil',
    'fullVerificationReportSha256',
    'verificationManifestSha256',
  ]) {
    assert.match(closureReporter, new RegExp(field, 'u'));
  }
});
