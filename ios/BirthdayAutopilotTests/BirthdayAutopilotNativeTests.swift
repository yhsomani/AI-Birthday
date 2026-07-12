import Foundation
import XCTest
@testable import BirthdayAutopilot

final class BirthdayAutopilotNativeTests: XCTestCase {
  func testGeminiOperationalPolicyIsCanonicalRemoteAndOffByDefault() {
    XCTAssertFalse(IOSGeminiOperationalPolicy.inAppDefault)
    XCTAssertEqual(
      IOSGeminiOperationalPolicy.parameterKey,
      "gemini_suggestions_enabled"
    )
    XCTAssertTrue(
      IOSGeminiOperationalPolicy.acceptsActivatedValue(
        sourceIsRemote: true,
        canonicalString: "true",
        boolValue: true
      )
    )
    for value in [
      (false, "true", true),
      (true, "TRUE", true),
      (true, " true", true),
      (true, "1", true),
      (true, "", false),
      (true, "false", false),
      (true, "true", false),
    ] {
      XCTAssertFalse(
        IOSGeminiOperationalPolicy.acceptsActivatedValue(
          sourceIsRemote: value.0,
          canonicalString: value.1,
          boolValue: value.2
        )
      )
    }
  }

  func testNativePresentationFormatterLocalizesProtectedDateLabels() throws {
    let hindi = Locale(identifier: "hi_IN")
    XCTAssertEqual(
      IOSNativePresentationFormatter.reminderWindowLabel(locale: hindi),
      "रिमाइंडर समय"
    )
    XCTAssertEqual(
      IOSNativePresentationFormatter.windowLabel(
        primaryStart: "09:00",
        primaryEnd: "11:00",
        graceEnd: "12:00",
        locale: hindi
      ),
      "09:00–11:00 · अतिरिक्त समय 12:00 तक"
    )
    let birthday = try XCTUnwrap(
      IOSNativePresentationFormatter.birthdayLabel(
        year: nil,
        month: 7,
        day: 12,
        locale: hindi
      )
    )
    XCTAssertFalse(birthday.contains("Birthday"))

    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    let date = try XCTUnwrap(calendar.date(from: DateComponents(
      year: 2026,
      month: 7,
      day: 18
    )))
    XCTAssertTrue(
      IOSNativePresentationFormatter.nextOccurrenceLabel(
        date,
        calendar: calendar,
        locale: hindi
      ).hasPrefix("अगला:")
    )
  }

  func testPeopleSyncFenceRejectsStaleAndWrongAccountGenerations() {
    let captured = IOSPeopleSyncFencePolicy.freshGeneration()
    let advanced = IOSPeopleSyncFencePolicy.freshGeneration()

    XCTAssertNotEqual(captured, advanced)
    XCTAssertTrue(
      IOSPeopleSyncFencePolicy.permitsCommit(
        capturedGeneration: captured,
        durableGeneration: captured,
        exactAccountGenerationMatches: true
      )
    )
    XCTAssertFalse(
      IOSPeopleSyncFencePolicy.permitsCommit(
        capturedGeneration: captured,
        durableGeneration: advanced,
        exactAccountGenerationMatches: true
      )
    )
    XCTAssertFalse(
      IOSPeopleSyncFencePolicy.permitsCommit(
        capturedGeneration: captured,
        durableGeneration: captured,
        exactAccountGenerationMatches: false
      )
    )
  }

  func testComposerTerminalOutcomesPreventUnsafeRepeat() {
    XCTAssertTrue(CompanionComposerOutcome.openCommitted.preventsRepeat)
    XCTAssertTrue(CompanionComposerOutcome.presented.preventsRepeat)
    XCTAssertTrue(CompanionComposerOutcome.outcomeUnknown.preventsRepeat)
    XCTAssertTrue(CompanionComposerOutcome.reportedSent.preventsRepeat)
    XCTAssertFalse(CompanionComposerOutcome.cancelled.preventsRepeat)
    XCTAssertFalse(CompanionComposerOutcome.failed.preventsRepeat)
  }

  func testPeopleParserRejectsMixedSourcePrivateFields() throws {
    let source = ["type": "CONTACT", "id": "source-1"]
    let person: [String: Any] = [
      "resourceName": "people/one",
      "metadata": ["sources": [source]],
      "names": [[
        "displayName": "Example",
        "metadata": ["source": source],
      ]],
      "birthdays": [[
        "date": ["month": 7, "day": 12],
        "metadata": ["source": source],
      ]],
      "phoneNumbers": [[
        "value": "+919999999999",
        "metadata": [
          "source": ["type": "PROFILE", "id": "profile-1"],
        ],
      ]],
    ]
    let data = try JSONSerialization.data(withJSONObject: ["connections": [person]])

    XCTAssertEqual(
      IOSPeopleJSONParser(maximumPagePeople: 1_000).parse(data),
      .failure(.partialSourceMerge)
    )
  }

  func testPeopleParserRejectsDuplicateProviderIdentity() throws {
    let source = ["type": "CONTACT", "id": "source-1"]
    let person: [String: Any] = [
      "resourceName": "people/one",
      "metadata": ["sources": [source]],
      "names": [],
      "birthdays": [],
      "phoneNumbers": [],
    ]
    let data = try JSONSerialization.data(
      withJSONObject: ["connections": [person, person]]
    )

    XCTAssertEqual(
      IOSPeopleJSONParser(maximumPagePeople: 1_000).parse(data),
      .failure(.duplicatePerson)
    )
  }

  func testGeminiPolicyAcceptsOnlyTheExactPublicRequestShape() {
    let request: [String: Any] = [
      "language": "hi",
      "tone": "warm",
      "placeholderMode": ["kind": "given-name", "requiredCount": 1],
      "requestedSegmentCap": 2,
    ]
    XCTAssertNotNil(IOSGeminiSuggestionPolicy.parseRequest(request))

    var requestWithPrivateField = request
    requestWithPrivateField["contactName"] = "private"
    XCTAssertNil(IOSGeminiSuggestionPolicy.parseRequest(requestWithPrivateField))
  }

  func testGeminiPolicyRejectsUnsafeOrPromotionalCandidates() throws {
    let request = try XCTUnwrap(
      IOSGeminiSuggestionPolicy.parseRequest([
        "language": "en",
        "tone": "simple",
        "placeholderMode": ["kind": "generic", "requiredCount": 0],
        "requestedSegmentCap": 1,
      ])
    )
    let raw = """
      {"candidates":[
        {"text":"Happy birthday!","language":"en"},
        {"text":"Birthday sale: buy now at example.com","language":"en"},
        {"text":"Remember our secret birthday story","language":"en"}
      ]}
      """

    XCTAssertEqual(
      IOSGeminiSuggestionPolicy.validatedCandidates(raw, request: request),
      ["Happy birthday!"]
    )
  }

  func testPublicSafeCodesNeverContainPrivateMaterial() {
    let errors: [CompanionStoreError] = [
      .accountMismatch,
      .accountUnavailable,
      .androidManaged,
      .coexistenceUnverified,
      .duplicateOperation,
      .ledgerCapacityReached,
      .invalidReview,
      .invalidWorkflowState,
      .nonceInvalid,
      .operationOutOfOrder,
      .proposalMissing,
      .repeatSuppressed,
      .resetFenceActive,
      .resetFenceOverflow,
      .staleRevision,
      .staleMaterial,
      .storageUnavailable,
      .unsupportedSchema,
    ]

    for error in errors {
      XCTAssertNotNil(error.safeCode.range(of: "^[A-Z0-9_]+$", options: .regularExpression))
      XCTAssertLessThanOrEqual(error.safeCode.utf8.count, 64)
    }
    XCTAssertEqual(Set(errors.map(\.safeCode)).count, errors.count)
  }

  func testCompanionRecurrencePlannerCoversTwentyYearsAndEveryLeapPolicy() throws {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 0))
    let unknownYear = IOSPeoplePrivateBirthday(
      localId: "birthday-unknown", year: nil, month: 7, day: 12
    )
    let knownYear = IOSPeoplePrivateBirthday(
      localId: "birthday-known", year: 1980, month: 7, day: 12
    )
    let leap = IOSPeoplePrivateBirthday(
      localId: "birthday-leap", year: nil, month: 2, day: 29
    )

    for year in 2024...2043 {
      XCTAssertEqual(
        IOSCompanionRecurrencePlanner.occurrenceDate(
          in: year,
          birthday: unknownYear,
          leapPolicy: nil,
          schedulingCalendar: calendar
        ),
        exactDate(year: year, month: 7, day: 12, calendar: calendar)
      )
      XCTAssertEqual(
        IOSCompanionRecurrencePlanner.occurrenceDate(
          in: year,
          birthday: unknownYear,
          leapPolicy: nil,
          schedulingCalendar: calendar
        ),
        IOSCompanionRecurrencePlanner.occurrenceDate(
          in: year,
          birthday: knownYear,
          leapPolicy: nil,
          schedulingCalendar: calendar
        ),
        "A source birth year must not change an annual month/day recurrence"
      )
      let isLeap =
        year.isMultiple(of: 4)
        && (!year.isMultiple(of: 100) || year.isMultiple(of: 400))
      XCTAssertEqual(
        IOSCompanionRecurrencePlanner.occurrenceDate(
          in: year,
          birthday: leap,
          leapPolicy: "feb-28",
          schedulingCalendar: calendar
        ),
        exactDate(
          year: year,
          month: 2,
          day: isLeap ? 29 : 28,
          calendar: calendar
        )
      )
      XCTAssertEqual(
        IOSCompanionRecurrencePlanner.occurrenceDate(
          in: year,
          birthday: leap,
          leapPolicy: "mar-01",
          schedulingCalendar: calendar
        ),
        exactDate(
          year: year,
          month: isLeap ? 2 : 3,
          day: isLeap ? 29 : 1,
          calendar: calendar
        )
      )
      XCTAssertEqual(
        IOSCompanionRecurrencePlanner.occurrenceDate(
          in: year,
          birthday: leap,
          leapPolicy: "skip",
          schedulingCalendar: calendar
        ),
        isLeap
          ? exactDate(year: year, month: 2, day: 29, calendar: calendar)
          : nil
      )
    }

    XCTAssertNil(
      IOSCompanionRecurrencePlanner.occurrenceDate(
        in: 2028,
        birthday: leap,
        leapPolicy: nil,
        schedulingCalendar: calendar
      )
    )
    XCTAssertNil(
      IOSCompanionRecurrencePlanner.occurrenceDate(
        in: 2100,
        birthday: leap,
        leapPolicy: "skip",
        schedulingCalendar: calendar
      )
    )
    XCTAssertNotNil(
      IOSCompanionRecurrencePlanner.occurrenceDate(
        in: 2104,
        birthday: leap,
        leapPolicy: "skip",
        schedulingCalendar: calendar
      )
    )
  }

  func testCompanionRecurrencePlannerUsesExactlyFourHundredCivilDates() throws {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 0))
    let start = try XCTUnwrap(
      exactDate(year: 2026, month: 1, day: 1, calendar: calendar)
    )
    let februaryFifth = IOSPeoplePrivateBirthday(
      localId: "birthday-boundary", year: nil, month: 2, day: 5
    )
    let dates = IOSCompanionRecurrencePlanner.occurrenceDates(
      birthday: februaryFifth,
      leapPolicy: nil,
      from: start,
      schedulingCalendar: calendar
    )

    // 2027-02-05 is start + 400 and therefore outside an exact 400-date horizon
    // (start through start + 399). The former implementation included both occurrences.
    XCTAssertEqual(
      dates,
      [try XCTUnwrap(exactDate(year: 2026, month: 2, day: 5, calendar: calendar))]
    )
    let lastIncluded = try XCTUnwrap(
      calendar.date(
        byAdding: .day,
        value: IOSCompanionRecurrencePlanner.planningDays - 1,
        to: calendar.startOfDay(for: start)
      )
    )
    XCTAssertTrue(dates.allSatisfy { $0 <= lastIncluded })
  }

  func testPeopleWholeSnapshotCapacityMatchesTheTenThousandContactReleaseGate() {
    let limits = IOSPeopleSyncLimits()

    XCTAssertEqual(IOSPeopleCapacityPolicy.maximumPeople, 10_000)
    XCTAssertEqual(limits.maximumPeople, 10_000)
    XCTAssertEqual(limits.maximumPageBytes, 4 * 1_024 * 1_024)
    XCTAssertEqual(limits.maximumTotalBytes, 16 * 1_024 * 1_024)
    XCTAssertEqual(IOSPeopleCapacityPolicy.maximumEncryptedSnapshotBytes, 32 * 1_024 * 1_024)
  }

  func testBirthdayMessageContentPolicyMatchesAndroidSafetyCategories() {
    let cases = [
      ("Hi {firstName} #birthday", "en", "template-tracking-not-allowed"),
      ("Hi {firstName} utm_source=x", "en", "template-tracking-not-allowed"),
      ("Limited offer for {firstName}", "en", "template-promotional-content"),
      ("Remember when, {firstName}", "en", "template-sensitive-content"),
      ("Happy 30th birthday, {firstName}", "en", "template-sensitive-content"),
      ("जन्मदिन मुबारक, {firstName}!", "en", "template-language-mismatch"),
      ("Happy birthday, {firstName}!", "hi", "template-language-mismatch"),
    ]
    for (text, language, expected) in cases {
      XCTAssertTrue(
        IOSBirthdayMessageContentPolicy.issueCodes(
          text: text,
          declaredLanguage: language
        ).contains(expected),
        "Expected \(expected) for \(text)"
      )
    }
    XCTAssertTrue(
      IOSBirthdayMessageContentPolicy.issueCodes(
        text: "Happy birthday, {firstName}!",
        declaredLanguage: "en"
      ).isEmpty
    )
    XCTAssertTrue(
      IOSBirthdayMessageContentPolicy.issueCodes(
        text: "जन्मदिन मुबारक हो!",
        declaredLanguage: "hi"
      ).isEmpty
    )
  }

  func testBirthdayMessageRenderedBodyNeverSilentlySubstitutesAnUnsafeName() {
    XCTAssertNil(
      IOSBirthdayMessageContentPolicy.renderedBody(
        templateText: "Happy birthday, {firstName}!",
        placeholderMode: "given-name",
        givenName: "उदाहरण",
        declaredLanguage: "en"
      )
    )
    XCTAssertEqual(
      IOSBirthdayMessageContentPolicy.renderedBody(
        templateText: "Happy birthday!",
        placeholderMode: "generic",
        givenName: nil,
        declaredLanguage: "en"
      ),
      "Happy birthday!"
    )
  }

  func testPersistedSafeDraftPolicyUpgradeInvalidatesApprovalsAndProposals() throws {
    let now = Date(timeIntervalSince1970: 1_700_000_600)
    let binding = IOSNativeGoogleAccountBinding(
      googleSubject: "1234567890",
      firebaseUID: "firebase-user-1",
      displayEmail: "person@example.com",
      displayName: nil,
      accountGeneration: "11111111-1111-4111-8111-111111111111"
    )
    var workflow = CompanionWorkflowState.empty(binding: binding)
    workflow.configurationGeneration = 4
    workflow.messageDraft = CompanionWorkflowMessageDraft(
      language: "en",
      tone: "warm",
      placeholderMode: "generic",
      text: "Happy birthday!",
      requestedSegmentCap: 1,
      provenance: CompanionWorkflowMessageProvenance(
        source: "USER",
        modelIdentifier: nil,
        promptPolicyVersion: nil,
        validatorVersion: "sms-template-validator-v1"
      )
    )
    workflow.contacts = [CompanionWorkflowContact(
      contactId: "contact-1",
      selectedPhoneId: "phone-1",
      selectedBirthdayId: "birthday-1",
      leapPolicy: nil,
      enrollment: .enabled,
      materialRevision: 1,
      approvalHash: String(repeating: "a", count: 64),
      approvedAt: now.addingTimeInterval(-60),
      approvalInvalidationReasons: [],
      lastOutcomeLabel: nil,
      updatedAt: now.addingTimeInterval(-60)
    )]
    workflow.occurrences = [CompanionWorkflowOccurrence(
      occurrenceId: "occurrence-1",
      proposalId: "proposal-1",
      contactId: "contact-1",
      civilDate: "2026-07-12",
      phase: .reminderPlanned,
      updatedAt: now.addingTimeInterval(-60)
    )]

    XCTAssertEqual(
      IOSCompanionPersistedDraftRecovery.apply(
        to: &workflow,
        now: now,
        validatorVersion: IOSBirthdayMessageContentPolicy.validatorVersion,
        contentIssueCodes: { text, language in
          IOSBirthdayMessageContentPolicy.issueCodes(
            text: text,
            declaredLanguage: language
          )
        }
      ),
      .revalidatedDraft
    )
    XCTAssertEqual(workflow.configurationGeneration, 5)
    XCTAssertEqual(
      workflow.messageDraft?.provenance?.validatorVersion,
      IOSBirthdayMessageContentPolicy.validatorVersion
    )
    XCTAssertEqual(
      try XCTUnwrap(workflow.contacts.first).approvalInvalidationReasons,
      ["template-changed"]
    )
    XCTAssertTrue(workflow.occurrences.isEmpty)
    XCTAssertTrue(workflow.reviews.isEmpty)
  }

  func testCompanionMessagePlaceholderPolicyRejectsEveryUnsafeStructure() {
    XCTAssertEqual(
      IOSCompanionMessagePlaceholderPolicy.issue(
        text: "Happy birthday, {firstName}!",
        placeholderMode: "generic"
      ),
      .invalidCount
    )
    XCTAssertEqual(
      IOSCompanionMessagePlaceholderPolicy.issue(
        text: "Happy birthday!",
        placeholderMode: "given-name"
      ),
      .invalidCount
    )
    XCTAssertEqual(
      IOSCompanionMessagePlaceholderPolicy.issue(
        text: "Hi {firstName}, happy birthday {firstName}!",
        placeholderMode: "given-name"
      ),
      .invalidCount
    )
    for text in [
      "Happy birthday, {first_name}!",
      "Happy birthday, {FirstName}!",
      "Happy birthday, {friend!",
      "Happy birthday, friend}!",
    ] {
      XCTAssertEqual(
        IOSCompanionMessagePlaceholderPolicy.issue(
          text: text,
          placeholderMode: "generic"
        ),
        .unsupportedPlaceholder,
        text
      )
    }
    XCTAssertEqual(
      IOSCompanionMessagePlaceholderPolicy.render(
        text: "Happy birthday!",
        placeholderMode: "generic",
        givenName: nil
      ),
      "Happy birthday!"
    )
    XCTAssertEqual(
      IOSCompanionMessagePlaceholderPolicy.render(
        text: "Happy birthday, {firstName}!",
        placeholderMode: "given-name",
        givenName: "Asha"
      ),
      "Happy birthday, Asha!"
    )
  }

  func testPersistedInvalidDraftRecoveryFailsClosedWithoutDeletingDurableUserData() throws {
    let originalDate = Date(timeIntervalSince1970: 1_700_000_000)
    let recoveryDate = Date(timeIntervalSince1970: 1_700_000_600)
    let binding = IOSNativeGoogleAccountBinding(
      googleSubject: "1234567890",
      firebaseUID: "firebase-user-1",
      displayEmail: "person@example.com",
      displayName: "Example Person",
      accountGeneration: "11111111-1111-4111-8111-111111111111"
    )
    let policy = CompanionWorkflowPolicy(
      primaryStart: "09:00",
      primaryEnd: "10:00",
      graceEnd: "11:00",
      legacyAndroidDailyCap: 1
    )
    let invalidDraft = CompanionWorkflowMessageDraft(
      language: "en",
      tone: "warm",
      placeholderMode: "given-name",
      text: "Happy birthday, {firstName} {firstName}!",
      requestedSegmentCap: 1,
      provenance: CompanionWorkflowMessageProvenance(
        source: "USER",
        modelIdentifier: nil,
        promptPolicyVersion: nil,
        validatorVersion: "birthday-message-v1"
      )
    )
    var workflow = CompanionWorkflowState.empty(binding: binding)
    workflow.configurationGeneration = 7
    workflow.contacts = [CompanionWorkflowContact(
      contactId: "contact-1",
      selectedPhoneId: "phone-1",
      selectedBirthdayId: "birthday-1",
      leapPolicy: "feb-28",
      enrollment: .enabled,
      materialRevision: 4,
      approvalHash: String(repeating: "a", count: 64),
      approvedAt: originalDate,
      approvalInvalidationReasons: ["phone-changed"],
      lastOutcomeLabel: "composer-cancelled",
      updatedAt: originalDate
    )]
    workflow.messageDraft = invalidDraft
    workflow.policy = policy
    workflow.desired = .remindersOn
    workflow.hasEverActivatedReminders = true
    workflow.reviews = [CompanionWorkflowReview(
      handle: "review-1",
      kind: .message,
      issuedForRevision: "7",
      expiresAt: recoveryDate,
      blockerHash: String(repeating: "b", count: 64),
      contactIds: ["contact-1"],
      messageDraft: invalidDraft,
      policy: nil,
      privacyAction: nil,
      occurrenceId: nil,
      consumedAt: nil
    )]
    workflow.occurrences = [CompanionWorkflowOccurrence(
      occurrenceId: "occurrence-1",
      proposalId: "proposal-1",
      contactId: "contact-1",
      civilDate: "2026-07-12",
      phase: .reminderPlanned,
      updatedAt: originalDate
    )]
    workflow.activity = [CompanionWorkflowActivity(
      id: "activity-1",
      kind: "sync",
      reason: nil,
      occurredAt: originalDate,
      actionable: false
    )]
    workflow.activityClearedAt = originalDate
    workflow.privacyOperations = [CompanionWorkflowPrivacyOperation(
      id: "privacy-1",
      action: "wipe-local-data",
      phase: "complete",
      reason: nil,
      updatedAt: originalDate
    )]
    workflow.lastCoordinationSuccessAt = originalDate

    XCTAssertEqual(
      IOSCompanionPersistedDraftRecovery.apply(
        to: &workflow,
        now: recoveryDate
      ),
      .clearedInvalidDraft
    )
    XCTAssertEqual(workflow.account, CompanionWorkflowAccount(binding: binding))
    XCTAssertEqual(workflow.configurationGeneration, 8)
    XCTAssertNil(workflow.messageDraft)
    XCTAssertEqual(
      workflow.desired.rawValue,
      CompanionWorkflowDesired.paused.rawValue
    )
    XCTAssertTrue(workflow.reviews.isEmpty)
    XCTAssertTrue(workflow.occurrences.isEmpty)
    XCTAssertEqual(workflow.policy, policy)
    XCTAssertEqual(workflow.hasEverActivatedReminders, true)
    XCTAssertEqual(workflow.activity.count, 1)
    XCTAssertEqual(workflow.activity.first?.id, "activity-1")
    XCTAssertEqual(workflow.activityClearedAt, originalDate)
    XCTAssertEqual(workflow.privacyOperations.count, 1)
    XCTAssertEqual(workflow.privacyOperations.first?.id, "privacy-1")
    XCTAssertEqual(workflow.lastCoordinationSuccessAt, originalDate)

    let contact = try XCTUnwrap(workflow.contacts.first)
    XCTAssertEqual(contact.contactId, "contact-1")
    XCTAssertEqual(contact.selectedPhoneId, "phone-1")
    XCTAssertEqual(contact.selectedBirthdayId, "birthday-1")
    XCTAssertEqual(contact.leapPolicy, "feb-28")
    XCTAssertEqual(
      contact.enrollment.rawValue,
      CompanionWorkflowEnrollment.enabled.rawValue
    )
    XCTAssertEqual(contact.materialRevision, 4)
    XCTAssertEqual(contact.approvalHash, String(repeating: "a", count: 64))
    XCTAssertEqual(contact.approvedAt, originalDate)
    XCTAssertEqual(
      contact.approvalInvalidationReasons,
      ["phone-changed", "template-changed"]
    )
    XCTAssertEqual(contact.lastOutcomeLabel, "composer-cancelled")
    XCTAssertEqual(contact.updatedAt, recoveryDate)
  }

  func testPersistedValidGenericDraftNeedsNoGivenNameAndDoesNotRecover() {
    let binding = IOSNativeGoogleAccountBinding(
      googleSubject: "1234567890",
      firebaseUID: "firebase-user-1",
      displayEmail: "person@example.com",
      displayName: nil,
      accountGeneration: "11111111-1111-4111-8111-111111111111"
    )
    let draft = CompanionWorkflowMessageDraft(
      language: "en",
      tone: "simple",
      placeholderMode: "generic",
      text: "Happy birthday!",
      requestedSegmentCap: 1,
      provenance: nil
    )
    var workflow = CompanionWorkflowState.empty(binding: binding)
    workflow.messageDraft = draft

    XCTAssertEqual(
      IOSCompanionPersistedDraftRecovery.apply(to: &workflow, now: Date()),
      .unchanged
    )
    XCTAssertEqual(workflow.messageDraft, draft)
    XCTAssertEqual(
      IOSCompanionMessagePlaceholderPolicy.render(
        text: draft.text,
        placeholderMode: draft.placeholderMode,
        givenName: nil
      ),
      draft.text
    )
  }

  func testCompanionRecurrencePlannerIsGregorianAcrossDSTZonesAndCalendarPreferences() throws {
    let zones = ["America/New_York", "Europe/Berlin", "Australia/Lord_Howe"]
    for zoneIdentifier in zones {
      let zone = try XCTUnwrap(TimeZone(identifier: zoneIdentifier))
      var selectedCalendar = Calendar(identifier: .buddhist)
      selectedCalendar.timeZone = zone
      var gregorian = Calendar(identifier: .gregorian)
      gregorian.timeZone = zone
      let start = try XCTUnwrap(
        exactDate(year: 2027, month: 1, day: 1, calendar: gregorian)
      )
      let birthday = IOSPeoplePrivateBirthday(
        localId: "birthday-\(zoneIdentifier)", year: nil, month: 11, day: 7
      )
      let dates = IOSCompanionRecurrencePlanner.occurrenceDates(
        birthday: birthday,
        leapPolicy: nil,
        from: start,
        schedulingCalendar: selectedCalendar
      )

      XCTAssertFalse(dates.isEmpty)
      for date in dates {
        let components = gregorian.dateComponents([.month, .day], from: date)
        XCTAssertEqual(components.month, 11, zoneIdentifier)
        XCTAssertEqual(components.day, 7, zoneIdentifier)
      }
    }
  }

  func testCompanionRecurrencePlannerRecalculatesUTCPlusFourteenToUTCMinusTwelveTravel()
    throws
  {
    let now = try XCTUnwrap(
      ISO8601DateFormatter().date(from: "2026-07-11T20:00:00Z")
    )
    let birthday = IOSPeoplePrivateBirthday(
      localId: "birthday-travel", year: nil, month: 7, day: 12
    )
    var east = Calendar(identifier: .gregorian)
    east.timeZone = try XCTUnwrap(TimeZone(identifier: "Pacific/Kiritimati"))
    var west = Calendar(identifier: .gregorian)
    west.timeZone = try XCTUnwrap(TimeZone(identifier: "Etc/GMT+12"))
    let eastFirst = try XCTUnwrap(
      IOSCompanionRecurrencePlanner.occurrenceDates(
        birthday: birthday,
        leapPolicy: nil,
        from: now,
        schedulingCalendar: east
      ).first
    )
    let westFirst = try XCTUnwrap(
      IOSCompanionRecurrencePlanner.occurrenceDates(
        birthday: birthday,
        leapPolicy: nil,
        from: now,
        schedulingCalendar: west
      ).first
    )

    XCTAssertEqual(east.dateComponents([.year, .month, .day], from: eastFirst).day, 12)
    XCTAssertEqual(west.dateComponents([.year, .month, .day], from: westFirst).day, 12)
    XCTAssertEqual(westFirst.timeIntervalSince(eastFirst), 26 * 60 * 60)
  }

  func testDestinationBlocklistIsCanonicalBoundedAndDoesNotRestoreByUnblocking() throws {
    let first = "+919876543210"
    let second = "+14155550123"
    XCTAssertTrue(IOSCompanionDestinationBlocklistPolicy.isCanonical(first))
    XCTAssertFalse(IOSCompanionDestinationBlocklistPolicy.isCanonical("9876543210"))
    XCTAssertFalse(IOSCompanionDestinationBlocklistPolicy.isCanonical("+0123"))

    let blocked = try XCTUnwrap(
      IOSCompanionDestinationBlocklistPolicy.updated(
        blocked: true,
        destination: first,
        current: [second]
      )
    )
    XCTAssertEqual(blocked, [second, first].sorted())
    XCTAssertEqual(
      IOSCompanionDestinationBlocklistPolicy.updated(
        blocked: true,
        destination: first,
        current: blocked
      ),
      blocked
    )

    let unblocked = try XCTUnwrap(
      IOSCompanionDestinationBlocklistPolicy.updated(
        blocked: false,
        destination: first,
        current: blocked
      )
    )
    XCTAssertEqual(unblocked, [second])
    XCTAssertNil(
      IOSCompanionDestinationBlocklistPolicy.normalized(
        Array(
          repeating: first,
          count: IOSCompanionDestinationBlocklistPolicy.maximumDestinations + 1
        )
      )
    )
  }

  func testContactsConsentRequiresDisclosureAndIsIdempotentForCurrentVersions() throws {
    var receipts: [CompanionWorkflowConsentReceipt]?
    var ids = [
      "00000000-0000-4000-8000-000000000001",
      "00000000-0000-4000-8000-000000000002",
    ]
    let makeId = { ids.removeFirst() }
    let now = Date(timeIntervalSince1970: 1_800_000_000)

    XCTAssertFalse(
      IOSCompanionConsentLedgerPolicy.hasCurrentContactsDisclosure(receipts)
    )
    XCTAssertFalse(
      IOSCompanionConsentLedgerPolicy.recordContactsGrant(
        receipts: &receipts,
        disclosureAcknowledged: false,
        at: now,
        makeId: makeId
      )
    )
    XCTAssertNil(receipts)
    XCTAssertTrue(
      IOSCompanionConsentLedgerPolicy.recordContactsGrant(
        receipts: &receipts,
        disclosureAcknowledged: true,
        at: now,
        makeId: makeId
      )
    )
    XCTAssertEqual(receipts?.count, 2)
    XCTAssertTrue(
      IOSCompanionConsentLedgerPolicy.hasCurrentContactsDisclosure(receipts)
    )
    XCTAssertTrue(IOSCompanionConsentLedgerPolicy.isValid(receipts))
    XCTAssertEqual(
      IOSCompanionConsentLedgerPolicy.versions(receipts),
      [
        IOSCompanionConsentLedgerPolicy.contactsDisclosureVersion,
        IOSCompanionConsentLedgerPolicy.contactsScopeVersion,
      ].sorted()
    )

    XCTAssertTrue(
      IOSCompanionConsentLedgerPolicy.recordContactsGrant(
        receipts: &receipts,
        disclosureAcknowledged: false,
        at: now.addingTimeInterval(60),
        makeId: { XCTFail("current consent must not append"); return "" }
      )
    )
    XCTAssertEqual(receipts?.count, 2)
  }

  func testContactsDisconnectAndProviderRevokeAppendTruthfulSupersedingDecisions() throws {
    var receipts: [CompanionWorkflowConsentReceipt]?
    var next = 1
    let makeId = {
      defer { next += 1 }
      return String(format: "00000000-0000-4000-8000-%012d", next)
    }
    let now = Date(timeIntervalSince1970: 1_800_000_000)
    XCTAssertTrue(
      IOSCompanionConsentLedgerPolicy.recordContactsGrant(
        receipts: &receipts,
        disclosureAcknowledged: true,
        at: now,
        makeId: makeId
      )
    )
    XCTAssertTrue(
      IOSCompanionConsentLedgerPolicy.recordDisclosureRevoked(
        receipts: &receipts,
        at: now.addingTimeInterval(1),
        makeId: makeId
      )
    )
    XCTAssertFalse(
      IOSCompanionConsentLedgerPolicy.hasCurrentContactsDisclosure(receipts)
    )
    // A local disconnect revokes only the app disclosure. The OAuth scope
    // remains truthful until the official Google disconnect has succeeded.
    XCTAssertFalse(
      IOSCompanionConsentLedgerPolicy.hasCurrentContactsScopeRevoked(receipts)
    )
    XCTAssertTrue(
      IOSCompanionConsentLedgerPolicy.recordScopeRevoked(
        receipts: &receipts,
        at: now.addingTimeInterval(2),
        makeId: makeId
      )
    )
    XCTAssertTrue(
      IOSCompanionConsentLedgerPolicy.hasCurrentContactsScopeRevoked(receipts)
    )
    XCTAssertTrue(IOSCompanionConsentLedgerPolicy.isValid(receipts))
    let disclosure = try XCTUnwrap(
      receipts?.filter { $0.kind == .contactsDisclosure }.last
    )
    let scope = try XCTUnwrap(receipts?.filter { $0.kind == .contactsReadOnly }.last)
    XCTAssertEqual(disclosure.decision, .revoked)
    XCTAssertEqual(disclosure.sequence, 2)
    XCTAssertNotNil(disclosure.supersedesReceiptId)
    XCTAssertEqual(scope.decision, .revoked)
    XCTAssertEqual(scope.sequence, 2)
    XCTAssertNotNil(scope.supersedesReceiptId)
  }

  func testApprovalDestinationBindingChangesWithExactE164AndMetadataPolicy() throws {
    let us = try XCTUnwrap(
      IOSCompanionApprovalDestinationBinding.resolve(
        selectedPhoneId: "phone-1",
        phones: [IOSPeoplePrivatePhone(localId: "phone-1", e164: "+14155550123")]
      )
    )
    let india = try XCTUnwrap(
      IOSCompanionApprovalDestinationBinding.resolve(
        selectedPhoneId: "phone-1",
        phones: [IOSPeoplePrivatePhone(localId: "phone-1", e164: "+919876543210")]
      )
    )

    XCTAssertNotEqual(us.hashComponents, india.hashComponents)
    XCTAssertEqual(us.phoneId, "phone-1")
    XCTAssertEqual(us.e164, "+14155550123")
    XCTAssertEqual(us.metadataRelease, IOSPhoneNumberNormalizer.metadataRelease)
    XCTAssertEqual(
      us.hashComponents.first,
      IOSCompanionApprovalDestinationBinding.version
    )
    XCTAssertNil(
      IOSCompanionApprovalDestinationBinding.resolve(
        selectedPhoneId: "phone-1",
        phones: [IOSPeoplePrivatePhone(localId: "phone-1", e164: nil)]
      )
    )
  }

  func testContactsFreshnessUsesExactTrustedSevenAndThirtyDayBoundaries() throws {
    let now = Date(timeIntervalSince1970: 4_000_000)
    let atSeven = now.addingTimeInterval(-IOSContactsFreshnessPolicy.normalMaximumAge)
    let afterSeven = atSeven.addingTimeInterval(-0.001)
    let atThirty = now.addingTimeInterval(-IOSContactsFreshnessPolicy.companionMaximumAge)
    let afterThirty = atThirty.addingTimeInterval(-0.001)

    XCTAssertEqual(
      IOSContactsFreshnessPolicy.assess(
        sourceState: .verified,
        lastSuccessAt: atSeven,
        trustedNow: now
      ).band,
      .normal
    )
    XCTAssertEqual(
      IOSContactsFreshnessPolicy.assess(
        sourceState: .verified,
        lastSuccessAt: afterSeven,
        trustedNow: now
      ).band,
      .staleWarning
    )
    XCTAssertEqual(
      IOSContactsFreshnessPolicy.assess(
        sourceState: .retainedAfterFailure,
        lastSuccessAt: atThirty,
        trustedNow: now
      ).band,
      .staleWarning
    )
    XCTAssertEqual(
      IOSContactsFreshnessPolicy.assess(
        sourceState: .retainedAfterFailure,
        lastSuccessAt: afterThirty,
        trustedNow: now
      ).band,
      .safetyPaused
    )
    XCTAssertTrue(
      IOSContactsFreshnessPolicy.assess(
        sourceState: .retainedAfterFailure,
        lastSuccessAt: now.addingTimeInterval(-86_400),
        trustedNow: now
      ).allowsCompanionAction
    )
    XCTAssertEqual(
      IOSContactsFreshnessPolicy.assess(
        sourceState: .verified,
        lastSuccessAt: now.addingTimeInterval(1),
        trustedNow: now
      ).band,
      .untrusted
    )
    XCTAssertEqual(
      IOSContactsFreshnessPolicy.assess(
        sourceState: .authorizationRequired,
        lastSuccessAt: now,
        trustedNow: now
      ).band,
      .safetyPaused
    )
    XCTAssertEqual(
      IOSContactsFreshnessPolicy.assess(
        sourceState: .verified,
        lastSuccessAt: now,
        trustedNow: nil
      ).band,
      .untrusted
    )
  }

  func testContactsTrustedTimeRejectsRollbackAndExpiredControlObservation() throws {
    let server = Date(timeIntervalSince1970: 4_000_000)
    let received = Date(timeIntervalSince1970: 10_000)
    let estimated = try XCTUnwrap(
      IOSContactsFreshnessPolicy.estimateTrustedNow(
        serverObservedAt: server,
        locallyReceivedAt: received,
        now: received.addingTimeInterval(60),
        maximumObservationAge: 60
      )
    )
    XCTAssertEqual(estimated, server.addingTimeInterval(60))
    XCTAssertNil(
      IOSContactsFreshnessPolicy.estimateTrustedNow(
        serverObservedAt: server,
        locallyReceivedAt: received,
        now: received.addingTimeInterval(-0.001),
        maximumObservationAge: 60
      )
    )
    XCTAssertNil(
      IOSContactsFreshnessPolicy.estimateTrustedNow(
        serverObservedAt: server,
        locallyReceivedAt: received,
        now: received.addingTimeInterval(60.001),
        maximumObservationAge: 60
      )
    )
  }

  private func exactDate(
    year: Int,
    month: Int,
    day: Int,
    calendar: Calendar
  ) -> Date? {
    calendar.date(
      from: DateComponents(
        calendar: calendar,
        timeZone: calendar.timeZone,
        year: year,
        month: month,
        day: day
      )
    )
  }
}
