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
const peopleStore = read(
  'ios/BirthdayAutopilot/Database/CompanionPeopleStore.swift',
);
const peopleContracts = read(
  'ios/BirthdayAutopilot/Contacts/PeopleContracts.swift',
);
const presentationFormatter = read(
  'ios/BirthdayAutopilot/Contacts/IOSNativePresentationFormatter.swift',
);
const reminder = read('ios/BirthdayAutopilot/CompanionReminderModule.swift');
const attentionNotifier = read(
  'ios/BirthdayAutopilot/Notifications/IOSCompanionAttentionNotifier.swift',
);
const notificationRouter = read(
  'ios/BirthdayAutopilot/Notifications/IOSCompanionNotificationRouter.swift',
);
const status = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionStatusClient.swift',
);
const workflow = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowEngine.swift',
);
const workflowModels = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionWorkflowModels.swift',
);
const placeholderPolicy = read(
  'ios/BirthdayAutopilot/Automation/IOSCompanionMessagePlaceholderPolicy.swift',
);
const nativeTests = read(
  'ios/BirthdayAutopilotTests/BirthdayAutopilotNativeTests.swift',
);
const featureSchemas = read('src/infrastructure/native/featureSchemas.ts');
const project = read('ios/BirthdayAutopilot.xcodeproj/project.pbxproj');
const reminderBridge = read(
  'ios/BirthdayAutopilot/CompanionReminderModuleBridge.m',
);
const companionGateway = read(
  'src/infrastructure/native/ios/CompanionNativeGateway.ts',
);
const composerReviewUi = read('src/features/live/LiveComposerReviewScreen.tsx');
const automationUi = read('src/features/live/LiveAutomationScreen.tsx');
const podfile = read('ios/Podfile');
const identity = read(
  'ios/BirthdayAutopilot/Identity/IOSGoogleIdentityCoordinator.swift',
);
const retentionPolicy = read(
  'ios/BirthdayAutopilot/Privacy/IOSCompanionRetentionPolicy.swift',
);
const localizationProvider = read('src/localization/LocalizationProvider.tsx');
const productionI18n = read('src/localization/i18n.ts');
const fixtureSettings = read('src/features/settings/SettingsScreen.tsx');
const liveSettings = read('src/features/live/LiveSettingsScreen.tsx');

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

test('app-owned Composer Review preserves native call order and opaque payload boundaries', () => {
  const prepareCall =
    composerReviewUi.match(
      /companionPort\.prepareComposerReview\(\{[\s\S]*?\}\)/u,
    )?.[0] ?? '';
  const openCall =
    composerReviewUi.match(
      /companionPort\.openUserConfirmedComposer\(\{[\s\S]*?\}\)/u,
    )?.[0] ?? '';

  assert.match(prepareCall, /expectedRevision: envelope\.revision/u);
  assert.match(prepareCall, /proposalId: proposalValue\.proposalId/u);
  assert.doesNotMatch(
    prepareCall,
    /actionNonce|body|maskedDestination|occurrence|recipient|routeId/u,
  );
  assert.match(openCall, /actionNonce: review\.actionNonce/u);
  assert.match(openCall, /expectedRevision: review\.revision/u);
  assert.match(openCall, /proposalId: review\.proposalId/u);
  assert.doesNotMatch(
    openCall,
    /body|maskedDestination|occurrence|recipient|routeId/u,
  );

  const prepare = composerReviewUi.indexOf(
    'companionPort.prepareComposerReview',
  );
  const validatePreparedReview = composerReviewUi.indexOf(
    'result.value.expiresAtEpochMilliseconds > Date.now()',
    prepare,
  );
  const exposePreparedReview = composerReviewUi.indexOf(
    'setReview(result.value)',
    validatePreparedReview,
  );
  const expiryRecheck = composerReviewUi.indexOf(
    'review.expiresAtEpochMilliseconds <= Date.now()',
    exposePreparedReview,
  );
  const availabilityCheck = composerReviewUi.indexOf(
    'companionPort.canOpenComposer()',
    expiryRecheck,
  );
  const open = composerReviewUi.indexOf(
    'companionPort.openUserConfirmedComposer',
    availabilityCheck,
  );
  const reloadProposal = composerReviewUi.indexOf(
    'await proposal.reload()',
    open,
  );

  assert.ok(prepare > 0);
  assert.ok(validatePreparedReview > prepare);
  assert.ok(exposePreparedReview > validatePreparedReview);
  assert.ok(expiryRecheck > exposePreparedReview);
  assert.ok(availabilityCheck > expiryRecheck);
  assert.ok(open > availabilityCheck);
  assert.ok(reloadProposal > open);
  assert.doesNotMatch(
    automationUi,
    /getNextComposerProposal|prepareComposerReview|canOpenComposer|openUserConfirmedComposer|live-open-composer/u,
  );
});

test('iOS composer binds and leases one exact durable People generation', () => {
  assert.match(
    peopleStore,
    /struct IOSPeoplePrivateSnapshot[\s\S]*?let generation: String/u,
  );
  assert.match(
    peopleStore,
    /func privateSnapshot[\s\S]*?snapshot\.sync\.generation = generation[\s\S]*?try persist\(snapshot\)/u,
  );
  const lease =
    peopleStore.match(
      /func acquireComposerMaterialLease[\s\S]*?func releaseComposerMaterialLease/u,
    )?.[0] ?? '';
  assert.match(lease, /snapshot\.binding == expectedBinding/u);
  assert.match(
    lease,
    /snapshot\.sync\.generation == expectedSnapshotGeneration/u,
  );
  assert.match(lease, /contact\.materialRevision == expectedMaterialRevision/u);
  assert.match(
    lease,
    /\$0\.localId == selectedPhoneId[\s\S]*?\.e164 == expectedRecipient/u,
  );
  assert.match(lease, /DispatchTime\.now\(\)\.uptimeNanoseconds/u);
  const leaseRevalidation =
    peopleStore.match(
      /func validateAndRetainComposerMaterialLease[\s\S]*?func releaseComposerMaterialLease/u,
    )?.[0] ?? '';
  assert.match(
    leaseRevalidation,
    /expectedSnapshotGeneration == lease\.snapshotGeneration/u,
  );
  assert.match(
    leaseRevalidation,
    /activeLease\.token == lease\.token[\s\S]*?activeLease\.snapshotGeneration == lease\.snapshotGeneration/u,
  );
  assert.match(
    leaseRevalidation,
    /self\.removeExpiredComposerMaterialLease\(\)/u,
  );
  assert.match(leaseRevalidation, /snapshot\.binding == expectedBinding/u);
  assert.match(
    leaseRevalidation,
    /snapshot\.sync\.generation == expectedSnapshotGeneration/u,
  );
  assert.match(
    leaseRevalidation,
    /contact\.materialRevision == expectedMaterialRevision/u,
  );
  assert.match(
    leaseRevalidation,
    /\$0\.localId == selectedPhoneId[\s\S]*?\.e164 == expectedRecipient/u,
  );
  assert.match(
    leaseRevalidation,
    /expiresAtUptimeNanoseconds: activeLease\.expiresAtUptimeNanoseconds[\s\S]*?retainedUntilRelease: true/u,
  );
  assert.match(
    peopleStore,
    /private func removeExpiredComposerMaterialLease[\s\S]*?!lease\.retainedUntilRelease[\s\S]*?uptimeNanoseconds >= lease\.expiresAtUptimeNanoseconds/u,
  );
  assert.ok(
    (peopleStore.match(/guard self\.peopleMaterialMutationAllowed\(\)/gu) ?? [])
      .length >= 7,
  );

  assert.match(workflowModels, /let peopleSnapshotGeneration: String/u);
  assert.match(workflow, /peopleStore\.privateSnapshot\(\)/u);
  assert.match(
    workflow,
    /peopleSnapshotGeneration: peopleSnapshot\.generation/u,
  );
  assert.match(protectedStore, /let peopleSnapshotGeneration: String\?/u);
  assert.match(
    protectedStore,
    /func commitComposerOpen[\s\S]*?expectedPeopleSnapshotGeneration: String[\s\S]*?String\(snapshot\.projectionRevision \?\? 0\) == expectedRevision[\s\S]*?proposal\.peopleSnapshotGeneration == expectedPeopleSnapshotGeneration/u,
  );
  assert.match(
    protectedStore,
    /recoveredLegacyPeopleGenerationProposal[\s\S]*?snapshot\.proposals\.removeAll\(\)/u,
  );
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

test('workflow CAS is crash-reload safe and lifecycle reconciliation removes stale reminders', () => {
  const mutation =
    protectedStore.match(
      /func mutateWorkflow<Value>[\s\S]*?func completeClearActivity/u,
    )?.[0] ?? '';
  assert.match(
    mutation,
    /workflow\.contacts\.sort[\s\S]*?Self\.validateWorkflow\(workflow\)[\s\S]*?snapshot\.workflow = workflow[\s\S]*?try Self\.invalidateStalePlanArtifacts/u,
  );

  const invalidation =
    protectedStore.match(
      /private static func invalidateStalePlanArtifacts[\s\S]*?private static func pruneWorkflowMetadata/u,
    )?.[0] ?? '';
  assert.match(
    invalidation,
    /index\.configurationGeneration == workflow\.configurationGeneration/u,
  );
  assert.match(
    invalidation,
    /index\.contactTableDigest == contactTableDigest/u,
  );
  assert.match(
    invalidation,
    /index\.contactCount == workflow\.contacts\.count/u,
  );
  assert.match(
    invalidation,
    /validateStoredProposal\(\$0, snapshot: snapshot\)/u,
  );
  assert.match(invalidation, /snapshot\.proposals\.removeAll\(\)/u);
  assert.match(invalidation, /snapshot\.planningIndex = nil/u);
  assert.match(invalidation, /applyReminderPlans\(\[\], to: &snapshot\)/u);
  assert.match(invalidation, /snapshot\.reminderHorizon = nil/u);
  assert.match(invalidation, /snapshot\.pendingNativeRoute = nil/u);
  assert.doesNotMatch(invalidation, /composerRecords\.(?:removeAll|remove)/u);
  assert.doesNotMatch(invalidation, /terminalLedger\s*=/u);

  // A launch after the atomic write either rebuilds a current plan or
  // reconciles the now-empty desired set. Reconciliation discovers owned OS
  // requests by prefix, so clearing protected request IDs cannot orphan them.
  assert.match(
    reminder,
    /private func replenishPlanBeforeReconciliation[\s\S]*?reconcileReminderPlanForLifecycle/u,
  );
  assert.match(
    reminder,
    /removeStaleOwnedRequestsBeforeAdding[\s\S]*?ownedIdentifiers\.subtracting\(desiredIdentifiers\)[\s\S]*?removePending\(withIdentifiers:/u,
  );
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
    /guard authorizationStatus != \.denied[\s\S]*?REMINDER_SETTINGS_REQUIRED/u,
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
    /enum IOSCompanionRecurrencePlanner[\s\S]*?static let planningDays = 400/u,
  );
  assert.match(workflow, /for date in self\.occurrenceDates/u);
  assert.match(
    workflow,
    /for offset in 0\.\.\.6[\s\S]*?calendar\.date\(byAdding: \.day, value: offset, to: now\)[\s\S]*?plannedOccurrenceCount/u,
  );
  assert.match(
    workflow,
    /private func occurrenceDates[\s\S]*?IOSCompanionRecurrencePlanner\.occurrenceDates/u,
  );
  assert.match(workflow, /value: planningDays - 1/u);
  assert.match(workflow, /Calendar\(identifier: \.gregorian\)/u);
});

test('iOS indexes every eligible occurrence and coalesces reminders by date', () => {
  const rebuild =
    workflow.match(
      /private func rebuildPlan[\s\S]*?\n\s*private func finishMutation/u,
    )?.[0] ?? '';
  const preview =
    workflow.match(
      /private func previewPolicy[\s\S]*?\n\s*private func savePolicy/u,
    )?.[0] ?? '';
  const simulation =
    workflow.match(
      /private func simulate[\s\S]*?\n\s*private func nextOccurrence/u,
    )?.[0] ?? '';
  const previewSchema =
    featureSchemas.match(
      /export const policyPreviewSchema[\s\S]*?\n\]\);/u,
    )?.[0] ?? '';

  assert.match(
    rebuild,
    /for \(ordinal, configuration\) in workflow\.contacts\.enumerated\(\)[\s\S]*?ordinalsByDay\[offset\]\.append[\s\S]*?IOSCompanionPlanningIndex\([\s\S]*?for dayOffset in ordinalsByDay\.indices[\s\S]*?plans\.append/u,
  );
  assert.doesNotMatch(
    rebuild,
    /countByDate|rollingInstants|dailyCap|legacyAndroidDailyCap|rolling24|< 20/u,
  );
  assert.doesNotMatch(
    preview,
    /window-capacity-conflict|firstConflictDate|"field": "dailyCap"/u,
  );
  assert.match(
    workflow,
    /strictInteger\(raw\["dailyCap"\], range: 1\.\.\.1_000_000\)/u,
  );
  assert.doesNotMatch(
    workflow,
    /strictInteger\(raw\["dailyCap"\], range: 1\.\.\.20\)/u,
  );
  assert.doesNotMatch(
    simulation,
    /firstConflictDate|rollingConflict|dailyConflict|> 20|legacyAndroidDailyCap/u,
  );
  assert.match(workflowModels, /legacyAndroidDailyCap = "dailyCap"/u);
  assert.match(
    workflow,
    /private static func approvalMatches[\s\S]*?includeLegacyAndroidDailyCap: true[\s\S]*?return storedHash == legacy/u,
  );
  assert.doesNotMatch(workflow, /up to [^"\n]*\/day/u);
  assert.match(
    previewSchema,
    /maximumPlannedInLocalDay:[^\n]*max\(1_000_000\)/u,
  );
  assert.match(
    previewSchema,
    /maximumPlannedInRolling24Hours:[\s\S]*?max\(1_000_000\)/u,
  );

  assert.match(
    workflow,
    /private static let planningDays = IOSCompanionRecurrencePlanner\.planningDays/u,
  );
  assert.match(reminder, /private static let maximumScheduledDateCount = 60/u);
  assert.match(
    protectedStore,
    /maximumReminderPlans = IOSCompanionPlanningIndex\.planningDayCount/u,
  );
  assert.match(
    reminder,
    /var earliestByCivilDate:[\s\S]*?for plan in schedule\.plans[\s\S]*?earliestByCivilDate\[plan\.civilDate\]/u,
  );
  assert.match(
    reminder,
    /Array\(candidates\.prefix\(Self\.maximumScheduledDateCount\)\)/u,
  );
});

test('iOS recurrence has an executable twenty-year and exact 400-day acceptance matrix', () => {
  assert.match(
    workflow,
    /enum IOSCompanionRecurrencePlanner[\s\S]*?value: planningDays - 1/u,
  );
  assert.match(workflow, /private var calendar: Calendar[\s\S]*?\.gregorian/u);
  assert.match(
    reminder,
    /private static func schedulingCalendar[\s\S]*?\.gregorian/u,
  );
  assert.match(
    protectedStore,
    /calendar: Calendar = \{[\s\S]*?Calendar\(identifier: \.gregorian\)[\s\S]*?timeZone = \.autoupdatingCurrent/u,
  );
  assert.doesNotMatch(reminder, /Calendar\.autoupdatingCurrent/u);
  for (const testName of [
    'testCompanionRecurrencePlannerCoversTwentyYearsAndEveryLeapPolicy',
    'testCompanionRecurrencePlannerUsesExactlyFourHundredCivilDates',
    'testCompanionRecurrencePlannerIsGregorianAcrossDSTZonesAndCalendarPreferences',
    'testCompanionRecurrencePlannerRecalculatesUTCPlusFourteenToUTCMinusTwelveTravel',
  ]) {
    assert.match(nativeTests, new RegExp(`func ${testName}`, 'u'));
  }
  assert.match(nativeTests, /for year in 2024\.\.\.2043/u);
  assert.match(nativeTests, /"feb-28"[\s\S]*?"mar-01"[\s\S]*?"skip"/u);
  assert.match(project, /BirthdayAutopilotNativeTests\.swift in Sources/u);
});

test('iOS reset safety preserves eight dates and makes the ninth fail closed', () => {
  assert.match(protectedStore, /private static let maximumResetDates = 8/u);
  const observation =
    protectedStore.match(
      /private func observeResetDate[\s\S]*?private func resolveDanglingComposerOperations/u,
    )?.[0] ?? '';
  assert.match(
    observation,
    /blockedCivilDates\.count < Self\.maximumResetDates[\s\S]*?blockedCivilDates\.append[\s\S]*?else \{[\s\S]*?overflowed = true/u,
  );
  assert.doesNotMatch(observation, /removeFirst|removeLast|prefix\(/u);
  const verifiedDate =
    protectedStore.match(
      /func establishVerifiedResetSafetyDate[\s\S]*?func prepareComposerReview/u,
    )?.[0] ?? '';
  assert.match(
    verifiedDate,
    /blockedCivilDates\.count < Self\.maximumResetDates[\s\S]*?overflowed = true[\s\S]*?resetFenceOverflow/u,
  );
});

test('iOS disclosures leave sender-line availability and transport to Messages and iOS', () => {
  assert.match(
    workflow,
    /Messages and iOS control the available sender line and final transport/u,
  );
  assert.doesNotMatch(
    workflow,
    /choose (?:a |the )?sender line|control (?:the )?sender line/u,
  );
  assert.match(
    composer,
    /does not know the final[\s\S]*?sender line, transport, carrier acceptance, or delivery/u,
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

test('iOS uses one strict placeholder policy and narrowly repairs unsafe persisted drafts', () => {
  assert.match(
    placeholderPolicy,
    /static let givenNamePlaceholder = "\{firstName\}"/u,
  );
  assert.match(
    placeholderPolicy,
    /case "generic":[\s\S]*?placeholderCount == 0/u,
  );
  assert.match(
    placeholderPolicy,
    /case "given-name":[\s\S]*?placeholderCount == 1/u,
  );
  assert.match(
    placeholderPolicy,
    /!withoutSupportedPlaceholder\.contains\("\{"\)[\s\S]*?!withoutSupportedPlaceholder\.contains\("\}"\)/u,
  );
  assert.match(workflow, /IOSCompanionMessagePlaceholderPolicy\.issue/u);
  assert.match(workflow, /IOSCompanionMessagePlaceholderPolicy\.isValid/u);
  assert.match(workflow, /IOSCompanionMessagePlaceholderPolicy\.render/u);
  assert.match(workflow, /IOSBirthdayMessageContentPolicy\.renderedBody/u);
  assert.match(
    composer,
    /IOSBirthdayMessageContentPolicy\.isSafeRenderedBody/u,
  );
  assert.match(protectedStore, /IOSCompanionPersistedDraftRecovery\.apply/u);
  assert.match(
    protectedStore,
    /case \.revalidatedDraft, \.clearedInvalidDraft:[\s\S]*?snapshot\.proposals\.removeAll\(\)[\s\S]*?snapshot\.planningIndex = nil[\s\S]*?Self\.applyReminderPlans\(\[\], to: &snapshot\)/u,
  );
  assert.match(workflowModels, /workflow\.desired = \.paused/u);
  assert.match(workflowModels, /reasons\.insert\("template-changed"\)/u);
  for (const testName of [
    'testCompanionMessagePlaceholderPolicyRejectsEveryUnsafeStructure',
    'testPersistedInvalidDraftRecoveryFailsClosedWithoutDeletingDurableUserData',
    'testPersistedValidGenericDraftNeedsNoGivenNameAndDoesNotRecover',
  ]) {
    assert.match(nativeTests, new RegExp(`func ${testName}`, 'u'));
  }
});

test('Xcode and CocoaPods include every native companion workflow dependency', () => {
  for (const file of [
    'IOSCompanionMessagePlaceholderPolicy.swift',
    'IOSCompanionWorkflowModels.swift',
    'IOSCompanionWorkflowEngine.swift',
    'IOSCompanionStatusClient.swift',
    'IOSCompanionPlanningIndex.swift',
    'IOSCompanionOccurrenceIdentity.swift',
    'IOSCompanionTerminalLedger.swift',
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
    'FirebaseRemoteConfig',
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

test('composer review refreshes People material before coexistence and nonce minting', () => {
  const peopleRefresh = composer.indexOf(
    'peopleSync.sync(interactiveAuthorization: false)',
  );
  const reconcile = composer.indexOf('workflow.reconcileAfterPeopleSync');
  const materialValidation = composer.indexOf(
    'refreshedMaterial == originalMaterial',
  );
  const coexistenceRefresh = composer.indexOf(
    'statusClient.refreshControlImmediatelyBeforeReview',
  );
  const nonceMint = composer.indexOf('store.prepareComposerReview');

  assert.ok(peopleRefresh > 0);
  assert.ok(reconcile > peopleRefresh);
  assert.ok(materialValidation > reconcile);
  assert.ok(coexistenceRefresh > materialValidation);
  assert.ok(nonceMint > coexistenceRefresh);
  assert.match(composer, /COMPOSER_CONTACTS_RECONNECT_REQUIRED/u);
  assert.match(composer, /COMPOSER_CONTACTS_FRESHNESS_UNAVAILABLE/u);
  assert.match(
    workflow,
    /configuration\.materialRevision == contact\.materialRevision/u,
  );
  assert.match(
    workflow,
    /IOSCompanionApprovalDestinationBinding\.resolve[\s\S]*?destinationCounts\[destination\] == 1/u,
  );
  assert.match(composer, /requireTrustedFreshness: true/u);
  assert.match(composer, /finalMaterial == expectedMaterial/u);
  assert.match(composer, /material: finalMaterial/u);
  assert.match(protectedStore, /snapshot\.terminalLedger\?\.check/u);
});

test('privacy shutdown drains stale notification adds and verifies final absence', () => {
  assert.match(reminder, /registerNotificationAdd\(generation: generation\)/u);
  assert.match(
    reminder,
    /!self\.isCurrentReconciliation\(generation\)[\s\S]*?removePending\(withIdentifiers:[\s\S]*?finishNotificationAdd/u,
  );
  assert.match(
    reminder,
    /waitForNotificationAddsToDrain[\s\S]*?removeAndVerifyAppOwnedNotifications/u,
  );
  assert.match(
    reminder,
    /pendingRequests[\s\S]*?deliveredRequests[\s\S]*?completion\(!remaining\)/u,
  );
  assert.match(reminder, /REMINDER_CANCELLATION_UNVERIFIED/u);
});

test('iOS companion attention notifications are generic, bounded, deduplicated and cancellable', () => {
  assert.match(
    attentionNotifier,
    /enum IOSCompanionAttentionKind[\s\S]*?case composer[\s\S]*?case contacts[\s\S]*?case coordination[\s\S]*?case reminders/u,
  );
  assert.match(attentionNotifier, /getNotificationSettings/u);
  assert.doesNotMatch(attentionNotifier, /requestAuthorization/u);
  assert.match(
    attentionNotifier,
    /getPendingNotificationRequests[\s\S]*?getDeliveredNotifications[\s\S]*?alreadyPresent/u,
  );
  assert.match(
    attentionNotifier,
    /kind\.rawValue \+ "\." \+ civilDate[\s\S]*?claimAttentionNotification/u,
  );
  assert.match(
    attentionNotifier,
    /stalePending[\s\S]*?staleDelivered[\s\S]*?removeDeliveredNotifications/u,
  );
  assert.match(attentionNotifier, /content\.userInfo = \[:\]/u);
  assert.match(
    attentionNotifier,
    /func beginCancellationDrain[\s\S]*?cancellationDepth \+= 1[\s\S]*?drainWaiters/u,
  );
  assert.match(
    attentionNotifier,
    /private func begin[\s\S]*?guard cancellationDepth == 0/u,
  );
  assert.doesNotMatch(
    attentionNotifier,
    /recipient|phoneNumber|messageBody|contactName|displayName/u,
  );
  assert.match(
    notificationRouter,
    /isAttentionIdentifier[\s\S]*?completionHandler\(\[\]\)/u,
  );
  assert.match(
    notificationRouter,
    /pendingAttentionRouteId = UUID\(\)\.uuidString\.lowercased\(\)[\s\S]*?companionNativeRouteAvailable/u,
  );
  assert.match(
    notificationRouter,
    /let attentionRouteId = pendingAttentionRouteId[\s\S]*?"kind": "attention"[\s\S]*?"source": "attention"/u,
  );
  assert.match(
    protectedStore,
    /func claimAttentionNotification[\s\S]*?mayAdvanceAttentionClaim[\s\S]*?attentionNotificationDays = claims/u,
  );
  assert.match(
    reminder,
    /cancelAppOwnedNotifications[\s\S]*?beginCancellationDrain[\s\S]*?removeAndVerifyAppOwnedNotifications[\s\S]*?endCancellationDrain/u,
  );
  assert.match(reminder, /if !successful[\s\S]*?notify\(\.reminders\)/u);
  assert.equal(
    (project.match(/IOSCompanionAttentionNotifier\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
});

test('retained sign-out proves protected review capability was invalidated', () => {
  assert.match(
    protectedStore,
    /func verifyAccountSessionInvalidated[\s\S]*?snapshot\.control == nil[\s\S]*?reviewNonceDigest == nil/u,
  );
  assert.match(
    identity,
    /func completeSignOutAfterSafetyShutdown[\s\S]*?peopleStore\.wipe[\s\S]*?invalidateCompanionAccountSession/u,
  );
  assert.match(
    identity,
    /private func invalidateCompanionAccountSession[\s\S]*?verifyAccountSessionInvalidated/u,
  );
  assert.match(
    workflow,
    /case "sign-out-retain"[\s\S]*?cancelPlansAndNotifications[\s\S]*?result\["kind"\] as\? String == "ok"[\s\S]*?completeSignOutAfterSafetyShutdown/u,
  );
  assert.match(
    identity,
    /let companionSessionInvalidated = await invalidateCompanionAccountSession\(\)[\s\S]*?guard firebaseSignOutSucceeded, sdkSessionsAbsent,[\s\S]*?peopleCleanupSucceeded, companionSessionInvalidated[\s\S]*?enterIdentitySafetyInterlock/u,
  );
});

test('iOS app-owned detail is pruned at 30 days while terminal safety stays trusted-time fenced', () => {
  assert.match(retentionPolicy, /30 \* 24 \* 60 \* 60/u);
  assert.match(
    retentionPolicy,
    /now\.timeIntervalSince\(recordedAt\) >= detailedRetention/u,
  );
  assert.match(
    retentionPolicy,
    /mayReleaseTerminalMarker[\s\S]*?abs\(now\.timeIntervalSince\(trustedServerTime\)\) <= trustedTimeFreshness[\s\S]*?trustedServerTime > releaseAfter/u,
  );
  assert.match(
    protectedStore,
    /workflow\.activity\.removeAll[\s\S]*?detailHasExpired/u,
  );
  assert.match(
    protectedStore,
    /workflow\.privacyOperations\.removeAll[\s\S]*?\["complete", "failed"\]/u,
  );
  assert.match(
    protectedStore,
    /expiredDetailProposalIDs[\s\S]*?snapshot\.proposals\.removeAll/u,
  );
  assert.match(
    protectedStore,
    /func completeClearActivity[\s\S]*?resolvedOperationIds[\s\S]*?snapshot\.proposals\.removeAll[\s\S]*?snapshot\.composerRecords\.removeAll/u,
  );
  assert.match(
    protectedStore,
    /ledger\.pruneReleased[\s\S]*?trustedServerTime: snapshot\.control\?\.trustedServerTime/u,
  );
  assert.match(
    protectedStore,
    /workflow\.activityClearedAt = snapshot\.composerRecords\.isEmpty \? nil : now/u,
  );
  assert.match(
    workflow,
    /case "clear-activity":[\s\S]*?store\.completeClearActivity/u,
  );
  assert.match(
    workflow,
    /activityCutoff\.map\(\{ record\.openedAt > \$0 \}\)[\s\S]*?activityCutoff\.map\(\{ terminalAt > \$0 \}\)/u,
  );
  assert.match(
    workflow,
    /visibleComposerActivityCount[\s\S]*?openedVisible[\s\S]*?terminalVisible/u,
  );
  assert.match(
    protectedStore,
    /attentionNotificationDays = \(snapshot\.attentionNotificationDays \?\? \[:\]\)[\s\S]*?attentionClaimHasExpired/u,
  );
  assert.match(
    protectedStore,
    /activityClearedAtBefore[\s\S]*?no retained composer record can project a pre-clear event[\s\S]*?activityClearedAt = nil/u,
  );
  assert.match(
    protectedStore,
    /case \.outcomeUnknown, \.reportedSent:[\s\S]*?mayReleaseTerminalMarker/u,
  );
  assert.match(
    protectedStore,
    /func loadSnapshotApplyingRetention[\s\S]*?bumpProjectionRevision[\s\S]*?persist/u,
  );
  assert.match(
    protectedStore,
    /func readProjectionStatus[\s\S]*?loadSnapshotApplyingRetention\(now: now\)/u,
  );
  assert.match(
    protectedStore,
    /func readWorkflowSnapshot[\s\S]*?loadSnapshotApplyingRetention\(now: Date\(\)\)/u,
  );
  assert.equal(
    (project.match(/IOSCompanionRetentionPolicy\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
  assert.equal(
    (
      project.match(/IOSCompanionRetentionPolicyTests\.swift in Sources/gu) ??
      []
    ).length,
    2,
  );
});

test('iOS setup records activation only after a full reconciled reminder horizon', () => {
  assert.match(
    workflowModels,
    /var hasEverActivatedReminders: Bool\?[\s\S]*?hasEverActivatedReminders: false/u,
  );
  assert.match(
    protectedStore,
    /func markReminderActivationCompleted[\s\S]*?workflow\.desired == \.remindersOn[\s\S]*?reminderHorizon\?\.state == \.full[\s\S]*?observedRequestIds\.isEmpty[\s\S]*?workflow\.hasEverActivatedReminders = true/u,
  );
  const activation =
    workflow.match(
      /private func confirmActivation[\s\S]*?\n\s*private func pauseAll/u,
    )?.[0] ?? '';
  assert.match(activation, /workflow\.desired = \.remindersOn/u);
  assert.doesNotMatch(activation, /hasEverActivatedReminders = true/u);
  assert.match(
    workflow,
    /private func finishMutation[\s\S]*?rebuildPlan[\s\S]*?case \.failed\(let problem\)[\s\S]*?completion\(\.failure\(problem\)\)/u,
  );
  assert.match(
    bridge,
    /private func setup[\s\S]*?"initialActivationCompleted"[\s\S]*?hasEverActivatedReminders == true/u,
  );
  assert.doesNotMatch(
    bridge,
    /hasEverActivatedReminders == true \|\| \$0\.desired == \.remindersOn/u,
  );
  assert.match(
    reminder,
    /replenishPlanBeforeReconciliation[\s\S]*?exactSessionBinding[\s\S]*?reconcileReminderPlanForLifecycle/u,
  );
});

test('iOS whole-snapshot People capacity is bounded to the measured 10k release gate', () => {
  const performanceBudgets = JSON.parse(read('tools/performance-budgets.json'));
  assert.match(peopleContracts, /static let maximumPeople = 10_000/u);
  assert.match(
    peopleContracts,
    /static let maximumTotalResponseBytes = 16 \* 1_024 \* 1_024/u,
  );
  assert.match(
    peopleStore,
    /maximumContacts = IOSPeopleCapacityPolicy\.maximumPeople/u,
  );
  assert.equal(
    performanceBudgets.shared.normalizeCommit10000MaximumPeakRssMiB,
    250,
  );
  assert.equal(
    performanceBudgets.shared.normalizeCommit10000MaximumWallMs,
    5000,
  );
  assert.doesNotMatch(peopleContracts, /maximumPeople: Int = 100_000/u);
});

test('iOS user-authored templates enforce Android-equivalent content and language policy', () => {
  for (const code of [
    'template-tracking-not-allowed',
    'template-promotional-content',
    'template-sensitive-content',
    'template-language-mismatch',
  ]) {
    assert.match(workflow, new RegExp(`issues\\.append\\("${code}"\\)`, 'u'));
  }
  assert.ok(workflow.includes('^\\\\p{Devanagari}+$'));
  assert.ok(workflow.includes('^\\\\p{Latin}+$'));
  assert.match(
    workflow,
    /private func rebuildPlan[\s\S]*?IOSBirthdayMessageContentPolicy\.issueCodes[\s\S]*?\.isEmpty/u,
  );
  assert.match(
    workflow,
    /private func saveMessage[\s\S]*?currentContentIssues = IOSBirthdayMessageContentPolicy\.issueCodes[\s\S]*?guard currentContentIssues\.isEmpty/u,
  );
  assert.match(
    nativeTests,
    /testBirthdayMessageContentPolicyMatchesAndroidSafetyCategories/u,
  );
});

test('native protected-date labels follow the current English or Hindi locale', () => {
  assert.doesNotMatch(
    workflow,
    /"Birthday selected"|"Selected birthday"|"Next:|"Reminder window"|grace until/u,
  );
  assert.doesNotMatch(peopleStore, /Birthday option/u);
  assert.match(
    presentationFormatter,
    /locale: Locale = \.autoupdatingCurrent/u,
  );
  assert.match(presentationFormatter, /"रिमाइंडर समय"/u);
  assert.match(presentationFormatter, /"अतिरिक्त समय"/u);
  assert.match(presentationFormatter, /setLocalizedDateFormatFromTemplate/u);
  assert.match(
    workflow,
    /IOSNativePresentationFormatter\.nextOccurrenceLabel/u,
  );
  assert.match(
    peopleStore,
    /IOSNativePresentationFormatter\.selectedBirthdayLabel/u,
  );
  assert.equal(
    (project.match(/IOSNativePresentationFormatter\.swift in Sources/gu) ?? [])
      .length,
    2,
  );
});

test('production and native presentation languages both follow device settings', () => {
  assert.match(productionI18n, /getLocales\(\)\[0\]\?\.languageCode/u);
  assert.doesNotMatch(localizationProvider, /setLanguage|changeLanguage/u);
  assert.doesNotMatch(
    fixtureSettings,
    /setLanguage|language-(?:en|hi|pseudo)/u,
  );
  assert.doesNotMatch(liveSettings, /setLanguage|changeLanguage/u);
  assert.match(
    presentationFormatter,
    /locale: Locale = \.autoupdatingCurrent/u,
  );
});

test('destructive reminder maintenance is native-only and absent from React Native', () => {
  for (const method of [
    'replacePlans',
    'cancelAppOwned',
    'wipeCompanionData',
  ]) {
    assert.doesNotMatch(
      reminderBridge,
      new RegExp(`RCT_EXTERN_METHOD\\(${method}`, 'u'),
    );
    assert.doesNotMatch(companionGateway, new RegExp(`\\b${method}\\b`, 'u'));
  }
  assert.doesNotMatch(
    companionGateway,
    /replaceReminderPlans|cancelReminders/u,
  );
  assert.doesNotMatch(reminder, /func replacePlans\(|validatePlans\(/u);
  assert.match(reminder, /func cancelAppOwnedNotifications\(/u);
  assert.match(reminder, /func wipeCompanionData\(/u);
});

test('reminder reconciliation removes an old full horizon before adding a new full horizon', () => {
  const reconciliation = reminder.slice(
    reminder.indexOf('private func runReconciliation('),
    reminder.indexOf('private func removeAllPendingAppOwnedRequests('),
  );
  const staleRemoval = reconciliation.indexOf(
    'removeStaleOwnedRequestsBeforeAdding(',
  );
  const firstAdd = reconciliation.indexOf('self.center.add(request)');
  const authoritative = reconciliation.lastIndexOf(
    'pendingRequests { finalObserved',
  );
  const horizonCommit = reconciliation.indexOf(
    'recordReminderHorizon(horizon)',
    authoritative,
  );
  assert.ok(staleRemoval >= 0 && staleRemoval < firstAdd);
  assert.ok(firstAdd < authoritative && authoritative < horizonCommit);
  assert.match(
    reconciliation,
    /if !self\.isCurrentReconciliation\(generation\)[\s\S]*?removePending\(withIdentifiers:[\s\S]*?removeDelivered\(withIdentifiers:/u,
  );

  const staleHelper = reminder.slice(
    reminder.indexOf('private func removeStaleOwnedRequestsBeforeAdding('),
    reminder.indexOf('private func removeAllPendingAppOwnedRequests('),
  );
  assert.ok(
    staleHelper.indexOf('pendingRequests') <
      staleHelper.indexOf('removePending(withIdentifiers:'),
  );
  assert.match(staleHelper, /isCurrentReconciliation\(generation\)/u);
  assert.match(staleHelper, /attemptsRemaining: Int = 3/u);

  const capacity = 60;
  const pending = new Set(
    Array.from({ length: capacity }, (_, index) => `old-${index}`),
  );
  const desired = new Set(
    Array.from({ length: capacity }, (_, index) => `new-${index}`),
  );
  for (const identifier of [...pending]) {
    if (!desired.has(identifier)) pending.delete(identifier);
  }
  let failures = 0;
  for (const identifier of desired) {
    if (!pending.has(identifier) && pending.size >= capacity) {
      failures += 1;
    } else {
      pending.add(identifier);
    }
  }
  assert.equal(failures, 0);
  assert.deepEqual(pending, desired);

  // A late completion owned by a superseded generation is removed instead of
  // consuming quota or entering the authoritative next-generation horizon.
  pending.add('superseded-late-add');
  pending.delete('superseded-late-add');
  assert.deepEqual(pending, desired);
});

test('iOS activation review binds and revalidates the displayed runtime snapshot', () => {
  const prepare = workflow.slice(
    workflow.indexOf('private func prepareActivation('),
    workflow.indexOf('private func confirmActivation('),
  );
  const confirm = workflow.slice(
    workflow.indexOf('private func confirmActivation('),
    workflow.indexOf('private func pauseAll('),
  );
  const hash = workflow.slice(
    workflow.indexOf('private static func activationReviewHash('),
    workflow.indexOf('private static func messageReviewHash('),
  );

  for (const field of [
    'plannedReminderCount',
    'reminderWindowLabel',
    'reminderHorizon',
    'coexistence',
    'contactsReady',
    'messageUiReady',
    'protectedStorageReady',
    'readiness',
  ]) {
    assert.match(prepare, new RegExp(`"${field}"`, 'u'));
    assert.match(featureSchemas, new RegExp(`\\b${field}:`, 'u'));
  }
  assert.match(prepare, /activationReviewHash\(/u);
  assert.match(confirm, /activationReviewHash\(/u);
  assert.match(confirm, /activationReviewIsConfirmable/u);
  assert.match(confirm, /review\.blockerHash == currentReviewHash/u);
  assert.match(
    hash,
    /status\.reminderHorizonState == nil[\s\S]*status\.reminderHorizonState == \.full/u,
  );
  assert.match(hash, /status\.reminderPlans\.count/u);
  assert.match(hash, /status\.reminderHorizonState\?\.rawValue/u);
  assert.match(hash, /activationCoexistenceValue\(status\.coexistence\)/u);
  assert.match(hash, /canonicalHash\(readinessParts\)/u);
  assert.doesNotMatch(hash, /recipient|body|phone|messageDraft\.text/u);
});
