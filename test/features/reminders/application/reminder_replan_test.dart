/// Reminder re-plan guarantees (audit A1-002, A1-003, A1-010).
library;

import 'package:clock/clock.dart';
import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/core_providers.dart';
import 'package:ai_birthday/core/database/app_database.dart' as db;
import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/people/application/person_service.dart';
import 'package:ai_birthday/features/people/data/person_providers.dart'
    show resyncReminderSchedule;
import 'package:ai_birthday/features/people/data/person_repository.dart';
import 'package:ai_birthday/features/people/domain/person_input.dart';
import 'package:ai_birthday/features/people/domain/person_input_validator.dart';
import 'package:ai_birthday/features/reminders/application/notification_scheduler_gateway.dart';
import 'package:ai_birthday/features/reminders/application/reminder_bootstrap.dart';
import 'package:ai_birthday/features/reminders/application/reminder_plan_stamp.dart';
import 'package:ai_birthday/features/reminders/application/reminder_providers.dart';
import 'package:ai_birthday/features/reminders/application/reminder_settings_controller.dart';
import 'package:ai_birthday/features/reminders/domain/reminder_schedule.dart';

class _Gateway implements NotificationSchedulerGateway {
  final List<ReminderPlan> applied = [];
  int cancelAllCalls = 0;
  bool applyResult = true;
  bool applyThrows = false;

  @override
  Future<bool> apply(ReminderPlan plan, {bool hideNames = false}) async {
    if (applyThrows) throw StateError('channel down');
    applied.add(plan);
    return applyResult;
  }

  @override
  Future<bool> cancelAll() async {
    cancelAllCalls++;
    return true;
  }

  @override
  Future<bool> hasPermission() async => true;
  @override
  Future<bool> requestPermission() async => true;
  @override
  Future<bool> hasExactAlarmPermission() async => true;
  @override
  Future<bool> requestExactAlarmPermission() async => true;
  @override
  Future<bool> sendTestNotification({
    required String title,
    required String body,
  }) async => true;
  @override
  Future<String?> getInitialNotificationPersonId() async => null;
  @override
  void setNotificationOpenedHandler(void Function(String personId) onOpened) {}
}

void background(WidgetTester tester) {
  for (final state in [
    AppLifecycleState.inactive,
    AppLifecycleState.hidden,
    AppLifecycleState.paused,
  ]) {
    tester.binding.handleAppLifecycleStateChanged(state);
  }
}

void foreground(WidgetTester tester) {
  for (final state in [
    AppLifecycleState.hidden,
    AppLifecycleState.inactive,
    AppLifecycleState.resumed,
  ]) {
    tester.binding.handleAppLifecycleStateChanged(state);
  }
}

void main() {
  late db.AppDatabase database;
  late _Gateway gateway;
  late RecordingLogger logger;
  late ProviderContainer container;

  final day1 = DateTime(2026, 3, 1, 9);
  final day2 = DateTime(2026, 3, 2, 9);

  ProviderContainer newContainer() {
    final c = ProviderContainer(
      overrides: [
        databaseProvider.overrideWithValue(database),
        notificationSchedulerGatewayProvider.overrideWithValue(gateway),
        loggerProvider.overrideWithValue(logger),
      ],
    );
    addTearDown(c.dispose);
    return c;
  }

  Future<void> seedSettings({required bool enabled}) => database
      .into(database.reminderSettingsEntries)
      .insertOnConflictUpdate(
        db.ReminderSettingsEntriesCompanion(
          key: const Value('default'),
          enabled: Value(enabled),
          kinds: const Value('birthday,ready'),
          quietHoursStartMinutes: const Value(0),
          quietHoursEndMinutes: const Value(0),
        ),
      );

  Future<void> seedPerson() async {
    final service = PersonService(
      DriftPeopleStore(database),
      const PersonInputValidator(),
      const NoopLogger(),
      now: () => day1,
    );
    await service.create(
      const PersonDraft(name: 'Ana', birthdayMonth: 3, birthdayDay: 20),
    );
  }

  setUp(() {
    database = db.AppDatabase(NativeDatabase.memory());
    gateway = _Gateway();
    logger = RecordingLogger();
    container = newContainer();
  });

  tearDown(() async => database.close());

  Future<bool> replan({
    DateTime? at,
    String reason = 'startup',
    bool onlyIfStale = true,
    bool cancelWhenDisabled = false,
    ProviderContainer? using,
  }) {
    final c = using ?? container;
    return withClock(
      Clock.fixed(at ?? day1),
      () => c
          .read(reminderSettingsProvider.notifier)
          .replan(
            reason: reason,
            onlyIfStale: onlyIfStale,
            cancelWhenDisabled: cancelWhenDisabled,
          ),
    );
  }

  group('A1-003: never plan before the settings are loaded', () {
    test('immediate re-plan uses the saved (enabled) settings', () async {
      await seedSettings(enabled: true);
      await seedPerson();

      // No awaiting between provider creation and the call: the load is
      // still in flight, which used to resolve to disabled defaults.
      final ok = await replan(onlyIfStale: false);

      expect(ok, isTrue);
      expect(gateway.cancelAllCalls, 0);
      expect(gateway.applied, hasLength(1));
      expect(gateway.applied.single.active, isNotEmpty);
    });

    test('person-change resync waits for the load and keeps alarms', () async {
      await seedSettings(enabled: true);
      await seedPerson();

      await withClock(
        Clock.fixed(day1),
        () => resyncReminderSchedule(container),
      );

      expect(gateway.cancelAllCalls, 0);
      expect(gateway.applied, hasLength(1));
    });

    test('unreadable settings never cancel or plan anything', () async {
      await seedSettings(enabled: true);
      await seedPerson();
      await database.customStatement('DROP TABLE reminder_settings_entries');

      final ok = await replan(onlyIfStale: false, cancelWhenDisabled: true);

      expect(ok, isFalse);
      expect(gateway.cancelAllCalls, 0);
      expect(gateway.applied, isEmpty);
      expect(container.read(reminderSettingsProvider).syncError, isNotNull);
    });

    test(
      'never-configured reminders do not touch device notifications',
      () async {
        await seedPerson();

        final ok = await replan();

        expect(ok, isTrue);
        expect(gateway.cancelAllCalls, 0);
        expect(gateway.applied, isEmpty);
      },
    );

    test(
      'retries the initial settings read after the database recovers',
      () async {
        await seedSettings(enabled: true);
        await seedPerson();
        await database.customStatement('DROP TABLE reminder_settings_entries');
        final retryable = newContainer();
        final notifier = retryable.read(reminderSettingsProvider.notifier);
        expect(await notifier.ensureLoaded(), isFalse);

        await database.customStatement('''
          CREATE TABLE reminder_settings_entries (
            key TEXT NOT NULL PRIMARY KEY,
            enabled INTEGER NOT NULL DEFAULT 0,
            kinds TEXT NOT NULL,
            quiet_hours_start_minutes INTEGER NOT NULL,
            quiet_hours_end_minutes INTEGER NOT NULL
          )
        ''');
        await seedSettings(enabled: true);

        expect(await replan(onlyIfStale: false, using: retryable), isTrue);
        expect(gateway.applied, hasLength(1));
      },
    );

    test(
      'turning reminders off after an enabled plan cancels exactly once',
      () async {
        await seedSettings(enabled: true);
        await seedPerson();
        await replan(onlyIfStale: false);
        expect(gateway.applied, hasLength(1));

        await seedSettings(enabled: false);
        final fresh = newContainer();
        final ok = await replan(at: day2, using: fresh);

        expect(ok, isTrue);
        expect(gateway.cancelAllCalls, 1);
        // Once cancelled, later start-ups leave device notifications alone.
        await replan(at: DateTime(2026, 3, 3, 9), using: newContainer());
        expect(gateway.cancelAllCalls, 1);
      },
    );
  });

  group('A1-002: re-plan as time passes', () {
    test('cold start rebuilds even with a fresh same-day plan stamp', () async {
      await seedSettings(enabled: true);
      await seedPerson();
      await replan();
      expect(gateway.applied, hasLength(1));

      // Simulate a contact write whose follow-up replan was interrupted by
      // process death. The next cold start must not trust the same-day stamp.
      await (database.update(database.persons)
            ..where((row) => row.name.equals('Ana')))
          .write(const db.PersonsCompanion(birthdayDay: Value(21)));
      final coldStart = newContainer();
      await replan(using: coldStart, onlyIfStale: false);
      expect(gateway.applied, hasLength(2));
    });

    test(
      'plans on first start, then only when the day or zone changes',
      () async {
        await seedSettings(enabled: true);
        await seedPerson();

        await replan(); // first start
        expect(gateway.applied, hasLength(1));

        await replan(at: day1.add(const Duration(hours: 3)));
        expect(gateway.applied, hasLength(1), reason: 'same day: guarded');

        await replan(at: day2);
        expect(gateway.applied, hasLength(2), reason: 'new day: re-planned');
      },
    );

    test('a changed device time zone forces a re-plan the same day', () async {
      await seedSettings(enabled: true);
      await seedPerson();
      await replan();
      expect(gateway.applied, hasLength(1));

      final stamp = ReminderPlanStamp.at(day1, enabled: true, ok: true);
      await database
          .into(database.reminderSettingsEntries)
          .insertOnConflictUpdate(
            db.ReminderSettingsEntriesCompanion(
              key: const Value('plan_state'),
              enabled: const Value(true),
              kinds: Value(
                ReminderPlanStamp(
                  plannedAt: stamp.plannedAt,
                  timezoneKey: 'Elsewhere@-480',
                  dateKey: stamp.dateKey,
                  enabled: true,
                  ok: true,
                ).encode(),
              ),
              quietHoursStartMinutes: const Value(0),
              quietHoursEndMinutes: const Value(0),
            ),
          );

      await replan(using: newContainer());
      expect(gateway.applied, hasLength(2));
    });

    test('a clock moved backwards re-plans', () async {
      await seedSettings(enabled: true);
      await seedPerson();
      await replan(at: day2);
      await replan(at: day1);
      expect(gateway.applied, hasLength(2));
    });

    test('stamp survives a round trip and rejects garbage', () {
      final stamp = ReminderPlanStamp.at(day1, enabled: true, ok: true);
      final decoded = ReminderPlanStamp.decode(stamp.encode())!;
      expect(decoded.timezoneKey, stamp.timezoneKey);
      expect(decoded.dateKey, '2026-03-01');
      expect(decoded.enabled, isTrue);
      expect(decoded.isStaleAt(day1), isFalse);
      expect(ReminderPlanStamp.decode('nonsense'), isNull);
      expect(reminderPlanNeedsRefresh(null, day1), isTrue);
    });
  });

  group('A1-010: planning failures are visible and retried', () {
    test(
      'person save succeeds; failure is surfaced, logged and retried',
      () async {
        await seedSettings(enabled: true);
        final c = newContainer();
        gateway.applyThrows = true;

        await withClock(Clock.fixed(day1), () async {
          // Reaches resyncReminderSchedule only on a live binding, so call the
          // same hook directly after the write.
          final created =
              await PersonService(
                DriftPeopleStore(database),
                const PersonInputValidator(),
                const NoopLogger(),
                now: () => day1,
              ).create(
                const PersonDraft(
                  name: 'Ana',
                  birthdayMonth: 3,
                  birthdayDay: 20,
                ),
              );
          await resyncReminderSchedule(c);
          expect(created.id, isNotEmpty);
        });

        expect(await DriftPeopleStore(database).getAll(), hasLength(1));
        expect(c.read(reminderSettingsProvider).syncError, contains('retried'));
        expect(
          logger.records.where((r) => r.category == 'reminders'),
          isNotEmpty,
        );

        // Channel recovers: next start-up retries even though the day is the same.
        gateway.applyThrows = false;
        final ok = await replan(using: newContainer());
        expect(ok, isTrue);
        expect(gateway.applied, hasLength(1));
      },
    );

    test('an unconfirmed schedule keeps a retry pending', () async {
      await seedSettings(enabled: true);
      await seedPerson();
      gateway.applyResult = false;

      expect(await replan(), isFalse);
      expect(container.read(reminderSettingsProvider).syncError, isNotNull);

      gateway.applyResult = true;
      final fresh = newContainer();
      expect(await replan(using: fresh), isTrue);
      expect(gateway.applied, hasLength(2));
      expect(fresh.read(reminderSettingsProvider).syncError, isNull);
    });
  });

  group('ReminderBootstrap', () {
    testWidgets(
      'reconciles at start-up and on every resume, not after dispose',
      (tester) async {
        final reasons = <String>[];
        var lifecycleRefreshes = 0;
        final bootstrap = ReminderBootstrap(
          reconcile: (reason) async {
            reasons.add(reason);
            return true;
          },
          logger: const NoopLogger(),
          onResume: () => lifecycleRefreshes++,
        );
        await tester.pump();
        expect(reasons, ['startup']);

        background(tester);
        foreground(tester);
        await tester.pump();
        expect(reasons, ['startup', 'resume']);
        expect(lifecycleRefreshes, 1);

        bootstrap.dispose();
        background(tester);
        foreground(tester);
        await tester.pump();
        expect(reasons, ['startup', 'resume']);
        expect(lifecycleRefreshes, 1);
      },
    );

    testWidgets('a throwing reconcile is contained and logged', (tester) async {
      final log = RecordingLogger();
      ReminderBootstrap(
        reconcile: (_) async => throw StateError('boom'),
        logger: log,
        observeLifecycle: false,
      );
      await tester.pump();
      expect(log.records.single.message, contains('threw'));
    });
  });
}
