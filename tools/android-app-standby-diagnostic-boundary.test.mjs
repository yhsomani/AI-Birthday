import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

const diagnostic = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/readiness/AppStandbyBucketDiagnostic.kt',
);
const lifecycle = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/lifecycle/AndroidLifecycleController.kt',
);
const automationGateSources = [
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/readiness/AndroidReadinessProbe.kt',
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/readiness/DistributionEligibility.kt',
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/readiness/ReadinessEvaluator.kt',
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AndroidAutomationEnvironment.kt',
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AndroidAutomationOrchestrator.kt',
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/sms/SmsGateway.kt',
].map(read);

test('App Standby bucket is sampled as content-free diagnostic evidence', () => {
  assert.match(
    diagnostic,
    /UsageStatsManager::class\.java\)\?\.appStandbyBucket/u,
  );
  assert.match(diagnostic, /catch \(_: SecurityException\)/u);
  assert.match(diagnostic, /catch \(_: RuntimeException\)/u);
  assert.match(diagnostic, /else -> AppStandbyBucketDiagnostic\.UNKNOWN/u);
  assert.doesNotMatch(diagnostic, /Log\.|print(?:ln)?\(|exception\.message/u);
  assert.match(
    lifecycle,
    /AndroidAppStandbyBucketDiagnosticReader\(context\)\.read\(\)\.wireCode/u,
  );
  assert.match(
    lifecycle,
    /listOf\(standbyCode\) \+ activityCodes \+ schedulerCodes/u,
  );
});

test('App Standby bucket cannot become an automation readiness predicate', () => {
  automationGateSources.forEach(source => {
    assert.doesNotMatch(
      source,
      /AndroidAppStandbyBucketDiagnosticReader|AppStandbyBucketDiagnostic|appStandbyBucket|UsageStatsManager/u,
    );
  });
});
