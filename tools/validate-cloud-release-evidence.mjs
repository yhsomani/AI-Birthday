#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import {
  createHash,
  createPrivateKey,
  createPublicKey,
  verify as verifySignature,
} from 'node:crypto';
import {
  closeSync,
  constants,
  fstatSync,
  lstatSync,
  openSync,
  readdirSync,
  readFileSync,
  realpathSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const PROJECT_ROOT = fileURLToPath(new URL('../', import.meta.url));
const DEFAULT_AUTHORITY_PIN = path.join(
  PROJECT_ROOT,
  'tools/distribution-authority-pin.json',
);
const SHA256 = /^[0-9a-f]{64}$/u;
const REVISION = /^[0-9a-f]{40}$/u;
const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const PROJECT_NUMBER = /^[1-9][0-9]{5,19}$/u;
const FIREBASE_APP_ID = /^1:[1-9][0-9]{5,19}:(?:android|ios):[0-9a-f]{8,64}$/u;
const OAUTH_CLIENT = /^[0-9]+-[a-z0-9-]+\.apps\.googleusercontent\.com$/u;
const REVERSED_CLIENT = /^com\.googleusercontent\.apps\.[A-Za-z0-9-]+$/u;
const SHA1 = /^[0-9a-f]{40}$/u;
const TEAM_ID = /^[A-Z0-9]{10}$/u;
const SERVICE_ACCOUNT =
  /^[a-z][a-z0-9-]{2,62}@[a-z][a-z0-9-]{4,28}[a-z0-9]\.iam\.gserviceaccount\.com$/u;
const BILLING_ACCOUNT = /^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$/u;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const SAFE_RELATIVE = /^[A-Za-z0-9][A-Za-z0-9._/@()+ -]{0,511}$/u;
const SAFE_ID = /^[a-z0-9][a-z0-9._:-]{2,127}$/u;
const PLACEHOLDER =
  /(?:^|[\s_./:-])(?:required|todo|tbd|placeholder|replace|example|unknown|unprovisioned|pending)(?:$|[\s_./:-])|[<>]/iu;
const MAX_REFERENCE_BYTES = 64 * 1024 * 1024;
const MAX_TREE_BYTES = 128 * 1024 * 1024;
const MAX_VALIDITY_MS = 30 * 24 * 60 * 60 * 1_000;
const MAX_CLOCK_SKEW_MS = 5 * 60 * 1_000;

export const FUNCTIONS_DEPLOYMENT_SOURCE_PATHS = Object.freeze([
  'backend/functions/.npmrc',
  'backend/functions/.nvmrc',
  'backend/functions/package.json',
  'backend/functions/src',
  'backend/functions/tsconfig.build.json',
  'backend/functions/tsconfig.json',
]);

export const HOSTING_DEPLOYMENT_SOURCE_PATHS = Object.freeze([
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

export const EXPECTED_CALLABLE_FUNCTIONS = Object.freeze(
  [
    'accountDeletionReceipt',
    'armAttempt',
    'authorizeSafeRetry',
    'beginSenderTransfer',
    'changeAccountMode',
    'claimOccurrence',
    'claimTest',
    'companionStatus',
    'completeSenderTransfer',
    'coordinationLifecycleStatus',
    'getArmStatus',
    'registerAndroidInstallation',
    'releaseAndroidSender',
    'renewSenderLease',
    'reportTestOutcome',
    'requestAccountDeletion',
    'resetContactDerivedState',
  ].sort(),
);

export const EXPECTED_SCHEDULED_FUNCTIONS = Object.freeze(
  ['sweepCoordinationOperations', 'sweepDeletionDrains'].sort(),
);

export const EXPECTED_TTL_COLLECTION_GROUPS = Object.freeze(
  [
    'armBudgets',
    'armOutcomes',
    'claimRequests',
    'coordinationLatestReceipts',
    'coordinationOperationReceipts',
    'deletionReceipts',
    'deletionTombstones',
    'destinationGuards',
    'installations',
    'occurrenceClaims',
    'occurrenceKeys',
    'testClaims',
  ].sort(),
);

export const REQUIRED_EVIDENCE_IDS = Object.freeze([
  'api-key-restrictions',
  'app-check-android',
  'app-check-ios',
  'app-check-replay',
  'app-check-web',
  'cloud-project-inventory',
  'continuity-dr',
  'data-residency',
  'deletion-live-smoke',
  'firebase-ai-vertex',
  'firebase-auth-deletion',
  'firestore-backup-pitr',
  'firestore-index-contention',
  'firestore-rules-live',
  'firestore-ttl',
  'functions-deployment',
  'functions-live-smoke',
  'hosting-release',
  'iam-effective-permissions',
  'iam-policy',
  'incident-rollback',
  'live-readonly-audit',
  'logging-privacy',
  'oauth-client-inventory',
  'people-consent',
  'prohibited-services',
  'project-ownership-billing',
  'quota-budget',
  'remote-config',
  'secret-keyring-metadata',
  'secret-rotation',
  'signed-channel-smoke',
  'slo-load-cost',
  'source-deployment-provenance',
  'tier-isolation',
  ...[
    'backend',
    'cloud-owner',
    'finance',
    'privacy',
    'release',
    'security',
    'sre',
  ].map(role => `approval-${role}`),
]);

export const REQUIRED_APPROVAL_ROLES = Object.freeze([
  'backend',
  'cloud-owner',
  'finance',
  'privacy',
  'release',
  'security',
  'sre',
]);

const TOP_LEVEL_KEYS = new Set([
  '$schema',
  'schemaVersion',
  'product',
  'status',
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
  'evidenceReferences',
  'approvals',
]);
const SOURCE_KEYS = new Set([
  'revision',
  'repository',
  'clean',
  'projectAboutSha256',
  'firebaseJsonSha256',
  'functionsSourceTreeSha256',
  'functionsLockSha256',
  'firestoreRulesSha256',
  'firestoreIndexesSha256',
  'ttlPoliciesSha256',
  'hostingSourceTreeSha256',
  'deploymentProvenanceEvidenceId',
]);
const VALIDITY_KEYS = new Set(['generatedAt', 'validUntil', 'maximumAgeDays']);
const PROJECT_KEYS = new Set([
  'tier',
  'projectId',
  'projectNumber',
  'androidAppId',
  'iosAppId',
  'webAppId',
  'androidPackage',
  'iosBundle',
  'functionsRegion',
  'firestoreLocation',
  'retainedProjectId',
  'retainedProjectAssignment',
  'noCrossTierSharing',
  'evidenceIds',
]);
const IDENTITY_KEYS = new Set([
  'firebaseGoogleProviderEnabled',
  'peopleApiEnabled',
  'peopleScope',
  'webOauthClientId',
  'androidOauthClients',
  'iosOauthClient',
  'oneVisibleGoogleChoice',
  'noOauthClientSecretInApps',
  'accountLifecycleTestsPassed',
  'peopleConsentTestsPassed',
  'publicApiKeysRestricted',
  'firebaseClientConfigsRefreshed',
  'evidenceIds',
]);
const ANDROID_OAUTH_KEYS = new Set([
  'channel',
  'clientId',
  'applicationId',
  'signingCertificateSha1',
]);
const IOS_OAUTH_KEYS = new Set([
  'clientId',
  'bundleId',
  'teamId',
  'reversedClientId',
]);
const AI_KEYS = new Set([
  'provider',
  'sdkSurface',
  'model',
  'location',
  'apiService',
  'authenticatedUsersOnly',
  'remoteConfigKey',
  'defaultOff',
  'activatedCanonicalTrueOnly',
  'monitoring',
  'generativeLanguageApiAllowedOnMobileKeys',
  'billingEnabled',
  'providerQuotasConfigured',
  'perUserRateLimitConfigured',
  'budgetAlertsConfigured',
  'killSwitchTested',
  'providerTermsApproved',
  'dataGovernanceApproved',
  'modelAvailabilityCheckedAt',
  'evidenceIds',
]);
const APP_CHECK_KEYS = new Set([
  'androidRegistrations',
  'iosRegistrations',
  'webRegistration',
  'aiLogicEnforced',
  'callableBaselineEnforced',
  'limitedUseReplayForStateChanges',
  'limitedUseReplayForCompanionStatus',
  'signedChannelsTested',
  'evidenceIds',
]);
const ANDROID_APP_CHECK_KEYS = new Set([
  'firebaseAppId',
  'channel',
  'distributionScope',
  'signingCertificateSha256',
  'provider',
  'playRecognizedRequired',
  'licensedRequired',
  'deviceIntegrityRequired',
  'debugProvider',
]);
const IOS_APP_CHECK_KEYS = new Set([
  'firebaseAppId',
  'provider',
  'teamId',
  'productionEnvironment',
  'fallbackPolicyApproved',
  'debugProvider',
]);
const WEB_APP_CHECK_KEYS = new Set([
  'firebaseAppId',
  'provider',
  'siteKeySha256',
  'debugProvider',
]);
const FUNCTIONS_KEYS = new Set([
  'generation',
  'region',
  'runtime',
  'runtimeServiceAccount',
  'deployedSourceRevision',
  'callableNames',
  'scheduledNames',
  'commonOptions',
  'scheduledOptions',
  'requestBodiesLogged',
  'rawExceptionsLogged',
  'deploymentEvidenceId',
  'liveSmokeEvidenceId',
]);
const COMMON_OPTIONS_KEYS = new Set([
  'enforceAppCheck',
  'consumeAppCheckToken',
  'timeoutSeconds',
  'memory',
  'minInstances',
  'maxInstances',
  'concurrency',
]);
const SCHEDULED_OPTIONS_KEYS = new Set([
  'schedule',
  'timeZone',
  'timeoutSeconds',
  'memory',
  'maxInstances',
]);
const FIRESTORE_KEYS = new Set([
  'databaseId',
  'databaseType',
  'location',
  'directClientRules',
  'deployedRulesSha256',
  'deployedIndexesSha256',
  'deployedTtlPolicySha256',
  'ttlCollectionGroups',
  'logicalExpiryEnforcedTransactionally',
  'pointInTimeRecovery',
  'backupScheduleCount',
  'recursiveDeletionVerified',
  'authDeletionAbsenceVerified',
  'continuityState',
  'ledgerGeneration',
  'emptyLedgerTreatedAsNoSendProof',
  'evidenceIds',
]);
const SECRET_KEYS = new Set([
  'secretName',
  'secretValueIncluded',
  'currentSecretManagerVersionId',
  'previousSecretManagerVersionId',
  'currentKeyLabel',
  'previousKeyLabel',
  'currentCreatedAt',
  'previousLastWriteAt',
  'previousRetainUntil',
  'longestAliasRetentionDays',
  'rotationCadenceDays',
  'replicationPolicyApproved',
  'runtimeAccessorOnly',
  'repositoryAndCiValueAbsent',
  'metadataEvidenceId',
  'rotationEvidenceId',
]);
const IAM_KEYS = new Set([
  'runtimeServiceAccount',
  'auditServiceAccount',
  'workloadIdentityFederation',
  'serviceAccountKeyFilesUsed',
  'runtimeUserManagedKeyCount',
  'auditUserManagedKeyCount',
  'broadPrimitiveRolesAbsent',
  'wildcardPermissionsAbsent',
  'capabilities',
  'reviewedAt',
  'policyEvidenceId',
  'effectivePermissionsEvidenceId',
]);
const IAM_CAPABILITY_KEYS = new Set([
  'firestoreCoordination',
  'firebaseAuthDeletion',
  'secretVersionAccessForKeyringOnly',
  'appCheckTokenVerification',
  'schedulerInvocation',
]);
const OBSERVABILITY_KEYS = new Set([
  'requestBodiesExcluded',
  'rawExceptionsExcluded',
  'contactMessagePromptContentExcluded',
  'aiMonitoring',
  'aiPromptResponseExcluded',
  'defaultLoggingExclusionName',
  'applicationLogRetentionDays',
  'dataAccessAuditRetentionDays',
  'deletionCorrelationAccessRestricted',
  'logSinksReviewed',
  'unapprovedCrashAndAnalyticsAbsent',
  'evidenceId',
]);
const COST_KEYS = new Set([
  'billingAccountId',
  'billingEnabled',
  'monthlyBudget',
  'budgetAlertsNotHardCap',
  'providerHardQuotasConfigured',
  'quotas',
  'quotaOwnerEmails',
  'incidentContactEmails',
  'evidenceId',
]);
const MONEY_KEYS = new Set(['currencyCode', 'units']);
const QUOTA_KEYS = new Set(['name', 'limit', 'unit']);
const HOSTING_KEYS = new Set([
  'siteId',
  'deployedVersionId',
  'publicBaseUrl',
  'privacyUrl',
  'termsUrl',
  'supportUrl',
  'deletionUrl',
  'identityVerifiedSupportUrl',
  'firebaseConfigSha256',
  'releaseConfigSha256',
  'deployedArtifactSha256',
  'recaptchaEnterpriseAppCheckRegistered',
  'securityHeadersVerified',
  'legalCopyApproved',
  'hindiCopyApproved',
  'deletionSagaTested',
  'evidenceId',
]);
const PROHIBITED_KEYS = new Set([
  'realtimeDatabaseEnabled',
  'cloudStorageEnabled',
  'fcmEnabled',
  'analyticsEnabled',
  'adSdkEnabled',
  'crashlyticsEnabled',
  'performanceMonitoringEnabled',
  'directMobileFirestorePathPresent',
  'rawContactOrMessageCloudStorePresent',
  'evidenceId',
]);
const VERIFICATION_KEYS = new Set([
  'firebaseAuthLifecyclePassed',
  'peopleConsentAndRevocationPassed',
  'androidSignedAttestationPassed',
  'iosSignedAttestationPassed',
  'androidAiLogicCallPassed',
  'iosAiLogicCallPassed',
  'stateChangingCallableProbePassed',
  'companionStatusProbePassed',
  'replayedLimitedUseTokenRejected',
  'directFirestoreAnonymousDenied',
  'directFirestoreAuthenticatedDenied',
  'ttlPoliciesObserved',
  'recursiveDeletionAbsencePassed',
  'signedOutReceiptPassed',
  'contentLeakScanFindingCount',
  'debugProviderFindingCount',
  'liveAuditEvidenceId',
  'signedChannelEvidenceId',
  'deletionEvidenceId',
]);
const OPERATIONS_KEYS = new Set([
  'globalControlState',
  'ledgerGeneration',
  'emptyLedgerNeverUsedAsNoSendProof',
  'disasterCreatesReviewedNewGeneration',
  'reRegistrationAndReapprovalRequiredAfterDisaster',
  'sameDateAutomationBlockedAfterDisaster',
  'userLedgerBackupsNeverRestored',
  'rtoMinutes',
  'availabilityTargetPercent',
  'p95CallableLatencyMs',
  'errorBudgetWindowDays',
  'deletionSlaHours',
  'loadContentionTestPassed',
  'regionalFailureTestPassed',
  'incidentRollbackReady',
  'continuityEvidenceId',
  'sloEvidenceId',
  'incidentEvidenceId',
]);
const REFERENCE_KEYS = new Set([
  'id',
  'path',
  'sha256',
  'capturedAt',
  'validUntil',
  'issuer',
  'kind',
]);
const APPROVAL_KEYS = new Set([
  'role',
  'approver',
  'decision',
  'approvedAt',
  'validUntil',
  'evidenceId',
]);

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const exactKeys = (value, keys, label, errors) => {
  if (!isObject(value)) {
    errors.push(`${label} must be an object`);
    return false;
  }
  const actual = Object.keys(value);
  if (actual.length !== keys.size || actual.some(key => !keys.has(key))) {
    errors.push(`${label} fields do not match the exact schema`);
    return false;
  }
  return true;
};

const requireTrue = (value, label, errors) => {
  if (value !== true) errors.push(`${label} must be true`);
};

const requireFalse = (value, label, errors) => {
  if (value !== false) errors.push(`${label} must be false`);
};

const requirePattern = (value, pattern, label, errors) => {
  if (typeof value !== 'string' || !pattern.test(value)) {
    errors.push(`${label} is invalid`);
  }
};

const requireText = (value, label, errors) => {
  if (
    typeof value !== 'string' ||
    value.trim().length < 3 ||
    value.length > 512 ||
    PLACEHOLDER.test(value)
  ) {
    errors.push(`${label} must be a provisioned, non-placeholder value`);
  }
};

const requireDigest = (value, label, errors) => {
  if (
    typeof value !== 'string' ||
    !SHA256.test(value) ||
    /^0{64}$/u.test(value)
  ) {
    errors.push(`${label} must be a non-zero lowercase SHA-256 digest`);
  }
};

const parseInstant = (value, label, errors) => {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  const parsed = Date.parse(value);
  if (
    !Number.isFinite(parsed) ||
    new Date(parsed).toISOString().slice(0, 19) !== value.slice(0, 19)
  ) {
    errors.push(`${label} must be a real RFC 3339 UTC instant`);
    return null;
  }
  return parsed;
};

const equalStringSet = (actual, expected) =>
  Array.isArray(actual) &&
  actual.length === expected.length &&
  new Set(actual).size === actual.length &&
  [...actual]
    .sort()
    .every((value, index) => value === [...expected].sort()[index]);

const requireEvidenceIds = (actual, expected, label, errors) => {
  if (!equalStringSet(actual, expected)) {
    errors.push(`${label} must contain the exact required evidence IDs`);
  }
};

const requireHttpsUrl = (value, label, errors, originOnly = false) => {
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    errors.push(`${label} must be a provisioned HTTPS URL`);
    return null;
  }
  if (
    parsed.protocol !== 'https:' ||
    parsed.username !== '' ||
    parsed.password !== '' ||
    parsed.hostname === 'localhost' ||
    parsed.hostname.endsWith('.invalid') ||
    parsed.hostname.endsWith('.example') ||
    parsed.hostname.endsWith('.test') ||
    PLACEHOLDER.test(value) ||
    (originOnly &&
      (parsed.pathname !== '/' || parsed.search !== '' || parsed.hash !== ''))
  ) {
    errors.push(
      `${label} must be a provisioned HTTPS${originOnly ? ' origin' : ' URL'}`,
    );
    return null;
  }
  return parsed;
};

const validateSource = (value, context, errors) => {
  if (!exactKeys(value, SOURCE_KEYS, 'source', errors)) return;
  requirePattern(value.revision, REVISION, 'source.revision', errors);
  if (value.repository !== 'https://github.com/yhsomani/AI-Birthday.git') {
    errors.push('source.repository must be the authoritative repository');
  }
  requireTrue(value.clean, 'source.clean', errors);
  for (const field of [
    'projectAboutSha256',
    'firebaseJsonSha256',
    'functionsSourceTreeSha256',
    'functionsLockSha256',
    'firestoreRulesSha256',
    'firestoreIndexesSha256',
    'ttlPoliciesSha256',
    'hostingSourceTreeSha256',
  ]) {
    requireDigest(value[field], `source.${field}`, errors);
    if (context.expectedSource?.[field] !== value[field]) {
      errors.push(`source.${field} does not match the checked-out source`);
    }
  }
  if (context.expectedSource?.revision !== value.revision) {
    errors.push('source.revision does not match checked-out HEAD');
  }
  if (value.deploymentProvenanceEvidenceId !== 'source-deployment-provenance') {
    errors.push('source deployment provenance evidence is missing');
  }
};

const validateValidity = (value, nowMs, errors) => {
  if (!exactKeys(value, VALIDITY_KEYS, 'validity', errors)) return null;
  const generatedAt = parseInstant(
    value.generatedAt,
    'validity.generatedAt',
    errors,
  );
  const validUntil = parseInstant(
    value.validUntil,
    'validity.validUntil',
    errors,
  );
  if (value.maximumAgeDays !== 30) {
    errors.push('validity.maximumAgeDays must be exactly 30');
  }
  if (generatedAt !== null && validUntil !== null) {
    if (
      validUntil <= generatedAt ||
      validUntil - generatedAt > MAX_VALIDITY_MS
    ) {
      errors.push(
        'cloud release evidence validity must be positive and no longer than 30 days',
      );
    }
    if (generatedAt > nowMs + MAX_CLOCK_SKEW_MS || validUntil <= nowMs) {
      errors.push('cloud release evidence is future-dated or expired');
    }
  }
  return { generatedAt, validUntil };
};

const validateProject = (value, errors) => {
  if (!exactKeys(value, PROJECT_KEYS, 'project', errors)) return;
  if (value.tier !== 'production')
    errors.push('project.tier must be production');
  requirePattern(value.projectId, PROJECT_ID, 'project.projectId', errors);
  requirePattern(
    value.projectNumber,
    PROJECT_NUMBER,
    'project.projectNumber',
    errors,
  );
  requirePattern(
    value.androidAppId,
    FIREBASE_APP_ID,
    'project.androidAppId',
    errors,
  );
  requirePattern(value.iosAppId, FIREBASE_APP_ID, 'project.iosAppId', errors);
  requirePattern(
    value.webAppId,
    /^1:[1-9][0-9]{5,19}:web:[0-9a-f]{8,64}$/u,
    'project.webAppId',
    errors,
  );
  if (!String(value.androidAppId).includes(':android:'))
    errors.push('project.androidAppId must be Android');
  if (!String(value.iosAppId).includes(':ios:'))
    errors.push('project.iosAppId must be iOS');
  for (const field of ['androidAppId', 'iosAppId', 'webAppId']) {
    if (!String(value[field]).startsWith(`1:${value.projectNumber}:`)) {
      errors.push(`project.${field} does not belong to project.projectNumber`);
    }
  }
  if (value.androidPackage !== 'com.yashsomani.birthdayautopilot') {
    errors.push('project.androidPackage must be the production application ID');
  }
  if (value.iosBundle !== 'com.yashsomani.birthdayautopilot') {
    errors.push('project.iosBundle must be the production bundle ID');
  }
  if (
    value.functionsRegion !== 'asia-south1' ||
    value.firestoreLocation !== 'asia-south1'
  ) {
    errors.push(
      'project Functions and Firestore locations must match the approved asia-south1 source contract',
    );
  }
  if (value.retainedProjectId !== 'relateai-birthday-ysomani') {
    errors.push(
      'project.retainedProjectId must identify the retained project under review',
    );
  }
  if (
    !['development', 'staging', 'production'].includes(
      value.retainedProjectAssignment,
    )
  ) {
    errors.push(
      'project.retainedProjectAssignment must name exactly one approved tier',
    );
  }
  if (
    (value.projectId === value.retainedProjectId) !==
    (value.retainedProjectAssignment === 'production')
  ) {
    errors.push(
      'retained project assignment contradicts the production project ID',
    );
  }
  requireTrue(value.noCrossTierSharing, 'project.noCrossTierSharing', errors);
  requireEvidenceIds(
    value.evidenceIds,
    ['cloud-project-inventory', 'data-residency', 'tier-isolation'],
    'project.evidenceIds',
    errors,
  );
};

const validateIdentity = (value, project, errors) => {
  if (!exactKeys(value, IDENTITY_KEYS, 'identityAndApis', errors)) return;
  for (const field of [
    'firebaseGoogleProviderEnabled',
    'peopleApiEnabled',
    'oneVisibleGoogleChoice',
    'noOauthClientSecretInApps',
    'accountLifecycleTestsPassed',
    'peopleConsentTestsPassed',
    'publicApiKeysRestricted',
    'firebaseClientConfigsRefreshed',
  ])
    requireTrue(value[field], `identityAndApis.${field}`, errors);
  if (
    value.peopleScope !== 'https://www.googleapis.com/auth/contacts.readonly'
  ) {
    errors.push('identityAndApis.peopleScope must be contacts.readonly only');
  }
  requirePattern(
    value.webOauthClientId,
    OAUTH_CLIENT,
    'identityAndApis.webOauthClientId',
    errors,
  );
  const oauthClientIds = new Set();
  if (
    typeof value.webOauthClientId === 'string' &&
    !value.webOauthClientId.startsWith(`${project?.projectNumber}-`)
  ) {
    errors.push(
      'identityAndApis.webOauthClientId does not belong to the production project',
    );
  }
  oauthClientIds.add(value.webOauthClientId);
  if (
    !Array.isArray(value.androidOauthClients) ||
    value.androidOauthClients.length < 1
  ) {
    errors.push(
      'identityAndApis.androidOauthClients must cover at least one signed production channel',
    );
  } else {
    const channels = new Set();
    for (const [index, client] of value.androidOauthClients.entries()) {
      const label = `identityAndApis.androidOauthClients[${index}]`;
      if (!exactKeys(client, ANDROID_OAUTH_KEYS, label, errors)) continue;
      if (
        !['google-play', 'direct-managed'].includes(client.channel) ||
        channels.has(client.channel)
      ) {
        errors.push(`${label}.channel is invalid or duplicated`);
      }
      channels.add(client.channel);
      requirePattern(
        client.clientId,
        OAUTH_CLIENT,
        `${label}.clientId`,
        errors,
      );
      if (
        typeof client.clientId === 'string' &&
        !client.clientId.startsWith(`${project?.projectNumber}-`)
      ) {
        errors.push(
          `${label}.clientId does not belong to the production project`,
        );
      }
      if (oauthClientIds.has(client.clientId))
        errors.push(`${label}.clientId is duplicated across OAuth clients`);
      oauthClientIds.add(client.clientId);
      if (client.applicationId !== project?.androidPackage)
        errors.push(`${label}.applicationId does not match project`);
      requirePattern(
        client.signingCertificateSha1,
        SHA1,
        `${label}.signingCertificateSha1`,
        errors,
      );
    }
  }
  if (
    exactKeys(
      value.iosOauthClient,
      IOS_OAUTH_KEYS,
      'identityAndApis.iosOauthClient',
      errors,
    )
  ) {
    requirePattern(
      value.iosOauthClient.clientId,
      OAUTH_CLIENT,
      'identityAndApis.iosOauthClient.clientId',
      errors,
    );
    if (
      typeof value.iosOauthClient.clientId === 'string' &&
      !value.iosOauthClient.clientId.startsWith(`${project?.projectNumber}-`)
    ) {
      errors.push(
        'identityAndApis.iosOauthClient.clientId does not belong to the production project',
      );
    }
    if (oauthClientIds.has(value.iosOauthClient.clientId))
      errors.push(
        'identityAndApis.iosOauthClient.clientId is duplicated across OAuth clients',
      );
    if (value.iosOauthClient.bundleId !== project?.iosBundle)
      errors.push(
        'identityAndApis.iosOauthClient.bundleId does not match project',
      );
    requirePattern(
      value.iosOauthClient.teamId,
      TEAM_ID,
      'identityAndApis.iosOauthClient.teamId',
      errors,
    );
    requirePattern(
      value.iosOauthClient.reversedClientId,
      REVERSED_CLIENT,
      'identityAndApis.iosOauthClient.reversedClientId',
      errors,
    );
  }
  requireEvidenceIds(
    value.evidenceIds,
    [
      'api-key-restrictions',
      'firebase-auth-deletion',
      'oauth-client-inventory',
      'people-consent',
    ],
    'identityAndApis.evidenceIds',
    errors,
  );
};

const validateAiLogic = (value, nowMs, errors) => {
  if (!exactKeys(value, AI_KEYS, 'aiLogic', errors)) return;
  const exact = {
    provider: 'vertex-ai',
    sdkSurface: 'firebase-ai-logic',
    model: 'gemini-3.5-flash',
    location: 'global',
    apiService: 'firebasevertexai.googleapis.com',
    remoteConfigKey: 'gemini_suggestions_enabled',
    monitoring: 'off',
  };
  for (const [field, expected] of Object.entries(exact)) {
    if (value[field] !== expected)
      errors.push(`aiLogic.${field} must be ${expected}`);
  }
  for (const field of [
    'authenticatedUsersOnly',
    'defaultOff',
    'activatedCanonicalTrueOnly',
    'billingEnabled',
    'providerQuotasConfigured',
    'perUserRateLimitConfigured',
    'budgetAlertsConfigured',
    'killSwitchTested',
    'providerTermsApproved',
    'dataGovernanceApproved',
  ])
    requireTrue(value[field], `aiLogic.${field}`, errors);
  requireFalse(
    value.generativeLanguageApiAllowedOnMobileKeys,
    'aiLogic.generativeLanguageApiAllowedOnMobileKeys',
    errors,
  );
  const checkedAt = parseInstant(
    value.modelAvailabilityCheckedAt,
    'aiLogic.modelAvailabilityCheckedAt',
    errors,
  );
  if (
    checkedAt !== null &&
    (checkedAt > nowMs + MAX_CLOCK_SKEW_MS ||
      nowMs - checkedAt > 24 * 60 * 60 * 1_000)
  ) {
    errors.push(
      'AI model availability evidence must be no older than 24 hours',
    );
  }
  requireEvidenceIds(
    value.evidenceIds,
    ['firebase-ai-vertex', 'quota-budget', 'remote-config'],
    'aiLogic.evidenceIds',
    errors,
  );
};

const validateAppCheck = (value, project, identity, errors) => {
  if (!exactKeys(value, APP_CHECK_KEYS, 'appCheck', errors)) return;
  for (const field of [
    'aiLogicEnforced',
    'callableBaselineEnforced',
    'limitedUseReplayForStateChanges',
    'limitedUseReplayForCompanionStatus',
    'signedChannelsTested',
  ])
    requireTrue(value[field], `appCheck.${field}`, errors);
  if (
    !Array.isArray(value.androidRegistrations) ||
    value.androidRegistrations.length < 1
  ) {
    errors.push(
      'appCheck.androidRegistrations must cover a signed production channel',
    );
  } else {
    const scopes = new Set();
    const channels = new Set();
    const certificates = new Set();
    for (const [index, registration] of value.androidRegistrations.entries()) {
      const label = `appCheck.androidRegistrations[${index}]`;
      if (!exactKeys(registration, ANDROID_APP_CHECK_KEYS, label, errors))
        continue;
      if (registration.firebaseAppId !== project?.androidAppId)
        errors.push(`${label}.firebaseAppId does not match project`);
      if (registration.provider !== 'play-integrity')
        errors.push(`${label}.provider must be play-integrity`);
      if (
        !['google-play', 'direct-managed'].includes(registration.channel) ||
        channels.has(registration.channel)
      ) {
        errors.push(`${label}.channel is invalid or duplicated`);
      }
      channels.add(registration.channel);
      requireDigest(
        registration.signingCertificateSha256,
        `${label}.signingCertificateSha256`,
        errors,
      );
      if (certificates.has(registration.signingCertificateSha256))
        errors.push(`${label}.signingCertificateSha256 is duplicated`);
      certificates.add(registration.signingCertificateSha256);
      requireFalse(
        registration.debugProvider,
        `${label}.debugProvider`,
        errors,
      );
      const expected = {
        'outside-play-only': [false, false, true],
        'play-only': [true, true, false],
        mixed: [true, false, false],
      }[registration.distributionScope];
      if (expected === undefined) {
        errors.push(`${label}.distributionScope is invalid`);
      } else if (
        registration.playRecognizedRequired !== expected[0] ||
        registration.licensedRequired !== expected[1] ||
        registration.deviceIntegrityRequired !== expected[2]
      ) {
        errors.push(
          `${label} Play Integrity settings contradict its distribution scope`,
        );
      }
      scopes.add(registration.distributionScope);
    }
    if (scopes.size !== 1) {
      errors.push(
        'all Android App Check registrations must use one consistent distribution scope',
      );
    }
    const distributionScope = [...scopes][0];
    const expectedChannels = {
      'outside-play-only': ['direct-managed'],
      'play-only': ['google-play'],
      mixed: ['direct-managed', 'google-play'],
    }[distributionScope];
    if (
      expectedChannels !== undefined &&
      !equalStringSet([...channels], expectedChannels)
    ) {
      errors.push(
        'Android App Check channels do not fully cover the distribution scope',
      );
    }
    const oauthChannels = Array.isArray(identity?.androidOauthClients)
      ? identity.androidOauthClients.map(client => client.channel)
      : [];
    if (!equalStringSet([...channels], oauthChannels)) {
      errors.push(
        'Android App Check and OAuth signed-channel inventories do not match',
      );
    }
  }
  if (
    !Array.isArray(value.iosRegistrations) ||
    value.iosRegistrations.length !== 1
  ) {
    errors.push(
      'appCheck.iosRegistrations must contain the exact production iOS registration',
    );
  } else {
    const registration = value.iosRegistrations[0];
    if (
      exactKeys(
        registration,
        IOS_APP_CHECK_KEYS,
        'appCheck.iosRegistrations[0]',
        errors,
      )
    ) {
      if (registration.firebaseAppId !== project?.iosAppId)
        errors.push('iOS App Check Firebase app does not match project');
      if (registration.provider !== 'app-attest')
        errors.push(
          'iOS App Check provider must match the compiled App Attest provider',
        );
      requirePattern(
        registration.teamId,
        TEAM_ID,
        'appCheck.iosRegistrations[0].teamId',
        errors,
      );
      if (registration.teamId !== identity?.iosOauthClient?.teamId) {
        errors.push(
          'iOS App Check team ID does not match the iOS OAuth client',
        );
      }
      requireTrue(
        registration.productionEnvironment,
        'appCheck.iosRegistrations[0].productionEnvironment',
        errors,
      );
      requireTrue(
        registration.fallbackPolicyApproved,
        'appCheck.iosRegistrations[0].fallbackPolicyApproved',
        errors,
      );
      requireFalse(
        registration.debugProvider,
        'appCheck.iosRegistrations[0].debugProvider',
        errors,
      );
    }
  }
  if (
    exactKeys(
      value.webRegistration,
      WEB_APP_CHECK_KEYS,
      'appCheck.webRegistration',
      errors,
    )
  ) {
    if (value.webRegistration.firebaseAppId !== project?.webAppId) {
      errors.push('web App Check Firebase app does not match project');
    }
    if (value.webRegistration.provider !== 'recaptcha-enterprise') {
      errors.push('web App Check provider must be recaptcha-enterprise');
    }
    requireDigest(
      value.webRegistration.siteKeySha256,
      'appCheck.webRegistration.siteKeySha256',
      errors,
    );
    requireFalse(
      value.webRegistration.debugProvider,
      'appCheck.webRegistration.debugProvider',
      errors,
    );
  }
  requireEvidenceIds(
    value.evidenceIds,
    [
      'app-check-android',
      'app-check-ios',
      'app-check-replay',
      'app-check-web',
      'signed-channel-smoke',
    ],
    'appCheck.evidenceIds',
    errors,
  );
};

const validateFunctions = (value, source, errors) => {
  if (!exactKeys(value, FUNCTIONS_KEYS, 'functions', errors)) return;
  if (
    value.generation !== 2 ||
    value.region !== 'asia-south1' ||
    value.runtime !== 'nodejs22'
  ) {
    errors.push(
      'functions generation, region, and runtime must match deployed source',
    );
  }
  requirePattern(
    value.runtimeServiceAccount,
    SERVICE_ACCOUNT,
    'functions.runtimeServiceAccount',
    errors,
  );
  if (value.deployedSourceRevision !== source?.revision)
    errors.push('functions.deployedSourceRevision must equal source.revision');
  if (!equalStringSet(value.callableNames, EXPECTED_CALLABLE_FUNCTIONS))
    errors.push(
      'functions.callableNames do not match the exact callable inventory',
    );
  if (!equalStringSet(value.scheduledNames, EXPECTED_SCHEDULED_FUNCTIONS))
    errors.push(
      'functions.scheduledNames do not match the exact scheduler inventory',
    );
  if (
    exactKeys(
      value.commonOptions,
      COMMON_OPTIONS_KEYS,
      'functions.commonOptions',
      errors,
    )
  ) {
    const expected = {
      enforceAppCheck: true,
      consumeAppCheckToken: true,
      timeoutSeconds: 30,
      memory: '256MiB',
      minInstances: 0,
      maxInstances: 20,
      concurrency: 20,
    };
    for (const [field, expectedValue] of Object.entries(expected)) {
      if (value.commonOptions[field] !== expectedValue)
        errors.push(`functions.commonOptions.${field} does not match source`);
    }
  }
  if (
    exactKeys(
      value.scheduledOptions,
      SCHEDULED_OPTIONS_KEYS,
      'functions.scheduledOptions',
      errors,
    )
  ) {
    const expected = {
      schedule: 'every 1 minutes',
      timeZone: 'Etc/UTC',
      timeoutSeconds: 300,
      memory: '256MiB',
      maxInstances: 1,
    };
    for (const [field, expectedValue] of Object.entries(expected)) {
      if (value.scheduledOptions[field] !== expectedValue)
        errors.push(
          `functions.scheduledOptions.${field} does not match source`,
        );
    }
  }
  requireFalse(
    value.requestBodiesLogged,
    'functions.requestBodiesLogged',
    errors,
  );
  requireFalse(
    value.rawExceptionsLogged,
    'functions.rawExceptionsLogged',
    errors,
  );
  if (
    value.deploymentEvidenceId !== 'functions-deployment' ||
    value.liveSmokeEvidenceId !== 'functions-live-smoke'
  ) {
    errors.push('functions deployment and live-smoke evidence IDs are invalid');
  }
};

const validateFirestore = (value, source, project, errors) => {
  if (!exactKeys(value, FIRESTORE_KEYS, 'firestore', errors)) return;
  if (
    value.databaseId !== '(default)' ||
    value.databaseType !== 'FIRESTORE_NATIVE'
  )
    errors.push(
      'firestore database identity/type must match the approved control plane',
    );
  if (value.location !== project?.firestoreLocation)
    errors.push('firestore.location must match project.firestoreLocation');
  if (value.directClientRules !== 'deny-all')
    errors.push('firestore.directClientRules must be deny-all');
  for (const [field, sourceField] of [
    ['deployedRulesSha256', 'firestoreRulesSha256'],
    ['deployedIndexesSha256', 'firestoreIndexesSha256'],
    ['deployedTtlPolicySha256', 'ttlPoliciesSha256'],
  ]) {
    requireDigest(value[field], `firestore.${field}`, errors);
    if (value[field] !== source?.[sourceField])
      errors.push(`firestore.${field} does not match checked-out source`);
  }
  if (
    !equalStringSet(value.ttlCollectionGroups, EXPECTED_TTL_COLLECTION_GROUPS)
  )
    errors.push(
      'firestore.ttlCollectionGroups do not match the exact retention policy',
    );
  requireTrue(
    value.logicalExpiryEnforcedTransactionally,
    'firestore.logicalExpiryEnforcedTransactionally',
    errors,
  );
  if (
    value.pointInTimeRecovery !== 'disabled' ||
    value.backupScheduleCount !== 0
  )
    errors.push('Firestore PITR and managed backup schedules must be disabled');
  requireTrue(
    value.recursiveDeletionVerified,
    'firestore.recursiveDeletionVerified',
    errors,
  );
  requireTrue(
    value.authDeletionAbsenceVerified,
    'firestore.authDeletionAbsenceVerified',
    errors,
  );
  if (value.continuityState !== 'HEALTHY')
    errors.push('firestore.continuityState must be HEALTHY');
  requirePattern(
    value.ledgerGeneration,
    SAFE_ID,
    'firestore.ledgerGeneration',
    errors,
  );
  requireFalse(
    value.emptyLedgerTreatedAsNoSendProof,
    'firestore.emptyLedgerTreatedAsNoSendProof',
    errors,
  );
  requireEvidenceIds(
    value.evidenceIds,
    [
      'continuity-dr',
      'firestore-backup-pitr',
      'firestore-index-contention',
      'firestore-rules-live',
      'firestore-ttl',
    ],
    'firestore.evidenceIds',
    errors,
  );
};

const validateSecrets = (value, validity, errors) => {
  if (!exactKeys(value, SECRET_KEYS, 'secrets', errors)) return;
  if (value.secretName !== 'COORDINATION_HMAC_KEYRING')
    errors.push('secrets.secretName must be COORDINATION_HMAC_KEYRING');
  requireFalse(
    value.secretValueIncluded,
    'secrets.secretValueIncluded',
    errors,
  );
  for (const field of [
    'currentSecretManagerVersionId',
    'previousSecretManagerVersionId',
  ]) {
    if (
      typeof value[field] !== 'string' ||
      !/^[1-9][0-9]{0,18}$/u.test(value[field])
    )
      errors.push(
        `secrets.${field} must be a Secret Manager numeric version ID`,
      );
  }
  if (
    value.currentSecretManagerVersionId === value.previousSecretManagerVersionId
  )
    errors.push('Secret Manager current and previous versions must differ');
  requirePattern(
    value.currentKeyLabel,
    /^v[1-9][0-9]{0,8}$/u,
    'secrets.currentKeyLabel',
    errors,
  );
  requirePattern(
    value.previousKeyLabel,
    /^v[0-9]{1,9}$/u,
    'secrets.previousKeyLabel',
    errors,
  );
  if (value.currentKeyLabel === value.previousKeyLabel)
    errors.push('current and previous key labels must differ');
  const currentCreatedAt = parseInstant(
    value.currentCreatedAt,
    'secrets.currentCreatedAt',
    errors,
  );
  const previousLastWriteAt = parseInstant(
    value.previousLastWriteAt,
    'secrets.previousLastWriteAt',
    errors,
  );
  const previousRetainUntil = parseInstant(
    value.previousRetainUntil,
    'secrets.previousRetainUntil',
    errors,
  );
  if (
    currentCreatedAt !== null &&
    previousLastWriteAt !== null &&
    currentCreatedAt < previousLastWriteAt
  )
    errors.push(
      'current key cannot predate the previous key last-write boundary',
    );
  if (
    previousLastWriteAt !== null &&
    previousRetainUntil !== null &&
    previousRetainUntil - previousLastWriteAt < 400 * 24 * 60 * 60 * 1_000
  )
    errors.push(
      'previous HMAC key must remain available for at least 400 days after its last write',
    );
  if (
    previousRetainUntil !== null &&
    validity?.validUntil !== null &&
    previousRetainUntil <= validity.validUntil
  )
    errors.push(
      'previous HMAC key retention must extend beyond this release evidence',
    );
  if (value.longestAliasRetentionDays !== 400)
    errors.push('secrets.longestAliasRetentionDays must be 400');
  if (
    !Number.isSafeInteger(value.rotationCadenceDays) ||
    value.rotationCadenceDays < 1 ||
    value.rotationCadenceDays > 365
  )
    errors.push(
      'secrets.rotationCadenceDays must be an approved value from 1 to 365',
    );
  for (const field of [
    'replicationPolicyApproved',
    'runtimeAccessorOnly',
    'repositoryAndCiValueAbsent',
  ])
    requireTrue(value[field], `secrets.${field}`, errors);
  if (
    value.metadataEvidenceId !== 'secret-keyring-metadata' ||
    value.rotationEvidenceId !== 'secret-rotation'
  )
    errors.push('secret metadata/rotation evidence IDs are invalid');
};

const validateIam = (value, project, functionsValue, errors) => {
  if (!exactKeys(value, IAM_KEYS, 'iam', errors)) return;
  for (const field of ['runtimeServiceAccount', 'auditServiceAccount']) {
    requirePattern(value[field], SERVICE_ACCOUNT, `iam.${field}`, errors);
    if (
      typeof value[field] === 'string' &&
      !value[field].endsWith(`@${project?.projectId}.iam.gserviceaccount.com`)
    )
      errors.push(`iam.${field} must belong to the exact production project`);
  }
  if (value.runtimeServiceAccount !== functionsValue?.runtimeServiceAccount)
    errors.push(
      'IAM runtime service account must equal Functions runtime identity',
    );
  if (value.runtimeServiceAccount === value.auditServiceAccount)
    errors.push(
      'read-only audit and runtime service accounts must be distinct',
    );
  requireTrue(
    value.workloadIdentityFederation,
    'iam.workloadIdentityFederation',
    errors,
  );
  requireFalse(
    value.serviceAccountKeyFilesUsed,
    'iam.serviceAccountKeyFilesUsed',
    errors,
  );
  if (
    value.runtimeUserManagedKeyCount !== 0 ||
    value.auditUserManagedKeyCount !== 0
  )
    errors.push(
      'runtime and audit service accounts must have zero user-managed keys',
    );
  requireTrue(
    value.broadPrimitiveRolesAbsent,
    'iam.broadPrimitiveRolesAbsent',
    errors,
  );
  requireTrue(
    value.wildcardPermissionsAbsent,
    'iam.wildcardPermissionsAbsent',
    errors,
  );
  if (
    exactKeys(
      value.capabilities,
      IAM_CAPABILITY_KEYS,
      'iam.capabilities',
      errors,
    )
  ) {
    for (const field of IAM_CAPABILITY_KEYS)
      requireTrue(
        value.capabilities[field],
        `iam.capabilities.${field}`,
        errors,
      );
  }
  parseInstant(value.reviewedAt, 'iam.reviewedAt', errors);
  if (
    value.policyEvidenceId !== 'iam-policy' ||
    value.effectivePermissionsEvidenceId !== 'iam-effective-permissions'
  )
    errors.push('IAM evidence IDs are invalid');
};

const validateObservability = (value, errors) => {
  if (!exactKeys(value, OBSERVABILITY_KEYS, 'observability', errors)) return;
  for (const field of [
    'requestBodiesExcluded',
    'rawExceptionsExcluded',
    'contactMessagePromptContentExcluded',
    'aiPromptResponseExcluded',
    'deletionCorrelationAccessRestricted',
    'logSinksReviewed',
    'unapprovedCrashAndAnalyticsAbsent',
  ])
    requireTrue(value[field], `observability.${field}`, errors);
  if (value.aiMonitoring !== 'off')
    errors.push('observability.aiMonitoring must be off');
  requireText(
    value.defaultLoggingExclusionName,
    'observability.defaultLoggingExclusionName',
    errors,
  );
  if (
    !Number.isSafeInteger(value.applicationLogRetentionDays) ||
    value.applicationLogRetentionDays < 1 ||
    value.applicationLogRetentionDays > 30
  )
    errors.push('application log retention must be between 1 and 30 days');
  if (
    !Number.isSafeInteger(value.dataAccessAuditRetentionDays) ||
    value.dataAccessAuditRetentionDays < 1 ||
    value.dataAccessAuditRetentionDays > 400
  )
    errors.push(
      'Data Access audit retention must be explicitly bounded at 1–400 days',
    );
  if (value.evidenceId !== 'logging-privacy')
    errors.push('observability.evidenceId is invalid');
};

const validateCostControls = (value, errors) => {
  if (!exactKeys(value, COST_KEYS, 'costControls', errors)) return;
  requirePattern(
    value.billingAccountId,
    BILLING_ACCOUNT,
    'costControls.billingAccountId',
    errors,
  );
  requireTrue(value.billingEnabled, 'costControls.billingEnabled', errors);
  if (
    exactKeys(
      value.monthlyBudget,
      MONEY_KEYS,
      'costControls.monthlyBudget',
      errors,
    )
  ) {
    if (!/^[A-Z]{3}$/u.test(value.monthlyBudget.currencyCode ?? ''))
      errors.push(
        'costControls.monthlyBudget.currencyCode must be ISO-4217-like uppercase',
      );
    if (
      !Number.isSafeInteger(value.monthlyBudget.units) ||
      value.monthlyBudget.units <= 0
    )
      errors.push(
        'costControls.monthlyBudget.units must be a positive integer',
      );
  }
  requireTrue(
    value.budgetAlertsNotHardCap,
    'costControls.budgetAlertsNotHardCap',
    errors,
  );
  requireTrue(
    value.providerHardQuotasConfigured,
    'costControls.providerHardQuotasConfigured',
    errors,
  );
  if (!Array.isArray(value.quotas)) {
    errors.push('costControls.quotas must be an array');
  } else {
    const byName = new Map();
    for (const [index, quota] of value.quotas.entries()) {
      const label = `costControls.quotas[${index}]`;
      if (!exactKeys(quota, QUOTA_KEYS, label, errors)) continue;
      if (
        ![
          'firebase-ai-user-requests',
          'firestore-writes',
          'functions-max-instances',
        ].includes(quota.name) ||
        byName.has(quota.name)
      )
        errors.push(`${label}.name is invalid or duplicated`);
      if (!Number.isSafeInteger(quota.limit) || quota.limit <= 0)
        errors.push(`${label}.limit must be a positive integer`);
      requireText(quota.unit, `${label}.unit`, errors);
      byName.set(quota.name, quota.limit);
    }
    if (byName.size !== 3)
      errors.push(
        'costControls.quotas must bind AI, Firestore, and Functions limits',
      );
    if (byName.get('functions-max-instances') !== 20)
      errors.push('Functions quota must match maxInstances 20');
  }
  for (const field of ['quotaOwnerEmails', 'incidentContactEmails']) {
    if (
      !Array.isArray(value[field]) ||
      value[field].length < 1 ||
      new Set(value[field]).size !== value[field].length
    ) {
      errors.push(
        `costControls.${field} must contain unique accountable owners`,
      );
    } else {
      for (const email of value[field])
        requirePattern(
          email,
          /^[^\s@]+@[^\s@]+\.[^\s@]+$/u,
          `costControls.${field}`,
          errors,
        );
    }
  }
  if (value.evidenceId !== 'quota-budget')
    errors.push('costControls.evidenceId is invalid');
};

const validateHosting = (value, source, errors) => {
  if (!exactKeys(value, HOSTING_KEYS, 'hosting', errors)) return;
  requirePattern(
    value.siteId,
    /^[a-z0-9][a-z0-9-]{4,62}$/u,
    'hosting.siteId',
    errors,
  );
  requirePattern(
    value.deployedVersionId,
    SAFE_ID,
    'hosting.deployedVersionId',
    errors,
  );
  const base = requireHttpsUrl(
    value.publicBaseUrl,
    'hosting.publicBaseUrl',
    errors,
    true,
  );
  const sameOriginFields = ['privacyUrl', 'termsUrl', 'deletionUrl'];
  for (const field of sameOriginFields) {
    const parsed = requireHttpsUrl(value[field], `hosting.${field}`, errors);
    if (base !== null && parsed !== null && parsed.origin !== base.origin)
      errors.push(`hosting.${field} must use the approved Hosting origin`);
  }
  requireHttpsUrl(value.supportUrl, 'hosting.supportUrl', errors);
  const verifiedSupport = requireHttpsUrl(
    value.identityVerifiedSupportUrl,
    'hosting.identityVerifiedSupportUrl',
    errors,
  );
  if (
    base !== null &&
    verifiedSupport !== null &&
    verifiedSupport.origin === base.origin
  )
    errors.push(
      'identity-verified support must be separately provisioned from public Hosting',
    );
  for (const field of [
    'firebaseConfigSha256',
    'releaseConfigSha256',
    'deployedArtifactSha256',
  ])
    requireDigest(value[field], `hosting.${field}`, errors);
  if (value.firebaseConfigSha256 !== source?.firebaseJsonSha256)
    errors.push(
      'hosting.firebaseConfigSha256 must match checked-out firebase.json',
    );
  for (const field of [
    'recaptchaEnterpriseAppCheckRegistered',
    'securityHeadersVerified',
    'legalCopyApproved',
    'hindiCopyApproved',
    'deletionSagaTested',
  ])
    requireTrue(value[field], `hosting.${field}`, errors);
  if (value.evidenceId !== 'hosting-release')
    errors.push('hosting.evidenceId is invalid');
};

const validateProhibitedServices = (value, errors) => {
  if (!exactKeys(value, PROHIBITED_KEYS, 'prohibitedServices', errors)) return;
  for (const field of [...PROHIBITED_KEYS].filter(
    candidate => candidate !== 'evidenceId',
  ))
    requireFalse(value[field], `prohibitedServices.${field}`, errors);
  if (value.evidenceId !== 'prohibited-services')
    errors.push('prohibitedServices.evidenceId is invalid');
};

const validateVerification = (value, errors) => {
  if (!exactKeys(value, VERIFICATION_KEYS, 'verification', errors)) return;
  for (const field of [
    'firebaseAuthLifecyclePassed',
    'peopleConsentAndRevocationPassed',
    'androidSignedAttestationPassed',
    'iosSignedAttestationPassed',
    'androidAiLogicCallPassed',
    'iosAiLogicCallPassed',
    'stateChangingCallableProbePassed',
    'companionStatusProbePassed',
    'replayedLimitedUseTokenRejected',
    'directFirestoreAnonymousDenied',
    'directFirestoreAuthenticatedDenied',
    'ttlPoliciesObserved',
    'recursiveDeletionAbsencePassed',
    'signedOutReceiptPassed',
  ])
    requireTrue(value[field], `verification.${field}`, errors);
  if (
    value.contentLeakScanFindingCount !== 0 ||
    value.debugProviderFindingCount !== 0
  )
    errors.push(
      'verification privacy/debug-provider scans must have zero findings',
    );
  if (
    value.liveAuditEvidenceId !== 'live-readonly-audit' ||
    value.signedChannelEvidenceId !== 'signed-channel-smoke' ||
    value.deletionEvidenceId !== 'deletion-live-smoke'
  )
    errors.push('verification evidence IDs are invalid');
};

const validateOperations = (value, firestore, errors) => {
  if (!exactKeys(value, OPERATIONS_KEYS, 'operations', errors)) return;
  if (value.globalControlState !== 'HEALTHY')
    errors.push('operations.globalControlState must be HEALTHY');
  if (value.ledgerGeneration !== firestore?.ledgerGeneration)
    errors.push(
      'operations.ledgerGeneration must match Firestore continuity evidence',
    );
  for (const field of [
    'emptyLedgerNeverUsedAsNoSendProof',
    'disasterCreatesReviewedNewGeneration',
    'reRegistrationAndReapprovalRequiredAfterDisaster',
    'sameDateAutomationBlockedAfterDisaster',
    'userLedgerBackupsNeverRestored',
    'loadContentionTestPassed',
    'regionalFailureTestPassed',
    'incidentRollbackReady',
  ])
    requireTrue(value[field], `operations.${field}`, errors);
  for (const field of [
    'rtoMinutes',
    'p95CallableLatencyMs',
    'errorBudgetWindowDays',
    'deletionSlaHours',
  ]) {
    if (!Number.isSafeInteger(value[field]) || value[field] <= 0)
      errors.push(`operations.${field} must be a positive approved integer`);
  }
  if (
    typeof value.availabilityTargetPercent !== 'number' ||
    value.availabilityTargetPercent < 99 ||
    value.availabilityTargetPercent >= 100
  )
    errors.push(
      'operations.availabilityTargetPercent must be an approved target from 99 to less than 100',
    );
  if (
    value.continuityEvidenceId !== 'continuity-dr' ||
    value.sloEvidenceId !== 'slo-load-cost' ||
    value.incidentEvidenceId !== 'incident-rollback'
  )
    errors.push('operations evidence IDs are invalid');
};

const validateReferences = (references, validity, context, errors) => {
  if (!Array.isArray(references)) {
    errors.push('evidenceReferences must be an array');
    return new Set();
  }
  const ids = new Set();
  const paths = new Set();
  for (const [index, reference] of references.entries()) {
    const label = `evidenceReferences[${index}]`;
    if (!exactKeys(reference, REFERENCE_KEYS, label, errors)) continue;
    if (!SAFE_ID.test(reference.id ?? '') || ids.has(reference.id))
      errors.push(`${label}.id is invalid or duplicated`);
    ids.add(reference.id);
    if (
      typeof reference.path !== 'string' ||
      !SAFE_RELATIVE.test(reference.path) ||
      path.isAbsolute(reference.path) ||
      reference.path.split(/[\\/]/u).some(part => part === '..' || part === '')
    )
      errors.push(`${label}.path must be a safe normalized relative path`);
    if (paths.has(reference.path))
      errors.push(`${label}.path is duplicated across evidence references`);
    paths.add(reference.path);
    requireDigest(reference.sha256, `${label}.sha256`, errors);
    const capturedAt = parseInstant(
      reference.capturedAt,
      `${label}.capturedAt`,
      errors,
    );
    const validUntil = parseInstant(
      reference.validUntil,
      `${label}.validUntil`,
      errors,
    );
    if (
      capturedAt !== null &&
      validity?.generatedAt !== null &&
      capturedAt > validity.generatedAt + MAX_CLOCK_SKEW_MS
    )
      errors.push(
        `${label} was captured after the evidence package was generated`,
      );
    if (
      capturedAt !== null &&
      validity?.generatedAt !== null &&
      validity.generatedAt - capturedAt > MAX_VALIDITY_MS
    )
      errors.push(`${label} is older than the 30-day evidence window`);
    if (
      reference.id === 'live-readonly-audit' &&
      capturedAt !== null &&
      validity?.generatedAt !== null &&
      validity.generatedAt - capturedAt > 24 * 60 * 60 * 1_000
    )
      errors.push('live-readonly-audit must be no older than 24 hours');
    if (
      validUntil !== null &&
      validity?.validUntil !== null &&
      validUntil < validity.validUntil
    )
      errors.push(`${label} expires before the release evidence`);
    requireText(reference.issuer, `${label}.issuer`, errors);
    if (
      ![
        'approval',
        'attestation',
        'cli-export',
        'console-export',
        'provenance',
        'review',
        'runbook',
        'test-report',
      ].includes(reference.kind)
    )
      errors.push(`${label}.kind is invalid`);
    const observed = context.evidenceFiles?.get(reference.path);
    if (observed === undefined) {
      errors.push(`${label}.path was not collected from the evidence root`);
    } else if (observed.sha256 !== reference.sha256) {
      errors.push(`${label}.sha256 does not match the referenced file`);
    }
  }
  for (const id of REQUIRED_EVIDENCE_IDS) {
    if (!ids.has(id))
      errors.push(`required evidence reference is missing: ${id}`);
  }
  if (
    references.length !== REQUIRED_EVIDENCE_IDS.length ||
    [...ids].some(id => !REQUIRED_EVIDENCE_IDS.includes(id))
  ) {
    errors.push(
      'evidenceReferences must contain only the exact required evidence set',
    );
  }
  for (const file of context.evidenceFiles?.keys() ?? []) {
    if (!paths.has(file))
      errors.push(`evidence root contains an unreferenced file: ${file}`);
  }
  return ids;
};

const validateApprovals = (approvals, validity, referenceIds, errors) => {
  if (
    !Array.isArray(approvals) ||
    approvals.length !== REQUIRED_APPROVAL_ROLES.length
  ) {
    errors.push(
      'approvals must contain every required independent role exactly once',
    );
    return;
  }
  const roles = new Set();
  const approvers = new Set();
  for (const [index, approval] of approvals.entries()) {
    const label = `approvals[${index}]`;
    if (!exactKeys(approval, APPROVAL_KEYS, label, errors)) continue;
    if (
      !REQUIRED_APPROVAL_ROLES.includes(approval.role) ||
      roles.has(approval.role)
    )
      errors.push(`${label}.role is invalid or duplicated`);
    roles.add(approval.role);
    requireText(approval.approver, `${label}.approver`, errors);
    if (approvers.has(approval.approver))
      errors.push('approval roles must have distinct accountable approvers');
    approvers.add(approval.approver);
    if (approval.decision !== 'approved')
      errors.push(`${label}.decision must be approved`);
    const approvedAt = parseInstant(
      approval.approvedAt,
      `${label}.approvedAt`,
      errors,
    );
    const validUntil = parseInstant(
      approval.validUntil,
      `${label}.validUntil`,
      errors,
    );
    if (
      approvedAt !== null &&
      validity?.generatedAt !== null &&
      approvedAt > validity.generatedAt + MAX_CLOCK_SKEW_MS
    )
      errors.push(`${label} approval is later than package generation`);
    if (
      approvedAt !== null &&
      validity?.generatedAt !== null &&
      validity.generatedAt - approvedAt > MAX_VALIDITY_MS
    )
      errors.push(`${label} approval is older than the 30-day evidence window`);
    if (validUntil !== validity?.validUntil)
      errors.push(`${label}.validUntil must equal package validUntil`);
    if (
      approval.evidenceId !== `approval-${approval.role}` ||
      !referenceIds.has(approval.evidenceId)
    )
      errors.push(`${label}.evidenceId is invalid or missing`);
  }
  if (!equalStringSet([...roles], REQUIRED_APPROVAL_ROLES))
    errors.push('approvals do not cover the exact required roles');
};

export function validateCloudReleaseEvidence(document, context = {}) {
  const errors = [];
  const nowMs = context.nowMs ?? Date.now();
  if (!exactKeys(document, TOP_LEVEL_KEYS, 'document', errors)) return errors;
  if (document.$schema !== './cloud-release-evidence.schema.json')
    errors.push('$schema must reference the repository cloud schema');
  if (document.schemaVersion !== 1)
    errors.push('schemaVersion must be exactly 1');
  if (document.product !== 'birthday-autopilot')
    errors.push('product must be birthday-autopilot');
  if (document.status !== 'approved') errors.push('status must be approved');

  validateSource(document.source, context, errors);
  const validity = validateValidity(document.validity, nowMs, errors);
  validateProject(document.project, errors);
  validateIdentity(document.identityAndApis, document.project, errors);
  validateAiLogic(document.aiLogic, nowMs, errors);
  validateAppCheck(
    document.appCheck,
    document.project,
    document.identityAndApis,
    errors,
  );
  validateFunctions(document.functions, document.source, errors);
  validateFirestore(
    document.firestore,
    document.source,
    document.project,
    errors,
  );
  validateSecrets(document.secrets, validity, errors);
  validateIam(document.iam, document.project, document.functions, errors);
  validateObservability(document.observability, errors);
  validateCostControls(document.costControls, errors);
  validateHosting(document.hosting, document.source, errors);
  validateProhibitedServices(document.prohibitedServices, errors);
  validateVerification(document.verification, errors);
  validateOperations(document.operations, document.firestore, errors);
  const referenceIds = validateReferences(
    document.evidenceReferences,
    validity,
    context,
    errors,
  );
  validateApprovals(document.approvals, validity, referenceIds, errors);

  return errors;
}

const stableFileBytes = (filePath, maximumBytes = MAX_REFERENCE_BYTES) => {
  const before = lstatSync(filePath, { bigint: true });
  if (
    !before.isFile() ||
    before.size <= 0n ||
    before.size > BigInt(maximumBytes)
  )
    throw new Error(`unsafe or oversized regular file: ${filePath}`);
  let descriptor;
  try {
    descriptor = openSync(
      filePath,
      // File-descriptor flags form a bit mask.
      // eslint-disable-next-line no-bitwise
      constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
    );
    const opened = fstatSync(descriptor, { bigint: true });
    if (
      !opened.isFile() ||
      opened.dev !== before.dev ||
      opened.ino !== before.ino ||
      opened.size !== before.size
    )
      throw new Error(`file changed before read: ${filePath}`);
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    const pathAfter = lstatSync(filePath, { bigint: true });
    if (
      after.dev !== opened.dev ||
      after.ino !== opened.ino ||
      after.size !== opened.size ||
      after.mtimeNs !== opened.mtimeNs ||
      pathAfter.dev !== opened.dev ||
      pathAfter.ino !== opened.ino ||
      BigInt(bytes.byteLength) !== opened.size
    )
      throw new Error(`file changed while read: ${filePath}`);
    return bytes;
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
};

const digest = bytes => createHash('sha256').update(bytes).digest('hex');

const portablePath = value => value.split(path.sep).join('/');

const collectTreeRecords = (base, selectedPath, state) => {
  const metadata = lstatSync(selectedPath);
  const relative = portablePath(path.relative(base, selectedPath));
  if (metadata.isSymbolicLink())
    throw new Error(`source tree contains a symbolic link: ${relative}`);
  if (metadata.isDirectory()) {
    for (const name of readdirSync(selectedPath).sort())
      collectTreeRecords(base, path.join(selectedPath, name), state);
    return;
  }
  if (!metadata.isFile())
    throw new Error(`source tree contains an unsupported entry: ${relative}`);
  const bytes = stableFileBytes(selectedPath, MAX_TREE_BYTES);
  state.bytes += bytes.byteLength;
  if (state.bytes > MAX_TREE_BYTES)
    throw new Error('selected source tree exceeds the safety bound');
  state.records.push(
    `file\0${relative}\0${bytes.byteLength}\0${digest(bytes)}`,
  );
};

export function digestSelectedSourcePaths(root, relativePaths) {
  const resolvedRoot = realpathSync(root);
  const state = { bytes: 0, records: [] };
  for (const relativePath of [...relativePaths].sort()) {
    if (
      path.isAbsolute(relativePath) ||
      relativePath.split(/[\\/]/u).some(part => part === '..' || part === '')
    )
      throw new Error(`unsafe source path: ${relativePath}`);
    collectTreeRecords(
      resolvedRoot,
      path.join(resolvedRoot, relativePath),
      state,
    );
  }
  return digest(Buffer.from(state.records.sort().join('\n'), 'utf8'));
}

const command = (binary, args, cwd) =>
  execFileSync(binary, args, {
    cwd,
    encoding: 'utf8',
    maxBuffer: 4 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();

export function collectExpectedCloudSource(sourceRoot = PROJECT_ROOT) {
  const root = realpathSync(sourceRoot);
  const repositoryRoot = realpathSync(
    command('git', ['rev-parse', '--show-toplevel'], root),
  );
  if (repositoryRoot !== root)
    throw new Error('source root must be the Git repository root');
  const revision = command('git', ['rev-parse', '--verify', 'HEAD'], root);
  if (!REVISION.test(revision))
    throw new Error('checked-out source revision is invalid');
  const status = command(
    'git',
    ['status', '--porcelain=v1', '--untracked-files=all'],
    root,
  );
  if (status !== '')
    throw new Error('cloud release evidence requires a clean source checkout');
  const remote = command('git', ['remote', 'get-url', 'origin'], root);
  if (
    remote !== 'https://github.com/yhsomani/AI-Birthday.git' &&
    remote !== 'git@github.com:yhsomani/AI-Birthday.git'
  )
    throw new Error('source checkout does not use the authoritative origin');
  const hashFile = relative =>
    digest(stableFileBytes(path.join(root, relative), MAX_TREE_BYTES));
  return Object.freeze({
    revision,
    projectAboutSha256: hashFile('PROJECT_ABOUT.md'),
    firebaseJsonSha256: hashFile('backend/firebase.json'),
    functionsSourceTreeSha256: digestSelectedSourcePaths(
      root,
      FUNCTIONS_DEPLOYMENT_SOURCE_PATHS,
    ),
    functionsLockSha256: hashFile('backend/functions/package-lock.json'),
    firestoreRulesSha256: hashFile('backend/firestore.rules'),
    firestoreIndexesSha256: hashFile('backend/firestore.indexes.json'),
    ttlPoliciesSha256: hashFile('backend/ttl-policies.json'),
    hostingSourceTreeSha256: digestSelectedSourcePaths(
      root,
      HOSTING_DEPLOYMENT_SOURCE_PATHS,
    ),
  });
}

export function collectCloudEvidenceFiles(evidenceRoot) {
  const root = realpathSync(evidenceRoot);
  const files = new Map();
  const walk = directory => {
    for (const name of readdirSync(directory).sort()) {
      const absolute = path.join(directory, name);
      const metadata = lstatSync(absolute);
      const relative = portablePath(path.relative(root, absolute));
      if (metadata.isSymbolicLink())
        throw new Error(`evidence contains a symbolic link: ${relative}`);
      if (metadata.isDirectory()) {
        walk(absolute);
      } else if (metadata.isFile()) {
        const bytes = stableFileBytes(absolute);
        files.set(
          relative,
          Object.freeze({ sha256: digest(bytes), bytes: bytes.byteLength }),
        );
      } else {
        throw new Error(`evidence contains an unsupported entry: ${relative}`);
      }
    }
  };
  walk(root);
  return files;
}

export function verifyCloudReleaseAuthority({
  evidenceBytes,
  signatureBytes,
  publicKeyBytes,
  pin,
}) {
  if (
    !isObject(pin) ||
    pin.schemaVersion !== 1 ||
    pin.algorithm !== 'Ed25519' ||
    typeof pin.publicKeySpkiSha256 !== 'string'
  )
    throw new Error('release authority pin is malformed');
  if (pin.publicKeySpkiSha256 === 'UNPROVISIONED')
    throw new Error('release authority is not provisioned');
  if (!SHA256.test(pin.publicKeySpkiSha256))
    throw new Error('release authority pin is invalid');
  let containsPrivateKey = false;
  try {
    createPrivateKey(publicKeyBytes);
    containsPrivateKey = true;
  } catch {
    // A public-only PEM/DER input is expected not to parse as a private key.
  }
  if (containsPrivateKey)
    throw new Error(
      'cloud release authority input contains private-key material',
    );
  const key = createPublicKey(publicKeyBytes);
  if (key.asymmetricKeyType !== 'ed25519')
    throw new Error('cloud release authority must be Ed25519');
  const spki = key.export({ type: 'spki', format: 'der' });
  if (digest(spki) !== pin.publicKeySpkiSha256)
    throw new Error(
      'release authority public key does not match the repository pin',
    );
  if (
    signatureBytes.length !== 64 ||
    !verifySignature(null, evidenceBytes, key, signatureBytes)
  )
    throw new Error('release authority signature is invalid');
  return true;
}

const parseArgs = argv => {
  const allowed = new Set(['file', 'evidence-root', 'signature', 'public-key']);
  const result = {};
  for (let index = 0; index < argv.length; index += 2) {
    const token = argv[index];
    if (!token?.startsWith('--') || index + 1 >= argv.length)
      throw new Error(
        'cloud evidence CLI arguments must be --name value pairs',
      );
    const key = token.slice(2);
    if (!allowed.has(key) || result[key] !== undefined)
      throw new Error(`unknown or duplicated argument: --${key}`);
    result[key] = argv[index + 1];
  }
  for (const key of ['file', 'evidence-root', 'signature', 'public-key']) {
    if (result[key] === undefined)
      throw new Error(`missing required argument: --${key}`);
  }
  return result;
};

const main = () => {
  const args = parseArgs(process.argv.slice(2));
  const evidenceBytes = stableFileBytes(path.resolve(args.file));
  const signatureBytes = stableFileBytes(
    path.resolve(args.signature),
    4 * 1024,
  );
  const publicKeyBytes = stableFileBytes(
    path.resolve(args['public-key']),
    64 * 1024,
  );
  const pinBytes = stableFileBytes(DEFAULT_AUTHORITY_PIN, 64 * 1024);
  const pin = JSON.parse(pinBytes.toString('utf8'));
  verifyCloudReleaseAuthority({
    evidenceBytes,
    signatureBytes,
    publicKeyBytes,
    pin,
  });
  const document = JSON.parse(evidenceBytes.toString('utf8'));
  // The validator, tracked authority pin, and release source must come from
  // the same clean checkout. Allowing a second source root would let a caller
  // hide a modified trust pin behind an unrelated clean clone.
  const expectedSource = collectExpectedCloudSource(PROJECT_ROOT);
  const evidenceFiles = collectCloudEvidenceFiles(
    path.resolve(args['evidence-root']),
  );
  const errors = validateCloudReleaseEvidence(document, {
    nowMs: Date.now(),
    expectedSource,
    evidenceFiles,
  });
  if (errors.length > 0)
    throw new Error(
      `cloud release evidence rejected:\n- ${errors.join('\n- ')}`,
    );
  process.stdout.write(
    'Cloud release evidence is valid and authority-signed.\n',
  );
};

if (
  process.argv[1] !== undefined &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  try {
    main();
  } catch (error) {
    process.stderr.write(
      `${error instanceof Error ? error.message : String(error)}\n`,
    );
    process.exitCode = 1;
  }
}
