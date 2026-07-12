package com.yashsomani.birthdayautopilot.automation.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionBindingPolicyTest {
  @Test
  fun `system default requires exact current active subscription`() {
    assertTrue(SubscriptionBindingPolicy.matches("SYSTEM_DEFAULT", 4, 4, true))
    assertFalse(SubscriptionBindingPolicy.matches("SYSTEM_DEFAULT", 4, 5, true))
    assertFalse(SubscriptionBindingPolicy.matches("SYSTEM_DEFAULT", 4, 4, false))
    assertFalse(SubscriptionBindingPolicy.matches("SYSTEM_DEFAULT", 4, null, true))
  }

  @Test
  fun `unsupported policy and invalid approved subscription fail closed`() {
    assertFalse(SubscriptionBindingPolicy.matches("EXPLICIT_SUBSCRIPTION", 4, 4, true))
    assertFalse(SubscriptionBindingPolicy.matches("SYSTEM_DEFAULT", -1, -1, true))
  }
}
