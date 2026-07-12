package com.yashsomani.birthdayautopilot.automation.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsOutcomeAttentionPolicyTest {
  @Test
  fun finalFailurePartialAndUnknownOutcomesHaveStableAttentionCodes() {
    assertEquals(
      "BIRTHDAY_DELIVERY_FAILED",
      SmsOutcomeAttentionPolicy.safeCode(SmsVisibleOutcome.DELIVERY_FAILED),
    )
    assertEquals(
      "BIRTHDAY_PARTIAL_DELIVERY",
      SmsOutcomeAttentionPolicy.safeCode(SmsVisibleOutcome.PARTIAL_DELIVERY),
    )
    assertEquals(
      "BIRTHDAY_OUTCOME_UNKNOWN",
      SmsOutcomeAttentionPolicy.safeCode(SmsVisibleOutcome.SUBMISSION_UNKNOWN),
    )
  }

  @Test
  fun successAndFirstRetryableZeroRemainSilent() {
    assertNull(SmsOutcomeAttentionPolicy.safeCode(SmsVisibleOutcome.DELIVERED))
    assertNull(SmsOutcomeAttentionPolicy.safeCode(SmsVisibleOutcome.SENT_FROM_DEVICE))
    assertNull(SmsOutcomeAttentionPolicy.safeCode(SmsVisibleOutcome.ZERO_ACCEPTED_NO_SERVICE))
    assertNull(SmsOutcomeAttentionPolicy.safeCode(SmsVisibleOutcome.ZERO_ACCEPTED_RADIO_OFF))
  }
}
