import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:ai_birthday/app/providers.dart' show credentialStorageProvider;
import 'package:ai_birthday/core/config/product_capabilities.dart';

import '../../../core/core_providers.dart';
import '../../../core/logging/app_logger.dart';
import '../../birthdays/domain/birthday_engine.dart';
import '../../birthdays/domain/models/birthday.dart';
import '../../message_studio/domain/models/message_draft.dart';
import '../../people/domain/models/person.dart';
import '../../people/domain/person_enums.dart';
import '../data/native_sms_gateway.dart';

String _newSmsScheduleToken() => const Uuid().v4();

/// Global automatic SMS settings.
class AutomaticSmsSettings {
  const AutomaticSmsSettings({
    this.enabled = false,
    this.isSupported = false,
    this.hasSmsPermission = false,
  });

  final bool enabled;
  final bool isSupported;
  final bool hasSmsPermission;

  AutomaticSmsSettings copyWith({
    bool? enabled,
    bool? isSupported,
    bool? hasSmsPermission,
  }) {
    return AutomaticSmsSettings(
      enabled: enabled ?? this.enabled,
      isSupported: isSupported ?? this.isSupported,
      hasSmsPermission: hasSmsPermission ?? this.hasSmsPermission,
    );
  }
}

/// Controller managing global automatic SMS preferences and permissions.
class AutomaticSmsSettingsController extends Notifier<AutomaticSmsSettings> {
  static const _storageKey = 'global_automatic_sms_enabled';

  @override
  AutomaticSmsSettings build() {
    _init();
    return const AutomaticSmsSettings();
  }

  Future<void> _init() async {
    final gateway = ref.read(nativeSmsGatewayProvider);
    final isSupported =
        ProductCapabilities.automaticSms && await gateway.isSupported();
    final hasPerm = isSupported ? await gateway.hasSmsPermission() : false;

    // Read stored preference
    final storage = ref.read(credentialStorageProvider);
    final rawPref = await storage.readString(_storageKey);
    if (!isSupported) {
      await _cancelAllSmsJobs(gateway);
      await storage.writeString(_storageKey, 'false');
      state = AutomaticSmsSettings(
        enabled: false,
        isSupported: false,
        hasSmsPermission: false,
      );
      return;
    }

    final enabled = (rawPref == 'true') && isSupported && hasPerm;

    state = AutomaticSmsSettings(
      enabled: enabled,
      isSupported: isSupported,
      hasSmsPermission: hasPerm,
    );
  }

  Future<void> setEnabled(bool enabled) async {
    final gateway = ref.read(nativeSmsGatewayProvider);
    final storage = ref.read(credentialStorageProvider);

    if (enabled) {
      if (!ProductCapabilities.automaticSms || !state.isSupported) {
        state = state.copyWith(enabled: false);
        await storage.writeString(_storageKey, 'false');
        await _cancelAllSmsJobs(gateway);
        return;
      }
      bool hasPerm = await gateway.hasSmsPermission();
      if (!hasPerm) {
        hasPerm = await gateway.requestSmsPermission();
      }
      if (!hasPerm) {
        state = state.copyWith(enabled: false, hasSmsPermission: false);
        await storage.writeString(_storageKey, 'false');
        return;
      }
      await storage.writeString(_storageKey, 'true');
      state = state.copyWith(enabled: true, hasSmsPermission: true);
    } else {
      await storage.writeString(_storageKey, 'false');
      await _cancelAllSmsJobs(gateway);
      state = state.copyWith(enabled: false);
    }
  }

  Future<void> refreshPermissions() async {
    final gateway = ref.read(nativeSmsGatewayProvider);
    final isSupported =
        ProductCapabilities.automaticSms && await gateway.isSupported();
    final hasPerm = isSupported ? await gateway.hasSmsPermission() : false;
    if (!isSupported) await _cancelAllSmsJobs(gateway);
    state = state.copyWith(isSupported: isSupported, hasSmsPermission: hasPerm);
  }

  Future<void> _cancelAllSmsJobs(NativeSmsGateway gateway) async {
    if (await gateway.cancelAllSmsJobs()) return;
    ref
        .read(loggerProvider)
        .warning(
          'sms_automation',
          'Could not persist cancellation of all SMS jobs',
        );
  }
}

final automaticSmsSettingsProvider =
    NotifierProvider<AutomaticSmsSettingsController, AutomaticSmsSettings>(
      AutomaticSmsSettingsController.new,
    );

final nativeSmsGatewayProvider = Provider<NativeSmsGateway>((ref) {
  return const AndroidNativeSmsGateway();
});

/// Application service orchestrating native automatic birthday SMS scheduling.
class BirthdaySmsService {
  BirthdaySmsService({
    required this.gateway,
    required this.logger,
    this.engine = const BirthdayEngine(),
    String Function()? scheduleTokenGenerator,
  }) : _scheduleTokenGenerator = scheduleTokenGenerator ?? _newSmsScheduleToken;

  final NativeSmsGateway gateway;
  final AppLogger logger;
  final BirthdayEngine engine;
  final String Function() _scheduleTokenGenerator;

  String _jobIdFor(Birthday birthday) =>
      'sms_${birthday.id}_${birthday.cycleYear}';

  Future<bool> _cancelPersonSmsJobs(
    String personId, {
    String? exceptJobId,
  }) async {
    final cancelled = await gateway.cancelSmsJobsForPerson(
      personId,
      exceptJobId: exceptJobId,
    );
    if (!cancelled) {
      logger.warning(
        'sms_automation',
        'Could not persist cancellation of pending SMS jobs for person',
        params: {'personId': personId},
      );
    }
    return cancelled;
  }

  /// Pure computation of midnight send timestamp for a given person and birthday.
  DateTime? computeSendTime({
    required Person person,
    required DateTime reference,
    String? fallbackTimezone,
  }) {
    final timezone = person.timezone ?? fallbackTimezone;
    final next = engine.computeNext(
      month: person.birthdayMonth!,
      day: person.birthdayDay!,
      birthYear: person.birthYear,
      timezoneName: timezone,
      leapDay: person.leapDayResolution,
      reference: reference,
    );
    if (next == null) return null;

    // Midnight at the very beginning of the birthday date (00:00:00)
    return DateTime(
      next.nextDate.year,
      next.nextDate.month,
      next.nextDate.day,
      0,
      0,
      0,
    );
  }

  /// Evaluates strict eligibility and synchronizes the scheduled SMS alarm with Android.
  Future<bool> reconcileRecipientSms({
    required Person person,
    required Birthday? birthday,
    required MessageDraft? draft,
    bool globalSmsEnabled = true,
    DateTime? reference,
    String? fallbackTimezone,
  }) async {
    final refDate = reference ?? DateTime.now();
    final isSupported =
        ProductCapabilities.automaticSms && await gateway.isSupported();
    if (!isSupported || !globalSmsEnabled) {
      await _cancelPersonSmsJobs(person.id);
      return false;
    }

    // Strict eligibility checklist
    final hasPhone =
        person.phoneNumber != null && person.phoneNumber!.trim().isNotEmpty;
    final isAutoSmsApproved =
        person.autoSendPolicy == AutoSendPolicy.automaticSmsApproved;
    final hasDraft = draft != null && draft.body.trim().isNotEmpty;
    final isDraftApproved = draft?.status == DraftStatus.approvedForAutoSms;
    final hasBirthday = person.hasBirthday && birthday != null;

    final hasResolvedLeapPolicy =
        !person.isLeapDayBirthday || person.leapDayResolution != null;
    final isEligible =
        hasResolvedLeapPolicy &&
        hasPhone &&
        isAutoSmsApproved &&
        hasDraft &&
        isDraftApproved &&
        hasBirthday;

    if (!isEligible) {
      await _cancelPersonSmsJobs(person.id);
      return false;
    }

    // Schedule for midnight 00:00
    final sendTime = computeSendTime(
      person: person,
      reference: refDate,
      fallbackTimezone: fallbackTimezone,
    );

    // A legacy unresolved leap-day or an elapsed occurrence cancels stale work.
    if (sendTime == null || sendTime.isBefore(refDate)) {
      await _cancelPersonSmsJobs(person.id);
      return false;
    }

    final jobId = _jobIdFor(birthday);
    final clearedObsoleteJobs = await _cancelPersonSmsJobs(
      person.id,
      exceptJobId: jobId,
    );
    if (!clearedObsoleteJobs) {
      return false;
    }

    final job = ScheduledSmsJob(
      jobId: jobId,
      personId: person.id,
      birthdayId: birthday.id,
      phoneNumber: person.phoneNumber!.trim(),
      message: draft.body.trim(),
      timestampMs: sendTime.millisecondsSinceEpoch,
      scheduleToken: _scheduleTokenGenerator(),
      isApproved: true,
      status: 'scheduled',
    );

    final scheduled = await gateway.scheduleSmsJob(job);
    logger.info(
      'sms_automation',
      'Scheduled automated SMS job',
      params: {
        'jobId': job.jobId,
        'personId': person.id,
        'sendTime': sendTime.toIso8601String(),
      },
    );
    return scheduled;
  }
}

final birthdaySmsServiceProvider = Provider<BirthdaySmsService>((ref) {
  return BirthdaySmsService(
    gateway: ref.watch(nativeSmsGatewayProvider),
    logger: ref.watch(loggerProvider),
  );
});
