import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path =>
  readFileSync(path, 'utf8').replace(/\r\n/gu, '\n');
const recovery = read(
  'ios/BirthdayAutopilot/Privacy/IOSCompanionWipeRecoveryStore.swift',
);
const store = read('ios/BirthdayAutopilot/CompanionProtectedStore.swift');
const engine = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const identity = read(
  'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
);
const lifecycle = read('ios/BirthdayAutopilot/CompanionReminderModule.swift');
const reservationJournal = read(
  'ios/BirthdayAutopilot/Automation/IOSComposerReservationJournal.swift',
);
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');

const section = (source, start, end) => {
  const startIndex = source.indexOf(start);
  assert.notEqual(startIndex, -1, `missing section start: ${start}`);
  const endIndex = source.indexOf(end, startIndex + start.length);
  assert.notEqual(endIndex, -1, `missing section end: ${end}`);
  return source.slice(startIndex, endIndex);
};

test('independent reset marker is content-free, protected, backup-excluded, and verified', () => {
  const journal = section(
    recovery,
    'struct IOSCompanionWipeRecoveryJournal:',
    'final class IOSCompanionWipeRecoveryStore',
  );
  assert.doesNotMatch(
    journal,
    /contact|recipient|destination|message|body|displayEmail|displayName|phone/iu,
  );
  assert.match(recovery, /accountBindingSalt/u);
  assert.match(recovery, /firebaseUIDDigest/u);
  assert.match(recovery, /googleSubjectDigest/u);
  assert.match(recovery, /SHA256\.hash/u);
  assert.match(recovery, /constantTimeEqual/u);

  const persist = section(
    recovery,
    'private func persist(',
    'private func journalFileURL()',
  );
  assert.match(persist, /\.atomic, \.completeFileProtection/u);
  assert.match(persist, /FileProtectionType\.complete/u);
  assert.match(persist, /isExcludedFromBackup = true/u);
  assert.match(persist, /loadCurrent\(\) == journal/u);
  assert.match(recovery, /maximumFileBytes = 4_096/u);
  assert.match(project, /IOSCompanionWipeRecoveryStore\.swift in Sources/u);
});

test('protected reset commits intent before file/key destruction and clears only after disk verification', () => {
  const reset = section(
    store,
    'private func resetStore(',
    'private func initializeMissingStore(',
  );
  const arm = reset.indexOf('armCompanionReset');
  const remove = reset.indexOf('removeItem');
  const deleteKey = reset.indexOf('deleteKeychainKey');
  const persist = reset.indexOf('try persist(snapshot)');
  const verify = reset.indexOf('persistedResetSnapshotMatches');
  const installed = reset.indexOf('markCompanionResetInstalled');
  const clear = reset.indexOf('clearIfComplete');
  assert.ok(arm >= 0 && arm < remove);
  assert.ok(remove < deleteKey && deleteKey < persist);
  assert.ok(persist < verify && verify < installed && installed < clear);

  const load = section(
    store,
    'private func loadSnapshot()',
    'private func resetStore(',
  );
  assert.match(load, /hasPendingOrUnreadableJournal/u);
  assert.match(load, /observeResetCivilDate/u);
  assert.match(load, /throw CompanionStoreError\.storageUnavailable/u);
  assert.match(
    load,
    /!journal\.companionResetInstalled[\s\S]*?!persistedResetSnapshotMatches/u,
  );
  assert.match(load, /installResetSnapshot/u);

  const missing = section(
    store,
    'private func initializeMissingStore(',
    'private func persistedResetSnapshotMatches(',
  );
  assert.match(missing, /resetStore\(at: fileURL, now: Date\(\)\)/u);
  assert.doesNotMatch(missing, /blockedCivilDates:\s*\[\]/u);
  assert.match(
    store,
    /static func initialInstall\([\s\S]*?blockedCivilDates: \[civilDate\]/u,
  );
});

test('reviewed wipe saga is durable before destructive work and retires after every store', () => {
  const actions = section(
    engine,
    'private func performPrivacyActionAfterPeopleSyncFence(',
    'private func performLocalContactsDisconnect(',
  );
  assert.ok(
    actions.indexOf('beginSaga(') <
      actions.lastIndexOf('switch operation.action'),
  );

  const signOut = section(
    actions,
    'case "sign-out-wipe":\n      // Remove reminders',
    'case "wipe-local-data":\n      reminderCoordinator',
  );
  assert.ok(
    signOut.indexOf('completeSignOutAfterSafetyShutdown') <
      signOut.indexOf('markLocalCleanupComplete'),
  );
  assert.ok(
    signOut.indexOf('markLocalCleanupComplete') <
      signOut.indexOf('IOSComposerReservationJournal.shared.destroyAll'),
  );
  assert.ok(
    signOut.indexOf('IOSComposerReservationJournal.shared.destroyAll') <
      signOut.indexOf('markReservationJournalDestroyed'),
  );
  assert.ok(
    signOut.indexOf('markReservationJournalDestroyed') <
      signOut.indexOf('wipeCompanionData'),
  );
  assert.ok(
    signOut.indexOf('wipeCompanionData') <
      signOut.indexOf('markNotificationCleanupVerified'),
  );
  assert.ok(
    signOut.indexOf('markNotificationCleanupVerified') <
      signOut.indexOf('clearCompletedSaga'),
  );

  const local = section(
    actions,
    'case "wipe-local-data":\n      reminderCoordinator',
    'case "revoke-google-access":',
  );
  assert.ok(
    local.indexOf('wipeCompanionData') <
      local.indexOf('markNotificationCleanupVerified'),
  );
  assert.ok(
    local.indexOf('markNotificationCleanupVerified') <
      local.indexOf('wipeLocalDataAfterSafetyShutdown'),
  );
  assert.ok(
    local.indexOf('wipeLocalDataAfterSafetyShutdown') <
      local.indexOf('markLocalCleanupComplete'),
  );
  assert.ok(
    local.indexOf('markLocalCleanupComplete') <
      local.indexOf('IOSComposerReservationJournal.shared.destroyAll'),
  );
  assert.ok(
    local.indexOf('IOSComposerReservationJournal.shared.destroyAll') <
      local.indexOf('markReservationJournalDestroyed'),
  );
  assert.ok(
    local.indexOf('markReservationJournalDestroyed') <
      local.indexOf('clearCompletedSaga'),
  );

  const destroy = section(
    reservationJournal,
    'func destroyAll()',
    'private func load()',
  );
  assert.match(destroy, /destroyFileAndVerifyAbsence/u);
  assert.match(
    reservationJournal,
    /private func destroyFileAndVerifyAbsence[\s\S]*?removeItem[\s\S]*?!fileManager\.fileExists/u,
  );
  assert.match(
    recovery,
    /func clearCompletedSaga[\s\S]*?journal\.reservationJournalDestroyed/u,
  );
  assert.match(
    recovery,
    /ensureReservationJournalDestroyed[\s\S]*?destroyAll\(\)[\s\S]*?markReservationJournalDestroyed/u,
  );

  const revokedCleanup = section(
    identity,
    'func finishRevokedGoogleSDKCleanupAfterLocalCleanup()',
    'func completeAccountDeletionLocalShutdown()',
  );
  assert.match(
    revokedCleanup,
    /IOSComposerReservationJournal\.shared\.destroyAll\(\)/u,
  );
  const deletionCleanup = section(
    identity,
    'func completeAccountDeletionLocalShutdown()',
    'func wipeLocalDataAfterSafetyShutdown(',
  );
  assert.match(
    deletionCleanup,
    /IOSComposerReservationJournal\.shared\.destroyAll\(\)/u,
  );
});

test('cold-launch recovery blocks ordinary identity and never wipes a mismatched account', () => {
  assert.match(
    lifecycle,
    /IOSCompanionWipeRecoveryStore\.shared\.hasPendingOrUnreadableJournal\(\)[\s\S]*?IOSCompanionWipeRecoveryCoordinator\.shared\.resumeIfNeeded\(\)/u,
  );
  assert.match(
    identity,
    /if IOSCompanionWipeRecoveryStore\.shared\.hasPendingOrUnreadableJournal\(\)[\s\S]*?transition\(\.reconnectRequired\)[\s\S]*?resumeIfNeeded/u,
  );
  assert.match(
    identity,
    /canBeginOrdinaryGoogleSelection[\s\S]*?!IOSCompanionWipeRecoveryStore\.shared\.hasPendingOrUnreadableJournal/u,
  );
  const recoveredSignOut = section(
    identity,
    'func resumeJournaledSignOutWipe(',
    'func resumeJournaledLocalDataWipe(',
  );
  assert.ok(
    recoveredSignOut.indexOf('recoveryStore.matches(binding: stored') <
      recoveredSignOut.indexOf('Auth.auth().signOut()'),
  );
  assert.ok(
    recoveredSignOut.indexOf('matchesProviderIdentity') <
      recoveredSignOut.indexOf('peopleStore.wipe'),
  );
  const recoveredLocal = section(
    identity,
    'private func recoverBindingForJournaledLocalWipe(',
    'private func restorePreviousSession()',
  );
  assert.ok(
    recoveredLocal.indexOf('matchesGoogleSubject(restored.userID') <
      recoveredLocal.indexOf('Auth.auth().signIn'),
  );
  assert.match(recoveredLocal, /allowedJournaledWipeRecoveryScopes/u);
  assert.match(
    identity,
    /allowedJournaledWipeRecoveryScopes[\s\S]*?scopes\.contains\(birthdayContactsReadOnlyScope\)/u,
  );
  assert.ok(
    recoveredLocal.lastIndexOf('matchesProviderIdentity') <
      recoveredLocal.indexOf('return IOSNativeGoogleAccountBinding'),
  );
});

test('reset and multi-store recovery state machine closes every modeled crash point', () => {
  const resume = state => {
    const recovered = { ...state };
    if (!recovered.localCleanupComplete) recovered.localCleanupComplete = true;
    if (!recovered.reservationJournalDestroyed) {
      recovered.reservationJournalDestroyed = true;
    }
    if (!recovered.companionResetInstalled) {
      recovered.companionResetRequired = true;
      recovered.companionResetInstalled = true;
    }
    recovered.notificationCleanupVerified = true;
    recovered.markerPresent = !(
      recovered.localCleanupComplete &&
      recovered.reservationJournalDestroyed &&
      recovered.companionResetInstalled &&
      recovered.notificationCleanupVerified
    );
    return recovered;
  };

  const checkpoints = [
    {
      markerPresent: true,
      companionResetRequired: false,
      companionResetInstalled: false,
      notificationCleanupVerified: false,
      reservationJournalDestroyed: false,
      localCleanupComplete: false,
    },
    {
      markerPresent: true,
      companionResetRequired: true,
      companionResetInstalled: false,
      notificationCleanupVerified: false,
      reservationJournalDestroyed: false,
      localCleanupComplete: false,
    },
    {
      markerPresent: true,
      companionResetRequired: true,
      companionResetInstalled: true,
      notificationCleanupVerified: false,
      reservationJournalDestroyed: false,
      localCleanupComplete: false,
    },
    {
      markerPresent: true,
      companionResetRequired: false,
      companionResetInstalled: false,
      notificationCleanupVerified: false,
      reservationJournalDestroyed: false,
      localCleanupComplete: true,
    },
    {
      markerPresent: true,
      companionResetRequired: true,
      companionResetInstalled: true,
      notificationCleanupVerified: true,
      reservationJournalDestroyed: true,
      localCleanupComplete: true,
    },
  ];
  for (const checkpoint of checkpoints) {
    assert.equal(checkpoint.markerPresent, true);
    const completed = resume(checkpoint);
    assert.equal(completed.localCleanupComplete, true);
    assert.equal(completed.companionResetInstalled, true);
    assert.equal(completed.notificationCleanupVerified, true);
    assert.equal(completed.reservationJournalDestroyed, true);
    assert.equal(completed.markerPresent, false);
    assert.deepEqual(resume(completed), completed);
  }

  const reset = {
    dates: ['2026-07-13'],
    overflowed: false,
    installed: true,
  };
  const observe = (state, civilDate) => {
    if (state.dates.includes(civilDate)) return { ...state };
    if (state.dates.length >= 8) {
      return { ...state, overflowed: true, installed: false };
    }
    return {
      dates: [...state.dates, civilDate].sort(),
      overflowed: state.overflowed,
      installed: false,
    };
  };
  const rolled = observe(reset, '2026-07-14');
  assert.deepEqual(rolled.dates, ['2026-07-13', '2026-07-14']);
  assert.equal(rolled.installed, false);
  let saturated = reset;
  for (let day = 14; day <= 21; day += 1) {
    saturated = observe(saturated, `2026-07-${day}`);
  }
  assert.equal(saturated.dates.length, 8);
  assert.equal(saturated.overflowed, true);
  assert.equal(saturated.installed, false);
});
