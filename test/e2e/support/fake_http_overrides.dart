/// Opaque-box HTTP boundary interceptor for E2E tests.
///
/// Installs a scoped [HttpOverrides] implementation so production HTTP callers
/// (`UserGeminiApiProvider`, `LiveGoogleAuthGateway`, `SubscriptionNotifier`,
/// `CloudSyncService`) execute real serialization, headers, timeouts, and error
/// mapping without ever hitting the real network.
library;

import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:io';

/// Recorded outbound HTTP request captured by [FakeHttpOverrides].
class RecordedHttpRequest {
  RecordedHttpRequest({
    required this.method,
    required this.uri,
    required this.headers,
    required this.body,
    required this.timestamp,
  });

  final String method;
  final Uri uri;
  final Map<String, String> headers;
  final String body;
  final DateTime timestamp;

  Map<String, dynamic>? _cachedJson;
  bool _jsonParsed = false;

  Map<String, dynamic>? get jsonBody {
    if (_jsonParsed) return _cachedJson;
    _jsonParsed = true;
    if (body.trim().isEmpty) return null;
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) {
        _cachedJson = decoded;
      }
    } catch (_) {
      _cachedJson = null;
    }
    return _cachedJson;
  }

  String? header(String name) => headers[name.toLowerCase()];
}

/// Canned HTTP response or simulated transport failure.
class FakeHttpResponse {
  const FakeHttpResponse({
    required this.statusCode,
    this.body = '',
    this.headers = const {'content-type': 'application/json; charset=utf-8'},
    this.delay,
    this.error,
  });

  factory FakeHttpResponse.json(
    int statusCode,
    Object payload, {
    Map<String, String> headers = const {
      'content-type': 'application/json; charset=utf-8',
    },
    Duration? delay,
  }) {
    return FakeHttpResponse(
      statusCode: statusCode,
      body: jsonEncode(payload),
      headers: headers,
      delay: delay,
    );
  }

  factory FakeHttpResponse.error(Object error, {Duration? delay}) {
    return FakeHttpResponse(statusCode: 0, error: error, delay: delay);
  }

  final int statusCode;
  final String body;
  final Map<String, String> headers;
  final Duration? delay;
  final Object? error;
}

/// Captured metadata from a Gemini `generateContent` HTTP call.
class GeminiCallRecord {
  GeminiCallRecord({
    required this.request,
    required this.model,
    required this.apiKey,
    required this.promptText,
    required this.isPing,
    required this.generationConfig,
  });

  final RecordedHttpRequest request;
  final String model;
  final String? apiKey;
  final String promptText;
  final bool isPing;
  final Map<String, dynamic>? generationConfig;

  String? get thinkingLevel {
    final thinking = generationConfig?['thinkingConfig'];
    if (thinking is Map) {
      return thinking['thinkingLevel']?.toString();
    }
    return null;
  }
}

/// Stateful configurable HTTP router for Gemini, Identity Toolkit, SecureToken,
/// Cloud Functions (`verifyPurchase`, `requestAccountDeletion`), and Firestore REST.
class FakeHttpRouter {
  FakeHttpRouter();

  static const String firebaseProjectId = 'relateai-birthday-ysoman-a2372';
  static const String _firestoreDocsPrefix =
      '/v1/projects/$firebaseProjectId/databases/(default)/documents';

  final List<RecordedHttpRequest> requests = [];
  final List<GeminiCallRecord> geminiCalls = [];

  /// Optional override hook evaluated before built-in route handlers.
  Future<FakeHttpResponse?> Function(RecordedHttpRequest request)?
  customHandler;

  // ---------------------------------------------------------------------------
  // Gemini REST configuration
  // ---------------------------------------------------------------------------
  final Queue<FakeHttpResponse> geminiQueue = Queue<FakeHttpResponse>();
  FakeHttpResponse? geminiPingOverride;
  String geminiDefaultText = 'Happy Birthday! Wishing you a joyful year ahead.';
  int? geminiStatusOverride;
  Duration? geminiDelay;
  Object? geminiError;

  // ---------------------------------------------------------------------------
  // Identity Toolkit & SecureToken configuration
  // ---------------------------------------------------------------------------
  final Queue<FakeHttpResponse> identityToolkitQueue =
      Queue<FakeHttpResponse>();
  FakeHttpResponse? identityToolkitOverride;
  String defaultFirebaseUid = 'firebase-uid-123';
  String defaultFirebaseIdToken = 'fake-firebase-id-token';
  String defaultFirebaseRefreshToken = 'fake-refresh-token';

  final Queue<FakeHttpResponse> secureTokenQueue = Queue<FakeHttpResponse>();
  FakeHttpResponse? secureTokenOverride;
  String defaultRefreshedIdToken = 'refreshed-firebase-id-token';
  String defaultRefreshedRefreshToken = 'refreshed-refresh-token';

  // ---------------------------------------------------------------------------
  // Cloud Functions configuration
  // ---------------------------------------------------------------------------
  final Queue<FakeHttpResponse> verifyPurchaseQueue = Queue<FakeHttpResponse>();
  FakeHttpResponse? verifyPurchaseProbeOverride;
  FakeHttpResponse? verifyPurchaseOverride;
  bool verifyPurchaseRequireAppCheck = true;

  final Queue<FakeHttpResponse> accountDeletionQueue =
      Queue<FakeHttpResponse>();
  FakeHttpResponse? accountDeletionOverride;

  // ---------------------------------------------------------------------------
  // Firestore REST in-memory store
  // ---------------------------------------------------------------------------
  final Map<String, Map<String, dynamic>> firestoreDocs = {};
  final Queue<FakeHttpResponse> firestoreCommitQueue =
      Queue<FakeHttpResponse>();
  final Queue<FakeHttpResponse> firestoreGetQueue = Queue<FakeHttpResponse>();
  FakeHttpResponse? firestoreCommitOverride;
  FakeHttpResponse? firestoreGetOverride;
  bool firestoreRequireAppCheck = false;
  int? firestorePageSize;
  int _writeSeq = 0;

  static final RegExp _geminiPathPattern = RegExp(
    r'^/v1beta/models/([^/:]+):generateContent$',
  );

  List<RecordedHttpRequest> requestsMatching({String? host, String? path}) {
    return requests.where((r) {
      if (host != null && r.uri.host != host) return false;
      if (path != null && !r.uri.path.contains(path)) return false;
      return true;
    }).toList();
  }

  /// Seeds a Firestore document directly into the in-memory store for test setup
  /// (e.g. simulating count mismatch or tombstone conflict before restore).
  void putFirestoreDoc({
    required String documentPath,
    required Map<String, dynamic> fields,
    String? updateTime,
  }) {
    final normalized = documentPath.startsWith('projects/')
        ? documentPath
        : 'projects/$firebaseProjectId/databases/(default)/documents/$documentPath';
    firestoreDocs[normalized] = {
      'name': normalized,
      'fields': fields,
      'updateTime': updateTime ?? _nextTimestamp(),
    };
  }

  String _nextTimestamp() {
    _writeSeq++;
    return DateTime.utc(2026, 11, 27, 12, 0, _writeSeq).toIso8601String();
  }

  Future<FakeHttpResponse> dispatch(RecordedHttpRequest request) async {
    requests.add(request);

    if (customHandler != null) {
      final handled = await customHandler!(request);
      if (handled != null) return handled;
    }

    final host = request.uri.host;
    final path = request.uri.path;

    // 1. Gemini REST API
    if (host == 'generativelanguage.googleapis.com') {
      final match = _geminiPathPattern.firstMatch(path);
      if (match != null) {
        return _handleGemini(request, match.group(1)!);
      }
    }

    // 2. Identity Toolkit
    if (host == 'identitytoolkit.googleapis.com' &&
        path == '/v1/accounts:signInWithIdp') {
      return _handleIdentityToolkit(request);
    }

    // 3. SecureToken
    if (host == 'securetoken.googleapis.com' && path == '/v1/token') {
      return _handleSecureToken(request);
    }

    // 4. Cloud Functions
    if (host == 'asia-south1-$firebaseProjectId.cloudfunctions.net') {
      if (path == '/verifyPurchase') {
        return _handleVerifyPurchase(request);
      }
      if (path == '/requestAccountDeletion') {
        return _handleAccountDeletion(request);
      }
    }

    // 5. Firestore REST
    if (host == 'firestore.googleapis.com' &&
        path.startsWith(_firestoreDocsPrefix)) {
      return _handleFirestore(request);
    }

    return FakeHttpResponse.json(404, {
      'error': {
        'code': 404,
        'message':
            'Unhandled fake HTTP route: ${request.method} ${request.uri}',
      },
    });
  }

  FakeHttpResponse _handleGemini(RecordedHttpRequest request, String model) {
    final json = request.jsonBody;
    final contents = json?['contents'];
    var promptText = '';
    if (contents is List && contents.isNotEmpty) {
      final first = contents.first;
      if (first is Map && first['parts'] is List) {
        final parts = first['parts'] as List;
        if (parts.isNotEmpty && parts.first is Map) {
          promptText = (parts.first as Map)['text']?.toString() ?? '';
        }
      }
    }
    final genConfig = json?['generationConfig'] is Map<String, dynamic>
        ? json!['generationConfig'] as Map<String, dynamic>
        : null;
    final isPing = promptText == 'Ping' && genConfig?['maxOutputTokens'] == 1;

    geminiCalls.add(
      GeminiCallRecord(
        request: request,
        model: model,
        apiKey: request.header('x-goog-api-key'),
        promptText: promptText,
        isPing: isPing,
        generationConfig: genConfig,
      ),
    );

    if (geminiError != null) {
      return FakeHttpResponse.error(geminiError!, delay: geminiDelay);
    }
    if (isPing && geminiPingOverride != null) {
      return geminiPingOverride!;
    }
    if (geminiQueue.isNotEmpty) {
      return geminiQueue.removeFirst();
    }
    if (geminiStatusOverride != null && geminiStatusOverride! >= 400) {
      return FakeHttpResponse.json(geminiStatusOverride!, {
        'error': {
          'code': geminiStatusOverride,
          'message': 'Simulated Gemini error $geminiStatusOverride',
        },
      }, delay: geminiDelay);
    }
    final text = isPing ? 'ok' : geminiDefaultText;
    return FakeHttpResponse.json(200, {
      'candidates': [
        {
          'content': {
            'parts': [
              {'text': text},
            ],
          },
        },
      ],
    }, delay: geminiDelay);
  }

  FakeHttpResponse _handleIdentityToolkit(RecordedHttpRequest request) {
    if (identityToolkitQueue.isNotEmpty) {
      return identityToolkitQueue.removeFirst();
    }
    if (identityToolkitOverride != null) {
      return identityToolkitOverride!;
    }
    return FakeHttpResponse.json(200, {
      'localId': defaultFirebaseUid,
      'idToken': defaultFirebaseIdToken,
      'refreshToken': defaultFirebaseRefreshToken,
      'expiresIn': '3600',
    });
  }

  FakeHttpResponse _handleSecureToken(RecordedHttpRequest request) {
    if (secureTokenQueue.isNotEmpty) {
      return secureTokenQueue.removeFirst();
    }
    if (secureTokenOverride != null) {
      return secureTokenOverride!;
    }
    return FakeHttpResponse.json(200, {
      'id_token': defaultRefreshedIdToken,
      'refresh_token': defaultRefreshedRefreshToken,
      'expires_in': '3600',
    });
  }

  FakeHttpResponse _handleVerifyPurchase(RecordedHttpRequest request) {
    final json = request.jsonBody;
    final data = json?['data'];
    final isProbe = data is Map && data.isEmpty;

    if (verifyPurchaseRequireAppCheck &&
        (request.header('x-firebase-appcheck')?.isEmpty ?? true)) {
      return FakeHttpResponse.json(401, {
        'error': {
          'code': 401,
          'status': 'UNAUTHENTICATED',
          'message': 'Missing X-Firebase-AppCheck header',
        },
      });
    }

    if (isProbe && verifyPurchaseProbeOverride != null) {
      return verifyPurchaseProbeOverride!;
    }
    if (verifyPurchaseQueue.isNotEmpty) {
      return verifyPurchaseQueue.removeFirst();
    }
    if (!isProbe && verifyPurchaseOverride != null) {
      return verifyPurchaseOverride!;
    }
    if (isProbe) {
      // SubscriptionNotifier accepts 200 or 400 as reachable for probe.
      return FakeHttpResponse.json(400, {
        'error': {'status': 'INVALID_ARGUMENT', 'message': 'probe'},
      });
    }
    final expiryMs = DateTime.now()
        .toUtc()
        .add(const Duration(days: 30))
        .millisecondsSinceEpoch;
    final productId =
        (data is Map ? data['productId']?.toString() : null) ??
        'ai_birthday_pro_monthly';
    return FakeHttpResponse.json(200, {
      'result': {
        'status': 'active',
        'productId': productId,
        'expiryDateMs': expiryMs,
        'canUseAi': true,
        'isAutoRenewing': true,
      },
    });
  }

  FakeHttpResponse _handleAccountDeletion(RecordedHttpRequest request) {
    if (accountDeletionQueue.isNotEmpty) {
      return accountDeletionQueue.removeFirst();
    }
    if (accountDeletionOverride != null) {
      return accountDeletionOverride!;
    }
    final data = request.jsonBody?['data'];
    final requestId =
        (data is Map ? data['requestId']?.toString() : null) ?? 'req-default';
    return FakeHttpResponse.json(200, {
      'result': {'status': 'started', 'requestId': requestId},
    });
  }

  FakeHttpResponse _handleFirestore(RecordedHttpRequest request) {
    if (firestoreRequireAppCheck &&
        (request.header('x-firebase-appcheck')?.isEmpty ?? true)) {
      return FakeHttpResponse.json(401, {
        'error': {'code': 401, 'status': 'UNAUTHENTICATED'},
      });
    }

    final path = request.uri.path;
    if (request.method == 'POST' && path.endsWith(':commit')) {
      if (firestoreCommitQueue.isNotEmpty) {
        return firestoreCommitQueue.removeFirst();
      }
      if (firestoreCommitOverride != null) {
        return firestoreCommitOverride!;
      }
      return _handleFirestoreCommit(request);
    }

    if (request.method == 'GET') {
      if (firestoreGetQueue.isNotEmpty) {
        return firestoreGetQueue.removeFirst();
      }
      if (firestoreGetOverride != null) {
        return firestoreGetOverride!;
      }
      return _handleFirestoreGet(request);
    }

    return FakeHttpResponse.json(404, {
      'error': {'code': 404, 'status': 'NOT_FOUND'},
    });
  }

  FakeHttpResponse _handleFirestoreCommit(RecordedHttpRequest request) {
    final json = request.jsonBody;
    final writes = json?['writes'];
    if (writes is! List) {
      return FakeHttpResponse.json(400, {
        'error': {'code': 400, 'status': 'INVALID_ARGUMENT'},
      });
    }

    final pendingUpdates = <String, Map<String, dynamic>>{};
    final writeResults = <Map<String, dynamic>>[];

    for (final rawWrite in writes) {
      if (rawWrite is! Map) continue;
      final update = rawWrite['update'];
      if (update is! Map) continue;
      final name = update['name']?.toString();
      if (name == null || name.isEmpty) continue;

      final currentDoc = rawWrite['currentDocument'];
      final existing = firestoreDocs[name];
      if (currentDoc is Map) {
        if (currentDoc.containsKey('exists')) {
          final mustExist = currentDoc['exists'] == true;
          if (mustExist && existing == null) {
            return FakeHttpResponse.json(404, {
              'error': {'code': 404, 'status': 'NOT_FOUND'},
            });
          }
          if (!mustExist && existing != null) {
            return FakeHttpResponse.json(409, {
              'error': {'code': 409, 'status': 'ALREADY_EXISTS'},
            });
          }
        }
        if (currentDoc.containsKey('updateTime')) {
          final expected = currentDoc['updateTime']?.toString();
          if (existing == null || existing['updateTime'] != expected) {
            return FakeHttpResponse.json(409, {
              'error': {'code': 409, 'status': 'ABORTED'},
            });
          }
        }
      }

      final ts = _nextTimestamp();
      pendingUpdates[name] = {
        'name': name,
        'fields': update['fields'] ?? <String, dynamic>{},
        'updateTime': ts,
      };
      writeResults.add({'updateTime': ts});
    }

    firestoreDocs.addAll(pendingUpdates);
    return FakeHttpResponse.json(200, {'writeResults': writeResults});
  }

  FakeHttpResponse _handleFirestoreGet(RecordedHttpRequest request) {
    final resourceName = request.uri.path.substring('/v1/'.length);

    final exactDoc = firestoreDocs[resourceName];
    if (exactDoc != null) {
      return FakeHttpResponse.json(200, exactDoc);
    }

    // Collection list check: match direct children under resourceName/
    final prefix = '$resourceName/';
    final children =
        firestoreDocs.entries
            .where((e) {
              if (!e.key.startsWith(prefix)) return false;
              final remainder = e.key.substring(prefix.length);
              return remainder.isNotEmpty && !remainder.contains('/');
            })
            .map((e) => e.value)
            .toList()
          ..sort(
            (a, b) => (a['name'] as String).compareTo(b['name'] as String),
          );

    final isKnownCollection =
        children.isNotEmpty ||
        resourceName.endsWith('/people') ||
        resourceName.endsWith('/birthdays') ||
        resourceName.endsWith('/drafts') ||
        resourceName.endsWith('/reminderSettings') ||
        resourceName.endsWith('/generations');

    if (isKnownCollection) {
      final pageSizeParam = int.tryParse(
        request.uri.queryParameters['pageSize'] ?? '',
      );
      final effectivePageSize = firestorePageSize ?? pageSizeParam ?? 100;
      final startOffset =
          int.tryParse(request.uri.queryParameters['pageToken'] ?? '') ?? 0;

      if (startOffset >= children.length) {
        return FakeHttpResponse.json(200, {'documents': <Object>[]});
      }

      final endOffset = (startOffset + effectivePageSize).clamp(
        0,
        children.length,
      );
      final page = children.sublist(startOffset, endOffset);
      final response = <String, dynamic>{'documents': page};
      if (endOffset < children.length) {
        response['nextPageToken'] = '$endOffset';
      }
      return FakeHttpResponse.json(200, response);
    }

    return FakeHttpResponse.json(404, {
      'error': {
        'code': 404,
        'status': 'NOT_FOUND',
        'message': 'Document $resourceName not found',
      },
    });
  }
}

/// Scoped [HttpOverrides] binding all `dart:io.HttpClient` and `package:http`
/// traffic to [router].
class FakeHttpOverrides extends HttpOverrides {
  FakeHttpOverrides({FakeHttpRouter? router, HttpOverrides? previousOverrides})
    : router = router ?? FakeHttpRouter(),
      _previous = previousOverrides;

  final FakeHttpRouter router;
  final HttpOverrides? _previous;
  final List<_FakeHttpClient> _clients = [];

  static FakeHttpOverrides install({FakeHttpRouter? router}) {
    final previous = HttpOverrides.current;
    final overrides = FakeHttpOverrides(
      router: router,
      previousOverrides: previous,
    );
    HttpOverrides.global = overrides;
    return overrides;
  }

  void restore() {
    for (final client in _clients) {
      client.close(force: true);
    }
    _clients.clear();
    if (identical(HttpOverrides.current, this)) {
      HttpOverrides.global = _previous;
    }
  }

  List<RecordedHttpRequest> get requests => router.requests;

  @override
  HttpClient createHttpClient(SecurityContext? context) {
    final client = _FakeHttpClient(router);
    _clients.add(client);
    return client;
  }
}

class _FakeHttpClient implements HttpClient {
  _FakeHttpClient(this._router);

  final FakeHttpRouter _router;
  final Set<Completer<HttpClientResponse>> _pendingResponses = {};
  final Set<Timer> _pendingTimers = {};
  bool _closed = false;

  @override
  bool autoUncompress = true;

  @override
  Duration? connectionTimeout;

  @override
  Duration idleTimeout = const Duration(seconds: 15);

  @override
  int? maxConnectionsPerHost;

  @override
  String? userAgent;

  @override
  set authenticate(
    Future<bool> Function(Uri url, String scheme, String? realm)? f,
  ) {}

  @override
  set authenticateProxy(
    Future<bool> Function(String host, int port, String scheme, String? realm)?
    f,
  ) {}

  @override
  set badCertificateCallback(
    bool Function(X509Certificate cert, String host, int port)? callback,
  ) {}

  @override
  set connectionFactory(
    Future<ConnectionTask<Socket>> Function(
      Uri url,
      String? proxyHost,
      int? proxyPort,
    )?
    f,
  ) {}

  @override
  set findProxy(String Function(Uri url)? f) {}

  @override
  set keyLog(Function(String line)? callback) {}

  @override
  void addCredentials(
    Uri url,
    String realm,
    HttpClientCredentials credentials,
  ) {}

  @override
  void addProxyCredentials(
    String host,
    int port,
    String realm,
    HttpClientCredentials credentials,
  ) {}

  @override
  void close({bool force = false}) {
    _closed = true;
    if (force) {
      for (final timer in _pendingTimers.toList()) {
        timer.cancel();
      }
      _pendingTimers.clear();
      for (final completer in _pendingResponses.toList()) {
        if (!completer.isCompleted) {
          completer.completeError(
            const SocketException('Connection closed by client'),
          );
        }
      }
      _pendingResponses.clear();
    }
  }

  Future<HttpClientResponse> _send(
    String method,
    Uri uri,
    _FakeHttpHeaders headers,
    String body,
  ) async {
    if (_closed) {
      throw const SocketException('HttpClient is closed');
    }

    final headerMap = <String, String>{};
    headers.forEach((name, values) {
      if (values.isNotEmpty) {
        headerMap[name.toLowerCase()] = values.join(', ');
      }
    });

    final recorded = RecordedHttpRequest(
      method: method,
      uri: uri,
      headers: headerMap,
      body: body,
      timestamp: DateTime.now().toUtc(),
    );

    final response = await _router.dispatch(recorded);
    if (response.delay != null && response.delay! > Duration.zero) {
      final completer = Completer<HttpClientResponse>();
      _pendingResponses.add(completer);
      late final Timer timer;
      timer = Timer(response.delay!, () {
        _pendingTimers.remove(timer);
        _pendingResponses.remove(completer);
        if (completer.isCompleted) return;
        if (response.error != null) {
          completer.completeError(response.error!);
        } else {
          completer.complete(_FakeHttpClientResponse(response));
        }
      });
      _pendingTimers.add(timer);
      return completer.future;
    }

    if (response.error != null) {
      throw response.error!;
    }
    return _FakeHttpClientResponse(response);
  }

  @override
  Future<HttpClientRequest> openUrl(String method, Uri url) async {
    return _FakeHttpClientRequest(method: method, uri: url, onSend: _send);
  }

  @override
  Future<HttpClientRequest> open(
    String method,
    String host,
    int port,
    String path,
  ) => openUrl(method, Uri(scheme: 'http', host: host, port: port, path: path));

  @override
  Future<HttpClientRequest> get(String host, int port, String path) =>
      open('GET', host, port, path);

  @override
  Future<HttpClientRequest> getUrl(Uri url) => openUrl('GET', url);

  @override
  Future<HttpClientRequest> post(String host, int port, String path) =>
      open('POST', host, port, path);

  @override
  Future<HttpClientRequest> postUrl(Uri url) => openUrl('POST', url);

  @override
  Future<HttpClientRequest> put(String host, int port, String path) =>
      open('PUT', host, port, path);

  @override
  Future<HttpClientRequest> putUrl(Uri url) => openUrl('PUT', url);

  @override
  Future<HttpClientRequest> delete(String host, int port, String path) =>
      open('DELETE', host, port, path);

  @override
  Future<HttpClientRequest> deleteUrl(Uri url) => openUrl('DELETE', url);

  @override
  Future<HttpClientRequest> patch(String host, int port, String path) =>
      open('PATCH', host, port, path);

  @override
  Future<HttpClientRequest> patchUrl(Uri url) => openUrl('PATCH', url);

  @override
  Future<HttpClientRequest> head(String host, int port, String path) =>
      open('HEAD', host, port, path);

  @override
  Future<HttpClientRequest> headUrl(Uri url) => openUrl('HEAD', url);
}

class _FakeHttpClientRequest implements HttpClientRequest {
  _FakeHttpClientRequest({
    required this.method,
    required this.uri,
    required Future<HttpClientResponse> Function(
      String method,
      Uri uri,
      _FakeHttpHeaders headers,
      String body,
    )
    onSend,
  }) : _sender = onSend {
    // Prevent unhandled Zone errors when callers await `close()` without
    // subscribing to `done` (e.g. `UserGeminiApiProvider`, `package:http`).
    // Callers that explicitly await `request.done` still observe any error.
    _doneCompleter.future.ignore();
  }

  @override
  final String method;

  @override
  final Uri uri;

  final Future<HttpClientResponse> Function(
    String method,
    Uri uri,
    _FakeHttpHeaders headers,
    String body,
  )
  _sender;

  @override
  final _FakeHttpHeaders headers = _FakeHttpHeaders();

  @override
  Encoding encoding = utf8;

  @override
  bool bufferOutput = true;

  @override
  int contentLength = -1;

  @override
  bool followRedirects = true;

  @override
  int maxRedirects = 5;

  @override
  bool persistentConnection = true;

  final List<int> _bytes = [];
  final Completer<HttpClientResponse> _doneCompleter = Completer();
  bool _closed = false;

  @override
  HttpConnectionInfo? get connectionInfo => null;

  @override
  List<Cookie> get cookies => const [];

  @override
  Future<HttpClientResponse> get done => _doneCompleter.future;

  @override
  void abort([Object? exception, StackTrace? stackTrace]) {
    if (!_doneCompleter.isCompleted) {
      _doneCompleter.completeError(
        exception ?? const SocketException('Request aborted'),
        stackTrace,
      );
    }
  }

  @override
  void add(List<int> data) {
    _bytes.addAll(data);
  }

  @override
  void addError(Object error, [StackTrace? stackTrace]) {}

  @override
  Future<void> addStream(Stream<List<int>> stream) async {
    await for (final chunk in stream) {
      _bytes.addAll(chunk);
    }
  }

  @override
  Future<HttpClientResponse> close() async {
    if (_closed) return _doneCompleter.future;
    _closed = true;
    try {
      final body = encoding.decode(_bytes);
      final response = await _sender(method, uri, headers, body);
      if (!_doneCompleter.isCompleted) {
        _doneCompleter.complete(response);
      }
      return response;
    } catch (error, stackTrace) {
      if (!_doneCompleter.isCompleted) {
        _doneCompleter.completeError(error, stackTrace);
      }
      rethrow;
    }
  }

  @override
  Future<void> flush() async {}

  @override
  void write(Object? object) {
    final text = '$object';
    _bytes.addAll(encoding.encode(text));
  }

  @override
  void writeAll(Iterable<dynamic> objects, [String separator = '']) {
    write(objects.join(separator));
  }

  @override
  void writeCharCode(int charCode) {
    write(String.fromCharCode(charCode));
  }

  @override
  void writeln([Object? object = '']) {
    write('$object\n');
  }
}

class _FakeHttpClientResponse extends Stream<List<int>>
    implements HttpClientResponse {
  _FakeHttpClientResponse(this._response)
    : _bytes = utf8.encode(_response.body) {
    _response.headers.forEach((key, value) {
      headers.set(key, value);
    });
  }

  final FakeHttpResponse _response;
  final List<int> _bytes;

  @override
  final _FakeHttpHeaders headers = _FakeHttpHeaders();

  @override
  X509Certificate? get certificate => null;

  @override
  HttpClientResponseCompressionState get compressionState =>
      HttpClientResponseCompressionState.notCompressed;

  @override
  HttpConnectionInfo? get connectionInfo => null;

  @override
  int get contentLength => _bytes.length;

  @override
  List<Cookie> get cookies => const [];

  @override
  Future<Socket> detachSocket() {
    throw UnsupportedError(
      'detachSocket is not supported in FakeHttpOverrides',
    );
  }

  @override
  bool get isRedirect => false;

  @override
  StreamSubscription<List<int>> listen(
    void Function(List<int> event)? onData, {
    Function? onError,
    void Function()? onDone,
    bool? cancelOnError,
  }) {
    final stream = _bytes.isEmpty
        ? const Stream<List<int>>.empty()
        : Stream<List<int>>.value(_bytes);
    return stream.listen(
      onData,
      onError: onError,
      onDone: onDone,
      cancelOnError: cancelOnError,
    );
  }

  @override
  bool get persistentConnection => true;

  @override
  String get reasonPhrase => statusCode == 200 ? 'OK' : 'HTTP $statusCode';

  @override
  Future<HttpClientResponse> redirect([
    String? method,
    Uri? url,
    bool? followLoops,
  ]) {
    throw UnsupportedError('redirect is not supported in FakeHttpOverrides');
  }

  @override
  List<RedirectInfo> get redirects => const [];

  @override
  int get statusCode => _response.statusCode;
}

class _FakeHttpHeaders implements HttpHeaders {
  final Map<String, List<String>> _map = {};

  @override
  bool chunkedTransferEncoding = false;

  @override
  int contentLength = -1;

  @override
  ContentType? contentType;

  @override
  DateTime? date;

  @override
  DateTime? expires;

  @override
  String? host;

  @override
  DateTime? ifModifiedSince;

  @override
  bool persistentConnection = true;

  @override
  int? port;

  @override
  List<String>? operator [](String name) => _map[name.toLowerCase()];

  @override
  void add(String name, Object value, {bool preserveHeaderCase = false}) {
    final key = name.toLowerCase();
    _map.putIfAbsent(key, () => []).add('$value');
  }

  @override
  void clear() => _map.clear();

  @override
  void forEach(void Function(String name, List<String> values) action) {
    _map.forEach(action);
  }

  @override
  void noFolding(String name) {}

  @override
  void remove(String name, Object value) {
    _map[name.toLowerCase()]?.remove('$value');
  }

  @override
  void removeAll(String name) {
    _map.remove(name.toLowerCase());
  }

  @override
  void set(String name, Object value, {bool preserveHeaderCase = false}) {
    final key = name.toLowerCase();
    _map[key] = ['$value'];
    if (key == HttpHeaders.contentTypeHeader) {
      try {
        contentType = ContentType.parse('$value');
      } catch (_) {}
    }
  }

  @override
  String? value(String name) {
    final list = _map[name.toLowerCase()];
    if (list == null || list.isEmpty) return null;
    return list.first;
  }
}
