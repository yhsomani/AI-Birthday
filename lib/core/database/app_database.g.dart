// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_database.dart';

// ignore_for_file: type=lint
class $PersonsTable extends Persons with TableInfo<$PersonsTable, Person> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $PersonsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _birthdayMonthMeta = const VerificationMeta(
    'birthdayMonth',
  );
  @override
  late final GeneratedColumn<int> birthdayMonth = GeneratedColumn<int>(
    'birthday_month',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _birthdayDayMeta = const VerificationMeta(
    'birthdayDay',
  );
  @override
  late final GeneratedColumn<int> birthdayDay = GeneratedColumn<int>(
    'birthday_day',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _birthYearMeta = const VerificationMeta(
    'birthYear',
  );
  @override
  late final GeneratedColumn<int> birthYear = GeneratedColumn<int>(
    'birth_year',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _phoneNumberMeta = const VerificationMeta(
    'phoneNumber',
  );
  @override
  late final GeneratedColumn<String> phoneNumber = GeneratedColumn<String>(
    'phone_number',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _emailMeta = const VerificationMeta('email');
  @override
  late final GeneratedColumn<String> email = GeneratedColumn<String>(
    'email',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _relationshipMeta = const VerificationMeta(
    'relationship',
  );
  @override
  late final GeneratedColumn<String> relationship = GeneratedColumn<String>(
    'relationship',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _relationshipClosenessMeta =
      const VerificationMeta('relationshipCloseness');
  @override
  late final GeneratedColumn<String> relationshipCloseness =
      GeneratedColumn<String>(
        'relationship_closeness',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _preferredLanguageMeta = const VerificationMeta(
    'preferredLanguage',
  );
  @override
  late final GeneratedColumn<String> preferredLanguage =
      GeneratedColumn<String>(
        'preferred_language',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _preferredToneMeta = const VerificationMeta(
    'preferredTone',
  );
  @override
  late final GeneratedColumn<String> preferredTone = GeneratedColumn<String>(
    'preferred_tone',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _importantFactsMeta = const VerificationMeta(
    'importantFacts',
  );
  @override
  late final GeneratedColumn<String> importantFacts = GeneratedColumn<String>(
    'important_facts',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _notesMeta = const VerificationMeta('notes');
  @override
  late final GeneratedColumn<String> notes = GeneratedColumn<String>(
    'notes',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _preferredDeliveryChannelMeta =
      const VerificationMeta('preferredDeliveryChannel');
  @override
  late final GeneratedColumn<String> preferredDeliveryChannel =
      GeneratedColumn<String>(
        'preferred_delivery_channel',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _timezoneMeta = const VerificationMeta(
    'timezone',
  );
  @override
  late final GeneratedColumn<String> timezone = GeneratedColumn<String>(
    'timezone',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _autoPrepareMeta = const VerificationMeta(
    'autoPrepare',
  );
  @override
  late final GeneratedColumn<bool> autoPrepare = GeneratedColumn<bool>(
    'auto_prepare',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("auto_prepare" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _autoSendPolicyMeta = const VerificationMeta(
    'autoSendPolicy',
  );
  @override
  late final GeneratedColumn<String> autoSendPolicy = GeneratedColumn<String>(
    'auto_send_policy',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _versionMeta = const VerificationMeta(
    'version',
  );
  @override
  late final GeneratedColumn<int> version = GeneratedColumn<int>(
    'version',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _deletedAtMeta = const VerificationMeta(
    'deletedAt',
  );
  @override
  late final GeneratedColumn<DateTime> deletedAt = GeneratedColumn<DateTime>(
    'deleted_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    name,
    birthdayMonth,
    birthdayDay,
    birthYear,
    phoneNumber,
    email,
    relationship,
    relationshipCloseness,
    preferredLanguage,
    preferredTone,
    importantFacts,
    notes,
    preferredDeliveryChannel,
    timezone,
    autoPrepare,
    autoSendPolicy,
    createdAt,
    updatedAt,
    version,
    deletedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'persons';
  @override
  VerificationContext validateIntegrity(
    Insertable<Person> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('birthday_month')) {
      context.handle(
        _birthdayMonthMeta,
        birthdayMonth.isAcceptableOrUnknown(
          data['birthday_month']!,
          _birthdayMonthMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_birthdayMonthMeta);
    }
    if (data.containsKey('birthday_day')) {
      context.handle(
        _birthdayDayMeta,
        birthdayDay.isAcceptableOrUnknown(
          data['birthday_day']!,
          _birthdayDayMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_birthdayDayMeta);
    }
    if (data.containsKey('birth_year')) {
      context.handle(
        _birthYearMeta,
        birthYear.isAcceptableOrUnknown(data['birth_year']!, _birthYearMeta),
      );
    }
    if (data.containsKey('phone_number')) {
      context.handle(
        _phoneNumberMeta,
        phoneNumber.isAcceptableOrUnknown(
          data['phone_number']!,
          _phoneNumberMeta,
        ),
      );
    }
    if (data.containsKey('email')) {
      context.handle(
        _emailMeta,
        email.isAcceptableOrUnknown(data['email']!, _emailMeta),
      );
    }
    if (data.containsKey('relationship')) {
      context.handle(
        _relationshipMeta,
        relationship.isAcceptableOrUnknown(
          data['relationship']!,
          _relationshipMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_relationshipMeta);
    }
    if (data.containsKey('relationship_closeness')) {
      context.handle(
        _relationshipClosenessMeta,
        relationshipCloseness.isAcceptableOrUnknown(
          data['relationship_closeness']!,
          _relationshipClosenessMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_relationshipClosenessMeta);
    }
    if (data.containsKey('preferred_language')) {
      context.handle(
        _preferredLanguageMeta,
        preferredLanguage.isAcceptableOrUnknown(
          data['preferred_language']!,
          _preferredLanguageMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_preferredLanguageMeta);
    }
    if (data.containsKey('preferred_tone')) {
      context.handle(
        _preferredToneMeta,
        preferredTone.isAcceptableOrUnknown(
          data['preferred_tone']!,
          _preferredToneMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_preferredToneMeta);
    }
    if (data.containsKey('important_facts')) {
      context.handle(
        _importantFactsMeta,
        importantFacts.isAcceptableOrUnknown(
          data['important_facts']!,
          _importantFactsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_importantFactsMeta);
    }
    if (data.containsKey('notes')) {
      context.handle(
        _notesMeta,
        notes.isAcceptableOrUnknown(data['notes']!, _notesMeta),
      );
    }
    if (data.containsKey('preferred_delivery_channel')) {
      context.handle(
        _preferredDeliveryChannelMeta,
        preferredDeliveryChannel.isAcceptableOrUnknown(
          data['preferred_delivery_channel']!,
          _preferredDeliveryChannelMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_preferredDeliveryChannelMeta);
    }
    if (data.containsKey('timezone')) {
      context.handle(
        _timezoneMeta,
        timezone.isAcceptableOrUnknown(data['timezone']!, _timezoneMeta),
      );
    }
    if (data.containsKey('auto_prepare')) {
      context.handle(
        _autoPrepareMeta,
        autoPrepare.isAcceptableOrUnknown(
          data['auto_prepare']!,
          _autoPrepareMeta,
        ),
      );
    }
    if (data.containsKey('auto_send_policy')) {
      context.handle(
        _autoSendPolicyMeta,
        autoSendPolicy.isAcceptableOrUnknown(
          data['auto_send_policy']!,
          _autoSendPolicyMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_autoSendPolicyMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_updatedAtMeta);
    }
    if (data.containsKey('version')) {
      context.handle(
        _versionMeta,
        version.isAcceptableOrUnknown(data['version']!, _versionMeta),
      );
    } else if (isInserting) {
      context.missing(_versionMeta);
    }
    if (data.containsKey('deleted_at')) {
      context.handle(
        _deletedAtMeta,
        deletedAt.isAcceptableOrUnknown(data['deleted_at']!, _deletedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Person map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Person(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      birthdayMonth: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}birthday_month'],
      )!,
      birthdayDay: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}birthday_day'],
      )!,
      birthYear: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}birth_year'],
      ),
      phoneNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}phone_number'],
      ),
      email: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}email'],
      ),
      relationship: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}relationship'],
      )!,
      relationshipCloseness: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}relationship_closeness'],
      )!,
      preferredLanguage: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}preferred_language'],
      )!,
      preferredTone: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}preferred_tone'],
      )!,
      importantFacts: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}important_facts'],
      )!,
      notes: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}notes'],
      ),
      preferredDeliveryChannel: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}preferred_delivery_channel'],
      )!,
      timezone: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}timezone'],
      ),
      autoPrepare: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}auto_prepare'],
      )!,
      autoSendPolicy: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}auto_send_policy'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
      version: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}version'],
      )!,
      deletedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}deleted_at'],
      ),
    );
  }

  @override
  $PersonsTable createAlias(String alias) {
    return $PersonsTable(attachedDatabase, alias);
  }
}

class Person extends DataClass implements Insertable<Person> {
  final String id;
  final String name;
  final int birthdayMonth;
  final int birthdayDay;
  final int? birthYear;
  final String? phoneNumber;
  final String? email;
  final String relationship;
  final String relationshipCloseness;
  final String preferredLanguage;
  final String preferredTone;
  final String importantFacts;
  final String? notes;
  final String preferredDeliveryChannel;
  final String? timezone;
  final bool autoPrepare;
  final String autoSendPolicy;
  final DateTime createdAt;
  final DateTime updatedAt;
  final int version;
  final DateTime? deletedAt;
  const Person({
    required this.id,
    required this.name,
    required this.birthdayMonth,
    required this.birthdayDay,
    this.birthYear,
    this.phoneNumber,
    this.email,
    required this.relationship,
    required this.relationshipCloseness,
    required this.preferredLanguage,
    required this.preferredTone,
    required this.importantFacts,
    this.notes,
    required this.preferredDeliveryChannel,
    this.timezone,
    required this.autoPrepare,
    required this.autoSendPolicy,
    required this.createdAt,
    required this.updatedAt,
    required this.version,
    this.deletedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['name'] = Variable<String>(name);
    map['birthday_month'] = Variable<int>(birthdayMonth);
    map['birthday_day'] = Variable<int>(birthdayDay);
    if (!nullToAbsent || birthYear != null) {
      map['birth_year'] = Variable<int>(birthYear);
    }
    if (!nullToAbsent || phoneNumber != null) {
      map['phone_number'] = Variable<String>(phoneNumber);
    }
    if (!nullToAbsent || email != null) {
      map['email'] = Variable<String>(email);
    }
    map['relationship'] = Variable<String>(relationship);
    map['relationship_closeness'] = Variable<String>(relationshipCloseness);
    map['preferred_language'] = Variable<String>(preferredLanguage);
    map['preferred_tone'] = Variable<String>(preferredTone);
    map['important_facts'] = Variable<String>(importantFacts);
    if (!nullToAbsent || notes != null) {
      map['notes'] = Variable<String>(notes);
    }
    map['preferred_delivery_channel'] = Variable<String>(
      preferredDeliveryChannel,
    );
    if (!nullToAbsent || timezone != null) {
      map['timezone'] = Variable<String>(timezone);
    }
    map['auto_prepare'] = Variable<bool>(autoPrepare);
    map['auto_send_policy'] = Variable<String>(autoSendPolicy);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['version'] = Variable<int>(version);
    if (!nullToAbsent || deletedAt != null) {
      map['deleted_at'] = Variable<DateTime>(deletedAt);
    }
    return map;
  }

  PersonsCompanion toCompanion(bool nullToAbsent) {
    return PersonsCompanion(
      id: Value(id),
      name: Value(name),
      birthdayMonth: Value(birthdayMonth),
      birthdayDay: Value(birthdayDay),
      birthYear: birthYear == null && nullToAbsent
          ? const Value.absent()
          : Value(birthYear),
      phoneNumber: phoneNumber == null && nullToAbsent
          ? const Value.absent()
          : Value(phoneNumber),
      email: email == null && nullToAbsent
          ? const Value.absent()
          : Value(email),
      relationship: Value(relationship),
      relationshipCloseness: Value(relationshipCloseness),
      preferredLanguage: Value(preferredLanguage),
      preferredTone: Value(preferredTone),
      importantFacts: Value(importantFacts),
      notes: notes == null && nullToAbsent
          ? const Value.absent()
          : Value(notes),
      preferredDeliveryChannel: Value(preferredDeliveryChannel),
      timezone: timezone == null && nullToAbsent
          ? const Value.absent()
          : Value(timezone),
      autoPrepare: Value(autoPrepare),
      autoSendPolicy: Value(autoSendPolicy),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
      version: Value(version),
      deletedAt: deletedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(deletedAt),
    );
  }

  factory Person.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Person(
      id: serializer.fromJson<String>(json['id']),
      name: serializer.fromJson<String>(json['name']),
      birthdayMonth: serializer.fromJson<int>(json['birthdayMonth']),
      birthdayDay: serializer.fromJson<int>(json['birthdayDay']),
      birthYear: serializer.fromJson<int?>(json['birthYear']),
      phoneNumber: serializer.fromJson<String?>(json['phoneNumber']),
      email: serializer.fromJson<String?>(json['email']),
      relationship: serializer.fromJson<String>(json['relationship']),
      relationshipCloseness: serializer.fromJson<String>(
        json['relationshipCloseness'],
      ),
      preferredLanguage: serializer.fromJson<String>(json['preferredLanguage']),
      preferredTone: serializer.fromJson<String>(json['preferredTone']),
      importantFacts: serializer.fromJson<String>(json['importantFacts']),
      notes: serializer.fromJson<String?>(json['notes']),
      preferredDeliveryChannel: serializer.fromJson<String>(
        json['preferredDeliveryChannel'],
      ),
      timezone: serializer.fromJson<String?>(json['timezone']),
      autoPrepare: serializer.fromJson<bool>(json['autoPrepare']),
      autoSendPolicy: serializer.fromJson<String>(json['autoSendPolicy']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      version: serializer.fromJson<int>(json['version']),
      deletedAt: serializer.fromJson<DateTime?>(json['deletedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'name': serializer.toJson<String>(name),
      'birthdayMonth': serializer.toJson<int>(birthdayMonth),
      'birthdayDay': serializer.toJson<int>(birthdayDay),
      'birthYear': serializer.toJson<int?>(birthYear),
      'phoneNumber': serializer.toJson<String?>(phoneNumber),
      'email': serializer.toJson<String?>(email),
      'relationship': serializer.toJson<String>(relationship),
      'relationshipCloseness': serializer.toJson<String>(relationshipCloseness),
      'preferredLanguage': serializer.toJson<String>(preferredLanguage),
      'preferredTone': serializer.toJson<String>(preferredTone),
      'importantFacts': serializer.toJson<String>(importantFacts),
      'notes': serializer.toJson<String?>(notes),
      'preferredDeliveryChannel': serializer.toJson<String>(
        preferredDeliveryChannel,
      ),
      'timezone': serializer.toJson<String?>(timezone),
      'autoPrepare': serializer.toJson<bool>(autoPrepare),
      'autoSendPolicy': serializer.toJson<String>(autoSendPolicy),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'version': serializer.toJson<int>(version),
      'deletedAt': serializer.toJson<DateTime?>(deletedAt),
    };
  }

  Person copyWith({
    String? id,
    String? name,
    int? birthdayMonth,
    int? birthdayDay,
    Value<int?> birthYear = const Value.absent(),
    Value<String?> phoneNumber = const Value.absent(),
    Value<String?> email = const Value.absent(),
    String? relationship,
    String? relationshipCloseness,
    String? preferredLanguage,
    String? preferredTone,
    String? importantFacts,
    Value<String?> notes = const Value.absent(),
    String? preferredDeliveryChannel,
    Value<String?> timezone = const Value.absent(),
    bool? autoPrepare,
    String? autoSendPolicy,
    DateTime? createdAt,
    DateTime? updatedAt,
    int? version,
    Value<DateTime?> deletedAt = const Value.absent(),
  }) => Person(
    id: id ?? this.id,
    name: name ?? this.name,
    birthdayMonth: birthdayMonth ?? this.birthdayMonth,
    birthdayDay: birthdayDay ?? this.birthdayDay,
    birthYear: birthYear.present ? birthYear.value : this.birthYear,
    phoneNumber: phoneNumber.present ? phoneNumber.value : this.phoneNumber,
    email: email.present ? email.value : this.email,
    relationship: relationship ?? this.relationship,
    relationshipCloseness: relationshipCloseness ?? this.relationshipCloseness,
    preferredLanguage: preferredLanguage ?? this.preferredLanguage,
    preferredTone: preferredTone ?? this.preferredTone,
    importantFacts: importantFacts ?? this.importantFacts,
    notes: notes.present ? notes.value : this.notes,
    preferredDeliveryChannel:
        preferredDeliveryChannel ?? this.preferredDeliveryChannel,
    timezone: timezone.present ? timezone.value : this.timezone,
    autoPrepare: autoPrepare ?? this.autoPrepare,
    autoSendPolicy: autoSendPolicy ?? this.autoSendPolicy,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    version: version ?? this.version,
    deletedAt: deletedAt.present ? deletedAt.value : this.deletedAt,
  );
  Person copyWithCompanion(PersonsCompanion data) {
    return Person(
      id: data.id.present ? data.id.value : this.id,
      name: data.name.present ? data.name.value : this.name,
      birthdayMonth: data.birthdayMonth.present
          ? data.birthdayMonth.value
          : this.birthdayMonth,
      birthdayDay: data.birthdayDay.present
          ? data.birthdayDay.value
          : this.birthdayDay,
      birthYear: data.birthYear.present ? data.birthYear.value : this.birthYear,
      phoneNumber: data.phoneNumber.present
          ? data.phoneNumber.value
          : this.phoneNumber,
      email: data.email.present ? data.email.value : this.email,
      relationship: data.relationship.present
          ? data.relationship.value
          : this.relationship,
      relationshipCloseness: data.relationshipCloseness.present
          ? data.relationshipCloseness.value
          : this.relationshipCloseness,
      preferredLanguage: data.preferredLanguage.present
          ? data.preferredLanguage.value
          : this.preferredLanguage,
      preferredTone: data.preferredTone.present
          ? data.preferredTone.value
          : this.preferredTone,
      importantFacts: data.importantFacts.present
          ? data.importantFacts.value
          : this.importantFacts,
      notes: data.notes.present ? data.notes.value : this.notes,
      preferredDeliveryChannel: data.preferredDeliveryChannel.present
          ? data.preferredDeliveryChannel.value
          : this.preferredDeliveryChannel,
      timezone: data.timezone.present ? data.timezone.value : this.timezone,
      autoPrepare: data.autoPrepare.present
          ? data.autoPrepare.value
          : this.autoPrepare,
      autoSendPolicy: data.autoSendPolicy.present
          ? data.autoSendPolicy.value
          : this.autoSendPolicy,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      version: data.version.present ? data.version.value : this.version,
      deletedAt: data.deletedAt.present ? data.deletedAt.value : this.deletedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Person(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('birthdayMonth: $birthdayMonth, ')
          ..write('birthdayDay: $birthdayDay, ')
          ..write('birthYear: $birthYear, ')
          ..write('phoneNumber: $phoneNumber, ')
          ..write('email: $email, ')
          ..write('relationship: $relationship, ')
          ..write('relationshipCloseness: $relationshipCloseness, ')
          ..write('preferredLanguage: $preferredLanguage, ')
          ..write('preferredTone: $preferredTone, ')
          ..write('importantFacts: $importantFacts, ')
          ..write('notes: $notes, ')
          ..write('preferredDeliveryChannel: $preferredDeliveryChannel, ')
          ..write('timezone: $timezone, ')
          ..write('autoPrepare: $autoPrepare, ')
          ..write('autoSendPolicy: $autoSendPolicy, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('version: $version, ')
          ..write('deletedAt: $deletedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hashAll([
    id,
    name,
    birthdayMonth,
    birthdayDay,
    birthYear,
    phoneNumber,
    email,
    relationship,
    relationshipCloseness,
    preferredLanguage,
    preferredTone,
    importantFacts,
    notes,
    preferredDeliveryChannel,
    timezone,
    autoPrepare,
    autoSendPolicy,
    createdAt,
    updatedAt,
    version,
    deletedAt,
  ]);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Person &&
          other.id == this.id &&
          other.name == this.name &&
          other.birthdayMonth == this.birthdayMonth &&
          other.birthdayDay == this.birthdayDay &&
          other.birthYear == this.birthYear &&
          other.phoneNumber == this.phoneNumber &&
          other.email == this.email &&
          other.relationship == this.relationship &&
          other.relationshipCloseness == this.relationshipCloseness &&
          other.preferredLanguage == this.preferredLanguage &&
          other.preferredTone == this.preferredTone &&
          other.importantFacts == this.importantFacts &&
          other.notes == this.notes &&
          other.preferredDeliveryChannel == this.preferredDeliveryChannel &&
          other.timezone == this.timezone &&
          other.autoPrepare == this.autoPrepare &&
          other.autoSendPolicy == this.autoSendPolicy &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.version == this.version &&
          other.deletedAt == this.deletedAt);
}

class PersonsCompanion extends UpdateCompanion<Person> {
  final Value<String> id;
  final Value<String> name;
  final Value<int> birthdayMonth;
  final Value<int> birthdayDay;
  final Value<int?> birthYear;
  final Value<String?> phoneNumber;
  final Value<String?> email;
  final Value<String> relationship;
  final Value<String> relationshipCloseness;
  final Value<String> preferredLanguage;
  final Value<String> preferredTone;
  final Value<String> importantFacts;
  final Value<String?> notes;
  final Value<String> preferredDeliveryChannel;
  final Value<String?> timezone;
  final Value<bool> autoPrepare;
  final Value<String> autoSendPolicy;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<int> version;
  final Value<DateTime?> deletedAt;
  final Value<int> rowid;
  const PersonsCompanion({
    this.id = const Value.absent(),
    this.name = const Value.absent(),
    this.birthdayMonth = const Value.absent(),
    this.birthdayDay = const Value.absent(),
    this.birthYear = const Value.absent(),
    this.phoneNumber = const Value.absent(),
    this.email = const Value.absent(),
    this.relationship = const Value.absent(),
    this.relationshipCloseness = const Value.absent(),
    this.preferredLanguage = const Value.absent(),
    this.preferredTone = const Value.absent(),
    this.importantFacts = const Value.absent(),
    this.notes = const Value.absent(),
    this.preferredDeliveryChannel = const Value.absent(),
    this.timezone = const Value.absent(),
    this.autoPrepare = const Value.absent(),
    this.autoSendPolicy = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.version = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  PersonsCompanion.insert({
    required String id,
    required String name,
    required int birthdayMonth,
    required int birthdayDay,
    this.birthYear = const Value.absent(),
    this.phoneNumber = const Value.absent(),
    this.email = const Value.absent(),
    required String relationship,
    required String relationshipCloseness,
    required String preferredLanguage,
    required String preferredTone,
    required String importantFacts,
    this.notes = const Value.absent(),
    required String preferredDeliveryChannel,
    this.timezone = const Value.absent(),
    this.autoPrepare = const Value.absent(),
    required String autoSendPolicy,
    required DateTime createdAt,
    required DateTime updatedAt,
    required int version,
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       name = Value(name),
       birthdayMonth = Value(birthdayMonth),
       birthdayDay = Value(birthdayDay),
       relationship = Value(relationship),
       relationshipCloseness = Value(relationshipCloseness),
       preferredLanguage = Value(preferredLanguage),
       preferredTone = Value(preferredTone),
       importantFacts = Value(importantFacts),
       preferredDeliveryChannel = Value(preferredDeliveryChannel),
       autoSendPolicy = Value(autoSendPolicy),
       createdAt = Value(createdAt),
       updatedAt = Value(updatedAt),
       version = Value(version);
  static Insertable<Person> custom({
    Expression<String>? id,
    Expression<String>? name,
    Expression<int>? birthdayMonth,
    Expression<int>? birthdayDay,
    Expression<int>? birthYear,
    Expression<String>? phoneNumber,
    Expression<String>? email,
    Expression<String>? relationship,
    Expression<String>? relationshipCloseness,
    Expression<String>? preferredLanguage,
    Expression<String>? preferredTone,
    Expression<String>? importantFacts,
    Expression<String>? notes,
    Expression<String>? preferredDeliveryChannel,
    Expression<String>? timezone,
    Expression<bool>? autoPrepare,
    Expression<String>? autoSendPolicy,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<int>? version,
    Expression<DateTime>? deletedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (name != null) 'name': name,
      if (birthdayMonth != null) 'birthday_month': birthdayMonth,
      if (birthdayDay != null) 'birthday_day': birthdayDay,
      if (birthYear != null) 'birth_year': birthYear,
      if (phoneNumber != null) 'phone_number': phoneNumber,
      if (email != null) 'email': email,
      if (relationship != null) 'relationship': relationship,
      if (relationshipCloseness != null)
        'relationship_closeness': relationshipCloseness,
      if (preferredLanguage != null) 'preferred_language': preferredLanguage,
      if (preferredTone != null) 'preferred_tone': preferredTone,
      if (importantFacts != null) 'important_facts': importantFacts,
      if (notes != null) 'notes': notes,
      if (preferredDeliveryChannel != null)
        'preferred_delivery_channel': preferredDeliveryChannel,
      if (timezone != null) 'timezone': timezone,
      if (autoPrepare != null) 'auto_prepare': autoPrepare,
      if (autoSendPolicy != null) 'auto_send_policy': autoSendPolicy,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (version != null) 'version': version,
      if (deletedAt != null) 'deleted_at': deletedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  PersonsCompanion copyWith({
    Value<String>? id,
    Value<String>? name,
    Value<int>? birthdayMonth,
    Value<int>? birthdayDay,
    Value<int?>? birthYear,
    Value<String?>? phoneNumber,
    Value<String?>? email,
    Value<String>? relationship,
    Value<String>? relationshipCloseness,
    Value<String>? preferredLanguage,
    Value<String>? preferredTone,
    Value<String>? importantFacts,
    Value<String?>? notes,
    Value<String>? preferredDeliveryChannel,
    Value<String?>? timezone,
    Value<bool>? autoPrepare,
    Value<String>? autoSendPolicy,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<int>? version,
    Value<DateTime?>? deletedAt,
    Value<int>? rowid,
  }) {
    return PersonsCompanion(
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
      autoSendPolicy: autoSendPolicy ?? this.autoSendPolicy,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      version: version ?? this.version,
      deletedAt: deletedAt ?? this.deletedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (birthdayMonth.present) {
      map['birthday_month'] = Variable<int>(birthdayMonth.value);
    }
    if (birthdayDay.present) {
      map['birthday_day'] = Variable<int>(birthdayDay.value);
    }
    if (birthYear.present) {
      map['birth_year'] = Variable<int>(birthYear.value);
    }
    if (phoneNumber.present) {
      map['phone_number'] = Variable<String>(phoneNumber.value);
    }
    if (email.present) {
      map['email'] = Variable<String>(email.value);
    }
    if (relationship.present) {
      map['relationship'] = Variable<String>(relationship.value);
    }
    if (relationshipCloseness.present) {
      map['relationship_closeness'] = Variable<String>(
        relationshipCloseness.value,
      );
    }
    if (preferredLanguage.present) {
      map['preferred_language'] = Variable<String>(preferredLanguage.value);
    }
    if (preferredTone.present) {
      map['preferred_tone'] = Variable<String>(preferredTone.value);
    }
    if (importantFacts.present) {
      map['important_facts'] = Variable<String>(importantFacts.value);
    }
    if (notes.present) {
      map['notes'] = Variable<String>(notes.value);
    }
    if (preferredDeliveryChannel.present) {
      map['preferred_delivery_channel'] = Variable<String>(
        preferredDeliveryChannel.value,
      );
    }
    if (timezone.present) {
      map['timezone'] = Variable<String>(timezone.value);
    }
    if (autoPrepare.present) {
      map['auto_prepare'] = Variable<bool>(autoPrepare.value);
    }
    if (autoSendPolicy.present) {
      map['auto_send_policy'] = Variable<String>(autoSendPolicy.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (version.present) {
      map['version'] = Variable<int>(version.value);
    }
    if (deletedAt.present) {
      map['deleted_at'] = Variable<DateTime>(deletedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('PersonsCompanion(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('birthdayMonth: $birthdayMonth, ')
          ..write('birthdayDay: $birthdayDay, ')
          ..write('birthYear: $birthYear, ')
          ..write('phoneNumber: $phoneNumber, ')
          ..write('email: $email, ')
          ..write('relationship: $relationship, ')
          ..write('relationshipCloseness: $relationshipCloseness, ')
          ..write('preferredLanguage: $preferredLanguage, ')
          ..write('preferredTone: $preferredTone, ')
          ..write('importantFacts: $importantFacts, ')
          ..write('notes: $notes, ')
          ..write('preferredDeliveryChannel: $preferredDeliveryChannel, ')
          ..write('timezone: $timezone, ')
          ..write('autoPrepare: $autoPrepare, ')
          ..write('autoSendPolicy: $autoSendPolicy, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('version: $version, ')
          ..write('deletedAt: $deletedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $PersonsTable persons = $PersonsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [persons];
}

typedef $$PersonsTableCreateCompanionBuilder = PersonsCompanion Function({
  required String id,
  required String name,
  required int birthdayMonth,
  required int birthdayDay,
  Value<int?> birthYear,
  Value<String?> phoneNumber,
  Value<String?> email,
  required String relationship,
  required String relationshipCloseness,
  required String preferredLanguage,
  required String preferredTone,
  required String importantFacts,
  Value<String?> notes,
  required String preferredDeliveryChannel,
  Value<String?> timezone,
  Value<bool> autoPrepare,
  required String autoSendPolicy,
  required DateTime createdAt,
  required DateTime updatedAt,
  required int version,
  Value<DateTime?> deletedAt,
  Value<int> rowid,
});
typedef $$PersonsTableUpdateCompanionBuilder = PersonsCompanion Function({
  Value<String> id,
  Value<String> name,
  Value<int> birthdayMonth,
  Value<int> birthdayDay,
  Value<int?> birthYear,
  Value<String?> phoneNumber,
  Value<String?> email,
  Value<String> relationship,
  Value<String> relationshipCloseness,
  Value<String> preferredLanguage,
  Value<String> preferredTone,
  Value<String> importantFacts,
  Value<String?> notes,
  Value<String> preferredDeliveryChannel,
  Value<String?> timezone,
  Value<bool> autoPrepare,
  Value<String> autoSendPolicy,
  Value<DateTime> createdAt,
  Value<DateTime> updatedAt,
  Value<int> version,
  Value<DateTime?> deletedAt,
  Value<int> rowid,
});

class $$PersonsTableFilterComposer
    extends Composer<_$AppDatabase, $PersonsTable> {
  $$PersonsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get birthdayMonth => $composableBuilder(
    column: $table.birthdayMonth,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get birthdayDay => $composableBuilder(
    column: $table.birthdayDay,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get birthYear => $composableBuilder(
    column: $table.birthYear,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get phoneNumber => $composableBuilder(
    column: $table.phoneNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get email => $composableBuilder(
    column: $table.email,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get relationship => $composableBuilder(
    column: $table.relationship,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get relationshipCloseness => $composableBuilder(
    column: $table.relationshipCloseness,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get preferredLanguage => $composableBuilder(
    column: $table.preferredLanguage,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get preferredTone => $composableBuilder(
    column: $table.preferredTone,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get importantFacts => $composableBuilder(
    column: $table.importantFacts,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get preferredDeliveryChannel => $composableBuilder(
    column: $table.preferredDeliveryChannel,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get timezone => $composableBuilder(
    column: $table.timezone,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get autoPrepare => $composableBuilder(
    column: $table.autoPrepare,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get autoSendPolicy => $composableBuilder(
    column: $table.autoSendPolicy,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get version => $composableBuilder(
    column: $table.version,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$PersonsTableOrderingComposer
    extends Composer<_$AppDatabase, $PersonsTable> {
  $$PersonsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get birthdayMonth => $composableBuilder(
    column: $table.birthdayMonth,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get birthdayDay => $composableBuilder(
    column: $table.birthdayDay,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get birthYear => $composableBuilder(
    column: $table.birthYear,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get phoneNumber => $composableBuilder(
    column: $table.phoneNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get email => $composableBuilder(
    column: $table.email,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get relationship => $composableBuilder(
    column: $table.relationship,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get relationshipCloseness => $composableBuilder(
    column: $table.relationshipCloseness,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get preferredLanguage => $composableBuilder(
    column: $table.preferredLanguage,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get preferredTone => $composableBuilder(
    column: $table.preferredTone,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get importantFacts => $composableBuilder(
    column: $table.importantFacts,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get preferredDeliveryChannel => $composableBuilder(
    column: $table.preferredDeliveryChannel,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get timezone => $composableBuilder(
    column: $table.timezone,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get autoPrepare => $composableBuilder(
    column: $table.autoPrepare,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get autoSendPolicy => $composableBuilder(
    column: $table.autoSendPolicy,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get version => $composableBuilder(
    column: $table.version,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$PersonsTableAnnotationComposer
    extends Composer<_$AppDatabase, $PersonsTable> {
  $$PersonsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<int> get birthdayMonth => $composableBuilder(
    column: $table.birthdayMonth,
    builder: (column) => column,
  );

  GeneratedColumn<int> get birthdayDay => $composableBuilder(
    column: $table.birthdayDay,
    builder: (column) => column,
  );

  GeneratedColumn<int> get birthYear =>
      $composableBuilder(column: $table.birthYear, builder: (column) => column);

  GeneratedColumn<String> get phoneNumber => $composableBuilder(
    column: $table.phoneNumber,
    builder: (column) => column,
  );

  GeneratedColumn<String> get email =>
      $composableBuilder(column: $table.email, builder: (column) => column);

  GeneratedColumn<String> get relationship => $composableBuilder(
    column: $table.relationship,
    builder: (column) => column,
  );

  GeneratedColumn<String> get relationshipCloseness => $composableBuilder(
    column: $table.relationshipCloseness,
    builder: (column) => column,
  );

  GeneratedColumn<String> get preferredLanguage => $composableBuilder(
    column: $table.preferredLanguage,
    builder: (column) => column,
  );

  GeneratedColumn<String> get preferredTone => $composableBuilder(
    column: $table.preferredTone,
    builder: (column) => column,
  );

  GeneratedColumn<String> get importantFacts => $composableBuilder(
    column: $table.importantFacts,
    builder: (column) => column,
  );

  GeneratedColumn<String> get notes =>
      $composableBuilder(column: $table.notes, builder: (column) => column);

  GeneratedColumn<String> get preferredDeliveryChannel => $composableBuilder(
    column: $table.preferredDeliveryChannel,
    builder: (column) => column,
  );

  GeneratedColumn<String> get timezone =>
      $composableBuilder(column: $table.timezone, builder: (column) => column);

  GeneratedColumn<bool> get autoPrepare => $composableBuilder(
    column: $table.autoPrepare,
    builder: (column) => column,
  );

  GeneratedColumn<String> get autoSendPolicy => $composableBuilder(
    column: $table.autoSendPolicy,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<int> get version =>
      $composableBuilder(column: $table.version, builder: (column) => column);

  GeneratedColumn<DateTime> get deletedAt =>
      $composableBuilder(column: $table.deletedAt, builder: (column) => column);
}

class $$PersonsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $PersonsTable,
          Person,
          $$PersonsTableFilterComposer,
          $$PersonsTableOrderingComposer,
          $$PersonsTableAnnotationComposer,
          $$PersonsTableCreateCompanionBuilder,
          $$PersonsTableUpdateCompanionBuilder,
          (Person, BaseReferences<_$AppDatabase, $PersonsTable, Person>),
          Person,
          PrefetchHooks Function()
        > {
  $$PersonsTableTableManager(_$AppDatabase db, $PersonsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$PersonsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$PersonsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$PersonsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<int> birthdayMonth = const Value.absent(),
                Value<int> birthdayDay = const Value.absent(),
                Value<int?> birthYear = const Value.absent(),
                Value<String?> phoneNumber = const Value.absent(),
                Value<String?> email = const Value.absent(),
                Value<String> relationship = const Value.absent(),
                Value<String> relationshipCloseness = const Value.absent(),
                Value<String> preferredLanguage = const Value.absent(),
                Value<String> preferredTone = const Value.absent(),
                Value<String> importantFacts = const Value.absent(),
                Value<String?> notes = const Value.absent(),
                Value<String> preferredDeliveryChannel = const Value.absent(),
                Value<String?> timezone = const Value.absent(),
                Value<bool> autoPrepare = const Value.absent(),
                Value<String> autoSendPolicy = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> version = const Value.absent(),
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => PersonsCompanion(
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
                createdAt: createdAt,
                updatedAt: updatedAt,
                version: version,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String name,
                required int birthdayMonth,
                required int birthdayDay,
                Value<int?> birthYear = const Value.absent(),
                Value<String?> phoneNumber = const Value.absent(),
                Value<String?> email = const Value.absent(),
                required String relationship,
                required String relationshipCloseness,
                required String preferredLanguage,
                required String preferredTone,
                required String importantFacts,
                Value<String?> notes = const Value.absent(),
                required String preferredDeliveryChannel,
                Value<String?> timezone = const Value.absent(),
                Value<bool> autoPrepare = const Value.absent(),
                required String autoSendPolicy,
                required DateTime createdAt,
                required DateTime updatedAt,
                required int version,
                Value<DateTime?> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => PersonsCompanion.insert(
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
                createdAt: createdAt,
                updatedAt: updatedAt,
                version: version,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$PersonsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $PersonsTable,
      Person,
      $$PersonsTableFilterComposer,
      $$PersonsTableOrderingComposer,
      $$PersonsTableAnnotationComposer,
      $$PersonsTableCreateCompanionBuilder,
      $$PersonsTableUpdateCompanionBuilder,
      (Person, BaseReferences<_$AppDatabase, $PersonsTable, Person>),
      Person,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$PersonsTableTableManager get persons =>
      $$PersonsTableTableManager(_db, _db.persons);
}
