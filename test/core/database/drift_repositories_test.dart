import 'package:ai_birthday/core/database/app_database.dart'
    hide Birthday, MessageDraft;
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/features/birthdays/application/birthday_lifecycle_service.dart';
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_handoff.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart'
    as domain;
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';
import 'package:ai_birthday/features/people/domain/person_enums.dart'
    hide DeliveryChannel;
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
      'batch import is all-or-nothing when a birthday write fails',
      () async {
        await db.customStatement('''
        CREATE TRIGGER reject_second_birthday BEFORE INSERT ON birthdays
        WHEN NEW.person_id = 'p-2'
        BEGIN
          SELECT RAISE(ABORT, 'birthday write failed');
        END;
      ''');
        final people = [person(id: 'p-1'), person(id: 'p-2')];
        final birthdays = [
          for (final p in people)
            Birthday(
              id: 'birthday-${p.id}',
              personId: p.id,
              cycleYear: 2026,
              date: DateTime(2026, 10, 15),
              createdAt: p.createdAt,
              updatedAt: p.updatedAt,
            ),
        ];

        await expectLater(
          repo.saveImportBatch(people: people, birthdays: birthdays),
          throwsA(isA<Exception>()),
        );

        expect(await repo.getPeople(), isEmpty);
        expect(await db.select(db.birthdays).get(), isEmpty);
      },
    );

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

    test(
      'getPeople sorts case-insensitively using database lower(name)',
      () async {
        await repo.savePerson(person(id: 'p-1', name: 'charlie'));
        await repo.savePerson(person(id: 'p-2', name: 'Alice'));
        await repo.savePerson(person(id: 'p-3', name: 'bob'));

        final people = await repo.getPeople();
        expect(people.map((p) => p.name).toList(), ['Alice', 'bob', 'charlie']);
      },
    );

    test(
      'watchPeople sorts case-insensitively using database lower(name)',
      () async {
        await repo.savePerson(person(id: 'p-1', name: 'bob'));
        await repo.savePerson(person(id: 'p-2', name: 'Alice'));

        final firstEmission = await repo.watchPeople().first;
        expect(firstEmission.map((p) => p.name).toList(), ['Alice', 'bob']);
      },
    );

    test(
      'savePerson and getPerson round-trip all fields including autoSendPolicy and deletedAt',
      () async {
        final now = DateTime.utc(2025, 5, 20, 10, 30);
        final p = domain.Person(
          id: 'full-fields-1',
          name: 'Jordan Lee',
          birthdayMonth: 7,
          birthdayDay: 14,
          birthYear: 1992,
          leapDayResolution: LeapDayResolution.mar1,
          phoneNumber: '+15551234567',
          email: 'jordan@example.com',
          relationship: RelationshipCategory.mentor,
          relationshipCloseness: RelationshipCloseness.close,
          preferredLanguage: 'fr',
          preferredTone: MessageTone.emotional,
          importantFacts: const ['Plays cello', 'Has twin brother'],
          notes: 'Met at university conference',
          preferredDeliveryChannel: DeliveryChannel.sms,
          timezone: 'Europe/Paris',
          autoPrepare: true,
          autoSendPolicy: AutoSendPolicy.manualOnly,
          createdAt: now,
          updatedAt: now,
          version: 5,
          deletedAt: null,
        );

        await repo.savePerson(p);
        final loaded = await repo.getPerson('full-fields-1');
        expect(loaded, isNotNull);
        expect(loaded!.id, 'full-fields-1');
        expect(loaded.name, 'Jordan Lee');
        expect(loaded.birthdayMonth, 7);
        expect(loaded.birthdayDay, 14);
        expect(loaded.birthYear, 1992);
        expect(loaded.leapDayResolution, LeapDayResolution.mar1);
        expect(loaded.phoneNumber, '+15551234567');
        expect(loaded.email, 'jordan@example.com');
        expect(loaded.relationship, RelationshipCategory.mentor);
        expect(loaded.relationshipCloseness, RelationshipCloseness.close);
        expect(loaded.preferredLanguage, 'fr');
        expect(loaded.preferredTone, MessageTone.emotional);
        expect(loaded.importantFacts, ['Plays cello', 'Has twin brother']);
        expect(loaded.notes, 'Met at university conference');
        expect(loaded.preferredDeliveryChannel, DeliveryChannel.sms);
        expect(loaded.timezone, 'Europe/Paris');
        expect(loaded.autoPrepare, isTrue);
        expect(loaded.autoSendPolicy, AutoSendPolicy.manualOnly);
        expect(loaded.version, 5);
        expect(loaded.deletedAt, isNull);
      },
    );
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

    test(
      'delivery choices and failures are append-only persisted outcomes',
      () async {
        final at = DateTime.utc(2026, 1, 4, 9);
        await repo.recordOutcome(
          birthdayId: 'b-history',
          draftId: 'draft-1',
          channel: DeliveryChannel.clipboard,
          outcome: DeliveryOutcome.copied,
          at: at,
        );
        await repo.recordOutcome(
          birthdayId: 'b-history',
          draftId: 'draft-1',
          channel: DeliveryChannel.share,
          outcome: DeliveryOutcome.launchFailed,
          at: at.add(const Duration(seconds: 1)),
        );
        await repo.recordOutcome(
          birthdayId: 'b-history',
          draftId: 'draft-1',
          channel: DeliveryChannel.share,
          outcome: DeliveryOutcome.notSent,
          at: at.add(const Duration(seconds: 2)),
        );

        final events = await repo.watchHandoffs().first;
        expect(events, hasLength(3));
        expect(
          events.map((event) => event.outcome),
          containsAll([
            DeliveryOutcome.copied,
            DeliveryOutcome.launchFailed,
            DeliveryOutcome.notSent,
          ]),
        );
        expect(events.every((event) => event.draftId == 'draft-1'), isTrue);
      },
    );
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

    test(
      'handoff evidence binds the exact draft revision and body hash',
      () async {
        await repo.recordHandoff(
          birthdayId: 'b-bound',
          draftId: 'draft-4',
          draftRevision: 4,
          bodyHash: 'body-sha256',
          channel: DeliveryChannel.whatsapp,
          at: DateTime.utc(2026, 1, 5),
        );

        final handoff = await repo.latestHandoffForBirthday('b-bound');
        expect(handoff?.draftId, 'draft-4');
        expect(handoff?.draftRevision, 4);
        expect(handoff?.bodyHash, 'body-sha256');
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

    test(
      'revision increments on every save and rejects stale writes',
      () async {
        final t = DateTime.utc(2026, 1, 1, 9);
        await db
            .into(db.birthdays)
            .insert(
              BirthdaysCompanion.insert(
                id: 'b-rev',
                personId: 'p-rev',
                cycleYear: 2026,
                date: DateTime(2026, 10, 15),
                status: BirthdayStatus.upcoming.name,
                createdAt: t,
                updatedAt: t,
              ),
            );
        MessageDraft draftWith(String body) => MessageDraft(
          id: 'b-rev',
          birthdayId: 'b-rev',
          personId: 'p-rev',
          body: body,
          // Same wall-clock second for every write: updatedAt cannot tell them
          // apart, only the revision can.
          createdAt: t,
          updatedAt: t,
        );

        expect(await repo.getDraft('b-rev'), isNull);
        expect(
          await repo.saveDraftAndUpdateBirthday(
            draftWith('v1'),
            BirthdayStatus.messageDrafted,
          ),
          isTrue,
        );
        final first = (await repo.getDraft('b-rev'))!;
        expect(first.revision, 1);

        expect(
          await repo.saveDraftAndUpdateBirthday(
            draftWith('v2'),
            BirthdayStatus.messageDrafted,
            requireUnchanged: true,
            expectedCurrentDraft: first,
          ),
          isTrue,
        );
        final second = (await repo.getDraft('b-rev'))!;
        expect(second.revision, 2);
        expect(second.updatedAt, first.updatedAt);

        // A writer still holding revision 1 (same updatedAt!) must be rejected.
        expect(
          await repo.saveDraftAndUpdateBirthday(
            draftWith('stale'),
            BirthdayStatus.messageDrafted,
            requireUnchanged: true,
            expectedCurrentDraft: first,
          ),
          isFalse,
        );
        expect((await repo.getDraft('b-rev'))!.body, 'v2');
        expect((await repo.getDraft('b-rev'))!.revision, 2);

        // The revision on the incoming model never lowers the stored counter,
        // and a plain saveDraft also bumps it.
        await repo.saveDraft(draftWith('v3').copyWith(revision: 0));
        expect((await repo.getDraft('b-rev'))!.revision, 3);
      },
    );

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

    test(
      'draft and birthday link roll back together when linking fails',
      () async {
        final t = DateTime.utc(2026, 1, 1, 9);
        await db
            .into(db.birthdays)
            .insert(
              BirthdaysCompanion.insert(
                id: 'b-atomic',
                personId: 'p-atomic',
                cycleYear: 2026,
                date: DateTime.utc(2026, 10, 15),
                status: BirthdayStatus.upcoming.name,
                createdAt: t,
                updatedAt: t,
              ),
            );
        await db.customStatement('''
        CREATE TRIGGER reject_birthday_link BEFORE UPDATE ON birthdays
        WHEN NEW.id = 'b-atomic'
        BEGIN
          SELECT RAISE(ABORT, 'birthday link failed');
        END;
      ''');

        await expectLater(
          repo.saveDraftAndUpdateBirthday(
            MessageDraft(
              id: 'b-atomic',
              birthdayId: 'b-atomic',
              personId: 'p-atomic',
              body: 'Must not remain unlinked',
              status: DraftStatus.draft,
              createdAt: t,
              updatedAt: t.add(const Duration(minutes: 1)),
            ),
            BirthdayStatus.messageDrafted,
          ),
          throwsException,
        );

        expect(await repo.getDraft('b-atomic'), isNull);
        final birthday = await (db.select(
          db.birthdays,
        )..where((row) => row.id.equals('b-atomic'))).getSingle();
        expect(birthday.status, BirthdayStatus.upcoming.name);
        expect(birthday.draftId, isNull);
      },
    );

    test(
      'confirmed-sent drafts are immutable and later edits use a revision',
      () async {
        final t = DateTime.utc(2026, 1, 1, 9);
        final confirmed = MessageDraft(
          id: 'b-terminal',
          birthdayId: 'b-terminal',
          personId: 'p-terminal',
          body: 'The exact sent message',
          status: DraftStatus.draft,
          createdAt: t,
          updatedAt: t,
        );
        await repo.saveDraft(confirmed);
        await repo.saveDraft(
          confirmed.copyWith(status: DraftStatus.confirmedSent),
        );

        await expectLater(
          repo.saveDraft(
            confirmed.copyWith(
              body: 'Attempted overwrite',
              updatedAt: t.add(const Duration(minutes: 1)),
            ),
          ),
          throwsStateError,
        );

        final revision = confirmed.copyWith(
          id: 'b-terminal-revision-1',
          body: 'A new unsent edit',
          status: DraftStatus.draft,
          createdAt: t.add(const Duration(minutes: 1)),
          updatedAt: t.add(const Duration(minutes: 1)),
        );
        await repo.saveDraft(revision);

        final drafts = await repo.getAllDrafts();
        expect(drafts, hasLength(2));
        final original = drafts.singleWhere(
          (draft) => draft.id == 'b-terminal',
        );
        expect(original.body, 'The exact sent message');
        expect(original.status, DraftStatus.confirmedSent);
        expect((await repo.getDraftForBirthday('b-terminal'))?.id, revision.id);
      },
    );

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
      'unresolved legacy leap day refresh preserves cycle drafts and jobs',
      () async {
        final db = AppDatabase(NativeDatabase.memory());
        addTearDown(db.close);
        final birthdaysRepo = DriftBirthdaysRepository(db);
        final now = DateTime.utc(2026, 1, 1, 8);
        const birthdayId = 'birthday-p-leap';
        final person = domain.Person(
          id: 'p-leap',
          name: 'Legacy Leap',
          birthdayMonth: 2,
          birthdayDay: 29,
          createdAt: now,
          updatedAt: now,
        );
        await birthdaysRepo.saveBirthday(
          Birthday(
            id: birthdayId,
            personId: person.id,
            cycleYear: 2026,
            date: DateTime(2026, 2, 28),
            draftId: 'draft-active',
            createdAt: now,
            updatedAt: now,
          ),
        );
        for (final status in [DraftStatus.ready, DraftStatus.confirmedSent]) {
          await db
              .into(db.messageDrafts)
              .insert(
                MessageDraftsCompanion.insert(
                  id: status == DraftStatus.ready
                      ? 'draft-active'
                      : 'draft-sent',
                  birthdayId: birthdayId,
                  personId: person.id,
                  body: 'Message',
                  tone: 'warm',
                  length: 'standard',
                  status: status.name,
                  providerType: 'manual',
                  createdAt: now,
                  updatedAt: now,
                ),
              );
        }
        await db
            .into(db.jobs)
            .insert(
              JobsCompanion.insert(
                id: 'job-active',
                type: 'ai_generate',
                status: 'queued',
                subjectId: birthdayId,
                createdAt: now,
                maxAttempts: const Value(1),
              ),
            );

        await const BirthdayLifecycleService().refresh(
          people: [person],
          birthdaysRepository: birthdaysRepo,
        );

        expect(await birthdaysRepo.getBirthdayForPerson(person.id), isNotNull);
        expect(
          (await db.select(db.messageDrafts).get()).map((row) => row.id),
          unorderedEquals(['draft-active', 'draft-sent']),
        );
        expect(await db.select(db.jobs).get(), hasLength(1));
        expect(
          (await DriftDraftsRepository(db).getDraftForBirthday(birthdayId))?.id,
          'draft-active',
        );

        await const BirthdayLifecycleService().refresh(
          people: [person.copyWith(leapDayResolution: LeapDayResolution.feb28)],
          birthdaysRepository: birthdaysRepo,
        );
        expect(
          (await birthdaysRepo.getBirthdayForPerson(person.id))!.date,
          DateTime(2027, 2, 28),
        );
      },
    );

    test(
      'batch read self-heals duplicate personId rows (legacy data, no unique index)',
      () async {
        final db = AppDatabase(NativeDatabase.memory());
        addTearDown(db.close);
        final birthdaysRepo = DriftBirthdaysRepository(db);
        final now = DateTime.utc(2025, 1, 1, 8);

        // The composite unique index rejects same-cycle duplicates; drop it to
        // simulate legacy rows.
        await db.customStatement(
          'DROP INDEX idx_birthdays_person_cycle_year_unique',
        );
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

        await BirthdayLifecycleService(now: () => DateTime(2025, 3, 1)).refresh(
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
        );

        final rows = await birthdaysRepo.getBirthdays();
        expect(rows, hasLength(1));
        expect(rows.single.personId, 'p-1');
      },
    );

    test(
      'unique index rejects same-cycle duplicates but retains other cycles',
      () async {
        final db = AppDatabase(NativeDatabase.memory());
        addTearDown(db.close);
        final repo = DriftBirthdaysRepository(db);
        final now = DateTime.utc(2025, 1, 1, 8);
        Birthday birthday(String id) => Birthday(
          id: id,
          personId: 'p-1',
          cycleYear: 2025,
          date: DateTime(2025, 10, 15),
          createdAt: now,
          updatedAt: now,
        );
        await repo.saveBirthday(birthday('b-1'));
        await expectLater(
          repo.saveBirthday(birthday('b-2')),
          throwsA(anything),
        );
        await repo.saveBirthday(
          birthday('b-next-cycle').copyWith(cycleYear: 2026),
        );
        expect(await repo.getBirthdayForPerson('p-1'), isNotNull);
        expect((await repo.getBirthdays()).map((b) => b.cycleYear), [
          2025,
          2026,
        ]);
      },
    );
  });

  group('DriftDeliveryEventsRepository confirmation tests', () {
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
      'rejects a draft belonging to another birthday, including legacy handoff',
      () async {
        final now = DateTime.utc(2026, 1, 1, 9);
        Future<void> seedBirthday(String id) => db
            .into(db.birthdays)
            .insert(
              BirthdaysCompanion.insert(
                id: id,
                personId: 'p-$id',
                cycleYear: 2026,
                date: now,
                status: BirthdayStatus.handedOff.name,
                createdAt: now,
                updatedAt: now,
              ),
            );
        await seedBirthday('b-requested');
        await seedBirthday('b-other');
        await db
            .into(db.messageDrafts)
            .insert(
              MessageDraftsCompanion.insert(
                id: 'draft-other',
                birthdayId: 'b-other',
                personId: 'p-b-other',
                body: 'Other birthday message',
                tone: 'warm',
                length: 'standard',
                status: DraftStatus.ready.name,
                providerType: 'manual',
                createdAt: now,
                updatedAt: now,
              ),
            );
        // Legacy row intentionally has no draftId, so the ownership check must
        // still be enforced from the persisted draft itself.
        await repo.recordHandoff(
          birthdayId: 'b-requested',
          channel: DeliveryChannel.whatsapp,
          at: now,
        );

        await expectLater(
          repo.confirmHandoff(
            birthdayId: 'b-requested',
            draftId: 'draft-other',
            expectedBodyHash: 'hash-other',
            expectedDraftRevision: 1,
            at: now.add(const Duration(minutes: 1)),
          ),
          throwsStateError,
        );

        final requested = await (db.select(
          db.birthdays,
        )..where((row) => row.id.equals('b-requested'))).getSingle();
        final other = await (db.select(
          db.birthdays,
        )..where((row) => row.id.equals('b-other'))).getSingle();
        final draft = await (db.select(
          db.messageDrafts,
        )..where((row) => row.id.equals('draft-other'))).getSingle();
        expect(requested.status, BirthdayStatus.handedOff.name);
        expect(requested.draftId, isNull);
        expect(other.status, BirthdayStatus.handedOff.name);
        expect(draft.status, DraftStatus.ready.name);
        expect(await repo.watchHandoffs().first, hasLength(1));
      },
    );

    test(
      'confirmation requires evidence and atomically completes draft + birthday',
      () async {
        final now = DateTime.utc(2026, 1, 1, 9);
        await db
            .into(db.birthdays)
            .insert(
              BirthdaysCompanion.insert(
                id: 'b-confirm',
                personId: 'p-confirm',
                cycleYear: 2026,
                date: DateTime.utc(2026, 1, 1),
                status: 'handedOff',
                createdAt: now,
                updatedAt: now,
              ),
            );
        await db
            .into(db.messageDrafts)
            .insert(
              MessageDraftsCompanion.insert(
                id: 'draft-confirm',
                birthdayId: 'b-confirm',
                personId: 'p-confirm',
                body: 'Happy birthday',
                tone: 'warm',
                length: 'standard',
                status: 'ready',
                providerType: 'manual',
                createdAt: now,
                updatedAt: now,
              ),
            );

        await expectLater(
          repo.confirmHandoff(
            birthdayId: 'b-confirm',
            draftId: 'draft-confirm',
            expectedBodyHash: 'hash-confirm',
            expectedDraftRevision: 1,
            at: now,
          ),
          throwsStateError,
        );

        const confirmHash =
            '041ed57d3a0deefb20c55bd180b7d3733a936434ab5f1c8ac79cf0b7c626a092';
        await repo.recordHandoffAndMarkHandedOff(
          birthdayId: 'b-confirm',
          channel: DeliveryChannel.whatsapp,
          at: now,
          draftId: 'draft-confirm',
          draftRevision: 0,
          bodyHash: confirmHash,
        );
        await repo.confirmHandoff(
          birthdayId: 'b-confirm',
          draftId: 'draft-confirm',
          expectedBodyHash: confirmHash,
          expectedDraftRevision: 0,
          at: now.add(const Duration(minutes: 1)),
        );

        final birthday = await (db.select(
          db.birthdays,
        )..where((row) => row.id.equals('b-confirm'))).getSingle();
        final draft = await (db.select(
          db.messageDrafts,
        )..where((row) => row.id.equals('draft-confirm'))).getSingle();
        expect(birthday.status, 'completed');
        expect(draft.status, 'confirmedSent');
      },
    );
  });
}
