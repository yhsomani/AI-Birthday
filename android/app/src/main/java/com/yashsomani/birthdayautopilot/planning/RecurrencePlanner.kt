package com.yashsomani.birthdayautopilot.planning

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.time.Year

enum class LeapDayPolicy {
  FEBRUARY_28,
  MARCH_1,
  SKIP_NON_LEAP_YEAR,
}

data class BirthdayRule(
  val month: Int,
  val day: Int,
  val leapDayPolicy: LeapDayPolicy? = null,
)

class RecurrencePlanner {
  fun nextOccurrence(onOrAfter: LocalDate, birthday: BirthdayRule): LocalDate {
    validate(birthday)
    for (year in onOrAfter.year..(onOrAfter.year + SEARCH_YEARS)) {
      val candidate = occurrenceInYear(year, birthday) ?: continue
      if (!candidate.isBefore(onOrAfter)) return candidate
    }
    throw IllegalStateException("occurrence-search-exhausted")
  }

  fun occurrenceInYear(year: Int, birthday: BirthdayRule): LocalDate? {
    validate(birthday)
    if (birthday.month == Month.FEBRUARY.value && birthday.day == 29 && !Year.isLeap(year.toLong())) {
      return when (birthday.leapDayPolicy) {
        LeapDayPolicy.FEBRUARY_28 -> LocalDate.of(year, Month.FEBRUARY, 28)
        LeapDayPolicy.MARCH_1 -> LocalDate.of(year, Month.MARCH, 1)
        LeapDayPolicy.SKIP_NON_LEAP_YEAR -> null
        null -> throw IllegalArgumentException("leap-day-policy-required")
      }
    }
    return LocalDate.of(year, birthday.month, birthday.day)
  }

  private fun validate(birthday: BirthdayRule) {
    try {
      LocalDate.of(LEAP_VALIDATION_YEAR, birthday.month, birthday.day)
    } catch (error: DateTimeException) {
      throw IllegalArgumentException("birthday-invalid", error)
    }
    if (birthday.month == Month.FEBRUARY.value && birthday.day == 29 && birthday.leapDayPolicy == null) {
      throw IllegalArgumentException("leap-day-policy-required")
    }
  }

  private companion object {
    const val LEAP_VALIDATION_YEAR = 2024
    const val SEARCH_YEARS = 8
  }
}
