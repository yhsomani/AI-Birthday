import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
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
