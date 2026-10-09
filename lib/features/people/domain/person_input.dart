import 'person.dart';
import 'person_enums.dart';

/// Editable recipient data captured by the add/edit forms (SSOT §7).
///
/// The sync envelope ([Person.id], timestamps, [Person.version]) is produced
/// on save, never entered by the user.
class PersonDraft {
  const PersonDraft({
    this.name = '',
    this.birthdayMonth,
    this.birthdayDay,
    this.birthYear,
    this.phoneNumber,
    this.email,
    this.relationship = '',
    this.relationshipCloseness = RelationshipCloseness.casual,
    this.preferredLanguage = 'en',
    this.preferredTone = PreferredTone.warm,
    this.importantFacts = const [],
    this.notes,
    this.preferredDeliveryChannel = DeliveryChannel.none,
    this.timezone,
    this.autoPrepare = false,
    this.autoSendPolicy = AutoSendPolicy.manualOnly,
  });

  final String name;
  final int? birthdayMonth;
  final int? birthdayDay;
  final int? birthYear;
  final String? phoneNumber;
  final String? email;
  final String relationship;
  final RelationshipCloseness relationshipCloseness;
  final String preferredLanguage;
  final PreferredTone preferredTone;
  final List<String> importantFacts;
  final String? notes;
  final DeliveryChannel preferredDeliveryChannel;
  final String? timezone;
  final bool autoPrepare;
  final AutoSendPolicy autoSendPolicy;

  PersonDraft copyWith({
    String? name,
    int? birthdayMonth,
    int? birthdayDay,
    int? birthYear,
    bool clearBirthYear = false,
    String? phoneNumber,
    String? email,
    String? relationship,
    RelationshipCloseness? relationshipCloseness,
    String? preferredLanguage,
    PreferredTone? preferredTone,
    List<String>? importantFacts,
    String? notes,
    DeliveryChannel? preferredDeliveryChannel,
    String? timezone,
    bool clearTimezone = false,
    bool? autoPrepare,
    AutoSendPolicy? autoSendPolicy,
  }) {
    return PersonDraft(
      name: name ?? this.name,
      birthdayMonth: birthdayMonth ?? this.birthdayMonth,
      birthdayDay: birthdayDay ?? this.birthdayDay,
      birthYear: clearBirthYear ? null : (birthYear ?? this.birthYear),
      phoneNumber: phoneNumber ?? this.phoneNumber,
      email: email ?? this.email,
      relationship: relationship ?? this.relationship,
      relationshipCloseness:
          relationshipCloseness ?? this.relationshipCloseness,
      preferredLanguage: preferredLanguage ?? this.preferredLanguage,
      preferredTone: preferredTone ?? this.preferredTone,
      importantFacts: importantFacts ?? this.importantFacts,
      notes: notes ?? this.notes,
      preferredDeliveryChannel:
          preferredDeliveryChannel ?? this.preferredDeliveryChannel,
      timezone: clearTimezone ? null : (timezone ?? this.timezone),
      autoPrepare: autoPrepare ?? this.autoPrepare,
      autoSendPolicy: autoSendPolicy ?? this.autoSendPolicy,
    );
  }

  /// Builder used by add flow. Edit flows should start from an existing
  /// [Person] via [fromPerson] and re-apply updated content.
  Person toPerson({required String id, required DateTime now}) {
    return Person(
      id: id,
      name: name,
      birthdayMonth: birthdayMonth,
      birthdayDay: birthdayDay,
      birthYear: birthYear,
      phoneNumber: phoneNumber,
      email: email,
      relationship: relationship,
      relationshipCloseness: relationshipCloseness,
      preferredLanguage: preferredLanguage,
      preferredTone: preferredTone,
      importantFacts: importantFacts,
      notes: notes,
      preferredDeliveryChannel: preferredDeliveryChannel,
      timezone: timezone,
      autoPrepare: autoPrepare,
      autoSendPolicy: autoSendPolicy,
      createdAt: now,
      updatedAt: now,
      version: 1,
    );
  }

  /// Builds the draft content of an edited person, preserving envelope state.
  static PersonDraft fromPerson(Person p) {
    return PersonDraft(
      name: p.name,
      birthdayMonth: p.birthdayMonth,
      birthdayDay: p.birthdayDay,
      birthYear: p.birthYear,
      phoneNumber: p.phoneNumber,
      email: p.email,
      relationship: p.relationship,
      relationshipCloseness: p.relationshipCloseness,
      preferredLanguage: p.preferredLanguage,
      preferredTone: p.preferredTone,
      importantFacts: p.importantFacts,
      notes: p.notes,
      preferredDeliveryChannel: p.preferredDeliveryChannel,
      timezone: p.timezone,
      autoPrepare: p.autoPrepare,
      autoSendPolicy: p.autoSendPolicy,
    );
  }

  /// Applies edited content to an existing entity, bumping [version] and
  /// [updatedAt] so the sync envelope stays monotonic.
  Person applyTo(Person p, {required DateTime now}) {
    return p.copyWith(
      name: name,
      birthdayMonth: birthdayMonth,
      birthdayDay: birthdayDay,
      birthYear: birthYear,
      clearBirthYear: birthYear == null,
      phoneNumber: phoneNumber,
      email: email,
      relationship: relationship,
      relationshipCloseness: relationshipCloseness,
      preferredLanguage: preferredLanguage,
      preferredTone: preferredTone,
      importantFacts: importantFacts,
      notes: notes,
      preferredDeliveryChannel: preferredDeliveryChannel,
      timezone: timezone,
      clearTimezone: timezone == null,
      autoPrepare: autoPrepare,
      autoSendPolicy: autoSendPolicy,
      updatedAt: now,
      version: p.version + 1,
    );
  }
}
