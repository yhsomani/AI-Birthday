import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

const freshness = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/PeopleDataFreshnessPolicy.kt',
);
const orchestrator = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AndroidAutomationOrchestrator.kt',
);
const environment = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/orchestration/AndroidAutomationEnvironment.kt',
);
const ledger = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/storage/database/SafetyLedgerDao.kt',
);
const scheduler = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/workers/AutomationScheduler.kt',
);
const worker = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/automation/workers/ReconcileWorker.kt',
);
const bridge = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/bridge/BirthdayNativeModule.kt',
);
const peopleSync = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/people/AndroidPeopleSyncService.kt',
);
const contactsConsent = read(
  'android/app/src/main/java/com/yashsomani/birthdayautopilot/people/ContactsConsentRecorder.kt',
);

test('every unattended Android lease and final gate enforces 30-day trusted People freshness', () => {
  assert.match(
    freshness,
    /MAXIMUM_UNATTENDED_AGE_MILLIS = 30L \* 24L \* 60L \* 60L \* 1_000L/u,
  );
  assert.match(freshness, /lastSuccessMillis > trustedNowMillis/u);
  assert.match(
    freshness,
    /Math\.subtractExact\(trustedNowMillis, lastSuccessMillis\)/u,
  );
  assert.match(
    orchestrator,
    /leasePreflightReady[\s\S]*?TrustedTimeEstimator\.estimate[\s\S]*?PeopleDataFreshnessPolicy\.allowsUnattendedAutomation\(sync, trustedNowMillis\)/u,
  );
  assert.match(
    environment,
    /TrustedTimeEstimator\.estimate[\s\S]*?contactsAuthorizationValid = PeopleDataFreshnessPolicy\.allowsUnattendedAutomation/u,
  );
  assert.match(
    ledger,
    /validateBirthdayPreflight[\s\S]*?!PeopleDataFreshnessPolicy\.allowsUnattendedAutomation\(sync, trustedNowMillis\)/u,
  );
});

test('network-attempt scheduling keeps one durable earliest wake and retires it by token', () => {
  const scheduleBlock =
    scheduler.match(
      /fun scheduleNetworkAttempt[\s\S]*?\n\s*\/\*\* Retires only/u,
    )?.[0] ?? '';
  assert.match(scheduleBlock, /minOf\(existingTarget, requestedRunAtMillis\)/u);
  assert.match(
    scheduleBlock,
    /!preferences\.contains\(inFlightPreferenceKey\(stableSuffix\)\)/u,
  );
  assert.doesNotMatch(scheduleBlock, /APPEND(?:_OR_REPLACE)?/u);
  assert.match(
    scheduleBlock,
    /putString\(tokenKey, token\)[\s\S]*?\.commit\(\)/u,
  );
  assert.match(
    scheduler,
    /consumeNetworkAttempt[\s\S]*?getString\(tokenKey, null\) != token[\s\S]*?remove\(targetPreferenceKey/u,
  );
  assert.match(
    scheduler,
    /completeNetworkAttempt[\s\S]*?pendingNetworkAttempt[\s\S]*?enqueueNetworkAttempt/u,
  );
  assert.match(
    scheduler,
    /enqueueNetworkAttempt[\s\S]*?ExistingWorkPolicy\.REPLACE/u,
  );
  assert.match(
    scheduler,
    /recoverNetworkAttempts[\s\S]*?IN_FLIGHT_KEY_PREFIX/u,
  );
  assert.match(
    worker,
    /override suspend fun doWork[\s\S]*?consumeNetworkAttempt[\s\S]*?finally[\s\S]*?completeNetworkAttempt/u,
  );
});

test('Contacts consent is proven by the dedicated disclosure action and gates real Birthday claims', () => {
  assert.match(
    bridge,
    /handlePeopleSyncIntent\([\s\S]*?disclosureAcknowledged = intent == "authorize-contacts"/u,
  );
  assert.match(
    peopleSync,
    /sync\([\s\S]*?interactiveAuthorization: Boolean,[\s\S]*?disclosureAcknowledged: Boolean = false/u,
  );
  assert.match(
    peopleSync,
    /if \(!disclosureAcknowledged\)[\s\S]*?consentRecorder\.disclosureState\(account\.accountId\)[\s\S]*?ContactsDisclosureState\.MISSING[\s\S]*?PeopleAuthorizationReason\.CONTACTS_DISCLOSURE[\s\S]*?val state = dao\.contactSyncState/u,
  );
  assert.match(
    contactsConsent,
    /disclosureState\(accountId: String\)[\s\S]*?ConsentKind\.CONTACTS_DISCLOSURE[\s\S]*?ContactsDisclosureState\.CURRENT[\s\S]*?ContactsDisclosureState\.MISSING/u,
  );
  assert.match(
    peopleSync,
    /recordGranted\([\s\S]*?disclosureAcknowledged = disclosureAcknowledged/u,
  );
  assert.match(
    contactsConsent,
    /!currentDisclosureIsGranted && disclosureAcknowledged/u,
  );
  assert.match(
    contactsConsent,
    /latestDisclosure\?\.decision != ConsentDecision\.GRANTED[\s\S]*?return@withTransaction false/u,
  );
  assert.match(
    ledger,
    /latestConsentRow\(occurrence\.accountId, ConsentKind\.CONTACTS_READONLY\)\?\.decision !=[\s\S]*?ConsentDecision\.GRANTED/u,
  );
});
