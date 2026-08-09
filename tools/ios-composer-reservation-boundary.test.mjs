import assert from 'node:assert/strict';
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const read = path =>
  readFileSync(path, 'utf8').replace(/\r\n/gu, '\n');
const backendModel = read('backend/functions/src/domain/model.ts');
const backendService = read('backend/functions/src/services/controlPlane.ts');
const backendFunctions = read('backend/functions/src/functions/index.ts');
const backendPaths = read('backend/functions/src/persistence/paths.ts');
const backendSchemas = read('backend/functions/src/transport/schemas.ts');
const client = read(
  'ios/BirthdayAutopilot/Automation/IOSComposerReservationClient.swift',
);
const journal = read(
  'ios/BirthdayAutopilot/Automation/IOSComposerReservationJournal.swift',
);
const reservationPolicy = read(
  'ios/BirthdayAutopilot/Automation/IOSComposerReservationPolicy.swift',
);
const composer = read('ios/BirthdayAutopilot/CompanionMessageModule.swift');
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
const androidOrchestrator = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AndroidAutomationOrchestrator.kt',
);
const androidWorker = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/workers/ReconcileWorker.kt',
);
const androidBridge = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/bridge/BirthdayNativeModule.kt',
);
const androidAttention = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/attention/AndroidAttentionNotifications.kt',
);

const section = (source, start, end) => {
  const startIndex = source.indexOf(start);
  assert.notEqual(startIndex, -1, `missing section start: ${start}`);
  const endIndex = source.indexOf(end, startIndex + start.length);
  assert.notEqual(endIndex, -1, `missing section end: ${end}`);
  return source.slice(startIndex, endIndex);
};

test('server reservation is top-level, content-free and logically bounded', () => {
  const model = section(
    backendModel,
    'export interface IOSComposerReservation',
    '/**\n * A short-lived, account-global mutation fence.',
  );
  assert.match(model, /reservationKey: string/u);
  assert.match(model, /phase: 'PREPARED' \| 'COMMITTED'/u);
  assert.match(model, /expiresAtMs: number/u);
  assert.doesNotMatch(
    model,
    /contact|civilDate|destination|phone|body|message|recipient/u,
  );
  assert.match(
    backendPaths,
    /iosComposerReservation: db\.collection\('iosComposerReservations'\)\.doc\(uid\)/u,
  );
  assert.match(backendModel, /IOS_COMPOSER_RESERVATION_MS = 72 \* HOUR_MS/u);
  assert.match(backendSchemas, /reservationId: canonicalLowercaseUUID/u);
  assert.doesNotMatch(
    section(
      backendSchemas,
      'const iosComposerReservationBody',
      'export type RegistrationRequest',
    ),
    /contact|civilDate|destination|phone|body|message|recipient/u,
  );
});

test('all reservation callables are authenticated and consume limited-use App Check', () => {
  assert.match(backendFunctions, /enforceAppCheck: true/u);
  assert.match(backendFunctions, /consumeAppCheckToken: true/u);
  for (const [start, end] of [
    [
      'export const acquireIOSComposerReservation',
      'export const commitIOSComposerReservation',
    ],
    [
      'export const commitIOSComposerReservation',
      'export const releaseIOSComposerReservation',
    ],
    [
      'export const releaseIOSComposerReservation',
      'export const sweepDeletionDrains',
    ],
  ]) {
    const callable = section(backendFunctions, start, end);
    assert.match(callable, /commonOptions/u);
    assert.match(callable, /requireAuthenticated\(request\)/u);
  }
  const acquire = section(
    backendService,
    'public async acquireIOSComposerReservation',
    'public async commitIOSComposerReservation',
  );
  assert.match(acquire, /reservationExpiresAtMs/u);
  assert.doesNotMatch(acquire, /reservationKey:/u);
});

test('every Android sender mutation contends on the one reservation document', () => {
  const ranges = [
    [
      'public async requestContactDerivedReset',
      'public async requestSenderRelease',
    ],
    [
      'public async requestSenderRelease',
      'private async advanceContactResetDrain',
    ],
    ['public async registerAndroidInstallation', 'public async renewLease'],
    ['public async renewLease', 'public async changeAccountMode'],
    ['public async changeAccountMode', 'public async claimBirthdayOccurrence'],
    ['private async claim(', 'public async armAttempt'],
    ['private async resolveArm(', 'private applyArmDecision'],
    ['public async authorizeSafeRetry', 'public async reportTestOutcome'],
    ['public async reportTestOutcome', 'public async beginTransfer'],
    ['public async beginTransfer', 'public async completeTransfer'],
    ['public async completeTransfer', 'public async beginDeletion'],
  ];
  for (const [start, end] of ranges) {
    const transaction = section(backendService, start, end);
    assert.match(transaction, /paths\.iosComposerReservation/u);
    assert.match(transaction, /iosComposerBlocksSenderMutation/u);
  }
  const deletion = section(
    backendService,
    'public async beginDeletion',
    'public async accountDeletionReceipt',
  );
  assert.match(deletion, /transaction\.get\(paths\.iosComposerReservation\)/u);
  assert.match(
    deletion,
    /transaction\.delete\(paths\.iosComposerReservation\)/u,
  );
});

test('native capability is exact-account, protected, bounded and never downgraded', () => {
  assert.match(journal, /firebaseUIDDigest/u);
  assert.match(journal, /googleSubjectDigest/u);
  assert.match(journal, /binding\.accountGeneration/u);
  assert.match(journal, /constantTimeEqual/u);
  assert.match(journal, /\.completeFileProtection/u);
  assert.match(journal, /isExcludedFromBackup = true/u);
  assert.match(journal, /maximumEntries = 8/u);
  const pruning = section(
    journal,
    'payload.entries.removeAll',
    'guard payload.entries.count',
  );
  assert.match(pruning, /IOSComposerReservationPruningPolicy/u);
  assert.match(reservationPolicy, /phase == \.prepared && expiresAt == nil/u);
  assert.match(reservationPolicy, /\$0 <= observedAt/u);
  assert.match(
    journal,
    /entry\.phase != \.sticky \|\| entry\.expiresAt != nil/u,
  );
  assert.match(
    journal,
    /existing\.phase == \.sticky \|\| !earlyReleaseAllowed \? \.sticky : \.prepared/u,
  );
  assert.match(journal, /case \.confirmedCorrupt:/u);
  assert.match(journal, /guard destroyFileAndVerifyAbsence\(\)/u);
  assert.match(
    journal,
    /catch \{[\s\S]*?return \.unavailable[\s\S]*?guard !data\.isEmpty/u,
  );
  assert.match(journal, /func destroyAll\(\) -> Bool/u);
  assert.match(
    journal,
    /return !fileManager\.fileExists\(atPath: url\.path\)/u,
  );
  assert.doesNotMatch(
    section(
      journal,
      'struct IOSComposerReservationCapability',
      'private struct IOSComposerReservationJournalPayload',
    ),
    /contact|civilDate|destination|phone|body|message|recipient/u,
  );
});

test('final user tap leases exact People material before sticky server commit and MessageUI', () => {
  assert.match(
    client,
    /HTTPSCallableOptions\(requireLimitedUseAppCheckTokens: true\)/u,
  );
  assert.match(client, /acquireIOSComposerReservation/u);
  assert.match(client, /commitIOSComposerReservation/u);
  assert.match(client, /releaseIOSComposerReservation/u);
  assert.match(client, /journal\.markStickyBeforeCommit/u);
  assert.match(client, /dismissalGuardSeconds: TimeInterval = 5 \* 60/u);
  assert.match(client, /DispatchTime\.now\(\)\.uptimeNanoseconds/u);

  const tap = section(
    composer,
    'private func acquirePeopleLeaseAndReservation',
    'func messageComposeViewController',
  );
  assert.ok(
    tap.indexOf('acquireComposerMaterialLease') <
      tap.indexOf('acquireImmediatelyBeforePresentation'),
  );
  assert.ok(
    tap.indexOf('acquireImmediatelyBeforePresentation') <
      tap.indexOf('commitStickyImmediatelyBeforePresentation'),
  );
  assert.ok(
    tap.indexOf('commitStickyImmediatelyBeforePresentation') <
      tap.indexOf('commitComposerOpen'),
  );
  assert.ok(tap.indexOf('commitComposerOpen') < tap.indexOf('.present('));
  assert.match(tap, /requireTrustedFreshness: true/u);
  assert.match(tap, /pendingPeopleLease == lease/u);
  assert.match(tap, /preparationStillValid/u);
  assert.match(tap, /expectedPeopleSnapshotGeneration/u);
  assert.match(tap, /releasePendingPeopleLease\(expected: lease\)/u);
});

test('every composer-open recheck fails closed throughout privacy wipe recovery', () => {
  const present = section(
    composer,
    'func presentUserConfirmedComposer(',
    'private func acquirePeopleLeaseAndReservation',
  );
  assert.match(present, /contactsAccessIsSuspendedForPrivacy/u);
  assert.match(present, /hasPendingOrUnreadableJournal\(\)/u);

  const recheck = section(
    composer,
    'private func preparationStillValid(',
    'private func cancelPendingPreparation',
  );
  assert.match(recheck, /contactsAccessIsSuspendedForPrivacy/u);
  assert.match(recheck, /hasPendingOrUnreadableJournal\(\)/u);

  const preconditions = section(
    composer,
    'private static func presentationPreconditionsHold()',
    'private static func foregroundPresenter()',
  );
  assert.match(preconditions, /contactsAccessIsSuspendedForPrivacy/u);
  assert.match(preconditions, /hasPendingOrUnreadableJournal\(\)/u);
});

test('Android treats the long iOS hold as a bounded scheduled pause, not a retry storm', () => {
  assert.match(
    androidOrchestrator,
    /IOSComposerReservationRecheckPolicy[\s\S]*?RECHECK_DELAY_MILLIS = 60L \* 60L \* 1_000L/u,
  );
  assert.match(
    androidOrchestrator,
    /safeCode = SAFE_CODE,[\s\S]*?retryRecommended = false,[\s\S]*?nextWakeAtMillis/u,
  );
  assert.match(
    androidOrchestrator,
    /RegistrationResolution\(null, outcome\.reason\)/u,
  );
  assert.match(
    androidOrchestrator,
    /BirthdayClaimResolution\(null, outcome\.reason\)/u,
  );
  assert.match(androidWorker, /ReconcileSuccessorPolicy\.nextRunAtMillis/u);
  assert.match(
    androidBridge,
    /"IOS_COMPOSER_RESERVED" to "ios-composer-reserved"/u,
  );
  assert.match(
    androidAttention,
    /"IOS_COMPOSER_RESERVED" to AttentionClassification\(AttentionCategory\.COORDINATION, 1\)/u,
  );
});

test('sticky presentation is dismissed before expiry/background and never early-released', () => {
  assert.match(composer, /UIApplication\.willResignActiveNotification/u);
  assert.match(composer, /scheduleReservationDismissal/u);
  assert.match(composer, /completeUnknownAfterPresentation/u);
  assert.match(composer, /cancelPendingPreparation/u);
  const cancel = section(
    composer,
    'private func cancelPendingPreparation',
    'private func releasePendingPeopleLease',
  );
  assert.match(cancel, /pendingPreparedReservation/u);
  assert.match(cancel, /grant\.earlyReleaseAllowed/u);
  assert.match(cancel, /releasePreparedReservation/u);
  const callback = section(
    composer,
    'func messageComposeViewController',
    'private func completePresentationFailure',
  );
  assert.doesNotMatch(callback, /releasePreparedReservation/u);
  assert.doesNotMatch(callback, /releaseIOSComposerReservation/u);
});

test('every post-commit definitive failure becomes Unknown when terminal persistence fails', () => {
  assert.match(
    reservationPolicy,
    /enum IOSComposerTerminalPersistencePolicy[\s\S]*case \.failure:[\s\S]*return \.outcomeUnknown/u,
  );
  const staleLease = section(
    composer,
    'guard retained else',
    'self.presentCommittedComposer',
  );
  const presentationRefused = section(
    composer,
    'private func presentCommittedComposer',
    'let controller = MFMessageComposeViewController()',
  );
  const presentationFailure = section(
    composer,
    'private func completePresentationFailure',
    'private func finishPostCommitFailure',
  );
  for (const failurePath of [
    staleLease,
    presentationRefused,
    presentationFailure,
  ]) {
    assert.match(failurePath, /finishPostCommitFailure/u);
    assert.match(failurePath, /case \.outcomeUnknown:/u);
    assert.match(failurePath, /resolve\?*\("unknown"\)/u);
    assert.doesNotMatch(failurePath, /finishComposerOperation/u);
  }
  assert.equal(
    composer.match(/finishPostCommitFailure\(operationId:/gu)?.length,
    3,
  );
  assert.equal(
    composer.match(/finishComposerOperation\([\s\S]{0,120}outcome: \.failed/gu)
      ?.length,
    1,
  );
  const persistenceBoundary = section(
    composer,
    'private func finishPostCommitFailure',
    'private func completeUnknownAfterPresentation',
  );
  assert.match(persistenceBoundary, /store\.finishComposerOperation/u);
  assert.match(
    persistenceBoundary,
    /IOSComposerTerminalPersistencePolicy\.disposition\(for: result\)/u,
  );
});

test('reservation sources are members of the iOS application target', () => {
  for (const file of [
    'IOSComposerReservationClient.swift',
    'IOSComposerReservationJournal.swift',
    'IOSComposerReservationPolicy.swift',
  ]) {
    assert.match(project, new RegExp(`${file} in Sources`, 'u'));
    assert.match(
      project,
      new RegExp(`BirthdayAutopilot/Automation/${file}`, 'u'),
    );
  }
});

test(
  'nil-expiry crash pruning policy compiles and executes',
  { skip: process.platform !== 'darwin', timeout: 60_000 },
  () => {
    const binary = join(
      tmpdir(),
      `birthday-ios-composer-policy-${process.pid}-${Date.now()}`,
    );
    const sources = [
      'ios/BirthdayAutopilot/Automation/IOSComposerReservationPolicy.swift',
      'tests/ios/ComposerReservationPolicyTests.swift',
    ];
    const sdk = '/Library/Developer/CommandLineTools/SDKs/MacOSX15.4.sdk';
    const result = spawnSync(
      'swiftc',
      [
        ...(existsSync(sdk) ? ['-sdk', sdk] : []),
        '-swift-version',
        '5',
        '-warnings-as-errors',
        '-o',
        binary,
        ...sources,
      ],
      {
        encoding: 'utf8',
        env: {
          ...process.env,
          CLANG_MODULE_CACHE_PATH: join(
            tmpdir(),
            'birthday-clang-module-cache',
          ),
          SWIFT_MODULECACHE_PATH: join(tmpdir(), 'birthday-swift-module-cache'),
        },
        timeout: 60_000,
      },
    );
    try {
      assert.equal(result.status, 0, result.stderr);
      const execution = spawnSync(binary, [], {
        encoding: 'utf8',
        timeout: 10_000,
      });
      assert.equal(execution.status, 0, execution.stderr);
      assert.match(execution.stdout, /IOS_COMPOSER_RESERVATION_POLICY_OK/u);
    } finally {
      rmSync(binary, { force: true });
    }
  },
);
