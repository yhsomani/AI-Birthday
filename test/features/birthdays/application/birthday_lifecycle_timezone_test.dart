/// A1-001 regression: the stored occurrence is a calendar date, so refreshing
/// repeatedly must never "roll over" (and delete drafts) whatever the
/// recipient or device time zone is.
///
/// The device zone is the process zone (`TZ`); CI/dev runs of this file under
/// `TZ=UTC`, `TZ=America/New_York` (UTC-5) and `TZ=Asia/Kolkata` (+5:30)
/// exercise the same assertions.
library;

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timezone/data/latest_all.dart' as tz_data;

import 'package:ai_birthday/core/database/app_database.dart' as db;
import 'package:ai_birthday/core/database/drift_repositories.dart';
import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/birthdays/application/birthday_lifecycle_service.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart'
    show MessageDraft;
import 'package:ai_birthday/features/people/domain/models/person.dart';

void main() {
  setUpAll(tz_data.initializeTimeZones);

  late db.AppDatabase database;
  late DriftBirthdaysRepository birthdays;
  late DriftDraftsRepository drafts;
  late DriftDeliveryEventsRepository deliveryEvents;

  setUp(() {
    database = db.AppDatabase(NativeDatabase.memory());
    birthdays = DriftBirthdaysRepository(database);
    drafts = DriftDraftsRepository(database);
    deliveryEvents = DriftDeliveryEventsRepository(database);
  });

  tearDown(() => database.close());

  Person person(String? timezone) => Person(
    id: 'p-1',
    name: 'Aiko',
    birthdayMonth: 3,
    birthdayDay: 5,
    timezone: timezone,
    createdAt: DateTime.utc(2026, 1, 1),
    updatedAt: DateTime.utc(2026, 1, 1),
  );

  // Instants at which the recipient's local day and a device's local day
  // differ for zones far ahead of / behind UTC.
  final references = <DateTime>[
    DateTime.utc(2026, 3, 1, 12),
    DateTime.utc(2026, 3, 4, 16),
    DateTime.utc(2026, 3, 4, 20),
    DateTime.utc(2026, 3, 5, 3),
  ];

  for (final timezone in [
    'Asia/Tokyo',
    'Pacific/Kiritimati',
    'Pacific/Pago_Pago',
    'America/Los_Angeles',
    'Asia/Kolkata',
    null,
  ]) {
    for (final reference in references) {
      test('two refreshes keep one occurrence and its draft '
          '(tz=$timezone, now=$reference)', () async {
        final service = BirthdayLifecycleService(now: () => reference);
        final people = [person(timezone)];

        await service.refresh(people: people, birthdaysRepository: birthdays);
        final created = (await birthdays.getBirthdays()).single;
        expect(created.date.month, 3);
        expect(created.date.day, 5);
        expect(created.cycleYear, 2026);

        await drafts.saveDraftAndUpdateBirthday(
          MessageDraft(
            id: created.id,
            birthdayId: created.id,
            personId: 'p-1',
            body: 'Happy birthday!',
            createdAt: reference,
            updatedAt: reference,
          ),
          BirthdayStatus.messageDrafted,
        );

        await service.refresh(people: people, birthdaysRepository: birthdays);
        await service.refresh(people: people, birthdaysRepository: birthdays);

        final all = await birthdays.getBirthdays();
        expect(all, hasLength(1));
        expect(all.single.id, created.id);
        expect(all.single.draftId, created.id);
        expect(all.single.status, BirthdayStatus.messageDrafted);
        final draft = await drafts.getDraftForBirthday(created.id);
        expect(draft?.body, 'Happy birthday!');
      });
    }
  }

  test(
    'Birthday.date round-trips through the repository as a calendar day',
    () async {
      final now = DateTime.utc(2026, 1, 1);
      // A zoned midnight and a UTC midnight both persist their own y/m/d.
      for (final (id, date) in [
        ('b-local', DateTime(2026, 3, 5)),
        ('b-utc', DateTime.utc(2026, 12, 31)),
        ('b-leap', DateTime(2028, 2, 29)),
      ]) {
        await birthdays.saveBirthday(
          Birthday(
            id: id,
            personId: 'p-$id',
            cycleYear: date.year,
            date: date,
            createdAt: now,
            updatedAt: now,
          ),
        );
        final read = (await birthdays.getBirthday(id))!;
        expect(
          (read.date.year, read.date.month, read.date.day),
          (date.year, date.month, date.day),
        );
      }
      final raw = await database
          .customSelect("SELECT date FROM birthdays WHERE id = 'b-utc'")
          .getSingle();
      expect(raw.read<String>('date'), '2026-12-31');
    },
  );

  test('daysUntil compares calendar days regardless of time of day', () {
    final birthday = Birthday(
      id: 'b',
      personId: 'p',
      cycleYear: 2026,
      date: DateTime(2026, 3, 5),
      createdAt: DateTime.utc(2026),
      updatedAt: DateTime.utc(2026),
    );
    expect(birthday.daysUntil(DateTime(2026, 3, 5, 23, 59)), 0);
    expect(birthday.daysUntil(DateTime(2026, 3, 4, 0, 1)), 1);
    expect(birthday.daysUntil(DateTime.utc(2026, 3, 6, 1)), -1);
  });

  test(
    'duplicate birthday merge remaps drafts, delivery history, and jobs',
    () async {
      // Simulate pre-unique-index/legacy data that can contain duplicates.
      await database.customStatement(
        'DROP INDEX IF EXISTS idx_birthdays_person_cycle_year_unique',
      );
      final old = Birthday(
        id: 'birthday-old',
        personId: 'p-1',
        cycleYear: 2026,
        date: DateTime(2026, 3, 5),
        status: BirthdayStatus.messageDrafted,
        draftId: 'draft-old',
        createdAt: DateTime.utc(2026, 1, 1),
        updatedAt: DateTime.utc(2026, 2, 1),
      );
      final newest = Birthday(
        id: 'birthday-new',
        personId: 'p-1',
        cycleYear: 2026,
        date: DateTime(2026, 3, 5),
        status: BirthdayStatus.upcoming,
        createdAt: DateTime.utc(2026, 1, 2),
        updatedAt: DateTime.utc(2026, 2, 2),
      );
      await birthdays.saveBirthday(old);
      await birthdays.saveBirthday(newest);

      await drafts.saveDraftAndUpdateBirthday(
        MessageDraft(
          id: 'draft-old',
          birthdayId: old.id,
          personId: 'p-1',
          body: 'Older saved message',
          createdAt: DateTime.utc(2026, 2, 1),
          updatedAt: DateTime.utc(2026, 2, 1),
        ),
        old.status,
      );
      await drafts.saveDraft(
        MessageDraft(
          id: 'draft-new',
          birthdayId: newest.id,
          personId: 'p-1',
          body: 'Newer unlinked saved message',
          createdAt: DateTime.utc(2026, 2, 2),
          updatedAt: DateTime.utc(2026, 2, 2),
        ),
      );
      await deliveryEvents.recordHandoff(
        birthdayId: old.id,
        draftId: 'draft-old',
        channel: DeliveryChannel.whatsapp,
        at: DateTime.utc(2026, 2, 1),
      );
      await database
          .into(database.jobs)
          .insert(
            db.JobsCompanion.insert(
              id: 'job-old',
              type: 'ai_generate',
              status: 'queued',
              subjectId: old.id,
              createdAt: DateTime.utc(2026, 2, 1),
            ),
          );

      await BirthdayLifecycleService(
        now: () => DateTime.utc(2026, 3, 1),
      ).refresh(people: [person(null)], birthdaysRepository: birthdays);

      final remaining = await birthdays.getBirthdays();
      expect(remaining, hasLength(1));
      expect(remaining.single.id, newest.id);
      expect(remaining.single.draftId, 'draft-old');
      expect(remaining.single.status, BirthdayStatus.messageDrafted);
      expect((await drafts.getDraft('draft-old'))?.birthdayId, newest.id);
      expect((await drafts.getDraft('draft-new'))?.birthdayId, newest.id);
      expect(
        await deliveryEvents.latestHandoffForBirthday(newest.id),
        isNotNull,
      );
      expect(await deliveryEvents.latestHandoffForBirthday(old.id), isNull);
      final job = await (database.select(
        database.jobs,
      )..where((row) => row.id.equals('job-old'))).getSingle();
      expect(job.subjectId, newest.id);
    },
  );

  test(
    'different cycle years keep their own birthday and linked draft',
    () async {
      final previousCycle = Birthday(
        id: 'birthday-2025',
        personId: 'p-1',
        cycleYear: 2025,
        date: DateTime(2025, 3, 5),
        status: BirthdayStatus.messageDrafted,
        draftId: 'draft-2025',
        createdAt: DateTime.utc(2025, 1, 1),
        updatedAt: DateTime.utc(2025, 2, 1),
      );
      final currentCycle = Birthday(
        id: 'birthday-2026',
        personId: 'p-1',
        cycleYear: 2026,
        date: DateTime(2026, 3, 5),
        status: BirthdayStatus.upcoming,
        createdAt: DateTime.utc(2026, 1, 1),
        updatedAt: DateTime.utc(2026, 2, 1),
      );
      await birthdays.saveBirthday(previousCycle);
      await birthdays.saveBirthday(currentCycle);
      await drafts.saveDraftAndUpdateBirthday(
        MessageDraft(
          id: 'draft-2025',
          birthdayId: previousCycle.id,
          personId: 'p-1',
          body: 'Prior cycle message',
          createdAt: DateTime.utc(2025, 2, 1),
          updatedAt: DateTime.utc(2025, 2, 1),
        ),
        previousCycle.status,
      );

      await BirthdayLifecycleService(
        now: () => DateTime.utc(2026, 3, 1),
      ).refresh(people: [person(null)], birthdaysRepository: birthdays);

      expect(await birthdays.getBirthdays(), hasLength(2));
      final old = (await birthdays.getBirthday(previousCycle.id))!;
      final current = (await birthdays.getBirthday(currentCycle.id))!;
      expect(old.cycleYear, 2025);
      expect(old.draftId, 'draft-2025');
      expect(current.cycleYear, 2026);
      expect(current.draftId, isNull);
      expect(
        (await drafts.getDraft('draft-2025'))?.birthdayId,
        previousCycle.id,
      );
    },
  );

  test('toJson/fromJson keep the calendar day and accept legacy ISO dates', () {
    final birthday = Birthday(
      id: 'b',
      personId: 'p',
      cycleYear: 2026,
      date: DateTime(2026, 3, 5),
      createdAt: DateTime.utc(2026),
      updatedAt: DateTime.utc(2026),
    );
    final json = birthday.toJson();
    expect(json['date'], '2026-03-05');
    expect(Birthday.fromJson(json).date, DateTime(2026, 3, 5));
    json['date'] = '2026-03-05T00:00:00.000+09:00';
    expect(Birthday.fromJson(json).date, DateTime(2026, 3, 5));
    json['date'] = '2026-03-05T00:00:00.000';
    expect(Birthday.fromJson(json).date, DateTime(2026, 3, 5));
  });
}
