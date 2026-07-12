import CoreFoundation
import CryptoKit
import Foundation

enum IOSCompanionWorkflowEngineResult {
  case success(Any)
  case failure([String: Any])
}

private struct IOSCompanionEffectiveContact {
  let safe: IOSPeopleSafeContact
  let privateValue: IOSPeoplePrivateContact
  let configuration: CompanionWorkflowContact?
  let selectedPhoneId: String?
  let selectedBirthdayId: String?
  let selectedPhone: IOSPeoplePrivatePhone?
  let selectedBirthday: IOSPeoplePrivateBirthday?
  let readinessKind: String
  let readinessReasons: [String]
  let approvalKind: String
  let approvalReasons: [String]
}

@MainActor
final class IOSCompanionWorkflowEngine {
  static let shared = IOSCompanionWorkflowEngine()

  private static let maximumReviewCount = 32
  private static let reviewLifetime: TimeInterval = 5 * 60
  private static let planningDays = 400
  private static let maximumBatch = 50
  private static let deletionLocalWipeRecoveryReason =
    "deletion-local-wipe-recovery"
  private static let privacyActions: Set<String> = [
    "disconnect-contacts", "revoke-google-access", "sign-out-retain",
    "sign-out-wipe", "delete-account", "wipe-local-data",
    "clear-gemini-templates", "clear-activity",
  ]
  private static let activityKinds: Set<String> = [
    "reminder-scheduled", "composer-opened", "composer-cancelled",
    "composer-failed", "composer-outcome-unknown", "composer-reported-sent",
    "approval-invalidated", "coordination-blocked", "paused",
    "settings-changed", "sync",
  ]
  private static let isoFormatter: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter
  }()

  private let store = CompanionProtectedStore.shared
  private let peopleStore = CompanionPeopleStore.shared
  private let reminderCoordinator = CompanionReminderCoordinator.shared
  private let deletionClient = IOSAccountDeletionClient.shared
  private let contactResetClient = IOSContactDerivedResetClient.shared
  private let deletionReceiptStore = IOSAccountDeletionReceiptStore.shared
  private let deletionRecoveryStore = IOSAccountDeletionRecoveryStore.shared
  private let deletionCleanup = IOSAccountDeletionLocalCleanupCoordinator.shared
  private var calendar: Calendar { Calendar.autoupdatingCurrent }

  private init() {}

  func contactsProjection(
    request: [String: Any],
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    let contacts = effectiveContacts(status: status)
    switch request["kind"] as? String {
    case "list":
      guard Set(request.keys) == ["kind", "query"],
        let query = request["query"] as? [String: Any],
        Self.validPeopleQuery(query),
        let filter = query["filter"] as? String,
        let pageSize = Self.strictInteger(query["pageSize"], range: 1...50),
        let offset = Self.pageOffset(query["cursor"] as? String)
      else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      let search =
        (query["search"] as? String)?.trimmingCharacters(
          in: .whitespacesAndNewlines
        ).lowercased() ?? ""
      let filtered = contacts.filter { contact in
        let enrollment = contact.configuration?.enrollment ?? .off
        let matchesFilter: Bool
        switch filter {
        case "all": matchesFilter = enrollment != .excluded
        case "enabled": matchesFilter = enrollment == .enabled
        case "ready":
          matchesFilter =
            contact.readinessKind == "ready"
            && enrollment != .excluded
        case "needs-attention":
          matchesFilter =
            contact.readinessKind == "needs-attention"
            && enrollment != .excluded
        case "excluded": matchesFilter = enrollment == .excluded
        default: matchesFilter = false
        }
        return matchesFilter
          && (search.isEmpty
            || contact.safe.displayName.lowercased().contains(search))
      }
      guard offset <= filtered.count else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      let end = min(filtered.count, offset + pageSize)
      var page: [String: Any] = [
        "items": filtered[offset..<end].map(Self.contactSummary),
        "totalCount": filtered.count,
      ]
      if end < filtered.count { page["nextCursor"] = "page.\(end)" }
      return .success(page)
    case "detail":
      guard Set(request.keys) == ["contactId", "kind"],
        let contactId = request["contactId"] as? String,
        Self.validOpaque(contactId),
        let contact = contacts.first(where: { $0.safe.localId == contactId })
      else { return .failure(Self.temporarilyUnavailable("contacts-stale")) }
      return .success(contactDetail(contact))
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  func messagesProjection(
    request: [String: Any],
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard let workflow = status.workflow else {
      return .failure(Self.temporarilyUnavailable("account-reconnect-required"))
    }
    switch request["kind"] as? String {
    case "editor":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      guard let draft = workflow.messageDraft else {
        return .success(["kind": "not-configured"])
      }
      return .success(["kind": "configured", "draft": Self.messageDraftPayload(draft)])
    case "next-composer-proposal":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      guard workflow.desired == .remindersOn else {
        return .success(["kind": "none"])
      }
      let today = Self.localDate(Date(), calendar: calendar)
      let proposalById = Dictionary(
        uniqueKeysWithValues: status.proposals.map { ($0.proposalId, $0) }
      )
      let contacts = Dictionary(
        uniqueKeysWithValues: effectiveContacts(status: status).map {
          ($0.safe.localId, $0)
        }
      )
      guard
        let occurrence = workflow.occurrences
          .filter({ $0.civilDate == today })
          .sorted(by: { $0.occurrenceId < $1.occurrenceId })
          .first(where: { occurrence in
            guard let proposal = proposalById[occurrence.proposalId] else { return false }
            return proposal.state == .ready || proposal.state == .cancelled
              || proposal.state == .failed
          }), let contact = contacts[occurrence.contactId]
      else { return .success(["kind": "none"]) }
      return .success([
        "kind": "ready",
        "proposalId": occurrence.proposalId,
        "occurrenceId": occurrence.occurrenceId,
        "occurrenceDate": occurrence.civilDate,
        "recipient": contact.safe.displayName,
      ])
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  func approvalProjection(
    contactId: String,
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard Self.validOpaque(contactId),
      let contact = effectiveContacts(status: status).first(where: {
        $0.safe.localId == contactId
      })
    else { return .failure(Self.temporarilyUnavailable("contacts-stale")) }
    return .success(Self.approvalPayload(contact))
  }

  func policyEditorProjection(
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard let workflow = status.workflow else {
      return .failure(Self.temporarilyUnavailable("account-reconnect-required"))
    }
    guard let policy = workflow.policy else {
      return .success(["kind": "not-configured"])
    }
    let latePolicy: [String: Any] =
      policy.graceEnd.map {
        ["kind": "same-day-grace", "graceEnd": $0]
      } ?? ["kind": "none"]
    return .success([
      "kind": "configured",
      "draft": [
        "primaryStart": policy.primaryStart,
        "primaryEnd": policy.primaryEnd,
        "latePolicy": latePolicy,
        "dailyCap": policy.dailyCap,
      ],
    ])
  }

  func birthdayJobProjection(
    occurrenceId: String,
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard Self.validOpaque(occurrenceId),
      let occurrence = status.workflow?.occurrences.first(where: {
        $0.occurrenceId == occurrenceId
      })
    else { return .failure(Self.temporarilyUnavailable("contacts-stale")) }
    let record = status.composerRecords.last(where: { $0.occurrenceId == occurrenceId })
    let phase: String
    if let record {
      switch record.outcome {
      case .openCommitted, .presented, .reportedSent: phase = "composer-opened"
      case .cancelled, .failed: phase = "dismissed"
      case .outcomeUnknown: phase = "expired"
      }
    } else if occurrence.civilDate == Self.localDate(Date(), calendar: calendar) {
      phase = "composer-ready"
    } else {
      phase = "reminder-planned"
    }
    return .success([
      "platform": "ios",
      "occurrenceId": occurrence.occurrenceId,
      "occurrenceDate": occurrence.civilDate,
      "phase": phase,
      "updatedAt": Self.dateString(record?.resolvedAt ?? record?.openedAt ?? occurrence.updatedAt),
    ])
  }

  func activityProjection(
    request: [String: Any],
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard request["kind"] as? String == "list",
      Set(request.keys) == ["kind", "query"],
      let query = request["query"] as? [String: Any],
      Self.validActivityQuery(query),
      let pageSize = Self.strictInteger(query["pageSize"], range: 1...50),
      let offset = Self.pageOffset(query["cursor"] as? String)
    else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }

    var records =
      status.workflow?.activity.filter {
        Self.activityKinds.contains($0.kind)
      }.map { activity in
        Self.activityPayload(
          id: activity.id,
          kind: activity.kind,
          reason: activity.reason,
          occurredAt: activity.occurredAt,
          actionable: activity.actionable
        )
      } ?? []
    let activityCutoff = status.workflow?.activityClearedAt
    records.append(
      contentsOf: status.composerRecords.filter { record in
        guard let activityCutoff else { return true }
        return (record.resolvedAt ?? record.openedAt) > activityCutoff
      }.flatMap { record -> [[String: Any]] in
        var values = [
          Self.activityPayload(
            id: "composer.\(record.operationId).opened",
            kind: "composer-opened",
            reason: nil,
            occurredAt: record.openedAt,
            actionable: false
          )
        ]
        let terminalKind: String?
        switch record.outcome {
        case .openCommitted, .presented: terminalKind = nil
        case .cancelled: terminalKind = "composer-cancelled"
        case .failed: terminalKind = "composer-failed"
        case .outcomeUnknown: terminalKind = "composer-outcome-unknown"
        case .reportedSent: terminalKind = "composer-reported-sent"
        }
        if let terminalKind {
          values.append(
            Self.activityPayload(
              id: "composer.\(record.operationId).outcome",
              kind: terminalKind,
              reason: nil,
              occurredAt: record.resolvedAt ?? record.openedAt,
              actionable: record.outcome == .cancelled || record.outcome == .failed
            ))
        }
        return values
      })
    records.sort {
      (($0["occurredAt"] as? String) ?? "") > (($1["occurredAt"] as? String) ?? "")
    }
    guard offset <= records.count else {
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
    let end = min(records.count, offset + pageSize)
    var payload: [String: Any] = ["items": Array(records[offset..<end])]
    if end < records.count { payload["nextCursor"] = "page.\(end)" }
    return .success(payload)
  }

  func privacyInventory(status: CompanionProjectionStatus) -> [String: Any] {
    let people = peopleStore.projection()
    let effective = effectiveContacts(status: status)
    let workflow = status.workflow
    var result: [String: Any] = [
      "localContactCount": people.contacts.count,
      "enabledRecipientCount": effective.filter {
        $0.configuration?.enrollment == .enabled
      }.count,
      "approvalCount": effective.filter { $0.approvalKind == "valid" }.count,
      "activityCount": (workflow?.activity.count ?? 0)
        + status.composerRecords.filter {
          guard let cutoff = workflow?.activityClearedAt else { return true }
          return ($0.resolvedAt ?? $0.openedAt) > cutoff
        }.reduce(0) { count, record in
          count + (record.outcome == .openCommitted || record.outcome == .presented ? 1 : 2)
        },
      "templateCount": workflow?.messageDraft == nil ? 0 : 1,
      "localStorageBytes": people.localStorageBytes + status.localStorageBytes,
      "consentVersions": [],
      "externalSmsCopiesNotControlled": true,
    ]
    if case .fresh(let completedAt, _) = people.sync {
      result["lastContactsSyncAt"] = Self.dateString(completedAt)
    }
    return result
  }

  func homeMetrics(status: CompanionProjectionStatus) -> [String: Any] {
    let contacts = effectiveContacts(status: status)
    let enabled = contacts.filter { $0.configuration?.enrollment == .enabled }
    let now = Date()
    let today = Self.localDate(now, calendar: calendar)
    // Inclusive range: today plus the following six civil dates is seven days.
    let sevenDays = calendar.date(byAdding: .day, value: 6, to: now) ?? now
    let seven = Self.localDate(sevenDays, calendar: calendar)
    let occurrences = status.workflow?.occurrences ?? []
    var result: [String: Any] = [
      "enabled": enabled.count,
      "needsAttention": contacts.filter { $0.readinessKind == "needs-attention" }.count,
      "unavailable": contacts.filter { $0.readinessKind == "unavailable" }.count,
      "today": occurrences.filter { $0.civilDate == today }.count,
      "nextSevenDays": occurrences.filter {
        $0.civilDate >= today && $0.civilDate <= seven
      }.count,
    ]
    if let next = occurrences.filter({ $0.civilDate >= today }).sorted(by: {
      if $0.civilDate != $1.civilDate { return $0.civilDate < $1.civilDate }
      return $0.occurrenceId < $1.occurrenceId
    }).first, let contact = contacts.first(where: { $0.safe.localId == next.contactId }),
      let masked = contact.safe.phoneChoices.first(where: {
        $0.localId == contact.selectedPhoneId
      })?.maskedDisplay
    {
      result["next"] = [
        "occurrenceId": next.occurrenceId,
        "recipient": contact.safe.displayName,
        "localDate": next.civilDate,
        "windowLabel": status.workflow?.policy.map(Self.windowLabel) ?? "Reminder window",
        "maskedPhone": masked,
      ]
    }
    return result
  }

  func automationPayload(
    status: CompanionProjectionStatus,
    readiness: [String: Any]
  ) -> [String: Any] {
    guard let workflow = status.workflow else {
      return [
        "platform": "ios", "desired": "paused", "effective": "not-configured",
        "readiness": readiness,
      ]
    }
    let effective: String
    if workflow.messageDraft == nil || workflow.policy == nil {
      effective = "not-configured"
    } else if workflow.desired == .paused {
      effective = "paused"
    } else if let composer = readiness["composer"] as? [String: Any],
      composer["kind"] as? String == "allowed",
      status.reminderHorizonState == .full,
      effectiveContacts(status: status).contains(where: {
        $0.configuration?.enrollment == .enabled && $0.approvalKind == "valid"
      })
    {
      effective = "ready"
    } else {
      effective = "action-required"
    }
    return [
      "platform": "ios",
      "desired": workflow.desired.rawValue,
      "effective": effective,
      "readiness": readiness,
    ]
  }

  private func effectiveContacts(
    status: CompanionProjectionStatus
  ) -> [IOSCompanionEffectiveContact] {
    let privateById = Dictionary(
      uniqueKeysWithValues: peopleStore.privateContacts().map { ($0.localId, $0) }
    )
    let configById = Dictionary(
      uniqueKeysWithValues: (status.workflow?.contacts ?? []).map { ($0.contactId, $0) }
    )
    var values = peopleStore.projection().contacts.compactMap { safe in
      guard let privateValue = privateById[safe.localId] else { return nil }
      let configuration = configById[safe.localId]
      let selectablePhones = privateValue.phones.filter { $0.e164 != nil }
      let selectedPhoneId =
        configuration?.selectedPhoneId.flatMap { selected in
          selectablePhones.contains(where: { $0.localId == selected }) ? selected : nil
        } ?? (selectablePhones.count == 1 ? selectablePhones[0].localId : nil)
      let selectedBirthdayId =
        configuration?.selectedBirthdayId.flatMap { selected in
          privateValue.birthdays.contains(where: { $0.localId == selected }) ? selected : nil
        } ?? (privateValue.birthdays.count == 1 ? privateValue.birthdays[0].localId : nil)
      let selectedPhone = selectablePhones.first { $0.localId == selectedPhoneId }
      let selectedBirthday = privateValue.birthdays.first { $0.localId == selectedBirthdayId }
      var reasons = Set(safe.readinessReasons)
      // A missing safe given name is material only for a personalized draft.
      // Enrollment remains possible before a draft is chosen and generic
      // templates never require a name substitution.
      if status.workflow?.messageDraft?.placeholderMode != "given-name" {
        reasons.remove("safe-given-name-missing")
      }
      if selectedPhone != nil {
        reasons.remove("phone-choice-required")
        reasons.remove("phone-ambiguous-region")
        reasons.remove("phone-invalid")
      }
      if selectedBirthday != nil {
        reasons.remove("birthday-choice-required")
        reasons.remove("birthday-conflict")
      }
      if selectedBirthday?.month == 2, selectedBirthday?.day == 29,
        configuration?.leapPolicy == nil
      {
        reasons.insert("leap-policy-required")
      }
      let materialMatches = configuration?.materialRevision == privateValue.materialRevision
      let approvalHash = configuration.flatMap { config in
        Self.approvalHash(
          contact: privateValue,
          configuration: config,
          message: status.workflow?.messageDraft,
          policy: status.workflow?.policy
        )
      }
      let approvalKind: String
      var approvalReasons = configuration?.approvalInvalidationReasons ?? []
      if configuration?.approvalHash == nil || configuration?.approvedAt == nil {
        approvalKind = "missing"
      } else if !materialMatches || configuration?.approvalHash != approvalHash {
        approvalKind = "invalidated"
        if approvalReasons.isEmpty { approvalReasons = ["name-changed"] }
      } else if !approvalReasons.isEmpty {
        approvalKind = "invalidated"
      } else {
        approvalKind = "valid"
      }
      if let enrollment = configuration?.enrollment,
        enrollment == .enabled || enrollment == .paused,
        approvalKind != "valid"
      {
        reasons.insert("approval-invalid")
      }
      let kind =
        privateValue.deleted
        ? "unavailable"
        : (reasons.isEmpty ? "ready" : "needs-attention")
      return IOSCompanionEffectiveContact(
        safe: safe,
        privateValue: privateValue,
        configuration: configuration,
        selectedPhoneId: selectedPhoneId,
        selectedBirthdayId: selectedBirthdayId,
        selectedPhone: selectedPhone,
        selectedBirthday: selectedBirthday,
        readinessKind: kind,
        readinessReasons: Array(reasons).sorted(),
        approvalKind: approvalKind,
        approvalReasons: Array(Set(approvalReasons)).sorted()
      )
    }
    var destinationCounts: [String: Int] = [:]
    for value in values where value.configuration?.enrollment != .excluded {
      if let destination = value.selectedPhone?.e164 {
        destinationCounts[destination, default: 0] += 1
      }
    }
    values = values.map { value in
      guard let destination = value.selectedPhone?.e164,
        (destinationCounts[destination] ?? 0) > 1
      else { return value }
      var reasons = Set(value.readinessReasons)
      reasons.insert("duplicate-destination")
      return IOSCompanionEffectiveContact(
        safe: value.safe,
        privateValue: value.privateValue,
        configuration: value.configuration,
        selectedPhoneId: value.selectedPhoneId,
        selectedBirthdayId: value.selectedBirthdayId,
        selectedPhone: value.selectedPhone,
        selectedBirthday: value.selectedBirthday,
        readinessKind: value.privateValue.deleted ? "unavailable" : "needs-attention",
        readinessReasons: Array(reasons).sorted(),
        approvalKind: value.approvalKind,
        approvalReasons: value.approvalReasons
      )
    }
    return values.sorted {
      $0.safe.displayName.localizedCaseInsensitiveCompare($1.safe.displayName)
        == .orderedAscending
    }
  }

  private func contactDetail(_ contact: IOSCompanionEffectiveContact) -> [String: Any] {
    var result: [String: Any] = [
      "summary": Self.contactSummary(contact),
      "phoneChoices": contact.safe.phoneChoices.map { choice in
        var value: [String: Any] = [
          "id": choice.localId, "maskedDisplay": choice.maskedDisplay,
          "sourceLabel": choice.sourceLabel, "selectable": choice.selectable,
        ]
        if let issue = choice.issue { value["issue"] = issue }
        return value
      },
      "birthdayChoices": contact.safe.birthdayChoices.map { choice in
        var value: [String: Any] = [
          "id": choice.localId, "displayLabel": choice.displayLabel,
          "hasYear": choice.hasYear, "selectable": choice.selectable,
        ]
        if let issue = choice.issue { value["issue"] = issue }
        return value
      },
    ]
    if let id = contact.selectedPhoneId { result["selectedPhoneId"] = id }
    if let id = contact.selectedBirthdayId { result["selectedBirthdayId"] = id }
    if let next = nextOccurrence(
      birthday: contact.selectedBirthday,
      leapPolicy: contact.configuration?.leapPolicy,
      from: Date()
    ) {
      result["nextOccurrenceLabel"] = "Next: \(Self.localDate(next, calendar: calendar))"
    }
    if let label = contact.configuration?.lastOutcomeLabel {
      result["lastOutcomeLabel"] = label
    }
    return result
  }

  private static func contactSummary(
    _ contact: IOSCompanionEffectiveContact
  ) -> [String: Any] {
    let readiness: [String: Any] =
      contact.readinessKind == "ready"
      ? ["kind": "ready"]
      : ["kind": contact.readinessKind, "reasons": contact.readinessReasons]
    var enrollment: [String: Any]
    switch contact.configuration?.enrollment ?? .off {
    case .off:
      enrollment = ["kind": "off"]
    case .excluded:
      enrollment = ["kind": "excluded", "reason": "policy-suspended"]
    case .enabled:
      enrollment = ["kind": "enabled", "approval": approvalPayload(contact)]
    case .paused:
      enrollment = [
        "kind": "paused", "reason": "policy-suspended",
        "approval": approvalPayload(contact),
      ]
    }
    var result: [String: Any] = [
      "id": contact.safe.localId,
      "displayName": contact.safe.displayName,
      "readiness": readiness,
      "enrollment": enrollment,
    ]
    if let selected = contact.safe.phoneChoices.first(where: {
      $0.localId == contact.selectedPhoneId
    }) {
      result["maskedPhone"] = selected.maskedDisplay
    }
    if contact.selectedBirthday != nil { result["birthdayLabel"] = "Birthday selected" }
    return result
  }

  private static func approvalPayload(
    _ contact: IOSCompanionEffectiveContact
  ) -> [String: Any] {
    switch contact.approvalKind {
    case "valid":
      return [
        "kind": "valid",
        "approvedAt": dateString(contact.configuration?.approvedAt ?? Date()),
      ]
    case "invalidated":
      let allowed = Set([
        "phone-changed", "birthday-changed", "name-changed", "template-changed",
        "placeholder-semantics-changed", "window-changed", "late-policy-changed",
        "segment-plan-changed", "disclosure-changed",
      ])
      let reasons = contact.approvalReasons.filter { allowed.contains($0) }
      return ["kind": "invalidated", "reasons": reasons.isEmpty ? ["name-changed"] : reasons]
    default:
      return ["kind": "missing"]
    }
  }

  func execute(
    intent: String,
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard status.workflow?.account.matches(binding) == true else {
      completion(.failure(Self.temporarilyUnavailable("account-reconnect-required")))
      return
    }
    switch intent {
    case "choose-phone":
      choosePhone(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "choose-birthday":
      chooseBirthday(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "prepare-enrollment-review":
      prepareEnrollment(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "confirm-enrollment":
      confirmEnrollment(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "pause-recipient", "exclude-recipient", "restore-recipient":
      mutateRecipient(
        intent: intent, payload: payload, binding: binding,
        expectedRevision: expectedRevision, completion: completion
      )
    case "preview-message":
      previewMessage(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "save-message":
      saveMessage(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "preview-policy":
      previewPolicy(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "save-policy":
      savePolicy(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        readiness: readiness, completion: completion
      )
    case "prepare-approvals":
      prepareApprovals(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "confirm-approvals":
      confirmApprovals(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        readiness: readiness, completion: completion
      )
    case "prepare-activation", "prepare-resume":
      prepareActivation(
        binding: binding, revision: status.revision, status: status,
        completion: completion
      )
    case "activate", "resume":
      confirmActivation(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        readiness: readiness, completion: completion
      )
    case "pause-all":
      pauseAll(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        readiness: readiness, completion: completion
      )
    case "prepare-privacy-action":
      preparePrivacy(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "confirm-privacy-action":
      confirmPrivacy(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        completion: completion
      )
    case "resume-lifecycle-operation":
      resumePrivacyOperation(
        payload: payload, binding: binding, expectedRevision: expectedRevision,
        status: status, completion: completion
      )
    case "generate-suggestions":
      Task { @MainActor in
        let projection = await IOSGeminiSuggestionGateway.shared.generate(request: payload)
        completion(.success(projection))
      }
    default:
      completion(.failure(Self.unsupported("platform-composer-only")))
    }
  }

  private func choosePhone(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactId", "expectedRevision", "phoneId"],
      let contactId = payload["contactId"] as? String,
      let phoneId = payload["phoneId"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      Self.validOpaque(contactId), Self.validOpaque(phoneId),
      let contact = peopleStore.privateContact(localId: contactId), !contact.deleted,
      contact.phones.contains(where: { $0.localId == phoneId && $0.e164 != nil })
    else {
      completion(.failure(Self.validation([["field": "phone", "code": "phone-invalid"]])))
      return
    }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        var configuration = Self.contactConfiguration(contact, in: workflow)
        let changed = configuration.selectedPhoneId != phoneId
        configuration.selectedPhoneId = phoneId
        configuration.materialRevision = contact.materialRevision
        configuration.updatedAt = Date()
        if changed { Self.invalidateApproval(&configuration, reason: "phone-changed") }
        Self.upsert(configuration, in: &workflow)
        if changed { Self.bumpConfiguration(&workflow, activityKind: "settings-changed") }
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            guard
              let contact = self.effectiveContacts(status: status).first(where: {
                $0.safe.localId == contactId
              })
            else { return Self.temporarilyUnavailable("contacts-stale") }
            return self.contactDetail(contact)
          },
          completion: completion
        )
      }
    )
  }

  private func chooseBirthday(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    let allowedKeys: Set<String> =
      payload["leapPolicy"] == nil
      ? ["birthdayId", "contactId", "expectedRevision"]
      : ["birthdayId", "contactId", "expectedRevision", "leapPolicy"]
    guard Set(payload.keys) == allowedKeys,
      let contactId = payload["contactId"] as? String,
      let birthdayId = payload["birthdayId"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      Self.validOpaque(contactId), Self.validOpaque(birthdayId),
      let contact = peopleStore.privateContact(localId: contactId), !contact.deleted,
      let birthday = contact.birthdays.first(where: { $0.localId == birthdayId })
    else {
      completion(.failure(Self.validation([["field": "birthday", "code": "birthday-missing"]])))
      return
    }
    let leapPolicy = payload["leapPolicy"] as? String
    guard leapPolicy.map({ ["feb-28", "mar-01", "skip"].contains($0) }) ?? true,
      birthday.month != 2 || birthday.day != 29 || leapPolicy != nil
    else {
      completion(
        .failure(Self.validation([["field": "birthday", "code": "leap-policy-required"]])))
      return
    }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        var configuration = Self.contactConfiguration(contact, in: workflow)
        let changed =
          configuration.selectedBirthdayId != birthdayId
          || configuration.leapPolicy != leapPolicy
        configuration.selectedBirthdayId = birthdayId
        configuration.leapPolicy = leapPolicy
        configuration.materialRevision = contact.materialRevision
        configuration.updatedAt = Date()
        if changed { Self.invalidateApproval(&configuration, reason: "birthday-changed") }
        Self.upsert(configuration, in: &workflow)
        if changed { Self.bumpConfiguration(&workflow, activityKind: "settings-changed") }
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            guard
              let contact = self.effectiveContacts(status: status).first(where: {
                $0.safe.localId == contactId
              })
            else { return Self.temporarilyUnavailable("contacts-stale") }
            return self.contactDetail(contact)
          },
          completion: completion
        )
      }
    )
  }

  private func prepareEnrollment(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactIds", "expectedRevision"],
      let rawIds = payload["contactIds"] as? [Any],
      let ids = Self.validContactIds(rawIds), !ids.isEmpty,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let byId = Dictionary(
      uniqueKeysWithValues: effectiveContacts(status: status).map {
        ($0.safe.localId, $0)
      })
    guard ids.allSatisfy({ byId[$0] != nil }) else {
      return completion(.failure(Self.temporarilyUnavailable("contacts-stale")))
    }
    let recipients = ids.compactMap { byId[$0] }
    let ready = recipients.filter { $0.readinessKind == "ready" }
    let readyIds = ready.map { $0.safe.localId }
    let blocker = Self.reviewHash(
      kind: "enrollment", contactIds: readyIds,
      workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        Self.installReview(
          CompanionWorkflowReview(
            handle: UUID().uuidString.lowercased(),
            kind: .enrollment,
            issuedForRevision: committedRevision,
            expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
            blockerHash: blocker,
            contactIds: readyIds,
            messageDraft: nil,
            policy: nil,
            privacyAction: nil,
            occurrenceId: nil,
            consumedAt: nil
          ),
          in: &workflow
        )
        return workflow.reviews.last!.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          completion(
            .success([
              "handle": handle,
              "recipients": recipients.map(Self.contactSummary),
              "readyCount": ready.count,
              "attentionCount": recipients.count - ready.count,
              "explicitConfirmationRequired": true,
            ]))
        }
      }
    )
  }

  private func confirmEnrollment(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String, Self.validOpaque(handle),
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        let contacts = peopleStore.privateContacts()
        guard
          let reviewIndex = Self.reviewIndex(
            handle: handle, kind: .enrollment, revision: revision, workflow: workflow
          )
        else { throw CompanionStoreError.invalidReview }
        let review = workflow.reviews[reviewIndex]
        guard
          review.blockerHash
            == Self.reviewHash(
              kind: "enrollment", contactIds: review.contactIds,
              workflow: workflow, contacts: contacts
            )
        else { throw CompanionStoreError.staleMaterial }
        let byId = Dictionary(uniqueKeysWithValues: contacts.map { ($0.localId, $0) })
        var changed: [String] = []
        for id in review.contactIds {
          guard let contact = byId[id], !contact.deleted,
            let phoneId = Self.effectivePhoneId(contact, workflow: workflow),
            let birthdayId = Self.effectiveBirthdayId(contact, workflow: workflow),
            let phone = contact.phones.first(where: {
              $0.localId == phoneId && $0.e164 != nil
            }), phone.e164 != nil,
            let birthday = contact.birthdays.first(where: { $0.localId == birthdayId })
          else { continue }
          var configuration = Self.contactConfiguration(contact, in: workflow)
          if birthday.month == 2 && birthday.day == 29 && configuration.leapPolicy == nil {
            continue
          }
          configuration.selectedPhoneId = phoneId
          configuration.selectedBirthdayId = birthdayId
          configuration.materialRevision = contact.materialRevision
          configuration.enrollment = .enabled
          configuration.updatedAt = Date()
          Self.upsert(configuration, in: &workflow)
          changed.append(id)
        }
        guard !changed.isEmpty else { throw CompanionStoreError.staleMaterial }
        workflow.reviews[reviewIndex].consumedAt = Date()
        Self.bumpConfiguration(&workflow, activityKind: "settings-changed")
        return changed
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { _, changed in
            ["changedContactIds": changed, "invalidatedApprovalCount": 0]
          },
          completion: completion
        )
      }
    )
  }

  private func mutateRecipient(
    intent: String,
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactId", "expectedRevision"],
      let contactId = payload["contactId"] as? String, Self.validOpaque(contactId),
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let contact = peopleStore.privateContact(localId: contactId)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        var configuration = Self.contactConfiguration(contact, in: workflow)
        let previous = configuration.enrollment
        switch intent {
        case "pause-recipient":
          guard previous == .enabled else { throw CompanionStoreError.invalidWorkflowState }
          configuration.enrollment = .paused
        case "exclude-recipient":
          configuration.enrollment = .excluded
          Self.invalidateApproval(&configuration, reason: "disclosure-changed")
        case "restore-recipient":
          guard previous == .paused || previous == .excluded else {
            throw CompanionStoreError.invalidWorkflowState
          }
          configuration.enrollment = configuration.approvalHash == nil ? .off : .enabled
        default:
          throw CompanionStoreError.invalidWorkflowState
        }
        configuration.updatedAt = Date()
        Self.upsert(configuration, in: &workflow)
        Self.bumpConfiguration(
          &workflow,
          activityKind: intent == "pause-recipient" ? "paused" : "settings-changed"
        )
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { _ in
            [
              "changedContactIds": [contactId],
              "invalidatedApprovalCount": intent == "exclude-recipient" ? 1 : 0,
            ]
          },
          completion: completion
        )
      }
    )
  }

  private func previewMessage(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["draft", "expectedRevision"],
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let rawDraft = payload["draft"] as? [String: Any]
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let parsed = Self.parseMessageDraft(rawDraft)
    guard parsed.issues.isEmpty, let baseDraft = parsed.draft else {
      completion(
        .success([
          "kind": "invalid",
          "issues": parsed.issues,
          "affectedRecipientCount": Self.affectedContacts(status: status).count,
        ]))
      return
    }
    let draft = Self.draftWithProvenance(baseDraft)
    let affected = Self.affectedContacts(status: status)
    let rendered = affected.compactMap { contact -> (IOSCompanionEffectiveContact, String)? in
      guard let text = Self.render(draft: draft, contact: contact.privateValue) else {
        return nil
      }
      return (contact, text)
    }
    let estimates = rendered.map { Self.smsEstimate($0.1) }
    let maximumSegments = estimates.map(\.segments).max() ?? 1
    guard maximumSegments <= draft.requestedSegmentCap else {
      completion(
        .success([
          "kind": "invalid",
          "issues": [["field": "template", "code": "invalid-segment-cap"]],
          "affectedRecipientCount": affected.count,
        ]))
      return
    }
    let blocker = Self.messageReviewHash(
      draft: draft, workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(),
          kind: .message,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker,
          contactIds: affected.map { $0.safe.localId },
          messageDraft: draft,
          policy: nil,
          privacyAction: nil,
          occurrenceId: nil,
          consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return review.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          let examples = Array(rendered.prefix(3)).map { contact, text -> [String: Any] in
            let estimate = Self.smsEstimate(text)
            return [
              "displayName": contact.safe.displayName,
              "finalText": text,
              "characterCount": text.unicodeScalars.count,
              "segmentCount": estimate.segments,
              "encodingLabel": estimate.encoding,
            ]
          }
          completion(
            .success([
              "kind": "valid", "handle": handle, "examples": examples,
              "maximumSegmentCount": maximumSegments,
              "affectedRecipientCount": affected.count,
            ]))
        }
      }
    )
  }

  private func saveMessage(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String, Self.validOpaque(handle),
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    // Re-read the protected review first, then revalidate process-memory
    // Gemini provenance on the main actor immediately before the CAS. A review
    // whose candidate was cleared, changed, or expired is still safe to save,
    // but it is deliberately downgraded to USER provenance.
    store.readWorkflowSnapshot { [weak self] snapshotResult in
      guard let self else {
        return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
      }
      guard case .success(let snapshot) = snapshotResult,
        snapshot.revision == revision,
        let reviewedWorkflow = snapshot.workflow,
        reviewedWorkflow.account.matches(binding),
        let reviewedIndex = Self.reviewIndex(
          handle: handle, kind: .message, revision: revision,
          workflow: reviewedWorkflow
        ), let reviewedDraft = reviewedWorkflow.reviews[reviewedIndex].messageDraft
      else { return completion(.failure(Self.storeProblem(.invalidReview))) }

      let draftForCommit = Self.revalidatedDraftForSave(reviewedDraft)
      self.store.mutateWorkflow(
        expectedRevision: revision, binding: binding,
        body: { [peopleStore] workflow, _ in
          guard
            let index = Self.reviewIndex(
              handle: handle, kind: .message, revision: revision, workflow: workflow
            ), let originalDraft = workflow.reviews[index].messageDraft
          else {
            throw CompanionStoreError.invalidReview
          }
          guard
            workflow.reviews[index].blockerHash
              == Self.messageReviewHash(
                draft: originalDraft, workflow: workflow,
                contacts: peopleStore.privateContacts()
              )
          else { throw CompanionStoreError.staleMaterial }
          let invalidated = workflow.contacts.filter { $0.approvalHash != nil }.count
          workflow.messageDraft = draftForCommit
          for contactIndex in workflow.contacts.indices {
            Self.invalidateApproval(
              &workflow.contacts[contactIndex],
              reason: "template-changed"
            )
          }
          workflow.reviews[index].consumedAt = Date()
          Self.bumpConfiguration(&workflow, activityKind: "approval-invalidated")
          return (draftForCommit, invalidated)
        },
        completion: { [weak self] result in
          guard let self else {
            return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
          }
          if case .success(let value) = result,
            value.0.provenance?.source == "GEMINI"
          {
            _ = IOSGeminiSuggestionGateway.shared.consumeProvenance(
              for: Self.provenanceDraft(value.0)
            )
          }
          self.finishMutation(
            result, binding: binding, rebuild: true,
            payload: { status, value in
              [
                "draft": Self.messageDraftPayload(value.0),
                "affectedRecipientCount": Self.affectedContacts(status: status).count,
                "invalidatedApprovalCount": value.1,
              ]
            },
            completion: completion
          )
        }
      )
    }
  }

  private func previewPolicy(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["draft", "expectedRevision"],
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let raw = payload["draft"] as? [String: Any]
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    guard let policy = Self.parsePolicy(raw) else {
      return completion(
        .success([
          "kind": "invalid",
          "issues": [["field": "window", "code": "invalid-window"]],
        ]))
    }
    let simulation = simulate(policy: policy, status: status)
    if let conflict = simulation.firstConflictDate {
      return completion(
        .success([
          "kind": "invalid",
          "issues": [["field": "dailyCap", "code": "window-capacity-conflict"]],
          "firstConflictDate": conflict,
        ]))
    }
    let blocker = Self.policyReviewHash(
      policy: policy, workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(),
          kind: .policy,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker,
          contactIds: [],
          messageDraft: nil,
          policy: policy,
          privacyAction: nil,
          occurrenceId: nil,
          consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return review.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          completion(
            .success([
              "kind": "valid", "handle": handle,
              "summary": Self.windowLabel(policy),
              "simulatedDays": Self.planningDays,
              "maximumPlannedInLocalDay": simulation.maximumDaily,
              "maximumPlannedInRolling24Hours": simulation.maximumRolling,
            ]))
        }
      }
    )
  }

  private func savePolicy(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        guard
          let index = Self.reviewIndex(
            handle: handle, kind: .policy, revision: revision, workflow: workflow
          ), let policy = workflow.reviews[index].policy
        else {
          throw CompanionStoreError.invalidReview
        }
        guard
          workflow.reviews[index].blockerHash
            == Self.policyReviewHash(
              policy: policy, workflow: workflow, contacts: peopleStore.privateContacts()
            )
        else { throw CompanionStoreError.staleMaterial }
        workflow.policy = policy
        for contactIndex in workflow.contacts.indices {
          Self.invalidateApproval(
            &workflow.contacts[contactIndex],
            reason: "window-changed"
          )
        }
        workflow.reviews[index].consumedAt = Date()
        Self.bumpConfiguration(&workflow, activityKind: "approval-invalidated")
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            self.automationPayload(status: status, readiness: readiness)
          }, completion: completion
        )
      }
    )
  }

  private func prepareApprovals(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["contactIds", "expectedRevision"],
      let rawIds = payload["contactIds"] as? [Any],
      let ids = Self.validContactIds(rawIds),
      let revision = Self.payloadRevision(payload, expected: expectedRevision),
      let draft = status.workflow?.messageDraft,
      status.workflow?.policy != nil
    else { return completion(.failure(Self.actionRequired(["message-and-policy-required"]))) }
    let byId = Dictionary(
      uniqueKeysWithValues: effectiveContacts(status: status).map {
        ($0.safe.localId, $0)
      })
    let selected = ids.compactMap { byId[$0] }
    guard selected.count == ids.count else {
      return completion(.failure(Self.temporarilyUnavailable("contacts-stale")))
    }
    let ready = selected.filter { contact in
      (contact.configuration?.enrollment == .enabled
        || contact.configuration?.enrollment == .paused)
        && contact.selectedPhone != nil && contact.selectedBirthday != nil
        && contact.readinessReasons.allSatisfy { $0 == "approval-invalid" }
    }
    let blocker = Self.reviewHash(
      kind: "approval", contactIds: ready.map { $0.safe.localId },
      workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    let items = ready.compactMap { contact -> [String: Any]? in
      guard let text = Self.render(draft: draft, contact: contact.privateValue),
        let masked = contact.safe.phoneChoices.first(where: {
          $0.localId == contact.selectedPhoneId
        })?.maskedDisplay
      else { return nil }
      return [
        "platform": "ios", "contactId": contact.safe.localId,
        "recipient": contact.safe.displayName, "maskedPhone": masked,
        "birthdayLabel": "Selected birthday", "exactText": text,
        "deliveryMode": "user-controlled-composer",
        "consentDisclosure": "You choose whether to send each message in the iOS composer.",
      ]
    }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(), kind: .approval,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker, contactIds: ready.map { $0.safe.localId },
          messageDraft: nil, policy: nil, privacyAction: nil,
          occurrenceId: nil, consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return review.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          completion(
            .success([
              "handle": handle, "items": items, "readyCount": items.count,
              "blockedCount": selected.count - items.count,
              "explicitConfirmationRequired": true,
            ]))
        }
      }
    )
  }

  private func confirmApprovals(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        guard
          let index = Self.reviewIndex(
            handle: handle, kind: .approval, revision: revision, workflow: workflow
          )
        else { throw CompanionStoreError.invalidReview }
        let review = workflow.reviews[index]
        let contacts = peopleStore.privateContacts()
        guard
          review.blockerHash
            == Self.reviewHash(
              kind: "approval", contactIds: review.contactIds,
              workflow: workflow, contacts: contacts
            ), let message = workflow.messageDraft, let policy = workflow.policy
        else { throw CompanionStoreError.staleMaterial }
        let byId = Dictionary(uniqueKeysWithValues: contacts.map { ($0.localId, $0) })
        for id in review.contactIds {
          guard let contact = byId[id],
            let configIndex = workflow.contacts.firstIndex(where: { $0.contactId == id })
          else { throw CompanionStoreError.staleMaterial }
          workflow.contacts[configIndex].materialRevision = contact.materialRevision
          workflow.contacts[configIndex].approvalHash = Self.approvalHash(
            contact: contact, configuration: workflow.contacts[configIndex],
            message: message, policy: policy
          )
          workflow.contacts[configIndex].approvedAt = Date()
          workflow.contacts[configIndex].approvalInvalidationReasons = []
          workflow.contacts[configIndex].updatedAt = Date()
        }
        workflow.reviews[index].consumedAt = Date()
        Self.bumpConfiguration(&workflow, activityKind: "settings-changed")
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            self.automationPayload(status: status, readiness: readiness)
          }, completion: completion
        )
      }
    )
  }

  private func prepareActivation(
    binding: IOSNativeGoogleAccountBinding,
    revision: String,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    let eligible = effectiveContacts(status: status).filter {
      $0.configuration?.enrollment == .enabled && $0.approvalKind == "valid"
        && $0.readinessKind == "ready"
    }
    guard status.workflow?.messageDraft != nil, status.workflow?.policy != nil,
      !eligible.isEmpty
    else {
      completion(.failure(Self.actionRequired(["ios-configuration-incomplete"])))
      return
    }
    let ids = eligible.map { $0.safe.localId }
    let blocker = Self.reviewHash(
      kind: "activation", contactIds: ids,
      workflow: status.workflow!, contacts: peopleStore.privateContacts()
    )
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(), kind: .activation,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker, contactIds: ids, messageDraft: nil,
          policy: nil, privacyAction: nil, occurrenceId: nil, consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return review.handle
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let handle):
          completion(
            .success([
              "platform": "ios", "handle": handle,
              "reminderRecipientCount": eligible.count,
              "deliveryMode": "user-controlled-composer",
              "limitationsDisclosure":
                "iOS will only remind you. You review the editable system composer and tap Send yourself.",
            ]))
        }
      }
    )
  }

  private func confirmActivation(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { [peopleStore] workflow, _ in
        guard
          let index = Self.reviewIndex(
            handle: handle, kind: .activation, revision: revision, workflow: workflow
          )
        else { throw CompanionStoreError.invalidReview }
        let review = workflow.reviews[index]
        guard
          review.blockerHash
            == Self.reviewHash(
              kind: "activation", contactIds: review.contactIds,
              workflow: workflow, contacts: peopleStore.privateContacts()
            ), workflow.messageDraft != nil, workflow.policy != nil
        else { throw CompanionStoreError.staleMaterial }
        workflow.desired = .remindersOn
        workflow.reviews[index].consumedAt = Date()
        Self.bumpConfiguration(&workflow, activityKind: "settings-changed")
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            self.automationPayload(status: status, readiness: readiness)
          }, completion: completion
        )
      }
    )
  }

  private func pauseAll(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    readiness: [String: Any],
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision"],
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        workflow.desired = .paused
        Self.bumpConfiguration(&workflow, activityKind: "paused")
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        self.finishMutation(
          result, binding: binding, rebuild: true,
          payload: { status in
            self.automationPayload(status: status, readiness: readiness)
          }, completion: completion
        )
      }
    )
  }

  func privacyOperationProjection(
    operationId: String,
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    guard Self.validOpaque(operationId) else {
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
    if let receipt = deletionReceiptStore.current(),
      Self.matchesPrivacyProjectionId(
        operationId,
        nativeOperationId: receipt.operationId,
        action: "delete-account"
      )
    {
      if receipt.remoteDeletionComplete,
        deletionRecoveryStore.hasPendingOrUnreadableJournal()
      {
        return .failure(Self.temporarilyUnavailable("coordination-unavailable"))
      }
      if !receipt.remoteDeletionComplete, receipt.localDataErased,
        let recovery = deletionRecoveryStore.current(),
        recovery.operationId == receipt.operationId
      {
        return recovery.remoteAcceptanceConfirmed
          ? .success(Self.accountDeletionReceiptPayload(receipt))
          : .success(
            Self.accountDeletionRecoveryUnknownPayload(
              receipt,
              sameAccountRetryAvailable: recovery.retryAuthorized
            ))
      }
      if !receipt.remoteDeletionComplete,
        deletionRecoveryStore.hasPendingOrUnreadableJournal()
      {
        return .failure(Self.temporarilyUnavailable("coordination-unavailable"))
      }
      return .success(Self.accountDeletionReceiptPayload(receipt))
    }
    guard
      let operation = status.workflow?.privacyOperations.first(where: {
        Self.matchesPrivacyProjectionId(
          operationId,
          nativeOperationId: $0.id,
          action: $0.action
        )
      })
    else { return .failure(Self.temporarilyUnavailable("coordination-unavailable")) }
    return .success(Self.privacyOperationPayload(operation))
  }

  func currentPrivacyOperationProjection(
    status: CompanionProjectionStatus
  ) -> IOSCompanionWorkflowEngineResult {
    if let receipt = deletionReceiptStore.current() {
      if receipt.remoteDeletionComplete,
        deletionRecoveryStore.hasPendingOrUnreadableJournal()
      {
        return .success([
          "kind": "unavailable",
          "reason": "coordination-unavailable",
        ])
      }
      if !receipt.remoteDeletionComplete, receipt.localDataErased,
        let recovery = deletionRecoveryStore.current(),
        recovery.operationId == receipt.operationId
      {
        return recovery.remoteAcceptanceConfirmed
          ? .success(Self.accountDeletionReceiptPayload(receipt))
          : .success(
            Self.accountDeletionRecoveryUnknownPayload(
              receipt,
              sameAccountRetryAvailable: recovery.retryAuthorized
            ))
      }
      if !receipt.remoteDeletionComplete,
        deletionRecoveryStore.hasPendingOrUnreadableJournal()
      {
        return .success([
          "kind": "unavailable",
          "reason": "coordination-unavailable",
        ])
      }
      return .success(Self.accountDeletionReceiptPayload(receipt))
    }
    if deletionReceiptStore.hasPendingOrUnreadableReceipt()
      || deletionRecoveryStore.hasPendingOrUnreadableJournal()
    {
      return .success([
        "kind": "unavailable",
        "reason": "coordination-unavailable",
      ])
    }
    guard
      let operation = status.workflow?.privacyOperations.max(by: {
        $0.updatedAt < $1.updatedAt
      })
    else { return .success(["kind": "none"]) }
    return .success(Self.privacyOperationPayload(operation))
  }

  private func resumePrivacyOperation(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard expectedRevision == nil, Set(payload.keys) == ["operationId"],
      let operationId = payload["operationId"] as? String,
      Self.validOpaque(operationId),
      let operation = status.workflow?.privacyOperations.first(where: {
        Self.matchesPrivacyProjectionId(
          operationId,
          nativeOperationId: $0.id,
          action: $0.action
        )
      })
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    if ["complete", "failed"].contains(operation.phase) {
      completion(.success(Self.privacyOperationPayload(operation)))
      return
    }
    if operation.action == "delete-account", operation.phase == "local-wiping",
      operation.reason == Self.deletionLocalWipeRecoveryReason
    {
      performAmbiguousDeletionLocalWipe(
        operation: operation,
        binding: binding,
        completion: completion
      )
    } else {
      performPrivacyAction(
        operation: operation, binding: binding, completion: completion
      )
    }
  }

  private func preparePrivacy(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "kind"],
      let action = payload["kind"] as? String, Self.privacyActions.contains(action),
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let recoveryStateIsClear =
      !deletionReceiptStore.hasPendingOrUnreadableReceipt()
      && !deletionRecoveryStore.hasPendingOrUnreadableJournal()
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, committedRevision in
        let recoveryOperationId: String? =
          action == "wipe-local-data" && recoveryStateIsClear
          ? Self.recoverableDeletionOperation(in: workflow)?.id
          : nil
        let blocker = Self.canonicalHash([
          "privacy", action, binding.accountGeneration, revision,
          recoveryOperationId ?? "ordinary-action",
        ])
        let review = CompanionWorkflowReview(
          handle: UUID().uuidString.lowercased(), kind: .privacy,
          issuedForRevision: committedRevision,
          expiresAt: Date().addingTimeInterval(Self.reviewLifetime),
          blockerHash: blocker, contactIds: [], messageDraft: nil, policy: nil,
          privacyAction: action, occurrenceId: nil, consumedAt: nil
        )
        Self.installReview(review, in: &workflow)
        return (
          handle: review.handle,
          isDeletionRecovery: recoveryOperationId != nil
        )
      },
      completion: { result in
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let prepared):
          completion(
            .success([
              "handle": prepared.handle, "kind": action,
              "titleKey": "privacy.action.\(action)",
              "consequenceKeys": Self.privacyConsequenceKeys(action),
              // iOS cannot inspect another Android installation's in-flight permit
              // detail. Account-global reset and deletion actions therefore use the
              // conservative truthful disclosure.
              "preissuedPermitMayFinish": [
                  "delete-account", "disconnect-contacts", "revoke-google-access",
                  prepared.isDeletionRecovery ? action : "",
                ].contains(action),
              "remoteConnectionRequired": [
                "delete-account", "disconnect-contacts", "revoke-google-access",
              ].contains(action) && !prepared.isDeletionRecovery,
              "externalSmsCopiesNotErased": true,
            ]))
        }
      }
    )
  }

  private func confirmPrivacy(
    payload: [String: Any],
    binding: IOSNativeGoogleAccountBinding,
    expectedRevision: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard Set(payload.keys) == ["expectedRevision", "handle"],
      let handle = payload["handle"] as? String,
      let revision = Self.payloadRevision(payload, expected: expectedRevision)
    else { return completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))) }
    let recoveryStateIsClear =
      !deletionReceiptStore.hasPendingOrUnreadableReceipt()
      && !deletionRecoveryStore.hasPendingOrUnreadableJournal()
    store.mutateWorkflow(
      expectedRevision: revision, binding: binding,
      body: { workflow, _ in
        let recoveryOperationIndex: Int? =
          recoveryStateIsClear
            && workflow.reviews.first(where: { $0.handle == handle })?
              .privacyAction == "wipe-local-data"
          ? Self.recoverableDeletionOperationIndex(in: workflow)
          : nil
        let recoveryOperationId = recoveryOperationIndex.map {
          workflow.privacyOperations[$0].id
        }
        guard
          let index = Self.reviewIndex(
            handle: handle, kind: .privacy, revision: revision, workflow: workflow
          ), let action = workflow.reviews[index].privacyAction,
          Self.privacyActions.contains(action),
          workflow.reviews[index].blockerHash
            == Self.canonicalHash([
              "privacy", action, binding.accountGeneration,
              // The reviewed revision is one before the review transaction's
              // committed revision carried by the confirmation envelope.
              Self.previousRevision(of: revision),
              recoveryOperationId ?? "ordinary-action",
            ])
        else { throw CompanionStoreError.invalidReview }
        workflow.reviews[index].consumedAt = Date()
        let operation: CompanionWorkflowPrivacyOperation
        let isDeletionRecovery = recoveryOperationIndex != nil
        if let recoveryOperationIndex {
          workflow.privacyOperations[recoveryOperationIndex].phase = "local-wiping"
          workflow.privacyOperations[recoveryOperationIndex].reason =
            Self.deletionLocalWipeRecoveryReason
          workflow.privacyOperations[recoveryOperationIndex].updatedAt = Date()
          operation = workflow.privacyOperations[recoveryOperationIndex]
        } else if action == "delete-account",
          let existingIndex = workflow.privacyOperations.lastIndex(where: {
            $0.action == action
              && ["pausing", "remote-pending", "remote-draining", "local-wiping"]
                .contains($0.phase)
          })
        {
          workflow.privacyOperations[existingIndex].phase = "pausing"
          workflow.privacyOperations[existingIndex].reason = nil
          workflow.privacyOperations[existingIndex].updatedAt = Date()
          operation = workflow.privacyOperations[existingIndex]
        } else {
          operation = CompanionWorkflowPrivacyOperation(
            id: UUID().uuidString.lowercased(), action: action,
            phase: action == "delete-account" ? "pausing" : "local-wiping",
            reason: nil, updatedAt: Date()
          )
          workflow.privacyOperations.append(operation)
        }
        switch action {
        case "clear-gemini-templates":
          if workflow.messageDraft?.provenance?.source == "GEMINI" {
            workflow.messageDraft = nil
            workflow.desired = .paused
            for contactIndex in workflow.contacts.indices {
              Self.invalidateApproval(
                &workflow.contacts[contactIndex], reason: "template-changed"
              )
            }
            Self.bumpConfiguration(&workflow, activityKind: "approval-invalidated")
          }
        case "clear-activity":
          workflow.activity = []
          workflow.activityClearedAt = Date()
        case "disconnect-contacts":
          workflow.desired = .paused
          Self.bumpConfiguration(&workflow, activityKind: nil)
        case "sign-out-retain":
          workflow.desired = .paused
          Self.bumpConfiguration(&workflow, activityKind: "paused")
        case "delete-account":
          workflow.desired = .paused
          Self.bumpConfiguration(&workflow, activityKind: "paused")
        default:
          break
        }
        return (
          operation: operation,
          isDeletionRecovery: isDeletionRecovery
        )
      },
      completion: { [weak self] result in
        guard let self else {
          return completion(.failure(Self.internalProblem("NATIVE_BRIDGE_UNAVAILABLE")))
        }
        switch result {
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        case .success(let confirmed):
          if confirmed.isDeletionRecovery {
            self.performAmbiguousDeletionLocalWipe(
              operation: confirmed.operation,
              binding: binding,
              completion: completion
            )
          } else {
            self.performPrivacyAction(
              operation: confirmed.operation,
              binding: binding,
              completion: completion
            )
          }
        }
      }
    )
  }

  private func performPrivacyAction(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    if [
      "clear-gemini-templates", "disconnect-contacts", "sign-out-retain",
      "sign-out-wipe", "wipe-local-data", "revoke-google-access", "delete-account",
    ].contains(operation.action) {
      IOSGeminiSuggestionGateway.shared.clearProvenance()
    }
    switch operation.action {
    case "clear-gemini-templates":
      rebuildPlan(binding: binding) { [weak self] _ in
        self?.updatePrivacyOperation(
          operation, binding: binding, phase: "complete", reason: nil,
          completion: completion
        )
      }
    case "clear-activity":
      updatePrivacyOperation(
        operation, binding: binding, phase: "complete", reason: nil,
        completion: completion
      )
    case "disconnect-contacts":
      performContactDerivedReset(
        operation: operation, binding: binding, revokeGoogleAccess: false,
        completion: completion
      )
    case "sign-out-retain":
      rebuildPlan(binding: binding) { [weak self] _ in
        guard let self else { return }
        self.reminderCoordinator.cancelPlansAndNotifications { _ in
          Task { @MainActor in
            let success = await IOSGoogleIdentityCoordinator.shared
              .completeSignOutAfterSafetyShutdown(retainData: true)
            self.updatePrivacyOperation(
              operation, binding: binding,
              phase: success ? "complete" : "failed",
              reason: success ? nil : "coordination-unavailable",
              completion: completion
            )
          }
        }
      }
    case "sign-out-wipe":
      // CompanionReminderCoordinator removes pending and delivered app-owned
      // reminders before deleting the protected file/key generation.
      reminderCoordinator.wipeCompanionData { [weak self] result in
        guard let self else { return }
        guard result["kind"] as? String == "ok" else {
          return completion(
            .success(
              Self.privacyOperationPayload(
                operation, phase: "failed", reason: "coordination-unavailable"
              )))
        }
        Task { @MainActor in
          let success = await IOSGoogleIdentityCoordinator.shared
            .completeSignOutAfterSafetyShutdown(retainData: false)
          completion(
            .success(
              Self.privacyOperationPayload(
                operation,
                phase: success ? "complete" : "failed",
                reason: success ? nil : "coordination-unavailable"
              )))
        }
      }
    case "wipe-local-data":
      reminderCoordinator.wipeCompanionData { result in
        guard result["kind"] as? String == "ok" else {
          return completion(
            .success(
              Self.privacyOperationPayload(
                operation, phase: "failed", reason: "coordination-unavailable"
              )))
        }
        Task { @MainActor in
          let success = await IOSGoogleIdentityCoordinator.shared
            .wipeLocalDataAfterSafetyShutdown(binding: binding)
          completion(
            .success(
              Self.privacyOperationPayload(
                operation,
                phase: success ? "complete" : "failed",
                reason: success ? nil : "coordination-unavailable"
              )))
        }
      }
    case "revoke-google-access":
      performContactDerivedReset(
        operation: operation, binding: binding, revokeGoogleAccess: true,
        completion: completion
      )
    case "delete-account":
      performAccountDeletion(
        operation: operation, binding: binding, completion: completion
      )
    default:
      completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID")))
    }
  }

  private func performAmbiguousDeletionLocalWipe(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard operation.action == "delete-account", operation.phase == "local-wiping",
      operation.reason == Self.deletionLocalWipeRecoveryReason
    else {
      completion(.failure(Self.internalProblem("NATIVE_REQUEST_INVALID")))
      return
    }
    IOSGeminiSuggestionGateway.shared.clearProvenance()
    deletionRecoveryStore.recordReviewedLocalWipe(
      operationId: operation.id,
      binding: binding
    ) { [weak self] recoveryPersisted in
      guard let self else { return }
      guard recoveryPersisted else {
        self.updatePrivacyOperation(
          operation,
          binding: binding,
          phase: "remote-pending",
          reason: "coordination-unavailable",
          completion: completion
        )
        return
      }
      self.deletionReceiptStore.recordPending(operationId: operation.id) {
        [weak self] receiptPersisted in
        guard let self else { return }
        guard receiptPersisted else {
          // The reviewed recovery journal committed first. Keep the operation
          // blocked and let resumeIfNeeded recreate only this exact receipt;
          // destructive cleanup never starts without both durable records.
          self.deletionCleanup.resumeIfNeeded()
          completion(
            .success(
              Self.privacyOperationPayload(
                operation,
                phase: "local-wiping",
                reason: nil
              )))
          return
        }
        self.deletionCleanup.finishLocalCleanup(operationId: operation.id) {
          receipt in
          guard let receipt else {
            completion(
              .success(
                Self.privacyOperationPayload(
                  operation,
                  phase: "local-wiping",
                  reason: nil
                )))
            return
          }
          completion(
            .success(
              receipt.localDataErased
                ? Self.accountDeletionRecoveryUnknownPayload(
                  receipt,
                  sameAccountRetryAvailable: self.deletionRecoveryStore
                    .isRetryAuthorized(operationId: receipt.operationId)
                )
                : Self.accountDeletionReceiptPayload(receipt)
            ))
        }
      }
    }
  }

  private func performContactDerivedReset(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    revokeGoogleAccess: Bool,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    updatePrivacyOperation(
      operation, binding: binding, phase: "verifying", reason: nil
    ) { [weak self] _ in
      guard let self else { return }
      Task { @MainActor in
        let reauthentication = await IOSGoogleIdentityCoordinator.shared
          .ensureRecentExactGoogleAuthentication(binding: binding)
        guard case .success = reauthentication else {
          let reason: String
          if case .failure(let failure) = reauthentication {
            reason = Self.deletionIdentityFailureReason(failure)
          } else {
            reason = "account-reconnect-required"
          }
          self.updatePrivacyOperation(
            operation, binding: binding, phase: "remote-pending", reason: reason,
            completion: completion
          )
          return
        }

        self.contactResetClient.startOrReplay(
          binding: binding,
          requestId: operation.id
        ) { result in
          switch result {
          case .failure(let failure):
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-pending",
              reason: Self.contactResetFailureReason(failure),
              completion: completion
            )
          case .success(.inProgress(let drainUntil)):
            self.updatePrivacyOperation(
              operation, binding: binding,
              phase: drainUntil == nil ? "verifying" : "remote-draining",
              reason: nil, completion: completion
            )
          case .success(.completed):
            self.finishContactDerivedLocalCleanup(
              operation: operation, binding: binding,
              revokeGoogleAccess: revokeGoogleAccess, completion: completion
            )
          }
        }
      }
    }
  }

  private func finishContactDerivedLocalCleanup(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    revokeGoogleAccess: Bool,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    rebuildPlan(binding: binding) { [weak self] rebuilt in
      guard let self else { return }
      guard rebuilt else {
        return self.updatePrivacyOperation(
          operation, binding: binding, phase: "remote-pending",
          reason: "coordination-unavailable", completion: completion
        )
      }
      self.reminderCoordinator.cancelPlansAndNotifications { result in
        guard result["kind"] as? String == "ok" else {
          return self.updatePrivacyOperation(
            operation, binding: binding, phase: "remote-pending",
            reason: "coordination-unavailable", completion: completion
          )
        }
        self.peopleStore.clearContactsRetainingBinding { cleared in
          guard cleared else {
            return self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-pending",
              reason: "coordination-unavailable", completion: completion
            )
          }
          self.clearContactDerivedWorkflow(
            operation: operation, binding: binding
          ) { workflowCleared in
            guard workflowCleared else {
              return self.updatePrivacyOperation(
                operation, binding: binding, phase: "remote-pending",
                reason: "coordination-unavailable", completion: completion
              )
            }
            guard revokeGoogleAccess else {
              return self.updatePrivacyOperation(
                operation, binding: binding, phase: "complete", reason: nil,
                completion: completion
              )
            }
            Task { @MainActor in
              let revoked = await IOSGoogleIdentityCoordinator.shared
                .revokeGoogleAccessAfterSafetyShutdown()
              self.updatePrivacyOperation(
                operation, binding: binding,
                phase: revoked ? "complete" : "remote-pending",
                reason: revoked ? nil : "coordination-unavailable",
                completion: completion
              )
            }
          }
        }
      }
    }
  }

  private func clearContactDerivedWorkflow(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (Bool) -> Void
  ) {
    store.readProjectionStatus { [weak self] result in
      guard let self, case .success(let status) = result else {
        return completion(false)
      }
      self.store.mutateWorkflow(
        expectedRevision: status.revision, binding: binding,
        body: { workflow, _ in
          guard
            workflow.privacyOperations.contains(where: {
              $0.id == operation.id && $0.action == operation.action
            })
          else { throw CompanionStoreError.invalidWorkflowState }
          workflow.contacts = []
          workflow.occurrences = []
          workflow.reviews = []
          workflow.desired = .paused
          Self.bumpConfiguration(&workflow, activityKind: "settings-changed")
        },
        completion: { result in
          completion((try? result.get()) != nil)
        }
      )
    }
  }

  private func performAccountDeletion(
    operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    if let receipt = deletionReceiptStore.current(),
      receipt.operationId == operation.id
    {
      deletionCleanup.finishLocalCleanup(operationId: operation.id) { receipt in
        guard let receipt else {
          completion(
            .success(
              Self.privacyOperationPayload(
                operation, phase: "remote-pending", reason: "coordination-unavailable"
              )))
          return
        }
        completion(.success(Self.accountDeletionReceiptPayload(receipt)))
      }
      return
    }

    Task { @MainActor in
      let reauthentication = await IOSGoogleIdentityCoordinator.shared
        .ensureRecentExactGoogleAuthentication(binding: binding)
      guard case .success = reauthentication else {
        let reason: String
        if case .failure(let failure) = reauthentication {
          reason = Self.deletionIdentityFailureReason(failure)
        } else {
          reason = "account-reconnect-required"
        }
        self.updatePrivacyOperation(
          operation, binding: binding, phase: "remote-pending", reason: reason,
          completion: completion
        )
        return
      }

      self.deletionClient.startOrReplay(
        binding: binding,
        requestId: operation.id
      ) { result in
        switch result {
        case .failure(let failure):
          self.updatePrivacyOperation(
            operation, binding: binding, phase: "remote-pending",
            reason: Self.deletionClientFailureReason(failure),
            completion: completion
          )
        case .success(let acceptance):
          guard acceptance.receiptId == operation.id else {
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-pending",
              reason: "coordination-unavailable", completion: completion
            )
            return
          }
          self.deletionReceiptStore.recordRemoteDraining(operationId: operation.id) {
            persisted in
            guard persisted else {
              self.updatePrivacyOperation(
                operation, binding: binding, phase: "remote-pending",
                reason: "coordination-unavailable", completion: completion
              )
              return
            }
            self.updatePrivacyOperation(
              operation, binding: binding, phase: "remote-draining", reason: nil
            ) { _ in
              self.deletionCleanup.finishLocalCleanup(operationId: operation.id) {
                receipt in
                guard let receipt else {
                  completion(
                    .success(
                      Self.privacyOperationPayload(
                        operation, phase: "remote-pending",
                        reason: "coordination-unavailable"
                      )))
                  return
                }
                completion(.success(Self.accountDeletionReceiptPayload(receipt)))
              }
            }
          }
        }
      }
    }
  }

  private func updatePrivacyOperation(
    _ operation: CompanionWorkflowPrivacyOperation,
    binding: IOSNativeGoogleAccountBinding,
    phase: String,
    reason: String?,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    store.readProjectionStatus { [weak self] statusResult in
      guard let self else { return }
      guard case .success(let status) = statusResult else {
        return completion(
          .success(
            Self.privacyOperationPayload(
              operation, phase: phase, reason: reason
            )))
      }
      self.store.mutateWorkflow(
        expectedRevision: status.revision, binding: binding,
        body: { workflow, _ in
          guard
            let index = workflow.privacyOperations.firstIndex(where: {
              $0.id == operation.id
            })
          else { throw CompanionStoreError.invalidWorkflowState }
          workflow.privacyOperations[index].phase = phase
          workflow.privacyOperations[index].reason = reason
          workflow.privacyOperations[index].updatedAt = Date()
          return workflow.privacyOperations[index]
        },
        completion: { result in
          switch result {
          case .success(let updated):
            completion(.success(Self.privacyOperationPayload(updated)))
          case .failure:
            completion(
              .success(
                Self.privacyOperationPayload(
                  operation, phase: phase, reason: reason
                )))
          }
        }
      )
    }
  }

  func reconcileAfterPeopleSync(
    binding: IOSNativeGoogleAccountBinding,
    completion: (() -> Void)? = nil
  ) {
    store.readProjectionStatus { [weak self] result in
      guard let self, case .success(let status) = result,
        let workflow = status.workflow, workflow.account.matches(binding)
      else { return completion?() }
      let privateById = Dictionary(
        uniqueKeysWithValues: self.peopleStore.privateContacts().map { ($0.localId, $0) }
      )
      self.store.mutateWorkflow(
        expectedRevision: status.revision, binding: binding,
        body: { workflow, _ in
          var changed = false
          for index in workflow.contacts.indices {
            guard let contact = privateById[workflow.contacts[index].contactId] else {
              if workflow.contacts[index].approvalHash != nil {
                Self.invalidateApproval(
                  &workflow.contacts[index], reason: "name-changed"
                )
                changed = true
              }
              continue
            }
            if workflow.contacts[index].materialRevision != contact.materialRevision {
              Self.invalidateApproval(&workflow.contacts[index], reason: "name-changed")
              workflow.contacts[index].materialRevision = contact.materialRevision
              changed = true
            }
          }
          if changed { Self.bumpConfiguration(&workflow, activityKind: "sync") }
        },
        completion: { _ in
          self.rebuildPlan(binding: binding) { _ in completion?() }
        }
      )
    }
  }

  private func rebuildPlan(
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (Bool) -> Void
  ) {
    store.readWorkflowSnapshot { [weak self] result in
      guard let self, case .success(let snapshot) = result,
        let workflow = snapshot.workflow, workflow.account.matches(binding)
      else { return completion(false) }

      guard workflow.desired == .remindersOn,
        let draft = workflow.messageDraft,
        let policy = workflow.policy,
        let time = Self.timeComponents(policy.primaryStart)
      else {
        self.store.replaceWorkflowPlan(
          binding: binding,
          expectedConfigurationGeneration: workflow.configurationGeneration,
          occurrences: [], proposals: [], plans: []
        ) { replaceResult in
          guard case .success = replaceResult else { return completion(false) }
          self.reminderCoordinator.reconcilePersisted { response in
            completion(response["kind"] as? String == "ok")
          }
        }
        return
      }

      let privateById = Dictionary(
        uniqueKeysWithValues: peopleStore.privateContacts().map { ($0.localId, $0) }
      )
      let existingByKey = Dictionary(
        uniqueKeysWithValues: workflow.occurrences.map {
          ("\($0.contactId)|\($0.civilDate)", $0)
        }
      )
      var destinationCounts: [String: Int] = [:]
      for configuration in workflow.contacts where configuration.enrollment == .enabled {
        guard let contact = privateById[configuration.contactId],
          let phoneId = configuration.selectedPhoneId,
          let destination = contact.phones.first(where: {
            $0.localId == phoneId
          })?.e164
        else { continue }
        destinationCounts[destination, default: 0] += 1
      }
      var candidates:
        [(contact: IOSPeoplePrivateContact, config: CompanionWorkflowContact, date: Date)] = []
      for configuration in workflow.contacts where configuration.enrollment == .enabled {
        guard let contact = privateById[configuration.contactId], !contact.deleted,
          configuration.materialRevision == contact.materialRevision,
          configuration.approvalInvalidationReasons.isEmpty,
          let expectedApproval = Self.approvalHash(
            contact: contact, configuration: configuration,
            message: draft, policy: policy
          ), configuration.approvalHash == expectedApproval,
          let phoneId = configuration.selectedPhoneId,
          let destination = contact.phones.first(where: {
            $0.localId == phoneId
          })?.e164,
          destinationCounts[destination] == 1,
          let birthdayId = configuration.selectedBirthdayId,
          let birthday = contact.birthdays.first(where: { $0.localId == birthdayId })
        else { continue }
        for date in self.occurrenceDates(
          birthday: birthday, leapPolicy: configuration.leapPolicy, from: Date()
        ) {
          candidates.append((contact, configuration, date))
        }
      }
      candidates.sort {
        if $0.date != $1.date { return $0.date < $1.date }
        return $0.contact.localId < $1.contact.localId
      }
      var countByDate: [String: Int] = [:]
      var occurrences: [CompanionWorkflowOccurrence] = []
      var proposals: [CompanionApprovedProposal] = []
      var plans: [CompanionReminderPlan] = []
      var rollingInstants: [Date] = []
      let today = Self.localDate(Date(), calendar: calendar)
      for candidate in candidates {
        let civilDate = Self.localDate(candidate.date, calendar: calendar)
        let count = countByDate[civilDate] ?? 0
        guard
          let plannedInstant = calendar.date(
            bySettingHour: time.hour,
            minute: time.minute,
            second: 0,
            of: candidate.date
          )
        else { continue }
        rollingInstants.removeAll {
          plannedInstant.timeIntervalSince($0) >= 86_400
        }
        guard count < policy.dailyCap,
          rollingInstants.count < 20,
          let phoneId = candidate.config.selectedPhoneId,
          let phone = candidate.contact.phones.first(where: { $0.localId == phoneId })?.e164,
          let body = Self.render(draft: draft, contact: candidate.contact),
          Self.smsEstimate(body).segments <= draft.requestedSegmentCap
        else { continue }
        countByDate[civilDate] = count + 1
        rollingInstants.append(plannedInstant)
        let key = "\(candidate.contact.localId)|\(civilDate)"
        let existing = existingByKey[key]
        let occurrenceId = existing?.occurrenceId ?? UUID().uuidString.lowercased()
        let proposalId = existing?.proposalId ?? UUID().uuidString.lowercased()
        occurrences.append(
          CompanionWorkflowOccurrence(
            occurrenceId: occurrenceId, proposalId: proposalId,
            contactId: candidate.contact.localId, civilDate: civilDate,
            phase: civilDate == today ? .composerReady : .reminderPlanned,
            updatedAt: Date()
          )
        )
        proposals.append(
          CompanionApprovedProposal(
            proposalId: proposalId, revision: snapshot.revision,
            accountGeneration: binding.accountGeneration,
            occurrenceId: occurrenceId, occurrenceCivilDate: civilDate,
            recipient: phone, body: body, state: .ready,
            reviewNonceDigest: nil, reviewNonceExpiresAt: nil,
            reviewSessionGeneration: nil, reviewSceneIdentifier: nil,
            operationId: nil
          )
        )
        plans.append(
          CompanionReminderPlan(
            occurrenceId: occurrenceId, civilDate: civilDate,
            hour: time.hour, minute: time.minute
          )
        )
      }
      self.store.replaceWorkflowPlan(
        binding: binding,
        expectedConfigurationGeneration: workflow.configurationGeneration,
        occurrences: occurrences, proposals: proposals, plans: plans
      ) { replaceResult in
        guard case .success = replaceResult else { return completion(false) }
        self.reminderCoordinator.reconcilePersisted { response in
          completion(response["kind"] as? String == "ok")
        }
      }
    }
  }

  private func finishMutation<Value>(
    _ result: Result<Value, CompanionStoreError>,
    binding: IOSNativeGoogleAccountBinding,
    rebuild: Bool,
    payload: @escaping (CompanionProjectionStatus, Value) -> Any,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    guard case .success(let value) = result else {
      if case .failure(let error) = result {
        completion(.failure(Self.storeProblem(error)))
      }
      return
    }
    let finish = { [weak self] in
      guard let self else { return }
      self.store.readProjectionStatus { statusResult in
        switch statusResult {
        case .success(let status): completion(.success(payload(status, value)))
        case .failure(let error): completion(.failure(Self.storeProblem(error)))
        }
      }
    }
    if rebuild {
      rebuildPlan(binding: binding) { _ in finish() }
    } else {
      finish()
    }
  }

  private func finishMutation(
    _ result: Result<Void, CompanionStoreError>,
    binding: IOSNativeGoogleAccountBinding,
    rebuild: Bool,
    payload: @escaping (CompanionProjectionStatus) -> Any,
    completion: @escaping (IOSCompanionWorkflowEngineResult) -> Void
  ) {
    finishMutation(
      result, binding: binding, rebuild: rebuild,
      payload: { status, _ in payload(status) }, completion: completion
    )
  }

  private static func contactConfiguration(
    _ contact: IOSPeoplePrivateContact,
    in workflow: CompanionWorkflowState
  ) -> CompanionWorkflowContact {
    workflow.contacts.first(where: { $0.contactId == contact.localId })
      ?? CompanionWorkflowContact(
        contactId: contact.localId,
        selectedPhoneId: contact.phones.filter({ $0.e164 != nil }).count == 1
          ? contact.phones.first(where: { $0.e164 != nil })?.localId : nil,
        selectedBirthdayId: contact.birthdays.count == 1
          ? contact.birthdays[0].localId : nil,
        leapPolicy: nil,
        enrollment: .off,
        materialRevision: contact.materialRevision,
        approvalHash: nil,
        approvedAt: nil,
        approvalInvalidationReasons: [],
        lastOutcomeLabel: nil,
        updatedAt: Date()
      )
  }

  private static func upsert(
    _ configuration: CompanionWorkflowContact,
    in workflow: inout CompanionWorkflowState
  ) {
    if let index = workflow.contacts.firstIndex(where: {
      $0.contactId == configuration.contactId
    }) {
      workflow.contacts[index] = configuration
    } else {
      workflow.contacts.append(configuration)
    }
  }

  private static func invalidateApproval(
    _ configuration: inout CompanionWorkflowContact,
    reason: String
  ) {
    guard configuration.approvalHash != nil || configuration.approvedAt != nil else { return }
    configuration.approvalInvalidationReasons = Array(
      Set(configuration.approvalInvalidationReasons + [reason])
    ).sorted()
  }

  private static func bumpConfiguration(
    _ workflow: inout CompanionWorkflowState,
    activityKind: String?
  ) {
    guard workflow.configurationGeneration < UInt64.max else { return }
    workflow.configurationGeneration += 1
    if let activityKind {
      workflow.activity.append(
        CompanionWorkflowActivity(
          id: UUID().uuidString.lowercased(), kind: activityKind,
          reason: nil, occurredAt: Date(), actionable: false
        )
      )
    }
  }

  private static func installReview(
    _ review: CompanionWorkflowReview,
    in workflow: inout CompanionWorkflowState
  ) {
    workflow.reviews.removeAll {
      $0.kind == review.kind || $0.consumedAt != nil || $0.expiresAt < Date()
    }
    workflow.reviews.append(review)
    if workflow.reviews.count > maximumReviewCount {
      workflow.reviews = Array(workflow.reviews.suffix(maximumReviewCount))
    }
  }

  private static func reviewIndex(
    handle: String,
    kind: CompanionWorkflowReviewKind,
    revision: String,
    workflow: CompanionWorkflowState
  ) -> Int? {
    workflow.reviews.firstIndex {
      $0.handle == handle && $0.kind == kind && $0.consumedAt == nil
        && $0.issuedForRevision == revision && $0.expiresAt >= Date()
    }
  }

  private static func effectivePhoneId(
    _ contact: IOSPeoplePrivateContact,
    workflow: CompanionWorkflowState
  ) -> String? {
    let configuration = workflow.contacts.first { $0.contactId == contact.localId }
    if let selected = configuration?.selectedPhoneId,
      contact.phones.contains(where: { $0.localId == selected && $0.e164 != nil })
    {
      return selected
    }
    let valid = contact.phones.filter { $0.e164 != nil }
    return valid.count == 1 ? valid[0].localId : nil
  }

  private static func effectiveBirthdayId(
    _ contact: IOSPeoplePrivateContact,
    workflow: CompanionWorkflowState
  ) -> String? {
    let configuration = workflow.contacts.first { $0.contactId == contact.localId }
    if let selected = configuration?.selectedBirthdayId,
      contact.birthdays.contains(where: { $0.localId == selected })
    {
      return selected
    }
    return contact.birthdays.count == 1 ? contact.birthdays[0].localId : nil
  }

  private static func approvalHash(
    contact: IOSPeoplePrivateContact,
    configuration: CompanionWorkflowContact,
    message: CompanionWorkflowMessageDraft?,
    policy: CompanionWorkflowPolicy?
  ) -> String? {
    guard let message, let policy,
      let phone = configuration.selectedPhoneId,
      let birthday = configuration.selectedBirthdayId
    else { return nil }
    return canonicalHash([
      "approval", contact.localId, String(contact.materialRevision), phone,
      birthday, configuration.leapPolicy ?? "none", message.language,
      message.tone, message.placeholderMode, message.text,
      String(message.requestedSegmentCap), policy.primaryStart,
      policy.primaryEnd, policy.graceEnd ?? "none", String(policy.dailyCap),
      message.provenance?.source ?? "USER",
      message.provenance?.modelIdentifier ?? "none",
      message.provenance?.promptPolicyVersion ?? "none",
      message.provenance?.validatorVersion ?? "legacy-user-v1",
      "user-controlled-composer-v1",
    ])
  }

  private static func reviewHash(
    kind: String,
    contactIds: [String],
    workflow: CompanionWorkflowState,
    contacts: [IOSPeoplePrivateContact]
  ) -> String {
    let byId = Dictionary(uniqueKeysWithValues: contacts.map { ($0.localId, $0) })
    var parts = [
      kind, workflow.account.accountGeneration, String(workflow.configurationGeneration),
    ]
    for id in contactIds.sorted() {
      let contact = byId[id]
      let configuration = workflow.contacts.first { $0.contactId == id }
      parts.append(contentsOf: [
        id, String(contact?.materialRevision ?? 0),
        configuration?.selectedPhoneId ?? "none",
        configuration?.selectedBirthdayId ?? "none",
        configuration?.leapPolicy ?? "none",
        configuration?.enrollment.rawValue ?? "off",
        configuration?.approvalHash ?? "none",
      ])
    }
    if let message = workflow.messageDraft {
      parts.append(contentsOf: [
        message.language, message.tone, message.placeholderMode,
        message.text, String(message.requestedSegmentCap),
        message.provenance?.source ?? "USER",
        message.provenance?.modelIdentifier ?? "none",
        message.provenance?.promptPolicyVersion ?? "none",
        message.provenance?.validatorVersion ?? "legacy-user-v1",
      ])
    }
    if let policy = workflow.policy {
      parts.append(contentsOf: [
        policy.primaryStart, policy.primaryEnd, policy.graceEnd ?? "none",
        String(policy.dailyCap),
      ])
    }
    return canonicalHash(parts)
  }

  private static func messageReviewHash(
    draft: CompanionWorkflowMessageDraft,
    workflow: CompanionWorkflowState,
    contacts: [IOSPeoplePrivateContact]
  ) -> String {
    canonicalHash([
      reviewHash(
        kind: "message", contactIds: workflow.contacts.map(\.contactId),
        workflow: workflow, contacts: contacts
      ), draft.language, draft.tone, draft.placeholderMode, draft.text,
      String(draft.requestedSegmentCap),
      draft.provenance?.source ?? "USER",
      draft.provenance?.modelIdentifier ?? "none",
      draft.provenance?.promptPolicyVersion ?? "none",
      draft.provenance?.validatorVersion ?? "legacy-user-v1",
    ])
  }

  private static func policyReviewHash(
    policy: CompanionWorkflowPolicy,
    workflow: CompanionWorkflowState,
    contacts: [IOSPeoplePrivateContact]
  ) -> String {
    canonicalHash([
      reviewHash(
        kind: "policy", contactIds: workflow.contacts.map(\.contactId),
        workflow: workflow, contacts: contacts
      ), policy.primaryStart, policy.primaryEnd, policy.graceEnd ?? "none",
      String(policy.dailyCap),
    ])
  }

  private static func canonicalHash(_ parts: [String]) -> String {
    var data = Data()
    for part in parts {
      let bytes = Data(part.utf8)
      var length = UInt64(bytes.count).bigEndian
      withUnsafeBytes(of: &length) { data.append(contentsOf: $0) }
      data.append(bytes)
    }
    return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
  }

  private static func parseMessageDraft(
    _ raw: [String: Any]
  ) -> (draft: CompanionWorkflowMessageDraft?, issues: [[String: Any]]) {
    guard
      Set(raw.keys) == [
        "language", "placeholderMode", "requestedSegmentCap", "text", "tone",
      ], let language = raw["language"] as? String,
      let tone = raw["tone"] as? String,
      let mode = raw["placeholderMode"] as? [String: Any],
      let text = raw["text"] as? String,
      let cap = strictInteger(raw["requestedSegmentCap"], range: 1...2),
      ["en", "hi"].contains(language),
      ["warm", "simple", "cheerful"].contains(tone),
      [1, 2].contains(cap)
    else { return (nil, [["field": "template", "code": "internal-contract-invalid"]]) }
    let modeKind: String
    if Set(mode.keys) == ["kind", "requiredCount"],
      mode["kind"] as? String == "given-name",
      strictInteger(mode["requiredCount"], range: 1...1) == 1
    {
      modeKind = "given-name"
    } else if Set(mode.keys) == ["kind", "requiredCount"],
      mode["kind"] as? String == "generic",
      strictInteger(mode["requiredCount"], range: 0...0) == 0
    {
      modeKind = "generic"
    } else {
      return (nil, [["field": "template", "code": "template-placeholder-count"]])
    }
    var issues: [[String: Any]] = []
    if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
      || text.count > 1_000 || text.utf8.count > 4_096
    {
      issues.append(["field": "template", "code": "template-empty"])
    }
    let expression = try! NSRegularExpression(pattern: "\\{[^{}]+\\}")
    let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
    let placeholders = expression.matches(in: text, range: fullRange).compactMap {
      Range($0.range, in: text).map { String(text[$0]) }
    }
    let firstNameCount = placeholders.filter { $0 == "{firstName}" }.count
    if placeholders.contains(where: { $0 != "{firstName}" })
      || text.replacingOccurrences(
        of: "\\{[^{}]+\\}", with: "", options: .regularExpression
      ).contains(where: { $0 == "{" || $0 == "}" })
    {
      issues.append(["field": "template", "code": "template-unsupported-placeholder"])
    }
    if firstNameCount != (modeKind == "given-name" ? 1 : 0) {
      issues.append(["field": "template", "code": "template-placeholder-count"])
    }
    if text.range(of: "(?:https?://|www\\.)\\S+", options: [.regularExpression, .caseInsensitive])
      != nil
    {
      issues.append(["field": "template", "code": "template-url-not-allowed"])
    }
    let bidi = CharacterSet(
      charactersIn:
        "\u{061C}\u{200E}\u{200F}\u{202A}\u{202B}\u{202C}\u{202D}\u{202E}"
        + "\u{2066}\u{2067}\u{2068}\u{2069}")
    if text.unicodeScalars.contains(where: { bidi.contains($0) }) {
      issues.append(["field": "template", "code": "template-bidi-control"])
    }
    if text.unicodeScalars.contains(where: {
      ($0.value <= 0x1F && ![9, 10, 13].contains(Int($0.value))) || $0.value == 0x7F
    }) {
      issues.append(["field": "template", "code": "template-control-character"])
    }
    guard issues.isEmpty else { return (nil, issues) }
    return (
      CompanionWorkflowMessageDraft(
        language: language, tone: tone, placeholderMode: modeKind,
        text: text, requestedSegmentCap: cap,
        provenance: CompanionWorkflowMessageProvenance(
          source: "USER", modelIdentifier: nil, promptPolicyVersion: nil,
          validatorVersion: "birthday-template-validator-v1"
        )
      ),
      []
    )
  }

  private static func parsePolicy(_ raw: [String: Any]) -> CompanionWorkflowPolicy? {
    guard Set(raw.keys) == ["dailyCap", "latePolicy", "primaryEnd", "primaryStart"],
      let start = raw["primaryStart"] as? String,
      let end = raw["primaryEnd"] as? String,
      let cap = strictInteger(raw["dailyCap"], range: 1...20),
      (1...20).contains(cap), let startMinutes = minutes(start),
      let endMinutes = minutes(end), startMinutes < endMinutes,
      (30...240).contains(endMinutes - startMinutes),
      let late = raw["latePolicy"] as? [String: Any]
    else { return nil }
    let grace: String?
    if Set(late.keys) == ["kind"], late["kind"] as? String == "none" {
      grace = nil
    } else if Set(late.keys) == ["graceEnd", "kind"],
      late["kind"] as? String == "same-day-grace",
      let rawGrace = late["graceEnd"] as? String,
      let graceMinutes = minutes(rawGrace), graceMinutes > endMinutes,
      graceMinutes - startMinutes <= 240
    {
      grace = rawGrace
    } else {
      return nil
    }
    return CompanionWorkflowPolicy(
      primaryStart: start, primaryEnd: end, graceEnd: grace, dailyCap: cap
    )
  }

  private func simulate(
    policy: CompanionWorkflowPolicy,
    status: CompanionProjectionStatus
  ) -> (maximumDaily: Int, maximumRolling: Int, firstConflictDate: String?) {
    var counts: [String: Int] = [:]
    var instants: [Date] = []
    let time = Self.timeComponents(policy.primaryStart)
    for contact in effectiveContacts(status: status)
    where contact.configuration?.enrollment == .enabled {
      for date in occurrenceDates(
        birthday: contact.selectedBirthday,
        leapPolicy: contact.configuration?.leapPolicy,
        from: Date()
      ) {
        let key = Self.localDate(date, calendar: calendar)
        counts[key, default: 0] += 1
        if let time,
          let instant = calendar.date(
            bySettingHour: time.hour,
            minute: time.minute,
            second: 0,
            of: date
          )
        {
          instants.append(instant)
        }
      }
    }
    instants.sort()
    var start = 0
    var maximumRolling = 0
    var rollingConflict: String?
    for end in instants.indices {
      while start < end, instants[end].timeIntervalSince(instants[start]) >= 86_400 {
        start += 1
      }
      let count = end - start + 1
      maximumRolling = max(maximumRolling, count)
      if count > 20, rollingConflict == nil {
        rollingConflict = Self.localDate(instants[end], calendar: calendar)
      }
    }
    let dailyConflict = counts.filter { $0.value > policy.dailyCap }.keys.sorted().first
    return (
      counts.values.max() ?? 0,
      maximumRolling,
      dailyConflict ?? rollingConflict
    )
  }

  private func nextOccurrence(
    birthday: IOSPeoplePrivateBirthday?,
    leapPolicy: String?,
    from now: Date
  ) -> Date? {
    occurrenceDates(
      birthday: birthday, leapPolicy: leapPolicy, from: now
    ).first
  }

  private func occurrenceDates(
    birthday: IOSPeoplePrivateBirthday?,
    leapPolicy: String?,
    from now: Date
  ) -> [Date] {
    guard let birthday else { return [] }
    let start = calendar.startOfDay(for: now)
    guard
      let horizon = calendar.date(
        byAdding: .day, value: Self.planningDays, to: start
      )
    else { return [] }
    let currentYear = calendar.component(.year, from: start)
    var values: [Date] = []
    for year in currentYear...(currentYear + 2) {
      var month = birthday.month
      var day = birthday.day
      if month == 2 && day == 29,
        calendar.date(from: DateComponents(year: year, month: 2, day: 29)) == nil
      {
        switch leapPolicy {
        case "feb-28": day = 28
        case "mar-01":
          month = 3
          day = 1
        case "skip": continue
        default: return []
        }
      }
      guard let value = calendar.date(from: DateComponents(year: year, month: month, day: day)),
        value >= start,
        value <= horizon
      else { continue }
      values.append(value)
    }
    return values.sorted()
  }

  private static func render(
    draft: CompanionWorkflowMessageDraft,
    contact: IOSPeoplePrivateContact
  ) -> String? {
    if draft.placeholderMode == "given-name" {
      guard let givenName = contact.givenName else { return nil }
      return draft.text.replacingOccurrences(of: "{firstName}", with: givenName)
    }
    return draft.text
  }

  private static func smsEstimate(_ text: String) -> (encoding: String, segments: Int) {
    let basic = Set(
      "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    )
    let extended = Set("^{}\\[~]|€")
    let characters = Array(text)
    let isGsm = characters.allSatisfy { basic.contains($0) || extended.contains($0) }
    let units =
      isGsm
      ? characters.reduce(0) { $0 + (extended.contains($1) ? 2 : 1) }
      : text.utf16.count
    let segments: Int
    if units == 0 {
      segments = 0
    } else if isGsm && units <= 160 {
      segments = 1
    } else if isGsm {
      segments = (units + 152) / 153
    } else if units <= 70 {
      segments = 1
    } else {
      segments = (units + 66) / 67
    }
    return (isGsm ? "gsm-7" : "unicode", segments)
  }

  private static func affectedContacts(
    status: CompanionProjectionStatus
  ) -> [IOSCompanionEffectiveContact] {
    IOSCompanionWorkflowEngine.shared.effectiveContacts(status: status).filter {
      $0.configuration?.enrollment == .enabled
        || $0.configuration?.enrollment == .paused
    }
  }

  private static func messageDraftPayload(
    _ draft: CompanionWorkflowMessageDraft
  ) -> [String: Any] {
    [
      "language": draft.language,
      "tone": draft.tone,
      "placeholderMode": draft.placeholderMode == "given-name"
        ? ["kind": "given-name", "requiredCount": 1]
        : ["kind": "generic", "requiredCount": 0],
      "text": draft.text,
      "requestedSegmentCap": draft.requestedSegmentCap,
    ]
  }

  private static func provenanceDraft(
    _ draft: CompanionWorkflowMessageDraft
  ) -> IOSGeminiProvenanceDraft {
    IOSGeminiProvenanceDraft(
      language: draft.language,
      tone: draft.tone,
      placeholderMode: draft.placeholderMode,
      requestedSegmentCap: draft.requestedSegmentCap,
      text: draft.text
    )
  }

  private static func draftWithProvenance(
    _ draft: CompanionWorkflowMessageDraft
  ) -> CompanionWorkflowMessageDraft {
    guard
      let provenance = IOSGeminiSuggestionGateway.shared.peekProvenance(
        for: provenanceDraft(draft)
      )
    else { return draft }
    return CompanionWorkflowMessageDraft(
      language: draft.language,
      tone: draft.tone,
      placeholderMode: draft.placeholderMode,
      text: draft.text,
      requestedSegmentCap: draft.requestedSegmentCap,
      provenance: CompanionWorkflowMessageProvenance(
        source: provenance.source,
        modelIdentifier: provenance.modelIdentifier,
        promptPolicyVersion: provenance.promptPolicyVersion,
        validatorVersion: provenance.validatorVersion
      )
    )
  }

  private static func revalidatedDraftForSave(
    _ draft: CompanionWorkflowMessageDraft
  ) -> CompanionWorkflowMessageDraft {
    guard let stored = draft.provenance, stored.source == "GEMINI" else {
      return draft
    }
    guard
      let live = IOSGeminiSuggestionGateway.shared.peekProvenance(
        for: provenanceDraft(draft)
      ), live.source == stored.source,
      live.modelIdentifier == stored.modelIdentifier,
      live.promptPolicyVersion == stored.promptPolicyVersion,
      live.validatorVersion == stored.validatorVersion
    else {
      return CompanionWorkflowMessageDraft(
        language: draft.language,
        tone: draft.tone,
        placeholderMode: draft.placeholderMode,
        text: draft.text,
        requestedSegmentCap: draft.requestedSegmentCap,
        provenance: CompanionWorkflowMessageProvenance(
          source: "USER", modelIdentifier: nil, promptPolicyVersion: nil,
          validatorVersion: "birthday-template-validator-v1"
        )
      )
    }
    return draft
  }

  private static func windowLabel(_ policy: CompanionWorkflowPolicy) -> String {
    var label = "\(policy.primaryStart)–\(policy.primaryEnd) · up to \(policy.dailyCap)/day"
    if let grace = policy.graceEnd { label += " · grace until \(grace)" }
    return label
  }

  private static func recoverableDeletionOperationIndex(
    in workflow: CompanionWorkflowState
  ) -> Int? {
    workflow.privacyOperations.indices
      .filter {
        workflow.privacyOperations[$0].action == "delete-account"
          && workflow.privacyOperations[$0].phase == "remote-pending"
      }
      .max {
        workflow.privacyOperations[$0].updatedAt
          < workflow.privacyOperations[$1].updatedAt
      }
  }

  private static func recoverableDeletionOperation(
    in workflow: CompanionWorkflowState
  ) -> CompanionWorkflowPrivacyOperation? {
    recoverableDeletionOperationIndex(in: workflow).map {
      workflow.privacyOperations[$0]
    }
  }

  private static func privacyOperationPayload(
    _ operation: CompanionWorkflowPrivacyOperation,
    phase override: String? = nil,
    reason overrideReason: String? = nil
  ) -> [String: Any] {
    let phase = override ?? operation.phase
    let reason = overrideReason ?? operation.reason
    let projectionId = privacyProjectionId(
      nativeOperationId: operation.id,
      action: operation.action
    )
    switch phase {
    case "complete":
      return [
        "kind": "complete", "id": projectionId, "action": operation.action,
        "completedAt": dateString(Date()), "externalSmsCopiesNotErased": true,
      ]
    case "remote-pending":
      return [
        "kind": "remote-pending", "id": projectionId, "action": operation.action,
        "reason": reason ?? "coordination-unavailable",
        "updatedAt": dateString(Date()),
      ]
    case "failed":
      return [
        "kind": "failed", "id": projectionId, "action": operation.action,
        "reason": reason ?? "coordination-unavailable",
        "updatedAt": dateString(Date()),
      ]
    case "queued", "pausing", "remote-draining", "local-wiping", "verifying":
      return [
        "kind": phase, "id": projectionId, "action": operation.action,
        "updatedAt": dateString(operation.updatedAt),
      ]
    default:
      return [
        "kind": "failed", "id": projectionId, "action": operation.action,
        "reason": "internal-contract-invalid", "updatedAt": dateString(Date()),
      ]
    }
  }

  static func accountDeletionReceiptPayload(
    _ receipt: IOSAccountDeletionReceipt
  ) -> [String: Any] {
    if receipt.remoteDeletionComplete, let completedAt = receipt.completedAt {
      return [
        "kind": "complete",
        "id": privacyProjectionId(
          nativeOperationId: receipt.operationId,
          action: "delete-account"
        ),
        "action": "delete-account",
        "completedAt": dateString(completedAt),
        "localDataErased": true,
        "remoteDeletionComplete": true,
        "externalSmsCopiesNotErased": true,
      ]
    }
    let projectionId = privacyProjectionId(
      nativeOperationId: receipt.operationId,
      action: "delete-account"
    )
    guard receipt.localDataErased else {
      return [
        "kind": "local-wiping",
        "id": projectionId,
        "action": "delete-account",
        "updatedAt": dateString(receipt.recordedAt),
      ]
    }
    return [
      "kind": "remote-draining",
      "id": projectionId,
      "action": "delete-account",
      "updatedAt": dateString(receipt.recordedAt),
      "localDataErased": true,
      "remoteDeletionComplete": false,
      "externalSmsCopiesNotErased": true,
    ]
  }

  static func accountDeletionRecoveryUnknownPayload(
    _ receipt: IOSAccountDeletionReceipt,
    sameAccountRetryAvailable: Bool
  ) -> [String: Any] {
    [
      "kind": "remote-unknown",
      "id": privacyProjectionId(
        nativeOperationId: receipt.operationId,
        action: "delete-account"
      ),
      "action": "delete-account",
      "reason": "coordination-unavailable",
      "updatedAt": dateString(receipt.recordedAt),
      "localDataErased": true,
      "remoteDeletionComplete": false,
      "sameAccountRetryAvailable": sameAccountRetryAvailable,
      "externalSmsCopiesNotErased": true,
    ]
  }

  private static func privacyProjectionId(
    nativeOperationId: String,
    action: String
  ) -> String {
    guard action == "delete-account" else { return nativeOperationId }
    return "privacy_"
      + canonicalHash([
        "birthday-ios-delete-operation-projection-v1", nativeOperationId,
      ])
  }

  private static func matchesPrivacyProjectionId(
    _ candidate: String,
    nativeOperationId: String,
    action: String
  ) -> Bool {
    constantTimeEqual(
      candidate,
      privacyProjectionId(nativeOperationId: nativeOperationId, action: action)
    )
  }

  private static func constantTimeEqual(_ lhs: String, _ rhs: String) -> Bool {
    let left = Array(lhs.utf8)
    let right = Array(rhs.utf8)
    guard left.count == right.count else { return false }
    var difference: UInt8 = 0
    for index in left.indices {
      difference |= left[index] ^ right[index]
    }
    return difference == 0
  }

  private static func deletionIdentityFailureReason(
    _ failure: IOSGoogleIdentityFailure
  ) -> String {
    switch failure {
    case .cancelled: return "account-cancelled"
    case .networkOffline: return "network-offline"
    case .accountMismatch: return "account-mismatch"
    case .firebaseUserDisabled: return "account-disabled"
    case .presenterUnavailable, .reconnectRequired:
      return "account-reconnect-required"
    case .appCheckUnavailable, .configurationUnavailable, .internalFailure:
      return "coordination-unavailable"
    }
  }

  private static func deletionClientFailureReason(
    _ failure: IOSAccountDeletionFailure
  ) -> String {
    switch failure {
    case .accountChanged, .recentAuthenticationRequired:
      return "account-reconnect-required"
    case .networkOffline:
      return "network-offline"
    case .configuration, .responseInvalid, .unavailable:
      return "coordination-unavailable"
    }
  }

  private static func contactResetFailureReason(
    _ failure: IOSContactDerivedResetFailure
  ) -> String {
    switch failure {
    case .accountChanged, .recentAuthenticationRequired:
      return "account-reconnect-required"
    case .networkOffline:
      return "network-offline"
    case .deletionInProgress:
      return "firebase-account-deleting"
    case .requestMismatch, .generationExhausted:
      return "internal-contract-invalid"
    case .configuration, .continuityUnavailable, .operationInProgress,
      .resetSuppressed, .responseInvalid, .unavailable:
      return "coordination-unavailable"
    }
  }

  private static func privacyConsequenceKeys(_ action: String) -> [String] {
    switch action {
    case "clear-activity":
      return [
        "privacy.consequence.activity-hidden",
        "privacy.consequence.safety-retained",
      ]
    case "clear-gemini-templates":
      return [
        "privacy.consequence.gemini-templates-removed",
        "privacy.consequence.reapproval-required",
      ]
    case "disconnect-contacts":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.google-working-data-removed",
        "privacy.consequence.reapproval-required",
        "privacy.consequence.android-reset-paused",
        "privacy.consequence.android-test-required",
        "privacy.consequence.external-sms",
      ]
    case "revoke-google-access":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.all-google-scopes-revoked",
        "privacy.consequence.google-working-data-removed",
        "privacy.consequence.reapproval-required",
        "privacy.consequence.android-reset-paused",
        "privacy.consequence.android-test-required",
        "privacy.consequence.external-sms",
      ]
    case "sign-out-retain":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.same-account-setup-retained",
        "privacy.consequence.external-sms",
      ]
    case "sign-out-wipe", "wipe-local-data":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.local-data-erased",
        "privacy.consequence.external-sms",
      ]
    case "delete-account":
      return [
        "privacy.consequence.automation-paused",
        "privacy.consequence.remote-deletion-drain-started",
        "privacy.consequence.local-data-erased-after-drain",
        "privacy.consequence.external-sms",
      ]
    default:
      return [
        "privacy.consequence.local-data",
        "privacy.consequence.external-sms",
      ]
    }
  }

  private static func activityPayload(
    id: String,
    kind: String,
    reason: String?,
    occurredAt: Date,
    actionable: Bool
  ) -> [String: Any] {
    var result: [String: Any] = [
      "id": id, "kind": kind, "occurredAt": dateString(occurredAt),
      "actionable": actionable,
    ]
    if let reason { result["reason"] = reason }
    return result
  }

  private static func validPeopleQuery(_ query: [String: Any]) -> Bool {
    let allowed = Set(["all", "enabled", "ready", "needs-attention", "excluded"])
    let keys: Set<String> =
      query["cursor"] == nil && query["search"] == nil
      ? ["filter", "pageSize"]
      : query["cursor"] == nil
        ? ["filter", "pageSize", "search"]
        : query["search"] == nil
          ? ["cursor", "filter", "pageSize"]
          : ["cursor", "filter", "pageSize", "search"]
    guard Set(query.keys) == keys,
      let filter = query["filter"] as? String, allowed.contains(filter),
      let pageSize = strictInteger(query["pageSize"], range: 1...50),
      (1...50).contains(pageSize),
      (query["search"] as? String)?.count ?? 0 <= 256,
      pageOffset(query["cursor"] as? String) != nil
    else { return false }
    return true
  }

  private static func validActivityQuery(_ query: [String: Any]) -> Bool {
    let keys: Set<String> =
      query["cursor"] == nil
      ? ["pageSize"] : ["cursor", "pageSize"]
    guard Set(query.keys) == keys,
      let pageSize = strictInteger(query["pageSize"], range: 1...50),
      (1...50).contains(pageSize),
      pageOffset(query["cursor"] as? String) != nil
    else { return false }
    return true
  }

  private static func validContactIds(_ raw: [Any]) -> [String]? {
    guard !raw.isEmpty, raw.count <= maximumBatch else { return nil }
    let values = raw.compactMap { $0 as? String }
    guard values.count == raw.count, Set(values).count == values.count,
      values.allSatisfy(validOpaque)
    else { return nil }
    return values
  }

  private static func pageOffset(_ cursor: String?) -> Int? {
    guard let cursor else { return 0 }
    guard cursor.hasPrefix("page."), let value = Int(cursor.dropFirst(5)),
      value >= 0, value <= 1_000_000
    else { return nil }
    return value
  }

  private static func payloadRevision(
    _ payload: [String: Any],
    expected: String?
  ) -> String? {
    guard let value = payload["expectedRevision"] as? String,
      value == expected,
      value.range(of: "^(0|[1-9][0-9]{0,18})$", options: .regularExpression) != nil
    else { return nil }
    return value
  }

  private static func validOpaque(_ value: String) -> Bool {
    value.range(
      of: "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$",
      options: .regularExpression
    ) != nil
  }

  private static func minutes(_ value: String) -> Int? {
    guard
      value.range(of: "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$", options: .regularExpression)
        != nil
    else { return nil }
    let parts = value.split(separator: ":").compactMap { Int($0) }
    guard parts.count == 2 else { return nil }
    return parts[0] * 60 + parts[1]
  }

  private static func strictInteger(
    _ raw: Any?,
    range: ClosedRange<Int>
  ) -> Int? {
    guard let number = raw as? NSNumber,
      CFGetTypeID(number) != CFBooleanGetTypeID(),
      number.doubleValue.isFinite,
      number.doubleValue.rounded() == number.doubleValue,
      number.doubleValue >= Double(Int.min),
      number.doubleValue <= Double(Int.max)
    else { return nil }
    let value = number.intValue
    return range.contains(value) ? value : nil
  }

  private static func timeComponents(_ value: String) -> (hour: Int, minute: Int)? {
    guard let value = minutes(value) else { return nil }
    return (value / 60, value % 60)
  }

  private static func previousRevision(of value: String) -> String {
    guard let revision = UInt64(value), revision > 0 else { return "0" }
    return String(revision - 1)
  }

  private static func localDate(_ date: Date, calendar: Calendar) -> String {
    let components = calendar.dateComponents([.year, .month, .day], from: date)
    return String(
      format: "%04d-%02d-%02d",
      components.year ?? 0, components.month ?? 0, components.day ?? 0
    )
  }

  private static func dateString(_ date: Date) -> String {
    isoFormatter.string(from: date)
  }

  private static func storeProblem(_ error: CompanionStoreError) -> [String: Any] {
    switch error {
    case .staleRevision:
      return ["kind": "temporarily-unavailable", "code": "stale-revision"]
    case .staleMaterial, .invalidReview:
      return ["kind": "conflict", "code": "stale-revision"]
    case .accountMismatch, .accountUnavailable:
      return temporarilyUnavailable("account-reconnect-required")
    case .androidManaged:
      return temporarilyUnavailable("active-sender-other-device")
    case .coexistenceUnverified:
      return temporarilyUnavailable("coordination-unavailable")
    case .resetFenceActive:
      return temporarilyUnavailable("reset-safety-blocked")
    case .resetFenceOverflow:
      return temporarilyUnavailable("reset-safety-overflow")
    default:
      return internalProblem(error.safeCode)
    }
  }

  private static func internalProblem(_ code: String) -> [String: Any] {
    ["kind": "internal", "supportCode": code]
  }

  private static func temporarilyUnavailable(_ code: String) -> [String: Any] {
    ["kind": "temporarily-unavailable", "code": code]
  }

  private static func unsupported(_ code: String) -> [String: Any] {
    ["kind": "unsupported", "code": code]
  }

  private static func validation(_ issues: [[String: Any]]) -> [String: Any] {
    ["kind": "validation", "issues": issues]
  }

  private static func actionRequired(_ issueIds: [String]) -> [String: Any] {
    ["kind": "action-required", "issueIds": issueIds]
  }
}
