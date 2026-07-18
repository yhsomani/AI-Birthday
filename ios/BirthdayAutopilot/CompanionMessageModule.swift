import Foundation
import MessageUI
import React
import UIKit

private struct CompanionReviewRequest {
  let proposalId: String
  let expectedRevision: String
}

private struct CompanionOpenRequest {
  let proposalId: String
  let expectedRevision: String
  let actionNonce: String
}

private struct CompanionForegroundPresenter {
  let controller: UIViewController
  let sceneIdentifier: String
}

private enum CompanionComposerInputError: Error {
  case invalid

  var safeCode: String { "COMPOSER_INPUT_INVALID" }
}

@objc(CompanionMessageModule)
@MainActor
final class CompanionMessageModule: NSObject,
  @preconcurrency MFMessageComposeViewControllerDelegate
{
  private static weak var liveInstance: CompanionMessageModule?
  private static var accountDeletionShutdown = false
  private static let reviewRequestKeys: Set<String> = [
    "expectedRevision",
    "proposalId",
  ]
  private static let openRequestKeys: Set<String> = [
    "actionNonce",
    "expectedRevision",
    "proposalId",
  ]
  private static let opaqueIdentifierPattern = try! NSRegularExpression(
    pattern: "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$"
  )
  private static let revisionPattern = try! NSRegularExpression(
    pattern: "^(0|[1-9][0-9]{0,18})$"
  )
  private static let noncePattern = try! NSRegularExpression(
    pattern: "^[A-Za-z0-9_-]{43}$"
  )
  private static let e164Pattern = try! NSRegularExpression(
    pattern: "^\\+[1-9][0-9]{7,14}$"
  )
  private static let urlPattern = try! NSRegularExpression(
    pattern: "(?:https?://|www\\.)\\S+",
    options: [.caseInsensitive]
  )

  private let store = CompanionProtectedStore.shared
  private let peopleStore = CompanionPeopleStore.shared
  private let peopleSync = IOSPeopleSyncCoordinator.shared
  private let workflow = IOSCompanionWorkflowEngine.shared
  private let statusClient = IOSCompanionStatusClient.shared
  private let reservationClient = IOSComposerReservationClient.shared
  private let sessionGeneration = UUID().uuidString.lowercased()
  private var isPreparing = false
  private var preparationToken: String?
  private var pendingPeopleLease: IOSPeopleComposerMaterialLease?
  private var pendingPreparedReservation: (
    binding: IOSNativeGoogleAccountBinding,
    grant: IOSComposerReservationGrant
  )?
  private var reservationDismissalWorkItem: DispatchWorkItem?
  private var presentedController: MFMessageComposeViewController?
  private var isCompletingPresentation = false
  private var pendingOperationId: String?
  private var pendingResolve: RCTPromiseResolveBlock?
  private var accountDeletionShutdownWaiters: [() -> Void] = []

  override init() {
    super.init()
    Self.liveInstance = self
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(applicationWillResignActive),
      name: UIApplication.willResignActiveNotification,
      object: nil
    )
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
  }

  @MainActor
  static func beginAccountDeletionShutdown(completion: @escaping () -> Void) {
    dispatchPrecondition(condition: .onQueue(.main))
    accountDeletionShutdown = true
    guard let instance = liveInstance else {
      completion()
      return
    }
    instance.shutdownForAccountDeletion(completion: completion)
  }

  @objc
  nonisolated static func requiresMainQueueSetup() -> Bool {
    true
  }

  @objc(canPresent:rejecter:)
  func canPresent(
    _ resolve: RCTPromiseResolveBlock,
    rejecter _: RCTPromiseRejectBlock
  ) {
    dispatchPrecondition(condition: .onQueue(.main))
    resolve(
      UIApplication.shared.applicationState == .active
        && MFMessageComposeViewController.canSendText() && Self.foregroundPresenter() != nil
        && !Self.accountDeletionShutdown
        && !IOSPeopleBackgroundRefreshCoordinator.shared
          .contactsAccessIsSuspendedForPrivacy
        && !IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal()
        && !isPreparing && presentedController == nil
    )
  }

  /// Loads the protected proposal and mints one short-lived, one-use native
  /// action nonce for the current foreground scene. No presentation happens.
  @objc(prepareComposerReview:resolver:rejecter:)
  func prepareComposerReview(
    _ rawRequest: NSDictionary,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    dispatchPrecondition(condition: .onQueue(.main))
    guard !Self.accountDeletionShutdown,
      !IOSPeopleBackgroundRefreshCoordinator.shared
        .contactsAccessIsSuspendedForPrivacy,
      !IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal(),
      !isPreparing,
      presentedController == nil, pendingResolve == nil,
      let presenter = Self.foregroundPresenter()
    else {
      rejectSafe(reject, code: "COMPOSER_FOREGROUND_REQUIRED")
      return
    }

    let request: CompanionReviewRequest
    do {
      request = try Self.validateReviewRequest(rawRequest)
    } catch let error as CompanionComposerInputError {
      rejectSafe(reject, code: error.safeCode)
      return
    } catch {
      rejectSafe(reject, code: "COMPOSER_INPUT_INVALID")
      return
    }

    guard let binding = IOSGoogleIdentityCoordinator.shared.exactSessionBinding() else {
      rejectSafe(reject, code: "COMPANION_ACCOUNT_UNAVAILABLE")
      return
    }
    isPreparing = true
    store.readProjectionStatus { [weak self] result in
      guard let self else {
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      guard case .success(let status) = result,
        status.workflow?.privacyOperations.contains(where: {
          !["complete", "failed"].contains($0.phase)
        }) != true,
        let originalMaterial = self.workflow.lazyProposalMaterial(
          proposalId: request.proposalId,
          expectedRevision: request.expectedRevision,
          status: status,
          binding: binding,
          requireTrustedFreshness: false
        )
      else {
        self.isPreparing = false
        self.rejectSafe(reject, code: "COMPOSER_PROPOSAL_STALE")
        return
      }
      self.refreshPeopleBeforeReview(
        request: request,
        originalMaterial: originalMaterial,
        binding: binding,
        presenter: presenter,
        resolve: resolve,
        reject: reject
      )
    }
  }

  private func refreshPeopleBeforeReview(
    request: CompanionReviewRequest,
    originalMaterial: IOSCompanionLazyProposalMaterial,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard !IOSPeopleBackgroundRefreshCoordinator.shared
      .contactsAccessIsSuspendedForPrivacy,
      !IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal()
    else {
      isPreparing = false
      rejectSafe(reject, code: "COMPOSER_CONTACTS_FRESHNESS_UNAVAILABLE")
      return
    }
    peopleSync.sync(interactiveAuthorization: false) { [weak self] outcome in
      guard let self else {
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      guard case .completed = outcome else {
        self.isPreparing = false
        let code: String
        if case .failed(let failure) = outcome,
          [.authorizationRequired, .forbidden, .repeatedUnauthorized].contains(failure)
        {
          code = "COMPOSER_CONTACTS_RECONNECT_REQUIRED"
        } else {
          code = "COMPOSER_CONTACTS_FRESHNESS_UNAVAILABLE"
        }
        self.rejectSafe(reject, code: code)
        return
      }
      guard !Self.accountDeletionShutdown,
        IOSGoogleIdentityCoordinator.shared.exactSessionBinding() == binding,
        let currentPresenter = Self.foregroundPresenter(),
        currentPresenter.sceneIdentifier == presenter.sceneIdentifier
      else {
        self.isPreparing = false
        self.rejectSafe(reject, code: "COMPOSER_FOREGROUND_REQUIRED")
        return
      }
      self.workflow.reconcileAfterPeopleSync(binding: binding) { [weak self] in
        self?.validateRefreshedReview(
          request: request,
          originalMaterial: originalMaterial,
          binding: binding,
          presenter: presenter,
          resolve: resolve,
          reject: reject
        )
      }
    }
  }

  private func validateRefreshedReview(
    request: CompanionReviewRequest,
    originalMaterial: IOSCompanionLazyProposalMaterial,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    store.readProjectionStatus { [weak self] result in
      guard let self else {
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      guard case .success(let status) = result,
        let refreshedMaterial = self.workflow.lazyProposalMaterial(
          proposalId: request.proposalId,
          expectedRevision: nil,
          status: status,
          binding: binding,
          requireTrustedFreshness: false
        ), refreshedMaterial == originalMaterial,
        !Self.accountDeletionShutdown,
        IOSGoogleIdentityCoordinator.shared.exactSessionBinding() == binding,
        let currentPresenter = Self.foregroundPresenter(),
        currentPresenter.sceneIdentifier == presenter.sceneIdentifier
      else {
        self.isPreparing = false
        self.rejectSafe(reject, code: "COMPOSER_PROPOSAL_STALE")
        return
      }
      let refreshedRequest = CompanionReviewRequest(
        proposalId: request.proposalId,
        expectedRevision: status.revision
      )
      self.refreshCoexistenceAndPrepare(
        request: refreshedRequest,
        expectedMaterial: refreshedMaterial,
        binding: binding,
        presenter: presenter,
        resolve: resolve,
        reject: reject
      )
    }
  }

  private func refreshCoexistenceAndPrepare(
    request: CompanionReviewRequest,
    expectedMaterial: IOSCompanionLazyProposalMaterial,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    statusClient.refreshControlImmediatelyBeforeReview(binding: binding) {
      [weak self] status in
      guard let self else {
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      guard case .success = status else {
        self.isPreparing = false
        let code: String
        if case .failure(let failure) = status {
          switch failure {
          case .managedByAndroid: code = "COMPOSER_MANAGED_BY_ANDROID"
          case .deleting: code = "COMPOSER_ACCOUNT_DELETING"
          case .accountChanged: code = "COMPANION_ACCOUNT_UNAVAILABLE"
          case .configuration, .unavailable: code = "COMPOSER_COEXISTENCE_UNVERIFIED"
          }
        } else {
          code = "COMPOSER_COEXISTENCE_UNVERIFIED"
        }
        self.rejectSafe(reject, code: code)
        return
      }
      guard !Self.accountDeletionShutdown,
        let currentBinding = IOSGoogleIdentityCoordinator.shared.exactSessionBinding(),
        currentBinding == binding,
        let currentPresenter = Self.foregroundPresenter(),
        currentPresenter.sceneIdentifier == presenter.sceneIdentifier
      else {
        self.isPreparing = false
        self.rejectSafe(reject, code: "COMPOSER_FOREGROUND_REQUIRED")
        return
      }
      self.store.readProjectionStatus { [weak self] result in
        guard let self, case .success(let refreshedStatus) = result,
          refreshedStatus.coexistence == .clear,
          let finalMaterial = self.workflow.lazyProposalMaterial(
            proposalId: request.proposalId,
            expectedRevision: request.expectedRevision,
            status: refreshedStatus,
            binding: binding,
            requireTrustedFreshness: true
          ), finalMaterial == expectedMaterial,
          let finalPresenter = Self.foregroundPresenter(),
          finalPresenter.sceneIdentifier == presenter.sceneIdentifier
        else {
          self?.isPreparing = false
          self?.rejectSafe(reject, code: "COMPOSER_PROPOSAL_STALE")
          return
        }
        self.prepareStoredReview(
          request: request,
          material: finalMaterial,
          presenter: presenter,
          resolve: resolve,
          reject: reject
        )
      }
    }
  }

  private func prepareStoredReview(
    request: CompanionReviewRequest,
    material: IOSCompanionLazyProposalMaterial,
    presenter: CompanionForegroundPresenter,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    store.prepareComposerReview(
      material: material,
      expectedRevision: request.expectedRevision,
      sessionGeneration: sessionGeneration,
      sceneIdentifier: presenter.sceneIdentifier,
      now: Date()
    ) { [weak self] result in
      guard let self else {
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      self.isPreparing = false
      guard !Self.accountDeletionShutdown,
        let currentPresenter = Self.foregroundPresenter(),
        currentPresenter.sceneIdentifier == presenter.sceneIdentifier
      else {
        self.rejectSafe(reject, code: "COMPOSER_FOREGROUND_REQUIRED")
        return
      }
      switch result {
      case .failure(let error):
        self.rejectSafe(reject, code: error.safeCode)
      case .success(let projection):
        guard Self.isSafeRecipient(projection.recipient),
          Self.isSafeMessageBody(projection.body),
          let maskedDestination = Self.maskedDestination(projection.recipient)
        else {
          self.rejectSafe(reject, code: "COMPOSER_PROPOSAL_INVALID")
          return
        }
        resolve([
          "actionNonce": projection.actionNonce,
          "body": projection.body,
          "expiresAtEpochMilliseconds": projection.expiresAt.timeIntervalSince1970 * 1_000,
          "maskedDestination": maskedDestination,
          "proposalId": projection.proposalId,
          "revision": projection.revision,
        ])
      }
    }
  }

  /// Presents MessageUI from a protected native proposal. React Native can pass
  /// only the proposal identity/revision and the one-use review nonce; it cannot
  /// supply or replace a recipient or body at this presentation boundary.
  @objc(presentUserConfirmedComposer:resolver:rejecter:)
  func presentUserConfirmedComposer(
    _ rawRequest: NSDictionary,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    dispatchPrecondition(condition: .onQueue(.main))
    guard !Self.accountDeletionShutdown,
      !IOSPeopleBackgroundRefreshCoordinator.shared
        .contactsAccessIsSuspendedForPrivacy,
      !IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal(),
      !isPreparing,
      presentedController == nil, pendingResolve == nil
    else {
      rejectSafe(reject, code: "COMPOSER_ALREADY_IN_PROGRESS")
      return
    }

    let request: CompanionOpenRequest
    do {
      request = try Self.validateOpenRequest(rawRequest)
    } catch let error as CompanionComposerInputError {
      rejectSafe(reject, code: error.safeCode)
      return
    } catch {
      rejectSafe(reject, code: "COMPOSER_INPUT_INVALID")
      return
    }
    guard Self.presentationPreconditionsHold(),
      let presenter = Self.foregroundPresenter()
    else {
      rejectSafe(reject, code: "COMPOSER_FOREGROUND_REQUIRED")
      return
    }

    let token = UUID().uuidString.lowercased()
    preparationToken = token
    isPreparing = true
    store.readProjectionStatus { [weak self] result in
      guard let self, case .success(let status) = result,
        self.preparationToken == token,
        status.coexistence == .clear,
        let binding = IOSGoogleIdentityCoordinator.shared.exactSessionBinding(),
        let material = self.workflow.lazyProposalMaterial(
          proposalId: request.proposalId,
          expectedRevision: nil,
          status: status,
          binding: binding,
          requireTrustedFreshness: true
        ),
        status.proposals.contains(where: {
          $0.proposalId == request.proposalId
            && $0.revision == request.expectedRevision
            && $0.occurrenceDigest == material.occurrenceDigest
            && $0.contactMaterialRevision == material.contactMaterialRevision
            && $0.peopleSnapshotGeneration == material.peopleSnapshotGeneration
            && $0.selectedPhoneId == material.selectedPhoneId
            && $0.recipient == material.recipient && $0.body == material.body
        }),
        let currentPresenter = Self.foregroundPresenter(),
        currentPresenter.sceneIdentifier == presenter.sceneIdentifier
      else {
        self?.cancelPendingPreparation()
        self?.rejectSafe(reject, code: "COMPOSER_PROPOSAL_STALE")
        return
      }
      self.acquirePeopleLeaseAndReservation(
        request: request,
        material: material,
        binding: binding,
        presenter: presenter,
        preparationToken: token,
        resolve: resolve,
        reject: reject
      )
    }
  }

  private func acquirePeopleLeaseAndReservation(
    request: CompanionOpenRequest,
    material: IOSCompanionLazyProposalMaterial,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter,
    preparationToken token: String,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    peopleStore.acquireComposerMaterialLease(
      expectedBinding: binding,
      expectedSnapshotGeneration: material.peopleSnapshotGeneration,
      contactId: material.contactId,
      expectedMaterialRevision: material.contactMaterialRevision,
      selectedPhoneId: material.selectedPhoneId,
      expectedRecipient: material.recipient
    ) { [weak self] lease in
      guard let lease else {
        self?.cancelPendingPreparation()
        self?.rejectSafe(reject, code: "COMPOSER_PROPOSAL_STALE")
        return
      }
      guard let self else {
        CompanionPeopleStore.shared.releaseComposerMaterialLease(lease)
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      guard self.preparationStillValid(
        token: token,
        binding: binding,
        presenter: presenter
      ) else {
        self.peopleStore.releaseComposerMaterialLease(lease)
        self.cancelPendingPreparation()
        self.rejectSafe(reject, code: "COMPOSER_FOREGROUND_REQUIRED")
        return
      }
      self.pendingPeopleLease = lease
      self.reservationClient.acquireImmediatelyBeforePresentation(
        binding: binding
      ) { [weak self] reservationResult in
        guard let self else {
          CompanionPeopleStore.shared.releaseComposerMaterialLease(lease)
          if case .success(let grant) = reservationResult,
            grant.earlyReleaseAllowed
          {
            IOSComposerReservationClient.shared.releasePreparedReservation(
              binding: binding,
              reservationId: grant.reservationId
            )
          }
          reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
          return
        }
        switch reservationResult {
        case .failure(let failure):
          self.cancelPendingPreparation()
          self.rejectSafe(
            reject,
            code: Self.reservationFailureCode(failure)
          )
        case .success(let grant):
          guard self.preparationStillValid(
            token: token,
            binding: binding,
            presenter: presenter
          ) else {
            if grant.earlyReleaseAllowed {
              self.reservationClient.releasePreparedReservation(
                binding: binding,
                reservationId: grant.reservationId
              )
            }
            self.cancelPendingPreparation()
            self.rejectSafe(reject, code: "COMPOSER_FOREGROUND_REQUIRED")
            return
          }
          self.pendingPreparedReservation = (binding, grant)
          self.revalidateBeforeStickyCommit(
            request: request,
            material: material,
            binding: binding,
            presenter: presenter,
            preparationToken: token,
            lease: lease,
            grant: grant,
            resolve: resolve,
            reject: reject
          )
        }
      }
    }
  }

  private func revalidateBeforeStickyCommit(
    request: CompanionOpenRequest,
    material: IOSCompanionLazyProposalMaterial,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter,
    preparationToken token: String,
    lease: IOSPeopleComposerMaterialLease,
    grant: IOSComposerReservationGrant,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    store.readProjectionStatus { [weak self] result in
      guard let self else {
        CompanionPeopleStore.shared.releaseComposerMaterialLease(lease)
        if grant.earlyReleaseAllowed {
          IOSComposerReservationClient.shared.releasePreparedReservation(
            binding: binding,
            reservationId: grant.reservationId
          )
        }
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      guard case .success(let status) = result,
        self.pendingPeopleLease == lease,
        self.preparationStillValid(
          token: token,
          binding: binding,
          presenter: presenter
        ),
        status.coexistence == .clear,
        let currentMaterial = self.workflow.lazyProposalMaterial(
          proposalId: request.proposalId,
          expectedRevision: request.expectedRevision,
          status: status,
          binding: binding,
          requireTrustedFreshness: true
        ), currentMaterial == material,
        status.proposals.contains(where: {
          $0.proposalId == request.proposalId
            && $0.revision == request.expectedRevision
            && $0.peopleSnapshotGeneration == material.peopleSnapshotGeneration
            && $0.occurrenceDigest == material.occurrenceDigest
            && $0.contactMaterialRevision == material.contactMaterialRevision
            && $0.selectedPhoneId == material.selectedPhoneId
            && $0.recipient == material.recipient && $0.body == material.body
        })
      else {
        self.cancelPendingPreparation()
        self.rejectSafe(reject, code: "COMPOSER_PROPOSAL_STALE")
        return
      }
      self.peopleStore.validateAndRetainComposerMaterialLease(
        lease,
        expectedBinding: binding,
        expectedSnapshotGeneration: material.peopleSnapshotGeneration,
        contactId: material.contactId,
        expectedMaterialRevision: material.contactMaterialRevision,
        selectedPhoneId: material.selectedPhoneId,
        expectedRecipient: material.recipient
      ) { [weak self] retained in
        guard let self else {
          CompanionPeopleStore.shared.releaseComposerMaterialLease(lease)
          if grant.earlyReleaseAllowed {
            IOSComposerReservationClient.shared.releasePreparedReservation(
              binding: binding,
              reservationId: grant.reservationId
            )
          }
          reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
          return
        }
        guard retained, self.pendingPeopleLease == lease,
          self.preparationStillValid(
            token: token,
            binding: binding,
            presenter: presenter
          )
        else {
          self.cancelPendingPreparation()
          self.rejectSafe(reject, code: "COMPOSER_PROPOSAL_STALE")
          return
        }
        // From this point forward People mutations remain fenced until an
        // explicit release, and local state is sticky before the server commit
        // attempt. No cancellation or later proposal may release the server fence.
        self.pendingPreparedReservation = nil
        self.reservationClient.commitStickyImmediatelyBeforePresentation(
          binding: binding,
          grant: grant
        ) { [weak self] commitResult in
          guard let self else {
            CompanionPeopleStore.shared.releaseComposerMaterialLease(lease)
            reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
            return
          }
          switch commitResult {
          case .failure(let failure):
            self.cancelPendingPreparation()
            self.rejectSafe(reject, code: Self.reservationFailureCode(failure))
          case .success(let committed):
            guard self.preparationStillValid(
              token: token,
              binding: binding,
              presenter: presenter
            ) else {
              self.cancelPendingPreparation()
              self.rejectSafe(reject, code: "COMPOSER_FOREGROUND_REQUIRED")
              return
            }
            self.scheduleReservationDismissal(committed)
            self.commitValidatedComposer(
              request: request,
              binding: binding,
              presenter: presenter,
              preparationToken: token,
              lease: lease,
              material: material,
              resolve: resolve,
              reject: reject
            )
          }
        }
      }
    }
  }

  private func commitValidatedComposer(
    request: CompanionOpenRequest,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter,
    preparationToken token: String,
    lease: IOSPeopleComposerMaterialLease,
    material: IOSCompanionLazyProposalMaterial,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    store.commitComposerOpen(
      proposalId: request.proposalId,
      expectedRevision: request.expectedRevision,
      expectedPeopleSnapshotGeneration: material.peopleSnapshotGeneration,
      actionNonce: request.actionNonce,
      sessionGeneration: sessionGeneration,
      sceneIdentifier: presenter.sceneIdentifier,
      now: Date()
    ) { [weak self] result in
      guard let self else {
        CompanionPeopleStore.shared.releaseComposerMaterialLease(lease)
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      switch result {
      case .failure(let error):
        self.cancelPendingPreparation()
        self.rejectSafe(reject, code: error.safeCode)
      case .success(let commit):
        self.peopleStore.validateAndRetainComposerMaterialLease(
          lease,
          expectedBinding: binding,
          expectedSnapshotGeneration: material.peopleSnapshotGeneration,
          contactId: material.contactId,
          expectedMaterialRevision: material.contactMaterialRevision,
          selectedPhoneId: material.selectedPhoneId,
          expectedRecipient: material.recipient
        ) { [weak self] retained in
          guard let self else {
            CompanionPeopleStore.shared.releaseComposerMaterialLease(lease)
            reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
            return
          }
          guard retained else {
            self.cancelPendingPreparation()
            self.finishPostCommitFailure(operationId: commit.operationId) {
              disposition in
              switch disposition {
              case .definitiveFailure:
                self.rejectSafe(reject, code: "COMPOSER_PROPOSAL_STALE")
              case .outcomeUnknown:
                resolve("unknown")
              }
            }
            return
          }
          self.presentCommittedComposer(
            commit,
            binding: binding,
            presenter: presenter,
            preparationToken: token,
            lease: lease,
            resolve: resolve,
            reject: reject
          )
        }
      }
    }
  }

  private func presentCommittedComposer(
    _ commit: CompanionPresentationCommit,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter,
    preparationToken token: String,
    lease: IOSPeopleComposerMaterialLease,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard pendingPeopleLease == lease,
      preparationStillValid(
        token: token,
        binding: binding,
        presenter: presenter
      ),
      Self.isSafeRecipient(commit.recipient),
      Self.isSafeMessageBody(commit.body),
      let currentPresenter = Self.foregroundPresenter(),
      currentPresenter.sceneIdentifier == presenter.sceneIdentifier
    else {
      cancelPendingPreparation()
      finishPostCommitFailure(operationId: commit.operationId) {
        disposition in
        switch disposition {
        case .definitiveFailure:
          self.rejectSafe(reject, code: "COMPOSER_PRESENTATION_REFUSED")
        case .outcomeUnknown:
          resolve("unknown")
        }
      }
      return
    }

    let controller = MFMessageComposeViewController()
    controller.messageComposeDelegate = self
    controller.recipients = [commit.recipient]
    controller.body = commit.body

    pendingOperationId = commit.operationId
    pendingResolve = resolve
    presentedController = controller
    preparationToken = nil
    isPreparing = false

    currentPresenter.controller.present(
      controller,
      animated: true
    ) { [weak self, weak controller] in
      guard let self, let controller else {
        CompanionPeopleStore.shared.releaseComposerMaterialLease(lease)
        return
      }
      guard controller.presentingViewController != nil else {
        self.completePresentationFailure(
          controller: controller,
          operationId: commit.operationId
        )
        return
      }
      self.releasePendingPeopleLease(expected: lease)
      self.store.markComposerPresented(operationId: commit.operationId) {
        [weak self, weak controller] result in
        guard let self, let controller else { return }
        if case .failure = result {
          self.completeUnknownAfterPresentation(
            controller: controller,
            operationId: commit.operationId
          )
        }
      }
    }
  }

  func messageComposeViewController(
    _ controller: MFMessageComposeViewController,
    didFinishWith result: MessageComposeResult
  ) {
    dispatchPrecondition(condition: .onQueue(.main))
    guard controller === presentedController else {
      controller.dismiss(animated: true)
      return
    }
    // A mark-presented failure, foreground transition, or account-deletion
    // shutdown may already own the terminal write and dismissal. A concurrent
    // MessageUI delegate callback must not dismiss ahead of that durable write.
    guard !isCompletingPresentation else { return }
    guard let operationId = pendingOperationId else {
      controller.dismiss(animated: true)
      return
    }
    isCompletingPresentation = true

    let delegateResult: IOSComposerDelegateResult
    switch result {
    case .cancelled:
      delegateResult = .cancelled
    case .sent:
      // This is only MessageUI's result. The app does not know the final
      // recipient/body, sender line, transport, carrier acceptance, or delivery.
      delegateResult = .reportedSent
    case .failed:
      delegateResult = .failed
    @unknown default:
      delegateResult = .unknown
    }

    let resolve = pendingResolve
    IOSComposerDelegateTerminalSequencer.finish(
      result: delegateResult,
      persist: { [store] outcome, persisted in
        store.finishComposerOperation(
          operationId: operationId,
          outcome: outcome
        ) { result in
          if case .success = result {
            persisted(true)
          } else {
            persisted(false)
          }
        }
      },
      dismiss: { afterDismissal in
        controller.dismiss(animated: true, completion: afterDismissal)
      }
    ) { [weak self] publicOutcome in
      self?.clearPendingPresentation()
      resolve?(publicOutcome)
    }
  }

  private func completePresentationFailure(
    controller: MFMessageComposeViewController,
    operationId: String
  ) {
    guard controller === presentedController,
      operationId == pendingOperationId, !isCompletingPresentation
    else { return }
    isCompletingPresentation = true
    releasePendingPeopleLease()
    let resolve = pendingResolve
    finishPostCommitFailure(operationId: operationId) { [weak self] disposition in
      self?.clearPendingPresentation()
      switch disposition {
      case .definitiveFailure:
        resolve?("failed")
      case .outcomeUnknown:
        resolve?("unknown")
      }
    }
  }

  private func finishPostCommitFailure(
    operationId: String,
    completion: @escaping (IOSComposerTerminalPersistenceDisposition) -> Void
  ) {
    store.finishComposerOperation(
      operationId: operationId,
      outcome: .failed
    ) { result in
      completion(IOSComposerTerminalPersistencePolicy.disposition(for: result))
    }
  }

  private func completeUnknownAfterPresentation(
    controller: MFMessageComposeViewController,
    operationId: String
  ) {
    guard controller === presentedController,
      operationId == pendingOperationId, !isCompletingPresentation
    else { return }
    isCompletingPresentation = true
    releasePendingPeopleLease()
    let resolve = pendingResolve
    store.finishComposerOperation(
      operationId: operationId,
      outcome: .outcomeUnknown
    ) { [weak self] _ in
      controller.dismiss(animated: true) { [weak self] in
        self?.clearPendingPresentation()
        resolve?("unknown")
      }
    }
  }

  private func preparationStillValid(
    token: String,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter
  ) -> Bool {
    guard preparationToken == token, !Self.accountDeletionShutdown,
      !IOSPeopleBackgroundRefreshCoordinator.shared
        .contactsAccessIsSuspendedForPrivacy,
      !IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal(),
      IOSGoogleIdentityCoordinator.shared.exactSessionBinding() == binding,
      Self.presentationPreconditionsHold(),
      let currentPresenter = Self.foregroundPresenter()
    else { return false }
    return currentPresenter.sceneIdentifier == presenter.sceneIdentifier
  }

  private func cancelPendingPreparation() {
    preparationToken = nil
    if let pending = pendingPreparedReservation,
      pending.grant.earlyReleaseAllowed
    {
      reservationClient.releasePreparedReservation(
        binding: pending.binding,
        reservationId: pending.grant.reservationId
      )
    }
    pendingPreparedReservation = nil
    releasePendingPeopleLease()
    reservationDismissalWorkItem?.cancel()
    reservationDismissalWorkItem = nil
    isPreparing = false
  }

  private func releasePendingPeopleLease(
    expected: IOSPeopleComposerMaterialLease? = nil
  ) {
    guard let lease = pendingPeopleLease,
      expected.map({ $0 == lease }) ?? true
    else { return }
    pendingPeopleLease = nil
    peopleStore.releaseComposerMaterialLease(lease)
  }

  private func scheduleReservationDismissal(
    _ reservation: IOSComposerCommittedReservation
  ) {
    reservationDismissalWorkItem?.cancel()
    let work = DispatchWorkItem { [weak self] in
      guard let self else { return }
      guard let controller = self.presentedController,
        let operationId = self.pendingOperationId
      else {
        self.cancelPendingPreparation()
        return
      }
      self.completeUnknownAfterPresentation(
        controller: controller,
        operationId: operationId
      )
    }
    reservationDismissalWorkItem = work
    DispatchQueue.main.asyncAfter(
      deadline: .now() + reservation.safeDismissAfter,
      execute: work
    )
  }

  @objc
  private func applicationWillResignActive() {
    dispatchPrecondition(condition: .onQueue(.main))
    if let controller = presentedController, let operationId = pendingOperationId {
      completeUnknownAfterPresentation(
        controller: controller,
        operationId: operationId
      )
      return
    }
    if isPreparing {
      cancelPendingPreparation()
    }
  }

  private static func reservationFailureCode(
    _ failure: IOSComposerReservationFailure
  ) -> String {
    switch failure {
    case .accountChanged: return "COMPANION_ACCOUNT_UNAVAILABLE"
    case .deleting: return "COMPOSER_ACCOUNT_DELETING"
    case .managedByAndroid: return "COMPOSER_MANAGED_BY_ANDROID"
    case .reservationHeld: return "COMPOSER_RESERVATION_HELD"
    case .stale: return "COMPOSER_RESERVATION_STALE"
    case .configuration, .unavailable: return "COMPOSER_COEXISTENCE_UNVERIFIED"
    }
  }

  private func clearPendingPresentation() {
    releasePendingPeopleLease()
    reservationDismissalWorkItem?.cancel()
    reservationDismissalWorkItem = nil
    preparationToken = nil
    pendingPreparedReservation = nil
    pendingResolve = nil
    pendingOperationId = nil
    presentedController = nil
    isPreparing = false
    isCompletingPresentation = false
    let shutdownWaiters = accountDeletionShutdownWaiters
    accountDeletionShutdownWaiters.removeAll()
    // Terminal callers resolve their JavaScript promise immediately after this
    // reset. Defer deletion continuation one main-queue turn so that projection
    // is delivered before destructive privacy teardown resumes.
    if !shutdownWaiters.isEmpty {
      DispatchQueue.main.async {
        shutdownWaiters.forEach { $0() }
      }
    }
  }

  private func shutdownForAccountDeletion(completion: @escaping () -> Void) {
    dispatchPrecondition(condition: .onQueue(.main))
    cancelPendingPreparation()
    if isCompletingPresentation {
      // The active terminal path already owns persistence and dismissal. Join
      // it instead of dismissing ahead of its durable terminal write.
      accountDeletionShutdownWaiters.append(completion)
      return
    }
    guard let controller = presentedController,
      let operationId = pendingOperationId
    else {
      completion()
      return
    }
    accountDeletionShutdownWaiters.append(completion)
    isCompletingPresentation = true
    let resolve = pendingResolve
    store.finishComposerOperation(
      operationId: operationId,
      outcome: .outcomeUnknown
    ) { [weak self, weak controller] _ in
      guard let self else {
        completion()
        return
      }
      let finish = {
        self.clearPendingPresentation()
        resolve?("unknown")
      }
      if let controller, controller.presentingViewController != nil {
        controller.dismiss(animated: false, completion: finish)
      } else {
        finish()
      }
    }
  }

  private func rejectSafe(_ reject: RCTPromiseRejectBlock, code: String) {
    reject(code, code, nil)
  }

  private static func validateReviewRequest(
    _ rawRequest: NSDictionary
  ) throws -> CompanionReviewRequest {
    guard let request = rawRequest as? [String: Any],
      Set(request.keys) == reviewRequestKeys,
      let proposalId = request["proposalId"] as? String,
      let expectedRevision = request["expectedRevision"] as? String,
      isValidOpaqueIdentifier(proposalId),
      matches(expectedRevision, regularExpression: revisionPattern)
    else {
      throw CompanionComposerInputError.invalid
    }
    return CompanionReviewRequest(
      proposalId: proposalId,
      expectedRevision: expectedRevision
    )
  }

  private static func validateOpenRequest(
    _ rawRequest: NSDictionary
  ) throws -> CompanionOpenRequest {
    guard let request = rawRequest as? [String: Any],
      Set(request.keys) == openRequestKeys,
      let proposalId = request["proposalId"] as? String,
      let expectedRevision = request["expectedRevision"] as? String,
      let actionNonce = request["actionNonce"] as? String,
      isValidOpaqueIdentifier(proposalId),
      matches(expectedRevision, regularExpression: revisionPattern),
      matches(actionNonce, regularExpression: noncePattern)
    else {
      throw CompanionComposerInputError.invalid
    }
    return CompanionOpenRequest(
      proposalId: proposalId,
      expectedRevision: expectedRevision,
      actionNonce: actionNonce
    )
  }

  static func isValidOpaqueIdentifier(_ value: String) -> Bool {
    matches(value, regularExpression: opaqueIdentifierPattern)
  }

  private static func isSafeRecipient(_ value: String) -> Bool {
    matches(value, regularExpression: e164Pattern)
  }

  private static func maskedDestination(_ value: String) -> String? {
    guard isSafeRecipient(value) else { return nil }
    let suffix = value.suffix(4)
    guard suffix.count == 4, suffix.allSatisfy({ $0.isNumber }) else { return nil }
    return "•••• \(suffix)"
  }

  private static func matches(
    _ value: String,
    regularExpression: NSRegularExpression
  ) -> Bool {
    let range = NSRange(value.startIndex..<value.endIndex, in: value)
    return regularExpression.firstMatch(in: value, range: range)?.range == range
  }

  private static func containsMatch(
    _ value: String,
    regularExpression: NSRegularExpression
  ) -> Bool {
    let range = NSRange(value.startIndex..<value.endIndex, in: value)
    return regularExpression.firstMatch(in: value, range: range) != nil
  }

  private static func isSafeMessageBody(_ value: String) -> Bool {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty,
      value.count <= 1_000,
      value.lengthOfBytes(using: .utf8) <= 4_096,
      !containsMatch(value, regularExpression: urlPattern)
    else { return false }

    let forbiddenBidiScalars = CharacterSet(
      charactersIn:
        "\u{061C}\u{200E}\u{200F}\u{202A}\u{202B}\u{202C}\u{202D}\u{202E}"
        + "\u{2066}\u{2067}\u{2068}\u{2069}"
    )
    return value.unicodeScalars.allSatisfy { scalar in
      if forbiddenBidiScalars.contains(scalar) { return false }
      let codePoint = scalar.value
      if codePoint <= 0x1F {
        return codePoint == 0x09 || codePoint == 0x0A || codePoint == 0x0D
      }
      return codePoint != 0x7F
    } && IOSBirthdayMessageContentPolicy.isSafeRenderedBody(value)
  }

  private static func presentationPreconditionsHold() -> Bool {
    UIApplication.shared.applicationState == .active
      && MFMessageComposeViewController.canSendText() && foregroundPresenter() != nil
      && !IOSPeopleBackgroundRefreshCoordinator.shared
        .contactsAccessIsSuspendedForPrivacy
      && !IOSCompanionWipeRecoveryStore.shared.hasPendingOrUnreadableJournal()
  }

  private static func foregroundPresenter() -> CompanionForegroundPresenter? {
    let activeScenes = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }
      .filter { $0.activationState == .foregroundActive }
    guard activeScenes.count == 1,
      let scene = activeScenes.first,
      let window = scene.windows.first(where: { $0.isKeyWindow }),
      let presenter = visibleViewController(from: window.rootViewController),
      presenter.viewIfLoaded?.window === window,
      presenter.presentedViewController == nil,
      !(presenter is UIAlertController),
      !(presenter is MFMessageComposeViewController),
      !presenter.isBeingDismissed
    else { return nil }
    return CompanionForegroundPresenter(
      controller: presenter,
      sceneIdentifier: scene.session.persistentIdentifier
    )
  }

  private static func visibleViewController(
    from controller: UIViewController?
  ) -> UIViewController? {
    if let presented = controller?.presentedViewController {
      return visibleViewController(from: presented)
    }
    if let navigation = controller as? UINavigationController {
      return visibleViewController(from: navigation.visibleViewController)
    }
    if let tabs = controller as? UITabBarController {
      return visibleViewController(from: tabs.selectedViewController)
    }
    return controller
  }
}
