library;

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:http/http.dart' as http;

import '../../../core/database/app_database.dart';
import '../../../core/logging/app_logger.dart';
import '../../../core/security/credential_storage.dart';
import '../../auth/domain/auth_state.dart';

class CloudSyncResult {
  const CloudSyncResult({
    required this.success,
    this.uploadedCount = 0,
    this.downloadedCount = 0,
    this.error,
    required this.timestamp,
  });

  final bool success;
  final int uploadedCount;
  final int downloadedCount;
  final String? error;
  final DateTime timestamp;
}

class CloudSyncService {
  CloudSyncService({
    required AppDatabase db,
    required SecureStoreDriver store,
    http.Client? httpClient,
    AppLogger? logger,
    String? apiKey,
  }) : _db = db,
       _store = store,
       _http = httpClient ?? http.Client(),
       _logger = logger,
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

  static const String _projectId = 'relateai-birthday-ysomani';

  /// Firebase web API key of the shipped project. Public identifier (it also
  /// ships in `google-services.json`), so release builds work without a
  /// `--dart-define`; the request still needs a signed-in user's ID token.
  static const String _defaultFirebaseApiKey =
      'AIzaSyDUgbmii4EH0PCHVOxO9TXvGeXyFpyxWNQ';

  static const String _lastSyncPrefix = 'cloud_last_sync_timestamp_';

  Future<DateTime?> getLastSyncTime(String accountId) async {
    try {
      final str = await _store.read(_lastSyncPrefix + accountId);
      if (str != null) return DateTime.tryParse(str);
    } catch (_) {}
    return null;
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

  Future<bool> _patchDocument(
    String collectionUrl,
    String documentId,
    Map<String, dynamic> fields,
    Map<String, String> headers,
  ) async {
    final encodedDoc = Uri.encodeComponent(documentId);
    final url = Uri.parse('$collectionUrl/$encodedDoc?key=$_apiKey');
    final response = await _http.patch(
      url,
      headers: headers,
      body: jsonEncode({'fields': fields}),
    );
    return response.statusCode == 200;
  }

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
      final personRows = await _db.select(_db.persons).get();
      final birthdayRows = await _db.select(_db.birthdays).get();
      final draftRows = await _db.select(_db.messageDrafts).get();
      final reminderRows = await _db.select(_db.reminderSettingsEntries).get();
      final peopleMap = {for (final p in personRows) p.id: p};
      final headers = _headers(authState);

      final peopleUrl = _collectionUrl(uid, 'people');
      final birthdaysUrl = _collectionUrl(uid, 'birthdays');
      final draftsUrl = _collectionUrl(uid, 'drafts');
      final remindersUrl = _collectionUrl(uid, 'reminderSettings');

      var uploaded = 0;
      var totalItems = 0;
      var failures = 0;

      for (final p in personRows) {
        totalItems++;
        final fields = <String, dynamic>{
          'id': _firestoreString(p.id),
          'name': _firestoreString(p.name),
          if (p.birthdayMonth != null)
            'birthdayMonth': _firestoreInt(p.birthdayMonth!),
          if (p.birthdayDay != null)
            'birthdayDay': _firestoreInt(p.birthdayDay!),
          if (p.birthYear != null) 'birthYear': _firestoreInt(p.birthYear!),
          if (p.phoneNumber != null)
            'phoneNumber': _firestoreString(p.phoneNumber!),
          if (p.email != null) 'email': _firestoreString(p.email!),
          'relationship': _firestoreString(p.relationship),
          'relationshipCloseness': _firestoreString(p.relationshipCloseness),
          'preferredLanguage': _firestoreString(p.preferredLanguage),
          'preferredTone': _firestoreString(p.preferredTone),
          'importantFacts': _firestoreString(p.importantFacts),
          if (p.notes != null) 'notes': _firestoreString(p.notes!),
          'preferredDeliveryChannel': _firestoreString(
            p.preferredDeliveryChannel,
          ),
          if (p.timezone != null) 'timezone': _firestoreString(p.timezone!),
          'autoPrepare': _firestoreBool(p.autoPrepare),
          'autoSendPolicy': _firestoreString(p.autoSendPolicy),
          'createdAt': _firestoreDate(p.createdAt),
          'updatedAt': _firestoreDate(p.updatedAt),
          'version': _firestoreInt(p.version),
          if (p.deletedAt != null) 'deletedAt': _firestoreDate(p.deletedAt!),
        };

        if (await _patchDocument(peopleUrl, p.id, fields, headers)) {
          uploaded++;
        } else {
          failures++;
        }
      }

      for (final b in birthdayRows) {
        totalItems++;
        final person = peopleMap[b.personId];
        final fields = <String, dynamic>{
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

        if (await _patchDocument(birthdaysUrl, b.id, fields, headers)) {
          uploaded++;
        } else {
          failures++;
        }
      }

      for (final d in draftRows) {
        totalItems++;
        final fields = <String, dynamic>{
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

        if (await _patchDocument(draftsUrl, d.id, fields, headers)) {
          uploaded++;
        } else {
          failures++;
        }
      }

      for (final setting in reminderRows) {
        totalItems++;
        final fields = <String, dynamic>{
          'key': _firestoreString(setting.key),
          'enabled': _firestoreBool(setting.enabled),
          'kinds': _firestoreString(setting.kinds),
          'quietHoursStartMinutes': _firestoreInt(
            setting.quietHoursStartMinutes,
          ),
          'quietHoursEndMinutes': _firestoreInt(setting.quietHoursEndMinutes),
        };

        if (await _patchDocument(remindersUrl, setting.key, fields, headers)) {
          uploaded++;
        } else {
          failures++;
        }
      }

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
        timestamp: now,
      );
    }
  }

  Future<CloudSyncResult> restore(AuthState authState) async {
    final now = DateTime.now();
    final uid = _authenticatedUid(authState);

    if (uid == null) {
      return CloudSyncResult(
        success: false,
        error: authState.isSignedIn
            ? 'Your cloud session has expired. Sign in again before restoring.'
            : 'Please sign in to restore a cloud backup.',
        timestamp: now,
      );
    }

    if (_apiKey.isEmpty) {
      return CloudSyncResult(
        success: false,
        error: 'Cloud restore is not configured in this build.',
        timestamp: now,
      );
    }

    try {
      final headers = _headers(authState);
      var restored = 0;

      final peopleDocs = await _listDocuments(
        _collectionUrl(uid, 'people'),
        headers,
      );
      for (final doc in peopleDocs) {
        final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
        final id = _string(fields, 'id');
        final name = _string(fields, 'name');
        if (id == null || name == null) continue;

        final remoteUpdatedAt = _date(fields, 'updatedAt') ?? now;
        final existing = await (_db.select(
          _db.persons,
        )..where((row) => row.id.equals(id))).getSingleOrNull();
        if (existing != null && !remoteUpdatedAt.isAfter(existing.updatedAt)) {
          continue;
        }

        final createdAt = _date(fields, 'createdAt') ?? remoteUpdatedAt;
        final deletedAt = _date(fields, 'deletedAt');
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

      final birthdayDocs = await _listDocuments(
        _collectionUrl(uid, 'birthdays'),
        headers,
      );
      for (final doc in birthdayDocs) {
        final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
        final id = _string(fields, 'id');
        final personId = _string(fields, 'personId');
        final date = _date(fields, 'date');
        if (id == null || personId == null || date == null) continue;

        final remoteUpdatedAt = _date(fields, 'updatedAt') ?? now;
        final existing = await (_db.select(
          _db.birthdays,
        )..where((row) => row.id.equals(id))).getSingleOrNull();
        if (existing != null && !remoteUpdatedAt.isAfter(existing.updatedAt)) {
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

      final draftDocs = await _listDocuments(
        _collectionUrl(uid, 'drafts'),
        headers,
      );
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
        final existing = await (_db.select(
          _db.messageDrafts,
        )..where((row) => row.id.equals(id))).getSingleOrNull();
        if (existing != null && !remoteUpdatedAt.isAfter(existing.updatedAt)) {
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

      final reminderDocs = await _listDocuments(
        _collectionUrl(uid, 'reminderSettings'),
        headers,
      );
      for (final doc in reminderDocs) {
        final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
        final key = _string(fields, 'key');
        if (key == null) continue;

        // Reminder settings have no per-record sync timestamp. Preserve a
        // device-local preference rather than overwriting it with an older or
        // ambiguous cloud copy during a merge-style restore.
        final existing = await (_db.select(
          _db.reminderSettingsEntries,
        )..where((row) => row.key.equals(key))).getSingleOrNull();
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
        timestamp: now,
      );
    }
  }
}
