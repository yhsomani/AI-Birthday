import CryptoKit
import Foundation

struct IOSPeopleRequest {
  let url: URL

  init?(_ url: URL) {
    guard url.scheme == "https", url.host == "people.googleapis.com",
      url.port == nil, url.user == nil, url.password == nil,
      url.path == "/v1/people/me/connections", url.fragment == nil
    else {
      return nil
    }
    self.url = url
  }
}

enum IOSPeopleRequestError: Error {
  case invalidParameter
}

final class IOSPeopleRequestFactory {
  private static let endpoint = URL(string: "https://people.googleapis.com/v1/people/me/connections")!

  let pageSize: Int
  let parameterFingerprint: String

  init(pageSize: Int) {
    precondition((1...1_000).contains(pageSize))
    self.pageSize = pageSize
    let canonical = [
      "endpoint=\(Self.endpoint.absoluteString)",
      "personFields=\(birthdayPeoplePersonFields)",
      "sources=\(birthdayPeopleContactSource)",
      "pageSize=\(pageSize)",
      "requestSyncToken=true",
      "sortOrder=LAST_MODIFIED_ASCENDING",
    ].joined(separator: "&")
    parameterFingerprint = Self.sha256(canonical)
  }

  func make(mode: IOSPeopleSyncMode, pageToken: String?) throws -> IOSPeopleRequest {
    guard pageToken.flatMap(IOSPeopleValuePolicy.token) == pageToken else {
      throw IOSPeopleRequestError.invalidParameter
    }
    var items = [
      URLQueryItem(name: "personFields", value: birthdayPeoplePersonFields),
      URLQueryItem(name: "sources", value: birthdayPeopleContactSource),
      URLQueryItem(name: "pageSize", value: String(pageSize)),
      URLQueryItem(name: "requestSyncToken", value: "true"),
      URLQueryItem(name: "sortOrder", value: "LAST_MODIFIED_ASCENDING"),
    ]
    switch mode {
    case .full:
      break
    case .incremental(let syncToken, let fingerprint):
      guard fingerprint == parameterFingerprint,
        IOSPeopleValuePolicy.token(syncToken) == syncToken
      else {
        throw IOSPeopleRequestError.invalidParameter
      }
      items.append(URLQueryItem(name: "syncToken", value: syncToken))
    }
    if let pageToken {
      items.append(URLQueryItem(name: "pageToken", value: pageToken))
    }
    var components = URLComponents(url: Self.endpoint, resolvingAgainstBaseURL: false)
    components?.queryItems = items
    guard let url = components?.url, let request = IOSPeopleRequest(url) else {
      throw IOSPeopleRequestError.invalidParameter
    }
    return request
  }

  private static func sha256(_ value: String) -> String {
    SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
  }
}

protocol IOSPeopleTransport {
  func execute(
    request: IOSPeopleRequest,
    accessToken: IOSEphemeralGoogleAccessToken,
    completion: @escaping (IOSPeopleTransportResult) -> Void
  )
}

final class IOSPeopleHTTPTransport: IOSPeopleTransport {
  private let maximumPageBytes: Int

  init(maximumPageBytes: Int) {
    precondition(maximumPageBytes > 0)
    self.maximumPageBytes = maximumPageBytes
  }

  func execute(
    request: IOSPeopleRequest,
    accessToken: IOSEphemeralGoogleAccessToken,
    completion: @escaping (IOSPeopleTransportResult) -> Void
  ) {
    var urlRequest = URLRequest(
      url: request.url,
      cachePolicy: .reloadIgnoringLocalAndRemoteCacheData,
      timeoutInterval: 30
    )
    urlRequest.httpMethod = "GET"
    urlRequest.httpShouldHandleCookies = false
    urlRequest.setValue("application/json", forHTTPHeaderField: "Accept")
    accessToken.use { token in
      urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    }

    let operation = IOSPeopleHTTPTask(
      expectedURL: request.url,
      maximumSuccessBytes: maximumPageBytes,
      completion: completion
    )
    operation.start(urlRequest)
  }
}

private final class IOSPeopleHTTPTask: NSObject, URLSessionDataDelegate, URLSessionTaskDelegate {
  private let expectedURL: URL
  private let maximumSuccessBytes: Int
  private let completion: (IOSPeopleTransportResult) -> Void
  private var response: HTTPURLResponse?
  private var received = Data()
  private var overflowed = false
  private var finished = false
  private var session: URLSession?

  init(
    expectedURL: URL,
    maximumSuccessBytes: Int,
    completion: @escaping (IOSPeopleTransportResult) -> Void
  ) {
    self.expectedURL = expectedURL
    self.maximumSuccessBytes = maximumSuccessBytes
    self.completion = completion
  }

  func start(_ request: URLRequest) {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.urlCache = nil
    configuration.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
    configuration.httpCookieStorage = nil
    configuration.urlCredentialStorage = nil
    configuration.httpShouldSetCookies = false
    configuration.waitsForConnectivity = false
    configuration.timeoutIntervalForRequest = 30
    configuration.timeoutIntervalForResource = 60
    configuration.httpMaximumConnectionsPerHost = 1
    let session = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
    self.session = session
    session.dataTask(with: request).resume()
  }

  func urlSession(
    _ session: URLSession,
    task: URLSessionTask,
    willPerformHTTPRedirection response: HTTPURLResponse,
    newRequest request: URLRequest,
    completionHandler: @escaping (URLRequest?) -> Void
  ) {
    completionHandler(nil)
  }

  func urlSession(
    _ session: URLSession,
    dataTask: URLSessionDataTask,
    didReceive response: URLResponse,
    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
  ) {
    guard let response = response as? HTTPURLResponse,
      response.url == expectedURL,
      response.url?.scheme == "https",
      response.url?.host == "people.googleapis.com",
      response.url?.path == "/v1/people/me/connections"
    else {
      overflowed = true
      completionHandler(.cancel)
      return
    }
    self.response = response
    let limit = response.statusCode == 200 ? maximumSuccessBytes : 64 * 1_024
    if response.expectedContentLength > Int64(limit) {
      overflowed = true
      completionHandler(.cancel)
      return
    }
    received.reserveCapacity(
      max(0, min(limit, Int(max(0, response.expectedContentLength))))
    )
    completionHandler(.allow)
  }

  func urlSession(
    _ session: URLSession,
    dataTask: URLSessionDataTask,
    didReceive data: Data
  ) {
    guard !overflowed else { return }
    let limit = response?.statusCode == 200 ? maximumSuccessBytes : 64 * 1_024
    guard data.count <= limit - received.count else {
      overflowed = true
      dataTask.cancel()
      return
    }
    received.append(data)
  }

  func urlSession(
    _ session: URLSession,
    task: URLSessionTask,
    didCompleteWithError error: Error?
  ) {
    guard !finished else { return }
    finished = true
    defer {
      received.resetBytes(in: 0..<received.count)
      self.session?.finishTasksAndInvalidate()
      self.session = nil
    }
    guard !overflowed, let response else {
      finish(.unexpectedResponse)
      return
    }
    if let error = error as? URLError {
      switch error.code {
      case .notConnectedToInternet, .networkConnectionLost,
        .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed,
        .internationalRoamingOff, .dataNotAllowed:
        finish(.networkOffline)
      case .timedOut:
        finish(.timedOut)
      default:
        finish(.unexpectedResponse)
      }
      return
    }
    if error != nil {
      finish(.unexpectedResponse)
      return
    }

    switch response.statusCode {
    case 200:
      guard Self.isJSON(response.value(forHTTPHeaderField: "Content-Type")) else {
        finish(.unexpectedResponse)
        return
      }
      finish(.success(received))
    case 400:
      finish(IOSPeopleAPIErrorParser.isExpiredSyncToken(received)
        ? .expiredSyncToken : .unexpectedResponse)
    case 401:
      finish(.unauthorized)
    case 403:
      finish(.forbidden)
    case 429:
      finish(.rateLimited(Self.retryAfter(response.value(forHTTPHeaderField: "Retry-After"))))
    default:
      finish(.unexpectedResponse)
    }
  }

  private func finish(_ result: IOSPeopleTransportResult) {
    DispatchQueue.main.async { self.completion(result) }
  }

  private static func isJSON(_ contentType: String?) -> Bool {
    guard let contentType else { return false }
    let mediaType = contentType.split(separator: ";", maxSplits: 1).first?
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .lowercased()
    return mediaType == "application/json" || mediaType?.hasSuffix("+json") == true
  }

  private static func retryAfter(_ raw: String?) -> Int? {
    guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
      let seconds = Int(raw), (0...86_400).contains(seconds)
    else {
      return nil
    }
    return seconds
  }
}
