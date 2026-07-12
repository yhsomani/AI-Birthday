import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

const bridge = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/bridge/BirthdayNativeModule.kt',
);
const controller = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/lifecycle/AndroidLifecycleController.kt',
);
const journal = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/lifecycle/LifecycleStateStore.kt',
);
const binding = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/lifecycle/SenderReleaseRecoveryBindingPolicy.kt',
);
const appGraph = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/AppGraph.kt',
);

test('contact actions purge locally before any remote pause or reset can block them', () => {
  const confirmation = bridge.slice(
    bridge.indexOf('private fun handleConfirmPrivacyAction('),
    bridge.indexOf('private fun completeRetainedSignOut('),
  );
  const contactBranch = confirmation.slice(
    confirmation.indexOf(
      'plan.action in setOf("revoke-google-access", "disconnect-contacts")',
    ),
    confirmation.indexOf(
      'plan.action in setOf("sign-out-wipe", "wipe-local-data")',
    ),
  );
  assert.match(contactBranch, /purgeContactDerivedState/u);
  assert.match(contactBranch, /local\.localDataErased/u);
  assert.ok(
    contactBranch.indexOf('purgeContactDerivedState') <
      contactBranch.indexOf('convergeLifecycleServerPause'),
  );
  assert.ok(
    contactBranch.indexOf('convergeLifecycleServerPause') <
      contactBranch.indexOf('startOrReplayContactDerivedReset'),
  );
  assert.match(controller, /localDataErased = true/u);
  assert.match(controller, /state = "remote-pending"/u);
  assert.match(
    controller,
    /purgeContactDerivedState[\s\S]*?recordContactsConsentDecision\([\s\S]*?ConsentKind\.CONTACTS_DISCLOSURE[\s\S]*?ConsentDecision\.REVOKED[\s\S]*?deleteContactSnapshots/u,
  );
  assert.match(
    bridge,
    /CoordinationOperationOutcome\.Completed[\s\S]*?markContactResetRemoteCompleted/u,
  );
  assert.match(
    bridge,
    /SESSION_CLEANUP_PENDING[\s\S]*?markGoogleAccessRevoked/u,
  );
  assert.match(
    controller,
    /markGoogleAccessRevoked[\s\S]*?recordContactsConsentDecision\([\s\S]*?ConsentKind\.CONTACTS_READONLY[\s\S]*?ConsentDecision\.REVOKED[\s\S]*?remoteAccessRevoked = true/u,
  );
});

test('destructive wipe destroys local capability before sender release and remains remote pending', () => {
  const confirmation = bridge.slice(
    bridge.indexOf('private fun handleConfirmPrivacyAction('),
    bridge.indexOf('private fun completeRetainedSignOut('),
  );
  assert.match(
    confirmation,
    /plan\.action in setOf\("sign-out-wipe", "wipe-local-data"\)[\s\S]*?completeLocalFirstDestructiveWipe/u,
  );
  assert.doesNotMatch(confirmation, /startOrReplaySenderRelease/u);
  assert.match(
    bridge,
    /completeLocalFirstDestructiveWipe[\s\S]*?persistReleaseRequestBinding[\s\S]*?cancelAllLocalWork[\s\S]*?retireLocalCallbacks[\s\S]*?markLocalWipeStarted[\s\S]*?finishMarkedDestructiveWipe/u,
  );
  assert.match(
    bridge,
    /finishMarkedDestructiveWipe[\s\S]*?completeSignOutAfterSafetyShutdown[\s\S]*?eraseProtectedDatabaseAfterTeardown[\s\S]*?markDestructiveLocalDataErased/u,
  );
  assert.match(
    controller,
    /markDestructiveLocalDataErased[\s\S]*?state = "remote-pending"[\s\S]*?localDataErased = true[\s\S]*?wipeCallbackGeneration = null/u,
  );
  assert.match(
    journal,
    /completeRecoveredLocalWipe[\s\S]*?state = "remote-pending"[\s\S]*?localDataErased = true[\s\S]*?wipeCallbackGeneration = null/u,
  );
  assert.match(appGraph, /deleteDatabase\(BirthdayDatabase\.DATABASE_NAME\)/u);
  assert.match(appGraph, /DatabaseKeyManager\(appContext\)\.clear\(\)/u);
  assert.match(appGraph, /rotateAfterTeardown/u);
});

test('post-wipe sender release is exact-account only and terminal truth follows server completion', () => {
  assert.match(binding, /SenderReleaseRecoveryFirebaseUid\.v1/u);
  assert.match(binding, /SenderReleaseRecoveryGoogleSubject\.v1/u);
  assert.match(binding, /MessageDigest\.isEqual/u);
  assert.doesNotMatch(binding, /DeletionRecoveryBindingPolicy/u);
  assert.match(
    bridge,
    /senderReleaseRecoveryReauthenticationAllowed\(\)[\s\S]*?handleSenderReleaseRecoveryIdentityIntent/u,
  );
  assert.match(
    bridge,
    /continueWithGoogleForLifecycleRepair\([\s\S]*?matchesSenderReleaseRecoveryGoogleSubject[\s\S]*?matchesSenderReleaseRecoveryBinding/u,
  );
  assert.match(
    bridge,
    /senderReleaseRecoveryCoordinationPort\.releaseAndroidSender/u,
  );
  assert.match(
    bridge,
    /CoordinationCompletion\.SenderRelease[\s\S]*?completeSenderReleaseRemoteCleanup/u,
  );
  assert.match(
    controller,
    /completeSenderReleaseRemoteCleanup[\s\S]*?state = "complete"[\s\S]*?requestId = null[\s\S]*?senderReleaseRecoverySalt = null/u,
  );
  assert.match(
    appGraph,
    /SenderReleaseRecoveryStartupPolicy\.requiresIdentitySessionClear/u,
  );
});
