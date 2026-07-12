#!/usr/bin/env node

import { createHash, X509Certificate } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import {
  lstatSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  readlinkSync,
  realpathSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path, { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import {
  canonicalSha256,
  collectIOSReleaseReferenceDigests,
  stableJson,
  validateIOSReleaseEvidence,
} from './ios-release-evidence.mjs';
import {
  inspectCleanGitSource,
  sha256File,
  verifyDistributionEvidenceAuthority,
} from './validate-distribution-evidence.mjs';

const SCRIPT_FILE = fileURLToPath(import.meta.url);
const PROJECT_ROOT = resolve(dirname(SCRIPT_FILE), '..');
const AUTHORITY_PIN_FILE = resolve(
  PROJECT_ROOT,
  'tools/distribution-authority-pin.json',
);
const EXPECTED_BUNDLE = 'com.yashsomani.birthdayautopilot';
const EXPECTED_MINIMUM_OS = '15.1';
const EXPECTED_BACKGROUND_TASK =
  'com.yashsomani.birthdayautopilot.people-refresh';
const MAXIMUM_EVIDENCE_BYTES = 256 * 1024;
const MAXIMUM_PUBLIC_KEY_BYTES = 8 * 1024;
const MAXIMUM_IPA_BYTES = 2 * 1024 * 1024 * 1024;
const MAXIMUM_PLIST_BYTES = 2 * 1024 * 1024;
const ED25519_SIGNATURE_BYTES = 64;
const REVISION = /^[0-9a-f]{40}$/u;
const SAFE_EXECUTABLE = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$/u;
const TEAM_IDENTIFIER = /^[A-Z0-9]{10}$/u;
const FORBIDDEN_ENTITLEMENTS = new Set([
  'aps-environment',
  'com.apple.developer.associated-domains',
  'com.apple.developer.family-controls',
  'com.apple.developer.healthkit',
  'com.apple.developer.homekit',
  'com.apple.developer.icloud-container-environment',
  'com.apple.developer.icloud-container-identifiers',
  'com.apple.developer.icloud-services',
  'com.apple.developer.location.push',
  'com.apple.developer.networking.HotspotConfiguration',
  'com.apple.developer.networking.multicast',
  'com.apple.developer.networking.networkextension',
  'com.apple.developer.networking.vpn.api',
  'com.apple.developer.pass-type-identifiers',
  'com.apple.developer.siri',
  'com.apple.developer.ubiquity-container-identifiers',
  'com.apple.developer.ubiquity-kvstore-identifier',
  'com.apple.developer.usernotifications.communication',
  'com.apple.developer.usernotifications.filtering',
  'com.apple.developer.wifi-info',
  'com.apple.external-accessory.wireless-configuration',
  'com.apple.security.application-groups',
]);
const ALLOWED_ENTITLEMENTS = new Set([
  'application-identifier',
  'beta-reports-active',
  'com.apple.developer.default-data-protection',
  'com.apple.developer.devicecheck.appattest-environment',
  'com.apple.developer.team-identifier',
  'get-task-allow',
  'keychain-access-groups',
]);

const isObject = value =>
  value !== null && typeof value === 'object' && !Array.isArray(value);

const fail = message => {
  process.stderr.write(`FAIL ${message}\n`);
  process.exitCode = 1;
};

const checkedCommand = (binary, arguments_, options = {}) => {
  const result = spawnSync(binary, arguments_, {
    encoding: options.encoding ?? 'utf8',
    env: {
      ...process.env,
      LC_ALL: 'C',
      LANG: 'C',
    },
    maxBuffer: options.maxBuffer ?? 32 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  if (result.error || result.status !== 0) {
    const detail = `${String(result.stderr ?? '')}${String(
      result.stdout ?? '',
    )}`
      .trim()
      .split(/\r?\n/u)
      .slice(-2)
      .join(' ');
    throw new Error(
      `${path.basename(binary)} failed${detail ? `: ${detail}` : ''}`,
    );
  }
  return {
    stdout: result.stdout ?? '',
    stderr: result.stderr ?? '',
  };
};

const readBounded = (file, maximumBytes, label) => {
  const metadata = lstatSync(file, { bigint: true });
  if (
    !metadata.isFile() ||
    metadata.isSymbolicLink() ||
    metadata.size <= 0n ||
    metadata.size > BigInt(maximumBytes)
  ) {
    throw new Error(`${label} has an invalid size or type`);
  }
  const bytes = readFileSync(file);
  if (BigInt(bytes.byteLength) !== metadata.size) {
    throw new Error(`${label} changed while it was read`);
  }
  return bytes;
};

const parseArguments = argv => {
  const allowed = new Set([
    'mode',
    'archive',
    'ipa',
    'export-options',
    'evidence',
    'signature',
    'public-key',
    'evidence-root',
    'report',
  ]);
  if (argv.length % 2 !== 0) {
    throw new Error('arguments must be unique --name value pairs');
  }
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!flag?.startsWith('--') || value === undefined) {
      throw new Error('arguments must be unique --name value pairs');
    }
    const name = flag.slice(2);
    if (!allowed.has(name)) throw new Error(`unsupported argument ${flag}`);
    if (values.has(name)) throw new Error(`duplicate argument ${flag}`);
    values.set(name, value);
  }
  const mode = values.get('mode');
  if (mode !== 'inspect' && mode !== 'verify') {
    throw new Error('mode must be inspect or verify');
  }
  const required =
    mode === 'inspect'
      ? ['archive', 'ipa', 'export-options', 'report']
      : [...allowed];
  const missing = required.filter(name => !values.get(name));
  if (missing.length > 0) {
    throw new Error(`missing arguments: ${missing.join(', ')}`);
  }
  return values;
};

const parsePlist = (file, label) => {
  const bytes = readBounded(file, MAXIMUM_PLIST_BYTES, label);
  if (bytes.byteLength === 0) throw new Error(`${label} is empty`);
  const result = checkedCommand('/usr/bin/plutil', [
    '-convert',
    'json',
    '-o',
    '-',
    file,
  ]);
  let value;
  try {
    value = JSON.parse(result.stdout);
  } catch {
    throw new Error(`${label} cannot be represented as a property list`);
  }
  if (!isObject(value)) throw new Error(`${label} must contain a dictionary`);
  return value;
};

const normalizeInstant = (value, label) => {
  const parsed = Date.parse(String(value ?? ''));
  if (!Number.isFinite(parsed)) throw new Error(`${label} has an invalid date`);
  return new Date(parsed).toISOString();
};

const safeRelative = (root, candidate, label) => {
  const relative = path.relative(root, candidate);
  if (
    relative === '' ||
    relative === '..' ||
    relative.startsWith(`..${path.sep}`) ||
    path.isAbsolute(relative)
  ) {
    throw new Error(`${label} escapes its artifact root`);
  }
  return relative.split(path.sep).join('/');
};

const inspectSafeTree = rootPath => {
  const root = realpathSync(rootPath);
  const entries = [];
  const visit = candidate => {
    const metadata = lstatSync(candidate, { bigint: true });
    const relative = safeRelative(root, candidate, candidate);
    if (metadata.isSymbolicLink()) {
      const target = readlinkSync(candidate);
      if (path.isAbsolute(target)) {
        throw new Error(`${relative} has an absolute symbolic-link target`);
      }
      const resolved = path.resolve(path.dirname(candidate), target);
      safeRelative(root, resolved, relative);
      entries.push({ path: relative, target, type: 'symlink' });
      return;
    }
    if (metadata.isDirectory()) {
      entries.push({ path: `${relative}/`, type: 'directory' });
      for (const child of readdirSync(candidate).sort()) {
        visit(path.join(candidate, child));
      }
      return;
    }
    if (!metadata.isFile()) {
      throw new Error(`${relative} has an unsupported filesystem type`);
    }
    entries.push({
      path: relative,
      type: 'file',
      size: Number(metadata.size),
      // Executable permission bits are deliberately reduced to one stable flag.
      // eslint-disable-next-line no-bitwise
      executable: (Number(metadata.mode) & 0o111) !== 0,
      sha256: sha256File(candidate),
    });
  };
  for (const child of readdirSync(root).sort()) visit(path.join(root, child));
  return { entries, sha256: canonicalSha256(entries) };
};

const extractIpa = (ipaPath, temporaryRoot) => {
  const listing = checkedCommand('/usr/bin/unzip', ['-Z1', ipaPath])
    .stdout.split(/\r?\n/u)
    .filter(Boolean);
  if (listing.length === 0 || listing.length > 100_000) {
    throw new Error('IPA ZIP has an invalid entry count');
  }
  const seen = new Set();
  for (const entry of listing) {
    if (
      entry.includes('\\') ||
      entry.includes('\u0000') ||
      entry.startsWith('/') ||
      entry.split('/').some(part => part === '..') ||
      seen.has(entry)
    ) {
      throw new Error('IPA ZIP contains an unsafe or duplicate path');
    }
    seen.add(entry);
  }
  const extractionRoot = path.join(temporaryRoot, 'ipa-extracted');
  checkedCommand('/usr/bin/ditto', ['-x', '-k', ipaPath, extractionRoot]);
  inspectSafeTree(extractionRoot);
  const payload = path.join(extractionRoot, 'Payload');
  const apps = readdirSync(payload).filter(name => name.endsWith('.app'));
  if (apps.length !== 1 || readdirSync(payload).length !== 1) {
    throw new Error('IPA must contain exactly one Payload application');
  }
  return path.join(payload, apps[0]);
};

const findArchiveApp = archivePath => {
  const applications = path.join(archivePath, 'Products', 'Applications');
  const entries = readdirSync(applications);
  const apps = entries.filter(name => name.endsWith('.app'));
  if (apps.length !== 1 || entries.length !== 1) {
    throw new Error('xcarchive must contain exactly one application product');
  }
  return path.join(applications, apps[0]);
};

const extractCodeSignature = (
  bundlePath,
  temporaryRoot,
  label,
  entitlementsRequired = true,
) => {
  checkedCommand('/usr/bin/codesign', [
    '--verify',
    '--deep',
    '--strict',
    '--verbose=2',
    bundlePath,
  ]);
  const display = checkedCommand('/usr/bin/codesign', [
    '-d',
    '--verbose=4',
    bundlePath,
  ]);
  const metadataText = `${display.stdout}${display.stderr}`;
  const teamIdentifier =
    metadataText.match(/^TeamIdentifier=([A-Z0-9]+)$/mu)?.[1] ?? '';
  if (!TEAM_IDENTIFIER.test(teamIdentifier)) {
    throw new Error(`${label} code signature has no valid TeamIdentifier`);
  }
  const codeDirectoryHash =
    metadataText.match(/^CDHash=([0-9a-f]{40,64})$/mu)?.[1] ?? '';
  if (!/^[0-9a-f]{40,64}$/u.test(codeDirectoryHash)) {
    throw new Error(`${label} code signature has no valid CDHash`);
  }

  const entitlementPath = path.join(
    temporaryRoot,
    `${label}-entitlements.plist`,
  );
  checkedCommand('/usr/bin/codesign', [
    '-d',
    '--entitlements',
    entitlementPath,
    '--xml',
    bundlePath,
  ]);
  const entitlementMetadata = statSync(entitlementPath, {
    throwIfNoEntry: false,
  });
  const hasEntitlements =
    entitlementMetadata?.isFile() && entitlementMetadata.size > 0;
  if (entitlementsRequired && !hasEntitlements) {
    throw new Error(`${label} signed entitlements are missing`);
  }
  const entitlements = hasEntitlements
    ? parsePlist(entitlementPath, `${label} signed entitlements`)
    : {};

  const certificatePrefix = path.join(temporaryRoot, `${label}-certificate`);
  checkedCommand('/usr/bin/codesign', [
    '-d',
    '--extract-certificates',
    certificatePrefix,
    bundlePath,
  ]);
  const certificateFiles = readdirSync(temporaryRoot)
    .filter(name => name.startsWith(`${label}-certificate`))
    .sort()
    .map(name => path.join(temporaryRoot, name));
  if (certificateFiles.length < 1) {
    throw new Error(`${label} code signature has no extractable certificate`);
  }
  checkedCommand('/usr/bin/security', [
    'verify-cert',
    '-p',
    'codeSign',
    '-R',
    'offline',
    ...certificateFiles.flatMap(file => ['-c', file]),
  ]);
  const leafCertificateBytes = readFileSync(certificateFiles[0]);
  const leafCertificate = new X509Certificate(leafCertificateBytes);
  const certificateExpiration = normalizeInstant(
    leafCertificate.validTo,
    `${label} signing certificate expiration`,
  );
  return {
    certificateExpiration,
    certificateSha256: createHash('sha256')
      .update(leafCertificateBytes)
      .digest('hex'),
    codeDirectoryHash,
    entitlements,
    entitlementsSha256: canonicalSha256(entitlements),
    teamIdentifier,
  };
};

const extractProvisioningProfile = (appPath, temporaryRoot, label) => {
  const embedded = path.join(appPath, 'embedded.mobileprovision');
  const decoded = path.join(temporaryRoot, `${label}-profile.plist`);
  readBounded(embedded, MAXIMUM_PLIST_BYTES, `${label} provisioning profile`);
  checkedCommand('/usr/bin/security', [
    'cms',
    '-D',
    '-i',
    embedded,
    '-o',
    decoded,
  ]);
  const profile = parsePlist(decoded, `${label} provisioning profile`);
  return profile;
};

const architecturesFor = (binary, label) => {
  const output = checkedCommand('/usr/bin/lipo', [
    '-archs',
    binary,
  ]).stdout.trim();
  const architectures = [
    ...new Set(output.split(/\s+/u).filter(Boolean)),
  ].sort();
  if (architectures.length === 0) {
    throw new Error(`${label} has no Mach-O architecture`);
  }
  return architectures;
};

const inspectEmbeddedFrameworks = (
  appPath,
  appTeamIdentifier,
  temporaryRoot,
  label,
) => {
  const root = path.join(appPath, 'Frameworks');
  if (!statSync(root, { throwIfNoEntry: false })?.isDirectory()) return [];
  const records = [];
  for (const name of readdirSync(root).sort()) {
    const candidate = path.join(root, name);
    const relative = `Frameworks/${name}`;
    if (lstatSync(candidate).isSymbolicLink()) {
      throw new Error(`${relative} must not be a symbolic link`);
    }
    let binary;
    let bundleIdentifier = null;
    let bundleVersion = null;
    if (name.endsWith('.framework') && lstatSync(candidate).isDirectory()) {
      const info = parsePlist(
        path.join(candidate, 'Info.plist'),
        `${label} ${relative} Info.plist`,
      );
      const executable = info.CFBundleExecutable;
      if (typeof executable !== 'string' || !SAFE_EXECUTABLE.test(executable)) {
        throw new Error(`${relative} has an invalid executable name`);
      }
      binary = path.join(candidate, executable);
      bundleIdentifier = info.CFBundleIdentifier ?? null;
      bundleVersion = info.CFBundleVersion ?? null;
    } else if (name.endsWith('.dylib') && lstatSync(candidate).isFile()) {
      binary = candidate;
    } else {
      throw new Error(`Frameworks contains unsupported entry ${name}`);
    }
    const signature = extractCodeSignature(
      candidate,
      temporaryRoot,
      `${label}-framework-${records.length}`,
      false,
    );
    if (signature.teamIdentifier !== appTeamIdentifier) {
      throw new Error(`${relative} is signed by a different team`);
    }
    if (Object.keys(signature.entitlements).length > 0) {
      throw new Error(`${relative} must not carry library entitlements`);
    }
    records.push({
      architectures: architecturesFor(binary, relative),
      binarySha256: sha256File(binary),
      bundleIdentifier,
      bundleVersion,
      path: relative,
      codeDirectoryHash: signature.codeDirectoryHash,
      teamIdentifier: signature.teamIdentifier,
    });
  }
  return records;
};

const developerCertificateDigests = profile => {
  if (!Array.isArray(profile.DeveloperCertificates)) return [];
  return profile.DeveloperCertificates.map(value => {
    if (typeof value !== 'string') return '';
    return createHash('sha256')
      .update(Buffer.from(value, 'base64'))
      .digest('hex');
  });
};

const exactStringArray = (value, expected) =>
  Array.isArray(value) &&
  value.length === expected.length &&
  value.every((entry, index) => entry === expected[index]);

export function validateIOSApplicationPolicy(application, now = Date.now()) {
  const errors = [];
  const { info, privacy, entitlements, profile, signature, firebase } =
    application;
  const firebaseClientId = String(firebase.CLIENT_ID ?? '');
  const firebaseProjectNumber = String(firebase.GCM_SENDER_ID ?? '');
  const firebaseGoogleAppId = String(firebase.GOOGLE_APP_ID ?? '');
  const firebaseApiKey = String(firebase.API_KEY ?? '');
  if (
    firebase.BUNDLE_ID !== EXPECTED_BUNDLE ||
    typeof firebase.PROJECT_ID !== 'string' ||
    !/^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$/u.test(firebase.PROJECT_ID) ||
    !/^[1-9][0-9]{5,19}$/u.test(firebaseProjectNumber) ||
    !firebaseGoogleAppId.startsWith(`1:${firebaseProjectNumber}:ios:`) ||
    !/^[0-9]+-[a-z0-9-]+\.apps\.googleusercontent\.com$/u.test(
      firebaseClientId,
    ) ||
    firebase.REVERSED_CLIENT_ID !==
      firebaseClientId.split('.').reverse().join('.') ||
    firebaseApiKey.length < 20 ||
    firebaseApiKey.length > 128
  ) {
    errors.push(
      'embedded Firebase configuration identity is incomplete or invalid',
    );
  }
  if (info.CFBundleIdentifier !== EXPECTED_BUNDLE) {
    errors.push('bundle identifier is not the production bundle');
  }
  if (!REVISION.test(info.BirthdaySourceRevision ?? '')) {
    errors.push('BirthdaySourceRevision is missing or invalid');
  }
  if (info.BirthdayFirebaseEnvironment !== 'prod') {
    errors.push('embedded Firebase environment is not prod');
  }
  if (info.BirthdayExpectedFirebaseProjectID !== firebase.PROJECT_ID) {
    errors.push('expected Firebase project does not match embedded config');
  }
  if (info.BirthdayGoogleReversedClientID !== firebase.REVERSED_CLIENT_ID) {
    errors.push('expected reversed client ID does not match embedded config');
  }
  if (info.MinimumOSVersion !== EXPECTED_MINIMUM_OS) {
    errors.push('MinimumOSVersion is not 15.1');
  }
  if (
    info.DTPlatformName !== 'iphoneos' ||
    !exactStringArray(info.CFBundleSupportedPlatforms, ['iPhoneOS'])
  ) {
    errors.push('application is not an iphoneos device build');
  }
  if (!exactStringArray(info.UIRequiredDeviceCapabilities, ['arm64'])) {
    errors.push('UIRequiredDeviceCapabilities must require only arm64');
  }
  if (!exactStringArray(info.UIBackgroundModes, ['fetch'])) {
    errors.push('UIBackgroundModes must contain only fetch');
  }
  if (
    !exactStringArray(info.BGTaskSchedulerPermittedIdentifiers, [
      EXPECTED_BACKGROUND_TASK,
    ])
  ) {
    errors.push(
      'background task identifiers do not match the companion contract',
    );
  }
  const ats = info.NSAppTransportSecurity;
  if (
    !isObject(ats) ||
    ats.NSAllowsArbitraryLoads !== false ||
    ats.NSAllowsLocalNetworking !== false ||
    Object.hasOwn(ats, 'NSExceptionDomains')
  ) {
    errors.push('App Transport Security is not fail-closed');
  }
  for (const forbidden of [
    'CFBundleDocumentTypes',
    'LSApplicationQueriesSchemes',
    'LSSupportsOpeningDocumentsInPlace',
    'NSBonjourServices',
    'NSLocalNetworkUsageDescription',
    'UIFileSharingEnabled',
  ]) {
    if (Object.hasOwn(info, forbidden)) {
      errors.push(`Info.plist contains forbidden key ${forbidden}`);
    }
  }
  const urlTypes = info.CFBundleURLTypes;
  const urlType =
    Array.isArray(urlTypes) && urlTypes.length === 1 ? urlTypes[0] : null;
  if (
    !isObject(urlType) ||
    urlType.CFBundleTypeRole !== 'Editor' ||
    !exactStringArray(urlType.CFBundleURLSchemes, [firebase.REVERSED_CLIENT_ID])
  ) {
    errors.push(
      'URL schemes must contain only the exact Google reversed client ID',
    );
  }
  if (
    info.FirebaseAppCheckTokenAutoRefreshEnabled !== true ||
    info.RCTNewArchEnabled !== true
  ) {
    errors.push(
      'required Firebase App Check or React Native production flag is absent',
    );
  }
  if (privacy.NSPrivacyTracking !== false) {
    errors.push('privacy manifest must declare tracking false');
  }
  if (
    Object.hasOwn(privacy, 'NSPrivacyTrackingDomains') &&
    (!Array.isArray(privacy.NSPrivacyTrackingDomains) ||
      privacy.NSPrivacyTrackingDomains.length !== 0)
  ) {
    errors.push('privacy manifest must not declare tracking domains');
  }
  if (!Array.isArray(privacy.NSPrivacyAccessedAPITypes)) {
    errors.push(
      'privacy manifest required-reason API declarations are missing',
    );
  }

  for (const key of Object.keys(entitlements)) {
    if (FORBIDDEN_ENTITLEMENTS.has(key) || !ALLOWED_ENTITLEMENTS.has(key)) {
      errors.push(`signed entitlements contain unapproved capability ${key}`);
    }
  }
  if (entitlements['get-task-allow'] === true) {
    errors.push('get-task-allow must not be enabled');
  }
  if (
    entitlements['com.apple.developer.default-data-protection'] !==
      'NSFileProtectionComplete' ||
    entitlements['com.apple.developer.devicecheck.appattest-environment'] !==
      'production'
  ) {
    errors.push(
      'complete data protection and production App Attest are required',
    );
  }
  const team = signature.teamIdentifier;
  const applicationIdentifier = `${team}.${EXPECTED_BUNDLE}`;
  if (
    entitlements['com.apple.developer.team-identifier'] !== team ||
    entitlements['application-identifier'] !== applicationIdentifier
  ) {
    errors.push('signed application/team identifiers are inconsistent');
  }
  const keychainGroups = entitlements['keychain-access-groups'];
  if (
    keychainGroups !== undefined &&
    (!Array.isArray(keychainGroups) ||
      keychainGroups.length === 0 ||
      keychainGroups.some(
        group =>
          typeof group !== 'string' ||
          !group.startsWith(`${team}.`) ||
          group.includes('*'),
      ))
  ) {
    errors.push(
      'keychain access groups are not restricted to the signing team',
    );
  }

  const profileTeam = Array.isArray(profile.TeamIdentifier)
    ? profile.TeamIdentifier
    : [];
  const profilePrefixes = Array.isArray(profile.ApplicationIdentifierPrefix)
    ? profile.ApplicationIdentifierPrefix
    : [];
  if (
    !exactStringArray(profileTeam, [team]) ||
    !exactStringArray(profilePrefixes, [team])
  ) {
    errors.push(
      'provisioning profile team/prefix does not match the signature',
    );
  }
  if (
    profile.ProvisionedDevices !== undefined ||
    profile.ProvisionsAllDevices !== undefined ||
    profile.Entitlements?.['get-task-allow'] !== false
  ) {
    errors.push(
      'provisioning profile is not an App Store distribution profile',
    );
  }
  if (
    profile.Entitlements?.['application-identifier'] !== applicationIdentifier
  ) {
    errors.push('provisioning profile application identifier is not exact');
  }
  if (
    profile.Entitlements?.['com.apple.developer.default-data-protection'] !==
      'NSFileProtectionComplete' ||
    profile.Entitlements?.[
      'com.apple.developer.devicecheck.appattest-environment'
    ] !== 'production'
  ) {
    errors.push(
      'provisioning profile does not authorize complete data protection and production App Attest',
    );
  }
  if (
    !developerCertificateDigests(profile).includes(signature.certificateSha256)
  ) {
    errors.push('code-signing certificate is not authorized by the profile');
  }
  const expiration = Date.parse(String(profile.ExpirationDate ?? ''));
  if (!Number.isFinite(expiration) || expiration <= now) {
    errors.push('provisioning profile is expired or has no valid expiration');
  }
  if (!exactStringArray(application.architectures, ['arm64'])) {
    errors.push('application binary must contain only arm64');
  }
  for (const framework of application.frameworks) {
    if (!exactStringArray(framework.architectures, ['arm64'])) {
      errors.push(`${framework.path} must contain only arm64`);
    }
  }
  if (application.forbiddenBundleEntries.length > 0) {
    errors.push(
      'application contains an unapproved extension, App Clip, or Watch payload',
    );
  }
  return { errors };
}

const inspectApplication = (appPath, temporaryRoot, label, now) => {
  const infoPath = path.join(appPath, 'Info.plist');
  const privacyPath = path.join(appPath, 'PrivacyInfo.xcprivacy');
  const firebasePath = path.join(appPath, 'GoogleService-Info.plist');
  const info = parsePlist(infoPath, `${label} Info.plist`);
  const privacy = parsePlist(privacyPath, `${label} PrivacyInfo.xcprivacy`);
  const firebase = parsePlist(
    firebasePath,
    `${label} GoogleService-Info.plist`,
  );
  const executable = info.CFBundleExecutable;
  if (typeof executable !== 'string' || !SAFE_EXECUTABLE.test(executable)) {
    throw new Error(`${label} has an invalid CFBundleExecutable`);
  }
  const binary = path.join(appPath, executable);
  const signature = extractCodeSignature(appPath, temporaryRoot, label);
  const profile = extractProvisioningProfile(appPath, temporaryRoot, label);
  const frameworks = inspectEmbeddedFrameworks(
    appPath,
    signature.teamIdentifier,
    temporaryRoot,
    label,
  );
  const forbiddenBundleEntries = ['PlugIns', 'Watch', 'AppClips'].filter(name =>
    statSync(path.join(appPath, name), { throwIfNoEntry: false }),
  );
  const application = {
    appBinarySha256: sha256File(binary),
    architectures: architecturesFor(binary, `${label} application binary`),
    firebase,
    firebaseConfigSha256: sha256File(firebasePath),
    forbiddenBundleEntries,
    frameworks,
    frameworksManifestSha256: canonicalSha256(frameworks),
    frameworksSemanticSha256: canonicalSha256(
      frameworks.map(({ binarySha256: _binarySha256, ...record }) => record),
    ),
    info,
    infoPlistSha256: sha256File(infoPath),
    privacy,
    privacyManifestSha256: sha256File(privacyPath),
    profile,
    signature,
  };
  const policy = validateIOSApplicationPolicy(application, now);
  if (policy.errors.length > 0) {
    throw new Error(`${label}: ${policy.errors.join('; ')}`);
  }
  return application;
};

const firebaseProjection = application => ({
  environment: application.info.BirthdayFirebaseEnvironment,
  projectId: application.firebase.PROJECT_ID,
  projectNumber: String(application.firebase.GCM_SENDER_ID),
  googleAppId: application.firebase.GOOGLE_APP_ID,
  oauthClientId: application.firebase.CLIENT_ID,
  reversedClientId: application.firebase.REVERSED_CLIENT_ID,
  configSha256: application.firebaseConfigSha256,
  apiKeySha256: createHash('sha256')
    .update(String(application.firebase.API_KEY ?? ''), 'utf8')
    .digest('hex'),
});

const profileProjection = application => ({
  certificateExpiration: application.signature.certificateExpiration,
  certificateSha256: application.signature.certificateSha256,
  expiration: normalizeInstant(
    application.profile.ExpirationDate,
    'profile expiration',
  ),
  name: String(application.profile.Name ?? ''),
  uuid: String(application.profile.UUID ?? '').toUpperCase(),
});

const assertEquivalentApplications = (archive, exported) => {
  const comparisons = [
    [
      'bundle',
      archive.info.CFBundleIdentifier,
      exported.info.CFBundleIdentifier,
    ],
    [
      'marketing version',
      archive.info.CFBundleShortVersionString,
      exported.info.CFBundleShortVersionString,
    ],
    [
      'build number',
      archive.info.CFBundleVersion,
      exported.info.CFBundleVersion,
    ],
    [
      'source revision',
      archive.info.BirthdaySourceRevision,
      exported.info.BirthdaySourceRevision,
    ],
    [
      'application code directory',
      archive.signature.codeDirectoryHash,
      exported.signature.codeDirectoryHash,
    ],
    [
      'embedded frameworks',
      archive.frameworksSemanticSha256,
      exported.frameworksSemanticSha256,
    ],
    ['Info.plist', archive.infoPlistSha256, exported.infoPlistSha256],
    [
      'privacy manifest',
      archive.privacyManifestSha256,
      exported.privacyManifestSha256,
    ],
    [
      'signed entitlements',
      archive.signature.entitlementsSha256,
      exported.signature.entitlementsSha256,
    ],
    [
      'Firebase config',
      archive.firebaseConfigSha256,
      exported.firebaseConfigSha256,
    ],
    [
      'signing team',
      archive.signature.teamIdentifier,
      exported.signature.teamIdentifier,
    ],
  ];
  const mismatch = comparisons.find(([, left, right]) => left !== right);
  if (mismatch) {
    throw new Error(`archive and exported IPA differ in ${mismatch[0]}`);
  }
};

export function inspectIOSReleaseArtifacts({
  archivePath,
  ipaPath,
  exportOptionsPath,
  temporaryRoot,
  sourceRevision,
  now = Date.now(),
}) {
  if (process.platform !== 'darwin') {
    throw new Error(
      'signed iOS artifact inspection requires macOS official tools',
    );
  }
  if (lstatSync(archivePath).isSymbolicLink()) {
    throw new Error('archive path must not be a symbolic link');
  }
  const archive = realpathSync(archivePath);
  if (!lstatSync(archive).isDirectory() || !archive.endsWith('.xcarchive')) {
    throw new Error('archive must be a real .xcarchive directory');
  }
  if (lstatSync(ipaPath).isSymbolicLink()) {
    throw new Error('IPA path must not be a symbolic link');
  }
  const ipa = realpathSync(ipaPath);
  if (!lstatSync(ipa).isFile() || !ipa.endsWith('.ipa')) {
    throw new Error('IPA must be a real .ipa file');
  }
  readBounded(ipa, MAXIMUM_IPA_BYTES, 'IPA');
  const exportOptions = parsePlist(exportOptionsPath, 'ExportOptions.plist');
  if (
    exportOptions.method !== 'app-store-connect' ||
    exportOptions.signingStyle !== 'manual'
  ) {
    throw new Error(
      'ExportOptions.plist must use app-store-connect with manual signing',
    );
  }
  if (
    exportOptions.destination !== undefined &&
    exportOptions.destination !== 'export'
  ) {
    throw new Error(
      'ExportOptions.plist destination must be export when present',
    );
  }
  const archiveTree = inspectSafeTree(archive);
  const archiveApplication = inspectApplication(
    findArchiveApp(archive),
    temporaryRoot,
    'archive',
    now,
  );
  const exportedApplication = inspectApplication(
    extractIpa(ipa, temporaryRoot),
    temporaryRoot,
    'exported',
    now,
  );
  assertEquivalentApplications(archiveApplication, exportedApplication);
  if (archiveApplication.info.BirthdaySourceRevision !== sourceRevision) {
    throw new Error('embedded source revision does not match clean Git HEAD');
  }
  const team = exportedApplication.signature.teamIdentifier;
  if (exportOptions.teamID !== team) {
    throw new Error('ExportOptions.plist teamID does not match the signed app');
  }
  const profileSelection =
    exportOptions.provisioningProfiles?.[EXPECTED_BUNDLE];
  if (
    profileSelection !== exportedApplication.profile.Name &&
    String(profileSelection ?? '').toUpperCase() !==
      String(exportedApplication.profile.UUID ?? '').toUpperCase()
  ) {
    throw new Error(
      'ExportOptions.plist provisioning profile does not match the exported app',
    );
  }
  const archiveProfile = profileProjection(archiveApplication);
  const exportedProfile = profileProjection(exportedApplication);
  const firebase = firebaseProjection(exportedApplication);
  return {
    sourceRevision,
    artifact: {
      archiveTreeSha256: archiveTree.sha256,
      ipaSha256: sha256File(ipa, MAXIMUM_IPA_BYTES),
      exportOptionsSha256: sha256File(exportOptionsPath),
      bundleIdentifier: exportedApplication.info.CFBundleIdentifier,
      marketingVersion: exportedApplication.info.CFBundleShortVersionString,
      buildNumber: String(exportedApplication.info.CFBundleVersion),
      minimumOSVersion: exportedApplication.info.MinimumOSVersion,
      platform: exportedApplication.info.DTPlatformName,
      appBinarySha256: exportedApplication.appBinarySha256,
      embeddedFrameworksManifestSha256:
        exportedApplication.frameworksManifestSha256,
    },
    firebase,
    signing: {
      distributionMethod: exportOptions.method,
      teamIdentifier: team,
      archiveCertificateExpiration: archiveProfile.certificateExpiration,
      archiveCertificateSha256: archiveProfile.certificateSha256,
      exportedCertificateExpiration: exportedProfile.certificateExpiration,
      exportedCertificateSha256: exportedProfile.certificateSha256,
      archiveProvisioningProfileUuid: archiveProfile.uuid,
      archiveProvisioningProfileName: archiveProfile.name,
      archiveProvisioningProfileExpiration: archiveProfile.expiration,
      exportedProvisioningProfileUuid: exportedProfile.uuid,
      exportedProvisioningProfileName: exportedProfile.name,
      exportedProvisioningProfileExpiration: exportedProfile.expiration,
      applicationIdentifier:
        exportedApplication.signature.entitlements['application-identifier'],
    },
    security: {
      entitlementsSha256: exportedApplication.signature.entitlementsSha256,
      infoPlistSha256: exportedApplication.infoPlistSha256,
      privacyManifestSha256: exportedApplication.privacyManifestSha256,
      arm64Only: true,
      debugEntitlementAbsent: true,
      appAttestProduction: true,
      noForbiddenCapabilities: true,
      noForbiddenUrlSchemes: true,
    },
  };
}

const run = () => {
  let argumentsByName;
  try {
    argumentsByName = parseArguments(process.argv.slice(2));
  } catch (error) {
    fail(error instanceof Error ? error.message : 'invalid arguments');
    return;
  }
  if (argumentsByName.get('mode') === 'inspect') {
    let rawPin;
    let source;
    try {
      rawPin = readBounded(AUTHORITY_PIN_FILE, 1024, 'release authority pin');
      source = inspectCleanGitSource(PROJECT_ROOT, rawPin);
    } catch (error) {
      fail(error instanceof Error ? error.message : 'source is unreadable');
      return;
    }
    if (source.errors.length > 0) {
      source.errors.forEach(fail);
      return;
    }
    const temporaryRoot = mkdtempSync(
      path.join(tmpdir(), 'birthday-ios-observation-'),
    );
    let observed;
    try {
      observed = inspectIOSReleaseArtifacts({
        archivePath: argumentsByName.get('archive'),
        ipaPath: argumentsByName.get('ipa'),
        exportOptionsPath: argumentsByName.get('export-options'),
        temporaryRoot,
        sourceRevision: source.sourceRevision,
      });
      writeFileSync(
        argumentsByName.get('report'),
        `${stableJson({
          schemaVersion: 1,
          product: 'birthday-autopilot-ios-candidate-observation',
          status: 'candidate-observation-not-release',
          observedAt: new Date().toISOString(),
          observed,
        })}\n`,
        { encoding: 'utf8', flag: 'wx', mode: 0o600 },
      );
    } catch (error) {
      fail(
        error instanceof Error ? error.message : 'artifact inspection failed',
      );
      return;
    } finally {
      rmSync(temporaryRoot, { force: true, recursive: true });
    }
    process.stdout.write(
      `PASS candidate observation only; NOT RELEASE ipa=${observed.artifact.ipaSha256} source=${observed.sourceRevision}\n`,
    );
    return;
  }
  let rawEvidence;
  let detachedSignature;
  let publicKeyBytes;
  let rawPin;
  let pinDocument;
  try {
    rawEvidence = readBounded(
      argumentsByName.get('evidence'),
      MAXIMUM_EVIDENCE_BYTES,
      'release evidence',
    );
    detachedSignature = readBounded(
      argumentsByName.get('signature'),
      ED25519_SIGNATURE_BYTES,
      'release evidence signature',
    );
    publicKeyBytes = readBounded(
      argumentsByName.get('public-key'),
      MAXIMUM_PUBLIC_KEY_BYTES,
      'release authority public key',
    );
    rawPin = readBounded(AUTHORITY_PIN_FILE, 1024, 'release authority pin');
    pinDocument = JSON.parse(rawPin.toString('utf8'));
  } catch (error) {
    fail(
      error instanceof Error ? error.message : 'release evidence is unreadable',
    );
    return;
  }
  const authority = verifyDistributionEvidenceAuthority({
    rawEvidence,
    detachedSignature,
    publicKeyBytes,
    pinDocument,
  });
  if (authority.errors.length > 0) {
    authority.errors.forEach(fail);
    return;
  }
  const source = inspectCleanGitSource(PROJECT_ROOT, rawPin);
  if (source.errors.length > 0) {
    source.errors.forEach(fail);
    return;
  }
  let document;
  try {
    document = JSON.parse(rawEvidence.toString('utf8'));
  } catch {
    fail('release evidence is malformed JSON');
    return;
  }
  const evidencePreflight = validateIOSReleaseEvidence(document);
  if (evidencePreflight.errors.length > 0) {
    evidencePreflight.errors.forEach(fail);
    return;
  }
  let referenceDigests;
  let observed;
  const temporaryRoot = mkdtempSync(
    path.join(tmpdir(), 'birthday-ios-release-'),
  );
  try {
    referenceDigests = collectIOSReleaseReferenceDigests(
      argumentsByName.get('evidence-root'),
      document.references,
    );
    observed = inspectIOSReleaseArtifacts({
      archivePath: argumentsByName.get('archive'),
      ipaPath: argumentsByName.get('ipa'),
      exportOptionsPath: argumentsByName.get('export-options'),
      temporaryRoot,
      sourceRevision: source.sourceRevision,
    });
  } catch (error) {
    fail(error instanceof Error ? error.message : 'artifact inspection failed');
    return;
  } finally {
    rmSync(temporaryRoot, { force: true, recursive: true });
  }
  const validation = validateIOSReleaseEvidence(document, {
    observed,
    referenceDigests,
  });
  if (validation.errors.length > 0) {
    validation.errors.forEach(fail);
    return;
  }
  const report = {
    schemaVersion: 1,
    product: 'birthday-autopilot-ios-release-verification',
    verifiedAt: new Date().toISOString(),
    evidenceSha256: createHash('sha256').update(rawEvidence).digest('hex'),
    evidenceAuthorityPublicKeySpkiSha256: authority.publicKeySpkiSha256,
    observed,
    referenceDigests,
  };
  writeFileSync(argumentsByName.get('report'), `${stableJson(report)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
    mode: 0o600,
  });
  process.stdout.write(
    `PASS iOS release archive=${observed.artifact.archiveTreeSha256} ipa=${observed.artifact.ipaSha256} source=${observed.sourceRevision}\n`,
  );
};

if (
  process.argv[1] &&
  realpathSync(SCRIPT_FILE) === realpathSync(process.argv[1])
) {
  run();
}
