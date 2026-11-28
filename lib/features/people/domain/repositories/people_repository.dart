import 'package:ai_birthday/features/birthdays/domain/models/birthday.dart';
import 'package:ai_birthday/features/people/domain/models/person.dart';

abstract interface class PeopleRepository {
  Stream<List<Person>> watchActivePeople({bool includeDeleted = false});
  Future<List<Person>> listActivePeople({bool includeDeleted = false});
  Future<Person?> getPersonById(String id, {bool includeDeleted = false});
  Future<void> savePerson(Person person);
  Future<void> savePersonAndBirthday(Person person, Birthday? birthday);
  Future<void> saveImportBatch({
    required List<Person> people,
    required List<Birthday> birthdays,
  });
  Future<Birthday?> getBirthdayForPerson(String personId);
  Future<void> softDeletePerson(String personId, DateTime deletedAt);
  Future<void> restorePerson(Person person, Birthday? birthday);
  Future<int> purgeExpiredTombstones(DateTime olderThan);
}
