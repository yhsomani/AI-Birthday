package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayOccurrenceChoicePolicyTest {
  @Test
  fun protectedNormalPathIsAvailableOnlyInsideWindowWithClearResetSafety() {
    assertEquals(
      TodayOccurrenceChoices(
        TodayOccurrenceChoice.NORMAL_PATH,
        TodayOccurrenceChoice.NEXT_YEAR,
      ),
      TodayOccurrenceChoicePolicy.evaluate(1_500, 1_000, 2_000, true),
    )
    assertEquals(
      TodayOccurrenceChoices(
        TodayOccurrenceChoice.SYSTEM_COMPOSER,
        TodayOccurrenceChoice.NEXT_YEAR,
      ),
      TodayOccurrenceChoicePolicy.evaluate(1_500, 1_000, 2_000, false),
    )
  }

  @Test
  fun closedWindowOffersComposerAndNextYearButNeverNormalPath() {
    for (now in listOf(2_000L, 2_001L, Long.MAX_VALUE)) {
      assertEquals(
        TodayOccurrenceChoice.SYSTEM_COMPOSER,
        TodayOccurrenceChoicePolicy.evaluate(now, 1_000, 2_000, true).primary,
      )
    }
  }

  @Test
  fun aFutureOrMalformedWindowOffersOnlyNextYear() {
    for (choices in listOf(
      TodayOccurrenceChoicePolicy.evaluate(999, 1_000, 2_000, true),
      TodayOccurrenceChoicePolicy.evaluate(1_000, 2_000, 2_000, true),
    )) {
      assertEquals(TodayOccurrenceChoice.NEXT_YEAR, choices.primary)
      assertNull(choices.alternative)
    }
  }

  @Test
  fun wireValuesAreExactAndUnknownValuesFailClosed() {
    TodayOccurrenceChoice.entries.forEach { choice ->
      assertEquals(choice, TodayOccurrenceChoice.fromWire(choice.wireValue))
    }
    assertNull(TodayOccurrenceChoice.fromWire("send"))
  }
}
