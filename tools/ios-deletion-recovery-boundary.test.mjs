import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

const bridge = read('ios/BirthdayAutopilot/BirthdayNativeModule.swift');
const cleanup = read(
  'ios/BirthdayAutopilot/Privacy/IOSAccountDeletionLocalCleanupCoordinator.swift',
);
const identity = read(
  'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
);
const peopleStore = read(
  'ios/BirthdayAutopilot/Database/CompanionPeopleStore.swift',
);
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
const recovery = read(
  'ios/BirthdayAutopilot/Privacy/IOSAccountDeletionRecoveryStore.swift',
);
const receipt = read(
  'ios/BirthdayAutopilot/Privacy/IOSAccountDeletionReceiptStore.swift',
);
const receiptClient = read(
  'ios/BirthdayAutopilot/Automation/IOSAccountDeletionReceiptClient.swift',
);
const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);

test('ambiguous deletion recovery journal is protected, backup-excluded, and equality-only', () => {
  assert.match(recovery, /SecRandomCopyBytes/u);
  assert.match(recovery, /saltByteCount = 32/u);
  assert.match(recovery, /firebaseUIDDigest: String/u);
  assert.match(recovery, /googleSubjectDigest: String/u);
  assert.match(
    recovery,
    /birthday-ios-account-deletion-recovery-firebase-uid-v1\\0/u,
  );
  assert.match(
    recovery,
    /birthday-ios-account-deletion-recovery-google-subject-v1\\0/u,
  );
  assert.match(recovery, /appendLengthPrefixed\(operationId/u);
  assert.match(recovery, /\.completeFileProtection/u);
  assert.match(recovery, /isExcludedFromBackup = true/u);
  assert.match(recovery, /private static func constantTimeEqual/u);
  const persistedKeys =
    recovery.match(/private static let allowedKeys:[\s\S]*?\n {2}\]/u)?.[0] ??
    '';
  for (const forbidden of [
    'displayEmail',
    'displayName',
    'firebaseUID',
    'googleSubject',
    'accessToken',
    'idToken',
  ]) {
    assert.doesNotMatch(persistedKeys, new RegExp(`"${forbidden}"`, 'u'));
  }
  assert.doesNotMatch(recovery, /print\s*\(|NSLog\s*\(|os_log|Logger\s*\(/u);
});

test('wipe review is bound to and preserves the exact active delete operation', () => {
  assert.match(
    workflow,
    /recoverableDeletionOperationIndex[\s\S]*?action == "delete-account"[\s\S]*?phase == "remote-pending"/u,
  );
  const prepare = workflow.slice(
    workflow.indexOf('private func preparePrivacy('),
    workflow.indexOf('private func confirmPrivacy('),
  );
  const confirm = workflow.slice(
    workflow.indexOf('private func confirmPrivacy('),
    workflow.indexOf('private func performPrivacyAction('),
  );
  assert.match(
    prepare,
    /action == "wipe-local-data"[\s\S]*?recoverableDeletionOperation\(in: workflow\)\?\.id/u,
  );
  assert.match(
    prepare,
    /"privacy", action, binding\.accountGeneration, revision,[\s\S]*?recoveryOperationId/u,
  );
  assert.match(
    prepare,
    /"preissuedPermitMayFinish": \[[\s\S]*?prepared\.isDeletionRecovery \? action/u,
  );
  assert.match(
    prepare,
    /"remoteConnectionRequired"[\s\S]*?&& !prepared\.isDeletionRecovery/u,
  );
  assert.match(
    confirm,
    /Self\.previousRevision\(of: revision\),[\s\S]*?recoveryOperationId/u,
  );
  assert.match(
    confirm,
    /privacyOperations\[recoveryOperationIndex\]\.phase = "local-wiping"/u,
  );
  assert.match(
    confirm,
    /privacyOperations\[recoveryOperationIndex\]\.reason =[\s\S]*?deletionLocalWipeRecoveryReason/u,
  );
  assert.match(confirm, /performAmbiguousDeletionLocalWipe/u);
  assert.doesNotMatch(
    confirm,
    /isDeletionRecovery[\s\S]*?action: "wipe-local-data"/u,
  );
});

test('native-only recovery intent survives the post-confirm pre-journal crash boundary', () => {
  assert.match(
    workflow,
    /deletionLocalWipeRecoveryReason =[\s\S]*?"deletion-local-wipe-recovery"/u,
  );
  const resume = workflow.slice(
    workflow.indexOf('private func resumePrivacyOperation('),
    workflow.indexOf('private func preparePrivacy('),
  );
  assert.match(
    resume,
    /operation\.reason == Self\.deletionLocalWipeRecoveryReason[\s\S]*?performAmbiguousDeletionLocalWipe/u,
  );
  const projection = workflow.slice(
    workflow.indexOf('private static func privacyOperationPayload('),
    workflow.indexOf('static func accountDeletionReceiptPayload('),
  );
  assert.doesNotMatch(projection, /deletionLocalWipeRecoveryReason/u);
  assert.doesNotMatch(bridge, /deletion-local-wipe-recovery/u);
});

test('completed receipt expiry is monotonic across the recovery-clear crash boundary', () => {
  const expiry = receipt.slice(
    receipt.indexOf('let age = now.timeIntervalSince'),
    receipt.indexOf('private func persist('),
  );
  const recoveryClear = expiry.indexOf(
    'clearSynchronouslyForCompletedReceiptRetirement',
  );
  const receiptRemoval = expiry.indexOf('removeItem(at: url)');
  assert.ok(recoveryClear >= 0);
  assert.ok(receiptRemoval > recoveryClear);
  assert.match(expiry, /age < 0\s*\|\| age < Self\.retention/u);
  assert.doesNotMatch(expiry, /age <= Self\.retention/u);
  assert.match(recovery, /clearSynchronouslyForCompletedReceiptRetirement/u);
  assert.match(
    expiry,
    /clearSynchronouslyForCompletedReceiptRetirement[\s\S]*?else \{[\s\S]*?return receipt/u,
  );
  assert.match(
    recovery,
    /clearSynchronouslyForCompletedReceiptRetirement[\s\S]*?loadCurrent\(\)[\s\S]*?existing\.operationId, operationId[\s\S]*?else \{ return false \}/u,
  );

  // At exactly 365 days, clear matching recovery while COMPLETED still exists.
  // A crash then leaves completion authoritative; the next pass removes it.
  const expire = (state, crashAfterRecoveryClear = false) => {
    if (state.receipt === 'completed' && state.matchingRecovery) {
      state = { receipt: 'completed', matchingRecovery: false };
      if (crashAfterRecoveryClear) {
        return state;
      }
    }
    return state.receipt === 'completed' && !state.matchingRecovery
      ? { receipt: 'none', matchingRecovery: false }
      : state;
  };
  const afterCrash = expire(
    { receipt: 'completed', matchingRecovery: true },
    true,
  );
  assert.deepEqual(afterCrash, {
    receipt: 'completed',
    matchingRecovery: false,
  });
  assert.deepEqual(expire(afterCrash), {
    receipt: 'none',
    matchingRecovery: false,
  });
});

test('reviewed recovery commits both journals before native destructive cleanup', () => {
  const start = workflow.indexOf(
    'private func performAmbiguousDeletionLocalWipe(',
  );
  const end = workflow.indexOf(
    'private func performContactDerivedReset(',
    start,
  );
  const body = workflow.slice(start, end);
  const recoveryWrite = body.indexOf('recordReviewedLocalWipe');
  const receiptWrite = body.indexOf('recordPending');
  const destructiveCleanup = body.indexOf('finishLocalCleanup');
  assert.ok(recoveryWrite >= 0);
  assert.ok(receiptWrite > recoveryWrite);
  assert.ok(destructiveCleanup > receiptWrite);
  assert.match(body, /operation\.action == "delete-account"/u);
  assert.match(body, /accountDeletionRecoveryUnknownPayload/u);
  assert.match(workflow, /"kind": "remote-unknown"/u);
  assert.match(workflow, /"localDataErased": true/u);
  assert.match(workflow, /"remoteDeletionComplete": false/u);
  assert.match(
    workflow,
    /"sameAccountRetryAvailable": sameAccountRetryAvailable/u,
  );
  assert.match(workflow, /"externalSmsCopiesNotErased": true/u);
});

test('status proof gates replay, marks acceptance, and clears equality recovery only after completion', () => {
  assert.match(
    cleanup,
    /case \.success\(\.notFound\), \.failure\(\.networkOffline\),[\s\S]*?markRetryAuthorized/u,
  );
  assert.match(
    cleanup,
    /case \.failure\(\.signedOutRequired\), \.failure\(\.configuration\):[\s\S]*?finishStatusCheck\(\.unavailable\)/u,
  );
  assert.match(
    receiptClient,
    /case signedOutRequired[\s\S]*?FunctionsErrorCode\.failedPrecondition/u,
  );
  assert.match(cleanup, /case remoteUnknown|case \.remoteUnknown/u);
  assert.match(
    cleanup,
    /case \.success\(\.inProgress\):[\s\S]*?finishRemoteAcceptance/u,
  );
  assert.match(
    cleanup,
    /markRemoteDeletionComplete[\s\S]*?finishRemoteAcceptance/u,
  );
  assert.match(identity, /retryAuthorizedOperationId/u);
  assert.match(
    identity,
    /matchesRetryGoogleSubject[\s\S]*?Auth\.auth\(\)\.signIn/u,
  );
  assert.match(
    identity,
    /matchesRetryFirebaseUID[\s\S]*?matchesRetryAccount[\s\S]*?startOrReplay/u,
  );
  assert.match(
    identity,
    /signOutDeletionRecoverySession[\s\S]*?markRemoteAcceptanceConfirmed/u,
  );
  assert.match(cleanup, /clearAfterRemoteCompletion/u);
  assert.match(
    bridge,
    /continue-with-google[\s\S]*?retryAuthorizedOperationId[\s\S]*?executeGoogleDeletionRecovery/u,
  );
  assert.match(bridge, /case \.submitted:[\s\S]*?accountFromCurrentState/u);
});

test('recovery deletes only explicitly fresh Firebase replacements', () => {
  const recoveryMethod = identity.slice(
    identity.indexOf('func continueAccountDeletionRecoveryWithGoogle('),
    identity.indexOf(
      '/// Ensures the callable',
      identity.indexOf('func continueAccountDeletionRecoveryWithGoogle('),
    ),
  );
  assert.ok(
    recoveryMethod.indexOf('matchesRetryGoogleSubject') <
      recoveryMethod.indexOf('Auth.auth().signIn'),
  );
  assert.match(
    recoveryMethod,
    /firebaseResult\.additionalUserInfo\?\.isNewUser == true/u,
  );
  assert.ok(
    recoveryMethod.indexOf('additionalUserInfo?.isNewUser == true') <
      recoveryMethod.indexOf('firebaseResult.user.providerData.first'),
  );
  assert.ok(
    recoveryMethod.indexOf('deleteReplacementFirebaseUser') <
      recoveryMethod.indexOf('firebaseResult.user.providerData.first'),
  );
  assert.match(recoveryMethod, /if !uidMatchesOriginal/u);
  assert.ok(
    recoveryMethod.indexOf('deleteReplacementFirebaseUser') <
      recoveryMethod.indexOf('startOrReplay'),
  );
  assert.match(identity, /try await user\.delete\(\)/u);
  assert.match(identity, /Auth\.auth\(\)\.currentUser == nil/u);
});

test('signed-out receipt lookup and local-erasure proof require both SDK sessions absent', () => {
  const statusLookup = cleanup.slice(
    cleanup.indexOf('statusCompletions.append(completion)'),
    cleanup.indexOf('receiptClient.check'),
  );
  assert.match(statusLookup, /beginSignedOutDeletionReceiptLookup/u);
  assert.match(statusLookup, /finishStatusCheck\(\.unavailable\)/u);

  const lookupGate = identity.slice(
    identity.indexOf('func beginSignedOutDeletionReceiptLookup('),
    identity.indexOf('func completeSignOutAfterSafetyShutdown('),
  );
  assert.match(lookupGate, /signOutDeletionRecoverySession\(\)/u);
  assert.match(lookupGate, /guard signedOut/u);
  assert.match(
    lookupGate,
    /acquireIdentityOperation\(\.deletionReceiptLookup\)/u,
  );
  assert.match(lookupGate, /finishSignedOutDeletionReceiptLookup/u);
  assert.match(
    lookupGate,
    /releaseIdentityOperation\(\.deletionReceiptLookup\)/u,
  );

  assert.match(
    identity,
    /func continueWithGoogle[\s\S]*?acquireIdentityOperation\(\.ordinarySignIn\)[\s\S]*?defer \{ self\.releaseIdentityOperation\(\.ordinarySignIn\) \}/u,
  );
  assert.match(
    identity,
    /func continueAccountDeletionRecoveryWithGoogle[\s\S]*?acquireIdentityOperation\(\.deletionRecovery\)[\s\S]*?defer \{ self\.releaseIdentityOperation\(\.deletionRecovery\) \}/u,
  );
  assert.match(
    identity,
    /func ensureRecentExactGoogleAuthentication[\s\S]*?acquireIdentityOperation\(\.recentAuthentication\)[\s\S]*?defer \{ releaseIdentityOperation\(\.recentAuthentication\) \}/u,
  );
  assert.match(
    identity,
    /private var identityOperationInFlight: IOSGoogleIdentityOperation\?/u,
  );
  assert.match(
    cleanup,
    /statusCheckOwnsIdentityLease = true[\s\S]*?finishStatusCheck[\s\S]*?finishSignedOutDeletionReceiptLookup/u,
  );

  const localShutdown = identity.slice(
    identity.indexOf('func completeAccountDeletionLocalShutdown('),
    identity.indexOf('func wipeLocalDataAfterSafetyShutdown('),
  );
  const firstFirebaseAbsence = localShutdown.indexOf(
    'Auth.auth().currentUser == nil',
  );
  const firstGoogleAbsence = localShutdown.indexOf(
    'GIDSignIn.sharedInstance.currentUser == nil',
  );
  const destructiveStore = localShutdown.indexOf(
    'peopleStore.destroyAfterRemoteAccountDeletion',
  );
  assert.ok(
    firstFirebaseAbsence >= 0 && firstFirebaseAbsence < destructiveStore,
  );
  assert.ok(firstGoogleAbsence >= 0 && firstGoogleAbsence < destructiveStore);
  assert.match(
    localShutdown.slice(destructiveStore),
    /Auth\.auth\(\)\.currentUser == nil[\s\S]*?GIDSignIn\.sharedInstance\.currentUser == nil/u,
  );
  const cleanupCommit = cleanup.slice(
    cleanup.indexOf('destroyCompanionDataAfterAccountDeletion'),
    cleanup.indexOf('private func finish(receipt:'),
  );
  assert.ok(
    cleanupCommit.indexOf('deletionSDKSessionIsAbsent') <
      cleanupCommit.indexOf('markLocalDataErased'),
  );

  const freshReplacement = identity.slice(
    identity.indexOf('if firebaseResult.additionalUserInfo?.isNewUser == true'),
    identity.indexOf('guard !firebaseResult.user.isAnonymous'),
  );
  assert.match(
    freshReplacement,
    /guard self\.signOutDeletionRecoverySession\(\) else/u,
  );
  const mismatchedReplacement = identity.slice(
    identity.indexOf('if !uidMatchesOriginal'),
    identity.indexOf('let ephemeralBinding'),
  );
  assert.match(
    mismatchedReplacement,
    /guard self\.signOutDeletionRecoverySession\(\) else/u,
  );
  assert.doesNotMatch(mismatchedReplacement, /deleteReplacementFirebaseUser/u);
});

test('ordinary onboarding cleans only definitely fresh pre-attach Firebase users', () => {
  const attach = identity.slice(
    identity.indexOf('private func attachGoogleUser('),
    identity.indexOf('private func firebaseAppCheckGate('),
  );
  assert.match(
    attach,
    /firebaseUserWasCreated = result\.additionalUserInfo\?\.isNewUser == true/u,
  );
  assert.ok(
    attach.match(/deleteDefinitelyFreshUser: firebaseUserWasCreated/g)
      ?.length >= 3,
  );

  const attached = attach.slice(
    attach.indexOf('case .attached:'),
    attach.indexOf('case .accountMismatch:'),
  );
  assert.match(attached, /deleteDefinitelyFreshUser: false/u);
  assert.doesNotMatch(
    attached,
    /deleteDefinitelyFreshUser: firebaseUserWasCreated/u,
  );

  const storageFailure = attach.slice(attach.indexOf('case .storageFailure:'));
  assert.match(
    storageFailure,
    /peopleStore\.durableAttachmentState\(for: binding\)/u,
  );
  assert.match(storageFailure, /durableState == \.notAttached/u);
  assert.match(
    storageFailure,
    /deleteDefinitelyFreshUser: firebaseUserWasCreated && mayDeleteFreshUser/u,
  );

  const ambiguousResolution = peopleStore.slice(
    peopleStore.indexOf('func durableAttachmentState('),
    peopleStore.indexOf('func hasCompletedSyncGeneration('),
  );
  assert.match(ambiguousResolution, /readExistingSnapshotWithoutRepair\(\)/u);
  assert.match(ambiguousResolution, /snapshot\.binding == binding/u);
  assert.doesNotMatch(ambiguousResolution, /removeItem|deleteKey|persist\(/u);

  const failedSessionCleanup = identity.slice(
    identity.indexOf('private func endFailedIdentitySession('),
    identity.indexOf('private static func deleteReplacementFirebaseUser('),
  );
  assert.match(failedSessionCleanup, /try\? Auth\.auth\(\)\.signOut\(\)/u);
  assert.match(failedSessionCleanup, /GIDSignIn\.sharedInstance\.signOut\(\)/u);
  assert.match(
    failedSessionCleanup,
    /Auth\.auth\(\)\.currentUser == nil[\s\S]*?GIDSignIn\.sharedInstance\.currentUser == nil/u,
  );
});

test('ordinary identity retires prior completed deletion proof before any later account', () => {
  const retirement = receipt.slice(
    receipt.indexOf('func retireCompletedReceiptBeforeNewIdentity('),
    receipt.indexOf('func recordRemoteDraining('),
  );
  const recoveryClear = retirement.indexOf(
    'clearSynchronouslyForCompletedReceiptRetirement',
  );
  const receiptRemoval = retirement.indexOf('removeItem(at: url)');
  assert.ok(recoveryClear >= 0 && receiptRemoval > recoveryClear);
  assert.match(retirement, /receipt\.remoteDeletionComplete/u);
  assert.match(retirement, /hasPendingOrUnreadableJournal/u);

  const ordinarySignIn = identity.slice(
    identity.indexOf('func continueWithGoogle('),
    identity.indexOf('func exactSessionBinding('),
  );
  const chooser = ordinarySignIn.indexOf('GIDSignIn.sharedInstance.signIn');
  const priorProofRetirement = ordinarySignIn.indexOf(
    'retireCompletedReceiptBeforeNewIdentity',
  );
  const firebaseAttachment = ordinarySignIn.indexOf('attachGoogleUser');
  assert.ok(
    chooser >= 0 &&
      priorProofRetirement > chooser &&
      firebaseAttachment > priorProofRetirement,
  );
  assert.match(
    ordinarySignIn,
    /guard !self\.deletionReceiptLookupInFlight,[\s\S]*?retireCompletedReceiptBeforeNewIdentity\(\)[\s\S]*?else/u,
  );
  assert.match(ordinarySignIn, /canBeginOrdinaryGoogleSelection\(\)/u);
  assert.match(ordinarySignIn, /signOutDeletionRecoverySession\(\)/u);
});

test('latest deletion receipt returns strict unavailable as a successful projection', () => {
  const latest = bridge.slice(
    bridge.indexOf('case "latest-deletion-receipt":'),
    bridge.indexOf('case "public-resources":'),
  );
  assert.match(
    latest,
    /guard let receipt[\s\S]*?accountDeletionStateBlocksOrdinaryIdentity\(\)[\s\S]*?\.success\(\[[\s\S]*?"kind": "unavailable"/u,
  );
  assert.match(
    latest,
    /guard receipt\.localDataErased else \{[\s\S]*?\.success\(\[[\s\S]*?"kind": "unavailable"/u,
  );
  assert.doesNotMatch(
    latest,
    /\.failure\(Self\.temporarilyUnavailableProblem\("coordination-unavailable"\)\)/u,
  );
});

test('recovery source is compiled exactly once and private values never enter bridge payloads', () => {
  assert.equal(
    (project.match(/IOSAccountDeletionRecoveryStore\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
  assert.match(
    project,
    /path = BirthdayAutopilot\/Privacy\/IOSAccountDeletionRecoveryStore\.swift/u,
  );
  for (const forbidden of [
    'accountBindingSalt',
    'firebaseUIDDigest',
    'googleSubjectDigest',
    'retryAuthorizedOperationId()',
  ]) {
    if (forbidden.endsWith('()')) {
      continue;
    }
    assert.doesNotMatch(bridge, new RegExp(`"${forbidden}"`, 'u'));
  }
  assert.doesNotMatch(bridge, /operationId": receipt\.operationId/u);
});
