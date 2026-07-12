package com.yashsomani.birthdayautopilot.automation.state

enum class TestJobState {
  PREPARED,
  CLOUD_CLAIMED,
  ARM_RECONCILING,
  COORDINATION_UNKNOWN,
  CLOUD_ARMED,
  ARMED_SUPPRESSED,
  BARRIER_CONSUMED,
  SUBMITTED,
  SENT_FROM_DEVICE,
  PASSED,
  FAILED,
  PARTIAL_UNKNOWN,
  UNKNOWN,
  PERMANENT_FAILURE,
  CLEANUP_CANCELLED,
  RECEIPT_INVALIDATED,
}

object TestTransitionPolicy {
  private val allowed: Map<TestJobState, Set<TestJobState>> = mapOf(
    TestJobState.PREPARED to setOf(
      TestJobState.CLOUD_CLAIMED,
      TestJobState.FAILED,
      TestJobState.CLEANUP_CANCELLED,
    ),
    TestJobState.CLOUD_CLAIMED to setOf(
      TestJobState.ARM_RECONCILING,
      TestJobState.FAILED,
      TestJobState.CLEANUP_CANCELLED,
    ),
    TestJobState.ARM_RECONCILING to setOf(
      TestJobState.CLOUD_ARMED,
      TestJobState.ARMED_SUPPRESSED,
      TestJobState.COORDINATION_UNKNOWN,
      TestJobState.FAILED,
      TestJobState.CLEANUP_CANCELLED,
    ),
    TestJobState.COORDINATION_UNKNOWN to setOf(
      TestJobState.ARMED_SUPPRESSED,
      TestJobState.FAILED,
      TestJobState.CLEANUP_CANCELLED,
    ),
    TestJobState.CLOUD_ARMED to setOf(
      TestJobState.ARMED_SUPPRESSED,
      TestJobState.BARRIER_CONSUMED,
    ),
    TestJobState.BARRIER_CONSUMED to setOf(
      TestJobState.SUBMITTED,
      TestJobState.UNKNOWN,
      TestJobState.PERMANENT_FAILURE,
    ),
    TestJobState.SUBMITTED to setOf(
      TestJobState.SENT_FROM_DEVICE,
      TestJobState.PARTIAL_UNKNOWN,
      TestJobState.UNKNOWN,
      TestJobState.PERMANENT_FAILURE,
    ),
    TestJobState.SENT_FROM_DEVICE to setOf(
      TestJobState.PASSED,
      TestJobState.PARTIAL_UNKNOWN,
      TestJobState.UNKNOWN,
      TestJobState.PERMANENT_FAILURE,
    ),
    TestJobState.PASSED to setOf(TestJobState.RECEIPT_INVALIDATED),
  )

  fun canTransition(from: TestJobState, to: TestJobState): Boolean =
    allowed[from]?.contains(to) == true
}
