import Foundation

// Minimal companions for the standalone model contract. The production target
// supplies these from CompanionPeopleStore and CompanionProtectedStore.
struct IOSNativeGoogleAccountBinding {
  let googleSubject: String
  let firebaseUID: String
  let displayEmail: String
  let displayName: String?
  let accountGeneration: String
}

struct CompanionApprovedProposal {}
struct CompanionComposerRecord {}
struct CompanionReminderPlan {}

@main
enum CompanionPersistedDraftRecoveryTests {
  static func main() {
    let originalDate = Date(timeIntervalSince1970: 1_700_000_000)
    let recoveryDate = Date(timeIntervalSince1970: 1_700_000_600)
    let binding = IOSNativeGoogleAccountBinding(
      googleSubject: "1234567890",
      firebaseUID: "firebase-user-1",
      displayEmail: "person@example.com",
      displayName: "Example Person",
      accountGeneration: "11111111-1111-4111-8111-111111111111"
    )
    let invalidDraft = CompanionWorkflowMessageDraft(
      language: "en",
      tone: "warm",
      placeholderMode: "given-name",
      text: "Happy birthday, {firstName} {firstName}!",
      requestedSegmentCap: 1,
      provenance: nil
    )
    let policy = CompanionWorkflowPolicy(
      primaryStart: "09:00",
      primaryEnd: "10:00",
      graceEnd: nil,
      legacyAndroidDailyCap: 1
    )
    var workflow = CompanionWorkflowState.empty(binding: binding)
    workflow.configurationGeneration = 7
    workflow.contacts = [CompanionWorkflowContact(
      contactId: "contact-1",
      selectedPhoneId: "phone-1",
      selectedBirthdayId: "birthday-1",
      leapPolicy: nil,
      enrollment: .enabled,
      materialRevision: 4,
      approvalHash: String(repeating: "a", count: 64),
      approvedAt: originalDate,
      approvalInvalidationReasons: [],
      lastOutcomeLabel: nil,
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
      occurredAt: originalDate
    )]
    workflow.privacyOperations = [CompanionWorkflowPrivacyOperation(
      id: "privacy-1",
      action: "wipe-local-data",
      phase: "complete",
      reason: nil,
      updatedAt: originalDate
    )]

    let result = IOSCompanionPersistedDraftRecovery.apply(
      to: &workflow,
      now: recoveryDate
    )
    guard result == .clearedInvalidDraft,
      workflow.account.googleSubject == binding.googleSubject,
      workflow.account.firebaseUID == binding.firebaseUID,
      workflow.account.accountGeneration == binding.accountGeneration,
      workflow.configurationGeneration == 8,
      workflow.messageDraft == nil,
      workflow.desired == .paused,
      workflow.reviews.isEmpty,
      workflow.occurrences.isEmpty,
      workflow.policy == policy,
      workflow.hasEverActivatedReminders == true,
      workflow.activity.first?.id == "activity-1",
      workflow.privacyOperations.first?.id == "privacy-1",
      workflow.contacts.first?.selectedPhoneId == "phone-1",
      workflow.contacts.first?.selectedBirthdayId == "birthday-1",
      workflow.contacts.first?.approvalHash == String(repeating: "a", count: 64),
      workflow.contacts.first?.approvalInvalidationReasons == ["template-changed"],
      workflow.contacts.first?.updatedAt == recoveryDate
    else {
      fatalError("unsafe persisted draft recovery deleted durable user data or stayed active")
    }

    let validGeneric = CompanionWorkflowMessageDraft(
      language: "en",
      tone: "simple",
      placeholderMode: "generic",
      text: "Happy birthday!",
      requestedSegmentCap: 1,
      provenance: nil
    )
    var genericWorkflow = CompanionWorkflowState.empty(binding: binding)
    genericWorkflow.messageDraft = validGeneric
    guard IOSCompanionPersistedDraftRecovery.apply(
      to: &genericWorkflow,
      now: recoveryDate
    ) == .unchanged,
      genericWorkflow.messageDraft == validGeneric
    else {
      fatalError("valid generic persisted draft was unnecessarily recovered")
    }

    var legacyWorkflow = CompanionWorkflowState.empty(binding: binding)
    legacyWorkflow.configurationGeneration = 2
    legacyWorkflow.messageDraft = CompanionWorkflowMessageDraft(
      language: "en",
      tone: "simple",
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
    legacyWorkflow.contacts = [CompanionWorkflowContact(
      contactId: "contact-2",
      selectedPhoneId: "phone-2",
      selectedBirthdayId: "birthday-2",
      leapPolicy: nil,
      enrollment: .enabled,
      materialRevision: 1,
      approvalHash: String(repeating: "c", count: 64),
      approvedAt: originalDate,
      approvalInvalidationReasons: [],
      lastOutcomeLabel: nil,
      updatedAt: originalDate
    )]
    legacyWorkflow.occurrences = [CompanionWorkflowOccurrence(
      occurrenceId: "occurrence-2",
      proposalId: "proposal-2",
      contactId: "contact-2",
      civilDate: "2026-08-01",
      phase: .reminderPlanned,
      updatedAt: originalDate
    )]
    guard IOSCompanionPersistedDraftRecovery.apply(
      to: &legacyWorkflow,
      now: recoveryDate,
      validatorVersion: "sms-template-validator-v2",
      contentIssueCodes: { _, _ in [] }
    ) == .revalidatedDraft,
      legacyWorkflow.configurationGeneration == 3,
      legacyWorkflow.messageDraft?.provenance?.validatorVersion
        == "sms-template-validator-v2",
      legacyWorkflow.contacts.first?.approvalInvalidationReasons
        == ["template-changed"],
      legacyWorkflow.occurrences.isEmpty,
      legacyWorkflow.reviews.isEmpty
    else {
      fatalError("safe policy upgrade reused legacy approvals or proposals")
    }
  }
}
