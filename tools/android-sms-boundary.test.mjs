import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { extname, join } from 'node:path';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');

const walk = directory =>
  readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? walk(path) : [path];
  });

test('only the typed native gateway contains an SMS submission call', () => {
  const sourceRoot = 'android/app/src/main/java';
  const callSites = walk(sourceRoot)
    .filter(path => extname(path) === '.kt' || extname(path) === '.java')
    .flatMap(path => {
      const source = read(path);
      const matches =
        source.match(/\.send(?:Multipart)?TextMessage\s*\(/gu) ?? [];
      return matches.map(() => path);
    });

  assert.deepEqual(callSites, [
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/sms/SmsGateway.kt',
  ]);
  const gateway = read(callSites[0]);
  assert.match(gateway, /ArmedAttemptPermit/u);
  assert.match(gateway, /commitApiBoundary/u);
  assert.match(gateway, /registerCallbackTokens/u);
});

test('dangerous telephony permissions remain restricted-release only', () => {
  const main = read('android/app/src/main/AndroidManifest.xml');
  const lab = read('android/app/src/lab/AndroidManifest.xml');
  const prod = read('android/app/src/prod/AndroidManifest.xml');

  for (const permission of ['SEND_SMS', 'READ_PHONE_STATE']) {
    assert.doesNotMatch(
      main,
      new RegExp(`android\\.permission\\.${permission}`, 'u'),
    );
    assert.match(lab, new RegExp(`android\\.permission\\.${permission}`, 'u'));
    assert.match(prod, new RegExp(`android\\.permission\\.${permission}`, 'u'));
  }
  for (const restrictedManifest of [lab, prod]) {
    assert.match(
      restrictedManifest,
      /<uses-feature\s+android:name="android\.hardware\.telephony"\s+android:required="false"\s*\/>/u,
    );
  }
  assert.match(
    main,
    /android:name="\.automation\.sms\.SmsSentCallbackReceiver"[\s\S]*?android:exported="false"/u,
  );
  assert.match(
    main,
    /android:name="\.automation\.sms\.SmsDeliveryCallbackReceiver"[\s\S]*?android:exported="false"/u,
  );
});

test('telephony permissions are requested sequentially and SIM drift reconciles immediately', () => {
  const owner = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/auth/TelephonyPermissionActivityResultOwner.kt',
  );
  const manifest = read('android/app/src/main/AndroidManifest.xml');
  const receiver = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/workers/AutomationReconcileReceiver.kt',
  );
  const subscriptions = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/sms/SubscriptionChangeSignalStore.kt',
  );
  const scheduler = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/workers/AutomationScheduler.kt',
  );
  const appGraph = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/AppGraph.kt',
  );
  const mainActivity = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/MainActivity.kt',
  );
  const finalGate = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AndroidAutomationEnvironment.kt',
  );
  const gateway = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/sms/SmsGateway.kt',
  );
  const orchestrator = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AndroidAutomationOrchestrator.kt',
  );

  assert.doesNotMatch(owner, /RequestMultiplePermissions/u);
  assert.equal(
    (owner.match(/ActivityResultContracts\.RequestPermission\(\)/gu) ?? [])
      .length,
    2,
  );
  assert.match(
    owner,
    /!isGranted\(Manifest\.permission\.READ_PHONE_STATE\)[\s\S]*?!isGranted\(Manifest\.permission\.SEND_SMS\)/u,
  );
  assert.match(owner, /PHONE_STATE_PERMANENTLY_DENIED/u);
  assert.match(owner, /SMS_PERMANENTLY_DENIED/u);
  assert.match(manifest, /DEFAULT_SMS_SUBSCRIPTION_CHANGED/u);
  assert.doesNotMatch(manifest, /SIM_CARD_STATE_CHANGED/u);
  assert.doesNotMatch(manifest, /SIM_APPLICATION_STATE_CHANGED/u);
  assert.match(
    receiver,
    /recordConfirmedChange[\s\S]*?enqueueSubscriptionChange/u,
  );
  assert.match(subscriptions, /OnSubscriptionsChangedListener/u);
  assert.match(
    subscriptions,
    /addOnSubscriptionsChangedListener\(callbackExecutor, listener\)/u,
  );
  assert.match(subscriptions, /addOnSubscriptionsChangedListener\(listener\)/u);
  assert.match(subscriptions, /noBackupFilesDir/u);
  assert.match(subscriptions, /generation[\s\S]*?consumedGeneration/u);
  assert.match(subscriptions, /carrierId/u);
  assert.match(subscriptions, /mccString/u);
  assert.match(subscriptions, /mncString/u);
  assert.match(subscriptions, /cardId/u);
  assert.match(subscriptions, /markWriteFailure/u);
  assert.match(subscriptions, /FAIL_CLOSED_GENERATION/u);
  assert.match(scheduler, /ExistingWorkPolicy\.APPEND_OR_REPLACE/u);
  assert.match(appGraph, /private val subscriptionChangeObserver by lazy/u);
  assert.match(
    mainActivity,
    /onResume\(\)[\s\S]*?startSubscriptionChangeObservation\(\)/u,
  );
  assert.match(
    finalGate,
    /subscriptionChangeSignalStore\.pendingGeneration\(\) == null/u,
  );
  assert.match(
    gateway,
    /verifyEnvironment[\s\S]*?subscriptionChangeSignalStore\.pendingGeneration\(\) != null/u,
  );
  assert.match(
    orchestrator,
    /submitForegroundTest[\s\S]*?enforceRuntimeSafetyPause\(readinessProbe\.read\(\)\)/u,
  );
  assert.match(
    orchestrator,
    /val simBindingInvalidated =[\s\S]{0,180}\(automationActive \|\| hasSystemDefaultMaterial\)/u,
  );
  assert.match(
    orchestrator,
    /permissionEvidenceMustBeRechecked = automationActive \|\| validSystemDefaultReceipts > 0/u,
  );
});

test('Activity-bound permission owners defer Context access until Android attaches the base', () => {
  const telephonyOwner = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/auth/TelephonyPermissionActivityResultOwner.kt',
  );
  const notificationOwner = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/auth/NotificationPermissionActivityResultOwner.kt',
  );
  const launchTest = read(
    'android/app/src/androidTest/java/com/yashsomani/birthdayautopilot/MainActivityLaunchInstrumentationTest.kt',
  );
  assert.match(
    telephonyOwner,
    /requestHistory by lazy\(LazyThreadSafetyMode\.NONE\)/u,
  );
  assert.match(
    notificationOwner,
    /state by lazy\(LazyThreadSafetyMode\.NONE\)/u,
  );
  assert.match(
    launchTest,
    /ActivityScenario\.launch\(MainActivity::class\.java\)/u,
  );
});

test('the APK verifier keeps unsigned inspection dev-only and restricted artifacts signed', () => {
  const verifier = read('tools/verify-android-apk.sh');
  const aabVerifier = read('tools/verify-android-aab.sh');
  const aabManifestInspector = read('tools/inspect-android-aab-manifest.mjs');
  assert.match(verifier, /--unsigned-dev-release/u);
  assert.match(
    verifier,
    /package_name" != "com\.yashsomani\.birthdayautopilot\.dev"/u,
  );
  assert.match(verifier, /an unsigned artifact can never contain SEND_SMS/u);
  assert.match(verifier, /Missing META-INF\/MANIFEST\.MF/u);
  assert.match(verifier, /APK Sig Block 42/u);
  assert.match(verifier, /--restricted-evidence/u);
  assert.match(verifier, /--play-delivered-evidence/u);
  assert.match(verifier, /signature scheme v2 or v3/u);
  assert.match(aabVerifier, /--play-evidence/u);
  assert.match(aabVerifier, /play-aab/u);
  assert.match(aabVerifier, /exactly one signer/u);
  assert.match(aabVerifier, /inspect-android-aab-manifest\.mjs/u);
  assert.match(aabManifestInspector, /android:versionCode/u);
  assert.match(aabManifestInspector, /android:minSdkVersion/u);
  assert.match(aabManifestInspector, /android:targetSdkVersion/u);
  assert.match(aabManifestInspector, /android\.permission\.SEND_SMS/u);
  assert.match(aabManifestInspector, /FORBIDDEN_PERMISSIONS/u);
});

test('React Native exposes no SMS or telephony submission method', () => {
  const spec = read('specs/native/NativeBirthday.ts');
  assert.doesNotMatch(
    spec,
    /sendSms|sendTextMessage|sendMultipartTextMessage|SmsManager/iu,
  );
});

test('Android setup exposes a durable first-activation fact without deriving it from current mode', () => {
  const module = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/bridge/BirthdayNativeModule.kt',
  );
  const controller = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/configuration/AndroidConfigurationController.kt',
  );
  const control = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/ControlEntity.kt',
  );

  assert.match(
    module,
    /setupPayload[\s\S]*?"initialActivationCompleted"[\s\S]*?configurationController\.initialActivationCompleted/u,
  );
  assert.match(controller, /commitActivation[\s\S]*?markAutomationActivated/u);
  assert.match(control, /initialActivationCompleted: Boolean/u);
  assert.doesNotMatch(
    controller,
    /initialActivationCompleted\(\)[\s\S]{0,120}AccountMode\.AUTOMATION_ACTIVE/u,
  );
});

test('native Android notifications and fallback labels use localized resources', () => {
  const notifier = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/attention/AndroidAttentionNotifications.kt',
  );
  const controller = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/configuration/AndroidConfigurationController.kt',
  );
  const module = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/bridge/BirthdayNativeModule.kt',
  );
  const hindi = read('android/app/src/main/res/values-hi/strings.xml');

  assert.match(notifier, /R\.string\.attention_notification_title/u);
  assert.match(notifier, /R\.string\.attention_channel_name/u);
  for (const resource of [
    'birthday_incomplete',
    'configuration_not_configured',
    'configuration_grace_to',
    'phone_source_fallback',
    'outcome_delivered',
    'outcome_sent_from_device',
    'outcome_missed',
    'outcome_skipped',
    'outcome_unknown',
  ]) {
    assert.match(controller, new RegExp(`R\\.string\\.${resource}`, 'u'));
    assert.match(hindi, new RegExp(`name="${resource}"`, 'u'));
  }
  for (const resource of [
    'sender_local_device',
    'sender_other_verified_android_device',
  ]) {
    assert.match(module, new RegExp(`R\\.string\\.${resource}`, 'u'));
    assert.match(hindi, new RegExp(`name="${resource}"`, 'u'));
  }
  assert.doesNotMatch(notifier, /setContentTitle\("|setContentText\("/u);
  assert.doesNotMatch(
    controller,
    /return "Incomplete birthday"|\?: "Not configured"|\?: "Phone"/u,
  );
  assert.doesNotMatch(
    module,
    /\.put\("epochLabel", "Local device"\)|"Another verified Android device"/u,
  );
});

test('the Android system-composer alternative is foreground, recipient-scoped, and never a send API', () => {
  const composer = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/messages/AndroidUserControlledSmsComposer.kt',
  );
  const controller = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/lifecycle/AndroidLifecycleController.kt',
  );
  const dao = read(
    'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AutomationOrchestrationDao.kt',
  );
  const model = read('src/domain/automation/model.ts');
  const home = read('src/features/live/LiveHomeScreen.tsx');
  const manifest = read('android/app/src/main/AndroidManifest.xml');

  assert.match(composer, /Intent\.ACTION_SENDTO/u);
  assert.match(composer, /Uri\.fromParts\(SMSTO_SCHEME/u);
  assert.match(composer, /private const val SMSTO_SCHEME = "smsto"/u);
  assert.match(composer, /ForegroundActivityRegistry::withCurrentActivity/u);
  assert.match(
    composer,
    /resolveActivity\(activity\.packageManager\) != null/u,
  );
  assert.doesNotMatch(composer, /Intent\.ACTION_SEND(?:_MULTIPLE)?\b/u);
  assert.doesNotMatch(
    composer,
    /SmsManager|sendTextMessage|sendMultipartTextMessage/u,
  );
  assert.match(
    controller,
    /SystemSmsComposerIntentPolicy\.validDraft\(draft\)/u,
  );
  assert.match(controller, /userControlledSmsComposer\.canOpen\(draft\)/u);
  assert.match(
    controller,
    /userControlledSmsComposer\.open\(launchPlan\.draft\)/u,
  );
  assert.match(controller, /restoreKnownFailedSystemComposerRetirement/u);
  assert.match(controller, /UserControlledSmsComposerOpenResult\.UNKNOWN/u);
  assert.match(dao, /retireReviewedOccurrenceForSystemComposer/u);
  assert.match(dao, /USER_OPENED_SYSTEM_COMPOSER/u);
  assert.doesNotMatch(model, /canonicalRecipient|normalizedPhoneE164/u);
  assert.match(
    home,
    /choice === 'start-next-year'[\s\S]*?!todayReview\.review\.alternativeChoice[\s\S]*?'live\.home\.keepTodaySchedule'/u,
  );
  assert.match(
    manifest,
    /<queries>[\s\S]*?<action android:name="android\.intent\.action\.SENDTO"\s*\/>[\s\S]*?<data android:scheme="smsto"\s*\/>[\s\S]*?<\/queries>/u,
  );
});
