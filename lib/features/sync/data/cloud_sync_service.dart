/// Cloud Sync Service connecting local Drift SQLite with Firebase Firestore REST (SSOT §13, §14).
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
       _apiKey = apiKey ??
           (const String.fromEnvironment('FIREBASE_WEB_API_KEY').isNotEmpty
               ? const String.fromEnvironment('FIREBASE_WEB_API_KEY')
               : (httpClient != null || !const bool.fromEnvironment('dart.vm.product')
                   ? 'test_dev_firebase_api_key'
                   : ''));

  final AppDatabase _db;
  final SecureStoreDriver _store;
  final http.Client _http;
  final AppLogger? _logger;
  final String _apiKey;

  static const String _projectId = 'relateai-birthday-ysomani';
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
    if (_apiKey.isEmpty) {
      return CloudSyncResult(
        success: false,
        error: 'Cloud backup is not configured in this build.',
        timestamp: now,
      );
    }

    final uid =
        authState.identity!.firebaseUid ?? authState.identity!.googleSubject;
    _logger?.info('CloudSync', 'Starting sync for user $uid');

    try {
      // 1. Fetch all local birthdays and associated people
      final birthdayRows = await _db.select(_db.birthdays).get();
      final personRows = await _db.select(_db.persons).get();
      final peopleMap = {for (final p in personRows) p.id: p};

      var uploaded = 0;
      final birthdaysBaseUrl =
          'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents/users/$uid/birthdays';
      final peopleBaseUrl =
          'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents/users/$uid/people';

      final requestHeaders = <String, String>{
        'Content-Type': 'application/json',
        if (authState.identity?.idToken != null)
          'Authorization': 'Bearer ${authState.identity!.idToken}',
      };

      // 2. Upload local person contacts to Firestore
      for (final p in personRows) {
        final url = Uri.parse('$peopleBaseUrl/${p.id}?key=$_apiKey');
        final fields = <String, dynamic>{
          'id': {'stringValue': p.id},
          'name': {'stringValue': p.name},
          if (p.birthdayMonth != null)
            'birthdayMonth': {'integerValue': p.birthdayMonth.toString()},
          if (p.birthdayDay != null)
            'birthdayDay': {'integerValue': p.birthdayDay.toString()},
          if (p.birthYear != null)
            'birthYear': {'integerValue': p.birthYear.toString()},
          if (p.phoneNumber != null)
            'phoneNumber': {'stringValue': p.phoneNumber!},
          if (p.email != null) 'email': {'stringValue': p.email!},
          'relationship': {'stringValue': p.relationship},
          'relationshipCloseness': {'stringValue': p.relationshipCloseness},
          'preferredLanguage': {'stringValue': p.preferredLanguage},
          'preferredTone': {'stringValue': p.preferredTone},
          'importantFacts': {'stringValue': p.importantFacts},
          if (p.notes != null) 'notes': {'stringValue': p.notes!},
          'preferredDeliveryChannel': {
            'stringValue': p.preferredDeliveryChannel,
          },
          'createdAt': {'stringValue': p.createdAt.toIso8601String()},
          'updatedAt': {'stringValue': p.updatedAt.toIso8601String()},
        };

        final res = await _http.patch(
          url,
          headers: requestHeaders,
          body: jsonEncode({'fields': fields}),
        );

        if (res.statusCode == 200) {
          uploaded++;
        }
      }

      // 3. Upload local birthday cycle entries to Firestore
      for (final b in birthdayRows) {
        final person = peopleMap[b.personId];
        final url = Uri.parse('$birthdaysBaseUrl/${b.id}?key=$_apiKey');

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
          headers: requestHeaders,
          body: jsonEncode({'fields': fields}),
        );

        if (res.statusCode == 200) {
          uploaded++;
        }
      }

      final totalItems = personRows.length + birthdayRows.length;
      if (totalItems > 0 && uploaded < totalItems) {
        final errorMsg = uploaded == 0
            ? 'Cloud backup failed: none of the $totalItems items could be saved to cloud storage.'
            : 'Partial backup: only $uploaded of $totalItems items were saved to cloud storage.';
        _logger?.warning('CloudBackup', errorMsg);
        return CloudSyncResult(
          success: false,
          uploadedCount: uploaded,
          downloadedCount: 0,
          error: errorMsg,
          timestamp: now,
        );
      }

      // 4. Update last sync timestamp only on true success
      await _store.write(_lastSyncKey, now.toIso8601String());

      _logger?.info(
        'CloudBackup',
        'Cloud backup finished successfully. Uploaded: $uploaded',
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

  /// Restores cloud backup from Firestore REST into local Drift SQLite.
  Future<CloudSyncResult> restore(AuthState authState) async {
    final now = DateTime.now();
    if (!authState.isSignedIn || authState.identity == null) {
      return CloudSyncResult(
        success: false,
        error: 'Please sign in to restore from cloud backup.',
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

    final uid =
        authState.identity!.firebaseUid ?? authState.identity!.googleSubject;
    _logger?.info('CloudRestore', 'Starting restore for user $uid');

    try {
      final birthdaysBaseUrl =
          'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents/users/$uid/birthdays';
      final peopleBaseUrl =
          'https://firestore.googleapis.com/v1/projects/$_projectId/databases/(default)/documents/users/$uid/people';

      final requestHeaders = <String, String>{
        'Content-Type': 'application/json',
        if (authState.identity?.idToken != null)
          'Authorization': 'Bearer ${authState.identity!.idToken}',
      };

      var restored = 0;

      // 1. Fetch people documents from Firestore
      final peopleRes = await _http.get(
        Uri.parse('$peopleBaseUrl?key=$_apiKey'),
        headers: requestHeaders,
      );

      if (peopleRes.statusCode == 200) {
        final Map<String, dynamic> body = jsonDecode(peopleRes.body);
        final docs = body['documents'] as List<dynamic>? ?? [];
        for (final doc in docs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final id = fields['id']?['stringValue'] as String?;
          final name = fields['name']?['stringValue'] as String?;
          if (id == null || name == null) continue;

          final monthStr = fields['birthdayMonth']?['integerValue'] as String?;
          final dayStr = fields['birthdayDay']?['integerValue'] as String?;
          final yearStr = fields['birthYear']?['integerValue'] as String?;
          final phone = fields['phoneNumber']?['stringValue'] as String?;
          final email = fields['email']?['stringValue'] as String?;
          final rel =
              fields['relationship']?['stringValue'] as String? ?? 'friend';
          final closeness =
              fields['relationshipCloseness']?['stringValue'] as String? ??
              'close';
          final lang =
              fields['preferredLanguage']?['stringValue'] as String? ?? 'en';
          final tone =
              fields['preferredTone']?['stringValue'] as String? ?? 'warm';
          final facts =
              fields['importantFacts']?['stringValue'] as String? ?? '';
          final notes = fields['notes']?['stringValue'] as String?;
          final channel =
              fields['preferredDeliveryChannel']?['stringValue'] as String? ??
              'whatsapp';
          final createdAtStr = fields['createdAt']?['stringValue'] as String?;
          final updatedAtStr = fields['updatedAt']?['stringValue'] as String?;

          await _db
              .into(_db.persons)
              .insertOnConflictUpdate(
                PersonsCompanion(
                  id: drift.Value(id),
                  name: drift.Value(name),
                  birthdayMonth: drift.Value(
                    monthStr != null ? int.tryParse(monthStr) : null,
                  ),
                  birthdayDay: drift.Value(
                    dayStr != null ? int.tryParse(dayStr) : null,
                  ),
                  birthYear: drift.Value(
                    yearStr != null ? int.tryParse(yearStr) : null,
                  ),
                  phoneNumber: drift.Value(phone),
                  email: drift.Value(email),
                  relationship: drift.Value(rel),
                  relationshipCloseness: drift.Value(closeness),
                  preferredLanguage: drift.Value(lang),
                  preferredTone: drift.Value(tone),
                  importantFacts: drift.Value(facts),
                  notes: drift.Value(notes),
                  preferredDeliveryChannel: drift.Value(channel),
                  autoSendPolicy: const drift.Value('manualOnly'),
                  createdAt: drift.Value(
                    createdAtStr != null
                        ? DateTime.tryParse(createdAtStr) ?? now
                        : now,
                  ),
                  updatedAt: drift.Value(
                    updatedAtStr != null
                        ? DateTime.tryParse(updatedAtStr) ?? now
                        : now,
                  ),
                  version: const drift.Value(1),
                ),
              );
          restored++;
        }
      }

      // 2. Fetch birthday documents from Firestore
      final birthdaysRes = await _http.get(
        Uri.parse('$birthdaysBaseUrl?key=$_apiKey'),
        headers: requestHeaders,
      );

      if (birthdaysRes.statusCode == 200) {
        final Map<String, dynamic> body = jsonDecode(birthdaysRes.body);
        final docs = body['documents'] as List<dynamic>? ?? [];
        for (final doc in docs) {
          final fields = (doc['fields'] as Map<String, dynamic>?) ?? {};
          final id = fields['id']?['stringValue'] as String?;
          final personId = fields['personId']?['stringValue'] as String?;
          final dateStr = fields['date']?['stringValue'] as String?;
          final cycleYearStr = fields['cycleYear']?['integerValue'] as String?;
          final status =
              fields['status']?['stringValue'] as String? ?? 'upcoming';
          final updatedAtStr = fields['updatedAt']?['stringValue'] as String?;

          if (id == null || personId == null || dateStr == null) continue;
          final date = DateTime.tryParse(dateStr) ?? now;
          final cycleYear = cycleYearStr != null
              ? int.tryParse(cycleYearStr) ?? date.year
              : date.year;
          final updatedAt = updatedAtStr != null
              ? DateTime.tryParse(updatedAtStr) ?? now
              : now;

          await _db
              .into(_db.birthdays)
              .insertOnConflictUpdate(
                BirthdaysCompanion(
                  id: drift.Value(id),
                  personId: drift.Value(personId),
                  cycleYear: drift.Value(cycleYear),
                  date: drift.Value(date),
                  status: drift.Value(status),
                  createdAt: drift.Value(updatedAt),
                  updatedAt: drift.Value(updatedAt),
                ),
              );
          restored++;
        }
      }

      _logger?.info(
        'CloudRestore',
        'Cloud restore finished successfully. Downloaded/Restored: $restored',
      );

      return CloudSyncResult(
        success: true,
        uploadedCount: 0,
        downloadedCount: restored,
        timestamp: now,
      );
    } catch (e, st) {
      _logger?.error(
        'CloudRestore',
        'Restore failed with exception',
        error: e,
        stackTrace: st,
      );
      return CloudSyncResult(
        success: false,
        error: 'Cloud restore failed: ${e.toString()}',
        timestamp: now,
      );
    }
  }
}
