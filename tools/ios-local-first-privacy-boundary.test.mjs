import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');
const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const store = read('ios/BirthdayAutopilot/CompanionProtectedStore.swift');
const people = read(
  'ios/BirthdayAutopilot/Database/CompanionPeopleStore.swift',
);
const identity = read(
  'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
);
const bridge = read('ios/BirthdayAutopilot/BirthdayNativeModule.swift');
const composer = read('ios/BirthdayAutopilot/CompanionMessageModule.swift');
const background = read(
  'ios/BirthdayAutopilot/Contacts/IOSPeopleBackgroundRefreshCoordinator.swift',
);

const section = (source, start, end) => {
  const startIndex = source.indexOf(start);
  assert.notEqual(startIndex, -1, `missing section start: ${start}`);
  const endIndex = source.indexOf(end, startIndex + start.length);
  assert.notEqual(endIndex, -1, `missing section end: ${end}`);
  return source.slice(startIndex, endIndex);
};

test('iOS disconnect and revoke clear local contact material before remote work', () => {
  const action = section(
    workflow,
    'private func performPrivacyAction(',
    'private func performPrivacyActionAfterPeopleSyncFence(',
  );
  assert.ok(
    action.indexOf('invalidateOutstandingSync') <
      action.indexOf('suspendForPrivacyOperation'),
  );
  assert.ok(
    action.indexOf('suspendForPrivacyOperation') <
      action.lastIndexOf('performPrivacyActionAfterPeopleSyncFence'),
  );

  const local = section(
    workflow,
    'private func performLocalContactsDisconnect(',
    'private func performContactDerivedReset(',
  );
  const cancel = local.indexOf('cancelPlansAndNotifications');
  const clearPeople = local.indexOf('clearContactsRetainingBinding');
  const clearDerived = local.indexOf('clearContactDerivedState');
  const beginRemote = local.indexOf('performContactDerivedReset');
  assert.ok(cancel >= 0 && cancel < clearPeople);
  assert.ok(clearPeople < clearDerived && clearDerived < beginRemote);
  assert.match(local, /expectedBinding: binding/u);
  assert.match(local, /completionPhase:[\s\S]*?"local-cleared"/u);
  assert.doesNotMatch(local, /ensureRecentExactGoogleAuthentication/u);
  assert.doesNotMatch(local, /contactResetClient/u);

  const atomicClear = section(
    store,
    'func clearContactDerivedState(',
    'func markContactsProviderRevoked(',
  );
  assert.match(atomicClear, /\$0\.phase == "local-wiping"/u);
  assert.match(atomicClear, /recordDisclosureRevoked/u);
  assert.match(atomicClear, /workflow\.contacts\.removeAll\(\)/u);
  assert.match(atomicClear, /workflow\.occurrences\.removeAll\(\)/u);
  assert.match(atomicClear, /workflow\.reviews\.removeAll\(\)/u);
  assert.match(atomicClear, /snapshot\.proposals\.removeAll\(\)/u);
  assert.match(atomicClear, /Self\.applyReminderPlans\(\[\], to: &snapshot\)/u);
  assert.match(atomicClear, /snapshot\.pendingNativeRoute = nil/u);
});

test('People clear and wipe prove their encrypted postconditions', () => {
  const clear = section(
    people,
    'func clearContactsRetainingBinding(',
    'func wipe(',
  );
  assert.match(clear, /snapshot\.binding == expectedBinding/u);
  assert.ok(
    clear.indexOf('try self.persist(snapshot)') < clear.indexOf('let verified'),
  );
  assert.match(clear, /verified\.binding == expectedBinding/u);
  assert.match(clear, /verified\.contacts\.isEmpty/u);
  assert.match(clear, /verified\.sync\.nextSyncToken == nil/u);
  assert.match(clear, /verified\.sync\.lastSuccessAt == nil/u);

  const wipe = section(
    people,
    'func wipe(',
    'func destroyAfterRemoteAccountDeletion(',
  );
  assert.ok(wipe.indexOf('removeItem') < wipe.indexOf('deleteKey'));
  assert.ok(wipe.indexOf('deleteKey') < wipe.indexOf('let snapshot'));
  assert.match(wipe, /verified\.binding == nil/u);
  assert.match(wipe, /verified\.contacts\.isEmpty/u);
  assert.match(wipe, /verified\.sync\.nextSyncToken == nil/u);
});

test('provider revoke is ordered reset, official disconnect, durable marker, scope receipt, SDK cleanup', () => {
  const remote = section(
    workflow,
    'private func performContactDerivedRemoteReset(',
    'private func disconnectGoogleProviderAfterRemoteReset(',
  );
  assert.ok(
    remote.indexOf('ensureRecentExactGoogleAuthentication') <
      remote.indexOf('contactResetClient.startOrReplay'),
  );
  assert.match(
    remote,
    /case \.failure\(let failure\):[\s\S]*?phase: "local-cleared"/u,
  );
  assert.match(
    remote,
    /guard case \.success = reauthentication else \{[\s\S]*?phase: "local-cleared"/u,
  );

  const provider = section(
    workflow,
    'private func disconnectGoogleProviderAfterRemoteReset(',
    'private func markProviderRevoked(',
  );
  const disconnect = provider.indexOf(
    'disconnectGoogleProviderAfterLocalCleanup',
  );
  const guardSuccess = provider.indexOf('guard providerDisconnected');
  const mark = provider.indexOf('markProviderRevoked');
  const finish = provider.indexOf('finishProviderRevocationCleanup');
  assert.ok(disconnect >= 0 && disconnect < guardSuccess);
  assert.ok(guardSuccess < mark && mark < finish);
  assert.doesNotMatch(provider.slice(0, mark), /recordContactsScopeRevoked/u);

  const durableMarker = section(
    store,
    'func markContactsProviderRevoked(',
    'func replaceWorkflowPlan(',
  );
  assert.match(durableMarker, /self\.transaction/u);
  assert.doesNotMatch(durableMarker, /expectedRevision/u);
  assert.match(
    durableMarker,
    /phase == "provider-revoked"[\s\S]*?return workflow\.privacyOperations\[operationIndex\]/u,
  );
  assert.match(durableMarker, /phase = "provider-revoked"/u);

  const cleanup = section(
    workflow,
    'private func finishProviderRevocationCleanup(',
    'private func performAccountDeletion(',
  );
  assert.ok(
    cleanup.indexOf('recordContactsScopeRevoked') <
      cleanup.indexOf('finishRevokedGoogleSDKCleanupAfterLocalCleanup'),
  );
  assert.match(
    cleanup,
    /phase: sdkCleanupComplete \? "complete" : "provider-revoked"/u,
  );
  assert.match(
    workflow,
    /case "remote-pending", "local-cleared", "provider-revoked":[\s\S]*?"kind": "remote-pending"/u,
  );
});

test('stale concurrent resume callbacks cannot roll privacy milestones backward', () => {
  const transition = section(
    store,
    'func transitionPrivacyOperation(',
    'func markContactsProviderRevoked(',
  );
  assert.match(
    transition,
    /\["complete", "failed"\]\.contains\(current\.phase\)/u,
  );
  assert.match(
    transition,
    /current\.phase == "provider-revoked"[\s\S]*?!\["provider-revoked", "complete"\]\.contains\(requestedPhase\)/u,
  );
  assert.match(
    transition,
    /action == "revoke-google-access"[\s\S]*?"local-cleared", "verifying", "remote-draining"[\s\S]*?\["local-wiping", "remote-pending"\]\.contains\(requestedPhase\)/u,
  );
  const update = section(
    workflow,
    'private func updatePrivacyOperation(',
    'func recordContactsConsent(',
  );
  assert.match(update, /store\.transitionPrivacyOperation/u);
  assert.doesNotMatch(update, /mutateWorkflow|expectedRevision/u);
  assert.match(
    workflow,
    /phase: "local-wiping", reason: nil[\s\S]*?payload\["kind"\] as\? String == "local-wiping"/u,
  );
  assert.match(
    workflow,
    /phase: "verifying", reason: nil[\s\S]*?payload\["kind"\] as\? String == "verifying"/u,
  );
});

test('sign-out wipe attempts every local cleanup even when an SDK sign-out fails', () => {
  const signOut = section(
    identity,
    'func completeSignOutAfterSafetyShutdown(',
    'func disconnectGoogleProviderAfterLocalCleanup(',
  );
  assert.doesNotMatch(signOut, /guard configuration != nil/u);
  assert.match(
    signOut,
    /catch \{[\s\S]*?firebaseSignOutSucceeded = false[\s\S]*?GIDSignIn\.sharedInstance\.signOut\(\)/u,
  );
  const googleSignOut = signOut.indexOf('GIDSignIn.sharedInstance.signOut()');
  const peopleCleanup = signOut.indexOf('let peopleCleanupSucceeded');
  const companionCleanup = signOut.indexOf(
    'invalidateCompanionAccountSession()',
  );
  assert.ok(googleSignOut >= 0 && googleSignOut < peopleCleanup);
  assert.ok(peopleCleanup < companionCleanup);
  assert.match(signOut, /peopleStore\.wipe/u);
  assert.match(
    signOut,
    /guard firebaseSignOutSucceeded, sdkSessionsAbsent,[\s\S]*?peopleCleanupSucceeded, companionSessionInvalidated[\s\S]*?enterIdentitySafetyInterlock/u,
  );

  const signOutAction = section(
    workflow,
    'case "sign-out-wipe":\n      // Remove reminders',
    'case "wipe-local-data":\n      reminderCoordinator',
  );
  const cancel = signOutAction.indexOf('cancelPlansAndNotifications');
  const identityCleanup = signOutAction.indexOf(
    'completeSignOutAfterSafetyShutdown',
  );
  const companionWipe = signOutAction.indexOf('wipeCompanionData');
  assert.ok(
    cancel >= 0 && cancel < identityCleanup && identityCleanup < companionWipe,
  );
  assert.match(
    signOutAction,
    /guard success else \{[\s\S]*?phase: "remote-pending"/u,
  );
});

test('pending privacy cleanup blocks foreground and background contact repopulation', () => {
  assert.match(background, /privacy-suspended-v1/u);
  assert.match(
    background,
    /var contactsAccessIsSuspendedForPrivacy: Bool \{ privacySuspended \}/u,
  );
  assert.match(
    background,
    /func scheduleForConnectedSession[\s\S]*?guard registered, !privacySuspended/u,
  );
  assert.match(
    background,
    /private func handle[\s\S]*?guard registered, !privacySuspended, activeTask == nil/u,
  );
  assert.match(
    background,
    /func suspendForPrivacyOperation[\s\S]*?defaults\.set\(true[\s\S]*?cancelForDisconnectedSession/u,
  );
  assert.match(
    bridge,
    /if intent == "continue-with-google"[\s\S]*?privacyOperations\.contains[\s\S]*?!\["complete", "failed"\]\.contains/u,
  );
  assert.match(
    bridge,
    /if intent == "authorize-contacts" \|\| intent == "sync-contacts"[\s\S]*?privacyOperations\.contains[\s\S]*?!\["complete", "failed"\]\.contains/u,
  );
  assert.match(
    bridge,
    /private func executePeopleSync[\s\S]*?readProjectionStatus[\s\S]*?privacyOperations\.contains[\s\S]*?executePeopleSyncAfterPrivacyRecheck[\s\S]*?resumeAfterExplicitContactsAction[\s\S]*?peopleSync\.sync/u,
  );
  assert.match(bridge, /private var lifecycleMutationInProgress = false/u);
  assert.match(
    bridge,
    /intent == "confirm-privacy-action"[\s\S]*?intent == "resume-lifecycle-operation"[\s\S]*?beginLifecycleMutation/u,
  );
  assert.match(
    composer,
    /func canPresent[\s\S]*?contactsAccessIsSuspendedForPrivacy/u,
  );
  assert.match(
    composer,
    /prepareComposerReview[\s\S]*?privacyOperations\.contains[\s\S]*?refreshPeopleBeforeReview/u,
  );
  assert.match(
    composer,
    /private func refreshPeopleBeforeReview[\s\S]*?contactsAccessIsSuspendedForPrivacy[\s\S]*?peopleSync\.sync/u,
  );
});
