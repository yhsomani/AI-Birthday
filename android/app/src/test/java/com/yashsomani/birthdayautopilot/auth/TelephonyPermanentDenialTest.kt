package com.yashsomani.birthdayautopilot.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelephonyPermanentDenialTest {
  @Test
  fun exactPermanentDenialsMapToExactReadinessCodes() {
    assertEquals(
      "phone-state-permission-permanently-denied",
      TelephonyPermanentDenial.PHONE_STATE.readinessWireCode(),
    )
    assertEquals(
      "sms-permission-permanently-denied",
      TelephonyPermanentDenial.SMS.readinessWireCode(),
    )
    assertEquals(
      "permission-permanently-denied",
      TelephonyPermanentDenial.BOTH.readinessWireCode(),
    )
    assertNull(TelephonyPermanentDenial.NONE.readinessWireCode())
  }

  @Test
  fun unreadableDurableStateFailsClosedForBothPermissions() {
    assertTrue(TelephonyPermanentDenial.UNKNOWN.blocksPhoneState())
    assertTrue(TelephonyPermanentDenial.UNKNOWN.blocksSms())
    assertFalse(TelephonyPermanentDenial.NONE.blocksPhoneState())
    assertFalse(TelephonyPermanentDenial.NONE.blocksSms())
  }

  @Test
  fun exactPermanentIssuesReuseTheVerifiedAppSettingsActionHandle() {
    assertEquals(
      "permission-permanently-denied",
      TelephonyPermissionRemediationPolicy.actionCode(
        "phone-state-permission-permanently-denied",
      ),
    )
    assertEquals(
      "permission-permanently-denied",
      TelephonyPermissionRemediationPolicy.actionCode("sms-permission-permanently-denied"),
    )
    assertEquals(
      "permission-denied",
      TelephonyPermissionRemediationPolicy.actionCode("permission-denied"),
    )
  }
}
