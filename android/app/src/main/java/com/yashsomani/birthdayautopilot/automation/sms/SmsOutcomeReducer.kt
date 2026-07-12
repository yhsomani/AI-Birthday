package com.yashsomani.birthdayautopilot.automation.sms

import com.yashsomani.birthdayautopilot.storage.database.CallbackKind
import com.yashsomani.birthdayautopilot.storage.database.DeliveryEvidenceClass

/** Content-free, append-only callback evidence used by the pure outcome reducer. */
internal data class SmsPartEvidence(
  val partIndex: Int,
  val kind: CallbackKind,
  val evidenceClass: DeliveryEvidenceClass,
  val receivedAtMillis: Long,
)

internal enum class SentEvidenceDecision {
  WAITING,
  ALL_SENT,
  ALL_RADIO_OFF,
  ALL_NO_SERVICE,
  COMPLETE_FAILURE,
  PARTIAL_UNKNOWN,
  SUBMISSION_UNKNOWN,
}

internal enum class DeliveryEvidenceDecision {
  NOT_APPLICABLE,
  WAITING,
  DELIVERED,
  DELIVERY_FAILED,
  PARTIAL_DELIVERY,
  PARTIAL_DELIVERY_UNKNOWN,
  DELIVERY_UNKNOWN,
}

internal enum class EvidenceCompleteness {
  NONE,
  PARTIAL,
  COMPLETE,
  CONFLICTING,
}

internal data class SmsOutcomeDecision(
  /** Evidence that may still change durable submission safety and retry eligibility. */
  val timelySent: SentEvidenceDecision,
  /** All retained evidence, including evidence received after the sent watchdog. */
  val visibleSent: SentEvidenceDecision,
  val delivery: DeliveryEvidenceDecision,
  val sentCompleteness: EvidenceCompleteness,
  val deliveryCompleteness: EvidenceCompleteness,
  val latestSuccessfulSentAtMillis: Long?,
)

/**
 * Reduces callback evidence without reading message, recipient, or contact data.
 *
 * A retry proof is deliberately stricter than a success/failure projection: every part must have
 * exactly one allowed zero-acceptance class before the watchdog, with no contradictory evidence.
 */
internal object SmsOutcomeReducer {
  fun reduce(
    expectedPartCount: Int,
    sentDeadlineMillis: Long,
    deliveryDeadlineMillis: Long?,
    observedAtMillis: Long,
    evidence: List<SmsPartEvidence>,
  ): SmsOutcomeDecision {
    require(expectedPartCount in 1..MAX_PARTS) { "expected-part-count-invalid" }
    require(sentDeadlineMillis > 0 && observedAtMillis >= 0) { "outcome-time-invalid" }
    require(evidence.all { it.partIndex in 0 until expectedPartCount && it.receivedAtMillis >= 0 }) {
      "callback-evidence-invalid"
    }

    val sent = evidence.filter { it.kind == CallbackKind.SENT }
    val timelySent = sent.filter { it.receivedAtMillis < sentDeadlineMillis }
    val timely = reduceSentParts(
      expectedPartCount = expectedPartCount,
      evidence = timelySent,
      deadlineClosed = observedAtMillis >= sentDeadlineMillis,
    )
    val visible = reduceSentParts(
      expectedPartCount = expectedPartCount,
      evidence = sent,
      deadlineClosed = true,
    )
    val sentCompleteness = completeness(expectedPartCount, sent, CallbackKind.SENT)
    val latestSuccess = sent
      .filter { it.evidenceClass == DeliveryEvidenceClass.SENT_SUCCESS }
      .maxOfOrNull { it.receivedAtMillis }

    val deliveryRows = evidence.filter { it.kind == CallbackKind.DELIVERY }
    val delivery = if (visible != SentEvidenceDecision.ALL_SENT) {
      DeliveryEvidenceDecision.NOT_APPLICABLE
    } else {
      reduceDeliveryParts(
        expectedPartCount = expectedPartCount,
        evidence = deliveryRows,
        deadlineClosed = deliveryDeadlineMillis != null && observedAtMillis >= deliveryDeadlineMillis,
      )
    }
    return SmsOutcomeDecision(
      timelySent = timely,
      visibleSent = visible,
      delivery = delivery,
      sentCompleteness = sentCompleteness,
      deliveryCompleteness = completeness(expectedPartCount, deliveryRows, CallbackKind.DELIVERY),
      latestSuccessfulSentAtMillis = latestSuccess,
    )
  }

  private fun reduceSentParts(
    expectedPartCount: Int,
    evidence: List<SmsPartEvidence>,
    deadlineClosed: Boolean,
  ): SentEvidenceDecision {
    val states = (0 until expectedPartCount).map { partIndex ->
      val classes = evidence.asSequence()
        .filter { it.partIndex == partIndex }
        .map { it.evidenceClass }
        .toSet()
      when {
        classes.isEmpty() -> SentPartState.MISSING
        classes == setOf(DeliveryEvidenceClass.SENT_SUCCESS) -> SentPartState.SUCCESS
        classes == setOf(DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_RADIO_OFF) ->
          SentPartState.RADIO_OFF
        classes == setOf(DeliveryEvidenceClass.SENT_ZERO_ACCEPTANCE_NO_SERVICE) ->
          SentPartState.NO_SERVICE
        classes == setOf(DeliveryEvidenceClass.SENT_FAILURE) -> SentPartState.FAILURE
        else -> SentPartState.CONFLICTING
      }
    }

    return when {
      states.all { it == SentPartState.SUCCESS } -> SentEvidenceDecision.ALL_SENT
      states.all { it == SentPartState.RADIO_OFF } -> SentEvidenceDecision.ALL_RADIO_OFF
      states.all { it == SentPartState.NO_SERVICE } -> SentEvidenceDecision.ALL_NO_SERVICE
      states.none { it == SentPartState.MISSING } && states.none { it == SentPartState.SUCCESS } ->
        SentEvidenceDecision.COMPLETE_FAILURE
      !deadlineClosed -> SentEvidenceDecision.WAITING
      states.all { it == SentPartState.MISSING } -> SentEvidenceDecision.SUBMISSION_UNKNOWN
      else -> SentEvidenceDecision.PARTIAL_UNKNOWN
    }
  }

  private fun reduceDeliveryParts(
    expectedPartCount: Int,
    evidence: List<SmsPartEvidence>,
    deadlineClosed: Boolean,
  ): DeliveryEvidenceDecision {
    val states = (0 until expectedPartCount).map { partIndex ->
      val classes = evidence.asSequence()
        .filter { it.partIndex == partIndex }
        .map { it.evidenceClass }
        .toSet()
      val terminal = classes.intersect(
        setOf(
          DeliveryEvidenceClass.DELIVERY_COMPLETE,
          DeliveryEvidenceClass.DELIVERY_FAILED,
        ),
      )
      when {
        terminal.size > 1 -> DeliveryPartState.CONFLICTING
        terminal.singleOrNull() == DeliveryEvidenceClass.DELIVERY_COMPLETE ->
          DeliveryPartState.COMPLETE
        terminal.singleOrNull() == DeliveryEvidenceClass.DELIVERY_FAILED -> DeliveryPartState.FAILED
        DeliveryEvidenceClass.DELIVERY_PENDING in classes -> DeliveryPartState.PENDING
        DeliveryEvidenceClass.DELIVERY_UNKNOWN in classes -> DeliveryPartState.UNKNOWN
        else -> DeliveryPartState.MISSING
      }
    }
    return when {
      states.all { it == DeliveryPartState.COMPLETE } -> DeliveryEvidenceDecision.DELIVERED
      states.all { it == DeliveryPartState.FAILED } -> DeliveryEvidenceDecision.DELIVERY_FAILED
      states.all { it in TERMINAL_DELIVERY_STATES } &&
        states.any { it == DeliveryPartState.COMPLETE } &&
        states.any { it == DeliveryPartState.FAILED } -> DeliveryEvidenceDecision.PARTIAL_DELIVERY
      !deadlineClosed -> DeliveryEvidenceDecision.WAITING
      states.any { it in TERMINAL_DELIVERY_STATES } ->
        DeliveryEvidenceDecision.PARTIAL_DELIVERY_UNKNOWN
      states.any { it != DeliveryPartState.MISSING } ->
        DeliveryEvidenceDecision.PARTIAL_DELIVERY_UNKNOWN
      else -> DeliveryEvidenceDecision.DELIVERY_UNKNOWN
    }
  }

  private fun completeness(
    expectedPartCount: Int,
    evidence: List<SmsPartEvidence>,
    kind: CallbackKind,
  ): EvidenceCompleteness {
    val grouped = evidence.filter { it.kind == kind }.groupBy { it.partIndex }
    if (grouped.isEmpty()) return EvidenceCompleteness.NONE
    if (grouped.values.any { rows -> rows.map { it.evidenceClass }.toSet().size > 1 }) {
      return EvidenceCompleteness.CONFLICTING
    }
    return if ((0 until expectedPartCount).all(grouped::containsKey)) {
      EvidenceCompleteness.COMPLETE
    } else {
      EvidenceCompleteness.PARTIAL
    }
  }

  private enum class SentPartState { MISSING, SUCCESS, RADIO_OFF, NO_SERVICE, FAILURE, CONFLICTING }

  private enum class DeliveryPartState { MISSING, PENDING, COMPLETE, FAILED, UNKNOWN, CONFLICTING }

  private val TERMINAL_DELIVERY_STATES = setOf(
    DeliveryPartState.COMPLETE,
    DeliveryPartState.FAILED,
  )

  private const val MAX_PARTS = 255
}
