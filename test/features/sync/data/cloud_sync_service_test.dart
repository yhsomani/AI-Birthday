import 'dart:convert';
import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:ai_birthday/core/database/app_database.dart';
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/core/security/credential_storage.dart';
import 'package:ai_birthday/features/auth/domain/auth_state.dart';
import 'package:ai_birthday/features/auth/domain/google_identity.dart';
import 'package:ai_birthday/features/sync/data/cloud_sync_service.dart';

import '../../../helpers/counting_query_interceptor.dart';

class InMemoryStoreDriver implements SecureStoreDriver {
  final Map<String, String> data = {};

  @override
  Future<void> delete(String key) async => data.remove(key);

  @override
  Future<String?> read(String key) async => data[key];

  @override
  Future<void> write(String key, String value) async => data[key] = value;
}

MockClient _backupAwareClient(
  Future<http.Response> Function(http.Request) handler,
) => MockClient((request) async {
  final isCurrentPointer =
      request.method == 'GET' && request.url.path.endsWith('/backup/current');
  try {
    final response = await handler(request);
    if (isCurrentPointer && response.statusCode != 200) {
      return http.Response('not found', 404);
    }
    if (isCurrentPointer && response.statusCode == 200) {
      try {
        final body = jsonDecode(response.body);
        if (body is! Map || body['fields'] is! Map) {
          return http.Response('not found', 404);
        }
      } catch (_) {
        return http.Response('not found', 404);
      }
    }
    return response;
  } catch (_) {
    if (isCurrentPointer) return http.Response('not found', 404);
    rethrow;
  }
});

void main() {
  allowMultipleInMemoryDatabases();
  group('CloudSyncService', () {
    late AppDatabase db;
    late InMemoryStoreDriver store;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      store = InMemoryStoreDriver();
    });

    tearDown(() async {
      await db.close();
    });

    test('returns failure when authState is signed out', () async {
      final service = CloudSyncService(db: db, store: store);
      final result = await service.sync(
        const AuthState(status: AuthStatus.signedOut),
      );

      expect(result.success, isFalse);
      expect(result.error, contains('Please sign in'));
    });

    test(
      'account switch blocks local records from being uploaded under B',
      () async {
        var requests = 0;
        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: _backupAwareClient((_) async {
            requests++;
            return http.Response('{}', 200);
          }),
        );
        await service.assignLocalDataOwner('account-a');

        final result = await service.sync(
          const AuthState(
            status: AuthStatus.signedIn,
            identity: GoogleIdentity(
              googleSubject: 'sub-b',
              email: 'b@example.com',
              displayName: 'B',
              firebaseUid: 'account-b',
              idToken: 'token-b',
            ),
          ),
        );

        expect(result.success, isFalse);
        expect(result.errorCode, 'ownership');
        expect(result.error, contains('Nothing has been uploaded'));
        expect(requests, 0);
        expect(await service.getLocalDataOwner(), 'account-a');
      },
    );

    test('uploads all records in one batched Firestore commit', () async {
      final now = DateTime.now();

      // Seed a person and birthday
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-1',
              name: 'Taylor Swift',
              birthdayMonth: const Value(12),
              birthdayDay: const Value(13),
              leapDayResolution: const Value('mar1'),
              relationship: 'Friend',
              relationshipCloseness: 'close',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: 'Pop superstar',
              preferredDeliveryChannel: 'WhatsApp',
              autoSendPolicy: 'manual',
              createdAt: now,
              updatedAt: now,
              version: 1,
            ),
          );

      await db
          .into(db.birthdays)
          .insert(
            BirthdaysCompanion.insert(
              id: 'b-1',
              personId: 'p-1',
              cycleYear: 2026,
              date: DateTime(2026, 12, 13),
              status: 'pending',
              createdAt: now,
              updatedAt: now,
            ),
          );

      var commitCount = 0;
      final mockClient = _backupAwareClient((request) async {
        expect(request.method, 'POST');
        expect(request.headers['X-Firebase-AppCheck'], 'test-app-check');
        expect(
          request.url.path,
          endsWith('documents:commit'),
          reason: 'N+1 fix: records upload in one batch, not one PATCH each',
        );
        expect(request.url.queryParameters['key'], isNotEmpty);
        commitCount++;

        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final writes = body['writes'] as List;
        final isPublish = writes.any(
          (w) => (((w as Map)['update'] as Map)['name'] as String).endsWith(
            '/backup/current',
          ),
        );
        if (isPublish) {
          // The second commit publishes the generation: manifest, then pointer.
          expect(writes, hasLength(2));
          return http.Response(
            jsonEncode({
              'commitTime': '2026-01-01T00:00:00Z',
              'writeResults': [
                {'updateTime': '2026-01-01T00:00:00Z'},
                {'updateTime': '2026-01-01T00:00:00Z'},
              ],
            }),
            200,
          );
        }
        expect(writes, hasLength(2));

        final names = writes
            .map(
              (w) =>
                  (((w as Map<String, dynamic>)['update'] as Map)['name']
                      as String),
            )
            .toList();
        expect(names, contains(endsWith('/people/p-1')));
        expect(names, contains(endsWith('/birthdays/b-1')));

        final personWrite = writes.cast<Map<String, dynamic>>().firstWhere(
          (w) =>
              ((w['update'] as Map)['name'] as String).endsWith('/people/p-1'),
        );
        final personFields =
            ((personWrite['update'] as Map)['fields'] as Map<String, dynamic>);
        expect(personFields['name']['stringValue'], 'Taylor Swift');
        expect(personFields['leapDayResolution']['stringValue'], 'mar1');

        final birthdayWrite = writes.cast<Map<String, dynamic>>().firstWhere(
          (w) => ((w['update'] as Map)['name'] as String).endsWith(
            '/birthdays/b-1',
          ),
        );
        final birthdayFields =
            ((birthdayWrite['update'] as Map)['fields']
                as Map<String, dynamic>);
        expect(birthdayFields['personName']['stringValue'], 'Taylor Swift');

        return http.Response(
          jsonEncode({
            'commitTime': '2026-01-01T00:00:00Z',
            'writeResults': [
              {'updateTime': '2026-01-01T00:00:00Z'},
              {'updateTime': '2026-01-01T00:00:00Z'},
            ],
          }),
          200,
        );
      });

      final service = CloudSyncService(
        db: db,
        store: store,
        httpClient: mockClient,
        appCheckTokenProvider: () async => 'test-app-check',
      );

      final auth = const AuthState(
        status: AuthStatus.signedIn,
        identity: GoogleIdentity(
          googleSubject: 'sub-test',
          email: 'taylor@example.com',
          displayName: 'Taylor',
          firebaseUid: 'uid-test',
          idToken: 'test-id-token',
        ),
      );

      final result = await service.sync(auth);
      expect(result.success, isTrue);
      expect(result.uploadedCount, 2);
      expect(commitCount, 2, reason: 'rows first, then the publish commit');

      // Verify timestamp stored
      final lastSync = await service.getLastSyncTime('uid-test');
      expect(lastSync, isNotNull);
    });

    test(
      'a changed current pointer rejects stale generation publication (A3-016)',
      () async {
        var sawConditionalPointerWrite = false;
        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: _backupAwareClient((request) async {
            if (request.method == 'GET') {
              return http.Response(
                jsonEncode({
                  'updateTime': '2026-01-01T00:00:00Z',
                  'fields': {
                    'generationId': {
                      'stringValue': 'another-device-generation',
                    },
                  },
                }),
                200,
              );
            }
            final body = jsonDecode(request.body) as Map<String, dynamic>;
            final writes = body['writes'] as List;
            final pointerWrite = writes.cast<Map<String, dynamic>>().firstWhere(
              (write) => ((write['update'] as Map)['name'] as String).endsWith(
                '/backup/current',
              ),
            );
            expect(pointerWrite['currentDocument'], {
              'updateTime': '2026-01-01T00:00:00Z',
            });
            sawConditionalPointerWrite = true;
            return http.Response('precondition failed', 409);
          }),
        );

        final result = await service.sync(_signedInAuth);

        expect(sawConditionalPointerWrite, isTrue);
        expect(result.success, isFalse);
        expect(result.error, contains('previous backup is unchanged'));
      },
    );

    test('chunks more than 500 writes across multiple commits', () async {
      final now = DateTime.now();
      for (var i = 0; i < 501; i++) {
        await db
            .into(db.persons)
            .insert(
              PersonsCompanion.insert(
                id: 'p-$i',
                name: 'Person $i',
                relationship: 'Friend',
                relationshipCloseness: 'close',
                preferredLanguage: 'en',
                preferredTone: 'warm',
                importantFacts: '',
                preferredDeliveryChannel: 'whatsapp',
                autoSendPolicy: 'manualOnly',
                createdAt: now,
                updatedAt: now,
                version: 1,
              ),
            );
      }

      final batchSizes = <int>[];
      final mockClient = _backupAwareClient((request) async {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final writes = body['writes'] as List;
        batchSizes.add(writes.length);
        return http.Response(
          jsonEncode({
            'writeResults': List.generate(
              writes.length,
              (_) => {'updateTime': '2026-01-01T00:00:00Z'},
            ),
          }),
          200,
        );
      });

      final service = CloudSyncService(
        db: db,
        store: store,
        httpClient: mockClient,
      );

      final auth = const AuthState(
        status: AuthStatus.signedIn,
        identity: GoogleIdentity(
          googleSubject: 'sub-test',
          email: 'user@example.com',
          displayName: 'User',
          firebaseUid: 'uid-test',
          idToken: 'test-id-token',
        ),
      );

      final result = await service.sync(auth);
      expect(result.success, isTrue);
      expect(result.uploadedCount, 501);
      // Row chunks first; the manifest and pointer are published last (F05).
      expect(batchSizes, [500, 1, 2]);
    });

    test('counts per-write failures inside a commit as incomplete', () async {
      final now = DateTime.now();
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-ok',
              name: 'Saved',
              relationship: 'Friend',
              relationshipCloseness: 'close',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '',
              preferredDeliveryChannel: 'whatsapp',
              autoSendPolicy: 'manualOnly',
              createdAt: now,
              updatedAt: now,
              version: 1,
            ),
          );
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-fail',
              name: 'Rejected',
              relationship: 'Friend',
              relationshipCloseness: 'close',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '',
              preferredDeliveryChannel: 'whatsapp',
              autoSendPolicy: 'manualOnly',
              createdAt: now,
              updatedAt: now,
              version: 1,
            ),
          );

      final mockClient = _backupAwareClient((request) async {
        return http.Response(
          jsonEncode({
            'writeResults': [
              {'updateTime': '2026-01-01T00:00:00Z'},
              {
                'error': {'code': 3, 'message': 'document too large'},
              },
            ],
          }),
          200,
        );
      });

      final service = CloudSyncService(
        db: db,
        store: store,
        httpClient: mockClient,
      );

      final auth = const AuthState(
        status: AuthStatus.signedIn,
        identity: GoogleIdentity(
          googleSubject: 'sub-test',
          email: 'user@example.com',
          displayName: 'User',
          firebaseUid: 'uid-test',
          idToken: 'test-id-token',
        ),
      );

      final result = await service.sync(auth);
      expect(result.success, isFalse);
      expect(result.uploadedCount, 1);
      expect(result.error, contains('1 of 2'));
      final lastSync = await service.getLastSyncTime('uid-test');
      expect(lastSync, isNull);
    });

    test(
      'every backup writes a complete generation, including unchanged rows (F05)',
      () async {
        final now = DateTime.now();
        await db
            .into(db.persons)
            .insert(
              PersonsCompanion.insert(
                id: 'p-complete',
                name: 'Unchanged Person',
                relationship: 'Friend',
                relationshipCloseness: 'close',
                preferredLanguage: 'en',
                preferredTone: 'warm',
                importantFacts: '',
                preferredDeliveryChannel: 'WhatsApp',
                autoSendPolicy: 'manual',
                createdAt: now.subtract(const Duration(days: 30)),
                updatedAt: now.subtract(const Duration(days: 30)),
                version: 1,
              ),
            );

        final rowNames = <String>[];
        final client = _backupAwareClient((request) async {
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          final writes = body['writes'] as List;
          for (final w in writes) {
            rowNames.add(((w as Map)['update'] as Map)['name'] as String);
          }
          return http.Response(
            jsonEncode({
              'writeResults': List.generate(
                writes.length,
                (_) => {'updateTime': '2026-01-01T00:00:00Z'},
              ),
            }),
            200,
          );
        });

        // A previous backup ran after this row last changed. Snapshot semantics
        // still send the row, so the new generation is complete.
        store.data['cloud_last_sync_timestamp_uid-test'] = now
            .toIso8601String();
        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: client,
        );
        final result = await service.sync(_signedInAuth);

        expect(result.success, isTrue);
        expect(result.uploadedCount, 1);
        expect(
          rowNames.where((n) => n.endsWith('/people/p-complete')),
          hasLength(1),
        );
      },
    );

    test(
      'malformed successful commit response never publishes a backup generation',
      () async {
        final now = DateTime.now();
        await db
            .into(db.persons)
            .insert(
              PersonsCompanion.insert(
                id: 'p-invalid-response',
                name: 'Preserved locally',
                relationship: 'Friend',
                relationshipCloseness: 'close',
                preferredLanguage: 'en',
                preferredTone: 'warm',
                importantFacts: '',
                preferredDeliveryChannel: 'whatsapp',
                autoSendPolicy: 'manualOnly',
                createdAt: now,
                updatedAt: now,
                version: 1,
              ),
            );

        var publishRequested = false;
        final client = _backupAwareClient((request) async {
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          final writes = body['writes'] as List;
          publishRequested = writes.any(
            (write) => (((write as Map)['update'] as Map)['name'] as String)
                .endsWith('/backup/current'),
          );
          return http.Response('{}', 200);
        });
        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: client,
        );

        final result = await service.sync(_signedInAuth);

        expect(result.success, isFalse);
        expect(result.errorCode, 'invalid_response');
        expect(result.error, contains('invalid response'));
        expect(publishRequested, isFalse);
        expect(await service.getLastSyncTime('uid-test'), isNull);
      },
    );

    test(
      'logs the status code when Firestore rejects a batch (A3-019)',
      () async {
        final logger = RecordingLogger();
        final service = CloudSyncService(
          db: db,
          store: store,
          logger: logger,
          httpClient: _backupAwareClient((request) async {
            if (request.method == 'GET') return http.Response('missing', 404);
            return http.Response('denied', 503);
          }),
        );

        final result = await service.sync(_signedInAuth);

        expect(result.success, isFalse);
        expect(
          logger.records.any(
            (record) =>
                record.message == 'Firestore batch write was rejected.' &&
                record.params['statusCode'] == 503,
          ),
          isTrue,
        );
      },
    );

    test('a failed row write never publishes the pointer (F05)', () async {
      final now = DateTime.now();
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-unpublished',
              name: 'Not Published',
              relationship: 'Friend',
              relationshipCloseness: 'close',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '',
              preferredDeliveryChannel: 'WhatsApp',
              autoSendPolicy: 'manual',
              createdAt: now,
              updatedAt: now,
              version: 1,
            ),
          );

      final requestedPaths = <String>[];
      final client = _backupAwareClient((request) async {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final writes = body['writes'] as List;
        requestedPaths.addAll(
          writes.map((w) => ((w as Map)['update'] as Map)['name'] as String),
        );
        return http.Response('Server Error', 500);
      });

      final service = CloudSyncService(
        db: db,
        store: store,
        httpClient: client,
      );
      final result = await service.sync(_signedInAuth);

      expect(result.success, isFalse);
      expect(
        requestedPaths.where((n) => n.endsWith('/backup/current')),
        isEmpty,
        reason: 'the pointer moves only after every row is stored',
      );
    });

    test(
      'returns failure and does not store timestamp when cloud writes fail',
      () async {
        final now = DateTime.now();

        await db
            .into(db.persons)
            .insert(
              PersonsCompanion.insert(
                id: 'p-fail',
                name: 'Failed User',
                relationship: 'Friend',
                relationshipCloseness: 'close',
                preferredLanguage: 'en',
                preferredTone: 'warm',
                importantFacts: 'None',
                preferredDeliveryChannel: 'WhatsApp',
                autoSendPolicy: 'manual',
                createdAt: now,
                updatedAt: now,
                version: 1,
              ),
            );

        final failingClient = _backupAwareClient((request) async {
          return http.Response('Forbidden', 403);
        });

        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: failingClient,
        );

        final auth = const AuthState(
          status: AuthStatus.signedIn,
          identity: GoogleIdentity(
            googleSubject: 'sub-test',
            email: 'user@example.com',
            displayName: 'User',
            firebaseUid: 'uid-test',
            idToken: 'test-id-token',
          ),
        );

        final result = await service.sync(auth);
        expect(result.success, isFalse);
        expect(result.uploadedCount, 0);
        expect(result.error, contains('Cloud backup failed'));

        final lastSync = await service.getLastSyncTime('uid-test');
        expect(lastSync, isNull);
      },
    );

    test('restore preserves existing local reminder preferences', () async {
      await db
          .into(db.reminderSettingsEntries)
          .insert(
            ReminderSettingsEntriesCompanion.insert(
              key: 'default',
              enabled: const Value(true),
              kinds: 'birthday',
              quietHoursStartMinutes: 120,
              quietHoursEndMinutes: 360,
            ),
          );

      final mockClient = _backupAwareClient(
        (outer) => servePublished(outer, (request) async {
          expect(request.method, 'GET');
          if (request.url.path.endsWith('/people') ||
              request.url.path.endsWith('/birthdays') ||
              request.url.path.endsWith('/drafts')) {
            return http.Response(jsonEncode({'documents': []}), 200);
          }
          if (request.url.path.endsWith('/reminderSettings')) {
            return http.Response(
              jsonEncode({
                'documents': [
                  {
                    'name':
                        'projects/test/databases/(default)/documents/users/uid-test/reminderSettings/default',
                    'fields': {
                      'key': {'stringValue': 'default'},
                      'enabled': {'booleanValue': false},
                      'kinds': {'stringValue': 'prepare'},
                      'quietHoursStartMinutes': {'integerValue': '900'},
                      'quietHoursEndMinutes': {'integerValue': '1020'},
                    },
                  },
                ],
              }),
              200,
            );
          }
          return http.Response('Not Found', 404);
        }),
      );

      final service = CloudSyncService(
        db: db,
        store: store,
        httpClient: mockClient,
      );

      final auth = const AuthState(
        status: AuthStatus.signedIn,
        identity: GoogleIdentity(
          googleSubject: 'sub-test',
          email: 'user@example.com',
          displayName: 'User',
          firebaseUid: 'uid-test',
          idToken: 'test-id-token',
        ),
      );

      final result = await service.restore(auth);
      expect(result.success, isTrue);
      expect(result.downloadedCount, 0);
      expect(result.skippedCount, 0);
      expect(result.conflictCount, 1);
      expect(
        result.conflictRecords.single,
        contains('device-local preference preserved'),
      );

      final local = await (db.select(
        db.reminderSettingsEntries,
      )..where((row) => row.key.equals('default'))).getSingle();
      expect(local.enabled, isTrue);
      expect(local.kinds, 'birthday');
      expect(local.quietHoursStartMinutes, 120);
      expect(local.quietHoursEndMinutes, 360);
    });

    test('restore does not resurrect a locally-tombstoned contact when the '
        'cloud copy has no tombstone', () async {
      final now = DateTime.now().toUtc();
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-del',
              name: 'Deleted Locally',
              relationship: 'Friend',
              relationshipCloseness: 'close',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '',
              preferredDeliveryChannel: 'whatsapp',
              autoSendPolicy: 'manualOnly',
              createdAt: now.subtract(const Duration(days: 30)),
              updatedAt: now,
              version: 2,
              deletedAt: Value(now),
            ),
          );

      final mockClient = _backupAwareClient(
        (outer) => servePublished(outer, (request) async {
          expect(request.method, 'GET');
          if (request.url.path.endsWith('/people')) {
            // Cloud copy is NEWER than the local tombstone but still live:
            // it predates the on-device deletion (no deletedAt field).
            return http.Response(
              jsonEncode({
                'documents': [
                  {
                    'name':
                        'projects/t/databases/(default)/documents/users/uid-test/people/p-del',
                    'fields': {
                      'id': {'stringValue': 'p-del'},
                      'name': {'stringValue': 'Deleted Locally'},
                      'relationship': {'stringValue': 'Friend'},
                      'relationshipCloseness': {'stringValue': 'close'},
                      'preferredLanguage': {'stringValue': 'en'},
                      'preferredTone': {'stringValue': 'warm'},
                      'importantFacts': {'stringValue': ''},
                      'preferredDeliveryChannel': {'stringValue': 'whatsapp'},
                      'autoSendPolicy': {'stringValue': 'manualOnly'},
                      'createdAt': {
                        'stringValue': now
                            .subtract(const Duration(days: 30))
                            .toIso8601String(),
                      },
                      'updatedAt': {
                        'stringValue': now
                            .add(const Duration(days: 1))
                            .toIso8601String(),
                      },
                      'version': {'integerValue': '2'},
                    },
                  },
                ],
              }),
              200,
            );
          }
          return http.Response(jsonEncode({'documents': []}), 200);
        }),
      );

      final service = CloudSyncService(
        db: db,
        store: store,
        httpClient: mockClient,
      );

      final auth = const AuthState(
        status: AuthStatus.signedIn,
        identity: GoogleIdentity(
          googleSubject: 'sub-test',
          email: 'user@example.com',
          displayName: 'User',
          firebaseUid: 'uid-test',
          idToken: 'test-id-token',
        ),
      );

      final result = await service.restore(auth);
      expect(result.success, isTrue);

      final row = await (db.select(
        db.persons,
      )..where((r) => r.id.equals('p-del'))).getSingle();
      expect(row.deletedAt, isNotNull, reason: 'local tombstone must win');
    });

    test(
      'restore does not reintroduce orphaned birthdays or drafts for a tombstoned contact (AUD-004)',
      () async {
        final now = DateTime.now().toUtc();
        await db
            .into(db.persons)
            .insert(
              PersonsCompanion.insert(
                id: 'p-tombstoned',
                name: 'Deleted Person',
                relationship: 'Friend',
                relationshipCloseness: 'close',
                preferredLanguage: 'en',
                preferredTone: 'warm',
                importantFacts: '',
                preferredDeliveryChannel: 'whatsapp',
                autoSendPolicy: 'manualOnly',
                createdAt: now.subtract(const Duration(days: 10)),
                updatedAt: now,
                version: 2,
                deletedAt: Value(now),
              ),
            );

        final mockClient = _backupAwareClient(
          (outer) => servePublished(outer, (request) async {
            if (request.url.path.endsWith('/birthdays')) {
              return http.Response(
                jsonEncode({
                  'documents': [
                    {
                      'name':
                          'projects/t/databases/(default)/documents/users/uid-test/birthdays/b-orphaned',
                      'fields': {
                        'id': {'stringValue': 'b-orphaned'},
                        'personId': {'stringValue': 'p-tombstoned'},
                        'date': {'stringValue': now.toIso8601String()},
                        'cycleYear': {'integerValue': '2026'},
                        'status': {'stringValue': 'upcoming'},
                        'createdAt': {'stringValue': now.toIso8601String()},
                        'updatedAt': {'stringValue': now.toIso8601String()},
                      },
                    },
                  ],
                }),
                200,
              );
            }
            if (request.url.path.endsWith('/message_drafts')) {
              return http.Response(
                jsonEncode({
                  'documents': [
                    {
                      'name':
                          'projects/t/databases/(default)/documents/users/uid-test/message_drafts/d-orphaned',
                      'fields': {
                        'id': {'stringValue': 'd-orphaned'},
                        'birthdayId': {'stringValue': 'b-orphaned'},
                        'personId': {'stringValue': 'p-tombstoned'},
                        'body': {'stringValue': 'Orphaned Draft'},
                        'tone': {'stringValue': 'warm'},
                        'length': {'stringValue': 'medium'},
                        'status': {'stringValue': 'draft'},
                        'providerType': {'stringValue': 'manual'},
                        'createdAt': {'stringValue': now.toIso8601String()},
                        'updatedAt': {'stringValue': now.toIso8601String()},
                      },
                    },
                  ],
                }),
                200,
              );
            }
            return http.Response(jsonEncode({'documents': []}), 200);
          }),
        );

        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: mockClient,
        );

        final auth = const AuthState(
          status: AuthStatus.signedIn,
          identity: GoogleIdentity(
            googleSubject: 'sub-test',
            email: 'user@example.com',
            displayName: 'User',
            firebaseUid: 'uid-test',
            idToken: 'test-id-token',
          ),
        );

        final result = await service.restore(auth);
        expect(result.success, isTrue);

        final birthdays = await db.select(db.birthdays).get();
        expect(
          birthdays,
          isEmpty,
          reason: 'must not restore birthday for tombstoned person',
        );

        final drafts = await db.select(db.messageDrafts).get();
        expect(
          drafts,
          isEmpty,
          reason: 'must not restore draft for tombstoned person',
        );
      },
    );

    test('restore reads each collection once, not once per document', () async {
      final interceptor = CountingQueryInterceptor();
      final countedDb = AppDatabase(
        NativeDatabase.memory().interceptWith(interceptor),
      );
      addTearDown(countedDb.close);

      Map<String, dynamic> personDoc(String id) => {
        'name':
            'projects/t/databases/(default)/documents/users/uid-test/people/$id',
        'fields': {
          'id': {'stringValue': id},
          'name': {'stringValue': 'Person $id'},
        },
      };

      final peopleDocs = List.generate(150, (i) => personDoc('restored-$i'));

      final mockClient = _backupAwareClient(
        (outer) => servePublished(outer, (request) async {
          expect(request.method, 'GET');
          if (request.url.path.endsWith('/people')) {
            return http.Response(jsonEncode({'documents': peopleDocs}), 200);
          }
          if (request.url.path.endsWith('/birthdays') ||
              request.url.path.endsWith('/drafts') ||
              request.url.path.endsWith('/reminderSettings')) {
            return http.Response(jsonEncode({'documents': []}), 200);
          }
          return http.Response('Not Found', 404);
        }),
      );

      final service = CloudSyncService(
        db: countedDb,
        store: store,
        httpClient: mockClient,
      );

      final auth = const AuthState(
        status: AuthStatus.signedIn,
        identity: GoogleIdentity(
          googleSubject: 'sub-test',
          email: 'user@example.com',
          displayName: 'User',
          firebaseUid: 'uid-test',
          idToken: 'test-id-token',
        ),
      );

      final result = await service.restore(auth);
      expect(result.success, isTrue);
      expect(result.downloadedCount, 150);

      // Before the fix: 1 SELECT per restored document (150). Now: one
      // preload per non-empty collection (1), empty collections are skipped.
      expect(interceptor.selects, 1);
      expect(interceptor.inserts, 150);
    });

    test(
      'a restore that fails on a later collection writes nothing locally (F06)',
      () async {
        Map<String, dynamic> personDoc(String id) => {
          'name':
              'projects/t/databases/(default)/documents/users/uid-test/people/$id',
          'fields': {
            'id': {'stringValue': id},
            'name': {'stringValue': 'Person $id'},
          },
        };

        final mockClient = _backupAwareClient(
          (outer) => servePublished(outer, (request) async {
            if (request.url.path.endsWith('/people')) {
              return http.Response(
                jsonEncode({
                  'documents': [personDoc('a'), personDoc('b')],
                }),
                200,
              );
            }
            if (request.url.path.endsWith('/birthdays')) {
              return http.Response('Server Error', 500);
            }
            return http.Response(jsonEncode({'documents': []}), 200);
          }),
        );

        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: mockClient,
        );

        final auth = const AuthState(
          status: AuthStatus.signedIn,
          identity: GoogleIdentity(
            googleSubject: 'sub-test',
            email: 'user@example.com',
            displayName: 'User',
            firebaseUid: 'uid-test',
            idToken: 'test-id-token',
          ),
        );

        final result = await service.restore(auth);
        expect(result.success, isFalse);

        // People downloaded fine, but the failure on a later collection happens
        // before any local write, so the local dataset is unchanged.
        expect(await db.select(db.persons).get(), isEmpty);
      },
    );

    test(
      'backup sends a freshly refreshed ID token, not the stored one (F14)',
      () async {
        final now = DateTime.now();
        await db
            .into(db.persons)
            .insert(
              PersonsCompanion.insert(
                id: 'p-token',
                name: 'Token Check',
                birthdayMonth: const Value(3),
                birthdayDay: const Value(4),
                relationship: 'Friend',
                relationshipCloseness: 'close',
                preferredLanguage: 'en',
                preferredTone: 'warm',
                importantFacts: '',
                preferredDeliveryChannel: 'WhatsApp',
                autoSendPolicy: 'manual',
                createdAt: now,
                updatedAt: now,
                version: 1,
              ),
            );

        final authorizations = <String?>[];
        final client = _backupAwareClient((request) async {
          authorizations.add(request.headers['Authorization']);
          return http.Response('{}', 500);
        });
        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: client,
          freshIdToken: () async => 'fresh-token',
        );

        const expiredAuth = AuthState(
          status: AuthStatus.signedIn,
          identity: GoogleIdentity(
            googleSubject: 'sub-token',
            email: 'user@example.com',
            displayName: 'User',
            firebaseUid: 'uid-token',
            idToken: 'expired-token',
          ),
        );
        await service.sync(expiredAuth);

        expect(authorizations, isNotEmpty);
        expect(authorizations, everyElement('Bearer fresh-token'));
      },
    );

    test(
      'restore preserves legacy-null and restores explicit leap policy',
      () async {
        final old = DateTime.utc(2026, 1, 1);
        await db
            .into(db.persons)
            .insert(
              PersonsCompanion.insert(
                id: 'p-existing',
                name: 'Existing Leap',
                birthdayMonth: const Value(2),
                birthdayDay: const Value(29),
                leapDayResolution: const Value('mar1'),
                relationship: 'friend',
                relationshipCloseness: 'close',
                preferredLanguage: 'en',
                preferredTone: 'warm',
                importantFacts: '',
                preferredDeliveryChannel: 'whatsapp',
                autoSendPolicy: 'manualOnly',
                createdAt: old,
                updatedAt: old,
                version: 1,
              ),
            );

        final newer = DateTime.utc(2026, 2, 1);
        final client = _backupAwareClient(
          (request) => servePublished(request, (subReq) async {
            if (!subReq.url.path.endsWith('/people')) {
              return http.Response(jsonEncode({'documents': []}), 200);
            }
            Map<String, dynamic> fields(String id, String name) => {
              'id': {'stringValue': id},
              'name': {'stringValue': name},
              'birthdayMonth': {'integerValue': '2'},
              'birthdayDay': {'integerValue': '29'},
              'createdAt': {'stringValue': old.toIso8601String()},
              'updatedAt': {'stringValue': newer.toIso8601String()},
              'version': {'integerValue': '2'},
            };
            return http.Response(
              jsonEncode({
                'documents': [
                  {
                    'name': 'users/uid-test/people/p-existing',
                    'fields': fields('p-existing', 'Existing Leap'),
                  },
                  {
                    'name': 'users/uid-test/people/p-new',
                    'fields': {
                      ...fields('p-new', 'New Leap'),
                      'leapDayResolution': {'stringValue': 'feb28'},
                    },
                  },
                  {
                    'name': 'users/uid-test/people/p-old-backup',
                    'fields': fields('p-old-backup', 'Legacy Leap'),
                  },
                ],
              }),
              200,
            );
          }),
        );

        final result = await CloudSyncService(
          db: db,
          store: store,
          httpClient: client,
        ).restore(_signedInAuth);

        expect(result.success, isTrue);
        final existing = await (db.select(
          db.persons,
        )..where((row) => row.id.equals('p-existing'))).getSingle();
        final restored = await (db.select(
          db.persons,
        )..where((row) => row.id.equals('p-new'))).getSingle();
        final legacy = await (db.select(
          db.persons,
        )..where((row) => row.id.equals('p-old-backup'))).getSingle();
        expect(existing.leapDayResolution, 'mar1');
        expect(restored.leapDayResolution, 'feb28');
        expect(legacy.leapDayResolution, isNull);
      },
    );

    test('legacy timestamp birthday dates restore as cycle days and drafts remap '
        'from duplicate IDs', () async {
      final baseTime = DateTime.utc(2025, 1, 1);
      final older = DateTime.utc(2026, 1, 1);
      final newer = DateTime.utc(2026, 2, 1);
      final localProfileUpdate = DateTime.utc(2027, 1, 1);
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-legacy',
              name: 'Locally Changed Birthday',
              birthdayMonth: const Value(4),
              birthdayDay: const Value(2),
              relationship: 'friend',
              relationshipCloseness: 'close',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '',
              preferredDeliveryChannel: 'whatsapp',
              autoSendPolicy: 'manualOnly',
              createdAt: baseTime,
              updatedAt: localProfileUpdate,
              version: 2,
            ),
          );
      await db
          .into(db.birthdays)
          .insert(
            BirthdaysCompanion.insert(
              id: 'b-local',
              personId: 'p-legacy',
              cycleYear: 2026,
              date: DateTime(2026, 4, 2),
              status: 'pending',
              createdAt: baseTime,
              updatedAt: older,
            ),
          );
      Map<String, dynamic> date(String value) => {'stringValue': value};
      Map<String, dynamic> integer(int value) => {'integerValue': '$value'};
      Map<String, dynamic> person = {
        'id': date('p-legacy'),
        'name': date('Legacy Birthday'),
        'birthdayMonth': integer(3),
        'birthdayDay': integer(1),
        'relationship': date('friend'),
        'relationshipCloseness': date('close'),
        'preferredLanguage': date('en'),
        'preferredTone': date('warm'),
        'importantFacts': date(''),
        'preferredDeliveryChannel': date('whatsapp'),
        'autoSendPolicy': date('manualOnly'),
        'createdAt': date(baseTime.toIso8601String()),
        'updatedAt': date(newer.toIso8601String()),
        'version': integer(1),
      };
      Map<String, dynamic> birthday(
        String id,
        String timestamp,
        DateTime updatedAt,
      ) => {
        'id': date(id),
        'personId': date('p-legacy'),
        'cycleYear': integer(2026),
        'date': date(timestamp),
        'status': date('pending'),
        'createdAt': date(baseTime.toIso8601String()),
        'updatedAt': date(updatedAt.toIso8601String()),
      };
      final docs = {
        'people': [
          {'name': 'users/uid-test/people/p-legacy', 'fields': person},
        ],
        'birthdays': [
          {
            'name': 'users/uid-test/birthdays/b-old',
            'fields': birthday('b-old', '2026-03-01T23:30:00-08:00', older),
          },
          {
            'name': 'users/uid-test/birthdays/b-survivor',
            'fields': birthday('b-survivor', '2026-03-02T03:30:00+00:00', newer)
              ..['status'] = date('messageDrafted')
              ..['draftId'] = date('d-legacy'),
          },
        ],
        'drafts': [
          {
            'name': 'users/uid-test/drafts/d-legacy',
            'fields': {
              'id': date('d-legacy'),
              'birthdayId': date('b-old'),
              'personId': date('p-legacy'),
              'body': date('Happy birthday!'),
              'tone': date('warm'),
              'length': date('short'),
              'status': date('draft'),
              'providerType': date('manual'),
              'variationIndex': integer(0),
              'createdAt': date(baseTime.toIso8601String()),
              'updatedAt': date(newer.toIso8601String()),
            },
          },
        ],
        'reminderSettings': <Map<String, dynamic>>[],
      };

      final result = await CloudSyncService(
        db: db,
        store: store,
        httpClient: _backupAwareClient(
          (request) => servePublished(request, (subRequest) async {
            for (final collection in docs.keys) {
              if (subRequest.url.path.endsWith('/$collection')) {
                return http.Response(
                  jsonEncode({'documents': docs[collection]}),
                  200,
                );
              }
            }
            return http.Response(jsonEncode({'documents': []}), 200);
          }),
        ),
      ).restore(_signedInAuth);

      expect(result.success, isTrue, reason: result.error);
      final birthdays = await db.select(db.birthdays).get();
      expect(birthdays, hasLength(1));
      expect(birthdays.single.id, 'b-local');
      expect(birthdays.single.date, DateTime(2026, 3, 1));
      expect(birthdays.single.status, 'messageDrafted');
      expect(birthdays.single.draftId, 'd-legacy');
      final draft = await db.select(db.messageDrafts).getSingle();
      expect(draft.birthdayId, 'b-local');
      expect(
        result.conflictRecords.any(
          (conflict) => conflict.contains(
            'current profile differs; cycle date reconstructed from snapshot profile',
          ),
        ),
        isTrue,
      );
    });

    test(
      'newer cloud draft cannot overwrite confirmed-sent local draft',
      () async {
        final localTime = DateTime.utc(2026, 1, 1);
        final remoteTime = DateTime.utc(2026, 2, 1);
        await db
            .into(db.persons)
            .insert(
              PersonsCompanion.insert(
                id: 'p-sent',
                name: 'Sent Recipient',
                relationship: 'friend',
                relationshipCloseness: 'close',
                preferredLanguage: 'en',
                preferredTone: 'warm',
                importantFacts: '',
                preferredDeliveryChannel: 'whatsapp',
                autoSendPolicy: 'manualOnly',
                createdAt: localTime,
                updatedAt: localTime,
                version: 1,
              ),
            );
        await db
            .into(db.birthdays)
            .insert(
              BirthdaysCompanion.insert(
                id: 'b-sent',
                personId: 'p-sent',
                cycleYear: 2026,
                date: DateTime.utc(2026, 10, 1),
                status: 'completed',
                draftId: const Value('d-sent'),
                createdAt: localTime,
                updatedAt: localTime,
              ),
            );
        await db
            .into(db.messageDrafts)
            .insert(
              MessageDraftsCompanion.insert(
                id: 'd-sent',
                birthdayId: 'b-sent',
                personId: 'p-sent',
                body: 'The exact confirmed message',
                tone: 'warm',
                length: 'medium',
                status: 'confirmedSent',
                providerType: 'manual',
                createdAt: localTime,
                updatedAt: localTime,
              ),
            );

        final client = _backupAwareClient(
          (request) => servePublished(request, (subReq) async {
            if (subReq.url.path.endsWith('/drafts')) {
              return http.Response(
                jsonEncode({
                  'documents': [
                    {
                      'name': 'users/uid-test/drafts/d-sent',
                      'fields': {
                        'id': {'stringValue': 'd-sent'},
                        'birthdayId': {'stringValue': 'b-sent'},
                        'personId': {'stringValue': 'p-sent'},
                        'body': {'stringValue': 'A newer unsent revision'},
                        'tone': {'stringValue': 'warm'},
                        'length': {'stringValue': 'medium'},
                        'status': {'stringValue': 'draft'},
                        'providerType': {'stringValue': 'manual'},
                        'variationIndex': {'integerValue': '0'},
                        'createdAt': {
                          'stringValue': localTime.toIso8601String(),
                        },
                        'updatedAt': {
                          'stringValue': remoteTime.toIso8601String(),
                        },
                      },
                    },
                  ],
                }),
                200,
              );
            }
            return http.Response(jsonEncode({'documents': []}), 200);
          }),
        );

        final result = await CloudSyncService(
          db: db,
          store: store,
          httpClient: client,
        ).restore(_signedInAuth);

        expect(result.success, isTrue);
        expect(
          result.downloadedCount,
          1,
          reason: '${result.skippedRecords} / ${result.conflictRecords}',
        );
        expect(result.conflictCount, 1);
        final drafts = await db.select(db.messageDrafts).get();
        expect(drafts, hasLength(2));
        final original = drafts.singleWhere((row) => row.id == 'd-sent');
        final revision = drafts.singleWhere((row) => row.id != 'd-sent');
        expect(original.body, 'The exact confirmed message');
        expect(original.status, 'confirmedSent');
        expect(revision.id, startsWith('d-sent-restore-revision-'));
        expect(revision.body, 'A newer unsent revision');
        final birthday = await (db.select(
          db.birthdays,
        )..where((row) => row.id.equals('b-sent'))).getSingle();
        expect(birthday.draftId, revision.id);
        expect(birthday.status, 'messageDrafted');
      },
    );

    test(
      'restore keeps a prior-cycle draft attached to its own cycle',
      () async {
        final createdAt = DateTime.utc(2024, 1, 1);
        final firstUpdate = DateTime.utc(2025, 1, 1);
        final secondUpdate = DateTime.utc(2026, 1, 1);
        Map<String, dynamic> stringValue(String value) => {
          'stringValue': value,
        };
        Map<String, dynamic> integerValue(int value) => {
          'integerValue': '$value',
        };
        final person = {
          'id': stringValue('p-cycle'),
          'name': stringValue('Cycle Person'),
          'birthdayMonth': integerValue(3),
          'birthdayDay': integerValue(1),
          'relationship': stringValue('friend'),
          'relationshipCloseness': stringValue('close'),
          'preferredLanguage': stringValue('en'),
          'preferredTone': stringValue('warm'),
          'importantFacts': stringValue(''),
          'preferredDeliveryChannel': stringValue('whatsapp'),
          'autoSendPolicy': stringValue('manualOnly'),
          'createdAt': stringValue(createdAt.toIso8601String()),
          'updatedAt': stringValue(secondUpdate.toIso8601String()),
        };
        Map<String, dynamic> birthday(
          String id,
          int cycleYear,
          String status,
          DateTime updatedAt, {
          String? draftId,
        }) => {
          'id': stringValue(id),
          'personId': stringValue('p-cycle'),
          'cycleYear': integerValue(cycleYear),
          'date': stringValue('$cycleYear-03-01T00:00:00Z'),
          'status': stringValue(status),
          if (draftId != null) 'draftId': stringValue(draftId),
          'createdAt': stringValue(createdAt.toIso8601String()),
          'updatedAt': stringValue(updatedAt.toIso8601String()),
        };
        final docs = {
          'people': [
            {'name': 'users/uid-test/people/p-cycle', 'fields': person},
          ],
          'birthdays': [
            {
              'name': 'users/uid-test/birthdays/b-cycle-2025',
              'fields': birthday(
                'b-cycle-2025',
                2025,
                'messageDrafted',
                firstUpdate,
                draftId: 'd-prior-cycle',
              ),
            },
            {
              'name': 'users/uid-test/birthdays/b-cycle-2026',
              'fields': birthday(
                'b-cycle-2026',
                2026,
                'messageDrafted',
                secondUpdate,
                draftId: 'd-prior-cycle',
              ),
            },
          ],
          'drafts': [
            {
              'name': 'users/uid-test/drafts/d-prior-cycle',
              'fields': {
                'id': stringValue('d-prior-cycle'),
                'birthdayId': stringValue('b-cycle-2025'),
                'personId': stringValue('p-cycle'),
                'body': stringValue('Earlier birthday draft'),
                'tone': stringValue('warm'),
                'length': stringValue('short'),
                'status': stringValue('draft'),
                'providerType': stringValue('manual'),
                'variationIndex': integerValue(0),
                'createdAt': stringValue(createdAt.toIso8601String()),
                'updatedAt': stringValue(firstUpdate.toIso8601String()),
              },
            },
          ],
          'reminderSettings': <Map<String, dynamic>>[],
        };

        final result = await CloudSyncService(
          db: db,
          store: store,
          httpClient: _backupAwareClient(
            (request) => servePublished(request, (subRequest) async {
              for (final collection in docs.keys) {
                if (subRequest.url.path.endsWith('/$collection')) {
                  return http.Response(
                    jsonEncode({'documents': docs[collection]}),
                    200,
                  );
                }
              }
              return http.Response(jsonEncode({'documents': []}), 200);
            }),
          ),
        ).restore(_signedInAuth);

        expect(result.success, isTrue, reason: result.error);
        final birthdays = await db.select(db.birthdays).get();
        final earlier = birthdays.singleWhere((row) => row.cycleYear == 2025);
        final current = birthdays.singleWhere((row) => row.cycleYear == 2026);
        expect(earlier.draftId, 'd-prior-cycle');
        expect(earlier.status, 'messageDrafted');
        expect(current.draftId, isNull);
        expect(current.status, 'messageNotPrepared');
        expect(
          (await db.select(db.messageDrafts).getSingle()).birthdayId,
          'b-cycle-2025',
        );
        expect(
          result.conflictRecords.any(
            (conflict) => conflict.contains(
              'referenced draft belongs to another or unknown cycle',
            ),
          ),
          isTrue,
        );
      },
    );

    test(
      'restore fails and aborts before write when a document is malformed (AUD-002)',
      () async {
        final client = _backupAwareClient((request) async {
          return servePublished(request, (subReq) async {
            if (subReq.url.path.endsWith('/people')) {
              // A required field with the wrong Firestore type is invalid.
              return http.Response(
                jsonEncode({
                  'documents': [
                    {
                      'name':
                          'users/uid-test/generations/g-test/people/p-malformed',
                      'fields': {
                        'id': {'stringValue': 'p-malformed'},
                        'name': {'integerValue': '42'},
                      },
                    },
                  ],
                }),
                200,
              );
            }
            return http.Response(jsonEncode({'documents': []}), 200);
          });
        });

        final service = CloudSyncService(
          db: db,
          store: store,
          httpClient: client,
        );

        final result = await service.restore(_signedInAuth);
        expect(result.success, isFalse);
        expect(result.errorCode, 'sync');
        expect(result.retryable, isFalse);
        expect(result.error, contains('malformed'));

        // Verify no rows were inserted into local DB
        final localPeople = await db.select(db.persons).get();
        expect(localPeople, isEmpty);
      },
    );
  });
}

/// Signed-in identity used by the incremental-backup tests.
const AuthState _signedInAuth = AuthState(
  status: AuthStatus.signedIn,
  identity: GoogleIdentity(
    googleSubject: 'sub-test',
    email: 'user@example.com',
    displayName: 'User',
    firebaseUid: 'uid-test',
    idToken: 'test-id-token',
  ),
);

const _publishedGeneration = 'g-test';
const _snapshotCollections = [
  'people',
  'birthdays',
  'drafts',
  'reminderSettings',
];

/// Serves a published backup generation for restore tests. The pointer and
/// manifest are synthesised, with per-collection counts taken from the
/// responses [serve] gives for each collection, so each test keeps its own
/// documents and the restore's count check still runs.
Future<http.Response> servePublished(
  http.Request request,
  Future<http.Response> Function(http.Request request) serve,
) async {
  final path = request.url.path;
  if (path.endsWith('/backup/current')) {
    return http.Response(
      jsonEncode({
        'fields': {
          'generationId': {'stringValue': _publishedGeneration},
        },
      }),
      200,
    );
  }
  if (path.endsWith('/generations/$_publishedGeneration')) {
    final fields = <String, dynamic>{
      'schemaVersion': {'integerValue': '1'},
    };
    for (final name in _snapshotCollections) {
      final served = await serve(
        http.Request(
          'GET',
          request.url.replace(
            path: '/v1/users/uid-test/generations/$_publishedGeneration/$name',
          ),
        ),
      );
      final body = jsonDecode(served.body) as Map<String, dynamic>;
      final documents = (body['documents'] as List?) ?? const [];
      fields[name] = {'integerValue': '${documents.length}'};
    }
    return http.Response(jsonEncode({'fields': fields}), 200);
  }
  return serve(request);
}
