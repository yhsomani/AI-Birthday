import 'package:ai_birthday/features/birthdays/domain/birthday_engine.dart';
import 'package:ai_birthday/features/people/domain/person_input.dart';
import 'package:ai_birthday/features/people/domain/person_input_validator.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:timezone/data/latest_all.dart' as tz_data;

void main() {
  setUpAll(tz_data.initializeTimeZones);

  const validator = PersonInputValidator();
  const engine = BirthdayEngine();

  PersonDraft base() => const PersonDraft(
    name: 'Ada Lovelace',
    birthdayMonth: 12,
    birthdayDay: 10,
  );

  test('accepts a minimal valid draft', () {
    final v = validator.validate(base());
    expect(v.isValid, isTrue);
  });

  test('requires a non-empty name', () {
    final v = validator.validate(base().copyWith(name: '   '));
    expect(v.isValid, isFalse);
    expect(v.errorFor(PersonField.name), InputError.nameRequired);
  });

  test('caps name length', () {
    final v = validator.validate(base().copyWith(name: 'n' * 121));
    expect(v.errorFor(PersonField.name), InputError.nameTooLong);
  });

  test('requires month and day', () {
    final none = validator.validate(const PersonDraft(name: 'A'));
    expect(none.errorFor(PersonField.birthday), InputError.birthdayRequired);
  });

  test('rejects impossible calendar dates via the birthday engine', () {
    final apr31 = validator.validate(
      base().copyWith(birthdayMonth: 4, birthdayDay: 31),
    );
    expect(apr31.errorFor(PersonField.birthday), InputError.invalidBirthday);

    final feb30 = validator.validate(
      base().copyWith(birthdayMonth: 2, birthdayDay: 30),
    );
    expect(feb30.errorFor(PersonField.birthday), InputError.invalidBirthday);
  });

  test('Feb 29 accepted when year unknown and accepted for leap years', () {
    final unknown = validator.validate(
      base().copyWith(birthdayMonth: 2, birthdayDay: 29),
    );
    expect(unknown.isValid, isTrue);

    final leap = validator.validate(
      base().copyWith(birthdayMonth: 2, birthdayDay: 29, birthYear: 2024),
    );
    expect(leap.isValid, isTrue);
  });

  test('rejects an out-of-range birth year', () {
    final v = validator.validate(base().copyWith(birthYear: 1874));
    expect(v.errorFor(PersonField.birthYear), InputError.invalidBirthYear);
  });

  test('phone and email are validated only when provided', () {
    final dirty = validator.validate(
      base().copyWith(phoneNumber: 'not a phone!!', email: 'not-an-email'),
    );
    expect(dirty.errorFor(PersonField.phoneNumber), InputError.invalidPhone);
    expect(dirty.errorFor(PersonField.email), InputError.invalidEmail);

    final ok = validator.validate(
      base().copyWith(phoneNumber: '+91 98765 43210', email: 'ada@example.com'),
    );
    expect(ok.isValid, isTrue);
  });

  test('unknown timezones are rejected, IANA names accepted', () {
    final bad = validator.validate(base().copyWith(timezone: 'Fly/Nowhere'));
    expect(bad.errorFor(PersonField.timezone), InputError.invalidTimezone);

    final good = validator.validate(base().copyWith(timezone: 'Asia/Kolkata'));
    expect(good.isValid, isTrue);
  });

  test('constrains facts, notes and relationship lengths', () {
    expect(
      validator
          .validate(base().copyWith(importantFacts: ['f' * 201]))
          .errorFor(PersonField.importantFacts),
      InputError.factTooLong,
    );
    expect(
      validator
          .validate(base().copyWith(importantFacts: List.filled(21, 'x')))
          .errorFor(PersonField.importantFacts),
      InputError.tooManyFacts,
    );
    expect(
      validator
          .validate(base().copyWith(notes: 'n' * 2001))
          .errorFor(PersonField.notes),
      InputError.notesTooLong,
    );
    expect(
      validator
          .validate(base().copyWith(relationship: 'r' * 61))
          .errorFor(PersonField.relationship),
      InputError.relationshipTooLong,
    );
  });

  test('engine and validator agree on month cardinality', () {
    void check(int month, int day, bool expectValid) {
      final v = validator.validate(
        base().copyWith(birthdayMonth: month, birthdayDay: day),
      );
      expect(
        v.isValid,
        expectValid,
        reason: '$month/$day should validate=$expectValid',
      );
      expect(engine.isValidMonthDay(month, day), expectValid);
    }

    check(1, 31, true);
    check(4, 30, true);
    check(4, 31, false);
    check(2, 29, true);
    check(2, 30, false);
    check(12, 31, true);
  });
}
