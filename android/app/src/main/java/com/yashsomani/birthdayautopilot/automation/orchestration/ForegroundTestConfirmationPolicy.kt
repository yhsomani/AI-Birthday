package com.yashsomani.birthdayautopilot.automation.orchestration

/** Final, fail-closed foreground proof for a user-requested TEST submission. */
internal object ForegroundTestConfirmationPolicy {
  const val MAX_CONFIRMATION_AGE_MILLIS = 5 * 60 * 1_000L

  fun isValid(
    expectedNonceHash: String?,
    suppliedNonceHash: String?,
    foregroundConfirmedAtMillis: Long?,
    wallNowMillis: Long,
    resumedActivityPresent: Boolean,
  ): Boolean {
    if (
      !resumedActivityPresent ||
      expectedNonceHash.isNullOrBlank() ||
      suppliedNonceHash.isNullOrBlank() ||
      expectedNonceHash != suppliedNonceHash ||
      foregroundConfirmedAtMillis == null ||
      foregroundConfirmedAtMillis < 0 ||
      wallNowMillis < 0
    ) return false
    val ageMillis = try {
      Math.subtractExact(wallNowMillis, foregroundConfirmedAtMillis)
    } catch (_: ArithmeticException) {
      return false
    }
    return ageMillis in 0..MAX_CONFIRMATION_AGE_MILLIS
  }
}
