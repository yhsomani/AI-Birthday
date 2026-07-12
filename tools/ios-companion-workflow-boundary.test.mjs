import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

const bridge = read('ios/BirthdayAutopilot/BirthdayNativeModule.swift');
const composer = read('ios/BirthdayAutopilot/CompanionMessageModule.swift');
const protectedStore = read(
  'ios/BirthdayAutopilot/CompanionProtectedStore.swift',
);
const reminder = read('ios/BirthdayAutopilot/CompanionReminderModule.swift');
const status = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionStatusClient.swift',
);
const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
const podfile = read('ios/Podfile');
const identity = read(
  'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
);

test('iOS companion coordination is strict, replay-protected and fail closed', () => {
  assert.match(status, /Functions\.functions\(region: Self\.region\)/u);
  assert.match(status, /private static let region = "asia-south1"/u);
  assert.match(
    status,
    /HTTPSCallableOptions\(requireLimitedUseAppCheckTokens: true\)/u,
  );
  assert.match(status, /callable\.timeoutInterval = 10/u);
  assert.match(status, /"contractVersion": 1/u);
  assert.match(status, /"ledgerGeneration": Self\.expectedLedgerGeneration/u);
  assert.match(
    status,
    /private static let expectedLedgerGeneration = "birthday-ledger-v1"/u,
  );
  assert.match(status, /case "MANAGED_BY_ANDROID"/u);
  assert.match(status, /case "DELETING"/u);
  assert.match(status, /case "SAFETY_STATUS_UNAVAILABLE"/u);
  assert.match(status, /androidCoexistence: \.unknown/u);
  assert.match(status, /trustedServerTime: nil/u);
  assert.doesNotMatch(status, /trustedServerTime: Date\(\)/u);
});

test('iOS composer is foreground-only, nonce-fenced and never programmatically sends', () => {
  assert.match(composer, /MFMessageComposeViewController/u);
  assert.match(
    composer,
    /UIApplication\.shared\.applicationState == \.active/u,
  );
  assert.match(protectedStore, /reviewNonceDigest/u);
  assert.match(composer, /actionNonce/u);
  assert.match(composer, /controller\.recipients = \[commit\.recipient\]/u);
  assert.match(composer, /controller\.body = commit\.body/u);
  assert.match(composer, /case \.sent:[\s\S]*?reportedSent/u);
  assert.doesNotMatch(composer, /CTMessageCenter|sendSMS|sendTextMessage/u);
  const openKeys =
    composer.match(/private static let openRequestKeys[\s\S]*?\n\s*\]/u)?.[0] ??
    '';
  assert.match(openKeys, /"actionNonce"/u);
  assert.match(openKeys, /"expectedRevision"/u);
  assert.match(openKeys, /"proposalId"/u);
  assert.doesNotMatch(openKeys, /recipient|body/u);
});

test('protected workflow uses account binding, CAS, expiring reviews and rollback-on-rejection', () => {
  assert.match(protectedStore, /workflow\.account\.matches\(binding\)/u);
  assert.match(protectedStore, /expectedConfigurationGeneration/u);
  assert.match(
    protectedStore,
    /private static let reviewLifetime: TimeInterval = 45/u,
  );
  assert.match(protectedStore, /now <= expiresAt/u);
  assert.match(protectedStore, /persistMutationOnFailure: Bool = false/u);
  assert.match(
    protectedStore,
    /guard persistMutationOnFailure else \{ return \.failure\(error\) \}/u,
  );
  assert.ok(
    (protectedStore.match(/persistMutationOnFailure: true/gu) ?? []).length >=
      2,
  );
  assert.match(protectedStore, /outcome\.preventsRepeat/u);
  assert.match(protectedStore, /wipeAndInstallResetSafety/u);
});

test('all live iOS feature areas are wired and privacy cancels reminders before destructive wipes', () => {
  for (const area of [
    'account',
    'activity',
    'automation',
    'bootstrap',
    'contacts',
    'home',
    'messages',
    'privacy',
    'readiness',
    'setup',
  ]) {
    assert.match(bridge, new RegExp(`"${area}"`, 'u'));
  }
  for (const kind of [
    'reminder-scheduled',
    'composer-opened',
    'composer-cancelled',
    'composer-failed',
    'composer-outcome-unknown',
    'composer-reported-sent',
  ]) {
    assert.match(workflow, new RegExp(`"${kind}"`, 'u'));
  }
  assert.match(
    workflow,
    /case "sign-out-wipe":[\s\S]*?reminderCoordinator\.wipeCompanionData/u,
  );
  assert.match(
    reminder,
    /func wipeCompanionData[\s\S]*?cancelAppOwnedNotifications[\s\S]*?wipeAndInstallResetSafety[\s\S]*?cancelAppOwnedNotifications/u,
  );
  assert.match(
    reminder,
    /guard settings\.authorizationStatus != \.denied[\s\S]*?REMINDER_SETTINGS_REQUIRED/u,
  );
  assert.match(
    reminder,
    /func openNotificationSettings[\s\S]*?authorizationStatus == \.denied[\s\S]*?UIApplication\.openSettingsURLString/u,
  );
  assert.match(
    workflow,
    /case "wipe-local-data":[\s\S]*?wipeCompanionData[\s\S]*?wipeLocalDataAfterSafetyShutdown/u,
  );
  assert.match(
    identity,
    /func wipeLocalDataAfterSafetyShutdown[\s\S]*?peopleStore\.wipe[\s\S]*?peopleStore\.attach[\s\S]*?bindWorkflowAccount/u,
  );
  assert.match(
    bridge,
    /case \.fresh:[\s\S]*?configuration lives[\s\S]*?return "complete"/u,
  );
  assert.match(
    bridge,
    /case \.syncing\(_, let retainedGeneration\):[\s\S]*?retainedGeneration \? "complete"/u,
  );
  assert.match(
    bridge,
    /case \.failedRetained\(let lastSuccess, _\):[\s\S]*?lastSuccess == nil \? "sync-summary" : "complete"/u,
  );
  assert.doesNotMatch(
    bridge.match(
      /private func setupStep[\s\S]*?private func contactsSyncPayload/u,
    )?.[0] ?? '',
    /recipient-selection|message-and-policy|messageComposerIsAvailable/u,
  );
  assert.match(
    bridge,
    /case "policy-editor":[\s\S]*?request\.keys\.count == 1[\s\S]*?policyEditorProjection/u,
  );
  assert.match(
    workflow,
    /func policyEditorProjection[\s\S]*?"kind": "not-configured"[\s\S]*?"kind": "configured"[\s\S]*?"latePolicy"/u,
  );
  assert.match(
    workflow,
    /private static let planningDays = 400[\s\S]*?for date in self\.occurrenceDates/u,
  );
  assert.match(
    workflow,
    /let now = Date\(\)[\s\S]*?calendar\.date\(byAdding: \.day, value: 6, to: now\) \?\? now/u,
  );
  assert.match(
    workflow,
    /private func occurrenceDates[\s\S]*?value: Self\.planningDays[\s\S]*?values\.append\(value\)/u,
  );
});

test('Gemini provenance is revalidated at save and consumed only after protected commit', () => {
  const save =
    workflow.match(
      /private func saveMessage\([\s\S]*?\n\s*private func previewPolicy/u,
    )?.[0] ?? '';
  assert.match(save, /readWorkflowSnapshot/u);
  assert.match(save, /revalidatedDraftForSave/u);
  assert.match(save, /workflow\.messageDraft = draftForCommit/u);
  assert.match(
    save,
    /case \.success\(let value\)[\s\S]*?provenance\?\.source == "GEMINI"[\s\S]*?consumeProvenance/u,
  );
  const validation =
    workflow.match(
      /private static func revalidatedDraftForSave[\s\S]*?\n\s*private static func windowLabel/u,
    )?.[0] ?? '';
  assert.match(validation, /peekProvenance/u);
  assert.match(validation, /source: "USER"/u);
  assert.match(validation, /modelIdentifier: nil/u);
  assert.match(validation, /promptPolicyVersion: nil/u);
});

test('native JSON integer parsing rejects booleans, fractions and non-finite values', () => {
  assert.match(workflow, /CFGetTypeID\(number\) != CFBooleanGetTypeID\(\)/u);
  assert.match(workflow, /number\.doubleValue\.isFinite/u);
  assert.match(
    workflow,
    /number\.doubleValue\.rounded\(\) == number\.doubleValue/u,
  );
  for (const field of [
    'requestedSegmentCap',
    'requiredCount',
    'dailyCap',
    'pageSize',
  ]) {
    assert.match(
      workflow,
      new RegExp(`strictInteger\\([^\\n]*\\["${field}"\\]`, 'u'),
    );
  }
  const directIntValues = workflow.match(/\.intValue/gu) ?? [];
  assert.equal(directIntValues.length, 1);
  const policyParser =
    workflow.match(
      /private static func parsePolicy[\s\S]*?\n\s*private func simulate/u,
    )?.[0] ?? '';
  assert.match(
    policyParser,
    /\(30\.\.\.240\)\.contains\(endMinutes - startMinutes\)/u,
  );
  assert.match(policyParser, /graceMinutes > endMinutes/u);
  assert.match(policyParser, /graceMinutes - startMinutes <= 240/u);
});

test('Xcode and CocoaPods include every native companion workflow dependency', () => {
  for (const file of [
    'IOSCompanionWorkflowModels.swift',
    'IOSCompanionWorkflowEngine.swift',
    'IOSCompanionStatusClient.swift',
    'GeminiCandidateProvenanceRegistry.swift',
    'GeminiSuggestionPolicy.swift',
    'IOSGeminiSuggestionGateway.swift',
  ]) {
    assert.match(project, new RegExp(file.replaceAll('.', '\\.'), 'u'));
  }
  for (const pod of [
    'FirebaseAuth',
    'FirebaseAppCheck',
    'FirebaseFunctions',
    'FirebaseAILogic',
    'GoogleSignIn',
  ]) {
    assert.match(podfile, new RegExp(`pod '${pod}'`, 'u'));
  }
});

test('iOS diagnostics are content-free, revision-fenced and use the system share sheet', () => {
  assert.match(
    bridge,
    /if intent == "preview-diagnostics"[\s\S]*?diagnosticsPreview/u,
  );
  assert.match(
    bridge,
    /if intent == "share-diagnostics"[\s\S]*?executeDiagnosticsShare/u,
  );
  assert.match(
    bridge,
    /guard status\.revision == expectedRevision[\s\S]*?UIActivityViewController/u,
  );
  assert.match(bridge, /"excludesPrivateContent": true/u);
  assert.match(bridge, /"Private content excluded: yes"/u);
  const diagnostics =
    bridge.match(
      /private func diagnosticsPreview[\s\S]*?private func finishAsyncIntent/u,
    )?.[0] ?? '';
  for (const forbidden of [
    'privateContacts',
    'recipient',
    'messageDraft',
    'displayEmail',
    'firebaseUID',
    'googleSubject',
  ]) {
    assert.doesNotMatch(diagnostics, new RegExp(forbidden, 'u'));
  }
});
