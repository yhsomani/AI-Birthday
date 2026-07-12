package com.yashsomani.birthdayautopilot.automation.sms

internal object SmsVisibleOutcome {
  const val SUBMITTED = "SUBMITTED"
  const val SENT_FROM_DEVICE = "SENT_FROM_DEVICE"
  const val RETRY_SUBMITTED = "RETRY_SUBMITTED"
  const val RETRY_SENT_FROM_DEVICE = "RETRY_SENT_FROM_DEVICE"
  const val SENT_EVIDENCE_LATE = "SENT_EVIDENCE_LATE"
  const val DELIVERED = "DELIVERED"
  const val DELIVERED_LATE = "DELIVERED_LATE"
  const val DELIVERY_FAILED = "DELIVERY_FAILED"
  const val DELIVERY_FAILED_LATE = "DELIVERY_FAILED_LATE"
  const val PARTIAL_DELIVERY = "PARTIAL_DELIVERY"
  const val PARTIAL_DELIVERY_LATE = "PARTIAL_DELIVERY_LATE"
  const val PARTIAL_DELIVERY_UNKNOWN = "PARTIAL_DELIVERY_UNKNOWN"
  const val DELIVERY_UNKNOWN = "DELIVERY_UNKNOWN"
  const val PARTIAL_UNKNOWN = "PARTIAL_UNKNOWN"
  const val SUBMISSION_UNKNOWN = "SUBMISSION_UNKNOWN"
  const val ZERO_ACCEPTED_RADIO_OFF = "ZERO_ACCEPTED_RADIO_OFF"
  const val ZERO_ACCEPTED_NO_SERVICE = "ZERO_ACCEPTED_NO_SERVICE"
  const val ZERO_ACCEPTED_LATE = "ZERO_ACCEPTED_LATE"
  const val PERMANENT_FAILURE = "PERMANENT_FAILURE"
  const val RETRY_EXHAUSTED = "RETRY_EXHAUSTED"
  const val TEST_PASSED = "TEST_PASSED"
  const val TEST_FAILED = "TEST_FAILED"
}

internal object SmsOutcomeAttentionPolicy {
  fun safeCode(visibleOutcome: String): String? = when (visibleOutcome) {
    SmsVisibleOutcome.DELIVERY_FAILED,
    SmsVisibleOutcome.DELIVERY_FAILED_LATE,
    SmsVisibleOutcome.PERMANENT_FAILURE,
    SmsVisibleOutcome.RETRY_EXHAUSTED,
    SmsVisibleOutcome.TEST_FAILED,
    SmsVisibleOutcome.ZERO_ACCEPTED_LATE,
    -> "BIRTHDAY_DELIVERY_FAILED"
    SmsVisibleOutcome.PARTIAL_DELIVERY,
    SmsVisibleOutcome.PARTIAL_DELIVERY_LATE,
    -> "BIRTHDAY_PARTIAL_DELIVERY"
    SmsVisibleOutcome.DELIVERY_UNKNOWN,
    SmsVisibleOutcome.PARTIAL_DELIVERY_UNKNOWN,
    SmsVisibleOutcome.PARTIAL_UNKNOWN,
    SmsVisibleOutcome.SUBMISSION_UNKNOWN,
    -> "BIRTHDAY_OUTCOME_UNKNOWN"
    else -> null
  }
}

/** Late/malformed evidence can improve a projection but can never weaken a stronger result. */
internal object SmsOutcomeProjectionPolicy {
  fun refine(current: String?, candidate: String): String {
    if (current == null || current == candidate) return candidate
    if (current in ZERO_ACCEPTED_OUTCOMES && candidate in RETRY_PROGRESS_OUTCOMES) {
      return candidate
    }
    if (current == SmsVisibleOutcome.RETRY_SUBMITTED && candidate in RETRY_PROGRESS_OUTCOMES) {
      return candidate
    }
    if (current == SmsVisibleOutcome.SUBMITTED) return candidate
    if (current in setOf(
        SmsVisibleOutcome.SENT_FROM_DEVICE,
        SmsVisibleOutcome.RETRY_SENT_FROM_DEVICE,
      ) && candidate in DELIVERY_OUTCOMES
    ) {
      return candidate
    }
    if (current == SmsVisibleOutcome.TEST_PASSED && candidate in DELIVERY_OUTCOMES) {
      return candidate
    }
    if (current in setOf(
        SmsVisibleOutcome.DELIVERY_UNKNOWN,
        SmsVisibleOutcome.PARTIAL_DELIVERY_UNKNOWN,
      ) && candidate in FINAL_DELIVERY_OUTCOMES
    ) return candidate
    if (candidate in UNKNOWN_OUTCOMES && current !in UNKNOWN_OUTCOMES) return current
    if (current in FINAL_EVIDENCE_OUTCOMES) return current
    if (current in UNKNOWN_OUTCOMES && candidate in LATE_EVIDENCE_OUTCOMES) return candidate
    if (current == SmsVisibleOutcome.TEST_FAILED && candidate in LATE_EVIDENCE_OUTCOMES) {
      return candidate
    }
    if (current == SmsVisibleOutcome.SENT_EVIDENCE_LATE && candidate in LATE_DELIVERY_OUTCOMES) {
      return candidate
    }
    return current
  }

  private val UNKNOWN_OUTCOMES = setOf(
    SmsVisibleOutcome.SUBMISSION_UNKNOWN,
    SmsVisibleOutcome.PARTIAL_UNKNOWN,
    SmsVisibleOutcome.DELIVERY_UNKNOWN,
    SmsVisibleOutcome.PARTIAL_DELIVERY_UNKNOWN,
  )
  private val LATE_DELIVERY_OUTCOMES = setOf(
    SmsVisibleOutcome.DELIVERED_LATE,
    SmsVisibleOutcome.DELIVERY_FAILED_LATE,
    SmsVisibleOutcome.PARTIAL_DELIVERY_LATE,
  )
  private val LATE_EVIDENCE_OUTCOMES = LATE_DELIVERY_OUTCOMES + setOf(
    SmsVisibleOutcome.SENT_EVIDENCE_LATE,
    SmsVisibleOutcome.ZERO_ACCEPTED_LATE,
  )
  private val FINAL_DELIVERY_OUTCOMES = setOf(
    SmsVisibleOutcome.DELIVERED,
    SmsVisibleOutcome.DELIVERED_LATE,
    SmsVisibleOutcome.DELIVERY_FAILED,
    SmsVisibleOutcome.DELIVERY_FAILED_LATE,
    SmsVisibleOutcome.PARTIAL_DELIVERY,
    SmsVisibleOutcome.PARTIAL_DELIVERY_LATE,
  )
  private val DELIVERY_OUTCOMES = FINAL_DELIVERY_OUTCOMES + setOf(
    SmsVisibleOutcome.DELIVERY_UNKNOWN,
    SmsVisibleOutcome.PARTIAL_DELIVERY_UNKNOWN,
  )
  private val FINAL_EVIDENCE_OUTCOMES = FINAL_DELIVERY_OUTCOMES + setOf(
    SmsVisibleOutcome.ZERO_ACCEPTED_RADIO_OFF,
    SmsVisibleOutcome.ZERO_ACCEPTED_NO_SERVICE,
    SmsVisibleOutcome.ZERO_ACCEPTED_LATE,
    SmsVisibleOutcome.PERMANENT_FAILURE,
    SmsVisibleOutcome.RETRY_EXHAUSTED,
  )
  private val ZERO_ACCEPTED_OUTCOMES = setOf(
    SmsVisibleOutcome.ZERO_ACCEPTED_RADIO_OFF,
    SmsVisibleOutcome.ZERO_ACCEPTED_NO_SERVICE,
  )
  private val RETRY_PROGRESS_OUTCOMES = setOf(
    SmsVisibleOutcome.RETRY_SUBMITTED,
    SmsVisibleOutcome.RETRY_SENT_FROM_DEVICE,
    SmsVisibleOutcome.RETRY_EXHAUSTED,
    SmsVisibleOutcome.SENT_EVIDENCE_LATE,
    SmsVisibleOutcome.PERMANENT_FAILURE,
    SmsVisibleOutcome.PARTIAL_UNKNOWN,
    SmsVisibleOutcome.SUBMISSION_UNKNOWN,
  ) + DELIVERY_OUTCOMES
}
