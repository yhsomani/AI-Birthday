import 'package:ai_birthday/core/database/app_database.dart' hide MessageDraft;
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/features/birthdays/application/birthday_lifecycle_service.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_handoff.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart'
    as domain;
import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../helpers/counting_query_interceptor.dart';

void main() {
  allowMultipleInMemoryDatabases();
  group('DriftPeopleRepository', () {
    late AppDatabase db;
    late DriftPeopleRepository repo;

    domain.Person person({
      String id = 'p',
      int version = 1,
      String name = 'Ana',
    }) {
      final now = DateTime.utc(2025, 1, 1, 8);
      return domain.Person(
        id: id,
        name: name,
        birthdayMonth: 10,
        birthdayDay: 15,
        createdAt: now,
        updatedAt: now,
        version: version,
      );
    }

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      repo = DriftPeopleRepository(db);
    });

    tearDown(() async {
      await db.close();
    });

    test(
      'deletePerson tombstones the row and bumps the sync version',
      () async {
        await repo.savePerson(person(version: 3));

        await repo.deletePerson('p');

        final rows = await db.select(db.persons).get();
        expect(rows, hasLength(1));
        expect(rows.single.deletedAt, isNotNull);
        expect(rows.single.version, 4);
        // Tombstoned rows must no longer surface through the repository API.
        expect(await repo.getPeople(), isEmpty);
        expect(await repo.getPerson('p'), isNull);
      },
    );

    test('savePerson never resurrects a tombstoned row', () async {
      await repo.savePerson(person());
      await repo.deletePerson('p');

      // A later save touching the same id must keep the tombstone, not force
      // deletedAt back to null (audit F-1).
      await repo.savePerson(person());

      final row = await (db.select(
        db.persons,
      )..where((r) => r.id.equals('p'))).getSingle();
      expect(row.deletedAt, isNotNull);
    });

    test('getPeople is deterministically ordered by name then id', () async {
      await repo.savePerson(person(id: 'p-b', name: 'Alex'));
      await repo.savePerson(person(id: 'p-a', name: 'Alex'));

      // Equal names must not flip order between emissions (query audit
      // Phase 4: unique id tie-breaker).
      final people = await repo.getPeople();
      expect(people.map((p) => p.id).toList(), ['p-a', 'p-b']);
    });
  });

  group('DriftDeliveryEventsRepository (audit 03 P1-1)', () {
    late AppDatabase db;
    late DriftDeliveryEventsRepository repo;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      repo = DriftDeliveryEventsRepository(db);
    });

    tearDown(() async {
      await db.close();
    });

    test(
      'records persist and latestHandoffForBirthday returns newest',
      () async {
        final early = DateTime.utc(2026, 1, 1, 9);
        final late = DateTime.utc(2026, 1, 2, 9);

        expect(await repo.latestHandoffForBirthday('b1'), isNull);

        await repo.recordHandoff(
          birthdayId: 'b1',
          channel: DeliveryChannel.whatsapp,
          at: early,
        );
        await repo.recordHandoff(
          birthdayId: 'b1',
          channel: DeliveryChannel.sms,
          at: late,
        );
        await repo.recordHandoff(
          birthdayId: 'b2',
          channel: DeliveryChannel.share,
          at: early,
        );

        // Newest wins, and the channel is preserved exactly as launched.
        final latest = await repo.latestHandoffForBirthday('b1');
        expect(latest?.channel, DeliveryChannel.sms);
        expect(latest?.at, late);

        final all = await repo.watchHandoffs().first;
        expect(all, hasLength(3));
        final b2 = all.singleWhere((h) => h.birthdayId == 'b2');
        expect(b2.channel, DeliveryChannel.share);
      },
    );

    test(
      'watchLatestHandoffs batches to requested birthdays, newest wins',
      () async {
        await repo.recordHandoff(
          birthdayId: 'b1',
          channel: DeliveryChannel.whatsapp,
          at: DateTime.utc(2026, 1, 1, 9),
        );
        await repo.recordHandoff(
          birthdayId: 'b1',
          channel: DeliveryChannel.sms,
          at: DateTime.utc(2026, 1, 2, 9),
        );
        await repo.recordHandoff(
          birthdayId: 'b2',
          channel: DeliveryChannel.clipboard,
          at: DateTime.utc(2026, 1, 3, 9),
        );
        await repo.recordHandoff(
          birthdayId: 'b3',
          channel: DeliveryChannel.share,
          at: DateTime.utc(2026, 1, 4, 9),
        );

        // Only the requested birthdays are read; each resolves to its newest
        // event — b2's events are never materialized (query audit).
        final byId = <String, DeliveryHandoff>{
          for (final h in await repo.watchLatestHandoffs({'b1', 'b3'}).first)
            h.birthdayId: h,
        };
        expect(byId.keys.toSet(), {'b1', 'b3'});
        expect(byId['b1']?.channel, DeliveryChannel.sms);
        expect(byId['b3']?.channel, DeliveryChannel.share);
        expect(byId.containsKey('b2'), isFalse);
      },
    );

    test('watchLatestHandoffs emits an empty batch for no birthdays', () async {
      expect(await repo.watchLatestHandoffs({}).first, isEmpty);
    });
  });

  group('recordHandoffAndMarkHandedOff is one transaction (audit F20)', () {
    late AppDatabase db;
    late DriftDeliveryEventsRepository repo;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      repo = DriftDeliveryEventsRepository(db);
    });

    tearDown(() async {
      await db.close();
    });

    test(
      'the evidence row and the handed-off status are written together',
      () async {
        final now = DateTime.utc(2026, 1, 1, 9);
        await db
            .into(db.birthdays)
            .insert(
              BirthdaysCompanion.insert(
                id: 'b-tx',
                personId: 'p-tx',
                cycleYear: 2026,
                date: DateTime.utc(2026, 1, 1),
                status: 'pending',
                createdAt: now,
                updatedAt: now,
              ),
            );

        await repo.recordHandoffAndMarkHandedOff(
          birthdayId: 'b-tx',
          channel: DeliveryChannel.whatsapp,
          at: now,
        );

        final handoff = await repo.latestHandoffForBirthday('b-tx');
        expect(handoff?.channel, DeliveryChannel.whatsapp);
        final row = await (db.select(
          db.birthdays,
        )..where((r) => r.id.equals('b-tx'))).getSingle();
        expect(row.status, 'handedOff');
      },
    );
  });

  group('DriftDraftsRepository (stable ordering)', () {
    late AppDatabase db;
    late DriftDraftsRepository repo;

    setUp(() {
      db = AppDatabase(NativeDatabase.memory());
      repo = DriftDraftsRepository(db);
    });

    tearDown(() async {
      await db.close();
    });

    test('drafts sort by updatedAt desc with id desc tie-breaker', () async {
      final t = DateTime.utc(2026, 1, 1, 9);
      await repo.saveDraft(
        MessageDraft(
          id: 'd-b',
          birthdayId: 'b1',
          personId: 'p1',
          body: 'x',
          status: DraftStatus.ready,
          createdAt: t,
          updatedAt: t,
        ),
      );
      await repo.saveDraft(
        MessageDraft(
          id: 'd-a',
          birthdayId: 'b2',
          personId: 'p2',
          body: 'y',
          status: DraftStatus.ready,
          createdAt: t,
          updatedAt: t,
        ),
      );

      // Equal timestamps must not flip order between emissions
      // (query audit Phase 4).
      final drafts = await repo.getAllDrafts();
      expect(drafts.map((d) => d.id).toList(), ['d-b', 'd-a']);
    });

    test('pruneOrphanedDrafts removes shadowed legacy rows and dead-birthday '
        'drafts, keeps live ones, and is idempotent', () async {
      final t = DateTime.utc(2026, 1, 1, 9);
      Future<void> seedBirthday(String id) => db
          .into(db.birthdays)
          .insert(
            BirthdaysCompanion.insert(
              id: id,
              personId: 'p-$id',
              cycleYear: 2026,
              date: DateTime(2026, 10, 15),
              status: 'upcoming',
              createdAt: t,
              updatedAt: t,
            ),
          );
      Future<void> seedDraft({
        required String id,
        required String birthdayId,
      }) => repo.saveDraft(
        MessageDraft(
          id: id,
          birthdayId: birthdayId,
          personId: 'p',
          body: 'x',
          status: DraftStatus.ready,
          createdAt: t,
          updatedAt: t,
        ),
      );

      await seedBirthday('b1');
      await seedBirthday('b2');
      await seedDraft(id: 'b1', birthdayId: 'b1'); // canonical, live
      await seedDraft(id: 'draft-legacy', birthdayId: 'b1'); // shadowed dupe
      await seedDraft(id: 'draft-only', birthdayId: 'b2'); // legacy-only, live
      await seedDraft(id: 'draft-dead', birthdayId: 'b-gone'); // dead birthday

      expect(await repo.pruneOrphanedDrafts(), 2);

      final remaining = await repo.getAllDrafts();
      expect(remaining.map((d) => d.id).toSet(), {'b1', 'draft-only'});
      // The canonical row is still what the Studio resolves by birthdayId.
      expect((await repo.getDraftForBirthday('b1'))?.id, 'b1');

      // A second sweep finds nothing to remove.
      expect(await repo.pruneOrphanedDrafts(), 0);
    });

    test('pruneOrphanedDrafts with no birthdays removes every draft', () async {
      final t = DateTime.utc(2026, 1, 1, 9);
      await repo.saveDraft(
        MessageDraft(
          id: 'd1',
          birthdayId: 'b1',
          personId: 'p1',
          body: 'x',
          status: DraftStatus.ready,
          createdAt: t,
          updatedAt: t,
        ),
      );
      await repo.saveDraft(
        MessageDraft(
          id: 'd2',
          birthdayId: 'b2',
          personId: 'p2',
          body: 'y',
          status: DraftStatus.ready,
          createdAt: t,
          updatedAt: t,
        ),
      );

      expect(await repo.pruneOrphanedDrafts(), 2);
      expect(await repo.getAllDrafts(), isEmpty);
    });
  });

  group('BirthdayLifecycleService refresh (no N+1, SQL level)', () {
    test('reads people + birthdays once for 50 people', () async {
      final interceptor = CountingQueryInterceptor();
      final db = AppDatabase(
        NativeDatabase.memory().interceptWith(interceptor),
      );
      addTearDown(db.close);

      final peopleRepo = DriftPeopleRepository(db);
      final birthdaysRepo = DriftBirthdaysRepository(db);
      final now = DateTime.utc(2025, 1, 1, 8);

      for (var i = 0; i < 50; i++) {
        await peopleRepo.savePerson(
          domain.Person(
            id: 'p-$i',
            name: 'Person $i',
            birthdayMonth: 10,
            birthdayDay: 15,
            createdAt: now,
            updatedAt: now,
            version: 1,
          ),
        );
      }

      interceptor.selects = 0; // discount the seeding statements
      interceptor.inserts = 0;
      await const BirthdayLifecycleService().refresh(
        people: await peopleRepo.getPeople(),
        birthdaysRepository: birthdaysRepo,
      );

      // Before the fix this was 51 selects (getPeople + 50 × getBirthdayForPerson).
      expect(interceptor.selects, 2);
      expect(interceptor.inserts, 50);
    });

    test(
      'batch read throws on duplicate personId like getSingleOrNull',
      () async {
        final db = AppDatabase(NativeDatabase.memory());
        addTearDown(db.close);
        final birthdaysRepo = DriftBirthdaysRepository(db);
        final now = DateTime.utc(2025, 1, 1, 8);

        for (final id in ['b-1', 'b-2']) {
          await db
              .into(db.birthdays)
              .insert(
                BirthdaysCompanion.insert(
                  id: id,
                  personId: 'p-1',
                  cycleYear: 2025,
                  date: DateTime(2025, 10, 15),
                  status: 'upcoming',
                  createdAt: now,
                  updatedAt: now,
                ),
              );
        }

        await expectLater(
          const BirthdayLifecycleService().refresh(
            people: [
              domain.Person(
                id: 'p-1',
                name: 'Ana',
                birthdayMonth: 10,
                birthdayDay: 15,
                createdAt: now,
                updatedAt: now,
                version: 1,
              ),
            ],
            birthdaysRepository: birthdaysRepo,
          ),
          throwsStateError,
        );
      },
    );
  });
}
