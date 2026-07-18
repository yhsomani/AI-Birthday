#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  closeSync,
  constants,
  fstatSync,
  lstatSync,
  openSync,
  readFileSync,
  realpathSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  collectDistributionEvidenceFiles,
  inspectCleanGitSource,
  verifyDistributionEvidenceAuthority,
} from './validate-distribution-evidence.mjs';
import { stableJson } from './hosting-deployment-artifact.mjs';

const PROJECT_ROOT = fileURLToPath(new URL('../', import.meta.url));
const AUTHORITY_PIN_PATH = path.join(
  PROJECT_ROOT,
  'tools/distribution-authority-pin.json',
);
const AUTHORITATIVE_REPOSITORIES = new Set([
  'https://github.com/yhsomani/AI-Birthday.git',
  'git@github.com:yhsomani/AI-Birthday.git',
]);
const PRODUCT = 'birthday-autopilot-production-release-closure';
const ANDROID_PACKAGE = 'com.yashsomani.birthdayautopilot';
const IOS_BUNDLE = 'com.yashsomani.birthdayautopilot';
const SHA256 = /^[0-9a-f]{64}$/u;
const SHA1 = /^[0-9a-f]{40}$/u;
const REVISION = /^[0-9a-f]{40}$/u;
const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/u;
const PROJECT_NUMBER = /^[1-9][0-9]{5,19}$/u;
const SERVICE_ACCOUNT =
  /^[a-z][a-z0-9-]{2,62}@[a-z][a-z0-9-]{4,28}[a-z0-9]\.iam\.gserviceaccount\.com$/u;
const WIF_PROVIDER =
  /^projects\/[1-9][0-9]{5,19}\/locations\/global\/workloadIdentityPools\/[a-z0-9-]{4,32}\/providers\/[a-z0-9-]{4,32}$/u;
const FIREBASE_APP_ID =
  /^1:[1-9][0-9]{5,19}:(?:android|ios|web):[0-9a-f]{8,64}$/u;
const OAUTH_CLIENT = /^[0-9]+-[a-z0-9-]+\.apps\.googleusercontent\.com$/u;
const REVERSED_CLIENT = /^com\.googleusercontent\.apps\.[A-Za-z0-9-]+$/u;
const TEAM_IDENTIFIER = /^[A-Z0-9]{10}$/u;
const VERSION = /^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$/u;
const BUILD_NUMBER = /^[1-9][0-9]{0,17}$/u;
const SAFE_FILE_NAME = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,255}$/u;
const SAFE_RELATIVE = /^[A-Za-z0-9][A-Za-z0-9._/@()+ -]{0,511}$/u;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const MAXIMUM_VALIDITY_MS = 30 * 24 * 60 * 60 * 1_000;
const MAXIMUM_PLAY_PROOF_MS = 24 * 60 * 60 * 1_000;
const MAXIMUM_CLOCK_SKEW_MS = 5 * 60 * 1_000;
const MAXIMUM_MANIFEST_BYTES = 2 * 1024 * 1024;
const MAXIMUM_REPORT_BYTES = 4 * 1024 * 1024;
const MAXIMUM_ANDROID_BYTES = 1024 * 1024 * 1024;
const MAXIMUM_IOS_BYTES = 1024 * 1024 * 1024;
const MAXIMUM_HOSTING_BYTES = 512 * 1024 * 1024;
const COMPONENT_NAMES = Object.freeze([
  'androidDistribution',
  'androidPlayDelivery',
  'iosRelease',
  'cloud',
  'store',
  'hosting',
]);
const TOP_LEVEL_KEYS = [
  '$schema',
  'schemaVersion',
  'product',
  'status',
  'source',
  'validity',
  'release',
  'components',
  'finalApproval',
];
const CLI_KEYS = new Set([
  'mode',
  'file',
  'signature',
  'public-key',
  'evidence-root',
  'android-artifact',
  'android-delivered-base',
  'android-installed-apk-root',
  'ios-artifact',
  'hosting-artifact',
]);
const NULL_DEVICE = process.platform === 'win32' ? 'NUL' : '/dev/null';
const SANITIZED_GIT_ENVIRONMENT = {
  PATH: process.env.PATH,
  HOME: process.env.HOME,
  GIT_CONFIG_COUNT: '0',
  GIT_CONFIG_GLOBAL: NULL_DEVICE,
  GIT_CONFIG_NOSYSTEM: '1',
  GIT_LITERAL_PATHSPECS: '1',
  GIT_OPTIONAL_LOCKS: '0',
};

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const exactKeys = (value, expected, label, errors) => {
  if (!isObject(value)) {
    errors.push(`${label} must be an object`);
    return false;
  }
  const keys = Object.keys(value);
  if (
    keys.length !== expected.length ||
    keys.some(key => !expected.includes(key))
  ) {
    errors.push(`${label} fields do not match the exact contract`);
    return false;
  }
  return true;
};

const parseInstant = (value, label, errors) => {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  const date = new Date(parsed);
  if (
    date.getUTCFullYear() !== Number(value.slice(0, 4)) ||
    date.getUTCMonth() + 1 !== Number(value.slice(5, 7)) ||
    date.getUTCDate() !== Number(value.slice(8, 10)) ||
    date.getUTCHours() !== Number(value.slice(11, 13)) ||
    date.getUTCMinutes() !== Number(value.slice(14, 16)) ||
    date.getUTCSeconds() !== Number(value.slice(17, 19))
  ) {
    errors.push(`${label} must be a real RFC 3339 UTC instant`);
    return null;
  }
  return parsed;
};

const requireDigest = (value, label, errors) => {
  if (
    typeof value !== 'string' ||
    !SHA256.test(value) ||
    /^0{64}$/u.test(value)
  ) {
    errors.push(`${label} must be a non-placeholder lowercase SHA-256 digest`);
  }
};

const requirePattern = (value, pattern, label, errors) => {
  if (typeof value !== 'string' || !pattern.test(value)) {
    errors.push(`${label} is invalid`);
  }
};

const safeHttpsOrigin = value => {
  try {
    const url = new URL(value);
    return (
      url.protocol === 'https:' &&
      url.username === '' &&
      url.password === '' &&
      url.pathname === '/' &&
      url.search === '' &&
      url.hash === '' &&
      !['localhost', '127.0.0.1'].includes(url.hostname) &&
      !['example.com', 'example.org', 'example.net'].some(
        domain =>
          url.hostname === domain || url.hostname.endsWith(`.${domain}`),
      ) &&
      !url.hostname.endsWith('.example') &&
      !url.hostname.endsWith('.invalid') &&
      !url.hostname.endsWith('.test')
    );
  } catch {
    return false;
  }
};

const safeRelativePath = value =>
  typeof value === 'string' &&
  value === value.trim() &&
  SAFE_RELATIVE.test(value) &&
  !path.isAbsolute(value) &&
  !value.includes('\\') &&
  value.split('/').every(part => part !== '' && part !== '.' && part !== '..');

const validateReference = (reference, name, errors) => {
  if (
    !exactKeys(
      reference,
      ['path', 'sha256', 'bytes'],
      `components.${name}`,
      errors,
    )
  ) {
    return;
  }
  if (!safeRelativePath(reference.path)) {
    errors.push(`components.${name}.path is unsafe`);
  }
  requireDigest(reference.sha256, `components.${name}.sha256`, errors);
  if (!Number.isSafeInteger(reference.bytes) || reference.bytes <= 0) {
    errors.push(`components.${name}.bytes must be a positive safe integer`);
  }
};

const componentValidUntil = (
  report,
  label,
  nowMs,
  manifestValidUntil,
  errors,
) => {
  const value = parseInstant(report?.validUntil, `${label}.validUntil`, errors);
  if (value !== null && value <= nowMs) {
    errors.push(`${label} approval/report is expired`);
  }
  if (
    value !== null &&
    manifestValidUntil !== null &&
    value < manifestValidUntil
  ) {
    errors.push(`${label} expires before the final release closure`);
  }
};

const validateReportEnvelope = (
  report,
  expectedKeys,
  product,
  label,
  sourceRevision,
  authorityDigest,
  authorityField,
  errors,
) => {
  if (!exactKeys(report, expectedKeys, label, errors)) return false;
  if (
    report.schemaVersion !== 1 ||
    report.product !== product ||
    report.status !== 'verified'
  ) {
    errors.push(`${label} identity/status is invalid`);
  }
  if (report.sourceRevision !== sourceRevision) {
    errors.push(`${label} source revision does not match the closure`);
  }
  if (report[authorityField] !== authorityDigest) {
    errors.push(`${label} authority does not match the closure`);
  }
  return true;
};

export function validateProductionReleaseClosure(document, context = {}) {
  const errors = [];
  const nowMs = context.nowMs ?? Date.now();
  if (!exactKeys(document, TOP_LEVEL_KEYS, 'manifest', errors)) return errors;
  if (
    document.$schema !== './production-release-closure.schema.json' ||
    document.schemaVersion !== 1 ||
    document.product !== PRODUCT ||
    document.status !== 'approved'
  ) {
    errors.push(
      'manifest identity/status is not an approved production closure',
    );
  }

  let sourceRevision = null;
  let authorityDigest = null;
  if (
    exactKeys(
      document.source,
      ['revision', 'repository', 'clean', 'authorityPublicKeySpkiSha256'],
      'source',
      errors,
    )
  ) {
    requirePattern(
      document.source.revision,
      REVISION,
      'source.revision',
      errors,
    );
    sourceRevision = document.source.revision;
    if (!AUTHORITATIVE_REPOSITORIES.has(document.source.repository)) {
      errors.push('source.repository is not the authoritative repository');
    }
    if (document.source.clean !== true)
      errors.push('source.clean must be true');
    requireDigest(
      document.source.authorityPublicKeySpkiSha256,
      'source.authorityPublicKeySpkiSha256',
      errors,
    );
    authorityDigest = document.source.authorityPublicKeySpkiSha256;
    if (
      context.sourceRevision !== undefined &&
      context.sourceRevision !== sourceRevision
    ) {
      errors.push('manifest source revision does not match clean Git HEAD');
    }
    if (context.sourceClean === false)
      errors.push('release source is not clean');
    if (
      context.authorityPublicKeySpkiSha256 !== undefined &&
      context.authorityPublicKeySpkiSha256 !== authorityDigest
    ) {
      errors.push(
        'manifest authority does not match the verified signature key',
      );
    }
  }

  let manifestValidUntil = null;
  if (
    exactKeys(
      document.validity,
      ['generatedAt', 'validUntil'],
      'validity',
      errors,
    )
  ) {
    const generatedAt = parseInstant(
      document.validity.generatedAt,
      'validity.generatedAt',
      errors,
    );
    manifestValidUntil = parseInstant(
      document.validity.validUntil,
      'validity.validUntil',
      errors,
    );
    if (
      generatedAt !== null &&
      manifestValidUntil !== null &&
      !(
        generatedAt <= nowMs &&
        manifestValidUntil > nowMs &&
        manifestValidUntil - generatedAt <= MAXIMUM_VALIDITY_MS
      )
    ) {
      errors.push('closure must be current and valid for no more than 30 days');
    }
  }

  let release = null;
  if (
    exactKeys(
      document.release,
      ['environment', 'firebase', 'android', 'ios', 'hosting'],
      'release',
      errors,
    )
  ) {
    release = document.release;
    if (release.environment !== 'production') {
      errors.push('release.environment must be production');
    }
    if (
      exactKeys(
        release.firebase,
        ['projectId', 'projectNumber', 'androidAppId', 'iosAppId'],
        'release.firebase',
        errors,
      )
    ) {
      requirePattern(
        release.firebase.projectId,
        PROJECT_ID,
        'release.firebase.projectId',
        errors,
      );
      requirePattern(
        release.firebase.projectNumber,
        PROJECT_NUMBER,
        'release.firebase.projectNumber',
        errors,
      );
      requirePattern(
        release.firebase.androidAppId,
        FIREBASE_APP_ID,
        'release.firebase.androidAppId',
        errors,
      );
      requirePattern(
        release.firebase.iosAppId,
        FIREBASE_APP_ID,
        'release.firebase.iosAppId',
        errors,
      );
      if (!String(release.firebase.androidAppId).includes(':android:')) {
        errors.push('release.firebase.androidAppId must be an Android app ID');
      }
      if (!String(release.firebase.iosAppId).includes(':ios:')) {
        errors.push('release.firebase.iosAppId must be an iOS app ID');
      }
    }
    if (
      exactKeys(
        release.android,
        [
          'applicationId',
          'versionCode',
          'versionName',
          'artifactKind',
          'artifactFileName',
          'artifactSha256',
          'artifactSigningCertificateSha256',
          'installedSigningCertificateSha1',
          'installedSigningCertificateSha256',
          'deliveredBaseApkFileName',
          'deliveredBaseApkSha256',
        ],
        'release.android',
        errors,
      )
    ) {
      if (release.android.applicationId !== ANDROID_PACKAGE) {
        errors.push(
          'release.android.applicationId is not the production package',
        );
      }
      if (
        !Number.isSafeInteger(release.android.versionCode) ||
        release.android.versionCode < 1
      ) {
        errors.push(
          'release.android.versionCode must be a positive safe integer',
        );
      }
      requirePattern(
        release.android.versionName,
        VERSION,
        'release.android.versionName',
        errors,
      );
      if (release.android.artifactKind !== 'aab') {
        errors.push('final store closure requires the exact Google Play AAB');
      }
      requirePattern(
        release.android.artifactFileName,
        SAFE_FILE_NAME,
        'release.android.artifactFileName',
        errors,
      );
      if (!String(release.android.artifactFileName).endsWith('.aab')) {
        errors.push('release.android.artifactFileName must end in .aab');
      }
      requireDigest(
        release.android.artifactSha256,
        'release.android.artifactSha256',
        errors,
      );
      requireDigest(
        release.android.artifactSigningCertificateSha256,
        'release.android.artifactSigningCertificateSha256',
        errors,
      );
      requirePattern(
        release.android.installedSigningCertificateSha1,
        SHA1,
        'release.android.installedSigningCertificateSha1',
        errors,
      );
      requireDigest(
        release.android.installedSigningCertificateSha256,
        'release.android.installedSigningCertificateSha256',
        errors,
      );
      requirePattern(
        release.android.deliveredBaseApkFileName,
        SAFE_FILE_NAME,
        'release.android.deliveredBaseApkFileName',
        errors,
      );
      if (!String(release.android.deliveredBaseApkFileName).endsWith('.apk')) {
        errors.push(
          'release.android.deliveredBaseApkFileName must end in .apk',
        );
      }
      requireDigest(
        release.android.deliveredBaseApkSha256,
        'release.android.deliveredBaseApkSha256',
        errors,
      );
    }
    if (
      exactKeys(
        release.ios,
        [
          'bundleId',
          'shortVersion',
          'buildNumber',
          'artifactFileName',
          'artifactSha256',
          'distributionCertificateSha256',
        ],
        'release.ios',
        errors,
      )
    ) {
      if (release.ios.bundleId !== IOS_BUNDLE) {
        errors.push('release.ios.bundleId is not the production bundle');
      }
      requirePattern(
        release.ios.shortVersion,
        VERSION,
        'release.ios.shortVersion',
        errors,
      );
      requirePattern(
        release.ios.buildNumber,
        BUILD_NUMBER,
        'release.ios.buildNumber',
        errors,
      );
      requirePattern(
        release.ios.artifactFileName,
        SAFE_FILE_NAME,
        'release.ios.artifactFileName',
        errors,
      );
      if (!String(release.ios.artifactFileName).endsWith('.ipa')) {
        errors.push('release.ios.artifactFileName must end in .ipa');
      }
      requireDigest(
        release.ios.artifactSha256,
        'release.ios.artifactSha256',
        errors,
      );
      requireDigest(
        release.ios.distributionCertificateSha256,
        'release.ios.distributionCertificateSha256',
        errors,
      );
    }
    if (
      exactKeys(
        release.hosting,
        ['publicBaseUrl', 'releaseConfigSha256', 'deployedArtifactSha256'],
        'release.hosting',
        errors,
      )
    ) {
      if (!safeHttpsOrigin(release.hosting.publicBaseUrl)) {
        errors.push(
          'release.hosting.publicBaseUrl must be a provisioned HTTPS origin',
        );
      }
      requireDigest(
        release.hosting.releaseConfigSha256,
        'release.hosting.releaseConfigSha256',
        errors,
      );
      requireDigest(
        release.hosting.deployedArtifactSha256,
        'release.hosting.deployedArtifactSha256',
        errors,
      );
    }
  }

  if (exactKeys(document.components, COMPONENT_NAMES, 'components', errors)) {
    for (const name of COMPONENT_NAMES) {
      validateReference(document.components[name], name, errors);
      const loaded = context.reportFiles?.[name];
      if (loaded !== undefined) {
        if (loaded.sha256 !== document.components[name].sha256) {
          errors.push(
            `components.${name} bytes do not match its SHA-256 digest`,
          );
        }
        if (loaded.bytes !== document.components[name].bytes) {
          errors.push(`components.${name} byte count does not match`);
        }
      }
    }
  }

  if (
    exactKeys(
      document.finalApproval,
      ['decision', 'approvedAt', 'validUntil'],
      'finalApproval',
      errors,
    )
  ) {
    if (document.finalApproval.decision !== 'approved') {
      errors.push('finalApproval.decision must be approved');
    }
    const approvedAt = parseInstant(
      document.finalApproval.approvedAt,
      'finalApproval.approvedAt',
      errors,
    );
    const approvalValidUntil = parseInstant(
      document.finalApproval.validUntil,
      'finalApproval.validUntil',
      errors,
    );
    if (
      approvedAt !== null &&
      approvalValidUntil !== null &&
      !(approvedAt <= nowMs && approvalValidUntil > nowMs)
    ) {
      errors.push('final approval is not currently valid');
    }
    if (
      approvalValidUntil !== null &&
      manifestValidUntil !== null &&
      approvalValidUntil < manifestValidUntil
    ) {
      errors.push('final approval expires before the closure');
    }
  }

  const reports = context.reports;
  if (
    reports !== undefined &&
    release !== null &&
    sourceRevision !== null &&
    authorityDigest !== null
  ) {
    const android = reports.androidDistribution;
    if (
      validateReportEnvelope(
        android,
        [
          'schemaVersion',
          'product',
          'status',
          'sourceRevision',
          'authorityPublicKeySpkiSha256',
          'validUntil',
          'tier',
          'channel',
          'fullVerifierKind',
          'applicationId',
          'versionCode',
          'versionName',
          'artifactFileName',
          'artifactSha256',
          'artifactSigningCertificateSha256',
          'installedSigningCertificateSha256',
          'firebase',
          'signedEvidenceSha256',
          'fullVerificationReportSha256',
          'verificationManifestSha256',
        ],
        'birthday-autopilot-android-release-verification',
        'Android release report',
        sourceRevision,
        authorityDigest,
        'authorityPublicKeySpkiSha256',
        errors,
      )
    ) {
      componentValidUntil(
        android,
        'Android release report',
        nowMs,
        manifestValidUntil,
        errors,
      );
      for (const field of [
        'artifactSha256',
        'artifactSigningCertificateSha256',
        'installedSigningCertificateSha256',
        'signedEvidenceSha256',
        'fullVerificationReportSha256',
        'verificationManifestSha256',
      ]) {
        requireDigest(
          android[field],
          `Android release report.${field}`,
          errors,
        );
      }
      if (
        exactKeys(
          android.firebase,
          ['projectId', 'projectNumber', 'androidAppId', 'webOauthClientId'],
          'Android release report.firebase',
          errors,
        )
      ) {
        requirePattern(
          android.firebase.projectId,
          PROJECT_ID,
          'Android release report Firebase project ID',
          errors,
        );
        requirePattern(
          android.firebase.projectNumber,
          PROJECT_NUMBER,
          'Android release report Firebase project number',
          errors,
        );
        requirePattern(
          android.firebase.androidAppId,
          FIREBASE_APP_ID,
          'Android release report Firebase app ID',
          errors,
        );
        requirePattern(
          android.firebase.webOauthClientId,
          OAUTH_CLIENT,
          'Android release report packaged web OAuth client ID',
          errors,
        );
        if (
          android.firebase.projectId !== release.firebase.projectId ||
          android.firebase.projectNumber !== release.firebase.projectNumber ||
          android.firebase.androidAppId !== release.firebase.androidAppId ||
          !android.firebase.androidAppId.startsWith(
            `1:${android.firebase.projectNumber}:android:`,
          ) ||
          !android.firebase.webOauthClientId.startsWith(
            `${android.firebase.projectNumber}-`,
          )
        ) {
          errors.push(
            'Android artifact-derived Firebase identity does not match the closure',
          );
        }
      }
      if (
        android.tier !== 'prod' ||
        android.channel !== 'google-play' ||
        android.fullVerifierKind !== 'play-aab'
      ) {
        errors.push(
          'Android report is not the full production Play AAB verifier',
        );
      }
      if (
        android.applicationId !== release.android.applicationId ||
        android.versionCode !== release.android.versionCode ||
        android.versionName !== release.android.versionName ||
        android.artifactFileName !== release.android.artifactFileName ||
        android.artifactSha256 !== release.android.artifactSha256 ||
        android.artifactSigningCertificateSha256 !==
          release.android.artifactSigningCertificateSha256 ||
        android.installedSigningCertificateSha256 !==
          release.android.installedSigningCertificateSha256
      ) {
        errors.push(
          'Android full-verifier coordinates/artifact do not match the closure',
        );
      }
    }

    const playDelivery = reports.androidPlayDelivery;
    if (
      validateReportEnvelope(
        playDelivery,
        [
          'schemaVersion',
          'product',
          'status',
          'sourceRevision',
          'authorityPublicKeySpkiSha256',
          'observedAt',
          'validUntil',
          'tier',
          'channel',
          'physicalDevice',
          'deviceSerialSha256',
          'deviceApi',
          'installerOfRecord',
          'applicationId',
          'versionCode',
          'versionName',
          'uploadAabSha256',
          'deliveredBaseApkSha256',
          'installedSigningCertificateSha1',
          'installedSigningCertificateSha256',
          'installedArtifacts',
          'signedEvidenceSha256',
        ],
        'birthday-autopilot-android-play-delivery-verification',
        'Android Play delivery report',
        sourceRevision,
        authorityDigest,
        'authorityPublicKeySpkiSha256',
        errors,
      )
    ) {
      componentValidUntil(
        playDelivery,
        'Android Play delivery report',
        nowMs,
        manifestValidUntil,
        errors,
      );
      const observedAt = parseInstant(
        playDelivery.observedAt,
        'Android Play delivery report.observedAt',
        errors,
      );
      const playValidUntil = parseInstant(
        playDelivery.validUntil,
        'Android Play delivery report.validUntil',
        errors,
      );
      if (
        observedAt !== null &&
        (observedAt > nowMs + MAXIMUM_CLOCK_SKEW_MS ||
          observedAt <= nowMs - MAXIMUM_PLAY_PROOF_MS)
      ) {
        errors.push('physical Play observation is stale or future-dated');
      }
      if (
        observedAt !== null &&
        playValidUntil !== null &&
        playValidUntil > observedAt + MAXIMUM_PLAY_PROOF_MS
      ) {
        errors.push('physical Play proof validity exceeds 24 hours');
      }
      for (const field of [
        'deviceSerialSha256',
        'uploadAabSha256',
        'deliveredBaseApkSha256',
        'installedSigningCertificateSha256',
        'signedEvidenceSha256',
      ]) {
        requireDigest(
          playDelivery[field],
          `Android Play delivery report.${field}`,
          errors,
        );
      }
      requirePattern(
        playDelivery.installedSigningCertificateSha1,
        SHA1,
        'Android Play delivery report.installedSigningCertificateSha1',
        errors,
      );
      if (
        playDelivery.tier !== 'prod' ||
        playDelivery.channel !== 'google-play' ||
        playDelivery.physicalDevice !== true ||
        !Number.isSafeInteger(playDelivery.deviceApi) ||
        playDelivery.deviceApi < 29 ||
        playDelivery.installerOfRecord !== 'com.android.vending' ||
        playDelivery.applicationId !== release.android.applicationId ||
        playDelivery.versionCode !== release.android.versionCode ||
        playDelivery.versionName !== release.android.versionName ||
        playDelivery.uploadAabSha256 !== release.android.artifactSha256 ||
        playDelivery.deliveredBaseApkSha256 !==
          release.android.deliveredBaseApkSha256 ||
        playDelivery.installedSigningCertificateSha1 !==
          release.android.installedSigningCertificateSha1 ||
        playDelivery.installedSigningCertificateSha256 !==
          release.android.installedSigningCertificateSha256
      ) {
        errors.push(
          'physical Play delivery coordinates/certificates do not match the closure',
        );
      }
      if (
        !Array.isArray(playDelivery.installedArtifacts) ||
        playDelivery.installedArtifacts.length < 1 ||
        playDelivery.installedArtifacts.length > 256
      ) {
        errors.push('physical Play installed artifact inventory is invalid');
      } else {
        const paths = new Set();
        let baseCount = 0;
        for (const artifact of playDelivery.installedArtifacts) {
          if (
            !exactKeys(
              artifact,
              [
                'role',
                'packagePath',
                'fileName',
                'bytes',
                'sha256',
                'signingCertificateSha1',
                'signingCertificateSha256',
              ],
              'Android Play installed artifact',
              errors,
            )
          ) {
            continue;
          }
          requireDigest(
            artifact.sha256,
            'Android Play installed artifact.sha256',
            errors,
          );
          if (
            !['base', 'split'].includes(artifact.role) ||
            typeof artifact.packagePath !== 'string' ||
            !artifact.packagePath.startsWith('/data/') ||
            artifact.packagePath.includes('..') ||
            !artifact.packagePath.endsWith(`/${artifact.fileName}`) ||
            typeof artifact.fileName !== 'string' ||
            !SAFE_FILE_NAME.test(artifact.fileName) ||
            !artifact.fileName.endsWith('.apk') ||
            !Number.isSafeInteger(artifact.bytes) ||
            artifact.bytes <= 0 ||
            artifact.signingCertificateSha1 !==
              playDelivery.installedSigningCertificateSha1 ||
            artifact.signingCertificateSha256 !==
              playDelivery.installedSigningCertificateSha256 ||
            paths.has(artifact.packagePath)
          ) {
            errors.push('Android Play installed artifact is invalid');
          }
          paths.add(artifact.packagePath);
          if (artifact.role === 'base') {
            baseCount += 1;
            if (
              artifact.fileName !== 'base.apk' ||
              artifact.sha256 !== playDelivery.deliveredBaseApkSha256
            ) {
              errors.push('Android Play base APK inventory is invalid');
            }
          } else if (artifact.fileName === 'base.apk') {
            errors.push('Android Play split inventory contains a second base');
          }
        }
        if (baseCount !== 1) {
          errors.push('Android Play inventory must contain exactly one base');
        }
      }
    }

    const ios = reports.iosRelease;
    if (
      validateReportEnvelope(
        ios,
        [
          'schemaVersion',
          'product',
          'status',
          'sourceRevision',
          'validUntil',
          'evidenceSha256',
          'evidenceAuthorityPublicKeySpkiSha256',
          'observed',
          'referenceDigests',
        ],
        'birthday-autopilot-ios-release-verification',
        'iOS release report',
        sourceRevision,
        authorityDigest,
        'evidenceAuthorityPublicKeySpkiSha256',
        errors,
      )
    ) {
      componentValidUntil(
        ios,
        'iOS release report',
        nowMs,
        manifestValidUntil,
        errors,
      );
      const observed = ios.observed;
      if (
        observed?.sourceRevision !== sourceRevision ||
        observed?.artifact?.bundleIdentifier !== release.ios.bundleId ||
        observed?.artifact?.marketingVersion !== release.ios.shortVersion ||
        observed?.artifact?.buildNumber !== release.ios.buildNumber ||
        observed?.artifact?.ipaSha256 !== release.ios.artifactSha256 ||
        observed?.signing?.exportedCertificateSha256 !==
          release.ios.distributionCertificateSha256
      ) {
        errors.push(
          'iOS release coordinates/artifact do not match the closure',
        );
      }
      if (
        observed?.firebase?.environment !== 'prod' ||
        observed?.firebase?.projectId !== release.firebase.projectId ||
        observed?.firebase?.projectNumber !== release.firebase.projectNumber ||
        observed?.firebase?.googleAppId !== release.firebase.iosAppId
      ) {
        errors.push(
          'iOS Firebase environment/project does not match the closure',
        );
      }
    }

    const cloud = reports.cloud;
    if (
      validateReportEnvelope(
        cloud,
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
        'birthday-autopilot-cloud-release-verification',
        'cloud report',
        sourceRevision,
        authorityDigest,
        'authorityPublicKeySpkiSha256',
        errors,
      )
    ) {
      componentValidUntil(
        cloud,
        'cloud report',
        nowMs,
        manifestValidUntil,
        errors,
      );
      const projectFieldsValid = exactKeys(
        cloud.project,
        [
          'environment',
          'projectId',
          'projectNumber',
          'androidAppId',
          'iosAppId',
          'webAppId',
          'androidPackage',
          'iosBundle',
        ],
        'cloud report.project',
        errors,
      );
      if (
        !projectFieldsValid ||
        cloud.project?.environment !== 'production' ||
        cloud.project?.projectId !== release.firebase.projectId ||
        cloud.project?.projectNumber !== release.firebase.projectNumber ||
        cloud.project?.androidAppId !== release.firebase.androidAppId ||
        cloud.project?.iosAppId !== release.firebase.iosAppId ||
        cloud.project?.androidPackage !== release.android.applicationId ||
        cloud.project?.iosBundle !== release.ios.bundleId
      ) {
        errors.push('cloud project/app coordinates do not match the closure');
      }
      const trust = cloud.clientTrust;
      if (
        exactKeys(
          trust,
          ['androidGooglePlay', 'ios', 'web'],
          'cloud report.clientTrust',
          errors,
        )
      ) {
        if (
          exactKeys(
            trust.androidGooglePlay,
            [
              'appCheckSigningCertificateSha256',
              'oauthAndroidClientId',
              'oauthSigningCertificateSha1',
              'webOauthClientId',
            ],
            'cloud report.clientTrust.androidGooglePlay',
            errors,
          )
        ) {
          requireDigest(
            trust.androidGooglePlay.appCheckSigningCertificateSha256,
            'cloud report Android App Check signing certificate',
            errors,
          );
          requirePattern(
            trust.androidGooglePlay.oauthAndroidClientId,
            OAUTH_CLIENT,
            'cloud report Android OAuth client ID',
            errors,
          );
          requirePattern(
            trust.androidGooglePlay.oauthSigningCertificateSha1,
            SHA1,
            'cloud report Android OAuth signing certificate',
            errors,
          );
          requirePattern(
            trust.androidGooglePlay.webOauthClientId,
            OAUTH_CLIENT,
            'cloud report Android packaged web OAuth client ID',
            errors,
          );
          if (
            trust.androidGooglePlay.appCheckSigningCertificateSha256 !==
              playDelivery?.installedSigningCertificateSha256 ||
            trust.androidGooglePlay.oauthSigningCertificateSha1 !==
              playDelivery?.installedSigningCertificateSha1 ||
            trust.androidGooglePlay.webOauthClientId !==
              android?.firebase?.webOauthClientId
          ) {
            errors.push(
              'cloud Android App Check/OAuth trust does not match the AAB and Play-installed app',
            );
          }
        }
        if (
          exactKeys(
            trust.ios,
            ['oauthClientId', 'reversedClientId', 'teamId'],
            'cloud report.clientTrust.ios',
            errors,
          )
        ) {
          requirePattern(
            trust.ios.oauthClientId,
            OAUTH_CLIENT,
            'cloud report iOS OAuth client ID',
            errors,
          );
          requirePattern(
            trust.ios.reversedClientId,
            REVERSED_CLIENT,
            'cloud report iOS reversed OAuth client ID',
            errors,
          );
          requirePattern(
            trust.ios.teamId,
            TEAM_IDENTIFIER,
            'cloud report iOS team ID',
            errors,
          );
          if (
            trust.ios.oauthClientId !==
              ios?.observed?.firebase?.oauthClientId ||
            trust.ios.reversedClientId !==
              ios?.observed?.firebase?.reversedClientId ||
            trust.ios.teamId !== ios?.observed?.signing?.teamIdentifier
          ) {
            errors.push(
              'cloud iOS OAuth/team trust does not match the inspected IPA',
            );
          }
        }
        if (
          exactKeys(
            trust.web,
            ['firebaseAppId', 'recaptchaEnterpriseSiteKeySha256'],
            'cloud report.clientTrust.web',
            errors,
          )
        ) {
          requirePattern(
            trust.web.firebaseAppId,
            FIREBASE_APP_ID,
            'cloud report web Firebase app ID',
            errors,
          );
          requireDigest(
            trust.web.recaptchaEnterpriseSiteKeySha256,
            'cloud report reCAPTCHA Enterprise site-key digest',
            errors,
          );
          if (trust.web.firebaseAppId !== cloud.project?.webAppId) {
            errors.push(
              'cloud web App Check registration does not match the cloud project',
            );
          }
        }
      }
      if (
        cloud.hosting?.publicBaseUrl !== release.hosting.publicBaseUrl ||
        cloud.hosting?.releaseConfigSha256 !==
          release.hosting.releaseConfigSha256 ||
        cloud.hosting?.deployedArtifactSha256 !==
          release.hosting.deployedArtifactSha256
      ) {
        errors.push('cloud Hosting proof does not match the closure');
      }
      exactKeys(
        cloud.hostingReleaseControl,
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
        'cloud report.hostingReleaseControl',
        errors,
      );
    }

    const store = reports.store;
    if (
      validateReportEnvelope(
        store,
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
        'birthday-autopilot-store-release-verification',
        'store report',
        sourceRevision,
        authorityDigest,
        'authorityPublicKeySpkiSha256',
        errors,
      )
    ) {
      componentValidUntil(
        store,
        'store report',
        nowMs,
        manifestValidUntil,
        errors,
      );
      const androidCoordinate = store.releaseCoordinates?.android;
      const iosCoordinate = store.releaseCoordinates?.ios;
      if (
        androidCoordinate?.applicationId !== release.android.applicationId ||
        androidCoordinate?.versionCode !== release.android.versionCode ||
        androidCoordinate?.versionName !== release.android.versionName ||
        androidCoordinate?.artifactKind !== 'aab' ||
        androidCoordinate?.artifactFileName !==
          release.android.artifactFileName ||
        androidCoordinate?.artifactSha256 !== release.android.artifactSha256 ||
        androidCoordinate?.signingCertificateSha256 !==
          release.android.installedSigningCertificateSha256 ||
        androidCoordinate?.signingCertificateSha256 !==
          playDelivery?.installedSigningCertificateSha256 ||
        store.artifactDigests?.android !== release.android.artifactSha256
      ) {
        errors.push(
          'store Android coordinates/artifact do not match the closure',
        );
      }
      if (
        iosCoordinate?.bundleId !== release.ios.bundleId ||
        iosCoordinate?.shortVersion !== release.ios.shortVersion ||
        iosCoordinate?.buildNumber !== release.ios.buildNumber ||
        iosCoordinate?.artifactKind !== 'ipa' ||
        iosCoordinate?.artifactFileName !== release.ios.artifactFileName ||
        iosCoordinate?.artifactSha256 !== release.ios.artifactSha256 ||
        iosCoordinate?.distributionCertificateSha256 !==
          release.ios.distributionCertificateSha256 ||
        store.artifactDigests?.ios !== release.ios.artifactSha256
      ) {
        errors.push('store iOS coordinates/artifact do not match the closure');
      }
      if (
        store.publicIdentity?.publicBaseUrl !== release.hosting.publicBaseUrl ||
        store.hostingConfigSha256 !== release.hosting.releaseConfigSha256
      ) {
        errors.push('store Hosting identity/config do not match the closure');
      }
    }

    const hosting = reports.hosting;
    if (
      validateReportEnvelope(
        hosting,
        [
          'schemaVersion',
          'product',
          'status',
          'sourceRevision',
          'authorityPublicKeySpkiSha256',
          'validUntil',
          'publicBaseUrl',
          'projectId',
          'projectNumber',
          'webAppId',
          'siteId',
          'deployedVersionId',
          'releaseName',
          'versionName',
          'releaseTime',
          'providerOriginObservationSha256',
          'firebaseWebConfigObservationSha256',
          'currentLiveObservationSha256',
          'currentLiveCapturedAt',
          'currentLiveObserverServiceAccount',
          'currentLiveObserverWifProvider',
          'repositoryId',
          'repositoryOwnerId',
          'admissionSecurityProjectId',
          'applicationIamAnalysisScope',
          'releaseSecurityIamAnalysisScope',
          'admissionSecurityProjectNumber',
          'admissionBucketName',
          'admissionBucketMetageneration',
          'admissionObjectName',
          'admissionObjectGeneration',
          'admissionObjectMetageneration',
          'admissionObjectTimeCreated',
          'admissionObjectRetentionExpirationTime',
          'admissionRetentionSeconds',
          'admissionLifecycleDeleteAgeDays',
          'admissionReaderServiceAccount',
          'admissionReaderWifProvider',
          'admissionCheckCheckedAt',
          'admissionCheckPageCount',
          'admissionCheckObjectCount',
          'admissionCheckManifestSha256',
          'admissionCheckSnapshotSha256',
          'admissionCheckRawRootSha256',
          'admissionCheckRawRootBytes',
          'hostingBuildArtifactId',
          'hostingBuildArtifactDigest',
          'hostingAdmissionArtifactId',
          'hostingAdmissionArtifactDigest',
          'hostingDeployServiceAccount',
          'hostingDeployWifProvider',
          'hostingDeployAuthenticatedAt',
          'admissionAuditLogBucketName',
          'admissionAuditSinkName',
          'admissionRunId',
          'admissionRunAttempt',
          'admissionValidUntil',
          'admissionLeaseContentSha256',
          'admissionBucketObservationSha256',
          'admissionObjectObservationSha256',
          'releaseConfigSha256',
          'deployedArtifactSha256',
          'deploymentManifestSha256',
          'deploymentProvenanceSha256',
          'deploymentConfigSha256',
          'publicTreeSha256',
          'firebaseConfigSha256',
          'recaptchaEnterpriseSiteKeySha256',
          'hostingSourceTreeSha256',
          'cloudReportSha256',
          'storeReportSha256',
        ],
        'birthday-autopilot-hosting-release-verification',
        'Hosting report',
        sourceRevision,
        authorityDigest,
        'authorityPublicKeySpkiSha256',
        errors,
      )
    ) {
      componentValidUntil(
        hosting,
        'Hosting report',
        nowMs,
        manifestValidUntil,
        errors,
      );
      const expectedAdmissionLeaseContentSha256 = sha256(
        Buffer.from(
          stableJson({
            schemaVersion: 1,
            siteId: hosting.siteId,
            sourceRevision: hosting.sourceRevision,
            runId: hosting.admissionRunId,
            runAttempt: hosting.admissionRunAttempt,
            validUntil: hosting.admissionValidUntil,
          }),
          'utf8',
        ),
      );
      if (
        hosting.publicBaseUrl !== release.hosting.publicBaseUrl ||
        hosting.releaseConfigSha256 !== release.hosting.releaseConfigSha256 ||
        hosting.deployedArtifactSha256 !==
          release.hosting.deployedArtifactSha256 ||
        hosting.projectId !== release.firebase.projectId ||
        hosting.projectNumber !== release.firebase.projectNumber ||
        hosting.webAppId !== cloud?.project?.webAppId ||
        hosting.siteId !== cloud?.hosting?.siteId ||
        hosting.deployedVersionId !== cloud?.hosting?.deployedVersionId ||
        hosting.releaseName !==
          `sites/${hosting.siteId}/releases/${hosting.releaseName
            ?.split('/')
            .at(-1)}` ||
        hosting.versionName !==
          `sites/${hosting.siteId}/versions/${hosting.deployedVersionId}` ||
        hosting.deploymentManifestSha256 !==
          cloud?.hosting?.deploymentManifestSha256 ||
        hosting.deploymentProvenanceSha256 !==
          cloud?.hosting?.deploymentProvenanceSha256 ||
        hosting.deploymentConfigSha256 !==
          cloud?.hosting?.deploymentConfigSha256 ||
        hosting.publicTreeSha256 !== cloud?.hosting?.publicTreeSha256 ||
        hosting.firebaseConfigSha256 !== cloud?.hosting?.firebaseConfigSha256 ||
        hosting.hostingSourceTreeSha256 !==
          cloud?.hosting?.hostingSourceTreeSha256 ||
        hosting.providerOriginObservationSha256 !==
          cloud?.hosting?.providerOriginObservationSha256 ||
        hosting.firebaseWebConfigObservationSha256 !==
          cloud?.hosting?.firebaseWebConfigObservationSha256 ||
        hosting.currentLiveObservationSha256 !==
          cloud?.hosting?.currentLiveObservationSha256 ||
        !SERVICE_ACCOUNT.test(
          hosting.currentLiveObserverServiceAccount ?? '',
        ) ||
        !WIF_PROVIDER.test(hosting.currentLiveObserverWifProvider ?? '') ||
        hosting.currentLiveObserverServiceAccount !==
          cloud?.hostingReleaseControl?.observerServiceAccount ||
        hosting.currentLiveObserverWifProvider !==
          cloud?.hostingReleaseControl?.observerWifProvider ||
        !/^[1-9][0-9]{0,19}$/u.test(hosting.repositoryId ?? '') ||
        !/^[1-9][0-9]{0,19}$/u.test(hosting.repositoryOwnerId ?? '') ||
        hosting.repositoryId !== cloud?.hostingReleaseControl?.repositoryId ||
        hosting.repositoryOwnerId !==
          cloud?.hostingReleaseControl?.repositoryOwnerId ||
        !PROJECT_ID.test(hosting.admissionSecurityProjectId ?? '') ||
        hosting.admissionSecurityProjectId === hosting.projectId ||
        hosting.admissionSecurityProjectId !==
          cloud?.hostingReleaseControl?.releaseSecurityProjectId ||
        hosting.applicationIamAnalysisScope !==
          cloud?.hostingReleaseControl?.applicationIamAnalysisScope ||
        hosting.releaseSecurityIamAnalysisScope !==
          cloud?.hostingReleaseControl?.releaseSecurityIamAnalysisScope ||
        (!/^organizations\/[1-9][0-9]{5,19}$/u.test(
          hosting.applicationIamAnalysisScope ?? '',
        ) &&
          hosting.applicationIamAnalysisScope !==
            `projects/${hosting.projectId}`) ||
        (!/^organizations\/[1-9][0-9]{5,19}$/u.test(
          hosting.releaseSecurityIamAnalysisScope ?? '',
        ) &&
          hosting.releaseSecurityIamAnalysisScope !==
            `projects/${hosting.admissionSecurityProjectId}`) ||
        !/^[1-9][0-9]{5,19}$/u.test(
          hosting.admissionSecurityProjectNumber ?? '',
        ) ||
        hosting.admissionSecurityProjectNumber === hosting.projectNumber ||
        hosting.admissionSecurityProjectNumber !==
          cloud?.hostingReleaseControl?.releaseSecurityProjectNumber ||
        !/^[a-z0-9][a-z0-9-]{4,61}[a-z0-9]$/u.test(
          hosting.admissionBucketName ?? '',
        ) ||
        !/^[1-9][0-9]{0,19}$/u.test(
          hosting.admissionBucketMetageneration ?? '',
        ) ||
        hosting.admissionBucketName !==
          cloud?.hostingReleaseControl?.admissionBucketName ||
        hosting.admissionBucketMetageneration !==
          cloud?.hostingReleaseControl?.admissionBucketMetageneration ||
        hosting.admissionObjectName !==
          `hosting-production-change-freezes/${hosting.siteId}/${hosting.sourceRevision}/${hosting.admissionRunId}/${hosting.admissionRunAttempt}.json` ||
        !/^[1-9][0-9]{0,19}$/u.test(hosting.admissionObjectGeneration ?? '') ||
        !/^[1-9][0-9]{0,19}$/u.test(
          hosting.admissionObjectMetageneration ?? '',
        ) ||
        hosting.admissionRetentionSeconds !== 900 ||
        hosting.admissionLifecycleDeleteAgeDays !== 1 ||
        !SERVICE_ACCOUNT.test(hosting.admissionReaderServiceAccount ?? '') ||
        !WIF_PROVIDER.test(hosting.admissionReaderWifProvider ?? '') ||
        hosting.admissionReaderServiceAccount !==
          cloud?.hostingReleaseControl?.admissionReaderServiceAccount ||
        hosting.admissionReaderWifProvider !==
          cloud?.hostingReleaseControl?.admissionReaderWifProvider ||
        !Number.isSafeInteger(hosting.admissionCheckPageCount) ||
        hosting.admissionCheckPageCount <= 0 ||
        !Number.isSafeInteger(hosting.admissionCheckObjectCount) ||
        hosting.admissionCheckObjectCount < 0 ||
        !SHA256.test(hosting.admissionCheckManifestSha256 ?? '') ||
        !SHA256.test(hosting.admissionCheckSnapshotSha256 ?? '') ||
        !SHA256.test(hosting.admissionCheckRawRootSha256 ?? '') ||
        !Number.isSafeInteger(hosting.admissionCheckRawRootBytes) ||
        hosting.admissionCheckRawRootBytes <= 0 ||
        !/^[1-9][0-9]{0,19}$/u.test(hosting.hostingBuildArtifactId ?? '') ||
        !SHA256.test(hosting.hostingBuildArtifactDigest ?? '') ||
        !/^[1-9][0-9]{0,19}$/u.test(hosting.hostingAdmissionArtifactId ?? '') ||
        !SHA256.test(hosting.hostingAdmissionArtifactDigest ?? '') ||
        !SERVICE_ACCOUNT.test(hosting.hostingDeployServiceAccount ?? '') ||
        !hosting.hostingDeployServiceAccount.endsWith(
          `@${hosting.projectId}.iam.gserviceaccount.com`,
        ) ||
        !WIF_PROVIDER.test(hosting.hostingDeployWifProvider ?? '') ||
        hosting.hostingDeployServiceAccount !==
          cloud?.hostingReleaseControl?.deployServiceAccount ||
        hosting.hostingDeployWifProvider !==
          cloud?.hostingReleaseControl?.deployWifProvider ||
        hosting.currentLiveObserverServiceAccount ===
          hosting.admissionReaderServiceAccount ||
        hosting.currentLiveObserverServiceAccount ===
          hosting.hostingDeployServiceAccount ||
        hosting.admissionReaderServiceAccount ===
          hosting.hostingDeployServiceAccount ||
        !new RegExp(
          `^projects/${hosting.admissionSecurityProjectId}/locations/(?:[a-z]+(?:-[a-z]+[0-9])?|global|eu|us)/buckets/[a-zA-Z0-9._-]{1,100}$`,
          'u',
        ).test(hosting.admissionAuditLogBucketName ?? '') ||
        hosting.admissionAuditLogBucketName !==
          cloud?.hostingReleaseControl?.auditLogBucketName ||
        !new RegExp(
          `^projects/${hosting.admissionSecurityProjectId}/sinks/[A-Za-z][A-Za-z0-9._-]{0,99}$`,
          'u',
        ).test(hosting.admissionAuditSinkName ?? '') ||
        hosting.admissionAuditSinkName !==
          cloud?.hostingReleaseControl?.auditSinkName ||
        !/^[1-9][0-9]{0,19}$/u.test(hosting.admissionRunId ?? '') ||
        !/^[1-9][0-9]{0,9}$/u.test(hosting.admissionRunAttempt ?? '') ||
        hosting.admissionLeaseContentSha256 !==
          expectedAdmissionLeaseContentSha256 ||
        !SHA256.test(hosting.admissionBucketObservationSha256 ?? '') ||
        !SHA256.test(hosting.admissionObjectObservationSha256 ?? '') ||
        hosting.recaptchaEnterpriseSiteKeySha256 !==
          cloud?.clientTrust?.web?.recaptchaEnterpriseSiteKeySha256 ||
        hosting.cloudReportSha256 !== document.components.cloud.sha256 ||
        hosting.storeReportSha256 !== document.components.store.sha256
      ) {
        errors.push(
          'Hosting report does not match the closure/component bytes',
        );
      }
      const currentLiveCapturedAt = parseInstant(
        hosting.currentLiveCapturedAt,
        'Hosting report current-live capturedAt',
        errors,
      );
      const hostingValidUntil = Date.parse(hosting.validUntil);
      const admissionValidUntil = parseInstant(
        hosting.admissionValidUntil,
        'Hosting report admission validUntil',
        errors,
      );
      const admissionObjectTimeCreated = parseInstant(
        hosting.admissionObjectTimeCreated,
        'Hosting report admission object timeCreated',
        errors,
      );
      const admissionObjectRetentionExpirationTime = parseInstant(
        hosting.admissionObjectRetentionExpirationTime,
        'Hosting report admission object retentionExpirationTime',
        errors,
      );
      const admissionCheckCheckedAt = parseInstant(
        hosting.admissionCheckCheckedAt,
        'Hosting report admission check checkedAt',
        errors,
      );
      const hostingDeployAuthenticatedAt = parseInstant(
        hosting.hostingDeployAuthenticatedAt,
        'Hosting report deploy authenticatedAt',
        errors,
      );
      const hostingReleaseTime = parseInstant(
        hosting.releaseTime,
        'Hosting report releaseTime',
        errors,
      );
      if (
        currentLiveCapturedAt !== null &&
        (currentLiveCapturedAt > nowMs + MAXIMUM_CLOCK_SKEW_MS ||
          nowMs - currentLiveCapturedAt >= 15 * 60 * 1_000 ||
          hostingValidUntil > currentLiveCapturedAt + 15 * 60 * 1_000 ||
          admissionValidUntil !== currentLiveCapturedAt + 15 * 60 * 1_000 ||
          admissionObjectTimeCreated < currentLiveCapturedAt ||
          admissionObjectRetentionExpirationTime -
            admissionObjectTimeCreated !==
            15 * 60 * 1_000 ||
          admissionValidUntil > admissionObjectRetentionExpirationTime ||
          admissionCheckCheckedAt > hostingDeployAuthenticatedAt ||
          hostingDeployAuthenticatedAt > hostingReleaseTime ||
          admissionCheckCheckedAt > currentLiveCapturedAt ||
          nowMs >= admissionValidUntil)
      ) {
        errors.push('Hosting current-live observation is stale or overlong');
      }
    }

    if (context.artifacts !== undefined) {
      for (const [name, coordinate] of [
        ['android', release.android],
        ['ios', release.ios],
      ]) {
        const artifact = context.artifacts[name];
        if (
          artifact?.sha256 !== coordinate.artifactSha256 ||
          artifact?.fileName !== coordinate.artifactFileName
        ) {
          errors.push(`${name} artifact bytes/name do not match the closure`);
        }
      }
      if (
        context.artifacts.hosting?.sha256 !==
        release.hosting.deployedArtifactSha256
      ) {
        errors.push('Hosting artifact bytes do not match the closure');
      }
      if (
        context.artifacts.androidDeliveredBase?.sha256 !==
          release.android.deliveredBaseApkSha256 ||
        context.artifacts.androidDeliveredBase?.fileName !==
          release.android.deliveredBaseApkFileName
      ) {
        errors.push(
          'Play-delivered base APK bytes/name do not match the closure',
        );
      }
    }
  }
  return errors;
}

export function validateProductionReleaseClosureTemplate(document) {
  const errors = [];
  if (!exactKeys(document, TOP_LEVEL_KEYS, 'template', errors)) return errors;
  if (
    document.$schema !== './production-release-closure.schema.json' ||
    document.schemaVersion !== 1 ||
    document.product !== PRODUCT ||
    document.status !== 'pending'
  ) {
    errors.push('template identity/status is invalid');
  }
  if (
    document.source?.revision !== null ||
    document.source?.authorityPublicKeySpkiSha256 !== null ||
    document.release?.firebase?.projectId !== null ||
    document.release?.firebase?.projectNumber !== null ||
    document.release?.firebase?.androidAppId !== null ||
    document.release?.firebase?.iosAppId !== null ||
    document.release?.android?.artifactSha256 !== null ||
    document.release?.android?.artifactSigningCertificateSha256 !== null ||
    document.release?.android?.installedSigningCertificateSha1 !== null ||
    document.release?.android?.installedSigningCertificateSha256 !== null ||
    document.release?.android?.deliveredBaseApkFileName !== null ||
    document.release?.android?.deliveredBaseApkSha256 !== null ||
    document.release?.ios?.artifactSha256 !== null ||
    document.release?.hosting?.publicBaseUrl !== null ||
    document.finalApproval?.decision !== 'pending'
  ) {
    errors.push('template must retain all external production blockers');
  }
  for (const name of COMPONENT_NAMES) {
    if (
      document.components?.[name]?.sha256 !== null ||
      document.components?.[name]?.bytes !== 0
    ) {
      errors.push(`template component ${name} must remain unbound`);
    }
  }
  const releaseErrors = validateProductionReleaseClosure(document, {
    nowMs: Date.now(),
  });
  if (releaseErrors.length === 0) {
    errors.push('template must never validate as a production release');
  }
  return errors;
}

const metadata = value => ({
  dev: value.dev,
  ino: value.ino,
  mode: value.mode,
  nlink: value.nlink,
  size: value.size,
  mtimeNs: value.mtimeNs,
  ctimeNs: value.ctimeNs,
});

const sameMetadata = (left, right) =>
  Object.keys(left).every(key => left[key] === right[key]);

const readStableFile = (file, maximumBytes, label) => {
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
    if (!sameMetadata(metadata(before), metadata(opened))) {
      throw new Error(`${label} changed before it was read`);
    }
    const bytes = readFileSync(descriptor);
    const after = fstatSync(descriptor, { bigint: true });
    const requestedAfter = lstatSync(requested, { bigint: true });
    const canonicalAfter = lstatSync(canonical, { bigint: true });
    if (
      BigInt(bytes.byteLength) !== opened.size ||
      !sameMetadata(metadata(opened), metadata(after)) ||
      requestedAfter.isSymbolicLink() ||
      !sameMetadata(metadata(before), metadata(requestedAfter)) ||
      !sameMetadata(metadata(opened), metadata(canonicalAfter)) ||
      realpathSync(requested) !== canonical
    ) {
      throw new Error(`${label} changed while it was read`);
    }
    return {
      bytes,
      requested,
      canonical,
      requestedMetadata: metadata(requestedAfter),
      canonicalMetadata: metadata(canonicalAfter),
    };
  } finally {
    closeSync(descriptor);
  }
};

const assertUnchanged = (record, label) => {
  const requested = lstatSync(record.requested, { bigint: true });
  const canonical = lstatSync(record.canonical, { bigint: true });
  if (
    requested.isSymbolicLink() ||
    canonical.isSymbolicLink() ||
    !sameMetadata(record.requestedMetadata, metadata(requested)) ||
    !sameMetadata(record.canonicalMetadata, metadata(canonical)) ||
    realpathSync(record.requested) !== record.canonical
  ) {
    throw new Error(`${label} changed during release closure validation`);
  }
};

const parseJson = (bytes, label) => {
  try {
    return JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`${label} is not valid JSON`);
  }
};

const git = args =>
  execFileSync('git', args, {
    cwd: PROJECT_ROOT,
    encoding: 'utf8',
    env: SANITIZED_GIT_ENVIRONMENT,
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();

const inspectCleanSource = pinBytes => {
  const source = inspectCleanGitSource(PROJECT_ROOT, pinBytes);
  if (source.errors.length > 0) throw new Error(source.errors.join('; '));
  const repository = git(['remote', 'get-url', 'origin']);
  if (!AUTHORITATIVE_REPOSITORIES.has(repository)) {
    throw new Error('checkout does not use the authoritative origin');
  }
  return source.sourceRevision;
};

export const loadProductionClosureReports = (rootPath, components) => {
  const paths = COMPONENT_NAMES.map(name => components[name].path);
  if (new Set(paths).size !== COMPONENT_NAMES.length) {
    throw new Error('component reports must use six distinct paths');
  }
  const inventory = collectDistributionEvidenceFiles(rootPath);
  if (
    inventory.size !== COMPONENT_NAMES.length ||
    paths.some(reportPath => !inventory.has(reportPath))
  ) {
    throw new Error(
      'evidence root must contain exactly the six referenced reports',
    );
  }
  const root = path.resolve(rootPath);
  const reports = {};
  const reportFiles = {};
  const records = [];
  for (const name of COMPONENT_NAMES) {
    const reference = components[name];
    if (!safeRelativePath(reference.path)) {
      throw new Error(`component ${name} path is unsafe`);
    }
    const requested = path.resolve(root, reference.path);
    const relative = path.relative(root, requested);
    if (relative === '..' || relative.startsWith(`..${path.sep}`)) {
      throw new Error(`component ${name} escapes the evidence root`);
    }
    const record = readStableFile(
      requested,
      MAXIMUM_REPORT_BYTES,
      `${name} report`,
    );
    records.push([record, `${name} report`]);
    reports[name] = parseJson(record.bytes, `${name} report`);
    reportFiles[name] = {
      sha256: sha256(record.bytes),
      bytes: record.bytes.byteLength,
    };
  }
  return { reports, reportFiles, records, rootPath, inventory };
};

export const loadInstalledPlayApkArtifacts = (rootPath, playReport) => {
  if (
    !Array.isArray(playReport?.installedArtifacts) ||
    playReport.installedArtifacts.length < 1
  ) {
    throw new Error('Play report installed artifact inventory is missing');
  }
  const names = playReport.installedArtifacts.map(
    artifact => artifact.fileName,
  );
  if (new Set(names).size !== names.length) {
    throw new Error('installed Play artifact file names must be unique');
  }
  const inventory = collectDistributionEvidenceFiles(rootPath);
  if (
    inventory.size !== names.length ||
    names.some(name => !inventory.has(name))
  ) {
    throw new Error(
      'installed APK root must contain exactly the reported base and split files',
    );
  }
  const root = path.resolve(rootPath);
  const records = [];
  for (const artifact of playReport.installedArtifacts) {
    if (
      typeof artifact.fileName !== 'string' ||
      !SAFE_FILE_NAME.test(artifact.fileName) ||
      !artifact.fileName.endsWith('.apk')
    ) {
      throw new Error('installed Play artifact file name is unsafe');
    }
    const record = readStableFile(
      path.join(root, artifact.fileName),
      MAXIMUM_ANDROID_BYTES,
      `installed Play artifact ${artifact.fileName}`,
    );
    if (
      sha256(record.bytes) !== artifact.sha256 ||
      record.bytes.byteLength !== artifact.bytes
    ) {
      throw new Error(
        `installed Play artifact ${artifact.fileName} bytes do not match the report`,
      );
    }
    records.push([record, `installed Play artifact ${artifact.fileName}`]);
  }
  return { rootPath, inventory, records };
};

const assertReportInventoryUnchanged = loaded => {
  const after = collectDistributionEvidenceFiles(loaded.rootPath);
  if (
    after.size !== loaded.inventory.size ||
    [...loaded.inventory].some(([name, expected]) => {
      const actual = after.get(name);
      return (
        actual === undefined ||
        actual.sha256 !== expected.sha256 ||
        actual.bytes !== expected.bytes
      );
    })
  ) {
    throw new Error('component report inventory changed during validation');
  }
};

const assertInstalledApkInventoryUnchanged = loaded => {
  const after = collectDistributionEvidenceFiles(loaded.rootPath);
  if (
    after.size !== loaded.inventory.size ||
    [...loaded.inventory].some(([name, expected]) => {
      const actual = after.get(name);
      return (
        actual === undefined ||
        actual.sha256 !== expected.sha256 ||
        actual.bytes !== expected.bytes
      );
    })
  ) {
    throw new Error('installed APK inventory changed during validation');
  }
};

const parseArguments = argv => {
  if (argv.length % 2 !== 0)
    throw new Error('arguments must be --name value pairs');
  const result = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!flag?.startsWith('--') || value === undefined) {
      throw new Error('arguments must be --name value pairs');
    }
    const name = flag.slice(2);
    if (!CLI_KEYS.has(name) || result.has(name)) {
      throw new Error(`unsupported or duplicate argument ${flag}`);
    }
    result.set(name, value);
  }
  return result;
};

const run = () => {
  const args = parseArguments(process.argv.slice(2));
  const mode = args.get('mode');
  const file = args.get('file');
  if (!['release', 'template'].includes(mode) || file === undefined) {
    throw new Error('--mode release|template and --file are required');
  }
  const manifestRecord = readStableFile(
    file,
    MAXIMUM_MANIFEST_BYTES,
    'production release closure manifest',
  );
  const document = parseJson(
    manifestRecord.bytes,
    'production release closure manifest',
  );
  if (mode === 'template') {
    const errors = validateProductionReleaseClosureTemplate(document);
    if (errors.length > 0) throw new Error(errors.join('; '));
    process.stdout.write(
      'PASS production release closure template remains unusable\n',
    );
    return;
  }
  const required = [
    'signature',
    'public-key',
    'evidence-root',
    'android-artifact',
    'android-delivered-base',
    'android-installed-apk-root',
    'ios-artifact',
    'hosting-artifact',
  ];
  const missing = required.filter(name => !args.has(name));
  if (missing.length > 0)
    throw new Error(`missing arguments: ${missing.join(', ')}`);
  const signatureRecord = readStableFile(
    args.get('signature'),
    64,
    'closure signature',
  );
  const publicKeyRecord = readStableFile(
    args.get('public-key'),
    8 * 1024,
    'release authority public key',
  );
  const pinRecord = readStableFile(
    AUTHORITY_PIN_PATH,
    1024,
    'release authority pin',
  );
  const sourceRevision = inspectCleanSource(pinRecord.bytes);
  const authority = verifyDistributionEvidenceAuthority({
    rawEvidence: manifestRecord.bytes,
    detachedSignature: signatureRecord.bytes,
    publicKeyBytes: publicKeyRecord.bytes,
    pinDocument: parseJson(pinRecord.bytes, 'release authority pin'),
  });
  if (authority.errors.length > 0) throw new Error(authority.errors.join('; '));
  const loaded = loadProductionClosureReports(
    args.get('evidence-root'),
    document.components,
  );
  const androidRecord = readStableFile(
    args.get('android-artifact'),
    MAXIMUM_ANDROID_BYTES,
    'Android release artifact',
  );
  const deliveredBaseRecord = readStableFile(
    args.get('android-delivered-base'),
    MAXIMUM_ANDROID_BYTES,
    'Play-delivered base APK',
  );
  const installedApks = loadInstalledPlayApkArtifacts(
    args.get('android-installed-apk-root'),
    loaded.reports.androidPlayDelivery,
  );
  const iosRecord = readStableFile(
    args.get('ios-artifact'),
    MAXIMUM_IOS_BYTES,
    'iOS release artifact',
  );
  const hostingRecord = readStableFile(
    args.get('hosting-artifact'),
    MAXIMUM_HOSTING_BYTES,
    'Hosting release artifact',
  );
  const errors = validateProductionReleaseClosure(document, {
    nowMs: Date.now(),
    sourceRevision,
    sourceClean: true,
    authorityPublicKeySpkiSha256: authority.publicKeySpkiSha256,
    reports: loaded.reports,
    reportFiles: loaded.reportFiles,
    artifacts: {
      android: {
        sha256: sha256(androidRecord.bytes),
        fileName: path.basename(androidRecord.canonical),
      },
      androidDeliveredBase: {
        sha256: sha256(deliveredBaseRecord.bytes),
        fileName: path.basename(deliveredBaseRecord.canonical),
      },
      ios: {
        sha256: sha256(iosRecord.bytes),
        fileName: path.basename(iosRecord.canonical),
      },
      hosting: { sha256: sha256(hostingRecord.bytes) },
    },
  });
  if (errors.length > 0) {
    throw new Error(
      `production release closure rejected:\n- ${errors.join('\n- ')}`,
    );
  }
  for (const [record, label] of [
    [manifestRecord, 'closure manifest'],
    [signatureRecord, 'closure signature'],
    [publicKeyRecord, 'authority public key'],
    [pinRecord, 'authority pin'],
    ...loaded.records,
    [androidRecord, 'Android artifact'],
    [deliveredBaseRecord, 'Play-delivered base APK'],
    ...installedApks.records,
    [iosRecord, 'iOS artifact'],
    [hostingRecord, 'Hosting artifact'],
  ]) {
    assertUnchanged(record, label);
  }
  assertReportInventoryUnchanged(loaded);
  assertInstalledApkInventoryUnchanged(installedApks);
  const finalSource = inspectCleanGitSource(PROJECT_ROOT, pinRecord.bytes);
  if (
    finalSource.errors.length > 0 ||
    finalSource.sourceRevision !== sourceRevision
  ) {
    throw new Error(
      'source checkout changed during release closure validation',
    );
  }
  process.stdout.write(
    `PASS production release closure source=${sourceRevision} android=${document.release.android.artifactSha256} ios=${document.release.ios.artifactSha256}\n`,
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
