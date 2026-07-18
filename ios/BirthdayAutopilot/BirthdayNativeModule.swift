import FirebaseCore
import Foundation
import MessageUI
import UIKit

private typealias BirthdayJSON = [String: Any]

private enum BirthdayNativePayloadResult {
  case success(Any)
  case failure(BirthdayJSON)
}

private struct BirthdayNativeRuntimeContext {
  let applicationIsActive: Bool
  let protectedDataIsAvailable: Bool
  let messageComposerIsAvailable: Bool
}

/// React Native's content-minimized projection boundary for iOS Companion.
///
/// This module deliberately does not expose a credential, Google/Firebase
/// object, contact payload, destination, proposal body, notification identity,
/// or background/programmatic SMS operation. MessageUI presentation remains in
/// CompanionMessageModule and accepts only a protected proposal CAS + nonce.
@MainActor
@objc(BirthdayNativeService)
public final class BirthdayNativeService: NSObject {
  private static let contractVersion = 1
  private static let maximumRequestBytes = 65_536
  private static let projectionAreas: Set<String> = [
    "account",
    "activity",
    "automation",
    "bootstrap",
    "contacts",
    "eligibility",
    "home",
    "messages",
    "privacy",
    "readiness",
    "route",
    "setup",
  ]
  private static let userIntents: Set<String> = [
    "activate",
    "authorize-contacts",
    "block-recipient-destination",
    "choose-birthday",
    "choose-phone",
    "check-account-deletion-status",
    "confirm-approvals",
    "confirm-enrollment",
    "confirm-privacy-action",
    "confirm-today-occurrence",
    "continue-with-google",
    "exclude-recipient",
    "generate-suggestions",
    "pause-all",
    "pause-recipient",
    "perform-native-action",
    "prepare-activation",
    "prepare-approvals",
    "prepare-enrollment-review",
    "prepare-privacy-action",
    "prepare-resume",
    "prepare-test",
    "prepare-today-occurrence",
    "preview-diagnostics",
    "preview-message",
    "preview-policy",
    "refresh-compatibility",
    "restore-recipient",
    "resume-lifecycle-operation",
    "resume",
    "save-message",
    "save-policy",
    "share-diagnostics",
    "start-test",
    "sync-contacts",
    "unblock-recipient-destination",
  ]
  private static let revisionPattern = try! NSRegularExpression(
    pattern: "^(0|[1-9][0-9]{0,18})$"
  )
  private static let iso8601Formatter: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter
  }()

  private let store = CompanionProtectedStore.shared
  private let peopleStore = CompanionPeopleStore.shared
  private let identity = IOSGoogleIdentityCoordinator.shared
  private let peopleSync = IOSPeopleSyncCoordinator.shared
  private let workflow = IOSCompanionWorkflowEngine.shared
  private var diagnosticsShareInProgress = false
  private var lifecycleMutationInProgress = false

  @objc public override init() {
    super.init()
  }

  @objc(getProjection:requestJson:completion:)
  public func getProjection(
    _ area: String,
    requestJson: String,
    completion resolve: @escaping (NSDictionary) -> Void
  ) {
    let request = Self.parseObject(requestJson)
    if area == "route" {
      executeRouteProjection(request: request, completion: resolve)
      return
    }
    readStatus { [weak self] result in
      guard let self else {
        resolve(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      switch result {
      case .failure(let error):
        resolve(
          self.response(
            revision: "0",
            kind: "error",
            payload: Self.internalProblem(error.safeCode)
          ))
      case .success(let status):
        guard Self.projectionAreas.contains(area), let request else {
          resolve(
            self.response(
              revision: status.revision,
              kind: "error",
              payload: Self.internalProblem("NATIVE_REQUEST_INVALID")
            ))
          return
        }
        let context = Self.runtimeContext()
        switch self.projection(
          area: area,
          request: request,
          status: status,
          context: context
        ) {
        case .success(let payload):
          resolve(self.response(revision: status.revision, kind: "ok", payload: payload))
        case .failure(let problem):
          resolve(self.response(revision: status.revision, kind: "error", payload: problem))
        }
      }
    }
  }

  @objc(executeUserIntent:expectedRevision:payloadJson:completion:)
  public func executeUserIntent(
    _ intent: String,
    expectedRevision: String?,
    payloadJson: String,
    completion resolve: @escaping (NSDictionary) -> Void
  ) {
    let payload = Self.parseObject(payloadJson)
    readStatus { [weak self] result in
      guard let self else {
        resolve(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      switch result {
      case .failure(let error):
        resolve(
          self.response(
            revision: "0",
            kind: "error",
            payload: Self.internalProblem(error.safeCode)
          ))
      case .success(let status):
        let revisionIsValid = expectedRevision.map(Self.isValidRevision) ?? true
        guard Self.userIntents.contains(intent), let payload,
          revisionIsValid
        else {
          resolve(
            self.response(
              revision: status.revision,
              kind: "error",
              payload: Self.internalProblem("NATIVE_REQUEST_INVALID")
            ))
          return
        }
        if let expectedRevision, expectedRevision != status.revision {
          resolve(
            self.response(
              revision: status.revision,
              kind: "error",
              payload: [
                "kind": "stale-revision",
                "latestRevision": status.revision,
              ]
            ))
          return
        }

        if intent == "check-account-deletion-status" {
          guard payload.isEmpty, expectedRevision == nil else {
            resolve(
              self.response(
                revision: status.revision,
                kind: "error",
                payload: Self.internalProblem("NATIVE_REQUEST_INVALID")
              ))
            return
          }
          IOSAccountDeletionLocalCleanupCoordinator.shared.checkRemoteCompletion {
            [weak self] outcome in
            guard let self else {
              resolve(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
              return
            }
            let value: BirthdayJSON
            switch outcome {
            case .inProgress(let receipt), .completed(let receipt):
              value = IOSCompanionWorkflowEngine.accountDeletionReceiptPayload(receipt)
            case .remoteUnknown(let receipt, let sameAccountRetryAvailable):
              value = IOSCompanionWorkflowEngine.accountDeletionRecoveryUnknownPayload(
                receipt,
                sameAccountRetryAvailable: sameAccountRetryAvailable
              )
            case .unavailable:
              value = [
                "kind": "unavailable",
                "reason": "coordination-unavailable",
              ]
            }
            resolve(
              self.response(
                revision: status.revision,
                kind: "ok",
                payload: value
              ))
          }
          return
        }

        if intent == "continue-with-google" {
          guard payload.isEmpty, expectedRevision == nil else {
            resolve(
              self.response(
                revision: status.revision,
                kind: "error",
                payload: Self.internalProblem("NATIVE_REQUEST_INVALID")
              ))
            return
          }
          if Self.accountDeletionStateBlocksOrdinaryIdentity() {
            guard IOSAccountDeletionRecoveryStore.shared
              .retryAuthorizedOperationId() != nil
            else {
              resolve(
                self.response(
                  revision: status.revision,
                  kind: "error",
                  payload: Self.temporarilyUnavailableProblem(
                    "firebase-account-deleting"
                  )
                ))
              return
            }
            guard let lifecycleCompletion = self.beginLifecycleMutation(
              revision: status.revision,
              completion: resolve
            ) else { return }
            self.executeGoogleDeletionRecovery(completion: lifecycleCompletion)
            return
          }
          if status.workflow?.privacyOperations.contains(where: {
            !["complete", "failed"].contains($0.phase)
          }) == true {
            resolve(
              self.response(
                revision: status.revision,
                kind: "error",
                payload: Self.temporarilyUnavailableProblem(
                  "coordination-unavailable"
                )
              ))
            return
          }
          guard let lifecycleCompletion = self.beginLifecycleMutation(
            revision: status.revision,
            completion: resolve
          ) else { return }
          self.executeGoogleIdentity(completion: lifecycleCompletion)
          return
        }

        if Self.accountDeletionStateBlocksOrdinaryIdentity() {
          resolve(
            self.response(
              revision: status.revision,
              kind: "error",
              payload: Self.temporarilyUnavailableProblem("firebase-account-deleting")
            ))
          return
        }
        if intent == "authorize-contacts" || intent == "sync-contacts" {
          let validPayload: Bool
          if intent == "authorize-contacts" {
            validPayload = payload.isEmpty
          } else if Set(payload.keys) == Set(["reason"]),
            let reason = payload["reason"] as? String
          {
            validPayload = ["setup", "user"].contains(reason)
          } else {
            validPayload = false
          }
          guard validPayload else {
            resolve(
              self.response(
                revision: status.revision,
                kind: "error",
                payload: Self.internalProblem("NATIVE_REQUEST_INVALID")
              ))
            return
          }
          if status.workflow?.privacyOperations.contains(where: {
            !["complete", "failed"].contains($0.phase)
          }) == true {
            resolve(
              self.response(
                revision: status.revision,
                kind: "error",
                payload: Self.temporarilyUnavailableProblem(
                  "coordination-unavailable"
                )
              ))
            return
          }
          if intent == "sync-contacts" {
            guard let binding = self.identity.exactSessionBinding() else {
              resolve(
                self.response(
                  revision: status.revision,
                  kind: "error",
                  payload: Self.temporarilyUnavailableProblem(
                    "account-reconnect-required"
                  )
                ))
              return
            }
            guard let workflow = status.workflow, workflow.account.matches(binding) else {
              resolve(
                self.response(
                  revision: status.revision,
                  kind: "error",
                  payload: Self.temporarilyUnavailableProblem(
                    "account-reconnect-required"
                  )
                ))
              return
            }
            guard
              IOSCompanionConsentLedgerPolicy.hasCurrentContactsDisclosure(
                workflow.consentReceipts
              )
            else {
              resolve(
                self.response(
                  revision: status.revision,
                  kind: "ok",
                  payload: Self.contactsAuthorizationRequired()
                ))
              return
            }
          }
          guard let lifecycleCompletion = self.beginLifecycleMutation(
            revision: status.revision,
            completion: resolve
          ) else { return }
          self.executePeopleSync(
            disclosureAcknowledged: intent == "authorize-contacts",
            completion: lifecycleCompletion
          )
          return
        }

        if intent == "preview-diagnostics" {
          guard payload.isEmpty, expectedRevision == nil else {
            resolve(
              self.response(
                revision: status.revision,
                kind: "error",
                payload: Self.internalProblem("NATIVE_REQUEST_INVALID")
              ))
            return
          }
          resolve(
            self.response(
              revision: status.revision,
              kind: "ok",
              payload: self.diagnosticsPreview(status: status)
            ))
          return
        }
        if intent == "share-diagnostics" {
          guard Set(payload.keys) == ["expectedRevision"],
            let revision = Self.payloadRevision(
              payload, expected: expectedRevision
            )
          else {
            resolve(
              self.response(
                revision: status.revision,
                kind: "error",
                payload: Self.internalProblem("NATIVE_REQUEST_INVALID")
              ))
            return
          }
          self.executeDiagnosticsShare(
            expectedRevision: revision,
            completion: resolve
          )
          return
        }

        let workflowIntents: Set<String> = [
          "activate", "choose-birthday", "choose-phone", "confirm-approvals",
          "confirm-enrollment", "confirm-privacy-action", "exclude-recipient",
          "block-recipient-destination", "unblock-recipient-destination",
          "generate-suggestions", "pause-all", "pause-recipient",
          "prepare-activation", "prepare-approvals", "prepare-enrollment-review",
          "prepare-privacy-action", "prepare-resume", "preview-message",
          "preview-policy", "restore-recipient", "resume", "save-message",
          "resume-lifecycle-operation", "save-policy",
        ]
        if workflowIntents.contains(intent) {
          let privacyIntent =
            intent == "prepare-privacy-action"
            || intent == "confirm-privacy-action"
            || intent == "resume-lifecycle-operation"
          let binding =
            self.identity.exactSessionBinding()
            ?? (privacyIntent ? self.peopleStore.currentBinding() : nil)
            ?? (intent == "resume-lifecycle-operation"
              ? status.workflow.map { workflow in
                IOSNativeGoogleAccountBinding(
                  googleSubject: workflow.account.googleSubject,
                  firebaseUID: workflow.account.firebaseUID,
                  displayEmail: "",
                  displayName: nil,
                  accountGeneration: workflow.account.accountGeneration
                )
              } : nil)
          guard let binding else {
            resolve(
              self.response(
                revision: status.revision,
                kind: "error",
                payload: Self.temporarilyUnavailableProblem("account-reconnect-required")
              ))
            return
          }
          let currentReadiness = self.readiness(status: status, context: Self.runtimeContext())
          let requiresLifecycleExclusion =
            intent == "confirm-privacy-action"
            || intent == "resume-lifecycle-operation"
          let workflowCompletion: (NSDictionary) -> Void
          if requiresLifecycleExclusion {
            guard let lifecycleCompletion = self.beginLifecycleMutation(
              revision: status.revision,
              completion: resolve
            ) else { return }
            workflowCompletion = lifecycleCompletion
          } else {
            workflowCompletion = resolve
          }
          self.workflow.execute(
            intent: intent,
            payload: payload,
            binding: binding,
            expectedRevision: expectedRevision,
            status: status,
            readiness: currentReadiness
          ) { [weak self] result in
            self?.finishWorkflowIntent(result, completion: workflowCompletion)
          }
          return
        }

        let context = Self.runtimeContext()
        switch self.intent(
          intent,
          payload: payload,
          status: status,
          context: context
        ) {
        case .success(let value):
          resolve(self.response(revision: status.revision, kind: "ok", payload: value))
        case .failure(let problem):
          resolve(self.response(revision: status.revision, kind: "error", payload: problem))
        }
      }
    }
  }

  @objc(currentRevision:)
  public func currentRevision(_ completion: @escaping (String) -> Void) {
    readStatus { result in
      switch result {
      case .success(let status):
        completion(status.revision)
      case .failure:
        completion("0")
      }
    }
  }

  private func readStatus(
    completion: @escaping (Result<CompanionProjectionStatus, CompanionStoreError>) -> Void
  ) {
    store.readProjectionStatus(completion: completion)
  }

  private func executeRouteProjection(
    request: BirthdayJSON?,
    completion: @escaping (NSDictionary) -> Void
  ) {
    guard let request, request.isEmpty else {
      completion(
        response(
          revision: "0",
          kind: "error",
          payload: Self.internalProblem("NATIVE_REQUEST_INVALID")
        ))
      return
    }
    if Self.accountDeletionStateBlocksOrdinaryIdentity() {
      completion(response(revision: "0", kind: "ok", payload: ["kind": "none"]))
      return
    }
    IOSCompanionNotificationRouter.shared.takeProjection { [weak self] result in
      guard let self else {
        completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      switch result {
      case .failure(let error):
        completion(
          self.response(
            revision: "0", kind: "error",
            payload: Self.internalProblem(error.safeCode)
          ))
      case .success(let payload):
        self.readStatus { statusResult in
          let revision = (try? statusResult.get().revision) ?? "0"
          completion(self.response(revision: revision, kind: "ok", payload: payload))
        }
      }
    }
  }

  private func projection(
    area: String,
    request: BirthdayJSON,
    status: CompanionProjectionStatus,
    context: BirthdayNativeRuntimeContext
  ) -> BirthdayNativePayloadResult {
    switch area {
    case "bootstrap":
      guard request.isEmpty else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      return .success(bootstrap(status: status, context: context))
    case "setup":
      guard request.isEmpty else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      return .success(setup(status: status, context: context))
    case "home":
      guard request.isEmpty else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      guard identity.exactSessionBinding() != nil else {
        return .failure(Self.temporarilyUnavailableProblem("account-reconnect-required"))
      }
      return .success(home(status: status, context: context))
    case "eligibility":
      guard request.isEmpty else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      return .success(eligibility(context: context))
    case "readiness":
      guard request.isEmpty else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      return .success(readiness(status: status, context: context))
    case "account":
      guard request.isEmpty else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      return .success(account(status: status))
    case "contacts":
      guard identity.exactSessionBinding() != nil else {
        return .failure(Self.temporarilyUnavailableProblem("account-reconnect-required"))
      }
      return bridge(workflow.contactsProjection(request: request, status: status))
    case "automation":
      guard identity.exactSessionBinding() != nil else {
        return .failure(Self.temporarilyUnavailableProblem("account-reconnect-required"))
      }
      return automation(request: request, status: status)
    case "activity":
      if request["kind"] as? String != "issues", identity.exactSessionBinding() == nil {
        return .failure(Self.temporarilyUnavailableProblem("account-reconnect-required"))
      }
      return activity(request: request, status: status, context: context)
    case "privacy":
      return privacy(request: request, status: status)
    case "messages":
      guard identity.exactSessionBinding() != nil else {
        return .failure(Self.temporarilyUnavailableProblem("account-reconnect-required"))
      }
      return bridge(workflow.messagesProjection(request: request, status: status))
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  private func intent(
    _ intent: String,
    payload: BirthdayJSON,
    status: CompanionProjectionStatus,
    context: BirthdayNativeRuntimeContext
  ) -> BirthdayNativePayloadResult {
    switch intent {
    case "refresh-compatibility":
      guard payload.isEmpty else { return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID")) }
      return .success(eligibility(context: context))
    case "continue-with-google", "authorize-contacts", "sync-contacts":
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    case "generate-suggestions":
      return .failure(Self.temporarilyUnavailableProblem("coordination-unavailable"))
    case "prepare-test", "start-test":
      return .failure(Self.unsupportedProblem("platform-composer-only"))
    case "activate", "confirm-approvals", "confirm-enrollment",
      "confirm-privacy-action", "confirm-today-occurrence", "choose-birthday",
      "choose-phone", "exclude-recipient", "pause-all", "pause-recipient",
      "block-recipient-destination", "unblock-recipient-destination",
      "perform-native-action", "prepare-activation", "prepare-approvals",
      "prepare-enrollment-review", "prepare-privacy-action", "prepare-resume",
      "prepare-today-occurrence", "preview-diagnostics", "preview-message",
      "preview-policy", "restore-recipient", "resume", "resume-lifecycle-operation", "save-message",
      "save-policy", "share-diagnostics":
      return .failure(Self.temporarilyUnavailableProblem("account-reconnect-required"))
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  private func bootstrap(
    status: CompanionProjectionStatus,
    context: BirthdayNativeRuntimeContext
  ) -> BirthdayJSON {
    let people = peopleStore.projection()
    [
      "capability": Self.capability(),
      "eligibility": eligibility(context: context),
      "account": account(status: status),
      "setupStep": setupStep(context: context, people: people, status: status),
    ]
  }

  private func setup(
    status: CompanionProjectionStatus,
    context: BirthdayNativeRuntimeContext
  ) -> BirthdayJSON {
    let people = peopleStore.projection()
    let currentReadiness = readiness(status: status, context: context)
    return [
      "step": setupStep(context: context, people: people, status: status),
      "eligibility": eligibility(context: context),
      "account": account(status: status),
      "contacts": contactsSyncPayload(people.sync, trustedNow: status.trustedNow),
      "readiness": currentReadiness,
      "automation": workflow.automationPayload(status: status, readiness: currentReadiness),
      "initialActivationCompleted": status.workflow.map {
        $0.hasEverActivatedReminders == true
      } ?? false,
    ]
  }

  private func home(
    status: CompanionProjectionStatus,
    context: BirthdayNativeRuntimeContext
  ) -> BirthdayJSON {
    let people = peopleStore.projection()
    let currentReadiness = readiness(status: status, context: context)
    let metrics = workflow.homeMetrics(status: status)
    var counts = metrics
    let next = counts.removeValue(forKey: "next")
    var value: BirthdayJSON = [
      "automation": workflow.automationPayload(status: status, readiness: currentReadiness),
      "counts": counts,
      "contactsSync": contactsSyncPayload(people.sync, trustedNow: status.trustedNow),
    ]
    if let next { value["next"] = next }
    if let reconciledAt = status.lastReminderReconciledAt {
      value["schedulerHeartbeatAt"] = Self.dateString(reconciledAt)
    }
    if let coordinatedAt = status.workflow?.lastCoordinationSuccessAt {
      value["lastCoordinationSuccessAt"] = Self.dateString(coordinatedAt)
    }
    return value
  }

  private func eligibility(context: BirthdayNativeRuntimeContext) -> BirthdayJSON {
    if context.messageComposerIsAvailable {
      return [
        "kind": "supported",
        "capability": Self.capability(),
        "channelLabel": "iOS Companion",
        "chargeDisclosureVersion": "user-controlled-system-composer-v1",
      ]
    }
    return [
      "kind": "limited",
      "capability": Self.capability(),
      "primaryIssue": Self.issue(
        id: "eligibility-message-composer-unavailable",
        code: "platform-unsupported"
      ),
      "otherIssues": [],
    ]
  }

  private func readiness(
    status: CompanionProjectionStatus,
    context: BirthdayNativeRuntimeContext
  ) -> BirthdayJSON {
    var issues: [BirthdayJSON] = []
    guard case .connected = identity.state else {
      issues.append(
        Self.issue(
          id: "readiness-account-required",
          code: "account-reconnect-required"
        ))
      return readinessPayload(status: status, context: context, issues: issues)
    }
    let peopleSync = peopleStore.projection().sync
    switch peopleSync {
    case .authorizationRequired:
      issues.append(
        Self.issue(
          id: "readiness-contacts-authorization",
          code: "contacts-authorization-required"
        ))
    default:
      let assessment = contactsFreshnessAssessment(
        peopleSync,
        trustedNow: status.trustedNow
      )
      if assessment.allowsCompanionAction { break }
      issues.append(Self.issue(id: "readiness-contacts-stale", code: "contacts-stale"))
    }

    return readinessPayload(status: status, context: context, issues: issues)
  }

  private func readinessPayload(
    status: CompanionProjectionStatus,
    context: BirthdayNativeRuntimeContext,
    issues initialIssues: [BirthdayJSON]
  ) -> BirthdayJSON {
    var issues = initialIssues

    if let operation = status.workflow?.privacyOperations.max(by: {
      $0.updatedAt < $1.updatedAt
    }), !["complete", "failed"].contains(operation.phase) {
      issues.append(
        Self.issue(
          id: "readiness-account-cleanup-pending",
          code: operation.action == "delete-account"
            ? "firebase-account-deleting" : "coordination-unavailable"
        ))
    }

    if !context.messageComposerIsAvailable {
      issues.append(
        Self.issue(
          id: "readiness-message-composer-unavailable",
          code: "platform-unsupported"
        ))
    }
    if !context.applicationIsActive {
      issues.append(
        Self.issue(
          id: "readiness-foreground-required",
          code: "native-bridge-unavailable"
        ))
    }
    if !context.protectedDataIsAvailable {
      issues.append(
        Self.issue(
          id: "readiness-protected-data-unavailable",
          code: "native-bridge-unavailable"
        ))
    }
    if status.resetSafetyRequiresRelease {
      issues.append(
        Self.issue(
          id: status.resetSafetyOverflowed
            ? "readiness-reset-safety-overflow"
            : "readiness-reset-safety-blocked",
          code: status.resetSafetyOverflowed
            ? "reset-safety-overflow"
            : "reset-safety-blocked"
        ))
    }
    switch status.coexistence {
    case .clear:
      break
    case .deleting:
      issues.append(
        Self.issue(
          id: "readiness-account-deleting",
          code: "firebase-account-deleting"
        ))
    case .managed:
      issues.append(
        Self.issue(
          id: "readiness-managed-by-android",
          code: "active-sender-other-device"
        ))
    case .staleOrUnknown, .unavailable:
      issues.append(
        Self.issue(
          id: "readiness-companion-status-unverified",
          code: "coordination-unavailable"
        ))
    }

    let composer: BirthdayJSON =
      issues.isEmpty
      ? ["kind": "allowed"]
      : ["kind": "blocked", "issues": issues]
    return [
      "platform": "ios",
      "composer": composer,
      "unattendedAutomation": [
        "kind": "unavailable",
        "reason": "platform-composer-only",
      ],
      "lastCheckedAt": Self.dateString(Date()),
    ]
  }

  private func account(status: CompanionProjectionStatus) -> BirthdayJSON {
    if IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
      || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
    {
      return [
        "kind": "cleanup-pending",
        "operation": "delete",
        "issue": Self.issue(
          id: "account-deletion-cleanup-pending",
          code: "firebase-account-deleting"
        ),
      ]
    }
    if let operation = status.workflow?.privacyOperations.max(by: {
      $0.updatedAt < $1.updatedAt
    }), !["complete", "failed"].contains(operation.phase) {
      let accountOperation: String
      switch operation.action {
      case "disconnect-contacts": accountOperation = "disconnect"
      case "revoke-google-access": accountOperation = "revoke"
      case "delete-account": accountOperation = "delete"
      default: accountOperation = "sign-out"
      }
      return [
        "kind": "cleanup-pending",
        "operation": accountOperation,
        "issue": Self.issue(
          id: "account-lifecycle-cleanup-pending",
          code: operation.action == "delete-account"
            ? "firebase-account-deleting" : "coordination-unavailable"
        ),
      ]
    }
    switch identity.state {
    case .connecting:
      return ["kind": "connecting"]
    case .connected(let email):
      return [
        "kind": "connected",
        "displayEmail": email,
        "sender": [
          "platform": "ios",
          "kind": "companion",
          "unattendedAutomation": "unavailable",
          "composer": "available",
        ],
      ]
    case .reconnectRequired, .unavailable:
      return [
        "kind": "reconnect-required",
        "issue": Self.issue(
          id: "account-reconnect-required",
          code: "account-reconnect-required"
        ),
      ]
    case .signedOut(let retained):
      return [
        "kind": "signed-out",
        "retainedSetup": (retained || status.retainedSetupExists)
          ? "same-account-only" : "none",
      ]
    }
  }

  private func automation(
    request: BirthdayJSON,
    status: CompanionProjectionStatus
  ) -> BirthdayNativePayloadResult {
    switch request["kind"] as? String {
    case "policy-editor":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      return bridge(workflow.policyEditorProjection(status: status))
    case "approval":
      guard Set(request.keys) == Set(["contactId", "kind"]),
        let contactId = request["contactId"] as? String,
        Self.isValidOpaqueIdentifier(contactId)
      else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      return bridge(workflow.approvalProjection(contactId: contactId, status: status))
    case "latest-test":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      return .success([
        "platform": "ios",
        "kind": "unavailable",
        "reason": "platform-composer-only",
      ])
    case "birthday-job":
      guard Set(request.keys) == Set(["kind", "occurrenceId"]),
        let occurrenceId = request["occurrenceId"] as? String,
        Self.isValidOpaqueIdentifier(occurrenceId)
      else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      return bridge(
        workflow.birthdayJobProjection(
          occurrenceId: occurrenceId,
          status: status
        ))
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  private func activity(
    request: BirthdayJSON,
    status: CompanionProjectionStatus,
    context: BirthdayNativeRuntimeContext
  ) -> BirthdayNativePayloadResult {
    switch request["kind"] as? String {
    case "issues":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      let currentReadiness = readiness(status: status, context: context)
      guard let composer = currentReadiness["composer"] as? BirthdayJSON else {
        return .failure(Self.internalProblem("NATIVE_CONTRACT_INVALID"))
      }
      if composer["kind"] as? String == "allowed" { return .success([]) }
      guard let issues = composer["issues"] as? [BirthdayJSON] else {
        return .failure(Self.internalProblem("NATIVE_CONTRACT_INVALID"))
      }
      return .success(issues)
    case "list":
      let currentReadiness = readiness(status: status, context: context)
      guard let composer = currentReadiness["composer"] as? BirthdayJSON,
        let composerKind = composer["kind"] as? String
      else {
        return .failure(Self.internalProblem("NATIVE_CONTRACT_INVALID"))
      }
      let currentIssueCodes: Set<String>
      if composerKind == "allowed" {
        currentIssueCodes = []
      } else {
        guard composerKind == "blocked",
          let issues = composer["issues"] as? [BirthdayJSON]
        else {
          return .failure(Self.internalProblem("NATIVE_CONTRACT_INVALID"))
        }
        currentIssueCodes = Set(issues.compactMap { $0["code"] as? String })
      }
      return bridge(
        workflow.activityProjection(
          request: request,
          status: status,
          currentIssueCodes: currentIssueCodes
        ))
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  private func privacy(
    request: BirthdayJSON,
    status: CompanionProjectionStatus
  ) -> BirthdayNativePayloadResult {
    switch request["kind"] as? String {
    case "inventory":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      return .success(workflow.privacyInventory(status: status))
    case "operation":
      guard Set(request.keys) == Set(["kind", "operationId"]),
        let operationId = request["operationId"] as? String,
        Self.isValidOpaqueIdentifier(operationId)
      else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      return bridge(
        workflow.privacyOperationProjection(
          operationId: operationId,
          status: status
        ))
    case "current-operation":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      return bridge(workflow.currentPrivacyOperationProjection(status: status))
    case "latest-deletion-receipt":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      guard let receipt = IOSAccountDeletionReceiptStore.shared.current() else {
        return Self.accountDeletionStateBlocksOrdinaryIdentity()
          ? .success([
            "kind": "unavailable",
            "reason": "coordination-unavailable",
          ])
          : .success(["kind": "none"])
      }
      guard receipt.localDataErased else {
        return .success([
          "kind": "unavailable",
          "reason": "coordination-unavailable",
        ])
      }
      if receipt.remoteDeletionComplete,
        IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
      {
        return .success([
          "kind": "unavailable",
          "reason": "coordination-unavailable",
        ])
      }
      if !receipt.remoteDeletionComplete,
        let recovery = IOSAccountDeletionRecoveryStore.shared.current(),
        recovery.operationId == receipt.operationId
      {
        return recovery.remoteAcceptanceConfirmed
          ? .success(
            IOSCompanionWorkflowEngine.accountDeletionReceiptPayload(receipt)
          )
          : .success(
            IOSCompanionWorkflowEngine.accountDeletionRecoveryUnknownPayload(
              receipt,
              sameAccountRetryAvailable: recovery.retryAuthorized
            ))
      }
      if !receipt.remoteDeletionComplete,
        IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
      {
        return .success([
          "kind": "unavailable",
          "reason": "coordination-unavailable",
        ])
      }
      return .success(IOSCompanionWorkflowEngine.accountDeletionReceiptPayload(receipt))
    case "public-resources":
      guard request.keys.count == 1 else {
        return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
      }
      return .success(Self.publicResourcesPayload())
    default:
      return .failure(Self.internalProblem("NATIVE_REQUEST_INVALID"))
    }
  }

  private static func publicResourcesPayload() -> BirthdayJSON {
    let version = safeDiagnosticLabel(
      Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String,
      fallback: "unknown"
    )
    let build = safeDiagnosticLabel(
      Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String,
      fallback: "unknown"
    )
    let buildLabel = safeDiagnosticLabel(
      "Birthday Autopilot \(version) (\(build))",
      fallback: "Birthday Autopilot"
    )
    guard let projectId = FirebaseApp.app()?.options.projectID,
      projectId.range(
        of: "^[a-z][a-z0-9-]{4,28}[a-z0-9]$",
        options: .regularExpression
      ) != nil
    else {
      return ["kind": "unavailable", "buildLabel": buildLabel]
    }
    return [
      "kind": "available",
      "buildLabel": buildLabel,
      "baseUrl": "https://\(projectId).web.app",
    ]
  }

  private func executeGoogleIdentity(
    completion: @escaping (NSDictionary) -> Void
  ) {
    identity.continueWithGoogle { [weak self] outcome in
      guard let self else {
        completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      switch outcome {
      case .connected:
        self.finishAsyncIntent(
          kind: "ok", payload: self.accountFromCurrentState(), completion: completion)
      case .failed(let failure):
        self.finishAsyncIntent(
          kind: "error",
          payload: Self.identityProblem(failure),
          completion: completion
        )
      }
    }
  }

  private func executeGoogleDeletionRecovery(
    completion: @escaping (NSDictionary) -> Void
  ) {
    identity.continueAccountDeletionRecoveryWithGoogle { [weak self] outcome in
      guard let self else {
        completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      switch outcome {
      case .submitted:
        self.finishAsyncIntent(
          kind: "ok",
          payload: self.accountFromCurrentState(),
          completion: completion
        )
      case .unavailable:
        self.finishAsyncIntent(
          kind: "error",
          payload: Self.temporarilyUnavailableProblem(
            "coordination-unavailable"
          ),
          completion: completion
        )
      case .failed(let failure):
        self.finishAsyncIntent(
          kind: "error",
          payload: Self.identityProblem(failure),
          completion: completion
        )
      }
    }
  }

  private func executePeopleSync(
    disclosureAcknowledged: Bool,
    completion: @escaping (NSDictionary) -> Void
  ) {
    // Re-read immediately before lifting the durable privacy suspension. The
    // original bridge snapshot may have gone stale while another main-actor
    // lifecycle intent committed its reviewed operation.
    store.readProjectionStatus { [weak self] result in
      guard let self else {
        completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      guard case .success(let status) = result,
        status.workflow?.privacyOperations.contains(where: {
          !["complete", "failed"].contains($0.phase)
        }) != true
      else {
        self.finishAsyncIntent(
          kind: "error",
          payload: Self.temporarilyUnavailableProblem("coordination-unavailable"),
          completion: completion
        )
        return
      }
      self.executePeopleSyncAfterPrivacyRecheck(
        disclosureAcknowledged: disclosureAcknowledged,
        trustedNow: status.trustedNow,
        completion: completion
      )
    }
  }

  private func executePeopleSyncAfterPrivacyRecheck(
    disclosureAcknowledged: Bool,
    trustedNow: Date?,
    completion: @escaping (NSDictionary) -> Void
  ) {
    IOSPeopleBackgroundRefreshCoordinator.shared.resumeAfterExplicitContactsAction()
    peopleSync.sync(interactiveAuthorization: true) { [weak self] outcome in
      guard let self else {
        completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      switch outcome {
      case .completed:
        guard let binding = self.identity.exactSessionBinding() else {
          self.finishAsyncIntent(
            kind: "error",
            payload: Self.temporarilyUnavailableProblem("account-reconnect-required"),
            completion: completion
          )
          return
        }
        self.workflow.recordContactsConsent(
          binding: binding,
          disclosureAcknowledged: disclosureAcknowledged
        ) { recorded in
          guard recorded else {
            self.finishAsyncIntent(
              kind: "error",
              payload: Self.internalProblem("COMPANION_STORAGE_UNAVAILABLE"),
              completion: completion
            )
            return
          }
          self.workflow.reconcileAfterPeopleSync(binding: binding) {
            self.finishAsyncIntent(
              kind: "ok",
              payload: self.contactsSyncPayload(
                self.peopleStore.projection().sync,
                trustedNow: nil,
                freshSyncJustCompleted: true
              ),
              completion: completion
            )
          }
        }
      case .failed(let failure):
        switch failure {
        case .cancelled:
          self.finishAsyncIntent(
            kind: "error",
            payload: ["kind": "cancelled", "source": "user"],
            completion: completion
          )
        case .networkOffline:
          self.finishAsyncIntent(
            kind: "error",
            payload: Self.temporarilyUnavailableProblem("network-offline"),
            completion: completion
          )
        case .rateLimited(let retryAfter):
          var problem = Self.temporarilyUnavailableProblem("contacts-stale")
          if let retryAfter { problem["retryAfterSeconds"] = retryAfter }
          self.finishAsyncIntent(kind: "error", payload: problem, completion: completion)
        default:
          self.finishAsyncIntent(
            kind: "ok",
            payload: self.contactsSyncPayload(
              self.peopleStore.projection().sync,
              trustedNow: trustedNow
            ),
            completion: completion
          )
        }
      }
    }
  }

  private func diagnosticsPreview(
    status: CompanionProjectionStatus
  ) -> BirthdayJSON {
    let currentReadiness = readiness(status: status, context: Self.runtimeContext())
    var capabilityCodes = ["platform-composer-only"]
    if let composer = currentReadiness["composer"] as? BirthdayJSON,
      let issues = composer["issues"] as? [BirthdayJSON]
    {
      capabilityCodes.append(contentsOf: issues.compactMap { $0["code"] as? String })
    }
    capabilityCodes = Array(Set(capabilityCodes)).sorted()

    let workflowActivities = status.workflow?.activity ?? []
    let cutoff = status.workflow?.activityClearedAt
    let composerRecords = status.composerRecords.filter { record in
      guard let cutoff else { return true }
      return (record.resolvedAt ?? record.openedAt) > cutoff
    }
    var eventDates = workflowActivities.map(\.occurredAt)
    var transitionCount = workflowActivities.count
    for record in composerRecords {
      eventDates.append(record.openedAt)
      transitionCount += 1
      if let resolvedAt = record.resolvedAt {
        eventDates.append(resolvedAt)
        transitionCount += 1
      }
    }

    let version = Self.safeDiagnosticLabel(
      Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String,
      fallback: "unknown"
    )
    let build = Self.safeDiagnosticLabel(
      Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String,
      fallback: "unknown"
    )
    let system = Self.safeDiagnosticLabel(
      UIDevice.current.systemVersion,
      fallback: "unknown"
    )
    var value: BirthdayJSON = [
      "buildLabel": Self.safeDiagnosticLabel(
        "Birthday Autopilot \(version) (\(build))", fallback: "Birthday Autopilot"
      ),
      "androidOrIosVersionLabel": Self.safeDiagnosticLabel(
        "iOS \(system)", fallback: "iOS"
      ),
      "capabilityCodes": Array(capabilityCodes.prefix(64)),
      "transitionCount": min(transitionCount, 1_000_000),
      "excludesPrivateContent": true,
    ]
    if let earliest = eventDates.min() {
      value["earliestEventAt"] = Self.dateString(earliest)
    }
    if let latest = eventDates.max() {
      value["latestEventAt"] = Self.dateString(latest)
    }
    return value
  }

  private func executeDiagnosticsShare(
    expectedRevision: String,
    completion: @escaping (NSDictionary) -> Void
  ) {
    guard !diagnosticsShareInProgress else {
      completion(
        response(
          revision: expectedRevision,
          kind: "error",
          payload: Self.temporarilyUnavailableProblem("native-bridge-unavailable")
        ))
      return
    }
    readStatus { [weak self] result in
      guard let self else {
        completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      guard case .success(let status) = result else {
        completion(
          self.response(
            revision: "0", kind: "error",
            payload: Self.temporarilyUnavailableProblem("native-bridge-unavailable")
          ))
        return
      }
      guard status.revision == expectedRevision else {
        completion(
          self.response(
            revision: status.revision, kind: "error",
            payload: ["kind": "stale-revision", "latestRevision": status.revision]
          ))
        return
      }
      guard UIApplication.shared.applicationState == .active,
        let presenter = self.identity.foregroundViewController()
      else {
        completion(
          self.response(
            revision: status.revision, kind: "error",
            payload: Self.temporarilyUnavailableProblem("native-bridge-unavailable")
          ))
        return
      }
      let preview = self.diagnosticsPreview(status: status)
      let lines = [
        "Birthday Autopilot diagnostics",
        "Build: \(preview["buildLabel"] as? String ?? "unknown")",
        "System: \(preview["androidOrIosVersionLabel"] as? String ?? "unknown")",
        "Capabilities: \((preview["capabilityCodes"] as? [String] ?? []).joined(separator: ", "))",
        "Transitions: \(preview["transitionCount"] as? Int ?? 0)",
        "Earliest event: \(preview["earliestEventAt"] as? String ?? "none")",
        "Latest event: \(preview["latestEventAt"] as? String ?? "none")",
        "Private content excluded: yes",
      ]
      let controller = UIActivityViewController(
        activityItems: [lines.joined(separator: "\n")],
        applicationActivities: nil
      )
      if let popover = controller.popoverPresentationController {
        popover.sourceView = presenter.view
        popover.sourceRect = CGRect(
          x: presenter.view.bounds.midX,
          y: presenter.view.bounds.midY,
          width: 1,
          height: 1
        )
      }
      self.diagnosticsShareInProgress = true
      controller.completionWithItemsHandler = { [weak self] _, completed, _, error in
        guard let self, self.diagnosticsShareInProgress else { return }
        self.diagnosticsShareInProgress = false
        if error != nil {
          self.finishAsyncIntent(
            kind: "error",
            payload: Self.temporarilyUnavailableProblem("native-bridge-unavailable"),
            completion: completion
          )
        } else {
          self.finishAsyncIntent(
            kind: "ok",
            payload: ["kind": completed ? "shared" : "cancelled"],
            completion: completion
          )
        }
      }
      presenter.present(controller, animated: true) { [weak self, weak controller] in
        guard let self, self.diagnosticsShareInProgress,
          controller?.presentingViewController != nil
        else {
          guard let self, self.diagnosticsShareInProgress else { return }
          self.diagnosticsShareInProgress = false
          self.finishAsyncIntent(
            kind: "error",
            payload: Self.temporarilyUnavailableProblem("native-bridge-unavailable"),
            completion: completion
          )
          return
        }
      }
    }
  }

  private func finishAsyncIntent(
    kind: String,
    payload: BirthdayJSON,
    completion: @escaping (NSDictionary) -> Void
  ) {
    readStatus { [weak self] result in
      guard let self else {
        completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
        return
      }
      let revision = (try? result.get().revision) ?? "0"
      completion(self.response(revision: revision, kind: kind, payload: payload))
    }
  }

  /// Serializes account selection, foreground Contacts mutation, and reviewed
  /// privacy execution. Store reads are asynchronous, so this main-actor lease
  /// closes the stale-snapshot window between their final gate and SDK work.
  private func beginLifecycleMutation(
    revision: String,
    completion: @escaping (NSDictionary) -> Void
  ) -> ((NSDictionary) -> Void)? {
    guard !lifecycleMutationInProgress else {
      completion(
        response(
          revision: revision,
          kind: "error",
          payload: Self.temporarilyUnavailableProblem("coordination-unavailable")
        ))
      return nil
    }
    lifecycleMutationInProgress = true
    return { [weak self] response in
      self?.lifecycleMutationInProgress = false
      completion(response)
    }
  }

  private func finishWorkflowIntent(
    _ result: IOSCompanionWorkflowEngineResult,
    completion: @escaping (NSDictionary) -> Void
  ) {
    switch result {
    case .success(let payload):
      readStatus { [weak self] statusResult in
        guard let self else {
          completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
          return
        }
        let revision = (try? statusResult.get().revision) ?? "0"
        completion(self.response(revision: revision, kind: "ok", payload: payload))
      }
    case .failure(let problem):
      readStatus { [weak self] statusResult in
        guard let self else {
          completion(Self.fallbackInternalResponse(code: "NATIVE_BRIDGE_UNAVAILABLE"))
          return
        }
        let revision = (try? statusResult.get().revision) ?? "0"
        completion(self.response(revision: revision, kind: "error", payload: problem))
      }
    }
  }

  private func bridge(
    _ result: IOSCompanionWorkflowEngineResult
  ) -> BirthdayNativePayloadResult {
    switch result {
    case .success(let payload): return .success(payload)
    case .failure(let problem): return .failure(problem)
    }
  }

  private func accountFromCurrentState() -> BirthdayJSON {
    if Self.accountDeletionStateBlocksOrdinaryIdentity() {
      return [
        "kind": "cleanup-pending",
        "operation": "delete",
        "issue": Self.issue(
          id: "account-deletion-cleanup-pending",
          code: "firebase-account-deleting"
        ),
      ]
    }
    switch identity.state {
    case .connected(let email):
      return [
        "kind": "connected",
        "displayEmail": email,
        "sender": [
          "platform": "ios", "kind": "companion",
          "unattendedAutomation": "unavailable", "composer": "available",
        ],
      ]
    default:
      return [
        "kind": "reconnect-required",
        "issue": Self.issue(
          id: "account-reconnect-required",
          code: "account-reconnect-required"
        ),
      ]
    }
  }

  private func setupStep(
    context _: BirthdayNativeRuntimeContext,
    people: IOSPeopleSafeProjection,
    status _: CompanionProjectionStatus
  ) -> String {
    guard case .connected = identity.state else { return "google-account" }
    switch people.sync {
    case .neverSynced:
      return "contacts-disclosure"
    case .authorizationRequired:
      return peopleStore.hasCompletedSyncGeneration()
        ? "complete" : "contacts-disclosure"
    case .syncing(_, let retainedGeneration):
      return retainedGeneration ? "complete" : "sync-summary"
    case .failedRetained(let lastSuccess, _):
      return lastSuccess == nil ? "sync-summary" : "complete"
    case .fresh:
      // Setup owns only compatibility, exact account binding, disclosure, and
      // authoritative People sync. Recipient/message/policy configuration lives
      // in the full People, Message, and Automation surfaces; holding the app
      // inside setup here would make those controls unreachable.
      return "complete"
    }
  }

  private func contactsSyncPayload(
    _ state: IOSPeopleSafeSyncState,
    trustedNow: Date?,
    freshSyncJustCompleted: Bool = false
  ) -> BirthdayJSON {
    switch state {
    case .authorizationRequired:
      return Self.contactsAuthorizationRequired()
    case .neverSynced:
      return ["kind": "never-synced"]
    case .syncing(let mode, let retained):
      return [
        "kind": "syncing", "mode": mode.rawValue,
        "retainedGeneration": retained,
      ]
    case .fresh(let completedAt, let count) where freshSyncJustCompleted:
      return [
        "kind": "fresh", "completedAt": Self.dateString(completedAt),
        "contactCount": count,
      ]
    case .fresh(_, _), .failedRetained(_, _):
      let assessment = contactsFreshnessAssessment(state, trustedNow: trustedNow)
      if assessment.band == .normal {
        switch state {
        case .fresh(let completedAt, let count):
          return [
            "kind": "fresh", "completedAt": Self.dateString(completedAt),
            "contactCount": count,
          ]
        case .failedRetained(let lastSuccess, let reason):
          var value: BirthdayJSON = [
            "kind": "failed-retained",
            "reason": Self.safeSyncReason(reason),
          ]
          if let lastSuccess { value["lastSuccessAt"] = Self.dateString(lastSuccess) }
          return value
        default:
          return ["kind": "failed-retained", "reason": "contacts-stale"]
        }
      }
      if let lastSuccess = assessment.lastSuccessAt {
        return [
          "kind": "stale",
          "lastSuccessAt": Self.dateString(lastSuccess),
          "reason": state.failureReason.map(Self.safeSyncReason) ?? "contacts-stale",
        ]
      }
      let value: BirthdayJSON = [
        "kind": "failed-retained",
        "reason": state.failureReason.map(Self.safeSyncReason) ?? "contacts-stale",
      ]
      return value
    }
  }

  private func contactsFreshnessAssessment(
    _ state: IOSPeopleSafeSyncState,
    trustedNow: Date?
  ) -> IOSContactsFreshnessAssessment {
    let source: IOSContactsFreshnessSourceState
    let lastSuccess: Date?
    switch state {
    case .fresh(let completedAt, _):
      source = .verified
      lastSuccess = completedAt
    case .failedRetained(let retainedAt, _):
      source = .retainedAfterFailure
      lastSuccess = retainedAt
    case .authorizationRequired:
      source = .authorizationRequired
      lastSuccess = nil
    case .neverSynced, .syncing(_, _):
      source = .unavailable
      lastSuccess = nil
    }
    return IOSContactsFreshnessPolicy.assess(
      sourceState: source,
      lastSuccessAt: lastSuccess,
      trustedNow: trustedNow
    )
  }

  private static func safeSyncReason(_ reason: String) -> String {
    switch reason {
    case "network-offline": return "network-offline"
    case "contacts-authorization-required": return "contacts-authorization-required"
    case "contacts-partial-sync": return "contacts-partial-sync"
    default: return "contacts-stale"
    }
  }

  private static func identityProblem(_ failure: IOSGoogleIdentityFailure) -> BirthdayJSON {
    switch failure {
    case .cancelled: return ["kind": "cancelled", "source": "user"]
    case .networkOffline: return temporarilyUnavailableProblem("network-offline")
    case .accountMismatch: return ["kind": "conflict", "code": "account-mismatch"]
    case .firebaseUserDisabled: return ["kind": "conflict", "code": "account-disabled"]
    case .presenterUnavailable, .reconnectRequired:
      return temporarilyUnavailableProblem("account-reconnect-required")
    case .appCheckUnavailable, .configurationUnavailable, .internalFailure:
      return internalProblem("IDENTITY_CONFIGURATION_UNAVAILABLE")
    }
  }

  private func response(
    revision: String,
    kind: String,
    payload: Any
  ) -> NSDictionary {
    guard let payloadJson = Self.jsonString(payload) else {
      return Self.fallbackInternalResponse(code: "NATIVE_CONTRACT_INVALID")
    }
    return [
      "contractVersion": Self.contractVersion,
      "revision": revision,
      "generatedAt": Self.dateString(Date()),
      "kind": kind,
      "payloadJson": payloadJson,
    ]
  }

  private static func runtimeContext() -> BirthdayNativeRuntimeContext {
    dispatchPrecondition(condition: .onQueue(.main))
    return BirthdayNativeRuntimeContext(
      applicationIsActive: UIApplication.shared.applicationState == .active,
      protectedDataIsAvailable: UIApplication.shared.isProtectedDataAvailable,
      messageComposerIsAvailable: MFMessageComposeViewController.canSendText()
    )
  }

  private static func accountDeletionStateBlocksOrdinaryIdentity() -> Bool {
    IOSAccountDeletionReceiptStore.shared.hasPendingOrUnreadableReceipt()
      || IOSAccountDeletionRecoveryStore.shared.hasPendingOrUnreadableJournal()
  }

  private static func capability() -> BirthdayJSON {
    [
      "platform": "ios",
      "deliveryMode": "user-controlled-composer",
      "unattendedSms": "unavailable",
      "userComposer": "required",
    ]
  }

  private static func contactsAuthorizationRequired() -> BirthdayJSON {
    [
      "kind": "authorization-required",
      "reason": "contacts-authorization-required",
    ]
  }

  private static func issue(id: String, code: String) -> BirthdayJSON {
    [
      "id": id,
      "code": code,
      "severity": "blocking",
      "blocks": ["composer"],
    ]
  }

  private static func internalProblem(_ code: String) -> BirthdayJSON {
    ["kind": "internal", "supportCode": code]
  }

  private static func unsupportedProblem(_ code: String) -> BirthdayJSON {
    ["kind": "unsupported", "code": code]
  }

  private static func temporarilyUnavailableProblem(_ code: String) -> BirthdayJSON {
    ["kind": "temporarily-unavailable", "code": code]
  }

  private static func parseObject(_ raw: String) -> BirthdayJSON? {
    guard raw.utf8.count <= maximumRequestBytes,
      let data = raw.data(using: .utf8),
      let value = try? JSONSerialization.jsonObject(with: data),
      let object = value as? BirthdayJSON
    else {
      return nil
    }
    return object
  }

  private static func jsonString(_ value: Any) -> String? {
    guard JSONSerialization.isValidJSONObject(value),
      let data = try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
    else {
      return nil
    }
    return String(data: data, encoding: .utf8)
  }

  private static func isValidRevision(_ value: String) -> Bool {
    let range = NSRange(value.startIndex..<value.endIndex, in: value)
    return revisionPattern.firstMatch(in: value, range: range)?.range == range
  }

  private static func isValidOpaqueIdentifier(_ value: String) -> Bool {
    value.range(
      of: "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$",
      options: .regularExpression
    ) != nil
  }

  private static func payloadRevision(
    _ payload: BirthdayJSON,
    expected: String?
  ) -> String? {
    guard let value = payload["expectedRevision"] as? String,
      value == expected, isValidRevision(value)
    else { return nil }
    return value
  }

  private static func safeDiagnosticLabel(
    _ raw: String?,
    fallback: String
  ) -> String {
    guard let raw else { return fallback }
    let bidiControls = CharacterSet(
      charactersIn:
        "\u{061C}\u{200E}\u{200F}\u{202A}\u{202B}\u{202C}\u{202D}\u{202E}"
        + "\u{2066}\u{2067}\u{2068}\u{2069}")
    let value = raw.unicodeScalars.filter {
      !CharacterSet.controlCharacters.contains($0) && !bidiControls.contains($0)
    }.map(String.init).joined().trimmingCharacters(in: .whitespacesAndNewlines)
    guard !value.isEmpty else { return fallback }
    return String(value.prefix(64))
  }

  private static func dateString(_ date: Date) -> String {
    iso8601Formatter.string(from: date)
  }

  private static func fallbackInternalResponse(code: String) -> NSDictionary {
    let payload = "{\"kind\":\"internal\",\"supportCode\":\"\(code)\"}"
    return [
      "contractVersion": contractVersion,
      "revision": "0",
      "generatedAt": dateString(Date()),
      "kind": "error",
      "payloadJson": payload,
    ]
  }
}
