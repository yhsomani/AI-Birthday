package com.yashsomani.birthdayautopilot.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidActivityRecoveryPolicyTest {
  @Test
  fun `historical failure without a current issue has no recovery route`() {
    assertNull(
      AndroidActivityRecoveryPolicy.route(
        kind = "delivery-failed",
        reason = "sim-invalid",
        currentIssueCodes = emptySet(),
        automationEffective = "active",
      ),
    )
  }

  @Test
  fun `failure with the same current issue routes to attention`() {
    assertEquals(
      "attention",
      AndroidActivityRecoveryPolicy.route(
        kind = "delivery-failed",
        reason = "sim-invalid",
        currentIssueCodes = setOf("sim-invalid"),
        automationEffective = "action-required",
      ),
    )
  }

  @Test
  fun `state-specific repairs use their direct destination`() {
    assertEquals(
      "automation",
      AndroidActivityRecoveryPolicy.route(
        kind = "paused",
        reason = null,
        currentIssueCodes = emptySet(),
        automationEffective = "paused-repair",
      ),
    )
    assertEquals(
      "people",
      AndroidActivityRecoveryPolicy.route(
        kind = "approval-invalidated",
        reason = "approval-invalid",
        currentIssueCodes = setOf("approval-invalid"),
        automationEffective = "action-required",
      ),
    )
    assertEquals(
      "settings",
      AndroidActivityRecoveryPolicy.route(
        kind = "transfer",
        reason = "transfer-pending",
        currentIssueCodes = setOf("transfer-pending"),
        automationEffective = "transfer-pending",
      ),
    )
  }
}
