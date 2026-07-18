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
    'privacy:latest-deletion-receipt',
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

test('iOS Smoke is unsigned, simulator-only, production-entry, and startup-free', () => {
  const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
  const scheme = read(
    'ios/BirthdayAutopilot.xcodeproj/xcshareddata/xcschemes/BirthdayAutopilotProductionSmoke.xcscheme',
  );
  const info = read('ios/BirthdayAutopilot/Info-Smoke.plist');
  const appDelegate = read('ios/BirthdayAutopilot/AppDelegate.swift');
  const sceneDelegate = read('ios/BirthdayAutopilot/SceneDelegate.swift');
  const bridge = read(
    'ios/BirthdayAutopilot/BirthdayNativeSmokeModuleBridge.mm',
  );
  const bundleScript = read('ios/scripts/bundle-react-native.sh');
  const googleScript = read('ios/scripts/copy-google-config.sh');
  const fixtureScript = read('ios/scripts/copy-production-smoke-fixture.sh');
  assert.match(project, /BIRTHDAY_PRODUCTION_SMOKE = YES/u);
  assert.match(project, /BIRTHDAY_SMOKE=1/u);
  assert.match(
    project,
    /PRODUCT_BUNDLE_IDENTIFIER = com\.yashsomani\.birthdayautopilot\.smoke/u,
  );
  assert.match(project, /SUPPORTED_PLATFORMS = iphonesimulator/u);
  assert.match(project, /CODE_SIGNING_ALLOWED = NO/u);
  assert.match(project, /BirthdayAutopilot-Smoke\.entitlements/u);
  assert.match(scheme, /buildConfiguration = "Smoke"/u);
  assert.match(scheme, /buildForArchiving = "NO"/u);
  assert.match(info, /BirthdayProductionPathSmoke/u);
  assert.doesNotMatch(
    info,
    /Firebase|Google|BGTaskScheduler|UIBackgroundModes|CFBundleURLTypes/u,
  );
  assert.match(appDelegate, /#elseif BIRTHDAY_SMOKE/u);
  assert.match(appDelegate, /RCTAppDependencyProvider\(\)/u);
  assert.match(sceneDelegate, /withModuleName: "BirthdayAutopilot"/u);
  assert.match(bridge, /^#ifdef BIRTHDAY_SMOKE/mu);
  assert.match(bridge, /RCT_EXPORT_MODULE\(BirthdayNative\)/u);
  assert.match(bridge, /distribution-channel-unapproved/u);
  assert.doesNotMatch(
    bridge,
    /^#import[^\n]*(?:Firebase|Google|MessageUI|UserNotifications)|ProtectedStore|WorkflowEngine/mu,
  );
  assert.match(
    bundleScript,
    /elif \[ "\$\{CONFIGURATION:-\}" = "Smoke" \]; then[\s\S]*ENTRY_FILE='index\.js'/u,
  );
  assert.match(
    googleScript,
    /BIRTHDAY_PRODUCTION_SMOKE[\s\S]*production-path smoke must never have a Firebase configuration/u,
  );
  assert.match(
    fixtureScript,
    /validate-production-smoke-fixture\.mjs[\s\S]*\/usr\/bin\/ditto/u,
  );
});

test('product bridges are absent from Smoke while fixture E2E stays unchanged', () => {
  for (const bridge of [
    'ios/BirthdayAutopilot/BirthdayNativeModuleBridge.mm',
    'ios/BirthdayAutopilot/CompanionMessageModuleBridge.m',
    'ios/BirthdayAutopilot/CompanionReminderModuleBridge.m',
  ]) {
    assert.match(
      read(bridge),
      /^#if !defined\(BIRTHDAY_E2E\) && !defined\(BIRTHDAY_SMOKE\)/u,
    );
  }
  assert.match(
    read(
      'android/app/src/e2e/java/com/yashsomani/birthdayautopilot/e2e/E2EMainApplication.kt',
    ),
    /jsMainModulePath = "e2e\/index"/u,
  );
  assert.match(read('e2e/index.js'), /BirthdayAutopilotE2E/u);
  assert.doesNotMatch(read('index.js'), /FixturePreview|BirthdayAutopilotE2E/u);
});

test('production-path Maestro flow is app-ID-bound and navigation-only', () => {
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
    'live-automation-screen',
    'live-attention-screen',
    'live-diagnostics-screen',
    'live-people-screen',
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
    'live-home-message',
    'live-home-automation',
  ]) {
    assert.equal(
      scrollBlocks.some(block => block.includes(`id: ${id}`)),
      true,
      `${id} must have its own scroll command`,
    );
  }
  for (const id of [
    'live-activity-smoke.activity.1',
    'live-settings-privacy',
    'live-settings-help-legal',
    'live-settings-attention',
    'live-settings-diagnostics',
  ]) {
    assert.equal(
      commandBlocks.some(
        block => block.startsWith('- tapOn:') && block.includes(`id: ${id}`),
      ),
      true,
      `${id} must have its own tap command`,
    );
  }
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

test('CI blocks unsafe history and exercises both production-path smoke hosts', () => {
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
  assert.match(ci, /-scheme BirthdayAutopilotProductionSmoke/u);
  assert.match(ci, /npm run smoke:ios/u);
  assert.match(ci, /Info-Smoke\.plist/u);
  assert.match(ci, /BirthdayAutopilot-Smoke\.entitlements/u);
  assert.match(ci, /copy-production-smoke-fixture\.sh/u);
  assert.match(ci, /BirthdayAutopilotProductionSmoke\.xcscheme/u);
});
