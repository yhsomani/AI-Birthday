import Foundation

/// Bounded full/incremental People synchronization with atomic active-generation replacement.
@MainActor
final class IOSPeopleSyncCoordinator {
  static let shared = IOSPeopleSyncCoordinator()

  private let identity: IOSGoogleIdentityCoordinator
  private let authorization: IOSPeopleAuthorizationGateway
  private let store: CompanionPeopleStore
  private let limits: IOSPeopleSyncLimits
  private let requestFactory: IOSPeopleRequestFactory
  private let parser: IOSPeopleJSONParser
  private let transport: IOSPeopleTransport
  private var running = false

  init(
    identity: IOSGoogleIdentityCoordinator = .shared,
    authorization: IOSPeopleAuthorizationGateway? = nil,
    store: CompanionPeopleStore = .shared,
    limits: IOSPeopleSyncLimits = IOSPeopleSyncLimits(),
    transport: IOSPeopleTransport? = nil
  ) {
    self.identity = identity
    self.authorization = authorization ?? IOSPeopleAuthorizationGateway(identity: identity)
    self.store = store
    self.limits = limits
    requestFactory = IOSPeopleRequestFactory(pageSize: limits.pageSize)
    parser = IOSPeopleJSONParser(maximumPagePeople: limits.pageSize)
    self.transport = transport ?? IOSPeopleHTTPTransport(
      maximumPageBytes: limits.maximumPageBytes
    )
  }

  func sync(
    interactiveAuthorization: Bool,
    completion: @escaping (IOSPeopleSyncOutcome) -> Void
  ) {
    guard !running else {
      completion(.failed(.unavailable))
      return
    }
    guard let expectedBinding = identity.exactSessionBinding() else {
      completion(.failed(.authorizationRequired))
      return
    }
    running = true
    let initialMode = store.syncMode(
      parameterFingerprint: requestFactory.parameterFingerprint
    )
    Task { @MainActor in
      let outcome = await perform(
        initialMode: initialMode,
        expectedBinding: expectedBinding,
        interactiveAuthorization: interactiveAuthorization
      )
      running = false
      if case .failed(let failure) = outcome, failure != .cancelled {
        let storageReason = Self.safeStorageReason(failure)
        _ = await withCheckedContinuation { continuation in
          store.recordSyncFailure(
            storageReason,
            authorizationRequired: failure == .authorizationRequired || failure == .forbidden
          ) { continuation.resume(returning: $0) }
        }
      }
      CompanionProtectedStore.shared.markExternalProjectionChanged()
      completion(outcome)
    }
  }

  private func perform(
    initialMode: IOSPeopleSyncMode,
    expectedBinding: IOSNativeGoogleAccountBinding,
    interactiveAuthorization: Bool
  ) async -> IOSPeopleSyncOutcome {
    var mode = initialMode
    var unauthorizedRecoveryUsed = false
    var expiredTokenRecoveryUsed = false
    while true {
      let acquisition = await authorization.acquire(interactive: interactiveAuthorization)
      guard case .authorized(let accessToken) = acquisition else {
        if case .failed(let reason) = acquisition { return .failed(reason) }
        return .failed(.authorizationRequired)
      }
      let attempt = await runOnce(
        mode: mode,
        expectedBinding: expectedBinding,
        accessToken: accessToken
      )
      accessToken.clear()
      switch attempt {
      case .unauthorized:
        guard !unauthorizedRecoveryUsed else {
          return .failed(.repeatedUnauthorized)
        }
        unauthorizedRecoveryUsed = true
        authorization.clear(accessToken)
      case .expiredSyncToken:
        guard case .incremental = mode, !expiredTokenRecoveryUsed else {
          return .failed(.malformed)
        }
        expiredTokenRecoveryUsed = true
        mode = .full
      case .terminal(let outcome):
        if expiredTokenRecoveryUsed,
          case .completed(let count, let completedMode, _) = outcome
        {
          return .completed(
            contactCount: count,
            mode: completedMode,
            recoveredExpiredToken: true
          )
        }
        return outcome
      }
    }
  }

  private func runOnce(
    mode: IOSPeopleSyncMode,
    expectedBinding: IOSNativeGoogleAccountBinding,
    accessToken: IOSEphemeralGoogleAccessToken
  ) async -> AttemptResult {
    let completedMode: IOSPeopleCompletedMode = mode == .full ? .full : .incremental
    store.beginSync(mode: completedMode)
    CompanionProtectedStore.shared.markExternalProjectionChanged()
    let startedAt = ProcessInfo.processInfo.systemUptime
    var pageToken: String?
    var pageCount = 0
    var totalBytes = 0
    var expectedTotal: Int?
    var deltas: [IOSPeopleContactDelta] = []
    var pageTokens = Set<String>()
    var resourceNames = Set<String>()
    var sourceIDs = Set<String>()

    while true {
      guard durationIsSafe(startedAt), pageCount < limits.maximumPages else {
        return .terminal(.failed(pageCount > 0 ? .partial : .malformed))
      }
      let request: IOSPeopleRequest
      do {
        request = try requestFactory.make(mode: mode, pageToken: pageToken)
      } catch {
        return .terminal(.failed(pageCount > 0 ? .partial : .malformed))
      }
      let response = await withCheckedContinuation { continuation in
        transport.execute(request: request, accessToken: accessToken) {
          continuation.resume(returning: $0)
        }
      }
      switch response {
      case .unauthorized:
        return .unauthorized
      case .expiredSyncToken:
        return .expiredSyncToken
      case .forbidden:
        return .terminal(.failed(pageCount > 0 ? .partial : .forbidden))
      case .networkOffline, .timedOut:
        return .terminal(.failed(pageCount > 0 ? .partial : .networkOffline))
      case .rateLimited(let retryAfter):
        return .terminal(.failed(pageCount > 0 ? .partial : .rateLimited(
          retryAfterSeconds: retryAfter
        )))
      case .unexpectedResponse:
        return .terminal(.failed(pageCount > 0 ? .partial : .unavailable))
      case .success(let data):
        guard data.count <= limits.maximumPageBytes,
          totalBytes <= limits.maximumTotalBytes - data.count
        else {
          return .terminal(.failed(pageCount > 0 ? .partial : .malformed))
        }
        totalBytes += data.count
        let parsed = await parseOffMain(data)
        guard case .success(let page) = parsed else {
          return .terminal(.failed(pageCount > 0 ? .partial : .malformed))
        }
        if completedMode == .full && page.contacts.contains(where: \.deleted) {
          return .terminal(.failed(pageCount > 0 ? .partial : .malformed))
        }
        if let total = page.totalItems {
          guard total <= limits.maximumPeople,
            expectedTotal == nil || expectedTotal == total
          else {
            return .terminal(.failed(pageCount > 0 ? .partial : .malformed))
          }
          expectedTotal = total
        }
        guard page.contacts.count <= limits.maximumPeople - deltas.count else {
          return .terminal(.failed(pageCount > 0 ? .partial : .malformed))
        }
        for contact in page.contacts {
          guard resourceNames.insert(contact.resourceName).inserted,
            sourceIDs.insert(contact.contactSourceId).inserted
          else {
            return .terminal(.failed(pageCount > 0 ? .partial : .malformed))
          }
          deltas.append(contact)
        }
        pageCount += 1
        guard durationIsSafe(startedAt) else {
          return .terminal(.failed(.partial))
        }
        if let nextPage = page.nextPageToken {
          guard pageTokens.insert(nextPage).inserted else {
            return .terminal(.failed(.partial))
          }
          pageToken = nextPage
          continue
        }
        guard expectedTotal == nil || expectedTotal == deltas.count,
          let nextSyncToken = page.nextSyncToken
        else {
          return .terminal(.failed(pageCount > 1 ? .partial : .malformed))
        }
        let committed = await withCheckedContinuation { continuation in
          store.commit(
            expectedBinding: expectedBinding,
            mode: completedMode,
            deltas: deltas,
            nextSyncToken: nextSyncToken,
            parameterFingerprint: requestFactory.parameterFingerprint,
            completedAt: Date()
          ) { continuation.resume(returning: $0) }
        }
        guard committed else { return .terminal(.failed(.storage)) }
        return .terminal(
          .completed(
            contactCount: store.projection().contacts.filter {
              $0.readinessKind != "unavailable"
            }.count,
            mode: completedMode,
            recoveredExpiredToken: false
          )
        )
      }
    }
  }

  private func parseOffMain(
    _ data: Data
  ) async -> Result<IOSPeoplePage, IOSPeoplePageParseError> {
    await withCheckedContinuation { continuation in
      DispatchQueue.global(qos: .userInitiated).async {
        continuation.resume(returning: self.parser.parse(data))
      }
    }
  }

  private func durationIsSafe(_ startedAt: TimeInterval) -> Bool {
    let now = ProcessInfo.processInfo.systemUptime
    return now >= startedAt && now - startedAt <= limits.maximumDuration
  }

  private static func safeStorageReason(_ failure: IOSPeopleSyncFailure) -> String {
    switch failure {
    case .authorizationRequired, .forbidden: return "contacts-authorization-required"
    case .networkOffline: return "network-offline"
    case .partial: return "contacts-partial-sync"
    default: return "contacts-stale"
    }
  }

  private enum AttemptResult {
    case unauthorized
    case expiredSyncToken
    case terminal(IOSPeopleSyncOutcome)
  }
}
