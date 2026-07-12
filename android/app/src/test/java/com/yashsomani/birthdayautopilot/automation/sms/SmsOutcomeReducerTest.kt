package com.yashsomani.birthdayautopilot.automation.sms

import com.yashsomani.birthdayautopilot.storage.database.CallbackKind
import com.yashsomani.birthdayautopilot.storage.database.DeliveryEvidenceClass
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsOutcomeReducerTest {
  @Test
  fun `all timely sent successes require every multipart callback`() {
    assertEquals(
      SentEvidenceDecision.WAITING,
      reduce(sent(0, DeliveryEvidenceClass.SENT_SUCCESS), now = 500).timelySent,
    )
    assertEquals(
      SentEvidenceDecision.ALL_SENT,
      reduce(
        sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
        sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
        now = 500,
      ).timelySent,
    )
  }

  @Test
  fun `safe retry is exact uniform all-part proof only`() {
    assertEquals(
      SentEvidenceDecision.ALL_RADIO_OFF,
      reduce(
        sent(0, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF),
        sent(1, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF),
        now = 500,
      ).timelySent,
    )
    assertEquals(
      SentEvidenceDecision.COMPLETE_FAILURE,
      reduce(
        sent(0, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF),
        sent(1, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_NO_SERVICE),
        now = 500,
      ).timelySent,
    )
    assertEquals(
      SentEvidenceDecision.COMPLETE_FAILURE,
      reduce(
        sent(0, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF),
        sent(0, DeliveryEvidenceClass.SENT_FAILURE, at = 450),
        sent(1, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF),
        now = 500,
      ).timelySent,
    )
  }

  @Test
  fun `watchdog distinguishes no evidence from partial evidence`() {
    assertEquals(
      SentEvidenceDecision.SUBMISSION_UNKNOWN,
      reduce(now = SENT_DEADLINE).timelySent,
    )
    assertEquals(
      SentEvidenceDecision.PARTIAL_UNKNOWN,
      reduce(sent(0, DeliveryEvidenceClass.SENT_SUCCESS), now = SENT_DEADLINE).timelySent,
    )
  }

  @Test
  fun `partial sent evidence and partial delivery evidence remain distinct phases`() {
    val partialSent = reduce(
      sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
      now = SENT_DEADLINE,
    )
    assertEquals(SentEvidenceDecision.PARTIAL_UNKNOWN, partialSent.timelySent)
    assertEquals(DeliveryEvidenceDecision.NOT_APPLICABLE, partialSent.delivery)

    val partialDelivery = reduce(
      sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
      sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
      delivery(0, DeliveryEvidenceClass.DELIVERY_COMPLETE),
      now = DELIVERY_DEADLINE,
      deliveryDeadline = DELIVERY_DEADLINE,
    )
    assertEquals(SentEvidenceDecision.ALL_SENT, partialDelivery.timelySent)
    assertEquals(
      DeliveryEvidenceDecision.PARTIAL_DELIVERY_UNKNOWN,
      partialDelivery.delivery,
    )
  }

  @Test
  fun `sent watchdog decision table covers every terminal class`() {
    val cases = listOf(
      emptyList<SmsPartEvidence>() to SentEvidenceDecision.SUBMISSION_UNKNOWN,
      listOf(sent(0, DeliveryEvidenceClass.SENT_SUCCESS)) to
        SentEvidenceDecision.PARTIAL_UNKNOWN,
      listOf(
        sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
        sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
      ) to SentEvidenceDecision.ALL_SENT,
      listOf(
        sent(0, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF),
        sent(1, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF),
      ) to SentEvidenceDecision.ALL_RADIO_OFF,
      listOf(
        sent(0, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_NO_SERVICE),
        sent(1, DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_NO_SERVICE),
      ) to SentEvidenceDecision.ALL_NO_SERVICE,
      listOf(
        sent(0, DeliveryEvidenceClass.SENT_FAILURE),
        sent(1, DeliveryEvidenceClass.SENT_FAILURE),
      ) to SentEvidenceDecision.COMPLETE_FAILURE,
    )

    cases.forEach { (evidence, expected) ->
      assertEquals(
        expected,
        SmsOutcomeReducer.reduce(
          expectedPartCount = 2,
          sentDeadlineMillis = SENT_DEADLINE,
          deliveryDeadlineMillis = null,
          observedAtMillis = SENT_DEADLINE,
          evidence = evidence,
        ).timelySent,
      )
    }
  }

  @Test
  fun `evidence at deadline is late and cannot establish timely success`() {
    val decision = reduce(
      sent(0, DeliveryEvidenceClass.SENT_SUCCESS, at = SENT_DEADLINE),
      sent(1, DeliveryEvidenceClass.SENT_SUCCESS, at = SENT_DEADLINE),
      now = SENT_DEADLINE,
    )
    assertEquals(SentEvidenceDecision.SUBMISSION_UNKNOWN, decision.timelySent)
    assertEquals(SentEvidenceDecision.ALL_SENT, decision.visibleSent)
  }

  @Test
  fun `complete delivery requires every part in the same attempt`() {
    assertEquals(
      DeliveryEvidenceDecision.WAITING,
      reduce(
        sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
        sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
        delivery(0, DeliveryEvidenceClass.DELIVERY_COMPLETE),
        now = 700,
        deliveryDeadline = 900,
      ).delivery,
    )
    assertEquals(
      DeliveryEvidenceDecision.DELIVERED,
      reduce(
        sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
        sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
        delivery(0, DeliveryEvidenceClass.DELIVERY_COMPLETE),
        delivery(1, DeliveryEvidenceClass.DELIVERY_COMPLETE),
        now = 700,
        deliveryDeadline = 900,
      ).delivery,
    )
  }

  @Test
  fun `delivery terminal classes distinguish failed mixed and missing`() {
    assertEquals(
      DeliveryEvidenceDecision.DELIVERY_FAILED,
      delivered(
        DeliveryEvidenceClass.DELIVERY_FAILED,
        DeliveryEvidenceClass.DELIVERY_FAILED,
      ),
    )
    assertEquals(
      DeliveryEvidenceDecision.PARTIAL_DELIVERY,
      delivered(
        DeliveryEvidenceClass.DELIVERY_COMPLETE,
        DeliveryEvidenceClass.DELIVERY_FAILED,
      ),
    )
    assertEquals(
      DeliveryEvidenceDecision.PARTIAL_DELIVERY_UNKNOWN,
      reduce(
        sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
        sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
        delivery(0, DeliveryEvidenceClass.DELIVERY_COMPLETE),
        now = 900,
        deliveryDeadline = 900,
      ).delivery,
    )
    assertEquals(
      DeliveryEvidenceDecision.DELIVERY_UNKNOWN,
      reduce(
        sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
        sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
        now = 900,
        deliveryDeadline = 900,
      ).delivery,
    )
  }

  @Test
  fun `pending and malformed reports cannot overwrite a complete report`() {
    val decision = reduce(
      sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
      sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
      delivery(0, DeliveryEvidenceClass.DELIVERY_COMPLETE),
      delivery(0, DeliveryEvidenceClass.DELIVERY_PENDING, at = 650),
      delivery(0, DeliveryEvidenceClass.DELIVERY_UNKNOWN, at = 675),
      delivery(1, DeliveryEvidenceClass.DELIVERY_COMPLETE),
      now = 700,
      deliveryDeadline = 900,
    )
    assertEquals(DeliveryEvidenceDecision.DELIVERED, decision.delivery)
  }

  @Test
  fun `contradictory delivery terminals fail closed`() {
    val decision = reduce(
      sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
      sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
      delivery(0, DeliveryEvidenceClass.DELIVERY_COMPLETE),
      delivery(0, DeliveryEvidenceClass.DELIVERY_FAILED, at = 650),
      delivery(1, DeliveryEvidenceClass.DELIVERY_COMPLETE),
      now = 900,
      deliveryDeadline = 900,
    )
    assertEquals(DeliveryEvidenceDecision.PARTIAL_DELIVERY_UNKNOWN, decision.delivery)
    assertEquals(EvidenceCompleteness.CONFLICTING, decision.deliveryCompleteness)
  }

  private fun delivered(
    first: DeliveryEvidenceClass,
    second: DeliveryEvidenceClass,
  ): DeliveryEvidenceDecision = reduce(
    sent(0, DeliveryEvidenceClass.SENT_SUCCESS),
    sent(1, DeliveryEvidenceClass.SENT_SUCCESS),
    delivery(0, first),
    delivery(1, second),
    now = 700,
    deliveryDeadline = 900,
  ).delivery

  private fun reduce(
    vararg evidence: SmsPartEvidence,
    now: Long,
    deliveryDeadline: Long? = null,
  ): SmsOutcomeDecision = SmsOutcomeReducer.reduce(
    expectedPartCount = 2,
    sentDeadlineMillis = SENT_DEADLINE,
    deliveryDeadlineMillis = deliveryDeadline,
    observedAtMillis = now,
    evidence = evidence.toList(),
  )

  private fun sent(
    part: Int,
    evidence: DeliveryEvidenceClass,
    at: Long = 400,
  ) = SmsPartEvidence(part, CallbackKind.SENT, evidence, at)

  private fun delivery(
    part: Int,
    evidence: DeliveryEvidenceClass,
    at: Long = 600,
  ) = SmsPartEvidence(part, CallbackKind.DELIVERY, evidence, at)

  private companion object {
    const val SENT_DEADLINE = 1_000L
    const val DELIVERY_DEADLINE = 2_000L
  }
}
