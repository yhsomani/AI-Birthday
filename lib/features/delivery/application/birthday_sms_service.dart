import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart' show credentialStorageProvider;
import '../../../core/core_providers.dart';
import '../../../core/logging/app_logger.dart';
import '../../birthdays/domain/birthday_engine.dart';
import '../../birthdays/domain/models/birthday.dart';
import '../../message_studio/domain/models/message_draft.dart';
import '../../people/domain/models/person.dart';
import '../../people/domain/person_enums.dart';
import '../data/native_sms_gateway.dart';

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
    final isSupported = await gateway.isSupported();
    final hasPerm = isSupported ? await gateway.hasSmsPermission() : false;

    // Read stored preference
    final storage = ref.read(credentialStorageProvider);
    final rawPref = await storage.readString(_storageKey);
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
      if (!state.isSupported) {
        state = state.copyWith(enabled: false);
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
      await gateway.cancelAllSmsJobs();
      state = state.copyWith(enabled: false);
    }
  }

  Future<void> refreshPermissions() async {
    final gateway = ref.read(nativeSmsGatewayProvider);
    final isSupported = await gateway.isSupported();
    final hasPerm = isSupported ? await gateway.hasSmsPermission() : false;
    state = state.copyWith(isSupported: isSupported, hasSmsPermission: hasPerm);
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
  });

  final NativeSmsGateway gateway;
  final AppLogger logger;
  final BirthdayEngine engine;

  /// Pure computation of midnight send timestamp for a given person and birthday.
  DateTime computeSendTime({
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
      reference: reference,
    );

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
    final isSupported = await gateway.isSupported();
    if (!isSupported || !globalSmsEnabled) {
      if (birthday != null) {
        await gateway.cancelSmsJob('sms_${birthday.id}');
      }
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

    final isEligible =
        hasPhone &&
        isAutoSmsApproved &&
        hasDraft &&
        isDraftApproved &&
        hasBirthday;

    if (!isEligible) {
      if (birthday != null) {
        await gateway.cancelSmsJob('sms_${birthday.id}');
      }
      return false;
    }

    // Schedule for midnight 00:00
    final sendTime = computeSendTime(
      person: person,
      reference: refDate,
      fallbackTimezone: fallbackTimezone,
    );

    // If the send time has already passed for this year's birthday, do not schedule
    if (sendTime.isBefore(refDate)) {
      await gateway.cancelSmsJob('sms_${birthday.id}');
      return false;
    }

    final job = ScheduledSmsJob(
      jobId: 'sms_${birthday.id}',
      personId: person.id,
      birthdayId: birthday.id,
      phoneNumber: person.phoneNumber!.trim(),
      message: draft.body.trim(),
      timestampMs: sendTime.millisecondsSinceEpoch,
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
