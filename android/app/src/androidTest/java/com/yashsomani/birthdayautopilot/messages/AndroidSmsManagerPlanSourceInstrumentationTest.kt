package com.yashsomani.birthdayautopilot.messages

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
  private val source = AndroidSmsManagerPlanSource(
    ApplicationProvider.getApplicationContext(),
  )

  @Test
  fun realSmsManagerPlansExactTextWithoutSending() {
    val exactText = "Birthday wishes"
    val result = source.plan(exactText, TEST_SUBSCRIPTION_ID)

    assertTrue(result is NativeSmsPlanResult.Planned)
    val plan = (result as NativeSmsPlanResult.Planned).plan
    assertEquals(exactText, plan.orderedParts.joinToString(""))
    assertEquals(1, plan.segmentCount)
    assertFalse(result.toString().contains(exactText))
  }

  @Test
  fun realSmsManagerPreservesUnicodeMultipartBoundariesWithoutSending() {
    val exactText = "ज".repeat(71)
    val result = source.plan(exactText, TEST_SUBSCRIPTION_ID)

    assertTrue(result is NativeSmsPlanResult.Planned)
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
    // separate preflight; a non-negative ID is sufficient for this no-send platform contract test.
    const val TEST_SUBSCRIPTION_ID = 0
  }
}
