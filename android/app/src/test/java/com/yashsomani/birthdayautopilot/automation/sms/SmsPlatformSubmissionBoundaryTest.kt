package com.yashsomani.birthdayautopilot.automation.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsPlatformSubmissionBoundaryTest {
  @Test
  fun exactSinglePartUsesSingleDispatcherWithOneCallbackOfEachKind() {
    val submission = SmsPlatformSubmissionPlan.create(
      exactText = "Happy birthday!",
      orderedParts = listOf("Happy birthday!"),
      sentIntents = listOf("sent-0"),
      deliveryIntents = listOf("delivery-0"),
    )
    var singleCalls = 0
    var multipartCalls = 0

    SmsPlatformSubmissionDispatcher.dispatch(
      submission = checkNotNull(submission),
      sendSingle = { text, sent, delivery ->
        singleCalls += 1
        assertEquals("Happy birthday!", text)
        assertEquals("sent-0", sent)
        assertEquals("delivery-0", delivery)
      },
      sendMultipart = { _, _, _ -> multipartCalls += 1 },
    )

    assertEquals(1, singleCalls)
    assertEquals(0, multipartCalls)
  }

  @Test
  fun twoPartPlanUsesOneMultipartDispatcherWithOrderedCallbacks() {
    val submission = SmsPlatformSubmissionPlan.create(
      exactText = "part onepart two",
      orderedParts = listOf("part one", "part two"),
      sentIntents = listOf("sent-0", "sent-1"),
      deliveryIntents = listOf("delivery-0", "delivery-1"),
    )
    var singleCalls = 0
    var multipartCalls = 0

    SmsPlatformSubmissionDispatcher.dispatch(
      submission = checkNotNull(submission),
      sendSingle = { _, _, _ -> singleCalls += 1 },
      sendMultipart = { parts, sent, delivery ->
        multipartCalls += 1
        assertEquals(listOf("part one", "part two"), parts)
        assertEquals(listOf("sent-0", "sent-1"), sent)
        assertEquals(listOf("delivery-0", "delivery-1"), delivery)
      },
    )

    assertEquals(0, singleCalls)
    assertEquals(1, multipartCalls)
  }

  @Test
  fun emptyOverCapMismatchedTextAndCallbackCountsAreRejectedBeforeDispatch() {
    val invalid = listOf(
      SmsPlatformSubmissionPlan.create("", emptyList(), emptyList<String>(), emptyList()),
      SmsPlatformSubmissionPlan.create(
        "abc",
        listOf("a", "b", "c"),
        listOf("s0", "s1", "s2"),
        listOf("d0", "d1", "d2"),
      ),
      SmsPlatformSubmissionPlan.create(
        "exact",
        listOf("different"),
        listOf("s0"),
        listOf("d0"),
      ),
      SmsPlatformSubmissionPlan.create(
        "ab",
        listOf("a", "b"),
        listOf("s0"),
        listOf("d0", "d1"),
      ),
    )

    assertTrue(invalid.all { it == null })
  }

  @Test
  fun acceptedCallRunsExactlyOnce() {
    var calls = 0

    val result = SmsPlatformSubmissionBoundary.execute(
      finalGateOpen = { true },
      submit = { calls += 1 },
    )

    assertEquals(SmsPlatformBoundaryResult.Accepted, result)
    assertEquals(1, calls)
  }

  @Test
  fun platformRuntimeAndLinkageFailuresAreUnknownAfterEntry() {
    val failures = listOf<Throwable>(
      IllegalStateException("radio binder failed"),
      TestLinkageError(),
    )

    failures.forEach { failure ->
      var calls = 0
      val result = SmsPlatformSubmissionBoundary.execute(
        finalGateOpen = { true },
        submit = {
          calls += 1
          throw failure
        },
      )

      assertEquals(SmsPlatformBoundaryResult.OutcomeUnknown, result)
      assertEquals(1, calls)
    }
  }

  @Test
  fun closedOrUnreadableFinalGateNeverCallsPlatform() {
    listOf<() -> Boolean>(
      { false },
      { throw IllegalStateException("signal store unavailable") },
      { throw TestLinkageError() },
    ).forEach { gate ->
      var called = false
      val result = SmsPlatformSubmissionBoundary.execute(
        finalGateOpen = gate,
        submit = { called = true },
      )

      assertEquals(SmsPlatformBoundaryResult.NotCalled, result)
      assertFalse(called)
    }
  }

  @Test
  fun finalGateWinsRaceAgainstEarlierOpenSnapshot() {
    var pendingSubscriptionChange = false
    val preliminaryGateWasOpen = !pendingSubscriptionChange
    pendingSubscriptionChange = true
    var called = false

    val result = SmsPlatformSubmissionBoundary.execute(
      finalGateOpen = { !pendingSubscriptionChange },
      submit = { called = true },
    )

    assertTrue(preliminaryGateWasOpen)
    assertEquals(SmsPlatformBoundaryResult.NotCalled, result)
    assertFalse(called)
  }

  private class TestLinkageError : LinkageError()
}
