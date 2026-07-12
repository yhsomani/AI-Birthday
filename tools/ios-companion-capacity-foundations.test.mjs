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
  assert.match(planning, /static let maximumRecordCount =[\s\S]*maximumOccurrencesPerContact/u);
  assert.match(planning, /contactTableDigest: Data/u);
  assert.match(planning, /previous\.map\(\{ \$0 < ordinal \}\)/u);
  assert.match(planning, /offset == payload\.count/u);
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
  assert.match(terminal, /case \.cancelled/u);
  assert.match(terminal, /case \.failed/u);
  assert.match(terminal, /case outcomeUnknown/u);
  assert.match(terminal, /case reportedSent/u);
  assert.match(terminal, /detailedRetention: TimeInterval = 30 \* 24 \* 60 \* 60/u);
  assert.match(terminal, /trustedTimeFreshness: TimeInterval = 5 \* 60/u);
  assert.doesNotMatch(
    terminal,
    /\b(?:contactIdentifier|contactName|destination|messageBody|recipient)\b/u,
  );

  assert.match(harness, /oneDay\[0\] = \(0\.\.<10_000\)/u);
  assert.match(harness, /maximumIndex\.recordCount == 20_000/u);
  assert.match(harness, /a truncated planning payload was accepted/u);
  assert.match(harness, /duplicate ordinal was accepted/u);
  assert.match(harness, /legacy date-wide fence/u);
  assert.match(harness, /terminal ledger dropped markers/u);
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
      CLANG_MODULE_CACHE_PATH: join(
        tmpdir(),
        'birthday-clang-module-cache',
      ),
      SWIFT_MODULECACHE_PATH: join(
        tmpdir(),
        'birthday-swift-module-cache',
      ),
    };

    let successfulCompilation;
    const failures = [];
    for (const sdk of swiftSdkCandidates()) {
      const args = [
        ...(sdk === null ? [] : ['-sdk', sdk]),
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
        `${sdk ?? 'default SDK'}:\n${result.stderr || result.error || 'unknown error'}`,
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
        /IOS_CAPACITY_FOUNDATIONS_OK planning_ms=(\d+) terminal_ms=(\d+) terminal_bytes=(\d+) total_ms=(\d+)/u,
      );
      assert.ok(measurement, execution.stdout);
      assert.ok(Number(measurement[1]) < 10_000, execution.stdout);
      assert.ok(Number(measurement[2]) < 10_000, execution.stdout);
      assert.ok(Number(measurement[3]) < 1_000_000, execution.stdout);
      assert.ok(Number(measurement[4]) < 30_000, execution.stdout);
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
      .sort((left, right) => left.localeCompare(right, 'en', { numeric: true }));
    for (const name of installed) add(join(commandLineSdkRoot, name));
  }

  const sdkRoot = spawnSync('xcrun', ['--show-sdk-path'], {
    encoding: 'utf8',
  });
  if (sdkRoot.status === 0) add(sdkRoot.stdout.trim());

  values.push(null);
  return values;
}
