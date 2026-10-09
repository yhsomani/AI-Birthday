library;

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:http/http.dart' as http;

import '../../../core/database/app_database.dart';
import '../../../core/logging/app_logger.dart';
import '../../../core/security/credential_storage.dart';
import '../../auth/domain/auth_state.dart';
import '../../auth/domain/google_identity.dart';

class CloudSyncResult {
  const CloudSyncResult({
    required this.success,
    this.uploadedCount = 0,
    this.downloadedCount = 0,
    this.error,
    this.errorCode,
    this.retryable = false,
    required this.timestamp,
  });

  final bool success;
  final int uploadedCount;
  final int downloadedCount;
  final String? error;

  /// Stable failure classification consumed by the durable job worker:
  /// `auth` (sign back in — permanent), `config` (permanent), or
  /// `sync`/`network` (transient — retryable).
  final String? errorCode;

  /// Whether a failed result can reasonably be retried as-is.
  final bool retryable;
  final DateTime timestamp;
}

class CloudSyncService {
  CloudSyncService({
    required AppDatabase db,
    required SecureStoreDriver store,
    http.Client? httpClient,
    AppLogger? logger,
    String? apiKey,
    Future<String?> Function()? freshIdToken,
  }) : _db = db,
       _store = store,
       _http = httpClient ?? http.Client(),
       _logger = logger,
       _freshIdToken = freshIdToken,
       _apiKey =
           apiKey ??
           (const String.fromEnvironment('FIREBASE_WEB_API_KEY').isNotEmpty
               ? const String.fromEnvironment('FIREBASE_WEB_API_KEY')
               : _defaultFirebaseApiKey);

  final AppDatabase _db;
  final SecureStoreDriver _store;
  final http.Client _http;
  final AppLogger? _logger;
  final String _apiKey;
  final Future<String?> Function()? _freshIdToken;

  static const String _projectId = 'relateai-birthday-ysoman-a2372';

  /// Firebase web API key of the shipped project. Public identifier (it also
  /// ships in `google-services.json`), so release builds work without a
  /// `--dart-define`; the request still needs a signed-in user's ID token.
  static const String _defaultFirebaseApiKey =
      'AIzaSyAhV3scPZPa_KBygWgg-57zkZL2QFf8HS4';

  static const String _lastSyncPrefix = 'cloud_last_sync_timestamp_';

  Future<DateTime?> getLastSyncTime(String accountId) async {
    try {
      final str = await _store.read(_lastSyncPrefix + accountId);
      if (str != null) return DateTime.tryParse(str);
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

  Map<String, String> _headers(AuthState authState) {
    final idToken = authState.identity?.idToken;
    return {
      'Content-Type': 'application/json',
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

  static const int _snapshotSchemaVersion = 1;

  /// Identifier for one immutable backup generation (audit F05).
  String _newGenerationId(DateTime now) =>
      'g${now.toUtc().microsecondsSinceEpoch}';

  /// Document URL under the owner's tree, for single-document reads.
  String _documentUrl(String uid, String path) {
    final encodedUid = Uri.encodeComponent(uid);
    return 'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents/users/$encodedUid/$path';
  }

  /// Fields of one document, or null when it does not exist.
  Future<Map<String, dynamic>?> _getDocumentFields(
    String url,
    Map<String, String> headers,
  ) async {
    final uri = Uri.parse(url).replace(queryParameters: {'key': _apiKey});
    final response = await _http.get(uri, headers: headers);
    if (response.statusCode == 404) return null;
    if (response.statusCode != 200) {
      throw const FormatException('CLOUD_GET_FAILED');
    }
    final body = jsonDecode(response.body);
    if (body is! Map<String, dynamic>) {
      throw const FormatException('CLOUD_GET_INVALID_RESPONSE');
    }
    return (body['fields'] as Map<String, dynamic>?) ?? <String, dynamic>{};
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
    final response = await _http.post(
      Uri.parse(_commitUrl()),
      headers: headers,
      body: jsonEncode({'writes': writes}),
    );
    if (response.statusCode != 200) return 0;
    final Object? decoded;
    try {
      decoded = jsonDecode(response.body);
    } catch (_) {
      // HTTP 200 with an unparsable body: mirror the former "200 == saved" rule.
      return writes.length;
    }
    if (decoded is! Map<String, dynamic>) return writes.length;
    final results = decoded['writeResults'];
    if (results is! List) return writes.length;
    var ok = 0;
    for (final result in results) {
      if (result is Map<String, dynamic> && result['error'] != null) continue;
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
    'date': _firestoreDate(b.date),
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
      final response = await _http.get(uri, headers: headers);

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

  Map<String, dynamic> _firestoreDate(DateTime value) => {
    'stringValue': value.toIso8601String(),
  };

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
    return raw == null ? null : DateTime.tryParse(raw);
  }

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

    if (_apiKey.isEmpty) {
      return CloudSyncResult(
        success: false,
        error: 'Cloud backup is not configured in this build.',
        timestamp: now,
      );
    }

    try {
      // Snapshot backup (audit F05): every row goes into a new immutable
      // generation, including unchanged rows, so each generation is complete.
      // The `backup/current` pointer only moves after the whole generation and
      // its manifest are stored, so a failed run leaves the last backup intact.
      final generationId = _newGenerationId(now);
      final allPersonRows = await _db.select(_db.persons).get();
      final allBirthdayRows = await _db.select(_db.birthdays).get();
      final allDraftRows = await _db.select(_db.messageDrafts).get();
      final reminderRows = await _db.select(_db.reminderSettingsEntries).get();
      // Resolve person names for birthday docs from the full set (tombstoned
      // people included).
      final peopleMap = {for (final p in allPersonRows) p.id: p};
      final headers = _headers(authState);
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

      await _store.write(_lastSyncPrefix + uid, now.toIso8601String());
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
        error:
            'Cloud backup could not be completed. Check your connection and try again.',
        errorCode: 'network',
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

    if (_apiKey.isEmpty) {
      return CloudSyncResult(
        success: false,
        error: 'Cloud restore is not configured in this build.',
        errorCode: 'config',
        timestamp: now,
      );
    }

    try {
      final headers = _headers(authState);
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
      final manifest = await _getDocumentFields(
        _documentUrl(uid, 'generations/$generationId'),
        headers,
      );
      if (manifest == null ||
          _integer(manifest, 'schemaVersion') != _snapshotSchemaVersion) {
        throw const FormatException('CLOUD_MANIFEST_INVALID');
      }

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

      // Apply every local write in one transaction, so a failure part-way
      // cannot leave a mixed dataset.
      await _db.transaction(() async {
        // Performance audit: load every existing row once instead of one SELECT
        // per restored document (N+1 when restoring a large backup).
        var existingPeople = const <String, Person>{};
        if (peopleDocs.isNotEmpty) {
          existingPeople = {
            for (final row in await _db.select(_db.persons).get()) row.id: row,
          };
        }
        for (final doc in peopleDocs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final id = _string(fields, 'id');
          final name = _string(fields, 'name');
          if (id == null || name == null) continue;

          final remoteUpdatedAt = _date(fields, 'updatedAt') ?? now;
          final existing = existingPeople[id];
          if (existing != null &&
              !remoteUpdatedAt.isAfter(existing.updatedAt)) {
            continue;
          }

          final deletedAt = _date(fields, 'deletedAt');
          // A local tombstone outranks a live cloud copy that has no tombstone:
          // restore must never resurrect a contact the user deleted on-device
          // (audit F-2). Tombstones in the cloud still apply below.
          if (existing?.deletedAt != null && deletedAt == null) continue;

          final createdAt = _date(fields, 'createdAt') ?? remoteUpdatedAt;
          await _db
              .into(_db.persons)
              .insertOnConflictUpdate(
                PersonsCompanion(
                  id: drift.Value(id),
                  name: drift.Value(name),
                  birthdayMonth: drift.Value(_integer(fields, 'birthdayMonth')),
                  birthdayDay: drift.Value(_integer(fields, 'birthdayDay')),
                  birthYear: drift.Value(_integer(fields, 'birthYear')),
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
          restored++;
        }

        var existingBirthdays = const <String, Birthday>{};
        if (birthdayDocs.isNotEmpty) {
          existingBirthdays = {
            for (final row in await _db.select(_db.birthdays).get())
              row.id: row,
          };
        }
        for (final doc in birthdayDocs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final id = _string(fields, 'id');
          final personId = _string(fields, 'personId');
          final date = _date(fields, 'date');
          if (id == null || personId == null || date == null) continue;

          final remoteUpdatedAt = _date(fields, 'updatedAt') ?? now;
          final existing = existingBirthdays[id];
          if (existing != null &&
              !remoteUpdatedAt.isAfter(existing.updatedAt)) {
            continue;
          }

          await _db
              .into(_db.birthdays)
              .insertOnConflictUpdate(
                BirthdaysCompanion(
                  id: drift.Value(id),
                  personId: drift.Value(personId),
                  cycleYear: drift.Value(
                    _integer(fields, 'cycleYear') ?? date.year,
                  ),
                  date: drift.Value(date),
                  status: drift.Value(_string(fields, 'status') ?? 'upcoming'),
                  draftId: drift.Value(_string(fields, 'draftId')),
                  createdAt: drift.Value(
                    _date(fields, 'createdAt') ?? remoteUpdatedAt,
                  ),
                  updatedAt: drift.Value(remoteUpdatedAt),
                ),
              );
          restored++;
        }

        var existingDrafts = const <String, MessageDraft>{};
        if (draftDocs.isNotEmpty) {
          existingDrafts = {
            for (final row in await _db.select(_db.messageDrafts).get())
              row.id: row,
          };
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
            continue;
          }

          final remoteUpdatedAt = _date(fields, 'updatedAt') ?? now;
          final existing = existingDrafts[id];
          if (existing != null &&
              !remoteUpdatedAt.isAfter(existing.updatedAt)) {
            continue;
          }

          await _db
              .into(_db.messageDrafts)
              .insertOnConflictUpdate(
                MessageDraftsCompanion(
                  id: drift.Value(id),
                  birthdayId: drift.Value(birthdayId),
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
          if (key == null) continue;

          // Reminder settings have no per-record sync timestamp. Preserve a
          // device-local preference rather than overwriting it with an older or
          // ambiguous cloud copy during a merge-style restore.
          final existing = existingReminders[key];
          if (existing != null) continue;

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
        params: {'restored': restored},
      );

      return CloudSyncResult(
        success: true,
        downloadedCount: restored,
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
        error:
            'Cloud restore could not be completed. Check your connection and try again.',
        errorCode: 'network',
        retryable: true,
        timestamp: now,
      );
    }
  }
}
