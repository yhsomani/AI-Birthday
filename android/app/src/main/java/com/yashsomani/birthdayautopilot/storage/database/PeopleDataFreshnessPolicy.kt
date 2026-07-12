package com.yashsomani.birthdayautopilot.storage.database

/**
 * Fail-closed freshness boundary for contact-derived unattended automation.
 *
 * People synchronization timestamps are captured when a complete generation commits. Callers must
 * supply server-anchored trusted time; device wall time is never used to extend the allowance. A
 * timestamp in the future is rejected so a wall-clock jump at sync time cannot create an
 * effectively permanent cache.
 */
internal enum class PeopleDataFreshnessBand {
  NORMAL,
  STALE_WARNING,
  SAFETY_PAUSED,
  UNTRUSTED,
}

internal data class PeopleDataFreshnessAssessment(
  val band: PeopleDataFreshnessBand,
  val lastSuccessMillis: Long?,
)

internal object PeopleDataFreshnessPolicy {
  const val NORMAL_MAXIMUM_AGE_MILLIS = 7L * 24L * 60L * 60L * 1_000L
  const val MAXIMUM_UNATTENDED_AGE_MILLIS = 30L * 24L * 60L * 60L * 1_000L

  fun assess(
    state: ContactSyncStateEntity?,
    trustedNowMillis: Long?,
  ): PeopleDataFreshnessAssessment {
    val lastSuccessMillis = state?.let(::lastSuccessMillis)
    if (state == null) {
      return PeopleDataFreshnessAssessment(PeopleDataFreshnessBand.UNTRUSTED, null)
    }
    if (state.freshness in setOf(SyncFreshness.AUTH_ACTION_REQUIRED, SyncFreshness.SAFETY_PAUSED)) {
      return PeopleDataFreshnessAssessment(
        PeopleDataFreshnessBand.SAFETY_PAUSED,
        lastSuccessMillis,
      )
    }
    if (
      state.freshness !in setOf(SyncFreshness.FRESH, SyncFreshness.STALE_WARNING) ||
      trustedNowMillis == null ||
      trustedNowMillis < 0 ||
      lastSuccessMillis == null ||
      lastSuccessMillis < 0 ||
      lastSuccessMillis > trustedNowMillis
    ) {
      return PeopleDataFreshnessAssessment(
        PeopleDataFreshnessBand.UNTRUSTED,
        lastSuccessMillis,
      )
    }

    val ageMillis = try {
      Math.subtractExact(trustedNowMillis, lastSuccessMillis)
    } catch (_: ArithmeticException) {
      return PeopleDataFreshnessAssessment(
        PeopleDataFreshnessBand.UNTRUSTED,
        lastSuccessMillis,
      )
    }
    val band = when {
      ageMillis <= NORMAL_MAXIMUM_AGE_MILLIS -> PeopleDataFreshnessBand.NORMAL
      ageMillis <= MAXIMUM_UNATTENDED_AGE_MILLIS -> PeopleDataFreshnessBand.STALE_WARNING
      else -> PeopleDataFreshnessBand.SAFETY_PAUSED
    }
    return PeopleDataFreshnessAssessment(band, lastSuccessMillis)
  }

  fun allowsUnattendedAutomation(
    state: ContactSyncStateEntity?,
    trustedNowMillis: Long?,
  ): Boolean = assess(state, trustedNowMillis).band in setOf(
    PeopleDataFreshnessBand.NORMAL,
    PeopleDataFreshnessBand.STALE_WARNING,
  )

  private fun lastSuccessMillis(state: ContactSyncStateEntity): Long? = listOfNotNull(
    state.lastFullSuccessMillis,
    state.lastIncrementalSuccessMillis,
  ).maxOrNull()
}
