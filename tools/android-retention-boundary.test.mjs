import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');

const dao = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/RetentionDao.kt',
);
const worker = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/workers/DataRetentionWorker.kt',
);
const scheduler = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/workers/AutomationScheduler.kt',
);
const factory = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/workers/BirthdayWorkerFactory.kt',
);
const receipt = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/TestReceiptFactory.kt',
);

test('retention is startup plus daily bounded work with an explicit continuation', () => {
  assert.match(scheduler, /PeriodicWorkRequestBuilder<DataRetentionWorker>/u);
  assert.match(scheduler, /OneTimeWorkRequestBuilder<DataRetentionWorker>/u);
  assert.match(scheduler, /DATA_RETENTION_INTERVAL_HOURS = 24L/u);
  assert.match(scheduler, /ExistingPeriodicWorkPolicy\.UPDATE/u);
  assert.match(scheduler, /ExistingWorkPolicy\.KEEP/u);
  assert.match(factory, /DataRetentionWorker::class\.java\.name/u);
  assert.match(worker, /BATCH_SIZE = 256/u);
  assert.match(worker, /MAX_BATCHES_PER_RUN = 8/u);
  assert.match(worker, /callbackBacklog \|\| pruned\.moreWork/u);
  assert.match(worker, /Result\.retry\(\)/u);
  assert.match(worker, /LifecycleJournalStatus\.UNREADABLE/u);
  assert.match(worker, /MUTATING_LIFECYCLE_ACTIONS/u);
  assert.match(worker, /greatestTrustedServerMillis/u);
  assert.match(dao, /MAX\(greatestTrustedServerMillis\)/u);
});

test('database pruning cannot erase the 400-day Birthday safety ledger', () => {
  assert.doesNotMatch(dao, /DELETE FROM birthday_occurrences_v2/u);
  assert.doesNotMatch(dao, /DELETE FROM local_destination_guards_v2/u);
  assert.match(
    dao,
    /DELETE FROM coordination_permits_v2[\s\S]*?WHERE purpose = 'TEST'/u,
  );
  assert.match(
    dao,
    /DELETE FROM send_attempts_v2[\s\S]*?permit\.purpose = 'TEST'/u,
  );
});

test('raw TEST detail and closed callback identities have narrow 30-day predicates', () => {
  for (const field of [
    'normalizedDestination',
    'maskedDestination',
    'exactMessage',
    'foregroundConfirmationNonceHash',
    'foregroundConfirmedAtMillis',
  ]) {
    assert.match(dao, new RegExp(`${field} = (?:''|0)`, 'u'));
  }
  assert.match(dao, /retentionUntilMillis <= :nowMillis/u);
  assert.match(dao, /testJobsWithExpiredSensitiveDetail[\s\S]*?List<String>/u);
  assert.match(dao, /'COORDINATION_UNKNOWN'[\s\S]*?'RECEIPT_INVALIDATED'/u);
  assert.match(dao, /state IN \('RETIRED', 'EXPIRED'\)/u);
  assert.match(dao, /expiresAtMillis <= :nowMillis/u);
  assert.match(dao, /LIMIT :limit/u);
  assert.match(
    dao,
    /receipt\.state = 'VALID'[\s\S]*?receipt\.invalidatedAtMillis IS NULL/u,
  );
});

test('receipt validation accepts only complete or atomically redacted TEST detail', () => {
  assert.match(receipt, /enum class State \{ FULL, REDACTED, INVALID \}/u);
  assert.match(
    receipt,
    /retainedDetail == TestJobRetainedDetail\.State\.INVALID/u,
  );
  assert.match(
    receipt,
    /retainedDetail == TestJobRetainedDetail\.State\.REDACTED[\s\S]*?test\.retentionUntilMillis > nowMillis/u,
  );
  assert.match(receipt, /TestReceiptCanonicalHash\.bindingHash/u);
});
