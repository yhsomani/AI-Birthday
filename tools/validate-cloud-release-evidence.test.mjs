import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import {
  EXPECTED_CALLABLE_FUNCTIONS,
  EXPECTED_SCHEDULED_FUNCTIONS,
  EXPECTED_TTL_COLLECTION_GROUPS,
  FUNCTIONS_DEPLOYMENT_SOURCE_PATHS,
  HOSTING_DEPLOYMENT_SOURCE_PATHS,
  REQUIRED_APPROVAL_ROLES,
  REQUIRED_EVIDENCE_IDS,
  collectCloudEvidenceFiles,
  validateCloudReleaseEvidence,
  verifyCloudReleaseAuthority,
} from './validate-cloud-release-evidence.mjs';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const NOW = Date.parse('2026-07-12T12:00:00Z');
const GENERATED_AT = '2026-07-12T11:00:00Z';
const VALID_UNTIL = '2026-08-10T11:00:00Z';
const digest = value => createHash('sha256').update(value).digest('hex');
const clone = value => structuredClone(value);

test('cloud source binding covers every executable deployment build input', () => {
  assert.deepEqual(FUNCTIONS_DEPLOYMENT_SOURCE_PATHS, [
    'backend/functions/.npmrc',
    'backend/functions/.nvmrc',
    'backend/functions/package.json',
    'backend/functions/src',
    'backend/functions/tsconfig.build.json',
    'backend/functions/tsconfig.json',
  ]);
  assert.deepEqual(HOSTING_DEPLOYMENT_SOURCE_PATHS, [
    'backend/hosting/.npmrc',
    'backend/hosting/404.html',
    'backend/hosting/delete',
    'backend/hosting/index.html',
    'backend/hosting/package-lock.json',
    'backend/hosting/package.json',
    'backend/hosting/privacy',
    'backend/hosting/src',
    'backend/hosting/static',
    'backend/hosting/support',
    'backend/hosting/terms',
    'backend/hosting/tools',
    'backend/hosting/tsconfig.json',
    'backend/hosting/vite.config.ts',
  ]);
});

const sourceDigests = Object.freeze({
  revision: '1'.repeat(40),
  projectAboutSha256: '1'.repeat(64),
  firebaseJsonSha256: '2'.repeat(64),
  functionsSourceTreeSha256: '3'.repeat(64),
  functionsLockSha256: '4'.repeat(64),
  firestoreRulesSha256: '5'.repeat(64),
  firestoreIndexesSha256: '6'.repeat(64),
  ttlPoliciesSha256: '7'.repeat(64),
  hostingSourceTreeSha256: '8'.repeat(64),
});

function validFixture() {
  const evidenceFiles = new Map();
  const evidenceReferences = REQUIRED_EVIDENCE_IDS.map(id => {
    const file = `${id}.json`;
    const sha256 = digest(`${id}\n`);
    evidenceFiles.set(file, { sha256, bytes: id.length + 1 });
    return {
      id,
      path: file,
      sha256,
      capturedAt: '2026-07-12T10:00:00Z',
      validUntil: VALID_UNTIL,
      issuer: id.startsWith('approval-')
        ? `Independent ${id.slice('approval-'.length)} reviewer`
        : 'Protected production evidence workflow',
      kind: id.startsWith('approval-') ? 'approval' : 'attestation',
    };
  });
  const document = {
    $schema: './cloud-release-evidence.schema.json',
    schemaVersion: 1,
    product: 'birthday-autopilot',
    status: 'approved',
    source: {
      ...sourceDigests,
      repository: 'https://github.com/yhsomani/AI-Birthday.git',
      clean: true,
      deploymentProvenanceEvidenceId: 'source-deployment-provenance',
    },
    validity: {
      generatedAt: GENERATED_AT,
      validUntil: VALID_UNTIL,
      maximumAgeDays: 30,
    },
    project: {
      tier: 'production',
      projectId: 'birthday-prod-12345',
      projectNumber: '123456789012',
      androidAppId: '1:123456789012:android:abcdef1234567890',
      iosAppId: '1:123456789012:ios:abcdef1234567890',
      webAppId: '1:123456789012:web:abcdef1234567890',
      androidPackage: 'com.yashsomani.birthdayautopilot',
      iosBundle: 'com.yashsomani.birthdayautopilot',
      functionsRegion: 'asia-south1',
      firestoreLocation: 'asia-south1',
      retainedProjectId: 'relateai-birthday-ysomani',
      retainedProjectAssignment: 'staging',
      noCrossTierSharing: true,
      evidenceIds: [
        'cloud-project-inventory',
        'data-residency',
        'tier-isolation',
      ],
    },
    identityAndApis: {
      firebaseGoogleProviderEnabled: true,
      peopleApiEnabled: true,
      peopleScope: 'https://www.googleapis.com/auth/contacts.readonly',
      webOauthClientId: '123456789012-web.apps.googleusercontent.com',
      androidOauthClients: [
        {
          channel: 'direct-managed',
          clientId: '123456789012-android.apps.googleusercontent.com',
          applicationId: 'com.yashsomani.birthdayautopilot',
          signingCertificateSha1: 'a'.repeat(40),
        },
      ],
      iosOauthClient: {
        clientId: '123456789012-ios.apps.googleusercontent.com',
        bundleId: 'com.yashsomani.birthdayautopilot',
        teamId: 'ABCDEFGHIJ',
        reversedClientId: 'com.googleusercontent.apps.123456789012-ios',
      },
      oneVisibleGoogleChoice: true,
      noOauthClientSecretInApps: true,
      accountLifecycleTestsPassed: true,
      peopleConsentTestsPassed: true,
      publicApiKeysRestricted: true,
      firebaseClientConfigsRefreshed: true,
      evidenceIds: [
        'api-key-restrictions',
        'firebase-auth-deletion',
        'oauth-client-inventory',
        'people-consent',
      ],
    },
    aiLogic: {
      provider: 'vertex-ai',
      sdkSurface: 'firebase-ai-logic',
      model: 'gemini-3.5-flash',
      location: 'global',
      apiService: 'firebasevertexai.googleapis.com',
      authenticatedUsersOnly: true,
      remoteConfigKey: 'gemini_suggestions_enabled',
      defaultOff: true,
      activatedCanonicalTrueOnly: true,
      monitoring: 'off',
      generativeLanguageApiAllowedOnMobileKeys: false,
      billingEnabled: true,
      providerQuotasConfigured: true,
      perUserRateLimitConfigured: true,
      budgetAlertsConfigured: true,
      killSwitchTested: true,
      providerTermsApproved: true,
      dataGovernanceApproved: true,
      modelAvailabilityCheckedAt: '2026-07-12T10:30:00Z',
      evidenceIds: ['firebase-ai-vertex', 'quota-budget', 'remote-config'],
    },
    appCheck: {
      androidRegistrations: [
        {
          firebaseAppId: '1:123456789012:android:abcdef1234567890',
          channel: 'direct-managed',
          distributionScope: 'outside-play-only',
          signingCertificateSha256: '9'.repeat(64),
          provider: 'play-integrity',
          playRecognizedRequired: false,
          licensedRequired: false,
          deviceIntegrityRequired: true,
          debugProvider: false,
        },
      ],
      iosRegistrations: [
        {
          firebaseAppId: '1:123456789012:ios:abcdef1234567890',
          provider: 'app-attest',
          teamId: 'ABCDEFGHIJ',
          productionEnvironment: true,
          fallbackPolicyApproved: true,
          debugProvider: false,
        },
      ],
      webRegistration: {
        firebaseAppId: '1:123456789012:web:abcdef1234567890',
        provider: 'recaptcha-enterprise',
        siteKeySha256: 'c'.repeat(64),
        debugProvider: false,
      },
      aiLogicEnforced: true,
      callableBaselineEnforced: true,
      limitedUseReplayForStateChanges: true,
      limitedUseReplayForCompanionStatus: true,
      signedChannelsTested: true,
      evidenceIds: [
        'app-check-android',
        'app-check-ios',
        'app-check-replay',
        'app-check-web',
        'signed-channel-smoke',
      ],
    },
    functions: {
      generation: 2,
      region: 'asia-south1',
      runtime: 'nodejs22',
      runtimeServiceAccount:
        'birthday-runtime@birthday-prod-12345.iam.gserviceaccount.com',
      deployedSourceRevision: sourceDigests.revision,
      callableNames: [...EXPECTED_CALLABLE_FUNCTIONS],
      scheduledNames: [...EXPECTED_SCHEDULED_FUNCTIONS],
      commonOptions: {
        enforceAppCheck: true,
        consumeAppCheckToken: true,
        timeoutSeconds: 30,
        memory: '256MiB',
        minInstances: 0,
        maxInstances: 20,
        concurrency: 20,
      },
      scheduledOptions: {
        schedule: 'every 1 minutes',
        timeZone: 'Etc/UTC',
        timeoutSeconds: 300,
        memory: '256MiB',
        maxInstances: 1,
      },
      requestBodiesLogged: false,
      rawExceptionsLogged: false,
      deploymentEvidenceId: 'functions-deployment',
      liveSmokeEvidenceId: 'functions-live-smoke',
    },
    firestore: {
      databaseId: '(default)',
      databaseType: 'FIRESTORE_NATIVE',
      location: 'asia-south1',
      directClientRules: 'deny-all',
      deployedRulesSha256: sourceDigests.firestoreRulesSha256,
      deployedIndexesSha256: sourceDigests.firestoreIndexesSha256,
      deployedTtlPolicySha256: sourceDigests.ttlPoliciesSha256,
      ttlCollectionGroups: [...EXPECTED_TTL_COLLECTION_GROUPS],
      logicalExpiryEnforcedTransactionally: true,
      pointInTimeRecovery: 'disabled',
      backupScheduleCount: 0,
      recursiveDeletionVerified: true,
      authDeletionAbsenceVerified: true,
      continuityState: 'HEALTHY',
      ledgerGeneration: 'generation-2026-07-01',
      emptyLedgerTreatedAsNoSendProof: false,
      evidenceIds: [
        'continuity-dr',
        'firestore-backup-pitr',
        'firestore-index-contention',
        'firestore-rules-live',
        'firestore-ttl',
      ],
    },
    secrets: {
      secretName: 'COORDINATION_HMAC_KEYRING',
      secretValueIncluded: false,
      currentSecretManagerVersionId: '7',
      previousSecretManagerVersionId: '6',
      currentKeyLabel: 'v7',
      previousKeyLabel: 'v6',
      currentCreatedAt: '2026-06-02T00:00:00Z',
      previousLastWriteAt: '2026-06-01T00:00:00Z',
      previousRetainUntil: '2027-07-06T00:00:00Z',
      longestAliasRetentionDays: 400,
      rotationCadenceDays: 90,
      replicationPolicyApproved: true,
      runtimeAccessorOnly: true,
      repositoryAndCiValueAbsent: true,
      metadataEvidenceId: 'secret-keyring-metadata',
      rotationEvidenceId: 'secret-rotation',
    },
    iam: {
      runtimeServiceAccount:
        'birthday-runtime@birthday-prod-12345.iam.gserviceaccount.com',
      auditServiceAccount:
        'birthday-auditor@birthday-prod-12345.iam.gserviceaccount.com',
      workloadIdentityFederation: true,
      serviceAccountKeyFilesUsed: false,
      runtimeUserManagedKeyCount: 0,
      auditUserManagedKeyCount: 0,
      broadPrimitiveRolesAbsent: true,
      wildcardPermissionsAbsent: true,
      capabilities: {
        firestoreCoordination: true,
        firebaseAuthDeletion: true,
        secretVersionAccessForKeyringOnly: true,
        appCheckTokenVerification: true,
        schedulerInvocation: true,
      },
      reviewedAt: '2026-07-12T09:00:00Z',
      policyEvidenceId: 'iam-policy',
      effectivePermissionsEvidenceId: 'iam-effective-permissions',
    },
    observability: {
      requestBodiesExcluded: true,
      rawExceptionsExcluded: true,
      contactMessagePromptContentExcluded: true,
      aiMonitoring: 'off',
      aiPromptResponseExcluded: true,
      defaultLoggingExclusionName: 'exclude-callable-body-content',
      applicationLogRetentionDays: 30,
      dataAccessAuditRetentionDays: 30,
      deletionCorrelationAccessRestricted: true,
      logSinksReviewed: true,
      unapprovedCrashAndAnalyticsAbsent: true,
      evidenceId: 'logging-privacy',
    },
    costControls: {
      billingAccountId: 'ABCDEF-123456-789ABC',
      billingEnabled: true,
      monthlyBudget: { currencyCode: 'INR', units: 10000 },
      budgetAlertsNotHardCap: true,
      providerHardQuotasConfigured: true,
      quotas: [
        {
          name: 'firebase-ai-user-requests',
          limit: 20,
          unit: 'requests/day/user',
        },
        {
          name: 'firestore-writes',
          limit: 100000,
          unit: 'writes/day/project',
        },
        {
          name: 'functions-max-instances',
          limit: 20,
          unit: 'instances/function',
        },
      ],
      quotaOwnerEmails: ['quota-owner@yhsomani.com'],
      incidentContactEmails: ['incident-owner@yhsomani.com'],
      evidenceId: 'quota-budget',
    },
    hosting: {
      siteId: 'birthday-autopilot-prod',
      deployedVersionId: 'version-2026-07-12',
      publicBaseUrl: 'https://birthday-autopilot.yhsomani.com/',
      privacyUrl: 'https://birthday-autopilot.yhsomani.com/privacy/',
      termsUrl: 'https://birthday-autopilot.yhsomani.com/terms/',
      supportUrl: 'https://birthday-autopilot.yhsomani.com/support/',
      deletionUrl: 'https://birthday-autopilot.yhsomani.com/delete/',
      identityVerifiedSupportUrl:
        'https://support.yhsomani.com/account-deletion/',
      firebaseConfigSha256: sourceDigests.firebaseJsonSha256,
      releaseConfigSha256: 'a'.repeat(64),
      deployedArtifactSha256: 'b'.repeat(64),
      recaptchaEnterpriseAppCheckRegistered: true,
      securityHeadersVerified: true,
      legalCopyApproved: true,
      hindiCopyApproved: true,
      deletionSagaTested: true,
      evidenceId: 'hosting-release',
    },
    prohibitedServices: {
      realtimeDatabaseEnabled: false,
      cloudStorageEnabled: false,
      fcmEnabled: false,
      analyticsEnabled: false,
      adSdkEnabled: false,
      crashlyticsEnabled: false,
      performanceMonitoringEnabled: false,
      directMobileFirestorePathPresent: false,
      rawContactOrMessageCloudStorePresent: false,
      evidenceId: 'prohibited-services',
    },
    verification: {
      firebaseAuthLifecyclePassed: true,
      peopleConsentAndRevocationPassed: true,
      androidSignedAttestationPassed: true,
      iosSignedAttestationPassed: true,
      androidAiLogicCallPassed: true,
      iosAiLogicCallPassed: true,
      stateChangingCallableProbePassed: true,
      companionStatusProbePassed: true,
      replayedLimitedUseTokenRejected: true,
      directFirestoreAnonymousDenied: true,
      directFirestoreAuthenticatedDenied: true,
      ttlPoliciesObserved: true,
      recursiveDeletionAbsencePassed: true,
      signedOutReceiptPassed: true,
      contentLeakScanFindingCount: 0,
      debugProviderFindingCount: 0,
      liveAuditEvidenceId: 'live-readonly-audit',
      signedChannelEvidenceId: 'signed-channel-smoke',
      deletionEvidenceId: 'deletion-live-smoke',
    },
    operations: {
      globalControlState: 'HEALTHY',
      ledgerGeneration: 'generation-2026-07-01',
      emptyLedgerNeverUsedAsNoSendProof: true,
      disasterCreatesReviewedNewGeneration: true,
      reRegistrationAndReapprovalRequiredAfterDisaster: true,
      sameDateAutomationBlockedAfterDisaster: true,
      userLedgerBackupsNeverRestored: true,
      rtoMinutes: 240,
      availabilityTargetPercent: 99.9,
      p95CallableLatencyMs: 1500,
      errorBudgetWindowDays: 30,
      deletionSlaHours: 72,
      loadContentionTestPassed: true,
      regionalFailureTestPassed: true,
      incidentRollbackReady: true,
      continuityEvidenceId: 'continuity-dr',
      sloEvidenceId: 'slo-load-cost',
      incidentEvidenceId: 'incident-rollback',
    },
    evidenceReferences,
    approvals: REQUIRED_APPROVAL_ROLES.map((role, index) => ({
      role,
      approver: `Independent reviewer ${index + 1}`,
      decision: 'approved',
      approvedAt: '2026-07-12T10:30:00Z',
      validUntil: VALID_UNTIL,
      evidenceId: `approval-${role}`,
    })),
  };
  return {
    document,
    context: { nowMs: NOW, expectedSource: sourceDigests, evidenceFiles },
  };
}

const messages = (document, context) =>
  validateCloudReleaseEvidence(document, context).join('\n');

test('a complete independently evidenced cloud release package passes semantic validation', () => {
  const { document, context } = validFixture();
  assert.deepEqual(validateCloudReleaseEvidence(document, context), []);
});

test('the repository template is deliberately incapable of authorizing production', () => {
  const template = JSON.parse(
    readFileSync(
      path.join(ROOT, 'tools/cloud-release-evidence.template.json'),
      'utf8',
    ),
  );
  const errors = validateCloudReleaseEvidence(template, {
    nowMs: NOW,
    expectedSource: sourceDigests,
    evidenceFiles: new Map(),
  });
  assert.ok(errors.length > 40);
  assert.match(errors.join('\n'), /status must be approved/u);
  assert.match(errors.join('\n'), /source\.revision/u);
});

test('unknown top-level and nested fields fail closed', () => {
  const { document, context } = validFixture();
  document.unreviewed = true;
  assert.match(messages(document, context), /document fields/u);
  delete document.unreviewed;
  document.functions.commonOptions.ingress = 'all';
  assert.match(messages(document, context), /commonOptions fields/u);
});

test('clean source revision and every critical source digest are immutable', () => {
  const { document, context } = validFixture();
  document.source.clean = false;
  document.source.functionsSourceTreeSha256 = 'f'.repeat(64);
  document.functions.deployedSourceRevision = '2'.repeat(40);
  const error = messages(document, context);
  assert.match(error, /source\.clean/u);
  assert.match(error, /functionsSourceTreeSha256 does not match/u);
  assert.match(error, /deployedSourceRevision/u);
});

test('future-dated, overlong, and expired packages fail closed', () => {
  const { document, context } = validFixture();
  document.validity.generatedAt = '2026-07-13T12:00:00Z';
  document.validity.validUntil = '2026-09-13T12:00:00Z';
  const error = messages(document, context);
  assert.match(error, /no longer than 30 days/u);
  assert.match(error, /future-dated or expired/u);
});

test('production project identity, retained tier assignment, and Firebase app types are cross-bound', () => {
  const { document, context } = validFixture();
  document.project.projectId = 'relateai-birthday-ysomani';
  document.project.iosAppId = '1:123456789012:android:abcdef1234567890';
  const error = messages(document, context);
  assert.match(error, /retained project assignment contradicts/u);
  assert.match(error, /iosAppId must be iOS/u);
});

test('OAuth and People evidence cannot widen scope, omit lifecycle proof, or duplicate a channel', () => {
  const { document, context } = validFixture();
  document.identityAndApis.peopleScope =
    'https://www.googleapis.com/auth/contacts';
  document.identityAndApis.noOauthClientSecretInApps = false;
  document.identityAndApis.androidOauthClients.push(
    clone(document.identityAndApis.androidOauthClients[0]),
  );
  const error = messages(document, context);
  assert.match(error, /contacts\.readonly only/u);
  assert.match(error, /noOauthClientSecretInApps/u);
  assert.match(error, /invalid or duplicated/u);
});

test('Vertex model, monitoring, mobile-key API allowlist, and fresh availability are binding', () => {
  const { document, context } = validFixture();
  document.aiLogic.model = 'gemini-preview';
  document.aiLogic.monitoring = 'on';
  document.aiLogic.generativeLanguageApiAllowedOnMobileKeys = true;
  document.aiLogic.modelAvailabilityCheckedAt = '2026-07-10T10:00:00Z';
  const error = messages(document, context);
  assert.match(error, /model must be gemini-3\.5-flash/u);
  assert.match(error, /monitoring must be off/u);
  assert.match(error, /generativeLanguageApiAllowedOnMobileKeys/u);
  assert.match(error, /no older than 24 hours/u);
});

test('App Check distribution semantics, replay enforcement, and no-debug policy fail closed', () => {
  const { document, context } = validFixture();
  const android = document.appCheck.androidRegistrations[0];
  android.licensedRequired = true;
  android.debugProvider = true;
  document.appCheck.iosRegistrations[0].provider = 'device-check';
  document.appCheck.webRegistration.debugProvider = true;
  document.appCheck.limitedUseReplayForCompanionStatus = false;
  const error = messages(document, context);
  assert.match(error, /contradict its distribution scope/u);
  assert.match(error, /debugProvider/u);
  assert.match(error, /compiled App Attest provider/u);
  assert.match(error, /limitedUseReplayForCompanionStatus/u);
  assert.match(error, /webRegistration\.debugProvider/u);
});

test('mixed Android distribution requires distinct Play and direct OAuth/App Check certificates', () => {
  const { document, context } = validFixture();
  const directOauth = document.identityAndApis.androidOauthClients[0];
  document.identityAndApis.androidOauthClients.push({
    ...directOauth,
    channel: 'google-play',
    clientId: '123456789012-play.apps.googleusercontent.com',
    signingCertificateSha1: 'b'.repeat(40),
  });
  const directRegistration = document.appCheck.androidRegistrations[0];
  directRegistration.distributionScope = 'mixed';
  directRegistration.playRecognizedRequired = true;
  directRegistration.deviceIntegrityRequired = false;
  document.appCheck.androidRegistrations.push({
    ...directRegistration,
    channel: 'google-play',
    signingCertificateSha256: 'd'.repeat(64),
  });
  assert.deepEqual(validateCloudReleaseEvidence(document, context), []);

  document.appCheck.androidRegistrations.pop();
  assert.match(
    messages(document, context),
    /channels do not fully cover the distribution scope|inventories do not match/u,
  );
});

test('Functions inventory, options, source revision, and logging policy are exact', () => {
  const { document, context } = validFixture();
  document.functions.callableNames.pop();
  document.functions.commonOptions.maxInstances = 21;
  document.functions.requestBodiesLogged = true;
  const error = messages(document, context);
  assert.match(error, /exact callable inventory/u);
  assert.match(error, /maxInstances does not match source/u);
  assert.match(error, /requestBodiesLogged/u);
});

test('Firestore TTL inventory, source digests, backups, PITR, and empty-ledger semantics are exact', () => {
  const { document, context } = validFixture();
  document.firestore.ttlCollectionGroups.pop();
  document.firestore.deployedRulesSha256 = 'e'.repeat(64);
  document.firestore.pointInTimeRecovery = 'enabled';
  document.firestore.backupScheduleCount = 1;
  document.firestore.emptyLedgerTreatedAsNoSendProof = true;
  const error = messages(document, context);
  assert.match(error, /exact retention policy/u);
  assert.match(error, /deployedRulesSha256 does not match/u);
  assert.match(error, /PITR and managed backup schedules/u);
  assert.match(error, /emptyLedgerTreatedAsNoSendProof/u);
});

test('Secret evidence rejects values, same versions, and short prior-key retention', () => {
  const { document, context } = validFixture();
  document.secrets.secretValueIncluded = true;
  document.secrets.previousSecretManagerVersionId = '7';
  document.secrets.previousRetainUntil = '2026-08-01T00:00:00Z';
  const error = messages(document, context);
  assert.match(error, /secretValueIncluded/u);
  assert.match(error, /current and previous versions must differ/u);
  assert.match(error, /at least 400 days/u);
});

test('IAM evidence requires distinct keyless audit/runtime identities and narrow roles', () => {
  const { document, context } = validFixture();
  document.iam.auditServiceAccount = document.iam.runtimeServiceAccount;
  document.iam.serviceAccountKeyFilesUsed = true;
  document.iam.runtimeUserManagedKeyCount = 1;
  document.iam.broadPrimitiveRolesAbsent = false;
  const error = messages(document, context);
  assert.match(error, /must be distinct/u);
  assert.match(error, /serviceAccountKeyFilesUsed/u);
  assert.match(error, /zero user-managed keys/u);
  assert.match(error, /broadPrimitiveRolesAbsent/u);
});

test('logging evidence requires body/content exclusions and bounded retention', () => {
  const { document, context } = validFixture();
  document.observability.requestBodiesExcluded = false;
  document.observability.aiMonitoring = 'on';
  document.observability.applicationLogRetentionDays = 31;
  const error = messages(document, context);
  assert.match(error, /requestBodiesExcluded/u);
  assert.match(error, /aiMonitoring must be off/u);
  assert.match(error, /between 1 and 30 days/u);
});

test('budget alerts never masquerade as caps and exact provider quotas remain bounded', () => {
  const { document, context } = validFixture();
  document.costControls.budgetAlertsNotHardCap = false;
  document.costControls.quotas.find(
    quota => quota.name === 'functions-max-instances',
  ).limit = 100;
  document.costControls.quotaOwnerEmails = [];
  const error = messages(document, context);
  assert.match(error, /budgetAlertsNotHardCap/u);
  assert.match(error, /maxInstances 20/u);
  assert.match(error, /quotaOwnerEmails/u);
});

test('Hosting evidence binds source, public HTTPS identity, deletion, and security review', () => {
  const { document, context } = validFixture();
  document.hosting.publicBaseUrl = 'http://localhost:5000/';
  document.hosting.firebaseConfigSha256 = 'f'.repeat(64);
  document.hosting.deletionSagaTested = false;
  const error = messages(document, context);
  assert.match(error, /provisioned HTTPS origin/u);
  assert.match(error, /must match checked-out firebase\.json/u);
  assert.match(error, /deletionSagaTested/u);
});

test('prohibited Firebase products and any direct mobile Firestore path fail the release', () => {
  const { document, context } = validFixture();
  document.prohibitedServices.analyticsEnabled = true;
  document.prohibitedServices.directMobileFirestorePathPresent = true;
  const error = messages(document, context);
  assert.match(error, /analyticsEnabled/u);
  assert.match(error, /directMobileFirestorePathPresent/u);
});

test('live signed-channel, replay, denial, deletion, and leak checks are mandatory', () => {
  const { document, context } = validFixture();
  document.verification.iosSignedAttestationPassed = false;
  document.verification.replayedLimitedUseTokenRejected = false;
  document.verification.contentLeakScanFindingCount = 1;
  const error = messages(document, context);
  assert.match(error, /iosSignedAttestationPassed/u);
  assert.match(error, /replayedLimitedUseTokenRejected/u);
  assert.match(error, /zero findings/u);
});

test('continuity recovery forbids ledger restore and requires a reviewed new generation', () => {
  const { document, context } = validFixture();
  document.operations.disasterCreatesReviewedNewGeneration = false;
  document.operations.userLedgerBackupsNeverRestored = false;
  document.operations.ledgerGeneration = 'different-generation';
  const error = messages(document, context);
  assert.match(error, /disasterCreatesReviewedNewGeneration/u);
  assert.match(error, /userLedgerBackupsNeverRestored/u);
  assert.match(error, /must match Firestore continuity/u);
});

test('every external evidence file must exist, match its digest, and outlive the package', () => {
  const { document, context } = validFixture();
  const reference = document.evidenceReferences[0];
  context.evidenceFiles.delete(reference.path);
  context.evidenceFiles.set('unreviewed-export.json', {
    sha256: digest('unreviewed'),
    bytes: 10,
  });
  reference.validUntil = '2026-07-20T00:00:00Z';
  const error = messages(document, context);
  assert.match(error, /was not collected/u);
  assert.match(error, /expires before/u);
  assert.match(error, /unreferenced file/u);
});

test('all seven approval roles require distinct people and matching expiring approval files', () => {
  const { document, context } = validFixture();
  document.approvals[1].approver = document.approvals[0].approver;
  document.approvals[2].decision = 'pending';
  document.approvals[3].validUntil = '2027-01-01T00:00:00Z';
  const error = messages(document, context);
  assert.match(error, /distinct accountable approvers/u);
  assert.match(error, /decision must be approved/u);
  assert.match(error, /must equal package validUntil/u);
});

test('evidence collection rejects symbolic links instead of following them', () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'birthday-cloud-evidence-'));
  const outside = path.join(root, '..', 'outside-cloud-evidence.txt');
  writeFileSync(outside, 'outside', 'utf8');
  mkdirSync(path.join(root, 'nested'));
  symlinkSync(outside, path.join(root, 'nested', 'escape'));
  assert.throws(() => collectCloudEvidenceFiles(root), /symbolic link/u);
});

test('evidence collection rejects empty files that cannot substantiate a claim', () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'birthday-cloud-evidence-'));
  writeFileSync(path.join(root, 'empty-evidence.json'), '', 'utf8');
  assert.throws(
    () => collectCloudEvidenceFiles(root),
    /unsafe or oversized regular file/u,
  );
});

test('only an Ed25519 signature from the repository-pinned authority can authorize raw evidence bytes', () => {
  const evidenceBytes = Buffer.from('{"approved":true}\n', 'utf8');
  const { publicKey, privateKey } = generateKeyPairSync('ed25519');
  const publicKeyBytes = publicKey.export({ type: 'spki', format: 'pem' });
  const spki = publicKey.export({ type: 'spki', format: 'der' });
  const signatureBytes = sign(null, evidenceBytes, privateKey);
  const pin = {
    schemaVersion: 1,
    algorithm: 'Ed25519',
    publicKeySpkiSha256: digest(spki),
  };
  assert.equal(
    verifyCloudReleaseAuthority({
      evidenceBytes,
      signatureBytes,
      publicKeyBytes,
      pin,
    }),
    true,
  );
  assert.throws(
    () =>
      verifyCloudReleaseAuthority({
        evidenceBytes: Buffer.from('{}\n'),
        signatureBytes,
        publicKeyBytes,
        pin,
      }),
    /signature is invalid/u,
  );
  assert.throws(
    () =>
      verifyCloudReleaseAuthority({
        evidenceBytes,
        signatureBytes,
        publicKeyBytes,
        pin: {
          schemaVersion: 1,
          algorithm: 'Ed25519',
          publicKeySpkiSha256: 'UNPROVISIONED',
        },
      }),
    /not provisioned/u,
  );
  assert.throws(
    () =>
      verifyCloudReleaseAuthority({
        evidenceBytes,
        signatureBytes,
        publicKeyBytes: privateKey.export({ type: 'pkcs8', format: 'pem' }),
        pin,
      }),
    /private-key material/u,
  );
});

test('cloud evidence CLI cannot replace trust, validation time, or source checkout', () => {
  for (const forbiddenOption of ['authority-pin', 'now', 'source-root']) {
    const result = spawnSync(
      process.execPath,
      [
        path.join(ROOT, 'tools/validate-cloud-release-evidence.mjs'),
        `--${forbiddenOption}`,
        forbiddenOption === 'authority-pin'
          ? path.join(ROOT, 'tools/distribution-authority-pin.json')
          : forbiddenOption === 'source-root'
          ? ROOT
          : '2000-01-01T00:00:00Z',
      ],
      { encoding: 'utf8' },
    );
    assert.notEqual(result.status, 0);
    assert.match(
      result.stderr,
      new RegExp(`unknown or duplicated argument: --${forbiddenOption}`, 'u'),
    );
  }
});

test('template and authority pin preserve the intentional external blocker', () => {
  const template = JSON.parse(
    readFileSync(
      path.join(ROOT, 'tools/cloud-release-evidence.template.json'),
      'utf8',
    ),
  );
  const pin = JSON.parse(
    readFileSync(
      path.join(ROOT, 'tools/distribution-authority-pin.json'),
      'utf8',
    ),
  );
  assert.deepEqual(
    template.evidenceReferences.map(reference => reference.id).sort(),
    [...REQUIRED_EVIDENCE_IDS].sort(),
  );
  assert.equal(pin.publicKeySpkiSha256, 'UNPROVISIONED');
});

test('schema and template keep exact top-level and section key inventories in sync', () => {
  const template = JSON.parse(
    readFileSync(
      path.join(ROOT, 'tools/cloud-release-evidence.template.json'),
      'utf8',
    ),
  );
  const schema = JSON.parse(
    readFileSync(
      path.join(ROOT, 'tools/cloud-release-evidence.schema.json'),
      'utf8',
    ),
  );
  assert.deepEqual(Object.keys(template).sort(), [...schema.required].sort());
  for (const section of [
    'source',
    'validity',
    'project',
    'identityAndApis',
    'aiLogic',
    'appCheck',
    'functions',
    'firestore',
    'secrets',
    'iam',
    'observability',
    'costControls',
    'hosting',
    'prohibitedServices',
    'verification',
    'operations',
  ]) {
    assert.equal(schema.$defs[section].additionalProperties, false);
    assert.deepEqual(
      Object.keys(template[section]).sort(),
      [...schema.$defs[section].required].sort(),
      section,
    );
  }
  assert.doesNotMatch(
    JSON.stringify(template),
    /"(?:keyBase64|privateKey|clientSecret|refreshToken|accessToken)":/iu,
  );
});
