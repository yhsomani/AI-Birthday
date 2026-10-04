import 'person_enums.dart';

/// A person (birthday recipient) as defined by SSOT §7.
///
/// Timestamps and [version] form the sync envelope. [importantFacts] are the
/// only recipient facts that may be presented to AI as factual context;
/// [notes] are untrusted data and never treated as instructions.
class Person {
  const Person({
    required this.id,
    required this.name,
    required this.birthdayMonth,
    required this.birthdayDay,
    this.birthYear,
    this.phoneNumber,
    this.email,
    this.relationship = '',
    this.relationshipCloseness = RelationshipCloseness.other,
    this.preferredLanguage = 'en',
    this.preferredTone = PreferredTone.warm,
    this.importantFacts = const [],
    this.notes,
    this.preferredDeliveryChannel = DeliveryChannel.none,
    this.timezone,
    this.autoPrepare = false,
    this.autoSendPolicy = AutoSendPolicy.manualOnly,
    required this.createdAt,
    required this.updatedAt,
    this.version = 1,
    this.deletedAt,
  });

  final String id;
  final String name;
  final int birthdayMonth;
  final int birthdayDay;
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
  final DateTime createdAt;
  final DateTime updatedAt;
  final int version;
  final DateTime? deletedAt;

  /// Copies this person applying the given updates, bumping [version].
  Person copyWith({
    String? id,
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
    DateTime? createdAt,
    DateTime? updatedAt,
    int? version,
    DateTime? deletedAt,
  }) {
    return Person(
      id: id ?? this.id,
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
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      version: version ?? this.version,
      deletedAt: deletedAt ?? this.deletedAt,
    );
  }
}
