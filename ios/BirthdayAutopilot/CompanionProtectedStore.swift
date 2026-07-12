import CryptoKit
import Foundation
import Security

enum CompanionComposerOutcome: String, Codable, Hashable {
  case openCommitted = "open-committed"
  case presented
  case cancelled
  case failed
  case outcomeUnknown = "outcome-unknown"
  case reportedSent = "reported-sent"

  var preventsRepeat: Bool {
    self == .openCommitted || self == .presented || self == .outcomeUnknown
      || self == .reportedSent
  }
}

enum CompanionProposalState: String, Codable, Equatable {
  case ready
  case openCommitted = "open-committed"
  case presented
  case cancelled
  case failed
  case outcomeUnknown = "outcome-unknown"
  case reportedSent = "reported-sent"
}

enum CompanionAndroidCoexistence: String, Codable {
  case clear
  case deleting
  case managed
  case unknown
}

struct CompanionComposerRecord: Codable {
  let operationId: String
  let proposalId: String
  let occurrenceId: String
  let occurrenceCivilDate: String
  let openedAt: Date
  var outcome: CompanionComposerOutcome
  var resolvedAt: Date?
}

struct CompanionApprovedProposal: Codable {
  let proposalId: String
  var revision: String
  let accountGeneration: String
  let occurrenceId: String
  let occurrenceCivilDate: String
  let recipient: String
  let body: String
  var state: CompanionProposalState
  var reviewNonceDigest: Data?
  var reviewNonceExpiresAt: Date?
  var reviewSessionGeneration: String?
  var reviewSceneIdentifier: String?
  var operationId: String?
}

struct CompanionControlState: Codable {
  let accountGeneration: String
  let androidCoexistence: CompanionAndroidCoexistence
  let checkedAt: Date
  /// Present only when it came from a schema-validated backend response.
  /// Local receipt time is never promoted to trusted server time.
  let trustedServerTime: Date?
}

struct CompanionResetSafety: Codable {
  let generation: String
  var blockedCivilDates: [String]
  var overflowed: Bool
  // Set once after an authenticated server-time response verifies the local
  // clock. Subsequent days do not extend the reset fence indefinitely.
  var verifiedCivilDate: String?

  var requiresRelease: Bool {
    overflowed || !blockedCivilDates.isEmpty
  }
}

struct CompanionReminderPlan: Codable, Equatable {
  let occurrenceId: String
  let civilDate: String
  let hour: Int
  let minute: Int
}

struct CompanionNotificationIdentity: Codable, Equatable {
  let civilDate: String
  let requestId: String
}

struct IOSCompanionNativeRoute: Equatable {
  let routeId: String

  var projection: [String: Any] {
    [
      "kind": "automation-review",
      "routeId": routeId,
      "source": "birthday-reminder",
    ]
  }
}

private struct IOSCompanionPendingNativeRoute: Codable {
  let routeId: String
  let createdAt: Date
}

struct CompanionReminderSchedule {
  let plans: [CompanionReminderPlan]
  let requestIdByCivilDate: [String: String]
  let horizon: CompanionReminderHorizon?
}

enum CompanionReminderHorizonState: String, Codable, Equatable {
  case denied
  case full
  case partial
}

struct CompanionReminderHorizon: Codable {
  let generation: String
  let state: CompanionReminderHorizonState
  let observedRequestIds: [String]
  let earliestUnscheduledCivilDate: String?
  let reconciledAt: Date
}

struct CompanionReviewProjection {
  let proposalId: String
  let revision: String
  let recipient: String
  let body: String
  let actionNonce: String
  let expiresAt: Date
}

struct CompanionPresentationCommit {
  let operationId: String
  let proposalId: String
  let recipient: String
  let body: String
}

/// Native aggregate used to construct content-minimized React Native
/// projections. The bridge methods below must explicitly project only the
/// fields permitted by their TypeScript schemas.
struct CompanionProjectionStatus {
  enum Coexistence {
    case clear
    case deleting
    case managed
    case staleOrUnknown
    case unavailable
  }

  let revision: String
  let retainedSetupExists: Bool
  let approvedProposalCount: Int
  let composerRecordCount: Int
  let localStorageBytes: Int
  let resetSafetyRequiresRelease: Bool
  let resetSafetyOverflowed: Bool
  let resetSafetyVerified: Bool
  let coexistence: Coexistence
  /// Recent authenticated server time advanced by at most the bounded control-observation age.
  /// Nil is deliberately different from the device wall clock and always fails freshness closed.
  let trustedNow: Date?
  let lastReminderReconciledAt: Date?
  let reminderHorizonState: CompanionReminderHorizonState?
  // Native-only workflow data used to build validated React Native
  // projections. Provider IDs, destinations, and proposal bodies are never
  // copied into bridge payloads.
  let workflow: CompanionWorkflowState?
  let proposals: [CompanionApprovedProposal]
  let composerRecords: [CompanionComposerRecord]
  let reminderPlans: [CompanionReminderPlan]
}

extension Notification.Name {
  static let companionProtectedStoreDidChange = Notification.Name(
    "BirthdayAutopilot.CompanionProtectedStoreDidChange"
  )
}

enum CompanionStoreError: Error {
  case accountMismatch
  case accountUnavailable
  case androidManaged
  case coexistenceUnverified
  case duplicateOperation
  case ledgerCapacityReached
  case invalidReview
  case invalidWorkflowState
  case nonceInvalid
  case operationOutOfOrder
  case proposalMissing
  case repeatSuppressed
  case resetFenceActive
  case resetFenceOverflow
  case staleRevision
  case staleMaterial
  case storageUnavailable
  case unsupportedSchema

  var safeCode: String {
    switch self {
    case .accountMismatch:
      return "COMPANION_ACCOUNT_MISMATCH"
    case .accountUnavailable:
      return "COMPANION_ACCOUNT_UNAVAILABLE"
    case .androidManaged:
      return "COMPOSER_MANAGED_BY_ANDROID"
    case .coexistenceUnverified:
      return "COMPOSER_COEXISTENCE_UNVERIFIED"
    case .duplicateOperation:
      return "COMPOSER_OPERATION_DUPLICATE"
    case .ledgerCapacityReached:
      return "COMPANION_LEDGER_CAPACITY_REACHED"
    case .invalidReview:
      return "COMPANION_REVIEW_INVALID"
    case .invalidWorkflowState:
      return "COMPANION_WORKFLOW_INVALID"
    case .nonceInvalid:
      return "COMPOSER_ACTION_NONCE_INVALID"
    case .operationOutOfOrder:
      return "COMPOSER_OPERATION_OUT_OF_ORDER"
    case .proposalMissing:
      return "COMPOSER_PROPOSAL_UNAVAILABLE"
    case .repeatSuppressed:
      return "COMPOSER_REPEAT_SUPPRESSED"
    case .resetFenceActive:
      return "COMPOSER_RESET_FENCE_ACTIVE"
    case .resetFenceOverflow:
      return "COMPOSER_RESET_FENCE_OVERFLOW"
    case .staleRevision:
      return "COMPOSER_PROPOSAL_STALE"
    case .staleMaterial:
      return "COMPANION_MATERIAL_STALE"
    case .storageUnavailable:
      return "COMPANION_STORAGE_UNAVAILABLE"
    case .unsupportedSchema:
      return "COMPANION_STORAGE_SCHEMA_UNSUPPORTED"
    }
  }
}

private struct CompanionProtectedSnapshot: Codable {
  static let currentSchemaVersion = 2

  let schemaVersion: Int
  var resetSafety: CompanionResetSafety
  var control: CompanionControlState?
  var proposals: [CompanionApprovedProposal]
  var composerRecords: [CompanionComposerRecord]
  var reminderPlans: [CompanionReminderPlan]
  var notificationIdentities: [CompanionNotificationIdentity]
  // Optional keeps schema-v2 snapshots written before bounded daily companion
  // attention notifications forward-readable. Values are content-free civil dates.
  var attentionNotificationDays: [String: String]?
  var reminderHorizon: CompanionReminderHorizon?
  // Optional migrates schema-v2 snapshots created before durable cold-launch
  // notification routing. This contains no notification request identity.
  var pendingNativeRoute: IOSCompanionPendingNativeRoute?
  // Optional preserves forward migration from early schema-v2 snapshots.
  var workflow: CompanionWorkflowState?
  // Optional so schema-v2 files written before the BirthdayNative bridge remain
  // readable. A missing value is revision zero for that protected generation.
  var projectionRevision: UInt64?

  static func reset(on civilDate: String) -> CompanionProtectedSnapshot {
    CompanionProtectedSnapshot(
      schemaVersion: currentSchemaVersion,
      resetSafety: CompanionResetSafety(
        generation: UUID().uuidString.lowercased(),
        blockedCivilDates: [civilDate],
        overflowed: false,
        verifiedCivilDate: nil
      ),
      control: nil,
      proposals: [],
      composerRecords: [],
      reminderPlans: [],
      notificationIdentities: [],
      attentionNotificationDays: [:],
      reminderHorizon: nil,
      pendingNativeRoute: nil,
      workflow: nil,
      projectionRevision: 0
    )
  }

  static var initialInstall: CompanionProtectedSnapshot {
    CompanionProtectedSnapshot(
      schemaVersion: currentSchemaVersion,
      resetSafety: CompanionResetSafety(
        generation: UUID().uuidString.lowercased(),
        blockedCivilDates: [],
        overflowed: false,
        verifiedCivilDate: nil
      ),
      control: nil,
      proposals: [],
      composerRecords: [],
      reminderPlans: [],
      notificationIdentities: [],
      attentionNotificationDays: [:],
      reminderHorizon: nil,
      pendingNativeRoute: nil,
      workflow: nil,
      projectionRevision: 0
    )
  }
}

private enum CompanionKeychainError: Error {
  case notFound
  case unavailable
}

/// Encrypted native storage for iOS companion safety state and private proposals.
///
/// The file is protected while the device is locked, excluded from backups, and
/// sealed with an AES-256-GCM key that is non-synchronizing and device-only in
/// Keychain. Provider credentials never enter this store or React Native.
final class CompanionProtectedStore {
  static let shared = CompanionProtectedStore()

  private static let maximumFileBytes = 4 * 1_024 * 1_024
  private static let maximumComposerRecords = 1_024
  private static let maximumProposals = 1_024
  private static let maximumReminderPlans = 500
  private static let maximumWorkflowContacts = IOSPeopleCapacityPolicy.maximumPeople
  private static let maximumWorkflowReviews = 32
  private static let maximumWorkflowActivity = 2_048
  private static let maximumWorkflowOperations = 32
  private static let maximumResetDates = 8
  // The JavaScript contract accepts at most 19 decimal digits.
  private static let maximumProjectionRevision: UInt64 = 9_223_372_036_854_775_807
  private static let coexistenceMaximumAge: TimeInterval = 60
  private static let maximumFutureClockSkew: TimeInterval = 5
  private static let reviewLifetime: TimeInterval = 45
  private static let authenticatedContext = Data(
    "birthday-autopilot.companion-store.v2".utf8
  )
  private static let keychainService =
    "com.yashsomani.birthdayautopilot.companion-store"
  private static let keychainAccount = "database-key-v2"
  private static let legacyKeychainAccounts = ["database-key-v1"]

  private let queue = DispatchQueue(
    label: "com.yashsomani.birthdayautopilot.companion-protected-store",
    qos: .userInitiated
  )
  private let fileManager: FileManager
  private let calendar: Calendar

  private init(
    fileManager: FileManager = .default,
    calendar: Calendar = {
      var value = Calendar(identifier: .gregorian)
      value.timeZone = .autoupdatingCurrent
      return value
    }()
  ) {
    self.fileManager = fileManager
    self.calendar = calendar
  }

  func readProjectionStatus(
    now: Date = Date(),
    completion: @escaping (Result<CompanionProjectionStatus, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<CompanionProjectionStatus, CompanionStoreError>
      do {
        let snapshot = try self.loadSnapshotApplyingRetention(now: now)
        let fileURL = try self.storeFileURL()
        let attributes = try self.fileManager.attributesOfItem(atPath: fileURL.path)
        let fileSize = (attributes[.size] as? NSNumber)?.uint64Value ?? 0
        guard fileSize <= UInt64(Int.max) else {
          throw CompanionStoreError.storageUnavailable
        }

        let coexistence: CompanionProjectionStatus.Coexistence
        let trustedNow: Date?
        if let control = snapshot.control {
          let age = now.timeIntervalSince(control.checkedAt)
          if age < -Self.maximumFutureClockSkew || age > Self.coexistenceMaximumAge {
            coexistence = .staleOrUnknown
          } else {
            switch control.androidCoexistence {
            case .clear:
              coexistence = .clear
            case .deleting:
              coexistence = .deleting
            case .managed:
              coexistence = .managed
            case .unknown:
              coexistence = .staleOrUnknown
            }
          }
          trustedNow = IOSContactsFreshnessPolicy.estimateTrustedNow(
            serverObservedAt: control.trustedServerTime,
            locallyReceivedAt: control.checkedAt,
            now: now,
            maximumObservationAge: Self.coexistenceMaximumAge
          )
        } else {
          coexistence = .unavailable
          trustedNow = nil
        }

        result = .success(
          CompanionProjectionStatus(
            revision: String(snapshot.projectionRevision ?? 0),
            retainedSetupExists: snapshot.control != nil || !snapshot.proposals.isEmpty
              || !snapshot.composerRecords.isEmpty || !snapshot.reminderPlans.isEmpty
              || snapshot.reminderHorizon != nil || snapshot.workflow != nil,
            approvedProposalCount: snapshot.proposals.count,
            composerRecordCount: snapshot.composerRecords.count,
            localStorageBytes: Int(fileSize),
            resetSafetyRequiresRelease: snapshot.resetSafety.requiresRelease,
            resetSafetyOverflowed: snapshot.resetSafety.overflowed,
            resetSafetyVerified: snapshot.resetSafety.verifiedCivilDate != nil,
            coexistence: coexistence,
            trustedNow: trustedNow,
            lastReminderReconciledAt: snapshot.reminderHorizon?.reconciledAt,
            reminderHorizonState: snapshot.reminderHorizon?.state,
            workflow: snapshot.workflow,
            proposals: snapshot.proposals,
            composerRecords: snapshot.composerRecords,
            reminderPlans: snapshot.reminderPlans
          )
        )
      } catch let error as CompanionStoreError {
        result = .failure(error)
      } catch {
        result = .failure(.storageUnavailable)
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Called only by the future native account/coordination gateway. No bridge
  /// method exposes this mutator directly to JavaScript.
  func updateControl(
    _ control: CompanionControlState,
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        snapshot.control = control
      }
      self.complete(result, completion: completion)
    }
  }

  func invalidateAccountSession(
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        snapshot.control = nil
        snapshot.workflow?.reviews.removeAll()
        for index in snapshot.proposals.indices {
          snapshot.proposals[index].reviewNonceDigest = nil
          snapshot.proposals[index].reviewNonceExpiresAt = nil
          snapshot.proposals[index].reviewSessionGeneration = nil
          snapshot.proposals[index].reviewSceneIdentifier = nil
        }
      }
      self.complete(result, completion: completion)
    }
  }

  /// Postcondition used by retained sign-out/revoke. A write acknowledgement
  /// alone is insufficient because an interrupted protected-store write must
  /// never leave a usable review nonce or cached coexistence proof behind.
  func verifyAccountSessionInvalidated(completion: @escaping (Bool) -> Void) {
    queue.async {
      let verified: Bool
      do {
        let snapshot = try self.loadSnapshot()
        verified = snapshot.control == nil
          && (snapshot.workflow?.reviews.isEmpty ?? true)
          && snapshot.proposals.allSatisfy { proposal in
            proposal.reviewNonceDigest == nil
              && proposal.reviewNonceExpiresAt == nil
              && proposal.reviewSessionGeneration == nil
              && proposal.reviewSceneIdentifier == nil
          }
      } catch {
        verified = false
      }
      DispatchQueue.main.async { completion(verified) }
    }
  }

  /// Advances the shared bridge revision after another protected native store
  /// commits. No external store content is copied into the companion ledger.
  func markExternalProjectionChanged(
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { _ in () }
      self.complete(result, completion: completion)
    }
  }

  func bindWorkflowAccount(
    _ binding: IOSNativeGoogleAccountBinding,
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        if let workflow = snapshot.workflow {
          guard workflow.account.matches(binding) else {
            throw CompanionStoreError.accountMismatch
          }
        } else {
          snapshot.workflow = CompanionWorkflowState.empty(binding: binding)
        }
      }
      self.complete(result, completion: completion)
    }
  }

  func readWorkflowSnapshot(
    completion: @escaping (Result<CompanionWorkflowReadSnapshot, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<CompanionWorkflowReadSnapshot, CompanionStoreError>
      do {
        let snapshot = try self.loadSnapshotApplyingRetention(now: Date())
        result = .success(
          CompanionWorkflowReadSnapshot(
            revision: String(snapshot.projectionRevision ?? 0),
            workflow: snapshot.workflow,
            proposals: snapshot.proposals,
            composerRecords: snapshot.composerRecords,
            reminderPlans: snapshot.reminderPlans
          )
        )
      } catch let error as CompanionStoreError {
        result = .failure(error)
      } catch {
        result = .failure(.storageUnavailable)
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Performs a configuration CAS in the same encrypted transaction that owns
  /// the public projection revision. The caller must include all material
  /// blockers in its review hash; the serial transaction makes confirmation
  /// single-use even under repeated taps.
  func mutateWorkflow<Value>(
    expectedRevision: String,
    binding: IOSNativeGoogleAccountBinding,
    now: Date = Date(),
    body: @escaping (
      inout CompanionWorkflowState,
      _ committedRevision: String
    ) throws -> Value,
    completion: @escaping (Result<Value, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<Value, CompanionStoreError> = self.transaction { snapshot in
        let currentRevision = snapshot.projectionRevision ?? 0
        guard String(currentRevision) == expectedRevision else {
          throw CompanionStoreError.staleRevision
        }
        guard currentRevision < Self.maximumProjectionRevision else {
          throw CompanionStoreError.storageUnavailable
        }
        var workflow = snapshot.workflow ?? CompanionWorkflowState.empty(binding: binding)
        guard workflow.account.matches(binding) else {
          throw CompanionStoreError.accountMismatch
        }
        Self.pruneWorkflowMetadata(&workflow, now: now)
        let value = try body(&workflow, String(currentRevision + 1))
        guard Self.validateWorkflow(workflow) else {
          throw CompanionStoreError.invalidWorkflowState
        }
        snapshot.workflow = workflow
        return value
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Completes Clear activity in one protected-store transaction. Retryable
  /// cancelled/failed proposals remain available for a new explicit review,
  /// while their display-only records are removed. Reported-sent/unknown
  /// operations retain only the opaque repeat-safety marker; their recipient
  /// and message proposal is removed immediately rather than waiting 30 days.
  func completeClearActivity(
    operationId: String,
    binding: IOSNativeGoogleAccountBinding,
    now: Date = Date(),
    completion: @escaping (
      Result<CompanionWorkflowPrivacyOperation, CompanionStoreError>
    ) -> Void
  ) {
    queue.async {
      guard Self.isBoundedOpaqueIdentifier(operationId) else {
        DispatchQueue.main.async { completion(.failure(.invalidWorkflowState)) }
        return
      }
      let result: Result<CompanionWorkflowPrivacyOperation, CompanionStoreError> =
        self.transaction { snapshot in
          guard var workflow = snapshot.workflow,
            workflow.account.matches(binding),
            let operationIndex = workflow.privacyOperations.firstIndex(where: {
              $0.id == operationId && $0.action == "clear-activity"
                && $0.phase == "local-wiping"
            })
          else { throw CompanionStoreError.invalidWorkflowState }

          workflow.activity.removeAll()

          let terminalProposalIds = Set(
            snapshot.composerRecords.compactMap { record -> String? in
              record.outcome == .reportedSent || record.outcome == .outcomeUnknown
                ? record.proposalId : nil
            }
          )
          snapshot.proposals.removeAll {
            terminalProposalIds.contains($0.proposalId)
          }
          snapshot.composerRecords.removeAll {
            $0.outcome == .cancelled || $0.outcome == .failed
          }
          workflow.activityClearedAt = snapshot.composerRecords.isEmpty ? nil : now

          workflow.privacyOperations[operationIndex].phase = "complete"
          workflow.privacyOperations[operationIndex].reason = nil
          workflow.privacyOperations[operationIndex].updatedAt = now
          let completed = workflow.privacyOperations[operationIndex]
          snapshot.workflow = workflow
          return completed
        }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Atomically removes every contact-derived workflow payload after the
  /// People store has committed its local clear. Content-free composer outcome
  /// markers and the privacy operation journal remain, while proposal bodies,
  /// destinations, approvals, reminders, routes, and selected contact material
  /// are removed together. A local disclosure revocation is recorded in the
  /// same transaction so a crash cannot expose cleared contacts as consented.
  func clearContactDerivedState(
    operationId: String,
    action: String,
    completionPhase: String,
    binding: IOSNativeGoogleAccountBinding,
    now: Date = Date(),
    completion: @escaping (
      Result<CompanionWorkflowPrivacyOperation, CompanionStoreError>
    ) -> Void
  ) {
    queue.async {
      guard Self.isBoundedOpaqueIdentifier(operationId),
        ["disconnect-contacts", "revoke-google-access"].contains(action),
        ["complete", "local-cleared"].contains(completionPhase)
      else {
        DispatchQueue.main.async { completion(.failure(.invalidWorkflowState)) }
        return
      }
      let result: Result<CompanionWorkflowPrivacyOperation, CompanionStoreError> =
        self.transaction { snapshot in
          guard var workflow = snapshot.workflow,
            workflow.account.matches(binding),
            workflow.configurationGeneration < UInt64.max,
            let operationIndex = workflow.privacyOperations.firstIndex(where: {
              $0.id == operationId && $0.action == action
                && $0.phase == "local-wiping"
            }),
            IOSCompanionConsentLedgerPolicy.recordDisclosureRevoked(
              receipts: &workflow.consentReceipts,
              at: now
            )
          else { throw CompanionStoreError.invalidWorkflowState }

          workflow.configurationGeneration += 1
          workflow.contacts.removeAll()
          workflow.blockedDestinations = []
          workflow.occurrences.removeAll()
          workflow.reviews.removeAll()
          workflow.desired = .paused
          workflow.privacyOperations[operationIndex].phase = completionPhase
          workflow.privacyOperations[operationIndex].reason = nil
          workflow.privacyOperations[operationIndex].updatedAt = now
          let updatedOperation = workflow.privacyOperations[operationIndex]
          snapshot.proposals.removeAll()
          Self.applyReminderPlans([], to: &snapshot)
          snapshot.reminderHorizon = nil
          snapshot.pendingNativeRoute = nil
          snapshot.workflow = workflow
          return updatedOperation
        }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Serial, restart-safe state transition for a durable privacy saga. The
  /// irreversible provider/terminal milestones are monotonic, and stale local
  /// callbacks cannot send a revoke operation back through local cleanup.
  func transitionPrivacyOperation(
    operationId: String,
    action: String,
    phase requestedPhase: String,
    reason: String?,
    binding: IOSNativeGoogleAccountBinding,
    now: Date = Date(),
    completion: @escaping (
      Result<CompanionWorkflowPrivacyOperation, CompanionStoreError>
    ) -> Void
  ) {
    queue.async {
      let allowedPhases: Set<String> = [
        "queued", "pausing", "remote-pending", "remote-draining",
        "local-wiping", "local-cleared", "verifying", "provider-revoked",
        "complete", "failed",
      ]
      guard Self.isBoundedOpaqueIdentifier(operationId),
        Self.isBoundedOpaqueIdentifier(action),
        allowedPhases.contains(requestedPhase),
        reason.map(Self.isBoundedOpaqueIdentifier) ?? true
      else {
        DispatchQueue.main.async { completion(.failure(.invalidWorkflowState)) }
        return
      }
      let result: Result<CompanionWorkflowPrivacyOperation, CompanionStoreError> =
        self.transaction { snapshot in
          guard var workflow = snapshot.workflow,
            workflow.account.matches(binding),
            let operationIndex = workflow.privacyOperations.firstIndex(where: {
              $0.id == operationId && $0.action == action
            })
          else { throw CompanionStoreError.invalidWorkflowState }
          let current = workflow.privacyOperations[operationIndex]
          if ["complete", "failed"].contains(current.phase)
            || (current.phase == "provider-revoked"
              && !["provider-revoked", "complete"].contains(requestedPhase))
            || (action == "revoke-google-access"
              && [
                "local-cleared", "verifying", "remote-draining",
                "provider-revoked",
              ].contains(current.phase)
              && ["local-wiping", "remote-pending"].contains(requestedPhase))
          {
            return current
          }
          workflow.privacyOperations[operationIndex].phase = requestedPhase
          workflow.privacyOperations[operationIndex].reason = reason
          workflow.privacyOperations[operationIndex].updatedAt = now
          let updatedOperation = workflow.privacyOperations[operationIndex]
          snapshot.workflow = workflow
          return updatedOperation
        }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Commits the irreversible provider-disconnect milestone without a public
  /// projection CAS. External provider state cannot be rolled back, so a
  /// concurrent read or unrelated projection write must not strand the saga in
  /// a phase that would require an already-revoked Google session to retry.
  func markContactsProviderRevoked(
    operationId: String,
    binding: IOSNativeGoogleAccountBinding,
    now: Date = Date(),
    completion: @escaping (
      Result<CompanionWorkflowPrivacyOperation, CompanionStoreError>
    ) -> Void
  ) {
    queue.async {
      guard Self.isBoundedOpaqueIdentifier(operationId) else {
        DispatchQueue.main.async { completion(.failure(.invalidWorkflowState)) }
        return
      }
      let result: Result<CompanionWorkflowPrivacyOperation, CompanionStoreError> =
        self.transaction { snapshot in
          guard var workflow = snapshot.workflow,
            workflow.account.matches(binding),
            let operationIndex = workflow.privacyOperations.firstIndex(where: {
              $0.id == operationId && $0.action == "revoke-google-access"
            })
          else { throw CompanionStoreError.invalidWorkflowState }
          if workflow.privacyOperations[operationIndex].phase == "provider-revoked" {
            return workflow.privacyOperations[operationIndex]
          }
          guard ["local-cleared", "verifying", "remote-draining"]
            .contains(workflow.privacyOperations[operationIndex].phase)
          else { throw CompanionStoreError.invalidWorkflowState }
          workflow.privacyOperations[operationIndex].phase = "provider-revoked"
          workflow.privacyOperations[operationIndex].reason = nil
          workflow.privacyOperations[operationIndex].updatedAt = now
          let updatedOperation = workflow.privacyOperations[operationIndex]
          snapshot.workflow = workflow
          return updatedOperation
        }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Replaces derived occurrence/proposal/reminder state only if the source
  /// configuration generation is still current. This prevents a slow planner
  /// from resurrecting reminders after a later pause or privacy action.
  func replaceWorkflowPlan(
    binding: IOSNativeGoogleAccountBinding,
    expectedConfigurationGeneration: UInt64,
    occurrences: [CompanionWorkflowOccurrence],
    proposals: [CompanionApprovedProposal],
    plans: [CompanionReminderPlan],
    now: Date = Date(),
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    queue.async {
      guard occurrences.count <= Self.maximumReminderPlans,
        proposals.count <= Self.maximumProposals,
        plans.count <= Self.maximumReminderPlans,
        Set(occurrences.map(\.occurrenceId)).count == occurrences.count,
        Set(occurrences.map(\.proposalId)).count == occurrences.count,
        Set(proposals.map(\.proposalId)).count == proposals.count,
        Set(proposals.map(\.occurrenceId)).count == proposals.count,
        Set(plans.map(\.occurrenceId)).count == plans.count,
        Set(occurrences.map(\.occurrenceId)) == Set(proposals.map(\.occurrenceId)),
        Set(occurrences.map(\.occurrenceId)) == Set(plans.map(\.occurrenceId)),
        proposals.allSatisfy({ Self.validateProposal($0, binding: binding) })
      else {
        DispatchQueue.main.async { completion(.failure(.invalidWorkflowState)) }
        return
      }

      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        guard var workflow = snapshot.workflow,
          workflow.account.matches(binding),
          workflow.configurationGeneration == expectedConfigurationGeneration
        else {
          throw CompanionStoreError.staleMaterial
        }

        let preventingOccurrenceIds = Set(
          snapshot.composerRecords.filter { $0.outcome.preventsRepeat }.map(\.occurrenceId)
        )
        let safeOccurrences = occurrences.filter {
          !preventingOccurrenceIds.contains($0.occurrenceId)
        }
        let safeOccurrenceIds = Set(safeOccurrences.map(\.occurrenceId))
        let safeProposals = proposals.filter { safeOccurrenceIds.contains($0.occurrenceId) }
        let safePlans = plans.filter { safeOccurrenceIds.contains($0.occurrenceId) }
        let desiredProposalIds = Set(safeProposals.map(\.proposalId))

        let retained = snapshot.proposals.filter { proposal in
          if desiredProposalIds.contains(proposal.proposalId) { return false }
          switch proposal.state {
          case .ready, .cancelled, .failed:
            return preventingOccurrenceIds.contains(proposal.occurrenceId)
          case .openCommitted, .presented, .outcomeUnknown, .reportedSent:
            return true
          }
        }
        guard retained.count + safeProposals.count <= Self.maximumProposals,
          Set(retained.map(\.proposalId)).isDisjoint(with: desiredProposalIds)
        else {
          throw CompanionStoreError.ledgerCapacityReached
        }

        let previouslyPlanned = Set(workflow.occurrences.map(\.occurrenceId))
        for occurrence in safeOccurrences
        where !previouslyPlanned.contains(occurrence.occurrenceId) {
          workflow.activity.append(
            CompanionWorkflowActivity(
              id: UUID().uuidString.lowercased(),
              kind: "reminder-scheduled",
              reason: nil,
              occurredAt: now,
              actionable: false
            )
          )
        }
        workflow.occurrences = safeOccurrences
        Self.pruneWorkflowMetadata(&workflow, now: now)
        snapshot.workflow = workflow
        snapshot.proposals = retained + safeProposals
        Self.applyReminderPlans(safePlans, to: &snapshot)
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Records onboarding completion only after the exact configuration has a persisted, fully
  /// reconciled notification horizon. Desired reminders alone are not evidence of activation.
  func markReminderActivationCompleted(
    binding: IOSNativeGoogleAccountBinding,
    expectedConfigurationGeneration: UInt64,
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        guard var workflow = snapshot.workflow,
          workflow.account.matches(binding),
          workflow.configurationGeneration == expectedConfigurationGeneration,
          workflow.desired == .remindersOn,
          !snapshot.reminderPlans.isEmpty,
          snapshot.reminderHorizon?.state == .full,
          !(snapshot.reminderHorizon?.observedRequestIds.isEmpty ?? true)
        else {
          throw CompanionStoreError.staleMaterial
        }
        workflow.hasEverActivatedReminders = true
        snapshot.workflow = workflow
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Security refresh used immediately before composer review. Persisting this
  /// control evidence must not invalidate the UI revision that initiated the
  /// review, so it intentionally does not publish a projection change.
  func updateControlForComposer(
    _ control: CompanionControlState,
    binding: IOSNativeGoogleAccountBinding,
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError>
      do {
        var snapshot = try self.loadSnapshot()
        guard control.accountGeneration == binding.accountGeneration,
          var workflow = snapshot.workflow,
          workflow.account.matches(binding)
        else {
          throw CompanionStoreError.accountMismatch
        }
        if control.androidCoexistence == .clear {
          workflow.lastCoordinationSuccessAt = control.checkedAt
        }
        snapshot.workflow = workflow
        snapshot.control = control
        try self.persist(snapshot)
        result = .success(())
      } catch let error as CompanionStoreError {
        result = .failure(error)
      } catch {
        result = .failure(.storageUnavailable)
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Called only after native approval persistence. Message presentation accepts
  /// a proposal ID/revision/nonce and never a JavaScript-supplied payload.
  func storeApprovedProposal(
    _ proposal: CompanionApprovedProposal,
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        guard proposal.state == .ready,
          proposal.reviewNonceDigest == nil,
          proposal.reviewNonceExpiresAt == nil,
          proposal.reviewSessionGeneration == nil,
          proposal.reviewSceneIdentifier == nil,
          proposal.operationId == nil,
          Self.isBoundedOpaqueIdentifier(proposal.proposalId),
          Self.isBoundedOpaqueIdentifier(proposal.accountGeneration),
          Self.isBoundedOpaqueIdentifier(proposal.occurrenceId),
          proposal.revision.range(of: "^(0|[1-9][0-9]{0,18})$", options: .regularExpression)
            != nil,
          Self.resetReleaseThreshold(for: proposal.occurrenceCivilDate) != nil,
          proposal.recipient.range(
            of: "^\\+[1-9][0-9]{7,14}$",
            options: .regularExpression
          ) != nil,
          Self.isSafeMessageBody(proposal.body)
        else {
          throw CompanionStoreError.proposalMissing
        }
        guard
          !snapshot.composerRecords.contains(where: {
            $0.occurrenceId == proposal.occurrenceId && $0.outcome.preventsRepeat
          })
        else {
          throw CompanionStoreError.repeatSuppressed
        }
        if let index = snapshot.proposals.firstIndex(where: {
          $0.proposalId == proposal.proposalId
        }) {
          snapshot.proposals[index] = proposal
        } else {
          guard snapshot.proposals.count < Self.maximumProposals else {
            throw CompanionStoreError.ledgerCapacityReached
          }
          snapshot.proposals.append(proposal)
        }
      }
      self.complete(result, completion: completion)
    }
  }

  /// Releases reset safety only from a native, authenticated status response.
  /// Automatic time/timezone repair must already be verified by the caller.
  func releaseResetSafety(
    trustedServerTime: Date,
    automaticTimeVerified: Bool,
    observedAt: Date = Date(),
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        guard automaticTimeVerified else {
          throw CompanionStoreError.resetFenceActive
        }
        guard !snapshot.resetSafety.overflowed else {
          throw CompanionStoreError.resetFenceOverflow
        }
        guard snapshot.resetSafety.verifiedCivilDate != nil else {
          throw CompanionStoreError.resetFenceActive
        }
        guard
          snapshot.resetSafety.blockedCivilDates.allSatisfy({ civilDate in
            guard let releaseAfter = Self.resetReleaseThreshold(for: civilDate) else {
              return false
            }
            return trustedServerTime > releaseAfter
          })
        else {
          throw CompanionStoreError.resetFenceActive
        }
        snapshot.resetSafety.blockedCivilDates = []
      }
      self.complete(result, completion: completion)
    }
  }

  func canReleaseResetSafety(
    trustedServerTime: Date,
    automaticTimeVerified: Bool,
    observedAt: Date = Date(),
    completion: @escaping (Bool) -> Void
  ) {
    queue.async {
      guard automaticTimeVerified, let snapshot = try? self.loadSnapshot(),
        snapshot.resetSafety.requiresRelease, !snapshot.resetSafety.overflowed,
        snapshot.resetSafety.verifiedCivilDate != nil
      else {
        DispatchQueue.main.async { completion(false) }
        return
      }
      let eligible = snapshot.resetSafety.blockedCivilDates.allSatisfy { civilDate in
        guard let releaseAfter = Self.resetReleaseThreshold(for: civilDate) else {
          return false
        }
        return trustedServerTime > releaseAfter
      }
      DispatchQueue.main.async { completion(eligible) }
    }
  }

  func establishVerifiedResetSafetyDate(
    automaticTimeVerified: Bool,
    observedAt: Date,
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    guard automaticTimeVerified else {
      completion(.failure(.resetFenceActive))
      return
    }
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction(
        persistMutationOnFailure: true
      ) { snapshot in
        guard snapshot.resetSafety.requiresRelease else { return }
        guard !snapshot.resetSafety.overflowed else {
          throw CompanionStoreError.resetFenceOverflow
        }
        guard snapshot.resetSafety.verifiedCivilDate == nil else { return }
        let civilDate = Self.civilDate(for: observedAt, calendar: self.calendar)
        if !snapshot.resetSafety.blockedCivilDates.contains(civilDate) {
          guard snapshot.resetSafety.blockedCivilDates.count < Self.maximumResetDates else {
            snapshot.resetSafety.overflowed = true
            throw CompanionStoreError.resetFenceOverflow
          }
          snapshot.resetSafety.blockedCivilDates.append(civilDate)
          snapshot.resetSafety.blockedCivilDates.sort()
        }
        snapshot.resetSafety.verifiedCivilDate = civilDate
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  func prepareComposerReview(
    proposalId: String,
    expectedRevision: String,
    sessionGeneration: String,
    sceneIdentifier: String,
    now: Date,
    completion: @escaping (Result<CompanionReviewProjection, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<CompanionReviewProjection, CompanionStoreError>
      do {
        var snapshot = try self.loadSnapshot()
        do {
          self.resolveDanglingComposerOperations(in: &snapshot, at: now)
          try self.observeAndCheckResetSafety(in: &snapshot, now: now)
          guard
            let index = snapshot.proposals.firstIndex(where: {
              $0.proposalId == proposalId
            })
          else {
            throw CompanionStoreError.proposalMissing
          }
          guard String(snapshot.projectionRevision ?? 0) == expectedRevision else {
            throw CompanionStoreError.staleRevision
          }
          try self.checkControl(
            snapshot.control,
            proposalAccountGeneration: snapshot.proposals[index].accountGeneration,
            now: now
          )
          let state = snapshot.proposals[index].state
          guard state == .ready || state == .cancelled || state == .failed else {
            throw state == .reportedSent || state == .outcomeUnknown
              ? CompanionStoreError.repeatSuppressed
              : CompanionStoreError.operationOutOfOrder
          }
          guard
            !snapshot.composerRecords.contains(where: {
              $0.outcome == .openCommitted || $0.outcome == .presented
            })
          else {
            throw CompanionStoreError.operationOutOfOrder
          }
          guard
            !snapshot.composerRecords.contains(where: {
              $0.occurrenceId == snapshot.proposals[index].occurrenceId
                && $0.outcome.preventsRepeat
            })
          else {
            throw CompanionStoreError.repeatSuppressed
          }

          let nonce = try Self.randomNonce()
          let digest = Data(SHA256.hash(data: Data(nonce.utf8)))
          let expiresAt = now.addingTimeInterval(Self.reviewLifetime)
          snapshot.proposals[index].state = .ready
          snapshot.proposals[index].revision = expectedRevision
          snapshot.proposals[index].reviewNonceDigest = digest
          snapshot.proposals[index].reviewNonceExpiresAt = expiresAt
          snapshot.proposals[index].reviewSessionGeneration = sessionGeneration
          snapshot.proposals[index].reviewSceneIdentifier = sceneIdentifier
          snapshot.proposals[index].operationId = nil
          try self.persist(snapshot)
          result = .success(CompanionReviewProjection(
            proposalId: proposalId,
            revision: expectedRevision,
            recipient: snapshot.proposals[index].recipient,
            body: snapshot.proposals[index].body,
            actionNonce: nonce,
            expiresAt: expiresAt
          ))
        } catch let error as CompanionStoreError {
          // Reset-date and dangling-operation refinements are safety ledger
          // facts. Preserve them even when the requested review is refused,
          // without changing the UI revision that the review is fenced to.
          do {
            try self.persist(snapshot)
            result = .failure(error)
          } catch {
            result = .failure(.storageUnavailable)
          }
        }
      } catch let error as CompanionStoreError {
        result = .failure(error)
      } catch {
        result = .failure(.storageUnavailable)
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  func reconcileComposerOnCleanLaunch(
    now: Date = Date(),
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        self.resolveDanglingComposerOperations(in: &snapshot, at: now)
        self.observeResetDate(in: &snapshot, now: now)
      }
      self.complete(result, completion: completion)
    }
  }

  func observeResetSafetyDate(
    now: Date = Date(),
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        self.observeResetDate(in: &snapshot, now: now)
      }
      self.complete(result, completion: completion)
    }
  }

  func commitComposerOpen(
    proposalId: String,
    expectedRevision: String,
    actionNonce: String,
    sessionGeneration: String,
    sceneIdentifier: String,
    now: Date,
    completion: @escaping (Result<CompanionPresentationCommit, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<CompanionPresentationCommit, CompanionStoreError> =
        self.transaction(persistMutationOnFailure: true) { snapshot in
          self.resolveDanglingComposerOperations(in: &snapshot, at: now)
          try self.observeAndCheckResetSafety(in: &snapshot, now: now)
          guard
            let index = snapshot.proposals.firstIndex(where: {
              $0.proposalId == proposalId
            })
          else {
            throw CompanionStoreError.proposalMissing
          }
          let proposal = snapshot.proposals[index]
          guard proposal.revision == expectedRevision else {
            throw CompanionStoreError.staleRevision
          }
          try self.checkControl(
            snapshot.control,
            proposalAccountGeneration: proposal.accountGeneration,
            now: now
          )
          guard proposal.state == .ready,
            proposal.reviewSessionGeneration == sessionGeneration,
            proposal.reviewSceneIdentifier == sceneIdentifier,
            let expectedDigest = proposal.reviewNonceDigest,
            let expiresAt = proposal.reviewNonceExpiresAt,
            now <= expiresAt,
            Data(SHA256.hash(data: Data(actionNonce.utf8))) == expectedDigest
          else {
            throw CompanionStoreError.nonceInvalid
          }
          guard proposal.occurrenceCivilDate == Self.civilDate(for: now, calendar: self.calendar)
          else {
            throw CompanionStoreError.proposalMissing
          }
          guard
            !snapshot.composerRecords.contains(where: {
              $0.outcome == .openCommitted || $0.outcome == .presented
            })
          else {
            throw CompanionStoreError.operationOutOfOrder
          }
          guard
            !snapshot.composerRecords.contains(where: {
              $0.occurrenceId == proposal.occurrenceId && $0.outcome.preventsRepeat
            })
          else {
            throw CompanionStoreError.repeatSuppressed
          }

          Self.pruneComposerRecords(in: &snapshot, now: now)
          guard snapshot.composerRecords.count < Self.maximumComposerRecords else {
            throw CompanionStoreError.ledgerCapacityReached
          }

          let operationId = UUID().uuidString.lowercased()
          snapshot.proposals[index].state = .openCommitted
          snapshot.proposals[index].reviewNonceDigest = nil
          snapshot.proposals[index].reviewNonceExpiresAt = nil
          snapshot.proposals[index].reviewSessionGeneration = nil
          snapshot.proposals[index].reviewSceneIdentifier = nil
          snapshot.proposals[index].operationId = operationId
          snapshot.composerRecords.append(
            CompanionComposerRecord(
              operationId: operationId,
              proposalId: proposal.proposalId,
              occurrenceId: proposal.occurrenceId,
              occurrenceCivilDate: proposal.occurrenceCivilDate,
              openedAt: now,
              outcome: .openCommitted,
              resolvedAt: nil
            )
          )
          return CompanionPresentationCommit(
            operationId: operationId,
            proposalId: proposal.proposalId,
            recipient: proposal.recipient,
            body: proposal.body
          )
        }
      DispatchQueue.main.async { completion(result) }
    }
  }

  func markComposerPresented(
    operationId: String,
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    updateComposerOperation(
      operationId: operationId,
      expected: [.openCommitted],
      outcome: .presented,
      proposalState: .presented,
      resolvedAt: nil,
      completion: completion
    )
  }

  func finishComposerOperation(
    operationId: String,
    outcome: CompanionComposerOutcome,
    now: Date = Date(),
    completion: ((Result<Void, CompanionStoreError>) -> Void)? = nil
  ) {
    let proposalState: CompanionProposalState
    switch outcome {
    case .cancelled:
      proposalState = .cancelled
    case .failed:
      proposalState = .failed
    case .outcomeUnknown:
      proposalState = .outcomeUnknown
    case .reportedSent:
      proposalState = .reportedSent
    case .openCommitted, .presented:
      complete(.failure(.operationOutOfOrder), completion: completion)
      return
    }
    updateComposerOperation(
      operationId: operationId,
      expected: [.openCommitted, .presented],
      outcome: outcome,
      proposalState: proposalState,
      resolvedAt: now,
      completion: completion
    )
  }

  func replaceReminderPlans(
    _ plans: [CompanionReminderPlan],
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    queue.async {
      guard plans.count <= Self.maximumReminderPlans else {
        DispatchQueue.main.async { completion(.failure(.ledgerCapacityReached)) }
        return
      }
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        let desiredDates = Set(plans.map(\.civilDate))
        var identities = Dictionary(
          uniqueKeysWithValues: snapshot.notificationIdentities.map {
            ($0.civilDate, $0.requestId)
          }
        )
        identities = identities.filter { desiredDates.contains($0.key) }
        for civilDate in desiredDates where identities[civilDate] == nil {
          identities[civilDate] = UUID().uuidString.lowercased()
        }
        snapshot.reminderPlans = plans
        snapshot.notificationIdentities =
          identities
          .map { CompanionNotificationIdentity(civilDate: $0.key, requestId: $0.value) }
          .sorted { $0.civilDate < $1.civilDate }
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  func readReminderSchedule(
    completion: @escaping (Result<CompanionReminderSchedule, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<CompanionReminderSchedule, CompanionStoreError>
      do {
        let snapshot = try self.loadSnapshot()
        result = .success(
          CompanionReminderSchedule(
            plans: snapshot.reminderPlans,
            requestIdByCivilDate: Dictionary(
              uniqueKeysWithValues: snapshot.notificationIdentities.map {
                ($0.civilDate, $0.requestId)
              }
            ),
            horizon: snapshot.reminderHorizon
          )
        )
      } catch let error as CompanionStoreError {
        result = .failure(error)
      } catch {
        result = .failure(.storageUnavailable)
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Atomically consumes an opaque notification request identity and rotates it
  /// before returning a content-free navigation hint. A duplicate/stale tap can
  /// therefore never be replayed into another route, and no notification UUID,
  /// civil date, contact, destination, or proposal body reaches React Native.
  func consumeReminderRouteRequest(
    _ requestId: String,
    now: Date = Date(),
    completion: @escaping (Result<IOSCompanionNativeRoute, CompanionStoreError>) -> Void
  ) {
    queue.async {
      guard let uuid = UUID(uuidString: requestId),
        uuid.uuidString.lowercased() == requestId
      else {
        DispatchQueue.main.async { completion(.failure(.invalidReview)) }
        return
      }
      let result: Result<IOSCompanionNativeRoute, CompanionStoreError> =
        self.transaction { snapshot in
          guard let identityIndex = snapshot.notificationIdentities.firstIndex(where: {
            $0.requestId == requestId
          }), let workflow = snapshot.workflow,
            workflow.desired == .remindersOn
          else { throw CompanionStoreError.invalidReview }

          let identity = snapshot.notificationIdentities[identityIndex]
          let plannedOccurrenceIds = Set(
            snapshot.reminderPlans.filter { $0.civilDate == identity.civilDate }
              .map(\.occurrenceId)
          )
          let reviewableOccurrenceIds = Set(
            workflow.occurrences.filter {
              $0.civilDate == identity.civilDate
                && [.reminderPlanned, .composerReady].contains($0.phase)
            }.map(\.occurrenceId)
          )
          let readyProposalOccurrenceIds = Set(
            snapshot.proposals.filter {
              $0.accountGeneration == workflow.account.accountGeneration
                && $0.occurrenceCivilDate == identity.civilDate
                && $0.state == .ready
            }.map(\.occurrenceId)
          )
          guard !plannedOccurrenceIds.intersection(reviewableOccurrenceIds)
            .intersection(readyProposalOccurrenceIds).isEmpty
          else { throw CompanionStoreError.proposalMissing }

          let route = IOSCompanionNativeRoute(routeId: UUID().uuidString.lowercased())
          snapshot.notificationIdentities[identityIndex] = CompanionNotificationIdentity(
            civilDate: identity.civilDate,
            requestId: UUID().uuidString.lowercased()
          )
          snapshot.pendingNativeRoute = IOSCompanionPendingNativeRoute(
            routeId: route.routeId,
            createdAt: now
          )
          return route
        }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Atomically returns and clears the persisted content-free navigation hint.
  /// The current protected workflow is revalidated before projection; stale,
  /// signed-out, expired, or no-longer-reviewable hints are consumed as `none`.
  func takePendingNativeRoute(
    now: Date = Date(),
    completion: @escaping (Result<IOSCompanionNativeRoute?, CompanionStoreError>) -> Void
  ) {
    queue.async {
      do {
        let current = try self.loadSnapshot()
        guard current.pendingNativeRoute != nil else {
          DispatchQueue.main.async { completion(.success(nil)) }
          return
        }
      } catch let error as CompanionStoreError {
        DispatchQueue.main.async { completion(.failure(error)) }
        return
      } catch {
        DispatchQueue.main.async { completion(.failure(.storageUnavailable)) }
        return
      }

      let result: Result<IOSCompanionNativeRoute?, CompanionStoreError> =
        self.transaction { snapshot in
          guard let pending = snapshot.pendingNativeRoute else { return nil }
          snapshot.pendingNativeRoute = nil
          let age = now.timeIntervalSince(pending.createdAt)
          guard age >= -Self.maximumFutureClockSkew, age <= 48 * 60 * 60,
            let workflow = snapshot.workflow,
            workflow.desired == .remindersOn,
            snapshot.proposals.contains(where: { proposal in
              proposal.accountGeneration == workflow.account.accountGeneration
                && proposal.state == .ready
                && workflow.occurrences.contains(where: { occurrence in
                  occurrence.occurrenceId == proposal.occurrenceId
                    && [.reminderPlanned, .composerReady].contains(occurrence.phase)
                })
            })
          else { return nil }
          return IOSCompanionNativeRoute(routeId: pending.routeId)
        }
      DispatchQueue.main.async { completion(result) }
    }
  }

  func recordReminderHorizon(
    _ horizon: CompanionReminderHorizon,
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    queue.async {
      guard horizon.observedRequestIds.count <= 60 else {
        DispatchQueue.main.async { completion(.failure(.ledgerCapacityReached)) }
        return
      }
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        snapshot.reminderHorizon = horizon
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Atomically claims one generic attention notification per category and
  /// local civil day. The claim contains no contact, message, account, or route
  /// data and is removed with the protected store.
  func claimAttentionNotification(
    kind: IOSCompanionAttentionKind,
    civilDate: String,
    completion: @escaping (Result<Bool, CompanionStoreError>) -> Void
  ) {
    guard Self.resetReleaseThreshold(for: civilDate) != nil else {
      completion(.failure(.invalidWorkflowState))
      return
    }
    queue.async {
      let result: Result<Bool, CompanionStoreError> = self.transaction { snapshot in
        var claims = snapshot.attentionNotificationDays ?? [:]
        guard claims.count <= IOSCompanionAttentionKind.allCases.count,
          claims.keys.allSatisfy({ IOSCompanionAttentionKind(rawValue: $0) != nil })
        else { throw CompanionStoreError.storageUnavailable }
        guard IOSCompanionRetentionPolicy.mayAdvanceAttentionClaim(
          previousCivilDate: claims[kind.rawValue],
          to: civilDate
        ) else { return false }
        claims[kind.rawValue] = civilDate
        snapshot.attentionNotificationDays = claims
        return true
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Removes all prior state/key material and installs a new non-contact reset
  /// generation before completion, so wipe never creates an unsafe open gap.
  func wipeAndInstallResetSafety(
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError>
      do {
        let fileURL = try self.storeFileURL()
        if self.fileManager.fileExists(atPath: fileURL.path) {
          try self.fileManager.removeItem(at: fileURL)
        }
        try self.deleteKeychainKey(allowMissing: true)
        _ = try self.createKeychainKey()
        try self.persist(
          CompanionProtectedSnapshot.reset(
            on: Self.civilDate(for: Date(), calendar: self.calendar)
          )
        )
        self.publishProjectionChange(revision: "0")
        result = .success(())
      } catch {
        result = .failure(.storageUnavailable)
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  /// Final local account-deletion teardown. Unlike an ordinary local wipe this
  /// does not install a new reset generation or replacement Keychain key: the
  /// authenticated backend tombstone is already the no-new-work fence.
  func destroyAfterRemoteAccountDeletion(
    completion: @escaping (Result<Void, CompanionStoreError>) -> Void
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError>
      do {
        let fileURL = try self.storeFileURL()
        if self.fileManager.fileExists(atPath: fileURL.path) {
          try self.fileManager.removeItem(at: fileURL)
        }
        try self.deleteKeychainKey(allowMissing: true)
        guard !self.fileManager.fileExists(atPath: fileURL.path) else {
          throw CompanionStoreError.storageUnavailable
        }
        do {
          _ = try self.readKeychainKey()
          throw CompanionStoreError.storageUnavailable
        } catch CompanionKeychainError.notFound {
          result = .success(())
        }
      } catch let error as CompanionStoreError {
        result = .failure(error)
      } catch {
        result = .failure(.storageUnavailable)
      }
      DispatchQueue.main.async { completion(result) }
    }
  }

  private func updateComposerOperation(
    operationId: String,
    expected: Set<CompanionComposerOutcome>,
    outcome: CompanionComposerOutcome,
    proposalState: CompanionProposalState,
    resolvedAt: Date?,
    completion: ((Result<Void, CompanionStoreError>) -> Void)?
  ) {
    queue.async {
      let result: Result<Void, CompanionStoreError> = self.transaction { snapshot in
        guard
          let recordIndex = snapshot.composerRecords.lastIndex(where: {
            $0.operationId == operationId
          }), expected.contains(snapshot.composerRecords[recordIndex].outcome),
          let proposalIndex = snapshot.proposals.firstIndex(where: {
            $0.proposalId == snapshot.composerRecords[recordIndex].proposalId
              && $0.operationId == operationId
          })
        else {
          throw CompanionStoreError.operationOutOfOrder
        }
        snapshot.composerRecords[recordIndex].outcome = outcome
        snapshot.composerRecords[recordIndex].resolvedAt = resolvedAt
        snapshot.proposals[proposalIndex].state = proposalState
      }
      self.complete(result, completion: completion)
    }
  }

  private static func applyReminderPlans(
    _ plans: [CompanionReminderPlan],
    to snapshot: inout CompanionProtectedSnapshot
  ) {
    let desiredDates = Set(plans.map(\.civilDate))
    var identities = Dictionary(
      uniqueKeysWithValues: snapshot.notificationIdentities.map {
        ($0.civilDate, $0.requestId)
      }
    )
    identities = identities.filter { desiredDates.contains($0.key) }
    for civilDate in desiredDates where identities[civilDate] == nil {
      identities[civilDate] = UUID().uuidString.lowercased()
    }
    snapshot.reminderPlans = plans
    snapshot.notificationIdentities = identities.map {
      CompanionNotificationIdentity(civilDate: $0.key, requestId: $0.value)
    }.sorted { $0.civilDate < $1.civilDate }
  }

  private static func pruneWorkflowMetadata(
    _ workflow: inout CompanionWorkflowState,
    now: Date
  ) {
    workflow.reviews.removeAll { review in
      review.consumedAt != nil || review.expiresAt < now
    }
    if workflow.reviews.count > maximumWorkflowReviews {
      workflow.reviews = Array(workflow.reviews.suffix(maximumWorkflowReviews))
    }
    workflow.activity.removeAll { activity in
      IOSCompanionRetentionPolicy.detailHasExpired(
        recordedAt: activity.occurredAt,
        now: now
      )
    }
    workflow.activity.sort { $0.occurredAt < $1.occurredAt }
    if workflow.activity.count > maximumWorkflowActivity {
      workflow.activity = Array(workflow.activity.suffix(maximumWorkflowActivity))
    }
    workflow.privacyOperations.removeAll { operation in
      ["complete", "failed"].contains(operation.phase)
        && IOSCompanionRetentionPolicy.detailHasExpired(
          recordedAt: operation.updatedAt,
          now: now
        )
    }
    workflow.privacyOperations.sort { $0.updatedAt < $1.updatedAt }
    if workflow.privacyOperations.count > maximumWorkflowOperations {
      workflow.privacyOperations = Array(
        workflow.privacyOperations.suffix(maximumWorkflowOperations)
      )
    }
  }

  private static func validateProposal(
    _ proposal: CompanionApprovedProposal,
    binding: IOSNativeGoogleAccountBinding
  ) -> Bool {
    proposal.state == .ready && proposal.reviewNonceDigest == nil
      && proposal.reviewNonceExpiresAt == nil
      && proposal.reviewSessionGeneration == nil
      && proposal.reviewSceneIdentifier == nil && proposal.operationId == nil
      && isBoundedOpaqueIdentifier(proposal.proposalId)
      && proposal.accountGeneration == binding.accountGeneration
      && isBoundedOpaqueIdentifier(proposal.occurrenceId)
      && proposal.revision.range(
        of: "^(0|[1-9][0-9]{0,18})$",
        options: .regularExpression
      ) != nil
      && resetReleaseThreshold(for: proposal.occurrenceCivilDate) != nil
      && proposal.recipient.range(
        of: "^\\+[1-9][0-9]{7,14}$",
        options: .regularExpression
      ) != nil
      && isSafeMessageBody(proposal.body)
  }

  private static func isSafeMessageBody(_ value: String) -> Bool {
    let forbidden = CharacterSet(charactersIn:
      "\u{061C}\u{200E}\u{200F}\u{202A}\u{202B}\u{202C}\u{202D}\u{202E}"
        + "\u{2066}\u{2067}\u{2068}\u{2069}")
    return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
      && value.count <= 1_000 && value.lengthOfBytes(using: .utf8) <= 4_096
      && value.range(
        of: "(?:https?://|www\\.)\\S+",
        options: [.regularExpression, .caseInsensitive]
      ) == nil
      && IOSBirthdayMessageContentPolicy.isSafeRenderedBody(value)
      && value.unicodeScalars.allSatisfy { scalar in
        if forbidden.contains(scalar) { return false }
        if scalar.value <= 0x1F {
          return scalar.value == 0x09 || scalar.value == 0x0A || scalar.value == 0x0D
        }
        return scalar.value != 0x7F
      }
  }

  private static func validateWorkflow(_ workflow: CompanionWorkflowState) -> Bool {
    let opaque = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"
    let uuid = "^[a-f0-9-]{36}$"
    let hash = "^[a-f0-9]{64}$"
    let allowedActivity: Set<String> = [
      "approval-invalidated", "composer-cancelled", "composer-failed",
      "composer-opened", "composer-outcome-unknown", "composer-reported-sent",
      "coordination-blocked", "paused", "reminder-scheduled", "settings-changed",
      "sync",
    ]
    guard
      let normalizedBlockedDestinations = IOSCompanionDestinationBlocklistPolicy.normalized(
        workflow.blockedDestinations
      ),
      normalizedBlockedDestinations == (workflow.blockedDestinations ?? []),
      IOSCompanionConsentLedgerPolicy.isValid(workflow.consentReceipts)
    else { return false }
    guard workflow.contacts.count <= maximumWorkflowContacts,
      workflow.reviews.count <= maximumWorkflowReviews,
      workflow.occurrences.count <= maximumReminderPlans,
      workflow.activity.count <= maximumWorkflowActivity,
      workflow.privacyOperations.count <= maximumWorkflowOperations,
      workflow.account.accountGeneration.range(of: uuid, options: .regularExpression) != nil,
      IOSPeopleValuePolicy.googleSubject(workflow.account.googleSubject)
        == workflow.account.googleSubject,
      IOSPeopleValuePolicy.providerIdentifier(
        workflow.account.firebaseUID,
        maximumBytes: 256
      ),
      Set(workflow.contacts.map(\.contactId)).count == workflow.contacts.count,
      Set(workflow.reviews.map(\.handle)).count == workflow.reviews.count,
      Set(workflow.occurrences.map(\.occurrenceId)).count == workflow.occurrences.count,
      Set(workflow.occurrences.map(\.proposalId)).count == workflow.occurrences.count,
      Set(workflow.occurrences.map { "\($0.contactId)|\($0.civilDate)" }).count
        == workflow.occurrences.count,
      Set(workflow.activity.map(\.id)).count == workflow.activity.count,
      Set(workflow.privacyOperations.map(\.id)).count
        == workflow.privacyOperations.count
    else { return false }

    guard workflow.contacts.allSatisfy({ contact in
      contact.contactId.range(of: opaque, options: .regularExpression) != nil
        && contact.approvalInvalidationReasons.count <= 16
        && (contact.selectedPhoneId?.range(
          of: opaque, options: .regularExpression
        ) != nil || contact.selectedPhoneId == nil)
        && (contact.selectedBirthdayId?.range(
          of: opaque, options: .regularExpression
        ) != nil || contact.selectedBirthdayId == nil)
        && (contact.approvalHash?.range(of: hash, options: .regularExpression) != nil
          || contact.approvalHash == nil)
        && (contact.leapPolicy.map { ["feb-28", "mar-01", "skip"].contains($0) } ?? true)
    }), workflow.reviews.allSatisfy({ review in
      review.handle.range(of: opaque, options: .regularExpression) != nil
        && review.blockerHash.range(of: hash, options: .regularExpression) != nil
        && review.issuedForRevision.range(
          of: "^(0|[1-9][0-9]{0,18})$",
          options: .regularExpression
        ) != nil
        && review.contactIds.count <= 50
        && Set(review.contactIds).count == review.contactIds.count
        && review.contactIds.allSatisfy {
          $0.range(of: opaque, options: .regularExpression) != nil
        }
        && (review.messageDraft.map(validateMessageDraft) ?? true)
        && (review.policy.map { policy in
          (1...20).contains(policy.legacyAndroidDailyCap)
            && policy.primaryStart.count <= 12 && policy.primaryEnd.count <= 12
            && policy.graceEnd?.count ?? 0 <= 12
        } ?? true)
    }), workflow.occurrences.allSatisfy({ occurrence in
      occurrence.occurrenceId.range(of: opaque, options: .regularExpression) != nil
        && occurrence.proposalId.range(
          of: "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$",
          options: .regularExpression
        ) != nil
        && resetReleaseThreshold(for: occurrence.civilDate) != nil
    }), workflow.activity.allSatisfy({ activity in
      activity.id.range(of: opaque, options: .regularExpression) != nil
        && allowedActivity.contains(activity.kind)
    }), workflow.privacyOperations.allSatisfy({ operation in
      operation.id.range(of: opaque, options: .regularExpression) != nil
        && operation.action.count <= 64 && operation.phase.count <= 32
        && (operation.reason?.range(
          of: opaque,
          options: .regularExpression
        ) != nil || operation.reason == nil)
    }) else { return false }

    if let draft = workflow.messageDraft {
      guard validateMessageDraft(draft) else { return false }
    }
    if let policy = workflow.policy {
      guard (1...20).contains(policy.legacyAndroidDailyCap),
        policy.primaryStart.count <= 12, policy.primaryEnd.count <= 12,
        policy.graceEnd?.count ?? 0 <= 12
      else { return false }
    }
    return true
  }

  private static func validateMessageDraft(
    _ draft: CompanionWorkflowMessageDraft
  ) -> Bool {
    guard ["en", "hi"].contains(draft.language),
      ["warm", "simple", "cheerful"].contains(draft.tone),
      ["given-name", "generic"].contains(draft.placeholderMode),
      (1...2).contains(draft.requestedSegmentCap),
      isSafeMessageBody(draft.text),
      IOSCompanionMessagePlaceholderPolicy.isValid(
        text: draft.text,
        placeholderMode: draft.placeholderMode
      ), IOSBirthdayMessageContentPolicy.issueCodes(
        text: draft.text,
        declaredLanguage: draft.language
      ).isEmpty
    else { return false }
    guard let provenance = draft.provenance else { return false }
    guard ["USER", "GEMINI", "BUILT_IN"].contains(provenance.source),
      provenance.validatorVersion == IOSBirthdayMessageContentPolicy.validatorVersion
    else { return false }
    if provenance.source == "GEMINI" {
      return (provenance.modelIdentifier.map {
        !$0.isEmpty && $0.utf8.count <= 256
      } ?? false) && (provenance.promptPolicyVersion.map {
        !$0.isEmpty && $0.utf8.count <= 128
      } ?? false)
    }
    return provenance.modelIdentifier == nil && provenance.promptPolicyVersion == nil
  }

  private func checkControl(
    _ control: CompanionControlState?,
    proposalAccountGeneration: String,
    now: Date
  ) throws {
    guard let control, control.accountGeneration == proposalAccountGeneration else {
      throw CompanionStoreError.accountUnavailable
    }
    let age = now.timeIntervalSince(control.checkedAt)
    guard age >= -Self.maximumFutureClockSkew, age <= Self.coexistenceMaximumAge else {
      throw CompanionStoreError.coexistenceUnverified
    }
    switch control.androidCoexistence {
    case .clear:
      return
    case .deleting, .managed:
      throw CompanionStoreError.androidManaged
    case .unknown:
      throw CompanionStoreError.coexistenceUnverified
    }
  }

  private func observeAndCheckResetSafety(
    in snapshot: inout CompanionProtectedSnapshot,
    now: Date
  ) throws {
    observeResetDate(in: &snapshot, now: now)
    guard snapshot.resetSafety.requiresRelease else { return }
    throw snapshot.resetSafety.overflowed
      ? CompanionStoreError.resetFenceOverflow
      : CompanionStoreError.resetFenceActive
  }

  private func observeResetDate(
    in snapshot: inout CompanionProtectedSnapshot,
    now: Date
  ) {
    guard snapshot.resetSafety.requiresRelease,
      snapshot.resetSafety.verifiedCivilDate == nil
    else { return }
    let currentDate = Self.civilDate(for: now, calendar: calendar)
    if !snapshot.resetSafety.blockedCivilDates.contains(currentDate) {
      if snapshot.resetSafety.blockedCivilDates.count < Self.maximumResetDates {
        snapshot.resetSafety.blockedCivilDates.append(currentDate)
        snapshot.resetSafety.blockedCivilDates.sort()
      } else {
        snapshot.resetSafety.overflowed = true
      }
    }
  }

  private func resolveDanglingComposerOperations(
    in snapshot: inout CompanionProtectedSnapshot,
    at now: Date
  ) {
    for recordIndex in snapshot.composerRecords.indices
    where snapshot.composerRecords[recordIndex].outcome == .openCommitted
      || snapshot.composerRecords[recordIndex].outcome == .presented
    {
      let operationId = snapshot.composerRecords[recordIndex].operationId
      snapshot.composerRecords[recordIndex].outcome = .outcomeUnknown
      snapshot.composerRecords[recordIndex].resolvedAt = now
      if let proposalIndex = snapshot.proposals.firstIndex(where: {
        $0.operationId == operationId
      }) {
        snapshot.proposals[proposalIndex].state = .outcomeUnknown
      }
    }
  }

  private static func pruneComposerRecords(
    in snapshot: inout CompanionProtectedSnapshot,
    now: Date
  ) {
    let expiredDetailProposalIDs = Set(
      snapshot.composerRecords.compactMap { record -> String? in
        switch record.outcome {
        case .cancelled, .failed, .outcomeUnknown, .reportedSent:
          let recordedAt = record.resolvedAt ?? record.openedAt
          return IOSCompanionRetentionPolicy.detailHasExpired(
            recordedAt: recordedAt,
            now: now
          ) ? record.proposalId : nil
        case .openCommitted, .presented:
          return nil
        }
      }
    )
    // The ComposerRecord is the content-free terminal marker. Once detail has
    // aged out, the proposal's destination and message are no longer needed.
    snapshot.proposals.removeAll {
      expiredDetailProposalIDs.contains($0.proposalId)
    }

    let trustedServerTime = snapshot.control?.trustedServerTime
    let removableOperationIds = Set(
      snapshot.composerRecords.compactMap { record -> String? in
        let recordedAt = record.resolvedAt ?? record.openedAt
        switch record.outcome {
        case .cancelled, .failed:
          return IOSCompanionRetentionPolicy.detailHasExpired(
            recordedAt: recordedAt,
            now: now
          ) ? record.operationId : nil
        case .outcomeUnknown, .reportedSent:
          return IOSCompanionRetentionPolicy.mayReleaseTerminalMarker(
            recordedAt: recordedAt,
            now: now,
            trustedServerTime: trustedServerTime,
            releaseAfter: Self.resetReleaseThreshold(for: record.occurrenceCivilDate)
          ) ? record.operationId : nil
        case .openCommitted, .presented:
          return nil
        }
      }
    )
    snapshot.composerRecords.removeAll {
      removableOperationIds.contains($0.operationId)
    }
  }

  @discardableResult
  private static func applyAppOwnedRetention(
    in snapshot: inout CompanionProtectedSnapshot,
    now: Date
  ) -> Bool {
    let proposalIDsBefore = snapshot.proposals.map(\.proposalId)
    let composerIDsBefore = snapshot.composerRecords.map(\.operationId)
    let reviewIDsBefore = snapshot.workflow?.reviews.map(\.handle) ?? []
    let activityIDsBefore = snapshot.workflow?.activity.map(\.id) ?? []
    let activityClearedAtBefore = snapshot.workflow?.activityClearedAt
    let privacyIDsBefore = snapshot.workflow?.privacyOperations.map(\.id) ?? []
    let attentionClaimsBefore = snapshot.attentionNotificationDays ?? [:]

    if var workflow = snapshot.workflow {
      pruneWorkflowMetadata(&workflow, now: now)
      snapshot.workflow = workflow
    }
    pruneComposerRecords(in: &snapshot, now: now)
    if var workflow = snapshot.workflow, let cutoff = workflow.activityClearedAt,
      !snapshot.composerRecords.contains(where: { $0.openedAt <= cutoff })
    {
      // Once no retained composer record can project a pre-clear event, the
      // content-free cutoff itself no longer serves a purpose.
      workflow.activityClearedAt = nil
      snapshot.workflow = workflow
    }
    snapshot.attentionNotificationDays = (snapshot.attentionNotificationDays ?? [:])
      .filter { _, civilDate in
        !IOSCompanionRetentionPolicy.attentionClaimHasExpired(
          civilDate: civilDate,
          now: now
        )
      }

    return proposalIDsBefore != snapshot.proposals.map(\.proposalId)
      || composerIDsBefore != snapshot.composerRecords.map(\.operationId)
      || reviewIDsBefore != (snapshot.workflow?.reviews.map(\.handle) ?? [])
      || activityIDsBefore != (snapshot.workflow?.activity.map(\.id) ?? [])
      || activityClearedAtBefore != snapshot.workflow?.activityClearedAt
      || privacyIDsBefore != (snapshot.workflow?.privacyOperations.map(\.id) ?? [])
      || attentionClaimsBefore != (snapshot.attentionNotificationDays ?? [:])
  }

  private func loadSnapshotApplyingRetention(
    now: Date
  ) throws -> CompanionProtectedSnapshot {
    var snapshot = try loadSnapshot()
    guard Self.applyAppOwnedRetention(in: &snapshot, now: now) else {
      return snapshot
    }
    try bumpProjectionRevision(in: &snapshot)
    try persist(snapshot)
    publishProjectionChange(revision: String(snapshot.projectionRevision ?? 0))
    return snapshot
  }

  private func transaction<Value>(
    persistMutationOnFailure: Bool = false,
    _ body: (inout CompanionProtectedSnapshot) throws -> Value
  ) -> Result<Value, CompanionStoreError> {
    do {
      var snapshot = try loadSnapshot()
      _ = Self.applyAppOwnedRetention(in: &snapshot, now: Date())
      do {
        let value = try body(&snapshot)
        try bumpProjectionRevision(in: &snapshot)
        try persist(snapshot)
        publishProjectionChange(revision: String(snapshot.projectionRevision ?? 0))
        return .success(value)
      } catch let error as CompanionStoreError {
        // Most failed CAS/review mutations must roll back and leave the public
        // revision unchanged. A small number of safety paths opt in because
        // they deliberately refine reset/dangling state before refusing the
        // requested transition.
        guard persistMutationOnFailure else { return .failure(error) }
        do {
          try bumpProjectionRevision(in: &snapshot)
          try persist(snapshot)
          publishProjectionChange(revision: String(snapshot.projectionRevision ?? 0))
          return .failure(error)
        } catch {
          return .failure(.storageUnavailable)
        }
      }
    } catch let error as CompanionStoreError {
      return .failure(error)
    } catch {
      return .failure(.storageUnavailable)
    }
  }

  private func bumpProjectionRevision(
    in snapshot: inout CompanionProtectedSnapshot
  ) throws {
    let current = snapshot.projectionRevision ?? 0
    guard current < Self.maximumProjectionRevision else {
      throw CompanionStoreError.storageUnavailable
    }
    snapshot.projectionRevision = current + 1
  }

  private func publishProjectionChange(revision: String) {
    DispatchQueue.main.async {
      NotificationCenter.default.post(
        name: .companionProtectedStoreDidChange,
        object: self,
        userInfo: ["revision": revision]
      )
    }
  }

  private func complete<Value>(
    _ result: Result<Value, CompanionStoreError>,
    completion: ((Result<Value, CompanionStoreError>) -> Void)?
  ) {
    if let completion {
      DispatchQueue.main.async { completion(result) }
    }
  }

  private func loadSnapshot() throws -> CompanionProtectedSnapshot {
    let fileURL = try storeFileURL()
    guard fileManager.fileExists(atPath: fileURL.path) else {
      return try initializeMissingStore(at: fileURL)
    }

    let key: SymmetricKey
    do {
      key = try readKeychainKey()
    } catch CompanionKeychainError.notFound {
      return try resetStore(at: fileURL)
    } catch {
      throw CompanionStoreError.storageUnavailable
    }

    do {
      let attributes = try fileManager.attributesOfItem(atPath: fileURL.path)
      if let fileSize = attributes[.size] as? NSNumber,
        fileSize.intValue > Self.maximumFileBytes
      {
        return try resetStore(at: fileURL)
      }

      var sealedData = try Data(contentsOf: fileURL, options: [.mappedIfSafe])
      defer { sealedData.resetBytes(in: 0..<sealedData.count) }
      let sealedBox = try AES.GCM.SealedBox(combined: sealedData)
      var plaintext = try AES.GCM.open(
        sealedBox,
        using: key,
        authenticating: Self.authenticatedContext
      )
      defer { plaintext.resetBytes(in: 0..<plaintext.count) }

      let decoder = JSONDecoder()
      decoder.dateDecodingStrategy = .millisecondsSince1970
      var snapshot = try decoder.decode(CompanionProtectedSnapshot.self, from: plaintext)
      guard snapshot.schemaVersion == CompanionProtectedSnapshot.currentSchemaVersion else {
        throw CompanionStoreError.unsupportedSchema
      }
      var recoveredPersistedDraft = false
      if var workflow = snapshot.workflow {
        switch IOSCompanionPersistedDraftRecovery.apply(
          to: &workflow,
          now: Date(),
          validatorVersion: IOSBirthdayMessageContentPolicy.validatorVersion,
          contentIssueCodes: { text, language in
            IOSBirthdayMessageContentPolicy.issueCodes(
              text: text,
              declaredLanguage: language
            )
          }
        ) {
        case .unchanged:
          break
        case .removedInvalidReviews:
          snapshot.workflow = workflow
          recoveredPersistedDraft = true
        case .revalidatedDraft, .clearedInvalidDraft:
          snapshot.workflow = workflow
          snapshot.proposals.removeAll()
          Self.applyReminderPlans([], to: &snapshot)
          snapshot.reminderHorizon = nil
          snapshot.pendingNativeRoute = nil
          recoveredPersistedDraft = true
        case .unrecoverable:
          return try resetStore(at: fileURL)
        }
      }
      guard snapshot.composerRecords.count <= Self.maximumComposerRecords,
        snapshot.proposals.count <= Self.maximumProposals,
        snapshot.reminderPlans.count <= Self.maximumReminderPlans,
        snapshot.notificationIdentities.count <= Self.maximumReminderPlans,
        snapshot.resetSafety.blockedCivilDates.count <= Self.maximumResetDates,
        snapshot.attentionNotificationDays.map { claims in
          claims.count <= IOSCompanionAttentionKind.allCases.count
            && claims.allSatisfy { key, civilDate in
              IOSCompanionAttentionKind(rawValue: key) != nil
                && Self.resetReleaseThreshold(for: civilDate) != nil
            }
        } ?? true,
        snapshot.pendingNativeRoute.map { route in
          Self.isBoundedOpaqueIdentifier(route.routeId)
            && route.createdAt.timeIntervalSince1970.isFinite
            && route.createdAt.timeIntervalSince1970 >= 0
        } ?? true,
        (snapshot.projectionRevision ?? 0) <= Self.maximumProjectionRevision,
        Set(snapshot.proposals.map(\.proposalId)).count == snapshot.proposals.count,
        snapshot.proposals.allSatisfy { proposal in
          Self.isBoundedOpaqueIdentifier(proposal.proposalId)
            && Self.isBoundedOpaqueIdentifier(proposal.accountGeneration)
            && Self.isBoundedOpaqueIdentifier(proposal.occurrenceId)
            && Self.resetReleaseThreshold(for: proposal.occurrenceCivilDate) != nil
            && proposal.recipient.range(
              of: "^\\+[1-9][0-9]{7,14}$",
              options: .regularExpression
            ) != nil
            && Self.isSafeMessageBody(proposal.body)
        },
        Set(snapshot.composerRecords.map(\.operationId)).count
          == snapshot.composerRecords.count,
        Set(snapshot.notificationIdentities.map(\.civilDate)).count
          == snapshot.notificationIdentities.count,
        Set(snapshot.notificationIdentities.map(\.requestId)).count
          == snapshot.notificationIdentities.count,
        Set(snapshot.reminderPlans.map(\.occurrenceId)).count
          == snapshot.reminderPlans.count,
        Set(snapshot.reminderPlans.map(\.civilDate))
          == Set(snapshot.notificationIdentities.map(\.civilDate)),
        Set(snapshot.resetSafety.blockedCivilDates).count
          == snapshot.resetSafety.blockedCivilDates.count,
        snapshot.resetSafety.verifiedCivilDate.map { verified in
          Self.resetReleaseThreshold(for: verified) != nil
            && snapshot.resetSafety.blockedCivilDates.contains(verified)
        } ?? true,
        snapshot.workflow.map(Self.validateWorkflow) ?? true
      else {
        return try resetStore(at: fileURL)
      }
      if recoveredPersistedDraft {
        try bumpProjectionRevision(in: &snapshot)
        try persist(snapshot)
        publishProjectionChange(
          revision: String(snapshot.projectionRevision ?? 0)
        )
      }
      return snapshot
    } catch CompanionStoreError.unsupportedSchema {
      throw CompanionStoreError.unsupportedSchema
    } catch let cocoaError as CocoaError
      where cocoaError.code == .fileReadNoPermission
    {
      throw CompanionStoreError.storageUnavailable
    } catch {
      return try resetStore(at: fileURL)
    }
  }

  private func resetStore(at fileURL: URL) throws -> CompanionProtectedSnapshot {
    if fileManager.fileExists(atPath: fileURL.path) {
      try fileManager.removeItem(at: fileURL)
    }
    try deleteKeychainKey(allowMissing: true)
    _ = try createKeychainKey()
    let snapshot = CompanionProtectedSnapshot.reset(
      on: Self.civilDate(for: Date(), calendar: calendar)
    )
    try persist(snapshot)
    return snapshot
  }

  private func initializeMissingStore(
    at fileURL: URL
  ) throws -> CompanionProtectedSnapshot {
    let retainedKeyExists: Bool
    do {
      _ = try readKeychainKey()
      retainedKeyExists = true
    } catch CompanionKeychainError.notFound {
      retainedKeyExists = false
    } catch {
      throw CompanionStoreError.storageUnavailable
    }
    if retainedKeyExists {
      // A device-only key without its protected file indicates reinstall or an
      // incomplete external deletion. Install a reset fence before rebuilding.
      return try resetStore(at: fileURL)
    }
    _ = try createKeychainKey()
    let snapshot = CompanionProtectedSnapshot.initialInstall
    try persist(snapshot)
    return snapshot
  }

  private func persist(_ snapshot: CompanionProtectedSnapshot) throws {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .millisecondsSince1970
    encoder.outputFormatting = [.sortedKeys]
    var plaintext = try encoder.encode(snapshot)
    defer { plaintext.resetBytes(in: 0..<plaintext.count) }

    let key: SymmetricKey
    do {
      key = try readKeychainKey()
    } catch CompanionKeychainError.notFound {
      key = try createKeychainKey()
    } catch {
      throw CompanionStoreError.storageUnavailable
    }

    let sealedBox = try AES.GCM.seal(
      plaintext,
      using: key,
      authenticating: Self.authenticatedContext
    )
    guard var combined = sealedBox.combined else {
      throw CompanionStoreError.storageUnavailable
    }
    defer { combined.resetBytes(in: 0..<combined.count) }
    guard combined.count <= Self.maximumFileBytes else {
      throw CompanionStoreError.storageUnavailable
    }

    let fileURL = try storeFileURL()
    try combined.write(to: fileURL, options: [.atomic, .completeFileProtection])
    try fileManager.setAttributes(
      [.protectionKey: FileProtectionType.complete],
      ofItemAtPath: fileURL.path
    )
    var resourceValues = URLResourceValues()
    resourceValues.isExcludedFromBackup = true
    var mutableURL = fileURL
    try mutableURL.setResourceValues(resourceValues)
  }

  private func storeFileURL() throws -> URL {
    let base = try fileManager.url(
      for: .applicationSupportDirectory,
      in: .userDomainMask,
      appropriateFor: nil,
      create: true
    )
    let directory = base.appendingPathComponent(
      "BirthdayAutopilotCompanion",
      isDirectory: true
    )
    try fileManager.createDirectory(
      at: directory,
      withIntermediateDirectories: true,
      attributes: [.protectionKey: FileProtectionType.complete]
    )
    try fileManager.setAttributes(
      [.protectionKey: FileProtectionType.complete],
      ofItemAtPath: directory.path
    )
    var resourceValues = URLResourceValues()
    resourceValues.isExcludedFromBackup = true
    var mutableDirectory = directory
    try mutableDirectory.setResourceValues(resourceValues)
    return directory.appendingPathComponent("companion-state.bin")
  }

  private func readKeychainKey() throws -> SymmetricKey {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: Self.keychainService,
      kSecAttrAccount as String: Self.keychainAccount,
      kSecAttrSynchronizable as String: kCFBooleanFalse as Any,
      kSecReturnData as String: kCFBooleanTrue as Any,
      kSecMatchLimit as String: kSecMatchLimitOne,
      kSecUseDataProtectionKeychain as String: kCFBooleanTrue as Any,
    ]
    var result: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &result)
    if status == errSecItemNotFound {
      throw CompanionKeychainError.notFound
    }
    guard status == errSecSuccess,
      var keyData = result as? Data,
      keyData.count == 32
    else {
      throw CompanionKeychainError.unavailable
    }
    defer { keyData.resetBytes(in: 0..<keyData.count) }
    return SymmetricKey(data: keyData)
  }

  private func createKeychainKey() throws -> SymmetricKey {
    var keyData = Data(count: 32)
    let randomStatus = keyData.withUnsafeMutableBytes { buffer in
      SecRandomCopyBytes(kSecRandomDefault, 32, buffer.baseAddress!)
    }
    guard randomStatus == errSecSuccess else {
      throw CompanionStoreError.storageUnavailable
    }
    defer { keyData.resetBytes(in: 0..<keyData.count) }

    let add: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: Self.keychainService,
      kSecAttrAccount as String: Self.keychainAccount,
      kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
      kSecAttrSynchronizable as String: kCFBooleanFalse as Any,
      kSecValueData as String: keyData,
      kSecUseDataProtectionKeychain as String: kCFBooleanTrue as Any,
    ]
    let status = SecItemAdd(add as CFDictionary, nil)
    if status == errSecDuplicateItem {
      return try readKeychainKey()
    }
    guard status == errSecSuccess else {
      throw CompanionStoreError.storageUnavailable
    }
    return SymmetricKey(data: keyData)
  }

  private func deleteKeychainKey(allowMissing: Bool) throws {
    for account in [Self.keychainAccount] + Self.legacyKeychainAccounts {
      let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: Self.keychainService,
        kSecAttrAccount as String: account,
        kSecAttrSynchronizable as String: kSecAttrSynchronizableAny,
        kSecUseDataProtectionKeychain as String: kCFBooleanTrue as Any,
      ]
      let status = SecItemDelete(query as CFDictionary)
      guard status == errSecSuccess || (allowMissing && status == errSecItemNotFound) else {
        throw CompanionStoreError.storageUnavailable
      }
    }
  }

  private static func randomNonce() throws -> String {
    var data = Data(count: 32)
    let status = data.withUnsafeMutableBytes { buffer in
      SecRandomCopyBytes(kSecRandomDefault, 32, buffer.baseAddress!)
    }
    guard status == errSecSuccess else {
      throw CompanionStoreError.storageUnavailable
    }
    defer { data.resetBytes(in: 0..<data.count) }
    return data.base64EncodedString()
      .replacingOccurrences(of: "+", with: "-")
      .replacingOccurrences(of: "/", with: "_")
      .replacingOccurrences(of: "=", with: "")
  }

  private static func isBoundedOpaqueIdentifier(_ value: String) -> Bool {
    value.range(
      of: "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$",
      options: .regularExpression
    ) != nil
  }

  private static func resetReleaseThreshold(for civilDate: String) -> Date? {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.isLenient = false
    guard let startUTC = formatter.date(from: civilDate),
      formatter.string(from: startUTC) == civilDate
    else {
      return nil
    }
    // The civil date finishes latest at UTC-12: start + 36h, then a 5m guard.
    return startUTC.addingTimeInterval((36 * 60 * 60) + (5 * 60))
  }

  static func civilDate(for date: Date, calendar: Calendar) -> String {
    let components = calendar.dateComponents([.year, .month, .day], from: date)
    return String(
      format: "%04d-%02d-%02d",
      components.year ?? 0,
      components.month ?? 0,
      components.day ?? 0
    )
  }
}
