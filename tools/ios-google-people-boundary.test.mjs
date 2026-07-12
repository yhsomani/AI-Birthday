import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { validateIOSGoogleConfigInventory } from './validate-ios-google-configs.mjs';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

test('iOS identity and People dependencies are exact and native-only', () => {
  const podfile = read('ios/Podfile');
  assert.match(podfile, /pod 'FirebaseCore', '12\.15\.0'/u);
  assert.match(podfile, /pod 'FirebaseAuth', '12\.15\.0'/u);
  assert.match(podfile, /pod 'FirebaseAppCheck', '12\.15\.0'/u);
  assert.match(podfile, /pod 'FirebaseFunctions', '12\.15\.0'/u);
  assert.match(podfile, /pod 'GoogleSignIn', '9\.1\.0'/u);

  const sources = [
    'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
    'ios/BirthdayAutopilot/Contacts/PeopleContracts.swift',
    'ios/BirthdayAutopilot/Contacts/IOSPeopleAuthorizationGateway.swift',
    'ios/BirthdayAutopilot/Contacts/IOSPeopleSyncCoordinator.swift',
    'ios/BirthdayAutopilot/Contacts/PeopleAPIClient.swift',
    'ios/BirthdayAutopilot/Database/CompanionPeopleStore.swift',
  ]
    .map(read)
    .join('\n');
  assert.match(
    sources,
    /https:\/\/www\.googleapis\.com\/auth\/contacts\.readonly/u,
  );
  assert.doesNotMatch(sources, /grantOfflineAccess|serverClientID\s*:/u);
  assert.ok((sources.match(/people\.googleapis\.com/g) ?? []).length >= 2);
  assert.doesNotMatch(sources, /print\s*\(|NSLog\s*\(|os_log|Logger\s*\(/u);
  assert.doesNotMatch(sources, /sendMultipartTextMessage|SmsManager|SEND_SMS/u);

  const bridge = read('ios/BirthdayAutopilot/BirthdayNativeModule.swift');
  assert.doesNotMatch(
    bridge,
    /"(?:accessToken|idToken|firebaseUID|googleSubject|resourceName|contactSourceId|rawValue|birthdayLabel)"/u,
  );
});

test('iOS build configuration is explicit, App Attest production-only, and secrets are ignored', () => {
  const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
  const info = read('ios/BirthdayAutopilot/Info.plist');
  const debugInfo = read('ios/BirthdayAutopilot/Info-Debug.plist');
  const privacy = read('ios/BirthdayAutopilot/PrivacyInfo.xcprivacy');
  const entitlements = read(
    'ios/BirthdayAutopilot/BirthdayAutopilot.entitlements',
  );
  const gitignore = read('.gitignore');
  assert.match(project, /BIRTHDAY_FIREBASE_ENV = dev;/u);
  assert.match(project, /BIRTHDAY_FIREBASE_ENV = prod;/u);
  assert.match(project, /BIRTHDAY_FIREBASE_CONFIG_REQUIRED = YES;/u);
  assert.match(project, /com\.yashsomani\.birthdayautopilot\.dev/u);
  assert.match(project, /TARGETED_DEVICE_FAMILY = 1;/u);
  assert.match(project, /BIRTHDAY_PRIVACY_REVIEW_APPROVED = NO;/u);
  assert.match(info, /BirthdayExpectedFirebaseProjectID/u);
  assert.match(info, /BirthdayGoogleReversedClientID/u);
  assert.match(entitlements, /appattest-environment[\s\S]*production/u);
  assert.match(info, /NSAllowsLocalNetworking<\/key>\s*<false\/>/u);
  assert.match(debugInfo, /NSAllowsLocalNetworking<\/key><true\/>/u);
  assert.doesNotMatch(privacy, /NSPrivacyCollectedDataTypes/u);
  assert.doesNotMatch(
    project + info,
    /AppCheckDebugProvider|GIDServerClientID/u,
  );
  assert.match(gitignore, /ios\/Config\/\*\/GoogleService-Info\.plist/u);
  const configPhase = project.slice(
    project.indexOf('/* Validate and Copy Google Configuration */ = {'),
    project.indexOf('/* [CP] Check Pods Manifest.lock */ = {'),
  );
  assert.match(configPhase, /alwaysOutOfDate = 1;/u);
  assert.match(configPhase, /"\$\(SRCROOT\)\/Config"/u);
  assert.doesNotMatch(
    configPhase,
    /Config\/\$\(BIRTHDAY_FIREBASE_ENV\)\/GoogleService-Info\.plist/u,
  );
});

test(
  'Google config copy gate accepts exact config and rejects missing or cross-bundle config',
  { skip: process.platform !== 'darwin' },
  () => {
    const directory = mkdtempSync(join(tmpdir(), 'birthday-ios-config-'));
    const sourceRoot = join(directory, 'ios');
    const configDirectory = join(sourceRoot, 'Config', 'prod');
    const target = join(directory, 'build');
    mkdirSync(configDirectory, { recursive: true });
    mkdirSync(target, { recursive: true });
    const clientID = '123456789-abcdef.apps.googleusercontent.com';
    const reversed = 'com.googleusercontent.apps.123456789-abcdef';
    const plist = bundleID => `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>API_KEY</key><string>test-public-config-key</string>
<key>BUNDLE_ID</key><string>${bundleID}</string>
<key>CLIENT_ID</key><string>${clientID}</string>
<key>GCM_SENDER_ID</key><string>123456789</string>
<key>GOOGLE_APP_ID</key><string>1:123456789:ios:abcdef</string>
<key>PROJECT_ID</key><string>birthday-autopilot-prod</string>
<key>REVERSED_CLIENT_ID</key><string>${reversed}</string>
</dict></plist>`;
    const config = join(configDirectory, 'GoogleService-Info.plist');
    writeFileSync(config, plist('com.yashsomani.birthdayautopilot'));
    const environment = {
      ...process.env,
      BIRTHDAY_FIREBASE_CONFIG_REQUIRED: 'YES',
      BIRTHDAY_FIREBASE_ENV: 'prod',
      BIRTHDAY_FIREBASE_PROJECT_ID: 'birthday-autopilot-prod',
      BIRTHDAY_GOOGLE_REVERSED_CLIENT_ID: reversed,
      BIRTHDAY_PRIVACY_REVIEW_APPROVED: 'YES',
      PRODUCT_BUNDLE_IDENTIFIER: 'com.yashsomani.birthdayautopilot',
      SRCROOT: sourceRoot,
      TARGET_BUILD_DIR: target,
      UNLOCALIZED_RESOURCES_FOLDER_PATH: 'BirthdayAutopilot.app',
    };
    const script = fileURLToPath(
      new URL('../ios/scripts/copy-google-config.sh', import.meta.url),
    );
    execFileSync(script, [], {
      env: environment,
      stdio: 'pipe',
    });

    const privacyBlocked = spawnSync(script, [], {
      env: { ...environment, BIRTHDAY_PRIVACY_REVIEW_APPROVED: 'NO' },
      encoding: 'utf8',
    });
    assert.notEqual(privacyBlocked.status, 0);
    assert.match(
      privacyBlocked.stderr,
      /privacy\/App Store declaration evidence/u,
    );

    writeFileSync(config, plist('com.example.cross.tier'));
    const mismatch = spawnSync(script, [], {
      env: environment,
      encoding: 'utf8',
    });
    assert.notEqual(mismatch.status, 0);
    assert.doesNotMatch(
      `${mismatch.stdout}${mismatch.stderr}`,
      /test-public-config-key/u,
    );

    const missingRoot = join(directory, 'missing-ios');
    mkdirSync(missingRoot, { recursive: true });
    const missing = spawnSync(script, [], {
      env: { ...environment, SRCROOT: missingRoot },
      encoding: 'utf8',
    });
    assert.notEqual(missing.status, 0);
  },
);

test('tier inventory rejects a Firebase project reused across iOS tiers', () => {
  const directory = mkdtempSync(join(tmpdir(), 'birthday-ios-tier-inventory-'));
  const client = tier => `123456789-${tier}.apps.googleusercontent.com`;
  for (const [tier, bundle] of Object.entries({
    dev: 'com.yashsomani.birthdayautopilot.dev',
    staging: 'com.yashsomani.birthdayautopilot.staging',
  })) {
    const target = join(directory, tier);
    mkdirSync(target, { recursive: true });
    const clientID = client(tier);
    writeFileSync(
      join(target, 'GoogleService-Info.plist'),
      `<?xml version="1.0"?><plist version="1.0"><dict>
<key>API_KEY</key><string>key-${tier}</string><key>BUNDLE_ID</key><string>${bundle}</string>
<key>CLIENT_ID</key><string>${clientID}</string><key>GCM_SENDER_ID</key><string>123</string>
<key>GOOGLE_APP_ID</key><string>app-${tier}</string><key>PROJECT_ID</key><string>shared-project</string>
<key>REVERSED_CLIENT_ID</key><string>${clientID
        .split('.')
        .reverse()
        .join('.')}</string>
</dict></plist>`,
    );
  }
  assert.ok(
    validateIOSGoogleConfigInventory(directory).some(error =>
      error.includes('PROJECT_ID is shared across tiers'),
    ),
  );
});

test('checked-in iOS tier inventory has no generic or cross-tier configuration', () => {
  assert.deepEqual(
    validateIOSGoogleConfigInventory(
      fileURLToPath(new URL('../ios/Config', import.meta.url)),
    ),
    [],
  );
});
