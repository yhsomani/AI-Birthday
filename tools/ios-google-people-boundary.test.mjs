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
  const copyGate = read('ios/scripts/copy-google-config.sh');
  const workflow = read('.github/workflows/ci.yml');
  const info = read('ios/BirthdayAutopilot/Info.plist');
  const debugInfo = read('ios/BirthdayAutopilot/Info-Debug.plist');
  const appDelegate = read('ios/BirthdayAutopilot/AppDelegate.swift');
  const sceneDelegate = read('ios/BirthdayAutopilot/SceneDelegate.swift');
  const privacy = read('ios/BirthdayAutopilot/PrivacyInfo.xcprivacy');
  const entitlements = read(
    'ios/BirthdayAutopilot/BirthdayAutopilot.entitlements',
  );
  const gitignore = read('.gitignore');
  assert.match(project, /BIRTHDAY_FIREBASE_ENV = dev;/u);
  assert.match(project, /BIRTHDAY_FIREBASE_ENV = prod;/u);
  assert.doesNotMatch(
    project + info + debugInfo,
    /BIRTHDAY_FIREBASE_CONFIG_REQUIRED|BirthdayGoogleConfigurationRequired/u,
  );
  assert.match(project, /com\.yashsomani\.birthdayautopilot\.dev/u);
  assert.match(project, /TARGETED_DEVICE_FAMILY = 1;/u);
  assert.match(project, /BIRTHDAY_PRIVACY_REVIEW_APPROVED = NO;/u);
  assert.match(info, /BirthdayExpectedFirebaseProjectID/u);
  assert.match(info, /BirthdayGoogleReversedClientID/u);
  assert.match(entitlements, /appattest-environment[\s\S]*production/u);
  assert.match(info, /NSAllowsLocalNetworking<\/key>\s*<false\/>/u);
  assert.match(debugInfo, /NSAllowsLocalNetworking<\/key><true\/>/u);
  for (const plist of [info, debugInfo]) {
    assert.match(plist, /UIApplicationSceneManifest/u);
    assert.match(
      plist,
      /UIApplicationSupportsMultipleScenes[\s\S]*?<false\/>/u,
    );
    assert.match(plist, /\$\(PRODUCT_MODULE_NAME\)\.SceneDelegate/u);
  }
  assert.match(appDelegate, /configurationForConnecting/u);
  assert.match(
    appDelegate,
    /IOSPeopleBackgroundRefreshCoordinator\.shared\.registerAtLaunch\(\)/u,
  );
  assert.ok(
    appDelegate.indexOf('registerAtLaunch()') <
      appDelegate.indexOf('configureAtLaunch()'),
  );
  assert.match(
    appDelegate,
    /if application\.isProtectedDataAvailable[\s\S]*?configureAtLaunch/u,
  );
  assert.match(
    appDelegate,
    /applicationProtectedDataDidBecomeAvailable[\s\S]*?retryConfigurationAfterProtectedDataBecomesAvailable/u,
  );
  assert.match(sceneDelegate, /UIWindow\(windowScene: windowScene\)/u);
  assert.match(sceneDelegate, /connectionOptions\.urlContexts\.count == 1/u);
  assert.match(sceneDelegate, /scene\(_ scene: UIScene, openURLContexts/u);
  assert.match(
    sceneDelegate,
    /urlContexts\.count > 1[\s\S]*?rejectAmbiguousOpenURLs/u,
  );
  assert.match(
    sceneDelegate,
    /URLContexts\.count > 1[\s\S]*?rejectAmbiguousOpenURLs/u,
  );
  assert.match(
    sceneDelegate,
    /sceneDidEnterBackground[\s\S]*?scheduleForConnectedSession/u,
  );
  assert.match(
    sceneDelegate,
    /IOSGeminiSuggestionGateway\.shared\.refreshOperationalGateInBackground/u,
  );
  assert.equal(
    (project.match(/SceneDelegate\.swift in Sources/gu) ?? []).length,
    2,
  );
  for (const plist of [info, debugInfo]) {
    assert.match(
      plist,
      /BGTaskSchedulerPermittedIdentifiers[\s\S]*?com\.yashsomani\.birthdayautopilot\.people-refresh/u,
    );
    assert.match(plist, /UIBackgroundModes[\s\S]*?<string>fetch<\/string>/u);
  }
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
  assert.match(copyGate, /BIRTHDAY_IOS_SIMULATOR_COMPILE_SMOKE/u);
  assert.match(copyGate, /"\$\{ACTION:-\}" = "build"/u);
  assert.match(copyGate, /"\$\{PLATFORM_NAME:-\}" = "iphonesimulator"/u);
  assert.match(
    copyGate,
    /"\$\{EFFECTIVE_PLATFORM_NAME:-\}" = "-iphonesimulator"/u,
  );
  assert.match(copyGate, /"\$\{CODE_SIGNING_ALLOWED:-\}" = "NO"/u);
  assert.match(copyGate, /"\$\{CODE_SIGNING_REQUIRED:-\}" = "NO"/u);
  assert.doesNotMatch(
    copyGate,
    /required="\$\{BIRTHDAY_FIREBASE_CONFIG_REQUIRED/u,
  );
  assert.equal(
    (workflow.match(/BIRTHDAY_IOS_SIMULATOR_COMPILE_SMOKE=YES/gu) ?? []).length,
    4,
  );
  assert.doesNotMatch(workflow, /BIRTHDAY_FIREBASE_CONFIG_REQUIRED=NO/u);
});

test('iOS People refresh is system-scheduled, non-interactive, bounded, and fenced', () => {
  const policy = read(
    'ios/BirthdayAutopilot/Contacts/IOSPeopleBackgroundRefreshPolicy.swift',
  );
  const coordinator = read(
    'ios/BirthdayAutopilot/Contacts/IOSPeopleBackgroundRefreshCoordinator.swift',
  );
  const sync = read(
    'ios/BirthdayAutopilot/Contacts/IOSPeopleSyncCoordinator.swift',
  );
  const identity = read(
    'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
  );
  const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');

  assert.match(coordinator, /BGAppRefreshTaskRequest/u);
  assert.match(coordinator, /BGTaskScheduler\.shared\.register/u);
  assert.match(coordinator, /interactiveAuthorization: false/u);
  assert.doesNotMatch(
    coordinator,
    /interactiveAuthorization: true|\b(?:continueWithGoogle|addScopes|requestAuthorization)\s*\(|MFMessageComposeViewController/u,
  );
  assert.match(
    coordinator,
    /expirationHandler[\s\S]*?invalidateOutstandingSync/u,
  );
  assert.match(
    coordinator,
    /reconcileAfterPeopleSync[\s\S]*?setTaskCompleted/u,
  );
  assert.match(
    coordinator,
    /guard let expectedBinding = identity\.exactSessionBinding\(\)[\s\S]*?case \.connecting = identity\.state[\s\S]*?submitRequest[\s\S]*?task\.setTaskCompleted\(success: false\)/u,
  );
  assert.match(
    policy,
    /authorizationRequired[\s\S]*?repeatedUnauthorized[\s\S]*?return nil/u,
  );
  assert.match(policy, /minimumRateLimitDelay/u);
  assert.match(
    sync,
    /case \.completed = outcome[\s\S]*?scheduleForConnectedSession/u,
  );
  assert.match(
    identity,
    /case \.connected:[\s\S]*?scheduleForConnectedSession[\s\S]*?case \.reconnectRequired[\s\S]*?cancelForDisconnectedSession/u,
  );
  assert.equal(
    (
      project.match(/IOSPeopleBackgroundRefreshPolicy\.swift in Sources/gu) ??
      []
    ).length,
    2,
  );
  assert.equal(
    (
      project.match(
        /IOSPeopleBackgroundRefreshCoordinator\.swift in Sources/gu,
      ) ?? []
    ).length,
    2,
  );
  assert.equal(
    (
      project.match(
        /IOSPeopleBackgroundRefreshPolicyTests\.swift in Sources/gu,
      ) ?? []
    ).length,
    2,
  );
});

test('cold Google callbacks are held once in memory until exact configuration is ready', () => {
  const configuration = read(
    'ios/BirthdayAutopilot/Identity/IOSGoogleConfiguration.swift',
  );
  const identity = read(
    'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
  );

  assert.match(configuration, /func declaredCallbackScheme/u);
  assert.match(
    configuration,
    /BirthdayGoogleReversedClientID[\s\S]*?bundleURLSchemes/u,
  );
  assert.match(identity, /private var pendingOpenURL: URL\?/u);
  assert.doesNotMatch(
    identity,
    /UserDefaults.*pendingOpenURL|Keychain.*pendingOpenURL/u,
  );
  assert.match(
    identity,
    /pendingOpenURL == nil[\s\S]*?rejectedAmbiguousPendingOpenURL = true/u,
  );
  assert.match(
    identity,
    /func rejectAmbiguousOpenURLs[\s\S]*?pendingOpenURL = nil[\s\S]*?rejectedAmbiguousPendingOpenURL = true/u,
  );
  assert.match(
    identity,
    /consumePendingOpenURL\(expectedScheme: configuration\.reversedClientID\)/u,
  );
  assert.match(
    identity,
    /guard configuration != nil, googleIdentityAppCheckReady else \{[\s\S]*?pendingOpenURL = url/u,
  );
  assert.match(
    identity,
    /consumePendingOpenURL[\s\S]*?caseInsensitiveCompare\(expectedScheme\)[\s\S]*?GIDSignIn\.sharedInstance\.handle/u,
  );
  assert.match(
    identity,
    /case \.reconnectRequired, \.signedOut, \.unavailable:[\s\S]*?clearPendingOpenURL/u,
  );
});

test('locked cold launch never repairs protected People data and retries after unlock', () => {
  const appDelegate = read('ios/BirthdayAutopilot/AppDelegate.swift');
  const identity = read(
    'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
  );
  const peopleStore = read(
    'ios/BirthdayAutopilot/Database/CompanionPeopleStore.swift',
  );
  const lifecycle = read('ios/BirthdayAutopilot/CompanionReminderModule.swift');

  assert.match(
    peopleStore,
    /enum IOSPeopleStorePreparationResult[\s\S]*?case protectedDataUnavailable/u,
  );
  assert.match(
    peopleStore,
    /catch IOSPeopleStoreError\.protectedDataUnavailable[\s\S]*?result = \.protectedDataUnavailable/u,
  );
  assert.match(
    peopleStore,
    /case \.protectedDataUnavailable, \.storageUnavailable:[\s\S]*?throw error[\s\S]*?case \.corruptSnapshot, \.keyMissing:/u,
  );
  assert.match(
    peopleStore,
    /errSecInteractionNotAllowed \|\| status == errSecNotAvailable[\s\S]*?protectedDataUnavailable/u,
  );
  assert.match(
    identity,
    /case \.protectedDataUnavailable:[\s\S]*?configured = false[\s\S]*?protectedDataRaceRetryUsed/u,
  );
  assert.match(
    lifecycle,
    /UIApplication\.protectedDataDidBecomeAvailableNotification/u,
  );
  assert.match(
    appDelegate,
    /retryConfigurationAfterProtectedDataBecomesAvailable/u,
  );
});

test(
  'Google config copy gate accepts exact config and blocks unsigned release history',
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
      ACTION: 'build',
      BIRTHDAY_FIREBASE_ENV: 'prod',
      BIRTHDAY_FIREBASE_PROJECT_ID: 'birthday-autopilot-prod',
      BIRTHDAY_GOOGLE_REVERSED_CLIENT_ID: reversed,
      BIRTHDAY_PRIVACY_REVIEW_APPROVED: 'NO',
      CODE_SIGNING_ALLOWED: 'NO',
      CODE_SIGNING_REQUIRED: 'NO',
      CONFIGURATION: 'Debug',
      DEPLOYMENT_LOCATION: 'NO',
      EFFECTIVE_PLATFORM_NAME: '-iphonesimulator',
      PLATFORM_NAME: 'iphonesimulator',
      PRODUCT_BUNDLE_IDENTIFIER: 'com.yashsomani.birthdayautopilot',
      SDK_NAME: 'iphonesimulator26.5',
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

    const releaseEnvironment = {
      ...environment,
      ACTION: 'install',
      BIRTHDAY_PRIVACY_REVIEW_APPROVED: 'YES',
      CODE_SIGNING_ALLOWED: 'YES',
      CODE_SIGNING_REQUIRED: 'YES',
      CONFIGURATION: 'Release',
      DEPLOYMENT_LOCATION: 'YES',
      EFFECTIVE_PLATFORM_NAME: '-iphoneos',
      PLATFORM_NAME: 'iphoneos',
      SDK_NAME: 'iphoneos26.5',
    };
    const privacyBlocked = spawnSync(script, [], {
      env: { ...releaseEnvironment, BIRTHDAY_PRIVACY_REVIEW_APPROVED: 'NO' },
      encoding: 'utf8',
    });
    assert.notEqual(privacyBlocked.status, 0);
    assert.match(
      privacyBlocked.stderr,
      /privacy\/App Store declaration evidence/u,
    );

    const historyBlocked = spawnSync(script, [], {
      env: releaseEnvironment,
      encoding: 'utf8',
    });
    assert.notEqual(historyBlocked.status, 0);
    assert.match(
      historyBlocked.stderr,
      /signed iOS protected-store release history is required/u,
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
      env: {
        ...environment,
        BIRTHDAY_FIREBASE_CONFIG_REQUIRED: 'NO',
        SRCROOT: missingRoot,
      },
      encoding: 'utf8',
    });
    assert.notEqual(missing.status, 0);

    const unsignedSimulatorSmoke = {
      ...environment,
      ACTION: 'build',
      BIRTHDAY_FIREBASE_CONFIG_REQUIRED: 'NO',
      BIRTHDAY_FIREBASE_ENV: 'dev',
      BIRTHDAY_FIREBASE_PROJECT_ID: 'birthday-autopilot-dev',
      BIRTHDAY_IOS_SIMULATOR_COMPILE_SMOKE: 'YES',
      BIRTHDAY_PRIVACY_REVIEW_APPROVED: 'NO',
      CODE_SIGNING_ALLOWED: 'NO',
      CODE_SIGNING_REQUIRED: 'NO',
      CONFIGURATION: 'Debug',
      DEPLOYMENT_LOCATION: 'NO',
      EFFECTIVE_PLATFORM_NAME: '-iphonesimulator',
      PLATFORM_NAME: 'iphonesimulator',
      PRODUCT_BUNDLE_IDENTIFIER: 'com.yashsomani.birthdayautopilot.dev',
      SDK_NAME: 'iphonesimulator26.5',
      SRCROOT: missingRoot,
    };
    const allowedSmoke = spawnSync(script, [], {
      env: unsignedSimulatorSmoke,
      encoding: 'utf8',
    });
    assert.equal(allowedSmoke.status, 0, allowedSmoke.stderr);

    for (const overrides of [
      { BIRTHDAY_IOS_SIMULATOR_COMPILE_SMOKE: 'NO' },
      {
        ACTION: 'install',
        CONFIGURATION: 'Release',
        DEPLOYMENT_LOCATION: 'YES',
      },
      {
        CODE_SIGNING_ALLOWED: 'YES',
        CODE_SIGNING_REQUIRED: 'YES',
      },
      {
        EFFECTIVE_PLATFORM_NAME: '-iphoneos',
        PLATFORM_NAME: 'iphoneos',
        SDK_NAME: 'iphoneos26.5',
      },
    ]) {
      const bypass = spawnSync(script, [], {
        env: { ...unsignedSimulatorSmoke, ...overrides },
        encoding: 'utf8',
      });
      assert.notEqual(bypass.status, 0);
      assert.match(
        bypass.stderr,
        /required|limited to an unsigned simulator compile-smoke build/u,
      );
    }

    const releaseSimulatorSmoke = spawnSync(script, [], {
      env: {
        ...unsignedSimulatorSmoke,
        BIRTHDAY_FIREBASE_ENV: 'prod',
        CONFIGURATION: 'Release',
        PRODUCT_BUNDLE_IDENTIFIER: 'com.yashsomani.birthdayautopilot',
      },
      encoding: 'utf8',
    });
    assert.equal(releaseSimulatorSmoke.status, 0, releaseSimulatorSmoke.stderr);
  },
);

test(
  'tier inventory rejects a Firebase project reused across iOS tiers',
  { skip: process.platform !== 'darwin' },
  () => {
    const directory = mkdtempSync(
      join(tmpdir(), 'birthday-ios-tier-inventory-'),
    );
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
  },
);

test('checked-in iOS tier inventory has no generic or cross-tier configuration', () => {
  assert.deepEqual(
    validateIOSGoogleConfigInventory(
      fileURLToPath(new URL('../ios/Config', import.meta.url)),
    ),
    [],
  );
});

test('People sync commits are fenced by one durable exact-account generation', () => {
  const policy = read(
    'ios/BirthdayAutopilot/Contacts/IOSPeopleSyncFencePolicy.swift',
  );
  const store = read(
    'ios/BirthdayAutopilot/Database/CompanionPeopleStore.swift',
  );
  const coordinator = read(
    'ios/BirthdayAutopilot/Contacts/IOSPeopleSyncCoordinator.swift',
  );
  const workflow = read(
    'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
  );
  const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');

  assert.match(policy, /capturedGeneration == durableGeneration/u);
  assert.match(policy, /exactAccountGenerationMatches/u);
  assert.match(
    store,
    /func beginSync[\s\S]*?snapshot\.sync\.nextSyncToken[\s\S]*?snapshot\.sync\.generation = generation[\s\S]*?try self\.persist\(snapshot\)/u,
  );
  assert.match(
    store,
    /func commit[\s\S]*?expectedSyncGeneration[\s\S]*?IOSPeopleSyncFencePolicy\.permitsCommit/u,
  );
  assert.match(
    store,
    /func recordSyncFailure[\s\S]*?snapshot\.sync\.generation == expectedSyncGeneration/u,
  );
  assert.match(
    store,
    /func invalidateOutstandingSync[\s\S]*?freshGeneration[\s\S]*?persist/u,
  );
  assert.match(
    store,
    /func finishSyncWithoutMutation[\s\S]*?snapshot\.sync\.generation == expectedSyncGeneration[\s\S]*?syncInProgress = nil/u,
  );
  assert.match(
    coordinator,
    /store\.beginSync[\s\S]*?initialMode: syncStart\.mode[\s\S]*?syncGeneration: syncStart\.generation[\s\S]*?expectedSyncGeneration: syncStart\.generation/u,
  );
  assert.match(
    workflow,
    /requiresPeopleSyncFence[\s\S]*?IOSPeopleSyncCoordinator\.shared\.invalidateOutstandingSync/u,
  );
  assert.match(
    coordinator,
    /case \.failed\(\.cancelled\)[\s\S]*?finishSyncWithoutMutation/u,
  );
  assert.equal(
    (project.match(/IOSPeopleSyncFencePolicy\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
});

test('a People 401 never replays the same SDK-cached bearer', () => {
  const coordinator = read(
    'ios/BirthdayAutopilot/Contacts/IOSPeopleSyncCoordinator.swift',
  );
  const identity = read(
    'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
  );
  const unauthorized =
    coordinator.match(
      /case \.unauthorized:[\s\S]*?case \.expiredSyncToken:/u,
    )?.[0] ?? '';

  assert.match(
    unauthorized,
    /requirePeopleReconnectAfterUnauthorized[\s\S]*?return \.failed\(\.authorizationRequired\)/u,
  );
  assert.doesNotMatch(unauthorized, /continue|refreshTokensIfNeeded|runOnce/u);
  assert.match(
    identity,
    /func requirePeopleReconnectAfterUnauthorized[\s\S]*?GIDSignIn\.sharedInstance\.signOut\(\)[\s\S]*?invalidateAccountSession/u,
  );
  assert.match(
    coordinator,
    /case \.failed\(\.forbidden\) = outcome[\s\S]*?requirePeopleReconnectAfterUnauthorized[\s\S]*?return \.failed\(\.authorizationRequired\)/u,
  );
});
