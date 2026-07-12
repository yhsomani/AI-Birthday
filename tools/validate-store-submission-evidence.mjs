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
  readSync,
  realpathSync,
} from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { inflateSync } from 'node:zlib';
import { parseReleaseConfig } from '../backend/hosting/tools/release-config.mjs';

const PROJECT_ROOT = fileURLToPath(new URL('../', import.meta.url));
const SHA256 = /^[0-9a-f]{64}$/u;
const REVISION = /^[0-9a-f]{40}$/u;
const SAFE_RELATIVE = /^[A-Za-z0-9][A-Za-z0-9 ._/@()+-]{0,511}$/u;
const EMAIL =
  /^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$/iu;
const PLACEHOLDER =
  /(?:^|[\s_./-])(?:required|todo|tbd|placeholder|replace|example|unknown|unprovisioned)(?:$|[\s_./-])|[<>]/iu;
const UTC_INSTANT =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/u;
const REQUIRED_LOCALES = Object.freeze(['en-US', 'hi-IN']);
const REQUIRED_ROLES = Object.freeze([
  'product',
  'engineering',
  'security',
  'privacy',
  'legal-policy',
  'accessibility-ux',
  'operations-support',
  'release',
]);
const REQUIRED_EVIDENCE_IDS = Object.freeze([
  'privacy-inventory',
  'ui-copy-inventory',
  'stitch-screen-manifest',
  'artifact-provenance',
  'screenshot-capture-record',
  'play-data-safety-review',
  'play-sms-policy-review',
  'app-privacy-review',
  'app-review-notes',
  'legal-country-review',
  'support-operations-review',
  'accessibility-review',
]);
const PLAY_DATA_TYPES = Object.freeze([
  'name',
  'emailAddress',
  'userIds',
  'phoneNumber',
  'contacts',
  'smsOrMms',
  'otherUserGeneratedContent',
  'appInteractions',
  'diagnostics',
  'deviceOrOtherIds',
]);
const APP_PRIVACY_TYPES = Object.freeze([
  'name',
  'emailAddress',
  'userId',
  'deviceId',
  'contacts',
  'emailsOrTextMessages',
  'otherUserContent',
  'productInteraction',
  'crashData',
  'performanceData',
  'otherDiagnosticData',
]);
const PLAY_SCREEN_IDS = Object.freeze(['S01', 'H01', 'P01', 'S15', 'T08']);
const APP_STORE_SCREEN_IDS = Object.freeze(['S01', 'H01', 'P01', 'H06', 'T08']);
const IPHONE_69_PORTRAIT = new Set(['1260x2736', '1290x2796', '1320x2868']);
const ALLOWED_PURPOSES = new Set([
  'app-functionality',
  'analytics',
  'developer-communications',
  'advertising-or-marketing',
  'fraud-prevention-security-compliance',
  'personalization',
  'account-management',
  'other',
]);
const TOP_LEVEL_KEYS = new Set([
  'schemaVersion',
  'packageStage',
  'sourceRevision',
  'approvalScopeSha256',
  'generatedAt',
  'validUntil',
  'releaseCoordinates',
  'launchCountries',
  'publicIdentity',
  'localizations',
  'assets',
  'play',
  'appStore',
  'accessibility',
  'evidenceReferences',
  'approvals',
]);
const CLI_KEYS = new Set([
  'file',
  'mode',
  'android-artifact',
  'ios-artifact',
  'asset-root',
  'evidence-root',
  'hosting-config',
  'print-scope',
  'print-digests',
]);
const keySet = values => new Set(values);
const COORDINATE_ROOT_KEYS = keySet(['android', 'ios']);
const ANDROID_COORDINATE_KEYS = keySet([
  'applicationId',
  'versionCode',
  'versionName',
  'artifactKind',
  'artifactFileName',
  'artifactSha256',
  'signingCertificateSha256',
]);
const IOS_COORDINATE_KEYS = keySet([
  'bundleId',
  'shortVersion',
  'buildNumber',
  'artifactKind',
  'artifactFileName',
  'artifactSha256',
  'distributionCertificateSha256',
]);
const PUBLIC_IDENTITY_KEYS = keySet([
  'developerDisplayName',
  'supportEmail',
  'publicSiteBaseUrl',
  'storeSupportUrl',
  'privacyUrl',
  'termsUrl',
  'deletionUrl',
  'identityVerifiedSupportUrl',
]);
const LOCALIZATION_KEYS = keySet([
  'status',
  'copySha256',
  'humanReviewReference',
  'humanReviewSha256',
  'play',
  'appStore',
]);
const PLAY_COPY_KEYS = keySet([
  'title',
  'shortDescription',
  'fullDescription',
  'releaseNotes',
]);
const APP_COPY_KEYS = keySet([
  'name',
  'subtitle',
  'promotionalText',
  'description',
  'keywords',
  'whatsNew',
]);
const IMAGE_ASSET_KEYS = keySet([
  'status',
  'file',
  'sha256',
  'bytes',
  'width',
  'height',
  'containsRealPersonalData',
  'approvedForStore',
]);
const SCREENSHOT_KEYS = keySet([
  'id',
  'status',
  'store',
  'locale',
  'platform',
  'deviceClass',
  'screenId',
  'variant',
  'file',
  'sha256',
  'bytes',
  'width',
  'height',
  'captureArtifactSha256',
  'containsRealPersonalData',
  'imitatesSystemUi',
  'approvedForStore',
  'altText',
]);
const DATA_BUNDLE_KEYS = keySet([
  'status',
  'consoleExportReference',
  'consoleExportSha256',
  'taxonomyReviewedAt',
  'allCurrentConsoleQuestionsAnswered',
  'sdkPracticesReviewed',
  'privacyPolicyConsistent',
  'answers',
]);
const DATA_ANSWER_KEYS = keySet([
  'dataType',
  'answer',
  'shared',
  'ephemeral',
  'required',
  'linkedToIdentity',
  'tracking',
  'purposes',
  'implementationNote',
]);
const REVIEW_ACCESS_KEYS = keySet([
  'requiresGoogleSignIn',
  'testAccountProvisioned',
  'credentialVaultReference',
  'credentialsEmbedded',
  'instructionsReference',
  'instructionsSha256',
]);
const REVIEW_PAIR_KEYS = keySet(['status', 'reference', 'sha256']);
const PLAY_KEYS = keySet([
  'dataSafety',
  'smsPermissions',
  'reviewAccess',
  'contentRating',
  'targetAudience',
]);
const SMS_PERMISSION_KEYS = keySet([
  'status',
  'permissions',
  'declaredCoreFunctionality',
  'unattendedPersonalBirthdaySmsOnly',
  'defaultSmsHandlerClaimed',
  'alternativeSmsIntentMeetsRequirement',
  'prominentDisclosureCovered',
  'carrierChargesDisclosed',
  'recipientAndContentPreapproved',
  'declarationReference',
  'declarationSha256',
  'demoVideoUrl',
  'demoVideoEvidenceReference',
  'demoVideoEvidenceSha256',
  'reviewerInstructionsReference',
  'reviewerInstructionsSha256',
  'policyDecision',
  'policyDecisionReference',
  'policyDecisionSha256',
]);
const APP_STORE_KEYS = keySet([
  'appPrivacy',
  'privacyManifest',
  'googleOnlyLoginRationale',
  'reviewNotes',
  'reviewAccess',
  'ageRating',
  'exportCompliance',
  'appReviewDecision',
]);
const PRIVACY_MANIFEST_KEYS = keySet([
  'status',
  'sourcePath',
  'sha256',
  'tracking',
  'trackingDomains',
  'requiredReasonApisReviewed',
  'mergedArchiveManifestReference',
  'mergedArchiveManifestSha256',
]);
const LOGIN_RATIONALE_KEYS = keySet([
  'status',
  'specificThirdPartyServiceClient',
  'requiredService',
  'oauthScope',
  'oneVisibleGoogleAccountChoice',
  'secondaryPrimaryLoginOffered',
  'userManagedTokens',
  'rationaleReference',
  'rationaleSha256',
  'reviewGuidelineReviewedAt',
  'appReviewDisposition',
  'appReviewReference',
  'appReviewSha256',
  'rejectionAction',
]);
const REVIEW_NOTES_KEYS = keySet([
  'status',
  'reference',
  'sha256',
  'messageUiForegroundUserActionOnly',
  'messageUiEditable',
  'userMustTapSend',
  'unattendedOrBackgroundSmsClaimed',
  'senderLineOrTransportKnown',
  'carrierDeliveryGuaranteed',
  'notificationsBestEffort',
  'notificationPermissionMayBeDenied',
  'focusOrSystemMayDelayOrSuppress',
  'carrierChargesDisclosed',
  'externalMessagesIcloudCarrierRecipientCopiesDisclosed',
  'accountDeletionCannotEraseExternalCopies',
]);
const APP_REVIEW_DECISION_KEYS = keySet(['disposition', 'reference', 'sha256']);
const ACCESSIBILITY_KEYS = keySet([
  'status',
  'evidenceReference',
  'evidenceSha256',
  'englishAndHindi',
  'talkBack',
  'voiceOver',
  'textAt200Percent',
  'dynamicTypeAccessibilitySizes',
  'darkMode',
  'increasedContrast',
  'reducedMotion',
  'bidiAndPseudoRtl',
  'storeAssetAltTextReviewed',
  'appStoreAccessibilityLabelsSubmitted',
]);
const EVIDENCE_REFERENCE_KEYS = keySet(['id', 'path', 'sha256']);
const APPROVAL_KEYS = keySet([
  'role',
  'status',
  'approver',
  'reference',
  'sha256',
  'scopeSha256',
  'approvedAt',
  'validUntil',
]);

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const codePointLength = value => Array.from(value).length;

const sha256 = value => createHash('sha256').update(value).digest('hex');

const canonicalize = value => {
  if (Array.isArray(value)) return value.map(canonicalize);
  if (!isObject(value)) return value;
  return Object.fromEntries(
    Object.keys(value)
      .sort((left, right) => left.localeCompare(right, 'en'))
      .map(key => [key, canonicalize(value[key])]),
  );
};

export function calculateApprovalScopeSha256(document) {
  if (!isObject(document))
    throw new Error('submission evidence must be an object');
  const {
    approvals: _approvals,
    approvalScopeSha256: _scope,
    ...scope
  } = document;
  return sha256(Buffer.from(JSON.stringify(canonicalize(scope)), 'utf8'));
}

export function calculateLocalizationSha256(localization) {
  if (!isObject(localization))
    throw new Error('localization must be an object');
  return sha256(
    Buffer.from(
      JSON.stringify(
        canonicalize({
          play: localization.play,
          appStore: localization.appStore,
        }),
      ),
      'utf8',
    ),
  );
}

const exactKeys = (value, allowed, label, errors) => {
  if (!isObject(value)) {
    errors.push(`${label} must be an object`);
    return false;
  }
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) errors.push(`${label} has unsupported field ${key}`);
  }
  for (const key of allowed) {
    if (!Object.hasOwn(value, key)) errors.push(`${label} is missing ${key}`);
  }
  return true;
};

const requiredString = (value, label, errors, maximum = 512) => {
  if (
    typeof value !== 'string' ||
    value !== value.trim() ||
    value.length === 0 ||
    codePointLength(value) > maximum ||
    PLACEHOLDER.test(value)
  ) {
    errors.push(
      `${label} must be a non-placeholder string of at most ${maximum} characters`,
    );
    return null;
  }
  return value;
};

const fixedString = (value, expected, label, errors) => {
  if (value !== expected) errors.push(`${label} must be exactly ${expected}`);
};

const requiredTrue = (value, label, errors) => {
  if (value !== true) errors.push(`${label} must be true`);
};

const requiredFalse = (value, label, errors) => {
  if (value !== false) errors.push(`${label} must be false`);
};

const requiredSha = (value, label, errors) => {
  if (typeof value !== 'string' || !SHA256.test(value)) {
    errors.push(`${label} must be a lowercase SHA-256 digest`);
    return null;
  }
  return value;
};

const parseInstant = (value, label, errors) => {
  if (typeof value !== 'string' || !UTC_INSTANT.test(value)) {
    errors.push(`${label} must be an RFC 3339 UTC instant`);
    return null;
  }
  const epoch = Date.parse(value);
  if (!Number.isFinite(epoch)) {
    errors.push(`${label} must be a real RFC 3339 UTC instant`);
    return null;
  }
  return epoch;
};

const requiredHttps = (value, label, errors) => {
  const text = requiredString(value, label, errors, 2048);
  if (text === null) return null;
  let url;
  try {
    url = new URL(text);
  } catch {
    errors.push(`${label} must be an absolute HTTPS URL`);
    return null;
  }
  const hostname = url.hostname.toLowerCase();
  if (
    url.protocol !== 'https:' ||
    url.username !== '' ||
    url.password !== '' ||
    url.hash !== '' ||
    [
      'localhost',
      '127.0.0.1',
      'example.com',
      'example.org',
      'example.net',
    ].includes(hostname) ||
    hostname.endsWith('.invalid') ||
    hostname.endsWith('.example') ||
    hostname.endsWith('.test') ||
    hostname.endsWith('.localhost')
  ) {
    errors.push(
      `${label} must be a provisioned, credential-free public HTTPS URL`,
    );
    return null;
  }
  return url;
};

const exactSet = (values, expected, label, errors) => {
  if (!Array.isArray(values)) {
    errors.push(`${label} must be an array`);
    return;
  }
  const actual = new Set(values);
  if (
    actual.size !== values.length ||
    actual.size !== expected.length ||
    expected.some(value => !actual.has(value))
  ) {
    errors.push(`${label} must contain exactly ${expected.join(', ')}`);
  }
};

const verifyTextLimit = (value, label, minimum, maximum, errors) => {
  if (
    typeof value !== 'string' ||
    value !== value.trim() ||
    codePointLength(value) < minimum ||
    codePointLength(value) > maximum
  ) {
    errors.push(`${label} must contain ${minimum}-${maximum} characters`);
  }
};

const requirePhrases = (value, phrases, label, errors) => {
  const lowered = typeof value === 'string' ? value.toLowerCase() : '';
  for (const phrase of phrases) {
    if (!lowered.includes(phrase.toLowerCase())) {
      errors.push(`${label} must disclose “${phrase}”`);
    }
  }
};

const safeRelative = (value, label, errors) => {
  const text = requiredString(value, label, errors);
  if (
    text === null ||
    path.isAbsolute(text) ||
    !SAFE_RELATIVE.test(text) ||
    text
      .split(/[\\/]/u)
      .some(part => part === '' || part === '.' || part === '..')
  ) {
    if (text !== null)
      errors.push(`${label} must be a normalized relative path`);
    return null;
  }
  return text;
};

const stableFile = (filePath, maximumBytes) => {
  const beforePath = lstatSync(filePath, { bigint: true });
  if (!beforePath.isFile() || beforePath.size <= 0n) {
    throw new Error(`${filePath} is not a non-empty regular file`);
  }
  if (beforePath.size > BigInt(maximumBytes)) {
    throw new Error(`${filePath} exceeds its evidence size limit`);
  }
  let descriptor;
  try {
    descriptor = openSync(
      filePath,
      // eslint-disable-next-line no-bitwise
      constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0),
    );
    const beforeRead = fstatSync(descriptor, { bigint: true });
    if (
      !beforeRead.isFile() ||
      beforeRead.dev !== beforePath.dev ||
      beforeRead.ino !== beforePath.ino ||
      beforeRead.size !== beforePath.size
    ) {
      throw new Error(`${filePath} changed before it could be read`);
    }
    const digest = createHash('sha256');
    const chunks = [];
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    let total = 0;
    while (true) {
      const count = readSync(descriptor, buffer, 0, buffer.byteLength, null);
      if (count === 0) break;
      const chunk = Buffer.from(buffer.subarray(0, count));
      digest.update(chunk);
      chunks.push(chunk);
      total += count;
    }
    const afterRead = fstatSync(descriptor, { bigint: true });
    const afterPath = lstatSync(filePath, { bigint: true });
    if (
      afterRead.dev !== beforeRead.dev ||
      afterRead.ino !== beforeRead.ino ||
      afterRead.size !== beforeRead.size ||
      afterRead.mtimeNs !== beforeRead.mtimeNs ||
      afterRead.ctimeNs !== beforeRead.ctimeNs ||
      afterPath.dev !== beforeRead.dev ||
      afterPath.ino !== beforeRead.ino ||
      afterPath.size !== beforeRead.size
    ) {
      throw new Error(`${filePath} changed while it was read`);
    }
    return {
      bytes: total,
      buffer: Buffer.concat(chunks, total),
      sha256: digest.digest('hex'),
    };
  } finally {
    if (descriptor !== undefined) closeSync(descriptor);
  }
};

const resolveEvidenceFile = (root, relative, maximumBytes) => {
  const realRoot = realpathSync(root);
  const segments = relative.split(/[\\/]/u);
  let cursor = realRoot;
  for (const segment of segments) {
    cursor = path.join(cursor, segment);
    const metadata = lstatSync(cursor);
    if (metadata.isSymbolicLink()) {
      throw new Error(`${relative} contains a symbolic link`);
    }
  }
  const realFile = realpathSync(cursor);
  const containment = path.relative(realRoot, realFile);
  if (containment.startsWith('..') || path.isAbsolute(containment)) {
    throw new Error(`${relative} escapes its evidence root`);
  }
  return stableFile(realFile, maximumBytes);
};

const CRC_TABLE = Object.freeze(
  Array.from({ length: 256 }, (_, value) => {
    let crc = value;
    for (let bit = 0; bit < 8; bit += 1) {
      // CRC-32 is unsigned polynomial arithmetic.
      // eslint-disable-next-line no-bitwise
      crc = (crc & 1) === 1 ? 0xedb88320 ^ (crc >>> 1) : crc >>> 1;
    }
    // eslint-disable-next-line no-bitwise
    return crc >>> 0;
  }),
);

const crc32 = bytes => {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    // eslint-disable-next-line no-bitwise
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  // eslint-disable-next-line no-bitwise
  return (crc ^ 0xffffffff) >>> 0;
};

const parsePng = buffer => {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  if (buffer.length < 57 || !buffer.subarray(0, 8).equals(signature))
    return null;
  let offset = 8;
  let header = null;
  let sawImageData = false;
  let sawEnd = false;
  const imageData = [];
  while (offset + 12 <= buffer.length) {
    const length = buffer.readUInt32BE(offset);
    const end = offset + 12 + length;
    if (end > buffer.length) throw new Error('PNG chunk length is malformed');
    const typeBytes = buffer.subarray(offset + 4, offset + 8);
    const type = typeBytes.toString('ascii');
    const data = buffer.subarray(offset + 8, offset + 8 + length);
    if (
      crc32(Buffer.concat([typeBytes, data])) !==
      buffer.readUInt32BE(offset + 8 + length)
    ) {
      throw new Error(`PNG ${type} checksum is invalid`);
    }
    if (offset === 8 && (type !== 'IHDR' || length !== 13)) {
      throw new Error('PNG has no leading IHDR chunk');
    }
    if (type === 'IHDR') {
      if (header !== null || length !== 13)
        throw new Error('PNG IHDR is duplicated or malformed');
      header = {
        width: data.readUInt32BE(0),
        height: data.readUInt32BE(4),
        bitDepth: data[8],
        colorType: data[9],
        compression: data[10],
        filter: data[11],
        interlace: data[12],
      };
    } else if (type === 'IDAT') {
      sawImageData = true;
      imageData.push(data);
    } else if (type === 'IEND') {
      if (length !== 0 || end !== buffer.length)
        throw new Error('PNG IEND is malformed or not final');
      sawEnd = true;
      offset = end;
      break;
    }
    offset = end;
  }
  if (header === null || !sawImageData || !sawEnd || offset !== buffer.length) {
    throw new Error('PNG is incomplete');
  }
  if (
    header.width <= 0 ||
    header.height <= 0 ||
    header.bitDepth !== 8 ||
    header.colorType !== 2 ||
    header.compression !== 0 ||
    header.filter !== 0 ||
    header.interlace !== 0
  ) {
    throw new Error('PNG must be non-interlaced 8-bit RGB');
  }
  const expectedInflatedBytes = header.height * (1 + header.width * 3);
  if (
    !Number.isSafeInteger(expectedInflatedBytes) ||
    expectedInflatedBytes > 64 * 1024 * 1024
  ) {
    throw new Error('PNG dimensions exceed the decoded-image safety limit');
  }
  let inflated;
  try {
    inflated = inflateSync(Buffer.concat(imageData), {
      maxOutputLength: expectedInflatedBytes,
    });
  } catch {
    throw new Error('PNG image data cannot be decoded safely');
  }
  if (inflated.length !== expectedInflatedBytes) {
    throw new Error('PNG decoded byte count does not match its dimensions');
  }
  return {
    format: 'png',
    width: header.width,
    height: header.height,
    bitDepth: header.bitDepth,
    colorType: header.colorType,
  };
};

const parseJpeg = buffer => {
  if (buffer.length < 4 || buffer[0] !== 0xff || buffer[1] !== 0xd8)
    return null;
  let offset = 2;
  while (offset + 4 <= buffer.length) {
    if (buffer[offset] !== 0xff)
      throw new Error('JPEG marker stream is malformed');
    while (buffer[offset] === 0xff) offset += 1;
    const marker = buffer[offset];
    offset += 1;
    if (marker === 0xd9 || marker === 0xda) break;
    if (marker >= 0xd0 && marker <= 0xd7) continue;
    if (offset + 2 > buffer.length) break;
    const length = buffer.readUInt16BE(offset);
    if (length < 2 || offset + length > buffer.length) {
      throw new Error('JPEG segment length is malformed');
    }
    const isStartOfFrame =
      (marker >= 0xc0 && marker <= 0xc3) ||
      (marker >= 0xc5 && marker <= 0xc7) ||
      (marker >= 0xc9 && marker <= 0xcb) ||
      (marker >= 0xcd && marker <= 0xcf);
    if (isStartOfFrame) {
      if (length < 8) throw new Error('JPEG frame is malformed');
      return {
        format: 'jpeg',
        height: buffer.readUInt16BE(offset + 3),
        width: buffer.readUInt16BE(offset + 5),
      };
    }
    offset += length;
  }
  throw new Error('JPEG dimensions are unavailable');
};

const imageMetadata = buffer => {
  const metadata = parsePng(buffer) ?? parseJpeg(buffer);
  if (metadata === null) throw new Error('asset must be a PNG or JPEG image');
  return metadata;
};

const verifyExternalFile = (
  root,
  reference,
  expectedSha,
  label,
  errors,
  maximumBytes = 64 * 1024 * 1024,
) => {
  const relative = safeRelative(reference, `${label} reference`, errors);
  const digest = requiredSha(expectedSha, `${label} sha256`, errors);
  if (relative === null || digest === null || root === undefined) return null;
  try {
    const actual = resolveEvidenceFile(root, relative, maximumBytes);
    if (actual.sha256 !== digest)
      errors.push(`${label} digest does not match ${relative}`);
    return actual;
  } catch (error) {
    errors.push(`${label} cannot be verified: ${error.message}`);
    return null;
  }
};

const validateReviewPair = (value, label, errors, context, mode) => {
  if (!exactKeys(value, REVIEW_PAIR_KEYS, label, errors)) return;
  if (mode === 'template') return;
  if (value.status !== 'approved')
    errors.push(`${label}.status must be approved`);
  verifyExternalFile(
    context.evidenceRoot,
    value.reference,
    value.sha256,
    label,
    errors,
  );
};

const validateCoordinate = (document, errors, context, mode) => {
  const coordinates = document.releaseCoordinates;
  if (
    !exactKeys(coordinates, COORDINATE_ROOT_KEYS, 'releaseCoordinates', errors)
  )
    return;
  const android = coordinates.android;
  const ios = coordinates.ios;
  const androidValid = exactKeys(
    android,
    ANDROID_COORDINATE_KEYS,
    'releaseCoordinates.android',
    errors,
  );
  const iosValid = exactKeys(
    ios,
    IOS_COORDINATE_KEYS,
    'releaseCoordinates.ios',
    errors,
  );
  if (!androidValid || !iosValid) return;
  fixedString(
    android.applicationId,
    'com.yashsomani.birthdayautopilot',
    'Android applicationId',
    errors,
  );
  if (android.versionCode !== 1)
    errors.push('Android versionCode must be exactly 1');
  fixedString(android.versionName, '1.0', 'Android versionName', errors);
  fixedString(android.artifactKind, 'aab', 'Android artifactKind', errors);
  fixedString(
    ios.bundleId,
    'com.yashsomani.birthdayautopilot',
    'iOS bundleId',
    errors,
  );
  fixedString(ios.shortVersion, '1.0', 'iOS shortVersion', errors);
  fixedString(ios.buildNumber, '1', 'iOS buildNumber', errors);
  fixedString(ios.artifactKind, 'ipa', 'iOS artifactKind', errors);
  if (mode === 'template') return;
  requiredString(
    android.artifactFileName,
    'Android artifactFileName',
    errors,
    255,
  );
  requiredString(ios.artifactFileName, 'iOS artifactFileName', errors, 255);
  requiredSha(
    android.signingCertificateSha256,
    'Android signing certificate',
    errors,
  );
  requiredSha(
    ios.distributionCertificateSha256,
    'iOS distribution certificate',
    errors,
  );
  for (const [platform, coordinate] of [
    ['android', android],
    ['ios', ios],
  ]) {
    const file = context.artifacts?.[platform];
    const expected = requiredSha(
      coordinate.artifactSha256,
      `${platform} artifact`,
      errors,
    );
    if (file === undefined || expected === null) continue;
    try {
      const actual = stableFile(file, 2 * 1024 * 1024 * 1024);
      if (actual.sha256 !== expected)
        errors.push(`${platform} artifact digest does not match`);
      if (path.basename(file) !== coordinate.artifactFileName) {
        errors.push(
          `${platform} artifact file name does not match the bound file`,
        );
      }
    } catch (error) {
      errors.push(`${platform} artifact cannot be verified: ${error.message}`);
    }
  }
};

const validatePublicIdentity = (identity, errors, context, mode) => {
  if (!exactKeys(identity, PUBLIC_IDENTITY_KEYS, 'publicIdentity', errors))
    return;
  if (mode === 'template') return;
  const developer = requiredString(
    identity.developerDisplayName,
    'developerDisplayName',
    errors,
    100,
  );
  const email = requiredString(
    identity.supportEmail,
    'supportEmail',
    errors,
    254,
  );
  if (email !== null && !EMAIL.test(email))
    errors.push('supportEmail must be a valid public email address');
  const base = requiredHttps(
    identity.publicSiteBaseUrl,
    'publicSiteBaseUrl',
    errors,
  );
  const support = requiredHttps(
    identity.storeSupportUrl,
    'storeSupportUrl',
    errors,
  );
  const privacy = requiredHttps(identity.privacyUrl, 'privacyUrl', errors);
  const terms = requiredHttps(identity.termsUrl, 'termsUrl', errors);
  const deletion = requiredHttps(identity.deletionUrl, 'deletionUrl', errors);
  const identitySupport = requiredHttps(
    identity.identityVerifiedSupportUrl,
    'identityVerifiedSupportUrl',
    errors,
  );
  if (base !== null) {
    if (base.pathname !== '/' || base.search !== '')
      errors.push('publicSiteBaseUrl must be an HTTPS origin');
    const expected = suffix => new URL(suffix, base).toString();
    if (support?.toString() !== expected('/support/'))
      errors.push('storeSupportUrl must be publicSiteBaseUrl + /support/');
    if (privacy?.toString() !== expected('/privacy/'))
      errors.push('privacyUrl must be publicSiteBaseUrl + /privacy/');
    if (terms?.toString() !== expected('/terms/'))
      errors.push('termsUrl must be publicSiteBaseUrl + /terms/');
    if (deletion?.toString() !== expected('/delete/'))
      errors.push('deletionUrl must be publicSiteBaseUrl + /delete/');
  }
  const hosting = context.hosting;
  if (hosting !== undefined) {
    if (base?.toString() !== hosting.publicBaseUrl)
      errors.push('publicSiteBaseUrl does not match approved Hosting config');
    if (developer !== hosting.developerDisplayName)
      errors.push(
        'developerDisplayName does not match approved Hosting config',
      );
    if (identitySupport?.toString() !== hosting.supportUrl)
      errors.push(
        'identityVerifiedSupportUrl does not match approved Hosting config',
      );
  }
};

const validateLocalizations = (localizations, errors, context, mode) => {
  if (!isObject(localizations)) {
    errors.push('localizations must be an object');
    return;
  }
  exactSet(
    Object.keys(localizations),
    REQUIRED_LOCALES,
    'localization keys',
    errors,
  );
  for (const locale of REQUIRED_LOCALES) {
    const value = localizations[locale];
    if (
      !isObject(value) ||
      !isObject(value.play) ||
      !isObject(value.appStore)
    ) {
      errors.push(`${locale} localization is incomplete`);
      continue;
    }
    exactKeys(value, LOCALIZATION_KEYS, `${locale} localization`, errors);
    exactKeys(value.play, PLAY_COPY_KEYS, `${locale} Play copy`, errors);
    exactKeys(
      value.appStore,
      APP_COPY_KEYS,
      `${locale} App Store copy`,
      errors,
    );
    verifyTextLimit(value.play.title, `${locale} Play title`, 1, 30, errors);
    verifyTextLimit(
      value.play.shortDescription,
      `${locale} Play short description`,
      1,
      80,
      errors,
    );
    verifyTextLimit(
      value.play.fullDescription,
      `${locale} Play full description`,
      1,
      4000,
      errors,
    );
    verifyTextLimit(
      value.play.releaseNotes,
      `${locale} Play release notes`,
      1,
      500,
      errors,
    );
    verifyTextLimit(
      value.appStore.name,
      `${locale} App Store name`,
      2,
      30,
      errors,
    );
    verifyTextLimit(
      value.appStore.subtitle,
      `${locale} App Store subtitle`,
      1,
      30,
      errors,
    );
    verifyTextLimit(
      value.appStore.promotionalText,
      `${locale} App Store promotional text`,
      1,
      170,
      errors,
    );
    verifyTextLimit(
      value.appStore.description,
      `${locale} App Store description`,
      1,
      4000,
      errors,
    );
    verifyTextLimit(
      value.appStore.keywords,
      `${locale} App Store keywords`,
      1,
      100,
      errors,
    );
    verifyTextLimit(
      value.appStore.whatsNew,
      `${locale} App Store what's new`,
      1,
      4000,
      errors,
    );
    if (mode !== 'template') {
      if (value.status !== 'approved')
        errors.push(`${locale} localization must be approved`);
      const expected = calculateLocalizationSha256(value);
      if (value.copySha256 !== expected)
        errors.push(
          `${locale} copySha256 does not bind the exact localized copy`,
        );
      verifyExternalFile(
        context.evidenceRoot,
        value.humanReviewReference,
        value.humanReviewSha256,
        `${locale} human copy review`,
        errors,
      );
    }
  }
  const english = localizations['en-US'];
  if (isObject(english)) {
    requirePhrases(
      english.play?.fullDescription,
      [
        'Carrier SMS charges may apply',
        'best effort',
        'never treats submission as guaranteed delivery',
        'one Google account',
        'read-only Google Contacts',
        'Gemini never selects recipients',
        'SMS Provider, carrier, recipient, and backup copies',
      ],
      'English Play description',
      errors,
    );
    requirePhrases(
      english.appStore?.description,
      [
        'does not send SMS automatically or in the background',
        'tap Send',
        'Carrier messaging charges may apply',
        'best-effort local reminder',
        'Messages, iCloud, carrier, recipient, and backup copies',
      ],
      'English App Store description',
      errors,
    );
    if (
      /\b(?:best|top|#1|free|sale|discount)\s+(?:app|birthday|reminder)/iu.test(
        JSON.stringify(english),
      )
    ) {
      errors.push(
        'English metadata contains an unapproved ranking or promotional claim',
      );
    }
  }
  const hindi = localizations['hi-IN'];
  const hindiIos = `${hindi?.appStore?.description ?? ''} ${
    hindi?.appStore?.whatsNew ?? ''
  }`;
  if (!hindiIos.includes('अपने-आप') || !hindiIos.includes('नहीं')) {
    errors.push(
      'Hindi App Store copy must explicitly say iPhone does not send automatically',
    );
  }
  if (!hindiIos.includes('शुल्क') || !hindiIos.includes('iCloud')) {
    errors.push(
      'Hindi App Store copy must disclose carrier cost and external iCloud copies',
    );
  }
};

const validateImageRecord = (
  record,
  label,
  errors,
  context,
  mode,
  expected,
) => {
  if (!isObject(record)) {
    errors.push(`${label} must be an object`);
    return null;
  }
  if (mode === 'template') {
    if (record.status !== 'missing')
      errors.push(`${label} template status must be missing`);
    return null;
  }
  if (record.status !== 'captured')
    errors.push(`${label}.status must be captured`);
  requiredFalse(
    record.containsRealPersonalData,
    `${label}.containsRealPersonalData`,
    errors,
  );
  requiredTrue(record.approvedForStore, `${label}.approvedForStore`, errors);
  const relative = safeRelative(record.file, `${label}.file`, errors);
  const expectedDigest = requiredSha(record.sha256, `${label}.sha256`, errors);
  if (
    relative === null ||
    expectedDigest === null ||
    context.assetRoot === undefined
  )
    return null;
  try {
    const file = resolveEvidenceFile(
      context.assetRoot,
      relative,
      20 * 1024 * 1024,
    );
    const metadata = imageMetadata(file.buffer);
    if (file.sha256 !== expectedDigest)
      errors.push(`${label} digest does not match its image file`);
    if (file.bytes !== record.bytes)
      errors.push(`${label} byte count does not match its image file`);
    if (metadata.width !== record.width || metadata.height !== record.height) {
      errors.push(`${label} dimensions do not match its image file`);
    }
    if (expected?.width !== undefined && metadata.width !== expected.width)
      errors.push(`${label} must be ${expected.width}px wide`);
    if (expected?.height !== undefined && metadata.height !== expected.height)
      errors.push(`${label} must be ${expected.height}px high`);
    if (
      expected?.opaquePlay === true &&
      metadata.format === 'png' &&
      !(metadata.bitDepth === 8 && metadata.colorType === 2)
    ) {
      errors.push(`${label} Play PNG must be opaque 24-bit RGB`);
    }
    return metadata;
  } catch (error) {
    errors.push(`${label} cannot be verified: ${error.message}`);
    return null;
  }
};

const validateScreenshotSet = (
  screenshots,
  store,
  expectedScreenIds,
  errors,
  context,
  mode,
  artifactSha,
) => {
  if (!Array.isArray(screenshots)) {
    errors.push(`${store} screenshots must be an array`);
    return;
  }
  const ids = new Set();
  const files = new Set();
  const digests = new Set();
  const dimensions = new Set();
  for (const [index, shot] of screenshots.entries()) {
    const label = `${store} screenshot ${index + 1}`;
    if (!exactKeys(shot, SCREENSHOT_KEYS, label, errors)) continue;
    if (ids.has(shot.id))
      errors.push(`${store} screenshot id ${shot.id} is duplicated`);
    ids.add(shot.id);
    if (mode !== 'template') {
      if (files.has(shot.file))
        errors.push(`${store} screenshot file ${shot.file} is duplicated`);
      if (digests.has(shot.sha256))
        errors.push(`${store} screenshot image digest is duplicated`);
      files.add(shot.file);
      digests.add(shot.sha256);
    }
    if (shot.store !== store) errors.push(`${label}.store is incorrect`);
    if (!REQUIRED_LOCALES.includes(shot.locale))
      errors.push(`${label}.locale is unsupported`);
    if (!context.stitchIds?.has(shot.screenId))
      errors.push(`${label}.screenId is absent from the Stitch manifest`);
    verifyTextLimit(shot.altText, `${label}.altText`, 1, 140, errors);
    if (mode !== 'template') {
      requiredFalse(shot.imitatesSystemUi, `${label}.imitatesSystemUi`, errors);
      if (shot.captureArtifactSha256 !== artifactSha)
        errors.push(`${label} is not bound to the exact store artifact`);
    }
    const metadata = validateImageRecord(shot, label, errors, context, mode, {
      opaquePlay: store === 'play',
    });
    if (metadata !== null) {
      dimensions.add(`${metadata.width}x${metadata.height}`);
      if (store === 'play') {
        const min = Math.min(metadata.width, metadata.height);
        const max = Math.max(metadata.width, metadata.height);
        if (min < 1080 || max * 9 !== min * 16) {
          errors.push(
            `${label} must use the recommended 9:16 portrait size at 1080px or greater`,
          );
        }
      } else if (
        !IPHONE_69_PORTRAIT.has(`${metadata.width}x${metadata.height}`)
      ) {
        errors.push(
          `${label} is not an accepted iPhone 6.9-inch portrait size`,
        );
      }
    }
  }
  for (const locale of REQUIRED_LOCALES) {
    const localized = screenshots.filter(shot => shot?.locale === locale);
    if (localized.length !== 5)
      errors.push(`${store} must have exactly five ${locale} screenshots`);
    exactSet(
      localized.map(shot => shot.screenId),
      expectedScreenIds,
      `${store} ${locale} screen coverage`,
      errors,
    );
  }
  if (screenshots.length !== 10)
    errors.push(
      `${store} screenshot inventory must contain exactly ten localized assets`,
    );
  if (mode !== 'template' && store === 'app-store' && dimensions.size !== 1) {
    errors.push(
      'App Store screenshots must use one identical accepted size across localizations',
    );
  }
};

const validateAssets = (assets, errors, context, mode, document) => {
  if (
    !exactKeys(
      assets,
      keySet([
        'playFeatureGraphic',
        'playPhoneScreenshots',
        'appStoreIphoneScreenshots',
      ]),
      'assets',
      errors,
    )
  )
    return;
  exactKeys(
    assets.playFeatureGraphic,
    IMAGE_ASSET_KEYS,
    'Play feature graphic',
    errors,
  );
  validateImageRecord(
    assets.playFeatureGraphic,
    'Play feature graphic',
    errors,
    context,
    mode,
    {
      width: 1024,
      height: 500,
      opaquePlay: true,
    },
  );
  validateScreenshotSet(
    assets.playPhoneScreenshots,
    'play',
    PLAY_SCREEN_IDS,
    errors,
    context,
    mode,
    document.releaseCoordinates?.android?.artifactSha256,
  );
  validateScreenshotSet(
    assets.appStoreIphoneScreenshots,
    'app-store',
    APP_STORE_SCREEN_IDS,
    errors,
    context,
    mode,
    document.releaseCoordinates?.ios?.artifactSha256,
  );
};

const validateDataBundle = (
  bundle,
  label,
  requiredTypes,
  errors,
  context,
  mode,
) => {
  if (!exactKeys(bundle, DATA_BUNDLE_KEYS, label, errors)) return;
  if (!Array.isArray(bundle.answers)) {
    errors.push(`${label} must contain answers`);
    return;
  }
  exactSet(
    bundle.answers.map(answer => answer?.dataType),
    requiredTypes,
    `${label} data types`,
    errors,
  );
  if (mode !== 'template') {
    if (bundle.status !== 'approved')
      errors.push(`${label}.status must be approved`);
    requiredTrue(
      bundle.allCurrentConsoleQuestionsAnswered,
      `${label}.allCurrentConsoleQuestionsAnswered`,
      errors,
    );
    requiredTrue(
      bundle.sdkPracticesReviewed,
      `${label}.sdkPracticesReviewed`,
      errors,
    );
    requiredTrue(
      bundle.privacyPolicyConsistent,
      `${label}.privacyPolicyConsistent`,
      errors,
    );
    parseInstant(
      bundle.taxonomyReviewedAt,
      `${label}.taxonomyReviewedAt`,
      errors,
    );
    verifyExternalFile(
      context.evidenceRoot,
      bundle.consoleExportReference,
      bundle.consoleExportSha256,
      `${label} console export`,
      errors,
    );
  }
  for (const answer of bundle.answers) {
    if (!exactKeys(answer, DATA_ANSWER_KEYS, `${label} answer`, errors))
      continue;
    verifyTextLimit(
      answer.implementationNote,
      `${label}.${answer.dataType}.implementationNote`,
      1,
      500,
      errors,
    );
    if (
      !Array.isArray(answer.purposes) ||
      new Set(answer.purposes).size !== answer.purposes.length ||
      answer.purposes.some(item => !ALLOWED_PURPOSES.has(item))
    ) {
      errors.push(`${label}.${answer.dataType}.purposes is invalid`);
    }
    if (mode === 'template') {
      if (answer.answer !== 'pending')
        errors.push(
          `${label}.${answer.dataType} template answer must remain pending`,
        );
      continue;
    }
    if (!['not-collected', 'collected'].includes(answer.answer))
      errors.push(`${label}.${answer.dataType} must have a final answer`);
    requiredFalse(
      answer.tracking,
      `${label}.${answer.dataType}.tracking`,
      errors,
    );
    if (answer.answer === 'not-collected') {
      for (const field of [
        'shared',
        'ephemeral',
        'required',
        'linkedToIdentity',
      ])
        requiredFalse(
          answer[field],
          `${label}.${answer.dataType}.${field}`,
          errors,
        );
      if (answer.purposes.length !== 0)
        errors.push(
          `${label}.${answer.dataType} not-collected answer cannot have purposes`,
        );
    } else if (answer.answer === 'collected') {
      for (const field of [
        'shared',
        'ephemeral',
        'required',
        'linkedToIdentity',
      ]) {
        if (typeof answer[field] !== 'boolean')
          errors.push(`${label}.${answer.dataType}.${field} must be boolean`);
      }
      if (answer.purposes.length === 0)
        errors.push(
          `${label}.${answer.dataType} collected answer requires a purpose`,
        );
    }
  }
};

const validateReviewAccess = (access, label, errors, context, mode) => {
  if (!exactKeys(access, REVIEW_ACCESS_KEYS, label, errors)) return;
  requiredTrue(
    access.requiresGoogleSignIn,
    `${label}.requiresGoogleSignIn`,
    errors,
  );
  requiredFalse(
    access.credentialsEmbedded,
    `${label}.credentialsEmbedded`,
    errors,
  );
  if (mode === 'template') return;
  requiredTrue(
    access.testAccountProvisioned,
    `${label}.testAccountProvisioned`,
    errors,
  );
  requiredString(
    access.credentialVaultReference,
    `${label}.credentialVaultReference`,
    errors,
  );
  verifyExternalFile(
    context.evidenceRoot,
    access.instructionsReference,
    access.instructionsSha256,
    `${label} instructions`,
    errors,
  );
};

const validatePlay = (play, errors, context, mode) => {
  if (!exactKeys(play, PLAY_KEYS, 'play', errors)) return;
  validateDataBundle(
    play.dataSafety,
    'Play Data Safety',
    PLAY_DATA_TYPES,
    errors,
    context,
    mode,
  );
  validateReviewAccess(
    play.reviewAccess,
    'Play reviewer access',
    errors,
    context,
    mode,
  );
  validateReviewPair(
    play.contentRating,
    'Play content rating',
    errors,
    context,
    mode,
  );
  validateReviewPair(
    play.targetAudience,
    'Play target audience',
    errors,
    context,
    mode,
  );
  const sms = play.smsPermissions;
  if (
    !exactKeys(
      sms,
      SMS_PERMISSION_KEYS,
      'Play SMS permissions declaration',
      errors,
    )
  )
    return;
  exactSet(
    sms.permissions,
    ['android.permission.SEND_SMS', 'android.permission.READ_PHONE_STATE'],
    'Play restricted permissions',
    errors,
  );
  fixedString(
    sms.declaredCoreFunctionality,
    'device-automation',
    'Play SMS core functionality',
    errors,
  );
  requiredFalse(
    sms.defaultSmsHandlerClaimed,
    'Play default SMS handler claim',
    errors,
  );
  requiredFalse(
    sms.alternativeSmsIntentMeetsRequirement,
    'Play SMS Intent equivalence claim',
    errors,
  );
  if (mode === 'template') return;
  if (sms.status !== 'approved')
    errors.push('Play SMS declaration package must be approved internally');
  for (const field of [
    'unattendedPersonalBirthdaySmsOnly',
    'prominentDisclosureCovered',
    'carrierChargesDisclosed',
    'recipientAndContentPreapproved',
  ])
    requiredTrue(sms[field], `play.smsPermissions.${field}`, errors);
  verifyExternalFile(
    context.evidenceRoot,
    sms.declarationReference,
    sms.declarationSha256,
    'Play SMS declaration',
    errors,
  );
  requiredHttps(sms.demoVideoUrl, 'Play SMS demoVideoUrl', errors);
  verifyExternalFile(
    context.evidenceRoot,
    sms.demoVideoEvidenceReference,
    sms.demoVideoEvidenceSha256,
    'Play SMS demo video evidence',
    errors,
    512 * 1024 * 1024,
  );
  verifyExternalFile(
    context.evidenceRoot,
    sms.reviewerInstructionsReference,
    sms.reviewerInstructionsSha256,
    'Play SMS reviewer instructions',
    errors,
  );
  if (!['pending', 'approved'].includes(sms.policyDecision))
    errors.push(
      'Play SMS policy decision must be pending or approved for submission',
    );
  if (mode === 'release') {
    if (sms.policyDecision !== 'approved')
      errors.push(
        'Play SMS policy decision must be approved for public release',
      );
    verifyExternalFile(
      context.evidenceRoot,
      sms.policyDecisionReference,
      sms.policyDecisionSha256,
      'Play SMS policy decision',
      errors,
    );
  }
};

const validateAppStore = (appStore, errors, context, mode) => {
  if (!exactKeys(appStore, APP_STORE_KEYS, 'appStore', errors)) return;
  validateDataBundle(
    appStore.appPrivacy,
    'App Privacy',
    APP_PRIVACY_TYPES,
    errors,
    context,
    mode,
  );
  validateReviewAccess(
    appStore.reviewAccess,
    'App Store reviewer access',
    errors,
    context,
    mode,
  );
  validateReviewPair(
    appStore.ageRating,
    'App Store age rating',
    errors,
    context,
    mode,
  );
  validateReviewPair(
    appStore.exportCompliance,
    'App Store export compliance',
    errors,
    context,
    mode,
  );
  const manifest = appStore.privacyManifest;
  if (
    exactKeys(
      manifest,
      PRIVACY_MANIFEST_KEYS,
      'App Store privacy manifest evidence',
      errors,
    )
  ) {
    fixedString(
      manifest.sourcePath,
      'ios/BirthdayAutopilot/PrivacyInfo.xcprivacy',
      'privacy manifest sourcePath',
      errors,
    );
    requiredFalse(manifest.tracking, 'privacy manifest tracking', errors);
    if (
      !Array.isArray(manifest.trackingDomains) ||
      manifest.trackingDomains.length !== 0
    )
      errors.push('privacy manifest trackingDomains must be empty');
    if (mode !== 'template') {
      if (manifest.status !== 'approved')
        errors.push('privacy manifest must be approved');
      requiredTrue(
        manifest.requiredReasonApisReviewed,
        'privacy manifest requiredReasonApisReviewed',
        errors,
      );
      const source =
        context.projectRoot === undefined
          ? null
          : path.join(context.projectRoot, manifest.sourcePath);
      if (source !== null) {
        try {
          const actual = stableFile(source, 1024 * 1024);
          if (actual.sha256 !== manifest.sha256)
            errors.push('privacy manifest source digest does not match');
        } catch (error) {
          errors.push(
            `privacy manifest source cannot be verified: ${error.message}`,
          );
        }
      }
      verifyExternalFile(
        context.evidenceRoot,
        manifest.mergedArchiveManifestReference,
        manifest.mergedArchiveManifestSha256,
        'merged archive privacy manifest',
        errors,
      );
    }
  }
  const login = appStore.googleOnlyLoginRationale;
  if (
    exactKeys(
      login,
      LOGIN_RATIONALE_KEYS,
      'Google-only login rationale',
      errors,
    )
  ) {
    requiredTrue(
      login.specificThirdPartyServiceClient,
      'Google-only specific service client',
      errors,
    );
    fixedString(
      login.requiredService,
      'Google Contacts',
      'Google-only required service',
      errors,
    );
    fixedString(
      login.oauthScope,
      'https://www.googleapis.com/auth/contacts.readonly',
      'Google-only OAuth scope',
      errors,
    );
    requiredTrue(
      login.oneVisibleGoogleAccountChoice,
      'one visible Google account choice',
      errors,
    );
    requiredFalse(
      login.secondaryPrimaryLoginOffered,
      'secondary primary login offered',
      errors,
    );
    requiredFalse(login.userManagedTokens, 'user-managed token claim', errors);
    fixedString(
      login.rejectionAction,
      'block-ios-release-and-open-identity-change-control',
      'Google-only rejection action',
      errors,
    );
    if (mode !== 'template') {
      if (login.status !== 'approved')
        errors.push('Google-only login rationale must be internally approved');
      parseInstant(
        login.reviewGuidelineReviewedAt,
        'login guideline review date',
        errors,
      );
      verifyExternalFile(
        context.evidenceRoot,
        login.rationaleReference,
        login.rationaleSha256,
        'Google-only login rationale',
        errors,
      );
      if (!['pending', 'accepted'].includes(login.appReviewDisposition))
        errors.push(
          'App Review login disposition must be pending or accepted for submission',
        );
      if (mode === 'release') {
        if (login.appReviewDisposition !== 'accepted')
          errors.push(
            'App Review must accept the Google-only login rationale before release',
          );
        verifyExternalFile(
          context.evidenceRoot,
          login.appReviewReference,
          login.appReviewSha256,
          'App Review login decision',
          errors,
        );
      }
    }
  }
  const notes = appStore.reviewNotes;
  if (exactKeys(notes, REVIEW_NOTES_KEYS, 'App Store review notes', errors)) {
    requiredFalse(
      notes.unattendedOrBackgroundSmsClaimed,
      'iOS unattended/background SMS claim',
      errors,
    );
    requiredFalse(
      notes.senderLineOrTransportKnown,
      'iOS sender-line/transport claim',
      errors,
    );
    requiredFalse(
      notes.carrierDeliveryGuaranteed,
      'iOS carrier-delivery claim',
      errors,
    );
    if (mode !== 'template') {
      if (notes.status !== 'approved')
        errors.push('App Store review notes must be approved');
      for (const field of [
        'messageUiForegroundUserActionOnly',
        'messageUiEditable',
        'userMustTapSend',
        'notificationsBestEffort',
        'notificationPermissionMayBeDenied',
        'focusOrSystemMayDelayOrSuppress',
        'carrierChargesDisclosed',
        'externalMessagesIcloudCarrierRecipientCopiesDisclosed',
        'accountDeletionCannotEraseExternalCopies',
      ])
        requiredTrue(notes[field], `appStore.reviewNotes.${field}`, errors);
      verifyExternalFile(
        context.evidenceRoot,
        notes.reference,
        notes.sha256,
        'App Store review notes',
        errors,
      );
    }
  }
  const decision = appStore.appReviewDecision;
  if (
    exactKeys(
      decision,
      APP_REVIEW_DECISION_KEYS,
      'App Store review decision',
      errors,
    ) &&
    mode !== 'template'
  ) {
    if (!['pending', 'accepted'].includes(decision.disposition))
      errors.push(
        'App Store review decision must be pending or accepted for submission',
      );
    if (mode === 'release') {
      if (decision.disposition !== 'accepted')
        errors.push(
          'App Store review decision must be accepted before public release',
        );
      verifyExternalFile(
        context.evidenceRoot,
        decision.reference,
        decision.sha256,
        'App Store review decision',
        errors,
      );
    }
  }
};

const validateAccessibility = (value, errors, context, mode) => {
  if (!exactKeys(value, ACCESSIBILITY_KEYS, 'accessibility', errors)) return;
  if (mode === 'template') return;
  if (value.status !== 'approved')
    errors.push('accessibility evidence must be approved');
  for (const field of [
    'englishAndHindi',
    'talkBack',
    'voiceOver',
    'textAt200Percent',
    'dynamicTypeAccessibilitySizes',
    'darkMode',
    'increasedContrast',
    'reducedMotion',
    'bidiAndPseudoRtl',
    'storeAssetAltTextReviewed',
    'appStoreAccessibilityLabelsSubmitted',
  ])
    requiredTrue(value[field], `accessibility.${field}`, errors);
  verifyExternalFile(
    context.evidenceRoot,
    value.evidenceReference,
    value.evidenceSha256,
    'accessibility evidence',
    errors,
  );
};

const validateEvidenceReferences = (references, errors, context, mode) => {
  if (!Array.isArray(references)) {
    errors.push('evidenceReferences must be an array');
    return;
  }
  exactSet(
    references.map(reference => reference?.id),
    REQUIRED_EVIDENCE_IDS,
    'evidence reference IDs',
    errors,
  );
  for (const reference of references) {
    if (
      !exactKeys(
        reference,
        EVIDENCE_REFERENCE_KEYS,
        'evidence reference',
        errors,
      )
    )
      continue;
    if (mode === 'template') continue;
    verifyExternalFile(
      context.evidenceRoot,
      reference.path,
      reference.sha256,
      `evidence ${reference.id}`,
      errors,
    );
  }
};

const validateApprovals = (approvals, document, errors, context, mode, now) => {
  if (!Array.isArray(approvals)) {
    errors.push('approvals must be an array');
    return;
  }
  exactSet(
    approvals.map(approval => approval?.role),
    REQUIRED_ROLES,
    'approval roles',
    errors,
  );
  for (const approval of approvals) {
    exactKeys(approval, APPROVAL_KEYS, 'approval', errors);
  }
  if (mode === 'template') {
    for (const approval of approvals)
      if (approval?.status !== 'pending')
        errors.push('template approvals must remain pending');
    return;
  }
  const scope = calculateApprovalScopeSha256(document);
  if (document.approvalScopeSha256 !== scope)
    errors.push('approvalScopeSha256 does not bind the exact submission scope');
  for (const approval of approvals) {
    if (!exactKeys(approval, APPROVAL_KEYS, 'approval', errors)) continue;
    if (approval.status !== 'approved')
      errors.push(`${approval.role} approval must be approved`);
    requiredString(approval.approver, `${approval.role} approver`, errors, 200);
    if (approval.scopeSha256 !== scope)
      errors.push(`${approval.role} approval is not bound to the exact scope`);
    const approvedAt = parseInstant(
      approval.approvedAt,
      `${approval.role}.approvedAt`,
      errors,
    );
    const validUntil = parseInstant(
      approval.validUntil,
      `${approval.role}.validUntil`,
      errors,
    );
    if (
      approvedAt !== null &&
      validUntil !== null &&
      !(approvedAt < validUntil && validUntil > now)
    )
      errors.push(`${approval.role} approval is expired or temporally invalid`);
    verifyExternalFile(
      context.evidenceRoot,
      approval.reference,
      approval.sha256,
      `${approval.role} approval`,
      errors,
    );
  }
};

const scanForEmbeddedSecrets = (value, errors) => {
  const serialized = JSON.stringify(value);
  const patterns = [
    /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/u,
    /AIza[0-9A-Za-z_-]{20,}/u,
    /ya29\.[0-9A-Za-z_-]{20,}/u,
    /(?:password|accessToken|refreshToken|clientSecret)"\s*:/iu,
  ];
  if (patterns.some(pattern => pattern.test(serialized))) {
    errors.push(
      'submission evidence contains a credential or secret-shaped value',
    );
  }
};

export function validateStoreSubmissionEvidence(
  document,
  { mode = 'template', now = Date.now(), ...context } = {},
) {
  const errors = [];
  if (!['template', 'submission', 'release'].includes(mode)) {
    return {
      errors: ['mode must be template, submission, or release'],
      scopeSha256: null,
    };
  }
  if (!exactKeys(document, TOP_LEVEL_KEYS, 'submission evidence', errors)) {
    return { errors, scopeSha256: null };
  }
  if (document.schemaVersion !== 1)
    errors.push('schemaVersion must be exactly 1');
  const expectedStage = mode === 'template' ? 'draft' : mode;
  if (document.packageStage !== expectedStage)
    errors.push(`packageStage must be ${expectedStage} in ${mode} mode`);
  if (mode === 'template') {
    for (const field of [
      'sourceRevision',
      'approvalScopeSha256',
      'generatedAt',
      'validUntil',
    ]) {
      if (document[field] !== null)
        errors.push(`template ${field} must remain null`);
    }
    if (
      !Array.isArray(document.launchCountries) ||
      document.launchCountries.length !== 0
    )
      errors.push('template launchCountries must remain empty');
  } else {
    if (!REVISION.test(document.sourceRevision ?? ''))
      errors.push(
        'sourceRevision must be a lowercase 40-character Git revision',
      );
    if (
      context.currentSourceRevision !== undefined &&
      document.sourceRevision !== context.currentSourceRevision
    )
      errors.push(
        'sourceRevision does not match the checked-out release source',
      );
    const generatedAt = parseInstant(
      document.generatedAt,
      'generatedAt',
      errors,
    );
    const validUntil = parseInstant(document.validUntil, 'validUntil', errors);
    if (
      generatedAt !== null &&
      validUntil !== null &&
      !(
        generatedAt <= now &&
        validUntil > now &&
        validUntil - generatedAt <= 90 * 86_400_000
      )
    )
      errors.push(
        'submission package must be current and expire within 90 days',
      );
    if (
      !Array.isArray(document.launchCountries) ||
      document.launchCountries.length === 0 ||
      new Set(document.launchCountries).size !==
        document.launchCountries.length ||
      document.launchCountries.some(country => !/^[A-Z]{2}$/u.test(country))
    )
      errors.push(
        'launchCountries must contain unique ISO 3166-1 alpha-2 country codes',
      );
  }
  scanForEmbeddedSecrets(document, errors);
  validateCoordinate(document, errors, context, mode);
  validatePublicIdentity(document.publicIdentity, errors, context, mode);
  validateLocalizations(document.localizations, errors, context, mode);
  validateAssets(document.assets, errors, context, mode, document);
  validatePlay(document.play, errors, context, mode);
  validateAppStore(document.appStore, errors, context, mode);
  validateAccessibility(document.accessibility, errors, context, mode);
  validateEvidenceReferences(
    document.evidenceReferences,
    errors,
    context,
    mode,
  );
  validateApprovals(document.approvals, document, errors, context, mode, now);
  return {
    errors,
    scopeSha256: calculateApprovalScopeSha256(document),
  };
}

const parseArguments = argv => {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 1) {
    const flag = argv[index];
    if (!flag?.startsWith('--'))
      throw new Error('arguments must use --name value');
    const name = flag.slice(2);
    if (!CLI_KEYS.has(name)) throw new Error(`unsupported argument ${flag}`);
    if (values.has(name)) throw new Error(`duplicate argument ${flag}`);
    if (name === 'print-scope' || name === 'print-digests') {
      values.set(name, true);
      continue;
    }
    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--'))
      throw new Error(`${flag} requires a value`);
    values.set(name, value);
    index += 1;
  }
  return values;
};

const git = args =>
  execFileSync('git', args, {
    cwd: PROJECT_ROOT,
    encoding: 'utf8',
    env: {
      PATH: process.env.PATH,
      HOME: process.env.HOME,
      GIT_CONFIG_COUNT: '0',
      GIT_CONFIG_GLOBAL: process.platform === 'win32' ? 'NUL' : '/dev/null',
      GIT_CONFIG_NOSYSTEM: '1',
      GIT_OPTIONAL_LOCKS: '0',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();

export function parseStitchScreenIds(source) {
  return new Set(
    [...source.matchAll(/^\| ([A-Z][0-9]{2}) \|/gmu)].map(match => match[1]),
  );
}

async function runCli() {
  try {
    const argumentsMap = parseArguments(process.argv.slice(2));
    const file = argumentsMap.get('file');
    const mode = argumentsMap.get('mode') ?? 'template';
    if (file === undefined) throw new Error('--file is required');
    const document = JSON.parse(readFileSync(file, 'utf8'));
    const context = {
      projectRoot: PROJECT_ROOT,
      stitchIds: parseStitchScreenIds(
        readFileSync(
          path.join(PROJECT_ROOT, 'stitch/SCREEN_MANIFEST.md'),
          'utf8',
        ),
      ),
    };
    if (mode !== 'template') {
      for (const required of [
        'android-artifact',
        'ios-artifact',
        'asset-root',
        'evidence-root',
        'hosting-config',
      ]) {
        if (!argumentsMap.has(required))
          throw new Error(`--${required} is required in ${mode} mode`);
      }
      const trackedChanges = git([
        'status',
        '--porcelain=v1',
        '--untracked-files=no',
      ]);
      if (trackedChanges !== '')
        throw new Error('release source has tracked changes');
      context.currentSourceRevision = git(['rev-parse', '--verify', 'HEAD']);
      context.artifacts = {
        android: argumentsMap.get('android-artifact'),
        ios: argumentsMap.get('ios-artifact'),
      };
      context.assetRoot = argumentsMap.get('asset-root');
      context.evidenceRoot = argumentsMap.get('evidence-root');
      const hostingSource = readFileSync(
        argumentsMap.get('hosting-config'),
        'utf8',
      );
      context.hosting = parseReleaseConfig(JSON.parse(hostingSource));
    }
    const result = validateStoreSubmissionEvidence(document, {
      mode,
      ...context,
    });
    if (argumentsMap.get('print-digests') === true) {
      process.stdout.write(
        `${JSON.stringify(
          {
            approvalScopeSha256: result.scopeSha256,
            localizationSha256: Object.fromEntries(
              REQUIRED_LOCALES.map(locale => [
                locale,
                calculateLocalizationSha256(document.localizations[locale]),
              ]),
            ),
          },
          null,
          2,
        )}\n`,
      );
    }
    if (argumentsMap.get('print-scope') === true)
      process.stdout.write(`${result.scopeSha256}\n`);
    if (result.errors.length > 0) {
      for (const error of result.errors)
        process.stderr.write(`FAIL ${error}\n`);
      process.exitCode = 1;
      return;
    }
    process.stdout.write(
      `PASS store submission evidence (${mode}); scope ${result.scopeSha256}\n`,
    );
  } catch (error) {
    process.stderr.write(`FAIL ${error.message}\n`);
    process.exitCode = 1;
  }
}

if (
  process.argv[1] !== undefined &&
  realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))
) {
  await runCli();
}
