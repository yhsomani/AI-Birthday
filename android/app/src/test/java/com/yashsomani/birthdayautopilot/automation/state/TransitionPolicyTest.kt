package com.yashsomani.birthdayautopilot.automation.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionPolicyTest {
  @Test
  fun `birthday barrier has the complete submitted refused and unknown table`() {
    assertEquals(
      setOf(
        BirthdayJobState.SUBMITTED,
        BirthdayJobState.PERMANENT_FAILURE,
        BirthdayJobState.SUBMISSION_UNKNOWN,
      ),
      birthdayTargets(BirthdayJobState.SUBMISSION_BARRIER_CONSUMED),
    )
  }

  @Test
  fun `submitted birthday has only sent callback outcomes`() {
    assertEquals(
      setOf(
        BirthdayJobState.SENT_FROM_DEVICE,
        BirthdayJobState.RETRYABLE_ZERO,
        BirthdayJobState.RETRY_EXHAUSTED,
        BirthdayJobState.PERMANENT_FAILURE,
        BirthdayJobState.PARTIAL_UNKNOWN,
        BirthdayJobState.SUBMISSION_UNKNOWN,
      ),
      birthdayTargets(BirthdayJobState.SUBMITTED),
    )
    assertFalse(
      BirthdayTransitionPolicy.canTransition(
        BirthdayJobState.SUBMITTED,
        BirthdayJobState.PARTIAL_DELIVERY_UNKNOWN,
      ),
    )
  }

  @Test
  fun `sent from device has only delivery callback outcomes`() {
    assertEquals(
      setOf(
        BirthdayJobState.DELIVERED,
        BirthdayJobState.DELIVERY_FAILED,
        BirthdayJobState.PARTIAL_DELIVERY,
        BirthdayJobState.PARTIAL_DELIVERY_UNKNOWN,
        BirthdayJobState.DELIVERY_UNKNOWN,
      ),
      birthdayTargets(BirthdayJobState.SENT_FROM_DEVICE),
    )
    assertFalse(
      BirthdayTransitionPolicy.canTransition(
        BirthdayJobState.SENT_FROM_DEVICE,
        BirthdayJobState.PARTIAL_UNKNOWN,
      ),
    )
  }

  @Test
  fun `all post submission birthday terminal states stay terminal`() {
    setOf(
      BirthdayJobState.RETRY_EXHAUSTED,
      BirthdayJobState.DELIVERED,
      BirthdayJobState.DELIVERY_FAILED,
      BirthdayJobState.PARTIAL_DELIVERY,
      BirthdayJobState.PARTIAL_DELIVERY_UNKNOWN,
      BirthdayJobState.DELIVERY_UNKNOWN,
      BirthdayJobState.PARTIAL_UNKNOWN,
      BirthdayJobState.SUBMISSION_UNKNOWN,
      BirthdayJobState.PERMANENT_FAILURE,
    ).forEach { state ->
      assertEquals("$state must be terminal", emptySet<BirthdayJobState>(), birthdayTargets(state))
    }
  }

  @Test
  fun `birthday barrier cannot return to a schedulable state`() {
    assertTrue(
      BirthdayTransitionPolicy.canTransition(
        BirthdayJobState.SUBMISSION_BARRIER_CONSUMED,
        BirthdayJobState.SUBMISSION_UNKNOWN,
      ),
    )
    assertFalse(
      BirthdayTransitionPolicy.canTransition(
        BirthdayJobState.SUBMISSION_BARRIER_CONSUMED,
        BirthdayJobState.SCHEDULED,
      ),
    )
  }

  @Test
  fun `test barrier binds synchronous refusal to permanent failure`() {
    assertEquals(
      setOf(
        TestJobState.SUBMITTED,
        TestJobState.UNKNOWN,
        TestJobState.PERMANENT_FAILURE,
      ),
      testTargets(TestJobState.BARRIER_CONSUMED),
    )
  }

  @Test
  fun `passing test receipt can only become invalidated`() {
    assertTrue(TestTransitionPolicy.canTransition(TestJobState.PASSED, TestJobState.RECEIPT_INVALIDATED))
    assertFalse(TestTransitionPolicy.canTransition(TestJobState.PASSED, TestJobState.SUBMITTED))
  }

  private fun birthdayTargets(from: BirthdayJobState): Set<BirthdayJobState> =
    BirthdayJobState.entries.filterTo(mutableSetOf()) { to ->
      BirthdayTransitionPolicy.canTransition(from, to)
    }

  private fun testTargets(from: TestJobState): Set<TestJobState> =
    TestJobState.entries.filterTo(mutableSetOf()) { to -> TestTransitionPolicy.canTransition(from, to) }
}
