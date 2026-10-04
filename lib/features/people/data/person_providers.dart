import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/core_providers.dart';
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
  ),
);

/// Live, sorted list of active recipients.
final personListProvider = StreamProvider<List<Person>>(
  (ref) => ref.watch(personServiceProvider).watchAll(),
);

final personByIdProvider = FutureProvider.family<Person?, String>(
  (ref, id) => ref.watch(personServiceProvider).getById(id),
);
