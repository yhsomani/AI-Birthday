package com.yashsomani.birthdayautopilot.lifecycle

internal enum class TodayOccurrenceChoice(val wireValue: String) {
  NORMAL_PATH("send-through-normal-path"),
  SYSTEM_COMPOSER("open-system-composer"),
  NEXT_YEAR("start-next-year"),
  ;

  companion object {
    fun fromWire(value: String): TodayOccurrenceChoice? = entries.singleOrNull {
      it.wireValue == value
    }
  }
}

internal data class TodayOccurrenceChoices(
  val primary: TodayOccurrenceChoice,
  val alternative: TodayOccurrenceChoice?,
)

/**
 * Chooses only the review options. Confirmation must independently re-evaluate these predicates.
 */
internal object TodayOccurrenceChoicePolicy {
  fun evaluate(
    nowMillis: Long,
    windowStartMillis: Long,
    windowEndMillis: Long,
    resetSafetyAllowsBirthday: Boolean,
  ): TodayOccurrenceChoices {
    if (windowEndMillis <= windowStartMillis) {
      return TodayOccurrenceChoices(TodayOccurrenceChoice.NEXT_YEAR, null)
    }
    if (nowMillis < windowStartMillis) {
      return TodayOccurrenceChoices(TodayOccurrenceChoice.NEXT_YEAR, null)
    }
    if (nowMillis >= windowEndMillis || !resetSafetyAllowsBirthday) {
      return TodayOccurrenceChoices(
        TodayOccurrenceChoice.SYSTEM_COMPOSER,
        TodayOccurrenceChoice.NEXT_YEAR,
      )
    }
    return TodayOccurrenceChoices(
      TodayOccurrenceChoice.NORMAL_PATH,
      TodayOccurrenceChoice.NEXT_YEAR,
    )
  }
}
