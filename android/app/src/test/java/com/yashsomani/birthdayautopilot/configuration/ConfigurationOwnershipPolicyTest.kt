package com.yashsomani.birthdayautopilot.configuration

import com.yashsomani.birthdayautopilot.core.model.AccountMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigurationOwnershipPolicyTest {
  @Test
  fun `non-owner modes cannot mutate birthday configuration`() {
    assertEquals(
      "active-sender-other-device",
      ConfigurationOwnershipPolicy.blockedReason(AccountMode.STANDBY),
    )
    assertEquals(
      "transfer-pending",
      ConfigurationOwnershipPolicy.blockedReason(AccountMode.TRANSFER_PENDING),
    )
    assertEquals(
      "firebase-account-deleting",
      ConfigurationOwnershipPolicy.blockedReason(AccountMode.DELETING),
    )
  }

  @Test
  fun `current sender modes can mutate configuration`() {
    assertNull(ConfigurationOwnershipPolicy.blockedReason(AccountMode.TEST_ONLY))
    assertNull(ConfigurationOwnershipPolicy.blockedReason(AccountMode.PAUSED_REPAIR))
    assertNull(ConfigurationOwnershipPolicy.blockedReason(AccountMode.AUTOMATION_ACTIVE))
  }
}
