import { readFileSync, statSync } from 'node:fs';
import process from 'node:process';

const MAXIMUM_MANIFEST_BYTES = 2 * 1024 * 1024;
const E2E_ID = 'com.yashsomani.birthdayautopilot.e2e';
const PROD_ID = 'com.yashsomani.birthdayautopilot';

const exactMatches = (value, expression) => [...value.matchAll(expression)];

const manifestText = path => {
  const stat = statSync(path, { throwIfNoEntry: false });
  if (!stat?.isFile() || stat.size <= 0 || stat.size > MAXIMUM_MANIFEST_BYTES) {
    throw new Error('manifest must be a bounded regular file');
  }
  return readFileSync(path, 'utf8');
};

const packageName = manifest =>
  manifest.match(/<manifest\b[^>]*\bpackage="([^"]+)"/u)?.[1];

const componentNames = (manifest, element) =>
  exactMatches(
    manifest,
    new RegExp(`<${element}\\b[^>]*\\bandroid:name="([^"]+)"`, 'gu'),
  ).map(match => match[1]);

const permissions = manifest =>
  exactMatches(
    manifest,
    /<uses-permission\b[^>]*\bandroid:name="([^"]+)"/gu,
  ).map(match => match[1]);

const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};

export const verifyE2EMergedManifest = manifest => {
  assert(packageName(manifest) === E2E_ID, 'E2E package ID is not isolated');
  assert(
    /<application\b[^>]*\bandroid:name="com\.yashsomani\.birthdayautopilot\.e2e\.E2EMainApplication"/u.test(
      manifest,
    ),
    'E2E application host is missing',
  );
  assert(
    /<application\b[^>]*\bandroid:label="WishWell E2E"/u.test(manifest) ||
      /<application\b[^>]*\bandroid:label="@string\/app_name"/u.test(manifest),
    'E2E app has no distinct visible label',
  );
  assert(
    /<application\b[^>]*\bandroid:icon="@drawable\/e2e_launcher_icon"/u.test(
      manifest,
    ),
    'E2E app has no distinct icon',
  );
  assert(
    /<application\b[^>]*\bandroid:networkSecurityConfig="@xml\/e2e_network_security_config"/u.test(
      manifest,
    ),
    'E2E app is not bound to its reviewed network security config',
  );
  assert(
    /<application\b[^>]*\bandroid:usesCleartextTraffic="true"/u.test(manifest),
    'E2E app cannot reach loopback Metro',
  );

  const activities = componentNames(manifest, 'activity');
  assert(
    activities.length === 1 &&
      activities[0] === 'com.yashsomani.birthdayautopilot.e2e.E2EMainActivity',
    'E2E manifest must contain only its isolated activity',
  );
  for (const element of ['provider', 'receiver', 'service']) {
    assert(
      componentNames(manifest, element).length === 0,
      `E2E manifest contains a ${element}`,
    );
  }
  assert(
    !/<queries\b/u.test(manifest),
    'E2E manifest contains package queries',
  );
  assert(
    !/<uses-feature\b/u.test(manifest),
    'E2E manifest contains a hardware feature',
  );
  assert(
    JSON.stringify(permissions(manifest)) ===
      JSON.stringify(['android.permission.INTERNET']),
    'E2E manifest permissions are not limited to INTERNET',
  );
  assert(
    /android:allowBackup="false"/u.test(manifest),
    'E2E app must keep backup disabled',
  );
  assert(
    !/(SEND_SMS|READ_PHONE_STATE|Firebase|GoogleApi|WorkManager|SmsSent|SmsDelivery|AutomationReconcile)/u.test(
      manifest,
    ),
    'E2E manifest contains a product-native marker',
  );
};

export const verifyProdMergedManifest = manifest => {
  assert(packageName(manifest) === PROD_ID, 'production package ID changed');
  assert(
    permissions(manifest).includes('android.permission.SEND_SMS') &&
      permissions(manifest).includes('android.permission.READ_PHONE_STATE'),
    'production restricted manifest lost its required gated permissions',
  );
  assert(
    componentNames(manifest, 'activity').includes(
      'com.yashsomani.birthdayautopilot.MainActivity',
    ),
    'production MainActivity is missing',
  );
  assert(
    !/(E2EMain|WishWell E2E|birthday-e2e-fixture-v1)/u.test(manifest),
    'production merged manifest contains fixture identity',
  );
};

const parseArguments = args => {
  const values = new Map();
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index];
    const value = args[index + 1];
    if (!key?.startsWith('--') || value === undefined || values.has(key)) {
      throw new Error('invalid arguments');
    }
    values.set(key, value);
  }
  if (values.size !== 2) throw new Error('both merged manifests are required');
  return values;
};

if (process.argv[1]?.endsWith('verify-mobile-e2e-boundary.mjs')) {
  try {
    const values = parseArguments(process.argv.slice(2));
    verifyE2EMergedManifest(manifestText(values.get('--android-e2e-manifest')));
    verifyProdMergedManifest(
      manifestText(values.get('--android-prod-manifest')),
    );
    process.stdout.write(
      'PASS Android E2E and production merged manifests are isolated\n',
    );
  } catch (error) {
    process.stderr.write(
      `FAIL ${
        error instanceof Error ? error.message : 'boundary verification failed'
      }\n`,
    );
    process.exitCode = 1;
  }
}
