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

    test('uploads birthdays to Firestore and updates timestamp', () async {
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

      var patchCount = 0;
      final mockClient = MockClient((request) async {
        expect(request.method, 'PATCH');
        expect(request.url.queryParameters['key'], isNotEmpty);
        final body = jsonDecode(request.body) as Map<String, dynamic>;

        if (request.url.path.contains('/users/uid-test/people/p-1')) {
          expect(body['fields']['name']['stringValue'], 'Taylor Swift');
          patchCount++;
          return http.Response(
            jsonEncode({'name': 'projects/.../documents/p-1'}),
            200,
          );
        } else if (request.url.path.contains('/users/uid-test/birthdays/b-1')) {
          expect(body['fields']['personName']['stringValue'], 'Taylor Swift');
          patchCount++;
          return http.Response(
            jsonEncode({'name': 'projects/.../documents/b-1'}),
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
          email: 'taylor@example.com',
          displayName: 'Taylor',
          firebaseUid: 'uid-test',
          idToken: 'test-id-token',
        ),
      );

      final result = await service.sync(auth);
      expect(result.success, isTrue);
      expect(result.uploadedCount, 2);
      expect(patchCount, 2);

      // Verify timestamp stored
      final lastSync = await service.getLastSyncTime('uid-test');
      expect(lastSync, isNotNull);
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
  });
}
