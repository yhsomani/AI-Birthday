import assert from 'node:assert/strict';
import {
  existsSync,
  readFileSync,
  readdirSync,
  realpathSync,
  rmSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const relativeSources = [
  'ios/BirthdayAutopilot/Automation/IOSCompanionPlanningIndex.swift',
  'ios/BirthdayAutopilot/Automation/IOSCompanionOccurrenceIdentity.swift',
  'ios/BirthdayAutopilot/Automation/IOSCompanionTerminalLedger.swift',
];
const relativeHarness = 'tests/ios/CompanionCapacityFoundationTests.swift';
const sourcePaths = relativeSources.map(path =>
  fileURLToPath(new URL(path, root)),
);
const harnessPath = fileURLToPath(new URL(relativeHarness, root));

const read = relativePath =>
  readFileSync(fileURLToPath(new URL(relativePath, root)), 'utf8');

test('iOS companion capacity foundations remain compact and content-minimized', () => {
  const planning = read(relativeSources[0]);
  const identity = read(relativeSources[1]);
  const terminal = read(relativeSources[2]);
  const harness = read(relativeHarness);

  assert.match(planning, /static let planningDayCount = 400/u);
  assert.match(planning, /static let maximumContactCount = 10_000/u);
  assert.match(
    planning,
    /static let maximumRecordCount =[\s\S]*maximumOccurrencesPerContact/u,
  );
  assert.match(planning, /contactTableDigest: Data/u);
  assert.match(planning, /previous\.map\(\{ \$0 < ordinal \}\)/u);
  assert.match(planning, /offset == payload\.count/u);
  assert.match(planning, /enum IOSCompanionLazyOrdinalScanner/u);
  assert.match(
    planning,
    /if let material = materialize\(context, Int\(ordinal\)\)/u,
  );
  assert.match(planning, /enum IOSCompanionConfiguredContactScanner/u);
  assert.match(planning, /for index in configurations\.indices/u);
  assert.match(planning, /enum IOSCompanionTrustedClockPolicy/u);
  assert.match(
    planning,
    /static let maximumLocalSkew: TimeInterval = 5 \* 60/u,
  );
  assert.match(planning, /abs\(skew\) <= maximumLocalSkew/u);
  assert.doesNotMatch(
    planning,
    /\blet (?:contactIdentifier|contactName|destination|messageBody|recipient):/u,
  );

  assert.match(identity, /HMAC<SHA256>/u);
  assert.match(identity, /birthday-autopilot\.ios\.occurrence\.v1/u);
  assert.match(identity, /constantTimeEqual/u);
  assert.match(identity, /configurationGeneration/u);
  assert.match(identity, /timeZoneIdentifier/u);
  assert.match(identity, /contactTableDigest/u);

  assert.match(terminal, /static let maximumEntryCount = 20_000/u);
  assert.match(terminal, /static let maximumBucketCount = 400/u);
  assert.match(terminal, /legacySuppressAll/u);
  assert.match(terminal, /case cancelled/u);
  assert.match(terminal, /case failed/u);
  assert.match(terminal, /case outcomeUnknown/u);
  assert.match(terminal, /case reportedSent/u);
  assert.match(
    terminal,
    /detailedRetention: TimeInterval = 30 \* 24 \* 60 \* 60/u,
  );
  assert.match(terminal, /trustedTimeFreshness: TimeInterval = 5 \* 60/u);
  assert.doesNotMatch(
    terminal,
    /\b(?:let|var) (?:contactIdentifier|contactName|destination|messageBody|recipient):/u,
  );

  assert.match(harness, /oneDay\[0\] = \(0\.\.<10_000\)/u);
  assert.match(harness, /maximumIndex\.recordCount == 20_000/u);
  assert.match(harness, /an invalid first candidate hid a later valid one/u);
  assert.match(
    harness,
    /a 10k invalid-prefix scan rebuilt People or destination counts/u,
  );
  assert.match(
    harness,
    /identifierReads == 10_000 && predicateReads == 10_000/u,
  );
  assert.match(harness, /a day-ahead local clock was trusted/u);
  assert.match(harness, /five-minute boundary was rejected/u);
  assert.match(harness, /a truncated planning payload was accepted/u);
  assert.match(harness, /duplicate ordinal was accepted/u);
  assert.match(harness, /legacy date-wide fence/u);
  assert.match(harness, /terminal ledger dropped markers/u);
});

test('production iOS companion adopts the compact plan and independent terminal ledger', () => {
  const store = read('ios/BirthdayAutopilot/CompanionProtectedStore.swift');
  const engine = read(
    'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
  );
  const message = read('ios/BirthdayAutopilot/CompanionMessageModule.swift');
  const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');

  assert.match(store, /static let currentSchemaVersion = 3/u);
  assert.match(store, /private func migrateSchemaV2/u);
  assert.match(store, /snapshot\.planningIndex = nil/u);
  assert.match(store, /snapshot\.terminalLedger = ledger/u);
  assert.match(store, /private static let maximumProposals = 1/u);
  assert.match(
    store,
    /private static let maximumReminderPlans = IOSCompanionPlanningIndex\.planningDayCount/u,
  );
  assert.match(
    store,
    /ledger\.recordCommitted[\s\S]*?snapshot\.composerRecords\.append/u,
  );
  assert.match(store, /ledger\.resolve[\s\S]*?snapshot\.proposals\.removeAll/u);
  assert.match(store, /hasLegacyDateWideFence/u);
  const contactClear =
    store.match(
      /func clearContactDerivedState[\s\S]*?func transitionPrivacyOperation/u,
    )?.[0] ?? '';
  assert.match(contactClear, /promoteAllToLegacyDateWideFences/u);
  assert.doesNotMatch(
    contactClear,
    /occurrenceNamespace\s*=|terminalLedger\s*=\s*IOSCompanionTerminalLedger\(\)/u,
  );

  assert.match(engine, /var ordinalsByDay = \[\[UInt16\]\]/u);
  assert.match(engine, /IOSCompanionPlanningIndex\(/u);
  assert.match(engine, /lazyProposalMaterial\(/u);
  assert.match(engine, /IOSCompanionOccurrenceIdentity\.proposalHandle/u);
  assert.match(engine, /contactIds: \[\], messageDraft: nil/u);
  const nextLazyScan =
    engine.match(
      /private func nextLazyProposalMaterial[\s\S]*?private func firstLazyProposalMaterial/u,
    )?.[0] ?? '';
  assert.match(nextLazyScan, /IOSCompanionLazyOrdinalScanner\.first/u);
  assert.match(nextLazyScan, /lazyProposalMaterialContext/u);

  const firstLazyScan =
    engine.match(
      /private func firstLazyProposalMaterial[\s\S]*?private func lazyProposalMaterialContext/u,
    )?.[0] ?? '';
  assert.match(firstLazyScan, /if let material = lazyProposalMaterial/u);
  assert.match(firstLazyScan, /return material[\s\S]*?\}\s*\}\s*return nil/u);

  const lazyContext =
    engine.match(
      /private func lazyProposalMaterialContext[\s\S]*?private func lazyProposalMaterial\(/u,
    )?.[0] ?? '';
  assert.equal(
    lazyContext.match(/peopleStore\.privateSnapshot\(\)/gu)?.length,
    1,
    'one lazy scan must build exactly one private People snapshot',
  );
  assert.doesNotMatch(lazyContext, /peopleStore\.privateContacts\(\)/u);
  assert.match(
    lazyContext,
    /peopleSnapshotGeneration: peopleSnapshot\.generation/u,
  );
  assert.match(
    lazyContext,
    /destinationCounts: Self\.enabledDestinationCounts/u,
  );
  assert.match(
    lazyContext,
    /if requireTrustedFreshness[\s\S]*?IOSCompanionTrustedClockPolicy\.materializationNow/u,
  );
  assert.match(lazyContext, /trustedServerEstimate: status\.trustedNow/u);
  assert.match(lazyContext, /materializationNow = trustedMaterializationNow/u);

  const lazyCandidate =
    engine.match(
      /private func lazyProposalMaterial\([\s\S]*?private func validatedPlanningDescriptorContext/u,
    )?.[0] ?? '';
  assert.match(
    lazyCandidate,
    /terminalLedger\.check[\s\S]*?context\.privateContactsById/u,
  );
  assert.doesNotMatch(lazyCandidate, /peopleStore\.privateContacts\(\)/u);
  assert.doesNotMatch(lazyCandidate, /enabledDestinationCounts\(/u);

  const reviewHash =
    engine.match(
      /private static func reviewHash[\s\S]*?private static func messageReviewHash/u,
    )?.[0] ?? '';
  assert.match(reviewHash, /let configurationById = Dictionary/u);
  assert.match(reviewHash, /let configuration = configurationById\[id\]/u);
  assert.doesNotMatch(reviewHash, /workflow\.contacts\.first/u);

  const prepareActivation =
    engine.match(
      /private func prepareActivation[\s\S]*?private func confirmActivation/u,
    )?.[0] ?? '';
  assert.equal(
    prepareActivation.match(/peopleStore\.privateContacts\(\)/gu)?.length,
    1,
    'activation preparation must use one People snapshot',
  );
  assert.match(prepareActivation, /contacts: privateContacts/u);
  const confirmActivation =
    engine.match(
      /private func confirmActivation[\s\S]*?private func pauseAll/u,
    )?.[0] ?? '';
  assert.equal(
    confirmActivation.match(/peopleStore\.privateContacts\(\)/gu)?.length,
    1,
    'activation confirmation must use one People snapshot',
  );
  assert.match(confirmActivation, /contacts: privateContacts/u);

  const destinationBlock =
    engine.match(
      /private func mutateSelectedDestinationBlock[\s\S]*?private func previewMessage/u,
    )?.[0] ?? '';
  assert.match(destinationBlock, /let configurationIndexById = Dictionary/u);
  assert.match(
    destinationBlock,
    /IOSCompanionConfiguredContactScanner\.matchingIndices/u,
  );
  assert.match(destinationBlock, /for index in affectedIndices/u);
  assert.match(destinationBlock, /workflow\.contacts\[index\]/u);
  assert.doesNotMatch(destinationBlock, /for affected in contacts/u);
  assert.doesNotMatch(
    destinationBlock,
    /Self\.contactConfiguration\(affected/u,
  );
  assert.doesNotMatch(destinationBlock, /Self\.upsert\(configuration/u);
  assert.doesNotMatch(
    engine.match(
      /private func rebuildPlan[\s\S]*?private static func planRebuildOutcome/u,
    )?.[0] ?? '',
    /CompanionApprovedProposal\(|CompanionWorkflowOccurrence\(/u,
  );

  assert.match(message, /requireTrustedFreshness: true/u);
  assert.match(message, /finalMaterial == expectedMaterial/u);
  assert.match(message, /material: finalMaterial/u);
  assert.match(
    store,
    /maximumReminderPlans = IOSCompanionPlanningIndex\.planningDayCount/u,
  );
  assert.match(
    store,
    /Set\(snapshot\.reminderPlans\.map\(\\\.civilDate\)\)\.count[\s\S]*?snapshot\.reminderPlans\.count/u,
  );

  for (const file of relativeSources) {
    const name = file.split('/').at(-1);
    assert.equal(
      project.match(new RegExp(`${name} in Sources`, 'gu'))?.length,
      2,
      `${name} must have one build-file declaration and one Sources entry`,
    );
  }
});

test(
  'pure iOS capacity policies compile and execute without Xcode project membership',
  { skip: process.platform !== 'darwin', timeout: 120_000 },
  () => {
    const binary = join(
      tmpdir(),
      `birthday-ios-capacity-foundations-${process.pid}-${Date.now()}`,
    );
    const environment = {
      ...process.env,
      CLANG_MODULE_CACHE_PATH: join(tmpdir(), 'birthday-clang-module-cache'),
      SWIFT_MODULECACHE_PATH: join(tmpdir(), 'birthday-swift-module-cache'),
    };

    let successfulCompilation;
    const failures = [];
    for (const sdk of swiftSdkCandidates()) {
      const args = [
        ...(sdk === null ? [] : ['-sdk', sdk]),
        '-swift-version',
        '5',
        '-warnings-as-errors',
        '-O',
        '-o',
        binary,
        ...sourcePaths,
        harnessPath,
      ];
      const result = spawnSync('swiftc', args, {
        encoding: 'utf8',
        env: environment,
        maxBuffer: 10 * 1_024 * 1_024,
        timeout: 120_000,
      });
      if (result.status === 0) {
        successfulCompilation = result;
        break;
      }
      failures.push(
        `${sdk ?? 'default SDK'}:\n${
          result.stderr || result.error || 'unknown error'
        }`,
      );
    }

    try {
      assert.ok(
        successfulCompilation,
        `Swift capacity foundations did not compile:\n${failures.join('\n')}`,
      );
      const execution = spawnSync(binary, [], {
        encoding: 'utf8',
        env: environment,
        maxBuffer: 10 * 1_024 * 1_024,
        timeout: 30_000,
      });
      assert.equal(execution.status, 0, execution.stderr);
      const measurement = execution.stdout.match(
        /IOS_CAPACITY_FOUNDATIONS_OK planning_ms=(\d+) scan_ms=(\d+) terminal_ms=(\d+) terminal_bytes=(\d+) total_ms=(\d+)/u,
      );
      assert.ok(measurement, execution.stdout);
      // These are host-tool smoke ceilings, not release-device performance
      // evidence. The printed measurements remain available to the signed
      // device gate without making a shared CI runner spuriously flaky.
      assert.ok(Number(measurement[1]) < 25_000, execution.stdout);
      assert.ok(Number(measurement[2]) < 25_000, execution.stdout);
      assert.ok(Number(measurement[3]) < 25_000, execution.stdout);
      assert.ok(Number(measurement[4]) < 1_000_000, execution.stdout);
      assert.ok(Number(measurement[5]) < 30_000, execution.stdout);
    } finally {
      rmSync(binary, { force: true });
    }
  },
);

function swiftSdkCandidates() {
  const values = [];
  const add = value => {
    if (typeof value !== 'string' || value.length === 0 || !existsSync(value)) {
      return;
    }
    const resolved = realpathSync(value);
    if (!values.includes(resolved)) values.push(resolved);
  };

  add(process.env.SDKROOT);
  const commandLineSdkRoot = '/Library/Developer/CommandLineTools/SDKs';
  if (existsSync(commandLineSdkRoot)) {
    const installed = readdirSync(commandLineSdkRoot)
      .filter(name => /^MacOSX\d+(?:\.\d+)?\.sdk$/u.test(name))
      .sort((left, right) =>
        left.localeCompare(right, 'en', { numeric: true }),
      );
    for (const name of installed) add(join(commandLineSdkRoot, name));
  }

  const sdkRoot = spawnSync('xcrun', ['--show-sdk-path'], {
    encoding: 'utf8',
  });
  if (sdkRoot.status === 0) add(sdkRoot.stdout.trim());

  values.push(null);
  return values;
}
