import CryptoKit
import Foundation

struct CompanionWorkflowAccount: Codable, Equatable {
  let googleSubject: String
  let firebaseUID: String
  let accountGeneration: String

  init(binding: IOSNativeGoogleAccountBinding) {
    googleSubject = binding.googleSubject
    firebaseUID = binding.firebaseUID
    accountGeneration = binding.accountGeneration
  }

  func matches(_ binding: IOSNativeGoogleAccountBinding) -> Bool {
    googleSubject == binding.googleSubject && firebaseUID == binding.firebaseUID
      && accountGeneration == binding.accountGeneration
  }
}

enum CompanionWorkflowEnrollment: String, Codable {
  case off
  case enabled
  case paused
  case excluded
}

struct CompanionWorkflowContact: Codable {
  let contactId: String
  var selectedPhoneId: String?
  var selectedBirthdayId: String?
  var leapPolicy: String?
  var enrollment: CompanionWorkflowEnrollment
  var materialRevision: UInt64
  var approvalHash: String?
  var approvedAt: Date?
  var approvalInvalidationReasons: [String]
  var lastOutcomeLabel: String?
  var updatedAt: Date
}

struct CompanionWorkflowMessageDraft: Codable, Equatable {
  let language: String
  let tone: String
  let placeholderMode: String
  let text: String
  let requestedSegmentCap: Int
  // Optional migrates schema-v2 drafts written before provenance tracking.
  // Missing provenance is conservatively treated as USER.
  let provenance: CompanionWorkflowMessageProvenance?
}

struct CompanionWorkflowMessageProvenance: Codable, Equatable {
  let source: String
  let modelIdentifier: String?
  let promptPolicyVersion: String?
  let validatorVersion: String
}

struct CompanionWorkflowPolicy: Codable, Equatable {
  let primaryStart: String
  let primaryEnd: String
  let graceEnd: String?
  // Kept under the historical `dailyCap` coding key so existing protected
  // state and the shared React Native bridge remain readable. iOS Companion
  // does not send in the background, so this Android sending limit is never
  // used to reject a policy or discard a proposal/reminder plan.
  let legacyAndroidDailyCap: Int

  private enum CodingKeys: String, CodingKey {
    case primaryStart
    case primaryEnd
    case graceEnd
    case legacyAndroidDailyCap = "dailyCap"
  }
}

enum CompanionWorkflowDesired: String, Codable {
  case remindersOn = "composer-reminders-on"
  case paused
}

enum CompanionWorkflowConsentKind: String, Codable {
  case contactsDisclosure = "CONTACTS_DISCLOSURE"
  case contactsReadOnly = "CONTACTS_READONLY"
}

enum CompanionWorkflowConsentDecision: String, Codable {
  case granted = "GRANTED"
  case revoked = "REVOKED"
}

struct CompanionWorkflowConsentReceipt: Codable, Equatable {
  let id: String
  let kind: CompanionWorkflowConsentKind
  let decision: CompanionWorkflowConsentDecision
  let disclosureVersion: String
  let scopeHash: String
  let sequence: UInt64
  let supersedesReceiptId: String?
  let recordedAt: Date
}

enum IOSCompanionConsentLedgerPolicy {
  static let contactsDisclosureVersion = "contacts-device-storage-v1"
  static let contactsScopeVersion = "google-contacts-readonly-v1"
  static let maximumReceipts = 64

  static var contactsDisclosureScopeHash: String {
    scopeHash(
      domain: "BirthdayAutopilot.iOS.ContactsDisclosure.v1",
      values: [contactsDisclosureVersion]
    )
  }

  static var contactsReadOnlyScopeHash: String {
    scopeHash(
      domain: "BirthdayAutopilot.iOS.ContactsOAuthScope.v1",
      values: [birthdayContactsReadOnlyScope]
    )
  }

  static func recordContactsGrant(
    receipts: inout [CompanionWorkflowConsentReceipt]?,
    disclosureAcknowledged: Bool,
    at now: Date,
    makeId: () -> String = { UUID().uuidString.lowercased() }
  ) -> Bool {
    var updated = receipts ?? []
    guard isValid(updated) else { return false }
    if !isCurrent(
      updated,
      kind: .contactsDisclosure,
      decision: .granted,
      version: contactsDisclosureVersion,
      scopeHash: contactsDisclosureScopeHash
    ) {
      guard disclosureAcknowledged,
        append(
          to: &updated,
          kind: .contactsDisclosure,
          decision: .granted,
          version: contactsDisclosureVersion,
          scopeHash: contactsDisclosureScopeHash,
          at: now,
          makeId: makeId
        )
      else { return false }
    }
    guard isCurrent(
      updated,
      kind: .contactsReadOnly,
      decision: .granted,
      version: contactsScopeVersion,
      scopeHash: contactsReadOnlyScopeHash
    ) || append(
      to: &updated,
      kind: .contactsReadOnly,
      decision: .granted,
      version: contactsScopeVersion,
      scopeHash: contactsReadOnlyScopeHash,
      at: now,
      makeId: makeId
    ) else { return false }
    guard isValid(updated) else { return false }
    receipts = updated
    return true
  }

  static func recordDisclosureRevoked(
    receipts: inout [CompanionWorkflowConsentReceipt]?,
    at now: Date,
    makeId: () -> String = { UUID().uuidString.lowercased() }
  ) -> Bool {
    recordDecision(
      receipts: &receipts,
      kind: .contactsDisclosure,
      decision: .revoked,
      version: contactsDisclosureVersion,
      scopeHash: contactsDisclosureScopeHash,
      at: now,
      makeId: makeId
    )
  }

  static func recordScopeRevoked(
    receipts: inout [CompanionWorkflowConsentReceipt]?,
    at now: Date,
    makeId: () -> String = { UUID().uuidString.lowercased() }
  ) -> Bool {
    recordDecision(
      receipts: &receipts,
      kind: .contactsReadOnly,
      decision: .revoked,
      version: contactsScopeVersion,
      scopeHash: contactsReadOnlyScopeHash,
      at: now,
      makeId: makeId
    )
  }

  static func versions(_ receipts: [CompanionWorkflowConsentReceipt]?) -> [String] {
    Array(Set((receipts ?? []).map(\.disclosureVersion))).sorted()
  }

  static func hasCurrentContactsDisclosure(
    _ receipts: [CompanionWorkflowConsentReceipt]?
  ) -> Bool {
    let values = receipts ?? []
    guard isValid(values) else { return false }
    return isCurrent(
      values,
      kind: .contactsDisclosure,
      decision: .granted,
      version: contactsDisclosureVersion,
      scopeHash: contactsDisclosureScopeHash
    )
  }

  static func hasCurrentContactsScopeRevoked(
    _ receipts: [CompanionWorkflowConsentReceipt]?
  ) -> Bool {
    let values = receipts ?? []
    guard isValid(values) else { return false }
    return isCurrent(
      values,
      kind: .contactsReadOnly,
      decision: .revoked,
      version: contactsScopeVersion,
      scopeHash: contactsReadOnlyScopeHash
    )
  }

  static func isValid(_ receipts: [CompanionWorkflowConsentReceipt]?) -> Bool {
    let values = receipts ?? []
    guard values.count <= maximumReceipts,
      Set(values.map(\.id)).count == values.count,
      values.allSatisfy({ receipt in
        UUID(uuidString: receipt.id)?.uuidString.lowercased() == receipt.id
          && receipt.sequence > 0
          && receipt.scopeHash.range(
            of: "^[a-f0-9]{64}$",
            options: .regularExpression
          ) != nil
          && expectedVersion(for: receipt.kind) == receipt.disclosureVersion
          && expectedScopeHash(for: receipt.kind) == receipt.scopeHash
      })
    else { return false }
    for kind in [
      CompanionWorkflowConsentKind.contactsDisclosure,
      .contactsReadOnly,
    ] {
      let rows = values.filter { $0.kind == kind }.sorted { $0.sequence < $1.sequence }
      for (index, row) in rows.enumerated() {
        guard row.sequence == UInt64(index + 1),
          row.supersedesReceiptId == (index == 0 ? nil : rows[index - 1].id)
        else { return false }
      }
    }
    return true
  }

  private static func recordDecision(
    receipts: inout [CompanionWorkflowConsentReceipt]?,
    kind: CompanionWorkflowConsentKind,
    decision: CompanionWorkflowConsentDecision,
    version: String,
    scopeHash: String,
    at now: Date,
    makeId: () -> String
  ) -> Bool {
    var updated = receipts ?? []
    guard isValid(updated) else { return false }
    if isCurrent(
      updated,
      kind: kind,
      decision: decision,
      version: version,
      scopeHash: scopeHash
    ) {
      return true
    }
    guard append(
      to: &updated,
      kind: kind,
      decision: decision,
      version: version,
      scopeHash: scopeHash,
      at: now,
      makeId: makeId
    ), isValid(updated) else { return false }
    receipts = updated
    return true
  }

  private static func append(
    to receipts: inout [CompanionWorkflowConsentReceipt],
    kind: CompanionWorkflowConsentKind,
    decision: CompanionWorkflowConsentDecision,
    version: String,
    scopeHash: String,
    at now: Date,
    makeId: () -> String
  ) -> Bool {
    if receipts.count >= maximumReceipts {
      receipts = [
        CompanionWorkflowConsentKind.contactsDisclosure,
        .contactsReadOnly,
      ].compactMap { retainedKind in
        receipts.filter { $0.kind == retainedKind }.max { $0.sequence < $1.sequence }
      }.map { retained in
        CompanionWorkflowConsentReceipt(
          id: retained.id,
          kind: retained.kind,
          decision: retained.decision,
          disclosureVersion: retained.disclosureVersion,
          scopeHash: retained.scopeHash,
          sequence: 1,
          supersedesReceiptId: nil,
          recordedAt: retained.recordedAt
        )
      }
    }
    guard receipts.count < maximumReceipts else { return false }
    let prior = receipts.filter { $0.kind == kind }.max { $0.sequence < $1.sequence }
    guard prior?.sequence != UInt64.max else { return false }
    let id = makeId()
    guard UUID(uuidString: id)?.uuidString.lowercased() == id,
      !receipts.contains(where: { $0.id == id })
    else { return false }
    receipts.append(
      CompanionWorkflowConsentReceipt(
        id: id,
        kind: kind,
        decision: decision,
        disclosureVersion: version,
        scopeHash: scopeHash,
        sequence: (prior?.sequence ?? 0) + 1,
        supersedesReceiptId: prior?.id,
        recordedAt: now
      ))
    return true
  }

  private static func isCurrent(
    _ receipts: [CompanionWorkflowConsentReceipt],
    kind: CompanionWorkflowConsentKind,
    decision: CompanionWorkflowConsentDecision,
    version: String,
    scopeHash: String
  ) -> Bool {
    guard let latest = receipts.filter({ $0.kind == kind }).max(by: {
      $0.sequence < $1.sequence
    }) else { return false }
    return latest.decision == decision && latest.disclosureVersion == version
      && latest.scopeHash == scopeHash
  }

  private static func expectedVersion(
    for kind: CompanionWorkflowConsentKind
  ) -> String {
    kind == .contactsDisclosure ? contactsDisclosureVersion : contactsScopeVersion
  }

  private static func expectedScopeHash(
    for kind: CompanionWorkflowConsentKind
  ) -> String {
    kind == .contactsDisclosure ? contactsDisclosureScopeHash : contactsReadOnlyScopeHash
  }

  private static func scopeHash(domain: String, values: [String]) -> String {
    var data = Data((domain + "\0").utf8)
    for value in values {
      data.append(Data("\(value.utf8.count):".utf8))
      data.append(Data(value.utf8))
      data.append(0)
    }
    return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
  }
}

/// Native-only account blocklist for canonical SMS destinations. Values remain inside the
/// complete-protection workflow store and are never projected to React Native, diagnostics, or
/// notifications. A sorted unique representation keeps persistence and review hashes stable.
enum IOSCompanionDestinationBlocklistPolicy {
  static let maximumDestinations = IOSPeopleCapacityPolicy.maximumPeople
  private static let canonicalPattern = try! NSRegularExpression(
    pattern: "^\\+[1-9][0-9]{1,14}$"
  )

  static func isCanonical(_ destination: String) -> Bool {
    let range = NSRange(destination.startIndex..<destination.endIndex, in: destination)
    return canonicalPattern.firstMatch(in: destination, range: range)?.range == range
  }

  static func normalized(_ values: [String]?) -> [String]? {
    let source = values ?? []
    guard source.count <= maximumDestinations, source.allSatisfy(isCanonical) else {
      return nil
    }
    return Array(Set(source)).sorted()
  }

  static func updated(
    blocked: Bool,
    destination: String,
    current: [String]?
  ) -> [String]? {
    guard isCanonical(destination), let normalized = normalized(current) else { return nil }
    var values = Set(normalized)
    if blocked {
      guard values.count < maximumDestinations || values.contains(destination) else { return nil }
      values.insert(destination)
    } else {
      values.remove(destination)
    }
    return values.sorted()
  }
}

/// Exact native destination material frozen into every iOS approval and review hash.
/// A regional raw number can resolve to a different E.164 value after the device region changes;
/// binding only the provider phone ID would silently carry approval to that new destination.
struct IOSCompanionApprovalDestinationBinding: Equatable {
  static let version = "ios-approved-e164-v1"

  let phoneId: String
  let e164: String
  let metadataRelease: String

  var hashComponents: [String] {
    [Self.version, phoneId, e164, metadataRelease]
  }

  static func resolve(
    selectedPhoneId: String?,
    phones: [IOSPeoplePrivatePhone]
  ) -> IOSCompanionApprovalDestinationBinding? {
    guard let selectedPhoneId,
      let e164 = phones.first(where: { $0.localId == selectedPhoneId })?.e164,
      IOSCompanionDestinationBlocklistPolicy.isCanonical(e164)
    else { return nil }
    return IOSCompanionApprovalDestinationBinding(
      phoneId: selectedPhoneId,
      e164: e164,
      metadataRelease: IOSPhoneNumberNormalizer.metadataRelease
    )
  }
}

enum CompanionWorkflowReviewKind: String, Codable {
  case activation
  case approval
  case enrollment
  case message
  case policy
  case privacy
  case todayOccurrence
}

/// A bounded, single-use CAS record. The hash covers the reviewed payload and
/// every material blocker; private values remain encrypted and native-only.
struct CompanionWorkflowReview: Codable {
  let handle: String
  let kind: CompanionWorkflowReviewKind
  let issuedForRevision: String
  let expiresAt: Date
  let blockerHash: String
  let contactIds: [String]
  let messageDraft: CompanionWorkflowMessageDraft?
  let policy: CompanionWorkflowPolicy?
  let privacyAction: String?
  let occurrenceId: String?
  var consumedAt: Date?
}

enum CompanionWorkflowOccurrencePhase: String, Codable {
  case reminderPlanned = "reminder-planned"
  case composerReady = "composer-ready"
  case composerOpened = "composer-opened"
  case dismissed
  case expired
}

struct CompanionWorkflowOccurrence: Codable {
  let occurrenceId: String
  let proposalId: String
  let contactId: String
  let civilDate: String
  var phase: CompanionWorkflowOccurrencePhase
  var updatedAt: Date
}

struct CompanionWorkflowActivity: Codable {
  let id: String
  let kind: String
  let reason: String?
  let occurredAt: Date
  let actionable: Bool
}

struct CompanionWorkflowPrivacyOperation: Codable {
  let id: String
  let action: String
  var phase: String
  var reason: String?
  var updatedAt: Date
}

struct CompanionWorkflowState: Codable {
  let account: CompanionWorkflowAccount
  var configurationGeneration: UInt64
  var contacts: [CompanionWorkflowContact]
  // Optional preserves protected snapshots written before destination blocking existed.
  var blockedDestinations: [String]?
  // Optional preserves protected snapshots written before consent receipts existed.
  var consentReceipts: [CompanionWorkflowConsentReceipt]?
  var messageDraft: CompanionWorkflowMessageDraft?
  var policy: CompanionWorkflowPolicy?
  var desired: CompanionWorkflowDesired
  // Optional only for protected snapshots created before the onboarding
  // completion marker. A missing legacy value is conservatively incomplete
  // unless the durable desired state is currently reminders-on.
  var hasEverActivatedReminders: Bool?
  var reviews: [CompanionWorkflowReview]
  var occurrences: [CompanionWorkflowOccurrence]
  var activity: [CompanionWorkflowActivity]
  var activityClearedAt: Date?
  var privacyOperations: [CompanionWorkflowPrivacyOperation]
  var lastCoordinationSuccessAt: Date?

  static func empty(binding: IOSNativeGoogleAccountBinding) -> CompanionWorkflowState {
    CompanionWorkflowState(
      account: CompanionWorkflowAccount(binding: binding),
      configurationGeneration: 0,
      contacts: [],
      blockedDestinations: [],
      consentReceipts: [],
      messageDraft: nil,
      policy: nil,
      desired: .paused,
      hasEverActivatedReminders: false,
      reviews: [],
      occurrences: [],
      activity: [],
      activityClearedAt: nil,
      privacyOperations: [],
      lastCoordinationSuccessAt: nil
    )
  }
}

enum IOSCompanionPersistedDraftRecoveryResult: Equatable {
  case unchanged
  case removedInvalidReviews
  case revalidatedDraft
  case clearedInvalidDraft
  case unrecoverable
}

/// Repairs only template-derived workflow state after an authenticated legacy
/// snapshot contains placeholder structure that the current product cannot
/// safely render. Account ownership, contact choices/enrollment, policy,
/// privacy operations, history, and terminal composer safety remain intact.
enum IOSCompanionPersistedDraftRecovery {
  static func apply(
    to workflow: inout CompanionWorkflowState,
    now: Date,
    validatorVersion: String? = nil,
    contentIssueCodes: ((String, String) -> [String])? = nil
  ) -> IOSCompanionPersistedDraftRecoveryResult {
    if let draft = workflow.messageDraft {
      let structurallyValid = IOSCompanionMessagePlaceholderPolicy.isValid(
        text: draft.text,
        placeholderMode: draft.placeholderMode
      )
      let semanticallyValid = contentIssueCodes?(
        draft.text,
        draft.language
      ).isEmpty ?? true
      if !structurallyValid || !semanticallyValid {
        guard prepareTemplatePolicyMigration(workflow: &workflow, now: now) else {
          return .unrecoverable
        }
        workflow.messageDraft = nil
        workflow.desired = .paused
        return .clearedInvalidDraft
      }

      if let validatorVersion,
        draft.provenance?.validatorVersion != validatorVersion
      {
        guard prepareTemplatePolicyMigration(workflow: &workflow, now: now) else {
          return .unrecoverable
        }
        let existing = draft.provenance
        workflow.messageDraft = CompanionWorkflowMessageDraft(
          language: draft.language,
          tone: draft.tone,
          placeholderMode: draft.placeholderMode,
          text: draft.text,
          requestedSegmentCap: draft.requestedSegmentCap,
          provenance: CompanionWorkflowMessageProvenance(
            source: existing?.source ?? "USER",
            modelIdentifier: existing?.modelIdentifier,
            promptPolicyVersion: existing?.promptPolicyVersion,
            validatorVersion: validatorVersion
          )
        )
        return .revalidatedDraft
      }
    }

    let previousReviewCount = workflow.reviews.count
    workflow.reviews.removeAll { review in
      guard let draft = review.messageDraft else { return false }
      if !IOSCompanionMessagePlaceholderPolicy.isValid(
        text: draft.text,
        placeholderMode: draft.placeholderMode
      ) { return true }
      if contentIssueCodes?(draft.text, draft.language).isEmpty == false {
        return true
      }
      return validatorVersion.map {
        draft.provenance?.validatorVersion != $0
      } ?? false
    }
    return workflow.reviews.count == previousReviewCount
      ? .unchanged : .removedInvalidReviews
  }

  private static func prepareTemplatePolicyMigration(
    workflow: inout CompanionWorkflowState,
    now: Date
  ) -> Bool {
    guard workflow.configurationGeneration < UInt64.max else { return false }
    workflow.configurationGeneration += 1
    workflow.reviews.removeAll()
    workflow.occurrences.removeAll()
    for index in workflow.contacts.indices
    where workflow.contacts[index].approvalHash != nil
      || workflow.contacts[index].approvedAt != nil
    {
      var reasons = Set(workflow.contacts[index].approvalInvalidationReasons)
      reasons.insert("template-changed")
      let sorted = reasons.sorted()
      workflow.contacts[index].approvalInvalidationReasons =
        sorted.count <= 16
        ? sorted
        : Array(sorted.filter { $0 != "template-changed" }.prefix(15))
          + ["template-changed"]
      workflow.contacts[index].updatedAt = now
    }
    return true
  }
}

struct CompanionWorkflowReadSnapshot {
  let revision: String
  let workflow: CompanionWorkflowState?
  let proposals: [CompanionApprovedProposal]
  let composerRecords: [CompanionComposerRecord]
  let reminderPlans: [CompanionReminderPlan]
}
