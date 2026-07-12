package com.yashsomani.birthdayautopilot.automation.state

enum class BirthdayJobState {
  PLANNED,
  PREPARED,
  SCHEDULED,
  CLAIMED,
  COORDINATION_BLOCKED,
  CLOUD_CLAIMED,
  ARM_RECONCILING,
  COORDINATION_UNKNOWN,
  CLOUD_ARMED,
  ARMED_SUPPRESSED,
  SUBMISSION_BARRIER_CONSUMED,
  SUBMITTED,
  SENT_FROM_DEVICE,
  RETRYABLE_ZERO,
  RETRY_CLAIMED,
  RETRY_EXHAUSTED,
  DELIVERED,
  DELIVERY_FAILED,
  PARTIAL_DELIVERY,
  PARTIAL_DELIVERY_UNKNOWN,
  DELIVERY_UNKNOWN,
  PARTIAL_UNKNOWN,
  SUBMISSION_UNKNOWN,
  PERMANENT_FAILURE,
  SKIPPED,
  MISSED,
  CANCELLED,
}

object BirthdayTransitionPolicy {
  private val allowed: Map<BirthdayJobState, Set<BirthdayJobState>> = mapOf(
    BirthdayJobState.PLANNED to setOf(
      BirthdayJobState.PREPARED,
      BirthdayJobState.SKIPPED,
      BirthdayJobState.MISSED,
      BirthdayJobState.CANCELLED,
    ),
    BirthdayJobState.PREPARED to setOf(
      BirthdayJobState.SCHEDULED,
      BirthdayJobState.SKIPPED,
      BirthdayJobState.MISSED,
      BirthdayJobState.CANCELLED,
    ),
    BirthdayJobState.SCHEDULED to setOf(
      BirthdayJobState.CLAIMED,
      BirthdayJobState.COORDINATION_BLOCKED,
      BirthdayJobState.SKIPPED,
      BirthdayJobState.MISSED,
      BirthdayJobState.CANCELLED,
    ),
    BirthdayJobState.CLAIMED to setOf(
      BirthdayJobState.CLOUD_CLAIMED,
      BirthdayJobState.COORDINATION_BLOCKED,
      BirthdayJobState.MISSED,
      BirthdayJobState.CANCELLED,
    ),
    BirthdayJobState.COORDINATION_BLOCKED to setOf(
      BirthdayJobState.SCHEDULED,
      BirthdayJobState.MISSED,
      BirthdayJobState.CANCELLED,
    ),
    BirthdayJobState.CLOUD_CLAIMED to setOf(
      BirthdayJobState.ARM_RECONCILING,
      BirthdayJobState.SCHEDULED,
      BirthdayJobState.MISSED,
      BirthdayJobState.CANCELLED,
    ),
    BirthdayJobState.ARM_RECONCILING to setOf(
      BirthdayJobState.CLOUD_CLAIMED,
      BirthdayJobState.CLOUD_ARMED,
      BirthdayJobState.ARMED_SUPPRESSED,
      BirthdayJobState.COORDINATION_UNKNOWN,
      BirthdayJobState.SCHEDULED,
      BirthdayJobState.MISSED,
      BirthdayJobState.RETRY_EXHAUSTED,
      BirthdayJobState.CANCELLED,
    ),
    BirthdayJobState.COORDINATION_UNKNOWN to setOf(
      BirthdayJobState.ARMED_SUPPRESSED,
      BirthdayJobState.SCHEDULED,
      BirthdayJobState.MISSED,
      BirthdayJobState.CANCELLED,
    ),
    BirthdayJobState.CLOUD_ARMED to setOf(
      BirthdayJobState.ARMED_SUPPRESSED,
      BirthdayJobState.SUBMISSION_BARRIER_CONSUMED,
    ),
    BirthdayJobState.SUBMISSION_BARRIER_CONSUMED to setOf(
      BirthdayJobState.SUBMITTED,
      BirthdayJobState.SUBMISSION_UNKNOWN,
      BirthdayJobState.PERMANENT_FAILURE,
    ),
    BirthdayJobState.SUBMITTED to setOf(
      BirthdayJobState.SENT_FROM_DEVICE,
      BirthdayJobState.RETRYABLE_ZERO,
      BirthdayJobState.RETRY_EXHAUSTED,
      BirthdayJobState.PARTIAL_UNKNOWN,
      BirthdayJobState.SUBMISSION_UNKNOWN,
      BirthdayJobState.PERMANENT_FAILURE,
    ),
    BirthdayJobState.SENT_FROM_DEVICE to setOf(
      BirthdayJobState.DELIVERED,
      BirthdayJobState.DELIVERY_FAILED,
      BirthdayJobState.PARTIAL_DELIVERY,
      BirthdayJobState.PARTIAL_DELIVERY_UNKNOWN,
      BirthdayJobState.DELIVERY_UNKNOWN,
    ),
    BirthdayJobState.RETRYABLE_ZERO to setOf(
      BirthdayJobState.RETRY_CLAIMED,
      BirthdayJobState.RETRY_EXHAUSTED,
      BirthdayJobState.MISSED,
    ),
    BirthdayJobState.RETRY_CLAIMED to setOf(
      BirthdayJobState.ARM_RECONCILING,
      BirthdayJobState.RETRY_EXHAUSTED,
      BirthdayJobState.MISSED,
    ),
  )

  fun canTransition(from: BirthdayJobState, to: BirthdayJobState): Boolean =
    allowed[from]?.contains(to) == true

  fun requireTransition(from: BirthdayJobState, to: BirthdayJobState) {
    require(canTransition(from, to)) { "illegal-birthday-transition" }
  }
}
