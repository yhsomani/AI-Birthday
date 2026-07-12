package com.yashsomani.birthdayautopilot.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSmsManagerPlanSourceInstrumentationTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val source = AndroidSmsManagerPlanSource(context)

  @Test
  fun realSmsManagerPlansExactTextWithoutSending() {
    val exactText = "Birthday wishes"
    val result = source.plan(exactText, TEST_SUBSCRIPTION_ID)

    assertTrue("Expected a native text plan, got $result", result is NativeSmsPlanResult.Planned)
    val plan = (result as NativeSmsPlanResult.Planned).plan
    assertEquals(exactText, plan.orderedParts.joinToString(""))
    assertEquals(1, plan.segmentCount)
    assertFalse(result.toString().contains(exactText))
  }

  @Test
  fun realSmsManagerPreservesUnicodeMultipartBoundariesOrFailsClosedWithoutPlatformAccess() {
    val exactText = "ज".repeat(71)
    val result = source.plan(exactText, TEST_SUBSCRIPTION_ID)

    if (result is NativeSmsPlanResult.Rejected) {
      assertTrue(
        "Only Android 17+ without READ_PHONE_STATE may reject this no-send platform probe",
        Build.VERSION.SDK_INT >= 37 &&
          context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
          PackageManager.PERMISSION_GRANTED,
      )
      assertEquals(NativeSmsPlanFailure.PLATFORM_UNAVAILABLE, result.reason)
      return
    }
    assertTrue("Expected a native Unicode plan, got $result", result is NativeSmsPlanResult.Planned)
    val plan = (result as NativeSmsPlanResult.Planned).plan
    assertEquals(SmsEncoding.UNICODE, plan.encoding)
    assertEquals(exactText, plan.orderedParts.joinToString(""))
    assertTrue(plan.segmentCount >= 2)
  }

  @Test
  fun invalidSubscriptionFailsBeforeAnyPlatformPlanning() {
    val result = source.plan("Birthday wishes", SubscriptionManager.INVALID_SUBSCRIPTION_ID)

    assertEquals(
      NativeSmsPlanResult.Rejected(NativeSmsPlanFailure.INVALID_SUBSCRIPTION),
      result,
    )
  }

  private companion object {
    // Segmentation does not access the radio or submit SMS. Active-subscription validation is a
    // separate preflight. Android 17 may additionally require the restricted build's telephony
    // permission even for length calculation; the unrestricted probe must then fail closed.
    const val TEST_SUBSCRIPTION_ID = 0
  }
}
