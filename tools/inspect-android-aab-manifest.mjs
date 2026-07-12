#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { lstatSync, realpathSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const SCRIPT_FILE = fileURLToPath(import.meta.url);
const PROJECT_ROOT = resolve(dirname(SCRIPT_FILE), '..');
const ANDROID_ROOT = resolve(PROJECT_ROOT, 'android');
const GRADLEW = resolve(ANDROID_ROOT, 'gradlew');
const MAXIMUM_AAB_BYTES = 1024 * 1024 * 1024;
const MAXIMUM_MANIFEST_BYTES = 4 * 1024 * 1024;
const SAFE_PACKAGE = /^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/u;
const SAFE_PERMISSION = /^[A-Za-z][A-Za-z0-9_.]{2,255}$/u;

const RELEASE_COORDINATES = new Map([
  [
    'lab',
    Object.freeze({
      applicationId: 'com.yashsomani.birthdayautopilot.lab',
      versionCode: '1',
      versionName: '1.0-lab',
    }),
  ],
  [
    'prod',
    Object.freeze({
      applicationId: 'com.yashsomani.birthdayautopilot',
      versionCode: '1',
      versionName: '1.0',
    }),
  ],
]);

const REQUIRED_PERMISSIONS = new Set([
  'android.permission.READ_PHONE_STATE',
  'android.permission.SEND_SMS',
]);

const FORBIDDEN_PERMISSIONS = new Set([
  'android.permission.ACCESS_BACKGROUND_LOCATION',
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.ANSWER_PHONE_CALLS',
  'android.permission.CALL_PHONE',
  'android.permission.CAMERA',
  'android.permission.MANAGE_EXTERNAL_STORAGE',
  'android.permission.PACKAGE_USAGE_STATS',
  'android.permission.PROCESS_OUTGOING_CALLS',
  'android.permission.QUERY_ALL_PACKAGES',
  'android.permission.READ_CALL_LOG',
  'android.permission.READ_CONTACTS',
  'android.permission.READ_EXTERNAL_STORAGE',
  'android.permission.READ_PHONE_NUMBERS',
  'android.permission.READ_SMS',
  'android.permission.RECEIVE_MMS',
  'android.permission.RECEIVE_SMS',
  'android.permission.RECEIVE_WAP_PUSH',
  'android.permission.RECORD_AUDIO',
  'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
  'android.permission.REQUEST_INSTALL_PACKAGES',
  'android.permission.SCHEDULE_EXACT_ALARM',
  'android.permission.SYSTEM_ALERT_WINDOW',
  'android.permission.USE_EXACT_ALARM',
  'android.permission.WRITE_CALL_LOG',
  'android.permission.WRITE_CONTACTS',
  'android.permission.WRITE_EXTERNAL_STORAGE',
  'android.permission.WRITE_SMS',
]);

const parseArguments = arguments_ => {
  if (arguments_.length % 2 !== 0) {
    throw new Error('arguments must be --name value pairs');
  }
  const allowed = new Set(['aab', 'package', 'tier']);
  const values = new Map();
  for (let index = 0; index < arguments_.length; index += 2) {
    const flag = arguments_[index];
    const value = arguments_[index + 1];
    if (!flag?.startsWith('--') || value === undefined) {
      throw new Error('arguments must be --name value pairs');
    }
    const name = flag.slice(2);
    if (!allowed.has(name)) throw new Error(`unsupported argument ${flag}`);
    if (values.has(name)) throw new Error(`duplicate argument ${flag}`);
    values.set(name, value);
  }
  const missing = [...allowed].filter(name => !values.get(name));
  if (missing.length > 0) {
    throw new Error(`missing arguments: ${missing.join(', ')}`);
  }
  return values;
};

const decodeXmlAttribute = value => {
  const decoded = value.replace(
    /&(?:amp|quot|apos|lt|gt|#\d{1,7}|#x[0-9A-Fa-f]{1,6});/gu,
    entity => {
      if (entity === '&amp;') return '&';
      if (entity === '&quot;') return '"';
      if (entity === '&apos;') return "'";
      if (entity === '&lt;') return '<';
      if (entity === '&gt;') return '>';
      const radix = entity.startsWith('&#x') ? 16 : 10;
      const digits = entity.slice(radix === 16 ? 3 : 2, -1);
      const codePoint = Number.parseInt(digits, radix);
      if (
        !Number.isSafeInteger(codePoint) ||
        codePoint < 0 ||
        codePoint > 0x10ffff ||
        (codePoint >= 0xd800 && codePoint <= 0xdfff)
      ) {
        throw new Error('decoded manifest contains an invalid XML entity');
      }
      return String.fromCodePoint(codePoint);
    },
  );
  if (decoded.includes('&')) {
    throw new Error('decoded manifest contains an unsupported XML entity');
  }
  return decoded;
};

const parseAttributes = (tail, label) => {
  const attributes = new Map();
  const source = tail.replace(/\/\s*$/u, '');
  const pattern = /\s*([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*"([^"]*)"/uy;
  let cursor = 0;
  while (cursor < source.length) {
    pattern.lastIndex = cursor;
    const match = pattern.exec(source);
    if (match === null) {
      if (/^\s*$/u.test(source.slice(cursor))) break;
      throw new Error(`${label} has malformed canonical attributes`);
    }
    const [, name, encodedValue] = match;
    if (attributes.has(name)) {
      throw new Error(`${label} contains a duplicate attribute`);
    }
    attributes.set(name, decodeXmlAttribute(encodedValue));
    cursor = pattern.lastIndex;
  }
  return attributes;
};

const findTags = (xml, name) => {
  const pattern = new RegExp(`<${name}(?=[\\s/>])([^>]*)>`, 'gu');
  return [...xml.matchAll(pattern)].map((match, index) =>
    parseAttributes(match[1], `${name} ${index + 1}`),
  );
};

const requireExact = (actual, expected, label) => {
  if (actual !== expected) {
    throw new Error(`decoded manifest ${label} does not match release policy`);
  }
};

const requireAbsentOrFalse = (attributes, name, label) => {
  const value = attributes.get(name);
  if (value !== undefined && value !== 'false') {
    throw new Error(`decoded manifest ${label} is not release-safe`);
  }
};

export function validateDecodedAabManifest(
  manifestXml,
  { expectedPackage, tier },
) {
  if (
    typeof manifestXml !== 'string' ||
    Buffer.byteLength(manifestXml, 'utf8') === 0 ||
    Buffer.byteLength(manifestXml, 'utf8') > MAXIMUM_MANIFEST_BYTES
  ) {
    throw new Error('decoded AAB manifest has an invalid size');
  }
  const coordinates = RELEASE_COORDINATES.get(tier);
  if (coordinates === undefined) throw new Error('AAB tier is unsupported');
  if (
    typeof expectedPackage !== 'string' ||
    !SAFE_PACKAGE.test(expectedPackage) ||
    expectedPackage !== coordinates.applicationId
  ) {
    throw new Error(
      'expected package does not match the selected release tier',
    );
  }

  const xml = manifestXml.trim();
  if (
    !xml.startsWith('<manifest ') ||
    !xml.endsWith('</manifest>') ||
    xml.includes('<!') ||
    xml.includes('<?') ||
    (xml.match(/<\/manifest>/gu) ?? []).length !== 1
  ) {
    throw new Error('bundletool returned a non-canonical base manifest');
  }

  const manifests = findTags(xml, 'manifest');
  const usesSdks = findTags(xml, 'uses-sdk');
  const applications = findTags(xml, 'application');
  if (
    manifests.length !== 1 ||
    usesSdks.length !== 1 ||
    applications.length !== 1
  ) {
    throw new Error('decoded manifest has an ambiguous release structure');
  }
  const manifest = manifests[0];
  const usesSdk = usesSdks[0];
  const application = applications[0];

  requireExact(manifest.get('package'), coordinates.applicationId, 'package');
  requireExact(
    manifest.get('android:versionCode'),
    coordinates.versionCode,
    'versionCode',
  );
  requireExact(
    manifest.get('android:versionName'),
    coordinates.versionName,
    'versionName',
  );
  requireExact(
    manifest.get('android:compileSdkVersion'),
    '36',
    'compileSdkVersion',
  );
  requireExact(usesSdk.get('android:minSdkVersion'), '29', 'minSdkVersion');
  requireExact(
    usesSdk.get('android:targetSdkVersion'),
    '36',
    'targetSdkVersion',
  );
  if (usesSdk.has('android:maxSdkVersion')) {
    throw new Error('decoded manifest must not cap maxSdkVersion');
  }
  if (manifest.has('android:sharedUserId')) {
    throw new Error('decoded manifest must not use a shared Android UID');
  }

  requireExact(application.get('android:allowBackup'), 'false', 'allowBackup');
  requireExact(
    application.get('android:usesCleartextTraffic'),
    'false',
    'usesCleartextTraffic',
  );
  requireExact(
    application.get('android:extractNativeLibs'),
    'false',
    'extractNativeLibs',
  );
  requireAbsentOrFalse(application, 'android:debuggable', 'debuggable flag');
  requireAbsentOrFalse(application, 'android:testOnly', 'test-only flag');
  requireAbsentOrFalse(
    application,
    'android:requestLegacyExternalStorage',
    'legacy-storage flag',
  );
  if (findTags(xml, 'profileable').length > 0) {
    throw new Error('decoded manifest must not expose release profiling');
  }

  const permissionTags = [
    ...findTags(xml, 'uses-permission'),
    ...findTags(xml, 'uses-permission-sdk-23'),
  ];
  const permissions = new Set();
  for (const permission of permissionTags) {
    const name = permission.get('android:name');
    if (typeof name !== 'string' || !SAFE_PERMISSION.test(name)) {
      throw new Error('decoded manifest contains an invalid permission name');
    }
    if (permissions.has(name)) {
      throw new Error('decoded manifest contains a duplicate permission');
    }
    permissions.add(name);
  }
  for (const permission of REQUIRED_PERMISSIONS) {
    if (!permissions.has(permission)) {
      throw new Error(
        'decoded manifest is missing a restricted-SMS permission',
      );
    }
  }
  for (const permission of FORBIDDEN_PERMISSIONS) {
    if (permissions.has(permission)) {
      throw new Error('decoded manifest contains a forbidden permission');
    }
  }

  const messagingFeatures = findTags(xml, 'uses-feature').filter(
    attributes =>
      attributes.get('android:name') === 'android.hardware.telephony.messaging',
  );
  if (
    messagingFeatures.length !== 1 ||
    messagingFeatures[0].get('android:required') !== 'true'
  ) {
    throw new Error(
      'decoded manifest must require the telephony messaging feature',
    );
  }

  return Object.freeze({
    applicationId: manifest.get('package'),
    versionCode: manifest.get('android:versionCode'),
    versionName: manifest.get('android:versionName'),
    minimumSdk: usesSdk.get('android:minSdkVersion'),
    targetSdk: usesSdk.get('android:targetSdkVersion'),
    permissions: Object.freeze([...permissions].sort()),
  });
}

const sameStableFile = (before, after) =>
  before.dev === after.dev &&
  before.ino === after.ino &&
  before.mode === after.mode &&
  before.nlink === after.nlink &&
  before.size === after.size &&
  before.mtimeNs === after.mtimeNs &&
  before.ctimeNs === after.ctimeNs;

export function inspectAabManifest({ aabPath, expectedPackage, tier }) {
  const requestedPath = resolve(aabPath);
  const beforePath = lstatSync(requestedPath, { bigint: true });
  if (
    !beforePath.isFile() ||
    beforePath.isSymbolicLink() ||
    beforePath.nlink !== 1n ||
    beforePath.size <= 0n ||
    beforePath.size > BigInt(MAXIMUM_AAB_BYTES)
  ) {
    throw new Error('AAB must be a non-linked regular file of bounded size');
  }
  const canonicalAab = realpathSync(requestedPath);
  if (canonicalAab !== requestedPath) {
    throw new Error('AAB path must already be canonical');
  }
  if (!process.env.JAVA_HOME || !process.env.ANDROID_HOME) {
    throw new Error('JAVA_HOME and ANDROID_HOME are required');
  }

  const result = spawnSync(
    GRADLEW,
    [
      '-q',
      'dumpBirthdayAabManifest',
      `-PbirthdayAabPath=${canonicalAab}`,
      '--offline',
      '--no-daemon',
      '--no-configuration-cache',
      '--console=plain',
    ],
    {
      cwd: ANDROID_ROOT,
      encoding: 'utf8',
      env: process.env,
      maxBuffer: MAXIMUM_MANIFEST_BYTES,
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  );
  if (result.error !== undefined || result.status !== 0) {
    throw new Error('locked bundletool could not decode the AAB base manifest');
  }
  const afterPath = lstatSync(requestedPath, { bigint: true });
  if (!sameStableFile(beforePath, afterPath)) {
    throw new Error('AAB changed while its manifest was decoded');
  }
  return validateDecodedAabManifest(result.stdout, { expectedPackage, tier });
}

function run() {
  try {
    const values = parseArguments(process.argv.slice(2));
    const result = inspectAabManifest({
      aabPath: values.get('aab'),
      expectedPackage: values.get('package'),
      tier: values.get('tier'),
    });
    process.stdout.write(
      [
        result.applicationId,
        result.versionCode,
        result.versionName,
        result.minimumSdk,
        result.targetSdk,
      ].join('\t') + '\n',
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : 'unknown error';
    process.stderr.write(`FAIL ${message}\n`);
    process.exitCode = 1;
  }
}

if (process.argv[1] && realpathSync(process.argv[1]) === SCRIPT_FILE) run();
