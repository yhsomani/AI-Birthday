import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import {
  verifyE2EMergedManifest,
  verifyProdMergedManifest,
} from './verify-mobile-e2e-boundary.mjs';

const root = process.cwd();
const read = relative => readFileSync(path.join(root, relative), 'utf8');

const manifest = ({ e2e = true, extra = '' } = {}) =>
  e2e
    ? `<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.yashsomani.birthdayautopilot.e2e">
        <uses-permission android:name="android.permission.INTERNET" />
        <application android:name="com.yashsomani.birthdayautopilot.e2e.E2EMainApplication" android:allowBackup="false" android:icon="@drawable/e2e_launcher_icon" android:label="Birthday Autopilot E2E" android:networkSecurityConfig="@xml/e2e_network_security_config" android:usesCleartextTraffic="true">
          <activity android:name="com.yashsomani.birthdayautopilot.e2e.E2EMainActivity" />${extra}
        </application>
      </manifest>`
    : `<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.yashsomani.birthdayautopilot">
        <uses-permission android:name="android.permission.SEND_SMS" />
        <uses-permission android:name="android.permission.READ_PHONE_STATE" />
        <application><activity android:name="com.yashsomani.birthdayautopilot.MainActivity" />${extra}</application>
      </manifest>`;

test('merged manifest verifier accepts only the isolated E2E surface', () => {
  assert.doesNotThrow(() => verifyE2EMergedManifest(manifest()));
  assert.throws(
    () =>
      verifyE2EMergedManifest(
        manifest({
          extra:
            '<provider android:name="com.google.firebase.provider.FirebaseInitProvider" />',
        }),
      ),
    /provider/u,
  );
  assert.throws(
    () =>
      verifyE2EMergedManifest(
        manifest().replace(
          '</manifest>',
          '<uses-permission android:name="android.permission.SEND_SMS" /></manifest>',
        ),
      ),
    /permissions/u,
  );
});

test('production merged manifest rejects every fixture identity', () => {
  assert.doesNotThrow(() => verifyProdMergedManifest(manifest({ e2e: false })));
  assert.throws(
    () =>
      verifyProdMergedManifest(
        manifest({
          e2e: false,
          extra: '<activity android:name="E2EMainActivity" />',
        }),
      ),
    /fixture identity/u,
  );
});

test('production and E2E JavaScript entries are disjoint', () => {
  const productionEntry = read('index.js');
  const e2eEntry = read('e2e/index.js');
  const e2eRoot = read('e2e/src/DeviceE2EFixtureApp.tsx');
  assert.doesNotMatch(productionEntry, /e2e|FixturePreview/u);
  assert.match(e2eEntry, /BirthdayAutopilotE2E/u);
  assert.match(e2eRoot, /birthday-e2e-fixture-v1/u);
  assert.doesNotMatch(
    e2eRoot,
    /NativeBirthday|BirthdayNativeAdapter|CompanionNativeGateway|Firebase|Google|Sms/u,
  );
});

test('Android E2E build is debug-only and omits every product graph', () => {
  const gradle = read('android/app/build.gradle');
  const app = read(
    'android/app/src/e2e/java/com/yashsomani/birthdayautopilot/e2e/E2EMainApplication.kt',
  );
  const activity = read(
    'android/app/src/e2e/java/com/yashsomani/birthdayautopilot/e2e/E2EMainActivity.kt',
  );
  const overlay = read('android/app/src/e2e/AndroidManifest.xml');
  const networkSecurity = read(
    'android/app/src/e2e/res/xml/e2e_network_security_config.xml',
  );
  assert.match(gradle, /applicationIdSuffix "\.e2e"/u);
  assert.match(
    gradle,
    /\(environment == "e2e" \|\| environment == "smoke"\)[\s\S]*?variantBuilder\.buildType != "debug"/u,
  );
  assert.match(gradle, /requestedReactEntryFile == "e2e\/index\.js"/u);
  assert.match(
    gradle,
    /task\.name == "processE2eDebugGoogleServices"[\s\S]*?task\.enabled = false/u,
  );
  assert.doesNotMatch(
    gradle,
    /process(?:Dev|Staging|Lab|Prod).*GoogleServices[\s\S]*?enabled = false/u,
  );
  assert.match(app, /PackageList\(this\)\.packages/u);
  assert.match(app, /putString\("debug_http_host", LOOPBACK_METRO_HOST\)/u);
  assert.match(app, /LOOPBACK_METRO_HOST = "localhost:8081"/u);
  assert.doesNotMatch(
    app,
    /BirthdayNativePackage|AppGraph|AutomationScheduler|Firebase|Google|WorkManager|Sms/u,
  );
  assert.doesNotMatch(activity, /AppGraph|Firebase|Google|Sms|MessageUI/u);
  for (const value of [
    'SEND_SMS',
    'READ_PHONE_STATE',
    'AutomationReconcileReceiver',
    'SmsSentCallbackReceiver',
    'SmsDeliveryCallbackReceiver',
  ]) {
    assert.match(overlay, new RegExp(value, 'u'));
  }
  assert.match(
    networkSecurity,
    /<base-config cleartextTrafficPermitted="false"/u,
  );
  assert.match(
    networkSecurity,
    /<domain-config cleartextTrafficPermitted="true">[\s\S]*<domain includeSubdomains="false">localhost<\/domain>/u,
  );
  assert.doesNotMatch(
    networkSecurity,
    /10\.0\.2\.2|0\.0\.0\.0|includeSubdomains="true"/u,
  );
});

test('iOS E2E configuration is unsigned, simulator-only, and bridge-free', () => {
  const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
  const info = read('ios/BirthdayAutopilot/Info-E2E.plist');
  const appDelegate = read('ios/BirthdayAutopilot/AppDelegate.swift');
  const sceneDelegate = read('ios/BirthdayAutopilot/SceneDelegate.swift');
  assert.match(
    project,
    /PRODUCT_BUNDLE_IDENTIFIER = com\.yashsomani\.birthdayautopilot\.e2e/u,
  );
  assert.match(project, /SUPPORTED_PLATFORMS = iphonesimulator/u);
  assert.match(project, /CODE_SIGNING_ALLOWED = NO/u);
  assert.match(project, /ASSETCATALOG_COMPILER_APPICON_NAME = ""/u);
  assert.match(
    project,
    /SWIFT_ACTIVE_COMPILATION_CONDITIONS = "\$\(inherited\) DEBUG BIRTHDAY_E2E"/u,
  );
  assert.match(info, /<key>BirthdayE2EFixture<\/key>[\s\S]*?<true\/>/u);
  assert.doesNotMatch(
    info,
    /Firebase|Google|BGTaskScheduler|UIBackgroundModes|CFBundleURLTypes/u,
  );
  assert.match(appDelegate, /#if BIRTHDAY_E2E[\s\S]*?validateE2EHost/u);
  assert.match(
    appDelegate,
    /delegate\.dependencyProvider = E2EReactNativeDependencyProvider\(\)/u,
  );
  assert.match(
    appDelegate,
    /class E2EReactNativeDependencyProvider: RCTAppDependencyProvider[\s\S]*?override func moduleProviders\(\)[\s\S]*?\[:\]/u,
  );
  assert.match(appDelegate, /forBundleRoot: "e2e\/index"/u);
  assert.match(sceneDelegate, /withModuleName: "BirthdayAutopilotE2E"/u);
  for (const bridge of [
    'ios/BirthdayAutopilot/BirthdayNativeModuleBridge.mm',
    'ios/BirthdayAutopilot/CompanionMessageModuleBridge.m',
    'ios/BirthdayAutopilot/CompanionReminderModuleBridge.m',
  ]) {
    assert.match(
      read(bridge),
      /^#if !defined\(BIRTHDAY_E2E\) && !defined\(BIRTHDAY_SMOKE\)[\s\S]*#endif\s{2}\/\/ !BIRTHDAY_E2E && !BIRTHDAY_SMOKE\s*$/u,
    );
  }
});

test('iOS signing entitlements remain configuration-specific', () => {
  const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
  const debug = read(
    'ios/BirthdayAutopilot/BirthdayAutopilot-Debug.entitlements',
  );
  const release = read('ios/BirthdayAutopilot/BirthdayAutopilot.entitlements');
  const e2e = read('ios/BirthdayAutopilot/BirthdayAutopilot-E2E.entitlements');
  assert.match(project, /Debug[\s\S]*BirthdayAutopilot-Debug\.entitlements/u);
  assert.match(project, /Release[\s\S]*BirthdayAutopilot\.entitlements/u);
  assert.match(project, /E2E[\s\S]*BirthdayAutopilot-E2E\.entitlements/u);
  assert.match(debug, /<string>development<\/string>/u);
  assert.match(release, /<string>production<\/string>/u);
  assert.match(debug, /NSFileProtectionComplete/u);
  assert.match(release, /NSFileProtectionComplete/u);
  assert.doesNotMatch(e2e, /appattest|data-protection/u);
});

test('Maestro installer is version and checksum pinned without pipe execution', () => {
  const installer = read('tools/install-maestro.sh');
  const runner = read('tools/run-mobile-e2e.sh');
  assert.match(installer, /version='2\.6\.1'/u);
  assert.match(
    installer,
    /3440825f514f537c6a96bcf5de995780c2a4a7f83a43208fdc95d4f1fecfad3b/u,
  );
  assert.match(
    installer,
    /a133cb76b324bfcb6d018eb320174da0ed9ff03c7a7fa2c32eede0010dc069a9/u,
  );
  assert.match(installer, /shasum -a 256/u);
  assert.match(installer, /\.verified-release/u);
  assert.match(installer, /actual_binary_sha256/u);
  assert.match(installer, /receipt_binary_sha256/u);
  assert.match(installer, /calculate_tree_sha256/u);
  assert.match(installer, /receipt_tree_sha256/u);
  assert.match(installer, /! -type d ! -type f/u);
  assert.match(installer, /--max-filesize 419430400/u);
  assert.doesNotMatch(installer, /curl[^\n]*\|\s*(?:ba)?sh/u);
  assert.match(runner, /MAESTRO_BIN overrides are forbidden/u);
  assert.match(runner, /install-maestro\.sh/u);

  const printed = execFileSync(
    'sh',
    ['tools/install-maestro.sh', '--print-config'],
    {
      cwd: root,
      encoding: 'utf8',
    },
  );
  assert.equal(
    printed,
    'version=2.6.1\n' +
      'url=https://github.com/mobile-dev-inc/Maestro/releases/download/cli-2.6.1/maestro.zip\n' +
      'sha256=3440825f514f537c6a96bcf5de995780c2a4a7f83a43208fdc95d4f1fecfad3b\n' +
      'tree_sha256=a133cb76b324bfcb6d018eb320174da0ed9ff03c7a7fa2c32eede0010dc069a9\n',
  );
});

test('every Maestro flow is fixture-ID-bound and free of network/script escapes', () => {
  const flowDirectory = path.join(root, 'e2e/maestro');
  const flows = readdirSync(flowDirectory)
    .filter(file => file.endsWith('.yaml'))
    .map(file => read(path.join('e2e/maestro', file)));
  assert.equal(flows.length, 4);
  for (const flow of flows) {
    assert.match(flow, /^appId: \$\{E2E_APP_ID\}$/mu);
    assert.doesNotMatch(
      flow,
      /openLink|runScript|evalScript|copyTextFrom|http:|https:|SEND_SMS|READ_PHONE_STATE/u,
    );
  }
  const runner = read('tools/run-mobile-e2e.sh');
  assert.match(
    runner,
    /expected_app_id='com\.yashsomani\.birthdayautopilot\.e2e'/u,
  );
  assert.doesNotMatch(runner, /birthdayautopilot(?:\.lab|\.dev|\.staging)'/u);
});
