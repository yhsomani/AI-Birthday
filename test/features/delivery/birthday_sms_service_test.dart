import 'package:flutter_test/flutter_test.dart';

import 'package:ai_birthday/core/logging/app_logger.dart';
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
      );

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
        );

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
      );

      expect(sendTime.year, 2027);
      expect(sendTime.hour, 0);
      expect(sendTime.minute, 0);
      expect(sendTime.month == 2 || sendTime.month == 3, isTrue);
    });
  });

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

        expect(scheduled, isTrue);
        expect(gateway.scheduledJobs.length, 1);
        final job = gateway.scheduledJobs.first;
        expect(job.jobId, 'sms_b-100');
        expect(job.personId, 'p-eligible');
        expect(job.phoneNumber, '+15551234567');
        expect(job.message, draftApproved.body);
        expect(job.isApproved, isTrue);
      },
    );

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
      expect(gateway.cancelledJobs, contains('sms_b-100'));
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
        expect(gateway.cancelledJobs, contains('sms_b-100'));
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
      expect(gateway.cancelledJobs, contains('sms_b-100'));
    });

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
        expect(gateway.cancelledJobs, contains('sms_b-100'));
      },
    );
  });
}
