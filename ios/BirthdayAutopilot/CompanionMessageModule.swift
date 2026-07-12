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

private struct CompanionReviewMaterial: Equatable {
  let accountGeneration: String
  let occurrenceId: String
  let occurrenceCivilDate: String
  let recipient: String
  let body: String
  let state: CompanionProposalState
  let contactMaterialRevision: UInt64
  let selectedPhoneId: String
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
final class CompanionMessageModule: NSObject, MFMessageComposeViewControllerDelegate {
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
  private let sessionGeneration = UUID().uuidString.lowercased()
  private var isPreparing = false
  private var presentedController: MFMessageComposeViewController?
  private var pendingOperationId: String?
  private var pendingResolve: RCTPromiseResolveBlock?

  override init() {
    super.init()
    Self.liveInstance = self
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
  static func requiresMainQueueSetup() -> Bool {
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
        let originalMaterial = self.reviewMaterial(
          proposalId: request.proposalId,
          expectedRevision: request.expectedRevision,
          status: status,
          binding: binding
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
    originalMaterial: CompanionReviewMaterial,
    binding: IOSNativeGoogleAccountBinding,
    presenter: CompanionForegroundPresenter,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    guard !IOSPeopleBackgroundRefreshCoordinator.shared
      .contactsAccessIsSuspendedForPrivacy
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
    originalMaterial: CompanionReviewMaterial,
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
        let refreshedMaterial = self.reviewMaterial(
          proposalId: request.proposalId,
          expectedRevision: nil,
          status: status,
          binding: binding
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
        binding: binding,
        presenter: presenter,
        resolve: resolve,
        reject: reject
      )
    }
  }

  private func refreshCoexistenceAndPrepare(
    request: CompanionReviewRequest,
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
      self.prepareStoredReview(
        request: request,
        presenter: presenter,
        resolve: resolve,
        reject: reject
      )
    }
  }

  private func reviewMaterial(
    proposalId: String,
    expectedRevision: String?,
    status: CompanionProjectionStatus,
    binding: IOSNativeGoogleAccountBinding
  ) -> CompanionReviewMaterial? {
    guard expectedRevision == nil || status.revision == expectedRevision,
      let workflow = status.workflow, workflow.account.matches(binding),
      let proposal = status.proposals.first(where: { $0.proposalId == proposalId }),
      proposal.accountGeneration == binding.accountGeneration,
      let occurrence = workflow.occurrences.first(where: { $0.proposalId == proposalId }),
      occurrence.occurrenceId == proposal.occurrenceId,
      occurrence.civilDate == proposal.occurrenceCivilDate,
      let configuration = workflow.contacts.first(where: {
        $0.contactId == occurrence.contactId
      }), configuration.enrollment == .enabled,
      configuration.approvalHash != nil,
      let contact = peopleStore.privateContact(localId: occurrence.contactId),
      !contact.deleted,
      contact.materialRevision == configuration.materialRevision,
      let phoneId = configuration.selectedPhoneId,
      contact.phones.first(where: { $0.localId == phoneId })?.e164 == proposal.recipient,
      let draft = workflow.messageDraft,
      IOSBirthdayMessageContentPolicy.renderedBody(
        templateText: draft.text,
        placeholderMode: draft.placeholderMode,
        givenName: contact.givenName,
        declaredLanguage: draft.language
      ) == proposal.body
    else { return nil }
    return CompanionReviewMaterial(
      accountGeneration: proposal.accountGeneration,
      occurrenceId: proposal.occurrenceId,
      occurrenceCivilDate: proposal.occurrenceCivilDate,
      recipient: proposal.recipient,
      body: proposal.body,
      state: proposal.state,
      contactMaterialRevision: contact.materialRevision,
      selectedPhoneId: phoneId
    )
  }

  private func prepareStoredReview(
    request: CompanionReviewRequest,
    presenter: CompanionForegroundPresenter,
    resolve: @escaping RCTPromiseResolveBlock,
    reject: @escaping RCTPromiseRejectBlock
  ) {
    store.prepareComposerReview(
      proposalId: request.proposalId,
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
    guard !Self.accountDeletionShutdown, !isPreparing,
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

    isPreparing = true
    store.commitComposerOpen(
      proposalId: request.proposalId,
      expectedRevision: request.expectedRevision,
      actionNonce: request.actionNonce,
      sessionGeneration: sessionGeneration,
      sceneIdentifier: presenter.sceneIdentifier,
      now: Date()
    ) { [weak self] result in
      guard let self else {
        reject("COMPOSER_MODULE_UNAVAILABLE", "COMPOSER_MODULE_UNAVAILABLE", nil)
        return
      }
      switch result {
      case .failure(let error):
        self.isPreparing = false
        self.rejectSafe(reject, code: error.safeCode)
      case .success(let commit):
        guard !Self.accountDeletionShutdown, Self.presentationPreconditionsHold(),
          let currentPresenter = Self.foregroundPresenter(),
          currentPresenter.sceneIdentifier == presenter.sceneIdentifier,
          Self.isSafeRecipient(commit.recipient),
          Self.isSafeMessageBody(commit.body)
        else {
          self.isPreparing = false
          self.store.finishComposerOperation(
            operationId: commit.operationId,
            outcome: .failed
          )
          self.rejectSafe(reject, code: "COMPOSER_PRESENTATION_REFUSED")
          return
        }

        let controller = MFMessageComposeViewController()
        controller.messageComposeDelegate = self
        controller.recipients = [commit.recipient]
        controller.body = commit.body

        self.pendingOperationId = commit.operationId
        self.pendingResolve = resolve
        self.presentedController = controller
        self.isPreparing = false

        currentPresenter.controller.present(
          controller,
          animated: true
        ) { [weak self, weak controller] in
          guard let self, let controller else { return }
          guard controller.presentingViewController != nil else {
            self.completePresentationFailure(
              controller: controller,
              operationId: commit.operationId
            )
            return
          }
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
    }
  }

  func messageComposeViewController(
    _ controller: MFMessageComposeViewController,
    didFinishWith result: MessageComposeResult
  ) {
    dispatchPrecondition(condition: .onQueue(.main))
    guard controller === presentedController,
      let operationId = pendingOperationId
    else {
      controller.dismiss(animated: true)
      return
    }

    let outcome: CompanionComposerOutcome
    let publicOutcome: String
    switch result {
    case .cancelled:
      outcome = .cancelled
      publicOutcome = "cancelled"
    case .sent:
      // This is only MessageUI's result. The app does not know the final
      // recipient/body, sender line, transport, carrier acceptance, or delivery.
      outcome = .reportedSent
      publicOutcome = "reported-sent"
    case .failed:
      outcome = .failed
      publicOutcome = "failed"
    @unknown default:
      outcome = .outcomeUnknown
      publicOutcome = "unknown"
    }

    let resolve = pendingResolve
    store.finishComposerOperation(
      operationId: operationId,
      outcome: outcome
    ) { [weak self] persistenceResult in
      controller.dismiss(animated: true) { [weak self] in
        self?.clearPendingPresentation()
        switch persistenceResult {
        case .success:
          resolve?(publicOutcome)
        case .failure:
          resolve?("unknown")
        }
      }
    }
  }

  private func completePresentationFailure(
    controller: MFMessageComposeViewController,
    operationId: String
  ) {
    guard controller === presentedController,
      operationId == pendingOperationId
    else { return }
    let resolve = pendingResolve
    store.finishComposerOperation(operationId: operationId, outcome: .failed) { [weak self] _ in
      self?.clearPendingPresentation()
      resolve?("failed")
    }
  }

  private func completeUnknownAfterPresentation(
    controller: MFMessageComposeViewController,
    operationId: String
  ) {
    guard controller === presentedController,
      operationId == pendingOperationId
    else { return }
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

  private func clearPendingPresentation() {
    pendingResolve = nil
    pendingOperationId = nil
    presentedController = nil
    isPreparing = false
  }

  private func shutdownForAccountDeletion(completion: @escaping () -> Void) {
    dispatchPrecondition(condition: .onQueue(.main))
    isPreparing = false
    guard let controller = presentedController,
      let operationId = pendingOperationId
    else {
      completion()
      return
    }
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
        completion()
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
