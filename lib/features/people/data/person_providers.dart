import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/product_capabilities.dart';
import '../../../core/core_providers.dart';
import '../../reminders/application/reminder_settings_controller.dart';
import '../application/person_service.dart';
import '../domain/person.dart';
import '../domain/person_input_validator.dart';
import '../../../app/providers.dart'
    show
        birthdaysRepositoryProvider,
        draftsRepositoryProvider,
        peopleRepositoryProvider;
import '../../delivery/application/birthday_sms_service.dart';

final personServiceProvider = Provider<PersonService>(
  (ref) => PersonService(
    ref.watch(peopleRepositoryProvider),
    const PersonInputValidator(),
    ref.watch(loggerProvider),
    onChanged: () async {
      if (WidgetsBinding.instance is! WidgetsFlutterBinding) return;
      await resyncReminderSchedule(ref.container);
    },
  ),
);

/// Refreshes the reminder plan after contact changes that bypass
/// [PersonService]'s onChanged hook (CSV / device-contacts imports, which
/// batch-write through the repositories). Failures are logged and exposed as
/// a retry-pending reminder state, never thrown — a person write must not be
/// blocked by schedule refresh (audit A1-003/A1-010).
Future<void> resyncReminderSchedule(ProviderContainer container) async {
  try {
    // Waits for the saved settings; failures are surfaced on the settings
    // state and retried at the next start / resume, never thrown here.
    await container
        .read(reminderSettingsProvider.notifier)
        .replan(reason: 'people_changed');
    final people = await container
        .read(peopleRepositoryProvider)
        .listActivePeople();

    // Reconcile automated SMS jobs only for a separately approved build.
    if (ProductCapabilities.automaticSms) {
      final smsSettings = container.read(automaticSmsSettingsProvider);
      if (smsSettings.enabled) {
        final smsService = container.read(birthdaySmsServiceProvider);
        final birthdaysRepo = container.read(birthdaysRepositoryProvider);
        final draftsRepo = container.read(draftsRepositoryProvider);
        for (final person in people) {
          try {
            final birthday = await birthdaysRepo.getBirthdayForPerson(
              person.id,
            );
            final draft = birthday?.draftId != null
                ? await draftsRepo.getDraft(birthday!.draftId!)
                : null;
            await smsService.reconcileRecipientSms(
              person: person,
              birthday: birthday,
              draft: draft,
            );
          } catch (error, stackTrace) {
            container
                .read(loggerProvider)
                .warning(
                  'people',
                  'Automatic SMS schedule reconciliation failed for a recipient.',
                  params: {'personId': person.id},
                  error: error,
                  stackTrace: stackTrace,
                );
          }
        }
      }
    }
  } catch (error, stackTrace) {
    container
        .read(loggerProvider)
        .warning(
          'people',
          'Contact schedule refresh failed after a contact change.',
          error: error,
          stackTrace: stackTrace,
        );
  }
}

/// Live, sorted list of active recipients.
final personListProvider = StreamProvider<List<Person>>(
  (ref) => ref.watch(personServiceProvider).watchAll(),
);

final personByIdProvider = FutureProvider.family<Person?, String>(
  (ref, id) => ref.watch(personServiceProvider).getById(id),
);
