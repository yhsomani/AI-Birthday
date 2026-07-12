import { readFileSync, statSync } from 'node:fs';
import process from 'node:process';

const MAXIMUM_MANIFEST_BYTES = 2 * 1024 * 1024;
const SMOKE_ID = 'com.yashsomani.birthdayautopilot.smoke';

const exactMatches = (value, expression) => [...value.matchAll(expression)];

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

export const verifyProductionSmokeMergedManifest = manifest => {
  assert(
    /<manifest\b[^>]*\bpackage="com\.yashsomani\.birthdayautopilot\.smoke"/u.test(
      manifest,
    ),
    'production-path smoke package ID is not isolated',
  );
  assert(
    /<application\b[^>]*\bandroid:name="com\.yashsomani\.birthdayautopilot\.smoke\.SmokeMainApplication"/u.test(
      manifest,
    ),
    'production-path smoke application host is missing',
  );
  assert(
    /<application\b[^>]*\bandroid:icon="@drawable\/smoke_launcher_icon"/u.test(
      manifest,
    ) &&
      /<application\b[^>]*\bandroid:networkSecurityConfig="@xml\/smoke_network_security_config"/u.test(
        manifest,
      ),
    'production-path smoke visual or loopback identity is missing',
  );
  assert(
    /android:usesCleartextTraffic="true"/u.test(manifest) &&
      /android:allowBackup="false"/u.test(manifest),
    'production-path smoke host policy is incomplete',
  );

  const activities = componentNames(manifest, 'activity');
  assert(
    activities.length === 1 &&
      activities[0] ===
        'com.yashsomani.birthdayautopilot.smoke.SmokeMainActivity',
    'production-path smoke manifest must contain only its isolated activity',
  );
  for (const element of ['provider', 'receiver', 'service']) {
    assert(
      componentNames(manifest, element).length === 0,
      `production-path smoke manifest contains a ${element}`,
    );
  }
  assert(
    JSON.stringify(permissions(manifest)) ===
      JSON.stringify(['android.permission.INTERNET']),
    'production-path smoke permissions are not limited to INTERNET',
  );
  assert(
    !/<queries\b/u.test(manifest),
    'production-path smoke contains queries',
  );
  assert(
    !/<uses-feature\b/u.test(manifest),
    'production-path smoke contains a hardware feature',
  );
  assert(
    !/(SEND_SMS|READ_PHONE_STATE|Firebase|GoogleApi|WorkManager|AppGraph|SmsSent|SmsDelivery|AutomationReconcile)/u.test(
      manifest,
    ),
    'production-path smoke contains a product-native marker',
  );
};

const manifestText = path => {
  const stat = statSync(path, { throwIfNoEntry: false });
  if (!stat?.isFile() || stat.size <= 0 || stat.size > MAXIMUM_MANIFEST_BYTES) {
    throw new Error('manifest must be a bounded regular file');
  }
  return readFileSync(path, 'utf8');
};

const parseArguments = args => {
  if (
    args.length !== 2 ||
    args[0] !== '--android-smoke-manifest' ||
    args[1] === undefined
  ) {
    throw new Error(
      'usage: verify-production-smoke-manifest.mjs --android-smoke-manifest <path>',
    );
  }
  return args[1];
};

if (process.argv[1]?.endsWith('verify-production-smoke-manifest.mjs')) {
  try {
    const manifestPath = parseArguments(process.argv.slice(2));
    verifyProductionSmokeMergedManifest(manifestText(manifestPath));
    process.stdout.write(
      `PASS ${SMOKE_ID} merged manifest is synthetic and fail-closed\n`,
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
