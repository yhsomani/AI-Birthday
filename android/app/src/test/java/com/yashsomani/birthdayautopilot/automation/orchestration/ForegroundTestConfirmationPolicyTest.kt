package com.yashsomani.birthdayautopilot.automation.orchestration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTestConfirmationPolicyTest {
  @Test
  fun `fresh matching confirmation requires a resumed activity`() {
    assertTrue(valid(now = CONFIRMED_AT + 1, resumed = true))
    assertFalse(valid(now = CONFIRMED_AT + 1, resumed = false))
  }

  @Test
  fun `delayed claim or arm response expires the foreground confirmation`() {
    assertTrue(
      valid(
        now = CONFIRMED_AT + ForegroundTestConfirmationPolicy.MAX_CONFIRMATION_AGE_MILLIS,
        resumed = true,
      ),
    )
    assertFalse(
      valid(
        now = CONFIRMED_AT + ForegroundTestConfirmationPolicy.MAX_CONFIRMATION_AGE_MILLIS + 1,
        resumed = true,
      ),
    )
  }

  @Test
  fun `nonce mismatch and wall rollback fail closed`() {
    assertFalse(
      ForegroundTestConfirmationPolicy.isValid(
        expectedNonceHash = NONCE_HASH,
        suppliedNonceHash = "different",
        foregroundConfirmedAtMillis = CONFIRMED_AT,
        wallNowMillis = CONFIRMED_AT + 1,
        resumedActivityPresent = true,
      ),
    )
    assertFalse(valid(now = CONFIRMED_AT - 1, resumed = true))
  }

  private fun valid(now: Long, resumed: Boolean): Boolean =
    ForegroundTestConfirmationPolicy.isValid(
      expectedNonceHash = NONCE_HASH,
      suppliedNonceHash = NONCE_HASH,
      foregroundConfirmedAtMillis = CONFIRMED_AT,
      wallNowMillis = now,
      resumedActivityPresent = resumed,
    )

  private companion object {
    const val NONCE_HASH = "0123456789abcdef"
    const val CONFIRMED_AT = 1_000_000L
  }
}
