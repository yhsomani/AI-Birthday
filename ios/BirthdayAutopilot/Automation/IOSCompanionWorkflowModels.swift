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
  let dailyCap: Int
}

enum CompanionWorkflowDesired: String, Codable {
  case remindersOn = "composer-reminders-on"
  case paused
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
  var messageDraft: CompanionWorkflowMessageDraft?
  var policy: CompanionWorkflowPolicy?
  var desired: CompanionWorkflowDesired
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
      messageDraft: nil,
      policy: nil,
      desired: .paused,
      reviews: [],
      occurrences: [],
      activity: [],
      activityClearedAt: nil,
      privacyOperations: [],
      lastCoordinationSuccessAt: nil
    )
  }
}

struct CompanionWorkflowReadSnapshot {
  let revision: String
  let workflow: CompanionWorkflowState?
  let proposals: [CompanionApprovedProposal]
  let composerRecords: [CompanionComposerRecord]
  let reminderPlans: [CompanionReminderPlan]
}
