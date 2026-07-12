package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyConsequencePolicyTest {
  @Test
  fun `every privacy action returns stable content-free localization keys`() {
    LifecycleStateStore.PRIVACY_ACTIONS.forEach { action ->
      val keys = PrivacyConsequencePolicy.keys(action)
      assertTrue(keys.isNotEmpty())
      assertEquals(keys.distinct(), keys)
      assertTrue(keys.all(KEY::matches))
    }
    assertEquals(
      listOf(
        "privacy.consequence.automation-paused",
        "privacy.consequence.all-google-scopes-revoked",
        "privacy.consequence.google-working-data-removed",
      ),
      PrivacyConsequencePolicy.keys("revoke-google-access"),
    )
  }

  private companion object {
    val KEY = Regex("^privacy\\.consequence\\.[a-z0-9-]+$")
  }
}
