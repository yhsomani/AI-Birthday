import { lstatSync, readFileSync } from 'node:fs';
import process from 'node:process';

const MAXIMUM_FIXTURE_BYTES = 256 * 1024;
const EXPECTED_PROJECTION_KEYS = [
  'account',
  'activity:issues',
  'activity:list',
  'automation:approval',
  'automation:latest-test',
  'automation:policy-editor',
  'automation:sender-transfer-operation',
  'bootstrap',
  'contacts:list',
  'eligibility',
  'home',
  'messages:editor',
  'messages:next-composer-proposal',
  'notifications',
  'privacy:current-operation',
  'privacy:inventory',
  'privacy:public-resources',
  'readiness',
  'route',
  'setup',
];
const FORBIDDEN_PRIVATE_KEYS = new Set([
  'accessToken',
  'body',
  'contactId',
  'displayName',
  'exactText',
  'idToken',
  'maskedPhone',
  'phoneNumber',
  'recipient',
  'refreshToken',
]);
const CONTENT_FREE_ACTIVITY = {
  id: 'smoke.activity.1',
  kind: 'settings-changed',
  occurredAt: '2026-07-12T00:00:00.000Z',
};

const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};

const inspectPrivateKeys = value => {
  if (Array.isArray(value)) {
    value.forEach(inspectPrivateKeys);
    return;
  }
  if (value === null || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert(
      !FORBIDDEN_PRIVATE_KEYS.has(key),
      `private field ${key} is forbidden`,
    );
    inspectPrivateKeys(child);
  }
};

export const validateProductionSmokeFixture = value => {
  assert(
    value !== null && typeof value === 'object',
    'fixture must be an object',
  );
  assert(value.schemaVersion === 1, 'fixture schema version is invalid');
  assert(
    /^(?:0|[1-9][0-9]{0,18})$/u.test(value.revision),
    'fixture revision is invalid',
  );
  assert(
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/u.test(value.generatedAt),
    'fixture timestamp is invalid',
  );
  assert(
    JSON.stringify(value.intentProblem) ===
      JSON.stringify({
        kind: 'unsupported',
        code: 'distribution-channel-unapproved',
      }),
    'fixture intent outcome is not fixed and fail-closed',
  );
  assert(
    JSON.stringify(Object.keys(value.platforms ?? {}).sort()) ===
      JSON.stringify(['android', 'ios']),
    'fixture platforms are invalid',
  );
  for (const platform of ['android', 'ios']) {
    const projections = value.platforms[platform];
    assert(
      JSON.stringify(Object.keys(projections ?? {}).sort()) ===
        JSON.stringify(EXPECTED_PROJECTION_KEYS),
      `${platform} projection allowlist is invalid`,
    );
    assert(
      projections.bootstrap.capability.platform === platform &&
        projections.setup.step === 'complete' &&
        projections.setup.initialActivationCompleted === true,
      `${platform} bootstrap cannot open the live shell`,
    );
    assert(
      JSON.stringify(projections['contacts:list'].items) === '[]' &&
        projections['contacts:list'].totalCount === 0,
      `${platform} fixture contains contact records`,
    );
    assert(
      JSON.stringify(projections['activity:list'].items) ===
        JSON.stringify([CONTENT_FREE_ACTIVITY]),
      `${platform} fixture activity is not the exact content-free route record`,
    );
    for (const count of [
      'approvalCount',
      'enabledRecipientCount',
      'localContactCount',
      'localStorageBytes',
      'templateCount',
    ]) {
      assert(
        projections['privacy:inventory'][count] === 0,
        `${platform} ${count} must be zero`,
      );
    }
    assert(
      projections['privacy:inventory'].activityCount === 1,
      `${platform} activityCount must bind the content-free route record`,
    );
  }
  inspectPrivateKeys(value);
};

export const validateProductionSmokeFixtureFile = path => {
  const stat = lstatSync(path, { throwIfNoEntry: false });
  assert(
    stat?.isFile() && !stat.isSymbolicLink(),
    'fixture must be a regular non-symlink file',
  );
  assert(
    stat.size > 0 && stat.size <= MAXIMUM_FIXTURE_BYTES,
    'fixture size is invalid',
  );
  let value;
  try {
    value = JSON.parse(readFileSync(path, 'utf8'));
  } catch {
    throw new Error('fixture JSON is malformed');
  }
  validateProductionSmokeFixture(value);
};

if (process.argv[1]?.endsWith('validate-production-smoke-fixture.mjs')) {
  try {
    if (process.argv.length !== 3) {
      throw new Error('exactly one fixture path is required');
    }
    validateProductionSmokeFixtureFile(process.argv[2]);
    process.stdout.write(
      'PASS production-path smoke fixture is content-free\n',
    );
  } catch (error) {
    process.stderr.write(
      `FAIL ${error instanceof Error ? error.message : 'fixture is invalid'}\n`,
    );
    process.exitCode = 1;
  }
}
