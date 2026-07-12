package com.yashsomani.birthdayautopilot.planning

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdayCapacityPolicyTest {
  @Test
  fun `new initial Arm is refused when local civil-date cap is spent`() {
    assertTrue(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = 9,
        dailyCap = 10,
        occurrenceAlreadyArmed = false,
      ),
    )
    assertFalse(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = 10,
        dailyCap = 10,
        occurrenceAlreadyArmed = false,
      ),
    )
    assertFalse(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = 11,
        dailyCap = 10,
        occurrenceAlreadyArmed = false,
      ),
    )
  }

  @Test
  fun `retry for the already armed occurrence does not consume another birthday slot`() {
    assertTrue(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = 10,
        dailyCap = 10,
        occurrenceAlreadyArmed = true,
      ),
    )
    assertFalse(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = 11,
        dailyCap = 10,
        occurrenceAlreadyArmed = true,
      ),
    )
    assertFalse(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = -1,
        dailyCap = 10,
        occurrenceAlreadyArmed = true,
      ),
    )
    assertFalse(
      BirthdayCapacityPolicy.allowsArm(
        armedOnCivilDate = 0,
        dailyCap = 21,
        occurrenceAlreadyArmed = false,
      ),
    )
  }

  @Test
  fun `occurrence window is exactly bound to policy civil date and timezone`() {
    val date = LocalDate.of(2027, 3, 14)
    val zone = ZoneId.of("America/New_York")
    val start = BirthdayCapacityPolicy.resolve(date, 2 * 60 + 30, zone).toEpochMilli()
    val end = BirthdayCapacityPolicy.resolve(date, 3 * 60 + 30, zone).toEpochMilli()

    assertTrue(
      BirthdayCapacityPolicy.exactWindowBindingMatches(
        localDate = date.toString(),
        timeZoneId = zone.id,
        resolvedWindowStartMillis = start,
        resolvedWindowEndMillis = end,
        startMinute = 2 * 60 + 30,
        effectiveEndMinute = 3 * 60 + 30,
      ),
    )
    assertFalse(
      BirthdayCapacityPolicy.exactWindowBindingMatches(
        localDate = date.toString(),
        timeZoneId = "Asia/Kolkata",
        resolvedWindowStartMillis = start,
        resolvedWindowEndMillis = end,
        startMinute = 2 * 60 + 30,
        effectiveEndMinute = 3 * 60 + 30,
      ),
    )
  }
}
