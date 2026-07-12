package com.yashsomani.birthdayautopilot.auth

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeletionRecoverySessionOutcomeGuardTest {
  @Test
  fun `cancel and exchange failure clear a stale recovered session`() = runBlocking {
    var sessionPresent = true
    var cleanupAttempts = 0
    val guard = DeletionRecoverySessionOutcomeGuard {
      cleanupAttempts += 1
      sessionPresent = false
      true
    }

    val exchangeFailure = guard.run {
      IdentityOutcome.Failed(IdentityFailure.FIREBASE_UNAVAILABLE)
    }
    assertEquals(
      IdentityOutcome.Failed(IdentityFailure.FIREBASE_UNAVAILABLE),
      exchangeFailure,
    )
    assertFalse(sessionPresent)

    sessionPresent = true
    try {
      guard.run { throw CancellationException("cancelled") }
      throw AssertionError("cancellation-required")
    } catch (_: CancellationException) {
      // Expected; cleanup must still have run in a non-cancellable context.
    }
    assertFalse(sessionPresent)
    assertEquals(2, cleanupAttempts)
  }

  @Test
  fun `successful exact recovery retains session only for immediate replay`() = runBlocking {
    var cleanupCalled = false
    val guard = DeletionRecoverySessionOutcomeGuard {
      cleanupCalled = true
      true
    }
    val signedIn = IdentityOutcome.SignedIn(IdentityProfile("person@example.com", null))

    assertEquals(signedIn, guard.run { signedIn })
    assertFalse(cleanupCalled)
  }

  @Test
  fun `cleanup failure converts a failed attempt to fail closed internal failure`() = runBlocking {
    val guard = DeletionRecoverySessionOutcomeGuard { false }
    assertEquals(
      IdentityOutcome.Failed(IdentityFailure.INTERNAL_FAILURE),
      guard.run { IdentityOutcome.Failed(IdentityFailure.USER_CANCELLED) },
    )
  }
}
