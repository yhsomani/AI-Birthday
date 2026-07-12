package com.yashsomani.birthdayautopilot.people

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleBackgroundSyncPolicyTest {
  @Test
  fun `completed refresh reconciles automation without attention`() {
    val decision = PeopleBackgroundSyncPolicy.decide(
      PeopleSyncOutcome.Completed(2, 1, CompletedSyncMode.INCREMENTAL, false),
      runAttemptCount = 0,
    )

    assertTrue(decision.reconcileAutomation)
    assertFalse(decision.retry)
    assertFalse(decision.notifyAttention)
  }

  @Test
  fun `missing account stays idle while foreground resolution creates attention`() {
    val signedOut = PeopleBackgroundSyncPolicy.decide(
      PeopleSyncOutcome.AuthorizationRequired(PeopleAuthorizationReason.FIREBASE_SESSION),
      runAttemptCount = 0,
    )
    val resolution = PeopleBackgroundSyncPolicy.decide(
      PeopleSyncOutcome.AuthorizationRequired(
        PeopleAuthorizationReason.FOREGROUND_RESOLUTION_REQUIRED,
      ),
      runAttemptCount = 0,
    )

    assertFalse(signedOut.notifyAttention)
    assertTrue(resolution.notifyAttention)
    assertFalse(resolution.retry)
  }

  @Test
  fun `network recovery is bounded and rate limits wait for the next period`() {
    assertTrue(
      PeopleBackgroundSyncPolicy.decide(PeopleSyncOutcome.Offline, 0).retry,
    )
    assertFalse(
      PeopleBackgroundSyncPolicy.decide(PeopleSyncOutcome.NetworkFailure, 2).retry,
    )
    assertFalse(
      PeopleBackgroundSyncPolicy.decide(
        PeopleSyncOutcome.RateLimited(retryAfterSeconds = 86_400),
        0,
      ).retry,
    )
  }

  @Test
  fun `permission and malformed provider states are terminal attention`() {
    for (outcome in listOf(
      PeopleSyncOutcome.Forbidden,
      PeopleSyncOutcome.Malformed(PeopleMalformedReason.INVALID_PAGE),
      PeopleSyncOutcome.BoundExceeded(PeopleBound.PERSON_COUNT),
      PeopleSyncOutcome.StorageFailure,
    )) {
      val decision = PeopleBackgroundSyncPolicy.decide(outcome, 0)
      assertTrue(decision.notifyAttention)
      assertFalse(decision.retry)
    }
  }

  @Test
  fun `standby ownership block stays idle without retry or misleading attention`() {
    val decision = PeopleBackgroundSyncPolicy.decide(
      PeopleSyncOutcome.OwnershipBlocked(
        PeopleSyncOwnershipBlock.ACTIVE_SENDER_OTHER_DEVICE,
      ),
      runAttemptCount = 0,
    )

    assertFalse(decision.retry)
    assertFalse(decision.reconcileAutomation)
    assertFalse(decision.notifyAttention)
  }
}
