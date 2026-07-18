import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, unlinkSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');
const policies = read(
  'ios/BirthdayAutopilot/Automation/IOSNativeBoundaryPolicies.swift',
);
const store = read('ios/BirthdayAutopilot/CompanionProtectedStore.swift');
const reminder = read('ios/BirthdayAutopilot/CompanionReminderModule.swift');
const router = read(
  'ios/BirthdayAutopilot/Notifications/IOSCompanionNotificationRouter.swift',
);
const attention = read(
  'ios/BirthdayAutopilot/Notifications/IOSCompanionAttentionNotifier.swift',
);
const composer = read('ios/BirthdayAutopilot/CompanionMessageModule.swift');
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
const workflow = read('.github/workflows/ci.yml');

test('protected store uses the executable authenticated envelope and fail-closed load policy', () => {
  assert.match(policies, /AES\.GCM\.seal/u);
  assert.match(policies, /AES\.GCM\.open/u);
  assert.match(
    policies,
    /guard fileExists else \{ return \.installFencedReset \}/u,
  );
  assert.match(policies, /case \.missing:[\s\S]*?return \.installFencedReset/u);
  assert.match(
    policies,
    /case \.notChecked, \.unavailable:[\s\S]*?return \.refuseAccess/u,
  );
  assert.match(store, /IOSProtectedStoreEnvelope\.open/u);
  assert.match(store, /IOSProtectedStoreEnvelope\.encode\(snapshot\)/u);
  assert.match(store, /IOSProtectedStoreEnvelope\.seal/u);
  assert.match(store, /IOSProtectedStoreSchemaPolicy\.action/u);
  assert.match(store, /func migrateSchemaV2ForNativeTests/u);
});

test('reminder and notification router production singletons retain system wiring with internal seams', () => {
  assert.match(
    reminder,
    /static let shared = CompanionReminderCoordinator\([\s\S]*?IOSSystemCompanionNotificationCenterClient\(\)[\s\S]*?CompanionProtectedStore\.shared[\s\S]*?IOSCompanionAttentionNotifier\.shared/u,
  );
  assert.match(reminder, /protocol IOSCompanionNotificationCenterClient/u);
  assert.match(reminder, /protocol IOSCompanionReminderStore/u);
  assert.match(reminder, /protocol IOSCompanionAttentionNotifying/u);
  assert.match(
    reminder,
    /waitForNotificationAddsToDrain[\s\S]*?removeAndVerifyAppOwnedNotifications/u,
  );
  assert.match(
    router,
    /static let shared = IOSCompanionNotificationRouter\([\s\S]*?CompanionProtectedStore\.shared[\s\S]*?IOSSystemCompanionRoutePrivacyGate[\s\S]*?IOSSystemCompanionProtectedDataStatus/u,
  );
  assert.match(router, /protocol IOSCompanionNativeRouteStore/u);
  assert.match(router, /protocol IOSCompanionRoutePrivacyGate/u);
  assert.match(router, /protocol IOSCompanionProtectedDataStatus/u);
  assert.match(
    router,
    /IOSCompanionWipeRecoveryStore\.shared\.hasPendingOrUnreadableJournal\(\)/u,
  );
  assert.match(
    attention,
    /IOSCompanionWipeRecoveryStore\.shared\.hasPendingOrUnreadableJournal\(\)/u,
  );
});

test('MessageUI delegate uses the production terminal sequencer and never gains a send API', () => {
  assert.match(
    composer,
    /func messageComposeViewController[\s\S]*?IOSComposerDelegateTerminalSequencer\.finish/u,
  );
  assert.match(
    policies,
    /persist\(terminal\.persistedOutcome\)[\s\S]*?dismiss[\s\S]*?completion\(persisted \? terminal\.publicOutcome : "unknown"\)/u,
  );
  assert.match(
    composer,
    /guard controller === presentedController else \{[\s\S]*?guard !isCompletingPresentation else \{ return \}[\s\S]*?IOSComposerDelegateTerminalSequencer\.finish/u,
  );
  assert.match(
    composer,
    /if isCompletingPresentation \{\s*\/\/[^\n]*\n\s*\/\/[^\n]*\n\s*accountDeletionShutdownWaiters\.append\(completion\)\s*return\s*\}/u,
  );
  assert.match(
    composer,
    /accountDeletionShutdownWaiters\.append\(completion\)\s*isCompletingPresentation = true\s*let resolve = pendingResolve\s*store\.finishComposerOperation/u,
  );
  assert.match(
    composer,
    /let shutdownWaiters = accountDeletionShutdownWaiters[\s\S]*?accountDeletionShutdownWaiters\.removeAll\(\)[\s\S]*?DispatchQueue\.main\.async/u,
  );
  assert.doesNotMatch(
    composer,
    /sendMessage|sendText|SMSCompose|UIApplication\.shared\.open\([^)]*sms:/u,
  );
});

test('native component XCTest sources are hosted and standalone policy tests are a CI gate', () => {
  for (const file of [
    'IOSNativeBoundaryPolicies.swift',
    'IOSNativeComponentBoundaryTests.swift',
  ]) {
    assert.match(project, new RegExp(`${file} in Sources`, 'u'));
  }
  const applicationSourcesStart = project.indexOf(
    '13B07F871A680F5B00A75B9A /* Sources */ = {',
  );
  const testSourcesStart = project.indexOf(
    'A2A000092F10000100000009 /* Sources */ = {',
  );
  const sourcesEnd = project.indexOf('/* End PBXSourcesBuildPhase section */');
  assert.ok(
    applicationSourcesStart >= 0 &&
      applicationSourcesStart < testSourcesStart &&
      testSourcesStart < sourcesEnd,
  );
  const applicationSources = project.slice(
    applicationSourcesStart,
    testSourcesStart,
  );
  const testSources = project.slice(testSourcesStart, sourcesEnd);
  assert.match(
    applicationSources,
    /IOSNativeBoundaryPolicies\.swift in Sources/u,
  );
  assert.doesNotMatch(
    applicationSources,
    /IOSNativeComponentBoundaryTests\.swift in Sources/u,
  );
  assert.match(
    testSources,
    /IOSNativeComponentBoundaryTests\.swift in Sources/u,
  );
  assert.doesNotMatch(
    testSources,
    /IOSNativeBoundaryPolicies\.swift in Sources/u,
  );
  assert.match(
    workflow,
    /Run standalone iOS protected-store and composer boundary contracts[\s\S]*?IOSNativeBoundaryPolicyTests/u,
  );
  assert.match(
    workflow,
    /Run hosted iOS native unit tests[\s\S]*?-only-testing:BirthdayAutopilotTests/u,
  );
});

test(
  'protected-store and composer boundary policies compile and execute',
  { skip: process.platform !== 'darwin', timeout: 60_000 },
  () => {
    const binary = join(
      tmpdir(),
      `birthday-ios-native-boundaries-${process.pid}-${Date.now()}`,
    );
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
        'ios/BirthdayAutopilot/Automation/IOSNativeBoundaryPolicies.swift',
        'tests/ios/IOSNativeBoundaryPolicyTests.swift',
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
      assert.equal(result.status, 0, result.stderr || result.stdout);
      const execution = spawnSync(binary, [], {
        encoding: 'utf8',
        timeout: 10_000,
      });
      assert.equal(execution.status, 0, execution.stderr || execution.stdout);
      assert.match(
        execution.stdout,
        /iOS native boundary policy tests passed/u,
      );
    } finally {
      if (existsSync(binary)) unlinkSync(binary);
    }
  },
);
