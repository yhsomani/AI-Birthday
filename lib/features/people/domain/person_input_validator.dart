import '../../birthdays/domain/birthday_engine.dart';
import 'person_input.dart';

/// Fields the add/edit form can flag. Keys stay stable so the UI can attach
/// errors to the right inputs regardless of language.
enum PersonField {
  name,
  birthday,
  birthYear,
  phoneNumber,
  email,
  relationship,
  language,
  importantFacts,
  notes,
  timezone,
}

enum InputError {
  nameRequired,
  nameTooLong,
  birthdayRequired,
  invalidBirthday,
  invalidBirthYear,
  invalidPhone,
  invalidEmail,
  relationshipTooLong,
  languageTooLong,
  factTooLong,
  tooManyFacts,
  notesTooLong,
  invalidTimezone,
}

extension InputErrorLabel on InputError {
  String get label => switch (this) {
    InputError.nameRequired => 'Name is required',
    InputError.nameTooLong => 'Name must be 120 characters or fewer',
    InputError.birthdayRequired => 'Pick a birthday (month and day)',
    InputError.invalidBirthday => 'That date does not exist',
    InputError.invalidBirthYear => 'Birth year should be a valid 4-digit year',
    InputError.invalidPhone => 'Phone number looks incorrect',
    InputError.invalidEmail => 'Email address looks incorrect',
    InputError.relationshipTooLong =>
      'Relationship must be 60 characters or fewer',
    InputError.languageTooLong => 'Language tag is too long',
    InputError.factTooLong => 'Each fact must be 200 characters or fewer',
    InputError.tooManyFacts => 'A maximum of 20 facts is supported',
    InputError.notesTooLong => 'Notes must be 2000 characters or fewer',
    InputError.invalidTimezone =>
      'Unknown timezone; use an IANA name like "Asia/Kolkata"',
  };
}

class PersonValidation {
  const PersonValidation({this.fieldErrors = const {}});

  final Map<PersonField, InputError> fieldErrors;

  bool get isValid => fieldErrors.isEmpty;

  InputError? errorFor(PersonField field) => fieldErrors[field];
}

/// Pure input validation for the recipient form. Uses the [BirthdayEngine] so
/// month/day rules match the recurrence engine exactly (incl. Feb 29).
class PersonInputValidator {
  const PersonInputValidator({this.engine = const BirthdayEngine()});

  final BirthdayEngine engine;

  static final RegExp _phone = RegExp(r'^[0-9+\-(). ]{6,20}$');
  static final RegExp _email = RegExp(
    r'^[^@\s]+@[^@\s]+\.[^@\s]+$',
    caseSensitive: false,
  );

  PersonValidation validate(PersonDraft d) {
    final errors = <PersonField, InputError>{};

    final name = d.name.trim();
    if (name.isEmpty) {
      errors[PersonField.name] = InputError.nameRequired;
    } else if (name.length > 120) {
      errors[PersonField.name] = InputError.nameTooLong;
    }

    final month = d.birthdayMonth;
    final day = d.birthdayDay;
    if (month == null || day == null) {
      errors[PersonField.birthday] = InputError.birthdayRequired;
    } else if (!engine.isValidMonthDay(month, day, year: d.birthYear)) {
      errors[PersonField.birthday] = InputError.invalidBirthday;
    }

    final year = d.birthYear;
    if (year != null && (year < 1900 || year > 2199)) {
      errors[PersonField.birthYear] = InputError.invalidBirthYear;
    }

    final phone = d.phoneNumber?.trim();
    if (phone != null && phone.isNotEmpty && !_phone.hasMatch(phone)) {
      errors[PersonField.phoneNumber] = InputError.invalidPhone;
    }

    final email = d.email?.trim();
    if (email != null && email.isNotEmpty) {
      if (email.length > 254 || !_email.hasMatch(email)) {
        errors[PersonField.email] = InputError.invalidEmail;
      }
    }

    final relationship = d.relationship.trim();
    if (relationship.length > 60) {
      errors[PersonField.relationship] = InputError.relationshipTooLong;
    }

    final language = d.preferredLanguage.trim();
    if (language.isEmpty) {
      errors[PersonField.language] = InputError.languageTooLong;
    } else if (language.length > 10) {
      errors[PersonField.language] = InputError.languageTooLong;
    }

    final facts = d.importantFacts
        .map((f) => f.trim())
        .where((f) => f.isNotEmpty)
        .toList();
    if (facts.length > 20) {
      errors[PersonField.importantFacts] = InputError.tooManyFacts;
    } else if (facts.any((f) => f.length > 200)) {
      errors[PersonField.importantFacts] = InputError.factTooLong;
    }

    final notes = d.notes?.trim();
    if (notes != null && notes.length > 2000) {
      errors[PersonField.notes] = InputError.notesTooLong;
    }

    final timezone = d.timezone?.trim();
    if (timezone != null && timezone.isNotEmpty) {
      if (!engine.isKnownTimezone(timezone)) {
        errors[PersonField.timezone] = InputError.invalidTimezone;
      } else if (timezone.length > 64) {
        errors[PersonField.timezone] = InputError.invalidTimezone;
      }
    }

    return PersonValidation(fieldErrors: errors);
  }
}
