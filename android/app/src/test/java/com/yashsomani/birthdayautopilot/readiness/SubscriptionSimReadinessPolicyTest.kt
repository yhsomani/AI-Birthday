package com.yashsomani.birthdayautopilot.readiness

import android.telephony.TelephonyManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSimReadinessPolicyTest {
  @Test
  fun readinessUsesTheSelectedSubscriptionState() {
    assertTrue(
      SubscriptionSimReadinessPolicy.evaluate(
        telephonyMessagingAvailable = true,
        subscriptionId = 2,
        phoneStatePermissionGranted = true,
        activeSubscriptionIds = setOf(1, 2),
        selectedSubscriptionSimState = TelephonyManager.SIM_STATE_READY,
      ) == true,
    )
    assertFalse(
      SubscriptionSimReadinessPolicy.evaluate(
        telephonyMessagingAvailable = true,
        subscriptionId = 2,
        phoneStatePermissionGranted = true,
        activeSubscriptionIds = setOf(1, 2),
        selectedSubscriptionSimState = TelephonyManager.SIM_STATE_ABSENT,
      ) == true,
    )
  }

  @Test
  fun inactiveOrUnobservableSelectionFailsClosed() {
    assertFalse(
      SubscriptionSimReadinessPolicy.evaluate(
        telephonyMessagingAvailable = true,
        subscriptionId = 2,
        phoneStatePermissionGranted = true,
        activeSubscriptionIds = setOf(1),
        selectedSubscriptionSimState = TelephonyManager.SIM_STATE_READY,
      ) == true,
    )
    assertNull(
      SubscriptionSimReadinessPolicy.evaluate(
        telephonyMessagingAvailable = true,
        subscriptionId = 2,
        phoneStatePermissionGranted = true,
        activeSubscriptionIds = null,
        selectedSubscriptionSimState = TelephonyManager.SIM_STATE_READY,
      ),
    )
    assertFalse(
      SubscriptionSimReadinessPolicy.evaluate(
        telephonyMessagingAvailable = true,
        subscriptionId = -1,
        phoneStatePermissionGranted = false,
        activeSubscriptionIds = null,
        selectedSubscriptionSimState = TelephonyManager.SIM_STATE_READY,
      ) == true,
    )
  }

  @Test
  fun prePermissionProbeCanStillUseTheExactSelectedManagerState() {
    assertTrue(
      SubscriptionSimReadinessPolicy.evaluate(
        telephonyMessagingAvailable = true,
        subscriptionId = 7,
        phoneStatePermissionGranted = false,
        activeSubscriptionIds = null,
        selectedSubscriptionSimState = TelephonyManager.SIM_STATE_READY,
      ) == true,
    )
  }

  @Test
  fun missingHardwareAndUnknownSelectedStateNeverReportReady() {
    assertFalse(
      SubscriptionSimReadinessPolicy.evaluate(
        telephonyMessagingAvailable = false,
        subscriptionId = 7,
        phoneStatePermissionGranted = true,
        activeSubscriptionIds = setOf(7),
        selectedSubscriptionSimState = TelephonyManager.SIM_STATE_READY,
      ) == true,
    )
    assertNull(
      SubscriptionSimReadinessPolicy.evaluate(
        telephonyMessagingAvailable = true,
        subscriptionId = 7,
        phoneStatePermissionGranted = true,
        activeSubscriptionIds = setOf(7),
        selectedSubscriptionSimState = null,
      ),
    )
  }
}
