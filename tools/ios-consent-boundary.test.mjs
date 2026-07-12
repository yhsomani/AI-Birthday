import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = path => readFileSync(path, 'utf8');
const models = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowModels.swift',
);
const store = read('ios/BirthdayAutopilot/CompanionProtectedStore.swift');
const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const bridge = read('ios/BirthdayAutopilot/BirthdayNativeModule.swift');

test('iOS persists a bounded content-free Contacts consent ledger', () => {
  assert.match(models, /struct CompanionWorkflowConsentReceipt: Codable/u);
  assert.match(
    models,
    /var consentReceipts: \[CompanionWorkflowConsentReceipt\]\?/u,
  );
  assert.match(models, /maximumReceipts = 64/u);
  assert.match(models, /contacts-device-storage-v1/u);
  assert.match(models, /google-contacts-readonly-v1/u);
  assert.match(models, /birthdayContactsReadOnlyScope/u);
  assert.match(models, /scopeHash\.range\([\s\S]*?\^\[a-f0-9\]\{64\}\$/u);
  assert.match(models, /row\.supersedesReceiptId ==/u);
  assert.match(store, /IOSCompanionConsentLedgerPolicy\.isValid/u);
});

test('only the dedicated disclosure action can establish first Contacts consent', () => {
  assert.match(
    bridge,
    /executePeopleSync\([\s\S]*?disclosureAcknowledged: intent == "authorize-contacts"/u,
  );
  const syncIntentGate = bridge.match(
    /if intent == "sync-contacts"[\s\S]*?self\.executePeopleSync\(/u,
  )?.[0];
  assert.ok(syncIntentGate);
  assert.match(
    syncIntentGate,
    /hasCurrentContactsDisclosure\([\s\S]*?contactsAuthorizationRequired/u,
  );
  assert.match(
    bridge,
    /recordContactsConsent\([\s\S]*?disclosureAcknowledged: disclosureAcknowledged/u,
  );
  assert.match(
    models,
    /guard disclosureAcknowledged,[\s\S]*?kind: \.contactsDisclosure,[\s\S]*?decision: \.granted/u,
  );
  assert.match(models, /if isCurrent\([\s\S]*?return true/u);
});

test('privacy inventory and disconnect/revoke decisions use the durable ledger', () => {
  assert.match(
    workflow,
    /"consentVersions": IOSCompanionConsentLedgerPolicy\.versions/u,
  );
  assert.match(
    store,
    /func clearContactDerivedState[\s\S]*?recordDisclosureRevoked/u,
  );
  assert.match(
    workflow,
    /disconnectGoogleProviderAfterLocalCleanup\(\)[\s\S]*?guard providerDisconnected[\s\S]*?markProviderRevoked/u,
  );
  assert.match(
    workflow,
    /private func finishProviderRevocationCleanup[\s\S]*?recordContactsScopeRevoked[\s\S]*?finishRevokedGoogleSDKCleanupAfterLocalCleanup/u,
  );
});
