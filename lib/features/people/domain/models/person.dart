/// Recipient model representing a person whose birthday is tracked (SSOT §7).
library;

import 'package:ai_birthday/features/delivery/domain/models/delivery_channel.dart';
import 'package:ai_birthday/features/people/domain/models/relationship.dart';
import 'package:ai_birthday/features/people/domain/models/tone.dart';

/// An individual recipient whose birthday and preferences are stored.
class Person {
  const Person({
    required this.id,
    required this.name,
    required this.birthdayMonth,
    required this.birthdayDay,
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
    required this.createdAt,
    required this.updatedAt,
    this.version = 1,
  });

  final String id;
  final String name;

  /// Month of birthday (1-12).
  final int birthdayMonth;

  /// Day of birthday (1-31).
  final int birthdayDay;

  /// Optional birth year (used for calculating milestone ages).
  final int? birthYear;

  /// Normalized phone number used for WhatsApp / SMS delivery.
  final String? phoneNumber;

  /// Optional contact email.
  final String? email;

  /// Relationship category (Family, Friend, Colleague, etc.).
  final RelationshipCategory relationship;

  /// Closeness degree (Close, Casual, Distant).
  final RelationshipCloseness relationshipCloseness;

  /// ISO 639-1 language code (e.g. 'en', 'es', 'hi').
  final String preferredLanguage;

  /// Desired message tone.
  final MessageTone preferredTone;

  /// Verified facts provided strictly by the user (SSOT §2, §7, §21).
  /// AI must NEVER invent facts beyond what is stored here.
  final List<String> importantFacts;

  /// User's private notes (treated as data, never prompt instructions).
  final String? notes;

  /// Preferred delivery channel.
  final DeliveryChannel preferredDeliveryChannel;

  /// Optional explicit timezone identifier (e.g., 'America/New_York').
  final String? timezone;

  /// Whether to automatically prepare draft messages before the birthday.
  final bool autoPrepare;

  final DateTime createdAt;
  final DateTime updatedAt;
  final int version;

  /// Returns true if this person's birthday is on Leap Day (Feb 29).
  bool get isLeapDayBirthday => birthdayMonth == 2 && birthdayDay == 29;

  Person copyWith({
    String? id,
    String? name,
    int? birthdayMonth,
    int? birthdayDay,
    int? birthYear,
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
    bool? autoPrepare,
    DateTime? createdAt,
    DateTime? updatedAt,
    int? version,
  }) {
    return Person(
      id: id ?? this.id,
      name: name ?? this.name,
      birthdayMonth: birthdayMonth ?? this.birthdayMonth,
      birthdayDay: birthdayDay ?? this.birthdayDay,
      birthYear: birthYear ?? this.birthYear,
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
      timezone: timezone ?? this.timezone,
      autoPrepare: autoPrepare ?? this.autoPrepare,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      version: version ?? this.version,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'birthdayMonth': birthdayMonth,
    'birthdayDay': birthdayDay,
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
    'createdAt': createdAt.toIso8601String(),
    'updatedAt': updatedAt.toIso8601String(),
    'version': version,
  };

  factory Person.fromJson(Map<String, dynamic> json) {
    return Person(
      id: json['id'] as String,
      name: json['name'] as String,
      birthdayMonth: json['birthdayMonth'] as int,
      birthdayDay: json['birthdayDay'] as int,
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
      preferredTone: MessageTone.fromString(json['preferredTone'] as String?),
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
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
      version: (json['version'] as int?) ?? 1,
    );
  }
}
