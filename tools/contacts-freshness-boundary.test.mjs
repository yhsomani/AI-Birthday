import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');
const contractPath = 'contracts/contacts-freshness-policy-v1.json';
const contract = JSON.parse(read(contractPath));
const androidPolicy = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/PeopleDataFreshnessPolicy.kt',
);
const androidBridge = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/bridge/BirthdayNativeModule.kt',
);
const iosPolicy = read('ios/BirthdayAutopilot/Contacts/PeopleContracts.swift');
const iosStore = read('ios/BirthdayAutopilot/CompanionProtectedStore.swift');
const iosBridge = read('ios/BirthdayAutopilot/BirthdayNativeModule.swift');

test('shared Contacts freshness contract is bounded and uniquely identified', () => {
  assert.equal(contract.version, 'contacts-freshness-v1');
  assert.equal(contract.normalMaximumAgeMillis, 7 * 24 * 60 * 60 * 1_000);
  assert.equal(contract.automationMaximumAgeMillis, 30 * 24 * 60 * 60 * 1_000);
  assert.equal(contract.cases.length, 10);
  assert.equal(new Set(contract.cases.map(item => item.id)).size, 10);
  assert.deepEqual(
    new Set(contract.cases.map(item => item.expectedBand)),
    new Set(['NORMAL', 'STALE_WARNING', 'SAFETY_PAUSED', 'UNTRUSTED']),
  );
});

test('Android projections and readiness derive age from trusted monotonic time', () => {
  assert.match(androidPolicy, /NORMAL_MAXIMUM_AGE_MILLIS = 7L \* 24L/u);
  assert.match(androidPolicy, /MAXIMUM_UNATTENDED_AGE_MILLIS = 30L \* 24L/u);
  assert.match(androidPolicy, /lastSuccessMillis > trustedNowMillis/u);
  assert.match(
    androidPolicy,
    /Math\.subtractExact\(trustedNowMillis, lastSuccessMillis\)/u,
  );
  assert.match(
    androidBridge,
    /TrustedTimeEstimator\.estimate\([\s\S]*?SystemClock\.elapsedRealtime\(\)[\s\S]*?trustedBootCount\(\)/u,
  );
  assert.match(
    androidBridge,
    /PeopleDataFreshnessPolicy\.assess\([\s\S]*?trustedNowMillis\(account\.accountId\)/u,
  );
  assert.doesNotMatch(
    androidBridge,
    /when \(state\.freshness\)[\s\S]*?SyncFreshness\.FRESH/u,
  );
});

test(
  'production iOS freshness policy executes every shared boundary case',
  { skip: process.platform !== 'darwin' },
  t => {
    const directory = mkdtempSync(join(tmpdir(), 'birthday-ios-freshness-'));
    const binary = join(directory, 'contacts-freshness-tests');
    const moduleCache = join(directory, 'module-cache');
    mkdirSync(moduleCache);
    try {
      const compile = spawnSync(
        'xcrun',
        [
          'swiftc',
          'ios/BirthdayAutopilot/Contacts/PeopleContracts.swift',
          'tests/ios/ContactsFreshnessPolicyTests.swift',
          '-o',
          binary,
        ],
        {
          encoding: 'utf8',
          env: {
            ...process.env,
            CLANG_MODULE_CACHE_PATH: moduleCache,
            SWIFT_MODULECACHE_PATH: moduleCache,
          },
        },
      );
      if (
        compile.status !== 0 &&
        /SDK is not supported by the compiler|compiler\/SDK version mismatch/u.test(
          compile.stderr,
        )
      ) {
        t.skip('host Command Line Tools compiler and SDK do not match');
        return;
      }
      assert.equal(compile.status, 0, compile.stderr || compile.stdout);
      const run = spawnSync(binary, [contractPath], { encoding: 'utf8' });
      assert.equal(run.status, 0, run.stderr || run.stdout);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  },
);

test('iOS bridge uses recent authenticated time and never ages from device wall time alone', () => {
  assert.match(iosPolicy, /enum IOSContactsFreshnessPolicy/u);
  assert.match(iosPolicy, /age <= normalMaximumAge/u);
  assert.match(iosPolicy, /age <= companionMaximumAge/u);
  assert.match(
    iosPolicy,
    /receiptAge >= 0, receiptAge <= maximumObservationAge/u,
  );
  assert.match(
    iosStore,
    /IOSContactsFreshnessPolicy\.estimateTrustedNow\([\s\S]*?control\.trustedServerTime/u,
  );
  assert.match(iosStore, /trustedNow: trustedNow/u);
  assert.match(
    iosBridge,
    /contactsFreshnessAssessment\([\s\S]*?trustedNow: status\.trustedNow/u,
  );
  assert.match(iosBridge, /assessment\.allowsCompanionAction/u);
  assert.match(iosBridge, /assessment\.band == \.normal/u);
});
