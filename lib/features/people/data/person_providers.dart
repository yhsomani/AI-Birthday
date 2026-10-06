import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/core_providers.dart';
import '../../reminders/application/reminder_providers.dart';
import '../../reminders/application/reminder_settings_controller.dart';
import '../application/person_service.dart';
import '../domain/person.dart';
import '../domain/person_input_validator.dart';
import 'person_repository.dart';

final peopleStoreProvider = Provider<PeopleStore>(
  (ref) => DriftPeopleStore(ref.watch(databaseProvider)),
);

final personServiceProvider = Provider<PersonService>(
  (ref) => PersonService(
    ref.watch(peopleStoreProvider),
    const PersonInputValidator(),
    ref.watch(loggerProvider),
    onChanged: () async {
      if (WidgetsBinding.instance is! WidgetsFlutterBinding) return;
      try {
        final reminderService = ref.read(reminderServiceProvider);
        final settings = ref.read(reminderSettingsProvider);
        final people = await ref.read(peopleStoreProvider).getAll();
        await reminderService.sync(people: people, settings: settings);
      } catch (error, stackTrace) {
        ref
            .read(loggerProvider)
            .warning(
              'people',
              'Reminder schedule refresh failed after a contact change.',
              error: error,
              stackTrace: stackTrace,
            );
      }
    },
  ),
);

/// Live, sorted list of active recipients.
final personListProvider = StreamProvider<List<Person>>(
  (ref) => ref.watch(personServiceProvider).watchAll(),
);

final personByIdProvider = FutureProvider.family<Person?, String>(
  (ref, id) => ref.watch(personServiceProvider).getById(id),
);
