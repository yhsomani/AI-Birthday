import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const read = relative => readFileSync(`${root}${relative}`, 'utf8');

test('security reporting stays private and names the SMS safety boundary', () => {
  const policy = read('SECURITY.md');
  assert.match(policy, /private security-advisory channel/u);
  assert.match(policy, /unintended SMS/u);
  assert.match(policy, /duplicate prevention/u);
  assert.match(policy, /deletion fencing/u);
  assert.match(policy, /Do not send real user data/u);
  assert.doesNotMatch(policy, /<[^>]+>|TODO|TBD/u);
});

test('the incident runbook covers every binding operational incident', () => {
  const runbook = read('docs/OPERATIONS_RUNBOOK.md');
  for (const required of [
    'Release rollback or unsafe build',
    'Android signing-key incident',
    'HMAC pepper rotation',
    'Functions, Firestore, or regional outage',
    'Ledger corruption, disaster recovery, or duplicate report',
    'Account-deletion failure',
    'Gemini safety, privacy, or cost incident',
    'OAuth, Google People, or Firebase identity incident',
    'SEND_SMS policy, installer, carrier, or legal suspension',
    'Recovery checklist',
  ]) {
    assert.match(runbook, new RegExp(required.replaceAll(' ', '\\s+'), 'u'));
  }
  assert.match(runbook, /GlobalControl\.armingEnabled/u);
  assert.match(runbook, /continuityState.*FROZEN/su);
  assert.match(runbook, /previously issued permit may still cross/u);
  assert.match(runbook, /Do not delete or rewrite an Armed claim/u);
  assert.match(runbook, /built-in templates/u);
  assert.doesNotMatch(runbook, /<[^>]+>|TODO|TBD/u);
});

test('critical safety and release paths require a repository owner review', () => {
  const owners = read('.github/CODEOWNERS');
  for (const criticalPath of [
    '/PROJECT_ABOUT.md',
    '/backend/firestore.rules',
    '/android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/',
    '/android/app/src/main/java/com/yashsomani/birthdayautopilot/coordination/',
    '/ios/BirthdayAutopilot/Automation/',
    '/ios/BirthdayAutopilot/Privacy/',
    '/backend/functions/src/',
    '/android/app/build.gradle',
    '/tools/distribution-authority-pin.json',
    '/tools/validate-distribution-evidence.mjs',
  ]) {
    assert.match(
      owners,
      new RegExp(`^${criticalPath.replaceAll('/', '\\/')}\\s+@yhsomani$`, 'mu'),
    );
  }
});
