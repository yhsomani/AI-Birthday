import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/delivery/application/birthday_sms_service.dart';
import 'package:ai_birthday/features/delivery/data/native_sms_gateway.dart';
import 'package:ai_birthday/features/message_studio/domain/models/message_draft.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';
import 'package:ai_birthday/features/people/domain/person_enums.dart';

class FakeNativeSmsGateway implements NativeSmsGateway {
  bool supported = true;
  bool permission = true;
  final List<ScheduledSmsJob> scheduledJobs = [];
  final List<String> cancelledJobs = [];
  final List<String> cancelledPeople = [];
  final Map<String, String?> retainedJobByPerson = {};
  bool allCancelled = false;
  String attemptsLogJson = '[]';

  @override
  Future<bool> isSupported() async => supported;

  @override
  Future<bool> hasSmsPermission() async => permission;

  @override
  Future<bool> requestSmsPermission() async => permission;

  @override
  Future<bool> scheduleSmsJob(ScheduledSmsJob job) async {
    scheduledJobs.removeWhere((j) => j.jobId == job.jobId);
    scheduledJobs.add(job);
    return true;
  }

  @override
  Future<bool> cancelSmsJob(String jobId) async {
    cancelledJobs.add(jobId);
    scheduledJobs.removeWhere((j) => j.jobId == jobId);
    return true;
  }

  @override
  Future<bool> cancelAllSmsJobs() async {
    allCancelled = true;
    scheduledJobs.clear();
    return true;
  }

  @override
  Future<bool> cancelSmsJobsForPerson(
    String personId, {
    String? exceptJobId,
  }) async {
    cancelledPeople.add(personId);
    retainedJobByPerson[personId] = exceptJobId;
    scheduledJobs.removeWhere(
      (job) => job.personId == personId && job.jobId != exceptJobId,
    );
    return true;
  }

  @override
  Future<String> getSmsAttemptsLog() async => attemptsLogJson;
}

void main() {
  late FakeNativeSmsGateway gateway;
  late BirthdaySmsService service;

  setUp(() {
    gateway = FakeNativeSmsGateway();
    service = BirthdaySmsService(
      gateway: gateway,
      logger: ConsoleAppLogger(sink: (_) {}),
    );
  });

  group('BirthdaySmsService - Midnight 00:00 Send Time Calculation', () {
    test('computes exactly midnight 00:00 on the upcoming birthday', () {
      final person = Person(
        id: 'p1',
        name: 'Arjun',
        birthdayMonth: 10,
        birthdayDay: 25,
        phoneNumber: '+15551234567',
        autoSendPolicy: AutoSendPolicy.automaticSmsApproved,
        createdAt: DateTime(2026, 1, 1),
        updatedAt: DateTime(2026, 1, 1),
      );

      final reference = DateTime(2026, 10, 18, 14, 30);
      final sendTime = service.computeSendTime(
        person: person,
        reference: reference,
      )!;

      expect(sendTime.year, 2026);
      expect(sendTime.month, 10);
      expect(sendTime.day, 25);
      expect(sendTime.hour, 0);
      expect(sendTime.minute, 0);
      expect(sendTime.second, 0);
      expect(sendTime.millisecond, 0);
    });

    test(
      'advances to next year at midnight if birthday has already passed',
      () {
        final person = Person(
          id: 'p2',
          name: 'Sara',
          birthdayMonth: 5,
          birthdayDay: 10,
          phoneNumber: '+15559876543',
          autoSendPolicy: AutoSendPolicy.automaticSmsApproved,
          createdAt: DateTime(2026, 1, 1),
          updatedAt: DateTime(2026, 1, 1),
        );

        final reference = DateTime(2026, 10, 18, 14, 30);
        final sendTime = service.computeSendTime(
          person: person,
          reference: reference,
        )!;

        expect(sendTime.year, 2027);
        expect(sendTime.month, 5);
        expect(sendTime.day, 10);
        expect(sendTime.hour, 0);
        expect(sendTime.minute, 0);
      },
    );

    test('handles leap day (Feb 29) on non-leap years appropriately', () {
      final person = Person(
        id: 'p3',
        name: 'Leap Baby',
        birthdayMonth: 2,
        birthdayDay: 29,
        leapDayResolution: LeapDayResolution.mar1,
        phoneNumber: '+15550001111',
        autoSendPolicy: AutoSendPolicy.automaticSmsApproved,
        createdAt: DateTime(2024, 1, 1),
        updatedAt: DateTime(2024, 1, 1),
      );

      // 2027 is non-leap
      final reference = DateTime(2027, 1, 1);
      final sendTime = service.computeSendTime(
        person: person,
        reference: reference,
      )!;

      expect(sendTime.year, 2027);
      expect(sendTime.hour, 0);
      expect(sendTime.minute, 0);
      expect(sendTime.month, 3);
      expect(sendTime.day, 1);
    });

    test('returns null for an unresolved legacy leap-day policy', () {
      final person = Person(
        id: 'p-unresolved',
        name: 'Unresolved Leap',
        birthdayMonth: 2,
        birthdayDay: 29,
        createdAt: DateTime(2026, 1, 1),
        updatedAt: DateTime(2026, 1, 1),
      );

      expect(
        service.computeSendTime(
          person: person,
          reference: DateTime(2027, 1, 1),
        ),
        isNull,
      );
    });
  });

  test(
    'ScheduledSmsJob preserves its schedule token in the native payload',
    () {
      final job = ScheduledSmsJob(
        jobId: 'sms_b-100_2026',
        personId: 'p-eligible',
        birthdayId: 'b-100',
        phoneNumber: '+15551234567',
        message: 'Happy Birthday!',
        timestampMs: 1,
        scheduleToken: 'schedule-token-1',
        isApproved: true,
      );

      expect(job.toMap()['scheduleToken'], 'schedule-token-1');
      expect(
        ScheduledSmsJob.fromMap(job.toMap()).scheduleToken,
        'schedule-token-1',
      );
    },
  );

  group('BirthdaySmsService - Strict Eligibility & Reconciliation', () {
    final now = DateTime(2026, 10, 18, 10, 0);

    final personEligible = Person(
      id: 'p-eligible',
      name: 'Eligible Friend',
      birthdayMonth: 10,
      birthdayDay: 25,
      phoneNumber: '+15551234567',
      autoSendPolicy: AutoSendPolicy.automaticSmsApproved,
      createdAt: now,
      updatedAt: now,
    );

    final birthday = Birthday(
      id: 'b-100',
      personId: 'p-eligible',
      cycleYear: 2026,
      date: DateTime(2026, 10, 25),
      createdAt: now,
      updatedAt: now,
    );

    final draftApproved = MessageDraft(
      id: 'd-100',
      birthdayId: 'b-100',
      personId: 'p-eligible',
      body: 'Happy Birthday! Hope you have a fantastic day!',
      status: DraftStatus.approvedForAutoSms,
      createdAt: now,
      updatedAt: now,
    );

    test(
      'schedules SMS alarm when all opt-in and approval criteria are met',
      () async {
        final scheduled = await service.reconcileRecipientSms(
          person: personEligible,
          birthday: birthday,
          draft: draftApproved,
          globalSmsEnabled: true,
          reference: now,
        );

        expect(scheduled, isFalse);
        expect(gateway.scheduledJobs, isEmpty);
        expect(gateway.cancelledPeople, contains('p-eligible'));
      },
    );

    test('cancels SMS for an unresolved leap-day policy', () async {
      final unresolved = personEligible.copyWith(
        birthdayMonth: 2,
        birthdayDay: 29,
        clearLeapDayResolution: true,
      );

      final scheduled = await service.reconcileRecipientSms(
        person: unresolved,
        birthday: birthday,
        draft: draftApproved,
        globalSmsEnabled: true,
        reference: now,
      );

      expect(scheduled, isFalse);
      expect(gateway.scheduledJobs, isEmpty);
      expect(gateway.cancelledPeople, contains('p-eligible'));
    });

    test('rejects scheduling if person is manualOnly', () async {
      final personManual = personEligible.copyWith(
        autoSendPolicy: AutoSendPolicy.manualOnly,
      );

      final scheduled = await service.reconcileRecipientSms(
        person: personManual,
        birthday: birthday,
        draft: draftApproved,
        globalSmsEnabled: true,
        reference: now,
      );

      expect(scheduled, isFalse);
      expect(gateway.scheduledJobs, isEmpty);
      expect(gateway.cancelledPeople, contains('p-eligible'));
    });

    test(
      'rejects scheduling if draft is not approvedForAutoSms (e.g. still draft)',
      () async {
        final draftUnapproved = draftApproved.copyWith(
          status: DraftStatus.draft,
        );

        final scheduled = await service.reconcileRecipientSms(
          person: personEligible,
          birthday: birthday,
          draft: draftUnapproved,
          globalSmsEnabled: true,
          reference: now,
        );

        expect(scheduled, isFalse);
        expect(gateway.scheduledJobs, isEmpty);
        expect(gateway.cancelledPeople, contains('p-eligible'));
      },
    );

    test('rejects scheduling if phone number is missing', () async {
      final personNoPhone = Person(
        id: 'p-nophone',
        name: 'No Phone',
        birthdayMonth: 10,
        birthdayDay: 25,
        phoneNumber: null,
        autoSendPolicy: AutoSendPolicy.automaticSmsApproved,
        createdAt: now,
        updatedAt: now,
      );

      final scheduled = await service.reconcileRecipientSms(
        person: personNoPhone,
        birthday: birthday,
        draft: draftApproved,
        globalSmsEnabled: true,
        reference: now,
      );

      expect(scheduled, isFalse);
      expect(gateway.scheduledJobs, isEmpty);
    });

    test('cancels scheduled alarm if globalSmsEnabled is false', () async {
      final scheduled = await service.reconcileRecipientSms(
        person: personEligible,
        birthday: birthday,
        draft: draftApproved,
        globalSmsEnabled: false,
        reference: now,
      );

      expect(scheduled, isFalse);
      expect(gateway.scheduledJobs, isEmpty);
      expect(gateway.cancelledPeople, contains('p-eligible'));
    });

    test(
      'cancels pending schedules for a person across birthday cycles',
      () async {
        gateway.scheduledJobs.add(
          ScheduledSmsJob(
            jobId: 'sms_b-100_2026',
            personId: personEligible.id,
            birthdayId: birthday.id,
            phoneNumber: personEligible.phoneNumber!,
            message: draftApproved.body,
            timestampMs: now.millisecondsSinceEpoch,
            scheduleToken: 'stale-cycle-token',
            isApproved: true,
          ),
        );

        await service.reconcileRecipientSms(
          person: personEligible,
          birthday: birthday.copyWith(cycleYear: 2027),
          draft: draftApproved,
          globalSmsEnabled: false,
          reference: now,
        );

        expect(gateway.cancelledPeople, contains('p-eligible'));
        expect(gateway.retainedJobByPerson['p-eligible'], isNull);
        expect(gateway.scheduledJobs, isEmpty);
      },
    );

    test(
      'cancels scheduled alarm if platform does not support SMS (e.g. Play build)',
      () async {
        gateway.supported = false;

        final scheduled = await service.reconcileRecipientSms(
          person: personEligible,
          birthday: birthday,
          draft: draftApproved,
          globalSmsEnabled: true,
          reference: now,
        );

        expect(scheduled, isFalse);
        expect(gateway.scheduledJobs, isEmpty);
        expect(gateway.cancelledPeople, contains('p-eligible'));
      },
    );

    test(
      'keeps direct SMS gated even when consent and approval are present',
      () async {
        final scheduled = await service.reconcileRecipientSms(
          person: personEligible,
          birthday: birthday,
          draft: draftApproved,
          globalSmsEnabled: true,
          reference: now,
        );

        expect(scheduled, isFalse);
        expect(gateway.scheduledJobs, isEmpty);
        expect(gateway.cancelledPeople, contains('p-eligible'));
      },
    );
  });
}
