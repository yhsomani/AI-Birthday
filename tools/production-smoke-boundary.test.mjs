import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import { verifyProductionSmokeMergedManifest } from './verify-production-smoke-manifest.mjs';
import { validateProductionSmokeFixture } from './validate-production-smoke-fixture.mjs';

const root = process.cwd();
const read = relative => readFileSync(path.join(root, relative), 'utf8');

const mergedManifest = (extra = '') =>
  `<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.yashsomani.birthdayautopilot.smoke">
    <uses-permission android:name="android.permission.INTERNET" />
    <application android:name="com.yashsomani.birthdayautopilot.smoke.SmokeMainApplication" android:allowBackup="false" android:icon="@drawable/smoke_launcher_icon" android:networkSecurityConfig="@xml/smoke_network_security_config" android:usesCleartextTraffic="true">
      <activity android:name="com.yashsomani.birthdayautopilot.smoke.SmokeMainActivity" />${extra}
    </application>
  </manifest>`;

test('production-path merged manifest verifier rejects privileged surfaces', () => {
  assert.doesNotThrow(() =>
    verifyProductionSmokeMergedManifest(mergedManifest()),
  );
  assert.throws(
    () =>
      verifyProductionSmokeMergedManifest(
        mergedManifest(
          '<service android:name="androidx.work.impl.background.systemjob.SystemJobService" />',
        ),
      ),
    /service/u,
  );
  assert.throws(
    () =>
      verifyProductionSmokeMergedManifest(
        mergedManifest().replace(
          '</manifest>',
          '<uses-permission android:name="android.permission.SEND_SMS" /></manifest>',
        ),
      ),
    /permissions/u,
  );
});

test('shared smoke projections are content-free and fail every intent closed', () => {
  const fixture = JSON.parse(
    read('e2e/production-smoke/production-smoke-projections.json'),
  );
  assert.doesNotThrow(() => validateProductionSmokeFixture(fixture));
  assert.throws(
    () =>
      validateProductionSmokeFixture({
        ...fixture,
        intentProblem: { code: 'temporarily-unavailable', kind: 'unsupported' },
      }),
    /intent outcome/u,
  );
  const privateFixture = structuredClone(fixture);
  privateFixture.platforms.android.account.accessToken = 'forbidden';
  assert.throws(
    () => validateProductionSmokeFixture(privateFixture),
    /private field accessToken/u,
  );
  const expectedKeys = [
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
  assert.equal(fixture.schemaVersion, 1);
  assert.deepEqual(fixture.intentProblem, {
    code: 'distribution-channel-unapproved',
    kind: 'unsupported',
  });
  for (const platform of ['android', 'ios']) {
    assert.deepEqual(
      Object.keys(fixture.platforms[platform]).sort(),
      expectedKeys,
    );
    assert.deepEqual(fixture.platforms[platform]['contacts:list'].items, []);
    assert.equal(fixture.platforms[platform]['contacts:list'].totalCount, 0);
    assert.deepEqual(fixture.platforms[platform]['activity:list'].items, [
      {
        id: 'smoke.activity.1',
        kind: 'settings-changed',
        occurredAt: '2026-07-12T00:00:00.000Z',
      },
    ]);
    assert.equal(
      fixture.platforms[platform]['privacy:inventory'].activityCount,
      1,
    );
    assert.equal(
      fixture.platforms[platform]['privacy:inventory'].localStorageBytes,
      0,
    );
  }
  assert.doesNotMatch(
    JSON.stringify(fixture),
    /"(?:accessToken|body|contactId|displayName|exactText|idToken|maskedPhone|phoneNumber|recipient|refreshToken)"/u,
  );
});

test('Android smoke loads production index with only the synthetic bridge', () => {
  const gradle = read('android/app/build.gradle');
  const app = read(
    'android/app/src/smoke/java/com/yashsomani/birthdayautopilot/smoke/SmokeMainApplication.kt',
  );
  const module = read(
    'android/app/src/smoke/java/com/yashsomani/birthdayautopilot/smoke/SmokeBirthdayNativeModule.kt',
  );
  const activity = read(
    'android/app/src/smoke/java/com/yashsomani/birthdayautopilot/smoke/SmokeMainActivity.kt',
  );
  assert.match(gradle, /applicationIdSuffix "\.smoke"/u);
  assert.match(gradle, /"smokeDebug"/u);
  assert.match(gradle, /environment == "smoke"/u);
  assert.match(
    gradle,
    /if \(environment == "smoke"\)[\s\S]*variantBuilder\.enableUnitTest = false[\s\S]*variantBuilder\.enableAndroidTest = false/u,
  );
  assert.match(gradle, /smoke\.assets\.srcDirs/u);
  assert.match(app, /jsMainModulePath = "index"/u);
  assert.match(app, /SmokeBirthdayNativePackage/u);
  assert.match(activity, /"BirthdayAutopilot"/u);
  assert.doesNotMatch(
    `${app}\n${activity}`,
    /AppGraph|AutomationScheduler|Firebase|Google|Permission|Sms|WorkManager/u,
  );
  assert.match(module, /class SmokeBirthdayNativeModule/u);
  assert.match(module, /distribution-channel-unapproved/u);
  assert.match(module, /executeUserIntent[\s\S]*?ignoredArguments/u);
  assert.doesNotMatch(
    module,
    /AppGraph|Firebase|Google|Http|Permission|Room|SmsManager|WorkManager/u,
  );
});

test('product bridges are absent from Smoke while fixture E2E stays unchanged', () => {
  assert.match(
    read(
      'android/app/src/e2e/java/com/yashsomani/birthdayautopilot/e2e/E2EMainApplication.kt',
    ),
    /jsMainModulePath = "e2e\/index"/u,
  );
  assert.match(read('e2e/index.js'), /BirthdayAutopilotE2E/u);
  assert.doesNotMatch(read('index.js'), /FixturePreview|BirthdayAutopilotE2E/u);
});

test('production-path Maestro flow is app-ID-bound and read-only', () => {
  const directory = path.join(root, 'e2e/maestro-production-smoke');
  const flows = readdirSync(directory)
    .filter(file => file.endsWith('.yaml'))
    .map(file => read(path.join('e2e/maestro-production-smoke', file)));
  assert.equal(flows.length, 1);
  assert.match(flows[0], /^appId: \$\{PRODUCTION_SMOKE_APP_ID\}$/mu);
  for (const id of [
    'live-app-shell',
    'live-home-screen',
    'live-activity-screen',
    'live-activity-detail-screen',
    'live-message-screen',
    'live-schedule-screen',
    'live-automation-screen',
    'live-attention-screen',
    'live-diagnostics-screen',
    'live-people-screen',
    'live-people-search',
    'live-people-filter-ready',
    'live-people-filter-all',
    'live-settings-screen',
    'live-privacy-screen',
    'live-help-legal-screen',
  ]) {
    assert.match(flows[0], new RegExp(id, 'u'));
  }
  const commandBlocks = flows[0].split(/\n(?=- )/u);
  const scrollBlocks = commandBlocks.filter(block =>
    block.startsWith('- scrollUntilVisible:'),
  );
  for (const id of [
    'live-home-activity',
    'live-home-attention',
    'live-settings-message',
    'live-settings-schedule',
    'live-settings-automation',
    'live-settings-privacy',
    'live-settings-help-legal',
  ]) {
    assert.equal(
      scrollBlocks.some(block => block.includes(`id: ${id}`)),
      true,
      `${id} must have its own scroll command`,
    );
  }
  for (const id of [
    'live-activity-smoke.activity.1',
    'live-home-attention',
    'live-people-filter-ready',
    'live-people-search',
    'live-people-filter-all',
    'live-settings-message',
    'live-settings-schedule',
    'live-settings-automation',
    'live-settings-privacy',
    'live-settings-help-legal',
    'live-help-diagnostics',
  ]) {
    assert.equal(
      commandBlocks.some(
        block => block.startsWith('- tapOn:') && block.includes(`id: ${id}`),
      ),
      true,
      `${id} must have its own tap command`,
    );
  }
  const peopleFlow = flows[0].slice(
    flows[0].indexOf('id: live-tab-people'),
    flows[0].indexOf('id: live-tab-settings'),
  );
  assert.match(
    peopleFlow,
    /id: live-people-filter-ready[\s\S]*No one is ready to set up[\s\S]*id: live-people-search[\s\S]*inputText: smoke-no-match[\s\S]*No people match this search[\s\S]*eraseText: 14[\s\S]*id: live-people-filter-all/u,
  );
  assert.doesNotMatch(
    peopleFlow,
    /id: live-people-(?:sync|select-page-ready|confirm-page-enrollment)|id: live-person-/u,
  );
  assert.doesNotMatch(
    flows[0],
    /live-settings-(?:activity|attention|diagnostics)/u,
  );
  assert.doesNotMatch(
    flows[0],
    /id: live-home-(?:message|automation|refresh)(?:\s|$)/u,
  );
  assert.match(
    flows[0],
    /id: live-settings-help-legal[\s\S]*id: live-help-legal-screen[\s\S]*id: live-help-diagnostics[\s\S]*id: live-diagnostics-screen[\s\S]*id: live-diagnostics-back[\s\S]*id: live-help-legal-screen[\s\S]*id: live-help-back[\s\S]*id: live-settings-screen/u,
  );
  assert.match(
    flows[0],
    /extendedWaitUntil:\s*\n\s+visible:\s*\n\s+id: live-app-shell\s*\n\s+timeout: 120000/u,
  );
  assert.equal((flows[0].match(/extendedWaitUntil:/gu) ?? []).length, 1);
  assert.doesNotMatch(
    flows[0],
    /openLink|runScript|evalScript|http:|https:|SEND_SMS|READ_PHONE_STATE/u,
  );
  const runner = read('tools/run-production-smoke.sh');
  assert.match(
    runner,
    /expected_app_id='com\.yashsomani\.birthdayautopilot\.smoke'/u,
  );
  assert.match(runner, /install-maestro\.sh/u);
  assert.match(runner, /MAESTRO_BIN overrides are forbidden/u);
  assert.doesNotMatch(
    runner,
    /birthdayautopilot\.(?:dev|e2e|lab|prod|staging)'/u,
  );
});

test('CI blocks unsafe history and exercises production-path smoke', () => {
  const ci = read('.github/workflows/ci.yml');
  const historyJob = ci.slice(
    ci.indexOf('  history-secrets:'),
    ci.indexOf('  backend-node22:'),
  );
  assert.match(
    historyJob,
    /history-secrets:[\s\S]*?fetch-depth: 0[\s\S]*?persist-credentials: false[\s\S]*?npm run security:history/u,
  );
  assert.match(historyJob, /test "\$\(node --version\)" = "v24\.18\.0"/u);
  assert.match(historyJob, /test "\$\(npm --version\)" = "11\.6\.0"/u);
  assert.doesNotMatch(
    historyJob,
    /continue-on-error|security:history[^\n]*\|\||history.*allowlist/iu,
  );
  assert.match(ci, /:app:compileSmokeDebugKotlin/u);
  assert.match(ci, /:app:assembleSmokeDebug/u);
  assert.match(ci, /:app:lintSmokeDebug/u);
  assert.match(ci, /:app:processSmokeDebugMainManifest/u);
  assert.match(ci, /npm run smoke:manifest:verify/u);
  assert.match(ci, /npm run smoke:android/u);
});
