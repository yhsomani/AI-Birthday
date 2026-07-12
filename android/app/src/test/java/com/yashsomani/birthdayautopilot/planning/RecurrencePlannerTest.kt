package com.yashsomani.birthdayautopilot.planning

import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrencePlannerTest {
  private val planner = RecurrencePlanner()

  @Test
  fun `uses the current year when the birthday is still ahead`() {
    assertEquals(
      LocalDate.of(2026, 7, 14),
      planner.nextOccurrence(
        onOrAfter = LocalDate.of(2026, 7, 11),
        birthday = BirthdayRule(month = 7, day = 14),
      ),
    )
  }

  @Test
  fun `moves to next year after the birthday closes`() {
    assertEquals(
      LocalDate.of(2027, 7, 14),
      planner.nextOccurrence(
        onOrAfter = LocalDate.of(2026, 7, 15),
        birthday = BirthdayRule(month = 7, day = 14),
      ),
    )
  }

  @Test
  fun `applies each explicit leap day policy`() {
    assertEquals(
      LocalDate.of(2025, 2, 28),
      planner.occurrenceInYear(2025, BirthdayRule(2, 29, LeapDayPolicy.FEBRUARY_28)),
    )
    assertEquals(
      LocalDate.of(2025, 3, 1),
      planner.occurrenceInYear(2025, BirthdayRule(2, 29, LeapDayPolicy.MARCH_1)),
    )
    assertNull(
      planner.occurrenceInYear(2025, BirthdayRule(2, 29, LeapDayPolicy.SKIP_NON_LEAP_YEAR)),
    )
  }

  @Test
  fun `every valid annual civil date is preserved across a twenty-year matrix`() {
    val horizon = 2024..2043
    for (month in 1..12) {
      for (day in 1..YearMonth.of(2024, month).lengthOfMonth()) {
        if (month == 2 && day == 29) continue
        val rule = BirthdayRule(month, day)
        for (year in horizon) {
          assertEquals(
            "annual civil date $month-$day in $year",
            LocalDate.of(year, month, day),
            planner.occurrenceInYear(year, rule),
          )
        }
      }
    }
  }

  @Test
  fun `all leap policies remain exact across twenty years and the skipped century leap`() {
    val horizon = 2024..2043
    for (year in horizon) {
      val leapYear = Year.isLeap(year.toLong())
      assertEquals(
        if (leapYear) LocalDate.of(year, 2, 29) else LocalDate.of(year, 2, 28),
        planner.occurrenceInYear(year, BirthdayRule(2, 29, LeapDayPolicy.FEBRUARY_28)),
      )
      assertEquals(
        if (leapYear) LocalDate.of(year, 2, 29) else LocalDate.of(year, 3, 1),
        planner.occurrenceInYear(year, BirthdayRule(2, 29, LeapDayPolicy.MARCH_1)),
      )
      assertEquals(
        if (leapYear) LocalDate.of(year, 2, 29) else null,
        planner.occurrenceInYear(year, BirthdayRule(2, 29, LeapDayPolicy.SKIP_NON_LEAP_YEAR)),
      )
    }

    assertEquals(
      LocalDate.of(2104, 2, 29),
      planner.nextOccurrence(
        LocalDate.of(2096, 3, 1),
        BirthdayRule(2, 29, LeapDayPolicy.SKIP_NON_LEAP_YEAR),
      ),
    )
  }

  @Test
  fun `next occurrence is the earliest valid date for every day in a twenty-year horizon`() {
    val rules = listOf(
      BirthdayRule(1, 1),
      BirthdayRule(2, 28),
      BirthdayRule(2, 29, LeapDayPolicy.FEBRUARY_28),
      BirthdayRule(2, 29, LeapDayPolicy.MARCH_1),
      BirthdayRule(2, 29, LeapDayPolicy.SKIP_NON_LEAP_YEAR),
      BirthdayRule(3, 1),
      BirthdayRule(7, 12),
      BirthdayRule(12, 31),
    )
    var date = LocalDate.of(2024, 1, 1)
    val end = LocalDate.of(2043, 12, 31)
    while (!date.isAfter(end)) {
      for (rule in rules) {
        val expected = expectedNext(date, rule)
        val actual = planner.nextOccurrence(date, rule)
        assertEquals("$rule on or after $date", expected, actual)
        assertFalse(actual.isBefore(date))
        assertTrue(actual == planner.occurrenceInYear(actual.year, rule))
      }
      date = date.plusDays(1)
    }
  }

  @Test
  fun `a birthday without a source year remains an annual rule`() {
    // BirthdayRule intentionally has no birth-year field. A source contact with an unknown year
    // therefore follows the same annual month/day rule at both ends of the acceptance horizon.
    val unknownYearBirthday = BirthdayRule(month = 7, day = 12)
    assertEquals(
      LocalDate.of(2024, 7, 12),
      planner.nextOccurrence(LocalDate.of(2024, 1, 1), unknownYearBirthday),
    )
    assertEquals(
      LocalDate.of(2044, 7, 12),
      planner.nextOccurrence(LocalDate.of(2043, 7, 13), unknownYearBirthday),
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects leap day without a policy`() {
    planner.nextOccurrence(LocalDate.of(2025, 1, 1), BirthdayRule(2, 29))
  }

  private fun expectedNext(onOrAfter: LocalDate, rule: BirthdayRule): LocalDate {
    for (year in onOrAfter.year..(onOrAfter.year + 8)) {
      val candidate = expectedInYear(year, rule) ?: continue
      if (!candidate.isBefore(onOrAfter)) return candidate
    }
    error("test-oracle-search-exhausted")
  }

  private fun expectedInYear(year: Int, rule: BirthdayRule): LocalDate? {
    if (MonthDay.of(rule.month, rule.day) == MonthDay.of(2, 29) && !Year.isLeap(year.toLong())) {
      return when (rule.leapDayPolicy) {
        LeapDayPolicy.FEBRUARY_28 -> LocalDate.of(year, 2, 28)
        LeapDayPolicy.MARCH_1 -> LocalDate.of(year, 3, 1)
        LeapDayPolicy.SKIP_NON_LEAP_YEAR -> null
        null -> error("test-oracle-leap-policy-required")
      }
    }
    return LocalDate.of(year, rule.month, rule.day)
  }
}
