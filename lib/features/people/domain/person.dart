/// Canonical recipient domain model for AI-Birthday.
library;

import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

/// A birthday recipient stored locally and optionally synced to the user's cloud account.
///
/// This is the single application/domain representation of a person. Persistence,
/// AI, reminders, delivery, and presentation layers should all depend on this model.
class Person {
  const Person({
    required this.id,
    required this.name,
    this.birthdayMonth,
    this.birthdayDay,
    this.birthYear,
    this.phoneNumber,
    this.email,
    this.relationship = RelationshipCategory.friend,
    this.relationshipCloseness = RelationshipCloseness.casual,
    this.preferredLanguage = 'en',
    this.preferredTone = MessageTone.warm,
    this.importantFacts = const [],
    this.notes,
    this.preferredDeliveryChannel = DeliveryChannel.whatsapp,
    this.timezone,
    this.autoPrepare = true,
    this.autoSendPolicy = AutoSendPolicy.manualOnly,
    required this.createdAt,
    required this.updatedAt,
    this.version = 1,
    this.deletedAt,
  });

  final String id;
  final String name;
  final int? birthdayMonth;
  final int? birthdayDay;
  final int? birthYear;
  final String? phoneNumber;
  final String? email;
  final RelationshipCategory relationship;
  final RelationshipCloseness relationshipCloseness;
  final String preferredLanguage;
  final MessageTone preferredTone;
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

  bool get hasBirthday =>
      birthdayMonth != null &&
      birthdayDay != null &&
      birthdayMonth! >= 1 &&
      birthdayMonth! <= 12 &&
      birthdayDay! >= 1 &&
      birthdayDay! <= 31;

  bool get isLeapDayBirthday => birthdayMonth == 2 && birthdayDay == 29;

  Person copyWith({
    String? id,
    String? name,
    int? birthdayMonth,
    int? birthdayDay,
    bool clearBirthday = false,
    int? birthYear,
    bool clearBirthYear = false,
    String? phoneNumber,
    String? email,
    RelationshipCategory? relationship,
    RelationshipCloseness? relationshipCloseness,
    String? preferredLanguage,
    MessageTone? preferredTone,
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
    bool clearDeletedAt = false,
  }) {
    return Person(
      id: id ?? this.id,
      name: name ?? this.name,
      birthdayMonth: clearBirthday ? null : (birthdayMonth ?? this.birthdayMonth),
      birthdayDay: clearBirthday ? null : (birthdayDay ?? this.birthdayDay),
      birthYear: clearBirthYear ? null : (birthYear ?? this.birthYear),
      phoneNumber: phoneNumber ?? this.phoneNumber,
      email: email ?? this.email,
      relationship: relationship ?? this.relationship,
      relationshipCloseness: relationshipCloseness ?? this.relationshipCloseness,
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
      deletedAt: clearDeletedAt ? null : (deletedAt ?? this.deletedAt),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        if (birthdayMonth != null) 'birthdayMonth': birthdayMonth,
        if (birthdayDay != null) 'birthdayDay': birthdayDay,
        if (birthYear != null) 'birthYear': birthYear,
        if (phoneNumber != null) 'phoneNumber': phoneNumber,
        if (email != null) 'email': email,
        'relationship': relationship.name,
        'relationshipCloseness': relationshipCloseness.name,
        'preferredLanguage': preferredLanguage,
        'preferredTone': preferredTone.name,
        'importantFacts': importantFacts,
        if (notes != null) 'notes': notes,
        'preferredDeliveryChannel': preferredDeliveryChannel.name,
        if (timezone != null) 'timezone': timezone,
        'autoPrepare': autoPrepare,
        'autoSendPolicy': autoSendPolicy.name,
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
        'version': version,
        if (deletedAt != null) 'deletedAt': deletedAt!.toIso8601String(),
      };

  factory Person.fromJson(Map<String, dynamic> json) {
    return Person(
      id: json['id'] as String,
      name: json['name'] as String,
      birthdayMonth: json['birthdayMonth'] as int?,
      birthdayDay: json['birthdayDay'] as int?,
      birthYear: json['birthYear'] as int?,
      phoneNumber: json['phoneNumber'] as String?,
      email: json['email'] as String?,
      relationship: RelationshipCategory.fromString(
        json['relationship'] as String?,
      ),
      relationshipCloseness: RelationshipCloseness.fromString(
        json['relationshipCloseness'] as String?,
      ),
      preferredLanguage: (json['preferredLanguage'] as String?) ?? 'en',
      preferredTone: MessageTone.fromString(
        json['preferredTone'] as String?,
      ),
      importantFacts:
          (json['importantFacts'] as List<dynamic>?)
                  ?.map((e) => e.toString())
                  .toList() ??
              const [],
      notes: json['notes'] as String?,
      preferredDeliveryChannel: DeliveryChannel.fromString(
        json['preferredDeliveryChannel'] as String?,
      ),
      timezone: json['timezone'] as String?,
      autoPrepare: (json['autoPrepare'] as bool?) ?? true,
      autoSendPolicy: AutoSendPolicy.fromString(
        json['autoSendPolicy'] as String?,
      ),
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
      version: (json['version'] as int?) ?? 1,
      deletedAt: json['deletedAt'] == null
          ? null
          : DateTime.parse(json['deletedAt'] as String),
    );
  }
}
