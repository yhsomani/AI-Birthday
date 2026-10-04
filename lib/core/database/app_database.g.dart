// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_database.dart';

// ignore_for_file: type=lint
class $PeopleEntriesTable extends PeopleEntries
    with TableInfo<$PeopleEntriesTable, PeopleEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $PeopleEntriesTable(this.attachedDatabase, [this._alias]);
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
    requiredDuringInsert: false,
    defaultValue: const Constant('other'),
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
        requiredDuringInsert: false,
        defaultValue: const Constant('casual'),
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
        requiredDuringInsert: false,
        defaultValue: const Constant('en'),
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
    requiredDuringInsert: false,
    defaultValue: const Constant('warm'),
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
    requiredDuringInsert: false,
    defaultValue: const Constant('[]'),
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
        requiredDuringInsert: false,
        defaultValue: const Constant('whatsapp'),
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
    defaultValue: const Constant(true),
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
    requiredDuringInsert: false,
    defaultValue: const Constant(1),
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
    createdAt,
    updatedAt,
    version,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'people_entries';
  @override
  VerificationContext validateIntegrity(
    Insertable<PeopleEntry> instance, {
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
    }
    if (data.containsKey('relationship_closeness')) {
      context.handle(
        _relationshipClosenessMeta,
        relationshipCloseness.isAcceptableOrUnknown(
          data['relationship_closeness']!,
          _relationshipClosenessMeta,
        ),
      );
    }
    if (data.containsKey('preferred_language')) {
      context.handle(
        _preferredLanguageMeta,
        preferredLanguage.isAcceptableOrUnknown(
          data['preferred_language']!,
          _preferredLanguageMeta,
        ),
      );
    }
    if (data.containsKey('preferred_tone')) {
      context.handle(
        _preferredToneMeta,
        preferredTone.isAcceptableOrUnknown(
          data['preferred_tone']!,
          _preferredToneMeta,
        ),
      );
    }
    if (data.containsKey('important_facts')) {
      context.handle(
        _importantFactsMeta,
        importantFacts.isAcceptableOrUnknown(
          data['important_facts']!,
          _importantFactsMeta,
        ),
      );
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
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  PeopleEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return PeopleEntry(
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
    );
  }

  @override
  $PeopleEntriesTable createAlias(String alias) {
    return $PeopleEntriesTable(attachedDatabase, alias);
  }
}

class PeopleEntry extends DataClass implements Insertable<PeopleEntry> {
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
  final DateTime createdAt;
  final DateTime updatedAt;
  final int version;
  const PeopleEntry({
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
    required this.createdAt,
    required this.updatedAt,
    required this.version,
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
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    map['version'] = Variable<int>(version);
    return map;
  }

  PeopleEntriesCompanion toCompanion(bool nullToAbsent) {
    return PeopleEntriesCompanion(
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
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
      version: Value(version),
    );
  }

  factory PeopleEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return PeopleEntry(
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
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
      version: serializer.fromJson<int>(json['version']),
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
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
      'version': serializer.toJson<int>(version),
    };
  }

  PeopleEntry copyWith({
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
    DateTime? createdAt,
    DateTime? updatedAt,
    int? version,
  }) => PeopleEntry(
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
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    version: version ?? this.version,
  );
  PeopleEntry copyWithCompanion(PeopleEntriesCompanion data) {
    return PeopleEntry(
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
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
      version: data.version.present ? data.version.value : this.version,
    );
  }

  @override
  String toString() {
    return (StringBuffer('PeopleEntry(')
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
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('version: $version')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
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
    createdAt,
    updatedAt,
    version,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is PeopleEntry &&
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
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt &&
          other.version == this.version);
}

class PeopleEntriesCompanion extends UpdateCompanion<PeopleEntry> {
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
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<int> version;
  final Value<int> rowid;
  const PeopleEntriesCompanion({
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
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.version = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  PeopleEntriesCompanion.insert({
    required String id,
    required String name,
    required int birthdayMonth,
    required int birthdayDay,
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
    required DateTime createdAt,
    required DateTime updatedAt,
    this.version = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       name = Value(name),
       birthdayMonth = Value(birthdayMonth),
       birthdayDay = Value(birthdayDay),
       createdAt = Value(createdAt),
       updatedAt = Value(updatedAt);
  static Insertable<PeopleEntry> custom({
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
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<int>? version,
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
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (version != null) 'version': version,
      if (rowid != null) 'rowid': rowid,
    });
  }

  PeopleEntriesCompanion copyWith({
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
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<int>? version,
    Value<int>? rowid,
  }) {
    return PeopleEntriesCompanion(
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
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (version.present) {
      map['version'] = Variable<int>(version.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('PeopleEntriesCompanion(')
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
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('version: $version, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $BirthdayEntriesTable extends BirthdayEntries
    with TableInfo<$BirthdayEntriesTable, BirthdayEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $BirthdayEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _personIdMeta = const VerificationMeta(
    'personId',
  );
  @override
  late final GeneratedColumn<String> personId = GeneratedColumn<String>(
    'person_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _cycleYearMeta = const VerificationMeta(
    'cycleYear',
  );
  @override
  late final GeneratedColumn<int> cycleYear = GeneratedColumn<int>(
    'cycle_year',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _dateMeta = const VerificationMeta('date');
  @override
  late final GeneratedColumn<DateTime> date = GeneratedColumn<DateTime>(
    'date',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('upcoming'),
  );
  static const VerificationMeta _draftIdMeta = const VerificationMeta(
    'draftId',
  );
  @override
  late final GeneratedColumn<String> draftId = GeneratedColumn<String>(
    'draft_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
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
  @override
  List<GeneratedColumn> get $columns => [
    id,
    personId,
    cycleYear,
    date,
    status,
    draftId,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'birthday_entries';
  @override
  VerificationContext validateIntegrity(
    Insertable<BirthdayEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('person_id')) {
      context.handle(
        _personIdMeta,
        personId.isAcceptableOrUnknown(data['person_id']!, _personIdMeta),
      );
    } else if (isInserting) {
      context.missing(_personIdMeta);
    }
    if (data.containsKey('cycle_year')) {
      context.handle(
        _cycleYearMeta,
        cycleYear.isAcceptableOrUnknown(data['cycle_year']!, _cycleYearMeta),
      );
    } else if (isInserting) {
      context.missing(_cycleYearMeta);
    }
    if (data.containsKey('date')) {
      context.handle(
        _dateMeta,
        date.isAcceptableOrUnknown(data['date']!, _dateMeta),
      );
    } else if (isInserting) {
      context.missing(_dateMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    }
    if (data.containsKey('draft_id')) {
      context.handle(
        _draftIdMeta,
        draftId.isAcceptableOrUnknown(data['draft_id']!, _draftIdMeta),
      );
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
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  BirthdayEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return BirthdayEntry(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      personId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}person_id'],
      )!,
      cycleYear: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}cycle_year'],
      )!,
      date: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}date'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      draftId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}draft_id'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $BirthdayEntriesTable createAlias(String alias) {
    return $BirthdayEntriesTable(attachedDatabase, alias);
  }
}

class BirthdayEntry extends DataClass implements Insertable<BirthdayEntry> {
  final String id;
  final String personId;
  final int cycleYear;
  final DateTime date;
  final String status;
  final String? draftId;
  final DateTime createdAt;
  final DateTime updatedAt;
  const BirthdayEntry({
    required this.id,
    required this.personId,
    required this.cycleYear,
    required this.date,
    required this.status,
    this.draftId,
    required this.createdAt,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['person_id'] = Variable<String>(personId);
    map['cycle_year'] = Variable<int>(cycleYear);
    map['date'] = Variable<DateTime>(date);
    map['status'] = Variable<String>(status);
    if (!nullToAbsent || draftId != null) {
      map['draft_id'] = Variable<String>(draftId);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  BirthdayEntriesCompanion toCompanion(bool nullToAbsent) {
    return BirthdayEntriesCompanion(
      id: Value(id),
      personId: Value(personId),
      cycleYear: Value(cycleYear),
      date: Value(date),
      status: Value(status),
      draftId: draftId == null && nullToAbsent
          ? const Value.absent()
          : Value(draftId),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
    );
  }

  factory BirthdayEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return BirthdayEntry(
      id: serializer.fromJson<String>(json['id']),
      personId: serializer.fromJson<String>(json['personId']),
      cycleYear: serializer.fromJson<int>(json['cycleYear']),
      date: serializer.fromJson<DateTime>(json['date']),
      status: serializer.fromJson<String>(json['status']),
      draftId: serializer.fromJson<String?>(json['draftId']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'personId': serializer.toJson<String>(personId),
      'cycleYear': serializer.toJson<int>(cycleYear),
      'date': serializer.toJson<DateTime>(date),
      'status': serializer.toJson<String>(status),
      'draftId': serializer.toJson<String?>(draftId),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  BirthdayEntry copyWith({
    String? id,
    String? personId,
    int? cycleYear,
    DateTime? date,
    String? status,
    Value<String?> draftId = const Value.absent(),
    DateTime? createdAt,
    DateTime? updatedAt,
  }) => BirthdayEntry(
    id: id ?? this.id,
    personId: personId ?? this.personId,
    cycleYear: cycleYear ?? this.cycleYear,
    date: date ?? this.date,
    status: status ?? this.status,
    draftId: draftId.present ? draftId.value : this.draftId,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  BirthdayEntry copyWithCompanion(BirthdayEntriesCompanion data) {
    return BirthdayEntry(
      id: data.id.present ? data.id.value : this.id,
      personId: data.personId.present ? data.personId.value : this.personId,
      cycleYear: data.cycleYear.present ? data.cycleYear.value : this.cycleYear,
      date: data.date.present ? data.date.value : this.date,
      status: data.status.present ? data.status.value : this.status,
      draftId: data.draftId.present ? data.draftId.value : this.draftId,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('BirthdayEntry(')
          ..write('id: $id, ')
          ..write('personId: $personId, ')
          ..write('cycleYear: $cycleYear, ')
          ..write('date: $date, ')
          ..write('status: $status, ')
          ..write('draftId: $draftId, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    personId,
    cycleYear,
    date,
    status,
    draftId,
    createdAt,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is BirthdayEntry &&
          other.id == this.id &&
          other.personId == this.personId &&
          other.cycleYear == this.cycleYear &&
          other.date == this.date &&
          other.status == this.status &&
          other.draftId == this.draftId &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class BirthdayEntriesCompanion extends UpdateCompanion<BirthdayEntry> {
  final Value<String> id;
  final Value<String> personId;
  final Value<int> cycleYear;
  final Value<DateTime> date;
  final Value<String> status;
  final Value<String?> draftId;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const BirthdayEntriesCompanion({
    this.id = const Value.absent(),
    this.personId = const Value.absent(),
    this.cycleYear = const Value.absent(),
    this.date = const Value.absent(),
    this.status = const Value.absent(),
    this.draftId = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  BirthdayEntriesCompanion.insert({
    required String id,
    required String personId,
    required int cycleYear,
    required DateTime date,
    this.status = const Value.absent(),
    this.draftId = const Value.absent(),
    required DateTime createdAt,
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       personId = Value(personId),
       cycleYear = Value(cycleYear),
       date = Value(date),
       createdAt = Value(createdAt),
       updatedAt = Value(updatedAt);
  static Insertable<BirthdayEntry> custom({
    Expression<String>? id,
    Expression<String>? personId,
    Expression<int>? cycleYear,
    Expression<DateTime>? date,
    Expression<String>? status,
    Expression<String>? draftId,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (personId != null) 'person_id': personId,
      if (cycleYear != null) 'cycle_year': cycleYear,
      if (date != null) 'date': date,
      if (status != null) 'status': status,
      if (draftId != null) 'draft_id': draftId,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  BirthdayEntriesCompanion copyWith({
    Value<String>? id,
    Value<String>? personId,
    Value<int>? cycleYear,
    Value<DateTime>? date,
    Value<String>? status,
    Value<String?>? draftId,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return BirthdayEntriesCompanion(
      id: id ?? this.id,
      personId: personId ?? this.personId,
      cycleYear: cycleYear ?? this.cycleYear,
      date: date ?? this.date,
      status: status ?? this.status,
      draftId: draftId ?? this.draftId,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (personId.present) {
      map['person_id'] = Variable<String>(personId.value);
    }
    if (cycleYear.present) {
      map['cycle_year'] = Variable<int>(cycleYear.value);
    }
    if (date.present) {
      map['date'] = Variable<DateTime>(date.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (draftId.present) {
      map['draft_id'] = Variable<String>(draftId.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('BirthdayEntriesCompanion(')
          ..write('id: $id, ')
          ..write('personId: $personId, ')
          ..write('cycleYear: $cycleYear, ')
          ..write('date: $date, ')
          ..write('status: $status, ')
          ..write('draftId: $draftId, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $MessageDraftEntriesTable extends MessageDraftEntries
    with TableInfo<$MessageDraftEntriesTable, MessageDraftEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $MessageDraftEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _birthdayIdMeta = const VerificationMeta(
    'birthdayId',
  );
  @override
  late final GeneratedColumn<String> birthdayId = GeneratedColumn<String>(
    'birthday_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _personIdMeta = const VerificationMeta(
    'personId',
  );
  @override
  late final GeneratedColumn<String> personId = GeneratedColumn<String>(
    'person_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _bodyMeta = const VerificationMeta('body');
  @override
  late final GeneratedColumn<String> body = GeneratedColumn<String>(
    'body',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _toneMeta = const VerificationMeta('tone');
  @override
  late final GeneratedColumn<String> tone = GeneratedColumn<String>(
    'tone',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _lengthMeta = const VerificationMeta('length');
  @override
  late final GeneratedColumn<String> length = GeneratedColumn<String>(
    'length',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('draft'),
  );
  static const VerificationMeta _providerTypeMeta = const VerificationMeta(
    'providerType',
  );
  @override
  late final GeneratedColumn<String> providerType = GeneratedColumn<String>(
    'provider_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('manual'),
  );
  static const VerificationMeta _variationIndexMeta = const VerificationMeta(
    'variationIndex',
  );
  @override
  late final GeneratedColumn<int> variationIndex = GeneratedColumn<int>(
    'variation_index',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
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
  @override
  List<GeneratedColumn> get $columns => [
    id,
    birthdayId,
    personId,
    body,
    tone,
    length,
    status,
    providerType,
    variationIndex,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'message_draft_entries';
  @override
  VerificationContext validateIntegrity(
    Insertable<MessageDraftEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('birthday_id')) {
      context.handle(
        _birthdayIdMeta,
        birthdayId.isAcceptableOrUnknown(data['birthday_id']!, _birthdayIdMeta),
      );
    } else if (isInserting) {
      context.missing(_birthdayIdMeta);
    }
    if (data.containsKey('person_id')) {
      context.handle(
        _personIdMeta,
        personId.isAcceptableOrUnknown(data['person_id']!, _personIdMeta),
      );
    } else if (isInserting) {
      context.missing(_personIdMeta);
    }
    if (data.containsKey('body')) {
      context.handle(
        _bodyMeta,
        body.isAcceptableOrUnknown(data['body']!, _bodyMeta),
      );
    } else if (isInserting) {
      context.missing(_bodyMeta);
    }
    if (data.containsKey('tone')) {
      context.handle(
        _toneMeta,
        tone.isAcceptableOrUnknown(data['tone']!, _toneMeta),
      );
    } else if (isInserting) {
      context.missing(_toneMeta);
    }
    if (data.containsKey('length')) {
      context.handle(
        _lengthMeta,
        length.isAcceptableOrUnknown(data['length']!, _lengthMeta),
      );
    } else if (isInserting) {
      context.missing(_lengthMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    }
    if (data.containsKey('provider_type')) {
      context.handle(
        _providerTypeMeta,
        providerType.isAcceptableOrUnknown(
          data['provider_type']!,
          _providerTypeMeta,
        ),
      );
    }
    if (data.containsKey('variation_index')) {
      context.handle(
        _variationIndexMeta,
        variationIndex.isAcceptableOrUnknown(
          data['variation_index']!,
          _variationIndexMeta,
        ),
      );
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
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  MessageDraftEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return MessageDraftEntry(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      birthdayId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}birthday_id'],
      )!,
      personId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}person_id'],
      )!,
      body: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}body'],
      )!,
      tone: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tone'],
      )!,
      length: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}length'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      providerType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}provider_type'],
      )!,
      variationIndex: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}variation_index'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      )!,
    );
  }

  @override
  $MessageDraftEntriesTable createAlias(String alias) {
    return $MessageDraftEntriesTable(attachedDatabase, alias);
  }
}

class MessageDraftEntry extends DataClass
    implements Insertable<MessageDraftEntry> {
  final String id;
  final String birthdayId;
  final String personId;
  final String body;
  final String tone;
  final String length;
  final String status;
  final String providerType;
  final int variationIndex;
  final DateTime createdAt;
  final DateTime updatedAt;
  const MessageDraftEntry({
    required this.id,
    required this.birthdayId,
    required this.personId,
    required this.body,
    required this.tone,
    required this.length,
    required this.status,
    required this.providerType,
    required this.variationIndex,
    required this.createdAt,
    required this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['birthday_id'] = Variable<String>(birthdayId);
    map['person_id'] = Variable<String>(personId);
    map['body'] = Variable<String>(body);
    map['tone'] = Variable<String>(tone);
    map['length'] = Variable<String>(length);
    map['status'] = Variable<String>(status);
    map['provider_type'] = Variable<String>(providerType);
    map['variation_index'] = Variable<int>(variationIndex);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['updated_at'] = Variable<DateTime>(updatedAt);
    return map;
  }

  MessageDraftEntriesCompanion toCompanion(bool nullToAbsent) {
    return MessageDraftEntriesCompanion(
      id: Value(id),
      birthdayId: Value(birthdayId),
      personId: Value(personId),
      body: Value(body),
      tone: Value(tone),
      length: Value(length),
      status: Value(status),
      providerType: Value(providerType),
      variationIndex: Value(variationIndex),
      createdAt: Value(createdAt),
      updatedAt: Value(updatedAt),
    );
  }

  factory MessageDraftEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return MessageDraftEntry(
      id: serializer.fromJson<String>(json['id']),
      birthdayId: serializer.fromJson<String>(json['birthdayId']),
      personId: serializer.fromJson<String>(json['personId']),
      body: serializer.fromJson<String>(json['body']),
      tone: serializer.fromJson<String>(json['tone']),
      length: serializer.fromJson<String>(json['length']),
      status: serializer.fromJson<String>(json['status']),
      providerType: serializer.fromJson<String>(json['providerType']),
      variationIndex: serializer.fromJson<int>(json['variationIndex']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'birthdayId': serializer.toJson<String>(birthdayId),
      'personId': serializer.toJson<String>(personId),
      'body': serializer.toJson<String>(body),
      'tone': serializer.toJson<String>(tone),
      'length': serializer.toJson<String>(length),
      'status': serializer.toJson<String>(status),
      'providerType': serializer.toJson<String>(providerType),
      'variationIndex': serializer.toJson<int>(variationIndex),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime>(updatedAt),
    };
  }

  MessageDraftEntry copyWith({
    String? id,
    String? birthdayId,
    String? personId,
    String? body,
    String? tone,
    String? length,
    String? status,
    String? providerType,
    int? variationIndex,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) => MessageDraftEntry(
    id: id ?? this.id,
    birthdayId: birthdayId ?? this.birthdayId,
    personId: personId ?? this.personId,
    body: body ?? this.body,
    tone: tone ?? this.tone,
    length: length ?? this.length,
    status: status ?? this.status,
    providerType: providerType ?? this.providerType,
    variationIndex: variationIndex ?? this.variationIndex,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
  );
  MessageDraftEntry copyWithCompanion(MessageDraftEntriesCompanion data) {
    return MessageDraftEntry(
      id: data.id.present ? data.id.value : this.id,
      birthdayId: data.birthdayId.present
          ? data.birthdayId.value
          : this.birthdayId,
      personId: data.personId.present ? data.personId.value : this.personId,
      body: data.body.present ? data.body.value : this.body,
      tone: data.tone.present ? data.tone.value : this.tone,
      length: data.length.present ? data.length.value : this.length,
      status: data.status.present ? data.status.value : this.status,
      providerType: data.providerType.present
          ? data.providerType.value
          : this.providerType,
      variationIndex: data.variationIndex.present
          ? data.variationIndex.value
          : this.variationIndex,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('MessageDraftEntry(')
          ..write('id: $id, ')
          ..write('birthdayId: $birthdayId, ')
          ..write('personId: $personId, ')
          ..write('body: $body, ')
          ..write('tone: $tone, ')
          ..write('length: $length, ')
          ..write('status: $status, ')
          ..write('providerType: $providerType, ')
          ..write('variationIndex: $variationIndex, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    birthdayId,
    personId,
    body,
    tone,
    length,
    status,
    providerType,
    variationIndex,
    createdAt,
    updatedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is MessageDraftEntry &&
          other.id == this.id &&
          other.birthdayId == this.birthdayId &&
          other.personId == this.personId &&
          other.body == this.body &&
          other.tone == this.tone &&
          other.length == this.length &&
          other.status == this.status &&
          other.providerType == this.providerType &&
          other.variationIndex == this.variationIndex &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class MessageDraftEntriesCompanion extends UpdateCompanion<MessageDraftEntry> {
  final Value<String> id;
  final Value<String> birthdayId;
  final Value<String> personId;
  final Value<String> body;
  final Value<String> tone;
  final Value<String> length;
  final Value<String> status;
  final Value<String> providerType;
  final Value<int> variationIndex;
  final Value<DateTime> createdAt;
  final Value<DateTime> updatedAt;
  final Value<int> rowid;
  const MessageDraftEntriesCompanion({
    this.id = const Value.absent(),
    this.birthdayId = const Value.absent(),
    this.personId = const Value.absent(),
    this.body = const Value.absent(),
    this.tone = const Value.absent(),
    this.length = const Value.absent(),
    this.status = const Value.absent(),
    this.providerType = const Value.absent(),
    this.variationIndex = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  MessageDraftEntriesCompanion.insert({
    required String id,
    required String birthdayId,
    required String personId,
    required String body,
    required String tone,
    required String length,
    this.status = const Value.absent(),
    this.providerType = const Value.absent(),
    this.variationIndex = const Value.absent(),
    required DateTime createdAt,
    required DateTime updatedAt,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       birthdayId = Value(birthdayId),
       personId = Value(personId),
       body = Value(body),
       tone = Value(tone),
       length = Value(length),
       createdAt = Value(createdAt),
       updatedAt = Value(updatedAt);
  static Insertable<MessageDraftEntry> custom({
    Expression<String>? id,
    Expression<String>? birthdayId,
    Expression<String>? personId,
    Expression<String>? body,
    Expression<String>? tone,
    Expression<String>? length,
    Expression<String>? status,
    Expression<String>? providerType,
    Expression<int>? variationIndex,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (birthdayId != null) 'birthday_id': birthdayId,
      if (personId != null) 'person_id': personId,
      if (body != null) 'body': body,
      if (tone != null) 'tone': tone,
      if (length != null) 'length': length,
      if (status != null) 'status': status,
      if (providerType != null) 'provider_type': providerType,
      if (variationIndex != null) 'variation_index': variationIndex,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  MessageDraftEntriesCompanion copyWith({
    Value<String>? id,
    Value<String>? birthdayId,
    Value<String>? personId,
    Value<String>? body,
    Value<String>? tone,
    Value<String>? length,
    Value<String>? status,
    Value<String>? providerType,
    Value<int>? variationIndex,
    Value<DateTime>? createdAt,
    Value<DateTime>? updatedAt,
    Value<int>? rowid,
  }) {
    return MessageDraftEntriesCompanion(
      id: id ?? this.id,
      birthdayId: birthdayId ?? this.birthdayId,
      personId: personId ?? this.personId,
      body: body ?? this.body,
      tone: tone ?? this.tone,
      length: length ?? this.length,
      status: status ?? this.status,
      providerType: providerType ?? this.providerType,
      variationIndex: variationIndex ?? this.variationIndex,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (birthdayId.present) {
      map['birthday_id'] = Variable<String>(birthdayId.value);
    }
    if (personId.present) {
      map['person_id'] = Variable<String>(personId.value);
    }
    if (body.present) {
      map['body'] = Variable<String>(body.value);
    }
    if (tone.present) {
      map['tone'] = Variable<String>(tone.value);
    }
    if (length.present) {
      map['length'] = Variable<String>(length.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (providerType.present) {
      map['provider_type'] = Variable<String>(providerType.value);
    }
    if (variationIndex.present) {
      map['variation_index'] = Variable<int>(variationIndex.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('MessageDraftEntriesCompanion(')
          ..write('id: $id, ')
          ..write('birthdayId: $birthdayId, ')
          ..write('personId: $personId, ')
          ..write('body: $body, ')
          ..write('tone: $tone, ')
          ..write('length: $length, ')
          ..write('status: $status, ')
          ..write('providerType: $providerType, ')
          ..write('variationIndex: $variationIndex, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $SyncTombstonesEntriesTable extends SyncTombstonesEntries
    with TableInfo<$SyncTombstonesEntriesTable, SyncTombstonesEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SyncTombstonesEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _entityTypeMeta = const VerificationMeta(
    'entityType',
  );
  @override
  late final GeneratedColumn<String> entityType = GeneratedColumn<String>(
    'entity_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _entityIdMeta = const VerificationMeta(
    'entityId',
  );
  @override
  late final GeneratedColumn<String> entityId = GeneratedColumn<String>(
    'entity_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _deletedAtMeta = const VerificationMeta(
    'deletedAt',
  );
  @override
  late final GeneratedColumn<DateTime> deletedAt = GeneratedColumn<DateTime>(
    'deleted_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [id, entityType, entityId, deletedAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'sync_tombstones_entries';
  @override
  VerificationContext validateIntegrity(
    Insertable<SyncTombstonesEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('entity_type')) {
      context.handle(
        _entityTypeMeta,
        entityType.isAcceptableOrUnknown(data['entity_type']!, _entityTypeMeta),
      );
    } else if (isInserting) {
      context.missing(_entityTypeMeta);
    }
    if (data.containsKey('entity_id')) {
      context.handle(
        _entityIdMeta,
        entityId.isAcceptableOrUnknown(data['entity_id']!, _entityIdMeta),
      );
    } else if (isInserting) {
      context.missing(_entityIdMeta);
    }
    if (data.containsKey('deleted_at')) {
      context.handle(
        _deletedAtMeta,
        deletedAt.isAcceptableOrUnknown(data['deleted_at']!, _deletedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_deletedAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  SyncTombstonesEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SyncTombstonesEntry(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      entityType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}entity_type'],
      )!,
      entityId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}entity_id'],
      )!,
      deletedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}deleted_at'],
      )!,
    );
  }

  @override
  $SyncTombstonesEntriesTable createAlias(String alias) {
    return $SyncTombstonesEntriesTable(attachedDatabase, alias);
  }
}

class SyncTombstonesEntry extends DataClass
    implements Insertable<SyncTombstonesEntry> {
  final String id;
  final String entityType;
  final String entityId;
  final DateTime deletedAt;
  const SyncTombstonesEntry({
    required this.id,
    required this.entityType,
    required this.entityId,
    required this.deletedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['entity_type'] = Variable<String>(entityType);
    map['entity_id'] = Variable<String>(entityId);
    map['deleted_at'] = Variable<DateTime>(deletedAt);
    return map;
  }

  SyncTombstonesEntriesCompanion toCompanion(bool nullToAbsent) {
    return SyncTombstonesEntriesCompanion(
      id: Value(id),
      entityType: Value(entityType),
      entityId: Value(entityId),
      deletedAt: Value(deletedAt),
    );
  }

  factory SyncTombstonesEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SyncTombstonesEntry(
      id: serializer.fromJson<String>(json['id']),
      entityType: serializer.fromJson<String>(json['entityType']),
      entityId: serializer.fromJson<String>(json['entityId']),
      deletedAt: serializer.fromJson<DateTime>(json['deletedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'entityType': serializer.toJson<String>(entityType),
      'entityId': serializer.toJson<String>(entityId),
      'deletedAt': serializer.toJson<DateTime>(deletedAt),
    };
  }

  SyncTombstonesEntry copyWith({
    String? id,
    String? entityType,
    String? entityId,
    DateTime? deletedAt,
  }) => SyncTombstonesEntry(
    id: id ?? this.id,
    entityType: entityType ?? this.entityType,
    entityId: entityId ?? this.entityId,
    deletedAt: deletedAt ?? this.deletedAt,
  );
  SyncTombstonesEntry copyWithCompanion(SyncTombstonesEntriesCompanion data) {
    return SyncTombstonesEntry(
      id: data.id.present ? data.id.value : this.id,
      entityType: data.entityType.present
          ? data.entityType.value
          : this.entityType,
      entityId: data.entityId.present ? data.entityId.value : this.entityId,
      deletedAt: data.deletedAt.present ? data.deletedAt.value : this.deletedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SyncTombstonesEntry(')
          ..write('id: $id, ')
          ..write('entityType: $entityType, ')
          ..write('entityId: $entityId, ')
          ..write('deletedAt: $deletedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, entityType, entityId, deletedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SyncTombstonesEntry &&
          other.id == this.id &&
          other.entityType == this.entityType &&
          other.entityId == this.entityId &&
          other.deletedAt == this.deletedAt);
}

class SyncTombstonesEntriesCompanion
    extends UpdateCompanion<SyncTombstonesEntry> {
  final Value<String> id;
  final Value<String> entityType;
  final Value<String> entityId;
  final Value<DateTime> deletedAt;
  final Value<int> rowid;
  const SyncTombstonesEntriesCompanion({
    this.id = const Value.absent(),
    this.entityType = const Value.absent(),
    this.entityId = const Value.absent(),
    this.deletedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  SyncTombstonesEntriesCompanion.insert({
    required String id,
    required String entityType,
    required String entityId,
    required DateTime deletedAt,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       entityType = Value(entityType),
       entityId = Value(entityId),
       deletedAt = Value(deletedAt);
  static Insertable<SyncTombstonesEntry> custom({
    Expression<String>? id,
    Expression<String>? entityType,
    Expression<String>? entityId,
    Expression<DateTime>? deletedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (entityType != null) 'entity_type': entityType,
      if (entityId != null) 'entity_id': entityId,
      if (deletedAt != null) 'deleted_at': deletedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  SyncTombstonesEntriesCompanion copyWith({
    Value<String>? id,
    Value<String>? entityType,
    Value<String>? entityId,
    Value<DateTime>? deletedAt,
    Value<int>? rowid,
  }) {
    return SyncTombstonesEntriesCompanion(
      id: id ?? this.id,
      entityType: entityType ?? this.entityType,
      entityId: entityId ?? this.entityId,
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
    if (entityType.present) {
      map['entity_type'] = Variable<String>(entityType.value);
    }
    if (entityId.present) {
      map['entity_id'] = Variable<String>(entityId.value);
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
    return (StringBuffer('SyncTombstonesEntriesCompanion(')
          ..write('id: $id, ')
          ..write('entityType: $entityType, ')
          ..write('entityId: $entityId, ')
          ..write('deletedAt: $deletedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $PeopleEntriesTable peopleEntries = $PeopleEntriesTable(this);
  late final $BirthdayEntriesTable birthdayEntries = $BirthdayEntriesTable(
    this,
  );
  late final $MessageDraftEntriesTable messageDraftEntries =
      $MessageDraftEntriesTable(this);
  late final $SyncTombstonesEntriesTable syncTombstonesEntries =
      $SyncTombstonesEntriesTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    peopleEntries,
    birthdayEntries,
    messageDraftEntries,
    syncTombstonesEntries,
  ];
}

typedef $$PeopleEntriesTableCreateCompanionBuilder =
    PeopleEntriesCompanion Function({
      required String id,
      required String name,
      required int birthdayMonth,
      required int birthdayDay,
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
      required DateTime createdAt,
      required DateTime updatedAt,
      Value<int> version,
      Value<int> rowid,
    });
typedef $$PeopleEntriesTableUpdateCompanionBuilder =
    PeopleEntriesCompanion Function({
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
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<int> version,
      Value<int> rowid,
    });

class $$PeopleEntriesTableFilterComposer
    extends Composer<_$AppDatabase, $PeopleEntriesTable> {
  $$PeopleEntriesTableFilterComposer({
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
}

class $$PeopleEntriesTableOrderingComposer
    extends Composer<_$AppDatabase, $PeopleEntriesTable> {
  $$PeopleEntriesTableOrderingComposer({
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
}

class $$PeopleEntriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $PeopleEntriesTable> {
  $$PeopleEntriesTableAnnotationComposer({
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

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);

  GeneratedColumn<int> get version =>
      $composableBuilder(column: $table.version, builder: (column) => column);
}

class $$PeopleEntriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $PeopleEntriesTable,
          PeopleEntry,
          $$PeopleEntriesTableFilterComposer,
          $$PeopleEntriesTableOrderingComposer,
          $$PeopleEntriesTableAnnotationComposer,
          $$PeopleEntriesTableCreateCompanionBuilder,
          $$PeopleEntriesTableUpdateCompanionBuilder,
          (
            PeopleEntry,
            BaseReferences<_$AppDatabase, $PeopleEntriesTable, PeopleEntry>,
          ),
          PeopleEntry,
          PrefetchHooks Function()
        > {
  $$PeopleEntriesTableTableManager(_$AppDatabase db, $PeopleEntriesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$PeopleEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$PeopleEntriesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$PeopleEntriesTableAnnotationComposer($db: db, $table: table),
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
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> version = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => PeopleEntriesCompanion(
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
                createdAt: createdAt,
                updatedAt: updatedAt,
                version: version,
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
                Value<String> relationship = const Value.absent(),
                Value<String> relationshipCloseness = const Value.absent(),
                Value<String> preferredLanguage = const Value.absent(),
                Value<String> preferredTone = const Value.absent(),
                Value<String> importantFacts = const Value.absent(),
                Value<String?> notes = const Value.absent(),
                Value<String> preferredDeliveryChannel = const Value.absent(),
                Value<String?> timezone = const Value.absent(),
                Value<bool> autoPrepare = const Value.absent(),
                required DateTime createdAt,
                required DateTime updatedAt,
                Value<int> version = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => PeopleEntriesCompanion.insert(
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
                createdAt: createdAt,
                updatedAt: updatedAt,
                version: version,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$PeopleEntriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $PeopleEntriesTable,
      PeopleEntry,
      $$PeopleEntriesTableFilterComposer,
      $$PeopleEntriesTableOrderingComposer,
      $$PeopleEntriesTableAnnotationComposer,
      $$PeopleEntriesTableCreateCompanionBuilder,
      $$PeopleEntriesTableUpdateCompanionBuilder,
      (
        PeopleEntry,
        BaseReferences<_$AppDatabase, $PeopleEntriesTable, PeopleEntry>,
      ),
      PeopleEntry,
      PrefetchHooks Function()
    >;
typedef $$BirthdayEntriesTableCreateCompanionBuilder =
    BirthdayEntriesCompanion Function({
      required String id,
      required String personId,
      required int cycleYear,
      required DateTime date,
      Value<String> status,
      Value<String?> draftId,
      required DateTime createdAt,
      required DateTime updatedAt,
      Value<int> rowid,
    });
typedef $$BirthdayEntriesTableUpdateCompanionBuilder =
    BirthdayEntriesCompanion Function({
      Value<String> id,
      Value<String> personId,
      Value<int> cycleYear,
      Value<DateTime> date,
      Value<String> status,
      Value<String?> draftId,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

class $$BirthdayEntriesTableFilterComposer
    extends Composer<_$AppDatabase, $BirthdayEntriesTable> {
  $$BirthdayEntriesTableFilterComposer({
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

  ColumnFilters<String> get personId => $composableBuilder(
    column: $table.personId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get cycleYear => $composableBuilder(
    column: $table.cycleYear,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get date => $composableBuilder(
    column: $table.date,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get draftId => $composableBuilder(
    column: $table.draftId,
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
}

class $$BirthdayEntriesTableOrderingComposer
    extends Composer<_$AppDatabase, $BirthdayEntriesTable> {
  $$BirthdayEntriesTableOrderingComposer({
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

  ColumnOrderings<String> get personId => $composableBuilder(
    column: $table.personId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get cycleYear => $composableBuilder(
    column: $table.cycleYear,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get date => $composableBuilder(
    column: $table.date,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get draftId => $composableBuilder(
    column: $table.draftId,
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
}

class $$BirthdayEntriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $BirthdayEntriesTable> {
  $$BirthdayEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get personId =>
      $composableBuilder(column: $table.personId, builder: (column) => column);

  GeneratedColumn<int> get cycleYear =>
      $composableBuilder(column: $table.cycleYear, builder: (column) => column);

  GeneratedColumn<DateTime> get date =>
      $composableBuilder(column: $table.date, builder: (column) => column);

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get draftId =>
      $composableBuilder(column: $table.draftId, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $$BirthdayEntriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $BirthdayEntriesTable,
          BirthdayEntry,
          $$BirthdayEntriesTableFilterComposer,
          $$BirthdayEntriesTableOrderingComposer,
          $$BirthdayEntriesTableAnnotationComposer,
          $$BirthdayEntriesTableCreateCompanionBuilder,
          $$BirthdayEntriesTableUpdateCompanionBuilder,
          (
            BirthdayEntry,
            BaseReferences<_$AppDatabase, $BirthdayEntriesTable, BirthdayEntry>,
          ),
          BirthdayEntry,
          PrefetchHooks Function()
        > {
  $$BirthdayEntriesTableTableManager(
    _$AppDatabase db,
    $BirthdayEntriesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$BirthdayEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$BirthdayEntriesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$BirthdayEntriesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> personId = const Value.absent(),
                Value<int> cycleYear = const Value.absent(),
                Value<DateTime> date = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<String?> draftId = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => BirthdayEntriesCompanion(
                id: id,
                personId: personId,
                cycleYear: cycleYear,
                date: date,
                status: status,
                draftId: draftId,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String personId,
                required int cycleYear,
                required DateTime date,
                Value<String> status = const Value.absent(),
                Value<String?> draftId = const Value.absent(),
                required DateTime createdAt,
                required DateTime updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => BirthdayEntriesCompanion.insert(
                id: id,
                personId: personId,
                cycleYear: cycleYear,
                date: date,
                status: status,
                draftId: draftId,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$BirthdayEntriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $BirthdayEntriesTable,
      BirthdayEntry,
      $$BirthdayEntriesTableFilterComposer,
      $$BirthdayEntriesTableOrderingComposer,
      $$BirthdayEntriesTableAnnotationComposer,
      $$BirthdayEntriesTableCreateCompanionBuilder,
      $$BirthdayEntriesTableUpdateCompanionBuilder,
      (
        BirthdayEntry,
        BaseReferences<_$AppDatabase, $BirthdayEntriesTable, BirthdayEntry>,
      ),
      BirthdayEntry,
      PrefetchHooks Function()
    >;
typedef $$MessageDraftEntriesTableCreateCompanionBuilder =
    MessageDraftEntriesCompanion Function({
      required String id,
      required String birthdayId,
      required String personId,
      required String body,
      required String tone,
      required String length,
      Value<String> status,
      Value<String> providerType,
      Value<int> variationIndex,
      required DateTime createdAt,
      required DateTime updatedAt,
      Value<int> rowid,
    });
typedef $$MessageDraftEntriesTableUpdateCompanionBuilder =
    MessageDraftEntriesCompanion Function({
      Value<String> id,
      Value<String> birthdayId,
      Value<String> personId,
      Value<String> body,
      Value<String> tone,
      Value<String> length,
      Value<String> status,
      Value<String> providerType,
      Value<int> variationIndex,
      Value<DateTime> createdAt,
      Value<DateTime> updatedAt,
      Value<int> rowid,
    });

class $$MessageDraftEntriesTableFilterComposer
    extends Composer<_$AppDatabase, $MessageDraftEntriesTable> {
  $$MessageDraftEntriesTableFilterComposer({
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

  ColumnFilters<String> get birthdayId => $composableBuilder(
    column: $table.birthdayId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get personId => $composableBuilder(
    column: $table.personId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get body => $composableBuilder(
    column: $table.body,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tone => $composableBuilder(
    column: $table.tone,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get length => $composableBuilder(
    column: $table.length,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get providerType => $composableBuilder(
    column: $table.providerType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get variationIndex => $composableBuilder(
    column: $table.variationIndex,
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
}

class $$MessageDraftEntriesTableOrderingComposer
    extends Composer<_$AppDatabase, $MessageDraftEntriesTable> {
  $$MessageDraftEntriesTableOrderingComposer({
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

  ColumnOrderings<String> get birthdayId => $composableBuilder(
    column: $table.birthdayId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get personId => $composableBuilder(
    column: $table.personId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get body => $composableBuilder(
    column: $table.body,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tone => $composableBuilder(
    column: $table.tone,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get length => $composableBuilder(
    column: $table.length,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get providerType => $composableBuilder(
    column: $table.providerType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get variationIndex => $composableBuilder(
    column: $table.variationIndex,
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
}

class $$MessageDraftEntriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $MessageDraftEntriesTable> {
  $$MessageDraftEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get birthdayId => $composableBuilder(
    column: $table.birthdayId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get personId =>
      $composableBuilder(column: $table.personId, builder: (column) => column);

  GeneratedColumn<String> get body =>
      $composableBuilder(column: $table.body, builder: (column) => column);

  GeneratedColumn<String> get tone =>
      $composableBuilder(column: $table.tone, builder: (column) => column);

  GeneratedColumn<String> get length =>
      $composableBuilder(column: $table.length, builder: (column) => column);

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get providerType => $composableBuilder(
    column: $table.providerType,
    builder: (column) => column,
  );

  GeneratedColumn<int> get variationIndex => $composableBuilder(
    column: $table.variationIndex,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $$MessageDraftEntriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $MessageDraftEntriesTable,
          MessageDraftEntry,
          $$MessageDraftEntriesTableFilterComposer,
          $$MessageDraftEntriesTableOrderingComposer,
          $$MessageDraftEntriesTableAnnotationComposer,
          $$MessageDraftEntriesTableCreateCompanionBuilder,
          $$MessageDraftEntriesTableUpdateCompanionBuilder,
          (
            MessageDraftEntry,
            BaseReferences<
              _$AppDatabase,
              $MessageDraftEntriesTable,
              MessageDraftEntry
            >,
          ),
          MessageDraftEntry,
          PrefetchHooks Function()
        > {
  $$MessageDraftEntriesTableTableManager(
    _$AppDatabase db,
    $MessageDraftEntriesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$MessageDraftEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$MessageDraftEntriesTableOrderingComposer(
                $db: db,
                $table: table,
              ),
          createComputedFieldComposer: () =>
              $$MessageDraftEntriesTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> birthdayId = const Value.absent(),
                Value<String> personId = const Value.absent(),
                Value<String> body = const Value.absent(),
                Value<String> tone = const Value.absent(),
                Value<String> length = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<String> providerType = const Value.absent(),
                Value<int> variationIndex = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => MessageDraftEntriesCompanion(
                id: id,
                birthdayId: birthdayId,
                personId: personId,
                body: body,
                tone: tone,
                length: length,
                status: status,
                providerType: providerType,
                variationIndex: variationIndex,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String birthdayId,
                required String personId,
                required String body,
                required String tone,
                required String length,
                Value<String> status = const Value.absent(),
                Value<String> providerType = const Value.absent(),
                Value<int> variationIndex = const Value.absent(),
                required DateTime createdAt,
                required DateTime updatedAt,
                Value<int> rowid = const Value.absent(),
              }) => MessageDraftEntriesCompanion.insert(
                id: id,
                birthdayId: birthdayId,
                personId: personId,
                body: body,
                tone: tone,
                length: length,
                status: status,
                providerType: providerType,
                variationIndex: variationIndex,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$MessageDraftEntriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $MessageDraftEntriesTable,
      MessageDraftEntry,
      $$MessageDraftEntriesTableFilterComposer,
      $$MessageDraftEntriesTableOrderingComposer,
      $$MessageDraftEntriesTableAnnotationComposer,
      $$MessageDraftEntriesTableCreateCompanionBuilder,
      $$MessageDraftEntriesTableUpdateCompanionBuilder,
      (
        MessageDraftEntry,
        BaseReferences<
          _$AppDatabase,
          $MessageDraftEntriesTable,
          MessageDraftEntry
        >,
      ),
      MessageDraftEntry,
      PrefetchHooks Function()
    >;
typedef $$SyncTombstonesEntriesTableCreateCompanionBuilder =
    SyncTombstonesEntriesCompanion Function({
      required String id,
      required String entityType,
      required String entityId,
      required DateTime deletedAt,
      Value<int> rowid,
    });
typedef $$SyncTombstonesEntriesTableUpdateCompanionBuilder =
    SyncTombstonesEntriesCompanion Function({
      Value<String> id,
      Value<String> entityType,
      Value<String> entityId,
      Value<DateTime> deletedAt,
      Value<int> rowid,
    });

class $$SyncTombstonesEntriesTableFilterComposer
    extends Composer<_$AppDatabase, $SyncTombstonesEntriesTable> {
  $$SyncTombstonesEntriesTableFilterComposer({
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

  ColumnFilters<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get entityId => $composableBuilder(
    column: $table.entityId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$SyncTombstonesEntriesTableOrderingComposer
    extends Composer<_$AppDatabase, $SyncTombstonesEntriesTable> {
  $$SyncTombstonesEntriesTableOrderingComposer({
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

  ColumnOrderings<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get entityId => $composableBuilder(
    column: $table.entityId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get deletedAt => $composableBuilder(
    column: $table.deletedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$SyncTombstonesEntriesTableAnnotationComposer
    extends Composer<_$AppDatabase, $SyncTombstonesEntriesTable> {
  $$SyncTombstonesEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get entityType => $composableBuilder(
    column: $table.entityType,
    builder: (column) => column,
  );

  GeneratedColumn<String> get entityId =>
      $composableBuilder(column: $table.entityId, builder: (column) => column);

  GeneratedColumn<DateTime> get deletedAt =>
      $composableBuilder(column: $table.deletedAt, builder: (column) => column);
}

class $$SyncTombstonesEntriesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $SyncTombstonesEntriesTable,
          SyncTombstonesEntry,
          $$SyncTombstonesEntriesTableFilterComposer,
          $$SyncTombstonesEntriesTableOrderingComposer,
          $$SyncTombstonesEntriesTableAnnotationComposer,
          $$SyncTombstonesEntriesTableCreateCompanionBuilder,
          $$SyncTombstonesEntriesTableUpdateCompanionBuilder,
          (
            SyncTombstonesEntry,
            BaseReferences<
              _$AppDatabase,
              $SyncTombstonesEntriesTable,
              SyncTombstonesEntry
            >,
          ),
          SyncTombstonesEntry,
          PrefetchHooks Function()
        > {
  $$SyncTombstonesEntriesTableTableManager(
    _$AppDatabase db,
    $SyncTombstonesEntriesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SyncTombstonesEntriesTableFilterComposer(
                $db: db,
                $table: table,
              ),
          createOrderingComposer: () =>
              $$SyncTombstonesEntriesTableOrderingComposer(
                $db: db,
                $table: table,
              ),
          createComputedFieldComposer: () =>
              $$SyncTombstonesEntriesTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> entityType = const Value.absent(),
                Value<String> entityId = const Value.absent(),
                Value<DateTime> deletedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => SyncTombstonesEntriesCompanion(
                id: id,
                entityType: entityType,
                entityId: entityId,
                deletedAt: deletedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String entityType,
                required String entityId,
                required DateTime deletedAt,
                Value<int> rowid = const Value.absent(),
              }) => SyncTombstonesEntriesCompanion.insert(
                id: id,
                entityType: entityType,
                entityId: entityId,
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

typedef $$SyncTombstonesEntriesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $SyncTombstonesEntriesTable,
      SyncTombstonesEntry,
      $$SyncTombstonesEntriesTableFilterComposer,
      $$SyncTombstonesEntriesTableOrderingComposer,
      $$SyncTombstonesEntriesTableAnnotationComposer,
      $$SyncTombstonesEntriesTableCreateCompanionBuilder,
      $$SyncTombstonesEntriesTableUpdateCompanionBuilder,
      (
        SyncTombstonesEntry,
        BaseReferences<
          _$AppDatabase,
          $SyncTombstonesEntriesTable,
          SyncTombstonesEntry
        >,
      ),
      SyncTombstonesEntry,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$PeopleEntriesTableTableManager get peopleEntries =>
      $$PeopleEntriesTableTableManager(_db, _db.peopleEntries);
  $$BirthdayEntriesTableTableManager get birthdayEntries =>
      $$BirthdayEntriesTableTableManager(_db, _db.birthdayEntries);
  $$MessageDraftEntriesTableTableManager get messageDraftEntries =>
      $$MessageDraftEntriesTableTableManager(_db, _db.messageDraftEntries);
  $$SyncTombstonesEntriesTableTableManager get syncTombstonesEntries =>
      $$SyncTombstonesEntriesTableTableManager(_db, _db.syncTombstonesEntries);
}
