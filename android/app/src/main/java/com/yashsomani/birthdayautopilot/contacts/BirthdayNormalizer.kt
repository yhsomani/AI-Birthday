package com.yashsomani.birthdayautopilot.contacts

import com.yashsomani.birthdayautopilot.planning.BirthdayRule
import com.yashsomani.birthdayautopilot.planning.LeapDayPolicy
import java.time.DateTimeException
import java.time.LocalDate

sealed interface BirthdayResolution {
  data class Resolved(val birthday: NormalizedBirthday) : BirthdayResolution
  data class Unresolved(val issue: ContactIssue) : BirthdayResolution
}

class BirthdayNormalizer {
  fun resolve(
    birthdays: List<RawBirthday>,
    selected: RawBirthday?,
    leapDayPolicy: LeapDayPolicy?,
  ): BirthdayResolution {
    if (birthdays.isEmpty()) return BirthdayResolution.Unresolved(ContactIssue.BIRTHDAY_MISSING)

    val validated = birthdays.map(::validate)
    if (validated.any { it == null }) {
      return BirthdayResolution.Unresolved(ContactIssue.BIRTHDAY_INVALID)
    }
    val distinct = validated.filterNotNull().distinct()
    val chosen = when {
      selected != null -> {
        val validatedSelection = validate(selected)
          ?: return BirthdayResolution.Unresolved(ContactIssue.BIRTHDAY_SELECTION_INVALID)
        if (validatedSelection !in distinct) {
          return BirthdayResolution.Unresolved(ContactIssue.BIRTHDAY_SELECTION_INVALID)
        }
        validatedSelection
      }
      distinct.size == 1 -> distinct.single()
      else -> return BirthdayResolution.Unresolved(ContactIssue.BIRTHDAY_SELECTION_REQUIRED)
    }

    val isLeapDay = chosen.month == 2 && chosen.day == 29
    if (isLeapDay && leapDayPolicy == null) {
      return BirthdayResolution.Unresolved(ContactIssue.LEAP_DAY_POLICY_REQUIRED)
    }
    if (!isLeapDay && leapDayPolicy != null) {
      return BirthdayResolution.Unresolved(ContactIssue.LEAP_DAY_POLICY_INVALID)
    }

    return BirthdayResolution.Resolved(
      NormalizedBirthday(
        rule = BirthdayRule(
          month = chosen.month,
          day = chosen.day,
          leapDayPolicy = leapDayPolicy,
        ),
        selectedSource = RawBirthday(chosen.year, chosen.month, chosen.day),
      ),
    )
  }

  private fun validate(raw: RawBirthday): ValidatedBirthday? {
    val month = raw.month ?: return null
    val day = raw.day ?: return null
    val validationYear = raw.year ?: LEAP_YEAR
    if (validationYear !in 1..9999) return null
    return try {
      LocalDate.of(validationYear, month, day)
      ValidatedBirthday(raw.year, month, day)
    } catch (_: DateTimeException) {
      null
    }
  }

  private data class ValidatedBirthday(
    val year: Int?,
    val month: Int,
    val day: Int,
  )

  private companion object {
    const val LEAP_YEAR = 2000
  }
}
