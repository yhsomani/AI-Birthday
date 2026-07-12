import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

const appDelegate = read('ios/BirthdayAutopilot/AppDelegate.swift');
const bridge = read('ios/BirthdayAutopilot/BirthdayNativeModule.swift');
const bridgeEvents = read(
  'ios/BirthdayAutopilot/BirthdayNativeModuleBridge.mm',
);
const client = read(
  'ios/BirthdayAutopilot/Automation/IOSAccountDeletionClient.swift',
);
const receiptClient = read(
  'ios/BirthdayAutopilot/Automation/IOSAccountDeletionReceiptClient.swift',
);
const cleanup = read(
  'ios/BirthdayAutopilot/Privacy/IOSAccountDeletionLocalCleanupCoordinator.swift',
);
const identity = read(
  'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
);
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
const receipt = read(
  'ios/BirthdayAutopilot/Privacy/IOSAccountDeletionReceiptStore.swift',
);
const reminder = read('ios/BirthdayAutopilot/CompanionReminderModule.swift');
const router = read(
  'ios/BirthdayAutopilot/Notifications/IOSCompanionNotificationRouter.swift',
);
const store = read('ios/BirthdayAutopilot/CompanionProtectedStore.swift');
const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const activityModel = read('src/domain/activity/model.ts');

test('iOS account deletion starts a strict recent-authenticated replay-protected callable', () => {
  assert.match(
    client,
    /private static let callableName = "requestAccountDeletion"/u,
  );
  assert.match(client, /private static let region = "asia-south1"/u);
  assert.match(
    client,
    /HTTPSCallableOptions\(requireLimitedUseAppCheckTokens: true\)/u,
  );
  assert.match(client, /"contractVersion": 1/u);
  assert.match(client, /"requestId": requestId/u);
  assert.match(
    client,
    /Set\(raw\.keys\) == \["fence", "kind", "receiptId", "tombstone"\]/u,
  );
  assert.match(client, /\["STARTED", "REPLAYED"\]\.contains\(kind\)/u);
  assert.match(client, /raw\["receiptId"\] as\? String == expectedReceiptId/u);
  assert.match(client, /raw\["requestKey"\] as\? String == receiptKey/u);
  assert.match(client, /birthday-deletion-receipt-v1\\0/u);
  assert.match(client, /Set\(raw\.keys\) == fenceRequiredKeys/u);
  assert.match(client, /deletionDrainUntilMs/u);
  assert.match(client, /raw\["mode"\] as\? String == "DELETING"/u);
  assert.match(client, /hasExactFirebaseSession\(binding\)/u);
  assert.match(identity, /getIDTokenResult\(forcingRefresh: false\)/u);
  assert.match(identity, /firebaseUser\.reauthenticate\(with: credential\)/u);
  assert.match(identity, /subject == binding\.googleSubject/u);
  assert.match(identity, /reauthenticated\.user\.uid == binding\.firebaseUID/u);
  assert.match(
    identity,
    /func continueWithGoogle[\s\S]*?canBeginOrdinaryGoogleSelection\(\)[\s\S]*?GIDSignIn\.sharedInstance\.signIn[\s\S]*?retireCompletedReceiptBeforeNewIdentity\(\)[\s\S]*?attachGoogleUser/u,
  );
  assert.doesNotMatch(client, /print\s*\(|NSLog\s*\(|os_log|Logger\s*\(/u);
});

test('deletion writes a minimal restart-safe receipt before destructive local cleanup', () => {
  assert.match(receipt, /localDataErased: Bool/u);
  assert.match(receipt, /remoteDeletionComplete: Bool/u);
  assert.match(receipt, /externalSmsCopiesNotErased: Bool/u);
  assert.match(receipt, /remoteDeletionComplete: false/u);
  assert.match(receipt, /\.completeFileProtection/u);
  assert.match(receipt, /isExcludedFromBackup = true/u);
  assert.match(receipt, /private static let retention: TimeInterval = 365/u);
  assert.match(receipt, /!receipt\.remoteDeletionComplete \|\| age < 0/u);
  assert.doesNotMatch(receipt, /repairUnreadableReceipt/u);
  for (const forbidden of [
    'firebaseUID',
    'googleSubject',
    'displayEmail',
    'requestId',
    'accessToken',
    'idToken',
    'recipient',
    'message',
  ]) {
    assert.doesNotMatch(receipt, new RegExp(`"${forbidden}"`, 'u'));
  }
  assert.match(
    workflow,
    /recordRemoteDraining\(operationId: operation\.id\)[\s\S]*?finishLocalCleanup/u,
  );
  assert.match(workflow, /birthday-ios-delete-operation-projection-v1/u);
  assert.match(workflow, /private static func constantTimeEqual/u);
  assert.doesNotMatch(workflow, /"id": receipt\.operationId/u);
  assert.match(
    cleanup,
    /beginAccountDeletionShutdown[\s\S]*?cancelAppOwnedNotifications[\s\S]*?completeAccountDeletionLocalShutdown[\s\S]*?destroyCompanionDataAfterAccountDeletion[\s\S]*?markLocalDataErased/u,
  );
  assert.match(store, /destroyAfterRemoteAccountDeletion/u);
  assert.match(identity, /destroyAfterRemoteAccountDeletion/u);
  assert.match(reminder, /destroyCompanionDataAfterAccountDeletion/u);
});

test('signed-out deletion completion requires a strict App Check receipt proof', () => {
  assert.match(
    receiptClient,
    /private static let callableName = "accountDeletionReceipt"/u,
  );
  assert.match(
    receiptClient,
    /HTTPSCallableOptions\(requireLimitedUseAppCheckTokens: true\)/u,
  );
  assert.match(receiptClient, /"receiptId": receiptId/u);
  assert.match(
    receiptClient,
    /Set\(raw\.keys\) == \["kind", "requestedAtMs", "updatedAtMs"\]/u,
  );
  assert.match(receiptClient, /case "COMPLETED"/u);
  assert.match(receiptClient, /case "NOT_FOUND"/u);
  assert.doesNotMatch(receiptClient, /FirebaseAuth|Auth\.auth\(\)/u);
  assert.doesNotMatch(
    receiptClient,
    /print\s*\(|NSLog\s*\(|os_log|Logger\s*\(/u,
  );
  assert.match(cleanup, /checkRemoteCompletion/u);
  assert.match(
    cleanup,
    /if !receipt\.localDataErased[\s\S]*?finishLocalCleanup[\s\S]*?checkRemoteCompletion/u,
  );
  assert.match(cleanup, /markRemoteDeletionComplete/u);
  assert.match(
    bridge,
    /intent == "check-account-deletion-status"[\s\S]*?payload\.isEmpty/u,
  );
  assert.match(workflow, /if receipt\.remoteDeletionComplete/u);
  assert.match(workflow, /"remoteDeletionComplete": true/u);
});

test('deletion remains remote-draining until completion proof and blocks ordinary signed-out projection', () => {
  assert.match(workflow, /"kind": "remote-draining"/u);
  assert.match(workflow, /guard receipt\.localDataErased else/u);
  assert.match(workflow, /"kind": "local-wiping"/u);
  assert.match(workflow, /"localDataErased": true/u);
  assert.match(workflow, /"remoteDeletionComplete": false/u);
  assert.match(workflow, /"externalSmsCopiesNotErased": true/u);
  assert.match(
    bridge,
    /IOSAccountDeletionReceiptStore\.shared\.hasPendingOrUnreadableReceipt\(\)[\s\S]*?"kind": "cleanup-pending"[\s\S]*?"operation": "delete"/u,
  );
  assert.match(bridge, /case "latest-deletion-receipt"/u);
  assert.match(
    workflow,
    /action == "delete-account"[\s\S]*?workflow\.privacyOperations\.lastIndex/u,
  );
  assert.match(
    workflow,
    /"preissuedPermitMayFinish": \[[\s\S]*?"delete-account", "disconnect-contacts", "revoke-google-access"[\s\S]*?\]\.contains\(action\)/u,
  );
});

test('notification taps consume only an opaque protected request and route to review without MessageUI', () => {
  assert.match(
    appDelegate,
    /UNUserNotificationCenter\.current\(\)\.delegate = IOSCompanionNotificationRouter\.shared/u,
  );
  assert.match(
    router,
    /response\.actionIdentifier == UNNotificationDefaultActionIdentifier/u,
  );
  assert.match(
    router,
    /request\.content\.userInfo\["requestId"\] as\? String/u,
  );
  assert.match(router, /request\.content\.userInfo\.count == 1/u);
  assert.match(router, /consumeReminderRouteRequest\(requestId\)/u);
  assert.match(store, /snapshot\.notificationIdentities\[identityIndex\]/u);
  assert.match(store, /requestId: UUID\(\)\.uuidString\.lowercased\(\)/u);
  assert.match(
    store,
    /!plannedOccurrenceIds\.intersection\(reviewableOccurrenceIds\)/u,
  );
  assert.match(router, /"kind": "available"/u);
  assert.match(store, /"kind": "automation-review"/u);
  assert.match(store, /"source": "birthday-reminder"/u);
  assert.match(bridge, /if area == "route"[\s\S]*?executeRouteProjection/u);
  assert.match(
    bridge,
    /IOSCompanionNotificationRouter\.shared\.takeProjection/u,
  );
  assert.match(bridgeEvents, /BirthdayNativeRouteAvailable/u);
  assert.doesNotMatch(
    router,
    /MFMessageComposeViewController|presentUserConfirmedComposer/u,
  );
  const routeProjection =
    store.match(/var projection: \[String: Any\] \{[\s\S]*?\n {2}\}/u)?.[0] ??
    '';
  assert.doesNotMatch(
    routeProjection,
    /requestId|civilDate|contact|recipient|body|proposal/u,
  );
  assert.match(
    store,
    /var pendingNativeRoute: IOSCompanionPendingNativeRoute\?/u,
  );
  assert.match(
    store,
    /snapshot\.pendingNativeRoute = IOSCompanionPendingNativeRoute/u,
  );
  assert.match(
    store,
    /func takePendingNativeRoute[\s\S]*?snapshot\.pendingNativeRoute = nil/u,
  );
  const persistedRoute =
    store.match(
      /private struct IOSCompanionPendingNativeRoute[\s\S]*?\n\}/u,
    )?.[0] ?? '';
  assert.doesNotMatch(persistedRoute, /requestId|civilDate|contact|proposal/u);
});

test('every new iOS lifecycle source is in the Xcode target exactly once', () => {
  for (const file of [
    'IOSAccountDeletionClient.swift',
    'IOSAccountDeletionReceiptClient.swift',
    'IOSAccountDeletionReceiptStore.swift',
    'IOSAccountDeletionLocalCleanupCoordinator.swift',
    'IOSCompanionNotificationRouter.swift',
  ]) {
    const escaped = file.replaceAll('.', '\\.');
    assert.equal(
      (project.match(new RegExp(`${escaped} in Sources`, 'gu')) ?? []).length,
      2,
      `${file} must have one PBXBuildFile declaration and one Sources entry`,
    );
  }
});

test('iOS Activity keeps every kind the workflow truthfully emits in the shared allowlist', () => {
  const nativeAllowlist =
    workflow.match(
      /private static let activityKinds: Set<String> = \[[\s\S]*?\n {2}\]/u,
    )?.[0] ?? '';
  for (const kind of [
    'reminder-scheduled',
    'composer-opened',
    'composer-cancelled',
    'composer-failed',
    'composer-outcome-unknown',
    'composer-reported-sent',
    'approval-invalidated',
    'coordination-blocked',
    'paused',
    'settings-changed',
    'sync',
  ]) {
    assert.match(nativeAllowlist, new RegExp(`"${kind}"`, 'u'));
    assert.match(activityModel, new RegExp(`'${kind}'`, 'u'));
  }
  for (const invented of [
    'reminder-reconciled',
    'visibility-unknown',
    'android-managed',
  ]) {
    assert.doesNotMatch(nativeAllowlist, new RegExp(`"${invented}"`, 'u'));
  }
});
