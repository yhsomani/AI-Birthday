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

class _MemoryStore implements SecureStoreDriver {
  final Map<String, String> data = {};

  @override
  Future<void> delete(String key) async => data.remove(key);

  @override
  Future<String?> read(String key) async => data[key];

  @override
  Future<void> write(String key, String value) async => data[key] = value;
}

const _auth = AuthState(
  status: AuthStatus.signedIn,
  identity: GoogleIdentity(
    googleSubject: 'sub-test',
    email: 'user@example.com',
    displayName: 'User',
    firebaseUid: 'uid-test',
    idToken: 'test-id-token',
  ),
);

/// Stateful stand-in for the Firestore REST API (commit, list, get).
class _FakeFirestore {
  /// Document path relative to `/documents/` -> fields.
  final Map<String, Map<String, dynamic>> docs = {};
  bool failDeletes = false;
  bool failListing = false;
  int deleteCommits = 0;
  int _pointerRevision = 0;
  final List<String> deletedPaths = [];

  static const _prefix = '/documents/';

  String _rel(String fullName) =>
      fullName.substring(fullName.indexOf(_prefix) + _prefix.length);

  Future<http.Response> handle(http.Request request) async {
    final path = request.url.path;
    if (request.method == 'POST' && path.endsWith('documents:commit')) {
      final writes =
          (jsonDecode(request.body) as Map<String, dynamic>)['writes'] as List;
      final pointerPath = 'users/uid-test/backup/current';
      for (final raw in writes) {
        final write = raw as Map<String, dynamic>;
        final update = write['update'] as Map<String, dynamic>?;
        if (update?['name'] !=
            'projects/p/databases/(default)/documents/$pointerPath') {
          continue;
        }
        final condition = write['currentDocument'] as Map<String, dynamic>?;
        if (condition == null) continue;
        if (condition['exists'] == false && docs.containsKey(pointerPath)) {
          return http.Response('precondition failed', 409);
        }
        if (condition['updateTime'] != null &&
            condition['updateTime'] !=
                '2026-01-01T00:00:${_pointerRevision.toString().padLeft(2, '0')}Z') {
          return http.Response('precondition failed', 409);
        }
      }
      final isDelete = writes.any((w) => (w as Map).containsKey('delete'));
      if (isDelete) {
        deleteCommits++;
        if (failDeletes) return http.Response('boom', 500);
      }
      final results = <Map<String, dynamic>>[];
      for (final w in writes) {
        final write = w as Map<String, dynamic>;
        if (write['update'] != null) {
          final update = write['update'] as Map<String, dynamic>;
          docs[_rel(update['name'] as String)] = (update['fields'] as Map)
              .cast<String, dynamic>();
          if ((update['name'] as String).endsWith('/backup/current')) {
            _pointerRevision++;
          }
          results.add({'updateTime': '2026-01-01T00:00:00Z'});
        } else {
          final rel = _rel(write['delete'] as String);
          docs.remove(rel);
          deletedPaths.add(rel);
          results.add({});
        }
      }
      return http.Response(jsonEncode({'writeResults': results}), 200);
    }

    final rel = path.substring(path.indexOf(_prefix) + _prefix.length);
    final single = docs[rel];
    if (single != null) {
      return http.Response(
        jsonEncode({
          'fields': single,
          if (rel == 'users/uid-test/backup/current')
            'updateTime':
                '2026-01-01T00:00:${_pointerRevision.toString().padLeft(2, '0')}Z',
        }),
        200,
      );
    }
    final segments = rel.split('/');
    // Odd number of segments after users/<uid>: a collection listing.
    if (segments.length.isEven) return http.Response('{}', 404);
    if (failListing) return http.Response('boom', 500);
    final showMissing = request.url.queryParameters['showMissing'] == 'true';
    final children = <String, Map<String, dynamic>?>{};
    for (final entry in docs.entries) {
      if (!entry.key.startsWith('$rel/')) continue;
      final rest = entry.key.substring(rel.length + 1).split('/');
      if (rest.length == 1) {
        children[entry.key] = entry.value;
      } else if (showMissing) {
        children.putIfAbsent('$rel/${rest.first}', () => null);
      }
    }
    return http.Response(
      jsonEncode({
        'documents': [
          for (final entry in children.entries)
            {
              'name': 'projects/p/databases/(default)/documents/${entry.key}',
              if (entry.value != null) 'fields': entry.value,
            },
        ],
      }),
      200,
    );
  }

  Set<String> get generationIds => {
    for (final key in docs.keys)
      if (key.startsWith('users/uid-test/generations/')) key.split('/')[3],
  };

  String? get currentGeneration =>
      (docs['users/uid-test/backup/current']?['generationId']
              as Map?)?['stringValue']
          as String?;

  bool hasManifest(String id) =>
      docs.containsKey('users/uid-test/generations/$id');

  bool hasChunks(String id) =>
      docs.keys.any((k) => k.startsWith('users/uid-test/generations/$id/'));

  void seedChunk(String id) {
    docs['users/uid-test/generations/$id/people/p-$id'] = {
      'id': {'stringValue': 'p-$id'},
    };
  }
}

Future<void> _insertPerson(
  AppDatabase db, {
  required String id,
  required String name,
  required DateTime updatedAt,
}) {
  return db
      .into(db.persons)
      .insert(
        PersonsCompanion.insert(
          id: id,
          name: name,
          relationship: 'Friend',
          relationshipCloseness: 'close',
          preferredLanguage: 'en',
          preferredTone: 'warm',
          importantFacts: '',
          preferredDeliveryChannel: 'WhatsApp',
          autoSendPolicy: 'manual',
          createdAt: updatedAt,
          updatedAt: updatedAt,
          version: 1,
        ),
      );
}

void main() {
  allowMultipleInMemoryDatabases();

  late AppDatabase db;
  late _MemoryStore store;
  late _FakeFirestore firestore;
  late CloudSyncService service;

  setUp(() async {
    db = AppDatabase(NativeDatabase.memory());
    store = _MemoryStore();
    firestore = _FakeFirestore();
    service = CloudSyncService(
      db: db,
      store: store,
      httpClient: MockClient(firestore.handle),
    );
    await _insertPerson(
      db,
      id: 'p-1',
      name: 'Alex',
      updatedAt: DateTime.utc(2026, 1, 1),
    );
  });

  tearDown(() async {
    await db.close();
  });

  Future<String> backup() async {
    final result = await service.sync(_auth);
    expect(result.success, isTrue, reason: result.error);
    await Future<void>.delayed(const Duration(milliseconds: 2));
    return firestore.currentGeneration!;
  }

  group('backup generation safety (A3-002/A3-016)', () {
    test(
      'retains all generations until server-coordinated pruning exists',
      () async {
        final generations = <String>[];
        for (var i = 0; i < 4; i++) {
          generations.add(await backup());
        }

        expect(firestore.generationIds, containsAll(generations));
        for (final id in generations) {
          expect(firestore.hasManifest(id), isTrue);
          expect(firestore.hasChunks(id), isTrue);
        }
        expect(firestore.deleteCommits, 0);
        expect(firestore.currentGeneration, generations.last);
      },
    );

    test('clock-skewed stale upload candidates are never deleted', () async {
      firestore.seedChunk('g1000');
      final stalePublished = 'g${DateTime.utc(2000).microsecondsSinceEpoch}';
      firestore.docs['users/uid-test/generations/$stalePublished'] = {
        'generationId': {'stringValue': stalePublished},
      };
      firestore.seedChunk(stalePublished);

      final current = await backup();

      expect(firestore.hasChunks('g1000'), isTrue);
      expect(firestore.hasChunks(stalePublished), isTrue);
      expect(firestore.hasManifest(stalePublished), isTrue);
      expect(firestore.hasChunks(current), isTrue);
      expect(firestore.deleteCommits, 0);
    });

    test(
      'overlapping publication candidates are not pruned by start order',
      () async {
        final earlyStartedButLatePublished =
            'g${DateTime.utc(2001).microsecondsSinceEpoch}';
        firestore
            .docs['users/uid-test/generations/$earlyStartedButLatePublished'] = {
          'generationId': {'stringValue': earlyStartedButLatePublished},
        };
        firestore.seedChunk(earlyStartedButLatePublished);

        await backup();

        expect(firestore.hasManifest(earlyStartedButLatePublished), isTrue);
        expect(firestore.hasChunks(earlyStartedButLatePublished), isTrue);
        expect(firestore.deleteCommits, 0);
      },
    );
  });

  group('backup timestamps (A3-014)', () {
    String stringOf(Map<String, dynamic> fields, String key) =>
        (fields[key] as Map)['stringValue'] as String;

    test(
      'are serialised as UTC instants; new birthday dates are versioned calendar days',
      () async {
        final local = DateTime(2026, 3, 1, 23, 30);
        await _insertPerson(db, id: 'p-2', name: 'Sam', updatedAt: local);
        await db
            .into(db.birthdays)
            .insert(
              BirthdaysCompanion.insert(
                id: 'b-2',
                personId: 'p-2',
                cycleYear: 2026,
                date: DateTime(2026, 12, 13),
                status: 'pending',
                createdAt: local,
                updatedAt: local,
              ),
            );

        final generation = await backup();
        final base = 'users/uid-test/generations/$generation';
        final person = firestore.docs['$base/people/p-2']!;
        expect(stringOf(person, 'updatedAt'), endsWith('Z'));
        expect(
          DateTime.parse(stringOf(person, 'updatedAt')).isAtSameMomentAs(local),
          isTrue,
        );
        expect(
          stringOf(firestore.docs['$base/birthdays/b-2']!, 'date'),
          '2026-12-13',
        );
        expect(
          (firestore.docs[base]!['schemaVersion'] as Map)['integerValue'],
          '2',
        );
        expect(
          stringOf(firestore.docs[base]!, 'birthdayDateEncoding'),
          'calendar-date-v1',
        );
        expect(
          (firestore.docs['users/uid-test/backup/current']!['schemaVersion']
              as Map)['integerValue'],
          '2',
        );
        expect(
          stringOf(
            firestore.docs['users/uid-test/backup/current']!,
            'publishedAt',
          ),
          endsWith('Z'),
        );
        expect(
          stringOf(
            firestore.docs['users/uid-test/generations/$generation']!,
            'createdAt',
          ),
          endsWith('Z'),
        );
        expect(
          (await store.read('cloud_last_sync_timestamp_uid-test'))!,
          endsWith('Z'),
        );
      },
    );

    Future<String> restoredName(
      String remoteUpdatedAt,
      DateTime localUpdatedAt,
    ) async {
      final generation = await backup();
      final personDoc =
          firestore.docs['users/uid-test/generations/$generation/people/p-1']!;
      personDoc['name'] = {'stringValue': 'Remote'};
      personDoc['updatedAt'] = {'stringValue': remoteUpdatedAt};
      personDoc['createdAt'] = {'stringValue': remoteUpdatedAt};
      await (db.update(db.persons)..where((t) => t.id.equals('p-1'))).write(
        PersonsCompanion(
          name: const Value('Local'),
          updatedAt: Value(localUpdatedAt),
        ),
      );
      final result = await service.restore(_auth);
      expect(result.success, isTrue, reason: result.error);
      return (await (db.select(
        db.persons,
      )..where((t) => t.id.equals('p-1'))).getSingle()).name;
    }

    test('legacy zone-less strings are read as local time', () async {
      // 12:00 local wall-clock; independent of the machine's timezone.
      final remoteInstant = DateTime(2026, 3, 1, 12);
      const naive = '2026-03-01T12:00:00.000';

      expect(
        await restoredName(
          naive,
          remoteInstant.subtract(const Duration(hours: 1)),
        ),
        'Remote',
        reason: 'remote is an hour newer than local',
      );
    });

    test(
      'legacy zone-less remote older than local does not overwrite',
      () async {
        final remoteInstant = DateTime(2026, 3, 1, 12);
        expect(
          await restoredName(
            '2026-03-01T12:00:00.000',
            remoteInstant.add(const Duration(hours: 1)),
          ),
          'Local',
        );
      },
    );

    test(
      'UTC strings compare as instants regardless of local offset',
      () async {
        expect(
          await restoredName(
            '2026-03-01T12:00:00.000Z',
            DateTime.utc(2026, 3, 1, 11),
          ),
          'Remote',
        );
      },
    );

    test('offset strings compare as instants', () async {
      expect(
        await restoredName(
          '2026-03-01T12:00:00.000+05:30',
          DateTime.utc(2026, 3, 1, 7),
        ),
        'Local',
        reason: '12:00+05:30 is 06:30Z, older than 07:00Z',
      );
    });

    test('a restored birthday keeps its calendar day', () async {
      await db
          .into(db.birthdays)
          .insert(
            BirthdaysCompanion.insert(
              id: 'b-1',
              personId: 'p-1',
              cycleYear: 2026,
              date: DateTime(2026, 12, 13),
              status: 'pending',
              createdAt: DateTime.utc(2026, 1, 1),
              updatedAt: DateTime.utc(2026, 1, 1),
            ),
          );
      final generation = await backup();
      final doc = firestore
          .docs['users/uid-test/generations/$generation/birthdays/b-1']!;
      // A legacy backup written in another zone: a zone-less midnight string.
      doc['date'] = {'stringValue': '2026-12-14T00:00:00.000'};
      doc['updatedAt'] = {'stringValue': '2027-01-01T00:00:00.000Z'};
      final result = await service.restore(_auth);
      expect(result.success, isTrue, reason: result.error);
      final row = await (db.select(
        db.birthdays,
      )..where((t) => t.id.equals('b-1'))).getSingle();
      expect([row.date.year, row.date.month, row.date.day], [2026, 12, 14]);
    });
  });
}
