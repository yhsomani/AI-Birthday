package com.yashsomani.birthdayautopilot.automation.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsOutcomeProjectionPolicyTest {
  @Test
  fun `unknown submission refines to late sent and delivered evidence`() {
    val sent = SmsOutcomeProjectionPolicy.refine(
      SmsVisibleOutcome.SUBMISSION_UNKNOWN,
      SmsVisibleOutcome.SENT_EVIDENCE_LATE,
    )
    assertEquals(SmsVisibleOutcome.SENT_EVIDENCE_LATE, sent)
    assertEquals(
      SmsVisibleOutcome.DELIVERED_LATE,
      SmsOutcomeProjectionPolicy.refine(sent, SmsVisibleOutcome.DELIVERED_LATE),
    )
  }

  @Test
  fun `unknown evidence never overwrites confirmed delivery`() {
    assertEquals(
      SmsVisibleOutcome.DELIVERED,
      SmsOutcomeProjectionPolicy.refine(
        SmsVisibleOutcome.DELIVERED,
        SmsVisibleOutcome.DELIVERY_UNKNOWN,
      ),
    )
  }

  @Test
  fun `watchdogs can close submitted and sent projections as unknown`() {
    assertEquals(
      SmsVisibleOutcome.SUBMISSION_UNKNOWN,
      SmsOutcomeProjectionPolicy.refine(
        SmsVisibleOutcome.SUBMITTED,
        SmsVisibleOutcome.SUBMISSION_UNKNOWN,
      ),
    )
    assertEquals(
      SmsVisibleOutcome.DELIVERY_UNKNOWN,
      SmsOutcomeProjectionPolicy.refine(
        SmsVisibleOutcome.SENT_FROM_DEVICE,
        SmsVisibleOutcome.DELIVERY_UNKNOWN,
      ),
    )
  }

  @Test
  fun `conflicting later terminal evidence cannot rewrite a terminal projection`() {
    assertEquals(
      SmsVisibleOutcome.DELIVERY_FAILED,
      SmsOutcomeProjectionPolicy.refine(
        SmsVisibleOutcome.DELIVERY_FAILED,
        SmsVisibleOutcome.DELIVERED,
      ),
    )
  }

  @Test
  fun `late test evidence refines visibility without rewriting immutable test safety`() {
    val immutableSafetyState = "UNKNOWN"
    assertEquals(
      SmsVisibleOutcome.SENT_EVIDENCE_LATE,
      SmsOutcomeProjectionPolicy.refine(
        SmsVisibleOutcome.TEST_FAILED,
        SmsVisibleOutcome.SENT_EVIDENCE_LATE,
      ),
    )
    assertEquals("UNKNOWN", immutableSafetyState)
    assertEquals(
      SmsVisibleOutcome.DELIVERED_LATE,
      SmsOutcomeProjectionPolicy.refine(
        SmsVisibleOutcome.SENT_EVIDENCE_LATE,
        SmsVisibleOutcome.DELIVERED_LATE,
      ),
    )
  }

  @Test
  fun `attempt two progress replaces attempt one zero acceptance projection`() {
    val submitted = SmsOutcomeProjectionPolicy.refine(
      SmsVisibleOutcome.ZERO_ACCEPTED_RADIO_OFF,
      SmsVisibleOutcome.RETRY_SUBMITTED,
    )
    assertEquals(SmsVisibleOutcome.RETRY_SUBMITTED, submitted)
    val sent = SmsOutcomeProjectionPolicy.refine(
      submitted,
      SmsVisibleOutcome.RETRY_SENT_FROM_DEVICE,
    )
    assertEquals(SmsVisibleOutcome.RETRY_SENT_FROM_DEVICE, sent)
    assertEquals(
      SmsVisibleOutcome.DELIVERED,
      SmsOutcomeProjectionPolicy.refine(sent, SmsVisibleOutcome.DELIVERED),
    )
  }

  @Test
  fun `pre acceptance late projections normalize after durable acceptance`() {
    val normalizations = listOf(
      SmsVisibleOutcome.SENT_EVIDENCE_LATE to SmsVisibleOutcome.SENT_FROM_DEVICE,
      SmsVisibleOutcome.SENT_EVIDENCE_LATE to SmsVisibleOutcome.TEST_PASSED,
      SmsVisibleOutcome.ZERO_ACCEPTED_LATE to SmsVisibleOutcome.ZERO_ACCEPTED_RADIO_OFF,
      SmsVisibleOutcome.ZERO_ACCEPTED_LATE to SmsVisibleOutcome.ZERO_ACCEPTED_NO_SERVICE,
      SmsVisibleOutcome.DELIVERED_LATE to SmsVisibleOutcome.DELIVERED,
      SmsVisibleOutcome.DELIVERY_FAILED_LATE to SmsVisibleOutcome.DELIVERY_FAILED,
      SmsVisibleOutcome.PARTIAL_DELIVERY_LATE to SmsVisibleOutcome.PARTIAL_DELIVERY,
      SmsVisibleOutcome.PARTIAL_UNKNOWN to SmsVisibleOutcome.SUBMITTED,
    )

    normalizations.forEach { (provisional, accepted) ->
      assertEquals(
        accepted,
        SmsOutcomeProjectionPolicy.refine(provisional, accepted),
      )
    }
  }
}
