package com.yashsomani.birthdayautopilot.automation.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionPolicyTest {
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
  fun `unknown birthday outcome is terminal for automatic submission`() {
    BirthdayJobState.entries
      .filterNot { it == BirthdayJobState.SUBMISSION_UNKNOWN }
      .forEach { next ->
        assertFalse(
          BirthdayTransitionPolicy.canTransition(BirthdayJobState.SUBMISSION_UNKNOWN, next),
        )
      }
  }

  @Test
  fun `passing test receipt can only become invalidated`() {
    assertTrue(TestTransitionPolicy.canTransition(TestJobState.PASSED, TestJobState.RECEIPT_INVALIDATED))
    assertFalse(TestTransitionPolicy.canTransition(TestJobState.PASSED, TestJobState.SUBMITTED))
  }
}
