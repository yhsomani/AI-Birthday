package com.yashsomani.birthdayautopilot.planning

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

  @Test(expected = IllegalArgumentException::class)
  fun `rejects leap day without a policy`() {
    planner.nextOccurrence(LocalDate.of(2025, 1, 1), BirthdayRule(2, 29))
  }
}
