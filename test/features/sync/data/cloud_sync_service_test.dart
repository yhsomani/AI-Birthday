import 'dart:convert';
import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:ai_birthday/core/database/app_database.dart';
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
      final mockClient = MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          endsWith('documents:commit'),
          reason: 'N+1 fix: records upload in one batch, not one PATCH each',
        );
        expect(request.url.queryParameters['key'], isNotEmpty);
        commitCount++;

        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final writes = body['writes'] as List;
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
      expect(commitCount, 1);

      // Verify timestamp stored
      final lastSync = await service.getLastSyncTime('uid-test');
      expect(lastSync, isNotNull);
    });

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
      final mockClient = MockClient((request) async {
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
      expect(batchSizes, [500, 1]);
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

      final mockClient = MockClient((request) async {
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

    test('a backup with nothing new uploads zero rows', () async {
      final now = DateTime.now();
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-stale',
              name: 'Unchanged',
              relationship: 'Friend',
              relationshipCloseness: 'close',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '',
              preferredDeliveryChannel: 'whatsapp',
              autoSendPolicy: 'manualOnly',
              createdAt: now.subtract(const Duration(days: 1)),
              updatedAt: now.subtract(const Duration(days: 1)),
              version: 1,
            ),
          );

      // A full backup already ran moments ago; nothing changed since.
      store.data['cloud_last_sync_timestamp_uid-test'] = now.toIso8601String();

      var commitCount = 0;
      final mockClient = MockClient((request) async {
        commitCount++;
        return http.Response(
          jsonEncode({
            'writeResults': [
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
      );

      final result = await service.sync(_signedInAuth);
      expect(result.success, isTrue);
      expect(result.uploadedCount, 0);
      expect(commitCount, 0, reason: 'no changes, no Firestore round trip');
    });

    test('a backup after the initial one uploads only rows changed since the '
        'last sync, including tombstones', () async {
      final now = DateTime.now();
      // Previous backup: one hour ago.
      final lastSync = now.subtract(const Duration(hours: 1));
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-tomb',
              name: 'Deleted Since',
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
      await db
          .into(db.persons)
          .insert(
            PersonsCompanion.insert(
              id: 'p-stale',
              name: 'Already Uploaded',
              relationship: 'Friend',
              relationshipCloseness: 'close',
              preferredLanguage: 'en',
              preferredTone: 'warm',
              importantFacts: '',
              preferredDeliveryChannel: 'whatsapp',
              autoSendPolicy: 'manualOnly',
              createdAt: now.subtract(const Duration(hours: 2)),
              updatedAt: now.subtract(const Duration(hours: 2)),
              version: 1,
            ),
          );
      await db
          .into(db.messageDrafts)
          .insert(
            MessageDraftsCompanion.insert(
              id: 'd-new',
              birthdayId: 'b-1',
              personId: 'p-1',
              body: 'Edited since the last backup',
              tone: 'warm',
              length: 'short',
              status: 'ready',
              providerType: 'local',
              createdAt: now.subtract(const Duration(hours: 2)),
              updatedAt: now,
            ),
          );
      await db
          .into(db.messageDrafts)
          .insert(
            MessageDraftsCompanion.insert(
              id: 'd-stale',
              birthdayId: 'b-2',
              personId: 'p-2',
              body: 'Unchanged',
              tone: 'warm',
              length: 'short',
              status: 'ready',
              providerType: 'local',
              createdAt: now.subtract(const Duration(hours: 3)),
              updatedAt: now.subtract(const Duration(hours: 2)),
            ),
          );

      store.data['cloud_last_sync_timestamp_uid-test'] = lastSync
          .toIso8601String();

      final uploadedNames = <String>[];
      final mockClient = MockClient((request) async {
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        final writes = body['writes'] as List;
        expect(writes, hasLength(2));
        for (final write in writes.cast<Map<String, dynamic>>()) {
          final name = ((write['update'] as Map)['name'] as String);
          uploadedNames.add(name.split('/').last);
          expect(name, isNot(endsWith('/people/p-stale')));
          expect(name, isNot(endsWith('/drafts/d-stale')));
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

      final service = CloudSyncService(
        db: db,
        store: store,
        httpClient: mockClient,
      );

      final result = await service.sync(_signedInAuth);
      expect(result.success, isTrue);
      expect(result.uploadedCount, 2);
      expect(uploadedNames.toSet(), {'p-tomb', 'd-new'});
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

        final failingClient = MockClient((request) async {
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

      final mockClient = MockClient((request) async {
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

      final result = await service.restore(auth);
      expect(result.success, isTrue);
      expect(result.downloadedCount, 0);

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

      final mockClient = MockClient((request) async {
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

      final result = await service.restore(auth);
      expect(result.success, isTrue);

      final row = await (db.select(
        db.persons,
      )..where((r) => r.id.equals('p-del'))).getSingle();
      expect(row.deletedAt, isNotNull, reason: 'local tombstone must win');
    });

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

      final mockClient = MockClient((request) async {
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
      });

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

        final mockClient = MockClient((request) async {
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
        final client = MockClient((request) async {
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
