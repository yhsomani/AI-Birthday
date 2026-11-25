library;

import 'dart:convert';
import 'dart:math' as math;

import 'package:drift/drift.dart' as drift;
import 'package:http/http.dart' as http;

import '../../../core/database/app_database.dart';
import '../../../core/logging/app_logger.dart';
import '../../../core/security/credential_storage.dart';
import '../../auth/domain/auth_state.dart';
import '../../auth/domain/google_identity.dart';
import '../../birthdays/domain/models/birthday.dart' as domain;
import 'package:ai_birthday/core/config/firebase_config.dart';

class CloudSyncResult {
  const CloudSyncResult({
    required this.success,
    this.uploadedCount = 0,
    this.downloadedCount = 0,
    this.skippedCount = 0,
    this.skippedRecords = const [],
    this.conflictCount = 0,
    this.conflictRecords = const [],
    this.error,
    this.errorCode,
    this.retryable = false,
    required this.timestamp,
  });

  final bool success;
  final int uploadedCount;
  final int downloadedCount;
  final int skippedCount;
  final List<String> skippedRecords;
  final int conflictCount;
  final List<String> conflictRecords;
  final String? error;

  /// Stable failure classification consumed by the durable job worker:
  /// `auth` (sign back in — permanent), `config` (permanent), or
  /// `sync`/`network` (transient — retryable).
  final String? errorCode;

  /// Whether a failed result can reasonably be retried as-is.
  final bool retryable;
  final DateTime timestamp;

  String toResultRef() => jsonEncode({
    'timestamp': timestamp.toUtc().toIso8601String(),
    'uploaded': uploadedCount,
    'restored': downloadedCount,
    'skipped': skippedCount,
    'skippedDetails': skippedRecords,
    'conflicts': conflictCount,
    'conflictDetails': conflictRecords,
  });
}

class CloudSyncService {
  CloudSyncService({
    required this._db,
    required this._store,
    http.Client? httpClient,
    this._logger,
    String? apiKey,
    this._freshIdToken,
    this._appCheckTokenProvider,
  }) : _http = httpClient ?? http.Client(),
       _apiKey = apiKey ?? FirebaseConfig.webApiKey;

  final AppDatabase _db;
  final SecureStoreDriver _store;
  final http.Client _http;
  final AppLogger? _logger;
  final String _apiKey;
  final Future<String?> Function()? _freshIdToken;
  final Future<String?> Function()? _appCheckTokenProvider;

  static const String _projectId = FirebaseConfig.projectId;

  /// Firebase web API key of the shipped project. Public identifier (it also
  /// ships in `google-services.json`), so release builds work without a
  /// `--dart-define`; the request still needs a signed-in user's ID token.
  static const String _lastSyncPrefix = 'cloud_last_sync_timestamp_';
  static const String _localDataOwnerKey = 'cloud_local_data_owner_v1';

  Future<String?> getLocalDataOwner() => _store.read(_localDataOwnerKey);

  /// Records an explicit decision to bind the single local dataset to an
  /// account. UI must call this only after warning the user when the owner
  /// changes; the sync path itself never silently reassigns existing data.
  Future<void> assignLocalDataOwner(String accountId) async {
    if (accountId.isEmpty) throw ArgumentError.value(accountId, 'accountId');
    await _store.write(_localDataOwnerKey, accountId);
  }

  Future<CloudSyncResult?> ownershipFailure(
    String accountId,
    DateTime timestamp,
  ) async {
    final owner = await getLocalDataOwner();
    if (owner == null || owner.isEmpty) {
      await assignLocalDataOwner(accountId);
      return null;
    }
    if (owner == accountId) return null;
    return CloudSyncResult(
      success: false,
      error:
          'These local records belong to a different signed-in account. Nothing has been uploaded. Reopen Cloud Backup and explicitly decide who owns this device data.',
      errorCode: 'ownership',
      timestamp: timestamp,
    );
  }

  Future<DateTime?> getLastSyncTime(String accountId) async {
    try {
      final str = await _store.read(_lastSyncPrefix + accountId);
      if (str != null) return _parseInstant(str);
    } catch (_) {}
    return null;
  }

  /// Returns [authState] with a freshly refreshed ID token when a provider is
  /// configured. A failed or empty refresh keeps the stored state, so the
  /// request still goes out and the server reports any real rejection (F14).
  Future<AuthState> _withFreshIdToken(AuthState authState) async {
    final provider = _freshIdToken;
    final identity = authState.identity;
    if (provider == null || identity == null || !authState.isSignedIn) {
      return authState;
    }
    final token = await provider();
    if (token == null || token.isEmpty) return authState;
    return AuthState(
      status: authState.status,
      identity: GoogleIdentity(
        googleSubject: identity.googleSubject,
        email: identity.email,
        displayName: identity.displayName,
        photoUrl: identity.photoUrl,
        firebaseUid: identity.firebaseUid,
        idToken: token,
      ),
    );
  }

  String? _authenticatedUid(AuthState authState) {
    if (!authState.isSignedIn || authState.identity == null) return null;
    final idToken = authState.identity!.idToken;
    if (idToken == null || idToken.isEmpty) return null;

    final uid =
        authState.identity!.firebaseUid ?? authState.identity!.googleSubject;
    return uid.isEmpty ? null : uid;
  }

  Future<Map<String, String>> _headers(AuthState authState) async {
    final idToken = authState.identity?.idToken;
    final appCheckToken = await _appCheckTokenProvider?.call();
    return {
      'Content-Type': 'application/json',
      if (appCheckToken != null && appCheckToken.isNotEmpty)
        'X-Firebase-AppCheck': appCheckToken,
      if (idToken != null && idToken.isNotEmpty)
        'Authorization': 'Bearer $idToken',
    };
  }

  String _collectionUrl(String uid, String collection) {
    final encodedUid = Uri.encodeComponent(uid);
    return 'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents/users/$encodedUid/$collection';
  }

  static const int _firestoreCommitLimit = 500;

  /// Firestore `commit` endpoint (collection-agnostic batch write).
  String _commitUrl() {
    return 'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents:commit?key=$_apiKey';
  }

  /// Resource name for a write target inside a commit request. This is a
  /// resource path, not a URL: Firebase/Google uids are alphanumeric and
  /// document ids are app-generated, so no escaping is required.
  String _documentPath(String uid, String collection, String documentId) {
    return 'projects/$_projectId/databases/(default)/documents/users/$uid/$collection/$documentId';
  }

  static const int _snapshotSchemaVersion = 2;
  static final math.Random _generationRandom = math.Random.secure();

  /// Identifier for one immutable backup generation (audit F05).
  String _newGenerationId(DateTime now) =>
      'g${now.toUtc().microsecondsSinceEpoch}-${_generationRandom.nextInt(1 << 32).toRadixString(16)}';

  /// Document URL under the owner's tree, for single-document reads.
  String _documentUrl(String uid, String path) {
    final encodedUid = Uri.encodeComponent(uid);
    return 'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents/users/$encodedUid/$path';
  }

  /// Fields of one document, or null when it does not exist.
  Future<Map<String, dynamic>?> _getDocument(
    String url,
    Map<String, String> headers,
  ) async {
    final uri = Uri.parse(url).replace(queryParameters: {'key': _apiKey});
    final response = await _http
        .get(uri, headers: headers)
        .timeout(const Duration(seconds: 30));
    if (response.statusCode == 404) return null;
    if (response.statusCode != 200) {
      throw const FormatException('CLOUD_GET_FAILED');
    }
    final body = jsonDecode(response.body);
    if (body is! Map<String, dynamic>) {
      throw const FormatException('CLOUD_GET_INVALID_RESPONSE');
    }
    return body;
  }

  Future<Map<String, dynamic>?> _getDocumentFields(
    String url,
    Map<String, String> headers,
  ) async {
    final document = await _getDocument(url, headers);
    if (document == null) return null;
    final fields = document['fields'];
    if (fields == null) return <String, dynamic>{};
    if (fields is! Map<String, dynamic>) {
      throw const FormatException('CLOUD_GET_INVALID_FIELDS');
    }
    return fields;
  }

  /// Sends up to 500 full-document writes in one Firestore `commit` call and
  /// returns how many of them were accepted.
  ///
  /// A commit `update` without an `updateMask` overwrites the entire document
  /// with the given fields (omitted fields are lost), which is what the former
  /// per-document PATCH without a mask did — so this preserves the write
  /// semantics while collapsing N round trips into `N/500`.
  Future<int> _commitBatch(
    List<Map<String, dynamic>> writes,
    Map<String, String> headers,
  ) async {
    if (writes.isEmpty) return 0;
    final response = await _http
        .post(
          Uri.parse(_commitUrl()),
          headers: headers,
          body: jsonEncode({'writes': writes}),
        )
        .timeout(const Duration(seconds: 30));
    if (response.statusCode != 200) {
      _logger?.warning(
        'CloudBackup',
        'Firestore batch write was rejected.',
        params: {
          'statusCode': response.statusCode,
          'writeCount': writes.length,
        },
      );
      return 0;
    }
    final decoded = jsonDecode(response.body);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('CLOUD_COMMIT_INVALID_RESPONSE');
    }
    final results = decoded['writeResults'];
    if (results is! List || results.length != writes.length) {
      throw const FormatException('CLOUD_COMMIT_INVALID_WRITE_RESULTS');
    }
    var ok = 0;
    for (final result in results) {
      if (result is! Map<String, dynamic>) {
        throw const FormatException('CLOUD_COMMIT_INVALID_WRITE_RESULT');
      }
      if (result['error'] != null) continue;
      if (result['updateTime'] is! String) {
        throw const FormatException('CLOUD_COMMIT_MISSING_UPDATE_TIME');
      }
      ok++;
    }
    return ok;
  }

  /// Flushes [writes] in Firestore-sized batches, reporting per-batch outcomes
  /// through [onResult].
  Future<void> _flushWrites(
    List<Map<String, dynamic>> writes,
    Map<String, String> headers,
    void Function(int ok, int total) onResult,
  ) async {
    for (var i = 0; i < writes.length; i += _firestoreCommitLimit) {
      var end = i + _firestoreCommitLimit;
      if (end > writes.length) end = writes.length;
      final ok = await _commitBatch(writes.sublist(i, end), headers);
      onResult(ok, end - i);
    }
  }

  Map<String, dynamic> _personFields(Person p) => {
    'id': _firestoreString(p.id),
    'name': _firestoreString(p.name),
    if (p.birthdayMonth != null)
      'birthdayMonth': _firestoreInt(p.birthdayMonth!),
    if (p.birthdayDay != null) 'birthdayDay': _firestoreInt(p.birthdayDay!),
    if (p.birthYear != null) 'birthYear': _firestoreInt(p.birthYear!),
    if (p.leapDayResolution != null)
      'leapDayResolution': _firestoreString(p.leapDayResolution!),
    if (p.phoneNumber != null) 'phoneNumber': _firestoreString(p.phoneNumber!),
    if (p.email != null) 'email': _firestoreString(p.email!),
    'relationship': _firestoreString(p.relationship),
    'relationshipCloseness': _firestoreString(p.relationshipCloseness),
    'preferredLanguage': _firestoreString(p.preferredLanguage),
    'preferredTone': _firestoreString(p.preferredTone),
    'importantFacts': _firestoreString(p.importantFacts),
    if (p.notes != null) 'notes': _firestoreString(p.notes!),
    'preferredDeliveryChannel': _firestoreString(p.preferredDeliveryChannel),
    if (p.timezone != null) 'timezone': _firestoreString(p.timezone!),
    'autoPrepare': _firestoreBool(p.autoPrepare),
    'autoSendPolicy': _firestoreString(p.autoSendPolicy),
    'createdAt': _firestoreDate(p.createdAt),
    'updatedAt': _firestoreDate(p.updatedAt),
    'version': _firestoreInt(p.version),
    if (p.deletedAt != null) 'deletedAt': _firestoreDate(p.deletedAt!),
  };

  Map<String, dynamic> _birthdayFields(Birthday b, Person? person) => {
    'id': _firestoreString(b.id),
    'personId': _firestoreString(b.personId),
    'personName': _firestoreString(person?.name ?? 'Unknown'),
    'cycleYear': _firestoreInt(b.cycleYear),
    'date': _firestoreCalendarDate(b.date),
    'status': _firestoreString(b.status),
    if (b.draftId != null) 'draftId': _firestoreString(b.draftId!),
    'createdAt': _firestoreDate(b.createdAt),
    'updatedAt': _firestoreDate(b.updatedAt),
  };

  Map<String, dynamic> _draftFields(MessageDraft d) => {
    'id': _firestoreString(d.id),
    'birthdayId': _firestoreString(d.birthdayId),
    'personId': _firestoreString(d.personId),
    'body': _firestoreString(d.body),
    'tone': _firestoreString(d.tone),
    'length': _firestoreString(d.length),
    'status': _firestoreString(d.status),
    'providerType': _firestoreString(d.providerType),
    'variationIndex': _firestoreInt(d.variationIndex),
    'createdAt': _firestoreDate(d.createdAt),
    'updatedAt': _firestoreDate(d.updatedAt),
  };

  Map<String, dynamic> _reminderFields(ReminderSettingsEntry setting) => {
    'key': _firestoreString(setting.key),
    'enabled': _firestoreBool(setting.enabled),
    'kinds': _firestoreString(setting.kinds),
    'quietHoursStartMinutes': _firestoreInt(setting.quietHoursStartMinutes),
    'quietHoursEndMinutes': _firestoreInt(setting.quietHoursEndMinutes),
  };

  Future<List<Map<String, dynamic>>> _listDocuments(
    String collectionUrl,
    Map<String, String> headers,
  ) async {
    final documents = <Map<String, dynamic>>[];
    String? pageToken;

    do {
      final params = <String, String>{'key': _apiKey};
      if (pageToken != null && pageToken.isNotEmpty) {
        params['pageToken'] = pageToken;
      }

      final uri = Uri.parse(collectionUrl).replace(queryParameters: params);
      final response = await _http
          .get(uri, headers: headers)
          .timeout(const Duration(seconds: 30));

      if (response.statusCode != 200) {
        throw const FormatException('CLOUD_LIST_FAILED');
      }

      final body = jsonDecode(response.body);
      if (body is! Map<String, dynamic>) {
        throw const FormatException('CLOUD_LIST_INVALID_RESPONSE');
      }

      final rawDocuments = body['documents'];
      if (rawDocuments is List) {
        for (final raw in rawDocuments) {
          if (raw is Map<String, dynamic>) {
            documents.add(raw);
          }
        }
      }

      pageToken = body['nextPageToken'] as String?;
    } while (pageToken != null && pageToken.isNotEmpty);

    return documents;
  }

  Map<String, dynamic> _firestoreString(String value) => {'stringValue': value};

  Map<String, dynamic> _firestoreInt(int value) => {
    'integerValue': value.toString(),
  };

  Map<String, dynamic> _firestoreBool(bool value) => {'booleanValue': value};

  /// Backup instants are written as UTC so a restore on a device in another
  /// timezone compares them correctly (A3-014).
  Map<String, dynamic> _firestoreDate(DateTime value) => {
    'stringValue': value.toUtc().toIso8601String(),
  };

  /// New snapshots store birthday dates as unambiguous ISO calendar days.
  Map<String, dynamic> _firestoreCalendarDate(DateTime value) {
    String two(int v) => v.toString().padLeft(2, '0');
    final year = value.year.toString().padLeft(4, '0');
    return {'stringValue': '$year-${two(value.month)}-${two(value.day)}'};
  }

  /// Parses a stored timestamp as a UTC instant. Strings without a zone
  /// (written by older builds with the device's local time) are read as local.
  static DateTime? _parseInstant(String raw) => DateTime.tryParse(raw)?.toUtc();

  String? _string(Map<String, dynamic> fields, String key) {
    final raw = fields[key];
    if (raw is! Map<String, dynamic>) return null;
    final value = raw['stringValue'];
    return value is String ? value : null;
  }

  int? _integer(Map<String, dynamic> fields, String key) {
    final raw = fields[key];
    if (raw is! Map<String, dynamic>) return null;
    final value = raw['integerValue'];
    return value is String ? int.tryParse(value) : null;
  }

  bool? _boolean(Map<String, dynamic> fields, String key) {
    final raw = fields[key];
    if (raw is! Map<String, dynamic>) return null;
    final value = raw['booleanValue'];
    return value is bool ? value : null;
  }

  DateTime? _date(Map<String, dynamic> fields, String key) {
    final raw = _string(fields, key);
    return raw == null ? null : _parseInstant(raw);
  }

  /// Calendar day of a birthday as a local midnight, whatever zone the stored
  /// string carries.
  DateTime? _calendarDate(Map<String, dynamic> fields, String key) {
    final raw = _string(fields, key);
    final parsed = raw == null ? null : DateTime.tryParse(raw);
    return parsed == null
        ? null
        : DateTime(parsed.year, parsed.month, parsed.day);
  }

  DateTime? _restoreBirthdayDate(
    Map<String, dynamic> fields,
    Map<String, dynamic>? snapshotPerson,
    int snapshotSchemaVersion,
  ) {
    final raw = _string(fields, 'date');
    if (raw == null) return null;
    final cycleYear =
        _integer(fields, 'cycleYear') ?? DateTime.tryParse(raw)?.year;
    final isLegacyEncoding =
        snapshotSchemaVersion == 1 || RegExp(r'T').hasMatch(raw);
    final month = snapshotPerson == null
        ? null
        : _integer(snapshotPerson, 'birthdayMonth');
    final day = snapshotPerson == null
        ? null
        : _integer(snapshotPerson, 'birthdayDay');
    if (isLegacyEncoding && cycleYear != null && month != null && day != null) {
      return domain.Birthday.calculateOccurrenceDate(
        year: cycleYear,
        month: month,
        day: day,
        preferMar1:
            snapshotPerson != null &&
            _string(snapshotPerson, 'leapDayResolution') == 'mar1',
      );
    }

    return _calendarDate(fields, 'date');
  }

  int? _birthdayCycleYear(Map<String, dynamic> fields) =>
      _integer(fields, 'cycleYear') ??
      DateTime.tryParse(_string(fields, 'date') ?? '')?.year;

  String _birthdayCycleKey(String personId, int cycleYear) =>
      '$personId:$cycleYear';

  bool _isLegacyBirthdayDate(
    Map<String, dynamic> fields,
    int snapshotSchemaVersion,
  ) {
    final raw = _string(fields, 'date');
    return snapshotSchemaVersion == 1 ||
        (raw != null && RegExp(r'T').hasMatch(raw));
  }

  bool _birthdayRecordIsPreferred(
    Map<String, dynamic> candidate,
    Map<String, dynamic> current,
  ) {
    final candidateUpdated =
        _date(candidate, 'updatedAt') ?? DateTime.fromMillisecondsSinceEpoch(0);
    final currentUpdated =
        _date(current, 'updatedAt') ?? DateTime.fromMillisecondsSinceEpoch(0);
    final updatedOrder = candidateUpdated.compareTo(currentUpdated);
    if (updatedOrder != 0) return updatedOrder > 0;

    final candidateCreated =
        _date(candidate, 'createdAt') ?? DateTime.fromMillisecondsSinceEpoch(0);
    final currentCreated =
        _date(current, 'createdAt') ?? DateTime.fromMillisecondsSinceEpoch(0);
    final createdOrder = candidateCreated.compareTo(currentCreated);
    if (createdOrder != 0) return createdOrder > 0;

    final candidateId = _string(candidate, 'id')!;
    final currentId = _string(current, 'id')!;
    return candidateId.compareTo(currentId) < 0;
  }

  bool _localBirthdayIsPreferred(Birthday candidate, Birthday current) {
    final updatedOrder = candidate.updatedAt.compareTo(current.updatedAt);
    if (updatedOrder != 0) return updatedOrder > 0;
    final createdOrder = candidate.createdAt.compareTo(current.createdAt);
    if (createdOrder != 0) return createdOrder > 0;
    return candidate.id.compareTo(current.id) < 0;
  }

  String _nonConflictingRestoreId(
    String remoteId,
    String personId,
    int cycleYear,
    Map<String, Birthday> existingBirthdays,
  ) {
    final existing = existingBirthdays[remoteId];
    if (existing == null ||
        (existing.personId == personId && existing.cycleYear == cycleYear)) {
      return remoteId;
    }

    final base = '$remoteId-cycle-$cycleYear';
    var candidate = base;
    var suffix = 1;
    while (true) {
      final collision = existingBirthdays[candidate];
      if (collision == null ||
          (collision.personId == personId &&
              collision.cycleYear == cycleYear)) {
        return candidate;
      }
      candidate = '$base-$suffix';
      suffix++;
    }
  }

  bool _validField(
    Map<String, dynamic> fields,
    String key,
    bool Function(Map<String, dynamic>) read, {
    bool required = true,
  }) {
    if (!fields.containsKey(key)) return !required;
    final value = fields[key];
    return value is Map<String, dynamic> && read(fields);
  }

  bool _validString(
    Map<String, dynamic> fields,
    String key, {
    bool required = true,
  }) => _validField(
    fields,
    key,
    (fields) => _string(fields, key) != null,
    required: required,
  );

  bool _validInteger(
    Map<String, dynamic> fields,
    String key, {
    bool required = true,
  }) => _validField(
    fields,
    key,
    (fields) => _integer(fields, key) != null,
    required: required,
  );

  bool _validBoolean(
    Map<String, dynamic> fields,
    String key, {
    bool required = true,
  }) => _validField(
    fields,
    key,
    (fields) => _boolean(fields, key) != null,
    required: required,
  );

  bool _validInstant(
    Map<String, dynamic> fields,
    String key, {
    bool required = true,
  }) => _validField(
    fields,
    key,
    (fields) => _date(fields, key) != null,
    required: required,
  );

  bool _validCalendarDate(
    Map<String, dynamic> fields,
    String key, {
    bool required = true,
  }) => _validField(fields, key, (fields) {
    final raw = _string(fields, key);
    if (raw == null || _calendarDate(fields, key) == null) return false;
    final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})').firstMatch(raw);
    if (match == null) return false;
    final year = int.parse(match.group(1)!);
    final month = int.parse(match.group(2)!);
    final day = int.parse(match.group(3)!);
    final date = DateTime(year, month, day);
    return date.year == year && date.month == month && date.day == day;
  }, required: required);

  bool _validPersonRecord(Map<String, dynamic> fields) =>
      _validString(fields, 'id') &&
      _validString(fields, 'name') &&
      _validString(fields, 'relationship', required: false) &&
      _validString(fields, 'relationshipCloseness', required: false) &&
      _validString(fields, 'preferredLanguage', required: false) &&
      _validString(fields, 'preferredTone', required: false) &&
      _validString(fields, 'importantFacts', required: false) &&
      _validString(fields, 'preferredDeliveryChannel', required: false) &&
      _validString(fields, 'autoSendPolicy', required: false) &&
      _validString(fields, 'leapDayResolution', required: false) &&
      _validString(fields, 'phoneNumber', required: false) &&
      _validString(fields, 'email', required: false) &&
      _validString(fields, 'notes', required: false) &&
      _validString(fields, 'timezone', required: false) &&
      _validInteger(fields, 'birthdayMonth', required: false) &&
      _validInteger(fields, 'birthdayDay', required: false) &&
      _validInteger(fields, 'birthYear', required: false) &&
      _validInteger(fields, 'version', required: false) &&
      _validBoolean(fields, 'autoPrepare', required: false) &&
      _validInstant(fields, 'createdAt', required: false) &&
      _validInstant(fields, 'updatedAt', required: false) &&
      _validInstant(fields, 'deletedAt', required: false);

  bool _validBirthdayRecord(Map<String, dynamic> fields) =>
      _validString(fields, 'id') &&
      _validString(fields, 'personId') &&
      _validString(fields, 'status', required: false) &&
      _validString(fields, 'draftId', required: false) &&
      _validInteger(fields, 'cycleYear', required: false) &&
      _validCalendarDate(fields, 'date') &&
      _validInstant(fields, 'createdAt', required: false) &&
      _validInstant(fields, 'updatedAt', required: false);

  bool _validDraftRecord(Map<String, dynamic> fields) =>
      _validString(fields, 'id') &&
      _validString(fields, 'birthdayId') &&
      _validString(fields, 'personId') &&
      _validString(fields, 'body') &&
      _validString(fields, 'tone', required: false) &&
      _validString(fields, 'length', required: false) &&
      _validString(fields, 'status', required: false) &&
      _validString(fields, 'providerType', required: false) &&
      _validInteger(fields, 'variationIndex', required: false) &&
      _validInstant(fields, 'createdAt', required: false) &&
      _validInstant(fields, 'updatedAt', required: false);

  bool _validReminderRecord(Map<String, dynamic> fields) =>
      _validString(fields, 'key') &&
      _validString(fields, 'kinds', required: false) &&
      _validBoolean(fields, 'enabled', required: false) &&
      _validInteger(fields, 'quietHoursStartMinutes', required: false) &&
      _validInteger(fields, 'quietHoursEndMinutes', required: false);

  Future<CloudSyncResult> sync(AuthState authState) async {
    final now = DateTime.now();
    authState = await _withFreshIdToken(authState);
    final uid = _authenticatedUid(authState);

    if (uid == null) {
      return CloudSyncResult(
        success: false,
        error: authState.isSignedIn
            ? 'Your cloud session has expired. Sign in again before backing up.'
            : 'Please sign in to enable cloud backup.',
        timestamp: now,
      );
    }

    final ownershipBlocked = await ownershipFailure(uid, now);
    if (ownershipBlocked != null) return ownershipBlocked;

    if (_apiKey.isEmpty) {
      return CloudSyncResult(
        success: false,
        error: 'Cloud backup is not configured in this build.',
        timestamp: now,
      );
    }

    try {
      final headers = await _headers(authState);
      final previousPointer = await _getDocument(
        _documentUrl(uid, 'backup/current'),
        headers,
      );
      final previousPointerUpdateTime = previousPointer?['updateTime'];
      if (previousPointer != null &&
          (previousPointerUpdateTime is! String ||
              previousPointerUpdateTime.isEmpty)) {
        throw const FormatException('CLOUD_POINTER_UPDATE_TIME_MISSING');
      }

      // Snapshot backup (audit F05): every row goes into a new immutable
      // generation, including unchanged rows, so each generation is complete.
      // The `backup/current` pointer only moves after the whole generation and
      // its manifest are stored, so a failed run leaves the last backup intact.
      final generationId = _newGenerationId(now);
      final (
        allPersonRows,
        allBirthdayRows,
        allDraftRows,
        reminderRows,
      ) = await _db.transaction(() async {
        final p = await _db.select(_db.persons).get();
        final b = await _db.select(_db.birthdays).get();
        final d = await _db.select(_db.messageDrafts).get();
        final r = await _db.select(_db.reminderSettingsEntries).get();
        return (p, b, d, r);
      });
      // Resolve person names for birthday docs from the full set (tombstoned
      // people included).
      final peopleMap = {for (final p in allPersonRows) p.id: p};
      final generationPath = 'generations/$generationId';

      final writes = <Map<String, dynamic>>[
        for (final p in allPersonRows)
          {
            'update': {
              'name': _documentPath(uid, '$generationPath/people', p.id),
              'fields': _personFields(p),
            },
          },
        for (final b in allBirthdayRows)
          {
            'update': {
              'name': _documentPath(uid, '$generationPath/birthdays', b.id),
              'fields': _birthdayFields(b, peopleMap[b.personId]),
            },
          },
        for (final d in allDraftRows)
          {
            'update': {
              'name': _documentPath(uid, '$generationPath/drafts', d.id),
              'fields': _draftFields(d),
            },
          },
        for (final setting in reminderRows)
          {
            'update': {
              'name': _documentPath(
                uid,
                '$generationPath/reminderSettings',
                setting.key,
              ),
              'fields': _reminderFields(setting),
            },
          },
      ];
      final totalItems = writes.length;
      var uploaded = 0;
      var failures = 0;

      await _flushWrites(writes, headers, (ok, total) {
        uploaded += ok;
        failures += total - ok;
      });

      if (failures > 0) {
        final error = uploaded == 0
            ? 'Cloud backup failed. None of your $totalItems records were saved.'
            : 'Cloud backup is incomplete. $uploaded of $totalItems records were saved.';
        _logger?.warning(
          'CloudBackup',
          'Backup finished with failures.',
          params: {
            'uploaded': uploaded,
            'total': totalItems,
            'failed': failures,
          },
        );
        return CloudSyncResult(
          success: false,
          uploadedCount: uploaded,
          error: error,
          errorCode: 'sync',
          retryable: true,
          timestamp: now,
        );
      }

      // Publish: the manifest records the exact counts, and the pointer moves
      // last, in its own commit, so restore never sees a partial generation.
      final published = await _commitBatch([
        {
          'update': {
            'name': _documentPath(uid, 'generations', generationId),
            'fields': {
              'schemaVersion': _firestoreInt(_snapshotSchemaVersion),
              'birthdayDateEncoding': _firestoreString('calendar-date-v1'),
              'generationId': _firestoreString(generationId),
              'createdAt': _firestoreDate(now),
              'people': _firestoreInt(allPersonRows.length),
              'birthdays': _firestoreInt(allBirthdayRows.length),
              'drafts': _firestoreInt(allDraftRows.length),
              'reminderSettings': _firestoreInt(reminderRows.length),
            },
          },
        },
        {
          'update': {
            'name': _documentPath(uid, 'backup', 'current'),
            'fields': {
              'schemaVersion': _firestoreInt(_snapshotSchemaVersion),
              'generationId': _firestoreString(generationId),
              'publishedAt': _firestoreDate(now),
            },
          },
          'currentDocument': previousPointer == null
              ? {'exists': false}
              : {'updateTime': previousPointerUpdateTime},
        },
      ], headers);
      if (published != 2) {
        return CloudSyncResult(
          success: false,
          uploadedCount: uploaded,
          error:
              'Cloud backup could not be published. Your previous backup is unchanged.',
          errorCode: 'sync',
          retryable: true,
          timestamp: now,
        );
      }

      await _store.write(_lastSyncPrefix + uid, now.toUtc().toIso8601String());
      // Generation cleanup is intentionally disabled: client timestamps and
      // listing order cannot prove that a generation is neither active nor
      // currently published by another device. Retention needs a server lease
      // or equivalent coordination before deleting any generation.
      _logger?.warning(
        'CloudBackup',
        'Backup generation cleanup is paused pending server-coordinated leases.',
        params: {'generationId': generationId},
      );
      _logger?.info(
        'CloudBackup',
        'Backup finished successfully.',
        params: {'uploaded': uploaded},
      );

      return CloudSyncResult(
        success: true,
        uploadedCount: uploaded,
        timestamp: now,
      );
    } catch (error, stackTrace) {
      _logger?.error(
        'CloudSync',
        'Backup could not be completed.',
        error: error,
        stackTrace: stackTrace,
      );
      return CloudSyncResult(
        success: false,
        error: error is FormatException
            ? 'Cloud backup returned an invalid response. Check backup status before retrying.'
            : 'Cloud backup could not be completed. Check your connection and try again.',
        errorCode: error is FormatException ? 'invalid_response' : 'network',
        retryable: true,
        timestamp: now,
      );
    }
  }

  Future<CloudSyncResult> restore(AuthState authState) async {
    final now = DateTime.now();
    authState = await _withFreshIdToken(authState);
    final uid = _authenticatedUid(authState);

    if (uid == null) {
      return CloudSyncResult(
        success: false,
        error: authState.isSignedIn
            ? 'Your cloud session has expired. Sign in again before restoring.'
            : 'Please sign in to restore a cloud backup.',
        errorCode: 'auth',
        timestamp: now,
      );
    }

    final ownershipBlocked = await ownershipFailure(uid, now);
    if (ownershipBlocked != null) return ownershipBlocked;

    if (_apiKey.isEmpty) {
      return CloudSyncResult(
        success: false,
        error: 'Cloud restore is not configured in this build.',
        errorCode: 'config',
        timestamp: now,
      );
    }

    try {
      final headers = await _headers(authState);
      var restored = 0;

      // Read the published pointer, its manifest, and the generation's
      // collections. Every collection is checked against the manifest before
      // any local write, so an incomplete snapshot changes nothing (F06).
      final pointer = await _getDocumentFields(
        _documentUrl(uid, 'backup/current'),
        headers,
      );
      if (pointer == null) {
        return CloudSyncResult(
          success: false,
          error: 'No cloud backup has been saved for this account yet.',
          errorCode: 'not-found',
          timestamp: now,
        );
      }
      final generationId = _string(pointer, 'generationId');
      if (generationId == null || generationId.isEmpty) {
        throw const FormatException('CLOUD_POINTER_INVALID');
      }
      final pointerSchemaVersion = pointer.containsKey('schemaVersion')
          ? _integer(pointer, 'schemaVersion')
          : 1;
      if (pointerSchemaVersion != 1 &&
          pointerSchemaVersion != _snapshotSchemaVersion) {
        throw const FormatException('CLOUD_POINTER_SCHEMA_UNSUPPORTED');
      }
      final manifest = await _getDocumentFields(
        _documentUrl(uid, 'generations/$generationId'),
        headers,
      );
      if (manifest == null ||
          _integer(manifest, 'schemaVersion') != pointerSchemaVersion) {
        throw const FormatException('CLOUD_MANIFEST_INVALID');
      }
      final snapshotSchemaVersion = pointerSchemaVersion!;

      final peopleDocs = await _listDocuments(
        _collectionUrl(uid, 'generations/$generationId/people'),
        headers,
      );
      final birthdayDocs = await _listDocuments(
        _collectionUrl(uid, 'generations/$generationId/birthdays'),
        headers,
      );
      final draftDocs = await _listDocuments(
        _collectionUrl(uid, 'generations/$generationId/drafts'),
        headers,
      );
      final reminderDocs = await _listDocuments(
        _collectionUrl(uid, 'generations/$generationId/reminderSettings'),
        headers,
      );
      final downloaded = <String, List<Map<String, dynamic>>>{
        'people': peopleDocs,
        'birthdays': birthdayDocs,
        'drafts': draftDocs,
        'reminderSettings': reminderDocs,
      };
      for (final entry in downloaded.entries) {
        if (entry.value.length != _integer(manifest, entry.key)) {
          throw FormatException('CLOUD_SNAPSHOT_INCOMPLETE:${entry.key}');
        }
      }

      // Pre-validate all downloaded records before mutating local database (AUD-002).
      for (final doc in peopleDocs) {
        final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
        if (!_validPersonRecord(fields)) {
          throw const FormatException('CLOUD_RESTORE_MALFORMED_RECORD:people');
        }
      }
      for (final doc in birthdayDocs) {
        final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
        if (!_validBirthdayRecord(fields)) {
          throw const FormatException(
            'CLOUD_RESTORE_MALFORMED_RECORD:birthdays',
          );
        }
      }
      for (final doc in draftDocs) {
        final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
        if (!_validDraftRecord(fields)) {
          throw const FormatException('CLOUD_RESTORE_MALFORMED_RECORD:drafts');
        }
      }
      for (final doc in reminderDocs) {
        final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
        if (!_validReminderRecord(fields)) {
          throw const FormatException(
            'CLOUD_RESTORE_MALFORMED_RECORD:reminderSettings',
          );
        }
      }

      final skippedRecords = <String>[];
      final conflictRecords = <String>[];

      // Apply every local write in one transaction, so a failure part-way
      // cannot leave a mixed dataset.
      await _db.transaction(() async {
        // Performance audit: load every existing row once instead of one SELECT
        // per restored document (N+1 when restoring a large backup).
        var existingPeople = const <String, Person>{};
        if (peopleDocs.isNotEmpty ||
            birthdayDocs.isNotEmpty ||
            draftDocs.isNotEmpty) {
          existingPeople = {
            for (final row in await _db.select(_db.persons).get()) row.id: row,
          };
        }
        final activePersonIds = <String>{
          for (final p in existingPeople.values)
            if (p.deletedAt == null) p.id,
        };
        for (final doc in peopleDocs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final id = _string(fields, 'id');
          final name = _string(fields, 'name');
          if (id == null || name == null) {
            skippedRecords.add('people/${id ?? "unknown"}: missing id or name');
            continue;
          }

          final remoteUpdatedAt = _date(fields, 'updatedAt') ?? now;
          final existing = existingPeople[id];
          if (existing != null &&
              !remoteUpdatedAt.isAfter(existing.updatedAt)) {
            conflictRecords.add('people/$id: local copy is equal or newer');
            continue;
          }

          final deletedAt = _date(fields, 'deletedAt');
          // A local tombstone outranks a live cloud copy that has no tombstone:
          // restore must never resurrect a contact the user deleted on-device
          // (audit F-2). Tombstones in the cloud still apply below.
          if (existing?.deletedAt != null && deletedAt == null) {
            conflictRecords.add('people/$id: local tombstone preserved');
            continue;
          }

          final createdAt = _date(fields, 'createdAt') ?? remoteUpdatedAt;
          final hasLeapDayResolution = fields.containsKey('leapDayResolution');
          final restoredLeapDayResolution = hasLeapDayResolution
              ? _string(fields, 'leapDayResolution')
              : existing?.leapDayResolution;
          await _db
              .into(_db.persons)
              .insertOnConflictUpdate(
                PersonsCompanion(
                  id: drift.Value(id),
                  name: drift.Value(name),
                  birthdayMonth: drift.Value(_integer(fields, 'birthdayMonth')),
                  birthdayDay: drift.Value(_integer(fields, 'birthdayDay')),
                  birthYear: drift.Value(_integer(fields, 'birthYear')),
                  leapDayResolution: drift.Value(restoredLeapDayResolution),
                  phoneNumber: drift.Value(_string(fields, 'phoneNumber')),
                  email: drift.Value(_string(fields, 'email')),
                  relationship: drift.Value(
                    _string(fields, 'relationship') ?? 'friend',
                  ),
                  relationshipCloseness: drift.Value(
                    _string(fields, 'relationshipCloseness') ?? 'close',
                  ),
                  preferredLanguage: drift.Value(
                    _string(fields, 'preferredLanguage') ?? 'en',
                  ),
                  preferredTone: drift.Value(
                    _string(fields, 'preferredTone') ?? 'warm',
                  ),
                  importantFacts: drift.Value(
                    _string(fields, 'importantFacts') ?? '',
                  ),
                  notes: drift.Value(_string(fields, 'notes')),
                  preferredDeliveryChannel: drift.Value(
                    _string(fields, 'preferredDeliveryChannel') ?? 'whatsapp',
                  ),
                  timezone: drift.Value(_string(fields, 'timezone')),
                  autoPrepare: drift.Value(
                    _boolean(fields, 'autoPrepare') ?? false,
                  ),
                  autoSendPolicy: drift.Value(
                    _string(fields, 'autoSendPolicy') ?? 'manualOnly',
                  ),
                  createdAt: drift.Value(createdAt),
                  updatedAt: drift.Value(remoteUpdatedAt),
                  version: drift.Value(_integer(fields, 'version') ?? 1),
                  deletedAt: drift.Value(deletedAt),
                ),
              );
          if (deletedAt != null) {
            activePersonIds.remove(id);
          } else {
            activePersonIds.add(id);
          }
          restored++;
        }

        final snapshotPeopleById = {
          for (final doc in peopleDocs)
            _string((doc['fields'] as Map<String, dynamic>?) ?? {}, 'id')!:
                (doc['fields'] as Map<String, dynamic>?) ?? {},
        };
        final localPeopleById = birthdayDocs.isEmpty
            ? const <String, Person>{}
            : {
                for (final row in await _db.select(_db.persons).get())
                  row.id: row,
              };
        var existingBirthdays = const <String, Birthday>{};
        if (birthdayDocs.isNotEmpty || draftDocs.isNotEmpty) {
          existingBirthdays = {
            for (final row in await _db.select(_db.birthdays).get())
              row.id: row,
          };
        }
        final validBirthdayIds = {...existingBirthdays.keys};
        var existingDrafts = const <String, MessageDraft>{};
        if (draftDocs.isNotEmpty || birthdayDocs.isNotEmpty) {
          existingDrafts = {
            for (final row in await _db.select(_db.messageDrafts).get())
              row.id: row,
          };
        }
        final remoteDraftFieldsById = <String, Map<String, dynamic>>{
          for (final doc in draftDocs)
            if (_string((doc['fields'] as Map<String, dynamic>?) ?? {}, 'id')
                case final String id)
              id: (doc['fields'] as Map<String, dynamic>?) ?? {},
        };
        final birthdayIdByCycle = <String, String>{};
        final birthdayCycleKeyById = <String, String>{};
        final birthdayPersonIdById = <String, String>{};
        for (final birthday in existingBirthdays.values) {
          final cycleKey = _birthdayCycleKey(
            birthday.personId,
            birthday.cycleYear,
          );
          final currentId = birthdayIdByCycle[cycleKey];
          final current = currentId == null
              ? null
              : existingBirthdays[currentId];
          if (current == null || _localBirthdayIsPreferred(birthday, current)) {
            birthdayIdByCycle[cycleKey] = birthday.id;
          }
          birthdayCycleKeyById[birthday.id] = cycleKey;
          birthdayPersonIdById[birthday.id] = birthday.personId;
        }
        final birthdayDocsByCycle = <String, List<Map<String, dynamic>>>{};
        final remoteBirthdayCycleKeyById = <String, String>{};
        final ambiguousRemoteBirthdayIds = <String>{};
        for (final doc in birthdayDocs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final personId = _string(fields, 'personId');
          final cycleYear = _birthdayCycleYear(fields);
          if (personId == null ||
              cycleYear == null ||
              !activePersonIds.contains(personId)) {
            continue;
          }
          final cycleKey = _birthdayCycleKey(personId, cycleYear);
          birthdayDocsByCycle.putIfAbsent(cycleKey, () => []).add(doc);
          final remoteId = _string(fields, 'id');
          if (remoteId != null) {
            final priorCycle = remoteBirthdayCycleKeyById[remoteId];
            if (priorCycle != null && priorCycle != cycleKey) {
              ambiguousRemoteBirthdayIds.add(remoteId);
            } else {
              remoteBirthdayCycleKeyById[remoteId] = cycleKey;
            }
          }
        }
        final selectedBirthdayIdByCycle = <String, String>{};
        final selectedRemoteBirthdayIdByCycle = <String, String>{};
        final birthdayIdRemap = <String, String>{};
        final sortedCycleKeys = birthdayDocsByCycle.keys.toList()..sort();
        for (final cycleKey in sortedCycleKeys) {
          final localBirthdayId = birthdayIdByCycle[cycleKey];
          final candidateDocs = birthdayDocsByCycle[cycleKey]!;
          final winner = candidateDocs.reduce((best, candidate) {
            final bestFields = best['fields'] as Map<String, dynamic>;
            final candidateFields = candidate['fields'] as Map<String, dynamic>;
            return _birthdayRecordIsPreferred(candidateFields, bestFields)
                ? candidate
                : best;
          });
          final winnerFields = winner['fields'] as Map<String, dynamic>;
          final winnerId = _string(winnerFields, 'id')!;
          var selectedId =
              localBirthdayId ??
              _nonConflictingRestoreId(
                winnerId,
                _string(winnerFields, 'personId')!,
                _birthdayCycleYear(winnerFields)!,
                existingBirthdays,
              );
          if (localBirthdayId == null) {
            final base = selectedId;
            var suffix = 1;
            while (birthdayCycleKeyById.containsKey(selectedId) &&
                birthdayCycleKeyById[selectedId] != cycleKey) {
              selectedId = '$base-$suffix';
              suffix++;
            }
          }
          selectedBirthdayIdByCycle[cycleKey] = selectedId;
          selectedRemoteBirthdayIdByCycle[cycleKey] = winnerId;
          birthdayCycleKeyById[selectedId] = cycleKey;
          birthdayPersonIdById[selectedId] = _string(winnerFields, 'personId')!;
          for (final doc in candidateDocs) {
            final fields = doc['fields'] as Map<String, dynamic>;
            final remoteId = _string(fields, 'id')!;
            if (!ambiguousRemoteBirthdayIds.contains(remoteId)) {
              birthdayIdRemap[remoteId] = selectedId;
            }
          }
        }

        bool draftMatchesCycle(
          String? draftId,
          String personId,
          String cycleKey,
        ) {
          if (draftId == null) return false;
          final remoteDraft = remoteDraftFieldsById[draftId];
          if (remoteDraft != null) {
            final sourceBirthdayId = _string(remoteDraft, 'birthdayId');
            final sourceCycleKey = sourceBirthdayId == null
                ? null
                : remoteBirthdayCycleKeyById[sourceBirthdayId];
            return sourceBirthdayId != null &&
                !ambiguousRemoteBirthdayIds.contains(sourceBirthdayId) &&
                _string(remoteDraft, 'personId') == personId &&
                sourceCycleKey == cycleKey;
          }
          final localDraft = existingDrafts[draftId];
          if (localDraft == null || localDraft.personId != personId) {
            return false;
          }
          return birthdayPersonIdById[localDraft.birthdayId] == personId &&
              birthdayCycleKeyById[localDraft.birthdayId] == cycleKey;
        }

        String statusForCycle(String? status, bool hasCycleDraft) {
          final restoredStatus = status ?? 'upcoming';
          if (!hasCycleDraft &&
              const {
                'messageDrafted',
                'messageReviewed',
                'readyForDelivery',
              }.contains(restoredStatus)) {
            return 'messageNotPrepared';
          }
          return restoredStatus;
        }

        for (final doc in birthdayDocs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final id = _string(fields, 'id');
          final personId = _string(fields, 'personId');
          final cycleYear = _birthdayCycleYear(fields);
          if (id == null || personId == null) {
            skippedRecords.add(
              'birthdays/${id ?? "unknown"}: missing id or personId',
            );
            continue;
          }

          // AUD-004: Never restore a birthday whose person is deleted (tombstoned) or non-existent.
          if (!activePersonIds.contains(personId)) {
            skippedRecords.add('birthdays/$id: person $personId not active');
            continue;
          }

          if (cycleYear == null) {
            skippedRecords.add('birthdays/$id: missing cycle year');
            continue;
          }
          final cycleKey = _birthdayCycleKey(personId, cycleYear);
          final selectedId = selectedBirthdayIdByCycle[cycleKey];
          if (selectedId == null ||
              id != selectedRemoteBirthdayIdByCycle[cycleKey]) {
            conflictRecords.add(
              'birthdays/$id: duplicate cycle mapped to survivor ${selectedId ?? "unknown"}',
            );
            continue;
          }
          final snapshotPerson = snapshotPeopleById[personId];
          final date = _restoreBirthdayDate(
            fields,
            snapshotPerson,
            snapshotSchemaVersion,
          );
          if (date == null) {
            skippedRecords.add('birthdays/$id: invalid calendar date');
            continue;
          }
          if (_isLegacyBirthdayDate(fields, snapshotSchemaVersion)) {
            final localPerson = localPeopleById[personId];
            final snapshotMonth = snapshotPerson == null
                ? null
                : _integer(snapshotPerson, 'birthdayMonth');
            final snapshotDay = snapshotPerson == null
                ? null
                : _integer(snapshotPerson, 'birthdayDay');
            if (snapshotMonth == null || snapshotDay == null) {
              conflictRecords.add(
                'birthdays/$id: snapshot birthday is unavailable; stored date retained instead of using current profile',
              );
            } else if (localPerson != null &&
                (localPerson.birthdayMonth != snapshotMonth ||
                    localPerson.birthdayDay != snapshotDay ||
                    localPerson.leapDayResolution !=
                        _string(snapshotPerson!, 'leapDayResolution'))) {
              conflictRecords.add(
                'birthdays/$id: current profile differs; cycle date reconstructed from snapshot profile',
              );
            }
          }

          final remoteUpdatedAt = _date(fields, 'updatedAt') ?? now;
          final existing = existingBirthdays[selectedId];
          final remoteDraftId = _string(fields, 'draftId');
          final remoteDraftIsForCycle = draftMatchesCycle(
            remoteDraftId,
            personId,
            cycleKey,
          );
          if (remoteDraftId != null && !remoteDraftIsForCycle) {
            conflictRecords.add(
              'birthdays/$id: referenced draft belongs to another or unknown cycle; it was not made current',
            );
          }
          if (existing != null &&
              !remoteUpdatedAt.isAfter(existing.updatedAt)) {
            conflictRecords.add(
              'birthdays/$id: local cycle is newer or equal; local status and date preserved',
            );
            final localDraftIsForCycle = draftMatchesCycle(
              existing.draftId,
              personId,
              cycleKey,
            );
            final keptDraftId = localDraftIsForCycle
                ? existing.draftId
                : (remoteDraftIsForCycle ? remoteDraftId : null);
            final keptStatus = statusForCycle(
              existing.status,
              keptDraftId != null,
            );
            if (existing.draftId != keptDraftId ||
                existing.status != keptStatus) {
              await (_db.update(
                _db.birthdays,
              )..where((row) => row.id.equals(selectedId))).write(
                BirthdaysCompanion(
                  draftId: drift.Value(keptDraftId),
                  status: drift.Value(keptStatus),
                ),
              );
            }
            continue;
          }

          final localDraftIsForCycle = draftMatchesCycle(
            existing?.draftId,
            personId,
            cycleKey,
          );
          final restoredDraftId = remoteDraftIsForCycle
              ? remoteDraftId
              : (localDraftIsForCycle ? existing?.draftId : null);
          final restoredStatus = statusForCycle(
            _string(fields, 'status'),
            restoredDraftId != null,
          );
          await _db
              .into(_db.birthdays)
              .insertOnConflictUpdate(
                BirthdaysCompanion(
                  id: drift.Value(selectedId),
                  personId: drift.Value(personId),
                  cycleYear: drift.Value(cycleYear),
                  date: drift.Value(date),
                  status: drift.Value(restoredStatus),
                  draftId: drift.Value(restoredDraftId),
                  createdAt: drift.Value(
                    _date(fields, 'createdAt') ?? remoteUpdatedAt,
                  ),
                  updatedAt: drift.Value(remoteUpdatedAt),
                ),
              );
          validBirthdayIds.add(selectedId);
          birthdayIdRemap[id] = selectedId;
          restored++;
        }

        for (final doc in draftDocs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final id = _string(fields, 'id');
          final birthdayId = _string(fields, 'birthdayId');
          final personId = _string(fields, 'personId');
          final body = _string(fields, 'body');
          if (id == null ||
              birthdayId == null ||
              personId == null ||
              body == null) {
            skippedRecords.add(
              'drafts/${id ?? "unknown"}: missing required fields',
            );
            continue;
          }
          final restoredBirthdayId = birthdayIdRemap[birthdayId] ?? birthdayId;
          final sourceCycleKey = remoteBirthdayCycleKeyById[birthdayId];
          final restoredCycleKey = birthdayCycleKeyById[restoredBirthdayId];

          // AUD-004: Never restore a draft whose person or birthday is deleted or missing.
          if (!activePersonIds.contains(personId)) {
            skippedRecords.add('drafts/$id: person $personId not active');
            continue;
          }
          if (!validBirthdayIds.contains(restoredBirthdayId) ||
              birthdayPersonIdById[restoredBirthdayId] != personId ||
              (sourceCycleKey != null &&
                  (ambiguousRemoteBirthdayIds.contains(birthdayId) ||
                      restoredCycleKey != sourceCycleKey))) {
            skippedRecords.add(
              'drafts/$id: birthday $birthdayId has no matching person/cycle survivor',
            );
            continue;
          }

          final remoteUpdatedAt = _date(fields, 'updatedAt') ?? now;
          final existing = existingDrafts[id];
          if (existing != null &&
              !remoteUpdatedAt.isAfter(existing.updatedAt)) {
            conflictRecords.add('drafts/$id: local copy is equal or newer');
            continue;
          }

          if (existing?.status == 'confirmedSent') {
            final revisionId =
                '$id-restore-revision-${remoteUpdatedAt.toUtc().microsecondsSinceEpoch}';
            if (existingDrafts.containsKey(revisionId)) {
              conflictRecords.add(
                'drafts/$id: confirmed-sent original preserved; newer restored revision already exists',
              );
              continue;
            }

            final remoteStatus = _string(fields, 'status') ?? 'draft';
            final birthdayStatus = remoteStatus == 'confirmedSent'
                ? 'completed'
                : 'messageDrafted';

            // Keep the delivery evidence immutable. The newer cloud content is
            // restored as a separate editorial revision and the birthday's
            // active draft pointer moves in the same transaction.
            await _db
                .into(_db.messageDrafts)
                .insert(
                  MessageDraftsCompanion(
                    id: drift.Value(revisionId),
                    birthdayId: drift.Value(restoredBirthdayId),
                    personId: drift.Value(personId),
                    body: drift.Value(body),
                    tone: drift.Value(_string(fields, 'tone') ?? 'warm'),
                    length: drift.Value(_string(fields, 'length') ?? 'medium'),
                    status: drift.Value(remoteStatus),
                    providerType: drift.Value(
                      _string(fields, 'providerType') ?? 'manual',
                    ),
                    variationIndex: drift.Value(
                      _integer(fields, 'variationIndex') ?? 0,
                    ),
                    createdAt: drift.Value(
                      _date(fields, 'createdAt') ?? remoteUpdatedAt,
                    ),
                    updatedAt: drift.Value(remoteUpdatedAt),
                  ),
                );
            await (_db.update(
              _db.birthdays,
            )..where((row) => row.id.equals(restoredBirthdayId))).write(
              BirthdaysCompanion(
                draftId: drift.Value(revisionId),
                status: drift.Value(birthdayStatus),
                updatedAt: drift.Value(now),
              ),
            );
            conflictRecords.add(
              'drafts/$id: confirmed-sent original preserved; newer revision restored as $revisionId',
            );
            restored++;
            continue;
          }

          await _db
              .into(_db.messageDrafts)
              .insertOnConflictUpdate(
                MessageDraftsCompanion(
                  id: drift.Value(id),
                  birthdayId: drift.Value(restoredBirthdayId),
                  personId: drift.Value(personId),
                  body: drift.Value(body),
                  tone: drift.Value(_string(fields, 'tone') ?? 'warm'),
                  length: drift.Value(_string(fields, 'length') ?? 'medium'),
                  status: drift.Value(_string(fields, 'status') ?? 'draft'),
                  providerType: drift.Value(
                    _string(fields, 'providerType') ?? 'manual',
                  ),
                  variationIndex: drift.Value(
                    _integer(fields, 'variationIndex') ?? 0,
                  ),
                  createdAt: drift.Value(
                    _date(fields, 'createdAt') ?? remoteUpdatedAt,
                  ),
                  updatedAt: drift.Value(remoteUpdatedAt),
                ),
              );
          restored++;
        }

        var existingReminders = const <String, ReminderSettingsEntry>{};
        if (reminderDocs.isNotEmpty) {
          existingReminders = {
            for (final row
                in await _db.select(_db.reminderSettingsEntries).get())
              row.key: row,
          };
        }
        for (final doc in reminderDocs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final key = _string(fields, 'key');
          if (key == null) {
            skippedRecords.add('reminderSettings: missing key');
            continue;
          }

          // Reminder settings have no per-record sync timestamp. Preserve a
          // device-local preference rather than overwriting it with an older or
          // ambiguous cloud copy during a merge-style restore.
          final existing = existingReminders[key];
          if (existing != null) {
            conflictRecords.add(
              'reminderSettings/$key: device-local preference preserved',
            );
            continue;
          }

          await _db
              .into(_db.reminderSettingsEntries)
              .insertOnConflictUpdate(
                ReminderSettingsEntriesCompanion(
                  key: drift.Value(key),
                  enabled: drift.Value(_boolean(fields, 'enabled') ?? false),
                  kinds: drift.Value(_string(fields, 'kinds') ?? ''),
                  quietHoursStartMinutes: drift.Value(
                    _integer(fields, 'quietHoursStartMinutes') ?? 0,
                  ),
                  quietHoursEndMinutes: drift.Value(
                    _integer(fields, 'quietHoursEndMinutes') ?? 0,
                  ),
                ),
              );
          restored++;
        }
      });

      _logger?.info(
        'CloudRestore',
        'Restore finished successfully.',
        params: {
          'restored': restored,
          'skipped': skippedRecords.length,
          'conflicts': conflictRecords.length,
        },
      );

      return CloudSyncResult(
        success: true,
        downloadedCount: restored,
        skippedCount: skippedRecords.length,
        skippedRecords: skippedRecords,
        conflictCount: conflictRecords.length,
        conflictRecords: conflictRecords,
        timestamp: now,
      );
    } catch (error, stackTrace) {
      _logger?.error(
        'CloudRestore',
        'Restore could not be completed.',
        error: error,
        stackTrace: stackTrace,
      );
      return CloudSyncResult(
        success: false,
        error: error is FormatException
            ? 'Cloud restore could not be completed because the backup data is malformed.'
            : 'Cloud restore could not be completed. Check your connection and try again.',
        errorCode: error is FormatException ? 'sync' : 'network',
        retryable: error is! FormatException,
        timestamp: now,
      );
    }
  }
}
