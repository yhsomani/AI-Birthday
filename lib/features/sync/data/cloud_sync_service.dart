/// Cloud Sync Service connecting local Drift SQLite with Firebase Firestore REST (SSOT §13, §14).
library;

import 'dart:convert';
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
  })  : _db = db,
        _store = store,
        _http = httpClient ?? http.Client(),
        _logger = logger;

  final AppDatabase _db;
  final SecureStoreDriver _store;
  final http.Client _http;
  final AppLogger? _logger;

  static const String _projectId = 'relateai-birthday-ysomani';
  static const String _apiKey = 'AIzaSyDUgbmii4EH0PCHVOxO9TXvGeXyFpyxWNQ';
  static const String _lastSyncKey = 'cloud_last_sync_timestamp';

  Future<DateTime?> getLastSyncTime() async {
    try {
      final str = await _store.read(_lastSyncKey);
      if (str != null) return DateTime.tryParse(str);
    } catch (_) {}
    return null;
  }

  /// Synchronizes local birthdays with the authenticated user's Firestore collection.
  Future<CloudSyncResult> sync(AuthState authState) async {
    final now = DateTime.now();
    if (!authState.isSignedIn || authState.identity == null) {
      return CloudSyncResult(
        success: false,
        error: 'Please sign in to enable cloud backup & sync.',
        timestamp: now,
      );
    }

    final uid = authState.identity!.firebaseUid ?? authState.identity!.googleSubject;
    _logger?.info('CloudSync', 'Starting sync for user $uid');

    try {
      // 1. Fetch all local birthdays and associated people
      final birthdayRows = await _db.select(_db.birthdays).get();
      final personRows = await _db.select(_db.persons).get();
      final peopleMap = {for (final p in personRows) p.id: p};

      var uploaded = 0;
      final baseUrl =
          'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents/users/$uid/birthdays';

      // 2. Upload local entries to Firestore
      for (final b in birthdayRows) {
        final person = peopleMap[b.personId];
        final url = Uri.parse('$baseUrl/${b.id}?key=$_apiKey');

        final fields = <String, dynamic>{
          'id': {'stringValue': b.id},
          'personId': {'stringValue': b.personId},
          'personName': {'stringValue': person?.name ?? 'Unknown'},
          'cycleYear': {'integerValue': b.cycleYear.toString()},
          'month': {'integerValue': b.date.month.toString()},
          'day': {'integerValue': b.date.day.toString()},
          'date': {'stringValue': b.date.toIso8601String()},
          'status': {'stringValue': b.status},
          'updatedAt': {'stringValue': b.updatedAt.toIso8601String()},
        };

        final res = await _http.patch(
          url,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({'fields': fields}),
        );

        if (res.statusCode == 200) {
          uploaded++;
        }
      }

      // 3. Update last sync timestamp
      await _store.write(_lastSyncKey, now.toIso8601String());

      _logger?.info(
        'CloudSync',
        'Cloud sync finished successfully. Uploaded: $uploaded',
      );

      return CloudSyncResult(
        success: true,
        uploadedCount: uploaded,
        downloadedCount: 0,
        timestamp: now,
      );
    } catch (e, st) {
      _logger?.error(
        'CloudSync',
        'Sync failed with exception',
        error: e,
        stackTrace: st,
      );
      return CloudSyncResult(
        success: false,
        error: e.toString(),
        timestamp: now,
      );
    }
  }
}
